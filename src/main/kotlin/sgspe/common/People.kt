package sgspe.common

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException
import wasichai.core.platform.WasichaiSchemas
import java.util.UUID

// una persona de la organización por su correo, con su nombre visible
data class PersonName(
    val email: String,
    val displayName: String
)

// los nombres visibles de los usuarios de una organización por su correo, sin distinguir mayúsculas. lee la tabla de usuarios
// de la plataforma (de solo lectura) con la organización enlazada, como DatabaseOrganizationUsers: un correo de otro tenant no
// sale. incluye a los usuarios deshabilitados: un registro viejo sigue nombrando a quien lo tuvo a cargo
fun interface PeopleNames {
    suspend fun of(
        organizationId: UUID,
        emails: Collection<String>
    ): List<PersonName>
}

@Component
class DatabasePeopleNames(
    private val db: DatabaseClient,
    private val schemas: WasichaiSchemas
) : PeopleNames {
    override suspend fun of(
        organizationId: UUID,
        emails: Collection<String>
    ): List<PersonName> {
        if (emails.isEmpty()) return emptyList()
        return db
            .sql(
                "SELECT email, display_name FROM ${schemas.metadata}.users " +
                    "WHERE organization_id = :organizationId AND lower(email) = ANY(:emails) ORDER BY email"
            ).bind("organizationId", organizationId)
            .bind("emails", emails.map { it.lowercase() }.distinct().toTypedArray())
            .map { row, _ -> PersonName(row.get("email", String::class.java)!!, row.get("display_name", String::class.java)!!) }
            .all()
            .collectList()
            .awaitSingle()
    }
}

// GET /api/sgspe/people?email=…&email=… (api.md §4.1, ASM-142): el nombre visible de las personas de la organización de quien
// llama, por su correo. los registros guardan al responsable por su id y su correo (ASM-023) y el directorio de usuarios de
// wasichai exige MANAGE_ORGANIZATION, así que sin esto el portal solo podía mostrar el correo, que quien mira ya ve. basta con
// estar autenticado; un correo desconocido o de otro tenant no sale. hasta 100 correos por consulta
@RestController
@RequestMapping("/api/sgspe/people")
class PeopleController(
    private val names: PeopleNames,
    private val callers: Callers
) {
    @GetMapping
    suspend fun people(exchange: ServerWebExchange): List<PersonName> {
        val params = exchange.request.queryParams
        StrictQuery.requireOnly(params, setOf(EMAIL))
        val emails = params[EMAIL].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
        if (emails.size > MAX) throw TooManyEmailsException()
        val user = callers.require()
        return names.of(user.organizationId, emails)
    }

    companion object {
        private const val EMAIL = "email"
        const val MAX = 100
    }
}

class TooManyEmailsException :
    WasichaiException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Demasiados correos",
        listOf(FieldViolation("email", "se admiten hasta ${PeopleController.MAX} correos por consulta"))
    )
