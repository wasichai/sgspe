package sgspe.warning

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Organizations
import sgspe.common.Raised
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.risk.Risks
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// la vuelta de JOB-04 sin candado, para probar su aislamiento con dobles: una organización que falla se registra y no detiene a
// las demás, y al final lanza si alguna falló (como DeadlineSweep)
class WarningSweep(
    private val organizations: suspend () -> List<UUID>,
    private val asPlatform: suspend (UUID, suspend () -> Int) -> Int,
    private val evaluate: suspend (UUID) -> Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(): Int {
        var created = 0
        val failed = mutableListOf<UUID>()
        val all = organizations()
        for (organization in all) {
            try {
                created += asPlatform(organization) { evaluate(organization) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += organization
                log.error("WarningRulesJob falló en la organización {}", organization, e)
            }
        }
        check(failed.isEmpty()) { "WarningRulesJob falló en ${failed.size} organizaciones: $failed (se crearon $created alertas)" }
        log.info("WarningRulesJob: {} alertas creadas en {} organizaciones", created, all.size)
        return created
    }
}

// JOB-04 WarningRulesJob (sgspe.warning, workflows.md «Tipos de regla de JOB-04», ASM-097): cada hora, una sola réplica
// (ClusterLock con la clave sgspe.warning), como la plataforma de cada organización con reglas de alerta
// (Organizations.having) y con el origen job:warning-rules. por organización lee las reglas habilitadas y los proyectos en
// active o suspended; a cada proyecto le aplica sus reglas y, de cada tipo que no tiene, las del tenant; mide cada regla con
// conteos (Records.count), salvo CONTEO DE QCS, que cuenta planteamientos con AttentionCounting sobre los de la ventana, y NIVEL
// DE RIESGO, que lee los riesgos revisados del proyecto. si dispara, crea la alerta en detected salvo que haya una abierta de la
// misma regla y proyecto (y riesgo) o una detectada dentro de la ventana; la crea en su propia transacción, con su
// generator_key único (un duplicado de otra réplica cuenta como hecho), así WarningNoticeListener y AUT-09 la ven como un
// alta. nunca aplica transiciones ni confirma (RN-EWS-001). lee como la plataforma, así que CONTEO DE QCS cuenta también las
// confidenciales: la alerta solo lleva el número
@Component
class WarningRulesJob(
    private val records: Records,
    private val organizations: Organizations,
    private val transactions: Transactions,
    private val catalog: ModelCatalog,
    private val locks: ClusterLock,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // las alertas creadas; null si otra réplica tenía el candado
    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use {
            val now = clock.instant()
            val today = LocalDate.ofInstant(now, LIMA)
            WarningSweep(
                { organizations.having(Warnings.RULE) },
                { organization, block -> records.asPlatform(organization, SOURCE, block) },
                { evaluate(now, today) }
            ).run()
        }
    }

    private suspend fun evaluate(
        now: Instant,
        today: LocalDate
    ): Int {
        val rules =
            records.all(Warnings.RULE, filters = mapOf("enabled" to "true")).mapNotNull { row ->
                WarningEvaluation.parse(row.id, row.attributes).also {
                    if (it == null) log.warn("WarningRulesJob: la regla {} no se puede evaluar (tipo, umbral o ventana inválidos); se salta", row.id)
                }
            }
        if (rules.isEmpty()) return 0
        val projects = records.all(PROJECT, criteria = listOf(Criteria.inState(ACTIVE))).map { it.id }
        var created = 0
        for (project in projects) {
            for (rule in WarningEvaluation.applicable(rules, project)) created += evaluate(rule, project, now, today)
        }
        return created
    }

    private suspend fun evaluate(
        rule: WarningRule,
        project: String,
        now: Instant,
        today: LocalDate
    ): Int {
        if (rule.kind == Warnings.RISK_LEVEL) {
            return risks(project).filter { WarningEvaluation.fires(it.score, rule.threshold) }.count { create(rule, project, it.score, it, now, today) }
        }
        val value = measure(rule, project, now, today)
        return if (WarningEvaluation.fires(value, rule.threshold) && create(rule, project, value, null, now, today)) 1 else 0
    }

    // el valor medido de un tipo por eventos o de COMPROMISOS VENCIDOS, en el proyecto
    private suspend fun measure(
        rule: WarningRule,
        project: String,
        now: Instant,
        today: LocalDate
    ): Long {
        val start = WarningEvaluation.windowStart(today, rule.windowDays)
        val from = start.atStartOfDay(LIMA).toInstant()
        val until = now.plusSeconds(1)
        val inProject = mapOf("project" to project)
        return when (rule.kind) {
            Warnings.SIGNALS -> records.count(Warnings.SIGNAL, inProject, listOf(Criteria.between("received_at", from, until)))
            Warnings.QCS -> {
                val raised =
                    Attention.OBJECTS.flatMap { type ->
                        records
                            .all(type, inProject, listOf(Criteria.between(Attention.RECEIVED_AT, from, until)), max = MAX_ROWS)
                            .map { Raised(type, it.id, it.state, it.attributes) }
                    }
                AttentionCounting.counted(raised).size.toLong()
            }
            Warnings.OVERDUE_COMMITMENTS ->
                records.count(COMMITMENT, inProject + ("deadline_status" to OVERDUE), listOf(Criteria.inState(PENDING)))
            Warnings.NEGATIVE_MENTIONS ->
                records.count(Warnings.MENTION, inProject + ("tone" to NEGATIVE), listOf(Criteria.dateBetween("published_date", start, today)))
            else -> records.count(Warnings.INCIDENT, inProject, listOf(Criteria.between("occurred_at", from, until)))
        }
    }

    // los riesgos revisados del proyecto con su puntaje vigente
    private suspend fun risks(project: String): List<RiskHit> =
        records
            .all(Risks.SOCIAL_RISK, mapOf("project" to project), listOf(Criteria.inState(Risks.REVIEWED)), max = MAX_ROWS)
            .mapNotNull { row ->
                val a = row.attributes
                val score = Values.decimal(a[Risks.SCORE])?.toLong() ?: return@mapNotNull null
                RiskHit(row.id, Values.text(a["code"]), score, Values.text(a["locality"]), Values.text(a["community"]))
            }

    // crea la alerta si la regla no está en silencio; false si lo estaba o si otra réplica la creó primero
    private suspend fun create(
        rule: WarningRule,
        project: String,
        value: Long,
        risk: RiskHit?,
        now: Instant,
        today: LocalDate
    ): Boolean {
        if (silenced(rule, project, risk?.id, now, today)) return false
        return try {
            transactions.run { records.create(Warnings.WARNING, WarningEvaluation.alert(rule, project, value, risk, now, today), REASON) }
            true
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    // en silencio: una alerta abierta (fuera de dismissed y closed) de la misma regla y proyecto (y riesgo), o una detectada
    // dentro de los window_days días de la regla (ASM-097)
    private suspend fun silenced(
        rule: WarningRule,
        project: String,
        risk: String?,
        now: Instant,
        today: LocalDate
    ): Boolean {
        val same = mapOf("project" to project, "early_warning_rule" to rule.id) + listOfNotNull(risk?.let { "social_risk" to it })
        val open = catalog.states(Warnings.WARNING) - Warnings.FINISHED
        if (records.count(Warnings.WARNING, same, listOf(Criteria.inState(open))) > 0) return true
        val from = WarningEvaluation.windowStart(today, rule.windowDays).atStartOfDay(LIMA).toInstant()
        return records.count(Warnings.WARNING, same, listOf(Criteria.between("detected_at", from, now.plus(Duration.ofDays(1))))) > 0
    }

    companion object {
        const val LOCK = "sgspe.warning"
        const val NAME = "JOB-04 WarningRulesJob"

        // la etiqueta de sus filas de auditoría (WAS-ADR-0050)
        const val SOURCE = "job:warning-rules"
        const val REASON = "alerta por regla ($NAME)"
        const val PROJECT = "project"
        const val COMMITMENT = "commitment"
        const val OVERDUE = "VENCIDO"
        const val NEGATIVE = "NEGATIVO"

        // los proyectos que se vigilan y los compromisos con plazo en curso (los estados de JOB-01, Commitments.PENDING)
        val ACTIVE: Set<String> = setOf("active", "suspended")
        val PENDING: Set<String> = setOf("approved", "in_progress", "blocked")

        // el tope de QCS o de riesgos de un proyecto que se leen en una regla: pasarse falla la organización, nunca recorta
        const val MAX_ROWS = 20_000
    }
}

// el reloj de JOB-04, cada hora en la zona de Lima (ASM-006); sgspe.jobs.enabled=false lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class WarningRulesSchedule(
    private val job: WarningRulesJob
) {
    @Scheduled(cron = "\${sgspe.jobs.warning-rules.cron:0 25 * * * *}", zone = "America/Lima")
    suspend fun hourly() {
        job.runOnce()
    }
}
