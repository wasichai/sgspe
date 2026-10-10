# SGSPE · Fase 4, plan 4.4a (alcance de lectura por proyecto: documentos, modelo y backend): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Cerrar UPS-01 por proyecto antes de F5: que cada persona lea solo los registros de los proyectos a los que está
asignada, en todas las rutas de lectura de wasichai y de SGSPE, con un `RecordReadScope` propio (`ProjectReadScope`), y
dejar el despliegue listo para varios clientes con UPS-10 (`separate-provisioning` y `MANAGE_TENANTS`) y un
aprovisionamiento por tenant con script.

**Architecture:** `sgspe.scope` gana `ProjectReadScope` (el segundo `RecordReadScope`, junto a
`ConfidentialAttentionScope`) y su condición pura `ProjectReadCondition`: una subconsulta SQL sobre la tabla de
`project_assignment` vigente de quien llama, sin lecturas desde Kotlin; `ProjectScope` clasifica los 72 objetos (proyecto,
asignaciones, 31 raíces, 19 hijos y 20 maestros del tenant). `sgspe.common` gana `ProjectReach` («mis proyectos» o todos,
por la acción declarada `READ_ALL_PROJECTS` sobre `project`, sin literales de rol), que comparten el alcance, los tableros,
el reporte unificado y las exportaciones, y `SweepTiming` (la duración por organización de los trabajos).
`IssueDocumentFilter` cubre también las dos lecturas de documentos emitidos. `ProjectScopeGuard` se queda como segunda
línea en la escritura. `model/` gana `provision_tenant.py`, `upgrade_tenants.py` y `tenant_steps.py`.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 0.6.0 (sin
starters nuevos: `RecordReadScope` es de `wasichai-core` y `DocumentService` de `wasichai-documents`, ya en el classpath por
`wasichai-spring-boot-starter-documents`), R2DBC, Jackson 3 (`tools.jackson`), JUnit 5 con `wasichai-test`
(`WasichaiIntegrationTest`); Python 3 con solo la biblioteca estándar en `model/`.

**Spec:** `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md` (vinculante): objetivo, restricciones
globales, §1, §2 (decisiones del usuario 1 a 3 y elecciones 1 a 8), §3.1 a §3.11 y §3.13, §4, §5, §6 (fila 4.4a) y §7. La
evaluación `.superpowers/sdd/fase-4/ups-01-evaluacion.md` trae la evidencia en el código de wasichai 0.6.0. Lo que la
especificación asigna a 4.4b (el portal, §3.12) queda fuera.

**Confirmación del usuario (2026-10-10).** La «pregunta abierta» de §7 de la especificación queda resuelta: el usuario
aprobó la especificación con `READ_ALL_PROJECTS` **solo para `AUDITOR` y `PROJECT_ADMIN`**; `SOCIAL_MANAGER` queda acotado
a sus asignaciones y se asigna a cada proyecto que conduce. La Tarea 1 cierra la pregunta también en la especificación, y
la Tarea 3 lo fija con una prueba (`test_leen_todos_los_proyectos_solo_auditor_y_project_admin`).

## Global Constraints

- **Repositorio y rama.** Todo el trabajo en `~/IdeaProjects/sgspe`, en la rama `feat/fase-4-4a-alcance` creada desde
  `main` con la especificación 4.4 integrada (hoy en `docs/fase-4-4-alcance`). El plan 4.4b (`../sgspe-ui`) parte de esta
  rama ya integrada en `main`.
- **Versiones.** Las de F4, sin cambios: wasichai `0.6.0` exacto, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle
  `9.8.0`, ktlint `1.7.1`. Ningún starter nuevo.
- **Idioma.** Español (es-PE) en documentos, comentarios, mensajes y `detail`; nombres técnicos en inglés (ADR-0009);
  constantes de la API en inglés UPPER_SNAKE (`READ_ALL_PROJECTS`, `NOT_ASSIGNED`).
- **Arquitectura.** Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`,
  `AuditService` ni `ObjectSchemaManager`; `scope` y `privacy` solo dependen de `common`; los tenants solo con
  `Organizations`; ningún literal de rol en `src/main/kotlin`: quién lee todo el tenant sale de `model/roles.json` por la
  acción declarada (`ArchitectureTest`). Ningún import sin uso: ktlint de este repositorio no los quita.
- **Lecturas.** Nunca `asPlatform` dentro de una petición; los trabajos siguen corriendo como la plataforma, sin alcance.
- **SQL.** Toda condición con valores enlazados (`bind`), nunca concatenados; los nombres de tabla y columna salen de la
  definición (`WasichaiSchemas.dataTable`, `SqlIdentifier.quote`). La columna del objeto leído va sin alias (el SQL del
  store de wasichai no usa alias); las subconsultas califican siempre las suyas (`a.`, `t.`).
- **Errores (ASM-108).** Un registro fuera del alcance responde `404` (detalle, cambio, borrado, transición, historial,
  `POST .../related` con un extremo ilegible, documentos); un valor `RELATION` que nombra un registro ilegible es el `400` de
  wasichai con `errors[{field: <campo>, message: "no record with this id"}]` (D30, `RelationTargets`); `403 NOT_ASSIGNED`
  queda para quien lee sin estar asignado. Comprobado en wasichai 0.6.0: `RelatedRecordService.kt` lanza
  `NotFoundException` y `RelationTargets.kt` lanza `ValidationException` con `"no record with this id"`.
- **Datos de prueba.** Ficticios: correos `@sgspe.test`, «Proyecto Ejemplo A» y «Proyecto Ejemplo B», «Asociación
  Ejemplo», «Cliente Ejemplo»; ningún nombre real de persona en nada versionado.
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde
  `~/IdeaProjects/sgspe`; `./gradlew ktlintFormat` antes de `build`. El token nunca va a un archivo ni a la salida. Con poca
  memoria, `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Integración.** Las clases `*ApiTest` necesitan PostgreSQL: **corren en la CI del PR** y, si se quiere antes, las corre
  el coordinador, nunca un subagente. Los subagentes no tocan bases de datos, Docker, el túnel ni el tenant de desarrollo:
  compilan (`compileTestKotlin` o `build`, que compila también las de integración) y entregan la tarea marcada
  «integración pendiente» con las clases de su último paso. Con el túnel abierto, el coordinador:
  `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'`.
  La primera clase de una JVM aprovisiona el modelo de F4 (más de 8 minutos por el túnel); `sgspe_sdd_test` no la usan dos
  suites a la vez.
- **Pasos del coordinador.** Los marcados «(coordinador)» —medición con `EXPLAIN ANALYZE`, trabajos con tres tenants,
  actualizar el tenant de desarrollo, la suite por el túnel— exigen base, túnel o tenant de desarrollo: los hace el
  coordinador principal, nunca un subagente (Tarea 17).
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres); al
  final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas; nunca
  `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni el inventario real de tenants. `yarn format` antes de commitear
  yaml o json. Push y PR en la Tarea 18.

### Comandos

Unitarias de una clase (sin base):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase>'
```

Build completo (ktlint, todas las unitarias y la compilación de las de integración):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Modelo, documentos y formato:

```bash
cd ~/IdeaProjects/sgspe/model && python3 -m unittest && python3 apply.py --validate-only
cd ~/IdeaProjects/sgspe && python3 tools/check_traceability.py && yarn format:check
```

## Review Focus

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la
tarea dueña del código:

1. **Un documento emitido como puerta trasera.** wasichai-documents solo pide READ sobre el objeto y devuelve el `snapshot`
   congelado: sin el filtro, quien no lee un compromiso de otro proyecto lee su acta. Pruebas: `IssueDocumentFilterTest`
   «un documento cuyo registro no se lee es el 404 de wasichai y no llega al controlador» (Tarea 8) y
   `ProjectReadScopeApiTest` «el feature GIS, el archivo de una evidencia y los documentos de B responden 404» (Tarea 11).
2. **Quien da de alta un proyecto y no puede leerlo.** El alta de un `project` no tiene equipo: sin `READ_ALL_PROJECTS`,
   wasichai respondería el alta con el registro ilegible y el portal se quedaría sin el proyecto recién creado. Pruebas:
   `test_quien_crea_proyectos_lee_todos_los_proyectos` (Tarea 3) y `ProjectScopeApiTest` «el alta de un proyecto y las
   asignaciones no piden asignacion previa», que sigue en verde tras la migración (Tarea 13).
3. **Un objeto nuevo de F5 a F8 sin clasificar.** Quedaría ilegible para todos (falla cerrado) o, mal clasificado como
   maestro, abierto. Pruebas: `ArchitectureTest` «cada objeto del modelo esta clasificado en ProjectScope» (Tarea 4) y
   `ProjectReadScopeTest` «un objeto sin clasificar no admite ningun registro» (Tarea 7).
4. **Una asignación que vence o empieza en la frontera del día de Lima.** A las 03:00 UTC todavía es el día anterior en
   Lima; la persona deja de ver el proyecto pero tiene que seguir viendo su asignación vencida (el portal le dice por qué).
   Pruebas: `ProjectReachTest` «mis proyectos son las asignaciones vigentes hoy en Lima, sin repetidos» (Tarea 5),
   `ProjectReadConditionTest` (la fecha enlazada, Tarea 6) y `ProjectReadScopeApiTest` «una asignacion vencida deja de dar
   alcance pero la persona la sigue leyendo» (Tarea 11).
5. **Quien lee todo y exporta un proyecto ajeno.** La fila de `export_log` llevaría ese `project` y `ProjectScopeGuard`
   respondería `403` después de armar el archivo. Prueba: `ProjectReadScopeApiTest` «quien lee todo y exporta un proyecto
   ajeno lo exporta y la fila de export_log no lleva el proyecto» (Tarea 11), con un rol de prueba con `EXPORT` y
   `READ_ALL_PROJECTS` (hoy ningún rol de `roles.json` tiene los dos).

---

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | §7 de la especificación deja una «pregunta abierta» sobre `SOCIAL_MANAGER` | Resuelta por el usuario el 2026-10-10: `READ_ALL_PROJECTS` solo para `AUDITOR` y `PROJECT_ADMIN`. La Tarea 1 reemplaza la pregunta por la decisión en la especificación; la Tarea 3 lo prueba |
| 2 | §3.6 de la especificación dice `400` para un vínculo M2M a un registro de otro proyecto | Comprobado en wasichai 0.6.0: `POST .../related` con un extremo ilegible responde `404` (`RelatedRecordService.kt`, «out of scope looks missing too»); un campo `RELATION` responde `400` con `errors[{field, message: "no record with this id"}]` (`RelationTargets.kt`). La Tarea 1 corrige §3.6 de la especificación y la Tarea 2, `api.md` §3.3; las pruebas esperan esas formas |
| 3 | §3.4 deja abierto si `DocumentService` se puede inyectar en `sgspe.scope` | Se puede: `wasichai-documents` llega en `compile` por `wasichai-spring-boot-starter-documents` y `DocumentService` es un bean de `WasichaiDocumentsAutoConfiguration`. `DocumentReadByScope` lo usa (`byId` ya exige READ sobre el objeto y da el `snapshot.objectName` y el `recordId`); no hace falta decorar la respuesta. `ArchitectureTest` no lo prohíbe (solo `sgspe.*` fuera de `common`) |
| 4 | §3.1 habla de «las 29 raíces con `project` obligatorio» contando `project_assignment` | En el código, `ProjectScope.ROOTS` tiene 31 (28 obligatorias más `evidence`, `early_warning_rule` y `export_log`) y `project_assignment` es su propio grupo (`ReadGroup.ASSIGNMENT`): 1 + 1 + 31 + 19 + 20 = 72. `ArchitectureTest` lo fija |
| 5 | La regla «solo `common` y `scope` llaman a `ProjectReach.readsAll`» choca con la exención de `ExportProjects` (`privacy`) | `ProjectReach` gana `reaches(user, project)` (lee todo o está asignado): `privacy` la usa y nunca llama a `readsAll` |
| 6 | `AttentionReport` (`sgspe.attention`) tiene el mismo respaldo «sin asignaciones, todos los proyectos» que `Dashboards` | También pasa a `ProjectReach.mine` (Tarea 9). `Assignments.projectsOf` queda sin uso y se borra |
| 7 | §3.6 pide que el `409 HAS_DEPENDENTS` no nombre el objeto dependiente | El `detail` pasa a «No se puede borrar <objeto>: otros registros todavía dependen de él» (Tarea 10). El `field` sigue siendo `<objeto>.<campo>` del primer dependiente, porque es el contrato de `api.md` §3.3 que usa el portal (`http.ts`, `RegistrosSimples.tsx`): se señala en el PR para que el usuario decida si 4.4b lo cambia |
| 8 | `GET /api/audit` pide READ de todo el tenant (`AuditQueryService.kt`): en `roles.json` solo `AUDITOR`, que está exento | `ProjectReadScopeApiTest` prueba la auditoría con un rol de prueba con `*` READ y sin `READ_ALL_PROJECTS` |
| 9 | `IssueAccessByScope` respondía `403 OBJECT_NOT_READABLE` también a un registro que no existe | Un registro que no existe o que está fuera del alcance responde el `404` de wasichai (`NotFoundException("Record <id> does not exist")`); `OBJECT_NOT_READABLE` queda para quien no tiene READ sobre el objeto (Tarea 8; `api.md` §3.3 en la Tarea 2) |
| 10 | §3.13 pide en `ProjectReadScopeApiTest` el caso de `ConfidentialRelease` dentro de A | Ya lo prueba `StakeholderRequestWorkflowApiTest` (cierre de una solicitud confidencial por su compromiso, en el proyecto de la clase): corre con el alcance nuevo en la CI del PR y se nombra en el lote 1 de la Tarea 13; no se duplica |
| 11 | §6 pone todos los documentos en la tarea 1 | Se dividen en dos tareas paralelas (archivos disjuntos): Tarea 1 (IDs, ADR y especificación) y Tarea 2 (`security.md` y los demás). La celda `PAD` de `security.md` §7.2 y la línea «Acciones declaradas» de `project` en `database-model.md` van con el modelo en la Tarea 3, porque `test_apply_roles.py` y `test_model.py` las comparan con `roles.json` y `model.json`: un commit con solo una mitad dejaría el modelo en rojo. `CLAUDE.md` va al cierre (Tarea 18) y `docs/develop/README.md` con su código (Tareas 15 y 16) |
| 12 | §6 junta «rendimiento y medición de trabajos» en una tarea | El código (la línea de log por organización, `SweepTiming`) es la Tarea 14, de un subagente; las mediciones y la actualización del tenant de desarrollo son del coordinador (Tarea 17). El `WebFilter` que abre `RequestMemo` para toda petición queda condicionado a la medición, con su código en la Tarea 17 |
| 13 | §3.10 pide «una línea de log por organización» en cada barrido | Los cuatro barridos que recorren organizaciones (`DeadlineSweep`, `WarningSweep`, `FollowUpSweep`, `DerivedSweep`); `RetentionSweep` no recorre organizaciones |
| 14 | La vista previa de una importación de predios (`land_parcel`, del tenant) lee el `project` para su CRS de trabajo (`TerritoryImports.kt`) | Quien no lee el proyecto recibe `404`: el predio es del tenant pero la importación es de un proyecto. `TerritoryImportApiTest` lo espera (Tarea 13) y el PR lo señala |
| 15 | §3.4 del cálculo: un documento de un registro borrado | Para quien no es `ADMIN`, falla cerrado (`404`): sin el registro no se puede saber de qué proyecto es. `ADMIN` no pasa por el alcance |
| 16 | `ExportProjects` leía con tope (`sgspe.exports.max-rows`) | `ProjectReach.assigned` lee sin tope: son las asignaciones de una sola persona |
| 17 | §3.5 pone en `test_model.py` las reglas «quien crea proyectos tiene `READ_ALL_PROJECTS`» y «solo sobre `project`» | Van en `test_apply_roles.py` (`RolesJsonTests`), donde se prueba `roles.json`; `test_model.py` ya compara las acciones de `model.json` con las fichas de `database-model.md`, y `apply_roles.validate` ya rechaza una acción declarada sobre otro objeto |
| 18 | Un objeto creado fuera de `model.json` (`createObject` en las pruebas, `/admin`) no está en `ProjectScope` | `ReadGroup.UNCLASSIFIED`: nadie que no sea `ADMIN` lo lee (falla cerrado, con una advertencia por lectura). Las pruebas que crean objetos propios los leen como `ADMIN` o `AUDITOR`, o esperan el `403` por permiso, que llega antes (`RolesApiTest`, `ErrorContractApiTest`, `TenantIsolationApiTest`); el PR lo señala |

## Lo que este plan toma de la especificación y de la evaluación

- **Mecanismo** (evaluación §1 y §4): `RecordReadScopes.criteria` une los criterios de todos los beans con AND y entre
  paréntesis detrás de `organization_id` (y de `created_by`); no se pide para `ADMIN`, la plataforma ni las
  automatizaciones. La firma es `suspend fun criterion(caller: AuthenticatedUser, definition: ObjectDefinition):
  RecordCriterion?` y `RecordCriterion` es `fun interface { fun condition(definition: ObjectDefinition, bind: (Any) ->
  String): String }` (`wasichai-core`, `RecordReadScope.kt` y `RecordQueryContributor.kt` en `v0.6.0`).
- **Rutas** (evaluación §1): listas, totales, búsqueda, detalle, `PUT`/`PATCH`/`DELETE`, relacionados y `POST/DELETE
  .../related`, destinos de `RELATION`, historial, `/api/audit`, transiciones, features GIS, archivos y agente. Las que
  cubre SGSPE: los dos `GET` de documentos.
- **Modelo** (§4): `READ_ALL_PROJECTS` («Leer todos los proyectos») declarada en `project`; `AUDITOR` y `PROJECT_ADMIN`.
- **Orden de despliegue** (§4): el tenant se actualiza antes de desplegar el backend (Tarea 17).

## Interfaces con el plan 4.4b y con el portal

Lo que este plan deja y el portal (4.4b) consume, además de `api.md`:

- `GET /api/auth/me/permissions` lista `READ_ALL_PROJECTS` en `objects.project` para `AUDITOR` y `PROJECT_ADMIN` (y no
  para los demás): `ProjectReadScopeApiTest` «AUDITOR, PROJECT_ADMIN y ADMIN leen los dos proyectos sin asignacion, y me
  permissions lo dice» (Tarea 11). El `ADMIN` se reconoce por `admin: true`.
- Cada persona lee sus propias asignaciones aunque estén vencidas (`member_user_id = quien llama`) y, de las de sus
  proyectos vigentes, las de su equipo: `ProjectReadScopeApiTest` «una asignacion vencida deja de dar alcance pero la
  persona la sigue leyendo».
- Errores (ASM-108): detalle y escrituras de un registro fuera del alcance, `404`; `POST .../related` con un extremo
  ilegible, `404`; un campo `RELATION` hacia un registro ilegible, `400` con `errors[{field, message: "no record with this
  id"}]`; `403 NOT_ASSIGNED` para quien lee sin estar asignado (`PROJECT_ADMIN`, `AUDITOR` no escribe).
- Tableros y reporte sin `project`: los proyectos de `ProjectReach.mine` (todos los que lee quien lee todo; ninguno sin
  asignaciones, con `filters.project` vacío en la respuesta del tablero). Exportaciones: «mis proyectos» igual; con un solo
  proyecto pedido, `403 NOT_ASSIGNED` salvo con `READ_ALL_PROJECTS`.

Lo que este plan deja para las fases siguientes: `ProjectScope.TENANT` y la regla de `ArchitectureTest`; cada objeto nuevo
(F5: `indicator_measurement`, F7: `ai_suggestion` y `ai_interaction`, raíces opcionales) se clasifica en su plan de modelo.

---

## Mapa de archivos

