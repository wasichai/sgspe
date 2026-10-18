package sgspe.territory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import sgspe.common.LIMA
import sgspe.common.ObjectDefinitions
import sgspe.common.Records
import sgspe.common.TenantModel
import sgspe.common.Transactions
import wasichai.core.data.RecordResponse
import wasichai.files.ContentSniffer
import wasichai.files.FileService
import wasichai.files.FileUpload
import wasichai.files.contentTypes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// el archivo original de una importación, tal como llegó a la confirmación
class ImportOriginal(
    val fileName: String,
    val bytes: ByteArray,
    val format: ImportFormat,
    val sha256: String,
    val batchId: UUID,
    // el objeto destino (project_component, land_parcel…)
    val target: String
)

// lo que se sube a evidence.file: el original o, si el campo no lo admite, un PDF que lo lleva adjunto (wrapped)
class EvidenceUpload(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
    val wrapped: Boolean
)

// ASM-060, sin base: cómo se guarda el original de una importación en evidence.file. el tipo lo decide wasichai-files por los
// bytes (ContentSniffer) contra la lista contentTypes del campo, que se fija al crearlo (wasichai 1.3.0 no la cambia en un
// tenant ya aprovisionado). lo que el campo admite (CSV y GeoJSON, KML y GPX en UTF-8 como texto, y el XLSX) va tal cual, con la
// misma huella que la vista previa. lo demás (el ZIP de un Shapefile o de una FileGDB, el KMZ, el GeoPackage, un CSV que no es
// UTF-8) sale como application/zip o application/octet-stream, que evidence.file no admite: abrir la lista a esos tipos
// dejaría subir cualquier binario como evidencia. ese original va adjunto, byte a byte, en un PDF con una portada que dice el
// lote, el destino, el formato, el tamaño y su SHA-256 (el de la vista previa). ni el título ni la portada llevan el nombre del
// archivo (lo escribe quien sube y puede nombrar a una persona, y JOB-07 indexa el texto de un PDF); el nombre queda en el
// descriptor del archivo y en el adjunto, como en source_reference. un ZIP solo llega aquí con las entradas de su formato
// (ImportFormat.detect)
object ImportEvidenceFiles {
    const val PDF = "application/pdf"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val TEXT = "text/plain"
    private const val ZIP = "application/zip"

    private val LABELS =
        mapOf(
            "project_component" to "componentes",
            "land_parcel" to "predios",
            "mining_concession" to "concesiones mineras",
            "community" to "comunidades",
            "environmental_feature" to "elementos ambientales"
        )

    // el tipo que se declara al subir: wasichai-files solo le cree el texto y los formatos de oficina en ZIP, y solo si los
    // bytes coinciden (ContentSniffer)
    fun declaredType(format: ImportFormat): String =
        when (format) {
            ImportFormat.XLSX -> XLSX
            ImportFormat.CSV -> "text/csv"
            ImportFormat.GEOJSON, ImportFormat.KML, ImportFormat.GPX -> TEXT
            ImportFormat.SHP, ImportFormat.KMZ, ImportFormat.GDB -> ZIP
            ImportFormat.GPKG -> ContentSniffer.OCTET_STREAM
        }

    fun label(target: String): String = LABELS[target] ?: target

    // el original trae todas las columnas del archivo, no solo las que se importan: el padrón de predios, comunidades,
    // concesiones o elementos ambientales suele traer titulares, DNI y partidas registrales, que ningún permiso de campo
    // alcanza en un archivo opaco. por eso es DATOS PERSONALES (su descarga exige DOWNLOAD_RESTRICTED, RF-DOC-003, y el
    // descriptor sale sin nombre ni huella para quien no la tiene, EvidenceFileReadPolicy). una hoja de componentes (sondajes,
    // plataformas) no trae personas: INTERNA
    fun classification(target: String): String = if (target == ComponentVersions.COMPONENT) INTERNAL else PERSONAL_DATA

    const val INTERNAL = "INTERNA"
    const val PERSONAL_DATA = "DATOS PERSONALES"

    fun title(
        target: String,
        date: LocalDate
    ): String = "Importación de ${label(target)} del $date"

    // `allowList`: los contentTypes de evidence.file en el tenant (null: todo tipo)
    fun pack(
        original: ImportOriginal,
        allowList: List<String>?
    ): EvidenceUpload {
        val declared = declaredType(original.format)
        val sniffed = ContentSniffer.detect(original.bytes, declared)
        if (ContentSniffer.allowed(sniffed, allowList)) return EvidenceUpload(original.fileName, sniffed, original.bytes, false)
        return EvidenceUpload("${original.fileName}.pdf", PDF, envelope(original, declared), true)
    }

    // el nombre y los bytes del archivo adjunto a un PDF de `pack` (para las pruebas y para quien audita)
    fun embedded(pdf: ByteArray): Pair<String, ByteArray> =
        Loader.loadPDF(pdf).use { document ->
            val (name, spec) =
                document.documentCatalog.names.embeddedFiles.names.entries
                    .single()
            val file = spec.embeddedFileUnicode ?: spec.embeddedFile
            (spec.fileUnicode ?: name) to file.toByteArray()
        }

