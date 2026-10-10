package sgspe.contract

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.TestPropertySource
import sgspe.SgspeApiTest
import wasichai.core.platform.WasichaiSchemas
import java.util.UUID

// UPS-10 resuelta con wasichai 0.6.0 (WAS-ADR-0055, ASM-109): con wasichai.organizations.separate-provisioning encendido, como
// en application-prod.yml, crear y borrar tenants exige MANAGE_TENANTS. el ADMIN de la organización operadora (la sembrada
// por la prueba) la recibe fuera de banda, con la fila de permissions que siembra quien opera la base (docs/develop/README.md);
// el ADMIN de un tenant cliente conserva su tenant y recibe 403 en las dos rutas. la fila no cambia nada con el interruptor
// apagado (el valor de las demás clases), y se siembra una sola vez
@TestPropertySource(properties = ["wasichai.organizations.separate-provisioning=true"])
class SeparateProvisioningApiTest : SgspeApiTest() {
    @Autowired
    private lateinit var db: DatabaseClient

    @Autowired
    private lateinit var schemas: WasichaiSchemas

    // la siembra de docs/develop/README.md: MANAGE_TENANTS para el rol ADMIN de la organización operadora, sin objeto
    @BeforeEach
    fun seedOperator() {
        val organization = UUID.fromString(me()["organizationId"].asString())
        runBlocking {
            db
                .sql(
                    """
                    INSERT INTO ${schemas.metadata}.permissions (role_id, object_id, action)
                    SELECT r.id, NULL, 'MANAGE_TENANTS' FROM ${schemas.metadata}.roles r
                    WHERE r.organization_id = :organization AND r.name = 'ADMIN'
                      AND NOT EXISTS (SELECT 1 FROM ${schemas.metadata}.permissions p WHERE p.role_id = r.id AND p.action = 'MANAGE_TENANTS')
                    """.trimIndent()
                ).bind("organization", organization)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    @Test
    fun `el ADMIN de la operadora crea un tenant y el ADMIN del cliente no crea ni borra tenants`() {
        // provisionTenant pide 201 con el token del ADMIN de la operadora
        val client = provisionTenant()
        val body =
            mapOf(
                "name" to "Organización intrusa",
                "slug" to uniqueName("intrusa"),
                "adminEmail" to "${uniqueName("intrusa")}@sgspe.test",
                "adminPassword" to PASSWORD,
                "adminDisplayName" to "Intrusa"
            )
        assertEquals(HttpStatus.FORBIDDEN, exchange("POST", "/api/organizations", body, client.token).status)
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/organizations/current", null, client.token).status)
        // su tenant sigue ahí y lo administra: renombrarlo es MANAGE_ORGANIZATION
        assertEquals(HttpStatus.OK, exchange("PUT", "/api/organizations/current", mapOf("name" to "Cliente Ejemplo"), client.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/auth/me", null, client.token).status)
    }
}
