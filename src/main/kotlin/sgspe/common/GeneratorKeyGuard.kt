package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// un campo que solo escribe la plataforma (un trabajo, sin usuario), con el código del 403 que recibe una persona que lo fija,
// cambia o vacía. `marked`: el código de sgspe con la marca SgspeWrite también lo fija cuando falta (un listener como quien
// llama), pero no lo cambia ni lo vacía
class PlatformField(
    val code: String,
    val marked: Boolean = false
)

// los campos de la plataforma, puro. generator_key de task solo la escribe la plataforma (JobTasks, dentro del trabajo): la
// clave es predecible, y quien pudiera crear una tarea con ella suprimiría en silencio la que el trabajo debe crear
// (JobTasks.ensure la da por hecha). desde F4 vale igual para early_warning, cuya clave escribe JOB-04 (WarningRulesJob): una
// persona que la fijara silenciaría la regla ese día. y desde la ronda branchfix de 4.2b (R-I3), early_warning_rule y
// detected_at de una alerta, en los que confían el silencio de JOB-04 (WarningRulesJob.silenced) y la protección de una regla
// con alertas (DependentsDeleteGuard.PRESERVED): una persona que vinculara una alerta manual a una regla, la reapuntara o
// moviera su hora silenciaría la regla, y quien vaciara el vínculo podría borrarla. detected_at de una alerta manual lo pone
// sgspe en el alta (WarningDetectedAt), con la marca
object PlatformOnlyFields {
    const val GENERATOR_KEY_CODE = "GENERATOR_KEY_PLATFORM_ONLY"
    const val CODE = "PLATFORM_ONLY"

    // por objeto, en el orden en que se juzgan: la clave primero, con su código de siempre
    val FIELDS: Map<String, Map<String, PlatformField>> =
        mapOf(
            JobTasks.TASK to mapOf(JobTasks.GENERATOR_KEY to PlatformField(GENERATOR_KEY_CODE)),
            "early_warning" to
                linkedMapOf(
                    JobTasks.GENERATOR_KEY to PlatformField(GENERATOR_KEY_CODE),
                    "early_warning_rule" to PlatformField(CODE),
                    "detected_at" to PlatformField(CODE, marked = true)
                )
        )

    // `changed`, los campos que la escritura cambia (WriteView.changed: reenviar un valor tal como se leyó no es cambiarlo);
    // `person`, si la escribe una persona (la plataforma no tiene usuario); `marked`, si lleva la marca de sgspe; `before`, lo
    // guardado (vacío en un alta): la marca solo fija un campo que estaba vacío
    fun findings(
        objectName: String,
        changed: Set<String>,
        person: Boolean,
        marked: Boolean,
        before: Map<String, Any?> = emptyMap()
    ): List<Finding> {
        if (!person) return emptyList()
        return FIELDS[objectName]
            .orEmpty()
            .filter { (name, field) -> name in changed && !(marked && field.marked && Values.text(before[name]) == null) }
            .map { (name, field) ->
                Finding(
                    code = field.code,
                    message = "$name lo escribe solo la plataforma: no se fija, cambia ni vacía por esta vía",
                    field = name,
                    status = HttpStatus.FORBIDDEN
                )
            }
    }
}

// la guarda de los campos de la plataforma (PlatformOnlyFields): una persona que fija o cambia uno de ellos (también al
// dejarlo vacío en un PUT) recibe un 403 con su código sobre el campo; reenviarlo tal como se leyó no es cambiarlo. la
// plataforma no tiene usuario (change.userId nulo)
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
        return PlatformOnlyFields.findings(change.objectName, view.changed(change), true, WriteMark.present(), change.before.orEmpty())
    }

    companion object {
        const val CODE = PlatformOnlyFields.GENERATOR_KEY_CODE

        // los objetos con campos de la plataforma: las tareas de los trabajos y, desde F4, las alertas de JOB-04
        val OBJECTS: Set<String> = PlatformOnlyFields.FIELDS.keys
    }
}
