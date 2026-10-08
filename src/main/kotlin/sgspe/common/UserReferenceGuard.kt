package sgspe.common

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.platform.WasichaiSchemas
import java.util.UUID

// los pares <rol>_user_id y <rol>_user_email (ASM-023): wasichai no tiene relaciones con usuarios, así que cuando un par
// viene informado tiene que nombrar a un usuario real de la organización, con su correo. un par vacío no se valida (una
// interacción de IA que corre como la plataforma). se juzgan solo los pares que la escritura cambia: un usuario dado de
// baja después no bloquea los demás cambios del registro. el correo se busca dentro de la organización de la escritura
// (OrganizationUsers): el mismo correo puede existir en dos tenants, y buscarlo sin organización daría un 500
@Component
@Order(GuardOrder.USER_REFERENCE)
class UserReferenceGuard(
    private val view: WriteView,
    private val users: OrganizationUsers
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val changed = view.changed(change)
        val prefixes =
            after.keys
                .mapNotNull { key ->
                    when {
                        key.endsWith(ID) -> key.removeSuffix(ID)
                        key.endsWith(EMAIL) -> key.removeSuffix(EMAIL)
                        else -> null
                    }
                }.toSet()
        return prefixes
            .filter { "$it$ID" in changed || "$it$EMAIL" in changed }
            .mapNotNull { prefix ->
                judge(users, prefix, Values.text(after["$prefix$ID"]), Values.text(after["$prefix$EMAIL"]), change.organizationId)
            }
    }

    companion object {
        private const val ID = "_user_id"
        private const val EMAIL = "_user_email"

        // un par informado a medias, o que no nombra a un usuario de la organización con ese correo, es un 400
        suspend fun judge(
            users: OrganizationUsers,
            prefix: String,
            id: String?,
            email: String?,
            organizationId: UUID
        ): Finding? {
            if (id == null && email == null) return null
            if (id == null || email == null) {
                return finding("${prefix}_user_${if (id == null) "id" else "email"}", "Informe juntos el id y el correo del usuario ($prefix)")
            }
            if (users.idOf(organizationId, email)?.toString() != id) {
                return finding("$prefix$ID", "El usuario $prefix no es un usuario de la organización con ese correo")
            }
            return null
        }

        private fun finding(
            field: String,
            message: String
        ) = Finding(code = "USER_REFERENCE", message = message, field = field, status = HttpStatus.BAD_REQUEST)
    }
}

// el usuario de una organización por su correo. UserRepository.findByEmail de wasichai no filtra por organización y falla
// con más de una fila, así que aquí se consulta la tabla de usuarios de la plataforma (de solo lectura) con la organización
// y el correo enlazados
fun interface OrganizationUsers {
    suspend fun idOf(
        organizationId: UUID,
        email: String
    ): UUID?
}

@Component
class DatabaseOrganizationUsers(
    private val db: DatabaseClient,
    private val schemas: WasichaiSchemas
) : OrganizationUsers {
    override suspend fun idOf(
        organizationId: UUID,
        email: String
    ): UUID? =
        db
            .sql("SELECT id FROM ${schemas.metadata}.users WHERE organization_id = :organizationId AND email = :email LIMIT 1")
            .bind("organizationId", organizationId)
            .bind("email", email)
            .map { row, _ -> row.get("id", UUID::class.java)!! }
            .first()
            .awaitSingleOrNull()
}
