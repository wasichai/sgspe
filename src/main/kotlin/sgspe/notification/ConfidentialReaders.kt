package sgspe.notification

import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.scope.Assignment
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

// quién vería una QCS confidencial (security.md §9.6, ASM-102), para filtrar los destinatarios de sus avisos: su responsable,
// quien la recibió, los asignados vigentes del proyecto cuyo role_name tiene READ_CONFIDENTIAL sobre el objeto en
// model/roles.json y, en una queja ALTA o CRITICA, los que tienen APPROVE sobre grievance. decide con ModelCatalog.grants,
// nunca con nombres de rol. una QCS que no es confidencial la ven todos los que tienen READ: no filtra
@Component
class ConfidentialReaders(
    private val catalog: ModelCatalog
) {
    // los roles cuyos asignados la verían
    fun roles(
        objectName: String,
        attributes: Map<String, Any?>
    ): Set<String> {
        val roles = catalog.rolesGranting(objectName, Attention.READ_CONFIDENTIAL).toMutableSet()
        if (objectName == Attention.GRIEVANCE && attributes[Attention.SEVERITY]?.toString() in Attention.HIGH_SEVERITY) {
            roles += catalog.rolesGranting(Attention.GRIEVANCE, Attention.APPROVE)
        }
        return roles
    }

    // de `users`, los que la verían; null si no es confidencial (todos). `assignments`: las del proyecto, vigentes
    fun filter(
        objectName: String,
        attributes: Map<String, Any?>,
        users: Collection<UUID>,
        assignments: Collection<Assignment>,
        today: LocalDate
    ): List<UUID>? {
        if (!Attention.isConfidential(attributes)) return null
        val roles = roles(objectName, attributes)
        val readers =
            setOfNotNull(Records.uuid(attributes[Attention.OWNER]), Records.uuid(attributes[Attention.RECEIVER])) +
                assignments.filter { it.inForce(today) && it.roleName in roles }.map { it.memberUserId }
        return users.filter { it in readers }.distinct()
    }

    companion object {
        // la huella del aviso genérico: los 16 primeros dígitos hexadecimales del SHA-256 de «<objeto>:<id>:<fecha del plazo>»,
        // para que la clave no lleve el id, el tipo ni la fecha de la QCS
        fun fingerprint(
            objectName: String,
            id: String,
            due: LocalDate
        ): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest("$objectName:$id:$due".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
    }
}
