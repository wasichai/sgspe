package sgspe.territory

import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.ObjectDefinitions
import sgspe.common.Readability
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordReadScopes
import wasichai.core.identity.AccessPolicy
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import wasichai.core.platform.WasichaiSchemas
import wasichai.gis.srid
import java.util.UUID

// un objeto que lee una consulta de la compuerta y los campos que nombra, geometrías incluidas. principal: sin READ sobre
// él, la consulta entera es 403; si no es principal, le falta READ o un campo, la consulta sale no disponible
data class GateRead(
    val objectName: String,
    val fields: Set<String>,
    val principal: Boolean = false
)

// lo que el SQL de una consulta puede nombrar: cada objeto como subconsulta ya filtrada por la organización y por todos los
// RecordReadScope (sin alias: lo pone quien escribe el SQL), las columnas declaradas en sus GateRead, entrecomilladas, y el
// SRID declarado de cada campo GEOMETRY
interface GateTables {
    fun table(objectName: String): String

    fun column(
        objectName: String,
        field: String
    ): String

    fun srid(
        objectName: String,
        field: String
    ): Int
}

sealed interface GateOutcome<out T> {
    data class Rows<T>(
        val rows: List<T>
    ) : GateOutcome<T>

    // los objeto.campo que quien llama no puede leer: el componente sale available: false, nunca cero (RQ-DSH-032)
    data class Unavailable(
        val missingFields: List<String>
    ) : GateOutcome<Nothing>
}

// quien ejecuta el SQL ya armado con sus valores: DatabaseClient en la aplicación, uno de mentira en SpatialGateTest
fun interface GateRunner {
    suspend fun run(
        sql: String,
        bindings: Map<String, Any>,
        map: (Readable) -> Any
    ): List<Any>
}

// las tablas de una consulta, puro: la subconsulta de cada objeto lleva, en el orden de PhysicalTableRecordStore.whereClause,
// la organización, el filtro de dueño (own_records_only) y cada criterio de alcance entre paréntesis, armado con la
// definición completa del objeto (como RecordReadScopes). los valores van enlazados como :g<n>; la subconsulta de un objeto
// se arma una vez y se repite tal cual si el SQL lo nombra dos veces
internal class GateSql(
    private val declared: Map<String, Set<String>>,
    private val definitions: Map<String, ObjectDefinition>,
    private val criteria: Map<String, List<RecordCriterion>>,
    private val organizationId: UUID,
    private val owner: UUID?,
    private val physical: (ObjectDefinition) -> String
) : GateTables {
    private val values = linkedMapOf<String, Any>()
    private val tables = mutableMapOf<String, String>()

    val bindings: Map<String, Any> get() = values

    fun bind(value: Any): String {
        val name = "g${values.size}"
        values[name] = value
        return ":$name"
    }

    override fun table(objectName: String): String {
        tables[objectName]?.let { return it }
        val definition = definition(objectName)
        val terms = mutableListOf("organization_id = ${bind(organizationId)}")
        owner?.let { terms += "created_by = ${bind(it)}" }
        for (criterion in criteria[objectName].orEmpty()) {
            val condition = criterion.condition(definition, ::bind)
            check(condition.isNotBlank()) { "un RecordReadScope devolvió una condición vacía para $objectName" }
            terms += "($condition)"
        }
        return "(SELECT * FROM ${physical(definition)} WHERE ${terms.joinToString(" AND ")})".also { tables[objectName] = it }
    }

    override fun column(
        objectName: String,
        field: String
    ): String {
        if (field == SpatialGate.ID || field == SpatialGate.STATE) {
            definition(objectName)
            return SqlIdentifier.quote(field)
        }
        return SqlIdentifier.quote(declaredField(objectName, field).columnName)
    }

    override fun srid(
        objectName: String,
        field: String
    ): Int = declaredField(objectName, field).srid ?: throw IllegalArgumentException("$objectName.$field no es una geometría")

    private fun definition(objectName: String): ObjectDefinition =
        definitions[objectName] ?: throw IllegalArgumentException("la consulta no declara $objectName en sus GateRead")

    private fun declaredField(
        objectName: String,
        field: String
    ): CustomField {
        require(field in declared[objectName].orEmpty()) { "la consulta no declara $objectName.$field en sus GateRead" }
        return definition(objectName).fields.firstOrNull { it.name == field } ?: error("$objectName no tiene el campo $field")
    }
}

