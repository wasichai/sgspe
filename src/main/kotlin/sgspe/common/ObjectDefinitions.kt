package sgspe.common

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

// las definiciones de objeto de una organización (la caché del esquema, plan corto del 2026-10-11). wasichai no las guarda:
// MetadataService.loadDefinition consulta custom_objects y custom_fields cada vez, y la medición del plan 5.2b vio la mitad
// de las consultas de una lista de una analista en definiciones que SGSPE volvía a cargar. dos lecturas:
// - current: de la base una vez por petición de /api (RequestMemo.rememberForRequest; ninguna petición de SGSPE cambia el
//   modelo: lo cambian las de /api/metadata de wasichai, que no pasan por SGSPE después). fuera de una petición, cada vez.
//   la usan las guardas, WriteView, TenantModel y los comandos: un campo nuevo invisible les dejaría pasar una escritura
// - recent: además, entre peticiones, a lo sumo `ttl` (sgspe.metadata.definition-ttl), por organización y objeto. solo
//   donde una definición vieja falla cerrado (ProjectReadScope, Permissions.has; security.md §9.7): tras un apply.py le
//   puede faltar una columna o un objeto, y entonces la condición queda incompleta, el SQL falla o el permiso se niega,
//   nunca se abren registros. una ausencia (NotFoundException) nunca se guarda entre peticiones
// wasichai 1.0.0 no avisa los cambios del modelo (solo ObjectRemovalListener al borrar un objeto), así que se vence por
// tiempo. la hora es System.nanoTime y no el Clock de sgspe, que las pruebas de integración fijan. con el tope lleno se
// quitan las vencidas y, si no alcanza, se vacía
@Component
class ObjectDefinitions internal constructor(
    private val load: suspend (UUID, String) -> ObjectDefinition,
    private val ttl: Duration,
    private val maxEntries: Int,
    private val ticker: () -> Long
) {
    @Autowired
    constructor(
        metadata: MetadataService,
        @Value("\${sgspe.metadata.definition-ttl:30s}") ttl: Duration,
        @Value("\${sgspe.metadata.definition-max-entries:1000}") maxEntries: Int
    ) : this({ organizationId, name -> metadata.loadDefinition(organizationId, name) }, ttl, maxEntries, System::nanoTime)

    init {
        require(!ttl.isNegative) { "sgspe.metadata.definition-ttl no puede ser negativo: $ttl" }
        require(maxEntries > 0) { "sgspe.metadata.definition-max-entries debe ser positivo: $maxEntries" }
    }

    private class Entry(
        val definition: ObjectDefinition,
        val loadedAt: Long
    )

    private data class Key(
        val organizationId: UUID,
        val name: String
    )

    private val entries = ConcurrentHashMap<Key, Entry>()
    private val missed = AtomicLong()

    // la definición de esta petición: de la base la primera vez que se pide en ella
    suspend fun current(
        organizationId: UUID,
        name: String
    ): ObjectDefinition = RequestMemo.rememberForRequest(Key(organizationId, name)) { load(organizationId, name) }

    // la definición de hasta `ttl` atrás; con ttl cero, la de esta petición
    suspend fun recent(
        organizationId: UUID,
        name: String
    ): ObjectDefinition {
        if (ttl.isZero) return current(organizationId, name)
        val key = Key(organizationId, name)
        entries[key]?.let { entry -> if (ticker() - entry.loadedAt < ttl.toNanos()) return entry.definition }
        missed.incrementAndGet()
        val definition = current(organizationId, name)
        keep(key, definition)
        return definition
    }

    private fun keep(
        key: Key,
        definition: ObjectDefinition
    ) {
        val now = ticker()
        if (entries.size >= maxEntries && !entries.containsKey(key)) {
            entries.entries.removeIf { now - it.value.loadedAt >= ttl.toNanos() }
            if (entries.size >= maxEntries) entries.clear()
        }
        entries[key] = Entry(definition, now)
    }

    // para las pruebas: las entradas guardadas y las cargas de recent por no tener una vigente
    internal val size: Int get() = entries.size

    internal fun misses(): Long = missed.get()
}
