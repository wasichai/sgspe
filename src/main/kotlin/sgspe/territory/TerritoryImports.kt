package sgspe.territory

import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.RequestMemo
import sgspe.common.TenantModel
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.toCodedException
import sgspe.scope.Assignments
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import java.io.File
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import kotlin.math.floor

// ImportProperties (Tarea 17) se registra aquí: sgspe no escanea @ConfigurationProperties
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImportProperties::class)
class TerritoryConfiguration

data class InvalidRow(
    val row: Int,
    val field: String,
    val message: String
)

data class OutOfZoneRow(
    val row: Int,
    val code: String?,
    val message: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Difference(
    val row: Int,
    val code: String,
    val outcome: String,
    val currentVersion: Int? = null,
    val distanceM: BigDecimal? = null,
    val previousEasting: BigDecimal? = null,
    val previousNorthing: BigDecimal? = null,
    val newEasting: BigDecimal? = null,
    val newNorthing: BigDecimal? = null,
    val areaHaBefore: BigDecimal? = null,
    val areaHaAfter: BigDecimal? = null
)

// la vista previa no lleva geometrías: el portal las pinta desde lo que confirma (contrato de la Fase 2.3)
data class ImportPreview(
    val importBatchId: UUID,
    val sha256: String,
    val target: String,
    val sourceCrs: String,
    val rows: Int,
    val valid: Int,
    val invalid: List<InvalidRow>,
    val differences: List<Difference>,
    val outOfZone: List<OutOfZoneRow>,
    // desde F6: el formato, la capa leída y su CRS detectado, una propuesta (CON-024)
    val format: String = ImportFormat.CSV.key,
    val layer: String? = null,
    val detectedCrs: String? = null
)

data class ImportConfirmation(
    val created: Int,
    val newVersions: Int,
    val updated: Int,
    val unchanged: Int,
    val rejected: Int,
    val evidenceId: UUID?
)

// lo que llega en las partes del multipart, ya leído. desde F6, la capa elegida y la carpeta temporal donde GDAL descomprime y
// convierte (la crea y la borra ImportController)
class ImportRequest(
    val target: String?,
    val project: String?,
    val sourceCrs: String?,
    val columns: String?,
    val file: File,
    val filename: String,
    val sha256: String,
    val layer: String? = null,
    val workspace: ImportWorkspace? = null
)

// la decisión de una fila al confirmar (la forma que fija la Fase 2.3): APPLY aplica la acción propuesta y DISCARD
// descarta la fila. una fila sin decisión aplica su acción propuesta, salvo la que cae fuera de la zona o de la región
// esperada, que solo entra con un APPLY explícito (gis.md §4.3, RQ-GIS-007)
data class RowDecision(
    val row: Int,
    val action: String
)

// un error de la importación con su código en errors[0].message y el campo en errors[0].field (api.md §3.3, P15)
internal fun importError(
    code: String,
    field: String,
    message: String,
    status: HttpStatus = HttpStatus.BAD_REQUEST
): WasichaiException = Finding(code, message, field, status = status).toCodedException()

// los dos límites de tiempo y de carga de una importación (RQ-SEC-020, ASM-050): a lo más `maxConcurrent` a la vez, y la
// que espera un lugar más que `timeout` es un 503 IMPORT_BUSY; la lectura y la validación de cada una, dentro de `timeout`
// o un 400 IMPORT_TIMEOUT. lo bloqueante (POI, la lectura del archivo) corre con `blocking`: en el despachador de E/S e
// interrumpible, así que el plazo vencido interrumpe el hilo, los lectores lo atienden (ImportReaders) y el permiso se
// suelta solo cuando la lectura de verdad se detuvo
class ImportGate internal constructor(
    maxConcurrent: Int,
    private val timeout: Duration,
    // solo para las pruebas: lo que pasa entre obtener el permiso y salir de la espera (la ventana del menor 1)
    private val afterAcquire: suspend () -> Unit
) {
    constructor(maxConcurrent: Int, timeout: Duration) : this(maxConcurrent, timeout, {})

    private val permits = Semaphore(maxConcurrent)

    // se decide por `acquired` y no por el null de withTimeoutOrNull: el plazo puede vencer cuando acquire ya obtuvo el
    // permiso, y entonces withTimeoutOrNull devuelve null igual. decidir por el null perdería ese permiso para siempre
    suspend fun <T> admit(block: suspend () -> T): T {
        var acquired = false
        withTimeoutOrNull(timeout.toMillis()) {
            permits.acquire()
            acquired = true
            afterAcquire()
        }
        if (!acquired) {
            throw importError(
                "IMPORT_BUSY",
                "file",
                "Hay demasiadas importaciones en curso: intente de nuevo en unos segundos",
                HttpStatus.SERVICE_UNAVAILABLE
            )
        }
        try {
            return block()
        } finally {
            permits.release()
        }
    }

    suspend fun <T> timed(block: suspend () -> T): T =
        try {
            withTimeout(timeout.toMillis()) { block() }
        } catch (_: TimeoutCancellationException) {
            throw importError("IMPORT_TIMEOUT", "file", "La importación tardó demasiado: la lectura y la validación superaron ${label(timeout)}")
        }

    companion object {
        suspend fun <T> blocking(block: () -> T): T = runInterruptible(Dispatchers.IO) { block() }

        fun label(duration: Duration): String = if (duration.toMillis() % 1000 == 0L) "${duration.seconds} s" else "${duration.toMillis()} ms"
    }
}

// las reglas de la importación que no consultan la base: las decisiones por fila, la zona y la región, los rangos de los
// números y el motivo de una geometría que PostGIS no procesa
internal object ImportRules {
    const val APPLY = "APPLY"
    const val DISCARD = "DISCARD"

    // los atributos sin los que no se crea un registro del destino, en orden. uno que la fila trajo con un valor inválido ya
    // se rechazó con su motivo (`rejected`): no se repite como «es obligatorio»
    fun missingRequired(
        required: Set<String>,
        attributes: Map<String, Any?>,
        rejected: Set<String> = emptySet()
    ): List<String> = required.filter { attributes[it] == null && it !in rejected }.sorted()

    // los destinos que llegan con el modelo de F6 (community.code, environmental_feature.code y los campos de origen)
    val SINCE_F6: Set<String> = setOf("community", "environmental_feature")

    // un destino de F6 en una organización que todavía no tiene su modelo (sin `code`): 400 sobre target, antes de leer el
    // archivo, y no un error al escribir
    fun requireModel(
        target: String,
        hasCode: Boolean
    ) {
        if (target in SINCE_F6 && !hasCode) {
            throw ValidationException("Destino inválido", "target", "$target se importa con el modelo de F6, que esta organización todavía no tiene")
        }
    }

    // cada valor numérico de un destino tiene un rango finito; fuera de él la fila se rechaza nombrando el campo. los
    // ángulos en grados; la cota en m s. n. m. (del fondo de un tajo a más que la cumbre más alta del Perú); el área
    // registral no pasa la superficie del Perú (unos 128,5 millones de ha)
    private val RANGES: Map<String, Pair<BigDecimal, BigDecimal>> =
        mapOf(
            "azimuth" to (BigDecimal.ZERO to BigDecimal(360)),
            "dip" to (BigDecimal(-90) to BigDecimal(90)),
            "depth_m" to (BigDecimal.ZERO to BigDecimal(10_000)),
            "source_elevation" to (BigDecimal(-500) to BigDecimal(9_000)),
            "registered_area_ha" to (BigDecimal.ZERO to BigDecimal(130_000_000))
        )

    // antes de leer el archivo: cada acción es APPLY o DISCARD y cada fila tiene a lo más una decisión
    fun checkShape(decisions: List<RowDecision>) {
        decisions.firstOrNull { it.action != APPLY && it.action != DISCARD }?.let {
            throw invalidDecision("la acción de la fila ${it.row} es APPLY o DISCARD")
        }
        decisions
            .groupingBy { it.row }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
            ?.let { throw invalidDecision("la fila ${it.key} tiene más de una decisión") }
    }

    // `rows`: cada fila válida y si cae fuera de zona. devuelve las que se aplican. una decisión sobre una fila que no es
    // válida en el archivo es un 400: el portal decide sobre las filas de la vista previa, y el archivo es el mismo
    fun decide(
        rows: Map<Int, Boolean>,
        decisions: List<RowDecision>
    ): Set<Int> {
        checkShape(decisions)
        decisions.firstOrNull { it.row !in rows }?.let { throw invalidDecision("la fila ${it.row} no es una fila válida del archivo") }
        val actions = decisions.associate { it.row to it.action }
        return rows
            .filter { (row, outOfZone) ->
                when (actions[row]) {
                    APPLY -> true
                    DISCARD -> false
                    else -> !outOfZone
                }
            }.keys
    }

    private fun invalidDecision(message: String) = ValidationException("Decisión inválida", "decisions", message)

    // fuera de la zona UTM del CRS declarado o de la envolvente del Perú: la fila vale, pero confirmarla es explícito.
    // `centroid` es (longitud, latitud)
    fun outOfZone(
        centroid: Pair<Double, Double>?,
        sourceSrid: Int,
        properties: ImportProperties
    ): String? {
        if (centroid == null) return "no se pudo ubicar la geometría"
        val (x, y) = centroid
        if (x < properties.minLongitude || x > properties.maxLongitude || y < properties.minLatitude || y > properties.maxLatitude) {
            return "cae fuera de la región esperada (envolvente del Perú)"
        }
        val declared = Crs.zone(sourceSrid) ?: return null
        val actual = floor((x + 180) / 6).toInt() + 1
        return if (actual != declared) "cae en la zona UTM ${actual}S, no en la ${declared}S del CRS declarado" else null
    }

    // el primer código cuyo registro (id y updatedAt; null si no había) cambió entre el análisis y el candado, o null.
    // cubre lo creado, lo cambiado y lo borrado entretanto
    fun <V> conflict(
        analyzed: Map<String, Pair<String, V>?>,
        fresh: Map<String, Pair<String, V>>
    ): String? = analyzed.entries.firstOrNull { (code, seen) -> fresh[code] != seen }?.key

    fun numberIssue(
        field: String,
        value: BigDecimal
    ): String? {
        val (min, max) = RANGES[field] ?: return null
        return if (value < min || value > max) "fuera de rango: ${value.toPlainString()} (de ${min.toPlainString()} a ${max.toPlainString()})" else null
    }

    // el motivo de una fila cuya geometría PostGIS no pudo procesar (InvalidGeometryException) o cuyo CRS no sirve para
    // medirla (IllegalArgumentException de GeoSql): en español, sin el texto crudo de PostGIS
    fun unprocessable(e: Exception): String {
        val reason =
            when {
                e is InvalidGeometryException && e.detail.contains("transform", ignoreCase = true) ->
                    "no se puede transformar desde el CRS de origen: las coordenadas caen fuera de su dominio"
                e is InvalidGeometryException -> "PostGIS no pudo procesarla: coordenadas fuera de dominio o GeoJSON mal formado"
                else -> "no se puede medir en un CRS de trabajo"
            }
        return "geometría inválida: $reason"
    }
}

// las versiones de un componente (RQ-GIS-010, gis.md §4.4): la primera, o la siguiente con previous_version mientras la
// anterior pasa a superseded, en la transacción de quien llama. nunca una sobrescritura. la usan la importación y el alta
// manual de componentes (preflight M1: una sola fuente para crear una versión)
@Component
class ComponentVersions(
    private val records: Records
) {
    suspend fun first(
        project: UUID,
        attributes: Map<String, Any?>,
        reason: String?,
        geometries: Map<String, Any?>
    ): RecordResponse =
        records.create(COMPONENT, attributes + mapOf("project" to project.toString(), "version_number" to 1, "superseded" to false), reason, geometries)

    // la versión nueva y la anterior ya reemplazada
    suspend fun next(
        project: UUID,
        current: RecordResponse,
        attributes: Map<String, Any?>,
        reason: String?,
        geometries: Map<String, Any?>
    ): Pair<RecordResponse, RecordResponse> {
        val version = (Values.decimal(current.attributes["version_number"])?.toInt() ?: 0) + 1
        val created =
            records.create(
                COMPONENT,
                attributes +
                    mapOf("project" to project.toString(), "version_number" to version, "superseded" to false, "previous_version" to current.id),
                reason,
                geometries
            )
        // superseded es derivado: se escribe solo si cambia (P11), y la versión vigente nunca lo tiene
        val superseded =
            if (Values.bool(current.attributes["superseded"]) == true) {
                current
            } else {
                records.patch(COMPONENT, UUID.fromString(current.id), mapOf("superseded" to true), reason)
            }
        return created to superseded
    }

    companion object {
        const val COMPONENT = "project_component"
    }
}

// la importación de coordenadas y geometrías con CRS de origen, vista previa y confirmación (gis.md §4, ADR-0019,
// api.md §4.2, RQ-GIS-003, RQ-GIS-007, RQ-GIS-008, RQ-GIS-010). no deja estado en el servidor: la confirmación vuelve a
// mandar el mismo archivo y los mismos campos con el SHA-256 de la vista previa, y se repite toda la validación. cinco
// destinos: project_component (puntos de XLSX o CSV, y GeoJSON), land_parcel, mining_concession y, desde F6, community y
// environmental_feature (reconciliados por code como las concesiones; una fila sin code se rechaza). un código
// nuevo se crea; uno sin desplazamiento queda igual (o se actualizan sus datos); uno desplazado es una versión nueva del
// componente, o una actualización del predio o la concesión. nunca una sobrescritura silenciosa.
// todo se escribe como quien importa (con su permiso, su alcance y la marca de Records), nunca como la plataforma.
// la lectura y las cuentas de PostGIS van fuera de toda transacción (un fallo de PostGIS dentro la dejaría abortada, P20):
// cada fila que PostGIS no procesa queda rechazada con su motivo. la confirmación toma después el candado por destino
// (y por proyecto en los componentes), verifica que lo registrado sea lo mismo que analizó (si otra confirmación escribió
// entretanto, 409 IMPORT_CONFLICT y nada se escribe) y escribe todo en una sola transacción. desde F3, en esa misma
// transacción, el archivo original como evidence con el import_batch_id del lote (ASM-060, ImportEvidence)
@Service
class TerritoryImports(
    private val records: Records,
    private val geo: GeoSql,
    private val catalog: ModelCatalog,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val callers: Callers,
    private val assignments: Assignments,
    private val versions: ComponentVersions,
    private val properties: ImportProperties,
    private val gdal: GdalReader,
    private val tools: GdalTools,
    private val tenantModel: TenantModel,
    private val evidence: ImportEvidence
) {
    private val gate = ImportGate(properties.maxConcurrent, properties.timeout)

    suspend fun preview(request: ImportRequest): ImportPreview =
        gate.admit {
            val prepared = prepare(request)
            gate.timed {
                val context = open(prepared)
                val analysis = reconcile(context, analyze(context))
                ImportPreview(
                    importBatchId = UUID.randomUUID(),
                    sha256 = request.sha256,
                    target = context.target.name,
                    sourceCrs = Crs.value(context.sourceSrid),
                    rows = analysis.rows,
                    valid = analysis.candidates.size,
                    invalid = analysis.invalid,
                    differences = analysis.candidates.map { it.difference },
                    outOfZone = analysis.candidates.mapNotNull { c -> c.parsed.outOfZone?.let { OutOfZoneRow(c.parsed.row, c.parsed.code, it) } },
                    format = context.format.key,
                    layer = context.layer?.name,
                    detectedCrs = context.layer?.detectedCrs
                )
            }
        }

    suspend fun confirm(
        batchId: UUID,
        request: ImportRequest,
        sha256: String?,
        decisions: List<RowDecision>
    ): ImportConfirmation {
        if (sha256.isNullOrBlank()) throw ValidationException("Falta el SHA-256 de la vista previa", "sha256", "es obligatorio: envíe el de la vista previa")
        if (!sha256.equals(request.sha256, ignoreCase = true)) {
            throw importError("SHA256_MISMATCH", "sha256", "El archivo no es el de la vista previa: su SHA-256 no coincide")
        }
        ImportRules.checkShape(decisions)
        return gate.admit {
            val prepared = prepare(request)
            val (context, analysis) = gate.timed { open(prepared).let { it to reconcile(it, analyze(it)) } }
            val applied = ImportRules.decide(analysis.candidates.associate { it.parsed.row to (it.parsed.outOfZone != null) }, decisions)
            val reason = "importación $batchId"
            // el original como evidence (ASM-060): solo si esta confirmación escribe algo con el lote; si el lote ya tiene su
            // evidencia, una confirmación que no escribe nada (la repetida) la devuelve y una que escribiría es 409
            // IMPORT_BATCH_USED, ya con el candado y después de IMPORT_CONFLICT. sin evidencia del lote, el original se sube antes
            val writes = analysis.candidates.any { it.parsed.row in applied && it.outcome != UNCHANGED }
            val staged =
                if (prepared.keepsOriginal && writes && evidence.of(batchId) == null) {
                    evidence.stage(context.organization, request.file, request.filename, context.format, request.sha256, batchId, context.target.name)
                } else {
                    null
                }
            // el candado del lote antes que el del destino, siempre en ese orden: dos confirmaciones del mismo lote a otro destino o
            // a otro proyecto también quedan en fila, y el lote tiene a lo sumo una evidencia (con la que quien confirma lee)
            transactions.locked(batchLock(context.organization, batchId)) {
                transactions.locked(lockKey(context)) {
                    // el alcance de quien importa se lee una vez, no en cada alta (ProjectScopeGuard)
                    RequestMemo.within {
                        requireUnchanged(context, analysis)
                        // con el candado: la evidencia del lote, también la que otra confirmación del mismo lote creó entretanto
                        val previous = if (prepared.keepsOriginal) evidence.of(batchId) else null
                        if (previous != null && writes) throw ImportEvidence.batchUsed(batchId)
                        val linked = linkedConcessions(context)
                        val counts = mutableMapOf(CREATE to 0, NEW_VERSION to 0, UPDATE to 0, UNCHANGED to 0)
                        for (candidate in analysis.candidates) {
                            if (candidate.parsed.row !in applied) continue
                            write(context, candidate, batchId, reason, linked)
                            counts.merge(candidate.outcome, 1, Int::plus)
                        }
                        val rejected =
                            analysis.invalid
                                .map { it.row }
                                .distinct()
                                .size + (analysis.candidates.size - applied.size)
                        // en la misma transacción que los registros (ImportEvidence)
                        val project = if (context.target.tables || context.target.name == CONCESSION) context.project else null
                        val kept = staged?.let { evidence.create(it, batchId, context.target.name, project, reason) } ?: previous
                        ImportConfirmation(
                            counts.getValue(CREATE),
                            counts.getValue(NEW_VERSION),
                            counts.getValue(UPDATE),
                            counts.getValue(UNCHANGED),
                            rejected,
                            kept?.let { UUID.fromString(it.id) }
                        )
                    }
                }
            }
        }
    }

    private suspend fun write(
        context: Context,
        candidate: Candidate,
        batchId: UUID,
        reason: String,
        linked: Set<String>
    ) {
        val target = context.target.name
        val row = candidate.parsed
        val origin =
            mapOf(
                "source_crs" to Crs.value(context.sourceSrid),
                "source_reference" to "${context.filename}${context.layer?.let { ", capa ${it.name}" }.orEmpty()}, fila ${row.row}",
                "import_batch_id" to batchId.toString()
            )
        val geometries = mapOf(row.field to row.geometry)
        val existing = candidate.existing
        when (candidate.outcome) {
            UNCHANGED -> Unit
            CREATE -> {
                val record =
                    if (target == COMPONENT) {
                        versions.first(context.project, row.attributes + origin, reason, geometries)
                    } else {
                        // el área va con la marca: AreaListener omite estas escrituras (Areas.FIELD, P11)
                        val area = if (context.target.writesArea) listOfNotNull(row.areaHa?.let { Areas.FIELD to it }).toMap() else emptyMap()
                        records.create(target, row.attributes + origin + area, reason, geometries)
                    }
                if (target == CONCESSION) records.link("project", context.project, PROJECT_CONCESSION, UUID.fromString(record.id), reason)
            }
            NEW_VERSION -> versions.next(context.project, existing!!, row.attributes + origin, reason, geometries)
            UPDATE -> {
                val id = UUID.fromString(existing!!.id)
                val changes = row.attributes.filter { (name, value) -> !Values.same(existing.attributes[name], value) }
                val area =
                    if (context.target.writesArea &&
                        !Values.same(existing.attributes[Areas.FIELD], row.areaHa)
                    ) {
                        mapOf(Areas.FIELD to row.areaHa)
                    } else {
                        emptyMap()
                    }
                if (changes.isNotEmpty() || area.isNotEmpty() || !candidate.sameShape) {
                    records.patch(target, id, changes + origin + area, reason, if (candidate.sameShape) emptyMap() else geometries)
                }
                if (target == CONCESSION && existing.id !in linked) records.link("project", context.project, PROJECT_CONCESSION, id, reason)
            }
        }
    }

    private class Target(
        val name: String,
        // campo por tipo GeoJSON: el de los componentes depende de la geometría; los demás tienen uno solo
        val fields: Map<String, Pair<String, String>>,
        val attributes: List<String>,
        val tables: Boolean,
        // lo que una fila debe traer (columna elegida o valor fijo del formulario); si no, se rechaza nombrándolo
        val required: Set<String> = emptySet(),
        // calculated_area_ha se escribe con la marca en la transacción (Areas.GEOMETRY): predios, concesiones y comunidades
        val writesArea: Boolean = !tables
    )

    private class Context(
        val target: Target,
        val project: UUID,
        val sourceSrid: Int,
        val workingSrid: Int?,
        val columns: ColumnMapping,
        // el archivo que se lee: el subido (F2) o el GeoJSON de la capa elegida (GDAL, en su CRS de origen)
        val file: File,
        val filename: String,
        val organization: UUID,
        val format: ImportFormat,
        val layer: SourceLayer?
    )

    // lo que se revisa antes de leer el archivo: destino, proyecto, permisos, formato, CRS pedido y GDAL
    private class Prepared(
        val target: Target,
        val project: UUID,
        val requestedSrid: Int?,
        val workingSrid: Int?,
        val columns: ColumnMapping,
        val format: ImportFormat,
        val request: ImportRequest,
        val organization: UUID,
        // el tenant tiene el modelo de F3: la confirmación guarda el original como evidence (ASM-060)
        val keepsOriginal: Boolean
    )

    // una fila con su geometría en EPSG:4326 ya normalizada al tipo del campo, sus atributos y lo que se midió de ella
    private class ParsedRow(
        val row: Int,
        val code: String,
        val attributes: Map<String, Any?>,
        val field: String,
        val geometry: Map<String, Any?>,
        val outOfZone: String?,
        val calcSrid: Int,
        val point: UtmPoint?,
        val areaHa: BigDecimal?
    )

    private class Rows(
        val total: Int,
        val parsed: List<ParsedRow>,
        val invalid: List<InvalidRow>
    )

    private class Candidate(
        val parsed: ParsedRow,
        val existing: RecordResponse?,
        val outcome: String,
        val sameShape: Boolean,
        val difference: Difference
    )

    private class Analysis(
        val rows: Int,
        val candidates: List<Candidate>,
        val invalid: List<InvalidRow>
    )

    // los campos, los permisos y el proyecto; nada del archivo todavía
    private suspend fun prepare(request: ImportRequest): Prepared {
        val target =
            TARGETS[request.target] ?: throw ValidationException("Destino inválido", "target", "debe ser uno de ${TARGETS.keys.joinToString(", ")}")
        val project = Records.uuid(request.project) ?: throw ValidationException("Falta el proyecto", "project", "debe ser el id de un proyecto")
        val catalogued = catalog.enumValues(SOURCE_CRS)
        // un valor fuera del catálogo es siempre 400 (CON-024); que falte se decide con el formato
        val requestedSrid = request.sourceCrs?.let { value -> value.takeIf { it in catalogued }?.let(Crs::srid) ?: throw invalidCrs(catalogued) }
        val user = callers.require()
        val grants = mutableListOf(Actions.CREATE to target.name, Actions.UPDATE to target.name)
        if (target.name == CONCESSION) grants += Actions.UPDATE to "project"
        // la confirmación guarda el original como evidence (ASM-060) y busca la de su lote: también la vista previa lo exige,
        // para que lo que se previsualiza se pueda confirmar
        val keepsOriginal = evidence.enabled(user.organizationId)
        if (keepsOriginal) grants += listOf(Actions.CREATE to ImportEvidence.EVIDENCE, Actions.READ to ImportEvidence.EVIDENCE)
        permissions.require(user, "Importar ${target.name}", *grants.toTypedArray())
        if (target.name in ImportRules.SINCE_F6) ImportRules.requireModel(target.name, tenantModel.hasField(user.organizationId, target.name, "code"))
        // los componentes se escriben bajo el alcance del proyecto: sin asignación vigente, 403 NOT_ASSIGNED ya aquí y no tras
        // analizar el archivo entero con PostGIS. los predios y las concesiones son del tenant y no pasan por el alcance
        // componentes y concesiones escriben en el proyecto (la concesión se vincula a él): asignación antes de leer
        if (target.tables || target.name == CONCESSION) assignments.requireAssigned(user, project)
        val format = ImportGate.blocking { ImportFormat.detect(request.filename, request.file, properties.maxEntries) }
        // F2 exige el CRS ya aquí; GDAL lo pide después de listar las capas, para proponer el detectado; KML, KMZ y GPX están
        // siempre en EPSG 4326
        if (requestedSrid == null && !format.gdal) throw invalidCrs(catalogued)
        format.fixedSrid?.let { fixed ->
            if (requestedSrid != null && requestedSrid != fixed) {
                throw ValidationException("CRS de origen inválido", "sourceCrs", "un archivo ${format.key.uppercase()} está siempre en ${Crs.value(fixed)}")
            }
        }
        format.driver?.let { driver -> ImportGate.blocking { tools.require(driver, format.key) } }
        val projectRecord = records.get("project", project)
        // solo una zona de trabajo: GeoSql mide en ellas y nada más (P19)
        val workingSrid = Crs.srid(Values.text(projectRecord.attributes["working_crs"]))?.takeIf(Crs::isWorkingZone)
        return Prepared(target, project, requestedSrid, workingSrid, ColumnMapping.parse(request.columns), format, request, user.organizationId, keepsOriginal)
    }

    private fun invalidCrs(catalogued: List<String>) =
        ValidationException("CRS de origen inválido", "sourceCrs", "es obligatorio y sale del catálogo «CRS de origen»: ${catalogued.joinToString(", ")}")

    // el archivo listo para leer y el CRS de origen resuelto: el subido (F2) o el GeoJSON que GDAL sacó de la capa elegida. un
    // formato de GDAL sin sourceCrs es 400 con las capas y su CRS detectado (ImportChoiceRequired); KML, KMZ y GPX, 4326
    private suspend fun open(prepared: Prepared): Context {
        val request = prepared.request
        val source =
            if (prepared.format.gdal) {
                val workspace = request.workspace ?: error("una importación con GDAL necesita su carpeta temporal")
                ImportGate.blocking { gdal.read(prepared.format, request.file, workspace, request.layer) }
            } else {
                null
            }
        val sourceSrid =
            prepared.format.fixedSrid ?: prepared.requestedSrid
                ?: throw ImportChoiceRequired.sourceCrs(source?.layers.orEmpty(), catalog.enumValues(SOURCE_CRS))
        return Context(
            prepared.target,
            prepared.project,
            sourceSrid,
            prepared.workingSrid,
            prepared.columns,
            source?.geojson ?: request.file,
            request.filename,
            prepared.organization,
            prepared.format,
            source?.layer
        )
    }

    // el archivo, fila por fila: código, duplicados, geometría, atributos, validez y lo que se mide de ella
    private suspend fun analyze(context: Context): Rows {
        // POI y la lectura del archivo bloquean: en el despachador de E/S, interrumpibles si vence el plazo
        val rows =
            ImportGate.blocking {
                if (context.format.gdal) {
                    ImportReaders.geojson(context.file, properties.maxRows)
                } else {
                    ImportReaders.read(context.file, context.filename, properties.maxRows)
                }
            }
        if (!context.target.tables && rows.any { it.geometry == null }) {
            throw importError("FORMAT_NOT_SUPPORTED", "file", "Este destino necesita un polígono: importe GeoJSON, Shapefile, KML, KMZ, GeoPackage o FileGDB")
        }
        val invalid = mutableListOf<InvalidRow>()
        val parsed = mutableListOf<ParsedRow>()
        val seen = mutableMapOf<String, Int>()
        for (row in rows) {
            val code = context.columns.value(row, "code")
            if (code == null) {
                invalid += InvalidRow(row.row, "code", "el código es obligatorio: es la clave de la conciliación")
                continue
            }
            val first = seen[code]
            if (first != null) {
                invalid += InvalidRow(row.row, "code", "código repetido en el archivo (fila $first)")
                continue
            }
            seen[code] = row.row
            parse(context, row, code, invalid)?.let { parsed += it }
        }
        return Rows(rows.size, parsed, invalid)
    }

    private suspend fun parse(
        context: Context,
        row: SourceRow,
        code: String,
        invalid: MutableList<InvalidRow>
    ): ParsedRow? {
        val (field, geometry) = shape(context, row, invalid) ?: return null
        val attributes = attributes(context.target, row, context.columns, invalid) ?: return null
        return try {
            val validity = geo.validity(geometry)
            if (!validity.valid) {
                invalid += InvalidRow(row.row, field, "geometría inválida: ${validity.reason}")
                return null
            }
            val centroid = geo.centroid(geometry)
            val zone = centroid?.let { GeoSql.zoneOf(it.first, it.second) }
            // el CRS de cálculo: el working_crs del proyecto para los componentes; la zona del centroide para predios y
            // concesiones, que no son de un solo proyecto (como Areas); si no, la zona del CRS de origen
            val calcSrid =
                (if (context.target.name == COMPONENT) context.workingSrid else null)
                    ?: zone
                    ?: Crs.zone(context.sourceSrid)?.let { 32700 + it }
                    ?: DEFAULT_SRID
            val areaHa = if (context.target.name == COMPONENT) null else zone?.let { geo.areaHa(geometry, it) }
            ParsedRow(
                row = row.row,
                code = code,
                attributes = attributes,
                field = field,
                geometry = geometry,
                outOfZone = ImportRules.outOfZone(centroid, context.sourceSrid, properties),
                calcSrid = calcSrid,
                point = geo.utm(geometry, calcSrid),
                areaHa = areaHa
            )
        } catch (e: InvalidGeometryException) {
            invalid += InvalidRow(row.row, field, ImportRules.unprocessable(e))
            null
        } catch (e: IllegalArgumentException) {
            invalid += InvalidRow(row.row, field, ImportRules.unprocessable(e))
            null
        }
    }

    // la geometría de una fila en EPSG:4326 y del tipo del campo destino, o null con su error en `invalid`
    private suspend fun shape(
        context: Context,
        row: SourceRow,
        invalid: MutableList<InvalidRow>
    ): Pair<String, Map<String, Any?>>? {
        val srid = context.sourceSrid
        val columns = context.columns
        val source =
            if (row.geometry != null) {
                "geometry"
            } else if (srid == Crs.WGS84) {
                "latitude"
            } else {
                "easting"
            }
        try {
            val wgs84 =
                if (row.geometry != null) {
                    geo.toWgs84(row.geometry, srid)
                } else if (srid == Crs.WGS84) {
                    val latitude = ImportReaders.number(columns.value(row, "latitude"))
                    val longitude = ImportReaders.number(columns.value(row, "longitude"))
                    if (latitude == null || longitude == null) {
                        invalid += InvalidRow(row.row, "latitude", "faltan la latitud y la longitud")
                        return null
                    }
                    Axes.latLonSwapped(latitude, longitude)?.let {
                        invalid += InvalidRow(row.row, "latitude", it)
                        return null
                    }
                    geo.pointToWgs84(longitude, latitude, srid)
                } else {
                    val easting = ImportReaders.number(columns.value(row, "easting"))
                    val northing = ImportReaders.number(columns.value(row, "northing"))
                    if (easting == null || northing == null) {
                        invalid += InvalidRow(row.row, "easting", "faltan el este y el norte")
                        return null
                    }
                    Axes.utmSwapped(easting, northing)?.let {
                        invalid += InvalidRow(row.row, "easting", it)
                        return null
                    }
                    geo.pointToWgs84(easting, northing, srid)
                }
            val type = wgs84["type"]?.toString().orEmpty()
            val (field, expected) =
                context.target.fields[type.removePrefix("Multi")] ?: context.target.fields[type] ?: run {
                    invalid += InvalidRow(row.row, "geometry", "${context.target.name} no admite una geometría ${type.ifEmpty { "sin tipo" }}")
                    return null
                }
            val normalized =
                geo.asType(wgs84, expected) ?: run {
                    invalid += InvalidRow(row.row, field, "una geometría $type de varias partes no cabe en un campo $expected")
                    return null
                }
            return field to normalized
        } catch (e: InvalidGeometryException) {
            invalid += InvalidRow(row.row, source, ImportRules.unprocessable(e))
            return null
        } catch (e: IllegalArgumentException) {
            invalid += InvalidRow(row.row, source, ImportRules.unprocessable(e))
            return null
        }
    }

    // los atributos del destino que trae la fila, validados contra su catálogo y sus rangos; null con su error si alguno no vale
    private fun attributes(
        target: Target,
        row: SourceRow,
        columns: ColumnMapping,
        invalid: MutableList<InvalidRow>
    ): Map<String, Any?>? {
        val out = mutableMapOf<String, Any?>("code" to columns.value(row, "code"))
        val rejected = mutableSetOf<String>()
        var ok = true
        for (name in target.attributes) {
            val raw = columns.value(row, SOURCE_COLUMNS[name] ?: name) ?: continue
            val field = catalog.objectOrNull(target.name)?.fields?.get(name)
            val issue: String?
            val value: Any?
            when (field?.type) {
                "DECIMAL", "INTEGER" -> {
                    value = ImportReaders.number(raw)
                    issue = if (value == null) "valor inválido: $raw" else ImportRules.numberIssue(name, value)
                }
                "ENUM" -> {
                    value = raw.takeIf { it in catalog.enumOf(target.name, name) }
                    issue = if (value == null) "valor inválido: $raw" else null
                }
                else -> {
                    value = raw
                    issue = null
                }
            }
            if (issue != null) {
                invalid += InvalidRow(row.row, name, issue)
                rejected += name
                ok = false
            } else {
                out[name] = value
            }
        }
        for (name in ImportRules.missingRequired(target.required, out, rejected)) {
            invalid += InvalidRow(row.row, name, "es obligatorio: elija su columna o un valor fijo")
            ok = false
        }
        if (!ok) return null
        // una hoja sin tipo de componente es la de sondajes de DOC-03 (gis.md §9): el portal mapea solo sus columnas
        if (target.name == COMPONENT && out["component_type"] == null) out["component_type"] = DEFAULT_COMPONENT_TYPE
        return out
    }

    // concilia cada fila con lo registrado: lo que hay con su código y, en las concesiones, si ya está vinculada al proyecto
    private suspend fun reconcile(
        context: Context,
        rows: Rows
    ): Analysis {
        val existing = existing(context, rows.parsed.map { it.code })
        val linked = linkedConcessions(context)
        val invalid = rows.invalid.toMutableList()
        val candidates =
            rows.parsed.mapNotNull { row ->
                try {
                    candidate(context, row, existing[row.code], linked)
                } catch (e: InvalidGeometryException) {
                    invalid += InvalidRow(row.row, row.field, ImportRules.unprocessable(e))
                    null
                } catch (e: IllegalArgumentException) {
                    invalid += InvalidRow(row.row, row.field, ImportRules.unprocessable(e))
                    null
                }
            }
        return Analysis(rows.total, candidates, invalid.sortedBy { it.row })
    }

    private suspend fun candidate(
        context: Context,
        row: ParsedRow,
        existing: RecordResponse?,
        linked: Set<String>
    ): Candidate {
        if (existing == null) {
            val difference = Difference(row.row, row.code, CREATE, newEasting = row.point?.easting, newNorthing = row.point?.northing, areaHaAfter = row.areaHa)
            return Candidate(row, null, CREATE, false, difference)
        }
        val stored = Records.geometriesOf(existing)
        val previous = stored[row.field] ?: stored.values.firstOrNull { it != null }
        val distance = previous?.let { geo.distanceM(it, row.geometry, row.calcSrid) }
        val previousPoint = previous?.let { geo.utm(it, row.calcSrid) }
        val sameShape = stored[row.field] != null && distance != null && distance < properties.unchangedToleranceM
        // los datos de origen no cuentan: el ruido numérico de una coordenada bajo la tolerancia no es un cambio (gis.md §4.4)
        val sameData = row.attributes.filterKeys { it !in PROVENANCE }.all { (name, value) -> Values.same(existing.attributes[name], value) }
        // una concesión ya registrada que este proyecto aún no tiene se vincula: es una actualización, no un «sin cambio»
        val sameLinks = context.target.name != CONCESSION || existing.id in linked
        val outcome =
            when {
                sameShape && sameData && sameLinks -> UNCHANGED
                context.target.name == COMPONENT && !sameShape -> NEW_VERSION
                else -> UPDATE
            }
        val difference =
            Difference(
                row = row.row,
                code = row.code,
                outcome = outcome,
                currentVersion = Values.decimal(existing.attributes["version_number"])?.toInt(),
                distanceM = distance,
                previousEasting = previousPoint?.easting,
                previousNorthing = previousPoint?.northing,
                newEasting = row.point?.easting,
                newNorthing = row.point?.northing,
                areaHaBefore = Values.decimal(existing.attributes[Areas.FIELD]),
                areaHaAfter = row.areaHa
            )
        return Candidate(row, existing, outcome, sameShape, difference)
    }

    // ya con el candado: lo registrado con los códigos del archivo debe ser lo mismo que se analizó. si otra confirmación (u
    // otra escritura) lo cambió entretanto, nada se escribe: el análisis ya no vale y hay que volver a previsualizar
    private suspend fun requireUnchanged(
        context: Context,
        analysis: Analysis
    ) {
        val fresh = existing(context, analysis.candidates.map { it.parsed.code })
        val changed =
            ImportRules.conflict(
                analysis.candidates.associate { it.parsed.code to it.existing?.let { record -> record.id to record.updatedAt } },
                fresh.mapValues { (_, record) -> record.id to record.updatedAt }
            )
        if (changed != null) {
            throw importError(
                "IMPORT_CONFLICT",
                "file",
                "Lo registrado con el código $changed cambió mientras se confirmaba: vuelva a previsualizar el archivo",
                HttpStatus.CONFLICT
            )
        }
    }

    // lo registrado con los códigos del archivo: la versión vigente de cada componente del proyecto, o el predio o la
    // concesión del tenant con ese código
    private suspend fun existing(
        context: Context,
        codes: List<String>
    ): Map<String, RecordResponse> {
        if (codes.isEmpty()) return emptyMap()
        val criteria = listOf(Criteria.textIn("code", codes.distinct()))
        return if (context.target.name == COMPONENT) {
            records
                .all(COMPONENT, filters = mapOf("project" to context.project.toString()), criteria = criteria)
                .filter { Values.bool(it.attributes["superseded"]) != true }
                .groupBy { Values.text(it.attributes["code"])!! }
                .mapValues { (_, current) -> current.maxBy { Values.decimal(it.attributes["version_number"]) ?: BigDecimal.ZERO } }
        } else {
            records.all(context.target.name, criteria = criteria).associateBy { Values.text(it.attributes["code"])!! }
        }
    }

    // las concesiones ya vinculadas al proyecto (project_concession); vacío para los otros destinos
    private suspend fun linkedConcessions(context: Context): Set<String> =
        if (context.target.name == CONCESSION) {
            records.related("project", context.project, PROJECT_CONCESSION).map { it.id }.toSet()
        } else {
            emptySet()
        }

    // el lote de una confirmación (ASM-060): lo toma antes que el del destino
    private fun batchLock(
        organization: UUID,
        batchId: UUID
    ): String = "$LOCK_PREFIX$organization:batch:$batchId"

    // los componentes se serializan por proyecto; los predios y las concesiones, que son del tenant, por organización
    private fun lockKey(context: Context): String =
        when (context.target.name) {
            COMPONENT -> componentLock(context.organization, context.project)
            else -> "$LOCK_PREFIX${context.organization}:${context.target.name}"
        }

    companion object {
        const val COMPONENT = ComponentVersions.COMPONENT
        private const val LOCK_PREFIX = "territory-import:"

        // el candado de los componentes de un proyecto: la confirmación de una importación y los comandos de
        // ProjectComponents (alta, versión y PATCH) toman el mismo, así que se ordenan entre sí
        fun componentLock(
            organization: UUID,
            project: UUID
        ): String = "$LOCK_PREFIX$organization:$COMPONENT:$project"

        const val PARCEL = "land_parcel"
        const val CONCESSION = "mining_concession"
        const val COMMUNITY = "community"
        const val FEATURE = "environmental_feature"
        const val PROJECT_CONCESSION = "project_concession"
        const val CREATE = "CREATE"
        const val UNCHANGED = "UNCHANGED"
        const val NEW_VERSION = "NEW_VERSION"
        const val UPDATE = "UPDATE"

        const val DEFAULT_COMPONENT_TYPE = "SONDAJE"
        private const val SOURCE_CRS = "source_crs"

        // sin zona ni working_crs (un punto fuera de 17S a 19S con un CRS de origen en grados): la zona central del Perú
        private const val DEFAULT_SRID = 32718

        // los datos de origen: se guardan como procedencia, pero no cuentan para «sin cambio»
        private val PROVENANCE = setOf("source_easting", "source_northing")

        // los datos de origen se leen de las columnas lógicas del mapeo
        private val SOURCE_COLUMNS = mapOf("source_elevation" to "elevation", "source_easting" to "easting", "source_northing" to "northing")

        private val TARGETS =
            mapOf(
                COMPONENT to
                    Target(
                        COMPONENT,
                        mapOf("Point" to ("location" to "Point"), "LineString" to ("trace" to "LineString"), "Polygon" to ("footprint" to "Polygon")),
                        listOf(
                            "component_type",
                            "drill_type",
                            "component_status",
                            "azimuth",
                            "dip",
                            "depth_m",
                            "source_elevation",
                            "source_easting",
                            "source_northing"
                        ),
                        tables = true
                    ),
                PARCEL to Target(PARCEL, mapOf("Polygon" to ("boundary" to "Polygon")), listOf("tenure_type", "registered_area_ha"), tables = false),
                CONCESSION to Target(CONCESSION, mapOf("Polygon" to ("boundary" to "MultiPolygon")), listOf("name", "registered_area_ha"), tables = false),
                COMMUNITY to
                    Target(
                        COMMUNITY,
                        mapOf("Polygon" to ("territory" to "MultiPolygon")),
                        listOf("name"),
                        tables = false,
                        required = setOf("name")
                    ),
                FEATURE to
                    Target(
                        FEATURE,
                        mapOf("Polygon" to ("boundary" to "MultiPolygon")),
                        listOf("name", "feature_type"),
                        tables = false,
                        required = setOf("name", "feature_type"),
                        writesArea = false
                    )
            )
    }
}
