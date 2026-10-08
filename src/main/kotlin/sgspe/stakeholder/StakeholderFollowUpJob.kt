package sgspe.stakeholder

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.Organizations
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.engagement.EngagementWriteGuard
import wasichai.core.platform.ClusterLock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// lo que el seguimiento calcula de un grupo de interés, puro para las pruebas unitarias (domain-model §7.2 y §7.4)
object FollowUp {
    const val NO_CONTACT = "SIN CONTACTO"
    const val CONTACT = "CONTACTO ESTABLECIDO"

    // los cambios del grupo de interés: last_contact_date si cambió y, si pasa de vacío a una fecha, la regla de primer
    // contacto de relationship_status (SIN CONTACTO, o vacío porque wasichai no aplica el valor por defecto, UPS-15, pasa a
    // CONTACTO ESTABLECIDO). un estado que una persona ya cambió no se toca (ASM-032)
    fun changes(
        stakeholder: Map<String, Any?>,
        lastContact: LocalDate?
    ): Map<String, Any?> {
        val stored = Values.text(stakeholder["last_contact_date"])
        val next = lastContact?.toString()
        if (stored == next) return emptyMap()
        val changes = mutableMapOf<String, Any?>("last_contact_date" to next)
        val status = Values.text(stakeholder["relationship_status"])
        if (stored == null && next != null && (status == null || status == NO_CONTACT)) changes["relationship_status"] = CONTACT
        return changes
    }

    // la fecha en Lima de un instante (el held_at de la interacción: un DATETIME en UTC, con Z o con desfase)
    fun dateOf(heldAt: Any?): LocalDate? =
        when (heldAt) {
            is Instant -> heldAt
            is OffsetDateTime -> heldAt.toInstant()
            else -> Values.text(heldAt)?.let { OffsetDateTime.parse(it).toInstant() }
        }?.atZone(LIMA)?.toLocalDate()

    // la fecha más reciente de cada grupo de interés entre sus interacciones realizadas: cada par es la fecha de una
    // interacción y los grupos de interés que ella alcanza
    fun latest(interactions: List<Pair<LocalDate, Collection<String>>>): Map<String, LocalDate> {
        val contacts = mutableMapOf<String, LocalDate>()
        for ((date, stakeholders) in interactions) stakeholders.forEach { contacts.merge(it, date) { a, b -> maxOf(a, b) } }
        return contacts
    }
}

