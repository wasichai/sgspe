# SGSPE · Plan corto: la caché del esquema que lee el alcance de lectura por proyecto

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [x]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Que una persona que no es `ADMIN` deje de pagar, en cada petición, las definiciones de objeto que SGSPE vuelve a
cargar de la base (`ProjectReadScope`, `Permissions.has` y las guardas), con un memo por petición para todas y una caché
corta entre peticiones solo donde una definición vieja falla cerrado, y medir el antes y el después por el túnel.

**Architecture:** `sgspe.common` gana `ObjectDefinitions`, la única pieza que llama a `MetadataService.loadDefinition`
(salvo el camino de la plataforma de `Records`): `current(organización, objeto)` la carga una vez por petición de `/api`
(`RequestMemo.rememberForRequest`) y `recent(organización, objeto)` la guarda además entre peticiones hasta
`sgspe.metadata.definition-ttl` (30 s), por organización y objeto, sin guardar ausencias y con un tope de entradas.
`ProjectReadScope` y `Permissions.has` (que solo usa el id del objeto) leen con `recent`; las guardas, `WriteView`,
`TenantModel` y los comandos, con `current`. Los permisos nunca se guardan entre peticiones. `ArchitectureTest` fija la
regla. Además (Tarea 6), `RequestMemoFilter` abre el memo de un comando en las escrituras de `/api/sgspe/`, y
`Assignments.isAssigned` lee `project_assignment` una vez por usuario y proyecto en cada comando, nunca entre peticiones.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 1.0.0 (sin
starters ni dependencias nuevas: `ConcurrentHashMap` y `System.nanoTime`, sin Caffeine), JUnit 5 y Mockito con
`wasichai-test` (`WasichaiIntegrationTest`); PostgreSQL 18 con PostGIS por el túnel para la medición (solo el coordinador).

**Spec:** no hay especificación aparte. La sección «Contexto y decisión» de este plan hace de especificación (aprobada por
el usuario al revisar este plan). Documentos que la enmarcan: `docs/security.md` §9.7 (alcance de lectura por proyecto,
falla cerrado), `docs/roadmap.md` (fila F4: «la caché del esquema que resuelve la condición» en el backlog; fila F5: «la
caché del esquema va como un plan corto justo después de 5.2b, antes de F6»), el plan 4.4a (Tarea 17, `RequestMemoFilter`)
y el plan 5.2b (Tarea 13, la base de medición).

## Global Constraints

- **Repositorio y rama.** Todo en `~/IdeaProjects/sgspe`, en la rama `perf/cache-esquema` creada desde `main` (con 5.2b
  integrado, `06a47f2` o posterior); ramas de tarea `ce/t<k>` integradas en `perf/cache-esquema` con `--no-ff`, como en
  F4 y F5. El Paso 1 de la Tarea 1 lo comprueba.
- **Versiones.** wasichai `1.0.0` exacto, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`, ktlint `1.7.1`.
  Ninguna dependencia nueva (ni Caffeine ni `spring-boot-starter-cache`).
- **Idioma.** Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009).
- **Arquitectura.** Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`,
  `AuditService` ni `ObjectSchemaManager`; ningún literal de rol en `src/main/kotlin`; `scope` y `privacy` solo dependen de
  `common` (`ObjectDefinitions` vive en `common`); ningún bean de estereotipo con el nombre simple de otro
  (`ArchitectureTest`). Ningún import sin uso: ktlint de este repositorio no los quita.
- **Falla cerrado.** Una definición vieja nunca abre registros: `recent` solo se usa donde una definición vieja deja la
  condición incompleta (`NOTHING`), acota por menos padres, produce un SQL que falla o niega un permiso. Ninguna ausencia
  (`NotFoundException`) se guarda entre peticiones.
- **Permisos y asignaciones.** Nunca se guardan entre peticiones: cambian al instante por `/api/roles` y
  `/api/objects/project_assignment`. Una asignación vive a lo sumo lo que un comando (Tarea 6). Dentro de una petición siguen el
  memo que ya tienen (`ProjectReach.readsAll`, `ConfidentialAttentionScope`, `EvidenceFileReadPolicy`).
- **Configuración.** `sgspe.metadata.definition-ttl: 30s` (`0s` apaga la caché entre peticiones y deja el memo por
  petición) y `sgspe.metadata.definition-max-entries: 1000`, en `src/main/resources/application.yml`.
- **Datos.** Ficticios: correos `@sgspe.test`, «Proyecto Ejemplo A»; ningún nombre real de persona en nada versionado.
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde
  `~/IdeaProjects/sgspe`; `./gradlew ktlintFormat` antes de `build`; `yarn format` antes de commitear yaml o json. El token
  nunca va a un archivo ni a la salida. Con poca memoria, `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Integración.** Las clases `*ApiTest` necesitan PostgreSQL: **corren en la CI del PR** y, si se quiere antes, las corre el
  coordinador, nunca el implementador ni un subagente. El implementador compila (`build` compila también las de
  integración) y entrega la tarea marcada «integración pendiente» con las clases de su último paso.
- **Orden de las tareas.** 1, 2, 3, 6 (agregada con la aprobación del usuario), 4 y 5.
- **Base, túnel, Docker y tenant de desarrollo.** Solo el coordinador principal los toca (Tarea 4).
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo; al final, tras una línea en
  blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas. Push y PR
  en la Tarea 5.

### Comandos

Unitarias de una clase o de un paquete (sin base):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase o paquete>'
```

Build completo (ktlint, todas las unitarias y la compilación de las de integración):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

## Review Focus

Las cinco entradas que este cambio implica y que más pueden morder a quien use SGSPE, cada una con su prueba en la tarea
dueña del código:

1. **Subir un tenant de fase (`apply.py`, `upgrade_tenants.py`) mientras una analista lee.** Una definición guardada a medio
   aplicar (sin `end_date` en `project_assignment`, sin la columna nueva de un padre) no puede abrir registros: la
   condición queda incompleta y no admite ninguno hasta que vence la entrada. Prueba: `ProjectReadScopeTest` «con
   ObjectDefinitions las definiciones del alcance se piden una vez y una vieja falla cerrado» (Tarea 2).
2. **Un objeto que el tenant todavía no tiene y que aparece con la subida de fase.** Si la ausencia se guardara 30 s, las
   reglas que preguntan `TenantModel` no juzgarían el objeto nuevo. Pruebas: `ObjectDefinitionsTest` «una ausencia no se
   guarda: el objeto que aparece se ve en la petición siguiente» (Tarea 1) y `TenantModelTest` «dentro de una petición
   cada objeto se pregunta una vez, también el que falta» (Tarea 3, la ausencia solo vive lo que la petición).
3. **Dos tenants con los mismos nombres de objeto.** Sus tablas físicas son distintas; una definición de otro tenant en la
   condición leería otra tabla. Prueba: `ObjectDefinitionsTest` «dos organizaciones no comparten definiciones» (Tarea 1).
4. **Muchos tenants en un nodo.** La caché no puede crecer sin tope. Prueba: `ObjectDefinitionsTest` «el tope de entradas
   se respeta y la última entrada queda» (Tarea 1).
5. **Un operador que necesita la definición nueva ya, o una caché que da problemas.** `sgspe.metadata.definition-ttl: 0s`
   la apaga sin quitar el memo por petición. Prueba: `ObjectDefinitionsTest` «con ttl cero no guarda entre peticiones,
   pero sí dentro de una» (Tarea 1). Un permiso cambiado vale desde la petición siguiente: lo sigue fijando
   `RequestMemoFilterApiTest` (sin cambios).
6. **Una asignación quitada en medio de un trabajo.** El memo de un comando no puede durar más que el comando: la asignación
   que se quita por `/api/objects/project_assignment` deja de valer desde la petición siguiente. Pruebas:
   `RequestMemoFilterTest` «dos comandos no comparten la asignacion» y `AssignmentMemoApiTest` (Tarea 6).

---

## Contexto y decisión (hace de especificación)

### La medición que lo motiva (2026-10-11, rama de 5.2b, base `sgspe_medicion5` por el túnel)

- Por la API, una analista tarda 750 a 850 ms por lista (`/api/objects/program_measurement/records` y
  `/api/objects/delivery/records`, `size=50` o `1`); el admin, 260 a 330 ms. El SQL del alcance tarda a lo sumo 34 ms.
- Consultas por petición (log `io.r2dbc.postgresql.QUERY`): analista 18 a 20, admin 6 a 7. Cada consulta por el túnel
  cuesta unos 30 a 40 ms.
- En el portal, una ficha de aporte hace unas 18 peticiones de 0,2 a 0,9 s, `transition-checks` tarda 1,5 a 3,5 s y el alta
  de una entrega con tres evidencias (`POST /api/sgspe/social-contributions/{id}/deliveries`), 14 s.

### De dónde salen las consultas de una lista (leído en el código de wasichai 1.0.0 y de SGSPE)

`MetadataService.loadDefinition` no tiene caché (dos consultas: `custom_objects` y `custom_fields`); el comentario de
`ProjectReadScope` que dice «wasichai las tiene en caché» es falso. `RecordService.list` pide el alcance **una sola vez**
por lista (`caller.scope(definition)`): el conteo y la página reusan la misma condición, así que la repetición no viene de
ahí, sino de que cada lectura carga varias definiciones distintas, y de que `Permissions.has` carga la de `project` para
saber su id. Para `GET /api/objects/delivery/records` como analista:

| Origen | Consultas | Con este plan |
|---|---|---|
| wasichai, para todos: definición de `delivery` (2), estado del workflow (1), página y conteo (2), sesión (1 o 2) | 6 a 7 | 6 a 7 |
| wasichai, solo para quien no es `ADMIN`: permiso READ (1), `AccessPolicy.fieldAccess` (`WITH my_roles …`, 1), `ownRecordsOnly` (`bool_and(own_records_only)`, 1) | 3 | 3 |
| SGSPE, `ProjectReach.readsAll`: `Permissions.has` carga la definición de `project` (2) y pregunta `READ_ALL_PROJECTS` (1); ya memorizado por petición (plan 4.4a) | 3 | 1 |
| SGSPE, `ProjectReadScope`: `project_assignment` (2) y la cadena de cada padre (`commitment` 2, `social_contribution` 2; en `program_measurement`, `program_indicator` 2 y `social_program` 2) | 6 | 0 (caché caliente) |
| **Total** | **18 a 20** | **10 a 11** |

