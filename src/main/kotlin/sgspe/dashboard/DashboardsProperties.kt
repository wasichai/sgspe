package sgspe.dashboard

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

// los límites de los tableros consolidados (especificación de F8 §2.2.16): la tabla por proyecto del tablero executive calcula
// cada fila con las mismas funciones, así que admite hasta max-projects proyectos; más proyectos pedidos es 400
// TOO_MANY_RECORDS sobre project, y más por defecto omite la tabla (byProjectOmitted, decisión 24 del plan 8.2a)
@ConfigurationProperties("sgspe.dashboards")
data class DashboardsProperties(
    val executive: Executive = Executive()
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
