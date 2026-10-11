package sgspe.investment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Amounts
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// el presupuesto total de un programa, puro (especificación de F5 §3.6, decisión 4, ASM-063): la suma de allocated_amount de
// sus presupuestos en la moneda del programa; los de otra moneda no se suman (S43 los muestra aparte) y el ejecutado no se
// deriva. sin moneda del programa no hay total; con moneda y sin presupuestos en ella, 0
object ProgramTotals {
    data class Budget(
        val allocated: BigDecimal?,
        val currency: String?
    )

    // los campos del presupuesto de los que depende el total
    private val SOURCES = listOf(Programs.PROGRAM, Programs.ALLOCATED, Programs.CURRENCY)

    fun total(
        currency: String?,
        budgets: List<Budget>
    ): BigDecimal? {
        if (currency == null) return null
        return budgets.filter { it.currency == currency }.mapNotNull { it.allocated }.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    // si el cambio de un presupuesto puede mover algún total: un alta y una baja siempre; un cambio solo si toca su programa, su
    // monto asignado o su moneda (el ejecutado registrado no lo mueve)
    fun affects(
        before: Map<String, Any?>?,
        after: Map<String, Any?>?
    ): Boolean = before == null || after == null || SOURCES.any { !Values.same(before[it], after[it]) }
}

// recalcula y escribe total_budget de un programa con Records (la marca SgspeWrite y el motivo), como quien escribió o como la
// plataforma. escribe solo si cambia: un valor igual no toca updatedAt ni deja historia. es una escritura de solo derivados
// (Records.patch, ADR-0027): el If-Match de quien editaba el programa sigue valiendo. la lectura y la escritura van dentro de
// Transactions.locked("social_program:<id>"): dos presupuestos que entran a la vez se suman los dos
@Component
class ProgramTotalsWriter(
    private val records: Records,
    private val transactions: Transactions
) {
    // true si escribió. reason: el motivo de la historia; el listener manda REASON y JOB-02 el que nombra al trabajo
    suspend fun refresh(
        program: UUID,
        reason: String
    ): Boolean =
        transactions.locked("${Programs.PROGRAM}:$program") {
            val current = records.find(Programs.PROGRAM, program)
            if (current == null) {
                false
            } else {
                val budgets =
                    records.all(Programs.BUDGET, filters = mapOf(Programs.PROGRAM to program.toString())).map {
                        ProgramTotals.Budget(
                            Amounts.parse(it.attributes[Programs.ALLOCATED], Programs.ALLOCATED),
                            Values.text(it.attributes[Programs.CURRENCY])
                        )
                    }
                val wanted = ProgramTotals.total(Values.text(current.attributes[Programs.CURRENCY]), budgets)
                if (Values.same(current.attributes[Programs.TOTAL_BUDGET], wanted)) {
                    false
                } else {
                    records.patch(Programs.PROGRAM, program, mapOf(Programs.TOTAL_BUDGET to wanted), reason)
                    true
                }
            }
        }

    companion object {
        const val REASON = "derivado: presupuesto total del programa"
    }
}

// ProgramTotalsListener (especificación de F5 §3.6, decisión 6 del plan 5.2b): al crear, cambiar o borrar un presupuesto
// recalcula su programa (los dos, si cambió de programa); al crear un programa o cambiar su moneda, ese programa. corre como
// quien escribió, dentro de la petición; si no puede, lo registra y la escritura sigue: JOB-02 corrige el valor en la hora
@Component
@Order(110)
class ProgramTotalsListener(
    private val writer: ProgramTotalsWriter
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind == RecordChangeKind.TRANSITIONED) return
        val programs =
            when (change.objectName) {
                Programs.BUDGET ->
                    if (ProgramTotals.affects(change.before, change.after)) {
                        setOfNotNull(Records.uuid(change.before?.get(Programs.PROGRAM)), Records.uuid(change.after?.get(Programs.PROGRAM)))
                    } else {
                        emptySet()
                    }
                Programs.PROGRAM ->
                    if (change.kind == RecordChangeKind.CREATED || !Values.same(change.before?.get(Programs.CURRENCY), change.after?.get(Programs.CURRENCY))) {
                        if (change.kind == RecordChangeKind.DELETED) emptySet() else setOf(change.recordId)
                    } else {
                        emptySet()
                    }
                else -> emptySet()
            }
        for (program in programs) {
            try {
                writer.refresh(program, ProgramTotalsWriter.REASON)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("ProgramTotalsListener: no se actualizó total_budget del programa {}", program, e)
            }
        }
    }
}

// el recalculador de JOB-02 para total_budget: los programas cuyos presupuestos o cuya moneda cambiaron desde la vuelta anterior,
// o todos en la vuelta diaria (un presupuesto borrado solo lo ve la vuelta diaria)
@Component
class ProgramTotalsRecalculator(
    private val records: Records,
    private val writer: ProgramTotalsWriter
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Programs.PROGRAM

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val programs =
            if (since == null) {
                records.all(Programs.PROGRAM).map { UUID.fromString(it.id) }
            } else {
                val changed = listOf(Criteria.updatedSince(since))
                (
                    records.all(Programs.BUDGET, criteria = changed).mapNotNull { Records.uuid(it.attributes[Programs.PROGRAM]) } +
                        records.all(Programs.PROGRAM, criteria = changed).map { UUID.fromString(it.id) }
                ).distinct()
            }
        var written = 0
        var failed = 0
        for (program in programs) {
            try {
                if (writer.refresh(program, REASON)) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("ProgramTotalsRecalculator: no se recalculó total_budget del programa {}", program, e)
            }
        }
        check(failed == 0) { "no se recalculó total_budget de $failed de ${programs.size} programas (se escribieron $written)" }
        return written
    }

    companion object {
        // JOB-02 firma sus escrituras: «derivado: presupuesto total del programa (JOB-02 DerivedFieldsJob)»
        val REASON = DerivedFieldsJob.reason("presupuesto total del programa")
    }
}
