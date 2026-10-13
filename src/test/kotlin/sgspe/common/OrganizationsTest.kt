package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import wasichai.core.platform.TenantDirectory
import wasichai.core.platform.TenantRef
import java.util.UUID

// las organizaciones de un trabajo de fondo: las que tienen el objeto y, para JOB-07, solo las que activaron la IA por su slug
class OrganizationsTest {
    private val demo = TenantRef(UUID.fromString("00000000-0000-0000-0000-00000000000a"), "demo")
    private val other = TenantRef(UUID.fromString("00000000-0000-0000-0000-00000000000b"), "otra")

    private val tenants =
        object : TenantDirectory {
            override suspend fun organizations(): List<TenantRef> = listOf(demo, other)

            override suspend fun organizationsWithObject(objectName: String): List<TenantRef> =
                if (objectName ==
                    "evidence"
                ) {
                    listOf(demo, other)
                } else {
                    emptyList()
                }
        }

    @Test
    fun `las que tienen el objeto y cuyo slug se admite`() {
        val organizations = Organizations(tenants)
        assertEquals(listOf(demo.id, other.id), runBlocking { organizations.having("evidence") })
        assertEquals(listOf(demo.id), runBlocking { organizations.having("evidence") { it == "demo" } })
        assertEquals(emptyList<UUID>(), runBlocking { organizations.having("deliverable") { true } })
    }
}
