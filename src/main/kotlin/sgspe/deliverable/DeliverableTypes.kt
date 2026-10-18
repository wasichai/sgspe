package sgspe.deliverable

import org.springframework.stereotype.Component
import sgspe.dashboard.MonitoringRow
import sgspe.privacy.ReportModel
import java.time.LocalDate
import java.util.UUID

// una sección que el tipo declara (deliverables.md §7.2): su clave, título, orden, tipo (catálogo «Tipo de sección de
// entregable»), si es obligatoria, la guía de redacción y si admite sugerencias de IA (AI-04, plan 8.2c)
data class SectionSpec(
    val key: String,
    val title: String,
    val order: Int,
    val kind: String,
    val required: Boolean,
    val guide: String,
    val assisted: Boolean
)

// una fila leída como la plataforma, solo con los campos que el tipo declara (ReportSource los proyecta)
data class SourceRow(
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>
) {
    fun text(field: String): String? = attributes[field]?.toString()?.trim()?.ifEmpty { null }

    fun ref(field: String): UUID? = text(field)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
}

// lo que el ensamblado puede leer (especificación de F8 §3.3, paso 2): registros como la plataforma de la organización,
// proyectados a los campos que declara el tipo (ReportSourcesTest: nunca documento de identidad ni teléfono), las relaciones
// muchos a muchos y la consulta de F6 de concesiones sobre territorios
interface ReportSource {
    suspend fun one(
        objectName: String,
        id: UUID
    ): SourceRow?

    suspend fun all(
        objectName: String,
        filters: Map<String, String> = emptyMap()
    ): List<SourceRow>

    // los registros `target` del otro lado de una relación muchos a muchos, proyectados a los campos declarados de `target`
    suspend fun related(
        objectName: String,
        id: UUID,
        relationship: String,
        target: String
    ): List<SourceRow>

    // comunidades, sectores y localidades sobre las concesiones dadas (caso 12 de gis.md §5.3), con el área del cruce
    suspend fun concessionTerritories(concessions: List<UUID>): List<Map<String, Any?>>

    // la microcuenca de cada localidad dada: el nombre de la environmental_feature MICROCUENCA cuyo boundary cubre su location
    // (4.2 de T1, #80). las localidades sin microcuenca quedan fuera del mapa
    suspend fun microbasins(localities: List<UUID>): Map<UUID, String>

    // las 13 filas de la Tabla 9.5 (tablero monitoring) del proyecto en el periodo, con el lector de quien ensambla y la cifra
    // oficial si el periodo se cerró (especificación de F8 §3.8); null si la fuente no las calcula
    suspend fun monitoring(
        project: UUID,
        start: LocalDate,
        end: LocalDate
    ): List<MonitoringRow>? = null
}

// lo que el tipo recibe para ensamblar: el entregable, sus secciones con su texto, la plantilla congelada y las revisiones
class AssemblyInput(
    val deliverable: SourceRow,
    val sections: List<SourceRow>,
    val templateId: String?,
    val templateSha256: String,
    val draftRevision: String,
    val issueRevision: String,
    val today: LocalDate
)

// lo que sale del ensamblado: el modelo sin mapas (los pide JOB-05 a ReportMaps), los mapas que pide, los nombres de las
// personas del proyecto (para PersonalDataScan), los textos de cada sección que se revisan y la persona detrás de cada mención
// (por su token sin corchetes; la de un cargo o un grupo de interés, su titular), que AI-04 de sección compara con el
// diccionario (SectionText). `holders` no entra al modelo congelado
class Assembled(
    val model: ReportModel,
    val maps: List<MapRequest>,
    val personNames: Set<String>,
    val texts: Map<String, List<String>>,
    val holders: Map<String, UUID> = emptyMap()
)

// la clase de un tipo de entregable (deliverables.md §7.2): sus secciones, sus parámetros, sus lecturas declaradas y el
// ensamblado. `parameters` devuelve los campos que faltan para pedir un borrador (422 DELIVERABLE_PARAMETERS_MISSING)
interface DeliverableType {
    val name: String
    val version: Int
    val sections: List<SectionSpec>

    // objeto → campos que el ensamblado lee: ReportSource no entrega otros
    val reads: Map<String, Set<String>>

    // T1 exige un AIDS aprobado (§2.2.13; decisión 15 del plan 8.2c): el pedido lo comprueba como la persona
    val requiresApprovedArea: Boolean get() = false

    suspend fun missingParameters(
        deliverable: Map<String, Any?>,
        project: Map<String, Any?>
    ): List<String>

    suspend fun assemble(
        input: AssemblyInput,
        source: ReportSource
    ): Assembled
}

// los tipos de F8 por su valor de «Tipo de entregable»: T2 (FieldReportType, plan 8.2b) y T1 (SocialDiagnosisType, plan 8.2c)
@Component
class DeliverableTypes(
    types: List<DeliverableType>
) {
    private val byName = types.associateBy { it.name }

    fun of(name: String?): DeliverableType? = name?.let { byName[it] }

    val all: Collection<DeliverableType> get() = byName.values
}
