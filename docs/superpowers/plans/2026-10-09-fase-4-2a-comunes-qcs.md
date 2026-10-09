# SGSPE · Fase 4, plan 4.2a (backend: piezas comunes y QCS): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Escribir el código propio de SGSPE para las quejas, consultas y solicitudes (QCS) de la Fase 4 sobre el modelo
del plan 4.1, con las piezas comunes que reutiliza el plan 4.2b: el calendario de días hábiles, el conteo de
planteamientos, los parámetros de F4, el alcance por proyecto y la regla «mismo proyecto» de los objetos de F4, la
confidencialidad por registro (`RecordReadScope`), `AttentionWriteGuard`, los plazos y sus listeners, la reclasificación,
la aceptación de solicitudes, la derivación de preguntas, JOB-01 para las QCS con sus avisos y el aviso genérico de nivel
2, el aviso de queja grave, el reporte unificado, los KPI-GRV con el tablero `attention` y la exportación
`attention-register`.

**Architecture:** Paquete nuevo `sgspe.attention` (guarda, listeners, comandos y reporte de las QCS); cambios en
`sgspe.common` (`HolidayCalendar`, `BusinessDays`, `Attention`, `AttentionCounting`, parámetros de F4, `NotReadable` y
`readAs`, que salen de `sgspe.commitment`, las concesiones de `roles.json` en `ModelCatalog`), `sgspe.scope` (alcance de
los 12 objetos de F4, `ProjectLinks` y `ProjectLinkGuard`, `ConfidentialAttentionScope`), `sgspe.evidence` (vínculos con
QCS confidenciales), `sgspe.engagement` (`referred_at` en RN-ENG-005), `sgspe.deadline` (JOB-01 por objeto y los avisos
`qcs_deadline` y `qcs_confidential_overdue`), `sgspe.notification` (`ConfidentialReaders` y el aviso de queja grave),
`sgspe.dashboard` y `sgspe.privacy`. Todo escribe por `sgspe.common.Records` con la marca `SgspeWrite`; las reglas son
`RecordWriteGuard` hechas de reglas puras que `POST /api/sgspe/transition-checks` corre en seco; los comandos honran
`Idempotency-Key` e `If-Match` como en F3; los trabajos leen como la plataforma y filtran la salida.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 0.6.0 (sin
starters nuevos: `RecordReadScope` es de `wasichai-core`), R2DBC, Apache POI 5.5.1, Jackson 3 (`tools.jackson`), JUnit 5
con `wasichai-test` (`WasichaiIntegrationTest`).

**Spec:** `docs/superpowers/specs/2026-10-09-fase-4-design.md` (vinculante): §1, §2 (decisiones 1 a 8, valores por defecto
y elecciones 1 a 10), §3.1 (piezas comunes), §3.2 (QCS), §3.3 (confidencialidad), §3.4 (días hábiles), §3.5 (plazos,
escalamiento, tareas y avisos, en lo que toca a las QCS), §3.8 (KPI-GRV y tablero `attention`), §5 y §5.1
(`attention-register`), §7 (fila 4.2a) y §8. Los documentos como los dejan los planes 4.0
(`docs/superpowers/plans/2026-10-09-fase-4-0-documentos.md`: `api.md` §3.3, §4.4, §4.6, §4.11 y §4.13; `security.md`
§9.6; `workflows.md` WF-E, «Listeners», «Avisos», «Destinatarios por rol» y «Plazos y escalamiento»;
`database-model.md` §6 y §12; `dashboards.md` §3.3, §4.5, §5.7 y §8; `deliverables.md` §10) y 4.1
(`docs/superpowers/plans/2026-10-09-fase-4-1-modelo.md`: el modelo, los workflows, `roles.json` y `F4ApiTest`). Lo que la
especificación asigna a 4.2b (riesgos, revisiones, `RiskWriteGuard`, alertas, JOB-04, incidentes, `risk-register`,
KPI-RSK y KPI-ENG-06 a 08) queda fuera.

## Global Constraints

- **Repositorio y rama.** Todo el trabajo en `~/IdeaProjects/sgspe`, en la rama `feat/fase-4-2a-qcs` creada desde `main`
  con los planes 4.0 y 4.1 integrados. El plan 4.2b parte de esta rama ya integrada.
- **Versiones.** Las de F3 y 4.1, sin cambios: wasichai `0.6.0` exacto, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`,
  Gradle `9.8.0`, ktlint `1.7.1`, Apache POI `poi-ooxml:5.5.1`. Ningún starter nuevo.
- **Idioma.** Español (es-PE) en comentarios, mensajes y `detail`; nombres técnicos en inglés (ADR-0009); constantes de la
  API en inglés UPPER_SNAKE; propiedades del cuerpo en camelCase; atributos con su nombre del modelo en snake_case; valores
  ENUM en español MAYÚSCULAS sin tildes, exactamente como el modelo (`POR VENCER`, `DATOS PERSONALES`, `CRITICA`).
- **Arquitectura.** Ningún literal de rol en `src/main/kotlin` (los roles salen de `model/roles.json` y de
  `model/workflows/*.json` por `ModelCatalog`); ningún código bajo el paquete `wasichai`; nunca `RecordStore`,
  `PhysicalTableRecordStore`, `AuditService` ni `ObjectSchemaManager`; `SgspeWrite` y `RecordService` solo en
  `sgspe.common`; `scope` y `privacy` solo dependen de `common`; los tenants solo con `Organizations`
  (`ArchitectureTest`). Ningún import sin uso: ktlint de este repositorio no los quita.
- **Lecturas y escrituras.** Nunca `asPlatform` dentro de una petición: guardas, listeners, comandos, el reporte, los
  tableros y las exportaciones leen y escriben como quien llama; solo JOB-01, JOB-02 y la fuente `DeadlineNotices` leen
  como la plataforma, con `source = job:<nombre>` y un motivo que nombra al trabajo. Los trabajos nunca aplican
  transiciones ni vinculan registros existentes. `JobTasks.ensure` nunca dentro de una transacción abierta.
- **Errores.** Todo 400, 403 o 409 de una guarda o de un comando lleva su código en `errors[0].message` y el campo en
  `errors[0].field` (`Finding.toCodedException`). Los códigos son los de `api.md` §3.3 tras el plan 4.0;
  `FIELD_REQUIRED` de `AttentionWriteGuard` es `400` (decisión 9 del plan 4.0).
- **Lecturas acotadas.** `Records.all(max)` con `400 TOO_MANY_RECORDS`, nunca un recorte silencioso; un parámetro de
  consulta desconocido es `422` (`StrictQuery`); una propiedad o un atributo desconocido de un comando es `400`
  (`StrictBody`).
- **Fechas.** En America/Lima (`sgspe.common.LIMA`, el `Clock` de la aplicación); instantes en UTC con `Z`; las pruebas que
  dependen del tiempo usan fechas lejanas (enero de 2026) o el feriado fijo del 8 de octubre de 2026.
- **Datos.** Ficticios: correos `@sgspe.test`, «Persona Ejemplo», «Asociación Ejemplo», «Proyecto Ejemplo»; ningún nombre
  real de persona en nada versionado.
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde
  `~/IdeaProjects/sgspe`; `./gradlew ktlintFormat` antes de `build`. El token nunca va a un archivo ni a la salida. Con
  poca memoria, `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Integración.** Las clases `*ApiTest` necesitan PostgreSQL (Testcontainers o el túnel): **corren en la CI del PR** y,
  si se quiere antes, las corre el coordinador, nunca el implementador ni un subagente. El implementador compila
  (`compileTestKotlin` o `build`, que compila también las de integración) y entrega la tarea marcada «integración
  pendiente» con las clases de su último paso. Con el túnel abierto:
  `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'`.
  La primera clase de una JVM aprovisiona el modelo de F4 (más de 8 minutos por el túnel); conviene correr juntas las
  clases de una tarea. `sgspe_sdd_test` no la usan dos suites a la vez.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres);
  al final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas. Push y
  PR en la Tarea 18.

### Comandos

Unitarias de una clase (sin base):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase>'
```

Build completo (ktlint, todas las unitarias y la compilación de las de integración):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

## Review Focus

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la
tarea dueña del código:

1. **Una QCS confidencial por una ruta que no es la lista.** El relacionista que no es responsable ni la recibió no debe
   verla tampoco por id, por el historial, por las transiciones ni desde el proyecto (`related`), ni cambiarla ni
   transicionarla; quien la registra sí debe verla al guardarla (riesgo 1 de la especificación). Pruebas:
   `ConfidentialAttentionApiTest` «quien la recibio la ve desde el alta, y otro relacionista no la ve por ninguna ruta» y
   «quien no la ve no la cambia ni la transiciona» (Tarea 5).
2. **Un aviso que delata una QCS confidencial.** `qcs_deadline`, la tarea vencida vinculada y el aviso de queja grave solo
   van a quien la vería; el aviso genérico del nivel 2 no lleva código, enlace ni el id en la clave. Pruebas:
   `QcsNoticesApiTest` «una queja confidencial solo avisa a quien la veria y el nivel 2 generico no lleva datos» y
   `QcsNoticePlanTest` «el aviso generico solo lleva el nombre del proyecto y su clave una huella» (Tarea 13).
3. **Un plazo de acuse que cae en feriado o en fin de semana.** Una queja recibida un lunes de octubre de 2026 vence su
   acuse el martes 13, no el lunes 12, porque el jueves 8 es feriado; una recibida un sábado cuenta desde el lunes; y el
   acuse de cinco días hábiles no nace POR VENCER. Pruebas: `BusinessDaysTest` (Tarea 1), `QcsDueDatesApiTest` «una queja
   recibida un lunes vence su acuse en el quinto dia habil, sin contar el feriado» (Tarea 8) y `DeadlinesQcsTest` «el
   acuse de cinco dias habiles no nace POR VENCER» (Tarea 12).
4. **Un reintento del portal tras perder la red.** Reclasificar dos veces la misma queja dejaría dos registros nuevos y
   el planteamiento contado dos veces. Prueba: `ReclassificationApiTest` «un reenvio con la misma clave devuelve la misma
   respuesta y no reclasifica dos veces» (Tarea 9); la aceptación y la derivación usan el mismo `Idempotency`.
5. **Una relación hacia otro proyecto.** Una queja, un compromiso que nace de ella o un riesgo vinculado a un grupo de
   interés ajeno inflarían los indicadores de otro proyecto y, con la lectura de todo el tenant (CON-041), pasarían sin
   regla. Pruebas: `ProjectLinkGuardApiTest` (Tarea 4), con la regla del tenant que no discrepa.

---
## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | `ProjectLinks` (P54) vive en `sgspe.commitment` y usa `readable`, `NotReadable` y `linkedProject` de `CommitmentWriteGuard.kt`; `ProjectLinkGuard` va en `sgspe.scope`, que solo depende de `common` | `NotReadable`, `readAs` y `Records.readable` pasan a `sgspe.common` (`ReadAs.kt`); `ProjectLinks` y `Records.linkedProject`, a `sgspe.scope` (`ProjectLinks.kt`). Los archivos que los usaban cambian solo sus imports (Tarea 4) |
| 2 | La especificación pide que `CommitmentWriteGuard` y `CandidateLinkRule` usen la misma pieza que `ProjectLinkGuard` | `ProjectLinkRule` y `CandidateLinkRule` siguen como reglas de su guarda (su orden P49 no cambia) y llaman a `sgspe.scope.ProjectLinks`; como los campos salen del catálogo filtrado por `ProjectScope.covers`, `commitment.origin_grievance` y `commitment.social_risk` se juzgan solos en cuanto `grievance` y `social_risk` tienen alcance. `ProjectLinkGuard` cubre los 12 objetos de F4 y los vínculos `social_risk_stakeholder` e `incident_stakeholder` (Tarea 4) |
| 3 | La derivación escribe `referred_at` en la pregunta como quien llama, y `api.md` §4.4 solo pide READ sobre `engagement_question`; `GRIEVANCE_MANAGER` no tiene UPDATE sobre ella en `roles.json` | El comando exige UPDATE sobre `engagement_question` y CREATE sobre el destino: hoy deriva `COMMUNITY_RELATIONS_OFFICER`, que registra las preguntas en la reunión. `api.md` §4.4 lo dice (Tarea 18). Darle UPDATE a `GRIEVANCE_MANAGER` es un cambio de `roles.json` que queda como pregunta abierta |
| 4 | `RequestClosureListener` corre como quien cierra el compromiso; si la solicitud aceptada es confidencial y quien cierra no la lee, wasichai no la muestra | El listener busca las solicitudes aceptadas como quien cierra: una que no lee no aparece y queda en `accepted` (lo registra en el log). No se usa `asPlatform` dentro de una petición. Queda como pregunta abierta: dar `READ_CONFIDENTIAL` o el responsable a quien cierra, o un paso manual del gestor de quejas |
| 5 | `close_accepted` de WF-E3 solo la aplica `RequestClosureListener`, al cerrar un compromiso que llegó a `verified` | Sigue en `DEFERRED` de `WorkflowMatrixApiTest`; `StakeholderRequestWorkflowApiTest` prueba el cierre, el motivo y `COMMAND_ONLY`. `accept` y `close_unfulfilled` dejan de diferirse: `perform` acepta por el comando y `prepare` cancela el compromiso creado (Tarea 10) |
| 6 | El comando de reclasificación elige la transición `close_reclassified_*` por el estado del original; la matriz prueba cada transición también desde un estado equivocado | `perform` reclasifica por el comando cuando el registro está en el estado de origen de la transición; fuera de él la pide por la API genérica, que responde `409` antes de las guardas, como pide el caso «estado equivocado» (Tarea 9) |
| 7 | `workflows.md` pone el aviso de queja grave como un paso de `ApprovalNoticeListener`, cuyos `STEPS` modelan envío y estado pendiente | Un listener propio, `GraveGrievanceNoticeListener` (`sgspe.notification`), con la misma fuente (`sgspe.approvals`) y la clave `approval_pending:grievance:<id>`: dispara por severidad y política, no por un envío. `workflows.md` lo nombra (Tarea 18) |
| 8 | `assess` de WF-F está entre las transiciones de comando de §3.1 | Llega con su endpoint en el plan 4.2b: una transición de comando sin su endpoint no se podría aplicar. `WorkflowMatrixApiTest` ya la difiere |
| 9 | La especificación pone el alcance de los objetos de F4 en las piezas comunes | Este plan suma los 12 objetos a `ProjectScope` (raíces y, por `social_risk`, `risk_assessment` y `mitigation_measure`), también los de riesgos y alertas, y la regla de rangos de los umbrales de riesgo en `ProjectWriteGuard` (Tareas 3 y 4) |
| 10 | La lista de feriados la pide la especificación «verificada contra El Peruano» | `application.yml` trae los 16 feriados nacionales de 2026 y de 2027 (D. Leg. 713 y sus modificatorias: 7 de junio, 23 de julio, 6 de agosto y 9 de diciembre sumados desde 2022); los días no laborables del sector público no son feriados. Quien ejecuta la Tarea 1 los contrasta con la norma vigente antes del commit |
| 11 | La reclasificación «copia los campos comunes» y el código es único por proyecto en cada objeto | El código no se copia: cada tipo numera los suyos y `attributes` trae el del registro nuevo; si falta, wasichai responde `400` por el campo requerido (Tarea 9) |
| 12 | La tabla de §3.2 pide categoría y severidad en `investigate` y `start_action`; `workflows.md` (plan 4.0) también en `start_action_after_investigation` | Las tres (Tarea 6) |
| 13 | El nivel 1 del aviso de una tarea vencida sale de su registro vinculado (`TASK_REFERENCE`) y no había fila para las QCS | La tarea vinculada a una queja, una consulta o una solicitud escala en el nivel 1 a los roles de `verify` de WF-E1, como los plazos de las QCS (Tarea 13) |
| 14 | `security.md` §9.6 pide memorizar por petición las acciones de quien llama | `RequestMemo` vive solo dentro de un comando que lo abre (`RequestMemo.within`): `ConfidentialAttentionScope` memoriza ahí y, fuera, pregunta a `Permissions` cada vez que wasichai le pide el criterio (una vez por objeto y lectura) |

## Lo que este plan toma de los planes 4.0 y 4.1

- **Documentos (4.0).** Los códigos de error de `api.md` §3.3 (`ANONYMOUS_WITH_IDENTITY`, `CONFIDENTIAL_RECEIVER_REQUIRED`,
  `CONFORMITY_GIVEN`, `INVALID_TARGET`, `ALREADY_REFERRED`, `EVIDENCE_NOT_RESTRICTED`, y `PROJECT_MISMATCH`,
  `SENIOR_APPROVAL_REQUIRED`, `STATE_MISMATCH`, `FIELD_REQUIRED` y `ACTION_REQUIRED` en sus casos de F4); los contratos de
  la derivación (§4.4), la reclasificación, la aceptación y el reporte unificado (§4.6) y de `attention-register`
  (§4.13 y `deliverables.md` §10); `security.md` §9.6; los avisos, destinatarios y plazos de `workflows.md`.
- **Modelo (4.1).** Los 72 objetos y las acciones `READ_CONFIDENTIAL` (solo `GRIEVANCE_MANAGER`), `APPROVE` en
  `grievance` (`APPROVER`) y `EXPORT`; los 8 parámetros de `project` con su `defaultValue`;
  `engagement_question.referred_at` derivado; WF-E1, WF-E2 y WF-E3 con sus roles; AUT-02; `ExactlyOneGuard.GROUPS["task"]`
  con los orígenes de F4 (este plan no lo cambia); `F4ApiTest` con `platformPatch`, `attentionAttributes`, `grievance`,
  `inquiry`, `stakeholderRequest`, `socialRisk`, `incident`, `warningRule`, `earlyWarning`; `WorkflowMatrixApiTest` sobre
  `F4ApiTest` con las transiciones de F4 en `DEFERRED`. Si un nombre de aquí y uno de los documentos difieren, mandan los
  documentos.

## Interfaces con el plan 4.2b y con el portal

Lo que este plan deja y el plan 4.2b consume (nombres exactos):

- `sgspe.common.HolidayCalendar` (bean) y `BusinessDays.addBusinessDays(start: LocalDate, n: Int, calendar)`.
- `sgspe.common.Attention` (`OBJECTS`, `READ_CONFIDENTIAL`, `APPROVE`, `RECEIVED_AT`, `RECLASSIFIED_FROM`, `FINAL_STATES`,
  `isConfidential`, `isOpen`), `Raised`, `AttentionTotals` y `AttentionCounting.counted(rows)` / `totals(rows)`: la regla
  CONTEO DE QCS de JOB-04 cuenta con `counted`, leyendo como la plataforma (confidenciales incluidas).
- `ProjectParameters.integer(project, "risk_review_days" | "risk_medium_min_score" | "risk_high_min_score" |
  "risk_critical_min_score")` con sus valores por defecto, `ProjectParameters.THRESHOLDS` y la regla de rangos de
  `ProjectWriteGuard` (el endpoint de revisiones responde `409 RISK_THRESHOLDS_INVALID` ante umbrales guardados antes de
  ella).
- `sgspe.common.NotReadable`, `readAs(objectName) { … }` y `Records.readable(objectName, id)`.
- `sgspe.scope.ProjectScope` con los 12 objetos de F4; `ProjectLinks.fieldsOf(catalog, objectName)`,
  `ProjectLinks.tenantCapable(catalog)`, `ProjectLinks.judge(...)`, `Records.linkedProject(target, id)`;
  `ProjectLinkGuard` ya juzga `social_risk`, `potential_impact`, `incident`, `media_mention`, `early_warning_signal`,
  `early_warning_rule` y `early_warning` y los vínculos `social_risk_stakeholder` e `incident_stakeholder`.
- `ModelCatalog.grants` y `ModelCatalog.rolesGranting(objectName, action)`.
- `sgspe.deadline.Deadlines.Tracked(objectName, field, dateField, states, validity, dateByState, halfFromReceipt,
  escalates)`, `Deadlines.due(tracked, state, attributes)`, `DeadlineJob` que recorre por objeto con
  `Organizations.having` (sumar `social_risk` es agregar su `Tracked` y su paso de revisión),
  `DeadlineJob.escalationKey(objectName, id, due, level)`, `DeadlineNoticePlan.qcs(...)`, `PlannedNotice.level`,
  `DeadlineNoticePlan.TASK_REFERENCE` (el plan 4.2b suma `social_risk`).
- `sgspe.notification.ConfidentialReaders` (`filter`, `fingerprint`), `NoticeTexts.qcs*`,
  `GraveGrievanceNoticeListener`; las fuentes `Notices.APPROVALS` y `Notices.DEADLINES` (el plan 4.2b suma
  `sgspe.risks` y `sgspe.warnings`).
- `Permissions.confidentialHidden(user)`; `CommandTransitionGuard.COMMANDS` con las transiciones de comando de WF-E1, WF-E2
  y WF-E3 (el plan 4.2b suma `social_risk.assess`).

Lo que el portal (plan 4.3) consume, además de `api.md`: la forma de las respuestas `201 { original, created }`
(reclasificación), `201 { request, commitment }` (aceptación), `201 { question, created }` (derivación), el reporte
`200 { totals, byType, confidentialHidden, page }` y `confidentialHidden` en el tablero `attention`, en cada KPI-GRV y en
KPI-ENG-05.

---

## Mapa de archivos

Todos bajo la raíz del repositorio. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `src/main/kotlin/sgspe/common/BusinessDays.kt` (crear), `src/main/resources/application.yml` | `CalendarProperties`, `HolidayCalendar`, `BusinessDays`; feriados de 2026 y 2027 | 1 |
| `src/main/kotlin/sgspe/common/Attention.kt` (crear) | Nombres de las QCS, `Raised`, `AttentionTotals`, `AttentionCounting` | 2 |
| `src/main/kotlin/sgspe/common/ProjectParameters.kt`, `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt` | Parámetros de F4 y sus rangos | 3 |
| `src/main/kotlin/sgspe/common/ReadAs.kt`, `src/main/kotlin/sgspe/scope/ProjectLinks.kt`, `ProjectLinkGuard.kt` (crear); `ProjectScope.kt`; `CommitmentWriteGuard.kt`, `ProjectLinkRule.kt` y los imports de nueve archivos de `commitment`, `investment` y `notification` | Alcance de F4 y «mismo proyecto»; `NotReadable` y `readAs` a `common` | 4 |
| `src/main/kotlin/sgspe/scope/ConfidentialAttentionScope.kt` (crear), `src/main/kotlin/sgspe/common/Permissions.kt` | `RecordReadScope` de las QCS confidenciales; `confidentialHidden` | 5 |
| `src/main/kotlin/sgspe/attention/AttentionRules.kt`, `AttentionWriteGuard.kt` (crear) | Las reglas de WF-E1, WF-E2 y WF-E3 | 6 |
| `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt` | `EVIDENCE_NOT_RESTRICTED` | 7 |
| `src/main/kotlin/sgspe/attention/QcsDates.kt` (crear) | `QcsDueDates`, `QcsTransitionDates`, sus listeners y el recalculador de JOB-02 | 8 |
| `src/main/kotlin/sgspe/attention/Reclassifications.kt` (crear), `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt` | Reclasificación | 9 |
| `src/main/kotlin/sgspe/attention/Acceptances.kt` (crear), `CommandTransitionGuard.kt`, `WorkflowMatrixApiTest.kt` | Aceptación y `RequestClosureListener` | 10 |
| `src/main/kotlin/sgspe/attention/Referrals.kt` (crear), `src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt` | Derivación y RN-ENG-005 | 11 |
| `src/main/kotlin/sgspe/deadline/Deadlines.kt`, `DeadlineJob.kt` | JOB-01 por objeto con quejas y consultas | 12 |
| `src/main/kotlin/sgspe/notification/ConfidentialReaders.kt` (crear), `Notices.kt`, `src/main/kotlin/sgspe/common/ModelCatalog.kt`, `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt` | `qcs_deadline`, filtro de destinatarios y `qcs_confidential_overdue` | 13 |
| `src/main/kotlin/sgspe/notification/GraveGrievanceNotices.kt` (crear) | Aviso de queja grave | 14 |
| `src/main/kotlin/sgspe/attention/AttentionReport.kt` (crear) | Reporte unificado | 15 |
| `src/main/kotlin/sgspe/dashboard/AttentionKpis.kt` (crear), `DashboardData.kt`, `Kpis.kt`, `Indicators.kt`, `Dashboards.kt` | KPI-GRV y tablero `attention` | 16 |
| `src/main/kotlin/sgspe/privacy/AttentionSheets.kt`, `AttentionRegister.kt` (crear), `ExportController.kt` | `attention-register` | 17 |
| `docs/api.md`, `docs/workflows.md`, `CLAUDE.md`, `src/test/kotlin/sgspe/ArchitectureTest.kt` | Cierre | 18 |

Las pruebas viven en `src/test/kotlin/sgspe/<paquete>/` con el nombre que da cada tarea: unitarias `*Test` (sin base) y de
integración `*ApiTest` (sobre `F4ApiTest`, en la CI del PR).

## Orden y paralelismo

Cada tarea se puede hacer en su propio worktree cuando las tareas de las que depende ya están integradas en
`feat/fase-4-2a-qcs`. Las de un mismo grupo tocan archivos distintos y no usan nada unas de otras. La integración la corre
la CI del PR (o el coordinador, una clase tras otra, porque todas usan `sgspe_sdd_test`).

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| A | 1, 2, 3 | La rama creada | Calendario, conteo y parámetros: archivos distintos de `common` y `ProjectWriteGuard` |
| B | 4, 5 | 2 (la 5 usa `Attention`) | Alcance y «mismo proyecto» frente al alcance de lectura; la 4 mueve `NotReadable` y la 5 toca `Permissions` |
| C | 6, 8 | 2, 3, 4 (las QCS ya tienen alcance); la 8 también la 1 | La guarda frente a los plazos; archivos distintos de `sgspe.attention` |
| D | 7, 9, 11, 12 | 6; la 12 también la 8 | Evidencias, reclasificación (primera edición de la matriz), derivación y JOB-01: archivos disjuntos |
| E | 10, 13 | 9 (la 10 edita la matriz y `CommandTransitionGuard` después de ella); 12 y 5 (13) | Aceptación y avisos de plazo |
| F | 14, 15, 16, 17 | 13 (la 14, por `ConfidentialReaders`); 2, 5 y 8 (15, 16 y 17) | Aviso de queja grave (`notification`), reporte (`attention`), tableros (`dashboard`) y exportación (`privacy`): archivos disjuntos |
| G | 18 | Todas | Documentos, vigía, verificación, push y PR |

Archivos que comparten tareas y por eso las ordenan: `WorkflowMatrixApiTest.kt` y `CommandTransitionGuard.kt` (9 → 10),
`Notices.kt` y `ModelCatalog.kt` (13), `Permissions.kt` (5, que leen 15, 16 y 17).

---

### Tarea 1: El calendario de días hábiles

**Files:**
- Create: `src/main/kotlin/sgspe/common/BusinessDays.kt`
- Modify: `src/main/resources/application.yml` (`sgspe.calendar.holidays`)
- Test: `src/test/kotlin/sgspe/common/BusinessDaysTest.kt`

**Interfaces:**
- Consumes: `sgspe.common.LIMA` y el `Clock` de la aplicación (`Clocks`).
- Produces: `CalendarProperties(holidays: Map<String, List<LocalDate>>)` (`@ConfigurationProperties("sgspe.calendar")`);
  `HolidayCalendar` (bean de `HolidayCalendarConfiguration`) con `configured(year: Int): Boolean`,
  `isHoliday(date: LocalDate): Boolean`, `HolidayCalendar.of(configured, missing = …)` y
  `HolidayCalendar.warning(year): String`; `object BusinessDays` con `isBusinessDay(date, calendar): Boolean` y
  `addBusinessDays(start: LocalDate, n: Int, calendar: HolidayCalendar): LocalDate`.

La especificación §3.4 fija la forma: feriados nacionales por año en `application.yml`, validados al arrancar; un año sin
lista usa lunes a viernes con una advertencia por año y proceso; el día de recepción no cuenta. Antes del commit, quien
ejecuta contrasta las dos listas con la norma vigente publicada en El Peruano (D. Leg. 713 y sus modificatorias); un
cambio en la lista cambia la cuenta de la prueba `application yml trae los feriados de 2026 y 2027`.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/common/BusinessDaysTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate

// ASM-096: días hábiles de lunes a viernes menos los feriados configurados; el día de recepción no cuenta; un año sin
// lista usa lunes a viernes y lo advierte una vez
class BusinessDaysTest {
    private fun date(text: String) = LocalDate.parse(text)

    private val calendar = HolidayCalendar.of(mapOf("2026" to listOf(date("2026-07-28"), date("2026-07-29"), date("2026-08-06"))))

    @Test
    fun `el dia de recepcion no cuenta y el fin de semana tampoco`() {
        // lunes 5 de octubre de 2026: el quinto día hábil es el lunes 12
        assertEquals(date("2026-10-12"), BusinessDays.addBusinessDays(date("2026-10-05"), 5, calendar))
        // recibida el sábado 3: cuenta desde el lunes 5, así que el quinto día hábil es el viernes 9
        assertEquals(date("2026-10-09"), BusinessDays.addBusinessDays(date("2026-10-03"), 5, calendar))
        assertEquals(date("2026-10-05"), BusinessDays.addBusinessDays(date("2026-10-05"), 0, calendar))
    }

    @Test
    fun `un feriado no es habil`() {
        // lunes 27 de julio de 2026: el 28 y el 29 son Fiestas Patrias, así que el cuarto día hábil es el martes 4 de agosto
        assertEquals(date("2026-08-04"), BusinessDays.addBusinessDays(date("2026-07-27"), 4, calendar))
        assertFalse(BusinessDays.isBusinessDay(date("2026-08-06"), calendar))
        assertTrue(BusinessDays.isBusinessDay(date("2026-08-07"), calendar))
    }

    @Test
    fun `un anio sin lista usa lunes a viernes y advierte una sola vez`() {
        val warned = mutableListOf<Int>()
        val partial = HolidayCalendar.of(mapOf("2026" to emptyList()), missing = { warned += it })
        assertEquals(date("2028-01-07"), BusinessDays.addBusinessDays(date("2027-12-31"), 5, partial))
        BusinessDays.addBusinessDays(date("2028-02-01"), 3, partial)
        assertEquals(listOf(2028), warned)
        assertEquals("sgspe.calendar: sin feriados configurados para 2028; se usan lunes a viernes", HolidayCalendar.warning(2028))
    }

    @Test
    fun `una fecha de otro anio, repetida o una clave que no es un anio impiden arrancar`() {
        assertThrows<IllegalStateException> { HolidayCalendar.of(mapOf("2026" to listOf(date("2027-01-01")))) }
        assertThrows<IllegalStateException> { HolidayCalendar.of(mapOf("2026" to listOf(date("2026-01-01"), date("2026-01-01")))) }
        assertThrows<IllegalStateException> { HolidayCalendar.of(mapOf("dos mil" to emptyList())) }
    }

    // la configuración de application.yml: 2026 y 2027 completos (16 feriados cada uno) y válidos
    @Test
    fun `application yml trae los feriados de 2026 y 2027`() {
        val sources = YamlPropertySourceLoader().load("application", ClassPathResource("application.yml"))
        val byYear =
            listOf("2026", "2027").associateWith { year ->
                (0 until 20).mapNotNull { i -> sources.firstNotNullOfOrNull { it.getProperty("sgspe.calendar.holidays.$year[$i]") }?.toString() }
            }
        assertEquals(16, byYear.getValue("2026").size, byYear.toString())
        assertEquals(16, byYear.getValue("2027").size, byYear.toString())
        val calendar = HolidayCalendar.of(byYear.mapValues { (_, dates) -> dates.map(LocalDate::parse) })
        assertFalse(BusinessDays.isBusinessDay(date("2026-04-03"), calendar))
        assertFalse(BusinessDays.isBusinessDay(date("2027-03-25"), calendar))
        assertFalse(BusinessDays.isBusinessDay(date("2026-12-09"), calendar))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.BusinessDaysTest'
```

Esperado: FAIL en la compilación de las pruebas (`Unresolved reference 'HolidayCalendar'` y `'BusinessDays'`).

- [ ] **Paso 3: El calendario y los feriados de 2026 y 2027**

Crear `src/main/kotlin/sgspe/common/BusinessDays.kt`:

```kotlin
package sgspe.common

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// los feriados nacionales por año, por despliegue y no por organización (ASM-096, architecture.md §10.2): sgspe.calendar.holidays
// en application.yml, con el año entre comillas como clave. cada año alguien de operación agrega el siguiente
@ConfigurationProperties("sgspe.calendar")
data class CalendarProperties(
    val holidays: Map<String, List<LocalDate>> = emptyMap()
)

// el calendario de feriados, validado al arrancar: cada fecha pertenece a su año y ninguna se repite; una fecha inválida impide
// arrancar. un año sin lista usa lunes a viernes y lo advierte una sola vez por año y proceso (`missing`)
class HolidayCalendar private constructor(
    private val holidays: Map<Int, Set<LocalDate>>,
    private val missing: (Int) -> Unit
) {
    private val warned = ConcurrentHashMap.newKeySet<Int>()

    fun configured(year: Int): Boolean = year in holidays

    fun isHoliday(date: LocalDate): Boolean {
        val days = holidays[date.year]
        if (days == null) {
            if (warned.add(date.year)) missing(date.year)
            return false
        }
        return date in days
    }

    companion object {
        private val log = LoggerFactory.getLogger(HolidayCalendar::class.java)

        fun warning(year: Int): String = "sgspe.calendar: sin feriados configurados para $year; se usan lunes a viernes"

        // valida la configuración: la clave es un año y cada fecha es de ese año, sin repetir
        fun of(
            configured: Map<String, List<LocalDate>>,
            missing: (Int) -> Unit = { log.warn(warning(it)) }
        ): HolidayCalendar {
            val byYear =
                configured.entries.associate { (key, dates) ->
                    val year = key.trim().toIntOrNull() ?: error("sgspe.calendar.holidays: la clave '$key' no es un año")
                    val wrong = dates.filter { it.year != year }
                    check(wrong.isEmpty()) { "sgspe.calendar.holidays.$year: $wrong no son de $year" }
                    val repeated =
                        dates
                            .groupingBy { it }
                            .eachCount()
                            .filterValues { it > 1 }
                            .keys
                    check(repeated.isEmpty()) { "sgspe.calendar.holidays.$year: $repeated se repiten" }
                    year to dates.toSet()
                }
            return HolidayCalendar(byYear, missing)
        }
    }
}

// los días hábiles, puros (RNF-I18N-002, ASM-096): de lunes a viernes y fuera de los feriados de su año
object BusinessDays {
    fun isBusinessDay(
        date: LocalDate,
        calendar: HolidayCalendar
    ): Boolean = date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && !calendar.isHoliday(date)

    // el n-ésimo día hábil posterior a `start`: el día de recepción no cuenta (ASM-096). n en 0 es `start`
    fun addBusinessDays(
        start: LocalDate,
        n: Int,
        calendar: HolidayCalendar
    ): LocalDate {
        require(n >= 0) { "n no puede ser negativo: $n" }
        var date = start
        var counted = 0
        while (counted < n) {
            date = date.plusDays(1)
            if (isBusinessDay(date, calendar)) counted++
        }
        return date
    }
}

// el calendario como bean. al arrancar advierte si falta el año en curso de Lima (riesgo 4 de la especificación de F4)
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CalendarProperties::class)
class HolidayCalendarConfiguration {
    @Bean
    fun holidayCalendar(
        properties: CalendarProperties,
        clock: Clock
    ): HolidayCalendar {
        val calendar = HolidayCalendar.of(properties.holidays)
        val year = LocalDate.now(clock.withZone(LIMA)).year
        if (!calendar.configured(year)) calendar.isHoliday(LocalDate.of(year, 1, 1))
        return calendar
    }
}
```

En `src/main/resources/application.yml`, reemplazar:

```yaml
    # tope de filas que una exportación lee de cada conjunto (StakeholderRegister): pasarse es 400 TOO_MANY_RECORDS
    max-rows: 20000
  concurrency:
    # cuánto se guardan los saltos de versión de las escrituras de derivados (sgspe.derived_version, ADR-0027, ASM-091):
```

por:

```yaml
    # tope de filas que una exportación lee de cada conjunto (StakeholderRegister): pasarse es 400 TOO_MANY_RECORDS
    max-rows: 20000
  calendar:
    # feriados nacionales por año (ASM-096, HolidayCalendar): el acuse de una queja cuenta días hábiles. un año que falta
    # usa lunes a viernes con una advertencia en el log; cada año, operación agrega el siguiente según la norma publicada
    # en El Peruano (D. Leg. 713 y sus modificatorias). los días no laborables del sector público no son feriados
    holidays:
      '2026':
        - 2026-01-01
        - 2026-04-02
        - 2026-04-03
        - 2026-05-01
        - 2026-06-07
        - 2026-06-29
        - 2026-07-23
        - 2026-07-28
        - 2026-07-29
        - 2026-08-06
        - 2026-08-30
        - 2026-10-08
        - 2026-11-01
        - 2026-12-08
        - 2026-12-09
        - 2026-12-25
      '2027':
        - 2027-01-01
        - 2027-03-25
        - 2027-03-26
        - 2027-05-01
        - 2027-06-07
        - 2027-06-29
        - 2027-07-23
        - 2027-07-28
        - 2027-07-29
        - 2027-08-06
        - 2027-08-30
        - 2027-10-08
        - 2027-11-01
        - 2027-12-08
        - 2027-12-09
        - 2027-12-25
  concurrency:
    # cuánto se guardan los saltos de versión de las escrituras de derivados (sgspe.derived_version, ADR-0027, ASM-091):
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `BusinessDaysTest` con sus 5 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/BusinessDays.kt src/main/resources/application.yml src/test/kotlin/sgspe/common/BusinessDaysTest.kt
git commit -F - <<'MSG'
feat: calendario de días hábiles con los feriados de 2026 y 2027

HolidayCalendar lee los feriados nacionales por año de application.yml y los valida al arrancar;
BusinessDays cuenta días hábiles desde el día siguiente a la recepción. Un año sin lista usa lunes
a viernes y lo advierte una vez (ASM-096).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 2: Los nombres de las QCS y el conteo de planteamientos

**Files:**
- Create: `src/main/kotlin/sgspe/common/Attention.kt`
- Test: `src/test/kotlin/sgspe/common/AttentionCountingTest.kt`

**Interfaces:**
- Consumes: `Values`, `ModelCatalog` (solo la prueba).
- Produces: `object Attention` (`GRIEVANCE`, `INQUIRY`, `REQUEST`, `OBJECTS`, `READ_CONFIDENTIAL`, `APPROVE`,
  `CONFIDENTIAL`, `OWNER`, `RECEIVER`, `SEVERITY`, `RECEIVED_AT`, `RESPONDED_AT`, `DEADLINE_STATUS`, `HIGH_SEVERITY`,
  `RECLASSIFIED_FROM: Map<String, Map<String, String>>`, `FINAL_STATES`, `reclassifiedField(target, source)`,
  `isConfidential(attributes)`, `isOpen(objectName, state)`); `data class Raised(type, id, state, attributes)`;
  `data class AttentionTotals(received, open, closed, rejected, withdrawn, absolved, overdue)`;
  `object AttentionCounting` con `originals(rows)`, `counted(rows): List<Raised>` y `totals(rows): AttentionTotals`.

`AttentionCounting` es la regla de `dashboards.md` §3.3 y vive en `common` porque la usan el reporte (`sgspe.attention`),
los KPI-GRV (`sgspe.dashboard`), la exportación PUBLICO (`sgspe.privacy`, que solo depende de `common`) y la regla CONTEO
DE QCS de JOB-04 (plan 4.2b). `Attention` reúne los nombres que comparten esos paquetes y `sgspe.scope`.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/common/AttentionCountingTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// dashboards.md §3.3: cada planteamiento cuenta una vez, como el último registro de la cadena de reclasificaciones
class AttentionCountingTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun raised(
        type: String,
        id: String,
        state: String = "received",
        attributes: Map<String, Any?> = emptyMap()
    ) = Raised(type, id, state, attributes)

    @Test
    fun `un original reclasificado no cuenta y su registro nuevo si`() {
        val queja = raised("grievance", "q1", "closed")
        val consulta = raised("inquiry", "c1", "received", mapOf("reclassified_from_grievance" to "q1"))
        val otra = raised("grievance", "q2", "acknowledged")
        assertEquals(listOf(consulta, otra), AttentionCounting.counted(listOf(queja, consulta, otra)))
        assertEquals(setOf("grievance" to "q1"), AttentionCounting.originals(listOf(queja, consulta, otra)))
    }

    @Test
    fun `una cadena de dos reclasificaciones cuenta una sola vez, como el ultimo`() {
        val queja = raised("grievance", "q1", "closed")
        val solicitud = raised("stakeholder_request", "s1", "closed", mapOf("reclassified_from_grievance" to "q1"))
        val consulta = raised("inquiry", "c1", "answered", mapOf("reclassified_from_request" to "s1"))
        assertEquals(listOf(consulta), AttentionCounting.counted(listOf(queja, solicitud, consulta)))
    }

    @Test
    fun `sin el registro nuevo en la lista el original cuenta`() {
        // quien llama no lee el registro nuevo (una QCS confidencial): solo cuenta lo que ve
        val queja = raised("grievance", "q1", "closed")
        assertEquals(listOf(queja), AttentionCounting.counted(listOf(queja)))
    }

    @Test
    fun `los totales del reporte`() {
        val rows =
            listOf(
                raised("grievance", "q1", "closed", mapOf("responded_at" to "2026-10-01T15:00:00Z")),
                raised("grievance", "q2", "rejected", mapOf("responded_at" to "2026-10-01T15:00:00Z")),
                raised("grievance", "q3", "withdrawn"),
                raised("grievance", "q4", "in_action", mapOf("deadline_status" to "VENCIDO")),
                raised("inquiry", "c1", "received", mapOf("deadline_status" to "AL DIA")),
                raised("stakeholder_request", "s1", "declined", mapOf("responded_at" to "2026-10-02T15:00:00Z")),
                raised("inquiry", "c2", "closed", mapOf("deadline_status" to "VENCIDO"))
            )
        assertEquals(
            AttentionTotals(received = 7, open = 3, closed = 2, rejected = 1, withdrawn = 1, absolved = 3, overdue = 1),
            AttentionCounting.totals(rows)
        )
    }

    // los nombres de Attention existen en el modelo: un renombre rompería el conteo en silencio
    @Test
    fun `los vinculos de reclasificacion y los estados finales existen en el modelo`() {
        for ((objectName, links) in Attention.RECLASSIFIED_FROM) {
            for ((field, source) in links) assertEquals(source, catalog.manyToOneFields(objectName).toMap()[field], "$objectName.$field")
        }
        for ((objectName, states) in Attention.FINAL_STATES) {
            assertEquals(emptySet<String>(), states - catalog.states(objectName).toSet(), objectName)
        }
        for (objectName in Attention.OBJECTS) {
            val fields = catalog.objectOrNull(objectName)!!.fields.keys
            val expected =
                listOf(Attention.CONFIDENTIAL, Attention.OWNER, Attention.RECEIVER, Attention.RECEIVED_AT, Attention.RESPONDED_AT, Attention.DEADLINE_STATUS)
            assertEquals(emptyList<String>(), expected.filter { it !in fields }, objectName)
            assertEquals(true, Attention.READ_CONFIDENTIAL in catalog.objectOrNull(objectName)!!.actions, objectName)
        }
        assertEquals(Attention.HIGH_SEVERITY, catalog.enumOf("grievance", Attention.SEVERITY).takeLast(2).toSet())
        assertEquals("reclassified_from_request", Attention.reclassifiedField("inquiry", "stakeholder_request"))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.AttentionCountingTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'Raised'`, `'AttentionCounting'` y `'Attention'`).

- [ ] **Paso 3: `Attention` y `AttentionCounting`**

Crear `src/main/kotlin/sgspe/common/Attention.kt`:

```kotlin
package sgspe.common

// los nombres de las quejas, consultas y solicitudes (QCS, BC-07) que comparten sgspe.attention, sgspe.scope (el alcance de
// las confidenciales), sgspe.deadline, sgspe.notification, los tableros y las exportaciones: viven en common porque scope y
// privacy solo dependen de common (architecture.md §5.3)
object Attention {
    const val GRIEVANCE = "grievance"
    const val INQUIRY = "inquiry"
    const val REQUEST = "stakeholder_request"

    // en el orden de las fichas (database-model.md §12)
    val OBJECTS: List<String> = listOf(GRIEVANCE, INQUIRY, REQUEST)

    // la acción declarada que lee toda QCS confidencial del objeto (ASM-095, security.md §9.6)
    const val READ_CONFIDENTIAL = "READ_CONFIDENTIAL"

    // la acción declarada de quien resuelve una queja grave (RN-GRV-002): ve la queja confidencial ALTA o CRITICA
    const val APPROVE = "APPROVE"

    const val CONFIDENTIAL = "confidential"
    const val OWNER = "owner_user_id"
    const val RECEIVER = "received_by_user_id"
    const val SEVERITY = "severity"
    const val RECEIVED_AT = "received_at"
    const val RESPONDED_AT = "responded_at"
    const val DEADLINE_STATUS = "deadline_status"

    // las severidades de una queja grave (RN-GRV-002)
    val HIGH_SEVERITY: Set<String> = setOf("ALTA", "CRITICA")

    // los vínculos de reclasificación de cada QCS: campo -> objeto del registro original (RF-GRV-007, ASM-072)
    val RECLASSIFIED_FROM: Map<String, Map<String, String>> =
        mapOf(
            GRIEVANCE to mapOf("reclassified_from_inquiry" to INQUIRY, "reclassified_from_request" to REQUEST),
            INQUIRY to mapOf("reclassified_from_grievance" to GRIEVANCE, "reclassified_from_request" to REQUEST),
            REQUEST to mapOf("reclassified_from_grievance" to GRIEVANCE, "reclassified_from_inquiry" to INQUIRY)
        )

    // los estados finales de cada workflow (dashboards.md §3.2): abierta es todo lo demás
    val FINAL_STATES: Map<String, Set<String>> =
        mapOf(
            GRIEVANCE to setOf("closed", "rejected", "withdrawn"),
            INQUIRY to setOf("closed"),
            REQUEST to setOf("closed")
        )

    // el campo de reclasificación con que el registro nuevo apunta a un original de `source` (reclassified_from_<tipo>)
    fun reclassifiedField(
        target: String,
        source: String
    ): String =
        RECLASSIFIED_FROM
            .getValue(target)
            .entries
            .first { it.value == source }
            .key

    fun isConfidential(attributes: Map<String, Any?>): Boolean = Values.bool(attributes[CONFIDENTIAL]) == true

    fun isOpen(
        objectName: String,
        state: String?
    ): Boolean = state != null && state !in FINAL_STATES.getValue(objectName)
}

// un planteamiento como lo cuenta AttentionCounting: su tipo (el objeto), su id, su estado y sus atributos ya leídos
data class Raised(
    val type: String,
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>
)

// los conteos de un conjunto de planteamientos (api.md §4.6, reporte unificado): recibidos, abiertos, cerrados (en
// `closed`), rechazados, desistidos, absueltos (con responded_at, RQ-DSH-023) y vencidos (abiertos con deadline_status VENCIDO)
data class AttentionTotals(
    val received: Int,
    val open: Int,
    val closed: Int,
    val rejected: Int,
    val withdrawn: Int,
    val absolved: Int,
    val overdue: Int
)

// la regla de conteo de dashboards.md §3.3, pura: cada planteamiento cuenta una vez, como el último registro de su cadena de
// reclasificaciones; los originales a los que apunta un reclassified_from_* no cuentan. la usan el reporte unificado, los
// KPI-GRV, la exportación PUBLICO y la regla CONTEO DE QCS de JOB-04 (plan 4.2b). solo ve lo que recibe: lo que quien llama
// no lee no está en la lista y no cuenta
object AttentionCounting {
    // los originales reclasificados que la lista apunta: (tipo, id)
    fun originals(rows: Collection<Raised>): Set<Pair<String, String>> =
        rows
            .flatMap { row ->
                Attention.RECLASSIFIED_FROM[row.type].orEmpty().mapNotNull { (field, source) ->
                    Values.text(row.attributes[field])?.let { source to it }
                }
            }.toSet()

    // los planteamientos: la lista sin los originales que otro registro de la lista reclasificó
    fun counted(rows: Collection<Raised>): List<Raised> {
        val originals = originals(rows)
        return rows.filterNot { (it.type to it.id) in originals }
    }

    fun totals(rows: Collection<Raised>): AttentionTotals {
        val counted = counted(rows)
        return AttentionTotals(
            received = counted.size,
            open = counted.count { Attention.isOpen(it.type, it.state) },
            closed = counted.count { it.state == "closed" },
            rejected = counted.count { it.state == "rejected" },
            withdrawn = counted.count { it.state == "withdrawn" },
            absolved = counted.count { Values.text(it.attributes[Attention.RESPONDED_AT]) != null },
            overdue = counted.count { Attention.isOpen(it.type, it.state) && Values.text(it.attributes[Attention.DEADLINE_STATUS]) == OVERDUE }
        )
    }

    const val OVERDUE = "VENCIDO"
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `AttentionCountingTest` con 5 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/Attention.kt src/test/kotlin/sgspe/common/AttentionCountingTest.kt
git commit -F - <<'MSG'
feat: conteo de planteamientos de las QCS

AttentionCounting cuenta cada planteamiento una vez, como el último registro de su cadena de
reclasificaciones (dashboards.md §3.3); Attention reúne los nombres de las QCS que comparten el
reporte, los tableros, las exportaciones, el alcance y los avisos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 3: Los parámetros de F4 del proyecto y sus rangos

**Files:**
- Modify: `src/main/kotlin/sgspe/common/ProjectParameters.kt`, `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`
- Test: `src/test/kotlin/sgspe/common/ProjectParametersF4Test.kt`, `src/test/kotlin/sgspe/scope/ProjectParametersApiTest.kt`

**Interfaces:**
- Consumes: `Finding`, `Values`, `WriteView`, `isLink`.
- Produces: los ocho parámetros de F4 con su valor por defecto en `ProjectParameters.integer` y `policy`
  (`grievance_ack_days` 5, `inquiry_response_days` 15, `grievance_resolution_days` 30, `risk_review_days` 7,
  `risk_medium_min_score` 5, `risk_high_min_score` 10, `risk_critical_min_score` 15,
  `policy_high_severity_requires_approver` verdadero); `ProjectParameters.PERIODS`, `ProjectParameters.THRESHOLDS` y
  `ProjectParameters.outOfRange(after: Map<String, Any?>, changed: Set<String>): List<Finding>`; `ProjectWriteGuard` con
  un `WriteView` más en su constructor.

Un proyecto creado antes de F4 tiene los ocho vacíos: el código los lee con el mismo valor por defecto de la ficha
(UPS-15). `ProjectWriteGuard` rechaza con `400 OUT_OF_RANGE` lo que la escritura cambia: un plazo o una cadencia fuera de 1
a 365 o no entero, y umbrales fuera de 1 a 25, no enteros o que no crecen (especificación §3.1).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/ProjectParametersF4Test.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// los parámetros de F4 (database-model.md §6): su valor por defecto en un proyecto anterior a F4 y sus rangos
class ProjectParametersF4Test {
    private val empty = emptyMap<String, Any?>()
    private val catalog = ModelCatalog.fromClasspath()

    private fun codes(
        after: Map<String, Any?>,
        changed: Set<String> = after.keys
    ) = ProjectParameters.outOfRange(after, changed).map { it.field to it.code }

    @Test
    fun `los ocho de F4 tienen su valor por defecto`() {
        assertEquals(5L, ProjectParameters.integer(empty, "grievance_ack_days"))
        assertEquals(15L, ProjectParameters.integer(empty, "inquiry_response_days"))
        assertEquals(30L, ProjectParameters.integer(empty, "grievance_resolution_days"))
        assertEquals(7L, ProjectParameters.integer(empty, "risk_review_days"))
        assertEquals(listOf(5L, 10L, 15L), ProjectParameters.THRESHOLDS.map { ProjectParameters.integer(empty, it) })
        assertEquals(true, ProjectParameters.policy(empty, "policy_high_severity_requires_approver"))
    }

    // el valor por defecto de aquí es el de model.json (defaultValue), que wasichai guarda pero no aplica (UPS-15)
    @Test
    fun `los valores por defecto son los del modelo`() {
        val project = catalog.objectOrNull("project")!!.fields
        for (name in ProjectParameters.PERIODS + ProjectParameters.THRESHOLDS) assertEquals(true, name in project, name)
        assertEquals(true, "policy_high_severity_requires_approver" in project)
    }

    @Test
    fun `un plazo o una cadencia fuera de 1 a 365, o no entero, es OUT_OF_RANGE`() {
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("grievance_ack_days" to 1, "risk_review_days" to 365)))
        assertEquals(listOf("grievance_ack_days" to "OUT_OF_RANGE"), codes(mapOf("grievance_ack_days" to 0)))
        assertEquals(listOf("inquiry_response_days" to "OUT_OF_RANGE"), codes(mapOf("inquiry_response_days" to 366)))
        assertEquals(listOf("grievance_resolution_days" to "OUT_OF_RANGE"), codes(mapOf("grievance_resolution_days" to "2.5")))
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("grievance_resolution_days" to "30.00")))
        // vacío vale el valor por defecto
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("grievance_ack_days" to null)))
    }

    @Test
    fun `los umbrales van de 1 a 25 y crecen estrictamente`() {
        assertEquals(
            emptyList<Pair<String?, String>>(),
            codes(mapOf("risk_medium_min_score" to 4, "risk_high_min_score" to 9, "risk_critical_min_score" to 20))
        )
        assertEquals(listOf("risk_critical_min_score" to "OUT_OF_RANGE"), codes(mapOf("risk_critical_min_score" to 26)))
        // alto igual a crítico, con el medio por defecto
        assertEquals(listOf("risk_high_min_score" to "OUT_OF_RANGE"), codes(mapOf("risk_high_min_score" to 15)))
        // un umbral que no se cambia no se juzga: un valor viejo fuera de regla no bloquea otra escritura
        assertEquals(emptyList<Pair<String?, String>>(), codes(mapOf("risk_high_min_score" to 15, "name" to "x"), setOf("name")))
    }
}
```

Crear `src/test/kotlin/sgspe/scope/ProjectParametersApiTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// los parámetros de F4 del proyecto (database-model.md §6, ASM-004, ASM-005): ProjectWriteGuard rechaza con 400 OUT_OF_RANGE
// un plazo o una cadencia fuera de 1 a 365 días y umbrales de riesgo fuera de 1 a 25 o que no crecen, en el alta y en el cambio
class ProjectParametersApiTest : DomainApiTest() {
    private fun createProject(attributes: Map<String, Any?>): Response =
        exchange(
            "POST",
            "/api/objects/project/records",
            mapOf("attributes" to mapOf("code" to uniqueName("PRY-"), "name" to "Proyecto de prueba") + attributes)
        )

    @Test
    fun `un plazo de 0 o de 366 dias no se guarda`() {
        val zero = createProject(mapOf("grievance_ack_days" to 0))
        assertEquals(HttpStatus.BAD_REQUEST, zero.status, zero.body)
        assertEquals("OUT_OF_RANGE", code(zero))
        assertEquals(listOf("grievance_ack_days"), problemFields(zero.body))
        val project = project(mapOf("inquiry_response_days" to 20))
        val long = edit("project", project, mapOf("risk_review_days" to 366))
        assertEquals(HttpStatus.BAD_REQUEST, long.status, long.body)
        assertEquals(listOf("risk_review_days"), problemFields(long.body))
    }

    @Test
    fun `los umbrales de riesgo crecen estrictamente`() {
        val project = project()
        val flat = edit("project", project, mapOf("risk_medium_min_score" to 10, "risk_high_min_score" to 10))
        assertEquals(HttpStatus.BAD_REQUEST, flat.status, flat.body)
        assertEquals("OUT_OF_RANGE", code(flat))
        val ok = edit("project", project, mapOf("risk_medium_min_score" to 4, "risk_high_min_score" to 12, "risk_critical_min_score" to 20))
        assertEquals(HttpStatus.OK, ok.status, ok.body)
    }
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.ProjectParametersF4Test'
```

Esperado: FAIL en la compilación (`Unresolved reference 'outOfRange'`, `'PERIODS'` y `'THRESHOLDS'`).

- [ ] **Paso 3: Los valores por defecto y la regla de rangos**

En `src/main/kotlin/sgspe/common/ProjectParameters.kt`, reemplazar:

```kotlin
package sgspe.common

import java.math.BigDecimal

```

por:

```kotlin
package sgspe.common

import org.springframework.http.HttpStatus
import java.math.BigDecimal

```

En `src/main/kotlin/sgspe/common/ProjectParameters.kt`, reemplazar:

```kotlin
            "due_soon_days" to 7L,
            "escalation_first_days" to 7L,
            "escalation_second_days" to 15L
        )
    private val POLICIES =
```

por:

```kotlin
            "due_soon_days" to 7L,
            "escalation_first_days" to 7L,
            "escalation_second_days" to 15L,
            // F4: plazos de QCS (ASM-005, ASM-100), cadencia de revisión de riesgos (RN-RSK-001) y umbrales de nivel (ASM-004)
            "grievance_ack_days" to 5L,
            "inquiry_response_days" to 15L,
            "grievance_resolution_days" to 30L,
            "risk_review_days" to 7L,
            "risk_medium_min_score" to 5L,
            "risk_high_min_score" to 10L,
            "risk_critical_min_score" to 15L
        )
    private val POLICIES =
```

En `src/main/kotlin/sgspe/common/ProjectParameters.kt`, reemplazar:

```kotlin
            "policy_senior_approval_for_dialogue" to true,
            "policy_internal_approval" to true,
            "policy_cap_blocks_approval" to true
        )

    fun decimal(
```

por:

```kotlin
            "policy_senior_approval_for_dialogue" to true,
            "policy_internal_approval" to true,
            "policy_cap_blocks_approval" to true,
            // F4: una queja ALTA o CRITICA la resuelve quien tiene APPROVE sobre grievance (RN-GRV-002)
            "policy_high_severity_requires_approver" to true
        )

    // F4: los plazos y la cadencia van de 1 a 365 días; los umbrales de nivel de riesgo, de 1 a 25 y crecientes (ASM-004)
    val PERIODS: List<String> = listOf("grievance_ack_days", "inquiry_response_days", "grievance_resolution_days", "risk_review_days")
    val THRESHOLDS: List<String> = listOf("risk_medium_min_score", "risk_high_min_score", "risk_critical_min_score")

    fun decimal(
```

En `src/main/kotlin/sgspe/common/ProjectParameters.kt`, reemplazar:

```kotlin
        name: String
    ): Boolean = Values.bool(project[name]) ?: POLICIES.getValue(name)
}
```

por:

```kotlin
        name: String
    ): Boolean = Values.bool(project[name]) ?: POLICIES.getValue(name)

    // los 400 OUT_OF_RANGE de los parámetros de F4 que la escritura cambia (`changed`), con el proyecto como queda (`after`):
    // un plazo o una cadencia fuera de 1 a 365, o no entero; un umbral fuera de 1 a 25, no entero, o que no crece
    // estrictamente (medio < alto < crítico, con el valor por defecto en los vacíos). lo aplica ProjectWriteGuard; un valor
    // guardado antes de la regla no bloquea otra escritura del proyecto
    fun outOfRange(
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        for (name in PERIODS.filter { it in changed }) {
            if (!within(after[name], 1, 365)) findings += outOfRange(name, "debe ser un número entero de días de 1 a 365")
        }
        val touched = THRESHOLDS.filter { it in changed }
        if (touched.isEmpty()) return findings
        val wrong = touched.filter { !within(after[it], 1, 25) }
        findings += wrong.map { outOfRange(it, "debe ser un puntaje entero de 1 a 25") }
        if (wrong.isEmpty()) {
            val values = THRESHOLDS.map { integer(after, it) }
            if (values.zipWithNext().any { (low, high) -> low >= high }) {
                findings += outOfRange(touched.first(), "los umbrales crecen estrictamente: medio < alto < crítico (hoy ${values.joinToString(", ")})")
            }
        }
        return findings
    }

    // vacío vale el valor por defecto, que está en rango
    private fun within(
        value: Any?,
        min: Long,
        max: Long
    ): Boolean {
        val number = Values.decimal(value) ?: return Values.text(value) == null
        val whole = runCatching { number.stripTrailingZeros().longValueExact() }.getOrNull() ?: return false
        return whole in min..max
    }

    private fun outOfRange(
        field: String,
        detail: String
    ): Finding = Finding("OUT_OF_RANGE", "$field $detail", field, status = HttpStatus.BAD_REQUEST)
}
```

En `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`, reemplazar:

```kotlin
import sgspe.common.RuleGuard
import sgspe.common.Values
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
```

por:

```kotlin
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
```

En `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`, reemplazar:

```kotlin
// las reglas de WF-A (workflows.md, RF-PRJ-003): llegar a `active` (approve, y resume desde suspended) exige el CRS de
// trabajo y al menos una project_party CLIENTE u OPERADOR, salvo que policy_activation_preconditions esté en falso
// (ASM-062); approve exige además la acción declarada APPROVE sobre project, la doble llave de RF-WFL-002
@Component
@Order(GuardOrder.DOMAIN)
```

por:

```kotlin
// las reglas de WF-A (workflows.md, RF-PRJ-003): llegar a `active` (approve, y resume desde suspended) exige el CRS de
// trabajo y al menos una project_party CLIENTE u OPERADOR, salvo que policy_activation_preconditions esté en falso
// (ASM-062); approve exige además la acción declarada APPROVE sobre project, la doble llave de RF-WFL-002. desde F4, el alta
// y el cambio de un proyecto rechazan los plazos, la cadencia y los umbrales de riesgo fuera de rango (ProjectParameters)
@Component
@Order(GuardOrder.DOMAIN)
```

En `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`, reemplazar:

```kotlin
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions
) : RuleGuard() {
    override suspend fun findings(
```

por:

```kotlin
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
```

En `src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt`, reemplazar:

```kotlin
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != "project" || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition !in TO_ACTIVE) return emptyList()
```

por:

```kotlin
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != "project") return emptyList()
        if ((change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) && !change.isLink()) {
            val after = view.after(change)
            return ProjectParameters.outOfRange(after, view.changed(change, after))
        }
        if (change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val transition = change.transition ?: return emptyList()
        if (transition !in TO_ACTIVE) return emptyList()
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ProjectParametersF4Test` con 4 pruebas en verde y `ProjectParametersF3Test` sin cambios.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/ProjectParameters.kt src/main/kotlin/sgspe/scope/ProjectWriteGuard.kt \
  src/test/kotlin/sgspe/common/ProjectParametersF4Test.kt src/test/kotlin/sgspe/scope/ProjectParametersApiTest.kt
git commit -F - <<'MSG'
feat: parámetros de F4 del proyecto y sus rangos

ProjectParameters suma los plazos de QCS, la cadencia de revisión de riesgos, los umbrales de nivel
y la política de quejas graves con su valor por defecto; ProjectWriteGuard rechaza con 400
OUT_OF_RANGE un plazo fuera de 1 a 365 días y umbrales fuera de 1 a 25 o que no crecen.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.scope.ProjectParametersApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 4: El alcance de los objetos de F4 y la regla «mismo proyecto»

**Files:**
- Create: `src/main/kotlin/sgspe/common/ReadAs.kt`, `src/main/kotlin/sgspe/scope/ProjectLinks.kt`,
  `src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt`
- Modify: `src/main/kotlin/sgspe/scope/ProjectScope.kt`; `src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt`,
  `ProjectLinkRule.kt` (reescrito), y los imports de `ApprovalRules.kt`, `CandidateRules.kt`, `Candidates.kt`,
  `CommitmentDerivations.kt`, `CommitmentRecordRules.kt`, `FulfillmentRules.kt`, `SeniorApproval.kt`,
  `src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt` y `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`
- Move: `src/test/kotlin/sgspe/commitment/ProjectLinksTest.kt` → `src/test/kotlin/sgspe/scope/ProjectLinksTest.kt`
- Modify (imports): `src/test/kotlin/sgspe/commitment/{BlockingDependencyRuleTest,CommitmentRecordRulesApiTest,CommitmentRecordRulesTest,CommitmentWriteGuardTest}.kt`,
  `src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt`, `src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt`
- Test: `src/test/kotlin/sgspe/scope/ProjectScopeF4Test.kt`, `src/test/kotlin/sgspe/scope/ProjectLinkGuardApiTest.kt`

**Interfaces:**
- Consumes: `ModelCatalog.manyToOneFields`, `isRequiredRelation`, `manyToMany`; `WriteView`; `F4ApiTest`.
- Produces: `sgspe.common.NotReadable(objectName)`, `suspend fun <T> readAs(objectName, block): T`,
  `suspend fun Records.readable(objectName, id): RecordResponse` (antes en `sgspe.commitment`);
  `sgspe.scope.ProjectLinks` (`fieldsOf(catalog, objectName)`, `tenantCapable(catalog)`, `mismatches(...)`, `judge(...)`;
  `fields(catalog)` desaparece: se usa `fieldsOf(catalog, "commitment")`) y `suspend fun Records.linkedProject(target, id)`;
  `ProjectLinkGuard` con `OBJECTS` (los 12 de F4) y `LINKS` (`social_risk_stakeholder`, `incident_stakeholder`);
  `ProjectScope.ROOTS` y `CHILDREN` con los objetos de F4.

`sgspe.scope` solo depende de `sgspe.common` (`ArchitectureTest`), así que la pieza de P54 sale de `sgspe.commitment`
(decisión 1). Con `grievance` y `social_risk` en `ProjectScope`, los campos de `ProjectLinkRule` suman solos
`origin_grievance` y `social_risk` del compromiso (decisión 2). Una regla de alerta sin proyecto es del tenant: no
discrepa.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/scope/ProjectScopeF4Test.kt`:

```kotlin
package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// el alcance de escritura de los objetos de F4 (security.md §9.2): las raíces por su proyecto, las revisiones y las medidas por
// su riesgo, y la regla de alerta, que puede ser del tenant, solo cuando trae proyecto
class ProjectScopeF4Test {
    private val project = UUID.randomUUID()
    private val risk = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()
    private val parentProject: suspend (String, UUID) -> UUID? = { _, id -> if (id == risk) project else null }

    private fun resolve(
        objectName: String,
        before: Map<String, Any?>
    ) = runBlocking {
        val write =
            RecordWrite(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                objectName,
                UUID.randomUUID(),
                RecordChangeKind.UPDATED,
                before,
                null,
                null,
                null
            )
        ProjectResolver.resolve(write, before, catalog::isRequiredRelation, parentProject)
    }

    @Test
    fun `cada objeto de F4 tiene alcance`() {
        for (name in ProjectLinkGuard.OBJECTS - setOf("risk_assessment", "mitigation_measure")) assertTrue(name in ProjectScope.ROOTS, name)
        for (name in listOf("risk_assessment", "mitigation_measure")) assertEquals("social_risk" to "social_risk", ProjectScope.CHILDREN[name], name)
    }

    @Test
    fun `una revision y una medida toman el proyecto de su riesgo`() {
        assertEquals(setOf(project), resolve("risk_assessment", mapOf("social_risk" to risk.toString())).projects)
        assertEquals(setOf(project), resolve("mitigation_measure", mapOf("social_risk" to risk.toString())).projects)
    }

    @Test
    fun `una regla del tenant no pide alcance, y una de proyecto si`() {
        val tenant = resolve("early_warning_rule", mapOf("project" to null))
        assertFalse(tenant.unresolved)
        assertTrue(tenant.projects.isEmpty())
        assertEquals(setOf(project), resolve("early_warning_rule", mapOf("project" to project.toString())).projects)
        // una queja guardada sin proyecto no tiene dueño: falla cerrada
        assertTrue(resolve("grievance", mapOf("project" to null)).unresolved)
    }
}
```

Crear `src/test/kotlin/sgspe/scope/ProjectLinkGuardApiTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el alcance de los objetos de F4 y la regla «mismo proyecto» (ProjectLinkGuard, api.md §3.3): un registro de F4 solo
// referencia registros de su proyecto, también por un vínculo M2M y desde un compromiso; una regla del tenant no discrepa
class ProjectLinkGuardApiTest : F4ApiTest() {
    private fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        token: String = this.token
    ): Response = exchange("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), token, withReason("alta de prueba"))

    @Test
    fun `una queja con el grupo de interes de otro proyecto es PROJECT_MISMATCH`() {
        val other = project()
        val response = create("grievance", attentionAttributes(testProject(), null, "QJ-") + mapOf("stakeholder" to stakeholder(other)))
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("stakeholder"), problemFields(response.body))
        // del mismo proyecto, pasa
        grievance(testProject(), attributes = mapOf("stakeholder" to stakeholder(testProject())))
    }

    @Test
    fun `una solicitud reclasificada de una queja de otro proyecto es PROJECT_MISMATCH`() {
        val foreign = grievance(project())
        val response =
            create("stakeholder_request", attentionAttributes(testProject(), null, "SO-") + mapOf("reclassified_from_grievance" to foreign))
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("reclassified_from_grievance"), problemFields(response.body))
    }

    @Test
    fun `un compromiso que nace de una queja de otro proyecto es PROJECT_MISMATCH`() {
        val foreign = grievance(project())
        val response =
            exchange(
                "POST",
                "/api/objects/commitment/records",
                mapOf(
                    "attributes" to
                        commitmentAttributes(testProject(), userWithRole("COMMITMENT_MANAGER"), userWithRole("COMMITMENT_MANAGER")) +
                        ("origin_grievance" to foreign)
                ),
                token,
                withReason("alta de prueba")
            )
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals(listOf("origin_grievance"), problemFields(response.body))
    }

    @Test
    fun `un riesgo vinculado a un grupo de interes de otro proyecto es PROJECT_MISMATCH`() {
        val risk = socialRisk(testProject())
        val response = linkWithReason("social_risk", risk, "social_risk_stakeholder", stakeholder(project()))
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("social_risk_stakeholder"), problemFields(response.body))
        assertEquals(HttpStatus.NO_CONTENT, linkWithReason("social_risk", risk, "social_risk_stakeholder", stakeholder(testProject())).status)
    }

    @Test
    fun `una alerta de una regla del tenant pasa y una de una regla de otro proyecto no`() {
        earlyWarning(testProject(), attributes = mapOf("early_warning_rule" to warningRule(null)))
        val response =
            exchange(
                "POST",
                "/api/objects/early_warning/records",
                mapOf(
                    "attributes" to
                        mapOf(
                            "project" to testProject(),
                            "title" to "Alerta",
                            "detected_at" to "2026-10-07T15:00:00Z",
                            "early_warning_rule" to warningRule(project())
                        )
                ),
                token
            )
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals(listOf("early_warning_rule"), problemFields(response.body))
    }

    @Test
    fun `quien no esta asignado al proyecto no registra una queja`() {
        val outsider = userWithRole("COMMUNITY_RELATIONS_OFFICER")
        val response = create("grievance", attentionAttributes(testProject(), outsider, "QJ-"), outsider.token)
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertEquals("NOT_ASSIGNED", code(response))
    }
}
```

La prueba de `ProjectLinks` pasa a `sgspe.scope`, con los campos de F4:

```bash
cd ~/IdeaProjects/sgspe && git mv src/test/kotlin/sgspe/commitment/ProjectLinksTest.kt src/test/kotlin/sgspe/scope/ProjectLinksTest.kt
```

Reescribir `src/test/kotlin/sgspe/scope/ProjectLinksTest.kt` entero:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.util.UUID

// P54 y la regla «mismo proyecto» de F4: un registro solo referencia registros de su proyecto; los campos salen del modelo,
// sin base
class ProjectLinksTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val a = UUID.randomUUID()
    private val b = UUID.randomUUID()

    @Test
    fun `los campos vinculados con alcance de proyecto salen del modelo`() {
        val fields = ProjectLinks.fieldsOf(catalog, "commitment").map { it.first }
        assertTrue(fields.containsAll(listOf("exploration_campaign", "origin_engagement", "agreement")), fields.toString())
        // el propio alcance no es un vínculo, ni lo que no es de un proyecto (localidad, tema, comunidad)
        assertFalse("project" in fields)
        assertTrue(listOf("locality", "topic", "community").none { it in fields }, fields.toString())
    }

    @Test
    fun `un destino de otro proyecto, o sin proyecto, es una discrepancia`() {
        assertEquals(emptyList<String>(), ProjectLinks.mismatches(a, mapOf("agreement" to a, "exploration_campaign" to a)))
        assertEquals(listOf("exploration_campaign"), ProjectLinks.mismatches(a, mapOf("agreement" to a, "exploration_campaign" to b)))
        assertEquals(listOf("agreement"), ProjectLinks.mismatches(a, mapOf("agreement" to null)))
        assertEquals(emptyList<String>(), ProjectLinks.mismatches(null, mapOf("agreement" to b)))
    }

    @Test
    fun `un destino que puede ser del tenant pasa sin proyecto, pero no con el de otro`() {
        val targets = mapOf("source_evidence" to ("evidence" to null), "engagement" to ("engagement" to null))
        assertEquals(listOf("engagement"), ProjectLinks.mismatches(a, targets, setOf("evidence")))
        assertEquals(listOf("source_evidence"), ProjectLinks.mismatches(a, mapOf("source_evidence" to ("evidence" to b)), setOf("evidence")))
        assertEquals(listOf("source_evidence", "engagement"), ProjectLinks.mismatches(a, targets, emptySet()))
    }

    // F4: la queja y el riesgo de origen de un compromiso, y las relaciones de las QCS hacia objetos con proyecto
    @Test
    fun `los campos de F4 con alcance de proyecto salen del modelo`() {
        assertTrue(ProjectLinks.fieldsOf(catalog, "commitment").map { it.first }.containsAll(listOf("origin_grievance", "social_risk")))
        val grievance = ProjectLinks.fieldsOf(catalog, "grievance").toMap()
        assertEquals(
            mapOf(
                "stakeholder" to "stakeholder",
                "origin_engagement" to "engagement",
                "origin_question" to "engagement_question",
                "social_risk" to "social_risk",
                "incident" to "incident",
                "duplicate_of" to "grievance",
                "reclassified_from_inquiry" to "inquiry",
                "reclassified_from_request" to "stakeholder_request"
            ),
            grievance
        )
        assertTrue("created_commitment" in ProjectLinks.fieldsOf(catalog, "stakeholder_request").toMap())
        assertEquals("early_warning_rule", ProjectLinks.fieldsOf(catalog, "early_warning").toMap()["early_warning_rule"])
    }

    @Test
    fun `una regla de alerta del tenant no es de otro proyecto`() {
        assertEquals(setOf("export_log", "evidence", "early_warning_rule"), ProjectLinks.tenantCapable(catalog))
        val targets = mapOf("early_warning_rule" to ("early_warning_rule" to null))
        assertEquals(emptyList<String>(), ProjectLinks.mismatches(a, targets, ProjectLinks.tenantCapable(catalog)))
    }

    // los vínculos M2M que juzga ProjectLinkGuard existen y salen de un objeto de F4 hacia stakeholder
    @Test
    fun `los vinculos de ProjectLinkGuard existen en el modelo`() {
        for (name in ProjectLinkGuard.LINKS) {
            val relationship = catalog.manyToMany(name)
            assertEquals("stakeholder", relationship?.target, name)
            assertTrue(relationship!!.source in ProjectLinkGuard.OBJECTS, name)
        }
        assertEquals(emptySet<String>(), ProjectLinkGuard.OBJECTS - catalog.objects.keys)
    }
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.*'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ProjectLinkGuard'` y `'ProjectLinks'` en `sgspe.scope`).

- [ ] **Paso 3: `readAs` a `common`, `ProjectLinks` a `scope` y `ProjectLinkGuard`**

Crear las piezas nuevas:

Crear `src/main/kotlin/sgspe/common/ReadAs.kt`:

```kotlin
package sgspe.common

import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordResponse
import java.util.UUID

// lo que una regla no puede leer como quien escribe (P12): un objeto sin READ es un bloqueo OBJECT_NOT_READABLE, nunca un
// pase en silencio. vive en common desde F4: lo usan las reglas de compromisos y ProjectLinkGuard (sgspe.scope)
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

// el registro `id` de `objectName` leído como quien escribe. falla cerrada (P12): sin READ, fuera del alcance o inexistente es
// NotReadable, y el mensaje no distingue «no existe» de «no se lee» para no revelar si existe
suspend fun Records.readable(
    objectName: String,
    id: UUID
): RecordResponse = readAs(objectName) { find(objectName, id) } ?: throw NotReadable(objectName)
```

Crear `src/main/kotlin/sgspe/scope/ProjectLinks.kt`:

```kotlin
package sgspe.scope

import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// P54 (F3) y la regla «mismo proyecto» de F4 (api.md §3.3, `PROJECT_MISMATCH`): un registro solo referencia registros de su
// propio proyecto. las lecturas son del tenant hasta UPS-01, así que sin esta regla un miembro del proyecto A vincularía lo del
// proyecto B. fieldsOf y mismatches son puras; judge lee los destinos como quien escribe. la usan ProjectLinkRule y
// CandidateLinkRule (sgspe.commitment) y ProjectLinkGuard (F4)
object ProjectLinks {
    // los campos RELATION de `objectName` cuyo objeto destino tiene alcance de proyecto, derivados del modelo; `project` es el
    // propio alcance y no un vínculo
    fun fieldsOf(
        catalog: ModelCatalog,
        objectName: String
    ): List<Pair<String, String>> = catalog.manyToOneFields(objectName).filter { (field, target) -> field != "project" && ProjectScope.covers(target) }

    // las raíces cuyo `project` es opcional (evidence, export_log, early_warning_rule): sin proyecto son del tenant y no de otro
    // proyecto
    fun tenantCapable(catalog: ModelCatalog): Set<String> = ProjectScope.ROOTS.filter { !catalog.isRequiredRelation(it, "project") }.toSet()

    // los campos cuyo destino es de otro proyecto (o de ninguno): `targets` da el proyecto de cada destino vinculado
    fun mismatches(
        project: UUID?,
        targets: Map<String, UUID?>
    ): List<String> = if (project == null) emptyList() else targets.filterValues { it != project }.keys.toList()

    // lo mismo con el objeto destino de cada campo: uno de `tenantCapable` sin proyecto es del tenant y no de otro proyecto; con
    // el de otro, sí discrepa
    fun mismatches(
        project: UUID?,
        targets: Map<String, Pair<String, UUID?>>,
        tenantCapable: Set<String>
    ): List<String> = mismatches(project, targets.filterValues { (target, owner) -> owner != null || target !in tenantCapable }.mapValues { it.value.second })

    // los campos de `fields` que una escritura de `objectName` deja vinculados a otro proyecto. se juzga un alta, un vínculo que
    // cambia y todos los vínculos si cambia el proyecto del registro; ni una transición, ni un vínculo M2M, ni un registro sin
    // proyecto. lee como quien escribe: un destino que no se lee es NotReadable
    suspend fun judge(
        objectName: String,
        fields: List<Pair<String, String>>,
        tenantCapable: Set<String>,
        change: RecordWrite,
        records: Records,
        view: WriteView
    ): List<String> {
        if (change.objectName != objectName) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val project = Records.uuid(after["project"]) ?: return emptyList()
        val changed = view.changed(change, after)
        val all = change.kind == RecordChangeKind.CREATED || "project" in changed
        val judged = fields.filter { (field, _) -> (all || field in changed) && Records.uuid(after[field]) != null }
        val targets = judged.associate { (field, target) -> field to (target to records.linkedProject(target, Records.uuid(after[field])!!)) }
        return mismatches(project, targets, tenantCapable)
    }
}

// el proyecto del registro `id` de `target` (un objeto con alcance de proyecto) leído como quien escribe: el suyo si es una raíz,
// el de su padre si es un hijo (agreement_clause, engagement_question). un destino que no se lee es NotReadable
suspend fun Records.linkedProject(
    target: String,
    id: UUID
): UUID? {
    val record = readable(target, id)
    val child = ProjectScope.CHILDREN[target] ?: return Records.uuid(record.attributes["project"])
    val parent = Records.uuid(record.attributes[child.first]) ?: return null
    return Records.uuid(readable(child.second, parent).attributes["project"])
}
```

Crear `src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt`:

```kotlin
package sgspe.scope

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordWrite

// la regla «mismo proyecto» de los objetos de F4 (workflows.md, regla 10; api.md §3.3): una relación hacia un registro con
// alcance de proyecto (los campos salen del catálogo, ProjectLinks.fieldsOf) y los vínculos social_risk_stakeholder e
// incident_stakeholder deben quedar en el proyecto del registro; si no, 409 PROJECT_MISMATCH sobre el campo o la relación. una
// regla de alerta del tenant (sin proyecto) no discrepa. lee los destinos como quien escribe: uno que no lee es 403
// OBJECT_NOT_READABLE. compromisos y candidatos usan la misma pieza desde sus reglas (ProjectLinkRule, CandidateLinkRule), así
// que commitment.origin_grievance y commitment.social_risk se juzgan allí
@Component
@Order(GuardOrder.DOMAIN)
class ProjectLinkGuard(
    private val records: Records,
    private val view: WriteView,
    private val catalog: ModelCatalog
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS) return emptyList()
        return try {
            if (change.isLink()) links(change) else fields(change)
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName}: el registro no existe o quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun fields(change: RecordWrite): List<Finding> =
        ProjectLinks
            .judge(change.objectName, ProjectLinks.fieldsOf(catalog, change.objectName), ProjectLinks.tenantCapable(catalog), change, records, view)
            .map { mismatch(change.objectName, it) }

    // un vínculo M2M que se crea, juzgado en el extremo que es la fuente de la relación (wasichai llama a las guardas con cada
    // extremo): el proyecto del registro contra el del grupo de interés. deshacer un vínculo no se juzga
    private suspend fun links(change: RecordWrite): List<Finding> {
        val project = Records.uuid(change.before.orEmpty()["project"]) ?: return emptyList()
        return LINKS.mapNotNull { name ->
            val relationship = catalog.manyToMany(name)?.takeIf { it.source == change.objectName } ?: return@mapNotNull null
            val other = Records.uuid(change.attributes.orEmpty()["rel:$name"]) ?: return@mapNotNull null
            if (ProjectLinks
                    .mismatches(
                        project,
                        mapOf(name to records.linkedProject(relationship.target, other))
                    ).isEmpty()
            ) {
                null
            } else {
                mismatch(change.objectName, name)
            }
        }
    }

    private fun mismatch(
        objectName: String,
        field: String
    ) = Finding("PROJECT_MISMATCH", "El registro vinculado en $field es de otro proyecto: un registro de $objectName solo referencia lo de su proyecto", field)

    companion object {
        // los objetos de F4 (BC-07, BC-08 y BC-09)
        val OBJECTS: Set<String> =
            setOf(
                "grievance",
                "inquiry",
                "stakeholder_request",
                "social_risk",
                "risk_assessment",
                "mitigation_measure",
                "potential_impact",
                "incident",
                "media_mention",
                "early_warning_signal",
                "early_warning_rule",
                "early_warning"
            )

        // los vínculos MANY_TO_MANY de F4 hacia grupos de interés
        val LINKS: List<String> = listOf("social_risk_stakeholder", "incident_stakeholder")
    }
}
```

El alcance de los objetos de F4:

En `src/main/kotlin/sgspe/scope/ProjectScope.kt`, reemplazar:

```kotlin
// a qué proyecto pertenece un registro (domain-model §4.7): las raíces llevan `project`; los hijos lo heredan de su raíz
// por su relación; `project` es su propio proyecto. los objetos que también pueden ser del tenant (export_log y, desde F3,
// evidence…) tienen `project` opcional y solo se acotan cuando lo traen. las fases siguientes agregan sus objetos aquí
object ProjectScope {
    val ROOTS: Set<String> =
```

por:

```kotlin
// a qué proyecto pertenece un registro (domain-model §4.7): las raíces llevan `project`; los hijos lo heredan de su raíz
// por su relación; `project` es su propio proyecto. los objetos que también pueden ser del tenant (export_log y, desde F3,
// evidence y, desde F4, early_warning_rule) tienen `project` opcional y solo se acotan cuando lo traen. las fases siguientes agregan sus objetos aquí
object ProjectScope {
    val ROOTS: Set<String> =
```

En `src/main/kotlin/sgspe/scope/ProjectScope.kt`, reemplazar:

```kotlin
            "agreement",
            "task",
            "evidence"
        )

```

por:

```kotlin
            "agreement",
            "task",
            "evidence",
            // F4: early_warning_rule también puede ser del tenant (project opcional): una regla sin proyecto no se acota
            "grievance",
            "inquiry",
            "stakeholder_request",
            "social_risk",
            "potential_impact",
            "incident",
            "media_mention",
            "early_warning_signal",
            "early_warning_rule",
            "early_warning"
        )

```

En `src/main/kotlin/sgspe/scope/ProjectScope.kt`, reemplazar:

```kotlin
            "delivery" to ("commitment" to "commitment"),
            "agreement_party" to ("agreement" to "agreement"),
            "agreement_clause" to ("agreement" to "agreement")
        )

```

por:

```kotlin
            "delivery" to ("commitment" to "commitment"),
            "agreement_party" to ("agreement" to "agreement"),
            "agreement_clause" to ("agreement" to "agreement"),
            "risk_assessment" to ("social_risk" to "social_risk"),
            "mitigation_measure" to ("social_risk" to "social_risk")
        )

```

`CommitmentWriteGuard.kt` deja de declarar `NotReadable`, `readAs`, `readable` y `linkedProject`, y los importa:

En `src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt`, reemplazar:

```kotlin
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Records
import sgspe.common.RequestMemo
import sgspe.common.RuleGuard
import sgspe.common.toCodedException
import sgspe.scope.ProjectScope
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordWrite
```

por:

```kotlin
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.RequestMemo
import sgspe.common.RuleGuard
import sgspe.common.readAs
import sgspe.common.readable
import sgspe.common.toCodedException
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordWrite
```

En `src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt`, reemplazar:

```kotlin
}

// lo que una regla no puede leer como quien escribe (P12): un objeto sin READ es un bloqueo OBJECT_NOT_READABLE, nunca un
// pase en silencio
class NotReadable(
    val objectName: String
) : RuntimeException("sin lectura de $objectName")

// las familias de reglas y su lugar (CommitmentRule.order): alta y edición, aprobación (D1 a D5 y D8), cumplimiento y
// verificación (D9 a D11) y candidatos. dentro de una familia, se suma un desplazamiento (1, 2, …)
```

por:

```kotlin
}

// las familias de reglas y su lugar (CommitmentRule.order): alta y edición, aprobación (D1 a D5 y D8), cumplimiento y
// verificación (D9 a D11) y candidatos. dentro de una familia, se suma un desplazamiento (1, 2, …)
```

En `src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt`, reemplazar:

```kotlin
    const val CANDIDATE = 400
}

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

// el registro `id` de `objectName` leído como quien escribe. falla cerrada (P12): sin READ, fuera del alcance o inexistente es
// NotReadable, y el mensaje no distingue «no existe» de «no se lee» para no revelar si existe
suspend fun Records.readable(
    objectName: String,
    id: UUID
): RecordResponse = readAs(objectName) { find(objectName, id) } ?: throw NotReadable(objectName)

// los atributos del proyecto de un registro, para sus políticas (ASM-019): sin project, vacío (las políticas por defecto de la
```

por:

```kotlin
    const val CANDIDATE = 400
}

// los atributos del proyecto de un registro, para sus políticas (ASM-019): sin project, vacío (las políticas por defecto de la
```

En `src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt`, reemplazar:

```kotlin
        readAs("stakeholder") { related(Commitments.COMMITMENT, commitmentId, Commitments.STAKEHOLDERS) }
    }

// el proyecto del registro `id` de `target` (un objeto con alcance de proyecto) leído como quien escribe: el suyo si es una raíz,
// el de su padre si es un hijo (agreement_clause). lo usa ProjectLinks.judge (P54); un destino que no se lee es NotReadable
suspend fun Records.linkedProject(
    target: String,
    id: UUID
): UUID? {
    val record = readable(target, id)
    val child = ProjectScope.CHILDREN[target] ?: return Records.uuid(record.attributes["project"])
    val parent = Records.uuid(record.attributes[child.first]) ?: return null
    return Records.uuid(readable(child.second, parent).attributes["project"])
}

// CommitmentWriteGuard (sgspe.commitment): junta las reglas de compromisos, entregas, dependencias y candidatos. cada regla
```

por:

```kotlin
        readAs("stakeholder") { related(Commitments.COMMITMENT, commitmentId, Commitments.STAKEHOLDERS) }
    }

// CommitmentWriteGuard (sgspe.commitment): junta las reglas de compromisos, entregas, dependencias y candidatos. cada regla
```

Reescribir `src/main/kotlin/sgspe/commitment/ProjectLinkRule.kt` entero:

```kotlin
package sgspe.commitment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import sgspe.scope.ProjectLinks
import wasichai.core.data.RecordWrite

// PROJECT_MISMATCH (409) en el campo del compromiso vinculado a un registro de otro proyecto (ProjectLinks.judge, sgspe.scope).
// desde F4 los campos del catálogo suman origin_grievance y social_risk
@Component
class ProjectLinkRule(
    private val records: Records,
    private val view: WriteView,
    private val catalog: ModelCatalog
) : CommitmentRule {
    override val order = CommitmentRuleOrder.RECORD + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        ProjectLinks.judge(Commitments.COMMITMENT, ProjectLinks.fieldsOf(catalog, Commitments.COMMITMENT), emptySet(), change, records, view).map { field ->
            Finding("PROJECT_MISMATCH", "El registro vinculado en $field es de otro proyecto: un compromiso solo referencia lo de su proyecto (P54)", field)
        }
}
```

Los demás archivos que usaban las piezas movidas solo cambian sus imports:

En `src/main/kotlin/sgspe/commitment/ApprovalRules.kt`, reemplazar:

```kotlin
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
```

por:

```kotlin
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
```

En `src/main/kotlin/sgspe/commitment/CandidateRules.kt`, reemplazar:

```kotlin
import sgspe.common.Values
import sgspe.common.WriteView
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
```

por:

```kotlin
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.scope.ProjectLinks
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
```

En `src/main/kotlin/sgspe/commitment/Candidates.kt`, reemplazar:

```kotlin
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
```

por:

```kotlin
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.Records
```

En `src/main/kotlin/sgspe/commitment/Candidates.kt`, reemplazar:

```kotlin
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
```

por:

```kotlin
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.readable
import sgspe.common.toCodedException
import wasichai.core.common.Actions
```

En `src/main/kotlin/sgspe/commitment/CommitmentDerivations.kt`, reemplazar:

```kotlin
import sgspe.common.DerivedRecalculator
import sgspe.common.LIMA
import sgspe.common.Records
import sgspe.common.Transactions
```

por:

```kotlin
import sgspe.common.DerivedRecalculator
import sgspe.common.LIMA
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.Transactions
```

En `src/main/kotlin/sgspe/commitment/CommitmentRecordRules.kt`, reemplazar:

```kotlin
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.ProjectParameters
import sgspe.common.Records
```

por:

```kotlin
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.ProjectParameters
import sgspe.common.Records
```

En `src/main/kotlin/sgspe/commitment/CommitmentRecordRules.kt`, reemplazar:

```kotlin
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
```

por:

```kotlin
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
```

En `src/main/kotlin/sgspe/commitment/FulfillmentRules.kt`, reemplazar:

```kotlin
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.Readability
```

por:

```kotlin
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.Readability
```

En `src/main/kotlin/sgspe/commitment/FulfillmentRules.kt`, reemplazar:

```kotlin
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
```

por:

```kotlin
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.data.RecordWrite
import wasichai.core.metadata.MetadataService
```

En `src/main/kotlin/sgspe/commitment/SeniorApproval.kt`, reemplazar:

```kotlin

import org.springframework.stereotype.Component
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import java.util.UUID

```

por:

```kotlin

import org.springframework.stereotype.Component
import sgspe.common.NotReadable
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.readable
import java.util.UUID

```

En `src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt`, reemplazar:

```kotlin
import sgspe.commitment.CommitmentRuleOrder
import sgspe.commitment.Commitments
import sgspe.commitment.NotReadable
import sgspe.commitment.readAs
import sgspe.common.Amounts
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
```

por:

```kotlin
import sgspe.commitment.CommitmentRuleOrder
import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
```

En `src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt`, reemplazar:

```kotlin
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
```

por:

```kotlin
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
```

En `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`, reemplazar:

```kotlin
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.commitment.NotReadable
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.Values
```

por:

```kotlin
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.Values
```

En `src/test/kotlin/sgspe/commitment/BlockingDependencyRuleTest.kt`, reemplazar:

```kotlin
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Records
import wasichai.core.common.ForbiddenException
```

por:

```kotlin
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Records
import wasichai.core.common.ForbiddenException
```

En `src/test/kotlin/sgspe/commitment/CommitmentRecordRulesApiTest.kt`, reemplazar:

```kotlin
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest

// CommitmentWriteGuard, alta y edición (RN-ENG-001, RF-COM-003, RF-COM-004): por la API genérica, como el portal
```

por:

```kotlin
import org.springframework.http.HttpStatus
import sgspe.F3DomainApiTest
import sgspe.common.NotReadable

// CommitmentWriteGuard, alta y edición (RN-ENG-001, RF-COM-003, RF-COM-004): por la API genérica, como el portal
```

En `src/test/kotlin/sgspe/commitment/CommitmentRecordRulesTest.kt`, reemplazar:

```kotlin
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.Transactions
```

por:

```kotlin
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.Transactions
```

En `src/test/kotlin/sgspe/commitment/CommitmentWriteGuardTest.kt`, reemplazar:

```kotlin
import org.springframework.http.HttpStatus
import sgspe.common.Finding
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
```

por:

```kotlin
import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.NotReadable
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordChangeKind
```

En `src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt`, reemplazar:

```kotlin
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.commitment.NotReadable
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
```

por:

```kotlin
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.Records
```

En `src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt`, reemplazar:

```kotlin
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import sgspe.commitment.NotReadable
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
import sgspe.scope.Assignments
import tools.jackson.databind.json.JsonMapper
```

por:

```kotlin
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import sgspe.commitment.SeniorApprovalReader
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.scope.Assignments
import tools.jackson.databind.json.JsonMapper
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
grep -rn 'sgspe.commitment.NotReadable\|sgspe.commitment.readAs\|sgspe.commitment.ProjectLinks' src || echo limpio
```

Esperado: `BUILD SUCCESSFUL` (con `ArchitectureTest`: `scope` sigue dependiendo solo de `common`) y `limpio`.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/ReadAs.kt src/main/kotlin/sgspe/scope/ProjectLinks.kt src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt \
  src/main/kotlin/sgspe/scope/ProjectScope.kt src/main/kotlin/sgspe/commitment/CommitmentWriteGuard.kt \
  src/main/kotlin/sgspe/commitment/ProjectLinkRule.kt src/main/kotlin/sgspe/commitment/ApprovalRules.kt \
  src/main/kotlin/sgspe/commitment/CandidateRules.kt src/main/kotlin/sgspe/commitment/Candidates.kt \
  src/main/kotlin/sgspe/commitment/CommitmentDerivations.kt src/main/kotlin/sgspe/commitment/CommitmentRecordRules.kt \
  src/main/kotlin/sgspe/commitment/FulfillmentRules.kt src/main/kotlin/sgspe/commitment/SeniorApproval.kt \
  src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt src/main/kotlin/sgspe/notification/ApprovalNotices.kt \
  src/test/kotlin/sgspe/commitment/ProjectLinksTest.kt src/test/kotlin/sgspe/scope/ProjectLinksTest.kt \
  src/test/kotlin/sgspe/scope/ProjectScopeF4Test.kt src/test/kotlin/sgspe/scope/ProjectLinkGuardApiTest.kt \
  src/test/kotlin/sgspe/commitment/BlockingDependencyRuleTest.kt src/test/kotlin/sgspe/commitment/CommitmentRecordRulesApiTest.kt \
  src/test/kotlin/sgspe/commitment/CommitmentRecordRulesTest.kt src/test/kotlin/sgspe/commitment/CommitmentWriteGuardTest.kt \
  src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt
git commit -F - <<'MSG'
feat: alcance de los objetos de F4 y regla de mismo proyecto

ProjectScope suma los doce objetos de F4; ProjectLinkGuard rechaza con 409 PROJECT_MISMATCH toda
relación de un objeto de F4 hacia otro proyecto, también los vínculos con grupos de interés.
ProjectLinks pasa a sgspe.scope y NotReadable y readAs a sgspe.common; el compromiso juzga ahora su
queja y su riesgo de origen.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.scope.ProjectLinkGuardApiTest` y, porque cambian sus imports,
`sgspe.commitment.CommitmentRecordRulesApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 5: La confidencialidad de las QCS (`ConfidentialAttentionScope`)

**Files:**
- Create: `src/main/kotlin/sgspe/scope/ConfidentialAttentionScope.kt`
- Modify: `src/main/kotlin/sgspe/common/Permissions.kt` (`confidentialHidden`)
- Test: `src/test/kotlin/sgspe/scope/ConfidentialAttentionScopeTest.kt`, `src/test/kotlin/sgspe/scope/ConfidentialAttentionApiTest.kt`

**Interfaces:**
- Consumes: `wasichai.core.data.RecordReadScope` y `RecordCriterion` (WAS-ADR-0048), `Permissions.has`, `RequestMemo`,
  `Attention`, `SqlIdentifier`.
- Produces: `object ConfidentialCondition` (`NOTHING`, `FIELDS`, `condition(objectName, columns, user, readConfidential,
  approve, bind): String?`); el bean `ConfidentialAttentionScope : RecordReadScope`;
  `Permissions.confidentialHidden(user: AuthenticatedUser): Boolean` (verdadero si no es ADMIN y le falta
  `READ_CONFIDENTIAL` sobre alguna de las tres QCS), que usan el reporte, el tablero y la exportación.

Es la primera tarea de integración de la confidencialidad (riesgo 1 de la especificación): la prueba de integración
comprueba que wasichai aplica el criterio en lista, detalle, historial, transiciones y relacionados, que quien no la ve no
la cambia ni la transiciona y que el alta devuelve el registro a quien la recibió. Si la plataforma difiere en algún
punto, se registra en el PR y `AttentionWriteGuard` (Tarea 6) suma la regla que falte.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/scope/ConfidentialAttentionScopeTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

// la condición de las QCS confidenciales por caso (security.md §9.6), sin base: qué lee cada quien y que falla cerrada
class ConfidentialAttentionScopeTest {
    private val user = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val columns =
        mapOf(
            "confidential" to "\"confidential\"",
            "owner_user_id" to "\"owner_user_id\"",
            "received_by_user_id" to "\"received_by_user_id\"",
            "severity" to "\"severity\""
        )

    private fun condition(
        objectName: String,
        readConfidential: Boolean = false,
        approve: Boolean = false,
        columns: Map<String, String?> = this.columns
    ): Pair<String?, List<Any>> {
        val bound = mutableListOf<Any>()
        val text =
            ConfidentialCondition.condition(objectName, columns, user, readConfidential, approve) { value ->
                bound += value
                ":c${bound.size - 1}"
            }
        return text to bound
    }

    @Test
    fun `fuera de las QCS o con READ_CONFIDENTIAL no restringe`() {
        assertNull(condition("commitment").first)
        assertNull(condition("grievance", readConfidential = true).first)
        assertNull(condition("stakeholder_request", readConfidential = true).first)
    }

    @Test
    fun `los demas ven la no confidencial, la suya como responsable y la que recibieron`() {
        val (text, bound) = condition("inquiry")
        assertEquals("\"confidential\" IS NOT TRUE OR \"owner_user_id\" = :c0 OR \"received_by_user_id\" = :c1", text)
        assertEquals(listOf<Any>(user, user), bound)
    }

    @Test
    fun `con APPROVE una queja ve ademas las ALTA o CRITICA, y las otras QCS no`() {
        val (text, bound) = condition("grievance", approve = true)
        assertEquals(
            "\"confidential\" IS NOT TRUE OR \"owner_user_id\" = :c0 OR \"received_by_user_id\" = :c1 OR \"severity\" = ANY(:c2)",
            text
        )
        assertEquals(listOf("ALTA", "CRITICA"), (bound[2] as Array<*>).toList())
        assertEquals(2, condition("stakeholder_request", approve = true).second.size)
    }

    @Test
    fun `sin una columna no admite ningun registro`() {
        assertEquals("false", condition("grievance", columns = columns - "received_by_user_id").first)
        assertEquals("false", condition("grievance", approve = true, columns = columns + ("severity" to null)).first)
    }
}
```

Crear `src/test/kotlin/sgspe/scope/ConfidentialAttentionApiTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el RecordReadScope de las QCS confidenciales en las rutas de wasichai (security.md §9.6, riesgo 1 de la especificación de
// F4): un relacionista que no es responsable ni la recibió no ve una QCS confidencial en lista, detalle, historial,
// relacionados ni transiciones, y no la cambia ni la transiciona; su responsable, quien la recibió, el gestor de quejas y el
// ADMIN sí; el aprobador solo la queja grave. el alta de una confidencial devuelve el registro a quien la recibió. los
// tableros, el reporte, las exportaciones y los avisos tienen sus propias pruebas
class ConfidentialAttentionApiTest : F4ApiTest() {
    private val receiver by lazy { member("COMMUNITY_RELATIONS_OFFICER") }
    private val colleague by lazy { member("COMMUNITY_RELATIONS_OFFICER") }

    private fun confidential(
        severity: String = "BAJA",
        attributes: Map<String, Any?> = emptyMap()
    ): String = grievance(testProject(), receiver, mapOf("confidential" to true, "severity" to severity) + attributes)

    private fun listed(
        objectName: String,
        account: Account
    ): Set<String> =
        tree(send("GET", "/api/objects/$objectName/records?project=${testProject()}&size=200", null, HttpStatus.OK, account.token))["content"]
            .values()
            .map { it["id"].asString() }
            .toSet()

    @Test
    fun `quien la recibio la ve desde el alta, y otro relacionista no la ve por ninguna ruta`() {
        val id = confidential()
        val open = grievance(testProject(), receiver)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/grievance/records/$id", null, receiver.token).status)
        assertTrue(id in listed("grievance", receiver))
        assertEquals(setOf(open), listed("grievance", colleague) intersect setOf(id, open))
        for (path in listOf(
            "/api/objects/grievance/records/$id",
            "/api/objects/grievance/records/$id/history",
            "/api/objects/grievance/records/$id/transitions"
        )) {
            assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, colleague.token).status, path)
        }
        val related =
            tree(send("GET", "/api/objects/project/records/${testProject()}/related/grievance_project?size=200", null, HttpStatus.OK, colleague.token))
        assertFalse(related["content"].values().any { it["id"].asString() == id })
    }

    @Test
    fun `quien no la ve no la cambia ni la transiciona`() {
        val id = confidential()
        assertEquals(HttpStatus.NOT_FOUND, transitionWith("grievance", id, "acknowledge", colleague.token, reason = "acuse").status)
        val put = exchange("PUT", "/api/objects/grievance/records/$id", mapOf("attributes" to mapOf("priority" to "ALTA")), colleague.token, withReason())
        assertEquals(HttpStatus.NOT_FOUND, put.status, put.body)
        assertEquals("received", state("grievance", id))
    }

    @Test
    fun `su responsable, el gestor de quejas y el ADMIN la ven`() {
        val owner = member("COMMUNITY_RELATIONS_OFFICER")
        val id = confidential(attributes = mapOf("owner_user_id" to owner.id, "owner_user_email" to owner.email))
        assertTrue(id in listed("grievance", owner))
        assertTrue(id in listed("grievance", member("GRIEVANCE_MANAGER")))
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/grievance/records/$id", null, token).status)
    }

    @Test
    fun `el aprobador solo ve la queja confidencial grave`() {
        val low = confidential("MEDIA")
        val high = confidential("ALTA")
        val approver = member("APPROVER")
        val seen = listed("grievance", approver)
        assertTrue(high in seen)
        assertFalse(low in seen)
        // una consulta confidencial no tiene severidad: el aprobador no la ve
        val inquiry = inquiry(testProject(), receiver, mapOf("confidential" to true))
        assertFalse(inquiry in listed("inquiry", approver))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.ConfidentialAttentionScopeTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ConfidentialCondition'`).

- [ ] **Paso 3: El alcance de lectura y `confidentialHidden`**

Crear `src/main/kotlin/sgspe/scope/ConfidentialAttentionScope.kt`:

```kotlin
package sgspe.scope

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.common.Attention
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
        return RecordCriterion { _, bind ->
            ConfidentialCondition.condition(objectName, columns, caller.userId, readConfidential = false, approve, bind) ?: ConfidentialCondition.NOTHING
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
```

En `src/main/kotlin/sgspe/common/Permissions.kt`, reemplazar:

```kotlin
        }

    private fun label(action: String): String =
        when (action) {
```

por:

```kotlin
        }

    // si a quien llama se le ocultan QCS confidenciales (security.md §9.6): no es ADMIN ni tiene READ_CONFIDENTIAL sobre los tres
    // objetos. el tablero attention, los KPI-GRV, KPI-ENG-05 y el reporte lo dicen con confidentialHidden, un booleano sin
    // el número de excluidas
    suspend fun confidentialHidden(user: AuthenticatedUser): Boolean = !user.isAdmin && Attention.OBJECTS.any { !has(user, Attention.READ_CONFIDENTIAL, it) }

    private fun label(action: String): String =
        when (action) {
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ConfidentialAttentionScopeTest` con 4 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/scope/ConfidentialAttentionScope.kt src/main/kotlin/sgspe/common/Permissions.kt \
  src/test/kotlin/sgspe/scope/ConfidentialAttentionScopeTest.kt src/test/kotlin/sgspe/scope/ConfidentialAttentionApiTest.kt
git commit -F - <<'MSG'
feat: alcance de lectura de las QCS confidenciales

ConfidentialAttentionScope implementa el RecordReadScope de wasichai para las tres QCS: sin
READ_CONFIDENTIAL, una confidencial solo la ven su responsable, quien la recibió y, en una queja
grave, quien tiene APPROVE; falla cerrado (security.md §9.6, ASM-095).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.scope.ConfidentialAttentionApiTest` (en la CI del PR, con
Testcontainers). Es la prueba del riesgo 1: si falla, se detiene el plan y se decide antes de la Tarea 6.

---

### Tarea 6: `AttentionWriteGuard`

**Files:**
- Create: `src/main/kotlin/sgspe/attention/AttentionRules.kt`, `src/main/kotlin/sgspe/attention/AttentionWriteGuard.kt`
- Test: `src/test/kotlin/sgspe/attention/AttentionRulesTest.kt`, `src/test/kotlin/sgspe/attention/GrievanceWorkflowApiTest.kt`

**Interfaces:**
- Consumes: `Attention`, `ProjectParameters.policy`, `Permissions.has`, `Callers`, `WriteView`, `readable`, `NotReadable`.
- Produces: `object AttentionRules` (`ATTEND`, `RESOLVE`, `RESPONDED_AT`, `RESPONSE`, `REOPEN`, `RESOLVE_PLAIN`,
  `RESOLVE_HIGH`, `CLOSE_UNFULFILLED`, `CONFORMITY`, `DUPLICATE_OF`, `CREATED_COMMITMENT`, `IDENTITY`,
  `record(objectName, recordId, after)`, `transition(objectName, transition, record)`, `grave(record)`,
  `protectedChanges(before, after)`, `foreignReceiver(after, writer)`); el bean `AttentionWriteGuard`
  (`@Order(GuardOrder.DOMAIN)`, `POLICY`, `COMMITMENT`, `CANCELLED`).

Las once reglas de la tabla de §3.2 de la especificación, con su código y su campo. Las de datos son puras
(`AttentionRules`); la guarda suma las que miran permisos (`READ_CONFIDENTIAL`, `APPROVE`) o leen otro registro como quien
escribe (la política del proyecto, el compromiso creado). La doble llave de `resolve_high_severity` solo se prueba en
seco: ningún rol de `roles.json` tiene la transición sin `APPROVE`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/attention/AttentionRulesTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.util.UUID

// las reglas de datos de las QCS (especificación de F4 §3.2), sin base: el código y el campo de cada una
class AttentionRulesTest {
    private val id = UUID.randomUUID()
    private val writer = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()

    private fun codes(findings: List<sgspe.common.Finding>) = findings.map { it.code to it.field }

    @Test
    fun `una QCS anonima no lleva persona ni grupo de interes`() {
        assertEquals(listOf("ANONYMOUS_WITH_IDENTITY" to "person"), codes(AttentionRules.record("inquiry", null, mapOf("anonymous" to true, "person" to "p1"))))
        assertEquals(
            listOf("ANONYMOUS_WITH_IDENTITY" to "stakeholder"),
            codes(
                AttentionRules.record(
                    "grievance",
                    id,
                    mapOf(
                        "anonymous" to true,
                        "stakeholder" to "s1"
                    )
                )
            )
        )
        assertEquals(emptyList<Pair<String, String?>>(), codes(AttentionRules.record("grievance", id, mapOf("anonymous" to false, "person" to "p1"))))
    }

    @Test
    fun `una queja no es duplicado de si misma`() {
        assertEquals(listOf("INVALID_LINK" to "duplicate_of"), codes(AttentionRules.record("grievance", id, mapOf("duplicate_of" to id.toString()))))
    }

    @Test
    fun `atender y resolver una queja exigen sus datos`() {
        assertEquals(listOf("FIELD_REQUIRED" to "severity"), codes(AttentionRules.transition("grievance", "start_action", mapOf("category" to "EMPLEO"))))
        assertEquals(listOf("FIELD_REQUIRED" to "category"), codes(AttentionRules.transition("grievance", "investigate", mapOf("severity" to "BAJA"))))
        assertEquals(
            listOf("FIELD_REQUIRED" to "resolution", "FIELD_REQUIRED" to "response"),
            codes(AttentionRules.transition("grievance", "resolve_high_severity", emptyMap()))
        )
    }

    @Test
    fun `las transiciones que comunican una respuesta exigen response y responded_at`() {
        assertEquals(
            listOf("FIELD_REQUIRED" to "response", "FIELD_REQUIRED" to "responded_at"),
            codes(AttentionRules.transition("grievance", "reject_received", emptyMap()))
        )
        assertEquals(listOf("FIELD_REQUIRED" to "responded_at"), codes(AttentionRules.transition("inquiry", "close_referred", mapOf("response" to "x"))))
        assertEquals(listOf("FIELD_REQUIRED" to "response"), codes(AttentionRules.transition("stakeholder_request", "decline", emptyMap())))
        assertEquals(
            emptyList<Pair<String, String?>>(),
            codes(
                AttentionRules.transition(
                    "inquiry",
                    "answer",
                    mapOf(
                        "response" to "x",
                        "responded_at" to "2026-10-08T15:00:00Z"
                    )
                )
            )
        )
    }

    @Test
    fun `no se reabre una queja con conformidad`() {
        assertEquals(
            listOf("CONFORMITY_GIVEN" to "complainant_conformity_given"),
            codes(
                AttentionRules.transition(
                    "grievance",
                    "reopen",
                    mapOf("complainant_conformity_given" to true)
                )
            )
        )
        assertEquals(
            emptyList<Pair<String, String?>>(),
            codes(AttentionRules.transition("grievance", "reopen", mapOf("complainant_conformity_given" to false)))
        )
    }

    @Test
    fun `la confidencialidad protegida y el receptor de una confidencial`() {
        val before = mapOf("confidential" to true, "owner_user_id" to "a", "received_by_user_id" to "b")
        assertEquals(listOf("confidential"), AttentionRules.protectedChanges(before, before + ("confidential" to false)))
        assertEquals(
            listOf("owner_user_id", "received_by_user_id"),
            AttentionRules.protectedChanges(
                before,
                before + mapOf("owner_user_id" to "c", "received_by_user_id" to null)
            )
        )
        assertEquals(emptyList<String>(), AttentionRules.protectedChanges(before + ("confidential" to false), mapOf("owner_user_id" to "c")))
        assertTrue(AttentionRules.foreignReceiver(mapOf("confidential" to true, "received_by_user_id" to null), writer))
        assertEquals(false, AttentionRules.foreignReceiver(mapOf("confidential" to true, "received_by_user_id" to writer.toString()), writer))
        assertEquals(false, AttentionRules.foreignReceiver(mapOf("confidential" to false), writer))
    }

    // las tablas nombran transiciones por texto: si el modelo renombra una, la regla dejaría de vigilarla en silencio
    @Test
    fun `cada transicion de las reglas existe en su workflow`() {
        val named =
            AttentionRules.RESPONDED_AT.flatMap { (o, ts) -> ts.map { o to it } } + AttentionRules.RESPONSE.flatMap { (o, ts) -> ts.map { o to it } } +
                (AttentionRules.ATTEND + AttentionRules.RESOLVE + AttentionRules.REOPEN).map { "grievance" to it } +
                ("stakeholder_request" to AttentionRules.CLOSE_UNFULFILLED)
        assertEquals(emptyList<Pair<String, String>>(), named.filter { (o, t) -> catalog.transition(o, t) == null })
        assertTrue(AttentionWriteGuard.POLICY in catalog.objectOrNull("project")!!.fields)
    }
}
```

Crear `src/test/kotlin/sgspe/attention/GrievanceWorkflowApiTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-E1 con AttentionWriteGuard (workflows.md, especificación de F4 §3.2): los casos propios de la queja que la matriz de
// workflows no cubre. la anónima sin identidad, la confidencialidad (receptor y marca protegida), atender y resolver con
// datos, la queja grave con su doble llave, la reapertura sin conformidad, la respuesta comunicada y el duplicado
class GrievanceWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("GRIEVANCE_MANAGER") }
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER") }

    private fun create(
        attributes: Map<String, Any?>,
        account: Account? = null
    ): Response =
        exchange(
            "POST",
            "/api/objects/grievance/records",
            mapOf("attributes" to attentionAttributes(testProject(), account, "QJ-") + attributes),
            account?.token ?: token,
            withReason("alta de prueba")
        )

    // una queja llevada por el gestor de quejas hasta in_action
    private fun inAction(attributes: Map<String, Any?> = emptyMap()): String {
        val id = grievance(testProject(), attributes = attributes)
        for (step in listOf("acknowledge", "assess", "start_action")) move("grievance", id, step, manager.token, step)
        return id
    }

    @Test
    fun `una queja anonima no lleva persona`() {
        val response = create(mapOf("anonymous" to true, "person" to person()))
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("ANONYMOUS_WITH_IDENTITY", code(response))
        assertEquals(listOf("person"), problemFields(response.body))
    }

    @Test
    fun `una confidencial la recibe quien la registra, salvo con READ_CONFIDENTIAL`() {
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val foreign = mapOf("confidential" to true, "received_by_user_id" to other.id, "received_by_user_email" to other.email)
        val response = create(foreign + mapOf("code" to uniqueName("QJ-")), officer)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("CONFIDENTIAL_RECEIVER_REQUIRED", code(response))
        assertEquals(listOf("received_by_user_id"), problemFields(response.body))
        assertEquals(HttpStatus.CREATED, create(foreign, manager).status)
    }

    @Test
    fun `quitar la marca o cambiar el responsable de una confidencial exige READ_CONFIDENTIAL`() {
        val id = grievance(testProject(), officer, mapOf("confidential" to true))
        val response = edit("grievance", id, mapOf("confidential" to false), officer.token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
        assertEquals("ACTION_REQUIRED", code(response))
        assertEquals(listOf("confidential"), problemFields(response.body))
        val owner = edit("grievance", id, mapOf("owner_user_id" to manager.id, "owner_user_email" to manager.email), officer.token, withReason())
        assertEquals(listOf("owner_user_id"), problemFields(owner.body))
        assertEquals(HttpStatus.OK, edit("grievance", id, mapOf("confidential" to false), manager.token, withReason()).status)
    }

    @Test
    fun `atender exige la severidad y resolver la resolucion`() {
        val id = grievance(testProject(), attributes = mapOf("severity" to null))
        for (step in listOf("acknowledge", "assess")) move("grievance", id, step, manager.token, step)
        val attend = transition("grievance", id, "start_action", manager.token, "atender")
        assertEquals(HttpStatus.BAD_REQUEST, attend.status, attend.body)
        assertEquals("FIELD_REQUIRED", code(attend))
        assertEquals(listOf("severity"), problemFields(attend.body))
        val unresolved = inAction(mapOf("resolution" to null))
        assertEquals(listOf("resolution"), problemFields(transition("grievance", unresolved, "resolve", manager.token, "resolver").body))
    }

    @Test
    fun `una queja grave la resuelve quien tiene APPROVE, salvo con la politica apagada`() {
        val id = inAction(mapOf("severity" to "ALTA"))
        val response = transition("grievance", id, "resolve", manager.token, "resolver")
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("SENIOR_APPROVAL_REQUIRED", code(response))
        move("grievance", id, "resolve_high_severity", member("APPROVER").token, "resolución de la queja grave")
        assertEquals("resolved", state("grievance", id))
        val relaxed = project(mapOf("policy_high_severity_requires_approver" to false))
        val gvm = member("GRIEVANCE_MANAGER", relaxed)
        val other = grievance(relaxed, attributes = mapOf("severity" to "CRITICA"))
        for (step in listOf("acknowledge", "assess", "start_action", "resolve")) move("grievance", other, step, gvm.token, step)
    }

    @Test
    fun `no se reabre una queja con conformidad`() {
        val id = inAction(mapOf("complainant_conformity_given" to true))
        move("grievance", id, "resolve", manager.token, "resolver")
        val response = transition("grievance", id, "reopen", manager.token, "reabrir")
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("CONFORMITY_GIVEN", code(response))
    }

    @Test
    fun `rechazar exige la respuesta comunicada`() {
        val id = grievance(testProject(), attributes = mapOf("responded_at" to null))
        val response = transition("grievance", id, "reject_received", manager.token, "rechazo")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("responded_at"), problemFields(response.body))
    }

    @Test
    fun `una queja no es duplicado de si misma`() {
        val id = grievance(testProject())
        val response = edit("grievance", id, mapOf("duplicate_of" to id), token, withReason())
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("INVALID_LINK", code(response))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.attention.AttentionRulesTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'AttentionRules'` y `'AttentionWriteGuard'`).

- [ ] **Paso 3: Las reglas y la guarda**

Crear `src/main/kotlin/sgspe/attention/AttentionRules.kt`:

```kotlin
package sgspe.attention

import org.springframework.http.HttpStatus
import sgspe.common.Attention
import sgspe.common.Finding
import sgspe.common.Values
import java.util.UUID

// las reglas de datos de las QCS (workflows.md WF-E1, WF-E2 y WF-E3; especificación de F4 §3.2), puras: lo que se decide con el
// registro antes y después de la escritura, sin lecturas ni permisos. AttentionWriteGuard las corre y suma las que leen o
// miran permisos (confidencialidad, queja grave, doble llave y cierre sin ejecución)
object AttentionRules {
    // atender una queja exige su categoría y su severidad
    val ATTEND = setOf("investigate", "start_action", "start_action_after_investigation")

    // resolverla exige la resolución y la respuesta
    val RESOLVE = setOf("resolve", "resolve_high_severity")

    // las transiciones que comunican una respuesta: exigen responded_at (cuenta como absuelta, RQ-DSH-023) y/o response
    val RESPONDED_AT: Map<String, Set<String>> =
        mapOf(
            Attention.GRIEVANCE to setOf("close_resolved", "close", "reject_received", "reject_acknowledged", "reject_in_assessment"),
            Attention.INQUIRY to setOf("answer", "close_referred"),
            Attention.REQUEST to setOf("accept", "close_declined")
        )
    val RESPONSE: Map<String, Set<String>> =
        mapOf(
            Attention.GRIEVANCE to setOf("reject_received", "reject_acknowledged", "reject_in_assessment"),
            Attention.INQUIRY to setOf("answer", "refer"),
            Attention.REQUEST to setOf("decline", "close_unfulfilled", "accept")
        )

    const val REOPEN = "reopen"
    const val RESOLVE_PLAIN = "resolve"
    const val RESOLVE_HIGH = "resolve_high_severity"
    const val CLOSE_UNFULFILLED = "close_unfulfilled"
    const val CONFORMITY = "complainant_conformity_given"
    const val DUPLICATE_OF = "duplicate_of"
    const val CREATED_COMMITMENT = "created_commitment"

    // los campos de identidad que una QCS anónima no lleva (RF-GRV-001, GAP-005)
    val IDENTITY = listOf("person", "stakeholder")

    // el alta o el cambio: anónima sin identidad y una queja que no es duplicado de sí misma
    fun record(
        objectName: String,
        recordId: UUID?,
        after: Map<String, Any?>
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        if (Values.bool(after["anonymous"]) == true) {
            IDENTITY.firstOrNull { Values.text(after[it]) != null }?.let { field ->
                findings +=
                    Finding(
                        "ANONYMOUS_WITH_IDENTITY",
                        "Una QCS anónima no lleva $field: quite la identidad o desmarque anonymous",
                        field,
                        status = HttpStatus.BAD_REQUEST
                    )
            }
        }
        if (objectName == Attention.GRIEVANCE && recordId != null && Values.text(after[DUPLICATE_OF]) == recordId.toString()) {
            findings += Finding("INVALID_LINK", "Una queja no es duplicado de sí misma", DUPLICATE_OF, status = HttpStatus.BAD_REQUEST)
        }
        return findings
    }

    // una transición con el registro como está guardado: los datos que cada transición exige
    fun transition(
        objectName: String,
        transition: String,
        record: Map<String, Any?>
    ): List<Finding> {
        val required = mutableListOf<String>()
        if (objectName == Attention.GRIEVANCE && transition in ATTEND) required += listOf("category", Attention.SEVERITY)
        if (objectName == Attention.GRIEVANCE && transition in RESOLVE) required += listOf("resolution", "response")
        if (transition in RESPONSE[objectName].orEmpty()) required += "response"
        if (transition in RESPONDED_AT[objectName].orEmpty()) required += Attention.RESPONDED_AT
        val findings =
            required.distinct().filter { Values.text(record[it]) == null }.mapTo(mutableListOf()) { field ->
                Finding("FIELD_REQUIRED", "La transición $transition exige $field", field, status = HttpStatus.BAD_REQUEST)
            }
        if (objectName == Attention.GRIEVANCE && transition == REOPEN && Values.bool(record[CONFORMITY]) == true) {
            findings += Finding("CONFORMITY_GIVEN", "No se reabre una queja con la conformidad de quien la planteó", CONFORMITY)
        }
        return findings
    }

    // una queja grave: ALTA o CRITICA (RN-GRV-002)
    fun grave(record: Map<String, Any?>): Boolean = Values.text(record[Attention.SEVERITY]) in Attention.HIGH_SEVERITY

    // la confidencialidad que una escritura cambia sin READ_CONFIDENTIAL: quitar la marca, o cambiar el responsable o quien la
    // recibió de una QCS confidencial (ASM-095). `before` vacío: un alta, que juzga confidentialReceiver
    fun protectedChanges(
        before: Map<String, Any?>,
        after: Map<String, Any?>
    ): List<String> {
        if (!Attention.isConfidential(before)) return emptyList()
        val fields = mutableListOf<String>()
        if (!Attention.isConfidential(after)) fields += Attention.CONFIDENTIAL
        for (field in listOf(Attention.OWNER, Attention.RECEIVER)) if (!Values.same(before[field], after[field])) fields += field
        return fields
    }

    // el alta de una confidencial cuyo receptor no es quien escribe (ASM-095): exige READ_CONFIDENTIAL
    fun foreignReceiver(
        after: Map<String, Any?>,
        writer: UUID
    ): Boolean = Attention.isConfidential(after) && Values.text(after[Attention.RECEIVER]) != writer.toString()
}
```

Crear `src/main/kotlin/sgspe/attention/AttentionWriteGuard.kt`:

```kotlin
package sgspe.attention

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// AttentionWriteGuard (sgspe.attention, especificación de F4 §3.2): las reglas de las QCS por cualquier puerta (la API
// genérica, los comandos y la consulta en seco). las de datos son AttentionRules; aquí se suman las que miran permisos o leen
// otros registros como quien escribe: la confidencialidad (READ_CONFIDENTIAL, ASM-095), la queja grave
// (policy_high_severity_requires_approver del proyecto, RN-GRV-002), la doble llave de resolve_high_severity (APPROVE) y el
// cierre sin ejecución (close_unfulfilled con el compromiso cancelado). una escritura sin usuario (la plataforma, una
// automatización) no tiene permisos que comprobar. lo que no se puede leer es 403 OBJECT_NOT_READABLE, nunca un pase
@Component
@Order(GuardOrder.DOMAIN)
class AttentionWriteGuard(
    private val records: Records,
    private val view: WriteView,
    private val callers: Callers,
    private val permissions: Permissions
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in Attention.OBJECTS) return emptyList()
        return try {
            when {
                change.kind == RecordChangeKind.TRANSITIONED -> transition(change)
                (change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) && !change.isLink() -> record(change)
                else -> emptyList()
            }
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName}: el registro no existe o quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
    }

    private suspend fun record(change: RecordWrite): List<Finding> {
        val after = view.after(change)
        val findings = AttentionRules.record(change.objectName, change.recordId, after).toMutableList()
        val writer = change.userId ?: return findings
        if (change.kind == RecordChangeKind.CREATED) {
            if (AttentionRules.foreignReceiver(after, writer) && !holds(Attention.READ_CONFIDENTIAL, change.objectName)) {
                findings +=
                    Finding(
                        "CONFIDENTIAL_RECEIVER_REQUIRED",
                        "Una QCS confidencial la recibe quien la registra: received_by_user_id debe ser quien escribe",
                        Attention.RECEIVER,
                        status = HttpStatus.BAD_REQUEST
                    )
            }
            return findings
        }
        val protected = AttentionRules.protectedChanges(change.before.orEmpty(), after)
        if (protected.isNotEmpty() && !holds(Attention.READ_CONFIDENTIAL, change.objectName)) {
            findings +=
                protected.map { field ->
                    Finding(
                        "ACTION_REQUIRED",
                        "Cambiar $field de una QCS confidencial exige la acción READ_CONFIDENTIAL sobre ${change.objectName}",
                        field,
                        status = HttpStatus.FORBIDDEN
                    )
                }
        }
        return findings
    }

    private suspend fun transition(change: RecordWrite): List<Finding> {
        val name = change.transition ?: return emptyList()
        val record = change.before.orEmpty()
        val findings = AttentionRules.transition(change.objectName, name, record).toMutableList()
        if (findings.any { it.blocking }) return findings
        if (change.objectName == Attention.GRIEVANCE && name == AttentionRules.RESOLVE_PLAIN && AttentionRules.grave(record)) {
            val project = Records.uuid(record["project"])?.let { records.readable("project", it).attributes }.orEmpty()
            if (ProjectParameters.policy(project, POLICY)) {
                findings +=
                    Finding(
                        "SENIOR_APPROVAL_REQUIRED",
                        "Una queja ${Values.text(record[Attention.SEVERITY])} se resuelve con resolve_high_severity, por quien tiene APPROVE (RN-GRV-002)",
                        status = HttpStatus.CONFLICT
                    )
            }
        }
        if (change.objectName == Attention.GRIEVANCE && name == AttentionRules.RESOLVE_HIGH && change.userId != null &&
            !holds(Attention.APPROVE, Attention.GRIEVANCE)
        ) {
            findings += Finding("ACTION_REQUIRED", "Resolver una queja grave exige la acción APPROVE sobre grievance", status = HttpStatus.FORBIDDEN)
        }
        if (change.objectName == Attention.REQUEST && name == AttentionRules.CLOSE_UNFULFILLED) {
            val commitment = Records.uuid(record[AttentionRules.CREATED_COMMITMENT])?.let { records.readable(COMMITMENT, it) }
            if (commitment?.state != CANCELLED) {
                findings +=
                    Finding(
                        "STATE_MISMATCH",
                        "close_unfulfilled cierra una solicitud cuyo compromiso está cancelado; el suyo está en ${commitment?.state ?: "ninguno"}",
                        AttentionRules.CREATED_COMMITMENT
                    )
            }
        }
        return findings
    }

    // las acciones de quien escribe; sin usuario identificable, no las tiene (falla cerrada)
    private suspend fun holds(
        action: String,
        objectName: String
    ): Boolean {
        val user = callers.userOrNull() ?: return false
        return permissions.has(user, action, objectName)
    }

    companion object {
        const val POLICY = "policy_high_severity_requires_approver"
        const val COMMITMENT = "commitment"
        const val CANCELLED = "cancelled"
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `AttentionRulesTest` con 7 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/AttentionRules.kt src/main/kotlin/sgspe/attention/AttentionWriteGuard.kt \
  src/test/kotlin/sgspe/attention/AttentionRulesTest.kt src/test/kotlin/sgspe/attention/GrievanceWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: AttentionWriteGuard para quejas, consultas y solicitudes

Las reglas de WF-E1, WF-E2 y WF-E3: anónima sin identidad, receptor y marca de una confidencial,
atender y resolver con datos, queja grave con su doble llave, reapertura sin conformidad,
respuesta comunicada, duplicado y cierre sin ejecución, con su código y su campo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.attention.GrievanceWorkflowApiTest` y `sgspe.workflow.WorkflowMatrixApiTest`
(sus QCS ya traen lo que la guarda pide: `F4ApiTest`) (en la CI del PR, con Testcontainers).

---

### Tarea 7: Las evidencias de una QCS confidencial

**Files:**
- Modify: `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`
- Test: `src/test/kotlin/sgspe/evidence/EvidenceQcsLinksApiTest.kt`

**Interfaces:**
- Consumes: `Attention`, `EvidenceRules.restricted` y `linkRelationship`; `Records.find` como quien vincula.
- Produces: `EvidenceWriteGuard` con un `Records` más en su constructor y el `409 EVIDENCE_NOT_RESTRICTED` sobre
  `classification`.

Los vínculos `evidence_grievance`, `evidence_inquiry` y `evidence_stakeholder_request` ya existen en el modelo (plan 4.1),
así que `POST /api/sgspe/evidence/{id}/links` los acepta sin cambios; esta tarea solo suma la regla de la especificación
§3.1. wasichai llama a las guardas con cada extremo del vínculo: la regla se juzga en el de la evidencia.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/evidence/EvidenceQcsLinksApiTest.kt`:

```kotlin
package sgspe.evidence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// las evidencias de una QCS (api.md §4.9, especificación de F4 §3.1): el cargo y la carta firmados se suben como evidencia y
// se vinculan a la queja, la consulta o la solicitud; a una confidencial solo se vincula una evidencia CONFIDENCIAL o DATOS
// PERSONALES (409 EVIDENCE_NOT_RESTRICTED)
class EvidenceQcsLinksApiTest : F4ApiTest() {
    private fun linkTo(
        evidence: String,
        objectName: String,
        recordId: String,
        account: Account
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/evidence/$evidence/links",
            mapOf("object" to objectName, "recordId" to recordId),
            account.token,
            withReason("cargo firmado")
        )

    @Test
    fun `una evidencia interna no se vincula a una queja confidencial y una de datos personales si`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val grievance = grievance(testProject(), officer, mapOf("confidential" to true))
        val internal = evidence(testProject(), "FORMULARIO", "INTERNA", officer)
        val refused = linkTo(internal, "grievance", grievance, officer)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("EVIDENCE_NOT_RESTRICTED", code(refused))
        assertEquals(listOf("classification"), problemFields(refused.body))
        val personal = evidence(testProject(), "FORMULARIO", "DATOS PERSONALES", officer)
        assertEquals(HttpStatus.NO_CONTENT, linkTo(personal, "grievance", grievance, officer).status)
    }

    @Test
    fun `una carta interna se vincula a una consulta y a una solicitud que no son confidenciales`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val letter = evidence(testProject(), "CARTA", "INTERNA", officer)
        assertEquals(HttpStatus.NO_CONTENT, linkTo(letter, "inquiry", inquiry(testProject(), officer), officer).status)
        assertEquals(HttpStatus.NO_CONTENT, linkTo(letter, "stakeholder_request", stakeholderRequest(testProject(), officer), officer).status)
    }
}
```

- [ ] **Paso 2: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Esperado: `BUILD SUCCESSFUL`. La prueba es de integración: falla en la CI (o con el coordinador) hasta el paso 3.

- [ ] **Paso 3: La regla del vínculo**

En `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`, reemplazar:

```kotlin
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
```

por:

```kotlin
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.Finding
```

En `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`, reemplazar:

```kotlin
// la clasificación lo exige FieldChangeReasonGuard. un archivo pertenece a una sola evidencia: adjuntarlo a otra abriría la
// descarga de una restringida con otra clasificación (ASM-071) y un reintento sin Idempotency-Key dejaría un duplicado que
// no se borra
@Component
@Order(GuardOrder.DOMAIN)
```

por:

```kotlin
// la clasificación lo exige FieldChangeReasonGuard. un archivo pertenece a una sola evidencia: adjuntarlo a otra abriría la
// descarga de una restringida con otra clasificación (ASM-071) y un reintento sin Idempotency-Key dejaría un duplicado que
// no se borra. desde F4, una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial
// (409 EVIDENCE_NOT_RESTRICTED, ASM-095): su descarga no exigiría DOWNLOAD_RESTRICTED y abriría el caso reservado
@Component
@Order(GuardOrder.DOMAIN)
```

En `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`, reemplazar:

```kotlin
    private val metadata: MetadataService,
    private val schemas: WasichaiSchemas,
    private val db: DatabaseClient
) : RuleGuard() {
    override suspend fun findings(
```

por:

```kotlin
    private val metadata: MetadataService,
    private val schemas: WasichaiSchemas,
    private val db: DatabaseClient,
    private val records: Records
) : RuleGuard() {
    override suspend fun findings(
```

En `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`, reemplazar:

```kotlin
        }
        if (change.kind == RecordChangeKind.CREATED) return fileInUse(change, dryRun)
        if (change.kind != RecordChangeKind.UPDATED || change.isLink()) return emptyList()
        val before = change.before.orEmpty()
        val after = view.after(change)
```

por:

```kotlin
        }
        if (change.kind == RecordChangeKind.CREATED) return fileInUse(change, dryRun)
        if (change.kind == RecordChangeKind.UPDATED && change.isLink()) return confidentialLinks(change)
        if (change.kind != RecordChangeKind.UPDATED) return emptyList()
        val before = change.before.orEmpty()
        val after = view.after(change)
```

En `src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt`, reemplazar:

```kotlin
        }
        return findings
    }

```

por:

```kotlin
        }
        return findings
    }

    // el vínculo de una evidencia no restringida con una QCS confidencial, en el extremo de la evidencia (wasichai llama a las
    // guardas con cada extremo). la QCS se lee como quien vincula: wasichai ya comprobó que la lee. deshacer un vínculo no se juzga
    private suspend fun confidentialLinks(change: RecordWrite): List<Finding> {
        if (EvidenceRules.restricted(change.before.orEmpty()[EvidenceRules.CLASSIFICATION])) return emptyList()
        return Attention.OBJECTS.mapNotNull { target ->
            val id = Records.uuid(change.attributes.orEmpty()["rel:${EvidenceRules.linkRelationship(target)}"]) ?: return@mapNotNull null
            val qcs = records.find(target, id) ?: return@mapNotNull null
            if (!Attention.isConfidential(qcs.attributes)) return@mapNotNull null
            Finding(
                "EVIDENCE_NOT_RESTRICTED",
                "Una evidencia vinculada a una QCS confidencial debe ser CONFIDENCIAL o DATOS PERSONALES: cambie su clasificación antes",
                EvidenceRules.CLASSIFICATION,
                status = HttpStatus.CONFLICT
            )
        }
    }

```

- [ ] **Paso 4: Build**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/evidence/EvidenceWriteGuard.kt src/test/kotlin/sgspe/evidence/EvidenceQcsLinksApiTest.kt
git commit -F - <<'MSG'
feat: evidencias restringidas para las QCS confidenciales

Una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial (409
EVIDENCE_NOT_RESTRICTED): el cargo y la carta firmados de un caso reservado no se descargan sin
DOWNLOAD_RESTRICTED.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.evidence.EvidenceQcsLinksApiTest` y `sgspe.evidence.EvidenceApiTest` (en la CI
del PR, con Testcontainers).

---

### Tarea 8: Los plazos y las fechas de las QCS

**Files:**
- Create: `src/main/kotlin/sgspe/attention/QcsDates.kt`
- Test: `src/test/kotlin/sgspe/attention/QcsDatesTest.kt`, `src/test/kotlin/sgspe/attention/QcsDueDatesApiTest.kt`

**Interfaces:**
- Consumes: `HolidayCalendar`, `BusinessDays`, `ProjectParameters`, `AttentionRules.RESOLVE` y `REOPEN`,
  `DerivedRecalculator` y `DerivedFieldsJob.reason`, `Transactions.locked`.
- Produces: `object QcsDueDates` (`ACK`, `RESOLUTION`, `RESPONSE`, `FIELDS`, `receivedDate(receivedAt): LocalDate?`,
  `of(objectName, receivedAt, project, calendar): Map<String, String>`, `missing(attributes, computed)`);
  `object QcsTransitionDates` (`RESOLVED`, `CLOSED`, `changes(objectName, transition, to, today)`);
  los beans `QcsDueDatesListener` (`REASON`), `QcsTransitionDatesListener` y `QcsDueDatesRecalculator` (JOB-02, `REASON`).

`QcsDueDatesListener` escribe los plazos al recibir y, si no puede, deja que JOB-02 los complete; nunca recalcula un plazo
guardado (ASM-100). `QcsTransitionDatesListener` corre en la transacción de la transición y, si falla, la revierte
(`workflows.md`, «Listeners»). Escriben como quien registra o transiciona: los derivados no tienen permiso de campo, así
que también el `u` de `APPROVER` los escribe al resolver una queja grave.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/attention/QcsDatesTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.HolidayCalendar
import sgspe.common.ModelCatalog
import java.time.LocalDate

// los plazos y las fechas de transición de las QCS (ASM-096, ASM-100, dashboards.md §12), sin base
class QcsDatesTest {
    private val calendar = HolidayCalendar.of(mapOf("2026" to listOf(LocalDate.parse("2026-10-08"))))
    private val today = LocalDate.parse("2026-10-20")

    @Test
    fun `la fecha de recepcion es la de Lima`() {
        // las 02:00 UTC del 6 son las 21:00 del 5 en Lima
        assertEquals(LocalDate.parse("2026-10-05"), QcsDueDates.receivedDate("2026-10-06T02:00:00Z"))
        assertEquals(LocalDate.parse("2026-10-06"), QcsDueDates.receivedDate("2026-10-06T05:00:00Z"))
    }

    @Test
    fun `una queja mide el acuse en dias habiles y la resolucion en dias calendario`() {
        // recibida el lunes 5 de octubre de 2026; el jueves 8 es feriado (Combate de Angamos): el quinto día hábil es el 13
        val dates = QcsDueDates.of("grievance", "2026-10-05T15:00:00Z", emptyMap(), calendar)
        assertEquals(mapOf("ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04"), dates)
        val custom = QcsDueDates.of("grievance", "2026-10-05T15:00:00Z", mapOf("grievance_ack_days" to 2, "grievance_resolution_days" to 10), calendar)
        assertEquals(mapOf("ack_due_date" to "2026-10-07", "resolution_due_date" to "2026-10-15"), custom)
    }

    @Test
    fun `una consulta mide la respuesta y una solicitud no tiene plazo`() {
        assertEquals(mapOf("response_due_date" to "2026-10-20"), QcsDueDates.of("inquiry", "2026-10-05T15:00:00Z", emptyMap(), calendar))
        assertEquals(emptyMap<String, String>(), QcsDueDates.of("stakeholder_request", "2026-10-05T15:00:00Z", emptyMap(), calendar))
        assertEquals(emptyMap<String, String>(), QcsDueDates.of("inquiry", null, emptyMap(), calendar))
    }

    @Test
    fun `un plazo guardado no se pisa`() {
        val computed = mapOf("ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04")
        assertEquals(mapOf("resolution_due_date" to "2026-11-04"), QcsDueDates.missing(mapOf("ack_due_date" to "2026-10-12"), computed))
    }

    @Test
    fun `resolver, reabrir y cerrar escriben sus fechas`() {
        assertEquals(mapOf("resolved_date" to "2026-10-20"), QcsTransitionDates.changes("grievance", "resolve", "resolved", today))
        assertEquals(mapOf("resolved_date" to null), QcsTransitionDates.changes("grievance", "reopen", "in_assessment", today))
        assertEquals(mapOf("closed_date" to "2026-10-20"), QcsTransitionDates.changes("grievance", "withdraw_received", "withdrawn", today))
        assertEquals(mapOf("closed_date" to "2026-10-20"), QcsTransitionDates.changes("stakeholder_request", "close_declined", "closed", today))
        assertEquals(emptyMap<String, Any?>(), QcsTransitionDates.changes("stakeholder_request", "decline", "declined", today))
    }

    @Test
    fun `los plazos y las fechas son derivados del modelo`() {
        val catalog = ModelCatalog.fromClasspath()
        for ((objectName, fields) in QcsDueDates.FIELDS) {
            assertEquals(emptyList<String>(), fields.filter { it !in catalog.derivedFields(objectName) }, objectName)
            assertEquals(true, QcsTransitionDates.CLOSED in catalog.derivedFields(objectName), objectName)
        }
        assertEquals(true, QcsTransitionDates.RESOLVED in catalog.derivedFields("grievance"))
    }
}
```

Crear `src/test/kotlin/sgspe/attention/QcsDueDatesApiTest.kt`:

```kotlin
package sgspe.attention

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F4ApiTest
import sgspe.common.DerivedFieldsJob
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import java.time.LocalDate

// los plazos y las fechas de transición de las QCS (ASM-096, ASM-100, dashboards.md §12): QcsDueDatesListener al recibir, con
// el calendario de application.yml (el 8 de octubre de 2026 es feriado), QcsTransitionDatesListener al resolver, reabrir y
// cerrar, y JOB-02 completa un plazo que falte sin pisar los guardados
class QcsDueDatesApiTest : F4ApiTest() {
    @Autowired
    private lateinit var derived: DerivedFieldsJob

    private val manager by lazy { member("GRIEVANCE_MANAGER") }

    private fun attributes(
        objectName: String,
        id: String
    ): JsonNode = record(objectName, id)["attributes"]

    private fun blank(node: JsonNode?): Boolean = node == null || node.isNull || node.isMissingNode

    private fun today(): String = LocalDate.now(LIMA).toString()

    @Test
    fun `una queja recibida un lunes vence su acuse en el quinto dia habil, sin contar el feriado`() {
        val id = grievance(testProject(), member("COMMUNITY_RELATIONS_OFFICER"), mapOf("received_at" to "2026-10-05T15:00:00Z"))
        val dates = attributes("grievance", id)
        assertEquals("2026-10-13", dates["ack_due_date"].asString())
        assertEquals("2026-11-04", dates["resolution_due_date"].asString())
        assertTrue(blank(dates["response_due_date"]))
    }

    @Test
    fun `una consulta mide su respuesta y una solicitud no tiene plazo`() {
        val inquiry = inquiry(testProject(), attributes = mapOf("received_at" to "2026-10-05T15:00:00Z"))
        assertEquals("2026-10-20", attributes("inquiry", inquiry)["response_due_date"].asString())
        val request = stakeholderRequest(testProject())
        assertTrue(blank(attributes("stakeholder_request", request)["response_due_date"]))
    }

    @Test
    fun `resolver, reabrir y rechazar escriben sus fechas`() {
        val id = grievance(testProject())
        for (step in listOf("acknowledge", "assess", "start_action", "resolve")) move("grievance", id, step, manager.token, step)
        assertEquals(today(), attributes("grievance", id)["resolved_date"].asString())
        move("grievance", id, "reopen", manager.token, "reabrir")
        assertTrue(blank(attributes("grievance", id)["resolved_date"]))
        val rejected = grievance(testProject())
        move("grievance", rejected, "reject_received", manager.token, "rechazo")
        assertEquals(today(), attributes("grievance", rejected)["closed_date"].asString())
    }

    @Test
    fun `JOB-02 completa un plazo que falta y no pisa los guardados`() {
        val id = grievance(testProject(), attributes = mapOf("received_at" to "2026-10-05T15:00:00Z"))
        platformPatch("grievance", id, mapOf("ack_due_date" to null, "resolution_due_date" to "2026-12-01"))
        runBlocking { derived.runOnce(full = true) }
        val dates = attributes("grievance", id)
        assertEquals("2026-10-13", dates["ack_due_date"].asString())
        assertEquals("2026-12-01", dates["resolution_due_date"].asString())
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.attention.QcsDatesTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'QcsDueDates'` y `'QcsTransitionDates'`).

- [ ] **Paso 3: Los plazos, las fechas de transición y JOB-02**

Crear `src/main/kotlin/sgspe/attention/QcsDates.kt`:

```kotlin
package sgspe.attention

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.BusinessDays
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.HolidayCalendar
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// los plazos de una QCS, puros (ASM-005, ASM-096, ASM-100; workflows.md «Plazos y escalamiento»): desde la fecha de recepción
// en Lima, el acuse de una queja en días hábiles (BusinessDays) y su resolución en días calendario; la respuesta de una
// consulta en días calendario; una solicitud no tiene plazo en el MVP y grievance.response_due_date queda vacío. se calculan
// al recibir (o los completa JOB-02) y no se recalculan si cambia la lista de feriados o un parámetro
object QcsDueDates {
    const val ACK = "ack_due_date"
    const val RESOLUTION = "resolution_due_date"
    const val RESPONSE = "response_due_date"

    // los plazos que mide cada objeto
    val FIELDS: Map<String, List<String>> =
        mapOf(Attention.GRIEVANCE to listOf(ACK, RESOLUTION), Attention.INQUIRY to listOf(RESPONSE), Attention.REQUEST to emptyList())

    // la fecha de recepción: received_at llevado a America/Lima
    fun receivedDate(receivedAt: Any?): LocalDate? {
        val text = Values.text(receivedAt) ?: return null
        val instant = runCatching { Instant.parse(text) }.getOrElse { runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull() } ?: return null
        return instant.atZone(LIMA).toLocalDate()
    }

    // los plazos de un registro con los parámetros de su proyecto; vacío sin fecha de recepción
    fun of(
        objectName: String,
        receivedAt: Any?,
        project: Map<String, Any?>,
        calendar: HolidayCalendar
    ): Map<String, String> {
        val received = receivedDate(receivedAt) ?: return emptyMap()
        return when (objectName) {
            Attention.GRIEVANCE ->
                mapOf(
                    ACK to BusinessDays.addBusinessDays(received, ProjectParameters.integer(project, "grievance_ack_days").toInt(), calendar).toString(),
                    RESOLUTION to received.plusDays(ProjectParameters.integer(project, "grievance_resolution_days")).toString()
                )
            Attention.INQUIRY -> mapOf(RESPONSE to received.plusDays(ProjectParameters.integer(project, "inquiry_response_days")).toString())
            else -> emptyMap()
        }
    }

    // de los plazos calculados, los que el registro todavía no tiene: nunca se pisa uno guardado
    fun missing(
        attributes: Map<String, Any?>,
        computed: Map<String, String>
    ): Map<String, String> = computed.filterKeys { Values.text(attributes[it]) == null }
}

// las fechas de las transiciones de una QCS, puras (dashboards.md §12): resolved_date al resolver una queja y vacía al
// reabrirla; closed_date al llegar a un estado final (closed y, en la queja, rejected y withdrawn)
object QcsTransitionDates {
    const val RESOLVED = "resolved_date"
    const val CLOSED = "closed_date"

    fun changes(
        objectName: String,
        transition: String?,
        to: String?,
        today: LocalDate
    ): Map<String, Any?> {
        val changes = linkedMapOf<String, Any?>()
        if (objectName == Attention.GRIEVANCE && transition in AttentionRules.RESOLVE) changes[RESOLVED] = today.toString()
        if (objectName == Attention.GRIEVANCE && transition == AttentionRules.REOPEN) changes[RESOLVED] = null
        if (to != null && to in Attention.FINAL_STATES.getValue(objectName)) changes[CLOSED] = today.toString()
        return changes
    }
}

// QcsDueDatesListener (workflows.md, «Listeners»): al recibir una QCS escribe sus plazos, como quien la registra y con la marca.
// si no puede (no lee el proyecto, por ejemplo), lo registra y el alta sigue: JOB-02 los completa (QcsDueDatesRecalculator)
@Component
@Order(120)
class QcsDueDatesListener(
    private val records: Records,
    private val calendar: HolidayCalendar
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.CREATED || change.objectName !in Attention.OBJECTS) return
        if (QcsDueDates.FIELDS.getValue(change.objectName).isEmpty()) return
        try {
            val record = records.find(change.objectName, change.recordId) ?: return
            val project = Records.uuid(record.attributes["project"])?.let { records.find("project", it)?.attributes }.orEmpty()
            val missing = QcsDueDates.missing(record.attributes, QcsDueDates.of(change.objectName, record.attributes[Attention.RECEIVED_AT], project, calendar))
            if (missing.isNotEmpty()) records.patch(change.objectName, change.recordId, missing, REASON)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn("QcsDueDatesListener: no se escribieron los plazos de {} {}; JOB-02 los completará", change.objectName, change.recordId, e)
        }
    }

    companion object {
        const val REASON = "derivado: plazos de la QCS"
    }
}

// QcsTransitionDatesListener (workflows.md, «Listeners»): en la transacción de la transición escribe resolved_date y closed_date
// como quien la aplica y con la marca; si no puede, lanza y la transición se revierte
@Component
@Order(120)
class QcsTransitionDatesListener(
    private val records: Records,
    private val catalog: ModelCatalog,
    private val clock: Clock
) : RecordChangeListener {
    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.TRANSITIONED || change.objectName !in Attention.OBJECTS) return
        val to = change.transition?.let { catalog.transition(change.objectName, it)?.to }
        val changes = QcsTransitionDates.changes(change.objectName, change.transition, to, LocalDate.now(clock.withZone(LIMA)))
        if (changes.isNotEmpty()) records.patch(change.objectName, change.recordId, changes, "derivado: fecha de la transición ${change.transition}")
    }
}

// el recalculador de JOB-02 para los plazos de las QCS: completa los que falten en las QCS abiertas cambiadas desde la vuelta
// anterior (o en todas, en la vuelta diaria); nunca recalcula uno guardado
@Component
class QcsDueDatesRecalculator(
    private val records: Records,
    private val catalog: ModelCatalog,
    private val transactions: Transactions,
    private val calendar: HolidayCalendar
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Attention.GRIEVANCE

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val projects = mutableMapOf<String, Map<String, Any?>>()
        var written = 0
        var failed = 0
        for ((objectName, fields) in QcsDueDates.FIELDS) {
            if (fields.isEmpty()) continue
            val open = Criteria.inState(catalog.states(objectName) - Attention.FINAL_STATES.getValue(objectName))
            val rows = records.all(objectName, criteria = listOfNotNull(open, since?.let { Criteria.updatedSince(it) }))
            for (row in rows.filter { r -> fields.any { Values.text(r.attributes[it]) == null } }) {
                try {
                    val projectId = Values.text(row.attributes["project"]) ?: continue
                    val project = projects.getOrPut(projectId) { records.find("project", UUID.fromString(projectId))?.attributes.orEmpty() }
                    val missing = QcsDueDates.missing(row.attributes, QcsDueDates.of(objectName, row.attributes[Attention.RECEIVED_AT], project, calendar))
                    if (missing.isEmpty()) continue
                    val id = UUID.fromString(row.id)
                    transactions.locked("$objectName:$id") { records.patch(objectName, id, missing, REASON) }
                    written++
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                    log.warn("QcsDueDatesRecalculator: no se completaron los plazos de {} {}", objectName, row.id, e)
                }
            }
        }
        check(failed == 0) { "no se completaron los plazos de $failed QCS (se escribieron $written)" }
        return written
    }

    companion object {
        val REASON = DerivedFieldsJob.reason("plazos de la QCS")
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `QcsDatesTest` con 6 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/QcsDates.kt src/test/kotlin/sgspe/attention/QcsDatesTest.kt src/test/kotlin/sgspe/attention/QcsDueDatesApiTest.kt
git commit -F - <<'MSG'
feat: plazos y fechas de transición de las QCS

QcsDueDatesListener escribe al recibir el acuse en días hábiles y la resolución de una queja y la
respuesta de una consulta; JOB-02 completa los que falten sin pisar los guardados.
QcsTransitionDatesListener fecha la resolución, la reapertura y el cierre.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.attention.QcsDueDatesApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 9: La reclasificación

**Files:**
- Create: `src/main/kotlin/sgspe/attention/Reclassifications.kt`
- Modify: `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`
- Test: `src/test/kotlin/sgspe/attention/ReclassificationsTest.kt`, `src/test/kotlin/sgspe/attention/ReclassificationApiTest.kt`,
  `src/test/kotlin/sgspe/attention/InquiryWorkflowApiTest.kt`

**Interfaces:**
- Consumes: `Idempotency.respond`, `IfMatch.required`, `Versions.check`, `TransitionRoles.require`, `StrictBody`,
  `Records.transition` (con la marca), `Records.geometriesOf`, `Attention.reclassifiedField`.
- Produces: `POST /api/sgspe/{grievances|inquiries|stakeholder-requests}/{id}/reclassify`
  `{ targetType, attributes }` → `201 { original, created }` con `ETag` del registro nuevo (`IK`, `IM`, `CR`);
  `Reclassifications` (`TARGET`, `CLOSE_PREFIX`, `COPIED`, `PATHS`, `closing(catalog, source, state): String?`);
  `CommandTransitionGuard.COMMANDS` con las seis `close_reclassified*`; en la matriz, `perform` reclasifica por el
  comando.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/attention/ReclassificationsTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.common.CommandTransitionGuard
import sgspe.common.ModelCatalog

// la transición de reclasificación de cada estado y los campos que se copian (api.md §4.6), sin base
class ReclassificationsTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `cada estado con reclasificacion tiene su transicion de comando`() {
        assertEquals("close_reclassified_acknowledged", Reclassifications.closing(catalog, "grievance", "acknowledged"))
        assertEquals("close_reclassified", Reclassifications.closing(catalog, "inquiry", "received"))
        assertEquals("close_reclassified_under_evaluation", Reclassifications.closing(catalog, "stakeholder_request", "under_evaluation"))
        assertNull(Reclassifications.closing(catalog, "grievance", "in_action"))
        assertNull(Reclassifications.closing(catalog, "inquiry", "answered"))
        for (objectName in listOf("grievance", "inquiry", "stakeholder_request")) {
            val names =
                catalog
                    .transitions(objectName)
                    .filter { it.name.startsWith(Reclassifications.CLOSE_PREFIX) }
                    .map { it.name }
                    .toSet()
            assertEquals(
                names,
                CommandTransitionGuard.COMMANDS[objectName]
                    .orEmpty()
                    .keys
                    .filter { it.startsWith(Reclassifications.CLOSE_PREFIX) }
                    .toSet()
            )
        }
    }

    @Test
    fun `los campos copiados existen en los tres objetos`() {
        for (objectName in listOf("grievance", "inquiry", "stakeholder_request")) {
            val fields = catalog.objectOrNull(objectName)!!.fields.keys + catalog.manyToOneFields(objectName).map { it.first }
            assertEquals(emptyList<String>(), Reclassifications.COPIED.filter { it !in fields }, objectName)
        }
    }
}
```

Crear `src/test/kotlin/sgspe/attention/ReclassificationApiTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import sgspe.common.IdempotencyKey

// la reclasificación de una QCS (RF-GRV-007, ASM-072, api.md §4.6): crea el registro del tipo nuevo con los campos comunes y la
// fecha de recepción del primero de la cadena, lo vincula y cierra el original con su transición de comando y el motivo;
// honra Idempotency-Key; la API genérica no aplica close_reclassified_* (COMMAND_ONLY)
class ReclassificationApiTest : F4ApiTest() {
    private val manager by lazy { member("GRIEVANCE_MANAGER") }

    private fun reclassify(
        path: String,
        objectName: String,
        id: String,
        target: String,
        account: Account = manager,
        attributes: Map<String, Any?> = mapOf("code" to uniqueName("RC-")),
        headers: Map<String, String> = emptyMap()
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/$path/$id/reclassify",
            mapOf("targetType" to target, "attributes" to attributes),
            account.token,
            ifMatch(objectName, id, account.token) + withReason("era una consulta") + headers
        )

    @Test
    fun `una queja reclasificada como consulta conserva la recepcion y cierra el original`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id = grievance(testProject(), officer, mapOf("received_at" to "2026-10-05T15:00:00Z"))
        move("grievance", id, "acknowledge", manager.token, "acuse")
        val response = reclassify("grievances", "grievance", id, "inquiry")
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val created = tree(response.body)["created"]
        assertEquals(id, created["attributes"]["reclassified_from_grievance"].asString())
        assertEquals(officer.id, created["attributes"]["received_by_user_id"].asString())
        assertTrue(created["attributes"]["received_at"].asString().startsWith("2026-10-05T15:00"))
        // los plazos de la consulta corren desde la recepción original
        assertEquals("2026-10-20", record("inquiry", created["id"].asString())["attributes"]["response_due_date"].asString())
        assertEquals("closed", tree(response.body)["original"]["state"].asString())
        val history = tree(send("GET", "/api/objects/grievance/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString() == "era una consulta" })
    }

    @Test
    fun `el mismo tipo es INVALID_TARGET y un estado sin reclasificacion es STATE_MISMATCH`() {
        val id = inquiry(testProject())
        val same = reclassify("inquiries", "inquiry", id, "inquiry")
        assertEquals(HttpStatus.BAD_REQUEST, same.status, same.body)
        assertEquals("INVALID_TARGET", code(same))
        assertEquals(listOf("targetType"), problemFields(same.body))
        move("inquiry", id, "answer", manager.token, "respuesta")
        val answered = reclassify("inquiries", "inquiry", id, "grievance")
        assertEquals(HttpStatus.CONFLICT, answered.status, answered.body)
        assertEquals("STATE_MISMATCH", code(answered))
    }

    @Test
    fun `solo el rol de la transicion reclasifica y la API generica no cierra por reclasificacion`() {
        val id = stakeholderRequest(testProject())
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val refused = reclassify("stakeholder-requests", "stakeholder_request", id, "grievance", officer)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("ROLE_REQUIRED", code(refused))
        val generic = transition("stakeholder_request", id, "close_reclassified_received", manager.token, "cierre")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
        assertEquals("received", state("stakeholder_request", id))
    }

    @Test
    fun `un reenvio con la misma clave devuelve la misma respuesta y no reclasifica dos veces`() {
        val id = grievance(testProject())
        val key = mapOf(IdempotencyKey.HEADER to uniqueName("rc-"))
        val attributes = mapOf("code" to uniqueName("RC-"))
        val first = reclassify("grievances", "grievance", id, "stakeholder_request", attributes = attributes, headers = key)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val again =
            exchange(
                "POST",
                "/api/sgspe/grievances/$id/reclassify",
                mapOf("targetType" to "stakeholder_request", "attributes" to attributes),
                manager.token,
                key + withReason("era una consulta") + mapOf("If-Match" to first.headers.getFirst("ETag").orEmpty())
            )
        assertEquals("true", again.headers.getFirst("Idempotent-Replayed"))
        assertArrayEquals(first.body.toByteArray(), again.body.toByteArray())
    }
}
```

Crear `src/test/kotlin/sgspe/attention/InquiryWorkflowApiTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-E2 con AttentionWriteGuard (workflows.md, especificación de F4 §3.2): responder exige la respuesta y su fecha, derivar
// exige la respuesta, cerrar una derivada exige la fecha de respuesta; una consulta anónima no lleva grupo de interés
class InquiryWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("GRIEVANCE_MANAGER") }

    @Test
    fun `responder exige response y responded_at`() {
        val id = inquiry(testProject(), attributes = mapOf("response" to null, "responded_at" to null))
        val response = transition("inquiry", id, "answer", manager.token, "respuesta")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("FIELD_REQUIRED", code(response))
        assertEquals(listOf("response"), problemFields(response.body))
    }

    @Test
    fun `derivar exige la respuesta y cerrar la derivada su fecha`() {
        val id = inquiry(testProject(), attributes = mapOf("responded_at" to null))
        move("inquiry", id, "refer", manager.token, "derivada al área de proyectos")
        val response = transition("inquiry", id, "close_referred", manager.token, "cierre")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("responded_at"), problemFields(response.body))
    }

    @Test
    fun `una consulta anonima no lleva grupo de interes`() {
        val response =
            exchange(
                "POST",
                "/api/objects/inquiry/records",
                mapOf(
                    "attributes" to attentionAttributes(testProject(), null, "CO-") + mapOf("anonymous" to true, "stakeholder" to stakeholder(testProject()))
                ),
                token,
                withReason("alta de prueba")
            )
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("ANONYMOUS_WITH_IDENTITY", code(response))
        assertEquals(listOf("stakeholder"), problemFields(response.body))
    }
}
```

La matriz de workflows deja de diferir las reclasificaciones y las aplica por el comando (decisión 6):

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin

    // una transición como la aplica el portal: accept de WF-K por su comando, que crea el compromiso en draft con los valores
    // que revisa la persona (CommandTransitionGuard rechaza la API genérica); las demás, por la API genérica
    private fun perform(
        workflow: Workflow,
```

por:

```kotlin

    // una transición como la aplica el portal: accept de WF-K por su comando, que crea el compromiso en draft con los valores
    // que revisa la persona (CommandTransitionGuard rechaza la API genérica); las reclasificaciones de F4 por el suyo, cuando el
    // registro está en su estado de origen (fuera de él, el comando elegiría la transición de otro estado: la API genérica
    // responde 409 antes de las guardas); las demás, por la API genérica
    private fun perform(
        workflow: Workflow,
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        who: Account
    ): Response {
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
```

por:

```kotlin
        who: Account
    ): Response {
        if (rule.name.startsWith("close_reclassified") && state(workflow.objectName, id) == rule.from) return reclassify(workflow, id, who)
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
            "/api/sgspe/commitment-candidates/$id/accept",
            mapOf("attributes" to reviewed),
            who.token,
            ifMatch(workflow.objectName, id, who.token) + withReason(TEXT)
```

por:

```kotlin
            "/api/sgspe/commitment-candidates/$id/accept",
            mapOf("attributes" to reviewed),
            who.token,
            ifMatch(workflow.objectName, id, who.token) + withReason(TEXT)
        )
    }

    // la reclasificación de una QCS a otro tipo (POST /api/sgspe/{tipo}/{id}/reclassify), con el código que pide el registro nuevo
    private fun reclassify(
        workflow: Workflow,
        id: String,
        who: Account
    ): Response {
        val target = if (workflow.objectName == "inquiry") "stakeholder_request" else "inquiry"
        val path = mapOf("grievance" to "grievances", "inquiry" to "inquiries", "stakeholder_request" to "stakeholder-requests").getValue(workflow.objectName)
        return exchange(
            "POST",
            "/api/sgspe/$path/$id/reclassify",
            mapOf("targetType" to target, "attributes" to mapOf("code" to uniqueName("RC-"))),
            who.token,
            ifMatch(workflow.objectName, id, who.token) + withReason(TEXT)
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        // las de F3 ya no se difieren (plan 3.2): approve_direct corre en un proyecto sin aprobación interna, prepare registra la
        // entrega que pide register_fulfillment y perform aplica accept por su comando. las de F4 que llegan con sus comandos
        // (CommandTransitionGuard, planes 4.2a y 4.2b) y lo que solo se alcanza por ellos: las reclasificaciones de WF-E1,
        // WF-E2 y WF-E3, accept y close_accepted de WF-E3 (y close_unfulfilled, que sale de `accepted`), y assess de WF-F
        // (y todo lo que sale de `assessed`, `mitigating` y `monitoring`); sus casos los escriben GrievanceWorkflowApiTest,
        // InquiryWorkflowApiTest, StakeholderRequestWorkflowApiTest y SocialRiskWorkflowApiTest, que también los quitan de aquí
```

por:

```kotlin
        // las de F3 ya no se difieren (plan 3.2): approve_direct corre en un proyecto sin aprobación interna, prepare registra la
        // entrega que pide register_fulfillment y perform aplica accept por su comando. las de F4 que llegan con sus comandos
        // (CommandTransitionGuard, planes 4.2a y 4.2b) y lo que solo se alcanza por ellos: accept y close_accepted de WF-E3 (y
        // close_unfulfilled, que sale de `accepted`), y assess de WF-F; las reclasificaciones ya no se difieren (plan 4.2a):
        // perform las aplica por su comando
        // (y todo lo que sale de `assessed`, `mitigating` y `monitoring`); sus casos los escriben GrievanceWorkflowApiTest,
        // InquiryWorkflowApiTest, StakeholderRequestWorkflowApiTest y SocialRiskWorkflowApiTest, que también los quitan de aquí
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "grievance.close_reclassified_received",
                "grievance.close_reclassified_acknowledged",
                "grievance.close_reclassified_in_assessment",
                "inquiry.close_reclassified",
                "stakeholder_request.accept",
                "stakeholder_request.close_accepted",
                "stakeholder_request.close_unfulfilled",
                "stakeholder_request.close_reclassified_received",
                "stakeholder_request.close_reclassified_under_evaluation",
                "social_risk.assess",
                "social_risk.decide_mitigation",
```

por:

```kotlin
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "stakeholder_request.accept",
                "stakeholder_request.close_accepted",
                "stakeholder_request.close_unfulfilled",
                "social_risk.assess",
                "social_risk.decide_mitigation",
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.attention.ReclassificationsTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'Reclassifications'`).

- [ ] **Paso 3: El comando y sus transiciones de comando**

Crear `src/main/kotlin/sgspe/attention/Reclassifications.kt`:

```kotlin
package sgspe.attention

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
import sgspe.common.Attention
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
import java.util.UUID

data class Reclassification(
    val original: RecordResponse,
    val created: RecordResponse
)

// la reclasificación de una QCS (RF-GRV-007, ASM-072, api.md §4.6): en una transacción crea la QCS de `targetType` con los
// campos comunes del original (la fecha de recepción es la del primer registro de la cadena, que el original ya trae) más
// reclassified_from_<tipo>, y cierra el original con la transición de comando close_reclassified_* de su estado, con el motivo
// de la petición. `attributes` completa o cambia los campos no derivados, salvo received_at, el proyecto y los vínculos de
// reclasificación. el código no se copia: cada tipo numera sus registros. todo como quien llama (las QCS confidenciales las
// copia quien las lee, con su marca y su receptor)
@Service
class Reclassifications(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun reclassify(
        source: String,
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf(TARGET, "attributes"))
        val target = Values.text(body[TARGET])
        if (target == null || target == source || target !in Attention.OBJECTS) {
            throw Finding(
                "INVALID_TARGET",
                "targetType es grievance, inquiry o stakeholder_request, distinto del tipo de la QCS que se reclasifica",
                TARGET,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, target, attributes, fixed(target))
        if (reason.isNullOrBlank()) throw ValidationException("Reclasificar exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Reclasificar una QCS", Actions.UPDATE to source, Actions.CREATE to target)
        val result =
            transactions.locked("$source:$id") {
                val original = records.get(source, id)
                versions.check(source, id, expected, original.updatedAt, "La QCS")
                val close =
                    closing(catalog, source, original.state)
                        ?: throw Finding(
                            "STATE_MISMATCH",
                            "Una QCS en ${original.state} no se reclasifica: no tiene transición de reclasificación desde ese estado",
                            "record"
                        ).toCodedException()
                TransitionRoles.require(catalog, user, source, listOf(close))
                val copied = COPIED.filter { it in original.attributes }.associateWith { original.attributes[it] }
                val geometries = Records.geometriesOf(original).filterValues { it != null }
                val created =
                    records.create(target, copied + attributes + (Attention.reclassifiedField(target, source) to id.toString()), reason, geometries)
                records.transition(source, id, close, reason)
                Reclassification(records.get(source, id), records.get(target, UUID.fromString(created.id)))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.created.updatedAt, "/api/objects/$target/records/${result.created.id}")
    }

    // lo que una reclasificación fija y `attributes` no cambia
    private fun fixed(target: String): Set<String> = setOf(Attention.RECEIVED_AT, "project") + Attention.RECLASSIFIED_FROM.getValue(target).keys

    companion object {
        const val TARGET = "targetType"
        const val CLOSE_PREFIX = "close_reclassified"

        // la transición de reclasificación que sale del estado del original, de model/workflows (close_reclassified_*)
        fun closing(
            catalog: ModelCatalog,
            source: String,
            state: String?
        ): String? = catalog.transitions(source).firstOrNull { it.name.startsWith(CLOSE_PREFIX) && it.from == state }?.name

        // los campos comunes que pasan del original al registro nuevo (api.md §4.6): canal, recepción, quien recibió,
        // anonimato, confidencialidad, categoría, descripción, prioridad, responsable, grupo de interés, persona, territorio,
        // tema, interacción y pregunta de origen y el proyecto; la ubicación va por geometries
        val COPIED: List<String> =
            listOf(
                "project",
                "channel",
                Attention.RECEIVED_AT,
                Attention.RECEIVER,
                "received_by_user_email",
                "anonymous",
                Attention.CONFIDENTIAL,
                "category",
                "description",
                "priority",
                Attention.OWNER,
                "owner_user_email",
                "stakeholder",
                "person",
                "locality",
                "community",
                "topic",
                "origin_engagement",
                "origin_question"
            )

        // la ruta de cada QCS en /api/sgspe
        val PATHS: Map<String, String> =
            mapOf("grievances" to Attention.GRIEVANCE, "inquiries" to Attention.INQUIRY, "stakeholder-requests" to Attention.REQUEST)
    }
}

// api.md §4.6: POST /api/sgspe/{grievances|inquiries|stakeholder-requests}/{id}/reclassify, con Idempotency-Key
@RestController
@RequestMapping("/api/sgspe")
class ReclassificationController(
    private val reclassifications: Reclassifications,
    private val idempotency: Idempotency
) {
    @PostMapping("/{kind:grievances|inquiries|stakeholder-requests}/{id}/reclassify")
    suspend fun reclassify(
        @PathVariable kind: String,
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val source = Reclassifications.PATHS.getValue(kind)
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            reclassifications.reclassify(source, id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
```

En `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, reemplazar:

```kotlin
// las transiciones de comando solo llegan desde su endpoint, que escribe varios registros en una transacción y lleva la
// marca SgspeWrite (workflows.md, «Transiciones de comando»). por la API genérica de workflow, sin la marca: 403, como la
// puerta cerrada de un objeto apiOnly. las de F3 y F4 las agregan sus fases a COMMANDS
@Component
@Order(GuardOrder.COMMAND)
```

por:

```kotlin
// las transiciones de comando solo llegan desde su endpoint, que escribe varios registros en una transacción y lleva la
// marca SgspeWrite (workflows.md, «Transiciones de comando»). por la API genérica de workflow, sin la marca: 403, como la
// puerta cerrada de un objeto apiOnly. las de F3 y F4 las agregan sus fases a COMMANDS (assess de WF-F, con su endpoint, en el
// plan 4.2b)
@Component
@Order(GuardOrder.COMMAND)
```

En `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, reemplazar:

```kotlin
                        "supersede" to "POST /api/sgspe/areas-of-influence/{id}/approve"
                    ),
                "commitment_candidate" to mapOf("accept" to "POST /api/sgspe/commitment-candidates/{id}/accept")
            )
    }
```

por:

```kotlin
                        "supersede" to "POST /api/sgspe/areas-of-influence/{id}/approve"
                    ),
                "commitment_candidate" to mapOf("accept" to "POST /api/sgspe/commitment-candidates/{id}/accept"),
                // F4: las reclasificaciones de WF-E1, WF-E2 y WF-E3 (plan 4.2a)
                "grievance" to
                    mapOf(
                        "close_reclassified_received" to "POST /api/sgspe/grievances/{id}/reclassify",
                        "close_reclassified_acknowledged" to "POST /api/sgspe/grievances/{id}/reclassify",
                        "close_reclassified_in_assessment" to "POST /api/sgspe/grievances/{id}/reclassify"
                    ),
                "inquiry" to mapOf("close_reclassified" to "POST /api/sgspe/inquiries/{id}/reclassify"),
                "stakeholder_request" to
                    mapOf(
                        "close_reclassified_received" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify",
                        "close_reclassified_under_evaluation" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify"
                    )
            )
    }
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ReclassificationsTest` con 2 pruebas y `GuardTablesCatalogTest` («cada transicion de comando
existe en el workflow de su objeto») en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/Reclassifications.kt src/main/kotlin/sgspe/common/CommandTransitionGuard.kt \
  src/test/kotlin/sgspe/attention/ReclassificationsTest.kt src/test/kotlin/sgspe/attention/ReclassificationApiTest.kt \
  src/test/kotlin/sgspe/attention/InquiryWorkflowApiTest.kt src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -F - <<'MSG'
feat: reclasificación de quejas, consultas y solicitudes

POST /api/sgspe/{tipo}/{id}/reclassify crea la QCS del otro tipo con los campos comunes y la
recepción del primero de la cadena, la vincula y cierra el original con su transición de comando,
en una transacción y con Idempotency-Key. La matriz deja de diferir las reclasificaciones.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.attention.ReclassificationApiTest`, `sgspe.attention.InquiryWorkflowApiTest` y
`sgspe.workflow.WorkflowMatrixApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 10: La aceptación de una solicitud y su cierre por el compromiso

**Files:**
- Create: `src/main/kotlin/sgspe/attention/Acceptances.kt`
- Modify: `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`
- Test: `src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt`

**Interfaces:**
- Consumes: lo mismo que la Tarea 9; `AttentionRules.CREATED_COMMITMENT`; `Criteria.inState`.
- Produces: `POST /api/sgspe/stakeholder-requests/{id}/accept` `{ creates, response, respondedAt, attributes }` →
  `201 { request, commitment }` con `ETag` de la solicitud (`IK`, `IM`, `CR`); `Acceptances` (`CREATES`, `COMMITMENT`,
  `ACCEPT`, `UNDER_EVALUATION`); `RequestClosureListener` (`@Order(130)`, `CLOSE`, `ACCEPTED`, `CLOSE_ACCEPTED`);
  `CommandTransitionGuard.COMMANDS` con `accept` y `close_accepted` de WF-E3.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// WF-E3 (workflows.md, api.md §4.6, RN-GRV-001, ASM-068): aceptar es responder y crear el compromiso en draft, por su comando;
// la API genérica no acepta ni cierra por el compromiso (COMMAND_ONLY); el cierre del compromiso cierra la solicitud
// (RequestClosureListener, como quien cierra y con el motivo); close_unfulfilled exige el compromiso cancelado; declinar exige
// la respuesta
class StakeholderRequestWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_MANAGER") }
    private val grievances by lazy { member("GRIEVANCE_MANAGER") }

    private fun underEvaluation(attributes: Map<String, Any?> = emptyMap()): String {
        val id = stakeholderRequest(testProject(), attributes = attributes)
        move("stakeholder_request", id, "start_evaluation", grievances.token, "evaluación")
        return id
    }

    private fun accept(
        id: String,
        body: Map<String, Any?> =
            mapOf(
                "creates" to "commitment",
                "response" to "Se atenderá con un compromiso de materiales.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to mapOf("code" to uniqueName("COM-"), "title" to "Materiales para el canal de riego")
            ),
        account: Account = manager
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/stakeholder-requests/$id/accept",
            body,
            account.token,
            ifMatch("stakeholder_request", id, account.token) + withReason("se atiende la solicitud")
        )

    private fun attributes(id: String): JsonNode = record("stakeholder_request", id)["attributes"]

    @Test
    fun `aceptar responde la solicitud y crea el compromiso en draft`() {
        val id = underEvaluation(mapOf("response" to null, "responded_at" to null))
        val response = accept(id)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val body = tree(response.body)
        assertEquals("accepted", body["request"]["state"].asString())
        assertEquals("draft", body["commitment"]["state"].asString())
        assertEquals(body["commitment"]["id"].asString(), body["request"]["attributes"]["created_commitment"].asString())
        assertEquals(testProject(), body["commitment"]["attributes"]["project"].asString())
        assertEquals("Se atenderá con un compromiso de materiales.", attributes(id)["response"].asString())
    }

    @Test
    fun `un aporte social, una solicitud fuera de evaluacion o sin respuesta no se aceptan`() {
        val id = underEvaluation(mapOf("response" to null, "responded_at" to null))
        val contribution = accept(id, mapOf("creates" to "social_contribution", "response" to "x", "respondedAt" to "2026-10-08T15:00:00Z"))
        assertEquals(HttpStatus.BAD_REQUEST, contribution.status, contribution.body)
        assertEquals("INVALID_TARGET", code(contribution))
        assertEquals(listOf("creates"), problemFields(contribution.body))
        val silent = accept(id, mapOf("creates" to "commitment", "attributes" to mapOf("code" to uniqueName("COM-"), "title" to "Sin respuesta")))
        assertEquals(HttpStatus.BAD_REQUEST, silent.status, silent.body)
        assertEquals("FIELD_REQUIRED", code(silent))
        assertEquals("under_evaluation", state("stakeholder_request", id))
        assertTrue(attributes(id)["created_commitment"].let { it == null || it.isNull })
        val received = stakeholderRequest(testProject())
        assertEquals("STATE_MISMATCH", code(accept(received)))
        val generic = transition("stakeholder_request", id, "accept", manager.token, "aceptada")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
    }

    @Test
    fun `cerrar el compromiso creado cierra la solicitud, como quien cierra y con el motivo`() {
        val id = underEvaluation()
        assertEquals(HttpStatus.CREATED, accept(id).status)
        // el compromiso creado sigue WF-D; para no recorrerlo entero, la solicitud apunta a uno ya aprobado del proyecto
        val owner = member("COMMITMENT_MANAGER")
        val commitment = approvedCommitment(testProject(), owner, mapOf("resource_type" to "NO MATERIAL"))
        platformPatch("stakeholder_request", id, mapOf("created_commitment" to commitment))
        val delivery =
            exchange(
                "POST",
                "/api/sgspe/commitments/$commitment/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva"),
                    "evidence" to listOf(evidence(testProject(), "ACTA"))
                ),
                token,
                withReason()
            )
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
        move("commitment", commitment, "register_fulfillment", reason = "entregado")
        val verifier = member("COMMITMENT_MANAGER")
        move("commitment", commitment, "verify", verifier.token, "verificado")
        move("commitment", commitment, "close", verifier.token, "cerrado")
        assertEquals("closed", state("stakeholder_request", id))
        val history = tree(send("GET", "/api/objects/stakeholder_request/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString()?.startsWith("RN-GRV-001: compromiso") == true }, history.toString())
        // por la API genérica, una solicitud aceptada no se cierra por el compromiso
        val other = underEvaluation()
        assertEquals(HttpStatus.CREATED, accept(other).status)
        val generic = transition("stakeholder_request", other, "close_accepted", manager.token, "cierre")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
    }

    @Test
    fun `close_unfulfilled exige el compromiso cancelado`() {
        val id = underEvaluation()
        val commitment = tree(accept(id).body)["commitment"]["id"].asString()
        val refused = transition("stakeholder_request", id, "close_unfulfilled", grievances.token, "sin ejecución")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("STATE_MISMATCH", code(refused))
        assertEquals(listOf("created_commitment"), problemFields(refused.body))
        move("commitment", commitment, "cancel_draft", reason = "no se ejecutará")
        move("stakeholder_request", id, "close_unfulfilled", grievances.token, "sin ejecución")
    }

    @Test
    fun `declinar exige la respuesta`() {
        val id = underEvaluation(mapOf("response" to null))
        val response = transition("stakeholder_request", id, "decline", grievances.token, "no procede")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("response"), problemFields(response.body))
    }
}
```

La matriz acepta por el comando y prepara `close_unfulfilled` con el compromiso cancelado; `close_accepted` sigue diferida
(decisión 5):

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
    // lo que una transición pide antes de aplicarse y la API genérica no da: register_fulfillment de WF-D exige una entrega con
    // conformidad y, en un compromiso MATERIAL, su acta de entrega y su foto (D9), que el admin registra con el comando de
    // entregas del plan 3.2
    private fun prepare(
        workflow: Workflow,
```

por:

```kotlin
    // lo que una transición pide antes de aplicarse y la API genérica no da: register_fulfillment de WF-D exige una entrega con
    // conformidad y, en un compromiso MATERIAL, su acta de entrega y su foto (D9), que el admin registra con el comando de
    // entregas del plan 3.2; close_unfulfilled de WF-E3, el compromiso creado al aceptar ya cancelado (plan 4.2a)
    private fun prepare(
        workflow: Workflow,
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        rule: Transition
    ) {
        if (workflow.key(rule) != "commitment.register_fulfillment") return
        val evidence = listOf(evidence(testProject(), "ACTA DE ENTREGA"), evidence(testProject(), "FOTO", "PUBLICA"))
```

por:

```kotlin
        rule: Transition
    ) {
        if (workflow.key(rule) == "stakeholder_request.close_unfulfilled" && state(workflow.objectName, id) == rule.from) {
            move("commitment", record(workflow.objectName, id)["attributes"]["created_commitment"].asString(), "cancel_draft", reason = TEXT)
            return
        }
        if (workflow.key(rule) != "commitment.register_fulfillment") return
        val evidence = listOf(evidence(testProject(), "ACTA DE ENTREGA"), evidence(testProject(), "FOTO", "PUBLICA"))
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
    ): Response {
        if (rule.name.startsWith("close_reclassified") && state(workflow.objectName, id) == rule.from) return reclassify(workflow, id, who)
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
```

por:

```kotlin
    ): Response {
        if (rule.name.startsWith("close_reclassified") && state(workflow.objectName, id) == rule.from) return reclassify(workflow, id, who)
        if (workflow.key(rule) == "stakeholder_request.accept") return accept(id, who)
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        )
    }

    // quien crea el registro de la prueba: el propio rol si puede crearlo (RU: FIELD_AGENT solo cambia lo que creó), si no el
```

por:

```kotlin
        )
    }

    // la aceptación de una solicitud (POST /api/sgspe/stakeholder-requests/{id}/accept): su respuesta y el compromiso en draft
    private fun accept(
        id: String,
        who: Account
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/stakeholder-requests/$id/accept",
            mapOf(
                "creates" to "commitment",
                "response" to "Se atenderá con un compromiso.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to mapOf("code" to uniqueName("COM-"), "title" to "Materiales para el canal de riego")
            ),
            who.token,
            ifMatch("stakeholder_request", id, who.token) + withReason(TEXT)
        )

    // quien crea el registro de la prueba: el propio rol si puede crearlo (RU: FIELD_AGENT solo cambia lo que creó), si no el
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest.
        // las de F3 ya no se difieren (plan 3.2): approve_direct corre en un proyecto sin aprobación interna, prepare registra la
        // entrega que pide register_fulfillment y perform aplica accept por su comando. las de F4 que llegan con sus comandos
        // (CommandTransitionGuard, planes 4.2a y 4.2b) y lo que solo se alcanza por ellos: accept y close_accepted de WF-E3 (y
        // close_unfulfilled, que sale de `accepted`), y assess de WF-F; las reclasificaciones ya no se difieren (plan 4.2a):
        // perform las aplica por su comando
        // (y todo lo que sale de `assessed`, `mitigating` y `monitoring`); sus casos los escriben GrievanceWorkflowApiTest,
        // InquiryWorkflowApiTest, StakeholderRequestWorkflowApiTest y SocialRiskWorkflowApiTest, que también los quitan de aquí
        val DEFERRED =
            setOf(
```

por:

```kotlin
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest.
        // las de F3 ya no se difieren (plan 3.2): approve_direct corre en un proyecto sin aprobación interna, prepare registra la
        // entrega que pide register_fulfillment y perform aplica accept por su comando. de F4 se difieren close_accepted de WF-E3,
        // que solo aplica RequestClosureListener al cerrar el compromiso (sus casos, en StakeholderRequestWorkflowApiTest), y
        // assess de WF-F con todo lo que sale de `assessed`, `mitigating` y `monitoring` (plan 4.2b, SocialRiskWorkflowApiTest).
        // las reclasificaciones y accept de WF-E3 ya no se difieren (plan 4.2a): perform las aplica por su comando y prepare
        // cancela el compromiso que pide close_unfulfilled
        val DEFERRED =
            setOf(
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "stakeholder_request.accept",
                "stakeholder_request.close_accepted",
                "stakeholder_request.close_unfulfilled",
                "social_risk.assess",
                "social_risk.decide_mitigation",
```

por:

```kotlin
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "stakeholder_request.close_accepted",
                "social_risk.assess",
                "social_risk.decide_mitigation",
```

- [ ] **Paso 2: Compilar para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Esperado: `BUILD SUCCESSFUL` (las pruebas son de integración: sin el comando, la CI recibiría `404` en la aceptación).

- [ ] **Paso 3: El comando, el listener y las transiciones de comando**

Crear `src/main/kotlin/sgspe/attention/Acceptances.kt`:

```kotlin
package sgspe.attention

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Criteria
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
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.core.data.RecordResponse
import java.util.UUID

data class RequestAcceptance(
    val request: RecordResponse,
    val commitment: RecordResponse
)

// la aceptación de una solicitud (RF-GRV-004, api.md §4.6): aceptar es responderla. en una transacción escribe response y
// responded_at en la solicitud, crea el compromiso en draft con `attributes` (sigue WF-D, con su aprobación), guarda
// created_commitment y aplica accept, la transición de comando de WF-E3, con el motivo de la petición. el aporte social
// (creates igual a social_contribution) llega en F5: hoy es 400 INVALID_TARGET. todo como quien llama
@Service
class Acceptances(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun accept(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf(CREATES, "response", "respondedAt", "attributes"))
        if (Values.text(body[CREATES]) != COMMITMENT) {
            throw Finding(
                "INVALID_TARGET",
                "Una solicitud aceptada crea un compromiso (creates igual a commitment); el aporte social llega en F5",
                CREATES,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, COMMITMENT, attributes, setOf("project"))
        if (reason.isNullOrBlank()) throw ValidationException("Aceptar una solicitud exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Aceptar una solicitud", Actions.UPDATE to Attention.REQUEST, Actions.CREATE to COMMITMENT)
        TransitionRoles.require(catalog, user, Attention.REQUEST, listOf(ACCEPT))
        val result =
            transactions.locked("${Attention.REQUEST}:$id") {
                val request = records.get(Attention.REQUEST, id)
                versions.check(Attention.REQUEST, id, expected, request.updatedAt, "La solicitud")
                if (request.state != UNDER_EVALUATION) {
                    throw Finding("STATE_MISMATCH", "Solo se acepta una solicitud en evaluación; esta está en ${request.state}", "record").toCodedException()
                }
                // la respuesta primero: AttentionWriteGuard exige response y responded_at para aplicar accept
                records.patch(Attention.REQUEST, id, mapOf("response" to body["response"], Attention.RESPONDED_AT to body["respondedAt"]), reason)
                val commitment = records.create(COMMITMENT, attributes + ("project" to request.attributes["project"]), reason)
                records.patch(Attention.REQUEST, id, mapOf(AttentionRules.CREATED_COMMITMENT to commitment.id), reason)
                records.transition(Attention.REQUEST, id, ACCEPT, reason)
                RequestAcceptance(records.get(Attention.REQUEST, id), records.get(COMMITMENT, UUID.fromString(commitment.id)))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.request.updatedAt, "/api/objects/$COMMITMENT/records/${result.commitment.id}")
    }

    companion object {
        const val CREATES = "creates"
        const val COMMITMENT = "commitment"
        const val ACCEPT = "accept"
        const val UNDER_EVALUATION = "under_evaluation"
    }
}

// RequestClosureListener (RN-GRV-001, ASM-068, workflows.md WF-E3): en la transacción de `close` de WF-D, como quien cierra el
// compromiso, aplica close_accepted (transición de comando, con la marca) a la solicitud aceptada que lo originó, con el motivo
// «RN-GRV-001: compromiso <código> cerrado». si no puede, lanza y el cierre se revierte. lee las solicitudes como quien cierra:
// una solicitud confidencial que no lee no aparece y queda aceptada (se registra una advertencia; decisión 4 del plan 4.2a)
@Component
@Order(130)
class RequestClosureListener(
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Acceptances.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED || change.transition != CLOSE) return
        val requests =
            records.all(
                Attention.REQUEST,
                filters = mapOf(AttentionRules.CREATED_COMMITMENT to change.recordId.toString()),
                criteria = listOf(Criteria.inState(listOf(ACCEPTED)))
            )
        if (requests.isEmpty()) {
            log.debug("RequestClosureListener: el compromiso {} no tiene una solicitud aceptada que quien cierra lea", change.recordId)
            return
        }
        val code = Values.text((change.after ?: change.before).orEmpty()["code"]) ?: change.recordId.toString()
        for (request in requests) records.transition(Attention.REQUEST, UUID.fromString(request.id), CLOSE_ACCEPTED, "RN-GRV-001: compromiso $code cerrado")
    }

    companion object {
        const val CLOSE = "close"
        const val ACCEPTED = "accepted"
        const val CLOSE_ACCEPTED = "close_accepted"
    }
}

// api.md §4.6: POST /api/sgspe/stakeholder-requests/{id}/accept, con Idempotency-Key
@RestController
@RequestMapping("/api/sgspe/stakeholder-requests")
class AcceptanceController(
    private val acceptances: Acceptances,
    private val idempotency: Idempotency
) {
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
            acceptances.accept(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
```

En `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, reemplazar:

```kotlin
                    mapOf(
                        "close_reclassified_received" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify",
                        "close_reclassified_under_evaluation" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify"
                    )
            )
```

por:

```kotlin
                    mapOf(
                        "close_reclassified_received" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify",
                        "close_reclassified_under_evaluation" to "POST /api/sgspe/stakeholder-requests/{id}/reclassify",
                        "accept" to "POST /api/sgspe/stakeholder-requests/{id}/accept",
                        "close_accepted" to "el cierre del compromiso creado (RequestClosureListener)"
                    )
            )
```

- [ ] **Paso 4: Build**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL` (`GuardTablesCatalogTest` comprueba que `accept` y `close_accepted` existen en WF-E3).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/Acceptances.kt src/main/kotlin/sgspe/common/CommandTransitionGuard.kt \
  src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -F - <<'MSG'
feat: aceptación de solicitudes y cierre por su compromiso

POST /api/sgspe/stakeholder-requests/{id}/accept responde la solicitud, crea el compromiso en draft
y aplica accept en una transacción; RequestClosureListener cierra la solicitud aceptada al cerrarse
su compromiso (RN-GRV-001). La matriz deja de diferir accept y close_unfulfilled.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.attention.StakeholderRequestWorkflowApiTest` y
`sgspe.workflow.WorkflowMatrixApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 11: La derivación de preguntas de reunión

**Files:**
- Create: `src/main/kotlin/sgspe/attention/Referrals.kt`
- Modify: `src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt`, `src/test/kotlin/sgspe/engagement/EngagementWriteGuardTest.kt`
- Test: `src/test/kotlin/sgspe/attention/ReferralApiTest.kt`

**Interfaces:**
- Consumes: `Idempotency.respond`, `StrictBody`, `readable`, el `Clock` de la aplicación.
- Produces: `POST /api/sgspe/engagement-questions/{id}/referral` `{ target, attributes }` → `201 { question, created }`
  (`IK`, `CR`); `Referrals` (`TARGET`, `QUESTION`, `ENGAGEMENT`, `REFERRED_AT`, `CHANNEL`, `FIXED`, `HELD`);
  `EngagementWriteGuard.answered(question)` cuenta `referred_at`.

Escribir `referred_at` como quien deriva exige UPDATE sobre `engagement_question` (decisión 3).

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/engagement/EngagementWriteGuardTest.kt`, reemplazar:

```kotlin
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_on_site" to true)))
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_at" to "2026-05-13T15:00:00Z")))
    }

```

por:

```kotlin
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_on_site" to true)))
        assertTrue(EngagementWriteGuard.answered(mapOf("answered_at" to "2026-05-13T15:00:00Z")))
        // F4: derivada a una QCS
        assertTrue(EngagementWriteGuard.answered(mapOf("referred_at" to "2026-05-13T15:00:00Z")))
    }

```

Crear `src/test/kotlin/sgspe/attention/ReferralApiTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// la derivación de una pregunta de reunión (RF-ENG-006, api.md §4.4): crea la QCS con la pregunta, la interacción, el proyecto,
// el canal REUNION y la recepción de la reunión, marca referred_at y con eso la interacción cierra (RN-ENG-005); una pregunta
// no se deriva dos veces, ni la de una interacción sin realizar
class ReferralApiTest : F4ApiTest() {
    private val officer by lazy { member("COMMUNITY_RELATIONS_OFFICER") }

    // una reunión realizada con acta aprobada y una pregunta pendiente: su id y el de la pregunta
    private fun meeting(held: Boolean = true): Pair<String, String> {
        val meeting = engagement(testProject(), attributes = mapOf("held_at" to "2026-10-05T15:00:00Z", "minutes" to "Se presentó el proyecto."))
        participant(meeting)
        val question = createRecord("engagement_question", mapOf("engagement" to meeting, "question_text" to "¿Quién arregla el canal?"))
        if (held) for (step in listOf("register_held_unplanned", "draft_minutes", "submit_minutes", "approve_minutes")) move("engagement", meeting, step)
        return meeting to question
    }

    private fun refer(
        question: String,
        target: String = "inquiry",
        account: Account = officer,
        attributes: Map<String, Any?> = mapOf("code" to uniqueName("CO-"), "description" to "Pregunta derivada de la reunión.")
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/engagement-questions/$question/referral",
            mapOf("target" to target, "attributes" to attributes),
            account.token,
            withReason("derivada")
        )

    @Test
    fun `derivar crea la consulta de la reunion y deja cerrar la interaccion`() {
        val (meeting, question) = meeting()
        val response = refer(question)
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val created = tree(response.body)["created"]["attributes"]
        assertEquals(question, created["origin_question"].asString())
        assertEquals(meeting, created["origin_engagement"].asString())
        assertEquals(testProject(), created["project"].asString())
        assertEquals("REUNION", created["channel"].asString())
        assertTrue(created["received_at"].asString().startsWith("2026-10-05T15:00"))
        assertTrue(tree(response.body)["question"]["attributes"]["referred_at"].asString().isNotBlank())
        move("engagement", meeting, "close")
    }

    @Test
    fun `una pregunta no se deriva dos veces`() {
        val (_, question) = meeting()
        assertEquals(HttpStatus.CREATED, refer(question).status)
        val again = refer(question, "grievance")
        assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        assertEquals("ALREADY_REFERRED", code(again))
    }

    @Test
    fun `la pregunta de una interaccion sin realizar no se deriva y un destino invalido es INVALID_TARGET`() {
        val (_, question) = meeting(held = false)
        val planned = refer(question)
        assertEquals(HttpStatus.CONFLICT, planned.status, planned.body)
        assertEquals("STATE_MISMATCH", code(planned))
        assertEquals(listOf("engagement"), problemFields(planned.body))
        val invalid = refer(question, "commitment")
        assertEquals(HttpStatus.BAD_REQUEST, invalid.status, invalid.body)
        assertEquals("INVALID_TARGET", code(invalid))
    }

    @Test
    fun `una derivacion confidencial la recibe quien deriva`() {
        val (_, question) = meeting()
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val foreign =
            refer(
                question,
                "grievance",
                attributes =
                    mapOf(
                        "code" to uniqueName("QJ-"),
                        "description" to "Queja planteada en la reunión.",
                        "confidential" to true,
                        "received_by_user_id" to other.id,
                        "received_by_user_email" to other.email
                    )
            )
        assertEquals(HttpStatus.BAD_REQUEST, foreign.status, foreign.body)
        assertEquals("CONFIDENTIAL_RECEIVER_REQUIRED", code(foreign))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.engagement.EngagementWriteGuardTest'
```

Esperado: FAIL en `una pregunta esta atendida si se respondio en el lugar o tiene fecha de respuesta` (una derivada todavía
no cuenta como atendida).

- [ ] **Paso 3: El comando y RN-ENG-005**

Crear `src/main/kotlin/sgspe/attention/Referrals.kt`:

```kotlin
package sgspe.attention

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
import sgspe.common.Attention
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readable
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.util.UUID

data class Referral(
    val question: RecordResponse,
    val created: RecordResponse
)

// la derivación de una pregunta de reunión a una QCS (RF-ENG-006, api.md §4.4): en una transacción crea la QCS de `target` con
// `attributes` (sujetos a AttentionWriteGuard) más origin_question, origin_engagement (la interacción de la pregunta), el
// proyecto de la interacción, el canal REUNION y la recepción igual al held_at de la interacción, y escribe referred_at en la
// pregunta con la marca: EngagementWriteGuard la cuenta como atendida (RN-ENG-005) sin leer la QCS, que puede ser confidencial.
// todo como quien llama: escribir referred_at exige UPDATE sobre engagement_question (decisión 3 del plan 4.2a)
@Service
class Referrals(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val catalog: ModelCatalog,
    private val clock: Clock
) {
    suspend fun refer(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?
    ): CommandAnswer {
        StrictBody.requireOnly(body, setOf(TARGET, "attributes"))
        val target = Values.text(body[TARGET])
        if (target == null || target !in Attention.OBJECTS) {
            throw Finding("INVALID_TARGET", "target es grievance, inquiry o stakeholder_request", TARGET, status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, target, attributes, FIXED + Attention.RECLASSIFIED_FROM.getValue(target).keys)
        if (reason.isNullOrBlank()) throw ValidationException("Derivar una pregunta exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Derivar una pregunta", Actions.UPDATE to QUESTION, Actions.CREATE to target)
        val result =
            transactions.locked("$QUESTION:$id") {
                val question = records.get(QUESTION, id)
                if (Values.text(question.attributes[REFERRED_AT]) != null) {
                    throw Finding("ALREADY_REFERRED", "La pregunta ya se derivó a una QCS", "record").toCodedException()
                }
                val engagementId =
                    Records.uuid(question.attributes[ENGAGEMENT])
                        ?: throw Finding("STATE_MISMATCH", "La pregunta no tiene interacción", ENGAGEMENT).toCodedException()
                val engagement = records.readable(ENGAGEMENT, engagementId)
                if (engagement.state !in HELD) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "Solo se deriva una pregunta de una interacción realizada y sin cerrar; esta está en ${engagement.state}",
                        ENGAGEMENT
                    ).toCodedException()
                }
                val fixed =
                    mapOf(
                        "origin_question" to id.toString(),
                        "origin_engagement" to engagement.id,
                        "project" to engagement.attributes["project"],
                        "channel" to CHANNEL,
                        Attention.RECEIVED_AT to engagement.attributes["held_at"]
                    )
                val created = records.create(target, attributes + fixed, reason)
                records.patch(QUESTION, id, mapOf(REFERRED_AT to clock.instant().toString()), reason)
                Referral(records.get(QUESTION, id), records.get(target, UUID.fromString(created.id)))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.created.updatedAt, "/api/objects/$target/records/${result.created.id}")
    }

    companion object {
        const val TARGET = "target"
        const val QUESTION = "engagement_question"
        const val ENGAGEMENT = "engagement"
        const val REFERRED_AT = "referred_at"
        const val CHANNEL = "REUNION"

        // lo que fija la derivación: la pregunta y la interacción de origen, el proyecto, el canal y la recepción
        val FIXED: Set<String> = setOf("origin_question", "origin_engagement", "project", "channel", Attention.RECEIVED_AT)

        // de held a minutes_approved: realizada y sin cerrar (WF-C)
        val HELD: Set<String> = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved")
    }
}

// api.md §4.4: POST /api/sgspe/engagement-questions/{id}/referral, con Idempotency-Key
@RestController
@RequestMapping("/api/sgspe/engagement-questions")
class ReferralController(
    private val referrals: Referrals,
    private val idempotency: Idempotency
) {
    @PostMapping("/{id}/referral")
    suspend fun refer(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) { referrals.refer(id, body, ChangeReason.fromHeader(reason)) }
    }
}
```

En `src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt`, reemplazar:

```kotlin
// realización con al menos un asistente, minuta para el acta, motivo de cancelación, cierre sin preguntas pendientes
// (RN-ENG-005) y cierre sin acta solo para los tipos que no la exigen (RN-ENG-004, ASM-033). desde F4, una pregunta
// derivada a una QCS también cuenta como atendida; ese paso lo agrega la derivación.
// los asistentes y las preguntas se leen como quien llama, en seco y al escribir por igual: no se puede usar asPlatform
// dentro de una petición. quien no puede leerlos no puede verificar la condición, y es un 403 que lo dice (como las partes
```

por:

```kotlin
// realización con al menos un asistente, minuta para el acta, motivo de cancelación, cierre sin preguntas pendientes
// (RN-ENG-005) y cierre sin acta solo para los tipos que no la exigen (RN-ENG-004, ASM-033). desde F4, una pregunta
// derivada a una QCS (referred_at, que escribe la derivación) también cuenta como atendida, sin leer la QCS.
// los asistentes y las preguntas se leen como quien llama, en seco y al escribir por igual: no se puede usar asPlatform
// dentro de una petición. quien no puede leerlos no puede verificar la condición, y es un 403 que lo dice (como las partes
```

En `src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt`, reemplazar:

```kotlin
        val HELD_STATES = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved", "closed")

        // atendida: respondida en el lugar o con su respuesta posterior (answered_at)
        fun answered(question: Map<String, Any?>): Boolean = Values.bool(question["answered_on_site"]) == true || Values.text(question["answered_at"]) != null
    }
}
```

por:

```kotlin
        val HELD_STATES = setOf("held", "minutes_drafted", "minutes_in_review", "minutes_approved", "closed")

        // atendida: respondida en el lugar, con su respuesta posterior (answered_at) o, desde F4, derivada a una QCS (referred_at)
        fun answered(question: Map<String, Any?>): Boolean =
            Values.bool(question["answered_on_site"]) == true || Values.text(question["answered_at"]) != null ||
                Values.text(question["referred_at"]) != null
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/Referrals.kt src/main/kotlin/sgspe/engagement/EngagementWriteGuard.kt \
  src/test/kotlin/sgspe/engagement/EngagementWriteGuardTest.kt src/test/kotlin/sgspe/attention/ReferralApiTest.kt
git commit -F - <<'MSG'
feat: derivación de preguntas de reunión a QCS

POST /api/sgspe/engagement-questions/{id}/referral crea la QCS con la pregunta, la interacción, el
canal REUNION y la recepción de la reunión y marca referred_at; EngagementWriteGuard cuenta la
pregunta derivada como atendida sin leer la QCS (RN-ENG-005).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.attention.ReferralApiTest` y `sgspe.engagement.EngagementWorkflowGuardsApiTest`
(en la CI del PR, con Testcontainers).

---

### Tarea 12: JOB-01 con las quejas y las consultas

**Files:**
- Modify: `src/main/kotlin/sgspe/deadline/Deadlines.kt`, `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`
- Test: `src/test/kotlin/sgspe/deadline/DeadlinesQcsTest.kt`, `src/test/kotlin/sgspe/deadline/DeadlineJobQcsApiTest.kt`

**Interfaces:**
- Consumes: `QcsDueDates` (Tarea 8), `Attention`, `Organizations.having`, `JobTasks.ensure`.
- Produces: `Deadlines.Tracked` con `dateByState`, `halfFromReceipt`, `escalates` y `dateFieldIn(state)`;
  `Deadlines.TRACKED` con `grievance` e `inquiry`; `Deadlines.due(tracked, state, attributes)` y
  `Deadlines.halfCap(dueSoonDays, received, due)`; `DeadlineJob` que recorre por objeto (`Organizations.having` de cada
  objeto seguido) y escala todo `Tracked` con `escalates`; `DeadlineJob.escalationKey(objectName, id, due, level)` (la de
  tres argumentos sigue para los compromisos) y `DeadlineJob.OVERDUE`.

Un tenant que todavía no tiene el modelo de F4 sigue con F3: la vuelta solo mide en cada organización los objetos que
tiene (especificación §3.5). La tarea de escalamiento de una QCS lleva su vínculo (`grievance` o `inquiry`, un origen de
`ExactlyOneGuard`), sin asignado ni fecha, y su título solo el código (§3.3).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/deadline/DeadlinesQcsTest.kt`:

```kotlin
package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.time.LocalDate

// la situación de plazo de las QCS (workflows.md «Plazos y escalamiento», ASM-100), sin base: la queja mide el acuse en
// `received` y la resolución después; la consulta, la respuesta en `received`; POR VENCER se acota a la mitad del plazo
class DeadlinesQcsTest {
    private val grievance = Deadlines.TRACKED.first { it.objectName == "grievance" }
    private val inquiry = Deadlines.TRACKED.first { it.objectName == "inquiry" }
    private val received = "2026-10-05T15:00:00Z"

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `la queja mide el acuse al recibir y la resolucion despues`() {
        val dates = mapOf("received_at" to received, "ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04")
        assertEquals("VENCIDO", Deadlines.wanted(grievance, "received", dates, date("2026-10-14"), 7))
        assertEquals("AL DIA", Deadlines.wanted(grievance, "acknowledged", dates, date("2026-10-14"), 7))
        assertNull(Deadlines.wanted(grievance, "resolved", dates, date("2026-12-01"), 7))
        assertEquals(date("2026-11-04"), Deadlines.due(grievance, "in_investigation", dates))
    }

    @Test
    fun `el acuse de cinco dias habiles no nace POR VENCER`() {
        // del 5 al 13 hay 8 días: POR VENCER empieza 4 días antes, no 7
        val dates = mapOf("received_at" to received, "ack_due_date" to "2026-10-13")
        assertEquals("AL DIA", Deadlines.wanted(grievance, "received", dates, date("2026-10-05"), 7))
        assertEquals("AL DIA", Deadlines.wanted(grievance, "received", dates, date("2026-10-08"), 7))
        assertEquals("POR VENCER", Deadlines.wanted(grievance, "received", dates, date("2026-10-09"), 7))
        assertEquals(4L, Deadlines.halfCap(7, date("2026-10-05"), date("2026-10-13")))
        assertEquals(7L, Deadlines.halfCap(7, date("2026-10-05"), date("2026-11-04")))
        assertEquals(7L, Deadlines.halfCap(7, null, date("2026-10-13")))
    }

    @Test
    fun `la consulta solo mide su respuesta en received`() {
        val dates = mapOf("received_at" to received, "response_due_date" to "2026-10-20")
        assertEquals("POR VENCER", Deadlines.wanted(inquiry, "received", dates, date("2026-10-14"), 7))
        assertNull(Deadlines.wanted(inquiry, "answered", dates, date("2026-10-30"), 7))
        assertEquals("escalation:inquiry:x:2026-10-20:2", DeadlineJob.escalationKey("inquiry", "x", date("2026-10-20"), 2))
    }

    @Test
    fun `los estados y las fechas de las QCS existen en el modelo, y las solicitudes no se siguen`() {
        val catalog = ModelCatalog.fromClasspath()
        for (tracked in listOf(grievance, inquiry)) {
            assertEquals(emptySet<String>(), tracked.states - catalog.states(tracked.objectName).toSet(), tracked.objectName)
            val dates = (tracked.dateByState.values + tracked.dateField).toSet()
            assertEquals(emptySet<String>(), dates - catalog.derivedFields(tracked.objectName), tracked.objectName)
        }
        assertEquals(false, Deadlines.TRACKED.any { it.objectName == "stakeholder_request" })
    }
}
```

Crear `src/test/kotlin/sgspe/deadline/DeadlineJobQcsApiTest.kt`:

```kotlin
package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// JOB-01 con las QCS (workflows.md «Plazos y escalamiento», ASM-100): la queja vencida queda VENCIDO sin cambiar de estado y
// escala con una tarea por nivel, una sola vez y con solo su código en el título; la consulta respondida deja de medirse; la
// solicitud no tiene plazo
class DeadlineJobQcsApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    private fun escalations(
        objectName: String,
        id: String
    ): List<JsonNode> =
        tree(send("GET", "/api/objects/task/records?$objectName=$id", null, HttpStatus.OK))["content"]
            .values()
            .filter { it["attributes"]["generator_key"]?.asString()?.startsWith("escalation:") == true }

    private fun blank(node: JsonNode?): Boolean = node == null || node.isNull || node.isMissingNode

    @Test
    fun `una queja sin acuse desde enero queda VENCIDO y escala una sola vez`() {
        val id = grievance(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z", "code" to uniqueName("QJ-")))
        val ack = record("grievance", id)["attributes"]["ack_due_date"].asString()
        runBlocking { job.runOnce() }
        val after = record("grievance", id)
        assertEquals("VENCIDO", after["attributes"]["deadline_status"].asString())
        assertEquals("received", after["state"].asString())
        val first = escalations("grievance", id)
        assertEquals(listOf(1, 2).map { "escalation:grievance:$id:$ack:$it" }, first.map { it["attributes"]["generator_key"].asString() }.sorted())
        val code = after["attributes"]["code"].asString()
        assertTrue(first.all { it["attributes"]["title"].asString().endsWith("queja $code vencida") }, first.toString())
        runBlocking { job.runOnce() }
        assertEquals(2, escalations("grievance", id).size)
    }

    @Test
    fun `una consulta respondida deja de medirse y una solicitud no tiene plazo`() {
        val inquiry = inquiry(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z"))
        runBlocking { job.runOnce() }
        assertEquals("VENCIDO", record("inquiry", inquiry)["attributes"]["deadline_status"].asString())
        move("inquiry", inquiry, "answer", member("GRIEVANCE_MANAGER").token, "respuesta")
        runBlocking { job.runOnce() }
        assertTrue(blank(record("inquiry", inquiry)["attributes"]["deadline_status"]))
        val request = stakeholderRequest(testProject(), attributes = mapOf("received_at" to "2026-01-05T15:00:00Z"))
        runBlocking { job.runOnce() }
        assertTrue(blank(record("stakeholder_request", request)["attributes"]["deadline_status"]))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.deadline.DeadlinesQcsTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'due'`, `'halfCap'` y `'dateByState'`).

- [ ] **Paso 3: Las QCS en `Deadlines` y JOB-01 por objeto**

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
package sgspe.deadline

import sgspe.commitment.Commitments
import sgspe.common.Values
import java.time.LocalDate
```

por:

```kotlin
package sgspe.deadline

import sgspe.attention.QcsDueDates
import sgspe.commitment.Commitments
import sgspe.common.Attention
import sgspe.common.Values
import java.time.LocalDate
```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
    const val VALID = "VIGENTE"

    // un objeto que JOB-01 sigue: su campo derivado, la fecha que mide y los estados en que se calcula
    data class Tracked(
        val objectName: String,
```

por:

```kotlin
    const val VALID = "VIGENTE"

    // un objeto que JOB-01 sigue: su campo derivado, la fecha que mide y los estados en que se calcula. desde F4: la fecha que
    // mide un estado si no es la de siempre (el acuse de una queja en `received`), si POR VENCER se acota a la mitad del plazo
    // medido desde la recepción (una QCS, ASM-100) y si escala con tareas (los compromisos y, desde F4, las QCS)
    data class Tracked(
        val objectName: String,
```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
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

```

por:

```kotlin
        val dateField: String,
        val states: Set<String>,
        val validity: Boolean = false,
        val dateByState: Map<String, String> = emptyMap(),
        val halfFromReceipt: Boolean = false,
        val escalates: Boolean = false
    ) {
        fun dateFieldIn(state: String?): String = dateByState[state] ?: dateField
    }

    val TASK_OPEN = setOf("open", "in_progress", "blocked")

    // F3: compromisos, tareas y acuerdos; F4 suma las quejas y las consultas (las solicitudes no tienen plazo en el MVP,
    // ASM-100) y el plan 4.2b, los riesgos
    val TRACKED =
        listOf(
            Tracked(Commitments.COMMITMENT, "deadline_status", "due_date", Commitments.PENDING, escalates = true),
            Tracked("task", "deadline_status", "due_date", TASK_OPEN),
            Tracked("agreement", "validity_status", "expiry_date", setOf("active", "suspended"), validity = true),
            Tracked(
                Attention.GRIEVANCE,
                Attention.DEADLINE_STATUS,
                QcsDueDates.RESOLUTION,
                setOf("received", "acknowledged", "in_assessment", "in_investigation", "in_action"),
                dateByState = mapOf("received" to QcsDueDates.ACK),
                halfFromReceipt = true,
                escalates = true
            ),
            Tracked(Attention.INQUIRY, Attention.DEADLINE_STATUS, QcsDueDates.RESPONSE, setOf("received"), halfFromReceipt = true, escalates = true)
        )

```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
    ): String? {
        if (state !in tracked.states) return null
        val date = Values.text(attributes[tracked.dateField])?.let(LocalDate::parse)
        return if (tracked.validity) validity(date, today, dueSoonDays) else status(date, today, dueSoonDays)
    }

    // el compromiso cuya fecha de vencimiento cambió en esta escritura (desde approved, solo la reprogramación, RF-COM-003):
```

por:

```kotlin
    ): String? {
        if (state !in tracked.states) return null
        val date = due(tracked, state, attributes)
        if (tracked.validity) return validity(date, today, dueSoonDays)
        val soon =
            if (tracked.halfFromReceipt &&
                date != null
            ) {
                halfCap(dueSoonDays, QcsDueDates.receivedDate(attributes[Attention.RECEIVED_AT]), date)
            } else {
                dueSoonDays
            }
        return status(date, today, soon)
    }

    // la fecha que mide un registro en su estado
    fun due(
        tracked: Tracked,
        state: String?,
        attributes: Map<String, Any?>
    ): LocalDate? = Values.text(attributes[tracked.dateFieldIn(state)])?.let(LocalDate::parse)

    // POR VENCER de una QCS: due_soon_days, pero nunca más de la mitad del plazo medido en días calendario desde la recepción,
    // redondeada hacia abajo (ASM-100): el acuse de 5 días hábiles no nace POR VENCER
    fun halfCap(
        dueSoonDays: Long,
        received: LocalDate?,
        due: LocalDate
    ): Long = if (received == null) dueSoonDays else minOf(dueSoonDays, ChronoUnit.DAYS.between(received, due) / 2)

    // el compromiso cuya fecha de vencimiento cambió en esta escritura (desde approved, solo la reprogramación, RF-COM-003):
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Criteria
import sgspe.common.JobTasks
```

por:

```kotlin
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.JobTasks
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin

// JOB-01 DeadlineJob (sgspe.deadline, workflows.md): cada hora, una sola réplica (ClusterLock.tryLock), como la plataforma de
// cada organización con el modelo de F3 (TenantDirectory). escribe deadline_status de compromisos y tareas y validity_status de
// acuerdos con los parámetros de su proyecto, solo si cambia (P11) y bajo el candado del registro, con el motivo que nombra al
// trabajo (P16); crea la tarea de escalamiento de los niveles 1 y 2 de un compromiso vencido (generator_key, sin asignado ni
// fecha: la toma uno de los avisados). nunca aplica transiciones (RN-WFL-001). el día de Lima se lee una vez por vuelta: todas
// las organizaciones se miden con la misma fecha. los avisos de plazo los publica DeadlineNotices, que lee lo que este trabajo
// escribió
@Component
class DeadlineJob(
```

por:

```kotlin

// JOB-01 DeadlineJob (sgspe.deadline, workflows.md): cada hora, una sola réplica (ClusterLock.tryLock), como la plataforma de
// cada organización. recorre por objeto (Organizations.having): un tenant sin el modelo de F4 sigue con los objetos de F3.
// escribe deadline_status de compromisos, tareas, quejas y consultas y validity_status de acuerdos con los parámetros de su
// proyecto, solo si cambia (P11) y bajo el candado del registro, con el motivo que nombra al trabajo (P16); crea la tarea de
// escalamiento de los niveles 1 y 2 de un compromiso, una queja o una consulta vencidos (generator_key, sin asignado ni fecha:
// la toma uno de los avisados; su título solo lleva el código). nunca aplica transiciones (RN-WFL-001). el día de Lima se lee
// una vez por vuelta: todas las organizaciones se miden con la misma fecha. los avisos de plazo los publica DeadlineNotices,
// que lee lo que este trabajo escribió
@Component
class DeadlineJob(
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
        return lease.use {
            val today = LocalDate.now(clock.withZone(LIMA))
            DeadlineSweep(
                { organizations.having(Commitments.COMMITMENT) },
                { organization, block -> records.asPlatform(organization, SOURCE, block) },
                { organization -> refresh(organization, today) }
            ).run()
        }
```

por:

```kotlin
        return lease.use {
            val today = LocalDate.now(clock.withZone(LIMA))
            // las organizaciones que tienen cada objeto seguido, una vez por vuelta
            val having =
                Deadlines.TRACKED
                    .map { it.objectName }
                    .distinct()
                    .associateWith { organizations.having(it).toSet() }
            DeadlineSweep(
                {
                    having.values
                        .flatten()
                        .distinct()
                        .sorted()
                },
                { organization, block -> records.asPlatform(organization, SOURCE, block) },
                { organization -> refresh(organization, today, Deadlines.TRACKED.filter { organization in having.getValue(it.objectName) }) }
            ).run()
        }
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
    private suspend fun refresh(
        organization: UUID,
        today: LocalDate
    ): Int {
        val projects = mutableMapOf<String, Map<String, Any?>>()
```

por:

```kotlin
    private suspend fun refresh(
        organization: UUID,
        today: LocalDate,
        tracked: List<Deadlines.Tracked>
    ): Int {
        val projects = mutableMapOf<String, Map<String, Any?>>()
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
        var created = 0
        var failed = 0
        for (tracked in Deadlines.TRACKED) {
            // solo los estados donde se calcula, más los que salieron de ellos con el campo todavía lleno (hay que vaciarlo):
            // los registros cerrados que ya se vaciaron no se vuelven a leer cada hora
```

por:

```kotlin
        var created = 0
        var failed = 0
        for (tracked in tracked) {
            // solo los estados donde se calcula, más los que salieron de ellos con el campo todavía lleno (hay que vaciarlo):
            // los registros cerrados que ya se vaciaron no se vuelven a leer cada hora
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
                    if (outcome.written) written++
                    // JobTasks.ensure va fuera de toda transacción: write ya cerró la suya (un único duplicado aborta la abierta)
                    if (tracked.objectName == Commitments.COMMITMENT) created += escalate(row, outcome.locked, today, params)
                } catch (e: CancellationException) {
                    throw e
```

por:

```kotlin
                    if (outcome.written) written++
                    // JobTasks.ensure va fuera de toda transacción: write ya cerró la suya (un único duplicado aborta la abierta)
                    if (tracked.escalates) created += escalate(tracked, row, outcome.locked, today, params)
                } catch (e: CancellationException) {
                    throw e
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
    }

    // la fecha de vencimiento y el nivel alcanzado de un compromiso vencido y pendiente; null si no escala
    private fun escalation(
        record: RecordResponse,
        today: LocalDate,
        params: Map<String, Any?>
    ): Pair<LocalDate, Int>? {
        if (record.state !in Commitments.PENDING) return null
        val due = Values.text(record.attributes["due_date"])?.let(LocalDate::parse) ?: return null
        if (!today.isAfter(due)) return null
        val level =
```

por:

```kotlin
    }

    // la fecha de vencimiento y el nivel alcanzado de un registro vencido en un estado con plazo; null si no escala
    private fun escalation(
        tracked: Deadlines.Tracked,
        record: RecordResponse,
        today: LocalDate,
        params: Map<String, Any?>
    ): Pair<LocalDate, Int>? {
        if (record.state !in tracked.states) return null
        val due = Deadlines.due(tracked, record.state, record.attributes) ?: return null
        if (!today.isAfter(due)) return null
        val level =
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
    }

    // la tarea de cada nivel alcanzado de un compromiso vencido y pendiente: una por compromiso, fecha y nivel. decide con el
    // registro releído bajo el candado de write o, si write no lo tomó, con uno releído ahora: la fila de la lectura inicial
    // puede tener horas en una organización grande, y una reprogramación o un cumplimiento entretanto no deben escalar
    private suspend fun escalate(
        row: RecordResponse,
        locked: RecordResponse?,
```

por:

```kotlin
    }

    // la tarea de cada nivel alcanzado de un registro vencido: una por registro, fecha y nivel. decide con el registro releído
    // bajo el candado de write o, si write no lo tomó, con uno releído ahora: la fila de la lectura inicial puede tener horas en
    // una organización grande, y una reprogramación, un cumplimiento o una respuesta entretanto no deben escalar. el título y la
    // descripción solo llevan el tipo, el código y la fecha: nunca texto libre ni datos de una QCS confidencial
    private suspend fun escalate(
        tracked: Deadlines.Tracked,
        row: RecordResponse,
        locked: RecordResponse?,
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
        params: Map<String, Any?>
    ): Int {
        if (locked == null && escalation(row, today, params) == null) return 0
        val record = locked ?: records.find(Commitments.COMMITMENT, UUID.fromString(row.id)) ?: return 0
        val (due, level) = escalation(record, today, params) ?: return 0
        val code = Values.text(record.attributes["code"]) ?: record.id
        return (1..level).count { reached ->
            tasks.ensure(
                escalationKey(record.id, due, reached),
                mapOf(
                    "title" to "Escalamiento nivel $reached: compromiso $code vencido",
                    "description" to "El compromiso venció el $due y sigue abierto. Tómela quien atienda el escalamiento.",
                    "project" to record.attributes["project"],
                    "commitment" to record.id
                ),
                TASK_REASON
```

por:

```kotlin
        params: Map<String, Any?>
    ): Int {
        if (locked == null && escalation(tracked, row, today, params) == null) return 0
        val record = locked ?: records.find(tracked.objectName, UUID.fromString(row.id)) ?: return 0
        val (due, level) = escalation(tracked, record, today, params) ?: return 0
        val code = Values.text(record.attributes["code"]) ?: record.id
        return (1..level).count { reached ->
            tasks.ensure(
                escalationKey(tracked.objectName, record.id, due, reached),
                mapOf(
                    "title" to "Escalamiento nivel $reached: ${OVERDUE.getValue(tracked.objectName).format(code)}",
                    "description" to "El plazo venció el $due y sigue abierto. Tómela quien atienda el escalamiento.",
                    "project" to record.attributes["project"],
                    tracked.objectName to record.id
                ),
                TASK_REASON
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin

        fun escalationKey(
            commitment: String,
            due: LocalDate,
            level: Int
        ): String = "escalation:${Commitments.COMMITMENT}:$commitment:$due:$level"
    }
}
```

por:

```kotlin

        fun escalationKey(
            objectName: String,
            id: String,
            due: LocalDate,
            level: Int
        ): String = "escalation:$objectName:$id:$due:$level"

        // la de un compromiso (F3)
        fun escalationKey(
            commitment: String,
            due: LocalDate,
            level: Int
        ): String = escalationKey("commitment", commitment, due, level)

        // cómo nombra la tarea de escalamiento al registro vencido, solo con su código
        val OVERDUE: Map<String, String> = mapOf("commitment" to "compromiso %s vencido", "grievance" to "queja %s vencida", "inquiry" to "consulta %s vencida")
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `DeadlinesQcsTest` con 4 pruebas y `DeadlinesTest` sin cambios en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/deadline/Deadlines.kt src/main/kotlin/sgspe/deadline/DeadlineJob.kt \
  src/test/kotlin/sgspe/deadline/DeadlinesQcsTest.kt src/test/kotlin/sgspe/deadline/DeadlineJobQcsApiTest.kt
git commit -F - <<'MSG'
feat: JOB-01 mide los plazos de quejas y consultas

DeadlineJob recorre por objeto con Organizations.having y escribe deadline_status de las quejas y
consultas, con POR VENCER acotado a la mitad del plazo; sus tareas de escalamiento de nivel 1 y 2
llevan solo el código de la QCS (ASM-100).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.deadline.DeadlineJobQcsApiTest` y `sgspe.deadline.DeadlineJobApiTest` (en la CI
del PR, con Testcontainers).

---

### Tarea 13: Los avisos de plazo de las QCS y el aviso genérico de nivel 2

**Files:**
- Create: `src/main/kotlin/sgspe/notification/ConfidentialReaders.kt`
- Modify: `src/main/kotlin/sgspe/common/ModelCatalog.kt`, `src/main/kotlin/sgspe/notification/Notices.kt` (`NoticeTexts`),
  `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`
- Test: `src/test/kotlin/sgspe/deadline/QcsNoticePlanTest.kt`, `src/test/kotlin/sgspe/deadline/QcsNoticesApiTest.kt`

**Interfaces:**
- Consumes: `Assignments.ofProject`, `Assignment.inForce`, `Deadlines.due` (Tarea 12).
- Produces: `ModelCatalog.grants: Map<String, Map<String, Set<String>>>` y `rolesGranting(objectName, action): Set<String>`;
  `ConfidentialReaders` (`roles(objectName, attributes)`, `filter(objectName, attributes, users, assignments, today):
  List<UUID>?`, `fingerprint(objectName, id, due)`); `NoticeTexts.qcsDueSoon`, `qcsOverdue`, `qcsConfidentialOverdue`;
  `PlannedNotice.level`; `DeadlineNoticePlan.QCS_NEXT`, `QCS_LABEL` y `qcs(...)`; `DeadlineNoticePlan.TASK_REFERENCE` con
  las QCS; `DeadlineNotices` con un `ConfidentialReaders` más en su constructor.

Las claves y los destinatarios son los de `workflows.md` («Avisos» y la regla 7 de «Destinatarios por rol») y §3.5 de la
especificación. La fuente lee como la plataforma (ve todo) y filtra la salida: un aviso sobre una QCS confidencial, y el
de una tarea vencida vinculada a ella, solo llega a quien la vería; si un nivel queda sin destinatarios no hay aviso (con
la advertencia de siempre). En el nivel 2 de una confidencial, los roles de `approve_senior` de WF-D que no la verían
reciben `qcs_confidential_overdue`: título y texto fijos salvo el nombre del proyecto, sin enlace y con la huella en la
clave.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/deadline/QcsNoticePlanTest.kt`:

```kotlin
package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.notification.ConfidentialReaders
import sgspe.notification.NoticeTexts
import sgspe.scope.Assignment
import java.time.LocalDate
import java.util.UUID

// los avisos de plazo de una QCS (workflows.md «Avisos» y «Destinatarios por rol», ASM-102), sin base: a quién va cada nivel,
// quién vería una confidencial y que el aviso genérico del nivel 2 no lleva datos del registro
class QcsNoticePlanTest {
    private val due = LocalDate.parse("2026-10-13")
    private val today = LocalDate.parse("2026-11-01")
    private val owner = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()
    private val readers = ConfidentialReaders(catalog)

    private fun assignment(role: String) = Assignment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "persona@sgspe.test", role, true, null, null)

    @Test
    fun `por vencer va al responsable o, sin el, a los roles de la transicion que sigue`() {
        val withOwner = DeadlineNoticePlan.qcs("grievance", "q", "Queja QJ-1", "received", "POR VENCER", due, 0, owner)
        assertEquals(listOf("qcs_deadline:grievance:q:2026-10-13:POR VENCER"), withOwner.map { it.key })
        assertEquals(NoticeAudience.Person(owner), withOwner.single().audience)
        val without = DeadlineNoticePlan.qcs("inquiry", "c", "Consulta CO-1", "received", "POR VENCER", due, 0, null)
        assertEquals(NoticeAudience.Roles("inquiry", "answer"), without.single().audience)
    }

    @Test
    fun `vencida escala a verify de WF-E1 y a approve_senior de WF-D`() {
        val plan = DeadlineNoticePlan.qcs("grievance", "q", "Queja QJ-1", "in_action", "VENCIDO", due, 2, null)
        assertEquals((0..2).map { "qcs_deadline:grievance:q:2026-10-13:$it" }, plan.map { it.key })
        assertEquals(
            listOf(NoticeAudience.Roles("grievance", "resolve"), NoticeAudience.Roles("grievance", "verify"), DeadlineNoticePlan.SENIOR),
            plan.map { it.audience }
        )
        assertEquals(listOf(0, 1, 2), plan.map { it.level })
    }

    @Test
    fun `una confidencial solo la ven su responsable, quien la recibio, READ_CONFIDENTIAL y en una grave APPROVE`() {
        val receiver = UUID.randomUUID()
        val manager = assignment("GRIEVANCE_MANAGER")
        val approver = assignment("APPROVER")
        val social = assignment("SOCIAL_MANAGER")
        val team = listOf(manager, approver, social)
        val users = listOf(owner, receiver, manager.memberUserId, approver.memberUserId, social.memberUserId)
        val low = mapOf("confidential" to true, "owner_user_id" to owner.toString(), "received_by_user_id" to receiver.toString(), "severity" to "BAJA")
        assertEquals(listOf(owner, receiver, manager.memberUserId), readers.filter("grievance", low, users, team, today))
        val high = low + ("severity" to "CRITICA")
        assertEquals(listOf(owner, receiver, manager.memberUserId, approver.memberUserId), readers.filter("grievance", high, users, team, today))
        assertEquals(null, readers.filter("grievance", low + ("confidential" to false), users, team, today))
    }

    @Test
    fun `el aviso generico solo lleva el nombre del proyecto y su clave una huella`() {
        val (title, body) = NoticeTexts.qcsConfidentialOverdue("Proyecto Ejemplo")
        assertEquals("QCS confidencial fuera de plazo", title)
        assertEquals("Una QCS confidencial del proyecto Proyecto Ejemplo superó su plazo. Pida el detalle al equipo de quejas.", body)
        val id = UUID.randomUUID().toString()
        val print = ConfidentialReaders.fingerprint("grievance", id, due)
        assertEquals(16, print.length)
        assertTrue(print.all { it in "0123456789abcdef" })
        assertFalse(print.contains(id.take(8)))
        assertEquals(print, ConfidentialReaders.fingerprint("grievance", id, due))
    }

    @Test
    fun `las transiciones de los avisos existen en los workflows`() {
        for ((objectName, next) in DeadlineNoticePlan.QCS_NEXT) {
            for ((state, transition) in next) assertEquals(state, catalog.transition(objectName, transition)?.from, "$objectName.$transition")
        }
        assertEquals(setOf("GRIEVANCE_MANAGER"), catalog.rolesGranting("inquiry", "READ_CONFIDENTIAL"))
    }
}
```

Crear `src/test/kotlin/sgspe/deadline/QcsNoticesApiTest.kt`:

```kotlin
package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import sgspe.F4ApiTest
import sgspe.common.Records
import wasichai.notifications.Audience
import wasichai.notifications.NotificationDraft
import java.time.Instant
import java.util.UUID

// los avisos de plazo de las QCS como fuente de wasichai-notifications (ASM-102): qcs_deadline por nivel con sus destinatarios
// y, en una QCS confidencial, solo a quien la vería, con el aviso genérico del nivel 2 para los roles de approve_senior que no
// la ven: sin código, sin enlace y con una clave sin el id
class QcsNoticesApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    @Autowired
    private lateinit var notices: DeadlineNotices

    @Autowired
    private lateinit var records: Records

    private fun drafts(): List<NotificationDraft> {
        val organization = organizationId()
        return runBlocking { records.asPlatform(organization) { notices.currentNotifications(organization, Instant.now()) } }
    }

    private fun users(draft: NotificationDraft): Set<UUID> = draft.audience.map { (it as Audience.User).id }.toSet()

    private fun ids(vararg accounts: Account): Set<UUID> = accounts.map { UUID.fromString(it.id) }.toSet()

    @Test
    fun `una queja vencida avisa al responsable, a verify y a approve_senior`() {
        val owner = member("GRIEVANCE_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val senior = member("PROJECT_MANAGER")
        val id =
            grievance(
                testProject(),
                attributes =
                    mapOf(
                        "received_at" to "2026-01-05T15:00:00Z",
                        "owner_user_id" to owner.id,
                        "owner_user_email" to owner.email
                    )
            )
        runBlocking { job.runOnce() }
        val mine = drafts().filter { it.key?.startsWith("qcs_deadline:grievance:$id:") == true }.associateBy { it.key!!.substringAfterLast(":") }
        assertEquals(setOf("0", "1", "2"), mine.keys)
        assertEquals(ids(owner), users(mine.getValue("0")))
        assertTrue(users(mine.getValue("1")).containsAll(ids(owner, social)))
        assertEquals(ids(senior), users(mine.getValue("2")))
    }

    @Test
    fun `una queja confidencial solo avisa a quien la veria y el nivel 2 generico no lleva datos`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val other = member("COMMUNITY_RELATIONS_OFFICER")
        val manager = member("GRIEVANCE_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val senior = member("PROJECT_MANAGER")
        val id = grievance(testProject(), receiver, mapOf("received_at" to "2026-01-05T15:00:00Z", "confidential" to true))
        val code = record("grievance", id)["attributes"]["code"].asString()
        runBlocking { job.runOnce() }
        val all = drafts()
        val mine = all.filter { it.key?.startsWith("qcs_deadline:grievance:$id:") == true }.associateBy { it.key!!.substringAfterLast(":") }
        // sin responsable, el nivel 0 va a los roles de acknowledge que la verían: quien la recibió y el gestor de quejas, no
        // otro relacionista
        assertEquals(ids(receiver, manager), users(mine.getValue("0")))
        assertFalse(UUID.fromString(other.id) in users(mine.getValue("0")))
        assertEquals(ids(manager), users(mine.getValue("1")))
        assertFalse(users(mine.getValue("1")).containsAll(ids(social)))
        assertNull(mine["2"])
        val generic = all.filter { it.key?.startsWith("qcs_confidential_overdue:${testProject()}:") == true }
        assertEquals(1, generic.size, all.map { it.key }.toString())
        assertEquals(ids(senior), users(generic.single()))
        assertNull(generic.single().link)
        assertFalse(generic.single().key!!.contains(id))
        assertFalse((generic.single().title + generic.single().body).contains(code))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.deadline.QcsNoticePlanTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ConfidentialReaders'`, `'qcs'` y `'rolesGranting'`).

- [ ] **Paso 3: Las concesiones de los roles, quién vería una QCS y los avisos**

En `src/main/kotlin/sgspe/common/ModelCatalog.kt`, reemplazar:

```kotlin
            .map { it.path("name").asString() }
            .toSet()

    private val transitionsByObject: Map<String, List<Transition>> =
```

por:

```kotlin
            .map { it.path("name").asString() }
            .toSet()

    // las concesiones de roles.json por rol: objeto -> acciones (READ, CREATE, UPDATE, DELETE, `u` y las declaradas). desde F4
    // deciden, sin literales, quién vería una QCS confidencial (los avisos de ASM-102)
    val grants: Map<String, Map<String, Set<String>>> =
        roles.path("roles").values().associate { role ->
            role.path("name").asString() to
                role.path("permisos").properties().associate { (objectName, actions) -> objectName to actions.values().map { it.asString() }.toSet() }
        }

    // los roles que tienen `action` sobre `objectName` en roles.json
    fun rolesGranting(
        objectName: String,
        action: String
    ): Set<String> = grants.filterValues { action in it[objectName].orEmpty() }.keys

    private val transitionsByObject: Map<String, List<Transition>> =
```

En `src/main/kotlin/sgspe/notification/Notices.kt`, reemplazar:

```kotlin
            "Compromiso vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierto: revise con su responsable."
        }

    fun taskOverdue(
```

por:

```kotlin
            "Compromiso vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierto: revise con su responsable."
        }

    // F4: el plazo de una QCS (ASM-100, ASM-102). `label` es «Queja <código>» o «Consulta <código>»: el tipo y el código, nunca
    // la descripción, la persona ni otro texto libre
    fun qcsDueSoon(
        label: String,
        due: LocalDate
    ): Pair<String, String> = "Plazo por vencer: $label" to "Vence el ${due.format(DATE)}."

    fun qcsOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Plazo vencido: $label" to "Venció el ${due.format(DATE)}."
        } else {
            "Plazo vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue sin atender."
        }

    // el aviso genérico del nivel 2 de una QCS confidencial a quien no la ve (ASM-102): fijo salvo el nombre del proyecto, sin
    // código, título, tipo, fecha, nivel, categoría ni enlace al registro
    fun qcsConfidentialOverdue(project: String): Pair<String, String> =
        "QCS confidencial fuera de plazo" to "Una QCS confidencial del proyecto $project superó su plazo. Pida el detalle al equipo de quejas."

    fun taskOverdue(
```

Crear `src/main/kotlin/sgspe/notification/ConfidentialReaders.kt`:

```kotlin
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
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Criteria
import sgspe.common.LIMA
```

por:

```kotlin
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Attention
import sgspe.common.Criteria
import sgspe.common.LIMA
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
import sgspe.common.Records
import sgspe.common.Values
import sgspe.notification.NoticeTexts
import sgspe.notification.Notices
import sgspe.scope.Assignments
import wasichai.core.common.NotFoundException
```

por:

```kotlin
import sgspe.common.Records
import sgspe.common.Values
import sgspe.notification.ConfidentialReaders
import sgspe.notification.NoticeTexts
import sgspe.notification.Notices
import sgspe.scope.Assignment
import sgspe.scope.Assignments
import wasichai.core.common.NotFoundException
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
}

// un aviso de plazo planeado: su clave, su tipo, su texto y a quién va, antes de resolver los destinatarios
data class PlannedNotice(
    val key: String,
    val kind: NotificationKind,
    val text: Pair<String, String>,
    val audience: NoticeAudience
)

```

por:

```kotlin
}

// un aviso de plazo planeado: su clave, su tipo, su texto y a quién va, antes de resolver los destinatarios. `level`: el nivel
// de escalamiento de un aviso de vencido (null en uno por vencer); el nivel 2 de una QCS confidencial suma el aviso genérico
data class PlannedNotice(
    val key: String,
    val kind: NotificationKind,
    val text: Pair<String, String>,
    val audience: NoticeAudience,
    val level: Int? = null
)

```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
// 1; los de approve_senior de WF-D en el nivel 2. cada nivel alcanzado sigue abierto mientras dure el atraso
object DeadlineNoticePlan {
    // la transición de referencia del nivel 1 de una tarea, por su registro vinculado (workflows.md); F4 suma QCS y riesgos
    val TASK_REFERENCE =
        linkedMapOf(
```

por:

```kotlin
// 1; los de approve_senior de WF-D en el nivel 2. cada nivel alcanzado sigue abierto mientras dure el atraso
object DeadlineNoticePlan {
    // la transición de referencia del nivel 1 de una tarea, por su registro vinculado (workflows.md); F4 suma las QCS (verify
    // de WF-E1, como el nivel 1 de sus plazos) y el plan 4.2b, los riesgos
    val TASK_REFERENCE =
        linkedMapOf(
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
            "engagement" to ("engagement" to "approve_minutes"),
            "agreement" to ("agreement" to "activate"),
            "stakeholder" to ("stakeholder" to "assess")
        )
    val SENIOR = NoticeAudience.Roles(Commitments.COMMITMENT, "approve_senior")

```

por:

```kotlin
            "engagement" to ("engagement" to "approve_minutes"),
            "agreement" to ("agreement" to "activate"),
            "stakeholder" to ("stakeholder" to "assess"),
            Attention.GRIEVANCE to (Attention.GRIEVANCE to "verify"),
            Attention.INQUIRY to (Attention.GRIEVANCE to "verify"),
            Attention.REQUEST to (Attention.GRIEVANCE to "verify")
        )

    // la transición que sigue en cada estado con plazo de una QCS: sus roles reciben el nivel 0 de una QCS sin responsable
    val QCS_NEXT: Map<String, Map<String, String>> =
        mapOf(
            Attention.GRIEVANCE to
                mapOf(
                    "received" to "acknowledge",
                    "acknowledged" to "assess",
                    "in_assessment" to "start_action",
                    "in_investigation" to "start_action_after_investigation",
                    "in_action" to "resolve"
                ),
            Attention.INQUIRY to mapOf("received" to "answer")
        )

    // cómo nombra el aviso a una QCS: su tipo y su código
    val QCS_LABEL: Map<String, String> = mapOf(Attention.GRIEVANCE to "Queja", Attention.INQUIRY to "Consulta")

    // una queja o una consulta (workflows.md «Avisos», ASM-102): POR VENCER al responsable o, sin él, a los roles de la
    // transición que sigue; vencida, el nivel 0 igual, el 1 a los roles de verify de WF-E1 y el 2 a los de approve_senior de
    // WF-D, porque WF-E no tiene aprobación superior
    fun qcs(
        objectName: String,
        id: String,
        label: String,
        state: String?,
        status: String?,
        due: LocalDate,
        level: Int,
        owner: UUID?
    ): List<PlannedNotice> {
        val first = owner?.let { NoticeAudience.Person(it) } ?: QCS_NEXT[objectName]?.get(state)?.let { NoticeAudience.Roles(objectName, it) }
        val key = "qcs_deadline:$objectName:$id:$due"
        return when (status) {
            Deadlines.DUE_SOON ->
                listOfNotNull(
                    first?.let {
                        PlannedNotice("$key:${Deadlines.DUE_SOON}", NotificationKind.WARNING, NoticeTexts.qcsDueSoon(label, due), it)
                    }
                )
            Deadlines.OVERDUE ->
                (0..level).mapNotNull { reached ->
                    val audience =
                        when (reached) {
                            0 -> first
                            1 -> NoticeAudience.Roles(Attention.GRIEVANCE, "verify")
                            else -> SENIOR
                        } ?: return@mapNotNull null
                    PlannedNotice("$key:$reached", NotificationKind.ACTION, NoticeTexts.qcsOverdue(label, due, reached), audience, reached)
                }
            else -> emptyList()
        }
    }

    val SENIOR = NoticeAudience.Roles(Commitments.COMMITMENT, "approve_senior")

```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
// cumplido, una tarea hecha) se resuelve solo, y lo que no cambió no se vuelve a escribir. lee deadline_status, que mantiene
// JOB-01, así que un aviso llega a lo sumo una hora después del cambio de situación. un aviso a roles llega a los asignados
// vigentes del proyecto del registro con ese role_name (ASM-070); sin coincidencias no hay aviso (workflows.md)
@Component
class DeadlineNotices(
    private val records: Records,
    private val assignments: Assignments
) : NotificationSource {
    private val log = LoggerFactory.getLogger(javaClass)
```

por:

```kotlin
// cumplido, una tarea hecha) se resuelve solo, y lo que no cambió no se vuelve a escribir. lee deadline_status, que mantiene
// JOB-01, así que un aviso llega a lo sumo una hora después del cambio de situación. un aviso a roles llega a los asignados
// vigentes del proyecto del registro con ese role_name (ASM-070); sin coincidencias no hay aviso (workflows.md). desde F4 suma
// las quejas y las consultas (qcs_deadline) en las organizaciones que las tienen; el aviso de una QCS confidencial, y el de una
// tarea vinculada a ella, solo va a quien la vería (ConfidentialReaders, ASM-102), y en el nivel 2 los roles de approve_senior
// que no la ven reciben qcs_confidential_overdue, sin datos del registro ni enlace
@Component
class DeadlineNotices(
    private val records: Records,
    private val assignments: Assignments,
    private val readers: ConfidentialReaders
) : NotificationSource {
    private val log = LoggerFactory.getLogger(javaClass)
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
            }
        val tasks = records.all(TASK, criteria = listOf(Criteria.inState(Deadlines.TASK_OPEN)))
        val projects = mutableMapOf<String, Map<String, Any?>>()
        val recipients = mutableMapOf<Triple<UUID, String, String>, List<UUID>>()

        suspend fun params(id: String): Map<String, Any?> = projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }
```

por:

```kotlin
            }
        val tasks = records.all(TASK, criteria = listOf(Criteria.inState(Deadlines.TASK_OPEN)))
        val qcs = Deadlines.TRACKED.filter { it.objectName in Attention.OBJECTS }.associateWith { tracked -> qcsRows(tracked) }
        val projects = mutableMapOf<String, Map<String, Any?>>()
        val recipients = mutableMapOf<Triple<UUID, String, String>, List<UUID>>()
        val teams = mutableMapOf<UUID, List<Assignment>>()

        suspend fun team(project: UUID): List<Assignment> = teams.getOrPut(project) { assignments.ofProject(project) }

        suspend fun params(id: String): Map<String, Any?> = projects.getOrPut(id) { records.find("project", UUID.fromString(id))?.attributes.orEmpty() }
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
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
                if (users.isEmpty()) {
                    // sin coincidencias no hay aviso y queda la advertencia (workflows.md, «Destinatarios por rol», punto 4);
```

por:

```kotlin
        val drafts = mutableListOf<NotificationDraft>()

        // `confidential`: la QCS (su objeto y sus atributos) cuyo aviso solo va a quien la vería; null si no es confidencial
        suspend fun add(
            row: RecordResponse,
            objectName: String,
            plan: List<PlannedNotice>,
            due: LocalDate,
            confidential: Pair<String, Map<String, Any?>>? = null
        ) {
            val project = Records.uuid(row.attributes["project"]) ?: return
            for (notice in plan) {
                val all = users(project, notice.audience)
                val users = confidential?.let { (qcs, attributes) -> readers.filter(qcs, attributes, all, team(project), today) } ?: all
                if (confidential != null && notice.level == 2 && objectName == confidential.first) {
                    // el nivel 2 de una QCS confidencial: quienes no la ven reciben el aviso genérico (ASM-102)
                    val blind = all - users.toSet()
                    if (blind.isNotEmpty()) {
                        val name = Values.text(params(project.toString())["name"]) ?: Values.text(params(project.toString())["code"]) ?: project.toString()
                        val key = "qcs_confidential_overdue:$project:${ConfidentialReaders.fingerprint(objectName, row.id, due)}"
                        drafts += Notices.draft(key, NotificationKind.ACTION, NoticeTexts.qcsConfidentialOverdue(name), blind, null, null)
                    }
                }
                if (users.isEmpty()) {
                    // sin coincidencias no hay aviso y queda la advertencia (workflows.md, «Destinatarios por rol», punto 4);
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
            add(row, Commitments.COMMITMENT, plan, due)
        }
        for (row in tasks) {
            val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: continue
```

por:

```kotlin
            add(row, Commitments.COMMITMENT, plan, due)
        }
        for ((tracked, rows) in qcs) {
            for (row in rows) {
                val due = Deadlines.due(tracked, row.state, row.attributes) ?: continue
                val params = params(Values.text(row.attributes["project"]) ?: continue)
                val label = "${DeadlineNoticePlan.QCS_LABEL.getValue(tracked.objectName)} ${Values.text(row.attributes["code"]) ?: row.id}"
                val plan =
                    DeadlineNoticePlan.qcs(
                        tracked.objectName,
                        row.id,
                        label,
                        row.state,
                        Values.text(row.attributes[tracked.field]),
                        due,
                        level(due, today, params),
                        Records.uuid(row.attributes[Attention.OWNER])
                    )
                val confidential = if (Attention.isConfidential(row.attributes)) tracked.objectName to row.attributes else null
                add(row, tracked.objectName, plan, due, confidential)
            }
        }
        // las QCS confidenciales a las que apunta una tarea: su aviso de vencida también se filtra (ASM-102)
        val linkedQcs = linkedConfidential(tasks)
        for (row in tasks) {
            val due = Values.text(row.attributes["due_date"])?.let(LocalDate::parse) ?: continue
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
                    linked
                )
            add(row, TASK, plan, due)
        }
        return drafts
    }

    private fun level(
```

por:

```kotlin
                    linked
                )
            add(row, TASK, plan, due, linked?.let { linkedQcs[it to Values.text(row.attributes[it])] }?.let { linked to it })
        }
        return drafts
    }

    // las QCS de una organización con situación de plazo, en los estados que se miden; vacío si la organización no tiene el
    // modelo de F4
    private suspend fun qcsRows(tracked: Deadlines.Tracked): List<RecordResponse> =
        try {
            records
                .all(tracked.objectName, criteria = listOf(Criteria.inState(tracked.states)))
                .filter { Values.text(it.attributes[tracked.field]) in setOf(Deadlines.DUE_SOON, Deadlines.OVERDUE) }
        } catch (_: NotFoundException) {
            emptyList()
        }

    // las QCS confidenciales vinculadas a las tareas, por (objeto, id): una lectura por objeto
    private suspend fun linkedConfidential(tasks: List<RecordResponse>): Map<Pair<String, String?>, Map<String, Any?>> =
        Attention.OBJECTS
            .flatMap { objectName ->
                val ids = tasks.mapNotNull { Records.uuid(it.attributes[objectName]) }.distinct()
                if (ids.isEmpty()) return@flatMap emptyList()
                records
                    .all(objectName, ids = ids)
                    .filter { Attention.isConfidential(it.attributes) }
                    .map { (objectName to it.id) to it.attributes }
            }.toMap()

    private fun level(
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `QcsNoticePlanTest` con 5 pruebas y `DeadlineNoticePlanTest` sin cambios en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/ModelCatalog.kt src/main/kotlin/sgspe/notification/Notices.kt \
  src/main/kotlin/sgspe/notification/ConfidentialReaders.kt src/main/kotlin/sgspe/deadline/DeadlineNotices.kt \
  src/test/kotlin/sgspe/deadline/QcsNoticePlanTest.kt src/test/kotlin/sgspe/deadline/QcsNoticesApiTest.kt
git commit -F - <<'MSG'
feat: avisos de plazo de las QCS sin delatar las confidenciales

DeadlineNotices publica qcs_deadline por nivel; el de una QCS confidencial, y el de una tarea
vinculada, solo llega a quien la vería según roles.json, y en el nivel 2 quien no la ve recibe
qcs_confidential_overdue, sin datos del registro ni enlace (ASM-102).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.deadline.QcsNoticesApiTest` y `sgspe.deadline.DeadlineJobApiTest` (en la CI del
PR, con Testcontainers).

---

### Tarea 14: El aviso de una queja grave

**Files:**
- Create: `src/main/kotlin/sgspe/notification/GraveGrievanceNotices.kt`
- Test: `src/test/kotlin/sgspe/notification/GraveGrievanceNoticeTest.kt`, `src/test/kotlin/sgspe/notification/GraveGrievanceNoticesApiTest.kt`

**Interfaces:**
- Consumes: `Notices.publish` y `resolve` (fuente `Notices.APPROVALS`), `Assignments.recipients`, `ConfidentialReaders`
  (Tarea 13), `ProjectParameters.policy`.
- Produces: `object GraveGrievanceNotice` (`Effect`, `IN_ACTION`, `ENTERS`, `suspend fun effect(change, from, policy)`,
  `grave(attributes)`, `key(id)`); el bean `GraveGrievanceNoticeListener` (`@Order(210)`, `RESOLVE_HIGH`).

La decisión 8 de la especificación y `workflows.md` («Avisos»): una queja ALTA o CRITICA que entra en `in_action` con la
política activa, o cuya severidad sube a ALTA o CRITICA allí, avisa a los roles de `resolve_high_severity`; salir de
`in_action` o bajar la severidad lo resuelve. Un listener propio (decisión 7).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/notification/GraveGrievanceNoticeTest.kt`:

```kotlin
package sgspe.notification

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.notification.GraveGrievanceNotice.Effect
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// cuándo se publica y cuándo se resuelve el aviso de una queja grave (decisión 8 de F4), sin base
class GraveGrievanceNoticeTest {
    private fun change(
        kind: RecordChangeKind,
        before: Map<String, Any?>,
        after: Map<String, Any?> = before,
        state: String? = null,
        transition: String? = null
    ) = RecordChange(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "grievance", UUID.randomUUID(), kind, before, after, state, transition)

    private fun effect(
        change: RecordChange,
        from: String?,
        policy: () -> Boolean
    ): Effect = runBlocking { GraveGrievanceNotice.effect(change, from) { policy() } }

    private val high = mapOf("severity" to "ALTA")
    private val low = mapOf("severity" to "MEDIA")

    @Test
    fun `entrar en in_action con una queja grave publica, salvo con la politica apagada`() {
        assertEquals(
            Effect.PUBLISH,
            effect(change(RecordChangeKind.TRANSITIONED, high, transition = "start_action"), "in_assessment") { true }
        )
        assertEquals(
            Effect.NONE,
            effect(change(RecordChangeKind.TRANSITIONED, high, transition = "start_action"), "in_assessment") { false }
        )
        assertEquals(
            Effect.NONE,
            effect(change(RecordChangeKind.TRANSITIONED, low, transition = "start_action"), "in_assessment") { true }
        )
    }

    @Test
    fun `salir de in_action resuelve`() {
        assertEquals(
            Effect.RESOLVE,
            effect(
                change(RecordChangeKind.TRANSITIONED, high, transition = "resolve_high_severity"),
                "in_action"
            ) {
                true
            }
        )
        assertEquals(
            Effect.RESOLVE,
            effect(change(RecordChangeKind.TRANSITIONED, high, transition = "withdraw_in_action"), "in_action") { true }
        )
    }

    @Test
    fun `subir la severidad en in_action publica y bajarla resuelve`() {
        assertEquals(Effect.PUBLISH, effect(change(RecordChangeKind.UPDATED, low, high, "in_action"), null) { true })
        assertEquals(Effect.RESOLVE, effect(change(RecordChangeKind.UPDATED, high, low, "in_action"), null) { true })
        assertEquals(Effect.NONE, effect(change(RecordChangeKind.UPDATED, low, high, "in_assessment"), null) { true })
        assertEquals(
            Effect.NONE,
            effect(change(RecordChangeKind.UPDATED, high, mapOf("severity" to "CRITICA"), "in_action"), null) { true }
        )
    }
}
```

Crear `src/test/kotlin/sgspe/notification/GraveGrievanceNoticesApiTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el aviso de una queja grave en la bandeja (decisión 8 de F4, ASM-102): al entrar en in_action una queja ALTA o CRITICA lo
// reciben los asignados con un rol de resolve_high_severity, también de una confidencial, y se resuelve al resolverla; con la
// política apagada no hay aviso
class GraveGrievanceNoticesApiTest : F4ApiTest() {
    private fun pendingFor(
        account: Account,
        id: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, account.token))["content"]
            .values()
            .any { it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION" }

    private fun inAction(
        project: String,
        manager: Account,
        attributes: Map<String, Any?>,
        creator: Account? = null
    ): String {
        val id = grievance(project, creator, attributes)
        for (step in listOf("acknowledge", "assess", "start_action")) move("grievance", id, step, manager.token, step)
        return id
    }

    @Test
    fun `una queja grave en atencion avisa al aprobador y resolverla lo resuelve`() {
        val manager = member("GRIEVANCE_MANAGER")
        val approver = member("APPROVER")
        val social = member("SOCIAL_MANAGER")
        val id = inAction(testProject(), manager, mapOf("severity" to "CRITICA"))
        assertTrue(pendingFor(approver, id))
        assertFalse(pendingFor(social, id))
        assertFalse(pendingFor(manager, id))
        move("grievance", id, "resolve_high_severity", approver.token, "resolución de la queja grave")
        assertFalse(pendingFor(approver, id))
    }

    @Test
    fun `una queja confidencial grave tambien avisa al aprobador, y con la politica apagada no hay aviso`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val manager = member("GRIEVANCE_MANAGER")
        val approver = member("APPROVER")
        val confidential = inAction(testProject(), manager, mapOf("severity" to "ALTA", "confidential" to true), receiver)
        assertTrue(pendingFor(approver, confidential))
        val relaxed = project(mapOf("policy_high_severity_requires_approver" to false))
        val other = member("APPROVER", relaxed)
        val quiet = inAction(relaxed, member("GRIEVANCE_MANAGER", relaxed), mapOf("severity" to "ALTA"))
        assertFalse(pendingFor(other, quiet))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.notification.GraveGrievanceNoticeTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'GraveGrievanceNotice'`).

- [ ] **Paso 3: El listener**

Crear `src/main/kotlin/sgspe/notification/GraveGrievanceNotices.kt`:

```kotlin
package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.ModelCatalog
import sgspe.common.ProjectParameters
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

// qué hace un cambio de una queja con su aviso de aprobación pendiente, puro (decisión 8 de F4, ASM-102, RN-GRV-002):
// publicarlo al entrar una queja grave en in_action o al subir a ALTA o CRITICA la severidad de una que ya está ahí, con
// policy_high_severity_requires_approver activo; resolverlo al salir de in_action o al bajar la severidad
object GraveGrievanceNotice {
    enum class Effect { PUBLISH, RESOLVE, NONE }

    const val IN_ACTION = "in_action"
    val ENTERS: Set<String> = setOf("start_action", "start_action_after_investigation")

    suspend fun effect(
        change: RecordChange,
        from: String?,
        policy: suspend () -> Boolean
    ): Effect {
        val before = change.before.orEmpty()
        val after = change.after ?: before
        return when (change.kind) {
            RecordChangeKind.TRANSITIONED ->
                when {
                    change.transition in ENTERS && grave(after) && policy() -> Effect.PUBLISH
                    from == IN_ACTION -> Effect.RESOLVE
                    else -> Effect.NONE
                }
            RecordChangeKind.UPDATED ->
                when {
                    change.state != IN_ACTION || Values.same(before[Attention.SEVERITY], after[Attention.SEVERITY]) -> Effect.NONE
                    grave(after) && !grave(before) && policy() -> Effect.PUBLISH
                    !grave(after) && grave(before) -> Effect.RESOLVE
                    else -> Effect.NONE
                }
            else -> Effect.NONE
        }
    }

    fun grave(attributes: Map<String, Any?>): Boolean = Values.text(attributes[Attention.SEVERITY]) in Attention.HIGH_SEVERITY

    fun key(id: UUID): String = "approval_pending:${Attention.GRIEVANCE}:$id"
}

// el aviso «aprobación pendiente» de una queja grave (decisión 8 de F4, workflows.md «Avisos»): el paso de F4 de los avisos de
// aprobación, en la fuente sgspe.approvals y con la clave approval_pending:grievance:<id>, para los asignados vigentes con un
// rol de resolve_high_severity salvo quien escribió. una queja confidencial solo avisa a quien la vería (ConfidentialReaders):
// quien tiene APPROVE ve las graves, así que el filtro no le quita el aviso. corre en la transacción de la escritura, como
// quien escribe; si no puede leer el proyecto o las asignaciones, lo registra y la escritura sigue
@Component
@Order(210)
class GraveGrievanceNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val readers: ConfidentialReaders,
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName != Attention.GRIEVANCE) return
        if (change.kind == RecordChangeKind.UPDATED &&
            change.after
                .orEmpty()
                .keys
                .any { it.startsWith("rel:") }
        ) {
            return
        }
        val from = change.transition?.let { catalog.transition(Attention.GRIEVANCE, it)?.from }
        val attributes = change.after ?: change.before.orEmpty()
        val project = Records.uuid(attributes["project"]) ?: return
        try {
            val effect = GraveGrievanceNotice.effect(change, from) { policy(project) }
            when (effect) {
                GraveGrievanceNotice.Effect.PUBLISH -> publish(change, project, attributes)
                GraveGrievanceNotice.Effect.RESOLVE -> notices.resolve(change.organizationId, Notices.APPROVALS, GraveGrievanceNotice.key(change.recordId))
                GraveGrievanceNotice.Effect.NONE -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForbiddenException) {
            log.error("GraveGrievanceNoticeListener: quien escribió la queja {} no lee el proyecto o las asignaciones; no hay aviso", change.recordId, e)
        }
    }

    private suspend fun policy(project: UUID): Boolean =
        ProjectParameters.policy(records.find("project", project)?.attributes.orEmpty(), "policy_high_severity_requires_approver")

    private suspend fun publish(
        change: RecordChange,
        project: UUID,
        attributes: Map<String, Any?>
    ) {
        val candidates = assignments.recipients(project, Attention.GRIEVANCE, RESOLVE_HIGH, change.userId).map { it.userId }
        val users = readers.filter(Attention.GRIEVANCE, attributes, candidates, assignments.ofProject(project), assignments.today()) ?: candidates
        if (users.isEmpty()) {
            log.warn("GraveGrievanceNoticeListener: la queja {} no tiene asignaciones vigentes con un rol de {}; no hay aviso", change.recordId, RESOLVE_HIGH)
            return
        }
        val label = "Queja ${Values.text(attributes["code"]) ?: change.recordId}"
        notices.publish(
            change.organizationId,
            Notices.APPROVALS,
            GraveGrievanceNotice.key(change.recordId),
            NotificationKind.ACTION,
            "Queja grave por resolver: $label" to "La queja es ${Values.text(attributes[Attention.SEVERITY])} y la resuelve quien tiene la aprobación.",
            users,
            NotificationLink.Record(Attention.GRIEVANCE, change.recordId)
        )
    }

    companion object {
        const val RESOLVE_HIGH = "resolve_high_severity"
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `GraveGrievanceNoticeTest` con 3 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/notification/GraveGrievanceNotices.kt \
  src/test/kotlin/sgspe/notification/GraveGrievanceNoticeTest.kt src/test/kotlin/sgspe/notification/GraveGrievanceNoticesApiTest.kt
git commit -F - <<'MSG'
feat: aviso de aprobación pendiente de una queja grave

GraveGrievanceNoticeListener avisa a los roles de resolve_high_severity cuando una queja ALTA o
CRITICA entra en atención con la política activa, o sube a esa severidad allí, y resuelve el aviso
al salir de in_action o al bajarla (decisión 8, ASM-102).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.notification.GraveGrievanceNoticesApiTest` y
`sgspe.notification.ApprovalNoticesApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 15: El reporte unificado de QCS

**Files:**
- Create: `src/main/kotlin/sgspe/attention/AttentionReport.kt`
- Test: `src/test/kotlin/sgspe/attention/AttentionReportRowsTest.kt`, `src/test/kotlin/sgspe/attention/AttentionReportApiTest.kt`

**Interfaces:**
- Consumes: `Attention`, `AttentionCounting.counted/totals`, `Raised`, `AttentionTotals` (Tarea 2); `Permissions.confidentialHidden(user)`
  (Tarea 5); `QcsDueDates.ACK`, `RESOLUTION`, `RESPONSE` (Tarea 8); `Assignments.projectsOf`, `Criteria.anyOf`,
  `Criteria.between`, `Records.all(..., max)`, `StrictQuery.requireOnly`.
- Produces: `GET /api/sgspe/attention/report?project=&periodStart=&periodEnd=&type=&channel=&category=&page=&size=` →
  `200 { totals, byType, confidentialHidden, page }`. `totals` = `{ received, open, closed, rejected, withdrawn, absolved,
  overdue }`; `byType` = lista, por tipo pedido, de `{ type, available: true, <los siete conteos>, byState, byChannel,
  byCategory }` (cada desglose, filas `{ key, value }` de más a menos; un valor vacío es `sin dato`) o `{ type, available:
  false }` si quien llama no tiene READ sobre el objeto; `page` = `{ items, page, size, totalElements }` con `items` de
  claves exactas `type, id, code, state, channel, category, severity, receivedAt, deadlineStatus, dueDate, ownerEmail,
  respondedAt, anonymous`, de la recepción más reciente a la más antigua. Errores: `422` (parámetro desconocido),
  `400 INVALID_FILTER` sobre el parámetro, `400 TOO_MANY_RECORDS` (`sgspe.exports.max-rows` por tipo). Puras para el portal
  y los planes siguientes: `AttentionReportRows.report(read: Map<String, List<Raised>?>, confidentialHidden: Boolean, page:
  Int, size: Int): Map<String, Any?>`, `AttentionReportRows.dueDate(type: String, state: String?, attributes: Map<String,
  Any?>): String?`, `AttentionReportRows.parse(params: Map<String, List<String>>, catalog: ModelCatalog):
  AttentionReportFilters`.

El reporte lee como quien llama (nunca como la plataforma): una QCS confidencial que no ve no cuenta, y
`confidentialHidden` lo dice sin el número de excluidas (security.md §9.6). Cada planteamiento cuenta una vez, como el
último registro de su cadena de reclasificaciones, entre los tipos pedidos (`AttentionCounting`). Sin `project`, «mis
proyectos», como los tableros: las asignaciones vigentes o, para el ADMIN o quien no tiene ninguna, todos los proyectos que
lee. Sin periodo no filtra por fecha; con él, `received_at` entre los días de Lima. La fila nunca lleva descripción,
respuesta, resolución ni persona.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/attention/AttentionReportRowsTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.common.ModelCatalog
import sgspe.common.Raised
import sgspe.common.RuleException
import sgspe.common.UnprocessableContentException

// el reporte unificado de QCS (api.md §4.6), sin base: conteos con AttentionCounting, tipos no legibles, filas sin texto libre,
// el plazo que corre en cada estado y los parámetros
class AttentionReportRowsTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun raised(
        type: String,
        id: String,
        state: String,
        attributes: Map<String, Any?> = emptyMap()
    ) = Raised(type, id, state, mapOf("code" to id.uppercase(), "received_at" to "2026-10-0${id.last()}T15:00:00Z") + attributes)

    @Suppress("UNCHECKED_CAST")
    private fun byType(
        report: Map<String, Any?>,
        type: String
    ): Map<String, Any?> = (report["byType"] as List<Map<String, Any?>>).first { it["type"] == type }

    @Test
    fun `cuenta cada planteamiento una vez y un tipo que no se lee sale no disponible`() {
        val read =
            mapOf(
                "grievance" to listOf(raised("grievance", "q1", "closed"), raised("grievance", "q2", "in_action", mapOf("deadline_status" to "VENCIDO"))),
                "inquiry" to listOf(raised("inquiry", "c3", "received", mapOf("reclassified_from_grievance" to "q1", "channel" to "CARTA"))),
                "stakeholder_request" to null
            )
        val report = AttentionReportRows.report(read, confidentialHidden = true, page = 0, size = 50)
        assertEquals(mapOf("received" to 2, "open" to 2, "closed" to 0, "rejected" to 0, "withdrawn" to 0, "absolved" to 0, "overdue" to 1), report["totals"])
        assertEquals(1, byType(report, "grievance")["received"])
        assertEquals(listOf(mapOf("key" to "CARTA", "value" to 1)), byType(report, "inquiry")["byChannel"])
        assertEquals(mapOf("type" to "stakeholder_request", "available" to false), byType(report, "stakeholder_request"))
        assertEquals(true, report["confidentialHidden"])
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `la pagina va de la mas reciente a la mas antigua y no lleva texto libre ni persona`() {
        val rows =
            listOf(
                raised("grievance", "q1", "received", mapOf("ack_due_date" to "2026-10-08", "description" to "texto", "person" to "p", "severity" to "ALTA")),
                raised("inquiry", "c2", "received", mapOf("response_due_date" to "2026-10-17", "response" to "texto"))
            )
        val report = AttentionReportRows.report(mapOf("grievance" to rows.take(1), "inquiry" to rows.drop(1)), false, 0, 1)
        val page = report["page"] as Map<String, Any?>
        assertEquals(2, page["totalElements"])
        val items = page["items"] as List<Map<String, Any?>>
        assertEquals(listOf("c2"), items.map { it["id"] })
        assertEquals(
            listOf(
                "type",
                "id",
                "code",
                "state",
                "channel",
                "category",
                "severity",
                "receivedAt",
                "deadlineStatus",
                "dueDate",
                "ownerEmail",
                "respondedAt",
                "anonymous"
            ),
            items.single().keys.toList()
        )
        assertEquals("2026-10-17", items.single()["dueDate"])
        val second = AttentionReportRows.report(mapOf("grievance" to rows.take(1), "inquiry" to rows.drop(1)), false, 1, 1)
        val item = ((second["page"] as Map<String, Any?>)["items"] as List<Map<String, Any?>>).single()
        assertEquals("ALTA", item["severity"])
        assertFalse("description" in item || "person" in item)
    }

    @Test
    fun `el plazo que corre depende del estado`() {
        val dates = mapOf("ack_due_date" to "2026-10-13", "resolution_due_date" to "2026-11-04", "response_due_date" to "2026-10-20")
        assertEquals("2026-10-13", AttentionReportRows.dueDate("grievance", "received", dates))
        assertEquals("2026-11-04", AttentionReportRows.dueDate("grievance", "in_investigation", dates))
        assertEquals(null, AttentionReportRows.dueDate("grievance", "resolved", dates))
        assertEquals(null, AttentionReportRows.dueDate("inquiry", "answered", dates))
        assertEquals(null, AttentionReportRows.dueDate("stakeholder_request", "received", dates))
    }

    @Test
    fun `los parametros se validan`() {
        val parsed = AttentionReportRows.parse(mapOf("type" to listOf("inquiry"), "channel" to listOf("CARTA"), "size" to listOf("10")), catalog)
        assertEquals(listOf("inquiry"), parsed.types)
        assertEquals(10, parsed.size)
        assertEquals(listOf("grievance", "inquiry", "stakeholder_request"), AttentionReportRows.parse(emptyMap(), catalog).types)
        assertThrows<UnprocessableContentException> { AttentionReportRows.parse(mapOf("territory" to listOf("x")), catalog) }
        for (bad in listOf("type" to "commitment", "channel" to "FAX", "periodStart" to "ayer", "size" to "500", "project" to "x")) {
            val e = assertThrows<RuleException> { AttentionReportRows.parse(mapOf(bad.first to listOf(bad.second)), catalog) }
            assertEquals(bad.first, e.violations.single().field)
            assertEquals("INVALID_FILTER", e.violations.single().message)
        }
    }
}
```

Crear `src/test/kotlin/sgspe/attention/AttentionReportApiTest.kt`:

```kotlin
package sgspe.attention

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// GET /api/sgspe/attention/report (api.md §4.6): cuenta cada planteamiento una vez, lee como quien llama (una QCS confidencial
// que no ve no cuenta y confidentialHidden lo dice), no trae texto libre y rechaza un parámetro desconocido o inválido
class AttentionReportApiTest : F4ApiTest() {
    private fun report(
        query: String,
        account: Account? = null
    ): JsonNode = tree(send("GET", "/api/sgspe/attention/report?project=${testProject()}$query", null, HttpStatus.OK, account?.token ?: token))

    private fun byType(
        report: JsonNode,
        type: String
    ): JsonNode = report["byType"].values().first { it["type"].asString() == type }

    @Test
    fun `una queja reclasificada cuenta una vez, como su consulta`() {
        val manager = member("GRIEVANCE_MANAGER")
        val id = grievance(testProject())
        val reclassified =
            exchange(
                "POST",
                "/api/sgspe/grievances/$id/reclassify",
                mapOf("targetType" to "inquiry", "attributes" to mapOf("code" to uniqueName("CO-"))),
                manager.token,
                ifMatch("grievance", id, manager.token) + withReason("era una consulta")
            )
        assertEquals(HttpStatus.CREATED, reclassified.status, reclassified.body)
        stakeholderRequest(testProject())
        val body = report("")
        assertEquals(2, body["totals"]["received"].asInt())
        assertEquals(0, byType(body, "grievance")["received"].asInt())
        assertEquals(1, byType(body, "inquiry")["received"].asInt())
        assertFalse(body["confidentialHidden"].asBoolean())
        val item = body["page"]["items"].values().first()
        assertFalse(item.has("description"))
        assertFalse(item.has("person"))
    }

    @Test
    fun `una QCS confidencial que quien llama no ve no cuenta y confidentialHidden lo dice`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val colleague = member("COMMUNITY_RELATIONS_OFFICER")
        grievance(testProject(), receiver, mapOf("confidential" to true))
        grievance(testProject(), receiver)
        val seen = report("&type=grievance", colleague)
        assertEquals(1, seen["totals"]["received"].asInt())
        assertTrue(seen["confidentialHidden"].asBoolean())
        assertEquals(2, report("&type=grievance", receiver)["totals"]["received"].asInt())
        assertEquals(2, report("&type=grievance", member("GRIEVANCE_MANAGER"))["totals"]["received"].asInt())
    }

    @Test
    fun `un parametro desconocido es 422 y uno invalido 400 INVALID_FILTER`() {
        assertEquals(422, exchange("GET", "/api/sgspe/attention/report?territory=x").status.value())
        val invalid = exchange("GET", "/api/sgspe/attention/report?type=commitment")
        assertEquals(HttpStatus.BAD_REQUEST, invalid.status, invalid.body)
        assertEquals("INVALID_FILTER", code(invalid))
        assertEquals(listOf("type"), problemFields(invalid.body))
    }
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.attention.AttentionReportRowsTest'
```

Esperado: falla la compilación (`Unresolved reference 'AttentionReportRows'`).

- [ ] **Paso 3: El reporte y su controlador**

Crear `src/main/kotlin/sgspe/attention/AttentionReport.kt`:

```kotlin
package sgspe.attention

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.AttentionTotals
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Raised
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

// los filtros del reporte unificado (api.md §4.6), ya validados; projects vacío: «mis proyectos» por resolver
data class AttentionReportFilters(
    val projects: List<UUID>,
    val periodStart: LocalDate?,
    val periodEnd: LocalDate?,
    val types: List<String>,
    val channel: String?,
    val category: String?,
    val page: Int,
    val size: Int
)

// el reporte unificado de QCS, puro (spec de F4 §3.2, api.md §4.6, RF-GRV-005): los conteos con AttentionCounting (cada
// planteamiento una vez, dashboards.md §3.3) y la página de filas sin texto libre ni persona. un tipo que quien llama no lee
// llega como null y sale `available: false`, sin contar
object AttentionReportRows {
    const val PARAM_PROJECT = "project"
    const val PARAM_START = "periodStart"
    const val PARAM_END = "periodEnd"
    const val PARAM_TYPE = "type"
    const val PARAM_CHANNEL = "channel"
    const val PARAM_CATEGORY = "category"
    const val PARAM_PAGE = "page"
    const val PARAM_SIZE = "size"
    val PARAMS: Set<String> = setOf(PARAM_PROJECT, PARAM_START, PARAM_END, PARAM_TYPE, PARAM_CHANNEL, PARAM_CATEGORY, PARAM_PAGE, PARAM_SIZE)

    const val DEFAULT_SIZE = 50
    const val MAX_SIZE = 200

    // la fecha que mide una QCS en su estado (workflows.md «Plazos y escalamiento»): el acuse de una queja en `received` y su
    // resolución en los demás estados con plazo; la respuesta de una consulta en `received`; null fuera de ellos y en las
    // solicitudes, que no tienen plazo en el MVP (ASM-100)
    val GRIEVANCE_TRACKED: Set<String> = setOf("received", "acknowledged", "in_assessment", "in_investigation", "in_action")

    fun dueDate(
        type: String,
        state: String?,
        attributes: Map<String, Any?>
    ): String? =
        when {
            type == Attention.GRIEVANCE && state == "received" -> Values.text(attributes[QcsDueDates.ACK])
            type == Attention.GRIEVANCE && state in GRIEVANCE_TRACKED -> Values.text(attributes[QcsDueDates.RESOLUTION])
            type == Attention.INQUIRY && state == "received" -> Values.text(attributes[QcsDueDates.RESPONSE])
            else -> null
        }

    // una fila de la página: solo estos campos, nunca descripción, respuesta, resolución ni persona
    fun item(row: Raised): Map<String, Any?> {
        val a = row.attributes
        return linkedMapOf(
            "type" to row.type,
            "id" to row.id,
            "code" to Values.text(a["code"]),
            "state" to row.state,
            "channel" to Values.text(a["channel"]),
            "category" to Values.text(a["category"]),
            "severity" to if (row.type == Attention.GRIEVANCE) Values.text(a[Attention.SEVERITY]) else null,
            "receivedAt" to Values.text(a[Attention.RECEIVED_AT]),
            "deadlineStatus" to Values.text(a[Attention.DEADLINE_STATUS]),
            "dueDate" to dueDate(row.type, row.state, a),
            "ownerEmail" to Values.text(a["owner_user_email"]),
            "respondedAt" to Values.text(a[Attention.RESPONDED_AT]),
            "anonymous" to (Values.bool(a["anonymous"]) == true)
        )
    }

    fun totals(totals: AttentionTotals): Map<String, Any?> =
        linkedMapOf(
            "received" to totals.received,
            "open" to totals.open,
            "closed" to totals.closed,
            "rejected" to totals.rejected,
            "withdrawn" to totals.withdrawn,
            "absolved" to totals.absolved,
            "overdue" to totals.overdue
        )

    // filas { key, value } de más a menos y, a igual cuenta, por clave; un valor vacío cuenta como «sin dato»
    fun counts(keys: List<String?>): List<Map<String, Any?>> =
        keys
            .map { it ?: NO_VALUE }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { mapOf("key" to it.key, "value" to it.value) }

    const val NO_VALUE = "sin dato"

    // el reporte: `read` trae, por tipo pedido, sus filas como las lee quien llama o null si no lo lee
    fun report(
        read: Map<String, List<Raised>?>,
        confidentialHidden: Boolean,
        page: Int,
        size: Int
    ): Map<String, Any?> {
        val counted = AttentionCounting.counted(read.values.filterNotNull().flatten())
        val byType =
            read.map { (type, rows) ->
                if (rows == null) {
                    linkedMapOf("type" to type, "available" to false)
                } else {
                    val mine = counted.filter { it.type == type }
                    linkedMapOf<String, Any?>("type" to type, "available" to true) + totals(AttentionCounting.totals(mine)) +
                        mapOf(
                            "byState" to counts(mine.map { it.state }),
                            "byChannel" to counts(mine.map { Values.text(it.attributes["channel"]) }),
                            "byCategory" to counts(mine.map { Values.text(it.attributes["category"]) })
                        )
                }
            }
        val sorted =
            counted.sortedWith(
                compareByDescending<Raised> { Values.text(it.attributes[Attention.RECEIVED_AT]).orEmpty() }.thenBy { it.id }
            )
        val items = sorted.drop(page * size).take(size).map { item(it) }
        return linkedMapOf(
            "totals" to totals(AttentionCounting.totals(counted)),
            "byType" to byType,
            "confidentialHidden" to confidentialHidden,
            "page" to linkedMapOf("items" to items, "page" to page, "size" to size, "totalElements" to counted.size)
        )
    }

    // los parámetros validados: un valor inválido es 400 INVALID_FILTER sobre el parámetro; uno desconocido, 422
    fun parse(
        params: Map<String, List<String>>,
        catalog: ModelCatalog
    ): AttentionReportFilters {
        StrictQuery.requireOnly(params, PARAMS)

        fun invalid(
            param: String,
            detail: String
        ): Nothing = throw Finding("INVALID_FILTER", "Filtro inválido: $param $detail", param, status = HttpStatus.BAD_REQUEST).toCodedException()

        fun single(param: String): String? {
            val values = params[param].orEmpty()
            if (values.size > 1) invalid(param, "va una sola vez")
            return values.firstOrNull()?.trim()?.ifEmpty { null }
        }

        fun date(param: String): LocalDate? =
            single(param)?.let {
                try {
                    LocalDate.parse(it)
                } catch (_: DateTimeParseException) {
                    invalid(param, "debe ser una fecha AAAA-MM-DD")
                }
            }

        fun number(
            param: String,
            default: Int,
            range: IntRange
        ): Int {
            val raw = single(param) ?: return default
            val value = raw.toIntOrNull()
            if (value == null || value !in range) invalid(param, "debe ser un entero de ${range.first} a ${range.last}")
            return value
        }

        fun option(
            param: String,
            field: String
        ): String? {
            val value = single(param) ?: return null
            val allowed = catalog.enumOf(Attention.GRIEVANCE, field)
            if (value !in allowed) invalid(param, "debe ser uno de ${allowed.joinToString(", ")}")
            return value
        }
        val projects =
            params[PARAM_PROJECT].orEmpty().map { raw -> runCatching { UUID.fromString(raw.trim()) }.getOrElse { invalid(PARAM_PROJECT, "debe ser un UUID") } }
        val types = params[PARAM_TYPE].orEmpty().map { it.trim() }
        types.firstOrNull { it !in Attention.OBJECTS }?.let { invalid(PARAM_TYPE, "debe ser uno de ${Attention.OBJECTS.joinToString(", ")}") }
        val start = date(PARAM_START)
        val end = date(PARAM_END)
        if (start != null && end != null && start.isAfter(end)) invalid(PARAM_START, "no puede ser posterior a periodEnd")
        return AttentionReportFilters(
            projects = projects.distinct(),
            periodStart = start,
            periodEnd = end,
            types = Attention.OBJECTS.filter { types.isEmpty() || it in types },
            channel = option(PARAM_CHANNEL, "channel"),
            category = option(PARAM_CATEGORY, "category"),
            page = number(PARAM_PAGE, 0, 0..Int.MAX_VALUE),
            size = number(PARAM_SIZE, DEFAULT_SIZE, 1..MAX_SIZE)
        )
    }
}

// GET /api/sgspe/attention/report (api.md §4.6): lee como quien llama, nunca como la plataforma, así que una QCS confidencial
// que no ve no cuenta, y lo dice confidentialHidden, sin el número de excluidas (security.md §9.6). sin `project`, «mis
// proyectos»: las asignaciones vigentes o, para el ADMIN o quien no tiene ninguna, todos los proyectos que lee. la lectura es
// acotada por tipo (sgspe.exports.max-rows): pasarse es 400 TOO_MANY_RECORDS
@Service
class AttentionReport(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    suspend fun report(params: Map<String, List<String>>): Map<String, Any?> {
        val filters = AttentionReportRows.parse(params, catalog)
        val user = callers.require()
        val projects = filters.projects.ifEmpty { if (user.isAdmin) allProjects() else assigned(user.userId).ifEmpty { allProjects() } }
        val read = filters.types.associateWith { type -> rows(type, projects, filters) }
        return AttentionReportRows.report(read, permissions.confidentialHidden(user), filters.page, filters.size)
    }

    // las filas de un tipo como las lee quien llama; null si no tiene READ sobre el objeto
    private suspend fun rows(
        type: String,
        projects: List<UUID>,
        filters: AttentionReportFilters
    ): List<Raised>? {
        if (projects.isEmpty()) return emptyList()
        val equal = listOfNotNull(filters.channel?.let { "channel" to it }, filters.category?.let { "category" to it }).toMap()
        val criteria =
            listOfNotNull(
                Criteria.anyOf("project", projects),
                if (filters.periodStart == null && filters.periodEnd == null) {
                    null
                } else {
                    Criteria.between(
                        Attention.RECEIVED_AT,
                        (filters.periodStart ?: LocalDate.of(1900, 1, 1)).atStartOfDay(LIMA).toInstant(),
                        (filters.periodEnd ?: LocalDate.of(9999, 12, 30)).plusDays(1).atStartOfDay(LIMA).toInstant()
                    )
                }
            )
        return try {
            records.all(type, equal, criteria, max = maxRows).map { Raised(type, it.id, it.state, it.attributes) }
        } catch (_: ForbiddenException) {
            null
        }
    }

    private suspend fun assigned(userId: UUID): List<UUID> =
        try {
            assignments.projectsOf(userId)
        } catch (_: ForbiddenException) {
            emptyList()
        }

    private suspend fun allProjects(): List<UUID> =
        try {
            records.all("project", max = maxRows).map { UUID.fromString(it.id) }
        } catch (_: ForbiddenException) {
            emptyList()
        }
}

@RestController
@RequestMapping("/api/sgspe/attention")
class AttentionReportController(
    private val report: AttentionReport
) {
    @GetMapping("/report")
    suspend fun report(request: ServerHttpRequest): Map<String, Any?> = report.report(request.queryParams)
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`, con las 4 pruebas de `AttentionReportRowsTest` en verde (`build` compila también
`AttentionReportApiTest`).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/AttentionReport.kt src/test/kotlin/sgspe/attention/AttentionReportRowsTest.kt \
  src/test/kotlin/sgspe/attention/AttentionReportApiTest.kt
git commit -F - <<'MSG'
feat: reporte unificado de quejas, consultas y solicitudes

GET /api/sgspe/attention/report cuenta cada planteamiento una vez, lee como quien llama y dice
con confidentialHidden si hay QCS confidenciales que no ve; la página no lleva texto libre ni
persona, y un filtro inválido es 400 INVALID_FILTER.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.attention.AttentionReportApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 16: Los KPI-GRV y el tablero attention

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/AttentionKpis.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/DashboardData.kt` (lectura de las QCS), `src/main/kotlin/sgspe/dashboard/Kpis.kt`
  (KPI-GRV-01 a 10, tablero `attention`, `confidentialSensitive`), `src/main/kotlin/sgspe/dashboard/Indicators.kt`
  (`confidentialHidden` en `IndicatorValue` y `DashboardResponse`), `src/main/kotlin/sgspe/dashboard/Dashboards.kt` (la marca)
- Test: `src/test/kotlin/sgspe/dashboard/AttentionKpisTest.kt`, `src/test/kotlin/sgspe/dashboard/AttentionDashboardApiTest.kt`

**Interfaces:**
- Consumes: `sgspe.common.Attention` (`OBJECTS`, `RECLASSIFIED_FROM`, `isOpen`, nombres de campos), `AttentionCounting.counted` y
  `Raised` (Tarea 2); `Permissions.confidentialHidden(user: AuthenticatedUser): Boolean` (Tarea 5);
  `sgspe.attention.QcsDueDates.receivedDate(Any?): LocalDate?`, `QcsDueDates.RESOLUTION` y `RESPONSE` (Tarea 8); `F4ApiTest` (plan 4.1).
- Produces: `data class AttentionRow(type, id, state, received, responded, resolved, closed, resolutionDue, responseDue,
  deadlineStatus, channel, category, severity, locality, community, stakeholder, anonymous: Boolean, reclassified: Map<String,
  String>)` y `object AttentionKpis` (`counted`, `received`, `open`, `resolved`, `closed`, `rejected`, `overdue`, `absolved`,
  `breakdowns`, `receivedVsClosed`, `attentionTime`, `median(List<Long>): BigDecimal?`); `DashboardData.attention():
  List<AttentionRow>`; `Kpis.ATTENTION = "attention"`, `Kpis.confidentialSensitive(code: String): Boolean`;
  `IndicatorValue.confidentialHidden: Boolean?` (en el JSON solo si no es null) y `DashboardResponse.confidentialHidden:
  Boolean?` (solo en `attention`, `true` o `false`); `GET /api/sgspe/dashboards/attention` con los diez KPI-GRV en el orden de
  `dashboards.md` §5.7. El plan 4.2b suma `risks` y KPI-ENG-06 a 08 a `Kpis` sin tocar esto.

Los KPI-GRV leen los tres objetos enteros de los proyectos del filtro (acotados con `MAX_ROWS`, como los compromisos) y cortan
por la fecha de cada uno en Kotlin: la regla de conteo necesita la cadena de reclasificaciones aunque el original sea de
otro periodo. Leen como quien mira, así que una QCS confidencial que no ve no cuenta; la marca `confidentialHidden` lo dice
sin el número de excluidas. El territorio y el ámbito filtran por la localidad o la comunidad de la QCS; la campaña y el
responsable no aplican y van en `ignoredFilters`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/AttentionKpisTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-GRV puros (dashboards.md §4.5): cada planteamiento una vez (los originales reclasificados no cuentan), los estados de
// §3.2 y las fechas de §12, sin base
class AttentionKpisTest {
    private val start = LocalDate.parse("2026-10-01")
    private val end = LocalDate.parse("2026-10-31")

    private fun date(text: String?) = text?.let(LocalDate::parse)

    private fun row(
        type: String,
        id: String,
        state: String = "received",
        received: String? = "2026-10-05",
        responded: String? = null,
        resolved: String? = null,
        closed: String? = null,
        resolutionDue: String? = null,
        responseDue: String? = null,
        deadline: String? = null,
        channel: String? = "PRESENCIAL",
        severity: String? = null,
        stakeholder: String? = null,
        anonymous: Boolean = false,
        reclassified: Map<String, String> = emptyMap()
    ) = AttentionRow(
        type,
        id,
        state,
        date(received),
        date(responded),
        date(resolved),
        date(closed),
        date(resolutionDue),
        date(responseDue),
        deadline,
        channel,
        "EMPLEO",
        severity,
        "loc-1",
        null,
        stakeholder,
        anonymous,
        reclassified
    )

    // una queja reclasificada como consulta: el original cerrado no cuenta
    private val original = row("grievance", "q1", "closed", closed = "2026-10-06")
    private val successor = row("inquiry", "c1", reclassified = mapOf("reclassified_from_grievance" to "q1"))

    private fun breakdown(
        result: IndicatorResult,
        by: String
    ) = result.breakdowns.first { it.by == by }.rows

    @Test
    fun `KPI-GRV-01 cuenta cada planteamiento una vez, por tipo y canal`() {
        val rows = listOf(original, successor, row("grievance", "q2", channel = "CARTA"), row("grievance", "q3", received = "2026-09-30"))
        val result = AttentionKpis.received(rows, start, end)
        assertEquals(2, result.value)
        assertEquals(listOf(mapOf("key" to "grievance", "value" to 1), mapOf("key" to "inquiry", "value" to 1)), breakdown(result, "type"))
        assertEquals(setOf("PRESENCIAL", "CARTA"), breakdown(result, "channel").map { it["key"] }.toSet())
    }

    @Test
    fun `KPI-GRV-02 cuenta las abiertas por tipo y estado`() {
        val rows = listOf(original, successor, row("grievance", "q2", "rejected"), row("stakeholder_request", "s1", "declined"))
        val result = AttentionKpis.open(rows)
        assertEquals(2, result.value)
        assertEquals(setOf("inquiry.received", "stakeholder_request.declined"), breakdown(result, "workflow_state").map { it["key"] }.toSet())
    }

    @Test
    fun `KPI-GRV-03 cuenta las quejas resueltas en el periodo por severidad`() {
        val rows =
            listOf(row("grievance", "q1", "resolved", resolved = "2026-10-10", severity = "ALTA"), row("grievance", "q2", "in_assessment", resolved = null))
        val result = AttentionKpis.resolved(rows, start, end)
        assertEquals(1, result.value)
        assertEquals(listOf(mapOf("key" to "ALTA", "value" to 1)), breakdown(result, "severity"))
    }

    @Test
    fun `KPI-GRV-04 y 05 separan cerradas, rechazadas y desistidas`() {
        val rows =
            listOf(
                original,
                successor,
                row("inquiry", "c2", "closed", closed = "2026-10-08"),
                row("grievance", "q2", "rejected", closed = "2026-10-09"),
                row("grievance", "q3", "withdrawn", closed = "2026-10-09")
            )
        assertEquals(1, AttentionKpis.closed(rows, start, end).value)
        val rejected = AttentionKpis.rejected(rows, start, end)
        assertEquals(1, rejected.value)
        assertEquals(listOf(mapOf("key" to "rejected", "value" to 1), mapOf("key" to "withdrawn", "value" to 1)), breakdown(rejected, "workflow_state"))
    }

    @Test
    fun `KPI-GRV-06 cuenta las abiertas vencidas`() {
        val rows =
            listOf(
                row("grievance", "q1", deadline = "VENCIDO"),
                row("inquiry", "c1", "closed", deadline = "VENCIDO"),
                row("inquiry", "c2", deadline = "AL DIA")
            )
        assertEquals(1, AttentionKpis.overdue(rows).value)
    }

    @Test
    fun `KPI-GRV-07 es el porcentaje de recibidas con respuesta`() {
        val rows =
            listOf(
                original,
                successor.copy(responded = date("2026-10-07")),
                row("grievance", "q2"),
                row("stakeholder_request", "s1", "closed", responded = "2026-10-09")
            )
        val result = AttentionKpis.absolved(rows, start, end)
        assertEquals(BigDecimal("66.7"), result.value)
        assertEquals(2, result.numerator)
        assertEquals(3, result.denominator)
    }

    @Test
    fun `KPI-GRV-08 desglosa recibidas y abiertas, con las anonimas aparte`() {
        val rows = listOf(row("grievance", "q1", severity = "ALTA", stakeholder = "g1"), row("inquiry", "c1", anonymous = true))
        val result = AttentionKpis.breakdowns(rows, start, end, mapOf("loc-1" to "Localidad Ejemplo"), mapOf("g1" to "Asociación Ejemplo"))
        assertEquals(2, result.value)
        val stakeholders = breakdown(result, "stakeholder").associate { it["key"] to it["label"] }
        assertEquals(mapOf("g1" to "Asociación Ejemplo", "ANONIMA" to "Anónimas"), stakeholders)
        assertEquals("Localidad Ejemplo", breakdown(result, "territory").single()["label"])
        assertEquals(1, breakdown(result, "severity").first { it["key"] == "ALTA" }["open"])
    }

    @Test
    fun `KPI-GRV-09 da doce meses de recibidas y cerradas`() {
        val rows = listOf(original, successor, row("inquiry", "c2", "closed", received = "2026-08-02", closed = "2026-10-03"))
        val result = AttentionKpis.receivedVsClosed(rows, end)
        val series = result.series!!
        assertEquals(12, series.size)
        assertEquals("2026-10", series.last()["period"])
        assertEquals(1, series.last()["received"])
        assertEquals(1, series.last()["closed"])
        assertEquals(1, series.first { it["period"] == "2026-08" }["received"])
    }

    @Test
    fun `KPI-GRV-10 da la mediana y el porcentaje dentro de plazo, y la solicitud solo la mediana`() {
        val rows =
            listOf(
                row("grievance", "q1", "resolved", received = "2026-10-01", resolved = "2026-10-11", resolutionDue = "2026-10-31"),
                row("grievance", "q2", "resolved", received = "2026-09-01", resolved = "2026-10-11", resolutionDue = "2026-10-01"),
                row("inquiry", "c1", "answered", received = "2026-10-01", responded = "2026-10-05", responseDue = "2026-10-16"),
                row("stakeholder_request", "s1", "declined", received = "2026-10-01", responded = "2026-10-21")
            )
        val result = AttentionKpis.attentionTime(rows, start, end)
        // días: 10, 40, 4, 20; mediana 15
        assertEquals(BigDecimal("15.0"), result.value)
        assertEquals(2, result.numerator)
        assertEquals(3, result.denominator)
        val byType = breakdown(result, "type").associateBy { it["key"] }
        assertEquals(BigDecimal("25.0"), byType.getValue("grievance")["value"])
        assertEquals(BigDecimal("50.0"), byType.getValue("grievance")["onTime"])
        assertTrue("onTime" !in byType.getValue("stakeholder_request"))
        assertEquals(BigDecimal("20.0"), byType.getValue("stakeholder_request")["value"])
        assertNull(AttentionKpis.median(emptyList()))
    }

    @Test
    fun `el tablero attention trae los diez KPI-GRV en orden y la marca de confidenciales`() {
        assertEquals((1..10).map { "KPI-GRV-%02d".format(it) }, Kpis.DASHBOARDS.getValue("attention"))
        assertTrue((1..10).all { Kpis.BY_CODE.getValue("KPI-GRV-%02d".format(it)).dashboard == "attention" })
        assertTrue(Kpis.confidentialSensitive("KPI-GRV-08") && Kpis.confidentialSensitive("KPI-ENG-05"))
        assertTrue(!Kpis.confidentialSensitive("KPI-ENG-04") && !Kpis.confidentialSensitive("KPI-COM-01"))
    }
}
```

Crear `src/test/kotlin/sgspe/dashboard/AttentionDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// el tablero attention (dashboards.md §5.7 y §8, api.md §4.11): los diez KPI-GRV en orden, cada planteamiento una vez, y las QCS
// confidenciales fuera para quien no las ve, con confidentialHidden en el tablero y en cada indicador; el gestor de quejas y el
// ADMIN las cuentan y no reciben la marca
class AttentionDashboardApiTest : F4ApiTest() {
    private fun dashboard(account: Account? = null): JsonNode =
        tree(
            send(
                "GET",
                "/api/sgspe/dashboards/attention?project=${testProject()}&periodStart=2026-10-01&periodEnd=2026-10-31",
                null,
                HttpStatus.OK,
                account?.token ?: token
            )
        )

    private fun received(body: JsonNode): Int = body["indicators"].values().first { it["code"].asString() == "KPI-GRV-01" }["value"].asInt()

    private fun hidden(node: JsonNode): Boolean = node["confidentialHidden"]?.asBoolean() == true

    @Test
    fun `el tablero trae los diez KPI-GRV en orden y cuenta una vez la QCS reclasificada`() {
        grievance(testProject())
        inquiry(testProject(), attributes = mapOf("reclassified_from_grievance" to grievance(testProject())))
        val body = dashboard()
        assertEquals((1..10).map { "KPI-GRV-%02d".format(it) }, body["indicators"].values().map { it["code"].asString() })
        assertEquals(2, received(body))
    }

    @Test
    fun `una QCS confidencial queda fuera para otro relacionista, con la marca, y el gestor y el ADMIN la cuentan`() {
        val receiver = member("COMMUNITY_RELATIONS_OFFICER")
        val colleague = member("COMMUNITY_RELATIONS_OFFICER")
        val manager = member("GRIEVANCE_MANAGER")
        grievance(testProject(), receiver)
        grievance(testProject(), receiver, mapOf("confidential" to true))
        val blind = dashboard(colleague)
        assertEquals(1, received(blind))
        assertTrue(hidden(blind))
        assertTrue(blind["indicators"].values().all { hidden(it) })
        val engagement =
            tree(send("GET", "/api/sgspe/dashboards/indicators/KPI-ENG-05?project=${testProject()}", null, HttpStatus.OK, colleague.token))
        assertTrue(hidden(engagement))
        for (account in listOf(manager, null)) {
            val full = dashboard(account)
            assertEquals(2, received(full))
            assertFalse(hidden(full))
            assertFalse(full["indicators"].values().any { hidden(it) })
        }
        val commitments = tree(send("GET", "/api/sgspe/dashboards/commitments?project=${testProject()}", null, HttpStatus.OK, colleague.token))
        assertFalse(commitments.has("confidentialHidden"))
    }
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.AttentionKpisTest'
```

Esperado: falla la compilación (`Unresolved reference 'AttentionRow'`, `'AttentionKpis'`, `'confidentialSensitive'`).

- [ ] **Paso 3: Los KPI-GRV puros, la lectura de las QCS, el tablero y la marca**

Crear `src/main/kotlin/sgspe/dashboard/AttentionKpis.kt`:

```kotlin
package sgspe.dashboard

import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.Raised
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// una QCS como la leen los KPI-GRV, ya acotada a los proyectos, al territorio y a lo que quien mira lee (una confidencial que
// no ve no está). las fechas son de Lima. `reclassified`: sus vínculos reclassified_from_* informados (campo -> id)
data class AttentionRow(
    val type: String,
    val id: String,
    val state: String?,
    val received: LocalDate?,
    val responded: LocalDate?,
    val resolved: LocalDate?,
    val closed: LocalDate?,
    val resolutionDue: LocalDate?,
    val responseDue: LocalDate?,
    val deadlineStatus: String?,
    val channel: String?,
    val category: String?,
    val severity: String?,
    val locality: String?,
    val community: String?,
    val stakeholder: String?,
    val anonymous: Boolean,
    val reclassified: Map<String, String> = emptyMap()
) {
    fun raised(): Raised = Raised(type, id, state, reclassified)

    val open: Boolean get() = Attention.isOpen(type, state)
}

// los KPI-GRV (dashboards.md §4.5) como funciones puras. cada planteamiento cuenta una vez (AttentionCounting, §3.3): los
// originales reclasificados nunca cuentan, salvo que su registro nuevo no esté en la lista (quien mira no lo lee)
object AttentionKpis {
    const val OVERDUE = "VENCIDO"
    const val OTHERS = "Otros"
    const val ANONYMOUS = "ANONIMA"
    const val NONE = "SIN DATO"
    const val TOP = 10

    // los planteamientos de la lista (sin los originales reclasificados)
    fun counted(rows: List<AttentionRow>): List<AttentionRow> {
        val kept = AttentionCounting.counted(rows.map { it.raised() }).map { it.type to it.id }.toSet()
        return rows.filter { (it.type to it.id) in kept }
    }

    private fun within(
        date: LocalDate?,
        start: LocalDate,
        end: LocalDate
    ): Boolean = date != null && !date.isBefore(start) && !date.isAfter(end)

    // cuántas por clave, en el orden de los tres objetos o de mayor a menor
    private fun byType(rows: List<AttentionRow>): Breakdown {
        val counts = rows.groupingBy { it.type }.eachCount()
        return Breakdown("type", Attention.OBJECTS.filter { it in counts }.map { mapOf("key" to it, "value" to counts.getValue(it)) })
    }

    // KPI-GRV-01: recibidas en el periodo, por tipo y por canal
    fun received(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val inPeriod = counted(rows).filter { within(it.received, start, end) }
        return IndicatorResult(inPeriod.size, breakdowns = listOf(byType(inPeriod), Breakdown("channel", Numbers.counts(inPeriod.map { it.channel ?: NONE }))))
    }

    // KPI-GRV-02: abiertas hoy (dashboards.md §3.2), por tipo y por estado (clave «<tipo>.<estado>»)
    fun open(rows: List<AttentionRow>): IndicatorResult {
        val open = counted(rows).filter { it.open }
        return IndicatorResult(open.size, breakdowns = listOf(byType(open), Breakdown("workflow_state", Numbers.counts(open.map { "${it.type}.${it.state}" }))))
    }

    // KPI-GRV-03: quejas con resolved_date en el periodo, por severidad (una reabierta no cuenta: la reapertura vacía la fecha)
    fun resolved(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = rows.filter { it.type == Attention.GRIEVANCE && within(it.resolved, start, end) }
        return IndicatorResult(done.size, breakdowns = listOf(Breakdown("severity", Numbers.counts(done.map { it.severity ?: NONE }))))
    }

    // KPI-GRV-04: en closed con closed_date en el periodo, por tipo, sin los originales cerrados por reclasificación
    fun closed(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = counted(rows).filter { it.state == CLOSED && within(it.closed, start, end) }
        return IndicatorResult(done.size, breakdowns = listOf(byType(done)))
    }

    // KPI-GRV-05: quejas rechazadas con closed_date en el periodo; las desistidas, aparte en el desglose
    fun rejected(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val finals = rows.filter { it.type == Attention.GRIEVANCE && it.state in REJECTED_OR_WITHDRAWN && within(it.closed, start, end) }
        val counts = finals.groupingBy { it.state!! }.eachCount()
        return IndicatorResult(
            counts[REJECTED] ?: 0,
            breakdowns = listOf(Breakdown("workflow_state", REJECTED_OR_WITHDRAWN.map { mapOf("key" to it, "value" to (counts[it] ?: 0)) }))
        )
    }

    // KPI-GRV-06: abiertas con deadline_status VENCIDO, por tipo
    fun overdue(rows: List<AttentionRow>): IndicatorResult {
        val late = counted(rows).filter { it.open && it.deadlineStatus == OVERDUE }
        return IndicatorResult(late.size, breakdowns = listOf(byType(late)))
    }

    // KPI-GRV-07 (#6 de la Tabla 9.5): recibidas en el periodo con responded_at, en cualquier estado ÷ recibidas en el periodo
    fun absolved(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val inPeriod = counted(rows).filter { within(it.received, start, end) }
        val answered = inPeriod.count { it.responded != null }
        val byType =
            Attention.OBJECTS.mapNotNull { type ->
                val list = inPeriod.filter { it.type == type }
                if (list.isEmpty()) {
                    null
                } else {
                    val n = list.count { it.responded != null }
                    mapOf("key" to type, "value" to Numbers.percent(n, list.size), "numerator" to n, "denominator" to list.size)
                }
            }
        return IndicatorResult(Numbers.percent(answered, inPeriod.size), answered, inPeriod.size, listOf(Breakdown("type", byType)))
    }

    // KPI-GRV-08: las recibidas en el periodo (value) y las abiertas (open) por severidad, categoría, territorio (la localidad o,
    // sin ella, la comunidad), grupo de interés (las anónimas aparte) y canal; los 10 primeros y «Otros»
    fun breakdowns(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate,
        territories: Map<String, String>,
        stakeholders: Map<String, String>
    ): IndicatorResult {
        val counted = counted(rows)
        val inPeriod = counted.filter { within(it.received, start, end) }
        val open = counted.filter { it.open }

        fun by(
            name: String,
            key: (AttentionRow) -> String?,
            label: (String) -> String
        ): Breakdown {
            val keys = (inPeriod + open).map { key(it) ?: NONE }.distinct()
            val sorted =
                keys
                    .map { k -> Triple(k, inPeriod.count { (key(it) ?: NONE) == k }, open.count { (key(it) ?: NONE) == k }) }
                    .sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first })
            val top =
                sorted.take(TOP).map { (k, value, opened) ->
                    mapOf(
                        "key" to k,
                        "label" to
                            if (k ==
                                NONE
                            ) {
                                "Sin dato"
                            } else {
                                label(k)
                            },
                        "value" to value,
                        "open" to opened
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
                            "open" to rest.sumOf { it.third }
                        )
                    )
                }
            return Breakdown(name, top + others)
        }
        return IndicatorResult(
            inPeriod.size,
            breakdowns =
                listOf(
                    by("severity", { if (it.type == Attention.GRIEVANCE) it.severity else null }) { it },
                    by("category", { it.category }) { it },
                    by("territory", { it.locality ?: it.community }) { territories[it] ?: "Territorio" },
                    by("stakeholder", { if (it.anonymous) ANONYMOUS else it.stakeholder }) {
                        if (it ==
                            ANONYMOUS
                        ) {
                            "Anónimas"
                        } else {
                            stakeholders[it] ?: Labels.STAKEHOLDER
                        }
                    },
                    by("channel", { it.channel }) { it }
                )
        )
    }

    // KPI-GRV-09: los 12 meses que terminan en `end`, recibidas (como KPI-GRV-01) y cerradas (como KPI-GRV-04); value es lo
    // recibido en el último mes
    fun receivedVsClosed(
        rows: List<AttentionRow>,
        end: LocalDate
    ): IndicatorResult {
        val counted = counted(rows)
        val series =
            (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }.map { month ->
                val received = counted.count { it.received != null && YearMonth.from(it.received) == month }
                val closed = counted.count { it.state == CLOSED && it.closed != null && YearMonth.from(it.closed) == month }
                mapOf("period" to month.toString(), "value" to received, "received" to received, "closed" to closed)
            }
        return IndicatorResult(series.last()["value"] as Int, series = series)
    }

    // KPI-GRV-10: sobre las atendidas en el periodo (queja con resolved_date; consulta y solicitud con responded_at), la mediana
    // de días desde la recepción (value) y el porcentaje dentro de plazo frente a resolution_due_date (quejas) y
    // response_due_date (consultas): numerator y denominator. las solicitudes no tienen plazo y solo dan su mediana
    fun attentionTime(
        rows: List<AttentionRow>,
        start: LocalDate,
        end: LocalDate
    ): IndicatorResult {
        val done = counted(rows).filter { within(attended(it), start, end) && it.received != null }
        val measured = done.filter { due(it) != null }
        val onTime = measured.count { !attended(it)!!.isAfter(due(it)) }
        val byType =
            Attention.OBJECTS.mapNotNull { type ->
                val list = done.filter { it.type == type }
                if (list.isEmpty()) return@mapNotNull null
                val withDue = list.filter { due(it) != null }
                val n = withDue.count { !attended(it)!!.isAfter(due(it)) }
                linkedMapOf<String, Any?>("key" to type, "value" to median(list.map(::days)), "attended" to list.size).apply {
                    if (type != Attention.REQUEST) {
                        put("onTime", Numbers.percent(n, withDue.size))
                        put("numerator", n)
                        put("denominator", withDue.size)
                    }
                }
            }
        return IndicatorResult(median(done.map(::days)), onTime, measured.size, listOf(Breakdown("type", byType)))
    }

    // el día en que se atendió: la resolución de una queja, la respuesta de una consulta o una solicitud
    private fun attended(row: AttentionRow): LocalDate? = if (row.type == Attention.GRIEVANCE) row.resolved else row.responded

    private fun due(row: AttentionRow): LocalDate? =
        when (row.type) {
            Attention.GRIEVANCE -> row.resolutionDue
            Attention.INQUIRY -> row.responseDue
            else -> null
        }

    private fun days(row: AttentionRow): Long = ChronoUnit.DAYS.between(row.received, attended(row))

    // la mediana con un decimal; null sin valores
    fun median(values: List<Long>): BigDecimal? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        val value = if (sorted.size % 2 == 1) BigDecimal(sorted[middle]) else BigDecimal(sorted[middle - 1] + sorted[middle]).divide(BigDecimal(2))
        return value.setScale(1, RoundingMode.HALF_UP)
    }

    const val CLOSED = "closed"
    const val REJECTED = "rejected"
    val REJECTED_OR_WITHDRAWN = listOf(REJECTED, "withdrawn")
}
```

Después, la lectura de las QCS en `DashboardData`, los diez KPI-GRV y el tablero en `Kpis`, el campo en `Indicators` y la
marca en `Dashboards`:

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, reemplazar:

```kotlin

import org.slf4j.LoggerFactory
import sgspe.common.Criteria
import sgspe.common.LIMA
```

por:

```kotlin

import org.slf4j.LoggerFactory
import sgspe.attention.QcsDueDates
import sgspe.common.Attention
import sgspe.common.Criteria
import sgspe.common.LIMA
```

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, reemplazar:

```kotlin
    }

    // la moneda de la campaña pedida (PEN si la campaña no la trae, RNF-I18N-002); null si no se pidió una campaña o si la
    // pedida no existe o no se lee: quien llama lo distingue por filters.explorationCampaign
```

por:

```kotlin
    }

    // ---- quejas, consultas y solicitudes (F4) ----

    // las QCS de los proyectos, de los tres objetos, filtradas por territorio y ámbito como los compromisos (su localidad o su
    // comunidad). se leen enteras (no por periodo): la regla de conteo necesita la cadena de reclasificaciones, y cada KPI-GRV
    // corta por la fecha que le toca. como quien mira: una QCS confidencial que no lee no está (ASM-095)
    suspend fun attention(): List<AttentionRow> =
        cached("attention") {
            if (filters.projects.isEmpty()) return@cached emptyList<AttentionRow>()
            val territories = engagementTerritories()
            Attention.OBJECTS.flatMap { type ->
                read(type, criteria = listOf(Criteria.anyOf("project", filters.projects)))
                    .filter { q ->
                        val locality = Values.text(q.attributes["locality"])
                        val community = Values.text(q.attributes["community"])
                        territories.all { t -> (locality != null && locality in t.localities) || (community != null && community in t.communities) }
                    }.map { attentionRow(type, it) }
            }
        }

    private fun attentionRow(
        type: String,
        record: RecordResponse
    ): AttentionRow {
        val a = record.attributes

        fun date(name: String): LocalDate? = Values.text(a[name])?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return AttentionRow(
            type,
            record.id,
            record.state,
            QcsDueDates.receivedDate(a[Attention.RECEIVED_AT]),
            QcsDueDates.receivedDate(a[Attention.RESPONDED_AT]),
            date("resolved_date"),
            date("closed_date"),
            date(QcsDueDates.RESOLUTION),
            date(QcsDueDates.RESPONSE),
            Values.text(a[Attention.DEADLINE_STATUS]),
            Values.text(a["channel"]),
            Values.text(a["category"]),
            Values.text(a[Attention.SEVERITY]),
            Values.text(a["locality"]),
            Values.text(a["community"]),
            Values.text(a["stakeholder"]),
            Values.bool(a["anonymous"]) == true,
            Attention.RECLASSIFIED_FROM
                .getValue(type)
                .keys
                .mapNotNull { field -> Values.text(a[field])?.let { field to it } }
                .toMap()
        )
    }

    // la moneda de la campaña pedida (PEN si la campaña no la trae, RNF-I18N-002); null si no se pidió una campaña o si la
    // pedida no existe o no se lee: quien llama lo distingue por filters.explorationCampaign
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
package sgspe.dashboard

import sgspe.common.ProjectParameters
import sgspe.common.Values
```

por:

```kotlin
package sgspe.dashboard

import sgspe.common.Attention
import sgspe.common.ProjectParameters
import sgspe.common.Values
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
    const val ENGAGEMENT = "engagement"
    const val COMMITMENTS = "commitments"

    // los KPI de F2 no aplican explorationCampaign (dashboards.md §3.1: solo compromisos e inversión social) ni owner, el
```

por:

```kotlin
    const val ENGAGEMENT = "engagement"
    const val COMMITMENTS = "commitments"
    const val ATTENTION = "attention"

    // los indicadores que leen QCS y que, a quien no ve las confidenciales, se lo dicen con confidentialHidden (dashboards.md §8,
    // ASM-095): los KPI-GRV y KPI-ENG-05, que cuenta como atendida una pregunta derivada a una QCS
    fun confidentialSensitive(code: String): Boolean = code.startsWith("KPI-GRV-") || code == "KPI-ENG-05"

    // los KPI de F2 no aplican explorationCampaign (dashboards.md §3.1: solo compromisos e inversión social) ni owner, el
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
                    "KPI-COM-08",
                    "KPI-COM-11"
                )
        )

    // lo que leen todos los KPI-COM: el compromiso, su estado y su territorio
```

por:

```kotlin
                    "KPI-COM-08",
                    "KPI-COM-11"
                ),
            // dashboards.md §5.7: flujo, absueltas, desgloses, tendencia y tiempos
            ATTENTION to (1..10).map { "KPI-GRV-%02d".format(it) }
        )

    // los KPI-GRV no aplican la campaña ni el responsable de un compromiso (dashboards.md §3.1); el territorio y el ámbito sí,
    // por la localidad o la comunidad de la QCS
    private val ATTENTION_IGNORES: Set<String> = setOf("explorationCampaign", "owner")

    // los campos de los tres objetos de QCS
    private fun qcs(vararg fields: String): List<String> = Attention.OBJECTS.flatMap { o -> fields.map { "$o.$it" } }

    // lo que leen todos los KPI-GRV: el proyecto, la recepción, el territorio y los vínculos de reclasificación (§3.3)
    private val QCS: List<String> =
        qcs("project", "received_at", "locality", "community") + Attention.RECLASSIFIED_FROM.flatMap { (o, links) -> links.keys.map { "$o.$it" } }

    // lo que leen todos los KPI-COM: el compromiso, su estado y su territorio
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
                val reschedules = data.reschedules(rows.map { it.id })
                reschedules.warn(CommitmentKpis.overdueSeries(rows, reschedules.rows, data.filters.periodEnd, data.today))
            }
        )

```

por:

```kotlin
                val reschedules = data.reschedules(rows.map { it.id })
                reschedules.warn(CommitmentKpis.overdueSeries(rows, reschedules.rows, data.filters.periodEnd, data.today))
            },
            // ---- F4: quejas, consultas y solicitudes (dashboards.md §4.5); leen como quien mira (ASM-095) ----
            Kpi("KPI-GRV-01", "QCS recibidas", ATTENTION, "QCS", QCS + qcs("channel"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.received(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-02", "QCS abiertas", ATTENTION, "QCS", QCS, ignores = ATTENTION_IGNORES) { AttentionKpis.open(it.attention()) },
            Kpi(
                "KPI-GRV-03",
                "Quejas resueltas",
                ATTENTION,
                "quejas",
                QCS + listOf("grievance.resolved_date", "grievance.severity"),
                ignores = ATTENTION_IGNORES
            ) {
                AttentionKpis.resolved(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-04", "QCS cerradas", ATTENTION, "QCS", QCS + qcs("closed_date"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.closed(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-05", "Quejas rechazadas", ATTENTION, "quejas", QCS + "grievance.closed_date", ignores = ATTENTION_IGNORES) {
                AttentionKpis.rejected(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi("KPI-GRV-06", "QCS fuera de plazo", ATTENTION, "QCS", QCS + qcs("deadline_status"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.overdue(it.attention())
            },
            Kpi("KPI-GRV-07", "QCS absueltas", ATTENTION, "%", QCS + qcs("responded_at"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.absolved(it.attention(), it.filters.periodStart, it.filters.periodEnd)
            },
            Kpi(
                "KPI-GRV-08",
                "Desgloses de QCS",
                ATTENTION,
                "QCS",
                QCS + qcs("category", "stakeholder", "anonymous", "channel") + "grievance.severity",
                ignores = ATTENTION_IGNORES
            ) { data ->
                val rows = data.attention()
                val stakeholders = rows.mapNotNull { it.stakeholder }.toSet()
                AttentionKpis.breakdowns(
                    rows,
                    data.filters.periodStart,
                    data.filters.periodEnd,
                    data.names("locality", rows.mapNotNull { it.locality }) + data.names("community", rows.mapNotNull { it.community }),
                    Labels.of(data, data.stakeholders().filter { it.id in stakeholders })
                )
            },
            Kpi("KPI-GRV-09", "QCS recibidas frente a cerradas por mes", ATTENTION, "QCS", QCS + qcs("closed_date"), ignores = ATTENTION_IGNORES) {
                AttentionKpis.receivedVsClosed(it.attention(), it.filters.periodEnd)
            },
            Kpi(
                "KPI-GRV-10",
                "Tiempo de atención",
                ATTENTION,
                "días",
                QCS + qcs("responded_at") + listOf("grievance.resolved_date", "grievance.resolution_due_date", "inquiry.response_due_date"),
                ignores = ATTENTION_IGNORES
            ) { AttentionKpis.attentionTime(it.attention(), it.filters.periodStart, it.filters.periodEnd) }
        )

```

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, reemplazar:

```kotlin
package sgspe.dashboard

import com.fasterxml.jackson.annotation.JsonValue
import sgspe.common.Values
```

por:

```kotlin
package sgspe.dashboard

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import sgspe.common.Values
```

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, reemplazar:

```kotlin
    val warnings: List<String>? = null,
    // los filtros pedidos que este indicador no aplica (ruling P27): community en los KPI-STK, explorationCampaign en F2
    val ignoredFilters: List<String>? = null
) {
    @JsonValue
```

por:

```kotlin
    val warnings: List<String>? = null,
    // los filtros pedidos que este indicador no aplica (ruling P27): community en los KPI-STK, explorationCampaign en F2
    val ignoredFilters: List<String>? = null,
    // F4: true si quien mira no ve las QCS confidenciales y el indicador las lee (dashboards.md §8); un booleano, nunca el
    // número de excluidas. null en los demás
    val confidentialHidden: Boolean? = null
) {
    @JsonValue
```

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, reemplazar:

```kotlin
            "missingFields" to missingFields,
            "warnings" to warnings,
            "ignoredFilters" to ignoredFilters
        ).forEach { (key, item) -> if (item != null) out[key] = item }
        return out
```

por:

```kotlin
            "missingFields" to missingFields,
            "warnings" to warnings,
            "ignoredFilters" to ignoredFilters,
            "confidentialHidden" to confidentialHidden
        ).forEach { (key, item) -> if (item != null) out[key] = item }
        return out
```

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, reemplazar:

```kotlin
    val filters: Map<String, Any?>,
    val computedAt: String,
    val indicators: List<IndicatorValue>
)

```

por:

```kotlin
    val filters: Map<String, Any?>,
    val computedAt: String,
    val indicators: List<IndicatorValue>,
    // F4: en el tablero attention, true si quien mira no ve las QCS confidenciales (dashboards.md §8); no viaja en los demás
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val confidentialHidden: Boolean? = null
)

```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Readability
import sgspe.common.Records
```

por:

```kotlin
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
// falta un campo) y los campos que faltan, nunca cero (RQ-DSH-032). KPI-STK-03 exige un solo proyecto
// (SINGLE_PROJECT_REQUIRED). nada se guarda en el servidor (§6.8). los errores llevan su código en errors[0].message (P15):
// 404 DASHBOARD_NOT_FOUND e INDICATOR_NOT_FOUND, 400 INVALID_BREAKDOWN y TOO_MANY_RECORDS (Records.all con tope)
@Service
class Dashboards(
```

por:

```kotlin
// falta un campo) y los campos que faltan, nunca cero (RQ-DSH-032). KPI-STK-03 exige un solo proyecto
// (SINGLE_PROJECT_REQUIRED). nada se guarda en el servidor (§6.8). los errores llevan su código en errors[0].message (P15):
// 404 DASHBOARD_NOT_FOUND e INDICATOR_NOT_FOUND, 400 INVALID_BREAKDOWN y TOO_MANY_RECORDS (Records.all con tope). desde F4, el
// tablero attention, cada KPI-GRV y KPI-ENG-05 llevan confidentialHidden si quien mira no ve las QCS confidenciales
@Service
class Dashboards(
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val clock: Clock
) {
    suspend fun catalog(): List<CatalogEntry> {
```

por:

```kotlin
    private val assignments: Assignments,
    private val catalog: ModelCatalog,
    private val clock: Clock,
    private val permissions: Permissions
) {
    suspend fun catalog(): List<CatalogEntry> {
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
        val readable = Readability(metadata)
        val data = DashboardData(records, filters, readable::canRead, today)
        return DashboardResponse(name, filters.echo(), clock.instant().toString(), codes.map { evaluate(Kpis.BY_CODE.getValue(it), data, readable) })
    }

```

por:

```kotlin
        val readable = Readability(metadata)
        val data = DashboardData(records, filters, readable::canRead, today)
        val hidden = hidden()
        val indicators = codes.map { mark(evaluate(Kpis.BY_CODE.getValue(it), data, readable), hidden) }
        return DashboardResponse(name, filters.echo(), clock.instant().toString(), indicators, if (name == Kpis.ATTENTION) hidden else null)
    }

```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
        val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN, today)
        val readable = Readability(metadata)
        val value = evaluate(kpi, DashboardData(records, filters, readable::canRead, today), readable)
        val by = filters.breakdown ?: return value
        if (!value.available) return value
```

por:

```kotlin
        val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN, today)
        val readable = Readability(metadata)
        val value =
            mark(evaluate(kpi, DashboardData(records, filters, readable::canRead, today), readable), if (Kpis.confidentialSensitive(code)) hidden() else false)
        val by = filters.breakdown ?: return value
        if (!value.available) return value
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
        return result.toValue(kpi.code, kpi.label, kpi.unit).copy(ignoredFilters = ignored.ifEmpty { null })
    }

    // hoy en Lima, del reloj de la aplicación: una sola vez por petición
```

por:

```kotlin
        return result.toValue(kpi.code, kpi.label, kpi.unit).copy(ignoredFilters = ignored.ifEmpty { null })
    }

    // si a quien mira se le ocultan QCS confidenciales (Permissions.confidentialHidden)
    private suspend fun hidden(): Boolean = permissions.confidentialHidden(callers.require())

    // la marca en un indicador que lee QCS, solo si es cierta (los demás no la llevan)
    private fun mark(
        value: IndicatorValue,
        hidden: Boolean
    ): IndicatorValue = if (hidden && Kpis.confidentialSensitive(value.code)) value.copy(confidentialHidden = true) else value

    // hoy en Lima, del reloj de la aplicación: una sola vez por petición
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`, con las 11 pruebas de `AttentionKpisTest` en verde y las demás de `sgspe.dashboard` sin cambios
(los demás tableros no llevan `confidentialHidden`). `AttentionDashboardApiTest` compila.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/dashboard/AttentionKpis.kt src/main/kotlin/sgspe/dashboard/DashboardData.kt \
  src/main/kotlin/sgspe/dashboard/Kpis.kt src/main/kotlin/sgspe/dashboard/Indicators.kt src/main/kotlin/sgspe/dashboard/Dashboards.kt \
  src/test/kotlin/sgspe/dashboard/AttentionKpisTest.kt src/test/kotlin/sgspe/dashboard/AttentionDashboardApiTest.kt
git commit -F - <<'MSG'
feat: KPI-GRV y tablero attention con la marca de las QCS confidenciales

Los diez KPI-GRV cuentan cada planteamiento una vez con AttentionCounting, leen como quien mira y,
a quien no ve las QCS confidenciales, se lo dicen con confidentialHidden, también en KPI-ENG-05 y
en el tablero attention; los demás tableros no cambian.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.dashboard.AttentionDashboardApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 17: La exportación attention-register

Cierra la exportación de las QCS (especificación §5 y §5.1, ASM-101): `POST /api/sgspe/exports/attention-register` con los
tres niveles de divulgación, las hojas y columnas de `deliverables.md` §10 y el contrato de `api.md` §4.13. EXTERNO excluye
toda QCS confidencial también para quien la lee; PUBLICO cuenta planteamientos con `AttentionCounting` y `SmallCells`.

**Files:**
- Create: `src/main/kotlin/sgspe/privacy/AttentionSheets.kt`, `src/main/kotlin/sgspe/privacy/AttentionRegister.kt`
- Modify: `src/main/kotlin/sgspe/privacy/ExportController.kt`
- Test: `src/test/kotlin/sgspe/privacy/AttentionSheetsTest.kt`, `src/test/kotlin/sgspe/privacy/AttentionRegisterApiTest.kt`

**Interfaces:**
- Consumes: `sgspe.common.Attention` (`OBJECTS`, `RECLASSIFIED_FROM`, `CONFIDENTIAL`, `RECEIVED_AT`, `RESPONDED_AT`,
  `isConfidential`), `Raised`, `AttentionTotals`, `AttentionCounting.counted(rows)` y `totals(rows)` (Tarea 2);
  `Permissions.confidentialHidden(user): Boolean` (Tarea 5); `Deadlines.TRACKED` y `Tracked.dateFieldIn(state)` (Tarea 12, solo
  en la prueba); de F2 y F3, `ExportProjects.resolve`, `ExportLog.export`, `ExportFile`, `Xlsx.write`, `XlsxSheet`,
  `Disclosure`, `NOT_AVAILABLE`, `SmallCells.apply` y `RegisterSheets.code(id)`.
- Produces: `class AttentionRegister` con `suspend fun export(body: Map<String, Any?>): ExportFile`; `object AttentionSheets`
  con `records(type: String, lines: List<Raised>, context: AttentionContext, disclosure: Disclosure): XlsxSheet`,
  `summary(byType: Map<String, AttentionTotals>): XlsxSheet`, `notes(disclosure: Disclosure, confidentialIncluded: Boolean):
  XlsxSheet`, `aggregates(lines: List<Raised>, readable: Set<String>, states: Map<String, List<String>>, channels: List<String>,
  categories: List<String>, confidentialIncluded: Boolean): List<XlsxSheet>`, `DUE_FIELDS`, `REQUIRES`, `FREE_TEXT`; `class
  AttentionContext(readable, stakeholders, people, places, topics, codes)`; el tipo `attention-register` en `ExportController`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/privacy/AttentionSheetsTest.kt`:

```kotlin
package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.common.Raised
import sgspe.deadline.Deadlines

// las hojas de attention-register por nivel de divulgación (deliverables.md §10, ASM-101), sin base
class AttentionSheetsTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val all = AttentionSheets.REQUIRES.toSet()
    private val stakeholder = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
    private val grievance =
        Raised(
            "grievance",
            "q1",
            "received",
            mapOf(
                "code" to "QJ-1",
                "channel" to "PRESENCIAL",
                "received_at" to "2026-10-06T02:00:00Z",
                "received_by_user_email" to "relacionista@sgspe.test",
                "anonymous" to false,
                "confidential" to true,
                "category" to "EMPLEO",
                "deadline_status" to "AL DIA",
                "ack_due_date" to "2026-10-13",
                "owner_user_email" to "gestor@sgspe.test",
                "stakeholder" to stakeholder,
                "person" to "p1",
                "description" to "Texto libre de la queja",
                "resolution" to "Texto libre de la resolución",
                "severity" to "BAJA"
            )
        )
    private val context = AttentionContext(all, stakeholders = mapOf(stakeholder to "Asociación Ejemplo"), people = mapOf("p1" to "Persona Ejemplo"))

    private fun cells(sheet: XlsxSheet): List<Any?> = sheet.rows.toList().flatten()

    @Test
    fun `INTERNO trae persona, receptor, responsable, texto libre y el plazo del estado en hora de Lima`() {
        val sheet = AttentionSheets.records("grievance", listOf(grievance), context, Disclosure.INTERNO)
        assertEquals("Quejas", sheet.name)
        val row = cells(sheet)
        assertTrue(
            listOf("Persona Ejemplo", "Asociación Ejemplo", "relacionista@sgspe.test", "gestor@sgspe.test", "Texto libre de la queja").all {
                it in row
            },
            row.toString()
        )
        // las 02:00 UTC del 6 son las 21:00 del 5 en Lima; en received corre el acuse
        assertTrue("2026-10-05 21:00" in row)
        assertEquals(listOf("AL DIA", "2026-10-13"), row.subList(sheet.header.indexOf("Situación de plazo"), sheet.header.indexOf("Plazo que corre") + 1))
        assertFalse(sheet.header.any { it.contains("identidad", ignoreCase = true) || it.contains("teléfono", ignoreCase = true) })
    }

    @Test
    fun `EXTERNO cita por codigo GI y no trae persona, receptor, responsable ni texto libre`() {
        val sheet = AttentionSheets.records("grievance", listOf(grievance), context, Disclosure.EXTERNO)
        val row = cells(sheet)
        assertTrue("GI-AAAAAAAABBBB" in row, row.toString())
        assertTrue(
            listOf(
                "Persona Ejemplo",
                "Asociación Ejemplo",
                "relacionista@sgspe.test",
                "gestor@sgspe.test",
                "Texto libre de la queja",
                "Texto libre de la resolución"
            ).none {
                it in
                    row
            }
        )
        assertFalse(listOf("Persona", "Responsable", "Recibida por", "Descripción", "Respuesta", "Resolución").any { it in sheet.header })
        assertEquals(listOf("Las QCS confidenciales no se incluyen en esta exportación."), cells(AttentionSheets.notes(Disclosure.EXTERNO, false)).take(1))
    }

    @Test
    fun `una anonima sale anonima y lo que no se lee sale no disponible`() {
        val anonymous = grievance.copy(attributes = grievance.attributes + ("anonymous" to true))
        assertTrue("anónima" in cells(AttentionSheets.records("grievance", listOf(anonymous), context, Disclosure.INTERNO)))
        val blind = AttentionContext(all - "grievance.person")
        assertTrue(NOT_AVAILABLE in cells(AttentionSheets.records("grievance", listOf(grievance), blind, Disclosure.INTERNO)))
    }

    @Test
    fun `PUBLICO cuenta planteamientos sobre la grilla con reservado y dice si incluye confidenciales`() {
        val original = Raised("inquiry", "c1", "closed", mapOf("channel" to "CARTA"))
        val sheets =
            AttentionSheets.aggregates(
                listOf(grievance, original, grievance.copy(id = "q2", attributes = grievance.attributes + ("reclassified_from_inquiry" to "c1"))),
                all,
                listOf("grievance", "inquiry", "stakeholder_request").associateWith { catalog.states(it) },
                catalog.enumValues("qcs_channel"),
                catalog.enumValues("qcs_category"),
                confidentialIncluded = false
            )
        assertEquals(listOf("Por tipo y estado", "Por tipo y canal", "Por tipo y categoría", "Absueltas por tipo", "Notas"), sheets.map { it.name })
        val states = sheets.first().rows.toList()
        // grilla completa: la solicitud tiene sus declinadas aunque no haya ninguna, y todo se reserva
        assertTrue(states.any { it[0] == "Solicitudes" && it[1] == "declinadas" })
        assertTrue(states.all { it[2] == SmallCells.HIDDEN })
        assertTrue(cells(sheets.last()).any { it.toString().contains("confidenciales: no") })
        assertFalse(sheets.flatMap { cells(it) }.any { it == "QJ-1" })
    }

    @Test
    fun `el resumen trae los conteos del reporte y el estado agrupado de PUBLICO`() {
        val summary = AttentionSheets.summary(mapOf("grievance" to sgspe.common.AttentionCounting.totals(listOf(grievance))))
        assertEquals(listOf<Any?>("Quejas", 1, 1, 0, 0, 0, 0, 0), summary.rows.first())
        assertEquals("abiertas", AttentionSheets.group("in_action"))
        assertEquals("declinadas", AttentionSheets.group("declined"))
    }

    // los estados con plazo y su fecha son los de JOB-01 (privacy solo depende de common)
    @Test
    fun `los plazos de las hojas son los de JOB-01 y sus campos existen`() {
        for (tracked in Deadlines.TRACKED.filter { it.objectName in AttentionSheets.SHEETS }) {
            assertEquals(tracked.states.associateWith { tracked.dateFieldIn(it) }, AttentionSheets.DUE_FIELDS[tracked.objectName], tracked.objectName)
        }
        for (type in AttentionSheets.SHEETS.keys) {
            val fields = catalog.objectOrNull(type)!!.fields.keys + catalog.manyToOneFields(type).map { it.first }
            assertEquals(emptyList<String>(), AttentionSheets.fields(type).filter { it !in fields }, type)
        }
    }
}
```

Crear `src/test/kotlin/sgspe/privacy/AttentionRegisterApiTest.kt`:

```kotlin
package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// POST /api/sgspe/exports/attention-register (api.md §4.13, deliverables.md §10, ASM-101): las QCS por nivel, sin confidenciales
// ni texto libre en EXTERNO (también para el gestor de quejas, que las lee), PUBLICO con la marca reservado, y el permiso EXPORT
// sobre los tres objetos
class AttentionRegisterApiTest : F4ApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String = this.token
    ): ResultBytes {
        val result =
            client
                .post()
                .uri("/api/sgspe/exports/attention-register")
                .headers { it.set("Authorization", token) }
                .bodyValue(body)
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return ResultBytes(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun sheets(bytes: ByteArray): Map<String, List<String>> =
        XSSFWorkbook(bytes.inputStream()).use { book ->
            (0 until book.numberOfSheets).associate { i -> book.getSheetAt(i).sheetName to book.getSheetAt(i).flatMap { row -> row.map { it.toString() } } }
        }

    private fun body(level: String): Map<String, Any?> =
        mapOf("filters" to mapOf("project" to listOf(testProject())), "disclosureLevel" to level, "checklistConfirmed" to true)

    @Test
    fun `INTERNO trae las tres hojas y el resumen, con la confidencial que quien exporta lee`() {
        val manager = member("GRIEVANCE_MANAGER")
        val code =
            record(
                "grievance",
                grievance(testProject(), member("COMMUNITY_RELATIONS_OFFICER"), mapOf("confidential" to true))
            )["attributes"]["code"].asString()
        inquiry(testProject())
        val file = export(body("INTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Quejas", "Consultas", "Solicitudes", "Resumen"), sheets.keys.toList())
        assertTrue(code in sheets.getValue("Quejas"))
        assertTrue(sheets.getValue("Quejas").contains("Planteamiento de prueba sobre el canal de riego."))
    }

    @Test
    fun `EXTERNO no trae confidenciales ni texto libre, tampoco para el gestor de quejas`() {
        val manager = member("GRIEVANCE_MANAGER")
        val hidden =
            record(
                "grievance",
                grievance(testProject(), member("COMMUNITY_RELATIONS_OFFICER"), mapOf("confidential" to true))
            )["attributes"]["code"].asString()
        val open = record("grievance", grievance(testProject()))["attributes"]["code"].asString()
        val file = export(body("EXTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = sheets(file.body)
        val all = cells.values.flatten()
        assertTrue(open in all)
        assertFalse(hidden in all)
        assertFalse(all.any { it.contains("canal de riego") }, "sin texto libre")
        assertTrue("Notas" in cells.keys)
        assertFalse(cells.getValue("Quejas").contains("Persona"))
    }

    @Test
    fun `PUBLICO solo cuenta con la marca reservado y dice si incluye las confidenciales`() {
        grievance(testProject())
        val file = export(body("PUBLICO"), member("SOCIAL_MANAGER").token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val cells = sheets(file.body)
        assertEquals(listOf("Por tipo y estado", "Por tipo y canal", "Por tipo y categoría", "Absueltas por tipo", "Notas"), cells.keys.toList())
        assertTrue(cells.values.flatten().contains(SmallCells.HIDDEN))
        assertFalse(cells.values.flatten().any { it.startsWith("QJ-") })
        assertTrue(cells.getValue("Notas").any { it.contains("confidenciales: no") })
    }

    @Test
    fun `sin EXPORT sobre los tres objetos es 403 y un filtro invalido INVALID_FILTER`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        assertEquals(HttpStatus.FORBIDDEN, export(body("INTERNO"), officer.token).status)
        val bad = export(mapOf("filters" to mapOf("type" to listOf("commitment")), "disclosureLevel" to "INTERNO"))
        assertEquals(HttpStatus.BAD_REQUEST, bad.status, bad.text)
        assertEquals("INVALID_FILTER", tree(bad.text)["errors"][0]["message"].asString())
        assertEquals("filters.type", tree(bad.text)["errors"][0]["field"].asString())
    }
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.privacy.AttentionSheetsTest'
```

Esperado: FAIL de compilación (`Unresolved reference 'AttentionSheets'` y `'AttentionContext'`).

- [ ] **Paso 3: Las hojas, el servicio y el tipo en el controlador**

Crear `src/main/kotlin/sgspe/privacy/AttentionSheets.kt`:

```kotlin
package sgspe.privacy

import org.springframework.http.HttpStatus
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.AttentionTotals
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Raised
import sgspe.common.Values
import sgspe.common.toCodedException
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// lo que las hojas de attention-register necesitan además de las QCS, ya leído como quien exporta. `readable` son los
// objeto.campo que puede leer (Readability); los mapas solo traen lo que puede leer, así que lo que falta sale «no disponible».
// `codes` da el código de los registros a los que apuntan reclassified_from_* y created_commitment, por id
class AttentionContext(
    val readable: Set<String>,
    val stakeholders: Map<String, String> = emptyMap(),
    val people: Map<String, String> = emptyMap(),
    val places: Map<String, String> = emptyMap(),
    val topics: Map<String, String> = emptyMap(),
    val codes: Map<String, String> = emptyMap()
) {
    fun canRead(path: String): Boolean = path in readable
}

// las hojas de attention-register según el nivel de divulgación (deliverables.md §10, ASM-101, RF-GRV-005): INTERNO trae las tres
// hojas con todas sus columnas y las confidenciales que quien exporta lee; EXTERNO, las mismas hojas sin confidenciales, sin texto
// libre, sin persona, sin quien recibió ni responsable y con el grupo de interés por código GI-, más una hoja «Notas»; PUBLICO
// solo cuenta planteamientos (AttentionCounting) sobre la grilla del catálogo con SmallCells (ASM-088). nada de esto lee datos:
// recibe lo que quien exporta pudo leer. en ningún nivel hay documento de identidad, teléfono ni correo de una persona
object AttentionSheets {
    // los estados en que corre un plazo y la fecha que mide cada uno (workflows.md «Plazos y escalamiento», ASM-100): los mismos
    // que JOB-01 (privacy solo depende de common; AttentionSheetsTest comprueba que coinciden con Deadlines.TRACKED)
    val DUE_FIELDS: Map<String, Map<String, String>> =
        mapOf(
            Attention.GRIEVANCE to
                mapOf(
                    "received" to "ack_due_date",
                    "acknowledged" to "resolution_due_date",
                    "in_assessment" to "resolution_due_date",
                    "in_investigation" to "resolution_due_date",
                    "in_action" to "resolution_due_date"
                ),
            Attention.INQUIRY to mapOf("received" to "response_due_date")
        )

    // el texto libre (LONG_TEXT) de las QCS: nunca en EXTERNO
    val FREE_TEXT: Set<String> = setOf("description", "response", "resolution", "requested_resource")

    // los campos de cada QCS que las hojas escriben: su lectura se comprueba antes (Readability)
    val COMMON_FIELDS: List<String> =
        listOf(
            "code",
            "channel",
            "received_at",
            "received_by_user_email",
            "anonymous",
            "confidential",
            "category",
            "priority",
            "deadline_status",
            "owner_user_email",
            "stakeholder",
            "person",
            "locality",
            "community",
            "topic",
            "description",
            "response",
            "responded_at",
            "closed_date"
        )
    val GRIEVANCE_FIELDS: List<String> =
        listOf("severity", "ack_due_date", "resolution_due_date", "resolved_date", "resolution", "complainant_conformity_given")
    val INQUIRY_FIELDS: List<String> = listOf("response_due_date")
    val REQUEST_FIELDS: List<String> = listOf("request_medium", "requested_resource", "created_commitment")

    // las hojas de cada tipo, en el orden de las fichas
    val SHEETS: Map<String, String> = mapOf(Attention.GRIEVANCE to "Quejas", Attention.INQUIRY to "Consultas", Attention.REQUEST to "Solicitudes")

    // el estado agrupado de PUBLICO: abiertas, cerradas, rechazadas, desistidas, declinadas
    val GROUPS: Map<String, String> = mapOf("closed" to "cerradas", "rejected" to "rechazadas", "withdrawn" to "desistidas", "declined" to "declinadas")
    const val OPEN = "abiertas"
    const val NO_DATA = "SIN DATO"
    const val ANONYMOUS = "anónima"

    private val LIMA_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    // los campos de un tipo: los comunes más los propios (la queja mide el acuse y la resolución; la consulta, la respuesta)
    fun fields(type: String): List<String> =
        COMMON_FIELDS +
            when (type) {
                Attention.GRIEVANCE -> GRIEVANCE_FIELDS
                Attention.INQUIRY -> INQUIRY_FIELDS
                else -> REQUEST_FIELDS
            }

    // los objeto.campo que leen las hojas
    val REQUIRES: List<String> =
        Attention.OBJECTS.flatMap { type -> fields(type).map { "$type.$it" } } +
            listOf("organization.name", "person.given_names", "person.surnames", "locality.name", "community.name", "topic.name", "commitment.code")

    // la fecha que mide la QCS en su estado (null fuera de los estados con plazo)
    fun dueField(
        type: String,
        state: String?
    ): String? = DUE_FIELDS[type]?.get(state)

    // la hoja de un tipo, en INTERNO o EXTERNO
    fun records(
        type: String,
        lines: List<Raised>,
        context: AttentionContext,
        disclosure: Disclosure
    ): XlsxSheet {
        require(disclosure != Disclosure.PUBLICO) { "PUBLICO no lleva registros: solo agregados" }
        val internal = disclosure == Disclosure.INTERNO
        val header =
            listOf("Código", "Canal", "Recibida") + (if (internal) listOf("Recibida por") else emptyList()) +
                listOf("Anónima") + (if (internal) listOf("Confidencial") else emptyList()) +
                listOf("Categoría", "Prioridad", "Estado", "Situación de plazo", "Plazo que corre") +
                (if (internal) listOf("Responsable") else emptyList()) + listOf("Grupo de interés") +
                (if (internal) listOf("Persona") else emptyList()) + listOf("Localidad", "Comunidad", "Tema") +
                (if (internal) listOf("Descripción", "Respuesta") else emptyList()) + listOf("Respondida", "Cerrada", "Reclasificada de") +
                when (type) {
                    Attention.GRIEVANCE ->
                        listOf("Severidad", "Acuse", "Plazo de resolución", "Resuelta") +
                            (if (internal) listOf("Resolución") else emptyList()) +
                            "Conformidad"
                    Attention.REQUEST -> listOf("Medio") + (if (internal) listOf("Recurso pedido") else emptyList()) + "Compromiso creado"
                    else -> emptyList()
                }
        val rows =
            lines.asSequence().map { line ->
                val a = line.attributes

                fun readable(name: String) = context.canRead("$type.$name")

                fun text(name: String): Any = if (readable(name)) Values.text(a[name]).orEmpty() else NOT_AVAILABLE

                fun flag(name: String): Any = if (readable(name)) (if (Values.bool(a[name]) == true) "Sí" else "No") else NOT_AVAILABLE

                fun named(
                    name: String,
                    names: Map<String, String>,
                    path: String
                ): Any {
                    if (!readable(name)) return NOT_AVAILABLE
                    val id = Values.text(a[name]) ?: return ""
                    return if (context.canRead(path)) names[id] ?: NOT_AVAILABLE else NOT_AVAILABLE
                }
                val due = dueField(type, line.state)
                listOf(text("code"), text("channel"), if (readable("received_at")) limaTime(a["received_at"]) else NOT_AVAILABLE) +
                    (if (internal) listOf(text("received_by_user_email")) else emptyList()) +
                    listOf(flag("anonymous")) + (if (internal) listOf(flag("confidential")) else emptyList()) +
                    listOf(
                        text("category"),
                        text("priority"),
                        line.state.orEmpty(),
                        if (due == null && readable("deadline_status")) "" else text("deadline_status"),
                        if (due == null) "" else text(due)
                    ) +
                    (if (internal) listOf(text("owner_user_email")) else emptyList()) +
                    listOf(stakeholder(a, context, type, internal)) +
                    (if (internal) listOf(person(a, context, type)) else emptyList()) +
                    listOf(
                        named("locality", context.places, "locality.name"),
                        named("community", context.places, "community.name"),
                        named("topic", context.topics, "topic.name")
                    ) +
                    (if (internal) listOf(text("description"), text("response")) else emptyList()) +
                    listOf(
                        if (readable("responded_at")) limaTime(a["responded_at"]) else NOT_AVAILABLE,
                        text("closed_date"),
                        reclassifiedFrom(type, a, context)
                    ) +
                    when (type) {
                        Attention.GRIEVANCE ->
                            listOf(text("severity"), text("ack_due_date"), text("resolution_due_date"), text("resolved_date")) +
                                (if (internal) listOf(text("resolution")) else emptyList()) + flag("complainant_conformity_given")
                        Attention.REQUEST ->
                            listOf(text("request_medium")) + (if (internal) listOf(text("requested_resource")) else emptyList()) +
                                named("created_commitment", context.codes, "commitment.code")
                        else -> emptyList()
                    }
            }
        return XlsxSheet(SHEETS.getValue(type), header, rows)
    }

    // el grupo de interés: su nombre en INTERNO si se lee, si no (y siempre en EXTERNO) su código GI-
    private fun stakeholder(
        a: Map<String, Any?>,
        context: AttentionContext,
        type: String,
        internal: Boolean
    ): Any {
        if (!context.canRead("$type.stakeholder")) return NOT_AVAILABLE
        val id = Values.text(a["stakeholder"]) ?: return ""
        return if (internal) context.stakeholders[id] ?: RegisterSheets.code(id) else RegisterSheets.code(id)
    }

    // la persona que plantea: «anónima», sus nombres y apellidos si quien exporta los lee, o «no disponible»
    private fun person(
        a: Map<String, Any?>,
        context: AttentionContext,
        type: String
    ): Any {
        if (Values.bool(a["anonymous"]) == true) return ANONYMOUS
        if (!context.canRead("$type.person")) return NOT_AVAILABLE
        val id = Values.text(a["person"]) ?: return ""
        return context.people[id] ?: NOT_AVAILABLE
    }

    // el código del registro original de una reclasificación
    private fun reclassifiedFrom(
        type: String,
        a: Map<String, Any?>,
        context: AttentionContext
    ): Any {
        val links = Attention.RECLASSIFIED_FROM.getValue(type)
        val id = links.keys.firstNotNullOfOrNull { Values.text(a[it]) } ?: return ""
        return context.codes[id] ?: NOT_AVAILABLE
    }

    // «Resumen» de INTERNO: los conteos del reporte unificado, por tipo y en total
    fun summary(byType: Map<String, AttentionTotals>): XlsxSheet {
        val total =
            AttentionTotals(
                byType.values.sumOf { it.received },
                byType.values.sumOf { it.open },
                byType.values.sumOf { it.closed },
                byType.values.sumOf { it.rejected },
                byType.values.sumOf { it.withdrawn },
                byType.values.sumOf { it.absolved },
                byType.values.sumOf { it.overdue }
            )
        val rows = byType.map { (type, t) -> listOf<Any?>(SHEETS.getValue(type)) + counts(t) } + listOf(listOf<Any?>("Total") + counts(total))
        return XlsxSheet("Resumen", listOf("Tipo", "Recibidas", "Abiertas", "Cerradas", "Rechazadas", "Desistidas", "Absueltas", "Vencidas"), rows.asSequence())
    }

    private fun counts(t: AttentionTotals): List<Any?> = listOf(t.received, t.open, t.closed, t.rejected, t.withdrawn, t.absolved, t.overdue)

    // «Notas»: en EXTERNO, que no salen las confidenciales ni el texto libre (sin conteos); en PUBLICO, si el conteo las incluye
    fun notes(
        disclosure: Disclosure,
        confidentialIncluded: Boolean
    ): XlsxSheet {
        val lines =
            when (disclosure) {
                Disclosure.EXTERNO ->
                    listOf(
                        "Las QCS confidenciales no se incluyen en esta exportación.",
                        "El texto libre (descripción, respuesta, resolución y recurso pedido), la persona, quien recibió y el responsable no se incluyen."
                    )
                else ->
                    listOf(
                        "El conteo incluye las QCS confidenciales: ${if (confidentialIncluded) "sí" else "no"}.",
                        "Cada planteamiento cuenta una vez, como el último registro de su reclasificación. Las celdas con menos de " +
                            "${SmallCells.MIN} planteamientos salen como «${SmallCells.HIDDEN}»."
                    )
            }
        return XlsxSheet("Notas", listOf("Nota"), lines.map { listOf<Any?>(it) }.asSequence())
    }

    // PUBLICO: planteamientos por tipo y estado agrupado, por tipo y canal, por tipo y categoría, y recibidos y absueltos por
    // tipo, sobre la grilla del catálogo con sus ceros y SmallCells en cada tabla; sin totales, territorio ni grupo de interés.
    // `states` son los estados de cada workflow, `channels` y `categories` los catálogos qcs_channel y qcs_category
    fun aggregates(
        lines: List<Raised>,
        readable: Set<String>,
        states: Map<String, List<String>>,
        channels: List<String>,
        categories: List<String>,
        confidentialIncluded: Boolean
    ): List<XlsxSheet> {
        val counted = AttentionCounting.counted(lines)
        val types = Attention.OBJECTS
        if (channels.isEmpty() || categories.isEmpty() || types.any { states[it].isNullOrEmpty() }) {
            throw Finding(
                "CATALOGUE_UNAVAILABLE",
                "Un catálogo de las QCS no está disponible: una salida PUBLICO no puede armar su grilla",
                "type",
                status = HttpStatus.CONFLICT
            ).toCodedException()
        }
        val groupAxis = types.associateWith { type -> states.getValue(type).map { group(it) }.distinct() + NOT_AVAILABLE }
        val byState = counted.groupingBy { it.type to (if (it.state in states.getValue(it.type)) group(it.state) else NOT_AVAILABLE) }.eachCount()
        val stateTable = SmallCells.apply(types.flatMap { t -> groupAxis.getValue(t).map { g -> (t to g) to (byState[t to g] ?: 0) } })

        fun byField(
            field: String,
            known: List<String>
        ): List<Pair<Pair<String, String>, Any>> {
            val axis = known + NO_DATA + NOT_AVAILABLE
            val counts =
                counted
                    .groupingBy { line ->
                        line.type to
                            when {
                                "${line.type}.$field" !in readable -> NOT_AVAILABLE
                                Values.text(line.attributes[field]) == null -> NO_DATA
                                Values.text(line.attributes[field]) in known -> Values.text(line.attributes[field])!!
                                else -> NOT_AVAILABLE
                            }
                    }.eachCount()
            return SmallCells.apply(types.flatMap { t -> axis.map { v -> (t to v) to (counts[t to v] ?: 0) } })
        }
        val absolved =
            SmallCells.apply(
                types.flatMap { t ->
                    val ofType = counted.filter { it.type == t }
                    listOf((t to "Recibidas") to ofType.size, (t to "Absueltas") to ofType.count { Values.text(it.attributes[Attention.RESPONDED_AT]) != null })
                }
            )

        fun sheet(
            name: String,
            column: String,
            cells: List<Pair<Pair<String, String>, Any>>
        ) = XlsxSheet(
            name,
            listOf("Tipo", column, "Planteamientos"),
            cells.asSequence().map { (key, count) ->
                listOf(SHEETS.getValue(key.first), key.second, count)
            }
        )
        return listOf(
            sheet("Por tipo y estado", "Estado", stateTable),
            sheet("Por tipo y canal", "Canal", byField("channel", channels)),
            sheet("Por tipo y categoría", "Categoría", byField("category", categories)),
            sheet("Absueltas por tipo", "Conteo", absolved),
            notes(Disclosure.PUBLICO, confidentialIncluded)
        )
    }

    fun group(state: String?): String = GROUPS[state] ?: OPEN

    // un instante como fecha y hora de Lima; vacío sin valor
    fun limaTime(value: Any?): String {
        val text = Values.text(value) ?: return ""
        val instant = runCatching { Instant.parse(text) }.getOrElse { runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull() } ?: return text
        return instant.atZone(LIMA).format(LIMA_TIME)
    }
}
```

Crear `src/main/kotlin/sgspe/privacy/AttentionRegister.kt`:

```kotlin
package sgspe.privacy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Raised
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
import java.time.LocalDate
import java.util.UUID

// la exportación attention-register (api.md §4.13, RF-GRV-005, ASM-101, deliverables.md §10): las quejas, consultas y solicitudes
// leídas como quien exporta (una QCS confidencial que no lee no existe para él; un campo ilegible sale «no disponible»), con el
// nivel de divulgación aplicado antes de escribir y su fila de export_log antes de entregar el archivo. exige READ y EXPORT
// sobre los tres objetos y CREATE sobre export_log antes de leer nada: un registro unificado con un tipo de menos engañaría.
// EXTERNO excluye toda QCS confidencial, también para quien las lee (security.md §9.6). los proyectos siguen ExportProjects; la
// lectura es acotada por sgspe.exports.max-rows por conjunto (400 TOO_MANY_RECORDS sobre filters.project). export_log cuenta
// planteamientos (AttentionCounting)
@Service
class AttentionRegister(
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
    // los filtros ya validados
    private class Filters(
        val periodStart: LocalDate?,
        val periodEnd: LocalDate?,
        val types: List<String>,
        val states: List<String>,
        val channel: String?,
        val category: String?,
        val includeReclassified: Boolean
    )

    suspend fun export(body: Map<String, Any?>): ExportFile {
        val user = callers.require()
        val grants = Attention.OBJECTS.flatMap { listOf(Actions.READ to it, EXPORT to it) } + (Actions.CREATE to ExportLog.OBJECT)
        permissions.require(user, "Exportar las quejas, consultas y solicitudes", *grants.toTypedArray())
        StrictBody.requireOnly(body, setOf("filters", "disclosureLevel", "checklistConfirmed"))
        val raw = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(raw, FILTERS, "filters.")
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw bad("INVALID_DISCLOSURE_LEVEL", "El nivel de divulgación debe ser INTERNO, EXTERNO o PUBLICO", "disclosureLevel")
        if (disclosure == Disclosure.EXTERNO && body["checklistConfirmed"] != true) {
            throw bad("CHECKLIST_REQUIRED", "Una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)", "checklistConfirmed")
        }
        val filters = filters(raw)
        val requested = uuids(raw["project"], "filters.project").distinct()
        val projects = scope.resolve(user, requested)
        val readability = Readability(metadata)
        val readable =
            AttentionSheets.REQUIRES
                .filter { readability.canRead(it) }
                .filter { disclosure == Disclosure.INTERNO || it.substringAfter('.') !in AttentionSheets.FREE_TEXT }
                .toSet()
        val read = if (projects?.isEmpty() == true) emptyList() else read(projects, filters)
        // EXTERNO: nunca una confidencial; si quien exporta no lee la marca de un tipo, ninguna de ese tipo (falla cerrada)
        val visible =
            if (disclosure == Disclosure.EXTERNO) {
                read.filter { "${it.type}.${Attention.CONFIDENTIAL}" in readable && !Attention.isConfidential(it.attributes) }
            } else {
                read
            }
        val counted = AttentionCounting.counted(visible)
        val lines = if (filters.includeReclassified) visible else counted
        val confidentialIncluded = !permissions.confidentialHidden(user)
        val sheets = sheets(lines, counted, readable, disclosure, filters.types, confidentialIncluded)
        val export = withContext(Dispatchers.IO) { ExportFile.create("quejas-consultas-solicitudes-${disclosure.name.lowercase()}.xlsx", clock.instant()) }
        try {
            withContext(Dispatchers.IO) { export.file.outputStream().use { Xlsx.write(sheets, it) } }
            log.export(user, OBJECT_NAME, "XLSX", disclosure, description(projects, raw, disclosure), counted.size, projects?.singleOrNull())
            return export
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    private suspend fun sheets(
        lines: List<Raised>,
        counted: List<Raised>,
        readable: Set<String>,
        disclosure: Disclosure,
        types: List<String>,
        confidentialIncluded: Boolean
    ): List<XlsxSheet> {
        if (disclosure == Disclosure.PUBLICO) {
            return AttentionSheets.aggregates(
                counted,
                readable,
                Attention.OBJECTS.associateWith { catalog.states(it) },
                catalog.enumValues("qcs_channel"),
                catalog.enumValues("qcs_category"),
                confidentialIncluded
            )
        }
        val context = context(lines, readable, disclosure)
        val byType = lines.groupBy { it.type }
        val sheets = types.map { type -> AttentionSheets.records(type, byType[type].orEmpty(), context, disclosure) }
        return if (disclosure == Disclosure.INTERNO) {
            sheets + AttentionSheets.summary(types.associateWith { type -> AttentionCounting.totals(counted.filter { it.type == type }) })
        } else {
            sheets + AttentionSheets.notes(disclosure, confidentialIncluded = false)
        }
    }

    // las QCS de los tipos pedidos, con los filtros en la consulta; un tipo sin estados pedidos que le apliquen no se lee
    private suspend fun read(
        projects: List<UUID>?,
        filters: Filters
    ): List<Raised> {
        val out = mutableListOf<Raised>()
        for (type in filters.types) {
            val states = if (filters.states.isEmpty()) null else filters.states.filter { it in catalog.states(type) }
            if (states?.isEmpty() == true) continue
            val criteria =
                listOfNotNull(
                    projects?.let { Criteria.anyOf("project", it) },
                    states?.let { Criteria.inState(it) },
                    period(filters)
                )
            val equal = listOfNotNull(filters.channel?.let { "channel" to it }, filters.category?.let { "category" to it }).toMap()
            out += records.all(type, equal, criteria, max = maxRows - out.size, field = TOO_MANY_FIELD).map { Raised(type, it.id, it.state, it.attributes) }
        }
        return out
    }

    // el periodo filtra received_at en fechas de Lima: desde el inicio del primer día hasta el fin del último
    private fun period(filters: Filters): RecordCriterion? {
        if (filters.periodStart == null && filters.periodEnd == null) return null
        val from = (filters.periodStart ?: LocalDate.of(1900, 1, 1)).atStartOfDay(LIMA).toInstant()
        val until = (filters.periodEnd ?: LocalDate.of(9999, 1, 1)).plusDays(1).atStartOfDay(LIMA).toInstant()
        return Criteria.between(Attention.RECEIVED_AT, from, until)
    }

    // los nombres y códigos que muestran las hojas, leídos como quien exporta y solo si los puede leer. en EXTERNO no hace falta
    // ningún nombre de persona ni de grupo de interés: van por código
    private suspend fun context(
        lines: List<Raised>,
        readable: Set<String>,
        disclosure: Disclosure
    ): AttentionContext {
        val internal = disclosure == Disclosure.INTERNO

        fun ids(field: String) = lines.mapNotNull { Values.text(it.attributes[field]) }
        val stakeholders = if (internal) readIds("stakeholder", ids("stakeholder")) else emptyList()
        val organizations =
            if (internal &&
                "organization.name" in readable
            ) {
                names("organization", stakeholders.mapNotNull { Values.text(it.attributes["organization"]) })
            } else {
                emptyMap()
            }
        val stakeholderPeople =
            if (internal &&
                "person.given_names" in readable
            ) {
                personNames(stakeholders.mapNotNull { Values.text(it.attributes["person"]) }, readable)
            } else {
                emptyMap()
            }
        val stakeholderNames =
            stakeholders
                .mapNotNull { s ->
                    val name =
                        Values.text(s.attributes["organization"])?.let { organizations[it] }
                            ?: Values.text(s.attributes["person"])?.let { stakeholderPeople[it] }
                    name?.ifBlank { null }?.let { s.id to it }
                }.toMap()
        val people = if (internal && "person.given_names" in readable) personNames(ids("person"), readable) else emptyMap()
        val places =
            (if ("locality.name" in readable) names("locality", ids("locality")) else emptyMap()) +
                (if ("community.name" in readable) names("community", ids("community")) else emptyMap())
        val topics = if ("topic.name" in readable) names("topic", ids("topic")) else emptyMap()
        val codes =
            Attention.OBJECTS
                .flatMap { source ->
                    val pointing =
                        lines.flatMap { line ->
                            Attention.RECLASSIFIED_FROM
                                .getValue(line.type)
                                .filterValues { it == source }
                                .keys
                                .mapNotNull { Values.text(line.attributes[it]) }
                        }
                    if ("$source.code" in readable) names(source, pointing, "code").toList() else emptyList()
                }.toMap() + (if ("commitment.code" in readable) names("commitment", ids("created_commitment"), "code") else emptyMap())
        return AttentionContext(readable, stakeholderNames, people, places, topics, codes)
    }

    private suspend fun personNames(
        ids: Collection<String>,
        readable: Set<String>
    ): Map<String, String> {
        val surnames = "person.surnames" in readable
        return readIds("person", ids).associate { p ->
            p.id to listOfNotNull(Values.text(p.attributes["given_names"]), if (surnames) Values.text(p.attributes["surnames"]) else null).joinToString(" ")
        }
    }

    private suspend fun names(
        objectName: String,
        ids: Collection<String>,
        field: String = "name"
    ): Map<String, String> = readIds(objectName, ids).mapNotNull { r -> Values.text(r.attributes[field])?.let { r.id to it } }.toMap()

    // por ids, en tramos de CHUNK y con el tope del conjunto; sin READ sobre el objeto, nada (la celda sale «no disponible»)
    private suspend fun readIds(
        objectName: String,
        ids: Collection<String>
    ): List<RecordResponse> {
        if (!Readability(metadata).canRead(objectName)) return emptyList()
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += records.all(objectName, ids = chunk, max = maxRows - out.size, field = TOO_MANY_FIELD)
        }
        return out
    }

    // ---- el cuerpo ----

    private fun filters(raw: Map<String, Any?>): Filters {
        val types = texts(raw["type"], "filters.type")
        if (types.any { it !in Attention.OBJECTS }) {
            throw bad("INVALID_FILTER", "El filtro filters.type debe ser uno de ${Attention.OBJECTS.joinToString(", ")}", "filters.type")
        }
        val known = Attention.OBJECTS.flatMap { catalog.states(it) }.distinct()
        val states = texts(raw["state"], "filters.state")
        if (states.any { it !in known }) throw bad("INVALID_FILTER", "El filtro filters.state debe ser un estado de WF-E1, WF-E2 o WF-E3", "filters.state")
        val start = date(raw["periodStart"], "filters.periodStart")
        val end = date(raw["periodEnd"], "filters.periodEnd")
        if (start != null && end != null &&
            start.isAfter(end)
        ) {
            throw bad("INVALID_FILTER", "periodStart no puede ser posterior a periodEnd", "filters.periodStart")
        }
        val include = raw["includeReclassified"]
        if (include != null &&
            include !is Boolean
        ) {
            throw bad("INVALID_FILTER", "El filtro filters.includeReclassified es verdadero o falso", "filters.includeReclassified")
        }
        return Filters(
            start,
            end,
            types.distinct().ifEmpty { Attention.OBJECTS },
            states.distinct(),
            enumValue(raw["channel"], "qcs_channel", "filters.channel"),
            enumValue(raw["category"], "qcs_category", "filters.category"),
            include == true
        )
    }

    private fun texts(
        raw: Any?,
        field: String
    ): List<String> {
        val values =
            when (raw) {
                null -> return emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values.map { it as? String ?: throw bad("INVALID_FILTER", "El filtro $field es una lista de textos", field) }
    }

    private fun date(
        raw: Any?,
        field: String
    ): LocalDate? {
        if (raw == null) return null
        return runCatching { LocalDate.parse(raw.toString()) }.getOrNull() ?: throw bad("INVALID_FILTER", "El filtro $field es una fecha AAAA-MM-DD", field)
    }

    private fun enumValue(
        raw: Any?,
        enum: String,
        field: String
    ): String? {
        if (raw == null) return null
        val value = raw as? String
        if (value == null || value !in catalog.enumValues(enum)) throw bad("INVALID_FILTER", "El filtro $field debe ser un valor del catálogo $enum", field)
        return value
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
        const val EXPORT = "EXPORT"

        // export_log.object_name de esta exportación: los tres objetos
        val OBJECT_NAME = Attention.OBJECTS.joinToString(",")
        private const val TOO_MANY_FIELD = "filters.project"
        val FILTERS = setOf("project", "periodStart", "periodEnd", "type", "state", "channel", "category", "includeReclassified")
        private const val CHUNK = 500
    }
}
```

En `src/main/kotlin/sgspe/privacy/ExportController.kt`, reemplazar:

```kotlin
    private val matrix: CommitmentMatrix
) {
```

por:

```kotlin
    private val matrix: CommitmentMatrix,
    private val attention: AttentionRegister
) {
```

En `src/main/kotlin/sgspe/privacy/ExportController.kt`, reemplazar:

```kotlin
                "commitment-matrix" -> matrix.export(body)
```

por:

```kotlin
                "commitment-matrix" -> matrix.export(body)
                "attention-register" -> attention.export(body)
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`, con las 6 pruebas de `AttentionSheetsTest` en verde; `ArchitectureTest` sigue en verde
(`sgspe.privacy` solo importa `sgspe.common`) y `AttentionRegisterApiTest` compila.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/privacy/AttentionSheets.kt src/main/kotlin/sgspe/privacy/AttentionRegister.kt \
  src/main/kotlin/sgspe/privacy/ExportController.kt src/test/kotlin/sgspe/privacy/AttentionSheetsTest.kt \
  src/test/kotlin/sgspe/privacy/AttentionRegisterApiTest.kt
git commit -F - <<'MSG'
feat: exportación attention-register de quejas, consultas y solicitudes

INTERNO trae las tres hojas y el resumen con lo que quien exporta lee; EXTERNO excluye las QCS
confidenciales, el texto libre, la persona, el receptor y el responsable; PUBLICO cuenta
planteamientos sobre la grilla del catálogo con la marca reservado y dice si incluye las
confidenciales. Exige EXPORT sobre los tres objetos y deja su fila en export_log.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.privacy.AttentionRegisterApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 18: Documentos, vigía de arquitectura, verificación final, push y PR

**Files:**
- Modify: `docs/api.md` (§4.4, permiso de la derivación), `docs/workflows.md` (WF-E1 `start_action`, «Listeners»,
  «Avisos»), `CLAUDE.md`, `src/test/kotlin/sgspe/ArchitectureTest.kt`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: la rama `feat/fase-4-2a-qcs` en verde y el PR. Los documentos dicen lo que este plan decidió frente a ellos
  (decisiones 3, 4 y 7); el resto del diseño de F4 ya lo escribió el plan 4.0.

- [ ] **Paso 1: Los documentos y `CLAUDE.md`**

En `docs/api.md`, reemplazar:

```markdown
| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/engagement-questions/{id}/referral` | Deriva una pregunta pendiente a una consulta, una solicitud o una queja, creada y vinculada (`origin_question`) en un solo comando | CREATE sobre el objeto destino; READ sobre `engagement_question` | `{ target, attributes }` con `target` igual a `inquiry`, `stakeholder_request` o `grievance` → `201 { question, created }` | `IK`, `CR` | F4 |

- **Derivación (F4).** En una transacción, crea la QCS de `target` con `attributes` (sujetos a `AttentionWriteGuard`)
```

por:

```markdown
| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/engagement-questions/{id}/referral` | Deriva una pregunta pendiente a una consulta, una solicitud o una queja, creada y vinculada (`origin_question`) en un solo comando | CREATE sobre el objeto destino; READ y UPDATE sobre `engagement_question`, porque escribe `referred_at` como quien deriva | `{ target, attributes }` con `target` igual a `inquiry`, `stakeholder_request` o `grievance` → `201 { question, created }` | `IK`, `CR` | F4 |

- **Derivación (F4).** En una transacción, crea la QCS de `target` con `attributes` (sujetos a `AttentionWriteGuard`)
```

En `docs/workflows.md`, reemplazar:

```markdown
| `assess` (Evaluar) | `acknowledged` | `in_assessment` | `GRIEVANCE_MANAGER` | — | Obligatorio (`requiresReason`) | — | [PRM-01 §5, §8] UNDER_REVIEW y Assessment |
| `investigate` (Investigar) | `in_assessment` | `in_investigation` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | — | [PRM-01 §5, §8] INVESTIGATION, opcional |
| `start_action` (Atender) | `in_assessment` | `in_action` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | `ApprovalNoticeListener`: `approval_pending` a los roles de `resolve_high_severity` si la queja es ALTA o CRITICA y `policy_high_severity_requires_approver` está activo (ASM-102) | [PRM-01 §5, §8] ACTION_REQUIRED y Action |
| `start_action_after_investigation` (Atender) | `in_investigation` | `in_action` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | Como `start_action` | Ídem |
| `resolve` (Resolver) | `in_action` | `resolved` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `resolution` y `response` informadas; rechaza la severidad ALTA o CRITICA si `policy_high_severity_requires_approver` está activo (RN-GRV-002, ASM-036) | Obligatorio (`requiresReason`) | `QcsTransitionDatesListener` escribe `resolved_date` | [PRM-01 §5, §8] RESOLVED |
```

por:

```markdown
| `assess` (Evaluar) | `acknowledged` | `in_assessment` | `GRIEVANCE_MANAGER` | — | Obligatorio (`requiresReason`) | — | [PRM-01 §5, §8] UNDER_REVIEW y Assessment |
| `investigate` (Investigar) | `in_assessment` | `in_investigation` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | — | [PRM-01 §5, §8] INVESTIGATION, opcional |
| `start_action` (Atender) | `in_assessment` | `in_action` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | `GraveGrievanceNoticeListener`: `approval_pending` a los roles de `resolve_high_severity` si la queja es ALTA o CRITICA y `policy_high_severity_requires_approver` está activo (ASM-102) | [PRM-01 §5, §8] ACTION_REQUIRED y Action |
| `start_action_after_investigation` (Atender) | `in_investigation` | `in_action` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `category` y `severity` informadas | Obligatorio (`requiresReason`) | Como `start_action` | Ídem |
| `resolve` (Resolver) | `in_action` | `resolved` | `GRIEVANCE_MANAGER` | `AttentionWriteGuard`: `resolution` y `response` informadas; rechaza la severidad ALTA o CRITICA si `policy_high_severity_requires_approver` está activo (RN-GRV-002, ASM-036) | Obligatorio (`requiresReason`) | `QcsTransitionDatesListener` escribe `resolved_date` | [PRM-01 §5, §8] RESOLVED |
```

En `docs/workflows.md`, reemplazar:

```markdown
Los listeners son los once de §7.5 del modelo de dominio, con `ApprovalNoticeListener` en lugar de
`NotificationListener` desde la decisión D2, `DeadlineStatusListener` desde F3 y `RiskNoticeListener` y
`WarningNoticeListener` desde F4. Corren como quien escribió y son atómicos con la escritura solo dentro de una
transición o de un endpoint con transacción. Esta tabla agrega su relación con los workflows.

| Listener (paquete) | Se dispara con | Transiciones que lo disparan | Escribe en | Atómico | Si no puede escribir | Consecuencia para los roles |
```

por:

```markdown
Los listeners son los once de §7.5 del modelo de dominio, con `ApprovalNoticeListener` en lugar de
`NotificationListener` desde la decisión D2, `DeadlineStatusListener` desde F3 y `RiskNoticeListener` y
`WarningNoticeListener` desde F4, más `GraveGrievanceNoticeListener` (F4), que publica el aviso de aprobación pendiente de
una queja grave aparte de `ApprovalNoticeListener` porque no lo dispara un envío sino la severidad. Corren como quien
escribió y son atómicos con la escritura solo dentro de una transición o de un endpoint con transacción. Esta tabla agrega
su relación con los workflows.

| Listener (paquete) | Se dispara con | Transiciones que lo disparan | Escribe en | Atómico | Si no puede escribir | Consecuencia para los roles |
```

En `docs/workflows.md`, reemplazar:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | Alta, cambio o baja de `contribution_item` | Ninguna (alimenta `register_procurement`) | `total_amount` de la `social_contribution` | Ídem | No falla; JOB-02 recalcula | `UPDATE` sobre `social_contribution`, con motivo |
| `CommitmentDerivationsListener` (`sgspe.commitment`) | Alta o cambio del origen de un `commitment`; transiciones a `fulfilled`, `closed` y `cancelled`, y `reopen_execution` | `register_fulfillment`, `reopen_execution`, `close` y las `cancel_*` de WF-D | `requires_senior_approval`, `fulfilled_date`, `closed_date` y `cancelled_date` del mismo compromiso; en `reopen_execution` vacía `fulfilled_date` | Sí en las transiciones | En el alta o el cambio, no falla; en una transición, la revierte | Los roles de esas transiciones necesitan `UPDATE` con motivo sobre `commitment`; `FIELD_AGENT` no tiene esas transiciones |
| `QcsTransitionDatesListener` (`sgspe.attention`) | Transiciones de una QCS a `resolved` o a un estado final (`closed` y, en la queja, `rejected` y `withdrawn`), y la reapertura de una queja | `resolve`, `resolve_high_severity`, `reopen`, las de cierre de WF-E1, WF-E2 y WF-E3, y `reject_*` y `withdraw_*` de WF-E1 | `resolved_date` (queja) y `closed_date` del mismo registro, en todo estado final | Sí | La transición se revierte | Los roles de esas transiciones ya tienen `UPDATE` sobre el objeto, con motivo |
| `RequestClosureListener` (`sgspe.attention`) | Transición de un `commitment` a `closed` y, desde F5, de un `social_contribution` a `closed` (ASM-068) | `close` de WF-D y, desde F5, `close` de WF-P | Aplica `close_accepted` a la `stakeholder_request` que originó el registro, con la marca y con motivo | Sí | Revierte el cierre del compromiso o del aporte | Los roles de `close` de WF-D y de WF-P tienen `READ` y `UPDATE` sobre `stakeholder_request` y figuran en `close_accepted` |
| `DeadlineStatusListener` (`sgspe.deadline`) | Cambio de `due_date` de un `commitment` (desde `approved`, solo la reprogramación) | Ninguna | `deadline_status` del mismo compromiso, con la misma regla de JOB-01 y con la marca | Sí: corre en la transacción de la reprogramación | Una regla que rechaza la escritura se registra y la reprogramación sigue (JOB-01 corrige a la hora); un fallo de la base revierte la reprogramación | Quien reprograma ya tiene `UPDATE` sobre `commitment`, con motivo |
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación (`submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D) y toda transición que sale del estado pendiente; desde F4, una queja ALTA o CRITICA que entra en `in_action` con `policy_high_severity_requires_approver` activo, un cambio de `severity` a ALTA o CRITICA en `in_action` y toda transición que sale de `in_action` (ASM-102) | Las mismas; desde F4, `start_action`, `start_action_after_investigation`, `resolve_high_severity` y `withdraw_in_action` de WF-E1 | El módulo wasichai-notifications (`Notifications.publish` y `resolve`), en la transacción de la transición | Sí | Un aviso mal formado se registra y la transición sigue; un fallo de la base al guardarlo revierte la transición; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien envió, así que todo rol que envía a aprobación tiene `READ` sobre ella (`security.md`). Si quien envía un compromiso no puede leer lo que decide el nivel (D5), el aviso va a los roles de `approve` y de `approve_senior` |

| `RiskNoticeListener` (`sgspe.notification`) | Revisión no residual que sube `risk_level` a ALTO o CRITICO (de un nivel menor, o de ALTO a CRITICO), en la transacción del endpoint de revisiones | Ninguna (la primera revisión aplica `assess` en la misma transacción) | El módulo wasichai-notifications: publica `risk_escalated` del nivel y resuelve el anterior del mismo riesgo | Sí: corre en la transacción del endpoint | Un aviso mal formado se registra y la revisión sigue; un fallo de la base al guardarlo revierte la revisión; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien revisa, que tiene `READ` sobre ella |
```

por:

```markdown
| `ContributionTotalsListener` (`sgspe.investment`) | Alta, cambio o baja de `contribution_item` | Ninguna (alimenta `register_procurement`) | `total_amount` de la `social_contribution` | Ídem | No falla; JOB-02 recalcula | `UPDATE` sobre `social_contribution`, con motivo |
| `CommitmentDerivationsListener` (`sgspe.commitment`) | Alta o cambio del origen de un `commitment`; transiciones a `fulfilled`, `closed` y `cancelled`, y `reopen_execution` | `register_fulfillment`, `reopen_execution`, `close` y las `cancel_*` de WF-D | `requires_senior_approval`, `fulfilled_date`, `closed_date` y `cancelled_date` del mismo compromiso; en `reopen_execution` vacía `fulfilled_date` | Sí en las transiciones | En el alta o el cambio, no falla; en una transición, la revierte | Los roles de esas transiciones necesitan `UPDATE` con motivo sobre `commitment`; `FIELD_AGENT` no tiene esas transiciones |
| `GraveGrievanceNoticeListener` (`sgspe.notification`, F4) | Una queja ALTA o CRITICA que entra en `in_action` con `policy_high_severity_requires_approver` activo, un cambio de `severity` a ALTA o CRITICA en `in_action` y toda transición que sale de `in_action` (ASM-102) | `start_action`, `start_action_after_investigation`, `resolve`, `resolve_high_severity` y `withdraw_in_action` de WF-E1 | El módulo wasichai-notifications, en la fuente `sgspe.approvals` con la clave `approval_pending:grievance:<id>`, en la transacción de la escritura | Sí | Como `ApprovalNoticeListener`; si quien escribe no lee el proyecto o las asignaciones, se registra y la escritura sigue | Lee el proyecto y `project_assignment` como quien escribe; los destinatarios de una queja confidencial se filtran por quien la vería (`ConfidentialReaders`, regla 7 de «Destinatarios por rol») |
| `QcsTransitionDatesListener` (`sgspe.attention`) | Transiciones de una QCS a `resolved` o a un estado final (`closed` y, en la queja, `rejected` y `withdrawn`), y la reapertura de una queja | `resolve`, `resolve_high_severity`, `reopen`, las de cierre de WF-E1, WF-E2 y WF-E3, y `reject_*` y `withdraw_*` de WF-E1 | `resolved_date` (queja) y `closed_date` del mismo registro, en todo estado final | Sí | La transición se revierte | Los roles de esas transiciones ya tienen `UPDATE` sobre el objeto, con motivo |
| `RequestClosureListener` (`sgspe.attention`) | Transición de un `commitment` a `closed` y, desde F5, de un `social_contribution` a `closed` (ASM-068) | `close` de WF-D y, desde F5, `close` de WF-P | Aplica `close_accepted` a la `stakeholder_request` que originó el registro, con la marca y con motivo; busca la solicitud como quien cierra, así que una confidencial que no lee queda en `accepted` y se registra en el log (plan 4.2a, decisión 4) | Sí | Revierte el cierre del compromiso o del aporte | Los roles de `close` de WF-D y de WF-P tienen `READ` y `UPDATE` sobre `stakeholder_request` y figuran en `close_accepted` |
| `DeadlineStatusListener` (`sgspe.deadline`) | Cambio de `due_date` de un `commitment` (desde `approved`, solo la reprogramación) | Ninguna | `deadline_status` del mismo compromiso, con la misma regla de JOB-01 y con la marca | Sí: corre en la transacción de la reprogramación | Una regla que rechaza la escritura se registra y la reprogramación sigue (JOB-01 corrige a la hora); un fallo de la base revierte la reprogramación | Quien reprograma ya tiene `UPDATE` sobre `commitment`, con motivo |
| `ApprovalNoticeListener` (`sgspe.notification`) | Envío a aprobación (`submit_for_review` de WF-A, `submit_minutes` de WF-C, `submit` de WF-D) y toda transición que sale del estado pendiente | Las mismas | El módulo wasichai-notifications (`Notifications.publish` y `resolve`), en la transacción de la transición | Sí | Un aviso mal formado se registra y la transición sigue; un fallo de la base al guardarlo revierte la transición; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien envió, así que todo rol que envía a aprobación tiene `READ` sobre ella (`security.md`). Si quien envía un compromiso no puede leer lo que decide el nivel (D5), el aviso va a los roles de `approve` y de `approve_senior` |

| `RiskNoticeListener` (`sgspe.notification`) | Revisión no residual que sube `risk_level` a ALTO o CRITICO (de un nivel menor, o de ALTO a CRITICO), en la transacción del endpoint de revisiones | Ninguna (la primera revisión aplica `assess` en la misma transacción) | El módulo wasichai-notifications: publica `risk_escalated` del nivel y resuelve el anterior del mismo riesgo | Sí: corre en la transacción del endpoint | Un aviso mal formado se registra y la revisión sigue; un fallo de la base al guardarlo revierte la revisión; sin asignaciones que coincidan, una advertencia en el log | Lee `project_assignment` como quien revisa, que tiene `READ` sobre ella |
```

En `docs/workflows.md`, reemplazar:

```markdown
| Aviso | `notification_type` | Origen | Disparador | Destinatarios | Clave |
|---|---|---|---|---|---|
| Aprobación pendiente (RF-NTF-001) | `approval_pending` | `ApprovalNoticeListener` (WF-G, WF-H y `social_contribution` suman su paso a `ApprovalNoticeListener.STEPS` en su fase) | WF-A `submit_for_review`; WF-C `submit_minutes`; WF-D `submit`; WF-G `submit`; WF-H `submit_for_review` y `resubmit`; alta de `social_contribution` en `requested`; desde F4, `start_action` o `start_action_after_investigation` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo, o un cambio de `severity` a ALTA o CRITICA en `in_action` (ASM-102) | Los roles de la transición de aprobación que sigue; en la queja, los de `resolve_high_severity` | `approval_pending:<objeto>:<id>`: volver a enviar reabre el mismo aviso; el de una queja se resuelve al salir de `in_action` o si la severidad baja de ALTA |
| Compromiso por vencer (RF-NTF-001) | `commitment_due_soon` | `DeadlineNotices` | `deadline_status` pasa a POR VENCER (`due_soon_days`, ASM-006) | El responsable (`owner_user_id`) | `commitment_due_soon:<id>:<due_date>` |
| Compromiso vencido (RF-NTF-001, RF-WFL-004) | `commitment_overdue` | `DeadlineNotices` | `deadline_status` pasa a VENCIDO (nivel 0); luego, a los `escalation_first_days` (nivel 1) y `escalation_second_days` (nivel 2) días de atraso | Nivel 0: el responsable; nivel 1: los roles de `verify`; nivel 2: los de `approve_senior` (ASM-030, ASM-070). Una tarea de escalamiento en los niveles 1 y 2 | `commitment_overdue:<id>:<due_date>:<nivel>`: un aviso por nivel alcanzado, todos abiertos mientras dure el atraso |
```

por:

```markdown
| Aviso | `notification_type` | Origen | Disparador | Destinatarios | Clave |
|---|---|---|---|---|---|
| Aprobación pendiente (RF-NTF-001) | `approval_pending` | `ApprovalNoticeListener` (WF-G, WF-H y `social_contribution` suman su paso a `ApprovalNoticeListener.STEPS` en su fase); el de una queja grave, `GraveGrievanceNoticeListener` (F4) | WF-A `submit_for_review`; WF-C `submit_minutes`; WF-D `submit`; WF-G `submit`; WF-H `submit_for_review` y `resubmit`; alta de `social_contribution` en `requested`; desde F4, `start_action` o `start_action_after_investigation` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo, o un cambio de `severity` a ALTA o CRITICA en `in_action` (ASM-102) | Los roles de la transición de aprobación que sigue; en la queja, los de `resolve_high_severity` | `approval_pending:<objeto>:<id>`: volver a enviar reabre el mismo aviso; el de una queja se resuelve al salir de `in_action` o si la severidad baja de ALTA |
| Compromiso por vencer (RF-NTF-001) | `commitment_due_soon` | `DeadlineNotices` | `deadline_status` pasa a POR VENCER (`due_soon_days`, ASM-006) | El responsable (`owner_user_id`) | `commitment_due_soon:<id>:<due_date>` |
| Compromiso vencido (RF-NTF-001, RF-WFL-004) | `commitment_overdue` | `DeadlineNotices` | `deadline_status` pasa a VENCIDO (nivel 0); luego, a los `escalation_first_days` (nivel 1) y `escalation_second_days` (nivel 2) días de atraso | Nivel 0: el responsable; nivel 1: los roles de `verify`; nivel 2: los de `approve_senior` (ASM-030, ASM-070). Una tarea de escalamiento en los niveles 1 y 2 | `commitment_overdue:<id>:<due_date>:<nivel>`: un aviso por nivel alcanzado, todos abiertos mientras dure el atraso |
```

En `CLAUDE.md`, reemplazar:

```markdown
  Gradle 9.8.0. Paquetes propios `sgspe.*` (`docs/architecture.md` §5.2): `common`, `observability`, `scope`, `engagement`,
  `stakeholder`, `territory`, `dashboard`, `privacy`, `prod` y, desde F3, `commitment`, `agreement`, `evidence`, `investment`,
  `deadline` y `notification`.
- `model/`: `model.json` (los 72 objetos de F2 a F4), `roles.json`, `workflows/` (los 15 de F2 a F4: WF-A, WF-B, WF-C,
  WF-L, WF-O, WF-D, WF-I, WF-K, WF-N, WF-E1, WF-E2, WF-E3, WF-F, WF-J y WF-M), `document-types/`, `automations/` y los
```

por:

```markdown
  Gradle 9.8.0. Paquetes propios `sgspe.*` (`docs/architecture.md` §5.2): `common`, `observability`, `scope`, `engagement`,
  `stakeholder`, `territory`, `dashboard`, `privacy`, `prod` y, desde F3, `commitment`, `agreement`, `evidence`, `investment`,
  `deadline` y `notification` y, desde F4, `attention`.
- `model/`: `model.json` (los 72 objetos de F2 a F4), `roles.json`, `workflows/` (los 15 de F2 a F4: WF-A, WF-B, WF-C,
  WF-L, WF-O, WF-D, WF-I, WF-K, WF-N, WF-E1, WF-E2, WF-E3, WF-F, WF-J y WF-M), `document-types/`, `automations/` y los
```

En `CLAUDE.md`, reemplazar:

```markdown
| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |
| JOB-01 y los avisos de plazo (`DeadlineNotices`) | `src/main/kotlin/sgspe/deadline/` |
| Avisos sobre wasichai-notifications (`Notices`, `ApprovalNoticeListener`) | `src/main/kotlin/sgspe/notification/` |
| Base de las pruebas de integración del plan 3.2 (sobre `F3ApiTest`: evidencias y compromisos de los comandos) y de las de `Idempotency-Key` sin el modelo de F3 | `src/test/kotlin/sgspe/F3DomainApiTest.kt`, `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt` |
| Base de las pruebas de integración de F2 | `src/test/kotlin/sgspe/DomainApiTest.kt` |
```

por:

```markdown
| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |
| JOB-01 y los avisos de plazo (`DeadlineNotices`) | `src/main/kotlin/sgspe/deadline/` |
| Avisos sobre wasichai-notifications (`Notices`, `ApprovalNoticeListener`, `GraveGrievanceNoticeListener`, `ConfidentialReaders`) | `src/main/kotlin/sgspe/notification/` |
| QCS: `AttentionWriteGuard`, plazos y fechas, reclasificación, aceptación, derivación, `RequestClosureListener`, reporte unificado | `src/main/kotlin/sgspe/attention/` |
| Días hábiles (`HolidayCalendar`, `BusinessDays`, feriados en `sgspe.calendar.holidays`), `Attention` y `AttentionCounting`, `NotReadable` y `readAs` | `src/main/kotlin/sgspe/common/`, `src/main/resources/application.yml` |
| QCS confidenciales (`ConfidentialAttentionScope`, un `RecordReadScope`) y la regla «mismo proyecto» (`ProjectLinks`, `ProjectLinkGuard`) | `src/main/kotlin/sgspe/scope/` |
| Base de las pruebas de integración del plan 3.2 (sobre `F3ApiTest`: evidencias y compromisos de los comandos) y de las de `Idempotency-Key` sin el modelo de F3 | `src/test/kotlin/sgspe/F3DomainApiTest.kt`, `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt` |
| Base de las pruebas de integración de F2 | `src/test/kotlin/sgspe/DomainApiTest.kt` |
```

- [ ] **Paso 2: `ArchitectureTest` ve las fuentes de F4**

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, reemplazar:

```kotlin

    @Test
    fun `el escaneo ve las fuentes de F1, F2 y F3`() {
        val names = sources(main).map { it.name }
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt")), "$names")
```

por:

```kotlin

    @Test
    fun `el escaneo ve las fuentes de F1, F2, F3 y F4`() {
        val names = sources(main).map { it.name }
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt")), "$names")
```

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, reemplazar:

```kotlin
        val f3 = listOf("Idempotency.kt", "Evidences.kt", "CommitmentWriteGuard.kt", "AgreementWriteGuard.kt", "DeadlineJob.kt", "Notices.kt")
        assertTrue(names.containsAll(f3), "$names")
        val testNames = sources(test).map { it.name }
        assertTrue("ArchitectureTest.kt" in testNames, "$testNames")
```

por:

```kotlin
        val f3 = listOf("Idempotency.kt", "Evidences.kt", "CommitmentWriteGuard.kt", "AgreementWriteGuard.kt", "DeadlineJob.kt", "Notices.kt")
        assertTrue(names.containsAll(f3), "$names")
        // y las de F4 que cruzan paquetes: attention, el alcance de las confidenciales, el calendario y los avisos
        val f4 = listOf("AttentionWriteGuard.kt", "ConfidentialAttentionScope.kt", "BusinessDays.kt", "ConfidentialReaders.kt", "AttentionRegister.kt")
        assertTrue(names.containsAll(f4), "$names")
        val testNames = sources(test).map { it.name }
        assertTrue("ArchitectureTest.kt" in testNames, "$testNames")
```

- [ ] **Paso 3: Todo lo local**

```bash
cd ~/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
(cd model && python3 -m unittest 2>&1 | tail -1 && python3 apply.py --validate-only)
python3 tools/check_traceability.py && yarn format:check
grep -rn 'sgspe.commitment.NotReadable\|sgspe.commitment.ProjectLinks' src || echo limpio
git status --short
```

Esperado: `BUILD SUCCESSFUL` con todas las unitarias en verde (531 en la copia de trabajo de este plan: las 460 que deja
el plan 4.1 y 71 de este plan), `OK` del modelo, `ok: 298 requisitos, todos con su fila`, prettier sin cambios, `limpio`
y un `git status` vacío tras el commit del paso 4.

- [ ] **Paso 4: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add docs/api.md docs/workflows.md CLAUDE.md src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -F - <<'MSG'
docs: cierre del plan 4.2a con las decisiones de QCS

api.md pide UPDATE sobre la pregunta para derivarla; workflows.md nombra
GraveGrievanceNoticeListener y el límite de RequestClosureListener con una solicitud confidencial;
CLAUDE.md dice dónde está cada pieza de F4 y ArchitectureTest ve sus fuentes.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

- [ ] **Paso 5: La suite de integración (CI del PR o coordinador)**

Las clases de integración de este plan corren en la CI del PR con Testcontainers. Si el coordinador las quiere antes, por
el túnel y con `sgspe_sdd_test` libre, juntas en una sola JVM (una aprovisiona el modelo de F4 para todas):

```bash
cd ~/IdeaProjects/sgspe
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun \
  --tests 'sgspe.scope.*ApiTest' --tests 'sgspe.attention.*ApiTest' --tests 'sgspe.evidence.EvidenceQcsLinksApiTest' \
  --tests 'sgspe.deadline.*ApiTest' --tests 'sgspe.notification.*ApiTest' --tests 'sgspe.dashboard.AttentionDashboardApiTest' \
  --tests 'sgspe.privacy.AttentionRegisterApiTest' --tests 'sgspe.workflow.WorkflowMatrixApiTest' \
  --tests 'sgspe.commitment.CommitmentRecordRulesApiTest' --tests 'sgspe.engagement.EngagementWorkflowGuardsApiTest'
```

Esperado: verde; la matriz de workflows con `grievance (22 de 22 transiciones)`, `inquiry (5 de 5)` y
`stakeholder_request (7 de 8)` (solo `close_accepted` diferida). Si `ConfidentialAttentionApiTest` falla (riesgo 1 de la
especificación), no se mezcla la rama: se registra la diferencia de la plataforma y se decide con el usuario.

- [ ] **Paso 6: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin feat/fase-4-2a-qcs
gh pr create --base main --title "feat: fase 4, plan 4.2a: piezas comunes y backend de QCS" --body-file - <<'MSG'
## Qué trae

- Piezas comunes de F4: calendario de días hábiles con los feriados de 2026 y 2027, conteo de planteamientos, los ocho
  parámetros de `project` con sus rangos, alcance de los doce objetos de F4 y la regla «mismo proyecto»
  (`ProjectLinkGuard`).
- QCS confidenciales con un `RecordReadScope` (`ConfidentialAttentionScope`): solo su responsable, quien la recibió, el
  gestor de quejas y, en una queja grave, el aprobador.
- `AttentionWriteGuard` con las reglas de WF-E1, WF-E2 y WF-E3; evidencias restringidas para una QCS confidencial.
- Plazos y fechas de transición de las QCS (listeners y JOB-02); reclasificación, aceptación de solicitudes con su cierre
  por el compromiso y derivación de preguntas, con `Idempotency-Key`.
- JOB-01 por objeto con quejas y consultas; avisos `qcs_deadline` filtrados por quien vería la QCS y el aviso genérico
  `qcs_confidential_overdue`; aviso de aprobación pendiente de una queja grave.
- Reporte unificado, KPI-GRV-01 a 10 con el tablero `attention` y `confidentialHidden`, y la exportación
  `attention-register` en sus tres niveles.

## Pruebas

- Unitarias: 531 (71 nuevas).
- Integración (esta CI): alcance y confidencialidad, las tres clases de workflow de QCS, plazos, reclasificación,
  aceptación, derivación, JOB-01 y avisos, queja grave, reporte, tablero y exportación; la matriz de workflows sin las
  reclasificaciones ni `accept` en `DEFERRED`.

## Para revisar

- Decisiones 3 y 4 del plan (derivación con UPDATE sobre la pregunta; solicitud confidencial que quien cierra el
  compromiso no ve) quedan como preguntas abiertas.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

## Resumen

| Tarea | Entrega | Grupo | Integración (CI del PR) |
|---|---|---|---|
| 1 | Calendario de días hábiles y feriados de 2026 y 2027 | A | — |
| 2 | `Attention` y `AttentionCounting` | A | — |
| 3 | Parámetros de F4 y sus rangos | A | `ProjectParametersApiTest` |
| 4 | Alcance de F4 y «mismo proyecto» | B | `ProjectLinkGuardApiTest`, `CommitmentRecordRulesApiTest` |
| 5 | `ConfidentialAttentionScope` y `confidentialHidden` | B | `ConfidentialAttentionApiTest` |
| 6 | `AttentionWriteGuard` | C | `GrievanceWorkflowApiTest`, `WorkflowMatrixApiTest` |
| 7 | Evidencias de una QCS confidencial | D | `EvidenceQcsLinksApiTest` |
| 8 | Plazos y fechas de las QCS, JOB-02 | C | `QcsDueDatesApiTest` |
| 9 | Reclasificación | D | `ReclassificationApiTest`, `InquiryWorkflowApiTest`, `WorkflowMatrixApiTest` |
| 10 | Aceptación y cierre por el compromiso | E | `StakeholderRequestWorkflowApiTest`, `WorkflowMatrixApiTest` |
| 11 | Derivación de preguntas | D | `ReferralApiTest`, `EngagementWorkflowGuardsApiTest` |
| 12 | JOB-01 con quejas y consultas | D | `DeadlineJobQcsApiTest`, `DeadlineJobApiTest` |
| 13 | Avisos de plazo y aviso genérico de nivel 2 | E | `QcsNoticesApiTest` |
| 14 | Aviso de queja grave | F | `GraveGrievanceNoticesApiTest`, `ApprovalNoticesApiTest` |
| 15 | Reporte unificado | F | `AttentionReportApiTest` |
| 16 | KPI-GRV y tablero `attention` | F | `AttentionDashboardApiTest` |
| 17 | `attention-register` | F | `AttentionRegisterApiTest` |
| 18 | Documentos, vigía, verificación y PR | G | Suite completa |

## Cobertura del alcance de este plan

| Alcance de 4.2a (especificación §7 y §3) | Dónde |
|---|---|
| `ProjectScope` con los objetos de F4 | Tarea 4 |
| `ProjectLinkGuard` (y `CommitmentWriteGuard` y `CandidateLinkRule` con la misma pieza) | Tarea 4 |
| `ExactlyOneGuard` con los orígenes de F4 | Plan 4.1 (decisión 3 de 4.1); JOB-01 lo usa en la Tarea 12 |
| Vínculos de evidencias con QCS y `EVIDENCE_NOT_RESTRICTED` | Tarea 7 |
| Parámetros de F4 y sus rangos | Tarea 3 |
| `CommandTransitionGuard` con las transiciones de comando de QCS (`assess`, en 4.2b) | Tareas 9 y 10 (decisión 8) |
| `AttentionCounting` | Tarea 2 |
| `HolidayCalendar` y `BusinessDays` (§3.4) | Tarea 1 |
| `ConfidentialAttentionScope` y las reglas de confidencialidad (§3.3) | Tareas 5 y 6 |
| `AttentionWriteGuard` (§3.2) | Tarea 6 |
| `QcsDueDatesListener` y JOB-02; `QcsTransitionDatesListener` | Tarea 8 |
| Reclasificación | Tarea 9 |
| Aceptación y `RequestClosureListener` | Tarea 10 |
| Derivación, `referred_at` y RN-ENG-005 | Tarea 11 |
| JOB-01 para QCS (multiobjeto con `Organizations.having`), tareas de escalamiento | Tarea 12 |
| `qcs_deadline`, filtro de destinatarios y `qcs_confidential_overdue` con la clave `qcs_confidential_overdue:<proyecto>:<huella>` | Tarea 13 |
| `approval_pending` de queja grave | Tarea 14 |
| Reporte unificado | Tarea 15 |
| `AttentionKpis` y tablero `attention`, `confidentialHidden` (también en KPI-ENG-05) | Tarea 16 |
| `attention-register` | Tarea 17 |
| Las tres clases por workflow de QCS (`GrievanceWorkflowApiTest`, `InquiryWorkflowApiTest`, `StakeholderRequestWorkflowApiTest`) y su salida de `DEFERRED` | Tareas 6, 9 y 10 |
| PR | Tarea 18 |