Con la caché caliente, la analista pasa de 18 a 20 consultas a 10 a 11 (unos 8 × 35 ms ≈ 0,3 s menos por lista: de 750 a
850 ms a unos 450 a 550 ms). La meta «las del admin + 1 o 2» **no se alcanza desde SGSPE**: las tres consultas de rol de
wasichai (permiso, permisos de campo y «solo lo propio») se pagan en toda lectura de quien no es `ADMIN`, y la de
`READ_ALL_PROJECTS` no se guarda entre peticiones. La meta realista de este plan es **las del admin + 4** (3 de wasichai y 1
de SGSPE), con cero consultas de definiciones de SGSPE en una lista con la caché caliente. Lo que queda es de wasichai: el
anexo A redacta el issue.

### Diagnóstico del alta de una entrega con tres evidencias (14 s), deducido del código

`ContributionDeliveries.create`, como `SOCIAL_INVESTMENT_MANAGER` (no exento del alcance). Estimación por lectura del código,
a confirmar con la Tarea 4 (Paso 3):

| Paso | Consultas estimadas | Lo que repite definiciones o permisos | Con este plan |
|---|---|---|---|
| `permissions.require` de 4 permisos (CREATE `delivery`, UPDATE `social_contribution`, `evidence` y `delivery`) | 12 | cada `Permissions.has` carga una definición (2) además de su consulta (1) | 4 |
| 4 lecturas del comando (`get` del aporte dos veces, `count` de entregas, `get` de la entrega) | unas 45 | wasichai carga la definición leída en cada una (2) y sus 3 consultas de rol; el alcance, `project_assignment` (2) y, en `delivery`, la cadena de dos padres (4) | unas 29 |
| Alta de la entrega (wasichai, `Records.writableFields` con `definitionOf`, guardas, listeners de totales y avisos) | 50 a 70 | `WriteView.after`, `TenantModel`, el alcance de cada lectura de una guarda | 8 a 12 menos |
| 3 vínculos `evidence_delivery` (wasichai carga las dos definiciones, el permiso y lee los dos extremos con el alcance) | unas 60 | por vínculo: definiciones de `evidence` y `delivery` (4), alcance de `delivery` (6) y de `evidence` (2) | unas 24 menos |
| `ProjectScopeGuard` en el alta y en los dos extremos de cada vínculo (7 veces): `ProjectResolver` lee el aporte y `Assignments.isAssigned` lista `project_assignment` como quien escribe | unas 90 | `isAssigned` usa `RequestMemo.remember`, que solo vale dentro de un `RequestMemo.within`, y el comando no lo abre: cada vez es una lista completa (7 a 10 consultas) | unas 70 menos (definiciones, y con la Tarea 6 una sola lista de asignaciones) |
| **Total** | **260 a 320 (unos 10 a 13 s por el túnel)** | | **unas 145 a 165 menos (≈ 55 %, 5 a 6 s)** |

Lo que queda después de este plan: unas 100 consultas de wasichai por operación (definición del objeto y las tres de rol
en cada `get`, `list`, `create` y `link`, anexo A). Las unas 60 de las listas repetidas de `isAssigned` las quita la Tarea 6
(decisión 8): `RequestMemoFilter` abre el memo del comando en las escrituras de `/api/sgspe/`, como la confirmación de una
importación abre el suyo. `transition-checks` (1,5 a 3,5 s) corre las mismas reglas en seco y gana
lo mismo por petición: las definiciones de las guardas una vez y los permisos sin su definición.

### Decisiones de este plan

| # | Pregunta | Decisión y razón |
|---|---|---|
| 1 | ¿Memo por petición, caché entre peticiones o los dos? | **Los dos, en una sola pieza (`ObjectDefinitions`), recomendado.** Una lista carga cada definición una vez, así que el memo por petición no le ahorra nada: las 8 consultas de definiciones de la analista solo desaparecen con la caché entre peticiones. El alta de una entrega repite las mismas definiciones decenas de veces dentro de la petición: ahí el memo, que no tiene riesgo de datos viejos, ahorra casi todo. Por eso `current` (memo) es lo que usan todos, y `recent` (memo más caché) solo donde una definición vieja falla cerrado |
| 2 | ¿Dónde se usa `recent`? | **Solo en `ProjectReadScope` y en `Permissions.has`.** En el alcance, una definición vieja solo puede faltarle una columna (la condición queda incompleta y no admite registros, o acota por los padres que sí tiene), tener una que ya no existe (el SQL falla: 500, ningún dato) o tener la tabla de un objeto borrado (igual). En `Permissions.has` solo se usa el id del objeto: el de un objeto borrado y vuelto a crear no tiene permisos propios, así que una entrada vieja solo puede negar. Las guardas, `WriteView`, `TenantModel` y los comandos usan `current`: un campo nuevo invisible por 30 s dejaría pasar una escritura que su regla rechazaría |
| 3 | ¿TTL y tope? | **30 s y 1000 entradas, configurables.** Tras subir un tenant de fase, a lo sumo 30 s en que una analista ve menos registros (nunca más); el costo es de 2 consultas por organización y objeto cada 30 s. 1000 entradas cubren unos 50 tenants con los ~20 objetos que lee el alcance; al llenarse se quitan las vencidas y, si no alcanza, se vacía (raro y barato). `0s` apaga la caché entre peticiones |
| 4 | ¿Invalidación por evento? | **No: wasichai 1.0.0 no publica ninguno que sirva.** `ObjectRemovalListener` solo avisa el borrado de un objeto, dentro de su transacción (una lectura concurrente volvería a guardar la definición vieja antes del commit) y `FieldUsage` solo pregunta antes de borrar un campo; no hay aviso de alta de objeto ni de alta o cambio de campo. `AdminAudit` registra todos los cambios del modelo, pero es un único bean de wasichai: decorarlo interceptaría la auditoría de la plataforma. Además, con varios nodos un evento local no alcanza. El TTL corto es suficiente y el anexo A pide el evento upstream |
| 5 | ¿Ausencias? | **Nunca se guardan entre peticiones.** `TenantModel.has` memoriza su respuesta (también el «no está») solo por petición, como un permiso |
| 6 | ¿Permisos de `ProjectReach.readsAll`? | **Sin cambios en su memo.** Ya se pregunta una vez por petición (`RequestMemoFilter`, plan 4.4a, Tarea 17; `RequestMemoFilterApiTest` lo fija) y nunca se guarda entre peticiones: los permisos cambian al instante. Lo que baja es el costo de cada pregunta, de 3 consultas a 1, porque la definición de `project` sale de `recent` |
| 7 | ¿Caffeine o la caché de Spring? | **Ninguna.** Spring Boot 4.1.1 no trae Caffeine, y `spring-boot-starter-cache` con `ConcurrentMapCache` no tiene TTL; una dependencia nueva para unas 60 líneas no se justifica. `ObjectDefinitions` usa un `ConcurrentHashMap` con la hora de carga de `System.nanoTime`, como `TokenRevocation` de wasichai. No usa el `Clock` de SGSPE: las pruebas de integración lo fijan (`Clock.fixed`) y la caché nunca vencería |
| 8 | ¿Se arregla aquí la repetición de `Assignments.isAssigned` en los comandos de entregas y evidencias? | **Sí, en la Tarea 6** (el usuario la aprobó con el plan, 2026-10-11; antes iba al backlog). No es la caché del esquema, pero es barata y es la mayor repetición propia del alta de una entrega: `RequestMemoFilter` abre el memo de un comando (`RequestMemo.within`) en toda escritura de `/api/sgspe/`, y `isAssigned` lee una vez por usuario y proyecto. Nunca entre peticiones, como los permisos |
| 9 | `Records.writableFields` (camino de la plataforma) también llama a `loadDefinition` | **Se exceptúa en `ArchitectureTest`.** Corre en los trabajos de fondo, fuera de una petición, donde el memo no aplica; cambiar el constructor de `Records` tocaría diez pruebas sin ganar nada medible |
| 10 | ¿Comentarios falsos? | `ProjectReadScope` («wasichai las tiene en caché») y `RequestMemoFilterApiTest` («la caché de definiciones de wasichai») se corrigen (Tareas 2 y 3) |

---

## Archivos

| Archivo | Tarea | Responsabilidad |
|---|---|---|
| `src/main/kotlin/sgspe/common/ObjectDefinitions.kt` (nuevo) | 1 | `current` y `recent`; la única que llama a `loadDefinition` |
| `src/main/resources/application.yml` | 1 | `sgspe.metadata.definition-ttl` y `definition-max-entries` |
| `src/test/kotlin/sgspe/common/ObjectDefinitionsTest.kt` (nuevo) | 1 | memo, TTL, ausencias, tenants, tope |
| `src/main/kotlin/sgspe/scope/ProjectReadScope.kt` | 2 | lee con `recent`; comentario corregido |
| `src/main/kotlin/sgspe/common/Permissions.kt` | 2 | el id del objeto con `recent` |
| `src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt`, `common/ProjectReachTest.kt`, `privacy/ExportProjectsTest.kt` | 2 | constructor de `Permissions`; prueba nueva del alcance con la caché |
| `src/test/kotlin/sgspe/common/ObjectDefinitionsApiTest.kt` (nuevo) | 2 | la segunda lista de una analista no carga definiciones |
| `src/main/kotlin/sgspe/common/WriteView.kt`, `TenantModel.kt`, `DependentsDeleteGuard.kt`, `TransitionChecks.kt`, `RequestMemo.kt` | 3 | `current`; el memo de `TenantModel`; el comentario del memo |
| `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`, `src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt` | 3 | `current` |
| `src/test/kotlin/sgspe/ArchitectureTest.kt` | 3 | «solo ObjectDefinitions carga definiciones de objeto» |
| `src/test/kotlin/sgspe/common/TenantModelTest.kt`, `attention/RequestClosureListenerTest.kt`, `investment/CampaignCommittedTest.kt`, `investment/SupportPolicyGuardTest.kt`, `evidence/EvidenceWriteGuardTenantTest.kt`, `common/RequestMemoFilterApiTest.kt` | 3 | los dobles pasan a `ObjectDefinitions`; comentario corregido |
| `src/main/kotlin/sgspe/common/RequestMemoFilter.kt`, `RequestMemo.kt`, `src/main/kotlin/sgspe/scope/Assignments.kt` | 6 | el memo del comando en las escrituras de `/api/sgspe/`; comentarios |
| `src/test/kotlin/sgspe/common/RequestMemoFilterTest.kt`, `src/test/kotlin/sgspe/scope/AssignmentMemoApiTest.kt` (nuevo) | 6 | el memo del comando por ruta y método; una lectura de asignaciones por comando |
| `docs/security.md`, `docs/roadmap.md`, `CLAUDE.md` | 5 | §9.7, cifras antes y después, backlog, tabla «Dónde está cada cosa» |