    private fun envelope(
        original: ImportOriginal,
        declared: String
    ): ByteArray =
        PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val lines =
                listOf(
                    "Archivo original de una importación de SGSPE (ASM-060)",
                    "",
                    "Lote: ${original.batchId}",
                    "Destino: ${label(original.target)} (${original.target})",
                    "Formato: ${original.format.key}",
                    "Tamaño: ${original.bytes.size} bytes",
                    "SHA-256: ${original.sha256}",
                    "",
                    "El archivo va adjunto a este PDF tal como se subió, byte a byte: ábralo desde los",
                    "adjuntos del lector. Su SHA-256 es el de la vista previa de la importación."
                )
            PDPageContentStream(document, page).use { content ->
                content.beginText()
                content.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE)
                content.newLineAtOffset(MARGIN, page.mediaBox.height - MARGIN)
                for (line in lines) {
                    content.showText(line)
                    content.newLineAtOffset(0f, -LEADING)
                }
                content.endText()
            }
            val file =
                PDEmbeddedFile(document, ByteArrayInputStream(original.bytes)).apply {
                    subtype = declared
                    size = original.bytes.size
                }
            val spec =
                PDComplexFileSpecification().apply {
                    this.file = winAnsi(original.fileName)
                    fileUnicode = original.fileName
                    embeddedFile = file
                    embeddedFileUnicode = file
                    fileDescription = "Archivo original del lote ${original.batchId}"
                }
            val tree = PDEmbeddedFilesNameTreeNode().apply { names = mapOf(original.fileName to spec) }
            document.documentCatalog.names = PDDocumentNameDictionary(document.documentCatalog).apply { embeddedFiles = tree }
            ByteArrayOutputStream().also { document.save(it) }.toByteArray()
        }

    // Helvetica de las 14 estándar escribe WinAnsi: lo demás (y todo control) sale como «?», sin romper el PDF
    private fun winAnsi(text: String): String = text.map { if (it in ' '..'~' || it in '¡'..'ÿ') it else '?' }.joinToString("")

    private const val FONT_SIZE = 11f
    private const val LEADING = 16f
    private const val MARGIN = 56f
}

// ASM-060 con base: el original de una importación confirmada como evidence, creada como quien confirma. solo en un tenant con
// el modelo de F3 (evidence.import_batch_id); en uno de F2, la confirmación responde evidenceId null como antes.
// el archivo se sube (FileService.stage, como la persona: wasichai-files no deja adjuntar como la plataforma lo que subió una
// persona, ni al revés) en una transacción propia antes de la de la confirmación, como las copias de la emisión de un
// entregable: los bytes van al almacén fuera de toda transacción, así que una subida dentro de la de los registros dejaría
// bytes sin fila si esa se revierte. el alta de la evidencia va en la misma transacción que los registros importados: o se
// confirman juntos o ninguno, y si se revierte, la subida queda sin adjuntar y la borra StoredFileCleanup de wasichai-files
// pasado wasichai.files.cleanup.delay
@Component
class ImportEvidence(
    private val records: Records,
    private val files: FileService,
    private val definitions: ObjectDefinitions,
    private val tenantModel: TenantModel,
    private val transactions: Transactions,
    private val clock: Clock
) {
    suspend fun enabled(organizationId: UUID): Boolean = tenantModel.hasField(organizationId, EVIDENCE, BATCH)

    // la evidencia de un lote, como la lee quien confirma
    suspend fun of(batchId: UUID): RecordResponse? = records.all(EVIDENCE, filters = mapOf(BATCH to batchId.toString())).firstOrNull()

    // sube el original como quien confirma y devuelve el id del archivo, todavía sin adjuntar
    suspend fun stage(
        organizationId: UUID,
        file: File,
        fileName: String,
        format: ImportFormat,
        sha256: String,
        batchId: UUID,
        target: String
    ): String {
        val allowList =
            definitions
                .current(organizationId, EVIDENCE)
                .fields
                .first { it.name == FILE }
                .contentTypes
        val upload =
            withContext(Dispatchers.IO) {
                ImportEvidenceFiles.pack(ImportOriginal(fileName, file.readBytes(), format, sha256, batchId, target), allowList)
            }
        return transactions
            .independent {
                files.stage(
                    EVIDENCE,
                    FILE,
                    FileUpload(upload.fileName, upload.contentType, Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(upload.bytes)))
                )
            }.id
            .toString()
    }

    // el alta, dentro de la transacción de la confirmación. `project`: el de la importación si sus registros son del proyecto
    // (componentes y concesiones, que ya exigieron la asignación); los predios, las comunidades y los elementos ambientales son
    // del tenant, y su evidencia también (sin project, ProjectScopeGuard no pide una asignación que su importación no pide)
    suspend fun create(
        fileId: String,
        batchId: UUID,
        target: String,
        project: UUID?,
        reason: String
    ): RecordResponse {
        // una sola lectura del reloj: el título y la fecha del documento no caen en días distintos a medianoche
        val today = LocalDate.ofInstant(clock.instant(), LIMA)
        return records.create(
            EVIDENCE,
            mapOf(
                FILE to fileId,
                "evidence_type" to TYPE,
                "classification" to ImportEvidenceFiles.classification(target),
                "title" to ImportEvidenceFiles.title(target, today),
                "document_date" to today.toString(),
                BATCH to batchId.toString()
            ) + listOfNotNull(project?.let { "project" to it.toString() }).toMap(),
            reason
        )
    }

    companion object {
        const val EVIDENCE = "evidence"
        const val FILE = "file"
        const val BATCH = "import_batch_id"

        // todos los destinos son geográficos: el original es un mapa
        const val TYPE = "MAPA"

        // 409 IMPORT_BATCH_USED: el lote ya tiene su evidencia y esta confirmación escribiría registros nuevos con él
        fun batchUsed(batchId: UUID) =
            importError(
                "IMPORT_BATCH_USED",
                "importBatchId",
                "El lote $batchId ya se confirmó y tiene su archivo original: vuelva a previsualizar el archivo para un lote nuevo",
                HttpStatus.CONFLICT
            )
    }
}