Todos bajo la raíz del repositorio. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `docs/requirements-analysis.md`, `docs/adr/0029-alcance-de-lectura-por-proyecto.md` (crear), `docs/adr/0006-autorizacion-y-alcance-por-proyecto.md`, `docs/adr/0028-qcs-confidenciales-con-recordreadscope.md`, `docs/adr/README.md`, `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md` | IDs nuevos, filas que cambian, ADR-0029, cierre de §7 y corrección de §3.6 de la especificación | 1 |
| `docs/security.md` (salvo la celda `PAD` de §7.2), `docs/api.md`, `docs/dashboards.md`, `docs/gis.md`, `docs/architecture.md`, `docs/domain-model.md`, `docs/ux/screens.md`, `docs/glossary.md`, `docs/requirements-traceability.md`, `docs/roadmap.md` | El diseño por proyecto en cada documento | 2 |
| `model/model.json`, `model/roles.json`, `model/test_apply_roles.py`, `model/test_design_docs.py`, `model/test_apply.py`, `docs/security.md` (celda `PAD` de §7.2), `docs/database-model.md` (ficha de `project`) | `READ_ALL_PROJECTS` y sus dos concesiones | 3 |
| `src/main/kotlin/sgspe/scope/ProjectScope.kt`, `src/test/kotlin/sgspe/ArchitectureTest.kt` | `TENANT`, `ReadGroup`, `readGroup` y la regla de clasificación | 4 |
| `src/main/kotlin/sgspe/common/ProjectReach.kt` (crear), `src/test/kotlin/sgspe/common/ProjectReachTest.kt` (crear) | «Mis proyectos» o todos | 5 |
| `src/main/kotlin/sgspe/scope/ProjectReadCondition.kt` (crear), `src/test/kotlin/sgspe/scope/ProjectReadConditionTest.kt` (crear) | La condición SQL pura | 6 |
| `src/main/kotlin/sgspe/scope/ProjectReadScope.kt` (crear), `src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt` (crear), `src/main/kotlin/sgspe/scope/ProjectScopeGuard.kt`, `src/test/kotlin/sgspe/ArchitectureTest.kt` | El `RecordReadScope` y su vigía | 7 |
| `src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt`, `src/test/kotlin/sgspe/scope/IssueDocumentFilterTest.kt` | Las dos lecturas de documentos y el `404` de la emisión | 8 |
| `src/main/kotlin/sgspe/privacy/ExportProjects.kt`, `ExportLog.kt`, `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, `src/main/kotlin/sgspe/attention/AttentionReport.kt`, `src/main/kotlin/sgspe/scope/Assignments.kt`, `src/test/kotlin/sgspe/privacy/ExportProjectsTest.kt` | `ProjectReach` en tableros, reporte y exportaciones | 9 |
| `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`, `src/test/kotlin/sgspe/common/DependentsDeleteGuardTest.kt` | El detalle de `HAS_DEPENDENTS` | 10 |
| `src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt` (crear) | Las rutas con dos proyectos | 11 |
| `src/test/kotlin/sgspe/security/PermissionMatrixApiTest.kt`, `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt` | Columnas «otro proyecto» y «no asignado» | 12 |
| Doce clases de `src/test/kotlin/sgspe/**` (lista en la Tarea 13) | Migración de la suite | 13 |
| `src/main/kotlin/sgspe/common/SweepTiming.kt` (crear), `src/test/kotlin/sgspe/common/SweepTimingTest.kt` (crear), `DeadlineJob.kt`, `WarningRulesJob.kt`, `StakeholderFollowUpJob.kt`, `DerivedFieldsJob.kt` | Duración por organización de los trabajos | 14 |
| `src/main/resources/application-prod.yml`, `src/test/kotlin/sgspe/ProdProfileTest.kt`, `src/test/kotlin/sgspe/contract/SeparateProvisioningApiTest.kt` (crear), `docs/develop/README.md` | UPS-10 | 15 |
| `model/tenant_steps.py`, `model/provision_tenant.py`, `model/upgrade_tenants.py`, `model/tenants.example.json`, `model/test_provision_tenant.py`, `model/test_upgrade_tenants.py` (crear), `model/fake_core.py`, `docs/develop/README.md` | Aprovisionamiento y subida de fase por tenant | 16 |
| — (coordinador) | Tenant de desarrollo, mediciones y su registro en el roadmap | 17 |
| `CLAUDE.md`, `docs/roadmap.md` (medición), PR | Cierre | 18 |

## Orden y paralelismo

Cada tarea se puede hacer en su propio worktree cuando las tareas de las que depende ya están integradas en
`feat/fase-4-4a-alcance`. Las de un mismo grupo tocan archivos distintos y no usan nada unas de otras. La integración la
corre la CI del PR (o el coordinador, una clase tras otra, porque todas usan `sgspe_sdd_test`).

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| A | 1, 2, 4, 5, 8, 10, 14, 15 | La rama creada | Documentos (dos mitades disjuntas), `ProjectScope`, `ProjectReach`, documentos emitidos, `HAS_DEPENDENTS`, la duración de los trabajos y UPS-10: archivos disjuntos |
| B | 3, 6, 9, 16 | 2 (la 3, por `security.md`); 4 (la 6, por `ReadGroup`); 5 (la 9, por `ProjectReach`); 15 (la 16, por `docs/develop/README.md`) | El modelo, la condición, `ProjectReach` en sus consumidores y los scripts: archivos disjuntos |
| C | 7 | 4, 5 y 6 | `ProjectReadScope` usa las tres; edita `ArchitectureTest` después de la 4 |
| D | 11, 12, 13 | 3, 7, 8, 9 y 10 | Pruebas de integración en archivos disjuntos (una nueva, las dos matrices, la migración) |
| E | 17 | 3 y 7 integradas (coordinador) | Tenant de desarrollo y mediciones por el túnel |
| F | 18 | Todas | `CLAUDE.md`, verificación, suite en la CI, push y PR |

Archivos que comparten tareas y por eso las ordenan: `src/test/kotlin/sgspe/ArchitectureTest.kt` (4 → 7),
`docs/security.md` (2 → 3), `docs/develop/README.md` (15 → 16), `docs/roadmap.md` (2 → 18).

---
# Fase 3.2 · Código propio del backend (Kotlin) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or
> superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Escribir el código propio de SGSPE de la Fase 3 («Compromisos y evidencias») sobre wasichai 0.6.0: las guardas, los
listeners, los comandos y los trabajos de compromisos, acuerdos, evidencias, tareas, plazos y avisos; la idempotencia de los
comandos de `/api/sgspe/**`; el tablero `commitments` con KPI-COM-01 a KPI-COM-11; la exportación `commitment-matrix`; y los
documentos que registran las decisiones D1 a D3 del 2026-10-09.

**Architecture:** Paquetes nuevos `sgspe.commitment`, `sgspe.agreement`, `sgspe.evidence`, `sgspe.investment`
(`SupportPolicyGuard`), `sgspe.deadline` (JOB-01) y `sgspe.notification`; cambios en `sgspe.common` (idempotencia con la tabla
fija `sgspe.idempotency_key`, JOB-08, piezas comunes), `sgspe.scope` (alcance de los objetos de F3), `sgspe.stakeholder`
(tareas de JOB-03), `sgspe.dashboard` y `sgspe.privacy`. Todo escribe por `sgspe.common.Records` con la marca `SgspeWrite`;
las reglas son `RecordWriteGuard` hechas de `WriteRule` que `POST /api/sgspe/transition-checks` corre en seco. Las evidencias
guardan el archivo en el campo `FILE` de wasichai-files (D1); los avisos los entrega wasichai-notifications y SGSPE solo
decide a quién y con qué texto (D2); los comandos propios honran `Idempotency-Key` con el contrato de la cabecera nativa (D3).

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, wasichai 0.6.0 (core, `-gis`, `-workflow`, y los que
agrega el plan 3.1: `-automation`, `-documents`, `-files`, `-notifications`), Flyway por `ModuleMigration` de wasichai, R2DBC
`DatabaseClient`, Apache POI 5.5.1, Jackson 3 (`tools.jackson`), JUnit 5, `wasichai-test` (`WasichaiIntegrationTest`).

**Spec:** `docs/roadmap.md` (fila F3), `docs/api.md` (§2, §3.3, §3.5, §3.10, §4.1, §4.5, §4.9, §4.10, §4.11, §4.13, §5),
`docs/workflows.md` (WF-D, WF-I, WF-K, WF-N, «Transiciones de comando», «Automatizaciones», «Trabajos programados»,
«Listeners», «Avisos», «Destinatarios por rol», «Plazos y escalamiento», «Pruebas»), `docs/domain-model.md` (§6.1, §7.1, §7.4,
§7.5, §8), `docs/database-model.md` (§2.3, §10, §11, §16, §17, §18, §22, §26), `docs/security.md` (§4, §7.9, §8, §11.6, §14),
`docs/dashboards.md` (§3, §4.4, §5.6, §6 a §8, §12), `docs/deliverables.md` (§9, §10, §11), `docs/platform-fit.md` §4.3,
ADR-0014, ADR-0018, ADR-0025, y las decisiones D1 a D4 de `.superpowers/sdd/f3-plan-common.md`. Las lecciones que este plan
diseña desde el principio salen del registro de la Fase 2.2 (`.superpowers/sdd/2026-10-07-fase-2-2-backend/progress.md`,
dictámenes P1 a P29) y de su revisión final (`final-review.md`).

## Global Constraints

- wasichai `0.6.0` exacto (`wasichai-bom`); Kotlin `2.4.20`; Spring Boot `4.1.1`; JDK `25`; Gradle `9.8.0`; ktlint `1.7.1`;
  Apache POI `poi-ooxml:5.5.1`.
- Imagen de pruebas `postgis/postgis:18-3.6`; base externa por el túnel `localhost:5435`, usuario y clave `sgspe`; las
  corridas de SDD usan la base `sgspe_sdd_test`.
- Español (es-PE) en comentarios, mensajes y `detail`; nombres técnicos en inglés (ADR-0009). Constantes de la API en inglés
  UPPER_SNAKE; propiedades en camelCase; atributos con su nombre del modelo en snake_case (`docs/api.md` §3.2).
- Valores ENUM exactamente como los guarda el modelo, en español MAYÚSCULAS sin tildes (`MESA DE DIALOGO`, `ACTA DE
  ENTREGA`, `DATOS PERSONALES`, `POR VENCER`).
- Ningún literal de rol en `src/main/kotlin`; ningún código bajo el paquete `wasichai`; nunca `RecordStore`,
  `PhysicalTableRecordStore`, `AuditService` ni `ObjectSchemaManager`; `SgspeWrite` y `RecordService` solo en `sgspe/common`;
  `scope` y `privacy` solo dependen de `common`; los tenants solo se enumeran con `Organizations` (sobre `TenantDirectory`),
  nunca con `findAllOrganizations` (`ArchitectureTest`).
- Nunca `asPlatform` dentro de una petición: los comandos, guardas y listeners leen y escriben como quien llama; solo los
  trabajos (JOB-01, JOB-02, JOB-03) y la fuente de avisos de plazo (llamada por el bucle de wasichai-notifications) corren
  como la plataforma. Cada escritura de un trabajo lleva el motivo que nombra al trabajo (P16) y la etiqueta `job:<nombre>` en
  la columna `source` de su auditoría (WAS-ADR-0050).
- Todo 400, 403 o 409 de una guarda o de un comando de SGSPE lleva su código en `errors[0].message` y el campo en
  `errors[0].field` (P15). Cada código nuevo queda en `docs/api.md` (Tarea 23).
- `If-Match` con el `updatedAt` entre comillas, obligatorio (428/412) en los comandos `IM`, comparado con la tolerancia de
  `Versions` (derivados y vínculos posteriores no son conflicto, api.md §3.6); un comando que no cambia nada no mueve
  `updatedAt`; un derivado se escribe solo si cambia (P11).
- `Idempotency-Key`: de 1 a 128 caracteres ASCII imprimibles; mismo plazo que la nativa (`wasichai.idempotency.ttl`, 24 h por
  defecto); reenvío con los mismos bytes y `Idempotent-Replayed: true`; otra petición con la misma clave `422`; en curso
  `409` con `Retry-After: 1`.
- Un parámetro de consulta desconocido es `422` (`StrictQuery`); una propiedad o un atributo desconocido del cuerpo de un
  comando es `400` que lo nombra (`StrictBody`).
- Toda lectura de un comando, un tablero o una exportación es acotada: `Records.all(max)` con `400 TOO_MANY_RECORDS`, nunca un
  recorte silencioso; las lecturas por registro (vínculos M2M) tienen su propio tope.
- Todo monto que llega en un cuerpo pasa por `Amounts.parse` (15 dígitos enteros, 4 decimales, no negativo) antes de sumarse.
- Fechas y periodos en America/Lima (`sgspe.common.LIMA`, `Clock`); instantes en UTC con `Z`; las pruebas que dependen del
  tiempo fijan el reloj o usan fechas lejanas.
- Datos de prueba ficticios (`@sgspe.test`, «Persona Ejemplo», «Asociación Ejemplo», códigos `C-…`, `A-…`, `P-…`).
- Commits: Conventional Commits con asunto en español, cuerpo de 100 columnas como máximo (contadas en caracteres), `git add`
  con rutas explícitas y el único trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`. Nunca
  imprimir tokens.
- Antes de cada commit: `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat`.

### Comandos

Unitarias de una clase (sin base):

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase>'
```

Build completo (ktlint y todas las unitarias):

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Integración de una clase. **La corre el coordinador**, no el implementador ni un subagente (el clasificador de permisos los
frena): el implementador escribe, compila (`compileTestKotlin`) y corre `build`, y entrega la tarea marcada «integración
pendiente». Con el túnel abierto:

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'
```

La primera clase de una JVM aprovisiona el modelo de F3 con los scripts de `model/` (de 5 a 8 minutos por el túnel): conviene
correr juntas las clases de una tarea. Cada tarea dice en su último paso qué clases de integración debe correr el coordinador.

## Review Focus

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la tarea
dueña del código:

1. **El portal reintenta un comando tras perder la red, con la misma `Idempotency-Key`.** Un teléfono en campo reenvía la
   entrega o la evaluación; un segundo registro sería una entrega o una evaluación duplicada que nadie puede borrar
   (append-only). Pruebas: `IdempotencyApiTest` «un reenvío con la misma clave devuelve los mismos bytes y no evalúa otra
   vez» y «una petición con la clave en curso recibe 409 con Retry-After» (Tarea 3).
2. **Un campo `FILE` se descarga por la ruta nativa de wasichai-files.** `@wasichai/ui` baja un campo FILE con
   `GET /api/objects/{object}/records/{id}/files/{field}`, que solo mira READ: una evidencia CONFIDENCIAL se abriría sin
   `DOWNLOAD_RESTRICTED`. Prueba: `EvidenceApiTest` «una evidencia restringida no se descarga sin DOWNLOAD_RESTRICTED,
   tampoco por la ruta nativa» (Tarea 6).
3. **Un documento emitido imprime datos restringidos.** wasichai-documents lee el registro sin los permisos de campo de quien
   lo pide y `relatedTable` imprime todos los campos del otro objeto: una «Lista de asistentes» con la tabla de participantes
   mostraría nombres, teléfonos y correos a cualquiera con READ sobre `engagement`. Pruebas: `IssuedDocumentsPrivacyTest`
   «ninguna plantilla de documento emitido imprime un campo restringido» y `IssuedDocumentsApiTest` (Tarea 22).
4. **Dos aprobaciones simultáneas de compromisos de la misma campaña.** Cada una ve los aprobados sin la otra y las dos pasan
   el tope de apoyo social (RN-INV-003). Prueba: `SupportPolicyApiTest` «dos aprobaciones simultáneas de la misma campaña no
   pasan el tope juntas» (Tarea 9).
5. **Un monto con un exponente enorme en una entrega.** `1e999999` en `delivered_amount` agotaría la memoria al sumar el
   ejecutado (la lección de la Tarea 17 de F2). Pruebas: `AmountsTest` (Tarea 2) y `DeliveryApiTest` «un monto negativo o
   enorme, un atributo desconocido o el compromiso en draft no crean nada» (Tarea 12).

---
## Mapa de archivos

Todos bajo la raíz del repositorio `sgspe`. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `docs/adr/0014-almacenamiento-de-objetos.md`, `docs/adr/0018-notificaciones.md` (modificar) | D1 y D2: se aceptan con wasichai-files y wasichai-notifications | 1 |
| `docs/requirements-analysis.md`, `docs/platform-fit.md`, `docs/database-model.md` §2.3, §18, §22, §26, `docs/roadmap.md` (modificar) | ASM-012, ASM-013, ASM-065, UPS-07, UPS-11, UPS-18; ASM-089 y ASM-090; tablas fijas | 1 |
| `src/main/kotlin/sgspe/common/{Records,ModelCatalog,ProjectParameters,StrictBody,ExactlyOneGuard,CommandTransitionGuard,FieldChangeReasonGuard}.kt` (modificar) | `unlink`, `asPlatform` con etiqueta, estados y M2M del catálogo, parámetros de F3, atributos estrictos, grupos y comandos de F3 | 2 |
| `src/main/kotlin/sgspe/common/{Amounts,JobTasks}.kt` | Montos acotados; tareas de trabajos por `generator_key` | 2 |
| `src/main/kotlin/sgspe/scope/ProjectScope.kt` (modificar) | Alcance de los objetos de F3 | 2 |
| `src/test/kotlin/sgspe/F3DomainApiTest.kt`, `src/test/kotlin/sgspe/ArchitectureTest.kt` (modificar) | Base de integración de F3; paquetes de F3 clasificados | 2 |
| `src/main/resources/db/sgspe/common/V1__idempotency_key.sql`, `src/main/kotlin/sgspe/common/Idempotency.kt` | Idempotencia de los comandos (D3) | 3 |
| `src/main/kotlin/sgspe/stakeholder/AssessmentController.kt` (modificar) | Las evaluaciones honran la clave | 3 |
| `src/main/kotlin/sgspe/common/RetentionJob.kt`, `src/main/resources/application.yml` (modificar) | JOB-08 | 4 |
| `src/main/kotlin/sgspe/evidence/{EvidenceRules,EvidenceWriteGuard,Evidences}.kt` | Evidencias: guarda, alta, cambio y vínculos | 5 |
| `src/main/kotlin/sgspe/evidence/EvidenceContent.kt` | Descarga con `DOWNLOAD_RESTRICTED` por las dos rutas | 6 |
| `src/main/kotlin/sgspe/commitment/{Commitments,CommitmentWriteGuard,CommitmentRecordRules}.kt` | La guarda de compromisos y sus reglas de datos | 7 |
| `src/main/kotlin/sgspe/commitment/{SeniorApproval,ApprovalRules}.kt` | D1 a D5 y D8 | 8 |
| `src/main/kotlin/sgspe/investment/{SupportPolicyGuard,CampaignExecution}.kt` | D7 y el ejecutado de la campaña | 9 |
| `src/main/kotlin/sgspe/commitment/CommitmentDerivations.kt` | Fechas de transición y `requires_senior_approval` | 10 |
| `src/main/kotlin/sgspe/commitment/Reschedules.kt` | Reprogramaciones | 11 |
| `src/main/kotlin/sgspe/commitment/Deliveries.kt` | Entregas y su corrección | 12 |
| `src/main/kotlin/sgspe/commitment/FulfillmentRules.kt` | D9, D10 y D11 | 13 |
| `src/main/kotlin/sgspe/commitment/{CandidateRules,Candidates}.kt` | Candidatos humanos (WF-K) | 14 |
| `src/main/kotlin/sgspe/agreement/AgreementWriteGuard.kt` | WF-I | 15 |
| `src/main/kotlin/sgspe/notification/{Notices,ApprovalNotices}.kt`, `build.gradle.kts` (modificar) | Avisos de aprobación pendiente sobre wasichai-notifications | 16 |
| `src/main/kotlin/sgspe/deadline/{Deadlines,DeadlineJob,DeadlineNotices}.kt`, `src/main/resources/application.yml` (modificar) | JOB-01 y los avisos de plazo | 17 |
| `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt` (modificar) | Tareas de JOB-03 | 18 |
| `src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt` | KPI-COM puros | 19 |
| `src/main/kotlin/sgspe/dashboard/{DashboardFilters,DashboardData,Kpis,Dashboards}.kt` (modificar) | Tablero `commitments` y filtro `owner` | 20 |
| `src/main/kotlin/sgspe/privacy/{ExportProjects,MatrixSheets,CommitmentMatrix}.kt`, `{StakeholderRegister,ExportController}.kt` (modificar) | Exportación `commitment-matrix` | 21 |
| `src/test/kotlin/sgspe/privacy/{IssuedDocumentsPrivacyTest,IssuedDocumentsApiTest}.kt` | Documentos emitidos sin datos restringidos | 22 |
| `docs/api.md`, `docs/workflows.md`, `docs/security.md`, `docs/architecture.md`, `docs/deliverables.md`, `docs/domain-model.md`, `docs/dashboards.md`, `docs/requirements-traceability.md`, más los restos de las tablas de avisos en `docs/database-model.md`, `docs/requirements-analysis.md`, `docs/glossary.md` y `docs/platform-fit.md` (modificar) | El contrato de F3 y el diseño como quedó | 23 |
| `src/test/kotlin/sgspe/ArchitectureTest.kt`, `CLAUDE.md` (modificar) | Cierre | 24 |

Las pruebas viven en `src/test/kotlin/sgspe/<paquete>/` con el nombre que cada tarea da.

## Interfaces con los otros planes de F3 y con la rama de wasichai 0.6.0

### Lo que este plan asume de la rama `chore/adopta-wasichai-0.6.0`, que se mezcla antes

- `sgspe.common.Organizations.having(objectName)` sobre `wasichai.core.platform.TenantDirectory.organizationsWithObject`
  (UPS-17), y `ArchitectureTest` que rechaza `findAllOrganizations` en todo el código. Este plan no toca `Organizations`.
- Las escrituras de JOB-02 y JOB-03 ya etiquetadas con la fuente del trabajo (`source = "job:…"`, WAS-ADR-0050, UPS-03). Si
  esa rama ya agregó a `Records` un `asPlatform(organizationId, source, block)`, la Tarea 2 no lo vuelve a agregar y los
  trabajos de este plan usan el suyo con la misma firma; si su firma difiere, se adapta la única línea de cada trabajo que lo
  llama (`DeadlineJob`, `StakeholderFollowUpJob`).

### Lo que este plan asume del plan 3.1 (modelo y aprovisionamiento), que se ejecuta antes

- **`build.gradle.kts`** con `wasichai-spring-boot-starter-automation`, `-documents`, `-files` y `-notifications`, el SDK de S3
  (`software.amazon.awssdk:s3`) y `spring-boot-starter-mail`; y su configuración: `wasichai.files.store` (S3 con MinIO en
  desarrollo), `wasichai.notifications.email.*` con el SMTP de prueba. Este plan agrega solo
  `wasichai.notifications.tick=0` a las dos tareas de prueba (Tarea 16); si 3.1 ya lo fijó, no se duplica.
- **`model/model.json`** con los 12 objetos de la fila F3 de `docs/roadmap.md` y los nombres exactos de `docs/database-model.md`
  §10, §11, §16 y §17, y en particular:
  - `evidence` con el campo **`file`** de tipo `FILE`, obligatorio, en lugar de `storage_key`, `file_name`, `mime_type`,
    `size_bytes` y `sha256` (decisión D1), con `contentTypes` que admite al menos `application/pdf`, `image/jpeg` e
    `image/png`; `evidence_type`, `classification`, `title`, `document_date`, `author`, `captured_at`,
    `people_identifiable`, `photo_consent_given`, `import_batch_id`, `location`; relaciones `project` y `replaces`
    (MANY_TO_ONE) y las M2M `evidence_<objeto>` de §16 que existen en F3 (`evidence_community`, `evidence_land_parcel`,
    `evidence_stakeholder`, `evidence_engagement`, `evidence_field_visit`, `evidence_field_observation`,
    `evidence_commitment`, `evidence_delivery`, `evidence_agreement`, `evidence_study`); `apiOnly`; acciones
    `DOWNLOAD_RESTRICTED` y `EDIT_ANY`.
  - `commitment` con los campos de §10 (los derivados `executed_amount`, `deadline_status`, `requires_senior_approval`,
    `fulfilled_date`, `closed_date`, `cancelled_date` con `"derived": true`), `requiresReason`, acciones `APPROVE`, `VERIFY`,
    `EXPORT`, `APPROVE_OVER_CAP`, la M2M `commitment_stakeholder` y las MANY_TO_ONE `project`, `exploration_campaign`,
    `locality`, `community`, `topic`, `origin_engagement`, `agreement`, `agreement_clause`.
  - `commitment_reschedule` (`appendOnly`, `apiOnly`), `commitment_dependency` (`commitment`, `depends_on`, `blocking`),
    `commitment_action`, `commitment_milestone`, `delivery` (`apiOnly`, con `commitment`, `delivered_amount`,
    `conformity_given` obligatorio, `conformity_by` y `recipients` restringidos), `commitment_candidate` (`apiOnly`, con
    `ai_generated` obligatorio, `confidence`, `created_commitment`, `duplicate_of`, `project`, `engagement`,
    `source_evidence`).
  - `agreement` (`requiresReason`, `agreement_type`, `signed_date`, `effective_date`, `expiry_date`, el derivado
    `validity_status`, `community`, `assembly_engagement`, la M2M `agreement_land_parcel`), `agreement_party`,
    `agreement_clause`.
  - `task` (`title`, `description`, `assignee_user_id`, `assignee_user_email`, `due_date`, `priority`, el derivado
    `deadline_status`, `generator_key` único; MANY_TO_ONE `project`, `commitment`, `engagement`, `agreement`, `stakeholder`).
  - `project` con los parámetros «Desde F3» de §6: `policy_workshop_commitments_blocked`,
    `policy_senior_approval_for_dialogue`, `policy_internal_approval`, `policy_cap_blocks_approval`, `due_soon_days`,
    `escalation_first_days`, `escalation_second_days` (y los demás de la ficha).
  - Los ENUM `resource_type`, `commitment_theme`, `commitment_formality`, `request_medium`, `deadline_status`,
    `validity_status`, `agreement_type`, `evidence_type`, `evidence_classification`, `priority`, con los valores de §10 del
    análisis.
- **`model/workflows/`** `commitment.json` (WF-D), `agreement.json` (WF-I), `commitment_candidate.json` (WF-K) y `task.json`
  (WF-N) con las transiciones y los roles de `docs/workflows.md`; este plan nombra `submit`, `approve`, `approve_senior`,
  `approve_direct`, `start`, `register_fulfillment`, `reopen_execution`, `verify`, `close`, las `cancel_*` (WF-D);
  `activate`, `activate_from_draft`, `resume` (WF-I); `accept`, `reject`, `mark_duplicate` (WF-K); y los estados `open`,
  `in_progress`, `blocked`, `done`, `cancelled` (WF-N).
- **`model/automations/`** con AUT-01, AUT-03, AUT-05 y AUT-10, y **`model/document-types/`** con un archivo por objeto
  (`delivery.json`, `engagement.json`), cada uno una lista JSON de cuerpos de `POST /api/objects/{object}/document-types`
  (`{ name, label, prefix, template }`), con los nombres técnicos `acta-de-entrega`, `minuta-de-reunion` y
  `lista-de-asistentes`. Las plantillas no nombran campos restringidos ni tablas relacionadas de objetos con campos
  restringidos: la Tarea 22 lo prueba (ASM-090).
- **`model/roles.json`** con las concesiones de F3 de `docs/security.md` §7 (incluidos `COMMITMENT_MANAGER` y `APPROVER`),
  las acciones declaradas (`APPROVE`, `VERIFY` y `APPROVE_OVER_CAP` en `commitment`; `DOWNLOAD_RESTRICTED` y `EDIT_ANY` en
  `evidence`), READ sobre `project_assignment` para todo rol que aplica transiciones, y los `restrictedFields` de §7.9 para
  F3 (`commitment.beneficiaries_detail`, `delivery.recipients`, `delivery.conformity_by`,
  `commitment_candidate.evidence_quote`).
- **Pruebas de 3.1** que este plan da por hechas: `F2ApiTest.sample` sabe dar un valor a un campo `FILE` (subiendo un
  archivo) y `EXACTLY_ONE`/`FIXED` tienen las entradas de `delivery`, `agreement_party` y `task`; `GuardTablesCatalogTest`
  ya no lista `commitment_candidate` ni `task` en `notYetModeled`. Los casos de `CommitmentWorkflowApiTest`,
  `AgreementWorkflowApiTest`, `CommitmentCandidateWorkflowApiTest` y `TaskWorkflowApiTest` que dependen de una guarda o un
  comando de este plan los cubren clases propias de este plan:

  | Caso que 3.1 deja a 3.2 | Prueba de este plan (tarea) |
  |---|---|
  | D1 (cada campo de la matriz), D2, sin `execution_time` permitido, D4, D5 (`approve` rechazada, `approve_senior` permitida), D8 | `CommitmentApprovalApiTest` (8) |
  | D7 con la política que bloquea y la que advierte; `approve_direct` con un tope que bloquea | `SupportPolicyApiTest` (9) |
  | `reopen_execution` vacía `fulfilled_date`; cada `cancel_*` escribe `cancelled_date` | `CommitmentDerivationsApiTest` (10), `CommitmentFulfillmentApiTest` (13) |
  | Registrar la entrega no cambia el estado; D9 MATERIAL y NO MATERIAL; D10; D11 | `DeliveryApiTest` (12), `CommitmentFulfillmentApiTest` (13) |
  | `accept` por la API genérica rechazada; el endpoint crea el compromiso; cuenta de servicio rechazada; `mark_duplicate` sin `duplicate_of` | `CandidateApiTest` (14) |
  | Servidumbre sin predios; autorización de asamblea sin ASAMBLEA; vencer no cambia el estado | `AgreementGuardApiTest` (15), `DeadlineJobApiTest` (17) |
  | Las tareas de un trabajo no se duplican | `DeadlineJobApiTest` (17), `FollowUpTasksApiTest` (18) |

  `close` cierra la solicitud que originó el compromiso (`RequestClosureListener`) es de F4: llega con `stakeholder_request`.

Si un nombre de 3.1 difiere de lo de arriba, mandan `docs/database-model.md` y `docs/workflows.md`: se corrige el que se
apartó de ellos. En la copia de trabajo de este plan, la verificación usó un modelo de F3 generado desde
`docs/database-model.md` con `evidence.file` (FILE) en lugar de los cinco campos del archivo.

### Lo que este plan entrega al plan 3.3 (portal)

Los contratos de `docs/api.md` como los deja la Tarea 23. En resumen:

- **Idempotencia:** `Idempotency-Key` en `POST` de `/api/sgspe/commitments/{id}/reschedules`, `…/deliveries`,
  `/api/sgspe/commitment-candidates`, `…/{id}/accept`, `/api/sgspe/evidence` y `/api/sgspe/stakeholders/{id}/assessments`;
  en la API genérica, la cabecera nativa de wasichai en `POST /api/objects/{object}/records`. Un reenvío responde los mismos
  bytes con `Idempotent-Replayed: true`; `422` (`errors[0].message` = `IDEMPOTENCY_KEY_REUSED`, `field` =
  `Idempotency-Key`) si la clave se usó con otra petición; `409` con `Retry-After: 1` (`IDEMPOTENCY_IN_FLIGHT`) si la
  primera sigue en curso: el portal espera y reenvía. Una clave por acción del usuario, repetida en cada reintento.
- **ETag:** los comandos responden `ETag` con el `updatedAt` del registro que el portal sigue editando: el compromiso en la
  reprogramación y en la aceptación de un candidato, la entrega en su alta y en su `PATCH`, la evidencia en su alta y en su
  `PATCH`, el grupo de interés en una evaluación. El portal lo usa como el próximo `If-Match`.
- **Evidencias:** subir es dos pasos: `POST /api/objects/evidence/files/file` (multipart, parte `file`; wasichai-files
  valida tamaño y tipo y responde `201 { id, name, contentType, size, sha256 }`) y `POST /api/sgspe/evidence`
  `{ attributes: { file: <id>, evidence_type, classification, … }, geometries?, links?: [{ object, recordId }] }` →
  `201` registro (con `attributes.file` = el descriptor). Un reintento repite solo el segundo paso, con la misma clave y el
  mismo `file`. Los EXIF los lee el portal (`captured_at`, `geometries.location`). `PATCH /api/sgspe/evidence/{id}`
  `{ attributes }` solo con `title`, `classification`, `document_date`, `author`, `captured_at`, `people_identifiable`,
  `photo_consent_given`. `POST /api/sgspe/evidence/{id}/links` `{ object, recordId }` y
  `DELETE /api/sgspe/evidence/{id}/links/{object}/{recordId}` → `204`. Descarga: `GET /api/sgspe/evidence/{id}/content` o la
  ruta nativa `GET /api/objects/evidence/records/{id}/files/file`, las dos con la misma comprobación.
  Códigos: `400 INVALID_LINK` (`links[i].object`), `400 FIELD_NOT_EDITABLE` (`attributes.<campo>`), `400 REASON_REQUIRED`
  (`reason`), `400 FIELD_REASON_REQUIRED` (`reason`, al cambiar la clasificación), `403 ACTION_REQUIRED` (`classification`,
  bajar o descargar una restringida sin `DOWNLOAD_RESTRICTED`), `403 NOT_OWN_RECORD`, `409 EVIDENCE_IMMUTABLE` (el campo),
  `409 EVIDENCE_NOT_DELETABLE`.
- **Compromisos:** reprogramación `{ attributes: { new_due_date, reschedule_reason, agreed_with_stakeholder } }` →
  `201 { reschedule, commitment }` (`IK`, `IM`, `CR`; `409 STATE_MISMATCH`, `400 SAME_DUE_DATE`, `400 FIELD_REQUIRED`);
  entrega `{ attributes, geometries?, evidence?: [<id de evidence>] }` → `201 { delivery, commitment }` (`IK`, `CR`;
  `409 STATE_MISMATCH`, `400 INVALID_NUMBER`, `400 OUT_OF_RANGE`); `PATCH /api/sgspe/deliveries/{id}` →
  `200 { delivery, commitment }` (`IM`, `CR`; `409 DELIVERY_FROZEN`). Por la API genérica y la consulta en seco:
  `400 JUSTIFICATION_REQUIRED` (`origin_justification`), `409 WORKSHOP_COMMITMENT_BLOCKED` (`origin_engagement`),
  `409 DUE_DATE_RESCHEDULE_ONLY` (`due_date`), `400 DEPENDENCY_SELF`, `409 DEPENDENCY_CYCLE`, `409 DEPENDENCY_GRAPH_TOO_LARGE`
  (`depends_on`), `409 FIELD_REQUIRED` (el primer campo de la matriz que falta), `409 STAKEHOLDER_REQUIRED`
  (`commitment_stakeholder`), `403 ACTION_REQUIRED`, `403 SEGREGATION_OF_DUTIES` (`record` u `owner_user_id`),
  `409 SENIOR_APPROVAL_REQUIRED` (`origin_engagement`), `409 INTERNAL_APPROVAL_REQUIRED` (`project`),
  `409 SUPPORT_CAP_EXCEEDED` (`exploration_campaign`; advertencia con la política apagada), advertencia `CAP_OTHER_CURRENCY`
  (`currency`), `409 DELIVERY_REQUIRED` (`delivery`), `409 EVIDENCE_REQUIRED` (`evidence_delivery`), `403 FIELD_NOT_READABLE`
  (`delivery.conformity_by`), `409 BLOCKING_DEPENDENCY` (`commitment_dependency`), `403 OBJECT_NOT_READABLE` (el objeto),
  `409 DELIVERY_FROZEN` (`commitment`).
- **Candidatos:** alta `{ attributes }` → `201` registro (sin `ai_generated`, `confidence`, `ai_interaction`,
  `created_commitment` ni `duplicate_of`: `400`); aceptación `{ attributes }` (los del compromiso, sin `project`) →
  `201 { commitment, candidate }` (`IK`, `IM`, `CR`; `403 PERSON_REQUIRED`, `403 ROLE_REQUIRED`, `409 STATE_MISMATCH`);
  duplicado `{ duplicateOf }` → `200 { candidate }` (`IM`, `CR`; `400 DUPLICATE_NOT_FOUND`, `400 DUPLICATE_OTHER_PROJECT`).
  `mark_duplicate` por la API genérica: `409 FIELD_REQUIRED` (`duplicate_of`); `accept`: `403 COMMAND_ONLY`.
- **Acuerdos:** al activar, `409 FIELD_REQUIRED` (`signed_date`, `effective_date`, `community`),
  `409 LAND_PARCEL_REQUIRED` (`agreement_land_parcel`), `409 ASSEMBLY_REQUIRED` (`assembly_engagement`).
- **Avisos (decisión D2):** no hay `/api/sgspe/notifications*`. La bandeja es la de wasichai-notifications:
  `GET /api/auth/me/notifications?kind=&state=&page=&size=`, `GET …/summary`, `POST …/{id}/read`, `…/dismiss`, `…/snooze`,
  `POST …/read-all`, `GET …/stream` (SSE) y `GET`/`PUT /api/auth/me/notification-preferences`; `@wasichai/*` 0.5.1 trae sus
  componentes. SGSPE publica con las fuentes `sgspe.approvals` (aprobación pendiente: `ACTION`, clave
  `approval_pending:<objeto>:<id>`, se resuelve al salir del estado pendiente) y `sgspe.deadlines` (por vencer: `WARNING`;
  vencido y escalamientos: `ACTION`, se resuelven al dejar de estar vencido); cada aviso lleva un enlace `RECORD` al registro.
- **Tableros:** `GET /api/sgspe/dashboards/commitments` con KPI-COM-01 a KPI-COM-11 en el orden de `dashboards.md` §5.6; el
  filtro nuevo `owner` (UUID, `owner_user_id`) que aplican KPI-COM-03, 04, 06 y 09; los demás lo devuelven en
  `ignoredFilters`, igual que `explorationCampaign` en los KPI que no lo aplican. Desgloses: KPI-COM-01 `workflow_state`;
  KPI-COM-03 `daysOverdue` (`1-7`, `8-15`, `más de 15` con los umbrales del proyecto); KPI-COM-07 `owner`, `project`,
  `territory`, `topic` (filas `{ key, label, value, overdue }`, 10 y «Otros»); KPI-COM-08 `commitment_theme`; KPI-COM-09
  `against` (`current`, `original` con `value`, `numerator`, `denominator`); KPI-COM-10 `currency` (`committed`,
  `executed`); KPI-COM-11 `series` `{ period, value }` de 12 meses.
- **Exportación** `POST /api/sgspe/exports/commitment-matrix` `{ filters: { project[], explorationCampaign, state[] },
  disclosureLevel, checklistConfirmed }`: INTERNO hojas «Matriz» y «Entregas»; EXTERNO sin responsable ni campos
  restringidos y con los solicitantes por código `GI-`; PUBLICO hojas «Por estado y tema» y «Por tipo de recurso» con la
  marca `reservado`. Errores: los de `stakeholder-register` y `400 INVALID_FILTER` (`filters.state`).
- **Documentos emitidos:** `GET /api/objects/{object}/records/{id}/documents` y `GET /api/documents/{id}` de
  wasichai-documents; la «Lista de asistentes» solo trae conteos (ASM-090).

## Decisiones de este plan

Conflictos entre documentos, o entre un documento y D1 a D3, que este plan resuelve; la Tarea 1 o la 23 los escribe:

1. **La subida de una evidencia es en dos pasos** (D1): el archivo por la ruta de wasichai-files, que ya valida, calcula el
   SHA-256 y aplica el `StoredFileGuard`; el registro por `POST /api/sgspe/evidence` en JSON. Así la clave de idempotencia
   cubre una petición JSON (como la nativa) y un reintento no vuelve a subir el archivo. `api.md` §4.9 decía multipart con
   `attributes` y `links` y lectura de EXIF en el servidor: los EXIF los lee el portal.
2. **La ruta nativa de descarga de `evidence` se reemplaza** por un controlador de SGSPE con el segmento literal `evidence`
   (más específico que `{object}` para Spring), que aplica la misma comprobación que `GET /api/sgspe/evidence/{id}/content`.
   Sin esto, D1 abriría las evidencias restringidas a todo lector.
3. **`Idempotency-Key` sigue el contrato nativo** (D3): `422` (no `409`) para otra petición con la misma clave, `409` con
   `Retry-After` en vez de esperar el candado, 128 caracteres en vez de 64, y la respuesta guardada byte por byte en vez de
   releer los registros. `idempotency_key` guarda la respuesta y no `created_object`/`created_record_ids`.
4. **`IK` sale de la confirmación de importaciones y del alta de componentes.** Son idempotentes por conciliación: un
   reintento de la confirmación encuentra las filas `UNCHANGED` y uno del alta de componentes responde
   `409 SAME_COORDINATES`. Envolverlos en la transacción de la clave rompería P20 (las cuentas de PostGIS fuera de la
   transacción). `api.md` §4.2 deja de marcarlos `IK (desde F3)`.
5. **D6 (políticas de empleo y servidumbres) no se implementa en F3** (ASM-089): el modelo no tiene un dato que diga que un
   compromiso ofrece empleo, y GAP-016 deja fuera de alcance el registro de empleo. La guarda no puede evaluar lo que el
   modelo no guarda; WF-D lo anota.
6. **Los documentos emitidos no imprimen campos restringidos** (ASM-090): wasichai-documents no aplica permisos de campo y
   `relatedTable` imprime todos los campos del otro objeto. La «Lista de asistentes» lleva los conteos de la interacción, no
   la tabla de participantes, y el «Acta de entrega» no lleva receptores ni quiénes dieron la conformidad.
7. **La fecha de vencimiento desde `approved`** la protege `CommitmentWriteGuard` (`DUE_DATE_RESCHEDULE_ONLY`), no
   `DerivedFieldsGuard`, que solo mira derivados (`domain-model.md` §7.4 lo decía al revés).
8. **Los avisos se publican en la transacción de quien escribe** (D2, `Notifications.publish` se une a ella): un aviso mal
   formado se registra y la escritura sigue, pero un fallo de la base al insertar el aviso revierte la transición, como
   cualquier otra escritura de esa transacción. `workflows.md` («Listeners») pedía un savepoint o una conexión aparte.
9. **Los avisos de plazo son una fuente de wasichai-notifications** (`DeadlineNotices`), no escrituras de JOB-01: el módulo
   resuelve solo lo que deja de ser cierto. JOB-01 escribe los derivados y las tareas de escalamiento; la fuente lee lo que
   JOB-01 escribió, así que un aviso llega a lo sumo una hora después.
10. **Un vínculo M2M no llega a los listeners** (wasichai 0.6.0, `RelatedRecordService`): `requires_senior_approval` se
    recalcula al crear o cambiar el origen y, para los solicitantes, en la vuelta diaria de JOB-02. La guarda D5 y los avisos
    evalúan la regla en el momento, así que el derivado atrasado no cambia ninguna decisión.
11. **Las tareas de seguimiento de JOB-03** tienen las claves `stakeholder_review:<id>:<fecha>` y
    `stakeholder_inactivity:<id>:<fecha de referencia>`, que `workflows.md` no fijaba; la tarea de escalamiento de un
    compromiso, `escalation:commitment:<id>:<fecha>:<nivel>`, como dice «Destinatarios por rol».
12. **El filtro `owner` de los tableros** existe porque `dashboards.md` §4.4 da «responsable» como filtro de KPI-COM-03, 04,
    06 y 09, y §3.1 no lo tenía: se agrega a la fila de filtros y a `api.md` §4.11.

---
### Tarea 1: IDs, ADR-0029 y la especificación

**Files:**
- Modify: `docs/requirements-analysis.md` (filas de RQ-SEC-006, CON-041, GAP-025, UPS-01, UPS-06 y UPS-10; ASM-106 a
  ASM-109 tras ASM-105; UPS-28 tras UPS-27; §20.1)
- Create: `docs/adr/0029-alcance-de-lectura-por-proyecto.md`
- Modify: `docs/adr/0006-autorizacion-y-alcance-por-proyecto.md` (Estado y Seguimiento),
  `docs/adr/0028-qcs-confidenciales-con-recordreadscope.md` (Seguimiento), `docs/adr/README.md`
- Modify: `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md` (§3.6 y §7)
- Test: `python3 tools/check_traceability.py`, `cd model && python3 -m unittest`, `yarn format:check`

**Interfaces:**
- Consumes: la especificación y la confirmación del usuario del 2026-10-10 (cabecera de este plan).
- Produces: los IDs ASM-106 (alcance de lectura), ASM-107 (maestros del tenant), ASM-108 (errores), ASM-109 (varios
  clientes), UPS-28 (documentos) y ADR-0029, que citan las tareas siguientes. `docs/` define los IDs; el código y las
  pruebas solo los nombran.

Los IDs se definen aquí porque `docs/requirements-analysis.md` es el único lugar donde nacen (`CLAUDE.md`). La Tarea 2
escribe el resto de los documentos en paralelo y nombra estos IDs; si se integra antes, sus referencias quedan colgando
hasta que esta tarea entra, lo que no rompe ninguna verificación (`check_traceability.py` mira solo RF, RNF, RN y RQ).

- [ ] **Paso 1: Las filas nuevas de `docs/requirements-analysis.md`**

Después de la fila que empieza con `| ASM-105 |`, agregar:

```markdown
| ASM-106 | Alcance de lectura por proyecto (`security.md` §9.7, ADR-0029): una persona lee `project`, las raíces con `project` y sus hijos solo de sus asignaciones vigentes (`active` y, si las tiene, hoy en Lima entre `start_date` y `end_date`); las raíces y los hijos sin proyecto (`evidence`, `early_warning_rule`, `export_log`, `delivery` sin compromiso, `locality_profile` sin estudio) los lee todo quien tiene READ; cada persona ve sus propias asignaciones aunque estén vencidas y las vigentes de sus equipos; la acción declarada `READ_ALL_PROJECTS` sobre `project` exime (en `roles.json`, `AUDITOR` y `PROJECT_ADMIN`, confirmado por el usuario el 2026-10-10); `ANALYST`, `SOCIAL_MANAGER`, `GIS_ANALYST` y `READ_ONLY` leen sus proyectos; compone con ASM-095 por AND; los trabajos leen como la plataforma, sin alcance | Decisión del usuario del 2026-10-10: equipos separados por proyecto dentro de un cliente antes de F5 (la opción A de CON-041 no lo permite) | Quitar `ProjectReadScope`: vuelve la opción A de `security.md` §9.2 | RQ-SEC-005, RQ-SEC-006, CON-041, GAP-025, UPS-01, ADR-0029 |
| ASM-107 | Los maestros del tenant (`ProjectScope.TENANT`: territorio, actores, línea base y `topic`) se comparten entre los proyectos de un cliente: un grupo de interés de un proyecto apunta a una persona u organización que leen los demás, con sus campos restringidos protegidos por los permisos de campo | Decisión del usuario del 2026-10-10 (decisión 3): acotarlos exigiría un vínculo con el proyecto que el modelo no tiene | Agregar al modelo un vínculo de cada maestro con sus proyectos y sumarlo a `ProjectReadCondition` | RQ-SEC-006, RQ-SEC-010, ASM-106 |
| ASM-108 | Semántica de errores del alcance por proyecto: un registro fuera del alcance responde `404` (detalle, cambio, borrado, transición, historial, documentos y `POST .../related` con un extremo ilegible); un campo `RELATION` que nombra un registro ilegible responde el `400` de wasichai sobre ese campo (`"no record with this id"`); `403 NOT_ASSIGNED` queda para quien lee sin estar asignado; `409 HAS_DEPENDENTS` y los únicos del tenant quedan como oráculos aceptados de sí o no, sin datos (el detalle de `HAS_DEPENDENTS` no nombra el dependiente) | wasichai trata como inexistente lo que el alcance oculta (WAS-ADR-0048); contar los dependientes como quien llama dejaría registros huérfanos (`ON DELETE SET NULL`) | Ninguna sin cambiar wasichai: es su comportamiento | RQ-SEC-006, ASM-106, `docs/api.md` §3.3 |
| ASM-109 | Varios clientes en una instalación: `wasichai.organizations.separate-provisioning` encendido en producción; `MANAGE_TENANTS` solo en el rol `ADMIN` de la organización operadora, sembrado fuera de banda antes de encender el interruptor y nunca en `roles.json`; el alta y la subida de fase de cada tenant, con `provision_tenant.py` y `upgrade_tenants.py` sobre un inventario que no se versiona; los trabajos recorren los tenants en serie y se miden | Decisión del usuario del 2026-10-10 (decisión 1): el `ADMIN` de un cliente no crea ni borra tenants (WAS-ADR-0055) | Apagar el interruptor: el `ADMIN` de cada tenant vuelve a crear y borrar tenants | RQ-SEC-002, RQ-SEC-003, UPS-10 |
```

Después de la fila que empieza con `| UPS-27 |`, agregar:

```markdown
| UPS-28 | Candidata: que wasichai-documents aplique `RecordReadScope` en `GET /api/objects/{objeto}/records/{id}/documents`, `GET /api/documents/{id}` y en la emisión, y no devuelva el `snapshot` de un registro fuera del alcance | Sin ella, el documento emitido de un registro de otro proyecto se lee por su ruta | `IssueDocumentFilter` (`sgspe.scope`) lee el registro como quien llama y responde `404` si no lo lee (ADR-0029); sin issue hasta la aprobación del usuario | F4 (plan 4.4) | RQ-SEC-006 |
```

- [ ] **Paso 2: Las filas que cambian**

Reemplazar la fila entera que empieza con `| RQ-SEC-006 |` por:

```markdown
| RQ-SEC-006 | Alcance por proyecto en todas las rutas de lectura (listas, detalle, relacionados, historial, auditoría, transiciones, features GIS, archivos, documentos, agente y endpoints propios de indicadores, exportaciones y análisis espacial) y de escritura, con `project_assignment` como fuente: `ProjectReadScope` y `ProjectScopeGuard` (ASM-106, ADR-0029). Territorio y región siguen sin fuente de datos en el modelo (UPS-01) | [PRM-01 §7] | [PRM] | BC-16 | F2 |
```

Reemplazar la fila entera que empieza con `| CON-041 |` por:

```markdown
| CON-041 | El prompt pide limitar la autorización por organización, proyecto, región y territorio; wasichai no tiene alcance de lectura por registro | [PRM-01 §7] [WAS-ADR-0020] | **Decidido: opción B del lado de SGSPE con `RecordReadScope` (WAS-ADR-0048), ADR-0029.** Decisión del usuario del 2026-10-10: la lectura por proyecto la acota `ProjectReadScope` en todas las rutas de wasichai y `IssueDocumentFilter` en los documentos; `ProjectScopeGuard` sigue en la escritura (ASM-106). Territorio y región, sin fuente de datos (UPS-01) | Resuelta |
```

Reemplazar la fila entera que empieza con `| GAP-025 |` por:

```markdown
| GAP-025 | No se sabe si el alcance de lectura por proyecto es imprescindible en el MVP | ¿Basta en el MVP, con un solo cliente, la lectura a nivel de tenant con escritura acotada por `project_assignment` (opción A), o se espera la extensión UPS-01 (opción B)? | Resuelta por la decisión del usuario del 2026-10-10: alcance de lectura por proyecto antes de F5 (plan 4.4, ASM-106, ADR-0029) | RQ-SEC-006 |
```

Reemplazar la fila entera que empieza con `| UPS-01 |` por:

```markdown
| UPS-01 | Alcance de lectura por proyecto, territorio o región en todas las rutas de lectura (listas, detalle, relacionados, historial, features GIS, agente) y su aplicación en escritura | Sin ella no se cumple la autorización por ámbito del prompt | Resuelta por proyecto del lado de SGSPE con el SPI `RecordReadScope` de wasichai 0.6.0 (WAS-ADR-0048): `ProjectReadScope` (ASM-106, ADR-0029). Queda abierta solo por territorio y región, que no tienen fuente de datos en el modelo; wasichai#48 se comenta con el resultado y no se cierra sin el usuario | Territorio y región: sin fecha | RQ-SEC-006, RQ-GIS-017 |
```

Reemplazar la fila entera que empieza con `| UPS-06 |` por:

```markdown
| UPS-06 | Capacidades a nivel de tenant (y, cuando exista, el alcance) en `/me/permissions` | La interfaz no puede saber qué capacidades globales tiene el usuario | El portal las deduce de los permisos por objeto; si quien llama lee todos los proyectos lo dice `READ_ALL_PROJECTS` en `objects.project` (ASM-106), y qué proyectos concretos ve, leer `project`, que ya viene acotado | F2 | RF-IAM-002, RQ-SEC-007 |
```

Reemplazar la fila entera que empieza con `| UPS-10 |` por:

```markdown
| UPS-10 | Separar la provisión de tenants del permiso de administración de la organización | Quien administra usuarios también podría crear organizaciones | Resuelta con wasichai 0.6.0 (WAS-ADR-0055): `separate-provisioning` encendido en producción y `MANAGE_TENANTS` solo en el `ADMIN` de la organización operadora (ASM-109) | Operación multi-cliente | RQ-SEC-003 |
```

- [ ] **Paso 3: Los conteos de §20.1**

En la tabla «IDs definidos por familia», reemplazar:

```markdown
| `UPS` | 27 |
| `ASM` | 105 |
| Total | 522 |
```

por:

```markdown
| `UPS` | 28 |
| `ASM` | 109 |
| Total | 527 |
```

- [ ] **Paso 4: ADR-0029**

Crear `docs/adr/0029-alcance-de-lectura-por-proyecto.md`:

````markdown
# ADR-0029: Alcance de lectura por proyecto con `RecordReadScope`

## Estado

Aceptado · 2026-10-10 · decisión del usuario que cierra CON-041 y GAP-025 (opción B, del lado de SGSPE) y sustituye el
alcance por proyecto de ADR-0006

## Contexto

- Hasta F4 rige la opción A de CON-041 ([security.md §9.2](../security.md)): quien tiene READ sobre un objeto lo lee en
  todos los proyectos del tenant, también en mapas, tableros, historial, documentos y archivos; solo la escritura está
  acotada por `ProjectScopeGuard`. Es aceptable con un proyecto o con equipos que trabajan en todos; no con muchos proyectos
  por cliente y equipos separados, el caso que el usuario prevé.
- wasichai 0.6.0 trae el SPI `RecordReadScope` [WAS-ADR-0048], que SGSPE ya usa para las QCS confidenciales (ADR-0028):
  la plataforma aplica el criterio de la app en listas, totales, detalle, relacionados, destinos de `RELATION`, historial,
  auditoría, transiciones, features GIS, archivos y agente. No se aplica al `ADMIN`, a la plataforma ni a las
  automatizaciones.
- Casi todas las lecturas de SGSPE pasan por `Records` como quien llama, así que tableros, exportaciones, capas y reportes
  heredan el criterio sin cambios. wasichai-documents no aplica el SPI en sus lecturas (UPS-28).
- Ningún rol se nombra en el código (RQ-SEC-005): quién lee todo el tenant tiene que salir de `model/roles.json`.
- Evaluación de factibilidad: `.superpowers/sdd/fase-4/ups-01-evaluacion.md` (2026-10-10).

## Opciones consideradas

### A, como hasta F4

- A favor: nada nuevo.
- En contra: la lectura no tiene frontera por proyecto; inaceptable con equipos separados (§9.2, riesgo residual).

### B con un `RecordReadScope` propio y SQL puro (elegida)

- A favor: rige en todas las rutas de wasichai; el criterio es una subconsulta sobre `project_assignment`, sin lecturas
  desde Kotlin; reversible quitando el componente.
- En contra: un componente que debe fallar cerrado; cambia la semántica de errores (`404` y `400` en lugar de `403`); la
  suite de integración se migra.

### B con una lista de proyectos leída en Kotlin

- En contra: leer `project_assignment` con `Records` dentro del criterio dispara el mismo alcance sobre
  `project_assignment` (recursión), y fuera de un comando no hay `RequestMemo`: se repetiría por cada objeto de una lectura.

### Acotar también los maestros del tenant

- En contra: el modelo no vincula personas, organizaciones, territorio ni línea base con un proyecto; el usuario decidió
  compartirlos (decisión 3 del 2026-10-10, ASM-107).

## Decisión

- **Componente.** `ProjectReadScope` (`sgspe.scope`) implementa `RecordReadScope`, con la condición pura
  `ProjectReadCondition`. «Mis proyectos» es `SELECT project FROM project_assignment WHERE member_user_id = <quien llama>`
  con la vigencia de `Validity.inForce` (hoy en Lima). `project` se lee si está entre mis proyectos; `project_assignment`,
  las propias (aun vencidas) y las de mis proyectos; las 31 raíces con `project`, por él (las tres opcionales, también sin
  él); los 19 hijos, por el proyecto de su padre (los dos de padre opcional, también sin él); los 20 maestros del tenant
  (`ProjectScope.TENANT`), sin restricción. Valores enlazados, nombres de la definición.
- **Exención sin literales.** La acción declarada `READ_ALL_PROJECTS` sobre `project` exime; en `roles.json`, solo
  `AUDITOR` y `PROJECT_ADMIN` (confirmado por el usuario el 2026-10-10). `ANALYST`, `SOCIAL_MANAGER`, `GIS_ANALYST` y
  `READ_ONLY` leen sus proyectos. `ProjectReach` (`sgspe.common`) es la única pieza que decide «mis proyectos» o todos, para
  el alcance, los tableros, el reporte y las exportaciones.
- **Falla cerrado.** Un objeto sin clasificar, una definición sin sus columnas o una consulta de permisos o de definiciones
  que falla no admiten ningún registro y dejan una advertencia sin datos del registro. `ArchitectureTest` exige que cada
  objeto de `model.json` esté clasificado.
- **Documentos emitidos.** `IssueDocumentFilter` cubre `GET .../records/{id}/documents` y `GET /api/documents/{id}`: si quien
  llama no lee el registro del documento, `404` (hasta UPS-28).
- **Composición.** Con `ConfidentialAttentionScope` (ADR-0028), por AND: `READ_CONFIDENTIAL` no abre otros proyectos y
  `READ_ALL_PROJECTS` no abre las confidenciales.
- **Escritura.** `ProjectScopeGuard` se queda como segunda línea: es la única barrera para quien lee todo sin estar asignado.
- **Errores** (ASM-108). Fuera del alcance, `404`; un `RELATION` hacia un registro ilegible, el `400` de wasichai; un vínculo
  M2M con un extremo ilegible, `404`; `NOT_ASSIGNED`, para quien lee sin estar asignado.

## Consecuencias

### Positivas

- Equipos separados por proyecto dentro de un mismo cliente, en todas las rutas de lectura.
- Quién lee todo se cambia en `roles.json`, sin tocar el código.
- Los trabajos siguen como la plataforma, sin alcance; los avisos ya se dirigen por las asignaciones del proyecto.

### Negativas y riesgos

- Maestros compartidos (ASM-107) y oráculos de sí o no: `409 HAS_DEPENDENTS` y los únicos del tenant (ASM-108).
- Ninguna diferencia entre «no existe» y «no es de mis proyectos», en la API ni en el portal.
- Cifras por quien llama: dos personas con asignaciones distintas ven tableros distintos.
- Cada fase nueva clasifica sus objetos en `ProjectScope`; un objeto mal clasificado como maestro quedaría abierto.
- Orden de despliegue: los roles del tenant antes que el backend, o `AUDITOR` y `PROJECT_ADMIN` pierden lectura un rato.

### Seguimiento

- Pruebas: `ProjectReadConditionTest`, `ProjectReadScopeTest`, `ProjectReachTest`, `IssueDocumentFilterTest` (unitarias);
  `ProjectReadScopeApiTest`, las columnas «otro proyecto» de `PermissionMatrixApiTest` y «no asignado» de
  `WorkflowMatrixApiTest` (integración); `ArchitectureTest`; `test_apply_roles.py`.
- UPS-28 (que wasichai-documents aplique el SPI) retira la parte de documentos de `IssueDocumentFilter`.
- Se revierte quitando el `RecordReadScope`: vuelve la opción A de `security.md` §9.2.

## Referencias

- Requisitos: RQ-SEC-005, RQ-SEC-006; CON-041; GAP-025; UPS-01, UPS-06, UPS-28; ASM-095, ASM-106, ASM-107, ASM-108.
- Documentos: [security.md §9.2, §9.6 y §9.7](../security.md), [api.md §3.3](../api.md),
  [domain-model.md §4.7](../domain-model.md), especificación `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md`.
- ADRs: [ADR-0006](0006-autorizacion-y-alcance-por-proyecto.md), [ADR-0028](0028-qcs-confidenciales-con-recordreadscope.md);
  WAS-ADR-0042, WAS-ADR-0048.
````

- [ ] **Paso 5: ADR-0006, ADR-0028 y el índice**

En `docs/adr/0006-autorizacion-y-alcance-por-proyecto.md`, reemplazar:

```markdown
Aceptado; el alcance por proyecto, Propuesto · 2026-10-07 · el alcance se acepta al iniciar F2, con la opción del checkpoint
```

por:

```markdown
Aceptado · 2026-10-07 · el alcance por proyecto (opción A hasta F4) queda sustituido por ADR-0029 desde el plan 4.4
(2026-10-10)
```

y, en su «Seguimiento», reemplazar:

```markdown
- Checkpoint de F0: elegir A o B (CON-041, GAP-025) y aprobar los issues UPS-01, UPS-02, UPS-06 y UPS-10 (GAP-028).
```

por:

```markdown
- Checkpoint de F0: elegir A o B (CON-041, GAP-025) y aprobar los issues UPS-01, UPS-02, UPS-06 y UPS-10 (GAP-028).
  Resuelto el 2026-10-10: opción B del lado de SGSPE (ADR-0029); UPS-10 resuelta con wasichai 0.6.0 (ASM-109).
```

En `docs/adr/0028-qcs-confidenciales-con-recordreadscope.md`, reemplazar:

```markdown
- Si el checkpoint decide UPS-01 con la opción B de CON-041, este alcance se integra en el del proyecto.
```

por:

```markdown
- Desde el plan 4.4 se compone por AND con el alcance por proyecto de ADR-0029: `READ_CONFIDENTIAL` no abre otros
  proyectos y `READ_ALL_PROJECTS` no abre las confidenciales.
```

En `docs/adr/README.md`, reemplazar en la fila de `[0006]` la celda
`Aceptado; el alcance por proyecto, Propuesto (F2, con la opción del checkpoint)` por
`Aceptado; el alcance por proyecto, sustituido por ADR-0029`, y agregar después de la fila de `[0028]`:

```markdown
| [0029](0029-alcance-de-lectura-por-proyecto.md) | Alcance de lectura por proyecto con `RecordReadScope` | Aceptado | 2026-10-10 | 0042, 0048, 0055 |
```

- [ ] **Paso 6: La especificación: §3.6 corregida y §7 cerrada**

En `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md`, en la tabla de §3.6, reemplazar:

```markdown
| Alta de una raíz con `project` ajeno | `403 NOT_ASSIGNED` sobre `project` | `400` de wasichai sobre `project` (registro inexistente) |
| Vincular por `RELATION` o M2M un registro de un proyecto en que no se está asignado | `409 PROJECT_MISMATCH` | `400` sobre el campo |
```

por:

```markdown
| Alta de una raíz con `project` ajeno | `403 NOT_ASSIGNED` sobre `project` | `400` de wasichai: `errors[{field: "project", message: "no record with this id"}]` (registro inexistente, `RelationTargets`) |
| Vincular por `RELATION` un registro de un proyecto en que no se está asignado | `409 PROJECT_MISMATCH` | `400` de wasichai sobre el campo, con la misma forma |
| Vincular por M2M (`POST .../related`) un registro de un proyecto en que no se está asignado | `409 PROJECT_MISMATCH` | `404` (`RelatedRecordService`: un extremo ilegible no existe) |
```

y reemplazar el párrafo entero que empieza con `**Pregunta abierta para el usuario.**` por:

```markdown
**Pregunta resuelta (2026-10-10).** La decisión 2 nombraba `AUDITOR` y `PROJECT_ADMIN` «por ejemplo». El usuario aprobó
esta especificación con `READ_ALL_PROJECTS` solo para esos dos: `SOCIAL_MANAGER` («región si conduce varios proyectos»,
`security.md` §6.1) queda acotado a sus asignaciones y se asigna a cada proyecto que conduce. Sumarle la acción más adelante
es un cambio de `roles.json` y de `security.md` §7.2, sin código (`test_apply_roles.py` lo fija).
```

- [ ] **Paso 7: Verificar**

```bash
cd ~/IdeaProjects/sgspe
python3 tools/check_traceability.py
(cd model && python3 -m unittest 2>&1 | tail -1)
yarn format:check
grep -c "^| ASM-10[6-9] \|^| UPS-28 " docs/requirements-analysis.md
```

Esperado: `ok: 298 requisitos, todos con su fila`; `OK` del modelo (esta tarea no toca lo que `design_docs.py` lee:
las fichas, §7 de `security.md` ni `workflows.md`); `format:check` en verde (prettier no mira `docs/` ni `*.md`,
`.prettierignore`: el formato de los documentos es a mano, con líneas de 120 columnas como máximo); `5`.

- [ ] **Paso 8: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add docs/requirements-analysis.md docs/adr/0029-alcance-de-lectura-por-proyecto.md \
  docs/adr/0006-autorizacion-y-alcance-por-proyecto.md docs/adr/0028-qcs-confidenciales-con-recordreadscope.md \
  docs/adr/README.md docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md
git commit -F - <<'MSG'
docs: ADR-0029 y los IDs del alcance de lectura por proyecto

Define ASM-106 a ASM-109 y UPS-28, cierra CON-041, GAP-025 y UPS-10, deja UPS-01 abierta solo por
territorio y región, y registra la decisión en ADR-0029. La especificación 4.4 corrige el 404 de un
vínculo M2M y cierra la pregunta de SOCIAL_MANAGER con la decisión del usuario.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 2: El alcance por proyecto en los documentos de diseño

**Files:**
- Modify: `docs/security.md` (§4.1, §5.3, §6.1, §6.2, §7.1, §9.2, §9.5, §9.6, §9.7 nueva, §10.2, §14, §15; no la celda
  `PAD` de §7.2, que es de la Tarea 3)
- Modify: `docs/api.md` (§3.3, §4.11, §4.13), `docs/dashboards.md` (§3.1), `docs/gis.md` (§2.1),
  `docs/architecture.md` (§5.2, §14), `docs/domain-model.md` (§4.7, §8.1.1), `docs/ux/screens.md` (S34), `docs/glossary.md`
  («Plataforma»), `docs/requirements-traceability.md` (RQ-SEC-003, RQ-SEC-006), `docs/roadmap.md` (F4, F5 a F8, brechas,
  riesgo 1)
- Test: `cd model && python3 -m unittest`, `python3 tools/check_traceability.py`, `yarn format:check`

**Interfaces:**
- Consumes: los IDs de la Tarea 1 (ASM-106 a ASM-109, UPS-28, ADR-0029), que se nombran aquí; pueden entrar en cualquier
  orden.
- Produces: `docs/` dice lo que hacen las Tareas 3 a 16; la Tarea 3 agrega la celda de la matriz y la ficha.

`design_docs.py` lee de `security.md` solo §7.1 (la tabla de roles y fases), las tablas `| Objeto | PAD |` y §7.9: esta
tarea no toca ninguna de las tres, así que `test_apply_roles.py` sigue en verde.

- [ ] **Paso 1: `docs/security.md`**

En §4.1, antes de la línea `Dos reglas de operación:`, agregar:

```markdown
Desde el plan 4.4 (ASM-109), el paso 1 lo da la persona del operador que tiene `MANAGE_TENANTS`
(`wasichai.organizations.separate-provisioning` está encendido en producción; el `ADMIN` de un cliente recibe `403` en
`POST /api/organizations` y `DELETE /api/organizations/current`), y los pasos 1 a 5 los corre `model/provision_tenant.py`;
una subida de fase los repite en cada tenant con `model/upgrade_tenants.py` sobre un inventario que no se versiona. La
siembra de `MANAGE_TENANTS` y el uso de los scripts están en `docs/develop/README.md` §5.
```

En §5.3, en la primera tabla, después de la fila que empieza con `` | `READ_CONFIDENTIAL` | ``, agregar:

```markdown
| `READ_ALL_PROJECTS` | `project` | Leer todos los proyectos del tenant sin asignación: `ProjectReadScope` no acota a quien la tiene, y sus tableros, reporte y exportaciones toman todos los proyectos por defecto. No da escritura: `ProjectScopeGuard` sigue exigiendo la asignación | `ProjectReadScope` y `ProjectReach` (§9.7) | `AUDITOR`, `PROJECT_ADMIN` (confirmado por el usuario el 2026-10-10); el `*` READ de `AUDITOR` no concede acciones declaradas | RQ-SEC-005, RQ-SEC-006, ASM-106 |
```

y, en la segunda tabla («Las mismas acciones, por objeto»), reemplazar:

```markdown
| `project`, `area_of_influence`, `social_program` | `APPROVE` |
```

por:

```markdown
| `project` | `APPROVE`, `READ_ALL_PROJECTS` |
| `area_of_influence`, `social_program` | `APPROVE` |
```

En §6.1, reemplazar:

```markdown
la fase más tardía entre la suya y la del objeto (§7.1). En "Ámbito que necesita", la organización es nativa; el
proyecto, el territorio y la región dependen de UPS-01 (§9).
```

por:

```markdown
la fase más tardía entre la suya y la del objeto (§7.1). En "Ámbito que necesita", la organización es nativa y el
proyecto lo pone `ProjectReadScope` desde el plan 4.4 (§9.7): leen la organización entera solo `ADMIN` y quien tiene
`READ_ALL_PROJECTS` (`AUDITOR` y `PROJECT_ADMIN`); `SOCIAL_MANAGER` se asigna a cada proyecto que conduce y `ANALYST` lee
sus proyectos. El territorio y la región siguen sin fuente de datos (UPS-01).
```

En §6.2, reemplazar:

```markdown
- Separar la provisión de tenants de la administración de la organización es UPS-10, necesaria solo para operar varios
  clientes en una misma instalación.
```

por:

```markdown
- Separar la provisión de tenants de la administración de la organización era UPS-10, resuelta con wasichai 0.6.0
  (WAS-ADR-0055, ASM-109): en producción, crear y borrar tenants exige `MANAGE_TENANTS`, que solo tiene el `ADMIN` de la
  organización operadora, sembrado fuera de banda; el `ADMIN` de un cliente conserva usuarios, roles, cuentas de servicio
  y el nombre de su tenant.
```

En §7.1, reemplazar:

```markdown
- `AUDITOR` no tiene columna: lee todo el tenant (`*` READ), incluida `/api/audit`, no escribe nada y no ve los campos
  restringidos (§7.9) mientras no se responda GAP-024.
```

por:

```markdown
- `AUDITOR` no tiene columna: lee todo el tenant (`*` READ y `READ_ALL_PROJECTS` sobre `project`, §9.7), incluida
  `/api/audit`, no escribe nada y no ve los campos restringidos (§7.9) mientras no se responda GAP-024.
```

y reemplazar:

```markdown
  necesita; el resto de los roles operativos lee todo lo demás, porque la lectura es de todo el tenant mientras no exista
  UPS-01 (§9).
```

por:

```markdown
  necesita; el resto de los roles operativos lee todo lo demás de sus proyectos (§9.7) y los maestros del tenant.
```

En §9.2, reemplazar el título `### 9.2 Opción A: lectura de todo el tenant y escritura acotada por asignación` por
`### 9.2 Opción A: lectura de todo el tenant y escritura acotada por asignación (hasta F4)` y agregar, como primer
párrafo de la sección:

```markdown
Rigió hasta F4. Desde el plan 4.4, la lectura la acota `ProjectReadScope` (§9.7); la escritura de esta sección sigue
igual, como segunda línea.
```

En §9.5, reemplazar el título `### 9.5 Recomendación` por `### 9.5 Decisión` y reemplazar los dos párrafos que siguen a la
tabla (los que empiezan con `- **Decisión pendiente del checkpoint, con recomendación A**` y `- A no hace depender F2`) por:

```markdown
- **Decidido el 2026-10-10 (CON-041, GAP-025, ADR-0029):** la opción B del lado de SGSPE, con el SPI `RecordReadScope` de
  wasichai 0.6.0 en lugar de una extensión upstream nueva. A rigió hasta F4; `ProjectScopeGuard` se queda.
```

En §9.6, reemplazar:

```markdown
usuario del 2026-10-09); el alcance por proyecto sigue en la opción A de CON-041 (§9.2) hasta que el checkpoint decida
UPS-01, que este mismo SPI resolvería del lado de SGSPE. La decisión, sus alternativas y sus consecuencias están en
```

por:

```markdown
usuario del 2026-10-09). Desde el plan 4.4 el alcance por proyecto usa el mismo SPI (§9.7): los dos criterios se componen
por AND, así que `READ_CONFIDENTIAL` no abre otros proyectos y `READ_ALL_PROJECTS` no abre las confidenciales; la regla del
sistema `ConfidentialRelease` no se extiende, porque la solicitud y el compromiso son siempre del mismo proyecto
(`ProjectLinkGuard`). La decisión, sus alternativas y sus consecuencias están en
```

Antes de la línea `## 10. GeoServer`, agregar la sección nueva:

```markdown
### 9.7 Alcance de lectura por proyecto: `ProjectReadScope` (plan 4.4)

El segundo `RecordReadScope` de SGSPE (ASM-106, [ADR-0029](adr/0029-alcance-de-lectura-por-proyecto.md)). wasichai lo pide
para toda persona y cuenta de servicio que no es `ADMIN`, nunca para la plataforma ni para las automatizaciones, y lo
aplica en listas, totales, búsqueda, detalle, cambios, bajas, transiciones, relacionados (y `POST/DELETE .../related`),
destinos de `RELATION`, historial, `/api/audit`, features GIS, archivos y agente.

| Objeto | Lo lee quien llama si… |
|---|---|
| `project` | Está entre «mis proyectos»: los de sus asignaciones vigentes (`active` y, si las tiene, hoy en Lima entre `start_date` y `end_date`) |
| `project_assignment` | Es suya (aunque esté vencida) o es de uno de mis proyectos |
| Las 31 raíces de `ProjectScope.ROOTS` | Su `project` es de mis proyectos; `evidence`, `early_warning_rule` y `export_log` sin `project`, siempre |
| Los 19 hijos de `ProjectScope.CHILDREN` | Su padre es de mis proyectos; `delivery` sin compromiso y `locality_profile` sin estudio, siempre |
| Los 20 maestros de `ProjectScope.TENANT` (territorio, actores, línea base, `topic`) | Siempre (ASM-107) |

- **Exentos:** `ADMIN` (wasichai) y quien tiene la acción declarada `READ_ALL_PROJECTS` sobre `project` (`AUDITOR` y
  `PROJECT_ADMIN`). `ProjectReach` (`sgspe.common`) lo decide también para los tableros, el reporte y las exportaciones.
- **SQL puro:** «mis proyectos» es una subconsulta sobre la tabla de `project_assignment`, con valores enlazados; leerla con
  `Records` dispararía el mismo alcance sobre sí misma. Los nombres de tabla y columna salen de la definición.
- **Falla cerrado:** un objeto sin clasificar en `ProjectScope`, una definición sin sus columnas o un error al consultar
  permisos o definiciones no admiten ningún registro. `ArchitectureTest` exige que todo objeto de `model.json` esté en un
  solo grupo.
- **Documentos emitidos:** wasichai-documents solo pide READ sobre el objeto (UPS-28); `IssueDocumentFilter` lee el registro
  del documento como quien llama en `GET .../records/{id}/documents` y `GET /api/documents/{id}` y responde `404` si no lo
  lee.
- **Escritura:** `ProjectScopeGuard` (§9.2) sigue como segunda línea: frena a quien lee todo sin estar asignado.
- **Errores (ASM-108):** fuera del alcance, `404`; un `RELATION` hacia un registro ilegible, el `400` de wasichai sobre el
  campo (`"no record with this id"`); un vínculo M2M con un extremo ilegible, `404`; `NOT_ASSIGNED`, para quien lee sin estar
  asignado; `409 HAS_DEPENDENTS` y los únicos del tenant son oráculos aceptados de sí o no.
- **Riesgos aceptados:** maestros compartidos (ASM-107); el texto de los avisos viejos en la bandeja de quien deja un
  proyecto; cifras que dependen de quién llama. GeoServer no publica objetos de proyecto (§10.2).
```

En §10.2, después del último punto numerado de la lista de reglas, agregar un punto con el número siguiente:

```markdown
1. **Ningún objeto de proyecto.** GeoServer lee la base con sus credenciales, sin permisos ni alcance (WAS-ADR-0048): solo
   publica maestros de `ProjectScope.TENANT` sin campos restringidos; ningún `project`, raíz ni hijo de `ProjectScope`.
   `publish_layers.py` (F6) lo comprueba contra la misma clasificación y `test_model.py` lo prueba (ASM-106).
```

(con el número que sigue al último punto de la lista; prettier no formatea `docs/`).

En §14, reemplazar las dos filas que empiezan con `| Alcance, opción A |` y `| Alcance, opción B |` por:

```markdown
| Alcance, escritura | Un usuario sin asignación no escribe en el proyecto y uno asignado sí; `ADMIN` queda exento; quien lee todo sin estar asignado recibe `403 NOT_ASSIGNED` (`ProjectScopeApiTest`) | Integración | F2, 4.4 | RQ-SEC-006 |
| Alcance, lectura | Dos proyectos: quien está asignado a uno no lee el otro en listas, totales, detalle, relacionados, historial, auditoría, transiciones, features, archivos, documentos, indicadores, exportaciones y capas; asignación vencida; exenciones; cuenta de servicio; composición con las QCS confidenciales (`ProjectReadScopeApiTest`); condición y componente sin base (`ProjectReadConditionTest`, `ProjectReadScopeTest`, `ProjectReachTest`); clasificación de los objetos (`ArchitectureTest`); columnas «otro proyecto» y «no asignado» de las matrices | Unitaria e integración | 4.4 | RQ-SEC-006, RQ-GIS-017 |
| Alta de tenants separada | Con `separate-provisioning`, el `ADMIN` de un cliente recibe `403` al crear o borrar tenants y el de la operadora, con `MANAGE_TENANTS`, crea uno (`SeparateProvisioningApiTest`, `ProdProfileTest`) | Integración y unitaria | 4.4 | RQ-SEC-003 |
```

En §15, reemplazar la fila que empieza con `| Alcance por proyecto | CON-041, GAP-025, UPS-01 |` por:

```markdown
| Alcance por proyecto | CON-041, GAP-025, UPS-01 | Decidido el 2026-10-10: opción B del lado de SGSPE (§9.7, ADR-0029) | `READ_ALL_PROJECTS` solo para `AUDITOR` y `PROJECT_ADMIN`; territorio y región, sin fuente (UPS-01) |
```

- [ ] **Paso 2: `docs/api.md`**

En §3.3, en la fila `| \`403\` |`, reemplazar `` `NOT_ASSIGNED` (sin asignación vigente al proyecto) `` por
`` `NOT_ASSIGNED` (sin asignación vigente al proyecto, para quien lo lee: desde el plan 4.4, quien no lo lee recibe el `404`
o el `400` de wasichai, ASM-108) ``. Después de la tabla de códigos (antes de `Los comandos de F4 suman sus propios
códigos`), agregar:

```markdown
Desde el plan 4.4, el alcance de lectura por proyecto (`security.md` §9.7, ASM-108) cambia lo que ve quien no está
asignado a un proyecto, y no tiene `READ_ALL_PROJECTS`:

| Caso | Respuesta |
|---|---|
| Leer, cambiar, borrar o transicionar un registro de otro proyecto, o su historial o sus documentos | `404`, como un registro que no existe |
| Alta o cambio con un campo `RELATION` hacia un registro de otro proyecto (también `project`) | `400` de wasichai: `errors[{field: "<campo>", message: "no record with this id"}]` |
| `POST` o `DELETE .../related` con un extremo de otro proyecto | `404` |
| Vincular dos registros de proyectos distintos que quien llama lee | `409 PROJECT_MISMATCH`, como hasta F4 |
| Escribir sin asignación quien lee todos los proyectos (`PROJECT_ADMIN`) | `403 NOT_ASSIGNED` |
| Tablero, indicador o reporte con `project` de otro proyecto | `200` con conjuntos vacíos |
| Borrar un registro con dependientes en otro proyecto | `409 HAS_DEPENDENTS`; el `detail` no nombra el dependiente, el `field` sigue siendo `<objeto>.<campo>` |
```

En el párrafo de la emisión manual, reemplazar:

```markdown
sin `EDIT_ANY`, el registro es de otra persona). Un registro que no existe responde `403` `OBJECT_NOT_READABLE` a quien
no es `ADMIN`, igual que uno que no puede leer, para no revelar si existe; `ADMIN` recibe el `404` de wasichai.
```

por:

```markdown
sin `EDIT_ANY`, el registro es de otra persona). Un registro que no existe o que quien emite no lee (otro proyecto, una QCS
confidencial ajena) responde el `404` de wasichai; `OBJECT_NOT_READABLE` queda para quien no tiene READ sobre el objeto.
Las dos lecturas, `GET /api/objects/{object}/records/{id}/documents` y `GET /api/documents/{id}`, responden `404` a quien
no lee el registro del documento (UPS-28).
```

En §4.11, después de la tabla de rutas de la sección, agregar:

```markdown
Sin `project`, los proyectos por defecto son «mis proyectos» de `ProjectReach`: todos los que lee quien lee todos los
proyectos (`ADMIN` y `READ_ALL_PROJECTS`) y, si no, sus asignaciones vigentes; sin asignaciones, ninguno (la respuesta trae
`filters.project` vacío y los indicadores, conjuntos vacíos). Un proyecto pedido que quien mira no lee da conjuntos vacíos,
sin error.
```

En §4.13, reemplazar:

```markdown
- **Proyectos.** Una lista vacía (o sin `project`) es «mis proyectos»: las asignaciones vigentes de quien exporta, hoy en
  America/Lima; el `ADMIN`, todos. Con **un solo** proyecto (los repetidos cuentan una vez), `export_log.project` lleva ese
  proyecto y quien no es `ADMIN` necesita una asignación vigente a él: si no, `403` `NOT_ASSIGNED`. Con dos o más proyectos
  explícitos no se exige asignación y `export_log.project` queda vacío: con la opción A (`security.md` §9.2) la lectura es de
  todo el tenant y la asignación no es una frontera de confidencialidad, solo la exige la escritura de `export_log` acotada
  por proyecto.
```

por:

```markdown
- **Proyectos.** Una lista vacía (o sin `project`) es «mis proyectos» de `ProjectReach`: las asignaciones vigentes de quien
  exporta, hoy en America/Lima; quien lee todos los proyectos (`ADMIN` y `READ_ALL_PROJECTS`), todos. Con **un solo**
  proyecto (los repetidos cuentan una vez), quien no lo lee todo necesita una asignación vigente a él: si no, `403`
  `NOT_ASSIGNED`; `export_log.project` lleva ese proyecto si quien exporta es `ADMIN` o está asignado, y si no queda vacío y
  el proyecto va en `filters_description`. Con dos o más proyectos explícitos no se exige asignación y `export_log.project`
  queda vacío; las filas de los proyectos que quien exporta no lee no salen (`security.md` §9.7).
```

- [ ] **Paso 3: `docs/dashboards.md`, `docs/gis.md`, `docs/architecture.md` y `docs/domain-model.md`**

En `docs/dashboards.md` §3.1, en la fila `| Proyecto |`, reemplazar el texto que va desde `Por defecto, los proyectos con`
hasta `pasa a ver todos los que puede leer` por:

```markdown
Por defecto, «mis proyectos» de `ProjectReach`: todos los que lee quien lee todos los proyectos (`ADMIN` y
`READ_ALL_PROJECTS`) y, si no, los de sus asignaciones vigentes (`active` y, si tiene fechas, hoy en Lima entre ellas); sin
asignaciones vigentes, ninguno. Un proyecto pedido que quien mira no lee da conjuntos vacíos, sin error (`security.md` §9.7)
```

En `docs/gis.md`, al final de §2.1 (antes de `### 2.2 Capas \`LYR-NN\``), agregar:

```markdown
Desde el plan 4.4, las capas que sirve la API (LYR-01 a LYR-16 y LYR-20) heredan el alcance de lectura por proyecto
(`security.md` §9.7): se leen como quien mira, así que un objeto de un proyecto que no lee no sale. GeoServer no lo
aplica: ningún objeto de `ProjectScope` (`project`, sus raíces ni sus hijos) se publica, solo maestros de
`ProjectScope.TENANT` sin campos restringidos (`security.md` §10.2).
```

En `docs/architecture.md` §5.2, en la fila de `` `sgspe.scope` ``, reemplazar
`` y `ConfidentialAttentionScope` (`RecordReadScope` de las QCS confidenciales, ASM-095); adaptador de la extensión de alcance cuando exista ``
por
`` y `ConfidentialAttentionScope` (`RecordReadScope` de las QCS confidenciales, ASM-095); desde 4.4, `ProjectReadScope` y `ProjectReadCondition` (`RecordReadScope` del alcance por proyecto, ASM-106) y `ProjectScope.TENANT` ``;
y en la fila de `` `sgspe.common` `` de la misma tabla, agregar al final de su segunda celda
`` ; desde 4.4, `ProjectReach` («mis proyectos» o todos, ASM-106) y `SweepTiming` (duración por organización de los trabajos) ``.
En §14, reemplazar la fila que empieza con `| 1 | La extensión de alcance por proyecto (UPS-01)` por:

```markdown
| 1 | El alcance por proyecto se desalinea del modelo en F5 a F8 | Un objeto nuevo sin clasificar no se lee; uno mal clasificado como maestro queda abierto | Resuelto por proyecto en el plan 4.4 (`ProjectReadScope`, ADR-0029); `ArchitectureTest` exige que cada objeto de `model.json` esté en `ProjectScope` y la revisión de cada plan de modelo mira la clasificación; territorio y región siguen sin fuente (UPS-01) | CON-041, GAP-025, RQ-SEC-006, UPS-01, ASM-106 |
```

En `docs/domain-model.md` §4.7, reemplazar:

```markdown
- La escritura en objetos de proyecto la acota una guarda por asignación (`ProjectScopeGuard`, `sgspe.scope`); la lectura por
  proyecto requiere la extensión upstream UPS-01 (RQ-SEC-006, CON-041, GAP-025).
```

por:

```markdown
- La escritura en objetos de proyecto la acota una guarda por asignación (`ProjectScopeGuard`, `sgspe.scope`) y, desde el
  plan 4.4, la lectura `ProjectReadScope` (ASM-106, `security.md` §9.7). `ProjectScope` clasifica cada objeto en un solo
  grupo: `project`; `project_assignment`; las 31 raíces con `project` (`evidence`, `early_warning_rule` y `export_log` con
  `project` opcional); los 19 hijos, por su padre (`delivery` y `locality_profile` con padre opcional); y los 20 maestros del
  tenant (`TENANT`), compartidos entre los proyectos (ASM-107). Un objeto nuevo se clasifica en su fase.
```

y, en §8.1.1, reemplazar la fila `` | `project` | `APPROVE` | Llevar el proyecto a `active` (WF-A) | `` por:

```markdown
| `project` | `APPROVE`, `READ_ALL_PROJECTS` | Llevar el proyecto a `active` (WF-A); leer todos los proyectos sin asignación (ASM-106) |
```

- [ ] **Paso 4: `docs/ux/screens.md`, `docs/glossary.md`, `docs/requirements-traceability.md` y `docs/roadmap.md`**

En `docs/ux/screens.md`, después del punto que empieza con `- **Registro reservado (ASM-095).**`, agregar:

```markdown
- **Registro reservado general y proyectos (ASM-106, plan 4.4b).** Todo vínculo cuyo destino responde `404` (otro
  proyecto o una QCS confidencial) muestra «Registro reservado»; una ficha que responde `404` dice «El registro no existe o
  no es de sus proyectos», sin distinguir. El selector de proyectos lista los `project` que la cuenta lee y, con
  `READ_ALL_PROJECTS` en `/api/auth/me/permissions`, ofrece «Todos los proyectos»; sin asignaciones ni la acción, dice «No
  tiene proyectos asignados. Pida una asignación al administrador de proyectos».
```

En `docs/glossary.md`, al final de la tabla de la sección `## Plataforma`, agregar:

```markdown
| Alcance de lectura por proyecto | `ProjectReadScope` | project-scoped read | Regla por la que una persona lee los registros de los proyectos a los que está asignada (y los maestros del tenant); quien tiene `READ_ALL_PROJECTS` lee todos (ASM-106) | [PRM-01 §7] | alcance por proyecto | permiso por objeto; registros propios |
| Maestro del tenant | `ProjectScope.TENANT` | tenant master data | Objeto que no pertenece a un proyecto y comparten todos los proyectos de un cliente: territorio, actores, línea base y temas (ASM-107) | ADR-0029 | maestro | raíz de proyecto |
```

En `docs/requirements-traceability.md`, en la fila de RQ-SEC-006, reemplazar la cuarta celda
`` BC-16 · `sgspe.scope` (`ProjectScopeGuard`) `` por `` BC-16 · `sgspe.scope` (`ProjectScopeGuard`, `ProjectReadScope`, `IssueDocumentFilter`); `sgspe.common` (`ProjectReach`) ``,
la celda de pruebas por `` `ProjectReadScopeApiTest`; `ProjectReadConditionTest`; `ProjectReadScopeTest`; `ProjectScopeApiTest`; `PermissionMatrixApiTest` («otro proyecto») ``
y la última celda por `Diseñado (ADR-0029); territorio y región, Upstream (UPS-01)`. En la fila de RQ-SEC-003, reemplazar
`Implementado; Upstream (UPS-10)` por `Implementado; UPS-10 resuelta (\`SeparateProvisioningApiTest\`)`.

En `docs/roadmap.md`:

- en la tabla de F4, reemplazar el final de la celda de «Estado»
  `` 4.2b (riesgos y alertas) y 4.3 (portal, con sgspe#14) `` por
  `` 4.2b (riesgos y alertas), 4.3 (portal, con sgspe#14) y 4.4 (alcance de lectura por proyecto y varios clientes: 4.4a backend en `docs/superpowers/plans/2026-10-10-fase-4-4a-alcance.md`, 4.4b portal; especificación `docs/superpowers/specs/2026-10-10-alcance-por-proyecto-design.md`) ``;
- en la misma tabla, agregar al final de la celda de «Definición de terminado» (antes del `|` final):
  `` ; plan 4.4: `ProjectReadScopeApiTest`, `SeparateProvisioningApiTest` y las columnas «otro proyecto» y «no asignado» de las matrices en verde; el cierre de la Release 1 exige 4.4a y 4.4b integrados, el tenant de desarrollo con `READ_ALL_PROJECTS`, la medición de la especificación 4.4 §3.10 anotada aquí y la siembra de `MANAGE_TENANTS` documentada ``;
- en las filas `| Definición de terminado |` de F5, F6, F7 y F8, agregar al final de la celda (antes del `|` final):
  `` ; objetos nuevos clasificados en `ProjectScope` (`ArchitectureTest`) ``;
- en «Brechas upstream por fase», reemplazar `| F4 | UPS-27 |` por `| F4 | UPS-27, UPS-28 |`, y
  `| Operación multicliente | UPS-10 |` por `| Operación multicliente | UPS-10 (resuelta con wasichai 0.6.0, ASM-109) |`;
- en «Registro de riesgos», reemplazar la fila `| 1 | La extensión de alcance por proyecto no llega …` por:

```markdown
| 1 | La extensión de alcance por proyecto no llega antes de la producción con varios proyectos | Un usuario lee proyectos que no le corresponden | Mitigado en el plan 4.4: `ProjectReadScope` sobre el SPI `RecordReadScope` de wasichai 0.6.0 (ADR-0029); territorio y región siguen sin fuente | CON-041, GAP-025, UPS-01, ASM-106 |
```

- [ ] **Paso 5: Verificar**

```bash
cd ~/IdeaProjects/sgspe
(cd model && python3 -m unittest 2>&1 | tail -1)
python3 tools/check_traceability.py
yarn format:check
grep -n "mientras no exista\|hasta que el checkpoint decida" docs/security.md || echo limpio
```

Esperado: `OK` del modelo; `ok: 298 requisitos, todos con su fila`; `format:check` en verde (prettier no mira `docs/`:
el formato es a mano, con líneas de 120 columnas como máximo y las tablas sin alinear, como el resto); `limpio`.

- [ ] **Paso 6: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add docs/security.md docs/api.md docs/dashboards.md docs/gis.md docs/architecture.md docs/domain-model.md \
  docs/ux/screens.md docs/glossary.md docs/requirements-traceability.md docs/roadmap.md
git commit -F - <<'MSG'
docs: el alcance de lectura por proyecto en el diseño

security.md suma §9.7 (ProjectReadScope), READ_ALL_PROJECTS en §5.3, UPS-10 resuelta y la regla de
GeoServer; api.md fija la semántica de 404 y 400 (ASM-108) y los proyectos por defecto; tableros,
SIG, arquitectura, modelo de dominio, pantallas, glosario, trazabilidad y roadmap lo dicen.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 3: `READ_ALL_PROJECTS` en el modelo y en los roles

**Files:**
- Modify: `model/model.json` (acciones de `project`), `model/roles.json` (`PROJECT_ADMIN` y `AUDITOR`)
- Modify: `docs/security.md` (celda `PAD` de `project` en §7.2), `docs/database-model.md` (ficha de `project`, «Acciones
  declaradas»)
- Test: `model/test_apply_roles.py`, `model/test_design_docs.py`, `model/test_apply.py`

**Interfaces:**
- Consumes: `docs/security.md` como lo deja la Tarea 2 (archivo compartido).
- Produces: la acción declarada `READ_ALL_PROJECTS` sobre `project`, concedida a `AUDITOR` y `PROJECT_ADMIN`, que leen
  `ProjectReach.readsAll` (Tarea 5) por `Permissions.has` y `/api/auth/me/permissions` (Tarea 11). `apply.py` declara
  una acción más: un tenant nuevo cuenta 333 elementos creados en lugar de 332.

`test_apply_roles.py` compara `roles.json` con la matriz de `security.md` §7.2 a §7.8 (salvo `AUDITOR`, que no tiene
columna) y `test_model.py` compara las acciones de `model.json` con la línea «Acciones declaradas» de cada ficha de
`database-model.md`: por eso las dos celdas de los documentos van en el mismo commit que el modelo.

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `model/test_apply_roles.py`, reemplazar:

```python
    def test_solo_auditor_tiene_un_permiso_de_todo_el_tenant_y_es_de_lectura(self):
        tenant = {name for name, role in self.roles.items() if "*" in role["permisos"]}
        self.assertEqual(tenant, {"AUDITOR"})
        self.assertEqual(self.roles["AUDITOR"]["permisos"], {"*": ["READ"]})
```

por:

```python
    def test_solo_auditor_tiene_un_permiso_de_todo_el_tenant_y_es_de_lectura(self):
        tenant = {name for name, role in self.roles.items() if "*" in role["permisos"]}
        self.assertEqual(tenant, {"AUDITOR"})
        # "*" READ no concede acciones declaradas: la de leer todos los proyectos va aparte (ASM-106)
        self.assertEqual(self.roles["AUDITOR"]["permisos"], {"*": ["READ"], "project": ["READ_ALL_PROJECTS"]})

    def test_leen_todos_los_proyectos_solo_auditor_y_project_admin(self):
        # decisión 2 de la especificación 4.4 (ASM-106), confirmada por el usuario el 2026-10-10: SOCIAL_MANAGER, ANALYST,
        # GIS_ANALYST y READ_ONLY leen solo sus proyectos. sumar un rol es agregarle la acción aquí, sin tocar el código
        holders = {(name, obj) for name, role in self.roles.items() for obj, grants in role["permisos"].items()
                   if "READ_ALL_PROJECTS" in grants}
        self.assertEqual(holders, {("AUDITOR", "project"), ("PROJECT_ADMIN", "project")})

    def test_quien_crea_proyectos_lee_todos_los_proyectos(self):
        # el alta de un proyecto todavía no tiene equipo: sin READ_ALL_PROJECTS, wasichai respondería el alta con un 404
        # porque ProjectReadScope no le dejaría leer lo que acaba de crear (security.md §9.7)
        creators = {name for name, role in self.roles.items() if "CREATE" in role["permisos"].get("project", [])}
        self.assertTrue(creators)
        for name in creators:
            self.assertIn("READ_ALL_PROJECTS", self.roles[name]["permisos"]["project"], name)
```

y reemplazar:

```python
        self.assertEqual(puts["/api/roles/AUDITOR/permissions"], {"permissions": [{"objectName": None, "action": "READ"}]})
```

por:

```python
        self.assertEqual(puts["/api/roles/AUDITOR/permissions"],
                         {"permissions": [{"objectName": None, "action": "READ"},
                                          {"objectName": "project", "action": "READ_ALL_PROJECTS"}]})
```

En `model/test_design_docs.py`, reemplazar `("Proyecto", "F2", "WF-A", ["APPROVE"]))` por
`("Proyecto", "F2", "WF-A", ["APPROVE", "READ_ALL_PROJECTS"]))` y
`self.assertEqual(matrix["project"]["PROJECT_ADMIN"], ["READ", "CREATE", "UPDATE", "APPROVE"])` por
`self.assertEqual(matrix["project"]["PROJECT_ADMIN"], ["READ", "CREATE", "UPDATE", "APPROVE", "READ_ALL_PROJECTS"])`.

En `model/test_apply.py` (`NewTenantTests`; `phase_subset` conserva las acciones de `project`, así que el modelo de F2 y el
de F3 también la declaran), reemplazar:

| Texto actual | Texto nuevo |
|---|---|
| `"done: 332 created, 21 updated, 0 skipped"` | `"done: 333 created, 21 updated, 0 skipped"` |
| `"done: 0 created, 0 updated, 332 skipped"` (las dos) | `"done: 0 created, 0 updated, 333 skipped"` |
| `(232 creados y 15 conjuntos` | `(233 creados y 15 conjuntos` |
| `"done: 232 created, 15 updated, 0 skipped"` | `"done: 233 created, 15 updated, 0 skipped"` |
| `"done: 109 created, 6 updated, 230 skipped"` | `"done: 109 created, 6 updated, 231 skipped"` |
| `"done: 168 created, 11 updated, 0 skipped"` | `"done: 169 created, 11 updated, 0 skipped"` |
| `"done: 188 created, 10 updated, 166 skipped"` | `"done: 188 created, 10 updated, 167 skipped"` |

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe/model && python3 -m unittest test_apply_roles test_design_docs test_apply 2>&1 | tail -3
```

Esperado: `FAILED` (`test_solo_auditor…`, `test_leen_todos_los_proyectos…`, `test_crea_los_15_roles…`,
`test_la_ficha_de_project`, `test_celdas_de_la_matriz` y las tres de `NewTenantTests`).

- [ ] **Paso 3: El modelo, los roles y las dos celdas**

En `model/model.json`, en el objeto `project` (el primero), reemplazar:

```json
      "actions": [{"name": "APPROVE", "label": "Aprobar"}],
```

por:

```json
      "actions": [{"name": "APPROVE", "label": "Aprobar"}, {"name": "READ_ALL_PROJECTS", "label": "Leer todos los proyectos"}],
```

(es la primera aparición de esa línea en el archivo; las de `area_of_influence` y las demás no cambian).

En `model/roles.json`, en `PROJECT_ADMIN`, reemplazar `"project": ["READ", "CREATE", "UPDATE", "APPROVE"],` por
`"project": ["READ", "CREATE", "UPDATE", "APPROVE", "READ_ALL_PROJECTS"],`, y en `AUDITOR` reemplazar:

```json
      "permisos": {
        "*": ["READ"]
      }
```

por:

```json
      "permisos": {
        "*": ["READ"],
        "project": ["READ_ALL_PROJECTS"]
      }
```

En `docs/security.md` §7.2, reemplazar `` | `project` | CRU `APPROVE` | RU | `` por
`` | `project` | CRU `APPROVE` `READ_ALL_PROJECTS` | RU | ``.

En `docs/database-model.md`, en la ficha de `project`, reemplazar:

```markdown
- **Acciones declaradas:** `APPROVE`.
- **Workflow:** WF-A.
```

por:

```markdown
- **Acciones declaradas:** `APPROVE`, `READ_ALL_PROJECTS`.
- **Workflow:** WF-A.
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe/model && python3 -m unittest 2>&1 | tail -1 && python3 apply.py --validate-only && echo valido
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ArchitectureTest' --tests 'sgspe.common.ModelCatalogTest'
```

Esperado: `OK` (210 pruebas: las 208 de `main` y 2 nuevas), `valido` (`model/` lo formatea a mano, no prettier);
`BUILD SUCCESSFUL` (`ArchitectureTest` lee `roles.json` para los literales de rol y `ModelCatalog` empaqueta el modelo).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add model/model.json model/roles.json model/test_apply_roles.py model/test_design_docs.py model/test_apply.py \
  docs/security.md docs/database-model.md
git commit -F - <<'MSG'
feat: acción declarada READ_ALL_PROJECTS para AUDITOR y PROJECT_ADMIN

project declara READ_ALL_PROJECTS («Leer todos los proyectos»); roles.json la concede solo a AUDITOR
y PROJECT_ADMIN, como confirmó el usuario el 2026-10-10. Las pruebas fijan que quien crea proyectos
la tiene y que ningún otro rol la recibe (ASM-106).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 4: La clasificación de los objetos (`ProjectScope.TENANT` y `ReadGroup`)

**Files:**
- Modify: `src/main/kotlin/sgspe/scope/ProjectScope.kt`
- Test: `src/test/kotlin/sgspe/ArchitectureTest.kt`

**Interfaces:**
- Consumes: `model/model.json` (72 objetos, sus relaciones con `fieldName`).
- Produces: `ProjectScope.TENANT: Set<String>` (los 20 maestros), `ProjectScope.PROJECT = "project"`,
  `ProjectScope.ASSIGNMENT = "project_assignment"`, `ProjectScope.readGroup(objectName: String): ReadGroup` y
  `enum class ReadGroup { PROJECT, ASSIGNMENT, ROOT, CHILD, TENANT, UNCLASSIFIED }` (en `ProjectScope.kt`), que usan las
  Tareas 6, 7 y 12.

La regla nueva de `ArchitectureTest` es la que impide que F5 a F8 agreguen un objeto sin clasificarlo (riesgo 6 de la
especificación): cada objeto en un solo grupo, todo objeto con la relación `project` es una raíz (o `project_assignment`),
y el padre de cada hijo es una raíz por el campo que dice `CHILDREN`.

- [ ] **Paso 1: Escribir la prueba que falla**

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, reemplazar:

```kotlin
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
```

por:

```kotlin
import org.junit.jupiter.api.Test
import sgspe.scope.ProjectScope
import tools.jackson.databind.json.JsonMapper
```

y, antes de:

```kotlin
    @Test
    fun `observability no depende de otros paquetes de sgspe`() {
```

agregar:

```kotlin
    // el alcance de lectura por proyecto (ASM-106, security.md §9.7): cada objeto de model.json está en un solo grupo de
    // ProjectScope, todo objeto con la relación `project` es una raíz (o project_assignment) y el padre de cada hijo es una raíz
    // por el campo que dice CHILDREN. así una fase nueva (F5 a F8) no agrega un objeto sin clasificarlo: ProjectReadScope no
    // admitiría ningún registro suyo
    @Test
    fun `cada objeto del modelo esta clasificado en ProjectScope`() {
        val model = JsonMapper.builder().build().readTree(File("model/model.json"))
        val objects = model["objects"].values().map { it["name"].asString() }
        val groups =
            mapOf(
                "project" to setOf(ProjectScope.PROJECT),
                "project_assignment" to setOf(ProjectScope.ASSIGNMENT),
                "ROOTS" to ProjectScope.ROOTS,
                "CHILDREN" to ProjectScope.CHILDREN.keys,
                "TENANT" to ProjectScope.TENANT
            )
        for (name in objects) {
            val holders = groups.filterValues { name in it }.keys
            assertEquals(1, holders.size, "$name está en $holders: clasifíquelo en un solo grupo de ProjectScope")
        }
        val named = ProjectScope.ROOTS + ProjectScope.CHILDREN.keys + ProjectScope.TENANT
        assertEquals(emptySet<String>(), named - objects.toSet(), "ProjectScope nombra objetos que model.json no tiene")
        // (objeto, campo, destino) de cada relación con campo
        val relations =
            model["relationships"].values().mapNotNull { rel ->
                val field = rel["fieldName"]?.takeUnless { it.isNull }?.asString() ?: return@mapNotNull null
                Triple(rel["source"].asString(), field, rel["target"].asString())
            }
        val withProject = relations.filter { (_, field, target) -> field == "project" && target == "project" }.map { it.first }.toSet()
        assertEquals(ProjectScope.ROOTS + ProjectScope.ASSIGNMENT, withProject)
        for ((child, link) in ProjectScope.CHILDREN) {
            val (field, parent) = link
            assertTrue(parent in ProjectScope.ROOTS, "$child: su padre $parent no es una raíz")
            assertTrue(Triple(child, field, parent) in relations, "$child.$field no apunta a $parent")
        }
    }

```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ArchitectureTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'TENANT'`, `'PROJECT'`, `'ASSIGNMENT'`).

- [ ] **Paso 3: Los maestros, los grupos y `readGroup`**

En `src/main/kotlin/sgspe/scope/ProjectScope.kt`, reemplazar:

```kotlin
    // quien escribe una asignación con permiso sobre ella forma los equipos: no se le pide asignación (security.md §9.2)
    val EXEMPT: Set<String> = setOf("project_assignment")

    fun covers(objectName: String): Boolean = objectName == "project" || objectName in ROOTS || objectName in CHILDREN
}
```

por:

```kotlin
    // quien escribe una asignación con permiso sobre ella forma los equipos: no se le pide asignación (security.md §9.2)
    val EXEMPT: Set<String> = setOf("project_assignment")

    // los maestros del tenant (ASM-107, decisión 3 de la especificación 4.4): territorio, actores, línea base y el catálogo
    // topic. se comparten entre los proyectos del cliente y ProjectReadScope no los acota. mining_concession se vincula a los
    // proyectos por M2M (project_concession) pero es un maestro. las fases siguientes clasifican aquí sus objetos nuevos
    val TENANT: Set<String> =
        setOf(
            "administrative_unit",
            "locality",
            "community",
            "community_sector",
            "mining_concession",
            "land_parcel",
            "land_right",
            "environmental_feature",
            "person",
            "organization",
            "contact_method",
            "position",
            "actor_relationship",
            "school",
            "health_facility",
            "road_access",
            "festivity",
            "state_program",
            "local_conflict",
            "topic"
        )

    fun covers(objectName: String): Boolean = objectName == "project" || objectName in ROOTS || objectName in CHILDREN

    // cómo se lee un objeto (security.md §9.7, ASM-106): el grupo decide la condición de ProjectReadCondition
    fun readGroup(objectName: String): ReadGroup =
        when (objectName) {
            PROJECT -> ReadGroup.PROJECT
            ASSIGNMENT -> ReadGroup.ASSIGNMENT
            in ROOTS -> ReadGroup.ROOT
            in CHILDREN -> ReadGroup.CHILD
            in TENANT -> ReadGroup.TENANT
            else -> ReadGroup.UNCLASSIFIED
        }

    const val PROJECT = "project"
    const val ASSIGNMENT = "project_assignment"
}

// los grupos de la lectura por proyecto: el proyecto mismo, las asignaciones, las raíces con `project`, los hijos por su
// padre, los maestros del tenant y lo que nadie clasificó (falla cerrado; ArchitectureTest impide que llegue a main)
enum class ReadGroup { PROJECT, ASSIGNMENT, ROOT, CHILD, TENANT, UNCLASSIFIED }
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.ArchitectureTest' --tests 'sgspe.scope.*'
```

Esperado: `BUILD SUCCESSFUL`; `ArchitectureTest` con la prueba nueva en verde. Si falla con un objeto en dos grupos o en
ninguno, la clasificación de la especificación (§3.5) y `model.json` difieren: se corrige `TENANT`, nunca la prueba.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/scope/ProjectScope.kt src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -F - <<'MSG'
feat: clasificación de los objetos para el alcance de lectura

ProjectScope suma TENANT (los 20 maestros), ReadGroup y readGroup; ArchitectureTest exige que cada
objeto de model.json esté en un solo grupo y que todo objeto con project sea una raíz (ASM-106).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 5: `ProjectReach` («mis proyectos» o todos)

**Files:**
- Create: `src/main/kotlin/sgspe/common/ProjectReach.kt`
- Test: `src/test/kotlin/sgspe/common/ProjectReachTest.kt`

**Interfaces:**
- Consumes: `Records.all(objectName, filters, …)`, `Permissions.has(user, action, objectName)`, `RequestMemo.remember`,
  `Validity.inForce`, `LIMA`, el `Clock` de la aplicación.
- Produces: el bean `ProjectReach(records: Records, permissions: Permissions, clock: Clock)` con
  `suspend fun readsAll(user: AuthenticatedUser): Boolean`, `suspend fun assigned(user: AuthenticatedUser): List<UUID>`,
  `suspend fun reaches(user: AuthenticatedUser, project: UUID): Boolean` y
  `suspend fun mine(user: AuthenticatedUser): List<UUID>?` (null: todos), y las constantes `READ_ALL_PROJECTS`, `PROJECT`
  y `ASSIGNMENT`. Lo usan `ProjectReadScope` (Tarea 7) y `ExportProjects`, `ExportLog`, `Dashboards` y `AttentionReport`
  (Tarea 9). Fuera de `common` y `scope`, nadie llama a `readsAll` (`ArchitectureTest`, Tarea 7).

`ProjectReach` es `@Component`: el plugin `kotlin("plugin.spring")` lo abre, así que las pruebas lo extienden igual que
`ExportProjectsTest` extiende `Records`.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/common/ProjectReachTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// ProjectReach (ASM-106, elección 6 de la especificación 4.4): quién lee todos los proyectos, sin literales de rol, y las
// asignaciones vigentes de quien llama, con la misma vigencia que ProjectScopeGuard (hoy en Lima)
class ProjectReachTest {
    private val userId = UUID.randomUUID()
    private val user = AuthenticatedUser(userId, UUID.randomUUID(), "persona@sgspe.test", listOf("ANALYST"), null)
    private val admin = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "admin@sgspe.test", listOf("ADMIN"), null)
    private val current = UUID.randomUUID()
    private val expired = UUID.randomUUID()
    private val inactive = UUID.randomUUID()

    // el 15 de octubre de 2026 a las 03:00 UTC todavía es el 14 en Lima
    private val clock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private fun row(attributes: Map<String, Any?>) = RecordResponse(UUID.randomUUID().toString(), null, null, attributes)

    // un Records que suspende de verdad, como el de la base (la lección de ExportProjectsTest)
    private fun records(
        rows: List<RecordResponse>,
        forbidden: Boolean = false
    ) = object : Records(
        provider<RecordService>(),
        provider<RelatedRecordService>(),
        provider<WorkflowService>(),
        mock(MetadataService::class.java),
        mock(Callers::class.java),
        mock(ModelCatalog::class.java),
        mock(DerivedVersions::class.java)
    ) {
        override suspend fun all(
            objectName: String,
            filters: Map<String, String>,
            criteria: List<RecordCriterion>,
            ids: List<UUID>?,
            max: Int?,
            field: String
        ): List<RecordResponse> {
            delay(1)
            if (forbidden) throw ForbiddenException("Missing permission READ")
            assertEquals(ProjectReach.ASSIGNMENT, objectName)
            assertEquals(mapOf("member_user_id" to userId.toString()), filters)
            return rows
        }
    }

    // Permissions con la respuesta fija de has; cuenta las preguntas
    private class Held(
        private val holds: Boolean
    ) : Permissions(mock(CurrentUser::class.java), mock(MetadataService::class.java)) {
        var asked = 0

        override suspend fun has(
            user: AuthenticatedUser,
            action: String,
            objectName: String
        ): Boolean {
            asked++
            assertEquals(ProjectReach.READ_ALL_PROJECTS to ProjectReach.PROJECT, action to objectName)
            return holds
        }
    }

    private fun assignment(
        project: UUID,
        active: Boolean = true,
        start: String? = null,
        end: String? = null,
        member: UUID = userId
    ) = row(
        mapOf(
            "project" to project.toString(),
            "member_user_id" to member.toString(),
            "active" to active,
            "start_date" to start,
            "end_date" to end
        )
    )

    @Test
    fun `lee todo el ADMIN y quien tiene READ_ALL_PROJECTS sobre project, y nadie mas`() {
        val none = Held(false)
        assertTrue(runBlocking { ProjectReach(records(emptyList()), none, clock).readsAll(admin) })
        assertEquals(0, none.asked, "al ADMIN no se le pregunta")
        assertTrue(runBlocking { ProjectReach(records(emptyList()), Held(true), clock).readsAll(user) })
        assertFalse(runBlocking { ProjectReach(records(emptyList()), none, clock).readsAll(user) })
    }

    @Test
    fun `dentro de RequestMemo se pregunta una sola vez`() {
        val held = Held(true)
        val reach = ProjectReach(records(emptyList()), held, clock)
        runBlocking { RequestMemo.within { repeat(3) { reach.readsAll(user) } } }
        assertEquals(1, held.asked)
    }

    @Test
    fun `mis proyectos son las asignaciones vigentes hoy en Lima, sin repetidos`() {
        val rows =
            listOf(
                assignment(current, start = "2026-10-01", end = "2026-10-14"),
                assignment(current),
                assignment(expired, end = "2026-10-13"),
                assignment(inactive, active = false),
                // el 15 todavía no empieza en Lima
                assignment(UUID.randomUUID(), start = "2026-10-15"),
                // una fila de otra persona que un filtro ignorado dejaría pasar
                assignment(UUID.randomUUID(), member = UUID.randomUUID())
            )
        val reach = ProjectReach(records(rows), Held(false), clock)
        assertEquals(listOf(current), runBlocking { reach.assigned(user) })
        assertEquals(listOf(current), runBlocking { reach.mine(user) })
    }

    @Test
    fun `quien lee todo no tiene proyectos por defecto, y sin READ sobre las asignaciones no tiene ninguno`() {
        assertNull(runBlocking { ProjectReach(records(listOf(assignment(current))), Held(true), clock).mine(user) })
        assertEquals(emptyList<UUID>(), runBlocking { ProjectReach(records(emptyList(), forbidden = true), Held(false), clock).mine(user) })
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ProjectReachTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ProjectReach'`).

- [ ] **Paso 3: Implementar**

Crear `src/main/kotlin/sgspe/common/ProjectReach.kt`:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.ForbiddenException
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// «mis proyectos» o todos (elección 6 de la especificación 4.4, ASM-106): la única pieza que lo decide. la comparten el alcance
// de lectura (ProjectReadScope), los proyectos por defecto de tableros, reporte y exportaciones, y la exención de
// ExportProjects y de ExportLog. lee todos los proyectos el ADMIN y quien tiene la acción declarada READ_ALL_PROJECTS sobre
// project (en roles.json, AUDITOR y PROJECT_ADMIN); nunca compara nombres de rol (RQ-SEC-005). vive en common porque scope y
// privacy no se conocen (architecture.md §5.3)
@Component
class ProjectReach(
    private val records: Records,
    private val permissions: Permissions,
    private val clock: Clock
) {
    // si quien llama lee todos los proyectos del tenant. dentro de un comando que abrió RequestMemo se pregunta una sola vez
    suspend fun readsAll(user: AuthenticatedUser): Boolean =
        user.isAdmin || RequestMemo.remember(MEMO to user.userId) { permissions.has(user, READ_ALL_PROJECTS, PROJECT) }

    // las asignaciones vigentes de quien llama (Validity.inForce, hoy en Lima), sin repetidos. se leen como quien llama: el
    // alcance le deja ver las suyas aunque estén vencidas (ASM-106). sin READ sobre project_assignment, ninguna
    suspend fun assigned(user: AuthenticatedUser): List<UUID> {
        val today = LocalDate.now(clock.withZone(LIMA))
        val rows =
            try {
                records.all(ASSIGNMENT, filters = mapOf("member_user_id" to user.userId.toString()))
            } catch (_: ForbiddenException) {
                return emptyList()
            }
        return rows
            .filter { row ->
                Records.uuid(row.attributes["member_user_id"]) == user.userId &&
                    Validity.inForce(
                        Values.bool(row.attributes["active"]) == true,
                        Values.text(row.attributes["start_date"])?.let(LocalDate::parse),
                        Values.text(row.attributes["end_date"])?.let(LocalDate::parse),
                        today
                    )
            }.mapNotNull { Records.uuid(it.attributes["project"]) }
            .distinct()
    }

    // si quien llama alcanza `project`: lo lee todo o está asignado a él. la exención de ExportProjects
    suspend fun reaches(
        user: AuthenticatedUser,
        project: UUID
    ): Boolean {
        if (readsAll(user)) return true
        return project in assigned(user)
    }

    // los proyectos por defecto: null (todos los que lee) para quien lee todo; si no, sus asignaciones vigentes (quizá ninguna)
    suspend fun mine(user: AuthenticatedUser): List<UUID>? {
        if (readsAll(user)) return null
        return assigned(user)
    }

    companion object {
        const val READ_ALL_PROJECTS = "READ_ALL_PROJECTS"
        const val PROJECT = "project"
        const val ASSIGNMENT = "project_assignment"
        private const val MEMO = "project-reach"
    }
}
```

- [ ] **Paso 4: Correr la prueba para verla pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.common.ProjectReachTest'
```

Esperado: `BUILD SUCCESSFUL`; 4 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/ProjectReach.kt src/test/kotlin/sgspe/common/ProjectReachTest.kt
git commit -F - <<'MSG'
feat: ProjectReach decide mis proyectos o todos

Una sola pieza en common: lee todo el ADMIN y quien tiene READ_ALL_PROJECTS sobre project (sin
literales de rol); si no, las asignaciones vigentes de hoy en Lima. La comparten el alcance, los
tableros, el reporte y las exportaciones (ASM-106).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 6: La condición pura (`ProjectReadCondition`)

**Files:**
- Create: `src/main/kotlin/sgspe/scope/ProjectReadCondition.kt`
- Test: `src/test/kotlin/sgspe/scope/ProjectReadConditionTest.kt`

**Interfaces:**
- Consumes: `ReadGroup` (Tarea 4).
- Produces: `data class AssignmentColumns(table, project, member, active, startDate, endDate)`,
  `data class ParentColumns(table, project)`,
  `data class ReadColumns(scope: String?, assignments: AssignmentColumns?, optional: Boolean = false, member: String? = null, parent: ParentColumns? = null)`
  y `object ProjectReadCondition` con `NOTHING = "false"`, `ID_COLUMN = "\"id\""`,
  `complete(group: ReadGroup, columns: ReadColumns): Boolean` y
  `condition(group, columns, organizationId: UUID, user: UUID, today: LocalDate, bind: (Any) -> String): String?`, que
  usa la Tarea 7. Todas las cadenas de `*Columns` llegan ya entrecomilladas.

Es función pura, como `ConfidentialCondition`: las pruebas fijan el SQL exacto y el orden de los valores enlazados. Dos
detalles del SQL del store de wasichai 0.6.0 (`PhysicalTableRecordStore.whereClause` y `byIdClause`, y `RelationTargets.sql`)
mandan: la consulta externa no usa alias, así que la columna del objeto leído va sin alias; y la consulta externa nombra
`id` y `organization_id` sin calificar, así que las subconsultas califican todas sus columnas (`a.`, `t.`) para que ninguna
se resuelva contra la tabla externa.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/scope/ProjectReadConditionTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// la condición de lectura por proyecto por grupo (security.md §9.7, ASM-106), sin base: qué lee cada quien, que los valores
// van siempre enlazados y que falla cerrada sin una columna o con un objeto sin clasificar
class ProjectReadConditionTest {
    private val organization = UUID.fromString("00000000-0000-0000-0000-0000000000a0")
    private val user = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val today = LocalDate.parse("2026-10-14")
    private val assignments =
        AssignmentColumns("\"data\".\"pa_tbl\"", "\"project\"", "\"member_user_id\"", "\"active\"", "\"start_date\"", "\"end_date\"")

    // «mis proyectos» con sus parámetros a partir de :c<first>
    private fun mine(first: Int): String =
        "SELECT a.\"project\" FROM \"data\".\"pa_tbl\" a WHERE a.organization_id = :c$first AND a.\"member_user_id\" = :c${first + 1} " +
            "AND a.\"active\" IS TRUE AND (a.\"start_date\" IS NULL OR a.\"start_date\" <= :c${first + 2}) " +
            "AND (a.\"end_date\" IS NULL OR a.\"end_date\" >= :c${first + 3})"

    private fun condition(
        group: ReadGroup,
        columns: ReadColumns
    ): Pair<String?, List<Any>> {
        val bound = mutableListOf<Any>()
        val text =
            ProjectReadCondition.condition(group, columns, organization, user, today) { value ->
                bound += value
                ":c${bound.size - 1}"
            }
        return text to bound
    }

    @Test
    fun `un proyecto se lee si esta entre mis proyectos`() {
        val (text, bound) = condition(ReadGroup.PROJECT, ReadColumns(ProjectReadCondition.ID_COLUMN, assignments))
        assertEquals("\"id\" IN (${mine(0)})", text)
        assertEquals(listOf<Any>(organization, user, today, today), bound)
    }

    @Test
    fun `cada uno ve sus asignaciones, aun vencidas, y las de sus equipos`() {
        val (text, bound) = condition(ReadGroup.ASSIGNMENT, ReadColumns("\"project\"", assignments, member = "\"member_user_id\""))
        assertEquals("\"member_user_id\" = :c0 OR \"project\" IN (${mine(1)})", text)
        assertEquals(listOf<Any>(user, organization, user, today, today), bound)
    }

    @Test
    fun `una raiz se lee por su proyecto y, si es opcional, tambien sin proyecto`() {
        assertEquals("\"project\" IN (${mine(0)})", condition(ReadGroup.ROOT, ReadColumns("\"project\"", assignments)).first)
        assertEquals(
            "\"project\" IS NULL OR \"project\" IN (${mine(0)})",
            condition(ReadGroup.ROOT, ReadColumns("\"project\"", assignments, optional = true)).first
        )
    }

    @Test
    fun `un hijo se lee por el proyecto de su padre y, con padre opcional, tambien sin padre`() {
        val parent = ParentColumns("\"data\".\"commitment_tbl\"", "\"project\"")
        val child = "\"commitment\" IN (SELECT t.\"id\" FROM \"data\".\"commitment_tbl\" t WHERE t.organization_id = :c0 AND t.\"project\" IN (${mine(1)}))"
        val (text, bound) = condition(ReadGroup.CHILD, ReadColumns("\"commitment\"", assignments, parent = parent))
        assertEquals(child, text)
        assertEquals(listOf<Any>(organization, organization, user, today, today), bound)
        assertEquals(
            "\"commitment\" IS NULL OR $child",
            condition(ReadGroup.CHILD, ReadColumns("\"commitment\"", assignments, optional = true, parent = parent)).first
        )
    }

    @Test
    fun `un maestro del tenant no se restringe`() {
        assertNull(condition(ReadGroup.TENANT, ReadColumns(null, null)).first)
    }

    @Test
    fun `sin clasificar o sin una columna no admite ningun registro`() {
        assertEquals("false", condition(ReadGroup.UNCLASSIFIED, ReadColumns("\"project\"", assignments)).first)
        assertEquals("false", condition(ReadGroup.ROOT, ReadColumns(null, assignments)).first)
        assertEquals("false", condition(ReadGroup.ROOT, ReadColumns("\"project\"", null)).first)
        assertEquals("false", condition(ReadGroup.ASSIGNMENT, ReadColumns("\"project\"", assignments)).first)
        assertEquals("false", condition(ReadGroup.CHILD, ReadColumns("\"commitment\"", assignments)).first)
        assertEquals("false", condition(ReadGroup.PROJECT, ReadColumns(ProjectReadCondition.ID_COLUMN, null)).first)
    }

    @Test
    fun `ningun valor va en el texto`() {
        val (text, _) = condition(ReadGroup.ROOT, ReadColumns("\"project\"", assignments))
        for (value in listOf(organization.toString(), user.toString(), today.toString())) {
            assertEquals(false, text!!.contains(value), value)
        }
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.ProjectReadConditionTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'AssignmentColumns'`).

- [ ] **Paso 3: Implementar**

Crear `src/main/kotlin/sgspe/scope/ProjectReadCondition.kt`:

```kotlin
package sgspe.scope

import java.time.LocalDate
import java.util.UUID

// la tabla física de project_assignment del tenant y sus columnas, ya entrecomilladas (WasichaiSchemas.dataTable y
// SqlIdentifier.quote): lo que arma «mis proyectos»
data class AssignmentColumns(
    val table: String,
    val project: String,
    val member: String,
    val active: String,
    val startDate: String,
    val endDate: String
)

// la tabla física del padre de un hijo y su columna `project`, ya entrecomilladas
data class ParentColumns(
    val table: String,
    val project: String
)

// lo que la condición necesita del objeto leído, ya entrecomillado. `scope`: su columna de alcance (`id` en project,
// `project` en las raíces y en project_assignment, la del padre en los hijos); `optional`: si esa relación es opcional en el
// modelo (ModelCatalog.isRequiredRelation); `member`: member_user_id, solo en project_assignment; `parent`: solo en los
// hijos. null en una columna: la definición no la trae, y la condición falla cerrada
data class ReadColumns(
    val scope: String?,
    val assignments: AssignmentColumns?,
    val optional: Boolean = false,
    val member: String? = null,
    val parent: ParentColumns? = null
)

// lo que decide qué registros de proyecto lee quien llama, puro (security.md §9.7, ASM-106): SQL sobre la tabla de
// project_assignment, sin lecturas desde Kotlin (leerla con Records dentro del alcance lo dispararía sobre sí mismo). la
// columna del objeto leído va sin alias, porque el SQL del store de wasichai no usa alias; las subconsultas califican siempre
// las suyas. los valores van siempre enlazados (bind), nunca concatenados. solo ProjectReadScope la usa (ArchitectureTest)
object ProjectReadCondition {
    // la condición que no admite ningún registro: falla cerrada
    const val NOTHING = "false"

    // la columna física del id de un registro de wasichai
    const val ID_COLUMN = "\"id\""

    // si `columns` trae todo lo que la condición del grupo necesita
    fun complete(
        group: ReadGroup,
        columns: ReadColumns
    ): Boolean =
        when (group) {
            ReadGroup.TENANT -> true
            ReadGroup.UNCLASSIFIED -> false
            ReadGroup.PROJECT -> columns.assignments != null
            ReadGroup.ASSIGNMENT -> columns.assignments != null && columns.scope != null && columns.member != null
            ReadGroup.ROOT -> columns.assignments != null && columns.scope != null
            ReadGroup.CHILD -> columns.assignments != null && columns.scope != null && columns.parent != null
        }

    // null: no restringe (un maestro del tenant). NOTHING: un objeto sin clasificar o sin las columnas. si no, la condición
    // del grupo: el proyecto entre mis proyectos; mis asignaciones y las de mis equipos; la raíz de mis proyectos; el hijo
    // cuyo padre es de mis proyectos; con la relación opcional, también el registro sin ella (del tenant, §9.2 punto 4)
    fun condition(
        group: ReadGroup,
        columns: ReadColumns,
        organizationId: UUID,
        user: UUID,
        today: LocalDate,
        bind: (Any) -> String
    ): String? {
        if (group == ReadGroup.TENANT) return null
        if (!complete(group, columns)) return NOTHING
        val assignments = columns.assignments!!
        val mine = { mine(assignments, organizationId, user, today, bind) }
        return when (group) {
            ReadGroup.PROJECT -> "$ID_COLUMN IN (${mine()})"
            ReadGroup.ASSIGNMENT -> "${columns.member} = ${bind(user)} OR ${columns.scope} IN (${mine()})"
            ReadGroup.ROOT -> orUnset(columns, "${columns.scope} IN (${mine()})")
            ReadGroup.CHILD -> {
                val parent = columns.parent!!
                orUnset(
                    columns,
                    "${columns.scope} IN (SELECT t.$ID_COLUMN FROM ${parent.table} t WHERE t.organization_id = ${bind(organizationId)} " +
                        "AND t.${parent.project} IN (${mine()}))"
                )
            }
            else -> NOTHING
        }
    }

    // «mis proyectos»: los de las asignaciones vigentes de quien llama, con la vigencia de Validity.inForce (active y, si tiene
    // fechas, hoy en Lima entre ellas, con los extremos incluidos)
    private fun mine(
        a: AssignmentColumns,
        organizationId: UUID,
        user: UUID,
        today: LocalDate,
        bind: (Any) -> String
    ): String =
        "SELECT a.${a.project} FROM ${a.table} a WHERE a.organization_id = ${bind(organizationId)} AND a.${a.member} = ${bind(user)} " +
            "AND a.${a.active} IS TRUE AND (a.${a.startDate} IS NULL OR a.${a.startDate} <= ${bind(today)}) " +
            "AND (a.${a.endDate} IS NULL OR a.${a.endDate} >= ${bind(today)})"

    private fun orUnset(
        columns: ReadColumns,
        condition: String
    ): String = if (columns.optional) "${columns.scope} IS NULL OR $condition" else condition
}
```

- [ ] **Paso 4: Correr la prueba para verla pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.scope.ProjectReadConditionTest'
```

Esperado: `BUILD SUCCESSFUL`; 7 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/scope/ProjectReadCondition.kt src/test/kotlin/sgspe/scope/ProjectReadConditionTest.kt
git commit -F - <<'MSG'
feat: condición SQL del alcance de lectura por proyecto

ProjectReadCondition, pura: mis proyectos son una subconsulta sobre project_assignment vigente en
Lima; proyecto, asignaciones, raíces e hijos por su grupo, con la relación opcional como del tenant;
falla cerrada sin una columna o sin clasificar; valores siempre enlazados (ASM-106).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 7: El componente (`ProjectReadScope`)

**Files:**
- Create: `src/main/kotlin/sgspe/scope/ProjectReadScope.kt`
- Modify: `src/main/kotlin/sgspe/scope/ProjectScopeGuard.kt` (comentario), `src/test/kotlin/sgspe/ArchitectureTest.kt`
- Test: `src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt`

**Interfaces:**
- Consumes: `ProjectReach.readsAll` (Tarea 5), `ProjectScope.readGroup`, `ProjectScope.CHILDREN`, `ProjectScope.ASSIGNMENT`
  (Tarea 4), `ProjectReadCondition` (Tarea 6), `ModelCatalog.isRequiredRelation(source, fieldName)`,
  `MetadataService.loadDefinition(organizationId, name)`, `WasichaiSchemas.dataTable(table)`, `SqlIdentifier.quote`, el
  `Clock` y `LIMA`; el SPI `wasichai.core.data.RecordReadScope` (`suspend fun criterion(caller: AuthenticatedUser,
  definition: ObjectDefinition): RecordCriterion?`).
- Produces: el bean `ProjectReadScope : RecordReadScope`. Su constructor primario `internal` recibe
  `load: suspend (UUID, String) -> ObjectDefinition` para las pruebas; Spring usa el secundario `@Autowired`, que lo arma
  con `MetadataService`.

Es el corazón del plan: desde que entra, todo `*ApiTest` que lee como un usuario sin asignación cambia de resultado. Por
eso la migración de la suite (Tarea 13) y las pruebas de integración propias (Tareas 11 y 12) dependen de esta tarea, y la
CI del PR las corre juntas.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt`:

```kotlin
package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Records
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.WasichaiSchemas
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

// ProjectReadScope sin base (ASM-106): los maestros no cuestan consultas, READ_ALL_PROJECTS exime, y falla cerrado si los
// permisos o una definición fallan o le falta una columna. la condición misma la prueba ProjectReadConditionTest
class ProjectReadScopeTest {
    private val organization = UUID.randomUUID()
    private val caller = AuthenticatedUser(UUID.randomUUID(), organization, "persona@sgspe.test", listOf("ANALYST"), null)
    private val catalog = ModelCatalog.fromClasspath()
    private val schemas = WasichaiSchemas("wasichai", "data")
    private val clock = Clock.fixed(Instant.parse("2026-10-15T03:00:00Z"), ZoneOffset.UTC)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private val records =
        Records(
            provider<RecordService>(),
            provider<RelatedRecordService>(),
            provider<WorkflowService>(),
            mock(MetadataService::class.java),
            mock(Callers::class.java),
            mock(ModelCatalog::class.java),
            mock(DerivedVersions::class.java)
        )

    // ProjectReach con la respuesta fija (o la falla) de readsAll
    private fun reach(readsAll: () -> Boolean) =
        object : ProjectReach(records, Permissions(mock(CurrentUser::class.java), mock(MetadataService::class.java)), clock) {
            override suspend fun readsAll(user: AuthenticatedUser): Boolean = readsAll()
        }

    private fun definition(
        name: String,
        vararg fields: String
    ) = ObjectDefinition(
        CustomObject(UUID.randomUUID(), organization, name, name, name, null, true, "${name}_tbl", null, null),
        fields.mapIndexed { i, field ->
            CustomField(
                UUID.randomUUID(),
                UUID.randomUUID(),
                field,
                field,
                FieldType.TEXT,
                field,
                false,
                false,
                null,
                null,
                i,
                null,
                null,
                emptyMap(),
                true,
                true
            )
        }
    )

    private val assignment = definition("project_assignment", "project", "member_user_id", "active", "start_date", "end_date")

    // las definiciones que el alcance pide, por nombre; cuenta las consultas
    private class Definitions(
        private val known: Map<String, ObjectDefinition>
    ) {
        val asked = mutableListOf<String>()

        suspend fun load(
            organization: UUID,
            name: String
        ): ObjectDefinition {
            asked += name
            return known[name] ?: throw NotFoundException("Object $name does not exist")
        }
    }

    private fun scope(
        readsAll: () -> Boolean = { false },
        definitions: Definitions = Definitions(mapOf("project_assignment" to assignment))
    ) = ProjectReadScope(reach(readsAll), catalog, schemas, clock, definitions::load)

    private fun condition(
        scope: ProjectReadScope,
        definition: ObjectDefinition
    ): String? = runBlocking { scope.criterion(caller, definition) }?.condition(definition) { ":p" }

    @Test
    fun `un maestro del tenant no se acota ni consulta nada`() {
        val definitions = Definitions(emptyMap())
        var asked = false
        val readsAll = {
            asked = true
            false
        }
        assertNull(runBlocking { scope(readsAll, definitions).criterion(caller, definition("person", "given_names")) })
        assertEquals(false, asked)
        assertEquals(emptyList<String>(), definitions.asked)
    }

    @Test
    fun `quien tiene READ_ALL_PROJECTS no se acota`() {
        assertNull(runBlocking { scope({ true }).criterion(caller, definition("stakeholder", "project")) })
    }

    @Test
    fun `una raiz se acota por la tabla de las asignaciones del tenant`() {
        val text = condition(scope(), definition("stakeholder", "project"))!!
        assertTrue(text.startsWith("\"project\" IN (SELECT a.\"project\" FROM \"data\".\"project_assignment_tbl\" a"), text)
    }

    @Test
    fun `un hijo se acota por la tabla de su padre y la relacion opcional admite el vacio`() {
        val definitions =
            Definitions(mapOf("project_assignment" to assignment, "commitment" to definition("commitment", "project")))
        val text = condition(scope(definitions = definitions), definition("delivery", "commitment"))!!
        assertTrue(text.startsWith("\"commitment\" IS NULL OR \"commitment\" IN (SELECT t.\"id\" FROM \"data\".\"commitment_tbl\" t"), text)
        assertEquals(listOf("project_assignment", "commitment"), definitions.asked)
    }

    @Test
    fun `si los permisos fallan, falta la definicion del padre o falta una columna, ningun registro`() {
        val failing = scope({ throw IllegalStateException("la base no responde") })
        assertEquals("false", condition(failing, definition("stakeholder", "project")))
        assertEquals("false", condition(scope(), definition("stakeholder_assessment", "stakeholder")))
        assertEquals("false", condition(scope(), definition("stakeholder", "name")))
        val partial = Definitions(mapOf("project_assignment" to definition("project_assignment", "project", "member_user_id", "active")))
        assertEquals("false", condition(scope(definitions = partial), definition("stakeholder", "project")))
    }

    @Test
    fun `un objeto sin clasificar no admite ningun registro`() {
        assertEquals("false", condition(scope(), definition("indicator_measurement", "project")))
    }
}
```

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, antes de:

```kotlin
    @Test
    fun `observability no depende de otros paquetes de sgspe`() {
```

agregar:

```kotlin
    // la condición del alcance por proyecto la arma solo ProjectReadScope, y «lee todos los proyectos» lo preguntan solo common y
    // scope: los demás paquetes usan mine, assigned o reaches de ProjectReach (elección 6 de la especificación 4.4)
    @Test
    fun `solo ProjectReadScope arma la condicion y solo common y scope preguntan si se lee todo`() {
        val scope = "/sgspe/scope/ProjectReadScope.kt"
        val definition = "/sgspe/scope/ProjectReadCondition.kt"
        val others = sources(main).filterNot { file -> listOf(scope, definition).any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, READ_CONDITION))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(scope) })
        val outside = sources(main).filterNot { isCommon(it) || it.invariantSeparatorsPath.contains("/sgspe/scope/") }
        assertEquals(emptyList<String>(), offenders(outside, READS_ALL))
        assertTrue(flags(READ_CONDITION, "ProjectReadCondition.condition(group, columns, organization, user, today, bind)"))
        assertFalse(flags(READ_CONDITION, "// ProjectReadCondition solo en ProjectReadScope"))
        assertTrue(flags(READS_ALL, "if (reach.readsAll(user)) return null"))
        assertFalse(flags(READS_ALL, "val projects = reach.mine(user)"))
    }

```

y, en su `companion object`, reemplazar:

```kotlin
        private val DERIVED_RESTORE_NEW = Regex("""\bDerivedRestore\s*\(""")
```

por:

```kotlin
        private val DERIVED_RESTORE_NEW = Regex("""\bDerivedRestore\s*\(""")
        private val READ_CONDITION = Regex("""\bProjectReadCondition\b""")
        private val READS_ALL = Regex("""\.\s*readsAll\s*\(""")
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.ProjectReadScopeTest' --tests 'sgspe.ArchitectureTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ProjectReadScope'`).

- [ ] **Paso 3: Implementar**

Crear `src/main/kotlin/sgspe/scope/ProjectReadScope.kt`:

```kotlin
package sgspe.scope

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.ProjectReach
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordReadScope
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import wasichai.core.platform.WasichaiSchemas
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// el RecordReadScope del alcance por proyecto (WAS-ADR-0048, ADR-0029, ASM-106, security.md §9.7): wasichai lo pide para cada
// persona o cuenta de servicio que no es ADMIN (nunca para la plataforma ni las automatizaciones) y aplica la condición de
// ProjectReadCondition en listas, totales, detalle, relacionados, destinos de RELATION, historial, auditoría, transiciones,
// features GIS, archivos y agente; fuera de ella el registro no existe (404, o el 400 de una RELATION). los maestros del tenant
// no se acotan y no cuestan consultas; quien tiene READ_ALL_PROJECTS sobre project (ProjectReach) lee todo. se compone por
// AND con ConfidentialAttentionScope (§9.6). falla cerrado: un objeto sin clasificar, una definición sin sus columnas, o una
// consulta de permisos o de definiciones que falla, no admiten ningún registro, con una advertencia sin datos del registro
@Component
class ProjectReadScope internal constructor(
    private val reach: ProjectReach,
    private val catalog: ModelCatalog,
    private val schemas: WasichaiSchemas,
    private val clock: Clock,
    private val load: suspend (UUID, String) -> ObjectDefinition
) : RecordReadScope {
    @Autowired
    constructor(
        reach: ProjectReach,
        metadata: MetadataService,
        catalog: ModelCatalog,
        schemas: WasichaiSchemas,
        clock: Clock
    ) : this(reach, catalog, schemas, clock, { organization, name -> metadata.loadDefinition(organization, name) })

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun criterion(
        caller: AuthenticatedUser,
        definition: ObjectDefinition
    ): RecordCriterion? {
        val objectName = definition.obj.name
        val group = ProjectScope.readGroup(objectName)
        if (group == ReadGroup.TENANT) return null
        if (group == ReadGroup.UNCLASSIFIED) {
            log.warn("ProjectReadScope: {} no está clasificado en ProjectScope; no se admite ningún registro", objectName)
            return NOTHING
        }
        val columns =
            try {
                if (reach.readsAll(caller)) return null
                columns(caller.organizationId, objectName, group, definition)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("ProjectReadScope: no se resolvieron los permisos o las definiciones de {}; no se admite ningún registro", objectName, e)
                return NOTHING
            }
        if (!ProjectReadCondition.complete(group, columns)) {
            log.warn("ProjectReadScope: a {} o a sus tablas de alcance les falta una columna; no se admite ningún registro", objectName)
            return NOTHING
        }
        val today = LocalDate.now(clock.withZone(LIMA))
        return RecordCriterion { _, bind ->
            ProjectReadCondition.condition(group, columns, caller.organizationId, caller.userId, today, bind) ?: ProjectReadCondition.NOTHING
        }
    }

    // las tablas y columnas del grupo, de la definición del objeto leído, de la de project_assignment y, en un hijo, de la de su
    // padre (wasichai las tiene en caché); si una relación es opcional lo dice el modelo, nunca una lista aparte
    private suspend fun columns(
        organization: UUID,
        objectName: String,
        group: ReadGroup,
        definition: ObjectDefinition
    ): ReadColumns {
        val assignments = assignmentColumns(load(organization, ProjectScope.ASSIGNMENT))
        return when (group) {
            ReadGroup.PROJECT -> ReadColumns(ProjectReadCondition.ID_COLUMN, assignments)
            ReadGroup.ASSIGNMENT -> ReadColumns(column(definition, PROJECT_FIELD), assignments, member = column(definition, MEMBER))
            ReadGroup.ROOT ->
                ReadColumns(column(definition, PROJECT_FIELD), assignments, optional = !catalog.isRequiredRelation(objectName, PROJECT_FIELD))
            ReadGroup.CHILD -> {
                val (field, parentName) = ProjectScope.CHILDREN.getValue(objectName)
                val parent = load(organization, parentName)
                ReadColumns(
                    column(definition, field),
                    assignments,
                    optional = !catalog.isRequiredRelation(objectName, field),
                    parent = column(parent, PROJECT_FIELD)?.let { ParentColumns(schemas.dataTable(parent.obj.physicalTable), it) }
                )
            }
            else -> ReadColumns(null, assignments)
        }
    }

    private fun assignmentColumns(definition: ObjectDefinition): AssignmentColumns? =
        AssignmentColumns(
            schemas.dataTable(definition.obj.physicalTable),
            column(definition, PROJECT_FIELD) ?: return null,
            column(definition, MEMBER) ?: return null,
            column(definition, "active") ?: return null,
            column(definition, "start_date") ?: return null,
            column(definition, "end_date") ?: return null
        )

    private fun column(
        definition: ObjectDefinition,
        field: String
    ): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }

    companion object {
        private const val PROJECT_FIELD = "project"
        private const val MEMBER = "member_user_id"
        private val NOTHING = RecordCriterion { _, _ -> ProjectReadCondition.NOTHING }
    }
}
```

En `src/main/kotlin/sgspe/scope/ProjectScopeGuard.kt`, reemplazar:

```kotlin
// la escritura acotada por asignación, opción A del checkpoint (security.md §9.2, RQ-SEC-006, CON-041): altas, cambios,
// bajas, transiciones y vínculos de los objetos de un proyecto solo para quien tiene una project_assignment vigente a ese
// proyecto. la lectura sigue siendo de todo el tenant hasta UPS-01. exentos: ADMIN (isAdmin), la plataforma y las
```

por:

```kotlin
// la escritura acotada por asignación (security.md §9.2, RQ-SEC-006, CON-041): altas, cambios, bajas, transiciones y
// vínculos de los objetos de un proyecto solo para quien tiene una project_assignment vigente a ese proyecto. desde 4.4 la
// lectura la acota ProjectReadScope (§9.7) y esta guarda es la segunda línea: frena a quien lee todo sin estar asignado
// (READ_ALL_PROJECTS) y el alta de raíces que la persona lee. exentos: ADMIN (isAdmin), la plataforma y las
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ProjectReadScopeTest` con 6 pruebas y `ArchitectureTest` con la regla nueva en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/scope/ProjectReadScope.kt src/main/kotlin/sgspe/scope/ProjectScopeGuard.kt \
  src/test/kotlin/sgspe/scope/ProjectReadScopeTest.kt src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -F - <<'MSG'
feat: ProjectReadScope acota la lectura por proyecto

Segundo RecordReadScope de sgspe: los maestros del tenant no se acotan ni consultan nada, quien
tiene READ_ALL_PROJECTS lee todo y el resto ve sus proyectos en todas las rutas de wasichai; falla
cerrado con advertencia. ArchitectureTest vigila quién arma la condición (ASM-106, ADR-0029).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** ninguna clase propia en esta tarea; `ProjectReadScopeApiTest` (Tarea 11) es su
prueba de integración. Hasta que la Tarea 13 entra, la suite de integración de la rama está en rojo por diseño.

---

### Tarea 8: Los documentos emitidos (`IssueDocumentFilter`)

**Files:**
- Modify: `src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt`
- Test: `src/test/kotlin/sgspe/scope/IssueDocumentFilterTest.kt`

**Interfaces:**
- Consumes: `Callers.userOrNull`, `Records.find(objectName, id)` (null si no existe o no se lee), y
  `wasichai.documents.DocumentService.byId(id: UUID): Document` (exige READ sobre el objeto del documento; `404` si no
  existe), con `Document.recordId: UUID` y `Document.snapshot.objectName: String` (wasichai-documents 0.6.0).
- Produces: `sealed interface DocumentRead { data class OfRecord(objectName, recordId); data class ById(documentId) }`,
  `interface DocumentReadAccess { suspend fun requireReadable(read: DocumentRead) }`, el bean `DocumentReadByScope`,
  `IssueDocumentFilter.readOf(request: ServerHttpRequest): DocumentRead?` y el constructor
  `IssueDocumentFilter(access: IssueAccess, reads: DocumentReadAccess, handler, codecs)`. `IssueAccessByScope` responde el
  `404` de wasichai (`NotFoundException("Record <id> does not exist")`) a un registro que no existe o que quien emite no lee.

Decisión 3 del plan: `DocumentService` se inyecta; no hace falta decorar la respuesta. Un error que lanza la decisión
(`NotFoundException`, `ForbiddenException`) sale por `handler.handleWasichai` como el problem+json de wasichai, igual que
hoy los errores de la emisión.

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/scope/IssueDocumentFilterTest.kt`, reemplazar:

```kotlin
import wasichai.core.common.GlobalExceptionHandler
```

por:

```kotlin
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.NotFoundException
```

reemplazar:

```kotlin
    private fun filter(access: IssueAccess) = IssueDocumentFilter(access, GlobalExceptionHandler("https://sgspe.test/problems"), ServerCodecConfigurer.create())
```

por:

```kotlin
    // la lectura de documentos: lanza `failure` si se da; anota lo que se le preguntó
    private class Reads(
        private val failure: Exception? = null
    ) : DocumentReadAccess {
        val asked = mutableListOf<DocumentRead>()

        override suspend fun requireReadable(read: DocumentRead) {
            asked += read
            if (failure != null) throw failure
        }
    }

    private fun filter(
        access: IssueAccess,
        reads: DocumentReadAccess = Reads()
    ) = IssueDocumentFilter(access, reads, GlobalExceptionHandler("https://sgspe.test/problems"), ServerCodecConfigurer.create())
```

reemplazar:

```kotlin
    private fun through(
        access: IssueAccess,
        request: MockServerHttpRequest
    ): Triple<MockServerWebExchange, String, Boolean> {
        val exchange = MockServerWebExchange.from(request)
        var passed = false
        filter(access)
```

por:

```kotlin
    private fun through(
        access: IssueAccess,
        request: MockServerHttpRequest,
        reads: DocumentReadAccess = Reads()
    ): Triple<MockServerWebExchange, String, Boolean> {
        val exchange = MockServerWebExchange.from(request)
        var passed = false
        filter(access, reads)
```

y, antes de la línea `    // ---- la decisión (P60 y P62): UPDATE, el alcance del registro leído como quien llama y, al final, EDIT_ANY ----`,
agregar:

```kotlin
    // ---- la lectura de documentos emitidos (UPS-28, ASM-108): sus dos rutas GET, y el 404 de un registro que no se lee ----

    @Test
    fun `las dos lecturas de documentos son rutas del filtro, y la emision y lo demas no`() {
        val record = UUID.fromString(id)
        assertEquals(
            DocumentRead.OfRecord("engagement", record),
            IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/objects/Engagement/records/$id/documents").build())
        )
        assertEquals(DocumentRead.ById(record), IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/documents/$id").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/documents/no-es-uuid").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/objects/engagement/records/$id").build()))
        assertNull(IssueDocumentFilter.readOf(MockServerHttpRequest.get("/api/objects/engagement/document-types").build()))
    }

    @Test
    fun `un documento cuyo registro no se lee es el 404 de wasichai y no llega al controlador`() {
        val reads = Reads(NotFoundException("Record $id does not exist"))
        for (path in listOf("/api/objects/engagement/records/$id/documents", "/api/documents/$id")) {
            val (exchange, body, passed) = through(Access(null), MockServerHttpRequest.get(path).build(), reads)
            assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode, body)
            assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(exchange.response.headers.contentType), exchange.response.headers.toString())
            assertFalse(passed, path)
        }
        val allowed = Reads()
        assertTrue(through(Access(null), MockServerHttpRequest.get("/api/documents/$id").build(), allowed).third)
        assertEquals(listOf<DocumentRead>(DocumentRead.ById(UUID.fromString(id))), allowed.asked)
    }

    @Test
    fun `emitir sobre un registro que no se lee tambien es 404`() {
        val (exchange, _, passed) =
            through(
                Access(null, NotFoundException("Record $id does not exist")),
                MockServerHttpRequest.post("/api/objects/engagement/records/$id/documents/meeting-minutes").build()
            )
        assertEquals(HttpStatus.NOT_FOUND, exchange.response.statusCode)
        assertFalse(passed)
    }

```

La prueba existente «sin negativa pasa, y fuera de la ruta ni se pregunta» sigue igual: un `GET .../documents` ahora sí es
ruta del filtro, pero el `Reads()` por defecto lo deja pasar y `refused.asked` (la emisión) sigue vacío.

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.IssueDocumentFilterTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'DocumentReadAccess'`).

- [ ] **Paso 3: Implementar**

En `src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt`, reemplazar:

```kotlin
import wasichai.core.metadata.MetadataService
```

por:

```kotlin
import wasichai.core.metadata.MetadataService
import wasichai.documents.DocumentService
```

reemplazar:

```kotlin
    // el registro como lo lee quien llama, o null si no lo puede leer o no existe
    private suspend fun stored(target: IssueTarget): Map<String, Any?>? =
        try {
            records.find(target.objectName, target.recordId)?.attributes
        } catch (_: ForbiddenException) {
            null
        }
```

por:

```kotlin
    // el registro como lo lee quien llama, o null sin READ sobre el objeto. uno que no existe o que está fuera de su alcance
    // (otro proyecto, una QCS confidencial ajena) es el 404 de wasichai, como el detalle del registro (ASM-108)
    private suspend fun stored(target: IssueTarget): Map<String, Any?>? =
        try {
            records.find(target.objectName, target.recordId)?.attributes ?: throw NotFoundException("Record ${target.recordId} does not exist")
        } catch (_: ForbiddenException) {
            null
        }
```

en el `companion object` de `IssueAccessByScope`, reemplazar:

```kotlin
        // pura, para las pruebas unitarias: la primera negativa, o null. `scope` lee el registro (null si no se puede) y solo
```

por:

```kotlin
        // pura, para las pruebas unitarias: la primera negativa, o null. `scope` lee el registro (null sin READ; si no existe o
        // no se lee lanza el 404) y solo
```

Antes de la línea `// wasichai-documents 0.6.0 emite con POST /api/objects/{objeto}/records/{id}/documents/{tipo} y solo pide READ sobre el objeto`,
agregar:

```kotlin
// la lectura de documentos emitidos que el filtro vigila: los de un registro (GET /api/objects/{objeto}/records/{id}/documents)
// o uno por su id (GET /api/documents/{id})
sealed interface DocumentRead {
    data class OfRecord(
        val objectName: String,
        val recordId: UUID
    ) : DocumentRead

    data class ById(
        val documentId: UUID
    ) : DocumentRead
}

// si quien llama lee el registro de un documento: lanza el 404 de wasichai si no lo lee (DocumentReadByScope; las pruebas del
// filtro dan la suya)
interface DocumentReadAccess {
    suspend fun requireReadable(read: DocumentRead)
}

// wasichai-documents 0.6.0 solo pide READ sobre el objeto para leer documentos emitidos, y la respuesta lleva el snapshot
// congelado del registro (UPS-28): sin esto, el alcance de lectura (ProjectReadScope, ConfidentialAttentionScope) se saltaría
// leyendo el documento. aquí el registro se lee como quien llama con Records: si no lo lee, 404 como el detalle del registro.
// el documento por id se resuelve con DocumentService.byId, que ya exige READ sobre su objeto (403) y responde 404 si no
// existe. sin usuario no decide: wasichai responde su 401. el ADMIN no tiene alcance que aplicar
@Component
class DocumentReadByScope(
    private val callers: Callers,
    private val records: Records,
    private val documents: DocumentService
) : DocumentReadAccess {
    override suspend fun requireReadable(read: DocumentRead) {
        val user = callers.userOrNull() ?: return
        if (user.isAdmin) return
        val (objectName, recordId) =
            when (read) {
                is DocumentRead.OfRecord -> read.objectName to read.recordId
                is DocumentRead.ById -> documents.byId(read.documentId).let { it.snapshot.objectName to it.recordId }
            }
        records.find(objectName, recordId) ?: throw NotFoundException("Record $recordId does not exist")
    }
}

```

reemplazar:

```kotlin
class IssueDocumentFilter(
    private val access: IssueAccess,
    private val handler: GlobalExceptionHandler,
```

por:

```kotlin
class IssueDocumentFilter(
    private val access: IssueAccess,
    private val reads: DocumentReadAccess,
    private val handler: GlobalExceptionHandler,
```

reemplazar:

```kotlin
        val target = targetOf(exchange.request) ?: return chain.filter(exchange)
        // Optional: una corrutina que devuelve null completaría vacía y la petición no seguiría ni se respondería. un error al
        // decidir (la base, por ejemplo) no es un pase: sale como el problem+json del manejador de wasichai, no como el 500 de
        // Boot. solo la decisión se envuelve; lo que falle después en la cadena lo maneja el controlador
        val decision =
            mono { Optional.ofNullable(access.refusal(target)?.let { handler.handleWasichai(it.toCodedException()) }) }
                .onErrorResume(Exception::class.java) { e -> Mono.just(Optional.of(problemOf(e))) }
```

por:

```kotlin
        val target = targetOf(exchange.request)
        val read = if (target == null) readOf(exchange.request) else null
        if (target == null && read == null) return chain.filter(exchange)
        // Optional: una corrutina que devuelve null completaría vacía y la petición no seguiría ni se respondería. un error al
        // decidir (la base, por ejemplo) no es un pase: sale como el problem+json del manejador de wasichai, no como el 500 de
        // Boot; el 404 de un registro que no se lee, igual. solo la decisión se envuelve; lo que falle después en la cadena lo
        // maneja el controlador
        val decision =
            mono {
                if (target != null) {
                    Optional.ofNullable(access.refusal(target)?.let { handler.handleWasichai(it.toCodedException()) })
                } else {
                    reads.requireReadable(read!!)
                    Optional.empty<ProblemDetail>()
                }
            }.onErrorResume(Exception::class.java) { e -> Mono.just(Optional.of(problemOf(e))) }
```

reemplazar:

```kotlin
        private val ROUTE = PathPatternParser.defaultInstance.parse("/api/objects/{object}/records/{id}/documents/{type}")
```

por:

```kotlin
        private val ROUTE = PathPatternParser.defaultInstance.parse("/api/objects/{object}/records/{id}/documents/{type}")

        // las dos lecturas de DocumentController: los documentos de un registro y uno por su id
        private val RECORD_DOCUMENTS = PathPatternParser.defaultInstance.parse("/api/objects/{object}/records/{id}/documents")
        private val DOCUMENT = PathPatternParser.defaultInstance.parse("/api/documents/{id}")
```

y, al final del `companion object` del filtro, después de `targetOf`, reemplazar:

```kotlin
            return IssueTarget(objectName, recordId)
        }
    }
}
```

por:

```kotlin
            return IssueTarget(objectName, recordId)
        }

        // la lectura de documentos de la petición, o null si no es una. un id que no es UUID no llega a leer: el controlador
        // de wasichai responde su 400
        fun readOf(request: ServerHttpRequest): DocumentRead? {
            if (request.method != HttpMethod.GET) return null
            val path = request.path.pathWithinApplication()
            RECORD_DOCUMENTS.matchAndExtract(path)?.let { match ->
                val objectName = match.uriVariables["object"]?.trim()?.lowercase() ?: return null
                val recordId = runCatching { UUID.fromString(match.uriVariables["id"]) }.getOrNull() ?: return null
                return DocumentRead.OfRecord(objectName, recordId)
            }
            val match = DOCUMENT.matchAndExtract(path) ?: return null
            return runCatching { UUID.fromString(match.uriVariables["id"]) }.getOrNull()?.let { DocumentRead.ById(it) }
        }
    }
}
```

En el comentario sobre `@Component class IssueAccessByScope`, reemplazar:

```kotlin
// necesitan); y, como en EditAnyGuard, la regla de OwnRecords: sin EDIT_ANY, solo en un registro propio. un registro que quien
// llama no puede leer (o que no existe) falla cerrado con OBJECT_NOT_READABLE, y uno sin proyecto resoluble con NOT_ASSIGNED,
// como en la guarda. sin usuario o con un objeto que no existe no decide: wasichai contesta su 401 o su 404. nunca compara
```

por:

```kotlin
// necesitan); y, como en EditAnyGuard, la regla de OwnRecords: sin EDIT_ANY, solo en un registro propio. sin READ sobre el
// objeto falla cerrado con OBJECT_NOT_READABLE; un registro que no existe o que quien llama no lee (otro proyecto, una QCS
// confidencial ajena) es el 404 de wasichai (ASM-108); y uno sin proyecto resoluble, NOT_ASSIGNED, como en la guarda. sin
// usuario o con un objeto que no existe no decide: wasichai contesta su 401 o su 404. nunca compara
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `IssueDocumentFilterTest` con 12 pruebas en verde (las 9 de antes y 3 nuevas).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/scope/IssueDocumentFilter.kt src/test/kotlin/sgspe/scope/IssueDocumentFilterTest.kt
git commit -F - <<'MSG'
feat: los documentos emitidos respetan el alcance de lectura

IssueDocumentFilter cubre GET .../records/{id}/documents y GET /api/documents/{id}: si quien
llama no lee el registro del documento, 404 como su detalle (UPS-28). La emisión sobre un registro
que no existe o no se lee también responde 404; OBJECT_NOT_READABLE queda para quien no tiene READ.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.scope.IssueDocumentApiTest` (su caso de otro proyecto cambia en la
Tarea 13) y el caso de documentos de `ProjectReadScopeApiTest` (Tarea 11).

---

### Tarea 9: `ProjectReach` en tableros, reporte y exportaciones

**Files:**
- Modify: `src/main/kotlin/sgspe/privacy/ExportProjects.kt` (reescrito), `src/main/kotlin/sgspe/privacy/ExportLog.kt`,
  `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, `src/main/kotlin/sgspe/attention/AttentionReport.kt`,
  `src/main/kotlin/sgspe/scope/Assignments.kt` (sin `projectsOf`)
- Test: `src/test/kotlin/sgspe/privacy/ExportProjectsTest.kt` (reescrito)

**Interfaces:**
- Consumes: `ProjectReach.mine`, `assigned`, `reaches` (Tarea 5).
- Produces: `ExportProjects(reach: ProjectReach)` con las mismas `resolve(user, requested): List<UUID>?` y
  `requireAssigned(user, requested)`; `ExportLog(records, reach, clock)`, que deja `project` vacío si quien exporta no es
  `ADMIN` ni está asignado; `Dashboards` y `AttentionReport` con `ProjectReach` en lugar de `Assignments`.
  `Assignments.projectsOf` desaparece (sin otros usos: `grep -rn projectsOf src` vacío).

Elección 6 de la especificación: una sola regla de «mis proyectos». Sin asignaciones y sin `READ_ALL_PROJECTS`, «mis
proyectos» es la lista vacía: el tablero y el reporte responden conjuntos vacíos, y la exportación, un archivo sin filas
(antes, `Dashboards` y `AttentionReport` caían a «todos los que lee», que con el alcance ya serían ninguno).

- [ ] **Paso 1: Escribir la prueba que falla**

Reemplazar el contenido de `src/test/kotlin/sgspe/privacy/ExportProjectsTest.kt` por:

```kotlin
package sgspe.privacy

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Records
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordService
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.identity.CurrentUser
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.time.Clock
import java.util.UUID

// ExportProjects sobre ProjectReach (api.md §4.13, ASM-106): los pedidos, o «mis proyectos»; con un solo proyecto pedido, quien
// no lo alcanza recibe NOT_ASSIGNED, y quien lee todos los proyectos (READ_ALL_PROJECTS) no necesita la asignación. ProjectReach
// suspende de verdad, como el de la base: una llamada suspendida dentro de un lambda en línea en posición de cola se compilaba
// como llamada de cola y devolvía Unit a quien llamaba (CI del plan 3.2)
class ExportProjectsTest {
    private val project = UUID.randomUUID()
    private val user = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "persona@sgspe.test", listOf("STAKEHOLDER_MANAGER"), null)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(): ObjectProvider<T> = mock(ObjectProvider::class.java) { null } as ObjectProvider<T>

    private val records =
        Records(
            provider<RecordService>(),
            provider<RelatedRecordService>(),
            provider<WorkflowService>(),
            mock(MetadataService::class.java),
            mock(Callers::class.java),
            mock(ModelCatalog::class.java),
            mock(DerivedVersions::class.java)
        )

    private fun exports(
        readsAll: Boolean,
        assigned: List<UUID>
    ) = ExportProjects(
        object : ProjectReach(records, Permissions(mock(CurrentUser::class.java), mock(MetadataService::class.java)), Clock.systemUTC()) {
            override suspend fun readsAll(user: AuthenticatedUser): Boolean {
                delay(1)
                return readsAll
            }

            override suspend fun assigned(user: AuthenticatedUser): List<UUID> {
                delay(1)
                return assigned
            }
        }
    )

    @Test
    fun `un solo proyecto pedido por quien lo tiene asignado devuelve los pedidos aunque la lectura suspenda`() {
        assertEquals(listOf(project), runBlocking { exports(false, listOf(project)).resolve(user, listOf(project)) })
    }

    @Test
    fun `sin proyectos pedidos devuelve mis proyectos, y todos para quien lee todo`() {
        assertEquals(listOf(project), runBlocking { exports(false, listOf(project)).resolve(user, emptyList()) })
        assertNull(runBlocking { exports(true, emptyList()).resolve(user, emptyList()) })
    }

    @Test
    fun `un solo proyecto ajeno es NOT_ASSIGNED salvo para quien lee todos los proyectos`() {
        val refused = assertThrows<WasichaiException> { runBlocking { exports(false, emptyList()).resolve(user, listOf(project)) } }
        assertEquals(403, refused.status.value())
        assertEquals(listOf(project), runBlocking { exports(true, emptyList()).resolve(user, listOf(project)) })
        // dos o más proyectos no piden asignación: el alcance de lectura acota las filas
        val two = listOf(project, UUID.randomUUID())
        assertEquals(two, runBlocking { exports(false, emptyList()).resolve(user, two) })
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.privacy.ExportProjectsTest'
```

Esperado: FAIL en la compilación (el constructor `ExportProjects(ProjectReach)` no existe).

- [ ] **Paso 3: Implementar**

Reemplazar el contenido de `src/main/kotlin/sgspe/privacy/ExportProjects.kt` por:

```kotlin
package sgspe.privacy

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ProjectReach
import sgspe.common.toCodedException
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// los proyectos de una exportación (api.md §4.13), la misma regla para todos los tipos: los pedidos o, si no se pide ninguno,
// «mis proyectos» de ProjectReach (las asignaciones vigentes de quien exporta, hoy en Lima); quien lee todos los proyectos (el
// ADMIN y quien tiene READ_ALL_PROJECTS), todos (null). con un solo proyecto pedido, quien no lo alcanza (no lo lee todo ni está
// asignado a él) recibe 403 NOT_ASSIGNED sobre filters.project. las filas, además, las acota el alcance de lectura
// (ProjectReadScope). privacy solo depende de common (architecture.md §5.3). la comparten las cuatro exportaciones
@Component
class ExportProjects(
    private val reach: ProjectReach
) {
    suspend fun resolve(
        user: AuthenticatedUser,
        requested: List<UUID>
    ): List<UUID>? {
        if (requested.isNotEmpty()) {
            requireAssigned(user, requested)
            return requested
        }
        return reach.mine(user)
    }

    // se comprueba antes de leer nada; ProjectScopeGuard lo vuelve a exigir si la fila de export_log lleva el proyecto
    suspend fun requireAssigned(
        user: AuthenticatedUser,
        requested: List<UUID>
    ) {
        val single = requested.singleOrNull() ?: return
        if (reach.reaches(user, single)) return
        throw Finding(
            "NOT_ASSIGNED",
            "Exportar un proyecto exige una asignación vigente a él",
            "filters.project",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()
    }
}
```

En `src/main/kotlin/sgspe/privacy/ExportLog.kt`, reemplazar:

```kotlin
import sgspe.common.Records
```

por:

```kotlin
import sgspe.common.ProjectReach
import sgspe.common.Records
```

reemplazar:

```kotlin
// fila que acaba de escribir. export_kind distingue exportación, emisión y descarga (estas dos, desde F8)
@Component
class ExportLog(
    private val records: Records,
    private val clock: Clock
) {
```

por:

```kotlin
// fila que acaba de escribir. export_kind distingue exportación, emisión y descarga (estas dos, desde F8). la fila lleva el
// proyecto solo si quien exporta es ADMIN o está asignado a él: quien lo lee todo sin estar asignado (READ_ALL_PROJECTS) no
// escribe en ese proyecto (ProjectScopeGuard respondería 403), y el proyecto queda en filters_description
@Component
class ExportLog(
    private val records: Records,
    private val reach: ProjectReach,
    private val clock: Clock
) {
```

reemplazar:

```kotlin
    ) {
        records.create(
```

por:

```kotlin
    ) {
        val logged = if (project == null || user.isAdmin || project in reach.assigned(user)) project else null
        records.create(
```

y reemplazar `            ) + listOfNotNull(project?.let { "project" to it.toString() }),` por
`            ) + listOfNotNull(logged?.let { "project" to it.toString() }),`.

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar `import sgspe.common.Permissions` por
`import sgspe.common.Permissions` + salto de línea + `import sgspe.common.ProjectReach`, borrar la línea
`import sgspe.scope.Assignments`, reemplazar `    private val assignments: Assignments,` por
`    private val reach: ProjectReach,`, reemplazar:

```kotlin
// tablero attention, cada KPI-GRV y KPI-ENG-05 llevan confidentialHidden si quien mira no ve las QCS confidenciales
@Service
```

por:

```kotlin
// tablero attention, cada KPI-GRV y KPI-ENG-05 llevan confidentialHidden si quien mira no ve las QCS confidenciales. desde 4.4,
// los proyectos por defecto son «mis proyectos» de ProjectReach (todos los que lee quien lee todo, ninguno si no tiene
// asignaciones) y un proyecto ajeno pedido da conjuntos vacíos: el alcance de lectura acota las filas (dashboards.md §3.1)
@Service
```

y reemplazar:

```kotlin
            .parse(params, allowed, catalog, today) {
                if (user.isAdmin) allProjects() else assigned(user.userId).ifEmpty { allProjects() }
            }.resolve()
    }

    // las asignaciones vigentes de quien mira (Assignments.projectsOf, con Validity.inForce); sin READ sobre
    // project_assignment no tiene asignaciones que leer
    private suspend fun assigned(userId: UUID): List<UUID> =
        try {
            assignments.projectsOf(userId)
        } catch (_: ForbiddenException) {
            emptyList()
        }
```

por:

```kotlin
            .parse(params, allowed, catalog, today) { reach.mine(user) ?: allProjects() }
            .resolve()
    }
```

En `src/main/kotlin/sgspe/attention/AttentionReport.kt`, reemplazar `import sgspe.common.Permissions` por
`import sgspe.common.Permissions` + salto de línea + `import sgspe.common.ProjectReach`, borrar la línea
`import sgspe.scope.Assignments`, reemplazar `    private val assignments: Assignments,` por
`    private val reach: ProjectReach,`, reemplazar:

```kotlin
// que no ve no cuenta, y lo dice confidentialHidden, sin el número de excluidas (security.md §9.6). sin `project`, «mis
// proyectos»: las asignaciones vigentes o, para el ADMIN o quien no tiene ninguna, todos los proyectos que lee. la lectura es
// acotada por tipo (sgspe.exports.max-rows): pasarse es 400 TOO_MANY_RECORDS
```

por:

```kotlin
// que no ve no cuenta, y lo dice confidentialHidden, sin el número de excluidas (security.md §9.6). sin `project`, «mis
// proyectos» de ProjectReach: todos los que lee quien lee todo (ADMIN y READ_ALL_PROJECTS) y, si no, las asignaciones vigentes
// (ninguna, sin asignaciones). la lectura es acotada por tipo (sgspe.exports.max-rows): pasarse es 400 TOO_MANY_RECORDS
```

reemplazar:

```kotlin
        val projects = filters.projects.ifEmpty { if (user.isAdmin) allProjects() else assigned(user.userId).ifEmpty { allProjects() } }
```

por (sin lambda en línea: una llamada suspendida en posición de cola de un lambda en línea es la trampa que recuerda
`ExportProjectsTest`):

```kotlin
        val projects = if (filters.projects.isNotEmpty()) filters.projects else reach.mine(user) ?: allProjects()
```

y borrar:

```kotlin
    private suspend fun assigned(userId: UUID): List<UUID> =
        try {
            assignments.projectsOf(userId)
        } catch (_: ForbiddenException) {
            emptyList()
        }

```

En `src/main/kotlin/sgspe/scope/Assignments.kt`, borrar:

```kotlin
    // los proyectos con una asignación vigente del usuario: el filtro por defecto de los tableros (RF-PRJ-005)
    suspend fun projectsOf(userId: UUID): List<UUID> =
        records
            .all(OBJECT, filters = mapOf("member_user_id" to userId.toString()))
            .mapNotNull { Assignment.of(it.id, it.attributes) }
            .filter { it.memberUserId == userId && it.inForce(today()) }
            .map { it.project }
            .distinct()

```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
grep -rn "projectsOf\|import sgspe.scope.Assignments" src/main/kotlin/sgspe/dashboard src/main/kotlin/sgspe/attention src/main/kotlin/sgspe/scope/Assignments.kt || echo limpio
```

Esperado: `BUILD SUCCESSFUL`; `ExportProjectsTest` con 3 pruebas en verde; `limpio`. Si ktlint o el compilador dicen que
`ForbiddenException` o `UUID` quedaron sin uso en `Dashboards.kt` o `AttentionReport.kt`, no es así: `allProjects()` y
`rows()` los siguen usando.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/privacy/ExportProjects.kt src/main/kotlin/sgspe/privacy/ExportLog.kt \
  src/main/kotlin/sgspe/dashboard/Dashboards.kt src/main/kotlin/sgspe/attention/AttentionReport.kt \
  src/main/kotlin/sgspe/scope/Assignments.kt src/test/kotlin/sgspe/privacy/ExportProjectsTest.kt
git commit -F - <<'MSG'
feat: tableros, reporte y exportaciones con ProjectReach

Los proyectos por defecto son mis proyectos de ProjectReach (todos para ADMIN y
READ_ALL_PROJECTS, ninguno sin asignaciones); un proyecto pedido no exige asignación a quien lee
todo, y export_log deja el proyecto vacío si quien exporta no está asignado. Assignments.projectsOf
se retira.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.dashboard.DashboardsApiTest`, `sgspe.attention.AttentionReportApiTest`,
`sgspe.privacy.*ApiTest` (sus casos cambian en la Tarea 13).

---

### Tarea 10: El detalle de `HAS_DEPENDENTS` no nombra al dependiente

**Files:**
- Modify: `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`
- Test: `src/test/kotlin/sgspe/common/DependentsDeleteGuardTest.kt`

**Interfaces:**
- Consumes: nada nuevo.
- Produces: `DependentsDeleteGuard.message(objectName: String): String`. El `field` del hallazgo sigue siendo
  `<objeto>.<campo>` del primer dependiente (decisión 7 del plan).

- [ ] **Paso 1: Escribir la prueba que falla**

En `src/test/kotlin/sgspe/common/DependentsDeleteGuardTest.kt`, reemplazar
`import org.junit.jupiter.api.Assertions.assertEquals` por esa línea más `import org.junit.jupiter.api.Assertions.assertFalse`
en la línea siguiente, y agregar antes de la última `}` de la clase:

```kotlin

    // ASM-108: el detalle no nombra el objeto dependiente, su proyecto ni cuántos hay
    @Test
    fun `el detalle del 409 no nombra al dependiente`() {
        val message = DependentsDeleteGuard.message("engagement_plan")
        assertEquals("No se puede borrar engagement_plan: otros registros todavía dependen de él", message)
        assertFalse(message.contains("planned_activity"))
    }
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.DependentsDeleteGuardTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'message'`).

- [ ] **Paso 3: Implementar**

En `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`, reemplazar:

```kotlin
                message = "No se puede borrar ${change.objectName}: ${first.first} todavía depende de él por ${first.second}",
```

por:

```kotlin
                message = message(change.objectName),
```

y reemplazar:

```kotlin
    companion object {
        // objeto que se borra -> (objeto, campo) que lo referencian y que se vigilan aunque la relación sea opcional
        val PRESERVED: Map<String, List<Pair<String, String>>> = mapOf("early_warning_rule" to listOf("early_warning" to "early_warning_rule"))
    }
```

por:

```kotlin
    companion object {
        // objeto que se borra -> (objeto, campo) que lo referencian y que se vigilan aunque la relación sea opcional
        val PRESERVED: Map<String, List<Pair<String, String>>> = mapOf("early_warning_rule" to listOf("early_warning" to "early_warning_rule"))

        // el detalle del 409: no nombra el proyecto, el objeto dependiente ni cuántos hay, porque el dependiente puede ser de un
        // proyecto que quien borra no lee (ASM-108, oráculo aceptado de sí o no). el campo sigue siendo <objeto>.<campo> del
        // primer dependiente (api.md §3.3), que el portal usa para elegir su texto
        fun message(objectName: String): String = "No se puede borrar $objectName: otros registros todavía dependen de él"
    }
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.common.DependentsDeleteGuardTest'
grep -rn "todavía depende de él por" src || echo limpio
```

Esperado: `BUILD SUCCESSFUL` con 4 pruebas en verde; `limpio` (ninguna prueba de integración compara el `detail`).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt src/test/kotlin/sgspe/common/DependentsDeleteGuardTest.kt
git commit -F - <<'MSG'
feat: el 409 HAS_DEPENDENTS no nombra al dependiente en su detalle

El dependiente puede ser de un proyecto que quien borra no lee: el detalle dice solo que otros
registros dependen del que se borra (ASM-108). El campo sigue siendo <objeto>.<campo>.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 11: `ProjectReadScopeApiTest`, las rutas con dos proyectos

**Files:**
- Create: `src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt`

**Interfaces:**
- Consumes: todo lo anterior; de las bases de prueba, `F4ApiTest` (con `F3DomainApiTest`, `DomainApiTest` y `F2ApiTest`):
  `createWith`, `point`, `member`, `assign`, `assignWith`, `userWithRole`, `stakeholder`, `organization`, `engagement`,
  `evidence`, `grievance`, `link`, `transition`, `state`, `code`, `problemFields`, `me`; el endpoint
  `POST /api/service-accounts` y `POST /api/auth/token` (como `EarlyWarningWorkflowApiTest`); `POST /api/roles` y
  `PUT /api/roles/{name}/permissions` para dos roles de prueba que `roles.json` no tiene.
- Produces: la prueba de integración del plan (§3.13 de la especificación) y las garantías que consume 4.4b
  (`READ_ALL_PROJECTS` en `/api/auth/me/permissions` y la lectura de la asignación propia vencida).

Cubre lo que pide §3.13 salvo `ConfidentialRelease`, que ya prueba `StakeholderRequestWorkflowApiTest` (decisión 10). Los
dos roles de prueba (`PRUEBA_…`) se crean por la API y quedan en el tenant de prueba: `apply_roles.py` avisa de un rol que
`roles.json` no declara y no lo borra, y ninguna otra clase los nombra.

- [ ] **Paso 1: Escribir la prueba**

Crear `src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// el alcance de lectura por proyecto en las rutas de wasichai y de sgspe (ASM-106, ASM-108, security.md §9.7 y §14): con dos
// proyectos, «Proyecto Ejemplo A» y «Proyecto Ejemplo B», quien está asignado solo a A ve A y no B en listas, totales,
// detalle, relacionados, historial, auditoría, transiciones, features, archivos, documentos, indicadores, exportaciones y
// capas; no da de alta ni vincula hacia B; una asignación vencida deja de dar alcance pero se sigue leyendo; AUDITOR,
// PROJECT_ADMIN y ADMIN leen los dos; una cuenta de servicio asignada a A, solo A; y una QCS confidencial de A no la ve un gestor
// de quejas de B aunque tenga READ_CONFIDENTIAL. ConfidentialRelease dentro de A lo prueba StakeholderRequestWorkflowApiTest
class ProjectReadScopeApiTest : F4ApiTest() {
    private val a by lazy {
        createWith(
            "project",
            mapOf("code" to uniqueName("PRY-A-"), "name" to "Proyecto Ejemplo A", "working_crs" to "EPSG 32717"),
            mapOf("location" to point(-78.2, -7.9))
        )
    }
    private val b by lazy {
        createWith(
            "project",
            mapOf("code" to uniqueName("PRY-B-"), "name" to "Proyecto Ejemplo B", "working_crs" to "EPSG 32717"),
            mapOf("location" to point(-78.3, -7.8))
        )
    }
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER", a) }

    private fun status(
        method: String,
        path: String,
        account: Account,
        body: Any? = null
    ): HttpStatus = exchange(method, path, body, account.token).status

    private fun ids(node: JsonNode): Set<String> = node["content"].values().map { it["id"].asString() }.toSet()

    private fun listed(
        objectName: String,
        account: Account,
        query: String = ""
    ): JsonNode = tree(send("GET", "/api/objects/$objectName/records?size=200$query", null, HttpStatus.OK, account.token))

    // una cuenta de servicio con `role`, asignada a `project` (como EarlyWarningWorkflowApiTest)
    private fun serviceAccount(
        role: String,
        project: String
    ): Account {
        val created = tree(send("POST", "/api/service-accounts", mapOf("name" to uniqueName("cuenta-"), "roles" to listOf(role)), HttpStatus.CREATED))
        val issued =
            tree(
                send(
                    "POST",
                    "/api/auth/token",
                    mapOf("clientId" to created["clientId"].asString(), "clientSecret" to created["clientSecret"].asString()),
                    HttpStatus.OK,
                    token = null
                )
            )
        val bearer = "Bearer ${issued["token"].asString()}"
        val me = me(bearer)
        val account = Account(bearer, me["email"].asString(), me["userId"].asString())
        assign(account, project, role)
        return account
    }

    // un rol de prueba creado por la API (no está en roles.json): sus permisos (objeto o null, acción)
    private fun roleWith(vararg grants: Pair<String?, String>): String {
        val name = "PRUEBA_" + uniqueName("").uppercase().filter { it.isLetterOrDigit() }
        send("POST", "/api/roles", mapOf("name" to name, "label" to "Rol de prueba"), HttpStatus.CREATED)
        send(
            "PUT",
            "/api/roles/$name/permissions",
            mapOf(
                "permissions" to
                    grants.map { (o, action) ->
                        mapOf("objectName" to o, "action" to action)
                    }
            ),
            HttpStatus.OK
        )
        return name
    }

    @Test
    fun `quien esta asignado a A no ve B en listas, totales, detalle, historial, transiciones ni relacionados`() {
        val organization = organization("Asociación Ejemplo")
        val mine = stakeholder(a, mapOf("organization" to organization))
        val theirs = stakeholder(b, mapOf("organization" to organization))
        val list = listed("stakeholder", officer)
        assertEquals(setOf(mine), ids(list) intersect setOf(mine, theirs))
        assertEquals(1, listed("stakeholder", officer, "&project=$a")["totalElements"].asInt())
        assertEquals(0, listed("stakeholder", officer, "&project=$b")["totalElements"].asInt())
        assertEquals(setOf(a), ids(listed("project", officer)) intersect setOf(a, b))
        for (path in listOf(
            "/api/objects/stakeholder/records/$theirs",
            "/api/objects/stakeholder/records/$theirs/history",
            "/api/objects/stakeholder/records/$theirs/transitions",
            "/api/objects/project/records/$b",
            "/api/objects/project/records/$b/related/stakeholder_project"
        )) {
            assertEquals(HttpStatus.NOT_FOUND, status("GET", path, officer), path)
        }
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/stakeholder/records/$mine", officer))
        // desde un maestro del tenant, la organización, el otro lado se omite
        val related =
            tree(send("GET", "/api/objects/organization/records/$organization/related/stakeholder_organization?size=200", null, HttpStatus.OK, officer.token))
        assertEquals(setOf(mine), ids(related))
        // cambiar, borrar o transicionar un registro de B: 404, y nada cambia
        assertEquals(
            HttpStatus.NOT_FOUND,
            status(
                "PUT",
                "/api/objects/stakeholder/records/$theirs",
                officer,
                mapOf("attributes" to mapOf("profile_summary" to "x"))
            )
        )
        assertEquals(HttpStatus.NOT_FOUND, transition("stakeholder", theirs, "profile", officer.token, "perfil").status)
        assertEquals("identified", state("stakeholder", theirs))
    }

    @Test
    fun `la auditoria de todo el tenant solo trae lo de los proyectos de quien la lee`() {
        val mine = stakeholder(a)
        val theirs = stakeholder(b)
        // un rol con READ de todo el tenant y sin READ_ALL_PROJECTS: /api/audit pide READ de todo el tenant
        val reader = userWithRole(roleWith(null to "READ")).also { assign(it, a, "READ_ONLY") }
        for ((id, expected) in listOf(mine to true, theirs to false)) {
            val audit = tree(send("GET", "/api/audit?recordId=$id&limit=500", null, HttpStatus.OK, reader.token))
            assertEquals(expected, audit.values().any { it["recordId"]?.asString() == id }, id)
        }
    }

    @Test
    fun `el feature GIS, el archivo de una evidencia y los documentos de B responden 404`() {
        assertEquals(HttpStatus.OK, status("GET", "/api/gis/objects/project/features/$a", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/gis/objects/project/features/$b", officer))
        val file = evidence(b)
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/evidence/records/$file/files/file", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/sgspe/evidence/$file/content", officer))
        val meeting = engagement(b)
        val issued = tree(send("POST", "/api/objects/engagement/records/$meeting/documents/meeting-minutes", null, HttpStatus.CREATED))
        val document = issued["id"].asString()
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/engagement/records/$meeting/documents", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/documents/$document", officer))
        assertEquals(HttpStatus.NOT_FOUND, status("POST", "/api/objects/engagement/records/$meeting/documents/meeting-minutes", officer))
        // quien es de B sí los lee
        val other = member("COMMUNITY_RELATIONS_OFFICER", b)
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/engagement/records/$meeting/documents", other))
        assertEquals(HttpStatus.OK, status("GET", "/api/documents/$document", other))
    }

    @Test
    fun `el alta con project B y el vinculo hacia un registro de B no pasan`() {
        // RELATION: el 400 de wasichai sobre el campo, como un registro que no existe (D30)
        val created =
            exchange(
                "POST",
                "/api/objects/engagement/records",
                mapOf("attributes" to mapOf("project" to b, "engagement_type" to "REUNION", "title" to "Reunión")),
                officer.token
            )
        assertEquals(HttpStatus.BAD_REQUEST, created.status, created.body)
        assertEquals(listOf("project"), problemFields(created.body))
        assertEquals("no record with this id", tree(created.body)["errors"][0]["message"].asString())
        // M2M: POST .../related responde 404 si un extremo no se lee (RelatedRecordService)
        val own = engagement(a, token = officer.token)
        val foreign = stakeholder(b)
        assertEquals(HttpStatus.NOT_FOUND, link("engagement", own, "engagement_stakeholder", foreign, officer.token).status)
    }

    @Test
    fun `una asignacion vencida deja de dar alcance pero la persona la sigue leyendo`() {
        // un compañero con asignación vigente a A
        assertTrue(officer.id.isNotEmpty())
        val expired = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val assignment = assignWith(expired, a, "COMMUNITY_RELATIONS_OFFICER", mapOf("start_date" to "2020-01-01", "end_date" to "2020-12-31"))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/project/records/$a", expired))
        assertEquals(setOf(assignment), ids(listed("project_assignment", expired, "&member_user_id=${expired.id}")))
        // y ya no ve las asignaciones del equipo de A: solo la suya
        assertEquals(setOf(assignment), ids(listed("project_assignment", expired, "&project=$a")))
    }

    @Test
    fun `AUDITOR, PROJECT_ADMIN y ADMIN leen los dos proyectos sin asignacion, y me permissions lo dice`() {
        val theirs = stakeholder(b)
        val auditor = userWithRole("AUDITOR")
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/stakeholder/records/$theirs", auditor))
        assertEquals(setOf(a, b), ids(listed("project", auditor)) intersect setOf(a, b))
        assertEquals(setOf(a, b), ids(listed("project", userWithRole("PROJECT_ADMIN"))) intersect setOf(a, b))
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/stakeholder/records/$theirs").status)
        // GET /api/auth/me/permissions lista la acción declarada en objects.project (WAS-ADR-0042): el portal la usa (4.4b)
        for ((role, expected) in listOf("AUDITOR" to true, "PROJECT_ADMIN" to true, "ANALYST" to false, "SOCIAL_MANAGER" to false)) {
            val permissions = tree(send("GET", "/api/auth/me/permissions", null, HttpStatus.OK, userWithRole(role).token))
            val actions = permissions["objects"]["project"].values().map { it.asString() }
            assertEquals(expected, "READ_ALL_PROJECTS" in actions, "$role: $actions")
        }
    }

    @Test
    fun `PROJECT_ADMIN lee un proyecto ajeno pero no escribe en el sin asignacion`() {
        val admin = userWithRole("PROJECT_ADMIN")
        val party = mapOf("project" to b, "organization" to organization(), "party_role" to "CLIENTE")
        val refused = exchange("POST", "/api/objects/project_party/records", mapOf("attributes" to party), admin.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("NOT_ASSIGNED", code(refused))
    }

    @Test
    fun `una cuenta de servicio asignada a A solo lee A`() {
        val mine = stakeholder(a)
        val theirs = stakeholder(b)
        val service = serviceAccount("STAKEHOLDER_MANAGER", a)
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/stakeholder/records/$mine", service))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/stakeholder/records/$theirs", service))
    }

    @Test
    fun `una QCS confidencial de A no la ve un gestor de quejas de B aunque tenga READ_CONFIDENTIAL`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER", a)
        val confidential = grievance(a, receiver, mapOf("confidential" to true))
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/grievance/records/$confidential", member("GRIEVANCE_MANAGER", a)))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/grievance/records/$confidential", member("GRIEVANCE_MANAGER", b)))
    }

    @Test
    fun `el indicador, la exportacion y la capa de B no traen datos para quien es de A`() {
        stakeholder(a)
        stakeholder(b)
        val analyst = member("ANALYST", a)

        fun value(project: String): Int =
            tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-STK-01?project=$project", null, HttpStatus.OK, analyst.token))["value"].asInt()
        assertEquals(1, value(a))
        assertEquals(0, value(b))
        // dos proyectos pedidos no exigen asignación, pero las filas de B no salen
        val export =
            exchange(
                "POST",
                "/api/sgspe/exports/stakeholder-register",
                mapOf("filters" to mapOf("project" to listOf(a, b)), "disclosureLevel" to "INTERNO"),
                analyst.token
            )
        assertEquals(HttpStatus.OK, export.status)
        val log = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${analyst.id}&sort=exported_at&dir=desc", null, HttpStatus.OK))["content"]
        assertEquals(1, log[0]["attributes"]["row_count"].asInt())
        val layer = tree(send("GET", "/api/sgspe/territory/layers/LYR-09?project=$b", null, HttpStatus.OK, analyst.token))
        assertEquals(0, layer["features"].size())
    }

    @Test
    fun `quien lee todo y exporta un proyecto ajeno lo exporta y la fila de export_log no lleva el proyecto`() {
        stakeholder(b)
        val role = roleWith("stakeholder" to "READ", "stakeholder" to "EXPORT", "export_log" to "CREATE", "project" to "READ", "project" to "READ_ALL_PROJECTS")
        val exporter = userWithRole(role)
        val export =
            exchange(
                "POST",
                "/api/sgspe/exports/stakeholder-register",
                mapOf("filters" to mapOf("project" to listOf(b)), "disclosureLevel" to "INTERNO"),
                exporter.token
            )
        assertEquals(HttpStatus.OK, export.status, export.body)
        val log = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${exporter.id}", null, HttpStatus.OK))["content"][0]["attributes"]
        assertEquals(1, log["row_count"].asInt())
        assertTrue(log["project"] == null || log["project"].isNull)
        assertTrue(log["filters_description"].asString().contains(b), log.toString())
    }
}
```

- [ ] **Paso 2: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL` (la clase compila; no corre sin base).

- [ ] **Paso 3: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt
git commit -F - <<'MSG'
test: el alcance de lectura por proyecto en las rutas de la API

Con dos proyectos, quien está asignado a uno no lee el otro en listas, totales, detalle, historial,
auditoría, transiciones, relacionados, features, archivos, documentos, indicadores, exportaciones y
capas; asignación vencida, exenciones, cuenta de servicio y QCS confidenciales (ASM-106, ASM-108).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.scope.ProjectReadScopeApiTest`. Si una ruta de wasichai no acota como dice
la evaluación (§1), se detiene el plan y se decide con el usuario antes de la Tarea 13: es el riesgo que esta clase vigila.

---

### Tarea 12: Las matrices: «otro proyecto» y «no asignado»

**Files:**
- Modify: `src/test/kotlin/sgspe/security/PermissionMatrixApiTest.kt`, `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`

**Interfaces:**
- Consumes: `ProjectScope.ROOTS` y `ProjectScope.TENANT` (Tarea 4); `READ_ALL_PROJECTS` en `roles.json` (Tarea 3); de
  `F2ApiTest`, `member`, `project`, `fixture`, `sample`, `requiresReason`, `reason`, `Model` y `EXACTLY_ONE`; de
  `WorkflowMatrixApiTest`, `WORKFLOWS`, `DEFERRED`, `pathTo`, `recordAt`, `creatorFor`, `engagementTypeFor` y `TEXT`.
- Produces: la columna «otro proyecto» (lectura `404`, alta `400` sobre `project`; con `READ_ALL_PROJECTS`, `200` y `403`)
  y la columna «no asignado» (una transición por workflow, `404`).

La matriz de permisos ya asigna cada usuario al proyecto de la matriz (`member(role)`), así que su prueba existente sigue
igual. La columna nueva usa un segundo proyecto compartido y las raíces cuyo registro mínimo solo nombra maestros del
tenant (así el registro de prueba se arma en ese proyecto sin un padre de él).

- [ ] **Paso 1: La columna «otro proyecto» de `PermissionMatrixApiTest`**

Reemplazar:

```kotlin
import sgspe.F2ApiTest
```

por:

```kotlin
import sgspe.F2ApiTest
import sgspe.scope.ProjectScope
```

Antes de la línea `    // el admin del tenant tiene acceso a todo por otro camino: un objeto apiOnly le rechaza el alta aunque el cuerpo sea`,
agregar:

```kotlin
    // la columna «otro proyecto» (ASM-106 y ASM-108, security.md §14): un registro de un proyecto al que el rol no está asignado
    // no se lee (404) y el alta con ese proyecto es el 400 de wasichai sobre `project` (registro inexistente); quien tiene
    // READ_ALL_PROJECTS sobre project lo lee (200) y no da de alta en él sin asignación (403 NOT_ASSIGNED de ProjectScopeGuard).
    // se prueban el proyecto y las raíces cuyo registro mínimo solo nombra maestros del tenant (OTHER_PROJECT_ROOTS)
    @ParameterizedTest(name = "{0}")
    @MethodSource("roles")
    fun `ningun rol lee ni da de alta en un proyecto al que no esta asignado`(role: String) {
        val account = member(role)
        val grants = Model.roles.getValue(role)
        val readsTenant = grants["*"]?.contains("READ") == true
        val readsAll = "READ_ALL_PROJECTS" in grants["project"].orEmpty()
        val other = SHARED.shared.getOrPut(OTHER) { Fixture(project(), emptyMap()) }.id
        val failures = mutableListOf<String>()

        fun expect(
            what: String,
            wanted: HttpStatus,
            response: Response,
            field: String? = null
        ) {
            if (response.status != wanted) {
                failures += "$what: $wanted esperado, ${response.status} ${response.body.take(300)}"
            } else if (field != null && field !in problemFields(response.body)) {
                failures += "$what: el error no nombra $field: ${response.body.take(300)}"
            }
        }
        if ("READ" in grants["project"].orEmpty() || readsTenant) {
            expect(
                "project READ (otro proyecto)",
                if (readsAll) HttpStatus.OK else HttpStatus.NOT_FOUND,
                exchange("GET", "/api/objects/project/records/$other", token = account.token)
            )
        }
        for (name in OTHER_PROJECT_ROOTS) {
            val actions = grants[name].orEmpty()
            val headers = if (requiresReason(name)) mapOf(REASON to reason("matriz de permisos")) else emptyMap()
            val records = "/api/objects/$name/records"
            if ("READ" in actions || readsTenant) {
                val foreign =
                    try {
                        SHARED.shared.getOrPut("$OTHER:$name") { fixture(name, mapOf("project" to other)) }
                    } catch (e: Throwable) {
                        failures += "$name: no se pudo armar el registro del otro proyecto, ${e.message?.take(300)}"
                        null
                    }
                if (foreign != null) {
                    expect(
                        "$name READ (otro proyecto)",
                        if (readsAll) HttpStatus.OK else HttpStatus.NOT_FOUND,
                        exchange("GET", "$records/${foreign.id}", token = account.token)
                    )
                }
            }
            if ("CREATE" in actions) {
                val created = exchange("POST", records, mapOf("attributes" to sample(name) + ("project" to other)), account.token, headers)
                if (readsAll) {
                    expect("$name CREATE (otro proyecto)", HttpStatus.FORBIDDEN, created, "project")
                } else {
                    expect("$name CREATE (otro proyecto)", HttpStatus.BAD_REQUEST, created, "project")
                }
            }
        }
        assertEquals(emptyList<String>(), failures, "$role:\n${failures.joinToString("\n")}")
    }

    private fun problemFields(body: String): List<String> =
        runCatching { tree(body)["errors"].values().map { it["field"].asString() } }.getOrDefault(emptyList())
```

y en el `companion object`, reemplazar:

```kotlin
        private const val PROJECT = "\u0000project"
```

por:

```kotlin
        private const val PROJECT = "\u0000project"
        private const val OTHER = "\u0000other"

        // las raíces con `project` obligatorio cuyo registro mínimo solo nombra maestros del tenant: se pueden armar en otro
        // proyecto sin un padre de ese proyecto (ProjectScope, ASM-106)
        private val OTHER_PROJECT_ROOTS: List<String> get() =
            ProjectScope.ROOTS
                .filter { name ->
                    val obj = Model.objects.getValue(name)
                    obj["apiOnly"] != true && name !in EXACTLY_ONE &&
                        Model.relationships.any { it["source"] == name && it["fieldName"] == "project" && it["required"] == true } &&
                        Model.relationships
                            .filter { it["source"] == name && it["required"] == true && it["fieldName"] != null && it["fieldName"] != "project" }
                            .all { it["target"] in ProjectScope.TENANT }
                }.sorted()
```

(la línea `private const val OTHER` y `OTHER_PROJECT_ROOTS` quedan dentro del `companion object`, con su sangría).

- [ ] **Paso 2: La columna «no asignado» de `WorkflowMatrixApiTest`**

Antes de:

```kotlin
    @Test
    fun `cada transicion diferida a 2_2 o 3_2 existe en su workflow`() {
```

agregar:

```kotlin
    // un rol de la lista que no lee todos los proyectos: sin asignación al proyecto del registro, no lo ve (ASM-106)
    private fun scoped(role: String): Boolean = "READ_ALL_PROJECTS" !in Model.roles.getValue(role)["project"].orEmpty()

    // la columna «no asignado» (ASM-108, security.md §14): por workflow, la primera transición que se aplica por la API genérica,
    // como un rol de su lista que no lee todos los proyectos y está asignado a otro proyecto: 404 y el registro no cambia. las
    // de comando (aceptaciones, reclasificaciones, assess) leen el registro antes y responden igual, por su endpoint
    @TestFactory
    fun `una transicion de cada workflow como no asignado responde 404`(): List<DynamicNode> =
        WORKFLOWS.values.map { workflow ->
            val rule =
                workflow.transitions.first { rule ->
                    workflow.key(rule) !in DEFERRED && workflow.key(rule) !in BY_COMMAND && !rule.name.startsWith("close_reclassified") &&
                        rule.roles.any(::scoped) && pathTo(workflow, rule.from) != null
                }
            dynamicTest("${workflow.objectName}.${rule.name}") {
                val role = rule.roles.first(::scoped)
                val id = recordAt(workflow, rule.from, creatorFor(workflow, role), engagementTypeFor(workflow, rule), rule)
                val stranger = userWithRole(role).also { assign(it, project(), role) }
                val response = transition(workflow.objectName, id, rule.name, stranger.token, TEXT)
                assertEquals(HttpStatus.NOT_FOUND, response.status, "$role: ${response.body}")
                assertEquals(rule.from, state(workflow.objectName, id))
            }
        }

```

y en su `companion object`, reemplazar:

```kotlin
        private const val TEXT = "prueba de la matriz de workflows"
```

por:

```kotlin
        private const val TEXT = "prueba de la matriz de workflows"

        // las transiciones que el portal aplica por su comando y no por la API genérica (perform)
        private val BY_COMMAND = setOf("commitment_candidate.accept", "stakeholder_request.accept", "social_risk.assess")
```

Cada workflow tiene al menos una transición así: WF-A (`project`) la tiene con `PROJECT_MANAGER` en `submit_for_review`,
y en los demás ningún rol de transición lee todos los proyectos. Si `first` no encuentra una, la prueba falla al armarse y
lo dice: es una señal de que un workflow nuevo solo lo mueven roles con `READ_ALL_PROJECTS`.

- [ ] **Paso 3: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 4: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/security/PermissionMatrixApiTest.kt src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -F - <<'MSG'
test: columnas otro proyecto y no asignado en las matrices

La matriz de permisos prueba, por rol, que un registro de otro proyecto no se lee (404) y que el
alta en él es el 400 de wasichai, salvo con READ_ALL_PROJECTS (200 y 403); la de workflows, que una
transición de cada workflow como no asignado responde 404 (ASM-108).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.security.PermissionMatrixApiTest` (entre 11 y 58 minutos por el túnel) y
`sgspe.workflow.WorkflowMatrixApiTest`.

---

### Tarea 13: La migración de la suite de integración, por lotes

**Files:**
- Modify (lote 1, alcance y contrato): `src/test/kotlin/sgspe/scope/ProjectScopeApiTest.kt` (reescrito),
  `src/test/kotlin/sgspe/scope/ProjectLinkGuardApiTest.kt`, `src/test/kotlin/sgspe/scope/IssueDocumentApiTest.kt`,
  `src/test/kotlin/sgspe/common/TransitionChecksApiTest.kt`
- Modify (lote 2, permisos de campo): `src/test/kotlin/sgspe/security/FieldPermissionsApiTest.kt`
- Modify (lote 3, tableros y exportaciones): `src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt`,
  `src/test/kotlin/sgspe/privacy/StakeholderRegisterApiTest.kt`
- Modify (lote 4, territorio y evidencias): `src/test/kotlin/sgspe/territory/TerritoryLayersApiTest.kt`,
  `src/test/kotlin/sgspe/territory/TerritoryImportApiTest.kt`, `src/test/kotlin/sgspe/territory/ProjectComponentsApiTest.kt`,
  `src/test/kotlin/sgspe/territory/AreaOfInfluenceWorkflowGuardsApiTest.kt`, `src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt`

**Interfaces:**
- Consumes: el comportamiento de las Tareas 3, 7, 8, 9 y 10 y la tabla de errores de ASM-108.
- Produces: la suite de integración en verde con el alcance de lectura. Ningún cambio de código principal.

Es el riesgo principal de la especificación (§7, riesgo 1). La clasificación sale de un grep real de la rama 4.4
(`grep -rn 'userWithRoles\{0,1\}(' src/test/kotlin` sin la definición: 67 líneas con 70 llamadas en 25 archivos, una de
ellas el propio `member()` de `F2ApiTest`; y `grep -rn NOT_ASSIGNED src/test/kotlin`). Cada llamada cae en una de tres
clases (§3.13):

1. **A `member`.** El usuario tenía que leer el proyecto de la prueba: pasa a `member(...)` o se le asigna.
2. **Sin cambio.** Es `AUDITOR` o `PROJECT_ADMIN`, la prueba es de un maestro del tenant o de un objeto propio de la
   prueba, el usuario solo pone su id en un campo (`*_user_id`), lo frena un permiso de objeto antes de leer, o la prueba
   no lee registros (bandeja, login, `/api/users`).
3. **Fila de ASM-108.** La prueba quería a alguien sin asignación: espera el `404`, el `400` de `RELATION` o el conjunto
   vacío que le toca.

| Archivo:línea (rama 4.4) | Llamada | Clase | Qué cambia |
|---|---|---|---|
| `F2ApiTest.kt:55` | `member()` | — | Es la definición de `member` |
| `F2ApiTest.kt:108` | `READ_ONLY` en `sample` | 2 | Solo el id de un `*_user_id` |
| `F3DomainApiTest.kt:13`, `:49`, `:55` | `COMMITMENT_MANAGER` | 2 | Verificador y responsable: solo ids |
| `F4ApiTest.kt:95` | `SOCIAL_RISK_MANAGER` | 2 | Responsable del riesgo: solo id |
| `contract/ErrorContractApiTest.kt:73` | `ANALYST` | 2 | Objeto de prueba sin READ: 403 por permiso, antes de leer |
| `contract/RolesApiTest.kt:35` | `AUDITOR` | 2 | Lee todo; el objeto de prueba lo lee igual (`READ_ALL_PROJECTS` exime) |
| `contract/RolesApiTest.kt:46`, `:52` | `ANALYST`, `AUDITOR` | 2 | `/api/audit`, `/api/users`, `/api/roles`: no son registros |
| `contract/SecurityApiTest.kt:28`, `:40` | `READ_ONLY` | 2 | Login y `/api/auth/me` |
| `privacy/StakeholderRegisterApiTest.kt:137`, `:193` | `ANALYST` | 2 | `NOT_ASSIGNED` antes de leer (un solo proyecto pedido); con la asignación, 200 |
| `privacy/StakeholderRegisterApiTest.kt:160` | `ANALYST` | 2 | Sin asignaciones, «mis proyectos» vacío: 0 filas, como antes |
| `privacy/StakeholderRegisterApiTest.kt:209` | `ANALYST` | 3 | Dos proyectos que no lee: `row_count` 0 (antes 2) |
| `privacy/CommitmentMatrixApiTest.kt:128` | `COMMITMENT_MANAGER` | 2 | `NOT_ASSIGNED` antes de leer |
| `evidence/EvidenceApiTest.kt:277` | `FIELD_AGENT` | 3 | La evidencia del proyecto del que ya no es miembro: `404` (la prueba ya lo anunciaba) |
| `security/FieldPermissionsApiTest.kt:61`, `:62`, `:63` | `READ_ONLY`, `PROJECT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` | 2 | `person`, maestro |
| `security/FieldPermissionsApiTest.kt:85` | `AUDITOR` | 2 | Lee todo |
| `security/FieldPermissionsApiTest.kt:103`, `:104` | `ANALYST`, `FIELD_AGENT` | 1 | `engagement_participant` del proyecto de la prueba |
| `security/FieldPermissionsApiTest.kt:115`, `:116` | `READ_ONLY`, `GIS_ANALYST` | 2 | `land_parcel` y `land_right`, maestros |
| `security/FieldPermissionsApiTest.kt:126`, `:127` | `COMMUNITY_RELATIONS_OFFICER`, `SOCIAL_MANAGER` | 2 | `locality_profile` sin estudio: del tenant |
| `security/FieldPermissionsApiTest.kt:150`, `:151` | `FIELD_AGENT`, `STAKEHOLDER_MANAGER` | 1 | `stakeholder` |
| `security/FieldPermissionsApiTest.kt:181`, `:182`, `:183` | `READ_ONLY`, `APPROVER`, `COMMITMENT_MANAGER` | 1 | `delivery` y `commitment_candidate` |
| `security/FieldPermissionsApiTest.kt:199`, `:209` | `userWithRoles` | 1 | `stakeholder`: se asigna con el primer rol |
| `security/FieldPermissionsApiTest.kt:312`, `:313`, `:314` | no lector y lector | 1 | Cada objeto de `restrictedFields` cuelga del proyecto de la prueba; en un maestro, `member` no cambia nada |
| `notification/ApprovalNoticesApiTest.kt:29` | `APPROVER` inactivo | 2 | Solo la bandeja |
| `scope/ProjectLinkGuardApiTest.kt:54` (dos) | `COMMITMENT_MANAGER` | 2 | Solo ids |
| `scope/ProjectLinkGuardApiTest.kt:86` | `COMMUNITY_RELATIONS_OFFICER` | 3 | Queja con `project` que no lee: `400` sobre `project` (antes `403 NOT_ASSIGNED`) |
| `scope/ProjectScopeApiTest.kt:43`, `:52`, `:55` | `COMMUNITY_RELATIONS_OFFICER` | 3 | `400` sobre `project` con `"no record with this id"` |
| `scope/ProjectScopeApiTest.kt:139` | `PROJECT_ADMIN` | 2 | Crea el proyecto y lo lee (`READ_ALL_PROJECTS`) |
| `scope/RecipientsApiTest.kt:24`, `:46` | `ANALYST`, `PROJECT_ADMIN` | 2 | Ids; la prueba lee como la plataforma |
| `commitment/CommitmentApprovalApiTest.kt:21` (dos) | `COMMITMENT_MANAGER` | 2 | Ids |
| `commitment/CommitmentApprovalApiTest.kt:69`, `:93` | `userWithRoles` | 2 | Ya se asignan |
| `commitment/ProjectLinkApiTest.kt:14`, `:15` | `COMMITMENT_MANAGER` | 2 | Ids |
| `commitment/CommitmentRecordRulesApiTest.kt:13` (dos) | `COMMITMENT_MANAGER` | 2 | Ids |
| `dashboard/DashboardsApiTest.kt:255` | `ANALYST` | 3 | Sin asignaciones ni `READ_ALL_PROJECTS`: ningún proyecto por defecto; se suma `AUDITOR`, que los ve todos |
| `common/CrossGuardsApiTest.kt:73` | `ANALYST` | 2 | Id de una asignación que crea el admin |
| `common/TransitionChecksApiTest.kt:126` | `PROJECT_MANAGER` | 3 | `404`; `NOT_ASSIGNED` pasa a probarse con `PROJECT_ADMIN`, que lee sin estar asignado |
| `common/AmountFieldsApiTest.kt:29`, `:30` | `COMMITMENT_MANAGER` | 2 | Ids |
| `territory/TerritoryLayersApiTest.kt:78`, `:80` | `GIS_ANALYST`, `ANALYST`, `READ_ONLY` | 2 | LYR-14, predios: maestros |
| `territory/TerritoryLayersApiTest.kt:213` | `READ_ONLY` | 1 | LYR-13 de dos proyectos: se asigna a los dos |
| `territory/ProjectComponentsApiTest.kt:205` | `READ_ONLY` | 2 | Sin UPDATE: 403 antes de leer |
| `workflow/ProjectWorkflowApiTest.kt:80` | `PROJECT_ADMIN` | 2 | Se asigna |
| `workflow/WorkflowMatrixApiTest.kt:68` | `actor()` | 2 | Asigna al proyecto de la prueba |
| `workflow/WorkflowMatrixApiTest.kt:130`, `:131`, `:151`, `:219` | `READ_ONLY` | 2 | Ids de responsable y verificador |

Las aserciones `NOT_ASSIGNED` y los «de otro proyecto» que no salen de `userWithRole`:

| Archivo:línea | Clase | Qué cambia |
|---|---|---|
| `scope/ProjectScopeApiTest.kt:87`, `:102`, `:116` (`member(…, other)`) | 3 | Borrar, transicionar y sacar de su proyecto un registro que no lee: `404`; vínculo M2M: `404` |
| `scope/IssueDocumentApiTest.kt:57-62` | 3 | Emitir sobre la interacción de otro proyecto: `404` |
| `territory/AreaOfInfluenceWorkflowGuardsApiTest.kt:175-182` | 3 | Aprobar el área de otro proyecto: `404` |
| `territory/ProjectComponentsApiTest.kt:206` | 3 | El `PATCH` del componente de otro proyecto: `404`; el alta sigue `403 NOT_ASSIGNED` (se comprueba antes de leer) |
| `territory/TerritoryImportApiTest.kt:331-339`, `:345-350`, `:358-361` | 2 | Confirmación y vistas previas de componentes y concesiones: `NOT_ASSIGNED` antes de leer el proyecto |
| `territory/TerritoryImportApiTest.kt:352-354` | 3 | Vista previa de predios: lee el CRS del proyecto, que no lee: `404` (decisión 14) |
| `privacy/StakeholderRegisterApiTest.kt:140`, `:197`; `privacy/CommitmentMatrixApiTest.kt:131` | 2 | `NOT_ASSIGNED` antes de leer |
| `warning/WarningRulesJobApiTest.kt:49` | — | Comentario, sin aserción |

Los subagentes editan y compilan; el coordinador corre cada lote (o la CI lo hace en el PR) y, si una clase que no está en
la tabla falla, la clasifica con las mismas tres clases y suma el cambio al lote en curso, con su fila en el cuerpo del
PR.

- [ ] **Paso 1: Lote 1, alcance y contrato**

Reemplazar el contenido de `src/test/kotlin/sgspe/scope/ProjectScopeApiTest.kt` por:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// security.md §14 «Alcance» (RQ-SEC-006, ASM-106, ASM-108): sin asignación vigente no se escribe en el proyecto; con ella sí;
// ADMIN queda exento; los hijos se acotan por su raíz; cambiar de proyecto exige los dos. desde 4.4 el alcance de lectura
// responde antes que ProjectScopeGuard: un proyecto que no se lee es el 400 de wasichai sobre la relación (registro
// inexistente) y un registro que no se lee, 404. NOT_ASSIGNED queda para quien lee sin estar asignado (PROJECT_ADMIN)
class ProjectScopeApiTest : DomainApiTest() {
    // el 403 del alcance lleva en errors[0] el campo de la relación de alcance y, como message, el código NOT_ASSIGNED: un 403
    // por permiso o por otra guarda no lo trae, así que la prueba no se queda verde si otra guarda rechaza antes
    private fun assertNotAssigned(
        response: Response,
        field: String
    ) {
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        val error = tree(response.body)["errors"].values().single()
        assertEquals("NOT_ASSIGNED", error["message"].asString(), response.body)
        assertEquals(field, error["field"].asString(), response.body)
    }

    // el 400 de wasichai sobre una RELATION que nombra un registro que quien escribe no lee (D30, ASM-108)
    private fun assertMissing(
        response: Response,
        field: String
    ) {
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        val error = tree(response.body)["errors"].values().single()
        assertEquals(field, error["field"].asString(), response.body)
        assertEquals("no record with this id", error["message"].asString(), response.body)
    }

    private fun delete(
        objectName: String,
        id: String,
        token: String
    ) = exchange("DELETE", "/api/objects/$objectName/records/$id", token = token)

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
    fun `sin asignacion el proyecto no existe para quien escribe y con una asignacion vigente se escribe`() {
        val project = projectWithCrs()
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assertMissing(createEngagement(project, outsider.token), "project")
        assign(outsider, project, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.CREATED, createEngagement(project, outsider.token).status)
    }

    // quien lee todos los proyectos (READ_ALL_PROJECTS) sin estar asignado sigue recibiendo NOT_ASSIGNED: ProjectScopeGuard es
    // la segunda línea (elección 5 de la especificación 4.4)
    @Test
    fun `quien lee todos los proyectos no escribe en uno sin asignacion`() {
        val project = projectWithCrs()
        val admin = userWithRole("PROJECT_ADMIN")
        val party = mapOf("project" to project, "organization" to organization(), "party_role" to "CLIENTE")
        assertNotAssigned(exchange("POST", "/api/objects/project_party/records", mapOf("attributes" to party), admin.token), "project")
        assign(admin, project, "PROJECT_ADMIN")
        assertEquals(HttpStatus.CREATED, exchange("POST", "/api/objects/project_party/records", mapOf("attributes" to party), admin.token).status)
    }

    @Test
    fun `una asignacion inactiva o vencida no da alcance, y ADMIN no la necesita`() {
        val project = projectWithCrs()
        val inactive = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(inactive, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("active" to false))
        assertMissing(createEngagement(project, inactive.token), "project")
        val expired = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        assignWith(expired, project, "COMMUNITY_RELATIONS_OFFICER", mapOf("start_date" to "2020-01-01", "end_date" to "2020-12-31"))
        assertMissing(createEngagement(project, expired.token), "project")
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
        assertMissing(participant, "engagement")
        val own = engagement(mine, token = officer.token)
        assertMissing(edit("engagement", own, mapOf("project" to other), officer.token), "project")
        assign(officer, other, "COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.OK, edit("engagement", own, mapOf("project" to other), officer.token).status)
    }

    // las bajas, las transiciones, los vínculos (desde el lado del grupo de interés) y sacar un registro de un proyecto ajeno
    // también piden asignación: wasichai manda en todos esos caminos la fila completa como `before`
    @Test
    fun `sin asignacion no se borra, ni se transiciona, ni se vincula, ni se saca un registro del proyecto`() {
        val mine = projectWithCrs()
        val other = projectWithCrs()
        val outsider = member("SOCIAL_MANAGER", other)
        val plan = fixture("engagement_plan", mapOf("project" to mine)).id
        assertEquals(HttpStatus.NOT_FOUND, delete("engagement_plan", plan, outsider.token).status)

        val foreign = engagement(mine)
        assertEquals(HttpStatus.NOT_FOUND, transition("engagement", foreign, "schedule", outsider.token, "prueba").status)

        val holder = stakeholder(mine)
        // un vínculo M2M con un extremo que no se lee: 404 de RelatedRecordService
        assertEquals(HttpStatus.NOT_FOUND, link("stakeholder", holder, "engagement_stakeholder", foreign, outsider.token).status)
        // con asignación al proyecto, el mismo vínculo se acepta
        val insider = member("SOCIAL_MANAGER", mine)
        val linked = link("stakeholder", holder, "engagement_stakeholder", foreign, insider.token)
        assertTrue(linked.status.is2xxSuccessful, linked.body)

        // asignado solo al destino: el registro del proyecto de origen no se lee, así que no se saca de él (404)
        val ownProject = member("COMMUNITY_RELATIONS_OFFICER", other)
        val moved = mapOf("attributes" to mapOf("project" to other, "engagement_type" to "REUNION", "title" to "Reunión de prueba"))
        assertEquals(HttpStatus.NOT_FOUND, exchange("PUT", "/api/objects/engagement/records/$foreign", moved, ownProject.token).status)
    }

    // un padre con dependientes no se borra (DependentsDeleteGuard, P13): wasichai declara las relaciones obligatorias NOT NULL
    // con ON DELETE SET NULL, así que el borrado fallaría en la base. por eso no hay registros huérfanos por la API en los
    // objetos del alcance, cuyas relaciones de alcance son obligatorias (las opcionales, export_log.project y
    // locality_profile.study, quedan del tenant a propósito, security.md §9.2). la guarda cerrada ante un registro sin dueño
    // es defensa en profundidad y la cubre ProjectResolverTest, sin base
    @Test
    fun `un plan con actividades no se borra, ni siquiera el administrador, y sin actividades si`() {
        val mine = projectWithCrs()
        val other = projectWithCrs()
        val owner = member("SOCIAL_MANAGER", mine)
        val outsider = member("SOCIAL_MANAGER", other)
        val plan = fixture("engagement_plan", mapOf("project" to mine)).id
        val activity = fixture("planned_activity", mapOf("engagement_plan" to plan)).id

        for (caller in listOf(owner.token, token)) {
            val refused = delete("engagement_plan", plan, caller)
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            val error = tree(refused.body)["errors"].values().single()
            assertEquals("HAS_DEPENDENTS", error["message"].asString(), refused.body)
            assertEquals("planned_activity.engagement_plan", error["field"].asString(), refused.body)
        }
        // el alcance habla primero: quien no es del equipo no lee el plan (404), sin enterarse de los dependientes
        assertEquals(HttpStatus.NOT_FOUND, delete("engagement_plan", plan, outsider.token).status)
        // ambos registros siguen, y la actividad sigue ligada al plan
        assertEquals(plan, record("planned_activity", activity)["attributes"]["engagement_plan"].asString())
        assertEquals(mine, record("engagement_plan", plan)["attributes"]["project"].asString())

        assertEquals(HttpStatus.NO_CONTENT, delete("planned_activity", activity, owner.token).status)
        assertEquals(HttpStatus.NO_CONTENT, delete("engagement_plan", plan, owner.token).status)
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

En `src/test/kotlin/sgspe/scope/ProjectLinkGuardApiTest.kt`, reemplazar
`import org.junit.jupiter.api.Assertions.assertEquals` por esa línea más `import org.junit.jupiter.api.Assertions.assertTrue`
en la línea siguiente, y reemplazar:

```kotlin
    fun `quien no esta asignado al proyecto no registra una queja`() {
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val response = create("grievance", attentionAttributes(testProject(), outsider, "QJ-"), outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertEquals("NOT_ASSIGNED", code(response))
    }
```

por:

```kotlin
    fun `quien no esta asignado al proyecto no registra una queja`() {
        // desde 4.4 el proyecto no existe para quien no lo lee: el 400 de wasichai sobre la relación (ASM-108)
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val response = create("grievance", attentionAttributes(testProject(), outsider, "QJ-"), outsider.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertTrue("project" in problemFields(response.body), response.body)
    }
```

En `src/test/kotlin/sgspe/scope/IssueDocumentApiTest.kt`, reemplazar:

```kotlin
        val refused = exchange("POST", "/api/objects/engagement/records/$other/documents/meeting-minutes", null, officer.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("NOT_ASSIGNED", code(refused))
        assertEquals(listOf("project"), problemFields(refused.body))
        assertEquals(emptyList<String>(), validMinutes(other))
```

por:

```kotlin
        // la interacción de otro proyecto no se lee: el 404 de wasichai, como su detalle (ASM-108)
        val refused = exchange("POST", "/api/objects/engagement/records/$other/documents/meeting-minutes", null, officer.token)
        assertEquals(HttpStatus.NOT_FOUND, refused.status, refused.body)
        assertEquals(emptyList<String>(), validMinutes(other))
```

y, en el comentario de la clase, reemplazar `wasichai-documents) y no en otro: 403 NOT_ASSIGNED.` por
`wasichai-documents) y no en otro, que no lee: 404 (ASM-108).`.

En `src/test/kotlin/sgspe/common/TransitionChecksApiTest.kt`, reemplazar:

```kotlin
    fun `quien no esta asignado al proyecto recibe NOT_ASSIGNED entre los bloqueos`() {
        val project = projectWithCrs()
        val stranger = userWithRole("PROJECT_MANAGER")
        val check =
```

por:

```kotlin
    fun `quien no esta asignado al proyecto recibe NOT_ASSIGNED entre los bloqueos`() {
        // desde 4.4 quien no lee el proyecto recibe 404 (ASM-108); NOT_ASSIGNED queda para quien lo lee sin estar asignado:
        // PROJECT_ADMIN, que tiene READ_ALL_PROJECTS
        val project = projectWithCrs()
        val unreadable =
            exchange(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "project", "recordId" to project, "transition" to "submit_for_review"),
                userWithRole("PROJECT_MANAGER").token
            )
        assertEquals(HttpStatus.NOT_FOUND, unreadable.status, unreadable.body)
        val stranger = userWithRole("PROJECT_ADMIN")
        val check =
```

Compilar y commit:

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat compileTestKotlin
git add src/test/kotlin/sgspe/scope/ProjectScopeApiTest.kt src/test/kotlin/sgspe/scope/ProjectLinkGuardApiTest.kt \
  src/test/kotlin/sgspe/scope/IssueDocumentApiTest.kt src/test/kotlin/sgspe/common/TransitionChecksApiTest.kt
git commit -F - <<'MSG'
test: el alcance de lectura en las pruebas de alcance y contrato

Quien no está asignado ya no lee el proyecto: el alta es el 400 de wasichai sobre la relación y
leer, borrar, transicionar, vincular o emitir sobre un registro ajeno es 404. NOT_ASSIGNED queda
para quien lee sin estar asignado (PROJECT_ADMIN), que la prueba nueva fija (ASM-108).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración del lote 1 (coordinador):** `sgspe.scope.*ApiTest`, `sgspe.contract.*ApiTest`,
`sgspe.common.TransitionChecksApiTest`, `sgspe.common.CrossGuardsApiTest`, `sgspe.attention.StakeholderRequestWorkflowApiTest`
(`ConfidentialRelease` dentro del proyecto, decisión 10).

- [ ] **Paso 2: Lote 2, permisos de campo**

En `src/test/kotlin/sgspe/security/FieldPermissionsApiTest.kt`, reemplazar:

```kotlin
        val analyst = userWithRole("ANALYST")
        val fieldAgent = userWithRole("FIELD_AGENT")
        val forAnalyst
```

por:

```kotlin
        // el asistente es de una interacción del proyecto de la prueba: desde 4.4 (ASM-106) lo lee quien está asignado a él
        val analyst = member("ANALYST")
        val fieldAgent = member("FIELD_AGENT")
        val forAnalyst
```

reemplazar:

```kotlin
        val fieldAgent = userWithRole("FIELD_AGENT")
        val manager = userWithRole("STAKEHOLDER_MANAGER")
        assertFalse(record("stakeholder", stakeholder.id, fieldAgent).has("stance"))
```

por:

```kotlin
        val fieldAgent = member("FIELD_AGENT")
        val manager = member("STAKEHOLDER_MANAGER")
        assertFalse(record("stakeholder", stakeholder.id, fieldAgent).has("stance"))
```

reemplazar:

```kotlin
        val readOnly = userWithRole("READ_ONLY")
        val approver = userWithRole("APPROVER")
        val manager = userWithRole("COMMITMENT_MANAGER")
```

por:

```kotlin
        val readOnly = member("READ_ONLY")
        val approver = member("APPROVER")
        val manager = member("COMMITMENT_MANAGER")
```

reemplazar:

```kotlin
            assertTrue(record("stakeholder", stakeholder.id, userWithRoles(roles)).has("stance"), roles.toString())
```

por:

```kotlin
            val account = userWithRoles(roles).also { assign(it, testProject(), roles.first()) }
            assertTrue(record("stakeholder", stakeholder.id, account).has("stance"), roles.toString())
```

reemplazar:

```kotlin
            val account = userWithRoles(roles)
            assertFalse(record("stakeholder", stakeholder.id, account).has("stance"), roles.toString())
```

por:

```kotlin
            val account = userWithRoles(roles).also { assign(it, testProject(), roles.first()) }
            assertFalse(record("stakeholder", stakeholder.id, account).has("stance"), roles.toString())
```

y reemplazar:

```kotlin
                        val byId = record(objectName, created.id, userWithRole(nonReader))
                        val inList = listed(objectName, filter, userWithRole(nonReader))
                        val forReader = record(objectName, created.id, userWithRole(reader))
```

por:

```kotlin
                        // asignados al proyecto de la prueba, del que cuelga el registro (ASM-106); en un maestro no cambia nada
                        val byId = record(objectName, created.id, member(nonReader))
                        val inList = listed(objectName, filter, member(nonReader))
                        val forReader = record(objectName, created.id, member(reader))
```

Compilar y commit:

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat compileTestKotlin
git add src/test/kotlin/sgspe/security/FieldPermissionsApiTest.kt
git commit -F - <<'MSG'
test: los permisos de campo se prueban con usuarios asignados

Las pruebas de permisos de campo sobre objetos de proyecto (asistentes, grupos de interés, entregas,
candidatos y cada campo de restrictedFields) asignan sus usuarios al proyecto de la prueba: sin
asignación ya no leerían el registro (ASM-106). Las de maestros no cambian.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración del lote 2 (coordinador):** `sgspe.security.FieldPermissionsApiTest`.

- [ ] **Paso 3: Lote 3, tableros y exportaciones**

En `src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt`, reemplazar:

```kotlin
        assertTrue(projects(token).containsAll(listOf(mine, other)))
        assertTrue(projects(userWithRole("ANALYST").token).containsAll(listOf(mine, other)))
        assertEquals(listOf(mine), projects(member("ANALYST", mine).token))
```

por:

```kotlin
        assertTrue(projects(token).containsAll(listOf(mine, other)))
        // desde 4.4 (ProjectReach): quien lee todos los proyectos (AUDITOR) los recibe todos; sin asignaciones, ninguno
        assertTrue(projects(userWithRole("AUDITOR").token).containsAll(listOf(mine, other)))
        assertEquals(emptyList<String>(), projects(userWithRole("ANALYST").token))
        assertEquals(listOf(mine), projects(member("ANALYST", mine).token))
```

y su comentario
`    // sin project: el ADMIN ve todos los proyectos; quien no tiene asignaciones, todos los que puede leer; quien las tiene,`
por
`    // sin project: el ADMIN y quien lee todos los proyectos ven todos; quien no tiene asignaciones, ninguno; quien las tiene,`.

En `src/test/kotlin/sgspe/privacy/StakeholderRegisterApiTest.kt`, reemplazar:

```kotlin
        assertEquals(200, export(both, outsider.token).status.value())
        val attributes = logOf(outsider.id)[0]["attributes"]
        assertEquals(2, attributes["row_count"].asInt())
```

por:

```kotlin
        assertEquals(200, export(both, outsider.token).status.value())
        val attributes = logOf(outsider.id)[0]["attributes"]
        // desde 4.4 las filas de proyectos que no lee no salen (ASM-106): el archivo va vacío
        assertEquals(0, attributes["row_count"].asInt())
```

Compilar y commit:

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat compileTestKotlin
git add src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt src/test/kotlin/sgspe/privacy/StakeholderRegisterApiTest.kt
git commit -F - <<'MSG'
test: tableros y exportaciones con mis proyectos de ProjectReach

Sin asignaciones ni READ_ALL_PROJECTS no hay proyectos por defecto, AUDITOR los ve todos, y una
exportación de proyectos que no se leen sale sin filas (ASM-106).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración del lote 3 (coordinador):** `sgspe.dashboard.*ApiTest`, `sgspe.privacy.*ApiTest`,
`sgspe.attention.AttentionReportApiTest`.

- [ ] **Paso 4: Lote 4, territorio y evidencias**

En `src/test/kotlin/sgspe/territory/TerritoryLayersApiTest.kt`, reemplazar:

```kotlin
        val readOnly = userWithRole("READ_ONLY")
        val own = tree(send("GET", "/api/sgspe/territory/layers/LYR-13?project=$project", null, HttpStatus.OK, readOnly.token))
```

por:

```kotlin
        // desde 4.4 la capa solo trae lo que quien mira lee (ASM-106): asignado a los dos proyectos
        val readOnly = member("READ_ONLY", project).also { assign(it, other, "READ_ONLY") }
        val own = tree(send("GET", "/api/sgspe/territory/layers/LYR-13?project=$project", null, HttpStatus.OK, readOnly.token))
```

En `src/test/kotlin/sgspe/territory/TerritoryImportApiTest.kt`, reemplazar:

```kotlin
        val tenantLevel = preview(project, parcel, outsider.token, target = "land_parcel", sourceCrs = "EPSG 4326", columns = null)
        assertEquals(HttpStatus.OK, tenantLevel.status, tenantLevel.text)
```

por:

```kotlin
        // un predio es del tenant, pero la vista previa lee el CRS de trabajo del proyecto, que quien no es del equipo no lee
        // desde 4.4: 404 (ASM-108)
        val tenantLevel = preview(project, parcel, outsider.token, target = "land_parcel", sourceCrs = "EPSG 4326", columns = null)
        assertEquals(HttpStatus.NOT_FOUND, tenantLevel.status, tenantLevel.text)
```

En `src/test/kotlin/sgspe/territory/ProjectComponentsApiTest.kt`, reemplazar:

```kotlin
        for ((who, account) in listOf("sin permiso" to readOnly, "sin asignación" to outsider)) {
            val created = registerOf(project, account.token, footprint, "TST-403-2", "PLATAFORMA")
            assertEquals(HttpStatus.FORBIDDEN, created.status, "$who: ${created.body}")
            val changed =
                patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), account.token, ifMatch(id))
            assertEquals(HttpStatus.FORBIDDEN, changed.status, "$who: ${changed.body}")
            // un PATCH que no cambia nada tampoco depende de los datos: sigue siendo 403
            val unchanged =
                patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_type" to "PLATAFORMA")), account.token, ifMatch(id))
            assertEquals(HttpStatus.FORBIDDEN, unchanged.status, "$who: ${unchanged.body}")
        }
```

por:

```kotlin
        // el alta pide la asignación antes de leer nada (403 para los dos); el cambio lee el componente: quien no es del equipo no
        // lo lee desde 4.4 y recibe 404 (ASM-108), quien no tiene UPDATE sigue con 403
        for ((who, account, onChange) in listOf(
            Triple("sin permiso", readOnly, HttpStatus.FORBIDDEN),
            Triple("sin asignación", outsider, HttpStatus.NOT_FOUND)
        )) {
            val created = registerOf(project, account.token, footprint, "TST-403-2", "PLATAFORMA")
            assertEquals(HttpStatus.FORBIDDEN, created.status, "$who: ${created.body}")
            val changed =
                patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_status" to "EJECUTADO")), account.token, ifMatch(id))
            assertEquals(onChange, changed.status, "$who: ${changed.body}")
            // un PATCH que no cambia nada tampoco depende de los datos
            val unchanged =
                patch("/api/sgspe/project-components/$id", mapOf("attributes" to mapOf("component_type" to "PLATAFORMA")), account.token, ifMatch(id))
            assertEquals(onChange, unchanged.status, "$who: ${unchanged.body}")
        }
```

y el título de la prueba `` `sin permiso o sin asignacion es 403 antes de cualquier cuenta de PostGIS` `` por
`` `sin permiso o sin asignacion se rechaza antes de cualquier cuenta de PostGIS` ``.

En `src/test/kotlin/sgspe/territory/AreaOfInfluenceWorkflowGuardsApiTest.kt`, reemplazar:

```kotlin
    fun `quien tiene el rol pero no una asignacion vigente al proyecto recibe 403 NOT_ASSIGNED y nada cambia`() {
        val project = projectWithCrs()
        val area = version(project, 1)
        val elsewhere = member("SOCIAL_MANAGER", projectWithCrs())
        val refused = approve(area, elsewhere.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("NOT_ASSIGNED", code(refused.body))
```

por:

```kotlin
    fun `quien tiene el rol pero no una asignacion vigente al proyecto no ve el area y nada cambia`() {
        // desde 4.4 el área de otro proyecto no se lee: 404 (ASM-108)
        val project = projectWithCrs()
        val area = version(project, 1)
        val elsewhere = member("SOCIAL_MANAGER", projectWithCrs())
        val refused = approve(area, elsewhere.token)
        assertEquals(HttpStatus.NOT_FOUND, refused.status, refused.body)
```

En `src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt`, reemplazar:

```kotlin
        assertEquals(HttpStatus.OK, read.status, "la lectura ya no es de todo el tenant (UPS-01): exija aquí un 4xx")
```

por:

```kotlin
        // desde 4.4 la lectura es por proyecto (ASM-106): quien ya no es miembro no la lee
        assertEquals(HttpStatus.NOT_FOUND, read.status, read.body)
```

Compilar (ktlint señala imports que quedaron sin uso, como `code` no lo es: es un método de la base) y commit:

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/test/kotlin/sgspe/territory/TerritoryLayersApiTest.kt src/test/kotlin/sgspe/territory/TerritoryImportApiTest.kt \
  src/test/kotlin/sgspe/territory/ProjectComponentsApiTest.kt src/test/kotlin/sgspe/territory/AreaOfInfluenceWorkflowGuardsApiTest.kt \
  src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt
git commit -F - <<'MSG'
test: territorio y evidencias con el alcance de lectura

Cambiar un componente, aprobar un área, previsualizar predios de un proyecto o leer una evidencia
que no se lee responde 404; las capas se prueban con un usuario asignado a los dos proyectos; lo que
se comprueba antes de leer sigue en 403 NOT_ASSIGNED (ASM-108).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración del lote 4 (coordinador):** `sgspe.territory.*ApiTest`, `sgspe.evidence.*ApiTest`.

- [ ] **Paso 5: Lote 5, el resto de la suite (coordinador)**

Con los lotes 1 a 4 integrados, la suite entera una vez: en la CI del PR o por el túnel con `sgspe_sdd_test` libre (de 30
minutos a más de una hora):

```bash
cd ~/IdeaProjects/sgspe
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun
```

Cada falla que no está en las tablas de arriba se clasifica (1, 2 o 3), se corrige en un commit `test:` con el mismo
formato y se anota en el cuerpo del PR («Para revisar»). Una falla que no encaja en ninguna clase (la plataforma no acota
una ruta, o acota algo que no debería) detiene el plan: se decide con el usuario.

---

### Tarea 14: La duración por organización de los trabajos (`SweepTiming`)

**Files:**
- Create: `src/main/kotlin/sgspe/common/SweepTiming.kt`
- Modify: `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, `src/main/kotlin/sgspe/warning/WarningRulesJob.kt`,
  `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt`, `src/main/kotlin/sgspe/common/DerivedFieldsJob.kt`
- Test: `src/test/kotlin/sgspe/common/SweepTimingTest.kt`

**Interfaces:**
- Consumes: el `log` (SLF4J) de cada barrido.
- Produces: `object SweepTiming { suspend fun <T> timed(log: Logger, job: String, organization: UUID, nanoTime: () -> Long = System::nanoTime, block: suspend () -> T): T }`,
  que deja `«<job>: organización <id> en <ms> ms»` por organización, también si falla. La lee el coordinador en la medición
  de la Tarea 17.

§3.10 de la especificación: los trabajos recorren los tenants en serie; esta tarea solo mide (sin nombres ni datos de
registros: el id de la organización no es un dato personal). `RetentionSweep` no recorre organizaciones (decisión 13).

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/common/SweepTimingTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.slf4j.Logger
import java.util.UUID

// una línea de log por organización con su duración (especificación 4.4 §3.10), también cuando la organización falla
class SweepTimingTest {
    private val organization = UUID.fromString("00000000-0000-0000-0000-0000000000a0")

    // un reloj que avanza 7 ms entre la primera y la segunda lectura
    private fun ticks(): () -> Long {
        val readings = ArrayDeque(listOf(1_000_000L, 8_000_000L))
        return { readings.removeFirst() }
    }

    @Test
    fun `deja la duracion de la organizacion y devuelve lo del bloque`() {
        val log = mock(Logger::class.java)
        assertEquals(3, runBlocking { SweepTiming.timed(log, "DeadlineJob", organization, ticks()) { 3 } })
        verify(log).info("{}: organización {} en {} ms", "DeadlineJob", organization, 7L)
    }

    @Test
    fun `tambien la deja si la organizacion falla`() {
        val log = mock(Logger::class.java)
        assertThrows<IllegalStateException> { runBlocking { SweepTiming.timed(log, "WarningRulesJob", organization, ticks()) { error("falla") } } }
        verify(log).info("{}: organización {} en {} ms", "WarningRulesJob", organization, 7L)
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.SweepTimingTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'SweepTiming'`).

- [ ] **Paso 3: Implementar y usarlo en los cuatro barridos**

Crear `src/main/kotlin/sgspe/common/SweepTiming.kt`:

```kotlin
package sgspe.common

import org.slf4j.Logger
import java.util.UUID

// la duración de cada organización en un barrido de un trabajo (especificación 4.4 §3.10): los trabajos recorren los tenants
// en serie (Organizations.having), y con muchos clientes un barrido podría no caber en su intervalo. una línea de log por
// organización, con su id y los milisegundos, sin nombres ni datos de registros; también si la organización falla
object SweepTiming {
    private const val NANOS_PER_MILLI = 1_000_000L

    suspend fun <T> timed(
        log: Logger,
        job: String,
        organization: UUID,
        nanoTime: () -> Long = System::nanoTime,
        block: suspend () -> T
    ): T {
        val start = nanoTime()
        try {
            return block()
        } finally {
            log.info("{}: organización {} en {} ms", job, organization, (nanoTime() - start) / NANOS_PER_MILLI)
        }
    }
}
```

Reemplazar, en cada barrido, la línea de la izquierda por la de la derecha, y agregar `import sgspe.common.SweepTiming`
en orden alfabético entre los `import sgspe.common.*` de los tres archivos que no están en `common`:

| Archivo | Línea actual | Línea nueva |
|---|---|---|
| `deadline/DeadlineJob.kt` (`DeadlineSweep.run`) | `written += asPlatform(organization) { refresh(organization) }` | `written += SweepTiming.timed(log, "DeadlineJob", organization) { asPlatform(organization) { refresh(organization) } }` |
| `warning/WarningRulesJob.kt` (`WarningSweep.run`) | `created += asPlatform(organization) { evaluate(organization) }` | `created += SweepTiming.timed(log, "WarningRulesJob", organization) { asPlatform(organization) { evaluate(organization) } }` |
| `stakeholder/StakeholderFollowUpJob.kt` (`FollowUpSweep.run`) | `written += asPlatform(organization) { followUp(organization) }` | `written += SweepTiming.timed(log, "StakeholderFollowUpJob", organization) { asPlatform(organization) { followUp(organization) } }` |
| `common/DerivedFieldsJob.kt` (`DerivedSweep.run`) | `written += asPlatform(organization) { recalculator.recalculate(organization, since) }` | `written += SweepTiming.timed(log, "DerivedFieldsJob", organization) { asPlatform(organization) { recalculator.recalculate(organization, since) } }` |

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `SweepTimingTest` con 2 pruebas y las pruebas de los cuatro barridos (`DeadlineSweepTest`,
`WarningSweepTest`, `FollowUpSweepTest`, `DerivedSweepTest`, las que existan) en verde: el bloque devuelve lo mismo y la
excepción de una organización sigue llegando a su `catch`.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/SweepTiming.kt src/test/kotlin/sgspe/common/SweepTimingTest.kt \
  src/main/kotlin/sgspe/deadline/DeadlineJob.kt src/main/kotlin/sgspe/warning/WarningRulesJob.kt \
  src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt src/main/kotlin/sgspe/common/DerivedFieldsJob.kt
git commit -F - <<'MSG'
feat: duración por organización en los barridos de los trabajos

JOB-01, JOB-04, el seguimiento de grupos de interés y los derivados dejan una línea de log por
organización con su duración, también si falla: los tenants se recorren en serie y la medición con
varios clientes la necesita (ASM-109).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 15: UPS-10: el interruptor, su prueba y la siembra

**Files:**
- Modify: `src/main/resources/application-prod.yml`, `src/test/kotlin/sgspe/ProdProfileTest.kt`, `docs/develop/README.md` (§5)
- Create: `src/test/kotlin/sgspe/contract/SeparateProvisioningApiTest.kt`

**Interfaces:**
- Consumes: `wasichai.core.platform.WasichaiOrganizationsProperties(separateProvisioning: Boolean)` y la tabla
  `<metadata>.permissions (role_id, object_id, action)` con `<metadata>.roles (id, organization_id, name)` de wasichai 0.6.0
  (WAS-ADR-0055 y su `SeparateProvisioningApiTest`); `SgspeApiTest.provisionTenant()`, `me()`, `PASSWORD`.
- Produces: `wasichai.organizations.separate-provisioning: true` en producción; el procedimiento de siembra en
  `docs/develop/README.md` §5, que sigue el coordinador en la Tarea 17 y la operación en cada despliegue.

En desarrollo y en las pruebas el interruptor queda apagado (el valor por defecto): `TenantIsolationApiTest` y el tenant de
desarrollo crean organizaciones con el `ADMIN`. La prueba de integración lo enciende con `@TestPropertySource`, como la de
wasichai, y siembra la fila con `NOT EXISTS` (la base de pruebas se reutiliza entre corridas). Con el interruptor apagado,
esa fila no cambia nada para las demás clases (WAS-ADR-0055).

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/ProdProfileTest.kt`, reemplazar:

```kotlin
import wasichai.core.platform.WasichaiLoginProperties
```

por:

```kotlin
import wasichai.core.platform.WasichaiLoginProperties
import wasichai.core.platform.WasichaiOrganizationsProperties
```

y, antes de:

```kotlin
    @Test
    fun `revoca los tokens`() {
```

agregar:

```kotlin
    // UPS-10 (WAS-ADR-0055): el ADMIN de un cliente no crea ni borra tenants; ligado a la clase de wasichai, como los de UPS-09
    @Test
    fun `separa el alta de tenants de la administracion de cada uno`() {
        assertTrue(bound<WasichaiOrganizationsProperties>("wasichai.organizations").separateProvisioning)
    }

```

Crear `src/test/kotlin/sgspe/contract/SeparateProvisioningApiTest.kt`:

```kotlin
package sgspe.contract

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.TestPropertySource
import sgspe.SgspeApiTest
import wasichai.core.platform.WasichaiSchemas
import java.util.UUID

// UPS-10 resuelta con wasichai 0.6.0 (WAS-ADR-0055, ASM-109): con wasichai.organizations.separate-provisioning encendido, como
// en application-prod.yml, crear y borrar tenants exige MANAGE_TENANTS. el ADMIN de la organización operadora (la sembrada
// por la prueba) la recibe fuera de banda, con la fila de permissions que siembra quien opera la base (docs/develop/README.md);
// el ADMIN de un tenant cliente conserva su tenant y recibe 403 en las dos rutas. la fila no cambia nada con el interruptor
// apagado (el valor de las demás clases), y se siembra una sola vez
@TestPropertySource(properties = ["wasichai.organizations.separate-provisioning=true"])
class SeparateProvisioningApiTest : SgspeApiTest() {
    @Autowired
    private lateinit var db: DatabaseClient

    @Autowired
    private lateinit var schemas: WasichaiSchemas

    // la siembra de docs/develop/README.md: MANAGE_TENANTS para el rol ADMIN de la organización operadora, sin objeto
    @BeforeEach
    fun seedOperator() {
        val organization = UUID.fromString(me()["organizationId"].asString())
        runBlocking {
            db
                .sql(
                    """
                    INSERT INTO ${schemas.metadata}.permissions (role_id, object_id, action)
                    SELECT r.id, NULL, 'MANAGE_TENANTS' FROM ${schemas.metadata}.roles r
                    WHERE r.organization_id = :organization AND r.name = 'ADMIN'
                      AND NOT EXISTS (SELECT 1 FROM ${schemas.metadata}.permissions p WHERE p.role_id = r.id AND p.action = 'MANAGE_TENANTS')
                    """.trimIndent()
                ).bind("organization", organization)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
    }

    @Test
    fun `el ADMIN de la operadora crea un tenant y el ADMIN del cliente no crea ni borra tenants`() {
        // provisionTenant pide 201 con el token del ADMIN de la operadora
        val client = provisionTenant()
        val body =
            mapOf(
                "name" to "Organización intrusa",
                "slug" to uniqueName("intrusa"),
                "adminEmail" to "${uniqueName("intrusa")}@sgspe.test",
                "adminPassword" to PASSWORD,
                "adminDisplayName" to "Intrusa"
            )
        assertEquals(HttpStatus.FORBIDDEN, exchange("POST", "/api/organizations", body, client.token).status)
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/organizations/current", null, client.token).status)
        // su tenant sigue ahí y lo administra: renombrarlo es MANAGE_ORGANIZATION
        assertEquals(HttpStatus.OK, exchange("PUT", "/api/organizations/current", mapOf("name" to "Cliente Ejemplo"), client.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/auth/me", null, client.token).status)
    }
}
```

- [ ] **Paso 2: Correr la prueba unitaria para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ProdProfileTest'
```

Esperado: FAIL en `separa el alta de tenants de la administracion de cada uno` (`separateProvisioning` es `false`).

- [ ] **Paso 3: El interruptor y la siembra documentada**

En `src/main/resources/application-prod.yml`, reemplazar:

```yaml
  seed:
    # nunca el admin@wasichai.local de desarrollo
    dev: false
```

por:

```yaml
  seed:
    # nunca el admin@wasichai.local de desarrollo
    dev: false
  organizations:
    # UPS-10 (WAS-ADR-0055, ASM-109): crear y borrar tenants exige MANAGE_TENANTS, que solo tiene el rol ADMIN de la
    # organización operadora, sembrado fuera de banda antes de desplegar (docs/develop/README.md). el ADMIN de un cliente
    # conserva usuarios, roles, cuentas de servicio y el nombre de su tenant
    separate-provisioning: true
```

En `docs/develop/README.md`, al final de §5 (antes de `## 6. Pruebas`), agregar:

````markdown
### Varios clientes: la siembra de `MANAGE_TENANTS` (ASM-109)

En producción `wasichai.organizations.separate-provisioning` está encendido (`application-prod.yml`): crear y borrar
tenants exige `MANAGE_TENANTS`, que nadie tiene hasta que quien opera la base siembra el primer titular (WAS-ADR-0055). Se
hace **una sola vez, antes del primer despliegue con el interruptor encendido**, para el rol `ADMIN` de la organización
operadora (la del `SUPER_ADMIN`). Nunca va en `roles.json`, que se aplica a cada tenant cliente, y ninguna cuenta de
servicio la recibe:

```sql
INSERT INTO <esquema de metadata>.permissions (role_id, object_id, action)
SELECT r.id, NULL, 'MANAGE_TENANTS'
FROM <esquema de metadata>.roles r JOIN <esquema de metadata>.organizations o ON o.id = r.organization_id
WHERE o.slug = '<slug de la organización operadora>' AND r.name = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM <esquema de metadata>.permissions p WHERE p.role_id = r.id AND p.action = 'MANAGE_TENANTS');
```

El esquema de metadata es `wasichai.database.metadata-schema`. Con el interruptor encendido y sin la fila, nadie crea
tenants hasta sembrarla. Comprobación: `GET /api/auth/me/permissions` del `ADMIN` operador lista `MANAGE_TENANTS` en
`capabilities`, y el de un cliente no. `SeparateProvisioningApiTest` prueba lo mismo.
````

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
yarn format:check
```

Esperado: `BUILD SUCCESSFUL` (`ProdProfileTest` con 13 pruebas; `SeparateProvisioningApiTest` compila); prettier sin
cambios en `application-prod.yml` (si los hay, `yarn format` antes del commit).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/resources/application-prod.yml src/test/kotlin/sgspe/ProdProfileTest.kt \
  src/test/kotlin/sgspe/contract/SeparateProvisioningApiTest.kt docs/develop/README.md
git commit -F - <<'MSG'
feat: el alta de tenants separada de su administración en producción

application-prod.yml enciende wasichai.organizations.separate-provisioning: crear y borrar tenants
exige MANAGE_TENANTS, sembrado fuera de banda en el ADMIN de la operadora; el ADMIN de un cliente
recibe 403. La siembra queda en develop/README.md (UPS-10, ASM-109).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.contract.SeparateProvisioningApiTest` (un contexto de Spring propio, por
`@TestPropertySource`).

---

### Tarea 16: `provision_tenant.py` y `upgrade_tenants.py`

**Files:**
- Create: `model/tenant_steps.py`, `model/provision_tenant.py`, `model/upgrade_tenants.py`, `model/tenants.example.json`,
  `model/test_provision_tenant.py`, `model/test_upgrade_tenants.py`
- Modify: `model/fake_core.py` (`POST /api/organizations`), `docs/develop/README.md` (§5)

**Interfaces:**
- Consumes: `apply.main(argv)`, `apply_roles.main(argv)`, `load_ubigeo.main(argv)` (devuelven el código de salida y
  aceptan `--core`, `--email`, `--password`); `core_client.Client` (`login`, `post`) y `CoreError` (`status`); `FakeCore`.
- Produces: `tenant_steps.STEPS` y `tenant_steps.run_steps(core, email, password, steps=STEPS) -> str | None` (el paso que
  falló); `provision_tenant.main(argv=None, steps=..., ask=getpass.getpass) -> int`;
  `upgrade_tenants.main(argv=None, steps=..., ask=getpass.getpass) -> int`, `load_inventory(path)` y
  `password_variable(slug)`; `FakeCore(refuse_organizations=False)` con `organizations` (los cuerpos recibidos).

Las contraseñas llegan por variable de entorno o por consola (`getpass`, sin eco), nunca por argumento ni archivo: la de
cada paso viaja en el `argv` de `main` dentro del mismo proceso, que no se ve en la lista de procesos ni en la salida. El
inventario real no se versiona; `tenants.example.json` muestra la forma con datos ficticios.

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `model/fake_core.py`, reemplazar:

```python
                 existing_actions=()):
```

por:

```python
                 existing_actions=(), refuse_organizations=False):
```

reemplazar:

```python
        self.requests = []
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeCoreHandler)
```

por:

```python
        self.refuse_organizations = refuse_organizations  # POST /api/organizations answers 403 (sin MANAGE_TENANTS)
        self.organizations = []  # the bodies of POST /api/organizations
        self.requests = []
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), FakeCoreHandler)
```

y, en `_script`, antes de la primera línea `        if RECORDS.match(path) and method in ("GET", "POST"):`, agregar:

```python
        if path == "/api/organizations" and method == "POST":
            if self.refuse_organizations:
                return 403, {"detail": "Missing permission MANAGE_TENANTS"}
            self.organizations.append(body)
            return 201, {"id": str(uuid.uuid4()), "slug": body.get("slug"), "name": body.get("name")}
```

Crear `model/test_provision_tenant.py`:

```python
"""Pruebas de provision_tenant.py contra un FakeCore (security.md §4.1, ASM-109): el alta del tenant con las credenciales del
operador, los cuatro pasos en orden con las del administrador nuevo, las contraseñas fuera de la salida y un paso que falla.

Correr: cd model && python3 -m unittest -v test_provision_tenant
"""
import io
import os
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

import provision_tenant
from fake_core import FakeCore

OPERATOR_SECRET = "clave-del-operador-ficticia"
ADMIN_SECRET = "clave-inicial-ficticia"


class ProvisionTenantTests(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)
        self.calls = []

    def step(self, name, code=0):
        def run(argv):
            self.calls.append((name, argv))
            return code
        return (name, run)

    def steps(self, failing=None):
        names = ("apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py")
        return tuple(self.step(n, 1 if n == failing else 0) for n in names)

    def run_cli(self, steps, env=None, core=None):
        args = ["--core", (core or self.core).base_url, "--operator-email", "operador@sgspe.test", "--name", "Cliente Ejemplo",
                "--slug", "cliente-ejemplo", "--admin-email", "admin-cliente-ejemplo@sgspe.test"]
        env = {"SGSPE_OPERATOR_PASSWORD": OPERATOR_SECRET, "SGSPE_TENANT_ADMIN_PASSWORD": ADMIN_SECRET} if env is None else env
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.dict(os.environ, env, clear=False), redirect_stdout(out), redirect_stderr(err):
            code = provision_tenant.main(args, steps=steps, ask=lambda prompt: "")
        return code, out.getvalue(), err.getvalue()

    def test_crea_el_tenant_como_el_operador_y_corre_los_cuatro_pasos_como_su_administrador(self):
        code, out, err = self.run_cli(self.steps())
        self.assertEqual(code, 0, err)
        login = [b for m, p, _, b in self.core.requests if p == "/api/auth/login"]
        self.assertEqual(login, [{"email": "operador@sgspe.test", "password": OPERATOR_SECRET}])
        self.assertEqual(self.core.organizations, [{
            "name": "Cliente Ejemplo", "slug": "cliente-ejemplo", "adminEmail": "admin-cliente-ejemplo@sgspe.test",
            "adminPassword": ADMIN_SECRET, "adminDisplayName": "Administración del tenant"}])
        self.assertEqual([n for n, _ in self.calls], ["apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py"])
        for _, argv in self.calls:
            self.assertEqual(argv[argv.index("--email") + 1], "admin-cliente-ejemplo@sgspe.test")
            self.assertEqual(argv[argv.index("--password") + 1], ADMIN_SECRET)
        self.assertIn("cambiar la contraseña inicial", out)

    def test_ninguna_contrasena_sale_en_la_salida(self):
        for failing in (None, "apply_roles.py"):
            self.calls.clear()
            _, out, err = self.run_cli(self.steps(failing))
            for secret in (OPERATOR_SECRET, ADMIN_SECRET):
                self.assertNotIn(secret, out + err)

    def test_un_paso_que_falla_detiene_los_siguientes_y_lo_nombra(self):
        code, _, err = self.run_cli(self.steps("apply_roles.py"))
        self.assertEqual(code, 1)
        self.assertEqual([n for n, _ in self.calls], ["apply.py", "apply_roles.py"])
        self.assertIn("falló apply_roles.py", err)

    def test_sin_manage_tenants_no_crea_nada_ni_corre_pasos(self):
        refusing = FakeCore(refuse_organizations=True)
        self.addCleanup(refusing.stop)
        code, _, err = self.run_cli(self.steps(), core=refusing)
        self.assertEqual(code, 1)
        self.assertEqual(self.calls, [])
        self.assertIn("403", err)

    def test_sin_contrasenas_no_llama_a_core(self):
        code, _, _ = self.run_cli(self.steps(), env={"SGSPE_OPERATOR_PASSWORD": "", "SGSPE_TENANT_ADMIN_PASSWORD": ""})
        self.assertEqual(code, 2)
        self.assertEqual(self.core.requests, [])

    def test_la_contrasena_no_se_acepta_como_argumento(self):
        with redirect_stderr(io.StringIO()):
            code = provision_tenant.main(["--core", self.core.base_url, "--operator-email", "o@sgspe.test", "--name", "N",
                                          "--slug", "n", "--admin-email", "a@sgspe.test", "--password", "x"],
                                         steps=self.steps(), ask=lambda prompt: "")
        self.assertEqual(code, 2)
        self.assertEqual(self.core.requests, [])


if __name__ == "__main__":
    unittest.main()
```

Crear `model/test_upgrade_tenants.py`:

```python
"""Pruebas de upgrade_tenants.py (security.md §4.1, ASM-109): cada tenant del inventario, con su administrador y en orden;
uno que falla no detiene a los demás, el resumen lo nombra y la salida es 1; las contraseñas no salen.

Correr: cd model && python3 -m unittest -v test_upgrade_tenants
"""
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from unittest import mock

import upgrade_tenants

INVENTORY = {"tenants": [{"slug": "cliente-uno", "adminEmail": "admin-cliente-uno@sgspe.test"},
                         {"slug": "cliente-dos", "adminEmail": "admin-cliente-dos@sgspe.test"},
                         {"slug": "cliente-tres", "adminEmail": "admin-cliente-tres@sgspe.test"}]}
SECRETS = {"SGSPE_TENANT_PASSWORD_CLIENTE_UNO": "clave-uno-ficticia", "SGSPE_TENANT_PASSWORD_CLIENTE_DOS": "clave-dos-ficticia",
           "SGSPE_TENANT_PASSWORD_CLIENTE_TRES": "clave-tres-ficticia"}


class UpgradeTenantsTests(unittest.TestCase):
    def setUp(self):
        self.calls = []

    def inventory(self, data=INVENTORY):
        handle = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
        with handle:
            json.dump(data, handle)
        self.addCleanup(os.unlink, handle.name)
        return handle.name

    def steps(self, failing=None):
        """Los cuatro pasos; `failing` es (correo, paso) que responde 1."""
        def make(name):
            def run(argv):
                email = argv[argv.index("--email") + 1]
                self.calls.append((email, name))
                return 1 if failing == (email, name) else 0
            return (name, run)
        return tuple(make(n) for n in ("apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py"))

    def run_cli(self, path, steps):
        out, err = io.StringIO(), io.StringIO()
        with mock.patch.dict(os.environ, SECRETS, clear=False), redirect_stdout(out), redirect_stderr(err):
            code = upgrade_tenants.main(["--inventory", path, "--core", "http://127.0.0.1:9"], steps=steps, ask=lambda p: "")
        return code, out.getvalue(), err.getvalue()

    def test_recorre_cada_tenant_con_su_administrador_y_en_orden(self):
        code, out, err = self.run_cli(self.inventory(), self.steps())
        self.assertEqual(code, 0, err)
        self.assertEqual(len(self.calls), 12)
        self.assertEqual([n for e, n in self.calls[:4]], ["apply.py", "apply_roles.py", "apply.py --workflows", "load_ubigeo.py"])
        self.assertEqual([e for e, _ in self.calls[::4]], [t["adminEmail"] for t in INVENTORY["tenants"]])
        self.assertIn("resumen: 3 de 3 tenants al día", out)

    def test_un_tenant_que_falla_no_detiene_a_los_demas_y_el_resumen_lo_nombra(self):
        code, out, err = self.run_cli(self.inventory(), self.steps(("admin-cliente-dos@sgspe.test", "apply_roles.py")))
        self.assertEqual(code, 1)
        self.assertEqual([e for e, n in self.calls if n == "apply.py"], [t["adminEmail"] for t in INVENTORY["tenants"]])
        self.assertIn("resumen: 2 de 3 tenants al día", out)
        self.assertIn("cliente-dos: falló apply_roles.py", out)
        for secret in SECRETS.values():
            self.assertNotIn(secret, out + err)

    def test_un_inventario_invalido_no_corre_nada(self):
        for data in ({}, {"tenants": []}, {"tenants": [{"slug": "Con Espacios", "adminEmail": "a@sgspe.test"}]},
                     {"tenants": [{"slug": "bien", "adminEmail": "sin-arroba"}]}):
            code, _, _ = self.run_cli(self.inventory(data), self.steps())
            self.assertEqual(code, 2, data)
        self.assertEqual(self.calls, [])

    def test_la_variable_de_la_contrasena_sale_del_slug(self):
        self.assertEqual(upgrade_tenants.password_variable("cliente-uno"), "SGSPE_TENANT_PASSWORD_CLIENTE_UNO")

    def test_el_ejemplo_versionado_es_un_inventario_valido_y_ficticio(self):
        tenants, errors = upgrade_tenants.load_inventory(os.path.join(os.path.dirname(os.path.abspath(__file__)), "tenants.example.json"))
        self.assertEqual(errors, [])
        self.assertTrue(all(email.endswith("@sgspe.test") for _, email in tenants))


if __name__ == "__main__":
    unittest.main()
```

Crear `model/tenants.example.json`:

```json
{
  "tenants": [
    { "slug": "cliente-ejemplo-a", "adminEmail": "admin-cliente-ejemplo-a@sgspe.test" },
    { "slug": "cliente-ejemplo-b", "adminEmail": "admin-cliente-ejemplo-b@sgspe.test" }
  ]
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe/model && python3 -m unittest test_provision_tenant test_upgrade_tenants 2>&1 | tail -3
```

Esperado: `ModuleNotFoundError: No module named 'provision_tenant'` (y `upgrade_tenants`).

- [ ] **Paso 3: Implementar**

Crear `model/tenant_steps.py`:

```python
"""Los pasos 2 a 5 de security.md §4.1 sobre un tenant, en orden, como su administrador (ASM-109).

Los comparten provision_tenant.py (alta) y upgrade_tenants.py (subida de fase de cada tenant). Cada paso es la función
main(argv) de su script, llamada en el mismo proceso: la contraseña viaja en argv solo dentro de este proceso, nunca en la
línea de comandos ni en la salida. Solo biblioteca estándar.
"""
import apply
import apply_roles
import load_ubigeo

# (nombre que se informa, función que recibe el argv de conexión y devuelve el código de salida)
STEPS = (
    ("apply.py", lambda argv: apply.main(argv)),
    ("apply_roles.py", lambda argv: apply_roles.main(argv)),
    ("apply.py --workflows", lambda argv: apply.main(argv + ["--workflows"])),
    ("load_ubigeo.py", lambda argv: load_ubigeo.main(argv)),
)


def run_steps(core, email, password, steps=STEPS):
    """Corre los pasos en orden y se detiene en el primero que falla: su nombre, o None si todos terminaron en 0."""
    argv = ["--core", core, "--email", email, "--password", password]
    for name, step in steps:
        print(f"== {name} ({email})", flush=True)
        if step(list(argv)) != 0:
            return name
    return None
```

Crear `model/provision_tenant.py`:

```python
#!/usr/bin/env python3
"""Da de alta un tenant cliente de SGSPE y lo aprovisiona (security.md §4.1, ASM-109).

1. Con las credenciales del operador (una persona del rol que tiene MANAGE_TENANTS en la organización operadora, nunca una
   cuenta de servicio), llama POST /api/organizations con el nombre, el slug y el primer administrador.
2. Con las credenciales del administrador nuevo corre, en este orden, apply.py, apply_roles.py, apply.py --workflows y
   load_ubigeo.py (tenant_steps.py).
3. Termina recordando que el cliente debe cambiar la contraseña inicial de su administrador.

Las contraseñas llegan por variable de entorno (SGSPE_OPERATOR_PASSWORD y SGSPE_TENANT_ADMIN_PASSWORD) o por consola, nunca
por argumento ni archivo, y no se imprimen.

Correr: python3 provision_tenant.py --core URL --operator-email E --name N --slug S --admin-email A [--admin-name D]
Salida: 0 si va bien; 1 si core rechaza el alta o falla un paso (lo nombra); 2 si faltan datos.
"""
import argparse
import getpass
import os
import sys

import tenant_steps
from core_client import Client, CoreError

OPERATOR_PASSWORD = "SGSPE_OPERATOR_PASSWORD"
ADMIN_PASSWORD = "SGSPE_TENANT_ADMIN_PASSWORD"


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Da de alta un tenant de SGSPE y lo aprovisiona.")
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--operator-email", required=True)
    p.add_argument("--name", required=True)
    p.add_argument("--slug", required=True)
    p.add_argument("--admin-email", required=True)
    p.add_argument("--admin-name", default="Administración del tenant")
    return p.parse_args(argv)


def secret(variable, prompt, ask=getpass.getpass):
    """La contraseña de la variable de entorno o, si no está, la que se escribe en la consola (sin eco)."""
    value = os.environ.get(variable)
    return value if value else ask(prompt)


def provision(client, args, admin_password):
    """POST /api/organizations como el operador ya autenticado; lanza CoreError si core lo rechaza."""
    client.post("/api/organizations", {
        "name": args.name,
        "slug": args.slug,
        "adminEmail": args.admin_email,
        "adminPassword": admin_password,
        "adminDisplayName": args.admin_name,
    })


def main(argv=None, steps=tenant_steps.STEPS, ask=getpass.getpass):
    try:
        args = _parse_args(sys.argv[1:] if argv is None else argv)
    except SystemExit:
        return 2
    operator_password = secret(OPERATOR_PASSWORD, f"Contraseña de {args.operator_email}: ", ask)
    admin_password = secret(ADMIN_PASSWORD, f"Contraseña inicial de {args.admin_email}: ", ask)
    if not operator_password or not admin_password:
        print("error: faltan las contraseñas del operador o del administrador nuevo", file=sys.stderr)
        return 2
    client = Client(args.core)
    try:
        client.login(args.operator_email, operator_password)
        provision(client, args, admin_password)
    except CoreError as e:
        print(f"error: el alta del tenant {args.slug} falló: {e.status}", file=sys.stderr)
        return 1
    print(f"tenant {args.slug} creado; se aprovisiona como {args.admin_email}", flush=True)
    failed = tenant_steps.run_steps(args.core, args.admin_email, admin_password, steps)
    if failed:
        print(f"error: el tenant {args.slug} quedó a medias: falló {failed}; corrija y corra upgrade_tenants.py", file=sys.stderr)
        return 1
    print(f"listo: el administrador {args.admin_email} debe cambiar la contraseña inicial en su primer acceso "
          "(security.md §4.1)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

Crear `model/upgrade_tenants.py`:

```python
#!/usr/bin/env python3
"""Sube de fase cada tenant de un inventario: repite en cada uno los pasos 2 a 5 de security.md §4.1 (ASM-109).

wasichai no lista organizaciones, así que el inventario es del operador: un JSON que no se versiona, con la forma de
tenants.example.json ({"tenants": [{"slug": ..., "adminEmail": ...}]}). La contraseña del administrador de cada tenant llega
por la variable SGSPE_TENANT_PASSWORD_<SLUG> (el slug en mayúsculas, con guiones como guiones bajos) o por consola; nunca por
argumento ni archivo, y no se imprime. Un tenant que falla se informa y no detiene a los demás.

Correr: python3 upgrade_tenants.py --inventory RUTA [--core URL]
Salida: 0 si todos terminaron; 1 si alguno falló (el resumen dice qué paso en cuál); 2 si el inventario no es válido.
"""
import argparse
import getpass
import json
import os
import re
import sys

import tenant_steps

SLUG = re.compile(r"^[a-z0-9][a-z0-9-]{0,62}$")


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Sube de fase cada tenant de un inventario.")
    p.add_argument("--inventory", required=True)
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    return p.parse_args(argv)


def load_inventory(path):
    """Los tenants del inventario como (slug, correo), o una lista de errores."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:
        return None, [f"{path}: {e}"]
    tenants = data.get("tenants") if isinstance(data, dict) else None
    if not isinstance(tenants, list) or not tenants:
        return None, [f"{path}: debe tener una lista 'tenants' no vacía"]
    errors, result = [], []
    for i, tenant in enumerate(tenants):
        slug = tenant.get("slug") if isinstance(tenant, dict) else None
        email = tenant.get("adminEmail") if isinstance(tenant, dict) else None
        if not isinstance(slug, str) or not SLUG.fullmatch(slug):
            errors.append(f"tenants[{i}]: slug inválido")
        if not isinstance(email, str) or "@" not in email:
            errors.append(f"tenants[{i}]: adminEmail inválido")
        result.append((slug, email))
    return (None, errors) if errors else (result, [])


def password_variable(slug):
    return "SGSPE_TENANT_PASSWORD_" + slug.upper().replace("-", "_")


def main(argv=None, steps=tenant_steps.STEPS, ask=getpass.getpass):
    try:
        args = _parse_args(sys.argv[1:] if argv is None else argv)
    except SystemExit:
        return 2
    tenants, errors = load_inventory(args.inventory)
    if errors:
        for e in errors:
            print(f"error: {e}", file=sys.stderr)
        return 2
    failures = []
    for slug, email in tenants:
        password = os.environ.get(password_variable(slug)) or ask(f"Contraseña de {email} ({slug}): ")
        failed = "login" if not password else tenant_steps.run_steps(args.core, email, password, steps)
        if failed:
            failures.append((slug, failed))
            print(f"error: el tenant {slug} falló en {failed}; se sigue con los demás", file=sys.stderr)
    print(f"resumen: {len(tenants) - len(failures)} de {len(tenants)} tenants al día")
    for slug, step in failures:
        print(f"  {slug}: falló {step}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
```

```bash
cd ~/IdeaProjects/sgspe/model && chmod +x provision_tenant.py upgrade_tenants.py
```

En `docs/develop/README.md` §5, reemplazar
`python3 apply.py                        # objetos, acciones, relaciones e índices. En F4: "done: 332 created, 21 updated, 0 skipped"`
por
`python3 apply.py                        # objetos, acciones, relaciones e índices. En F4: "done: 333 created, 21 updated, 0 skipped"`,
y reemplazar:

```markdown
- Un tenant nuevo se prepara con `POST /api/organizations` y después estos cuatro pasos, con el administrador del tenant.
  Los cuatro son idempotentes: una subida de fase los repite.
```

por:

````markdown
- Un tenant nuevo se prepara con `POST /api/organizations` y después estos cuatro pasos, con el administrador del tenant.
  Los cuatro son idempotentes: una subida de fase los repite. Desde el plan 4.4 lo hacen dos scripts (ASM-109):

  ```bash
  # alta: como la persona del operador con MANAGE_TENANTS; las contraseñas, por variable o por consola
  SGSPE_OPERATOR_PASSWORD=… SGSPE_TENANT_ADMIN_PASSWORD=… python3 provision_tenant.py --core https://… \
    --operator-email operador@… --name "Cliente Ejemplo" --slug cliente-ejemplo --admin-email admin-cliente-ejemplo@…
  # subida de fase de cada tenant del inventario (no versionado; la forma, en tenants.example.json)
  SGSPE_TENANT_PASSWORD_CLIENTE_EJEMPLO=… python3 upgrade_tenants.py --core https://… --inventory ~/tenants.json
  ```

  Un tenant que falla en `upgrade_tenants.py` se informa y no detiene a los demás; la salida es 1 y el resumen dice qué
  paso falló en cuál. El administrador de un tenant nuevo cambia la contraseña inicial en su primer acceso.
````

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe/model && python3 -m unittest test_provision_tenant test_upgrade_tenants -v 2>&1 | tail -3
python3 -m unittest 2>&1 | tail -1
```

Esperado: 11 pruebas en verde, y `OK` en la suite del modelo (221 pruebas: las 208 de `main`, 2 de la
Tarea 3 y 11 de esta).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add model/tenant_steps.py model/provision_tenant.py model/upgrade_tenants.py model/tenants.example.json \
  model/test_provision_tenant.py model/test_upgrade_tenants.py model/fake_core.py docs/develop/README.md
git commit -F - <<'MSG'
feat: alta y subida de fase de tenants por script

provision_tenant.py crea el tenant como el operador y corre apply.py, apply_roles.py,
apply.py --workflows y load_ubigeo.py como su administrador; upgrade_tenants.py los repite en cada
tenant de un inventario no versionado. Contraseñas por variable o consola, nunca en la salida
(ASM-109).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 17: Tenant de desarrollo y mediciones (coordinador)

**Files:**
- Ninguno versionado salvo, si la medición lo pide, `src/main/kotlin/sgspe/common/RequestMemoFilter.kt` (Paso 5) y la
  anotación del resultado, que va en la Tarea 18.

**Interfaces:**
- Consumes: las Tareas 3 y 7 integradas en la rama; la Tarea 14 para las líneas de duración; la Tarea 16 para los tenants
  de la medición; el túnel (puerto 5435) y el backend de desarrollo (8094).
- Produces: el tenant de desarrollo con `READ_ALL_PROJECTS` (el orden de despliegue de §4 de la especificación) y las cifras
  de §3.10 que la Tarea 18 anota en el roadmap.

**Solo el coordinador principal**: exige base, túnel y el tenant de desarrollo. Ningún subagente la ejecuta.

- [ ] **Paso 1: El tenant de desarrollo, antes del backend nuevo (coordinador)**

Con el backend de `main` todavía corriendo contra la base de desarrollo, desde la rama:

```bash
cd ~/IdeaProjects/sgspe/model
python3 apply.py                 # "done: 1 created, … updated, … skipped": la acción READ_ALL_PROJECTS
python3 apply_roles.py           # AUDITOR y PROJECT_ADMIN reciben la acción: "2 updated"
python3 apply.py --workflows     # sin cambios
```

Después se reinicia el backend desde la rama (`docs/develop/README.md` §4) y se comprueba:

```bash
TOKEN=$(curl -s localhost:8094/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@wasichai.local","password":"admin"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -s localhost:8094/api/roles -H "Authorization: Bearer $TOKEN" | python3 -c '
import json,sys
for r in json.load(sys.stdin):
    if r["name"] in ("AUDITOR","PROJECT_ADMIN"):
        print(r["name"], sorted({p.get("objectName") or "*" for p in r.get("permissions", []) if p["action"]=="READ_ALL_PROJECTS"}))'
```

Esperado: `AUDITOR ['project']` y `PROJECT_ADMIN ['project']`. Si el backend nuevo se arranca antes que los roles,
`AUDITOR` y `PROJECT_ADMIN` dejan de leer los proyectos sin asignación hasta que corre `apply_roles.py` (riesgo 5).

- [ ] **Paso 2: El volumen de la medición en `sgspe_test` (coordinador)**

Con el backend de la rama apuntado a `sgspe_test` (`WASICHAI_DB_NAME=sgspe_test`, el resto como en
`docs/develop/README.md` §2) y el modelo aplicado ahí, el admin crea por la API un proyecto plantilla, un grupo de interés y
una evaluación válidos (`POST /api/objects/project/records`, `.../stakeholder/records`, y la evaluación por
`POST /api/sgspe/stakeholders/{id}/assessments`), y una persona de prueba `analista-medicion@sgspe.test` con `ANALYST`
asignada al primer proyecto. Después, en `psql` contra `sgspe_test`, se clonan las filas plantilla (`:tpl_*` son sus ids;
`wasichai` es el esquema de metadata y `data` el de datos por defecto):

```sql
\set org '<id de la organización>'
SELECT name, physical_table FROM wasichai.custom_objects
 WHERE organization_id = :'org' AND name IN ('project', 'project_assignment', 'stakeholder', 'stakeholder_assessment') \gset t_
-- \gset deja una variable por fila: renómbrelas a :proj, :pa, :st y :sa según el name de cada una
CREATE TEMP TABLE medicion_proyectos AS
  SELECT gen_random_uuid() AS id, g FROM generate_series(1, 10) g;
INSERT INTO data.:"proj" SELECT (jsonb_populate_record(p, jsonb_build_object('id', m.id, 'code', 'MED-' || m.g, 'name', 'Proyecto de medición ' || m.g))).*
  FROM data.:"proj" p, medicion_proyectos m WHERE p.id = :'tpl_project';
CREATE TEMP TABLE medicion_grupos AS
  SELECT gen_random_uuid() AS id, m.id AS project FROM medicion_proyectos m, generate_series(1, 500);
INSERT INTO data.:"st" SELECT (jsonb_populate_record(s, jsonb_build_object('id', g.id, 'project', g.project))).*
  FROM data.:"st" s, medicion_grupos g WHERE s.id = :'tpl_stakeholder';
INSERT INTO data.:"sa" SELECT (jsonb_populate_record(a, jsonb_build_object('id', gen_random_uuid(), 'stakeholder', g.id))).*
  FROM data.:"sa" a, medicion_grupos g, generate_series(1, 10) WHERE a.id = :'tpl_assessment';
ANALYZE data.:"proj"; ANALYZE data.:"st"; ANALYZE data.:"sa"; ANALYZE data.:"pa";
```

Si un objeto tiene un único por organización (`uniqueConstraints` en `model.json`; el `code` de `project` ya se cambia),
se agrega su columna a `jsonb_build_object` con un valor distinto por fila. Resultado: 10 proyectos, 5 000 grupos de
interés y 50 000 evaluaciones.

- [ ] **Paso 3: `EXPLAIN ANALYZE` con y sin alcance (coordinador)**

Las dos lecturas que hace el store de wasichai para una lista paginada (50 filas, orden por id), sin el criterio y con el
de `ProjectReadCondition` para la persona de medición (`:me`, hoy en Lima):

```sql
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"sa" WHERE organization_id = :'org' ORDER BY id LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"sa" WHERE organization_id = :'org'
  AND ("stakeholder" IN (SELECT t."id" FROM data.:"st" t WHERE t.organization_id = :'org' AND t."project" IN (
    SELECT a."project" FROM data.:"pa" a WHERE a.organization_id = :'org' AND a."member_user_id" = :'me'
       AND a."active" IS TRUE AND (a."start_date" IS NULL OR a."start_date" <= CURRENT_DATE)
       AND (a."end_date" IS NULL OR a."end_date" >= CURRENT_DATE))))
  ORDER BY id LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"st" WHERE organization_id = :'org'
  AND ("project" IN (SELECT a."project" FROM data.:"pa" a WHERE a.organization_id = :'org' AND a."member_user_id" = :'me'
       AND a."active" IS TRUE AND (a."start_date" IS NULL OR a."start_date" <= CURRENT_DATE)
       AND (a."end_date" IS NULL OR a."end_date" >= CURRENT_DATE)))
  ORDER BY id LIMIT 50;
```

Criterio (§3.10): sin `Seq Scan` sobre la tabla del padre (`data.:"st"`) ni sobre la de asignaciones, y menos de 20 % más
de tiempo que la lectura sin alcance, en tres corridas. Además, con el backend de la rama y `curl`, la lista
`GET /api/objects/stakeholder_assessment/records?size=50` como la persona de medición y como el admin, cinco veces cada
una, para ver el costo añadido por la consulta de permisos de la exención (`ProjectReach.readsAll`, una por objeto y
lectura fuera de un comando). Si el criterio de `EXPLAIN` falla, un índice va en `model.json` (`indexes`), nunca con SQL
propio, en un commit aparte con su prueba de `test_model.py`; si lo que pesa es la consulta de permisos, el Paso 5.

- [ ] **Paso 4: Los trabajos con tres tenants (coordinador)**

En `sgspe_test`, dos tenants más con `provision_tenant.py` (el interruptor está apagado en desarrollo: el `ADMIN` sembrado
hace de operador), cada uno con una copia reducida del volumen del Paso 2. El backend de la rama, contra `sgspe_test`, con
los trabajos cada dos minutos:

```bash
SGSPE_JOBS_DEADLINE_CRON='0 */2 * * * *' SGSPE_JOBS_WARNING_RULES_CRON='30 */2 * * * *' \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun 2>&1 | tee /tmp/sgspe-medicion.log
grep -E '(DeadlineJob|WarningRulesJob): organización .* en [0-9]+ ms' /tmp/sgspe-medicion.log
```

Se anotan, por trabajo, la duración por tenant (mediana de tres vueltas) y la cuenta de §3.10: si la duración por tenant
por 20 clientes supera la mitad del intervalo más corto (JOB-04, cada hora: 30 minutos), se abre una tarea de backlog
para paralelizar con tope; 4.4 no paraleliza. El log de la medición no se versiona.

- [ ] **Paso 5: Solo si el Paso 3 lo pide: `RequestMemo` para toda petición de `/api`**

Si la consulta de permisos de la exención pesa (más de 20 % del costo de la lista, o una consulta de permisos por objeto
visible en el log de SQL), un subagente crea `src/main/kotlin/sgspe/common/RequestMemoFilter.kt` (compila contra Spring
7.0.9: `CoWebFilter` pasa el contexto de la corrutina al controlador):

```kotlin
package sgspe.common

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.CoWebFilter
import org.springframework.web.server.CoWebFilterChain
import org.springframework.web.server.ServerWebExchange