---

### Tarea 1: `ObjectDefinitions`, el memo por petición y la caché reciente

**Files:**
- Create: `src/main/kotlin/sgspe/common/ObjectDefinitions.kt`
- Create: `src/test/kotlin/sgspe/common/ObjectDefinitionsTest.kt`
- Modify: `src/main/resources/application.yml` (bloque `sgspe:`, después de `concurrency:`)

**Interfaces:**
- Consumes: `RequestMemo.rememberForRequest(key: Any, compute: suspend () -> T): T` (`sgspe.common.RequestMemo`);
  `MetadataService.loadDefinition(organizationId: UUID, name: String): ObjectDefinition` (wasichai, lanza
  `NotFoundException` si el objeto no existe).
- Produces (lo usan las Tareas 2 y 3):
  - `@Component class ObjectDefinitions` con `suspend fun current(organizationId: UUID, name: String): ObjectDefinition` y
    `suspend fun recent(organizationId: UUID, name: String): ObjectDefinition`; las dos dejan pasar el
    `NotFoundException` de wasichai.
  - constructor `internal` para las pruebas:
    `ObjectDefinitions(load: suspend (UUID, String) -> ObjectDefinition, ttl: Duration, maxEntries: Int, ticker: () -> Long)`.
  - `internal val size: Int` (entradas guardadas) e `internal fun misses(): Long` (cargas que hizo `recent` por no tener
    una entrada vigente), solo para las pruebas.

- [x] **Paso 1: Rama y punto de partida**

```bash
cd ~/IdeaProjects/sgspe && git fetch origin && git switch main && git pull --ff-only
git log --oneline -1 && git merge-base --is-ancestor 06a47f2 HEAD && echo "5.2b integrado"
git switch -c perf/cache-esquema && git switch -c ce/t1
```

Esperado: «5.2b integrado» y las dos ramas creadas.

- [x] **Paso 2: La prueba que falla**

`src/test/kotlin/sgspe/common/ObjectDefinitionsTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.ObjectDefinition
import java.time.Duration
import java.util.UUID

// ObjectDefinitions (plan corto de la caché del esquema): current carga una vez por petición de /api; recent guarda además
// entre peticiones a lo sumo `ttl`, por organización y objeto, nunca una ausencia, con un tope de entradas. el reloj es un
// contador propio de la prueba (la caché no usa el Clock de sgspe, que las pruebas de integración fijan)
class ObjectDefinitionsTest {
    private val organization = UUID.randomUUID()
    private val other = UUID.randomUUID()
    private var now = 0L
    private val asked = mutableListOf<Pair<UUID, String>>()
    private val absent = mutableSetOf<String>()

    private fun definition(
        organizationId: UUID,
        name: String
    ) = ObjectDefinition(
        CustomObject(UUID.randomUUID(), organizationId, name, name, name, null, true, "${name}_tbl", null, null),
        emptyList()
    )

    private suspend fun load(
        organizationId: UUID,
        name: String
    ): ObjectDefinition {
        asked += organizationId to name
        if (name in absent) throw NotFoundException("Object '$name' does not exist")
        return definition(organizationId, name)
    }

    private fun definitions(
        ttl: Duration = Duration.ofSeconds(30),
        maxEntries: Int = 100
    ) = ObjectDefinitions(::load, ttl, maxEntries) { now }

    private fun advance(seconds: Long) {
        now += Duration.ofSeconds(seconds).toNanos()
    }

    @Test
    fun `current carga una vez por peticion, organizacion y objeto, y fuera de una peticion cada vez`() {
        val definitions = definitions()
        runBlocking {
            RequestMemo.forRequest {
                repeat(3) { definitions.current(organization, "project") }
                definitions.current(other, "project")
            }
            RequestMemo.forRequest { definitions.current(organization, "project") }
        }
        assertEquals(listOf(organization to "project", other to "project", organization to "project"), asked)
        runBlocking { repeat(2) { definitions.current(organization, "project") } }
        assertEquals(5, asked.size)
    }

    @Test
    fun `recent guarda entre peticiones hasta ttl y despues vuelve a cargar`() {
        val definitions = definitions()
        runBlocking {
            val first = RequestMemo.forRequest { definitions.recent(organization, "commitment") }
            advance(29)
            val second = RequestMemo.forRequest { definitions.recent(organization, "commitment") }
            assertSame(first, second)
            advance(1)
            val third = RequestMemo.forRequest { definitions.recent(organization, "commitment") }
            assertNotSame(first, third)
        }
        assertEquals(2, asked.size)
        assertEquals(2L, definitions.misses())
    }

    @Test
    fun `una ausencia no se guarda - el objeto que aparece se ve en la peticion siguiente`() {
        absent += "social_contribution"
        val definitions = definitions()
        assertThrows(NotFoundException::class.java) { runBlocking { definitions.recent(organization, "social_contribution") } }
        absent.clear()
        assertEquals("social_contribution", runBlocking { definitions.recent(organization, "social_contribution") }.obj.name)
        assertEquals(2, asked.size)
    }

    @Test
    fun `dos organizaciones no comparten definiciones`() {
        val definitions = definitions()
        val mine = runBlocking { definitions.recent(organization, "project") }
        val theirs = runBlocking { definitions.recent(other, "project") }
        assertEquals(organization, mine.obj.organizationId)
        assertEquals(other, theirs.obj.organizationId)
        assertEquals(listOf(organization to "project", other to "project"), asked)
    }

    @Test
    fun `con ttl cero no guarda entre peticiones, pero si dentro de una`() {
        val definitions = definitions(ttl = Duration.ZERO)
        runBlocking {
            RequestMemo.forRequest { repeat(2) { definitions.recent(organization, "project") } }
            RequestMemo.forRequest { definitions.recent(organization, "project") }
        }
        assertEquals(2, asked.size)
        assertEquals(0, definitions.size)
    }

    @Test
    fun `el tope de entradas se respeta y la ultima entrada queda`() {
        val definitions = definitions(maxEntries = 3)
        val names = listOf("project", "commitment", "social_contribution", "program_indicator", "social_program")
        runBlocking { names.forEach { definitions.recent(organization, it) } }
        assertTrue(definitions.size <= 3, "entradas: ${definitions.size}")
        runBlocking { definitions.recent(organization, names.last()) }
        assertEquals(names.size, asked.size)
    }

    @Test
    fun `un ttl negativo o un tope sin entradas no arrancan`() {
        assertThrows(IllegalArgumentException::class.java) { definitions(ttl = Duration.ofSeconds(-1)) }
        assertThrows(IllegalArgumentException::class.java) { definitions(maxEntries = 0) }
    }
}
```

- [x] **Paso 3: Correrla y ver que falla**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ObjectDefinitionsTest'`
Expected: FAIL de compilación, «Unresolved reference 'ObjectDefinitions'».

- [x] **Paso 4: La implementación mínima**

`src/main/kotlin/sgspe/common/ObjectDefinitions.kt`:

```kotlin
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
        if (entries.size >= maxEntries && key !in entries) {
            entries.entries.removeIf { now - it.value.loadedAt >= ttl.toNanos() }
            if (entries.size >= maxEntries) entries.clear()
        }
        entries[key] = Entry(definition, now)
    }

    // para las pruebas: las entradas guardadas y las cargas de recent por no tener una vigente
    internal val size: Int get() = entries.size

    internal fun misses(): Long = missed.get()
}
```

En `src/main/resources/application.yml`, dentro de `sgspe:`, después del bloque `concurrency:` (antes de `stakeholder:`):

```yaml
  metadata:
    # la caché del esquema (ObjectDefinitions.recent): cuánto vale una definición de objeto entre peticiones para el
    # alcance de lectura y los permisos (security.md §9.7). 0s la apaga y deja solo el memo por petición
    definition-ttl: 30s
    # el tope de definiciones guardadas (organización y objeto)
    definition-max-entries: 1000
```

- [x] **Paso 5: Correrla y ver que pasa**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ObjectDefinitionsTest'`
Expected: PASS, 7 pruebas.

- [x] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && yarn format && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/common/ObjectDefinitions.kt src/test/kotlin/sgspe/common/ObjectDefinitionsTest.kt src/main/resources/application.yml
git commit -m "$(cat <<'EOF'
perf(common): ObjectDefinitions guarda las definiciones por petición y por 30 s

current carga una definición de objeto una vez por petición de /api; recent la guarda además
entre peticiones hasta sgspe.metadata.definition-ttl, por organización y objeto, sin guardar
ausencias y con un tope de entradas. wasichai 1.0.0 no tiene caché de definiciones.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
git switch perf/cache-esquema && git merge --no-ff ce/t1 -m "chore: integra la tarea 1 del plan de la caché del esquema"
```

Expected: `BUILD SUCCESSFUL` (si `ktlintFormat` reescribe algo, se incluye en el mismo `git add`).

---

### Tarea 2: El alcance y los permisos leen la caché reciente

**Files:**
- Modify: `src/main/kotlin/sgspe/scope/ProjectReadScope.kt` (imports, constructor `@Autowired`, comentario de `columns`)
- Modify: `src/main/kotlin/sgspe/common/Permissions.kt` (constructor y `has`)
- Modify: `src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt`, `src/test/kotlin/sgspe/common/ProjectReachTest.kt`,
  `src/test/kotlin/sgspe/privacy/ExportProjectsTest.kt` (constructor de `Permissions`)
- Create: `src/test/kotlin/sgspe/common/ObjectDefinitionsApiTest.kt`

**Interfaces:**
- Consumes: `ObjectDefinitions.recent(organizationId: UUID, name: String): ObjectDefinition`, el constructor `internal`
  `ObjectDefinitions(load, ttl, maxEntries, ticker)` e `internal fun misses(): Long` (Tarea 1).
- Produces: `Permissions(currentUser: CurrentUser, definitions: ObjectDefinitions)` (nuevo tipo del segundo parámetro, lo
  usa la Tarea 3 en sus dobles); `ProjectReadScope` con `@Autowired constructor(reach: ProjectReach, definitions:
  ObjectDefinitions, catalog: ModelCatalog, schemas: WasichaiSchemas, clock: Clock)` (el constructor `internal` con la
  función `load` no cambia).

- [x] **Paso 1: Rama**

```bash
cd ~/IdeaProjects/sgspe && git switch perf/cache-esquema && git switch -c ce/t2
```

- [x] **Paso 2: Las pruebas que fallan**

En `src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt`, agregue los imports `sgspe.common.ObjectDefinitions` y
`java.time.Duration` (en orden alfabético con los demás) y esta prueba al final de la clase (antes de la llave que la cierra):

```kotlin
    // la caché del esquema: dos lecturas seguidas piden las definiciones del alcance una sola vez, y una definición vieja (un
    // project_assignment a medio aplicar, sin end_date) sigue sin admitir ningún registro mientras dura en la caché
    @Test
    fun `con ObjectDefinitions las definiciones del alcance se piden una vez y una vieja falla cerrado`() {
        val partial =
            Definitions(
                mapOf(
                    "project_assignment" to definition("project_assignment", "project", "member_user_id", "active", "start_date"),
                    "stakeholder" to definition("stakeholder", "project")
                )
            )
        val cache = ObjectDefinitions(partial::load, Duration.ofSeconds(30), 100) { 0L }
        val scope = ProjectReadScope(reach { false }, catalog, schemas, clock) { org, name -> cache.recent(org, name) }
        val assessment = definition("stakeholder_assessment", "stakeholder")
        assertEquals("false", condition(scope, assessment))
        assertEquals("false", condition(scope, assessment))
        assertEquals(listOf("project_assignment", "stakeholder"), partial.asked)
    }
