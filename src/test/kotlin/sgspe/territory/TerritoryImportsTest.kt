package sgspe.territory

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.codec.DecodingException
import org.springframework.core.io.buffer.DataBufferLimitException
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpStatus
import wasichai.core.common.WasichaiException
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Duration
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

// las piezas de la importación que no necesitan base (gis.md §4, api.md §4.2, RQ-SEC-020): las decisiones por fila, la
// zona y los rangos, el tope de importaciones simultáneas y el plazo con cancelación, la subida acotada y el reporte XLSX
class TerritoryImportsTest {
    private val properties = ImportProperties()

    private fun codeOf(e: WasichaiException) = e.violations.single().let { it.field to it.message }

    @Test
    fun `sin decision una fila aplica su accion, salvo la fuera de zona, que solo entra con APPLY`() {
        val rows = mapOf(2 to false, 3 to true, 4 to false, 5 to true)
        assertEquals(setOf(2, 4), ImportRules.decide(rows, emptyList()))
        val decided = ImportRules.decide(rows, listOf(RowDecision(3, "APPLY"), RowDecision(4, "DISCARD"), RowDecision(5, "DISCARD")))
        assertEquals(setOf(2, 3), decided)
    }

    @Test
    fun `una decision con otra accion, repetida o de una fila que no es valida es 400 sobre decisions`() {
        val rows = mapOf(2 to false, 3 to true)
        for (decisions in listOf(
            listOf(RowDecision(2, "apply")),
            listOf(RowDecision(2, "SKIP")),
            listOf(RowDecision(2, "APPLY"), RowDecision(2, "DISCARD")),
            listOf(RowDecision(9, "APPLY"))
        )) {
            val e = assertThrows(WasichaiException::class.java) { ImportRules.decide(rows, decisions) }
            assertEquals(HttpStatus.BAD_REQUEST, e.status)
            assertEquals(listOf("decisions"), e.violations.map { it.field }, decisions.toString())
        }
        // antes del análisis solo se mira la forma: la fila 9 todavía no se sabe si existe
        ImportRules.checkShape(listOf(RowDecision(9, "APPLY")))
        assertThrows(WasichaiException::class.java) { ImportRules.checkShape(listOf(RowDecision(9, "KEEP"))) }
    }

    @Test
    fun `fuera de la zona del CRS declarado o de la envolvente del Peru se marca, dentro no`() {
        // EPSG 32717 declara la zona 17S; -76.8 cae en la 18S
        assertEquals(null, ImportRules.outOfZone(-78.6 to -7.9, 32717, properties))
        assertTrue(ImportRules.outOfZone(-76.8 to -7.9, 32717, properties)!!.contains("18S"))
        // PSAD56 17S declara la misma zona
        assertEquals(null, ImportRules.outOfZone(-78.6 to -7.9, 24877, properties))
        // EPSG 4326 no declara zona: solo la envolvente
        assertEquals(null, ImportRules.outOfZone(-76.8 to -7.9, 4326, properties))
        assertTrue(ImportRules.outOfZone(-60.0 to -7.9, 4326, properties)!!.contains("envolvente"))
        assertTrue(ImportRules.outOfZone(null, 32717, properties)!!.isNotBlank())
    }

    @Test
    fun `los numeros de los atributos tienen rangos finitos`() {
        assertNull(ImportRules.numberIssue("azimuth", BigDecimal("359.9")))
        assertTrue(ImportRules.numberIssue("azimuth", BigDecimal("361")) != null)
        assertTrue(ImportRules.numberIssue("dip", BigDecimal("-91")) != null)
        assertNull(ImportRules.numberIssue("dip", BigDecimal("-90")))
        assertTrue(ImportRules.numberIssue("depth_m", BigDecimal("-1")) != null)
        assertTrue(ImportRules.numberIssue("source_elevation", BigDecimal("1e12")) != null)
        assertTrue(ImportRules.numberIssue("registered_area_ha", BigDecimal("-0.5")) != null)
        assertNull(ImportRules.numberIssue("registered_area_ha", BigDecimal("1234.56")))
        // un campo sin rango propio no se juzga aquí
        assertNull(ImportRules.numberIssue("source_easting", BigDecimal("812345")))
    }

