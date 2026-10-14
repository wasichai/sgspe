package sgspe.dashboard

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Readability
import sgspe.common.Records
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// quién lee los KPI (especificación de F8 §2.2.16, RQ-DSH-034): la persona que pide (tableros, cierre de periodo, AI-04) o la
// plataforma de una organización (JOB-05, que ensambla los entregables). las dos calculan con las mismas funciones de Kpis
sealed interface KpiReader {
    val organizationId: UUID

    data class Person(
        val user: AuthenticatedUser
    ) : KpiReader {
        override val organizationId: UUID get() = user.organizationId
    }

    data class Platform(
        override val organizationId: UUID
    ) : KpiReader
}

// lo que un lector deja ver al calcular: qué objeto.campo lee, si se le ocultan QCS confidenciales, si lee todo el tenant
// (para la cifra oficial del tenant) y los proyectos por defecto. `platform` es la organización si lee la plataforma
class KpiContext(
    val reader: KpiReader,
    val readable: suspend (String) -> Boolean,
    val confidentialHidden: Boolean,
    val tenantWide: Boolean,
    private val defaults: suspend () -> List<UUID>
) {
    val platform: UUID? get() = (reader as? KpiReader.Platform)?.organizationId

    suspend fun defaultProjects(): List<UUID> = defaults()
}

// arma el contexto de cada lector. la persona: Readability (la definición filtrada por sus permisos, dashboards.md §8),
// Permissions.confidentialHidden y «mis proyectos» de ProjectReach o, si lee todos, todos los que lee. la plataforma: todo
// campo que el tenant tiene (ObjectDefinitions, sin permisos), sin confidentialHidden, todos los proyectos, y el cálculo
// entero dentro de Records.asPlatform (WAS-ADR-0039). la plataforma nunca se usa con una persona en el contexto: es la única
// pieza de sgspe.dashboard que llama a asPlatform (ArchitectureTest), y lo comprueba antes de leer nada (Review Focus 1)
@Component
class KpiReaders internal constructor(
    private val userOrNull: suspend () -> AuthenticatedUser?,
    private val requireUser: suspend () -> AuthenticatedUser,
    private val asPlatform: suspend (UUID, suspend () -> Any?) -> Any?,
    private val readability: () -> suspend (String) -> Boolean,
    private val definition: suspend (UUID, String) -> ObjectDefinition,
    private val hidden: suspend (AuthenticatedUser) -> Boolean,
    private val mine: suspend (AuthenticatedUser) -> List<UUID>?,
    private val allProjects: suspend () -> List<UUID>
) {
    @Autowired
    constructor(
        callers: Callers,
        records: Records,
        metadata: MetadataService,
        definitions: ObjectDefinitions,
        permissions: Permissions,
        reach: ProjectReach
    ) : this(
        { callers.userOrNull() },
        { callers.require() },
        { organization, block -> records.asPlatform(organization) { block() } },
        { Readability(metadata)::canRead },
        { organization, name -> definitions.current(organization, name) },
        { permissions.confidentialHidden(it) },
        { reach.mine(it) },
        {
            try {
                records.all("project", max = DashboardData.MAX_ROWS).map { UUID.fromString(it.id) }
            } catch (_: ForbiddenException) {
                emptyList()
            }
        }
    )

    suspend fun person(): KpiReader.Person = KpiReader.Person(requireUser())

    // el lector de la plataforma, solo fuera de una petición (JOB-05, las pruebas de paridad)
    suspend fun platform(organizationId: UUID): KpiReader.Platform {
        outsideRequest()
        return KpiReader.Platform(organizationId)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> reading(
        reader: KpiReader,
        block: suspend (KpiContext) -> T
    ): T =
        when (reader) {
            is KpiReader.Person -> {
                // decisión 21: el lector de una persona solo vale para la persona que llama (nadie calcula como otro)
                check(userOrNull()?.userId == reader.user.userId) { "El lector de una persona solo vale para la persona que llama" }
                val readsAll = mine(reader.user) == null
                block(KpiContext(reader, readability(), hidden(reader.user), readsAll) { mine(reader.user) ?: allProjects() })
            }
            is KpiReader.Platform -> {
                outsideRequest()
                asPlatform(reader.organizationId) {
                    block(KpiContext(reader, existing(reader.organizationId), confidentialHidden = false, tenantWide = true) { allProjects() })
                } as T
            }
        }

    private suspend fun outsideRequest() {
        check(userOrNull() == null) { "El lector de la plataforma no se usa dentro de una petición: solo JOB-05 lee como la plataforma" }
    }

    // objeto.campo existe en el tenant (sin permisos: la plataforma no los tiene); una vez por objeto en cada cálculo
    private fun existing(organizationId: UUID): suspend (String) -> Boolean {
        val fields = mutableMapOf<String, Set<String>?>()
        return { path ->
            val objectName = path.substringBefore('.')
            val field = path.substringAfter('.', "")
            val known =
                if (objectName in fields) {
                    fields[objectName]
                } else {
                    val loaded =
                        try {
                            definition(organizationId, objectName).fields.map { it.name }.toSet()
                        } catch (_: NotFoundException) {
                            null
                        }
                    loaded.also { fields[objectName] = it }
                }
            known != null && (field.isEmpty() || field in known)
        }
    }
}