```

En el mismo archivo, en `reach(...)`, cambie
`Permissions(mock(CurrentUser::class.java), mock(MetadataService::class.java))` por
`Permissions(mock(CurrentUser::class.java), mock(ObjectDefinitions::class.java))` (el import de `MetadataService` se queda:
lo usa `Records`). Haga el mismo reemplazo en `src/test/kotlin/sgspe/common/ProjectReachTest.kt` (línea del
`Permissions(...)` de la clase que lo extiende; `MetadataService` sigue en uso por `Records`) y en
`src/test/kotlin/sgspe/privacy/ExportProjectsTest.kt` (agregue `import sgspe.common.ObjectDefinitions`; `MetadataService`
sigue en uso por `Records`).

`src/test/kotlin/sgspe/common/ObjectDefinitionsApiTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// la caché del esquema por la API (plan corto del 2026-10-11): con la caché caliente, una lista de una analista no vuelve a
// cargar las definiciones que lee SGSPE (project_assignment y el padre para el alcance, project para READ_ALL_PROJECTS).
// se cuentan las cargas de ObjectDefinitions.recent y no las consultas del log de SQL, que también trae las de wasichai
class ObjectDefinitionsApiTest : F4ApiTest() {
    @Autowired
    private lateinit var definitions: ObjectDefinitions

    @Test
    fun `la segunda lista de una analista no carga definiciones del alcance`() {
        val project = testProject()
        stakeholder(project)
        val analyst = member("ANALYST", project)
        val assessments = "/api/objects/stakeholder_assessment/records?size=50"
        // la primera calienta la caché: project, project_assignment y stakeholder
        send("GET", assessments, null, HttpStatus.OK, analyst.token)
        val before = definitions.misses()
        send("GET", assessments, null, HttpStatus.OK, analyst.token)
        send("GET", "/api/objects/stakeholder/records?size=1", null, HttpStatus.OK, analyst.token)
        assertEquals(before, definitions.misses())
    }
}
```

- [x] **Paso 3: Correr las unitarias y ver que fallan**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.ProjectReadScopeTest' --tests 'sgspe.common.ProjectReachTest' --tests 'sgspe.privacy.ExportProjectsTest'`
Expected: FAIL de compilación: `Permissions` espera un `MetadataService` y recibe un `ObjectDefinitions` («Argument type
mismatch»).

- [x] **Paso 4: La implementación**

`src/main/kotlin/sgspe/common/Permissions.kt`: quite `import wasichai.core.metadata.MetadataService`, cambie el constructor
y `has`:

```kotlin
@Component
class Permissions(
    private val currentUser: CurrentUser,
    private val definitions: ObjectDefinitions
) {
```

```kotlin
    // la consulta de permisos de wasichai, cada vez (o una vez por petición donde quien llama la memoriza); el id del objeto
    // sale de la caché reciente (ObjectDefinitions.recent): un objeto borrado y vuelto a crear tiene otro id y ningún
    // permiso propio, así que una entrada vieja solo puede negar
    suspend fun has(
        user: AuthenticatedUser,
        action: String,
        objectName: String
    ): Boolean =
        try {
            currentUser.requirePermission(user, action, definitions.recent(user.organizationId, objectName).obj.id)
            true
        } catch (_: ForbiddenException) {
            false
        }
```

`src/main/kotlin/sgspe/scope/ProjectReadScope.kt`: quite `import wasichai.core.metadata.MetadataService`, agregue
`import sgspe.common.ObjectDefinitions` y cambie el constructor `@Autowired`:

```kotlin
    @Autowired
    constructor(
        reach: ProjectReach,
        definitions: ObjectDefinitions,
        catalog: ModelCatalog,
        schemas: WasichaiSchemas,
        clock: Clock
    ) : this(reach, catalog, schemas, clock, { organization, name -> definitions.recent(organization, name) })
```

Reemplace el comentario de `columns` (las tres líneas que empiezan con «las tablas y columnas del grupo») por:

```kotlin
    // las tablas y columnas del grupo, de la definición del objeto leído (la de wasichai, de esta lectura), de la de
    // project_assignment y, en un hijo, de las de cada salto de la cadena de cada padre posible. estas salen de
    // ObjectDefinitions.recent (hasta sgspe.metadata.definition-ttl atrás; wasichai no las guarda): una vieja tras un
    // apply.py solo deja la condición incompleta o acotada por menos padres, nunca más registros. si una relación es
    // opcional lo dice el modelo, nunca una lista aparte
```

Y en el comentario de la clase, la última línea («consulta de permisos o de definiciones que falla, …») queda igual; agregue
después de ella una línea:

```kotlin
// las definiciones de la condición salen de la caché del esquema (ObjectDefinitions.recent, security.md §9.7)
```

- [x] **Paso 5: Correr las unitarias y ver que pasan**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.ProjectReadScopeTest' --tests 'sgspe.common.ProjectReachTest' --tests 'sgspe.privacy.ExportProjectsTest'`
Expected: PASS, con la prueba nueva de `ProjectReadScopeTest`.

- [x] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/scope/ProjectReadScope.kt src/main/kotlin/sgspe/common/Permissions.kt \
  src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt src/test/kotlin/sgspe/common/ProjectReachTest.kt \
  src/test/kotlin/sgspe/privacy/ExportProjectsTest.kt src/test/kotlin/sgspe/common/ObjectDefinitionsApiTest.kt
git commit -m "$(cat <<'EOF'
perf(scope): el alcance y los permisos leen las definiciones de la caché reciente

ProjectReadScope y Permissions.has toman las definiciones de ObjectDefinitions.recent: una lista
de una analista deja de cargar project_assignment, la cadena del padre y project en cada petición.
Corrige el comentario que decía que wasichai las tenía en caché.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
git switch perf/cache-esquema && git merge --no-ff ce/t2 -m "chore: integra la tarea 2 del plan de la caché del esquema"
```

Expected: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.common.ObjectDefinitionsApiTest`,
`sgspe.common.RequestMemoFilterApiTest`, `sgspe.scope.ProjectReadScopeApiTest` (CI del PR o el coordinador).

---

### Tarea 3: Las guardas y los comandos leen las definiciones una vez por petición

**Files:**
- Modify: `src/main/kotlin/sgspe/common/WriteView.kt`, `TenantModel.kt`, `DependentsDeleteGuard.kt`, `TransitionChecks.kt`,
  `RequestMemo.kt` (comentario)
- Modify: `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`, `src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt`
  (`IssueAccessByScope`)
- Modify: `src/test/kotlin/sgspe/ArchitectureTest.kt`
- Modify: `src/test/kotlin/sgspe/common/TenantModelTest.kt`, `src/test/kotlin/sgspe/attention/RequestClosureListenerTest.kt`,
  `src/test/kotlin/sgspe/investment/CampaignCommittedTest.kt`, `src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt`,
  `src/test/kotlin/sgspe/evidence/EvidenceWriteGuardTenantTest.kt`, `src/test/kotlin/sgspe/common/RequestMemoFilterApiTest.kt`
  (comentario)

**Interfaces:**
- Consumes: `ObjectDefinitions.current(organizationId: UUID, name: String): ObjectDefinition` (Tarea 1); los dobles de
  `ObjectDefinitions` con Mockito responden por el nombre del método `"current"` y leen el objeto en `arguments[1]`.
- Produces: `TenantModel(definitions: ObjectDefinitions)`, `WriteView(definitions: ObjectDefinitions, access: AccessPolicy,
  currentUser: CurrentUser)`, `EvidenceWriteGuard(…, definitions: ObjectDefinitions, …)` (el parámetro `metadata` pasa a
  llamarse `definitions`, en la misma posición), `DependentsDeleteGuard(catalog, definitions, schemas, db)`,
  `TransitionChecksController(rules, records, definitions, catalog, callers)`, `IssueAccessByScope(catalog, callers,
  permissions, definitions, records, resolver, assignments)`.

- [x] **Paso 1: Rama**

```bash
cd ~/IdeaProjects/sgspe && git switch perf/cache-esquema && git switch -c ce/t3
```

- [x] **Paso 2: Las pruebas que fallan**

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, después de la prueba `solo DerivedFieldsKeeper construye un DerivedRestore`:

```kotlin
    // la caché del esquema (plan corto del 2026-10-11): las definiciones de objeto se cargan solo por ObjectDefinitions (memo
    // por petición y caché reciente). Records conserva la suya en el camino de la plataforma: los trabajos de fondo corren
    // fuera de una petición, donde el memo no aplica
    @Test
    fun `solo ObjectDefinitions carga definiciones de objeto`() {
        val allowed = listOf("/sgspe/common/ObjectDefinitions.kt", "/sgspe/common/Records.kt")
        val others = sources(main).filterNot { file -> allowed.any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, LOAD_DEFINITION))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(allowed.first()) })
        assertTrue(flags(LOAD_DEFINITION, "metadata.loadDefinition(organizationId, name)"))
        assertTrue(flags(LOAD_DEFINITION, "metadata.loadDefinitionById(organizationId, id)"))
        assertTrue(flags(LOAD_DEFINITION, "val load = metadata::loadDefinition"))
        assertFalse(flags(LOAD_DEFINITION, "definitions.current(organizationId, name)"))
        assertFalse(flags(LOAD_DEFINITION, "// MetadataService.loadDefinition no tiene caché"))
    }
```

y en el `companion object`, junto a `READS_ALL`:

```kotlin
        private val LOAD_DEFINITION = Regex("""\bloadDefinition(ById)?\b""")
```

