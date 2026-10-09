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

- **Repositorio y rama.** Todo el trabajo en `~/IdeaProjects/sgspe`, en la rama `feat/fase-3-2-backend` creada desde
  `feat/fase-3-1-modelo` (el plan 3.1, que parte de `main` en `87f4108`: wasichai 0.6.0, `TenantDirectory`, el origen `job:`
  de las escrituras de los trabajos y el perfil de producción endurecido). Las tareas que la sección «Orden y paralelismo»
  marca como previas al plan 3.1 pueden empezar desde `feat/fase-3-1-modelo` antes de que el plan 3.1 termine, cada una en su
  propio worktree, y se integran a `feat/fase-3-2-backend` cuando la rama del plan 3.1 ya está mezclada en ella.
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
  con rutas explícitas y, al final, tras una línea en blanco, los dos trailers
  `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. Nunca imprimir tokens.
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

La primera clase de una JVM aprovisiona el modelo de F3 con los scripts de `model/` (por el túnel, de 6 a 10 minutos con los
60 objetos): conviene correr juntas las clases de una tarea. Cada tarea dice en su último paso qué clases de integración debe
correr el coordinador. `sgspe_sdd_test` no la usan dos suites a la vez: dos worktrees en paralelo comparten la base, así que
el coordinador corre sus integraciones una tras otra.

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
3. **Un documento emitido congela datos restringidos.** wasichai-documents guarda en la copia del documento el registro entero
   y cada columna de las tablas relacionadas que nombra la plantilla, sin permisos de campo, y `GET /api/documents/{id}` la
   devuelve a todo lector del objeto (UPS-23): una «Lista de asistentes» con la tabla de participantes entregaría nombres,
   teléfonos y correos a cualquiera con READ sobre `engagement`. Pruebas: `IssuedDocumentsPrivacyTest` «ningún tipo de
   documento congela un campo restringido» e `IssuedDocumentsApiTest` «quien lee la interacción recibe la minuta y la lista
   sin los datos de los asistentes» (Tarea 22), además de `test_model.py` y `AutomationsApiTest` del plan 3.1.
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
| `docs/adr/0014-almacenamiento-de-objetos.md`, `docs/adr/0018-notificaciones.md`, `docs/adr/README.md` (modificar) | D1 y D2: se aceptan con wasichai-files y wasichai-notifications | 1 |
| `docs/requirements-analysis.md`, `docs/platform-fit.md` §1.2 y §4.3, `docs/database-model.md` §2.3, §18, §22, §26, `docs/roadmap.md`, `model/test_design_docs.py` (modificar) | ASM-012, ASM-013, ASM-065, UPS-07, UPS-11, UPS-18; ASM-089 y ASM-090; tablas fijas; total de fichas | 1 |
| `src/main/kotlin/sgspe/common/{Records,ModelCatalog,ProjectParameters,StrictBody,FieldChangeReasonGuard}.kt` (modificar) | `unlink`, estados y M2M del catálogo, parámetros de F3, atributos estrictos, motivo al reclasificar | 2 |
| `src/main/kotlin/sgspe/common/{Amounts,JobTasks}.kt`, `src/main/kotlin/sgspe/commitment/Commitments.kt` | Montos acotados; tareas de trabajos por `generator_key`; los conjuntos de estados de WF-D | 2 |
| `src/main/kotlin/sgspe/scope/ProjectScope.kt` (modificar) | Alcance de los objetos de F3 | 2 |
| `src/test/kotlin/sgspe/DomainApiTest.kt`, `src/test/kotlin/sgspe/ArchitectureTest.kt` (modificar) | Motivo, `If-Match` y código de error en la base de F2; paquetes de F3 clasificados | 2 |
| `src/main/kotlin/sgspe/common/{ExactlyOneGuard,CommandTransitionGuard}.kt` (modificar), `src/test/kotlin/sgspe/F3DomainApiTest.kt` | Grupos y comando de F3; base de integración de 3.2 sobre `F3ApiTest` | 2b |
| `src/main/resources/db/sgspe/common/V1__idempotency_key.sql`, `src/main/kotlin/sgspe/common/Idempotency.kt` | Idempotencia de los comandos (D3) | 3 |
| `src/main/kotlin/sgspe/stakeholder/AssessmentController.kt` (modificar), `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt` | Las evaluaciones honran la clave; la base de sus pruebas, sin el modelo de F3 | 3 |
| `src/main/kotlin/sgspe/common/RetentionJob.kt`, `src/main/resources/application.yml` (modificar) | JOB-08 | 4 |
| `src/main/kotlin/sgspe/evidence/{EvidenceRules,EvidenceWriteGuard,Evidences}.kt` | Evidencias: guarda, alta, cambio y vínculos | 5 |
| `src/main/kotlin/sgspe/evidence/EvidenceContent.kt` | Descarga con `DOWNLOAD_RESTRICTED` por las dos rutas | 6 |
| `src/main/kotlin/sgspe/commitment/{CommitmentWriteGuard,CommitmentRecordRules}.kt` | La guarda de compromisos y sus reglas de datos | 7 |
| `src/main/kotlin/sgspe/commitment/{SeniorApproval,ApprovalRules}.kt`; `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt` (modificar) | D1 a D5 y D8; `approve_direct` deja de diferirse | 8 |
| `src/main/kotlin/sgspe/investment/{SupportPolicyGuard,CampaignExecution}.kt` | D7 y el ejecutado de la campaña | 9 |
| `src/main/kotlin/sgspe/commitment/CommitmentDerivations.kt` | Fechas de transición y `requires_senior_approval` | 10 |
| `src/main/kotlin/sgspe/commitment/Reschedules.kt` | Reprogramaciones | 11 |
| `src/main/kotlin/sgspe/commitment/Deliveries.kt` | Entregas y su corrección | 12 |
| `src/main/kotlin/sgspe/commitment/FulfillmentRules.kt`; `WorkflowMatrixApiTest.kt` (modificar) | D9, D10 y D11; `register_fulfillment`, `reopen_execution`, `verify` y `close` dejan de diferirse | 13 |
| `src/main/kotlin/sgspe/commitment/{CandidateRules,Candidates}.kt`; `WorkflowMatrixApiTest.kt` (modificar) | Candidatos humanos (WF-K); `accept` y `mark_duplicate` dejan de diferirse | 14 |
| `src/main/kotlin/sgspe/agreement/AgreementWriteGuard.kt` | WF-I | 15 |
| `src/main/kotlin/sgspe/notification/{Notices,ApprovalNotices}.kt` | Avisos de aprobación pendiente sobre wasichai-notifications | 16 |
| `src/main/kotlin/sgspe/deadline/{Deadlines,DeadlineJob,DeadlineNotices}.kt`, `src/main/resources/application.yml` (modificar) | JOB-01 y los avisos de plazo | 17 |
| `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt` (modificar) | Tareas de JOB-03 | 18 |
| `src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt` | KPI-COM puros | 19 |
| `src/main/kotlin/sgspe/dashboard/{DashboardFilters,DashboardData,Kpis,Dashboards}.kt` (modificar) | Tablero `commitments` y filtro `owner` | 20 |
| `src/main/kotlin/sgspe/privacy/{ExportProjects,MatrixSheets,CommitmentMatrix}.kt`, `{StakeholderRegister,ExportController}.kt` (modificar) | Exportación `commitment-matrix` | 21 |
| `src/test/kotlin/sgspe/privacy/{IssuedDocumentsPrivacyTest,IssuedDocumentsApiTest}.kt` | Documentos emitidos sin datos restringidos | 22 |
| `docs/api.md`, `docs/workflows.md`, `docs/security.md`, `docs/architecture.md`, `docs/deliverables.md`, `docs/domain-model.md`, `docs/dashboards.md`, `docs/requirements-traceability.md`, más los restos de las tablas de avisos en `docs/database-model.md`, `docs/requirements-analysis.md`, `docs/glossary.md` y `docs/platform-fit.md` (modificar) | El contrato de F3 y el diseño como quedó | 23 |
| `src/test/kotlin/sgspe/ArchitectureTest.kt`, `CLAUDE.md` (modificar) | Cierre | 24 |

Las pruebas viven en `src/test/kotlin/sgspe/<paquete>/` con el nombre que cada tarea da.

## Interfaces con `main` y con los otros planes de F3

### Lo que este plan asume de `main` (`87f4108`, wasichai 0.6.0)

- `sgspe.common.Organizations.having(objectName)` sobre `wasichai.core.platform.TenantDirectory.organizationsWithObject`
  (UPS-17), y `ArchitectureTest` que rechaza `findAllOrganizations` en todo el código y deja `TenantDirectory` solo en
  `Organizations.kt`. Este plan no toca `Organizations`.
- `Records.asPlatform(organizationId: UUID, source: String, block: suspend () -> T): T`, que escribe como la plataforma con el
  origen `source` en la fila de auditoría (WAS-ADR-0050, UPS-03). JOB-02 y JOB-03 ya lo usan con `DerivedFieldsJob.SOURCE`
  (`job:derived-fields`) y `StakeholderFollowUpJob.SOURCE` (`job:stakeholder-follow-up`); JOB-01 (Tarea 17) lo usa con
  `job:deadline`. Este plan no lo vuelve a agregar.
- Los documentos ya dicen que los trabajos escriben con su origen: `workflows.md` («Trabajos programados», «Motivo y
  origen»), `security.md` §13.2 y `architecture.md` §8.1. La Tarea 23 solo suma JOB-01 donde nombran a JOB-02 y JOB-03.

### Lo que este plan asume del plan 3.1 (modelo y aprovisionamiento), que se integra antes

Los nombres exactos que produce el plan 3.1 (`2026-10-09-fase-3-1-modelo.md`, «Interfaces con los otros planes de F3»). Si
uno de aquí y uno de `docs/database-model.md` o `docs/workflows.md` difieren, mandan los documentos.

- **`build.gradle.kts` y configuración.** Los starters `wasichai-spring-boot-starter-automation`, `-documents`, `-files` y
  `-notifications`, el AWS SDK v2 (`software.amazon.awssdk:s3`) y `spring-boot-starter-mail`; `wasichai.files.*` (`local` en
  desarrollo por defecto, `s3` obligatorio en `prod`, tope `wasichai.files.max-bytes` de 20 MB), `wasichai.notifications.*`
  y `spring.mail.*`. La tarea `integrationTest` ya fija en `integrationDefaults()` `wasichai.files.store=local`,
  `wasichai.files.cleanup.interval=0s`, `wasichai.notifications.tick=0s` y el correo apagado: este plan no agrega ninguna
  propiedad de prueba.
- **`model/model.json`**: los 60 objetos (los 48 de F2 y los 12 de F3), con los campos, relaciones, únicos, índices y
  acciones de la sección «El modelo» del plan 3.1. Lo que este plan lee de él:
  - `evidence` (`apiOnly`): el campo **`file`** (`FILE`, requerido) en lugar de `storage_key`, `file_name`, `mime_type`,
    `size_bytes` y `sha256` (D1), con los 8 `contentTypes` de la ficha: `application/pdf`, `image/*`, `text/csv`,
    `text/plain`, los dos de Office Open XML (`wordprocessingml.document`, `spreadsheetml.sheet`) y los dos de OpenDocument
    (`opendocument.text`, `opendocument.spreadsheet`). Las pruebas de este plan suben un PDF (`F2ApiTest.PDF`). El registro y
    su historial muestran el descriptor `{ id, name, contentType, size, sha256 }`; la subida preparada es
    `POST /api/objects/evidence/files/file` (CREATE sobre `evidence`, parte `file`) y su `id` va en `attributes.file` del
    alta, que solo hace `sgspe.evidence`. Además `evidence_type`, `classification`, `title`, `document_date`, `author`,
    `captured_at`, `people_identifiable`, `photo_consent_given`, `import_batch_id`, `location`; `project` y `replaces`; las
    M2M `evidence_community`, `evidence_land_parcel`, `evidence_stakeholder`, `evidence_engagement`, `evidence_field_visit`,
    `evidence_field_observation`, `evidence_commitment`, `evidence_delivery`, `evidence_agreement` y `evidence_study`;
    acciones `DOWNLOAD_RESTRICTED` y `EDIT_ANY`.
  - **La descarga genérica `GET /api/objects/evidence/records/{id}/files/file` solo pide READ** (wasichai-files): el plan 3.1
    la deja abierta y este plan la reemplaza (decisión 2, Tarea 6) con la comprobación de `DOWNLOAD_RESTRICTED` de
    `GET /api/sgspe/evidence/{id}/content`.
  - `commitment` (`requiresReason`): los campos de §10 con los derivados `executed_amount`, `deadline_status`,
    `requires_senior_approval`, `fulfilled_date`, `closed_date` y `cancelled_date`; acciones `APPROVE`, `VERIFY`, `EXPORT` y
    `APPROVE_OVER_CAP`; la M2M `commitment_stakeholder` y las MANY_TO_ONE `project`, `exploration_campaign`, `locality`,
    `community`, `topic`, `origin_engagement`, `agreement` y `agreement_clause`.
  - `commitment_reschedule` (`appendOnly`, `apiOnly`), `commitment_dependency` (único `commitment`, `depends_on`),
    `commitment_action`, `commitment_milestone`, `delivery` (`apiOnly`; `commitment` con «exactamente uno de»;
    `conformity_given` obligatorio; `recipients` y `conformity_by` restringidos), `commitment_candidate` (`apiOnly`;
    `ai_generated` obligatorio, `confidence`, `evidence_quote` restringido; `project`, `engagement`, `source_evidence`,
    `created_commitment`, `duplicate_of`).
  - `agreement` (`requiresReason`, el derivado `validity_status`, `community`, `assembly_engagement`, la M2M
    `agreement_land_parcel`), `agreement_party` (`organization` o `person`, exactamente uno) y `agreement_clause`.
  - `task` (el derivado `deadline_status`, `generator_key` único; `project` y los orígenes `commitment`, `engagement`,
    `agreement`, `stakeholder`, exactamente uno).
  - **Los 15 parámetros de `project` «Desde F3»** con su `defaultValue`: `policy_workshop_commitments_blocked` (`false`),
    `policy_senior_approval_for_dialogue` (`true`), `policy_internal_approval` (`true`), `policy_local_jobs_direct_area_only`
    (`true`), `local_jobs_min` (`6`), `local_jobs_max` (`10`), `policy_no_jobs_in_easements` (`true`), `due_soon_days`
    (`7`), `escalation_first_days` (`7`), `escalation_second_days` (`15`), `policy_cap_blocks_approval` (`true`),
    `policy_no_paid_labor` (`true`), `policy_non_monetary_donations` (`true`), `policy_synergy_requires_agreement` (`true`),
    `policy_priority_warning` (`true`). Este plan lee siete y usa el mismo valor por defecto que la ficha
    (`ProjectParameters`, Tarea 2), porque un proyecto creado antes de F3 los tiene vacíos (ASM-019); los de empleo esperan
    su dato (ASM-089).
  - Los ENUM de §10 del análisis (`resource_type`, `commitment_theme`, `commitment_formality`, `request_medium`,
    `priority`, `deadline_status`, `validity_status`, `agreement_type`, `evidence_type`, `evidence_classification`).
- **`model/workflows/`**: `wf-d-commitment`, `wf-i-agreement`, `wf-k-commitment_candidate` y `wf-n-task` con los estados,
  transiciones y roles de la tabla del plan 3.1. Este plan nombra `submit`, `approve`, `approve_senior`, `approve_direct`,
  `start`, `register_fulfillment`, `reopen_execution`, `verify`, `close` y las `cancel_*` (WF-D); `activate`,
  `activate_from_draft` y `resume` (WF-I); `accept`, `reject` y `mark_duplicate` (WF-K); y los estados de WF-N. La
  transición de comando es `commitment_candidate.accept` (`CommandTransitionGuard`, Tarea 2b); el motivo de `reject`,
  `mark_duplicate` y las cancelaciones de WF-N ya lo exige `TransitionReasonGuard` de F2.
- **Tipos de documento y automatizaciones.** `model/document-types/engagement.json` con `meeting-minutes` («Minuta de
  reunión», prefijo `MR`) y `attendance-list` («Lista de asistentes», prefijo `LA`, el total de asistentes y filas en blanco
  para nombres y firmas); ninguno sobre un objeto con campos restringidos ni con una `relatedTable` hacia uno, la regla de
  `test_model.py` (`test_ningun_tipo_de_documento_congela_un_campo_restringido`) y de `security.md` §11.4. **No hay
  `model/document-types/delivery.json` ni AUT-03**: el «Acta de entrega» (`delivery-certificate`, `AE`) espera a la brecha
  UPS-23, que el plan 3.1 agrega. Automatizaciones: `aut-01-commitment-execution-task`, `aut-05-meeting-minutes` y
  `aut-10-engagement-reschedule-task`. El JSON de un documento emitido no trae el nombre técnico de su tipo: se reconoce por
  el prefijo de su `number` (`MR-2026-001`) o por `snapshot.platform.documentName`, que es la etiqueta («Minuta de
  reunión», `DocumentService.issue` de wasichai-documents 0.6.0).
- **`model/roles.json`**: las concesiones de F3 de `security.md` §7 (con `COMMITMENT_MANAGER` y `APPROVER`;
  `PROJECT_ADMIN` con RU sobre `task`); `APPROVE` en `commitment` para `APPROVER` y `PROJECT_MANAGER`, `VERIFY` para `COMMITMENT_MANAGER` y
  `SOCIAL_MANAGER`, `APPROVE_OVER_CAP` para `PROJECT_MANAGER`, `DOWNLOAD_RESTRICTED` y `EDIT_ANY` en `evidence`; **READ sobre
  `project_assignment` para todo rol que aplica una transición**, que `ApprovalNoticeListener` (Tarea 16) necesita porque lee
  las asignaciones como quien envía; y los 12 `restrictedFields` (en F3, `commitment.beneficiaries_detail`,
  `delivery.recipients`, `delivery.conformity_by` y `commitment_candidate.evidence_quote`).
- **La base de pruebas.** `sgspe.F3ApiTest : DomainApiTest` con `organizationId()`, `platformCreate`,
  `commitmentAttributes(project, owner, verifier)`, `commitment(project, owner, verifier, creator, attributes)` (en draft,
  con D1, D2 y un solicitante), `linkWithReason`, `agreement`, `task` y `awaitRun`; `F2ApiTest.stagedFile(objectName, field,
  account)` y `F2ApiTest.PDF`; `F2ApiTest.sample()` con las elecciones de `EXACTLY_ONE` (`delivery` → `commitment`,
  `agreement_party` → `organization`, `task` → `engagement`) y el `FIXED` `ai_generated = false` de `commitment_candidate`;
  `GuardTablesCatalogTest.notYetModeled` = `social_program`, `early_warning`, `ai_suggestion`. `F3DomainApiTest` de este
  plan (Tarea 2b) extiende `F3ApiTest` y no repite nada de ella ni de `F2ApiTest`.
- **Las pruebas de integración del plan 3.1** y lo que dejan a este plan:

  | Clase del plan 3.1 | Lo que prueba este plan (tarea) |
  |---|---|
  | `CommitmentWorkflowApiTest` | D1 (cada campo de la matriz), D2, D4, D5 y D8 en `CommitmentApprovalApiTest` (8); D7 en `SupportPolicyApiTest` (9); las fechas y `requires_senior_approval` en `CommitmentDerivationsApiTest` (10); D9, D10 y D11, `register_fulfillment`, `reopen_execution`, `verify` y `close` en `CommitmentFulfillmentApiTest` (13) |
  | `AgreementWorkflowApiTest` | `AgreementWriteGuard` en `AgreementGuardApiTest` (15); `validity_status` en `DeadlineJobApiTest` (17) |
  | `CommitmentCandidateWorkflowApiTest` | El alta humana, `accept` por su comando y `mark_duplicate` con y sin `duplicate_of` en `CandidateApiTest` (14); la cuenta de servicio, según la nota de integración de la Tarea 14 |
  | `TaskWorkflowApiTest` | `deadline_status` y las tareas de escalamiento en `DeadlineJobApiTest` (17); las de JOB-03 en `FollowUpTasksApiTest` (18) |
  | `AutomationsApiTest` | Nada: este plan solo agrega que un lector sin los datos de los asistentes recibe la minuta y la lista sin ellos (`IssuedDocumentsApiTest`, 22) |
  | `WorkflowMatrixApiTest` | Difiere `commitment.approve_direct`, `register_fulfillment`, `reopen_execution`, `verify`, `close`, `commitment_candidate.accept` y `mark_duplicate`. Este plan las saca de `DEFERRED` en la tarea dueña de su guarda: `approve_direct` en la 8, las cuatro de cumplimiento en la 13 y las dos de WF-K en la 14 (decisión 13) |

  El plan 3.1 dice que este plan no edita esas clases; la única que edita es `WorkflowMatrixApiTest`, para que la matriz
  cubra por rol las transiciones que difería (decisión 13). `close` cierra la solicitud que originó el compromiso
  (`RequestClosureListener`) en F4, con `stakeholder_request`.

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
  valida el tamaño, hasta 20 MB, y el tipo detectado por los bytes contra los 8 `contentTypes` de `evidence.file`, y responde
  `201 { id, name, contentType, size, sha256 }`) y `POST /api/sgspe/evidence`
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
  wasichai-documents. En F3, la «Minuta de reunión» (`meeting-minutes`, número `MR-…`) y la «Lista de asistentes»
  (`attendance-list`, `LA-…`, con el total y filas en blanco para nombres y firmas) de una interacción, que emite AUT-05; el
  JSON no trae el nombre técnico del tipo, así que el portal reconoce cada documento por el prefijo de su `number` y muestra
  `snapshot.platform.documentName` (la etiqueta). No hay «Acta de entrega» hasta UPS-23: el acta firmada se sube como
  evidencia ACTA DE ENTREGA (ASM-090).

## Decisiones de este plan

Conflictos entre documentos, o entre un documento y D1 a D3, que este plan resuelve; la Tarea 1 o la 23 los escribe:

1. **La subida de una evidencia es en dos pasos** (D1): el archivo por la ruta de wasichai-files, que ya valida, calcula el
   SHA-256 y aplica el `StoredFileGuard`; el registro por `POST /api/sgspe/evidence` en JSON. Así la clave de idempotencia
   cubre una petición JSON (como la nativa) y un reintento no vuelve a subir el archivo. `api.md` §4.9 decía multipart con
   `attributes` y `links` y lectura de EXIF en el servidor: los EXIF los lee el portal.
2. **La ruta nativa de descarga de `evidence` se reemplaza** por un controlador de SGSPE con el segmento literal `evidence`
   (más específico que `{object}` para Spring), que aplica la misma comprobación que `GET /api/sgspe/evidence/{id}/content`
   (Tarea 6). El plan 3.1 la deja como la trae wasichai-files, que solo pide READ: sin esto, D1 abriría las evidencias
   restringidas a todo lector.
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
6. **Un tipo de documento solo se declara sobre un objeto sin campos restringidos** y sin tablas relacionadas de objetos que
   los tengan (ASM-090), la regla del plan 3.1 (`security.md` §11.4, `test_model.py`): wasichai-documents congela en el
   documento el registro entero y cada columna de las tablas relacionadas que nombra la plantilla, sin permisos de campo, y
   `GET /api/documents/{id}` lo devuelve a todo lector del objeto (UPS-23). Por eso la «Lista de asistentes» lleva el total y
   filas en blanco en lugar de la tabla de participantes, y el «Acta de entrega» de `delivery` (que restringe `recipients` y
   `conformity_by`) y AUT-03 esperan a UPS-23. El plan 3.1 no la registra como supuesto: ASM-090 la registra aquí, y UPS-23
   es la brecha que la levantaría.
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
13. **`WorkflowMatrixApiTest` deja de diferir las transiciones de F3** que el plan 3.1 le dejaba a este plan
    (`commitment.approve_direct`, `register_fulfillment`, `reopen_execution`, `verify`, `close`,
    `commitment_candidate.accept` y `mark_duplicate`), cada una en la tarea dueña de su guarda: la matriz pasa a extender
    `F3DomainApiTest`; su proyecto de prueba no tiene aprobación interna (`approve_direct`, D8, y `approve` no la mira);
    `prepare` registra con el comando de entregas la entrega con acta y foto que pide `register_fulfillment` (D9) antes de
    aplicarla o de pasar por ella; `apply` aplica `accept` por su comando (la API genérica responde `COMMAND_ONLY`); y los
    candidatos nacen con `duplicate_of`, que `mark_duplicate` exige. Así cada rol de cada transición de F3 se prueba como
    las de F2. Las cuatro diferidas de F2 (`assess`, `retire_assessed`, `approve` y `supersede` de WF-O) siguen igual.

## Orden y paralelismo

Cada tarea se puede hacer en su propio worktree cuando las tareas de las que depende ya están integradas en
`feat/fase-3-2-backend`. Las de un mismo grupo tocan archivos distintos y no usan nada unas de otras, así que corren en
paralelo; un grupo empieza cuando el anterior está integrado (salvo lo que dice cada fila). La integración la corre el
coordinador después de integrar cada tarea, una clase tras otra, porque todas usan `sgspe_sdd_test`.

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| 0a, antes de que el plan 3.1 se integre | 2 | `main` (`87f4108`) o `feat/fase-3-1-modelo` sin el plan 3.1 | Ninguna de sus pruebas lee el modelo de F3 |
| 0b, antes de que el plan 3.1 se integre | 3, 19 | 2 | 3: idempotencia y su base de pruebas sobre las evaluaciones de F2; 19: funciones puras sobre `Commitments` |
| 0c, antes de que el plan 3.1 se integre | 4 | 3 | JOB-08 purga lo que guarda `Idempotency` |
| 1 | 1, 2b | el plan 3.1 integrado; 2b también la 2 | 1 solo toca documentos (reemplaza texto que deja el plan 3.1, como el total 501 de §20.1); 2b, las guardas transversales y la base de pruebas |
| 2 | 5, 7, 11, 15, 18, 20, 21, 22 | 2b; además 3 (5, 11), 19 (20) | Evidencias, guarda de compromisos, reprogramaciones, acuerdos y `DependentsDeleteGuard`, JOB-03, tablero, exportación y documentos emitidos: archivos disjuntos |
| 3 | 6, 8, 9, 12 | 5 (6), 7 (8, 9), 11 (12) | Descarga de evidencias, aprobación (y la primera edición de `WorkflowMatrixApiTest`), tope de apoyo social, entregas |
| 4 | 10, 13, 16 | 8 (10, 16), 7 y 12 (13), y la 8 por `WorkflowMatrixApiTest` (13) | Derivados, cumplimiento (segunda edición de la matriz), avisos de aprobación |
| 5 | 14, 17 | 7 y 3 (14), y la 13 por `WorkflowMatrixApiTest` (14); 16 y 4 (17) | Candidatos (tercera edición de la matriz), JOB-01 |
| 6 | 23, después 24 | todas las anteriores | 23 documenta lo construido sobre el texto que dejan el plan 3.1 y la Tarea 1; 24 cierra |

Archivos que comparten tareas y que por eso las ordenan: `WorkflowMatrixApiTest.kt` (8 → 13 → 14), `application.yml` (4 → 17:
la 17 agrega `deadline` antes del `retention` de la 4), `ArchitectureTest.kt` (2 → 24), `EvidenceApiTest.kt` (5 → 6),
`ModelCatalog.kt` (2 → 15, en partes distintas del archivo) y los documentos de la Tarea 1 que vuelve a tocar la 23.

**Antes de que el plan 3.1 se integre** pueden hacerse las tareas 2, 3, 4 y 19: no leen `model.json` de F3 ni usan `F3ApiTest`
(las pruebas de integración de la 3 y la 4 extienden `AssessmentCommandApiTest`, sobre `DomainApiTest`). Lo que de las
piezas comunes sí lee el modelo (los grupos de «exactamente uno de», la transición de comando de WF-K, que
`GuardTablesCatalogTest` comprueba contra `model.json`, y `F3DomainApiTest`, que extiende `F3ApiTest`) está en la Tarea 2b.
Sus integraciones (`IdempotencyApiTest`, `RetentionJobApiTest`) no dependen del modelo: corren con el de F2 o con el de F3
que aprovisione la primera clase de la JVM.

---

### Tarea 1: Las decisiones D1 a D3 en los documentos

Antes de escribir código, los documentos que lo gobiernan dicen lo que se decidió el 2026-10-09: las evidencias guardan su
archivo en el campo `FILE` de wasichai-files (D1), los avisos los entrega wasichai-notifications (D2) y los comandos propios
honran `Idempotency-Key` con el contrato de la cabecera nativa (D3). Esta tarea acepta ADR-0014 y ADR-0018 con esas
decisiones, reescribe los supuestos y las brechas que cambian, agrega ASM-089 y ASM-090 (decisiones 5 y 6 de este plan) y
actualiza las tablas fijas del modelo de datos. El contrato de cada endpoint lo escribe la Tarea 23.

**Archivos:**
- Modificar: `docs/adr/0014-almacenamiento-de-objetos.md`, `docs/adr/0018-notificaciones.md`, `docs/adr/README.md`
- Modificar: `docs/requirements-analysis.md` (§18.2 UPS-07, UPS-11, UPS-18; §19 ASM-012, ASM-013, ASM-065; ASM-089 y ASM-090; §20.1)
- Modificar: `docs/platform-fit.md` (§1.2, §4.1 y §4.3)
- Modificar: `docs/database-model.md` (§2.3, §18, §22, §26)
- Modificar: `docs/roadmap.md` (tabla de fases y fila F3)
- Modificar: `model/test_design_docs.py` (el total de fichas: §18 deja de tener las dos de las tablas de avisos)
- Probar: `tools/check_traceability.py`, `model/test_model.py`, `model/test_design_docs.py`

**Interfaces:**
- Consume: nada de código; los documentos como los deja el plan 3.1: UPS-23 en §18.2 y el total de §20.1 en 501
  (`requirements-analysis.md`), la regla de los tipos de documento en `security.md` §11.4 y las filas «Workflows»,
  «Entregables» y «Definición de terminado» de F3 en `roadmap.md`, que esta tarea no toca.
- Produce: ASM-089 (D6 sin dato), ASM-090 (un tipo de documento solo sobre un objeto sin campos restringidos), la ficha de
  `idempotency_key` con las columnas que crea la Tarea 3 y la sección §18 sin tablas propias, que citan las tareas
  siguientes.

- [ ] **Paso 1: Aceptar ADR-0014 con wasichai-files**

En `docs/adr/0014-almacenamiento-de-objetos.md`, en `## Contexto`, reemplazar el bullet
`- wasichai no tiene campos FILE ni IMAGE (CON-046, UPS-07) [WAS-DOC domain/metadata-model.md].` por:

```markdown
- wasichai no tenía campos FILE ni IMAGE (CON-046, UPS-07) [WAS-DOC domain/metadata-model.md]; desde la 0.6.0 los trae
  wasichai-files [WAS-ADR-0061].
```

Reemplazar la sección `## Estado` entera por:

```markdown
## Estado

Aceptado · 2026-10-09 · decisión D1 del usuario: las evidencias usan el campo `FILE` de wasichai-files (UPS-07,
WAS-ADR-0061); el puerto propio queda para los entregables de F8
```

Reemplazar las secciones `## Decisión` y `## Consecuencias` enteras por:

```markdown
## Decisión

Los archivos viven en almacenamiento de objetos; la base guarda su descriptor (nombre, tipo, tamaño, SHA-256) y nunca los
bytes (ASM-013). Desde wasichai 0.6.0 la plataforma lo ofrece (UPS-07, WAS-ADR-0061), y SGSPE lo adopta para las evidencias:

- **`evidence.file` es un campo `FILE` de wasichai-files.** Su columna nombra una fila de `stored_files` (organización,
  objeto y campo, clave `<organización>/<id>`, nombre del cliente, tipo detectado por sus bytes, tamaño, SHA-256, quién y
  cuándo). Un registro, su historial y sus listeners ven el descriptor `{id, name, contentType, size, sha256}`, nunca los
  bytes. El almacenamiento es el `FileStore` del módulo: un servicio compatible con S3 (`wasichai.files.store=s3`, AWS SDK
  v2) y MinIO solo en desarrollo, por su licencia AGPL ([architecture.md §10.1](../architecture.md)).
- **Subida en dos pasos.** El portal sube el archivo con `POST /api/objects/evidence/files/file` (wasichai-files: tope de
  tamaño, tipo detectado por sus bytes y comparado con la lista del campo, SHA-256, CREATE sobre `evidence`) y registra la
  evidencia con `POST /api/sgspe/evidence`, que la crea (es `apiOnly`) y la vincula en una transacción, con
  `Idempotency-Key` ([api.md §4.9](../api.md)). `StoredFileGuard` de wasichai solo deja adjuntar un archivo que subió la misma
  persona para `evidence.file`; un archivo que nadie adjunta lo borra la limpieza del módulo pasadas 24 horas.
- **`evidence` inmutable.** Es `apiOnly` con `EvidenceWriteGuard` (`sgspe.evidence`): el archivo, la versión a la que
  reemplaza y la importación de origen no cambian, y la evidencia no se borra; una corrección es una evidencia nueva con
  `replaces`. No es `appendOnly` porque tiene vínculos MANY_TO_MANY con los registros que prueba [WAS-ADR-0040]; los
  vínculos con objetos `requiresReason` llevan motivo [WAS-ADR-0041] ([domain-model.md §6.12](../domain-model.md)).
- **Control de descarga propio.** La descarga nativa de wasichai-files solo mira READ y los permisos de campo. SGSPE
  agrega la clasificación: `GET /api/sgspe/evidence/{id}/content` exige READ sobre `evidence` y, para CONFIDENCIAL y DATOS
  PERSONALES, la acción declarada `DOWNLOAD_RESTRICTED`, también para quien la subió; y la ruta nativa
  `GET /api/objects/evidence/records/{id}/files/{field}` la atiende un controlador de SGSPE con la misma comprobación (el
  segmento literal `evidence` es más específico que `{object}`). Bajar una clasificación restringida exige la misma acción
  (RF-DOC-003, ASM-071) [WAS-ADR-0042]. Cambiar o vincular una evidencia ajena exige `EDIT_ANY` (ASM-069, ADR-0006). La
  descarga responde `attachment`, `nosniff` y `ETag` igual al SHA-256.
- **Importaciones.** Desde F6, con los formatos de GDAL, el archivo original de una importación se guarda como `evidence`
  con su `import_batch_id` (ASM-060, ADR-0019); hasta entonces la importación lee de una carpeta temporal con límites
  (RQ-SEC-020).
- **Entregables.** Sus borradores, versiones y plantillas no son campos de un registro: en F8 usan un `FileStore` del mismo
  módulo o un puerto propio en `sgspe.deliverable`, que se decide con su plan; las claves y los SHA-256 siguen en
  `deliverable_version` (ADR-0013).
- **Retención.** Las evidencias no se purgan; un archivo que deja de nombrar un registro lo borra la limpieza de
  wasichai-files (`wasichai.files.cleanup.*`). JOB-08 conserva los N últimos borradores de cada entregable desde F8
  (RQ-REP-027, ASM-028) ([database-model.md §26](../database-model.md)).
- **Baja de un tenant.** `stored_files` no tiene clave foránea a la organización: la limpieza del módulo borra los archivos de
  un tenant borrado (WAS-ADR-0061).

## Consecuencias

### Positivas

- Sin código propio de almacenamiento ni de subida para las evidencias: tamaño, tipo, hash, permisos, auditoría y limpieza
  son de la plataforma.
- Una evidencia es verificable por su hash, que viaja en el registro, y su historia de reemplazos queda completa.
- Los permisos de la escritura del archivo son los del registro (WAS-ADR-0061): `apiOnly`, `EDIT_ANY` y el alcance por
  proyecto valen igual.

### Negativas y riesgos

- La descarga nativa no conoce la clasificación: SGSPE la reemplaza para `evidence`. Un objeto nuevo con un campo `FILE` y
  datos restringidos necesitará lo mismo.
- El archivo se sostiene en memoria mientras se valida (`wasichai.files.max-bytes`, 20 MB en SGSPE).
- El almacenamiento de objetos contiene datos personales: su cifrado, su respaldo y su retención dependen del despliegue
  (GAP-022, GAP-014) ([security.md §11.6](../security.md)).

### Seguimiento

- Abiertos: GAP-014, GAP-022, GAP-026. UPS-07 queda resuelta por wasichai 0.6.0.
- Pruebas: `EvidenceApiTest` (alta, inmutabilidad, reclasificación y las dos rutas de descarga); la de "Descargas" de
  [security.md §14](../security.md).
```

En `## Referencias`, en la línea de wasichai, agregar al final: `` `WAS-ADR-0061` (FILE and IMAGE fields, stored through a
FileStore SPI and written as record writes).``

- [ ] **Paso 2: Aceptar ADR-0018 con wasichai-notifications**

En `docs/adr/0018-notificaciones.md`, reemplazar `## Estado` por:

```markdown
## Estado

Aceptado · 2026-10-09 · decisión D2 del usuario: los avisos los entrega wasichai-notifications (UPS-11, WAS-ADR-0046,
WAS-ADR-0047, WAS-ADR-0060); SGSPE decide los destinatarios y los textos
```

Reemplazar las secciones `## Decisión` y `## Consecuencias` enteras por:

```markdown
## Decisión

Desde wasichai 0.6.0 la plataforma tiene el módulo de notificaciones que faltaba (UPS-11): bandeja con estado por persona,
SSE, canal de correo con reintentos, preferencias y la acción `NOTIFY` de las automatizaciones. SGSPE lo adopta y no tiene
tablas ni bandeja propias (se retiran `notification`, `notification_delivery`, JOB-06 y `/api/sgspe/notifications*`):

- **SGSPE decide a quién y qué dice.** `sgspe.notification` publica con `Notifications.publish` del módulo, con una clave
  estable por aviso: publicar la misma clave no duplica (RF-NTF-001) y la bandeja muestra lo que sigue siendo cierto. Los
  textos van en español y nombran el registro y el evento, nunca valores restringidos (ADR-0017).
- **Quién crea avisos.** Solo eventos, nunca las entidades (RN-NTF-001): `ApprovalNoticeListener` al enviar un proyecto, un
  acta o un compromiso a aprobación (aviso `ACTION`, fuente `sgspe.approvals`, que se resuelve cuando el registro sale del
  estado pendiente), y `DeadlineNotices`, una fuente del módulo (`sgspe.deadlines`) que cada hora devuelve todos los avisos
  de plazo vigentes según lo que JOB-01 escribió; lo que deja de estar vencido se resuelve solo. Los avisos y sus claves
  están en [workflows.md](../workflows.md) ("Avisos").
- **Destinatarios por rol** (ASM-070): un aviso nombra una transición, nunca un rol. Lo reciben las asignaciones vigentes del
  proyecto del registro cuyo `role_name` está en la lista `roles` de esa transición, leída de `model/workflows/<objeto>.json`;
  cambiar esos roles cambia los destinatarios sin tocar el código. Quedan fuera quien aplicó la transición, `SUPER_ADMIN`,
  `ORGANIZATION_ADMIN` y las cuentas de servicio [WAS-ADR-0043]. Sin coincidencias no se crea el aviso y queda una advertencia
  en el log. El aviso va a personas concretas (`Audience.User`), nunca a un rol de wasichai: los roles de wasichai son del
  tenant, no del proyecto.
- **Transacción.** `Notifications.publish` se une a la transacción de quien escribe: el aviso se confirma o se revierte con la
  transición. Un aviso mal formado (un error de SGSPE) se registra y la escritura sigue; un destinatario que ya no existe lo
  descarta el módulo con una advertencia.
- **Correo.** Es el canal `email` del módulo (Spring Mail, `wasichai.notifications.email.*`), con reintentos y preferencias
  por persona; el servidor SMTP es configurable, con uno de prueba en desarrollo (GAP-021). Un aviso nunca lleva al correo un
  enlace `RECORD`: el módulo lo quita.
- **Bandeja.** La del módulo: `GET /api/auth/me/notifications`, su resumen, leer, descartar, posponer, leer todo y el flujo
  SSE ([api.md §2](../api.md)). La bandeja quita un enlace `RECORD` a quien no puede leer el objeto.
- **Escalamiento.** En los niveles 1 y 2 de un compromiso vencido, JOB-01 crea además una tarea sin asignado ni fecha, con
  `generator_key` único, que toma uno de los avisados (RF-WFL-004, ASM-030).

## Consecuencias

### Positivas

- Sin tablas, bandeja, envío por correo ni reintentos propios: SGSPE mantiene solo los destinatarios y los textos.
- Cada aviso llega una sola vez a las personas del proyecto con el rol de la siguiente decisión, sin nombres de rol en el
  código, y se resuelve solo cuando deja de ser cierto.

### Negativas y riesgos

- Un fallo de la base al guardar un aviso revierte la transición que lo dispara, como cualquier escritura de esa transacción.
- Un aviso de plazo llega hasta una hora después del cambio que lo causa (el intervalo de la fuente).
- Quien recibe un aviso actúa con sus roles de wasichai: si `role_name` no coincide con ellos, recibe el aviso y la
  transición le responde 403.

### Seguimiento

- Se acepta al iniciar F3, con los avisos de aprobación, compromisos y tareas; los de QCS, riesgos y alertas se suman en F4.
- Abiertos: GAP-014, GAP-021. UPS-11 queda resuelta por wasichai 0.6.0.
- Pruebas: `ApprovalNoticesApiTest`, `DeadlineJobApiTest` y `NoticesTest`; "Destinatarios (ASM-070)" de
  [workflows.md](../workflows.md); `ArchitectureTest` no encuentra nombres de rol en `sgspe.notification`.
```

En `## Referencias`, en la línea de wasichai, agregar: `` `WAS-ADR-0046` (A notifications module), `WAS-ADR-0047` (Server
push over SSE), `WAS-ADR-0060` (Delivery channels and automation NOTIFY).``

En el índice `docs/adr/README.md`, reemplazar las filas de 0014 y 0018 por:

```markdown
| [0014](0014-almacenamiento-de-objetos.md) | Evidencias y entregables en almacenamiento de objetos | Aceptado | 2026-10-09 | 0040, 0041, 0042, 0061 |
```

```markdown
| [0018](0018-notificaciones.md) | Notificaciones en la aplicación y abstracción del correo | Aceptado | 2026-10-09 | 0016, 0026, 0038, 0039, 0043, 0046, 0047, 0060 |
```

y en `docs/platform-fit.md` §1.2, en las filas de ADR-0014 y ADR-0018, cambiar «Propuesto (se acepta en F3)» por «Aceptado».

- [ ] **Paso 3: Reescribir las filas de supuestos y brechas que cambian**

En `docs/requirements-analysis.md` §18.2, reemplazar las filas de UPS-07, UPS-11 y UPS-18 por:

```markdown
| UPS-07 | Campo FILE (e IMAGE) | Evidencias y fotos sin tipo de campo propio | Resuelta en wasichai 0.6.0 (WAS-ADR-0061): `evidence.file` es un campo `FILE` y la descarga la controla SGSPE (ASM-013) | F3 | RF-DOC-001 |
| UPS-11 | Módulo de notificaciones | Cada app construye su propia bandeja | Resuelta en wasichai 0.4.0 y 0.6.0 (WAS-ADR-0046, WAS-ADR-0060): bandeja, correo y fuentes del módulo; SGSPE decide destinatarios y textos (ASM-012) | F3–F4 | RF-NTF-001 |
| UPS-18 | Idempotencia en la API genérica (`Idempotency-Key`) | Un reintento de una creación por la API genérica puede duplicar el registro | Resuelta en wasichai 0.6.0 para el alta genérica (WAS-ADR-0058); los comandos de `/api/sgspe/**` usan la tabla fija `idempotency_key` con el mismo contrato (ASM-065) | F3 | RNF-INT-002 |
```

En §19, reemplazar las filas de ASM-012, ASM-013 y ASM-065 por:

```markdown
| ASM-012 | Los avisos los entrega el módulo wasichai-notifications (bandeja, SSE, correo y preferencias); SGSPE publica con una clave estable por aviso, a personas concretas que resuelve por las asignaciones del proyecto (ASM-070), y no tiene tablas de avisos propias | Decisión D2 del usuario (2026-10-09) al llegar el módulo upstream (UPS-11) | Volver a tablas fijas propias si el módulo no alcanza | RF-NTF-001, ADR-0018 |
| ASM-013 | Las evidencias guardan su archivo en el campo `FILE` de wasichai-files, en almacenamiento compatible con S3, con SHA-256; la descarga de las restringidas la controla SGSPE por las dos rutas | Decisión D1 del usuario (2026-10-09) al llegar el campo FILE (UPS-07) | Volver a un puerto de almacenamiento propio | RF-DOC-001, RF-DOC-003, ADR-0014 |
| ASM-065 | Las claves de idempotencia de los comandos `/api/sgspe/**` se guardan en una tabla fija `idempotency_key` del esquema `sgspe`, con el contrato de la cabecera nativa de wasichai (respuesta guardada, `422` para otra petición, `409` con `Retry-After` en curso, el mismo plazo), escritas en la transacción del comando y purgadas por JOB-08 | La cabecera nativa (UPS-18, WAS-ADR-0058) solo cubre el alta genérica; decisión D3 del usuario (2026-10-09) | Retirar la tabla cuando la plataforma ofrezca la clave a las rutas de una app | RNF-INT-002, tabla fija `idempotency_key` |
```

Después de la fila de ASM-088, agregar:

```markdown
| ASM-089 | D6 de WF-D (políticas de empleo y de servidumbres, RN-COM-002 y RN-COM-003) no se verifica en F3: ningún campo del modelo dice que un compromiso ofrece puestos de trabajo ni cuántos, y el registro de empleo está fuera de alcance (GAP-016) | Una guarda no puede evaluar un dato que el modelo no guarda | Agregar a `commitment` un dato de puestos ofrecidos y la regla D6 en `CommitmentWriteGuard` | RN-COM-002, RN-COM-003, GAP-016, WF-D |
| ASM-090 | Un tipo de documento de wasichai-documents solo se declara sobre un objeto sin campos restringidos y sin tablas relacionadas de objetos que los tengan: en F3, la «Minuta de reunión» y la «Lista de asistentes» de `engagement`, esta con el total de asistentes y filas en blanco para los nombres y las firmas; el «Acta de entrega» de `delivery` y AUT-03 esperan, y el acta firmada se sube como evidencia ACTA DE ENTREGA | Un documento emitido congela el registro entero y cada columna de las tablas relacionadas que nombra la plantilla, sin permisos de campo, y se lee con READ sobre el objeto (UPS-23, WAS-ADR-0023, `security.md` §11.4) | Que wasichai-documents congele solo lo que la plantilla nombra o aplique los permisos de campo de quien lee (UPS-23) | RQ-REP-029, RQ-SEC-010, UPS-23, AUT-03, AUT-05 |
```

En §20.1, cambiar la fila `| `ASM` | 88 |` por `| `ASM` | 90 |` y la fila `| Total | 501 |` por `| Total | 503 |` (el plan 3.1
ya la llevó de 500 a 501 con UPS-23).

- [ ] **Paso 4: Actualizar el encaje con wasichai 0.6.0**

En `docs/platform-fit.md` §4.3, en el párrafo de entrada, cambiar «(UPS-03, UPS-09 y UPS-17 están adoptadas)» por
«(UPS-03, UPS-09 y UPS-17 están adoptadas, y UPS-07, UPS-11 y UPS-18 desde F3)», y reemplazar las filas de UPS-07, UPS-11 y
UPS-18 de la tabla por:

```markdown
| UPS-07 | 0.6.0: módulo `wasichai-files`, campos `FILE` e `IMAGE` con un SPI de almacenamiento [WAS-ADR-0061] | Adoptada en F3 (decisión D1): `evidence.file` es un campo `FILE`; SGSPE reemplaza la descarga nativa de `evidence` para aplicar `DOWNLOAD_RESTRICTED` (ADR-0014) | Nada |
| UPS-11 | 0.4.0 y 0.6.0: módulo `wasichai-notifications`, con bandeja, SSE, canales de entrega y preferencias [WAS-ADR-0046] [WAS-ADR-0060] | Adoptada en F3 (decisión D2): bandeja, correo y fuentes del módulo; SGSPE publica a personas resueltas por las asignaciones (ADR-0018) | Nada |
| UPS-18 | 0.6.0: `Idempotency-Key` en el alta de la API genérica [WAS-ADR-0058] | Adoptada en F3 (decisión D3) en el alta genérica; los comandos de `/api/sgspe/**` usan la tabla fija `idempotency_key` con el mismo contrato (ASM-065) | Retirar `idempotency_key` cuando la plataforma ofrezca la clave a las rutas de una app |
```

En la tabla de §4.1, reemplazar las filas de UPS-07, UPS-11 y UPS-18 por:

```markdown
| UPS-07 Campo FILE e IMAGE | F3 | Resuelta en wasichai 0.6.0: `evidence.file` es un campo `FILE` de wasichai-files; SGSPE reemplaza la descarga nativa de `evidence` para aplicar `DOWNLOAD_RESTRICTED` (ADR-0014) | `model/model.json`; `sgspe.evidence` | Nada |
| UPS-11 Módulo de notificaciones | F3–F4 | Resuelta: wasichai-notifications (ADR-0018); SGSPE decide destinatarios y textos (ASM-012) | `sgspe.notification` | Nada |
| UPS-18 Idempotencia en la API genérica (`Idempotency-Key`) | F3 | Resuelta en el alta genérica (WAS-ADR-0058); los comandos de `/api/sgspe/**` usan la tabla fija `idempotency_key` con el mismo contrato (ASM-065) | `sgspe.common`; [API §3.5](api.md#35-idempotencia-idempotency-key) | La tabla `idempotency_key`, cuando la plataforma ofrezca la clave a las rutas de una app |
```

- [ ] **Paso 5: Actualizar las tablas fijas del modelo de datos**

En `docs/database-model.md` §2.3, reemplazar los dos primeros bullets por:

```markdown
- `idempotency_key` (transversal, desde F3) y `knowledge_chunk` (BC-18, F7) no son Custom Objects: son tablas fijas del
  esquema `sgspe` (ASM-065, RQ-AI-008, CON-052). Los avisos no tienen tablas propias desde la decisión D2: los guarda el
  módulo wasichai-notifications en su esquema (ASM-012, ADR-0018).
- Cada paquete dueño registra su propia `ModuleMigration`, como un módulo más [WAS-ADR-0026]:
  - `sgspe_common`, en `classpath:db/sgspe/common`, con orden 200, en F3, para `idempotency_key`;
  - `sgspe_ai`, en `classpath:db/sgspe/ai`, en F7 con el RAG documental (RF-AIK-006, RQ-AI-008).
```

En el tercer bullet, cambiar «Las tres usan un orden mayor» por «Las dos usan un orden mayor»; en el quinto, cambiar
«(`sgspe.common`, `sgspe.notification` y `sgspe.ai`)» por «(`sgspe.common` y `sgspe.ai`)»; y reemplazar el sexto por:

```markdown
- No hay claves foráneas entre esquemas: ni hacia `app_data`, porque cada organización tiene su propia tabla física, ni
  hacia las organizaciones de `wasichai`. `project_id` y `evidence_id` de `knowledge_chunk` son referencias lógicas que valida
  el paquete.
```

Reemplazar la sección `## 18. BC-14 Notificaciones` entera (su intro, el diagrama y las fichas de `notification` y
`notification_delivery`) por:

```markdown
## 18. BC-14 Notificaciones

BC-14 no tiene objetos ni tablas propias desde la decisión D2 (ASM-012, ADR-0018): los avisos, su estado por persona, los
envíos por correo y las preferencias viven en las tablas del módulo wasichai-notifications (`notifications`,
`notification_targets`, `notification_receipts`, `notification_deliveries`, `notification_preferences`), que SGSPE no lee
ni escribe con SQL: publica con `Notifications.publish` y lee la bandeja con la API del módulo. Los avisos salen de
listeners y trabajos, nunca de las entidades (RN-NTF-001); sus claves están en `workflows.md` («Avisos»).
```

Reemplazar la ficha de `### idempotency_key` de §22 (desde su título hasta el bullet «La escribe y la lee solo
`sgspe.common`…» incluido) por:

```markdown
### `idempotency_key`

**Clave de idempotencia** (Transversal). Clave `Idempotency-Key` de cada comando de `/api/sgspe/**` que la trae, con la
respuesta que recibió la primera petición, escrita en la misma transacción que el comando y purgada por JOB-08 después de
`wasichai.idempotency.ttl`; el alta genérica usa la tabla nativa de wasichai, con el mismo contrato (UPS-18). Tabla fija
`sgspe.idempotency_key`, no Custom Object (§2.3). Requisitos: RNF-INT-002, ASM-065, UPS-18.

| Campo | Tipo | Req. | Único | Descripción | Fuente |
|---|---|---|---|---|---|
| `organization_id` | uuid | sí | compuesto | Tenant; toda consulta filtra por él. | ASM-065 |
| `user_id` | uuid | sí | compuesto | Usuario que hizo la petición: la clave vale por organización y usuario. | ASM-065 |
| `request_key` | text | sí | compuesto | Valor de la cabecera `Idempotency-Key`, de 1 a 128 caracteres ASCII imprimibles. | RNF-INT-002 |
| `request_sha256` | text | sí | — | SHA-256 del método, la ruta y el cuerpo con sus claves ordenadas: con la misma clave, otra petición es un `422`. | RNF-INT-002 |
| `response_status` | integer | sí | — | Estado de la primera respuesta. | RNF-INT-002 |
| `response_body` | text | sí | — | Cuerpo de la primera respuesta, que un reenvío recibe byte por byte; lleva lo que quien llama podía leer. | RNF-INT-002 |
| `response_etag` | text | no | — | `ETag` de la primera respuesta. | RNF-INT-002 |
| `response_location` | text | no | — | `Location` de la primera respuesta. | RNF-INT-002 |
| `created_at` | timestamptz | sí | — | Momento de la primera petición; `now()` por defecto y base del plazo y de la purga. | ASM-065 |

- **Flags:** no aplican; es una tabla fija sin metadata de wasichai.
- **Únicos:** clave primaria (`organization_id`, `user_id`, `request_key`).
- **Índices:** (`created_at`).
- **Acciones declaradas:** no aplican.
- **Workflow:** ninguno.
- **Fase:** F3 · Release 1 (MVP).
- Lo que está en curso no es una columna: es un candado de transacción (`pg_try_advisory_xact_lock`) sobre la clave, que no
  espera; una segunda petición con la clave en curso recibe `409` con `Retry-After`. Una fila significa que el comando se
  confirmó; un comando que falló no deja fila y la clave queda libre (`api.md` §3.5).
- Una fila más antigua que el plazo se borra antes de leerla, así que el plazo es exacto aunque la purga se atrase. La escribe
  y la lee solo `sgspe.common`, con `DatabaseClient` (RNF-INT-002).
```

En §26, reemplazar la fila de «Notificaciones y envíos» por:

```markdown
| Avisos y envíos | Tablas de wasichai-notifications | El módulo purga los avisos resueltos o vencidos después de `wasichai.notifications.retention` (90 días); los envíos se van con su aviso | RF-NTF-001, ADR-0018 |
```

y la de «Claves de idempotencia» por:

```markdown
| Claves de idempotencia | `sgspe.idempotency_key` | JOB-08 borra cada día las más antiguas que `wasichai.idempotency.ttl` (24 h por defecto), el mismo plazo de la cabecera nativa; guardan la respuesta de la primera petición, nunca su cuerpo | ASM-065, RNF-INT-002 |
```

Como §18 pierde las fichas de `notification` y `notification_delivery`, en `model/test_design_docs.py`
(`test_totales_de_las_fichas`) cambiar:

```python
        self.assertEqual(len(cards), 93)
```

por:

```python
        # 91 desde F3: las tablas de avisos de §18 se retiraron con la decisión D2 (ASM-012)
        self.assertEqual(len(cards), 91)
```

- [ ] **Paso 6: Actualizar la fila F3 del roadmap**

En `docs/roadmap.md`, en la tabla de fases (la fila que empieza `| F3 | 12, más las tablas fijas…`), reemplazar
«12, más las tablas fijas `notification`, `notification_delivery` e `idempotency_key`» por «12, más la tabla fija
`idempotency_key`», «+ `-automation`, + `-documents`» por «+ `-automation`, + `-documents`, + `-files`, + `-notifications`»
y «`sgspe.notification`; `SupportPolicyGuard` de `sgspe.investment`» por «`sgspe.notification`, `sgspe.investment`
(`SupportPolicyGuard`)».

En la sección `## F3 · Compromisos y evidencias`, reemplazar las filas «Objetos (12)», «Starters y capacidades», «Código
SGSPE», «Endpoints» y «Riesgos» por:

```markdown
| Objetos (12) | BC-05: `commitment`, `commitment_action`, `commitment_milestone`, `commitment_reschedule`, `commitment_dependency`, `delivery`, `commitment_candidate`. BC-06: `agreement`, `agreement_party`, `agreement_clause`. BC-12: `evidence` (archivo en un campo `FILE`). BC-13: `task`. Tabla fija: `idempotency_key` (transversal). BC-14 sin tablas propias: los avisos son del módulo wasichai-notifications (ASM-012) |
| Starters y capacidades | + `-automation` (con `AutomationDrain`), + `-documents`, + `-files` (campo `FILE`, almacenamiento compatible con S3 con MinIO solo en desarrollo) y + `-notifications` (bandeja, SSE, correo y preferencias); frontend + `@wasichai/automation` y + `@wasichai/documents`; SMTP de prueba en el compose; AWS SDK v2 |
| Código SGSPE | `sgspe.commitment`: `CommitmentWriteGuard`, `CommitmentDerivationsListener`, reprogramación, entregas, alta, aceptación y duplicado de candidatos. `sgspe.agreement`: `AgreementWriteGuard`. `sgspe.evidence`: `EvidenceWriteGuard`, alta y vínculos, y la descarga controlada por las dos rutas. `sgspe.deadline`: `DeadlineJob` (JOB-01) para compromisos, acuerdos y tareas, y la fuente de avisos de plazo. `sgspe.notification`: destinatarios por asignación y textos de los avisos sobre wasichai-notifications. `sgspe.investment`: `SupportPolicyGuard`. `sgspe.common`: `Idempotency-Key` de los comandos con la tabla fija `idempotency_key` y el contrato nativo. `StakeholderFollowUpJob` (JOB-03) empieza a crear tareas de evaluación periódica y de inactividad. `RetentionJob` (JOB-08) purga desde F3, con SQL propio y sin `asPlatform`, las claves vencidas (ASM-065, RNF-INT-002) |
| Endpoints | `POST /api/sgspe/commitments/{id}/reschedules`; `POST /api/sgspe/commitments/{id}/deliveries`; `PATCH /api/sgspe/deliveries/{id}`; `POST /api/sgspe/commitment-candidates`; `POST /api/sgspe/commitment-candidates/{id}/accept`; `POST /api/sgspe/commitment-candidates/{id}/duplicate`; `POST /api/sgspe/evidence`; `GET /api/sgspe/evidence/{id}/content`; `PATCH /api/sgspe/evidence/{id}`; `POST /api/sgspe/evidence/{id}/links`; `DELETE /api/sgspe/evidence/{id}/links/{object}/{recordId}`; tablero `commitments`; exportación `commitment-matrix`. De wasichai: subida de archivos (`POST /api/objects/evidence/files/file`), bandeja (`/api/auth/me/notifications`) y documentos emitidos |
| Riesgos | Escalamientos sin asignaciones que coincidan (el aviso no se crea y queda una advertencia en el log); el nivel 2 reutiliza `approve_senior` de WF-D; la descarga nativa de un campo `FILE` no conoce la clasificación (SGSPE la reemplaza para `evidence`); un documento emitido congela el registro entero sin permisos de campo (UPS-23), así que los tipos de documento solo se declaran sobre objetos sin campos restringidos y el «Acta de entrega» espera (ASM-090) |
```

En la fila «IDs abiertos», reemplazar `UPS-07, UPS-11, UPS-18; ` por `UPS-23; ` en «Condicionan»: las tres quedan resueltas
y la nueva condiciona el acta de entrega.

- [ ] **Paso 7: Verificar los documentos**

Run:

```bash
python3 tools/check_traceability.py && (cd model && python3 -m unittest -v test_design_docs test_model 2>&1 | tail -3)
```

Expected: `check_traceability` sin IDs faltantes y `OK` en las pruebas del modelo (`design_docs.cards()` sigue leyendo la
ficha de `idempotency_key`, ya no encuentra las de `notification` y cuenta 91 fichas: el plan 3.1 deja el total en 93, porque
reescribe la ficha de `evidence` sin agregar ni quitar fichas, y sus totales de F3 no cuentan tablas fijas).

- [ ] **Paso 8: Commit**

```bash
git add docs/adr/0014-almacenamiento-de-objetos.md docs/adr/0018-notificaciones.md docs/adr/README.md \
  docs/requirements-analysis.md docs/platform-fit.md docs/database-model.md docs/roadmap.md model/test_design_docs.py
git commit -m "$(cat <<'EOF'
docs: registra las decisiones D1 a D3 de la fase 3

Evidencias en el campo FILE de wasichai-files, avisos de wasichai-notifications y la clave de
idempotencia de los comandos propios con el contrato nativo. Acepta ADR-0014 y ADR-0018, reescribe
ASM-012, ASM-013, ASM-065, UPS-07, UPS-11 y UPS-18, agrega ASM-089 y ASM-090 (un tipo de
documento solo sobre un objeto sin campos restringidos, UPS-23) y deja sin tablas propias de
avisos el modelo de datos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

Sin integración.

### Tarea 2: Las piezas comunes de F3 que no leen el modelo

Lo que comparten las tareas siguientes y no depende del modelo de F3, así que puede empezar antes de que el plan 3.1 se
integre: `Records` deshace vínculos; el catálogo da los estados de un workflow y las relaciones MANY_TO_MANY; los parámetros y
políticas de F3 del proyecto tienen su valor por defecto; los objetos de F3 tienen alcance por proyecto y la reclasificación
de una evidencia pide motivo; un comando rechaza los atributos que el objeto no tiene o que fija él mismo; los montos se
acotan antes de sumarlos (Review Focus 5); los trabajos crean tareas por `generator_key`; `Commitments` es la única fuente de
los conjuntos de estados de WF-D (dashboards.md §3.2), que usan las guardas, JOB-01, los avisos y los tableros; y
`DomainApiTest` gana los ayudantes de los comandos (motivo, `If-Match` y el código de un error). También la clasificación de
los paquetes nuevos en `ArchitectureTest`. Las pruebas de esta tarea arman su propio catálogo: ninguna lee el `model.json` de
F3. Lo que sí lo lee (los grupos de «exactamente uno de», la transición de comando y la base de las pruebas de F3) es la
Tarea 2b.

**Archivos:**
- Modificar: `src/main/kotlin/sgspe/common/Records.kt`, `ModelCatalog.kt`, `ProjectParameters.kt`, `StrictBody.kt`,
  `FieldChangeReasonGuard.kt`
- Modificar: `src/main/kotlin/sgspe/scope/ProjectScope.kt`
- Crear: `src/main/kotlin/sgspe/common/Amounts.kt`, `src/main/kotlin/sgspe/common/JobTasks.kt`,
  `src/main/kotlin/sgspe/commitment/Commitments.kt`
- Modificar: `src/test/kotlin/sgspe/DomainApiTest.kt`, `src/test/kotlin/sgspe/ArchitectureTest.kt`
- Probar: `src/test/kotlin/sgspe/common/{AmountsTest,ModelCatalogF3Test}.kt`,
  `src/test/kotlin/sgspe/scope/ProjectScopeF3Test.kt`

**Interfaces:**
- Consume: lo de `main` (`87f4108`); nada del plan 3.1.
- Produce:
  - `Records.unlink(objectName: String, id: UUID, relationship: String, otherId: UUID, reason: String?)`. Los trabajos usan
    `Records.asPlatform(organizationId, source, block)`, que ya está en `main`.
  - `ModelCatalog.states(objectName: String): List<String>`; `ModelCatalog.manyToMany(name: String): Relationship?`.
  - `ProjectParameters.integer(project, "due_soon_days" | "escalation_first_days" | "escalation_second_days")` (7, 7, 15) y
    `ProjectParameters.policy(project, "policy_workshop_commitments_blocked" | "policy_senior_approval_for_dialogue" |
    "policy_internal_approval" | "policy_cap_blocks_approval")` (false, true, true, true).
  - `StrictBody.requireAttributes(catalog: ModelCatalog, objectName: String, attributes: Map<String, Any?>, fixed: Set<String> = emptySet(), prefix: String = "attributes.")`.
  - `Amounts.parse(value: Any?, field: String): BigDecimal?` (400 `INVALID_NUMBER` u `OUT_OF_RANGE`).
  - `JobTasks.ensure(key: String, attributes: Map<String, Any?>, reason: String): Boolean`; `JobTasks.TASK`,
    `JobTasks.GENERATOR_KEY`.
  - `object Commitments` (`COMMITMENT`, `RESCHEDULE`, `DEPENDENCY`, `DELIVERY`, `CANDIDATE`, `STAKEHOLDERS`,
    `EVIDENCE_DELIVERY`, `PENDING`, `FULFILLED`, `COMMITTED`, `CLOSED`, `AFTER_APPROVAL`, `EXECUTING`, `APPROVALS`).
  - `ProjectScope.ROOTS` y `CHILDREN` con los objetos de F3; `FieldChangeReasonGuard.FIELDS` con `evidence.classification`.
  - En `DomainApiTest`: `withReason(text)`, `ifMatch(objectName, id, token)` y `code(response)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/AmountsTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

// los montos de un comando se acotan antes de sumarlos (la lección de la Tarea 17 de F2: un exponente enorme agota la memoria)
class AmountsTest {
    private fun code(value: Any?): String = assertThrows<RuleException> { Amounts.parse(value, "attributes.delivered_amount") }.violations.single().message

    @Test
    fun `un monto valido pasa como decimal, y vacio es null`() {
        assertEquals(BigDecimal("1500.50"), Amounts.parse("1500.50", "x"))
        assertEquals(0, BigDecimal("1500.5").compareTo(Amounts.parse(1500.5, "x")))
        assertEquals(BigDecimal("999999999999999.9999"), Amounts.parse("999999999999999.9999", "x"))
        assertNull(Amounts.parse(null, "x"))
        assertNull(Amounts.parse("  ", "x"))
    }

    @Test
    fun `lo que no es un numero es INVALID_NUMBER y lo que se sale de las cotas, OUT_OF_RANGE`() {
        assertEquals("INVALID_NUMBER", code("mil"))
        assertEquals("OUT_OF_RANGE", code("-1"))
        assertEquals("OUT_OF_RANGE", code("1e999999999"))
        assertEquals("OUT_OF_RANGE", code(1e300))
        assertEquals("OUT_OF_RANGE", code("1000000000000000"))
        assertEquals("OUT_OF_RANGE", code("1.00001"))
        assertEquals("OUT_OF_RANGE", code("1".repeat(41)))
    }
}
```

Crear `src/test/kotlin/sgspe/common/ModelCatalogF3Test.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// lo que F3 lee del catálogo: los estados de un workflow en el orden de sus transiciones y las relaciones MANY_TO_MANY
class ModelCatalogF3Test {
    private val catalog =
        ModelCatalog.parse(
            """
            {"objects":[{"name":"evidence","fields":[]},{"name":"delivery","fields":[]}],
             "relationships":[{"name":"evidence_delivery","type":"MANY_TO_MANY","source":"evidence","target":"delivery"},
                              {"name":"delivery_commitment","source":"delivery","target":"commitment","fieldName":"commitment"}]}
            """.trimIndent(),
            """{"roles":[{"name":"APPROVER"}]}""",
            mapOf(
                "commitment" to
                    """
                    {"definition":{"transitions":[
                      {"name":"submit","from":"draft","to":"pending_approval","roles":["APPROVER"]},
                      {"name":"approve","from":"pending_approval","to":"approved","roles":["APPROVER"]},
                      {"name":"cancel_draft","from":"draft","to":"cancelled","roles":["APPROVER"]}]}}
                    """.trimIndent()
            )
        )

    @Test
    fun `los estados salen de las transiciones, sin repetir y en orden`() {
        assertEquals(listOf("draft", "pending_approval", "approved", "cancelled"), catalog.states("commitment"))
        assertEquals(emptyList<String>(), catalog.states("task"))
    }

    @Test
    fun `una relacion MANY_TO_MANY se encuentra por su nombre y una MANY_TO_ONE no`() {
        assertEquals("evidence", catalog.manyToMany("evidence_delivery")?.source)
        assertNull(catalog.manyToMany("delivery_commitment"))
    }
}
```

Crear `src/test/kotlin/sgspe/scope/ProjectScopeF3Test.kt`:

```kotlin
package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// el alcance de escritura de los objetos de F3 (security.md §9.2): las raíces por su proyecto, los hijos por su compromiso o
// su acuerdo, y la evidencia, que puede ser del tenant, solo cuando trae proyecto
class ProjectScopeF3Test {
    private val project = UUID.randomUUID()
    private val commitment = UUID.randomUUID()
    private val agreement = UUID.randomUUID()

    // las relaciones obligatorias de F3: todas menos evidence.project y delivery.commitment (exactamente uno, desde F5 con el aporte)
    private val required = { objectName: String, field: String -> !(objectName == "evidence" || (objectName == "delivery" && field == "commitment")) }
    private val parentProject: suspend (String, UUID) -> UUID? = { _, id -> if (id == commitment || id == agreement) project else null }

    private fun write(
        objectName: String,
        before: Map<String, Any?>?
    ) = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), RecordChangeKind.UPDATED, before, null, null, null)

    private fun resolve(
        objectName: String,
        before: Map<String, Any?>
    ) = runBlocking { ProjectResolver.resolve(write(objectName, before), before, required, parentProject) }

    @Test
    fun `cada objeto de F3 tiene alcance`() {
        for (name in listOf("commitment", "commitment_candidate", "agreement", "task", "evidence")) assertTrue(name in ProjectScope.ROOTS, name)
        for (name in listOf("commitment_action", "commitment_milestone", "commitment_reschedule", "commitment_dependency", "delivery")) {
            assertEquals("commitment" to "commitment", ProjectScope.CHILDREN[name], name)
        }
        for (name in listOf("agreement_party", "agreement_clause")) assertEquals("agreement" to "agreement", ProjectScope.CHILDREN[name], name)
    }

    @Test
    fun `una entrega y una clausula toman el proyecto de su padre`() {
        assertEquals(setOf(project), resolve("delivery", mapOf("commitment" to commitment.toString())).projects)
        assertEquals(setOf(project), resolve("agreement_clause", mapOf("agreement" to agreement.toString())).projects)
    }

    @Test
    fun `una evidencia del tenant no pide alcance, y una de proyecto si`() {
        val tenant = resolve("evidence", mapOf("project" to null))
        assertFalse(tenant.unresolved)
        assertTrue(tenant.projects.isEmpty())
        assertEquals(setOf(project), resolve("evidence", mapOf("project" to project.toString())).projects)
    }
}
```


- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.AmountsTest' --tests 'sgspe.common.ModelCatalogF3Test' --tests 'sgspe.scope.ProjectScopeF3Test'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference` a `Amounts`, `states` y `manyToMany`.

- [ ] **Paso 3: `Amounts`, `JobTasks` y los conjuntos de estados de WF-D**

Crear `src/main/kotlin/sgspe/common/Amounts.kt`:

```kotlin
package sgspe.common

import org.springframework.http.HttpStatus
import java.math.BigDecimal

// un monto o una cantidad que llega en el cuerpo de un comando, con cotas antes de hacer cuentas con él (la lección de la
// Tarea 17 de F2: un número con un exponente enorme agota la memoria al sumarlo). hasta 15 dígitos enteros y 4 decimales, y
// nunca negativo: un monto de compromiso o de entrega no pasa de ahí. un texto de más de 40 caracteres ni se convierte
object Amounts {
    const val MAX_TEXT = 40
    const val MAX_INTEGER_DIGITS = 15
    const val MAX_SCALE = 4

    // null si no vino; 400 INVALID_NUMBER si no es un número y OUT_OF_RANGE si se sale de las cotas, sobre `field`
    fun parse(
        value: Any?,
        field: String
    ): BigDecimal? {
        if (value == null || (value is String && value.isBlank())) return null
        val text = value.toString().trim()
        val number =
            if (text.length > MAX_TEXT) {
                null
            } else {
                Values.decimal(if (value is Number) value else text)
                    ?: throw Finding("INVALID_NUMBER", "$field debe ser un número", field, status = HttpStatus.BAD_REQUEST).toCodedException()
            }
        if (number == null || number.signum() < 0 || number.precision() - number.scale() > MAX_INTEGER_DIGITS || number.scale() > MAX_SCALE) {
            throw Finding(
                "OUT_OF_RANGE",
                "$field debe ser un número de 0 a ${"9".repeat(MAX_INTEGER_DIGITS)} con a lo sumo $MAX_SCALE decimales",
                field,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        return number
    }
}
```

Crear `src/main/kotlin/sgspe/common/JobTasks.kt`:

```kotlin
package sgspe.common

import org.springframework.stereotype.Component
import wasichai.core.common.ConflictException

// las tareas que crea un trabajo programado (JOB-01 para el escalamiento, JOB-03 para el seguimiento): una por generator_key,
// que el modelo declara único (RF-WFL-004), así que repetir la corrida nunca duplica. corre dentro de Records.asPlatform de una
// organización. si otra réplica la creó entre la lectura y el alta, el único de la base la frena (409) y cuenta como hecha
@Component
class JobTasks(
    private val records: Records
) {
    // true si la creó
    suspend fun ensure(
        key: String,
        attributes: Map<String, Any?>,
        reason: String
    ): Boolean {
        if (records.count(TASK, filters = mapOf(GENERATOR_KEY to key)) > 0) return false
        return try {
            records.create(TASK, attributes + (GENERATOR_KEY to key), reason)
            true
        } catch (_: ConflictException) {
            false
        }
    }

    companion object {
        const val TASK = "task"
        const val GENERATOR_KEY = "generator_key"
    }
}
```

Crear `src/main/kotlin/sgspe/commitment/Commitments.kt`:

```kotlin
package sgspe.commitment

// los nombres y los conjuntos de estados de WF-D que comparten las guardas, los comandos, JOB-01, los avisos y los tableros
// (workflows.md WF-D, dashboards.md §3.2): una sola fuente
object Commitments {
    const val COMMITMENT = "commitment"
    const val RESCHEDULE = "commitment_reschedule"
    const val DEPENDENCY = "commitment_dependency"
    const val DELIVERY = "delivery"
    const val CANDIDATE = "commitment_candidate"
    const val STAKEHOLDERS = "commitment_stakeholder"
    const val EVIDENCE_DELIVERY = "evidence_delivery"

    // donde JOB-01 calcula deadline_status: pendiente de cumplimiento
    val PENDING = setOf("approved", "in_progress", "blocked")

    // donde se lee fulfilled_date: cumplido
    val FULFILLED = setOf("fulfilled", "verified", "closed")

    // los montos aprobados de ASM-063: comprometido
    val COMMITTED = PENDING + FULFILLED

    // abierto: todos salvo closed y cancelled
    val CLOSED = setOf("closed", "cancelled")

    // desde approved la fecha de vencimiento solo cambia con una reprogramación (RF-COM-003)
    val AFTER_APPROVAL = COMMITTED + "cancelled"

    // donde se pueden registrar entregas y reprogramaciones: la ejecución
    val EXECUTING = PENDING

    // las transiciones que llegan a approved (D1, D2, D5, D7)
    val APPROVALS = setOf("approve", "approve_senior", "approve_direct")
}
```

- [ ] **Paso 4: `Records`, el catálogo y los parámetros**

En `src/main/kotlin/sgspe/common/Records.kt`, agregar antes del comentario `// los registros del otro lado de una
relación, como los ve quien llama` (`asPlatform` con el origen del trabajo ya está en `main`):

```kotlin
    // deshace un vínculo MANY_TO_MANY, como quien llama y con la marca (las evidencias, desde F3)
    suspend fun unlink(
        objectName: String,
        id: UUID,
        relationship: String,
        otherId: UUID,
        reason: String?
    ) = withContext(SgspeWrite) { related.unlink(objectName, id, relationship, otherId, reason) }

```

En `src/main/kotlin/sgspe/common/ModelCatalog.kt`, agregar después de `fun transition(objectName: String, name: String)`:

```kotlin
    // los estados del workflow de un objeto en el orden en que aparecen sus transiciones (de y a), sin repetir: la grilla de
    // las tablas que cuentan por estado (la matriz de compromisos en PUBLICO)
    fun states(objectName: String): List<String> = transitions(objectName).flatMap { listOf(it.from, it.to) }.distinct()

    // la relación MANY_TO_MANY `name` del modelo, o null
    fun manyToMany(name: String): Relationship? = relationships.firstOrNull { it.type == "MANY_TO_MANY" && it.name == name }
```

En `src/main/kotlin/sgspe/common/ProjectParameters.kt`, reemplazar `INTEGERS` y `POLICIES` por:

```kotlin
    private val INTEGERS =
        mapOf(
            "stakeholder_review_days" to 30L,
            "inactivity_days" to 90L,
            "meetings_per_month_target" to 2L,
            // F3: plazos y escalamiento (ASM-006, ASM-030)
            "due_soon_days" to 7L,
            "escalation_first_days" to 7L,
            "escalation_second_days" to 15L
        )
    private val POLICIES =
        mapOf(
            "policy_minutes_only_formal_types" to true,
            "policy_activation_preconditions" to true,
            // F3: compromisos (RN-ENG-001, RN-ENG-002, ASM-008) y tope de apoyo social (RN-INV-003)
            "policy_workshop_commitments_blocked" to false,
            "policy_senior_approval_for_dialogue" to true,
            "policy_internal_approval" to true,
            "policy_cap_blocks_approval" to true
        )
```

En `src/main/kotlin/sgspe/common/StrictBody.kt`, agregar antes de `fun objectOf(`:

```kotlin
    // los atributos de un registro que manda un comando (`prefix` es donde van en el cuerpo, attributes. por defecto): campos del
    // objeto o sus relaciones MANY_TO_ONE, nunca un derivado ni un campo que fija el propio comando (`fixed`). un nombre que el
    // objeto no tiene es un 400 que lo nombra; la API genérica lo ignoraría en silencio (api.md §3.2). desde F3
    fun requireAttributes(
        catalog: ModelCatalog,
        objectName: String,
        attributes: Map<String, Any?>,
        fixed: Set<String> = emptySet(),
        prefix: String = "attributes."
    ) {
        val obj = catalog.objectOrNull(objectName)
        val known = obj?.fields?.keys.orEmpty() + catalog.relationships.filter { it.source == objectName }.mapNotNull { it.fieldName }
        val derived = catalog.derivedFields(objectName)
        val violations =
            attributes.keys.sorted().mapNotNull { name ->
                when {
                    name !in known -> FieldViolation("$prefix$name", "no es un campo de $objectName")
                    name in derived -> FieldViolation("$prefix$name", "es derivado: lo calcula SGSPE")
                    name in fixed -> FieldViolation("$prefix$name", "lo fija este comando")
                    else -> null
                }
            }
        if (violations.isNotEmpty()) throw ValidationException("Atributo no admitido", violations)
    }

```

- [ ] **Paso 5: El alcance de los objetos de F3 y el motivo al reclasificar**

En `src/main/kotlin/sgspe/scope/ProjectScope.kt`, cerrar `ROOTS` con:

```kotlin
            "key_theme",
            "export_log",
            // F3: evidence también puede ser del tenant (project opcional), como export_log
            "commitment",
            "commitment_candidate",
            "agreement",
            "task",
            "evidence"
        )
```

y `CHILDREN` con:

```kotlin
            "locality_profile" to ("study" to "study"),
            // F3. delivery cuelga del compromiso (desde F5, también del aporte social)
            "commitment_action" to ("commitment" to "commitment"),
            "commitment_milestone" to ("commitment" to "commitment"),
            "commitment_reschedule" to ("commitment" to "commitment"),
            "commitment_dependency" to ("commitment" to "commitment"),
            "delivery" to ("commitment" to "commitment"),
            "agreement_party" to ("agreement" to "agreement"),
            "agreement_clause" to ("agreement" to "agreement")
        )
```

En `src/main/kotlin/sgspe/common/FieldChangeReasonGuard.kt`, reemplazar `FIELDS` por:

```kotlin
        // F3: reclasificar una evidencia lleva motivo (api.md §4.9, ASM-071)
        val FIELDS: Map<String, Set<String>> = mapOf("project" to setOf("stage", "exploration_phase"), "evidence" to setOf("classification"))
```

- [ ] **Paso 6: Los paquetes de F3 en `ArchitectureTest`**

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, reemplazar `NON_DOMAIN_PACKAGES` y `DOMAIN_PACKAGES` por:

```kotlin
        private val NON_DOMAIN_PACKAGES = listOf("common", "observability", "prod", "scope", "privacy", "dashboard", "deliverable", "ai")

        // deadline (BC-13, JOB-01) y notification (BC-14) son de dominio desde F3: no dependen de tableros, entregables ni IA
        private val DOMAIN_PACKAGES =
            listOf(
                "stakeholder",
                "engagement",
                "commitment",
                "agreement",
                "attention",
                "risk",
                "warning",
                "investment",
                "evidence",
                "territory",
                "deadline",
                "notification"
            )
```

- [ ] **Paso 7: Los ayudantes de los comandos en `DomainApiTest`**

En `src/test/kotlin/sgspe/DomainApiTest.kt`, agregar el import `import sgspe.common.IfMatch` y, después de `link(...)` y antes
de `// ---- datos de prueba ----`:

```kotlin
    // ---- los comandos de /api/sgspe/** (desde F3) ----

    // la cabecera de motivo de un comando o de una transición
    protected fun withReason(text: String = "motivo de prueba"): Map<String, String> = mapOf(REASON to reason(text))

    // el updatedAt del registro como lo pide If-Match
    protected fun ifMatch(
        objectName: String,
        id: String,
        token: String = this.token
    ): Map<String, String> = mapOf(IfMatch.HEADER to "\"${record(objectName, id, token)["updatedAt"].asString()}\"")

    // el código de una respuesta de error (P15: errors[0].message)
    protected fun code(response: Response): String = tree(response.body)["errors"][0]["message"].asString()

```

Son sobrecargas de los ayudantes privados de algunas pruebas de F2 (`code(body: String)` de
`StakeholderWorkflowGuardsApiTest` y `AreaOfInfluenceWorkflowGuardsApiTest`, `ifMatch(id: String)` de
`ProjectComponentsApiTest`), que no cambian.

- [ ] **Paso 8: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; pasan `AmountsTest` (2), `ModelCatalogF3Test` (2), `ProjectScopeF3Test` (3) y
`ArchitectureTest`.

- [ ] **Paso 9: Commit**

```bash
git add src/main/kotlin/sgspe/common/Records.kt src/main/kotlin/sgspe/common/ModelCatalog.kt \
  src/main/kotlin/sgspe/common/ProjectParameters.kt src/main/kotlin/sgspe/common/StrictBody.kt \
  src/main/kotlin/sgspe/common/FieldChangeReasonGuard.kt src/main/kotlin/sgspe/common/Amounts.kt \
  src/main/kotlin/sgspe/common/JobTasks.kt src/main/kotlin/sgspe/commitment/Commitments.kt \
  src/main/kotlin/sgspe/scope/ProjectScope.kt src/test/kotlin/sgspe/DomainApiTest.kt src/test/kotlin/sgspe/ArchitectureTest.kt \
  src/test/kotlin/sgspe/common/AmountsTest.kt src/test/kotlin/sgspe/common/ModelCatalogF3Test.kt \
  src/test/kotlin/sgspe/scope/ProjectScopeF3Test.kt
git commit -m "$(cat <<'EOF'
feat: piezas comunes de la fase 3

Vínculos que se deshacen, estados y relaciones del catálogo, parámetros de F3, alcance de los
objetos de F3 y motivo al reclasificar una evidencia, atributos estrictos en los comandos, montos
acotados, tareas por generator_key y los conjuntos de estados de WF-D; los ayudantes de los
comandos en la base de las pruebas de F2.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

Sin integración: la corre la Tarea 2b, cuando las guardas transversales ven los objetos del modelo de F3.

### Tarea 2b: Los grupos y el comando de F3 y la base de sus pruebas

Lo que sí lee el modelo del plan 3.1, así que empieza cuando la rama del plan 3.1 está integrada: los grupos de «exactamente
uno de» de `delivery`, `agreement_party` y `task` y la transición de comando `commitment_candidate.accept`, que
`GuardTablesCatalogTest` comprueba contra `model.json`; los valores por defecto de los parámetros de F3 frente a su
`defaultValue` del modelo (`ProjectParametersTest`); y `F3DomainApiTest`, la base de las pruebas de integración de este plan,
que extiende `F3ApiTest` del plan 3.1 y solo suma lo que traen los comandos de 3.2.

**Archivos:**
- Modificar: `src/main/kotlin/sgspe/common/ExactlyOneGuard.kt`, `CommandTransitionGuard.kt`
- Crear: `src/test/kotlin/sgspe/F3DomainApiTest.kt`
- Modificar: `src/test/kotlin/sgspe/common/ProjectParametersTest.kt`
- Probar: `GuardTablesCatalogTest` y `ProjectParametersTest` (existentes)

**Interfaces:**
- Consume: el modelo de F3 y `F3ApiTest` del plan 3.1 (`commitmentAttributes`, `commitment(project, owner, verifier,
  creator, attributes)`, `platformCreate`, `linkWithReason`, `agreement`, `task`, `awaitRun`), `F2ApiTest.stagedFile` y
  `PDF`; `withReason`, `ifMatch` y `code` de `DomainApiTest` (Tarea 2).
- Produce: `ExactlyOneGuard.GROUPS` y `CommandTransitionGuard.COMMANDS` con los de F3; `F3DomainApiTest` con
  `createEvidence`, `evidence(project, type, classification, account, links, headers)`, `commitment(project, attributes,
  creator, owner)` (una sobrecarga de la de `F3ApiTest` con un responsable nuevo si no se da y el verificador de la prueba) y
  `approvedCommitment(project, owner, attributes, start)`.

- [ ] **Paso 1: Los grupos y el comando de F3**

En `src/main/kotlin/sgspe/common/ExactlyOneGuard.kt`, cerrar `GROUPS` con:

```kotlin
                "network_node" to listOf(listOf("person", "organization")),
                // F3. los orígenes de F4, F5 y F8 (QCS, riesgos, alertas, aportes, programas, entregables) los agrega su fase
                "delivery" to listOf(listOf("commitment")),
                "agreement_party" to listOf(listOf("organization", "person")),
                "task" to listOf(listOf("commitment", "engagement", "agreement", "stakeholder"))
            )
```

En `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, cerrar `COMMANDS` con:

```kotlin
                "area_of_influence" to
                    mapOf(
                        "approve" to "POST /api/sgspe/areas-of-influence/{id}/approve",
                        "supersede" to "POST /api/sgspe/areas-of-influence/{id}/approve"
                    ),
                "commitment_candidate" to mapOf("accept" to "POST /api/sgspe/commitment-candidates/{id}/accept")
            )
```

- [ ] **Paso 2: Los valores por defecto de F3 frente al modelo**

En `src/test/kotlin/sgspe/common/ProjectParametersTest.kt`, en `los valores por defecto son los defaultValue de model json`,
reemplazar las listas de `integer` y de `policy` por:

```kotlin
        // desde F3, los plazos y las políticas que leen las guardas, JOB-01 y los tableros (el modelo del plan 3.1)
        listOf(
            "stakeholder_review_days",
            "inactivity_days",
            "meetings_per_month_target",
            "due_soon_days",
            "escalation_first_days",
            "escalation_second_days"
        ).forEach {
            assertEquals(defaults.getValue(it).toLong(), ProjectParameters.integer(empty, it), it)
        }
        listOf(
            "policy_minutes_only_formal_types",
            "policy_activation_preconditions",
            "policy_workshop_commitments_blocked",
            "policy_senior_approval_for_dialogue",
            "policy_internal_approval",
            "policy_cap_blocks_approval"
        ).forEach {
            assertEquals(defaults.getValue(it).toBooleanStrict(), ProjectParameters.policy(empty, it), it)
        }
```

- [ ] **Paso 3: La base de las pruebas de integración de F3**

Crear `src/test/kotlin/sgspe/F3DomainApiTest.kt` (sus ayudantes llaman endpoints que llegan en las tareas siguientes; aquí
solo compila):

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus

// la base de las pruebas de integración del plan 3.2 (F3) sobre F3ApiTest del plan 3.1, que ya da los compromisos con D1, D2
// y un solicitante, los acuerdos, las tareas, las altas como la plataforma y la espera de las automatizaciones; F2ApiTest da
// la subida para un campo FILE (stagedFile) y DomainApiTest, el motivo, If-Match y el código de un error. aquí solo lo que
// suman los comandos de 3.2: evidencias registradas con POST /api/sgspe/evidence y compromisos en draft o aprobados con un
// responsable y un verificador que no los mueven
abstract class F3DomainApiTest : F3ApiTest() {
    // el verificador de los compromisos de la prueba: D11 solo mira que quien verifica no sea el responsable
    private val verifier: Account by lazy { userWithRole("COMMITMENT_MANAGER") }

    // ---- evidencias ----

    protected fun createEvidence(
        attributes: Map<String, Any?>,
        links: List<Map<String, String>> = emptyList(),
        token: String = this.token,
        headers: Map<String, String> = emptyMap()
    ): Response = exchange("POST", "/api/sgspe/evidence", mapOf("attributes" to attributes, "links" to links), token, headers)

    // una evidencia subida y registrada por `account` (el admin si es null); su id
    protected fun evidence(
        project: String?,
        type: String = "ACTA DE ENTREGA",
        classification: String = "INTERNA",
        account: Account? = null,
        links: List<Map<String, String>> = emptyList(),
        headers: Map<String, String> = emptyMap()
    ): String {
        val attributes =
            mapOf("file" to stagedFile("evidence", "file", account), "evidence_type" to type, "classification" to classification) +
                listOfNotNull(project?.let { "project" to it })
        val response = createEvidence(attributes, links, account?.token ?: token, headers)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)["id"].asString()
    }

    // ---- compromisos ----

    // un compromiso en draft de F3ApiTest.commitment (D1, D2 y un solicitante), creado por `creator` (el admin si es null),
    // con un responsable nuevo si no se da
    protected fun commitment(
        project: String,
        attributes: Map<String, Any?> = emptyMap(),
        creator: Account? = null,
        owner: Account = userWithRole("COMMITMENT_MANAGER")
    ): String = commitment(project, owner, verifier, creator, attributes)

    // un compromiso aprobado por un APPROVER asignado (no quien lo registró, D4) y, si `start`, en ejecución
    protected fun approvedCommitment(
        project: String,
        owner: Account = userWithRole("COMMITMENT_MANAGER"),
        attributes: Map<String, Any?> = emptyMap(),
        start: Boolean = true
    ): String {
        val id = commitment(project, attributes, owner = owner)
        move("commitment", id, "submit", reason = "envío")
        move("commitment", id, "approve", member("APPROVER", project).token, "aprobación")
        if (start) move("commitment", id, "start", reason = "inicio")
        return id
    }
}
```

La sobrecarga de `commitment` no choca con la de `F3ApiTest`: la de 3.1 pide `owner` y `verifier` (`Account`) en segundo y
tercer lugar y esta, `attributes` (un `Map`), así que `commitment(project)`, `commitment(project, attributes = …)` y
`commitment(project, creator = …)` son de esta, y `commitment(project, owner = …, verifier = …)`, de la de 3.1. `PDF` y
`stagedFile` son los de `F2ApiTest`, y `withReason`, `ifMatch` y `code`, los de `DomainApiTest`.

- [ ] **Paso 4: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; pasan `GuardTablesCatalogTest` (los grupos de F3 sobre el modelo de 3.1) y
`ProjectParametersTest` (los siete valores de F3 iguales a su `defaultValue`).

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/common/ExactlyOneGuard.kt src/main/kotlin/sgspe/common/CommandTransitionGuard.kt \
  src/test/kotlin/sgspe/common/ProjectParametersTest.kt src/test/kotlin/sgspe/F3DomainApiTest.kt
git commit -m "$(cat <<'EOF'
feat: grupos y comando de F3 y la base de sus pruebas

Exactamente uno de en delivery, agreement_party y task, accept de WF-K como transición de comando,
los parámetros de F3 frente al modelo y F3DomainApiTest sobre F3ApiTest del plan 3.1.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

Integración: el coordinador corre `ProjectScopeApiTest`, `CrossGuardsApiTest` y `PermissionMatrixApiTest` (las guardas
transversales ven ahora los objetos de F3).

### Tarea 3: `Idempotency-Key` en los comandos de `/api/sgspe/**` (D3)

La tabla fija `sgspe.idempotency_key` con su propia migración (`ModuleMigration` `sgspe_common`, orden 200) y el componente
`Idempotency`, que da a los comandos el contrato de la cabecera nativa del alta genérica (WAS-ADR-0058): sin clave, el
comando corre como siempre; con clave, una sola vez por organización, usuario y clave dentro de `wasichai.idempotency.ttl`.
Lo en curso es un candado de transacción que no espera (`409` con `Retry-After: 1`); la clave, el comando y su respuesta se
confirman juntos; un reenvío recibe los mismos bytes con `Idempotent-Replayed: true`; otra petición con la clave es un `422`.
Las evaluaciones de F2 la honran desde aquí (api.md §4.3). Review Focus 1. No necesita el modelo de F3: sus pruebas de
integración extienden `AssessmentCommandApiTest`, una base sobre `DomainApiTest` con el registro de evaluaciones, así que la
tarea empieza en cuanto está la Tarea 2.

**Archivos:**
- Crear: `src/main/resources/db/sgspe/common/V1__idempotency_key.sql`
- Crear: `src/main/kotlin/sgspe/common/Idempotency.kt`
- Modificar: `src/main/kotlin/sgspe/stakeholder/AssessmentController.kt`
- Crear: `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt`
- Probar: `src/test/kotlin/sgspe/common/IdempotencyKeyTest.kt`, `src/test/kotlin/sgspe/common/IdempotencyApiTest.kt`

**Interfaces:**
- Consume: `Transactions.run`, `Callers.require` (F2); `ClusterLock.lockId` y `ModuleMigration` de wasichai; el `JsonMapper` de
  la aplicación; la propiedad `wasichai.idempotency.ttl` (la misma de la cabecera nativa); `withReason` y `code` de
  `DomainApiTest` (Tarea 2).
- Produce:
  - `CommandAnswer(status: HttpStatus, body: Any, etag: Instant? = null, location: String? = null)`: lo que devuelve un
    comando; `etag` es el `updatedAt` del registro que el portal sigue editando.
  - `Idempotency.respond(rawKey: String?, method: String, path: String, body: Any?, command: suspend () -> CommandAnswer):
    ResponseEntity<ByteArray>`, que usan los controladores de las tareas 5, 11, 12 y 14.
  - `Idempotency.purgeExpired(): Long` (JOB-08); `Idempotency.lockId(organizationId, userId, key): Long`;
    `Idempotency.IN_FLIGHT` = `IDEMPOTENCY_IN_FLIGHT`, `Idempotency.REUSED` = `IDEMPOTENCY_KEY_REUSED`.
  - `IdempotencyKey.HEADER`, `IdempotencyKey.REPLAYED`, `IdempotencyKey.parse`, `canonical`, `hash`, `entity`.
  - `AssessmentCommandApiTest` (`withKey`, `indicators`, `assessable`, `assess`, `assessments`), la base de
    `IdempotencyApiTest` y de `RetentionJobApiTest` (Tarea 4).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/IdempotencyKeyTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpHeaders
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ValidationException

// el contrato de Idempotency-Key de wasichai (WAS-ADR-0058) en los comandos de /api/sgspe/** (decisión D3): la cabecera, la
// identidad de una petición y la respuesta que recibe un reenvío
class IdempotencyKeyTest {
    private val json = JsonMapper.builder().build()

    @Test
    fun `sin cabecera no hay clave, y una clave valida pasa tal cual`() {
        assertNull(IdempotencyKey.parse(null))
        assertEquals("0b8e9d2a-3c1f", IdempotencyKey.parse("0b8e9d2a-3c1f"))
        assertEquals("x".repeat(128), IdempotencyKey.parse("x".repeat(128)))
    }

    @Test
    fun `una clave vacia, en blanco, de mas de 128 caracteres o fuera de ASCII imprimible es un 400 sobre la cabecera`() {
        for (bad in listOf("", "   ", "x".repeat(129), "clave-ñ", "tab\tclave")) {
            val e = assertThrows<ValidationException> { IdempotencyKey.parse(bad) }
            assertEquals(IdempotencyKey.HEADER, e.violations.single().field, bad)
        }
    }

    @Test
    fun `el orden de las claves y los espacios no cambian la peticion, la ruta, el metodo o un valor si`() {
        val a = IdempotencyKey.hash("POST", "/api/sgspe/x", IdempotencyKey.canonical(json, mapOf("b" to 1, "a" to mapOf("d" to 2, "c" to 3))))
        val b =
            IdempotencyKey.hash(
                "POST",
                "/api/sgspe/x",
                IdempotencyKey.canonical(json, json.readValue("""{ "a": {"c":3, "d":2},  "b":1 }""", Map::class.java))
            )
        assertEquals(a, b)
        assertNotEquals(a, IdempotencyKey.hash("POST", "/api/sgspe/y", IdempotencyKey.canonical(json, mapOf("a" to mapOf("c" to 3, "d" to 2), "b" to 1))))
        assertNotEquals(a, IdempotencyKey.hash("PATCH", "/api/sgspe/x", IdempotencyKey.canonical(json, mapOf("a" to mapOf("c" to 3, "d" to 2), "b" to 1))))
        assertNotEquals(a, IdempotencyKey.hash("POST", "/api/sgspe/x", IdempotencyKey.canonical(json, mapOf("a" to mapOf("c" to 3, "d" to 2), "b" to 2))))
        assertTrue(Regex("^[0-9a-f]{64}$").matches(a))
    }

    @Test
    fun `la respuesta guardada vuelve con sus bytes, su ETag y su Location, y el reenvio lo dice`() {
        val stored = StoredAnswer(201, """{"commitment":{"id":"1"}}""", "\"2026-10-09T15:00:00Z\"", "/api/objects/commitment/records/1")
        val first = IdempotencyKey.entity(stored, replayed = false)
        val again = IdempotencyKey.entity(stored, replayed = true)
        assertEquals(201, first.statusCode.value())
        assertEquals(stored.body, String(first.body!!, Charsets.UTF_8))
        assertEquals(stored.body, String(again.body!!, Charsets.UTF_8))
        assertEquals("\"2026-10-09T15:00:00Z\"", first.headers.getFirst(HttpHeaders.ETAG))
        assertEquals("/api/objects/commitment/records/1", first.headers.location.toString())
        assertNull(first.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals("true", again.headers.getFirst(IdempotencyKey.REPLAYED))
    }
}
```

Crear `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt`:

```kotlin
package sgspe.common

import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import java.util.UUID

// la base de las pruebas de Idempotency-Key y de JOB-08 (plan 3.2, tareas 3 y 4): el registro de evaluaciones de F2, el
// primer comando que honra la clave. no usa el modelo de F3, así que estas pruebas no esperan al plan 3.1
abstract class AssessmentCommandApiTest : DomainApiTest() {
    protected fun withKey(key: String = UUID.randomUUID().toString()): Map<String, String> = mapOf(IdempotencyKey.HEADER to key)

    // los quince indicadores de una evaluación (RF-STK-006), todos en `value`
    protected fun indicators(value: Int = 3): Map<String, Any?> =
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
        ).associateWith { value }

    // un grupo de interés caracterizado y quien lo evalúa (STAKEHOLDER_MANAGER asignado al proyecto de la prueba)
    protected fun assessable(): Pair<String, Account> {
        val project = testProject()
        val manager = member("STAKEHOLDER_MANAGER", project)
        val id = stakeholder(project, profileFields(manager, "AUTORIDAD LOCAL"))
        move("stakeholder", id, "profile", manager.token)
        return id to manager
    }

    protected fun assess(
        stakeholder: String,
        account: Account,
        headers: Map<String, String>,
        date: String = "2026-05-12",
        value: Int = 3
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/stakeholders/$stakeholder/assessments",
            mapOf("attributes" to indicators(value) + mapOf("assessment_date" to date, "basis" to "ENTREVISTA")),
            account.token,
            headers + withReason("evaluación")
        )

    protected fun assessments(stakeholder: String): Int =
        tree(send("GET", "/api/objects/stakeholder_assessment/records?stakeholder=$stakeholder", null, HttpStatus.OK))["totalElements"].asInt()
}
```

Crear `src/test/kotlin/sgspe/common/IdempotencyApiTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.util.UUID

// Idempotency-Key en los comandos de /api/sgspe/** con el contrato de la cabecera nativa (decisión D3, WAS-ADR-0058, api.md
// §3.5), probado con las evaluaciones (F2), que la honran desde F3: un reenvío recibe los mismos bytes con
// Idempotent-Replayed y no evalúa otra vez; otra petición con la misma clave es un 422; una en curso, un 409 con Retry-After; y
// un comando que falló deja la clave libre
class IdempotencyApiTest : AssessmentCommandApiTest() {
    @Autowired
    private lateinit var db: DatabaseClient

    @Autowired
    private lateinit var operator: TransactionalOperator

    @Test
    fun `un reenvio con la misma clave devuelve los mismos bytes y no evalua otra vez`() {
        val (id, manager) = assessable()
        val key = withKey()
        val first = assess(id, manager, key)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        assertNull(first.headers.getFirst(IdempotencyKey.REPLAYED))
        val again = assess(id, manager, key)
        assertEquals(HttpStatus.CREATED, again.status)
        assertEquals(first.body, again.body)
        assertEquals("true", again.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals(first.headers.getFirst(HttpHeaders.ETAG), again.headers.getFirst(HttpHeaders.ETAG))
        assertEquals(1, assessments(id))
    }

    @Test
    fun `la misma clave con otro cuerpo es un 422 que la nombra, y otra persona con la misma clave es otra clave`() {
        val (id, manager) = assessable()
        val key = withKey()
        assertEquals(HttpStatus.CREATED, assess(id, manager, key).status)
        val other = assess(id, manager, key, date = "2026-06-12")
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, other.status, other.body)
        assertEquals(IdempotencyKey.HEADER, tree(other.body)["errors"][0]["field"].asString())
        assertEquals(Idempotency.REUSED, code(other))
        val colleague = member("STAKEHOLDER_MANAGER", testProject())
        assertEquals(HttpStatus.CREATED, assess(id, colleague, key, date = "2026-06-12").status)
    }

    @Test
    fun `una clave mal formada es un 400 y un comando que falla deja la clave libre`() {
        val (id, manager) = assessable()
        assertEquals(HttpStatus.BAD_REQUEST, assess(id, manager, mapOf(IdempotencyKey.HEADER to "x".repeat(129))).status)
        val key = withKey()
        assertEquals(HttpStatus.BAD_REQUEST, assess(id, manager, key, value = 9).status)
        assertEquals(HttpStatus.CREATED, assess(id, manager, key).status)
        assertEquals(1, assessments(id))
    }

    @Test
    fun `una peticion con la clave en curso recibe 409 con Retry-After`() {
        val (id, manager) = assessable()
        val key = UUID.randomUUID().toString()
        val me = me(manager.token)
        val organization = UUID.fromString(me["organizationId"].asString())
        val user = UUID.fromString(me["userId"].asString())
        // otra transacción tiene el candado de la clave mientras llega la petición
        runBlocking {
            operator.executeAndAwait {
                db
                    .sql("SELECT pg_advisory_xact_lock(:id)")
                    .bind("id", Idempotency.lockId(organization, user, key))
                    .fetch()
                    .rowsUpdated()
                    .awaitSingle()
                val busy = assess(id, manager, mapOf(IdempotencyKey.HEADER to key))
                assertEquals(HttpStatus.CONFLICT, busy.status, busy.body)
                assertEquals("1", busy.headers.getFirst(HttpHeaders.RETRY_AFTER))
                assertEquals(Idempotency.IN_FLIGHT, code(busy))
            }
        }
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to key)).status)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.IdempotencyKeyTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'IdempotencyKey'` y `'StoredAnswer'`.

- [ ] **Paso 3: La migración**

Crear `src/main/resources/db/sgspe/common/V1__idempotency_key.sql`:

```sql
-- Idempotency-Key de los comandos de /api/sgspe/** (decisión D3, ASM-065, api.md §3.5): el mismo contrato que la cabecera
-- nativa de wasichai en el alta genérica (WAS-ADR-0058). una fila por organización, usuario y clave con la respuesta que
-- recibió la primera petición, escrita en la transacción del comando: sin comando no hay clave, y sin clave no hay comando.
-- lo que está en curso lo dice un candado de transacción (pg_try_advisory_xact_lock), no una columna. JOB-08 borra las filas
-- vencidas. sin claves foráneas entre esquemas (database-model.md §2.3): la baja de un tenant las borra por organization_id
CREATE SCHEMA IF NOT EXISTS sgspe;

CREATE TABLE sgspe.idempotency_key (
    organization_id   uuid        NOT NULL,
    user_id           uuid        NOT NULL,
    request_key       text        NOT NULL,
    -- SHA-256 del método, la ruta y el cuerpo con sus claves ordenadas: con la misma clave, otra petición es un 422
    request_sha256    text        NOT NULL,
    response_status   integer     NOT NULL,
    -- la respuesta tal como salió la primera vez: un reenvío recibe los mismos bytes
    response_body     text        NOT NULL,
    response_etag     text,
    response_location text,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_key_pk PRIMARY KEY (organization_id, user_id, request_key),
    CONSTRAINT idempotency_key_length CHECK (length(request_key) BETWEEN 1 AND 128),
    CONSTRAINT idempotency_key_sha256 CHECK (request_sha256 ~ '^[0-9a-f]{64}$')
);

-- la purga de JOB-08 recorre por antigüedad
CREATE INDEX idempotency_key_created_idx ON sgspe.idempotency_key (created_at);
```

- [ ] **Paso 4: `Idempotency`**

Crear `src/main/kotlin/sgspe/common/Idempotency.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.FieldViolation
import wasichai.core.common.RetryLaterException
import wasichai.core.common.UnprocessableContentException
import wasichai.core.common.ValidationException
import wasichai.core.platform.ClusterLock
import wasichai.core.platform.ModuleMigration
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

// lo que responde un comando de /api/sgspe/**: el estado, el cuerpo (que se serializa con el JsonMapper de la aplicación),
// y si los tiene, el updatedAt del registro principal (va en ETag, entre comillas, como lo pide If-Match) y su ubicación
class CommandAnswer(
    val status: HttpStatus,
    val body: Any,
    val etag: Instant? = null,
    val location: String? = null
)

// la respuesta ya serializada: la de la primera petición, que un reenvío recibe byte por byte
data class StoredAnswer(
    val status: Int,
    val body: String,
    val etag: String?,
    val location: String?
)

// la cabecera Idempotency-Key con el contrato de wasichai (WAS-ADR-0058, decisión D3): de 1 a 128 caracteres ASCII
// imprimibles; el mismo método, la misma ruta y el mismo cuerpo (con sus claves ordenadas: el orden y los espacios no
// cuentan) son la misma petición
object IdempotencyKey {
    const val HEADER = "Idempotency-Key"
    const val REPLAYED = "Idempotent-Replayed"
    const val MAX_LENGTH = 128
    val RETRY_AFTER: Duration = Duration.ofSeconds(1)

    // null si no vino; mal formada es un 400 sobre la cabecera
    fun parse(raw: String?): String? {
        if (raw == null) return null
        val valid = raw.length in 1..MAX_LENGTH && raw.isNotBlank() && raw.all { it.code in 0x20..0x7E }
        if (!valid) {
            throw ValidationException("Idempotency-Key mal formada", HEADER, "envíe de 1 a $MAX_LENGTH caracteres ASCII imprimibles")
        }
        return raw
    }

    // el cuerpo como lo leyó el controlador, con las claves ordenadas en todos los niveles
    fun canonical(
        json: JsonMapper,
        body: Any?
    ): String = json.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsString(body)

    fun hash(
        method: String,
        path: String,
        canonicalBody: String
    ): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$method $path\n$canonicalBody".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    // la respuesta http de una respuesta guardada; un reenvío lo dice en Idempotent-Replayed
    fun entity(
        stored: StoredAnswer,
        replayed: Boolean
    ): ResponseEntity<ByteArray> {
        val builder = ResponseEntity.status(stored.status).contentType(MediaType.APPLICATION_JSON)
        stored.etag?.let { builder.header(HttpHeaders.ETAG, it) }
        stored.location?.let { builder.location(URI.create(it)) }
        if (replayed) builder.header(REPLAYED, "true")
        return builder.body(stored.body.toByteArray(Charsets.UTF_8))
    }
}

// las claves de los comandos de /api/sgspe/** en la tabla fija sgspe.idempotency_key (database-model.md §22, ASM-065), con el
// mismo contrato que la cabecera nativa del alta genérica (WAS-ADR-0058): sin clave, el comando corre como siempre; con clave,
// corre una sola vez por organización, usuario y clave dentro de wasichai.idempotency.ttl (el mismo plazo que la nativa). lo
// en curso es un candado de transacción que no espera (pg_try_advisory_xact_lock): una segunda petición mientras corre la
// primera recibe 409 con Retry-After, nunca un segundo comando. la clave, el comando y la respuesta se confirman o se revierten
// juntos: un comando que falla deja la clave libre. un reenvío con la misma petición devuelve la respuesta guardada sin correr
// nada (ni permisos, ni guardas, ni auditoría), y con otra petición es un 422. JOB-08 purga las vencidas
@Component
class Idempotency(
    private val db: DatabaseClient,
    private val transactions: Transactions,
    private val callers: Callers,
    private val json: JsonMapper,
    @Value("\${wasichai.idempotency.ttl:24h}") private val ttl: Duration
) {
    suspend fun respond(
        rawKey: String?,
        method: String,
        path: String,
        body: Any?,
        command: suspend () -> CommandAnswer
    ): ResponseEntity<ByteArray> {
        val key = IdempotencyKey.parse(rawKey)
        if (key == null) return IdempotencyKey.entity(store(command()), replayed = false)
        val user = callers.require()
        val hash = IdempotencyKey.hash(method, path, IdempotencyKey.canonical(json, body))
        val (stored, replayed) = transactions.run { claimed(user.organizationId, user.userId, key, hash, command) }
        return IdempotencyKey.entity(stored, replayed)
    }

    private suspend fun claimed(
        organizationId: UUID,
        userId: UUID,
        key: String,
        hash: String,
        command: suspend () -> CommandAnswer
    ): Pair<StoredAnswer, Boolean> {
        val locked =
            db
                .sql("SELECT pg_try_advisory_xact_lock(:id) AS locked")
                .bind("id", lockId(organizationId, userId, key))
                .map { row, _ -> row.get("locked", java.lang.Boolean::class.java)?.booleanValue() == true }
                .one()
                .awaitSingle()
        if (!locked) {
            throw RetryLaterException(
                "Otra petición con esta Idempotency-Key todavía se está procesando: vuelva a enviarla en un momento",
                IdempotencyKey.RETRY_AFTER,
                listOf(FieldViolation(IdempotencyKey.HEADER, IN_FLIGHT))
            )
        }
        // vencida, la clave ya no existe aunque la purga no haya pasado: el plazo es exacto
        db
            .sql("DELETE FROM $TABLE WHERE $MATCH AND created_at < now() - make_interval(secs => :ttl)")
            .bind("organizationId", organizationId)
            .bind("userId", userId)
            .bind("key", key)
            .bind("ttl", ttl.toMillis() / 1000.0)
            .then()
            .awaitFirstOrNull()
        val previous =
            db
                .sql("SELECT request_sha256, response_status, response_body, response_etag, response_location FROM $TABLE WHERE $MATCH")
                .bind("organizationId", organizationId)
                .bind("userId", userId)
                .bind("key", key)
                .map { row, _ ->
                    row.get("request_sha256", String::class.java)!! to
                        StoredAnswer(
                            row.get("response_status", Integer::class.java)!!.toInt(),
                            row.get("response_body", String::class.java)!!,
                            row.get("response_etag", String::class.java),
                            row.get("response_location", String::class.java)
                        )
                }.one()
                .awaitFirstOrNull()
        if (previous != null) {
            if (previous.first != hash) {
                throw UnprocessableContentException(
                    "Esta Idempotency-Key ya se usó con otra petición: use una clave nueva",
                    listOf(FieldViolation(IdempotencyKey.HEADER, REUSED))
                )
            }
            return previous.second to true
        }
        val stored = store(command())
        db
            .sql(
                "INSERT INTO $TABLE (organization_id, user_id, request_key, request_sha256, response_status, response_body, response_etag, " +
                    "response_location) VALUES (:organizationId, :userId, :key, :hash, :status, :body, :etag, :location)"
            ).bind("organizationId", organizationId)
            .bind("userId", userId)
            .bind("key", key)
            .bind("hash", hash)
            .bind("status", stored.status)
            .bind("body", stored.body)
            .let { if (stored.etag == null) it.bindNull("etag", String::class.java) else it.bind("etag", stored.etag) }
            .let { if (stored.location == null) it.bindNull("location", String::class.java) else it.bind("location", stored.location) }
            .then()
            .awaitFirstOrNull()
        return stored to false
    }

    private fun store(answer: CommandAnswer): StoredAnswer =
        StoredAnswer(answer.status.value(), json.writeValueAsString(answer.body), answer.etag?.let { "\"$it\"" }, answer.location)

    // borra las claves vencidas de todas las organizaciones (JOB-08); devuelve cuántas
    suspend fun purgeExpired(): Long =
        db
            .sql("DELETE FROM $TABLE WHERE created_at < now() - make_interval(secs => :ttl)")
            .bind("ttl", ttl.toMillis() / 1000.0)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

    companion object {
        const val TABLE = "sgspe.idempotency_key"
        const val IN_FLIGHT = "IDEMPOTENCY_IN_FLIGHT"
        const val REUSED = "IDEMPOTENCY_KEY_REUSED"
        private const val MATCH = "organization_id = :organizationId AND user_id = :userId AND request_key = :key"

        // el candado de una clave: el mismo hash que usa ClusterLock, con un prefijo propio
        fun lockId(
            organizationId: UUID,
            userId: UUID,
            key: String
        ): Long = ClusterLock.lockId("sgspe.idempotency/$organizationId/$userId/$key")
    }
}

// la migración de las tablas fijas de sgspe.common (database-model.md §2.3, WAS-ADR-0026): su propia historia
// (flyway_history_sgspe_common) y un orden después de los módulos de wasichai, que van de 100 a 101
@Configuration(proxyBeanMethods = false)
class CommonMigration {
    @Bean
    fun sgspeCommonMigration(): ModuleMigration = ModuleMigration("sgspe_common", "classpath:db/sgspe/common", ORDER)

    companion object {
        const val ORDER = 200
    }
}
```

- [ ] **Paso 5: Las evaluaciones honran la clave**

Reemplazar `src/main/kotlin/sgspe/stakeholder/AssessmentController.kt` por:

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
import org.springframework.web.server.ServerWebExchange
import sgspe.common.CommandAnswer
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.StrictQuery
import wasichai.core.data.ChangeReason
import java.util.UUID

// api.md §4.3. desde F3 el registro honra Idempotency-Key (decisión D3): un reenvío con la misma clave devuelve la respuesta de
// la primera evaluación sin evaluar otra vez. el ETag es el updatedAt del grupo de interés, el registro que el portal sigue
// editando
@RestController
@RequestMapping("/api/sgspe/stakeholders/{id}/assessments")
class AssessmentController(
    private val assessments: Assessments,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun register(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            val result = assessments.register(id, body, ChangeReason.fromHeader(reason), ifMatch)
            CommandAnswer(
                HttpStatus.CREATED,
                result,
                result.stakeholder.updatedAt,
                "/api/objects/stakeholder_assessment/records/${result.assessment.id}"
            )
        }
    }

    @PostMapping("/preview")
    suspend fun preview(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): AssessmentPreview {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return assessments.preview(id, body)
    }
}
```

- [ ] **Paso 6: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `IdempotencyKeyTest` 4/4. `IdempotencyApiTest` compila.

- [ ] **Paso 7: Commit**

```bash
git add src/main/resources/db/sgspe/common/V1__idempotency_key.sql src/main/kotlin/sgspe/common/Idempotency.kt \
  src/main/kotlin/sgspe/stakeholder/AssessmentController.kt src/test/kotlin/sgspe/common/IdempotencyKeyTest.kt \
  src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt src/test/kotlin/sgspe/common/IdempotencyApiTest.kt
git commit -m "$(cat <<'EOF'
feat: Idempotency-Key en los comandos propios con el contrato nativo

Tabla fija sgspe.idempotency_key con su propia migración, el candado de transacción que no espera,
la respuesta guardada byte por byte, 422 para otra petición con la misma clave y 409 con
Retry-After si la primera sigue en curso. Las evaluaciones la honran desde F3 (decisión D3).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `IdempotencyApiTest` (5) y `StakeholderAssessmentApiTest` (la respuesta de las
evaluaciones pasa a ser los bytes de `CommandAnswer`, con el mismo JSON y un `ETag`). La primera corrida aplica la migración
`sgspe_common`: confirmar en el log `Migrating schema ... to version "1 - idempotency key"` de `flyway_history_sgspe_common`.

### Tarea 4: JOB-08 `RetentionJob`

La retención de F3 (workflows.md JOB-08, ASM-065): una vez al día y en una sola réplica, borra con SQL propio, sin
`asPlatform` ni `TenantDirectory`, las claves de `idempotency_key` más antiguas que el plazo. No escribe registros de
wasichai, así que no lleva etiqueta de fuente. Cada paso de retención es un `RetentionStep`; F8 suma el de los borradores.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/common/RetentionJob.kt`
- Modificar: `src/main/resources/application.yml`
- Probar: `src/test/kotlin/sgspe/common/RetentionSweepTest.kt`, `src/test/kotlin/sgspe/common/RetentionJobApiTest.kt`

**Interfaces:**
- Consume: `Idempotency.purgeExpired()` y `AssessmentCommandApiTest` (Tarea 3); `ClusterLock.tryLock`. Como la Tarea 3, no
  necesita el modelo de F3.
- Produce: `interface RetentionStep { val name: String; suspend fun purge(): Long }`; `RetentionJob.runOnce():
  Map<String, Long>?`; la propiedad `sgspe.jobs.retention.cron` (`0 15 4 * * *`).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/RetentionSweepTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// JOB-08 (workflows.md): cada paso de retención corre; uno que falla no detiene a los demás y la vuelta lanza al final
class RetentionSweepTest {
    private fun step(
        name: String,
        purge: suspend () -> Long
    ) = object : RetentionStep {
        override val name = name

        override suspend fun purge(): Long = purge()
    }

    @Test
    fun `devuelve lo purgado por paso`() {
        val result = runBlocking { RetentionSweep(listOf(step("a") { 3 }, step("b") { 0 })).run() }
        assertEquals(mapOf("a" to 3L, "b" to 0L), result)
    }

    @Test
    fun `un paso que falla no detiene a los demas y la vuelta lanza al final`() {
        var ran = false
        val e =
            assertThrows<IllegalStateException> {
                runBlocking {
                    RetentionSweep(
                        listOf(
                            step("roto") { error("sin base") },
                            step("bueno") {
                                ran = true
                                2
                            }
                        )
                    ).run()
                }
            }
        assertTrue(ran)
        assertTrue(e.message!!.contains("roto"), e.message)
    }
}
```

Crear `src/test/kotlin/sgspe/common/RetentionJobApiTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import java.util.UUID

// JOB-08 (workflows.md, «Pruebas de trabajos»): borra solo las claves de idempotency_key más antiguas que el plazo, y una
// clave vencida vuelve a correr el comando aunque la purga no haya pasado
class RetentionJobApiTest : AssessmentCommandApiTest() {
    @Autowired
    private lateinit var job: RetentionJob

    @Autowired
    private lateinit var db: DatabaseClient

    private fun count(key: String): Long =
        runBlocking {
            db
                .sql("SELECT count(*) AS n FROM ${Idempotency.TABLE} WHERE request_key = :key")
                .bind("key", key)
                .map { row, _ -> row.get("n", java.lang.Long::class.java)!!.toLong() }
                .one()
                .awaitSingle()
        }

    private fun age(
        key: String,
        hours: Int
    ) = runBlocking {
        db
            .sql("UPDATE ${Idempotency.TABLE} SET created_at = now() - make_interval(hours => :hours) WHERE request_key = :key")
            .bind("hours", hours)
            .bind("key", key)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    @Test
    fun `la purga borra solo las claves vencidas`() {
        val (id, manager) = assessable()
        val old = UUID.randomUUID().toString()
        val fresh = UUID.randomUUID().toString()
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to old)).status)
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to fresh), date = "2026-06-12").status)
        age(old, 25)
        val purged = runBlocking { job.runOnce() }!!
        assertTrue(purged.getValue("idempotency_key") >= 1)
        assertEquals(0, count(old))
        assertEquals(1, count(fresh))
    }

    @Test
    fun `una clave vencida vuelve a evaluar aunque la purga no haya pasado`() {
        val (id, manager) = assessable()
        val key = UUID.randomUUID().toString()
        assertEquals(HttpStatus.CREATED, assess(id, manager, mapOf(IdempotencyKey.HEADER to key)).status)
        age(key, 25)
        val again = assess(id, manager, mapOf(IdempotencyKey.HEADER to key))
        assertEquals(HttpStatus.CREATED, again.status)
        assertEquals(null, again.headers.getFirst(IdempotencyKey.REPLAYED))
        assertEquals(2, assessments(id))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.RetentionSweepTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'RetentionStep'`.

- [ ] **Paso 3: El trabajo**

Crear `src/main/kotlin/sgspe/common/RetentionJob.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import wasichai.core.platform.ClusterLock

// un paso de la retención (JOB-08): borra con SQL propio lo que una decisión registrada permite borrar, nunca registros de
// wasichai ni nada que referencie un registro (workflows.md, «Trabajos programados»). F3 trae el de idempotency_key; F8, el
// de los borradores de entregables
interface RetentionStep {
    val name: String

    // lo borrado
    suspend fun purge(): Long
}

// las claves de idempotencia vencidas, de todas las organizaciones en una sentencia (ASM-065): sin asPlatform ni
// TenantDirectory, porque la tabla fija no es de wasichai
@Component
class IdempotencyRetention(
    private val idempotency: Idempotency
) : RetentionStep {
    override val name: String = "idempotency_key"

    override suspend fun purge(): Long = idempotency.purgeExpired()
}

// la vuelta de JOB-08 sin candado, para probarla con pasos falsos: corre cada paso, uno que falla no detiene a los demás y
// al final lanza si alguno falló (como JOB-03), para que el fallo se vea
class RetentionSweep(
    private val steps: List<RetentionStep>
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(): Map<String, Long> {
        val purged = linkedMapOf<String, Long>()
        val failed = mutableListOf<String>()
        for (step in steps) {
            try {
                purged[step.name] = step.purge()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += step.name
                log.error("RetentionJob: falló el paso {}", step.name, e)
            }
        }
        check(failed.isEmpty()) { "RetentionJob falló en ${failed.joinToString(", ")} (se purgó $purged)" }
        return purged
    }
}

// JOB-08 RetentionJob (workflows.md): una vez al día, una sola réplica (ClusterLock.tryLock), cada paso de retención
@Component
class RetentionJob(
    steps: List<RetentionStep>,
    private val locks: ClusterLock
) {
    private val sweep = RetentionSweep(steps)

    // lo purgado por paso; null si otra réplica tenía el candado
    suspend fun runOnce(): Map<String, Long>? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use { sweep.run() }
    }

    companion object {
        const val LOCK = "sgspe.retention"
    }
}

// el reloj de JOB-08, en la zona de Lima; sgspe.jobs.enabled=false lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class RetentionSchedule(
    private val job: RetentionJob
) {
    @Scheduled(cron = "\${sgspe.jobs.retention.cron:0 15 4 * * *}", zone = "America/Lima")
    suspend fun daily() {
        job.runOnce()
    }
}
```

En `src/main/resources/application.yml`, reemplazar el comentario de `sgspe.jobs` y agregar al final de su bloque:

```yaml
  jobs:
    # trabajos de fondo (JOB-01 DeadlineJob, JOB-02 DerivedFieldsJob, JOB-03 StakeholderFollowUpJob y JOB-08 RetentionJob).
    # false los apaga: las pruebas lo fijan en Gradle y llaman a runOnce
```

```yaml
    retention:
      # JOB-08, una vez al día: borra las claves de idempotencia vencidas (wasichai.idempotency.ttl)
      cron: '0 15 4 * * *'
```

- [ ] **Paso 4: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `RetentionSweepTest` 2/2.

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/common/RetentionJob.kt src/main/resources/application.yml \
  src/test/kotlin/sgspe/common/RetentionSweepTest.kt src/test/kotlin/sgspe/common/RetentionJobApiTest.kt
git commit -m "$(cat <<'EOF'
feat: JOB-08 purga las claves de idempotencia vencidas

Una vez al día, en una réplica, con SQL propio y sin asPlatform; cada paso de retención es un
RetentionStep y uno que falla no detiene a los demás.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `RetentionJobApiTest` (2).

### Tarea 5: Evidencias: la guarda, el alta, el cambio y los vínculos (D1)

`evidence` guarda su archivo en el campo `file` de wasichai-files (D1): el portal lo sube con
`POST /api/objects/evidence/files/file` y registra la evidencia con `POST /api/sgspe/evidence` (`apiOnly`), que la crea y la
vincula en una transacción, con `Idempotency-Key`. `EvidenceWriteGuard` la hace inmutable (el archivo, `replaces` e
`import_batch_id` no cambian; no se borra) y exige `DOWNLOAD_RESTRICTED` para bajar una clasificación restringida
(ASM-071); el motivo al reclasificar lo exige `FieldChangeReasonGuard` (Tarea 2). `PATCH` cambia solo los datos
descriptivos, con `If-Match`; los vínculos van por su propio comando, con motivo si el destino es `requiresReason`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/evidence/EvidenceRules.kt`, `EvidenceWriteGuard.kt`, `Evidences.kt`
- Probar: `src/test/kotlin/sgspe/evidence/EvidenceRulesTest.kt`, `src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt`

**Interfaces:**
- Consume: `Records.create(objectName, attributes, reason, geometries)`, `Records.link`, `Records.unlink`, `Records.patch`,
  `Versions.check`, `IfMatch.required`, `Permissions`, `StrictBody.requireAttributes` (Tareas 2 y F2);
  `Idempotency.respond` y `CommandAnswer` (Tarea 3); `evidence.file` (FILE, los 8 `contentTypes` de la ficha) y las M2M
  `evidence_<objeto>` del plan 3.1; `F3DomainApiTest.evidence`, `createEvidence` y `F2ApiTest.stagedFile` (Tareas 2b y 3.1);
  `StoredFileGuard` de wasichai-files (un archivo ajeno es un `400` sobre `file`).
- Produce: `EvidenceRules` (`EVIDENCE`, `FILE`, `CLASSIFICATION`, `DOWNLOAD_RESTRICTED`, `RESTRICTED`, `fileId`,
  `restricted`, `downgrade`, `changedImmutable`, `linkRelationship`); `Evidences.create/patch/link/unlink`;
  `Evidences.lock(id): String` (`evidence:<id>`); los códigos `EVIDENCE_IMMUTABLE`, `EVIDENCE_NOT_DELETABLE`,
  `INVALID_LINK`, `FIELD_NOT_EDITABLE`, `REASON_REQUIRED` y `ACTION_REQUIRED` (`classification`).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/evidence/EvidenceRulesTest.kt`:

```kotlin
package sgspe.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// la evidencia inmutable y su clasificación (RF-DOC-001, RF-DOC-003, ASM-071), sin base
class EvidenceRulesTest {
    private val descriptor = mapOf("id" to "11111111-1111-1111-1111-111111111111", "name" to "acta.pdf", "sha256" to "a".repeat(64))

    @Test
    fun `el id del archivo sale del descriptor que da una lectura o del id que manda un alta`() {
        assertEquals("11111111-1111-1111-1111-111111111111", EvidenceRules.fileId(descriptor))
        assertEquals("11111111-1111-1111-1111-111111111111", EvidenceRules.fileId("11111111-1111-1111-1111-111111111111"))
        assertNull(EvidenceRules.fileId(null))
    }

    @Test
    fun `reenviar el descriptor tal como se leyo no cambia el archivo, otro id, otra version o otra importacion si`() {
        val before = mapOf("file" to descriptor, "replaces" to null, "import_batch_id" to null, "title" to "Acta")
        assertEquals(emptyList<String>(), EvidenceRules.changedImmutable(before, before + ("title" to "Acta firmada")))
        assertEquals(emptyList<String>(), EvidenceRules.changedImmutable(before, before + ("file" to descriptor["id"])))
        assertEquals(listOf("file"), EvidenceRules.changedImmutable(before, before + ("file" to "22222222-2222-2222-2222-222222222222")))
        assertEquals(listOf("replaces", "import_batch_id"), EvidenceRules.changedImmutable(before, before + ("replaces" to "x") + ("import_batch_id" to "y")))
    }

    @Test
    fun `bajar la clasificacion es pasar de restringida a no restringida`() {
        assertTrue(EvidenceRules.downgrade("CONFIDENCIAL", "INTERNA"))
        assertTrue(EvidenceRules.downgrade("DATOS PERSONALES", "PUBLICA"))
        assertFalse(EvidenceRules.downgrade("DATOS PERSONALES", "CONFIDENCIAL"))
        assertFalse(EvidenceRules.downgrade("INTERNA", "PUBLICA"))
        assertFalse(EvidenceRules.downgrade("INTERNA", "CONFIDENCIAL"))
    }

    @Test
    fun `cada vinculo es evidence_ mas el objeto, y lo descriptivo nunca incluye el archivo`() {
        assertEquals("evidence_delivery", EvidenceRules.linkRelationship("delivery"))
        assertFalse(EvidenceRules.FILE in EvidenceRules.DESCRIPTIVE)
        assertTrue(EvidenceRules.CLASSIFICATION in EvidenceRules.DESCRIPTIVE)
    }
}
```

Crear `src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt`:

```kotlin
package sgspe.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// las evidencias de F3 sobre wasichai-files (decisión D1, api.md §4.9, RF-DOC-001, RF-DOC-003, ASM-071): el alta con sus vínculos,
// la inmutabilidad y la reclasificación; la descarga con DOWNLOAD_RESTRICTED por las dos rutas llega en la Tarea 6
class EvidenceApiTest : F3DomainApiTest() {
    @Test
    fun `el alta guarda el descriptor del archivo con su SHA-256 y crea los vinculos en una transaccion`() {
        val project = testProject()
        val engagement = engagement(project)
        val response =
            createEvidence(
                mapOf("file" to stagedFile("evidence", "file"), "evidence_type" to "ACTA", "classification" to "INTERNA", "project" to project),
                listOf(mapOf("object" to "engagement", "recordId" to engagement))
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val created = tree(response.body)
        assertTrue(Regex("^[0-9a-f]{64}$").matches(created["attributes"]["file"]["sha256"].asString()))
        assertEquals("/api/objects/evidence/records/${created["id"].asString()}", response.headers.location.toString())
        val linked = tree(send("GET", "/api/objects/evidence/records/${created["id"].asString()}/related/evidence_engagement", null, HttpStatus.OK))
        assertEquals(engagement, linked["content"][0]["id"].asString())
    }

    @Test
    fun `un vinculo invalido, un atributo desconocido o un archivo ajeno no crean nada`() {
        val project = testProject()
        val file = stagedFile("evidence", "file")
        val bad =
            createEvidence(
                mapOf("file" to file, "evidence_type" to "ACTA", "classification" to "INTERNA"),
                listOf(
                    mapOf(
                        "object" to "project",
                        "recordId" to project
                    )
                )
            )
        assertEquals(HttpStatus.BAD_REQUEST, bad.status)
        assertEquals("INVALID_LINK", code(bad))
        val unknown = createEvidence(mapOf("file" to file, "evidence_type" to "ACTA", "classification" to "INTERNA", "sha256" to "x"))
        assertEquals(HttpStatus.BAD_REQUEST, unknown.status)
        assertEquals("attributes.sha256", problemFields(unknown.body).single())
        // StoredFileGuard: un archivo que subió otra persona no se adjunta
        val other = member("COMMUNITY_RELATIONS_OFFICER", project)
        val foreign = createEvidence(mapOf("file" to file, "evidence_type" to "ACTA", "classification" to "INTERNA", "project" to project), token = other.token)
        assertEquals(HttpStatus.BAD_REQUEST, foreign.status, foreign.body)
    }

    @Test
    fun `el archivo no cambia y la evidencia no se borra, ni por el comando ni por la API generica`() {
        val id = evidence(testProject())
        val change = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("file" to stagedFile("evidence", "file"))), token, ifMatch("evidence", id))
        assertEquals(HttpStatus.BAD_REQUEST, change.status)
        assertEquals("FIELD_NOT_EDITABLE", code(change))
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/evidence/records/$id", null, token).status)
        val replace =
            multipart("/api/objects/evidence/records/$id/files/file", mapOf("file" to ("otra.pdf" to PDF)), emptyMap(), token, ifMatch("evidence", id))
        assertTrue(replace.status.is4xxClientError, replace.text)
    }

    @Test
    fun `los datos descriptivos cambian con If-Match, y un PATCH sin cambios no mueve updatedAt`() {
        val id = evidence(testProject())
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Acta")), token).status)
        val headers = ifMatch("evidence", id)
        val changed = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Acta firmada")), token, headers)
        assertEquals(HttpStatus.OK, changed.status, changed.body)
        val updatedAt = tree(changed.body)["updatedAt"].asString()
        val same = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Acta firmada")), token, mapOf("If-Match" to "\"$updatedAt\""))
        assertEquals(updatedAt, tree(same.body)["updatedAt"].asString())
        assertEquals(HttpStatus.PRECONDITION_FAILED, patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("title" to "Otra")), token, headers).status)
    }

    @Test
    fun `bajar la clasificacion de una restringida exige DOWNLOAD_RESTRICTED y motivo`() {
        val project = testProject()
        val agent = member("FIELD_AGENT", project)
        val id = evidence(project, "FOTO", "DATOS PERSONALES", agent)
        val noReason = patch("/api/sgspe/evidence/$id", mapOf("attributes" to mapOf("classification" to "INTERNA")), agent.token, ifMatch("evidence", id))
        assertEquals(HttpStatus.BAD_REQUEST, noReason.status)
        val denied =
            patch(
                "/api/sgspe/evidence/$id",
                mapOf("attributes" to mapOf("classification" to "INTERNA")),
                agent.token,
                ifMatch("evidence", id) + withReason()
            )
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.body)
        assertEquals("ACTION_REQUIRED", code(denied))
        val manager = member("SOCIAL_MANAGER", project)
        val allowed =
            patch(
                "/api/sgspe/evidence/$id",
                mapOf("attributes" to mapOf("classification" to "INTERNA")),
                manager.token,
                ifMatch("evidence", id) + withReason()
            )
        assertEquals(HttpStatus.OK, allowed.status, allowed.body)
    }

    @Test
    fun `vincular y desvincular exigen motivo con un destino requiresReason, y FIELD_AGENT no toca la evidencia ajena`() {
        val project = testProject()
        val id = evidence(project)
        val commitment = commitment(project)
        val link = exchange("POST", "/api/sgspe/evidence/$id/links", mapOf("object" to "commitment", "recordId" to commitment), token)
        assertEquals(HttpStatus.BAD_REQUEST, link.status)
        assertEquals("REASON_REQUIRED", code(link))
        assertEquals(
            HttpStatus.NO_CONTENT,
            exchange("POST", "/api/sgspe/evidence/$id/links", mapOf("object" to "commitment", "recordId" to commitment), token, withReason()).status
        )
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/sgspe/evidence/$id/links/commitment/$commitment", null, token, withReason()).status)
        val agent = member("FIELD_AGENT", project)
        val foreign = exchange("POST", "/api/sgspe/evidence/$id/links", mapOf("object" to "engagement", "recordId" to engagement(project)), agent.token)
        assertEquals(HttpStatus.FORBIDDEN, foreign.status, foreign.body)
        assertEquals("NOT_OWN_RECORD", code(foreign))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.evidence.EvidenceRulesTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'EvidenceRules'`.

- [ ] **Paso 3: Las reglas y la guarda**

Crear `src/main/kotlin/sgspe/evidence/EvidenceRules.kt`:

```kotlin
package sgspe.evidence

import sgspe.common.Values

// las reglas de una evidencia, puras para las pruebas unitarias (RF-DOC-001, RF-DOC-003, ASM-071). desde F3 el archivo es un
// campo FILE de wasichai-files (decisión D1, WAS-ADR-0061): el registro guarda el descriptor {id, name, contentType, size,
// sha256}, nunca los bytes
object EvidenceRules {
    const val EVIDENCE = "evidence"
    const val FILE = "file"
    const val CLASSIFICATION = "classification"
    const val DOWNLOAD_RESTRICTED = "DOWNLOAD_RESTRICTED"

    // las clasificaciones cuya descarga exige DOWNLOAD_RESTRICTED (RF-DOC-003)
    val RESTRICTED = setOf("CONFIDENCIAL", "DATOS PERSONALES")

    // lo que nunca cambia después del alta: el archivo, la versión a la que reemplaza y la importación de origen. una
    // corrección es una evidencia nueva que reemplaza a la anterior (ASM-013)
    val IMMUTABLE = listOf(FILE, "replaces", "import_batch_id")

    // lo que PATCH /api/sgspe/evidence/{id} cambia (api.md §4.9): los datos descriptivos, nunca el archivo
    val DESCRIPTIVE = setOf("title", CLASSIFICATION, "document_date", "author", "captured_at", "people_identifiable", "photo_consent_given")

    // el id del archivo de un valor FILE: el descriptor que da una lectura, o el id tal cual lo manda un alta
    fun fileId(value: Any?): String? =
        when (value) {
            is Map<*, *> -> Values.text(value["id"])
            else -> Values.text(value)
        }

    fun restricted(classification: Any?): Boolean = Values.text(classification) in RESTRICTED

    // bajar la clasificación: de una restringida a una que no lo es (ASM-071)
    fun downgrade(
        before: Any?,
        after: Any?
    ): Boolean = restricted(before) && !restricted(after)

    // los campos inmutables que la escritura cambia
    fun changedImmutable(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<String> =
        IMMUTABLE.filter { field ->
            if (field == FILE) fileId(before[field]) != fileId(after[field]) else !Values.same(before[field], after[field])
        }

    // la relación MANY_TO_MANY de evidence hacia un objeto (database-model.md §16): evidence_<objeto>
    fun linkRelationship(target: String): String = "${EVIDENCE}_$target"
}
```

Crear `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`:

```kotlin
package sgspe.evidence

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.RuleGuard
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// la evidencia inmutable (RF-DOC-001, ASM-013) y su clasificación (RF-DOC-003, ASM-071): una evidencia no se borra, su archivo,
// la versión a la que reemplaza y su importación no cambian, y bajar la clasificación de una restringida exige
// DOWNLOAD_RESTRICTED, para que reclasificar no abra la descarga. evidence es apiOnly: la API genérica ya no la escribe; la
// guarda vale igual para los comandos de sgspe.evidence, /admin y cualquier otra puerta (WAS-ADR-0040). el motivo al cambiar
// la clasificación lo exige FieldChangeReasonGuard
@Component
@Order(GuardOrder.DOMAIN)
class EvidenceWriteGuard(
    private val view: WriteView,
    private val callers: Callers,
    private val permissions: Permissions
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != EvidenceRules.EVIDENCE) return emptyList()
        if (change.kind == RecordChangeKind.DELETED) {
            return listOf(
                Finding("EVIDENCE_NOT_DELETABLE", "Una evidencia no se borra: se reemplaza con una versión nueva", status = HttpStatus.CONFLICT)
            )
        }
        if (change.kind != RecordChangeKind.UPDATED || change.isLink()) return emptyList()
        val before = change.before.orEmpty()
        val after = view.after(change)
        val findings =
            EvidenceRules.changedImmutable(before, after).mapTo(mutableListOf()) { field ->
                Finding(
                    "EVIDENCE_IMMUTABLE",
                    "El campo $field de una evidencia no cambia: suba una evidencia nueva que reemplace a esta",
                    field,
                    status = HttpStatus.CONFLICT
                )
            }
        if (change.userId != null && EvidenceRules.downgrade(before[EvidenceRules.CLASSIFICATION], after[EvidenceRules.CLASSIFICATION])) {
            val user = callers.userOrNull()
            if (user == null || !permissions.has(user, EvidenceRules.DOWNLOAD_RESTRICTED, EvidenceRules.EVIDENCE)) {
                findings +=
                    Finding(
                        "ACTION_REQUIRED",
                        "Bajar la clasificación de una evidencia restringida exige la acción DOWNLOAD_RESTRICTED sobre evidence",
                        EvidenceRules.CLASSIFICATION,
                        status = HttpStatus.FORBIDDEN
                    )
            }
        }
        return findings
    }
}
```

- [ ] **Paso 4: Los comandos**

Crear `src/main/kotlin/sgspe/evidence/Evidences.kt`:

```kotlin
package sgspe.evidence

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.util.UUID

// un vínculo pedido: un registro de uno de los destinos de evidence_<objeto> (database-model.md §16)
data class EvidenceLink(
    val objectName: String,
    val recordId: UUID
)

// las evidencias (api.md §4.9). el archivo ya está en el almacenamiento: el portal lo sube antes con la ruta de wasichai-files
// (POST /api/objects/evidence/files/file, multipart, con CREATE sobre evidence), que valida tamaño y tipo, calcula el SHA-256
// y devuelve el id que aquí llega en attributes.file (decisión D1, WAS-ADR-0061). este comando crea el registro, que es
// apiOnly, y sus vínculos en una transacción, como quien llama: StoredFileGuard de wasichai exige que el archivo lo haya
// subido esa misma persona para evidence.file. los EXIF (fecha y lugar de captura) los lee el portal y llegan como
// attributes (captured_at, location en geometries)
@Service
class Evidences(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(
        body: Map<String, Any?>,
        reason: String?
    ): CommandAnswer {
        StrictBody.requireOnly(body, setOf("attributes", "geometries", "links"))
        val attributes = StrictBody.objectOf(body, "attributes")
        val geometries = StrictBody.objectOf(body, "geometries")
        StrictBody.requireAttributes(catalog, EvidenceRules.EVIDENCE, attributes)
        val links = links(body["links"])
        val user = callers.require()
        val grants = listOf(Actions.CREATE to EvidenceRules.EVIDENCE) + linkGrants(links)
        permissions.require(user, "Registrar una evidencia", *grants.toTypedArray())
        requireReasonFor(links.map { it.objectName }, reason)
        val evidence =
            transactions.run {
                val created = records.create(EvidenceRules.EVIDENCE, attributes, reason, geometries)
                val id = UUID.fromString(created.id)
                links.forEach { records.link(EvidenceRules.EVIDENCE, id, EvidenceRules.linkRelationship(it.objectName), it.recordId, reason) }
                created
            }
        return CommandAnswer(HttpStatus.CREATED, evidence, evidence.updatedAt, "/api/objects/evidence/records/${evidence.id}")
    }

    // los datos descriptivos; solo escribe lo que cambia, así un PATCH sin cambios deja el updatedAt como estaba
    suspend fun patch(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): RecordResponse {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes"))
        val changes = StrictBody.objectOf(body, "attributes")
        val notEditable = changes.keys.filter { it !in EvidenceRules.DESCRIPTIVE }.sorted()
        if (notEditable.isNotEmpty()) {
            throw Finding(
                "FIELD_NOT_EDITABLE",
                "Solo cambian los datos descriptivos de una evidencia (${EvidenceRules.DESCRIPTIVE.sorted().joinToString(", ")}); el archivo nunca",
                "attributes.${notEditable.first()}",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val user = callers.require()
        permissions.require(user, "Cambiar una evidencia", Actions.UPDATE to EvidenceRules.EVIDENCE)
        return transactions.locked(lock(id)) {
            val current = records.get(EvidenceRules.EVIDENCE, id)
            versions.check(EvidenceRules.EVIDENCE, id, expected, current.updatedAt, "La evidencia")
            val diff = changes.filter { (field, value) -> !Values.same(current.attributes[field], value) }
            if (diff.isEmpty()) current else records.patch(EvidenceRules.EVIDENCE, id, diff, reason)
        }
    }

    suspend fun link(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?
    ) {
        StrictBody.requireOnly(body, setOf("object", "recordId"))
        val target = linkOf(body, "")
        val user = callers.require()
        permissions.require(user, "Vincular una evidencia", *linkGrants(listOf(target)).toTypedArray())
        requireReasonFor(listOf(target.objectName), reason)
        transactions.run { records.link(EvidenceRules.EVIDENCE, id, EvidenceRules.linkRelationship(target.objectName), target.recordId, reason) }
    }

    suspend fun unlink(
        id: UUID,
        objectName: String,
        recordId: UUID,
        reason: String?
    ) {
        val target = linkOf(mapOf("object" to objectName, "recordId" to recordId.toString()), "")
        val user = callers.require()
        permissions.require(user, "Desvincular una evidencia", *linkGrants(listOf(target)).toTypedArray())
        requireReasonFor(listOf(target.objectName), reason)
        transactions.run { records.unlink(EvidenceRules.EVIDENCE, id, EvidenceRules.linkRelationship(target.objectName), target.recordId, reason) }
    }

    // ---- validación ----

    private fun links(raw: Any?): List<EvidenceLink> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: throw ValidationException("Vínculos inválidos", "links", "debe ser una lista de { object, recordId }")
        if (list.size > MAX_LINKS) throw ValidationException("Demasiados vínculos", "links", "a lo sumo $MAX_LINKS por evidencia")
        return list
            .mapIndexed { index, item ->
                @Suppress("UNCHECKED_CAST")
                val entry = item as? Map<String, Any?> ?: throw ValidationException("Vínculo inválido", "links[$index]", "debe ser { object, recordId }")
                StrictBody.requireOnly(entry, setOf("object", "recordId"), "links[$index].")
                linkOf(entry, "links[$index].")
            }.distinct()
    }

    // { object, recordId }: el objeto debe ser un destino de evidence_<objeto> del modelo; el id, un UUID
    private fun linkOf(
        entry: Map<String, Any?>,
        prefix: String
    ): EvidenceLink {
        val objectName = Values.text(entry["object"])
        if (objectName == null || catalog.manyToMany(EvidenceRules.linkRelationship(objectName))?.source != EvidenceRules.EVIDENCE) {
            throw Finding(
                "INVALID_LINK",
                "Una evidencia no se vincula con ${objectName ?: "ese objeto"}",
                "${prefix}object",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val recordId = Records.uuid(entry["recordId"]) ?: throw ValidationException("Vínculo inválido", "${prefix}recordId", "debe ser un UUID")
        return EvidenceLink(objectName, recordId)
    }

    // un vínculo escribe en los dos registros (WAS-ADR-0025): UPDATE sobre evidence y sobre cada objeto destino
    private fun linkGrants(links: List<EvidenceLink>): List<Pair<String, String>> =
        if (links.isEmpty()) emptyList() else listOf(Actions.UPDATE to EvidenceRules.EVIDENCE) + links.map { Actions.UPDATE to it.objectName }.distinct()

    // un vínculo con un objeto requiresReason exige motivo en los dos registros (WAS-ADR-0041): se pide antes de escribir
    private fun requireReasonFor(
        objects: List<String>,
        reason: String?
    ) {
        if (!reason.isNullOrBlank()) return
        val needs = objects.firstOrNull { catalog.objectOrNull(it)?.requiresReason == true } ?: return
        throw Finding(
            "REASON_REQUIRED",
            "Vincular una evidencia con $needs exige un motivo (X-Change-Reason)",
            "reason",
            status = HttpStatus.BAD_REQUEST
        ).toCodedException()
    }

    companion object {
        // el tope de vínculos de un alta: una evidencia prueba unos pocos registros, nunca cientos
        const val MAX_LINKS = 50

        fun lock(id: UUID): String = "${EvidenceRules.EVIDENCE}:$id"
    }
}

// api.md §4.9. el alta honra Idempotency-Key (decisión D3)
@RestController
@RequestMapping("/api/sgspe/evidence")
class EvidenceController(
    private val evidences: Evidences,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun create(
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { evidences.create(body, ChangeReason.fromHeader(reason)) }
    }

    @PatchMapping("/{id}")
    suspend fun patch(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<RecordResponse> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val record = evidences.patch(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity.ok().eTag("\"${record.updatedAt}\"").body(record)
    }

    @PostMapping("/{id}/links")
    suspend fun link(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<Void> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        evidences.link(id, body, ChangeReason.fromHeader(reason))
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/links/{object}/{recordId}")
    suspend fun unlink(
        @PathVariable id: UUID,
        @PathVariable("object") objectName: String,
        @PathVariable recordId: UUID,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<Void> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        evidences.unlink(id, objectName, recordId, ChangeReason.fromHeader(reason))
        return ResponseEntity.noContent().build()
    }
}
```

- [ ] **Paso 5: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `EvidenceRulesTest` 4/4.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/evidence/EvidenceRules.kt src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt \
  src/main/kotlin/sgspe/evidence/Evidences.kt src/test/kotlin/sgspe/evidence/EvidenceRulesTest.kt \
  src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt
git commit -m "$(cat <<'MSG'
feat: evidencias sobre el campo FILE de wasichai-files

Alta en dos pasos (el archivo por la ruta de wasichai-files, el registro y sus vínculos por un
comando con Idempotency-Key), EvidenceWriteGuard (inmutable, no se borra, bajar una clasificación
restringida exige DOWNLOAD_RESTRICTED) y el cambio de los datos descriptivos con If-Match.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

**Integración pendiente (coordinador):** `EvidenceApiTest` (6) y `EditAnyApiTest` (evidence declara `EDIT_ANY`).

### Tarea 6: La descarga de una evidencia por las dos rutas

`GET /api/sgspe/evidence/{id}/content` y la ruta nativa `GET /api/objects/evidence/records/{id}/files/{field}` aplican la misma
comprobación: READ sobre `evidence` como quien llama y, para CONFIDENCIAL y DATOS PERSONALES (o una clasificación que quien
llama no puede leer), la acción `DOWNLOAD_RESTRICTED`. Los bytes los entrega `FileService` de wasichai-files; la respuesta
es `attachment`, `nosniff`, `no-store` y `ETag` igual al SHA-256. La ruta nativa la atiende un controlador de SGSPE con el
segmento literal `evidence`, más específico que `{object}` (decisión 2 de este plan). Review Focus 2.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/evidence/EvidenceContent.kt`
- Modificar: `src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt`

**Interfaces:**
- Consume: `EvidenceRules` (Tarea 5); `wasichai.files.FileService.open(objectName, recordId, fieldName): FileDownload`.
- Produce: `EvidenceContent.download(id: UUID, field: String): ResponseEntity<Flux<DataBuffer>>`.

- [ ] **Paso 1: Escribir la prueba que falla**

En `src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt`, agregar el import `import org.springframework.http.HttpHeaders`,
cambiar la segunda línea del comentario de la clase por `// la inmutabilidad, la reclasificación y la descarga con
DOWNLOAD_RESTRICTED por las dos rutas` y agregar, antes de la prueba de los vínculos:

```kotlin
    @Test
    fun `una evidencia restringida no se descarga sin DOWNLOAD_RESTRICTED, tampoco por la ruta nativa`() {
        val project = testProject()
        val agent = member("FIELD_AGENT", project)
        val restricted = evidence(project, "FOTO", "CONFIDENCIAL")
        for (path in listOf("/api/sgspe/evidence/$restricted/content", "/api/objects/evidence/records/$restricted/files/file")) {
            val denied = exchange("GET", path, null, agent.token)
            assertEquals(HttpStatus.FORBIDDEN, denied.status, path)
            assertEquals("ACTION_REQUIRED", code(denied))
        }
        val open = evidence(project, "ACTA", "INTERNA")
        val download = exchange("GET", "/api/sgspe/evidence/$open/content", null, agent.token)
        assertEquals(HttpStatus.OK, download.status)
        val sha = record("evidence", open)["attributes"]["file"]["sha256"].asString()
        assertEquals("\"$sha\"", download.headers.getFirst(HttpHeaders.ETAG))
        assertTrue(download.headers.getFirst(HttpHeaders.CONTENT_DISPOSITION)!!.startsWith("attachment"))
        val manager = member("SOCIAL_MANAGER", project)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/evidence/records/$restricted/files/file", null, manager.token).status)
    }
```

- [ ] **Paso 2: Compilar y ver que la ruta no existe**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Expected: compila. Sin la Tarea 6, la prueba nueva fallaría en integración: la ruta `/api/sgspe/evidence/{id}/content`
responde `404` y la nativa entrega una evidencia CONFIDENCIAL a `FIELD_AGENT` con `200`.

- [ ] **Paso 3: La descarga**

Crear `src/main/kotlin/sgspe/evidence/EvidenceContent.kt`:

```kotlin
package sgspe.evidence

import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import wasichai.files.FileService
import java.nio.charset.StandardCharsets
import java.util.UUID

// la descarga de una evidencia (RF-DOC-003, api.md §4.9): READ sobre evidence como quien llama (un 403 o 404 si no la lee)
// y, si la clasificación es CONFIDENCIAL o DATOS PERSONALES, la acción DOWNLOAD_RESTRICTED. si quien llama no puede leer la
// clasificación, se la trata como restringida (falla cerrado). los bytes los entrega FileService de wasichai-files, que
// vuelve a leer el registro como quien llama; aquí se agregan el ETag (el SHA-256, para lo inmutable, api.md §3.10),
// attachment siempre (nunca se muestra en línea) y no-store
@Service
class EvidenceContent(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val files: FileService
) {
    suspend fun download(
        id: UUID,
        field: String
    ): ResponseEntity<Flux<DataBuffer>> {
        val user = callers.require()
        val evidence = records.get(EvidenceRules.EVIDENCE, id)
        val classification = evidence.attributes[EvidenceRules.CLASSIFICATION]
        val restricted = EvidenceRules.CLASSIFICATION !in evidence.attributes || EvidenceRules.restricted(classification)
        if (restricted && !permissions.has(user, EvidenceRules.DOWNLOAD_RESTRICTED, EvidenceRules.EVIDENCE)) {
            throw Finding(
                "ACTION_REQUIRED",
                "Descargar una evidencia CONFIDENCIAL o DATOS PERSONALES exige la acción DOWNLOAD_RESTRICTED sobre evidence",
                EvidenceRules.CLASSIFICATION,
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
        }
        val download = files.open(EvidenceRules.EVIDENCE, id, field)
        val file = download.file
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(file.contentType))
            .contentLength(file.sizeBytes)
            .eTag(file.sha256)
            .cacheControl(CacheControl.noStore())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(file.fileName, StandardCharsets.UTF_8)
                    .build()
                    .toString()
            ).header(NOSNIFF, "nosniff")
            .body(download.content)
    }

    companion object {
        const val NOSNIFF = "X-Content-Type-Options"
    }
}

// las dos rutas de descarga de una evidencia pasan por la misma comprobación. la nativa de wasichai-files
// (GET /api/objects/{object}/records/{id}/files/{field}) solo mira READ y los permisos de campo: aquí se la reemplaza para
// evidence con una ruta más específica (el segmento literal `evidence` gana al patrón {object}), así que la clasificación
// también vale por la API genérica y por los componentes de @wasichai/ui que bajan un campo FILE
@RestController
class EvidenceContentController(
    private val content: EvidenceContent
) {
    @GetMapping("/api/sgspe/evidence/{id}/content")
    suspend fun content(
        @PathVariable id: UUID,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return content.download(id, EvidenceRules.FILE)
    }

    @GetMapping("/api/objects/evidence/records/{id}/files/{field}")
    suspend fun native(
        @PathVariable id: UUID,
        @PathVariable field: String
    ): ResponseEntity<Flux<DataBuffer>> = content.download(id, field)
}
```

- [ ] **Paso 4: Correr el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`. `ArchitectureTest` acepta `wasichai.files.FileService` (no es una API interna).

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/evidence/EvidenceContent.kt src/test/kotlin/sgspe/evidence/EvidenceApiTest.kt
git commit -m "$(cat <<'MSG'
feat: descarga de evidencias con DOWNLOAD_RESTRICTED por las dos rutas

La ruta propia y la nativa de wasichai-files para evidence comprueban la clasificación antes de
entregar los bytes; attachment, nosniff, no-store y ETag igual al SHA-256.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
)"
```

**Integración pendiente (coordinador):** `EvidenceApiTest` (7). Si la ruta nativa respondiera `200` a `FIELD_AGENT`, el
controlador de wasichai-files ganó la resolución: revisar con `--debug` que `EvidenceContentController.native` está mapeado.

### Tarea 7: `CommitmentWriteGuard` y sus reglas de alta y edición

`CommitmentWriteGuard` es una sola guarda (`GuardOrder.DOMAIN`) que junta las reglas de compromisos, dependencias, entregas y
candidatos; cada familia es un `CommitmentRule` (no una `WriteRule`, para que la consulta en seco las corra una sola vez, a
través de la guarda). Una regla que no puede leer lo que necesita como quien escribe lanza `NotReadable` y la guarda la
vuelve un `403 OBJECT_NOT_READABLE` (P12). Esta tarea trae la primera familia: RN-ENG-001 al crear o cambiar el origen
(justificación o bloqueo por la política del proyecto), la fecha de vencimiento que desde `approved` solo cambia por la
reprogramación (decisión 7 de este plan), las dependencias sin ciclos (RF-COM-004) y las entregas congeladas de un
compromiso cumplido (`DELIVERY_FROZEN`, que prueba la Tarea 12). Los conjuntos de estados de WF-D (`Commitments`) llegan
en la Tarea 2.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt`, `CommitmentRecordRules.kt`
- Probar: `src/test/kotlin/sgspe/commitment/CommitmentDataTest.kt`, `CommitmentRecordRulesApiTest.kt`

**Interfaces:**
- Consume: `RuleGuard`, `Finding`, `GuardOrder`, `WriteView`, `WriteMark`, `ProjectParameters` (F2 y Tarea 2); `isLink`;
  `Commitments` (Tarea 2); `F3DomainApiTest` (Tarea 2b).
- Produce:
  - `interface CommitmentRule { suspend fun findings(change: RecordWrite, dryRun: Boolean): List<Finding> }`;
    `class NotReadable(objectName)`; `suspend fun <T> readAs(objectName: String, block: suspend () -> T): T`.
  - `CommitmentData.workshopFinding`, `originJudged`, `createsCycle` (puras).
  - Los códigos `JUSTIFICATION_REQUIRED` (400), `WORKSHOP_COMMITMENT_BLOCKED`, `DUE_DATE_RESCHEDULE_ONLY`, `DEPENDENCY_CYCLE`,
    `DEPENDENCY_GRAPH_TOO_LARGE`, `DELIVERY_FROZEN` (409), `DEPENDENCY_SELF` (400), `OBJECT_NOT_READABLE` (403).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/commitment/CommitmentDataTest.kt`:

```kotlin
package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// las reglas de datos de CommitmentWriteGuard (RN-ENG-001, RF-COM-004), sin base
class CommitmentDataTest {
    @Test
    fun `un compromiso de un taller exige justificacion y la politica que bloquea lo rechaza`() {
        assertNull(CommitmentData.workshopFinding("REUNION", null, blocked = false))
        assertNull(CommitmentData.workshopFinding("TALLER", "Absuelve una pregunta pendiente", blocked = false))
        val missing = CommitmentData.workshopFinding("REUNION INFORMATIVA", "  ", blocked = false)!!
        assertEquals("JUSTIFICATION_REQUIRED" to HttpStatus.BAD_REQUEST, missing.code to missing.status)
        assertEquals("origin_justification", missing.field)
        val blocked = CommitmentData.workshopFinding("TALLER", "con justificación", blocked = true)!!
        assertEquals("WORKSHOP_COMMITMENT_BLOCKED" to HttpStatus.CONFLICT, blocked.code to blocked.status)
    }

    @Test
    fun `el origen se juzga al crear o al cambiar la interaccion o la justificacion`() {
        assertEquals(true, CommitmentData.originJudged(RecordChangeKind.CREATED, emptySet()))
        assertEquals(true, CommitmentData.originJudged(RecordChangeKind.UPDATED, setOf("origin_justification")))
        assertEquals(false, CommitmentData.originJudged(RecordChangeKind.UPDATED, setOf("title")))
    }

    @Test
    fun `una dependencia que cierra un ciclo se detecta, y un grafo demasiado grande falla cerrado`() {
        val (a, b, c) = List(3) { UUID.randomUUID() }
        val edges = mapOf(b to listOf(c), c to listOf(a))
        // a → b cerraría a → b → c → a
        assertEquals(CommitmentData.CycleCheck.CYCLE, runBlocking { CommitmentData.createsCycle(a, b, 10) { edges[it].orEmpty() } })
        assertEquals(CommitmentData.CycleCheck.NONE, runBlocking { CommitmentData.createsCycle(c, a, 10) { emptyList() } })
        assertEquals(CommitmentData.CycleCheck.CYCLE, runBlocking { CommitmentData.createsCycle(a, a, 10) { emptyList() } })
        val chain = List(5) { UUID.randomUUID() }
        val next = { id: UUID -> listOfNotNull(chain.getOrNull(chain.indexOf(id) + 1)) }
        assertEquals(CommitmentData.CycleCheck.TOO_LARGE, runBlocking { CommitmentData.createsCycle(a, chain.first(), 3) { next(it) } })
    }
}
```

Crear `src/test/kotlin/sgspe/commitment/CommitmentRecordRulesApiTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// CommitmentWriteGuard, alta y edición (RN-ENG-001, RF-COM-003, RF-COM-004): por la API genérica, como el portal
class CommitmentRecordRulesApiTest : F3DomainApiTest() {
    // los atributos de un compromiso de F3ApiTest (D1 y D2), con un responsable y un verificador nuevos
    private fun attributes(project: String): Map<String, Any?> =
        commitmentAttributes(project, userWithRole("COMMITMENT_MANAGER"), userWithRole("COMMITMENT_MANAGER"))

    @Test
    fun `un compromiso de un taller exige justificacion, y con la politica que bloquea se rechaza`() {
        val project = testProject()
        val workshop = engagement(project, "TALLER")
        val body = attributes(project) + ("origin_engagement" to workshop)
        val missing = exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to body), token, withReason())
        assertEquals(HttpStatus.BAD_REQUEST, missing.status, missing.body)
        assertEquals("JUSTIFICATION_REQUIRED", code(missing))
        val justified = body + ("origin_justification" to "Absuelve una pregunta pendiente") + ("code" to uniqueName("C-"))
        assertEquals(HttpStatus.CREATED, exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to justified), token, withReason()).status)
        val strict = project(mapOf("policy_workshop_commitments_blocked" to true))
        val blocked = attributes(strict) + ("origin_engagement" to engagement(strict, "TALLER")) + ("origin_justification" to "x")
        val refused = exchange("POST", "/api/objects/commitment/records", mapOf("attributes" to blocked), token, withReason())
        assertEquals(HttpStatus.CONFLICT, refused.status)
        assertEquals("WORKSHOP_COMMITMENT_BLOCKED", code(refused))
    }

    @Test
    fun `desde approved la fecha de vencimiento no cambia por la API generica`() {
        val project = testProject()
        val draft = commitment(project)
        assertEquals(HttpStatus.OK, edit("commitment", draft, mapOf("due_date" to "2027-01-10"), headers = withReason()).status)
        val approved = approvedCommitment(project)
        val moved = edit("commitment", approved, mapOf("due_date" to "2027-01-10"), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, moved.status, moved.body)
        assertEquals("DUE_DATE_RESCHEDULE_ONLY", code(moved))
    }

    @Test
    fun `una dependencia de si mismo o que cierra un ciclo se rechaza`() {
        val project = testProject()
        val (a, b) = commitment(project) to commitment(project)
        assertEquals(
            HttpStatus.BAD_REQUEST,
            exchange(
                "POST",
                "/api/objects/commitment_dependency/records",
                mapOf("attributes" to mapOf("commitment" to a, "depends_on" to a, "blocking" to true)),
                token
            ).status
        )
        createRecord("commitment_dependency", mapOf("commitment" to a, "depends_on" to b, "blocking" to true))
        val cycle =
            exchange(
                "POST",
                "/api/objects/commitment_dependency/records",
                mapOf("attributes" to mapOf("commitment" to b, "depends_on" to a, "blocking" to false)),
                token
            )
        assertEquals(HttpStatus.CONFLICT, cycle.status, cycle.body)
        assertEquals("DEPENDENCY_CYCLE", code(cycle))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.commitment.CommitmentDataTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'CommitmentData'`.

- [ ] **Paso 3: La guarda**

Crear `src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt`:

```kotlin
package sgspe.commitment

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordWrite

// una familia de reglas de CommitmentWriteGuard (workflows.md WF-D y WF-K, domain-model §7.1): las del alta y la edición, las
// de aprobación (D1 a D5 y D8), las de cumplimiento y verificación (D9 a D11) y las de los candidatos. no es una WriteRule:
// la consulta en seco las corre a través de la guarda, una sola vez
interface CommitmentRule {
    suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding>
}

// lo que una regla no puede leer como quien escribe (P12): un objeto sin READ es un bloqueo OBJECT_NOT_READABLE, nunca un
// pase en silencio
class NotReadable(
    val objectName: String
) : RuntimeException("sin lectura de $objectName")

// lee como quien escribe; sin READ sobre el objeto, NotReadable
suspend fun <T> readAs(
    objectName: String,
    block: suspend () -> T
): T =
    try {
        block()
    } catch (_: ForbiddenException) {
        throw NotReadable(objectName)
    }

// CommitmentWriteGuard (sgspe.commitment): junta las reglas de compromisos, entregas, dependencias y candidatos. cada regla
// mira solo sus objetos y sus transiciones
@Component
@Order(GuardOrder.DOMAIN)
class CommitmentWriteGuard(
    private val rules: List<CommitmentRule>
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS) return emptyList()
        return rules.flatMap { rule ->
            try {
                rule.findings(change, dryRun)
            } catch (e: NotReadable) {
                listOf(
                    Finding(
                        "OBJECT_NOT_READABLE",
                        "La regla necesita leer ${e.objectName} y quien escribe no puede leerlo",
                        e.objectName,
                        status = HttpStatus.FORBIDDEN
                    )
                )
            }
        }
    }

    companion object {
        val OBJECTS = setOf(Commitments.COMMITMENT, Commitments.DEPENDENCY, Commitments.DELIVERY, Commitments.CANDIDATE)
    }
}
```

- [ ] **Paso 4: Las reglas de alta y edición**

Crear `src/main/kotlin/sgspe/commitment/CommitmentRecordRules.kt`:

```kotlin
package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.WriteMark
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las reglas de datos de los compromisos y sus hijos, puras para las pruebas unitarias
object CommitmentData {
    // RN-ENG-001: los talleres y las reuniones informativas no generan compromisos salvo con justificación (ASM-007)
    val WORKSHOP_TYPES = setOf("TALLER", "REUNION INFORMATIVA")

    // un alta o un cambio del origen se juzga; los demás cambios no
    fun originJudged(
        kind: RecordChangeKind,
        changed: Set<String>
    ): Boolean = kind == RecordChangeKind.CREATED || "origin_engagement" in changed || "origin_justification" in changed

    // RN-ENG-001 sobre el registro de después: null si pasa
    fun workshopFinding(
        originType: String?,
        justification: Any?,
        blocked: Boolean
    ): Finding? {
        if (originType !in WORKSHOP_TYPES) return null
        if (blocked) {
            return Finding(
                "WORKSHOP_COMMITMENT_BLOCKED",
                "El proyecto no admite compromisos nacidos de un taller o de una reunión informativa (RN-ENG-001)",
                "origin_engagement"
            )
        }
        if (Values.text(justification) != null) return null
        return Finding(
            "JUSTIFICATION_REQUIRED",
            "Un compromiso que nace de un taller o de una reunión informativa exige justificación (origin_justification)",
            "origin_justification",
            status = HttpStatus.BAD_REQUEST
        )
    }

    // si agregar la arista from → to cierra un ciclo: to llega a from siguiendo las dependencias. `next` da los compromisos de los
    // que depende uno; `max` acota los compromisos que se visitan (un grafo más grande falla cerrado, como un ciclo)
    suspend fun createsCycle(
        from: UUID,
        to: UUID,
        max: Int,
        next: suspend (UUID) -> List<UUID>
    ): CycleCheck {
        if (from == to) return CycleCheck.CYCLE
        val seen = mutableSetOf(to)
        val frontier = ArrayDeque(listOf(to))
        while (frontier.isNotEmpty()) {
            for (other in next(frontier.removeFirst())) {
                if (other == from) return CycleCheck.CYCLE
                if (seen.add(other)) {
                    if (seen.size > max) return CycleCheck.TOO_LARGE
                    frontier.addLast(other)
                }
            }
        }
        return CycleCheck.NONE
    }

    enum class CycleCheck { NONE, CYCLE, TOO_LARGE }
}

// CommitmentWriteGuard, alta y edición (domain-model §7.1): RN-ENG-001 al crear o cambiar el origen, la fecha de
// vencimiento que desde approved solo cambia con una reprogramación (RF-COM-003), las dependencias sin ciclos (RF-COM-004) y
// las entregas que ya sostienen un cumplimiento (api.md §4.5: corregirlas exige reopen_execution)
@Component
class CommitmentRecordRules(
    private val records: Records,
    private val view: WriteView
) : CommitmentRule {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when (change.objectName) {
            Commitments.COMMITMENT -> commitment(change)
            Commitments.DEPENDENCY -> dependency(change)
            Commitments.DELIVERY -> delivery(change)
            else -> emptyList()
        }

    private suspend fun commitment(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val changed = view.changed(change)
        val findings = mutableListOf<Finding>()
        if (CommitmentData.originJudged(change.kind, changed)) {
            val origin = Records.uuid(after["origin_engagement"])
            if (origin != null) {
                val type = readAs("engagement") { records.find("engagement", origin) }?.attributes?.get("engagement_type")
                val blocked =
                    Values.text(type) in CommitmentData.WORKSHOP_TYPES &&
                        ProjectParameters.policy(project(after), "policy_workshop_commitments_blocked")
                CommitmentData.workshopFinding(Values.text(type), after["origin_justification"], blocked)?.let { findings += it }
            }
        }
        val id = change.recordId
        if (change.kind == RecordChangeKind.UPDATED && id != null && "due_date" in changed && !WriteMark.present()) {
            val state = records.find(Commitments.COMMITMENT, id)?.state
            if (state in Commitments.AFTER_APPROVAL) {
                findings +=
                    Finding(
                        "DUE_DATE_RESCHEDULE_ONLY",
                        "Desde la aprobación, la fecha de vencimiento solo cambia con una reprogramación (POST /api/sgspe/commitments/{id}/reschedules)",
                        "due_date"
                    )
            }
        }
        return findings
    }

    private suspend fun dependency(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val from = Records.uuid(after["commitment"]) ?: return emptyList()
        val to = Records.uuid(after["depends_on"]) ?: return emptyList()
        if (from == to) {
            return listOf(Finding("DEPENDENCY_SELF", "Un compromiso no depende de sí mismo", "depends_on", status = HttpStatus.BAD_REQUEST))
        }
        val result =
            CommitmentData.createsCycle(from, to, MAX_GRAPH) { node ->
                readAs(Commitments.DEPENDENCY) {
                    records
                        .all(Commitments.DEPENDENCY, filters = mapOf("commitment" to node.toString()))
                        .filter { it.id != change.recordId?.toString() }
                        .mapNotNull { Records.uuid(it.attributes["depends_on"]) }
                }
            }
        return when (result) {
            CommitmentData.CycleCheck.NONE -> emptyList()
            CommitmentData.CycleCheck.CYCLE ->
                listOf(Finding("DEPENDENCY_CYCLE", "La dependencia cerraría un ciclo entre compromisos (RF-COM-004)", "depends_on"))
            CommitmentData.CycleCheck.TOO_LARGE ->
                listOf(Finding("DEPENDENCY_GRAPH_TOO_LARGE", "Las dependencias encadenan más de $MAX_GRAPH compromisos: no se pueden verificar", "depends_on"))
        }
    }

    // una entrega cuyo compromiso ya está cumplido sostiene esa transición: no cambia, no se vincula ni se desvincula hasta
    // reopen_execution
    private suspend fun delivery(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.UPDATED && change.kind != RecordChangeKind.DELETED) return emptyList()
        val commitment = Records.uuid(change.before?.get("commitment")) ?: return emptyList()
        val state = readAs(Commitments.COMMITMENT) { records.find(Commitments.COMMITMENT, commitment) }?.state
        if (state !in Commitments.FULFILLED) return emptyList()
        return listOf(
            Finding(
                "DELIVERY_FROZEN",
                "La entrega sostiene el cumplimiento del compromiso: devuélvalo a ejecución (reopen_execution) para corregirla",
                "commitment"
            )
        )
    }

    // el proyecto del registro, para sus políticas (ASM-019); sin lectura de project, NotReadable
    private suspend fun project(after: Map<String, Any?>): Map<String, Any?> {
        val id = Records.uuid(after["project"]) ?: return emptyMap()
        return readAs("project") { records.find("project", id) }?.attributes.orEmpty()
    }

    companion object {
        // los compromisos que la búsqueda de ciclos visita como mucho
        const val MAX_GRAPH = 500
    }
}
```

- [ ] **Paso 5: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `CommitmentDataTest` 3/3.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt \
  src/main/kotlin/sgspe/commitment/CommitmentRecordRules.kt src/test/kotlin/sgspe/commitment/CommitmentDataTest.kt \
  src/test/kotlin/sgspe/commitment/CommitmentRecordRulesApiTest.kt
git commit -m "$(cat <<'EOF'
feat: CommitmentWriteGuard con las reglas de alta y edición

Justificación de un compromiso nacido de un taller (RN-ENG-001), fecha de vencimiento solo por
reprogramación desde approved, dependencias sin ciclos y entregas congeladas de un compromiso
cumplido; una regla sin lectura es OBJECT_NOT_READABLE.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `CommitmentRecordRulesApiTest` (3) y, como punto de control, `WorkflowMatrixApiTest`
y `PermissionMatrixApiTest` de 3.1 (la guarda nueva no debe romper los recorridos de WF-D por la API genérica).

### Tarea 8: Las reglas de aprobación de WF-D (D1 a D5 y D8)

D1 y D2 al enviar y en cada aprobación (`pending_approval` admite cambios); D3 (la acción `APPROVE`, doble llave) y D4
(segregación: quien aprueba no registró el compromiso, comparando `created_by` en la base) en `approve` y `approve_senior`;
D5 (`approve` y `approve_direct` rechazan el compromiso que exige nivel superior, con la regla evaluada en el momento); D8
(`approve_direct` solo con `policy_internal_approval` en falso). D6 espera su dato (ASM-089) y D7 es la Tarea 9.
`SeniorApproval` es la regla de RN-ENG-002 que comparten la guarda, el derivado y los avisos: origen en una MESA DE DIALOGO o un
solicitante del grupo POLITICO, con la política del proyecto activa.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/commitment/SeniorApproval.kt`, `ApprovalRules.kt`
- Probar: `src/test/kotlin/sgspe/commitment/ApprovalDataTest.kt`, `CommitmentApprovalApiTest.kt`
- Modificar: `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`

**Interfaces:**
- Consume: `CommitmentRule` y `readAs` (Tarea 7); `Commitments` (Tarea 2); `Permissions.has`, `Callers`, `Records.related`,
  `Records.count` con `RecordCriterion` (F2); `WorkflowMatrixApiTest` del plan 3.1.
- Produce: `SeniorApproval.required(policy, originType, stakeholderGroups): Boolean`;
  `SeniorApprovalReader.required(commitmentId: UUID, attributes: Map<String, Any?>): Boolean` (Tareas 10 y 16);
  `ApprovalData.MATRIX`, `OWNER`, `firstMissing`; los códigos `FIELD_REQUIRED`, `STAKEHOLDER_REQUIRED`,
  `SENIOR_APPROVAL_REQUIRED`, `INTERNAL_APPROVAL_REQUIRED` (409), `ACTION_REQUIRED`, `SEGREGATION_OF_DUTIES` (403).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/commitment/ApprovalDataTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// D1, D2 y la regla de nivel superior de WF-D (RF-COM-001, RN-ENG-002), sin base
class ApprovalDataTest {
    private val complete =
        ApprovalData.MATRIX.associateWith { "x" } + ("owner_user_id" to "u") + ("owner_user_email" to "u@sgspe.test")

    @Test
    fun `falta el primero de la matriz o del responsable, en orden, execution_time es opcional`() {
        assertNull(ApprovalData.firstMissing(complete))
        assertNull(ApprovalData.firstMissing(complete + ("execution_time" to null)))
        assertEquals("objective", ApprovalData.firstMissing(complete + ("objective" to "") + ("unit" to null)))
        assertEquals("owner_user_email", ApprovalData.firstMissing(complete + ("owner_user_email" to null)))
    }

    @Test
    fun `una mesa de dialogo o un solicitante politico exigen nivel superior, solo con la politica activa`() {
        assertTrue(SeniorApproval.required(true, "MESA DE DIALOGO", emptyList()))
        assertTrue(SeniorApproval.required(true, "REUNION", listOf("ESTRATEGICO", "POLITICO")))
        assertFalse(SeniorApproval.required(true, "REUNION", listOf("ESTRATEGICO", null)))
        assertFalse(SeniorApproval.required(false, "MESA DE DIALOGO", listOf("POLITICO")))
    }
}
```

Crear `src/test/kotlin/sgspe/commitment/CommitmentApprovalApiTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// CommitmentWriteGuard en la aprobación (WF-D D1 a D5 y D8) y la consulta en seco: los casos que CommitmentWorkflowApiTest (3.1)
// deja a 3.2
class CommitmentApprovalApiTest : F3DomainApiTest() {
    @Test
    fun `submit exige cada campo de la matriz, el responsable y un solicitante, y execution_time no`() {
        val project = testProject()
        val noObjective = commitment(project, attributes = mapOf("objective" to null))
        val refused = transition("commitment", noObjective, "submit", reason = "envío")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("FIELD_REQUIRED", code(refused))
        assertEquals("objective", problemFields(refused.body).single())
        // el alta directa, sin el solicitante que vincula commitment()
        val attributes = commitmentAttributes(project, userWithRole("COMMITMENT_MANAGER"), userWithRole("COMMITMENT_MANAGER"))
        val alone = createRecord("commitment", attributes, withReason("alta de prueba"))
        assertEquals("STAKEHOLDER_REQUIRED", code(transition("commitment", alone, "submit", reason = "envío")))
        val noTime = commitment(project, attributes = mapOf("execution_time" to null))
        assertEquals(HttpStatus.OK, transition("commitment", noTime, "submit", reason = "envío").status)
    }

    @Test
    fun `quien registro no aprueba, y sin la accion APPROVE tampoco`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val own = commitment(project, creator = approver)
        move("commitment", own, "submit", reason = "envío")
        val segregation = transition("commitment", own, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.FORBIDDEN, segregation.status, segregation.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(segregation))
    }

    @Test
    fun `un compromiso de una mesa de dialogo solo se aprueba en nivel superior`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        move("commitment", id, "submit", reason = "envío")
        val approve = transition("commitment", id, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, approve.status, approve.body)
        assertEquals("SENIOR_APPROVAL_REQUIRED", code(approve))
        assertEquals(HttpStatus.OK, transition("commitment", id, "approve_senior", member("PROJECT_MANAGER", project).token, "aprobación superior").status)
    }

    @Test
    fun `approve_direct solo con la aprobacion interna desactivada`() {
        val active = testProject()
        val id = commitment(active)
        val manager = member("COMMITMENT_MANAGER", active)
        val refused = transition("commitment", id, "approve_direct", manager.token, "directa")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        val relaxed = project(mapOf("policy_internal_approval" to false))
        val direct = commitment(relaxed)
        assertEquals(HttpStatus.OK, transition("commitment", direct, "approve_direct", member("COMMITMENT_MANAGER", relaxed).token, "directa").status)
    }

    @Test
    fun `la consulta en seco dice lo mismo que la escritura real`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("unit" to null))
        val check =
            tree(send("POST", "/api/sgspe/transition-checks", mapOf("object" to "commitment", "recordId" to id, "transition" to "submit"), HttpStatus.OK))
        assertEquals(false, check["allowed"].asBoolean())
        assertEquals(listOf("FIELD_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
        assertEquals("unit", check["blocking"][0]["field"].asString())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.commitment.ApprovalDataTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'ApprovalData'` y `'SeniorApproval'`.

- [ ] **Paso 3: La regla de nivel superior**

Crear `src/main/kotlin/sgspe/commitment/SeniorApproval.kt`:

```kotlin
package sgspe.commitment

import org.springframework.stereotype.Component
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import java.util.UUID

// RN-ENG-002 (ASM-067): un compromiso nacido de una mesa de diálogo o pedido por un grupo de interés del grupo POLITICO
// requiere aprobación de nivel superior, si la política del proyecto está activa. la guarda (D5), los avisos y el derivado
// requires_senior_approval la evalúan en el momento con esta misma regla; la guarda nunca lee el derivado
object SeniorApproval {
    const val DIALOGUE = "MESA DE DIALOGO"
    const val POLITICAL = "POLITICO"

    fun required(
        policy: Boolean,
        originType: String?,
        stakeholderGroups: Collection<String?>
    ): Boolean = policy && (originType == DIALOGUE || POLITICAL in stakeholderGroups)
}

// lee lo que la regla necesita: la política del proyecto, el tipo de la interacción de origen y el grupo de influencia de los
// solicitantes (commitment_stakeholder), como quien llama o como la plataforma dentro de un trabajo
@Component
class SeniorApprovalReader(
    private val records: Records
) {
    suspend fun required(
        commitmentId: UUID,
        attributes: Map<String, Any?>
    ): Boolean {
        val project =
            Records
                .uuid(attributes["project"])
                ?.let { readAs("project") { records.find("project", it) } }
                ?.attributes
                .orEmpty()
        val policy = ProjectParameters.policy(project, "policy_senior_approval_for_dialogue")
        if (!policy) return false
        val origin = Records.uuid(attributes["origin_engagement"])
        val type = origin?.let { readAs("engagement") { records.find("engagement", it) } }?.attributes?.get("engagement_type")
        if (Values.text(type) == SeniorApproval.DIALOGUE) return true
        val groups =
            readAs("stakeholder") { records.related(Commitments.COMMITMENT, commitmentId, Commitments.STAKEHOLDERS) }
                .map { Values.text(it.attributes["influence_group"]) }
        return SeniorApproval.required(true, Values.text(type), groups)
    }
}
```

- [ ] **Paso 4: Las reglas de aprobación**

Crear `src/main/kotlin/sgspe/commitment/ApprovalRules.kt`:

```kotlin
package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import java.util.UUID

// D1 y D2 de WF-D, puras: los campos mínimos de la matriz de DOC-01 §9.3.1.2 (RF-COM-001) y el responsable (RF-COM-005,
// ASM-023), en el orden en que se informan. execution_time es opcional y amount ya es obligatorio desde el alta (RN-COM-004)
object ApprovalData {
    val MATRIX =
        listOf(
            "description",
            "objective",
            "resource_type",
            "quantity",
            "unit",
            "request_medium",
            "agreed_date",
            "due_date",
            "beneficiaries_count",
            "currency"
        )
    val OWNER = listOf("owner_user_id", "owner_user_email")

    // el primer campo que falta, o null
    fun firstMissing(attributes: Map<String, Any?>): String? = (MATRIX + OWNER).firstOrNull { Values.text(attributes[it]) == null }
}

// CommitmentWriteGuard, aprobación (workflows.md WF-D): D1 y D2 al enviar y en cada aprobación, porque pending_approval admite
// cambios; D3 (la acción APPROVE, doble llave, RF-WFL-002) y D4 (quien aprueba no registró el compromiso, RN-COM-005) en
// approve y approve_senior; D5 (approve y approve_direct rechazan el compromiso que exige nivel superior, RN-ENG-002); D8
// (approve_direct solo sin aprobación interna, ASM-008). D6 (políticas de empleo y servidumbres) espera el dato que GAP-016
// deja fuera de alcance (ASM-089) y D7 es SupportPolicyGuard. decide con permisos y datos, nunca con nombres de rol
@Component
class ApprovalRules(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val senior: SeniorApprovalReader
) : CommitmentRule {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition != SUBMIT && transition !in Commitments.APPROVALS) return emptyList()
        val id = change.recordId ?: return emptyList()
        val before = change.before.orEmpty()
        val findings = mutableListOf<Finding>()
        ApprovalData.firstMissing(before)?.let { field ->
            findings += Finding("FIELD_REQUIRED", "Falta $field: la matriz de compromisos lo exige antes de enviar y de aprobar (D1, D2)", field)
        }
        if (stakeholders(id) == 0) {
            findings += Finding("STAKEHOLDER_REQUIRED", "Falta al menos un grupo de interés solicitante (D1)", Commitments.STAKEHOLDERS)
        }
        if (transition == SUBMIT) return findings
        val user = callers.userOrNull()
        if (transition in DOUBLE_KEY) {
            if (user == null || !permissions.has(user, APPROVE, Commitments.COMMITMENT)) {
                findings += Finding("ACTION_REQUIRED", "Aprobar un compromiso exige la acción APPROVE sobre commitment (D3)", status = HttpStatus.FORBIDDEN)
            }
            if (user == null || createdBy(id, user.userId)) {
                findings +=
                    Finding(
                        "SEGREGATION_OF_DUTIES",
                        "Quien aprueba un compromiso no puede ser quien lo registró (D4, RN-COM-005)",
                        status = HttpStatus.FORBIDDEN
                    )
            }
        }
        if (transition != SENIOR && senior.required(id, before)) {
            findings +=
                Finding(
                    "SENIOR_APPROVAL_REQUIRED",
                    "Este compromiso exige la aprobación de nivel superior (approve_senior, RN-ENG-002)",
                    "origin_engagement"
                )
        }
        if (transition == DIRECT && ProjectParameters.policy(project(before), "policy_internal_approval")) {
            findings +=
                Finding(
                    "INTERNAL_APPROVAL_REQUIRED",
                    "El proyecto tiene la aprobación interna activa: el compromiso se envía a aprobación (D8, ASM-008)",
                    "project"
                )
        }
        return findings
    }

    private suspend fun stakeholders(id: UUID): Int = readAs("stakeholder") { records.related(Commitments.COMMITMENT, id, Commitments.STAKEHOLDERS) }.size

    // created_by es una columna de sistema: se compara en la base como quien llama, como EditAnyGuard
    private suspend fun createdBy(
        id: UUID,
        userId: UUID
    ): Boolean = records.count(Commitments.COMMITMENT, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(id)) > 0

    private suspend fun project(attributes: Map<String, Any?>): Map<String, Any?> {
        val id = Records.uuid(attributes["project"]) ?: return emptyMap()
        return readAs("project") { records.find("project", id) }?.attributes.orEmpty()
    }

    companion object {
        const val SUBMIT = "submit"
        const val SENIOR = "approve_senior"
        const val DIRECT = "approve_direct"
        const val APPROVE = "APPROVE"
        val DOUBLE_KEY = setOf("approve", SENIOR)
    }
}
```

- [ ] **Paso 5: `approve_direct` en la matriz de workflows**

La regla D8 ya deja aplicar `approve_direct` en un proyecto sin aprobación interna: la matriz deja de diferirla. Su proyecto
de prueba pasa a no tener aprobación interna (a `approve` no le importa) y el camino a `approved` sigue siendo `submit` y
`approve` (`WALK_AVOIDS`), para que la doble llave de D3 y D4 se siga ejerciendo. Esta es la primera de las tres tareas que
editan `WorkflowMatrixApiTest` (8, 13 y 14): se integran en ese orden.

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
    private val assigned = mutableSetOf<Pair<String, String>>()
```

por:

```kotlin
    private val assigned = mutableSetOf<Pair<String, String>>()

    // el proyecto de la prueba, sin aprobación interna: approve_direct solo pasa así (D8, plan 3.2) y approve no la mira
    private var relaxed: String? = null

    override fun testProject(): String = relaxed ?: project(mapOf("policy_internal_approval" to false)).also { relaxed = it }
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest
        // F3: lo que el plan 3.2 hace comando o pide de su comando (`accept` de WF-K, CommandTransitionGuard, y
        // `mark_duplicate`, que pide el `duplicate_of` que solo escribe POST .../duplicate), lo que pide la entrega con su
        // evidencia, que solo da su endpoint (`register_fulfillment`, D9) y lo que solo se alcanza por ella
        // (`reopen_execution`, `verify`, `close`), y `approve_direct`, que pide un proyecto sin aprobación interna (D8): los
        // prueba el plan 3.2 en CommitmentApprovalApiTest, CommitmentFulfillmentApiTest y CandidateApiTest
        val DEFERRED =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "commitment.approve_direct",
                "commitment.register_fulfillment",
                "commitment.reopen_execution",
                "commitment.verify",
                "commitment.close",
                "commitment_candidate.accept",
                "commitment_candidate.mark_duplicate"
            )
```

por:

```kotlin
        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest
        // F3: lo que el plan 3.2 hace comando o pide de su comando (`accept` de WF-K, CommandTransitionGuard, y
        // `mark_duplicate`, que pide el `duplicate_of` que solo escribe POST .../duplicate), y lo que pide la entrega con su
        // evidencia, que solo da su endpoint (`register_fulfillment`, D9) y lo que solo se alcanza por ella
        // (`reopen_execution`, `verify`, `close`): las tareas 13 y 14 del plan 3.2 las sacan de aquí. `approve_direct` corre en
        // el proyecto de la prueba, que no tiene aprobación interna (D8)
        val DEFERRED =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "commitment.register_fulfillment",
                "commitment.reopen_execution",
                "commitment.verify",
                "commitment.close",
                "commitment_candidate.accept",
                "commitment_candidate.mark_duplicate"
            )
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        // atajos de WF-C que 2.2 guarda con reglas propias: el registro llega a `held` por el ciclo normal (schedule y register_held)
        val WALK_AVOIDS = setOf("engagement.register_held_unplanned", "engagement.close_light")
```

por:

```kotlin
        // atajos de WF-C que 2.2 guarda con reglas propias: el registro llega a `held` por el ciclo normal (schedule y register_held);
        // y approve_direct de WF-D: un compromiso llega a `approved` con submit y approve, la doble llave de D3 y D4
        val WALK_AVOIDS = setOf("engagement.register_held_unplanned", "engagement.close_light", "commitment.approve_direct")
```

- [ ] **Paso 6: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `ApprovalDataTest` 2/2.

- [ ] **Paso 7: Commit**

```bash
git add src/main/kotlin/sgspe/commitment/SeniorApproval.kt src/main/kotlin/sgspe/commitment/ApprovalRules.kt \
  src/test/kotlin/sgspe/commitment/ApprovalDataTest.kt src/test/kotlin/sgspe/commitment/CommitmentApprovalApiTest.kt \
  src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -m "$(cat <<'EOF'
feat: reglas de aprobación de compromisos (D1 a D5 y D8)

Matriz mínima y responsable al enviar y al aprobar, doble llave con APPROVE, segregación de
funciones, nivel superior evaluado en el momento (RN-ENG-002) y aprobación directa solo sin
aprobación interna. D6 espera su dato (ASM-089). La matriz de workflows deja de diferir
approve_direct.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `CommitmentApprovalApiTest` (5), `CommitmentWorkflowApiTest` de 3.1 y
`WorkflowMatrixApiTest` (`approve_direct` por `COMMITMENT_MANAGER` y `SOCIAL_MANAGER`, 403 de `COMMUNITY_RELATIONS_OFFICER` y
409 desde `pending_approval`).

### Tarea 9: El tope de apoyo social de la campaña (`SupportPolicyGuard`, D7)

Al aprobar un compromiso con campaña y tope, la suma de los comprometidos de la campaña en su moneda, más el que se aprueba,
contra `social_support_cap` (RN-INV-003, ASM-063). Con `policy_cap_blocks_approval` (por defecto) superarlo es
`409 SUPPORT_CAP_EXCEEDED` salvo para quien tiene `APPROVE_OVER_CAP`; con la política apagada es una advertencia. Un monto en
otra moneda no se suma y se advierte (`CAP_OTHER_CURRENCY`). La aprobación toma el candado de la campaña dentro de la
transacción de la transición, así dos aprobaciones simultáneas no pasan el tope juntas (Review Focus 4); la consulta en seco
no lo toma. También el recalculador de JOB-02 para `executed_social_amount` y `other_currency_items_count` de la campaña.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt`, `CampaignExecution.kt`
- Probar: `src/test/kotlin/sgspe/investment/SupportCapTest.kt`, `SupportPolicyApiTest.kt`

**Interfaces:**
- Consume: `readAs`, `NotReadable` (Tarea 7) y `Commitments` (Tarea 2); `Transactions.locked`, `Criteria.inState`,
  `Records.all(max)`, `DerivedRecalculator`, `DerivedFieldsJob.reason` (F2).
- Produce: `SupportCap.evaluate(cap, campaignCurrency, candidate, approved): Outcome`; `CampaignExecution.of`; el candado
  `exploration_campaign:<id>` (lo comparte el recalculador); los códigos `SUPPORT_CAP_EXCEEDED` y `CAP_OTHER_CURRENCY`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/investment/SupportCapTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// el tope de apoyo social de una campaña (RN-INV-003, ASM-063) y su ejecutado, sin base
class SupportCapTest {
    private fun approved(
        amount: String,
        currency: String? = "PEN"
    ) = SupportCap.Approved(BigDecimal(amount), currency)

    @Test
    fun `suma los aprobados de la moneda de la campana mas el que se aprueba`() {
        val within = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("400"), listOf(approved("600")))
        assertFalse(within.exceeded)
        assertEquals(0, BigDecimal("1000").compareTo(within.total))
        val over = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("400.01"), listOf(approved("600")))
        assertTrue(over.exceeded)
    }

    @Test
    fun `otra moneda no se suma y se cuenta, y un candidato en otra moneda no se compara`() {
        val mixed = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("500"), listOf(approved("600"), approved("9000", "USD")))
        assertTrue(mixed.exceeded)
        assertEquals(1, mixed.otherCurrency)
        val foreign = SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("5000", "USD"), listOf(approved("999")))
        assertFalse(foreign.exceeded)
        assertTrue(foreign.incomparable)
        // sin moneda, PEN por defecto (RNF-I18N-002)
        assertFalse(SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("10", null), emptyList()).incomparable)
    }

    @Test
    fun `el ejecutado de la campana suma solo su moneda y cuenta los de otra`() {
        val result =
            CampaignExecution.of(
                "PEN",
                listOf(
                    CampaignExecution.Executed(BigDecimal("100"), "PEN"),
                    CampaignExecution.Executed(BigDecimal("50.5"), null),
                    CampaignExecution.Executed(BigDecimal("70"), "USD"),
                    CampaignExecution.Executed(BigDecimal.ZERO, "USD"),
                    CampaignExecution.Executed(null, "PEN")
                )
            )
        assertEquals(0, BigDecimal("150.5").compareTo(result["executed_social_amount"] as BigDecimal))
        assertEquals(1, result["other_currency_items_count"])
    }
}
```

Crear `src/test/kotlin/sgspe/investment/SupportPolicyApiTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

// SupportPolicyGuard, D7 de WF-D (RN-INV-003, ASM-063): el tope de la campaña bloquea salvo con APPROVE_OVER_CAP, o advierte
// con la política apagada
class SupportPolicyApiTest : F3DomainApiTest() {
    private fun campaign(
        project: String,
        cap: Int
    ): String =
        createRecord("exploration_campaign", mapOf("project" to project, "name" to "Campaña de prueba", "social_support_cap" to cap, "currency" to "PEN"))

    @Test
    fun `con el tope que bloquea, approve se rechaza y approve_senior con APPROVE_OVER_CAP pasa`() {
        val project = testProject()
        val campaign = campaign(project, 1500)
        approvedCommitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 1000), start = false)
        val over = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 600))
        move("commitment", over, "submit", reason = "envío")
        val refused = transition("commitment", over, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refused))
        assertEquals(HttpStatus.OK, transition("commitment", over, "approve_senior", member("PROJECT_MANAGER", project).token, "sobre el tope").status)
    }

    @Test
    fun `con la politica apagada el tope superado es una advertencia en la consulta en seco y la aprobacion pasa`() {
        val project = project(mapOf("policy_cap_blocks_approval" to false))
        val campaign = campaign(project, 100)
        val id = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 600))
        move("commitment", id, "submit", reason = "envío")
        val approver = member("APPROVER", project)
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "commitment", "recordId" to id, "transition" to "approve"),
                    HttpStatus.OK,
                    approver.token
                )
            )
        assertTrue(check["warnings"].values().any { it["code"].asString() == "SUPPORT_CAP_EXCEEDED" }, check.toString())
        assertEquals(HttpStatus.OK, transition("commitment", id, "approve", approver.token, "aprobación").status)
    }

    // la entrada del Review Focus 4: dos aprobaciones simultáneas de la misma campaña no pasan el tope juntas, porque el candado
    // de la campaña las pone en fila dentro de la transacción de la transición
    @Test
    fun `dos aprobaciones simultaneas de la misma campana no pasan el tope juntas`() {
        val project = testProject()
        val campaign = campaign(project, 1000)
        val ids = List(2) { commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 600)) }
        ids.forEach { move("commitment", it, "submit", reason = "envío") }
        val approver = member("APPROVER", project)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val statuses =
            try {
                val calls =
                    ids.map { id ->
                        pool.submit<HttpStatus> {
                            start.await()
                            transition("commitment", id, "approve", approver.token, "aprobación").status
                        }
                    }
                start.countDown()
                calls.map { it.get() }
            } finally {
                pool.shutdown()
            }
        assertEquals(listOf(HttpStatus.CONFLICT, HttpStatus.OK), statuses.sortedBy { it.value() }.reversed())
        assertEquals(1, ids.count { record("commitment", it)["state"].asString() == "approved" })
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.SupportCapTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'SupportCap'`.

- [ ] **Paso 3: La guarda**

Crear `src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt`:

```kotlin
package sgspe.investment

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.commitment.readAs
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.math.BigDecimal
import java.util.UUID

// el tope de apoyo social de una campaña, puro (RN-INV-003, ASM-063): la suma de los montos aprobados de la campaña en su
// moneda, más el que se aprueba, contra el tope. un monto en otra moneda no se suma y se advierte
object SupportCap {
    data class Approved(
        val amount: BigDecimal?,
        val currency: String?
    )

    data class Outcome(
        val exceeded: Boolean,
        val total: BigDecimal,
        val otherCurrency: Int,
        // el que se aprueba está en otra moneda: no se compara
        val incomparable: Boolean
    )

    fun evaluate(
        cap: BigDecimal,
        campaignCurrency: String,
        candidate: Approved,
        approved: List<Approved>
    ): Outcome {
        val same = approved.filter { (it.currency ?: DEFAULT_CURRENCY) == campaignCurrency }
        val other = approved.size - same.size
        val base = same.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
        if ((candidate.currency ?: DEFAULT_CURRENCY) != campaignCurrency) return Outcome(false, base, other, incomparable = true)
        val total = base + (candidate.amount ?: BigDecimal.ZERO)
        return Outcome(total > cap, total, other, incomparable = false)
    }

    const val DEFAULT_CURRENCY = "PEN"
}

// SupportPolicyGuard (sgspe.investment, D7 de WF-D, RF-INV-002): al aprobar un compromiso con campaña y tope, verifica el tope
// contra los compromisos comprometidos de la campaña (dashboards.md §3.2; desde F5, también los aportes). con la política
// policy_cap_blocks_approval activa (por defecto) superarlo es un 409 SUPPORT_CAP_EXCEEDED salvo para quien tiene la acción
// APPROVE_OVER_CAP; con la política apagada es una advertencia. una aprobación toma el candado de la campaña
// (`exploration_campaign:<id>`) dentro de la transacción de la transición, así dos aprobaciones de la misma campaña no pasan
// el tope a la vez; la consulta en seco no lo toma. lee como quien aprueba: sin READ sobre la campaña o los compromisos,
// OBJECT_NOT_READABLE
@Component
@Order(GuardOrder.DOMAIN)
class SupportPolicyGuard(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        if (change.transition !in Commitments.APPROVALS) return emptyList()
        val before = change.before.orEmpty()
        val campaign = Records.uuid(before["exploration_campaign"]) ?: return emptyList()
        return try {
            if (dryRun) evaluate(change, before, campaign) else transactions.locked("$CAMPAIGN:$campaign") { evaluate(change, before, campaign) }
        } catch (e: sgspe.commitment.NotReadable) {
            listOf(Finding("OBJECT_NOT_READABLE", "El tope de la campaña necesita leer ${e.objectName}", e.objectName, status = HttpStatus.FORBIDDEN))
        }
    }

    private suspend fun evaluate(
        change: RecordWrite,
        before: Map<String, Any?>,
        campaign: UUID
    ): List<Finding> {
        val record = readAs(CAMPAIGN) { records.find(CAMPAIGN, campaign) } ?: return emptyList()
        val cap = Values.decimal(record.attributes["social_support_cap"]) ?: return emptyList()
        val currency = Values.text(record.attributes["currency"]) ?: SupportCap.DEFAULT_CURRENCY
        val approved =
            readAs(Commitments.COMMITMENT) {
                records.all(
                    Commitments.COMMITMENT,
                    filters = mapOf("exploration_campaign" to campaign.toString()),
                    criteria = listOf(Criteria.inState(Commitments.COMMITTED)),
                    max = MAX_COMMITMENTS
                )
            }.filter { it.id != change.recordId?.toString() }
                .map { SupportCap.Approved(Values.decimal(it.attributes["amount"]), Values.text(it.attributes["currency"])) }
        val outcome = SupportCap.evaluate(cap, currency, SupportCap.Approved(Values.decimal(before["amount"]), Values.text(before["currency"])), approved)
        val findings = mutableListOf<Finding>()
        if (outcome.otherCurrency > 0 || outcome.incomparable) {
            findings +=
                Finding(
                    "CAP_OTHER_CURRENCY",
                    "Hay montos en una moneda distinta de la de la campaña ($currency): no se suman al tope (ASM-063)",
                    "currency",
                    blocking = false
                )
        }
        if (outcome.exceeded) {
            val project =
                Records
                    .uuid(before["project"])
                    ?.let { readAs("project") { records.find("project", it) } }
                    ?.attributes
                    .orEmpty()
            val blocks = ProjectParameters.policy(project, "policy_cap_blocks_approval")
            val user = callers.userOrNull()
            val overCap = user != null && permissions.has(user, OVER_CAP, Commitments.COMMITMENT)
            findings +=
                Finding(
                    "SUPPORT_CAP_EXCEEDED",
                    "La aprobación supera el tope de apoyo social de la campaña: $cap $currency, con ${outcome.total} aprobados (RN-INV-003)",
                    "exploration_campaign",
                    blocking = blocks && !overCap
                )
        }
        return findings
    }

    companion object {
        const val CAMPAIGN = "exploration_campaign"
        const val OVER_CAP = "APPROVE_OVER_CAP"

        // el tope de compromisos de una campaña que se leen al aprobar
        const val MAX_COMMITMENTS = 20_000
    }
}
```

- [ ] **Paso 4: El ejecutado de la campaña**

Crear `src/main/kotlin/sgspe/investment/CampaignExecution.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// el ejecutado de una campaña, puro (domain-model §7.4, ASM-063): executed_amount de sus compromisos en la moneda de la campaña;
// los que tienen ejecutado en otra moneda no se suman y se cuentan (other_currency_items_count). desde F5 se suman los aportes
// en delivered o closed
object CampaignExecution {
    data class Executed(
        val amount: BigDecimal?,
        val currency: String?
    )

    fun of(
        campaignCurrency: String,
        commitments: List<Executed>
    ): Map<String, Any?> {
        val executed = commitments.filter { (it.amount ?: BigDecimal.ZERO).signum() > 0 }
        val same = executed.filter { (it.currency ?: SupportCap.DEFAULT_CURRENCY) == campaignCurrency }
        return mapOf(
            "executed_social_amount" to same.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add),
            "other_currency_items_count" to (executed.size - same.size)
        )
    }
}

// el recalculador de JOB-02 para exploration_campaign.executed_social_amount y other_currency_items_count: desde cero en cada
// vuelta, solo escribe si el valor cambia (P11), con el motivo que nombra al trabajo (P16)
@Component
class CampaignExecutionRecalculator(
    private val records: Records,
    private val transactions: Transactions
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Commitments.COMMITMENT

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        var written = 0
        var failed = 0
        val campaigns = records.all(CAMPAIGN)
        for (campaign in campaigns) {
            try {
                if (refresh(UUID.fromString(campaign.id), campaign.attributes)) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("CampaignExecutionRecalculator: no se recalculó la campaña {}", campaign.id, e)
            }
        }
        check(failed == 0) { "no se recalcularon $failed de ${campaigns.size} campañas (se escribieron $written)" }
        return written
    }

    private suspend fun refresh(
        id: UUID,
        read: Map<String, Any?>
    ): Boolean {
        val currency = Values.text(read["currency"]) ?: SupportCap.DEFAULT_CURRENCY
        val commitments =
            records
                .all(Commitments.COMMITMENT, filters = mapOf(CAMPAIGN to id.toString()))
                .map { CampaignExecution.Executed(Values.decimal(it.attributes["executed_amount"]), Values.text(it.attributes["currency"])) }
        val wanted = CampaignExecution.of(currency, commitments)
        if (wanted.all { (field, value) -> Values.same(read[field], value) }) return false
        return transactions.locked("$CAMPAIGN:$id") {
            val current = records.find(CAMPAIGN, id)?.attributes ?: return@locked false
            val changes = wanted.filter { (field, value) -> !Values.same(current[field], value) }
            if (changes.isEmpty()) {
                false
            } else {
                records.patch(CAMPAIGN, id, changes, REASON)
                true
            }
        }
    }

    companion object {
        const val CAMPAIGN = "exploration_campaign"
        val REASON = DerivedFieldsJob.reason("ejecutado de la campaña")
    }
}
```

- [ ] **Paso 5: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `SupportCapTest` 3/3.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt src/main/kotlin/sgspe/investment/CampaignExecution.kt \
  src/test/kotlin/sgspe/investment/SupportCapTest.kt src/test/kotlin/sgspe/investment/SupportPolicyApiTest.kt
git commit -m "$(cat <<'EOF'
feat: tope de apoyo social de la campaña al aprobar compromisos (D7)

SupportPolicyGuard bloquea o advierte según la política del proyecto, deja pasar APPROVE_OVER_CAP
y serializa las aprobaciones de una campaña con su candado; JOB-02 recalcula el ejecutado de la
campaña en su moneda.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `SupportPolicyApiTest` (3).

### Tarea 10: `CommitmentDerivationsListener` y su recalculador

En la transacción de `register_fulfillment`, `close` y las `cancel_*`, la fecha en Lima de la transición
(`fulfilled_date`, `closed_date`, `cancelled_date`), como quien la aplica y con la marca; `reopen_execution` vacía
`fulfilled_date`; si no puede escribir, lanza y la transición se revierte. Al crear un compromiso o cambiar su origen o su
proyecto, `requires_senior_approval`, que solo informa; si falla, lo registra (JOB-02 lo corrige). Un vínculo de solicitantes
no llega a los listeners (decisión 10): lo recoge la vuelta diaria de JOB-02 con `SeniorApprovalRecalculator`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/commitment/CommitmentDerivations.kt`
- Probar: `src/test/kotlin/sgspe/commitment/TransitionDatesTest.kt`, `CommitmentDerivationsApiTest.kt`

**Interfaces:**
- Consume: `SeniorApprovalReader` (Tarea 8); `Records.patch`, `Clock`, `LIMA`, `DerivedRecalculator`, `Criteria` (F2).
- Produce: `TransitionDates.changes(transition, today)`; `CommitmentDerivationsListener.FIELD`, `reason(transition)`;
  `SeniorApprovalRecalculator.ALL_STATES`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/commitment/TransitionDatesTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

// las fechas de transición de un compromiso (domain-model §7.4, dashboards.md §12)
class TransitionDatesTest {
    private val today = LocalDate.parse("2026-10-09")

    @Test
    fun `cumplir, cerrar y cancelar fechan, reabrir vacia fulfilled_date, lo demas no escribe`() {
        assertEquals(mapOf("fulfilled_date" to "2026-10-09"), TransitionDates.changes("register_fulfillment", today))
        assertEquals(mapOf("fulfilled_date" to null), TransitionDates.changes("reopen_execution", today))
        assertEquals(mapOf("closed_date" to "2026-10-09"), TransitionDates.changes("close", today))
        for (cancel in listOf("cancel_draft", "cancel_pending_approval", "cancel_approved", "cancel_in_progress", "cancel_blocked")) {
            assertEquals(mapOf("cancelled_date" to "2026-10-09"), TransitionDates.changes(cancel, today), cancel)
        }
        assertEquals(emptyMap<String, Any?>(), TransitionDates.changes("start", today))
        assertEquals(emptyMap<String, Any?>(), TransitionDates.changes(null, today))
    }
}
```

Crear `src/test/kotlin/sgspe/commitment/CommitmentDerivationsApiTest.kt`:

```kotlin
package sgspe.commitment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import sgspe.common.DerivedFieldsJob

// CommitmentDerivationsListener y su recalculador (domain-model §7.4, §7.5, dashboards.md §12): las fechas de transición en la
// transacción de la transición y requires_senior_approval, que solo informa
class CommitmentDerivationsApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    @Test
    fun `cancelar fecha cancelled_date en la transicion, con el motivo del listener`() {
        val id = commitment(testProject())
        move("commitment", id, "cancel_draft", reason = "ya no aplica")
        assertTrue(record("commitment", id)["attributes"]["cancelled_date"].asString().isNotBlank())
        val history = tree(send("GET", "/api/objects/commitment/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"].asString() == CommitmentDerivationsListener.reason("cancel_draft") })
    }

    @Test
    fun `un origen en mesa de dialogo marca requires_senior_approval al crear`() {
        val project = testProject()
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        assertEquals(true, record("commitment", id)["attributes"]["requires_senior_approval"].asBoolean())
        assertEquals(false, record("commitment", commitment(project))["attributes"]["requires_senior_approval"].asBoolean(false))
    }

    @Test
    fun `JOB-02 corrige requires_senior_approval cuando cambia la politica del proyecto, y la segunda vuelta no escribe`() {
        val project = project(mapOf("policy_senior_approval_for_dialogue" to false))
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        assertEquals(false, record("commitment", id)["attributes"]["requires_senior_approval"].asBoolean(false))
        // cambiar la política del proyecto no toca el compromiso: lo recoge la vuelta diaria de JOB-02
        assertEquals(HttpStatus.OK, edit("project", project, mapOf("policy_senior_approval_for_dialogue" to true)).status)
        runBlocking { job.runOnce(full = true) }
        val after = record("commitment", id)
        assertEquals(true, after["attributes"]["requires_senior_approval"].asBoolean())
        runBlocking { job.runOnce(full = true) }
        assertEquals(after["updatedAt"].asString(), record("commitment", id)["updatedAt"].asString())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.commitment.TransitionDatesTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'TransitionDates'`.

- [ ] **Paso 3: El listener y el recalculador**

Crear `src/main/kotlin/sgspe/commitment/CommitmentDerivations.kt`:

```kotlin
package sgspe.commitment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.LIMA
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// las fechas de transición de un compromiso, puras (domain-model §7.4, dashboards.md §12): fulfilled_date, closed_date y
// cancelled_date son la fecha en Lima de su transición; reopen_execution vacía fulfilled_date
object TransitionDates {
    fun changes(
        transition: String?,
        today: LocalDate
    ): Map<String, Any?> =
        when {
            transition == "register_fulfillment" -> mapOf("fulfilled_date" to today.toString())
            transition == "reopen_execution" -> mapOf("fulfilled_date" to null)
            transition == "close" -> mapOf("closed_date" to today.toString())
            transition != null && transition.startsWith("cancel_") -> mapOf("cancelled_date" to today.toString())
            else -> emptyMap()
        }
}

// CommitmentDerivationsListener (domain-model §7.5): en la transacción de register_fulfillment, reopen_execution, close y las
// cancel_* escribe la fecha de la transición, como quien la aplica y con la marca; si no puede, lanza y la transición se
// revierte (workflows.md, «Listeners»). al crear un compromiso o cambiar su interacción de origen o su proyecto recalcula
// requires_senior_approval, que solo informa; si falla, lo registra y la escritura sigue: JOB-02 lo corrige. un vínculo de
// solicitantes (commitment_stakeholder) no llega a los listeners en wasichai 0.6.0 (RelatedRecordService solo audita y
// llama a las guardas): ese cambio lo recoge la vuelta diaria de JOB-02, y la guarda D5 nunca lee el derivado
@Component
@Order(120)
class CommitmentDerivationsListener(
    private val records: Records,
    private val senior: SeniorApprovalReader,
    private val clock: Clock
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Commitments.COMMITMENT) return
        when (change.kind) {
            RecordChangeKind.TRANSITIONED -> {
                val changes = TransitionDates.changes(change.transition, LocalDate.now(clock.withZone(LIMA)))
                if (changes.isNotEmpty()) records.patch(Commitments.COMMITMENT, change.recordId, changes, reason(change.transition))
            }
            RecordChangeKind.CREATED, RecordChangeKind.UPDATED -> if (originChanged(change)) refreshSenior(change)
            else -> Unit
        }
    }

    // un alta, o el cambio de la interacción de origen o del proyecto (cuya política decide)
    private fun originChanged(change: RecordChange): Boolean {
        if (change.kind == RecordChangeKind.CREATED) return true
        val after = change.after.orEmpty()
        val before = change.before.orEmpty()
        return !Values.same(before["origin_engagement"], after["origin_engagement"]) || !Values.same(before["project"], after["project"])
    }

    private suspend fun refreshSenior(change: RecordChange) {
        try {
            val current = records.find(Commitments.COMMITMENT, change.recordId) ?: return
            val wanted = senior.required(change.recordId, current.attributes)
            if (Values.bool(current.attributes[FIELD]) != wanted) records.patch(Commitments.COMMITMENT, change.recordId, mapOf(FIELD to wanted), SENIOR_REASON)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("CommitmentDerivationsListener: no se actualizó requires_senior_approval del compromiso {}", change.recordId, e)
        }
    }

    companion object {
        const val FIELD = "requires_senior_approval"
        const val SENIOR_REASON = "derivado: aprobación de nivel superior"

        fun reason(transition: String?): String = "derivado: fecha de la transición $transition"
    }
}

// el recalculador de JOB-02 para requires_senior_approval: los compromisos abiertos cambiados desde la vuelta anterior, o
// todos en la vuelta diaria. un vínculo de solicitantes, un cambio de política del proyecto o del grupo de influencia de un
// solicitante solo lo ve la vuelta diaria
@Component
class SeniorApprovalRecalculator(
    private val records: Records,
    private val senior: SeniorApprovalReader,
    private val transactions: Transactions
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Commitments.COMMITMENT

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val open = Criteria.inState(ALL_STATES - Commitments.CLOSED)
        val commitments =
            records.all(Commitments.COMMITMENT, criteria = listOfNotNull(open, since?.let { Criteria.updatedSince(it) }))
        var written = 0
        var failed = 0
        for (commitment in commitments) {
            try {
                val id = UUID.fromString(commitment.id)
                val wanted = senior.required(id, commitment.attributes)
                if (Values.bool(commitment.attributes[CommitmentDerivationsListener.FIELD]) == wanted) continue
                transactions.locked("${Commitments.COMMITMENT}:$id") {
                    records.patch(Commitments.COMMITMENT, id, mapOf(CommitmentDerivationsListener.FIELD to wanted), REASON)
                }
                written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("SeniorApprovalRecalculator: no se recalculó el compromiso {}", commitment.id, e)
            }
        }
        check(failed == 0) { "no se recalcularon $failed de ${commitments.size} compromisos (se escribieron $written)" }
        return written
    }

    companion object {
        val REASON = DerivedFieldsJob.reason("aprobación de nivel superior")

        // los estados de WF-D (workflows.md)
        val ALL_STATES = setOf("draft", "pending_approval") + Commitments.COMMITTED + "cancelled"
    }
}
```

- [ ] **Paso 4: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `TransitionDatesTest` 1/1.

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/commitment/CommitmentDerivations.kt src/test/kotlin/sgspe/commitment/TransitionDatesTest.kt \
  src/test/kotlin/sgspe/commitment/CommitmentDerivationsApiTest.kt
git commit -m "$(cat <<'EOF'
feat: fechas de transición y nivel superior de los compromisos

CommitmentDerivationsListener escribe fulfilled_date, closed_date y cancelled_date en la
transacción de su transición y vacía fulfilled_date al reabrir; requires_senior_approval al crear
o cambiar el origen, y JOB-02 lo corrige.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `CommitmentDerivationsApiTest` (3).

### Tarea 11: Reprogramaciones

`POST /api/sgspe/commitments/{id}/reschedules` (RF-COM-003, api.md §4.5): con `If-Match`, motivo e `Idempotency-Key`, bajo
el candado `commitment:<id>`, crea la `commitment_reschedule` (inmutable y `apiOnly`) con la fecha anterior y la nueva y
cambia `due_date` con la marca, la única vía desde `approved` (`DUE_DATE_RESCHEDULE_ONLY`, Tarea 7). Solo en ejecución
(`approved`, `in_progress`, `blocked`); la misma fecha es `400 SAME_DUE_DATE`. Responde `201 { reschedule, commitment }` con
`ETag` del compromiso y `Location` de la reprogramación.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/commitment/Reschedules.kt`
- Probar: `src/test/kotlin/sgspe/commitment/RescheduleApiTest.kt`

**Interfaces:**
- Consume: `Commitments.EXECUTING` (Tarea 2); `Idempotency.respond`, `CommandAnswer` (Tarea 3); `IfMatch`, `Versions`,
  `Transactions.locked`, `Permissions` (F2).
- Produce: `Reschedules.reschedule(id, body, reason, ifMatch): CommandAnswer`; `Reschedules.lock(id): String`
  (`commitment:<id>`, el candado que comparten la reprogramación, las entregas y JOB-01); `RescheduleResult(reschedule,
  commitment)`; los códigos `STATE_MISMATCH` (409, `state`), `SAME_DUE_DATE` y `FIELD_REQUIRED` (400, `attributes.<campo>`).

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/commitment/RescheduleApiTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// POST /api/sgspe/commitments/{id}/reschedules (RF-COM-003, api.md §4.5)
class RescheduleApiTest : F3DomainApiTest() {
    private fun body(date: String = "2027-02-01") =
        mapOf("attributes" to mapOf("new_due_date" to date, "reschedule_reason" to "Lluvias en la zona", "agreed_with_stakeholder" to true))

    @Test
    fun `reprograma en una transaccion, deja la fecha anterior y la nueva y devuelve el ETag del compromiso`() {
        val id = approvedCommitment(testProject())
        val response = exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, ifMatch("commitment", id) + withReason())
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val result = tree(response.body)
        assertEquals("2026-12-15", result["reschedule"]["attributes"]["previous_due_date"].asString())
        assertEquals("2027-02-01", result["commitment"]["attributes"]["due_date"].asString())
        assertEquals("\"${result["commitment"]["updatedAt"].asString()}\"", response.headers.getFirst(HttpHeaders.ETAG))
    }

    @Test
    fun `sin If-Match 428, con uno viejo 412, la misma fecha 400 y en draft 409`() {
        val project = testProject()
        val id = approvedCommitment(project)
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, withReason()).status)
        val stale = ifMatch("commitment", id)
        assertEquals(HttpStatus.CREATED, exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, stale + withReason()).status)
        assertEquals(
            HttpStatus.PRECONDITION_FAILED,
            exchange("POST", "/api/sgspe/commitments/$id/reschedules", body("2027-03-01"), token, stale + withReason()).status
        )
        val same = exchange("POST", "/api/sgspe/commitments/$id/reschedules", body(), token, ifMatch("commitment", id) + withReason())
        assertEquals("SAME_DUE_DATE", code(same))
        val draft = commitment(project)
        val inDraft = exchange("POST", "/api/sgspe/commitments/$draft/reschedules", body(), token, ifMatch("commitment", draft) + withReason())
        assertEquals(HttpStatus.CONFLICT, inDraft.status)
        assertEquals("STATE_MISMATCH", code(inDraft))
    }

    @Test
    fun `una reprogramacion no se crea por la API generica`() {
        val id = approvedCommitment(testProject())
        val direct =
            exchange(
                "POST",
                "/api/objects/commitment_reschedule/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "commitment" to id,
                            "previous_due_date" to "2026-12-15",
                            "new_due_date" to "2027-01-01",
                            "reschedule_reason" to "x",
                            "agreed_with_stakeholder" to true
                        )
                ),
                token
            )
        assertEquals(HttpStatus.FORBIDDEN, direct.status)
    }
}
```

- [ ] **Paso 2: Compilar y ver que la ruta no existe**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Expected: compila (la prueba solo usa HTTP). Sin el comando, en integración `POST …/reschedules` responde `404`.

- [ ] **Paso 3: El comando**

Crear `src/main/kotlin/sgspe/commitment/Reschedules.kt`:

```kotlin
package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

data class RescheduleResult(
    val reschedule: RecordResponse,
    val commitment: RecordResponse
)

// la reprogramación (RF-COM-003, api.md §4.5): en una transacción crea commitment_reschedule, inmutable, con la fecha anterior
// y la nueva, y cambia due_date, con la marca (la única vía para cambiarla desde approved, CommitmentRecordRules). exige
// If-Match, motivo, CREATE sobre commitment_reschedule y UPDATE sobre commitment. solo en ejecución (approved, in_progress,
// blocked): antes de aprobar, due_date se edita directamente; después de cumplir, ya no hay plazo
@Service
class Reschedules(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions
) {
    suspend fun reschedule(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireOnly(attributes, FIELDS, "attributes.")
        val newDate = date(attributes["new_due_date"])
        val why = Values.text(attributes["reschedule_reason"]) ?: throw required("reschedule_reason")
        val agreed = attributes["agreed_with_stakeholder"] as? Boolean ?: throw required("agreed_with_stakeholder")
        if (reason.isNullOrBlank()) throw ValidationException("Reprogramar exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Reprogramar un compromiso", Actions.CREATE to Commitments.RESCHEDULE, Actions.UPDATE to Commitments.COMMITMENT)
        val result =
            transactions.locked(lock(id)) {
                val commitment = records.get(Commitments.COMMITMENT, id)
                versions.check(Commitments.COMMITMENT, id, expected, commitment.updatedAt, "El compromiso")
                if (commitment.state !in Commitments.EXECUTING) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Solo se reprograma un compromiso en ejecución; este está en ${commitment.state}",
                        "state"
                    ).toCodedException()
                }
                val previous = Values.text(commitment.attributes["due_date"])
                if (previous == newDate.toString()) {
                    throw Finding(
                        "SAME_DUE_DATE",
                        "La fecha nueva es la que ya tiene el compromiso",
                        "attributes.new_due_date",
                        status = HttpStatus.BAD_REQUEST
                    ).toCodedException()
                }
                val reschedule =
                    records.create(
                        Commitments.RESCHEDULE,
                        mapOf(
                            "commitment" to id.toString(),
                            "previous_due_date" to previous,
                            "new_due_date" to newDate.toString(),
                            "reschedule_reason" to why,
                            "agreed_with_stakeholder" to agreed
                        ),
                        reason
                    )
                RescheduleResult(reschedule, records.patch(Commitments.COMMITMENT, id, mapOf("due_date" to newDate.toString()), reason))
            }
        return CommandAnswer(
            HttpStatus.CREATED,
            result,
            result.commitment.updatedAt,
            "/api/objects/${Commitments.RESCHEDULE}/records/${result.reschedule.id}"
        )
    }

    private fun date(value: Any?): LocalDate {
        val text = Values.text(value) ?: throw required("new_due_date")
        return try {
            LocalDate.parse(text)
        } catch (_: DateTimeParseException) {
            throw ValidationException("Fecha inválida", "attributes.new_due_date", "debe ser una fecha AAAA-MM-DD")
        }
    }

    private fun required(field: String) = Finding("FIELD_REQUIRED", "Falta $field", "attributes.$field", status = HttpStatus.BAD_REQUEST).toCodedException()

    companion object {
        val FIELDS = setOf("new_due_date", "reschedule_reason", "agreed_with_stakeholder")

        // el candado de un compromiso: lo toman la reprogramación, las entregas y JOB-01
        fun lock(id: UUID): String = "${Commitments.COMMITMENT}:$id"
    }
}

// api.md §4.5. honra Idempotency-Key (decisión D3)
@RestController
@RequestMapping("/api/sgspe/commitments/{id}/reschedules")
class RescheduleController(
    private val reschedules: Reschedules,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun reschedule(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            reschedules.reschedule(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
```

- [ ] **Paso 4: Correr el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/commitment/Reschedules.kt src/test/kotlin/sgspe/commitment/RescheduleApiTest.kt
git commit -m "$(cat <<'EOF'
feat: reprogramación de compromisos

Crea la commitment_reschedule inmutable y cambia due_date en una transacción, con If-Match, motivo,
Idempotency-Key y el candado del compromiso; solo en ejecución.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `RescheduleApiTest` (3).

### Tarea 12: Entregas de un compromiso

`POST /api/sgspe/commitments/{id}/deliveries` solo captura datos (api.md §4.5, RF-COM-008): bajo el candado del compromiso,
en ejecución, crea la `delivery` (`apiOnly`), la vincula con sus evidencias (`evidence_delivery`) y recalcula
`executed_amount` (la suma de `delivered_amount`, ASM-063), con la marca y solo si cambia (P11). No cambia el estado: el
cumplimiento es `register_fulfillment` (Tarea 13). `delivered_amount` pasa por `Amounts.parse` (Review Focus 5). No hay
«Acta de entrega» emitida ni AUT-03 en F3: `delivery` restringe `recipients` y `conformity_by`, y un documento emitido los
congelaría para todo lector (UPS-23, ASM-090); el acta firmada se sube como evidencia ACTA DE ENTREGA y se vincula a la
entrega, que es lo que D9 exige. `PATCH /api/sgspe/deliveries/{id}` corrige con `If-Match` y recalcula; con el
compromiso cumplido lo rechaza `CommitmentWriteGuard` (`409 DELIVERY_FROZEN`), también al vincular o desvincular evidencias.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/commitment/Deliveries.kt`
- Probar: `src/test/kotlin/sgspe/commitment/DeliveryApiTest.kt`

**Interfaces:**
- Consume: `Reschedules.lock` (Tarea 11); `Amounts.parse`, `StrictBody.requireAttributes`, `Commitments` (Tarea 2);
  `Idempotency.respond` (Tarea 3); en las pruebas, `F3DomainApiTest.evidence` (Tarea 2b) y `POST /api/sgspe/evidence`
  (Tarea 5).
- Produce: `Deliveries.create(commitmentId, body, reason): CommandAnswer`; `Deliveries.patch(id, body, reason, ifMatch):
  DeliveryResult`; `DeliveryResult(delivery, commitment)`.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/commitment/DeliveryApiTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import java.math.BigDecimal

// las entregas de un compromiso (RF-COM-008, api.md §4.5, ASM-063): solo capturan datos y recalculan executed_amount. el
// «Acta de entrega» (AUT-03) espera a UPS-23: hasta entonces el acta firmada se sube como evidencia ACTA DE ENTREGA
class DeliveryApiTest : F3DomainApiTest() {
    private fun deliver(
        commitment: String,
        attributes: Map<String, Any?>,
        evidence: List<String> = emptyList(),
        headers: Map<String, String> = withReason()
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/commitments/$commitment/deliveries",
            mapOf("attributes" to mapOf("delivery_date" to "2026-10-01", "conformity_given" to true) + attributes, "evidence" to evidence),
            token,
            headers
        )

    @Test
    fun `registrar una entrega no cambia el estado, vincula sus evidencias y suma el ejecutado`() {
        val project = testProject()
        val id = approvedCommitment(project)
        val acta = evidence(project, "ACTA DE ENTREGA")
        val first = deliver(id, mapOf("delivered_amount" to 400), listOf(acta))
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        assertEquals("in_progress", tree(first.body)["commitment"]["state"].asString())
        deliver(id, mapOf("delivered_amount" to "250.5"))
        assertEquals(0, BigDecimal("650.5").compareTo(record("commitment", id)["attributes"]["executed_amount"].decimalValue()))
        val delivery = tree(first.body)["delivery"]["id"].asString()
        assertEquals(
            acta,
            tree(send("GET", "/api/objects/delivery/records/$delivery/related/evidence_delivery", null, HttpStatus.OK))["content"][0]["id"].asString()
        )
    }

    @Test
    fun `un monto negativo o enorme, un atributo desconocido o el compromiso en draft no crean nada`() {
        val project = testProject()
        val id = approvedCommitment(project)
        assertEquals("OUT_OF_RANGE", code(deliver(id, mapOf("delivered_amount" to "1e999999"))))
        assertEquals("OUT_OF_RANGE", code(deliver(id, mapOf("delivered_amount" to -5))))
        assertEquals(HttpStatus.BAD_REQUEST, deliver(id, mapOf("commitment" to id)).status)
        assertEquals(HttpStatus.BAD_REQUEST, deliver(id, emptyMap(), headers = emptyMap()).status)
        val draft = commitment(project)
        assertEquals("STATE_MISMATCH", code(deliver(draft, emptyMap())))
    }

    @Test
    fun `una entrega se corrige con If-Match y recalcula el ejecutado, pero no la de un compromiso cumplido`() {
        val project = testProject()
        val id = approvedCommitment(project)
        val delivery = tree(deliver(id, mapOf("delivered_amount" to 100, "conformity_by" to "Junta directiva")).body)["delivery"]["id"].asString()
        val fixed =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("delivered_amount" to 120)),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.OK, fixed.status, fixed.body)
        assertEquals(0, BigDecimal("120").compareTo(tree(fixed.body)["commitment"]["attributes"]["executed_amount"].decimalValue()))
        val acta = evidence(project, "ACTA DE ENTREGA")
        val foto = evidence(project, "FOTO", "PUBLICA")
        for (e in listOf(acta, foto)) {
            assertEquals(
                HttpStatus.NO_CONTENT,
                exchange("POST", "/api/sgspe/evidence/$e/links", mapOf("object" to "delivery", "recordId" to delivery), token).status
            )
        }
        move("commitment", id, "register_fulfillment", reason = "entregado")
        val frozen =
            patch(
                "/api/sgspe/deliveries/$delivery",
                mapOf("attributes" to mapOf("delivered_amount" to 130)),
                token,
                ifMatch("delivery", delivery) + withReason()
            )
        assertEquals(HttpStatus.CONFLICT, frozen.status, frozen.body)
        assertEquals("DELIVERY_FROZEN", code(frozen))
        val unlink = exchange("DELETE", "/api/sgspe/evidence/$foto/links/delivery/$delivery", null, token)
        assertEquals("DELIVERY_FROZEN", code(unlink))
    }
}
```

- [ ] **Paso 2: Compilar**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Expected: compila. Sin el comando, en integración las rutas responden `404`.

- [ ] **Paso 3: El comando**

Crear `src/main/kotlin/sgspe/commitment/Deliveries.kt`:

```kotlin
package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Amounts
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.util.UUID

data class DeliveryResult(
    val delivery: RecordResponse,
    val commitment: RecordResponse
)

// la entrega de un compromiso (RF-COM-008, api.md §4.5): solo captura datos. en una transacción crea la delivery (apiOnly), la
// vincula con sus evidencias (evidence_delivery: el acta y las fotos, ya subidas con POST /api/sgspe/evidence) y recalcula
// executed_amount, la suma de delivered_amount de las entregas del compromiso (ASM-063), con la marca y solo si cambia. no
// cambia el estado: el cumplimiento es la transición humana register_fulfillment, con D9. el «Acta de entrega» emitida (AUT-03)
// espera a UPS-23. la corrección (PATCH) la rechaza CommitmentWriteGuard con DELIVERY_FROZEN si el compromiso ya está cumplido
@Service
class Deliveries(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(
        commitmentId: UUID,
        body: Map<String, Any?>,
        reason: String?
    ): CommandAnswer {
        StrictBody.requireOnly(body, setOf("attributes", "geometries", "evidence"))
        val attributes = StrictBody.objectOf(body, "attributes")
        val geometries = StrictBody.objectOf(body, "geometries")
        StrictBody.requireAttributes(catalog, Commitments.DELIVERY, attributes, FIXED)
        Amounts.parse(attributes[AMOUNT], "attributes.$AMOUNT")
        val evidence = evidence(body["evidence"])
        requireReason(reason)
        val user = callers.require()
        val grants =
            listOf(Actions.CREATE to Commitments.DELIVERY, Actions.UPDATE to Commitments.COMMITMENT) +
                if (evidence.isEmpty()) emptyList() else listOf(Actions.UPDATE to EVIDENCE, Actions.UPDATE to Commitments.DELIVERY)
        permissions.require(user, "Registrar una entrega", *grants.toTypedArray())
        val result =
            transactions.locked(Reschedules.lock(commitmentId)) {
                val commitment = records.get(Commitments.COMMITMENT, commitmentId)
                if (commitment.state !in Commitments.EXECUTING) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Las entregas se registran con el compromiso en ejecución; este está en ${commitment.state}",
                        "state"
                    ).toCodedException()
                }
                val delivery = records.create(Commitments.DELIVERY, attributes + ("commitment" to commitmentId.toString()), reason, geometries)
                val deliveryId = UUID.fromString(delivery.id)
                evidence.forEach { records.link(EVIDENCE, it, Commitments.EVIDENCE_DELIVERY, deliveryId, reason) }
                DeliveryResult(records.get(Commitments.DELIVERY, deliveryId), refreshExecuted(commitmentId, reason))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.delivery.updatedAt, "/api/objects/${Commitments.DELIVERY}/records/${result.delivery.id}")
    }

    // corrige los datos de una entrega y recalcula el ejecutado de su compromiso; solo escribe lo que cambia
    suspend fun patch(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): DeliveryResult {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes", "geometries"))
        val changes = StrictBody.objectOf(body, "attributes")
        val geometries = StrictBody.objectOf(body, "geometries")
        StrictBody.requireAttributes(catalog, Commitments.DELIVERY, changes, FIXED)
        Amounts.parse(changes[AMOUNT], "attributes.$AMOUNT")
        requireReason(reason)
        val user = callers.require()
        permissions.require(user, "Corregir una entrega", Actions.UPDATE to Commitments.DELIVERY, Actions.UPDATE to Commitments.COMMITMENT)
        val read = records.get(Commitments.DELIVERY, id)
        val commitmentId =
            Records.uuid(read.attributes["commitment"])
                ?: throw Finding("STATE_MISMATCH", "La entrega no es de un compromiso", "commitment").toCodedException()
        return transactions.locked(Reschedules.lock(commitmentId)) {
            val delivery = records.get(Commitments.DELIVERY, id)
            versions.check(Commitments.DELIVERY, id, expected, delivery.updatedAt, "La entrega")
            val diff = changes.filter { (field, value) -> !Values.same(delivery.attributes[field], value) }
            val written = if (diff.isEmpty() && geometries.isEmpty()) delivery else records.patch(Commitments.DELIVERY, id, diff, reason, geometries)
            DeliveryResult(written, refreshExecuted(commitmentId, reason))
        }
    }

    // executed_amount = suma de delivered_amount de las entregas del compromiso, escrito solo si cambia (P11)
    private suspend fun refreshExecuted(
        commitmentId: UUID,
        reason: String?
    ): RecordResponse {
        val total =
            records
                .all(Commitments.DELIVERY, filters = mapOf("commitment" to commitmentId.toString()))
                .mapNotNull { Values.decimal(it.attributes[AMOUNT]) }
                .fold(BigDecimal.ZERO, BigDecimal::add)
        val commitment = records.get(Commitments.COMMITMENT, commitmentId)
        return if (Values.same(commitment.attributes[EXECUTED], total)) {
            commitment
        } else {
            records.patch(Commitments.COMMITMENT, commitmentId, mapOf(EXECUTED to total), reason)
        }
    }

    private fun evidence(raw: Any?): List<UUID> {
        if (raw == null) return emptyList()
        val list = raw as? List<*> ?: throw ValidationException("Evidencias inválidas", "evidence", "debe ser una lista de ids de evidence")
        if (list.size > MAX_EVIDENCE) throw ValidationException("Demasiadas evidencias", "evidence", "a lo sumo $MAX_EVIDENCE por entrega")
        return list
            .mapIndexed { index, item ->
                Records.uuid(item) ?: throw ValidationException("Evidencia inválida", "evidence[$index]", "debe ser el id de una evidence")
            }.distinct()
    }

    // escribir executed_amount en el compromiso (requiresReason) exige motivo: se pide antes de escribir nada
    private fun requireReason(reason: String?) {
        if (reason.isNullOrBlank()) throw ValidationException("Registrar una entrega exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
    }

    companion object {
        const val EVIDENCE = "evidence"
        const val AMOUNT = "delivered_amount"
        const val EXECUTED = "executed_amount"

        // la relación con su compromiso la fija el comando; la del aporte social llega en F5
        val FIXED = setOf("commitment", "social_contribution")
        const val MAX_EVIDENCE = 50
    }
}

// api.md §4.5. el alta honra Idempotency-Key (decisión D3)
@RestController
class DeliveryController(
    private val deliveries: Deliveries,
    private val idempotency: Idempotency
) {
    @PostMapping("/api/sgspe/commitments/{id}/deliveries")
    suspend fun create(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { deliveries.create(id, body, ChangeReason.fromHeader(reason)) }
    }

    @PatchMapping("/api/sgspe/deliveries/{id}")
    suspend fun patch(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<DeliveryResult> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val result = deliveries.patch(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity.ok().eTag("\"${result.delivery.updatedAt}\"").body(result)
    }
}
```

- [ ] **Paso 4: Correr el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/commitment/Deliveries.kt src/test/kotlin/sgspe/commitment/DeliveryApiTest.kt
git commit -m "$(cat <<'EOF'
feat: entregas de compromisos y su corrección

La entrega solo captura datos: crea la delivery y sus vínculos de evidencia y recalcula
executed_amount en una transacción, con montos acotados; la corrección exige If-Match y queda
congelada cuando el compromiso ya está cumplido.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `DeliveryApiTest` (3), con la Tarea 5 ya integrada (sus evidencias).

### Tarea 13: Cumplimiento y verificación (D9, D10 y D11)

`register_fulfillment` exige una entrega con conformidad (`conformity_given` y `conformity_by`) vinculada a la evidencia de
su tipo de recurso: ACTA DE ENTREGA y FOTO si es MATERIAL; ACTA, CARTA u OTRO si es NO MATERIAL (RN-COM-001, ASM-009); y
ninguna dependencia bloqueante abierta (RF-COM-004). `verify` exige `VERIFY` y que quien verifica no sea el responsable
(RN-COM-005). Las entregas, sus evidencias y las dependencias se leen como quien aplica la transición; sin lectura de
`conformity_by` (restringido) es `403 FIELD_NOT_READABLE`.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/commitment/FulfillmentRules.kt`
- Probar: `src/test/kotlin/sgspe/commitment/FulfillmentTest.kt`, `CommitmentFulfillmentApiTest.kt`
- Modificar: `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`

**Interfaces:**
- Consume: `CommitmentRule` y `readAs` (Tarea 7); `Commitments` (Tarea 2); `Records.related` (F2); para la matriz, el comando
  de entregas (Tarea 12), `F3DomainApiTest.evidence` (Tarea 2b) y el `WorkflowMatrixApiTest` de la Tarea 8.
- Produce: `Fulfillment.missing`, `conforms`, `finding`, `RESOLVED`; los códigos `DELIVERY_REQUIRED`, `EVIDENCE_REQUIRED`,
  `BLOCKING_DEPENDENCY` (409), `FIELD_NOT_READABLE`, `ACTION_REQUIRED`, `SEGREGATION_OF_DUTIES` (403).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/commitment/FulfillmentTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

// D9 de WF-D: la entrega con conformidad y la evidencia según el tipo de recurso (RN-COM-001, ASM-009), sin base
class FulfillmentTest {
    private fun delivery(
        types: Set<String>,
        given: Boolean = true,
        by: String? = "Junta directiva"
    ) = Fulfillment.DeliveryEvidence(given, by, types)

    @Test
    fun `MATERIAL exige acta de entrega y foto en una misma entrega con conformidad`() {
        assertNull(Fulfillment.finding("MATERIAL", listOf(delivery(setOf("ACTA DE ENTREGA", "FOTO")))))
        assertEquals("EVIDENCE_REQUIRED", Fulfillment.finding("MATERIAL", listOf(delivery(setOf("ACTA DE ENTREGA"))))?.code)
        assertEquals(listOf("FOTO"), Fulfillment.missing("MATERIAL", delivery(setOf("ACTA DE ENTREGA"))))
        // la foto en otra entrega no completa la primera
        assertEquals("EVIDENCE_REQUIRED", Fulfillment.finding("MATERIAL", listOf(delivery(setOf("ACTA DE ENTREGA")), delivery(setOf("FOTO"), by = null)))?.code)
    }

    @Test
    fun `NO MATERIAL exige conformidad con quienes la dieron y un acta, una carta u otro`() {
        assertNull(Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("CARTA")))))
        assertEquals("EVIDENCE_REQUIRED", Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("FOTO"))))?.code)
        assertEquals("DELIVERY_REQUIRED", Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("CARTA"), by = null)))?.code)
        assertEquals("DELIVERY_REQUIRED", Fulfillment.finding("NO MATERIAL", listOf(delivery(setOf("CARTA"), given = false)))?.code)
        assertEquals("DELIVERY_REQUIRED", Fulfillment.finding("MATERIAL", emptyList())?.code)
    }
}
```

Crear `src/test/kotlin/sgspe/commitment/CommitmentFulfillmentApiTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// D9, D10 y D11 de WF-D (RN-COM-001, ASM-009, RF-COM-004, RN-COM-005)
class CommitmentFulfillmentApiTest : F3DomainApiTest() {
    private fun delivery(
        commitment: String,
        conformityBy: String? = "Junta directiva",
        evidence: List<String> = emptyList()
    ): String {
        val response =
            exchange(
                "POST",
                "/api/sgspe/commitments/$commitment/deliveries",
                mapOf(
                    "attributes" to
                        mapOf("delivery_date" to "2026-10-01", "conformity_given" to true) + listOfNotNull(conformityBy?.let { "conformity_by" to it }),
                    "evidence" to evidence
                ),
                token,
                withReason()
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return tree(response.body)["delivery"]["id"].asString()
    }

    @Test
    fun `MATERIAL sin foto se rechaza, con acta y foto pasa y fecha fulfilled_date, y reabrir la vacia`() {
        val project = testProject()
        val id = approvedCommitment(project)
        delivery(id, evidence = listOf(evidence(project, "ACTA DE ENTREGA")))
        val refused = transition("commitment", id, "register_fulfillment", reason = "entregado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("EVIDENCE_REQUIRED", code(refused))
        delivery(id, evidence = listOf(evidence(project, "ACTA DE ENTREGA"), evidence(project, "FOTO", "PUBLICA")))
        move("commitment", id, "register_fulfillment", reason = "entregado")
        assertTrue(record("commitment", id)["attributes"]["fulfilled_date"].asString().isNotBlank())
        move("commitment", id, "reopen_execution", reason = "falta un tramo")
        assertTrue(record("commitment", id)["attributes"]["fulfilled_date"].isNull)
    }

    @Test
    fun `NO MATERIAL exige quienes dieron la conformidad y una carta, un acta u otro`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        delivery(id, conformityBy = null, evidence = listOf(evidence(project, "CARTA")))
        assertEquals("DELIVERY_REQUIRED", code(transition("commitment", id, "register_fulfillment", reason = "entregado")))
        delivery(id, evidence = listOf(evidence(project, "CARTA")))
        assertEquals(HttpStatus.OK, transition("commitment", id, "register_fulfillment", reason = "entregado").status)
    }

    @Test
    fun `una dependencia bloqueante abierta impide registrar el cumplimiento`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("resource_type" to "NO MATERIAL"))
        createRecord("commitment_dependency", mapOf("commitment" to id, "depends_on" to commitment(project), "blocking" to true))
        delivery(id, evidence = listOf(evidence(project, "OTRO")))
        val refused = transition("commitment", id, "register_fulfillment", reason = "entregado")
        assertEquals("BLOCKING_DEPENDENCY", code(refused))
    }

    @Test
    fun `el responsable no verifica su compromiso`() {
        val project = testProject()
        val owner = member("COMMITMENT_MANAGER", project)
        val id = approvedCommitment(project, owner, mapOf("resource_type" to "NO MATERIAL"))
        delivery(id, evidence = listOf(evidence(project, "ACTA")))
        move("commitment", id, "register_fulfillment", reason = "entregado")
        val own = transition("commitment", id, "verify", owner.token, "verificado")
        assertEquals(HttpStatus.FORBIDDEN, own.status, own.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(own))
        assertEquals(HttpStatus.OK, transition("commitment", id, "verify", member("COMMITMENT_MANAGER", project).token, "verificado").status)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.commitment.FulfillmentTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'Fulfillment'`.

- [ ] **Paso 3: Las reglas**

Crear `src/main/kotlin/sgspe/commitment/FulfillmentRules.kt`:

```kotlin
package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// D9 de WF-D, pura (RN-COM-001, ASM-009): la evidencia mínima de una entrega según el tipo de recurso
object Fulfillment {
    const val MATERIAL = "MATERIAL"
    val MATERIAL_TYPES = listOf("ACTA DE ENTREGA", "FOTO")
    val NON_MATERIAL_TYPES = setOf("ACTA", "CARTA", "OTRO")

    // una entrega como la ve la regla: su conformidad y los tipos de las evidencias vinculadas
    data class DeliveryEvidence(
        val conformityGiven: Boolean,
        val conformityBy: String?,
        val evidenceTypes: Set<String>
    )

    // los tipos de evidencia que le faltan a una entrega con conformidad; vacío si la entrega basta
    fun missing(
        resourceType: String?,
        delivery: DeliveryEvidence
    ): List<String> =
        if (resourceType == MATERIAL) {
            MATERIAL_TYPES.filter { it !in delivery.evidenceTypes }
        } else if (delivery.evidenceTypes.none { it in NON_MATERIAL_TYPES }) {
            listOf(NON_MATERIAL_TYPES.sorted().joinToString(" o "))
        } else {
            emptyList()
        }

    fun conforms(delivery: DeliveryEvidence): Boolean = delivery.conformityGiven && Values.text(delivery.conformityBy) != null

    // el hallazgo de D9 sobre todas las entregas del compromiso: pasa si alguna basta
    fun finding(
        resourceType: String?,
        deliveries: List<DeliveryEvidence>
    ): Finding? {
        val conforming = deliveries.filter { conforms(it) }
        if (conforming.isEmpty()) {
            return Finding(
                "DELIVERY_REQUIRED",
                "Registrar el cumplimiento exige una entrega con conformidad del grupo de interés y quiénes la dieron (D9, RN-COM-001)",
                Commitments.DELIVERY
            )
        }
        if (conforming.any { missing(resourceType, it).isEmpty() }) return null
        val missing = conforming.minBy { missing(resourceType, it).size }.let { missing(resourceType, it) }
        return Finding(
            "EVIDENCE_REQUIRED",
            "A la entrega con conformidad le falta evidencia vinculada: ${missing.joinToString(", ")} (D9, ASM-009)",
            Commitments.EVIDENCE_DELIVERY
        )
    }

    // D10: una dependencia bloqueante sigue abierta mientras el compromiso del que depende no se cumple ni se cancela
    val RESOLVED = Commitments.FULFILLED + "cancelled"
}

// CommitmentWriteGuard, cumplimiento y verificación (workflows.md WF-D): D9 (register_fulfillment exige una entrega con
// conformidad y la evidencia de su tipo de recurso), D10 (ninguna dependencia bloqueante abierta, RF-COM-004) y D11 (verify
// exige la acción VERIFY y que quien verifica no sea el responsable, RN-COM-005). lee las entregas, sus evidencias y las
// dependencias como quien aplica la transición: sin lectura, OBJECT_NOT_READABLE; sin lectura de conformity_by (un campo
// restringido), FIELD_NOT_READABLE
@Component
class FulfillmentRules(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions
) : CommitmentRule {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val id = change.recordId ?: return emptyList()
        val before = change.before.orEmpty()
        return when (change.transition) {
            "register_fulfillment" -> listOfNotNull(evidence(id, before), dependencies(id))
            "verify" -> verify(before)
            else -> emptyList()
        }
    }

    private suspend fun evidence(
        id: UUID,
        before: Map<String, Any?>
    ): Finding? {
        val deliveries = readAs(Commitments.DELIVERY) { records.all(Commitments.DELIVERY, filters = mapOf("commitment" to id.toString())) }
        if (deliveries.isNotEmpty() && deliveries.any { "conformity_by" !in it.attributes }) {
            return Finding(
                "FIELD_NOT_READABLE",
                "Registrar el cumplimiento exige leer quiénes dieron la conformidad de la entrega",
                "delivery.conformity_by",
                status = HttpStatus.FORBIDDEN
            )
        }
        val rows =
            deliveries.map { delivery ->
                val types =
                    readAs("evidence") { records.related(Commitments.DELIVERY, UUID.fromString(delivery.id), Commitments.EVIDENCE_DELIVERY) }
                        .mapNotNull { Values.text(it.attributes["evidence_type"]) }
                        .toSet()
                Fulfillment.DeliveryEvidence(
                    Values.bool(delivery.attributes["conformity_given"]) == true,
                    Values.text(delivery.attributes["conformity_by"]),
                    types
                )
            }
        return Fulfillment.finding(Values.text(before["resource_type"]), rows)
    }

    private suspend fun dependencies(id: UUID): Finding? {
        val blocking =
            readAs(Commitments.DEPENDENCY) {
                records.all(Commitments.DEPENDENCY, filters = mapOf("commitment" to id.toString()))
            }.filter { Values.bool(it.attributes["blocking"]) == true }
                .mapNotNull { Records.uuid(it.attributes["depends_on"]) }
        if (blocking.isEmpty()) return null
        val open =
            readAs(Commitments.COMMITMENT) { records.all(Commitments.COMMITMENT, ids = blocking.distinct()) }
                .count { it.state !in Fulfillment.RESOLVED }
        if (open == 0) return null
        return Finding(
            "BLOCKING_DEPENDENCY",
            "El compromiso depende de $open compromiso(s) todavía abiertos con una dependencia bloqueante (D10, RF-COM-004)",
            Commitments.DEPENDENCY
        )
    }

    private suspend fun verify(before: Map<String, Any?>): List<Finding> {
        val user = callers.userOrNull()
        val findings = mutableListOf<Finding>()
        if (user == null || !permissions.has(user, VERIFY, Commitments.COMMITMENT)) {
            findings += Finding("ACTION_REQUIRED", "Verificar un compromiso exige la acción VERIFY sobre commitment (D11)", status = HttpStatus.FORBIDDEN)
        }
        if (user == null || Values.text(before["owner_user_id"]) == user.userId.toString()) {
            findings +=
                Finding(
                    "SEGREGATION_OF_DUTIES",
                    "Quien verifica un compromiso no puede ser su responsable (D11, RN-COM-005)",
                    "owner_user_id",
                    status = HttpStatus.FORBIDDEN
                )
        }
        return findings
    }

    companion object {
        const val VERIFY = "VERIFY"
    }
}
```

- [ ] **Paso 4: `register_fulfillment`, `reopen_execution`, `verify` y `close` en la matriz de workflows**

Con D9 a D11 y el comando de entregas (Tarea 12), la matriz deja de diferir las cuatro transiciones de cumplimiento. Pasa a
extender `F3DomainApiTest` (por `evidence`), y `prepare` registra, con el admin, la entrega con conformidad, su acta de entrega
y su foto que pide `register_fulfillment` de un compromiso MATERIAL, tanto al recorrer el camino hasta `fulfilled`, `verified`
o `closed` como antes de aplicarla. `verify` la aplica `COMMITMENT_MANAGER` o `SOCIAL_MANAGER`, que tienen `VERIFY` y no son el
responsable del compromiso de la matriz (D11). Va después de la Tarea 8 en el mismo archivo.

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
import sgspe.F3ApiTest
```

por:

```kotlin
import sgspe.F3DomainApiTest
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
class WorkflowMatrixApiTest : F3ApiTest() {
```

por:

```kotlin
class WorkflowMatrixApiTest : F3DomainApiTest() {
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
    // un registro nuevo llevado hasta `state` por el camino de pathTo, cada paso por el primer rol de su lista que maneja el objeto
    private fun recordAt(
        workflow: Workflow,
        state: String,
        creatorRole: String,
        engagementType: String = FORMAL_TYPE
    ): String {
        val id = create(workflow, creatorRole, engagementType)
        for (step in checkNotNull(pathTo(workflow, state)) { "${workflow.objectName}: el estado $state no se alcanza" }) {
            val role = workflow.drivers.first { it in step.roles }
            move(workflow.objectName, id, step.name, actor(workflow, role, id).token, TEXT)
        }
        return id
    }
```

por:

```kotlin
    // un registro nuevo llevado hasta `state` por el camino de pathTo, cada paso por el primer rol de su lista que maneja el objeto
    // y con lo que pide (prepare); si se da `next`, también listo para aplicarla
    private fun recordAt(
        workflow: Workflow,
        state: String,
        creatorRole: String,
        engagementType: String = FORMAL_TYPE,
        next: Transition? = null
    ): String {
        val id = create(workflow, creatorRole, engagementType)
        for (step in checkNotNull(pathTo(workflow, state)) { "${workflow.objectName}: el estado $state no se alcanza" }) {
            val role = workflow.drivers.first { it in step.roles }
            prepare(workflow, id, step)
            move(workflow.objectName, id, step.name, actor(workflow, role, id).token, TEXT)
        }
        if (next != null) prepare(workflow, id, next)
        return id
    }

    // lo que una transición pide antes de aplicarse y la API genérica no da: register_fulfillment de WF-D exige una entrega con
    // conformidad y, en un compromiso MATERIAL, su acta de entrega y su foto (D9), que el admin registra con el comando de
    // entregas del plan 3.2
    private fun prepare(
        workflow: Workflow,
        id: String,
        rule: Transition
    ) {
        if (workflow.key(rule) != "commitment.register_fulfillment") return
        val evidence = listOf(evidence(testProject(), "ACTA DE ENTREGA"), evidence(testProject(), "FOTO", "PUBLICA"))
        val attributes = mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva")
        val delivery =
            exchange("POST", "/api/sgspe/commitments/$id/deliveries", mapOf("attributes" to attributes, "evidence" to evidence), token, withReason(TEXT))
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
    }
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                    val id = recordAt(workflow, rule.from, creatorFor(workflow, role), type)
```

por:

```kotlin
                    val id = recordAt(workflow, rule.from, creatorFor(workflow, role), type, rule)
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                val id = recordAt(workflow, rule.from, workflow.drivers.first(), type)
```

por:

```kotlin
                val id = recordAt(workflow, rule.from, workflow.drivers.first(), type, rule)
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest
        // F3: lo que el plan 3.2 hace comando o pide de su comando (`accept` de WF-K, CommandTransitionGuard, y
        // `mark_duplicate`, que pide el `duplicate_of` que solo escribe POST .../duplicate), y lo que pide la entrega con su
        // evidencia, que solo da su endpoint (`register_fulfillment`, D9) y lo que solo se alcanza por ella
        // (`reopen_execution`, `verify`, `close`): las tareas 13 y 14 del plan 3.2 las sacan de aquí. `approve_direct` corre en
        // el proyecto de la prueba, que no tiene aprobación interna (D8)
        val DEFERRED =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "commitment.register_fulfillment",
                "commitment.reopen_execution",
                "commitment.verify",
                "commitment.close",
                "commitment_candidate.accept",
                "commitment_candidate.mark_duplicate"
            )
```

por:

```kotlin
        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest
        // F3: lo que el plan 3.2 hace comando o pide de su comando (`accept` de WF-K, CommandTransitionGuard, y
        // `mark_duplicate`, que pide el `duplicate_of` que solo escribe POST .../duplicate): la Tarea 14 del plan 3.2 las saca
        // de aquí. `approve_direct` corre en un proyecto sin aprobación interna y prepare registra la entrega que pide
        // `register_fulfillment` (D9)
        val DEFERRED =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "commitment_candidate.accept",
                "commitment_candidate.mark_duplicate"
            )
```

- [ ] **Paso 5: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `FulfillmentTest` 2/2.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/commitment/FulfillmentRules.kt src/test/kotlin/sgspe/commitment/FulfillmentTest.kt \
  src/test/kotlin/sgspe/commitment/CommitmentFulfillmentApiTest.kt \
  src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -m "$(cat <<'EOF'
feat: reglas de cumplimiento y verificación de compromisos (D9 a D11)

register_fulfillment exige una entrega con conformidad y la evidencia de su tipo de recurso y
ninguna dependencia bloqueante abierta; verify exige VERIFY y que no verifique el responsable. La
matriz de workflows prueba por rol register_fulfillment, reopen_execution, verify y close.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `CommitmentFulfillmentApiTest` (4), `DeliveryApiTest` (4) y, como punto de control
de WF-D completo, `CommitmentWorkflowApiTest`, `WorkflowMatrixApiTest` y `PermissionMatrixApiTest`.

### Tarea 14: Candidatos a compromiso registrados por personas (WF-K)

`POST /api/sgspe/commitment-candidates` crea un candidato humano (`ai_generated` en falso; ni la confianza ni las decisiones
llegan del cuerpo); `…/{id}/accept` crea el compromiso en `draft` con los valores que la persona revisó, guarda
`created_commitment` y aplica `accept` (transición de comando) en una transacción; `…/{id}/duplicate` escribe `duplicate_of`
(un compromiso del mismo proyecto) y aplica `mark_duplicate`. Solo una persona decide (RQ-AI-003): una cuenta de servicio
recibe `403 PERSON_REQUIRED`. `mark_duplicate` por la API genérica llega sin `duplicate_of` y se rechaza (`CandidateRules`).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/commitment/CandidateRules.kt`, `Candidates.kt`
- Probar: `src/test/kotlin/sgspe/commitment/CandidateApiTest.kt`
- Modificar: `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`

**Interfaces:**
- Consume: `CommitmentRule` (Tarea 7); `Idempotency.respond` (Tarea 3); `TransitionRoles.require`, `Records.transition` (F2);
  `CommandTransitionGuard.COMMANDS` con `commitment_candidate.accept` (Tarea 2b); el `WorkflowMatrixApiTest` de la Tarea 13.
- Produce: `Candidates.create/accept/duplicate`; `CandidateAcceptance(commitment, candidate)`;
  `CandidateDuplicate(candidate)`; los códigos `PERSON_REQUIRED` (403), `DUPLICATE_NOT_FOUND`, `DUPLICATE_OTHER_PROJECT`
  (400), `FIELD_REQUIRED` (`duplicate_of`, 409).

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/commitment/CandidateApiTest.kt`:

```kotlin
package sgspe.commitment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// los candidatos a compromiso registrados por personas (RF-COM-010, WF-K, api.md §4.5): los casos que
// CommitmentCandidateWorkflowApiTest (3.1) deja a 3.2
class CandidateApiTest : F3DomainApiTest() {
    private fun candidate(project: String): String {
        val response =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates",
                mapOf("attributes" to mapOf("project" to project, "proposed_text" to "Arreglar el puente")),
                token
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        assertEquals(false, tree(response.body)["attributes"]["ai_generated"].asBoolean())
        return tree(response.body)["id"].asString()
    }

    @Test
    fun `el alta humana no fija el origen de IA ni la confianza`() {
        val refused =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates",
                mapOf("attributes" to mapOf("project" to testProject(), "proposed_text" to "x", "ai_generated" to true)),
                token
            )
        assertEquals(HttpStatus.BAD_REQUEST, refused.status)
    }

    @Test
    fun `aceptar crea el compromiso en draft y lo vincula, y por la API generica se rechaza`() {
        val project = testProject()
        val id = candidate(project)
        val generic = transition("commitment_candidate", id, "accept")
        assertEquals(HttpStatus.FORBIDDEN, generic.status)
        assertEquals("COMMAND_ONLY", code(generic))
        val manager = member("COMMITMENT_MANAGER", project)
        val body = mapOf("attributes" to mapOf("code" to uniqueName("C-"), "title" to "Puente", "amount" to 500, "currency" to "PEN"))
        val accepted =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$id/accept",
                body,
                manager.token,
                ifMatch("commitment_candidate", id, manager.token) + withReason()
            )
        assertEquals(HttpStatus.CREATED, accepted.status, accepted.body)
        val result = tree(accepted.body)
        assertEquals("draft", result["commitment"]["state"].asString())
        assertEquals("accepted", result["candidate"]["state"].asString())
        assertEquals(result["commitment"]["id"].asString(), result["candidate"]["attributes"]["created_commitment"].asString())
    }

    @Test
    fun `marcar duplicado escribe duplicate_of con el comando, y sin el por la API generica se rechaza`() {
        val project = testProject()
        val id = candidate(project)
        val generic = transition("commitment_candidate", id, "mark_duplicate", reason = "repetido")
        assertEquals(HttpStatus.CONFLICT, generic.status)
        assertEquals("FIELD_REQUIRED", code(generic))
        val existing = commitment(project)
        val other = commitment(project())
        val elsewhere =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$id/duplicate",
                mapOf("duplicateOf" to other),
                token,
                ifMatch("commitment_candidate", id) + withReason()
            )
        assertEquals("DUPLICATE_OTHER_PROJECT", code(elsewhere))
        val marked =
            exchange(
                "POST",
                "/api/sgspe/commitment-candidates/$id/duplicate",
                mapOf("duplicateOf" to existing),
                token,
                ifMatch("commitment_candidate", id) + withReason()
            )
        assertEquals(HttpStatus.OK, marked.status, marked.body)
        assertEquals("duplicate", tree(marked.body)["candidate"]["state"].asString())
    }
}
```

- [ ] **Paso 2: Compilar**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Expected: compila. Sin los comandos, en integración las rutas responden `404`.

- [ ] **Paso 3: La regla del duplicado y los comandos**

Crear `src/main/kotlin/sgspe/commitment/CandidateRules.kt`:

```kotlin
package sgspe.commitment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Values
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// CommitmentWriteGuard, candidatos (WF-K): marcar un candidato como duplicado exige duplicate_of, que solo escribe
// POST /api/sgspe/commitment-candidates/{id}/duplicate (commitment_candidate es apiOnly). por la API genérica la transición
// llega sin él y se rechaza
@Component
class CandidateRules : CommitmentRule {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.CANDIDATE || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        if (change.transition != MARK_DUPLICATE || Values.text(change.before?.get(DUPLICATE_OF)) != null) return emptyList()
        return listOf(
            Finding(
                "FIELD_REQUIRED",
                "Marcar un candidato como duplicado exige el compromiso que ya lo cubre: use POST /api/sgspe/commitment-candidates/{id}/duplicate",
                DUPLICATE_OF
            )
        )
    }

    companion object {
        const val MARK_DUPLICATE = "mark_duplicate"
        const val DUPLICATE_OF = "duplicate_of"
    }
}
```

Crear `src/main/kotlin/sgspe/commitment/Candidates.kt`:

```kotlin
package sgspe.commitment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

data class CandidateAcceptance(
    val commitment: RecordResponse,
    val candidate: RecordResponse
)

data class CandidateDuplicate(
    val candidate: RecordResponse
)

// los candidatos a compromiso registrados por personas (RF-COM-010, WF-K, api.md §4.5). commitment_candidate es apiOnly: el
// alta, la aceptación y la marca de duplicado son comandos; rechazar es una transición genérica con motivo. aceptar es una
// transición de comando (CommandTransitionGuard) que crea el compromiso en draft con los valores que la persona revisó y lo
// guarda en created_commitment, en una transacción. solo una persona decide (RQ-AI-001, RQ-AI-003): una cuenta de servicio
// recibe 403 PERSON_REQUIRED aunque tenga el rol. los candidatos de la IA llegan en F7
@Service
class Candidates(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(body: Map<String, Any?>): CommandAnswer {
        StrictBody.requireOnly(body, setOf("attributes"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, Commitments.CANDIDATE, attributes, SYSTEM)
        val user = callers.require()
        permissions.require(user, "Registrar un candidato a compromiso", Actions.CREATE to Commitments.CANDIDATE)
        val candidate = transactions.run { records.create(Commitments.CANDIDATE, attributes + (AI_GENERATED to false), null) }
        return CommandAnswer(HttpStatus.CREATED, candidate, candidate.updatedAt, "/api/objects/${Commitments.CANDIDATE}/records/${candidate.id}")
    }

    suspend fun accept(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, Commitments.COMMITMENT, attributes, setOf("project"))
        if (reason.isNullOrBlank()) throw ValidationException("Aceptar un candidato exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = person(callers.require())
        permissions.require(user, "Aceptar un candidato", Actions.UPDATE to Commitments.CANDIDATE, Actions.CREATE to Commitments.COMMITMENT)
        TransitionRoles.require(catalog, user, Commitments.CANDIDATE, listOf(ACCEPT))
        val result =
            transactions.locked(lock(id)) {
                val candidate = proposed(id, expected)
                val origin = attributes["origin_engagement"] ?: candidate.attributes["engagement"]
                val commitment =
                    records.create(
                        Commitments.COMMITMENT,
                        attributes + ("project" to candidate.attributes["project"]) + ("origin_engagement" to origin),
                        reason
                    )
                records.patch(Commitments.CANDIDATE, id, mapOf(CREATED_COMMITMENT to commitment.id), reason)
                records.transition(Commitments.CANDIDATE, id, ACCEPT, reason)
                CandidateAcceptance(records.get(Commitments.COMMITMENT, UUID.fromString(commitment.id)), records.get(Commitments.CANDIDATE, id))
            }
        return CommandAnswer(
            HttpStatus.CREATED,
            result,
            result.commitment.updatedAt,
            "/api/objects/${Commitments.COMMITMENT}/records/${result.commitment.id}"
        )
    }

    suspend fun duplicate(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CandidateDuplicate {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("duplicateOf"))
        val duplicateOf =
            Records.uuid(body["duplicateOf"]) ?: throw ValidationException("Falta el compromiso", "duplicateOf", "debe ser el id de un commitment")
        if (reason.isNullOrBlank()) throw ValidationException("Marcar un duplicado exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = person(callers.require())
        permissions.require(user, "Marcar un candidato como duplicado", Actions.UPDATE to Commitments.CANDIDATE)
        TransitionRoles.require(catalog, user, Commitments.CANDIDATE, listOf(CandidateRules.MARK_DUPLICATE))
        return transactions.locked(lock(id)) {
            val candidate = proposed(id, expected)
            val existing =
                records.find(Commitments.COMMITMENT, duplicateOf)
                    ?: throw Finding("DUPLICATE_NOT_FOUND", "El compromiso no existe o no puede leerlo", "duplicateOf", status = HttpStatus.BAD_REQUEST)
                        .toCodedException()
            if (Values.text(existing.attributes["project"]) != Values.text(candidate.attributes["project"])) {
                throw Finding(
                    "DUPLICATE_OTHER_PROJECT",
                    "El compromiso que lo cubre debe ser del mismo proyecto que el candidato",
                    "duplicateOf",
                    status = HttpStatus.BAD_REQUEST
                ).toCodedException()
            }
            records.patch(Commitments.CANDIDATE, id, mapOf(CandidateRules.DUPLICATE_OF to duplicateOf.toString()), reason)
            CandidateDuplicate(records.transition(Commitments.CANDIDATE, id, CandidateRules.MARK_DUPLICATE, reason))
        }
    }

    private suspend fun proposed(
        id: UUID,
        expected: java.time.Instant
    ): RecordResponse {
        val candidate = records.get(Commitments.CANDIDATE, id)
        versions.check(Commitments.CANDIDATE, id, expected, candidate.updatedAt, "El candidato")
        if (candidate.state != PROPOSED) {
            throw Finding("STATE_MISMATCH", "El candidato ya se decidió: está en ${candidate.state}", "state").toCodedException()
        }
        return candidate
    }

    // solo una persona decide sobre un candidato (RQ-AI-003): una cuenta de servicio nunca, tenga el rol que tenga
    private fun person(user: AuthenticatedUser): AuthenticatedUser {
        if (user.serviceAccount != null) {
            throw Finding(
                "PERSON_REQUIRED",
                "Solo una persona acepta o descarta un candidato a compromiso; una cuenta de servicio no",
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
        }
        return user
    }

    companion object {
        const val ACCEPT = "accept"
        const val PROPOSED = "proposed"
        const val AI_GENERATED = "ai_generated"
        const val CREATED_COMMITMENT = "created_commitment"

        // lo que fija el sistema y nunca una persona en el alta humana: el origen de IA, la confianza y las decisiones
        val SYSTEM = setOf(AI_GENERATED, "confidence", "ai_interaction", CREATED_COMMITMENT, CandidateRules.DUPLICATE_OF)

        fun lock(id: UUID): String = "${Commitments.CANDIDATE}:$id"
    }
}

// api.md §4.5. el alta y la aceptación honran Idempotency-Key (decisión D3)
@RestController
@RequestMapping("/api/sgspe/commitment-candidates")
class CandidateController(
    private val candidates: Candidates,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun create(
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { candidates.create(body) }
    }

    @PostMapping("/{id}/accept")
    suspend fun accept(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            candidates.accept(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }

    @PostMapping("/{id}/duplicate")
    suspend fun duplicate(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<CandidateDuplicate> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val result = candidates.duplicate(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity.ok().eTag("\"${result.candidate.updatedAt}\"").body(result)
    }
}
```

- [ ] **Paso 4: `accept` y `mark_duplicate` en la matriz de workflows**

La matriz deja de diferir las dos transiciones de WF-K. `apply` aplica `accept` por su comando, como el portal (la API
genérica responde `403 COMMAND_ONLY`), en el camino, en el caso permitido, en el de un rol sin la transición y en el del
estado equivocado (`409 STATE_MISMATCH`); y los candidatos de la matriz nacen con `duplicate_of`, el compromiso que los cubre,
porque `mark_duplicate` por la API genérica lo exige (`CandidateRules`). Con esto `DEFERRED` vuelve a tener solo las cuatro de
F2. Va después de la Tarea 13 en el mismo archivo.

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
    override fun testProject(): String = relaxed ?: project(mapOf("policy_internal_approval" to false)).also { relaxed = it }
```

por:

```kotlin
    override fun testProject(): String = relaxed ?: project(mapOf("policy_internal_approval" to false)).also { relaxed = it }

    // el compromiso que cubre a los candidatos de la prueba (duplicate_of)
    private val covering: String by lazy { commitment(testProject()) }
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
            // apiOnly: la plataforma, como hará el alta humana del plan 3.2
            "commitment_candidate" ->
                platformCreate(
                    "commitment_candidate",
                    mapOf("project" to testProject(), "proposed_text" to "Arreglar el canal de riego", "ai_generated" to false)
                )
```

por:

```kotlin
            // apiOnly: la plataforma, como el alta humana del plan 3.2, con el compromiso que lo cubre: mark_duplicate exige
            // duplicate_of (CandidateRules), que en el portal escribe POST .../duplicate
            "commitment_candidate" ->
                platformCreate(
                    "commitment_candidate",
                    mapOf("project" to testProject(), "proposed_text" to "Arreglar el canal de riego", "ai_generated" to false, "duplicate_of" to covering)
                )
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
            prepare(workflow, id, step)
            move(workflow.objectName, id, step.name, actor(workflow, role, id).token, TEXT)
```

por:

```kotlin
            prepare(workflow, id, step)
            val response = apply(workflow, id, step, actor(workflow, role, id))
            assertTrue(response.status.is2xxSuccessful, "${workflow.key(step)}: ${response.status} ${response.body}")
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
    }
```

por:

```kotlin
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
    }

    // una transición como la aplica el portal: accept de WF-K por su comando, que crea el compromiso en draft con los valores
    // que revisa la persona (CommandTransitionGuard rechaza la API genérica); las demás, por la API genérica
    private fun apply(
        workflow: Workflow,
        id: String,
        rule: Transition,
        who: Account
    ): Response {
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
        return exchange(
            "POST",
            "/api/sgspe/commitment-candidates/$id/accept",
            mapOf("attributes" to reviewed),
            who.token,
            ifMatch(workflow.objectName, id, who.token) + withReason(TEXT)
        )
    }
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                    val id = recordAt(workflow, rule.from, creatorFor(workflow, role), type, rule)
                    move(objectName, id, rule.name, actor(workflow, role, id).token, TEXT)
```

por:

```kotlin
                    val id = recordAt(workflow, rule.from, creatorFor(workflow, role), type, rule)
                    val response = apply(workflow, id, rule, actor(workflow, role, id))
                    assertTrue(response.status.is2xxSuccessful, "$role: ${response.status} ${response.body}")
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                val who = actor(workflow, outsider!!, id)
                val response = transition(objectName, id, rule.name, who.token, TEXT)
```

por:

```kotlin
                val who = actor(workflow, outsider!!, id)
                val response = apply(workflow, id, rule, who)
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                val who = actor(workflow, role, id)
                val response = transition(objectName, id, rule.name, who.token, TEXT)
```

por:

```kotlin
                val who = actor(workflow, role, id)
                val response = apply(workflow, id, rule, who)
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest
        // F3: lo que el plan 3.2 hace comando o pide de su comando (`accept` de WF-K, CommandTransitionGuard, y
        // `mark_duplicate`, que pide el `duplicate_of` que solo escribe POST .../duplicate): la Tarea 14 del plan 3.2 las saca
        // de aquí. `approve_direct` corre en un proyecto sin aprobación interna y prepare registra la entrega que pide
        // `register_fulfillment` (D9)
        val DEFERRED =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "commitment_candidate.accept",
                "commitment_candidate.mark_duplicate"
            )
```

por:

```kotlin
        // lo que el plan 2.2 convierte en comando y la API genérica rechaza (CommandTransitionGuard: `assess` de WF-B, `approve`
        // y `supersede` de WF-O) y lo que solo se alcanza por ellos: `retire_assessed` sale de `assessed`. los casos de estas
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest.
        // las de F3 ya no se difieren (plan 3.2): approve_direct corre en un proyecto sin aprobación interna, prepare registra la
        // entrega que pide register_fulfillment y apply aplica accept por su comando
        val DEFERRED =
            setOf(
                "stakeholder.assess",
                "stakeholder.retire_assessed",
                "area_of_influence.approve",
                "area_of_influence.supersede"
            )
```

- [ ] **Paso 5: Correr el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/commitment/CandidateRules.kt src/main/kotlin/sgspe/commitment/Candidates.kt \
  src/test/kotlin/sgspe/commitment/CandidateApiTest.kt \
  src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -m "$(cat <<'EOF'
feat: candidatos a compromiso registrados por personas

Alta humana, aceptación que crea el compromiso en draft y lo vincula, y marca de duplicado con el
compromiso que lo cubre; solo una persona decide (RQ-AI-003). La matriz de workflows prueba por
rol accept, por su comando, y mark_duplicate.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `CandidateApiTest` (3), `CommitmentCandidateWorkflowApiTest` de 3.1 y
`WorkflowMatrixApiTest` (ya sin transiciones de F3 en `DEFERRED`). La cuenta de
servicio con rol la cubre la prueba de 3.1 si arma una; si no, `PERSON_REQUIRED` queda probado solo por lectura del código
(anotarlo en el registro de la tarea).

### Tarea 15: `AgreementWriteGuard` (WF-I)

`activate`, `activate_from_draft` y `resume` exigen `signed_date` y `effective_date`; un PERMISO DE USO DE TERRENO o una
SERVIDUMBRE, la comunidad titular y al menos un predio vinculado (RF-AGR-002); una AUTORIZACION DE ASAMBLEA, la interacción
de tipo ASAMBLEA que la otorgó (RF-AGR-003). Lee los predios y la interacción como quien aplica la transición: sin lectura,
`403 OBJECT_NOT_READABLE`. Además, una cláusula que nombra un compromiso no se borra: `commitment.agreement_clause` es
opcional, `SOCIAL_MANAGER` y `COMMUNITY_RELATIONS_OFFICER` borran cláusulas, y `ON DELETE SET NULL` vaciaría el vínculo de un
compromiso (`requiresReason`) sin motivo ni fila de auditoría. `DependentsDeleteGuard` (F2) solo miraba las relaciones
obligatorias; desde aquí mira también las opcionales de un objeto `requiresReason` o `appendOnly`
(`ModelCatalog.guardedDependents`) y responde `409 HAS_DEPENDENTS` sobre `commitment.agreement_clause`. Con el modelo de F3
eso alcanza a `commitment.agreement_clause` y a `stakeholder_assessment.position` (una evaluación es `appendOnly`): borrar un
cargo que nombra una evaluación también responde `409`; los demás vínculos opcionales de `commitment` y `agreement` apuntan a
objetos que ningún rol borra.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/agreement/AgreementWriteGuard.kt`
- Modificar: `src/main/kotlin/sgspe/common/ModelCatalog.kt`, `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`,
  `src/test/kotlin/sgspe/common/DependentsDeleteGuardTest.kt`
- Probar: `src/test/kotlin/sgspe/agreement/AgreementActivationTest.kt`, `AgreementGuardApiTest.kt`,
  `DependentsDeleteGuardTest`

**Interfaces:**
- Consume: `RuleGuard`, `Records.related`, `Records.find`, `DependentsDeleteGuard` (F2); `F3DomainApiTest` (Tarea 2b).
- Produce: `AgreementActivation.findings(attributes, landParcels, assemblyType)`; los códigos `FIELD_REQUIRED`,
  `LAND_PARCEL_REQUIRED`, `ASSEMBLY_REQUIRED` (409); `ModelCatalog.guardedDependents(target)`, y `HAS_DEPENDENTS` (409)
  también por una relación opcional de un objeto `requiresReason` o `appendOnly`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/agreement/AgreementActivationTest.kt`:

```kotlin
package sgspe.agreement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// lo que exige llegar a active en WF-I (RF-AGR-001 a RF-AGR-003), sin base
class AgreementActivationTest {
    private val dated = mapOf("signed_date" to "2026-09-01", "effective_date" to "2026-09-15")

    private fun codes(
        attributes: Map<String, Any?>,
        parcels: Int = 0,
        assembly: String? = null
    ) = AgreementActivation.findings(attributes, parcels, assembly).map { it.code to it.field }

    @Test
    fun `un acta de acuerdos solo exige sus fechas`() {
        assertEquals(emptyList<Pair<String, String?>>(), codes(dated + ("agreement_type" to "ACTA DE ACUERDOS")))
        assertEquals(listOf("FIELD_REQUIRED" to "effective_date"), codes(mapOf("signed_date" to "2026-09-01", "agreement_type" to "CONVENIO")))
    }

    @Test
    fun `una servidumbre exige la comunidad titular y sus predios`() {
        val easement = dated + ("agreement_type" to "SERVIDUMBRE")
        assertEquals(listOf("FIELD_REQUIRED" to "community", "LAND_PARCEL_REQUIRED" to "agreement_land_parcel"), codes(easement))
        assertEquals(emptyList<Pair<String, String?>>(), codes(easement + ("community" to "c"), parcels = 2))
    }

    @Test
    fun `una autorizacion de asamblea apunta a una ASAMBLEA`() {
        val authorization = dated + ("agreement_type" to "AUTORIZACION DE ASAMBLEA")
        assertEquals(listOf("ASSEMBLY_REQUIRED" to "assembly_engagement"), codes(authorization, assembly = "REUNION"))
        assertEquals(listOf("ASSEMBLY_REQUIRED" to "assembly_engagement"), codes(authorization))
        assertEquals(emptyList<Pair<String, String?>>(), codes(authorization, assembly = "ASAMBLEA"))
    }
}
```

Crear `src/test/kotlin/sgspe/agreement/AgreementGuardApiTest.kt`:

```kotlin
package sgspe.agreement

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// AgreementWriteGuard (WF-I, RF-AGR-002, RF-AGR-003): los casos que AgreementWorkflowApiTest (3.1) deja a 3.2; y la cláusula
// que nombra un compromiso, que DependentsDeleteGuard no deja borrar
class AgreementGuardApiTest : F3DomainApiTest() {
    private fun agreement(
        type: String,
        extra: Map<String, Any?> = emptyMap()
    ): String =
        createRecord(
            "agreement",
            mapOf(
                "project" to testProject(),
                "code" to uniqueName("A-"),
                "title" to "Acuerdo de prueba",
                "agreement_type" to type,
                "signed_date" to "2026-09-01",
                "effective_date" to "2026-09-15"
            ) + extra,
            withReason("alta de prueba")
        )

    @Test
    fun `una servidumbre sin predios no se activa, con predios y comunidad si`() {
        val community = createRecord("community", mapOf("name" to "Comunidad Ejemplo"))
        val id = agreement("SERVIDUMBRE", mapOf("community" to community))
        val refused = transition("agreement", id, "activate_from_draft", reason = "firmado")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("LAND_PARCEL_REQUIRED", code(refused))
        val parcel = createRecord("land_parcel", mapOf("code" to uniqueName("P-")))
        val linked = exchange("POST", "/api/objects/agreement/records/$id/related/agreement_land_parcel", mapOf("otherId" to parcel), token, withReason())
        assertEquals(true, linked.status.is2xxSuccessful, linked.body)
        assertEquals(HttpStatus.OK, transition("agreement", id, "activate_from_draft", reason = "firmado").status)
    }

    @Test
    fun `una autorizacion de asamblea exige la interaccion de tipo ASAMBLEA`() {
        val project = testProject()
        val meeting = agreement("AUTORIZACION DE ASAMBLEA", mapOf("assembly_engagement" to engagement(project, "REUNION")))
        assertEquals("ASSEMBLY_REQUIRED", code(transition("agreement", meeting, "activate_from_draft", reason = "firmado")))
        val assembly = agreement("AUTORIZACION DE ASAMBLEA", mapOf("assembly_engagement" to engagement(project, "ASAMBLEA")))
        assertEquals(HttpStatus.OK, transition("agreement", assembly, "activate_from_draft", reason = "firmado").status)
    }

    @Test
    fun `una clausula que nombra un compromiso no se borra y una libre si`() {
        val project = testProject()
        val id = agreement("CONVENIO")
        val social = member("SOCIAL_MANAGER", project)
        val cited = createRecord("agreement_clause", mapOf("agreement" to id, "clause_number" to "1", "clause_text" to "Mejorar el canal"))
        val free = createRecord("agreement_clause", mapOf("agreement" to id, "clause_number" to "2", "clause_text" to "Capacitar a la junta"))
        commitment(project, mapOf("agreement" to id, "agreement_clause" to cited))
        // ON DELETE SET NULL vaciaría commitment.agreement_clause sin motivo ni auditoría (commitment es requiresReason)
        val refused = exchange("DELETE", "/api/objects/agreement_clause/records/$cited", null, social.token)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("HAS_DEPENDENTS", code(refused))
        assertEquals("commitment.agreement_clause", problemFields(refused.body).single())
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/agreement_clause/records/$free", null, social.token).status)
    }
}
```

En `src/test/kotlin/sgspe/common/DependentsDeleteGuardTest.kt`, agregar antes de `` `el modelo real vigila a los padres de los
hijos obligatorios` ``:

```kotlin
    @Test
    fun `una relacion opcional de un objeto requiresReason o appendOnly tambien cuenta`() {
        val reasoned =
            ModelCatalog.parse(
                model =
                    """
                    {"objects": [{"name": "commitment", "fields": [], "requiresReason": true},
                                 {"name": "stakeholder_assessment", "fields": [], "appendOnly": true},
                                 {"name": "task", "fields": []}],
                     "relationships": [
                      {"name": "a", "source": "commitment", "target": "agreement_clause", "fieldName": "agreement_clause"},
                      {"name": "b", "source": "stakeholder_assessment", "target": "position", "fieldName": "position"},
                      {"name": "c", "source": "task", "target": "agreement", "fieldName": "agreement"}
                    ]}
                    """.trimIndent(),
                roles = """{"roles": []}"""
            )
        assertEquals(listOf("commitment" to "agreement_clause"), reasoned.guardedDependents("agreement_clause"))
        assertEquals(listOf("stakeholder_assessment" to "position"), reasoned.guardedDependents("position"))
        // la tarea no lleva motivo: borrar su acuerdo la deja sin origen, como cualquier relación opcional
        assertTrue(reasoned.guardedDependents("agreement").isEmpty())
        assertTrue(reasoned.requiredDependents("agreement_clause").isEmpty())
    }
```

y al final de esa prueba, después de `assertTrue(real.requiredDependents("study").none { it.first == "locality_profile" })`:

```kotlin
        assertTrue(("commitment" to "agreement_clause") in real.guardedDependents("agreement_clause"))
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.agreement.AgreementActivationTest' --tests 'sgspe.common.DependentsDeleteGuardTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'AgreementActivation'` y `'guardedDependents'`.

- [ ] **Paso 3: La guarda**

Crear `src/main/kotlin/sgspe/agreement/AgreementWriteGuard.kt`:

```kotlin
package sgspe.agreement

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// lo que exige llegar a active, puro (WF-I, RF-AGR-001 a RF-AGR-003)
object AgreementActivation {
    val LAND_TYPES = setOf("PERMISO DE USO DE TERRENO", "SERVIDUMBRE")
    const val ASSEMBLY_AUTHORIZATION = "AUTORIZACION DE ASAMBLEA"
    const val ASSEMBLY = "ASAMBLEA"
    val REQUIRED_DATES = listOf("signed_date", "effective_date")

    // los hallazgos con lo ya leído: los predios vinculados y el tipo de la interacción de asamblea
    fun findings(
        attributes: Map<String, Any?>,
        landParcels: Int,
        assemblyType: String?
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        REQUIRED_DATES.firstOrNull { Values.text(attributes[it]) == null }?.let {
            findings += Finding("FIELD_REQUIRED", "Falta $it: un acuerdo vigente tiene fecha de firma y de inicio", it)
        }
        val type = Values.text(attributes["agreement_type"])
        if (type in LAND_TYPES) {
            if (Values.text(attributes["community"]) == null) {
                findings += Finding("FIELD_REQUIRED", "Un permiso de uso de terreno o una servidumbre nombra a la comunidad titular (RF-AGR-002)", "community")
            }
            if (landParcels == 0) {
                findings += Finding("LAND_PARCEL_REQUIRED", "Vincule los predios afectados antes de registrar la vigencia (RF-AGR-002)", LAND_PARCELS)
            }
        }
        if (type == ASSEMBLY_AUTHORIZATION && assemblyType != ASSEMBLY) {
            findings +=
                Finding(
                    "ASSEMBLY_REQUIRED",
                    "Una autorización de asamblea apunta a la interacción de tipo ASAMBLEA que la otorgó (RF-AGR-003)",
                    "assembly_engagement"
                )
        }
        return findings
    }

    const val LAND_PARCELS = "agreement_land_parcel"
}

// AgreementWriteGuard (sgspe.agreement, WF-I): activate, activate_from_draft y resume, las transiciones que llegan a active,
// exigen las fechas, los predios y la comunidad de un permiso de uso de terreno o una servidumbre, y la asamblea de una
// autorización de asamblea. lee los predios vinculados y la interacción como quien aplica la transición: sin lectura,
// OBJECT_NOT_READABLE (P12)
@Component
@Order(GuardOrder.DOMAIN)
class AgreementWriteGuard(
    private val records: Records
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != AGREEMENT || change.kind != RecordChangeKind.TRANSITIONED || change.transition !in TO_ACTIVE) return emptyList()
        val id = change.recordId ?: return emptyList()
        val before = change.before.orEmpty()
        return try {
            val type = Values.text(before["agreement_type"])
            val parcels = if (type in AgreementActivation.LAND_TYPES) records.related(AGREEMENT, id, AgreementActivation.LAND_PARCELS).size else 0
            val assembly =
                Records.uuid(before["assembly_engagement"])?.let { engagementType(it) }
            AgreementActivation.findings(before, parcels, assembly)
        } catch (_: ForbiddenException) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "Registrar la vigencia exige leer los predios y la interacción de asamblea del acuerdo",
                    AgreementActivation.LAND_PARCELS,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun engagementType(id: UUID): String? = Values.text(records.find("engagement", id)?.attributes?.get("engagement_type"))

    companion object {
        const val AGREEMENT = "agreement"
        val TO_ACTIVE = setOf("activate", "activate_from_draft", "resume")
    }
}
```

- [ ] **Paso 4: Las cláusulas que nombra un compromiso**

En `src/main/kotlin/sgspe/common/ModelCatalog.kt`, agregar después de `fun requiredDependents(target: String)`:

```kotlin
    // lo que DependentsDeleteGuard vigila al borrar `target`: las relaciones obligatorias y, desde F3, las opcionales de un objeto
    // requiresReason o appendOnly, porque ON DELETE SET NULL vaciaría el vínculo de ese registro sin motivo ni fila de auditoría
    // (commitment.agreement_clause, stakeholder_assessment.position)
    fun guardedDependents(target: String): List<Pair<String, String>> =
        relationships
            .filter { rel ->
                val source = objects[rel.source]
                rel.type == "MANY_TO_ONE" && rel.target == target && rel.fieldName != null &&
                    (rel.required || source?.requiresReason == true || source?.appendOnly == true)
            }.map { it.source to it.fieldName!! }
```

En `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`, cambiar `catalog.requiredDependents(change.objectName)` por
`catalog.guardedDependents(change.objectName)` y agregar al comentario de la clase, después de «porque la base lo rechazaría
igual.»:

```kotlin
// desde F3 vigila también las relaciones opcionales de un objeto requiresReason o appendOnly (ModelCatalog.guardedDependents):
// ahí la base no falla, pero SET NULL cambiaría ese registro sin motivo ni auditoría (borrar una cláusula que nombra un
// compromiso).
```

- [ ] **Paso 5: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `AgreementActivationTest` 3/3 y `DependentsDeleteGuardTest` 3/3.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/agreement/AgreementWriteGuard.kt src/test/kotlin/sgspe/agreement/AgreementActivationTest.kt \
  src/test/kotlin/sgspe/agreement/AgreementGuardApiTest.kt src/main/kotlin/sgspe/common/ModelCatalog.kt \
  src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt src/test/kotlin/sgspe/common/DependentsDeleteGuardTest.kt
git commit -m "$(cat <<'EOF'
feat: AgreementWriteGuard para la vigencia de los acuerdos

Las fechas de firma e inicio, los predios y la comunidad de un permiso de uso de terreno o una
servidumbre, y la asamblea de una autorización de asamblea antes de llegar a active. Una cláusula
que nombra un compromiso no se borra: DependentsDeleteGuard mira también las relaciones opcionales
de un objeto requiresReason o appendOnly.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `AgreementGuardApiTest` (3), `AgreementWorkflowApiTest` de 3.1 y, por el cambio de
`DependentsDeleteGuard`, `ProjectScopeApiTest`, `StakeholderAssessmentApiTest` y `PermissionMatrixApiTest` (borran registros
recién creados, que nada nombra).

### Tarea 16: Avisos de aprobación pendiente sobre wasichai-notifications (D2)

`sgspe.notification` publica en el módulo wasichai-notifications y no tiene tablas ni bandeja propias (D2, ADR-0018).
`Notices` es la puerta al módulo: un aviso por clave estable a personas concretas (`Audience.User`), con enlace `RECORD` al
registro; un aviso mal formado se registra y no hace fallar la escritura. `ApprovalNoticeListener` publica el aviso `ACTION`
«aprobación pendiente» al enviar un proyecto (`submit_for_review`), un acta (`submit_minutes`) o un compromiso (`submit`) a
los asignados vigentes con un rol de la aprobación que sigue (`Assignments.recipients`, ASM-070), salvo quien envió; un
compromiso que exige nivel superior avisa solo a los de `approve_senior`. Cualquier transición que sale del estado pendiente
resuelve el aviso. El bucle del módulo ya está apagado en las pruebas: el plan 3.1 fija `wasichai.notifications.tick=0s` en
`integrationDefaults()` de `build.gradle.kts`, así que esta tarea no toca ese archivo.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/notification/Notices.kt`, `ApprovalNotices.kt`
- Probar: `src/test/kotlin/sgspe/notification/NoticesTest.kt`, `ApprovalNoticesApiTest.kt`

**Interfaces:**
- Consume: `wasichai.notifications.Notifications.publish(organizationId, source, NotificationDraft): UUID?` y
  `resolve(organizationId, source, key): Boolean`; `Audience.User`, `NotificationKind`, `NotificationLink.Record`;
  `Assignments.recipients(project, objectName, transition, exclude)` (F2), que lee `project_assignment` como quien envía:
  el plan 3.1 da READ sobre ella a todo rol que aplica una transición; `SeniorApprovalReader` (Tarea 8) y `NotReadable`
  (Tarea 7); `Commitments` (Tarea 2).
- Produce: `Notices.publish(organizationId, source, key, kind, text, users, link, dueAt): UUID?`; `Notices.resolve`;
  `Notices.draft(...)`; `Notices.APPROVALS` = `sgspe.approvals`, `Notices.DEADLINES` = `sgspe.deadlines`;
  `NoticeTexts.approvalPending`, `commitmentDueSoon`, `commitmentOverdue`, `taskOverdue`; `ApprovalNoticeListener.STEPS`,
  `key(step, id)`, `label(objectName, attributes)`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/notification/NoticesTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.notifications.Audience
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import java.time.LocalDate
import java.util.UUID

// los avisos que SGSPE publica en wasichai-notifications (decisión D2): textos, claves y destinatarios, sin base
class NoticesTest {
    private val due = LocalDate.parse("2026-10-09")

    @Test
    fun `los textos nombran el registro y la fecha, nunca a una persona`() {
        assertEquals("Compromiso por aprobar: C-001 · Agua" to "El compromiso espera su aprobación.", NoticeTexts.approvalPending("commitment", "C-001 · Agua"))
        assertEquals("Compromiso por vencer: C-001", NoticeTexts.commitmentDueSoon("C-001", due).first)
        assertTrue(NoticeTexts.commitmentDueSoon("C-001", due).second.contains("09/10/2026"))
        assertEquals("Compromiso vencido, escalamiento 2: C-001", NoticeTexts.commitmentOverdue("C-001", due, 2).first)
        assertEquals("Tarea vencida: Revisar acta", NoticeTexts.taskOverdue("Revisar acta", due, 0).first)
    }

    @Test
    fun `el borrador va a cada persona una vez, con su clave y su enlace`() {
        val user = UUID.randomUUID()
        val record = UUID.randomUUID()
        val draft =
            Notices.draft(
                "approval_pending:commitment:$record",
                NotificationKind.ACTION,
                "t" to "b",
                listOf(user, user),
                NotificationLink.Record("commitment", record),
                null
            )
        assertEquals(listOf<Audience>(Audience.User(user)), draft.audience)
        assertEquals("approval_pending:commitment:$record", draft.key)
        assertEquals(NotificationKind.ACTION, draft.kind)
        assertTrue(Regex("^[a-z][a-z0-9_.:-]{1,80}$").matches(Notices.APPROVALS))
        assertTrue(Regex("^[a-z][a-z0-9_.:-]{1,80}$").matches(Notices.DEADLINES))
    }

    @Test
    fun `cada envio a aprobacion tiene su clave estable y su etiqueta sin datos personales`() {
        val id = UUID.randomUUID()
        val step = ApprovalNoticeListener.STEPS.first { it.objectName == "commitment" }
        assertEquals("approval_pending:commitment:$id", ApprovalNoticeListener.key(step, id))
        assertEquals(listOf("approve", "approve_senior"), step.approvals)
        assertEquals("C-001 · Agua", ApprovalNoticeListener.label("commitment", mapOf("code" to "C-001", "title" to "Agua")))
        assertEquals("PRY-1 · Proyecto", ApprovalNoticeListener.label("project", mapOf("code" to "PRY-1", "name" to "Proyecto")))
        assertEquals("engagement", ApprovalNoticeListener.label("engagement", emptyMap()))
    }
}
```

Crear `src/test/kotlin/sgspe/notification/ApprovalNoticesApiTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// el aviso «aprobación pendiente» en la bandeja de wasichai-notifications (decisión D2, workflows.md «Destinatarios por rol»,
// ASM-070): llega a los asignados vigentes con un rol de la aprobación que sigue, nunca a quien envió, a un asignado inactivo ni
// a otro rol, y se resuelve cuando el compromiso sale de pending_approval
class ApprovalNoticesApiTest : F3DomainApiTest() {
    private fun inbox(token: String) = tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, token))["content"].values()

    private fun pendingFor(
        token: String,
        id: String
    ): Boolean = inbox(token).any { it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION" }

    @Test
    fun `enviar un compromiso avisa a los aprobadores asignados y aprobarlo lo resuelve`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val senior = member("PROJECT_MANAGER", project)
        val other = member("GIS_ANALYST", project)
        val inactive = userWithRole("APPROVER").also { assignWith(it, project, "APPROVER", mapOf("active" to false)) }
        val manager = member("COMMITMENT_MANAGER", project)
        val id = commitment(project, creator = manager)
        move("commitment", id, "submit", manager.token, "envío")
        assertTrue(pendingFor(approver.token, id))
        assertTrue(pendingFor(senior.token, id))
        assertTrue(!pendingFor(other.token, id))
        assertTrue(!pendingFor(inactive.token, id))
        assertTrue(!pendingFor(manager.token, id))
        move("commitment", id, "approve", approver.token, "aprobado")
        assertTrue(!pendingFor(approver.token, id))
    }

    @Test
    fun `un compromiso que exige nivel superior avisa solo a los roles de approve_senior`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val senior = member("PROJECT_MANAGER", project)
        val id = commitment(project, attributes = mapOf("origin_engagement" to engagement(project, "MESA DE DIALOGO")))
        move("commitment", id, "submit", reason = "envío")
        assertTrue(pendingFor(senior.token, id))
        assertEquals(false, pendingFor(approver.token, id))
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.notification.NoticesTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'NoticeTexts'`.

- [ ] **Paso 3: La puerta al módulo y los textos**

Crear `src/main/kotlin/sgspe/notification/Notices.kt`:

```kotlin
package sgspe.notification

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import wasichai.notifications.Audience
import wasichai.notifications.NotificationDraft
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import wasichai.notifications.Notifications
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

// los textos de los avisos, puros y en español (decisión D2: wasichai-notifications los entrega; SGSPE decide a quién y qué
// dice). ningún texto lleva datos personales: el código y el título del registro, sus fechas y el nivel (RQ-SEC-010)
object NoticeTexts {
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun approvalPending(
        objectName: String,
        label: String
    ): Pair<String, String> =
        when (objectName) {
            "project" -> "Proyecto por revisar: $label" to "El proyecto espera su revisión para pasar a vigente."
            "engagement" -> "Acta por aprobar: $label" to "El acta de la interacción espera su aprobación."
            "commitment" -> "Compromiso por aprobar: $label" to "El compromiso espera su aprobación."
            else -> "Aprobación pendiente: $label" to "El registro espera su aprobación."
        }

    fun commitmentDueSoon(
        label: String,
        due: LocalDate
    ): Pair<String, String> = "Compromiso por vencer: $label" to "Vence el ${due.format(DATE)}."

    fun commitmentOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Compromiso vencido: $label" to "Venció el ${due.format(DATE)}."
        } else {
            "Compromiso vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierto: revise con su responsable."
        }

    fun taskOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Tarea vencida: $label" to "Venció el ${due.format(DATE)}."
        } else {
            "Tarea vencida, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierta."
        }
}

// la puerta de SGSPE a wasichai-notifications (decisión D2, WAS-ADR-0046, WAS-ADR-0060): bandeja, correo, preferencias y
// SSE son del módulo; aquí solo se publica y se resuelve con una clave estable por aviso, así un aviso repetido no se
// duplica (RF-NTF-001) y uno que ya no aplica se resuelve. Notifications.publish se une a la transacción de quien escribe:
// un aviso se confirma o se revierte con su escritura. un aviso mal formado (un error de SGSPE) se registra y no hace fallar
// la escritura del usuario; un destinatario que dejó la organización lo descarta el módulo con una advertencia
@Component
class Notices(
    private val notifications: Notifications
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // el id del aviso, o null si no quedó ningún destinatario
    suspend fun publish(
        organizationId: UUID,
        source: String,
        key: String,
        kind: NotificationKind,
        text: Pair<String, String>,
        users: Collection<UUID>,
        link: NotificationLink? = null,
        dueAt: Instant? = null
    ): UUID? {
        if (users.isEmpty()) return null
        return try {
            notifications.publish(organizationId, source, draft(key, kind, text, users, link, dueAt))
        } catch (e: IllegalArgumentException) {
            log.error("Notices: aviso {} {} mal formado, no se publicó", source, key, e)
            null
        }
    }

    suspend fun resolve(
        organizationId: UUID,
        source: String,
        key: String
    ): Boolean =
        try {
            notifications.resolve(organizationId, source, key)
        } catch (e: IllegalArgumentException) {
            log.error("Notices: clave {} {} mal formada, no se resolvió", source, key, e)
            false
        }

    companion object {
        // las fuentes de SGSPE en el módulo (^[a-z][a-z0-9_.:-]{1,80}$; nunca manual, rule:… ni automation:…)
        const val APPROVALS = "sgspe.approvals"
        const val DEADLINES = "sgspe.deadlines"

        // el borrador de un aviso a personas concretas, con su enlace al registro (la bandeja lo quita a quien no lee el objeto)
        fun draft(
            key: String,
            kind: NotificationKind,
            text: Pair<String, String>,
            users: Collection<UUID>,
            link: NotificationLink?,
            dueAt: Instant?
        ): NotificationDraft =
            NotificationDraft(
                kind = kind,
                title = text.first,
                audience = users.distinct().map { Audience.User(it) },
                body = text.second,
                link = link,
                key = key,
                dueAt = dueAt
            )
    }
}
```

- [ ] **Paso 4: El aviso de aprobación pendiente**

Crear `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`:

```kotlin
package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.Values
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import java.util.UUID

// un envío a aprobación y lo que lo resuelve (workflows.md, «Avisos» y «Destinatarios por rol»)
data class ApprovalStep(
    val objectName: String,
    val submit: String,
    val pending: String,
    val approvals: List<String>
)

// el aviso «aprobación pendiente» (RF-NTF-001): al enviar un proyecto, un acta o un compromiso a aprobación, un aviso ACTION
// para los asignados vigentes al proyecto cuyo role_name está entre los roles de la transición de aprobación que sigue
// (ASM-070), salvo quien envió y las cuentas de servicio (Assignments.recipients). los roles salen de model/workflows, nunca
// de un literal (RQ-SEC-005). un compromiso que exige nivel superior (RN-ENG-002) avisa solo a los roles de approve_senior,
// con la regla evaluada en el momento. cualquier transición que sale del estado pendiente resuelve el aviso: la bandeja
// muestra lo que sigue siendo cierto. corre en la transacción de la transición, como quien la aplica; si no puede leer las
// asignaciones, o no hay destinatarios, lo registra y la transición sigue (workflows.md, «Sin coincidencias»)
@Component
@Order(200)
class ApprovalNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val senior: SeniorApprovalReader
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.TRANSITIONED) return
        val step = STEPS.firstOrNull { it.objectName == change.objectName } ?: return
        when {
            change.transition == step.submit -> publish(step, change)
            catalog.transition(step.objectName, change.transition ?: return)?.from == step.pending ->
                notices.resolve(change.organizationId, Notices.APPROVALS, key(step, change.recordId))
        }
    }

    private suspend fun publish(
        step: ApprovalStep,
        change: RecordChange
    ) {
        val attributes = change.after ?: change.before.orEmpty()
        val project = if (step.objectName == PROJECT) change.recordId else Records.uuid(attributes["project"]) ?: return
        val users =
            try {
                approvals(step, change.recordId, attributes).flatMap { assignments.recipients(project, step.objectName, it, change.userId) }.map { it.userId }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForbiddenException) {
                log.warn("ApprovalNoticeListener: quien envió {} {} no lee las asignaciones; no hay aviso", step.objectName, change.recordId, e)
                return
            }
        if (users.isEmpty()) {
            log.warn(
                "ApprovalNoticeListener: {} {} no tiene asignaciones vigentes con un rol de {}; no hay aviso",
                step.objectName,
                change.recordId,
                step.approvals
            )
            return
        }
        notices.publish(
            change.organizationId,
            Notices.APPROVALS,
            key(step, change.recordId),
            NotificationKind.ACTION,
            NoticeTexts.approvalPending(step.objectName, label(step.objectName, attributes)),
            users,
            NotificationLink.Record(step.objectName, change.recordId)
        )
    }

    // las transiciones cuyos roles reciben el aviso: un compromiso que exige nivel superior, solo approve_senior
    private suspend fun approvals(
        step: ApprovalStep,
        id: UUID,
        attributes: Map<String, Any?>
    ): List<String> {
        if (step.objectName != Commitments.COMMITMENT) return step.approvals
        val required =
            try {
                senior.required(id, attributes)
            } catch (e: sgspe.commitment.NotReadable) {
                false
            }
        return if (required) listOf(SENIOR) else step.approvals
    }

    companion object {
        const val PROJECT = "project"
        const val SENIOR = "approve_senior"

        val STEPS =
            listOf(
                ApprovalStep(PROJECT, "submit_for_review", "in_review", listOf("approve")),
                ApprovalStep("engagement", "submit_minutes", "minutes_in_review", listOf("approve_minutes")),
                ApprovalStep(Commitments.COMMITMENT, "submit", "pending_approval", listOf("approve", SENIOR))
            )

        // una clave por registro: volver a enviarlo reabre el mismo aviso (es noticia otra vez, WAS-ADR-0060)
        fun key(
            step: ApprovalStep,
            id: UUID
        ): String = "approval_pending:${step.objectName}:$id"

        // cómo se nombra el registro en el aviso: código y nombre o título, sin datos personales
        fun label(
            objectName: String,
            attributes: Map<String, Any?>
        ): String =
            when (objectName) {
                PROJECT -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["name"]))
                Commitments.COMMITMENT -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["title"]))
                else -> listOfNotNull(Values.text(attributes["title"]))
            }.joinToString(" · ").ifEmpty { objectName }
    }
}
```

- [ ] **Paso 5: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `NoticesTest` 3/3; `ArchitectureTest` no encuentra nombres de rol en `sgspe.notification`.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/notification/Notices.kt src/main/kotlin/sgspe/notification/ApprovalNotices.kt \
  src/test/kotlin/sgspe/notification/NoticesTest.kt src/test/kotlin/sgspe/notification/ApprovalNoticesApiTest.kt
git commit -m "$(cat <<'EOF'
feat: aviso de aprobación pendiente sobre wasichai-notifications

Al enviar un proyecto, un acta o un compromiso a aprobación, un aviso ACTION a los asignados
vigentes con un rol de la aprobación que sigue, sin nombres de rol en el código; salir del estado
pendiente lo resuelve (decisión D2).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `ApprovalNoticesApiTest` (2), `RecipientsApiTest` y, como punto de control,
`ProjectWorkflowApiTest` y `EngagementWorkflowApiTest` (sus transiciones de envío publican ahora un aviso).

### Tarea 17: JOB-01 `DeadlineJob` y los avisos de plazo

JOB-01 (workflows.md): cada hora, en una réplica y como la plataforma de cada organización con el modelo de F3 (por
`Organizations`, sobre `TenantDirectory`), escribe `deadline_status` de compromisos y tareas y `validity_status` de acuerdos
solo en los estados de su tabla, con los parámetros de su proyecto, solo si cambia y bajo el candado del registro, con el
motivo `situación de plazo (JOB-01 DeadlineJob)` y la etiqueta `job:deadline`; crea la tarea de escalamiento de cada nivel
alcanzado de un compromiso vencido (`escalation:commitment:<id>:<fecha>:<nivel>`, sin asignado ni fecha). Nunca aplica
transiciones (RN-WFL-001). `DeadlineNotices` es una fuente de wasichai-notifications (decisión 9 de este plan): cada hora
devuelve todos los avisos de plazo vigentes (por vencer al responsable; vencido al responsable y, por nivel, a los roles de
`verify` y de `approve_senior` de WF-D; tarea vencida al asignado y a la transición de referencia de su registro vinculado),
y el módulo resuelve los que dejan de serlo.

**Archivos:**
- Crear: `src/main/kotlin/sgspe/deadline/Deadlines.kt`, `DeadlineJob.kt`, `DeadlineNotices.kt`
- Modificar: `src/main/resources/application.yml`
- Probar: `src/test/kotlin/sgspe/deadline/DeadlinesTest.kt`, `DeadlineNoticePlanTest.kt`, `DeadlineJobApiTest.kt`

**Interfaces:**
- Consume: `Organizations.having` y `Records.asPlatform(organizationId, source, block)` (`main`); `JobTasks.ensure`,
  `ProjectParameters`, `Commitments` (Tarea 2); `sgspe.jobs` de `application.yml` como lo deja la Tarea 4; `Notices`,
  `NoticeTexts` (Tarea 16);
  `Assignments.recipients` (F2); `wasichai.notifications.NotificationSource`.
- Produce: `Deadlines.TRACKED`, `status`, `validity`, `wanted`, `level`, `daysOverdue`; `DeadlineJob.runOnce(): Int?`,
  `DeadlineJob.SOURCE` = `job:deadline`, `REASON`, `TASK_REASON`, `escalationKey`; `DeadlineSweep`; `DeadlineNoticePlan`
  (`commitment`, `task`, `TASK_REFERENCE`); la propiedad `sgspe.jobs.deadline.cron` (`0 5 * * * *`).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/deadline/DeadlinesTest.kt`:

```kotlin
package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.UUID

// JOB-01: situación de plazo y de vigencia y niveles de escalamiento (RN-WFL-001, ASM-006, ASM-030), sin base
class DeadlinesTest {
    private val today = LocalDate.parse("2026-10-09")
    private val commitment = Deadlines.TRACKED.first { it.objectName == "commitment" }
    private val agreement = Deadlines.TRACKED.first { it.objectName == "agreement" }

    @Test
    fun `al dia, por vencer (hoy incluido) y vencido`() {
        assertEquals("AL DIA", Deadlines.status(today.plusDays(8), today, 7))
        assertEquals("POR VENCER", Deadlines.status(today.plusDays(7), today, 7))
        assertEquals("POR VENCER", Deadlines.status(today, today, 7))
        assertEquals("VENCIDO", Deadlines.status(today.minusDays(1), today, 7))
        assertNull(Deadlines.status(null, today, 7))
    }

    @Test
    fun `solo se calcula en los estados de su tabla`() {
        val due = mapOf("due_date" to today.minusDays(3).toString())
        assertEquals("VENCIDO", Deadlines.wanted(commitment, "in_progress", due, today, 7))
        assertNull(Deadlines.wanted(commitment, "pending_approval", due, today, 7))
        assertNull(Deadlines.wanted(commitment, "fulfilled", due, today, 7))
    }

    @Test
    fun `la vigencia de un acuerdo, sin fin, VIGENTE, vencida, VENCIDO`() {
        assertEquals("VIGENTE", Deadlines.wanted(agreement, "active", mapOf("expiry_date" to null), today, 7))
        assertEquals("POR VENCER", Deadlines.wanted(agreement, "suspended", mapOf("expiry_date" to today.plusDays(2).toString()), today, 7))
        assertEquals("VENCIDO", Deadlines.wanted(agreement, "active", mapOf("expiry_date" to today.minusDays(1).toString()), today, 7))
        assertNull(Deadlines.wanted(agreement, "concluded", mapOf("expiry_date" to today.minusDays(1).toString()), today, 7))
    }

    @Test
    fun `los niveles de escalamiento con los umbrales del proyecto`() {
        assertEquals(0, Deadlines.level(today.minusDays(6), today, 7, 15))
        assertEquals(1, Deadlines.level(today.minusDays(7), today, 7, 15))
        assertEquals(2, Deadlines.level(today.minusDays(15), today, 7, 15))
        assertEquals("escalation:commitment:x:2026-10-01:1", DeadlineJob.escalationKey("x", LocalDate.parse("2026-10-01"), 1))
    }

    @Test
    fun `una organizacion que falla no detiene a las demas y la vuelta lanza al final`() {
        val (bad, good) = UUID.randomUUID() to UUID.randomUUID()
        val done = mutableListOf<UUID>()
        val sweep = DeadlineSweep({ listOf(bad, good) }, { _, block -> block() }, { org -> if (org == bad) error("sin base") else 1.also { done += org } })
        val e = assertThrows<IllegalStateException> { runBlocking { sweep.run() } }
        assertEquals(listOf(good), done)
        assertTrue(e.message!!.contains(bad.toString()))
    }
}
```

Crear `src/test/kotlin/sgspe/deadline/DeadlineNoticePlanTest.kt`:

```kotlin
package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

// a quién va cada aviso de plazo (workflows.md, «Avisos» y «Destinatarios por rol»), sin base
class DeadlineNoticePlanTest {
    private val due = LocalDate.parse("2026-10-01")
    private val owner = UUID.randomUUID()

    @Test
    fun `por vencer avisa al responsable, sin responsable no hay aviso`() {
        val plan = DeadlineNoticePlan.commitment("c", "C-1", "POR VENCER", due, 0, owner)
        assertEquals(listOf("commitment_due_soon:c:2026-10-01"), plan.map { it.key })
        assertEquals(NoticeAudience.Person(owner), plan.single().audience)
        assertEquals(emptyList<PlannedNotice>(), DeadlineNoticePlan.commitment("c", "C-1", "POR VENCER", due, 0, null))
        assertEquals(emptyList<PlannedNotice>(), DeadlineNoticePlan.commitment("c", "C-1", "AL DIA", due, 0, owner))
    }

    @Test
    fun `vencido avisa al responsable y, en cada nivel alcanzado, a verify y a approve_senior`() {
        val plan = DeadlineNoticePlan.commitment("c", "C-1", "VENCIDO", due, 2, owner)
        assertEquals((0..2).map { "commitment_overdue:c:2026-10-01:$it" }, plan.map { it.key })
        assertEquals(
            listOf(NoticeAudience.Person(owner), NoticeAudience.Roles("commitment", "verify"), NoticeAudience.Roles("commitment", "approve_senior")),
            plan.map { it.audience }
        )
    }

    @Test
    fun `una tarea sin asignado escala directo al nivel 1 por su registro vinculado`() {
        val unassigned = DeadlineNoticePlan.task("t", "Revisar", "VENCIDO", due, 0, null, "engagement")
        assertEquals(listOf(NoticeAudience.Roles("engagement", "approve_minutes")), unassigned.map { it.audience })
        val assigned = DeadlineNoticePlan.task("t", "Revisar", "VENCIDO", due, 2, owner, "agreement")
        assertEquals(
            listOf(NoticeAudience.Person(owner), NoticeAudience.Roles("agreement", "activate"), DeadlineNoticePlan.SENIOR),
            assigned.map { it.audience }
        )
        assertEquals(emptyList<PlannedNotice>(), DeadlineNoticePlan.task("t", "Revisar", "POR VENCER", due, 0, owner, "commitment"))
    }
}
```

Crear `src/test/kotlin/sgspe/deadline/DeadlineJobApiTest.kt`:

```kotlin
package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import sgspe.common.Records
import java.time.Instant
import java.util.UUID

// JOB-01 (workflows.md, «Pruebas de trabajos»): deadline_status y validity_status sin mover ningún estado, la tarea de
// escalamiento una sola vez, el motivo y la etiqueta que nombran al trabajo, y los avisos de plazo de DeadlineNotices
class DeadlineJobApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    @Autowired
    private lateinit var notices: DeadlineNotices

    @Autowired
    private lateinit var records: Records

    @Test
    fun `un compromiso vencido queda VENCIDO, sigue en ejecucion, y su escalamiento crea una tarea una sola vez`() {
        val project = testProject()
        val id = approvedCommitment(project, attributes = mapOf("due_date" to "2026-01-05"))
        runBlocking { job.runOnce() }
        val after = record("commitment", id)
        assertEquals("VENCIDO", after["attributes"]["deadline_status"].asString())
        assertEquals("in_progress", after["state"].asString())
        val history = tree(send("GET", "/api/objects/commitment/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"].asString() == DeadlineJob.REASON })
        val tasks = "/api/objects/task/records?commitment=$id"
        val first = tree(send("GET", tasks, null, HttpStatus.OK))["totalElements"].asInt()
        assertEquals(2, first)
        runBlocking { job.runOnce() }
        assertEquals(first, tree(send("GET", tasks, null, HttpStatus.OK))["totalElements"].asInt())
        assertEquals(after["updatedAt"].asString(), record("commitment", id)["updatedAt"].asString())
    }

    @Test
    fun `un compromiso en borrador no tiene plazo y un acuerdo vencido sigue vigente en su estado`() {
        val project = testProject()
        val draft = commitment(project, attributes = mapOf("due_date" to "2026-01-05"))
        val agreement =
            createRecord(
                "agreement",
                mapOf(
                    "project" to project,
                    "code" to uniqueName("A-"),
                    "title" to "Convenio",
                    "agreement_type" to "CONVENIO",
                    "signed_date" to "2025-01-01",
                    "effective_date" to "2025-01-01",
                    "expiry_date" to "2026-01-01"
                ),
                withReason()
            )
        move("agreement", agreement, "activate_from_draft", reason = "firmado")
        runBlocking { job.runOnce() }
        assertTrue(record("commitment", draft)["attributes"]["deadline_status"].isNull)
        val expired = record("agreement", agreement)
        assertEquals("VENCIDO", expired["attributes"]["validity_status"].asString())
        assertEquals("active", expired["state"].asString())
    }

    @Test
    fun `los avisos de plazo van al responsable y, al escalar, a los roles de verify y de approve_senior del proyecto`() {
        val project = testProject()
        val owner = member("COMMITMENT_MANAGER", project)
        val verifier = member("SOCIAL_MANAGER", project)
        val senior = member("PROJECT_MANAGER", project)
        val id = approvedCommitment(project, owner, mapOf("due_date" to "2026-01-05"))
        runBlocking { job.runOnce() }
        val organization = UUID.fromString(me()["organizationId"].asString())
        val drafts = runBlocking { records.asPlatform(organization) { notices.currentNotifications(organization, Instant.now()) } }
        val mine = drafts.filter { it.key?.startsWith("commitment_overdue:$id:") == true }
        assertEquals(3, mine.size)
        val audience = mine.flatMap { it.audience }.toString()
        for (user in listOf(owner, verifier, senior)) assertTrue(audience.contains(user.id), audience)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.deadline.*'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'Deadlines'`.

- [ ] **Paso 3: La situación de plazo, pura**

Crear `src/main/kotlin/sgspe/deadline/Deadlines.kt`:

```kotlin
package sgspe.deadline

import sgspe.commitment.Commitments
import sgspe.common.Values
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// la situación de plazo y de vigencia, pura (RN-WFL-001, ASM-006, workflows.md «Plazos y escalamiento»): un campo derivado,
// nunca un estado. se calcula solo en los estados de su tabla y queda vacío en los demás, así VENCIDO siempre significa
// «abierto y atrasado»
object Deadlines {
    const val ON_TIME = "AL DIA"
    const val DUE_SOON = "POR VENCER"
    const val OVERDUE = "VENCIDO"
    const val VALID = "VIGENTE"

    // un objeto que JOB-01 sigue: su campo derivado, la fecha que mide y los estados en que se calcula
    data class Tracked(
        val objectName: String,
        val field: String,
        val dateField: String,
        val states: Set<String>,
        val validity: Boolean = false
    )

    val TASK_OPEN = setOf("open", "in_progress", "blocked")

    // F3: compromisos, tareas y acuerdos; F4 suma QCS y riesgos
    val TRACKED =
        listOf(
            Tracked(Commitments.COMMITMENT, "deadline_status", "due_date", Commitments.PENDING),
            Tracked("task", "deadline_status", "due_date", TASK_OPEN),
            Tracked("agreement", "validity_status", "expiry_date", setOf("active", "suspended"), validity = true)
        )

    // AL DIA, POR VENCER (vence dentro de dueSoonDays días, hoy incluido) o VENCIDO (la fecha ya pasó); null sin fecha
    fun status(
        due: LocalDate?,
        today: LocalDate,
        dueSoonDays: Long
    ): String? =
        when {
            due == null -> null
            today.isAfter(due) -> OVERDUE
            !today.plusDays(dueSoonDays).isBefore(due) -> DUE_SOON
            else -> ON_TIME
        }

    // la vigencia de un acuerdo: sin fecha de fin, VIGENTE
    fun validity(
        expiry: LocalDate?,
        today: LocalDate,
        dueSoonDays: Long
    ): String = status(expiry, today, dueSoonDays)?.let { if (it == ON_TIME) VALID else it } ?: VALID

    // el valor que corresponde a un registro: vacío fuera de sus estados
    fun wanted(
        tracked: Tracked,
        state: String?,
        attributes: Map<String, Any?>,
        today: LocalDate,
        dueSoonDays: Long
    ): String? {
        if (state !in tracked.states) return null
        val date = Values.text(attributes[tracked.dateField])?.let(LocalDate::parse)
        return if (tracked.validity) validity(date, today, dueSoonDays) else status(date, today, dueSoonDays)
    }

    // los días de atraso
    fun daysOverdue(
        due: LocalDate,
        today: LocalDate
    ): Long = ChronoUnit.DAYS.between(due, today)

    // el nivel de escalamiento de un plazo vencido (RF-WFL-004, ASM-030): 0 al vencer, 1 desde escalation_first_days de atraso y
    // 2 desde escalation_second_days
    fun level(
        due: LocalDate,
        today: LocalDate,
        firstDays: Long,
        secondDays: Long
    ): Int {
        val days = daysOverdue(due, today)
        return when {
            days >= secondDays -> 2
            days >= firstDays -> 1
            else -> 0
        }
    }
}
```

- [ ] **Paso 4: El trabajo**

Crear `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`:

```kotlin
package sgspe.deadline

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.JobTasks
import sgspe.common.LIMA
import sgspe.common.Organizations
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordResponse
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// la vuelta de JOB-01 sin candado, para probar su aislamiento con dobles: una organización que falla se registra y no detiene
// a las demás, y al final lanza si alguna falló (como JOB-03 y JOB-08)
class DeadlineSweep(
    private val organizations: suspend () -> List<UUID>,
    private val asPlatform: suspend (UUID, suspend () -> Int) -> Int,
    private val refresh: suspend (UUID) -> Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(): Int {
        var written = 0
        val failed = mutableListOf<UUID>()
        for (organization in organizations()) {
            try {
                written += asPlatform(organization) { refresh(organization) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += organization
                log.error("DeadlineJob falló en la organización {}", organization, e)
            }
        }
        check(failed.isEmpty()) { "DeadlineJob falló en ${failed.size} organizaciones: $failed (se escribieron $written registros)" }
        return written
    }
}

// JOB-01 DeadlineJob (sgspe.deadline, workflows.md): cada hora, una sola réplica (ClusterLock.tryLock), como la plataforma de
// cada organización con el modelo de F3 (TenantDirectory). escribe deadline_status de compromisos y tareas y validity_status de
// acuerdos con los parámetros de su proyecto, solo si cambia (P11) y bajo el candado del registro, con el motivo que nombra al
// trabajo (P16); crea la tarea de escalamiento de los niveles 1 y 2 de un compromiso vencido (generator_key, sin asignado ni
// fecha: la toma uno de los avisados). nunca aplica transiciones (RN-WFL-001). los avisos de plazo los publica DeadlineNotices, que lee
// lo que este trabajo escribió
@Component
class DeadlineJob(
    private val records: Records,
    private val organizations: Organizations,
    private val transactions: Transactions,
    private val tasks: JobTasks,
    private val locks: ClusterLock,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val sweep =
        DeadlineSweep(
            { organizations.having(Commitments.COMMITMENT) },
            { organization, block -> records.asPlatform(organization, SOURCE, block) },
            ::refresh
        )

    // los registros escritos y las tareas creadas; null si otra réplica tenía el candado
    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use { sweep.run() }
    }

    private suspend fun refresh(organization: UUID): Int {
        val today = LocalDate.now(clock.withZone(LIMA))
        val projects = mutableMapOf<String, Map<String, Any?>>()

        suspend fun project(id: String?): Map<String, Any?> =
            if (id == null) emptyMap() else projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }
        var written = 0
        var failed = 0
        for (tracked in Deadlines.TRACKED) {
            for (row in records.all(tracked.objectName)) {
                try {
                    val params = project(Values.text(row.attributes["project"]))
                    if (write(tracked, row, today, ProjectParameters.integer(params, "due_soon_days"))) written++
                    if (tracked.objectName == Commitments.COMMITMENT) written += escalate(row, today, params)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    log.error("DeadlineJob: no se actualizó {} {} de la organización {}", tracked.objectName, row.id, organization, e)
                }
            }
        }
        check(failed == 0) { "no se actualizaron $failed registros de la organización $organization (se escribieron $written)" }
        return written
    }

    // true si escribió; relee bajo el candado y recalcula, por si una persona lo cambió entretanto
    private suspend fun write(
        tracked: Deadlines.Tracked,
        row: RecordResponse,
        today: LocalDate,
        dueSoonDays: Long
    ): Boolean {
        val wanted = Deadlines.wanted(tracked, row.state, row.attributes, today, dueSoonDays)
        if (Values.same(row.attributes[tracked.field], wanted)) return false
        val id = UUID.fromString(row.id)
        return transactions.locked("${tracked.objectName}:$id") {
            val current = records.find(tracked.objectName, id) ?: return@locked false
            val again = Deadlines.wanted(tracked, current.state, current.attributes, today, dueSoonDays)
            if (Values.same(current.attributes[tracked.field], again)) {
                false
            } else {
                records.patch(tracked.objectName, id, mapOf(tracked.field to again), REASON)
                true
            }
        }
    }

    // la tarea de cada nivel alcanzado de un compromiso vencido y pendiente: una por compromiso, fecha y nivel
    private suspend fun escalate(
        row: RecordResponse,
        today: LocalDate,
        params: Map<String, Any?>
    ): Int {
        if (row.state !in Commitments.PENDING) return 0
        val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: return 0
        if (!today.isAfter(due)) return 0
        val level =
            Deadlines.level(due, today, ProjectParameters.integer(params, "escalation_first_days"), ProjectParameters.integer(params, "escalation_second_days"))
        val code = Values.text(row.attributes["code"]) ?: row.id
        return (1..level).count { reached ->
            tasks.ensure(
                escalationKey(row.id, due, reached),
                mapOf(
                    "title" to "Escalamiento nivel $reached: compromiso $code vencido",
                    "description" to "El compromiso venció el $due y sigue abierto. Tómela quien atienda el escalamiento.",
                    "project" to row.attributes["project"],
                    "commitment" to row.id
                ),
                TASK_REASON
            )
        }
    }

    companion object {
        const val LOCK = "sgspe.deadline"
        const val NAME = "JOB-01 DeadlineJob"

        // la etiqueta de sus filas de auditoría (WAS-ADR-0050)
        const val SOURCE = "job:deadline"
        const val REASON = "situación de plazo ($NAME)"
        const val TASK_REASON = "tarea de escalamiento ($NAME)"

        fun escalationKey(
            commitment: String,
            due: LocalDate,
            level: Int
        ): String = "escalation:${Commitments.COMMITMENT}:$commitment:$due:$level"
    }
}

// el reloj de JOB-01, cada hora en la zona de Lima (ASM-006); sgspe.jobs.enabled=false lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class DeadlineSchedule(
    private val job: DeadlineJob
) {
    @Scheduled(cron = "\${sgspe.jobs.deadline.cron:0 5 * * * *}", zone = "America/Lima")
    suspend fun hourly() {
        job.runOnce()
    }
}
```

En `src/main/resources/application.yml`, en `sgspe.jobs`, agregar antes de `retention:`:

```yaml
    deadline:
      # JOB-01, cada hora (ASM-006)
      cron: '0 5 * * * *'
```

- [ ] **Paso 5: La fuente de avisos de plazo**

Crear `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`:

```kotlin
package sgspe.deadline

import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.notification.NoticeTexts
import sgspe.notification.Notices
import sgspe.scope.Assignments
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordResponse
import wasichai.notifications.NotificationDraft
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import wasichai.notifications.NotificationSource
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// a quién va un aviso de plazo: una persona (el responsable o el asignado) o los asignados con un rol de una transición
sealed interface NoticeAudience {
    data class Person(
        val userId: UUID
    ) : NoticeAudience

    data class Roles(
        val objectName: String,
        val transition: String
    ) : NoticeAudience
}

// un aviso de plazo planeado: su clave, su tipo, su texto y a quién va, antes de resolver los destinatarios
data class PlannedNotice(
    val key: String,
    val kind: NotificationKind,
    val text: Pair<String, String>,
    val audience: NoticeAudience
)

// qué avisos de plazo corresponden a un registro, puro (workflows.md, «Avisos» y «Destinatarios por rol», ASM-030, ASM-070):
// el responsable al estar por vencer y al vencer (nivel 0); los roles de la transición de verificación o revisión en el nivel
// 1; los de approve_senior de WF-D en el nivel 2. cada nivel alcanzado sigue abierto mientras dure el atraso
object DeadlineNoticePlan {
    // la transición de referencia del nivel 1 de una tarea, por su registro vinculado (workflows.md); F4 suma QCS y riesgos
    val TASK_REFERENCE =
        linkedMapOf(
            Commitments.COMMITMENT to (Commitments.COMMITMENT to "verify"),
            "engagement" to ("engagement" to "approve_minutes"),
            "agreement" to ("agreement" to "activate"),
            "stakeholder" to ("stakeholder" to "assess")
        )
    val SENIOR = NoticeAudience.Roles(Commitments.COMMITMENT, "approve_senior")

    fun commitment(
        id: String,
        label: String,
        status: String?,
        due: LocalDate,
        level: Int,
        owner: UUID?
    ): List<PlannedNotice> =
        when (status) {
            Deadlines.DUE_SOON ->
                listOfNotNull(
                    owner?.let {
                        PlannedNotice(
                            "commitment_due_soon:$id:$due",
                            NotificationKind.WARNING,
                            NoticeTexts.commitmentDueSoon(label, due),
                            NoticeAudience.Person(it)
                        )
                    }
                )
            Deadlines.OVERDUE ->
                (0..level).mapNotNull { reached ->
                    val audience =
                        when (reached) {
                            0 -> owner?.let { NoticeAudience.Person(it) }
                            1 -> NoticeAudience.Roles(Commitments.COMMITMENT, "verify")
                            else -> SENIOR
                        } ?: return@mapNotNull null
                    PlannedNotice("commitment_overdue:$id:$due:$reached", NotificationKind.ACTION, NoticeTexts.commitmentOverdue(label, due, reached), audience)
                }
            else -> emptyList()
        }

    // una tarea vencida: nivel 0 al asignado o, sin asignado, directamente el nivel 1; sin registro vinculado conocido, solo
    // el asignado y el nivel 2
    fun task(
        id: String,
        label: String,
        status: String?,
        due: LocalDate,
        level: Int,
        assignee: UUID?,
        linked: String?
    ): List<PlannedNotice> {
        if (status != Deadlines.OVERDUE) return emptyList()
        val reference = linked?.let { TASK_REFERENCE[it] }
        val effective = if (assignee == null) maxOf(level, 1) else level
        return (0..effective).mapNotNull { reached ->
            val audience =
                when (reached) {
                    0 -> assignee?.let { NoticeAudience.Person(it) }
                    1 -> reference?.let { NoticeAudience.Roles(it.first, it.second) }
                    else -> SENIOR
                } ?: return@mapNotNull null
            PlannedNotice("task_overdue:$id:$due:$reached", NotificationKind.ACTION, NoticeTexts.taskOverdue(label, due, reached), audience)
        }
    }
}

// los avisos de plazo como fuente de wasichai-notifications (decisión D2, WAS-ADR-0046): cada hora, en una réplica y como la
// plataforma de cada organización, el módulo pide todo lo que debería estar abierto ahora; lo que deja de estar (un compromiso
// cumplido, una tarea hecha) se resuelve solo, y lo que no cambió no se vuelve a escribir. lee deadline_status, que mantiene
// JOB-01, así que un aviso llega a lo sumo una hora después del cambio de situación. un aviso a roles llega a los asignados
// vigentes del proyecto del registro con ese role_name (ASM-070); sin coincidencias no hay aviso (workflows.md)
@Component
class DeadlineNotices(
    private val records: Records,
    private val assignments: Assignments
) : NotificationSource {
    override val key: String = Notices.DEADLINES

    override val interval: Duration = Duration.ofHours(1)

    override suspend fun currentNotifications(
        organizationId: UUID,
        now: Instant
    ): List<NotificationDraft> {
        val today = now.atZone(LIMA).toLocalDate()
        val commitments =
            try {
                records.all(Commitments.COMMITMENT, criteria = listOf(Criteria.inState(Commitments.PENDING)))
            } catch (_: NotFoundException) {
                return emptyList()
            }
        val tasks = records.all(TASK, criteria = listOf(Criteria.inState(Deadlines.TASK_OPEN)))
        val projects = mutableMapOf<String, Map<String, Any?>>()
        val recipients = mutableMapOf<Triple<UUID, String, String>, List<UUID>>()

        suspend fun params(id: String): Map<String, Any?> = projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }

        suspend fun users(
            project: UUID,
            audience: NoticeAudience
        ): List<UUID> =
            when (audience) {
                is NoticeAudience.Person -> listOf(audience.userId)
                is NoticeAudience.Roles ->
                    recipients.getOrPut(Triple(project, audience.objectName, audience.transition)) {
                        assignments.recipients(project, audience.objectName, audience.transition, null).map { it.userId }
                    }
            }
        val drafts = mutableListOf<NotificationDraft>()

        suspend fun add(
            row: RecordResponse,
            objectName: String,
            plan: List<PlannedNotice>,
            due: LocalDate
        ) {
            val project = Records.uuid(row.attributes["project"]) ?: return
            for (notice in plan) {
                val users = users(project, notice.audience)
                if (users.isEmpty()) continue
                val link = NotificationLink.Record(objectName, UUID.fromString(row.id))
                drafts += Notices.draft(notice.key, notice.kind, notice.text, users, link, due.atStartOfDay(LIMA).toInstant())
            }
        }
        for (row in commitments) {
            val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: continue
            val params = params(Values.text(row.attributes["project"]) ?: continue)
            val level = level(due, today, params)
            val label = listOfNotNull(Values.text(row.attributes["code"]), Values.text(row.attributes["title"])).joinToString(" · ")
            val plan =
                DeadlineNoticePlan.commitment(
                    row.id,
                    label,
                    Values.text(row.attributes["deadline_status"]),
                    due,
                    level,
                    Records.uuid(row.attributes["owner_user_id"])
                )
            add(row, Commitments.COMMITMENT, plan, due)
        }
        for (row in tasks) {
            val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: continue
            val params = params(Values.text(row.attributes["project"]) ?: continue)
            val linked = DeadlineNoticePlan.TASK_REFERENCE.keys.firstOrNull { Values.text(row.attributes[it]) != null }
            val plan =
                DeadlineNoticePlan.task(
                    row.id,
                    Values.text(row.attributes["title"]) ?: TASK,
                    Values.text(row.attributes["deadline_status"]),
                    due,
                    level(due, today, params),
                    Records.uuid(row.attributes["assignee_user_id"]),
                    linked
                )
            add(row, TASK, plan, due)
        }
        return drafts
    }

    private fun level(
        due: LocalDate,
        today: LocalDate,
        params: Map<String, Any?>
    ): Int =
        if (!today.isAfter(due)) {
            0
        } else {
            Deadlines.level(due, today, ProjectParameters.integer(params, "escalation_first_days"), ProjectParameters.integer(params, "escalation_second_days"))
        }

    companion object {
        const val TASK = "task"
    }
}
```

- [ ] **Paso 6: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `DeadlinesTest` 5/5, `DeadlineNoticePlanTest` 3/3.

- [ ] **Paso 7: Commit**

```bash
git add src/main/kotlin/sgspe/deadline/Deadlines.kt src/main/kotlin/sgspe/deadline/DeadlineJob.kt \
  src/main/kotlin/sgspe/deadline/DeadlineNotices.kt src/main/resources/application.yml \
  src/test/kotlin/sgspe/deadline/DeadlinesTest.kt src/test/kotlin/sgspe/deadline/DeadlineNoticePlanTest.kt \
  src/test/kotlin/sgspe/deadline/DeadlineJobApiTest.kt
git commit -m "$(cat <<'EOF'
feat: JOB-01 situación de plazo, escalamiento y avisos de plazo

DeadlineJob escribe deadline_status y validity_status sin mover estados y crea la tarea de cada
nivel de escalamiento; DeadlineNotices publica en wasichai-notifications los avisos de plazo
vigentes y el módulo resuelve los que dejan de serlo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `DeadlineJobApiTest` (3) y `TaskWorkflowApiTest` de 3.1. `runOnce` recorre todos los
tenants de la base compartida: si falla por un tenant ajeno a la prueba, el log nombra la organización.

### Tarea 18: Tareas de seguimiento de JOB-03

`StakeholderFollowUpJob` (JOB-03) crea desde F3 las tareas que esperaban a `task` (RF-STK-010, RF-STK-014, ASM-022): la de
evaluación periódica de un grupo de interés `assessed`, `stakeholder_review_days` después de su última evaluación, y la de
inactividad, `inactivity_days` sin contacto (o desde el alta si nunca lo hubo), nunca en un retirado. Cada una por su clave
(decisión 11 de este plan), asignada al responsable del grupo de interés si su par está completo. Sus escrituras ya llevan la
etiqueta `job:stakeholder-follow-up` desde `main` (`StakeholderFollowUpJob.SOURCE`, UPS-03): esta tarea no la toca.

**Archivos:**
- Modificar: `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt`
- Probar: `src/test/kotlin/sgspe/stakeholder/FollowUpTasksTest.kt`, `FollowUpTasksApiTest.kt`

**Interfaces:**
- Consume: `JobTasks.ensure`, `ProjectParameters` (Tarea 2); `F3DomainApiTest` (Tarea 2b); `StakeholderFollowUpJob.SOURCE` y
  su `Records.asPlatform(organization, SOURCE, block)` (`main`).
- Produce: `FollowUpTasks.review`, `inactivity`, `Planned`; `StakeholderFollowUpJob.TASK_REASON`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/stakeholder/FollowUpTasksTest.kt`:

```kotlin
package sgspe.stakeholder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

// JOB-03 desde F3: las tareas de evaluación periódica y de inactividad (RF-STK-010, RF-STK-014, ASM-022), sin base
class FollowUpTasksTest {
    private val today = LocalDate.parse("2026-10-09")

    @Test
    fun `la evaluacion periodica vence stakeholder_review_days despues de la ultima, solo en assessed`() {
        val planned = FollowUpTasks.review("s", "assessed", today.minusDays(30), 30, today)!!
        assertEquals("stakeholder_review:s:2026-10-09", planned.key)
        assertEquals(today, planned.due)
        assertNull(FollowUpTasks.review("s", "assessed", today.minusDays(29), 30, today))
        assertNull(FollowUpTasks.review("s", "profiled", today.minusDays(90), 30, today))
    }

    @Test
    fun `la inactividad cuenta desde el ultimo contacto o, sin contacto, desde el alta, nunca en un retirado`() {
        assertEquals("stakeholder_inactivity:s:2026-07-11", FollowUpTasks.inactivity("s", "identified", today.minusDays(90), null, 90, today)?.key)
        assertNull(FollowUpTasks.inactivity("s", "identified", today.minusDays(89), null, 90, today))
        assertEquals("stakeholder_inactivity:s:2026-01-01", FollowUpTasks.inactivity("s", "profiled", null, LocalDate.parse("2026-01-01"), 90, today)?.key)
        assertNull(FollowUpTasks.inactivity("s", "retired", null, LocalDate.parse("2026-01-01"), 90, today))
    }
}
```

Crear `src/test/kotlin/sgspe/stakeholder/FollowUpTasksApiTest.kt`:

```kotlin
package sgspe.stakeholder

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// JOB-03 desde F3 (workflows.md): la tarea de inactividad de un grupo de interés sin contacto, asignada a su responsable, una
// sola vez aunque el trabajo corra otra vez
class FollowUpTasksApiTest : F3DomainApiTest() {
    @Autowired
    private lateinit var job: StakeholderFollowUpJob

    @Test
    fun `un grupo de interes sin contacto mas alla de inactivity_days recibe una tarea, una sola vez`() {
        val project = project(mapOf("inactivity_days" to 0))
        val owner = member("STAKEHOLDER_MANAGER", project)
        val id = stakeholder(project, profileFields(owner))
        runBlocking { job.runOnce() }
        val tasks = tree(send("GET", "/api/objects/task/records?stakeholder=$id", null, HttpStatus.OK))
        assertEquals(1, tasks["totalElements"].asInt())
        assertEquals(owner.id, tasks["content"][0]["attributes"]["assignee_user_id"].asString())
        runBlocking { job.runOnce() }
        assertEquals(1, tree(send("GET", "/api/objects/task/records?stakeholder=$id", null, HttpStatus.OK))["totalElements"].asInt())
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.stakeholder.FollowUpTasksTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'FollowUpTasks'`.

- [ ] **Paso 3: Las tareas en el trabajo**

En `src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt`:

1. Agregar los imports `sgspe.common.JobTasks`, `sgspe.common.ProjectParameters`, `wasichai.core.data.RecordResponse` y
   `java.time.Clock`.
2. Después del objeto `FollowUp`, agregar:

```kotlin
// las tareas de seguimiento de un grupo de interés vigente, puras (RF-STK-010, RF-STK-014, ASM-022): la de evaluación
// periódica, stakeholder_review_days después de su última evaluación, y la de inactividad, inactivity_days sin contacto (o
// desde el alta si nunca lo hubo). cada una lleva la clave del plazo que la originó: no se repite mientras el plazo sea el
// mismo, y una evaluación o un contacto nuevos dan un plazo nuevo. los títulos no nombran al grupo de interés: la tarea lo
// vincula
object FollowUpTasks {
    data class Planned(
        val key: String,
        val title: String,
        val description: String,
        val due: LocalDate
    )

    const val RETIRED = "retired"
    const val ASSESSED = "assessed"

    fun review(
        id: String,
        state: String?,
        lastAssessment: LocalDate?,
        reviewDays: Long,
        today: LocalDate
    ): Planned? {
        if (state != ASSESSED || lastAssessment == null) return null
        val due = lastAssessment.plusDays(reviewDays)
        if (today.isBefore(due)) return null
        return Planned(
            "stakeholder_review:$id:$due",
            "Evaluación periódica de un grupo de interés",
            "Pasaron $reviewDays días desde su última evaluación ($lastAssessment): evalúelo otra vez (RF-STK-010).",
            due
        )
    }

    fun inactivity(
        id: String,
        state: String?,
        lastContact: LocalDate?,
        created: LocalDate?,
        inactivityDays: Long,
        today: LocalDate
    ): Planned? {
        if (state == RETIRED) return null
        val reference = lastContact ?: created ?: return null
        val due = reference.plusDays(inactivityDays)
        if (today.isBefore(due)) return null
        return Planned(
            "stakeholder_inactivity:$id:$reference",
            "Retomar el contacto con un grupo de interés",
            "No hay contacto registrado desde $reference ($inactivityDays días o más): planifique una interacción (RF-STK-014).",
            due
        )
    }
}
```

3. En el comentario de la clase, reemplazar «evaluación periódica y de inactividad llegan en F3, con `task`.» por
   «evaluación periódica y de inactividad (FollowUpTasks) llegan en F3, con `task`: una por clave (JobTasks), asignada al
   responsable del grupo de interés si lo tiene.».
4. Agregar al constructor, después de `private val locks: ClusterLock`, los parámetros `private val tasks: JobTasks` y
   `private val clock: Clock`. El `FollowUpSweep` ya escribe con `records.asPlatform(organization, SOURCE, block)` desde
   `main`.
5. En `followUp`, después de `val contacts = FollowUp.latest(interactions)`, agregar:

```kotlin
        val today = LocalDate.now(clock.withZone(LIMA))
        val projects = mutableMapOf<String, Map<String, Any?>>()

        // los parámetros del proyecto de un grupo de interés, leídos una vez por proyecto
        suspend fun params(project: Any?): Map<String, Any?> {
            val id = Values.text(project) ?: return emptyMap()
            return projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }
        }
```

   y dentro del `try` del recorrido, después de `if (refresh(stakeholder.id, stakeholder.attributes, contacts[stakeholder.id])) written++`:

```kotlin
                written += followUpTasks(stakeholder, contacts[stakeholder.id], params(stakeholder.attributes["project"]), today)
```

6. Agregar antes de `refresh`:

```kotlin
    // las tareas de seguimiento que faltan; devuelve cuántas creó
    private suspend fun followUpTasks(
        stakeholder: RecordResponse,
        lastContact: LocalDate?,
        params: Map<String, Any?>,
        today: LocalDate
    ): Int {
        val a = stakeholder.attributes
        val planned =
            listOfNotNull(
                FollowUpTasks.review(
                    stakeholder.id,
                    stakeholder.state,
                    Values.text(a["last_assessment_date"])?.let(LocalDate::parse),
                    ProjectParameters.integer(params, "stakeholder_review_days"),
                    today
                ),
                FollowUpTasks.inactivity(
                    stakeholder.id,
                    stakeholder.state,
                    lastContact ?: Values.text(a["last_contact_date"])?.let(LocalDate::parse),
                    stakeholder.createdAt?.atZone(LIMA)?.toLocalDate(),
                    ProjectParameters.integer(params, "inactivity_days"),
                    today
                )
            )
        // el responsable del grupo de interés, solo si el par está completo (UserReferenceGuard lo exige junto)
        val ownerId = Values.text(a["owner_user_id"])
        val ownerEmail = Values.text(a["owner_user_email"])
        val owner = if (ownerId != null && ownerEmail != null) mapOf("assignee_user_id" to ownerId, "assignee_user_email" to ownerEmail) else emptyMap()
        return planned.count { task ->
            tasks.ensure(
                task.key,
                mapOf(
                    "title" to task.title,
                    "description" to task.description,
                    "due_date" to task.due.toString(),
                    "project" to a["project"],
                    "stakeholder" to stakeholder.id
                ) + owner,
                TASK_REASON
            )
        }
    }

```

7. En el `companion object`, después de `const val REASON = "último contacto ($NAME)"` (`SOURCE` ya está en `main`), agregar:

```kotlin
        const val TASK_REASON = "tarea de seguimiento ($NAME)"
```

- [ ] **Paso 4: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `FollowUpTasksTest` 2/2 y `FollowUpTest` y `FollowUpSweepTest` siguen en verde.

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/stakeholder/StakeholderFollowUpJob.kt src/test/kotlin/sgspe/stakeholder/FollowUpTasksTest.kt \
  src/test/kotlin/sgspe/stakeholder/FollowUpTasksApiTest.kt
git commit -m "$(cat <<'EOF'
feat: JOB-03 crea las tareas de evaluación periódica y de inactividad

Una tarea por clave, asignada al responsable del grupo de interés si lo tiene, con los
parámetros de su proyecto.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `FollowUpTasksApiTest` (1) y `StakeholderFollowUpJobApiTest` (sigue en verde: sus
grupos de interés recién creados no tienen tareas pendientes).

### Tarea 19: Los KPI-COM, puros

KPI-COM-01 a KPI-COM-11 (dashboards.md §4.4) como funciones puras sobre filas ya leídas como quien mira, con los conjuntos de
estados de §3.2 (`Commitments`, Tarea 7): registrados por estado; abiertos; vencidos con su desglose por días de atraso según
los umbrales de escalamiento de su proyecto; por vencer; cumplidos en el periodo; bloqueados; abiertos y vencidos por
responsable, proyecto, territorio y tema (los 10 primeros y «Otros»); formales por tema; cumplidos a tiempo frente al plazo
vigente y al original (la `previous_due_date` de la primera reprogramación); montos comprometidos frente a ejecutados por
moneda (ASM-063); y la serie de vencidos al cierre de cada mes, con la fecha vigente en ese mes reconstruida desde las
reprogramaciones, que son inmutables (RQ-AUD-003).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt`
- Probar: `src/test/kotlin/sgspe/dashboard/CommitmentKpisTest.kt`

**Interfaces:**
- Consume: `Commitments` (Tarea 2); `IndicatorResult`, `Breakdown`, `Numbers` (F2). No lee el modelo de F3: puede empezar
  antes de que el plan 3.1 se integre.
- Produce: `CommitmentRow`, `RescheduleRow`, `Escalation`; `CommitmentKpis.registered`, `open`, `overdue`, `dueSoon`,
  `fulfilled`, `blocked`, `breakdowns`, `formalByTheme`, `onTime`, `amounts`, `overdueSeries`, `dueAt`,
  `DEFAULT_CURRENCY`.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/CommitmentKpisTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-COM (dashboards.md §4.4) con filas de prueba, sin base
class CommitmentKpisTest {
    private val start = LocalDate.parse("2026-09-01")
    private val end = LocalDate.parse("2026-09-30")
    private var seq = 0

    private fun row(
        state: String,
        deadline: String? = null,
        due: String? = null,
        fulfilled: String? = null,
        agreed: String? = "2026-01-15",
        cancelled: String? = null,
        owner: String? = "a@sgspe.test",
        theme: String? = "SOCIAL",
        formality: String? = "FORMAL",
        amount: String? = "100",
        currency: String? = "PEN",
        executed: String? = null,
        project: String? = "p1",
        locality: String? = "l1",
        topic: String? = "t1"
    ) = CommitmentRow(
        "c${++seq}",
        project,
        state,
        owner,
        locality,
        null,
        topic,
        theme,
        formality,
        agreed?.let(LocalDate::parse),
        due?.let(LocalDate::parse),
        fulfilled?.let(LocalDate::parse),
        cancelled?.let(LocalDate::parse),
        deadline,
        amount?.let(::BigDecimal),
        currency,
        executed?.let(::BigDecimal)
    )

    @Test
    fun `registrados por estado en el orden de WF-D, abiertos y bloqueados`() {
        val rows = listOf(row("closed"), row("draft"), row("blocked"), row("blocked"), row("cancelled"))
        val registered = CommitmentKpis.registered(rows)
        assertEquals(5, registered.value)
        assertEquals(
            listOf("draft", "blocked", "closed", "cancelled"),
            registered.breakdowns
                .single()
                .rows
                .map { it["key"] }
        )
        assertEquals(3, CommitmentKpis.open(rows).value)
        assertEquals(2, CommitmentKpis.blocked(rows).value)
    }

    @Test
    fun `vencidos por dias de atraso con los umbrales de su proyecto, y por vencer`() {
        val today = LocalDate.parse("2026-10-09")
        val rows =
            listOf(
                row("in_progress", "VENCIDO", "2026-10-05"),
                row("blocked", "VENCIDO", "2026-09-30"),
                row("approved", "VENCIDO", "2026-09-01"),
                row("fulfilled", "VENCIDO", "2026-09-01"),
                row("in_progress", "POR VENCER", "2026-10-12")
            )
        val overdue = CommitmentKpis.overdue(rows, today) { Escalation(7, 15) }
        assertEquals(3, overdue.value)
        assertEquals(
            listOf("1-7" to 1, "8-15" to 1, "más de 15" to 1),
            overdue.breakdowns
                .single()
                .rows
                .map { it["key"] to it["value"] }
        )
        assertEquals(1, CommitmentKpis.dueSoon(rows).value)
    }

    @Test
    fun `cumplidos en el periodo y a tiempo frente al plazo vigente y al original`() {
        val onTime = row("fulfilled", due = "2026-09-20", fulfilled = "2026-09-15")
        val late = row("verified", due = "2026-09-10", fulfilled = "2026-09-12")
        val rescheduled = row("closed", due = "2026-09-25", fulfilled = "2026-09-20")
        val outside = row("fulfilled", due = "2026-08-20", fulfilled = "2026-08-15")
        val rows = listOf(onTime, late, rescheduled, outside)
        assertEquals(3, CommitmentKpis.fulfilled(rows, start, end).value)
        val reschedules =
            listOf(
                RescheduleRow(rescheduled.id, LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-05")),
                RescheduleRow(rescheduled.id, LocalDate.parse("2026-09-18"), LocalDate.parse("2026-09-25"), LocalDate.parse("2026-09-15"))
            )
        val result = CommitmentKpis.onTime(rows, reschedules, start, end)
        assertEquals(BigDecimal("66.7"), result.value)
        assertEquals(2 to 3, result.numerator to result.denominator)
        val original =
            result.breakdowns
                .single()
                .rows
                .first { it["key"] == "original" }
        assertEquals(1, original["numerator"])
    }

    @Test
    fun `formales por tema con agreed_date en el periodo, sin los cancelados`() {
        val rows =
            listOf(
                row("approved", agreed = "2026-09-02", theme = "AMBIENTAL"),
                row("draft", agreed = "2026-09-03", theme = "SOCIAL"),
                row("cancelled", agreed = "2026-09-03"),
                row("approved", agreed = "2026-09-04", formality = "VERBAL"),
                row("approved", agreed = "2026-08-31")
            )
        val result = CommitmentKpis.formalByTheme(rows, start, end)
        assertEquals(2, result.value)
        assertEquals(
            setOf("AMBIENTAL", "SOCIAL"),
            result.breakdowns
                .single()
                .rows
                .map { it["key"] }
                .toSet()
        )
    }

    @Test
    fun `montos por moneda de los comprometidos, con campana, la otra moneda es una advertencia`() {
        val rows =
            listOf(
                row("approved", amount = "100", executed = "40"),
                row("closed", amount = "50.5", executed = "50.5"),
                row("in_progress", amount = "10", currency = "USD"),
                row("draft", amount = "999")
            )
        val all = CommitmentKpis.amounts(rows, null)
        assertEquals(0, BigDecimal("150.5").compareTo(all.value as BigDecimal))
        assertEquals(
            listOf("PEN", "USD"),
            all.breakdowns
                .single()
                .rows
                .map { it["key"] }
        )
        val campaign = CommitmentKpis.amounts(rows, "PEN")
        assertEquals(
            listOf("PEN"),
            campaign.breakdowns
                .single()
                .rows
                .map { it["key"] }
        )
        assertEquals(1, campaign.warnings.size)
    }

    @Test
    fun `desgloses de abiertos y vencidos, los 10 primeros y Otros`() {
        val rows = (1..12).map { row("in_progress", "VENCIDO", "2026-09-01", owner = "o$it@sgspe.test") } + row("closed", owner = "o1@sgspe.test")
        val result = CommitmentKpis.breakdowns(rows, mapOf("p1" to "PRY-1"), mapOf("l1" to "Localidad"), mapOf("t1" to "Agua"))
        val owners = result.breakdowns.first { it.by == "owner" }.rows
        assertEquals(11, owners.size)
        assertEquals("Otros" to 2, owners.last()["key"] to owners.last()["value"])
        assertEquals(
            "PRY-1",
            result.breakdowns
                .first { it.by == "project" }
                .rows
                .single()["label"]
        )
        assertEquals(
            12,
            result.breakdowns
                .first { it.by == "topic" }
                .rows
                .single()["overdue"]
        )
    }

    @Test
    fun `la serie de vencidos al cierre de cada mes usa la fecha vigente en ese mes`() {
        // vence el 2026-08-10, se reprograma el 2026-08-20 al 2026-09-30 y se cumple el 2026-10-05
        val rescheduled = row("fulfilled", due = "2026-09-30", fulfilled = "2026-10-05", agreed = "2026-07-01")
        // aprobado, vencido desde el 2026-07-15 y nunca cumplido
        val open = row("in_progress", due = "2026-07-15", agreed = "2026-07-01")
        // cancelado el 2026-09-05 tras vencer el 2026-07-20
        val cancelled = row("cancelled", due = "2026-07-20", agreed = "2026-07-01", cancelled = "2026-09-05")
        val reschedules = listOf(RescheduleRow(rescheduled.id, LocalDate.parse("2026-08-10"), LocalDate.parse("2026-09-30"), LocalDate.parse("2026-08-20")))
        val series = CommitmentKpis.overdueSeries(listOf(rescheduled, open, cancelled), reschedules, LocalDate.parse("2026-10-31")).series!!
        val byMonth = series.associate { it["period"] to it["value"] }
        assertEquals(2, byMonth["2026-07"])
        // en agosto la fecha vigente del reprogramado ya es el 2026-09-30; el cancelado cuenta hasta el mes de su cancelación
        assertEquals(2, byMonth["2026-08"])
        assertEquals(1, byMonth["2026-09"])
        assertEquals(1, byMonth["2026-10"])
        assertEquals(12, series.size)
    }
}
```

- [ ] **Paso 2: Correrla y ver que falla**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.CommitmentKpisTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'CommitmentRow'`.

- [ ] **Paso 3: Los KPI**

Crear `src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt`:

```kotlin
package sgspe.dashboard

import sgspe.commitment.Commitments
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

// un compromiso como lo leen los KPI-COM, ya acotado a los filtros y a los permisos de quien mira
data class CommitmentRow(
    val id: String,
    val project: String?,
    val state: String?,
    val ownerEmail: String?,
    val locality: String?,
    val community: String?,
    val topic: String?,
    val theme: String?,
    val formality: String?,
    val agreedDate: LocalDate?,
    val dueDate: LocalDate?,
    val fulfilledDate: LocalDate?,
    val cancelledDate: LocalDate?,
    val deadlineStatus: String?,
    val amount: BigDecimal?,
    val currency: String?,
    val executed: BigDecimal?
)

// una reprogramación: la fecha anterior, la nueva y el día (en Lima) en que se registró
data class RescheduleRow(
    val commitment: String,
    val previous: LocalDate?,
    val newDate: LocalDate?,
    val created: LocalDate
)

// los umbrales de escalamiento de un proyecto (escalation_first_days y escalation_second_days, 7 y 15 por defecto)
data class Escalation(
    val first: Long,
    val second: Long
)

// los KPI-COM (dashboards.md §4.4) como funciones puras, con los conjuntos de estados de §3.2 (Commitments)
object CommitmentKpis {
    const val OVERDUE = "VENCIDO"
    const val DUE_SOON = "POR VENCER"
    const val FORMAL = "FORMAL"
    const val OTHERS = "Otros"
    const val TOP = 10
    const val DEFAULT_CURRENCY = "PEN"

    // los estados de WF-D en el orden del workflow: el orden de los desgloses por estado
    val STATES = listOf("draft", "pending_approval", "approved", "in_progress", "blocked", "fulfilled", "verified", "closed", "cancelled")

    // KPI-COM-01: todos, por estado (solo los estados con compromisos)
    fun registered(rows: List<CommitmentRow>): IndicatorResult {
        val counts = rows.groupingBy { it.state ?: "sin estado" }.eachCount()
        val ordered = STATES.filter { it in counts } + counts.keys.filter { it !in STATES }.sorted()
        return IndicatorResult(rows.size, breakdowns = listOf(Breakdown("workflow_state", ordered.map { mapOf("key" to it, "value" to counts.getValue(it)) })))
    }

    // KPI-COM-02: abiertos (todos salvo closed y cancelled)
    fun open(rows: List<CommitmentRow>): IndicatorResult = IndicatorResult(rows.count { it.state !in Commitments.CLOSED })

    // KPI-COM-03: pendientes de cumplimiento con deadline_status VENCIDO, por días de atraso con los umbrales de su proyecto:
    // «1-<primero>», «<primero+1>-<segundo>» y «más de <segundo>»
    fun overdue(
        rows: List<CommitmentRow>,
        today: LocalDate,
        escalation: (String?) -> Escalation
    ): IndicatorResult {
        val overdue = rows.filter { it.state in Commitments.PENDING && it.deadlineStatus == OVERDUE && it.dueDate != null }
        val buckets =
            overdue.map { row ->
                val days =
                    java.time.temporal.ChronoUnit.DAYS
                        .between(row.dueDate, today)
                val (first, second) = escalation(row.project).let { it.first to it.second }
                when {
                    days <= first -> "1-$first"
                    days <= second -> "${first + 1}-$second"
                    else -> "más de $second"
                }
            }
        val rowsOut =
            buckets
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedBy { bucketOrder(it.key) }
                .map { mapOf("key" to it.key, "value" to it.value) }
        return IndicatorResult(overdue.size, breakdowns = listOf(Breakdown("daysOverdue", rowsOut)))
    }

    private fun bucketOrder(key: String): Long = if (key.startsWith("más de ")) Long.MAX_VALUE else key.substringBefore('-').toLongOrNull() ?: 0

    // KPI-COM-04: pendientes de cumplimiento con deadline_status POR VENCER
    fun dueSoon(rows: List<CommitmentRow>): IndicatorResult = IndicatorResult(rows.count { it.state in Commitments.PENDING && it.deadlineStatus == DUE_SOON })

    // KPI-COM-05: cumplidos con fulfilled_date en el periodo
    fun fulfilled(
        rows: List<CommitmentRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult = IndicatorResult(fulfilledIn(rows, start, end).size)

    private fun fulfilledIn(
        rows: List<CommitmentRow>,
        start: LocalDate,
        end: LocalDate
    ): List<CommitmentRow> =
        rows.filter {
            it.state in Commitments.FULFILLED && it.fulfilledDate != null && !it.fulfilledDate.isBefore(start) &&
                !it.fulfilledDate.isAfter(end)
        }

    // KPI-COM-06: en blocked
    fun blocked(rows: List<CommitmentRow>): IndicatorResult = IndicatorResult(rows.count { it.state == "blocked" })

    // KPI-COM-07: abiertos y vencidos por responsable, proyecto, territorio (la localidad o, sin ella, la comunidad) y tema; los
    // 10 de más abiertos y «Otros». cada fila: key (el id o el correo), label, value (abiertos) y overdue (vencidos)
    fun breakdowns(
        rows: List<CommitmentRow>,
        projects: Map<String, String>,
        territories: Map<String, String>,
        topics: Map<String, String>
    ): IndicatorResult {
        val open = rows.filter { it.state !in Commitments.CLOSED }

        fun by(
            name: String,
            key: (CommitmentRow) -> String?,
            label: (String) -> String
        ): Breakdown {
            val groups = open.groupBy { key(it) ?: "" }
            val sorted =
                groups.entries
                    .map { (k, list) -> Triple(k, list.size, list.count { it.state in Commitments.PENDING && it.deadlineStatus == OVERDUE }) }
                    .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.first })
            val top =
                sorted.take(TOP).map { (k, value, overdue) ->
                    mapOf(
                        "key" to k.ifEmpty { null },
                        "label" to if (k.isEmpty()) "Sin dato" else label(k),
                        "value" to value,
                        "overdue" to overdue
                    )
                }
            val rest = sorted.drop(TOP)
            val others =
                if (rest.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        mapOf(
                            "key" to OTHERS,
                            "label" to OTHERS,
                            "value" to rest.sumOf { it.second },
                            "overdue" to rest.sumOf { it.third }
                        )
                    )
                }
            return Breakdown(name, top + others)
        }
        return IndicatorResult(
            open.size,
            breakdowns =
                listOf(
                    by("owner", { it.ownerEmail }) { it },
                    by("project", { it.project }) { projects[it] ?: "Proyecto" },
                    by("territory", { it.locality ?: it.community }) { territories[it] ?: "Territorio" },
                    by("topic", { it.topic }) { topics[it] ?: "Tema" }
                )
        )
    }

    // KPI-COM-08 (#10 de la Tabla 9.5): formales (escritos), salvo los cancelados, por tema, con agreed_date en el periodo
    fun formalByTheme(
        rows: List<CommitmentRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val formal =
            rows.filter {
                it.formality == FORMAL && it.state != "cancelled" && it.agreedDate != null && !it.agreedDate.isBefore(start) && !it.agreedDate.isAfter(end)
            }
        return IndicatorResult(formal.size, breakdowns = listOf(Breakdown("commitment_theme", Numbers.counts(formal.map { it.theme ?: "SIN TEMA" }))))
    }

    // KPI-COM-09: cumplidos en el periodo dentro de su plazo vigente ÷ cumplidos en el periodo; también frente a la fecha
    // original (la previous_due_date de su primera reprogramación)
    fun onTime(
        rows: List<CommitmentRow>,
        reschedules: List<RescheduleRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = fulfilledIn(rows, start, end)
        val original = reschedules.groupBy { it.commitment }.mapValues { (_, list) -> list.minWith(compareBy({ it.created }, { it.previous })).previous }
        val current = done.count { it.dueDate != null && !it.fulfilledDate!!.isAfter(it.dueDate) }
        val againstOriginal =
            done.count { row ->
                val date = original[row.id] ?: row.dueDate
                date != null && !row.fulfilledDate!!.isAfter(date)
            }
        return IndicatorResult(
            Numbers.percent(current, done.size),
            current,
            done.size,
            breakdowns =
                listOf(
                    Breakdown(
                        "against",
                        listOf(
                            mapOf("key" to "current", "value" to Numbers.percent(current, done.size), "numerator" to current, "denominator" to done.size),
                            mapOf(
                                "key" to "original",
                                "value" to Numbers.percent(againstOriginal, done.size),
                                "numerator" to againstOriginal,
                                "denominator" to done.size
                            )
                        )
                    )
                )
        )
    }

    // KPI-COM-10: por moneda, el monto de los comprometidos frente a su executed_amount (ASM-063). con campaña, solo se suma su
    // moneda y lo demás se cuenta como advertencia; sin campaña, value es el de PEN y cada moneda tiene su fila
    fun amounts(
        rows: List<CommitmentRow>,
        campaignCurrency: String?
    ): IndicatorResult {
        val committed = rows.filter { it.state in Commitments.COMMITTED }
        val byCurrency = committed.groupBy { it.currency ?: DEFAULT_CURRENCY }
        val shown = if (campaignCurrency == null) byCurrency else byCurrency.filterKeys { it == campaignCurrency }
        val outRows =
            shown.entries.sortedBy { it.key }.map { (currency, list) ->
                val amount = list.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)
                val executed = list.mapNotNull { it.executed }.fold(BigDecimal.ZERO, BigDecimal::add)
                mapOf("key" to currency, "value" to amount, "committed" to amount, "executed" to executed)
            }
        val other = if (campaignCurrency == null) 0 else committed.count { (it.currency ?: DEFAULT_CURRENCY) != campaignCurrency }
        val main = outRows.firstOrNull { it["key"] == (campaignCurrency ?: DEFAULT_CURRENCY) }
        return IndicatorResult(
            main?.get("value") as BigDecimal?,
            breakdowns = listOf(Breakdown("currency", outRows)),
            warnings = if (other > 0) listOf("$other compromisos en otra moneda que la de la campaña no se suman") else emptyList()
        )
    }

    // KPI-COM-11: para cada fin de mes M de los 12 que terminan en `end`, los compromisos vencidos en M (dashboards.md §4.4):
    // hoy en approved o después (o cancelados después de M), con agreed_date hasta M, con su fecha vigente en M anterior a M y
    // sin fulfilled_date hasta M. la fecha vigente en M es la new_due_date de la última reprogramación registrada hasta M o, si
    // no la hay, la original (la previous_due_date de la primera, o due_date si nunca se reprogramó)
    fun overdueSeries(
        rows: List<CommitmentRow>,
        reschedules: List<RescheduleRow>,
        end: LocalDate
    ): IndicatorResult {
        val byCommitment = reschedules.groupBy { it.commitment }.mapValues { (_, list) -> list.sortedWith(compareBy({ it.created }, { it.newDate })) }
        val months = (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }
        val series =
            months.map { month ->
                val closing = month.atEndOfMonth()
                val count =
                    rows.count { row ->
                        val alive =
                            row.state in Commitments.COMMITTED || (row.state == "cancelled" && row.cancelledDate != null && row.cancelledDate.isAfter(closing))
                        alive && row.agreedDate != null && !row.agreedDate.isAfter(closing) &&
                            dueAt(row, byCommitment[row.id].orEmpty(), closing)?.isBefore(closing) == true &&
                            (row.fulfilledDate == null || row.fulfilledDate.isAfter(closing))
                    }
                mapOf("period" to month.toString(), "value" to count)
            }
        return IndicatorResult(series.last()["value"] as Int, series = series)
    }

    fun dueAt(
        row: CommitmentRow,
        reschedules: List<RescheduleRow>,
        closing: LocalDate
    ): LocalDate? {
        val last = reschedules.lastOrNull { !it.created.isAfter(closing) }
        if (last != null) return last.newDate
        return reschedules.firstOrNull()?.previous ?: row.dueDate
    }
}
```

- [ ] **Paso 4: Correr la prueba y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `CommitmentKpisTest` 7/7.

- [ ] **Paso 5: Commit**

```bash
git add src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt src/test/kotlin/sgspe/dashboard/CommitmentKpisTest.kt
git commit -m "$(cat <<'EOF'
feat: indicadores KPI-COM como funciones puras

Registrados, abiertos, vencidos por días de atraso, por vencer, cumplidos, bloqueados, desgloses,
formales por tema, cumplimiento a tiempo frente al plazo vigente y al original, montos por moneda
y la serie mensual de vencidos reconstruida desde las reprogramaciones.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

Sin integración.

### Tarea 20: El tablero `commitments` y el filtro `owner`

`GET /api/sgspe/dashboards/commitments` con los once KPI-COM en el orden de dashboards.md §5.6, leídos como quien mira con la
lectura acotada de F2. Los compromisos se filtran por proyecto, territorio y ámbito (como las interacciones) y, en los KPI que
los nombran en su fila, por campaña y por responsable: el filtro nuevo `owner` (decisión 12 de este plan). Cada KPI declara
en `ignores` los filtros que no aplica (ruling P27); los KPI de F2 ignoran también `owner`. KPI-COM-03 cuenta los días de
atraso contra hoy en Lima, del reloj de la aplicación.

**Archivos:**
- Modificar: `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`, `DashboardData.kt`, `Kpis.kt`, `Dashboards.kt`
- Probar: `src/test/kotlin/sgspe/dashboard/CommitmentsDashboardApiTest.kt`; `DashboardDataTest`, `DashboardFiltersTest`,
  `IndicatorsTest` (existentes)

**Interfaces:**
- Consume: `CommitmentKpis` y sus filas (Tarea 19); `Records.all(max)`, `Criteria`, `ProjectParameters` (F2).
- Produce: `DashboardFilters.owner: UUID?` y el parámetro `owner`; `DashboardData.commitments(campaign, owner)`,
  `reschedules(ids)`, `campaignCurrency()`, `escalation()`, `today`; `Kpis.COMMITMENTS` = `commitments`.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/CommitmentsDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// el tablero commitments (dashboards.md §5.6, api.md §4.11): sus KPI en una llamada, los filtros que cada uno no aplica, el
// filtro owner y «no disponible» para quien no lee lo que el KPI lee
class CommitmentsDashboardApiTest : F3DomainApiTest() {
    @Test
    fun `el tablero trae los once KPI-COM con el proyecto pedido`() {
        val project = testProject()
        approvedCommitment(project)
        commitment(project)
        val body = tree(send("GET", "/api/sgspe/dashboards/commitments?project=$project", null, HttpStatus.OK))
        val codes = body["indicators"].values().map { it["code"].asString() }
        assertEquals((1..11).map { "KPI-COM-%02d".format(it) }.toSet(), codes.toSet())
        val registered = body["indicators"].values().first { it["code"].asString() == "KPI-COM-01" }
        assertEquals(2, registered["value"].asInt())
        assertEquals(2, body["indicators"].values().first { it["code"].asString() == "KPI-COM-02" }["value"].asInt())
    }

    @Test
    fun `owner filtra los KPI que lo aceptan y los demas lo declaran en ignoredFilters`() {
        val project = testProject()
        val owner = member("COMMITMENT_MANAGER", project)
        approvedCommitment(project, owner)
        approvedCommitment(project)
        val blocked = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-COM-06?project=$project&owner=${owner.id}", null, HttpStatus.OK))
        assertTrue(blocked["ignoredFilters"] == null || blocked["ignoredFilters"].isNull)
        val registered = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-COM-01?project=$project&owner=${owner.id}", null, HttpStatus.OK))
        assertEquals(listOf("owner"), registered["ignoredFilters"].values().map { it.asString() })
        assertEquals(2, registered["value"].asInt())
        val engagement = tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-ENG-01?project=$project&owner=${owner.id}", null, HttpStatus.OK))
        assertEquals(listOf("owner"), engagement["ignoredFilters"].values().map { it.asString() })
    }

    @Test
    fun `un parametro desconocido es 422`() {
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/dashboards/commitments?responsable=x", null, token).status)
    }
}
```

- [ ] **Paso 2: Compilar**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Expected: compila. Sin el tablero, en integración responde `404 DASHBOARD_NOT_FOUND`.

- [ ] **Paso 3: El filtro `owner`**

En `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`:

1. En el constructor, reemplazar `val breakdown: String?` por:

```kotlin
    val breakdown: String?,
    // F3: el responsable de un compromiso (owner_user_id), en los KPI-COM que lo aceptan (dashboards.md §4.4)
    val owner: UUID? = null
```

2. En `echo()`, reemplazar `"scope" to scope` por `"scope" to scope,` seguido de `"owner" to owner?.toString()`; en `given()`,
   reemplazar `scope?.let { "scope" }` por `scope?.let { "scope" },` seguido de `owner?.let { "owner" }`.
3. Reemplazar `COMMON` por:

```kotlin
        val COMMON =
            setOf(
                "project",
                "periodStart",
                "periodEnd",
                "administrativeUnit",
                "locality",
                "community",
                "areaOfInfluence",
                "explorationCampaign",
                "scope",
                "owner"
            )
```

4. En `parse`, pasar `uuid("owner")` a `Pending` después de `params.getFirst("breakdown")`; en `Pending`, agregar el parámetro
   `private val owner: UUID?` antes de `defaults`, y en `resolve()` pasar `owner` después de `breakdown`.

- [ ] **Paso 4: Los compromisos en `DashboardData`**

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, reemplazar los dos últimos parámetros del constructor y la llave que
abre la clase por:

```kotlin
    private val maxRows: Int = MAX_ROWS,
    private val maxRelated: Int = MAX_RELATED,
    // hoy en Lima, del reloj de la aplicación (los días de atraso de KPI-COM-03); sin él, el fin del periodo
    private val clockToday: LocalDate? = null
) {
    val today: LocalDate get() = clockToday ?: filters.periodEnd
```

y agregar antes de `suspend fun projectRecords()`:

```kotlin
    // ---- compromisos (F3) ----

    // los compromisos de los proyectos, filtrados por territorio y ámbito como las interacciones (su localidad o su comunidad,
    // dashboards.md §3.1) y, si el KPI los acepta, por campaña y por responsable
    suspend fun commitments(
        campaign: Boolean,
        owner: Boolean
    ): List<CommitmentRow> =
        cached("commitments:$campaign:$owner") {
            if (filters.projects.isEmpty()) return@cached emptyList<CommitmentRow>()
            val equal = mutableMapOf<String, String>()
            if (campaign) filters.explorationCampaign?.let { equal["exploration_campaign"] = it.toString() }
            if (owner) filters.owner?.let { equal["owner_user_id"] = it.toString() }
            val territories = engagementTerritories()
            read("commitment", equal, listOf(Criteria.anyOf("project", filters.projects)))
                .filter { c ->
                    val locality = Values.text(c.attributes["locality"])
                    val community = Values.text(c.attributes["community"])
                    territories.all { t -> (locality != null && locality in t.localities) || (community != null && community in t.communities) }
                }.map { commitmentRow(it) }
        }

    private fun commitmentRow(record: RecordResponse): CommitmentRow {
        val a = record.attributes

        fun date(name: String): LocalDate? = Values.text(a[name])?.let(LocalDate::parse)
        return CommitmentRow(
            record.id,
            Values.text(a["project"]),
            record.state,
            Values.text(a["owner_user_email"]),
            Values.text(a["locality"]),
            Values.text(a["community"]),
            Values.text(a["topic"]),
            Values.text(a["commitment_theme"]),
            Values.text(a["formality"]),
            date("agreed_date"),
            date("due_date"),
            date("fulfilled_date"),
            date("cancelled_date"),
            Values.text(a["deadline_status"]),
            Values.decimal(a["amount"]),
            Values.text(a["currency"]),
            Values.decimal(a["executed_amount"])
        )
    }

    // las reprogramaciones de esos compromisos, con el día de Lima en que se registraron
    suspend fun reschedules(commitments: Collection<String>): List<RescheduleRow> =
        readIn("commitment_reschedule", "commitment", commitments).mapNotNull { r ->
            RescheduleRow(
                Values.text(r.attributes["commitment"]) ?: return@mapNotNull null,
                Values.text(r.attributes["previous_due_date"])?.let(LocalDate::parse),
                Values.text(r.attributes["new_due_date"])?.let(LocalDate::parse),
                r.createdAt?.atZone(LIMA)?.toLocalDate() ?: return@mapNotNull null
            )
        }

    // la moneda de la campaña pedida (PEN por defecto, RNF-I18N-002); null si no se pidió una campaña
    suspend fun campaignCurrency(): String? {
        val campaign = filters.explorationCampaign ?: return null
        val record = readIn("exploration_campaign", null, listOf(campaign.toString())).firstOrNull()
        return Values.text(record?.attributes?.get("currency")) ?: CommitmentKpis.DEFAULT_CURRENCY
    }

    // los umbrales de escalamiento de cada proyecto del filtro (ProjectParameters: 7 y 15 por defecto)
    suspend fun escalation(): Map<String, Escalation> =
        projectRecords().associate {
            it.id to
                Escalation(
                    ProjectParameters.integer(it.attributes, "escalation_first_days"),
                    ProjectParameters.integer(it.attributes, "escalation_second_days")
                )
        }
```

- [ ] **Paso 5: El tablero y los KPI-COM**

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`:

1. Reemplazar desde `const val STAKEHOLDERS` hasta la línea anterior a `// los KPI-STK ignoran además community` por:

```kotlin
    const val STAKEHOLDERS = "stakeholders"
    const val ENGAGEMENT = "engagement"
    const val COMMITMENTS = "commitments"

    // los KPI de F2 no aplican explorationCampaign (dashboards.md §3.1: solo compromisos e inversión social) ni owner, el
    // responsable de un compromiso que llega en F3
    val IGNORED_IN_F2: Set<String> = setOf("explorationCampaign", "owner")

    // los filtros que un KPI-COM no aplica, según su fila de dashboards.md §4.4: todos aplican el territorio y el ámbito
    // (traducidos a localidades y comunidades, §3.1); la campaña y el responsable, solo los que los nombran
    private fun commitmentIgnores(vararg accepts: String): Set<String> = setOf("explorationCampaign", "owner") - accepts.toSet()
```

2. En `DASHBOARDS`, después de la entrada `ENGAGEMENT to listOf(...)`, poner una coma y agregar (reemplazando el cierre
   `)` del mapa):

```kotlin
            // dashboards.md §5.6: estado, desgloses, cumplimiento, montos, formales por tema e historia
            COMMITMENTS to
                listOf(
                    "KPI-COM-01",
                    "KPI-COM-02",
                    "KPI-COM-03",
                    "KPI-COM-04",
                    "KPI-COM-05",
                    "KPI-COM-06",
                    "KPI-COM-07",
                    "KPI-COM-09",
                    "KPI-COM-10",
                    "KPI-COM-08",
                    "KPI-COM-11"
                )
        )

    // lo que leen todos los KPI-COM: el compromiso, su estado y su territorio
    private val COMMITMENT = listOf("commitment.project", "commitment.locality", "commitment.community")
    private val PLAZO = COMMITMENT + listOf("commitment.deadline_status", "commitment.due_date")
```

3. En `ALL`, después del último `Kpi` de F2 (`KPI-ENG-05`), poner una coma y agregar (reemplazando el cierre `)` de la
   lista):

```kotlin
            // ---- F3: compromisos (dashboards.md §4.4); cada uno aplica los filtros de su fila y declara los demás ----
            Kpi("KPI-COM-01", "Compromisos registrados", COMMITMENTS, "compromisos", COMMITMENT, ignores = commitmentIgnores("explorationCampaign")) {
                CommitmentKpis.registered(it.commitments(campaign = true, owner = false))
            },
            Kpi("KPI-COM-02", "Compromisos abiertos", COMMITMENTS, "compromisos", COMMITMENT, ignores = commitmentIgnores("explorationCampaign")) {
                CommitmentKpis.open(it.commitments(campaign = true, owner = false))
            },
            Kpi(
                "KPI-COM-03",
                "Compromisos vencidos",
                COMMITMENTS,
                "compromisos",
                PLAZO + listOf("commitment.owner_user_id", "project.escalation_first_days", "project.escalation_second_days"),
                ignores = commitmentIgnores("explorationCampaign", "owner")
            ) { data ->
                val escalation = data.escalation()
                CommitmentKpis.overdue(data.commitments(campaign = true, owner = true), data.today) { escalation[it] ?: Escalation(7, 15) }
            },
            Kpi(
                "KPI-COM-04",
                "Compromisos por vencer",
                COMMITMENTS,
                "compromisos",
                PLAZO + "commitment.owner_user_id",
                ignores = commitmentIgnores("explorationCampaign", "owner")
            ) { CommitmentKpis.dueSoon(it.commitments(campaign = true, owner = true)) },
            Kpi(
                "KPI-COM-05",
                "Compromisos cumplidos en el periodo",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + "commitment.fulfilled_date",
                ignores = commitmentIgnores("explorationCampaign")
            ) { CommitmentKpis.fulfilled(it.commitments(campaign = true, owner = false), it.filters.periodStart, it.filters.periodEnd) },
            Kpi(
                "KPI-COM-06",
                "Compromisos bloqueados",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + "commitment.owner_user_id",
                ignores = commitmentIgnores("owner")
            ) {
                CommitmentKpis.blocked(it.commitments(campaign = false, owner = true))
            },
            Kpi(
                "KPI-COM-07",
                "Desgloses de compromisos",
                COMMITMENTS,
                "compromisos",
                PLAZO + listOf("commitment.owner_user_email", "commitment.topic", "project.code", "locality.name", "community.name", "topic.name"),
                ignores = commitmentIgnores()
            ) { data ->
                val rows = data.commitments(campaign = false, owner = false)
                CommitmentKpis.breakdowns(
                    rows,
                    data.names("project", rows.mapNotNull { it.project }, "code"),
                    data.names("locality", rows.mapNotNull { it.locality }) + data.names("community", rows.mapNotNull { it.community }),
                    data.names("topic", rows.mapNotNull { it.topic })
                )
            },
            Kpi(
                "KPI-COM-08",
                "Compromisos formales por tema",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + listOf("commitment.formality", "commitment.commitment_theme", "commitment.agreed_date"),
                ignores = commitmentIgnores()
            ) { CommitmentKpis.formalByTheme(it.commitments(campaign = false, owner = false), it.filters.periodStart, it.filters.periodEnd) },
            Kpi(
                "KPI-COM-09",
                "Compromisos cumplidos a tiempo",
                COMMITMENTS,
                "%",
                COMMITMENT +
                    listOf(
                        "commitment.fulfilled_date",
                        "commitment.due_date",
                        "commitment.owner_user_id",
                        "commitment_reschedule.previous_due_date",
                        "commitment_reschedule.new_due_date",
                        "commitment_reschedule.commitment"
                    ),
                ignores = commitmentIgnores("owner")
            ) { data ->
                val rows = data.commitments(campaign = false, owner = true)
                CommitmentKpis.onTime(rows, data.reschedules(rows.map { it.id }), data.filters.periodStart, data.filters.periodEnd)
            },
            Kpi(
                "KPI-COM-10",
                "Monto comprometido frente a ejecutado",
                COMMITMENTS,
                "monto",
                COMMITMENT + listOf("commitment.amount", "commitment.currency", "commitment.executed_amount", "exploration_campaign.currency"),
                ignores = commitmentIgnores("explorationCampaign")
            ) { CommitmentKpis.amounts(it.commitments(campaign = true, owner = false), it.campaignCurrency()) },
            Kpi(
                "KPI-COM-11",
                "Compromisos vencidos al cierre de cada mes",
                COMMITMENTS,
                "compromisos",
                COMMITMENT +
                    listOf(
                        "commitment.agreed_date",
                        "commitment.due_date",
                        "commitment.fulfilled_date",
                        "commitment.cancelled_date",
                        "commitment_reschedule.previous_due_date",
                        "commitment_reschedule.new_due_date",
                        "commitment_reschedule.commitment"
                    ),
                ignores = commitmentIgnores()
            ) { data ->
                val rows = data.commitments(campaign = false, owner = false)
                CommitmentKpis.overdueSeries(rows, data.reschedules(rows.map { it.id }), data.filters.periodEnd)
            }
        )
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, en `dashboard` y en `indicator`, construir `DashboardData` con
`clockToday = LocalDate.now(clock.withZone(LIMA))`:

```kotlin
        val data = DashboardData(records, filters, readable::canRead, clockToday = LocalDate.now(clock.withZone(LIMA)))
```

```kotlin
        val value = evaluate(kpi, DashboardData(records, filters, readable::canRead, clockToday = LocalDate.now(clock.withZone(LIMA))), readable)
```

- [ ] **Paso 6: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `DashboardDataTest`, `DashboardFiltersTest`, `IndicatorsTest` y las de KPI de F2 siguen en
verde.

- [ ] **Paso 7: Commit**

```bash
git add src/main/kotlin/sgspe/dashboard/DashboardFilters.kt src/main/kotlin/sgspe/dashboard/DashboardData.kt \
  src/main/kotlin/sgspe/dashboard/Kpis.kt src/main/kotlin/sgspe/dashboard/Dashboards.kt \
  src/test/kotlin/sgspe/dashboard/CommitmentsDashboardApiTest.kt
git commit -m "$(cat <<'EOF'
feat: tablero de compromisos con KPI-COM-01 a KPI-COM-11

Los compromisos se leen como quien mira, acotados por proyecto, territorio y ámbito y, en los KPI
que lo aceptan, por campaña y por el filtro nuevo owner; cada KPI declara los filtros que no
aplica.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `CommitmentsDashboardApiTest` (3) y `DashboardsApiTest` (los KPI de F2 devuelven
`owner` en `ignoredFilters` cuando se pide).

### Tarea 21: La exportación `commitment-matrix`

`POST /api/sgspe/exports/commitment-matrix` (api.md §4.13, RF-COM-001, RQ-REP-022): la matriz de DOC-01 §9.3.1.2 leída como
quien exporta, con el nivel de divulgación aplicado antes de escribir y su fila de `export_log` antes de entregar el archivo.
INTERNO: hojas «Matriz» y «Entregas», con los solicitantes por nombre si se leen, el responsable y los campos restringidos que
quien exporta lee. EXTERNO: sin responsable ni campos restringidos, con los solicitantes por código `GI-` y la lista de
verificación. PUBLICO: conteos por estado y tema y por tipo de recurso, sobre la grilla completa del catálogo con
`SmallCells` (ASM-088). Nunca el documento de identidad (CON-014). Los proyectos de una exportación los resuelve ahora
`ExportProjects`, que comparten `stakeholder-register` y `commitment-matrix` (un duplicado menos de la revisión final de 2.2).

**Archivos:**
- Crear: `src/main/kotlin/sgspe/privacy/ExportProjects.kt`, `MatrixSheets.kt`, `CommitmentMatrix.kt`
- Modificar: `src/main/kotlin/sgspe/privacy/StakeholderRegister.kt`, `ExportController.kt`
- Probar: `src/test/kotlin/sgspe/privacy/MatrixSheetsTest.kt`, `CommitmentMatrixApiTest.kt`; `StakeholderRegisterApiTest`
  (existente)

**Interfaces:**
- Consume: `ExportLog`, `ExportFile`, `Xlsx`, `XlsxSheet`, `SmallCells`, `RegisterSheets.code`, `Disclosure`,
  `NOT_AVAILABLE` (F2); `ModelCatalog.states` (Tarea 2). `privacy` solo depende de `common`.
- Produce: `ExportProjects.resolve(user, requested): List<UUID>?`, `assigned`, `requireAssigned`; `MatrixLine`,
  `MatrixDelivery`, `MatrixSheets.matrix`, `deliveries`, `aggregates`, `REQUIRES`, `RESTRICTED`; `CommitmentMatrix.export`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/privacy/MatrixSheetsTest.kt`:

```kotlin
package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.RuleException

// las hojas de commitment-matrix por nivel de divulgación (deliverables.md §9, §10, RQ-REP-022), sin base
class MatrixSheetsTest {
    private val all = MatrixSheets.REQUIRES.toSet()
    private val line =
        MatrixLine(
            "11111111-2222-3333-4444-555555555555",
            "in_progress",
            mapOf(
                "project" to "p1",
                "code" to "C-1",
                "title" to "Agua",
                "beneficiaries_detail" to "detalle restringido",
                "owner_user_email" to "responsable@sgspe.test",
                "amount" to "100"
            ),
            listOf("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee" to "Asociación Ejemplo"),
            listOf("Primer hito (2026-10-01)"),
            listOf("2026-09-01 → 2026-10-01"),
            2
        )

    private fun cells(sheet: XlsxSheet): List<List<Any?>> = sheet.rows.toList()

    @Test
    fun `INTERNO nombra a los solicitantes y trae al responsable y lo restringido que se lee`() {
        val sheet = MatrixSheets.matrix(listOf(line), mapOf("p1" to "PRY-1"), all, Disclosure.INTERNO)
        val row = cells(sheet).single()
        assertTrue("Asociación Ejemplo" in row)
        assertTrue("detalle restringido" in row)
        assertEquals("responsable@sgspe.test", row.last())
        assertTrue("Responsable" in sheet.header)
        assertFalse(sheet.header.any { it.contains("identidad", ignoreCase = true) })
    }

    @Test
    fun `EXTERNO cita por codigo GI y no trae responsable ni lo restringido`() {
        val readable = all - MatrixSheets.RESTRICTED
        val sheet = MatrixSheets.matrix(listOf(line), mapOf("p1" to "PRY-1"), readable, Disclosure.EXTERNO)
        val row = cells(sheet).single()
        assertTrue("GI-AAAAAAAABBBB" in row, row.toString())
        assertFalse("Asociación Ejemplo" in row)
        assertFalse("detalle restringido" in row)
        assertFalse("responsable@sgspe.test" in row)
        assertFalse("Responsable" in sheet.header)
    }

    @Test
    fun `un campo que no se lee sale no disponible`() {
        val sheet = MatrixSheets.matrix(listOf(line), emptyMap(), all - "commitment.amount", Disclosure.INTERNO)
        assertTrue(NOT_AVAILABLE in cells(sheet).single())
    }

    @Test
    fun `las entregas en EXTERNO no traen quienes dieron la conformidad ni los receptores`() {
        val delivery =
            MatrixDelivery(
                "C-1",
                mapOf("conformity_given" to true, "conformity_by" to "Junta", "recipients" to "Padres"),
                listOf("ACTA DE ENTREGA", "FOTO", "FOTO")
            )
        val internal = cells(MatrixSheets.deliveries(listOf(delivery), all, Disclosure.INTERNO)).single()
        assertTrue("Junta" in internal && "Padres" in internal)
        val external = cells(MatrixSheets.deliveries(listOf(delivery), all - MatrixSheets.RESTRICTED, Disclosure.EXTERNO)).single()
        assertFalse("Junta" in external || "Padres" in external)
        assertEquals(listOf(1, 2, 0), external.takeLast(3))
    }

    @Test
    fun `PUBLICO cuenta sobre la grilla completa con la marca reservado y un catalogo vacio es un 409`() {
        val states = listOf("draft", "approved")
        val lines = List(6) { line } + List(2) { MatrixLine("x", "draft", mapOf("commitment_theme" to "AMBIENTAL"), emptyList(), emptyList(), emptyList(), 0) }
        val sheets = MatrixSheets.aggregates(lines, all, states, listOf("SOCIAL", "AMBIENTAL"), listOf("MATERIAL", "NO MATERIAL"))
        val byState = cells(sheets.first())
        // 3 estados de la grilla (con «no disponible») por 4 temas (con SIN TEMA y «no disponible»)
        assertEquals(12, byState.size)
        assertTrue(byState.all { it[2] == SmallCells.HIDDEN || (it[2] as Int) >= SmallCells.MIN })
        assertThrows<RuleException> { MatrixSheets.aggregates(lines, all, states, emptyList(), listOf("MATERIAL")) }
    }
}
```

Crear `src/test/kotlin/sgspe/privacy/CommitmentMatrixApiTest.kt`:

```kotlin
package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// POST /api/sgspe/exports/commitment-matrix (api.md §4.13, RQ-REP-022): la matriz por nivel, su fila de export_log, y nunca
// el documento de identidad ni, en EXTERNO, lo restringido
class CommitmentMatrixApiTest : F3DomainApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String = this.token
    ): ResultBytes {
        val result =
            client
                .post()
                .uri("/api/sgspe/exports/commitment-matrix")
                .headers { it.set("Authorization", token) }
                .bodyValue(body)
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return ResultBytes(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun texts(bytes: ByteArray): List<String> =
        XSSFWorkbook(bytes.inputStream()).use { book ->
            (0 until book.numberOfSheets).flatMap { i -> book.getSheetAt(i).flatMap { row -> row.map { it.toString() } } }
        }

    @Test
    fun `INTERNO trae la matriz y sus entregas, sin columna de documento de identidad`() {
        val project = testProject()
        approvedCommitment(project, attributes = mapOf("beneficiaries_detail" to "Detalle restringido"))
        val file = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "INTERNO"))
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = texts(file.body)
        assertTrue(cells.contains("Matriz") || cells.any { it == "Código" })
        assertFalse(cells.any { it.contains("identidad", ignoreCase = true) })
        assertTrue(cells.contains("Detalle restringido"))
    }

    @Test
    fun `EXTERNO exige la lista de verificacion y no trae lo restringido`() {
        val project = testProject()
        approvedCommitment(project, attributes = mapOf("beneficiaries_detail" to "Detalle restringido"))
        val unchecked = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "EXTERNO"))
        assertEquals(HttpStatus.BAD_REQUEST, unchecked.status)
        val file = export(mapOf("filters" to mapOf("project" to listOf(project)), "disclosureLevel" to "EXTERNO", "checklistConfirmed" to true))
        assertEquals(HttpStatus.OK, file.status, file.text)
        assertFalse(texts(file.body).contains("Detalle restringido"))
    }

    @Test
    fun `un estado fuera del catalogo es INVALID_FILTER y un proyecto sin asignacion NOT_ASSIGNED`() {
        val bad = export(mapOf("filters" to mapOf("state" to listOf("vencido")), "disclosureLevel" to "INTERNO"))
        assertEquals(HttpStatus.BAD_REQUEST, bad.status)
        val manager = userWithRole("COMMITMENT_MANAGER")
        val denied = export(mapOf("filters" to mapOf("project" to listOf(testProject())), "disclosureLevel" to "INTERNO"), manager.token)
        assertEquals(HttpStatus.FORBIDDEN, denied.status, denied.text)
    }
}
```

- [ ] **Paso 2: Correrlas y ver que fallan**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.privacy.MatrixSheetsTest'
```

Expected: FAIL en `compileTestKotlin` con `Unresolved reference 'MatrixLine'`.

- [ ] **Paso 3: Los proyectos de una exportación, compartidos**

Crear `src/main/kotlin/sgspe/privacy/ExportProjects.kt`:

```kotlin
package sgspe.privacy

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Records
import sgspe.common.Validity
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.identity.AuthenticatedUser
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los proyectos de una exportación (api.md §4.13), la misma regla para todos los tipos: los pedidos o, si no se pide ninguno,
// «mis proyectos» (las asignaciones vigentes de quien exporta, hoy en Lima); el ADMIN, todos (null). con un solo proyecto
// pedido, quien no es ADMIN debe estar asignado a él (alcance opción A, security.md §9.2): si no, 403 NOT_ASSIGNED sobre
// filters.project. privacy solo depende de common (architecture.md §5.3): lee project_assignment con Records y aplica la
// vigencia de common. desde F3 lo comparten stakeholder-register y commitment-matrix
@Component
class ExportProjects(
    private val records: Records,
    private val clock: Clock
) {
    suspend fun resolve(
        user: AuthenticatedUser,
        requested: List<UUID>
    ): List<UUID>? =
        when {
            requested.isNotEmpty() -> requested.also { requireAssigned(user, it) }
            user.isAdmin -> null
            else -> assigned(user)
        }

    // sin READ sobre project_assignment no tiene asignaciones que leer
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

    // se comprueba antes de leer nada; ProjectScopeGuard lo vuelve a exigir al escribir la fila de export_log
    suspend fun requireAssigned(
        user: AuthenticatedUser,
        requested: List<UUID>
    ) {
        val single = requested.singleOrNull() ?: return
        if (user.isAdmin || single in assigned(user)) return
        throw Finding(
            "NOT_ASSIGNED",
            "Exportar un proyecto exige una asignación vigente a él",
            "filters.project",
            status = HttpStatus.FORBIDDEN
        ).toCodedException()
    }

    companion object {
        const val ASSIGNMENT = "project_assignment"
    }
}
```

En `src/main/kotlin/sgspe/privacy/StakeholderRegister.kt`: agregar el parámetro `private val scope: ExportProjects` al
constructor, antes de `@Value(...) maxRows`; reemplazar el `when` que calcula `projects` por
`val projects: List<UUID>? = scope.resolve(user, requested)`; borrar la sección `// ---- quién y qué proyectos ----` entera
(`assignedProjects` y `requireAssigned`); y quitar los imports que quedan sin uso (`sgspe.common.LIMA`,
`sgspe.common.Validity`, `wasichai.core.common.ForbiddenException`, `wasichai.core.identity.AuthenticatedUser`,
`java.time.LocalDate`).

- [ ] **Paso 4: Las hojas y la exportación**

Crear `src/main/kotlin/sgspe/privacy/MatrixSheets.kt`:

```kotlin
package sgspe.privacy

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Values
import sgspe.common.toCodedException

// un compromiso de la matriz como lo leyó quien exporta, con lo que la matriz junta de sus hijos (DOC-01 §9.3.1.2, RF-COM-001)
class MatrixLine(
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>,
    // los solicitantes (commitment_stakeholder): su id de grupo de interés y su nombre si se puede mostrar
    val requesters: List<Pair<String, String?>>,
    val milestones: List<String>,
    val reschedules: List<String>,
    val actions: Int
)

// una entrega de la matriz, con los tipos de sus evidencias vinculadas
class MatrixDelivery(
    val commitmentCode: String,
    val attributes: Map<String, Any?>,
    val evidenceTypes: List<String>
)

// las hojas de commitment-matrix según el nivel de divulgación (deliverables.md §9 y §10): INTERNO nombra a las organizaciones y
// personas que quien exporta puede leer y trae al responsable y los campos restringidos que puede leer (beneficiaries_detail,
// recipients, conformity_by); EXTERNO cita a los solicitantes por código GI- (seudónimo, ruling P29) y omite al responsable y
// los campos restringidos; PUBLICO solo cuenta, sobre la grilla del catálogo y con SmallCells (ASM-088). en ningún nivel sale el
// documento de identidad de los solicitantes: la matriz no tiene esa columna (RQ-REP-022, CON-014)
object MatrixSheets {
    const val COMMITMENT = "commitment"
    const val DELIVERY = "delivery"
    private const val NO_THEME = "SIN TEMA"
    private const val NO_DATA = "SIN DATO"

    // los campos de commitment y de delivery que la matriz escribe: su lectura se comprueba antes (Readability)
    val COMMITMENT_FIELDS =
        listOf(
            "code",
            "title",
            "description",
            "objective",
            "resource_type",
            "quantity",
            "unit",
            "execution_time",
            "commitment_theme",
            "formality",
            "beneficiaries_count",
            "beneficiaries_female_count",
            "beneficiaries_male_count",
            "beneficiaries_detail",
            "request_medium",
            "agreed_date",
            "due_date",
            "amount",
            "currency",
            "executed_amount",
            "delay_risk_reported",
            "delay_risk_note",
            "deadline_status",
            "closed_date",
            "owner_user_email"
        )
    val DELIVERY_FIELDS = listOf("delivery_date", "place", "delivered_items", "delivered_amount", "conformity_given", "conformity_by", "recipients")

    // restringidos (security.md §7.9): solo en INTERNO y solo si quien exporta los lee
    val RESTRICTED = setOf("$COMMITMENT.beneficiaries_detail", "$DELIVERY.conformity_by", "$DELIVERY.recipients")

    val REQUIRES: List<String> =
        COMMITMENT_FIELDS.map { "$COMMITMENT.$it" } + DELIVERY_FIELDS.map { "$DELIVERY.$it" } +
            listOf("project.code", "organization.name", "person.given_names", "person.surnames", "evidence.evidence_type")

    fun matrix(
        lines: List<MatrixLine>,
        projects: Map<String, String>,
        readable: Set<String>,
        disclosure: Disclosure
    ): XlsxSheet {
        require(disclosure != Disclosure.PUBLICO) { "PUBLICO no lleva la matriz: solo agregados" }
        val internal = disclosure == Disclosure.INTERNO
        val header =
            listOf(
                "Proyecto",
                "Código",
                "Título",
                "Descripción",
                "Objetivo",
                "Tipo de recurso",
                "Cantidad",
                "Unidad",
                "Tiempo de ejecución",
                "Tema",
                "Formalidad",
                "Solicitantes",
                "Beneficiarios",
                "Beneficiarias",
                "Beneficiarios varones"
            ) + (if (internal) listOf("Detalle de beneficiarios") else emptyList()) +
                listOf(
                    "Medio de solicitud",
                    "Fecha de acuerdo",
                    "Hitos",
                    "Vencimiento",
                    "Reprogramaciones",
                    "Monto",
                    "Moneda",
                    "Ejecutado",
                    "Estado",
                    "Acciones",
                    "Riesgo de retraso",
                    "Detalle del riesgo",
                    "Situación de plazo",
                    "Cierre"
                ) + if (internal) listOf("Responsable") else emptyList()
        val rows =
            lines.asSequence().map { line ->
                val a = line.attributes

                fun text(name: String): Any = if ("$COMMITMENT.$name" in readable) Values.text(a[name]).orEmpty() else NOT_AVAILABLE

                fun number(name: String): Any = if ("$COMMITMENT.$name" in readable) Values.decimal(a[name]) ?: "" else NOT_AVAILABLE
                val requesters =
                    line.requesters.joinToString("; ") { (id, name) -> if (internal && name != null) name else RegisterSheets.code(id) }
                listOf(
                    Values
                        .text(a["project"])
                        ?.takeIf { "project.code" in readable }
                        ?.let { projects[it] }
                        .orEmpty(),
                    text("code"),
                    text("title"),
                    text("description"),
                    text("objective"),
                    text("resource_type"),
                    number("quantity"),
                    text("unit"),
                    text("execution_time"),
                    text("commitment_theme"),
                    text("formality"),
                    requesters,
                    number("beneficiaries_count"),
                    number("beneficiaries_female_count"),
                    number("beneficiaries_male_count")
                ) + (if (internal) listOf(text("beneficiaries_detail")) else emptyList()) +
                    listOf(
                        text("request_medium"),
                        text("agreed_date"),
                        line.milestones.joinToString("; "),
                        text("due_date"),
                        line.reschedules.joinToString("; "),
                        number("amount"),
                        text("currency"),
                        number("executed_amount"),
                        line.state.orEmpty(),
                        line.actions,
                        text("delay_risk_reported"),
                        text("delay_risk_note"),
                        text("deadline_status"),
                        text("closed_date")
                    ) + if (internal) listOf(text("owner_user_email")) else emptyList()
            }
        return XlsxSheet("Matriz", header, rows)
    }

    fun deliveries(
        deliveries: List<MatrixDelivery>,
        readable: Set<String>,
        disclosure: Disclosure
    ): XlsxSheet {
        val internal = disclosure == Disclosure.INTERNO
        val header =
            listOf("Compromiso", "Fecha", "Lugar", "Qué se entregó", "Monto entregado", "Conformidad") +
                (if (internal) listOf("Quiénes dieron la conformidad", "Receptores") else emptyList()) +
                listOf("Actas de entrega", "Fotos", "Otras evidencias")
        val rows =
            deliveries.asSequence().map { d ->
                val a = d.attributes

                fun text(name: String): Any = if ("$DELIVERY.$name" in readable) Values.text(a[name]).orEmpty() else NOT_AVAILABLE
                val types = d.evidenceTypes
                listOf(
                    d.commitmentCode,
                    text("delivery_date"),
                    text("place"),
                    text("delivered_items"),
                    if ("$DELIVERY.delivered_amount" in readable) Values.decimal(a["delivered_amount"]) ?: "" else NOT_AVAILABLE,
                    if ("$DELIVERY.conformity_given" in readable) (if (Values.bool(a["conformity_given"]) == true) "Sí" else "No") else NOT_AVAILABLE
                ) + (if (internal) listOf(text("conformity_by"), text("recipients")) else emptyList()) +
                    listOf(types.count { it == "ACTA DE ENTREGA" }, types.count { it == "FOTO" }, types.count { it != "ACTA DE ENTREGA" && it != "FOTO" })
            }
        return XlsxSheet("Entregas", header, rows)
    }

    // PUBLICO: compromisos por estado y tema y por tipo de recurso, sobre la grilla del catálogo (los estados de WF-D) con sus
    // ceros, y con SmallCells en cada tabla; sin totales. un catálogo vacío rechaza la salida (409 CATALOGUE_UNAVAILABLE)
    fun aggregates(
        lines: List<MatrixLine>,
        readable: Set<String>,
        states: List<String>,
        themes: List<String>,
        resourceTypes: List<String>
    ): List<XlsxSheet> {
        fun axis(
            field: String,
            known: List<String>,
            empty: String
        ): List<String> {
            if ("$COMMITMENT.$field" !in readable) return listOf(NOT_AVAILABLE)
            if (known.isEmpty()) {
                throw Finding(
                    "CATALOGUE_UNAVAILABLE",
                    "El catálogo de $field no está disponible: una salida PUBLICO no puede armar su grilla",
                    field,
                    status = HttpStatus.CONFLICT
                ).toCodedException()
            }
            return known + empty + NOT_AVAILABLE
        }

        fun value(
            line: MatrixLine,
            field: String,
            known: List<String>,
            empty: String
        ): String {
            if ("$COMMITMENT.$field" !in readable) return NOT_AVAILABLE
            val found = Values.text(line.attributes[field]) ?: return empty
            return if (found in known) found else NOT_AVAILABLE
        }
        if (states.isEmpty()) {
            throw Finding("CATALOGUE_UNAVAILABLE", "Los estados de WF-D no están disponibles", "state", status = HttpStatus.CONFLICT).toCodedException()
        }
        val themeAxis = axis("commitment_theme", themes, NO_THEME)
        val stateOf = lines.map { if (it.state in states) it.state!! else NOT_AVAILABLE }
        val themeOf = lines.map { value(it, "commitment_theme", themes, NO_THEME) }
        val counts = stateOf.zip(themeOf).groupingBy { it }.eachCount()
        val byState = SmallCells.apply((states + NOT_AVAILABLE).flatMap { s -> themeAxis.map { t -> (s to t) to (counts[s to t] ?: 0) } })
        val resourceAxis = axis("resource_type", resourceTypes, NO_DATA)
        val resourceCounts = lines.map { value(it, "resource_type", resourceTypes, NO_DATA) }.groupingBy { it }.eachCount()
        val byResource = SmallCells.apply(resourceAxis.map { it to (resourceCounts[it] ?: 0) })
        return listOf(
            XlsxSheet(
                "Por estado y tema",
                listOf("Estado", "Tema", "Compromisos"),
                byState.asSequence().map { (key, count) ->
                    listOf(key.first, key.second, count)
                }
            ),
            XlsxSheet("Por tipo de recurso", listOf("Tipo de recurso", "Compromisos"), byResource.asSequence().map { (key, count) -> listOf(key, count) })
        )
    }
}
```

Crear `src/main/kotlin/sgspe/privacy/CommitmentMatrix.kt`:

```kotlin
package sgspe.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.util.UUID

// la exportación commitment-matrix (api.md §4.13, RF-COM-001, RQ-REP-022, deliverables.md §10): la matriz de compromisos con los
// campos de DOC-01 §9.3.1.2, leída como quien exporta (un campo ilegible sale «no disponible»), con el nivel de divulgación
// aplicado antes de escribir y su fila de export_log antes de entregar el archivo. exige READ y EXPORT sobre commitment y CREATE
// sobre export_log antes de leer nada; los proyectos siguen ExportProjects. la lectura es acotada: sgspe.exports.max-rows por
// conjunto y, para las lecturas por registro (los solicitantes y las evidencias de cada entrega), MAX_RELATED registros; pasarse
// es 400 TOO_MANY_RECORDS sobre filters.project. nunca sale el documento de identidad de los solicitantes (CON-014)
@Service
class CommitmentMatrix(
    private val records: Records,
    private val permissions: Permissions,
    private val callers: Callers,
    private val metadata: MetadataService,
    private val log: ExportLog,
    private val clock: Clock,
    private val catalog: ModelCatalog,
    private val scope: ExportProjects,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    suspend fun export(body: Map<String, Any?>): ExportFile {
        val user = callers.require()
        permissions.require(user, "Exportar la matriz de compromisos", Actions.READ to COMMITMENT, EXPORT to COMMITMENT, Actions.CREATE to ExportLog.OBJECT)
        StrictBody.requireOnly(body, setOf("filters", "disclosureLevel", "checklistConfirmed"))
        val filters = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(filters, FILTERS, "filters.")
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw bad("INVALID_DISCLOSURE_LEVEL", "El nivel de divulgación debe ser INTERNO, EXTERNO o PUBLICO", "disclosureLevel")
        if (disclosure == Disclosure.EXTERNO && body["checklistConfirmed"] != true) {
            throw bad("CHECKLIST_REQUIRED", "Una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)", "checklistConfirmed")
        }
        val requested = uuids(filters["project"], "filters.project").distinct()
        val campaign = uuids(filters["explorationCampaign"], "filters.explorationCampaign").singleOrNull()
        val states = states(filters["state"])
        val projects = scope.resolve(user, requested)
        val commitments = if (projects?.isEmpty() == true) emptyList() else commitments(projects, campaign, states)
        val sheets = sheets(commitments, disclosure)
        val export = withContext(Dispatchers.IO) { ExportFile.create("matriz-de-compromisos-${disclosure.name.lowercase()}.xlsx", clock.instant()) }
        try {
            withContext(Dispatchers.IO) { export.file.outputStream().use { Xlsx.write(sheets, it) } }
            log.export(user, COMMITMENT, "XLSX", disclosure, description(projects, filters, disclosure), commitments.size, projects?.singleOrNull())
            return export
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    private suspend fun commitments(
        projects: List<UUID>?,
        campaign: UUID?,
        states: List<String>
    ): List<RecordResponse> {
        val criteria = listOfNotNull(projects?.let { Criteria.anyOf("project", it) }, states.takeIf { it.isNotEmpty() }?.let { Criteria.inState(it) })
        val equal = campaign?.let { mapOf("exploration_campaign" to it.toString()) }.orEmpty()
        return read(COMMITMENT, equal, criteria)
    }

    private suspend fun sheets(
        commitments: List<RecordResponse>,
        disclosure: Disclosure
    ): List<XlsxSheet> {
        val readability = Readability(metadata)
        val readable =
            MatrixSheets.REQUIRES
                .filter { readability.canRead(it) }
                .filter { disclosure == Disclosure.INTERNO || it !in MatrixSheets.RESTRICTED }
                .toSet()
        if (disclosure == Disclosure.PUBLICO) {
            val lines = commitments.map { MatrixLine(it.id, it.state, it.attributes, emptyList(), emptyList(), emptyList(), 0) }
            return MatrixSheets.aggregates(
                lines,
                readable,
                catalog.states(COMMITMENT),
                catalog.enumOf(COMMITMENT, "commitment_theme"),
                catalog.enumOf(COMMITMENT, "resource_type")
            )
        }
        if (commitments.size > MAX_RELATED) throw Records.tooMany(COMMITMENT, MAX_RELATED, TOO_MANY_FIELD)
        val ids = commitments.map { it.id }

        // un hijo que quien exporta no puede leer queda vacío, como un campo ilegible: la matriz sale igual
        suspend fun children(objectName: String): List<RecordResponse> = if (readability.canRead(objectName)) readIn(objectName, ids) else emptyList()
        val milestones = children("commitment_milestone").groupBy { Values.text(it.attributes["commitment"]) }
        val reschedules = children("commitment_reschedule").groupBy { Values.text(it.attributes["commitment"]) }
        val actions = children("commitment_action").groupingBy { Values.text(it.attributes["commitment"]) }.eachCount()
        val deliveries = children(MatrixSheets.DELIVERY)
        if (deliveries.size > MAX_RELATED) throw Records.tooMany(MatrixSheets.DELIVERY, MAX_RELATED, TOO_MANY_FIELD)
        val requesters =
            if (readability.canRead("stakeholder")) {
                commitments.associate { it.id to records.related(COMMITMENT, UUID.fromString(it.id), "commitment_stakeholder") }
            } else {
                emptyMap()
            }
        val names = if (disclosure == Disclosure.INTERNO) requesterNames(requesters.values.flatten(), readable) else emptyMap()
        val lines =
            commitments.map { c ->
                MatrixLine(
                    c.id,
                    c.state,
                    c.attributes,
                    requesters[c.id].orEmpty().map { it.id to names[it.id] },
                    milestones[c.id].orEmpty().map {
                        "${Values.text(
                            it.attributes["description"]
                        ).orEmpty()} (${Values.text(it.attributes["due_date"]).orEmpty()})"
                    },
                    reschedules[c.id].orEmpty().map {
                        "${Values.text(it.attributes["previous_due_date"]).orEmpty()} → ${Values.text(it.attributes["new_due_date"]).orEmpty()}"
                    },
                    actions[c.id] ?: 0
                )
            }
        val codes = commitments.associate { it.id to (Values.text(it.attributes["code"]) ?: it.id) }
        val evidenceReadable = "evidence.evidence_type" in readable
        val matrixDeliveries =
            deliveries.map { d ->
                val types =
                    if (evidenceReadable) {
                        records
                            .related(
                                MatrixSheets.DELIVERY,
                                UUID.fromString(d.id),
                                "evidence_delivery"
                            ).mapNotNull { Values.text(it.attributes["evidence_type"]) }
                    } else {
                        emptyList()
                    }
                MatrixDelivery(codes[Values.text(d.attributes["commitment"])] ?: "", d.attributes, types)
            }
        val projects = if ("project.code" in readable) readIds("project", commitments.mapNotNull { Values.text(it.attributes["project"]) }) else emptyList()
        return listOf(
            MatrixSheets.matrix(lines, projects.associate { it.id to Values.text(it.attributes["code"]).orEmpty() }, readable, disclosure),
            MatrixSheets.deliveries(matrixDeliveries, readable, disclosure)
        )
    }

    // el nombre de cada solicitante en INTERNO: la organización o la persona si quien exporta lee esos nombres; si no, null y
    // la matriz lo cita por su código GI-
    private suspend fun requesterNames(
        stakeholders: List<RecordResponse>,
        readable: Set<String>
    ): Map<String, String> {
        val organizations =
            if ("organization.name" in readable) {
                readIds("organization", stakeholders.mapNotNull { Values.text(it.attributes["organization"]) }).associate {
                    it.id to
                        Values.text(it.attributes["name"])
                }
            } else {
                emptyMap()
            }
        val surnames = "person.surnames" in readable
        val people =
            if ("person.given_names" in readable) {
                readIds("person", stakeholders.mapNotNull { Values.text(it.attributes["person"]) }).associate { p ->
                    p.id to
                        listOfNotNull(Values.text(p.attributes["given_names"]), if (surnames) Values.text(p.attributes["surnames"]) else null).joinToString(" ")
                }
            } else {
                emptyMap()
            }
        return stakeholders
            .mapNotNull { s ->
                val name =
                    Values.text(s.attributes["organization"])?.let { organizations[it] } ?: Values.text(s.attributes["person"])?.let { people[it] }
                name?.ifBlank { null }?.let { s.id to it }
            }.toMap()
    }

    // ---- lectura acotada ----

    private suspend fun read(
        objectName: String,
        equal: Map<String, String> = emptyMap(),
        criteria: List<RecordCriterion> = emptyList(),
        ids: List<UUID>? = null,
        already: Int = 0
    ): List<RecordResponse> = records.all(objectName, equal, criteria, ids, max = maxRows - already, field = TOO_MANY_FIELD)

    // los hijos de los compromisos (por su campo commitment), en tramos de CHUNK; el tope cuenta el conjunto entero
    private suspend fun readIn(
        objectName: String,
        commitments: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in commitments.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += read(objectName, criteria = listOf(Criteria.anyOf(COMMITMENT, chunk)), already = out.size)
        }
        return out
    }

    private suspend fun readIds(
        objectName: String,
        ids: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) out += read(objectName, ids = chunk, already = out.size)
        return out
    }

    // ---- el cuerpo ----

    private fun states(raw: Any?): List<String> {
        val values =
            when (raw) {
                null -> return emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        val known = catalog.states(COMMITMENT)
        return values
            .map { value ->
                val state = value as? String
                if (state == null ||
                    state !in known
                ) {
                    throw bad("INVALID_FILTER", "El filtro filters.state debe ser uno de ${known.joinToString(", ")}", "filters.state")
                }
                state
            }.distinct()
    }

    private fun description(
        projects: List<UUID>?,
        filters: Map<String, Any?>,
        disclosure: Disclosure
    ): String =
        buildString {
            append("filtros: proyectos=")
            append(projects?.joinToString(",") ?: "todos")
            filters.filterKeys { it != "project" }.toSortedMap().forEach { (key, value) -> append("; ").append(key).append('=').append(value) }
            if (disclosure == Disclosure.EXTERNO) append("; lista de verificación de EXTERNO confirmada")
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
        return values.map { value -> Records.uuid(value) ?: throw bad("INVALID_FILTER", "El filtro $field debe ser un UUID o una lista de UUID", field) }
    }

    private fun bad(
        code: String,
        message: String,
        field: String
    ) = Finding(code, message, field, status = HttpStatus.BAD_REQUEST).toCodedException()

    companion object {
        const val COMMITMENT = "commitment"
        const val EXPORT = "EXPORT"
        private const val TOO_MANY_FIELD = "filters.project"
        val FILTERS = setOf("project", "explorationCampaign", "state")

        // los compromisos y las entregas cuyos solicitantes y evidencias se leen uno por uno
        const val MAX_RELATED = 2_000
        private const val CHUNK = 500
    }
}
```

En `src/main/kotlin/sgspe/privacy/ExportController.kt`, agregar `private val matrix: CommitmentMatrix` al constructor, la
rama `"commitment-matrix" -> matrix.export(body)` después de la de `stakeholder-register`, y cambiar la última frase del
comentario de la clase por «una exportación no crea registros de negocio: no lleva Idempotency-Key (api.md §3.5)».

- [ ] **Paso 5: Correr las pruebas y el build**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Expected: `BUILD SUCCESSFUL`; `MatrixSheetsTest` 5/5; `RegisterSheetsTest`, `SmallCellsTest` y `ExportFileTest` siguen en
verde.

- [ ] **Paso 6: Commit**

```bash
git add src/main/kotlin/sgspe/privacy/ExportProjects.kt src/main/kotlin/sgspe/privacy/MatrixSheets.kt \
  src/main/kotlin/sgspe/privacy/CommitmentMatrix.kt src/main/kotlin/sgspe/privacy/StakeholderRegister.kt \
  src/main/kotlin/sgspe/privacy/ExportController.kt src/test/kotlin/sgspe/privacy/MatrixSheetsTest.kt \
  src/test/kotlin/sgspe/privacy/CommitmentMatrixApiTest.kt
git commit -m "$(cat <<'EOF'
feat: exportación de la matriz de compromisos

La matriz de DOC-01 y sus entregas por nivel de divulgación, sin el documento de identidad de los
solicitantes y con su fila de export_log; los proyectos de una exportación los resuelve una sola
pieza para los dos tipos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `CommitmentMatrixApiTest` (3) y `StakeholderRegisterApiTest` (11, por el cambio a
`ExportProjects`).

### Tarea 22: Documentos emitidos sin datos restringidos

wasichai-documents congela en cada documento emitido el registro entero y todas las columnas de cada tabla relacionada que
nombra la plantilla, sin permisos de campo, y `GET /api/documents/{id}` lo devuelve a todo lector del objeto (UPS-23,
`DocumentService.issue` de wasichai-documents 0.6.0). Por eso un tipo de documento solo se declara sobre un objeto sin campos
restringidos y sin tablas relacionadas de objetos que los tengan: la regla del plan 3.1 (`security.md` §11.4,
`test_ningun_tipo_de_documento_congela_un_campo_restringido` de `test_model.py`) y ASM-090 (Review Focus 3).
`IssuedDocumentsPrivacyTest` aplica la misma regla del lado de la aplicación, sobre `model/document-types/*.json`,
`model.json` y los `restrictedFields` de `roles.json`. `IssuedDocumentsApiTest` comprueba lo que la regla protege: un
`ANALYST`, que lee `engagement` pero no los datos de los asistentes, recibe la minuta y la lista que emite AUT-05 sin ninguno
de ellos. Como el JSON de un documento no trae el nombre técnico de su tipo, la prueba reconoce cada documento por el prefijo
de su `number` (`MR-`, `LA-`); `snapshot.platform.documentName` es la etiqueta («Minuta de reunión»). La emisión y la copia
que ve el admin ya las prueba `AutomationsApiTest` del plan 3.1, y no hay «Acta de entrega» hasta UPS-23.

**Archivos:**
- Probar: `src/test/kotlin/sgspe/privacy/IssuedDocumentsPrivacyTest.kt`, `IssuedDocumentsApiTest.kt`

**Interfaces:**
- Consume: `model/document-types/engagement.json` (`meeting-minutes` y `attendance-list`, listas de
  `{ name, label, prefix, template }`), `model/model.json` y `model/roles.json` (`restrictedFields`) del plan 3.1;
  `aut-05-meeting-minutes`; `F3ApiTest.awaitRun` y `F3DomainApiTest` (Tarea 2b).
- Produce: la regla que vigila ASM-090, del lado de la aplicación.

- [ ] **Paso 1: Escribir las pruebas**

Crear `src/test/kotlin/sgspe/privacy/IssuedDocumentsPrivacyTest.kt`:

```kotlin
package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.File

// wasichai-documents congela en cada documento emitido el registro entero y todas las columnas de cada tabla relacionada que
// nombra la plantilla, sin permisos de campo, y GET /api/documents/{id} lo devuelve a todo lector del objeto (UPS-23). por eso
// un tipo de documento solo se declara sobre un objeto sin campos restringidos de roles.json y sin tablas relacionadas de
// objetos que los tengan (security.md §11.4, ASM-090): la regla de model/test_model.py, del lado de la aplicación. lee los
// archivos versionados, como ArchitectureTest lee roles.json
class IssuedDocumentsPrivacyTest {
    private val json = JsonMapper.builder().build()
    private val model = json.readTree(File("model/model.json"))
    private val restricted: Set<String> =
        json
            .readTree(File("model/roles.json"))["restrictedFields"]
            .values()
            .map { it["object"].asString() }
            .toSet()

    private fun nodes(node: JsonNode): Sequence<JsonNode> =
        sequenceOf(node) +
            node
                .path("content")
                .values()
                .asSequence()
                .flatMap { nodes(it) }

    // el otro extremo de una relación vista desde el objeto de la plantilla
    private fun otherEnd(
        objectName: String,
        relationship: String
    ): String? {
        val rel = model["relationships"].values().firstOrNull { it["name"].asString() == relationship } ?: return null
        val source = rel["source"].asString()
        val target = rel["target"].asString()
        return if (source == objectName) target else source
    }

    private fun violations(): List<String> {
        val files = File("model/document-types").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue(files.isNotEmpty(), "no hay tipos de documento en model/document-types: la prueba pasaría en vacío")
        return files.flatMap { file ->
            val objectName = file.nameWithoutExtension
            val own = if (objectName in restricted) listOf("${file.name}: $objectName tiene campos restringidos") else emptyList()
            own +
                json.readTree(file).values().flatMap { type ->
                    nodes(type["template"])
                        .filter { it.path("type").asString() == "relatedTable" }
                        .mapNotNull { node ->
                            val other = otherEnd(objectName, node.path("attrs")["relationship"].asString())
                            if (other == null || other in restricted) {
                                "${file.name}: ${type["name"].asString()} congela la tabla de ${other ?: "una relación que no existe"}"
                            } else {
                                null
                            }
                        }.toList()
                }
        }
    }

    @Test
    fun `ningun tipo de documento congela un campo restringido`() {
        assertEquals(emptyList<String>(), violations())
    }
}
```

Crear `src/test/kotlin/sgspe/privacy/IssuedDocumentsApiTest.kt`:

```kotlin
package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// un documento emitido se lee con GET /api/documents/{id} y READ sobre el objeto, sin permisos de campo (UPS-23): la minuta y
// la lista de asistentes que AUT-05 emite al aprobar el acta llegan a un ANALYST, que no lee los datos de los asistentes, sin
// ninguno de ellos (RQ-REP-029, RQ-SEC-010, ASM-090). wasichai-documents no devuelve el nombre técnico del tipo: cada documento
// se reconoce por el prefijo de su número (MR-2026-001), y snapshot.platform.documentName es su etiqueta. AutomationsApiTest
// (plan 3.1) prueba la emisión y la copia que ve el admin
class IssuedDocumentsApiTest : F3DomainApiTest() {
    @Test
    fun `quien lee la interaccion recibe la minuta y la lista sin los datos de los asistentes`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id =
            engagement(
                testProject(),
                token = officer.token,
                attributes =
                    mapOf(
                        "scheduled_at" to "2026-10-08T15:00:00Z",
                        "held_at" to "2026-10-08T16:00:00Z",
                        "minutes" to "Se acordó mejorar el canal de riego."
                    )
            )
        participant(id, officer.token, mapOf("participant_name" to "Persona Ejemplo 7", "phone" to "900000007"))
        for (step in listOf("schedule", "register_held", "draft_minutes", "submit_minutes")) move("engagement", id, step, officer.token)
        move("engagement", id, "approve_minutes", member("SOCIAL_MANAGER").token)
        assertEquals("SUCCEEDED", awaitRun("engagement", "aut-05-meeting-minutes", id)["status"].asString())

        val documents = tree(send("GET", "/api/objects/engagement/records/$id/documents", null, HttpStatus.OK)).values()
        assertEquals(setOf("MR", "LA"), documents.map { it["number"].asString().substringBefore("-") }.toSet())
        val analyst = member("ANALYST")
        for (document in documents) {
            val body = send("GET", "/api/documents/${document["id"].asString()}", null, HttpStatus.OK, analyst.token)
            assertFalse(body.contains("Persona Ejemplo 7") || body.contains("900000007"), body)
        }
    }
}
```

- [ ] **Paso 2: Correr la prueba unitaria**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.privacy.IssuedDocumentsPrivacyTest'
```

Expected: PASS con los tipos del plan 3.1: `engagement` no tiene campos restringidos y ninguna plantilla nombra una
`relatedTable`. Si un tipo nuevo se declarara sobre un objeto con campos restringidos (como el «Acta de entrega» sobre
`delivery`) o con la tabla de `engagement_participant`, la prueba falla nombrando el archivo y el tipo: el tipo espera a
UPS-23, como dice `security.md` §11.4.

- [ ] **Paso 3: Commit**

```bash
git add src/test/kotlin/sgspe/privacy/IssuedDocumentsPrivacyTest.kt src/test/kotlin/sgspe/privacy/IssuedDocumentsApiTest.kt
git commit -m "$(cat <<'EOF'
test: los documentos emitidos no imprimen datos restringidos

Ningún tipo de documento se declara sobre un objeto con campos restringidos ni con una tabla
relacionada de uno (ASM-090, UPS-23); un lector sin los datos de los asistentes recibe la minuta y
la lista de AUT-05 sin ellos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

**Integración pendiente (coordinador):** `IssuedDocumentsApiTest` (1).

### Tarea 23: El contrato de F3 en `api.md` y el diseño como quedó

Los documentos que lee el plan 3.3 y que gobiernan las fases siguientes dicen lo que las tareas 2 a 22 construyeron: el
contrato de cada endpoint con sus códigos (P15: todo código en `api.md`), la idempotencia con el contrato nativo, los avisos
sobre wasichai-notifications, la descarga de evidencias, el filtro `owner`, la matriz de compromisos, las decisiones 1 a 12
de este plan y la trazabilidad de las pruebas. Ningún texto nuevo usa nombres de persona reales.

**Archivos:**
- Modificar: `docs/api.md`, `docs/workflows.md`, `docs/security.md`, `docs/architecture.md`, `docs/deliverables.md`,
  `docs/domain-model.md`, `docs/dashboards.md`, `docs/requirements-traceability.md`, `docs/database-model.md`,
  `docs/requirements-analysis.md`, `docs/glossary.md`, `docs/platform-fit.md`
- Probar: `tools/check_traceability.py`, `model/test_design_docs.py`

**Interfaces:**
- Consume: los nombres, rutas, códigos y formas de las tareas 2 a 22 (resumidos en «Lo que este plan entrega al plan 3.3»).
- Produce: el contrato que lee el plan 3.3.

- [ ] **Paso 1: `api.md` §2 y §3**

En la tabla de §2, reemplazar la fila «Documentos emitidos» y agregar después de ella:

```markdown
| Documentos emitidos | `GET /api/objects/{object}/records/{id}/documents`, `POST …/documents/{type}`, `GET /api/documents/{id}` | Actas, minutas, cartas y fichas de un registro (RQ-REP-029), emitidos a mano o por una automatización (en F3, la minuta y la lista de asistentes de AUT-05). La copia congelada lleva el registro entero sin permisos de campo, así que un tipo solo se declara sobre un objeto sin campos restringidos (`security.md` §11.4, UPS-23, ASM-090). El JSON no trae el nombre técnico del tipo: el prefijo de `number` (`MR-`, `LA-`) y `snapshot.platform.documentName` (la etiqueta) lo identifican | F3 |
| Archivos | `POST /api/objects/{object}/files/{field}` (subir para un alta), `GET /api/objects/{object}/records/{id}/files/{field}` (descargar) | El primer paso del alta de una evidencia (§4.9). La descarga de `evidence` la atiende SGSPE con su propia comprobación de la clasificación | F3 |
| Avisos | `GET /api/auth/me/notifications`, `…/summary`, `…/{id}/read`, `…/{id}/dismiss`, `…/{id}/snooze`, `…/read-all`, `…/stream` (SSE); `GET` y `PUT /api/auth/me/notification-preferences` | La bandeja, el contador, el flujo en vivo y las preferencias de correo de wasichai-notifications (ADR-0018, ASM-012) | F3 |
```

y en la fila «Registros», agregar al final de la columna «Uso en SGSPE»: «; el alta admite `Idempotency-Key` nativa
(WAS-ADR-0058)».

En §3.3, en la tabla de los códigos «que existen hoy, con el estado de la escritura real», agregar a la fila `400`: «`JUSTIFICATION_REQUIRED` (`origin_justification`:
un compromiso que nace de un taller o de una reunión informativa, RN-ENG-001), `DEPENDENCY_SELF` (`depends_on`)»; a la fila
`403`: «`SEGREGATION_OF_DUTIES` (quien aprueba registró el compromiso, `record`; quien verifica es su responsable,
`owner_user_id`), `FIELD_NOT_READABLE` (`delivery.conformity_by`: la regla de cumplimiento necesita leerlo), `OBJECT_NOT_READABLE`
también con `delivery`, `evidence`, `commitment_dependency`, `stakeholder`, `engagement`, `project` y `exploration_campaign`,
`ACTION_REQUIRED` también con `VERIFY` y `DOWNLOAD_RESTRICTED`»; y a la fila `409`: «`WORKSHOP_COMMITMENT_BLOCKED`
(`origin_engagement`), `DUE_DATE_RESCHEDULE_ONLY` (`due_date`), `DEPENDENCY_CYCLE` y `DEPENDENCY_GRAPH_TOO_LARGE`
(`depends_on`), `DELIVERY_FROZEN` (`commitment`), `FIELD_REQUIRED` también con los campos de la matriz de compromisos, de un
acuerdo (`signed_date`, `effective_date`, `community`) y `duplicate_of`, `HAS_DEPENDENTS` también por una relación opcional
de un objeto `requiresReason` o `appendOnly` (`commitment.agreement_clause`, `stakeholder_assessment.position`: `ON DELETE SET
NULL` cambiaría ese registro sin motivo ni auditoría), `STAKEHOLDER_REQUIRED` (`commitment_stakeholder`),
`SENIOR_APPROVAL_REQUIRED` (`origin_engagement`), `INTERNAL_APPROVAL_REQUIRED` (`project`), `SUPPORT_CAP_EXCEEDED`
(`exploration_campaign`), `DELIVERY_REQUIRED` (`delivery`), `EVIDENCE_REQUIRED` (`evidence_delivery`), `BLOCKING_DEPENDENCY`
(`commitment_dependency`), `LAND_PARCEL_REQUIRED` (`agreement_land_parcel`), `ASSEMBLY_REQUIRED` (`assembly_engagement`),
`EVIDENCE_IMMUTABLE` (el campo) y `EVIDENCE_NOT_DELETABLE`»; y en la misma fila, cambiar «(el registro tiene dependientes
obligatorios y no se borra;» por «(el registro tiene dependientes obligatorios, o uno opcional de un objeto `requiresReason` o
`appendOnly`, y no se borra;». En el párrafo que sigue a la tabla, después de «Borrar primero a los dependientes lo
resuelve.», agregar «Desde F3 cuenta también el vínculo opcional de un objeto `requiresReason` o `appendOnly`: ahí la base no
falla, pero el borrado cambiaría ese registro sin motivo ni auditoría.». Después del párrafo de las exportaciones, agregar:

```markdown
Los comandos de F3 (§4.5 y §4.9) responden además `400` `SAME_DUE_DATE` (`attributes.new_due_date`), `FIELD_REQUIRED`
(`attributes.<campo>`), `FIELD_NOT_EDITABLE` (`attributes.<campo>`), `INVALID_LINK` (`links[i].object` u `object`),
`REASON_REQUIRED` (`reason`), `INVALID_NUMBER` y `OUT_OF_RANGE` (un monto de más de 15 dígitos enteros, de más de 4 decimales
o negativo), `DUPLICATE_NOT_FOUND` y `DUPLICATE_OTHER_PROJECT` (`duplicateOf`); `403` `PERSON_REQUIRED` (una cuenta de
servicio no decide sobre un candidato) y `ROLE_REQUIRED`; y `409` `STATE_MISMATCH` (`state`). La idempotencia (§3.5) responde
`400` sobre `Idempotency-Key` (mal formada), `409` `IDEMPOTENCY_IN_FLIGHT` con `Retry-After: 1` y `422`
`IDEMPOTENCY_KEY_REUSED`, los dos con `field` `Idempotency-Key`.
```

Reemplazar §3.5 entera por:

```markdown
### 3.5 Idempotencia (`Idempotency-Key`)

Desde F3, con el contrato de la cabecera nativa de wasichai (WAS-ADR-0058, decisión D3, ASM-065):

- **Dónde.** La API genérica la honra en `POST /api/objects/{object}/records` (tabla nativa `idempotency_keys`). Los comandos
  de `/api/sgspe/**` marcados `IK` en §4 la honran con la tabla fija `sgspe.idempotency_key`
  ([database-model.md §22](database-model.md)). El portal genera una clave por acción del usuario y la repite en cada
  reintento.
- **Forma.** De 1 a 128 caracteres ASCII imprimibles; otra cosa es un `400` sobre `Idempotency-Key`. La clave vale por
  organización y usuario: la de otra persona es otra clave.
- **Primera petición.** El comando corre como sin clave (permisos, reglas, guardas, auditoría y listeners) y su respuesta se
  guarda con la clave en la misma transacción: sin comando no hay clave. Un comando que falla no deja clave: se corrige y se
  reenvía con la misma.
- **Reenvío con la misma petición** (mismo método, ruta y cuerpo, con las claves ordenadas: el orden y los espacios no
  cuentan): la respuesta guardada, byte por byte, con su estado, su `ETag` y su `Location`, y la cabecera
  `Idempotent-Replayed: true`. No corre nada, ni permisos ni guardas: devuelve a quien llama su propia respuesta.
- **La misma clave con otra petición:** `422` `IDEMPOTENCY_KEY_REUSED` sobre `Idempotency-Key`.
- **En curso.** Mientras la primera corre, otra con la misma clave recibe `409` `IDEMPOTENCY_IN_FLIGHT` con `Retry-After: 1`:
  el candado es de transacción y no espera, así que nunca retiene una conexión.
- **Plazo.** `wasichai.idempotency.ttl` (24 h por defecto), el mismo para las dos tablas. Pasado el plazo la clave no existe
  aunque la purga no haya pasado; JOB-08 borra cada día las vencidas de `sgspe.idempotency_key`.
- **Sin `IK`.** La confirmación de una importación y el alta de componentes no la honran: son idempotentes por conciliación
  (un reintento encuentra las filas `UNCHANGED` o responde `409 SAME_COORDINATES`), y envolverlos en la transacción de la
  clave pondría las cuentas de PostGIS dentro de ella (P20). Las exportaciones y las descargas no crean registros de negocio.
- **ETag.** Un comando responde `ETag` con el `updatedAt` del registro que el portal sigue editando: el compromiso en una
  reprogramación y en la aceptación de un candidato, la entrega en su alta y su corrección, la evidencia en su alta y su
  cambio, el grupo de interés en una evaluación.
```

En §3.10, reemplazar el primer bullet por:

```markdown
- Las importaciones usan `multipart/form-data` con una parte `file` y sus campos (§4.2). El tamaño máximo y el tiempo de
  proceso son configurables; corren en una carpeta temporal con control de rutas dentro de los ZIP (RQ-SEC-020, ASM-050). En
  F2, `sgspe.territory.import.*`: `max-bytes` (20 MB), `max-rows` (10 000), `timeout` (60 s, lectura y validación) y
  `max-concurrent` (4 importaciones a la vez); Spring guarda la parte de archivo en disco con su propio tope
  (`spring.webflux.multipart.max-disk-usage-per-part`, 25 MB, con `max-parts` 8), que responde igual (400 `FILE_TOO_LARGE`;
  un campo de texto de más de 1 MB o encabezados de parte de más de 10 KB, `PART_TOO_LARGE`; demasiadas partes,
  `INVALID_MULTIPART`, ambos sobre el campo `multipart`).
- Los archivos de una evidencia los sube la ruta de wasichai-files (`POST /api/objects/evidence/files/file`, parte `file`),
  con el tope y los tipos del campo `evidence.file` (`wasichai.files.max-bytes`), el tipo detectado por sus bytes y el
  SHA-256 calculado en el servidor (WAS-ADR-0061).
```

y en el bullet de las descargas, agregar al final: «Las de evidencias son siempre `attachment`, con `X-Content-Type-Options:
nosniff` y `Cache-Control: no-store`.».

- [ ] **Paso 2: `api.md` §4**

En §4.1, agregar a la tabla de códigos de `transition-checks` estas filas (todas las reglas de F3 corren en seco):

```markdown
| `FIELD_REQUIRED` | bloqueo (`field`: el primer campo de la matriz o del responsable que falta, o `signed_date`, `effective_date`, `community`, `duplicate_of`) | 409 | `submit` y las aprobaciones de un compromiso (D1, D2); la activación de un acuerdo; `mark_duplicate` de un candidato |
| `STAKEHOLDER_REQUIRED` | bloqueo (`field`: `commitment_stakeholder`) | 409 | Un compromiso sin solicitante (D1) |
| `ACTION_REQUIRED` | bloqueo | 403 | Aprobar un compromiso sin `APPROVE` (D3) o verificarlo sin `VERIFY` (D11) |
| `SEGREGATION_OF_DUTIES` | bloqueo (`field`: `record` u `owner_user_id`) | 403 | Quien aprueba registró el compromiso (D4); quien verifica es su responsable (D11) |
| `SENIOR_APPROVAL_REQUIRED` | bloqueo (`field`: `origin_engagement`) | 409 | `approve` o `approve_direct` de un compromiso que exige nivel superior (D5, RN-ENG-002) |
| `INTERNAL_APPROVAL_REQUIRED` | bloqueo (`field`: `project`) | 409 | `approve_direct` con la aprobación interna activa (D8) |
| `SUPPORT_CAP_EXCEEDED` | bloqueo, o advertencia con `policy_cap_blocks_approval` en falso o con `APPROVE_OVER_CAP` (`field`: `exploration_campaign`) | 409 | La aprobación supera el tope de la campaña (D7) |
| `CAP_OTHER_CURRENCY` | advertencia (`field`: `currency`) | — | Hay montos de la campaña en otra moneda que no se suman al tope |
| `DELIVERY_REQUIRED` | bloqueo (`field`: `delivery`) | 409 | `register_fulfillment` sin una entrega con conformidad y quiénes la dieron (D9) |
| `EVIDENCE_REQUIRED` | bloqueo (`field`: `evidence_delivery`) | 409 | A la entrega con conformidad le falta la evidencia de su tipo de recurso (D9) |
| `FIELD_NOT_READABLE` | bloqueo (`field`: `delivery.conformity_by`) | 403 | Quien consulta no lee quiénes dieron la conformidad |
| `BLOCKING_DEPENDENCY` | bloqueo (`field`: `commitment_dependency`) | 409 | Una dependencia bloqueante sigue abierta (D10) |
| `LAND_PARCEL_REQUIRED` | bloqueo (`field`: `agreement_land_parcel`) | 409 | Un permiso de uso de terreno o una servidumbre sin predios |
| `ASSEMBLY_REQUIRED` | bloqueo (`field`: `assembly_engagement`) | 409 | Una autorización de asamblea sin la interacción de tipo ASAMBLEA |
```

y en la fila `COMMAND_ONLY`, agregar `accept` de WF-K a la lista.

En la leyenda de columnas del principio de §4, cambiar «`IK` (admite `Idempotency-Key`, §3.5, que se honra desde F3)» por
«`IK` (honra `Idempotency-Key`, §3.5)». En §4.2, en las filas de la confirmación de importaciones y del alta de componentes,
reemplazar «`IK` (desde F3)» por «—». En §4.3, en la fila del registro de evaluaciones, reemplazar «`IK` (desde F3), `IM?`,
`CR`» por «`IK`, `IM?`, `CR`».

En §4.5, reemplazar la tabla por:

```markdown
| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/commitments/{id}/reschedules` | Reprograma: crea `commitment_reschedule` con la fecha anterior y la nueva y cambia `due_date`, en una transacción, solo en ejecución | CREATE sobre `commitment_reschedule`, UPDATE sobre `commitment` | `{ attributes: { new_due_date, reschedule_reason, agreed_with_stakeholder } }` → `201 { reschedule, commitment }`; fuera de `approved`, `in_progress` o `blocked`, `409 STATE_MISMATCH`; la misma fecha, `400 SAME_DUE_DATE` | `IK`, `IM`, `CR` | F3 |
| `POST` | `/api/sgspe/commitments/{id}/deliveries` | Solo captura datos: crea la entrega, la vincula con sus evidencias (`evidence_delivery`) y recalcula `executed_amount`, en una transacción; no cambia el estado. El «Acta de entrega» emitida (AUT-03) espera a UPS-23: el acta firmada es una evidencia ACTA DE ENTREGA | CREATE sobre `delivery`, UPDATE sobre `commitment` y, si vincula, sobre `delivery` y las `evidence` | `{ attributes, geometries?, evidence?: [<id de evidence>] }` → `201 { delivery, commitment }`; fuera de ejecución, `409 STATE_MISMATCH`; `delivered_amount` fuera de cotas, `400 OUT_OF_RANGE` | `IK`, `CR` | F3 |
| `PATCH` | `/api/sgspe/deliveries/{id}` | Corrige los datos de una entrega y recalcula el ejecutado de su compromiso; con el compromiso en `fulfilled`, `verified` o `closed`, `409 DELIVERY_FROZEN`: se corrige después de `reopen_execution` | UPDATE sobre `delivery` y sobre su compromiso | `{ attributes, geometries? }` → `200 { delivery, commitment }` | `IM`, `CR` | F3 |
| `POST` | `/api/sgspe/commitment-candidates` | Alta humana de un candidato a compromiso (`ai_generated` en falso) | CREATE sobre `commitment_candidate` | `{ attributes }` (sin `ai_generated`, `confidence`, `ai_interaction`, `created_commitment` ni `duplicate_of`) → `201` registro | `IK` | F3 |
| `POST` | `/api/sgspe/commitment-candidates/{id}/accept` | Acepta: crea el compromiso en `draft` con los valores que la persona revisó (el proyecto y, si no se manda, la interacción de origen salen del candidato), lo guarda en `created_commitment` y lleva el candidato a `accepted` | UPDATE sobre `commitment_candidate`, CREATE sobre `commitment` y el rol de `accept`; una persona, nunca una cuenta de servicio (`403 PERSON_REQUIRED`) | `{ attributes }` → `201 { commitment, candidate }`; ya decidido, `409 STATE_MISMATCH` | `IK`, `IM`, `CR` | F3 (candidatos de IA en F7) |
| `POST` | `/api/sgspe/commitment-candidates/{id}/duplicate` | Marca el candidato como duplicado de un compromiso del mismo proyecto (`duplicate_of`) | UPDATE sobre `commitment_candidate` y el rol de `mark_duplicate`; una persona | `{ duplicateOf }` → `200 { candidate }`; otro proyecto, `400 DUPLICATE_OTHER_PROJECT` | `IM`, `CR` | F3 |
```

En §4.9, reemplazar el párrafo introductorio y la tabla por:

```markdown
Paquete `sgspe.evidence`. El archivo es el campo `evidence.file` de wasichai-files (decisión D1, ADR-0014): se sube con la
ruta del módulo y el registro, `apiOnly` e inmutable en su archivo, lo crea el comando de alta; no se borra y una corrección
es una evidencia nueva con `replaces` (RF-DOC-001). Los EXIF los lee el portal y llegan como `captured_at` y
`geometries.location`. Los documentos emitidos de un registro usan la API de wasichai (§2).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/objects/evidence/files/file` (wasichai-files) | Sube el archivo: tope, tipo detectado por sus bytes, SHA-256; queda sin adjuntar hasta el alta | CREATE sobre `evidence` | multipart `file` → `201 { id, name, contentType, size, sha256 }` | — | F3 |
| `POST` | `/api/sgspe/evidence` | Registra la evidencia con el archivo subido por la misma persona y la vincula, en una transacción | CREATE sobre `evidence`; UPDATE sobre `evidence` y cada objeto vinculado | `{ attributes: { file: <id>, evidence_type, classification, … }, geometries?, links?: [{ object, recordId }] }` (hasta 50 vínculos) → `201` registro, con `attributes.file` = el descriptor | `IK`; `CR` si un vínculo toca un objeto `requiresReason` | F3 |
| `GET` | `/api/sgspe/evidence/{id}/content` | Descarga el archivo en streaming; la ruta nativa `GET /api/objects/evidence/records/{id}/files/{field}` aplica la misma comprobación | READ sobre `evidence`; para CONFIDENCIAL y DATOS PERSONALES, o si quien llama no lee la clasificación, además `DOWNLOAD_RESTRICTED` (`403 ACTION_REQUIRED`) | → archivo `attachment` con `ETag` igual al SHA-256 | — | F3 |
| `PATCH` | `/api/sgspe/evidence/{id}` | Cambia los datos descriptivos (`title`, `classification`, `document_date`, `author`, `captured_at`, `people_identifiable`, `photo_consent_given`); nunca el archivo; solo escribe lo que cambia | UPDATE sobre `evidence`; `EDIT_ANY` si la subió otra persona; bajar una clasificación restringida exige `DOWNLOAD_RESTRICTED` (ASM-071) | `{ attributes }` → `200` registro; otro campo, `400 FIELD_NOT_EDITABLE` | `IM`; `CR` si cambia la clasificación | F3 |
| `POST` | `/api/sgspe/evidence/{id}/links` | Vincula la evidencia con un registro de un destino `evidence_<objeto>` | UPDATE sobre los dos registros; `EDIT_ANY` si la evidencia la subió otra persona | `{ object, recordId }` → `204`; un objeto que no es destino, `400 INVALID_LINK` | `CR` si el destino es `requiresReason` | F3 |
| `DELETE` | `/api/sgspe/evidence/{id}/links/{object}/{recordId}` | Desvincula; una entrega de un compromiso cumplido no pierde evidencias (`409 DELIVERY_FROZEN`) | Igual | → `204` | `CR` si el destino es `requiresReason` | F3 |
```

Reemplazar §4.10 entera por:

```markdown
### 4.10 BC-14 Notificaciones

Sin endpoints propios (decisión D2, ADR-0018): la bandeja, el resumen, leer, descartar, posponer, el flujo SSE y las
preferencias son de wasichai-notifications (§2). SGSPE publica con las fuentes `sgspe.approvals` (aprobación pendiente,
`ACTION`, clave `approval_pending:<objeto>:<id>`) y `sgspe.deadlines` (por vencer, `WARNING`; vencido y escalamientos,
`ACTION`, claves de `workflows.md`, «Avisos»), siempre a personas concretas y con un enlace `RECORD` al registro, que la
bandeja quita a quien no puede leerlo. Un aviso de una fuente no se edita ni se borra a mano, y uno `ACTION` no se descarta:
se resuelve cuando deja de ser cierto.
```

En §4.11, en las dos primeras filas de la tabla, agregar `&owner=` a los parámetros; agregar a los bullets:

```markdown
- `owner` (UUID de `owner_user_id`) es el responsable de un compromiso: lo aplican KPI-COM-03, KPI-COM-04, KPI-COM-06 y
  KPI-COM-09 (`dashboards.md` §4.4); los demás indicadores lo devuelven en `ignoredFilters`. `explorationCampaign` lo aplican
  KPI-COM-01 a KPI-COM-05 y KPI-COM-10; los demás KPI-COM y todos los de F2 lo devuelven en `ignoredFilters`.
- El tablero `commitments` (`dashboards.md` §5.6) trae KPI-COM-01 a KPI-COM-11. Desgloses: KPI-COM-01 `workflow_state`;
  KPI-COM-03 `daysOverdue` (`1-<primer umbral>`, `<primero+1>-<segundo>`, `más de <segundo>`, con los umbrales de escalamiento
  del proyecto de cada compromiso); KPI-COM-07 `owner`, `project`, `territory` y `topic`, con filas `{ key, label, value,
  overdue }`, las 10 de más abiertos y «Otros»; KPI-COM-08 `commitment_theme`; KPI-COM-09 `against` con `current` y
  `original` (`value`, `numerator`, `denominator`); KPI-COM-10 `currency` (`value` = `committed`, `executed`); KPI-COM-11
  `series` `{ period, value }` de 12 meses.
```

y en el bullet de `ignoredFilters`, reemplazar «y en F2 todos ignoran `explorationCampaign`» por «y los KPI-STK y KPI-ENG
ignoran `explorationCampaign` y `owner`».

En §4.13, después de la sección de `stakeholder-register`, agregar:

```markdown
**`commitment-matrix` (F3).**

- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`; `filters` es `{ project: string[], explorationCampaign,
  state: string[] }`, con `state` en los estados de WF-D (`400 INVALID_FILTER` sobre `filters.state`). Los proyectos siguen la
  regla de `stakeholder-register`.
- **Contenido.** INTERNO: hojas «Matriz» (los campos de DOC-01 §9.3.1.2, los solicitantes por nombre si se leen, hitos,
  reprogramaciones, acciones y el responsable) y «Entregas» (con quiénes dieron la conformidad y los receptores si se leen, y
  los conteos de actas, fotos y otras evidencias). EXTERNO: lo mismo sin el responsable ni los campos restringidos
  (`beneficiaries_detail`, `conformity_by`, `recipients`) y con los solicitantes por código `GI-`; exige `checklistConfirmed`.
  PUBLICO: hojas «Por estado y tema» y «Por tipo de recurso», sobre la grilla completa con la marca `reservado` (ASM-088).
  Nunca el documento de identidad de los solicitantes (CON-014).
- **Tope.** `sgspe.exports.max-rows` por conjunto y 2 000 compromisos o entregas cuyos solicitantes y evidencias se leen uno
  por uno; pasarse es `400 TOO_MANY_RECORDS` sobre `filters.project`.
- **Errores.** Los de `stakeholder-register`; `export_log` lleva `object_name` `commitment` y `row_count` cuenta compromisos.
```

En §5, reemplazar la fila F3 por:

```markdown
| F3 | Reprogramaciones y entregas de compromisos; `PATCH` de entregas; candidatos (alta, aceptación y duplicado); evidencias (alta, descarga, `PATCH` y vínculos); tablero `commitments`; exportación `commitment-matrix`. Los avisos, la subida de archivos y los documentos emitidos son de wasichai |
```

- [ ] **Paso 3: `workflows.md`**

En «Trabajos programados», reemplazar el bullet «**Tablas fijas con SQL propio, sin `asPlatform`:**…» por «**Tablas fijas
con SQL propio, sin `asPlatform`:** JOB-07 escribe `knowledge_chunk` y JOB-08 borra las claves vencidas de
`idempotency_key`.», en el bullet «**Registros de wasichai como la plataforma:**» cambiar «JOB-02 y JOB-03 escriben con»
por «JOB-01, JOB-02 y JOB-03 escriben con» (el bullet «**Motivo y origen:**» ya dice que la fila de auditoría lleva
`source = job:<nombre>` desde `main`), borrar la fila de JOB-06, y en la fila de JOB-01
reemplazar «avisos de plazos, a los destinatarios de "Destinatarios por rol"» por «los avisos de plazo los publica
`DeadlineNotices`, una fuente de wasichai-notifications que lee lo que este trabajo escribe».

En «Listeners», reemplazar la fila de `NotificationListener` por:

```markdown
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación (`submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D) y toda transición que sale del estado pendiente | Las mismas | El módulo wasichai-notifications (`Notifications.publish` y `resolve`), en la transacción de la transición | Sí | Un aviso mal formado se registra y la transición sigue; un fallo de la base al guardarlo revierte la transición; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien envió, así que todo rol que envía a aprobación tiene `READ` sobre ella (`security.md`) |
```

y en la frase anterior a la tabla, cambiar «los ocho de §7.5» por «los ocho de §7.5, con `ApprovalNoticeListener` en lugar de
`NotificationListener` desde la decisión D2».

Reemplazar los dos primeros párrafos de «Avisos» (desde «Los avisos salen de eventos» hasta «`database-model.md` documenta
las columnas de las tablas fijas.») por:

```markdown
Los avisos salen de eventos, nunca de las entidades (RN-NTF-001): `ApprovalNoticeListener` al enviar a aprobación y
`DeadlineNotices` (`sgspe.deadline`), una fuente de wasichai-notifications que cada hora devuelve todos los avisos de plazo
vigentes según lo que escribió JOB-01. Los guarda y los entrega el módulo (bandeja, SSE y correo, ADR-0018, ASM-012). Cada
aviso tiene una clave estable dentro de su fuente (`sgspe.approvals` o `sgspe.deadlines`): publicarla otra vez no duplica
(RF-NTF-001), y un aviso que deja de ser cierto se resuelve (el envío deja el estado pendiente; el compromiso deja de estar
vencido). Un aviso de aprobación es `ACTION`; uno por vencer, `WARNING`; uno vencido o de escalamiento, `ACTION`. Quién recibe
cada aviso lo fija «Destinatarios por rol».

En F3 llegan `approval_pending` (WF-A, WF-C y WF-D), `commitment_due_soon`, `commitment_overdue` y `task_overdue`; los de
QCS, riesgos y alertas llegan en F4 con sus objetos. La columna `notification_type` de la tabla siguiente es el prefijo de la
clave de cada aviso.
```

En la tabla de avisos, renombrar la columna `dedup_key` a «Clave», cambiar la clave de `approval_pending` por
`approval_pending:<objeto>:<id>`, la de `commitment_overdue` por `commitment_overdue:<id>:<due_date>:<nivel>` (un aviso por
nivel alcanzado, todos abiertos mientras dure el atraso) y la de `task_overdue` por `task_overdue:<id>:<due_date>:<nivel>`;
y en la columna «Origen» reemplazar «JOB-01» por «`DeadlineNotices`» en las filas de compromisos y tareas (las de QCS y
riesgos de F4 siguen nombrando a JOB-01 hasta su plan) y «`NotificationListener`» por «`ApprovalNoticeListener`» en la de
aprobación pendiente (WF-G, WF-H y `social_contribution` agregan su paso a `ApprovalNoticeListener.STEPS` en su fase) y por
«un listener de `sgspe.notification` (F4)» en las de riesgo escalado y alerta nueva. En el punto 1 de «Destinatarios por rol», cambiar «La bandeja usa `member_user_id` y el correo,
`member_user_email`.» por «El aviso va a `member_user_id`; el módulo toma el correo de la cuenta.» y «El listener lee las
asignaciones como quien escribió» por «`ApprovalNoticeListener` lee las asignaciones como quien envió». En el punto 4
agregar «La tarea de escalamiento la crea JOB-01 con la clave `escalation:commitment:<id>:<fecha del plazo>:<nivel>`.».

En WF-D, en la lista de reglas, reemplazar «**D6. Políticas de empleo y servidumbres** (RN-COM-002, RN-COM-003).» por
«**D6. Políticas de empleo y servidumbres** (RN-COM-002, RN-COM-003): no se verifica en F3, porque el modelo no guarda si un
compromiso ofrece puestos (ASM-089); la tabla la conserva para cuando exista el dato.».

En «Pruebas de trabajos, listeners y automatizaciones», reemplazar «un insert fallido de `NotificationListener` no aborta la
transacción de quien escribe» por «un aviso mal formado de `ApprovalNoticeListener` no aborta la transición»; en «Trabajos»,
agregar «las tareas de seguimiento de JOB-03 tienen las claves `stakeholder_review:<id>:<fecha>` y
`stakeholder_inactivity:<id>:<fecha de referencia>`».

- [ ] **Paso 4: `security.md`, `architecture.md`, `domain-model.md`, `deliverables.md` y `dashboards.md`**

`security.md`:
- §4, bullet de las tablas fijas: «Las tablas fijas del esquema `sgspe` (`idempotency_key` desde F3 y, en F7,
  `knowledge_chunk`) llevan `organization_id`, y el código que las lee filtra por él (RQ-AI-008, ASM-065). Los avisos los
  guarda wasichai-notifications, por organización (ADR-0018).»
- En el punto de los destinatarios de §5 (el que nombra `NotificationListener`), reemplazar «`NotificationListener` los
  resuelve como quien escribe» por «`ApprovalNoticeListener` los resuelve como quien envía» y «los escalamientos de
  `DeadlineJob` (JOB-01) los resuelve la plataforma» por «los avisos de plazo de `DeadlineNotices` los resuelve la plataforma».
- §7.10: borrar la frase de `NotificationMailJob` (JOB-06) y agregar «Las escrituras de un trabajo llevan además su origen
  en la columna `source` de la auditoría (`job:<nombre>`, WAS-ADR-0050, §13.2).».
- §13.2, en el bullet del origen del cambio, cambiar «(`job:derived-fields` para JOB-02 y `job:stakeholder-follow-up` para
  JOB-03» por «(`job:deadline` para JOB-01, `job:derived-fields` para JOB-02 y `job:stakeholder-follow-up` para JOB-03».
- En la tabla de §8.1, reemplazar la fila de `NotificationListener` por una de `ApprovalNoticeListener`: escribe en el módulo
  wasichai-notifications; no escribe registros de wasichai; lee `project_assignment` como quien envía; roles que escriben la
  fuente: los de las transiciones de envío; la matriz les da `READ` sobre `project_assignment`.
- §11.6, el bullet de los avisos: «Los avisos (bandeja y correo de wasichai-notifications) no pasan por los permisos de campo:
  su título y su cuerpo nombran el registro y el evento, nunca valores restringidos (RF-NTF-001); su enlace al registro lo
  quita la bandeja a quien no lo lee. Los documentos emitidos tampoco pasan por los permisos de campo: por eso un tipo de
  documento solo se declara sobre un objeto sin campos restringidos (§11.4, UPS-23, ASM-090).» (§11.4 lo deja escrito el
  plan 3.1).
- §14, en «Descargas», agregar: «la evidencia CONFIDENCIAL por la ruta nativa de wasichai-files también exige
  `DOWNLOAD_RESTRICTED` (`EvidenceApiTest`)».

`architecture.md`:
- §4, fila «Almacenamiento de objetos»: «Compatible con S3 (MinIO solo en desarrollo), el `FileStore` de wasichai-files | Los
  archivos de las evidencias (campo `FILE`, ADR-0014); desde F8, los entregables. En F2 las cargas de importación usan una
  carpeta temporal | F3 | ASM-013, ASM-060, RF-DOC-001, RQ-SEC-020, GAP-022 [WAS-ADR-0061]».
- §4, fila «Servidor SMTP»: «Correos del canal `email` de wasichai-notifications».
- §5.2, filas: `sgspe.common` cambia «`Idempotency-Key` con la tabla fija `idempotency_key` (ASM-065)» por
  «`Idempotency-Key` de los comandos con la tabla fija `idempotency_key` y el contrato nativo (ASM-065)»; `sgspe.deadline`:
  «`DeadlineJob`: situaciones derivadas de plazo y vigencia (revisión de riesgos en F4), tareas de escalamiento; la fuente de
  avisos de plazo `DeadlineNotices`»; `sgspe.notification`: «Destinatarios por asignación y textos de los avisos sobre
  wasichai-notifications; `ApprovalNoticeListener`»; `sgspe.evidence`: «Alta, cambio y vínculos de evidencias sobre el campo
  `FILE` de wasichai-files; la descarga con `DOWNLOAD_RESTRICTED` por las dos rutas; `EvidenceWriteGuard`».
- §5.5: el primer bullet como en `database-model.md` §2.3 (Tarea 1); el segundo, «`idempotency_key` guarda la clave y la
  respuesta de cada comando de `/api/sgspe/**` que la trae, en la transacción del comando ([API de SGSPE §3.5](api.md#35-idempotencia-idempotency-key));
  el alta genérica usa la tabla nativa de wasichai con el mismo contrato; JOB-08 borra las claves vencidas (§8.2).»
- §6, reemplazar los dos bullets de la idempotencia por: «Una creación no tiene registro raíz: la clave de idempotencia
  se protege con un candado de transacción que no espera, y una segunda petición en curso recibe `409` con `Retry-After`
  ([API de SGSPE §3.5](api.md#35-idempotencia-idempotency-key)). Un reenvío no escribe nada: recibe la respuesta guardada
  (ASM-065).»
- §8.1: en el primer bullet, «o con un sondeo continuo (JOB-05)»; en el segundo, borrar «JOB-06 solo escribe su tabla fija con
  SQL propio,». El origen `job:<nombre>` de cada trabajo ya lo dice el bullet de `TenantDirectory` desde `main`.
- §8.2: borrar la fila de JOB-06; en la de JOB-01, «`deadline_status` y `validity_status` (y `review_status` desde F4);
  escalamientos por días de atraso con su tarea; los avisos de plazo los publica `DeadlineNotices`»; agregar la fila
  «| Bucle de wasichai-notifications | wasichai-notifications | Corre las fuentes de avisos (`DeadlineNotices`), envía el
  correo con reintentos y purga los avisos resueltos | Cada 30 s (`wasichai.notifications.tick`) | F3 | [WAS-ADR-0046]
  [WAS-ADR-0060] |».
- En el diagrama del final de §8, reemplazar el nodo `N["Aviso deduplicado en la tabla notification"]` por
  `N["Aviso con clave estable en wasichai-notifications"]`.
- §12 (observabilidad), fila «Correo y entregables»: «Estado de los envíos de wasichai-notifications y de `draft_generation_status`».

`domain-model.md`:
- §7.2, las tres primeras filas de `evidence`: endpoints `POST /api/objects/evidence/files/file` (wasichai-files) y
  `POST /api/sgspe/evidence`; el hash lo calcula wasichai-files al subir; «`EvidenceWriteGuard` (`sgspe.evidence`): el archivo,
  `replaces` e `import_batch_id` no cambian y no se borra»; la descarga, «`GET /api/sgspe/evidence/{id}/content` y la ruta
  nativa, con la misma comprobación».
- §7.2, la fila de la fecha de vencimiento: «`CommitmentWriteGuard` (`DUE_DATE_RESCHEDULE_ONLY`) sobre `due_date` después de
  `approved`», y en §7.4, la frase final «`DerivedFieldsGuard` rechaza los demás cambios (RF-COM-003)» por
  «`CommitmentWriteGuard` rechaza los demás cambios con `DUE_DATE_RESCHEDULE_ONLY` (RF-COM-003)».
- §7.4, en la fila de `commitment.requires_senior_approval`, agregar «los vínculos de solicitantes no llegan a los
  listeners: los recoge la vuelta diaria de JOB-02».
- §8.2, en la fila de `sgspe.evidence`, las columnas de contenido y de motivo: «Alta, cambio y vínculos sobre el campo `FILE`
  de wasichai-files; descarga con `DOWNLOAD_RESTRICTED` por las dos rutas; `EvidenceWriteGuard`» y «El campo `FILE` no
  conoce la clasificación (UPS-07 resuelta, ADR-0014)»; en la de `sgspe.deadline`, quitar «`review_status`» de F3 («y, desde
  F4, `review_status`») y agregar «la fuente de avisos de plazo `DeadlineNotices`»; en la de `sgspe.notification`: «Destinatarios
  por `project_assignment.role_name` y la definición del workflow (ASM-070) y textos de los avisos; `ApprovalNoticeListener`;
  publica en wasichai-notifications» y «El módulo guarda, entrega y purga los avisos; SGSPE decide quién y qué (ASM-012)».
- §4.10, primer bullet: «`knowledge_chunk` (BC-18, F7) e `idempotency_key` (transversal, ASM-065) **no son Custom
  Objects**…» (sin `notification` ni `notification_delivery`, y sin ASM-012 en la lista); agregar al final del bullet «BC-14
  no tiene tablas propias desde la decisión D2: los avisos los guarda wasichai-notifications (ASM-012, ADR-0018).».
- §6.14, reemplazar la tabla por el párrafo «BC-14 no tiene objetos ni tablas propias desde la decisión D2 (ASM-012,
  ADR-0018): los avisos los guarda y los entrega el módulo wasichai-notifications, y SGSPE publica con una clave estable por
  aviso a personas que resuelve por las asignaciones (ASM-070). Requisitos: RF-NTF-001, RN-NTF-001, GAP-021.».
- §7.2, en la fila de `notification`, cambiar el objeto a «avisos (BC-14)».
- §7.5, en el párrafo anterior a la tabla, reemplazar la frase de `NotificationListener` (desde «`NotificationListener` es
  atómico» hasta el final de la oración que explica el savepoint) por «`ApprovalNoticeListener` publica en la transacción de
  la transición: un aviso mal formado se registra y la transición sigue, y un fallo de la base al guardarlo la revierte con
  todo lo demás.»; y reemplazar la fila de `NotificationListener` por «| `ApprovalNoticeListener` (`sgspe.notification`) |
  Envío a aprobación y salida del estado pendiente (WF-A, WF-C, WF-D) | Avisos de wasichai-notifications (`publish` y
  `resolve`), no registros de wasichai | READ sobre `project_assignment`, que lee como quien envía (ASM-070) | Un aviso mal
  formado se registra; un fallo de la base revierte la transición |».
- §8.3, en la lista de trabajos, borrar «el envío de avisos por SMTP (JOB-06), que solo escribe tablas fijas,».

`deliverables.md`:
- §14, en la fila F3: «Evidencias en el campo `FILE` de wasichai-files; matriz de compromisos en XLSX; documentos emitidos de
  un registro con wasichai-documents (RQ-REP-029)».
- §10, en el bullet «**Matriz de compromisos.**», agregar: «En EXTERNO no lleva al responsable ni `beneficiaries_detail`,
  `conformity_by` ni `recipients`, y cita a los solicitantes por código `GI-`; en PUBLICO solo cuenta por estado y tema y por
  tipo de recurso, con la marca `reservado` (ASM-088).»
- §11, el bullet «**Dónde.**»: «En el almacenamiento de objetos compatible con S3, nunca como bytes en la base (ASM-013,
  ADR-0014). Las evidencias usan el campo `FILE` de wasichai-files; los entregables de F8 usan el `FileStore` del mismo módulo
  o un puerto propio, que decide el plan de F8, con claves relativas y una prueba de contrato.»
- §1, el bullet de wasichai-documents: agregar «Su copia congelada lleva el registro entero sin permisos de campo, así que un
  tipo de documento solo se declara sobre un objeto sin campos restringidos (UPS-23, ASM-090): el acta de entrega espera.»

`dashboards.md`:
- §3.1, agregar la fila «| Responsable | `owner` | Por `owner_user_id` del compromiso, en KPI-COM-03, KPI-COM-04, KPI-COM-06
  y KPI-COM-09; los demás indicadores lo declaran en `ignoredFilters` | RQ-DSH-019 |».
- §4.4, en KPI-COM-03, después de «desglose por días de atraso», agregar «(`1-7`, `8-15`, `más de 15` con los umbrales por
  defecto)».

- [ ] **Paso 5: Los restos de las tablas de avisos**

La decisión D2 retira las tablas `notification` y `notification_delivery`, JOB-06 y `NotificationListener`. Además de lo de
los pasos anteriores y de la Tarea 1:

- `docs/workflows.md`: el principio 6 de la introducción, «Avisos: `ApprovalNoticeListener`, `DeadlineNotices` y, desde F4,
  los listeners de avisos de `sgspe.notification`, nunca las entidades (RN-NTF-001)»; en WF-F (revisiones de riesgo),
  «`NotificationListener` emite el aviso de riesgo escalado» por «un listener de `sgspe.notification` publica el aviso de
  riesgo escalado (F4)»; en «Trabajos programados», el primer bullet «o con un sondeo continuo (JOB-05)».
- `docs/database-model.md`: en el diagrama de §2, el nodo `N1["notification, notification_delivery"]` se borra; en el índice
  de objetos, se borran las filas de `notification` y `notification_delivery`; en la baja de un tenant (§25), el paso borra
  las filas de `sgspe.idempotency_key` y `sgspe.knowledge_chunk`, y agrega «los avisos los borra la baja de wasichai con el
  módulo».
- `docs/requirements-analysis.md`: la fila de BC-14 del mapa de contextos, «Sin tablas propias: avisos de
  wasichai-notifications (ASM-012, ADR-0018) | Destinatarios y textos de los avisos | RF-NTF-001»; CON-058, la cuarta columna
  «El módulo wasichai-notifications (UPS-11 resuelta); SGSPE decide destinatarios y textos (ASM-012)» y la quinta «Resuelta».
- `docs/glossary.md`: «Notificación», la segunda columna «aviso de wasichai-notifications (fuentes `sgspe.approvals` y
  `sgspe.deadlines`)»; «Notification», la tercera «avisos de wasichai-notifications».
- `docs/platform-fit.md`: la fila «Notificaciones en la aplicación y abstracción de correo» pasa a «wasichai» con
  «wasichai-notifications: bandeja, SSE, correo y preferencias» y WAS-ADR-0046 y WAS-ADR-0060; la de los avisos de plazo y
  aprobación nombra «`DeadlineNotices` (fuente del módulo) y `ApprovalNoticeListener`, con clave estable por aviso»; la de
  BC-14 en el mapa de contextos, «Sin tablas propias» y «`ApprovalNoticeListener`, `DeadlineNotices`» (la fila de UPS-11 de
  §4.1 la cambia la Tarea 1).
- `docs/dashboards.md` §6, punto 4: «las únicas tablas fijas son `knowledge_chunk` e `idempotency_key`».
- `docs/requirements-traceability.md`: en RN-NTF-001, la columna de módulo «BC-14 · `sgspe.notification`
  (`ApprovalNoticeListener`); `sgspe.deadline` (`DeadlineNotices`)», la de datos «avisos de wasichai-notifications» y la de API
  «`/api/auth/me/notifications` (wasichai)».

- [ ] **Paso 6: La trazabilidad**

En `docs/requirements-traceability.md`, en las filas de F3, agregar las pruebas de este plan a la columna de pruebas, después
de las que deja el plan 3.1 (por ejemplo `CommitmentWorkflowApiTest`, `AutomationsApiTest`, `FileStoreContractTest`), quitar
los «(propuesto)» de las que este plan implementa y poner el estado `Implementado` (RNF-INT-002 deja de ser
`Upstream (UPS-18)`):

| Requisito | Pruebas |
|---|---|
| RF-COM-001 | `CommitmentApprovalApiTest`, `CommitmentMatrixApiTest`, `MatrixSheetsTest` |
| RF-COM-002 | `CommitmentApprovalApiTest`, `CommitmentFulfillmentApiTest`, `CommitmentDerivationsApiTest`, `WorkflowMatrixApiTest` |
| RF-COM-003 | `RescheduleApiTest`, `CommitmentRecordRulesApiTest` |
| RF-COM-004 | `CommitmentRecordRulesApiTest`, `CommitmentFulfillmentApiTest`, `CommitmentDataTest` |
| RF-COM-008 | `DeliveryApiTest`, `CommitmentFulfillmentApiTest`, `FulfillmentTest` |
| RF-COM-010 | `CandidateApiTest`, `WorkflowMatrixApiTest` |
| RF-AGR-002, RF-AGR-003 | `AgreementGuardApiTest`, `AgreementActivationTest`, `DependentsDeleteGuardTest` |
| RF-DOC-001, RF-DOC-003 | `EvidenceApiTest`, `EvidenceRulesTest` |
| RF-WFL-001, RF-WFL-004 | `DeadlineJobApiTest`, `DeadlinesTest`, `DeadlineNoticePlanTest`, `FollowUpTasksApiTest` |
| RF-NTF-001 | `ApprovalNoticesApiTest`, `DeadlineJobApiTest`, `NoticesTest` |
| RF-INV-002 | `SupportPolicyApiTest`, `SupportCapTest` |
| RF-STK-010, RF-STK-014 | `FollowUpTasksTest`, `FollowUpTasksApiTest` |
| RNF-INT-002 | `IdempotencyApiTest`, `IdempotencyKeyTest`, `RetentionJobApiTest` |
| RQ-DSH-018 a RQ-DSH-021, RQ-DSH-003 | `CommitmentKpisTest`, `CommitmentsDashboardApiTest` (en lugar de «(propuesto)») |
| RQ-REP-022 | `CommitmentMatrixApiTest` |
| RQ-REP-029 | `AutomationsApiTest` y `test_model.py` (los deja el plan 3.1), `IssuedDocumentsApiTest`, `IssuedDocumentsPrivacyTest` |

En RF-NTF-001, cambiar la columna de módulo a «BC-14 · `sgspe.notification` (destinatarios y textos), wasichai-notifications;
`sgspe.deadline` (`DeadlineNotices`)» y la de API a «`/api/auth/me/notifications` (wasichai)»; en RF-DOC-001, la de módulo a
«BC-12 · `sgspe.evidence`, wasichai-files».

- [ ] **Paso 7: Verificar**

Run:

```bash
python3 tools/check_traceability.py && (cd model && python3 -m unittest -v test_design_docs 2>&1 | tail -3) && yarn format:check
```

Expected: sin IDs faltantes, `OK` y el formato en verde. Buscar restos: `grep -n "notification_delivery\|NotificationMailJob\|JOB-06\|/api/sgspe/notifications" docs/*.md`
solo debe encontrar menciones históricas en `docs/HISTORY.md` o en ADR-0018 («se retiran»).

- [ ] **Paso 8: Commit**

```bash
git add docs/api.md docs/workflows.md docs/security.md docs/architecture.md docs/deliverables.md docs/domain-model.md \
  docs/dashboards.md docs/requirements-traceability.md docs/database-model.md docs/requirements-analysis.md \
  docs/glossary.md docs/platform-fit.md
git commit -m "$(cat <<'EOF'
docs: el contrato de la fase 3 y el diseño como quedó

Endpoints, cuerpos y códigos de compromisos, candidatos, evidencias y la matriz; la idempotencia
con el contrato nativo; los avisos sobre wasichai-notifications; el filtro owner del tablero de
compromisos; y la trazabilidad de las pruebas de F3.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

Sin integración.

### Tarea 24: Cierre: vigía de arquitectura, `CLAUDE.md` y la corrida completa

**Archivos:**
- Modificar: `src/test/kotlin/sgspe/ArchitectureTest.kt`, `CLAUDE.md`

**Interfaces:**
- Consume: todo lo anterior.
- Produce: la rama lista para la revisión final.

- [ ] **Paso 1: `ArchitectureTest` ve las fuentes de F3**

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, renombrar la prueba `` `el escaneo ve las fuentes de F1 y de F2` `` a
`` `el escaneo ve las fuentes de F1, F2 y F3` `` y agregar, después de `assertTrue(names.containsAll(f2), "$names")`:

```kotlin
        // y una de cada paquete de F3
        val f3 = listOf("Idempotency.kt", "Evidences.kt", "CommitmentWriteGuard.kt", "AgreementWriteGuard.kt", "DeadlineJob.kt", "Notices.kt")
        assertTrue(names.containsAll(f3), "$names")
```

- [ ] **Paso 2: `CLAUDE.md`**

En «Qué hay aquí», cambiar la lista de paquetes por «`common`, `observability`, `scope`, `engagement`, `stakeholder`,
`territory`, `dashboard`, `privacy`, `prod` y, desde F3, `commitment`, `agreement`, `evidence`, `investment`, `deadline` y
`notification`». En «Dónde está cada cosa», agregar estas filas:

```markdown
| Idempotencia de los comandos (`Idempotency`, tabla `sgspe.idempotency_key`), JOB-08, `JobTasks`, `Amounts` | `src/main/kotlin/sgspe/common/`, `src/main/resources/db/sgspe/common/` |
| Compromisos: `CommitmentWriteGuard` y sus reglas, reprogramaciones, entregas, candidatos, derivados | `src/main/kotlin/sgspe/commitment/` |
| Acuerdos (`AgreementWriteGuard`) | `src/main/kotlin/sgspe/agreement/` |
| Evidencias sobre wasichai-files, descarga con `DOWNLOAD_RESTRICTED` | `src/main/kotlin/sgspe/evidence/` |
| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |
| JOB-01 y los avisos de plazo (`DeadlineNotices`) | `src/main/kotlin/sgspe/deadline/` |
| Avisos sobre wasichai-notifications (`Notices`, `ApprovalNoticeListener`) | `src/main/kotlin/sgspe/notification/` |
| Base de las pruebas de integración del plan 3.2 (sobre `F3ApiTest`: evidencias y compromisos de los comandos) y de las de `Idempotency-Key` sin el modelo de F3 | `src/test/kotlin/sgspe/F3DomainApiTest.kt`, `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt` |
```

y en «Reglas», al final del bullet «Archivos:» que deja el plan 3.1, agregar: «La descarga de un campo `FILE` con datos
restringidos pasa por SGSPE (`EvidenceContent`), e `IssuedDocumentsPrivacyTest` vigila la regla de los documentos del lado de
la aplicación.».

- [ ] **Paso 3: El build completo**

Run:

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build && (cd model && python3 -m unittest -v 2>&1 | tail -3) && python3 tools/check_traceability.py
```

Expected: `BUILD SUCCESSFUL` con todas las unitarias en verde (en la copia de trabajo de este plan, sobre el modelo del plan
3.1, fueron 356: 298 de F2 y del plan 3.1 y 58 de este plan), `OK` del modelo (186 pruebas) y la trazabilidad completa.

- [ ] **Paso 4: Commit**

```bash
git add src/test/kotlin/sgspe/ArchitectureTest.kt CLAUDE.md
git commit -m "$(cat <<'EOF'
chore: cierre del plan 3.2

ArchitectureTest ve una fuente de cada paquete de F3 y CLAUDE.md dice dónde está cada cosa nueva.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
)"
```

- [ ] **Paso 5: La corrida completa (coordinador)**

El coordinador corre la suite de integración entera contra `sgspe_sdd_test` (de una a dos horas por el túnel):

```bash
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun
```

Expected: todas las clases en verde: las 18 clases de integración de este plan (`IdempotencyApiTest`, `RetentionJobApiTest`,
`EvidenceApiTest`, `CommitmentRecordRulesApiTest`, `CommitmentApprovalApiTest`, `SupportPolicyApiTest`,
`CommitmentDerivationsApiTest`, `RescheduleApiTest`, `DeliveryApiTest`, `CommitmentFulfillmentApiTest`, `CandidateApiTest`,
`AgreementGuardApiTest`, `ApprovalNoticesApiTest`, `DeadlineJobApiTest`, `FollowUpTasksApiTest`, `CommitmentsDashboardApiTest`,
`CommitmentMatrixApiTest`, `IssuedDocumentsApiTest`), `WorkflowMatrixApiTest` sin transiciones de F3 en `DEFERRED`, y las
de 3.1 y F2. Después, la revisión final de la rama.
