package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

// el par <rol>_user_id / <rol>_user_email se resuelve dentro de la organización de la escritura: el mismo correo en otro tenant
// no confunde ni rompe (antes findByEmail daba un 500 con el correo repetido)
class UserReferenceGuardTest {
    private val mine = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private val ana = UUID.randomUUID()
    private val anaElsewhere = UUID.randomUUID()

    // el mismo correo en dos organizaciones, cada uno con su id
    private val users =
        OrganizationUsers { organization, email ->
            when {
                email != "ana@sgspe.test" -> null
                organization == mine -> ana
                organization == other -> anaElsewhere
                else -> null
            }
        }

    private fun check(
        id: String?,
        email: String?,
        organization: UUID = mine
    ) = runBlocking { UserReferenceGuard.judge(users, "member", id, email, organization) }

    @Test
    fun `el correo repetido en otro tenant se resuelve en el de la escritura`() {
        assertNull(check(ana.toString(), "ana@sgspe.test", mine))
        assertNull(check(anaElsewhere.toString(), "ana@sgspe.test", other))
        // el id del otro tenant no vale en este
        val wrong = check(anaElsewhere.toString(), "ana@sgspe.test", mine)!!
        assertEquals(HttpStatus.BAD_REQUEST, wrong.status)
        assertEquals("member_user_id", wrong.field)
        assertEquals("USER_REFERENCE", wrong.code)
    }

    @Test
    fun `un correo desconocido, un par a medias y un par vacio`() {
        assertEquals("member_user_id", check(ana.toString(), "otra@sgspe.test")!!.field)
        assertEquals("member_user_email", check(ana.toString(), null)!!.field)
        assertEquals("member_user_id", check(null, "ana@sgspe.test")!!.field)
        assertNull(check(null, null))
        assertEquals("member_user_id", check(ana.toString(), "ana@sgspe.test", UUID.randomUUID())!!.field)
    }
}