// la vuelta de JOB-03 sin candado ni base, para probar su aislamiento con un doble: recorre las organizaciones, una que
// falla se registra con su excepción y no detiene a las demás, y al final lanza si alguna falló (el valor que devolvería se
// pierde, pero ya quedó en el registro)
class FollowUpSweep(
    private val organizationsHaving: suspend () -> List<UUID>,
    private val asPlatform: suspend (UUID, suspend () -> Int) -> Int,
    private val followUp: suspend (UUID) -> Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // los grupos de interés escritos
    suspend fun run(): Int {
        var written = 0
        val failed = mutableListOf<UUID>()
        for (organization in organizationsHaving()) {
            try {
                written += asPlatform(organization) { followUp(organization) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += organization
                log.error("StakeholderFollowUpJob falló en la organización {}", organization, e)
            }
        }
        check(failed.isEmpty()) { "StakeholderFollowUpJob falló en ${failed.size} organizaciones: $failed (se escribieron $written grupos de interés)" }
        return written
    }
}

// JOB-03 StakeholderFollowUpJob (workflows.md): una vez al día, como la plataforma de cada organización, mantiene
// last_contact_date de cada grupo de interés (la fecha en Lima del held_at más reciente de sus interacciones realizadas,
// vinculadas por engagement_stakeholder o por un participante con attended) y aplica la regla de primer contacto. calcula
// desde cero, sin ventana: la fecha más reciente depende de todas las interacciones, no de las que cambiaron. no usa
// listener, para que cerrar una interacción no exija escribir el grupo de interés. nunca mueve WF-B. las tareas de
// evaluación periódica y de inactividad llegan en F3, con `task`.
// una sola réplica por vuelta (ClusterLock.tryLock); una organización o un grupo de interés que falla no detiene a los
// demás: se registra con su excepción y el resto sigue, la vuelta lanza al final (FollowUpSweep) y la del día siguiente lo
// vuelve a intentar. cada grupo de interés que cambia se relee y se escribe dentro de Transactions.locked("stakeholder:<id>"),
// y solo si algún valor cambia (un valor igual no toca updatedAt ni deja historia)
@Component
class StakeholderFollowUpJob(
    private val records: Records,
    private val organizations: Organizations,
    private val transactions: Transactions,
    private val locks: ClusterLock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val sweep =
        FollowUpSweep(
            { organizations.having(STAKEHOLDER) },
            { organization, block -> records.asPlatform(organization, block) },
            ::followUp
        )

    // los grupos de interés escritos; null si otra réplica tenía el candado. si alguna organización o algún grupo de interés
    // falla, los demás se procesan y al final lanza, para que el fallo se vea y no solo quede en el log. JOB-02 (DerivedSweep)
    // en cambio solo lo registra y devuelve
    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use { sweep.run() }
    }

    private suspend fun followUp(organization: UUID): Int {
        val interactions = mutableListOf<Pair<LocalDate, Collection<String>>>()
        for (engagement in records.all(ENGAGEMENT, criteria = listOf(Criteria.inState(EngagementWriteGuard.HELD_STATES)))) {
            val date = FollowUp.dateOf(engagement.attributes["held_at"]) ?: continue
            val linked = records.relatedAsPlatform(organization, ENGAGEMENT, UUID.fromString(engagement.id), "engagement_stakeholder")
            val attended =
                records
                    .all(EngagementWriteGuard.PARTICIPANT, filters = mapOf("engagement" to engagement.id))
                    .filter { Values.bool(it.attributes["attended"]) == true }
                    .mapNotNull { Values.text(it.attributes["stakeholder"]) }
            interactions += date to (linked.map { it.id.toString() } + attended)
        }
        val contacts = FollowUp.latest(interactions)
        var written = 0
        var failed = 0
        val stakeholders = records.all(STAKEHOLDER)
        for (stakeholder in stakeholders) {
            try {
                if (refresh(stakeholder.id, stakeholder.attributes, contacts[stakeholder.id])) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.error("StakeholderFollowUpJob: no se actualizó el grupo de interés {}", stakeholder.id, e)
            }
        }
        check(failed == 0) { "no se actualizaron $failed de ${stakeholders.size} grupos de interés de la organización $organization (se escribieron $written)" }
        return written
    }

    // true si escribió. calcula los cambios con lo que ya se leyó: casi ningún grupo de interés cambia cada noche, y ese no
    // paga el candado ni la relectura. si hay cambios, relee bajo el candado y los calcula otra vez: si una persona lo cambió
    // mientras tanto, se parte de lo guardado ahora
    private suspend fun refresh(
        id: String,
        read: Map<String, Any?>,
        lastContact: LocalDate?
    ): Boolean {
        if (FollowUp.changes(read, lastContact).isEmpty()) return false
        val uuid = UUID.fromString(id)
        return transactions.locked("$STAKEHOLDER:$id") {
            val current = records.find(STAKEHOLDER, uuid)
            val changes = if (current == null) emptyMap() else FollowUp.changes(current.attributes, lastContact)
            if (changes.isEmpty()) {
                false
            } else {
                records.patch(STAKEHOLDER, uuid, changes, REASON)
                true
            }
        }
    }

    companion object {
        const val LOCK = "sgspe.stakeholder-follow-up"

        // el trabajo que firma cada escritura (RQ-AUD-007, security.md §13.2), como JOB-02
        const val NAME = "JOB-03 StakeholderFollowUpJob"
        const val REASON = "último contacto ($NAME)"
        const val STAKEHOLDER = "stakeholder"
        const val ENGAGEMENT = "engagement"
    }
}

// el reloj de JOB-03, en la zona de Lima. sgspe.jobs.enabled=false lo apaga (las pruebas lo fijan en las dos tareas de
// Gradle y llaman a runOnce); StakeholderFollowUpJob existe aunque esté apagado
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class StakeholderFollowUpSchedule(
    private val job: StakeholderFollowUpJob
) {
    // una vez al día, configurable (ASM-022)
    @Scheduled(cron = "\${sgspe.jobs.stakeholder-follow-up.cron:0 30 4 * * *}", zone = "America/Lima")
    suspend fun daily() {
        job.runOnce()
    }
}
