package sgspe.dashboard

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

// los límites de los tableros consolidados (especificación de F8 §2.2.16): la tabla por proyecto del tablero executive calcula
// cada fila con las mismas funciones, así que admite hasta max-projects proyectos; más proyectos pedidos es 400
// TOO_MANY_RECORDS sobre project, y más por defecto omite la tabla (byProjectOmitted, decisión 24 del plan 8.2a). desde el
// plan 8.2a-bis la tabla sale de una consulta agrupada por proyecto por KPI, con las mismas cifras que cada proyecto solo.
// aggregates (plan 8.2a-bis): los KPI del tablero executive cuentan en la base; en false vuelven a leer filas con Records.
// se lee al arrancar: volver atrás es cambiar la configuración y reiniciar. vive hasta el cierre de F8, cuando una tarea de
// limpieza la quita junto con el camino de Records de esos KPI
@ConfigurationProperties("sgspe.dashboards")
data class DashboardsProperties(
    val executive: Executive = Executive(),
    val aggregates: Boolean = true
) {
    data class Executive(
        val maxProjects: Int = 25
    ) {
        init {
            require(maxProjects >= 1) { "sgspe.dashboards.executive.max-projects debe ser al menos 1" }
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DashboardsProperties::class)
class DashboardsConfiguration
