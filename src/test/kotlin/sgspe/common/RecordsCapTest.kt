package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.http.HttpStatus
import wasichai.core.common.PageResponse
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID

// el tope opcional de Records.all (dashboards.md §6.2): sin tope lee todas las páginas; con tope, pasarse es un 400
// TOO_MANY_RECORDS en cuanto se sabe, nunca una lista recortada. RecordService es un doble que da páginas de 200 por cursor
class RecordsCapTest {
    private val queries = mutableListOf<RecordQuery>()

    // tres páginas: 200, 200 y 50 filas
    private val service: RecordService =
        mock(RecordService::class.java) { invocation ->
            if (invocation.method.name != "list") return@mock null
            val query = invocation.arguments[1] as RecordQuery
            queries += query
            val page = query.after?.toInt() ?: 0
            val size = if (page < 2) 200 else 50
            val rows = (0 until size).map { RecordResponse(UUID.randomUUID().toString(), null, null, emptyMap()) }
            PageResponse.of(rows, 0, 200, null, if (page < 2) (page + 1).toString() else null)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val records =
        Records(
            provider(service),
            provider<RelatedRecordService>(null),
            provider<WorkflowService>(null),
            mock(MetadataService::class.java),
            mock(Callers::class.java)
        )

    @Test
    fun `sin tope lee todas las paginas`() {
        assertEquals(450, runBlocking { records.all("stakeholder") }.size)
        assertEquals(3, queries.size)
    }

    @Test
    fun `con tope alcanza justo y pasarse es TOO_MANY_RECORDS sin leer de mas`() {
        assertEquals(450, runBlocking { records.all("stakeholder", max = 450) }.size)
        queries.clear()
        val error = assertThrows<WasichaiException> { runBlocking { records.all("stakeholder", max = 300) } }
        assertEquals(HttpStatus.BAD_REQUEST.value(), error.status.value())
        assertEquals("TOO_MANY_RECORDS", error.violations.single().message)
        assertEquals("project", error.violations.single().field)
        assertEquals(2, queries.size)
    }
}
