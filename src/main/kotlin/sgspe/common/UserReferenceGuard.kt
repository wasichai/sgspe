package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.UserRepository

// los pares <rol>_user_id y <rol>_user_email (ASM-023): wasichai no tiene relaciones con usuarios, así que cuando un par
// viene informado tiene que nombrar a un usuario real de la organización, con su correo. un par vacío no se valida (una
// interacción de IA que corre como la plataforma). se juzgan solo los pares que la escritura cambia: un usuario dado de
// baja después no bloquea los demás cambios del registro
@Component
@Order(GuardOrder.USER_REFERENCE)
class UserReferenceGuard(
    private val view: WriteView,
    private val users: UserRepository
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
            .mapNotNull { prefix -> check(prefix, Values.text(after["$prefix$ID"]), Values.text(after["$prefix$EMAIL"]), change) }
    }

    private suspend fun check(
        prefix: String,
        id: String?,
        email: String?,
        change: RecordWrite
    ): Finding? {
        if (id == null && email == null) return null
        if (id == null || email == null) {
            return finding("${prefix}_user_${if (id == null) "id" else "email"}", "Informe juntos el id y el correo del usuario ($prefix)")
        }
        val user = users.findByEmail(email)
        if (user == null || user.organizationId != change.organizationId || user.id.toString() != id) {
            return finding("$prefix$ID", "El usuario $prefix no es un usuario de la organización con ese correo")
        }
        return null
    }

    private fun finding(
        field: String,
        message: String
    ) = Finding(code = "USER_REFERENCE", message = message, field = field, status = HttpStatus.BAD_REQUEST)

    companion object {
        private const val ID = "_user_id"
        private const val EMAIL = "_user_email"
    }
}
