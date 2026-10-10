package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// generator_key de task solo la escribe la plataforma (JobTasks, dentro del trabajo): la clave es predecible, y quien pudiera
// crear una tarea con ella suprimiría en silencio la que el trabajo debe crear (JobTasks.ensure la da por hecha). una persona
// que fija o cambia la clave (también al dejarla vacía en un PUT) recibe un 403 con su código; reenviar la clave tal como se
// leyó no es cambiarla. la plataforma no tiene usuario (change.userId nulo). desde F4 vale igual para early_warning, cuya
// clave escribe JOB-04 (WarningRulesJob): una persona que la fijara silenciaría la regla ese día
@Component
@Order(GuardOrder.DERIVED)
class GeneratorKeyGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS || change.isLink()) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.userId == null) return emptyList()
        if (JobTasks.GENERATOR_KEY !in view.changed(change)) return emptyList()
        return listOf(
            Finding(
                code = CODE,
                message = "generator_key lo escribe solo la plataforma: no se fija ni se cambia por esta vía",
                field = JobTasks.GENERATOR_KEY,
                status = HttpStatus.FORBIDDEN
            )
        )
    }

    companion object {
        const val CODE = "GENERATOR_KEY_PLATFORM_ONLY"

        // los objetos con generator_key: las tareas de los trabajos y, desde F4, las alertas de JOB-04
        val OBJECTS: Set<String> = setOf(JobTasks.TASK, "early_warning")
    }
}
