package sgspe.investment

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.TenantModel
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordWrite
import java.util.UUID

// lo comprometido de una campaña (especificación de F5 §3.4, elección 6; dashboards.md §3.2): los compromisos en
// approved, in_progress, blocked, fulfilled, verified o closed con su amount y, si el tenant tiene el modelo de F5
// (TenantModel), los aportes en approved, procured, delivered o closed con su total_amount, cada uno en su moneda. sin el
// registro que se aprueba (cuenta como candidato). un monto guardado que Amounts rechaza se omite, se registra con su id y
// se cuenta (CAP_DATA_INVALID). lee como quien aprueba, dentro del candado de la campaña; la lectura es acotada por objeto
// (más de MAX_PER_OBJECT es un 400 TOO_MANY_RECORDS, nunca una suma recortada)
@Component
class CampaignCommitted(
    private val records: Records,
    private val tenant: TenantModel
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Committed(
        val approved: List<SupportCap.Approved>,
        val invalid: Int
    )

    data class Parsed(
        val approved: List<SupportCap.Approved>,
        val invalid: List<String>
    )

    suspend fun of(
        organizationId: UUID,
        campaign: UUID,
        excluding: UUID?
    ): Committed {
        val commitments = parse(read(Commitments.COMMITMENT, Commitments.COMMITTED, campaign), "amount", excluding)
        val contributions =
            if (tenant.has(organizationId, Contributions.CONTRIBUTION)) {
                parse(read(Contributions.CONTRIBUTION, Contributions.COMMITTED, campaign), Contributions.TOTAL, excluding)
            } else {
                Parsed(emptyList(), emptyList())
            }
        for (id in commitments.invalid + contributions.invalid) {
            log.warn("CampaignCommitted: el monto comprometido de {} en la campaña {} no es válido y no se suma al tope", id, campaign)
        }
        return Committed(commitments.approved + contributions.approved, commitments.invalid.size + contributions.invalid.size)
    }

    private suspend fun read(
        objectName: String,
        states: Set<String>,
        campaign: UUID
    ): List<RecordResponse> =
        readAs(objectName) {
            records.all(
                objectName,
                filters = mapOf(CAMPAIGN to campaign.toString()),
                criteria = listOf(Criteria.inState(states)),
                max = MAX_PER_OBJECT
            )
        }

    companion object {
        const val CAMPAIGN = "exploration_campaign"

        // el conjunto «comprometido» de los aportes (dashboards.md §3.2): el mismo que Contributions.COMMITTED, que es el que
        // lee KPI-INV-01 (DashboardData.committedOf)
        val CONTRIBUTION_STATES: Set<String> = Contributions.COMMITTED

        // los registros comprometidos de cada objeto que se leen al aprobar
        const val MAX_PER_OBJECT = 20_000

        // pura: el monto `field` y la moneda de cada fila, sin `excluding`; las filas cuyo monto Amounts rechaza, aparte
        fun parse(
            rows: List<RecordResponse>,
            field: String,
            excluding: UUID?
        ): Parsed {
            val approved = mutableListOf<SupportCap.Approved>()
            val invalid = mutableListOf<String>()
            for (row in rows) {
                if (row.id == excluding?.toString()) continue
                try {
                    approved += SupportCap.Approved(Amounts.parse(row.attributes[field], field), Values.text(row.attributes["currency"]))
                } catch (_: WasichaiException) {
                    invalid += row.id
                }
            }
            return Parsed(approved, invalid)
        }
    }
}

