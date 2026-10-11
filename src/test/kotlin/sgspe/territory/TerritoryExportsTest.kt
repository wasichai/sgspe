package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

// las partes puras de las exportaciones geográficas: el CSV (RFC 4180, BOM, fórmulas neutralizadas) y el mapa de fallos de GDAL
class TerritoryExportsTest {
    @TempDir
    lateinit var dir: Path

    private fun csv(vararg rows: List<Any?>): String {
        val out = dir.resolve("a.csv")
        Csv.write(listOf("id", "name"), rows.toList(), out)
        return Files.readString(out)
    }

    @Test
    fun `el CSV lleva BOM, coma y salto RFC 4180`() {
        val text = csv(listOf("1", "Predio, Ejemplo"), listOf("2", "dice \"hola\""))
        assertTrue(text.startsWith("﻿"))
        assertEquals("id,name\r\n1,\"Predio, Ejemplo\"\r\n2,\"dice \"\"hola\"\"\"\r\n", text.removePrefix("﻿"))
    }

    @Test
    fun `el CSV neutraliza fórmulas en el texto pero no en los números`() {
        val text = csv(listOf("=1+1", "@x"), listOf("+1", "-2"), listOf(BigDecimal("-12.50"), -3), listOf(null, "\tz")).removePrefix("﻿")
        assertEquals("id,name\r\n'=1+1,'@x\r\n'+1,'-2\r\n-12.50,-3\r\n,'\tz\r\n", text)
    }

    @Test
    fun `un fallo de GDAL es un 503 con codigo y sin detalles internos`() {
        val e = TerritoryExports.gdalFailure(IllegalStateException("GDAL no pudo escribir shp /tmp/secreto"))
        assertEquals(503, e.status.value())
        assertEquals("EXPORT_FAILED", e.violations.single().message)
        assertTrue(e.violations.single().field == "format")
        assertTrue(e.violations.none { "secreto" in it.message })
    }
}
