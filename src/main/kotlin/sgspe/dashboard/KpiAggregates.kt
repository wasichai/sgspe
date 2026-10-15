package sgspe.dashboard

import org.springframework.stereotype.Component
import sgspe.territory.GateOutcome
import sgspe.territory.SpatialGate
import java.util.UUID

// las consultas agregadas de los KPI (AggregateSql) por la compuerta de SQL propio (SpatialGate, especificación de F6 §3.1):
// como la persona del token, con su legibilidad y todos los RecordReadScope (el alcance por proyecto y el de las QCS
// confidenciales), o, con `platform` (KpiContext.platform, solo DashboardData lo pasa), por selectAsPlatform: sin alcances y
// con la organización enlazada, fuera de una petición (plan 8.2a-bis)
@Component
class KpiAggregates(
    private val gate: SpatialGate
) {
    suspend fun <T : Any> select(
        query: AggregateQuery<T>,
        platform: UUID?
    ): GateOutcome<T> =
        if (platform == null) {
            gate.select(query.reads, query.sql, query.map)
        } else {
            gate.selectAsPlatform(platform, query.reads, query.sql, query.map)
        }
}