En `src/test/kotlin/sgspe/common/TenantModelTest.kt`, reemplace el doble y agregue la prueba del memo. El archivo queda:

```kotlin
package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import wasichai.core.common.NotFoundException
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// los objetos que tiene un tenant (TenantModel): uno sin el modelo de F4 no tiene las QCS, y su definición no existe
// (NotFoundException de wasichai). ObjectDefinitions es un doble con el modelo de F3 que cuenta las preguntas
class TenantModelTest {
    private val organization = UUID.randomUUID()
    private var asked = 0

    private val f3: ObjectDefinitions =
        mock(ObjectDefinitions::class.java) { invocation ->
            if (invocation.method.name != "current") return@mock null
            asked++
            if (invocation.arguments[1] in Attention.OBJECTS) throw NotFoundException("sin ${invocation.arguments[1]}")
            mock(ObjectDefinition::class.java)
        }

    @Test
    fun `un tenant con el modelo de F3 no tiene las QCS y si los compromisos`() {
        val model = TenantModel(f3)
        runBlocking {
            assertFalse(model.has(organization, Attention.REQUEST))
            assertTrue(model.has(organization, "commitment"))
            assertEquals(emptyList<String>(), model.present(organization, Attention.OBJECTS))
            assertEquals(listOf("commitment"), model.present(organization, listOf("grievance", "commitment")))
        }
    }

    // la ausencia solo vive lo que la petición: el objeto que trae una subida de fase se ve en la siguiente
    @Test
    fun `dentro de una peticion cada objeto se pregunta una vez, tambien el que falta`() {
        val model = TenantModel(f3)
        runBlocking { RequestMemo.forRequest { repeat(3) { model.present(organization, Attention.OBJECTS + "commitment") } } }
        assertEquals(Attention.OBJECTS.size + 1, asked)
        runBlocking { RequestMemo.forRequest { model.has(organization, Attention.REQUEST) } }
        assertEquals(Attention.OBJECTS.size + 2, asked)
    }
}
```

En los dobles de `TenantModel` de otras pruebas, `mock(MetadataService::class.java)` pasa a
`mock(ObjectDefinitions::class.java)` y `"loadDefinition"` a `"current"`:

- `src/test/kotlin/sgspe/attention/RequestClosureListenerTest.kt` (`model(withF4)`): agregue
  `import sgspe.common.ObjectDefinitions` y quite `import wasichai.core.metadata.MetadataService` (no queda otro uso).
- `src/test/kotlin/sgspe/investment/CampaignCommittedTest.kt` (`tenant(withF5)`): agregue
  `import sgspe.common.ObjectDefinitions` y quite `import wasichai.core.metadata.MetadataService` si no queda otro uso
  (compruebe con `grep -n MetadataService` antes de quitarlo).
- `src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt`: la línea
  `private val tenant = TenantModel(mock(MetadataService::class.java) { throw NotFoundException("sin el modelo de F5") })`
  pasa a `private val tenant = TenantModel(mock(ObjectDefinitions::class.java) { throw NotFoundException("sin el modelo de F5") })`;
  agregue `import sgspe.common.ObjectDefinitions`; `MetadataService` sigue en uso por `Records`.
- `src/test/kotlin/sgspe/evidence/EvidenceWriteGuardTenantTest.kt` (`guard(withF4)`): el argumento con nombre
  `metadata = mock(MetadataService::class.java)` pasa a `definitions = mock(ObjectDefinitions::class.java)`, y el doble de
  `TenantModel` como arriba; agregue `import sgspe.common.ObjectDefinitions` y quite
  `import wasichai.core.metadata.MetadataService` (no queda otro uso).

En `src/test/kotlin/sgspe/common/RequestMemoFilterApiTest.kt`, en el comentario de la clase, «(frágil: depende del formato
del log, de la caché de definiciones de wasichai y de lo que corra en paralelo)» pasa a «(frágil: depende del formato del
log, de la caché del esquema de SGSPE y de lo que corra en paralelo)».

- [x] **Paso 3: Correrlas y ver que fallan**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ArchitectureTest' --tests 'sgspe.common.TenantModelTest'`
Expected: FAIL de compilación de las pruebas: `TenantModel(...)` espera un `MetadataService` y recibe un
`ObjectDefinitions` («Argument type mismatch»). La prueba nueva de `ArchitectureTest` fallaría además por sí sola con los
seis archivos de `src/main` que el Paso 4 migra.

- [x] **Paso 4: La implementación**

`src/main/kotlin/sgspe/common/TenantModel.kt` queda:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import java.util.UUID

// los objetos que una organización tiene aprovisionados en wasichai, dentro de una petición (Organizations solo vale en los
// trabajos de fondo, y ModelCatalog es el modelo del jar, el mismo para todos los tenants). un tenant sin el modelo de F4
// sigue con los objetos de F3: las reglas y los listeners de F3 que también miran las QCS preguntan aquí antes de leerlas.
// la definición se carga por organización, sin mirar los permisos de quien llama (ObjectDefinitions.current); su ausencia
// es el NotFoundException de wasichai, que solo se atrapa aquí y solo en esa llamada (como DependentsDeleteGuard). la
// respuesta, también el «no está», se memoriza solo por petición: el objeto que trae una subida de fase se ve en la siguiente
@Component
class TenantModel(
    private val definitions: ObjectDefinitions
) {
    suspend fun has(
        organizationId: UUID,
        objectName: String
    ): Boolean =
        RequestMemo.rememberForRequest(MEMO to (organizationId to objectName)) {
            try {
                definitions.current(organizationId, objectName)
                true
            } catch (_: NotFoundException) {
                false
            }
        }

    // los de `objectNames` que la organización tiene, en su orden
    suspend fun present(
        organizationId: UUID,
        objectNames: Collection<String>
    ): List<String> = objectNames.filter { has(organizationId, it) }

    companion object {
        private const val MEMO = "tenant-model"
    }
}
```

`src/main/kotlin/sgspe/common/WriteView.kt`: quite `import wasichai.core.metadata.MetadataService`; el constructor pasa a
`private val definitions: ObjectDefinitions,` en lugar de `private val metadata: MetadataService,`, y la línea

```kotlin
                val definition = metadata.loadDefinition(change.organizationId, change.objectName)
```

pasa a

```kotlin
                val definition = definitions.current(change.organizationId, change.objectName)
```

`src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`: quite `import wasichai.core.metadata.MetadataService`;
`private val metadata: MetadataService,` pasa a `private val definitions: ObjectDefinitions,` y
`metadata.loadDefinition(change.organizationId, source)` a `definitions.current(change.organizationId, source)` (el
`catch (_: NotFoundException)` que la rodea no cambia).

`src/main/kotlin/sgspe/common/TransitionChecks.kt` (`TransitionChecksController`): quite
`import wasichai.core.metadata.MetadataService`; `private val metadata: MetadataService,` pasa a
`private val definitions: ObjectDefinitions,` y `metadata.loadDefinition(user.organizationId, objectName).obj.id` a
`definitions.current(user.organizationId, objectName).obj.id`.

`src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`: quite `import wasichai.core.metadata.MetadataService` y agregue
`import sgspe.common.ObjectDefinitions`; `private val metadata: MetadataService,` pasa a
`private val definitions: ObjectDefinitions,` (misma posición) y, en `attached`,

```kotlin
        val definition = definitions.current(change.organizationId, EvidenceRules.EVIDENCE)
```

`src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt` (`IssueAccessByScope`): quite
`import wasichai.core.metadata.MetadataService` (si no queda otro uso en el archivo; compruebe con `grep -n MetadataService`)
y agregue `import sgspe.common.ObjectDefinitions`; `private val metadata: MetadataService,` pasa a
`private val definitions: ObjectDefinitions,` y `metadata.loadDefinition(user.organizationId, target.objectName).obj.id` a
`definitions.current(user.organizationId, target.objectName).obj.id` (el `catch (_: NotFoundException)` no cambia).

`src/main/kotlin/sgspe/common/RequestMemo.kt`: en el comentario de la clase, después de la línea que termina en
«evidencia restringida de una lista (UPS-24).», agregue:

```kotlin
//   también las definiciones de objeto (ObjectDefinitions.current) y lo que TenantModel responde: ninguna petición de SGSPE
//   cambia el modelo, lo cambian las de /api/metadata de wasichai, que no pasan por SGSPE después.
```

Compruebe que no queda ningún otro uso:

```bash
cd ~/IdeaProjects/sgspe && grep -rn "loadDefinition" src/main/kotlin | grep -v "^src/main/kotlin/sgspe/common/ObjectDefinitions.kt\|^src/main/kotlin/sgspe/common/Records.kt" | grep -v "^\S*:\s*//"
```

Expected: solo líneas de comentario (o nada).

- [x] **Paso 5: Correrlas y ver que pasan**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ArchitectureTest' --tests 'sgspe.common.TenantModelTest' --tests 'sgspe.attention.RequestClosureListenerTest' --tests 'sgspe.investment.CampaignCommittedTest' --tests 'sgspe.investment.SupportPolicyGuardTest' --tests 'sgspe.evidence.EvidenceWriteGuardTenantTest'`
Expected: PASS.

- [x] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/common/WriteView.kt src/main/kotlin/sgspe/common/TenantModel.kt \
  src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt src/main/kotlin/sgspe/common/TransitionChecks.kt \
  src/main/kotlin/sgspe/common/RequestMemo.kt src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt \
  src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt src/test/kotlin/sgspe/ArchitectureTest.kt \
  src/test/kotlin/sgspe/common/TenantModelTest.kt src/test/kotlin/sgspe/attention/RequestClosureListenerTest.kt \
  src/test/kotlin/sgspe/investment/CampaignCommittedTest.kt src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt \
  src/test/kotlin/sgspe/evidence/EvidenceWriteGuardTenantTest.kt src/test/kotlin/sgspe/common/RequestMemoFilterApiTest.kt
git commit -m "$(cat <<'EOF'
perf(common): las guardas y los comandos leen las definiciones una vez por petición

