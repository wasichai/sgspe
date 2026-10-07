# Fase 2.2 · Código propio del backend (Kotlin) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Escribir el código propio de SGSPE de la Fase 2 sobre wasichai 0.3.2: las guardas transversales y de alcance, las
guardas, listeners y trabajos de cada paquete de dominio, los trece endpoints de `/api/sgspe/**` de F2 con los contratos de
`docs/api.md`, los indicadores KPI-STK y KPI-ENG con sus dos tableros y la exportación `stakeholder-register`, con sus pruebas
de seguridad de F2.

**Architecture:** Paquetes `sgspe.common`, `sgspe.scope`, `sgspe.stakeholder`, `sgspe.engagement`, `sgspe.territory`,
`sgspe.dashboard` y `sgspe.privacy` (`docs/architecture.md` §5.2). Todo escribe por `sgspe.common.Records` (la única puerta a
`RecordService`, `RelatedRecordService` y `WorkflowService`, con la marca `SgspeWrite`); las reglas son `RecordWriteGuard`
de wasichai hechas de `WriteRule` que `POST /api/sgspe/transition-checks` puede correr en seco; los derivados los escriben
endpoints y listeners con la marca, y `DerivedFieldsJob` (JOB-02) los corrige. El modelo, los roles y los workflows de la
Fase 2.1 viajan dentro del jar (`ModelCatalog`): de ahí salen los campos derivados, los objetos con `EDIT_ANY`, los roles
declarados y los roles de cada transición, nunca de literales.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, wasichai 0.3.2 (core, `-gis`, `-workflow`; los
starters los agrega la Fase 2.1), PostGIS 3.6 (`ST_Transform`, `ST_Area`, `ST_HausdorffDistance`), Apache POI 5.5.1
(`poi-ooxml`) para leer y escribir XLSX, Jackson 3 (`tools.jackson`), JUnit 5, `wasichai-test` (`WasichaiIntegrationTest`).

**Spec:** `docs/roadmap.md` (fila F2), `docs/api.md` (§2, §3 y §4.1 a §4.4, §4.11, §4.13, §5), `docs/security.md` (§5, §7,
§8, §9.2, §14), `docs/workflows.md` (WF-A, WF-B, WF-C, WF-L, WF-O, «Transiciones de comando», «Transiciones con motivo
obligatorio», JOB-02, JOB-03, «Listeners», «Destinatarios por rol», «Pruebas»), `docs/domain-model.md` (§4.4 a §4.7, §7,
§8.2, §8.3), `docs/database-model.md` (fichas de F2, §2.4, §3.6), `docs/gis.md` (§2.2, §3, §4, §9), `docs/dashboards.md`
(§3, §4.2, §4.3, §5.3, §5.4, §6 a §8, §11), `docs/deliverables.md` (§9, §10), ADR-0019, ADR-0025 (se acepta al iniciar F2).
Este plan sigue `.superpowers/sdd/f2-plan-common.md`.

## Global Constraints

- wasichai `0.3.2` exacto; Kotlin `2.4.20`; Spring Boot `4.1.1`; JDK `25`; Gradle `9.8.0`; ktlint `1.7.1`; Apache POI
  `poi-ooxml:5.5.1` (5.4 o superior por CVE-2025-31672, `docs/platform-fit.md` §6).
- Imagen de pruebas `postgis/postgis:18-3.6`; base externa por el túnel `localhost:5435`, base `sgspe_test`, usuario y clave
  `sgspe`.
- Español (es-PE) en comentarios, mensajes y `detail` de los errores; nombres técnicos en inglés (ADR-0009). Constantes
  propias de la API en inglés UPPER_SNAKE (`STANCE_DIVERGENCE`, `FIELD_NOT_READABLE`), propiedades en camelCase y atributos
  con su nombre del modelo en snake_case (`docs/api.md` §3.2).
- Valores ENUM exactamente como los guarda el modelo, en español MAYÚSCULAS sin tildes (`EN CONTRA PERO ABIERTO A
  ESCUCHAR`, `PARA MONITOREO`, `EPSG 32717`) (ASM-025, checkpoint).
- Ningún literal de rol en `src/main/kotlin`, ningún código bajo el paquete `wasichai`, nunca `RecordStore`,
  `PhysicalTableRecordStore`, `AuditService` ni `ObjectSchemaManager`; `SgspeWrite` y `RecordService` solo en
  `sgspe/common` (`ArchitectureTest`). Las decisiones se toman con `requirePermission`, `isAdmin` y datos.
- Alcance opción A del checkpoint: lectura de todo el tenant y escritura acotada por `project_assignment` vigente
  (`docs/security.md` §9.2).
- `Idempotency-Key` se acepta y se ignora en F2 (`docs/api.md` §3.5, ASM-065); se honra desde F3.
- `If-Match` con el `updatedAt` entre comillas: obligatorio (428/412) en los comandos marcados `IM`; en la API genérica, un
  filtro y una guarda lo comparan si viene (`docs/api.md` §3.6, ADR-0025).
- Un parámetro de consulta desconocido en `/api/sgspe/**` es 422 (`StrictQuery`); una propiedad desconocida en el cuerpo
  de un comando es 400 que la nombra (`StrictBody`).
- Estados de las guardas: 400 si rechazan datos enviados; 409 si una transición choca con lo guardado o con otros
  registros; 403 si falta un permiso, una acción declarada, una asignación, la autoría (`EDIT_ANY`) o la marca de un
  comando.
- Fechas y periodos en America/Lima (`sgspe.common.LIMA`, `Clock`); instantes en UTC con `Z`.
- Datos de prueba ficticios (`@sgspe.test`, «Persona Ejemplo», códigos `TST-…`, «Asociación Ejemplo»), topónimos genéricos
  (RQ-SEC-018).
- Commits: Conventional Commits con asunto en español, cuerpo de 100 columnas como máximo, `git add` con rutas explícitas y
  los trailers `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. Nunca imprimir tokens.
- Antes de cada commit: `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat`.

### Comandos

Unitarias de una clase (sin base):

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase>'
```

Integración de una clase (con el túnel al PostGIS del VPS abierto; en la CI corre con Testcontainers):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'
```

Cada paso de este plan escribe el comando completo. La primera clase de integración de una JVM aprovisiona el modelo de F2
con `apply.py`, `apply_roles.py` y `apply.py --workflows` (la Fase 2.1); contra la base por el túnel eso tarda varios
minutos, así que conviene correr juntas las clases de una tarea. La suite vacía la base una vez por JVM: si otra corrida
usa `sgspe_test` al mismo tiempo, cree una base propia cuyo nombre termine en `_test`
(`docker compose -f compose.yml exec -T postgis createdb -U sgspe sgspe_f22_test`), póngala en `WASICHAI_TEST_DB_NAME` y
bórrela con `dropdb` al terminar.

## Review Focus

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la
tarea dueña del código:

1. **El portal reenvía un registro entero, con sus derivados o sin ellos.** El PUT de wasichai reemplaza el registro: si
   el portal omite un derivado lo borraría, y si lo manda la guarda podría rechazarlo. `DerivedFieldsGuard` solo rechaza un
   derivado enviado con un valor distinto del guardado, y `DerivedFieldsKeeper` devuelve el valor de los que el PUT dejó
   vacíos. Pruebas: `CrossGuardsApiTest` «el portal reenvía los derivados tal cual y pasa, cambiarlos es un 400» (Tarea 3)
   y `AttendanceCountsApiTest` «un PUT que omite los derivados, como el portal, los conserva» (Tarea 8).
2. **Un listener escribe derivados en el registro que el cliente está editando.** `AreaListener` recalcula el área del
   registro que el cliente acaba de cambiar, y `AttendanceCountsListener` los conteos de la interacción que el portal
   registra en varias escrituras: si `IfMatchGuard` comparara esa escritura con el `If-Match` de la petición, el área no se
   escribiría, y si el cliente quedara con el `updatedAt` del alta, su siguiente escritura sería un 412 sin conflicto real.
   Pruebas: `AreaApiTest` «un PUT con If-Match también recalcula el área» y «el updatedAt de la respuesta del alta sigue
   valiendo como If-Match» (Tarea 15), `IfMatchTest` «después de la versión leída, solo derivados y vínculos…» (Tarea 4) y
   `EngagementRegistrationApiTest` (Tarea 9).
3. **Una evaluación con fecha anterior a la última.** Cargar una evaluación atrasada no debe pisar los valores vigentes del
   grupo de interés con datos viejos. Prueba: `StakeholderAssessmentApiTest` «la segunda toma la primera como línea base…»,
   con la advertencia `BACKDATED_ASSESSMENT` (Tarea 12).
4. **Dos aprobaciones simultáneas de áreas de influencia del mismo proyecto y tipo.** Sin serializar, quedarían dos
   versiones `approved` (RN-TER-003). Prueba: `AreaOfInfluenceWorkflowGuardsApiTest` «dos aprobaciones simultáneas…»
   (Tarea 16).
5. **Una hoja CSV exportada de Excel en español.** BOM, separador «;» y coma decimal son lo normal en las hojas del Perú; leer
   mal un este o un norte desplaza un sondaje. Prueba: `ImportFilesTest` «un csv exportado de Excel con BOM se lee igual» y
   «csv con punto y coma, comillas y coma decimal» (Tarea 17).

---

## Mapa de archivos

Todos bajo la raíz del repositorio `sgspe`. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `build.gradle.kts` (modificar) | POI y el empaquetado de `model.json`, `roles.json` y `workflows/*.json` en `classpath:model/` | 1 |
| `src/main/kotlin/sgspe/common/ModelCatalog.kt` | Lee el modelo, los roles y los workflows del classpath | 1 |
| `src/test/kotlin/sgspe/ArchitectureTest.kt` (modificar) | Clasifica `scope` y `privacy`; `scope` y `privacy` solo dependen de `common`; `findAllOrganizations` solo en `Organizations.kt` | 1 |
| `src/main/kotlin/sgspe/common/Records.kt` (modificar) | La única puerta a `RecordService`, `RelatedRecordService` y `WorkflowService`, con la marca | 2 |
| `src/main/kotlin/sgspe/common/{WriteMark,Errors,Values,Callers,Permissions,Transactions,StrictBody,Criteria}.kt` | Marca legible fuera de common, 412/428, igualdad de valores, quién llama, permisos previos, transacción con candado, cuerpo estricto, criterios de lista | 2 |
| `src/main/kotlin/sgspe/common/{Rules,WriteView,DerivedFieldsGuard,DerivedFieldsKeeper,TransitionReasonGuard,FieldChangeReasonGuard,CommandTransitionGuard,ExactlyOneGuard,UserReferenceGuard}.kt` | Reglas y guardas transversales; los derivados que un PUT omite se conservan | 3 |
| `src/main/kotlin/sgspe/common/Preconditions.kt` | `If-Match`: parseo, filtro web, guarda y `Versions` (derivados posteriores no son conflicto) | 4 |
| `src/main/kotlin/sgspe/scope/{Assignments,ProjectScope,ProjectScopeGuard,AssignmentRoleGuard}.kt` | Asignaciones, destinatarios por rol, alcance de escritura | 5 |
| `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`, `src/main/kotlin/sgspe/common/{TransitionChecks,ProjectParameters}.kt` | WF-A y la consulta en seco | 6 |
| `src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt` | WF-C y la lista de asistentes | 7 |
| `src/main/kotlin/sgspe/engagement/AttendanceCounts.kt`, `src/main/kotlin/sgspe/common/{Organizations,DerivedFieldsJob}.kt` | Conteos de asistentes (listener y recalculador) y JOB-02 | 8 |
| `src/main/kotlin/sgspe/scope/EditAnyGuard.kt` | Edición de lo propio | 9 |
| `src/main/kotlin/sgspe/stakeholder/{InfluenceClassifier,Prioritizer}.kt` | Índices, grupo y prioridad (puros) | 10 |
| `src/main/kotlin/sgspe/stakeholder/StakeholderWriteGuard.kt` | WF-B, cargos y matriz de priorización | 11 |
| `src/main/kotlin/sgspe/stakeholder/{Assessments,AssessmentController}.kt` | Endpoint de evaluaciones y su vista previa | 12 |
| `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt` | JOB-03 | 13 |
| `src/main/kotlin/sgspe/territory/{Crs,GeoSql}.kt` | CRS y cuentas con PostGIS | 14 |
| `src/main/kotlin/sgspe/territory/{Areas,TerritoryWriteGuard}.kt` | Área oficial (listener y recalculador) y miembros congelados | 15 |
| `src/main/kotlin/sgspe/territory/AreasOfInfluence.kt` | Aprobación de áreas de influencia | 16 |
| `src/main/kotlin/sgspe/territory/ImportFiles.kt` | Lectura de XLSX, CSV y GeoJSON; carpeta temporal; límites | 17 |
| `src/main/kotlin/sgspe/territory/{TerritoryImports,ImportController}.kt` | Vista previa y confirmación de importaciones | 18 |
| `src/main/kotlin/sgspe/territory/ProjectComponents.kt` | Alta, versión y PATCH de componentes | 19 |
| `src/main/kotlin/sgspe/territory/Layers.kt` | Capas básicas LYR-01 a LYR-16 | 20 |
| `src/main/kotlin/sgspe/dashboard/{Indicators,StakeholderKpis,EngagementKpis}.kt` | Tipos de indicador y KPI puros | 21 |
| `src/main/kotlin/sgspe/dashboard/{DashboardFilters,DashboardData,Kpis,Dashboards}.kt` | Filtros, carga de datos, catálogo y endpoints | 22 |
| `src/main/kotlin/sgspe/privacy/{Disclosure,ExportLog,StakeholderRegister,ExportController}.kt` | Exportación con nivel de divulgación y `export_log` | 23 |
| `src/test/kotlin/sgspe/DomainApiTest.kt` | Base de las pruebas de integración de F2 | 3 |
| `CLAUDE.md` (modificar) | «Dónde está cada cosa» de F2 | 24 |

Las pruebas viven en `src/test/kotlin/sgspe/<paquete>/` con el nombre que cada tarea da.

## Interfaces con los otros planes de F2

### Lo que este plan asume de la Fase 2.1 (modelo y aprovisionamiento), que corre antes

- **`model/model.json`** con los 48 objetos de F2 y sus campos con los nombres exactos de las fichas de
  `docs/database-model.md`, y en particular:
  - `"derived": true` en: `exploration_campaign.executed_social_amount`, `other_currency_items_count`;
    `calculated_area_ha` de `area_of_influence`, `community`, `land_parcel` y `mining_concession`;
    `project_component.superseded`; `stakeholder.stance`, `influence_group`, `information_priority`, `interest_index`,
    `power_index`, `trust_score`, `last_assessment_date`, `last_contact_date`; `stakeholder_assessment.stance_changed`;
    los siete `attendees_*_count` de `engagement`; `strategic_alliance.formalized`.
  - Acciones declaradas (`actions: [{name, label}]`): `APPROVE` en `project` y `area_of_influence`; `EXPORT` en
    `stakeholder`; `EDIT_ANY` en `engagement`, `engagement_participant`, `field_visit` y `field_observation`.
  - Claves de `enums` (database-model §4) con los valores de §10 del análisis; este plan lee `stakeholder_scope`,
    `source_crs`, `working_crs`, `stance`, `influence_group` y `assessment_basis` (la de `stakeholder_assessment.basis`).
  - Relaciones MANY_TO_MANY con estos nombres: `engagement_stakeholder` (de `engagement` a `stakeholder`),
    `engagement_topic` (de `engagement` a `topic`) y `project_concession` (de `project` a `mining_concession`); y las
    MANY_TO_ONE con los campos de las fichas (`project`, `engagement`, `stakeholder`, `area_of_influence`, `parent_locality`,
    `parent_unit`, `administrative_unit`, `locality`, `community`, `person`, `organization`, `topic`, `previous_version`…).
  - Flags: `stakeholder_assessment` y `export_log` `appendOnly` y `apiOnly`; `stakeholder_assessment` `requiresReason`;
    `project_component` `apiOnly`.
- **`model/workflows/<objeto>.json`** para `project`, `stakeholder`, `engagement`, `field_visit` y `area_of_influence`, con
  el formato de `docs/workflows.md` y exactamente estas transiciones (nombre: de → a): WF-A `submit_for_review`,
  `return_to_draft`, `approve` (in_review → active), `suspend`, `resume` (suspended → active), `close`, `close_suspended`,
  `cancel_draft`, `cancel_in_review`; WF-B `profile` (identified → profiled), `assess` (profiled → assessed),
  `retire_identified`, `retire_profiled`, `retire_assessed`, `reinstate` (retired → identified); WF-C `schedule`,
  `register_held`, `register_held_unplanned` (planned → held), `postpone`, `reschedule`, `draft_minutes`, `submit_minutes`,
  `return_minutes`, `approve_minutes`, `close` (minutes_approved → closed), `close_light` (held → closed),
  `cancel_planned`, `cancel_scheduled`, `cancel_postponed`; WF-L `start`, `complete`, `complete_partial`, `cancel`
  (planned → cancelled); WF-O `approve` (draft → approved), `supersede` (approved → superseded). Los roles de cada
  transición son los de las tablas de `docs/workflows.md`.
- **`model/roles.json`** con las concesiones de F2 de `docs/security.md` §7.2, §7.3, §7.4 y §7.8 (incluido `C` sobre
  `export_log` para `PROJECT_MANAGER`, `SOCIAL_MANAGER`, `STAKEHOLDER_MANAGER` y `ANALYST`, y `u` de `GIS_ANALYST` sobre
  `project` para vincular concesiones), `roles[].name` con los 15 roles declarados (lo lee `AssignmentRoleGuard` en tiempo
  de ejecución: la Tarea 1 lo copia al classpath con `processResources` y `ModelCatalogTest` lo prueba) y la clave
  `restrictedFields` de §7.9 (sin lectura ni escritura de `stakeholder.stance` para todo rol salvo `SOCIAL_MANAGER` y
  `STAKEHOLDER_MANAGER`, `person.id_document_number`, etc.). `u` es UPDATE con los campos no derivados de solo lectura.
- **`apply.py`** que aplica relaciones MANY_TO_MANY, autorreferencias, acciones y `uniqueConstraints`, y **`apply.py
  --workflows`** que aplica `model/workflows/*.json`; `SgspeApiTest` aprovisiona con ellos una vez por JVM y da
  `SCRIPT_TIMEOUT_SECONDS = 1200L` a cada script (un tenant tarda de 6 a 8 minutos por el túnel). Las pruebas de este plan
  comparten ese tenant: ninguna aprovisiona otro, y una clase con `@TestPropertySource` (`ImportLimitsApiTest`) abre otro
  contexto de Spring pero no vuelve a aprovisionar.
- **`build.gradle.kts`** con `wasichai-spring-boot-starter-gis` y `wasichai-spring-boot-starter-workflow`.
- **`src/test/kotlin/sgspe/F2ApiTest.kt`** con las firmas de la sección «Interfaces» de
  `docs/superpowers/plans/2026-10-07-fase-2-1-modelo.md`: `project(attributes)`, `assign(account, projectId, role)`,
  `member(role, projectId = testProject())`, `transition(objectName, id, name, token, reason)`, `move(…)`, `state`,
  `fixture`, `sample`, `Model`. `DomainApiTest` (Tarea 3) la extiende y no redefine ninguna de esas funciones.
- **Pruebas de workflow de 2.1** (`sgspe.workflow.*WorkflowApiTest`): cubren roles, estados, 409 desde otro estado y el
  listado de transiciones, y sus registros ya cumplen las guardas de este plan. Los casos que 2.1 deja a este plan, uno por
  uno, están en clases propias de este plan, que extienden `DomainApiTest`, y no en las de 2.1: así este plan no edita
  archivos que entrega 2.1 y sus pruebas usan los ayudantes de `DomainApiTest`:

  | Caso que 2.1 asigna a 2.2 | Prueba de este plan (tarea) |
  |---|---|
  | `ProjectWorkflowApiTest`: `approve` sin `working_crs` o sin parte CLIENTE u OPERADOR; con la política en falso; `resume` verifica; doble llave; `stage` sin motivo; motivo obligatorio | `ProjectWorkflowGuardsApiTest` (6) |
  | `StakeholderWorkflowApiTest`: `assess` genérico rechazado; el endpoint lleva a `assessed` y aplica `profile` antes; `retire_*` y `reinstate` sin motivo; guarda de `profile` | `StakeholderWorkflowGuardsApiTest` (11) y `StakeholderAssessmentApiTest` (12) |
  | `EngagementWorkflowApiTest`: `register_held` sin asistente; `close_light` de un tipo formal; `close` con pregunta pendiente; cancelar sin `cancel_reason`; `FIELD_AGENT` sobre una ajena; participante en una ajena con conteos; participante ajeno | `EngagementWorkflowGuardsApiTest` (7) y `EditAnyApiTest` (9) |
  | `FieldVisitWorkflowApiTest`: `FIELD_AGENT` sobre una ajena; `cancel` sin motivo | `EditAnyApiTest` (9) y `FieldVisitWorkflowGuardsApiTest` (7) |
  | `AreaOfInfluenceWorkflowApiTest`: `approve` genérico rechazado; el endpoint aprueba y reemplaza; miembros congelados | `AreaOfInfluenceWorkflowGuardsApiTest` (16) |
  | `FieldPermissionsApiTest`: exportaciones sin campos restringidos; postura escrita por el endpoint | `StakeholderRegisterApiTest` (23) y `StakeholderAssessmentApiTest` (12) |

- **El PUT de wasichai 0.3.2** vacía todo campo escribible que el cuerpo no trae, derivados incluidos, y conserva el
  bloqueado por un permiso de campo. Este plan elige las dos cosas: (a) `DerivedFieldsGuard` rechaza solo un derivado
  enviado con un valor distinto del guardado, así que el portal puede devolver los valores que leyó; y (b) nunca toma del
  cliente un derivado: `DerivedFieldsKeeper` devuelve el valor guardado a cada derivado que el PUT dejó vacío, los
  listeners recalculan el área en cada escritura y los conteos en cada cambio de la lista de asistentes, y JOB-02 corrige
  lo que quede. Con eso el portal puede omitir los derivados (como hace hoy 2.3) o devolverlos (como pide 2.1).

- **Campos restringidos y `u`** (`restrictedFields` de 2.1, `docs/security.md` §7.9). Todo el código de este plan lee y
  escribe como quien llama, así que un campo que no puede leer no le llega y nunca se le pide:
  - `Records.patch` (lo que usan endpoints y listeners para cambiar un registro) manda solo los campos que la definición
    de quien llama marca editables: nunca un campo bloqueado, que wasichai respondería con 400; las guardas leen el
    registro de después con `WriteView.after`, que conserva lo que quien llama no puede escribir. Ninguna guarda exige un
    campo restringido ni uno de solo lectura de `u`: las condiciones miran lo guardado (`working_crs`, las partes, los
    asistentes), no lo que trae el cuerpo. Pruebas: `EditAnyApiTest` «FIELD_AGENT vincula su captura a un grupo de
    interés…» (`u` sobre `stakeholder`), `EngagementRegistrationApiTest` (`u` sobre `topic`) y
    `AreaOfInfluenceWorkflowGuardsApiTest` «PROJECT_MANAGER, con u y APPROVE, aprueba…» (`u` sobre `area_of_influence`).
  - Las evaluaciones las registran `SOCIAL_MANAGER` y `STAKEHOLDER_MANAGER`, los roles de `assess`, que son lectores de
    `stance`, `stance_reasons`, `concerns` y `expectations`; aun así, quien no puede escribir `stakeholder.stance` (o
    `stakeholder_assessment.stance`, si la manda) recibe 403 antes de que se guarde nada (`StakeholderAssessmentApiTest`
    «COMMUNITY_RELATIONS_OFFICER no evalúa…»).
  - Nada sale por una respuesta propia sin pasar por los permisos de quien pide: las capas, los tableros y la exportación
    leen con `RecordService` como quien llama. El nombre de una persona por la relación `stakeholder → person` sale como
    el cargo vigente o «Persona» en los tableros y «no disponible» en la exportación; `stance` es «no disponible»
    (`FIELD_NOT_READABLE`) en KPI-STK-02, KPI-STK-05 y KPI-STK-06 y no aparece en las filas de KPI-STK-03; las capas de F2
    no muestran `land_right`. Pruebas: `DashboardsApiTest` «quien no lee la postura recibe no disponible…» y «el nombre de
    una persona no llega por la matriz a quien no lo lee», `StakeholderRegisterApiTest` «quien no lee la postura ni los
    nombres los ve no disponibles…».

Si al ejecutar este plan un nombre de 2.1 difiere de lo de arriba, manda `docs/database-model.md` y `docs/workflows.md`:
se corrige el que se apartó de ellos.

### Lo que 2.2 hereda de las pruebas de 2.1

- `WorkflowMatrixApiTest` y `PermissionMatrixApiTest` (2.1) recorren todos los workflows y todos los roles por la API
  genérica y deben seguir en verde con cada guarda que agrega este plan. Sus registros y cuentas ya cumplen lo que las
  guardas piden, así que una matriz en rojo apunta a la guarda.
- `WorkflowMatrixApiTest` excluye `DEFERRED_TO_2_2`: `stakeholder.assess`, `stakeholder.retire_assessed`,
  `area_of_influence.approve` y `area_of_influence.supersede`, porque solo se alcanzan por el endpoint de comando. Esos
  casos son de este plan: `StakeholderAssessmentApiTest`, `StakeholderWorkflowGuardsApiTest` y
  `AreaOfInfluenceWorkflowGuardsApiTest`. La matriz también evita los atajos `engagement.register_held_unplanned` y
  `engagement.close_light` (`WALK_AVOIDS`) al llevar un registro a un estado, y los prueba `EngagementWorkflowGuardsApiTest`.
- Este plan debe escribir el caso de `retire_assessed` desde `assessed` (Tarea 11, `StakeholderWorkflowGuardsApiTest`):
  ninguna clase de 2.1 lo cubre, porque el estado `assessed` solo se alcanza por `assess`.
- Los tipos formales de interacción (RN-ENG-004) están copiados en `WorkflowMatrixApiTest` (`engagementTypeFor` y la prueba
  de `FORMAL_TYPES`). Cuando aterrice `EngagementWriteGuard` (Tarea 7), esa copia debe mantenerse igual a la de la guarda, o
  la prueba debe leerla de la fuente de la guarda.
- Al agregar `CommandTransitionGuard` (Tarea 3), volver a revisar `available()` y el 409 de `supersede` desde `draft` en
  `AreaOfInfluenceWorkflowApiTest`: el listado debe seguir ofreciendo `approve` a sus roles y `supersede` desde `draft` debe
  seguir respondiendo 409, no el rechazo del comando.
- Los nombres de persona que se alcanzan por una relación (`stakeholder → person`) para `ANALYST` y `READ_ONLY` no los
  prueba 2.1: son casos de este plan, en `DashboardsApiTest` y `StakeholderRegisterApiTest`.

### Lo que este plan entrega a la Fase 2.3 (portal)

Los contratos de `docs/api.md` §4.1 a §4.4, §4.11 y §4.13, con estas precisiones que la UI necesita:

- **Errores de las guardas** (`problem+json`): 400 con `errors[].field` (`reason`, el campo derivado, `represented_people_count`,
  `role_name`, `position_status`…); 409 con `errors[].field` para una transición que choca con lo guardado
  (`working_crs`, `project_party`, `scheduled_at`, `held_at`, `attended`, `minutes`, `engagement_question`,
  `engagement_type`, `cancel_reason`, `stakeholder_type`, `area_of_influence`); 403 sin asignación, sin `EDIT_ANY` sobre lo
  ajeno o para una transición de comando por la API genérica (`assess` de WF-B, `approve` y `supersede` de WF-O).
- **`POST /api/sgspe/transition-checks`** → `{ allowed, blocking[], warnings[] }`, cada ítem `{ code, message, field }`
  (`{ code, message }` como lo fija 2.3, más `field`, que puede ser `null`). Códigos: `STATE_MISMATCH`, `COMMAND_ONLY`,
  `REASON_REQUIRED` (advertencia: pida el motivo), `NOT_ASSIGNED`, `NOT_OWN_RECORD`, `ACTION_REQUIRED`,
  `WORKING_CRS_REQUIRED`, `PARTY_REQUIRED`, `FIELD_REQUIRED`, `ATTENDANCE_REQUIRED`, `PENDING_QUESTIONS`, `MINUTES_REQUIRED`,
  `PROFILE_INCOMPLETE`.
- **`If-Match`**: `"<updatedAt tal como lo devolvió la API>"`. Obligatorio en `PATCH /api/sgspe/project-components/{id}` y
  `POST /api/sgspe/areas-of-influence/{id}/approve` (428 sin él, 412 si cambió, con el valor actual en
  `errors[0].message`). En la API genérica (`PUT`, `DELETE`, transiciones y vínculos de
  `/api/objects/{objeto}/records/{id}…`) se compara si viene. Un cambio posterior que solo toca derivados o vínculos
  (los conteos que escribe `AttendanceCountsListener`, el área de `AreaListener`) no es un conflicto: el portal puede
  registrar una interacción en escrituras sucesivas y mandar en la transición el `updatedAt` que le devolvió el alta.
- **El PUT genérico** puede reenviar los derivados tal como los leyó (2.1) u omitirlos (2.3, `derivados.ts`): las dos
  formas los conservan. Mandar un valor distinto del guardado es 400 sobre el campo.
- **Importaciones**: multipart con `file`, `target` (`project_component`, `land_parcel` o `mining_concession`),
  `project`, `sourceCrs` (valor del catálogo, p. ej. `EPSG 32717`) y `columns`, un JSON `{clave: encabezado}` con las
  claves que fija 2.3 (`code`, `easting`, `northing`, `elevation`, `azimuth`, `dip`, `depth`, `drillType`, `latitude`,
  `longitude`); también se aceptan los campos del destino en snake_case (`component_type`, `component_status`,
  `tenure_type`, `registered_area_ha`, `name`) y una constante que empieza con `=`. Sin tipo de componente, la hoja es de
  sondajes (`SONDAJE`). La vista previa devuelve `{ importBatchId, sha256, target, sourceCrs, rows, valid, invalid[],
  differences[], outOfZone[] }`, sin geometrías; `differences[]` trae una entrada por fila válida con `outcome` `CREATE`,
  `UNCHANGED`, `NEW_VERSION` o `UPDATE`. Con `Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
  devuelve el reporte de diferencias como XLSX. **La confirmación manda otra vez `target`, `project`, `sourceCrs` y
  `columns`** junto con `file`, `sha256` y `decisions`: la importación no deja estado en el servidor (`docs/api.md` §4.2) y
  el servidor repite la validación con ellos. `decisions` es `[{ "row": 7, "action": "DISCARD" }]` con `action` `APPLY` o
  `DISCARD`; una fila sin decisión aplica su acción propuesta, salvo una fila de `outOfZone[]`, que solo entra con un
  `APPLY` explícito (`docs/gis.md` §4.3, «exige confirmación explícita»). F2 lee XLSX, CSV y GeoJSON; los predios y las
  concesiones, solo GeoJSON.
- **Evaluaciones**: advertencias `STANCE_DIVERGENCE`, `BACKDATED_ASSESSMENT` y `NO_PRIORITY_RULE` (`{ code, message }`).
  El ajuste manual del grupo es `group_overridden: true` con `influence_group` y `override_reason` (sin motivo, 400);
  `group_overridden: false` descarta `influence_group`. Los campos calculados (`interest_index`, `power_index`,
  `influence_score`, los componentes) no se mandan: 400 sobre el campo. La vista previa devuelve `computed` con los
  nombres de campo del modelo en snake_case (`interest_index`, `power_index`, `influence_score`, `influence_group`,
  `information_priority`, los componentes de interés y poder, `group_overridden`, `baseline_assessment`, `stance_changed`),
  como lo fija 2.3. `If-Match` es opcional aquí y se compara si viene.
- **Tableros**: cada indicador es `{ code, label, available, value, numerator, denominator, unit, breakdowns[], series[],
  unavailableReason, missingFields[], warnings[] }` (las claves nulas se omiten). `unavailableReason`:
  `FIELD_NOT_READABLE`, `OBJECT_NOT_READABLE` o `SINGLE_PROJECT_REQUIRED`. Cada desglose es `{ by, rows[] }` y cada fila
  `{ key, value, … }`, con los `by` que fija 2.3 en snake_case: KPI-STK-01 `influence_group` (`SIN EVALUAR` incluido),
  `influence_group_by_scope` (`influenceGroup`, `scope`) y `stakeholder_type`; KPI-STK-02 `stance` (orden del catálogo más
  `SIN EVALUAR`), `stance_by_influence_group`, `stance_by_type`; KPI-STK-03 `numerator` (evaluados), `denominator`
  (vigentes), `stakeholder` (`key` = id, `label`, `value` = `influence_score` de la última evaluación, `interest`,
  `power`, `influenceGroup`, `overridden`, `overrideReason`, y `stance` solo si se puede leer) y `thresholds`
  (`interest`, `power`); KPI-STK-04 `sex`, `scope`, `institution`; KPI-STK-06 `direction` (`MORE_FAVORABLE`,
  `LESS_FAVORABLE`) y `coverage` (`evaluated`, `opposition`); KPI-STK-08 `series` `{ period: "AAAA-MM", value, evaluated,
  distribution }`; KPI-ENG-01 `engagement_type` y `series` `{ period, value }`; KPI-ENG-02 `engagement_type` y `series`
  `{ period, value, target }`; KPI-ENG-03 `engagement` (`label`, `heldDate`, `value` = asistentes, `femalePercent`,
  `malePercent`, `directArea`, `indirectArea`, `regional`, `national` (conteos) y `organizations[]`); KPI-ENG-04 y
  KPI-ENG-05 `topic` (`label`, `meetings`, `questions`). En el catálogo (`GET /api/sgspe/dashboards/indicators`),
  `dashboard` es `stakeholders`, `engagement` o `null` para KPI-STK-04, KPI-STK-05 y KPI-STK-07, que solo tienen detalle en
  F2.
- **Exportación** `POST /api/sgspe/exports/stakeholder-register`: `filters` es `{ project: string[] }` como lo fija 2.3
  (también admite `scope`, `stakeholderType`, `influenceGroup`, `locality` e `includeRetired`); una lista vacía es «mis
  proyectos» (las asignaciones vigentes de quien exporta; el ADMIN, todos). Con un solo proyecto, `export_log` lleva ese
  proyecto y la exportación exige una asignación a él (alcance opción A). El servidor exige `checklistConfirmed` solo en
  `EXTERNO` (`docs/security.md` §14); que la UI lo pida también en `PUBLICO` es más estricto y compatible.
- **Capas**: cada elemento con la forma de wasichai-gis (`id` `<registro>:<geometría>`, `properties` con los atributos
  legibles, `__id` y `__object`, más `__layer` y `__state`); sin `READ` sobre un objeto de la capa, 403. El mapa base no
  pasa por el backend en F2 (`SGSPE_MAPA_BASE` del portal).

---

## Tareas

### Tarea 1: El catálogo del modelo dentro del jar, POI y las reglas de arquitectura de F2

Las guardas de F2 necesitan saber qué campos son derivados, qué objetos declaran `EDIT_ANY`, qué roles existen
y qué roles mueve cada transición, sin un solo literal de rol en el código (RQ-SEC-005, ASM-070). Esa información vive en
`model/model.json`, `model/roles.json` y `model/workflows/*.json`, que la Fase 2.1 deja listos: aquí se empaquetan en el jar
(`classpath:model/`) y `ModelCatalog` los lee. Se agrega también Apache POI, que usan la importación (Tarea 17) y la
exportación (Tarea 23), y `ArchitectureTest` aprende los paquetes `scope` y `privacy` (`docs/architecture.md` §5.3: solo
dependen de `common`) y la excepción vigilada de `findAllOrganizations` (UPS-17).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/common/ModelCatalog.kt`
- Modificar: `build.gradle.kts`
- Modificar: `src/test/kotlin/sgspe/ArchitectureTest.kt`
- Probar: `src/test/kotlin/sgspe/common/ModelCatalogTest.kt`

**Interfaces:**
- Consume: `model/model.json`, `model/roles.json` y `model/workflows/<objeto>.json` de la Fase 2.1 (formato en «Interfaces con los otros planes de F2»).
- Produce:
  - `class ModelCatalog` (bean de `ModelCatalogConfiguration`): `derivedFields(objectName): Set<String>`,
    `objectsDeclaring(action): Set<String>`, `enumOf(objectName, fieldName): List<String>`, `enumValues(key): List<String>`,
    `declaredRoles: Set<String>`, `transitions(objectName): List<Transition>`, `transition(objectName, name): Transition?`
    (`Transition(name, from, to, roles)`), `relationField(source, target): String?`, `objectOrNull(name): ModelObject?`.
  - `ModelCatalog.parse(model, roles, workflows)` para pruebas y `ModelCatalog.fromClasspath()`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/ModelCatalogTest.kt` (4 pruebas):

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// el catálogo lee del modelo lo que wasichai no guarda ("derived": true), las acciones, los ENUM, los roles declarados y
// los roles de cada transición
class ModelCatalogTest {
    private val catalog =
        ModelCatalog.parse(
            model =
                """
                {"enums": {"stakeholder_scope": ["AIDS", "AIIS"]},
                 "objects": [
                   {"name": "stakeholder", "actions": [{"name": "EXPORT", "label": "Exportar"}],
                    "fields": [{"name": "stance", "type": "ENUM", "enum": "stance", "derived": true},
                               {"name": "stakeholder_scope", "type": "ENUM", "enum": "stakeholder_scope"},
                               {"name": "inclusion_reason", "type": "LONG_TEXT"}]},
                   {"name": "engagement", "actions": [{"name": "EDIT_ANY", "label": "Editar lo ajeno"}], "fields": []}
                 ],
                 "relationships": [{"name": "stakeholder_project", "source": "stakeholder", "target": "project", "fieldName": "project"}]}
                """.trimIndent(),
            roles = """{"mappedToAdmin": ["SUPER_ADMIN"], "roles": [{"name": "STAKEHOLDER_MANAGER"}, {"name": "FIELD_AGENT"}]}""",
            workflows =
                mapOf(
                    "stakeholder" to
                        """{"definition": {"transitions": [{"name": "assess", "from": "profiled", "to": "assessed", "roles": ["STAKEHOLDER_MANAGER"]}]}}"""
                )
        )

    @Test
    fun `los derivados salen de la clave derived`() {
        assertEquals(setOf("stance"), catalog.derivedFields("stakeholder"))
        assertEquals(emptySet<String>(), catalog.derivedFields("engagement"))
        assertEquals(emptySet<String>(), catalog.derivedFields("no_existe"))
    }

    @Test
    fun `objetos que declaran una accion, enums y relaciones`() {
        assertEquals(setOf("engagement"), catalog.objectsDeclaring("EDIT_ANY"))
        assertEquals(listOf("AIDS", "AIIS"), catalog.enumOf("stakeholder", "stakeholder_scope"))
        assertEquals("project", catalog.relationField("stakeholder", "project"))
        assertNull(catalog.relationField("stakeholder", "engagement"))
    }

    @Test
    fun `los roles declarados no incluyen los mapeados a ADMIN, y las transiciones traen sus roles`() {
        assertEquals(setOf("STAKEHOLDER_MANAGER", "FIELD_AGENT"), catalog.declaredRoles)
        assertEquals(listOf("STAKEHOLDER_MANAGER"), catalog.transition("stakeholder", "assess")?.roles)
        assertNull(catalog.transition("stakeholder", "profile"))
        assertTrue(catalog.transitions("engagement").isEmpty())
    }

    @Test
    fun `fromClasspath lee los archivos que empaqueta processResources`() {
        val packaged = ModelCatalog.fromClasspath()
        assertTrue("AUDITOR" in packaged.declaredRoles)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ModelCatalogTest' --tests 'sgspe.ArchitectureTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'ModelCatalog'`).

- [ ] **Paso 3: Modificar `build.gradle.kts`**

Modificar `build.gradle.kts`. Después de las líneas de los starters de wasichai (la Fase 2.1 ya agregó
`wasichai-spring-boot-starter-gis` y `wasichai-spring-boot-starter-workflow`), y antes del comentario
`// WasichaiIntegrationTest; …`, agregar:

```kotlin
    // sgspe.territory lee hojas XLSX y sgspe.privacy las escribe (RQ-REP-022); 5.4 o superior por CVE-2025-31672
    // (platform-fit.md §6)
    implementation("org.apache.poi:poi-ooxml:5.5.1")

```

Y antes del bloque `// las pruebas unitarias corren siempre; las que necesitan base van por integrationTest`, agregar:

```kotlin
// el modelo, los roles y los workflows viajan dentro del jar como configuración (ModelCatalog): las guardas leen de ahí
// los campos derivados, los roles declarados y los roles de cada transición, nunca de literales en el código (ASM-070)
tasks.named<ProcessResources>("processResources") {
    from("model") {
        include("model.json", "roles.json", "workflows/*.json")
        into("model")
    }
}

```

- [ ] **Paso 4: Modificar `ArchitectureTest.kt`**

Modificar `src/test/kotlin/sgspe/ArchitectureTest.kt` en cuatro lugares.

1. Antes de la prueba `` `observability no depende de otros paquetes de sgspe` ``, agregar estas dos:

```kotlin
    // sgspe.scope y sgspe.privacy dependen solo de sgspe.common (architecture.md §5.3): el alcance no conoce el dominio y
    // las exportaciones leen con Records
    @Test
    fun `scope y privacy solo dependen de common`() {
        for (name in listOf("scope", "privacy")) {
            assertEquals(emptyList<String>(), offenders(sources(File(main, "sgspe/$name")), NOT_COMMON_IMPORT), name)
        }
    }

    // la enumeración de organizaciones de los trabajos es una excepción vigilada (UPS-17, architecture.md §8.1): vive solo
    // en sgspe/common/Organizations.kt
    @Test
    fun `solo Organizations enumera las organizaciones`() {
        val outside = sources(main).filterNot { it.invariantSeparatorsPath.endsWith("sgspe/common/Organizations.kt") }
        assertEquals(emptyList<String>(), offenders(outside, ORGANIZATIONS))
    }

```

2. En la prueba vigía de las expresiones, después de
`assertFalse(WASICHAI_IMPORT.containsMatchIn("assertTrue(flags(INTERNAL_API, \"import wasichai.*\"))"))`, agregar:

```kotlin
        assertTrue(NOT_COMMON_IMPORT.containsMatchIn("import sgspe.stakeholder.Assessments"))
        assertFalse(NOT_COMMON_IMPORT.containsMatchIn("import sgspe.common.Records"))
        assertTrue(ORGANIZATIONS.containsMatchIn("objects.findAllOrganizations()"))
        assertFalse(ORGANIZATIONS.containsMatchIn("organizations.having(\"stakeholder\")"))
```

3. Después de `private val OTHER_SGSPE_IMPORT = …`, agregar:

```kotlin
        private val NOT_COMMON_IMPORT = Regex("""^\s*import\s+sgspe\.(?!common\.)""")
        private val ORGANIZATIONS = Regex("""\bfindAllOrganizations\b""")
```

4. Clasificar los paquetes nuevos: reemplazar

```kotlin
        private val NON_DOMAIN_PACKAGES = listOf("common", "observability", "prod", "dashboard", "deliverable", "ai")
```

por

```kotlin
        private val NON_DOMAIN_PACKAGES = listOf("common", "observability", "prod", "scope", "privacy", "dashboard", "deliverable", "ai")
```

- [ ] **Paso 5: Crear `ModelCatalog.kt`**

Crear `src/main/kotlin/sgspe/common/ModelCatalog.kt`:

```kotlin
package sgspe.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

// model/model.json, model/roles.json y model/workflows/*.json leídos como configuración: viajan dentro del jar
// (processResources de build.gradle.kts) y son los mismos archivos que aplican apply.py y apply_roles.py. de aquí salen
// los campos derivados ("derived": true, que wasichai no conoce), los objetos que declaran una acción, los catálogos ENUM,
// los roles declarados (AssignmentRoleGuard) y los roles de cada transición (destinatarios por rol, ASM-070). así ningún
// nombre de rol vive en el código (RQ-SEC-005)
class ModelCatalog(
    model: JsonNode,
    roles: JsonNode,
    workflows: Map<String, JsonNode>
) {
    data class Field(
        val name: String,
        val type: String,
        val required: Boolean,
        val derived: Boolean,
        val enum: String?
    )

    data class ModelObject(
        val name: String,
        val fields: Map<String, Field>,
        val actions: Set<String>,
        val apiOnly: Boolean,
        val appendOnly: Boolean,
        val requiresReason: Boolean
    )

    data class Relationship(
        val name: String,
        val type: String,
        val source: String,
        val target: String,
        val fieldName: String?
    )

    data class Transition(
        val name: String,
        val from: String,
        val to: String,
        val roles: List<String>
    )

    val objects: Map<String, ModelObject> =
        model.path("objects").values().associate { node ->
            val name = node.path("name").asString()
            name to
                ModelObject(
                    name = name,
                    fields =
                        node.path("fields").values().associate { field ->
                            field.path("name").asString() to
                                Field(
                                    name = field.path("name").asString(),
                                    type = field.path("type").asString(),
                                    required = field.path("required").asBoolean(false),
                                    derived = field.path("derived").asBoolean(false),
                                    enum = field.path("enum").takeIf { it.isString }?.asString()
                                )
                        },
                    actions =
                        node
                            .path("actions")
                            .values()
                            .map { it.path("name").asString() }
                            .toSet(),
                    apiOnly = node.path("apiOnly").asBoolean(false),
                    appendOnly = node.path("appendOnly").asBoolean(false),
                    requiresReason = node.path("requiresReason").asBoolean(false)
                )
        }

    val relationships: List<Relationship> =
        model.path("relationships").values().map { node ->
            Relationship(
                name = node.path("name").asString(),
                type = node.path("type").takeIf { it.isString }?.asString() ?: "MANY_TO_ONE",
                source = node.path("source").asString(),
                target = node.path("target").asString(),
                fieldName = node.path("fieldName").takeIf { it.isString }?.asString()
            )
        }

    val enums: Map<String, List<String>> =
        model.path("enums").properties().associate { (key, values) -> key to values.values().map { it.asString() } }

    // los 15 roles declarados de roles.json; los de mappedToAdmin no son roles de wasichai (son ADMIN)
    val declaredRoles: Set<String> =
        roles
            .path("roles")
            .values()
            .map { it.path("name").asString() }
            .toSet()

    private val transitionsByObject: Map<String, List<Transition>> =
        workflows.mapValues { (_, node) ->
            node.path("definition").path("transitions").values().map { transition ->
                Transition(
                    name = transition.path("name").asString(),
                    from = transition.path("from").asString(),
                    to = transition.path("to").asString(),
                    roles = transition.path("roles").values().map { it.asString() }
                )
            }
        }

    fun objectOrNull(name: String): ModelObject? = objects[name]

    fun derivedFields(objectName: String): Set<String> =
        objects[objectName]
            ?.fields
            ?.values
            ?.filter { it.derived }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()

    fun objectsDeclaring(action: String): Set<String> =
        objects.values
            .filter { action in it.actions }
            .map { it.name }
            .toSet()

    fun enumOf(
        objectName: String,
        fieldName: String
    ): List<String> =
        objects[objectName]
            ?.fields
            ?.get(fieldName)
            ?.enum
            ?.let { enums[it] }
            .orEmpty()

    fun enumValues(key: String): List<String> = enums[key].orEmpty()

    fun transitions(objectName: String): List<Transition> = transitionsByObject[objectName].orEmpty()

    fun transition(
        objectName: String,
        name: String
    ): Transition? = transitions(objectName).firstOrNull { it.name == name }

    // el campo RELATION de `source` que apunta a `target` (MANY_TO_ONE), o null
    fun relationField(
        source: String,
        target: String
    ): String? = relationships.firstOrNull { it.type == "MANY_TO_ONE" && it.source == source && it.target == target }?.fieldName

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()

        fun parse(
            model: String,
            roles: String,
            workflows: Map<String, String> = emptyMap()
        ): ModelCatalog = ModelCatalog(JSON.readTree(model), JSON.readTree(roles), workflows.mapValues { JSON.readTree(it.value) })

        // los archivos que empaqueta processResources bajo classpath:model/. un workflow se llama como su objeto
        fun fromClasspath(): ModelCatalog {
            val resolver = PathMatchingResourcePatternResolver()
            val model = resolver.getResource("classpath:model/model.json")
            val roles = resolver.getResource("classpath:model/roles.json")
            check(model.exists() && roles.exists()) { "faltan model/model.json o model/roles.json en el classpath: revise processResources" }
            val workflows =
                resolver.getResources("classpath*:model/workflows/*.json").associate { resource ->
                    resource.filename!!.removeSuffix(".json") to resource.inputStream.use { JSON.readTree(it) }
                }
            return ModelCatalog(
                model.inputStream.use { JSON.readTree(it) },
                roles.inputStream.use { JSON.readTree(it) },
                workflows
            )
        }
    }
}

@Configuration(proxyBeanMethods = false)
class ModelCatalogConfiguration {
    @Bean
    fun modelCatalog(): ModelCatalog = ModelCatalog.fromClasspath()
}
```

- [ ] **Paso 6: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ModelCatalogTest' --tests 'sgspe.ArchitectureTest'
```

Expected: `BUILD SUCCESSFUL`; 15 pruebas en verde.

- [ ] **Paso 7: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/common/ModelCatalogTest.kt build.gradle.kts src/test/kotlin/sgspe/ArchitectureTest.kt src/main/kotlin/sgspe/common/ModelCatalog.kt
git commit -m "$(cat <<'MSG'
feat(common): el catálogo del modelo viaja en el jar y POI entra al build

ModelCatalog lee model.json, roles.json y los workflows empaquetados en classpath:model/: de ahí
salen los derivados, las acciones declaradas y los roles de cada transición, sin literales.
ArchitectureTest clasifica scope y privacy y vigila findAllOrganizations.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 2: Records completo y las piezas comunes de las guardas y los endpoints

`Records` es la única puerta de sgspe a `RecordService` (`ArchitectureTest`); F2 la completa con lectura por
cursor, conteo, geometrías, vínculos, transiciones con la marca `SgspeWrite` y un `patch` que solo cambia lo que nombra
(el `update` de wasichai reemplaza el registro entero). Se agregan las piezas pequeñas que usan todas las tareas
siguientes: la marca legible fuera de `common` (`WriteMark`), los errores 412/428 y de reglas (`Errors`), la igualdad de
valores entre JSON y base (`Values`), quién llama (`Callers`), los permisos previos de un endpoint (`Permissions`), la
transacción con candado de clúster (`Transactions`), el cuerpo estricto de los comandos (`StrictBody`, `docs/api.md` §3.3)
y los criterios de lista (`Criteria`).

Los servicios de wasichai llegan a `Records` por `ObjectProvider`: las guardas y listeners de las tareas siguientes usan
`Records`, y wasichai inyecta esas guardas en `RecordService`; con inyección directa Spring no arranca (ciclo de beans).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/common/WriteMark.kt`
- Crear: `src/main/kotlin/sgspe/common/Errors.kt`
- Crear: `src/main/kotlin/sgspe/common/Values.kt`
- Crear: `src/main/kotlin/sgspe/common/Callers.kt`
- Crear: `src/main/kotlin/sgspe/common/Permissions.kt`
- Crear: `src/main/kotlin/sgspe/common/Transactions.kt`
- Crear: `src/main/kotlin/sgspe/common/StrictBody.kt`
- Crear: `src/main/kotlin/sgspe/common/Criteria.kt`
- Modificar: `src/main/kotlin/sgspe/common/Records.kt`
- Probar: `src/test/kotlin/sgspe/common/ValuesTest.kt`
- Probar: `src/test/kotlin/sgspe/common/RecordsPatchApiTest.kt`

**Interfaces:**
- Consume: `SgspeWrite` y `LIMA` de F1; `RecordService`, `RelatedRecordService`, `WorkflowService`, `MetadataService`, `CurrentUser`, `ClusterLock` y `TransactionalOperator` de wasichai.
- Produce:
  - `Records`: `get`, `find` (null si no existe), `page`, `all(objectName, filters, criteria, ids)` (por cursor),
    `rows`, `count`, `create(objectName, attributes, reason[, geometries])`, `update`, `patch(objectName, id, changes, reason,
    geometries = emptyMap())`, `delete`, `link`, `related`, `relatedAsPlatform`, `transition(objectName, id, transition,
    reason)`, `asPlatform(organizationId) { … }`; `Records.GEOMETRIES`, `Records.geometriesOf(response)`, `Records.uuid(value)`.
  - `WriteMark.present()`; `PreconditionFailedException(message, current)`, `PreconditionRequiredException(message)`,
    `RuleException(status, message, violations)`; `Values.same/text/decimal/bool`; `Callers.userOrNull()/require()`;
    `Permissions.require(user, what, vararg action to objectName)` y `has(user, action, objectName)`;
    `Transactions.run { }` y `locked(key) { }`; `StrictBody.requireOnly(body, allowed, prefix)` y `objectOf(body, name)`;
    `Criteria.updatedSince/inState/anyOf/textIn/between/dateBetween`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/ValuesTest.kt` (2 pruebas):

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// lo guardado y lo que manda un cliente se comparan sin falsos cambios: números como números, textos como textos
class ValuesTest {
    @Test
    fun `numeros de distinto tipo y escala son iguales`() {
        assertTrue(Values.same(3L, 3.0))
        assertTrue(Values.same(BigDecimal("3.00"), 3))
        assertTrue(Values.same(BigDecimal("2.5"), "2.50"))
        assertFalse(Values.same(BigDecimal("2.5"), 2.6))
    }

    @Test
    fun `los textos se comparan como texto, y null y vacio son lo mismo`() {
        assertFalse(Values.same("0012", "12"))
        assertTrue(Values.same(null, ""))
        assertTrue(Values.same("  ", null))
        assertFalse(Values.same("A FAVOR", null))
        assertTrue(Values.same("2026-05-12", "2026-05-12"))
    }
}
```

Crear `src/test/kotlin/sgspe/common/RecordsPatchApiTest.kt` (1 pruebas):

```kotlin
package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.SgspeApiTest
import java.util.UUID

// Records.patch cambia solo lo que nombra: el PUT de wasichai reemplaza el registro, así que manda lo guardado más los
// cambios. find devuelve null para lo que no existe. como un trabajo de fondo, dentro de asPlatform
class RecordsPatchApiTest : SgspeApiTest() {
    @Autowired
    private lateinit var records: Records

    @Test
    fun `patch no borra los campos que no nombra y deja el motivo`() {
        val name = uniqueName("parchado")
        createObject(name, listOf(field("code", "TEXT"), field("note", "TEXT"), field("visits", "INTEGER")))
        val organization = UUID.fromString(me()["organizationId"].asString())
        val id = createRecord(name, mapOf("code" to "P-1", "note" to "se queda", "visits" to 3))
        runBlocking {
            records.asPlatform(organization) {
                records.patch(name, UUID.fromString(id), mapOf("visits" to 4), "derivado: visitas")
                assertNull(records.find(name, UUID.randomUUID()))
            }
        }
        val attributes = tree(send("GET", "/api/objects/$name/records/$id", null, org.springframework.http.HttpStatus.OK))["attributes"]
        assertEquals("se queda", attributes["note"].asString())
        assertEquals(4, attributes["visits"].asInt())
        val history = tree(send("GET", "/api/objects/$name/records/$id/history", null, org.springframework.http.HttpStatus.OK))
        assertEquals("derivado: visitas", history.values().first()["reason"].asString())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ValuesTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'Values'`).

- [ ] **Paso 3: Reemplazar `Records.kt`**

Reemplazar `src/main/kotlin/sgspe/common/Records.kt` completo por:

```kotlin
package sgspe.common

import kotlinx.coroutines.withContext
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component
import wasichai.core.common.NotFoundException
import wasichai.core.common.PageRequest
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordRow
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.data.toResponse
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.workflow.WorkflowService
import java.util.UUID

// el RecordService de wasichai para el código de sgspe (como Registros de caja-backend@dc9e391,
// src/main/kotlin/caja/comun/Registros.kt). RecordService comprueba los permisos de objeto y de campo de quien llama y
// valida cada escritura, así que aquí no se repite. cada escritura, vínculo y transición lleva la marca SgspeWrite, para
// que las guardas distingan el código propio de la API genérica, y su motivo, que wasichai deja en audit_log.reason
// (ADR-0011). es el único lugar fuera de las pruebas que toca RecordService (ArchitectureTest)
//
// RecordService, RelatedRecordService y WorkflowService llegan por ObjectProvider: las guardas y los listeners de sgspe
// usan Records, y wasichai los inyecta en esos mismos servicios (RecordWriteGuards, RecordChangeListener), así que una
// inyección directa sería un ciclo de beans
@Component
class Records(
    recordService: ObjectProvider<RecordService>,
    relatedService: ObjectProvider<RelatedRecordService>,
    workflowService: ObjectProvider<WorkflowService>,
    private val metadata: MetadataService,
    private val callers: Callers
) {
    private val records by lazy { recordService.getObject() }
    private val related by lazy { relatedService.getObject() }
    private val workflows by lazy { workflowService.getObject() }

    suspend fun get(
        objectName: String,
        id: UUID
    ): RecordResponse = records.get(objectName, id)

    // null si no existe o el que llama no lo ve (404 de wasichai)
    suspend fun find(
        objectName: String,
        id: UUID
    ): RecordResponse? =
        try {
            records.get(objectName, id)
        } catch (_: NotFoundException) {
            null
        }

    suspend fun page(
        objectName: String,
        query: RecordQuery
    ): PageResponse<RecordResponse> = records.list(objectName, query)

    // todos los que cumplen los filtros, por cursor y ordenados por id: cada fila sale una sola vez aunque otras columnas
    // empaten (la lección de Registros.all de caja), y sin COUNT(*) en cada página (WAS-ADR-0036)
    suspend fun all(
        objectName: String,
        filters: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null
    ): List<RecordResponse> {
        if (ids != null && ids.isEmpty()) return emptyList()
        val rows = mutableListOf<RecordResponse>()
        var cursor: String? = null
        do {
            val page =
                records.list(
                    objectName,
                    RecordQuery(
                        page = PageRequest.of(0, PageRequest.MAX_SIZE),
                        sort = "id",
                        filters = filters,
                        criteria = criteria,
                        ids = ids,
                        count = false,
                        after = cursor
                    )
                )
            rows += page.content
            cursor = page.nextCursor
        } while (cursor != null)
        return rows
    }

    // las filas con sus secciones (las geometrías) y la definición que las leyó, para quien arma GeoJSON
    suspend fun rows(
        objectName: String,
        query: RecordQuery
    ): Pair<ObjectDefinition, List<RecordRow>> = records.rows(objectName, query)

    suspend fun count(
        objectName: String,
        filters: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null
    ): Long {
        if (ids != null && ids.isEmpty()) return 0
        return records
            .list(objectName, RecordQuery(page = PageRequest.of(0, 1), filters = filters, criteria = criteria, ids = ids))
            .totalElements ?: 0
    }

    suspend fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = create(objectName, attributes, reason, emptyMap())

    // geometries: GeoJSON EPSG:4326 por campo, la sección "geometries" de wasichai-gis
    suspend fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String?,
        geometries: Map<String, Any?>
    ): RecordResponse = withContext(SgspeWrite) { records.create(objectName, request(attributes, geometries), reason) }

    // el update de wasichai tal cual: reemplaza cada campo escribible, y uno que falta queda vacío. el código de sgspe usa
    // patch, que no borra lo que no nombra
    suspend fun update(
        objectName: String,
        id: UUID,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { records.update(objectName, id, RecordRequest(attributes), reason) }

    // cambia solo `changes` (y las geometrías nombradas): lee el registro y manda lo guardado más los cambios. una persona
    // manda solo los campos que puede escribir, según el objeto como ella lo ve (MetadataService.definitionOf), porque
    // wasichai rechaza la escritura entera que nombra uno bloqueado y deja como estaban los que no se mandan; la
    // plataforma (un trabajo dentro de asPlatform) los escribe todos
    suspend fun patch(
        objectName: String,
        id: UUID,
        changes: Map<String, Any?>,
        reason: String?,
        geometries: Map<String, Any?> = emptyMap()
    ): RecordResponse {
        val stored = records.get(objectName, id).attributes
        val merged = stored + changes
        val body =
            if (callers.userOrNull() == null) {
                merged
            } else {
                val writable =
                    metadata
                        .definitionOf(objectName)
                        .fields
                        .filter { it.editable }
                        .map { it.name }
                        .toSet()
                merged.filterKeys { it in writable }
            }
        return withContext(SgspeWrite) { records.update(objectName, id, request(body, geometries), reason) }
    }

    suspend fun delete(
        objectName: String,
        id: UUID,
        reason: String?
    ) = withContext(SgspeWrite) { records.delete(objectName, id, reason) }

    suspend fun link(
        objectName: String,
        id: UUID,
        relationship: String,
        otherId: UUID,
        reason: String?
    ) = withContext(SgspeWrite) { related.link(objectName, id, relationship, otherId, reason) }

    // los registros del otro lado de una relación, como los ve quien llama
    suspend fun related(
        objectName: String,
        id: UUID,
        relationship: String
    ): List<RecordResponse> {
        val rows = mutableListOf<RecordResponse>()
        var page = 0
        do {
            val (_, result) =
                related.relatedRecords(objectName, id, relationship, RecordQuery(page = PageRequest.of(page, PageRequest.MAX_SIZE), sort = "id"))
            rows += result.content.map { it.toResponse() }
            page++
        } while (result.content.size >= PageRequest.MAX_SIZE)
        return rows
    }

    // lo mismo para un trabajo de fondo, sin usuario: la plataforma no tiene permisos que aplicar (WAS-ADR-0039)
    suspend fun relatedAsPlatform(
        organizationId: UUID,
        objectName: String,
        id: UUID,
        relationship: String
    ): List<RecordRow> {
        val rows = mutableListOf<RecordRow>()
        var page = 0
        do {
            val (_, result) =
                related.relatedRows(organizationId, objectName, id, relationship, RecordQuery(page = PageRequest.of(page, PageRequest.MAX_SIZE), sort = "id"))
            rows += result.content
            page++
        } while (result.content.size >= PageRequest.MAX_SIZE)
        return rows
    }

    // una transición como la persona que llama (WorkflowService exige un usuario), con la marca: así
    // CommandTransitionGuard deja pasar las transiciones de comando que aplica un endpoint de sgspe
    suspend fun transition(
        objectName: String,
        id: UUID,
        transition: String,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { workflows.apply(objectName, id, transition, reason) }

    // el trabajo de fondo de una organización (WAS-ADR-0039): sin usuario, sin permisos, nunca dentro de una petición
    suspend fun <T> asPlatform(
        organizationId: UUID,
        block: suspend () -> T
    ): T = records.asPlatform(organizationId, block)

    private fun request(
        attributes: Map<String, Any?>,
        geometries: Map<String, Any?>
    ): RecordRequest = RecordRequest(attributes).also { if (geometries.isNotEmpty()) it.section(GEOMETRIES, geometries) }

    companion object {
        // la sección en que wasichai-gis lleva las geometrías de un registro (wasichai.gis.GEOMETRIES)
        const val GEOMETRIES = "geometries"

        // las geometrías de una respuesta, por campo
        @Suppress("UNCHECKED_CAST")
        fun geometriesOf(response: RecordResponse): Map<String, Map<String, Any?>?> =
            (response.sections[GEOMETRIES] as Map<String, Map<String, Any?>?>?).orEmpty()

        fun uuid(value: Any?): UUID? = Values.text(value)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
```

- [ ] **Paso 4: Crear `WriteMark.kt`**

Crear `src/main/kotlin/sgspe/common/WriteMark.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.currentCoroutineContext

// si la escritura en curso la hace el código de sgspe (lleva la marca SgspeWrite). fuera de sgspe.common nadie nombra la
// marca (ArchitectureTest): las guardas y los listeners de los paquetes de dominio preguntan aquí
object WriteMark {
    suspend fun present(): Boolean = currentCoroutineContext()[SgspeWrite.Key] != null
}
```

- [ ] **Paso 5: Crear `Errors.kt`**

Crear `src/main/kotlin/sgspe/common/Errors.kt`:

```kotlin
package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException

// los estados propios de /api/sgspe/** que la plataforma no trae (api.md §3.3). el manejador de wasichai los vuelve
// problem+json como a los suyos

// 412: If-Match no coincide con el updatedAt actual (api.md §3.6); errors[] lleva el valor actual
class PreconditionFailedException(
    message: String,
    current: String?
) : WasichaiException(HttpStatus.PRECONDITION_FAILED, message, listOf(FieldViolation("updatedAt", "valor actual ${current ?: "desconocido"}")))

// 428: un comando que exige If-Match llegó sin él
class PreconditionRequiredException(
    message: String
) : WasichaiException(HttpStatus.PRECONDITION_REQUIRED, message, listOf(FieldViolation("If-Match", "envíe el updatedAt que leyó, entre comillas")))

// cualquier otro estado con su detalle y sus campos: lo usan las guardas de reglas (Finding.toException)
class RuleException(
    status: HttpStatus,
    message: String,
    violations: List<FieldViolation> = emptyList()
) : WasichaiException(status, message, violations)
```

- [ ] **Paso 6: Crear `Values.kt`**

Crear `src/main/kotlin/sgspe/common/Values.kt`:

```kotlin
package sgspe.common

import java.math.BigDecimal

// igualdad de dos valores de atributo, vengan del almacén (Long, BigDecimal, cadenas ISO, uuid en cadena) o de un cuerpo
// json (Int, Double, cadenas). si uno de los dos es un número se comparan como números (3, 3.0 y 3.00 son iguales); si
// no, como texto, así un código "0012" nunca es igual a "12". null y "" son el mismo vacío
object Values {
    fun same(
        a: Any?,
        b: Any?
    ): Boolean {
        val x = blankToNull(a)
        val y = blankToNull(b)
        if (x == null || y == null) return x == null && y == null
        if (x is Number || y is Number) {
            val dx = decimal(x) ?: return false
            val dy = decimal(y) ?: return false
            return dx.compareTo(dy) == 0
        }
        return x.toString() == y.toString()
    }

    private fun blankToNull(value: Any?): Any? = if (value is String && value.isBlank()) null else value

    fun text(value: Any?): String? = value?.toString()?.trim()?.ifEmpty { null }

    fun decimal(value: Any?): BigDecimal? =
        when (value) {
            null -> null
            is BigDecimal -> value
            is Number -> BigDecimal(value.toString())
            is String -> value.trim().toBigDecimalOrNull()
            else -> null
        }

    fun bool(value: Any?): Boolean? =
        when (value) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull()
            else -> null
        }
}
```

- [ ] **Paso 7: Crear `Callers.kt`**

Crear `src/main/kotlin/sgspe/common/Callers.kt`:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.UnauthorizedException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser

// quién llama: la persona o la cuenta de servicio del token, o null fuera de una petición (un trabajo dentro de
// RecordService.asPlatform, una automatización)
@Component
class Callers(
    private val currentUser: CurrentUser
) {
    suspend fun userOrNull(): AuthenticatedUser? =
        try {
            currentUser.require()
        } catch (_: UnauthorizedException) {
            null
        }

    suspend fun require(): AuthenticatedUser = currentUser.require()
}
```

- [ ] **Paso 8: Crear `Permissions.kt`**

Crear `src/main/kotlin/sgspe/common/Permissions.kt`:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.Actions
import wasichai.core.common.ForbiddenException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.MetadataService

// los permisos de objeto y las acciones declaradas de wasichai comprobados antes de la primera escritura, diciendo lo
// que falta (api.md §3.9, como Permisos de caja-backend@dc9e391). después RecordService los vuelve a comprobar en cada
// escritura. nunca compara nombres de rol (RQ-SEC-005)
@Component
class Permissions(
    private val currentUser: CurrentUser,
    private val metadata: MetadataService
) {
    // 403 si falta alguna (acción a objeto): «<what> exige <lo que falta>»
    suspend fun require(
        user: AuthenticatedUser,
        what: String,
        vararg grants: Pair<String, String>
    ) {
        val missing = grants.filterNot { (action, objectName) -> has(user, action, objectName) }
        if (missing.isNotEmpty()) {
            throw ForbiddenException("$what exige ${missing.joinToString(" y ") { (action, objectName) -> "${label(action)} sobre $objectName" }}")
        }
    }

    suspend fun has(
        user: AuthenticatedUser,
        action: String,
        objectName: String
    ): Boolean =
        try {
            currentUser.requirePermission(user, action, metadata.loadDefinition(user.organizationId, objectName).obj.id)
            true
        } catch (_: ForbiddenException) {
            false
        }

    private fun label(action: String): String =
        when (action) {
            Actions.READ -> "permiso de lectura"
            Actions.CREATE -> "permiso de creación"
            Actions.UPDATE -> "permiso de edición"
            Actions.DELETE -> "permiso de borrado"
            else -> "la acción $action"
        }
}
```

- [ ] **Paso 9: Crear `Transactions.kt`**

Crear `src/main/kotlin/sgspe/common/Transactions.kt`:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import wasichai.core.platform.ClusterLock

// una transacción de la base alrededor de un comando (architecture.md §6.2): RecordService no abre ninguna pero se une a
// esta (WAS-ADR-0038), así que las escrituras, sus filas de auditoría y los listeners que escriben se confirman o se
// revierten juntos. el usuario que llama sigue en el contexto. `locked` toma además ClusterLock.withXactLock sobre la
// clave dentro de la misma transacción: dos comandos sobre el mismo registro quedan en fila en todas las réplicas
// (WAS-ADR-0039), y el candado se suelta con el commit o el rollback
@Component
class Transactions(
    private val operator: TransactionalOperator,
    private val locks: ClusterLock
) {
    suspend fun <T> run(block: suspend () -> T): T = operator.executeAndAwait { block() }

    suspend fun <T> locked(
        key: String,
        block: suspend () -> T
    ): T = operator.executeAndAwait { locks.withXactLock("sgspe.$key") { block() } }
}
```

- [ ] **Paso 10: Crear `StrictBody.kt`**

Crear `src/main/kotlin/sgspe/common/StrictBody.kt`:

```kotlin
package sgspe.common

import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException

// el cuerpo de un comando de /api/sgspe/** no admite propiedades desconocidas: un 400 que nombra cada una (api.md §3.2).
// es más estricto que la api genérica de wasichai 0.3.2, que ignora los atributos que el objeto no tiene
object StrictBody {
    fun requireOnly(
        body: Map<String, Any?>,
        allowed: Set<String>,
        prefix: String = ""
    ) {
        val unknown = body.keys.filter { it !in allowed }.sorted()
        if (unknown.isEmpty()) return
        throw ValidationException(
            "Propiedad desconocida en el cuerpo",
            unknown.map { FieldViolation("$prefix$it", "no es una propiedad de este comando; se admiten ${allowed.sorted().joinToString(", ")}") }
        )
    }

    // un objeto json del cuerpo, o vacío si no vino; cualquier otra cosa es un 400 sobre la propiedad
    @Suppress("UNCHECKED_CAST")
    fun objectOf(
        body: Map<String, Any?>,
        name: String
    ): Map<String, Any?> =
        when (val value = body[name]) {
            null -> emptyMap()
            is Map<*, *> -> value as Map<String, Any?>
            else -> throw ValidationException("Cuerpo inválido", name, "debe ser un objeto json")
        }
}
```

- [ ] **Paso 11: Crear `Criteria.kt`**

Crear `src/main/kotlin/sgspe/common/Criteria.kt`:

```kotlin
package sgspe.common

import wasichai.core.data.RecordCriterion
import wasichai.core.platform.SqlIdentifier
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// condiciones de lista que los filtros de igualdad de wasichai no expresan (RecordCriterion, WAS-ADR-0025): rangos,
// conjuntos y el estado de workflow. los valores van enlazados; las columnas salen de la metadata del objeto, salvo las
// de sistema (updated_at, workflow_state, database-model.md §2.4), que wasichai nombra siempre igual
object Criteria {
    fun updatedSince(instant: Instant): RecordCriterion =
        RecordCriterion { _, bind -> "updated_at >= ${bind(OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))}" }

    // workflow_state en un conjunto: la columna que agrega wasichai-workflow al asociar el workflow (WAS-ADR-0013)
    fun inState(states: Collection<String>): RecordCriterion = RecordCriterion { _, bind -> "workflow_state = ANY(${bind(states.toTypedArray())})" }

    fun anyOf(
        field: String,
        ids: Collection<UUID>
    ): RecordCriterion = RecordCriterion { definition, bind -> "${column(definition, field)} = ANY(${bind(ids.toTypedArray())})" }

    fun textIn(
        field: String,
        values: Collection<String>
    ): RecordCriterion = RecordCriterion { definition, bind -> "${column(definition, field)} = ANY(${bind(values.toTypedArray())})" }

    // un DATETIME entre dos instantes, el segundo excluido
    fun between(
        field: String,
        from: Instant,
        until: Instant
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val column = column(definition, field)
            "$column >= ${bind(OffsetDateTime.ofInstant(from, ZoneOffset.UTC))} AND $column < ${bind(OffsetDateTime.ofInstant(until, ZoneOffset.UTC))}"
        }

    fun dateBetween(
        field: String,
        from: LocalDate,
        to: LocalDate
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val column = column(definition, field)
            "$column >= ${bind(from)} AND $column <= ${bind(to)}"
        }

    private fun column(
        definition: wasichai.core.metadata.ObjectDefinition,
        field: String
    ): String = SqlIdentifier.quote(definition.fields.first { it.name == field }.columnName)
}
```

- [ ] **Paso 12: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ValuesTest'
```

Expected: `BUILD SUCCESSFUL`; 2 pruebas en verde.

- [ ] **Paso 13: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.common.RecordsPatchApiTest' --tests 'sgspe.common.RecordsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 2 pruebas en verde.

- [ ] **Paso 14: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/common/ValuesTest.kt src/test/kotlin/sgspe/common/RecordsPatchApiTest.kt src/main/kotlin/sgspe/common/Records.kt src/main/kotlin/sgspe/common/WriteMark.kt src/main/kotlin/sgspe/common/Errors.kt src/main/kotlin/sgspe/common/Values.kt src/main/kotlin/sgspe/common/Callers.kt src/main/kotlin/sgspe/common/Permissions.kt src/main/kotlin/sgspe/common/Transactions.kt src/main/kotlin/sgspe/common/StrictBody.kt src/main/kotlin/sgspe/common/Criteria.kt
git commit -m "$(cat <<'MSG'
feat(common): Records completo y piezas comunes de F2

Records suma lectura por cursor, geometrías, vínculos, transiciones con la marca y un patch que
no borra lo que no nombra. Valores, permisos previos, transacción con candado, cuerpo estricto y
criterios de lista para las guardas y los endpoints.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 3: Reglas y guardas transversales

Cada regla de escritura de sgspe es un `WriteRule` que devuelve hallazgos (`Finding`); `RuleGuard` la convierte
en `RecordWriteGuard` de wasichai, y la consulta en seco de la Tarea 6 la corre sin escribir. `WriteView.after` arma el
registro como quedaría después de la escritura, con la semántica del PUT de wasichai (un campo bloqueado queda como estaba).

Las seis guardas transversales de `docs/workflows.md` y `docs/security.md`:
- `DerivedFieldsGuard`: un campo `"derived": true` solo lo cambia código propio (400); el portal puede reenviar el valor
  guardado tal cual u omitirlo, y `DerivedFieldsKeeper` (un listener) devuelve el valor que el PUT dejó vacío (Review
  Focus 1).
- `TransitionReasonGuard`: las transiciones de la tabla «Transiciones con motivo obligatorio» sin motivo son 400 (en seco,
  una advertencia `REASON_REQUIRED` para que la UI lo pida).
- `FieldChangeReasonGuard`: cambiar `stage` o `exploration_phase` de un proyecto pide motivo (RN-PRY-002).
- `CommandTransitionGuard`: `assess` de WF-B y `approve`/`supersede` de WF-O solo por su endpoint (403 `COMMAND_ONLY`).
- `ExactlyOneGuard`: exactamente uno de los campos de cada grupo de `docs/database-model.md` (400).
- `UserReferenceGuard`: el par `*_user_id`/`*_user_email` completo y de la misma persona (400).

`DomainApiTest` extiende `F2ApiTest` (la base que deja la Fase 2.1, con sus firmas) con lo que piden las pruebas de este
plan: datos de prueba ficticios, un proyecto con CRS de trabajo y un `edit` que, como el portal, reenvía el registro
entero.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/common/Rules.kt`
- Crear: `src/main/kotlin/sgspe/common/WriteView.kt`
- Crear: `src/main/kotlin/sgspe/common/DerivedFieldsGuard.kt`
- Crear: `src/main/kotlin/sgspe/common/DerivedFieldsKeeper.kt`
- Crear: `src/main/kotlin/sgspe/common/TransitionReasonGuard.kt`
- Crear: `src/main/kotlin/sgspe/common/FieldChangeReasonGuard.kt`
- Crear: `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`
- Crear: `src/main/kotlin/sgspe/common/ExactlyOneGuard.kt`
- Crear: `src/main/kotlin/sgspe/common/UserReferenceGuard.kt`
- Probar: `src/test/kotlin/sgspe/DomainApiTest.kt`
- Probar: `src/test/kotlin/sgspe/common/CrossGuardsTest.kt`
- Probar: `src/test/kotlin/sgspe/common/CrossGuardsApiTest.kt`

**Interfaces:**
- Consume: `Records`, `Values`, `WriteMark`, `RuleException` (Tarea 2); `ModelCatalog.derivedFields` (Tarea 1); `RecordWriteGuard`, `RecordWrite`, `AccessPolicy`, `UserRepository` de wasichai.
- Produce:
  - `data class Finding(code, message, field = null, blocking = true, status = HttpStatus.CONFLICT)` con `toException()`.
  - `interface WriteRule { suspend fun findings(change: RecordWrite, dryRun: Boolean = false): List<Finding> }` y
    `abstract class RuleGuard : WriteRule, RecordWriteGuard`.
  - `object GuardOrder` (`COMMAND` 100, `DERIVED` 110, `REASON` 120, `FIELD_REASON` 130, `SCOPE` 200, `EDIT_ANY` 210,
    `IF_MATCH` 220, `EXACTLY_ONE` 300, `USER_REFERENCE` 310, `ASSIGNMENT_ROLE` 320, `DOMAIN` 400).
  - `fun RecordWrite.isLink(): Boolean`; `WriteView.after(change)` y `changed(change)`.
  - `DerivedFieldsKeeper` (RecordChangeListener, `@Order(100)`), motivo `derivado: se conserva lo que el PUT no trajo`.
  - `DomainApiTest : F2ApiTest` (pruebas): además de lo de `F2ApiTest` de la Fase 2.1 (`project`, `assign(account,
    projectId, role)`, `member(role, projectId)`, `transition(objectName, id, name, token, reason)`, `move`, `fixture`,
    `state`…), `patch`, `multipart`, `record`, `createWith`, `edit`, `transitionWith(…, headers, reason)`, `link`,
    `projectWithCrs`, `assignWith(account, projectId, role, extra)`, `organization`, `person`, `stakeholder`,
    `engagement`, `participant`, `problemFields`, `json`, `point`, `square`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/DomainApiTest.kt` (la base de las pruebas de integración de F2):

```kotlin
package sgspe

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import tools.jackson.databind.JsonNode

// la base de las pruebas de integración del plan 2.2: F2ApiTest del plan 2.1 (project, assign, member, transition, move,
// fixture…) más las llamadas y los datos de prueba que piden las guardas y los endpoints de sgspe, todos ficticios
// (RQ-SEC-018). projectWithCrs lleva working_crs (EPSG 32717); las personas de prueba trabajan en el proyecto con una
// project_assignment vigente, porque ProjectScopeGuard acota sus escrituras
abstract class DomainApiTest : F2ApiTest() {
    // ---- llamadas que SgspeApiTest no trae ----

    protected fun patch(
        path: String,
        body: Any,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val result =
            client
                .patch()
                .uri(path)
                .headers { h ->
                    if (token != null) h.set(HttpHeaders.AUTHORIZATION, token)
                    headers.forEach(h::set)
                }.bodyValue(body)
                .exchange()
                .expectBody(String::class.java)
                .returnResult()
        return Response(HttpStatus.valueOf(result.status.value()), result.responseBody ?: "", result.responseHeaders)
    }

    // un multipart: archivos (nombre de parte -> nombre de archivo y bytes) y campos de texto
    protected fun multipart(
        path: String,
        files: Map<String, Pair<String, ByteArray>>,
        fields: Map<String, String>,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): ResultBytes {
        val builder = MultipartBodyBuilder()
        files.forEach { (name, file) -> builder.part(name, file.second).filename(file.first) }
        fields.forEach { (name, value) -> builder.part(name, value) }
        val result =
            client
                .post()
                .uri(path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .headers { h ->
                    if (token != null) h.set(HttpHeaders.AUTHORIZATION, token)
                    headers.forEach(h::set)
                }.body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return ResultBytes(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    protected data class ResultBytes(
        val status: HttpStatus,
        val body: ByteArray,
        val headers: HttpHeaders
    ) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    // ---- registros ----

    protected fun record(
        objectName: String,
        id: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", "/api/objects/$objectName/records/$id", null, HttpStatus.OK, token))

    // un alta por la API genérica con geometrías (GeoJSON EPSG:4326)
    protected fun createWith(
        objectName: String,
        attributes: Map<String, Any?>,
        geometries: Map<String, Any?> = emptyMap(),
        token: String = this.token,
        headers: Map<String, String> = emptyMap()
    ): String =
        tree(
            send("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes, "geometries" to geometries), HttpStatus.CREATED, token, headers)
        )["id"].asString()

    // como el portal: lee el registro y lo reenvía entero con los cambios (el PUT de wasichai reemplaza el registro)
    protected fun edit(
        objectName: String,
        id: String,
        changes: Map<String, Any?>,
        token: String = this.token,
        headers: Map<String, String> = emptyMap(),
        geometries: Map<String, Any?>? = null
    ): Response {
        val current = record(objectName, id, token)["attributes"]
        val attributes = current.properties().associate { (k, v) -> k to json(v) } + changes
        val body = if (geometries == null) mapOf("attributes" to attributes) else mapOf("attributes" to attributes, "geometries" to geometries)
        return exchange("PUT", "/api/objects/$objectName/records/$id", body, token, headers)
    }

    // una transición con cabeceras propias (If-Match); la de F2ApiTest solo lleva el motivo
    protected fun transitionWith(
        objectName: String,
        id: String,
        name: String,
        token: String = this.token,
        headers: Map<String, String> = emptyMap(),
        reason: String? = null
    ): Response =
        exchange(
            "POST",
            "/api/objects/$objectName/records/$id/transitions/$name",
            null,
            token,
            headers + listOfNotNull(reason?.let { REASON to reason(it) })
        )

    protected fun link(
        objectName: String,
        id: String,
        relationship: String,
        otherId: String,
        token: String = this.token
    ): Response = exchange("POST", "/api/objects/$objectName/records/$id/related/$relationship", mapOf("otherId" to otherId), token)

    // ---- datos de prueba ----

    // un proyecto con CRS de trabajo: el área oficial, las importaciones y approve de WF-A lo necesitan
    protected fun projectWithCrs(attributes: Map<String, Any?> = emptyMap()): String = project(mapOf("working_crs" to "EPSG 32717") + attributes)

    // una asignación con campos propios (inactiva, con fechas); la de F2ApiTest es vigente y sin fechas
    protected fun assignWith(
        account: Account,
        projectId: String,
        role: String,
        extra: Map<String, Any?>
    ): String =
        createRecord(
            "project_assignment",
            mapOf("project" to projectId, "member_user_id" to account.id, "member_user_email" to account.email, "role_name" to role, "active" to true) + extra
        )

    protected fun organization(name: String = "Asociación Ejemplo"): String = createRecord("organization", mapOf("name" to name))

    protected fun person(givenNames: String = "Persona Ejemplo"): String = createRecord("person", mapOf("given_names" to givenNames, "sex" to "FEMENINO"))

    protected fun stakeholder(
        project: String,
        attributes: Map<String, Any?> = emptyMap(),
        token: String = this.token
    ): String {
        val actor = if ("person" in attributes) emptyMap() else mapOf("organization" to organization())
        return createRecord("stakeholder", mapOf("project" to project, "stakeholder_scope" to "AIDS") + actor + attributes, token = token)
    }

    protected fun engagement(
        project: String,
        type: String = "REUNION",
        token: String = this.token,
        attributes: Map<String, Any?> = emptyMap()
    ): String = createRecord("engagement", mapOf("project" to project, "engagement_type" to type, "title" to "Reunión de prueba") + attributes, token = token)

    protected fun participant(
        engagement: String,
        token: String = this.token,
        attributes: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "engagement_participant",
            mapOf("engagement" to engagement, "represented_people_count" to 1, "attended" to true) + attributes,
            token = token
        )

    protected fun problemFields(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["field"].asString() }.orEmpty()

    companion object {
        // un valor de JsonNode como lo mandaría el portal
        fun json(node: JsonNode): Any? =
            when {
                node.isNull -> null
                node.isBoolean -> node.asBoolean()
                node.isIntegralNumber -> node.asLong()
                node.isNumber -> node.decimalValue()
                node.isString -> node.asString()
                else -> JSON.convertValue(node, Any::class.java)
            }

        fun point(
            longitude: Double,
            latitude: Double
        ): Map<String, Any?> = mapOf("type" to "Point", "coordinates" to listOf(longitude, latitude))

        // un cuadrado de `side` grados con su esquina en (longitude, latitude)
        fun square(
            longitude: Double,
            latitude: Double,
            side: Double,
            type: String = "Polygon"
        ): Map<String, Any?> {
            val ring =
                listOf(
                    listOf(longitude, latitude),
                    listOf(longitude + side, latitude),
                    listOf(longitude + side, latitude + side),
                    listOf(longitude, latitude + side),
                    listOf(longitude, latitude)
                )
            return if (type ==
                "MultiPolygon"
            ) {
                mapOf("type" to type, "coordinates" to listOf(listOf(ring)))
            } else {
                mapOf("type" to type, "coordinates" to listOf(ring))
            }
        }
    }
}
```

Crear `src/test/kotlin/sgspe/common/CrossGuardsTest.kt` (6 pruebas):

```kotlin
package sgspe.common

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las guardas transversales que no leen la base, sin servidor: motivo en las transiciones, marca de los comandos y
// «exactamente uno de»
class CrossGuardsTest {
    private fun transition(
        objectName: String,
        name: String,
        reason: String? = null
    ) = RecordWrite(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        objectName,
        UUID.randomUUID(),
        RecordChangeKind.TRANSITIONED,
        transition = name,
        reason = reason
    )

    @Test
    fun `retirar un grupo de interes sin motivo es un 400 sobre reason, y con motivo pasa`() {
        runBlocking {
            val guard = TransitionReasonGuard()
            val error = assertThrows<ValidationException> { guard.beforeWrite(transition("stakeholder", "retire_profiled")) }
            assertEquals("reason", error.violations.single().field)
            guard.beforeWrite(transition("stakeholder", "retire_profiled", "ya no es actor del proyecto"))
            guard.beforeWrite(transition("stakeholder", "profile"))
        }
    }

    @Test
    fun `en seco, el motivo faltante es una advertencia y no un bloqueo`() {
        runBlocking {
            val finding = TransitionReasonGuard().findings(transition("field_visit", "cancel"), dryRun = true).single()
            assertFalse(finding.blocking)
            assertEquals("REASON_REQUIRED", finding.code)
        }
    }

    @Test
    fun `la tabla de motivos es la de workflows md para los objetos de F2`() {
        assertEquals(setOf("return_to_draft", "suspend", "cancel_draft", "cancel_in_review"), TransitionReasonGuard.REQUIRED["project"])
        assertEquals(
            setOf("postpone", "return_minutes", "cancel_planned", "cancel_scheduled", "cancel_postponed"),
            TransitionReasonGuard.REQUIRED["engagement"]
        )
        assertEquals(setOf("cancel"), TransitionReasonGuard.REQUIRED["field_visit"])
    }

    @Test
    fun `una transicion de comando sin la marca es un 403, con la marca pasa`() {
        runBlocking {
            val guard = CommandTransitionGuard()
            assertThrows<ForbiddenException> { guard.beforeWrite(transition("stakeholder", "assess")) }
            assertThrows<ForbiddenException> { guard.beforeWrite(transition("area_of_influence", "supersede")) }
            withContext(SgspeWrite) { guard.beforeWrite(transition("stakeholder", "assess")) }
            guard.beforeWrite(transition("stakeholder", "profile"))
            assertTrue(guard.findings(transition("area_of_influence", "approve"), dryRun = true).single().blocking)
        }
    }

    @Test
    fun `exactamente uno de`() {
        val group = listOf("person", "organization")
        assertNull(ExactlyOneGuard.violation(group, mapOf("person" to "p-1")))
        val none = ExactlyOneGuard.violation(group, mapOf("person" to null, "organization" to " "))!!
        assertEquals(HttpStatus.BAD_REQUEST, none.status)
        assertEquals("Informe exactamente uno de person, organization", none.message)
        val both = ExactlyOneGuard.violation(group, mapOf("person" to "p-1", "organization" to "o-1"))!!
        assertEquals("person", both.field)
        assertEquals(listOf(listOf("from_person", "from_organization"), listOf("to_person", "to_organization")), ExactlyOneGuard.GROUPS["actor_relationship"])
    }

    @Test
    fun `un hallazgo se vuelve la excepcion de su estado`() {
        assertEquals(400, Finding("X", "m", "f", status = HttpStatus.BAD_REQUEST).toException().status.value())
        assertEquals(403, Finding("X", "m", status = HttpStatus.FORBIDDEN).toException().status.value())
        val conflict = Finding("X", "m", "working_crs").toException()
        assertEquals(409, conflict.status.value())
        assertEquals("working_crs", conflict.violations.single().field)
    }
}
```

Crear `src/test/kotlin/sgspe/common/CrossGuardsApiTest.kt` (5 pruebas):

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// las guardas transversales de sgspe.common por la API genérica, con los objetos de F2: derivados (domain-model §4.6),
// «exactamente uno de» (§4.4) y pares de usuario (ASM-023)
class CrossGuardsApiTest : DomainApiTest() {
    @Test
    fun `un derivado enviado al crear es un 400 que lo nombra y nada se guarda`() {
        val project = projectWithCrs()
        val organization = organization()
        val response =
            exchange(
                "POST",
                "/api/objects/stakeholder/records",
                mapOf("attributes" to mapOf("project" to project, "organization" to organization, "interest_index" to 4.5))
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("interest_index"), problemFields(response.body))
        assertEquals(0, tree(send("GET", "/api/objects/stakeholder/records?organization=$organization", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `el portal reenvia los derivados tal cual y pasa, cambiarlos es un 400`() {
        val stakeholder = stakeholder(projectWithCrs())
        assertEquals(HttpStatus.OK, edit("stakeholder", stakeholder, mapOf("profile_summary" to "Ronda de un caserío")).status)
        val changed = edit("stakeholder", stakeholder, mapOf("influence_group" to "ESTRATEGICO"))
        assertEquals(HttpStatus.BAD_REQUEST, changed.status, changed.body)
        assertEquals(listOf("influence_group"), problemFields(changed.body))
    }

    @Test
    fun `vincular no es escribir un derivado`() {
        val project = projectWithCrs()
        val engagement = engagement(project)
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", engagement, "engagement_stakeholder", stakeholder(project)).status)
    }

    @Test
    fun `exactamente uno de persona u organizacion, y de los extremos de una relacion entre actores`() {
        val project = projectWithCrs()
        val both =
            exchange(
                "POST",
                "/api/objects/stakeholder/records",
                mapOf("attributes" to mapOf("project" to project, "person" to person(), "organization" to organization()))
            )
        assertEquals(HttpStatus.BAD_REQUEST, both.status)
        assertEquals(listOf("person"), problemFields(both.body))
        val none =
            exchange("POST", "/api/objects/contact_method/records", mapOf("attributes" to mapOf("channel" to "TELEFONO", "contact_value" to "000000000")))
        assertEquals(HttpStatus.BAD_REQUEST, none.status)
        val relation =
            exchange(
                "POST",
                "/api/objects/actor_relationship/records",
                mapOf(
                    "attributes" to
                        mapOf("relationship_type" to "APOYA", "from_person" to person(), "to_person" to person(), "to_organization" to organization())
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, relation.status)
        assertEquals(listOf("to_person"), problemFields(relation.body))
    }

    @Test
    fun `un par de usuario informado debe ser un usuario de la organizacion con su correo`() {
        val project = projectWithCrs()
        val someone = userWithRole("ANALYST")
        val wrongEmail =
            exchange(
                "POST",
                "/api/objects/project_assignment/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "project" to project,
                            "member_user_id" to someone.id,
                            "member_user_email" to "otra@sgspe.test",
                            "role_name" to "ANALYST",
                            "active" to true
                        )
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, wrongEmail.status)
        assertEquals(listOf("member_user_id"), problemFields(wrongEmail.body))
        val half =
            exchange(
                "POST",
                "/api/objects/stakeholder/records",
                mapOf("attributes" to mapOf("project" to project, "organization" to organization(), "owner_user_id" to someone.id))
            )
        assertEquals(HttpStatus.BAD_REQUEST, half.status)
        assertTrue("owner_user_email" in problemFields(half.body))
        assign(someone, project, "ANALYST")
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.CrossGuardsTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'ExactlyOneGuard'`).

- [ ] **Paso 3: Crear `Rules.kt`**

Crear `src/main/kotlin/sgspe/common/Rules.kt`:

```kotlin
package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.common.FieldViolation
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard

// lo que una regla de sgspe encuentra en una escritura: un bloqueo (la escritura no ocurre) o una advertencia (pide
// justificación pero no bloquea). code es una constante en inglés UPPER_SNAKE (api.md §3.2); message va en español y sin
// datos personales (RQ-SEC-010). status: 400 si la regla rechaza los datos enviados; 409 si una transición choca con lo
// guardado o con otros registros; 403 si falta un permiso, una asignación o la marca de un comando (api.md §3.3)
data class Finding(
    val code: String,
    val message: String,
    val field: String? = null,
    val blocking: Boolean = true,
    val status: HttpStatus = HttpStatus.CONFLICT
) {
    fun toException(): WasichaiException =
        when (status) {
            HttpStatus.BAD_REQUEST -> ValidationException(message, field ?: "record", message)
            HttpStatus.FORBIDDEN -> ForbiddenException(message)
            else -> RuleException(status, message, field?.let { listOf(FieldViolation(it, message)) }.orEmpty())
        }
}

// una regla que POST /api/sgspe/transition-checks puede correr en seco. dryRun: la escritura no va a ocurrir, así que una
// regla que pide algo que la petición traería (el motivo) lo informa como advertencia
interface WriteRule {
    suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean = false
    ): List<Finding>
}

// una guarda de wasichai (WAS-ADR-0040) hecha de reglas: la primera que bloquea lanza su excepción, antes de guardar
abstract class RuleGuard :
    RecordWriteGuard,
    WriteRule {
    override suspend fun beforeWrite(change: RecordWrite) {
        findings(change, dryRun = false).firstOrNull { it.blocking }?.let { throw it.toException() }
    }
}

// el orden en que wasichai llama a las guardas de sgspe (@Order): primero lo que nunca puede pasar por esa puerta (la
// marca de un comando, los derivados, el motivo), después quién escribe (alcance, edición de lo propio, precondición) y
// al final las reglas de datos y las de cada paquete
object GuardOrder {
    const val COMMAND = 100
    const val DERIVED = 110
    const val REASON = 120
    const val FIELD_REASON = 130
    const val SCOPE = 200
    const val EDIT_ANY = 210
    const val IF_MATCH = 220
    const val EXACTLY_ONE = 300
    const val USER_REFERENCE = 310
    const val ASSIGNMENT_ROLE = 320
    const val DOMAIN = 400
}

// una escritura que vincula o desvincula dos registros: wasichai la manda como UPDATE con `rel:<relación>`
fun RecordWrite.isLink(): Boolean = kind == RecordChangeKind.UPDATED && attributes.orEmpty().keys.any { it.startsWith("rel:") }
```

- [ ] **Paso 4: Crear `WriteView.kt`**

Crear `src/main/kotlin/sgspe/common/WriteView.kt`:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AccessPolicy
import wasichai.core.identity.CurrentUser
import wasichai.core.identity.FieldAccess
import wasichai.core.metadata.MetadataService

// cómo queda un registro después de una escritura que una guarda está por dejar pasar. el PUT de wasichai reemplaza el
// registro: cada campo que el escritor puede escribir toma lo que manda, y uno que falta queda vacío; los que sus roles
// bloquean conservan lo guardado (RecordService y PhysicalTableRecordStore de wasichai 0.3.2). un alta es lo que manda;
// una transición, un vínculo o una baja no cambian ningún campo
@Component
class WriteView(
    private val metadata: MetadataService,
    private val access: AccessPolicy,
    private val currentUser: CurrentUser
) {
    suspend fun after(change: RecordWrite): Map<String, Any?> {
        val before = change.before.orEmpty()
        return when {
            change.kind == RecordChangeKind.CREATED -> change.attributes.orEmpty()
            change.kind != RecordChangeKind.UPDATED || change.isLink() -> before
            else -> {
                val definition = metadata.loadDefinition(change.organizationId, change.objectName)
                val fieldAccess = fieldAccess(change)
                val attributes = change.attributes.orEmpty()
                definition.fields
                    .filter { it.type.name != "GEOMETRY" }
                    .associate { field ->
                        val writes = field.editable && fieldAccess.canWrite(field.id)
                        field.name to if (writes) attributes[field.name] else before[field.name]
                    }
            }
        }
    }

    // los campos cuyo valor cambia con la escritura. en un alta, los que llegan con valor
    suspend fun changed(change: RecordWrite): Set<String> {
        val after = after(change)
        if (change.kind == RecordChangeKind.CREATED) return after.filterValues { Values.text(it) != null }.keys
        val before = change.before.orEmpty()
        return (after.keys + before.keys).filter { !Values.same(after[it], before[it]) }.toSet()
    }

    private suspend fun fieldAccess(change: RecordWrite): FieldAccess {
        if (change.userId == null) return FieldAccess.FULL
        return access.fieldAccess(currentUser.require(), change.objectId)
    }
}
```

- [ ] **Paso 5: Crear `DerivedFieldsGuard.kt`**

Crear `src/main/kotlin/sgspe/common/DerivedFieldsGuard.kt`:

```kotlin
package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// un campo derivado ("derived": true en model/model.json) solo lo escribe el código de sgspe, con la marca SgspeWrite
// (domain-model §4.6, api.md §2). por la API genérica, /admin o un módulo: un 400 que nombra el campo si el alta trae un
// valor o si el cambio lo deja distinto de lo guardado. el PUT reemplaza el registro: reenviar un derivado tal como se
// leyó no es cambiarlo, y omitirlo tampoco (el portal de la Fase 2.3 no los manda): lo que el PUT deja vacío lo devuelve
// DerivedFieldsKeeper
@Component
@Order(GuardOrder.DERIVED)
class DerivedFieldsGuard(
    private val catalog: ModelCatalog,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink() || WriteMark.present()) return emptyList()
        val derived = catalog.derivedFields(change.objectName)
        if (derived.isEmpty()) return emptyList()
        val sent = change.attributes.orEmpty().keys
        val touched = view.changed(change).filter { it in derived && it in sent }.sorted()
        return touched.map { field ->
            Finding(
                code = "DERIVED_FIELD",
                message = "El campo $field es derivado: lo calcula SGSPE y no se escribe por esta vía",
                field = field,
                status = HttpStatus.BAD_REQUEST
            )
        }
    }
}
```

- [ ] **Paso 6: Crear `DerivedFieldsKeeper.kt`**

Crear `src/main/kotlin/sgspe/common/DerivedFieldsKeeper.kt`:

```kotlin
package sgspe.common

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener

// el PUT de wasichai deja vacío todo campo escribible que el cuerpo no trae, y el portal (Fase 2.3) no manda los derivados:
// después de un cambio sin la marca, cada derivado que tenía valor y quedó vacío vuelve a su valor, con la marca y el
// motivo de abajo. un null explícito no llega aquí: es un cambio, y DerivedFieldsGuard lo rechazó con un 400. corre antes
// que los listeners que recalculan (AreaListener), que después escriben su valor nuevo
@Component
@Order(100)
class DerivedFieldsKeeper(
    private val catalog: ModelCatalog,
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.UPDATED || WriteMark.present()) return
        val before = change.before ?: return
        val after = change.after ?: return
        if (after.keys.any { it.startsWith("rel:") }) return
        val lost = catalog.derivedFields(change.objectName).filter { before[it] != null && after.containsKey(it) && after[it] == null }
        if (lost.isEmpty()) return
        try {
            records.patch(change.objectName, change.recordId, lost.associateWith { before[it] }, REASON)
        } catch (e: Exception) {
            log.warn("DerivedFieldsKeeper: no se devolvieron {} de {} {}: {}", lost, change.objectName, change.recordId, e.message)
        }
    }

    companion object {
        const val REASON = "derivado: se conserva lo que el PUT no trajo"
    }
}
```

- [ ] **Paso 7: Crear `TransitionReasonGuard.kt`**

Crear `src/main/kotlin/sgspe/common/TransitionReasonGuard.kt`:

```kotlin
package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// el motivo obligatorio en las transiciones de devolver, rechazar, suspender, cancelar, retirar y reabrir de los objetos
// sin requiresReason (RQ-AUD-002, ASM-058): la tabla «Transiciones con motivo obligatorio» de workflows.md, tal cual. en
// los objetos requiresReason lo exige wasichai antes de llegar aquí (WAS-ADR-0041)
@Component
@Order(GuardOrder.REASON)
class TransitionReasonGuard : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition !in REQUIRED[change.objectName].orEmpty()) return emptyList()
        if (!dryRun && !change.reason.isNullOrBlank()) return emptyList()
        return listOf(
            Finding(
                code = "REASON_REQUIRED",
                message = "La transición $transition de ${change.objectName} exige un motivo (X-Change-Reason)",
                field = "reason",
                blocking = !dryRun,
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    companion object {
        val REQUIRED: Map<String, Set<String>> =
            mapOf(
                "project" to setOf("return_to_draft", "suspend", "cancel_draft", "cancel_in_review"),
                "stakeholder" to setOf("retire_identified", "retire_profiled", "retire_assessed", "reinstate"),
                "engagement" to setOf("postpone", "return_minutes", "cancel_planned", "cancel_scheduled", "cancel_postponed"),
                "social_program" to
                    setOf("return_to_draft", "suspend", "cancel_draft", "cancel_pending_approval", "cancel_planned", "cancel_suspended"),
                "early_warning" to setOf("dismiss"),
                "commitment_candidate" to setOf("reject", "mark_duplicate"),
                "ai_suggestion" to setOf("reject", "mark_duplicate"),
                "field_visit" to setOf("cancel"),
                "task" to setOf("cancel_open", "cancel_in_progress", "cancel_blocked")
            )
    }
}
```

- [ ] **Paso 8: Crear `FieldChangeReasonGuard.kt`**

Crear `src/main/kotlin/sgspe/common/FieldChangeReasonGuard.kt`:

```kotlin
package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// el motivo al cambiar ciertos campos que no son estados: la etapa y la fase de exploración del proyecto cambian con
// motivo y nunca son estados de WF-A (RN-PRJ-001, CON-034). darles su primer valor no es cambiarlos
@Component
@Order(GuardOrder.FIELD_REASON)
class FieldChangeReasonGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.UPDATED || change.isLink()) return emptyList()
        val fields = FIELDS[change.objectName] ?: return emptyList()
        if (!change.reason.isNullOrBlank()) return emptyList()
        val before = change.before.orEmpty()
        val changed = view.changed(change).filter { it in fields && Values.text(before[it]) != null }.sorted()
        return changed.map { field ->
            Finding(
                code = "FIELD_REASON_REQUIRED",
                message = "Cambiar $field exige un motivo (X-Change-Reason)",
                field = "reason",
                status = HttpStatus.BAD_REQUEST
            )
        }
    }

    companion object {
        val FIELDS: Map<String, Set<String>> = mapOf("project" to setOf("stage", "exploration_phase"))
    }
}
```

- [ ] **Paso 9: Crear `CommandTransitionGuard.kt`**

Crear `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`:

```kotlin
package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las transiciones de comando solo llegan desde su endpoint, que escribe varios registros en una transacción y lleva la
// marca SgspeWrite (workflows.md, «Transiciones de comando»). por la API genérica de workflow, sin la marca: 403, como la
// puerta cerrada de un objeto apiOnly. las de F3 y F4 las agregan sus fases a COMMANDS
@Component
@Order(GuardOrder.COMMAND)
class CommandTransitionGuard : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val endpoint = COMMANDS[change.objectName]?.get(change.transition) ?: return emptyList()
        if (!dryRun && WriteMark.present()) return emptyList()
        return listOf(
            Finding(
                code = "COMMAND_ONLY",
                message = "La transición ${change.transition} de ${change.objectName} solo se aplica con $endpoint",
                status = HttpStatus.FORBIDDEN
            )
        )
    }

    companion object {
        val COMMANDS: Map<String, Map<String, String>> =
            mapOf(
                "stakeholder" to mapOf("assess" to "POST /api/sgspe/stakeholders/{id}/assessments"),
                "area_of_influence" to
                    mapOf(
                        "approve" to "POST /api/sgspe/areas-of-influence/{id}/approve",
                        "supersede" to "POST /api/sgspe/areas-of-influence/{id}/approve"
                    )
            )
    }
}
```

- [ ] **Paso 10: Crear `ExactlyOneGuard.kt`**

Crear `src/main/kotlin/sgspe/common/ExactlyOneGuard.kt`:

```kotlin
package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// «exactamente uno de»: varias relaciones opcionales de las que el registro lleva una y solo una (domain-model §4.4, las
// fichas de database-model.md con su línea «Exactamente uno de»). se juzga el registro como queda después de la escritura.
// los grupos de F3 en adelante (delivery, agreement_party, task) los agrega su fase
@Component
@Order(GuardOrder.EXACTLY_ONE)
class ExactlyOneGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val groups = GROUPS[change.objectName] ?: return emptyList()
        val after = view.after(change)
        return groups.mapNotNull { group -> violation(group, after) }
    }

    companion object {
        val GROUPS: Map<String, List<List<String>>> =
            mapOf(
                "area_of_influence_member" to listOf(listOf("locality", "community", "administrative_unit")),
                "land_right" to listOf(listOf("holder_person", "holder_organization")),
                "stakeholder" to listOf(listOf("person", "organization")),
                "contact_method" to listOf(listOf("person", "organization")),
                "actor_relationship" to listOf(listOf("from_person", "from_organization"), listOf("to_person", "to_organization")),
                "network_node" to listOf(listOf("person", "organization"))
            )

        // pura, para las pruebas unitarias: null si el grupo tiene exactamente uno informado
        fun violation(
            group: List<String>,
            after: Map<String, Any?>
        ): Finding? {
            val informed = group.filter { Values.text(after[it]) != null }
            if (informed.size == 1) return null
            val message =
                if (informed.isEmpty()) {
                    "Informe exactamente uno de ${group.joinToString(", ")}"
                } else {
                    "Solo uno de ${group.joinToString(", ")} puede estar informado; vienen ${informed.joinToString(", ")}"
                }
            return Finding(code = "EXACTLY_ONE", message = message, field = group.first(), status = HttpStatus.BAD_REQUEST)
        }
    }
}
```

- [ ] **Paso 11: Crear `UserReferenceGuard.kt`**

Crear `src/main/kotlin/sgspe/common/UserReferenceGuard.kt`:

```kotlin
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
```

- [ ] **Paso 12: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.CrossGuardsTest'
```

Expected: `BUILD SUCCESSFUL`; 6 pruebas en verde.

- [ ] **Paso 13: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.common.CrossGuardsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 5 pruebas en verde.

- [ ] **Paso 14: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/DomainApiTest.kt src/test/kotlin/sgspe/common/CrossGuardsTest.kt src/test/kotlin/sgspe/common/CrossGuardsApiTest.kt src/main/kotlin/sgspe/common/Rules.kt src/main/kotlin/sgspe/common/WriteView.kt src/main/kotlin/sgspe/common/DerivedFieldsGuard.kt src/main/kotlin/sgspe/common/DerivedFieldsKeeper.kt src/main/kotlin/sgspe/common/TransitionReasonGuard.kt src/main/kotlin/sgspe/common/FieldChangeReasonGuard.kt src/main/kotlin/sgspe/common/CommandTransitionGuard.kt src/main/kotlin/sgspe/common/ExactlyOneGuard.kt src/main/kotlin/sgspe/common/UserReferenceGuard.kt
git commit -m "$(cat <<'MSG'
feat(common): reglas de escritura y guardas transversales de F2

WriteRule y RuleGuard sobre RecordWriteGuard, con hallazgos que la consulta en seco reutiliza.
Derivados, motivo de transición y de campo, transiciones de comando, exactamente uno y referencias
a usuario. DomainApiTest es la base de las pruebas de integración de F2.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 4: If-Match: concurrencia optimista

ADR-0025 (se acepta al iniciar F2) y `docs/api.md` §3.6: los comandos marcados `IM` exigen `If-Match` con el
`updatedAt` leído (428 sin él, 412 si cambió). En la API genérica de wasichai, `IfMatchFilter` deja el valor de la cabecera
en el contexto de Reactor y `IfMatchGuard` lo compara dentro de la escritura; un valor que no es un instante es 400 con
Problem Details. La guarda no compara las escrituras con la marca: un listener que escribe el mismo registro en la misma
petición (Review Focus 2) no es la escritura del cliente.

Un `updatedAt` distinto no siempre es un conflicto: los listeners de F2 escriben derivados (conteos de asistentes, área)
en el registro que el cliente sigue editando, y el portal registra una interacción en varias escrituras sucesivas con el
`updatedAt` que le devolvió el alta. `Versions` lee el historial del registro (`AuditQueryService`, con los permisos de quien
llama) y deja pasar el `If-Match` si después de la versión leída solo cambiaron derivados o vínculos; cualquier otro cambio
visible es 412. La usan `IfMatchGuard` y los comandos `IM`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/common/Preconditions.kt`
- Probar: `src/test/kotlin/sgspe/common/IfMatchTest.kt`
- Probar: `src/test/kotlin/sgspe/common/ConcurrencyApiTest.kt`

**Interfaces:**
- Consume: `Records.find`, `WriteMark`, `PreconditionFailedException`, `PreconditionRequiredException` (Tarea 2); `GuardOrder.IF_MATCH` (Tarea 3); `ModelCatalog.derivedFields` (Tarea 1); `AuditQueryService` de wasichai.
- Produce:
  - `object IfMatch { const val HEADER = "If-Match"; fun parse(raw: String?): Instant?; fun check(expected: Instant?,
    current: Instant?, what: String); fun required(raw: String?): Instant; fun onlyDerivedSince(expected: Instant,
    writes: List<LaterWrite>, derived: Set<String>): Boolean }`; `data class LaterWrite(occurredAt: Instant, fields: Set<String>)`.
  - `Versions.check(objectName, recordId, expected: Instant?, current: Instant?, what)`: lo que usan los comandos `IM`.
  - `IfMatchFilter` (WebFilter) e `IfMatchGuard` (RecordWriteGuard).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/IfMatchTest.kt` (5 pruebas):

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException
import java.time.Instant

// api.md §3.6: If-Match con el updatedAt entre comillas; 412 si cambió, 428 si un comando lo exige y no vino
class IfMatchTest {
    private val read = Instant.parse("2026-10-07T14:03:11.482913Z")

    @Test
    fun `lee el instante entre comillas, debil o sin comillas, y asterisco o vacio no comparan`() {
        assertEquals(read, IfMatch.parse("\"2026-10-07T14:03:11.482913Z\""))
        assertEquals(read, IfMatch.parse("W/\"2026-10-07T14:03:11.482913Z\""))
        assertEquals(read, IfMatch.parse("2026-10-07T14:03:11.482913Z"))
        assertNull(IfMatch.parse("*"))
        assertNull(IfMatch.parse(null))
        assertNull(IfMatch.parse(" "))
    }

    @Test
    fun `un valor que no es un instante es un 400 sobre If-Match`() {
        val error = assertThrows<ValidationException> { IfMatch.parse("\"ayer\"") }
        assertEquals("If-Match", error.violations.single().field)
    }

    @Test
    fun `compara instantes, no textos, y un cambio es 412 con el valor actual`() {
        IfMatch.check(read, Instant.parse("2026-10-07T14:03:11.482913000Z"), "El registro")
        val error = assertThrows<PreconditionFailedException> { IfMatch.check(read, Instant.parse("2026-10-07T14:05:42.100371Z"), "El compromiso") }
        assertEquals(412, error.status.value())
        assertEquals("valor actual 2026-10-07T14:05:42.100371Z", error.violations.single().message)
        IfMatch.check(null, Instant.now(), "sin expectativa no compara")
    }

    @Test
    fun `un comando que lo exige sin el es 428`() {
        val error = assertThrows<PreconditionRequiredException> { IfMatch.required(null) }
        assertEquals(428, error.status.value())
        assertEquals(read, IfMatch.required("\"2026-10-07T14:03:11.482913Z\""))
    }

    @Test
    fun `despues de la version leida, solo derivados y vinculos no invalidan el If-Match`() {
        val created = Instant.parse("2026-10-07T14:00:00Z")
        val derived = setOf("attendees_total_count")
        val writes =
            listOf(
                LaterWrite(created.plusMillis(1), setOf("title", "engagement_type")),
                LaterWrite(created.plusSeconds(1), setOf("rel:engagement_topic")),
                LaterWrite(created.plusSeconds(2), setOf("attendees_total_count"))
            )
        assertEquals(true, IfMatch.onlyDerivedSince(created, writes, derived))
        val edited = writes + LaterWrite(created.plusSeconds(3), setOf("title"))
        assertEquals(false, IfMatch.onlyDerivedSince(created, edited, derived))
        assertEquals(false, IfMatch.onlyDerivedSince(created.plusSeconds(9), writes, derived))
        // un If-Match que no es la versión de ninguna escritura (anterior al alta) no pasa
        assertEquals(false, IfMatch.onlyDerivedSince(Instant.parse("2020-01-01T00:00:00Z"), writes, derived))
    }
}
```

Crear `src/test/kotlin/sgspe/common/ConcurrencyApiTest.kt` (2 pruebas):

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// la precondición sobre updatedAt por la API genérica (api.md §3.6, ADR-0025, RNF-INT-003): If-Match viejo, 412 con el
// valor actual; el que se leyó, pasa; sin cabecera gana la última escritura, como en /admin (UPS-04)
class ConcurrencyApiTest : DomainApiTest() {
    @Test
    fun `un PUT con el updatedAt que se leyo pasa y uno viejo es 412 con el valor actual`() {
        val locality = createRecord("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO"))
        val read = record("locality", locality)["updatedAt"].asString()
        assertEquals(
            HttpStatus.OK,
            edit("locality", locality, mapOf("alternative_names" to "Ejemplo Alto"), headers = mapOf(IfMatch.HEADER to "\"$read\"")).status
        )
        val stale = edit("locality", locality, mapOf("alternative_names" to "Ejemplo Bajo"), headers = mapOf(IfMatch.HEADER to "\"$read\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, stale.status, stale.body)
        val current = record("locality", locality)
        assertEquals("Ejemplo Alto", current["attributes"]["alternative_names"].asString())
        assertEquals("valor actual ${current["updatedAt"].asString()}", tree(stale.body)["errors"][0]["message"].asString())
        assertEquals(HttpStatus.OK, edit("locality", locality, mapOf("alternative_names" to "Sin precondición")).status)
    }

    @Test
    fun `una transicion y un borrado tambien se condicionan, y un If-Match que no es un instante es 400`() {
        val project = projectWithCrs()
        val visit = createRecord("field_visit", mapOf("project" to project, "title" to "Visita de prueba"))
        val read = record("field_visit", visit)["updatedAt"].asString()
        edit("field_visit", visit, mapOf("itinerary" to "Caserío Ejemplo"))
        assertEquals(HttpStatus.PRECONDITION_FAILED, transitionWith("field_visit", visit, "start", headers = mapOf(IfMatch.HEADER to "\"$read\"")).status)
        assertEquals(HttpStatus.BAD_REQUEST, transitionWith("field_visit", visit, "start", headers = mapOf(IfMatch.HEADER to "\"ayer\"")).status)
        val node = createRecord("topic", mapOf("code" to uniqueName("T-"), "name" to "Agua", "active" to true))
        val nodeRead = record("topic", node)["updatedAt"].asString()
        edit("topic", node, mapOf("description" to "Calidad y cantidad"))
        assertEquals(
            HttpStatus.PRECONDITION_FAILED,
            exchange(
                "DELETE",
                "/api/objects/topic/records/$node",
                null,
                headers =
                    mapOf(IfMatch.HEADER to "\"$nodeRead\"")
            ).status
        )
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.IfMatchTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'IfMatch'`).

- [ ] **Paso 3: Crear `Preconditions.kt`**

Crear `src/main/kotlin/sgspe/common/Preconditions.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.reactor.ReactorContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context
import wasichai.core.audit.AuditQueryService
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.UUID

// la precondición sobre updatedAt (api.md §3.6, ADR-0025, RNF-INT-003, ASM-035): el portal manda en If-Match el
// updatedAt que leyó, entre comillas como una etiqueta de entidad. se comparan instantes, no textos, así un formato
// distinto del mismo instante no falla. `*` no compara nada (RFC 9110)
object IfMatch {
    const val HEADER = "If-Match"

    // null: sin cabecera o `*`. un valor que no es un instante entre comillas es un 400 sobre If-Match
    fun parse(raw: String?): Instant? {
        val value = raw?.trim()?.ifEmpty { null } ?: return null
        if (value == "*") return null
        val unquoted = value.removePrefix("W/").removeSurrounding("\"")
        return try {
            Instant.parse(unquoted)
        } catch (_: DateTimeParseException) {
            throw ValidationException("If-Match inválido", HEADER, "envíe el updatedAt que leyó, entre comillas")
        }
    }

    // 412 si el registro cambió después de que el cliente lo leyera
    fun check(
        expected: Instant?,
        current: Instant?,
        what: String
    ) {
        if (expected == null) return
        if (current == null || current != expected) {
            throw PreconditionFailedException("$what cambió después de que lo leyeras: vuelve a cargarlo", current?.toString())
        }
    }

    // un comando con la marca IM de api.md §4: sin If-Match, 428
    fun required(raw: String?): Instant = parse(raw) ?: throw PreconditionRequiredException("Este comando exige If-Match con el updatedAt que leíste")

    // si después de la versión que leyó el cliente solo hubo escrituras de derivados. la primera escritura en o después de
    // `expected` es la que produjo esa versión y no cuenta: wasichai anota en audit_log un instante igual o apenas posterior
    // al updatedAt que escribió, así que tiene que caer dentro de VERSION_WINDOW (un If-Match que no es la versión de
    // ninguna escritura, como uno anterior al alta, no pasa). de las siguientes, un vínculo (rel:…) no cambia la versión y
    // un campo que quien llama no puede leer no le llega en el historial, así que tampoco cuenta. así un listener que
    // actualiza un derivado (los conteos de asistentes, el área) no invalida el If-Match de quien sigue trabajando
    fun onlyDerivedSince(
        expected: Instant,
        writes: List<LaterWrite>,
        derived: Set<String>
    ): Boolean {
        val after = writes.filter { !it.occurredAt.isBefore(expected) }.sortedBy { it.occurredAt }
        val version = after.firstOrNull() ?: return false
        if (version.occurredAt.isAfter(expected.plus(VERSION_WINDOW))) return false
        return after.drop(1).all { write -> write.fields.filterNot { it.startsWith("rel:") }.all { it in derived } }
    }

    private val VERSION_WINDOW: Duration = Duration.ofSeconds(2)
}

// una escritura del historial de un registro (audit_log), con los campos que cambió tal como los ve quien llama
data class LaterWrite(
    val occurredAt: Instant,
    val fields: Set<String>
)

// la comparación de If-Match con el registro guardado: igual, pasa; distinto, pasa solo si lo que cambió desde la versión
// leída son derivados (IfMatch.onlyDerivedSince, leído del historial con los permisos de quien llama); si no, 412. la usan
// IfMatchGuard y los comandos con IM
@Component
class Versions(
    private val audit: AuditQueryService,
    private val catalog: ModelCatalog
) {
    suspend fun check(
        objectName: String,
        recordId: UUID,
        expected: Instant?,
        current: Instant?,
        what: String
    ) {
        if (expected == null || current == expected) return
        if (current != null && onlyDerived(objectName, recordId, expected)) return
        IfMatch.check(expected, current, what)
    }

    private suspend fun onlyDerived(
        objectName: String,
        recordId: UUID,
        expected: Instant
    ): Boolean {
        val writes =
            audit.history(objectName, recordId, HISTORY).mapNotNull { entry ->
                entry.occurredAt?.let { LaterWrite(it, entry.changes.map { change -> change.field }.toSet()) }
            }
        return IfMatch.onlyDerivedSince(expected, writes, catalog.derivedFields(objectName))
    }

    companion object {
        private const val HISTORY = 50
    }
}

// lo que pide el If-Match de una petición a la API genérica: el registro de la ruta y el valor tal como llegó. lo lee la
// guarda, dentro del controlador, para que un valor inválido sea un 400 con Problem Details y no un error del filtro
data class IfMatchExpectation(
    val objectName: String,
    val recordId: UUID,
    val raw: String
)

// pone la expectativa en el contexto de Reactor, como el filtro de correlación pone su identificador: la guarda corre en
// la corrutina de la petición, que conserva ese contexto (WAS-ADR-0040). solo las escrituras de la API genérica sobre un
// registro (PUT y DELETE de /api/objects/{objeto}/records/{id}, sus transiciones y sus vínculos); los comandos propios
// comparan dentro de su transacción y con su candado
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class IfMatchFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val request = exchange.request
        val raw = request.headers.getFirst(IfMatch.HEADER) ?: return chain.filter(exchange)
        if (request.method == HttpMethod.GET) return chain.filter(exchange)
        val match = RECORD_PATH.find(request.path.value()) ?: return chain.filter(exchange)
        val recordId = runCatching { UUID.fromString(match.groupValues[2]) }.getOrNull() ?: return chain.filter(exchange)
        val expectation = IfMatchExpectation(match.groupValues[1], recordId, raw)
        return chain.filter(exchange).contextWrite(Context.of(KEY, expectation))
    }

    companion object {
        const val KEY = "sgspe.ifMatch"
        private val RECORD_PATH = Regex("^/api/objects/([a-z][a-z0-9_]*)/records/([0-9a-fA-F-]{36})(/.*)?$")
    }
}

// compara la expectativa con el updatedAt guardado antes de cambiar, transicionar, vincular o borrar el registro de la
// ruta. por la API genérica no hay transacción: queda una ventana mínima entre comparar y escribir hasta UPS-04
@Component
@Order(GuardOrder.IF_MATCH)
class IfMatchGuard(
    private val records: Records,
    private val versions: Versions
) : RecordWriteGuard {
    override suspend fun beforeWrite(change: RecordWrite) {
        // las escrituras del propio sgspe dentro de la petición (un listener que actualiza un derivado del mismo registro)
        // no son la escritura que el cliente condicionó
        if (change.kind == RecordChangeKind.CREATED || change.recordId == null || WriteMark.present()) return
        val expectation =
            currentCoroutineContext()[ReactorContext]?.context?.getOrDefault<IfMatchExpectation>(IfMatchFilter.KEY, null) ?: return
        if (expectation.objectName != change.objectName || expectation.recordId != change.recordId) return
        val expected = IfMatch.parse(expectation.raw) ?: return
        val current = records.find(change.objectName, change.recordId!!)?.updatedAt
        versions.check(change.objectName, change.recordId!!, expected, current, "El registro")
    }
}
```

- [ ] **Paso 4: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.IfMatchTest'
```

Expected: `BUILD SUCCESSFUL`; 5 pruebas en verde.

- [ ] **Paso 5: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.common.ConcurrencyApiTest'
```

Expected: `BUILD SUCCESSFUL`; 2 pruebas en verde.

- [ ] **Paso 6: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/common/IfMatchTest.kt src/test/kotlin/sgspe/common/ConcurrencyApiTest.kt src/main/kotlin/sgspe/common/Preconditions.kt
git commit -m "$(cat <<'MSG'
feat(common): If-Match en los comandos y en la API genérica

IfMatch parsea y compara el updatedAt entre comillas (428 y 412). Un filtro lleva la cabecera de
la API genérica al contexto y una guarda la compara; un cambio posterior que solo toca derivados
o vínculos no es un conflicto (Versions lee el historial).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 5: Alcance por proyecto: asignaciones, ProjectScopeGuard y AssignmentRoleGuard

Opción A del checkpoint (`docs/security.md` §9.2): se lee todo el tenant y se escribe solo en los proyectos con
una `project_assignment` vigente (activa y dentro de sus fechas en America/Lima). `ProjectScope` dice qué objetos cuelgan de
un proyecto (directo o por su padre) y cuáles son catálogos del tenant; `ProjectScopeGuard` rechaza con 403
`NOT_ASSIGNED` la escritura sobre un proyecto ajeno, también al mover un registro de un proyecto a otro. El ADMIN y los
trabajos de fondo (sin usuario) quedan fuera, y dar de alta un proyecto no exige asignación.

`AssignmentRoleGuard` exige que `project_assignment.role_name` sea un rol declarado en `roles.json` (400).
`Assignments.recipients` resuelve los destinatarios de una transición por rol (`docs/workflows.md` «Destinatarios por
rol»): las personas asignadas al proyecto con un rol de esa transición, sin cuentas de servicio. En F2 nadie notifica
todavía: `NotificationListener` (F3) lo usará, y la prueba fija el contrato.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/scope/Assignments.kt`
- Crear: `src/main/kotlin/sgspe/scope/ProjectScope.kt`
- Crear: `src/main/kotlin/sgspe/scope/ProjectScopeGuard.kt`
- Crear: `src/main/kotlin/sgspe/scope/AssignmentRoleGuard.kt`
- Probar: `src/test/kotlin/sgspe/scope/AssignmentTest.kt`
- Probar: `src/test/kotlin/sgspe/scope/ProjectScopeApiTest.kt`
- Probar: `src/test/kotlin/sgspe/scope/RecipientsApiTest.kt`

**Interfaces:**
- Consume: `Records`, `Values`, `Callers`, `LIMA`, `Clock` (Tarea 2 y F1); `RuleGuard`, `Finding`, `GuardOrder`, `WriteView` (Tarea 3); `ModelCatalog.declaredRoles` y `transition` (Tarea 1).
- Produce:
  - `data class Assignment(id, project, memberUserId, memberUserEmail, roleName, active, startDate, endDate)` con
    `inForce(today)`; `data class Recipient(userId, email)`.
  - `Assignments`: `today()`, `ofProject(project)`, `projectsOf(userId)`, `isAssigned(userId, project)`,
    `recipients(project, objectName, transition, exclude): List<Recipient>`.
  - `object ProjectScope { ROOTS; CHILDREN; EXEMPT; fun covers(objectName) }`; `ProjectResolver.projectsOf(change, after)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/scope/AssignmentTest.kt` (3 pruebas):

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// la vigencia de una asignación (security.md §9.2): active y, si tiene fechas, hoy entre ellas
class AssignmentTest {
    private val project = UUID.randomUUID().toString()
    private val user = UUID.randomUUID().toString()

    private fun assignment(
        active: Any?,
        start: String? = null,
        end: String? = null
    ) = Assignment.of(
        UUID.randomUUID().toString(),
        mapOf(
            "project" to project,
            "member_user_id" to user,
            "member_user_email" to "a@sgspe.test",
            "role_name" to "FIELD_AGENT",
            "active" to active,
            "start_date" to start,
            "end_date" to end
        )
    )!!

    @Test
    fun `vigente con active y dentro de sus fechas, incluidos los extremos`() {
        val today = LocalDate.parse("2026-05-12")
        assertTrue(assignment(true).inForce(today))
        assertTrue(assignment(true, "2026-05-12", "2026-05-12").inForce(today))
        assertFalse(assignment(false).inForce(today))
        assertFalse(assignment(true, "2026-05-13").inForce(today))
        assertFalse(assignment(true, null, "2026-05-11").inForce(today))
    }

    @Test
    fun `una fila sin proyecto o sin usuario no es una asignacion`() {
        assertNull(Assignment.of(UUID.randomUUID().toString(), mapOf("member_user_id" to user)))
        assertEquals("FIELD_AGENT", assignment(true).roleName)
    }

    @Test
    fun `el alcance cubre raices, hijos y el proyecto, y exime las asignaciones`() {
        assertTrue(ProjectScope.covers("project"))
        assertTrue(ProjectScope.covers("engagement"))
        assertTrue(ProjectScope.covers("engagement_participant"))
        assertFalse(ProjectScope.covers("locality"))
        assertFalse(ProjectScope.covers("person"))
        assertEquals("engagement" to "engagement", ProjectScope.CHILDREN["engagement_question"])
        assertTrue("project_assignment" in ProjectScope.EXEMPT)
    }
}
```

Crear `src/test/kotlin/sgspe/scope/ProjectScopeApiTest.kt` (4 pruebas):

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Alcance, opción A» (RQ-SEC-006): sin asignación vigente no se escribe en el proyecto; con ella sí;
// ADMIN queda exento; los hijos se acotan por su raíz; cambiar de proyecto exige los dos
class ProjectScopeApiTest : DomainApiTest() {
    private fun createEngagement(
        project: String,
        token: String
    ) = exchange(
        "POST",
        "/api/objects/engagement/records",
        mapOf("attributes" to mapOf("project" to project, "engagement_type" to "REUNION", "title" to "Reunión")),
        token
    )

    @Test
    fun `sin asignacion es 403 y con una asignacion vigente se escribe`() {
        val project = projectWithCrs()
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val denied = createEngagement(project, outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assign(outsider, project, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.CREATED, createEngagement(project, outsider.token).status)
    }

    @Test
    fun `una asignacion inactiva o vencida no da alcance, y ADMIN no la necesita`() {
        val project = projectWithCrs()
        val inactive = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(inactive, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("active" to false))
        assertEquals(HttpStatus.FORBIDDEN, createEngagement(project, inactive.token).status)
        val expired = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(expired, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("start_date" to "2020-01-01", "end_date" to "2020-12-31"))
        assertEquals(HttpStatus.FORBIDDEN, createEngagement(project, expired.token).status)
        assertEquals(HttpStatus.CREATED, createEngagement(project, token).status)
    }

    @Test
    fun `un hijo se acota por su raiz y mover un registro de proyecto exige asignacion a los dos`() {
        val mine = projectWithCrs()
        val other = projectWithCrs()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", mine)
        val foreign = engagement(other)
        val participant =
            exchange(
                "POST",
                "/api/objects/engagement_participant/records",
                mapOf("attributes" to mapOf("engagement" to foreign, "represented_people_count" to 1)),
                officer.token
            )
        assertEquals(HttpStatus.FORBIDDEN, participant.status)
        val own = engagement(mine, token = officer.token)
        assertEquals(HttpStatus.FORBIDDEN, edit("engagement", own, mapOf("project" to other), officer.token).status)
        assign(officer, other, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.OK, edit("engagement", own, mapOf("project" to other), officer.token).status)
    }

    @Test
    fun `el alta de un proyecto y las asignaciones no piden asignacion previa`() {
        val admin = userWithRole("PROJECT_ADMIN")
        val project =
            tree(
                send(
                    "POST",
                    "/api/objects/project/records",
                    mapOf("attributes" to mapOf("code" to uniqueName("PRY-"), "name" to "Nuevo")),
                    HttpStatus.CREATED,
                    admin.token
                )
            )["id"].asString()
        val response =
            exchange(
                "POST",
                "/api/objects/project_assignment/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "project" to project,
                            "member_user_id" to admin.id,
                            "member_user_email" to admin.email,
                            "role_name" to "PROJECT_ADMIN",
                            "active" to true
                        )
                ),
                admin.token
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertEquals(HttpStatus.OK, transition("project", project, "submit_for_review", admin.token).status)
    }
}
```

Crear `src/test/kotlin/sgspe/scope/RecipientsApiTest.kt` (2 pruebas):

```kotlin
package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.Records
import java.util.UUID

// security.md §14 «Destinatarios por rol» (ASM-070): role_name es un rol declarado (AssignmentRoleGuard), y los
// destinatarios de una transición son las asignaciones vigentes con un role_name de esa transición, salvo quien la aplicó
class RecipientsApiTest : DomainApiTest() {
    @Autowired
    private lateinit var assignments: Assignments

    @Autowired
    private lateinit var records: Records

    @Test
    fun `un role_name que no es un rol declarado es un 400`() {
        val project = projectWithCrs()
        val user = userWithRole("ANALYST")
        for (role in listOf("JEFE DE CAMPO", "SUPER_ADMIN")) {
            val response =
                exchange(
                    "POST",
                    "/api/objects/project_assignment/records",
                    mapOf(
                        "attributes" to
                            mapOf("project" to project, "member_user_id" to user.id, "member_user_email" to user.email, "role_name" to role, "active" to true)
                    )
                )
            assertEquals(HttpStatus.BAD_REQUEST, response.status, role)
            assertEquals(listOf("role_name"), problemFields(response.body))
        }
    }

    @Test
    fun `los destinatarios salen de la definicion del workflow y de las asignaciones vigentes`() {
        val project = projectWithCrs()
        val approver = member("PROJECT_ADMIN", project)
        val actor = member("PROJECT_ADMIN", project)
        val manager = member("PROJECT_MANAGER", project)
        val inactive = userWithRole("PROJECT_ADMIN")
        assignWith(inactive, project, "PROJECT_ADMIN", mapOf("active" to false))
        val organization = UUID.fromString(me()["organizationId"].asString())
        val approve =
            runBlocking {
                records.asPlatform(organization) { assignments.recipients(UUID.fromString(project), "project", "approve", UUID.fromString(actor.id)) }
            }
        assertEquals(listOf(approver.email), approve.map { it.email })
        val submit =
            runBlocking {
                records.asPlatform(organization) { assignments.recipients(UUID.fromString(project), "project", "submit_for_review", null) }
            }
        assertEquals(setOf(approver.email, actor.email, manager.email), submit.map { it.email }.toSet())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.AssignmentTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'Assignment'`).

- [ ] **Paso 3: Crear `Assignments.kt`**

Crear `src/main/kotlin/sgspe/scope/Assignments.kt`:

```kotlin
package sgspe.scope

import org.springframework.stereotype.Component
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.Values
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// una fila de project_assignment, como la leen el alcance y los destinatarios (RF-PRJ-005, ASM-070)
data class Assignment(
    val id: UUID,
    val project: UUID,
    val memberUserId: UUID,
    val memberUserEmail: String,
    val roleName: String,
    val active: Boolean,
    val startDate: LocalDate?,
    val endDate: LocalDate?
) {
    // vigente: active y, si tiene fechas, hoy (en Lima) entre ellas (security.md §9.2, workflows.md «Destinatarios»)
    fun inForce(today: LocalDate): Boolean = active && (startDate == null || !today.isBefore(startDate)) && (endDate == null || !today.isAfter(endDate))

    companion object {
        fun of(
            id: String,
            attributes: Map<String, Any?>
        ): Assignment? {
            val project = Records.uuid(attributes["project"]) ?: return null
            val member = Records.uuid(attributes["member_user_id"]) ?: return null
            return Assignment(
                id = UUID.fromString(id),
                project = project,
                memberUserId = member,
                memberUserEmail = Values.text(attributes["member_user_email"]).orEmpty(),
                roleName = Values.text(attributes["role_name"]).orEmpty(),
                active = Values.bool(attributes["active"]) ?: false,
                startDate = Values.text(attributes["start_date"])?.let(LocalDate::parse),
                endDate = Values.text(attributes["end_date"])?.let(LocalDate::parse)
            )
        }
    }
}

// quien recibe un aviso dirigido a los roles de una transición: su usuario y su correo
data class Recipient(
    val userId: UUID,
    val email: String
)

// las asignaciones a proyectos: la fuente del alcance de escritura (ProjectScopeGuard) y de los destinatarios por rol
// (ASM-070). se leen con Records: como quien llama (que tiene READ sobre project_assignment, security.md §5.8) o como la
// plataforma en un trabajo de fondo
@Component
class Assignments(
    private val records: Records,
    private val catalog: ModelCatalog,
    private val clock: Clock
) {
    fun today(): LocalDate = LocalDate.now(clock.withZone(LIMA))

    suspend fun ofProject(project: UUID): List<Assignment> =
        records.all(OBJECT, filters = mapOf("project" to project.toString())).mapNotNull { Assignment.of(it.id, it.attributes) }

    // los proyectos con una asignación vigente del usuario: el filtro por defecto de los tableros (RF-PRJ-005)
    suspend fun projectsOf(userId: UUID): List<UUID> =
        records
            .all(OBJECT, filters = mapOf("member_user_id" to userId.toString()))
            .mapNotNull { Assignment.of(it.id, it.attributes) }
            .filter { it.inForce(today()) }
            .map { it.project }
            .distinct()

    // si el usuario tiene una asignación vigente al proyecto
    suspend fun isAssigned(
        userId: UUID,
        project: UUID
    ): Boolean =
        records
            .all(OBJECT, filters = mapOf("project" to project.toString(), "member_user_id" to userId.toString()))
            .mapNotNull { Assignment.of(it.id, it.attributes) }
            .any { it.inForce(today()) }

    // las asignaciones vigentes del proyecto cuyo role_name está entre los roles de la transición, salvo quien la aplicó
    // y las cuentas de servicio (su correo de respaldo, WAS-ADR-0043). los roles salen de model/workflows/<objeto>.json,
    // nunca de un literal (RQ-SEC-005). sin coincidencias, lista vacía: quien pidió el aviso lo registra (workflows.md)
    suspend fun recipients(
        project: UUID,
        objectName: String,
        transition: String,
        exclude: UUID?
    ): List<Recipient> {
        val roles =
            catalog
                .transition(objectName, transition)
                ?.roles
                ?.toSet()
                .orEmpty()
        if (roles.isEmpty()) return emptyList()
        val today = today()
        return ofProject(project)
            .filter { it.inForce(today) && it.roleName in roles && it.memberUserId != exclude }
            .filterNot { it.memberUserEmail.endsWith(SERVICE_ACCOUNT_DOMAIN) }
            .map { Recipient(it.memberUserId, it.memberUserEmail) }
            .distinctBy { it.userId }
    }

    companion object {
        const val OBJECT = "project_assignment"
        const val SERVICE_ACCOUNT_DOMAIN = "@service-accounts.invalid"
    }
}
```

- [ ] **Paso 4: Crear `ProjectScope.kt`**

Crear `src/main/kotlin/sgspe/scope/ProjectScope.kt`:

```kotlin
package sgspe.scope

import org.springframework.stereotype.Component
import sgspe.common.Records
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// a qué proyecto pertenece un registro (domain-model §4.7): las raíces llevan `project`; los hijos lo heredan de su raíz
// por su relación; `project` es su propio proyecto. los objetos que también pueden ser del tenant (export_log y, desde F3,
// evidence…) tienen `project` opcional y solo se acotan cuando lo traen. las fases siguientes agregan sus objetos aquí
object ProjectScope {
    val ROOTS: Set<String> =
        setOf(
            "project_party",
            "exploration_campaign",
            "area_of_influence",
            "project_component",
            "stakeholder",
            "prioritization_rule",
            "community_network",
            "engagement",
            "field_visit",
            "engagement_plan",
            "key_message",
            "aligned_answer",
            "strategic_alliance",
            "study",
            "key_theme",
            "export_log"
        )

    // hijo -> (campo RELATION, objeto padre)
    val CHILDREN: Map<String, Pair<String, String>> =
        mapOf(
            "area_of_influence_member" to ("area_of_influence" to "area_of_influence"),
            "stakeholder_assessment" to ("stakeholder" to "stakeholder"),
            "network_node" to ("community_network" to "community_network"),
            "engagement_participant" to ("engagement" to "engagement"),
            "engagement_question" to ("engagement" to "engagement"),
            "field_observation" to ("field_visit" to "field_visit"),
            "planned_activity" to ("engagement_plan" to "engagement_plan"),
            "study_instrument" to ("study" to "study"),
            "perception" to ("study" to "study"),
            "locality_profile" to ("study" to "study")
        )

    // quien escribe una asignación con permiso sobre ella forma los equipos: no se le pide asignación (security.md §9.2)
    val EXEMPT: Set<String> = setOf("project_assignment")

    fun covers(objectName: String): Boolean = objectName == "project" || objectName in ROOTS || objectName in CHILDREN
}

// los proyectos que toca una escritura: el de antes y el de después (cambiar el proyecto exige asignación a los dos)
@Component
class ProjectResolver(
    private val records: Records
) {
    suspend fun projectsOf(
        change: RecordWrite,
        after: Map<String, Any?>
    ): Set<UUID> {
        val name = change.objectName
        if (name == "project") {
            return if (change.kind == RecordChangeKind.CREATED) emptySet() else setOfNotNull(change.recordId)
        }
        if (name in ProjectScope.ROOTS) {
            return setOfNotNull(Records.uuid(change.before?.get("project")), Records.uuid(after["project"]))
        }
        val (field, parent) = ProjectScope.CHILDREN[name] ?: return emptySet()
        val parents = setOfNotNull(Records.uuid(change.before?.get(field)), Records.uuid(after[field]))
        return parents.mapNotNull { projectOfParent(parent, it) }.toSet()
    }

    private suspend fun projectOfParent(
        parent: String,
        id: UUID
    ): UUID? {
        val record = records.find(parent, id) ?: return null
        return if (parent == "project") id else Records.uuid(record.attributes["project"])
    }
}
```

- [ ] **Paso 5: Crear `ProjectScopeGuard.kt`**

Crear `src/main/kotlin/sgspe/scope/ProjectScopeGuard.kt`:

```kotlin
package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import sgspe.common.WriteView
import wasichai.core.data.RecordWrite

// la escritura acotada por asignación, opción A del checkpoint (security.md §9.2, RQ-SEC-006, CON-041): altas, cambios,
// bajas, transiciones y vínculos de los objetos de un proyecto solo para quien tiene una project_assignment vigente a ese
// proyecto. la lectura sigue siendo de todo el tenant hasta UPS-01. exentos: ADMIN (isAdmin), la plataforma y las
// automatizaciones (sin usuario), el alta de un project (aún sin equipo) y las asignaciones (forman el equipo). decide con
// datos, nunca con nombres de rol (RQ-SEC-005)
@Component
@Order(GuardOrder.SCOPE)
class ProjectScopeGuard(
    private val callers: Callers,
    private val view: WriteView,
    private val resolver: ProjectResolver,
    private val assignments: Assignments
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.userId == null || change.objectName in ProjectScope.EXEMPT || !ProjectScope.covers(change.objectName)) return emptyList()
        val user = callers.userOrNull() ?: return emptyList()
        if (user.isAdmin) return emptyList()
        val projects = resolver.projectsOf(change, view.after(change))
        val outside = projects.filterNot { assignments.isAssigned(user.userId, it) }
        return outside.map { project ->
            Finding(
                code = "NOT_ASSIGNED",
                message = "No tienes una asignación vigente al proyecto $project: solo su equipo escribe en él",
                field = "project",
                status = HttpStatus.FORBIDDEN
            )
        }
    }
}
```

- [ ] **Paso 6: Crear `AssignmentRoleGuard.kt`**

Crear `src/main/kotlin/sgspe/scope/AssignmentRoleGuard.kt`:

```kotlin
package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ModelCatalog
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// role_name de project_assignment nombra un rol declarado de model/roles.json, cargado como configuración (ASM-070): de
// él dependen los destinatarios por rol. ni ADMIN ni los dos mapeados a ADMIN son roles de proyecto
@Component
@Order(GuardOrder.ASSIGNMENT_ROLE)
class AssignmentRoleGuard(
    private val catalog: ModelCatalog,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Assignments.OBJECT) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val role = Values.text(view.after(change)["role_name"]) ?: return emptyList()
        if (role in catalog.declaredRoles) return emptyList()
        return listOf(
            Finding(
                code = "UNKNOWN_ROLE",
                message = "role_name debe ser uno de los roles declarados: ${catalog.declaredRoles.sorted().joinToString(", ")}",
                field = "role_name",
                status = HttpStatus.BAD_REQUEST
            )
        )
    }
}
```

- [ ] **Paso 7: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.AssignmentTest'
```

Expected: `BUILD SUCCESSFUL`; 3 pruebas en verde.

- [ ] **Paso 8: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.scope.ProjectScopeApiTest' --tests 'sgspe.scope.RecipientsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 6 pruebas en verde.

- [ ] **Paso 9: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/scope/AssignmentTest.kt src/test/kotlin/sgspe/scope/ProjectScopeApiTest.kt src/test/kotlin/sgspe/scope/RecipientsApiTest.kt src/main/kotlin/sgspe/scope/Assignments.kt src/main/kotlin/sgspe/scope/ProjectScope.kt src/main/kotlin/sgspe/scope/ProjectScopeGuard.kt src/main/kotlin/sgspe/scope/AssignmentRoleGuard.kt
git commit -m "$(cat <<'MSG'
feat(scope): alcance de escritura por asignación vigente (opción A)

ProjectScopeGuard acota la escritura a los proyectos con project_assignment vigente; el ADMIN y
los trabajos quedan fuera. AssignmentRoleGuard exige un rol declarado y Assignments resuelve los
destinatarios por rol de cada transición.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 6: WF-A y la consulta en seco de transiciones

`ProjectWriteGuard` cubre las condiciones de WF-A (`docs/workflows.md`): `approve` exige la acción declarada
`APPROVE` además del rol de la transición (doble llave, 403 `ACTION_REQUIRED`); `approve` y `resume` exigen `working_crs`
(409 `WORKING_CRS_REQUIRED`) y al menos una `project_party` (409 `PARTY_REQUIRED`) mientras la política del proyecto
`policy_activation_preconditions` siga activa. `ProjectParameters` da los parámetros y políticas del proyecto con los
valores por omisión de `docs/database-model.md`.

`POST /api/sgspe/transition-checks` (`docs/api.md` §4.13) responde si una transición pasaría, corriendo en seco todas las
reglas: `{ allowed, blocking[], warnings[] }`. Un estado que no es el `from` de la transición es `STATE_MISMATCH`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/common/ProjectParameters.kt`
- Crear: `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`
- Crear: `src/main/kotlin/sgspe/common/TransitionChecks.kt`
- Probar: `src/test/kotlin/sgspe/scope/ProjectWorkflowGuardsApiTest.kt`
- Probar: `src/test/kotlin/sgspe/common/TransitionChecksApiTest.kt`

**Interfaces:**
- Consume: `Records`, `Callers`, `Values`, `StrictBody` (Tarea 2); `WriteRule`, `RuleGuard`, `Finding`, `GuardOrder` (Tarea 3); `ModelCatalog.transition` (Tarea 1).
- Produce:
  - `object ProjectParameters { fun decimal(project, name): BigDecimal; fun integer(project, name): Long; fun policy(project, name): Boolean }`.
  - `POST /api/sgspe/transition-checks` → `TransitionCheck(allowed, blocking: List<CheckItem>, warnings: List<CheckItem>)`,
    `CheckItem(code, message, field)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/scope/ProjectWorkflowGuardsApiTest.kt` (6 pruebas):

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los casos de WF-A que dependen de las guardas de 2.2 (workflows.md §Pruebas, ProjectWorkflowApiTest): approve sin
// working_crs o sin parte CLIENTE u OPERADOR se rechaza; con la política apagada pasa; resume vuelve a verificar; sin la
// acción APPROVE se rechaza; cambiar stage sin motivo es 400; devolver y anular sin motivo es 400
class ProjectWorkflowGuardsApiTest : DomainApiTest() {
    private fun inReview(attributes: Map<String, Any?> = emptyMap()): String {
        val project = projectWithCrs(attributes)
        move("project", project, "submit_for_review")
        return project
    }

    private fun party(
        project: String,
        role: String
    ) = createRecord("project_party", mapOf("project" to project, "organization" to organization(), "party_role" to role))

    @Test
    fun `approve exige working_crs y una parte CLIENTE u OPERADOR`() {
        val project = inReview(mapOf("working_crs" to null))
        party(project, "CONSULTORA")
        val response = transition("project", project, "approve")
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals(listOf("working_crs"), problemFields(response.body))
        edit("project", project, mapOf("working_crs" to "EPSG 32717"))
        assertEquals(listOf("project_party"), problemFields(transition("project", project, "approve").body))
        party(project, "OPERADOR")
        move("project", project, "approve")
    }

    @Test
    fun `con policy_activation_preconditions en falso approve pasa sin parte`() {
        val project = inReview(mapOf("policy_activation_preconditions" to false))
        move("project", project, "approve")
    }

    @Test
    fun `resume vuelve a verificar las precondiciones`() {
        val project = inReview()
        party(project, "CLIENTE")
        move("project", project, "approve")
        move("project", project, "suspend", reason = "paralización por lluvias")
        edit("project", project, mapOf("working_crs" to null))
        assertEquals(HttpStatus.CONFLICT, transition("project", project, "resume").status)
    }

    @Test
    fun `la guarda exige APPROVE aparte del rol, y lo informa en seco`() {
        val project = inReview()
        party(project, "CLIENTE")
        val manager = member("PROJECT_MANAGER", project)
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "project", "recordId" to project, "transition" to "approve"),
                    HttpStatus.OK,
                    manager.token
                )
            )
        assertEquals(listOf("ACTION_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
        move("project", project, "approve", member("PROJECT_ADMIN", project).token)
    }

    @Test
    fun `cambiar stage sin motivo es 400 y con motivo pasa, y su primer valor no lo pide`() {
        val project = projectWithCrs()
        assertEquals(HttpStatus.OK, edit("project", project, mapOf("stage" to "EXPLORACION")).status)
        val response = edit("project", project, mapOf("stage" to "FACTIBILIDAD"))
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("reason"), problemFields(response.body))
        assertEquals(
            HttpStatus.OK,
            edit("project", project, mapOf("stage" to "FACTIBILIDAD"), headers = mapOf(REASON to reason("cambio de etapa aprobado"))).status
        )
    }

    @Test
    fun `devolver y anular sin motivo es 400`() {
        val project = inReview()
        assertEquals(listOf("reason"), problemFields(transition("project", project, "return_to_draft").body))
        move("project", project, "cancel_in_review", reason = "el cliente desistió")
    }
}
```

Crear `src/test/kotlin/sgspe/common/TransitionChecksApiTest.kt` (2 pruebas):

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// POST /api/sgspe/transition-checks (api.md §4.1): lo que bloquearía una transición y sus advertencias, sin escribir
class TransitionChecksApiTest : DomainApiTest() {
    private fun check(
        objectName: String,
        id: String,
        transition: String
    ) = tree(send("POST", "/api/sgspe/transition-checks", mapOf("object" to objectName, "recordId" to id, "transition" to transition), HttpStatus.OK))

    @Test
    fun `una transicion que pide motivo trae la advertencia, y una de comando se bloquea`() {
        val project = projectWithCrs()
        val visit = createRecord("field_visit", mapOf("project" to project, "title" to "Visita de prueba"))
        val cancel = check("field_visit", visit, "cancel")
        assertTrue(cancel["allowed"].asBoolean())
        assertEquals("REASON_REQUIRED", cancel["warnings"][0]["code"].asString())
        val stakeholder = stakeholder(project)
        val assess = check("stakeholder", stakeholder, "assess")
        assertEquals(setOf("COMMAND_ONLY", "STATE_MISMATCH"), assess["blocking"].values().map { it["code"].asString() }.toSet())
    }

    @Test
    fun `un cuerpo con una propiedad desconocida es 400 y un registro de otro es 404`() {
        val response =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf(
                    "object" to "engagement",
                    "recordId" to
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    "transition" to "close",
                    "extra" to 1
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("extra"), problemFields(response.body))
        val missing =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf(
                    "object" to "engagement",
                    "recordId" to
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                    "transition" to "close"
                )
            )
        assertEquals(HttpStatus.NOT_FOUND, missing.status)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.scope.ProjectWorkflowGuardsApiTest' --tests 'sgspe.common.TransitionChecksApiTest'
```

Expected: FAIL: `ProjectWorkflowGuardsApiTest` espera 409 y 403 que todavía son 200, y `TransitionChecksApiTest` recibe 404 en `/api/sgspe/transition-checks`.

- [ ] **Paso 3: Crear `ProjectParameters.kt`**

Crear `src/main/kotlin/sgspe/common/ProjectParameters.kt`:

```kotlin
package sgspe.common

import java.math.BigDecimal

// los parámetros y políticas de `project` con su valor por defecto (database-model.md §6, ASM-019). wasichai guarda
// defaultValue pero no lo aplica (UPS-15): un parámetro vacío vale lo que dice la ficha, y todo el código de sgspe lo lee
// aquí para que el tablero, la guarda y el endpoint usen el mismo valor
object ProjectParameters {
    private val DECIMALS = mapOf("interest_threshold" to BigDecimal("3"), "power_threshold" to BigDecimal("3"))
    private val INTEGERS =
        mapOf(
            "stakeholder_review_days" to 30L,
            "inactivity_days" to 90L,
            "meetings_per_month_target" to 2L
        )
    private val POLICIES =
        mapOf(
            "policy_minutes_only_formal_types" to true,
            "policy_activation_preconditions" to true
        )

    fun decimal(
        project: Map<String, Any?>,
        name: String
    ): BigDecimal = Values.decimal(project[name]) ?: DECIMALS.getValue(name)

    fun integer(
        project: Map<String, Any?>,
        name: String
    ): Long = Values.decimal(project[name])?.toLong() ?: INTEGERS.getValue(name)

    fun policy(
        project: Map<String, Any?>,
        name: String
    ): Boolean = Values.bool(project[name]) ?: POLICIES.getValue(name)
}
```

- [ ] **Paso 4: Crear `ProjectWriteGuard.kt`**

Crear `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`:

```kotlin
package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.CurrentUser

// las reglas de WF-A (workflows.md, RF-PRJ-003): llegar a `active` (approve, y resume desde suspended) exige el CRS de
// trabajo y al menos una project_party CLIENTE u OPERADOR, salvo que policy_activation_preconditions esté en falso
// (ASM-062); approve exige además la acción declarada APPROVE sobre project, la doble llave de RF-WFL-002
@Component
@Order(GuardOrder.DOMAIN)
class ProjectWriteGuard(
    private val records: Records,
    private val callers: Callers,
    private val currentUser: CurrentUser
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != "project" || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition !in TO_ACTIVE) return emptyList()
        val findings = mutableListOf<Finding>()
        if (transition == "approve" && !holdsApprove(change)) {
            findings += Finding("ACTION_REQUIRED", "Aprobar un proyecto exige la acción APPROVE sobre project", status = HttpStatus.FORBIDDEN)
        }
        val project = change.before.orEmpty()
        if (ProjectParameters.policy(project, "policy_activation_preconditions")) {
            if (Values.text(project["working_crs"]) == null) {
                findings += Finding("WORKING_CRS_REQUIRED", "El proyecto necesita su CRS de trabajo (working_crs) para pasar a activo", "working_crs")
            }
            if (!hasClientOrOperator(change)) {
                findings += Finding("PARTY_REQUIRED", "El proyecto necesita una parte CLIENTE u OPERADOR para pasar a activo", "project_party")
            }
        }
        return findings
    }

    private suspend fun holdsApprove(change: RecordWrite): Boolean {
        callers.userOrNull() ?: return true
        return try {
            currentUser.requirePermission(currentUser.require(), APPROVE, change.objectId)
            true
        } catch (_: ForbiddenException) {
            false
        }
    }

    private suspend fun hasClientOrOperator(change: RecordWrite): Boolean {
        val project = change.recordId ?: return false
        return records
            .all("project_party", filters = mapOf("project" to project.toString()))
            .any { Values.text(it.attributes["party_role"]) in ACTIVATING_ROLES }
    }

    companion object {
        const val APPROVE = "APPROVE"
        val TO_ACTIVE = setOf("approve", "resume")
        val ACTIVATING_ROLES = setOf("CLIENTE", "OPERADOR")
    }
}
```

- [ ] **Paso 5: Crear `TransitionChecks.kt`**

Crear `src/main/kotlin/sgspe/common/TransitionChecks.kt`:

```kotlin
package sgspe.common

import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import wasichai.core.common.NotFoundException
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
import java.util.UUID

data class CheckItem(
    val code: String,
    val message: String,
    val field: String?
)

data class TransitionCheck(
    val allowed: Boolean,
    val blocking: List<CheckItem>,
    val warnings: List<CheckItem>
)

// POST /api/sgspe/transition-checks (api.md §4.1): corre en seco las reglas de sgspe sobre una transición y devuelve lo
// que la bloquearía y las advertencias, sin escribir nada. la lista de transiciones de wasichai ya explica los roles y el
// permiso UPDATE; esto agrega lo que deciden las guardas (RN-ENG-005, las precondiciones de WF-A…). exige READ sobre el
// objeto: el registro se lee como quien llama
@RestController
@RequestMapping("/api/sgspe/transition-checks")
class TransitionChecksController(
    private val rules: List<WriteRule>,
    private val records: Records,
    private val metadata: MetadataService,
    private val catalog: ModelCatalog,
    private val callers: Callers
) {
    @PostMapping
    suspend fun check(
        @RequestBody body: Map<String, Any?>
    ): TransitionCheck {
        StrictBody.requireOnly(body, setOf("object", "recordId", "transition"))
        val objectName = Values.text(body["object"]) ?: throw ValidationException("Falta el objeto", "object", "es obligatorio")
        val recordId = Records.uuid(body["recordId"]) ?: throw ValidationException("Falta el registro", "recordId", "debe ser un UUID")
        val name = Values.text(body["transition"]) ?: throw ValidationException("Falta la transición", "transition", "es obligatoria")
        val user = callers.require()
        val record = records.get(objectName, recordId)
        val transition =
            catalog.transition(objectName, name)
                ?: throw NotFoundException("El workflow de $objectName no tiene la transición $name")
        val change =
            RecordWrite(
                organizationId = user.organizationId,
                userId = user.userId,
                objectId = metadata.loadDefinition(user.organizationId, objectName).obj.id,
                objectName = objectName,
                recordId = UUID.fromString(record.id),
                kind = RecordChangeKind.TRANSITIONED,
                before = record.attributes,
                transition = name
            )
        val findings = mutableListOf<Finding>()
        if (record.state != transition.from) {
            findings += Finding("STATE_MISMATCH", "La transición $name sale de ${transition.from}, pero el registro está en ${record.state ?: "ningún estado"}")
        }
        rules.forEach { findings += it.findings(change, dryRun = true) }
        val blocking = findings.filter { it.blocking }.map { CheckItem(it.code, it.message, it.field) }
        val warnings = findings.filterNot { it.blocking }.map { CheckItem(it.code, it.message, it.field) }
        return TransitionCheck(blocking.isEmpty(), blocking, warnings)
    }
}
```

- [ ] **Paso 6: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.scope.ProjectWorkflowGuardsApiTest' --tests 'sgspe.common.TransitionChecksApiTest'
```

Expected: `BUILD SUCCESSFUL`; 8 pruebas en verde.

- [ ] **Paso 7: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/scope/ProjectWorkflowGuardsApiTest.kt src/test/kotlin/sgspe/common/TransitionChecksApiTest.kt src/main/kotlin/sgspe/common/ProjectParameters.kt src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt src/main/kotlin/sgspe/common/TransitionChecks.kt
git commit -m "$(cat <<'MSG'
feat(scope): condiciones de WF-A y consulta en seco de transiciones

approve exige APPROVE además del rol; approve y resume exigen working_crs y una parte del proyecto
mientras la política siga activa. POST /api/sgspe/transition-checks corre todas las reglas en seco.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 7: WF-C y WF-L: EngagementWriteGuard

Las condiciones de WF-C (`docs/workflows.md`): `schedule` y `reschedule` exigen `scheduled_at`;
`register_held` y `register_held_unplanned` exigen `held_at` y al menos un asistente con `attended`; `draft_minutes` y
`submit_minutes` exigen el acta; `close` y `close_light` no dejan preguntas sin responder (`PENDING_QUESTIONS`, salvo
`answered_on_site` o una respuesta registrada); `close_light` solo sirve a los tipos sin acta formal cuando la política
`policy_minutes_only_formal_types` está activa; cancelar exige `cancel_reason`. En la lista de asistentes,
`represented_people_count` es al menos 1 (400). WF-L: cancelar una visita pide motivo (lo da `TransitionReasonGuard`).

Los casos de `EngagementWorkflowApiTest` y `FieldVisitWorkflowApiTest` (Fase 2.1) que dependen de estas guardas viven en
`EngagementWorkflowGuardsApiTest` y `FieldVisitWorkflowGuardsApiTest`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt`
- Probar: `src/test/kotlin/sgspe/engagement/EngagementWorkflowGuardsApiTest.kt`
- Probar: `src/test/kotlin/sgspe/engagement/FieldVisitWorkflowGuardsApiTest.kt`

**Interfaces:**
- Consume: `Records`, `Values` (Tarea 2); `RuleGuard`, `Finding`, `GuardOrder`, `WriteView`, `isLink` (Tarea 3); `ProjectParameters.policy` (Tarea 6).
- Produce: `EngagementWriteGuard` (RecordWriteGuard y WriteRule), sin API propia.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/engagement/EngagementWorkflowGuardsApiTest.kt` (5 pruebas):

```kotlin
package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los casos de WF-C que dependen de EngagementWriteGuard y TransitionReasonGuard (workflows.md §Pruebas,
// EngagementWorkflowApiTest)
class EngagementWorkflowGuardsApiTest : DomainApiTest() {
    @Test
    fun `programar exige scheduled_at y realizar exige held_at y un asistente`() {
        val project = projectWithCrs()
        val engagement = engagement(project)
        assertEquals(listOf("scheduled_at"), problemFields(transition("engagement", engagement, "schedule").body))
        edit("engagement", engagement, mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(engagement, attributes = mapOf("attended" to false))
        val held = transition("engagement", engagement, "register_held_unplanned")
        assertEquals(HttpStatus.CONFLICT, held.status)
        assertEquals(listOf("attended"), problemFields(held.body))
        participant(engagement)
        move("engagement", engagement, "register_held_unplanned")
    }

    @Test
    fun `una REUNION no cierra sin acta con la politica activa, y un contacto si`() {
        val project = projectWithCrs()
        val meeting = engagement(project, "REUNION INFORMATIVA", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(meeting)
        move("engagement", meeting, "register_held_unplanned")
        assertEquals(listOf("engagement_type"), problemFields(transition("engagement", meeting, "close_light").body))
        val contact = engagement(project, "CONTACTO CARA A CARA", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(contact)
        move("engagement", contact, "register_held_unplanned")
        move("engagement", contact, "close_light")
    }

    @Test
    fun `cerrar con una pregunta pendiente se rechaza y respondida pasa`() {
        val project = projectWithCrs()
        val meeting = engagement(project, "REUNION", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z", "minutes" to "Se presentó el proyecto."))
        participant(meeting)
        val question = createRecord("engagement_question", mapOf("engagement" to meeting, "question_text" to "¿Habrá empleo local?"))
        for (step in listOf("register_held_unplanned", "draft_minutes", "submit_minutes", "approve_minutes")) move("engagement", meeting, step)
        val dryRun =
            tree(send("POST", "/api/sgspe/transition-checks", mapOf("object" to "engagement", "recordId" to meeting, "transition" to "close"), HttpStatus.OK))
        assertEquals(listOf("PENDING_QUESTIONS"), dryRun["blocking"].values().map { it["code"].asString() })
        assertEquals(listOf("engagement_question"), problemFields(transition("engagement", meeting, "close").body))
        edit("engagement_question", question, mapOf("answer" to "Sí, según la política del proyecto", "answered_at" to "2026-05-13T15:00:00Z"))
        move("engagement", meeting, "close")
    }

    @Test
    fun `cancelar exige cancel_reason y el motivo de la transicion`() {
        val project = projectWithCrs()
        val engagement = engagement(project)
        assertEquals(listOf("reason"), problemFields(transition("engagement", engagement, "cancel_planned").body))
        assertEquals(listOf("cancel_reason"), problemFields(transition("engagement", engagement, "cancel_planned", reason = "lluvia").body))
        edit("engagement", engagement, mapOf("cancel_reason" to "Lluvias intensas"))
        move("engagement", engagement, "cancel_planned", reason = "lluvia")
    }

    @Test
    fun `represented_people_count menor que 1 es 400`() {
        val engagement = engagement(projectWithCrs())
        val response =
            exchange(
                "POST",
                "/api/objects/engagement_participant/records",
                mapOf("attributes" to mapOf("engagement" to engagement, "represented_people_count" to 0))
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("represented_people_count"), problemFields(response.body))
    }
}
```

Crear `src/test/kotlin/sgspe/engagement/FieldVisitWorkflowGuardsApiTest.kt` (1 pruebas):

```kotlin
package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// el caso de WF-L que depende de TransitionReasonGuard (FieldVisitWorkflowApiTest): cancelar sin motivo es 400. el de
// FIELD_AGENT, que solo mueve sus visitas, está en EditAnyApiTest
class FieldVisitWorkflowGuardsApiTest : DomainApiTest() {
    @Test
    fun `cancelar sin motivo es 400`() {
        val visit = createRecord("field_visit", mapOf("project" to projectWithCrs(), "title" to "Visita"))
        assertEquals(listOf("reason"), problemFields(transition("field_visit", visit, "cancel").body))
        move("field_visit", visit, "cancel", reason = "huaico en la vía")
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.engagement.EngagementWorkflowGuardsApiTest' --tests 'sgspe.engagement.FieldVisitWorkflowGuardsApiTest'
```

Expected: FAIL: las transiciones que deben ser 409 o 400 todavía responden 200.

- [ ] **Paso 3: Crear `EngagementWriteGuard.kt`**

Crear `src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt`:

```kotlin
package sgspe.engagement

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las guardas de WF-C y de la lista de asistentes (workflows.md, domain-model §7.2): fecha programada para programar,
// realización con al menos un asistente, minuta para el acta, motivo de cancelación, cierre sin preguntas pendientes
// (RN-ENG-005) y cierre sin acta solo para los tipos que no la exigen (RN-ENG-004, ASM-033). desde F4, una pregunta
// derivada a una QCS también cuenta como atendida; ese paso lo agrega la derivación
@Component
@Order(GuardOrder.DOMAIN)
class EngagementWriteGuard(
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when {
            change.objectName == PARTICIPANT -> participant(change)
            change.objectName == ENGAGEMENT && change.kind == RecordChangeKind.TRANSITIONED -> transition(change)
            else -> emptyList()
        }

    private suspend fun participant(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val count = Values.decimal(view.after(change)["represented_people_count"])
        if (count != null && count.toLong() >= 1) return emptyList()
        return listOf(
            Finding(
                "REPRESENTED_PEOPLE",
                "represented_people_count es al menos 1: una fila representa a una persona o a un grupo",
                "represented_people_count",
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    private suspend fun transition(change: RecordWrite): List<Finding> {
        val engagement = change.before.orEmpty()
        return when (change.transition) {
            "schedule", "reschedule" -> required(engagement, "scheduled_at", "Programar exige la fecha y hora (scheduled_at)")
            "register_held", "register_held_unplanned" ->
                required(engagement, "held_at", "Registrar la realización exige la fecha y hora en que ocurrió (held_at)") + attendance(change)
            "draft_minutes", "submit_minutes" -> required(engagement, "minutes", "El acta necesita la minuta (minutes)")
            "close" -> pendingQuestions(change)
            "close_light" -> lightClose(change, engagement) + pendingQuestions(change)
            "cancel_planned", "cancel_scheduled", "cancel_postponed" ->
                required(engagement, "cancel_reason", "Cancelar exige el motivo de la cancelación (cancel_reason)")
            else -> emptyList()
        }
    }

    private fun required(
        engagement: Map<String, Any?>,
        field: String,
        message: String
    ): List<Finding> = if (Values.text(engagement[field]) == null) listOf(Finding("FIELD_REQUIRED", message, field)) else emptyList()

    private suspend fun attendance(change: RecordWrite): List<Finding> {
        val attended = records.count(PARTICIPANT, filters = mapOf("engagement" to change.recordId.toString(), "attended" to "true"))
        if (attended > 0) return emptyList()
        return listOf(Finding("ATTENDANCE_REQUIRED", "Registrar la realización exige al menos un participante que asistió (attended)", "attended"))
    }

    private suspend fun pendingQuestions(change: RecordWrite): List<Finding> {
        val pending =
            records
                .all(QUESTION, filters = mapOf("engagement" to change.recordId.toString()))
                .count { !answered(it.attributes) }
        if (pending == 0) return emptyList()
        return listOf(Finding("PENDING_QUESTIONS", "Quedan $pending preguntas sin respuesta: respóndalas antes de cerrar (RN-ENG-005)", "engagement_question"))
    }

    private suspend fun lightClose(
        change: RecordWrite,
        engagement: Map<String, Any?>
    ): List<Finding> {
        val type = Values.text(engagement["engagement_type"])
        val project = Records.uuid(engagement["project"])?.let { records.find("project", it)?.attributes }.orEmpty()
        val onlyFormal = ProjectParameters.policy(project, "policy_minutes_only_formal_types")
        if (onlyFormal && type !in FORMAL_TYPES) return emptyList()
        val why = if (onlyFormal) "una interacción de tipo $type cierra con acta aprobada" else "el proyecto exige acta en todas las interacciones"
        return listOf(Finding("MINUTES_REQUIRED", "No se puede cerrar sin acta: $why (RN-ENG-004)", "engagement_type"))
    }

    companion object {
        const val ENGAGEMENT = "engagement"
        const val PARTICIPANT = "engagement_participant"
        const val QUESTION = "engagement_question"

        // los tipos formales de RN-ENG-004
        val FORMAL_TYPES = setOf("REUNION", "REUNION INFORMATIVA", "TALLER", "ASAMBLEA", "MESA DE DIALOGO")

        // atendida: respondida en el lugar o con su respuesta posterior (answered_at)
        fun answered(question: Map<String, Any?>): Boolean = Values.bool(question["answered_on_site"]) == true || Values.text(question["answered_at"]) != null
    }
}
```

- [ ] **Paso 4: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.engagement.EngagementWorkflowGuardsApiTest' --tests 'sgspe.engagement.FieldVisitWorkflowGuardsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 6 pruebas en verde.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/engagement/EngagementWorkflowGuardsApiTest.kt src/test/kotlin/sgspe/engagement/FieldVisitWorkflowGuardsApiTest.kt src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt
git commit -m "$(cat <<'MSG'
feat(engagement): condiciones de WF-C y de la lista de asistentes

Programar, registrar la realización, el acta, cerrar sin preguntas pendientes, el cierre ligero
según la política del proyecto y cancelar con motivo. represented_people_count es al menos 1.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 8: Conteos de asistentes y DerivedFieldsJob (JOB-02)

Los siete `attendees_*_count` de `engagement` son derivados de su lista de asistentes (`docs/domain-model.md`
§4.6): `AttendanceCountsListener` los recalcula después de cada alta, cambio o baja de un participante, con el motivo
`derivado: conteos de asistentes` y la marca. Un listener que falla no deshace la escritura del participante, así que
`DerivedFieldsJob` (JOB-02, `docs/workflows.md`) corre cada hora sobre lo cambiado desde la corrida anterior y cada
madrugada sobre todo, con candado de clúster: cada `DerivedRecalculator` (los conteos aquí, el área en la Tarea 15) corrige
lo que quedó mal. `Organizations` es el único lugar que enumera las organizaciones (UPS-17, vigilado por
`ArchitectureTest`).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/common/Organizations.kt`
- Crear: `src/main/kotlin/sgspe/common/DerivedFieldsJob.kt`
- Crear: `src/main/kotlin/sgspe/engagement/AttendanceCounts.kt`
- Probar: `src/test/kotlin/sgspe/engagement/AttendanceCountsTest.kt`
- Probar: `src/test/kotlin/sgspe/engagement/AttendanceCountsApiTest.kt`

**Interfaces:**
- Consume: `Records`, `Criteria.updatedSince`, `Values` (Tarea 2); `ClusterLock` y `CustomObjectRepository.findAllOrganizations` de wasichai.
- Produce:
  - `interface DerivedRecalculator { val sourceObject: String; suspend fun recalculate(organizationId: UUID, since: Instant?): Int }`.
  - `DerivedFieldsJob.runOnce(full: Boolean): Int?` (null si otro nodo tiene el candado); `DerivedFieldsSchedule` con
    `sgspe.jobs.enabled`, `sgspe.jobs.derived-fields.hourly-cron` (`0 15 * * * *`) y `daily-cron` (`0 45 3 * * *`).
  - `Organizations.having(objectName): List<UUID>`.
  - `AttendanceCounts.of(participants)`, `AttendanceCountsWriter.refresh(engagement)`, `AttendanceCountsListener`,
    `AttendanceCountsRecalculator`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/engagement/AttendanceCountsTest.kt` (3 pruebas):

```kotlin
package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// RF-ENG-004 y RQ-DSH-012: suma de represented_people_count de los que asistieron, por sexo y por ámbito
class AttendanceCountsTest {
    @Test
    fun `suma a los que asistieron, una fila de grupo cuenta por las personas que representa`() {
        val counts =
            AttendanceCounts.of(
                listOf(
                    mapOf("attended" to true, "represented_people_count" to 1, "sex" to "FEMENINO", "participant_scope" to "AIDS"),
                    mapOf("attended" to true, "represented_people_count" to 12, "sex" to null, "participant_scope" to "AIIS"),
                    mapOf("attended" to true, "represented_people_count" to 1L, "sex" to "MASCULINO", "participant_scope" to "REGIONAL"),
                    mapOf("attended" to false, "represented_people_count" to 5, "sex" to "FEMENINO", "participant_scope" to "AIDS"),
                    mapOf("attended" to null, "represented_people_count" to 3)
                )
            )
        assertEquals(14L, counts["attendees_total_count"])
        assertEquals(1L, counts["attendees_female_count"])
        assertEquals(1L, counts["attendees_male_count"])
        assertEquals(1L, counts["attendees_direct_area_count"])
        assertEquals(12L, counts["attendees_indirect_area_count"])
        assertEquals(1L, counts["attendees_regional_count"])
        assertEquals(0L, counts["attendees_national_count"])
    }

    @Test
    fun `solo se escriben los conteos que cambian`() {
        val counts = AttendanceCounts.of(emptyList())
        assertEquals(AttendanceCounts.FIELDS.toSet(), AttendanceCounts.changes(emptyMap(), counts).keys)
        assertTrue(AttendanceCounts.changes(AttendanceCounts.FIELDS.associateWith { 0L }, counts).isEmpty())
    }

    @Test
    fun `una pregunta esta atendida si se respondio en el lugar o despues`() {
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_on_site" to true)))
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_on_site" to false, "answered_at" to "2026-05-13T15:00:00Z")))
        assertFalse(EngagementWriteGuard.answered(mapOf("answered_on_site" to false, "answer" to "lo vemos")))
    }
}
```

Crear `src/test/kotlin/sgspe/engagement/AttendanceCountsApiTest.kt` (3 pruebas):

```kotlin
package sgspe.engagement

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.DerivedFieldsJob
import sgspe.common.SgspeWrite
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordService
import java.util.UUID

// AttendanceCountsListener y su recalculador de JOB-02 (workflows.md §Pruebas «Listeners» y «Trabajos»): alta, cambio y
// baja de participantes mueven los conteos; un conteo que quedó mal lo corrige DerivedFieldsJob, con su motivo; un PUT
// que no trae los conteos, como los del portal, los conserva (DerivedFieldsKeeper)
class AttendanceCountsApiTest : DomainApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    @Autowired
    private lateinit var recordService: RecordService

    @Test
    fun `alta, cambio y baja de participantes mueven los conteos`() {
        val engagement = engagement(projectWithCrs())
        val first = participant(engagement, attributes = mapOf("represented_people_count" to 3, "sex" to "MASCULINO"))
        participant(engagement, attributes = mapOf("represented_people_count" to 2, "sex" to "FEMENINO"))
        assertEquals(5, record("engagement", engagement)["attributes"]["attendees_total_count"].asInt())
        edit("engagement_participant", first, mapOf("attended" to false))
        assertEquals(2, record("engagement", engagement)["attributes"]["attendees_total_count"].asInt())
        send("DELETE", "/api/objects/engagement_participant/records/$first", null, HttpStatus.NO_CONTENT)
        assertEquals(2, record("engagement", engagement)["attributes"]["attendees_female_count"].asInt())
    }

    @Test
    fun `DerivedFieldsJob corrige un conteo pendiente`() {
        val engagement = engagement(projectWithCrs())
        participant(engagement, attributes = mapOf("represented_people_count" to 4))
        val organization = UUID.fromString(me()["organizationId"].asString())
        runBlocking {
            recordService.asPlatform(organization) {
                val stored = recordService.get("engagement", UUID.fromString(engagement)).attributes
                withContext(SgspeWrite) {
                    recordService.update(
                        "engagement",
                        UUID.fromString(engagement),
                        RecordRequest(stored + ("attendees_total_count" to 99)),
                        "prueba: conteo dañado"
                    )
                }
            }
            job.runOnce(full = true)
        }
        assertEquals(4, record("engagement", engagement)["attributes"]["attendees_total_count"].asInt())
        val history = tree(send("GET", "/api/objects/engagement/records/$engagement/history", null, HttpStatus.OK))
        assertEquals("derivado: conteos de asistentes", history.values().first()["reason"].asString())
    }

    @Test
    fun `un PUT que omite los derivados, como el portal, los conserva`() {
        val engagement = engagement(projectWithCrs())
        participant(engagement, attributes = mapOf("represented_people_count" to 7))
        val current = record("engagement", engagement)["attributes"]
        assertEquals(7, current["attendees_total_count"].asInt())
        val derived = setOf("attendees_total_count", "attendees_female_count", "attendees_male_count")
        val attributes = current.properties().associate { (k, v) -> k to json(v) }.filterKeys { it !in derived } + ("title" to "Reunión renombrada")
        val response = exchange("PUT", "/api/objects/engagement/records/$engagement", mapOf("attributes" to attributes))
        assertEquals(HttpStatus.OK, response.status, response.body)
        val stored = record("engagement", engagement)["attributes"]
        assertEquals("Reunión renombrada", stored["title"].asString())
        assertEquals(7, stored["attendees_total_count"].asInt())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.engagement.AttendanceCountsTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'AttendanceCounts'`).

- [ ] **Paso 3: Crear `Organizations.kt`**

Crear `src/main/kotlin/sgspe/common/Organizations.kt`:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.metadata.CustomObjectRepository
import java.util.UUID

// las organizaciones que tienen un objeto, para los trabajos de fondo. wasichai no tiene una forma soportada de listar
// los tenants (UPS-17): como caja (BuzonStore), se usa findAllOrganizations, la lectura de la plataforma sin tenant que
// wasichai reserva para su trabajo de arranque. es una excepción vigilada a RNF-MNT-005 (architecture.md §8.1), aislada
// aquí: ArchitectureTest rechaza ese nombre en cualquier otro archivo. nunca se llama desde una petición
@Component
class Organizations(
    private val objects: CustomObjectRepository
) {
    suspend fun having(objectName: String): List<UUID> =
        objects
            .findAllOrganizations()
            .filter { it.name == objectName }
            .map { it.organizationId }
            .distinct()
}
```

- [ ] **Paso 4: Crear `DerivedFieldsJob.kt`**

Crear `src/main/kotlin/sgspe/common/DerivedFieldsJob.kt`:

```kotlin
package sgspe.common

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.Instant
import java.util.UUID

// un recalculador de campos derivados de un paquete (domain-model §7.4): calcula desde cero a partir de sus fuentes,
// nunca por incremento, y escribe solo si el valor cambió, con la marca y el motivo «derivado: <regla>». corre dentro de
// RecordService.asPlatform de una organización. since: los registros fuente cambiados desde ese instante; null, todos
interface DerivedRecalculator {
    // el objeto cuya presencia dice que la organización tiene el modelo que este recalculador necesita
    val sourceObject: String

    suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int
}

// JOB-02 (workflows.md): corrige los derivados que un listener no pudo escribir. cada hora sobre lo cambiado desde la
// vuelta anterior y una vez al día sobre todo. una sola réplica por vuelta (ClusterLock.tryLock); una organización que
// falla no detiene a las demás. nunca aplica transiciones ni vincula registros (RN-WFL-001)
@Component
class DerivedFieldsJob(
    private val recalculators: List<DerivedRecalculator>,
    private val organizations: Organizations,
    private val records: Records,
    private val locks: ClusterLock,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var lastRun: Instant? = null

    // los registros escritos; null si otra réplica tenía el candado
    suspend fun runOnce(full: Boolean): Int? {
        val started = clock.instant()
        val since = if (full) null else lastRun
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use {
            var written = 0
            for (recalculator in recalculators) {
                for (organization in organizations.having(recalculator.sourceObject)) {
                    try {
                        written += records.asPlatform(organization) { recalculator.recalculate(organization, since) }
                    } catch (e: Exception) {
                        log.error("DerivedFieldsJob: {} falló en la organización {}: {}", recalculator.javaClass.simpleName, organization, e.message)
                    }
                }
            }
            lastRun = started
            written
        }
    }

    companion object {
        const val LOCK = "sgspe.derived-fields"
    }
}

// los relojes de los trabajos, en la zona de Lima. sgspe.jobs.enabled=false los apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class DerivedFieldsSchedule(
    private val job: DerivedFieldsJob
) {
    @Scheduled(cron = "\${sgspe.jobs.derived-fields.hourly-cron:0 15 * * * *}", zone = "America/Lima")
    suspend fun hourly() {
        job.runOnce(full = false)
    }

    @Scheduled(cron = "\${sgspe.jobs.derived-fields.daily-cron:0 45 3 * * *}", zone = "America/Lima")
    suspend fun daily() {
        job.runOnce(full = true)
    }
}
```

- [ ] **Paso 5: Crear `AttendanceCounts.kt`**

Crear `src/main/kotlin/sgspe/engagement/AttendanceCounts.kt`:

```kotlin
package sgspe.engagement

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Instant
import java.util.UUID

// los conteos de asistentes de una interacción (RF-ENG-004, RQ-DSH-012): suma de represented_people_count de los
// participantes con attended, en total, por sexo y por ámbito, calculada desde cero (domain-model §7.4)
object AttendanceCounts {
    val FIELDS =
        listOf(
            "attendees_total_count",
            "attendees_female_count",
            "attendees_male_count",
            "attendees_direct_area_count",
            "attendees_indirect_area_count",
            "attendees_regional_count",
            "attendees_national_count"
        )

    fun of(participants: List<Map<String, Any?>>): Map<String, Long> {
        val attended = participants.filter { Values.bool(it["attended"]) == true }

        fun sum(predicate: (Map<String, Any?>) -> Boolean): Long =
            attended.filter(predicate).sumOf {
                (
                    Values.decimal(it["represented_people_count"])?.toLong()
                        ?: 1L
                ).coerceAtLeast(1L)
            }

        fun scope(value: String): (Map<String, Any?>) -> Boolean = { Values.text(it["participant_scope"]) == value }
        return linkedMapOf(
            "attendees_total_count" to sum { true },
            "attendees_female_count" to sum { Values.text(it["sex"]) == "FEMENINO" },
            "attendees_male_count" to sum { Values.text(it["sex"]) == "MASCULINO" },
            "attendees_direct_area_count" to sum(scope("AIDS")),
            "attendees_indirect_area_count" to sum(scope("AIIS")),
            "attendees_regional_count" to sum(scope("REGIONAL")),
            "attendees_national_count" to sum(scope("NACIONAL"))
        )
    }

    // los conteos que cambian, para escribir solo eso
    fun changes(
        engagement: Map<String, Any?>,
        counts: Map<String, Long>
    ): Map<String, Any?> = counts.filter { (field, value) -> !Values.same(engagement[field], value) }
}

// recalcula y escribe los conteos de una interacción con Records (la marca SgspeWrite y el motivo «derivado: conteos de
// asistentes»), como quien escribió o como la plataforma
@Component
class AttendanceCountsWriter(
    private val records: Records
) {
    suspend fun refresh(engagement: UUID): Boolean {
        val current = records.find(EngagementWriteGuard.ENGAGEMENT, engagement) ?: return false
        val participants = records.all(EngagementWriteGuard.PARTICIPANT, filters = mapOf("engagement" to engagement.toString()))
        val changes = AttendanceCounts.changes(current.attributes, AttendanceCounts.of(participants.map { it.attributes }))
        if (changes.isEmpty()) return false
        records.patch(EngagementWriteGuard.ENGAGEMENT, engagement, changes, REASON)
        return true
    }

    companion object {
        const val REASON = "derivado: conteos de asistentes"
    }
}

// AttendanceCountsListener (domain-model §7.5): al crear, cambiar o borrar un participante, recalcula los conteos de su
// interacción como quien escribió. si no puede, registra el error y no hace fallar la escritura: DerivedFieldsJob corrige
// el valor en la hora siguiente. su escritura lleva la marca, así que EditAnyGuard la deja pasar en una interacción ajena
// (ASM-069)
@Component
@Order(100)
class AttendanceCountsListener(
    private val writer: AttendanceCountsWriter
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != EngagementWriteGuard.PARTICIPANT || change.kind == RecordChangeKind.TRANSITIONED) return
        val engagements = setOfNotNull(Records.uuid(change.before?.get("engagement")), Records.uuid(change.after?.get("engagement")))
        for (engagement in engagements) {
            try {
                writer.refresh(engagement)
            } catch (e: Exception) {
                log.warn("AttendanceCountsListener: no se actualizaron los conteos de la interacción {}: {}", engagement, e.message)
            }
        }
    }
}

// el recalculador de JOB-02 para los conteos: las interacciones cuyos participantes cambiaron desde la vuelta anterior, o
// todas en la vuelta diaria (un participante borrado solo lo ve la vuelta diaria)
@Component
class AttendanceCountsRecalculator(
    private val records: Records,
    private val writer: AttendanceCountsWriter
) : DerivedRecalculator {
    override val sourceObject: String = EngagementWriteGuard.ENGAGEMENT

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val engagements =
            if (since == null) {
                records.all(EngagementWriteGuard.ENGAGEMENT).map { UUID.fromString(it.id) }
            } else {
                records
                    .all(EngagementWriteGuard.PARTICIPANT, criteria = listOf(Criteria.updatedSince(since)))
                    .mapNotNull { Records.uuid(it.attributes["engagement"]) }
                    .distinct()
            }
        return engagements.count { writer.refresh(it) }
    }
}
```

- [ ] **Paso 6: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.engagement.AttendanceCountsTest'
```

Expected: `BUILD SUCCESSFUL`; 3 pruebas en verde.

- [ ] **Paso 7: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.engagement.AttendanceCountsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 3 pruebas en verde.

- [ ] **Paso 8: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/engagement/AttendanceCountsTest.kt src/test/kotlin/sgspe/engagement/AttendanceCountsApiTest.kt src/main/kotlin/sgspe/common/Organizations.kt src/main/kotlin/sgspe/common/DerivedFieldsJob.kt src/main/kotlin/sgspe/engagement/AttendanceCounts.kt
git commit -m "$(cat <<'MSG'
feat(engagement): conteos de asistentes derivados y JOB-02

AttendanceCountsListener recalcula los conteos tras cada cambio de la lista de asistentes.
DerivedFieldsJob corre cada hora sobre lo cambiado y cada madrugada sobre todo, con candado.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 9: Edición de lo propio: EditAnyGuard

`docs/security.md` §8 y ASM-069: en los objetos que declaran `EDIT_ANY` (`engagement`, `engagement_participant`,
`field_visit` y `field_observation`), quien no tiene esa acción solo cambia, borra o mueve los registros que creó (403
`NOT_OWN_RECORD`); crear y leer no cambian. La autoría es la columna de sistema `created_by` de wasichai. Una escritura con
la marca que solo toca derivados (los conteos de la Tarea 8) no es una edición de la persona. Los casos de `FIELD_AGENT` de
WF-C y WF-L van aquí, y también la secuencia con la que el portal registra una interacción (Fase 2.3, S24): alta,
vínculos, participantes, preguntas y la transición con el `If-Match` del alta, todo por la API genérica y con todas las
guardas y listeners activos (`EngagementRegistrationApiTest`).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/scope/EditAnyGuard.kt`
- Probar: `src/test/kotlin/sgspe/scope/EditAnyApiTest.kt`
- Probar: `src/test/kotlin/sgspe/engagement/EngagementRegistrationApiTest.kt`

**Interfaces:**
- Consume: `ModelCatalog.objectsDeclaring`, `derivedFields` (Tarea 1); `Records.count`, `Callers`, `WriteMark` (Tarea 2); `RuleGuard`, `WriteView` (Tarea 3).
- Produce: `EditAnyGuard`, sin API propia.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/scope/EditAnyApiTest.kt` (5 pruebas):

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Edición de lo propio» (RQ-SEC-008, ASM-069): sin EDIT_ANY no se cambia, transiciona, vincula ni borra
// lo ajeno; con EDIT_ANY sí; FIELD_AGENT agrega un participante a una interacción ajena y sus conteos se actualizan, y
// vincula su captura a un grupo de interés que registró otra persona
class EditAnyApiTest : DomainApiTest() {
    @Test
    fun `sin EDIT_ANY lo ajeno no se cambia, mueve, vincula ni borra, y lo propio si`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = engagement(project, token = officer.token, attributes = mapOf("scheduled_at" to "2026-06-01T15:00:00Z"))
        assertEquals(HttpStatus.FORBIDDEN, edit("engagement", foreign, mapOf("place" to "Local comunal"), agent.token).status)
        assertEquals(HttpStatus.FORBIDDEN, transition("engagement", foreign, "schedule", agent.token).status)
        assertEquals(HttpStatus.FORBIDDEN, link("engagement", foreign, "engagement_stakeholder", stakeholder(project), agent.token).status)
        val foreignParticipant = participant(foreign, officer.token)
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/engagement_participant/records/$foreignParticipant", null, agent.token).status)
        val own = engagement(project, token = agent.token, attributes = mapOf("scheduled_at" to "2026-06-01T15:00:00Z"))
        assertEquals(HttpStatus.OK, edit("engagement", own, mapOf("place" to "Local comunal"), agent.token).status)
        assertEquals(HttpStatus.OK, transition("engagement", own, "schedule", agent.token).status)
        assertEquals(HttpStatus.OK, edit("engagement", foreign, mapOf("place" to "Plaza"), officer.token).status)
    }

    @Test
    fun `FIELD_AGENT agrega un participante a una interaccion ajena y los conteos se actualizan`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = engagement(project, token = officer.token)
        participant(foreign, agent.token, mapOf("represented_people_count" to 12, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        val counts = record("engagement", foreign)["attributes"]
        assertEquals(12, counts["attendees_total_count"].asInt())
        assertEquals(12, counts["attendees_female_count"].asInt())
        assertEquals(12, counts["attendees_direct_area_count"].asInt())
    }

    @Test
    fun `FIELD_AGENT vincula su captura a un grupo de interes que registro otra persona`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val theirs = stakeholder(project)
        val own = engagement(project, token = agent.token)
        val response = link("engagement", own, "engagement_stakeholder", theirs, agent.token)
        assertEquals(HttpStatus.NO_CONTENT, response.status, response.body)
    }

    @Test
    fun `FIELD_AGENT mueve sus visitas y no las ajenas, y agrega observaciones a cualquiera`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val own = createRecord("field_visit", mapOf("project" to project, "title" to "Visita propia"), token = agent.token)
        val foreign = createRecord("field_visit", mapOf("project" to project, "title" to "Visita ajena"), token = officer.token)
        move("field_visit", own, "start", agent.token)
        assertEquals(HttpStatus.FORBIDDEN, transition("field_visit", foreign, "start", agent.token).status)
        createRecord("field_observation", mapOf("field_visit" to foreign, "observation" to "Camino en mal estado"), token = agent.token)
    }

    @Test
    fun `FIELD_AGENT no cambia un participante ajeno y si el suyo`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val engagement = engagement(project, token = officer.token)
        val foreign = participant(engagement, officer.token)
        assertEquals(HttpStatus.FORBIDDEN, edit("engagement_participant", foreign, mapOf("represented_group" to "Ronda"), agent.token).status)
        val own = participant(engagement, agent.token)
        assertEquals(HttpStatus.OK, edit("engagement_participant", own, mapOf("represented_group" to "Ronda"), agent.token).status)
    }
}
```

Crear `src/test/kotlin/sgspe/engagement/EngagementRegistrationApiTest.kt` (1 pruebas):

```kotlin
package sgspe.engagement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.IfMatch

// S24 del portal (Fase 2.3, Tarea 21) registra una interacción como escrituras sucesivas por la API genérica, sin
// transacción: el alta, los vínculos con grupos de interés y temas, los participantes, las preguntas y al final la
// transición, con If-Match del updatedAt que devolvió el alta. las guardas y los listeners tienen que dejar pasar esa
// secuencia: los conteos que escribe AttendanceCountsListener no invalidan ese If-Match (Versions)
class EngagementRegistrationApiTest : DomainApiTest() {
    @Test
    fun `FIELD_AGENT registra una interaccion realizada como la registra el portal`() {
        val project = projectWithCrs()
        val agent = member("FIELD_AGENT", project)
        val stakeholder = stakeholder(project)
        val topic = createRecord("topic", mapOf("code" to uniqueName("T-"), "name" to "Agua", "active" to true))
        val created =
            tree(
                send(
                    "POST",
                    "/api/objects/engagement/records",
                    mapOf(
                        "attributes" to
                            mapOf(
                                "project" to project,
                                "engagement_type" to "REUNION INFORMATIVA",
                                "title" to "Asamblea de octubre",
                                "held_at" to "2026-10-07T15:00:00Z"
                            )
                    ),
                    HttpStatus.CREATED,
                    agent.token
                )
            )
        val engagement = created["id"].asString()
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", engagement, "engagement_stakeholder", stakeholder, agent.token).status)
        assertEquals(HttpStatus.NO_CONTENT, link("engagement", engagement, "engagement_topic", topic, agent.token).status)
        participant(engagement, agent.token, mapOf("represented_people_count" to 12, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        participant(engagement, agent.token, mapOf("represented_people_count" to 8, "sex" to "MASCULINO", "participant_scope" to "AIDS"))
        createRecord("engagement_question", mapOf("engagement" to engagement, "question_text" to "¿Y el agua?", "topic" to topic), token = agent.token)
        val held =
            transitionWith(
                "engagement",
                engagement,
                "register_held_unplanned",
                agent.token,
                headers = mapOf(IfMatch.HEADER to "\"${created["updatedAt"].asString()}\"")
            )
        assertEquals(HttpStatus.OK, held.status, held.body)
        val stored = record("engagement", engagement)
        assertEquals("held", stored["state"].asString())
        assertEquals(20, stored["attributes"]["attendees_total_count"].asInt())
        assertEquals(12, stored["attributes"]["attendees_female_count"].asInt())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.scope.EditAnyApiTest' --tests 'sgspe.engagement.EngagementRegistrationApiTest'
```

Expected: FAIL: las ediciones de lo ajeno que deben ser 403 todavía responden 200.

- [ ] **Paso 3: Crear `EditAnyGuard.kt`**

Crear `src/main/kotlin/sgspe/scope/EditAnyGuard.kt`:

```kotlin
package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.WriteMark
import sgspe.common.WriteView
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import wasichai.core.identity.CurrentUser
import java.util.UUID

// la edición de lo propio sin own_records_only (security.md §5.5, RQ-SEC-008, ASM-069): en los objetos que declaran
// EDIT_ANY (model/model.json), a quien no tiene esa acción se le rechaza cambiar, transicionar, vincular o borrar un
// registro que creó otra persona (created_by). crear no es cambiar: agregar participantes u observaciones a lo ajeno
// pasa. la escritura con la marca de sgspe que solo cambia derivados (los conteos de AttendanceCountsListener) también.
// decide con requirePermission(user, "EDIT_ANY", objeto) y created_by, nunca con nombres de rol
@Component
@Order(GuardOrder.EDIT_ANY)
class EditAnyGuard(
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val currentUser: CurrentUser,
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.kind == RecordChangeKind.CREATED || change.userId == null) return emptyList()
        if (change.objectName !in catalog.objectsDeclaring(EDIT_ANY)) return emptyList()
        val recordId = change.recordId ?: return emptyList()
        val user = callers.userOrNull() ?: return emptyList()
        if (holdsEditAny(change)) return emptyList()
        if (!dryRun && WriteMark.present() && change.kind == RecordChangeKind.UPDATED && onlyDerived(change)) return emptyList()
        if (createdBy(change.objectName, recordId, user.userId)) return emptyList()
        return listOf(
            Finding(
                code = "NOT_OWN_RECORD",
                message = "Solo puedes cambiar, mover, vincular o borrar los registros de ${change.objectName} que creaste: te falta EDIT_ANY",
                status = HttpStatus.FORBIDDEN
            )
        )
    }

    private suspend fun holdsEditAny(change: RecordWrite): Boolean =
        try {
            currentUser.requirePermission(currentUser.require(), EDIT_ANY, change.objectId)
            true
        } catch (_: ForbiddenException) {
            false
        }

    private suspend fun onlyDerived(change: RecordWrite): Boolean {
        if (change.attributes
                .orEmpty()
                .keys
                .any { it.startsWith("rel:") }
        ) {
            return false
        }
        val derived = catalog.derivedFields(change.objectName)
        return view.changed(change).all { it in derived }
    }

    // created_by es una columna de sistema de wasichai (database-model.md §2.4): se compara en la base, con RecordService
    // como quien llama, sin leer la tabla física
    private suspend fun createdBy(
        objectName: String,
        recordId: UUID,
        userId: UUID
    ): Boolean = records.count(objectName, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(recordId)) > 0

    companion object {
        const val EDIT_ANY = "EDIT_ANY"
    }
}
```

- [ ] **Paso 4: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.scope.EditAnyApiTest' --tests 'sgspe.engagement.EngagementRegistrationApiTest'
```

Expected: `BUILD SUCCESSFUL`; 6 pruebas en verde.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/scope/EditAnyApiTest.kt src/test/kotlin/sgspe/engagement/EngagementRegistrationApiTest.kt src/main/kotlin/sgspe/scope/EditAnyGuard.kt
git commit -m "$(cat <<'MSG'
feat(scope): sin EDIT_ANY solo se edita lo propio

En los objetos que declaran EDIT_ANY, quien no la tiene cambia, borra o mueve solo lo que creó
(created_by). Las escrituras con la marca que solo tocan derivados no cuentan.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 10: Índices de interés y poder, grupo de influencia y prioridad

`InfluenceClassifier` aplica `docs/domain-model.md` §8.2 (RN-STK-003 a RN-STK-005): los quince indicadores de 1 a
5 dan los componentes de afectación, oportunidad y poder; el índice de interés es el promedio de los componentes de
interés y el de poder el de los de poder, con dos decimales HALF_UP; el grupo sale de los umbrales del proyecto
(`ESTRATEGICO`, `POLITICO`, `PARA MONITOREO`, `VULNERABLE`). `Prioritizer` busca la regla de `prioritization_rule` más
específica para el grupo, la postura, el ámbito y el tipo (RN-STK-007). Los dos son funciones puras.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/stakeholder/InfluenceClassifier.kt`
- Crear: `src/main/kotlin/sgspe/stakeholder/Prioritizer.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/InfluenceClassifierTest.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/PrioritizerTest.kt`

**Interfaces:**
- Consume: Nada de las tareas anteriores.
- Produce:
  - `InfluenceClassifier.scores(indicators: Map<String, Int?>): Scores?` (`Scores(components, interestIndex, powerIndex,
    influenceScore)`), `group(interestIndex, powerIndex, interestThreshold, powerThreshold): String`, `INDICATORS`.
  - `Prioritizer.priority(rules: List<Rule>, influenceGroup, stance, scope, stakeholderType): String?`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/stakeholder/InfluenceClassifierTest.kt` (3 pruebas):

```kotlin
package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// RF-STK-006 a RF-STK-009 con los pesos iguales de ASM-031 y el umbral 3 de ASM-003: el ejemplo de api.md §4.3
class InfluenceClassifierTest {
    private val example =
        mapOf(
            "affect_environment" to 4,
            "affect_livelihood" to 3,
            "affect_property_status" to 2,
            "opportunity_work" to 4,
            "opportunity_development" to 3,
            "opportunity_benefits" to 2,
            "power_legal_action" to 2,
            "power_credibility" to 3,
            "power_permit_control" to 4,
            "power_action_scope" to 2,
            "power_organization_level" to 3,
            "power_convening_capacity" to 3,
            "power_network_membership" to 2,
            "power_group_mobilization" to 3,
            "power_other_pressure" to 2
        )

    @Test
    fun `el ejemplo de api md da interes 3,0, poder 2,67, influencia 5,67 y PARA MONITOREO`() {
        val scores = InfluenceClassifier.scores(example)!!
        assertEquals(BigDecimal("3.00"), scores.interestIndex)
        assertEquals(BigDecimal("2.67"), scores.powerIndex)
        assertEquals(BigDecimal("5.67"), scores.influenceScore)
        assertEquals(BigDecimal("2.67"), scores.components["power_action_capacity"])
        assertEquals(BigDecimal("2.33"), scores.components["power_network_mobilization"])
        assertEquals("PARA MONITOREO", InfluenceClassifier.group(scores.interestIndex, scores.powerIndex, BigDecimal(3), BigDecimal(3)))
    }

    @Test
    fun `los cuatro cuadrantes, con el umbral incluido como alto`() {
        val three = BigDecimal(3)
        assertEquals("ESTRATEGICO", InfluenceClassifier.group(three, three, three, three))
        assertEquals("POLITICO", InfluenceClassifier.group(BigDecimal("2.99"), BigDecimal(4), three, three))
        assertEquals("PARA MONITOREO", InfluenceClassifier.group(BigDecimal(5), BigDecimal(1), three, three))
        assertEquals("VULNERABLE", InfluenceClassifier.group(BigDecimal(1), BigDecimal(1), three, three))
        assertEquals("VULNERABLE", InfluenceClassifier.group(three, three, BigDecimal("3.5"), BigDecimal("3.5")))
    }

    @Test
    fun `un componente sin indicadores no cuenta, y sin indicadores de un indice no hay calculo`() {
        val onlyAffect = mapOf("affect_environment" to 5, "power_legal_action" to 1)
        val scores = InfluenceClassifier.scores(onlyAffect)!!
        assertEquals(BigDecimal("5.00"), scores.interestIndex)
        assertEquals(BigDecimal("1.00"), scores.powerIndex)
        assertNull(scores.components["interest_opportunity"])
        assertNull(InfluenceClassifier.scores(mapOf("affect_environment" to 5)))
    }
}
```

Crear `src/test/kotlin/sgspe/stakeholder/PrioritizerTest.kt` (2 pruebas):

```kotlin
package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// RF-STK-011: la fila con el tipo del grupo de interés gana a la que vale para cualquier tipo
class PrioritizerTest {
    private val rules =
        listOf(
            Prioritizer.Rule("ESTRATEGICO", "EN CONTRA", "AIDS", null, "MUY ALTA"),
            Prioritizer.Rule("ESTRATEGICO", "EN CONTRA", "AIDS", "AUTORIDAD LOCAL", "ALTA"),
            Prioritizer.Rule("POLITICO", "NEUTRAL", "AIIS", null, "MEDIA")
        )

    @Test
    fun `gana la fila del tipo, si no la de cualquier tipo`() {
        assertEquals("ALTA", Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", "AIDS", "AUTORIDAD LOCAL"))
        assertEquals("MUY ALTA", Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", "AIDS", "IGLESIA"))
        assertEquals("MUY ALTA", Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", "AIDS", null))
    }

    @Test
    fun `sin postura, sin ambito o sin fila que coincida, no hay prioridad`() {
        assertNull(Prioritizer.priority(rules, "ESTRATEGICO", null, "AIDS", null))
        assertNull(Prioritizer.priority(rules, "ESTRATEGICO", "EN CONTRA", null, null))
        assertNull(Prioritizer.priority(rules, "VULNERABLE", "A FAVOR", "AIDS", null))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.stakeholder.InfluenceClassifierTest' --tests 'sgspe.stakeholder.PrioritizerTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'InfluenceClassifier'`).

- [ ] **Paso 3: Crear `InfluenceClassifier.kt`**

Crear `src/main/kotlin/sgspe/stakeholder/InfluenceClassifier.kt`:

```kotlin
package sgspe.stakeholder

import java.math.BigDecimal
import java.math.RoundingMode

// los índices de una evaluación (RF-STK-006 a RF-STK-009, ASM-031, ASM-003). cada indicador va de 1 a 5; cada componente
// es el promedio de sus indicadores informados y cada índice, el promedio de sus componentes, con pesos iguales mientras
// GAP-002 no fije otros. influencia = interés + poder. el grupo sale de los umbrales del proyecto (3 por defecto): un
// índice es alto desde su umbral. todo se redondea a dos decimales, como se guarda, y el grupo se decide con lo guardado
object InfluenceClassifier {
    val INTEREST: Map<String, List<String>> =
        linkedMapOf(
            "interest_affectation" to listOf("affect_environment", "affect_livelihood", "affect_property_status"),
            "interest_opportunity" to listOf("opportunity_work", "opportunity_development", "opportunity_benefits")
        )
    val POWER: Map<String, List<String>> =
        linkedMapOf(
            "power_resource_control" to listOf("power_legal_action", "power_credibility", "power_permit_control"),
            "power_action_capacity" to listOf("power_action_scope", "power_organization_level", "power_convening_capacity"),
            "power_network_mobilization" to listOf("power_network_membership", "power_group_mobilization", "power_other_pressure")
        )
    val INDICATORS: List<String> = (INTEREST.values + POWER.values).flatten()

    const val STRATEGIC = "ESTRATEGICO"
    const val POLITICAL = "POLITICO"
    const val MONITORING = "PARA MONITOREO"
    const val VULNERABLE = "VULNERABLE"

    data class Scores(
        val components: Map<String, BigDecimal?>,
        val interestIndex: BigDecimal,
        val powerIndex: BigDecimal,
        val influenceScore: BigDecimal
    )

    // null si a uno de los dos índices no le llega ningún indicador
    fun scores(indicators: Map<String, Int?>): Scores? {
        val components = (INTEREST + POWER).mapValues { (_, names) -> average(names.mapNotNull { indicators[it] }.map { BigDecimal(it) }) }
        val interest = average(INTEREST.keys.mapNotNull { components[it] }) ?: return null
        val power = average(POWER.keys.mapNotNull { components[it] }) ?: return null
        return Scores(components.mapValues { it.value?.let(::round) }, round(interest), round(power), round(interest + power))
    }

    fun group(
        interestIndex: BigDecimal,
        powerIndex: BigDecimal,
        interestThreshold: BigDecimal,
        powerThreshold: BigDecimal
    ): String {
        val interestHigh = interestIndex >= interestThreshold
        val powerHigh = powerIndex >= powerThreshold
        return when {
            interestHigh && powerHigh -> STRATEGIC
            powerHigh -> POLITICAL
            interestHigh -> MONITORING
            else -> VULNERABLE
        }
    }

    private fun average(values: List<BigDecimal>): BigDecimal? =
        if (values.isEmpty()) null else values.reduce(BigDecimal::add).divide(BigDecimal(values.size), 10, RoundingMode.HALF_UP)

    private fun round(value: BigDecimal): BigDecimal = value.setScale(2, RoundingMode.HALF_UP)
}
```

- [ ] **Paso 4: Crear `Prioritizer.kt`**

Crear `src/main/kotlin/sgspe/stakeholder/Prioritizer.kt`:

```kotlin
package sgspe.stakeholder

// la prioridad informativa de un grupo de interés con la matriz de priorización del proyecto (RF-STK-011,
// prioritization_rule): grupo de influencia × postura × ámbito y, si la fila lo dice, tipo de actor. una fila con el tipo
// del grupo de interés gana a la que vale para cualquier tipo. null si falta un dato o ninguna fila coincide
object Prioritizer {
    data class Rule(
        val influenceGroup: String?,
        val stance: String?,
        val scope: String?,
        val stakeholderType: String?,
        val priority: String?
    )

    fun priority(
        rules: List<Rule>,
        influenceGroup: String?,
        stance: String?,
        scope: String?,
        stakeholderType: String?
    ): String? {
        if (influenceGroup == null || stance == null || scope == null) return null
        val matching = rules.filter { it.influenceGroup == influenceGroup && it.stance == stance && it.scope == scope }
        return (
            matching.firstOrNull {
                stakeholderType != null && it.stakeholderType == stakeholderType
            } ?: matching.firstOrNull { it.stakeholderType == null }
        )?.priority
    }
}
```

- [ ] **Paso 5: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.stakeholder.InfluenceClassifierTest' --tests 'sgspe.stakeholder.PrioritizerTest'
```

Expected: `BUILD SUCCESSFUL`; 5 pruebas en verde.

- [ ] **Paso 6: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/stakeholder/InfluenceClassifierTest.kt src/test/kotlin/sgspe/stakeholder/PrioritizerTest.kt src/main/kotlin/sgspe/stakeholder/InfluenceClassifier.kt src/main/kotlin/sgspe/stakeholder/Prioritizer.kt
git commit -m "$(cat <<'MSG'
feat(stakeholder): índices de interés y poder, grupo y prioridad

InfluenceClassifier calcula componentes, índices y grupo con los umbrales del proyecto; Prioritizer
elige la regla más específica de la matriz de priorización.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 11: WF-B: StakeholderWriteGuard

Las condiciones de WF-B y de los objetos vecinos (`docs/workflows.md`, `docs/domain-model.md` §4.5): `profile`
exige tipo, ámbito, motivo de inclusión y responsable (409 `PROFILE_INCOMPLETE` con el primer campo que falta,
RF-STK-003); un cargo (`position`) sin persona solo puede estar `VACANTE` o `PREVISTO` (400 `POSITION_WITHOUT_PERSON`,
RF-STK-002); en `prioritization_rule` no hay dos filas sin tipo de actor para el mismo proyecto, grupo, postura y ámbito
(409 `DUPLICATE_RULE`, RF-STK-011), porque el único compuesto no lo impide con NULL (WAS-ADR-0037). Los
casos de `StakeholderWorkflowApiTest` (Fase 2.1) que dependen de una guarda, incluido `assess` por la API genérica (403 de
`CommandTransitionGuard`), viven en `StakeholderWorkflowGuardsApiTest`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/stakeholder/StakeholderWriteGuard.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/StakeholderWorkflowGuardsApiTest.kt`

**Interfaces:**
- Consume: `Records`, `Values` (Tarea 2); `RuleGuard`, `Finding`, `GuardOrder`, `WriteView`, `isLink` (Tarea 3).
- Produce: `StakeholderWriteGuard`, sin API propia.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/stakeholder/StakeholderWorkflowGuardsApiTest.kt` (4 pruebas):

```kotlin
package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los casos de WF-B que dependen de las guardas de 2.2 (StakeholderWorkflowApiTest): assess por la API genérica se
// rechaza; caracterizar exige sus datos; retirar sin motivo es 400; las reglas de cargos y de la matriz de priorización
class StakeholderWorkflowGuardsApiTest : DomainApiTest() {
    @Test
    fun `assess por la API generica es 403 aunque el rol la tenga`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder =
            stakeholder(
                project,
                mapOf(
                    "stakeholder_type" to "LIDER LOCAL",
                    "inclusion_reason" to "Líder",
                    "owner_user_id" to manager.id,
                    "owner_user_email" to manager.email
                )
            )
        move("stakeholder", stakeholder, "profile", manager.token)
        assertEquals(HttpStatus.FORBIDDEN, transition("stakeholder", stakeholder, "assess", manager.token).status)
    }

    @Test
    fun `caracterizar exige tipo, ambito, motivo y responsable, y retirar y reincorporar exigen motivo`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = stakeholder(project)
        val response = transition("stakeholder", stakeholder, "profile", manager.token)
        assertEquals(HttpStatus.CONFLICT, response.status)
        assertEquals(listOf("stakeholder_type"), problemFields(response.body))
        assertEquals(listOf("reason"), problemFields(transition("stakeholder", stakeholder, "retire_identified", manager.token).body))
        move("stakeholder", stakeholder, "retire_identified", manager.token, "duplicado de otro registro")
        assertEquals(listOf("reason"), problemFields(transition("stakeholder", stakeholder, "reinstate", manager.token).body))
        move("stakeholder", stakeholder, "reinstate", manager.token, "se separó de la organización")
    }

    @Test
    fun `un cargo sin persona solo VACANTE o PREVISTO`() {
        val organization = organization()
        val vigente =
            exchange(
                "POST",
                "/api/objects/position/records",
                mapOf("attributes" to mapOf("organization" to organization, "position_title" to "Presidente", "position_status" to "VIGENTE"))
            )
        assertEquals(HttpStatus.BAD_REQUEST, vigente.status)
        assertEquals(listOf("position_status"), problemFields(vigente.body))
        createRecord("position", mapOf("organization" to organization, "position_title" to "Presidente", "position_status" to "VACANTE"))
    }

    @Test
    fun `dos filas sin tipo para la misma combinacion de la matriz se rechazan`() {
        val project = projectWithCrs()
        val row =
            mapOf(
                "project" to project,
                "influence_group" to "ESTRATEGICO",
                "stance" to "EN CONTRA",
                "stakeholder_scope" to "AIDS",
                "information_priority" to "MUY ALTA"
            )
        createRecord("prioritization_rule", row)
        assertEquals(HttpStatus.CONFLICT, exchange("POST", "/api/objects/prioritization_rule/records", mapOf("attributes" to row)).status)
        createRecord("prioritization_rule", row + ("stakeholder_type" to "AUTORIDAD LOCAL"))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.stakeholder.StakeholderWorkflowGuardsApiTest'
```

Expected: FAIL: `profile` sin tipo todavía pasa y la regla duplicada se guarda.

- [ ] **Paso 3: Crear `StakeholderWriteGuard.kt`**

Crear `src/main/kotlin/sgspe/stakeholder/StakeholderWriteGuard.kt`:

```kotlin
package sgspe.stakeholder

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las reglas de BC-03 que la metadata no expresa (domain-model §7.2): caracterizar un grupo de interés exige tipo,
// ámbito, motivo de inclusión y responsable (RF-STK-003); un cargo sin persona solo puede estar VACANTE o PREVISTO
// (RF-STK-002); y en la matriz de priorización no hay dos filas sin tipo para la misma combinación, porque el único
// compuesto no lo impide con NULL (RF-STK-011, WAS-ADR-0037)
@Component
@Order(GuardOrder.DOMAIN)
class StakeholderWriteGuard(
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when (change.objectName) {
            "stakeholder" ->
                if (change.kind == RecordChangeKind.TRANSITIONED &&
                    change.transition == "profile"
                ) {
                    profile(change.before.orEmpty())
                } else {
                    emptyList()
                }
            "position" -> if (writes(change)) position(view.after(change)) else emptyList()
            "prioritization_rule" -> if (writes(change)) rule(change, view.after(change)) else emptyList()
            else -> emptyList()
        }

    private fun writes(change: RecordWrite): Boolean = (change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) && !change.isLink()

    private fun profile(stakeholder: Map<String, Any?>): List<Finding> {
        val missing = PROFILE_FIELDS.filter { Values.text(stakeholder[it]) == null }
        if (missing.isEmpty()) return emptyList()
        return listOf(Finding("PROFILE_INCOMPLETE", "Caracterizar exige ${missing.joinToString(", ")}", missing.first()))
    }

    private fun position(after: Map<String, Any?>): List<Finding> {
        if (Values.text(after["person"]) != null) return emptyList()
        val status = Values.text(after["position_status"])
        if (status in WITHOUT_PERSON) return emptyList()
        return listOf(
            Finding(
                "POSITION_WITHOUT_PERSON",
                "Un cargo sin persona solo puede estar VACANTE o PREVISTO",
                "position_status",
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    private suspend fun rule(
        change: RecordWrite,
        after: Map<String, Any?>
    ): List<Finding> {
        if (Values.text(after["stakeholder_type"]) != null) return emptyList()
        val key = listOf("project", "influence_group", "stance", "stakeholder_scope")
        if (key.any { Values.text(after[it]) == null }) return emptyList()
        val twins =
            records
                .all("prioritization_rule", filters = key.associateWith { Values.text(after[it])!! })
                .filter { Values.text(it.attributes["stakeholder_type"]) == null && it.id != change.recordId?.toString() }
        if (twins.isEmpty()) return emptyList()
        return listOf(
            Finding(
                "DUPLICATE_RULE",
                "Ya hay una fila para cualquier tipo de actor con ese grupo de influencia, postura y ámbito",
                "stakeholder_type"
            )
        )
    }

    companion object {
        val PROFILE_FIELDS = listOf("stakeholder_type", "stakeholder_scope", "inclusion_reason", "owner_user_id")
        val WITHOUT_PERSON = setOf("VACANTE", "PREVISTO")
    }
}
```

- [ ] **Paso 4: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.stakeholder.StakeholderWorkflowGuardsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 4 pruebas en verde.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/stakeholder/StakeholderWorkflowGuardsApiTest.kt src/main/kotlin/sgspe/stakeholder/StakeholderWriteGuard.kt
git commit -m "$(cat <<'MSG'
feat(stakeholder): condiciones de WF-B, cargos y matriz de priorización

profile exige tipo, ámbito, motivo de inclusión y responsable; un cargo sin persona solo está
vacante o previsto; la matriz no admite dos filas sin tipo para la misma combinación.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 12: Evaluaciones de grupos de interés: endpoint y vista previa

`POST /api/sgspe/stakeholders/{id}/assessments` y `…/assessments/preview` (`docs/api.md` §4.1). En una
transacción con candado por grupo de interés: valida el cuerpo (los calculados no se mandan), calcula índices, grupo y
prioridad, guarda la evaluación (append-only, con motivo), aplica `profile` si hace falta y `assess` con la marca, y
actualiza los derivados del grupo de interés. Una evaluación con fecha anterior a la última no pisa los valores vigentes y
devuelve `BACKDATED_ASSESSMENT` (Review Focus 3); un cambio de postura sin contacto en los últimos
`sgspe.stakeholder.stance-divergence-days` (30) días devuelve `STANCE_DIVERGENCE`; sin regla de prioridad,
`NO_PRIORITY_RULE`. El ajuste manual del grupo llega como lo manda el portal (`group_overridden`, `influence_group`,
`override_reason`) y la vista previa devuelve `computed` con los nombres del modelo. `Idempotency-Key` se acepta y no
deduplica en F2.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/stakeholder/Assessments.kt`
- Crear: `src/main/kotlin/sgspe/stakeholder/AssessmentController.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/AssessmentInputTest.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/StakeholderAssessmentApiTest.kt`

**Interfaces:**
- Consume: `InfluenceClassifier`, `Prioritizer` (Tarea 10); `Records.transition`, `Transactions.locked`, `Permissions`, `Callers` (Tarea 2); `IfMatch` y `Versions` (Tarea 4); `ProjectParameters` (Tarea 6); `ModelCatalog.transition` y `enumOf` (Tarea 1).
- Produce:
  - `POST /api/sgspe/stakeholders/{id}/assessments` → 201, `Location: /api/objects/stakeholder_assessment/records/{id}`,
    cuerpo `AssessmentResult(assessment, stakeholder, warnings: List<AssessmentWarning(code, message)>)`.
  - `POST /api/sgspe/stakeholders/{id}/assessments/preview` → `AssessmentPreview(computed, warnings)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/stakeholder/AssessmentInputTest.kt` (2 pruebas):

```kotlin
package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.ModelCatalog
import wasichai.core.common.ValidationException
import java.time.LocalDate

// lo que valida el endpoint de evaluaciones antes de escribir: los rangos de 1 a 5 que wasichai no tiene, los ENUM, la
// fecha no futura y que lo calculado no se manda
class AssessmentInputTest {
    private val catalog =
        ModelCatalog.parse(
            """
            {"enums": {"stance": ["A FAVOR", "NEUTRAL", "EN CONTRA PERO ABIERTO A ESCUCHAR", "EN CONTRA"],
                       "assessment_basis": ["ENTREVISTA", "ENCUESTA"],
                       "influence_group": ["ESTRATEGICO", "POLITICO", "PARA MONITOREO", "VULNERABLE"]},
             "objects": [{"name": "stakeholder_assessment", "fields": [
               {"name": "stance", "type": "ENUM", "enum": "stance"},
               {"name": "basis", "type": "ENUM", "enum": "assessment_basis"},
               {"name": "influence_group", "type": "ENUM", "enum": "influence_group"}]}]}
            """.trimIndent(),
            """{"roles": []}"""
        )
    private val today = LocalDate.parse("2026-05-20")

    private fun parse(attributes: Map<String, Any?>) = AssessmentInput.parse(mapOf("attributes" to attributes), catalog, today, requireDate = true)

    @Test
    fun `una evaluacion valida separa el ajuste manual del grupo`() {
        val input =
            parse(
                mapOf(
                    "assessment_date" to "2026-05-12",
                    "basis" to "ENTREVISTA",
                    "affect_environment" to 4,
                    "power_legal_action" to 2.0,
                    "stance" to "EN CONTRA",
                    "trust_score" to 2,
                    "influence_group" to "ESTRATEGICO",
                    "override_reason" to "lidera la mesa de diálogo"
                )
            )
        assertEquals(4, input.indicators["affect_environment"])
        assertEquals(2, input.indicators["power_legal_action"])
        assertEquals("ESTRATEGICO", input.influenceGroup)
        assertFalse("influence_group" in input.attributes)
        assertEquals("lidera la mesa de diálogo", input.overrideReason)
        // como lo manda el portal (Fase 2.3): group_overridden acompaña al ajuste, y en false lo descarta
        val portal =
            parse(
                mapOf(
                    "assessment_date" to "2026-05-12",
                    "basis" to "ENTREVISTA",
                    "group_overridden" to true,
                    "influence_group" to "POLITICO",
                    "override_reason" to "peso real"
                )
            )
        assertEquals("POLITICO", portal.influenceGroup)
        assertFalse("group_overridden" in portal.attributes)
        assertEquals(
            null,
            parse(
                mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA", "group_overridden" to false, "influence_group" to "POLITICO")
            ).influenceGroup
        )
        val noGroup = assertThrows<ValidationException> { parse(mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA", "group_overridden" to true)) }
        assertEquals("attributes.influence_group", noGroup.violations.single().field)
    }

    @Test
    fun `fuera de rango, fraccion, ENUM desconocido, fecha futura y campo calculado son 400`() {
        val error =
            assertThrows<ValidationException> {
                parse(
                    mapOf("assessment_date" to "2026-06-01", "basis" to "RUMOR", "affect_environment" to 6, "power_legal_action" to 2.5, "stance" to "EN DUDA")
                )
            }
        assertEquals(
            setOf("attributes.affect_environment", "attributes.power_legal_action", "attributes.assessment_date", "attributes.basis", "attributes.stance"),
            error.violations.map { it.field }.toSet()
        )
        val computed = assertThrows<ValidationException> { parse(mapOf("interest_index" to 3)) }
        assertEquals("attributes.interest_index", computed.violations.single().field)
        val missing = assertThrows<ValidationException> { parse(emptyMap()) }
        assertEquals(setOf("attributes.assessment_date", "attributes.basis"), missing.violations.map { it.field }.toSet())
    }
}
```

Crear `src/test/kotlin/sgspe/stakeholder/StakeholderAssessmentApiTest.kt` (7 pruebas):

```kotlin
package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// POST /api/sgspe/stakeholders/{id}/assessments y su vista previa (api.md §4.3, RF-STK-005 a RF-STK-011): el ejemplo de
// api.md, la línea base, la advertencia de divergencia, la inmutabilidad y quién puede evaluar (GAP-024)
class StakeholderAssessmentApiTest : DomainApiTest() {
    private val indicators =
        mapOf(
            "affect_environment" to 4,
            "affect_livelihood" to 3,
            "affect_property_status" to 2,
            "opportunity_work" to 4,
            "opportunity_development" to 3,
            "opportunity_benefits" to 2,
            "power_legal_action" to 2,
            "power_credibility" to 3,
            "power_permit_control" to 4,
            "power_action_scope" to 2,
            "power_organization_level" to 3,
            "power_convening_capacity" to 3,
            "power_network_membership" to 2,
            "power_group_mobilization" to 3,
            "power_other_pressure" to 2
        )

    // un grupo de interés identificado con lo que exige caracterizarlo: tipo, ámbito, motivo y responsable
    private fun complete(
        project: String,
        manager: Account
    ): String =
        stakeholder(
            project,
            mapOf(
                "stakeholder_type" to "AUTORIDAD LOCAL",
                "inclusion_reason" to "Autoridad del caserío",
                "owner_user_id" to manager.id,
                "owner_user_email" to manager.email
            )
        )

    // el mismo, ya caracterizado (profile aplicado)
    private fun profiled(
        project: String,
        manager: Account
    ): String {
        val stakeholder = complete(project, manager)
        move("stakeholder", stakeholder, "profile", manager.token)
        return stakeholder
    }

    private fun assess(
        stakeholder: String,
        attributes: Map<String, Any?>,
        token: String,
        why: String? = "evaluación de mayo"
    ) = exchange(
        "POST",
        "/api/sgspe/stakeholders/$stakeholder/assessments",
        mapOf("attributes" to attributes),
        token,
        listOfNotNull(why?.let { REASON to reason(it) }).toMap()
    )

    @Test
    fun `el ejemplo de api md lleva al grupo de interes a assessed con sus valores vigentes`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        createRecord(
            "prioritization_rule",
            mapOf(
                "project" to project,
                "influence_group" to "PARA MONITOREO",
                "stance" to "EN CONTRA PERO ABIERTO A ESCUCHAR",
                "stakeholder_scope" to "AIDS",
                "information_priority" to "ALTA"
            )
        )
        val stakeholder = profiled(project, manager)
        val attributes =
            indicators +
                mapOf(
                    "assessment_date" to "2026-05-12",
                    "basis" to "ENTREVISTA",
                    "stance" to "EN CONTRA PERO ABIERTO A ESCUCHAR",
                    "open_to_dialogue" to true,
                    "trust_score" to 2
                )
        val response = assess(stakeholder, attributes, manager.token)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val body = tree(response.body)
        val assessment = body["assessment"]["attributes"]
        assertEquals(3.0, assessment["interest_index"].asDouble())
        assertEquals(2.67, assessment["power_index"].asDouble())
        assertEquals(5.67, assessment["influence_score"].asDouble())
        assertEquals("PARA MONITOREO", assessment["influence_group"].asString())
        assertEquals(manager.email, assessment["assessor_user_email"].asString())
        assertTrue(
            response.headers.location
                .toString()
                .endsWith(body["assessment"]["id"].asString())
        )
        val current = body["stakeholder"]
        assertEquals("assessed", current["state"].asString())
        assertEquals("ALTA", current["attributes"]["information_priority"].asString())
        assertEquals("EN CONTRA PERO ABIERTO A ESCUCHAR", current["attributes"]["stance"].asString())
        assertEquals("2026-05-12", current["attributes"]["last_assessment_date"].asString())
        assertEquals(2, current["attributes"]["trust_score"].asInt())
    }

    @Test
    fun `la segunda toma la primera como linea base y avisa la divergencia de postura`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val first =
            tree(
                assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-01", "basis" to "ENTREVISTA", "stance" to "EN CONTRA"), manager.token).body
            )
        val second =
            tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENCUESTA", "stance" to "NEUTRAL"), manager.token).body)
        assertEquals(first["assessment"]["id"].asString(), second["assessment"]["attributes"]["baseline_assessment"].asString())
        assertTrue(second["assessment"]["attributes"]["stance_changed"].asBoolean())
        assertEquals(listOf("STANCE_DIVERGENCE", "NO_PRIORITY_RULE"), second["warnings"].values().map { it["code"].asString() })
        val backdated =
            tree(assess(stakeholder, indicators + mapOf("assessment_date" to "2026-04-01", "basis" to "ENTREVISTA", "stance" to "A FAVOR"), manager.token).body)
        assertTrue(backdated["warnings"].values().any { it["code"].asString() == "BACKDATED_ASSESSMENT" })
        assertEquals("NEUTRAL", record("stakeholder", stakeholder)["attributes"]["stance"].asString())
    }

    @Test
    fun `sin motivo, con un indicador fuera de rango o con un ajuste sin justificar, 400 y nada se guarda`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val base = indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA")
        assertEquals(listOf("reason"), problemFields(assess(stakeholder, base, manager.token, why = null).body))
        assertEquals(listOf("attributes.power_credibility"), problemFields(assess(stakeholder, base + ("power_credibility" to 7), manager.token).body))
        assertEquals(listOf("attributes.override_reason"), problemFields(assess(stakeholder, base + ("influence_group" to "ESTRATEGICO"), manager.token).body))
        assertEquals("profiled", record("stakeholder", stakeholder)["state"].asString())
        assertEquals(0, tree(send("GET", "/api/objects/stakeholder_assessment/records?stakeholder=$stakeholder", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `un grupo de interes identificado se caracteriza antes, y uno retirado no se evalua`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val incomplete = stakeholder(project)
        val rejected = assess(incomplete, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token)
        assertEquals(HttpStatus.CONFLICT, rejected.status, rejected.body)
        assertEquals("identified", record("stakeholder", incomplete)["state"].asString())
        val identified = complete(project, manager)
        val assessed = assess(identified, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token)
        assertEquals(HttpStatus.CREATED, assessed.status, assessed.body)
        assertEquals("assessed", record("stakeholder", identified)["state"].asString())
        val ready = profiled(project, manager)
        move("stakeholder", ready, "retire_profiled", manager.token, "ya no participa")
        assertEquals(HttpStatus.CONFLICT, assess(ready, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token).status)
    }

    @Test
    fun `COMMUNITY_RELATIONS_OFFICER no evalua y la evaluacion no se cambia por ninguna puerta`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val stakeholder = profiled(project, manager)
        assertEquals(
            HttpStatus.FORBIDDEN,
            assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), officer.token).status
        )
        val id =
            tree(
                assess(stakeholder, indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"), manager.token).body
            )["assessment"]["id"].asString()
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange("PUT", "/api/objects/stakeholder_assessment/records/$id", mapOf("attributes" to mapOf("trust_score" to 5))).status
        )
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange(
                "POST",
                "/api/objects/stakeholder_assessment/records",
                mapOf("attributes" to mapOf("stakeholder" to stakeholder))
            ).status
        )
    }

    @Test
    fun `la vista previa calcula sin guardar nada`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val preview =
            tree(send("POST", "/api/sgspe/stakeholders/$stakeholder/assessments/preview", mapOf("attributes" to indicators), HttpStatus.OK, manager.token))
        assertEquals("PARA MONITOREO", preview["computed"]["influence_group"].asString())
        assertFalse(preview["computed"]["group_overridden"].asBoolean())
        assertEquals("profiled", record("stakeholder", stakeholder)["state"].asString())
        assertEquals(0, tree(send("GET", "/api/objects/stakeholder_assessment/records?stakeholder=$stakeholder", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `Idempotency-Key se acepta y en F2 todavia no deduplica`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val stakeholder = profiled(project, manager)
        val headers = mapOf(REASON to reason("evaluación"), "Idempotency-Key" to "clave-de-prueba-1")
        val body = mapOf("attributes" to indicators + mapOf("assessment_date" to "2026-05-12", "basis" to "ENTREVISTA"))
        repeat(2) { send("POST", "/api/sgspe/stakeholders/$stakeholder/assessments", body, HttpStatus.CREATED, manager.token, headers) }
        assertEquals(2, tree(send("GET", "/api/objects/stakeholder_assessment/records?stakeholder=$stakeholder", null, HttpStatus.OK))["totalElements"].asInt())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.stakeholder.AssessmentInputTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'AssessmentInput'`).

- [ ] **Paso 3: Crear `Assessments.kt`**

Crear `src/main/kotlin/sgspe/stakeholder/Assessments.kt`:

```kotlin
package sgspe.stakeholder

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.IfMatch
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import wasichai.core.common.Actions
import wasichai.core.common.ConflictException
import wasichai.core.common.FieldViolation
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AssessmentWarning(
    val code: String,
    val message: String
)

data class AssessmentResult(
    val assessment: RecordResponse,
    val stakeholder: RecordResponse,
    val warnings: List<AssessmentWarning>
)

data class AssessmentPreview(
    val computed: Map<String, Any?>,
    val warnings: List<AssessmentWarning>
)

// las evaluaciones de grupos de interés (api.md §4.3, RF-STK-005): fechadas e inmutables (stakeholder_assessment es
// appendOnly, apiOnly y requiresReason), con los índices, el grupo y la prioridad calculados aquí, la evaluación anterior
// como línea base y los valores vigentes del grupo de interés escritos en la misma transacción. la primera evaluación
// lleva el grupo de interés a `assessed` con la transición de comando `assess`, y antes `profile` si sigue identificado.
// evaluar escribe `stance`, un campo restringido (GAP-024): solo pueden hacerlo los roles que lo escriben
@Service
class Assessments(
    private val records: Records,
    private val versions: Versions,
    private val transactions: Transactions,
    private val permissions: Permissions,
    private val metadata: MetadataService,
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val clock: Clock,
    @Value("\${sgspe.stakeholder.stance-divergence-days:30}") private val divergenceDays: Long
) {
    suspend fun preview(
        stakeholderId: UUID,
        body: Map<String, Any?>
    ): AssessmentPreview {
        val input = AssessmentInput.parse(body, catalog, today(), requireDate = false)
        val stakeholder = records.get(STAKEHOLDER, stakeholderId)
        val plan = plan(stakeholder, input)
        return AssessmentPreview(plan.computed, plan.warnings)
    }

    suspend fun register(
        stakeholderId: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): AssessmentResult {
        val input = AssessmentInput.parse(body, catalog, today(), requireDate = true)
        if (reason.isNullOrBlank()) throw ValidationException("Una evaluación exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val expected = IfMatch.parse(ifMatch)
        val user = callers.require()
        permissions.require(user, "Evaluar un grupo de interés", Actions.CREATE to ASSESSMENT, Actions.UPDATE to STAKEHOLDER)
        requireStanceWritable(input)
        return transactions.locked("stakeholder:$stakeholderId") {
            var stakeholder = records.get(STAKEHOLDER, stakeholderId)
            versions.check(STAKEHOLDER, stakeholderId, expected, stakeholder.updatedAt, "El grupo de interés")
            if (stakeholder.state == RETIRED) throw ConflictException("Un grupo de interés retirado no se evalúa: reincorpórelo primero")
            requireTransitionRole(
                user,
                if (stakeholder.state ==
                    IDENTIFIED
                ) {
                    listOf("profile", "assess")
                } else if (stakeholder.state == ASSESSED) {
                    emptyList()
                } else {
                    listOf("assess")
                }
            )
            if (stakeholder.state == IDENTIFIED) stakeholder = records.transition(STAKEHOLDER, stakeholderId, "profile", reason)
            val plan = plan(stakeholder, input)
            val assessment =
                records.create(
                    ASSESSMENT,
                    input.attributes + plan.stored +
                        mapOf("stakeholder" to stakeholderId.toString(), "assessor_user_id" to user.userId.toString(), "assessor_user_email" to user.email),
                    reason
                )
            if (plan.latest) records.patch(STAKEHOLDER, stakeholderId, plan.current, reason)
            if (stakeholder.state != ASSESSED) records.transition(STAKEHOLDER, stakeholderId, "assess", reason)
            AssessmentResult(assessment, records.get(STAKEHOLDER, stakeholderId), plan.warnings)
        }
    }

    private class Plan(
        val computed: Map<String, Any?>,
        val stored: Map<String, Any?>,
        val current: Map<String, Any?>,
        val latest: Boolean,
        val warnings: List<AssessmentWarning>
    )

    private suspend fun plan(
        stakeholder: RecordResponse,
        input: AssessmentInput
    ): Plan {
        val attributes = stakeholder.attributes
        val project = Records.uuid(attributes["project"])?.let { records.find("project", it)?.attributes }.orEmpty()
        val scores =
            InfluenceClassifier.scores(input.indicators)
                ?: throw ValidationException(
                    "Faltan indicadores",
                    listOf(FieldViolation("attributes", "informe al menos un indicador de interés y uno de poder, de 1 a 5"))
                )
        val computedGroup =
            InfluenceClassifier.group(
                scores.interestIndex,
                scores.powerIndex,
                ProjectParameters.decimal(project, "interest_threshold"),
                ProjectParameters.decimal(project, "power_threshold")
            )
        val overridden = input.influenceGroup != null && input.influenceGroup != computedGroup
        if (overridden && input.overrideReason == null) {
            throw ValidationException("Ajustar el grupo exige justificación", "attributes.override_reason", "explique por qué el grupo no es $computedGroup")
        }
        val group = input.influenceGroup ?: computedGroup
        val rules =
            Records
                .uuid(attributes["project"])
                ?.let { projectId ->
                    records.all("prioritization_rule", filters = mapOf("project" to projectId.toString())).map {
                        Prioritizer.Rule(
                            Values.text(it.attributes["influence_group"]),
                            Values.text(it.attributes["stance"]),
                            Values.text(it.attributes["stakeholder_scope"]),
                            Values.text(it.attributes["stakeholder_type"]),
                            Values.text(it.attributes["information_priority"])
                        )
                    }
                }.orEmpty()
        val priority =
            Prioritizer.priority(
                rules,
                group,
                input.stance,
                Values.text(attributes["stakeholder_scope"]),
                Values.text(attributes["stakeholder_type"])
            )
        val history =
            records
                .all(ASSESSMENT, filters = mapOf("stakeholder" to stakeholder.id))
                .sortedWith(compareBy({ Values.text(it.attributes["assessment_date"]) }, { it.createdAt }))
        val date = input.date ?: today()
        val baseline = history.lastOrNull { LocalDate.parse(Values.text(it.attributes["assessment_date"])!!) <= date }
        val latest = history.none { LocalDate.parse(Values.text(it.attributes["assessment_date"])!!) > date }
        val baselineStance = baseline?.let { Values.text(it.attributes["stance"]) }
        val stanceChanged = baselineStance != null && input.stance != null && baselineStance != input.stance
        val warnings = mutableListOf<AssessmentWarning>()
        if (stanceChanged) {
            val days = ChronoUnit.DAYS.between(LocalDate.parse(Values.text(baseline!!.attributes["assessment_date"])!!), date)
            if (days <= divergenceDays) {
                warnings += AssessmentWarning("STANCE_DIVERGENCE", "La postura difiere de la evaluación de hace $days días: revise la fuente (CON-006)")
            }
        }
        if (!latest) {
            warnings += AssessmentWarning("BACKDATED_ASSESSMENT", "Hay evaluaciones posteriores: esta no cambia los valores vigentes del grupo de interés")
        }
        if (priority == null) {
            warnings += AssessmentWarning("NO_PRIORITY_RULE", "Ninguna fila de la matriz de priorización coincide: la prioridad queda vacía")
        }
        val stored =
            scores.components +
                mapOf(
                    "interest_index" to scores.interestIndex,
                    "power_index" to scores.powerIndex,
                    "influence_score" to scores.influenceScore,
                    "influence_group" to group,
                    "group_overridden" to overridden,
                    "baseline_assessment" to baseline?.id,
                    "stance_changed" to stanceChanged
                )
        val current =
            mapOf(
                "stance" to input.stance,
                "influence_group" to group,
                "information_priority" to priority,
                "interest_index" to scores.interestIndex,
                "power_index" to scores.powerIndex,
                "trust_score" to input.trustScore,
                "last_assessment_date" to date.toString()
            )
        return Plan(stored + ("information_priority" to priority), stored, current, latest, warnings)
    }

    // evaluar escribe stance en el grupo de interés y, si viene, en la evaluación: los dos son restringidos (GAP-024)
    private suspend fun requireStanceWritable(input: AssessmentInput) {
        val onStakeholder =
            metadata
                .definitionOf(STAKEHOLDER)
                .fields
                .firstOrNull { it.name == "stance" }
                ?.editable == true
        val onAssessment =
            input.stance == null || metadata
                .definitionOf(ASSESSMENT)
                .fields
                .firstOrNull { it.name == "stance" }
                ?.editable == true
        if (!onStakeholder || !onAssessment) {
            throw ForbiddenException("Evaluar escribe la postura del grupo de interés, un campo que tus roles no pueden escribir (GAP-024)")
        }
    }

    // antes de escribir: los roles de las transiciones que el comando aplicará, de model/workflows/stakeholder.json
    private fun requireTransitionRole(
        user: AuthenticatedUser,
        transitions: List<String>
    ) {
        if (user.isAdmin) return
        for (name in transitions) {
            val roles = catalog.transition(STAKEHOLDER, name)?.roles.orEmpty()
            if (roles.none { it in user.roles }) throw ForbiddenException("La transición $name exige uno de los roles ${roles.joinToString(", ")}")
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(LIMA))

    companion object {
        const val STAKEHOLDER = "stakeholder"
        const val ASSESSMENT = "stakeholder_assessment"
        const val IDENTIFIED = "identified"
        const val ASSESSED = "assessed"
        const val RETIRED = "retired"
    }
}

// lo que manda el cliente en `attributes`, validado: los indicadores y la confianza de 1 a 5 (wasichai no tiene mínimos
// ni máximos), los ENUM contra su catálogo, la fecha no futura. lo calculado no se manda
class AssessmentInput(
    val attributes: Map<String, Any?>,
    val indicators: Map<String, Int?>,
    val date: LocalDate?,
    val stance: String?,
    val trustScore: Int?,
    val influenceGroup: String?,
    val overrideReason: String?
) {
    companion object {
        val TEXTS =
            listOf(
                "stance_reasons",
                "concerns",
                "expectations",
                "project_knowledge",
                "company_knowledge",
                "team_closeness",
                "override_reason"
            )
        val BOOLEANS = listOf("open_to_dialogue", "communication_sufficient", "information_adequate")
        val ALLOWED: Set<String> =
            (
                listOf("assessment_date", "basis", "stance", "trust_score", "position", "influence_group", "group_overridden") + TEXTS + BOOLEANS +
                    InfluenceClassifier.INDICATORS
            ).toSet()

        fun parse(
            body: Map<String, Any?>,
            catalog: ModelCatalog,
            today: LocalDate,
            requireDate: Boolean
        ): AssessmentInput {
            sgspe.common.StrictBody
                .requireOnly(body, setOf("attributes"))
            val attributes = sgspe.common.StrictBody.objectOf(body, "attributes")
            sgspe.common.StrictBody
                .requireOnly(attributes, ALLOWED, "attributes.")
            val violations = mutableListOf<FieldViolation>()
            val indicators =
                InfluenceClassifier.INDICATORS.associateWith { name -> scale(attributes[name], "attributes.$name", violations) }
            val trust = scale(attributes["trust_score"], "attributes.trust_score", violations)
            val date =
                Values.text(attributes["assessment_date"])?.let {
                    try {
                        LocalDate.parse(it)
                    } catch (_: DateTimeParseException) {
                        violations += FieldViolation("attributes.assessment_date", "debe ser una fecha AAAA-MM-DD")
                        null
                    }
                }
            if (date == null && requireDate && violations.none { it.field == "attributes.assessment_date" }) {
                violations += FieldViolation("attributes.assessment_date", "es obligatoria")
            }
            if (date != null && date.isAfter(today)) violations += FieldViolation("attributes.assessment_date", "no puede ser futura")
            val basis = Values.text(attributes["basis"])
            if (requireDate && basis == null) violations += FieldViolation("attributes.basis", "es obligatoria")
            enumCheck(basis, catalog.enumOf(Assessments.ASSESSMENT, "basis"), "attributes.basis", violations)
            val stance = Values.text(attributes["stance"])
            enumCheck(stance, catalog.enumOf(Assessments.ASSESSMENT, "stance"), "attributes.stance", violations)
            // group_overridden lo manda el portal (Fase 2.3) junto con el ajuste: false descarta influence_group; true lo exige
            val overriddenFlag = Values.bool(attributes["group_overridden"])
            val group = if (overriddenFlag == false) null else Values.text(attributes["influence_group"])
            if (overriddenFlag == true && group == null) violations += FieldViolation("attributes.influence_group", "elija el grupo del ajuste")
            enumCheck(group, catalog.enumOf(Assessments.ASSESSMENT, "influence_group"), "attributes.influence_group", violations)
            if (violations.isNotEmpty()) throw ValidationException("Evaluación inválida", violations)
            // influence_group lo calcula sgspe: si el cliente lo manda es un ajuste manual, que va en el resultado
            return AssessmentInput(
                attributes = attributes - "influence_group" - "group_overridden",
                indicators = indicators,
                date = date,
                stance = stance,
                trustScore = trust,
                influenceGroup = group,
                overrideReason = Values.text(attributes["override_reason"])
            )
        }

        private fun scale(
            value: Any?,
            field: String,
            violations: MutableList<FieldViolation>
        ): Int? {
            if (value == null) return null
            val number = Values.decimal(value)
            if (number == null || number.stripTrailingZeros().scale() > 0 || number < BigDecimal.ONE || number > BigDecimal(5)) {
                violations += FieldViolation(field, "debe ser un entero de 1 a 5")
                return null
            }
            return number.toInt()
        }

        private fun enumCheck(
            value: String?,
            options: List<String>,
            field: String,
            violations: MutableList<FieldViolation>
        ) {
            if (value != null && options.isNotEmpty() && value !in options) violations += FieldViolation(field, "debe ser uno de ${options.joinToString(", ")}")
        }
    }
}
```

- [ ] **Paso 4: Crear `AssessmentController.kt`**

Crear `src/main/kotlin/sgspe/stakeholder/AssessmentController.kt`:

```kotlin
package sgspe.stakeholder

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.IfMatch
import wasichai.core.data.ChangeReason
import java.net.URI
import java.util.UUID

// api.md §4.3. Idempotency-Key se acepta y se ignora en F2 (§3.5): se honra desde F3, cuando llega idempotency_key
@RestController
@RequestMapping("/api/sgspe/stakeholders/{id}/assessments")
class AssessmentController(
    private val assessments: Assessments
) {
    @PostMapping
    suspend fun register(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?
    ): ResponseEntity<AssessmentResult> {
        val result = assessments.register(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/objects/stakeholder_assessment/records/${result.assessment.id}"))
            .body(result)
    }

    @PostMapping("/preview")
    suspend fun preview(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>
    ): AssessmentPreview = assessments.preview(id, body)
}
```

- [ ] **Paso 5: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.stakeholder.AssessmentInputTest'
```

Expected: `BUILD SUCCESSFUL`; 2 pruebas en verde.

- [ ] **Paso 6: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.stakeholder.StakeholderAssessmentApiTest'
```

Expected: `BUILD SUCCESSFUL`; 7 pruebas en verde.

- [ ] **Paso 7: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/stakeholder/AssessmentInputTest.kt src/test/kotlin/sgspe/stakeholder/StakeholderAssessmentApiTest.kt src/main/kotlin/sgspe/stakeholder/Assessments.kt src/main/kotlin/sgspe/stakeholder/AssessmentController.kt
git commit -m "$(cat <<'MSG'
feat(stakeholder): registro y vista previa de evaluaciones

El endpoint calcula índices, grupo y prioridad, guarda la evaluación con motivo, aplica assess con
la marca y actualiza los derivados. Una evaluación atrasada no pisa los vigentes. Idempotency-Key
se acepta y no deduplica en F2.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 13: Seguimiento de grupos de interés (JOB-03)

JOB-03 (`docs/workflows.md`): cada madrugada, por organización y con candado de clúster, escribe
`last_contact_date` de cada grupo de interés con la fecha de la última interacción realizada que lo vincula
(`engagement_stakeholder`), con el motivo `StakeholderFollowUpJob: último contacto`. La lógica de fechas es pura
(`FollowUp`).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/FollowUpTest.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/StakeholderFollowUpJobApiTest.kt`

**Interfaces:**
- Consume: `Records.asPlatform`, `Records.relatedAsPlatform`, `Criteria`, `Values` (Tarea 2); `Organizations` (Tarea 8).
- Produce: `StakeholderFollowUpJob.runOnce(): Int?`; `StakeholderFollowUpSchedule` con `sgspe.jobs.stakeholder-follow-up.cron` (`0 30 4 * * *`).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/stakeholder/FollowUpTest.kt` (3 pruebas):

```kotlin
package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

// JOB-03: last_contact_date y la regla de primer contacto (ASM-032), sin tocar un estado que una persona cambió
class FollowUpTest {
    private val may = LocalDate.parse("2026-05-12")

    @Test
    fun `el primer contacto pasa de SIN CONTACTO o vacio a CONTACTO ESTABLECIDO`() {
        assertEquals(
            mapOf("last_contact_date" to "2026-05-12", "relationship_status" to "CONTACTO ESTABLECIDO"),
            FollowUp.changes(mapOf("relationship_status" to "SIN CONTACTO"), may)
        )
        assertEquals("CONTACTO ESTABLECIDO", FollowUp.changes(mapOf("relationship_status" to null), may)["relationship_status"])
    }

    @Test
    fun `un estado que cambio una persona no se toca, y sin cambio no se escribe nada`() {
        assertEquals(mapOf("last_contact_date" to "2026-05-12"), FollowUp.changes(mapOf("relationship_status" to "RELACION DETERIORADA"), may))
        assertEquals(
            mapOf("last_contact_date" to "2026-05-12"),
            FollowUp.changes(
                mapOf(
                    "last_contact_date" to "2026-04-01",
                    "relationship_status" to "SIN CONTACTO"
                ),
                may
            )
        )
        assertEquals(emptyMap<String, Any?>(), FollowUp.changes(mapOf("last_contact_date" to "2026-05-12"), may))
    }

    @Test
    fun `la fecha es la de Lima`() {
        assertEquals(LocalDate.parse("2026-05-11"), FollowUp.dateOf("2026-05-12T04:59:00Z"))
        assertEquals(LocalDate.parse("2026-05-12"), FollowUp.dateOf("2026-05-12T05:00:00Z"))
    }
}
```

Crear `src/test/kotlin/sgspe/stakeholder/StakeholderFollowUpJobApiTest.kt` (1 pruebas):

```kotlin
package sgspe.stakeholder

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.DomainApiTest

// JOB-03 (workflows.md §Pruebas «Trabajos»): last_contact_date y el primer contacto, sin mover WF-B; el motivo nombra al
// trabajo; repetir la corrida no escribe nada
class StakeholderFollowUpJobApiTest : DomainApiTest() {
    @Autowired
    private lateinit var job: StakeholderFollowUpJob

    @Test
    fun `una interaccion realizada da el ultimo contacto y el primer contacto, y la segunda corrida no escribe`() {
        val project = projectWithCrs()
        val linked = stakeholder(project, mapOf("relationship_status" to "SIN CONTACTO"))
        val attendee = stakeholder(project)
        val invited = stakeholder(project)
        val engagement = engagement(project, "REUNION", attributes = mapOf("held_at" to "2026-05-12T15:00:00Z"))
        link("engagement", engagement, "engagement_stakeholder", linked)
        participant(engagement, attributes = mapOf("stakeholder" to attendee))
        participant(engagement, attributes = mapOf("stakeholder" to invited, "attended" to false))
        move("engagement", engagement, "register_held_unplanned")
        val first = runBlocking { job.runOnce() }
        assertTrue(first!! >= 2)
        val linkedNow = record("stakeholder", linked)
        assertEquals("2026-05-12", linkedNow["attributes"]["last_contact_date"].asString())
        assertEquals("CONTACTO ESTABLECIDO", linkedNow["attributes"]["relationship_status"].asString())
        assertEquals("identified", linkedNow["state"].asString())
        assertEquals("2026-05-12", record("stakeholder", attendee)["attributes"]["last_contact_date"].asString())
        assertTrue(record("stakeholder", invited)["attributes"]["last_contact_date"].isNull)
        val history = tree(send("GET", "/api/objects/stakeholder/records/$linked/history", null, org.springframework.http.HttpStatus.OK))
        assertEquals(StakeholderFollowUpJob.REASON, history.values().first()["reason"].asString())
        val before = history.size()
        runBlocking { job.runOnce() }
        assertEquals(before, tree(send("GET", "/api/objects/stakeholder/records/$linked/history", null, org.springframework.http.HttpStatus.OK)).size())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.stakeholder.FollowUpTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'FollowUp'`).

- [ ] **Paso 3: Crear `StakeholderFollowUpJob.kt`**

Crear `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt`:

```kotlin
package sgspe.stakeholder

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.Organizations
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.platform.ClusterLock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// lo que el seguimiento calcula de un grupo de interés, puro para las pruebas unitarias (domain-model §7.2 y §7.4)
object FollowUp {
    // los estados de una interacción realizada: la regla de last_contact_date
    val HELD = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved", "closed")
    const val NO_CONTACT = "SIN CONTACTO"
    const val CONTACT = "CONTACTO ESTABLECIDO"

    // los cambios del grupo de interés: last_contact_date si cambió y, si pasa de vacío a una fecha, la regla de primer
    // contacto de relationship_status (SIN CONTACTO, o vacío porque wasichai no aplica el valor por defecto, UPS-15, pasa a
    // CONTACTO ESTABLECIDO). un estado que una persona ya cambió no se toca (ASM-032)
    fun changes(
        stakeholder: Map<String, Any?>,
        lastContact: LocalDate?
    ): Map<String, Any?> {
        val stored = Values.text(stakeholder["last_contact_date"])
        val next = lastContact?.toString()
        if (stored == next) return emptyMap()
        val changes = mutableMapOf<String, Any?>("last_contact_date" to next)
        val status = Values.text(stakeholder["relationship_status"])
        if (stored == null && next != null && (status == null || status == NO_CONTACT)) changes["relationship_status"] = CONTACT
        return changes
    }

    fun dateOf(heldAt: Any?): LocalDate? = Values.text(heldAt)?.let { Instant.parse(it).atZone(LIMA).toLocalDate() }
}

// JOB-03 StakeholderFollowUpJob (workflows.md): una vez al día, como la plataforma de cada organización, mantiene
// last_contact_date de cada grupo de interés (la fecha en Lima del held_at más reciente de sus interacciones realizadas,
// vinculadas por engagement_stakeholder o por un participante con attended) y aplica la regla de primer contacto. no
// usa listener, para que cerrar una interacción no exija escribir el grupo de interés. nunca mueve WF-B. las tareas de
// evaluación periódica y de inactividad llegan en F3, con `task`
@Component
class StakeholderFollowUpJob(
    private val records: Records,
    private val organizations: Organizations,
    private val locks: ClusterLock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use {
            var written = 0
            for (organization in organizations.having(STAKEHOLDER)) {
                try {
                    written += records.asPlatform(organization) { followUp(organization) }
                } catch (e: Exception) {
                    log.error("StakeholderFollowUpJob falló en la organización {}: {}", organization, e.message)
                }
            }
            written
        }
    }

    private suspend fun followUp(organization: UUID): Int {
        val contacts = mutableMapOf<String, LocalDate>()

        fun touch(
            stakeholder: String?,
            date: LocalDate
        ) {
            if (stakeholder == null) return
            contacts.merge(stakeholder, date) { a, b -> maxOf(a, b) }
        }
        for (engagement in records.all(ENGAGEMENT, criteria = listOf(Criteria.inState(FollowUp.HELD)))) {
            val date = FollowUp.dateOf(engagement.attributes["held_at"]) ?: continue
            val id = UUID.fromString(engagement.id)
            records.relatedAsPlatform(organization, ENGAGEMENT, id, "engagement_stakeholder").forEach { touch(it.id.toString(), date) }
            records
                .all("engagement_participant", filters = mapOf("engagement" to engagement.id, "attended" to "true"))
                .forEach { touch(Values.text(it.attributes["stakeholder"]), date) }
        }
        var written = 0
        for (stakeholder in records.all(STAKEHOLDER)) {
            val changes = FollowUp.changes(stakeholder.attributes, contacts[stakeholder.id])
            if (changes.isEmpty()) continue
            records.patch(STAKEHOLDER, UUID.fromString(stakeholder.id), changes, REASON)
            written++
        }
        return written
    }

    companion object {
        const val LOCK = "sgspe.stakeholder-follow-up"
        const val REASON = "StakeholderFollowUpJob: último contacto"
        const val STAKEHOLDER = "stakeholder"
        const val ENGAGEMENT = "engagement"
    }
}

@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class StakeholderFollowUpSchedule(
    private val job: StakeholderFollowUpJob
) {
    // una vez al día, configurable (ASM-022)
    @Scheduled(cron = "\${sgspe.jobs.stakeholder-follow-up.cron:0 30 4 * * *}", zone = "America/Lima")
    suspend fun daily() {
        job.runOnce()
    }
}
```

- [ ] **Paso 4: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.stakeholder.FollowUpTest'
```

Expected: `BUILD SUCCESSFUL`; 3 pruebas en verde.

- [ ] **Paso 5: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.stakeholder.StakeholderFollowUpJobApiTest'
```

Expected: `BUILD SUCCESSFUL`; 1 pruebas en verde.

- [ ] **Paso 6: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/stakeholder/FollowUpTest.kt src/test/kotlin/sgspe/stakeholder/StakeholderFollowUpJobApiTest.kt src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt
git commit -m "$(cat <<'MSG'
feat(stakeholder): JOB-03 escribe el último contacto

Cada madrugada, por organización y con candado, last_contact_date toma la fecha de la última
interacción realizada que vincula al grupo de interés.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 14: CRS y cuentas geográficas con PostGIS

`docs/gis.md` §2.2 y §3: los valores del catálogo de CRS (`EPSG 4326`, `EPSG 32717` a `EPSG 32719` de WGS 84 UTM sur
y `EPSG 24877` a `EPSG 24879` de PSAD56 UTM sur, estos solo como origen) se traducen a SRID; las cuentas (transformar a EPSG:4326, validez, cambio de tipo MULTI*, área en
hectáreas en el CRS de trabajo, centroide y su zona UTM, distancia entre versiones y coordenadas UTM de origen) las hace
PostGIS con SQL parametrizado, nunca Kotlin.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/territory/Crs.kt`
- Crear: `src/main/kotlin/sgspe/territory/GeoSql.kt`
- Probar: `src/test/kotlin/sgspe/territory/CrsTest.kt`
- Probar: `src/test/kotlin/sgspe/territory/GeoSqlApiTest.kt`

**Interfaces:**
- Consume: `DatabaseClient` de Spring R2DBC.
- Produce:
  - `Crs.srid(value): Int?`, `value(srid)`, `zone(srid)`, `isUtm(srid)`, `WGS84`, `WORKING_ZONES`.
  - `GeoSql`: `toWgs84(geojson, srid)`, `pointToWgs84(x, y, srid)`, `validity(geojson): Validity`, `asType(geojson,
    target)`, `areaHa(geojson, srid)`, `centroid(geojson)`, `centroidZone(geojson)`, `distanceM(a, b, srid)`,
    `utm(geojson, srid): UtmPoint?`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/CrsTest.kt` (3 pruebas):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// gis.md §3: los valores del catálogo y sus SRID, y la zona del centroide (ASM-064)
class CrsTest {
    @Test
    fun `los valores del catalogo y su srid`() {
        assertEquals(32717, Crs.srid("EPSG 32717"))
        assertEquals(24879, Crs.srid("EPSG 24879"))
        assertEquals(4326, Crs.srid("EPSG 4326"))
        assertNull(Crs.srid("EPSG 3857"))
        assertNull(Crs.srid(null))
        assertEquals("EPSG 32718", Crs.value(32718))
    }

    @Test
    fun `la zona de un CRS UTM, WGS 84 o PSAD56`() {
        assertEquals(17, Crs.zone(32717))
        assertEquals(19, Crs.zone(24879))
        assertNull(Crs.zone(4326))
    }

    @Test
    fun `zona del centroide en el Peru, y nada fuera de 17S a 19S o al norte del ecuador`() {
        assertEquals(32717, GeoSql.zoneOf(-78.5, -7.9))
        assertEquals(32718, GeoSql.zoneOf(-75.0, -12.0))
        assertEquals(32719, GeoSql.zoneOf(-70.0, -15.5))
        assertNull(GeoSql.zoneOf(-60.0, -15.0))
        assertNull(GeoSql.zoneOf(-78.5, 1.0))
    }
}
```

Crear `src/test/kotlin/sgspe/territory/GeoSqlApiTest.kt` (4 pruebas):

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.DomainApiTest
import java.math.BigDecimal

// gis.md §9 contra PostGIS real, con geometrías sintéticas (RQ-GIS-021): ida y vuelta UTM ↔ 4326, área de un cuadrado,
// zona del centroide, PSAD56 y validez
class GeoSqlApiTest : DomainApiTest() {
    @Autowired
    private lateinit var geo: GeoSql

    @Test
    fun `ida y vuelta UTM y 4326 en 17S, 18S y 19S con error de 1 cm o menos`() {
        runBlocking {
            for ((srid, easting, northing) in listOf(
                Triple(32717, 812345.678, 9123456.789),
                Triple(32718, 500000.0, 8800000.0),
                Triple(32719, 300000.5, 8300000.25)
            )) {
                val wgs84 = geo.pointToWgs84(BigDecimal(easting), BigDecimal(northing), srid)
                val back = geo.utm(wgs84, srid)!!
                assertTrue((back.easting - BigDecimal(easting)).abs() <= BigDecimal("0.01"), "$srid este ${back.easting}")
                assertTrue((back.northing - BigDecimal(northing)).abs() <= BigDecimal("0.01"), "$srid norte ${back.northing}")
            }
        }
    }

    @Test
    fun `un cuadrado de 1 km por lado en UTM da 100 ha de area plana`() {
        runBlocking {
            val ring = listOf(listOf(500000, 8800000), listOf(501000, 8800000), listOf(501000, 8801000), listOf(500000, 8801000), listOf(500000, 8800000))
            val square = geo.toWgs84(mapOf("type" to "Polygon", "coordinates" to listOf(ring)), 32718)
            assertEquals(BigDecimal("100.00"), geo.areaHa(square, 32718))
            assertEquals(32718, geo.centroidZone(square))
            assertNull(geo.areaHa(point(-75.0, -12.0), 32718))
        }
    }

    @Test
    fun `el mismo numero leido como PSAD56 cae a cientos de metros que como WGS 84`() {
        runBlocking {
            val psad = geo.pointToWgs84(BigDecimal(812345), BigDecimal(9123456), 24877)
            val wgs = geo.pointToWgs84(BigDecimal(812345), BigDecimal(9123456), 32717)
            val shift = geo.distanceM(psad, wgs, 32717)
            assertTrue(shift > BigDecimal(100) && shift < BigDecimal(1000), "desplazamiento $shift m")
        }
    }

    @Test
    fun `un lazo es invalido con su motivo, y los tipos se normalizan sin perder partes`() {
        runBlocking {
            val bowtie =
                mapOf(
                    "type" to "Polygon",
                    "coordinates" to
                        listOf(listOf(listOf(-75.0, -12.0), listOf(-74.99, -11.99), listOf(-74.99, -12.0), listOf(-75.0, -11.99), listOf(-75.0, -12.0)))
                )
            val validity = geo.validity(bowtie)
            assertFalse(validity.valid)
            assertTrue(validity.reason!!.contains("Self-intersection"), validity.reason)
            val single = square(-75.0, -12.0, 0.01, "MultiPolygon")
            assertEquals("Polygon", geo.asType(single, "Polygon")!!["type"])
            assertEquals("MultiPolygon", geo.asType(square(-75.0, -12.0, 0.01), "MultiPolygon")!!["type"])
            val two =
                mapOf(
                    "type" to "MultiPolygon",
                    "coordinates" to
                        (
                            square(
                                -75.0,
                                -12.0,
                                0.01,
                                "MultiPolygon"
                            )["coordinates"] as List<*> + square(-74.9, -12.0, 0.01, "MultiPolygon")["coordinates"] as List<*>
                        )
                )
            assertNull(geo.asType(two, "Polygon"))
        }
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.territory.CrsTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'Crs'`).

- [ ] **Paso 3: Crear `Crs.kt`**

Crear `src/main/kotlin/sgspe/territory/Crs.kt`:

```kotlin
package sgspe.territory

// los CRS de SGSPE (gis.md §3, ASM-002, ASM-064): los valores ENUM "EPSG 32717" de los catálogos "CRS de trabajo" y "CRS
// de origen" y su SRID. 327xx son WGS 84 UTM sur; 248xx, PSAD56 UTM sur, solo como origen al importar (RQ-GIS-007)
object Crs {
    const val WGS84 = 4326
    val WORKING_ZONES = setOf(32717, 32718, 32719)

    // "EPSG 32717" -> 32717; null si no es un valor del catálogo
    fun srid(value: String?): Int? =
        value
            ?.trim()
            ?.removePrefix("EPSG")
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in KNOWN }

    fun value(srid: Int): String = "EPSG $srid"

    // la zona UTM (17, 18 o 19) de un SRID UTM sur, WGS 84 o PSAD56; null para 4326
    fun zone(srid: Int): Int? =
        when (srid) {
            in 32701..32760 -> srid - 32700
            in 24877..24879 -> srid - 24860
            else -> null
        }

    fun isUtm(srid: Int): Boolean = zone(srid) != null

    private val KNOWN = setOf(32717, 32718, 32719, 24877, 24878, 24879, WGS84)
}
```

- [ ] **Paso 4: Crear `GeoSql.kt`**

Crear `src/main/kotlin/sgspe/territory/GeoSql.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.util.Optional

data class Validity(
    val valid: Boolean,
    val reason: String?
)

data class UtmPoint(
    val easting: BigDecimal,
    val northing: BigDecimal
)

// las cuentas geométricas de sgspe.territory con PostGIS, sobre GeoJSON y sin tocar ninguna tabla: transformar desde el
// CRS de origen, validar, normalizar el tipo, medir áreas en ha y distancias en metros en un CRS UTM (gis.md §3 y §4). el
// almacenamiento sigue siendo de wasichai-gis (SRID 4326, 2D); nunca se calcula un área sobre grados
@Component
class GeoSql(
    private val db: DatabaseClient
) {
    // GeoJSON en `srid` -> GeoJSON EPSG:4326, en 2D
    suspend fun toWgs84(
        geojson: Map<String, Any?>,
        srid: Int
    ): Map<String, Any?> = geometry("SELECT ST_AsGeoJSON(ST_Force2D(ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid), 4326)), 9) AS out", geojson, srid)

    suspend fun pointToWgs84(
        x: BigDecimal,
        y: BigDecimal,
        srid: Int
    ): Map<String, Any?> =
        read(
            db
                .sql("SELECT ST_AsGeoJSON(ST_Transform(ST_SetSRID(ST_MakePoint(:x, :y), :srid), 4326), 9) AS out")
                .bind("x", x.toDouble())
                .bind("y", y.toDouble())
                .bind("srid", srid)
        )

    suspend fun validity(geojson: Map<String, Any?>): Validity =
        db
            .sql("SELECT ST_IsValid(g) AS valid, ST_IsValidReason(g) AS reason FROM (SELECT ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326) AS g) s")
            .bind("g", json(geojson))
            .map { row, _ ->
                val valid = row.get("valid", java.lang.Boolean::class.java)?.booleanValue() == true
                Validity(valid, if (valid) null else row.get("reason", String::class.java))
            }.one()
            .awaitSingleOrNull() ?: Validity(false, "geometría vacía")

    // al tipo del campo destino (gis.md §4.1): un MultiPolygon de una sola parte hacia POLYGON pasa a Polygon, y un
    // Polygon hacia MULTIPOLYGON se envuelve; lo mismo con puntos y líneas. null si no se puede sin perder partes
    suspend fun asType(
        geojson: Map<String, Any?>,
        target: String
    ): Map<String, Any?>? {
        val type = geojson["type"]?.toString() ?: return null
        if (type.equals(target, ignoreCase = true)) return geojson
        val multi = target.startsWith("Multi")
        if (multi && type.equals(target.removePrefix("Multi"), ignoreCase = true)) {
            return geometry("SELECT ST_AsGeoJSON(ST_Multi(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid)), 9) AS out", geojson, Crs.WGS84)
        }
        if (!multi && type.equals("Multi$target", ignoreCase = true)) {
            val parts =
                db
                    .sql("SELECT ST_NumGeometries(ST_GeomFromGeoJSON(:g)) AS n")
                    .bind("g", json(geojson))
                    .map { row, _ -> (row.get("n") as Number?)?.toInt() ?: 0 }
                    .one()
                    .awaitSingleOrNull() ?: 0
            if (parts != 1) return null
            return geometry("SELECT ST_AsGeoJSON(ST_GeometryN(ST_SetSRID(ST_GeomFromGeoJSON(:g), :srid), 1), 9) AS out", geojson, Crs.WGS84)
        }
        return null
    }

    // área plana en ha, redondeada a dos decimales (RQ-GIS-009, ASM-057); null para puntos y líneas
    suspend fun areaHa(
        geojson: Map<String, Any?>,
        srid: Int
    ): BigDecimal? =
        db
            .sql(
                "SELECT CASE WHEN ST_Dimension(g) = 2 THEN round((ST_Area(ST_Transform(g, :srid)) / 10000)::numeric, 2) END AS ha " +
                    "FROM (SELECT ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326) AS g) s"
            ).bind("g", json(geojson))
            .bind("srid", srid)
            .map { row, _ -> Optional.ofNullable(row.get("ha", BigDecimal::class.java)) }
            .one()
            .awaitSingleOrNull()
            ?.orElse(null)

    // el centroide en grados: (longitud, latitud)
    suspend fun centroid(geojson: Map<String, Any?>): Pair<Double, Double>? =
        db
            .sql("SELECT ST_X(c) AS x, ST_Y(c) AS y FROM (SELECT ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326)) AS c) s")
            .bind("g", json(geojson))
            .map { row, _ -> (row.get("x") as Number).toDouble() to (row.get("y") as Number).toDouble() }
            .one()
            .awaitSingleOrNull()

    // la zona UTM sur WGS 84 del centroide (ASM-064): 32717, 32718 o 32719 en el Perú. fuera de esas zonas o al norte
    // del ecuador, null: no se calcula con una zona inesperada (gis.md §3.3)
    suspend fun centroidZone(geojson: Map<String, Any?>): Int? = centroid(geojson)?.let { (x, y) -> zoneOf(x, y) }

    // distancia de Hausdorff en metros entre dos geometrías EPSG:4326, medida en `srid`: para dos puntos es su
    // distancia; para líneas y polígonos, el mayor desplazamiento (gis.md §4.4)
    suspend fun distanceM(
        a: Map<String, Any?>,
        b: Map<String, Any?>,
        srid: Int
    ): BigDecimal =
        db
            .sql(
                "SELECT round(ST_HausdorffDistance(ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:a), 4326), :srid), " +
                    "ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:b), 4326), :srid))::numeric, 3) AS d"
            ).bind("a", json(a))
            .bind("b", json(b))
            .bind("srid", srid)
            .map { row, _ -> Optional.ofNullable(row.get("d", BigDecimal::class.java)) }
            .one()
            .awaitSingleOrNull()
            ?.orElse(null) ?: BigDecimal.ZERO

    // el punto (o el centroide) en un CRS UTM, para el reporte de diferencias
    suspend fun utm(
        geojson: Map<String, Any?>,
        srid: Int
    ): UtmPoint? =
        db
            .sql(
                "SELECT round(ST_X(p)::numeric, 3) AS e, round(ST_Y(p)::numeric, 3) AS n " +
                    "FROM (SELECT ST_Transform(ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326)), :srid) AS p) s"
            ).bind("g", json(geojson))
            .bind("srid", srid)
            .map { row, _ -> UtmPoint(row.get("e", BigDecimal::class.java)!!, row.get("n", BigDecimal::class.java)!!) }
            .one()
            .awaitSingleOrNull()

    private suspend fun geometry(
        sql: String,
        geojson: Map<String, Any?>,
        srid: Int
    ): Map<String, Any?> = read(db.sql(sql).bind("g", json(geojson)).bind("srid", srid))

    @Suppress("UNCHECKED_CAST")
    private suspend fun read(spec: DatabaseClient.GenericExecuteSpec): Map<String, Any?> {
        val out =
            spec
                .map { row, _ -> Optional.ofNullable(row.get("out", String::class.java)) }
                .one()
                .awaitSingleOrNull()
                ?.orElse(null) ?: error("PostGIS no devolvió la geometría")
        return JSON.readValue(out, Map::class.java) as Map<String, Any?>
    }

    private fun json(geojson: Map<String, Any?>): String = JSON.writeValueAsString(geojson)

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()

        // pura: la zona de un punto en grados, en el hemisferio sur y en las zonas de trabajo
        fun zoneOf(
            longitude: Double,
            latitude: Double
        ): Int? {
            if (latitude > 0) return null
            val zone = kotlin.math.floor((longitude + 180) / 6).toInt() + 1
            return (32700 + zone).takeIf { it in Crs.WORKING_ZONES }
        }
    }
}
```

- [ ] **Paso 5: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.territory.CrsTest'
```

Expected: `BUILD SUCCESSFUL`; 3 pruebas en verde.

- [ ] **Paso 6: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.GeoSqlApiTest'
```

Expected: `BUILD SUCCESSFUL`; 4 pruebas en verde.

- [ ] **Paso 7: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/territory/CrsTest.kt src/test/kotlin/sgspe/territory/GeoSqlApiTest.kt src/main/kotlin/sgspe/territory/Crs.kt src/main/kotlin/sgspe/territory/GeoSql.kt
git commit -m "$(cat <<'MSG'
feat(territory): CRS del catálogo y cuentas con PostGIS

Crs traduce el catálogo a SRID; GeoSql transforma, valida, cambia el tipo MULTI*, mide áreas en
el CRS de trabajo y distancias con SQL parametrizado.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 15: Área oficial en hectáreas y miembros congelados

`calculated_area_ha` de `area_of_influence`, `community`, `land_parcel` y `mining_concession` es el área en el CRS
de trabajo del proyecto (`docs/gis.md` §4, RN-TER-006; sin proyecto o sin `working_crs`, la zona UTM del centroide).
`AreaListener` la recalcula tras cada alta o cambio, con la marca, y `AreaRecalculator` la corrige en JOB-02. El listener
escribe el mismo registro dentro de la petición del cliente: con `If-Match` en esa petición el área se escribe igual
(Review Focus 2). `TerritoryWriteGuard` congela los miembros de un área de influencia que no está en `draft` (409
`FROZEN_MEMBERS`, RN-TER-004).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/territory/Areas.kt`
- Crear: `src/main/kotlin/sgspe/territory/TerritoryWriteGuard.kt`
- Probar: `src/test/kotlin/sgspe/territory/AreaApiTest.kt`

**Interfaces:**
- Consume: `GeoSql`, `Crs` (Tarea 14); `Records`, `Values`, `WriteMark`, `Criteria` (Tarea 2); `DerivedRecalculator` (Tarea 8); `RuleGuard`, `WriteView` (Tarea 3).
- Produce: `Areas.compute(objectName, record)` y `refresh(objectName, id)`; `AreaListener`; `AreaRecalculator`; `TerritoryWriteGuard`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/AreaApiTest.kt` (4 pruebas):

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.DerivedFieldsJob
import java.math.BigDecimal

// gis.md §9 «Área derivada»: cambiar la geometría por la API genérica recalcula calculated_area_ha (AreaListener); si el
// valor quedó mal, DerivedFieldsJob lo corrige (JOB-02); el motivo dice qué regla lo escribió
class AreaApiTest : DomainApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    @Test
    fun `el area de una comunidad sale de su territorio en la zona del centroide`() {
        val community = createWith("community", mapOf("name" to "Comunidad Ejemplo"), mapOf("territory" to square(-78.6, -7.9, 0.01, "MultiPolygon")))
        val area = record("community", community)["attributes"]["calculated_area_ha"].decimalValue()
        assertTrue(area > BigDecimal(100) && area < BigDecimal(150), "área $area")
        edit("community", community, emptyMap(), geometries = mapOf("territory" to square(-78.6, -7.9, 0.02, "MultiPolygon")))
        val bigger = record("community", community)["attributes"]["calculated_area_ha"].decimalValue()
        assertTrue(bigger > area * BigDecimal(3), "área $bigger")
        val history = tree(send("GET", "/api/objects/community/records/$community/history", null, HttpStatus.OK))
        assertEquals("derivado: área en ha", history.values().first()["reason"].asString())
    }

    @Test
    fun `el area de un area de influencia usa el working_crs del proyecto y el trabajo corrige lo pendiente`() {
        val project = projectWithCrs()
        val area =
            createWith(
                "area_of_influence",
                mapOf("project" to project, "area_type" to "AIDS", "version_number" to 1),
                mapOf("boundary" to square(-78.6, -7.9, 0.01, "MultiPolygon"))
            )
        val computed = record("area_of_influence", area)["attributes"]["calculated_area_ha"].decimalValue()
        assertTrue(computed > BigDecimal(100))
        runBlocking { job.runOnce(full = true) }
        assertEquals(computed, record("area_of_influence", area)["attributes"]["calculated_area_ha"].decimalValue())
    }

    @Test
    fun `un PUT con If-Match tambien recalcula el area, porque la escritura del listener no es la del cliente`() {
        val community = createWith("community", mapOf("name" to "Comunidad Ejemplo"), mapOf("territory" to square(-78.6, -7.9, 0.01, "MultiPolygon")))
        val read = record("community", community)["updatedAt"].asString()
        val response =
            edit(
                "community",
                community,
                emptyMap(),
                headers = mapOf(sgspe.common.IfMatch.HEADER to "\"$read\""),
                geometries = mapOf("territory" to square(-78.6, -7.9, 0.02, "MultiPolygon"))
            )
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertTrue(record("community", community)["attributes"]["calculated_area_ha"].decimalValue() > BigDecimal(400))
    }

    @Test
    fun `el updatedAt de la respuesta del alta sigue valiendo como If-Match aunque el listener escribio el area`() {
        val created =
            tree(
                send(
                    "POST",
                    "/api/objects/community/records",
                    mapOf(
                        "attributes" to mapOf("name" to "Comunidad Ejemplo"),
                        "geometries" to mapOf("territory" to square(-78.6, -7.9, 0.01, "MultiPolygon"))
                    ),
                    HttpStatus.CREATED
                )
            )
        val community = created["id"].asString()
        val stale = created["updatedAt"].asString()
        assertTrue(stale != record("community", community)["updatedAt"].asString(), "el listener debió escribir el área")
        val response = edit("community", community, mapOf("registered_members_count" to 40), headers = mapOf(sgspe.common.IfMatch.HEADER to "\"$stale\""))
        assertEquals(HttpStatus.OK, response.status, response.body)
        val again = edit("community", community, mapOf("registered_members_count" to 41), headers = mapOf(sgspe.common.IfMatch.HEADER to "\"$stale\""))
        assertEquals(HttpStatus.PRECONDITION_FAILED, again.status, again.body)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.AreaApiTest'
```

Expected: FAIL: `calculated_area_ha` queda vacío.

- [ ] **Paso 3: Crear `Areas.kt`**

Crear `src/main/kotlin/sgspe/territory/Areas.kt`:

```kotlin
package sgspe.territory

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.WriteMark
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// el área oficial en ha (RQ-GIS-009, ASM-057, ASM-064): el área plana de la geometría, en el working_crs del proyecto
// para area_of_influence, y en la zona UTM del centroide para community, land_parcel y mining_concession, que no
// pertenecen a un solo proyecto. un área de influencia de un proyecto sin working_crs usa también la zona del centroide;
// un centroide fuera de las zonas 17S a 19S deja el área vacía
@Component
class Areas(
    private val records: Records,
    private val geo: GeoSql
) {
    suspend fun compute(
        objectName: String,
        record: RecordResponse
    ): BigDecimal? {
        val field = GEOMETRY[objectName] ?: return null
        val geometry = Records.geometriesOf(record)[field] ?: return null
        val srid = (if (objectName == AREA_OF_INFLUENCE) projectSrid(record) else null) ?: geo.centroidZone(geometry) ?: return null
        return geo.areaHa(geometry, srid)
    }

    // recalcula y escribe calculated_area_ha si cambió, con la marca y el motivo «derivado: área en ha»
    suspend fun refresh(
        objectName: String,
        id: UUID
    ): Boolean {
        val record = records.find(objectName, id) ?: return false
        val area = compute(objectName, record)
        if (Values.same(record.attributes[FIELD], area)) return false
        records.patch(objectName, id, mapOf(FIELD to area), REASON)
        return true
    }

    private suspend fun projectSrid(record: RecordResponse): Int? {
        val project = Records.uuid(record.attributes["project"]) ?: return null
        return Crs.srid(Values.text(records.find("project", project)?.attributes?.get("working_crs")))
    }

    companion object {
        const val FIELD = "calculated_area_ha"
        const val REASON = "derivado: área en ha"
        const val AREA_OF_INFLUENCE = "area_of_influence"
        val GEOMETRY = mapOf(AREA_OF_INFLUENCE to "boundary", "community" to "territory", "land_parcel" to "boundary", "mining_concession" to "boundary")
    }
}

// AreaListener (domain-model §7.5): cuando la API genérica crea o cambia uno de esos objetos, relee la geometría como quien
// escribió y escribe el área. las guardas y los listeners no ven geometrías (WAS-ADR-0040), por eso relee. si no puede,
// registra el error y deja el valor a DerivedFieldsJob; las escrituras con la marca (importaciones, el propio listener)
// ya traen su área
@Component
@Order(110)
class AreaListener(
    private val areas: Areas
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName !in Areas.GEOMETRY) return
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return
        if (change.after
                .orEmpty()
                .keys
                .any { it.startsWith("rel:") } || WriteMark.present()
        ) {
            return
        }
        try {
            areas.refresh(change.objectName, change.recordId)
        } catch (e: Exception) {
            log.warn("AreaListener: no se actualizó el área de {} {}: {}", change.objectName, change.recordId, e.message)
        }
    }
}

// el recalculador de JOB-02 para el área: los registros cambiados desde la vuelta anterior, o todos en la diaria
@Component
class AreaRecalculator(
    private val records: Records,
    private val areas: Areas
) : DerivedRecalculator {
    override val sourceObject: String = "community"

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        var written = 0
        for (objectName in Areas.GEOMETRY.keys) {
            val criteria = since?.let { listOf(Criteria.updatedSince(it)) }.orEmpty()
            for (record in records.all(objectName, criteria = criteria)) {
                if (areas.refresh(objectName, UUID.fromString(record.id))) written++
            }
        }
        return written
    }
}
```

- [ ] **Paso 4: Crear `TerritoryWriteGuard.kt`**

Crear `src/main/kotlin/sgspe/territory/TerritoryWriteGuard.kt`:

```kotlin
package sgspe.territory

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.WriteView
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// los miembros de una versión `approved` o `superseded` de un área de influencia no cambian (RN-TER-003): una
// corrección es una versión nueva. vale para altas, cambios y bajas, y para un miembro que se mueve de versión
@Component
@Order(GuardOrder.DOMAIN)
class TerritoryWriteGuard(
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != MEMBER || change.kind == RecordChangeKind.TRANSITIONED) return emptyList()
        val after = if (change.kind == RecordChangeKind.DELETED) emptyMap() else view.after(change)
        val areas = setOfNotNull(Records.uuid(change.before?.get(AREA_FIELD)), Records.uuid(after[AREA_FIELD]))
        return areas
            .mapNotNull { records.find(Areas.AREA_OF_INFLUENCE, it) }
            .filter { it.state in FROZEN }
            .map { Finding("FROZEN_MEMBERS", "Los miembros de una versión ${it.state} no cambian: cree una versión nueva del área (RN-TER-003)", AREA_FIELD) }
    }

    companion object {
        const val MEMBER = "area_of_influence_member"
        const val AREA_FIELD = "area_of_influence"
        val FROZEN = setOf("approved", "superseded")
    }
}
```

- [ ] **Paso 5: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.AreaApiTest'
```

Expected: `BUILD SUCCESSFUL`; 4 pruebas en verde.

- [ ] **Paso 6: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/territory/AreaApiTest.kt src/main/kotlin/sgspe/territory/Areas.kt src/main/kotlin/sgspe/territory/TerritoryWriteGuard.kt
git commit -m "$(cat <<'MSG'
feat(territory): área oficial en ha y miembros congelados

AreaListener y AreaRecalculator mantienen calculated_area_ha en el CRS de trabajo del proyecto.
Los miembros de un área de influencia fuera de draft no cambian.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 16: Aprobación de áreas de influencia (WF-O)

`POST /api/sgspe/areas-of-influence/{id}/approve` (`docs/api.md` §4.3): `If-Match` obligatorio, motivo
obligatorio, `UPDATE` y `APPROVE` sobre `area_of_influence` y un rol de la transición. En una transacción con candado por
proyecto y tipo: la versión en `draft` con al menos un miembro pasa a `approved` y la anterior aprobada del mismo proyecto
y tipo a `superseded`, las dos con la marca (`CommandTransitionGuard` rechaza esas transiciones por la API genérica). Dos
aprobaciones simultáneas dejan una sola versión aprobada (Review Focus 4).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/territory/AreasOfInfluence.kt`
- Probar: `src/test/kotlin/sgspe/territory/AreaOfInfluenceWorkflowGuardsApiTest.kt`

**Interfaces:**
- Consume: `Records.transition`, `Transactions.locked`, `Permissions`, `Callers`, `Criteria.inState` (Tarea 2); `IfMatch` y `Versions` (Tarea 4); `ModelCatalog.transition` (Tarea 1).
- Produce: `POST /api/sgspe/areas-of-influence/{id}/approve` → `AreaApproval(approved, superseded?)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/AreaOfInfluenceWorkflowGuardsApiTest.kt` (6 pruebas):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.IfMatch

// los casos de WF-O que dependen de 2.2 (AreaOfInfluenceWorkflowApiTest): approve por la API genérica se rechaza; el
// endpoint aprueba la nueva y reemplaza la anterior en una transacción; los miembros de una versión aprobada no cambian
class AreaOfInfluenceWorkflowGuardsApiTest : DomainApiTest() {
    private fun version(
        project: String,
        number: Int,
        member: Boolean = true
    ): String {
        val area = createRecord("area_of_influence", mapOf("project" to project, "area_type" to "AIDS", "version_number" to number))
        if (member) {
            createRecord(
                "area_of_influence_member",
                mapOf(
                    "area_of_influence" to area,
                    "locality" to createRecord("locality", mapOf("name" to "Caserío Ejemplo", "locality_type" to "CASERIO")),
                    "member_condition" to "INCLUIDO"
                )
            )
        }
        return area
    }

    private fun approve(
        area: String,
        token: String = this.token,
        ifMatch: String? = record("area_of_influence", area)["updatedAt"].asString(),
        why: String? = "aprobación del diagnóstico"
    ) = exchange(
        "POST",
        "/api/sgspe/areas-of-influence/$area/approve",
        null,
        token,
        listOfNotNull(ifMatch?.let { IfMatch.HEADER to "\"$it\"" }, why?.let { REASON to reason(it) }).toMap()
    )

    @Test
    fun `approve y supersede por la API generica son 403`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val area = version(project, 1)
        assertEquals(HttpStatus.FORBIDDEN, transition("area_of_influence", area, "approve", manager.token).status)
    }

    @Test
    fun `el endpoint aprueba la nueva version y reemplaza la anterior`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val first = version(project, 1)
        val approved = tree(send("POST", "/api/sgspe/areas-of-influence/$first/approve", null, HttpStatus.OK, manager.token, approveHeaders(first)))
        assertEquals("approved", approved["approved"]["state"].asString())
        assertNull(approved["superseded"]?.takeUnless { it.isNull })
        val second = version(project, 2)
        val replaced = tree(approve(second, manager.token).body)
        assertEquals("approved", replaced["approved"]["state"].asString())
        assertEquals(first, replaced["superseded"]["id"].asString())
        assertEquals("superseded", record("area_of_influence", first)["state"].asString())
    }

    @Test
    fun `PROJECT_MANAGER, con u y APPROVE, aprueba sin escribir campos de negocio`() {
        val project = projectWithCrs()
        val manager = member("PROJECT_MANAGER", project)
        val area = version(project, 1)
        val response = approve(area, manager.token)
        assertEquals(HttpStatus.OK, response.status, response.body)
        assertEquals("approved", record("area_of_influence", area)["state"].asString())
    }

    @Test
    fun `sin If-Match 428, con uno viejo 412, sin motivo 400, sin miembros 409`() {
        val project = projectWithCrs()
        val manager = member("SOCIAL_MANAGER", project)
        val area = version(project, 1)
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, approve(area, manager.token, ifMatch = null).status)
        assertEquals(HttpStatus.PRECONDITION_FAILED, approve(area, manager.token, ifMatch = "2020-01-01T00:00:00Z").status)
        assertEquals(HttpStatus.BAD_REQUEST, approve(area, manager.token, why = null).status)
        assertEquals(HttpStatus.CONFLICT, approve(version(project, 2, member = false), manager.token).status)
    }

    @Test
    fun `los miembros de una version aprobada no cambian`() {
        val project = projectWithCrs()
        val area = version(project, 1)
        approve(area)
        val locality = createRecord("locality", mapOf("name" to "Sector Ejemplo", "locality_type" to "SECTOR"))
        val response =
            exchange(
                "POST",
                "/api/objects/area_of_influence_member/records",
                mapOf("attributes" to mapOf("area_of_influence" to area, "locality" to locality, "member_condition" to "INCLUIDO"))
            )
        assertEquals(HttpStatus.CONFLICT, response.status)
        assertEquals(listOf("area_of_influence"), problemFields(response.body))
    }

    @Test
    fun `dos aprobaciones simultaneas del mismo proyecto y tipo dejan una sola version aprobada`() {
        val project = projectWithCrs()
        val first = version(project, 1)
        val second = version(project, 2)
        val results = listOf(first, second).parallelStream().map { approve(it).status }.toList()
        assertEquals(listOf(HttpStatus.OK, HttpStatus.OK), results)
        val states = listOf(first, second).map { record("area_of_influence", it)["state"].asString() }.sorted()
        assertEquals(listOf("approved", "superseded"), states)
    }

    private fun approveHeaders(area: String) =
        mapOf(IfMatch.HEADER to "\"${record("area_of_influence", area)["updatedAt"].asString()}\"", REASON to reason("aprobación del diagnóstico"))
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.AreaOfInfluenceWorkflowGuardsApiTest'
```

Expected: FAIL: `/api/sgspe/areas-of-influence/{id}/approve` responde 404.

- [ ] **Paso 3: Crear `AreasOfInfluence.kt`**

Crear `src/main/kotlin/sgspe/territory/AreasOfInfluence.kt`:

```kotlin
package sgspe.territory

import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import wasichai.core.common.Actions
import wasichai.core.common.ConflictException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.util.UUID

data class AreaApproval(
    val approved: RecordResponse,
    val superseded: RecordResponse?
)

// la aprobación de una versión del área de influencia (RN-TER-003, WF-O, api.md §4.2): en una transacción lleva la nueva
// a `approved` y la aprobada anterior del mismo proyecto y tipo a `superseded`, que se conserva. las dos son transiciones
// de comando: por la API genérica, CommandTransitionGuard las rechaza. doble llave: los roles de la transición y la
// acción declarada APPROVE (RF-WFL-002). exige If-Match y motivo, y al menos un miembro
@Service
class AreasOfInfluence(
    private val records: Records,
    private val versions: Versions,
    private val transactions: Transactions,
    private val permissions: Permissions,
    private val catalog: ModelCatalog,
    private val callers: Callers
) {
    suspend fun approve(
        id: UUID,
        reason: String?,
        ifMatch: String?
    ): AreaApproval {
        val expected = IfMatch.required(ifMatch)
        if (reason.isNullOrBlank()) throw ValidationException("Aprobar un área de influencia exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Aprobar un área de influencia", Actions.UPDATE to AREA, APPROVE to AREA)
        if (!user.isAdmin) {
            for (name in listOf("approve", "supersede")) {
                val roles = catalog.transition(AREA, name)?.roles.orEmpty()
                if (roles.none { it in user.roles }) throw ForbiddenException("La transición $name exige uno de los roles ${roles.joinToString(", ")}")
            }
        }
        return transactions.locked("area_of_influence:$id") {
            val area = records.get(AREA, id)
            versions.check(AREA, id, expected, area.updatedAt, "El área de influencia")
            if (area.state != "draft") throw ConflictException("Solo se aprueba una versión en borrador; esta está en ${area.state}")
            if (records.count(TerritoryWriteGuard.MEMBER, filters = mapOf(TerritoryWriteGuard.AREA_FIELD to id.toString())) == 0L) {
                throw ConflictException("Un área de influencia sin miembros no se aprueba: agregue sus localidades, comunidades o unidades")
            }
            val project = Values.text(area.attributes["project"])!!
            val type = Values.text(area.attributes["area_type"])!!
            transactions.locked("area_of_influence:$project:$type") {
                val previous =
                    records
                        .all(AREA, filters = mapOf("project" to project, "area_type" to type), criteria = listOf(Criteria.inState(setOf("approved"))))
                        .filter { it.id != id.toString() }
                val superseded = previous.map { records.transition(AREA, UUID.fromString(it.id), "supersede", reason) }
                AreaApproval(records.transition(AREA, id, "approve", reason), superseded.firstOrNull())
            }
        }
    }

    companion object {
        const val AREA = "area_of_influence"
        const val APPROVE = "APPROVE"
    }
}

@RestController
@RequestMapping("/api/sgspe/areas-of-influence")
class AreasOfInfluenceController(
    private val areas: AreasOfInfluence
) {
    @PostMapping("/{id}/approve")
    suspend fun approve(
        @PathVariable id: UUID,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?
    ): AreaApproval = areas.approve(id, ChangeReason.fromHeader(reason), ifMatch)
}
```

- [ ] **Paso 4: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.AreaOfInfluenceWorkflowGuardsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 6 pruebas en verde.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/territory/AreaOfInfluenceWorkflowGuardsApiTest.kt src/main/kotlin/sgspe/territory/AreasOfInfluence.kt
git commit -m "$(cat <<'MSG'
feat(territory): aprobación de áreas de influencia con versión única

El comando aprueba la versión en draft y reemplaza la aprobada del mismo proyecto y tipo en una
transacción con candado; exige If-Match, motivo, APPROVE y un rol de la transición.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 17: Lectura de archivos de importación

`docs/gis.md` §9 y `docs/security.md` §14 «Importaciones»: F2 lee XLSX (primera hoja), CSV (BOM, separador `;` o
`,`, comillas, coma decimal) y GeoJSON, cada uno en una carpeta temporal propia que se borra al terminar, con tope de filas
y ninguna entrada que salga de la carpeta. `ImportProperties` (`sgspe.territory.import.*`) fija tamaño máximo (20 MB),
filas (10 000), tiempo (60 s), tolerancia de «sin cambios» (0.01 m) y la envolvente del Perú. Una hoja CSV exportada de
Excel en español se lee igual que una escrita a mano (Review Focus 5).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/territory/ImportFiles.kt`
- Probar: `src/test/kotlin/sgspe/territory/ImportFilesTest.kt`

**Interfaces:**
- Consume: Apache POI (Tarea 1).
- Produce:
  - `ImportProperties(maxBytes, maxRows, timeout, unchangedToleranceM, minLongitude, maxLongitude, minLatitude, maxLatitude)`.
  - `SourceRow(row, values, geometry)`; `ImportWorkspace.create()`, `resolveEntry(name)`, `close()`;
    `ImportReaders.read(file, filename, maxRows)`, `number(raw): BigDecimal?`.
  - `ColumnMapping.parse(raw)` y `value(row, key)` (claves en snake_case o camelCase, `depth` es `depth_m`, `=CONSTANTE`);
    `Axes.utmSwapped(easting, northing)` y `latLonSwapped(latitude, longitude)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/ImportFilesTest.kt` (7 pruebas):

```kotlin
package sgspe.territory

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.ValidationException
import java.io.File
import java.math.BigDecimal

// la lectura de las hojas de F2 (gis.md §4.1) y los controles de la carpeta temporal (RQ-SEC-020). los códigos son
// ficticios (RQ-SEC-018)
class ImportFilesTest {
    @TempDir
    lateinit var dir: File

    @Test
    fun `csv con punto y coma, comillas y coma decimal`() {
        val rows = ImportReaders.csv("Codigo;Este;Norte;Nota\r\nTST-001;\"812345,5\";9123456,25;\"dice \"\"hola\"\"; y algo\"\nTST-002;812400;9123500;\n")
        assertEquals(2, rows.size)
        assertEquals(2, rows[0].row)
        assertEquals("TST-001", rows[0].values["Codigo"])
        assertEquals(BigDecimal("812345.5"), ImportReaders.number(rows[0].values["Este"]))
        assertEquals("dice \"hola\"; y algo", rows[0].values["Nota"])
        assertNull(rows[1].values["Nota"])
        assertEquals(BigDecimal("1234.5"), ImportReaders.number("1,234.5"))
        assertNull(ImportReaders.number("norte"))
    }

    @Test
    fun `un csv exportado de Excel con BOM se lee igual`() {
        val file =
            File(dir, "excel.csv").apply {
                writeBytes(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Codigo;Este\nTST-001;812345,5\n".toByteArray())
            }
        val row = ImportReaders.read(file, "excel.csv", 10).single()
        assertEquals("TST-001", row.values["Codigo"])
        assertEquals(BigDecimal("812345.5"), ImportReaders.number(row.values["Este"]))
    }

    @Test
    fun `xlsx de la primera hoja con encabezado, numeros sin notacion cientifica`() {
        val file = File(dir, "puntos.xlsx")
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            sheet.createRow(0).apply {
                createCell(0).setCellValue("Hole ID")
                createCell(1).setCellValue("Este")
                createCell(2).setCellValue("Norte")
            }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("TST-DDH01")
                createCell(1).setCellValue(812345.0)
                createCell(2).setCellValue(9123456.75)
            }
            file.outputStream().use(workbook::write)
        }
        val rows = ImportReaders.read(file, "puntos.xlsx", 10)
        assertEquals("TST-DDH01", rows.single().values["Hole ID"])
        assertEquals("812345", rows.single().values["Este"])
        assertEquals("9123456.75", rows.single().values["Norte"])
    }

    @Test
    fun `geojson con sus propiedades y su geometria, y un formato que no es de F2`() {
        val file = File(dir, "predios.geojson")
        file.writeText(
            """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"code":"TST-P1","area":2.5},
               "geometry":{"type":"Polygon","coordinates":[[[0,0],[1,0],[1,1],[0,0]]]}}]}"""
        )
        val row = ImportReaders.read(file, "predios.geojson", 10).single()
        assertEquals("TST-P1", row.values["code"])
        assertEquals("Polygon", row.geometry?.get("type"))
        val shapefile = File(dir, "capa.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals("file", assertThrows<ValidationException> { ImportReaders.read(shapefile, "capa.zip", 10) }.violations.single().field)
    }

    @Test
    fun `mas filas que el maximo y un xlsx roto son 400 sobre file`() {
        val csv = File(dir, "muchas.csv").apply { writeText("code\n" + (1..5).joinToString("\n") { "TST-$it" }) }
        assertThrows<ValidationException> { ImportReaders.read(csv, "muchas.csv", 4) }
        val broken = File(dir, "roto.xlsx").apply { writeText("no es un zip") }
        assertEquals("file", assertThrows<ValidationException> { ImportReaders.read(broken, "roto.xlsx", 10) }.violations.single().field)
    }

    @Test
    fun `una entrada que sale de la carpeta temporal se rechaza`() {
        ImportWorkspace.create().use { workspace ->
            assertEquals(workspace.dir.resolve("capa.shp"), workspace.resolveEntry("capa.shp"))
            assertThrows<ValidationException> { workspace.resolveEntry("../../etc/passwd") }
            assertThrows<ValidationException> { workspace.resolveEntry("/etc/passwd") }
        }
    }

    @Test
    fun `ejes invertidos y mapeo de columnas con constantes`() {
        assertEquals(null, Axes.utmSwapped(BigDecimal(812345), BigDecimal(9123456)))
        assertEquals(true, Axes.utmSwapped(BigDecimal(9123456), BigDecimal(812345)) != null)
        assertEquals(true, Axes.latLonSwapped(BigDecimal("-78.5"), BigDecimal("-7.9")) != null)
        assertEquals(null, Axes.latLonSwapped(BigDecimal("-7.9"), BigDecimal("-78.5")))
        val mapping = ColumnMapping.parse("""{"code": "Hole ID", "component_type": "=SONDAJE"}""")
        val row = SourceRow(2, mapOf("Hole ID" to "TST-1", "easting" to "812345"))
        assertEquals("TST-1", mapping.value(row, "code"))
        assertEquals("SONDAJE", mapping.value(row, "component_type"))
        assertEquals("812345", mapping.value(row, "easting"))
        assertThrows<ValidationException> { ColumnMapping.parse("[1,2]") }
        // las claves del portal (Fase 2.3) en camelCase, y depth por depth_m
        val portal = ColumnMapping.parse("""{"drillType": "Tipo", "depth": "Prof"}""")
        val drill = SourceRow(3, mapOf("Tipo" to "DIAMANTINA", "Prof" to "250,5"))
        assertEquals("DIAMANTINA", portal.value(drill, "drill_type"))
        assertEquals("250,5", portal.value(drill, "depth_m"))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.territory.ImportFilesTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'ImportReaders'`).

- [ ] **Paso 3: Crear `ImportFiles.kt`**

Crear `src/main/kotlin/sgspe/territory/ImportFiles.kt`:

```kotlin
package sgspe.territory

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

// los límites de una importación (RQ-SEC-020, ASM-050): tamaño del archivo, filas, tiempo de lectura y validación, la
// tolerancia de «sin cambio» (gis.md §4.4: 1 cm) y la envolvente del Perú para marcar lo que cae fuera de la región
@ConfigurationProperties("sgspe.territory.import")
data class ImportProperties(
    val maxBytes: Long = 20L * 1024 * 1024,
    val maxRows: Int = 10_000,
    val timeout: Duration = Duration.ofSeconds(60),
    val unchangedToleranceM: BigDecimal = BigDecimal("0.01"),
    val minLongitude: Double = -81.4,
    val maxLongitude: Double = -68.6,
    val minLatitude: Double = -18.4,
    val maxLatitude: Double = 0.1
)

// una fila del archivo: su número (el de la hoja o la posición del feature), sus valores por encabezado y, en GeoJSON, su
// geometría en el CRS de origen
data class SourceRow(
    val row: Int,
    val values: Map<String, String?>,
    val geometry: Map<String, Any?>? = null
)

// la carpeta temporal de una importación (RQ-SEC-020): todo se lee de ahí y se borra al terminar. una entrada de un ZIP
// (Shapefile y FileGDB, desde F6) solo se resuelve dentro de la carpeta: ../ o una ruta absoluta es un 400
class ImportWorkspace private constructor(
    val dir: Path
) : AutoCloseable {
    fun resolveEntry(name: String): Path {
        val resolved = dir.resolve(name).normalize()
        if (!resolved.startsWith(dir) || Path.of(name).isAbsolute) {
            throw ValidationException("Archivo inválido", "file", "una entrada del archivo apunta fuera de su carpeta: $name")
        }
        return resolved
    }

    override fun close() {
        dir.toFile().deleteRecursively()
    }

    companion object {
        fun create(): ImportWorkspace = ImportWorkspace(Files.createTempDirectory("sgspe-import-").toRealPath())
    }
}

// lectura de las hojas XLSX y CSV y de los GeoJSON de F2 (gis.md §4.1). la primera hoja, la primera fila como
// encabezado; un CSV con «;» o «,», con comillas, y con coma decimal (las hojas del Perú suelen traerla)
object ImportReaders {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    fun read(
        file: File,
        filename: String,
        maxRows: Int
    ): List<SourceRow> {
        val name = filename.lowercase()
        val rows =
            when {
                name.endsWith(".xlsx") -> xlsx(file)
                name.endsWith(".csv") -> csv(file.readText(Charsets.UTF_8).removePrefix("﻿"))
                name.endsWith(".geojson") || name.endsWith(".json") -> geojson(file)
                else -> throw ValidationException("Formato no admitido", "file", "en F2 se importan XLSX, CSV y GeoJSON")
            }
        if (rows.size > maxRows) throw ValidationException("Archivo demasiado grande", "file", "tiene ${rows.size} filas; el máximo es $maxRows")
        return rows
    }

    fun xlsx(file: File): List<SourceRow> =
        try {
            WorkbookFactory.create(file, null, true).use { workbook ->
                val sheet = workbook.getSheetAt(0)
                val formatter = DataFormatter()
                val header = sheet.getRow(sheet.firstRowNum) ?: return emptyList()
                val names = (0 until header.lastCellNum).map { formatter.formatCellValue(header.getCell(it)).trim() }
                ((sheet.firstRowNum + 1)..sheet.lastRowNum).mapNotNull { index ->
                    val row = sheet.getRow(index) ?: return@mapNotNull null
                    val values =
                        names.withIndex().associate { (column, title) ->
                            val cell = row.getCell(column)
                            val value =
                                when {
                                    cell == null -> null
                                    cell.cellType == CellType.NUMERIC -> BigDecimal(cell.numericCellValue.toString()).stripTrailingZeros().toPlainString()
                                    else -> formatter.formatCellValue(cell).trim().ifEmpty { null }
                                }
                            title to value
                        }
                    if (values.values.all { it == null }) null else SourceRow(index + 1, values)
                }
            }
        } catch (e: ValidationException) {
            throw e
        } catch (e: Exception) {
            // un XLSX roto o una bomba zip (ZipSecureFile de POI) es un archivo inválido, no un 500
            throw ValidationException("Archivo inválido", "file", "no se pudo leer el XLSX: ${e.javaClass.simpleName}")
        }

    fun csv(text: String): List<SourceRow> {
        val lines = records(text)
        if (lines.isEmpty()) return emptyList()
        val header = lines.first().map { it.trim() }
        return lines.drop(1).withIndex().mapNotNull { (index, cells) ->
            if (cells.all { it.isBlank() }) return@mapNotNull null
            SourceRow(index + 2, header.withIndex().associate { (column, title) -> title to cells.getOrNull(column)?.trim()?.ifEmpty { null } })
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun geojson(file: File): List<SourceRow> {
        val root =
            try {
                JSON.readValue(file, Map::class.java) as Map<String, Any?>
            } catch (_: Exception) {
                throw ValidationException("Archivo inválido", "file", "no es un GeoJSON válido")
            }
        val features =
            when (root["type"]) {
                "FeatureCollection" -> (root["features"] as? List<Map<String, Any?>>).orEmpty()
                "Feature" -> listOf(root)
                else -> throw ValidationException("Archivo inválido", "file", "se espera un FeatureCollection o un Feature")
            }
        return features.withIndex().map { (index, feature) ->
            val properties = (feature["properties"] as? Map<String, Any?>).orEmpty()
            SourceRow(index + 1, properties.mapValues { it.value?.toString() }, feature["geometry"] as? Map<String, Any?>)
        }
    }

    // RFC 4180 con el separador del encabezado: «;» si lo trae y no trae «,»
    private fun records(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val separator = if (';' in firstLine && ',' !in firstLine) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> {
                    cell.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                !quoted && c == separator -> {
                    row += cell.toString()
                    cell.clear()
                }
                !quoted && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += cell.toString()
                    cell.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }

    // un número de una celda: punto o coma decimal; null si no es un número
    fun number(raw: String?): BigDecimal? {
        val value = raw?.trim()?.ifEmpty { null } ?: return null
        val normalized = if (',' in value && '.' !in value) value.replace(',', '.') else value.replace(",", "")
        return normalized.toBigDecimalOrNull()
    }
}

// ejes invertidos, detectados por sus rangos (gis.md §4.3): la fila se marca con la sugerencia de corrección
object Axes {
    fun utmSwapped(
        easting: BigDecimal,
        northing: BigDecimal
    ): String? {
        val e = easting.toDouble()
        val n = northing.toDouble()
        return if (e > 1_000_000 && n in 100_000.0..900_000.0) "el este y el norte parecen invertidos: intercambie las columnas" else null
    }

    fun latLonSwapped(
        latitude: BigDecimal,
        longitude: BigDecimal
    ): String? {
        val lat = latitude.toDouble()
        val lon = longitude.toDouble()
        val swapped = kotlin.math.abs(lat) > 90 || (lat in -82.0..-68.0 && lon in -19.0..1.0)
        return if (swapped) "la latitud y la longitud parecen invertidas: intercambie las columnas" else null
    }
}

// el mapeo de columnas de la parte `columns`: {"code": "Hole ID", "easting": "Este", "depth": "Prof"}. la clave es un
// nombre lógico (code, easting, northing, elevation, latitude, longitude) o un campo del destino, en snake_case o en el
// camelCase del portal (drillType es drill_type; depth es depth_m); el valor, el encabezado de la hoja o la propiedad del
// GeoJSON, o una constante si empieza con «=». lo que no se mapea se busca por su propio nombre
class ColumnMapping(
    private val mapping: Map<String, String>
) {
    fun value(
        row: SourceRow,
        key: String
    ): String? {
        val source = mapping[key] ?: return row.values[key]?.trim()?.ifEmpty { null }
        if (source.startsWith("=")) return source.removePrefix("=").trim().ifEmpty { null }
        return row.values[source]?.trim()?.ifEmpty { null }
    }

    companion object {
        private val JSON =
            tools.jackson.databind.json.JsonMapper
                .builder()
                .build()

        @Suppress("UNCHECKED_CAST")
        fun parse(raw: String?): ColumnMapping {
            if (raw.isNullOrBlank()) return ColumnMapping(emptyMap())
            val parsed =
                try {
                    JSON.readValue(raw, Map::class.java) as Map<String, Any?>
                } catch (_: Exception) {
                    throw ValidationException("Columnas inválidas", listOf(FieldViolation("columns", "debe ser un objeto json {campo: encabezado}")))
                }
            return ColumnMapping(parsed.entries.associate { (key, value) -> canonical(key) to value?.toString().orEmpty() })
        }

        private val ALIASES = mapOf("depth" to "depth_m")

        // drillType -> drill_type; depth -> depth_m
        fun canonical(key: String): String {
            val snake = key.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
            return ALIASES[snake] ?: snake
        }
    }
}
```

- [ ] **Paso 4: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.territory.ImportFilesTest'
```

Expected: `BUILD SUCCESSFUL`; 7 pruebas en verde.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/territory/ImportFilesTest.kt src/main/kotlin/sgspe/territory/ImportFiles.kt
git commit -m "$(cat <<'MSG'
feat(territory): lectura de XLSX, CSV y GeoJSON para importar

Primera hoja de un XLSX, CSV con BOM, punto y coma y coma decimal, y GeoJSON, en una carpeta
temporal propia con tope de filas y sin rutas que salgan de ella.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 18: Importación con vista previa y conciliación

`POST /api/sgspe/territory/imports` y `…/{importBatchId}/confirm` (`docs/api.md` §4.2, `docs/gis.md` §9,
RQ-GIS-003, RQ-GIS-007, RQ-GIS-008, RQ-GIS-010, RQ-GIS-020). La vista previa no escribe: lee el archivo, exige el CRS de
origen, valida cada fila (código, coordenadas, ejes invertidos, envolvente, geometría válida, tipo del destino), concilia
contra lo guardado (`CREATE`, `UNCHANGED` dentro de la tolerancia, `NEW_VERSION` si cambian las coordenadas de un
componente, `UPDATE` en predios y concesiones, una entrada por fila válida) y devuelve el SHA-256 del archivo y las
diferencias, sin geometrías; con `Accept` de XLSX, el reporte de diferencias. La confirmación recibe otra vez el archivo y
los mismos campos de la vista previa (la importación no deja estado en el servidor), exige el mismo SHA-256, aplica las
decisiones por fila (`APPLY` o `DISCARD`, la forma que fija la Fase 2.3; una fila fuera de zona solo entra con `APPLY`
explícito) y escribe todo en una transacción con candado por proyecto y destino. `columns` acepta las claves del portal
(`drillType`, `depth`) y, sin tipo de componente, la hoja es de sondajes (`SONDAJE`). Los límites son 400 sobre `file` y
no escriben nada.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/territory/TerritoryImports.kt`
- Crear: `src/main/kotlin/sgspe/territory/ImportController.kt`
- Probar: `src/test/kotlin/sgspe/territory/TerritoryImportApiTest.kt`
- Probar: `src/test/kotlin/sgspe/territory/ImportLimitsApiTest.kt`

**Interfaces:**
- Consume: `ImportReaders`, `ImportWorkspace`, `ImportProperties` (Tarea 17); `GeoSql`, `Crs` (Tarea 14); `Records`, `Permissions`, `Transactions`, `Callers`, `Criteria` (Tarea 2); `ModelCatalog.enumValues` (Tarea 1).
- Produce:
  - `POST /api/sgspe/territory/imports` (multipart `file`, `target`, `project`, `sourceCrs`, `columns`) →
    `ImportPreview(importBatchId, sha256, target, sourceCrs, rows, valid, invalid, differences, outOfZone)`.
  - `POST /api/sgspe/territory/imports/{importBatchId}/confirm` (las mismas partes más `sha256` y `decisions`
    `[{row, action}]`, `action` `APPLY` o `DISCARD`) →
    `ImportConfirmation(created, newVersions, updated, unchanged, rejected, evidenceId)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/TerritoryImportApiTest.kt` (5 pruebas):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import tools.jackson.databind.JsonNode

// gis.md §9 «Importación tipo DOC-03», «CRS de origen», «Confirmación» y «MULTI*» (RQ-GIS-003, RQ-GIS-007, RQ-GIS-008,
// RQ-GIS-010, RQ-GIS-020), con códigos y coordenadas ficticios en UTM 17S (RQ-SEC-018)
class TerritoryImportApiTest : DomainApiTest() {
    // como las manda el portal (Fase 2.3): solo las columnas del archivo; el tipo de componente queda en SONDAJE
    private val columns = """{"code": "Codigo", "easting": "Este", "northing": "Norte", "elevation": "Cota"}"""

    private fun feature(
        properties: String,
        geometry: String
    ) = """{"type":"FeatureCollection","features":[{"type":"Feature","properties":$properties,"geometry":$geometry}]}""".toByteArray()

    private fun csv(vararg rows: String) = ("Codigo;Este;Norte;Cota\n" + rows.joinToString("\n")).toByteArray()

    private fun preview(
        project: String,
        file: Pair<String, ByteArray>,
        token: String,
        target: String = "project_component",
        sourceCrs: String? = "EPSG 32717",
        columns: String? = this.columns
    ) = multipart(
        "/api/sgspe/territory/imports",
        mapOf("file" to file),
        listOfNotNull("target" to target, "project" to project, sourceCrs?.let { "sourceCrs" to it }, columns?.let { "columns" to it }).toMap(),
        token
    )

    private fun confirm(
        preview: JsonNode,
        project: String,
        file: Pair<String, ByteArray>,
        token: String,
        decisions: String = "[]",
        target: String = "project_component",
        sha256: String = preview["sha256"].asString()
    ) = multipart(
        "/api/sgspe/territory/imports/${preview["importBatchId"].asString()}/confirm",
        mapOf("file" to file),
        mapOf("target" to target, "project" to project, "sourceCrs" to "EPSG 32717", "columns" to columns, "sha256" to sha256, "decisions" to decisions),
        token
    )

    @Test
    fun `una hoja tipo DOC-03 se previsualiza, se confirma y una segunda version se concilia`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val first =
            "pozos.csv" to
                csv(
                    "TST-DDH001;812345;9123456;3100",
                    "TST-DDH002;812400;9123500;3105",
                    "TST-DDH003;812450;9123550;3110",
                    "TST-DDH003;812451;9123551;3110",
                    "TST-DDH004;9123456;812345;3100"
                )
        val response = preview(project, first, analyst.token)
        assertEquals(HttpStatus.OK, response.status, response.text)
        val body = tree(response.text)
        assertEquals(5, body["rows"].asInt())
        assertEquals(3, body["valid"].asInt())
        assertEquals(setOf(5, 6), body["invalid"].values().map { it["row"].asInt() }.toSet())
        assertEquals(listOf("CREATE", "CREATE", "CREATE"), body["differences"].values().map { it["outcome"].asString() })
        assertTrue(body["features"] == null)
        val done = tree(confirm(body, project, first, analyst.token).text)
        assertEquals(3, done["created"].asInt())
        assertEquals(2, done["rejected"].asInt())
        val component = tree(send("GET", "/api/objects/project_component/records?project=$project&code=TST-DDH001", null, HttpStatus.OK))["content"][0]
        assertEquals("EPSG 32717", component["attributes"]["source_crs"].asString())
        assertEquals(body["importBatchId"].asString(), component["attributes"]["import_batch_id"].asString())
        assertEquals(812345.0, component["attributes"]["source_easting"].asDouble())
        assertEquals("SONDAJE", component["attributes"]["component_type"].asString())

        val second = "pozos-v2.csv" to csv("TST-DDH001;812345;9123456;3100", "TST-DDH002;812400;9123716;3105", "TST-DDH003;812450.004;9123550;3110")
        val diff = tree(preview(project, second, analyst.token).text)
        val outcomes = diff["differences"].values().associate { it["code"].asString() to it }
        assertEquals("UNCHANGED", outcomes.getValue("TST-DDH001")["outcome"].asString())
        assertEquals("NEW_VERSION", outcomes.getValue("TST-DDH002")["outcome"].asString())
        assertEquals(216.0, outcomes.getValue("TST-DDH002")["distanceM"].asDouble(), 0.01)
        assertEquals("UNCHANGED", outcomes.getValue("TST-DDH003")["outcome"].asString())
        val applied = tree(confirm(diff, project, second, analyst.token).text)
        assertEquals(1, applied["newVersions"].asInt())
        assertEquals(2, applied["unchanged"].asInt())
        val versions =
            tree(
                send("GET", "/api/objects/project_component/records?project=$project&code=TST-DDH002&sort=version_number", null, HttpStatus.OK)
            )["content"]
        assertEquals(listOf(1, 2), versions.values().map { it["attributes"]["version_number"].asInt() })
        assertTrue(versions[0]["attributes"]["superseded"].asBoolean())
        assertEquals(versions[0]["id"].asString(), versions[1]["attributes"]["previous_version"].asString())
    }

    @Test
    fun `sin CRS de origen es 400 y otro archivo con el SHA-256 de la vista previa se rechaza`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-DDH001;812345;9123456;3100")
        val missing = preview(project, file, analyst.token, sourceCrs = null)
        assertEquals(HttpStatus.BAD_REQUEST, missing.status)
        assertEquals(listOf("sourceCrs"), problemFields(missing.text))
        val body = tree(preview(project, file, analyst.token).text)
        val other = "pozos.csv" to csv("TST-DDH001;812999;9123999;3100")
        val rejected = confirm(body, project, other, analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, rejected.status)
        assertEquals(listOf("sha256"), problemFields(rejected.text))
        assertEquals(0, tree(send("GET", "/api/objects/project_component/records?project=$project", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `una fila fuera de la zona del CRS declarado solo entra si se acepta explicitamente`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val file = "pozos.csv" to csv("TST-DDH010;812345;9123456;3100", "TST-DDH011;960000;9123456;3100")
        val body = tree(preview(project, file, analyst.token).text)
        assertEquals(listOf(3), body["outOfZone"].values().map { it["row"].asInt() })
        assertEquals(1, tree(confirm(body, project, file, analyst.token).text)["created"].asInt())
        val again = tree(preview(project, file, analyst.token).text)
        assertEquals(1, tree(confirm(again, project, file, analyst.token, """[{"row": 3, "action": "APPLY"}]""").text)["created"].asInt())
    }

    @Test
    fun `GeoJSON MULTI de una parte entra a un predio como Polygon y una concesion se vincula al proyecto`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val ring = "[[[-78.60,-7.90],[-78.59,-7.90],[-78.59,-7.89],[-78.60,-7.89],[-78.60,-7.90]]]"
        val parcel = "predios.geojson" to feature("""{"code":"TST-PRD-1","tenure_type":"POSESION"}""", """{"type":"MultiPolygon","coordinates":[$ring]}""")
        val parcelPreview = tree(preview(project, parcel, analyst.token, target = "land_parcel", sourceCrs = "EPSG 4326", columns = null).text)
        val parcelDone =
            multipart(
                "/api/sgspe/territory/imports/${parcelPreview["importBatchId"].asString()}/confirm",
                mapOf("file" to parcel),
                mapOf("target" to "land_parcel", "project" to project, "sourceCrs" to "EPSG 4326", "sha256" to parcelPreview["sha256"].asString()),
                analyst.token
            )
        assertEquals(1, tree(parcelDone.text)["created"].asInt(), parcelDone.text)
        val stored = tree(send("GET", "/api/objects/land_parcel/records?code=TST-PRD-1", null, HttpStatus.OK))["content"][0]
        assertEquals("Polygon", stored["geometries"]["boundary"]["type"].asString())
        assertTrue(stored["attributes"]["calculated_area_ha"].asDouble() > 100)
        val concession = "concesiones.geojson" to feature("""{"code":"TST-CON-1","name":"Concesión Ejemplo"}""", """{"type":"Polygon","coordinates":$ring}""")
        val concessionPreview = tree(preview(project, concession, analyst.token, target = "mining_concession", sourceCrs = "EPSG 4326", columns = null).text)
        multipart(
            "/api/sgspe/territory/imports/${concessionPreview["importBatchId"].asString()}/confirm",
            mapOf("file" to concession),
            mapOf("target" to "mining_concession", "project" to project, "sourceCrs" to "EPSG 4326", "sha256" to concessionPreview["sha256"].asString()),
            analyst.token
        )
        val linked = tree(send("GET", "/api/objects/project/records/$project/related/project_concession", null, HttpStatus.OK))
        assertEquals("TST-CON-1", linked["content"][0]["attributes"]["code"].asString())
        assertEquals("MultiPolygon", linked["content"][0]["geometries"]["boundary"]["type"].asString())
    }

    @Test
    fun `una hoja de puntos para predios y un rol sin permiso sobre el destino se rechazan`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val sheet = "pozos.csv" to csv("TST-DDH001;812345;9123456;3100")
        assertEquals(listOf("file"), problemFields(preview(project, sheet, analyst.token, target = "land_parcel").text))
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        assertEquals(HttpStatus.FORBIDDEN, preview(project, sheet, officer.token).status)
        assertEquals(
            HttpStatus.BAD_REQUEST,
            multipart(
                "/api/sgspe/territory/imports",
                mapOf("file" to sheet),
                mapOf(
                    "target" to "project_component",
                    "otro" to "x"
                ),
                analyst.token
            ).status
        )
    }
}
```

Crear `src/test/kotlin/sgspe/territory/ImportLimitsApiTest.kt` (3 pruebas):

```kotlin
package sgspe.territory

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.DomainApiTest
import java.io.ByteArrayOutputStream

// security.md §14 «Importaciones» (RQ-SEC-020, ASM-050): archivo demasiado grande y tiempo excedido son 400 sobre file y
// no escriben nada; una bomba zip disfrazada de XLSX se rechaza. límites bajos solo en este contexto
@TestPropertySource(properties = ["sgspe.territory.import.max-bytes=65536", "sgspe.territory.import.timeout=1ms"])
class ImportLimitsApiTest : DomainApiTest() {
    private fun preview(
        project: String,
        file: Pair<String, ByteArray>,
        token: String
    ) = multipart(
        "/api/sgspe/territory/imports",
        mapOf("file" to file),
        mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717", "columns" to """{"component_type": "=SONDAJE"}"""),
        token
    )

    @Test
    fun `un archivo mayor que el maximo es 400 sobre file`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val big = "grande.csv" to ("code;easting;northing\n" + (1..5000).joinToString("\n") { "TST-$it;812345;9123456" }).toByteArray()
        val response = preview(project, big, analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("file"), problemFields(response.text))
        assertTrue(response.text.contains("demasiado grande"))
    }

    @Test
    fun `una lectura que pasa el tiempo maximo es 400 sobre file`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val response = preview(project, "pozos.csv" to "code;easting;northing\nTST-1;812345;9123456".toByteArray(), analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertTrue(response.text.contains("tardó demasiado"), response.text)
    }

    @Test
    fun `una bomba zip con extension xlsx se rechaza`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Hoja1")
            val filler = "A".repeat(32_000)
            for (i in 0 until 200) sheet.createRow(i).createCell(0).setCellValue(filler)
            workbook.write(out)
        }
        val response = preview(project, "bomba.xlsx" to out.toByteArray(), analyst.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status)
        assertEquals(listOf("file"), problemFields(response.text))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.TerritoryImportApiTest' --tests 'sgspe.territory.ImportLimitsApiTest'
```

Expected: FAIL: `/api/sgspe/territory/imports` responde 404.

- [ ] **Paso 3: Crear `TerritoryImports.kt`**

Crear `src/main/kotlin/sgspe/territory/TerritoryImports.kt`:

```kotlin
package sgspe.territory

import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordResponse
import java.io.File
import java.math.BigDecimal
import java.util.UUID

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ImportProperties::class)
class TerritoryConfiguration

data class InvalidRow(
    val row: Int,
    val field: String,
    val message: String
)

data class OutOfZoneRow(
    val row: Int,
    val code: String?,
    val message: String
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Difference(
    val row: Int,
    val code: String,
    val outcome: String,
    val currentVersion: Int? = null,
    val distanceM: BigDecimal? = null,
    val previousEasting: BigDecimal? = null,
    val previousNorthing: BigDecimal? = null,
    val newEasting: BigDecimal? = null,
    val newNorthing: BigDecimal? = null,
    val areaHaBefore: BigDecimal? = null,
    val areaHaAfter: BigDecimal? = null
)

data class ImportPreview(
    val importBatchId: UUID,
    val sha256: String,
    val target: String,
    val sourceCrs: String,
    val rows: Int,
    val valid: Int,
    val invalid: List<InvalidRow>,
    val differences: List<Difference>,
    val outOfZone: List<OutOfZoneRow>
)

data class ImportConfirmation(
    val created: Int,
    val newVersions: Int,
    val updated: Int,
    val unchanged: Int,
    val rejected: Int,
    val evidenceId: UUID?
)

// lo que llega en las partes del multipart, ya leído
class ImportRequest(
    val target: String?,
    val project: String?,
    val sourceCrs: String?,
    val columns: String?,
    val file: File,
    val filename: String,
    val sha256: String
)

// la decisión de una fila al confirmar (la forma que fija la Fase 2.3): APPLY aplica la acción propuesta y DISCARD
// descarta la fila. una fila sin decisión aplica su acción propuesta, salvo la que cae fuera de la zona o de la región
// esperada, que solo entra con un APPLY explícito (gis.md §4.3, RQ-GIS-007)
data class RowDecision(
    val row: Int,
    val action: String
)

// la importación de coordenadas y geometrías con CRS de origen, vista previa y confirmación (gis.md §4, ADR-0019,
// api.md §4.2, RQ-GIS-003, RQ-GIS-007, RQ-GIS-008, RQ-GIS-010). no deja estado en el servidor: la confirmación vuelve a
// mandar el mismo archivo con el SHA-256 de la vista previa, y se repite toda la validación. tres destinos:
// project_component (puntos de XLSX o CSV, y GeoJSON), land_parcel y mining_concession (GeoJSON). un código nuevo se crea;
// uno sin desplazamiento queda igual (o se actualizan sus datos); uno desplazado es una versión nueva del componente, o
// una actualización con motivo del predio o la concesión. nunca una sobrescritura silenciosa
@Service
class TerritoryImports(
    private val records: Records,
    private val geo: GeoSql,
    private val catalog: ModelCatalog,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val callers: Callers,
    private val properties: ImportProperties
) {
    suspend fun preview(request: ImportRequest): ImportPreview {
        val analysis = analyze(request)
        return ImportPreview(
            importBatchId = UUID.randomUUID(),
            sha256 = request.sha256,
            target = analysis.target.name,
            sourceCrs = Crs.value(analysis.sourceSrid),
            rows = analysis.rows,
            valid = analysis.candidates.size,
            invalid = analysis.invalid,
            differences = analysis.candidates.map { it.difference },
            outOfZone = analysis.candidates.filter { it.outOfZone != null }.map { OutOfZoneRow(it.row, it.code, it.outOfZone!!) }
        )
    }

    suspend fun confirm(
        batchId: UUID,
        request: ImportRequest,
        sha256: String?,
        decisions: List<RowDecision>
    ): ImportConfirmation {
        if (sha256.isNullOrBlank()) throw ValidationException("Falta el SHA-256 de la vista previa", "sha256", "es obligatorio")
        if (!sha256.equals(request.sha256, ignoreCase = true)) {
            throw ValidationException("El archivo no es el de la vista previa", "sha256", "el SHA-256 del archivo no coincide con el de la vista previa")
        }
        val analysis = analyze(request)
        val byRow = decisions.associateBy { it.row }
        val reason = "importación $batchId"
        var created = 0
        var newVersions = 0
        var updated = 0
        var unchanged = 0
        var rejected =
            analysis.invalid
                .map { it.row }
                .distinct()
                .size
        for (decision in decisions) {
            if (decision.action != APPLY && decision.action != DISCARD) {
                throw ValidationException("Decisión inválida", "decisions", "la acción de la fila ${decision.row} es APPLY o DISCARD")
            }
        }
        transactions.run {
            for (candidate in analysis.candidates) {
                val decision = byRow[candidate.row]
                if (decision?.action == DISCARD || (candidate.outOfZone != null && decision?.action != APPLY)) {
                    rejected++
                    continue
                }
                when (candidate.outcome) {
                    UNCHANGED -> unchanged++
                    CREATE -> {
                        write(analysis, candidate, batchId, reason)
                        created++
                    }
                    NEW_VERSION -> {
                        write(analysis, candidate, batchId, reason)
                        newVersions++
                    }
                    UPDATE -> {
                        write(analysis, candidate, batchId, reason)
                        updated++
                    }
                }
            }
        }
        // el archivo original como evidence llega en F3, con el almacenamiento de objetos (ASM-060)
        return ImportConfirmation(created, newVersions, updated, unchanged, rejected, null)
    }

    private suspend fun write(
        analysis: Analysis,
        candidate: Candidate,
        batchId: UUID,
        reason: String
    ) {
        val target = analysis.target
        val origin =
            mapOf(
                "source_crs" to Crs.value(analysis.sourceSrid),
                "source_reference" to "${analysis.filename}, fila ${candidate.row}",
                "import_batch_id" to batchId.toString()
            )
        val geometries = mapOf(candidate.geometryField to candidate.geometry)
        val area = if (target.name == COMPONENT) emptyMap() else mapOf(Areas.FIELD to candidate.areaHa)
        val existing = candidate.existing
        when {
            existing == null -> {
                val base =
                    if (target.name ==
                        COMPONENT
                    ) {
                        mapOf("project" to analysis.project.toString(), "version_number" to 1, "superseded" to false)
                    } else {
                        emptyMap()
                    }
                val record = records.create(target.name, base + candidate.attributes + origin + area, reason, geometries)
                if (target.name == CONCESSION) records.link("project", analysis.project, "project_concession", UUID.fromString(record.id), reason)
            }
            candidate.outcome == NEW_VERSION -> {
                val version = candidate.difference.currentVersion!! + 1
                records.create(
                    COMPONENT,
                    mapOf("project" to analysis.project.toString(), "version_number" to version, "superseded" to false, "previous_version" to existing.id) +
                        candidate.attributes + origin,
                    reason,
                    geometries
                )
                records.patch(COMPONENT, UUID.fromString(existing.id), mapOf("superseded" to true), reason)
            }
            target.name == COMPONENT -> records.patch(COMPONENT, UUID.fromString(existing.id), candidate.attributes + origin, reason)
            else -> {
                records.patch(target.name, UUID.fromString(existing.id), candidate.attributes + origin + area, reason, geometries)
                if (target.name == CONCESSION) records.link("project", analysis.project, "project_concession", UUID.fromString(existing.id), reason)
            }
        }
    }

    private class Target(
        val name: String,
        // campo por tipo GeoJSON: el de los componentes depende de la geometría; los demás tienen uno solo
        val fields: Map<String, Pair<String, String>>,
        val attributes: List<String>,
        val tables: Boolean
    )

    private class Candidate(
        val row: Int,
        val code: String,
        val attributes: Map<String, Any?>,
        val geometryField: String,
        val geometry: Map<String, Any?>,
        val outOfZone: String?,
        val existing: RecordResponse?,
        val outcome: String,
        val difference: Difference,
        val areaHa: BigDecimal?
    )

    private class Analysis(
        val target: Target,
        val project: UUID,
        val sourceSrid: Int,
        val filename: String,
        val rows: Int,
        val candidates: List<Candidate>,
        val invalid: List<InvalidRow>
    )

    private suspend fun analyze(request: ImportRequest): Analysis {
        val target = TARGETS[request.target] ?: throw ValidationException("Destino inválido", "target", "debe ser uno de ${TARGETS.keys.joinToString(", ")}")
        val project = Records.uuid(request.project) ?: throw ValidationException("Falta el proyecto", "project", "debe ser el UUID de un proyecto")
        val sourceSrid =
            Crs.srid(request.sourceCrs)?.takeIf { request.sourceCrs in catalog.enumValues("source_crs") || catalog.enumValues("source_crs").isEmpty() }
                ?: throw ValidationException(
                    "CRS de origen inválido",
                    "sourceCrs",
                    "es obligatorio y sale del catálogo «CRS de origen»: ${catalog.enumValues("source_crs").joinToString(", ")}"
                )
        val user = callers.require()
        val grants = mutableListOf(Actions.CREATE to target.name, Actions.UPDATE to target.name)
        if (target.name == CONCESSION) grants += Actions.UPDATE to "project"
        permissions.require(user, "Importar ${target.name}", *grants.toTypedArray())
        val projectRecord = records.get("project", project)
        val workingSrid = Crs.srid(Values.text(projectRecord.attributes["working_crs"]))
        val columns = ColumnMapping.parse(request.columns)
        return try {
            withTimeout(properties.timeout.toMillis()) {
                // POI y la lectura del archivo bloquean: en el despachador de E/S, interrumpibles si vence el plazo
                val rows = runInterruptible(Dispatchers.IO) { ImportReaders.read(request.file, request.filename, properties.maxRows) }
                if (!target.tables && rows.any { it.geometry == null }) {
                    throw ValidationException(
                        "Formato no admitido para ${target.name}",
                        "file",
                        "un predio o una concesión necesita un polígono: importe GeoJSON"
                    )
                }
                build(target, project, sourceSrid, workingSrid, request.filename, rows, columns)
            }
        } catch (_: TimeoutCancellationException) {
            throw ValidationException("La importación tardó demasiado", "file", "la lectura y la validación superaron ${properties.timeout.seconds} s")
        }
    }

    private suspend fun build(
        target: Target,
        project: UUID,
        sourceSrid: Int,
        workingSrid: Int?,
        filename: String,
        rows: List<SourceRow>,
        columns: ColumnMapping
    ): Analysis {
        val invalid = mutableListOf<InvalidRow>()
        val parsed = mutableListOf<Triple<SourceRow, String, Pair<String, Map<String, Any?>>>>()
        val seen = mutableMapOf<String, Int>()
        for (row in rows) {
            val code = columns.value(row, "code")
            if (code == null) {
                invalid += InvalidRow(row.row, "code", "el código es obligatorio: es la clave de la conciliación")
                continue
            }
            seen[code]?.let {
                invalid += InvalidRow(row.row, "code", "código repetido en el archivo (fila $it)")
                continue
            }
            seen[code] = row.row
            val shape = shape(target, row, sourceSrid, columns, invalid) ?: continue
            parsed += Triple(row, code, shape)
        }
        val existing = existing(target, project, parsed.map { it.second })
        val candidates = mutableListOf<Candidate>()
        for ((row, code, shape) in parsed) {
            val (field, geometry) = shape
            val attributes = attributes(target, row, columns, invalid) ?: continue
            val validity = geo.validity(geometry)
            if (!validity.valid) {
                invalid += InvalidRow(row.row, field, "geometría inválida: ${validity.reason}")
                continue
            }
            candidates += candidate(target, row.row, code, attributes, field, geometry, sourceSrid, workingSrid, existing[code])
        }
        return Analysis(target, project, sourceSrid, filename, rows.size, candidates, invalid)
    }

    // la geometría de una fila en EPSG:4326 y del tipo del campo destino, o null con su error en `invalid`
    private suspend fun shape(
        target: Target,
        row: SourceRow,
        srid: Int,
        columns: ColumnMapping,
        invalid: MutableList<InvalidRow>
    ): Pair<String, Map<String, Any?>>? {
        val wgs84 =
            if (row.geometry != null) {
                geo.toWgs84(row.geometry, srid)
            } else if (srid == Crs.WGS84) {
                val latitude = ImportReaders.number(columns.value(row, "latitude"))
                val longitude = ImportReaders.number(columns.value(row, "longitude"))
                if (latitude == null || longitude == null) {
                    invalid += InvalidRow(row.row, "latitude", "faltan la latitud y la longitud")
                    return null
                }
                Axes.latLonSwapped(latitude, longitude)?.let {
                    invalid += InvalidRow(row.row, "latitude", it)
                    return null
                }
                geo.pointToWgs84(longitude, latitude, srid)
            } else {
                val easting = ImportReaders.number(columns.value(row, "easting"))
                val northing = ImportReaders.number(columns.value(row, "northing"))
                if (easting == null || northing == null) {
                    invalid += InvalidRow(row.row, "easting", "faltan el este y el norte")
                    return null
                }
                Axes.utmSwapped(easting, northing)?.let {
                    invalid += InvalidRow(row.row, "easting", it)
                    return null
                }
                geo.pointToWgs84(easting, northing, srid)
            }
        val type = wgs84["type"]?.toString().orEmpty()
        val (field, expected) =
            target.fields[type.removePrefix("Multi")] ?: target.fields[type] ?: run {
                invalid += InvalidRow(row.row, "geometry", "${target.name} no admite una geometría $type")
                return null
            }
        val normalized =
            geo.asType(wgs84, expected) ?: run {
                invalid += InvalidRow(row.row, field, "una geometría $type de varias partes no cabe en un campo $expected")
                return null
            }
        return field to normalized
    }

    // los atributos del destino que trae la fila, validados contra su catálogo; null con su error si alguno no vale
    private fun attributes(
        target: Target,
        row: SourceRow,
        columns: ColumnMapping,
        invalid: MutableList<InvalidRow>
    ): Map<String, Any?>? {
        val out = mutableMapOf<String, Any?>("code" to columns.value(row, "code"))
        var ok = true
        for (name in target.attributes) {
            val raw = columns.value(row, SOURCE_COLUMNS[name] ?: name) ?: continue
            val field = catalog.objectOrNull(target.name)?.fields?.get(name)
            val value: Any? =
                when (field?.type) {
                    "DECIMAL", "INTEGER" -> ImportReaders.number(raw)
                    "ENUM" -> raw.takeIf { it in catalog.enumOf(target.name, name) }
                    else -> raw
                }
            if (value == null) {
                invalid += InvalidRow(row.row, name, "valor inválido: $raw")
                ok = false
            } else {
                out[name] = value
            }
        }
        // una hoja sin tipo de componente es la de sondajes de DOC-03 (gis.md §9): el portal mapea solo sus columnas
        if (target.name == COMPONENT && out["component_type"] == null && ok) out["component_type"] = DEFAULT_COMPONENT_TYPE
        return if (ok) out else null
    }

    private suspend fun candidate(
        target: Target,
        row: Int,
        code: String,
        attributes: Map<String, Any?>,
        field: String,
        geometry: Map<String, Any?>,
        sourceSrid: Int,
        workingSrid: Int?,
        existing: RecordResponse?
    ): Candidate {
        val centroid = geo.centroid(geometry)
        val zone = centroid?.let { GeoSql.zoneOf(it.first, it.second) }
        val calcSrid = (if (target.name == COMPONENT) workingSrid else null) ?: zone ?: Crs.zone(sourceSrid)?.let { 32700 + it } ?: 32718
        val outOfZone = outOfZone(centroid, sourceSrid)
        val areaHa = if (target.name == COMPONENT) null else zone?.let { geo.areaHa(geometry, it) }
        val newPoint = geo.utm(geometry, calcSrid)
        if (existing == null) {
            return Candidate(
                row,
                code,
                attributes,
                field,
                geometry,
                outOfZone,
                null,
                CREATE,
                Difference(row, code, CREATE, newEasting = newPoint?.easting, newNorthing = newPoint?.northing, areaHaAfter = areaHa),
                areaHa
            )
        }
        val previous = Records.geometriesOf(existing).values.firstOrNull { it != null }
        val distance = previous?.let { geo.distanceM(it, geometry, calcSrid) }
        val previousPoint = previous?.let { geo.utm(it, calcSrid) }
        val sameShape = previous != null && Records.geometriesOf(existing)[field] != null && distance!! < properties.unchangedToleranceM
        val sameData = attributes.filterKeys { it !in PROVENANCE }.all { (name, value) -> Values.same(existing.attributes[name], value) }
        val version = (Values.decimal(existing.attributes["version_number"]))?.toInt()
        val outcome =
            when {
                sameShape && sameData -> UNCHANGED
                target.name == COMPONENT && !sameShape -> NEW_VERSION
                else -> UPDATE
            }
        val difference =
            Difference(
                row = row,
                code = code,
                outcome = outcome,
                currentVersion = version,
                distanceM = distance,
                previousEasting = previousPoint?.easting,
                previousNorthing = previousPoint?.northing,
                newEasting = newPoint?.easting,
                newNorthing = newPoint?.northing,
                areaHaBefore = Values.decimal(existing.attributes[Areas.FIELD]),
                areaHaAfter = areaHa
            )
        return Candidate(row, code, attributes, field, geometry, outOfZone, existing, outcome, difference, areaHa)
    }

    // fuera de la zona UTM del CRS declarado o de la envolvente del Perú: la fila vale, pero confirmarla es explícito
    private fun outOfZone(
        centroid: Pair<Double, Double>?,
        sourceSrid: Int
    ): String? {
        if (centroid == null) return "no se pudo ubicar la geometría"
        val (x, y) = centroid
        if (x < properties.minLongitude || x > properties.maxLongitude || y < properties.minLatitude || y > properties.maxLatitude) {
            return "cae fuera de la región esperada (envolvente del Perú)"
        }
        val declared = Crs.zone(sourceSrid) ?: return null
        val actual = GeoSql.zoneOf(x, y)?.let { it - 32700 }
        return if (actual != declared) "cae en la zona UTM ${actual ?: "?"}S, no en la ${declared}S del CRS declarado" else null
    }

    // lo registrado con los códigos del archivo: la versión vigente de cada componente del proyecto, o el predio o la
    // concesión del tenant con ese código
    private suspend fun existing(
        target: Target,
        project: UUID,
        codes: List<String>
    ): Map<String, RecordResponse> {
        if (codes.isEmpty()) return emptyMap()
        return if (target.name == COMPONENT) {
            records
                .all(COMPONENT, filters = mapOf("project" to project.toString()), criteria = listOf(Criteria.textIn("code", codes)))
                .filter { Values.bool(it.attributes["superseded"]) != true }
                .groupBy { Values.text(it.attributes["code"])!! }
                .mapValues { (_, versions) -> versions.maxBy { Values.decimal(it.attributes["version_number"]) ?: BigDecimal.ZERO } }
        } else {
            records.all(target.name, criteria = listOf(Criteria.textIn("code", codes))).associateBy { Values.text(it.attributes["code"])!! }
        }
    }

    companion object {
        const val COMPONENT = "project_component"
        const val PARCEL = "land_parcel"
        const val CONCESSION = "mining_concession"
        const val CREATE = "CREATE"
        const val UNCHANGED = "UNCHANGED"
        const val NEW_VERSION = "NEW_VERSION"
        const val UPDATE = "UPDATE"
        const val APPLY = "APPLY"
        const val DISCARD = "DISCARD"

        const val DEFAULT_COMPONENT_TYPE = "SONDAJE"

        // los datos de origen: se guardan como procedencia, pero no cuentan para «sin cambio» (el ruido numérico de una
        // coordenada bajo la tolerancia no es un cambio, gis.md §4.4)
        private val PROVENANCE = setOf("source_easting", "source_northing")

        // los datos de origen se leen de las columnas lógicas del mapeo
        private val SOURCE_COLUMNS = mapOf("source_elevation" to "elevation", "source_easting" to "easting", "source_northing" to "northing")

        private val TARGETS =
            mapOf(
                COMPONENT to
                    Target(
                        COMPONENT,
                        mapOf("Point" to ("location" to "Point"), "LineString" to ("trace" to "LineString"), "Polygon" to ("footprint" to "Polygon")),
                        listOf(
                            "component_type",
                            "drill_type",
                            "component_status",
                            "azimuth",
                            "dip",
                            "depth_m",
                            "source_elevation",
                            "source_easting",
                            "source_northing"
                        ),
                        tables = true
                    ),
                PARCEL to Target(PARCEL, mapOf("Polygon" to ("boundary" to "Polygon")), listOf("tenure_type", "registered_area_ha"), tables = false),
                CONCESSION to Target(CONCESSION, mapOf("Polygon" to ("boundary" to "MultiPolygon")), listOf("name", "registered_area_ha"), tables = false)
            )
    }
}
```

- [ ] **Paso 4: Crear `ImportController.kt`**

Crear `src/main/kotlin/sgspe/territory/ImportController.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.codec.multipart.FilePart
import org.springframework.http.codec.multipart.FormFieldPart
import org.springframework.http.codec.multipart.Part
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

// las dos rutas de la importación (api.md §4.2). multipart: `file` y los campos `target`, `project`, `sourceCrs` y
// `columns`; la confirmación vuelve a mandar esos campos con el archivo y agrega `sha256` y `decisions` (un json
// [{row, action}], action APPLY o DISCARD). una parte
// desconocida es un 400. el archivo se escribe en la carpeta temporal contando bytes: al pasar el máximo, 400 y se borra
@RestController
@RequestMapping("/api/sgspe/territory/imports")
class ImportController(
    private val imports: TerritoryImports,
    private val properties: ImportProperties
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun preview(
        exchange: ServerWebExchange,
        @RequestHeader(name = HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<Any> {
        val parts = exchange.multipartData.awaitSingle().toSingleValueMap()
        requireOnly(parts.keys, PREVIEW_PARTS)
        return ImportWorkspace.create().use { workspace ->
            val request = request(parts, workspace)
            val preview = imports.preview(request)
            if (accept != null && XLSX in accept) {
                ResponseEntity
                    .ok()
                    .contentType(MediaType.parseMediaType(XLSX))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diferencias-${preview.importBatchId}.xlsx\"")
                    .body(differencesXlsx(preview))
            } else {
                ResponseEntity.ok(preview)
            }
        }
    }

    @PostMapping("/{importBatchId}/confirm", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun confirm(
        @PathVariable importBatchId: UUID,
        exchange: ServerWebExchange
    ): ImportConfirmation {
        val parts = exchange.multipartData.awaitSingle().toSingleValueMap()
        requireOnly(parts.keys, CONFIRM_PARTS)
        return ImportWorkspace.create().use { workspace ->
            val request = request(parts, workspace)
            imports.confirm(importBatchId, request, field(parts, "sha256"), decisions(field(parts, "decisions")))
        }
    }

    private suspend fun request(
        parts: Map<String, Part>,
        workspace: ImportWorkspace
    ): ImportRequest {
        val file = parts["file"] as? FilePart ?: throw ValidationException("Falta el archivo", "file", "envíe la parte file")
        val filename = File(file.filename()).name.ifBlank { "archivo" }
        val target = workspace.resolveEntry("upload-${filename.replace(Regex("[^A-Za-z0-9._-]"), "_")}").toFile()
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        withContext(Dispatchers.IO) {
            target.outputStream().use { out ->
                file.content().asFlow().collect { buffer ->
                    try {
                        size += buffer.readableByteCount()
                        if (size > properties.maxBytes) {
                            throw ValidationException("Archivo demasiado grande", "file", "el máximo es ${properties.maxBytes / (1024 * 1024)} MB")
                        }
                        val bytes = ByteArray(buffer.readableByteCount())
                        buffer.read(bytes)
                        digest.update(bytes)
                        out.write(bytes)
                    } finally {
                        DataBufferUtils.release(buffer)
                    }
                }
            }
        }
        return ImportRequest(
            target = field(parts, "target"),
            project = field(parts, "project"),
            sourceCrs = field(parts, "sourceCrs"),
            columns = field(parts, "columns"),
            file = target,
            filename = filename,
            sha256 = HexFormat.of().formatHex(digest.digest())
        )
    }

    private fun field(
        parts: Map<String, Part>,
        name: String
    ): String? = (parts[name] as? FormFieldPart)?.value()?.trim()?.ifEmpty { null }

    @Suppress("UNCHECKED_CAST")
    private fun decisions(raw: String?): List<RowDecision> {
        if (raw == null) return emptyList()
        val parsed =
            try {
                JSON.readValue(raw, List::class.java) as List<Map<String, Any?>>
            } catch (_: Exception) {
                throw ValidationException("Decisiones inválidas", "decisions", "debe ser un json [{row, action}]")
            }
        return parsed.map { item ->
            val row = (item["row"] as? Number)?.toInt() ?: throw ValidationException("Decisiones inválidas", "decisions", "cada decisión lleva su row")
            val action = item["action"]?.toString() ?: throw ValidationException("Decisiones inválidas", "decisions", "cada decisión lleva su action")
            RowDecision(row, action)
        }
    }

    private fun requireOnly(
        names: Set<String>,
        allowed: Set<String>
    ) {
        val unknown = (names - allowed).sorted()
        if (unknown.isNotEmpty()) {
            throw ValidationException(
                "Parte desconocida",
                unknown.map {
                    FieldViolation(it, "no es una parte de esta importación; se admiten ${allowed.sorted().joinToString(", ")}")
                }
            )
        }
    }

    // el reporte de diferencias como hoja (gis.md §4.4): una fila por fila del archivo, con su acción
    private suspend fun differencesXlsx(preview: ImportPreview): ByteArray =
        withContext(Dispatchers.IO) {
            SXSSFWorkbook(100).use { workbook ->
                val sheet = workbook.createSheet("Diferencias")
                val header =
                    listOf(
                        "Fila",
                        "Código",
                        "Acción",
                        "Versión vigente",
                        "Distancia (m)",
                        "Este anterior",
                        "Norte anterior",
                        "Este nuevo",
                        "Norte nuevo",
                        "Área anterior (ha)",
                        "Área nueva (ha)"
                    )
                sheet.createRow(0).let { row -> header.forEachIndexed { i, title -> row.createCell(i).setCellValue(title) } }
                preview.differences.forEachIndexed { index, d ->
                    val row = sheet.createRow(index + 1)
                    val values =
                        listOf(
                            d.row,
                            d.code,
                            d.outcome,
                            d.currentVersion,
                            d.distanceM,
                            d.previousEasting,
                            d.previousNorthing,
                            d.newEasting,
                            d.newNorthing,
                            d.areaHaBefore,
                            d.areaHaAfter
                        )
                    values.forEachIndexed { i, value ->
                        val cell = row.createCell(i)
                        when (value) {
                            null -> cell.setBlank()
                            is Number -> cell.setCellValue(value.toDouble())
                            else -> cell.setCellValue(value.toString())
                        }
                    }
                }
                val invalid = workbook.createSheet("Rechazadas")
                invalid.createRow(0).let { row -> listOf("Fila", "Campo", "Motivo").forEachIndexed { i, title -> row.createCell(i).setCellValue(title) } }
                preview.invalid.forEachIndexed { index, item ->
                    val row = invalid.createRow(index + 1)
                    row.createCell(0).setCellValue(item.row.toDouble())
                    row.createCell(1).setCellValue(item.field)
                    row.createCell(2).setCellValue(item.message)
                }
                ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()
            }
        }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private val JSON: JsonMapper = JsonMapper.builder().build()
        val PREVIEW_PARTS = setOf("file", "target", "project", "sourceCrs", "columns")
        val CONFIRM_PARTS = PREVIEW_PARTS + setOf("sha256", "decisions")
    }
}
```

- [ ] **Paso 5: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.TerritoryImportApiTest' --tests 'sgspe.territory.ImportLimitsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 8 pruebas en verde.

- [ ] **Paso 6: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/territory/TerritoryImportApiTest.kt src/test/kotlin/sgspe/territory/ImportLimitsApiTest.kt src/main/kotlin/sgspe/territory/TerritoryImports.kt src/main/kotlin/sgspe/territory/ImportController.kt
git commit -m "$(cat <<'MSG'
feat(territory): importación con vista previa y conciliación

La vista previa valida y concilia contra lo guardado sin escribir; la confirmación exige el mismo
SHA-256 y aplica las decisiones APPLY y DISCARD por fila en una transacción.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 19: Componentes del proyecto: alta, versión y cambio

`POST /api/sgspe/project-components` y `PATCH /api/sgspe/project-components/{id}` (`docs/api.md` §4.2,
RQ-GIS-010). `project_component` es `apiOnly`: la API genérica no lo escribe. El alta de un código existente con otras
coordenadas crea la versión siguiente, marca la anterior como `superseded` y devuelve la distancia entre las dos; con las
mismas coordenadas es 409. El PATCH (con `If-Match` obligatorio) cambia datos descriptivos y nunca las coordenadas ni el
código.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/territory/ProjectComponents.kt`
- Probar: `src/test/kotlin/sgspe/territory/ProjectComponentsApiTest.kt`

**Interfaces:**
- Consume: `GeoSql` (Tarea 14); `Records`, `Permissions`, `Transactions`, `Callers`, `StrictBody` (Tarea 2); `IfMatch` y `Versions` (Tarea 4).
- Produce:
  - `POST /api/sgspe/project-components` → 201 `ComponentResult(component, previousVersion, distanceM)`.
  - `PATCH /api/sgspe/project-components/{id}` → el registro.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/ProjectComponentsApiTest.kt` (3 pruebas):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import sgspe.common.IfMatch

// POST /api/sgspe/project-components y su PATCH (api.md §4.2, RQ-GIS-010): versión nueva si cambian las coordenadas, 409
// si no, nunca una sobrescritura; el PATCH no toca coordenadas; la API genérica no escribe componentes (apiOnly)
class ProjectComponentsApiTest : DomainApiTest() {
    private fun register(
        project: String,
        token: String,
        location: Map<String, Any?>,
        code: String = "TST-PLT-1"
    ) = exchange(
        "POST",
        "/api/sgspe/project-components",
        mapOf("attributes" to mapOf("project" to project, "code" to code, "component_type" to "PLATAFORMA"), "geometries" to mapOf("location" to location)),
        token
    )

    @Test
    fun `alta, version nueva con su distancia, y 409 con las mismas coordenadas`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val first = register(project, analyst.token, point(-78.2, -7.9))
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val v1 = tree(first.body)
        assertTrue(v1["previousVersion"].isNull)
        val moved = tree(register(project, analyst.token, point(-78.2, -7.899)).body)
        assertEquals(2, moved["component"]["attributes"]["version_number"].asInt())
        assertEquals(v1["component"]["id"].asString(), moved["component"]["attributes"]["previous_version"].asString())
        assertTrue(moved["previousVersion"]["attributes"]["superseded"].asBoolean())
        assertTrue(moved["distanceM"].asDouble() in 100.0..120.0, moved["distanceM"].toString())
        assertEquals(HttpStatus.CONFLICT, register(project, analyst.token, point(-78.2, -7.899)).status)
    }

    @Test
    fun `el PATCH cambia datos con If-Match y rechaza geometrias y campos de identidad`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val component = tree(register(project, analyst.token, point(-78.2, -7.9)).body)["component"]
        val id = component["id"].asString()
        val ifMatch = mapOf(IfMatch.HEADER to "\"${component["updatedAt"].asString()}\"")
        assertEquals(
            listOf("geometries"),
            problemFields(
                patch(
                    "/api/sgspe/project-components/$id",
                    mapOf("geometries" to mapOf("location" to point(-78.0, -7.0))),
                    analyst.token,
                    ifMatch
                ).body
            )
        )
        assertEquals(
            listOf("attributes.code"),
            problemFields(patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("code" to "OTRO")), analyst.token, ifMatch).body)
        )
        assertEquals(
            HttpStatus.PRECONDITION_REQUIRED,
            patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), analyst.token).status
        )
        val changed = patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), analyst.token, ifMatch)
        assertEquals(HttpStatus.OK, changed.status, changed.body)
        assertEquals("EJECUTADO", tree(changed.body)["attributes"]["component_status"].asString())
        assertEquals(
            HttpStatus.PRECONDITION_FAILED,
            patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("dip" to 60)), analyst.token, ifMatch).status
        )
    }

    @Test
    fun `la API generica no escribe componentes`() {
        val project = projectWithCrs()
        val response =
            exchange(
                "POST",
                "/api/objects/project_component/records",
                mapOf("attributes" to mapOf("project" to project, "code" to "TST-X", "component_type" to "SONDAJE", "version_number" to 1))
            )
        assertEquals(HttpStatus.FORBIDDEN, response.status)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.ProjectComponentsApiTest'
```

Expected: FAIL: `/api/sgspe/project-components` responde 404.

- [ ] **Paso 3: Crear `ProjectComponents.kt`**

Crear `src/main/kotlin/sgspe/territory/ProjectComponents.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.IfMatch
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import wasichai.core.common.Actions
import wasichai.core.common.ConflictException
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.net.URI
import java.util.UUID

data class ComponentResult(
    val component: RecordResponse,
    val previousVersion: RecordResponse?,
    val distanceM: BigDecimal?
)

// el alta y la versión manual de un componente (api.md §4.2, RQ-GIS-010): project_component es apiOnly y nunca se
// sobrescribe. un código nuevo en el proyecto es la versión 1; un código que ya existe con otras coordenadas es una
// versión nueva (version_number siguiente, previous_version) y la anterior pasa a superseded, en una transacción; con las
// mismas coordenadas (menos de 1 cm) es un 409: sus datos se cambian con PATCH. la distancia se mide en el working_crs
// del proyecto o, si no lo tiene, en la zona del centroide. el PATCH cambia datos descriptivos, nunca coordenadas
@Service
class ProjectComponents(
    private val records: Records,
    private val versions: Versions,
    private val geo: GeoSql,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val callers: Callers,
    private val properties: ImportProperties
) {
    suspend fun register(
        body: Map<String, Any?>,
        reason: String?
    ): ComponentResult {
        StrictBody.requireOnly(body, setOf("attributes", "geometries"))
        val attributes = StrictBody.objectOf(body, "attributes")
        val geometries = StrictBody.objectOf(body, "geometries")
        StrictBody.requireOnly(attributes, CREATABLE, "attributes.")
        StrictBody.requireOnly(geometries, GEOMETRY_TYPES.keys, "geometries.")
        val project = Records.uuid(attributes["project"]) ?: throw ValidationException("Falta el proyecto", "attributes.project", "es obligatorio")
        val code = Values.text(attributes["code"]) ?: throw ValidationException("Falta el código", "attributes.code", "es obligatorio")
        val (field, geometry) =
            geometries.entries.singleOrNull { it.value != null }?.let { it.key to geometryOf(it.key, it.value) }
                ?: throw ValidationException("Falta la geometría", "geometries", "envíe una de location, trace o footprint")
        val validity = geo.validity(geometry)
        if (!validity.valid) throw ValidationException("Geometría inválida", "geometries.$field", validity.reason ?: "no es válida")
        val user = callers.require()
        permissions.require(user, "Registrar un componente", Actions.CREATE to COMPONENT, Actions.UPDATE to COMPONENT)
        return transactions.locked("project_component:$project:$code") {
            val current =
                records
                    .all(COMPONENT, filters = mapOf("project" to project.toString(), "code" to code))
                    .filter { Values.bool(it.attributes["superseded"]) != true }
                    .maxByOrNull { Values.decimal(it.attributes["version_number"]) ?: BigDecimal.ZERO }
            if (current == null) {
                val created = records.create(COMPONENT, attributes + mapOf("version_number" to 1, "superseded" to false), reason, mapOf(field to geometry))
                return@locked ComponentResult(created, null, null)
            }
            val previous =
                Records.geometriesOf(current).values.firstOrNull { it != null }
                    ?: throw ConflictException("La versión vigente de $code no tiene geometría: corríjala antes de versionarla")
            val srid = workingSrid(project) ?: geo.centroidZone(geometry) ?: 32718
            val distance = geo.distanceM(previous, geometry, srid)
            if (distance < properties.unchangedToleranceM && Records.geometriesOf(current)[field] != null) {
                throw ConflictException("$code ya existe con las mismas coordenadas: cambie sus datos con PATCH")
            }
            val version = (Values.decimal(current.attributes["version_number"])?.toInt() ?: 0) + 1
            val created =
                records.create(
                    COMPONENT,
                    attributes + mapOf("version_number" to version, "superseded" to false, "previous_version" to current.id),
                    reason,
                    mapOf(field to geometry)
                )
            val superseded = records.patch(COMPONENT, UUID.fromString(current.id), mapOf("superseded" to true), reason)
            ComponentResult(created, superseded, distance)
        }
    }

    suspend fun change(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): RecordResponse {
        StrictBody.requireOnly(body, setOf("attributes", "geometries"))
        if (body.containsKey("geometries")) {
            throw ValidationException(
                "Las coordenadas no se cambian aquí",
                "geometries",
                "una geometría nueva es una versión nueva: POST /api/sgspe/project-components"
            )
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        val locked = attributes.keys.filter { it !in PATCHABLE }.sorted()
        if (locked.isNotEmpty()) {
            throw ValidationException(
                "Campo no editable",
                locked.map { FieldViolation("attributes.$it", "no se cambia con PATCH; se admiten ${PATCHABLE.sorted().joinToString(", ")}") }
            )
        }
        val expected = IfMatch.required(ifMatch)
        val user = callers.require()
        permissions.require(user, "Cambiar un componente", Actions.UPDATE to COMPONENT)
        return transactions.locked("project_component:$id") {
            val current = records.get(COMPONENT, id)
            versions.check(COMPONENT, id, expected, current.updatedAt, "El componente")
            records.patch(COMPONENT, id, attributes, reason)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun geometryOf(
        field: String,
        value: Any?
    ): Map<String, Any?> {
        val geometry = value as? Map<String, Any?> ?: throw ValidationException("Geometría inválida", "geometries.$field", "debe ser un GeoJSON EPSG:4326")
        if (geometry["type"] != GEOMETRY_TYPES[field]) {
            throw ValidationException("Geometría inválida", "geometries.$field", "$field es un ${GEOMETRY_TYPES[field]}")
        }
        return geometry
    }

    private suspend fun workingSrid(project: UUID): Int? = Crs.srid(Values.text(records.find("project", project)?.attributes?.get("working_crs")))

    companion object {
        const val COMPONENT = "project_component"
        val GEOMETRY_TYPES = mapOf("location" to "Point", "trace" to "LineString", "footprint" to "Polygon")
        val PATCHABLE = setOf("component_type", "drill_type", "component_status", "azimuth", "dip", "depth_m", "exploration_campaign", "source_reference")
        val CREATABLE =
            PATCHABLE +
                setOf("project", "code", "source_crs", "source_easting", "source_northing", "source_elevation")
    }
}

@RestController
@RequestMapping("/api/sgspe/project-components")
class ProjectComponentsController(
    private val components: ProjectComponents
) {
    // Idempotency-Key se acepta y se ignora en F2 (api.md §3.5)
    @PostMapping
    suspend fun register(
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?
    ): ResponseEntity<ComponentResult> {
        val result = components.register(body, ChangeReason.fromHeader(reason))
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .location(URI.create("/api/objects/project_component/records/${result.component.id}"))
            .body(result)
    }

    @PatchMapping("/{id}")
    suspend fun change(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?
    ): RecordResponse = components.change(id, body, ChangeReason.fromHeader(reason), ifMatch)
}
```

- [ ] **Paso 4: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.ProjectComponentsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 3 pruebas en verde.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/territory/ProjectComponentsApiTest.kt src/main/kotlin/sgspe/territory/ProjectComponents.kt
git commit -m "$(cat <<'MSG'
feat(territory): alta, versión y cambio de componentes del proyecto

Un código existente con otras coordenadas es una versión nueva con su distancia; las mismas
coordenadas son 409. El PATCH exige If-Match y no toca coordenadas ni código.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 20: Capas básicas del mapa

`GET /api/sgspe/territory/layers/{layer}` (`docs/api.md` §4.2, `docs/gis.md` §10): las dieciséis capas básicas
LYR-01 a LYR-16 como GeoJSON EPSG:4326, leídas con los permisos de quien mira, acotadas por proyecto y `bbox`, sin
versiones reemplazadas de componentes y con un tope de 5000 elementos. `periodStart` y `periodEnd` se aceptan y no filtran
en F2 (las capas temporales llegan con F6). Una capa que no es de F2 es 404; un parámetro desconocido, 422.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/territory/Layers.kt`
- Probar: `src/test/kotlin/sgspe/territory/TerritoryLayersApiTest.kt`

**Interfaces:**
- Consume: `Records.rows`, `Criteria` (Tarea 2); `StrictQuery` de F1.
- Produce: `GET /api/sgspe/territory/layers/{layer}?project=&periodStart=&periodEnd=&bbox=` → FeatureCollection.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/TerritoryLayersApiTest.kt` (2 pruebas):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// GET /api/sgspe/territory/layers/{layer} en F2 (RQ-GIS-001, RQ-GIS-012): las capas básicas como GeoJSON con los permisos
// de quien mira, acotadas por proyecto y bbox; la versión vigente de los componentes; 404 una capa de F6; 422 un parámetro
// desconocido; 403 sin READ sobre el objeto de la capa
class TerritoryLayersApiTest : DomainApiTest() {
    @Test
    fun `LYR-10 trae los sondajes vigentes del proyecto y bbox acota`() {
        val project = projectWithCrs()
        val other = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        assign(analyst, other, "GIS_ANALYST")

        fun drill(
            project: String,
            code: String,
            location: Map<String, Any?>
        ) = send(
            "POST",
            "/api/sgspe/project-components",
            mapOf("attributes" to mapOf("project" to project, "code" to code, "component_type" to "SONDAJE"), "geometries" to mapOf("location" to location)),
            HttpStatus.CREATED,
            analyst.token
        )
        drill(project, "TST-DDH1", point(-78.2, -7.9))
        drill(project, "TST-DDH1", point(-78.2, -7.899))
        drill(project, "TST-DDH2", point(-75.0, -12.0))
        drill(other, "TST-DDH9", point(-78.2, -7.9))
        val layer = tree(send("GET", "/api/sgspe/territory/layers/LYR-10?project=$project", null, HttpStatus.OK, analyst.token))
        assertEquals("FeatureCollection", layer["type"].asString())
        val codes = layer["features"].values().map { it["properties"]["code"].asString() to it["properties"]["version_number"].asInt() }
        assertEquals(setOf("TST-DDH1" to 2, "TST-DDH2" to 1), codes.toSet())
        val boxed = tree(send("GET", "/api/sgspe/territory/layers/LYR-10?project=$project&bbox=-78.3,-8.0,-78.1,-7.8", null, HttpStatus.OK, analyst.token))
        assertEquals(listOf("TST-DDH1"), boxed["features"].values().map { it["properties"]["code"].asString() })
    }

    @Test
    fun `una capa de F6 es 404, un parametro desconocido 422 y sin READ 403`() {
        val project = projectWithCrs()
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/territory/layers/LYR-17").status)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/territory/layers/LYR-01?proyecto=$project").status)
        val projectAdmin = member("PROJECT_ADMIN", project)
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/sgspe/territory/layers/LYR-16", token = projectAdmin.token).status)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.TerritoryLayersApiTest'
```

Expected: FAIL: `/api/sgspe/territory/layers/LYR-10` responde 404.

- [ ] **Paso 3: Crear `Layers.kt`**

Crear `src/main/kotlin/sgspe/territory/Layers.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Records
import sgspe.common.StrictQuery
import wasichai.core.common.NotFoundException
import wasichai.core.common.PageRequest
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQuery
import wasichai.gis.BboxQuery
import java.util.UUID

// una capa del catálogo de gis.md §2.2: de qué objeto y geometría sale, con qué filtro fijo y si se acota por proyecto
data class LayerDefinition(
    val id: String,
    val sources: List<LayerSource>
)

data class LayerSource(
    val objectName: String,
    val geometries: List<String>,
    // filtro fijo de igualdad o de conjunto (campo -> valores)
    val fixed: Map<String, Set<String>> = emptyMap(),
    // cómo se acota por proyecto: por su campo project, por ser el proyecto mismo o por la relación project_concession
    val projectScope: LayerScope = LayerScope.NONE,
    val excludeSuperseded: Boolean = false,
    val states: Set<String>? = null
)

enum class LayerScope { NONE, FIELD, SELF, CONCESSION }

// GET /api/sgspe/territory/layers/{layer} en F2 (api.md §4.2, gis.md §6.1 y §6.5): las dieciséis capas básicas LYR-01 a
// LYR-16 del mapa del proyecto como GeoJSON EPSG:4326, leídas con RecordService como quien llama, así que ninguna muestra
// registros ni campos que no puede leer (RQ-GIS-001). sin READ sobre un objeto de la capa, 403. project acota las capas de
// proyecto (por defecto, el área de influencia aprobada); bbox acota todo; periodStart y periodEnd se aceptan para las
// capas temáticas de F6 y no filtran estas capas sin fecha. un tope de elementos por capa, como /api/gis
@Service
class Layers(
    private val records: Records
) {
    suspend fun features(
        layer: String,
        projects: List<UUID>,
        bbox: String?
    ): Map<String, Any?> {
        val definition = CATALOG[layer] ?: throw NotFoundException("La capa $layer no existe o no está disponible en esta fase")
        val features = mutableListOf<Map<String, Any?>>()
        for (source in definition.sources) {
            for (geometry in source.geometries) {
                if (features.size >= MAX_FEATURES) break
                features += read(definition.id, source, geometry, projects, bbox, MAX_FEATURES - features.size)
            }
        }
        return mapOf("type" to "FeatureCollection", "features" to features)
    }

    private suspend fun read(
        layer: String,
        source: LayerSource,
        geometry: String,
        projects: List<UUID>,
        bbox: String?,
        limit: Int
    ): List<Map<String, Any?>> {
        val criteria = mutableListOf<RecordCriterion>()
        source.fixed.forEach { (field, values) -> criteria += Criteria.textIn(field, values) }
        source.states?.let { criteria += Criteria.inState(it) }
        if (projects.isNotEmpty()) {
            when (source.projectScope) {
                LayerScope.FIELD -> criteria += Criteria.anyOf("project", projects)
                LayerScope.SELF -> criteria += RecordCriterion { _, bind -> "id = ANY(${bind(projects.toTypedArray())})" }
                LayerScope.CONCESSION, LayerScope.NONE -> Unit
            }
        }
        bbox?.let { BboxQuery().parse(mapOf("bbox" to it, "geometry" to geometry))?.let(criteria::add) }
        val ofProject =
            if (source.projectScope == LayerScope.CONCESSION && projects.isNotEmpty()) {
                projects.flatMap { records.related("project", it, "project_concession") }.map { it.id }.toSet()
            } else {
                emptySet()
            }
        val (_, rows) = records.rows(source.objectName, RecordQuery(page = PageRequest(0, limit.coerceIn(1, MAX_FEATURES)), sort = "id", criteria = criteria))
        return rows
            .filter { !source.excludeSuperseded || it.attributes["superseded"] != true }
            .mapNotNull { row ->
                @Suppress("UNCHECKED_CAST")
                val shape = (row.sections[Records.GEOMETRIES] as Map<String, Any?>?)?.get(geometry) ?: return@mapNotNull null
                val extra = mutableMapOf<String, Any?>("__layer" to layer, "__object" to source.objectName, "__id" to row.id.toString(), "__state" to row.state)
                if (source.projectScope == LayerScope.CONCESSION && projects.isNotEmpty()) extra["ofProject"] = row.id.toString() in ofProject
                mapOf("type" to "Feature", "id" to "${row.id}:$geometry", "geometry" to shape, "properties" to row.attributes + extra)
            }
    }

    companion object {
        const val MAX_FEATURES = 5000
        private val LOCALITIES = setOf("CASERIO", "SECTOR", "CENTRO POBLADO", "ANEXO")

        val CATALOG: Map<String, LayerDefinition> =
            listOf(
                LayerDefinition("LYR-01", listOf(LayerSource("administrative_unit", listOf("boundary")))),
                LayerDefinition("LYR-02", listOf(LayerSource("locality", listOf("location", "boundary"), mapOf("locality_type" to LOCALITIES)))),
                LayerDefinition("LYR-03", listOf(LayerSource("locality", listOf("location"), mapOf("locality_type" to setOf("CIUDAD"))))),
                LayerDefinition("LYR-04", listOf(LayerSource("road_access", listOf("trace")))),
                LayerDefinition("LYR-05", listOf(LayerSource("community", listOf("territory")))),
                LayerDefinition("LYR-06", listOf(LayerSource("community_sector", listOf("boundary")))),
                LayerDefinition("LYR-07", listOf(LayerSource("mining_concession", listOf("boundary"), projectScope = LayerScope.CONCESSION))),
                LayerDefinition("LYR-08", listOf(LayerSource("environmental_feature", listOf("boundary", "trace", "location")))),
                LayerDefinition("LYR-09", listOf(LayerSource("project", listOf("location", "interest_area"), projectScope = LayerScope.SELF))),
                LayerDefinition(
                    "LYR-10",
                    listOf(
                        LayerSource(
                            "project_component",
                            listOf("location"),
                            mapOf("component_type" to setOf("SONDAJE")),
                            LayerScope.FIELD,
                            excludeSuperseded = true
                        )
                    )
                ),
                LayerDefinition(
                    "LYR-11",
                    listOf(
                        LayerSource(
                            "project_component",
                            listOf("footprint", "location"),
                            mapOf("component_type" to setOf("PLATAFORMA", "CAMPAMENTO", "CAPTACION")),
                            LayerScope.FIELD,
                            excludeSuperseded = true
                        )
                    )
                ),
                LayerDefinition(
                    "LYR-12",
                    listOf(
                        LayerSource(
                            "project_component",
                            listOf("trace"),
                            mapOf("component_type" to setOf("ACCESO")),
                            LayerScope.FIELD,
                            excludeSuperseded = true
                        )
                    )
                ),
                LayerDefinition(
                    "LYR-13",
                    listOf(LayerSource("area_of_influence", listOf("boundary"), projectScope = LayerScope.FIELD, states = setOf("approved")))
                ),
                LayerDefinition(
                    "LYR-14",
                    listOf(LayerSource("land_parcel", listOf("boundary"), mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL"))))
                ),
                LayerDefinition("LYR-15", listOf(LayerSource("land_parcel", listOf("boundary"), mapOf("tenure_type" to setOf("PROPIEDAD DE LA EMPRESA"))))),
                LayerDefinition("LYR-16", listOf(LayerSource("school", listOf("location")), LayerSource("health_facility", listOf("location"))))
            ).associateBy { it.id }
    }
}

@RestController
@RequestMapping("/api/sgspe/territory/layers")
class LayersController(
    private val layers: Layers
) {
    @GetMapping("/{layer}")
    suspend fun layer(
        @PathVariable layer: String,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("project", "periodStart", "periodEnd", "bbox"))
        val projects =
            params["project"].orEmpty().map {
                runCatching { UUID.fromString(it) }.getOrElse { throw ValidationException("Proyecto inválido", "project", "debe ser un UUID") }
            }
        return layers.features(layer, projects, params.getFirst("bbox"))
    }
}
```

- [ ] **Paso 4: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.territory.TerritoryLayersApiTest'
```

Expected: `BUILD SUCCESSFUL`; 2 pruebas en verde.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/territory/TerritoryLayersApiTest.kt src/main/kotlin/sgspe/territory/Layers.kt
git commit -m "$(cat <<'MSG'
feat(territory): capas básicas LYR-01 a LYR-16

GeoJSON EPSG:4326 por capa con los permisos de quien mira, acotado por proyecto y bbox, sin
versiones reemplazadas; una capa de F6 es 404.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 21: Indicadores KPI-STK y KPI-ENG (cálculo puro)

`docs/dashboards.md` §5.3 y §5.4: los cálculos de KPI-STK-01 a KPI-STK-08 y KPI-ENG-01 a KPI-ENG-05 como
funciones puras sobre filas ya leídas, con sus desgloses y series. La postura de línea base de KPI-STK-06 es la última
evaluación estrictamente anterior al inicio del periodo; las reuniones de KPI-ENG-02 son los tipos de reunión del catálogo;
KPI-ENG-03 da, por reunión, el total, el porcentaje de mujeres y los asistentes del área directa e indirecta. Los nombres
de los desgloses (`by`) y las propiedades de sus filas son los que fija la Fase 2.3 (`influence_group`, `stance`,
`stakeholder`, `direction`, `engagement_type`, `engagement`, `topic`); KPI-STK-03 devuelve evaluados en `numerator` y
vigentes en `denominator`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/dashboard/Indicators.kt`
- Crear: `src/main/kotlin/sgspe/dashboard/StakeholderKpis.kt`
- Crear: `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt`
- Probar: `src/test/kotlin/sgspe/dashboard/StakeholderKpisTest.kt`
- Probar: `src/test/kotlin/sgspe/dashboard/EngagementKpisTest.kt`

**Interfaces:**
- Consume: Nada de las tareas anteriores.
- Produce:
  - `Breakdown(by, rows)`, `IndicatorResult(value, numerator, denominator, breakdowns, series, warnings)`,
    `IndicatorValue`, `CatalogEntry`, `DashboardResponse`, `Numbers.percent/average`.
  - `StakeholderRow`, `AssessmentRow`, `Contact`, `StakeholderKpis.*`; `EngagementRow`, `QuestionRow`, `MonthlyTarget`,
    `EngagementKpis.*`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/StakeholderKpisTest.kt` (6 pruebas):

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-STK de dashboards.md §4.2 con datos ficticios, sin base
class StakeholderKpisTest {
    private fun stakeholder(
        id: String,
        group: String? = null,
        scope: String? = "AIDS",
        stance: String? = null,
        state: String = "assessed",
        person: String? = "p-$id",
        organization: String? = null,
        interest: String? = null,
        power: String? = null
    ) = StakeholderRow(id, state, group, scope, "LIDER LOCAL", stance, interest?.let(::BigDecimal), power?.let(::BigDecimal), person, organization)

    private fun assessment(
        stakeholder: String,
        date: String,
        stance: String?,
        trust: Int? = null
    ) = AssessmentRow(stakeholder, LocalDate.parse(date), stance, trust, false, null, null)

    private val start = LocalDate.parse("2026-05-01")
    private val end = LocalDate.parse("2026-05-31")

    @Test
    fun `KPI-STK-01 y 02 cuentan vigentes, con SIN EVALUAR y sin los retirados`() {
        val rows =
            listOf(
                stakeholder("a", "ESTRATEGICO", stance = "EN CONTRA"),
                stakeholder("b", "ESTRATEGICO", stance = "A FAVOR"),
                stakeholder("c"),
                stakeholder("d", "POLITICO", stance = "EN CONTRA", state = "retired")
            )
        val byGroup = StakeholderKpis.byInfluenceGroup(rows)
        assertEquals(3, byGroup.value)
        val groups = byGroup.breakdowns.first { it.by == "influence_group" }.rows
        assertEquals(mapOf("ESTRATEGICO" to 2, "SIN EVALUAR" to 1), groups.associate { it["key"] to it["value"] })
        val cross = byGroup.breakdowns.first { it.by == "influence_group_by_scope" }.rows
        assertEquals(2, cross.first { it["key"] == "ESTRATEGICO|AIDS" }["value"])
        assertEquals(1, cross.first { it["key"] == "SIN EVALUAR|AIDS" }["value"])
        val stances =
            StakeholderKpis
                .byStance(rows)
                .breakdowns
                .first { it.by == "stance" }
                .rows
        assertEquals(listOf("A FAVOR", "NEUTRAL", "EN CONTRA PERO ABIERTO A ESCUCHAR", "EN CONTRA", "SIN EVALUAR"), stances.map { it["key"] })
        assertEquals(listOf(1, 0, 0, 1, 1), stances.map { it["value"] })
    }

    @Test
    fun `KPI-STK-03 dibuja solo evaluados vigentes y oculta la postura a quien no la lee`() {
        val rows = listOf(stakeholder("a", "ESTRATEGICO", stance = "EN CONTRA", interest = "3.5", power = "4"), stakeholder("b"))
        val last = AssessmentRow("a", LocalDate.parse("2026-05-02"), "EN CONTRA", 3, false, null, null, BigDecimal("14.00"))
        val result =
            StakeholderKpis.matrix(
                rows,
                mapOf("a" to last),
                mapOf("a" to "Teniente Gobernador, Caserío Ejemplo"),
                BigDecimal(3),
                BigDecimal(3),
                stanceReadable = false
            )
        val point =
            result.breakdowns
                .first { it.by == "stakeholder" }
                .rows
                .single()
        assertEquals("a", point["key"])
        assertEquals("Teniente Gobernador, Caserío Ejemplo", point["label"])
        assertEquals(BigDecimal("14.00"), point["value"])
        assertEquals(BigDecimal("3.5"), point["interest"])
        assertEquals(BigDecimal("4"), point["power"])
        assertFalse(point.containsKey("stance"))
        assertEquals(1, result.numerator)
        assertEquals(2, result.denominator)
    }

    @Test
    fun `KPI-STK-04 y 07 cuentan grupos de interes distintos y los de dos o mas interacciones`() {
        val rows = listOf(stakeholder("a"), stakeholder("b", person = null, organization = "o-1", scope = "AIIS")).associateBy { it.id }
        val day = LocalDate.parse("2026-05-10")
        val contacts = listOf(Contact("a", "e1", day), Contact("a", "e2", day), Contact("b", "e1", day))
        val result =
            StakeholderKpis.contacted(
                contacts,
                rows,
                mapOf("a" to "FEMENINO"),
                mapOf(
                    "a" to listOf("Ronda Ejemplo"),
                    "b" to listOf("Asociación Ejemplo")
                )
            )
        assertEquals(2, result.value)
        assertEquals(
            setOf("FEMENINO", "ORGANIZACION"),
            result.breakdowns
                .first { it.by == "sex" }
                .rows
                .map { it["key"] }
                .toSet()
        )
        val repeated = StakeholderKpis.repeatedContact(contacts)
        assertEquals(BigDecimal("50.0"), repeated.value)
        assertEquals(1, repeated.numerator)
    }

    @Test
    fun `KPI-STK-05 toma la postura antes del periodo o la primera del periodo`() {
        val rows = listOf(stakeholder("a"), stakeholder("b"), stakeholder("c"))
        val assessments =
            mapOf(
                "a" to listOf(assessment("a", "2026-04-10", "EN CONTRA")),
                "b" to listOf(assessment("b", "2026-05-05", "EN CONTRA PERO ABIERTO A ESCUCHAR")),
                "c" to listOf(assessment("c", "2026-04-10", "A FAVOR"))
            )
        val result = StakeholderKpis.oppositionContacted(rows, assessments, listOf(Contact("a", "e1", LocalDate.parse("2026-05-10"))), start, end)
        assertEquals(2, result.denominator)
        assertEquals(1, result.numerator)
        assertEquals(BigDecimal("50.0"), result.value)
    }

    @Test
    fun `KPI-STK-06 compara la ultima del periodo con la linea base, con direccion y cobertura`() {
        val rows = listOf(stakeholder("a"), stakeholder("b"), stakeholder("c"))
        val assessments =
            mapOf(
                "a" to listOf(assessment("a", "2026-04-10", "EN CONTRA"), assessment("a", "2026-05-20", "NEUTRAL")),
                "b" to listOf(assessment("b", "2026-04-10", "EN CONTRA PERO ABIERTO A ESCUCHAR"), assessment("b", "2026-05-20", "EN CONTRA")),
                "c" to listOf(assessment("c", "2026-04-10", "EN CONTRA"))
            )
        val result = StakeholderKpis.oppositionChanged(rows, assessments, start, end)
        assertEquals(BigDecimal("100.0"), result.value)
        val direction =
            result.breakdowns
                .first { it.by == "direction" }
                .rows
                .associate { it["key"] to it["value"] }
        assertEquals(1, direction["MORE_FAVORABLE"])
        assertEquals(1, direction["LESS_FAVORABLE"])
        val coverage =
            result.breakdowns
                .first { it.by == "coverage" }
                .rows
                .associate { it["key"] to it["value"] }
        assertEquals(2, coverage["evaluated"])
        assertEquals(3, coverage["opposition"])
    }

    @Test
    fun `KPI-STK-08 promedia la ultima confianza hasta cada fin de mes, sin datos no inventa cero`() {
        val rows = listOf(stakeholder("a"), stakeholder("b"))
        val assessments =
            mapOf(
                "a" to listOf(assessment("a", "2026-03-10", null, 2), assessment("a", "2026-05-10", null, 4)),
                "b" to listOf(assessment("b", "2026-05-15", null, 3))
            )
        val result = StakeholderKpis.trust(rows, assessments, end)
        assertEquals(12, result.series!!.size)
        assertEquals(BigDecimal("3.50"), result.value)
        assertEquals(BigDecimal("2.00"), result.series!!.first { it["period"] == "2026-04" }["value"])
        assertNull(result.series!!.first { it["period"] == "2026-01" }["value"])
    }
}
```

Crear `src/test/kotlin/sgspe/dashboard/EngagementKpisTest.kt` (4 pruebas):

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// los KPI-ENG de F2 (dashboards.md §4.3) con datos ficticios
class EngagementKpisTest {
    private fun engagement(
        id: String,
        type: String,
        date: String,
        total: Long = 0,
        female: Long = 0
    ) = EngagementRow(id, "p", type, "Reunión $id", LocalDate.parse(date), mapOf("attendees_total_count" to total, "attendees_female_count" to female))

    private val end = LocalDate.parse("2026-05-31")

    @Test
    fun `KPI-ENG-01 cuenta las realizadas por tipo y por mes`() {
        val may = listOf(engagement("1", "REUNION", "2026-05-02"), engagement("2", "LLAMADA", "2026-05-03"))
        val window = may + engagement("3", "TALLER", "2026-02-10")
        val result = EngagementKpis.held(may, window, end)
        assertEquals(2, result.value)
        assertEquals(1, result.series!!.first { it["period"] == "2026-02" }["value"])
        assertEquals(12, result.series!!.size)
    }

    @Test
    fun `KPI-ENG-02 cuenta solo reuniones y suma la meta de los meses del periodo`() {
        val may = listOf(engagement("1", "REUNION", "2026-05-02"), engagement("2", "LLAMADA", "2026-05-03"), engagement("3", "ASAMBLEA", "2026-05-20"))
        val targets = EngagementKpis.months(end).map { MonthlyTarget(it, if (it == YearMonth.of(2026, 5)) 3 else 2) }
        val result = EngagementKpis.meetings(may, may, targets, LocalDate.parse("2026-05-01"), end)
        assertEquals(2, result.value)
        assertEquals(3L, result.denominator)
        assertEquals(3L, result.series!!.last()["target"])
    }

    @Test
    fun `KPI-ENG-03 promedia los asistentes de las reuniones informativas`() {
        val meetings =
            listOf(
                engagement("1", "REUNION INFORMATIVA", "2026-05-02", total = 10, female = 4),
                engagement("2", "TALLER", "2026-05-09", total = 20, female = 10)
            )
        val result = EngagementKpis.attendance(meetings, mapOf("1" to listOf("Ronda Ejemplo", "Ronda Ejemplo")))
        assertEquals(BigDecimal("15.00"), result.value)
        assertEquals(30L, result.numerator)
        val first =
            result.breakdowns
                .single { it.by == "engagement" }
                .rows
                .first()
        assertEquals("1", first["key"])
        assertEquals(10L, first["value"])
        assertEquals(BigDecimal("40.0"), first["femalePercent"])
        assertEquals(listOf("Ronda Ejemplo"), first["organizations"])
    }

    @Test
    fun `KPI-ENG-04 y 05 por tema y preguntas absueltas`() {
        val meetings = listOf(engagement("1", "REUNION INFORMATIVA", "2026-05-02"), engagement("2", "REUNION", "2026-05-03"))
        val questions =
            listOf(QuestionRow("1", "t-agua", true), QuestionRow("1", "t-agua", false), QuestionRow("1", null, true), QuestionRow("2", "t-agua", false))
        val topics = EngagementKpis.topics(meetings, mapOf("1" to setOf("t-agua", "t-empleo"), "2" to setOf("t-agua")), questions, mapOf("t-agua" to "Agua"))
        assertEquals(2, topics.value)
        val water =
            topics.breakdowns
                .single()
                .rows
                .first { it["key"] == "t-agua" }
        assertEquals(1, water["meetings"])
        assertEquals(2, water["questions"])
        val answered = EngagementKpis.answered(meetings, questions, mapOf("t-agua" to "Agua"))
        assertEquals(BigDecimal("66.7"), answered.value)
        assertEquals(3, answered.denominator)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.StakeholderKpisTest' --tests 'sgspe.dashboard.EngagementKpisTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'StakeholderKpis'`).

- [ ] **Paso 3: Crear `Indicators.kt`**

Crear `src/main/kotlin/sgspe/dashboard/Indicators.kt`:

```kotlin
package sgspe.dashboard

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.math.RoundingMode

// un desglose: por qué se corta (by) y sus filas. cada fila trae al menos key y value; algunas, más datos (la matriz
// interés × poder lleva x, y y su etiqueta). los nombres van en camelCase (api.md §3.2)
data class Breakdown(
    val by: String,
    val rows: List<Map<String, Any?>>
)

// lo que calcula un indicador: el valor, su numerador y denominador si es un porcentaje, sus desgloses y su serie mensual
data class IndicatorResult(
    val value: Number?,
    val numerator: Number? = null,
    val denominator: Number? = null,
    val breakdowns: List<Breakdown> = emptyList(),
    val series: List<Map<String, Any?>>? = null,
    val warnings: List<String> = emptyList()
)

// la respuesta de un indicador (api.md §4.11): disponible con su cifra, o no disponible con la razón y los campos que
// faltan, nunca cero (RQ-DSH-032). unavailableReason: FIELD_NOT_READABLE, OBJECT_NOT_READABLE o SINGLE_PROJECT_REQUIRED
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IndicatorValue(
    val code: String,
    val label: String,
    val available: Boolean,
    val value: Number? = null,
    val numerator: Number? = null,
    val denominator: Number? = null,
    val unit: String? = null,
    val breakdowns: List<Breakdown>? = null,
    val series: List<Map<String, Any?>>? = null,
    val unavailableReason: String? = null,
    val missingFields: List<String>? = null,
    val warnings: List<String>? = null
)

data class CatalogEntry(
    val code: String,
    val label: String,
    val dashboard: String?,
    val available: Boolean
)

data class DashboardResponse(
    val dashboard: String,
    val filters: Map<String, Any?>,
    val computedAt: String,
    val indicators: List<IndicatorValue>
)

object Numbers {
    // porcentaje con un decimal; null si no hay denominador
    fun percent(
        numerator: Int,
        denominator: Int
    ): BigDecimal? =
        if (denominator == 0) {
            null
        } else {
            BigDecimal(numerator * 100).divide(BigDecimal(denominator), 1, RoundingMode.HALF_UP)
        }

    fun average(values: List<Number>): BigDecimal? =
        if (values.isEmpty()) {
            null
        } else {
            values.map { BigDecimal(it.toString()) }.reduce(BigDecimal::add).divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP)
        }
}
```

- [ ] **Paso 4: Crear `StakeholderKpis.kt`**

Crear `src/main/kotlin/sgspe/dashboard/StakeholderKpis.kt`:

```kotlin
package sgspe.dashboard

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// los datos de un grupo de interés que leen los indicadores, ya filtrados por proyecto, territorio y ámbito
data class StakeholderRow(
    val id: String,
    val state: String?,
    val influenceGroup: String?,
    val scope: String?,
    val type: String?,
    val stance: String?,
    val interestIndex: BigDecimal?,
    val powerIndex: BigDecimal?,
    val person: String?,
    val organization: String?
) {
    val current: Boolean get() = state != "retired"
}

data class AssessmentRow(
    val stakeholder: String,
    val date: LocalDate,
    val stance: String?,
    val trust: Int?,
    val overridden: Boolean,
    val overrideReason: String?,
    val createdAt: Instant?,
    val influenceScore: BigDecimal? = null
)

// un contacto: una interacción realizada con un grupo de interés, por el vínculo M2M o por un participante que asistió
data class Contact(
    val stakeholder: String,
    val engagement: String,
    val date: LocalDate
)

// los KPI-STK de dashboards.md §4.2 como funciones puras, probadas sin base (RQ-DSH-031)
object StakeholderKpis {
    const val NOT_ASSESSED = "SIN EVALUAR"
    val STANCES = listOf("A FAVOR", "NEUTRAL", "EN CONTRA PERO ABIERTO A ESCUCHAR", "EN CONTRA")
    val OPPOSITION = setOf("EN CONTRA", "EN CONTRA PERO ABIERTO A ESCUCHAR")

    // KPI-STK-01: vigentes por grupo de influencia (la forma que dibuja el portal), por grupo × ámbito y por tipo; sin
    // evaluación, «SIN EVALUAR». los nombres de desglose van en snake_case, como los fija la Fase 2.3
    fun byInfluenceGroup(stakeholders: List<StakeholderRow>): IndicatorResult {
        val current = stakeholders.filter { it.current }
        val groups = counts(current.map { it.influenceGroup ?: NOT_ASSESSED })
        val cross =
            current
                .groupBy { (it.influenceGroup ?: NOT_ASSESSED) to (it.scope ?: "SIN AMBITO") }
                .map { (key, rows) -> mapOf("key" to "${key.first}|${key.second}", "influenceGroup" to key.first, "scope" to key.second, "value" to rows.size) }
                .sortedBy { it["key"].toString() }
        return IndicatorResult(
            value = current.size,
            breakdowns =
                listOf(
                    Breakdown("influence_group", groups),
                    Breakdown("influence_group_by_scope", cross),
                    Breakdown("stakeholder_type", counts(current.map { it.type ?: "SIN TIPO" }))
                )
        )
    }

    // KPI-STK-02: vigentes por postura, en el orden del catálogo, más «SIN EVALUAR»
    fun byStance(stakeholders: List<StakeholderRow>): IndicatorResult {
        val current = stakeholders.filter { it.current }
        val order = STANCES + NOT_ASSESSED
        val rows = order.map { stance -> mapOf("key" to stance, "value" to current.count { (it.stance ?: NOT_ASSESSED) == stance }) }
        val byGroup =
            current
                .groupBy { (it.stance ?: NOT_ASSESSED) to (it.influenceGroup ?: NOT_ASSESSED) }
                .map { (key, list) ->
                    mapOf("key" to "${key.first}|${key.second}", "stance" to key.first, "influenceGroup" to key.second, "value" to list.size)
                }
        val byType =
            current
                .groupBy { (it.stance ?: NOT_ASSESSED) to (it.type ?: "SIN TIPO") }
                .map { (key, list) ->
                    mapOf("key" to "${key.first}|${key.second}", "stance" to key.first, "stakeholderType" to key.second, "value" to list.size)
                }
        return IndicatorResult(
            current.size,
            breakdowns = listOf(Breakdown("stance", rows), Breakdown("stance_by_influence_group", byGroup), Breakdown("stance_by_type", byType))
        )
    }

    // KPI-STK-03: una fila por grupo de interés vigente evaluado (key su id, value su influence_score de la última
    // evaluación, interest y power sus índices), con los umbrales del proyecto. numerator son los evaluados y denominator
    // los vigentes: «sin evaluar» es denominator − numerator
    fun matrix(
        stakeholders: List<StakeholderRow>,
        latest: Map<String, AssessmentRow>,
        labels: Map<String, String>,
        interestThreshold: BigDecimal,
        powerThreshold: BigDecimal,
        stanceReadable: Boolean
    ): IndicatorResult {
        val current = stakeholders.filter { it.current }
        val points =
            current
                .filter { it.interestIndex != null && it.powerIndex != null }
                .map { s ->
                    val last = latest[s.id]
                    val point =
                        mutableMapOf<String, Any?>(
                            "key" to s.id,
                            "label" to (labels[s.id] ?: "Grupo de interés"),
                            "value" to last?.influenceScore,
                            "interest" to s.interestIndex,
                            "power" to s.powerIndex,
                            "influenceGroup" to s.influenceGroup,
                            "overridden" to (last?.overridden ?: false),
                            "overrideReason" to last?.overrideReason
                        )
                    if (stanceReadable) point["stance"] = s.stance
                    point
                }
        val thresholds = listOf(mapOf("key" to "interest", "value" to interestThreshold), mapOf("key" to "power", "value" to powerThreshold))
        return IndicatorResult(
            points.size,
            points.size,
            current.size,
            breakdowns = listOf(Breakdown("stakeholder", points), Breakdown("thresholds", thresholds))
        )
    }

    // KPI-STK-04: grupos de interés distintos contactados en el periodo, por sexo, ámbito e institución
    fun contacted(
        contacts: List<Contact>,
        stakeholders: Map<String, StakeholderRow>,
        sexOf: Map<String, String?>,
        institutionsOf: Map<String, List<String>>
    ): IndicatorResult {
        val ids = contacts.map { it.stakeholder }.filter { it in stakeholders }.toSet()
        val sex = ids.map { id -> if (stakeholders.getValue(id).organization != null) "ORGANIZACION" else (sexOf[id] ?: "SIN DATO") }
        val scope = ids.map { stakeholders.getValue(it).scope ?: "SIN AMBITO" }
        val institutions = ids.flatMap { institutionsOf[it].orEmpty().ifEmpty { listOf("SIN INSTITUCION") } }
        return IndicatorResult(
            ids.size,
            breakdowns = listOf(Breakdown("sex", counts(sex)), Breakdown("scope", counts(scope)), Breakdown("institution", counts(institutions)))
        )
    }

    // la postura vigente antes de `date`: la de la última evaluación con fecha anterior
    fun stanceBefore(
        assessments: List<AssessmentRow>,
        date: LocalDate
    ): String? = assessments.filter { it.date.isBefore(date) }.maxWithOrNull(ORDER)?.stance

    // KPI-STK-05: en oposición al inicio del periodo (la vigente antes de periodStart o, sin evaluación, la primera del
    // periodo) con al menos un contacto en el periodo ÷ en oposición al inicio
    fun oppositionContacted(
        stakeholders: List<StakeholderRow>,
        assessments: Map<String, List<AssessmentRow>>,
        contacts: List<Contact>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val contacted = contacts.map { it.stakeholder }.toSet()
        val opposition =
            stakeholders.filter { it.current }.map { it.id }.filter { id ->
                val own = assessments[id].orEmpty()
                val initial = stanceBefore(own, start) ?: own.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.minWithOrNull(ORDER)?.stance
                initial in OPPOSITION
            }
        val numerator = opposition.count { it in contacted }
        return IndicatorResult(Numbers.percent(numerator, opposition.size), numerator, opposition.size)
    }

    // KPI-STK-06: en oposición en la línea base y evaluados en el periodo, los que cambiaron de postura; desglose por
    // dirección (más o menos favorable, en el orden del catálogo) y cobertura (evaluados ÷ en oposición)
    fun oppositionChanged(
        stakeholders: List<StakeholderRow>,
        assessments: Map<String, List<AssessmentRow>>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        var base = 0
        var evaluated = 0
        var changed = 0
        var better = 0
        var worse = 0
        for (s in stakeholders.filter { it.current }) {
            val own = assessments[s.id].orEmpty()
            val baseline = stanceBefore(own, start) ?: continue
            if (baseline !in OPPOSITION) continue
            base++
            val last = own.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }.maxWithOrNull(ORDER) ?: continue
            evaluated++
            val stance = last.stance ?: continue
            if (stance == baseline) continue
            changed++
            if (STANCES.indexOf(stance) < STANCES.indexOf(baseline)) better++ else worse++
        }
        return IndicatorResult(
            Numbers.percent(changed, evaluated),
            changed,
            evaluated,
            breakdowns =
                listOf(
                    Breakdown("direction", listOf(mapOf("key" to "MORE_FAVORABLE", "value" to better), mapOf("key" to "LESS_FAVORABLE", "value" to worse))),
                    Breakdown("coverage", listOf(mapOf("key" to "evaluated", "value" to evaluated), mapOf("key" to "opposition", "value" to base)))
                )
        )
    }

    // KPI-STK-07: contactados en al menos dos interacciones distintas ÷ contactados
    fun repeatedContact(contacts: List<Contact>): IndicatorResult {
        val engagementsBy = contacts.groupBy { it.stakeholder }.mapValues { (_, list) -> list.map { it.engagement }.toSet().size }
        val numerator = engagementsBy.count { it.value >= 2 }
        return IndicatorResult(Numbers.percent(numerator, engagementsBy.size), numerator, engagementsBy.size)
    }

    // KPI-STK-08: para cada fin de mes de los 12 que terminan en `end`, el promedio de la confianza de la última
    // evaluación hasta esa fecha de cada grupo de interés vigente, con su distribución de 1 a 5
    fun trust(
        stakeholders: List<StakeholderRow>,
        assessments: Map<String, List<AssessmentRow>>,
        end: LocalDate
    ): IndicatorResult {
        val current = stakeholders.filter { it.current }.map { it.id }
        val last = YearMonth.from(end)
        val series =
            (11 downTo 0).map { back ->
                val month = last.minusMonths(back.toLong())
                val until = if (back == 0) end else month.atEndOfMonth()
                val scores =
                    current.mapNotNull { id ->
                        assessments[id]
                            .orEmpty()
                            .filter { !it.date.isAfter(until) }
                            .maxWithOrNull(ORDER)
                            ?.trust
                    }
                mapOf(
                    "period" to month.toString(),
                    "value" to Numbers.average(scores),
                    "evaluated" to scores.size,
                    "distribution" to (1..5).associate { it.toString() to scores.count { score -> score == it } }
                )
            }
        return IndicatorResult(series.last()["value"] as BigDecimal?, series = series)
    }

    val ORDER: Comparator<AssessmentRow> = compareBy<AssessmentRow>({ it.date }, { it.createdAt })

    fun counts(keys: List<String>): List<Map<String, Any?>> =
        keys
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { mapOf("key" to it.key, "value" to it.value) }
}
```

- [ ] **Paso 5: Crear `EngagementKpis.kt`**

Crear `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt`:

```kotlin
package sgspe.dashboard

import java.time.LocalDate
import java.time.YearMonth

data class EngagementRow(
    val id: String,
    val project: String?,
    val type: String?,
    val title: String?,
    val heldDate: LocalDate,
    val counts: Map<String, Long?>
)

data class QuestionRow(
    val engagement: String,
    val topic: String?,
    val answered: Boolean
)

// la meta de reuniones de un mes (dashboards.md KPI-ENG-02): la del plan vigente en el mes o, si no hay, la del proyecto
data class MonthlyTarget(
    val month: YearMonth,
    val target: Long
)

// los KPI-ENG de F2 (dashboards.md §4.3) como funciones puras
object EngagementKpis {
    // los tipos formales (RN-ENG-004), que dashboards.md §3.3 cuenta como reunión (#5)
    val MEETINGS = setOf("REUNION", "REUNION INFORMATIVA", "TALLER", "ASAMBLEA", "MESA DE DIALOGO")

    // reunión informativa (#7, #8, #9): REUNION INFORMATIVA o TALLER
    val INFORMATIVE = setOf("REUNION INFORMATIVA", "TALLER")

    // KPI-ENG-01: realizadas en el periodo, por tipo, y su serie mensual de 12 meses
    fun held(
        inPeriod: List<EngagementRow>,
        window: List<EngagementRow>,
        end: LocalDate
    ): IndicatorResult =
        IndicatorResult(
            inPeriod.size,
            breakdowns = listOf(Breakdown("engagement_type", StakeholderKpis.counts(inPeriod.map { it.type ?: "SIN TIPO" }))),
            series = months(end).map { month -> mapOf("period" to month.toString(), "value" to window.count { YearMonth.from(it.heldDate) == month }) }
        )

    // KPI-ENG-02: reuniones realizadas en el periodo frente a la meta; serie de 12 meses con la meta de cada mes
    fun meetings(
        inPeriod: List<EngagementRow>,
        window: List<EngagementRow>,
        targets: List<MonthlyTarget>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val count = inPeriod.count { it.type in MEETINGS }
        val target =
            targets
                .filter { !it.month.atEndOfMonth().isBefore(start) && !it.month.atDay(1).isAfter(end) }
                .sumOf { it.target }
        return IndicatorResult(
            count,
            numerator = count,
            denominator = target,
            breakdowns = listOf(Breakdown("engagement_type", StakeholderKpis.counts(inPeriod.filter { it.type in MEETINGS }.map { it.type!! }))),
            series =
                months(end).map { month ->
                    mapOf(
                        "period" to month.toString(),
                        "value" to window.count { it.type in MEETINGS && YearMonth.from(it.heldDate) == month },
                        "target" to (targets.firstOrNull { it.month == month }?.target ?: 0L)
                    )
                }
        )
    }

    // KPI-ENG-03: por reunión informativa, sus asistentes, porcentajes por sexo y ámbito y las organizaciones que
    // representan; el valor es el promedio del periodo
    fun attendance(
        meetings: List<EngagementRow>,
        organizations: Map<String, List<String>>
    ): IndicatorResult {
        val informative = meetings.filter { it.type in INFORMATIVE }
        val rows =
            informative.sortedBy { it.heldDate }.map { m ->
                val total = m.counts["attendees_total_count"] ?: 0L

                fun pct(field: String): Any? = Numbers.percent((m.counts[field] ?: 0L).toInt(), total.toInt())
                mapOf(
                    "key" to m.id,
                    "label" to m.title,
                    "heldDate" to m.heldDate.toString(),
                    "value" to total,
                    "femalePercent" to pct("attendees_female_count"),
                    "malePercent" to pct("attendees_male_count"),
                    "directArea" to (m.counts["attendees_direct_area_count"] ?: 0L),
                    "indirectArea" to (m.counts["attendees_indirect_area_count"] ?: 0L),
                    "regional" to (m.counts["attendees_regional_count"] ?: 0L),
                    "national" to (m.counts["attendees_national_count"] ?: 0L),
                    "organizations" to organizations[m.id].orEmpty().distinct().sorted()
                )
            }
        val total = informative.sumOf { it.counts["attendees_total_count"] ?: 0L }
        return IndicatorResult(
            Numbers.average(
                informative.map {
                    it.counts["attendees_total_count"] ?: 0L
                }
            ),
            total,
            informative.size,
            listOf(Breakdown("engagement", rows))
        )
    }

    // KPI-ENG-04: por tema, las reuniones informativas del periodo que lo trataron y sus preguntas con ese tema
    fun topics(
        meetings: List<EngagementRow>,
        topicsOf: Map<String, Set<String>>,
        questions: List<QuestionRow>,
        names: Map<String, String>
    ): IndicatorResult {
        val informative = meetings.filter { it.type in INFORMATIVE }.map { it.id }.toSet()
        val treated = informative.flatMap { id -> topicsOf[id].orEmpty().map { it to id } }.groupBy({ it.first }, { it.second })
        val asked = questions.filter { it.engagement in informative && it.topic != null }.groupingBy { it.topic!! }.eachCount()
        val ids = treated.keys + asked.keys
        val rows =
            ids
                .map { id ->
                    mapOf(
                        "key" to id,
                        "label" to (names[id] ?: "Tema"),
                        "value" to treated[id].orEmpty().toSet().size,
                        "meetings" to treated[id].orEmpty().toSet().size,
                        "questions" to (asked[id] ?: 0)
                    )
                }.sortedWith(
                    compareByDescending<Map<String, Any?>> { it["value"] as Int }.thenByDescending { it["questions"] as Int }.thenBy { it["label"].toString() }
                )
        return IndicatorResult(ids.size, breakdowns = listOf(Breakdown("topic", rows)))
    }

    // KPI-ENG-05: preguntas absueltas ÷ preguntas de las reuniones informativas del periodo, con su desglose por tema
    fun answered(
        meetings: List<EngagementRow>,
        questions: List<QuestionRow>,
        names: Map<String, String>
    ): IndicatorResult {
        val informative = meetings.filter { it.type in INFORMATIVE }.map { it.id }.toSet()
        val asked = questions.filter { it.engagement in informative }
        val numerator = asked.count { it.answered }
        val byTopic =
            asked.groupBy { it.topic }.map { (topic, list) ->
                mapOf(
                    "key" to (topic ?: "SIN TEMA"),
                    "label" to (topic?.let { names[it] } ?: "Sin tema"),
                    "value" to Numbers.percent(list.count { it.answered }, list.size),
                    "questions" to list.size
                )
            }
        return IndicatorResult(Numbers.percent(numerator, asked.size), numerator, asked.size, listOf(Breakdown("topic", byTopic)))
    }

    // los 12 meses que terminan en el de `end`
    fun months(end: LocalDate): List<YearMonth> = (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }
}
```

- [ ] **Paso 6: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.StakeholderKpisTest' --tests 'sgspe.dashboard.EngagementKpisTest'
```

Expected: `BUILD SUCCESSFUL`; 10 pruebas en verde.

- [ ] **Paso 7: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/dashboard/StakeholderKpisTest.kt src/test/kotlin/sgspe/dashboard/EngagementKpisTest.kt src/main/kotlin/sgspe/dashboard/Indicators.kt src/main/kotlin/sgspe/dashboard/StakeholderKpis.kt src/main/kotlin/sgspe/dashboard/EngagementKpis.kt
git commit -m "$(cat <<'MSG'
feat(dashboard): cálculo de KPI-STK y KPI-ENG

Funciones puras con desgloses y series para los ocho indicadores de grupos de interés y los cinco
de relacionamiento de F2.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 22: Tableros stakeholders y engagement

`GET /api/sgspe/dashboards/{dashboard}`, `GET /api/sgspe/dashboards/indicators` y
`GET /api/sgspe/dashboards/indicators/{code}` (`docs/api.md` §4.11, `docs/dashboards.md` §6 a §8 y §11). Los filtros
comunes (proyecto, periodo, unidad administrativa, localidad, comunidad, área de influencia, ámbito) con sus valores por
omisión (los proyectos asignados, el mes en curso); los datos se leen con los permisos de quien mira, y un indicador que
exige un campo u objeto que no puede leer es «no disponible» (`FIELD_NOT_READABLE` u `OBJECT_NOT_READABLE`), nunca cero.
KPI-STK-03 exige un solo proyecto (`SINGLE_PROJECT_REQUIRED`).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`
- Crear: `src/main/kotlin/sgspe/dashboard/DashboardData.kt`
- Crear: `src/main/kotlin/sgspe/dashboard/Kpis.kt`
- Crear: `src/main/kotlin/sgspe/dashboard/Dashboards.kt`
- Probar: `src/test/kotlin/sgspe/dashboard/DashboardFiltersTest.kt`
- Probar: `src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt`

**Interfaces:**
- Consume: Tarea 21; `Records`, `Criteria`, `Values`, `Callers` (Tarea 2); `Assignments.projectsOf` (Tarea 5); `ProjectParameters` (Tarea 6); `ModelCatalog.enumValues` (Tarea 1); `StrictQuery` de F1.
- Produce:
  - `interface Indicator { code; label; dashboard; unit; requires; singleProject; suspend fun compute(data: DashboardData): IndicatorResult }`,
    `class Kpi`, `Kpis.DASHBOARDS`, `Kpis.ALL`, `Kpis.BY_CODE`.
  - `GET /api/sgspe/dashboards/{stakeholders|engagement}` → `DashboardResponse(dashboard, filters, computedAt, indicators)`.
  - `GET /api/sgspe/dashboards/indicators` → `List<CatalogEntry(code, label, dashboard, available)>`.
  - `GET /api/sgspe/dashboards/indicators/{code}?breakdown=` → `IndicatorValue`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/DashboardFiltersTest.kt` (3 pruebas):

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.util.LinkedMultiValueMap
import sgspe.common.ModelCatalog
import sgspe.common.UnprocessableContentException
import wasichai.core.common.ValidationException
import java.time.LocalDate
import java.util.UUID

// dashboards.md §3.1: el mes en curso de Lima por defecto, los proyectos por defecto, 422 ante un parámetro desconocido
class DashboardFiltersTest {
    private val catalog = ModelCatalog.parse("""{"enums": {"stakeholder_scope": ["AIDS", "AIIS", "REGIONAL", "NACIONAL"]}}""", """{"roles": []}""")
    private val today = LocalDate.parse("2026-05-20")
    private val assigned = UUID.randomUUID()

    private fun parse(vararg pairs: Pair<String, String>): DashboardFilters =
        runBlocking {
            val params = LinkedMultiValueMap<String, String>()
            pairs.forEach { (k, v) -> params.add(k, v) }
            DashboardFilters.parse(params, DashboardFilters.COMMON, catalog, today) { listOf(assigned) }.resolve()
        }

    @Test
    fun `sin filtros, el mes en curso y los proyectos asignados`() {
        val filters = parse()
        assertEquals(LocalDate.parse("2026-05-01"), filters.periodStart)
        assertEquals(LocalDate.parse("2026-05-31"), filters.periodEnd)
        assertEquals(listOf(assigned), filters.projects)
    }

    @Test
    fun `project se repite y manda sobre las asignaciones`() {
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        assertEquals(listOf(a, b), parse("project" to a.toString(), "project" to b.toString()).projects)
    }

    @Test
    fun `un parametro desconocido es 422 y un valor invalido es 400`() {
        assertThrows<UnprocessableContentException> { parse("projectId" to "x") }
        val error =
            assertThrows<ValidationException> { parse("periodStart" to "2026-06-01", "periodEnd" to "2026-05-01", "scope" to "LOCAL", "project" to "x") }
        assertEquals(setOf("periodStart", "scope", "project"), error.violations.map { it.field }.toSet())
    }
}
```

Crear `src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt` (5 pruebas):

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import tools.jackson.databind.JsonNode

// StakeholdersDashboardApiTest, EngagementDashboardApiTest y DashboardPermissionsApiTest de la matriz de trazabilidad,
// juntas porque comparten los datos: los tableros `stakeholders` y `engagement` (api.md §4.11), el catálogo (RQ-DSH-036)
// y el «no disponible» (RQ-DSH-032)
class DashboardsApiTest : DomainApiTest() {
    private class Data(
        val project: String,
        val manager: Account,
        val officer: Account
    )

    private fun fixture(): Data {
        val project = projectWithCrs(mapOf("meetings_per_month_target" to 3))
        val manager = member("STAKEHOLDER_MANAGER", project)
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val profile =
            mapOf(
                "stakeholder_type" to "AUTORIDAD LOCAL",
                "inclusion_reason" to "Autoridad",
                "owner_user_id" to manager.id,
                "owner_user_email" to manager.email
            )
        val indicators = InfluenceIndicators.high
        for ((stance, date) in listOf("EN CONTRA" to "2026-04-10", "A FAVOR" to "2026-04-12")) {
            val stakeholder = stakeholder(project, profile)
            send(
                "POST",
                "/api/sgspe/stakeholders/$stakeholder/assessments",
                mapOf("attributes" to indicators + mapOf("assessment_date" to date, "basis" to "ENTREVISTA", "stance" to stance, "trust_score" to 3)),
                HttpStatus.CREATED,
                manager.token,
                mapOf(REASON to reason("evaluación"))
            )
        }
        val topic = createRecord("topic", mapOf("code" to uniqueName("T-"), "name" to "Agua", "active" to true))
        val meeting = engagement(project, "REUNION INFORMATIVA", officer.token, mapOf("held_at" to "2026-05-12T15:00:00Z"))
        participant(meeting, officer.token, mapOf("represented_people_count" to 10, "sex" to "FEMENINO", "participant_scope" to "AIDS"))
        participant(meeting, officer.token, mapOf("represented_people_count" to 10, "sex" to "MASCULINO", "participant_scope" to "AIIS"))
        link("engagement", meeting, "engagement_topic", topic, officer.token)
        createRecord(
            "engagement_question",
            mapOf("engagement" to meeting, "question_text" to "¿Y el agua?", "topic" to topic, "answered_on_site" to true),
            token = officer.token
        )
        createRecord("engagement_question", mapOf("engagement" to meeting, "question_text" to "¿Y el empleo?", "topic" to topic), token = officer.token)
        move("engagement", meeting, "register_held_unplanned", officer.token)
        engagement(project, "LLAMADA", officer.token, mapOf("held_at" to "2026-05-13T15:00:00Z")).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        return Data(project, manager, officer)
    }

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    @Test
    fun `el tablero de grupos de interes con sus cinco zonas`() {
        val f = fixture()
        val body =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/stakeholders?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31",
                    null,
                    HttpStatus.OK,
                    f.manager.token
                )
            )
        assertEquals(listOf("KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-06", "KPI-STK-08"), body["indicators"].values().map { it["code"].asString() })
        assertEquals(listOf(f.project), body["filters"]["project"].values().map { it.asString() })
        assertEquals(2, indicator(body, "KPI-STK-01")["value"].asInt())
        val stances = indicator(body, "KPI-STK-02")["breakdowns"][0]["rows"].values().associate { it["key"].asString() to it["value"].asInt() }
        assertEquals(1, stances["EN CONTRA"])
        assertEquals(1, stances["A FAVOR"])
        val groups = indicator(body, "KPI-STK-01")["breakdowns"].values().first { it["by"].asString() == "influence_group" }
        assertEquals(2, groups["rows"].values().first { it["key"].asString() == "ESTRATEGICO" }["value"].asInt())
        val matrix = indicator(body, "KPI-STK-03")
        assertEquals(2, matrix["numerator"].asInt())
        assertEquals(2, matrix["denominator"].asInt())
        assertEquals("stakeholder", matrix["breakdowns"][0]["by"].asString())
        assertEquals(3.0, indicator(body, "KPI-STK-08")["value"].asDouble())
    }

    @Test
    fun `el tablero de relacionamiento cuenta realizadas, reuniones, asistentes, temas e inquietudes`() {
        val f = fixture()
        val body =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/engagement?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31",
                    null,
                    HttpStatus.OK,
                    f.officer.token
                )
            )
        assertEquals(2, indicator(body, "KPI-ENG-01")["value"].asInt())
        val meetings = indicator(body, "KPI-ENG-02")
        assertEquals(1, meetings["value"].asInt())
        assertEquals(3, meetings["denominator"].asInt())
        val attendance = indicator(body, "KPI-ENG-03")
        assertEquals(20, attendance["numerator"].asInt())
        val meeting = attendance["breakdowns"].values().single { it["by"].asString() == "engagement" }["rows"][0]
        assertEquals(50.0, meeting["femalePercent"].asDouble())
        assertEquals(10, meeting["directArea"].asInt())
        assertEquals(1, indicator(body, "KPI-ENG-01")["breakdowns"].values().count { it["by"].asString() == "engagement_type" })
        assertEquals("Agua", indicator(body, "KPI-ENG-04")["breakdowns"][0]["rows"][0]["label"].asString())
        assertEquals(50.0, indicator(body, "KPI-ENG-05")["value"].asDouble())
    }

    @Test
    fun `quien no lee la postura recibe no disponible, nunca cero`() {
        val f = fixture()
        val body = tree(send("GET", "/api/sgspe/dashboards/stakeholders?project=${f.project}", null, HttpStatus.OK, f.officer.token))
        val stance = indicator(body, "KPI-STK-02")
        assertFalse(stance["available"].asBoolean())
        assertEquals("FIELD_NOT_READABLE", stance["unavailableReason"].asString())
        assertEquals(listOf("stakeholder.stance"), stance["missingFields"].values().map { it.asString() })
        assertTrue(stance["value"] == null)
        val point = indicator(body, "KPI-STK-03")["breakdowns"][0]["rows"][0]
        assertFalse(point.has("stance"))
        val catalog =
            tree(send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK, f.officer.token)).values().associate {
                it["code"].asString() to
                    it
            }
        assertFalse(catalog.getValue("KPI-STK-02")["available"].asBoolean())
        assertTrue(catalog.getValue("KPI-ENG-01")["available"].asBoolean())
        assertEquals("engagement", catalog.getValue("KPI-ENG-01")["dashboard"].asString())
        assertTrue(catalog.getValue("KPI-STK-04")["dashboard"].isNull)
    }

    @Test
    fun `el nombre de una persona no llega por la matriz a quien no lo lee`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val person = createRecord("person", mapOf("given_names" to "Persona Ejemplo Tres"))
        val stakeholder =
            stakeholder(
                project,
                mapOf(
                    "person" to person,
                    "stakeholder_type" to "AUTORIDAD LOCAL",
                    "inclusion_reason" to "Autoridad",
                    "owner_user_id" to manager.id,
                    "owner_user_email" to manager.email
                )
            )
        send(
            "POST",
            "/api/sgspe/stakeholders/$stakeholder/assessments",
            mapOf("attributes" to InfluenceIndicators.high + mapOf("assessment_date" to "2026-05-02", "basis" to "ENTREVISTA", "stance" to "NEUTRAL")),
            HttpStatus.CREATED,
            manager.token,
            mapOf(REASON to reason("evaluación"))
        )
        val reader = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-STK-03?project=$project", null, HttpStatus.OK, manager.token))
        assertEquals("Persona Ejemplo Tres", reader["breakdowns"][0]["rows"][0]["label"].asString())
        val analyst = member("ANALYST", project)
        val hidden = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-STK-03?project=$project", null, HttpStatus.OK, analyst.token))
        val row = hidden["breakdowns"][0]["rows"][0]
        assertFalse(row["label"].asString().contains("Persona Ejemplo Tres"))
        assertFalse(row.has("stance"))
    }

    @Test
    fun `KPI-STK-03 exige un solo proyecto, un tablero desconocido es 404 y un parametro desconocido 422`() {
        val f = fixture()
        val other = projectWithCrs()
        val body = tree(send("GET", "/api/sgspe/dashboards/stakeholders?project=${f.project}&project=$other", null, HttpStatus.OK, f.manager.token))
        assertEquals("SINGLE_PROJECT_REQUIRED", indicator(body, "KPI-STK-03")["unavailableReason"].asString())
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/sgspe/dashboards/commitments", token = f.manager.token).status)
        assertEquals(
            HttpStatus.UNPROCESSABLE_CONTENT,
            exchange("GET", "/api/sgspe/dashboards/engagement?projectId=${f.project}", token = f.manager.token).status
        )
        val detail =
            tree(
                send(
                    "GET",
                    "/api/sgspe/dashboards/indicators/KPI-STK-04?project=${f.project}&periodStart=2026-05-01&periodEnd=2026-05-31&breakdown=sex",
                    null,
                    HttpStatus.OK,
                    f.manager.token
                )
            )
        assertEquals(listOf("sex"), detail["breakdowns"].values().map { it["by"].asString() })
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/dashboards/indicators/KPI-STK-04?breakdown=color", token = f.manager.token).status)
    }
}

// los quince indicadores de una evaluación de interés y poder altos
object InfluenceIndicators {
    val high: Map<String, Any?> =
        listOf(
            "affect_environment",
            "affect_livelihood",
            "affect_property_status",
            "opportunity_work",
            "opportunity_development",
            "opportunity_benefits",
            "power_legal_action",
            "power_credibility",
            "power_permit_control",
            "power_action_scope",
            "power_organization_level",
            "power_convening_capacity",
            "power_network_membership",
            "power_group_mobilization",
            "power_other_pressure"
        ).associateWith { 4 }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.DashboardFiltersTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a lo que esta tarea crea (por ejemplo `'DashboardFilters'`).

- [ ] **Paso 3: Crear `DashboardFilters.kt`**

Crear `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.util.MultiValueMap
import sgspe.common.ModelCatalog
import sgspe.common.StrictQuery
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import java.util.UUID

// los filtros comunes de los tableros (dashboards.md §3.1, api.md §4.11). project se repite; sin project, el filtro por
// defecto son las asignaciones vigentes de quien llama o, sin ellas, todos los proyectos que puede leer: una comodidad,
// no un control de acceso (RF-PRJ-005, RQ-SEC-006). el periodo por defecto es el mes en curso de Lima. un parámetro
// desconocido es un 422 (ASM-054); un valor inválido, un 400 que lo nombra
data class DashboardFilters(
    val projects: List<UUID>,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val administrativeUnit: UUID?,
    val locality: UUID?,
    val community: UUID?,
    val areaOfInfluence: UUID?,
    val explorationCampaign: UUID?,
    val scope: String?,
    val breakdown: String?
) {
    // lo que la respuesta devuelve en `filters`: los proyectos efectivos y los filtros que vinieron
    fun echo(): Map<String, Any?> =
        linkedMapOf<String, Any?>(
            "project" to projects.map { it.toString() },
            "periodStart" to periodStart.toString(),
            "periodEnd" to periodEnd.toString(),
            "administrativeUnit" to administrativeUnit?.toString(),
            "locality" to locality?.toString(),
            "community" to community?.toString(),
            "areaOfInfluence" to areaOfInfluence?.toString(),
            "explorationCampaign" to explorationCampaign?.toString(),
            "scope" to scope
        ).filterValues { it != null }

    val hasTerritory: Boolean get() = administrativeUnit != null || locality != null || community != null || areaOfInfluence != null

    companion object {
        val COMMON =
            setOf("project", "periodStart", "periodEnd", "administrativeUnit", "locality", "community", "areaOfInfluence", "explorationCampaign", "scope")

        fun parse(
            params: MultiValueMap<String, String>,
            allowed: Set<String>,
            catalog: ModelCatalog,
            today: LocalDate,
            defaultProjects: suspend () -> List<UUID>
        ): Pending {
            StrictQuery.requireOnly(params, allowed)
            val violations = mutableListOf<FieldViolation>()

            fun uuid(name: String): UUID? =
                params.getFirst(name)?.let { raw ->
                    runCatching { UUID.fromString(raw) }.getOrElse {
                        violations += FieldViolation(name, "debe ser un UUID")
                        null
                    }
                }

            fun date(name: String): LocalDate? =
                params.getFirst(name)?.let { raw ->
                    try {
                        LocalDate.parse(raw)
                    } catch (_: DateTimeParseException) {
                        violations += FieldViolation(name, "debe ser una fecha AAAA-MM-DD")
                        null
                    }
                }
            val projects =
                params["project"].orEmpty().mapNotNull { raw ->
                    runCatching { UUID.fromString(raw) }.getOrElse {
                        violations +=
                            FieldViolation("project", "debe ser un UUID")
                        ; null
                    }
                }
            val start = date("periodStart") ?: YearMonth.from(today).atDay(1)
            val end = date("periodEnd") ?: YearMonth.from(today).atEndOfMonth()
            if (start.isAfter(end)) violations += FieldViolation("periodStart", "no puede ser posterior a periodEnd")
            val scope = params.getFirst("scope")
            val scopes = catalog.enumValues("stakeholder_scope")
            if (scope != null && scopes.isNotEmpty() && scope !in scopes) violations += FieldViolation("scope", "debe ser uno de ${scopes.joinToString(", ")}")
            val pending =
                Pending(
                    projects,
                    start,
                    end,
                    uuid("administrativeUnit"),
                    uuid("locality"),
                    uuid("community"),
                    uuid("areaOfInfluence"),
                    uuid("explorationCampaign"),
                    scope,
                    params.getFirst("breakdown"),
                    defaultProjects
                )
            if (violations.isNotEmpty()) throw ValidationException("Filtro inválido", violations)
            return pending
        }
    }

    // los filtros ya validados, con los proyectos por defecto por resolver (leerlos exige una consulta)
    class Pending(
        private val projects: List<UUID>,
        private val start: LocalDate,
        private val end: LocalDate,
        private val administrativeUnit: UUID?,
        private val locality: UUID?,
        private val community: UUID?,
        private val areaOfInfluence: UUID?,
        private val explorationCampaign: UUID?,
        private val scope: String?,
        private val breakdown: String?,
        private val defaults: suspend () -> List<UUID>
    ) {
        suspend fun resolve(): DashboardFilters =
            DashboardFilters(
                projects.ifEmpty { defaults() }.distinct(),
                start,
                end,
                administrativeUnit,
                locality,
                community,
                areaOfInfluence,
                explorationCampaign,
                scope,
                breakdown
            )
    }
}
```

- [ ] **Paso 4: Crear `DashboardData.kt`**

Crear `src/main/kotlin/sgspe/dashboard/DashboardData.kt`:

```kotlin
package sgspe.dashboard

import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordResponse
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// los conjuntos que leen los indicadores de un pedido, cargados una vez con Records como quien llama (RecordService
// aplica el tenant y los permisos de objeto y de campo) y agregados en Kotlin (dashboards.md §6). los filtros de
// territorio se resuelven a localidades y comunidades: una localidad incluye sus sectores, una unidad administrativa sus
// localidades y unidades hijas, un área de influencia los miembros de esa versión (dashboards.md §3.1). en los indicadores
// de grupos de interés el territorio filtra al grupo de interés (su localidad y su área de influencia); en los de
// relacionamiento, a la interacción (su localidad o su comunidad)
class DashboardData(
    private val records: Records,
    val filters: DashboardFilters,
    // si quien mira puede leer objeto.campo (la definición filtrada por sus permisos, dashboards.md §8)
    val readable: suspend (String) -> Boolean = { true }
) {
    private val cache = mutableMapOf<String, Any>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(
        key: String,
        load: suspend () -> T
    ): T = cache[key] as T? ?: load().also { cache[key] = it }

    // ---- territorio ----

    class Territory(
        val localities: Set<String>,
        val communities: Set<String>,
        val area: String?
    )

    suspend fun territories(): List<Territory> =
        cached("territories") {
            val out = mutableListOf<Territory>()
            filters.locality?.let { out += Territory(withSectors(setOf(it.toString())), emptySet(), null) }
            filters.community?.let { out += Territory(emptySet(), setOf(it.toString()), null) }
            filters.administrativeUnit?.let { out += unitTerritory(setOf(it.toString())) }
            filters.areaOfInfluence?.let { out += areaTerritory(listOf(it.toString())) }
            out
        }

    private suspend fun withSectors(localities: Set<String>): Set<String> {
        if (localities.isEmpty()) return emptySet()
        val sectors = records.all("locality", criteria = listOf(Criteria.anyOf("parent_locality", localities.map(UUID::fromString)))).map { it.id }
        return localities + sectors
    }

    private suspend fun unitTerritory(roots: Set<String>): Territory {
        val units = roots.toMutableSet()
        var frontier = roots
        while (frontier.isNotEmpty()) {
            val children =
                records.all("administrative_unit", criteria = listOf(Criteria.anyOf("parent_unit", frontier.map(UUID::fromString)))).map { it.id }.toSet() -
                    units
            units += children
            frontier = children
        }
        val ids = units.map(UUID::fromString)
        val localities = records.all("locality", criteria = listOf(Criteria.anyOf("administrative_unit", ids))).map { it.id }.toSet()
        val communities = records.all("community", criteria = listOf(Criteria.anyOf("administrative_unit", ids))).map { it.id }.toSet()
        return Territory(withSectors(localities), communities, null)
    }

    private suspend fun areaTerritory(areas: List<String>): Territory {
        if (areas.isEmpty()) return Territory(emptySet(), emptySet(), null)
        val members = records.all("area_of_influence_member", criteria = listOf(Criteria.anyOf("area_of_influence", areas.map(UUID::fromString))))
        val localities = members.mapNotNull { Values.text(it.attributes["locality"]) }.toSet()
        val communities = members.mapNotNull { Values.text(it.attributes["community"]) }.toSet()
        val units = members.mapNotNull { Values.text(it.attributes["administrative_unit"]) }.toSet()
        val fromUnits = if (units.isEmpty()) Territory(emptySet(), emptySet(), null) else unitTerritory(units)
        return Territory(withSectors(localities) + fromUnits.localities, communities + fromUnits.communities, areas.singleOrNull())
    }

    // AIDS y AIIS, en los indicadores de relacionamiento, son los miembros de las áreas aprobadas de ese tipo de los
    // proyectos; REGIONAL y NACIONAL solo filtran grupos de interés (dashboards.md §3.1)
    private var scopeLoaded = false
    private var scopeValue: Territory? = null

    private suspend fun scopeTerritory(): Territory? {
        if (!scopeLoaded) {
            val scope = filters.scope
            scopeValue =
                if (scope == "AIDS" || scope == "AIIS") {
                    val areas =
                        records
                            .all(
                                "area_of_influence",
                                filters = mapOf("area_type" to scope),
                                criteria = listOfNotNull(Criteria.inState(setOf("approved")), projectCriterion())
                            ).map { it.id }
                    areaTerritory(areas)
                } else {
                    null
                }
            scopeLoaded = true
        }
        return scopeValue
    }

    private fun projectCriterion() = if (filters.projects.isEmpty()) null else Criteria.anyOf("project", filters.projects)

    // ---- grupos de interés ----

    suspend fun stakeholderRecords(): List<RecordResponse> =
        cached("stakeholders") {
            if (filters.projects.isEmpty()) return@cached emptyList<RecordResponse>()
            val all = records.all("stakeholder", criteria = listOf(Criteria.anyOf("project", filters.projects)))
            val territories = territories()
            all.filter { s ->
                val locality = Values.text(s.attributes["locality"])
                val area = Values.text(s.attributes["area_of_influence"])
                territories.all { t -> (locality != null && locality in t.localities) || (t.area != null && area == t.area) } &&
                    (filters.scope == null || Values.text(s.attributes["stakeholder_scope"]) == filters.scope)
            }
        }

    suspend fun stakeholders(): List<StakeholderRow> =
        stakeholderRecords().map {
            val a = it.attributes
            StakeholderRow(
                it.id,
                it.state,
                Values.text(a["influence_group"]),
                Values.text(a["stakeholder_scope"]),
                Values.text(a["stakeholder_type"]),
                Values.text(a["stance"]),
                Values.decimal(a["interest_index"]),
                Values.decimal(a["power_index"]),
                Values.text(a["person"]),
                Values.text(a["organization"])
            )
        }

    suspend fun assessments(): Map<String, List<AssessmentRow>> =
        cached("assessments") {
            val ids = stakeholderRecords().map { UUID.fromString(it.id) }
            ids
                .chunked(CHUNK)
                .flatMap { chunk -> records.all("stakeholder_assessment", criteria = listOf(Criteria.anyOf("stakeholder", chunk))) }
                .mapNotNull { r ->
                    val a = r.attributes
                    val date = Values.text(a["assessment_date"])?.let(LocalDate::parse) ?: return@mapNotNull null
                    AssessmentRow(
                        Values.text(a["stakeholder"])!!,
                        date,
                        Values.text(a["stance"]),
                        Values.decimal(a["trust_score"])?.toInt(),
                        Values.bool(a["group_overridden"]) == true,
                        Values.text(a["override_reason"]),
                        r.createdAt,
                        Values.decimal(a["influence_score"])
                    )
                }.groupBy { it.stakeholder }
        }

    // ---- relacionamiento ----

    // las interacciones realizadas de los proyectos con held_at entre dos fechas de Lima, filtradas por territorio
    suspend fun held(
        from: LocalDate,
        to: LocalDate
    ): List<RecordResponse> =
        cached("held:$from:$to") {
            if (filters.projects.isEmpty()) return@cached emptyList<RecordResponse>()
            val start = from.atStartOfDay(LIMA).toInstant()
            val until = to.plusDays(1).atStartOfDay(LIMA).toInstant()
            val all =
                records.all(
                    "engagement",
                    criteria = listOf(Criteria.anyOf("project", filters.projects), Criteria.inState(HELD), Criteria.between("held_at", start, until))
                )
            val territories = territories() + listOfNotNull(scopeTerritory())
            all.filter { e ->
                val locality = Values.text(e.attributes["locality"])
                val community = Values.text(e.attributes["community"])
                territories.all { t -> (locality != null && locality in t.localities) || (community != null && community in t.communities) }
            }
        }

    suspend fun engagementRows(
        from: LocalDate,
        to: LocalDate
    ): List<EngagementRow> =
        held(from, to).map {
            val a = it.attributes
            EngagementRow(
                it.id,
                Values.text(a["project"]),
                Values.text(a["engagement_type"]),
                Values.text(a["title"]),
                Instant.parse(Values.text(a["held_at"])!!).atZone(LIMA).toLocalDate(),
                COUNT_FIELDS.associateWith { f -> Values.decimal(a[f])?.toLong() }
            )
        }

    suspend fun inPeriod(): List<EngagementRow> = engagementRows(filters.periodStart, filters.periodEnd)

    suspend fun window(): List<EngagementRow> = engagementRows(YearMonth.from(filters.periodEnd).minusMonths(11).atDay(1), filters.periodEnd)

    suspend fun participants(engagements: Collection<String>): List<RecordResponse> =
        engagements
            .map(UUID::fromString)
            .chunked(CHUNK)
            .flatMap { chunk -> records.all("engagement_participant", criteria = listOf(Criteria.anyOf("engagement", chunk))) }

    // los contactos del periodo: vínculo M2M engagement_stakeholder o participante con attended (dashboards.md §3.3)
    suspend fun contacts(): List<Contact> =
        cached("contacts") {
            val rows = inPeriod()
            val byId = rows.associateBy { it.id }
            val out = mutableListOf<Contact>()
            for (row in rows) {
                records.related("engagement", UUID.fromString(row.id), "engagement_stakeholder").forEach { out += Contact(it.id, row.id, row.heldDate) }
            }
            participants(byId.keys)
                .filter { Values.bool(it.attributes["attended"]) == true }
                .forEach { p ->
                    val stakeholder = Values.text(p.attributes["stakeholder"]) ?: return@forEach
                    val engagement = byId[Values.text(p.attributes["engagement"])] ?: return@forEach
                    out += Contact(stakeholder, engagement.id, engagement.heldDate)
                }
            out.distinct()
        }

    suspend fun questions(engagements: Collection<String>): List<QuestionRow> =
        engagements
            .map(UUID::fromString)
            .chunked(CHUNK)
            .flatMap { chunk -> records.all("engagement_question", criteria = listOf(Criteria.anyOf("engagement", chunk))) }
            .map { q ->
                QuestionRow(
                    Values.text(q.attributes["engagement"])!!,
                    Values.text(q.attributes["topic"]),
                    Values.bool(q.attributes["answered_on_site"]) == true || Values.text(q.attributes["answered_at"]) != null
                )
            }

    suspend fun topicsOf(engagements: Collection<String>): Map<String, Set<String>> =
        engagements.associateWith { id -> records.related("engagement", UUID.fromString(id), "engagement_topic").map { it.id }.toSet() }

    suspend fun names(
        objectName: String,
        ids: Collection<String>,
        field: String = "name"
    ): Map<String, String> =
        ids
            .distinct()
            .map(UUID::fromString)
            .chunked(CHUNK)
            .flatMap { chunk -> records.all(objectName, ids = chunk) }
            .associate { it.id to (Values.text(it.attributes[field]) ?: "") }

    // el nombre de cada persona, solo si quien mira puede leerlo (permiso de campo, security.md §7.9)
    suspend fun namesOfPeople(ids: Collection<String>): Map<String, String> =
        ids
            .distinct()
            .map(UUID::fromString)
            .chunked(CHUNK)
            .flatMap { chunk -> records.all("person", ids = chunk) }
            .mapNotNull { p ->
                val name = listOfNotNull(Values.text(p.attributes["given_names"]), Values.text(p.attributes["surnames"])).joinToString(" ")
                if (name.isBlank()) null else p.id to name
            }.toMap()

    // los cargos VIGENTE de cada persona: «cargo, organización»
    suspend fun currentPositions(people: Collection<String>): Map<String, List<String>> {
        val positions = vigentPositions(people)
        val organizations = names("organization", positions.mapNotNull { Values.text(it.attributes["organization"]) })
        return positions.groupBy { Values.text(it.attributes["person"])!! }.mapValues { (_, list) ->
            list.map {
                listOfNotNull(
                    Values.text(it.attributes["position_title"]),
                    Values.text(it.attributes["organization"])?.let(organizations::get)
                ).joinToString(", ")
            }
        }
    }

    suspend fun positionOrganizations(people: Collection<String>): Map<String, List<String>> {
        val positions = vigentPositions(people)
        val organizations = names("organization", positions.mapNotNull { Values.text(it.attributes["organization"]) })
        return positions.groupBy { Values.text(it.attributes["person"])!! }.mapValues { (_, list) ->
            list.mapNotNull { Values.text(it.attributes["organization"])?.let(organizations::get) }.distinct()
        }
    }

    private suspend fun vigentPositions(people: Collection<String>): List<RecordResponse> =
        people
            .distinct()
            .map(UUID::fromString)
            .chunked(CHUNK)
            .flatMap { chunk -> records.all("position", filters = mapOf("position_status" to "VIGENTE"), criteria = listOf(Criteria.anyOf("person", chunk))) }

    suspend fun projectRecords(): List<RecordResponse> = cached("projects") { records.all("project", ids = filters.projects) }

    // la meta de cada mes de la ventana: la del plan vigente en el mes o la del proyecto (2 por defecto), sumada entre
    // proyectos (RN-ENG-003)
    suspend fun monthlyTargets(): List<MonthlyTarget> {
        val plans = records.all("engagement_plan", criteria = listOf(Criteria.anyOf("project", filters.projects)))
        return EngagementKpis.months(filters.periodEnd).map { month ->
            val target =
                projectRecords().sumOf { project ->
                    val plan =
                        plans.firstOrNull { p ->
                            Values.text(p.attributes["project"]) == project.id &&
                                (Values.text(p.attributes["start_date"])?.let(LocalDate::parse)?.let { !it.isAfter(month.atEndOfMonth()) } ?: true) &&
                                (Values.text(p.attributes["end_date"])?.let(LocalDate::parse)?.let { !it.isBefore(month.atDay(1)) } ?: true) &&
                                Values.decimal(p.attributes["meetings_per_month_target"]) != null
                        }
                    plan?.let { Values.decimal(it.attributes["meetings_per_month_target"])!!.toLong() }
                        ?: ProjectParameters.integer(project.attributes, "meetings_per_month_target")
                }
            MonthlyTarget(month, target)
        }
    }

    companion object {
        const val CHUNK = 500
        val HELD = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved", "closed")
        val COUNT_FIELDS =
            listOf(
                "attendees_total_count",
                "attendees_female_count",
                "attendees_male_count",
                "attendees_direct_area_count",
                "attendees_indirect_area_count",
                "attendees_regional_count",
                "attendees_national_count"
            )
    }
}
```

- [ ] **Paso 5: Crear `Kpis.kt`**

Crear `src/main/kotlin/sgspe/dashboard/Kpis.kt`:

```kotlin
package sgspe.dashboard

import sgspe.common.ProjectParameters
import sgspe.common.Values

// un KPI de dashboards.md §4. requires: "objeto.campo" que lee; si alguno no es legible para quien llama, el indicador
// sale no disponible. dashboard: el tablero que lo muestra en su fase; null si solo se consulta por su detalle (§11)
interface Indicator {
    val code: String
    val label: String
    val dashboard: String?
    val unit: String
    val requires: List<String>
    val singleProject: Boolean get() = false

    suspend fun compute(data: DashboardData): IndicatorResult
}

// un KPI con su cálculo: los de F2 de dashboards.md §4.2 y §4.3 (RQ-DSH-036). cada fase agrega los suyos a ALL
class Kpi(
    override val code: String,
    override val label: String,
    override val dashboard: String?,
    override val unit: String,
    override val requires: List<String>,
    override val singleProject: Boolean = false,
    private val block: suspend (DashboardData) -> IndicatorResult
) : Indicator {
    override suspend fun compute(data: DashboardData): IndicatorResult = block(data)
}

object Kpis {
    const val STAKEHOLDERS = "stakeholders"
    const val ENGAGEMENT = "engagement"

    // los tableros de F2 y sus zonas, en el orden de dashboards.md §5.3 y §5.4
    val DASHBOARDS: Map<String, List<String>> =
        mapOf(
            STAKEHOLDERS to listOf("KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-06", "KPI-STK-08"),
            ENGAGEMENT to listOf("KPI-ENG-01", "KPI-ENG-02", "KPI-ENG-03", "KPI-ENG-04", "KPI-ENG-05")
        )

    private val CONTACT = listOf("engagement.held_at", "engagement_participant.attended", "engagement_participant.stakeholder")

    val ALL: List<Indicator> =
        listOf(
            Kpi(
                "KPI-STK-01",
                "Grupos de interés por grupo de influencia y ámbito",
                STAKEHOLDERS,
                "grupos de interés",
                listOf("stakeholder.influence_group", "stakeholder.stakeholder_scope", "stakeholder.stakeholder_type")
            ) { StakeholderKpis.byInfluenceGroup(it.stakeholders()) },
            Kpi(
                "KPI-STK-02",
                "Distribución de posturas",
                STAKEHOLDERS,
                "grupos de interés",
                listOf("stakeholder.stance")
            ) { StakeholderKpis.byStance(it.stakeholders()) },
            Kpi(
                "KPI-STK-03",
                "Matriz interés × poder",
                STAKEHOLDERS,
                "grupos de interés",
                listOf(
                    "stakeholder.interest_index",
                    "stakeholder.power_index",
                    "stakeholder.influence_group",
                    "stakeholder_assessment.group_overridden",
                    "project.interest_threshold"
                ),
                singleProject = true
            ) { data ->
                val project = data.projectRecords().single().attributes
                val stakeholders = data.stakeholders()
                StakeholderKpis.matrix(
                    stakeholders,
                    data.assessments().mapValues { (_, list) -> list.maxWith(StakeholderKpis.ORDER) },
                    Labels.of(data, stakeholders),
                    ProjectParameters.decimal(project, "interest_threshold"),
                    ProjectParameters.decimal(project, "power_threshold"),
                    stanceReadable = data.readable("stakeholder.stance")
                )
            },
            Kpi(
                "KPI-STK-04",
                "Grupos de interés contactados",
                null,
                "grupos de interés",
                CONTACT + listOf("stakeholder.stakeholder_scope", "person.sex")
            ) { data ->
                val stakeholders = data.stakeholders()
                StakeholderKpis.contacted(
                    data.contacts(),
                    stakeholders.associateBy { it.id },
                    Labels.sexOf(data, stakeholders),
                    Labels.institutionsOf(data, stakeholders)
                )
            },
            Kpi(
                "KPI-STK-05",
                "Oposición que aceptó conversar",
                null,
                "%",
                CONTACT + listOf("stakeholder_assessment.stance", "stakeholder_assessment.assessment_date")
            ) { StakeholderKpis.oppositionContacted(it.stakeholders(), it.assessments(), it.contacts(), it.filters.periodStart, it.filters.periodEnd) },
            Kpi(
                "KPI-STK-06",
                "Oposición que cambió de postura",
                STAKEHOLDERS,
                "%",
                listOf("stakeholder_assessment.stance", "stakeholder_assessment.assessment_date")
            ) { StakeholderKpis.oppositionChanged(it.stakeholders(), it.assessments(), it.filters.periodStart, it.filters.periodEnd) },
            Kpi("KPI-STK-07", "Grupos de interés con más de un contacto", null, "%", CONTACT) { StakeholderKpis.repeatedContact(it.contacts()) },
            Kpi(
                "KPI-STK-08",
                "Confianza promedio y tendencia",
                STAKEHOLDERS,
                "confianza de 1 a 5",
                listOf("stakeholder_assessment.trust_score", "stakeholder_assessment.assessment_date")
            ) { StakeholderKpis.trust(it.stakeholders(), it.assessments(), it.filters.periodEnd) },
            Kpi("KPI-ENG-01", "Interacciones realizadas", ENGAGEMENT, "interacciones", listOf("engagement.held_at", "engagement.engagement_type")) {
                EngagementKpis.held(it.inPeriod(), it.window(), it.filters.periodEnd)
            },
            Kpi(
                "KPI-ENG-02",
                "Reuniones sostenidas al mes",
                ENGAGEMENT,
                "reuniones",
                listOf("engagement.held_at", "engagement.engagement_type", "engagement_plan.meetings_per_month_target", "project.meetings_per_month_target")
            ) { EngagementKpis.meetings(it.inPeriod(), it.window(), it.monthlyTargets(), it.filters.periodStart, it.filters.periodEnd) },
            Kpi(
                "KPI-ENG-03",
                "Asistentes por reunión informativa",
                ENGAGEMENT,
                "asistentes",
                DashboardData.COUNT_FIELDS.map { "engagement.$it" } +
                    listOf("engagement_participant.organization", "engagement_participant.represented_group", "engagement_participant.attended")
            ) { data ->
                val meetings = data.inPeriod().filter { it.type in EngagementKpis.INFORMATIVE }
                EngagementKpis.attendance(meetings, Labels.organizationsOf(data, meetings.map { it.id }))
            },
            Kpi(
                "KPI-ENG-04",
                "Temas consultados",
                ENGAGEMENT,
                "temas",
                listOf("topic.name", "engagement_question.topic", "engagement.engagement_type")
            ) { data ->
                val meetings = data.inPeriod().filter { it.type in EngagementKpis.INFORMATIVE }
                val ids = meetings.map { it.id }
                val topics = data.topicsOf(ids)
                val questions = data.questions(ids)
                EngagementKpis.topics(meetings, topics, questions, data.names("topic", topics.values.flatten() + questions.mapNotNull { it.topic }))
            },
            Kpi(
                "KPI-ENG-05",
                "Inquietudes resueltas",
                ENGAGEMENT,
                "%",
                listOf("engagement_question.answered_on_site", "engagement_question.answered_at", "engagement_question.engagement")
            ) { data ->
                val meetings = data.inPeriod().filter { it.type in EngagementKpis.INFORMATIVE }
                val questions = data.questions(meetings.map { it.id })
                EngagementKpis.answered(meetings, questions, data.names("topic", questions.mapNotNull { it.topic }))
            }
        )

    val BY_CODE: Map<String, Indicator> = ALL.associateBy { it.code }
}

// las etiquetas y los cortes que necesitan leer actores: el nombre de una persona solo si quien mira puede leerlo; si no,
// su cargo vigente y su organización (dashboards.md §5.3, RQ-SEC-010)
object Labels {
    suspend fun of(
        data: DashboardData,
        stakeholders: List<StakeholderRow>
    ): Map<String, String> {
        val people = data.namesOfPeople(stakeholders.mapNotNull { it.person })
        val organizations = data.names("organization", stakeholders.mapNotNull { it.organization })
        val positions = data.currentPositions(stakeholders.mapNotNull { it.person })
        return stakeholders.associate { s ->
            s.id to
                when {
                    s.organization != null -> organizations[s.organization] ?: "Organización"
                    s.person != null -> people[s.person] ?: positions[s.person]?.firstOrNull() ?: "Persona"
                    else -> "Grupo de interés"
                }
        }
    }

    suspend fun sexOf(
        data: DashboardData,
        stakeholders: List<StakeholderRow>
    ): Map<String, String?> {
        val sex = data.names("person", stakeholders.mapNotNull { it.person }, "sex")
        return stakeholders.filter { it.person != null }.associate { it.id to sex[it.person]?.ifEmpty { null } }
    }

    suspend fun institutionsOf(
        data: DashboardData,
        stakeholders: List<StakeholderRow>
    ): Map<String, List<String>> {
        val organizations = data.names("organization", stakeholders.mapNotNull { it.organization })
        val byPerson = data.positionOrganizations(stakeholders.mapNotNull { it.person })
        return stakeholders.associate { s ->
            s.id to (s.organization?.let { listOfNotNull(organizations[it]) } ?: byPerson[s.person].orEmpty())
        }
    }

    suspend fun organizationsOf(
        data: DashboardData,
        engagements: List<String>
    ): Map<String, List<String>> {
        val participants = data.participants(engagements).filter { Values.bool(it.attributes["attended"]) == true }
        val names = data.names("organization", participants.mapNotNull { Values.text(it.attributes["organization"]) })
        return participants
            .groupBy { Values.text(it.attributes["engagement"])!! }
            .mapValues { (_, list) ->
                list.mapNotNull { p -> Values.text(p.attributes["organization"])?.let { names[it] } ?: Values.text(p.attributes["represented_group"]) }
            }
    }
}
```

- [ ] **Paso 6: Crear `Dashboards.kt`**

Crear `src/main/kotlin/sgspe/dashboard/Dashboards.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.common.ValidationException
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los indicadores de módulo y sus tableros (api.md §4.11, dashboards.md §6 a §8): se calculan en vivo con los permisos
// de quien mira. antes de calcular, cada KPI comprueba que puede leer los objetos y campos que usa; si no, sale no
// disponible con la razón y los campos que faltan, nunca cero (RQ-DSH-032). nada se guarda en el servidor (§6.8)
@Service
class Dashboards(
    private val records: Records,
    private val metadata: MetadataService,
    private val callers: Callers,
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val clock: Clock
) {
    suspend fun catalog(): List<CatalogEntry> {
        val readable = Readability(metadata)
        return Kpis.ALL.map { CatalogEntry(it.code, it.label, it.dashboard, readable.missing(it.requires).isEmpty()) }
    }

    suspend fun dashboard(
        name: String,
        params: org.springframework.util.MultiValueMap<String, String>
    ): DashboardResponse {
        val codes = Kpis.DASHBOARDS[name] ?: throw NotFoundException("El tablero $name no existe o no está disponible en esta fase")
        val filters = filters(params, DashboardFilters.COMMON)
        val readable = Readability(metadata)
        val data = DashboardData(records, filters, readable::canRead)
        return DashboardResponse(name, filters.echo(), clock.instant().toString(), codes.map { evaluate(Kpis.BY_CODE.getValue(it), data, readable) })
    }

    suspend fun indicator(
        code: String,
        params: org.springframework.util.MultiValueMap<String, String>
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw NotFoundException("El indicador $code no existe o no está disponible en esta fase")
        val filters = filters(params, DashboardFilters.COMMON + "breakdown")
        val readable = Readability(metadata)
        val value = evaluate(kpi, DashboardData(records, filters, readable::canRead), readable)
        val by = filters.breakdown ?: return value
        val kept = value.breakdowns.orEmpty().filter { it.by == by }
        if (value.available && kept.isEmpty()) {
            throw ValidationException("Desglose inválido", "breakdown", "$code se desglosa por ${value.breakdowns.orEmpty().joinToString(", ") { it.by }}")
        }
        return value.copy(breakdowns = kept)
    }

    private suspend fun evaluate(
        kpi: Indicator,
        data: DashboardData,
        readable: Readability
    ): IndicatorValue {
        val missing = readable.missing(kpi.requires)
        if (missing.isNotEmpty()) {
            val reason = if (missing.any { readable.objectMissing(it) }) "OBJECT_NOT_READABLE" else "FIELD_NOT_READABLE"
            return IndicatorValue(kpi.code, kpi.label, available = false, unavailableReason = reason, missingFields = missing)
        }
        if (kpi.singleProject && data.filters.projects.size != 1) {
            return IndicatorValue(kpi.code, kpi.label, available = false, unavailableReason = "SINGLE_PROJECT_REQUIRED", missingFields = listOf("project"))
        }
        val result = kpi.compute(data)
        return IndicatorValue(
            code = kpi.code,
            label = kpi.label,
            available = true,
            value = result.value,
            numerator = result.numerator,
            denominator = result.denominator,
            unit = kpi.unit,
            breakdowns = result.breakdowns,
            series = result.series,
            warnings = result.warnings
        )
    }

    private suspend fun filters(
        params: org.springframework.util.MultiValueMap<String, String>,
        allowed: Set<String>
    ): DashboardFilters {
        val user = callers.require()
        return DashboardFilters
            .parse(params, allowed, catalog, LocalDate.now(clock.withZone(LIMA))) {
                assignments.projectsOf(user.userId).ifEmpty { records.all("project").map { UUID.fromString(it.id) } }
            }.resolve()
    }
}

// qué objetos y campos puede leer quien mira: la definición de cada objeto filtrada por sus permisos
// (MetadataService.definitionOf, la misma vista que usan la interfaz y el agente, dashboards.md §8). sin READ sobre el
// objeto, ningún campo suyo es legible. se consulta una vez por objeto en cada pedido
class Readability(
    private val metadata: MetadataService
) {
    private val fields = mutableMapOf<String, Set<String>?>()

    private suspend fun fieldsOf(objectName: String): Set<String>? =
        fields.getOrPut(objectName) {
            try {
                metadata
                    .definitionOf(objectName)
                    .fields
                    .map { it.name }
                    .toSet()
            } catch (_: ForbiddenException) {
                null
            } catch (_: NotFoundException) {
                null
            }
        }

    suspend fun canRead(path: String): Boolean {
        val (objectName, field) = path.split('.', limit = 2).let { it[0] to it.getOrNull(1) }
        val readable = fieldsOf(objectName) ?: return false
        return field == null || field in readable
    }

    suspend fun missing(requires: List<String>): List<String> = requires.filterNot { canRead(it) }

    suspend fun objectMissing(path: String): Boolean = fieldsOf(path.substringBefore('.')) == null
}

@RestController
@RequestMapping("/api/sgspe/dashboards")
class DashboardsController(
    private val dashboards: Dashboards
) {
    @GetMapping("/indicators")
    suspend fun catalog(request: ServerHttpRequest): List<CatalogEntry> {
        StrictQuery.requireOnly(request.queryParams, emptySet())
        return dashboards.catalog()
    }

    @GetMapping("/indicators/{code}")
    suspend fun indicator(
        @PathVariable code: String,
        request: ServerHttpRequest
    ): IndicatorValue = dashboards.indicator(code, LinkedMultiValueMap(request.queryParams))

    @GetMapping("/{dashboard}")
    suspend fun dashboard(
        @PathVariable dashboard: String,
        request: ServerHttpRequest
    ): DashboardResponse = dashboards.dashboard(dashboard, LinkedMultiValueMap(request.queryParams))
}
```

- [ ] **Paso 7: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.DashboardFiltersTest'
```

Expected: `BUILD SUCCESSFUL`; 3 pruebas en verde.

- [ ] **Paso 8: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.dashboard.DashboardsApiTest'
```

Expected: `BUILD SUCCESSFUL`; 5 pruebas en verde.

- [ ] **Paso 9: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/dashboard/DashboardFiltersTest.kt src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt src/main/kotlin/sgspe/dashboard/DashboardFilters.kt src/main/kotlin/sgspe/dashboard/DashboardData.kt src/main/kotlin/sgspe/dashboard/Kpis.kt src/main/kotlin/sgspe/dashboard/Dashboards.kt
git commit -m "$(cat <<'MSG'
feat(dashboard): tableros de grupos de interés y de relacionamiento

Filtros comunes con sus valores por omisión, datos leídos con los permisos de quien mira y «no
disponible» cuando falta un permiso; catálogo y detalle de indicadores.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 23: Exportación del registro de grupos de interés

`POST /api/sgspe/exports/{kind}` con `kind = stakeholder-register` (`docs/api.md` §4.11, `docs/deliverables.md`
§9 y §10, `docs/security.md` §14 «Salidas»): un XLSX con Apache POI, con nivel de divulgación. `INTERNO` trae el registro
leído con los permisos de quien exporta (un campo que no puede leer sale «no disponible»); `EXTERNO` exige
`checklistConfirmed` y cita a las personas por un código (`GI-xxxxxxxx`) sin nombres; `PUBLICO` trae solo agregados.
Nunca sale el documento de identidad. Antes de entregar el archivo se escribe la fila de `export_log` (append-only).
Exige `READ` y `EXPORT` sobre `stakeholder` y `CREATE` sobre `export_log`; con un solo proyecto, la fila lleva el
proyecto y la exportación exige asignación a él. `filters.project` vacío es «mis proyectos» (las asignaciones vigentes de
quien exporta; el ADMIN, todos), como lo fija la Fase 2.3.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/privacy/Disclosure.kt`
- Crear: `src/main/kotlin/sgspe/privacy/ExportLog.kt`
- Crear: `src/main/kotlin/sgspe/privacy/StakeholderRegister.kt`
- Crear: `src/main/kotlin/sgspe/privacy/ExportController.kt`
- Probar: `src/test/kotlin/sgspe/privacy/StakeholderRegisterApiTest.kt`

**Interfaces:**
- Consume: `Records`, `Permissions`, `Callers`, `Criteria`, `StrictBody`, `Values`, `LIMA`, `Clock` (Tarea 2 y F1); Apache POI (Tarea 1); el alcance de la Tarea 5 sobre `export_log.project`.
- Produce: `POST /api/sgspe/exports/stakeholder-register` → `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` con `Content-Disposition: attachment`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/privacy/StakeholderRegisterApiTest.kt` (4 pruebas):

```kotlin
package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Salidas» y deliverables.md §13.2 (RQ-REP-014, RQ-REP-022, RQ-AUD-011): el registro de grupos de
// interés en XLSX con su nivel de divulgación y su fila de export_log; EXTERNO sin nombres de personas; nunca el documento
// de identidad; PUBLICO solo agregados; sin EXPORT, 403
class StakeholderRegisterApiTest : DomainApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String
    ) = client
        .post()
        .uri("/api/sgspe/exports/stakeholder-register")
        .header("Authorization", token)
        .bodyValue(body)
        .exchange()
        .expectBody(ByteArray::class.java)
        .returnResult()

    private fun cells(bytes: ByteArray): List<String> =
        XSSFWorkbook(bytes.inputStream()).use { workbook ->
            workbook.flatMap { sheet -> sheet.flatMap { row -> row.map { it.toString() } } }
        }

    @Test
    fun `INTERNO nombra a las personas, EXTERNO las cita por codigo y ninguno trae el documento`() {
        val project = projectWithCrs()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val person = createRecord("person", mapOf("given_names" to "Persona Ejemplo Uno", "id_document_number" to "00000001"))
        stakeholder(project, mapOf("person" to person))
        val internal = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "INTERNO"), manager.token)
        assertEquals(200, internal.status.value())
        assertTrue(
            internal.responseHeaders.contentDisposition.filename!!
                .endsWith(".xlsx")
        )
        val internalCells = cells(internal.responseBody!!)
        assertTrue("Persona Ejemplo Uno" in internalCells)
        assertFalse(internalCells.any { "00000001" in it })
        val missing =
            exchange(
                "POST",
                "/api/sgspe/exports/stakeholder-register",
                mapOf(
                    "filters" to mapOf("project" to listOf(project)),
                    "disclosureLevel" to "EXTERNO"
                ),
                manager.token
            )
        assertEquals(listOf("checklistConfirmed"), problemFields(missing.body))
        val external =
            export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "EXTERNO", "checklistConfirmed" to true), manager.token)
        val externalCells = cells(external.responseBody!!)
        assertFalse(externalCells.any { "Persona Ejemplo" in it })
        assertTrue(externalCells.any { it.startsWith("GI-") })
        val log = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${manager.id}&sort=exported_at&dir=desc", null, HttpStatus.OK))["content"]
        assertEquals(2, log.size())
        assertEquals("EXTERNO", log[0]["attributes"]["disclosure_level"].asString())
        assertEquals("EXPORTACION", log[0]["attributes"]["export_kind"].asString())
        assertEquals(1, log[0]["attributes"]["row_count"].asInt())
        assertTrue(log[0]["attributes"]["filters_description"].asString().contains("lista de verificación"))
    }

    @Test
    fun `quien no lee la postura ni los nombres los ve no disponibles, PUBLICO trae solo agregados, y sin EXPORT 403`() {
        val project = projectWithCrs()
        stakeholder(project)
        stakeholder(project, mapOf("person" to createRecord("person", mapOf("given_names" to "Persona Ejemplo Dos"))))
        val analyst = member("ANALYST", project)
        val analystCells = cells(export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "INTERNO"), analyst.token).responseBody!!)
        assertTrue(NOT_AVAILABLE in analystCells)
        // ANALYST no lee person.given_names (security.md §7.9): el nombre no sale por la relación stakeholder → person
        assertFalse(analystCells.any { "Persona Ejemplo Dos" in it })
        val public = cells(export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "PUBLICO"), analyst.token).responseBody!!)
        assertTrue("Grupos de interés" in public)
        assertFalse("Asociación Ejemplo" in public)
        val manager = member("PROJECT_MANAGER", project)
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange("POST", "/api/sgspe/exports/stakeholder-register", mapOf("disclosureLevel" to "INTERNO"), manager.token).status
        )
        assertEquals(HttpStatus.NOT_FOUND, exchange("POST", "/api/sgspe/exports/commitment-matrix", mapOf("disclosureLevel" to "INTERNO")).status)
        assertEquals(
            HttpStatus.FORBIDDEN,
            exchange("POST", "/api/objects/export_log/records", mapOf("attributes" to mapOf("export_kind" to "EXPORTACION"))).status
        )
    }

    @Test
    fun `exportar un solo proyecto deja el proyecto en export_log y exige asignacion a el`() {
        val project = projectWithCrs()
        stakeholder(project)
        val outsider = userWithRole("ANALYST")
        val denied =
            exchange(
                "POST",
                "/api/sgspe/exports/stakeholder-register",
                mapOf(
                    "filters" to mapOf("project" to listOf(project)),
                    "disclosureLevel" to "INTERNO"
                ),
                outsider.token
            )
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assign(outsider, project, "ANALYST")
        assertEquals(200, export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "INTERNO"), outsider.token).status.value())
        val log = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${outsider.id}", null, HttpStatus.OK))["content"][0]
        assertEquals(project, log["attributes"]["project"].asString())
    }

    @Test
    fun `una lista de proyectos vacia es mis proyectos`() {
        val mine = projectWithCrs()
        stakeholder(mine)
        stakeholder(projectWithCrs())
        val analyst = member("ANALYST", mine)
        assertEquals(200, export(mapOf("filters" to mapOf("project" to emptyList<String>()), "disclosureLevel" to "INTERNO"), analyst.token).status.value())
        val log = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${analyst.id}", null, HttpStatus.OK))["content"][0]
        assertEquals(1, log["attributes"]["row_count"].asInt())
        assertEquals(mine, log["attributes"]["project"].asString())
        val outsider = userWithRole("ANALYST")
        assertEquals(200, export(mapOf("filters" to mapOf("project" to emptyList<String>()), "disclosureLevel" to "INTERNO"), outsider.token).status.value())
        val none = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${outsider.id}", null, HttpStatus.OK))["content"][0]
        assertEquals(0, none["attributes"]["row_count"].asInt())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.privacy.StakeholderRegisterApiTest'
```

Expected: FAIL: `/api/sgspe/exports/stakeholder-register` responde 404.

- [ ] **Paso 3: Crear `Disclosure.kt`**

Crear `src/main/kotlin/sgspe/privacy/Disclosure.kt`:

```kotlin
package sgspe.privacy

// los niveles de divulgación (catálogo «Nivel de divulgación», ASM-037, deliverables.md §9): INTERNO nominal, EXTERNO
// con las personas por cargo o código y sin datos personales, PUBLICO solo con agregados. en ningún nivel salen el
// documento de identidad ni los teléfonos ni los correos (RQ-REP-014, RQ-REP-022)
enum class Disclosure {
    INTERNO,
    EXTERNO,
    PUBLICO;

    companion object {
        fun parse(raw: Any?): Disclosure? = entries.firstOrNull { it.name == raw?.toString() }
    }
}

// el valor que se escribe donde quien exporta no puede leer el campo: el mismo criterio que los indicadores (RQ-DSH-032)
const val NOT_AVAILABLE = "no disponible"
```

- [ ] **Paso 4: Crear `ExportLog.kt`**

Crear `src/main/kotlin/sgspe/privacy/ExportLog.kt`:

```kotlin
package sgspe.privacy

import org.springframework.stereotype.Component
import sgspe.common.Records
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.util.UUID

// una fila de export_log por cada salida (RQ-AUD-011, deliverables.md §9.5): quién, qué, con qué nivel, qué filtros y
// cuántas filas. export_log es appendOnly y apiOnly: solo lo escriben los endpoints de sgspe, como el usuario, con la
// marca. export_kind distingue exportación, emisión y descarga (estas dos, desde F8)
@Component
class ExportLog(
    private val records: Records,
    private val clock: Clock
) {
    suspend fun export(
        user: AuthenticatedUser,
        objectName: String,
        format: String,
        disclosure: Disclosure,
        filters: String,
        rows: Int,
        project: UUID?
    ): String =
        records
            .create(
                OBJECT,
                mapOf(
                    "exported_at" to clock.instant().toString(),
                    "exporter_user_id" to user.userId.toString(),
                    "exporter_user_email" to user.email,
                    "export_kind" to EXPORT,
                    "object_name" to objectName,
                    "file_format" to format,
                    "disclosure_level" to disclosure.name,
                    "filters_description" to filters,
                    "row_count" to rows,
                    "project" to project?.toString()
                ),
                "exportación $objectName ${disclosure.name}"
            ).id

    companion object {
        const val OBJECT = "export_log"
        const val EXPORT = "EXPORTACION"
        const val ISSUE = "EMISION"
        const val DOWNLOAD = "DESCARGA"
    }
}
```

- [ ] **Paso 5: Crear `StakeholderRegister.kt`**

Crear `src/main/kotlin/sgspe/privacy/StakeholderRegister.kt`:

```kotlin
package sgspe.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Values
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// un archivo listo para descargar y borrar después de enviarlo
class ExportFile(
    val file: File,
    val filename: String
)

// la exportación stakeholder-register (api.md §4.13, RQ-REP-022, deliverables.md §10): el registro de grupos de interés
// con su evaluación vigente, leído con RecordService como quien exporta (solo los registros y campos que puede leer; un
// campo ilegible sale «no disponible»), con el nivel de divulgación aplicado al modelo antes de escribir. exige READ y
// EXPORT sobre stakeholder y CREATE sobre export_log antes de leer nada; EXTERNO exige checklistConfirmed. la fila de
// export_log se escribe antes de enviar el archivo: no hay salida sin registro (RQ-AUD-011)
@Service
class StakeholderRegister(
    private val records: Records,
    private val permissions: Permissions,
    private val callers: Callers,
    private val metadata: MetadataService,
    private val log: ExportLog,
    private val clock: Clock
) {
    suspend fun export(body: Map<String, Any?>): ExportFile {
        StrictBody.requireOnly(body, setOf("filters", "disclosureLevel", "checklistConfirmed"))
        val filters = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(filters, FILTERS, "filters.")
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw ValidationException("Nivel de divulgación inválido", "disclosureLevel", "debe ser INTERNO, EXTERNO o PUBLICO")
        val confirmed = body["checklistConfirmed"] == true
        if (disclosure == Disclosure.EXTERNO && !confirmed) {
            throw ValidationException(
                "Falta la lista de verificación",
                "checklistConfirmed",
                "una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)"
            )
        }
        val requested = uuids(filters["project"], "filters.project")
        val user = callers.require()
        permissions.require(
            user,
            "Exportar el registro de grupos de interés",
            Actions.READ to STAKEHOLDER,
            "EXPORT" to STAKEHOLDER,
            Actions.CREATE to ExportLog.OBJECT
        )
        // sin proyectos, «mis proyectos» (la forma que fija la Fase 2.3): los de las asignaciones vigentes de quien exporta;
        // el ADMIN, todos
        val projects = requested.ifEmpty { if (user.isAdmin) null else myProjects(user) }
        val stakeholders = if (projects?.isEmpty() == true) emptyList() else stakeholders(projects.orEmpty(), filters)
        val file = Files.createTempFile("sgspe-register-", ".xlsx").toFile()
        try {
            withContext(Dispatchers.IO) {
                SXSSFWorkbook(100).use { workbook ->
                    if (disclosure == Disclosure.PUBLICO) aggregates(workbook, stakeholders) else register(workbook, stakeholders, disclosure)
                    file.outputStream().use { workbook.write(it) }
                }
            }
            val description =
                buildString {
                    append("filtros: ").append(filters.toSortedMap())
                    if (disclosure == Disclosure.EXTERNO) append("; lista de verificación de EXTERNO confirmada")
                }
            log.export(user, STAKEHOLDER, "XLSX", disclosure, description, stakeholders.size, projects?.singleOrNull())
            return ExportFile(file, "registro-grupos-de-interes-${disclosure.name.lowercase()}.xlsx")
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }

    // los proyectos de las asignaciones vigentes de quien exporta, hoy en America/Lima (security.md §9.2). privacy no
    // depende de sgspe.scope (architecture.md §5.3): lee project_assignment con Records
    private suspend fun myProjects(user: AuthenticatedUser): List<UUID> {
        val today = LocalDate.now(clock.withZone(LIMA))
        return records
            .all("project_assignment", filters = mapOf("member_user_id" to user.userId.toString()))
            .filter { assignment ->
                val start = Values.text(assignment.attributes["start_date"])?.let(LocalDate::parse)
                val end = Values.text(assignment.attributes["end_date"])?.let(LocalDate::parse)
                Values.bool(assignment.attributes["active"]) == true &&
                    (start == null || !today.isBefore(start)) &&
                    (end == null || !today.isAfter(end))
            }.mapNotNull { Records.uuid(it.attributes["project"]) }
            .distinct()
    }

    private suspend fun stakeholders(
        projects: List<UUID>,
        filters: Map<String, Any?>
    ): List<RecordResponse> {
        val equality = mutableMapOf<String, String>()
        Values.text(filters["scope"])?.let { equality["stakeholder_scope"] = it }
        Values.text(filters["stakeholderType"])?.let { equality["stakeholder_type"] = it }
        Values.text(filters["influenceGroup"])?.let { equality["influence_group"] = it }
        Values.text(filters["locality"])?.let { equality["locality"] = it }
        val criteria = if (projects.isEmpty()) emptyList() else listOf(Criteria.anyOf("project", projects))
        val includeRetired = filters["includeRetired"] == true
        return records
            .all(STAKEHOLDER, filters = equality, criteria = criteria)
            .filter { includeRetired || it.state != "retired" }
    }

    private suspend fun register(
        workbook: SXSSFWorkbook,
        stakeholders: List<RecordResponse>,
        disclosure: Disclosure
    ) {
        val readable =
            metadata
                .definitionOf(STAKEHOLDER)
                .fields
                .map { it.name }
                .toSet()
        val people = byIds("person", stakeholders.mapNotNull { Values.text(it.attributes["person"]) })
        val organizations = byIds("organization", stakeholders.mapNotNull { Values.text(it.attributes["organization"]) })
        val projects = byIds("project", stakeholders.mapNotNull { Values.text(it.attributes["project"]) })
        val localities = byIds("locality", stakeholders.mapNotNull { Values.text(it.attributes["locality"]) })
        val positions = positionsOf(people.keys)
        val sheet = workbook.createSheet("Registro")
        val header =
            listOf(
                "Proyecto",
                "Código",
                "Grupo de interés",
                "Tipo de actor",
                "Cargos vigentes",
                "Tipo de grupo de interés",
                "Ámbito",
                "Localidad",
                "Estado",
                "Estado de la relación",
                "Estado de entrevista",
                "Grupo de influencia",
                "Índice de interés",
                "Índice de poder",
                "Postura",
                "Prioridad informativa",
                "Confianza",
                "Última evaluación",
                "Último contacto"
            ) + if (disclosure == Disclosure.INTERNO) listOf("Responsable") else emptyList()
        sheet.createRow(0).let { row -> header.forEachIndexed { i, title -> row.createCell(i).setCellValue(title) } }
        stakeholders.forEachIndexed { index, s ->
            val a = s.attributes
            val person = Values.text(a["person"])?.let { people[it] }
            val organization = Values.text(a["organization"])?.let { organizations[it] }
            val code = "GI-${s.id.take(8).uppercase()}"
            val name =
                when {
                    organization != null -> Values.text(organization.attributes["name"]) ?: NOT_AVAILABLE
                    disclosure == Disclosure.EXTERNO -> code
                    person != null -> personName(person) ?: NOT_AVAILABLE
                    else -> NOT_AVAILABLE
                }

            fun field(name: String): String = if (name in readable) Values.text(a[name]).orEmpty() else NOT_AVAILABLE
            val values =
                listOf(
                    Values
                        .text(a["project"])
                        ?.let { projects[it]?.attributes?.get("code") }
                        ?.toString()
                        .orEmpty(),
                    code,
                    name,
                    if (organization != null) "ORGANIZACION" else "PERSONA",
                    Values
                        .text(a["person"])
                        ?.let { positions[it] }
                        .orEmpty()
                        .joinToString("; "),
                    field("stakeholder_type"),
                    field("stakeholder_scope"),
                    Values
                        .text(a["locality"])
                        ?.let { localities[it]?.attributes?.get("name") }
                        ?.toString()
                        .orEmpty(),
                    s.state.orEmpty(),
                    field("relationship_status"),
                    field("interview_status"),
                    field("influence_group"),
                    field("interest_index"),
                    field("power_index"),
                    field("stance"),
                    field("information_priority"),
                    field("trust_score"),
                    field("last_assessment_date"),
                    field("last_contact_date")
                ) + if (disclosure == Disclosure.INTERNO) listOf(field("owner_user_email")) else emptyList()
            val row = sheet.createRow(index + 1)
            values.forEachIndexed { i, value -> row.createCell(i).setCellValue(value) }
        }
    }

    // PUBLICO: solo conteos por grupo de influencia y postura, y por ámbito (deliverables.md §9.1)
    private suspend fun aggregates(
        workbook: SXSSFWorkbook,
        stakeholders: List<RecordResponse>
    ) {
        val readable =
            metadata
                .definitionOf(STAKEHOLDER)
                .fields
                .map { it.name }
                .toSet()
        val sheet = workbook.createSheet("Agregados")
        sheet.createRow(0).let { row ->
            listOf("Grupo de influencia", "Postura", "Grupos de interés").forEachIndexed { i, t -> row.createCell(i).setCellValue(t) }
        }
        val groups =
            stakeholders
                .groupBy {
                    (Values.text(it.attributes["influence_group"]) ?: "SIN EVALUAR") to
                        (if ("stance" in readable) Values.text(it.attributes["stance"]) ?: "SIN EVALUAR" else NOT_AVAILABLE)
                }.toSortedMap(compareBy({ it.first }, { it.second }))
        var index = 1
        for ((key, list) in groups) {
            val row = sheet.createRow(index++)
            row.createCell(0).setCellValue(key.first)
            row.createCell(1).setCellValue(key.second)
            row.createCell(2).setCellValue(list.size.toDouble())
        }
        val scopes = workbook.createSheet("Por ámbito")
        scopes.createRow(0).let { row -> listOf("Ámbito", "Grupos de interés").forEachIndexed { i, t -> row.createCell(i).setCellValue(t) } }
        stakeholders
            .groupBy { Values.text(it.attributes["stakeholder_scope"]) ?: "SIN AMBITO" }
            .toSortedMap()
            .entries
            .forEachIndexed { i, (scope, list) ->
                val row = scopes.createRow(i + 1)
                row.createCell(0).setCellValue(scope)
                row.createCell(1).setCellValue(list.size.toDouble())
            }
    }

    private fun personName(person: RecordResponse): String? =
        listOfNotNull(Values.text(person.attributes["given_names"]), Values.text(person.attributes["surnames"])).joinToString(" ").ifBlank { null }

    private suspend fun byIds(
        objectName: String,
        ids: List<String>
    ): Map<String, RecordResponse> =
        ids
            .distinct()
            .map(UUID::fromString)
            .chunked(500)
            .flatMap { records.all(objectName, ids = it) }
            .associateBy { it.id }

    // «cargo, organización» de los cargos vigentes de cada persona; en EXTERNO es como se cita a una persona
    private suspend fun positionsOf(people: Collection<String>): Map<String, List<String>> {
        if (people.isEmpty()) return emptyMap()
        val positions =
            records.all("position", filters = mapOf("position_status" to "VIGENTE"), criteria = listOf(Criteria.anyOf("person", people.map(UUID::fromString))))
        val organizations = byIds("organization", positions.mapNotNull { Values.text(it.attributes["organization"]) })
        return positions.groupBy { Values.text(it.attributes["person"])!! }.mapValues { (_, list) ->
            list.map {
                listOfNotNull(
                    Values.text(it.attributes["position_title"]),
                    Values.text(it.attributes["organization"])?.let { id -> organizations[id]?.attributes?.get("name")?.toString() }
                ).joinToString(", ")
            }
        }
    }

    private fun uuids(
        raw: Any?,
        field: String
    ): List<UUID> {
        val values =
            when (raw) {
                null -> emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values.map { value ->
            runCatching {
                UUID.fromString(value.toString())
            }.getOrElse { throw ValidationException("Filtro inválido", listOf(FieldViolation(field, "debe ser un UUID o una lista de UUID"))) }
        }
    }

    companion object {
        const val STAKEHOLDER = "stakeholder"
        val FILTERS = setOf("project", "scope", "stakeholderType", "influenceGroup", "locality", "includeRetired")
    }
}
```

- [ ] **Paso 6: Crear `ExportController.kt`**

Crear `src/main/kotlin/sgspe/privacy/ExportController.kt`:

```kotlin
package sgspe.privacy

import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import wasichai.core.common.NotFoundException

// POST /api/sgspe/exports/{kind} (api.md §4.13): el XLSX en streaming, con su fila de export_log ya escrita. el archivo
// temporal se borra al terminar de enviarlo. los tipos de F3 y F4 se agregan con su módulo
@RestController
@RequestMapping("/api/sgspe/exports")
class ExportController(
    private val register: StakeholderRegister
) {
    @PostMapping("/{kind}")
    suspend fun export(
        @PathVariable kind: String,
        @RequestBody body: Map<String, Any?>
    ): ResponseEntity<Flux<DataBuffer>> {
        val export =
            when (kind) {
                "stakeholder-register" -> register.export(body)
                else -> throw NotFoundException("La exportación $kind no existe o no está disponible en esta fase")
            }
        val stream =
            DataBufferUtils
                .read(FileSystemResource(export.file), DefaultDataBufferFactory.sharedInstance, 64 * 1024)
                .doFinally { export.file.delete() }
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(XLSX))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.filename}\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(stream)
    }

    companion object {
        const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
```

- [ ] **Paso 7: Correr las de integración**

Run (con el túnel abierto):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.privacy.StakeholderRegisterApiTest'
```

Expected: `BUILD SUCCESSFUL`; 4 pruebas en verde.

- [ ] **Paso 8: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/privacy/StakeholderRegisterApiTest.kt src/main/kotlin/sgspe/privacy/Disclosure.kt src/main/kotlin/sgspe/privacy/ExportLog.kt src/main/kotlin/sgspe/privacy/StakeholderRegister.kt src/main/kotlin/sgspe/privacy/ExportController.kt
git commit -m "$(cat <<'MSG'
feat(privacy): registro de grupos de interés en XLSX con nivel de divulgación

INTERNO con los permisos de quien exporta, EXTERNO sin nombres y con lista de verificación, PUBLICO
solo agregados; nunca el documento de identidad. Cada exportación deja su fila en export_log.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

### Tarea 24: Cierre: vigía de arquitectura, CLAUDE.md y la corrida completa

`ArchitectureTest` vigila que el escaneo vea las fuentes de F2, `CLAUDE.md` dice dónde está cada cosa nueva y
se corre todo: unitarias, integración, ktlint y el build.

**Archivos:**
- Modificar: `src/test/kotlin/sgspe/ArchitectureTest.kt`
- Modificar: `CLAUDE.md`

**Interfaces:**
- Consume: Todas las tareas anteriores.
- Produce: Nada nuevo.

- [ ] **Paso 1: Modificar `ArchitectureTest.kt`**

Modificar `src/test/kotlin/sgspe/ArchitectureTest.kt`: en la prueba `` `el escaneo ve las fuentes de F1` ``, reemplazar

```kotlin
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt")), "$names")
```

por

```kotlin
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt", "ProjectScopeGuard.kt", "Assessments.kt")), "$names")
```

- [ ] **Paso 2: Modificar `CLAUDE.md`**

Modificar `CLAUDE.md`, sección «Dónde está cada cosa»: reemplazar la fila

```markdown
| Marca de escritura, `Records`, Lima, 422 | `src/main/kotlin/sgspe/common/` |
```

por

```markdown
| Marca de escritura, `Records`, Lima, 422, catálogo del modelo, reglas y guardas transversales, `If-Match`, consulta en seco, JOB-02 | `src/main/kotlin/sgspe/common/` |
| Alcance por proyecto, asignaciones, destinatarios por rol, `EDIT_ANY`, WF-A | `src/main/kotlin/sgspe/scope/` |
| Evaluaciones, índices y grupo, prioridad, WF-B, JOB-03 | `src/main/kotlin/sgspe/stakeholder/` |
| WF-C, conteos de asistentes | `src/main/kotlin/sgspe/engagement/` |
| CRS, área oficial, áreas de influencia, importaciones, componentes, capas básicas | `src/main/kotlin/sgspe/territory/` |
| KPI-STK, KPI-ENG y tableros | `src/main/kotlin/sgspe/dashboard/` |
| Exportaciones con nivel de divulgación y `export_log` | `src/main/kotlin/sgspe/privacy/` |
| Base de las pruebas de integración de F2 | `src/test/kotlin/sgspe/DomainApiTest.kt` |
```

- [ ] **Paso 3: Correr las unitarias**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ArchitectureTest'
```

Expected: `BUILD SUCCESSFUL`; 11 pruebas en verde.

- [ ] **Paso 4: Correr todo**

Run (con el túnel abierto):

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintCheck test build -x integrationTest
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun
```

Expected: `BUILD SUCCESSFUL` en las dos; ninguna prueba falla ni se omite fuera de las de F1 que ya se omitían.

- [ ] **Paso 5: ktlint y commit**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat ktlintCheck
git add src/test/kotlin/sgspe/ArchitectureTest.kt CLAUDE.md
git commit -m "$(cat <<'MSG'
docs: dónde está cada cosa de F2 y vigía de las fuentes nuevas

ArchitectureTest comprueba que el escaneo ve ProjectScopeGuard.kt y Assessments.kt; CLAUDE.md
nombra los paquetes de F2.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

Expected: `ktlintCheck` sin hallazgos y el commit creado.

---

## Cobertura de la fila F2 de la hoja de ruta (alcance de este plan)

| Elemento de `docs/roadmap.md` (F2) | Tarea |
|---|---|
| `ProjectScopeGuard` (opción A), `AssignmentRoleGuard` | 5 |
| `ProjectWriteGuard` | 6 |
| `EditAnyGuard` | 9 |
| `DerivedFieldsGuard`, `TransitionReasonGuard`, `ExactlyOneGuard`, `UserReferenceGuard`, `FieldChangeReasonGuard`, `CommandTransitionGuard` | 3 |
| `DerivedFieldsJob` (JOB-02) | 8 (conteos) y 15 (áreas) |
| `InfluenceClassifier`, `Prioritizer` | 10 |
| `StakeholderWriteGuard` | 11 |
| `StakeholderFollowUpJob` (JOB-03) | 13 |
| `EngagementWriteGuard` | 7 |
| `AttendanceCountsListener` | 8 |
| Importación XLSX, CSV y GeoJSON con CRS de origen y reconciliación | 17 y 18 |
| Área oficial en ha, `AreaListener`, `TerritoryWriteGuard` | 14 y 15 |
| Indicadores KPI-STK-01 a KPI-STK-08 y KPI-ENG-01 a KPI-ENG-05; tableros `stakeholders` y `engagement` | 21 y 22 |
| Exportaciones y `export_log`; `stakeholder-register` con niveles de divulgación | 23 |
| `POST /api/sgspe/transition-checks` | 6 |
| `POST /api/sgspe/territory/imports` y `…/{importBatchId}/confirm` | 18 |
| `POST /api/sgspe/project-components` y `PATCH /api/sgspe/project-components/{id}` | 19 |
| `POST /api/sgspe/areas-of-influence/{id}/approve` | 16 |
| `GET /api/sgspe/territory/layers/{layer}` (básicas) | 20 |
| `POST /api/sgspe/stakeholders/{id}/assessments` y `…/preview` | 12 |
| `GET /api/sgspe/dashboards/indicators`, `…/indicators/{code}` y `…/{dashboard}` | 22 |
| `POST /api/sgspe/exports/{kind}` (`stakeholder-register`) | 23 |
| `Idempotency-Key` aceptado e ignorado | 12 (prueba) |
| Pruebas de seguridad de F2: edición de lo propio, alcance opción A, destinatarios por rol, importaciones | 9, 5, 5 y 18 |
| Pruebas GIS de `docs/gis.md` §9 que no dependen de F6 | 14, 15, 16, 18, 19 y 20 |
| Concurrencia (UPS-04): If-Match y candados | 4, 12, 16 y 18 |
| Enumeración de organizaciones en JOB-02 y JOB-03 (UPS-17) | 8 (`Organizations`, vigilado en la Tarea 1) |

Fuera de este plan: el modelo, los workflows, los roles, el script del ubigeo, las pruebas de workflow sin guarda, la
matriz de permisos y los permisos de campo (Fase 2.1); el portal y Playwright (Fase 2.3).
