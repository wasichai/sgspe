package sgspe.scope

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.ConfidentialRelease
import sgspe.common.Permissions
import sgspe.common.RequestMemo
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordReadScope
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import java.util.UUID

// lo que decide qué QCS confidenciales ve quien llama, puro (ASM-095, security.md §9.6): las columnas de la definición y las
// acciones de quien llama, sin base. los valores van siempre enlazados (bind), nunca concatenados
object ConfidentialCondition {
    // la condición que no admite ningún registro: falla cerrada
    const val NOTHING = "false"

    // las columnas que la condición necesita: el nombre del campo -> su columna, o null si la definición no lo trae
    val FIELDS: List<String> = listOf(Attention.CONFIDENTIAL, Attention.OWNER, Attention.RECEIVER)

    // null: no restringe (no es una QCS, o quien llama tiene READ_CONFIDENTIAL). si falta una columna, NOTHING. si no, la QCS
    // que no es confidencial, la de su responsable o la de quien la recibió y, en una queja con APPROVE, la ALTA o CRITICA
    fun condition(
        objectName: String,
        columns: Map<String, String?>,
        user: UUID,
        readConfidential: Boolean,
        approve: Boolean,
        bind: (Any) -> String
    ): String? {
        if (objectName !in Attention.OBJECTS || readConfidential) return null
        val confidential = columns[Attention.CONFIDENTIAL] ?: return NOTHING
        val owner = columns[Attention.OWNER] ?: return NOTHING
        val receiver = columns[Attention.RECEIVER] ?: return NOTHING
        val terms = mutableListOf("$confidential IS NOT TRUE", "$owner = ${bind(user)}", "$receiver = ${bind(user)}")
        if (objectName == Attention.GRIEVANCE && approve) {
            val severity = columns[Attention.SEVERITY] ?: return NOTHING
            terms += "$severity = ANY(${bind(Attention.HIGH_SEVERITY.sorted().toTypedArray())})"
        }
        return terms.joinToString(" OR ")
    }

    // la condición más los registros que libera una regla del sistema (ConfidentialRelease): los de `column` igual a `value`.
    // sin la columna en la definición, la misma condición
    fun released(
        condition: String,
        column: String?,
        value: UUID,
        bind: (Any) -> String
    ): String = if (column == null) condition else "($condition) OR $column = ${bind(value)}"
}

// el RecordReadScope de las QCS confidenciales (WAS-ADR-0048, ASM-095, security.md §9.6): wasichai lo pide para cada persona o
// cuenta de servicio que lee grievance, inquiry o stakeholder_request (nunca para ADMIN ni para la plataforma) y aplica la
// condición en listas, detalle, relacionados, historial, auditoría, transiciones, features GIS y agente; fuera de ella el
// registro no existe, así que tampoco se cambia ni se transiciona. las acciones de quien llama salen de Permissions, la misma
// consulta de permisos de sgspe, memorizada dentro de un comando (RequestMemo). falla cerrado: sin las columnas, o si la
// consulta de permisos falla, ningún registro del objeto, con una advertencia sin datos del registro
@Component
class ConfidentialAttentionScope(
    private val permissions: Permissions
) : RecordReadScope {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun criterion(
        caller: AuthenticatedUser,
        definition: ObjectDefinition
    ): RecordCriterion? {
        val objectName = definition.obj.name
        if (objectName !in Attention.OBJECTS) return null
        val columns = (ConfidentialCondition.FIELDS + Attention.SEVERITY).associateWith { field -> column(definition, field) }
        if (ConfidentialCondition.FIELDS.any { columns[it] == null }) {
            log.warn("ConfidentialAttentionScope: {} no trae {}; no se admite ningún registro", objectName, ConfidentialCondition.FIELDS)
            return NOTHING
        }
        val (readConfidential, approve) =
            try {
                RequestMemo.remember(MEMO to (caller.userId to objectName)) { held(caller, objectName) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("ConfidentialAttentionScope: no se leyeron los permisos de quien llama sobre {}; no se admite ningún registro", objectName, e)
                return NOTHING
            }
        if (readConfidential) return null
        // la regla del sistema que cierra una solicitud confidencial (RequestClosureListener, decisión 4 del plan 4.2a)
        val release = ConfidentialRelease.on(objectName)
        val releasedColumn = release?.let { column(definition, it.field) }
        return RecordCriterion { _, bind ->
            val condition =
                ConfidentialCondition.condition(objectName, columns, caller.userId, readConfidential = false, approve, bind) ?: ConfidentialCondition.NOTHING
            if (release == null) condition else ConfidentialCondition.released(condition, releasedColumn, release.value, bind)
        }
    }

    // READ_CONFIDENTIAL sobre el objeto y, en una queja, APPROVE
    private suspend fun held(
        caller: AuthenticatedUser,
        objectName: String
    ): Pair<Boolean, Boolean> {
        val readConfidential = permissions.has(caller, Attention.READ_CONFIDENTIAL, objectName)
        val approve = !readConfidential && objectName == Attention.GRIEVANCE && permissions.has(caller, Attention.APPROVE, objectName)
        return readConfidential to approve
    }

    private fun column(
        definition: ObjectDefinition,
        field: String
    ): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }

    companion object {
        private const val MEMO = "confidential-attention"
        private val NOTHING = RecordCriterion { _, _ -> ConfidentialCondition.NOTHING }
    }
}