// el tope de apoyo social de una campaña al aprobar (RN-INV-003, decisión 14 del plan 5.2a): la lógica común de D7 de WF-D
// (SupportPolicyGuard) y P1 de WF-P (ContributionCapRule). lee la campaña y lo comprometido como quien aprueba; una campaña,
// un proyecto o unos registros que no lee son NotReadable, nunca un pase. una campaña sin tope no se juzga; una sin moneda
// nunca bloquea (CAP_OTHER_CURRENCY, P45). superar el tope, o un dato guardado inválido (CAP_DATA_INVALID), bloquea con
// policy_cap_blocks_approval del proyecto (por defecto, sí) salvo para quien tiene APPROVE_OVER_CAP sobre el objeto que se
// aprueba (commitment o social_contribution: la autoridad difiere, ASM-119); si no, advierte. quien llama ya tomó el candado
// de la campaña (CampaignCap.lock) o corre en seco
@Component
class CampaignCap(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val committed: CampaignCommitted
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun findings(
        change: RecordWrite,
        campaign: UUID,
        candidate: SupportCap.Approved
    ): List<Finding> {
        val record = readAs(CAMPAIGN) { records.find(CAMPAIGN, campaign) } ?: throw NotReadable(CAMPAIGN)
        val cap =
            try {
                Amounts.parse(record.attributes["social_support_cap"], "social_support_cap") ?: return emptyList()
            } catch (e: WasichaiException) {
                log.warn("CampaignCap: el tope de la campaña {} no es un monto válido: {}", campaign, e.message)
                return listOf(invalid(change, "El tope de la campaña no es un monto válido: corrija los datos de la campaña"))
            }
        val currency =
            Values.text(record.attributes[CURRENCY])
                ?: return listOf(Finding("CAP_OTHER_CURRENCY", "La campaña no tiene moneda: el tope no se compara (ASM-063)", CURRENCY, blocking = false))
        val read = committed.of(change.organizationId, campaign, change.recordId)
        val outcome = SupportCap.evaluate(cap, currency, candidate, read.approved)
        val findings = mutableListOf<Finding>()
        if (read.invalid > 0) {
            findings += invalid(change, "${read.invalid} registros comprometidos de la campaña tienen un monto guardado no válido y no se suman al tope")
        }
        if (outcome.otherCurrency > 0 || outcome.incomparable) {
            findings +=
                Finding(
                    "CAP_OTHER_CURRENCY",
                    "Hay montos en una moneda distinta de la de la campaña ($currency): no se suman al tope (ASM-063)",
                    CURRENCY,
                    blocking = false
                )
        }
        if (outcome.exceeded) {
            findings +=
                Finding(
                    "SUPPORT_CAP_EXCEEDED",
                    "La aprobación supera el tope de apoyo social de la campaña: $cap $currency, con ${outcome.total} comprometidos (RN-INV-003)",
                    CAMPAIGN,
                    blocking = blocks(change)
                )
        }
        return findings
    }

    // el error de datos: bloquea como el tope; si no, advierte
    private suspend fun invalid(
        change: RecordWrite,
        message: String
    ): Finding = Finding("CAP_DATA_INVALID", message, CAMPAIGN, blocking = blocks(change))

    private suspend fun blocks(change: RecordWrite): Boolean = policy(change.before.orEmpty()) && !overCap(change.objectName)

    private suspend fun overCap(objectName: String): Boolean {
        val user = callers.userOrNull()
        return user != null && permissions.has(user, OVER_CAP, objectName)
    }

    // la política del proyecto del registro (ASM-019); un proyecto que no se lee es NotReadable, nunca la política por defecto
    private suspend fun policy(before: Map<String, Any?>): Boolean {
        val id = Records.uuid(before["project"]) ?: return ProjectParameters.policy(emptyMap(), POLICY)
        val project = readAs("project") { records.find("project", id) }?.attributes ?: throw NotReadable("project")
        return ProjectParameters.policy(project, POLICY)
    }

    companion object {
        const val CAMPAIGN = "exploration_campaign"
        const val CURRENCY = "currency"
        const val OVER_CAP = "APPROVE_OVER_CAP"
        const val POLICY = "policy_cap_blocks_approval"

        // el candado de la campaña: dos aprobaciones de la misma campaña (compromisos o aportes) no pasan el tope a la vez
        fun lock(campaign: UUID): String = "$CAMPAIGN:$campaign"
    }
}