WriteView, TenantModel, DependentsDeleteGuard, TransitionChecks, EvidenceWriteGuard e
IssueAccessByScope leen con ObjectDefinitions.current. TenantModel memoriza su respuesta por
petición. ArchitectureTest exige que solo ObjectDefinitions (y Records para la plataforma)
llame a loadDefinition.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
git switch perf/cache-esquema && git merge --no-ff ce/t3 -m "chore: integra la tarea 3 del plan de la caché del esquema"
```

Expected: `BUILD SUCCESSFUL`. Integración pendiente (CI del PR, suite completa): en especial
`sgspe.common.TransitionChecksApiTest`, `sgspe.common.CrossGuardsApiTest`, `sgspe.scope.IssueDocumentApiTest`,
`sgspe.scope.ProjectWorkflowGuardsApiTest`, `sgspe.ContributionApiTest` y las `*ApiTest` de `sgspe.evidence`.

---

### Tarea 6: Los comandos de SGSPE leen las asignaciones una vez por usuario y proyecto

Agregada al plan con su aprobación (2026-10-11); se ejecuta después de la Tarea 3 y antes de la 4, sobre
`perf/cache-esquema` con las Tareas 1 a 3 integradas. Se numera 6 para no renumerar las demás.

**Files:**
- Modify: `src/main/kotlin/sgspe/common/RequestMemoFilter.kt` (el memo del comando en `/api/sgspe/`; comentario)
- Modify: `src/main/kotlin/sgspe/common/RequestMemo.kt` (comentario del memo de un comando)
- Modify: `src/main/kotlin/sgspe/scope/Assignments.kt` (comentario de `isAssigned`)
- Modify: `src/test/kotlin/sgspe/common/RequestMemoFilterTest.kt`
- Create: `src/test/kotlin/sgspe/scope/AssignmentMemoApiTest.kt`

**Interfaces:**
- Consumes: `RequestMemo.within(block)` y `RequestMemo.remember(key, compute)` (sin cambios); `Assignments.isAssigned(userId:
  UUID, project: UUID): Boolean`, que ya memoriza con `RequestMemo.remember("project_assignment:<usuario>:<proyecto>")`;
  `ProjectScopeGuard`, que la pregunta en cada alta, cambio, transición y vínculo de un objeto con proyecto (sin cambios).
- Produces: `RequestMemoFilter` abre, además del memo de la petición, el memo de un comando (`RequestMemo.within`) en toda
  petición `POST`, `PUT`, `PATCH` o `DELETE` bajo `/api/sgspe/`. Ninguna firma pública cambia.

**Contexto.** `ProjectScopeGuard` (vía `Assignments.isAssigned`) relee `project_assignment` en cada juicio: en el alta de
una entrega con tres evidencias lo hace unas 7 veces (el alta y los dos extremos de cada vínculo `evidence_delivery`), unas
60 consultas, porque los comandos de `/api/sgspe/**` (`ContributionDeliveries`, `CommitmentDeliveries`, `Evidences`,
`Acceptances`, `Referrals`, `Reclassifications`, `Reschedules`, `Candidates`, `RiskAssessments`, `Assessments`, …) no abren
`RequestMemo.within` (solo la confirmación de una importación lo hace). `RequestMemo.remember` sin memo de comando calcula
cada vez, y el memo de la petición (`rememberForRequest`) no admite asignaciones (una petición de `/api/objects` sí las
escribe). La solución es la de la API genérica: el filtro que ya abre el memo de la petición abre también el de un comando
en las escrituras de `/api/sgspe/`. Es seguro porque ningún comando de `/api/sgspe/` escribe `project_assignment`,
`contribution_item` ni `commitment_stakeholder` (lo único que hoy guarda ese memo: `isAssigned`, `Records.itemsOf`,
`Records.stakeholdersOf`; comprobado con `grep -rn "records\.\(create\|update\|link\|unlink\|delete\)(" src/main/kotlin`), y
las guardas que juntan reglas (`CommitmentWriteGuard`, `InvestmentWriteGuard`) abren el suyo, que tapa al del comando. Una
asignación nunca se guarda entre peticiones: el memo vive lo que el comando. Las lecturas (`GET`) y la API genérica no
cambian: la segunda escribe un registro por petición.

- [x] **Paso 1: Rama**

```bash
cd ~/IdeaProjects/sgspe && git switch perf/cache-esquema && git switch -c ce/t6
```

- [x] **Paso 2: Las pruebas que fallan**

`src/test/kotlin/sgspe/common/RequestMemoFilterTest.kt` queda:

```kotlin
package sgspe.common

import kotlinx.coroutines.reactor.mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.WebFilterChain
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// RequestMemoFilter: toda petición de /api abre su propio memo, que llega al controlador por el contexto de la corrutina (como
// lo pasa Spring a un controlador suspend); fuera de /api no hay memo. un comando de SGSPE (una escritura bajo /api/sgspe/)
// abre además el memo del comando, donde Assignments.isAssigned guarda la asignación de quien escribe
class RequestMemoFilterTest {
    // cuántas veces se calcula una pregunta hecha tres veces por el controlador de una petición `method` a `path`: la de quien
    // llama (rememberForRequest, los permisos) o, con `command`, la de un comando (remember, la asignación de quien escribe)
    private fun computed(
        path: String,
        method: HttpMethod = HttpMethod.GET,
        command: Boolean = false
    ): Int {
        var calls = 0
        val compute: suspend () -> Boolean = {
            calls++
            true
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, path))
        val chain =
            WebFilterChain { forwarded ->
                val context = forwarded.getAttribute<CoroutineContext>(CoWebFilter.COROUTINE_CONTEXT_ATTRIBUTE) ?: EmptyCoroutineContext
                mono(context) {
                    repeat(3) {
                        if (command) RequestMemo.remember("asignacion", compute) else RequestMemo.rememberForRequest("permisos", compute)
                    }
                }.then()
            }
        RequestMemoFilter().filter(exchange, chain).block()
        return calls
    }

    @Test
    fun `una peticion de api pregunta una vez`() {
        assertEquals(1, computed("/api/objects/stakeholder_assessment/records?size=50"))
        assertEquals(1, computed("/api/sgspe/evidence", HttpMethod.POST))
    }

    @Test
    fun `dos peticiones no comparten el memo`() {
        assertEquals(2, computed("/api/sgspe/dashboards/indicators") + computed("/api/sgspe/dashboards/indicators"))
    }

    @Test
    fun `fuera de api no hay memo`() {
        assertEquals(3, computed("/actuator/health"))
        assertEquals(3, computed("/apix/otra"))
    }

    // el alta de una entrega con tres evidencias preguntaba unas 7 veces si quien escribe está asignado al proyecto
    @Test
    fun `un comando de SGSPE abre el memo del comando, y una lectura o la API generica no`() {
        val id = UUID.randomUUID()
        assertEquals(1, computed("/api/sgspe/social-contributions/$id/deliveries", HttpMethod.POST, command = true))
        assertEquals(1, computed("/api/sgspe/deliveries/$id", HttpMethod.PATCH, command = true))
        assertEquals(1, computed("/api/sgspe/evidence/$id/links/delivery/$id", HttpMethod.DELETE, command = true))
        assertEquals(3, computed("/api/sgspe/dashboards/indicators", command = true))
        assertEquals(3, computed("/api/objects/project_assignment/records", HttpMethod.POST, command = true))
        assertEquals(3, computed("/actuator/health", HttpMethod.POST, command = true))
    }

    // una asignación nunca pasa de un comando a otro: cambia al instante por /api/objects/project_assignment
    @Test
    fun `dos comandos no comparten la asignacion`() {
        val path = "/api/sgspe/evidence"
        assertEquals(2, computed(path, HttpMethod.POST, command = true) + computed(path, HttpMethod.POST, command = true))
    }
}
```

`src/test/kotlin/sgspe/scope/AssignmentMemoApiTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import sgspe.F5ApiTest
import sgspe.common.Records

// el memo del comando (plan corto de la caché del esquema, Tarea 6): un comando de /api/sgspe lee las asignaciones de quien
// escribe una vez por proyecto aunque ProjectScopeGuard pregunte en el alta y en los dos extremos de cada vínculo, y otro
// comando vuelve a leerlas (nada pasa de una petición a otra). en lugar de contar consultas en el log de SQL se espía Records y
// se cuentan sus lecturas de project_assignment filtradas por quien escribe (Assignments.isAssigned), como
// RequestMemoFilterApiTest cuenta las preguntas de permisos
class AssignmentMemoApiTest : F5ApiTest() {
    @MockitoSpyBean
    private lateinit var records: Records

    // las lecturas de project_assignment de `account` desde la última limpieza
    private fun assignmentReads(account: Account): Int =
        Mockito.mockingDetails(records).invocations.count { call ->
            call.method.name == "all" &&
                call.arguments[0] == Assignments.OBJECT &&
                (call.arguments[1] as? Map<*, *>)?.get("member_user_id") == account.id
        }

    @Test
    fun `el alta de una entrega con tres evidencias lee las asignaciones de quien la captura una vez`() {
        val project = testProject()
        val manager = member("SOCIAL_INVESTMENT_MANAGER", project)
        val contribution = approvedContribution(project, manager, procure = true)
        val evidence = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE").map { evidence(project, it, account = manager) }
        val headers = ifMatch("social_contribution", contribution, manager.token) + withReason("entrega")
        val body =
            mapOf(
                "attributes" to mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva de la escuela"),
                "evidence" to evidence
            )
        Mockito.clearInvocations(records)
        val captured = exchange("POST", "/api/sgspe/social-contributions/$contribution/deliveries", body, manager.token, headers)
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        assertEquals(1, assignmentReads(manager))
        // otro comando vuelve a leerlas: el memo dura lo que el comando
        val file = stagedFile("evidence", "file", manager)
        Mockito.clearInvocations(records)
        val other =
            createEvidence(mapOf("file" to file, "evidence_type" to "FOTO", "classification" to "INTERNA", "project" to project), token = manager.token)
        assertEquals(HttpStatus.CREATED, other.status, other.body)
        assertEquals(1, assignmentReads(manager))
    }
}
```

- [x] **Paso 3: Correr la unitaria y ver que falla**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.RequestMemoFilterTest'`
Expected: FAIL en `un comando de SGSPE abre el memo del comando, y una lectura o la API generica no` («expected: <1> but
was: <3>»: sin el memo del comando, cada pregunta se calcula); las otras cuatro pasan. `AssignmentMemoApiTest` compila
(`compileTestKotlin`) y fallaría en la CI con unas 7 lecturas (integración pendiente).

- [x] **Paso 4: La implementación**

`src/main/kotlin/sgspe/common/RequestMemoFilter.kt` queda:

```kotlin
package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange

// abre el memo de la petición (RequestMemo.forRequest) para toda petición de /api (especificación 4.4 §3.10; la medición de la
// Tarea 17 del plan 4.4a lo pidió: una lista de 50 evaluaciones como ANALYST tardaba el doble que como ADMIN por las preguntas
// de permisos, no por el SQL del alcance). la pregunta de permisos de ProjectReach.readsAll y ConfidentialAttentionScope se
// hace una vez por petición y no una vez por objeto y lectura. CoWebFilter pasa el contexto de la corrutina al controlador
// (COROUTINE_CONTEXT_ATTRIBUTE de Spring), así que el memo llega a RecordService y a los RecordReadScope. el memo de la
// petición solo guarda lo que una petición no cambia, los permisos de quien llama (RequestMemo explica por qué).
// un comando de SGSPE (POST, PUT, PATCH o DELETE bajo /api/sgspe/) abre además el memo del comando (RequestMemo.within; plan
// de la caché del esquema, Tarea 6): ProjectScopeGuard pregunta Assignments.isAssigned en el alta y en cada vínculo, y el alta
// de una entrega con tres evidencias leía project_assignment unas 7 veces. vale porque ningún comando de /api/sgspe escribe
// asignaciones, ítems de aporte ni solicitantes de un compromiso (lo que guarda ese memo). la API genérica escribe un registro
// por petición y no lo abre. cada petición abre memos nuevos: nada pasa de una a otra
@Component
@Order(RequestMemoFilter.ORDER)
class RequestMemoFilter : CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain
    ) {
        val path =
            exchange.request.path
                .pathWithinApplication()
                .value()
        if (!path.startsWith("/api/")) return chain.filter(exchange)
        if (path.startsWith(COMMANDS) && exchange.request.method in WRITES) {
            RequestMemo.forRequest { RequestMemo.within { chain.filter(exchange) } }
        } else {
            RequestMemo.forRequest { chain.filter(exchange) }
        }
    }

    companion object {
        // después de la cadena de seguridad (-100) y del filtro de documentos (IssueDocumentFilter, -99): quien llama ya está
        // autenticado. common no conoce scope, por eso el número
        const val ORDER = -98

        // los comandos de SGSPE: sus escrituras abren el memo del comando
        private const val COMMANDS = "/api/sgspe/"
        private val WRITES = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)
    }
}
```

`src/main/kotlin/sgspe/common/RequestMemo.kt`: en el comentario de la clase, reemplace las cuatro líneas de la primera viñeta
(de «// - el de un comando (`within` y `remember`): la confirmación de una importación crea cientos de componentes, y» a
«SgspeWrite). solo para datos que el comando no cambia: sus asignaciones, los solicitantes de un compromiso») por:

```kotlin
// - el de un comando (`within` y `remember`): RequestMemoFilter lo abre en cada comando de SGSPE (POST, PUT, PATCH o DELETE
//   bajo /api/sgspe/; plan de la caché del esquema, Tarea 6), y la confirmación de una importación y las guardas que juntan
//   reglas abren el suyo, que tapa al de afuera. ProjectScopeGuard pregunta en cada alta, cambio y vínculo si quien escribe
//   está asignado al proyecto (el alta de una entrega con tres evidencias, unas 7 veces; una importación, cientos), y
//   RecordService llama a las guardas en la corrutina del comando (como la marca SgspeWrite). solo para datos que el comando
//   no cambia: sus asignaciones, los ítems de un aporte, los solicitantes de un compromiso (ningún comando los escribe)
```

`src/main/kotlin/sgspe/scope/Assignments.kt`: el comentario de `isAssigned` (las dos líneas que empiezan con «si el usuario
tiene una asignación vigente al proyecto») pasa a:

```kotlin
    // si el usuario tiene una asignación vigente al proyecto. dentro del memo de un comando (RequestMemoFilter lo abre en cada
    // comando de /api/sgspe; la confirmación de una importación, el suyo) se lee una vez por usuario y proyecto, no una vez
    // por registro escrito o vinculado. nunca entre peticiones: una asignación cambia al instante por /api/objects
```

- [x] **Paso 5: Correr la unitaria y ver que pasa**

Run: `cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.RequestMemoFilterTest' --tests 'sgspe.common.RequestMemoTest'`
Expected: PASS, 9 pruebas.

- [x] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/common/RequestMemoFilter.kt src/main/kotlin/sgspe/common/RequestMemo.kt \
  src/main/kotlin/sgspe/scope/Assignments.kt src/test/kotlin/sgspe/common/RequestMemoFilterTest.kt \
  src/test/kotlin/sgspe/scope/AssignmentMemoApiTest.kt
git commit -m "$(cat <<'EOF'
perf(common): los comandos de SGSPE leen las asignaciones una vez por petición

RequestMemoFilter abre el memo del comando en las escrituras de /api/sgspe/: ProjectScopeGuard
lee project_assignment una vez por usuario y proyecto, no en el alta y en cada vínculo (unas 7
veces en el alta de una entrega con tres evidencias). Nada pasa de una petición a otra.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
git switch perf/cache-esquema && git merge --no-ff ce/t6 -m "chore: integra la tarea 6 del plan de la caché del esquema"
```

Expected: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.scope.AssignmentMemoApiTest`,
`sgspe.common.RequestMemoFilterApiTest`, `sgspe.ContributionApiTest` y sus subclases, `sgspe.commitment.DeliveryApiTest`,
`sgspe.evidence.EvidenceApiTest`, `sgspe.territory.ImportLimitsApiTest` (CI del PR o el coordinador).

---

### Tarea 4: Medición antes y después por el túnel (coordinador)

**Files:**
- Ninguno versionado. Las cifras van a `$TMPDIR/sgspe-cache-esquema-medicion.txt` y las anota la Tarea 5 en
  `docs/roadmap.md`.

**Interfaces:**
- Consumes: las Tareas 1, 2, 3 y 6 integradas en `perf/cache-esquema`; `main` en `06a47f2` o posterior (el «antes»); la base
  `sgspe_medicion5` de la medición de 5.2b (si ya no existe, se recrea con los Pasos 2 y 3 de la Tarea 13 del plan 5.2b en
  una base nueva), con `analista-medicion@sgspe.test` (`ANALYST`) e `inversion-medicion@sgspe.test`
  (`SOCIAL_INVESTMENT_MANAGER`) asignadas a «Proyecto Ejemplo A».
- Produces: la tabla antes/después y el desglose del POST que usa la Tarea 5; la decisión de abrir el issue del anexo A
  (la toma el usuario).

**Solo el coordinador principal**: exige base, túnel y backend local. Ningún subagente la ejecuta.

- [x] **Paso 1: El backend con el log de SQL**

Con el túnel abierto (puerto 5435), en un worktree de `main` para el «antes» y en el de `perf/cache-esquema` para el
«después» (uno a la vez, en el puerto 8094), en segundo plano:

```bash
cd <worktree> && WASICHAI_DB_NAME=sgspe_medicion5 GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) \
  ./gradlew bootRun --args='--logging.level.io.r2dbc.postgresql.QUERY=DEBUG' > "$TMPDIR/sgspe-<antes|despues>.log" 2>&1
```

Y la función que mide una petición por vez (las consultas que el log suma durante la petición, en total y por palabra
clave; una consulta puede contar en dos claves):

```bash
LOG="$TMPDIR/sgspe-<antes|despues>.log"
medir() { # $1 token, $2 método, $3 ruta, $4 archivo con el cuerpo (opcional), después: cabeceras extra
  local antes=$(wc -l < "$LOG") token=$1 metodo=$2 ruta=$3 cuerpo=${4:-}
  shift $(( $# < 4 ? $# : 4 ))
  curl -s -o /dev/null -w "$metodo $ruta %{http_code} %{time_total}s\n" -X "$metodo" "localhost:8094$ruta" \
    -H "Authorization: Bearer $token" ${cuerpo:+-H 'Content-Type: application/json' --data @"$cuerpo"} "$@"
  tail -n +$((antes + 1)) "$LOG" | grep 'Executing query' > "$TMPDIR/sgspe-peticion.log"
  echo "total $(wc -l < "$TMPDIR/sgspe-peticion.log")"
  for k in custom_objects custom_fields my_roles own_records_only permissions; do
    echo "$k $(grep -c "$k" "$TMPDIR/sgspe-peticion.log")"
  done
}
```

Los tokens, con `POST /api/auth/login` como en la Tarea 13 de 5.2b (la contraseña nunca va a un archivo).

- [x] **Paso 2: Las listas y `transition-checks`, antes y después**

Como `analista-medicion` y como el admin, cada una tres veces seguidas (se anota la segunda y la tercera: la primera calienta
la caché):

```bash
for r in '/api/objects/program_measurement/records?size=50' '/api/objects/program_measurement/records?size=1' \
         '/api/objects/delivery/records?size=50' '/api/objects/delivery/records?size=1'; do
  for i in 1 2 3; do medir "$TOKEN_ANALISTA" GET "$r"; done
  for i in 1 2 3; do medir "$TOKEN_ADMIN" GET "$r"; done
done
```

Y `transition-checks` del aporte de plantilla como `inversion-medicion` (`{"object":"social_contribution","recordId":
"<:tpl_contribution>","transition":"register_delivery"}` en un archivo de `$TMPDIR`), tres veces.

Esperado después (definición de terminado): en las listas, las consultas de la analista son **a lo sumo las del admin + 4**
y en la segunda y la tercera no aparece ningún par `custom_objects`/`custom_fields` además del de wasichai (uno por
petición); el tiempo de la analista baja en unos 0,3 s por lista. Si no se cumple, se diagnostica con el desglose antes
de seguir (superpowers:systematic-debugging) y no se pasa a la Tarea 5.

- [x] **Paso 3: El alta de una entrega con tres evidencias, antes y después (diagnóstico del POST de 14 s)**

Como `inversion-medicion`, para cada medición (antes y después) un aporte nuevo en `procured` sin entrega, con los pasos
del Paso 2 de la Tarea 13 de 5.2b (alta con un ítem BIEN, `approve_direct`, `register_procurement`), y tres evidencias del
mismo proyecto: subir un PDF a `POST /api/objects/evidence/files/file` (multipart, parte `file`) y registrarlo con
`POST /api/sgspe/evidence` (`{"attributes":{"file":"<id>","evidence_type":"ACTA DE RECEPCION"|"FOTO"|"COMPROBANTE",
"classification":"INTERNA","project":"<:tpl_project>"}}`). Luego, con el `ETag` del aporte:

```bash
medir "$TOKEN_INVERSION" POST "/api/sgspe/social-contributions/$APORTE/deliveries" "$TMPDIR/entrega.json" \
  -H "If-Match: $ETAG_APORTE" -H "X-Change-Reason: medicion de la cache del esquema"
```

con `entrega.json` = los atributos de la entrega del Paso 2 de la Tarea 13 de 5.2b y `"evidence": [<las tres>]`.

Se anota: tiempo, consultas totales y el desglose (`custom_objects`, `my_roles`, `own_records_only`, `permissions`, el resto).
Esperado después: al menos un 40 % menos de consultas que antes (el diagnóstico estima de 260-320 a 110-160). Con los logs
del «antes» y del «después», el coordinador cuenta además cuántas listas de `project_assignment` hace el POST (`grep -c` del
nombre de su tabla física en las consultas de datos): la repetición de `Assignments.isAssigned` de la decisión 8, que la
Tarea 6 deja en una lista por usuario y proyecto.

- [x] **Paso 4: Las cifras**

En `$TMPDIR/sgspe-cache-esquema-medicion.txt`, una tabla con: petición, perfil, consultas antes, consultas después, tiempo
antes, tiempo después; el desglose del POST; y la parte de wasichai que queda (definición del objeto y consultas de rol por
operación). El coordinador muestra la tabla al usuario con el anexo A y le pregunta si abre el issue upstream; no lo abre
sin su aprobación.

---

### Tarea 5: Documentos, verificación final, push y PR

**Files:**
- Modify: `docs/security.md` (§9.7, después de la viñeta «Falla cerrado»)
- Modify: `docs/roadmap.md` (fila «Estado» de F5)
- Modify: `CLAUDE.md` (tabla «Dónde está cada cosa»)
- Modify: `docs/superpowers/plans/2026-10-11-cache-esquema.md` (este plan, versionado en el primer commit de la rama, con sus casillas marcadas)

**Interfaces:**
- Consumes: las Tareas 1, 2, 3 y 6 integradas; las cifras de la Tarea 4 (`$TMPDIR/sgspe-cache-esquema-medicion.txt`).
- Produces: la rama `perf/cache-esquema` con su PR en verde e integrada en `main`.

- [x] **Paso 1: `docs/security.md` §9.7**

Después de la viñeta que empieza con «**Falla cerrado:** un objeto sin clasificar en `ProjectScope`…», agregue:

```markdown
- **Definiciones en caché (plan corto de la caché del esquema):** las tablas y columnas de la condición salen de
  `ObjectDefinitions.recent` (`sgspe.common`): definiciones de objeto de hasta `sgspe.metadata.definition-ttl` (30 s)
  atrás, por organización y objeto, con un tope de entradas; wasichai 1.0.0 no las guarda ni avisa sus cambios. Una
  definición vieja tras `apply.py` solo puede dejar la condición incompleta, acotarla por menos padres o hacer fallar el
  SQL: durante a lo sumo 30 s tras subir un tenant de fase una persona ve menos registros, nunca más. Una ausencia nunca
  se guarda entre peticiones. `Permissions.has` toma de ahí solo el id del objeto. Las guardas, `WriteView`, `TenantModel`
  y los comandos leen la definición de la petición (`ObjectDefinitions.current`), sin caché entre peticiones, y los
  permisos nunca se guardan entre peticiones. `0s` apaga la caché. `ArchitectureTest` exige que solo `ObjectDefinitions`
  (y `Records` en el camino de la plataforma) llame a `MetadataService.loadDefinition`.
```

- [x] **Paso 2: `docs/roadmap.md`, fila «Estado» de F5**

Reemplace «quedan la caché del esquema (un plan corto, antes de F6) y el plan 5.3 (portal)» por «integrada la caché del
esquema (plan corto `2026-10-11-cache-esquema`) y queda el plan 5.3 (portal)», y agregue al final de la celda, con las
cifras de la Tarea 4: «Medición de la caché del esquema (<fecha>): una lista de 50 mediciones o entregas como analista,
de <n> a <n> consultas y de <t> a <t> ms (el admin, <n> consultas y <t> ms; la diferencia que queda son las tres consultas
de rol de wasichai y la de `READ_ALL_PROJECTS`); `transition-checks`, de <t> a <t> s; el alta de una entrega con tres
evidencias, de <n> a <n> consultas y de <t> a <t> s, con la repetición de `Assignments.isAssigned` resuelta (de <n> listas
de `project_assignment` por alta a una: `RequestMemoFilter` abre el memo del comando en las escrituras de `/api/sgspe/`).
Queda en el backlog, upstream, la caché de definiciones y el memo de las consultas de rol de wasichai (<issue si el usuario
lo aprobó, o «propuesto, sin abrir»>)». Cada `<…>` es una cifra o un dato de `$TMPDIR/sgspe-cache-esquema-medicion.txt`; ninguno queda
sin llenar.

- [x] **Paso 3: `CLAUDE.md`**

En la fila de la tabla «Dónde está cada cosa» que empieza con «Marca de escritura, `Records`, Lima, 422, catálogo del
modelo…», agregue «, la caché del esquema (`ObjectDefinitions`: `current` por petición, `recent` hasta 30 s para el alcance y
los permisos)» después de «`RequestMemo`».

- [x] **Paso 4: Verificación local**

```bash
cd ~/IdeaProjects/sgspe && yarn format && yarn format:check && python3 tools/check_traceability.py
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: formato y trazabilidad sin errores, `BUILD SUCCESSFUL`.

- [x] **Paso 5: Commit, push y PR**

```bash
cd ~/IdeaProjects/sgspe && git switch perf/cache-esquema
git add docs/security.md docs/roadmap.md CLAUDE.md docs/superpowers/plans/2026-10-11-cache-esquema.md
git commit -m "$(cat <<'EOF'
docs: la caché del esquema en security.md, el roadmap y CLAUDE.md

security.md §9.7 dice de dónde salen las definiciones del alcance y por qué una vieja falla
cerrado; el roadmap anota la medición antes y después, las asignaciones leídas una vez por
comando y lo que queda en el backlog.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
git push -u origin perf/cache-esquema
gh pr create --base main --head perf/cache-esquema --title "perf: caché del esquema del alcance de lectura por proyecto" \
  --body "$(cat <<'EOF'
Plan corto `docs/superpowers/plans/2026-10-11-cache-esquema.md`.

- `ObjectDefinitions` (`sgspe.common`): `current` carga cada definición de objeto una vez por petición; `recent` la guarda
  además hasta `sgspe.metadata.definition-ttl` (30 s), solo para `ProjectReadScope` y `Permissions.has`, donde una
  definición vieja falla cerrado. Sin ausencias guardadas, con tope de entradas; los permisos nunca se guardan entre
  peticiones.
- Las guardas, `WriteView`, `TenantModel` y los comandos leen con `current`; `ArchitectureTest` exige que solo
  `ObjectDefinitions` (y `Records` para la plataforma) llame a `loadDefinition`.
- `RequestMemoFilter` abre el memo de un comando en las escrituras de `/api/sgspe/`: `Assignments.isAssigned` lee
  `project_assignment` una vez por usuario y proyecto en cada comando (antes, en el alta y en cada vínculo), nunca entre
  peticiones.
- Medición por el túnel en `docs/roadmap.md` (fila F5).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

Expected: el PR creado. Se espera la CI en verde (unitarias, integración completa con `ObjectDefinitionsApiTest`,
`RequestMemoFilterApiTest`, `ProjectReadScopeApiTest`, `AssignmentMemoApiTest`, `PermissionMatrixApiTest` y las de
evidencias e inversión) y se
integra en `main` como los PR de F5. Un rojo se diagnostica con superpowers:systematic-debugging antes de tocar código.

---

## Anexo A: issue propuesto para wasichai (no abrir sin la aprobación del usuario)

Cuánto del costo es de cada uno, con las cifras de este plan: en una lista de una analista, antes, unas 9 de 18 a 20
consultas son de SGSPE (8 de definiciones y 1 de permisos) y unas 9 a 10 de wasichai (2 de la definición del objeto, 3 de
rol, 1 del estado del workflow, 2 de datos y la sesión). Después de este plan, SGSPE pone 1 y wasichai las mismas 9 a 10:
**la mayor parte de lo que queda es de wasichai**, unas 6 de sus consultas de metadata y rol por lectura (unos 0,2 s por
el túnel). En el alta de una entrega con tres evidencias, tras este plan, unas 100 de las consultas que quedan son la
definición y las consultas de rol que wasichai repite en cada `get`, `list`, `create` y `link` (unos 3,5 a 4 s por el
túnel). Texto del issue, en inglés como los de wasichai:

```markdown
### Metadata and role checks: cache object definitions and memoize role queries per request

**Context.** An app on wasichai 1.0.0 (SGSPE) measured its record lists through a ~35 ms-per-query link: a non-admin
caller pays 18–20 queries per `GET /api/objects/{object}/records`, an admin 6–7. After the app cached its own reads of
object definitions, a non-admin list still costs about 10 queries, and a command that creates one record and links three
others about 200. Most of what remains is the platform's per-operation metadata and role lookups, repeated on every
`get`, `list`, `create` and `link` in the same request:

- `MetadataService.loadDefinition` has no cache: every call is two queries (`custom_objects` and `custom_fields`).
  `RecordService`, `RelatedRecordService` and `RelationTargets` load the same definitions again for each operation.
- For a non-admin caller every read also runs `RoleQueries.hasPermission`, `AccessPolicy.fieldAccess` and
  `AccessPolicy.ownRecordsOnly`, three queries that depend only on the caller's roles and the object, again for each
  operation in the request.

**Proposal.**
1. Cache `ObjectDefinition` by (organization, object name) inside `MetadataService`, invalidated by its own writes
   (`createObject`, `updateObject`, `deleteObject`, `addField`, `addRelationField`, `updateField`, `deleteField`, and
   relationship changes) after commit, with a short TTL (or a metadata version per organization) for multi-node
   deployments. Never cache "not found".
2. Memoize `hasPermission`, `fieldAccess` and `ownRecordsOnly` per request (coroutine or Reactor context), keyed by roles
   and object: permissions may change between requests, never inside one.
3. Publish a metadata-changed event (object or field created, updated, deleted; after commit) through a port like
   `ObjectRemovalListener`, so apps that keep their own derived caches can invalidate instead of relying on a TTL.
4. Optionally expose the cached lookup (for example `MetadataService.cachedDefinition(organizationId, name)`) so a
   `RecordReadScope` that needs other objects' definitions (a parent chain) does not need its own cache.

**Acceptance.** A non-admin list costs the same queries as an admin list plus at most one; a request with N record
operations on the same objects loads each definition and each role answer once.
```

Si el usuario lo aprueba, el coordinador lo abre en `wasichai/wasichai` con `gh issue create`, lo registra como la UPS
siguiente (hoy UPS-29) en `docs/requirements-analysis.md` y `docs/platform-fit.md` en un PR de documentos aparte, y la
Tarea 5 cita su número en el roadmap.