// abre RequestMemo para toda petición de /api (especificación 4.4 §3.10, solo si la medición lo pide): la pregunta de
// permisos de ProjectReach.readsAll y ConfidentialAttentionScope se hace una vez por petición y no una vez por objeto y
// lectura. CoWebFilter pasa el contexto de la corrutina al controlador (COROUTINE_CONTEXT_ATTRIBUTE de Spring), así que el
// memo llega a RecordService y a los RecordReadScope. el memo solo guarda lo que una petición no cambia: los permisos de quien
// llama y sus asignaciones (Assignments.isAssigned); una petición que crea una asignación y escribe en ese proyecto en la misma
// llamada no existe en la API (las asignaciones se escriben solas)
@Component
@Order(RequestMemoFilter.ORDER)
class RequestMemoFilter : CoWebFilter() {
    override suspend fun filter(
        exchange: ServerWebExchange,
        chain: CoWebFilterChain
    ) {
        if (!exchange.request.path
                .pathWithinApplication()
                .value()
                .startsWith("/api/")
        ) {
            return chain.filter(exchange)
        }
        RequestMemo.within { chain.filter(exchange) }
    }

    companion object {
        // después de la cadena de seguridad (-100) y del filtro de documentos (-99)
        const val ORDER = -98
    }
}
```

con una prueba de integración en `src/test/kotlin/sgspe/common/RequestMemoFilterApiTest.kt` que compare, con el log de
SQL encendido (`logging.level.io.r2dbc.postgresql.QUERY=DEBUG`), el número de consultas de permisos de una lista de
`stakeholder_assessment` como `ANALYST` asignado, antes (una por lectura) y después (una por petición), y el commit
`perf: RequestMemo para toda petición de /api` con el mismo formato. Si el Paso 3 no lo pide, este paso no se hace y el PR
lo dice.

---

### Tarea 18: `CLAUDE.md`, verificación final, push y PR

**Files:**
- Modify: `CLAUDE.md` («Dónde está cada cosa» y «Comandos»), `docs/roadmap.md` (la medición de la Tarea 17)

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: la rama `feat/fase-4-4a-alcance` en verde y el PR.

- [ ] **Paso 1: `CLAUDE.md` y el roadmap**

En `CLAUDE.md`, en «Dónde está cada cosa», reemplazar la fila:

```markdown
| QCS confidenciales (`ConfidentialAttentionScope`, un `RecordReadScope` que honra `ConfidentialRelease`) y la regla «mismo proyecto» (`ProjectLinks`, `ProjectLinkGuard`) | `src/main/kotlin/sgspe/scope/` |
```

por:

```markdown
| QCS confidenciales (`ConfidentialAttentionScope`, un `RecordReadScope` que honra `ConfidentialRelease`) y la regla «mismo proyecto» (`ProjectLinks`, `ProjectLinkGuard`) | `src/main/kotlin/sgspe/scope/` |
| Alcance de lectura por proyecto (`ProjectReadScope`, su condición pura `ProjectReadCondition`, la clasificación `ProjectScope.TENANT` y `readGroup`), documentos emitidos (`IssueDocumentFilter`, `DocumentReadByScope`) | `src/main/kotlin/sgspe/scope/` |
| «Mis proyectos» o todos (`ProjectReach`, acción `READ_ALL_PROJECTS`), duración por organización de los trabajos (`SweepTiming`) | `src/main/kotlin/sgspe/common/` |
| Alta y subida de fase por tenant (`provision_tenant.py`, `upgrade_tenants.py`, `tenant_steps.py`; inventario con la forma de `tenants.example.json`, sin versionar) | `model/` |
```

y en «Comandos», después de la fila `| Modelo | … |`, agregar:

```markdown
| Alta de un tenant (operador) | `cd model && SGSPE_OPERATOR_PASSWORD=… SGSPE_TENANT_ADMIN_PASSWORD=… python3 provision_tenant.py --core URL --operator-email … --name … --slug … --admin-email …` |
| Subida de fase de los tenants | `cd model && python3 upgrade_tenants.py --core URL --inventory RUTA` (contraseñas por `SGSPE_TENANT_PASSWORD_<SLUG>` o consola) |
```

En `docs/roadmap.md`, en la tabla de F4, agregar al final de la celda de «Estado» (antes del `|` final) la medición de la
Tarea 17, con este texto y las cifras medidas:
`` ; medición del plan 4.4 (2026-10-…): lista de 50 evaluaciones con alcance <a> ms frente a <b> ms sin él, sin barrido secuencial; JOB-01 <c> ms y JOB-04 <d> ms por tenant con tres tenants; <conclusión: «sin tarea de backlog» o «tarea de backlog para paralelizar abierta»> ``
(los valores entre `<>` son los que el coordinador midió; si el Paso 3 o el 4 no se pudieron hacer, se escribe «medición
pendiente» y el cierre de la Release 1 la sigue exigiendo).

- [ ] **Paso 2: Todo lo local**

```bash
cd ~/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
(cd model && python3 -m unittest 2>&1 | tail -1 && python3 apply.py --validate-only && python3 load_ubigeo.py --dry-run | tail -1)
python3 tools/check_traceability.py && yarn format:check
grep -rn "projectsOf\|todavía depende de él por\|hasta UPS-01" src || echo limpio
git status --short
```

Esperado: `BUILD SUCCESSFUL` con todas las unitarias en verde (las de `main` más 27 de este plan: 4 de `ProjectReachTest`,
7 de `ProjectReadConditionTest`, 6 de `ProjectReadScopeTest`, 3 de `IssueDocumentFilterTest`, 1 de `ExportProjectsTest`,
1 de `DependentsDeleteGuardTest`, 2 de `SweepTimingTest`, 2 de `ArchitectureTest` y 1 de `ProdProfileTest`); `OK` del
modelo (221); `ok: 298 requisitos, todos con su fila`; prettier sin cambios; `limpio`; un `git status` vacío tras el commit
del Paso 3.

- [ ] **Paso 3: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add CLAUDE.md docs/roadmap.md
git commit -F - <<'MSG'
docs: cierre del plan 4.4a con el alcance de lectura por proyecto

CLAUDE.md dice dónde viven ProjectReadScope, ProjectReach, SweepTiming y los scripts de tenants, y
cómo se corren; el roadmap anota la medición de la especificación 4.4 §3.10.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

- [ ] **Paso 4: La suite de integración (CI del PR o coordinador)**

Las clases de integración corren en la CI del PR con Testcontainers. Si el coordinador las quiere antes, por el túnel y
con `sgspe_sdd_test` libre, la suite entera (Tarea 13, lote 5). Esperado: verde, con `ProjectReadScopeApiTest`,
`SeparateProvisioningApiTest`, las dos matrices con sus columnas nuevas y las doce clases migradas. Si
`ProjectReadScopeApiTest` falla en una ruta de wasichai, no se mezcla la rama: se registra la diferencia de la plataforma
y se decide con el usuario.

- [ ] **Paso 5: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin feat/fase-4-4a-alcance
gh pr create --base main --title "feat: fase 4, plan 4.4a: alcance de lectura por proyecto y varios clientes" --body-file - <<'MSG'
## Qué trae

- Alcance de lectura por proyecto (UPS-01 por proyecto, ADR-0029, ASM-106): `ProjectReadScope`, un `RecordReadScope` con
  su condición SQL pura (`ProjectReadCondition`) sobre las asignaciones vigentes de quien llama, en todas las rutas de
  wasichai; `IssueDocumentFilter` cubre las dos lecturas de documentos emitidos (UPS-28).
- Acción declarada `READ_ALL_PROJECTS` sobre `project`, solo para `AUDITOR` y `PROJECT_ADMIN` (confirmado por el usuario
  el 2026-10-10); `ProjectReach` decide «mis proyectos» o todos para el alcance, los tableros, el reporte y las
  exportaciones, sin literales de rol.
- `ProjectScope.TENANT` y la regla de `ArchitectureTest`: cada objeto del modelo clasificado en un solo grupo.
- Semántica de errores (ASM-108): `404` fuera del alcance, `400` de wasichai en un `RELATION` hacia un registro ilegible,
  `404` en un vínculo M2M; `NOT_ASSIGNED` para quien lee sin estar asignado; `HAS_DEPENDENTS` sin nombrar al dependiente.
- Varios clientes (UPS-10, ASM-109): `separate-provisioning` en producción, la siembra de `MANAGE_TENANTS` documentada,
  `provision_tenant.py` y `upgrade_tenants.py`, y la duración por organización de los trabajos.
- Documentos: IDs ASM-106 a ASM-109 y UPS-28, ADR-0029, `security.md` §9.7 y los demás; la especificación cierra su
  pregunta abierta y corrige el `404` de un vínculo M2M.

## Pruebas

- Unitarias: las de `main` y 27 nuevas; modelo: 221 (13 nuevas).
- Integración (esta CI): `ProjectReadScopeApiTest`, `SeparateProvisioningApiTest`, las columnas «otro proyecto» de
  `PermissionMatrixApiTest` y «no asignado» de `WorkflowMatrixApiTest`, y la suite migrada en cuatro lotes.

## Para revisar

- Decisión 7 del plan: el `field` de `409 HAS_DEPENDENTS` sigue siendo `<objeto>.<campo>` (contrato que usa el portal);
  solo el `detail` deja de nombrar al dependiente. ¿Lo cambia 4.4b?
- Decisión 14: la vista previa de una importación de predios responde `404` a quien no lee el proyecto.
- Decisión 2: un vínculo M2M a un registro ilegible es `404`, no `400`; la especificación y `api.md` ya lo dicen.
- Objetos creados fuera de `model.json` (por `/admin`) quedan sin clasificar: nadie que no sea `ADMIN` los lee.
- Medición de la Tarea 17: lo que quedó anotado en el roadmap; el `WebFilter` de `RequestMemo`, hecho o no.
- Orden de despliegue: `apply.py` y `apply_roles.py` en cada tenant **antes** del backend nuevo.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

## Resumen

| Tarea | Entrega | Grupo | Integración (CI del PR) |
|---|---|---|---|
| 1 | IDs, ADR-0029, especificación cerrada y corregida | A | — |
| 2 | El diseño por proyecto en `security.md` y los demás documentos | A | — |
| 3 | `READ_ALL_PROJECTS` en el modelo y los roles | B | — |
| 4 | `ProjectScope.TENANT`, `ReadGroup` y la regla de clasificación | A | — |
| 5 | `ProjectReach` | A | — |
| 6 | `ProjectReadCondition` | B | — |
| 7 | `ProjectReadScope` | C | (la 11) |
| 8 | Documentos emitidos e `IssueAccessByScope` con `404` | A | `IssueDocumentApiTest` |
| 9 | `ProjectReach` en tableros, reporte y exportaciones | B | `DashboardsApiTest`, `AttentionReportApiTest`, `*RegisterApiTest` |
| 10 | `HAS_DEPENDENTS` sin nombrar al dependiente | A | `ProjectScopeApiTest` |
| 11 | `ProjectReadScopeApiTest` | D | `ProjectReadScopeApiTest` |
| 12 | Columnas «otro proyecto» y «no asignado» | D | `PermissionMatrixApiTest`, `WorkflowMatrixApiTest` |
| 13 | Migración de la suite en cuatro lotes y la corrida completa | D | Lotes 1 a 5 |
| 14 | `SweepTiming` en los barridos | A | — |
| 15 | UPS-10: interruptor, prueba y siembra | A | `SeparateProvisioningApiTest` |
| 16 | `provision_tenant.py` y `upgrade_tenants.py` | B | — |
| 17 | Tenant de desarrollo y mediciones (coordinador) | E | — |
| 18 | `CLAUDE.md`, verificación y PR | F | Suite completa |

## Cobertura de la especificación

| Especificación | Dónde |
|---|---|
| §2.1 decisión 1 (hacerlo ahora; UPS-10; scripts) | Tareas 7, 15 y 16 |
| §2.1 decisión 2 (`READ_ALL_PROJECTS`, `ANALYST` acotado; confirmación de §7) | Tareas 1, 3 y 5 |
| §2.1 decisión 3 (maestros compartidos) | Tareas 1 (ASM-107) y 4 |
| §2.2 elecciones 1 a 8 | Tareas 6 (SQL puro), 6 (raíz y padre opcionales), 6 (asignaciones propias), 10 (oráculo), 7 (la guarda se queda), 5 (`ProjectReach`), 15 (siembra fuera de banda), este plan (4.4a y 4.4b) |
| §3.1 la condición | Tarea 6 |
| §3.2 el componente y dónde rige | Tareas 7 y 11 |
| §3.3 composición, `ConfidentialRelease`, `DerivedRestore` | Tareas 2 (§9.6), 11 (QCS confidencial de A) y 13 (lote 1, `StakeholderRequestWorkflowApiTest`) |
| §3.4 documentos emitidos y UPS-28 | Tareas 1 y 8 |
| §3.5 registro de objetos, `ArchitectureTest`, `test_apply_roles.py` | Tareas 3, 4 y 7 |
| §3.6 semántica de errores (corregida para M2M) | Tareas 1, 2, 8, 10, 11 y 13 |
| §3.7 tableros, capas, exportaciones, `ExportLog` | Tareas 9 y 11 |
| §3.8 trabajos y avisos | Sin cambio de código; Tarea 2 (`security.md` §9.7) |
| §3.9 UPS-10 y aprovisionamiento | Tareas 15 y 16 |
| §3.10 rendimiento y medición | Tareas 14 y 17 |
| §3.11 GeoServer y `/api/auth/me/permissions` | Tareas 2 (§10.2, `gis.md`) y 11 (`READ_ALL_PROJECTS` en `objects.project`) |
| §3.13 pruebas del backend | Tareas 5, 6, 7, 11, 12 y 13 |
| §4 modelo y orden de despliegue | Tareas 3 y 17 |
| §5 documentos e IDs | Tareas 1, 2, 3 (celdas), 15 y 16 (`develop/README.md`) y 18 (`CLAUDE.md`) |
| §7 riesgos | Review Focus, decisiones 7 y 14, Tareas 13 y 17 |