// la única puerta de SQL de lectura que cruza tablas del tenant (especificación de F6 §3.1, ASM-122, gis.md §5.2 patrón b):
// 1. permisos: READ de cada objeto y legibilidad de cada campo nombrado, con la definición filtrada por quien llama
//    (Readability, la vista de los tableros); sin READ sobre un objeto principal, 403; lo demás que falte, no disponible.
// 2. alcance: los criterios de todos los RecordReadScope registrados, pedidos a RecordReadScopes.criteria, la misma llamada
//    de RecordService: ninguno para ADMIN, para la plataforma ni sin alcances registrados (appliesTo), y cada uno con la
//    definición completa. un alcance nuevo se aplica solo. más el filtro de dueño de AccessPolicy (hoy nulo: ASM-069).
// 3. SQL: tablas y columnas de ObjectDefinitions.current entrecomilladas, valores enlazados, solo SELECT o WITH, una sentencia.
// 4. resultado: ids y medidas; los atributos personales se leen después por Records.
// quien llama es el AuthenticatedUser del token (Callers.require, CurrentUser de wasichai): fuera de una petición es el 401
// de wasichai, nunca la plataforma. SpatialGateScopeParityTest compara sus filas con las de Records
@Component
class SpatialGate internal constructor(
    private val caller: suspend () -> AuthenticatedUser,
    private val readability: () -> suspend (String) -> Boolean,
    private val definition: suspend (UUID, String) -> ObjectDefinition,
    private val scopes: suspend (AuthenticatedUser, ObjectDefinition) -> List<RecordCriterion>,
    private val owner: suspend (AuthenticatedUser) -> UUID?,
    private val physical: (ObjectDefinition) -> String,
    private val runner: GateRunner
) {
    @Autowired
    constructor(
        callers: Callers,
        metadata: MetadataService,
        definitions: ObjectDefinitions,
        readScopes: RecordReadScopes,
        access: AccessPolicy,
        schemas: WasichaiSchemas,
        db: DatabaseClient
    ) : this(
        { callers.require() },
        { Readability(metadata)::canRead },
        { organization, name -> definitions.current(organization, name) },
        { user, definition -> readScopes.criteria(user, definition) },
        { user -> access.ownerFilter(user) },
        { definition -> schemas.dataTable(definition.obj.physicalTable) },
        DatabaseGateRunner(db)
    )

    suspend fun <T : Any> select(
        reads: List<GateRead>,
        sql: GateTables.(bind: (Any) -> String) -> String,
        map: (Readable) -> T
    ): GateOutcome<T> {
        val user = caller()
        val canRead = readability()
        for (read in reads.filter { it.principal }) {
            if (!canRead(read.objectName)) throw ForbiddenException("La consulta exige permiso de lectura sobre ${read.objectName}")
        }
        val missing = mutableListOf<String>()
        for (read in reads) {
            val paths = read.fields.sorted().map { "${read.objectName}.$it" }
            missing +=
                if (!canRead(read.objectName)) {
                    paths.ifEmpty { listOf(read.objectName) }
                } else {
                    paths.filterNot { canRead(it) }
                }
        }
        if (missing.isNotEmpty()) return GateOutcome.Unavailable(missing.distinct().sorted())
        val declared = reads.groupBy { it.objectName }.mapValues { (_, list) -> list.flatMap { it.fields }.toSet() }
        val definitions = declared.keys.associateWith { definition(user.organizationId, it) }
        val criteria = definitions.mapValues { (_, full) -> scopes(user, full) }
        val tables = GateSql(declared, definitions, criteria, user.organizationId, owner(user), physical)
        val text = tables.sql(tables::bind)
        requireReadOnly(text)
        @Suppress("UNCHECKED_CAST")
        return GateOutcome.Rows(runner.run(text, tables.bindings, map) as List<T>)
    }

    companion object {
        // las columnas de sistema que GateTables.column da sin declararlas en un GateRead
        const val ID = "id"
        const val STATE = "workflow_state"

        internal fun requireReadOnly(sql: String) {
            val start = sql.trimStart().uppercase()
            require(start.startsWith("SELECT") || start.startsWith("WITH")) { "SpatialGate solo ejecuta SELECT" }
            require(';' !in sql) { "SpatialGate ejecuta una sola sentencia" }
        }
    }
}

// el SQL de la compuerta por DatabaseClient: los valores por nombre, cada fila por `map`
internal class DatabaseGateRunner(
    private val db: DatabaseClient
) : GateRunner {
    override suspend fun run(
        sql: String,
        bindings: Map<String, Any>,
        map: (Readable) -> Any
    ): List<Any> {
        var spec = db.sql(sql)
        bindings.forEach { (name, value) -> spec = spec.bind(name, value) }
        return spec
            .map { row, _ -> map(row) }
            .all()
            .collectList()
            .awaitSingle()
    }
}