    @Test
    fun `una geometria que PostGIS no procesa es una fila invalida con su motivo en espanol`() {
        val reason = ImportRules.unprocessable(InvalidGeometryException("transform: Point outside of projection domain (2050)"))
        assertTrue(reason.startsWith("geometría inválida: "), reason)
        assertFalse(reason.contains("outside"), reason)
        assertTrue(ImportRules.unprocessable(IllegalArgumentException("SRID 1 fuera del catálogo de CRS")).startsWith("geometría inválida: "))
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `una lectura que pasa el plazo se corta interrumpiendo el hilo y es 400 IMPORT_TIMEOUT sobre file`() {
        val gate = ImportGate(2, Duration.ofMillis(200))
        val interrupted = AtomicBoolean(false)
        val e =
            assertThrows(WasichaiException::class.java) {
                runBlocking {
                    gate.timed {
                        ImportGate.blocking {
                            try {
                                Thread.sleep(30_000)
                            } catch (ex: InterruptedException) {
                                interrupted.set(true)
                                throw ex
                            }
                        }
                    }
                }
            }
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
        assertEquals("file" to "IMPORT_TIMEOUT", codeOf(e))
        assertTrue(e.message!!.contains("tardó demasiado"), e.message)
        assertTrue(interrupted.get(), "el hilo bloqueado no se interrumpió")
        // dentro del plazo devuelve lo que calcula
        assertEquals(42, runBlocking { gate.timed { ImportGate.blocking { 42 } } })
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `mas importaciones simultaneas que el tope esperan y, pasado el plazo, son 503 IMPORT_BUSY`() {
        val gate = ImportGate(1, Duration.ofMillis(200))
        val release = CompletableDeferred<Unit>()
        runBlocking {
            val first = async { gate.admit { release.await() } }
            // la primera toma el permiso y queda esperando
            yield()
            val e = runCatching { gate.admit { "segunda" } }.exceptionOrNull()
            assertTrue(e is WasichaiException, e.toString())
            e as WasichaiException
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.status)
            assertEquals("file" to "IMPORT_BUSY", codeOf(e))
            release.complete(Unit)
            first.await()
            // el permiso vuelve al terminar, también si la primera falla
            assertEquals("tercera", gate.admit { "tercera" })
            assertTrue(runCatching { gate.admit { error("falla") } }.exceptionOrNull() is IllegalStateException)
            assertEquals("cuarta", gate.admit { "cuarta" })
        }
    }

    // revisión de la Tarea 18, menor 1: el plazo de la espera puede vencer cuando acquire ya obtuvo el permiso, y
    // withTimeoutOrNull devuelve null igual. el gancho reproduce esa ventana: obtiene el permiso y sigue, sin poder
    // cancelarse, hasta después del plazo. el permiso debe usarse y volver, nunca perderse
    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `un permiso obtenido justo cuando vence la espera se usa y vuelve, no se pierde`() {
        var late = true
        val gate =
            ImportGate(1, Duration.ofMillis(100)) {
                if (late) {
                    late = false
                    withContext(NonCancellable) { delay(400) }
                }
            }
        runBlocking {
            assertEquals("primera", gate.admit { "primera" })
            // con un solo permiso, la segunda solo entra si la primera lo devolvió
            assertEquals("segunda", gate.admit { "segunda" })
        }
    }

    // la confirmación compara, ya con el candado, lo registrado con lo que analizó (preflight I5): id y updatedAt por código
    @Test
    fun `lo registrado que cambio entre el analisis y el candado es un conflicto`() {
        val t0 = java.time.Instant.parse("2026-10-08T10:00:00Z")
        val analyzed = mapOf("TST-1" to ("a" to t0), "TST-2" to null)
        assertNull(ImportRules.conflict(analyzed, mapOf("TST-1" to ("a" to t0))))
        // otro escribió TST-1 después del análisis
        assertEquals("TST-1", ImportRules.conflict(analyzed, mapOf("TST-1" to ("a" to t0.plusSeconds(1)))))
        // otro creó TST-2, que el análisis vio como nuevo
        assertEquals("TST-2", ImportRules.conflict(analyzed, mapOf("TST-1" to ("a" to t0), "TST-2" to ("b" to t0))))
        // otro borró TST-1
        assertEquals("TST-1", ImportRules.conflict(analyzed, emptyMap()))
    }

    // revisión de la Tarea 18, menores 5 y 6: los límites del multipart de Spring son 400 con código, nunca 500
    @Test
    fun `los limites del multipart de Spring son 400 con codigo y nombran el tipo de parte`() {
        val file = ImportUpload.multipartError(DataBufferLimitException("Part exceeded the disk usage limit of 26214400 bytes"), 20L * 1024 * 1024)
        assertEquals(HttpStatus.BAD_REQUEST, file!!.status)
        assertEquals("file" to "FILE_TOO_LARGE", codeOf(file))
        val field =
            ImportUpload.multipartError(
                RuntimeException(DataBufferLimitException("Form field value exceeded the memory usage limit of 1048576 bytes")),
                1024
            )
        assertEquals("multipart" to "PART_TOO_LARGE", codeOf(field!!))
        assertTrue(field.message!!.contains("decisions"), field.message)
        // los encabezados de una parte que pasan su tope no son un archivo grande: PART_TOO_LARGE
        val headers = ImportUpload.multipartError(DataBufferLimitException("Part headers exceeded the memory usage limit of 10240 bytes"), 1024)
        assertEquals("multipart" to "PART_TOO_LARGE", codeOf(headers!!))
        // una cadena de causas con un ciclo de dos termina y no la confunde con un límite
        val a = RuntimeException("a")
        val b = RuntimeException("b", a)
        a.initCause(b)
        assertNull(ImportUpload.multipartError(a, 1024))
        val parts = ImportUpload.multipartError(DecodingException("Too many parts (8 allowed)"), 1024)
        assertEquals(HttpStatus.BAD_REQUEST, parts!!.status)
        assertEquals("multipart" to "INVALID_MULTIPART", codeOf(parts))
        assertNull(ImportUpload.multipartError(IllegalStateException("otra cosa"), 1024))
    }

    @Test
    fun `la subida cuenta los bytes, calcula el SHA-256 y pasado el maximo es 400 FILE_TOO_LARGE sobre file`(
        @TempDir dir: File
    ) {
        val factory = DefaultDataBufferFactory()
        val parts = listOf("codigo;este\n".toByteArray(), "TST-1;812345\n".toByteArray())
        val target = File(dir, "ok.csv")
        val sha = runBlocking { ImportUpload.receive(flowOf(*parts.map { factory.wrap(it) }.toTypedArray()), target, 1024) }
        val all = parts.reduce { a, b -> a + b }
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all)), sha)
        assertEquals(all.toList(), target.readBytes().toList())
        val big = File(dir, "grande.csv")
        val e =
            assertThrows(WasichaiException::class.java) {
                runBlocking { ImportUpload.receive(flowOf(*parts.map { factory.wrap(it) }.toTypedArray()), big, 20) }
            }
        assertEquals(HttpStatus.BAD_REQUEST, e.status)
        assertEquals("file" to "FILE_TOO_LARGE", codeOf(e))
        assertTrue(e.message!!.contains("demasiado grande"), e.message)
    }

    @Test
    fun `el reporte de diferencias neutraliza las formulas en cada celda de texto`() {
        val preview =
            ImportPreview(
                importBatchId = UUID.randomUUID(),
                sha256 = "00",
                target = "project_component",
                sourceCrs = "EPSG 32717",
                rows = 4,
                valid = 2,
                invalid = listOf(InvalidRow(4, "=campo", "@motivo")),
                differences =
                    listOf(
                        Difference(2, "=HYPERLINK(\"http://x\")", "CREATE", newEasting = BigDecimal("812345.000")),
                        Difference(3, "-TST-2", "NEW_VERSION", currentVersion = 1, distanceM = BigDecimal("216.000"))
                    ),
                outOfZone = listOf(OutOfZoneRow(3, "+TST", "cae en la zona UTM 18S"))
            )
        XSSFWorkbook(ByteArrayInputStream(DifferencesReport.xlsx(preview))).use { book ->
            val differences = book.getSheet("Diferencias")
            assertEquals("'=HYPERLINK(\"http://x\")", differences.getRow(1).getCell(1).stringCellValue)
            assertEquals(812345.0, differences.getRow(1).getCell(7).numericCellValue)
            assertEquals("'-TST-2", differences.getRow(2).getCell(1).stringCellValue)
            assertEquals(216.0, differences.getRow(2).getCell(4).numericCellValue)
            val rejected = book.getSheet("Rechazadas")
            assertEquals("'=campo", rejected.getRow(1).getCell(1).stringCellValue)
            assertEquals("'@motivo", rejected.getRow(1).getCell(2).stringCellValue)
            val zone = book.getSheet("Fuera de zona")
            assertEquals("'+TST", zone.getRow(1).getCell(1).stringCellValue)
            for (sheet in book) for (row in sheet) for (cell in row) assertTrue(cell.cellType != org.apache.poi.ss.usermodel.CellType.FORMULA)
        }
    }

    // especificación de F6 §3.4: el alta de una comunidad o de un elemento ambiental por importación lleva el nombre y, el
    // elemento, su tipo (columna elegida o valor fijo); sin ellos la fila se rechaza nombrando el campo
    @Test
    fun `los atributos obligatorios de un destino que faltan se nombran en orden`() {
        assertEquals(emptyList<String>(), ImportRules.missingRequired(setOf("name"), mapOf("code" to "COM-01", "name" to "Comunidad Ejemplo")))
        assertEquals(listOf("feature_type", "name"), ImportRules.missingRequired(setOf("name", "feature_type"), mapOf("code" to "AMB-01", "name" to null)))
    }

    // un ENUM inválido ya se rechazó con «valor inválido»: no se repite como «es obligatorio»
    @Test
    fun `un obligatorio rechazado por su valor no se nombra otra vez como faltante`() {
        assertEquals(
            listOf("name"),
            ImportRules.missingRequired(setOf("name", "feature_type"), mapOf("code" to "AMB-01"), rejected = setOf("feature_type"))
        )
    }

    // un tenant de F5 no tiene community.code ni environmental_feature.code: pedir esos destinos es 400 sobre target
    @Test
    fun `un tenant de F5 que pide community o environmental_feature es 400 en target`() {
        for (target in listOf("community", "environmental_feature")) {
            val e = assertThrows(WasichaiException::class.java) { ImportRules.requireModel(target, hasCode = false) }
            assertEquals(HttpStatus.BAD_REQUEST, e.status)
            assertEquals("target", e.violations.single().field)
            ImportRules.requireModel(target, hasCode = true)
        }
        // los destinos de F2 no dependen del modelo de F6
        ImportRules.requireModel("land_parcel", hasCode = false)
        assertEquals(setOf("community", "environmental_feature"), ImportRules.SINCE_F6)
    }
}
