# SGSPE · Fase 4, plan 4.2b (backend: riesgos, alertas e incidentes): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Escribir el código propio de SGSPE para los riesgos sociales, la alerta temprana y los incidentes de la Fase 4
sobre el modelo del plan 4.1 y las piezas comunes del plan 4.2a: los niveles y la revisión de riesgos, `RiskWriteGuard`,
el endpoint de revisiones con `assess` como transición de comando, el aviso `risk_escalated` por nivel y por revisión
vencida, JOB-01 con la revisión de los riesgos y su tarea, `WarningWriteGuard`, la protección de `generator_key` y de las
reglas con alertas, JOB-04 con los seis tipos de regla, el aviso `warning_new`, KPI-ENG-06 a 08, los KPI-RSK con el
tablero `risks` y la exportación `risk-register`.

**Architecture:** Paquetes nuevos `sgspe.risk` (nombres, niveles y revisión de riesgos, `RiskWriteGuard`, el endpoint de
revisiones) y `sgspe.warning` (nombres de la alerta temprana, `WarningWriteGuard`, los evaluadores puros de JOB-04 y
`WarningRulesJob`); cambios en `sgspe.common` (`Criteria.filled`, `CommandTransitionGuard`, `GeneratorKeyGuard`,
`DependentsDeleteGuard`), `sgspe.deadline` (JOB-01 y `DeadlineNotices` con los riesgos), `sgspe.notification`
(`RiskNoticeListener`, `WarningNoticeListener`, fuentes `sgspe.risks` y `sgspe.warnings`), `sgspe.dashboard` y
`sgspe.privacy`. Todo escribe por `sgspe.common.Records` con la marca `SgspeWrite`; las reglas son `RecordWriteGuard`
hechas de reglas puras que `POST /api/sgspe/transition-checks` corre en seco; el endpoint honra `Idempotency-Key` como en
F3; JOB-01 y JOB-04 leen y escriben como la plataforma, con `source = job:<nombre>`.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 0.6.0 (sin
starters nuevos), R2DBC, Apache POI 5.5.1, Jackson 3 (`tools.jackson`), JUnit 5 con `wasichai-test`
(`WasichaiIntegrationTest`).

**Spec:** `docs/superpowers/specs/2026-10-09-fase-4-design.md` (vinculante): §1, §2.2 (revisiones, tarea de revisión,
incidentes), §3.1 (lo que toca a riesgos y alertas: `CommandTransitionGuard` con `assess`, `generator_key`, bajas), §3.5
(JOB-01 para riesgos, `risk_escalated` y `warning_new`, AUT-09 y AUT-11), §3.6 (riesgos), §3.7 (alerta temprana e
incidentes), §3.8 (KPI-RSK, tablero `risks` y KPI-ENG-06 a 08), §4 (los seis tipos de regla), §5 y §5.2 (`risk-register`),
§7 (fila 4.2b) y §8 (riesgo 2). Los documentos como los dejan los planes 4.0
(`docs/superpowers/plans/2026-10-09-fase-4-0-documentos.md`: `api.md` §3.3, §4.7, §4.11 y §4.13; `workflows.md` WF-F,
WF-J, WF-M, «Tipos de regla de JOB-04», «Listeners», «Avisos», «Destinatarios por rol» y «Plazos y escalamiento»;
`dashboards.md` §4.3, §4.6 y §5.8; `deliverables.md` §10), 4.1 (`docs/superpowers/plans/2026-10-09-fase-4-1-modelo.md`:
el modelo, los workflows, `roles.json`, AUT-09 y AUT-11, `F4ApiTest`) y 4.2a
(`docs/superpowers/plans/2026-10-09-fase-4-2a-comunes-qcs.md`, con sus decisiones de la revisión). Lo que la
especificación asigna a 4.2a (QCS, confidencialidad, días hábiles, reporte, KPI-GRV, `attention-register`) y a 4.3
(portal) queda fuera.

## Global Constraints

- **Repositorio y rama.** Todo el trabajo en `~/IdeaProjects/sgspe`, en la rama `feat/fase-4-2b-riesgos` creada desde
  `main` con los planes 4.0, 4.1 y 4.2a integrados. El paso 1 de la Tarea 1 lo comprueba.
- **Versiones.** Las de F3, 4.1 y 4.2a, sin cambios: wasichai `0.6.0` exacto, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK
  `25`, Gradle `9.8.0`, ktlint `1.7.1`, Apache POI `poi-ooxml:5.5.1`. Ningún starter nuevo.
- **Idioma.** Español (es-PE) en comentarios, mensajes y `detail`; nombres técnicos en inglés (ADR-0009); constantes de la
  API en inglés UPPER_SNAKE; propiedades del cuerpo en camelCase; atributos con su nombre del modelo en snake_case; valores
  ENUM en español MAYÚSCULAS sin tildes, exactamente como el modelo (`REVISION VENCIDA`, `CONTEO DE SEÑALES`, `CRITICO`).
- **Arquitectura.** Ningún literal de rol en `src/main/kotlin` (los roles salen de `model/workflows/*.json` y de
  `model/roles.json` por `ModelCatalog`); ningún código bajo el paquete `wasichai`; nunca `RecordStore`,
  `PhysicalTableRecordStore`, `AuditService` ni `ObjectSchemaManager`; `SgspeWrite` y `RecordService` solo en
  `sgspe.common`; `scope` y `privacy` solo dependen de `common` (`RiskSheets` copia los niveles y estados que necesita y
  una prueba los compara con `sgspe.risk.Risks`); los tenants solo con `Organizations`; ningún bean de estereotipo con el
  nombre simple de otro (`ArchitectureTest`). Nunca `ConfidentialRelease` (plan 4.2a): es solo de `RequestClosureListener`.
  Ningún import sin uso: ktlint de este repositorio no los quita.
- **Lecturas y escrituras.** Nunca `asPlatform` dentro de una petición: la guarda, el endpoint, los listeners, los
  tableros y la exportación leen y escriben como quien llama; solo JOB-01 (`source = job:deadline`), la fuente
  `DeadlineNotices` y JOB-04 (`source = job:warning-rules`) leen como la plataforma, con un motivo que nombra al trabajo.
  Los trabajos nunca aplican transiciones ni vinculan registros existentes: escriben campos derivados y crean registros
  nuevos (tareas y alertas) con sus relaciones ya puestas. `JobTasks.ensure` nunca dentro de una transacción abierta.
- **Errores.** Todo 400, 403 o 409 de una guarda o del endpoint lleva su código en `errors[0].message` y el campo en
  `errors[0].field` (`Finding.toCodedException`). Los códigos son los de `api.md` §3.3 tras el plan 4.0:
  `ASSESSMENT_OUT_OF_ORDER`, `FIRST_ASSESSMENT_NOT_RESIDUAL`, `RISK_THRESHOLDS_INVALID`, `MEASURE_OWNER_REQUIRED`,
  `RESIDUAL_ASSESSMENT_REQUIRED`, `HUMAN_DECISION_REQUIRED`, `GENERATOR_KEY_PLATFORM_ONLY`, `HAS_DEPENDENTS`,
  `COMMAND_ONLY`, `STATE_MISMATCH`, `OUT_OF_RANGE`, `FIELD_REQUIRED` y `ACTION_REQUIRED` en sus casos de F4.
- **Lecturas acotadas.** `Records.all(max)` con `400 TOO_MANY_RECORDS`, nunca un recorte silencioso; un parámetro de
  consulta desconocido es `422` (`StrictQuery`); una propiedad o un atributo desconocido de un comando es `400`
  (`StrictBody`).
- **Fechas.** En America/Lima (`sgspe.common.LIMA`, el `Clock` de la aplicación); instantes en UTC con `Z`. Las pruebas que
  dependen del tiempo usan fechas lejanas (enero de 2026) o relativas a ahora (JOB-04 mide ventanas que terminan hoy).
  Ninguna prueba depende de los feriados de 2027 (el plan 4.2a solo configura los de 2026).
- **Datos.** Ficticios: correos `@sgspe.test`, «Persona Ejemplo», «Asociación Ejemplo», «Proyecto Ejemplo», «Radio
  Ejemplo»; ningún nombre real de persona en nada versionado.
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
  PR en la Tarea 16.

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

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la
tarea dueña del código:

1. **Un reintento del portal tras perder la red al guardar una revisión.** Una revisión repetida dejaría dos filas
   inmutables (`appendOnly`), la tendencia en SE MANTIENE y el aviso de nivel resuelto por la segunda. Prueba:
   `RiskAssessmentsApiTest` «un reenvio con la misma clave devuelve la misma respuesta y no revisa dos veces» (Tarea 3).
2. **Una primera revisión de un riesgo sin responsable.** `assess` lo exige, pero la revisión, los valores vigentes y la
   próxima revisión se escriben antes de la transición: si la transacción no se revirtiera entera, el riesgo quedaría con
   nivel y sin evaluar. Prueba: `RiskAssessmentsApiTest` «sin responsable la primera revision no se guarda, porque assess
   lo exige» (Tarea 3).
3. **Una alerta por hora.** JOB-04 corre cada hora: sin el silencio de una alerta abierta o reciente, la misma regla
   llenaría la bandeja de `warning_new` y de tareas de triaje, y una regla del tenant lo haría en cada proyecto. Pruebas:
   `WarningRulesJobApiTest` «una alerta abierta y luego la ventana silencian la regla» y «la regla del proyecto reemplaza a
   la del tenant y una del tenant alerta en cada proyecto» (Tarea 12).
4. **Una integración que confirma alertas.** Una cuenta de servicio con el rol de `confirm` pasaría la lista de roles de
   wasichai; RN-EWS-001 exige una persona. Prueba: `EarlyWarningWorkflowApiTest` «una cuenta de servicio con el rol no
   confirma ni descarta» (Tarea 8).
5. **Una alerta de JOB-04 que nadie ve.** Si el alta de la plataforma no llegara a `WarningNoticeListener` ni a AUT-09,
   la alerta quedaría en `detected` sin aviso ni tarea (riesgo 2 de la especificación). Prueba: `WarningRulesJobApiTest`
   «la alerta de JOB-04 dispara AUT-09 y warning_new, con el motivo del trabajo» (Tarea 12); lo mismo para AUT-11 desde el
   endpoint: `RiskAssessmentsApiTest` «la revision que lleva el riesgo a CRITICO dispara AUT-11 para su responsable»
   (Tarea 3).

---
## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | El riesgo 2 de la especificación (§8) y el plan 4.1 dejan a 4.2b decidir si una escritura de la plataforma llega a `WarningNoticeListener`, a AUT-09 y a AUT-11 | Decidido por el código de wasichai 0.6.0: `RecordService` avisa a los listeners de toda escritura, también de la plataforma (`RecordService.recorded` → `notify`), y `AutomationDispatcher` es un listener sin filtro de usuario. JOB-04 no publica `warning_new` ni crea la tarea de triaje por su cuenta, ni el endpoint la de riesgo crítico. Lo vigilan `WarningRulesJobApiTest` y `RiskAssessmentsApiTest`; `workflows.md` cambia el párrafo de la salida alternativa (Tarea 16) |
| 2 | `RiskNoticeListener` publica `risk_escalated:<id>:<nivel>:<assessment_date>` y lo resuelve «con la revisión siguiente o el cierre», pero `social_risk` no guarda la fecha de su última revisión | Escucha el alta de una `risk_assessment` no residual: compara su nivel con el de la revisión no residual anterior (la clave de su aviso), resuelve ese aviso y publica el nuevo si escala; al cerrar el riesgo resuelve el de la última. No depende del orden de escritura del endpoint. Como en «Destinatarios por rol» (regla 3), quien revisa no lo recibe, aunque sea el responsable del riesgo: decisión del usuario (2026-10-09) (Tarea 5) |
| 3 | `decide_mitigation` y `decide_monitoring` exigen una decisión de tratamiento, pero `api.md` §3.3 no tiene código para una decisión equivocada | `400 FIELD_REQUIRED` sobre `treatment_decision`, como lo que falta al evaluar; `api.md` §3.3 lo dice (Tareas 2 y 16) |
| 4 | El endpoint mueve `next_review_date`; `review_status` lo calcula JOB-01 cada hora, y `DeadlineNotices` publicaría la revisión vencida con la fecha nueva y la situación vieja | El endpoint escribe también `review_status` con la misma función (`RiskReview.status`), y `DeadlineNotices` recalcula la situación con la fecha antes de avisar; `api.md` §4.7 lo dice (Tareas 3, 7 y 16) |
| 5 | JOB-01 mueve `next_review_date` «si cambió `review_every_days`», pero sin cadencia propia rige `risk_review_days` del proyecto | La cadencia efectiva (`review_every_days` o, vacía, `risk_review_days`; `review_every_days` va de 1 a 365, decisión 16) desde la última revisión de cualquier tipo: cambiar cualquiera de las dos mueve la fecha en la vuelta siguiente. El endpoint usa la misma fecha base. `workflows.md` y `domain-model.md` lo dicen (Tareas 6 y 16) |
| 6 | La especificación dice que «una regla con alertas responde `409 HAS_DEPENDENTS`» y que «lo cubre el catálogo», pero `early_warning.early_warning_rule` es opcional y `early_warning` no es `requiresReason`: `ModelCatalog.guardedDependents` no la vigila | `DependentsDeleteGuard.PRESERVED` suma esa relación, con una prueba que exige que siga siendo opcional y fuera del catálogo vigilado (Tarea 9) |
| 7 | `workflows.md` pide a JOB-04 contar «con conteos, nunca cargando los registros», pero CONTEO DE QCS cuenta con `AttentionCounting`, que necesita la cadena de reclasificaciones, y NIVEL DE RIESGO crea una alerta por riesgo | Esos dos leen sus filas (las QCS de la ventana y los riesgos revisados del proyecto, acotadas a 20 000); los otros cuatro cuentan. `workflows.md` lo dice (Tareas 12 y 16) |
| 8 | El silencio es «una alerta creada en los últimos `window_days` días» | Se mide por `detected_at` dentro de la misma ventana de días de Lima que los eventos; JOB-04 escribe `detected_at` = ahora y la clave por fecha de Lima frena dos alertas el mismo día (Tarea 12) |
| 9 | KPI-ENG-08 define dos cocientes (menciones por tono y medios por tono) y un solo valor | El valor es el porcentaje de menciones NEGATIVO del periodo (con su numerador y denominador); los demás cocientes van en el desglose `tone` (`value`, `outletPercent`); además `outlet` y `topic`. KPI-ENG-07 y 08 no aplican los filtros de territorio ni ámbito (`media_mention` no tiene territorio) y lo dicen en `ignoredFilters`. `dashboards.md` lo dice (Tareas 13 y 16). Decisión del usuario (2026-10-09): el titular de KPI-ENG-08 es el porcentaje de menciones NEGATIVO |
| 10 | KPI-RSK-04 pide «semana a semana» sin fijar la semana | Semanas de lunes a domingo; las doce que terminan en la de `periodEnd`, cada una por su lunes (`period`) (Tarea 14) |
| 11 | `risk-register` PUBLICO cuenta «riesgos activos», y el filtro `includeClosed` es común | PUBLICO nunca cuenta los cerrados; INTERNO y EXTERNO los incluyen solo con `includeClosed` (Tarea 15) |
| 12 | `ACTION_REQUIRED` en `confirm` y `dismiss` no se alcanza por la API con los roles de hoy: los dos roles de esas transiciones tienen `CONFIRM_ALERT` | La prueba de integración lo comprueba con la consulta en seco (`POST /api/sgspe/transition-checks`), que corre las guardas sin mirar los roles; la regla pura tiene su prueba unitaria (Tarea 8) |
| 13 | `WorkflowMatrixApiTest` difería WF-F entero (plan 4.1) | Ya no: `perform` aplica `assess` por el endpoint de revisiones desde `identified` (fuera de él, por la API genérica, que responde 409, como la decisión 6 de 4.2a) y `prepare` escribe la decisión de tratamiento, la medida con responsable y la revisión residual. `SocialRiskWorkflowApiTest`, `EarlyWarningWorkflowApiTest` e `IncidentWorkflowApiTest` son las clases por workflow que faltaban (Tareas 4 y 8) |
| 14 | El umbral de los tipos por conteo es «entero ≥ 1» sin tope | Sin tope; NIVEL DE RIESGO de 1 a 25. Un cambio que no toca umbral, ventana ni tipo no se juzga, para que una regla guardada antes de la guarda se pueda renombrar o desactivar (Tarea 8) |
| 15 | La ventana de eventos va «hasta ahora» y `Criteria.between` excluye el segundo instante | Hasta ahora más un segundo: un registro escrito en el mismo instante que la vuelta cuenta (Tarea 12) |
| 16 | `social_risk.review_every_days` no tenía rango, y `RiskLevels.cadence` caía en silencio a `risk_review_days` con un valor menor que 1 | Decisión del usuario (2026-10-09): `RiskWriteGuard` rechaza con `400 OUT_OF_RANGE` sobre `review_every_days` un valor que no sea un entero de 1 a 365, en el alta y en todo cambio de su valor, como la cadencia del proyecto (`ProjectParameters`); vacío sigue valiendo `risk_review_days` del proyecto, y un valor que la escritura no cambia no se juzga. `RiskLevels.cadence` ya no cae en silencio: usa el valor guardado o, vacío, el del proyecto. La ficha de `database-model.md` lo dice desde el plan 4.0 y `api.md` §3.3 en la Tarea 16 (Tareas 1, 2 y 4) |
| 17 | KPI-ENG-06 cuenta grupos de interés de las reuniones de monitoreo, y una interacción los vincula de más de una forma | Decisión del usuario (2026-10-09): en el MVP los cuenta por el vínculo `engagement_stakeholder` de cada interacción (`DashboardData.stakeholdersOf`) (Tarea 13) |
| 18 | Una regla de alerta del tenant (sin `project`) no dice en qué proyectos se evalúa | Decisión del usuario (2026-10-09): alerta en cada proyecto `active` o `suspended` que no tenga una regla propia del mismo tipo, con su propia alerta y su propio silencio (`WarningRulesJob.ACTIVE`) (Tarea 12) |
| 19 | Revisión de toda la rama, I-1: la tarea `risk_review` de JOB-01 lleva fecha y entraba en la cadena de «tarea vencida»: duplicaba `risk_escalated` en cada nivel y seguía escalando después de la revisión | Decisión del usuario (R-I1, ronda branchfix): `TASK_REFERENCE` ya no lleva `social_risk` y `DeadlineNotices` salta las tareas `risk_review:*` (`DeadlineNoticePlan.escalatesAsTask`), así que la revisión vencida avisa solo por `risk_escalated`; `early_warning → confirm` se queda (la tarea de AUT-09 nace sin fecha). El endpoint de revisiones deja hechas, en su transacción y como quien revisa, las tareas de revisión abiertas del riesgo (`RiskReviewTasks`) |
| 20 | Revisión de toda la rama, I-2: un `PUT` de un riesgo CRITICO deja vacío `risk_level`, `DerivedFieldsKeeper` lo repone y ese vacío → CRITICO disparaba AUT-11 otra vez | Decisión del usuario (R-I2, ronda branchfix): reponer un derivado igual al guardado no es un cambio. La reposición corre con `DerivedRestore`, y `RestoreAwareAutomationDispatcher`, que reemplaza al despachador de la autoconfiguración de wasichai-automation (`@ConditionalOnMissingBean`), no evalúa su eco; AUT-11 sigue en el modelo y el contrato no cambia |
| 21 | Revisión de toda la rama, I-3: `early_warning.early_warning_rule` y `detected_at` se escribían por la API genérica, y el silencio de JOB-04 y `PRESERVED` confían en ellos | Decisión del usuario (R-I3, ronda branchfix): son de la plataforma, como `generator_key`: `403 PLATFORM_ONLY` sobre el campo (también con un `PUT` que los omite; reenviarlos tal como se leyeron pasa), con la tabla `PlatformOnlyFields` de `GeneratorKeyGuard`. `detected_at` deja de ser obligatorio en el modelo y `WarningDetectedAt` le pone la hora del alta a una alerta manual, como quien la crea y con la marca |

## Lo que este plan toma de los planes 4.0, 4.1 y 4.2a

- **Documentos (4.0).** Los códigos de `api.md` §3.3; el contrato del endpoint de revisiones (§4.7), de los tableros
  (§4.11) y de `risk-register` (§4.13 y `deliverables.md` §10); WF-F, WF-J, WF-M, «Tipos de regla de JOB-04», los
  listeners, avisos, destinatarios y plazos de `workflows.md`; KPI-ENG-06 a 08 y KPI-RSK de `dashboards.md`.
- **Modelo (4.1).** Los objetos de BC-08 y BC-09 con sus campos derivados (`probability`, `impact`, `score`,
  `risk_level`, los residuales, `next_review_date` y `review_status` de `social_risk`); `risk_assessment` `appendOnly` y
  `apiOnly`; `early_warning.generator_key` único (en `SampleFields.PLATFORM_ONLY`); `early_warning_rule.threshold` en
  `AmountFieldsGuard.NOT_MONEY`; WF-F, WF-J y WF-M; AUT-09 y AUT-11; `CONFIRM_ALERT` de `SOCIAL_RISK_MANAGER` y
  `SOCIAL_MANAGER`; `F4ApiTest` (`socialRisk`, `riskAssessment`, `mitigationMeasure`, `incident`, `mediaMention`,
  `warningSignal`, `warningRule`, `earlyWarning`, `platformPatch`); `ExactlyOneGuard.GROUPS["task"]` con `social_risk` y
  `early_warning`.
- **Piezas comunes (4.2a).** `ProjectScope` y `ProjectLinkGuard` con los objetos de riesgos y alertas;
  `ProjectParameters.integer(project, "risk_review_days" | "risk_*_min_score")` y `ProjectParameters.THRESHOLDS`;
  `NotReadable` y `readAs` en `common`; `Attention`, `Raised` y `AttentionCounting.counted` (CONTEO DE QCS);
  `Deadlines.Tracked`, `Deadlines.due`, `DeadlineJob.escalationKey`, `DeadlineNoticePlan.TASK_REFERENCE` y `SENIOR`;
  `NoticeAudience`, `PlannedNotice`; `Notices.APPROVALS` y `DEADLINES`; `Kpis.confidentialSensitive`; `ExportProjects`,
  `ExportLog`, `SmallCells`, `Xlsx`, `RegisterSheets.code`; `CommandTransitionGuard.COMMANDS` sin `assess` (decisión 8 de
  4.2a); `WorkflowMatrixApiTest` con `close_accepted` como única diferida de QCS. Las decisiones de la revisión de 4.2a
  (`ConfidentialRelease` solo para `RequestClosureListener`, aceptado por el usuario y registrado en ADR-0028; feriados
  solo de 2026; `GRIEVANCE_MANAGER` con `u` sobre `engagement_question`) no cambian nada aquí.

## Interfaces con el portal (plan 4.3)

Lo que el portal consume de este plan, además de lo de 4.2a (contrato completo en `api.md`, §4.7, §4.11 y §4.13):

- `POST /api/sgspe/social-risks/{id}/assessments` con `{ attributes: { assessment_date, probability, impact, residual,
  notes } }`, `X-Change-Reason` obligatorio e `Idempotency-Key` opcional → `201 { assessment, risk }` con `ETag` (el
  `updatedAt` del riesgo) y `Location` de la revisión. Errores: `400 FIELD_REQUIRED` (`attributes.<campo>`; en la primera
  revisión, `owner_user_id` si el riesgo no tiene responsable), `400 OUT_OF_RANGE` (`attributes.probability`, `impact` o
  `assessment_date`), `400` sin código (propiedad desconocida, `residual` que no es booleano, sin motivo), `403` sin código
  (sin CREATE sobre `risk_assessment` o UPDATE sobre `social_risk`), `403 ROLE_REQUIRED` (la primera, sin el rol de
  `assess`), `409 STATE_MISMATCH` (`state`: riesgo cerrado), `409 FIRST_ASSESSMENT_NOT_RESIDUAL`
  (`attributes.residual`), `409 ASSESSMENT_OUT_OF_ORDER` (`attributes.assessment_date`), `409 RISK_THRESHOLDS_INVALID`
  (`project`), `422` (parámetro de consulta). `assess` por la API genérica: `403 COMMAND_ONLY`.
- Transiciones de WF-F por la API genérica: `400 FIELD_REQUIRED` (`treatment_decision`, `close_reason`), `409
  MEASURE_OWNER_REQUIRED` (`mitigation_measure`), `409 RESIDUAL_ASSESSMENT_REQUIRED` (`risk_assessment`); de WF-J: `403
  HUMAN_DECISION_REQUIRED` y `403 ACTION_REQUIRED` (`record`), `400 REASON_REQUIRED` en `dismiss`; de WF-M: `400
  FIELD_REQUIRED` (`immediate_response`, `consequences`). Todas en la consulta en seco.
- Reglas de alerta: `400 OUT_OF_RANGE` (`threshold`, `window_days`); borrar una con alertas: `409 HAS_DEPENDENTS`
  (`early_warning.early_warning_rule`). `early_warning.generator_key` por la API: `403 GENERATOR_KEY_PLATFORM_ONLY`;
  `early_warning.early_warning_rule` y `detected_at`: `403 PLATFORM_ONLY` sobre el campo si una persona los fija,
  cambia o vacía, también con un `PUT` que los omite (decisión 21). El formulario de alertas no los ofrece y reenvía en
  el `PUT` lo que leyó; una alerta manual nace sin regla y con la hora de su alta.
- Cadencia propia de un riesgo: `400 OUT_OF_RANGE` (`review_every_days`) si no es un entero de 1 a 365, al crear o al
  cambiarla por la API genérica; vacía vale `risk_review_days` del proyecto (decisión 16).
- Avisos en la bandeja: `risk_escalated:<riesgo>:<nivel>:<fecha>` (fuente `sgspe.risks`, `ACTION`, enlace al riesgo);
  `risk_escalated:<riesgo>:<0|1|2>:<next_review_date>` (fuente `sgspe.deadlines`); `warning_new:<alerta>` (fuente
  `sgspe.warnings`, `ACTION`, enlace a la alerta). Tareas: `risk_review:<riesgo>:<fecha>` (asignada al responsable, con
  fecha; vencida no suma `task_overdue`, y la revisión siguiente la deja hecha, decisión 19) y las de AUT-09 y AUT-11
  (un `PUT` de un riesgo ya CRITICO no crea otra, decisión 20).
- `GET /api/sgspe/dashboards/risks` → los cinco KPI-RSK en el orden de `dashboards.md` §5.8 (02, 01, 03, 04, 05), sin
  `confidentialHidden`. Formas: KPI-RSK-02 con los desgloses `matrix` y `residual` (25 filas `{ key: "P-I", probability,
  impact, value, level? }`, `level` solo con un proyecto) y `assessment` (`SIN EVALUAR`); KPI-RSK-03 con `locality`,
  `community`, `areaOfInfluence` y `project` (filas `{ key, label, value, BAJO, MEDIO, ALTO, CRITICO, "SIN NIVEL" }`);
  KPI-RSK-04 con `series` de doce semanas (`period` es el lunes; los cuatro niveles y las tres tendencias); KPI-RSK-05 con
  `overdue`, `risk` y `owner`. KPI-ENG-06 a 08 solo por `GET /api/sgspe/dashboards/indicators/{code}` (desgloses
  `engagement`; `outlet`; `tone`, `outlet` y `topic`).
- `POST /api/sgspe/exports/risk-register` con `{ filters: { project, state, category, riskLevel, includeClosed },
  disclosureLevel, checklistConfirmed }` → XLSX: INTERNO «Matriz», «Revisiones», «Medidas», «Impactos»; EXTERNO
  «Matriz» (con «Medidas», «Medidas completadas», «Medidas atrasadas»), «Revisiones», «Impactos»; PUBLICO «Por nivel y
  categoría», «Por tratamiento y nivel». Errores como `commitment-matrix`.

---

## Mapa de archivos

Todos bajo la raíz del repositorio. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `src/main/kotlin/sgspe/risk/Risks.kt` (crear) | `Risks`, `RiskThresholds`, `RiskLevels`, `RiskReview` | 1 |
| `src/main/kotlin/sgspe/risk/RiskWriteGuard.kt` (crear), `src/main/kotlin/sgspe/common/Criteria.kt` | `RiskRules`, `RiskWriteGuard`; `Criteria.filled` | 2 |
| `src/main/kotlin/sgspe/risk/RiskAssessments.kt` (crear), `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt` | Endpoint de revisiones; `assess` como comando | 3 |
| `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt` | WF-F en la matriz | 4 |
| `src/main/kotlin/sgspe/notification/RiskNotices.kt` (crear), `Notices.kt` | `risk_escalated` por nivel | 5 |
| `src/main/kotlin/sgspe/deadline/Deadlines.kt`, `DeadlineJob.kt` | JOB-01 con la revisión de riesgos y su tarea | 6 |
| `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, `src/main/kotlin/sgspe/notification/Notices.kt` | `risk_escalated` por revisión vencida | 7 |
| `src/main/kotlin/sgspe/warning/Warnings.kt`, `WarningWriteGuard.kt` (crear) | WF-J, WF-M y reglas de alerta | 8 |
| `src/main/kotlin/sgspe/common/GeneratorKeyGuard.kt`, `DependentsDeleteGuard.kt` | `generator_key` de alertas; reglas con alertas | 9 |
| `src/main/kotlin/sgspe/warning/WarningEvaluation.kt` (crear) | Los seis tipos de regla, puros | 10 |
| `src/main/kotlin/sgspe/notification/WarningNotices.kt` (crear), `Notices.kt` | `warning_new` | 11 |
| `src/main/kotlin/sgspe/warning/WarningRulesJob.kt` (crear), `src/main/resources/application.yml` | JOB-04 | 12 |
| `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt`, `DashboardData.kt`, `Kpis.kt` | KPI-ENG-06 a 08 | 13 |
| `src/main/kotlin/sgspe/dashboard/RiskKpis.kt` (crear), `DashboardData.kt`, `Kpis.kt` | KPI-RSK y tablero `risks` | 14 |
| `src/main/kotlin/sgspe/privacy/RiskSheets.kt`, `RiskRegister.kt` (crear), `ExportController.kt` | `risk-register` | 15 |
| `docs/api.md`, `docs/workflows.md`, `docs/domain-model.md`, `docs/dashboards.md`, `CLAUDE.md`, `src/test/kotlin/sgspe/ArchitectureTest.kt` | Cierre | 16 |

Las pruebas viven en `src/test/kotlin/sgspe/<paquete>/` con el nombre que da cada tarea: unitarias `*Test` (sin base) y de
integración `*ApiTest` (sobre `F4ApiTest`, en la CI del PR).

## Orden y paralelismo

Cada tarea se puede hacer en su propio worktree cuando las tareas de las que depende ya están integradas en
`feat/fase-4-2b-riesgos`. Las de un mismo grupo tocan archivos distintos y no usan nada unas de otras; las que comparten un
archivo en grupos distintos lo editan en puntos separados, así sus bloques «reemplazar» no dependen del orden. La
integración la corre la CI del PR (o el coordinador, una clase tras otra, porque todas usan `sgspe_sdd_test`).

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| A | 1, 8, 9, 11, 13 | La rama creada | Nombres y niveles de riesgo, guarda de alertas, protecciones comunes, aviso de alerta nueva y KPI-ENG: archivos disjuntos |
| B | 2, 6, 10, 14, 15 | 1; la 10 también la 8; la 14 también la 13 | Guarda de riesgos, JOB-01, evaluadores de JOB-04, KPI-RSK y `risk-register`: archivos disjuntos |
| C | 3, 7 | 2 (la 3); 6 (la 7) | Endpoint de revisiones y avisos de revisión vencida |
| D | 4, 5, 12 | 3; la 12 también la 10 y la 11 | Matriz y WF-F, aviso de nivel y JOB-04 (su prueba revisa riesgos por el endpoint) |
| E | 16 | Todas | Documentos, vigía, verificación, push y PR |

Archivos que comparten tareas: `Notices.kt` (5, 7 y 11, en puntos distintos), `DashboardData.kt` y `Kpis.kt` (13 → 14).

---
### Tarea 1: Los nombres, los niveles y la revisión de los riesgos

**Files:**
- Create: `src/main/kotlin/sgspe/risk/Risks.kt`
- Test: `src/test/kotlin/sgspe/risk/RiskLevelsTest.kt`

**Interfaces:**
- Consumes: `ProjectParameters.integer`, `ProjectParameters.THRESHOLDS` (plan 4.2a), `Values`, `ModelCatalog` (solo la
  prueba).
- Produces: `object Risks` (`SOCIAL_RISK`, `ASSESSMENT`, `MEASURE`, `ASSESS`, `IDENTIFIED`, `ASSESSED`, `CLOSED`, `OWNER`,
  `OWNER_EMAIL`, `LEVEL`, `SCORE`, `NEXT_REVIEW`, `REVIEW_STATUS`, `REVIEW_EVERY`, `REVIEWED: Set<String>`,
  `NEXT_TREATMENT: Map<String, String>`, `LEVELS: List<String>`, `ESCALATED: Set<String>`); `data class
  RiskThresholds(medium: Long, high: Long, critical: Long)` con `valid()` y `level(score: Int): String`; `object
  RiskLevels` (`UP`, `SAME`, `DOWN`, `thresholds(project)`, `trend(previous: Int?, current: Int): String?`, `rank(level)`,
  `escalates(previous: String?, current: String?): Boolean`, `cadence(risk, project): Long`); `object RiskReview`
  (`ON_TIME`, `DUE`, `OVERDUE`, `status(next: LocalDate?, today): String?`, `next(lastAssessment: LocalDate?, cadence):
  LocalDate?`, `changes(state, attributes, lastAssessment, cadence, today): Map<String, Any?>`).

Las reglas puras que comparten el endpoint de revisiones, JOB-01, los avisos, JOB-04 y los tableros (RN-RSK-001,
RN-RSK-002, ASM-004, ASM-099): el nivel por los umbrales del proyecto, la tendencia frente a la revisión anterior, cuándo
escala un aviso, la cadencia efectiva y la situación de revisión (POR REVISAR desde el día anterior, REVISION VENCIDA desde
el día siguiente).

- [ ] **Paso 1: Comprobar la rama y escribir la prueba que falla**

```bash
cd ~/IdeaProjects/sgspe && git switch main && git pull --ff-only && git switch -c feat/fase-4-2b-riesgos
test -f src/main/kotlin/sgspe/common/Attention.kt && test -f src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt && echo "4.2a integrado"
```

Esperado: `4.2a integrado`. Si no, 4.2a todavía no está en `main` y este plan espera.

Crear `src/test/kotlin/sgspe/risk/RiskLevelsTest.kt`:

```kotlin
package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.time.LocalDate

// el nivel, la tendencia, el escalamiento y la situación de revisión de un riesgo (RN-RSK-001, RN-RSK-002, ASM-004, ASM-099),
// sin base
class RiskLevelsTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `el nivel sale de los umbrales del proyecto, con 5, 10 y 15 por defecto`() {
        val defaults = RiskLevels.thresholds(emptyMap())
        assertEquals(RiskThresholds(5, 10, 15), defaults)
        assertEquals(listOf("BAJO", "MEDIO", "MEDIO", "ALTO", "ALTO", "CRITICO", "CRITICO"), listOf(4, 5, 9, 10, 14, 15, 25).map(defaults::level))
        val custom = RiskLevels.thresholds(mapOf("risk_medium_min_score" to 3, "risk_high_min_score" to 8, "risk_critical_min_score" to 20))
        assertEquals("ALTO", custom.level(12))
    }

    @Test
    fun `unos umbrales fuera de 1 a 25 o que no crecen no son validos`() {
        assertTrue(RiskThresholds(5, 10, 15).valid())
        assertFalse(RiskThresholds(10, 10, 15).valid())
        assertFalse(RiskThresholds(5, 10, 26).valid())
        assertFalse(RiskThresholds(0, 10, 15).valid())
    }

    @Test
    fun `la tendencia compara con la revision anterior del mismo tipo`() {
        assertNull(RiskLevels.trend(null, 12))
        assertEquals("AUMENTA", RiskLevels.trend(9, 12))
        assertEquals("SE MANTIENE", RiskLevels.trend(12, 12))
        assertEquals("DISMINUYE", RiskLevels.trend(16, 12))
    }

    @Test
    fun `escala al subir a ALTO o a CRITICO, nunca al bajar ni al quedarse`() {
        assertTrue(RiskLevels.escalates(null, "ALTO"))
        assertTrue(RiskLevels.escalates("MEDIO", "ALTO"))
        assertTrue(RiskLevels.escalates("ALTO", "CRITICO"))
        assertFalse(RiskLevels.escalates("ALTO", "ALTO"))
        assertFalse(RiskLevels.escalates("CRITICO", "ALTO"))
        assertFalse(RiskLevels.escalates("BAJO", "MEDIO"))
    }

    // decisión 16: review_every_days va de 1 a 365 (RiskWriteGuard), así que la cadencia ya no cae en silencio al proyecto
    @Test
    fun `la cadencia es la del riesgo o, vacia, la del proyecto`() {
        assertEquals(7L, RiskLevels.cadence(emptyMap(), emptyMap()))
        assertEquals(14L, RiskLevels.cadence(emptyMap(), mapOf("risk_review_days" to 14)))
        assertEquals(14L, RiskLevels.cadence(mapOf("review_every_days" to null), mapOf("risk_review_days" to 14)))
        assertEquals(30L, RiskLevels.cadence(mapOf("review_every_days" to 30), mapOf("risk_review_days" to 14)))
        assertEquals(365L, RiskLevels.cadence(mapOf("review_every_days" to "365"), mapOf("risk_review_days" to 14)))
    }

    // ASM-099: POR REVISAR desde el día anterior a la fecha y ese día; REVISION VENCIDA desde el día siguiente
    @Test
    fun `la situacion de revision cambia el dia anterior y el dia siguiente`() {
        val next = date("2026-10-14")
        assertEquals("AL DIA", RiskReview.status(next, date("2026-10-12")))
        assertEquals("POR REVISAR", RiskReview.status(next, date("2026-10-13")))
        assertEquals("POR REVISAR", RiskReview.status(next, date("2026-10-14")))
        assertEquals("REVISION VENCIDA", RiskReview.status(next, date("2026-10-15")))
        assertNull(RiskReview.status(null, date("2026-10-15")))
    }

    @Test
    fun `JOB-01 mueve la proxima revision con la cadencia y vacia la situacion fuera de los estados revisados`() {
        val stored = mapOf("next_review_date" to "2026-10-14")
        assertEquals(
            mapOf("next_review_date" to "2026-11-06", "review_status" to "AL DIA"),
            RiskReview.changes("monitoring", stored, date("2026-10-07"), 30, date("2026-10-20"))
        )
        assertEquals(
            mapOf("next_review_date" to "2026-10-14", "review_status" to "REVISION VENCIDA"),
            RiskReview.changes("assessed", stored, null, 7, date("2026-10-20"))
        )
        assertEquals(mapOf("review_status" to null), RiskReview.changes("closed", stored, date("2026-10-07"), 7, date("2026-10-20")))
    }

    // los nombres de Risks existen en el modelo: un renombre dejaría de revisar o de avisar en silencio
    @Test
    fun `los estados, las transiciones y los catalogos de Risks existen en el modelo`() {
        val states = catalog.states(Risks.SOCIAL_RISK)
        assertEquals(emptySet<String>(), Risks.REVIEWED - states.toSet())
        assertTrue(Risks.IDENTIFIED in states && Risks.CLOSED in states)
        for ((state, transition) in Risks.NEXT_TREATMENT.filterKeys { it != Risks.IDENTIFIED }) {
            assertEquals(state, catalog.transition(Risks.SOCIAL_RISK, transition)?.from, transition)
        }
        assertEquals(Risks.IDENTIFIED, catalog.transition(Risks.SOCIAL_RISK, Risks.ASSESS)?.from)
        assertEquals(Risks.LEVELS, catalog.enumOf(Risks.SOCIAL_RISK, Risks.LEVEL))
        assertEquals(listOf(RiskReview.ON_TIME, RiskReview.DUE, RiskReview.OVERDUE), catalog.enumOf(Risks.SOCIAL_RISK, Risks.REVIEW_STATUS))
        assertEquals(listOf(RiskLevels.UP, RiskLevels.SAME, RiskLevels.DOWN), catalog.enumOf(Risks.ASSESSMENT, "trend"))
        val derived = catalog.derivedFields(Risks.SOCIAL_RISK)
        assertTrue(derived.containsAll(listOf(Risks.LEVEL, Risks.SCORE, Risks.NEXT_REVIEW, Risks.REVIEW_STATUS)), derived.toString())
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.risk.RiskLevelsTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'RiskLevels'`, `'RiskThresholds'`, `'RiskReview'` y `'Risks'`).

- [ ] **Paso 3: Los nombres, los niveles y la revisión**

Crear `src/main/kotlin/sgspe/risk/Risks.kt`:

```kotlin
package sgspe.risk

import sgspe.common.ProjectParameters
import sgspe.common.Values
import java.time.LocalDate

// los nombres de los riesgos sociales (BC-08, database-model.md §13) que comparten RiskWriteGuard, el endpoint de revisiones,
// JOB-01, los avisos y JOB-04
object Risks {
    const val SOCIAL_RISK = "social_risk"
    const val ASSESSMENT = "risk_assessment"
    const val MEASURE = "mitigation_measure"
    const val ASSESS = "assess"
    const val IDENTIFIED = "identified"
    const val ASSESSED = "assessed"
    const val CLOSED = "closed"
    const val OWNER = "owner_user_id"
    const val OWNER_EMAIL = "owner_user_email"
    const val LEVEL = "risk_level"
    const val SCORE = "score"
    const val NEXT_REVIEW = "next_review_date"
    const val REVIEW_STATUS = "review_status"
    const val REVIEW_EVERY = "review_every_days"

    // los estados con puntaje vigente, en que JOB-01 calcula review_status (workflows.md «Plazos y escalamiento») y que
    // evalúa la regla NIVEL DE RIESGO de JOB-04
    val REVIEWED: Set<String> = setOf("assessed", "mitigating", "monitoring")

    // la transición de tratamiento que sigue en cada estado: sus roles reciben risk_escalated (workflows.md «Destinatarios por
    // rol»). un riesgo en identified llega a assessed con su primera revisión, así que su aviso va a los de decide_mitigation
    val NEXT_TREATMENT: Map<String, String> =
        mapOf(
            IDENTIFIED to "decide_mitigation",
            "assessed" to "decide_mitigation",
            "mitigating" to "start_monitoring",
            "monitoring" to "resume_mitigation"
        )

    // los niveles de riesgo de menor a mayor (catálogo «Nivel de riesgo») y los que escalan (RF-NTF-001)
    val LEVELS: List<String> = listOf("BAJO", "MEDIO", "ALTO", "CRITICO")
    val ESCALATED: Set<String> = setOf("ALTO", "CRITICO")
}

// los umbrales de nivel de un proyecto (ASM-004, RN-RSK-002): el puntaje mínimo de MEDIO, ALTO y CRITICO
data class RiskThresholds(
    val medium: Long,
    val high: Long,
    val critical: Long
) {
    // válidos como los deja ProjectWriteGuard: de 1 a 25 y crecientes. unos guardados antes de esa regla no lo son
    fun valid(): Boolean = listOf(medium, high, critical).all { it in 1..25 } && medium < high && high < critical

    fun level(score: Int): String =
        when {
            score >= critical -> Risks.LEVELS[3]
            score >= high -> Risks.LEVELS[2]
            score >= medium -> Risks.LEVELS[1]
            else -> Risks.LEVELS[0]
        }
}

// el nivel, la tendencia, el escalamiento y la cadencia de las revisiones de riesgo, puros (RN-RSK-001, RN-RSK-002, ASM-099)
object RiskLevels {
    const val UP = "AUMENTA"
    const val SAME = "SE MANTIENE"
    const val DOWN = "DISMINUYE"

    fun thresholds(project: Map<String, Any?>): RiskThresholds {
        val (medium, high, critical) = ProjectParameters.THRESHOLDS.map { ProjectParameters.integer(project, it) }
        return RiskThresholds(medium, high, critical)
    }

    // frente a la revisión anterior del mismo tipo, por el puntaje; null en la primera
    fun trend(
        previous: Int?,
        current: Int
    ): String? =
        when {
            previous == null -> null
            current > previous -> UP
            current < previous -> DOWN
            else -> SAME
        }

    // el orden de un nivel; -1 sin nivel
    fun rank(level: String?): Int = Risks.LEVELS.indexOf(level)

    // una revisión no residual escala si sube el nivel a ALTO o CRITICO: de uno menor, o de ALTO a CRITICO (RF-NTF-001)
    fun escalates(
        previous: String?,
        current: String?
    ): Boolean = current in Risks.ESCALATED && rank(current) > rank(previous)

    // la cadencia del riesgo: review_every_days (de 1 a 365, RiskWriteGuard) o, vacío, risk_review_days del proyecto (7 por
    // defecto)
    fun cadence(
        risk: Map<String, Any?>,
        project: Map<String, Any?>
    ): Long = Values.decimal(risk[Risks.REVIEW_EVERY])?.toLong() ?: ProjectParameters.integer(project, "risk_review_days")
}

// la situación de revisión de un riesgo, pura (ASM-099, workflows.md «Plazos y escalamiento»): AL DIA; POR REVISAR desde el día
// anterior a next_review_date y ese día; REVISION VENCIDA desde el día siguiente. la cadencia corre desde la última revisión
object RiskReview {
    const val ON_TIME = "AL DIA"
    const val DUE = "POR REVISAR"
    const val OVERDUE = "REVISION VENCIDA"

    fun status(
        next: LocalDate?,
        today: LocalDate
    ): String? =
        when {
            next == null -> null
            today.isAfter(next) -> OVERDUE
            !today.isBefore(next.minusDays(1)) -> DUE
            else -> ON_TIME
        }

    fun next(
        lastAssessment: LocalDate?,
        cadence: Long
    ): LocalDate? = lastAssessment?.plusDays(cadence)

    // lo que JOB-01 deja en un riesgo: en los estados revisados, next_review_date (última revisión + cadencia, o la guardada si
    // no hay revisiones que leer) y review_status; fuera de ellos, review_status vacío y next_review_date como estaba
    fun changes(
        state: String?,
        attributes: Map<String, Any?>,
        lastAssessment: LocalDate?,
        cadence: Long,
        today: LocalDate
    ): Map<String, Any?> {
        if (state !in Risks.REVIEWED) return mapOf(Risks.REVIEW_STATUS to null)
        val stored = Values.text(attributes[Risks.NEXT_REVIEW])?.let(LocalDate::parse)
        val next = next(lastAssessment, cadence) ?: stored
        return listOfNotNull(next?.let { Risks.NEXT_REVIEW to it.toString() }).toMap() + (Risks.REVIEW_STATUS to status(next, today))
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `RiskLevelsTest` con sus 8 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/risk/Risks.kt src/test/kotlin/sgspe/risk/RiskLevelsTest.kt
git commit -F - <<'MSG'
feat: niveles, tendencia y revisión de los riesgos

Risks reúne los nombres de BC-08; RiskLevels calcula el nivel con los umbrales del proyecto, la
tendencia y cuándo escala un aviso; RiskReview da la situación de revisión y la próxima fecha
con la cadencia efectiva (RN-RSK-001, ASM-004, ASM-099).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 2: `RiskWriteGuard`

**Files:**
- Create: `src/main/kotlin/sgspe/risk/RiskWriteGuard.kt`
- Modify: `src/main/kotlin/sgspe/common/Criteria.kt` (`filled`)
- Test: `src/test/kotlin/sgspe/risk/RiskWriteGuardTest.kt`

**Interfaces:**
- Consumes: `Risks` (Tarea 1); `RuleGuard`, `Finding`, `GuardOrder`, `Records.count`, `readAs` y `NotReadable` (plan
  4.2a); `WriteView` e `isLink` (F2).
- Produces: `Criteria.filled(field): RecordCriterion` (`IS NOT NULL`); `object RiskRules` (`MITIGATE`, `MONITOR`,
  `TREATMENT`, `CLOSE_REASON`, `CLOSE`, `NEEDS_MEASURE`, `NEEDS_RESIDUAL`, `MAX_REVIEW_EVERY`, `transition(name, record):
  List<Finding>`, `reviewEvery(after, changed): List<Finding>`, `measureRequired()`, `residualRequired()`); el bean
  `RiskWriteGuard(records: Records, view: WriteView)` (`@Order(GuardOrder.DOMAIN)`).

La tabla de §3.6 de la especificación: `assess` con probabilidad, impacto y responsable; mitigar con la decisión MITIGAR y
una medida con responsable; monitorear con la decisión MONITOREAR o ACEPTAR y una revisión residual; cerrar con motivo
(decisión 3). Las medidas y las revisiones se cuentan como quien transiciona; lo que no lee es `403 OBJECT_NOT_READABLE`.
En el alta y en el cambio de un riesgo, la cadencia propia (`review_every_days`) vacía o entera de 1 a 365; si no, `400
OUT_OF_RANGE` (decisión 16).

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/risk/RiskWriteGuardTest.kt`:

```kotlin
package sgspe.risk

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.WriteView
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las reglas de WF-F (workflows.md, especificación de F4 §3.6): las de datos puras y las que cuentan medidas y revisiones
// residuales, con un Records de prueba
class RiskWriteGuardTest {
    private val catalog = ModelCatalog.fromClasspath()

    private fun codes(
        name: String,
        record: Map<String, Any?>
    ) = RiskRules.transition(name, record).map { it.code to it.field }

    // un Records cuyo count responde `measures` para las medidas y `residuals` para las revisiones; sin lectura, Forbidden
    private fun guard(
        measures: Long?,
        residuals: Long?
    ): RiskWriteGuard {
        val records =
            mock(Records::class.java) { call ->
                if (call.method.name != "count") return@mock null
                val answer = if (call.arguments[0] == Risks.MEASURE) measures else residuals
                answer ?: throw ForbiddenException("sin lectura")
            }
        return RiskWriteGuard(records, mock(WriteView::class.java))
    }

    private fun transition(
        name: String,
        before: Map<String, Any?>
    ) = RecordWrite(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        Risks.SOCIAL_RISK,
        UUID.randomUUID(),
        RecordChangeKind.TRANSITIONED,
        before,
        null,
        name
    )

    private fun findings(
        guard: RiskWriteGuard,
        name: String,
        before: Map<String, Any?> = emptyMap()
    ) = runBlocking { guard.findings(transition(name, before), dryRun = false) }.map { it.code to it.field }

    @Test
    fun `evaluar exige probabilidad, impacto y responsable`() {
        assertEquals(
            listOf("FIELD_REQUIRED" to "probability", "FIELD_REQUIRED" to "impact", "FIELD_REQUIRED" to "owner_user_id"),
            codes("assess", emptyMap())
        )
        assertEquals(emptyList<Pair<String, String?>>(), codes("assess", mapOf("probability" to 3, "impact" to 4, "owner_user_id" to UUID.randomUUID())))
    }

    @Test
    fun `cada camino exige su decision de tratamiento y cerrar exige el motivo`() {
        assertEquals(listOf("FIELD_REQUIRED" to "treatment_decision"), codes("decide_mitigation", mapOf("treatment_decision" to "MONITOREAR")))
        assertEquals(emptyList<Pair<String, String?>>(), codes("decide_mitigation", mapOf("treatment_decision" to "MITIGAR")))
        assertEquals(emptyList<Pair<String, String?>>(), codes("decide_monitoring", mapOf("treatment_decision" to "ACEPTAR")))
        assertEquals(listOf("FIELD_REQUIRED" to "treatment_decision"), codes("decide_monitoring", emptyMap()))
        for (close in RiskRules.CLOSE) assertEquals(listOf("FIELD_REQUIRED" to "close_reason"), codes(close, emptyMap()), close)
    }

    @Test
    fun `mitigar exige una medida con responsable y monitorear una revision residual`() {
        val mitigate = mapOf("treatment_decision" to "MITIGAR")
        assertEquals(listOf("MEASURE_OWNER_REQUIRED" to "mitigation_measure"), findings(guard(0, 1), "decide_mitigation", mitigate))
        assertEquals(emptyList<Pair<String, String?>>(), findings(guard(1, 0), "decide_mitigation", mitigate))
        assertEquals(listOf("MEASURE_OWNER_REQUIRED" to "mitigation_measure"), findings(guard(0, 1), "resume_mitigation"))
        assertEquals(listOf("RESIDUAL_ASSESSMENT_REQUIRED" to "risk_assessment"), findings(guard(1, 0), "start_monitoring"))
        assertEquals(emptyList<Pair<String, String?>>(), findings(guard(1, 1), "decide_monitoring", mapOf("treatment_decision" to "MONITOREAR")))
    }

    @Test
    fun `sin lectura de las medidas es OBJECT_NOT_READABLE, nunca un pase`() {
        assertEquals(listOf("OBJECT_NOT_READABLE" to "mitigation_measure"), findings(guard(null, 1), "resume_mitigation"))
    }

    // decisión 16: la cadencia propia va de 1 a 365 días, como la del proyecto; vacía vale risk_review_days del proyecto
    @Test
    fun `una cadencia propia fuera de 1 a 365 dias es OUT_OF_RANGE y vacia vale la del proyecto`() {
        fun every(value: Any?) = RiskRules.reviewEvery(mapOf(Risks.REVIEW_EVERY to value), setOf(Risks.REVIEW_EVERY)).map { it.code to it.field }
        for (valid in listOf(null, "", 1, 30, "365.00")) assertEquals(emptyList<Pair<String, String?>>(), every(valid), "$valid")
        for (wrong in listOf(0, -7, 366, "2.5", "semanal")) assertEquals(listOf("OUT_OF_RANGE" to Risks.REVIEW_EVERY), every(wrong), "$wrong")
        // una cadencia que la escritura no cambia no se juzga: un valor guardado antes de la regla no bloquea otra escritura
        assertEquals(emptyList<Pair<String, String?>>(), RiskRules.reviewEvery(mapOf(Risks.REVIEW_EVERY to 0), setOf("title")))
    }

    // las transiciones de RiskRules existen en WF-F: un renombre dejaría de vigilarlas en silencio
    @Test
    fun `las transiciones de RiskRules existen en el workflow`() {
        val names = catalog.transitions(Risks.SOCIAL_RISK).map { it.name }.toSet()
        val used = RiskRules.CLOSE + RiskRules.NEEDS_MEASURE + RiskRules.NEEDS_RESIDUAL + Risks.ASSESS + "decide_mitigation"
        assertEquals(emptySet<String>(), used - names)
        assertEquals(listOf(RiskRules.MITIGATE) + RiskRules.MONITOR, catalog.enumOf(Risks.SOCIAL_RISK, RiskRules.TREATMENT))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.risk.RiskWriteGuardTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'RiskRules'` y `'RiskWriteGuard'`).

- [ ] **Paso 3: `Criteria.filled`, las reglas y la guarda**

En `src/main/kotlin/sgspe/common/Criteria.kt`, reemplazar:

```kotlin
        }

    // una fecha entre dos días, ambos incluidos (gancho de los filtros por periodo de F3)
    fun dateBetween(
```

por:

```kotlin
        }

    // el campo informado (IS NOT NULL): desde F4, la medida con responsable de RiskWriteGuard
    fun filled(field: String): RecordCriterion = RecordCriterion { definition, _ -> "${column(definition, field)} IS NOT NULL" }

    // una fecha entre dos días, ambos incluidos (gancho de los filtros por periodo de F3)
    fun dateBetween(
```

Crear `src/main/kotlin/sgspe/risk/RiskWriteGuard.kt`:

```kotlin
package sgspe.risk

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readAs
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// las reglas de datos de WF-F (workflows.md, especificación de F4 §3.6), puras: lo que cada transición exige del riesgo como
// está guardado. RiskWriteGuard suma las que leen las medidas y las revisiones
object RiskRules {
    const val MITIGATE = "MITIGAR"
    val MONITOR: Set<String> = setOf("MONITOREAR", "ACEPTAR")
    const val TREATMENT = "treatment_decision"
    const val CLOSE_REASON = "close_reason"

    val CLOSE: Set<String> = setOf("close_identified", "close_assessed", "close_mitigating", "close_monitoring")

    // las que exigen una medida con responsable (RN-RSK-003) y las que exigen una revisión residual
    val NEEDS_MEASURE: Set<String> = setOf("decide_mitigation", "resume_mitigation")
    val NEEDS_RESIDUAL: Set<String> = setOf("decide_monitoring", "start_monitoring")

    // la cadencia propia de un riesgo, en días, como la del proyecto (ProjectParameters.PERIODS)
    const val MAX_REVIEW_EVERY = 365L

    // assess con probabilidad, impacto y responsable (RF-RSK-001); la decisión de tratamiento que cada camino exige; el motivo de
    // cierre. todo 400 FIELD_REQUIRED sobre el campo: lo que falta lo completa quien transiciona
    fun transition(
        name: String,
        record: Map<String, Any?>
    ): List<Finding> {
        val required =
            when (name) {
                Risks.ASSESS -> listOf("probability", "impact", Risks.OWNER)
                in CLOSE -> listOf(CLOSE_REASON)
                else -> emptyList()
            }
        val findings =
            required.filter { Values.text(record[it]) == null }.mapTo(mutableListOf()) { field ->
                Finding("FIELD_REQUIRED", "La transición $name exige $field", field, status = HttpStatus.BAD_REQUEST)
            }
        val decision = Values.text(record[TREATMENT])
        if (name == "decide_mitigation" && decision != MITIGATE) {
            findings += Finding("FIELD_REQUIRED", "decide_mitigation exige treatment_decision $MITIGATE", TREATMENT, status = HttpStatus.BAD_REQUEST)
        }
        if (name == "decide_monitoring" && decision !in MONITOR) {
            findings += Finding("FIELD_REQUIRED", "decide_monitoring exige treatment_decision MONITOREAR o ACEPTAR", TREATMENT, status = HttpStatus.BAD_REQUEST)
        }
        return findings
    }

    // la cadencia propia (decisión 16 del plan 4.2b): vacía vale risk_review_days del proyecto; si no, un número entero de días
    // de 1 a 365. se juzga si la escritura la cambia; en un alta, siempre que llegue. 400 OUT_OF_RANGE sobre el campo
    fun reviewEvery(
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> {
        if (Risks.REVIEW_EVERY !in changed || Values.text(after[Risks.REVIEW_EVERY]) == null) return emptyList()
        val days = Values.decimal(after[Risks.REVIEW_EVERY])?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }
        if (days != null && days in 1..MAX_REVIEW_EVERY) return emptyList()
        return listOf(
            Finding(
                "OUT_OF_RANGE",
                "review_every_days es un número entero de días de 1 a 365; vacío, rige risk_review_days del proyecto",
                Risks.REVIEW_EVERY,
                status = HttpStatus.BAD_REQUEST
            )
        )
    }

    fun measureRequired(): Finding =
        Finding("MEASURE_OWNER_REQUIRED", "Mitigar exige al menos una medida de mitigación con responsable (RN-RSK-003)", Risks.MEASURE)

    fun residualRequired(): Finding =
        Finding("RESIDUAL_ASSESSMENT_REQUIRED", "Monitorear exige al menos una revisión residual del riesgo (RN-RSK-003)", Risks.ASSESSMENT)
}

// RiskWriteGuard (sgspe.risk, especificación de F4 §3.6): las reglas de WF-F por cualquier puerta (la API genérica, el endpoint
// de revisiones y la consulta en seco). las de datos son RiskRules; aquí se suman las que leen, como quien transiciona, las
// medidas del riesgo (409 MEASURE_OWNER_REQUIRED) y sus revisiones residuales (409 RESIDUAL_ASSESSMENT_REQUIRED). lo que no
// se puede leer es 403 OBJECT_NOT_READABLE, nunca un pase. en el alta y el cambio, la cadencia propia (400 OUT_OF_RANGE)
@Component
@Order(GuardOrder.DOMAIN)
class RiskWriteGuard(
    private val records: Records,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Risks.SOCIAL_RISK) return emptyList()
        if ((change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) && !change.isLink()) {
            val after = view.after(change)
            val changed = if (change.kind == RecordChangeKind.CREATED) after.keys else view.changed(change, after)
            return RiskRules.reviewEvery(after, changed)
        }
        if (change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val name = change.transition ?: return emptyList()
        val id = change.recordId ?: return emptyList()
        val findings = RiskRules.transition(name, change.before.orEmpty()).toMutableList()
        if (findings.any { it.blocking }) return findings
        return try {
            if (name in RiskRules.NEEDS_MEASURE && !measureWithOwner(id.toString())) findings += RiskRules.measureRequired()
            if (name in RiskRules.NEEDS_RESIDUAL && !residualAssessed(id.toString())) findings += RiskRules.residualRequired()
            findings
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

    private suspend fun measureWithOwner(risk: String): Boolean =
        readAs(Risks.MEASURE) { records.count(Risks.MEASURE, mapOf(Risks.SOCIAL_RISK to risk), listOf(Criteria.filled(Risks.OWNER))) } > 0

    private suspend fun residualAssessed(risk: String): Boolean =
        readAs(Risks.ASSESSMENT) { records.count(Risks.ASSESSMENT, mapOf(Risks.SOCIAL_RISK to risk, RESIDUAL to "true")) } > 0

    companion object {
        const val RESIDUAL = "residual"
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `RiskWriteGuardTest` con 6 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/Criteria.kt src/main/kotlin/sgspe/risk/RiskWriteGuard.kt src/test/kotlin/sgspe/risk/RiskWriteGuardTest.kt
git commit -F - <<'MSG'
feat: guarda de WF-F para mitigar, monitorear y cerrar riesgos

RiskWriteGuard exige probabilidad, impacto y responsable al evaluar, la decisión de tratamiento y
una medida con responsable al mitigar, una revisión residual al monitorear y el motivo al cerrar
(RN-RSK-003), también en la consulta en seco; y una cadencia propia de 1 a 365 días.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** la cubren `SocialRiskWorkflowApiTest` (Tarea 4) y `RiskAssessmentsApiTest`
(Tarea 3).

---

### Tarea 3: El endpoint de revisiones de riesgo

**Files:**
- Create: `src/main/kotlin/sgspe/risk/RiskAssessments.kt`
- Modify: `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt` (`social_risk.assess`)
- Test: `src/test/kotlin/sgspe/risk/AssessmentPlannerTest.kt`, `src/test/kotlin/sgspe/risk/RiskAssessmentsApiTest.kt`

**Interfaces:**
- Consumes: `Risks`, `RiskLevels`, `RiskThresholds`, `RiskReview` (Tarea 1); `RiskWriteGuard` (Tarea 2, en la
  transición `assess`); `Idempotency`, `CommandAnswer`, `IdempotencyKey`, `Transactions.locked`, `TransitionRoles`,
  `Permissions`, `StrictBody`, `StrictQuery`, `Records` (F3).
- Produces: `data class RiskAssessmentResult(assessment, risk)`; `class RiskAssessmentInput` (`parse(body, today)`,
  `score`); `data class AssessmentRow(id, date, residual, score, level, createdAt)` con `ORDER` y `of(record)`;
  `class AssessmentPlan(stored, current, lastDate)`; `object AssessmentPlanner.plan(input, history, thresholds)`; el
  servicio `RiskAssessments.register(riskId, body, reason)`; `POST /api/sgspe/social-risks/{id}/assessments` →
  `201 { assessment, risk }`; `CommandTransitionGuard.COMMANDS["social_risk"]["assess"]`.

El contrato de `api.md` §4.7 y la especificación §3.6, en una transacción bajo el candado del riesgo: valida; calcula
puntaje, nivel y tendencia; crea la revisión con quien revisa; escribe los valores vigentes o los residuales,
`next_review_date` y `review_status` (decisión 4) con la marca; y en la primera aplica `assess`. La escritura de
`risk_level` es la que dispara AUT-11 (decisión 1). `assess` entra en `CommandTransitionGuard` con su endpoint (decisión 8
del plan 4.2a).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/risk/AssessmentPlannerTest.kt`:

```kotlin
package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.time.Instant
import java.time.LocalDate

// el cuerpo y el plan de una revisión de riesgo (api.md §4.7, ASM-099), sin base: escalas de 1 a 5, fecha no futura, orden por
// tipo, la primera no residual, nivel con los umbrales del proyecto, tendencia y la fecha de la que corre la cadencia
class AssessmentPlannerTest {
    private val today = LocalDate.parse("2026-10-09")
    private val defaults = RiskThresholds(5, 10, 15)

    private fun input(
        probability: Any? = 3,
        impact: Any? = 4,
        date: String? = "2026-10-07",
        residual: Any? = null
    ) = RiskAssessmentInput.parse(
        mapOf("attributes" to mapOf("assessment_date" to date, "probability" to probability, "impact" to impact, "residual" to residual)),
        today
    )

    private fun row(
        date: String,
        score: Int,
        residual: Boolean = false,
        created: String = "${date}T15:00:00Z"
    ) = AssessmentRow(date, LocalDate.parse(date), residual, score, defaults.level(score), Instant.parse(created))

    private fun code(error: WasichaiException) = error.violations.single().message to error.violations.single().field

    @Test
    fun `probabilidad e impacto son enteros de 1 a 5 y la fecha no es futura`() {
        assertEquals(12, input().score)
        assertEquals("OUT_OF_RANGE" to "attributes.probability", code(assertThrows<WasichaiException> { input(probability = 6) }))
        assertEquals("OUT_OF_RANGE" to "attributes.impact", code(assertThrows<WasichaiException> { input(impact = 2.5) }))
        assertEquals("OUT_OF_RANGE" to "attributes.probability", code(assertThrows<WasichaiException> { input(probability = "3") }))
        assertEquals("OUT_OF_RANGE" to "attributes.assessment_date", code(assertThrows<WasichaiException> { input(date = "2026-10-10") }))
        assertEquals("FIELD_REQUIRED" to "attributes.impact", code(assertThrows<WasichaiException> { input(impact = null) }))
        assertThrows<ValidationException> { input(residual = "si") }
        // una propiedad desconocida es un 400 que la nombra
        val unknown = assertThrows<ValidationException> { RiskAssessmentInput.parse(mapOf("attributes" to mapOf("score" to 12)), today) }
        assertEquals("attributes.score", unknown.violations.single().field)
    }

    @Test
    fun `la primera revision calcula nivel y no tiene tendencia`() {
        val plan = AssessmentPlanner.plan(input(), emptyList(), defaults)
        assertEquals("ALTO", plan.stored["risk_level"])
        assertEquals(12, plan.stored["score"])
        assertNull(plan.stored["trend"])
        assertEquals(mapOf("probability" to 3, "impact" to 4, "score" to 12, "risk_level" to "ALTO"), plan.current)
        assertEquals(LocalDate.parse("2026-10-07"), plan.lastDate)
    }

    @Test
    fun `la primera revision no es residual`() {
        val error = assertThrows<WasichaiException> { AssessmentPlanner.plan(input(residual = true), emptyList(), defaults) }
        assertEquals(409, error.status.value())
        assertEquals("FIRST_ASSESSMENT_NOT_RESIDUAL" to "attributes.residual", code(error))
    }

    @Test
    fun `la tendencia y el orden son por tipo, y la cadencia corre desde la ultima revision de cualquiera`() {
        val history = listOf(row("2026-09-30", 9), row("2026-10-08", 4, residual = true))
        val up = AssessmentPlanner.plan(input(date = "2026-10-07"), history, defaults)
        assertEquals("AUMENTA", up.stored["trend"])
        assertEquals(LocalDate.parse("2026-10-08"), up.lastDate)
        val residual = AssessmentPlanner.plan(input(probability = 2, impact = 2, date = "2026-10-09", residual = true), history, defaults)
        assertEquals("SE MANTIENE", residual.stored["trend"])
        assertEquals(mapOf("residual_probability" to 2, "residual_impact" to 2, "residual_score" to 4, "residual_level" to "BAJO"), residual.current)
        val late = assertThrows<WasichaiException> { AssessmentPlanner.plan(input(date = "2026-09-29"), history, defaults) }
        assertEquals("ASSESSMENT_OUT_OF_ORDER" to "attributes.assessment_date", code(late))
        // a igual fecha que la última de su tipo pasa: se corrige con otra revisión del mismo día
        assertEquals("DISMINUYE", AssessmentPlanner.plan(input(probability = 2, impact = 2, date = "2026-09-30"), history, defaults).stored["trend"])
    }
}
```

Crear `src/test/kotlin/sgspe/risk/RiskAssessmentsApiTest.kt`:

```kotlin
package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// el endpoint de revisiones de riesgo (api.md §4.7, ASM-099): la primera revisión lleva el riesgo a assessed con assess, que por
// la API genérica es COMMAND_ONLY; las siguientes actualizan los valores vigentes o los residuales, la tendencia y la próxima
// revisión; los errores con su código; Idempotency-Key; y la escritura de risk_level dispara AUT-11
class RiskAssessmentsApiTest : F4ApiTest() {
    private val reviewer by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun assess(
        risk: String,
        attributes: Map<String, Any?>,
        account: Account = reviewer,
        headers: Map<String, String> = emptyMap()
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/social-risks/$risk/assessments",
            mapOf("attributes" to attributes),
            account.token,
            withReason("revisión semanal") + headers
        )

    private fun review(
        probability: Int,
        impact: Int,
        date: String,
        residual: Boolean = false
    ) = mapOf("assessment_date" to date, "probability" to probability, "impact" to impact, "residual" to residual)

    private fun assessments(risk: String): List<JsonNode> =
        tree(send("GET", "/api/objects/risk_assessment/records?social_risk=$risk", null, HttpStatus.OK))["content"].values().toList()

    @Test
    fun `la primera revision evalua el riesgo y la siguiente actualiza sus valores`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject(), owner = owner, attributes = mapOf("review_every_days" to 7))
        val first = assess(risk, review(3, 4, "2026-01-05"))
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val body = tree(first.body)
        assertEquals("ALTO", body["assessment"]["attributes"]["risk_level"].asString())
        assertEquals(12, body["assessment"]["attributes"]["score"].asInt())
        assertEquals(reviewer.email, body["assessment"]["attributes"]["assessor_user_email"].asString())
        val after = record("social_risk", risk)
        assertEquals("assessed", after["state"].asString())
        assertEquals("ALTO", after["attributes"]["risk_level"].asString())
        assertEquals("2026-01-12", after["attributes"]["next_review_date"].asString())
        assertEquals("REVISION VENCIDA", after["attributes"]["review_status"].asString())
        assertTrue(
            first.headers.location
                .toString()
                .endsWith("/api/objects/risk_assessment/records/${body["assessment"]["id"].asString()}")
        )
        val second = tree(assess(risk, review(4, 4, "2026-01-12")).body)
        assertEquals("AUMENTA", second["assessment"]["attributes"]["trend"].asString())
        assertEquals("CRITICO", second["risk"]["attributes"]["risk_level"].asString())
        val residual = tree(assess(risk, review(2, 2, "2026-01-13", residual = true)).body)
        assertEquals("BAJO", residual["risk"]["attributes"]["residual_level"].asString())
        assertEquals("CRITICO", residual["risk"]["attributes"]["risk_level"].asString())
        assertEquals("2026-01-20", residual["risk"]["attributes"]["next_review_date"].asString())
    }

    @Test
    fun `una revision fuera de orden, una primera residual o un riesgo cerrado responden 409`() {
        val risk = socialRisk(testProject())
        val residual = assess(risk, review(2, 2, "2026-01-05", residual = true))
        assertEquals(HttpStatus.CONFLICT, residual.status, residual.body)
        assertEquals("FIRST_ASSESSMENT_NOT_RESIDUAL", code(residual))
        assertEquals(HttpStatus.CREATED, assess(risk, review(3, 3, "2026-01-10")).status)
        val late = assess(risk, review(3, 3, "2026-01-09"))
        assertEquals("ASSESSMENT_OUT_OF_ORDER", code(late))
        assertEquals(listOf("attributes.assessment_date"), problemFields(late.body))
        move("social_risk", risk, "close_assessed", reviewer.token, "el riesgo dejó de existir")
        val closed = assess(risk, review(3, 3, "2026-01-11"))
        assertEquals(HttpStatus.CONFLICT, closed.status, closed.body)
        assertEquals("STATE_MISMATCH", code(closed))
        assertEquals(1, assessments(risk).size)
    }

    @Test
    fun `una escala fuera de 1 a 5 o una fecha futura son OUT_OF_RANGE y no escriben nada`() {
        val risk = socialRisk(testProject())
        val big = assess(risk, review(6, 3, "2026-01-05"))
        assertEquals(HttpStatus.BAD_REQUEST, big.status, big.body)
        assertEquals("OUT_OF_RANGE", code(big))
        assertEquals(listOf("attributes.probability"), problemFields(big.body))
        assertEquals("OUT_OF_RANGE", code(assess(risk, review(3, 3, "2099-01-05"))))
        assertEquals(0, assessments(risk).size)
        assertEquals("identified", state("social_risk", risk))
    }

    @Test
    fun `sin responsable la primera revision no se guarda, porque assess lo exige`() {
        val risk = socialRisk(testProject(), attributes = mapOf("owner_user_id" to null, "owner_user_email" to null))
        val response = assess(risk, review(3, 3, "2026-01-05"))
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("FIELD_REQUIRED", code(response))
        assertEquals(listOf("owner_user_id"), problemFields(response.body))
        // la transacción se revierte entera: ni la revisión ni los valores vigentes
        assertEquals(0, assessments(risk).size)
        assertTrue(record("social_risk", risk)["attributes"]["risk_level"].let { it == null || it.isNull })
    }

    @Test
    fun `assess por la API generica es COMMAND_ONLY y quien no evalua recibe 403`() {
        val risk = socialRisk(testProject())
        val generic = transition("social_risk", risk, "assess", reviewer.token, "evaluar")
        assertEquals(HttpStatus.FORBIDDEN, generic.status, generic.body)
        assertEquals("COMMAND_ONLY", code(generic))
        val social = member("SOCIAL_MANAGER")
        assertEquals(HttpStatus.FORBIDDEN, assess(risk, review(3, 3, "2026-01-05"), social).status)
        assertEquals("identified", state("social_risk", risk))
    }

    @Test
    fun `un reenvio con la misma clave devuelve la misma respuesta y no revisa dos veces`() {
        val risk = socialRisk(testProject())
        val key = mapOf("Idempotency-Key" to uniqueName("revision-"))
        val first = assess(risk, review(3, 3, "2026-01-05"), headers = key)
        val again = assess(risk, review(3, 3, "2026-01-05"), headers = key)
        assertEquals(HttpStatus.CREATED, again.status, again.body)
        assertEquals(first.body, again.body)
        assertEquals("true", again.headers.getFirst("Idempotent-Replayed"))
        assertEquals(1, assessments(risk).size)
    }

    // workflows.md «Pruebas de trabajos, listeners y automatizaciones»: la escritura de risk_level del endpoint dispara AUT-11
    @Test
    fun `la revision que lleva el riesgo a CRITICO dispara AUT-11 para su responsable`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        assertEquals(HttpStatus.CREATED, assess(risk, review(5, 4, "2026-01-05")).status)
        assertEquals("SUCCEEDED", awaitRun("social_risk", "aut-11-critical-risk-task", risk)["status"].asString())
        val code = record("social_risk", risk)["attributes"]["code"].asString()
        val tasks = tree(send("GET", "/api/objects/task/records?social_risk=$risk", null, HttpStatus.OK))["content"].values()
        val critical = tasks.single { it["attributes"]["title"].asString() == "Revisar el riesgo crítico $code" }
        assertEquals(owner.id, critical["attributes"]["assignee_user_id"].asString())
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.risk.AssessmentPlannerTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'RiskAssessmentInput'`, `'AssessmentRow'` y
`'AssessmentPlanner'`).

- [ ] **Paso 3: El endpoint y la transición de comando**

Crear `src/main/kotlin/sgspe/risk/RiskAssessments.kt`:

```kotlin
package sgspe.risk

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
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.TransitionRoles
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RiskAssessmentResult(
    val assessment: RecordResponse,
    val risk: RecordResponse
)

// lo que manda el cliente en `attributes`, validado (api.md §4.7): probabilidad e impacto enteros de 1 a 5, la fecha no futura
// en Lima, residual verdadero o falso (falso si no viene) y las notas. lo calculado (puntaje, nivel, tendencia) no se manda
class RiskAssessmentInput(
    val date: LocalDate,
    val probability: Int,
    val impact: Int,
    val residual: Boolean,
    val notes: String?
) {
    val score: Int get() = probability * impact

    companion object {
        val ALLOWED: Set<String> = setOf("assessment_date", "probability", "impact", "residual", "notes")

        fun parse(
            body: Map<String, Any?>,
            today: LocalDate
        ): RiskAssessmentInput {
            StrictBody.requireOnly(body, setOf("attributes"))
            val attributes = StrictBody.objectOf(body, "attributes")
            StrictBody.requireOnly(attributes, ALLOWED, "attributes.")
            for (name in listOf("assessment_date", "probability", "impact")) {
                if (Values.text(attributes[name]) == null) {
                    throw Finding("FIELD_REQUIRED", "La revisión exige $name", "attributes.$name", status = HttpStatus.BAD_REQUEST).toCodedException()
                }
            }
            val date =
                runCatching { LocalDate.parse(attributes["assessment_date"].toString()) }.getOrNull()
                    ?: throw outOfRange("assessment_date", "debe ser una fecha AAAA-MM-DD")
            if (date.isAfter(today)) throw outOfRange("assessment_date", "no puede ser futura")
            val residual =
                when (val raw = attributes["residual"]) {
                    null -> false
                    is Boolean -> raw
                    else -> throw ValidationException("Revisión inválida", "attributes.residual", "debe ser verdadero o falso")
                }
            return RiskAssessmentInput(
                date,
                scale(attributes["probability"], "probability"),
                scale(attributes["impact"], "impact"),
                residual,
                Values.text(attributes["notes"])
            )
        }

        // un número de json entero de 1 a 5: un texto como "4" no es una escala
        private fun scale(
            value: Any?,
            name: String
        ): Int {
            val number = (value as? Number)?.let(Values::decimal)
            if (number == null || number.stripTrailingZeros().scale() > 0 || number < BigDecimal.ONE || number > BigDecimal(5)) {
                throw outOfRange(name, "debe ser un entero de 1 a 5")
            }
            return number.toInt()
        }

        private fun outOfRange(
            name: String,
            detail: String
        ) = Finding("OUT_OF_RANGE", "$name $detail", "attributes.$name", status = HttpStatus.BAD_REQUEST).toCodedException()
    }
}

// una revisión guardada, como la lee el plan de la siguiente
data class AssessmentRow(
    val id: String,
    val date: LocalDate,
    val residual: Boolean,
    val score: Int,
    val level: String?,
    val createdAt: Instant?
) {
    companion object {
        // la más reciente: por fecha y, a igual fecha, por creación
        val ORDER: Comparator<AssessmentRow> = compareBy<AssessmentRow>({ it.date }, { it.createdAt ?: Instant.MIN })

        fun of(record: RecordResponse): AssessmentRow? {
            val a = record.attributes
            val date = Values.text(a["assessment_date"])?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return AssessmentRow(
                record.id,
                date,
                Values.bool(a["residual"]) == true,
                Values.decimal(a[Risks.SCORE])?.toInt() ?: 0,
                Values.text(a[Risks.LEVEL]),
                record.createdAt
            )
        }
    }
}

// lo que escribe una revisión: la risk_assessment (`stored`), los valores vigentes o residuales del riesgo (`current`) y la
// fecha de la última revisión de cualquier tipo, de la que corre la cadencia
class AssessmentPlan(
    val stored: Map<String, Any?>,
    val current: Map<String, Any?>,
    val lastDate: LocalDate
)

// el plan de una revisión, puro (api.md §4.7, ASM-099): las revisiones de un tipo, residual o no, van en orden de fecha (409
// ASSESSMENT_OUT_OF_ORDER); la primera no es residual (409 FIRST_ASSESSMENT_NOT_RESIDUAL); el nivel sale de los umbrales del
// proyecto y la tendencia del puntaje de la revisión anterior del mismo tipo
object AssessmentPlanner {
    fun plan(
        input: RiskAssessmentInput,
        history: List<AssessmentRow>,
        thresholds: RiskThresholds
    ): AssessmentPlan {
        if (input.residual && history.none { !it.residual }) {
            throw Finding(
                "FIRST_ASSESSMENT_NOT_RESIDUAL",
                "La primera revisión de un riesgo no es residual: evalúe primero el riesgo inherente",
                "attributes.residual"
            ).toCodedException()
        }
        val previous = history.filter { it.residual == input.residual }.maxWithOrNull(AssessmentRow.ORDER)
        if (previous != null && input.date.isBefore(previous.date)) {
            throw Finding(
                "ASSESSMENT_OUT_OF_ORDER",
                "Ya hay una revisión ${if (input.residual) "residual" else "no residual"} del ${previous.date}: una revisión no es anterior a la última de su tipo",
                "attributes.assessment_date"
            ).toCodedException()
        }
        val level = thresholds.level(input.score)
        val stored =
            mapOf(
                "assessment_date" to input.date.toString(),
                "probability" to input.probability,
                "impact" to input.impact,
                Risks.SCORE to input.score,
                Risks.LEVEL to level,
                "residual" to input.residual,
                "trend" to RiskLevels.trend(previous?.score, input.score),
                "notes" to input.notes
            )
        val current =
            if (input.residual) {
                mapOf(
                    "residual_probability" to input.probability,
                    "residual_impact" to input.impact,
                    "residual_score" to input.score,
                    "residual_level" to level
                )
            } else {
                mapOf("probability" to input.probability, "impact" to input.impact, Risks.SCORE to input.score, Risks.LEVEL to level)
            }
        val last = (history.map { it.date } + input.date).max()
        return AssessmentPlan(stored, current, last)
    }
}

// las revisiones de riesgo (api.md §4.7, RF-RSK-001, ASM-099): en una transacción y bajo el candado del riesgo, valida, calcula
// puntaje, nivel y tendencia, crea la risk_assessment (appendOnly y apiOnly: este endpoint es su única vía) con quien revisa,
// escribe los valores vigentes o los residuales, next_review_date (última revisión + cadencia) y review_status con la marca, y
// en la primera revisión aplica assess, una transición de comando. todo como quien llama, con el motivo de la petición. la
// escritura de risk_level dispara AUT-11 (CRITICO) y la revisión, RiskNoticeListener (ALTO o CRITICO)
@Service
class RiskAssessments(
    private val records: Records,
    private val transactions: Transactions,
    private val permissions: Permissions,
    private val catalog: ModelCatalog,
    private val callers: Callers,
    private val clock: Clock
) {
    suspend fun register(
        riskId: UUID,
        body: Map<String, Any?>,
        reason: String?
    ): RiskAssessmentResult {
        val today = today()
        val input = RiskAssessmentInput.parse(body, today)
        if (reason.isNullOrBlank()) throw ValidationException("Una revisión de riesgo exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Revisar un riesgo", Actions.CREATE to Risks.ASSESSMENT, Actions.UPDATE to Risks.SOCIAL_RISK)
        return transactions.locked("${Risks.SOCIAL_RISK}:$riskId") {
            val risk = records.get(Risks.SOCIAL_RISK, riskId)
            if (risk.state == Risks.CLOSED) {
                throw Finding("STATE_MISMATCH", "Un riesgo cerrado no se revisa", "state").toCodedException()
            }
            val first = risk.state == Risks.IDENTIFIED
            if (first) TransitionRoles.require(catalog, user, Risks.SOCIAL_RISK, listOf(Risks.ASSESS))
            val project = Records.uuid(risk.attributes["project"])?.let { records.find("project", it)?.attributes }.orEmpty()
            val thresholds = RiskLevels.thresholds(project)
            if (!thresholds.valid()) {
                throw Finding(
                    "RISK_THRESHOLDS_INVALID",
                    "Los umbrales de nivel del proyecto no son válidos (de 1 a 25 y crecientes): corríjalos antes de revisar",
                    "project"
                ).toCodedException()
            }
            val history =
                records
                    .all(Risks.ASSESSMENT, filters = mapOf(Risks.SOCIAL_RISK to riskId.toString()), max = MAX_HISTORY, field = Risks.SOCIAL_RISK)
                    .mapNotNull(AssessmentRow::of)
            val plan = AssessmentPlanner.plan(input, history, thresholds)
            val assessment =
                records.create(
                    Risks.ASSESSMENT,
                    plan.stored +
                        mapOf(Risks.SOCIAL_RISK to riskId.toString(), "assessor_user_id" to user.userId.toString(), "assessor_user_email" to user.email),
                    reason
                )
            val next = RiskReview.next(plan.lastDate, RiskLevels.cadence(risk.attributes, project))
            val state = if (first) Risks.ASSESSED else risk.state
            val changes =
                plan.current +
                    mapOf(Risks.NEXT_REVIEW to next?.toString(), Risks.REVIEW_STATUS to if (state in Risks.REVIEWED) RiskReview.status(next, today) else null)
            // solo lo que cambia (P11)
            val changed = changes.filter { (name, value) -> !Values.same(value, risk.attributes[name]) }
            if (changed.isNotEmpty()) records.patch(Risks.SOCIAL_RISK, riskId, changed, reason)
            if (first) records.transition(Risks.SOCIAL_RISK, riskId, Risks.ASSESS, reason)
            RiskAssessmentResult(assessment, records.get(Risks.SOCIAL_RISK, riskId))
        }
    }

    private fun today(): LocalDate = LocalDate.now(clock.withZone(LIMA))

    companion object {
        // las revisiones de un riesgo que se leen para ordenar la nueva: una por semana son unas 520 en diez años
        const val MAX_HISTORY = 5_000
    }
}

// api.md §4.7: POST /api/sgspe/social-risks/{id}/assessments, con Idempotency-Key: un reenvío con la misma clave devuelve la
// respuesta de la primera revisión sin revisar otra vez. el ETag es el updatedAt del riesgo
@RestController
@RequestMapping("/api/sgspe/social-risks/{id}/assessments")
class RiskAssessmentController(
    private val assessments: RiskAssessments,
    private val idempotency: Idempotency
) {
    @PostMapping
    suspend fun register(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            val result = assessments.register(id, body, ChangeReason.fromHeader(reason))
            CommandAnswer(HttpStatus.CREATED, result, result.risk.updatedAt, "/api/objects/${Risks.ASSESSMENT}/records/${result.assessment.id}")
        }
    }
}
```

En `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, reemplazar:

```kotlin
// las transiciones de comando solo llegan desde su endpoint, que escribe varios registros en una transacción y lleva la
// marca SgspeWrite (workflows.md, «Transiciones de comando»). por la API genérica de workflow, sin la marca: 403, como la
// puerta cerrada de un objeto apiOnly. las de F3 y F4 las agregan sus fases a COMMANDS (assess de WF-F, con su endpoint, en el
// plan 4.2b)
@Component
@Order(GuardOrder.COMMAND)
```

por:

```kotlin
// las transiciones de comando solo llegan desde su endpoint, que escribe varios registros en una transacción y lleva la
// marca SgspeWrite (workflows.md, «Transiciones de comando»). por la API genérica de workflow, sin la marca: 403, como la
// puerta cerrada de un objeto apiOnly. las de F3 y F4 las agregan sus fases a COMMANDS
@Component
@Order(GuardOrder.COMMAND)
```

En `src/main/kotlin/sgspe/common/CommandTransitionGuard.kt`, reemplazar:

```kotlin
                        "accept" to "POST /api/sgspe/stakeholder-requests/{id}/accept",
                        "close_accepted" to "el cierre del compromiso creado (RequestClosureListener)"
                    )
            )
    }
```

por:

```kotlin
                        "accept" to "POST /api/sgspe/stakeholder-requests/{id}/accept",
                        "close_accepted" to "el cierre del compromiso creado (RequestClosureListener)"
                    ),
                // F4: la primera revisión de un riesgo (plan 4.2b)
                "social_risk" to mapOf("assess" to "POST /api/sgspe/social-risks/{id}/assessments")
            )
    }
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `AssessmentPlannerTest` con 4 pruebas y `GuardTablesCatalogTest` (la transición de comando
existe) en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/risk/RiskAssessments.kt src/main/kotlin/sgspe/common/CommandTransitionGuard.kt \
  src/test/kotlin/sgspe/risk/AssessmentPlannerTest.kt src/test/kotlin/sgspe/risk/RiskAssessmentsApiTest.kt
git commit -F - <<'MSG'
feat: endpoint de revisiones de riesgo con assess como comando

POST /api/sgspe/social-risks/{id}/assessments valida, calcula puntaje, nivel y tendencia, crea la
revisión inmutable, escribe los valores vigentes o residuales, la próxima revisión y su situación,
y en la primera aplica assess; honra Idempotency-Key (ASM-099).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.risk.RiskAssessmentsApiTest` (en la CI del PR, con Testcontainers).

---

### Tarea 4: WF-F en la matriz y su clase de workflow

**Files:**
- Modify: `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`
- Test: `src/test/kotlin/sgspe/risk/SocialRiskWorkflowApiTest.kt`

**Interfaces:**
- Consumes: el endpoint de revisiones (Tarea 3), `RiskWriteGuard` (Tarea 2), `F4ApiTest.socialRisk` y
  `mitigationMeasure` (plan 4.1).
- Produces: la matriz sin ninguna transición de WF-F en `DEFERRED` (`social_risk (9 de 9 transiciones)`);
  `WorkflowMatrixApiTest.prepareRisk` y `assessRisk` (privadas).

Decisión 13: la matriz aplica `assess` por el endpoint desde `identified`, como el portal, y prepara lo que piden mitigar
y monitorear; la clase por workflow cubre los casos propios de WF-F que la matriz no ve.

- [ ] **Paso 1: Escribir las pruebas**

Crear `src/test/kotlin/sgspe/risk/SocialRiskWorkflowApiTest.kt`:

```kotlin
package sgspe.risk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-F con RiskWriteGuard (workflows.md, especificación de F4 §3.6): los casos propios del riesgo que la matriz de workflows no
// cubre. la primera revisión aplica assess; mitigar exige la decisión MITIGAR y una medida con responsable; monitorear, una
// revisión residual; cerrar, el motivo. también en la consulta en seco; y la cadencia propia de 1 a 365 días
class SocialRiskWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun assessed(): String {
        val risk = socialRisk(testProject())
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 3, "impact" to 3)),
                manager.token,
                withReason("primera revisión")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        return risk
    }

    private fun residual(risk: String) {
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-06", "probability" to 2, "impact" to 2, "residual" to true)),
                manager.token,
                withReason("riesgo residual")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
    }

    private fun decide(
        risk: String,
        decision: String
    ) = assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("treatment_decision" to decision), manager.token, withReason("decisión")).status)

    @Test
    fun `la primera revision lleva el riesgo a assessed`() {
        val risk = assessed()
        assertEquals("assessed", state("social_risk", risk))
        val history = tree(send("GET", "/api/objects/social_risk/records/$risk/history", null, HttpStatus.OK))
        assertEquals(true, history.values().any { it["reason"]?.asString() == "primera revisión" })
    }

    @Test
    fun `mitigar exige la decision MITIGAR y una medida con responsable`() {
        val risk = assessed()
        decide(risk, "MONITOREAR")
        val wrong = transition("social_risk", risk, "decide_mitigation", manager.token, "mitigar")
        assertEquals(HttpStatus.BAD_REQUEST, wrong.status, wrong.body)
        assertEquals("FIELD_REQUIRED", code(wrong))
        assertEquals(listOf("treatment_decision"), problemFields(wrong.body))
        decide(risk, "MITIGAR")
        createRecord("mitigation_measure", mapOf("social_risk" to risk, "description" to "Medida sin responsable."))
        val ownerless = transition("social_risk", risk, "decide_mitigation", manager.token, "mitigar")
        assertEquals(HttpStatus.CONFLICT, ownerless.status, ownerless.body)
        assertEquals("MEASURE_OWNER_REQUIRED", code(ownerless))
        assertEquals(listOf("mitigation_measure"), problemFields(ownerless.body))
        mitigationMeasure(risk, manager)
        move("social_risk", risk, "decide_mitigation", manager.token, "mitigar")
        assertEquals("mitigating", state("social_risk", risk))
    }

    @Test
    fun `monitorear exige una revision residual`() {
        val risk = assessed()
        decide(risk, "ACEPTAR")
        val missing = transition("social_risk", risk, "decide_monitoring", manager.token, "monitorear")
        assertEquals(HttpStatus.CONFLICT, missing.status, missing.body)
        assertEquals("RESIDUAL_ASSESSMENT_REQUIRED", code(missing))
        residual(risk)
        move("social_risk", risk, "decide_monitoring", manager.token, "monitorear")
        assertEquals("monitoring", state("social_risk", risk))
    }

    @Test
    fun `cerrar exige el motivo de cierre`() {
        val risk = socialRisk(testProject(), attributes = mapOf("close_reason" to null))
        val response = transition("social_risk", risk, "close_identified", manager.token, "cerrar")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals(listOf("close_reason"), problemFields(response.body))
    }

    // decisión 16: la cadencia propia va de 1 a 365 días (RiskWriteGuard); vacía vale risk_review_days del proyecto
    @Test
    fun `una cadencia propia fuera de 1 a 365 dias es OUT_OF_RANGE y vacia vale la del proyecto`() {
        val risk = socialRisk(testProject())
        for (days in listOf(0, 366)) {
            val wrong = edit("social_risk", risk, mapOf("review_every_days" to days), manager.token, withReason("cadencia"))
            assertEquals(HttpStatus.BAD_REQUEST, wrong.status, wrong.body)
            assertEquals("OUT_OF_RANGE", code(wrong))
            assertEquals(listOf("review_every_days"), problemFields(wrong.body))
        }
        assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("review_every_days" to 365), manager.token, withReason("cadencia")).status)
        assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("review_every_days" to null), manager.token, withReason("cadencia")).status)
    }

    // POST /api/sgspe/transition-checks corre RiskWriteGuard en seco, como las demás guardas
    @Test
    fun `la consulta en seco dice que falta la medida`() {
        val risk = assessed()
        decide(risk, "MITIGAR")
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "social_risk", "recordId" to risk, "transition" to "decide_mitigation"),
                    HttpStatus.OK,
                    manager.token
                )
            )
        assertFalse(check["allowed"].asBoolean())
        assertEquals(listOf("MEASURE_OWNER_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
    }
}
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
    // lo que una transición pide antes de aplicarse y la API genérica no da: register_fulfillment de WF-D exige una entrega con
    // conformidad y, en un compromiso MATERIAL, su acta de entrega y su foto (D9), que el admin registra con el comando de
    // entregas del plan 3.2; close_unfulfilled de WF-E3, el compromiso creado al aceptar ya cancelado (plan 4.2a)
    private fun prepare(
        workflow: Workflow,
```

por:

```kotlin
    // lo que una transición pide antes de aplicarse y la API genérica no da: register_fulfillment de WF-D exige una entrega con
    // conformidad y, en un compromiso MATERIAL, su acta de entrega y su foto (D9), que el admin registra con el comando de
    // entregas del plan 3.2; close_unfulfilled de WF-E3, el compromiso creado al aceptar ya cancelado (plan 4.2a); y en WF-F
    // (plan 4.2b) la decisión de tratamiento, una medida con responsable y una revisión residual (RiskWriteGuard)
    private fun prepare(
        workflow: Workflow,
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        rule: Transition
    ) {
        if (workflow.key(rule) == "stakeholder_request.close_unfulfilled" && state(workflow.objectName, id) == rule.from) {
            move("commitment", record(workflow.objectName, id)["attributes"]["created_commitment"].asString(), "cancel_draft", reason = TEXT)
```

por:

```kotlin
        rule: Transition
    ) {
        if (workflow.objectName == "social_risk" && state(workflow.objectName, id) == rule.from) {
            prepareRisk(id, rule)
            return
        }
        if (workflow.key(rule) == "stakeholder_request.close_unfulfilled" && state(workflow.objectName, id) == rule.from) {
            move("commitment", record(workflow.objectName, id)["attributes"]["created_commitment"].asString(), "cancel_draft", reason = TEXT)
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
    }

    // una transición como la aplica el portal: accept de WF-K por su comando, que crea el compromiso en draft con los valores
```

por:

```kotlin
        assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
    }

    // lo que pide RiskWriteGuard (plan 4.2b), escrito por el admin: mitigar, la decisión MITIGAR y una medida con responsable;
    // monitorear, la decisión MONITOREAR y una revisión residual por el endpoint de revisiones
    private fun prepareRisk(
        id: String,
        rule: Transition
    ) {
        val decision =
            when (rule.name) {
                "decide_mitigation" -> "MITIGAR"
                "decide_monitoring" -> "MONITOREAR"
                else -> null
            }
        if (decision != null) {
            val edited = edit("social_risk", id, mapOf("treatment_decision" to decision), headers = withReason(TEXT))
            assertEquals(HttpStatus.OK, edited.status, edited.body)
        }
        if (rule.name in setOf("decide_mitigation", "resume_mitigation")) mitigationMeasure(id, accounts.getOrPut("\u0000owner") { userWithRole("READ_ONLY") })
        if (rule.name in setOf("decide_monitoring", "start_monitoring")) {
            val residual = assessRisk(id, token, residual = true)
            assertEquals(HttpStatus.CREATED, residual.status, residual.body)
        }
    }

    // una revisión de riesgo por su endpoint (POST /api/sgspe/social-risks/{id}/assessments): la primera aplica assess
    private fun assessRisk(
        id: String,
        token: String,
        residual: Boolean = false
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/social-risks/$id/assessments",
            mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 3, "residual" to residual)),
            token,
            withReason(TEXT)
        )

    // una transición como la aplica el portal: accept de WF-K por su comando, que crea el compromiso en draft con los valores
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
        if (rule.name.startsWith("close_reclassified") && state(workflow.objectName, id) == rule.from) return reclassify(workflow, id, who)
        if (workflow.key(rule) == "stakeholder_request.accept") return accept(id, who)
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
```

por:

```kotlin
        if (rule.name.startsWith("close_reclassified") && state(workflow.objectName, id) == rule.from) return reclassify(workflow, id, who)
        if (workflow.key(rule) == "stakeholder_request.accept") return accept(id, who)
        // assess de WF-F por el endpoint de revisiones, desde identified; fuera de él, por la API genérica, que responde 409
        if (workflow.key(rule) == "social_risk.assess" && state(workflow.objectName, id) == rule.from) return assessRisk(id, who.token)
        if (workflow.key(rule) != "commitment_candidate.accept") return transition(workflow.objectName, id, rule.name, who.token, TEXT)
        val reviewed = mapOf("code" to uniqueName("COM-"), "title" to "Mejora del canal de riego", "amount" to 15000, "currency" to "PEN")
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

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

por:

```kotlin
        // cuatro los escriben StakeholderAssessmentApiTest, StakeholderWorkflowGuardsApiTest y AreaOfInfluenceWorkflowGuardsApiTest.
        // las de F3 ya no se difieren (plan 3.2): approve_direct corre en un proyecto sin aprobación interna, prepare registra la
        // entrega que pide register_fulfillment y perform aplica accept por su comando. de F4 se difiere close_accepted de WF-E3,
        // que solo aplica RequestClosureListener al cerrar el compromiso (sus casos, en StakeholderRequestWorkflowApiTest). las
        // reclasificaciones y accept de WF-E3 ya no se difieren (plan 4.2a): perform las aplica por su comando y prepare cancela el
        // compromiso que pide close_unfulfilled; WF-F tampoco (plan 4.2b): perform aplica assess por el endpoint de revisiones y
        // prepare escribe lo que piden mitigar y monitorear
        val DEFERRED =
            setOf(
```

En `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt`, reemplazar:

```kotlin
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "stakeholder_request.close_accepted",
                "social_risk.assess",
                "social_risk.decide_mitigation",
                "social_risk.decide_monitoring",
                "social_risk.start_monitoring",
                "social_risk.resume_mitigation",
                "social_risk.close_assessed",
                "social_risk.close_mitigating",
                "social_risk.close_monitoring"
            )

```

por:

```kotlin
                "area_of_influence.approve",
                "area_of_influence.supersede",
                "stakeholder_request.close_accepted"
            )

```

- [ ] **Paso 2: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL` (las dos clases son de integración y solo se compilan aquí; `WorkflowMatrixApiTest` no tiene
pruebas unitarias).

- [ ] **Paso 3: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/risk/SocialRiskWorkflowApiTest.kt src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -F - <<'MSG'
test: WF-F en la matriz de workflows y su clase de casos propios

La matriz ya no difiere WF-F: aplica assess por el endpoint de revisiones y prepara la decisión, la
medida y la revisión residual; SocialRiskWorkflowApiTest prueba RiskWriteGuard por la API y en la
consulta en seco.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.risk.SocialRiskWorkflowApiTest` y `sgspe.workflow.WorkflowMatrixApiTest`
(esperado: `social_risk (9 de 9 transiciones)`, `early_warning (6 de 6)`, `incident (3 de 3)`).

---

### Tarea 5: El aviso de riesgo escalado por nivel

**Files:**
- Create: `src/main/kotlin/sgspe/notification/RiskNotices.kt`
- Modify: `src/main/kotlin/sgspe/notification/Notices.kt` (`NoticeTexts.riskEscalated`, `Notices.RISKS`)
- Test: `src/test/kotlin/sgspe/notification/RiskNoticeTest.kt`, `src/test/kotlin/sgspe/notification/RiskNoticesApiTest.kt`

**Interfaces:**
- Consumes: `AssessmentRow` (Tarea 3), `Risks` y `RiskLevels.escalates` (Tarea 1), `Assignments.recipients`, `Notices`
  (F3), `ModelCatalog.transition`.
- Produces: `object RiskNotice` (`key(risk, level, date)`, `previous(current, history)`, `latest(history)`); el bean
  `RiskNoticeListener` (`@Order(220)`); `Notices.RISKS = "sgspe.risks"`; `NoticeTexts.riskEscalated(label, level)`.

Decisión 2. La fuente es `sgspe.risks`; los destinatarios, el responsable y los roles de la transición de tratamiento que
sigue (`Risks.NEXT_TREATMENT`), salvo quien revisó. Los textos solo llevan el código y el nivel.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/notification/RiskNoticeTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.risk.AssessmentRow
import java.time.Instant
import java.time.LocalDate

// el aviso de riesgo escalado por nivel (workflows.md «Avisos»), sin base: su clave, la revisión anterior que resuelve y su texto
class RiskNoticeTest {
    private fun row(
        id: String,
        date: String,
        level: String,
        residual: Boolean = false
    ) = AssessmentRow(id, LocalDate.parse(date), residual, 0, level, Instant.parse("${date}T15:00:00Z"))

    @Test
    fun `la clave lleva el riesgo, el nivel y la fecha de la revision`() {
        assertEquals("risk_escalated:r1:ALTO:2026-10-07", RiskNotice.key("r1", "ALTO", LocalDate.parse("2026-10-07")))
    }

    @Test
    fun `la anterior es la ultima no residual que no es la nueva`() {
        val history = listOf(row("a", "2026-09-30", "MEDIO"), row("b", "2026-10-06", "BAJO", residual = true), row("c", "2026-10-07", "ALTO"))
        assertEquals("a", RiskNotice.previous("c", history)?.id)
        assertNull(RiskNotice.previous("a", listOf(row("a", "2026-09-30", "MEDIO"))))
        assertEquals("c", RiskNotice.latest(history)?.id)
    }

    @Test
    fun `el texto nombra el riesgo por su codigo y su nivel, sin texto libre`() {
        assertEquals(
            "Riesgo escalado a CRITICO: Riesgo RS-1" to "La última revisión dejó el riesgo en nivel CRITICO: decida su tratamiento.",
            NoticeTexts.riskEscalated("Riesgo RS-1", "CRITICO")
        )
        assertEquals("sgspe.risks", Notices.RISKS)
    }
}
```

Crear `src/test/kotlin/sgspe/notification/RiskNoticesApiTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el aviso de riesgo escalado por nivel en la bandeja (workflows.md «Avisos», RF-NTF-001): una revisión que sube el riesgo a
// ALTO lo publica al responsable y a los roles de decide_mitigation, no a quien revisó; la revisión siguiente lo resuelve, y
// cerrar el riesgo resuelve el de la última
class RiskNoticesApiTest : F4ApiTest() {
    private fun pendingFor(
        account: Account,
        risk: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, account.token))["content"]
            .values()
            .any { it["link"]?.get("recordId")?.asString() == risk && it["kind"].asString() == "ACTION" }

    private fun assess(
        risk: String,
        reviewer: Account,
        probability: Int,
        impact: Int,
        date: String
    ) {
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to date, "probability" to probability, "impact" to impact)),
                reviewer.token,
                withReason("revisión semanal")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
    }

    @Test
    fun `subir a ALTO avisa al responsable y a decide_mitigation, y la revision siguiente lo resuelve`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        assess(risk, reviewer, 3, 3, "2026-01-05")
        assertFalse(pendingFor(owner, risk), "MEDIO no escala")
        assess(risk, reviewer, 3, 4, "2026-01-12")
        assertTrue(pendingFor(owner, risk))
        assertTrue(pendingFor(social, risk))
        assertFalse(pendingFor(reviewer, risk))
        assess(risk, reviewer, 2, 2, "2026-01-19")
        assertFalse(pendingFor(owner, risk))
        assertFalse(pendingFor(social, risk))
    }

    @Test
    fun `cerrar el riesgo resuelve el aviso de su ultima revision`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        assess(risk, reviewer, 4, 4, "2026-01-05")
        assertTrue(pendingFor(owner, risk))
        move("social_risk", risk, "close_assessed", social.token, "el riesgo dejó de existir")
        assertFalse(pendingFor(owner, risk))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.notification.RiskNoticeTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'RiskNotice'`, `'riskEscalated'` y `'RISKS'`).

- [ ] **Paso 3: El listener, su texto y su fuente**

En `src/main/kotlin/sgspe/notification/Notices.kt`, reemplazar:

```kotlin
        "QCS confidencial fuera de plazo" to "Una QCS confidencial del proyecto $project superó su plazo. Pida el detalle al equipo de quejas."

    fun taskOverdue(
        label: String,
```

por:

```kotlin
        "QCS confidencial fuera de plazo" to "Una QCS confidencial del proyecto $project superó su plazo. Pida el detalle al equipo de quejas."

    // F4: un riesgo cuya revisión sube su nivel a ALTO o CRITICO (RiskNoticeListener). `label` es «Riesgo <código>»
    fun riskEscalated(
        label: String,
        level: String
    ): Pair<String, String> = "Riesgo escalado a $level: $label" to "La última revisión dejó el riesgo en nivel $level: decida su tratamiento."

    fun taskOverdue(
        label: String,
```

En `src/main/kotlin/sgspe/notification/Notices.kt`, reemplazar:

```kotlin
        const val DEADLINES = "sgspe.deadlines"

        // el borrador de un aviso a personas concretas, con su enlace al registro (la bandeja lo quita a quien no lee el objeto)
        fun draft(
```

por:

```kotlin
        const val DEADLINES = "sgspe.deadlines"

        // F4: los avisos de nivel de riesgo (RiskNoticeListener)
        const val RISKS = "sgspe.risks"

        // el borrador de un aviso a personas concretas, con su enlace al registro (la bandeja lo quita a quien no lee el objeto)
        fun draft(
```

Crear `src/main/kotlin/sgspe/notification/RiskNotices.kt`:

```kotlin
package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.common.Values
import sgspe.risk.AssessmentRow
import sgspe.risk.RiskLevels
import sgspe.risk.Risks
import sgspe.scope.Assignments
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import wasichai.notifications.NotificationKind
import wasichai.notifications.NotificationLink
import java.util.UUID

// el aviso de riesgo escalado por nivel, puro (workflows.md «Avisos», RF-NTF-001): su clave y qué hace una revisión con él
object RiskNotice {
    fun key(
        risk: String,
        level: String?,
        date: Any?
    ): String = "risk_escalated:$risk:$level:$date"

    // la revisión no residual anterior a `current` (la más reciente de las otras); null si es la primera
    fun previous(
        current: String,
        history: List<AssessmentRow>
    ): AssessmentRow? = history.filter { !it.residual && it.id != current }.maxWithOrNull(AssessmentRow.ORDER)

    // la última revisión no residual de un riesgo: la que deja abierto el aviso que su cierre resuelve
    fun latest(history: List<AssessmentRow>): AssessmentRow? = history.filter { !it.residual }.maxWithOrNull(AssessmentRow.ORDER)
}

// RiskNoticeListener (sgspe.notification, workflows.md «Listeners» y «Avisos»): una revisión no residual que sube el nivel del
// riesgo a ALTO o CRITICO (de uno menor, o de ALTO a CRITICO) publica risk_escalated:<riesgo>:<nivel>:<fecha de la revisión>
// en la fuente sgspe.risks, para el responsable del riesgo y los asignados vigentes con un rol de la transición de tratamiento
// que sigue, salvo quien revisó. toda revisión no residual resuelve el aviso de la anterior, y cerrar el riesgo resuelve el de
// la última. corre en la transacción del endpoint de revisiones o de la transición, como quien escribe; si no lee las
// revisiones, el riesgo o las asignaciones, lo registra y la escritura sigue
@Component
@Order(220)
class RiskNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments,
    private val records: Records,
    private val catalog: ModelCatalog
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        try {
            when {
                change.objectName == Risks.ASSESSMENT && change.kind == RecordChangeKind.CREATED -> assessed(change)
                change.objectName == Risks.SOCIAL_RISK && change.kind == RecordChangeKind.TRANSITIONED &&
                    change.transition?.let { catalog.transition(Risks.SOCIAL_RISK, it)?.to } == Risks.CLOSED -> closed(change)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForbiddenException) {
            log.error("RiskNoticeListener: quien escribió {} {} no lee lo que el aviso necesita; no hay aviso", change.objectName, change.recordId, e)
        }
    }

    private suspend fun assessed(change: RecordChange) {
        val after = change.after.orEmpty()
        if (Values.bool(after["residual"]) == true) return
        val risk = Values.text(after[Risks.SOCIAL_RISK]) ?: return
        val previous = RiskNotice.previous(change.recordId.toString(), history(risk))
        if (previous != null) notices.resolve(change.organizationId, Notices.RISKS, RiskNotice.key(risk, previous.level, previous.date))
        val level = Values.text(after[Risks.LEVEL])
        if (!RiskLevels.escalates(previous?.level, level)) return
        publish(change, risk, level!!, Values.text(after["assessment_date"]))
    }

    private suspend fun closed(change: RecordChange) {
        val latest = RiskNotice.latest(history(change.recordId.toString())) ?: return
        notices.resolve(change.organizationId, Notices.RISKS, RiskNotice.key(change.recordId.toString(), latest.level, latest.date))
    }

    private suspend fun history(risk: String): List<AssessmentRow> =
        records.all(Risks.ASSESSMENT, filters = mapOf(Risks.SOCIAL_RISK to risk)).mapNotNull(AssessmentRow::of)

    private suspend fun publish(
        change: RecordChange,
        risk: String,
        level: String,
        date: String?
    ) {
        val record = records.find(Risks.SOCIAL_RISK, UUID.fromString(risk)) ?: return
        val project = Records.uuid(record.attributes["project"]) ?: return
        val roles =
            Risks.NEXT_TREATMENT[record.state]
                ?.let { assignments.recipients(project, Risks.SOCIAL_RISK, it, change.userId) }
                .orEmpty()
                .map { it.userId }
        val owner = Records.uuid(record.attributes[Risks.OWNER])?.takeIf { it != change.userId }
        val users = (listOfNotNull(owner) + roles).distinct()
        if (users.isEmpty()) {
            log.warn("RiskNoticeListener: el riesgo {} no tiene responsable ni asignaciones vigentes que avisar; no hay aviso", risk)
            return
        }
        val label = "Riesgo ${Values.text(record.attributes["code"]) ?: risk}"
        notices.publish(
            change.organizationId,
            Notices.RISKS,
            RiskNotice.key(risk, level, date),
            NotificationKind.ACTION,
            NoticeTexts.riskEscalated(label, level),
            users,
            NotificationLink.Record(Risks.SOCIAL_RISK, UUID.fromString(risk))
        )
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `RiskNoticeTest` con 3 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/notification/Notices.kt src/main/kotlin/sgspe/notification/RiskNotices.kt \
  src/test/kotlin/sgspe/notification/RiskNoticeTest.kt src/test/kotlin/sgspe/notification/RiskNoticesApiTest.kt
git commit -F - <<'MSG'
feat: aviso risk_escalated cuando una revisión sube el nivel del riesgo

RiskNoticeListener publica risk_escalated al responsable y a los roles del tratamiento que sigue
cuando una revisión no residual lleva el riesgo a ALTO o CRITICO; la revisión siguiente y el cierre
lo resuelven (fuente sgspe.risks).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.notification.RiskNoticesApiTest`.

---

### Tarea 6: JOB-01 con la revisión de los riesgos

**Files:**
- Modify: `src/main/kotlin/sgspe/deadline/Deadlines.kt`, `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`
- Test: `src/test/kotlin/sgspe/deadline/DeadlinesRiskTest.kt`, `src/test/kotlin/sgspe/deadline/DeadlineJobRiskApiTest.kt`

**Interfaces:**
- Consumes: `Risks`, `RiskLevels.cadence`, `RiskReview` (Tarea 1); `Deadlines.Tracked` y `DeadlineJob` por objeto
  (plan 4.2a); `JobTasks.ensure`.
- Produces: `Deadlines.Tracked.review: Boolean`; el `Tracked` de `social_risk` (`review_status`, `next_review_date`,
  `Risks.REVIEWED`); `DeadlineJob.reviewKey(risk, next)` y `DeadlineJob.REVIEW_REASON`.

Un tenant sin el modelo de F4 sigue con F3: el `Tracked` de `social_risk` entra en `Organizations.having` como los demás
(plan 4.2a). JOB-01 mueve `next_review_date` con la cadencia efectiva (decisión 5), escribe `review_status` solo si cambia
y crea la tarea `risk_review:<id>:<fecha>` para el responsable al pasar a POR REVISAR o REVISION VENCIDA. Un riesgo no
escala con tareas de escalamiento: ya tiene su tarea de revisión (`workflows.md`, «Destinatarios por rol», punto 6).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/deadline/DeadlinesRiskTest.kt`:

```kotlin
package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.time.LocalDate

// la situación de revisión de los riesgos en JOB-01 (workflows.md «Plazos y escalamiento», ASM-099), sin base: se mide en
// assessed, mitigating y monitoring contra next_review_date, no escala con tareas de escalamiento y su tarea tiene su clave
class DeadlinesRiskTest {
    private val risk = Deadlines.TRACKED.first { it.objectName == "social_risk" }

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `la revision se mide en los estados revisados y vacia en los demas`() {
        val next = mapOf("next_review_date" to "2026-10-14")
        assertEquals("POR REVISAR", Deadlines.wanted(risk, "mitigating", next, date("2026-10-13"), 7))
        assertEquals("REVISION VENCIDA", Deadlines.wanted(risk, "assessed", next, date("2026-10-15"), 7))
        assertEquals("AL DIA", Deadlines.wanted(risk, "monitoring", next, date("2026-10-01"), 7))
        assertNull(Deadlines.wanted(risk, "identified", next, date("2026-10-15"), 7))
        assertNull(Deadlines.wanted(risk, "closed", next, date("2026-10-15"), 7))
    }

    @Test
    fun `un riesgo no crea tareas de escalamiento y su tarea de revision lleva su clave`() {
        assertFalse(risk.escalates)
        assertEquals("risk_review:r1:2026-10-14", DeadlineJob.reviewKey("r1", "2026-10-14"))
    }

    @Test
    fun `los estados y los campos de la revision existen en el modelo`() {
        val catalog = ModelCatalog.fromClasspath()
        assertEquals(emptySet<String>(), risk.states - catalog.states(risk.objectName).toSet())
        assertEquals(emptySet<String>(), setOf(risk.field, risk.dateField) - catalog.derivedFields(risk.objectName))
    }
}
```

Crear `src/test/kotlin/sgspe/deadline/DeadlineJobRiskApiTest.kt`:

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

// JOB-01 con los riesgos (workflows.md «Plazos y escalamiento» y «Pruebas de trabajos», ASM-099): la revisión vencida queda
// REVISION VENCIDA sin cambiar el estado, crea una sola tarea risk_review:<id>:<fecha> para el responsable, la cadencia nueva
// mueve la próxima revisión y un riesgo cerrado deja de medirse
class DeadlineJobRiskApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    private fun reviews(risk: String): List<JsonNode> =
        tree(send("GET", "/api/objects/task/records?social_risk=$risk", null, HttpStatus.OK))["content"]
            .values()
            .filter { it["attributes"]["generator_key"]?.asString()?.startsWith("risk_review:") == true }

    private fun blank(node: JsonNode?): Boolean = node == null || node.isNull || node.isMissingNode

    @Test
    fun `una revision vencida crea una sola tarea para el responsable y la cadencia nueva mueve la fecha`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        val assessed =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 2)),
                reviewer.token,
                withReason("primera revisión")
            )
        assertEquals(HttpStatus.CREATED, assessed.status, assessed.body)
        runBlocking { job.runOnce() }
        val after = record("social_risk", risk)
        assertEquals("REVISION VENCIDA", after["attributes"]["review_status"].asString())
        assertEquals("assessed", after["state"].asString())
        val code = after["attributes"]["code"].asString()
        val first = reviews(risk).single()
        assertEquals("risk_review:$risk:2026-01-12", first["attributes"]["generator_key"].asString())
        assertEquals("Revisar el riesgo $code", first["attributes"]["title"].asString())
        assertEquals(owner.id, first["attributes"]["assignee_user_id"].asString())
        assertEquals("2026-01-12", first["attributes"]["due_date"].asString())
        runBlocking { job.runOnce() }
        assertEquals(1, reviews(risk).size)
        assertEquals(HttpStatus.OK, edit("social_risk", risk, mapOf("review_every_days" to 30), owner.token, withReason("revisión mensual")).status)
        runBlocking { job.runOnce() }
        assertEquals("2026-02-04", record("social_risk", risk)["attributes"]["next_review_date"].asString())
        assertEquals(
            setOf("risk_review:$risk:2026-01-12", "risk_review:$risk:2026-02-04"),
            reviews(risk).map { it["attributes"]["generator_key"].asString() }.toSet()
        )
        val history = tree(send("GET", "/api/objects/social_risk/records/$risk/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString() == DeadlineJob.REASON })
    }

    @Test
    fun `un riesgo cerrado deja de medirse`() {
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val risk = socialRisk(testProject())
        exchange(
            "POST",
            "/api/sgspe/social-risks/$risk/assessments",
            mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 2)),
            reviewer.token,
            withReason("primera revisión")
        )
        move("social_risk", risk, "close_assessed", reviewer.token, "el riesgo dejó de existir")
        runBlocking { job.runOnce() }
        assertTrue(blank(record("social_risk", risk)["attributes"]["review_status"]))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.deadline.DeadlinesRiskTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'reviewKey'`).

- [ ] **Paso 3: El riesgo en `Deadlines` y su paso en JOB-01**

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
import sgspe.common.Attention
import sgspe.common.Values
import java.time.LocalDate
import java.time.temporal.ChronoUnit
```

por:

```kotlin
import sgspe.common.Attention
import sgspe.common.Values
import sgspe.risk.RiskReview
import sgspe.risk.Risks
import java.time.LocalDate
import java.time.temporal.ChronoUnit
```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
    // un objeto que JOB-01 sigue: su campo derivado, la fecha que mide y los estados en que se calcula. desde F4: la fecha que
    // mide un estado si no es la de siempre (el acuse de una queja en `received`), si POR VENCER se acota a la mitad del plazo
    // medido desde la recepción (una QCS, ASM-100) y si escala con tareas (los compromisos y, desde F4, las QCS)
    data class Tracked(
        val objectName: String,
```

por:

```kotlin
    // un objeto que JOB-01 sigue: su campo derivado, la fecha que mide y los estados en que se calcula. desde F4: la fecha que
    // mide un estado si no es la de siempre (el acuse de una queja en `received`), si POR VENCER se acota a la mitad del plazo
    // medido desde la recepción (una QCS, ASM-100) y si escala con tareas (los compromisos y, desde F4, las QCS). `review`: la
    // situación de revisión de un riesgo (AL DIA, POR REVISAR, REVISION VENCIDA, ASM-099), que no escala con tareas sino con
    // su tarea de revisión
    data class Tracked(
        val objectName: String,
```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
        val dateByState: Map<String, String> = emptyMap(),
        val halfFromReceipt: Boolean = false,
        val escalates: Boolean = false
    ) {
        fun dateFieldIn(state: String?): String = dateByState[state] ?: dateField
```

por:

```kotlin
        val dateByState: Map<String, String> = emptyMap(),
        val halfFromReceipt: Boolean = false,
        val escalates: Boolean = false,
        val review: Boolean = false
    ) {
        fun dateFieldIn(state: String?): String = dateByState[state] ?: dateField
```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin

    // F3: compromisos, tareas y acuerdos; F4 suma las quejas y las consultas (las solicitudes no tienen plazo en el MVP,
    // ASM-100) y el plan 4.2b, los riesgos
    val TRACKED =
        listOf(
```

por:

```kotlin

    // F3: compromisos, tareas y acuerdos; F4 suma las quejas y las consultas (las solicitudes no tienen plazo en el MVP,
    // ASM-100) y la revisión de los riesgos (ASM-099)
    val TRACKED =
        listOf(
```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
                escalates = true
            ),
            Tracked(Attention.INQUIRY, Attention.DEADLINE_STATUS, QcsDueDates.RESPONSE, setOf("received"), halfFromReceipt = true, escalates = true)
        )

```

por:

```kotlin
                escalates = true
            ),
            Tracked(Attention.INQUIRY, Attention.DEADLINE_STATUS, QcsDueDates.RESPONSE, setOf("received"), halfFromReceipt = true, escalates = true),
            Tracked(Risks.SOCIAL_RISK, Risks.REVIEW_STATUS, Risks.NEXT_REVIEW, Risks.REVIEWED, review = true)
        )

```

En `src/main/kotlin/sgspe/deadline/Deadlines.kt`, reemplazar:

```kotlin
        val date = due(tracked, state, attributes)
        if (tracked.validity) return validity(date, today, dueSoonDays)
        val soon =
            if (tracked.halfFromReceipt &&
```

por:

```kotlin
        val date = due(tracked, state, attributes)
        if (tracked.validity) return validity(date, today, dueSoonDays)
        if (tracked.review) return RiskReview.status(date, today)
        val soon =
            if (tracked.halfFromReceipt &&
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordResponse
import wasichai.core.platform.ClusterLock
```

por:

```kotlin
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.risk.RiskLevels
import sgspe.risk.RiskReview
import sgspe.risk.Risks
import wasichai.core.data.RecordResponse
import wasichai.core.platform.ClusterLock
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
// proyecto, solo si cambia (P11) y bajo el candado del registro, con el motivo que nombra al trabajo (P16); crea la tarea de
// escalamiento de los niveles 1 y 2 de un compromiso, una queja o una consulta vencidos (generator_key, sin asignado ni fecha:
// la toma uno de los avisados; su título solo lleva el código). nunca aplica transiciones (RN-WFL-001). el día de Lima se lee
// una vez por vuelta: todas las organizaciones se miden con la misma fecha. los avisos de plazo los publica DeadlineNotices,
// que lee lo que este trabajo escribió
@Component
class DeadlineJob(
```

por:

```kotlin
// proyecto, solo si cambia (P11) y bajo el candado del registro, con el motivo que nombra al trabajo (P16); crea la tarea de
// escalamiento de los niveles 1 y 2 de un compromiso, una queja o una consulta vencidos (generator_key, sin asignado ni fecha:
// la toma uno de los avisados; su título solo lleva el código). desde el plan 4.2b mide también la revisión de los riesgos
// (ASM-099): mueve next_review_date si cambió la cadencia (última revisión + review_every_days o risk_review_days), escribe
// review_status y crea la tarea risk_review:<id>:<next_review_date> para el responsable al pasar a POR REVISAR o REVISION
// VENCIDA. nunca aplica transiciones (RN-WFL-001). el día de Lima se lee una vez por vuelta: todas las organizaciones se miden
// con la misma fecha. los avisos de plazo los publica DeadlineNotices, que lee lo que este trabajo escribió
@Component
class DeadlineJob(
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
            // los registros cerrados que ya se vaciaron no se vuelven a leer cada hora
            val rows = records.all(tracked.objectName, criteria = listOf(Criteria.inStateOrFilled(tracked.states, tracked.field)))
            for (row in rows) {
                try {
                    val params = project(Values.text(row.attributes["project"]))
                    val outcome = write(tracked, row, today, ProjectParameters.integer(params, "due_soon_days"))
                    if (outcome.written) written++
                    // JobTasks.ensure va fuera de toda transacción: write ya cerró la suya (un único duplicado aborta la abierta)
                    if (tracked.escalates) created += escalate(tracked, row, outcome.locked, today, params)
                } catch (e: CancellationException) {
                    throw e
```

por:

```kotlin
            // los registros cerrados que ya se vaciaron no se vuelven a leer cada hora
            val rows = records.all(tracked.objectName, criteria = listOf(Criteria.inStateOrFilled(tracked.states, tracked.field)))
            // la última revisión de cada riesgo, de la que corre su cadencia: una lectura por organización
            val latest = if (tracked.review) latestAssessments(rows) else emptyMap()
            for (row in rows) {
                try {
                    val params = project(Values.text(row.attributes["project"]))
                    val outcome = write(tracked, row) { record -> desired(tracked, record, today, params, latest[record.id]) }
                    if (outcome.written) written++
                    // JobTasks.ensure va fuera de toda transacción: write ya cerró la suya (un único duplicado aborta la abierta)
                    if (tracked.escalates) created += escalate(tracked, row, outcome.locked, today, params)
                    if (tracked.review) created += reviewTask(row, outcome.locked)
                } catch (e: CancellationException) {
                    throw e
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
        tracked: Deadlines.Tracked,
        row: RecordResponse,
        today: LocalDate,
        dueSoonDays: Long
    ): Outcome {
        val wanted = Deadlines.wanted(tracked, row.state, row.attributes, today, dueSoonDays)
        if (Values.same(row.attributes[tracked.field], wanted)) return Outcome(false, null)
        val id = UUID.fromString(row.id)
        return transactions.locked("${tracked.objectName}:$id") {
            val current = records.find(tracked.objectName, id) ?: return@locked Outcome(false, null)
            val again = Deadlines.wanted(tracked, current.state, current.attributes, today, dueSoonDays)
            if (Values.same(current.attributes[tracked.field], again)) {
                Outcome(false, current)
            } else {
                Outcome(true, records.patch(tracked.objectName, id, mapOf(tracked.field to again), REASON))
            }
        }
    }

    // la fecha de vencimiento y el nivel alcanzado de un registro vencido en un estado con plazo; null si no escala
```

por:

```kotlin
        tracked: Deadlines.Tracked,
        row: RecordResponse,
        desired: (RecordResponse) -> Map<String, Any?>
    ): Outcome {
        if (changed(row, desired(row)).isEmpty()) return Outcome(false, null)
        val id = UUID.fromString(row.id)
        return transactions.locked("${tracked.objectName}:$id") {
            val current = records.find(tracked.objectName, id) ?: return@locked Outcome(false, null)
            val changes = changed(current, desired(current))
            if (changes.isEmpty()) Outcome(false, current) else Outcome(true, records.patch(tracked.objectName, id, changes, REASON))
        }
    }

    // lo que el registro debe tener: la situación de plazo o de vigencia y, en un riesgo, también su próxima revisión
    private fun desired(
        tracked: Deadlines.Tracked,
        record: RecordResponse,
        today: LocalDate,
        params: Map<String, Any?>,
        latest: LocalDate?
    ): Map<String, Any?> =
        if (tracked.review) {
            RiskReview.changes(record.state, record.attributes, latest, RiskLevels.cadence(record.attributes, params), today)
        } else {
            mapOf(tracked.field to Deadlines.wanted(tracked, record.state, record.attributes, today, ProjectParameters.integer(params, "due_soon_days")))
        }

    private fun changed(
        record: RecordResponse,
        wanted: Map<String, Any?>
    ): Map<String, Any?> = wanted.filter { (name, value) -> !Values.same(record.attributes[name], value) }

    // la fecha de la última revisión (residual o no) de cada riesgo revisado de `rows`, por id
    private suspend fun latestAssessments(rows: List<RecordResponse>): Map<String, LocalDate> =
        rows
            .filter { it.state in Risks.REVIEWED }
            .map { UUID.fromString(it.id) }
            .chunked(CHUNK)
            .flatMap { chunk -> records.all(Risks.ASSESSMENT, criteria = listOf(Criteria.anyOf(Risks.SOCIAL_RISK, chunk))) }
            .mapNotNull { a ->
                val risk = Values.text(a.attributes[Risks.SOCIAL_RISK]) ?: return@mapNotNull null
                val date = Values.text(a.attributes["assessment_date"])?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
                risk to date
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, dates) -> dates.max() }

    // la tarea de revisión de un riesgo POR REVISAR o con REVISION VENCIDA (ASM-099): una por riesgo y fecha, para su
    // responsable y con la fecha de la revisión. decide, como escalate, con el registro releído y no con la fila inicial
    private suspend fun reviewTask(
        row: RecordResponse,
        locked: RecordResponse?
    ): Int {
        if (locked == null && !reviewDue(row)) return 0
        val record = locked ?: records.find(Risks.SOCIAL_RISK, UUID.fromString(row.id)) ?: return 0
        if (!reviewDue(record)) return 0
        val next = Values.text(record.attributes[Risks.NEXT_REVIEW]) ?: return 0
        val code = Values.text(record.attributes["code"]) ?: record.id
        val owner = Values.text(record.attributes[Risks.OWNER])
        val email = Values.text(record.attributes[Risks.OWNER_EMAIL])
        val assignee = if (owner != null && email != null) mapOf("assignee_user_id" to owner, "assignee_user_email" to email) else emptyMap()
        val created =
            tasks.ensure(
                reviewKey(record.id, next),
                mapOf(
                    "title" to "Revisar el riesgo $code",
                    "description" to "La revisión del riesgo vence el $next.",
                    "project" to record.attributes["project"],
                    Risks.SOCIAL_RISK to record.id,
                    "due_date" to next
                ) + assignee,
                REVIEW_REASON
            )
        return if (created) 1 else 0
    }

    private fun reviewDue(record: RecordResponse): Boolean =
        record.state in Risks.REVIEWED && Values.text(record.attributes[Risks.REVIEW_STATUS]) in setOf(RiskReview.DUE, RiskReview.OVERDUE)

    // la fecha de vencimiento y el nivel alcanzado de un registro vencido en un estado con plazo; null si no escala
```

En `src/main/kotlin/sgspe/deadline/DeadlineJob.kt`, reemplazar:

```kotlin
        const val REASON = "situación de plazo ($NAME)"
        const val TASK_REASON = "tarea de escalamiento ($NAME)"

        fun escalationKey(
```

por:

```kotlin
        const val REASON = "situación de plazo ($NAME)"
        const val TASK_REASON = "tarea de escalamiento ($NAME)"
        const val REVIEW_REASON = "tarea de revisión de riesgo ($NAME)"
        private const val CHUNK = 500

        // la tarea de revisión de un riesgo (ASM-099): una por riesgo y próxima revisión
        fun reviewKey(
            risk: String,
            next: String
        ): String = "risk_review:$risk:$next"

        fun escalationKey(
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `DeadlinesRiskTest` con 3 pruebas, y `DeadlinesTest` y `DeadlinesQcsTest` sin cambios, en
verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/deadline/Deadlines.kt src/main/kotlin/sgspe/deadline/DeadlineJob.kt \
  src/test/kotlin/sgspe/deadline/DeadlinesRiskTest.kt src/test/kotlin/sgspe/deadline/DeadlineJobRiskApiTest.kt
git commit -F - <<'MSG'
feat: JOB-01 mide la revisión de los riesgos y crea su tarea

DeadlineJob mueve la próxima revisión con la cadencia efectiva, escribe review_status y crea una
sola tarea risk_review por riesgo y fecha para su responsable al pasar a POR REVISAR o REVISION
VENCIDA (RN-RSK-001, ASM-099).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.deadline.DeadlineJobRiskApiTest`, `sgspe.deadline.DeadlineJobApiTest` y
`sgspe.deadline.DeadlineJobQcsApiTest`.

---

### Tarea 7: Los avisos de la revisión vencida

**Files:**
- Modify: `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, `src/main/kotlin/sgspe/notification/Notices.kt`
  (`NoticeTexts.riskReviewOverdue`)
- Test: `src/test/kotlin/sgspe/deadline/RiskNoticePlanTest.kt`, `src/test/kotlin/sgspe/deadline/RiskReviewNoticesApiTest.kt`

**Interfaces:**
- Consumes: `Risks`, `RiskReview` (Tarea 1); el `review_status` que escribe JOB-01 (Tarea 6); `DeadlineNoticePlan`,
  `NoticeAudience`, `PlannedNotice` (planes 3.2 y 4.2a).
- Produces: `DeadlineNoticePlan.risk(id, label, state, due, level, owner): List<PlannedNotice>`; `TASK_REFERENCE` con
  `social_risk` (`decide_mitigation`) y `early_warning` (`confirm`); `NoticeTexts.riskReviewOverdue(label, due, level)`.

`workflows.md` («Avisos» y «Destinatarios por rol»): `risk_escalated:<id>:<nivel>:<next_review_date>` en la fuente de
plazos, nivel 0 al responsable, 1 a los roles de la transición de tratamiento que sigue y 2 a los de `approve_senior` de
WF-D. La situación se recalcula con la fecha antes de avisar (decisión 4). El texto nuevo va en otro punto de `Notices.kt`
que los de las Tareas 5 y 11.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/deadline/RiskNoticePlanTest.kt`:

```kotlin
package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.notification.NoticeTexts
import java.time.LocalDate
import java.util.UUID

// los avisos de la revisión vencida de un riesgo (workflows.md «Avisos» y «Destinatarios por rol», ASM-099), sin base
class RiskNoticePlanTest {
    private val due = LocalDate.parse("2026-10-14")

    @Test
    fun `cada nivel va al responsable, a la transicion de tratamiento que sigue y a approve_senior`() {
        val owner = UUID.randomUUID()
        val plan = DeadlineNoticePlan.risk("r1", "Riesgo RS-1", "mitigating", due, 2, owner)
        assertEquals((0..2).map { "risk_escalated:r1:$it:2026-10-14" }, plan.map { it.key })
        assertEquals(
            listOf(NoticeAudience.Person(owner), NoticeAudience.Roles("social_risk", "start_monitoring"), DeadlineNoticePlan.SENIOR),
            plan.map { it.audience }
        )
        // sin responsable no hay nivel 0
        assertEquals(listOf("risk_escalated:r1:1:2026-10-14"), DeadlineNoticePlan.risk("r1", "Riesgo RS-1", "assessed", due, 1, null).map { it.key })
    }

    @Test
    fun `el texto nombra el riesgo por su codigo y la fecha`() {
        assertEquals(
            "Revisión de riesgo vencida: Riesgo RS-1" to "La revisión debía hacerse el 14/10/2026.",
            NoticeTexts.riskReviewOverdue("Riesgo RS-1", due, 0)
        )
    }

    @Test
    fun `una tarea de un riesgo o de una alerta escala a su transicion de revision`() {
        val catalog = ModelCatalog.fromClasspath()
        for (linked in listOf("social_risk", "early_warning")) {
            val (objectName, transition) = DeadlineNoticePlan.TASK_REFERENCE.getValue(linked)
            assertEquals(linked, objectName)
            assertEquals(true, catalog.transition(objectName, transition) != null, transition)
        }
    }
}
```

Crear `src/test/kotlin/sgspe/deadline/RiskReviewNoticesApiTest.kt`:

```kotlin
package sgspe.deadline

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import sgspe.common.Records
import wasichai.notifications.Audience
import java.time.Instant
import java.util.UUID

// los avisos de la revisión vencida de un riesgo como fuente de wasichai-notifications (ASM-099): risk_escalated por nivel,
// al responsable, a los roles de la transición de tratamiento que sigue y a los de approve_senior de WF-D
class RiskReviewNoticesApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: DeadlineJob

    @Autowired
    private lateinit var notices: DeadlineNotices

    @Autowired
    private lateinit var records: Records

    private fun ids(vararg accounts: Account): Set<UUID> = accounts.map { UUID.fromString(it.id) }.toSet()

    @Test
    fun `una revision vencida desde enero avisa en los tres niveles`() {
        val owner = member("SOCIAL_RISK_MANAGER")
        val social = member("SOCIAL_MANAGER")
        val senior = member("PROJECT_MANAGER")
        val risk = socialRisk(testProject(), owner = owner)
        val assessed =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-01-05", "probability" to 2, "impact" to 2)),
                owner.token,
                withReason("primera revisión")
            )
        assertEquals(HttpStatus.CREATED, assessed.status, assessed.body)
        runBlocking { job.runOnce() }
        val organization = organizationId()
        val drafts = runBlocking { records.asPlatform(organization) { notices.currentNotifications(organization, Instant.now()) } }
        val mine =
            drafts
                .filter { it.key?.startsWith("risk_escalated:$risk:") == true }
                .associate { it.key!!.split(":")[2] to it.audience.map { a -> (a as Audience.User).id }.toSet() }
        assertEquals(setOf("0", "1", "2"), mine.keys)
        assertEquals(ids(owner), mine.getValue("0"))
        assertTrue(mine.getValue("1").containsAll(ids(owner, social)))
        assertEquals(ids(senior), mine.getValue("2"))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.deadline.RiskNoticePlanTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'risk'` y `'riskReviewOverdue'`).

- [ ] **Paso 3: Los avisos y su texto**

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
import sgspe.notification.NoticeTexts
import sgspe.notification.Notices
import sgspe.scope.Assignment
import sgspe.scope.Assignments
```

por:

```kotlin
import sgspe.notification.NoticeTexts
import sgspe.notification.Notices
import sgspe.risk.RiskReview
import sgspe.risk.Risks
import sgspe.scope.Assignment
import sgspe.scope.Assignments
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
object DeadlineNoticePlan {
    // la transición de referencia del nivel 1 de una tarea, por su registro vinculado (workflows.md); F4 suma las QCS (verify
    // de WF-E1, como el nivel 1 de sus plazos) y el plan 4.2b, los riesgos
    val TASK_REFERENCE =
        linkedMapOf(
```

por:

```kotlin
object DeadlineNoticePlan {
    // la transición de referencia del nivel 1 de una tarea, por su registro vinculado (workflows.md); F4 suma las QCS (verify
    // de WF-E1, como el nivel 1 de sus plazos), los riesgos (decide_mitigation de WF-F) y las alertas (confirm de WF-J)
    val TASK_REFERENCE =
        linkedMapOf(
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
            Attention.GRIEVANCE to (Attention.GRIEVANCE to "verify"),
            Attention.INQUIRY to (Attention.GRIEVANCE to "verify"),
            Attention.REQUEST to (Attention.GRIEVANCE to "verify")
        )

```

por:

```kotlin
            Attention.GRIEVANCE to (Attention.GRIEVANCE to "verify"),
            Attention.INQUIRY to (Attention.GRIEVANCE to "verify"),
            Attention.REQUEST to (Attention.GRIEVANCE to "verify"),
            Risks.SOCIAL_RISK to (Risks.SOCIAL_RISK to "decide_mitigation"),
            "early_warning" to ("early_warning" to "confirm")
        )

```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin

    val SENIOR = NoticeAudience.Roles(Commitments.COMMITMENT, "approve_senior")

    fun commitment(
```

por:

```kotlin

    val SENIOR = NoticeAudience.Roles(Commitments.COMMITMENT, "approve_senior")

    // un riesgo con la revisión vencida (workflows.md «Avisos», ASM-099): risk_escalated:<id>:<nivel>:<next_review_date>, el
    // nivel 0 al responsable, el 1 a los roles de la transición de tratamiento que sigue en su estado y el 2 a los de
    // approve_senior de WF-D, porque WF-F no tiene aprobación superior. no hay aviso por revisar: ya está la tarea de revisión
    fun risk(
        id: String,
        label: String,
        state: String?,
        due: LocalDate,
        level: Int,
        owner: UUID?
    ): List<PlannedNotice> =
        (0..level).mapNotNull { reached ->
            val audience =
                when (reached) {
                    0 -> owner?.let { NoticeAudience.Person(it) }
                    1 -> Risks.NEXT_TREATMENT[state]?.let { NoticeAudience.Roles(Risks.SOCIAL_RISK, it) }
                    else -> SENIOR
                } ?: return@mapNotNull null
            PlannedNotice("risk_escalated:$id:$reached:$due", NotificationKind.ACTION, NoticeTexts.riskReviewOverdue(label, due, reached), audience, reached)
        }

    fun commitment(
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
// las quejas y las consultas (qcs_deadline) en las organizaciones que las tienen; el aviso de una QCS confidencial, y el de una
// tarea vinculada a ella, solo va a quien la vería (ConfidentialReaders, ASM-102), y en el nivel 2 los roles de approve_senior
// que no la ven reciben qcs_confidential_overdue, sin datos del registro ni enlace
@Component
class DeadlineNotices(
```

por:

```kotlin
// las quejas y las consultas (qcs_deadline) en las organizaciones que las tienen; el aviso de una QCS confidencial, y el de una
// tarea vinculada a ella, solo va a quien la vería (ConfidentialReaders, ASM-102), y en el nivel 2 los roles de approve_senior
// que no la ven reciben qcs_confidential_overdue, sin datos del registro ni enlace. y la revisión vencida de los riesgos
// (risk_escalated por niveles, ASM-099), con la situación que escribe JOB-01 y su fecha recalculada
@Component
class DeadlineNotices(
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
        val tasks = records.all(TASK, criteria = listOf(Criteria.inState(Deadlines.TASK_OPEN)))
        val qcs = Deadlines.TRACKED.filter { it.objectName in Attention.OBJECTS }.associateWith { tracked -> qcsRows(tracked) }
        val projects = mutableMapOf<String, Map<String, Any?>>()
        val recipients = mutableMapOf<Triple<UUID, String, String>, List<UUID>>()
```

por:

```kotlin
        val tasks = records.all(TASK, criteria = listOf(Criteria.inState(Deadlines.TASK_OPEN)))
        val qcs = Deadlines.TRACKED.filter { it.objectName in Attention.OBJECTS }.associateWith { tracked -> qcsRows(tracked) }
        val risks = overdueRisks(today)
        val projects = mutableMapOf<String, Map<String, Any?>>()
        val recipients = mutableMapOf<Triple<UUID, String, String>, List<UUID>>()
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
            }
        }
        // las QCS confidenciales a las que apunta una tarea: su aviso de vencida también se filtra (ASM-102)
        val linkedQcs = linkedConfidential(tasks)
```

por:

```kotlin
            }
        }
        for (row in risks) {
            val due = Values.text(row.attributes[Risks.NEXT_REVIEW])?.let(LocalDate::parse) ?: continue
            val params = params(Values.text(row.attributes["project"]) ?: continue)
            val plan =
                DeadlineNoticePlan.risk(
                    row.id,
                    "Riesgo ${Values.text(row.attributes["code"]) ?: row.id}",
                    row.state,
                    due,
                    level(due, today, params),
                    Records.uuid(row.attributes[Risks.OWNER])
                )
            add(row, Risks.SOCIAL_RISK, plan, due)
        }
        // las QCS confidenciales a las que apunta una tarea: su aviso de vencida también se filtra (ASM-102)
        val linkedQcs = linkedConfidential(tasks)
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, reemplazar:

```kotlin
        }

    // las QCS confidenciales vinculadas a las tareas, por (objeto, id): una lectura por objeto
    private suspend fun linkedConfidential(tasks: List<RecordResponse>): Map<Pair<String, String?>, Map<String, Any?>> =
```

por:

```kotlin
        }

    // los riesgos con la revisión vencida que escribió JOB-01 y que lo siguen hoy; vacío si la organización no tiene el modelo de F4
    private suspend fun overdueRisks(today: LocalDate): List<RecordResponse> =
        try {
            records
                .all(Risks.SOCIAL_RISK, filters = mapOf(Risks.REVIEW_STATUS to RiskReview.OVERDUE), criteria = listOf(Criteria.inState(Risks.REVIEWED)))
                .filter { RiskReview.status(Values.text(it.attributes[Risks.NEXT_REVIEW])?.let(LocalDate::parse), today) == RiskReview.OVERDUE }
        } catch (_: NotFoundException) {
            emptyList()
        }

    // las QCS confidenciales vinculadas a las tareas, por (objeto, id): una lectura por objeto
    private suspend fun linkedConfidential(tasks: List<RecordResponse>): Map<Pair<String, String?>, Map<String, Any?>> =
```

En `src/main/kotlin/sgspe/notification/Notices.kt`, reemplazar:

```kotlin
        } else {
            "Compromiso vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierto: revise con su responsable."
        }

```

por:

```kotlin
        } else {
            "Compromiso vencido, escalamiento $level: $label" to "Venció el ${due.format(DATE)} y sigue abierto: revise con su responsable."
        }

    // F4: un riesgo con la revisión vencida (DeadlineNotices, ASM-099). `label` es «Riesgo <código>»
    fun riskReviewOverdue(
        label: String,
        due: LocalDate,
        level: Int
    ): Pair<String, String> =
        if (level == 0) {
            "Revisión de riesgo vencida: $label" to "La revisión debía hacerse el ${due.format(DATE)}."
        } else {
            "Revisión de riesgo vencida, escalamiento $level: $label" to "La revisión debía hacerse el ${due.format(DATE)} y sigue pendiente."
        }

```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `RiskNoticePlanTest` con 3 pruebas, y `DeadlineNoticePlanTest` y `QcsNoticePlanTest` sin
cambios, en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/deadline/DeadlineNotices.kt src/main/kotlin/sgspe/notification/Notices.kt \
  src/test/kotlin/sgspe/deadline/RiskNoticePlanTest.kt src/test/kotlin/sgspe/deadline/RiskReviewNoticesApiTest.kt
git commit -F - <<'MSG'
feat: avisos de la revisión vencida de los riesgos

DeadlineNotices publica risk_escalated por nivel cuando la revisión de un riesgo vence: al
responsable, a los roles del tratamiento que sigue y a los de approve_senior; las tareas de un
riesgo o de una alerta escalan a su transición de revisión.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.deadline.RiskReviewNoticesApiTest` y `sgspe.deadline.QcsNoticesApiTest`.

---

### Tarea 8: `WarningWriteGuard`, WF-J y WF-M

**Files:**
- Create: `src/main/kotlin/sgspe/warning/Warnings.kt`, `src/main/kotlin/sgspe/warning/WarningWriteGuard.kt`
- Test: `src/test/kotlin/sgspe/warning/WarningGuardRulesTest.kt`, `EarlyWarningWorkflowApiTest.kt`,
  `IncidentWorkflowApiTest.kt`, `WarningRuleApiTest.kt` (los cuatro en `src/test/kotlin/sgspe/warning/`)

**Interfaces:**
- Consumes: `Callers`, `Permissions.has`, `WriteView`, `RuleGuard` (F2 y F3).
- Produces: `object Warnings` (`WARNING`, `RULE`, `SIGNAL`, `INCIDENT`, `MENTION`, `CONFIRM_ALERT`, `DECISIONS`,
  `INCIDENT_CLOSE`, `FINISHED`, los seis tipos `SIGNALS`, `QCS`, `OVERDUE_COMMITMENTS`, `NEGATIVE_MENTIONS`, `INCIDENTS`,
  `RISK_LEVEL` y `KINDS`, `DEFAULT_WINDOW`, `MAX_WINDOW`, `MAX_SCORE`, `SERVICE_ACCOUNT_DOMAIN`); `object WarningGuardRules`
  (`decision(user, holdsConfirm)`, `isServiceAccount(user)`, `incidentClose(record)`, `rule(after, changed)`); el bean
  `WarningWriteGuard`.

La tabla de §3.7 de la especificación: la decisión humana y la doble llave de `confirm` y `dismiss` (`dismiss` exige además
motivo, `TransitionReasonGuard`), el cierre del incidente con su respuesta y sus consecuencias, y la regla válida (decisión
14). Las tres clases por workflow que faltaban de F4 son `EarlyWarningWorkflowApiTest`, `IncidentWorkflowApiTest` (aquí) y
`SocialRiskWorkflowApiTest` (Tarea 4). El caso de `ACTION_REQUIRED` va por la consulta en seco (decisión 12).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/warning/WarningGuardRulesTest.kt`:

```kotlin
package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import wasichai.core.identity.AuthenticatedUser
import java.util.UUID

// las reglas de WF-J, WF-M y de las reglas de alerta (especificación de F4 §3.7 y §4, ASM-097), sin base
class WarningGuardRulesTest {
    private fun user(
        email: String = "persona@sgspe.test",
        service: String? = null
    ) = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), email, listOf("SOCIAL_RISK_MANAGER"), service)

    private fun codes(findings: List<sgspe.common.Finding>) = findings.map { it.code to it.field }

    @Test
    fun `confirmar o descartar lo decide una persona con CONFIRM_ALERT`() {
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.decision(user(), holdsConfirm = true)))
        assertEquals(listOf("ACTION_REQUIRED" to null), codes(WarningGuardRules.decision(user(), holdsConfirm = false)))
        assertEquals(listOf("HUMAN_DECISION_REQUIRED" to null), codes(WarningGuardRules.decision(null, holdsConfirm = true)))
        assertEquals(listOf("HUMAN_DECISION_REQUIRED" to null), codes(WarningGuardRules.decision(user(service = "integracion"), holdsConfirm = true)))
        assertEquals(
            listOf("HUMAN_DECISION_REQUIRED" to null),
            codes(WarningGuardRules.decision(user(email = "${UUID.randomUUID()}@service-accounts.invalid"), holdsConfirm = true))
        )
        assertEquals(
            403,
            WarningGuardRules
                .decision(null, true)
                .single()
                .status
                .value()
        )
    }

    @Test
    fun `cerrar un incidente exige su respuesta y sus consecuencias`() {
        assertEquals(
            listOf("FIELD_REQUIRED" to "immediate_response", "FIELD_REQUIRED" to "consequences"),
            codes(WarningGuardRules.incidentClose(emptyMap()))
        )
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.incidentClose(mapOf("immediate_response" to "a", "consequences" to "b"))))
    }

    @Test
    fun `el umbral es entero y la ventana va de 1 a 365`() {
        fun rule(vararg pairs: Pair<String, Any?>) =
            codes(WarningGuardRules.rule(mapOf("rule_kind" to "CONTEO DE SEÑALES") + pairs, setOf("threshold", "window_days", "rule_kind")))
        assertEquals(emptyList<Pair<String, String?>>(), rule("threshold" to 3, "window_days" to null))
        assertEquals(emptyList<Pair<String, String?>>(), rule("threshold" to "3.00", "window_days" to 365))
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), rule("threshold" to "2.5"))
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), rule("threshold" to null))
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), rule("threshold" to 0))
        assertEquals(listOf("OUT_OF_RANGE" to "window_days"), rule("threshold" to 3, "window_days" to 366))
        assertEquals(listOf("OUT_OF_RANGE" to "window_days"), rule("threshold" to 3, "window_days" to 0))
    }

    @Test
    fun `el umbral de NIVEL DE RIESGO es un puntaje de 1 a 25, tambien al cambiar solo el tipo`() {
        val after = mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 30)
        assertEquals(listOf("OUT_OF_RANGE" to "threshold"), codes(WarningGuardRules.rule(after, setOf("rule_kind"))))
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.rule(after + ("threshold" to 25), setOf("threshold"))))
        // un cambio que no toca el umbral, la ventana ni el tipo no se juzga: un valor viejo no bloquea renombrar la regla
        assertEquals(emptyList<Pair<String, String?>>(), codes(WarningGuardRules.rule(after, setOf("name"))))
    }

    // los nombres de Warnings existen en el modelo: un renombre dejaría de vigilarlos en silencio
    @Test
    fun `los tipos de regla, las transiciones y la accion existen en el modelo`() {
        val catalog = ModelCatalog.fromClasspath()
        assertEquals(Warnings.KINDS, catalog.enumOf(Warnings.RULE, WarningGuardRules.KIND))
        for (name in Warnings.DECISIONS) assertEquals("in_triage", catalog.transition(Warnings.WARNING, name)?.from, name)
        assertEquals("responding", catalog.transition(Warnings.INCIDENT, Warnings.INCIDENT_CLOSE)?.from)
        assertEquals(true, Warnings.CONFIRM_ALERT in catalog.objectOrNull(Warnings.WARNING)!!.actions)
        assertEquals(emptySet<String>(), Warnings.FINISHED - catalog.states(Warnings.WARNING).toSet())
    }
}
```

Crear `src/test/kotlin/sgspe/warning/EarlyWarningWorkflowApiTest.kt`:

```kotlin
package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-J con WarningWriteGuard (workflows.md, especificación de F4 §3.7, RN-EWS-001): los casos propios de la alerta que la matriz
// de workflows no cubre. confirma y descarta una persona con CONFIRM_ALERT; una cuenta de servicio con el rol no; quien no
// tiene la acción tampoco (en la consulta en seco: los roles de confirm la tienen todos); descartar exige motivo
class EarlyWarningWorkflowApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    // una cuenta de servicio con `role`, asignada al proyecto: su token y su usuario de respaldo (<id>@service-accounts.invalid)
    private fun serviceAccount(role: String): Account {
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
        assign(account, testProject(), role)
        return account
    }

    private fun inTriage(): String {
        val id = earlyWarning(testProject(), manager)
        move("early_warning", id, "start_triage", manager.token)
        return id
    }

    @Test
    fun `una persona con CONFIRM_ALERT confirma y descarta con motivo`() {
        val confirmed = inTriage()
        move("early_warning", confirmed, "confirm", manager.token)
        assertEquals("confirmed", state("early_warning", confirmed))
        val dismissed = inTriage()
        val bare = transition("early_warning", dismissed, "dismiss", manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, bare.status, bare.body)
        assertEquals("REASON_REQUIRED", code(bare))
        move("early_warning", dismissed, "dismiss", manager.token, "falsa alarma")
        assertEquals("dismissed", state("early_warning", dismissed))
    }

    @Test
    fun `una cuenta de servicio con el rol no confirma ni descarta`() {
        val service = serviceAccount("SOCIAL_RISK_MANAGER")
        val id = inTriage()
        for (name in listOf("confirm", "dismiss")) {
            val response = transition("early_warning", id, name, service.token, "decisión automática")
            assertEquals(HttpStatus.FORBIDDEN, response.status, response.body)
            assertEquals("HUMAN_DECISION_REQUIRED", code(response))
        }
        assertEquals("in_triage", state("early_warning", id))
    }

    @Test
    fun `quien no tiene CONFIRM_ALERT no confirma, y la consulta en seco lo dice`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val id = inTriage()
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "early_warning", "recordId" to id, "transition" to "confirm"),
                    HttpStatus.OK,
                    officer.token
                )
            )
        assertEquals(listOf("ACTION_REQUIRED"), check["blocking"].values().map { it["code"].asString() })
        assertEquals(HttpStatus.FORBIDDEN, transition("early_warning", id, "confirm", officer.token).status)
    }
}
```

Crear `src/test/kotlin/sgspe/warning/IncidentWorkflowApiTest.kt`:

```kotlin
package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// WF-M con WarningWriteGuard (workflows.md, especificación de F4 §3.7, ASM-103): el incidente se revisa y se atiende sin
// severidad ni motivo obligatorio, y se cierra solo con su respuesta inmediata y sus consecuencias
class IncidentWorkflowApiTest : F4ApiTest() {
    @Test
    fun `cerrar sin consecuencias se rechaza y con ellas cierra`() {
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val social = member("SOCIAL_MANAGER")
        val id = incident(testProject(), officer, mapOf("consequences" to null))
        move("incident", id, "start_review", officer.token)
        move("incident", id, "start_response", officer.token)
        val response = transition("incident", id, "close", social.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("FIELD_REQUIRED", code(response))
        assertEquals(listOf("consequences"), problemFields(response.body))
        assertEquals(HttpStatus.OK, edit("incident", id, mapOf("consequences" to "Dos horas sin acceso."), social.token).status)
        move("incident", id, "close", social.token)
        assertEquals("closed", state("incident", id))
    }
}
```

Crear `src/test/kotlin/sgspe/warning/WarningRuleApiTest.kt`:

```kotlin
package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// las reglas de alerta con WarningWriteGuard (especificación de F4 §4, ASM-097): umbral entero, de 1 a 25 en NIVEL DE RIESGO,
// y ventana de 1 a 365 días, en el alta y en el cambio; una regla sin proyecto es del tenant
class WarningRuleApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun create(attributes: Map<String, Any?>): Response =
        exchange(
            "POST",
            "/api/objects/early_warning_rule/records",
            mapOf(
                "attributes" to
                    mapOf("name" to "Regla de prueba", "rule_kind" to "CONTEO DE SEÑALES", "threshold" to 3, "enabled" to true, "project" to testProject()) +
                    attributes
            ),
            manager.token
        )

    @Test
    fun `un umbral con decimales o un puntaje mayor que 25 es OUT_OF_RANGE`() {
        val fractional = create(mapOf("threshold" to 2.5))
        assertEquals(HttpStatus.BAD_REQUEST, fractional.status, fractional.body)
        assertEquals("OUT_OF_RANGE", code(fractional))
        assertEquals(listOf("threshold"), problemFields(fractional.body))
        val score = create(mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 30))
        assertEquals(listOf("threshold"), problemFields(score.body))
        assertEquals(HttpStatus.CREATED, create(mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 25)).status)
    }

    @Test
    fun `una ventana fuera de 1 a 365 dias es OUT_OF_RANGE, tambien al cambiarla`() {
        assertEquals(listOf("window_days"), problemFields(create(mapOf("window_days" to 0)).body))
        val id = warningRule(testProject(), manager)
        val edited = edit("early_warning_rule", id, mapOf("window_days" to 400), manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, edited.status, edited.body)
        assertEquals(listOf("window_days"), problemFields(edited.body))
    }

    @Test
    fun `una regla sin proyecto es del tenant`() {
        val tenant = warningRule(null, manager)
        assertEquals(true, record("early_warning_rule", tenant)["attributes"]["project"].let { it == null || it.isNull })
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.warning.WarningGuardRulesTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'WarningGuardRules'` y `'Warnings'`).

- [ ] **Paso 3: Los nombres y la guarda**

Crear `src/main/kotlin/sgspe/warning/Warnings.kt`:

```kotlin
package sgspe.warning

// los nombres de la alerta temprana (BC-09, database-model.md §14) que comparten WarningWriteGuard, JOB-04 y sus pruebas
object Warnings {
    const val WARNING = "early_warning"
    const val RULE = "early_warning_rule"
    const val SIGNAL = "early_warning_signal"
    const val INCIDENT = "incident"
    const val MENTION = "media_mention"

    // la acción declarada de la doble llave de confirm y dismiss (RQ-SEC-004)
    const val CONFIRM_ALERT = "CONFIRM_ALERT"
    val DECISIONS: Set<String> = setOf("confirm", "dismiss")
    const val INCIDENT_CLOSE = "close"

    // las alertas que ya no están abiertas (WF-J): fuera de ellas, una alerta silencia su regla (ASM-097)
    val FINISHED: Set<String> = setOf("dismissed", "closed")

    // los seis tipos de regla del MVP (catálogo «Tipo de regla de alerta», ASM-097), sin CAMBIO DE POSTURA
    const val SIGNALS = "CONTEO DE SEÑALES"
    const val QCS = "CONTEO DE QCS"
    const val OVERDUE_COMMITMENTS = "COMPROMISOS VENCIDOS"
    const val NEGATIVE_MENTIONS = "MENCIONES NEGATIVAS"
    const val INCIDENTS = "INCIDENTES"
    const val RISK_LEVEL = "NIVEL DE RIESGO"
    val KINDS: List<String> = listOf(SIGNALS, QCS, OVERDUE_COMMITMENTS, NEGATIVE_MENTIONS, INCIDENTS, RISK_LEVEL)

    // window_days vacío vale 7; el umbral de NIVEL DE RIESGO es un puntaje P × I, de 1 a 25
    const val DEFAULT_WINDOW = 7L
    const val MAX_WINDOW = 365L
    const val MAX_SCORE = 25L

    // la marca de una cuenta de servicio en su correo de respaldo (WAS-ADR-0043)
    const val SERVICE_ACCOUNT_DOMAIN = "@service-accounts.invalid"
}
```

Crear `src/main/kotlin/sgspe/warning/WarningWriteGuard.kt`:

```kotlin
package sgspe.warning

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.Permissions
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import wasichai.core.identity.AuthenticatedUser
import java.math.BigDecimal

// las reglas de WF-J, WF-M y de las reglas de alerta (workflows.md, especificación de F4 §3.7 y §4), puras
object WarningGuardRules {
    const val THRESHOLD = "threshold"
    const val WINDOW = "window_days"
    const val KIND = "rule_kind"

    // confirmar o descartar una alerta es una decisión humana (RN-EWS-001, RQ-SEC-014): nunca la plataforma, una
    // automatización ni una cuenta de servicio, aunque tenga el rol; y exige la acción CONFIRM_ALERT (doble llave)
    fun decision(
        user: AuthenticatedUser?,
        holdsConfirm: Boolean
    ): List<Finding> =
        when {
            user == null || isServiceAccount(user) ->
                listOf(
                    Finding(
                        "HUMAN_DECISION_REQUIRED",
                        "Confirmar o descartar una alerta lo decide una persona, nunca la plataforma ni una cuenta de servicio (RN-EWS-001)",
                        status = HttpStatus.FORBIDDEN
                    )
                )
            !holdsConfirm ->
                listOf(Finding("ACTION_REQUIRED", "Confirmar o descartar una alerta exige la acción CONFIRM_ALERT", status = HttpStatus.FORBIDDEN))
            else -> emptyList()
        }

    fun isServiceAccount(user: AuthenticatedUser): Boolean = user.serviceAccount != null || user.email.endsWith(Warnings.SERVICE_ACCOUNT_DOMAIN)

    // cerrar un incidente exige su respuesta inmediata y sus consecuencias (RF-EWS-001)
    fun incidentClose(record: Map<String, Any?>): List<Finding> =
        listOf("immediate_response", "consequences").filter { Values.text(record[it]) == null }.map { field ->
            Finding("FIELD_REQUIRED", "Cerrar un incidente exige $field", field, status = HttpStatus.BAD_REQUEST)
        }

    // una regla de alerta: threshold entero (de 1 en adelante; de 1 a 25 en NIVEL DE RIESGO) y window_days vacío o entero de 1 a
    // 365 (ASM-097). se juzga si la escritura cambia el umbral, la ventana o el tipo; un alta, siempre
    fun rule(
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> {
        if (changed.none { it in setOf(THRESHOLD, WINDOW, KIND) }) return emptyList()
        val findings = mutableListOf<Finding>()
        val max = if (Values.text(after[KIND]) == Warnings.RISK_LEVEL) Warnings.MAX_SCORE else Long.MAX_VALUE
        val threshold = whole(after[THRESHOLD])
        if (threshold == null || threshold < 1 || threshold > max) {
            val range = if (max == Warnings.MAX_SCORE) "un puntaje entero de 1 a 25" else "un número entero desde 1"
            findings += outOfRange(THRESHOLD, "el umbral es $range")
        }
        if (Values.text(after[WINDOW]) != null) {
            val window = whole(after[WINDOW])
            if (window == null || window !in 1..Warnings.MAX_WINDOW) findings += outOfRange(WINDOW, "la ventana es un número entero de días de 1 a 365")
        }
        return findings
    }

    // un número entero, venga como Long, BigDecimal o texto; null si falta o tiene parte fraccionaria
    private fun whole(value: Any?): Long? {
        val number: BigDecimal = Values.decimal(value) ?: return null
        return runCatching { number.stripTrailingZeros().longValueExact() }.getOrNull()
    }

    private fun outOfRange(
        field: String,
        detail: String
    ) = Finding("OUT_OF_RANGE", detail, field, status = HttpStatus.BAD_REQUEST)
}

// WarningWriteGuard (sgspe.warning, especificación de F4 §3.7): la decisión humana y la doble llave de confirm y dismiss de
// WF-J, el cierre de un incidente de WF-M y la validez de las reglas de alerta, por cualquier puerta (la API genérica, la
// consulta en seco y JOB-04). `dismiss` exige además motivo (TransitionReasonGuard)
@Component
@Order(GuardOrder.DOMAIN)
class WarningWriteGuard(
    private val callers: Callers,
    private val permissions: Permissions,
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when {
            change.objectName == Warnings.WARNING && change.kind == RecordChangeKind.TRANSITIONED && change.transition in Warnings.DECISIONS -> {
                val user = if (change.userId == null) null else callers.userOrNull()
                WarningGuardRules.decision(user, user != null && permissions.has(user, Warnings.CONFIRM_ALERT, Warnings.WARNING))
            }
            change.objectName == Warnings.INCIDENT && change.kind == RecordChangeKind.TRANSITIONED && change.transition == Warnings.INCIDENT_CLOSE ->
                WarningGuardRules.incidentClose(change.before.orEmpty())
            change.objectName == Warnings.RULE &&
                (change.kind == RecordChangeKind.CREATED || change.kind == RecordChangeKind.UPDATED) &&
                !change.isLink() -> {
                val after = view.after(change)
                val changed = if (change.kind == RecordChangeKind.CREATED) after.keys else view.changed(change, after)
                WarningGuardRules.rule(after, changed)
            }
            else -> emptyList()
        }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `WarningGuardRulesTest` con 5 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/warning/Warnings.kt src/main/kotlin/sgspe/warning/WarningWriteGuard.kt \
  src/test/kotlin/sgspe/warning/WarningGuardRulesTest.kt src/test/kotlin/sgspe/warning/EarlyWarningWorkflowApiTest.kt \
  src/test/kotlin/sgspe/warning/IncidentWorkflowApiTest.kt src/test/kotlin/sgspe/warning/WarningRuleApiTest.kt
git commit -F - <<'MSG'
feat: guarda de alertas, incidentes y reglas de alerta

WarningWriteGuard exige una persona con CONFIRM_ALERT para confirmar o descartar una alerta, la
respuesta y las consecuencias para cerrar un incidente, y un umbral entero y una ventana de 1 a
365 días en las reglas (RN-EWS-001, ASM-097).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.warning.EarlyWarningWorkflowApiTest`, `IncidentWorkflowApiTest` y
`WarningRuleApiTest`.

---
### Tarea 9: La alerta de JOB-04 protegida: `generator_key` y reglas con alertas

**Files:**
- Modify: `src/main/kotlin/sgspe/common/GeneratorKeyGuard.kt`, `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`
- Test: `src/test/kotlin/sgspe/common/GuardTablesF4Test.kt`, `src/test/kotlin/sgspe/warning/EarlyWarningProtectionApiTest.kt`

**Interfaces:**
- Consumes: `JobTasks.GENERATOR_KEY`, `ModelCatalog.guardedDependents` (F3).
- Produces: `GeneratorKeyGuard.OBJECTS = setOf("task", "early_warning")`; `DependentsDeleteGuard.PRESERVED:
  Map<String, List<Pair<String, String>>>` con `early_warning_rule` → `early_warning.early_warning_rule`.

Especificación §3.1: `early_warning.generator_key` solo lo escribe JOB-04 (una persona que lo fija recibe `403
GENERATOR_KEY_PLATFORM_ONLY`), y una regla con alertas no se borra (`409 HAS_DEPENDENTS`): se desactiva con `enabled`
falso. Decisión 6: la relación es opcional y el catálogo no la vigila, así que va en `PRESERVED`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/common/GuardTablesF4Test.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// las tablas de F4 de GeneratorKeyGuard y DependentsDeleteGuard nombran objetos y campos por texto: si el modelo los renombra,
// la guarda dejaría de vigilarlos en silencio
class GuardTablesF4Test {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `cada objeto con generator_key lo declara unico en el modelo`() {
        for (objectName in GeneratorKeyGuard.OBJECTS) {
            val field = catalog.objectOrNull(objectName)?.fields?.get(JobTasks.GENERATOR_KEY)
            assertEquals("TEXT", field?.type, objectName)
        }
    }

    // la relación de la alerta con su regla es opcional: el catálogo no la vigila y por eso va en PRESERVED
    @Test
    fun `las relaciones que DependentsDeleteGuard preserva existen y son opcionales`() {
        for ((target, links) in DependentsDeleteGuard.PRESERVED) {
            for ((source, field) in links) {
                assertEquals(target, catalog.manyToOneFields(source).toMap()[field], "$source.$field")
                assertTrue(!catalog.isRequiredRelation(source, field) && (source to field) !in catalog.guardedDependents(target), "$source.$field")
            }
        }
    }
}
```

Crear `src/test/kotlin/sgspe/warning/EarlyWarningProtectionApiTest.kt`:

```kotlin
package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// lo que protege a las alertas de JOB-04 (especificación de F4 §3.1): generator_key solo lo escribe la plataforma, y una
// regla con alertas no se borra (se desactiva), para no perder el origen de sus alertas
class EarlyWarningProtectionApiTest : F4ApiTest() {
    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    @Test
    fun `una persona no fija ni cambia generator_key de una alerta`() {
        val key = uniqueName("warning_rule:")
        val created =
            exchange(
                "POST",
                "/api/objects/early_warning/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Alerta", "detected_at" to "2026-10-07T15:00:00Z", "generator_key" to key)),
                manager.token
            )
        assertEquals(HttpStatus.FORBIDDEN, created.status, created.body)
        assertEquals("GENERATOR_KEY_PLATFORM_ONLY", code(created))
        assertEquals(listOf("generator_key"), problemFields(created.body))
        val id = earlyWarning(testProject(), manager)
        assertEquals("GENERATOR_KEY_PLATFORM_ONLY", code(edit("early_warning", id, mapOf("generator_key" to key), manager.token)))
        // sin la clave, la alerta se edita con normalidad
        assertEquals(HttpStatus.OK, edit("early_warning", id, mapOf("title" to "Otra alerta"), manager.token).status)
    }

    @Test
    fun `una regla con alertas no se borra y una sin alertas si`() {
        val used = warningRule(testProject(), manager)
        earlyWarning(testProject(), manager, mapOf("early_warning_rule" to used))
        val refused = exchange("DELETE", "/api/objects/early_warning_rule/records/$used", null, manager.token)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("HAS_DEPENDENTS", code(refused))
        assertEquals(listOf("early_warning.early_warning_rule"), problemFields(refused.body))
        val unused = warningRule(testProject(), manager)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/early_warning_rule/records/$unused", null, manager.token).status)
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.GuardTablesF4Test'
```

Esperado: FAIL en la compilación (`Unresolved reference 'OBJECTS'` y `'PRESERVED'`).

- [ ] **Paso 3: Las dos guardas**

En `src/main/kotlin/sgspe/common/GeneratorKeyGuard.kt`, reemplazar:

```kotlin
// crear una tarea con ella suprimiría en silencio la que el trabajo debe crear (JobTasks.ensure la da por hecha). una persona
// que fija o cambia la clave (también al dejarla vacía en un PUT) recibe un 403 con su código; reenviar la clave tal como se
// leyó no es cambiarla. la plataforma no tiene usuario (change.userId nulo)
@Component
@Order(GuardOrder.DERIVED)
```

por:

```kotlin
// crear una tarea con ella suprimiría en silencio la que el trabajo debe crear (JobTasks.ensure la da por hecha). una persona
// que fija o cambia la clave (también al dejarla vacía en un PUT) recibe un 403 con su código; reenviar la clave tal como se
// leyó no es cambiarla. la plataforma no tiene usuario (change.userId nulo). desde F4 vale igual para early_warning, cuya
// clave escribe JOB-04 (WarningRulesJob): una persona que la fijara silenciaría la regla ese día
@Component
@Order(GuardOrder.DERIVED)
```

En `src/main/kotlin/sgspe/common/GeneratorKeyGuard.kt`, reemplazar:

```kotlin
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != JobTasks.TASK || change.isLink()) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.userId == null) return emptyList()
```

por:

```kotlin
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS || change.isLink()) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.userId == null) return emptyList()
```

En `src/main/kotlin/sgspe/common/GeneratorKeyGuard.kt`, reemplazar:

```kotlin
    companion object {
        const val CODE = "GENERATOR_KEY_PLATFORM_ONLY"
    }
}
```

por:

```kotlin
    companion object {
        const val CODE = "GENERATOR_KEY_PLATFORM_ONLY"

        // los objetos con generator_key: las tareas de los trabajos y, desde F4, las alertas de JOB-04
        val OBJECTS: Set<String> = setOf(JobTasks.TASK, "early_warning")
    }
}
```

En `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`, reemplazar:

```kotlin
// ahí la base no falla, pero SET NULL cambiaría ese registro sin motivo ni auditoría (borrar una cláusula que nombra un
// compromiso).
// la comprobación es de la base y no de quien borra: con Records solo vería lo que sus permisos le dejan leer (y
// RecordService.asPlatform no se puede usar dentro de una petición). por eso es un SELECT 1 … LIMIT 1 por relación sobre la
```

por:

```kotlin
// ahí la base no falla, pero SET NULL cambiaría ese registro sin motivo ni auditoría (borrar una cláusula que nombra un
// compromiso).
// desde F4 vigila además PRESERVED: relaciones opcionales que el modelo no puede marcar y cuyo vínculo es la historia de un
// registro. una regla de alerta con alertas no se borra (409 HAS_DEPENDENTS): se desactiva con `enabled` falso, para no perder
// el origen de sus alertas (especificación de F4 §3.1)
// la comprobación es de la base y no de quien borra: con Records solo vería lo que sus permisos le dejan leer (y
// RecordService.asPlatform no se puede usar dentro de una petición). por eso es un SELECT 1 … LIMIT 1 por relación sobre la
```

En `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`, reemplazar:

```kotlin
        val id = change.recordId
        if (change.kind != RecordChangeKind.DELETED || id == null) return emptyList()
        val first =
            catalog.guardedDependents(change.objectName).firstOrNull { (source, field) ->
                referenced(change, id, source, field)
            } ?: return emptyList()
        return listOf(
            Finding(
```

por:

```kotlin
        val id = change.recordId
        if (change.kind != RecordChangeKind.DELETED || id == null) return emptyList()
        val dependents = catalog.guardedDependents(change.objectName) + PRESERVED[change.objectName].orEmpty()
        val first = dependents.firstOrNull { (source, field) -> referenced(change, id, source, field) } ?: return emptyList()
        return listOf(
            Finding(
```

En `src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt`, reemplazar:

```kotlin
            )
        )
    }

```

por:

```kotlin
            )
        )
    }

    companion object {
        // objeto que se borra -> (objeto, campo) que lo referencian y que se vigilan aunque la relación sea opcional
        val PRESERVED: Map<String, List<Pair<String, String>>> = mapOf("early_warning_rule" to listOf("early_warning" to "early_warning_rule"))
    }

```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `GuardTablesF4Test` con 2 pruebas y `DependentsDeleteGuardTest` sin cambios en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/GeneratorKeyGuard.kt src/main/kotlin/sgspe/common/DependentsDeleteGuard.kt \
  src/test/kotlin/sgspe/common/GuardTablesF4Test.kt src/test/kotlin/sgspe/warning/EarlyWarningProtectionApiTest.kt
git commit -F - <<'MSG'
feat: generator_key de las alertas y reglas con alertas protegidas

GeneratorKeyGuard reserva a la plataforma también la clave de una alerta de JOB-04, y
DependentsDeleteGuard no deja borrar una regla de alerta que ya tiene alertas: se desactiva, para
no perder su origen.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.warning.EarlyWarningProtectionApiTest` y
`sgspe.workflow.TaskWorkflowApiTest`.

---

### Tarea 10: Los seis tipos de regla de JOB-04, puros

**Files:**
- Create: `src/main/kotlin/sgspe/warning/WarningEvaluation.kt`
- Test: `src/test/kotlin/sgspe/warning/WarningEvaluationTest.kt`

**Interfaces:**
- Consumes: `Warnings` (Tarea 8), `Values`.
- Produces: `data class WarningRule(id, name, kind, project: String?, threshold: Long, windowDays: Long, severity)`;
  `data class RiskHit(id, code, score: Long, locality, community)`; `object WarningEvaluation` (`DEFAULT_SEVERITY`,
  `parse(id, attributes): WarningRule?`, `applicable(rules, project)`, `windowStart(today, days)`, `fires(value,
  threshold)`, `title(rule)`, `description(rule, value, risk)`, `generatorKey(rule, project, risk, today)`, `alert(rule,
  project, value, risk, now, today): Map<String, Any?>`).

La tabla de §4 de la especificación y `workflows.md` («Tipos de regla de JOB-04»): la regla del proyecto reemplaza a la del
tenant del mismo tipo; la ventana son los días de Lima que terminan hoy; dispara al alcanzar el umbral; el texto lleva el
conteo, la ventana y el umbral (en NIVEL DE RIESGO, el código y el puntaje), nunca datos personales ni texto libre de otros
registros. Una regla guardada antes de `WarningWriteGuard` que no se puede evaluar se salta.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/warning/WarningEvaluationTest.kt`:

```kotlin
package sgspe.warning

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

// los seis tipos de regla de JOB-04 (workflows.md «Tipos de regla de JOB-04», ASM-097), sin base: la regla guardada, la
// precedencia de la del proyecto, la ventana, el umbral y la alerta que crea, sin datos personales
class WarningEvaluationTest {
    private val today = LocalDate.parse("2026-10-09")

    private fun rule(
        id: String,
        kind: String,
        project: String? = null,
        threshold: Long = 3,
        window: Long = 7
    ) = WarningRule(id, "Regla $id", kind, project, threshold, window, "MEDIO")

    @Test
    fun `una regla guardada se lee con sus valores por defecto y una invalida se salta`() {
        val parsed = WarningEvaluation.parse("r1", mapOf("name" to "Señales", "rule_kind" to "CONTEO DE SEÑALES", "threshold" to "3.00"))
        assertEquals(WarningRule("r1", "Señales", "CONTEO DE SEÑALES", null, 3, 7, "MEDIO"), parsed)
        assertNull(WarningEvaluation.parse("r2", mapOf("rule_kind" to "CAMBIO DE POSTURA", "threshold" to 3)))
        assertNull(WarningEvaluation.parse("r3", mapOf("rule_kind" to "NIVEL DE RIESGO", "threshold" to 30)))
        assertNull(WarningEvaluation.parse("r4", mapOf("rule_kind" to "INCIDENTES", "threshold" to 2.5)))
        assertNull(WarningEvaluation.parse("r5", mapOf("rule_kind" to "INCIDENTES", "threshold" to 2, "window_days" to 0)))
    }

    @Test
    fun `la regla del proyecto reemplaza a la del tenant del mismo tipo`() {
        val tenantSignals = rule("t1", Warnings.SIGNALS)
        val tenantIncidents = rule("t2", Warnings.INCIDENTS)
        val ownSignals = rule("p1", Warnings.SIGNALS, "P")
        val otherProject = rule("q1", Warnings.INCIDENTS, "Q")
        val rules = listOf(tenantSignals, tenantIncidents, ownSignals, otherProject)
        assertEquals(listOf(ownSignals, tenantIncidents), WarningEvaluation.applicable(rules, "P"))
        assertEquals(listOf(otherProject, tenantSignals), WarningEvaluation.applicable(rules, "Q"))
    }

    @Test
    fun `la ventana son los dias de Lima que terminan hoy y dispara al alcanzar el umbral`() {
        assertEquals(LocalDate.parse("2026-10-03"), WarningEvaluation.windowStart(today, 7))
        assertEquals(today, WarningEvaluation.windowStart(today, 1))
        assertTrue(WarningEvaluation.fires(3, 3))
        assertFalse(WarningEvaluation.fires(2, 3))
    }

    @Test
    fun `cada tipo describe su conteo, su ventana y su umbral, sin desglose`() {
        assertEquals("3 señales en 7 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.SIGNALS), 3))
        assertEquals("4 QCS recibidas en 7 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.QCS), 4))
        assertEquals("5 compromisos vencidos (umbral 3)", WarningEvaluation.description(rule("a", Warnings.OVERDUE_COMMITMENTS), 5))
        assertEquals("3 menciones negativas en 14 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.NEGATIVE_MENTIONS, window = 14), 3))
        assertEquals("3 incidentes en 7 días (umbral 3)", WarningEvaluation.description(rule("a", Warnings.INCIDENTS), 3))
        val risk = RiskHit("r", "RS-7", 16, "loc", null)
        assertEquals("Riesgo RS-7 con puntaje 16 (umbral 15)", WarningEvaluation.description(rule("a", Warnings.RISK_LEVEL, threshold = 15), 16, risk))
    }

    @Test
    fun `la alerta nace en detected con su clave y, en NIVEL DE RIESGO, con el riesgo y su territorio`() {
        val now = Instant.parse("2026-10-09T15:00:00Z")
        val signals = WarningEvaluation.alert(rule("a", Warnings.SIGNALS), "P", 3, null, now, today)
        assertEquals("Regla a: CONTEO DE SEÑALES", signals["title"])
        assertEquals("warning_rule:a:P:2026-10-09", signals["generator_key"])
        assertEquals(false, signals["ai_detected"])
        assertEquals(
            setOf("title", "trigger_description", "severity", "detected_at", "early_warning_rule", "project", "ai_detected", "generator_key"),
            signals.keys
        )
        val risk = WarningEvaluation.alert(rule("b", Warnings.RISK_LEVEL, threshold = 15), "P", 16, RiskHit("r", "RS-7", 16, "loc", "com"), now, today)
        assertEquals("warning_rule:b:P:r:2026-10-09", risk["generator_key"])
        assertEquals(listOf("r", "loc", "com"), listOf(risk["social_risk"], risk["locality"], risk["community"]))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.warning.WarningEvaluationTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'WarningEvaluation'`, `'WarningRule'` y `'RiskHit'`).

- [ ] **Paso 3: Los evaluadores**

Crear `src/main/kotlin/sgspe/warning/WarningEvaluation.kt`:

```kotlin
package sgspe.warning

import sgspe.common.Values
import java.time.Instant
import java.time.LocalDate

// una regla de alerta habilitada como la evalúa JOB-04 (especificación de F4 §4, ASM-097): `project` vacío es una regla del
// tenant; window_days vacío vale 7; severity vacía, MEDIO
data class WarningRule(
    val id: String,
    val name: String,
    val kind: String,
    val project: String?,
    val threshold: Long,
    val windowDays: Long,
    val severity: String
)

// el riesgo que dispara una regla NIVEL DE RIESGO, con lo que la alerta copia: su código para el texto y su territorio
data class RiskHit(
    val id: String,
    val code: String?,
    val score: Long,
    val locality: String?,
    val community: String?
)

// los seis tipos de regla de JOB-04, puros (workflows.md «Tipos de regla de JOB-04», ASM-097): qué reglas aplican a un proyecto,
// la ventana de días de Lima que terminan hoy, cuándo dispara una regla y la alerta que crea. ningún texto lleva datos
// personales, texto libre de otros registros ni valores de campos restringidos: solo el conteo, el umbral, la ventana y, en
// NIVEL DE RIESGO, el código y el puntaje del riesgo
object WarningEvaluation {
    const val DEFAULT_SEVERITY = "MEDIO"

    // la regla guardada, o null si no se puede evaluar (un tipo fuera del catálogo, un umbral o una ventana inválidos guardados
    // antes de WarningWriteGuard): JOB-04 la salta y lo registra
    fun parse(
        id: String,
        attributes: Map<String, Any?>
    ): WarningRule? {
        val kind = Values.text(attributes["rule_kind"])?.takeIf { it in Warnings.KINDS } ?: return null
        val threshold = whole(attributes["threshold"])?.takeIf { it >= 1 && (kind != Warnings.RISK_LEVEL || it <= Warnings.MAX_SCORE) } ?: return null
        val window =
            if (Values.text(attributes["window_days"]) == null) {
                Warnings.DEFAULT_WINDOW
            } else {
                whole(attributes["window_days"])?.takeIf { it in 1..Warnings.MAX_WINDOW } ?: return null
            }
        return WarningRule(
            id,
            Values.text(attributes["name"]) ?: kind,
            kind,
            Values.text(attributes["project"]),
            threshold,
            window,
            Values.text(attributes["severity"]) ?: DEFAULT_SEVERITY
        )
    }

    // las reglas de un proyecto: las suyas y, de cada tipo que no tiene, las del tenant. una regla del proyecto reemplaza a la
    // del tenant del mismo tipo (ASM-097)
    fun applicable(
        rules: List<WarningRule>,
        project: String
    ): List<WarningRule> {
        val own = rules.filter { it.project == project }
        val kinds = own.map { it.kind }.toSet()
        return own + rules.filter { it.project == null && it.kind !in kinds }
    }

    // el primer día de la ventana: los window_days días de Lima que terminan hoy, desde el inicio de hoy − window_days + 1
    fun windowStart(
        today: LocalDate,
        days: Long
    ): LocalDate = today.minusDays(days - 1)

    // dispara cuando el valor medido alcanza el umbral
    fun fires(
        value: Long,
        threshold: Long
    ): Boolean = value >= threshold

    fun title(rule: WarningRule): String = "${rule.name}: ${rule.kind}"

    fun description(
        rule: WarningRule,
        value: Long,
        risk: RiskHit? = null
    ): String {
        val w = rule.windowDays
        val t = rule.threshold
        return when (rule.kind) {
            Warnings.SIGNALS -> "$value señales en $w días (umbral $t)"
            Warnings.QCS -> "$value QCS recibidas en $w días (umbral $t)"
            Warnings.OVERDUE_COMMITMENTS -> "$value compromisos vencidos (umbral $t)"
            Warnings.NEGATIVE_MENTIONS -> "$value menciones negativas en $w días (umbral $t)"
            Warnings.INCIDENTS -> "$value incidentes en $w días (umbral $t)"
            else -> "Riesgo ${risk?.code ?: risk?.id} con puntaje $value (umbral $t)"
        }
    }

    // la clave única de la alerta: regla, proyecto, riesgo (en NIVEL DE RIESGO) y fecha de Lima. frena una carrera entre réplicas
    fun generatorKey(
        rule: WarningRule,
        project: String,
        risk: String?,
        today: LocalDate
    ): String = "warning_rule:${rule.id}:$project${risk?.let { ":$it" }.orEmpty()}:$today"

    // la alerta que crea JOB-04, en detected (WF-J): nunca confirmada ni con datos de una persona
    fun alert(
        rule: WarningRule,
        project: String,
        value: Long,
        risk: RiskHit?,
        now: Instant,
        today: LocalDate
    ): Map<String, Any?> =
        mapOf(
            "title" to title(rule),
            "trigger_description" to description(rule, value, risk),
            "severity" to rule.severity,
            "detected_at" to now.toString(),
            "early_warning_rule" to rule.id,
            "project" to project,
            "ai_detected" to false,
            "generator_key" to generatorKey(rule, project, risk?.id, today)
        ) +
            if (risk == null) {
                emptyMap()
            } else {
                listOfNotNull("social_risk" to risk.id, risk.locality?.let { "locality" to it }, risk.community?.let { "community" to it }).toMap()
            }

    private fun whole(value: Any?): Long? = Values.decimal(value)?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `WarningEvaluationTest` con 5 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/warning/WarningEvaluation.kt src/test/kotlin/sgspe/warning/WarningEvaluationTest.kt
git commit -F - <<'MSG'
feat: los seis tipos de regla de alerta como funciones puras

WarningEvaluation lee una regla guardada, aplica la precedencia de la regla del proyecto sobre la
del tenant, fija la ventana de días de Lima, decide si dispara y arma la alerta en detected con
su clave, sin datos personales (ASM-097).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 11: El aviso de alerta nueva

**Files:**
- Create: `src/main/kotlin/sgspe/notification/WarningNotices.kt`
- Modify: `src/main/kotlin/sgspe/notification/Notices.kt` (`NoticeTexts.warningNew`, `Notices.WARNINGS`)
- Test: `src/test/kotlin/sgspe/notification/WarningNoticeTest.kt`, `src/test/kotlin/sgspe/notification/WarningNoticesApiTest.kt`

**Interfaces:**
- Consumes: `Assignments.recipients`, `Notices` (F3). No usa `Warnings` (Tarea 8): nombra `early_warning` y
  `start_triage` por su cuenta, así puede ir en el mismo grupo.
- Produces: `object WarningNotice` (`Effect`, `WARNING`, `START_TRIAGE`, `key(id)`, `effect(change)`); el bean
  `WarningNoticeListener` (`@Order(230)`); `Notices.WARNINGS = "sgspe.warnings"`; `NoticeTexts.warningNew(title,
  severity)`.

`workflows.md` («Listeners» y «Avisos»): el alta de una alerta, por una persona o por JOB-04, publica `warning_new:<id>` al
responsable y a los roles de `start_triage`, salvo quien la creó; `start_triage` lo resuelve. Que el alta de la plataforma
llegue al listener lo prueba la Tarea 12 (decisión 1). El texto y la fuente van en otros puntos de `Notices.kt` que los de
las Tareas 5 y 7.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/notification/WarningNoticeTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.notification.WarningNotice.Effect
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// el aviso de alerta nueva (workflows.md «Avisos»), sin base: cuándo se publica y se resuelve, su clave y su texto
class WarningNoticeTest {
    private fun change(
        objectName: String,
        kind: RecordChangeKind,
        transition: String? = null
    ) = RecordChange(UUID.randomUUID(), null, UUID.randomUUID(), objectName, UUID.randomUUID(), kind, null, emptyMap(), null, transition)

    @Test
    fun `el alta publica, start_triage resuelve y lo demas no toca el aviso`() {
        assertEquals(Effect.PUBLISH, WarningNotice.effect(change("early_warning", RecordChangeKind.CREATED)))
        assertEquals(Effect.RESOLVE, WarningNotice.effect(change("early_warning", RecordChangeKind.TRANSITIONED, "start_triage")))
        assertEquals(Effect.NONE, WarningNotice.effect(change("early_warning", RecordChangeKind.TRANSITIONED, "confirm")))
        assertEquals(Effect.NONE, WarningNotice.effect(change("incident", RecordChangeKind.CREATED)))
    }

    @Test
    fun `la clave lleva el id y el texto el titulo y la severidad`() {
        val id = UUID.randomUUID()
        assertEquals("warning_new:$id", WarningNotice.key(id))
        assertEquals(
            "Alerta nueva: Señales: CONTEO DE SEÑALES" to "Severidad ALTO. Inicie su triaje.",
            NoticeTexts.warningNew("Señales: CONTEO DE SEÑALES", "ALTO")
        )
        assertEquals("Alerta nueva: A" to "Inicie su triaje.", NoticeTexts.warningNew("A", null))
        assertEquals("sgspe.warnings", Notices.WARNINGS)
    }

    @Test
    fun `start_triage sale de detected`() {
        assertEquals("detected", ModelCatalog.fromClasspath().transition(WarningNotice.WARNING, WarningNotice.START_TRIAGE)?.from)
    }
}
```

Crear `src/test/kotlin/sgspe/notification/WarningNoticesApiTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// el aviso de alerta nueva en la bandeja (workflows.md «Avisos»): una alerta registrada por una persona avisa a su
// responsable y a los roles de start_triage, no a quien la registró, y start_triage lo resuelve. el de las alertas de JOB-04
// lo prueba WarningRulesJobApiTest
class WarningNoticesApiTest : F4ApiTest() {
    private fun pendingFor(
        account: Account,
        id: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, account.token))["content"]
            .values()
            .any { it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION" }

    @Test
    fun `una alerta nueva avisa al responsable y a start_triage, y el triaje la resuelve`() {
        val creator = member("SOCIAL_RISK_MANAGER")
        val officer = member("COMMUNITY_RELATIONS_OFFICER")
        val owner = member("SOCIAL_MANAGER")
        val id = earlyWarning(testProject(), creator, mapOf("owner_user_id" to owner.id, "owner_user_email" to owner.email))
        assertTrue(pendingFor(officer, id))
        assertTrue(pendingFor(owner, id))
        assertFalse(pendingFor(creator, id))
        move("early_warning", id, "start_triage", officer.token)
        assertFalse(pendingFor(officer, id))
        assertFalse(pendingFor(owner, id))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.notification.WarningNoticeTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'WarningNotice'`, `'warningNew'` y `'WARNINGS'`).

- [ ] **Paso 3: El listener, su texto y su fuente**

En `src/main/kotlin/sgspe/notification/Notices.kt`, reemplazar:

```kotlin
object NoticeTexts {
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun approvalPending(
```

por:

```kotlin
object NoticeTexts {
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    // F4: una alerta nueva (WarningNoticeListener), por una persona o por JOB-04: su título y su severidad, que no llevan datos
    // personales (los de JOB-04 dicen la regla, el tipo, el conteo y el umbral)
    fun warningNew(
        title: String,
        severity: String?
    ): Pair<String, String> = "Alerta nueva: $title" to (severity?.let { "Severidad $it. " } ?: "") + "Inicie su triaje."

    fun approvalPending(
```

En `src/main/kotlin/sgspe/notification/Notices.kt`, reemplazar:

```kotlin

    companion object {
        // las fuentes de SGSPE en el módulo (^[a-z][a-z0-9_.:-]{1,80}$; nunca manual, rule:… ni automation:…)
        const val APPROVALS = "sgspe.approvals"
        const val DEADLINES = "sgspe.deadlines"
```

por:

```kotlin

    companion object {
        // las fuentes de SGSPE en el módulo (^[a-z][a-z0-9_.:-]{1,80}$; nunca manual, rule:… ni automation:…). desde F4, también
        // las alertas nuevas (WarningNoticeListener)
        const val WARNINGS = "sgspe.warnings"
        const val APPROVALS = "sgspe.approvals"
        const val DEADLINES = "sgspe.deadlines"
```

Crear `src/main/kotlin/sgspe/notification/WarningNotices.kt`:

```kotlin
package sgspe.notification

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
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

// el aviso de alerta nueva, puro (workflows.md «Avisos»): qué hace un cambio de una alerta con él
object WarningNotice {
    enum class Effect { PUBLISH, RESOLVE, NONE }

    const val WARNING = "early_warning"
    const val START_TRIAGE = "start_triage"

    fun key(id: UUID): String = "warning_new:$id"

    // el alta lo publica; start_triage lo resuelve
    fun effect(change: RecordChange): Effect =
        when {
            change.objectName != WARNING -> Effect.NONE
            change.kind == RecordChangeKind.CREATED -> Effect.PUBLISH
            change.kind == RecordChangeKind.TRANSITIONED && change.transition == START_TRIAGE -> Effect.RESOLVE
            else -> Effect.NONE
        }
}

// WarningNoticeListener (sgspe.notification, workflows.md «Listeners» y «Avisos»): el alta de una early_warning, por una persona
// o por JOB-04 como la plataforma, publica warning_new:<id> en la fuente sgspe.warnings para su responsable, si lo tiene, y los
// asignados vigentes con un rol de start_triage, salvo quien la creó; start_triage lo resuelve. corre en la transacción del
// alta (JOB-04 abre la suya) como quien escribe; si no lee las asignaciones, lo registra y el alta sigue
@Component
@Order(230)
class WarningNoticeListener(
    private val notices: Notices,
    private val assignments: Assignments
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        try {
            when (WarningNotice.effect(change)) {
                WarningNotice.Effect.PUBLISH -> publish(change)
                WarningNotice.Effect.RESOLVE -> notices.resolve(change.organizationId, Notices.WARNINGS, WarningNotice.key(change.recordId))
                WarningNotice.Effect.NONE -> Unit
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ForbiddenException) {
            log.error("WarningNoticeListener: quien creó la alerta {} no lee las asignaciones; no hay aviso", change.recordId, e)
        }
    }

    private suspend fun publish(change: RecordChange) {
        val attributes = change.after.orEmpty()
        val project = Records.uuid(attributes["project"]) ?: return
        val roles = assignments.recipients(project, WarningNotice.WARNING, WarningNotice.START_TRIAGE, change.userId).map { it.userId }
        val owner = Records.uuid(attributes["owner_user_id"])?.takeIf { it != change.userId }
        val users = (listOfNotNull(owner) + roles).distinct()
        if (users.isEmpty()) {
            log.warn(
                "WarningNoticeListener: la alerta {} no tiene responsable ni asignaciones vigentes con un rol de start_triage; no hay aviso",
                change.recordId
            )
            return
        }
        notices.publish(
            change.organizationId,
            Notices.WARNINGS,
            WarningNotice.key(change.recordId),
            NotificationKind.ACTION,
            NoticeTexts.warningNew(Values.text(attributes["title"]) ?: WarningNotice.WARNING, Values.text(attributes["severity"])),
            users,
            NotificationLink.Record(WarningNotice.WARNING, change.recordId)
        )
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `WarningNoticeTest` con 3 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/notification/Notices.kt src/main/kotlin/sgspe/notification/WarningNotices.kt \
  src/test/kotlin/sgspe/notification/WarningNoticeTest.kt src/test/kotlin/sgspe/notification/WarningNoticesApiTest.kt
git commit -F - <<'MSG'
feat: aviso warning_new al registrar una alerta

WarningNoticeListener avisa al responsable de la alerta y a los roles de start_triage, salvo a
quien la registró, y resuelve el aviso al iniciar el triaje (fuente sgspe.warnings).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.notification.WarningNoticesApiTest`.

---

### Tarea 12: JOB-04 `WarningRulesJob`

**Files:**
- Create: `src/main/kotlin/sgspe/warning/WarningRulesJob.kt`
- Modify: `src/main/resources/application.yml` (`sgspe.jobs.warning-rules.cron`)
- Test: `src/test/kotlin/sgspe/warning/WarningSweepTest.kt`, `src/test/kotlin/sgspe/warning/WarningRulesJobApiTest.kt`

**Interfaces:**
- Consumes: `WarningEvaluation`, `WarningRule`, `RiskHit` (Tarea 10); `Warnings` (Tarea 8); `Risks` (Tarea 1);
  `Attention`, `Raised`, `AttentionCounting.counted` (plan 4.2a); `Organizations.having`, `ClusterLock.tryLock`,
  `Records.asPlatform`, `Records.count`, `Transactions.run`; para la prueba, el endpoint de revisiones (Tarea 3), AUT-09
  y `WarningNoticeListener` (Tarea 11).
- Produces: `class WarningSweep(organizations, asPlatform, evaluate)`; el bean `WarningRulesJob.runOnce(): Int?`
  (`LOCK = "sgspe.warning"`, `SOURCE = "job:warning-rules"`, `REASON`, `ACTIVE`, `PENDING`); `WarningRulesSchedule`
  (cron `sgspe.jobs.warning-rules.cron`, apagado con `sgspe.jobs.enabled=false`).

La especificación §3.7 y `workflows.md` («Tipos de regla de JOB-04»), con las decisiones 1, 7, 8 y 15. Cada alerta se crea
en su propia transacción: así `WarningNoticeListener` publica en la misma y un único duplicado de otra réplica
(`DuplicateKeyException`) aborta solo esa. JOB-04 lee como la plataforma, así que CONTEO DE QCS cuenta también las
confidenciales (consecuencia aceptada 2 de la especificación).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/warning/WarningSweepTest.kt`:

```kotlin
package sgspe.warning

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.commitment.Commitments
import java.util.UUID

// la vuelta de JOB-04 por organización (especificación de F4 §3.7), sin base: una que falla no detiene a las demás, y la vuelta
// lanza al final
class WarningSweepTest {
    @Test
    fun `una organizacion que falla no detiene a las demas`() {
        val good = UUID.randomUUID()
        val bad = UUID.randomUUID()
        val seen = mutableListOf<UUID>()
        val sweep =
            WarningSweep(
                { listOf(bad, good) },
                { _, block -> block() },
                { organization ->
                    seen += organization
                    if (organization == bad) error("falla de prueba") else 2
                }
            )
        val error = assertThrows<IllegalStateException> { runBlocking { sweep.run() } }
        assertEquals(listOf(bad, good), seen)
        assertEquals(true, error.message!!.contains("1 organizaciones"))
        assertEquals(3, runBlocking { WarningSweep({ listOf(good) }, { _, block -> block() }, { 3 }).run() })
    }

    // COMPROMISOS VENCIDOS cuenta los estados en que JOB-01 calcula deadline_status
    @Test
    fun `los compromisos pendientes son los de JOB-01`() {
        assertEquals(Commitments.PENDING, WarningRulesJob.PENDING)
    }
}
```

Crear `src/test/kotlin/sgspe/warning/WarningRulesJobApiTest.kt`:

```kotlin
package sgspe.warning

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// JOB-04 (workflows.md «Tipos de regla de JOB-04» y «Pruebas de trabajos», ASM-097): cada tipo dispara con el valor igual al
// umbral y no con uno menos; la regla del proyecto reemplaza a la del tenant del mismo tipo y una del tenant crea una alerta por
// proyecto; una alerta abierta, o detectada dentro de la ventana, silencia la regla; la alerta nace en detected sin datos
// personales, y su alta dispara AUT-09 y warning_new aunque la cree la plataforma
class WarningRulesJobApiTest : F4ApiTest() {
    @Autowired
    private lateinit var job: WarningRulesJob

    private val manager by lazy { member("SOCIAL_RISK_MANAGER") }

    private fun recent(): String = Instant.now().minus(1, ChronoUnit.HOURS).toString()

    private fun today(): String = LocalDate.now(LIMA).toString()

    // un proyecto activo (JOB-04 solo mira active y suspended): sin las precondiciones de WF-A, como lo permite ASM-062
    private fun activeProject(): String {
        val id = project(mapOf("policy_activation_preconditions" to false))
        move("project", id, "submit_for_review", reason = "revisión")
        move("project", id, "approve", reason = "aprobación")
        return id
    }

    private fun run() {
        runBlocking { job.runOnce() }
    }

    private fun alerts(project: String): List<JsonNode> =
        tree(send("GET", "/api/objects/early_warning/records?project=$project", null, HttpStatus.OK))["content"].values().toList()

    private fun descriptions(project: String): Set<String> = alerts(project).map { it["attributes"]["trigger_description"].asString() }.toSet()

    private fun rule(
        project: String?,
        kind: String,
        threshold: Int,
        name: String = "Regla $kind"
    ): String = warningRule(project, manager, mapOf("rule_kind" to kind, "threshold" to threshold, "name" to name))

    @Test
    fun `cada tipo por eventos dispara al alcanzar el umbral y no con uno menos`() {
        val project = activeProject()
        for (kind in listOf(
            Warnings.SIGNALS,
            Warnings.QCS,
            Warnings.OVERDUE_COMMITMENTS,
            Warnings.NEGATIVE_MENTIONS,
            Warnings.INCIDENTS
        )) {
            rule(project, kind, 2)
        }
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        val original = grievance(project, attributes = mapOf("received_at" to recent()))
        // un planteamiento reclasificado cuenta una vez (AttentionCounting)
        inquiry(project, attributes = mapOf("received_at" to recent(), "reclassified_from_grievance" to original))
        platformPatch("commitment", approvedCommitment(project), mapOf("deadline_status" to "VENCIDO"))
        mediaMention(project, attributes = mapOf("published_date" to today()))
        mediaMention(project, attributes = mapOf("published_date" to today(), "tone" to "POSITIVO"))
        incident(project, attributes = mapOf("occurred_at" to recent()))
        // una señal y un incidente fuera de la ventana de 7 días no cuentan
        warningSignal(project, attributes = mapOf("received_at" to Instant.now().minus(8, ChronoUnit.DAYS).toString()))
        incident(project, attributes = mapOf("occurred_at" to Instant.now().minus(8, ChronoUnit.DAYS).toString()))
        run()
        assertEquals(emptyList<JsonNode>(), alerts(project))
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        grievance(project, attributes = mapOf("received_at" to recent()))
        platformPatch("commitment", approvedCommitment(project), mapOf("deadline_status" to "VENCIDO"))
        mediaMention(project, attributes = mapOf("published_date" to today()))
        incident(project, attributes = mapOf("occurred_at" to recent()))
        run()
        assertEquals(
            setOf(
                "2 señales en 7 días (umbral 2)",
                "2 QCS recibidas en 7 días (umbral 2)",
                "2 compromisos vencidos (umbral 2)",
                "2 menciones negativas en 7 días (umbral 2)",
                "2 incidentes en 7 días (umbral 2)"
            ),
            descriptions(project)
        )
        for (alert in alerts(project)) {
            assertEquals("detected", alert["state"].asString())
            assertFalse(alert["attributes"]["ai_detected"].asBoolean())
            assertTrue(alert["attributes"]["generator_key"].asString().startsWith("warning_rule:"))
        }
    }

    @Test
    fun `NIVEL DE RIESGO crea una alerta por riesgo con su territorio`() {
        val project = activeProject()
        rule(project, Warnings.RISK_LEVEL, 15)
        val high = socialRisk(project)
        val low = socialRisk(project)
        for ((risk, impact) in listOf(high to 4, low to 2)) {
            val response =
                exchange(
                    "POST",
                    "/api/sgspe/social-risks/$risk/assessments",
                    mapOf("attributes" to mapOf("assessment_date" to today(), "probability" to 4, "impact" to impact)),
                    token,
                    withReason("revisión")
                )
            assertEquals(HttpStatus.CREATED, response.status, response.body)
        }
        run()
        val alert = alerts(project).single()
        val code = record("social_risk", high)["attributes"]["code"].asString()
        assertEquals("Riesgo $code con puntaje 16 (umbral 15)", alert["attributes"]["trigger_description"].asString())
        assertEquals(high, alert["attributes"]["social_risk"].asString())
    }

    @Test
    fun `la regla del proyecto reemplaza a la del tenant y una del tenant alerta en cada proyecto`() {
        val tenant = rule(null, Warnings.INCIDENTS, 1, "Incidentes del tenant")
        try {
            val first = activeProject()
            val second = activeProject()
            val own = activeProject()
            rule(own, Warnings.INCIDENTS, 5)
            for (project in listOf(first, second, own)) incident(project, attributes = mapOf("occurred_at" to recent()))
            run()
            assertEquals(setOf("1 incidentes en 7 días (umbral 1)"), descriptions(first))
            assertEquals(setOf("1 incidentes en 7 días (umbral 1)"), descriptions(second))
            assertEquals(emptySet<String>(), descriptions(own))
            assertEquals("Incidentes del tenant: INCIDENTES", alerts(first).single()["attributes"]["title"].asString())
        } finally {
            edit("early_warning_rule", tenant, mapOf("enabled" to false), manager.token)
        }
    }

    @Test
    fun `una alerta abierta y luego la ventana silencian la regla`() {
        val project = activeProject()
        rule(project, Warnings.SIGNALS, 1)
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        run()
        run()
        val alert = alerts(project).single()["id"].asString()
        val triage = member("SOCIAL_RISK_MANAGER", project)
        move("early_warning", alert, "start_triage", triage.token)
        move("early_warning", alert, "dismiss", triage.token, "falsa alarma")
        run()
        assertEquals(1, alerts(project).size)
    }

    // workflows.md «Listeners»: el alta de JOB-04 como la plataforma llega a AUT-09 y a WarningNoticeListener
    @Test
    fun `la alerta de JOB-04 dispara AUT-09 y warning_new, con el motivo del trabajo`() {
        val project = activeProject()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        rule(project, Warnings.SIGNALS, 1, "Señales de la semana")
        warningSignal(project, attributes = mapOf("received_at" to recent()))
        run()
        val alert = alerts(project).single()
        val id = alert["id"].asString()
        assertEquals("Señales de la semana: CONTEO DE SEÑALES", alert["attributes"]["title"].asString())
        assertEquals("SUCCEEDED", awaitRun("early_warning", "aut-09-warning-triage-task", id)["status"].asString())
        val inbox = tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, officer.token))["content"].values()
        assertTrue(inbox.any { it["link"]?.get("recordId")?.asString() == id })
        val history = tree(send("GET", "/api/objects/early_warning/records/$id/history", null, HttpStatus.OK))
        assertTrue(history.values().any { it["reason"]?.asString() == WarningRulesJob.REASON })
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.warning.WarningSweepTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'WarningSweep'` y `'WarningRulesJob'`).

- [ ] **Paso 3: El trabajo, su reloj y su configuración**

Crear `src/main/kotlin/sgspe/warning/WarningRulesJob.kt`:

```kotlin
package sgspe.warning

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.dao.DuplicateKeyException
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import sgspe.common.Criteria
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Organizations
import sgspe.common.Raised
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.risk.Risks
import wasichai.core.platform.ClusterLock
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// la vuelta de JOB-04 sin candado, para probar su aislamiento con dobles: una organización que falla se registra y no detiene a
// las demás, y al final lanza si alguna falló (como DeadlineSweep)
class WarningSweep(
    private val organizations: suspend () -> List<UUID>,
    private val asPlatform: suspend (UUID, suspend () -> Int) -> Int,
    private val evaluate: suspend (UUID) -> Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun run(): Int {
        var created = 0
        val failed = mutableListOf<UUID>()
        val all = organizations()
        for (organization in all) {
            try {
                created += asPlatform(organization) { evaluate(organization) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed += organization
                log.error("WarningRulesJob falló en la organización {}", organization, e)
            }
        }
        check(failed.isEmpty()) { "WarningRulesJob falló en ${failed.size} organizaciones: $failed (se crearon $created alertas)" }
        log.info("WarningRulesJob: {} alertas creadas en {} organizaciones", created, all.size)
        return created
    }
}

// JOB-04 WarningRulesJob (sgspe.warning, workflows.md «Tipos de regla de JOB-04», ASM-097): cada hora, una sola réplica
// (ClusterLock con la clave sgspe.warning), como la plataforma de cada organización con reglas de alerta
// (Organizations.having) y con el origen job:warning-rules. por organización lee las reglas habilitadas y los proyectos en
// active o suspended; a cada proyecto le aplica sus reglas y, de cada tipo que no tiene, las del tenant; mide cada regla con
// conteos (Records.count), salvo CONTEO DE QCS, que cuenta planteamientos con AttentionCounting sobre los de la ventana, y NIVEL
// DE RIESGO, que lee los riesgos revisados del proyecto. si dispara, crea la alerta en detected salvo que haya una abierta de la
// misma regla y proyecto (y riesgo) o una detectada dentro de la ventana; la crea en su propia transacción, con su
// generator_key único (un duplicado de otra réplica cuenta como hecho), así WarningNoticeListener y AUT-09 la ven como un
// alta. nunca aplica transiciones ni confirma (RN-EWS-001). lee como la plataforma, así que CONTEO DE QCS cuenta también las
// confidenciales: la alerta solo lleva el número
@Component
class WarningRulesJob(
    private val records: Records,
    private val organizations: Organizations,
    private val transactions: Transactions,
    private val catalog: ModelCatalog,
    private val locks: ClusterLock,
    private val clock: Clock
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // las alertas creadas; null si otra réplica tenía el candado
    suspend fun runOnce(): Int? {
        val lease = locks.tryLock(LOCK) ?: return null
        return lease.use {
            val now = clock.instant()
            val today = LocalDate.ofInstant(now, LIMA)
            WarningSweep(
                { organizations.having(Warnings.RULE) },
                { organization, block -> records.asPlatform(organization, SOURCE, block) },
                { evaluate(now, today) }
            ).run()
        }
    }

    private suspend fun evaluate(
        now: Instant,
        today: LocalDate
    ): Int {
        val rules =
            records.all(Warnings.RULE, filters = mapOf("enabled" to "true")).mapNotNull { row ->
                WarningEvaluation.parse(row.id, row.attributes).also {
                    if (it == null) log.warn("WarningRulesJob: la regla {} no se puede evaluar (tipo, umbral o ventana inválidos); se salta", row.id)
                }
            }
        if (rules.isEmpty()) return 0
        val projects = records.all(PROJECT, criteria = listOf(Criteria.inState(ACTIVE))).map { it.id }
        var created = 0
        for (project in projects) {
            for (rule in WarningEvaluation.applicable(rules, project)) created += evaluate(rule, project, now, today)
        }
        return created
    }

    private suspend fun evaluate(
        rule: WarningRule,
        project: String,
        now: Instant,
        today: LocalDate
    ): Int {
        if (rule.kind == Warnings.RISK_LEVEL) {
            return risks(project).filter { WarningEvaluation.fires(it.score, rule.threshold) }.count { create(rule, project, it.score, it, now, today) }
        }
        val value = measure(rule, project, now, today)
        return if (WarningEvaluation.fires(value, rule.threshold) && create(rule, project, value, null, now, today)) 1 else 0
    }

    // el valor medido de un tipo por eventos o de COMPROMISOS VENCIDOS, en el proyecto
    private suspend fun measure(
        rule: WarningRule,
        project: String,
        now: Instant,
        today: LocalDate
    ): Long {
        val start = WarningEvaluation.windowStart(today, rule.windowDays)
        val from = start.atStartOfDay(LIMA).toInstant()
        val until = now.plusSeconds(1)
        val inProject = mapOf("project" to project)
        return when (rule.kind) {
            Warnings.SIGNALS -> records.count(Warnings.SIGNAL, inProject, listOf(Criteria.between("received_at", from, until)))
            Warnings.QCS -> {
                val raised =
                    Attention.OBJECTS.flatMap { type ->
                        records
                            .all(type, inProject, listOf(Criteria.between(Attention.RECEIVED_AT, from, until)), max = MAX_ROWS)
                            .map { Raised(type, it.id, it.state, it.attributes) }
                    }
                AttentionCounting.counted(raised).size.toLong()
            }
            Warnings.OVERDUE_COMMITMENTS ->
                records.count(COMMITMENT, inProject + ("deadline_status" to OVERDUE), listOf(Criteria.inState(PENDING)))
            Warnings.NEGATIVE_MENTIONS ->
                records.count(Warnings.MENTION, inProject + ("tone" to NEGATIVE), listOf(Criteria.dateBetween("published_date", start, today)))
            else -> records.count(Warnings.INCIDENT, inProject, listOf(Criteria.between("occurred_at", from, until)))
        }
    }

    // los riesgos revisados del proyecto con su puntaje vigente
    private suspend fun risks(project: String): List<RiskHit> =
        records
            .all(Risks.SOCIAL_RISK, mapOf("project" to project), listOf(Criteria.inState(Risks.REVIEWED)), max = MAX_ROWS)
            .mapNotNull { row ->
                val a = row.attributes
                val score = Values.decimal(a[Risks.SCORE])?.toLong() ?: return@mapNotNull null
                RiskHit(row.id, Values.text(a["code"]), score, Values.text(a["locality"]), Values.text(a["community"]))
            }

    // crea la alerta si la regla no está en silencio; false si lo estaba o si otra réplica la creó primero
    private suspend fun create(
        rule: WarningRule,
        project: String,
        value: Long,
        risk: RiskHit?,
        now: Instant,
        today: LocalDate
    ): Boolean {
        if (silenced(rule, project, risk?.id, now, today)) return false
        return try {
            transactions.run { records.create(Warnings.WARNING, WarningEvaluation.alert(rule, project, value, risk, now, today), REASON) }
            true
        } catch (_: DuplicateKeyException) {
            false
        }
    }

    // en silencio: una alerta abierta (fuera de dismissed y closed) de la misma regla y proyecto (y riesgo), o una detectada
    // dentro de los window_days días de la regla (ASM-097)
    private suspend fun silenced(
        rule: WarningRule,
        project: String,
        risk: String?,
        now: Instant,
        today: LocalDate
    ): Boolean {
        val same = mapOf("project" to project, "early_warning_rule" to rule.id) + listOfNotNull(risk?.let { "social_risk" to it })
        val open = catalog.states(Warnings.WARNING) - Warnings.FINISHED
        if (records.count(Warnings.WARNING, same, listOf(Criteria.inState(open))) > 0) return true
        val from = WarningEvaluation.windowStart(today, rule.windowDays).atStartOfDay(LIMA).toInstant()
        return records.count(Warnings.WARNING, same, listOf(Criteria.between("detected_at", from, now.plus(Duration.ofDays(1))))) > 0
    }

    companion object {
        const val LOCK = "sgspe.warning"
        const val NAME = "JOB-04 WarningRulesJob"

        // la etiqueta de sus filas de auditoría (WAS-ADR-0050)
        const val SOURCE = "job:warning-rules"
        const val REASON = "alerta por regla ($NAME)"
        const val PROJECT = "project"
        const val COMMITMENT = "commitment"
        const val OVERDUE = "VENCIDO"
        const val NEGATIVE = "NEGATIVO"

        // los proyectos que se vigilan y los compromisos con plazo en curso (los estados de JOB-01, Commitments.PENDING)
        val ACTIVE: Set<String> = setOf("active", "suspended")
        val PENDING: Set<String> = setOf("approved", "in_progress", "blocked")

        // el tope de QCS o de riesgos de un proyecto que se leen en una regla: pasarse falla la organización, nunca recorta
        const val MAX_ROWS = 20_000
    }
}

// el reloj de JOB-04, cada hora en la zona de Lima (ASM-006); sgspe.jobs.enabled=false lo apaga (las pruebas llaman a runOnce)
@Component
@EnableScheduling
@ConditionalOnProperty(name = ["sgspe.jobs.enabled"], havingValue = "true", matchIfMissing = true)
class WarningRulesSchedule(
    private val job: WarningRulesJob
) {
    @Scheduled(cron = "\${sgspe.jobs.warning-rules.cron:0 25 * * * *}", zone = "America/Lima")
    suspend fun hourly() {
        job.runOnce()
    }
}
```

En `src/main/resources/application.yml`, reemplazar:

```yaml
      # JOB-01, cada hora (ASM-006)
      cron: '0 5 * * * *'
    retention:
      # JOB-08, una vez al día: borra las claves de idempotencia vencidas (wasichai.idempotency.ttl) y los saltos de
```

por:

```yaml
      # JOB-01, cada hora (ASM-006)
      cron: '0 5 * * * *'
    warning-rules:
      # JOB-04, cada hora (ASM-006, ASM-097): las alertas por regla
      cron: '0 25 * * * *'
    retention:
      # JOB-08, una vez al día: borra las claves de idempotencia vencidas (wasichai.idempotency.ttl) y los saltos de
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `WarningSweepTest` con 2 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/warning/WarningRulesJob.kt src/main/resources/application.yml \
  src/test/kotlin/sgspe/warning/WarningSweepTest.kt src/test/kotlin/sgspe/warning/WarningRulesJobApiTest.kt
git commit -F - <<'MSG'
feat: JOB-04 crea alertas por regla cada hora

WarningRulesJob evalúa las reglas habilitadas de cada proyecto activo, con la precedencia de la
regla del proyecto, y crea la alerta en detected salvo que una abierta o reciente la silencie;
nunca confirma ni aplica transiciones (RN-EWS-001, ASM-097).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.warning.WarningRulesJobApiTest` y `sgspe.workflow.AutomationsApiTest`.
Si «la alerta de JOB-04 dispara AUT-09 y warning_new» falla porque el alta de la plataforma no llega al listener o a la
automatización, no se mezcla la rama: se registra la diferencia de wasichai y JOB-04 publica `warning_new` con
`Notices.publish` (misma clave) y crea la tarea de triaje con `JobTasks.ensure` (clave `warning_triage:<id>`), como prevé
`workflows.md`.

---

### Tarea 13: KPI-ENG-06 a 08

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt` (`MentionRow` y las tres funciones),
  `src/main/kotlin/sgspe/dashboard/DashboardData.kt` (`stakeholdersOf`, `mentions`), `src/main/kotlin/sgspe/dashboard/Kpis.kt`
- Test: `src/test/kotlin/sgspe/dashboard/EngagementKpisF4Test.kt`, `src/test/kotlin/sgspe/dashboard/EngagementIndicatorsF4ApiTest.kt`

**Interfaces:**
- Consumes: el marco de tableros de F2 y F3 (`Kpi`, `IndicatorResult`, `Breakdown`, `Numbers`, `DashboardData.read`,
  `relatedOf`, `inPeriod`, `names`).
- Produces: `data class MentionRow(id, outlet, outletName, tone, topic, reportsFulfilled)` con `outletKey`;
  `EngagementKpis.MONITORING`, `TONES`, `monitoring(meetings, stakeholders)`, `fulfilledCommunications(mentions,
  outletNames)`, `tones(mentions, outletNames, topicNames)`; `DashboardData.stakeholdersOf(engagements)` y `mentions()`;
  KPI-ENG-06, 07 y 08 en `Kpis.ALL` sin tablero.

`dashboards.md` §4.3 y §11: #11, #12 y #13 de la Tabla 9.5, solo por `GET /api/sgspe/dashboards/indicators/{code}` hasta
F8. Decisión 9.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/EngagementKpisF4Test.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import java.math.BigDecimal
import java.time.LocalDate

// KPI-ENG-06 a 08 (dashboards.md §4.3, #11 a #13 de la Tabla 9.5), sin base
class EngagementKpisF4Test {
    private fun engagement(
        id: String,
        type: String
    ) = EngagementRow(id, "p", type, "Monitoreo $id", LocalDate.parse("2026-10-0${id.last()}"), emptyMap())

    private fun mention(
        id: String,
        tone: String?,
        outlet: String? = null,
        name: String? = null,
        topic: String? = null,
        fulfilled: Boolean = false
    ) = MentionRow(id, outlet, name, tone, topic, fulfilled)

    @Test
    fun `KPI-ENG-06 cuenta los monitoreos participativos con sus grupos de interes`() {
        val rows = listOf(engagement("e1", "MONITOREO PARTICIPATIVO"), engagement("e2", "REUNION"), engagement("e3", "MONITOREO PARTICIPATIVO"))
        val result = EngagementKpis.monitoring(rows, mapOf("e1" to listOf("g1", "g2", "g1")))
        assertEquals(2, result.value)
        assertEquals(
            listOf(2, 0),
            result.breakdowns
                .single()
                .rows
                .map { it["value"] }
        )
    }

    @Test
    fun `KPI-ENG-07 cuenta las menciones de compromisos cumplidos por medio`() {
        val mentions =
            listOf(
                mention("m1", "POSITIVO", outlet = "o1", fulfilled = true),
                mention("m2", "NEUTRO", name = " Radio Ejemplo ", fulfilled = true),
                mention("m3", "POSITIVO")
            )
        val result = EngagementKpis.fulfilledCommunications(mentions, mapOf("o1" to "Diario Ejemplo"))
        assertEquals(2, result.value)
        assertEquals(
            setOf("Diario Ejemplo", "Radio Ejemplo"),
            result.breakdowns
                .single()
                .rows
                .map { it["label"] }
                .toSet()
        )
    }

    @Test
    fun `KPI-ENG-08 da el porcentaje de menciones negativas y de medios por tono`() {
        val mentions =
            listOf(
                mention("m1", "NEGATIVO", name = "Radio Ejemplo"),
                mention("m2", "POSITIVO", name = "radio ejemplo"),
                mention("m3", "NEGATIVO", outlet = "o1", topic = "t1"),
                mention("m4", "NEUTRO", outlet = "o1", topic = "t1")
            )
        val result = EngagementKpis.tones(mentions, mapOf("o1" to "Diario Ejemplo"), mapOf("t1" to "Agua"))
        assertEquals(BigDecimal("50.0"), result.value)
        assertEquals(2, result.numerator)
        assertEquals(4, result.denominator)
        val tones =
            result.breakdowns
                .first { it.by == "tone" }
                .rows
                .associateBy { it["key"] }
        // los dos medios tienen una mención negativa; solo la radio, una positiva (el nombre escrito se normaliza)
        assertEquals(BigDecimal("100.0"), tones.getValue("NEGATIVO")["outletPercent"])
        assertEquals(BigDecimal("50.0"), tones.getValue("POSITIVO")["outletPercent"])
        assertEquals(
            2,
            result.breakdowns
                .first { it.by == "outlet" }
                .rows.size
        )
        assertEquals(
            "Agua",
            result.breakdowns
                .first { it.by == "topic" }
                .rows
                .first { it["key"] == "t1" }["label"]
        )
    }

    @Test
    fun `los KPI-ENG de F4 no tienen tablero, y los de medios ignoran el territorio`() {
        for (code in listOf("KPI-ENG-06", "KPI-ENG-07", "KPI-ENG-08")) assertEquals(null, Kpis.BY_CODE.getValue(code).dashboard, code)
        assertEquals(true, "locality" in Kpis.BY_CODE.getValue("KPI-ENG-08").ignores)
        assertEquals(false, "locality" in Kpis.BY_CODE.getValue("KPI-ENG-06").ignores)
        val catalog = ModelCatalog.fromClasspath()
        assertEquals(true, EngagementKpis.MONITORING in catalog.enumOf("engagement", "engagement_type"))
        assertEquals(EngagementKpis.TONES, catalog.enumOf("media_mention", "tone"))
    }
}
```

Crear `src/test/kotlin/sgspe/dashboard/EngagementIndicatorsF4ApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// KPI-ENG-06 a 08 por el endpoint de indicadores (dashboards.md §4.3 y §11, api.md §4.11): disponibles desde F4 sin zona en un
// tablero; las menciones cuentan por tono y por compromisos cumplidos, y su territorio se ignora y se dice
class EngagementIndicatorsF4ApiTest : F4ApiTest() {
    private fun indicator(
        code: String,
        extra: String = ""
    ): JsonNode =
        tree(
            send(
                "GET",
                "/api/sgspe/dashboards/indicators/$code?project=${testProject()}&periodStart=2026-10-01&periodEnd=2026-10-31$extra",
                null,
                HttpStatus.OK
            )
        )

    @Test
    fun `el catalogo los informa disponibles desde F4`() {
        val catalog = tree(send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK)).values().associateBy { it["code"].asString() }
        for (code in listOf("KPI-ENG-06", "KPI-ENG-07", "KPI-ENG-08")) assertTrue(catalog.getValue(code)["available"].asBoolean(), code)
    }

    @Test
    fun `las menciones cuentan por tono y por compromisos cumplidos`() {
        mediaMention(testProject(), attributes = mapOf("published_date" to "2026-10-06"))
        mediaMention(testProject(), attributes = mapOf("published_date" to "2026-10-07", "tone" to "POSITIVO", "reports_fulfilled_commitment" to true))
        mediaMention(testProject(), attributes = mapOf("published_date" to "2026-09-30"))
        assertEquals(1, indicator("KPI-ENG-07")["value"].asInt())
        val tones = indicator("KPI-ENG-08", "&locality=${java.util.UUID.randomUUID()}")
        assertEquals(50.0, tones["value"].asDouble())
        assertEquals(2, tones["denominator"].asInt())
        assertEquals(listOf("locality"), tones["ignoredFilters"].values().map { it.asString() })
        assertEquals(0, indicator("KPI-ENG-06")["value"].asInt())
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.EngagementKpisF4Test'
```

Esperado: FAIL en la compilación (`Unresolved reference 'MentionRow'`, `'monitoring'`, `'tones'`).

- [ ] **Paso 3: Los KPI, su lectura y su registro**

En `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt`, reemplazar:

```kotlin
    val counts: Map<String, Long?>
)

data class QuestionRow(
```

por:

```kotlin
    val counts: Map<String, Long?>
)

// una mención en medios del periodo (F4, KPI-ENG-07 y 08): su medio (la organización o, sin ella, el nombre escrito), su tono,
// su tema y si informa de un compromiso cumplido
data class MentionRow(
    val id: String,
    val outlet: String?,
    val outletName: String?,
    val tone: String?,
    val topic: String?,
    val reportsFulfilled: Boolean
) {
    // el medio: la organización, o el nombre escrito sin mayúsculas ni espacios de más; null si no tiene ninguno
    val outletKey: String? get() = outlet ?: outletName?.trim()?.lowercase()?.ifEmpty { null }
}

data class QuestionRow(
```

En `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt`, reemplazar:

```kotlin
    }

    // los 12 meses que terminan en el de `end`
    fun months(end: LocalDate): List<YearMonth> = (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }
```

por:

```kotlin
    }

    // ---- F4 (dashboards.md §4.3: #11, #12 y #13 de la Tabla 9.5, sin zona en un tablero hasta F8) ----

    const val MONITORING = "MONITOREO PARTICIPATIVO"
    val TONES: List<String> = listOf("POSITIVO", "NEGATIVO", "NEUTRO")
    const val NO_OUTLET = "SIN MEDIO"
    const val NO_TOPIC = "SIN TEMA"

    // KPI-ENG-06: los monitoreos participativos realizados en el periodo, cada uno con sus grupos de interés participantes (la
    // M2M engagement_stakeholder); el agente independiente y los resultados constan en sus evidencias
    fun monitoring(
        meetings: List<EngagementRow>,
        stakeholders: Map<String, List<String>>
    ): IndicatorResult {
        val rows =
            meetings.filter { it.type == MONITORING }.sortedBy { it.heldDate }.map { m ->
                mapOf("key" to m.id, "label" to m.title, "heldDate" to m.heldDate.toString(), "value" to stakeholders[m.id].orEmpty().distinct().size)
            }
        return IndicatorResult(rows.size, breakdowns = listOf(Breakdown("engagement", rows)))
    }

    // KPI-ENG-07: las menciones del periodo que informan de un compromiso cumplido, por medio
    fun fulfilledCommunications(
        mentions: List<MentionRow>,
        outletNames: Map<String, String>
    ): IndicatorResult {
        val reporting = mentions.filter { it.reportsFulfilled }
        return IndicatorResult(reporting.size, breakdowns = listOf(Breakdown("outlet", outlets(reporting, outletNames))))
    }

    // KPI-ENG-08: el porcentaje de menciones negativas del periodo (valor, con su numerador y denominador); por tono, el
    // porcentaje de menciones y el de medios con al menos una mención de ese tono sobre los medios con menciones; por medio y
    // por tema, las menciones de cada tono
    fun tones(
        mentions: List<MentionRow>,
        outletNames: Map<String, String>,
        topicNames: Map<String, String>
    ): IndicatorResult {
        val outlets = mentions.mapNotNull { it.outletKey }.toSet()
        val byTone =
            TONES.map { tone ->
                val ofTone = mentions.filter { it.tone == tone }
                val withTone = ofTone.mapNotNull { it.outletKey }.toSet()
                mapOf(
                    "key" to tone,
                    "value" to Numbers.percent(ofTone.size, mentions.size),
                    "mentions" to ofTone.size,
                    "outletPercent" to Numbers.percent(withTone.size, outlets.size),
                    "outlets" to withTone.size
                )
            }
        val byTopic =
            mentions.groupBy { it.topic }.map { (topic, list) ->
                mapOf("key" to (topic ?: NO_TOPIC), "label" to (topic?.let { topicNames[it] } ?: "Sin tema"), "value" to list.size) +
                    TONES.associateWith { tone -> list.count { it.tone == tone } }
            }
        val negative = mentions.count { it.tone == TONES[1] }
        return IndicatorResult(
            Numbers.percent(negative, mentions.size),
            negative,
            mentions.size,
            listOf(Breakdown("tone", byTone), Breakdown("outlet", outlets(mentions, outletNames)), Breakdown("topic", byTopic))
        )
    }

    // las menciones por medio, con las de cada tono, de más a menos
    private fun outlets(
        mentions: List<MentionRow>,
        names: Map<String, String>
    ): List<Map<String, Any?>> =
        mentions
            .groupBy { it.outletKey ?: NO_OUTLET }
            .map { (key, list) ->
                val label = list.first().outlet?.let { names[it] } ?: list.firstNotNullOfOrNull { it.outletName?.trim() } ?: "Sin medio"
                mapOf("key" to key, "label" to label, "value" to list.size) + TONES.associateWith { tone -> list.count { it.tone == tone } }
            }.sortedWith(compareByDescending<Map<String, Any?>> { it["value"] as Int }.thenBy { it["label"].toString() })

    // los 12 meses que terminan en el de `end`
    fun months(end: LocalDate): List<YearMonth> = (11 downTo 0).map { YearMonth.from(end).minusMonths(it.toLong()) }
```

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, reemplazar:

```kotlin
        relatedOf(engagements, "engagement_topic").mapValues { (_, topics) -> topics.map { it.id }.toSet() }

    // el campo `field` de cada registro, solo si quien mira puede leerlo; si no, ninguno (la etiqueta cae a otra cosa)
    suspend fun names(
```

por:

```kotlin
        relatedOf(engagements, "engagement_topic").mapValues { (_, topics) -> topics.map { it.id }.toSet() }

    // F4: los grupos de interés vinculados a cada interacción (engagement_stakeholder, KPI-ENG-06), hasta maxRelated interacciones
    suspend fun stakeholdersOf(engagements: Collection<String>): Map<String, List<String>> =
        relatedOf(engagements, "engagement_stakeholder").mapValues { (_, linked) -> linked.map { it.id } }

    // F4: las menciones en medios de los proyectos con published_date en el periodo (KPI-ENG-07 y 08). no tienen territorio: esos
    // KPI no aplican sus filtros y lo dicen en ignoredFilters
    suspend fun mentions(): List<MentionRow> =
        cached("mentions") {
            if (filters.projects.isEmpty()) return@cached emptyList<MentionRow>()
            read(
                "media_mention",
                criteria = listOf(Criteria.anyOf("project", filters.projects), Criteria.dateBetween("published_date", filters.periodStart, filters.periodEnd))
            ).map {
                val a = it.attributes
                MentionRow(
                    it.id,
                    Values.text(a["outlet"]),
                    Values.text(a["outlet_name"]),
                    Values.text(a["tone"]),
                    Values.text(a["topic"]),
                    Values.bool(a["reports_fulfilled_commitment"]) == true
                )
            }
        }

    // el campo `field` de cada registro, solo si quien mira puede leerlo; si no, ninguno (la etiqueta cae a otra cosa)
    suspend fun names(
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
        )
    private val HELD = listOf("engagement.held_at", "engagement.engagement_type")

    val ALL: List<Indicator> =
```

por:

```kotlin
        )
    private val HELD = listOf("engagement.held_at", "engagement.engagement_type")

    // F4: lo que leen KPI-ENG-07 y 08, y los filtros que no aplican: las menciones no tienen territorio ni ámbito (dashboards.md
    // §4.3: «Proyecto, periodo»)
    private val MEDIA =
        listOf("media_mention.project", "media_mention.published_date", "media_mention.outlet", "media_mention.outlet_name", "media_mention.tone")
    private val MEDIA_IGNORES: Set<String> = IGNORED_IN_F2 + setOf("administrativeUnit", "locality", "community", "areaOfInfluence", "scope")

    val ALL: List<Indicator> =
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
                val questions = data.questions(meetings.map { it.id })
                EngagementKpis.answered(meetings, questions, data.names("topic", questions.mapNotNull { it.topic }))
            },
            // ---- F3: compromisos (dashboards.md §4.4); cada uno aplica los filtros de su fila y declara los demás ----
```

por:

```kotlin
                val questions = data.questions(meetings.map { it.id })
                EngagementKpis.answered(meetings, questions, data.names("topic", questions.mapNotNull { it.topic }))
            },
            // ---- F4: relacionamiento (dashboards.md §4.3, #11 a #13 de la Tabla 9.5): sin zona en un tablero hasta F8 (§11) ----
            Kpi(
                "KPI-ENG-06",
                "Monitoreos participativos de agua",
                null,
                "monitoreos",
                HELD + listOf("engagement.title", "stakeholder.project"),
                ignores = IGNORED_IN_F2
            ) { data ->
                val monitoring = data.inPeriod().filter { it.type == EngagementKpis.MONITORING }
                EngagementKpis.monitoring(monitoring, data.stakeholdersOf(monitoring.map { it.id }))
            },
            Kpi(
                "KPI-ENG-07",
                "Comunicados sobre compromisos cumplidos",
                null,
                "menciones",
                MEDIA + listOf("media_mention.reports_fulfilled_commitment", "organization.name"),
                ignores = MEDIA_IGNORES
            ) { data ->
                val mentions = data.mentions()
                EngagementKpis.fulfilledCommunications(mentions, data.names("organization", mentions.mapNotNull { it.outlet }))
            },
            Kpi(
                "KPI-ENG-08",
                "Medios con información positiva y negativa",
                null,
                "%",
                MEDIA + listOf("media_mention.topic", "organization.name", "topic.name"),
                ignores = MEDIA_IGNORES
            ) { data ->
                val mentions = data.mentions()
                EngagementKpis.tones(
                    mentions,
                    data.names("organization", mentions.mapNotNull { it.outlet }),
                    data.names("topic", mentions.mapNotNull { it.topic })
                )
            },
            // ---- F3: compromisos (dashboards.md §4.4); cada uno aplica los filtros de su fila y declara los demás ----
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `EngagementKpisF4Test` con 4 pruebas, y `EngagementKpisTest` y `DashboardDataTest` sin
cambios, en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/dashboard/EngagementKpis.kt src/main/kotlin/sgspe/dashboard/DashboardData.kt src/main/kotlin/sgspe/dashboard/Kpis.kt \
  src/test/kotlin/sgspe/dashboard/EngagementKpisF4Test.kt src/test/kotlin/sgspe/dashboard/EngagementIndicatorsF4ApiTest.kt
git commit -F - <<'MSG'
feat: KPI-ENG-06 a 08 de monitoreos participativos y medios

Los indicadores #11 a #13 de la Tabla 9.5: monitoreos participativos con sus grupos de interés,
comunicados sobre compromisos cumplidos y menciones por tono y por medio, solo por el endpoint de
indicadores hasta F8.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.dashboard.EngagementIndicatorsF4ApiTest` y `sgspe.dashboard.DashboardsApiTest`.

---

### Tarea 14: Los KPI-RSK y el tablero `risks`

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/RiskKpis.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/DashboardData.kt` (`risks`, `riskPoints`, `measures`),
  `src/main/kotlin/sgspe/dashboard/Kpis.kt` (`RISKS`, KPI-RSK-01 a 05)
- Test: `src/test/kotlin/sgspe/dashboard/RiskKpisTest.kt`, `src/test/kotlin/sgspe/dashboard/RisksDashboardApiTest.kt`

**Interfaces:**
- Consumes: `RiskThresholds`, `RiskLevels`, `Risks` (Tarea 1); lo que la Tarea 13 dejó en `DashboardData.kt` y
  `Kpis.kt`.
- Produces: `data class RiskRow`, `RiskPoint`, `MeasureRow`; `object RiskKpis` (`high`, `heatmap`, `distribution`,
  `weeks`, `evolution`, `mitigation`); `DashboardData.risks()`, `riskPoints(risks)`, `measures(risks)`; `Kpis.RISKS =
  "risks"` y `GET /api/sgspe/dashboards/risks` con KPI-RSK-02, 01, 03, 04 y 05.

`dashboards.md` §4.6 y §5.8. Los riesgos activos son todos salvo `closed` (§3.2); el territorio y el ámbito filtran por la
localidad o la comunidad del riesgo, como los compromisos; la campaña y el responsable no aplican. KPI-RSK-02 da el nivel
de cada celda solo con un proyecto (§7). Decisión 10.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/RiskKpisTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.risk.RiskThresholds
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-RSK (dashboards.md §4.6), sin base
class RiskKpisTest {
    private fun risk(
        id: String,
        probability: Int? = null,
        impact: Int? = null,
        level: String? = null,
        category: String? = "AMBIENTAL",
        locality: String? = "loc-1"
    ) = RiskRow(id, "p1", "RS-$id", category, probability, impact, level, null, null, null, locality, null, null)

    private fun date(text: String) = LocalDate.parse(text)

    @Test
    fun `KPI-RSK-01 cuenta los riesgos altos y criticos por categoria`() {
        val rows = listOf(risk("1", level = "ALTO"), risk("2", level = "CRITICO", category = "OTRO"), risk("3", level = "MEDIO"))
        val result = RiskKpis.high(rows)
        assertEquals(2, result.value)
        assertEquals(
            setOf("AMBIENTAL", "OTRO"),
            result.breakdowns
                .single()
                .rows
                .map { it["key"] }
                .toSet()
        )
    }

    @Test
    fun `KPI-RSK-02 llena la matriz 5 x 5, aparta los sin evaluar y con un proyecto da el nivel de cada celda`() {
        val rows = listOf(risk("1", 3, 4, "ALTO"), risk("2", 3, 4, "ALTO"), risk("3"))
        val result = RiskKpis.heatmap(rows, RiskThresholds(5, 10, 15))
        assertEquals(2, result.value)
        val matrix = result.breakdowns.first { it.by == "matrix" }.rows
        assertEquals(25, matrix.size)
        assertEquals(2, matrix.first { it["key"] == "3-4" }["value"])
        assertEquals("CRITICO", matrix.first { it["key"] == "5-5" }["level"])
        assertEquals(
            1,
            result.breakdowns
                .first { it.by == "assessment" }
                .rows
                .single()["value"]
        )
        assertTrue(
            RiskKpis
                .heatmap(rows, null)
                .breakdowns
                .first()
                .rows
                .none { "level" in it }
        )
    }

    @Test
    fun `KPI-RSK-03 apila por nivel cada territorio`() {
        val rows = listOf(risk("1", level = "ALTO"), risk("2", level = "ALTO"), risk("3", locality = "loc-2"))
        val locality =
            RiskKpis
                .distribution(rows, mapOf("loc-1" to "Localidad Ejemplo"))
                .breakdowns
                .first { it.by == "locality" }
                .rows
        assertEquals("Localidad Ejemplo", locality.first()["label"])
        assertEquals(2, locality.first()["ALTO"])
        assertEquals(1, locality.last()["SIN NIVEL"])
    }

    @Test
    fun `KPI-RSK-04 da doce semanas con el nivel de cada riesgo activo y las tendencias`() {
        val end = date("2026-10-09")
        val points =
            listOf(
                RiskPoint("1", date("2026-09-01"), false, "MEDIO", null, 1),
                RiskPoint("1", date("2026-10-06"), false, "ALTO", "AUMENTA", 2),
                RiskPoint("1", date("2026-10-07"), true, "BAJO", null, 3),
                RiskPoint("9", date("2026-10-06"), false, "CRITICO", null, 4)
            )
        val result = RiskKpis.evolution(listOf(risk("1")), points, end)
        val series = result.series!!
        assertEquals(12, series.size)
        assertEquals("2026-10-05", series.last()["period"])
        assertEquals(1, series.last()["ALTO"])
        assertEquals(1, series.last()["AUMENTA"])
        assertEquals(1, series.first { it["period"] == "2026-08-31" }["MEDIO"])
        // el riesgo 9 no está activo en el filtro: no cuenta
        assertEquals(0, series.last()["CRITICO"])
        assertEquals(3, result.value)
    }

    @Test
    fun `KPI-RSK-05 es el porcentaje de medidas completadas y lista las atrasadas`() {
        val measures =
            listOf(
                MeasureRow("m1", "1", "Mesa de trabajo", "a@sgspe.test", date("2026-10-01"), false),
                MeasureRow("m2", "1", "Taller", "a@sgspe.test", date("2026-10-01"), true),
                MeasureRow("m3", "1", "Visita", null, date("2026-12-01"), false)
            )
        val result = RiskKpis.mitigation(measures, mapOf("1" to "RS-1"), date("2026-10-09"))
        assertEquals(BigDecimal("33.3"), result.value)
        val late = result.breakdowns.first { it.by == "overdue" }.rows
        assertEquals(listOf("m1"), late.map { it["key"] })
        assertEquals("RS-1", late.single()["label"])
        assertEquals(
            setOf("a@sgspe.test", "SIN RESPONSABLE"),
            result.breakdowns
                .first { it.by == "owner" }
                .rows
                .map { it["key"] }
                .toSet()
        )
    }

    @Test
    fun `el tablero risks trae los cinco KPI-RSK en el orden de sus zonas`() {
        assertEquals(listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05"), Kpis.DASHBOARDS.getValue("risks"))
        assertTrue((1..5).all { Kpis.BY_CODE.getValue("KPI-RSK-%02d".format(it)).dashboard == "risks" })
        assertTrue((1..5).none { Kpis.confidentialSensitive("KPI-RSK-%02d".format(it)) })
    }
}
```

Crear `src/test/kotlin/sgspe/dashboard/RisksDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest
import tools.jackson.databind.JsonNode

// el tablero risks (dashboards.md §5.8 y §11, api.md §4.11): los cinco KPI-RSK en el orden de sus zonas, disponibles desde F4,
// sobre los riesgos activos y sin la marca de las QCS confidenciales
class RisksDashboardApiTest : F4ApiTest() {
    private fun dashboard(): JsonNode =
        tree(send("GET", "/api/sgspe/dashboards/risks?project=${testProject()}&periodStart=2026-10-01&periodEnd=2026-10-31", null, HttpStatus.OK))

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    @Test
    fun `el tablero cuenta los riesgos activos con su ultima revision`() {
        val reviewer = member("SOCIAL_RISK_MANAGER")
        val high = socialRisk(testProject())
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$high/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-10-05", "probability" to 3, "impact" to 4)),
                reviewer.token,
                withReason("revisión")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        mitigationMeasure(high, reviewer)
        val closed = socialRisk(testProject())
        move("social_risk", closed, "close_identified", reviewer.token, "el riesgo dejó de existir")
        socialRisk(testProject())
        val body = dashboard()
        assertEquals(listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05"), body["indicators"].values().map { it["code"].asString() })
        assertEquals(1, indicator(body, "KPI-RSK-01")["value"].asInt())
        val heatmap = indicator(body, "KPI-RSK-02")
        assertEquals(1, heatmap["value"].asInt())
        val cell =
            heatmap["breakdowns"]
                .values()
                .first { it["by"].asString() == "matrix" }["rows"]
                .values()
                .first { it["key"].asString() == "3-4" }
        assertEquals(1, cell["value"].asInt())
        assertEquals("ALTO", cell["level"].asString())
        assertEquals(1, heatmap["breakdowns"].values().first { it["by"].asString() == "assessment" }["rows"][0]["value"].asInt())
        assertEquals(0.0, indicator(body, "KPI-RSK-05")["value"].asDouble())
        assertFalse(body.has("confidentialHidden"))
        assertFalse(body["indicators"].values().any { it.has("confidentialHidden") })
    }

    @Test
    fun `el catalogo informa los KPI-RSK disponibles desde F4`() {
        val catalog =
            tree(
                send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK)
            ).values().filter { it["code"].asString().startsWith("KPI-RSK-") }
        assertEquals(5, catalog.size)
        assertTrue(catalog.all { it["available"].asBoolean() && it["dashboard"].asString() == "risks" })
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.RiskKpisTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'RiskRow'`, `'RiskKpis'`, `'RiskPoint'` y `'MeasureRow'`).

- [ ] **Paso 3: Los KPI-RSK, su lectura y el tablero**

Crear `src/main/kotlin/sgspe/dashboard/RiskKpis.kt`:

```kotlin
package sgspe.dashboard

import sgspe.risk.RiskLevels
import sgspe.risk.RiskThresholds
import sgspe.risk.Risks
import java.time.DayOfWeek
import java.time.LocalDate

// un riesgo social como lo leen los KPI-RSK, ya acotado a los proyectos, al territorio y a los activos (todos salvo closed,
// dashboards.md §3.2). los valores vigentes y residuales son los de su última revisión
data class RiskRow(
    val id: String,
    val project: String?,
    val code: String?,
    val category: String?,
    val probability: Int?,
    val impact: Int?,
    val level: String?,
    val residualProbability: Int?,
    val residualImpact: Int?,
    val residualLevel: String?,
    val locality: String?,
    val community: String?,
    val areaOfInfluence: String?
)

// una revisión de un riesgo activo (risk_assessment, appendOnly): la historia de KPI-RSK-04
data class RiskPoint(
    val risk: String,
    val date: LocalDate,
    val residual: Boolean,
    val level: String?,
    val trend: String?,
    val order: Long
)

// una medida de mitigación de un riesgo activo (KPI-RSK-05)
data class MeasureRow(
    val id: String,
    val risk: String,
    val description: String?,
    val ownerEmail: String?,
    val planned: LocalDate?,
    val completed: Boolean
)

// los KPI-RSK (dashboards.md §4.6) como funciones puras. las filas llegan leídas como quien mira
object RiskKpis {
    const val NO_LEVEL = "SIN NIVEL"
    const val NO_DATA = "SIN DATO"
    const val UNASSESSED = "SIN EVALUAR"
    const val NO_OWNER = "SIN RESPONSABLE"
    const val WEEKS = 12L

    // KPI-RSK-01: riesgos activos de nivel ALTO o CRITICO, por categoría
    fun high(rows: List<RiskRow>): IndicatorResult {
        val high = rows.filter { it.level in Risks.ESCALATED }
        return IndicatorResult(high.size, breakdowns = listOf(Breakdown("category", Numbers.counts(high.map { it.category ?: NO_DATA }))))
    }

    // KPI-RSK-02: la matriz 5 × 5 de los riesgos activos por probabilidad e impacto de su última revisión y su vista residual; con
    // un solo proyecto (`thresholds`), cada celda lleva el nivel que da su puntaje. los riesgos sin revisión van aparte
    fun heatmap(
        rows: List<RiskRow>,
        thresholds: RiskThresholds?
    ): IndicatorResult {
        fun matrix(
            by: String,
            cell: (RiskRow) -> Pair<Int?, Int?>
        ): Breakdown {
            val counts =
                rows
                    .map(cell)
                    .filter { it.first != null && it.second != null }
                    .groupingBy { it }
                    .eachCount()
            val cells =
                (1..5).flatMap { p ->
                    (1..5).map { i ->
                        mapOf("key" to "$p-$i", "probability" to p, "impact" to i, "value" to (counts[p to i] ?: 0)) +
                            (thresholds?.let { mapOf("level" to it.level(p * i)) } ?: emptyMap())
                    }
                }
            return Breakdown(by, cells)
        }
        val assessed = rows.count { it.probability != null && it.impact != null }
        return IndicatorResult(
            assessed,
            breakdowns =
                listOf(
                    matrix("matrix") { it.probability to it.impact },
                    matrix("residual") { it.residualProbability to it.residualImpact },
                    Breakdown("assessment", listOf(mapOf("key" to UNASSESSED, "value" to rows.size - assessed)))
                )
        )
    }

    // KPI-RSK-03: riesgos activos por localidad, comunidad, área de influencia y proyecto, cada fila apilada por nivel
    fun distribution(
        rows: List<RiskRow>,
        names: Map<String, String>
    ): IndicatorResult {
        fun stacked(
            by: String,
            key: (RiskRow) -> String?
        ): Breakdown {
            val groups = rows.filter { key(it) != null }.groupBy { key(it)!! }
            val stack =
                groups
                    .map { (id, list) ->
                        mapOf("key" to id, "label" to (names[id] ?: id), "value" to list.size) +
                            (Risks.LEVELS + NO_LEVEL).associateWith { level -> list.count { (it.level ?: NO_LEVEL) == level } }
                    }.sortedWith(compareByDescending<Map<String, Any?>> { it["value"] as Int }.thenBy { it["label"].toString() })
            return Breakdown(by, stack)
        }
        return IndicatorResult(
            rows.size,
            breakdowns =
                listOf(
                    stacked("locality") { it.locality },
                    stacked("community") { it.community },
                    stacked("areaOfInfluence") { it.areaOfInfluence },
                    stacked("project") { it.project }
                )
        )
    }

    // las WEEKS semanas (de lunes a domingo) que terminan en la de `end`, por su lunes
    fun weeks(end: LocalDate): List<LocalDate> {
        val last = end.with(DayOfWeek.MONDAY)
        return (WEEKS - 1 downTo 0).map { last.minusWeeks(it) }
    }

    // KPI-RSK-04: por semana, la distribución por nivel de los riesgos hoy activos según su última revisión no residual hasta el
    // fin de esa semana, y las revisiones de la semana por tendencia. el valor son las revisiones de las doce semanas
    fun evolution(
        active: List<RiskRow>,
        points: List<RiskPoint>,
        end: LocalDate
    ): IndicatorResult {
        val ids = active.map { it.id }.toSet()
        val mine = points.filter { it.risk in ids }
        val weeks = weeks(end)
        val series =
            weeks.map { monday ->
                val sunday = monday.plusDays(6)
                val levels =
                    mine
                        .filter { !it.residual && !it.date.isAfter(sunday) }
                        .groupBy { it.risk }
                        .mapValues { (_, list) -> list.maxWith(compareBy({ it.date }, { it.order })).level }
                val inWeek = mine.filter { !it.date.isBefore(monday) && !it.date.isAfter(sunday) }
                mapOf<String, Any?>("period" to monday.toString()) +
                    Risks.LEVELS.associateWith { level -> levels.values.count { it == level } } +
                    listOf(RiskLevels.UP, RiskLevels.SAME, RiskLevels.DOWN).associateWith { trend -> inWeek.count { it.trend == trend } }
            }
        val total = mine.count { !it.date.isBefore(weeks.first()) && !it.date.isAfter(weeks.last().plusDays(6)) }
        return IndicatorResult(total, series = series)
    }

    // KPI-RSK-05: medidas completadas ÷ medidas de los riesgos activos; aparte, las atrasadas (sin completar y con planned_date
    // anterior a hoy), y el avance por riesgo y por responsable
    fun mitigation(
        measures: List<MeasureRow>,
        codes: Map<String, String?>,
        today: LocalDate
    ): IndicatorResult {
        val done = measures.count { it.completed }
        val late =
            measures.filter { !it.completed && it.planned != null && it.planned.isBefore(today) }.sortedBy { it.planned }.map {
                mapOf(
                    "key" to it.id,
                    "label" to (codes[it.risk] ?: it.risk),
                    "description" to it.description,
                    "owner" to it.ownerEmail,
                    "plannedDate" to it.planned.toString()
                )
            }

        fun progress(
            by: String,
            key: (MeasureRow) -> String,
            label: (String) -> String
        ) = Breakdown(
            by,
            measures.groupBy(key).map { (id, list) ->
                mapOf("key" to id, "label" to label(id), "value" to list.count { it.completed }, "total" to list.size)
            }
        )
        return IndicatorResult(
            Numbers.percent(done, measures.size),
            done,
            measures.size,
            listOf(
                Breakdown("overdue", late),
                progress("risk", { it.risk }) { codes[it] ?: it },
                progress("owner", { it.ownerEmail ?: NO_OWNER }) { it }
            )
        )
    }
}
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

    // ---- riesgos (F4) ----

    // los riesgos activos (todos salvo closed, dashboards.md §3.2) de los proyectos, filtrados por territorio y ámbito como los
    // compromisos (su localidad o su comunidad)
    suspend fun risks(): List<RiskRow> =
        cached("risks") {
            if (filters.projects.isEmpty()) return@cached emptyList<RiskRow>()
            val territories = engagementTerritories()
            val active = Criteria.inState(setOf("identified", "assessed", "mitigating", "monitoring"))
            read("social_risk", criteria = listOf(Criteria.anyOf("project", filters.projects), active))
                .filter { r ->
                    val locality = Values.text(r.attributes["locality"])
                    val community = Values.text(r.attributes["community"])
                    territories.all { t -> (locality != null && locality in t.localities) || (community != null && community in t.communities) }
                }.map {
                    val a = it.attributes

                    fun int(name: String): Int? = Values.decimal(a[name])?.toInt()
                    RiskRow(
                        it.id,
                        Values.text(a["project"]),
                        Values.text(a["code"]),
                        Values.text(a["category"]),
                        int("probability"),
                        int("impact"),
                        Values.text(a["risk_level"]),
                        int("residual_probability"),
                        int("residual_impact"),
                        Values.text(a["residual_level"]),
                        Values.text(a["locality"]),
                        Values.text(a["community"]),
                        Values.text(a["area_of_influence"])
                    )
                }
        }

    // las revisiones de esos riesgos (KPI-RSK-04)
    suspend fun riskPoints(risks: Collection<String>): List<RiskPoint> =
        readIn("risk_assessment", "social_risk", risks).mapNotNull { r ->
            val a = r.attributes
            RiskPoint(
                Values.text(a["social_risk"]) ?: return@mapNotNull null,
                Values.text(a["assessment_date"])?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null,
                Values.bool(a["residual"]) == true,
                Values.text(a["risk_level"]),
                Values.text(a["trend"]),
                r.createdAt?.toEpochMilli() ?: 0
            )
        }

    // las medidas de mitigación de esos riesgos (KPI-RSK-05)
    suspend fun measures(risks: Collection<String>): List<MeasureRow> =
        readIn("mitigation_measure", "social_risk", risks).mapNotNull { m ->
            val a = m.attributes
            MeasureRow(
                m.id,
                Values.text(a["social_risk"]) ?: return@mapNotNull null,
                Values.text(a["description"]),
                Values.text(a["owner_user_email"]),
                Values.text(a["planned_date"])?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                Values.bool(a["completed"]) == true
            )
        }

    // la moneda de la campaña pedida (PEN si la campaña no la trae, RNF-I18N-002); null si no se pidió una campaña o si la
    // pedida no existe o no se lee: quien llama lo distingue por filters.explorationCampaign
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
import sgspe.common.Values
import sgspe.engagement.AttendanceCounts

// un KPI de dashboards.md §4. requires: "objeto.campo" que lee; si alguno no es legible para quien llama, el indicador
```

por:

```kotlin
import sgspe.common.Values
import sgspe.engagement.AttendanceCounts
import sgspe.risk.RiskLevels

// un KPI de dashboards.md §4. requires: "objeto.campo" que lee; si alguno no es legible para quien llama, el indicador
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
    const val COMMITMENTS = "commitments"
    const val ATTENTION = "attention"

    // los indicadores que leen QCS y que, a quien no ve las confidenciales, se lo dicen con confidentialHidden (dashboards.md §8,
```

por:

```kotlin
    const val COMMITMENTS = "commitments"
    const val ATTENTION = "attention"
    const val RISKS = "risks"

    // los indicadores que leen QCS y que, a quien no ve las confidenciales, se lo dicen con confidentialHidden (dashboards.md §8,
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
                ),
            // dashboards.md §5.7: flujo, absueltas, desgloses, tendencia y tiempos
            ATTENTION to (1..10).map { "KPI-GRV-%02d".format(it) }
        )

    // los KPI-GRV no aplican la campaña ni el responsable de un compromiso (dashboards.md §3.1); el territorio y el ámbito sí,
```

por:

```kotlin
                ),
            // dashboards.md §5.7: flujo, absueltas, desgloses, tendencia y tiempos
            ATTENTION to (1..10).map { "KPI-GRV-%02d".format(it) },
            // dashboards.md §5.8: mapa de calor, riesgos altos, distribución, evolución y mitigación
            RISKS to listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05")
        )

    // los KPI-RSK aplican el territorio y el ámbito por la localidad o la comunidad del riesgo; la campaña y el responsable no
    private val RISK_IGNORES: Set<String> = setOf("explorationCampaign", "owner")

    // lo que leen todos los KPI-RSK: el riesgo, su estado y su territorio
    private val RISK = listOf("social_risk.project", "social_risk.locality", "social_risk.community")

    // los KPI-GRV no aplican la campaña ni el responsable de un compromiso (dashboards.md §3.1); el territorio y el ámbito sí,
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
                QCS + qcs("responded_at") + listOf("grievance.resolved_date", "grievance.resolution_due_date", "inquiry.response_due_date"),
                ignores = ATTENTION_IGNORES
            ) { AttentionKpis.attentionTime(it.attention(), it.filters.periodStart, it.filters.periodEnd) }
        )

```

por:

```kotlin
                QCS + qcs("responded_at") + listOf("grievance.resolved_date", "grievance.resolution_due_date", "inquiry.response_due_date"),
                ignores = ATTENTION_IGNORES
            ) { AttentionKpis.attentionTime(it.attention(), it.filters.periodStart, it.filters.periodEnd) },
            // ---- F4: riesgos (dashboards.md §4.6); leen como quien mira, los riesgos activos ----
            Kpi("KPI-RSK-01", "Riesgos altos", RISKS, "riesgos", RISK + listOf("social_risk.risk_level", "social_risk.category"), ignores = RISK_IGNORES) {
                RiskKpis.high(it.risks())
            },
            Kpi(
                "KPI-RSK-02",
                "Mapa de calor probabilidad × impacto",
                RISKS,
                "riesgos",
                RISK +
                    listOf(
                        "social_risk.probability",
                        "social_risk.impact",
                        "social_risk.residual_probability",
                        "social_risk.residual_impact",
                        "project.risk_medium_min_score",
                        "project.risk_high_min_score",
                        "project.risk_critical_min_score"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                // el nivel de cada celda solo con un proyecto: con varios, los umbrales pueden diferir (dashboards.md §7)
                val single = if (data.filters.projects.size == 1) data.projectRecords().singleOrNull() else null
                RiskKpis.heatmap(data.risks(), single?.let { RiskLevels.thresholds(it.attributes) })
            },
            Kpi(
                "KPI-RSK-03",
                "Riesgos por territorio y por proyecto",
                RISKS,
                "riesgos",
                RISK +
                    listOf(
                        "social_risk.area_of_influence",
                        "social_risk.risk_level",
                        "project.code",
                        "locality.name",
                        "community.name",
                        "area_of_influence.area_type"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                val rows = data.risks()
                val names =
                    data.names("locality", rows.mapNotNull { it.locality }) + data.names("community", rows.mapNotNull { it.community }) +
                        data.names("area_of_influence", rows.mapNotNull { it.areaOfInfluence }, "area_type") +
                        data.names("project", rows.mapNotNull { it.project }, "code")
                RiskKpis.distribution(rows, names)
            },
            Kpi(
                "KPI-RSK-04",
                "Evolución de los riesgos",
                RISKS,
                "revisiones",
                RISK +
                    listOf(
                        "risk_assessment.social_risk",
                        "risk_assessment.assessment_date",
                        "risk_assessment.risk_level",
                        "risk_assessment.trend",
                        "risk_assessment.residual"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                val rows = data.risks()
                RiskKpis.evolution(rows, data.riskPoints(rows.map { it.id }), data.filters.periodEnd)
            },
            Kpi(
                "KPI-RSK-05",
                "Avance de la mitigación",
                RISKS,
                "%",
                RISK +
                    listOf(
                        "social_risk.code",
                        "mitigation_measure.social_risk",
                        "mitigation_measure.completed",
                        "mitigation_measure.planned_date",
                        "mitigation_measure.owner_user_email",
                        "mitigation_measure.description"
                    ),
                ignores = RISK_IGNORES
            ) { data ->
                val rows = data.risks()
                RiskKpis.mitigation(data.measures(rows.map { it.id }), rows.associate { it.id to it.code }, data.today)
            }
        )

```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `RiskKpisTest` con 6 pruebas y `AttentionKpisTest` sin cambios en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/dashboard/RiskKpis.kt src/main/kotlin/sgspe/dashboard/DashboardData.kt src/main/kotlin/sgspe/dashboard/Kpis.kt \
  src/test/kotlin/sgspe/dashboard/RiskKpisTest.kt src/test/kotlin/sgspe/dashboard/RisksDashboardApiTest.kt
git commit -F - <<'MSG'
feat: KPI-RSK y tablero de riesgos

Riesgos altos, mapa de calor 5 por 5 con su vista residual, distribución por territorio y
proyecto, evolución semanal y avance de la mitigación, sobre los riesgos activos y como quien
mira (dashboards.md §4.6 y §5.8).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.dashboard.RisksDashboardApiTest`.

---

### Tarea 15: La exportación `risk-register`

**Files:**
- Create: `src/main/kotlin/sgspe/privacy/RiskSheets.kt`, `src/main/kotlin/sgspe/privacy/RiskRegister.kt`
- Modify: `src/main/kotlin/sgspe/privacy/ExportController.kt`
- Test: `src/test/kotlin/sgspe/privacy/RiskSheetsTest.kt`, `src/test/kotlin/sgspe/privacy/RiskRegisterApiTest.kt`

**Interfaces:**
- Consumes: `ExportProjects`, `ExportLog`, `ExportFile`, `Xlsx`, `XlsxSheet`, `SmallCells`, `Disclosure`, `NOT_AVAILABLE`,
  `RegisterSheets.code` (F2 y F3), `Readability`, `Criteria.textIn`; `sgspe.risk.Risks` solo en la prueba (privacy solo
  depende de common).
- Produces: `class RiskLine`, `data class MeasureCount`, `class RiskContext`; `object RiskSheets` (`LEVELS`, `REVIEWED`,
  `FREE_TEXT`, `EMAILS`, `RISK_FIELDS`, `ASSESSMENT_FIELDS`, `MEASURE_FIELDS`, `IMPACT_FIELDS`, `LINKED`, `REQUIRES`,
  `allowed(path, disclosure)`, `matrix`, `assessments`, `measures`, `impacts`, `aggregates`); el servicio
  `RiskRegister.export(body)`; `POST /api/sgspe/exports/risk-register`.

`api.md` §4.13, `deliverables.md` §10 y la especificación §5.2: INTERNO con cuatro hojas; EXTERNO sin texto libre ni correos,
con los grupos de interés por código `GI-` y las medidas contadas en la «Matriz»; PUBLICO con los riesgos activos sobre la
grilla completa y `SmallCells` (decisión 11). `export_log` cuenta riesgos.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/privacy/RiskSheetsTest.kt`:

```kotlin
package sgspe.privacy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.risk.Risks

// las hojas de risk-register por nivel (deliverables.md §10, ASM-101), sin base
class RiskSheetsTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val all = RiskSheets.REQUIRES.toSet()

    private fun line(
        state: String = "assessed",
        attributes: Map<String, Any?> = emptyMap()
    ) = RiskLine(
        "r1",
        state,
        mapOf(
            "code" to "RS-1",
            "title" to "Expectativas de empleo",
            "risk_source" to "Texto libre de la fuente",
            "risk_level" to "ALTO",
            "review_status" to "REVISION VENCIDA",
            "owner_user_email" to "responsable@sgspe.test",
            "locality" to "loc"
        ) + attributes,
        listOf("8f14e45f-ceea-467a-9575-000000000001" to "Asociación Ejemplo"),
        "2026-10-05",
        "AUMENTA",
        MeasureCount(3, 1, 1)
    )

    private fun cells(sheet: XlsxSheet): List<String> = sheet.header + sheet.rows.flatten().map { it.toString() }

    @Test
    fun `INTERNO nombra a los grupos de interes y trae el texto libre y el responsable`() {
        val context = RiskContext(all, mapOf("loc" to "Localidad Ejemplo"))
        val sheet = RiskSheets.matrix(listOf(line()), context, Disclosure.INTERNO)
        val values = cells(sheet)
        assertTrue("Asociación Ejemplo" in values)
        assertTrue("Texto libre de la fuente" in values)
        assertTrue("responsable@sgspe.test" in values)
        assertTrue("Localidad Ejemplo" in values)
        assertFalse("Medidas" in sheet.header)
    }

    @Test
    fun `EXTERNO quita el texto libre y los correos, cita por GI- y cuenta las medidas`() {
        val readable = all.filter { RiskSheets.allowed(it, Disclosure.EXTERNO) }.toSet()
        val sheet = RiskSheets.matrix(listOf(line()), RiskContext(readable), Disclosure.EXTERNO)
        val values = cells(sheet)
        assertFalse("Texto libre de la fuente" in values)
        assertFalse("responsable@sgspe.test" in values)
        assertFalse("Asociación Ejemplo" in values)
        assertTrue(values.any { it.startsWith("GI-") })
        assertTrue(sheet.header.containsAll(listOf("Medidas", "Medidas completadas", "Medidas atrasadas")))
        assertFalse(RiskSheets.allowed("risk_assessment.notes", Disclosure.EXTERNO))
        assertFalse(RiskSheets.allowed("potential_impact.description", Disclosure.EXTERNO))
    }

    @Test
    fun `la situacion de revision solo sale en los estados que la calculan`() {
        val closed = cells(RiskSheets.matrix(listOf(line(state = "closed")), RiskContext(all), Disclosure.INTERNO))
        assertFalse("REVISION VENCIDA" in closed)
        assertTrue("REVISION VENCIDA" in cells(RiskSheets.matrix(listOf(line()), RiskContext(all), Disclosure.INTERNO)))
    }

    @Test
    fun `PUBLICO cuenta sobre la grilla completa con la marca reservado`() {
        // seis visibles; el uno se oculta y, para que lo oculto sume al menos cinco, también los cinco de MEDIO (SmallCells)
        val active =
            List(6) { mapOf("risk_level" to "ALTO", "category" to "AMBIENTAL") } + List(5) { mapOf("risk_level" to "MEDIO", "category" to "OTRO") } +
                mapOf("risk_level" to "BAJO", "category" to "OTRO")
        val sheets = RiskSheets.aggregates(active, all, catalog.enumOf("social_risk", "category"), catalog.enumOf("social_risk", "treatment_decision"))
        assertEquals(listOf("Por nivel y categoría", "Por tratamiento y nivel"), sheets.map { it.name })
        val byCategory = sheets.first().rows.toList()
        assertEquals((4 + 2) * (catalog.enumOf("social_risk", "category").size + 2), byCategory.size)
        assertEquals(6, byCategory.first { it[0] == "ALTO" && it[1] == "AMBIENTAL" }[2])
        assertEquals(SmallCells.HIDDEN, byCategory.first { it[0] == "BAJO" && it[1] == "OTRO" }[2])
        assertTrue(sheets.last().rows.any { it[0] == RiskSheets.UNDECIDED })
    }

    // privacy solo depende de common: sus copias de los niveles y estados son las de sgspe.risk
    @Test
    fun `los niveles, los estados y los campos son los del modelo`() {
        assertEquals(Risks.LEVELS, RiskSheets.LEVELS)
        assertEquals(Risks.REVIEWED, RiskSheets.REVIEWED)
        val fields = { o: String -> catalog.objectOrNull(o)!!.fields.keys + catalog.manyToOneFields(o).map { it.first } }
        assertEquals(emptyList<String>(), RiskSheets.RISK_FIELDS.filter { it !in fields(RiskSheets.RISK) })
        assertEquals(emptyList<String>(), RiskSheets.ASSESSMENT_FIELDS.filter { it !in fields(RiskSheets.ASSESSMENT) })
        assertEquals(emptyList<String>(), RiskSheets.MEASURE_FIELDS.filter { it !in fields(RiskSheets.MEASURE) })
        assertEquals(emptyList<String>(), RiskSheets.IMPACT_FIELDS.filter { it !in fields(RiskSheets.IMPACT) })
        for ((field, target) in RiskSheets.LINKED) assertEquals(target.first, catalog.manyToOneFields(RiskSheets.RISK).toMap()[field], field)
    }
}
```

Crear `src/test/kotlin/sgspe/privacy/RiskRegisterApiTest.kt`:

```kotlin
package sgspe.privacy

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F4ApiTest

// POST /api/sgspe/exports/risk-register (api.md §4.13, deliverables.md §10, ASM-101): la matriz de riesgos por nivel, sin texto
// libre ni correos en EXTERNO, PUBLICO con la marca reservado, sin los cerrados salvo includeClosed, el permiso EXPORT sobre
// social_risk y su fila de export_log
class RiskRegisterApiTest : F4ApiTest() {
    private fun export(
        body: Map<String, Any?>,
        token: String = this.token
    ): ResultBytes {
        val result =
            client
                .post()
                .uri("/api/sgspe/exports/risk-register")
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

    private fun body(
        level: String,
        filters: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> = mapOf("filters" to mapOf("project" to listOf(testProject())) + filters, "disclosureLevel" to level, "checklistConfirmed" to true)

    // un riesgo revisado, con una medida y un impacto, y su código
    private fun assessed(manager: Account): Pair<String, String> {
        val risk = socialRisk(testProject(), attributes = mapOf("risk_source" to "Fuente reservada del riesgo"))
        val response =
            exchange(
                "POST",
                "/api/sgspe/social-risks/$risk/assessments",
                mapOf("attributes" to mapOf("assessment_date" to "2026-10-05", "probability" to 3, "impact" to 4, "notes" to "Nota de la revisión")),
                manager.token,
                withReason("revisión")
            )
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        mitigationMeasure(risk, manager)
        createRecord(
            "potential_impact",
            mapOf(
                "project" to testProject(),
                "social_risk" to risk,
                "description" to "Impacto reservado",
                "impact_sign" to "NEGATIVO"
            )
        )
        return risk to record("social_risk", risk)["attributes"]["code"].asString()
    }

    @Test
    fun `INTERNO trae la matriz, las revisiones, las medidas y los impactos`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        val (_, code) = assessed(manager)
        val file = export(body("INTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Matriz", "Revisiones", "Medidas", "Impactos"), sheets.keys.toList())
        assertTrue(code in sheets.getValue("Matriz"))
        assertTrue("Fuente reservada del riesgo" in sheets.getValue("Matriz"))
        assertTrue("Nota de la revisión" in sheets.getValue("Revisiones"))
        assertTrue(manager.email in sheets.getValue("Revisiones"))
    }

    @Test
    fun `EXTERNO no trae texto libre ni correos y cuenta las medidas en la matriz`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        val (_, code) = assessed(manager)
        val file = export(body("EXTERNO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Matriz", "Revisiones", "Impactos"), sheets.keys.toList())
        val all = sheets.values.flatten()
        assertTrue(code in all)
        assertFalse(all.any { it.contains("reservad") || it.contains("Nota de la revisión") })
        assertFalse(all.any { it.contains("@sgspe.test") })
        assertTrue("Medidas completadas" in sheets.getValue("Matriz"))
    }

    @Test
    fun `PUBLICO cuenta los riesgos activos con la marca reservado`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        assessed(manager)
        val file = export(body("PUBLICO"), manager.token)
        assertEquals(HttpStatus.OK, file.status, file.text)
        val sheets = sheets(file.body)
        assertEquals(listOf("Por nivel y categoría", "Por tratamiento y nivel"), sheets.keys.toList())
        assertTrue(SmallCells.HIDDEN in sheets.getValue("Por nivel y categoría"))
    }

    @Test
    fun `sin includeClosed no salen los cerrados, y un filtro invalido es INVALID_FILTER`() {
        val manager = member("SOCIAL_RISK_MANAGER")
        val closed = socialRisk(testProject())
        move("social_risk", closed, "close_identified", manager.token, "el riesgo dejó de existir")
        val code = record("social_risk", closed)["attributes"]["code"].asString()
        assertFalse(code in sheets(export(body("INTERNO"), manager.token).body).getValue("Matriz"))
        assertTrue(code in sheets(export(body("INTERNO", mapOf("includeClosed" to true)), manager.token).body).getValue("Matriz"))
        val wrong = export(body("INTERNO", mapOf("riskLevel" to listOf("ENORME"))), manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, wrong.status, wrong.text)
        assertEquals("INVALID_FILTER", tree(wrong.text)["errors"][0]["message"].asString())
    }

    @Test
    fun `quien no tiene EXPORT sobre social_risk recibe 403 y la exportacion deja su fila en export_log`() {
        assertEquals(HttpStatus.FORBIDDEN, export(body("INTERNO"), member("GRIEVANCE_MANAGER").token).status)
        val manager = member("SOCIAL_RISK_MANAGER")
        assertEquals(HttpStatus.OK, export(body("INTERNO"), manager.token).status)
        val logs = tree(send("GET", "/api/objects/export_log/records?exporter_user_id=${manager.id}", null, HttpStatus.OK))["content"].values()
        assertTrue(logs.any { it["attributes"]["object_name"].asString() == "social_risk" })
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.privacy.RiskSheetsTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'RiskSheets'`, `'RiskLine'`, `'RiskContext'` y `'MeasureCount'`).

- [ ] **Paso 3: Las hojas, la exportación y su ruta**

Crear `src/main/kotlin/sgspe/privacy/RiskSheets.kt`:

```kotlin
package sgspe.privacy

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Values
import sgspe.common.toCodedException

// un riesgo de la matriz como lo leyó quien exporta, con lo que la matriz junta de sus hijos: sus grupos de interés (id y nombre
// si se puede mostrar; null si no se leen), su última revisión no residual y sus medidas contadas (null si no se leen)
class RiskLine(
    val id: String,
    val state: String?,
    val attributes: Map<String, Any?>,
    val stakeholders: List<Pair<String, String?>>?,
    val lastDate: String?,
    val lastTrend: String?,
    val measures: MeasureCount?
)

// las medidas de un riesgo: totales, completadas y atrasadas (sin completar y con planned_date anterior a hoy)
data class MeasureCount(
    val total: Int,
    val completed: Int,
    val late: Int
)

// lo que las hojas necesitan además de los riesgos, ya leído como quien exporta: los objeto.campo que lee, los nombres de los
// registros vinculados (localidad, comunidad, área de influencia, conflicto, tema, incidente), y el código de cada riesgo por id
class RiskContext(
    val readable: Set<String>,
    val names: Map<String, String> = emptyMap(),
    val codes: Map<String, String> = emptyMap()
) {
    fun canRead(path: String): Boolean = path in readable
}

// las hojas de risk-register según el nivel de divulgación (deliverables.md §10, ASM-101): INTERNO con «Matriz» (los campos de
// DOC-01 §9.3.2), «Revisiones», «Medidas» e «Impactos»; EXTERNO sin texto libre ni correos, con los grupos de interés por código
// GI- y las medidas contadas en la «Matriz»; PUBLICO solo cuenta riesgos activos sobre la grilla del catálogo con SmallCells
// (ASM-088). nada de esto lee datos: recibe lo que quien exporta pudo leer
object RiskSheets {
    const val RISK = "social_risk"
    const val ASSESSMENT = "risk_assessment"
    const val MEASURE = "mitigation_measure"
    const val IMPACT = "potential_impact"
    const val CLOSED = "closed"
    const val NO_DATA = "SIN DATO"
    const val NO_LEVEL = "SIN NIVEL"
    const val UNDECIDED = "sin decidir"

    // los niveles de riesgo y los estados que calculan review_status (privacy solo depende de common; RiskSheetsTest comprueba que
    // son los de sgspe.risk.Risks)
    val LEVELS: List<String> = listOf("BAJO", "MEDIO", "ALTO", "CRITICO")
    val REVIEWED: Set<String> = setOf("assessed", "mitigating", "monitoring")

    // el texto libre (LONG_TEXT) y los correos: nunca en EXTERNO (deliverables.md §10)
    val FREE_TEXT: Map<String, Set<String>> =
        mapOf(
            RISK to setOf("risk_source", "treatment_rationale", "current_controls", "monitoring_sources", "close_reason"),
            ASSESSMENT to setOf("notes"),
            MEASURE to setOf("description"),
            IMPACT to setOf("description")
        )
    val EMAILS: Set<String> = setOf("owner_user_email", "assessor_user_email")

    val RISK_FIELDS: List<String> =
        listOf(
            "code",
            "title",
            "risk_source",
            "category",
            "impact_type",
            "impact_nature",
            "probability",
            "impact",
            "score",
            "risk_level",
            "treatment_decision",
            "treatment_rationale",
            "current_controls",
            "controls_adequate",
            "monitoring_sources",
            "residual_probability",
            "residual_impact",
            "residual_score",
            "residual_level",
            "next_review_date",
            "review_status",
            "owner_user_email",
            "close_reason",
            "locality",
            "community",
            "area_of_influence",
            "local_conflict",
            "key_theme",
            "origin_incident"
        )
    val ASSESSMENT_FIELDS: List<String> =
        listOf("assessment_date", "probability", "impact", "score", "risk_level", "residual", "trend", "notes", "assessor_user_email")
    val MEASURE_FIELDS: List<String> = listOf("description", "owner_user_email", "planned_date", "completed_date", "completed")
    val IMPACT_FIELDS: List<String> = listOf("activity_type", "description", "impact_type", "impact_nature", "impact_sign")

    // el objeto y el campo de cada vínculo que la matriz nombra
    val LINKED: Map<String, Pair<String, String>> =
        mapOf(
            "locality" to ("locality" to "name"),
            "community" to ("community" to "name"),
            "area_of_influence" to ("area_of_influence" to "area_type"),
            "local_conflict" to ("local_conflict" to "title"),
            "key_theme" to ("key_theme" to "name"),
            "origin_incident" to ("incident" to "code")
        )

    val REQUIRES: List<String> =
        RISK_FIELDS.map { "$RISK.$it" } + ASSESSMENT_FIELDS.map { "$ASSESSMENT.$it" } + MEASURE_FIELDS.map { "$MEASURE.$it" } +
            IMPACT_FIELDS.map { "$IMPACT.$it" } + LINKED.values.map { (o, f) -> "$o.$f" } +
            listOf("organization.name", "person.given_names", "person.surnames")

    // si `path` (objeto.campo) sale en el nivel
    fun allowed(
        path: String,
        disclosure: Disclosure
    ): Boolean {
        if (disclosure == Disclosure.INTERNO) return true
        val objectName = path.substringBefore('.')
        val field = path.substringAfter('.')
        return field !in FREE_TEXT[objectName].orEmpty() && field !in EMAILS
    }

    private fun cell(
        context: RiskContext,
        objectName: String,
        attributes: Map<String, Any?>,
        field: String
    ): Any? {
        if (!context.canRead("$objectName.$field")) return NOT_AVAILABLE
        val value = attributes[field]
        return when {
            value is Boolean -> if (value) "Sí" else "No"
            value is Number -> Values.decimal(value)
            else -> Values.text(value).orEmpty()
        }
    }

    // «Matriz»: un riesgo por fila. la situación de revisión solo en los estados que la calculan; los vínculos por su nombre; en
    // EXTERNO, las medidas contadas en lugar de su hoja
    fun matrix(
        lines: List<RiskLine>,
        context: RiskContext,
        disclosure: Disclosure
    ): XlsxSheet {
        require(disclosure != Disclosure.PUBLICO) { "PUBLICO no lleva la matriz: solo agregados" }
        val internal = disclosure == Disclosure.INTERNO
        val columns =
            listOf(
                "code" to "Código",
                "title" to "Título",
                "risk_source" to "Fuente",
                "category" to "Categoría",
                "impact_type" to "Tipo de impacto",
                "impact_nature" to "Naturaleza del impacto",
                "probability" to "Probabilidad",
                "impact" to "Impacto",
                "score" to "Puntaje",
                "risk_level" to "Nivel",
                "treatment_decision" to "Decisión de tratamiento",
                "treatment_rationale" to "Razón de la decisión",
                "current_controls" to "Acciones actuales",
                "controls_adequate" to "Acciones adecuadas",
                "monitoring_sources" to "Fuentes de monitoreo",
                "residual_probability" to "Probabilidad residual",
                "residual_impact" to "Impacto residual",
                "residual_score" to "Puntaje residual",
                "residual_level" to "Nivel residual",
                "next_review_date" to "Próxima revisión",
                "review_status" to "Situación de revisión",
                "owner_user_email" to "Responsable",
                "locality" to "Localidad",
                "community" to "Comunidad",
                "area_of_influence" to "Área de influencia",
                "local_conflict" to "Conflicto local",
                "key_theme" to "Tema clave",
                "origin_incident" to "Incidente de origen",
                "close_reason" to "Motivo de cierre"
            ).filter { allowed("$RISK.${it.first}", disclosure) }
        val header =
            listOf("Estado") + columns.map { it.second } + listOf("Tendencia", "Última revisión", "Grupos de interés") +
                if (internal) emptyList() else listOf("Medidas", "Medidas completadas", "Medidas atrasadas")
        val rows =
            lines.asSequence().map { line ->
                val values =
                    columns.map { (field, _) ->
                        when {
                            field in LINKED -> linked(line, field, context)
                            field == "review_status" && line.state !in REVIEWED -> ""
                            else -> cell(context, RISK, line.attributes, field)
                        }
                    }
                val stakeholders: Any =
                    line.stakeholders?.joinToString("; ") { (id, name) -> if (internal && name != null) name else RegisterSheets.code(id) } ?: NOT_AVAILABLE
                val counts =
                    if (internal) {
                        emptyList()
                    } else {
                        line.measures?.let { listOf(it.total, it.completed, it.late) } ?: listOf(NOT_AVAILABLE, NOT_AVAILABLE, NOT_AVAILABLE)
                    }
                listOf<Any?>(line.state.orEmpty()) + values + listOf(line.lastTrend.orEmpty(), line.lastDate.orEmpty(), stakeholders) + counts
            }
        return XlsxSheet("Matriz", header, rows)
    }

    private fun linked(
        line: RiskLine,
        field: String,
        context: RiskContext
    ): Any {
        val (objectName, nameField) = LINKED.getValue(field)
        if (!context.canRead("$RISK.$field") || !context.canRead("$objectName.$nameField")) return NOT_AVAILABLE
        val id = Values.text(line.attributes[field]) ?: return ""
        return context.names[id] ?: NOT_AVAILABLE
    }

    // «Revisiones»: todas, de cada riesgo por fecha, con quien revisó en INTERNO
    fun assessments(
        rows: List<Map<String, Any?>>,
        context: RiskContext,
        disclosure: Disclosure
    ): XlsxSheet {
        val fields = ASSESSMENT_FIELDS.filter { allowed("$ASSESSMENT.$it", disclosure) }
        val labels =
            mapOf(
                "assessment_date" to "Fecha",
                "probability" to "Probabilidad",
                "impact" to "Impacto",
                "score" to "Puntaje",
                "risk_level" to "Nivel",
                "residual" to "Residual",
                "trend" to "Tendencia",
                "notes" to "Notas",
                "assessor_user_email" to "Revisó"
            )
        return XlsxSheet(
            "Revisiones",
            listOf("Riesgo") + fields.map { labels.getValue(it) },
            rows.asSequence().map { a -> listOf<Any?>(riskCode(a, context)) + fields.map { cell(context, ASSESSMENT, a, it) } }
        )
    }

    // «Medidas» (solo INTERNO)
    fun measures(
        rows: List<Map<String, Any?>>,
        context: RiskContext
    ): XlsxSheet {
        val labels = listOf("Descripción", "Responsable", "Fecha planificada", "Fecha de cumplimiento", "Completada")
        return XlsxSheet(
            "Medidas",
            listOf("Riesgo") + labels,
            rows.asSequence().map { m -> listOf<Any?>(riskCode(m, context)) + MEASURE_FIELDS.map { cell(context, MEASURE, m, it) } }
        )
    }

    // «Impactos»: en EXTERNO, actividad, tipo, naturaleza, signo y riesgo (sin la descripción)
    fun impacts(
        rows: List<Map<String, Any?>>,
        context: RiskContext,
        disclosure: Disclosure
    ): XlsxSheet {
        val fields = IMPACT_FIELDS.filter { allowed("$IMPACT.$it", disclosure) }
        val labels =
            mapOf(
                "activity_type" to "Actividad",
                "description" to "Descripción",
                "impact_type" to "Tipo",
                "impact_nature" to "Naturaleza",
                "impact_sign" to "Signo"
            )
        return XlsxSheet(
            "Impactos",
            listOf("Riesgo") + fields.map { labels.getValue(it) },
            rows.asSequence().map { i -> listOf<Any?>(riskCode(i, context)) + fields.map { cell(context, IMPACT, i, it) } }
        )
    }

    private fun riskCode(
        attributes: Map<String, Any?>,
        context: RiskContext
    ): String {
        val id = Values.text(attributes[RISK]) ?: return ""
        return if (context.canRead("$RISK.code")) context.codes[id] ?: "" else NOT_AVAILABLE
    }

    // PUBLICO: riesgos activos por nivel y categoría, y por decisión de tratamiento (con «sin decidir») y nivel, sobre la grilla
    // completa con sus ceros y SmallCells en cada tabla; sin totales. un catálogo vacío rechaza la salida
    fun aggregates(
        active: List<Map<String, Any?>>,
        readable: Set<String>,
        categories: List<String>,
        treatments: List<String>
    ): List<XlsxSheet> {
        if (categories.isEmpty() || treatments.isEmpty()) {
            throw Finding(
                "CATALOGUE_UNAVAILABLE",
                "Un catálogo de los riesgos no está disponible: una salida PUBLICO no puede armar su grilla",
                "category",
                status = HttpStatus.CONFLICT
            ).toCodedException()
        }

        fun value(
            attributes: Map<String, Any?>,
            field: String,
            known: List<String>,
            empty: String
        ): String {
            if ("$RISK.$field" !in readable) return NOT_AVAILABLE
            val found = Values.text(attributes[field]) ?: return empty
            return if (found in known) found else NOT_AVAILABLE
        }
        val levelAxis = LEVELS + NO_LEVEL + NOT_AVAILABLE
        val categoryAxis = categories + NO_DATA + NOT_AVAILABLE
        val treatmentAxis = treatments + UNDECIDED + NOT_AVAILABLE
        val byCategory = active.groupingBy { value(it, "risk_level", LEVELS, NO_LEVEL) to value(it, "category", categories, NO_DATA) }.eachCount()
        val byTreatment = active.groupingBy { value(it, "treatment_decision", treatments, UNDECIDED) to value(it, "risk_level", LEVELS, NO_LEVEL) }.eachCount()
        val first = SmallCells.apply(levelAxis.flatMap { l -> categoryAxis.map { c -> (l to c) to (byCategory[l to c] ?: 0) } })
        val second = SmallCells.apply(treatmentAxis.flatMap { t -> levelAxis.map { l -> (t to l) to (byTreatment[t to l] ?: 0) } })
        return listOf(
            XlsxSheet("Por nivel y categoría", listOf("Nivel", "Categoría", "Riesgos"), first.asSequence().map { (k, n) -> listOf(k.first, k.second, n) }),
            XlsxSheet(
                "Por tratamiento y nivel",
                listOf("Decisión de tratamiento", "Nivel", "Riesgos"),
                second.asSequence().map { (k, n) ->
                    listOf(k.first, k.second, n)
                }
            )
        )
    }
}
```

Crear `src/main/kotlin/sgspe/privacy/RiskRegister.kt`:

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
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// la exportación risk-register (api.md §4.13, ASM-101, deliverables.md §10): la matriz de riesgos con sus revisiones, medidas e
// impactos, leída como quien exporta (un campo ilegible sale «no disponible»), con el nivel de divulgación aplicado antes de
// escribir y su fila de export_log antes de entregar el archivo. exige READ y EXPORT sobre social_risk y CREATE sobre export_log
// antes de leer nada; los proyectos siguen ExportProjects. la lectura es acotada: sgspe.exports.max-rows por conjunto y, para
// los grupos de interés de cada riesgo (M2M), MAX_RELATED riesgos y MAX_PER_LINK vínculos por riesgo; pasarse es 400
// TOO_MANY_RECORDS sobre filters.project. PUBLICO cuenta los riesgos activos
@Service
class RiskRegister(
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
    private class Filters(
        val states: List<String>,
        val categories: List<String>,
        val levels: List<String>,
        val includeClosed: Boolean
    )

    suspend fun export(body: Map<String, Any?>): ExportFile {
        val user = callers.require()
        permissions.require(
            user,
            "Exportar la matriz de riesgos",
            Actions.READ to RiskSheets.RISK,
            EXPORT to RiskSheets.RISK,
            Actions.CREATE to ExportLog.OBJECT
        )
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
        val readable = RiskSheets.REQUIRES.filter { readability.canRead(it) && RiskSheets.allowed(it, disclosure) }.toSet()
        val risks = if (projects?.isEmpty() == true) emptyList() else risks(projects, filters, disclosure)
        val sheets = sheets(risks, readable, readability, disclosure)
        val export = withContext(Dispatchers.IO) { ExportFile.create("matriz-de-riesgos-${disclosure.name.lowercase()}.xlsx", clock.instant()) }
        try {
            withContext(Dispatchers.IO) { export.file.outputStream().use { Xlsx.write(sheets, it) } }
            log.export(user, RiskSheets.RISK, "XLSX", disclosure, description(projects, raw, disclosure), risks.size, projects?.singleOrNull())
            return export
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    // los riesgos de los proyectos con los filtros en la consulta; sin includeClosed (o en PUBLICO, que cuenta los activos), sin los
    // cerrados
    private suspend fun risks(
        projects: List<UUID>?,
        filters: Filters,
        disclosure: Disclosure
    ): List<RecordResponse> {
        val all = catalog.states(RiskSheets.RISK)
        val wanted = filters.states.ifEmpty { all }.filter { it != RiskSheets.CLOSED || (filters.includeClosed && disclosure != Disclosure.PUBLICO) }
        if (wanted.isEmpty()) return emptyList()
        val criteria =
            listOfNotNull(
                projects?.let { Criteria.anyOf("project", it) },
                Criteria.inState(wanted),
                filters.categories.takeIf { it.isNotEmpty() }?.let { Criteria.textIn("category", it) },
                filters.levels.takeIf { it.isNotEmpty() }?.let { Criteria.textIn("risk_level", it) }
            )
        return records.all(RiskSheets.RISK, criteria = criteria, max = maxRows, field = TOO_MANY_FIELD)
    }

    private suspend fun sheets(
        risks: List<RecordResponse>,
        readable: Set<String>,
        readability: Readability,
        disclosure: Disclosure
    ): List<XlsxSheet> {
        if (disclosure == Disclosure.PUBLICO) {
            return RiskSheets.aggregates(
                risks.map { it.attributes },
                readable,
                catalog.enumOf(RiskSheets.RISK, "category"),
                catalog.enumOf(RiskSheets.RISK, "treatment_decision")
            )
        }
        if (risks.size > MAX_RELATED) throw Records.tooMany(RiskSheets.RISK, MAX_RELATED, TOO_MANY_FIELD)
        val ids = risks.map { it.id }

        // un hijo que quien exporta no puede leer queda vacío (sus celdas salen «no disponible»)
        suspend fun children(objectName: String): List<RecordResponse>? = if (readability.canRead(objectName)) readIn(objectName, ids) else null
        val assessments = children(RiskSheets.ASSESSMENT)
        val measures = children(RiskSheets.MEASURE)
        val impacts = children(RiskSheets.IMPACT)
        val stakeholders =
            if (readability.canRead("stakeholder")) risks.associate { it.id to linked(it.id) } else null
        val names = if (disclosure == Disclosure.INTERNO) stakeholderNames(stakeholders?.values?.flatten().orEmpty(), readable) else emptyMap()
        val today = LocalDate.now(clock.withZone(LIMA))
        val lines =
            risks.map { r ->
                val last =
                    assessments
                        ?.filter { Values.text(it.attributes[RiskSheets.RISK]) == r.id && Values.bool(it.attributes["residual"]) != true }
                        ?.maxWithOrNull(compareBy({ Values.text(it.attributes["assessment_date"]) }, { it.createdAt }))
                val mine = measures?.filter { Values.text(it.attributes[RiskSheets.RISK]) == r.id }
                RiskLine(
                    r.id,
                    r.state,
                    r.attributes,
                    stakeholders?.get(r.id)?.map { it.id to names[it.id] },
                    last?.let { Values.text(it.attributes["assessment_date"]) },
                    last?.let { Values.text(it.attributes["trend"]) },
                    mine?.let { list ->
                        MeasureCount(
                            list.size,
                            list.count { Values.bool(it.attributes["completed"]) == true },
                            list.count { m ->
                                Values.bool(m.attributes["completed"]) != true &&
                                    Values.text(m.attributes["planned_date"])?.let { LocalDate.parse(it).isBefore(today) } == true
                            }
                        )
                    }
                )
            }
        val context = RiskContext(readable, linkedNames(risks, readable), risks.associate { it.id to (Values.text(it.attributes["code"]) ?: "") })
        val byDate =
            compareBy<RecordResponse>({ Values.text(it.attributes[RiskSheets.RISK]) }, { Values.text(it.attributes["assessment_date"]) }, { it.createdAt })
        val sheets =
            mutableListOf(
                RiskSheets.matrix(lines, context, disclosure),
                RiskSheets.assessments(assessments.orEmpty().sortedWith(byDate).map { it.attributes }, context, disclosure)
            )
        if (disclosure == Disclosure.INTERNO) sheets += RiskSheets.measures(measures.orEmpty().map { it.attributes }, context)
        sheets += RiskSheets.impacts(impacts.orEmpty().map { it.attributes }, context, disclosure)
        return sheets
    }

    // el nombre de cada vínculo de los riesgos que nombra la matriz, si quien exporta lo lee
    private suspend fun linkedNames(
        risks: List<RecordResponse>,
        readable: Set<String>
    ): Map<String, String> =
        RiskSheets.LINKED
            .flatMap { (field, target) ->
                val (objectName, nameField) = target
                if ("$objectName.$nameField" !in readable) return@flatMap emptyList()
                readIds(objectName, risks.mapNotNull { Values.text(it.attributes[field]) }).mapNotNull { r ->
                    Values.text(r.attributes[nameField])?.let { r.id to it }
                }
            }.toMap()

    // el nombre de cada grupo de interés en INTERNO: la organización o la persona, si quien exporta lee esos nombres
    private suspend fun stakeholderNames(
        stakeholders: List<RecordResponse>,
        readable: Set<String>
    ): Map<String, String> {
        val organizations =
            if ("organization.name" in readable) {
                readIds("organization", stakeholders.mapNotNull { Values.text(it.attributes["organization"]) })
                    .mapNotNull { o -> Values.text(o.attributes["name"])?.let { o.id to it } }
                    .toMap()
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
                val name = Values.text(s.attributes["organization"])?.let { organizations[it] } ?: Values.text(s.attributes["person"])?.let { people[it] }
                name?.ifBlank { null }?.let { s.id to it }
            }.toMap()
    }

    // ---- lectura acotada ----

    private suspend fun linked(risk: String): List<RecordResponse> =
        records.related(RiskSheets.RISK, UUID.fromString(risk), STAKEHOLDERS, max = MAX_PER_LINK, field = TOO_MANY_FIELD)

    private suspend fun readIn(
        objectName: String,
        risks: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in risks.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += records.all(objectName, criteria = listOf(Criteria.anyOf(RiskSheets.RISK, chunk)), max = maxRows - out.size, field = TOO_MANY_FIELD)
        }
        return out
    }

    private suspend fun readIds(
        objectName: String,
        ids: Collection<String>
    ): List<RecordResponse> {
        val out = mutableListOf<RecordResponse>()
        for (chunk in ids.distinct().mapNotNull(Records::uuid).chunked(CHUNK)) {
            out += records.all(objectName, ids = chunk, max = maxRows - out.size, field = TOO_MANY_FIELD)
        }
        return out
    }

    // ---- el cuerpo ----

    private fun filters(raw: Map<String, Any?>): Filters {
        val states = texts(raw["state"], "filters.state", catalog.states(RiskSheets.RISK))
        val categories = texts(raw["category"], "filters.category", catalog.enumOf(RiskSheets.RISK, "category"))
        val levels = texts(raw["riskLevel"], "filters.riskLevel", catalog.enumOf(RiskSheets.RISK, "risk_level"))
        val include = raw["includeClosed"]
        if (include != null && include !is Boolean) throw bad("INVALID_FILTER", "El filtro filters.includeClosed es verdadero o falso", "filters.includeClosed")
        return Filters(states, categories, levels, include == true)
    }

    // una lista de textos (o uno solo) del catálogo `known`
    private fun texts(
        raw: Any?,
        field: String,
        known: List<String>
    ): List<String> {
        val values =
            when (raw) {
                null -> return emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values
            .map { value ->
                val text = value as? String
                if (text == null || text !in known) throw bad("INVALID_FILTER", "El filtro $field debe ser uno de ${known.joinToString(", ")}", field)
                text
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
        const val EXPORT = "EXPORT"
        const val STAKEHOLDERS = "social_risk_stakeholder"
        private const val TOO_MANY_FIELD = "filters.project"
        val FILTERS = setOf("project", "state", "category", "riskLevel", "includeClosed")

        // los riesgos cuyos grupos de interés se leen uno por uno, y los vínculos por riesgo
        const val MAX_RELATED = 2_000
        private const val CHUNK = 500
        private const val MAX_PER_LINK = 200
    }
}
```

En `src/main/kotlin/sgspe/privacy/ExportController.kt`, reemplazar:

```kotlin
    private val register: StakeholderRegister,
    private val matrix: CommitmentMatrix,
    private val attention: AttentionRegister
) {
    @PostMapping("/{kind}")
```

por:

```kotlin
    private val register: StakeholderRegister,
    private val matrix: CommitmentMatrix,
    private val attention: AttentionRegister,
    private val risks: RiskRegister
) {
    @PostMapping("/{kind}")
```

En `src/main/kotlin/sgspe/privacy/ExportController.kt`, reemplazar:

```kotlin
                "commitment-matrix" -> matrix.export(body)
                "attention-register" -> attention.export(body)
                else ->
                    throw Finding(
```

por:

```kotlin
                "commitment-matrix" -> matrix.export(body)
                "attention-register" -> attention.export(body)
                "risk-register" -> risks.export(body)
                else ->
                    throw Finding(
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `RiskSheetsTest` con 5 pruebas en verde y `ArchitectureTest` (privacy solo depende de common)
sin cambios.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/privacy/RiskSheets.kt src/main/kotlin/sgspe/privacy/RiskRegister.kt src/main/kotlin/sgspe/privacy/ExportController.kt \
  src/test/kotlin/sgspe/privacy/RiskSheetsTest.kt src/test/kotlin/sgspe/privacy/RiskRegisterApiTest.kt
git commit -F - <<'MSG'
feat: exportación risk-register en sus tres niveles

La matriz de riesgos con sus revisiones, medidas e impactos: INTERNO completa, EXTERNO sin texto
libre ni correos y con los grupos de interés por código, PUBLICO con conteos de riesgos activos y
la marca reservado; su fila de export_log antes de entregar (ASM-101).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `sgspe.privacy.RiskRegisterApiTest`.

---

### Tarea 16: Documentos, vigía de arquitectura, verificación final, push y PR

**Files:**
- Modify: `docs/api.md` (§3.3 y §4.7), `docs/workflows.md` (JOB-01, JOB-04 y el párrafo de «Listeners»),
  `docs/domain-model.md` (`next_review_date`), `docs/dashboards.md` (KPI-ENG-08), `CLAUDE.md`,
  `src/test/kotlin/sgspe/ArchitectureTest.kt`

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: la rama `feat/fase-4-2b-riesgos` en verde y el PR. Los documentos dicen lo que este plan decidió frente a ellos
  (decisiones 1, 3, 4, 5, 7, 9 y 16); el resto del diseño de F4 ya lo escribió el plan 4.0 (también la ficha de
  `review_every_days`, de 1 a 365).

- [ ] **Paso 1: Los documentos, `CLAUDE.md` y el vigía**

En `docs/api.md`, reemplazar:

```markdown
| Estado | Códigos |
|---|---|
| `400` | `DERIVED_FIELD` (se envió un campo derivado), `REASON_REQUIRED` (la transición exige `X-Change-Reason`), `FIELD_REASON_REQUIRED` (cambiar `stage` o `exploration_phase` de un proyecto exige motivo), `EXACTLY_ONE` (debe venir exactamente uno de un grupo de campos), `USER_REFERENCE` (el par `<rol>_user_id` y `<rol>_user_email` no nombra a un usuario de la organización), `UNKNOWN_ROLE` (`role_name` de una asignación no es un rol declarado), `REPRESENTED_PEOPLE` (`represented_people_count` menor que 1), `POSITION_WITHOUT_PERSON` (`position_status`: un cargo sin persona solo puede estar `VACANTE` o `PREVISTO`), `INVALID_NUMBER` (un monto que no es un número) y `OUT_OF_RANGE` (un monto negativo, con más de 15 dígitos enteros o con más de 4 decimales tras quitar los ceros finales), sobre el campo: `exploration_campaign.social_support_cap`, `commitment.amount`, `delivery.delivered_amount` y `agreement.amount`, en el alta y en todo cambio de su valor (`AmountFieldsGuard`, ASM-092), `JUSTIFICATION_REQUIRED` (`origin_justification`: un compromiso que nace de un taller o de una reunión informativa, RN-ENG-001) y `DEPENDENCY_SELF` (`depends_on`: un compromiso no depende de sí mismo). Desde F4: `ANONYMOUS_WITH_IDENTITY` (`person` o `stakeholder` de una QCS anónima), `CONFIDENTIAL_RECEIVER_REQUIRED` (`received_by_user_id`: el alta de una QCS confidencial, sin `READ_CONFIDENTIAL`, lleva como receptor a quien escribe), `INVALID_LINK` (`duplicate_of`: una queja no es duplicado de sí misma), `FIELD_REQUIRED` (`category` y `severity` para atender una queja; `resolution` y `response` para resolverla; `responded_at` y `response` en las transiciones que comunican una respuesta; probabilidad, impacto y `owner_user_id` al evaluar un riesgo; `close_reason` al cerrarlo; `immediate_response` y `consequences` al cerrar un incidente) y `OUT_OF_RANGE` también en los plazos y la cadencia del proyecto (de 1 a 365), sus umbrales de riesgo (de 1 a 25 y crecientes) y `threshold` y `window_days` de una regla de alerta (ASM-097) |
| `403` | `COMMAND_ONLY` (la transición es de comando), `NOT_ASSIGNED` (sin asignación vigente al proyecto), `ACTION_REQUIRED` (falta la acción declarada: `APPROVE` de un proyecto o de un compromiso y `VERIFY` de un compromiso, sobre `record`; `DOWNLOAD_RESTRICTED` para bajar la clasificación de una evidencia restringida, sobre `classification`), `NOT_OWN_RECORD` (el objeto declara `EDIT_ANY`, quien escribe no lo tiene y no creó el registro: no lo cambia, mueve, vincula ni borra; `record`), `SEGREGATION_OF_DUTIES` (quien aprueba un compromiso lo registró, `record`; quien lo verifica es su responsable, `owner_user_id`; RN-COM-005), `FIELD_NOT_READABLE` (`delivery.conformity_by`: registrar el cumplimiento necesita leer quiénes dieron la conformidad), `GENERATOR_KEY_PLATFORM_ONLY` (`generator_key`: una persona fija, cambia o vacía la clave de una tarea, también con un `PUT` que la omite; reenviarla tal como se leyó no es cambiarla), `OBJECT_NOT_READABLE` (quien escribe no lee un objeto que la regla necesita: `project_party`, `project`, `engagement_participant`, `engagement_question` o `area_of_influence`; desde F3 también `commitment`, `engagement`, `exploration_campaign`, `delivery`, `evidence`, `commitment_dependency` o `stakeholder`, y `agreement_land_parcel` al activar un acuerdo; `field` es el objeto). Desde F4: `ACTION_REQUIRED` también sin `READ_CONFIDENTIAL` al quitar `confidential` o cambiar `owner_user_id` o `received_by_user_id` de una QCS confidencial (sobre el campo), sin `APPROVE` en `resolve_high_severity` y sin `CONFIRM_ALERT` en `confirm` y `dismiss` (`record`); `HUMAN_DECISION_REQUIRED` (`record`: confirmar o descartar una alerta sin usuario o con una cuenta de servicio, RN-EWS-001); `COMMAND_ONLY` también en `close_reclassified` y `close_reclassified_*`, `accept` y `close_accepted` de WF-E3 y `assess` de WF-F; `GENERATOR_KEY_PLATFORM_ONLY` también en `early_warning.generator_key` |
| `409` | `HAS_DEPENDENTS` (el registro tiene dependientes obligatorios, o uno opcional de un objeto `requiresReason`, y no se borra; `field` es `<objeto>.<campo>` del primer dependiente), `FROZEN_MEMBERS` (los miembros de una versión `approved` o `superseded` del área de influencia no cambian; `field`: `area_of_influence`), `FROZEN_AREA` (su proyecto, tipo y número de versión tampoco; `field` es el campo que cambia), `WORKING_CRS_REQUIRED`, `PARTY_REQUIRED`, `FIELD_REQUIRED` (`scheduled_at`, `held_at`, `minutes` o `cancel_reason` de una interacción), `ATTENDANCE_REQUIRED`, `PENDING_QUESTIONS`, `MINUTES_REQUIRED`, `PROFILE_INCOMPLETE` (`caracterizar` exige `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` y `owner_user_id`; `field` es el primero que falta), `DUPLICATE_RULE` (`stakeholder_type`: ya hay una fila de `prioritization_rule` para cualquier tipo de actor con ese proyecto, grupo de influencia, postura y ámbito). Desde F3: `WORKSHOP_COMMITMENT_BLOCKED` (`origin_engagement`: el proyecto no admite compromisos de talleres ni reuniones informativas), `DUE_DATE_RESCHEDULE_ONLY` (`due_date`: desde `approved`, también en `cancelled`, la fecha solo cambia con una reprogramación), `DEPENDENCY_CYCLE` y `DEPENDENCY_GRAPH_TOO_LARGE` (`depends_on`: la dependencia cerraría un ciclo, o encadena más de 500 compromisos y no se puede verificar), `PROJECT_MISMATCH` (el campo de un compromiso o de un candidato que apunta a un registro de otro proyecto), `DELIVERY_FROZEN` (`commitment`: la entrega de un compromiso cumplido, verificado, cerrado o cancelado no cambia ni se vincula), `FIELD_REQUIRED` también con los campos de la matriz de compromisos y el responsable (el primero que falta), `resource_type` al registrar el cumplimiento, `signed_date`, `effective_date` y `community` de un acuerdo, y `duplicate_of`; `STAKEHOLDER_REQUIRED` (`commitment_stakeholder`), `SENIOR_APPROVAL_REQUIRED` (`origin_engagement` si nace de una mesa de diálogo, `commitment_stakeholder` si lo pide un grupo de interés POLITICO), `INTERNAL_APPROVAL_REQUIRED` (`project`), `SUPPORT_CAP_EXCEEDED` y `CAP_DATA_INVALID` (`exploration_campaign`: la aprobación supera el tope de la campaña, o un monto guardado de la campaña no es válido y el tope no se puede sumar), `DELIVERY_REQUIRED` (`delivery`), `EVIDENCE_REQUIRED` (`evidence_delivery`), `BLOCKING_DEPENDENCY` (`commitment_dependency`), `LAND_PARCEL_REQUIRED` (`agreement_land_parcel`), `ASSEMBLY_REQUIRED` (`assembly_engagement`) y, por los comandos de evidencias, `EVIDENCE_IMMUTABLE` (`file`, `replaces` o `import_batch_id`), `EVIDENCE_NOT_DELETABLE` (`record`) y `EVIDENCE_FILE_IN_USE` (`file`: el archivo ya es de otra evidencia). Desde F4: `PROJECT_MISMATCH` en toda relación de un objeto de F4 hacia un registro de otro proyecto (`origin_engagement`, `origin_question`, `stakeholder`, `social_risk`, `incident`, `duplicate_of`, `reclassified_from_*`, `created_commitment`, `area_of_influence`, `key_theme`, `origin_incident`, `commitment`, `media_mention`, `early_warning` y `early_warning_rule`; una regla del tenant no discrepa), en `commitment.origin_grievance` y `commitment.social_risk` y en los vínculos `social_risk_stakeholder` e `incident_stakeholder` (`ProjectLinkGuard`); `SENIOR_APPROVAL_REQUIRED` (`record`: `resolve` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo); `CONFORMITY_GIVEN` (`complainant_conformity_given`: no se reabre una queja con conformidad); `STATE_MISMATCH` (`created_commitment`: `close_unfulfilled` con el compromiso fuera de `cancelled`); `MEASURE_OWNER_REQUIRED` (`mitigation_measure`: mitigar sin una medida con responsable); `RESIDUAL_ASSESSMENT_REQUIRED` (`risk_assessment`: monitorear sin una revisión residual); `HAS_DEPENDENTS` también en una regla de alerta con alertas; y, por los vínculos de evidencias, `EVIDENCE_NOT_RESTRICTED` (`classification`: una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial) |
```

por:

```markdown
| Estado | Códigos |
|---|---|
| `400` | `DERIVED_FIELD` (se envió un campo derivado), `REASON_REQUIRED` (la transición exige `X-Change-Reason`), `FIELD_REASON_REQUIRED` (cambiar `stage` o `exploration_phase` de un proyecto exige motivo), `EXACTLY_ONE` (debe venir exactamente uno de un grupo de campos), `USER_REFERENCE` (el par `<rol>_user_id` y `<rol>_user_email` no nombra a un usuario de la organización), `UNKNOWN_ROLE` (`role_name` de una asignación no es un rol declarado), `REPRESENTED_PEOPLE` (`represented_people_count` menor que 1), `POSITION_WITHOUT_PERSON` (`position_status`: un cargo sin persona solo puede estar `VACANTE` o `PREVISTO`), `INVALID_NUMBER` (un monto que no es un número) y `OUT_OF_RANGE` (un monto negativo, con más de 15 dígitos enteros o con más de 4 decimales tras quitar los ceros finales), sobre el campo: `exploration_campaign.social_support_cap`, `commitment.amount`, `delivery.delivered_amount` y `agreement.amount`, en el alta y en todo cambio de su valor (`AmountFieldsGuard`, ASM-092), `JUSTIFICATION_REQUIRED` (`origin_justification`: un compromiso que nace de un taller o de una reunión informativa, RN-ENG-001) y `DEPENDENCY_SELF` (`depends_on`: un compromiso no depende de sí mismo). Desde F4: `ANONYMOUS_WITH_IDENTITY` (`person` o `stakeholder` de una QCS anónima), `CONFIDENTIAL_RECEIVER_REQUIRED` (`received_by_user_id`: el alta de una QCS confidencial, sin `READ_CONFIDENTIAL`, lleva como receptor a quien escribe), `INVALID_LINK` (`duplicate_of`: una queja no es duplicado de sí misma), `FIELD_REQUIRED` (`category` y `severity` para atender una queja; `resolution` y `response` para resolverla; `responded_at` y `response` en las transiciones que comunican una respuesta; probabilidad, impacto y `owner_user_id` al evaluar un riesgo; `treatment_decision` MITIGAR al decidir mitigar y MONITOREAR o ACEPTAR al decidir monitorear; `close_reason` al cerrarlo; `immediate_response` y `consequences` al cerrar un incidente) y `OUT_OF_RANGE` también en los plazos y la cadencia del proyecto (de 1 a 365), sus umbrales de riesgo (de 1 a 25 y crecientes), `threshold` y `window_days` de una regla de alerta (ASM-097) y la cadencia propia de un riesgo (`review_every_days`: entera de 1 a 365, en el alta y en todo cambio de su valor; vacía vale `risk_review_days` del proyecto) |
| `403` | `COMMAND_ONLY` (la transición es de comando), `NOT_ASSIGNED` (sin asignación vigente al proyecto), `ACTION_REQUIRED` (falta la acción declarada: `APPROVE` de un proyecto o de un compromiso y `VERIFY` de un compromiso, sobre `record`; `DOWNLOAD_RESTRICTED` para bajar la clasificación de una evidencia restringida, sobre `classification`), `NOT_OWN_RECORD` (el objeto declara `EDIT_ANY`, quien escribe no lo tiene y no creó el registro: no lo cambia, mueve, vincula ni borra; `record`), `SEGREGATION_OF_DUTIES` (quien aprueba un compromiso lo registró, `record`; quien lo verifica es su responsable, `owner_user_id`; RN-COM-005), `FIELD_NOT_READABLE` (`delivery.conformity_by`: registrar el cumplimiento necesita leer quiénes dieron la conformidad), `GENERATOR_KEY_PLATFORM_ONLY` (`generator_key`: una persona fija, cambia o vacía la clave de una tarea, también con un `PUT` que la omite; reenviarla tal como se leyó no es cambiarla), `OBJECT_NOT_READABLE` (quien escribe no lee un objeto que la regla necesita: `project_party`, `project`, `engagement_participant`, `engagement_question` o `area_of_influence`; desde F3 también `commitment`, `engagement`, `exploration_campaign`, `delivery`, `evidence`, `commitment_dependency` o `stakeholder`, y `agreement_land_parcel` al activar un acuerdo; `field` es el objeto). Desde F4: `ACTION_REQUIRED` también sin `READ_CONFIDENTIAL` al quitar `confidential` o cambiar `owner_user_id` o `received_by_user_id` de una QCS confidencial (sobre el campo), sin `APPROVE` en `resolve_high_severity` y sin `CONFIRM_ALERT` en `confirm` y `dismiss` (`record`); `HUMAN_DECISION_REQUIRED` (`record`: confirmar o descartar una alerta sin usuario o con una cuenta de servicio, RN-EWS-001); `COMMAND_ONLY` también en `close_reclassified` y `close_reclassified_*`, `accept` y `close_accepted` de WF-E3 y `assess` de WF-F; `GENERATOR_KEY_PLATFORM_ONLY` también en `early_warning.generator_key` |
| `409` | `HAS_DEPENDENTS` (el registro tiene dependientes obligatorios, o uno opcional de un objeto `requiresReason`, y no se borra; `field` es `<objeto>.<campo>` del primer dependiente), `FROZEN_MEMBERS` (los miembros de una versión `approved` o `superseded` del área de influencia no cambian; `field`: `area_of_influence`), `FROZEN_AREA` (su proyecto, tipo y número de versión tampoco; `field` es el campo que cambia), `WORKING_CRS_REQUIRED`, `PARTY_REQUIRED`, `FIELD_REQUIRED` (`scheduled_at`, `held_at`, `minutes` o `cancel_reason` de una interacción), `ATTENDANCE_REQUIRED`, `PENDING_QUESTIONS`, `MINUTES_REQUIRED`, `PROFILE_INCOMPLETE` (`caracterizar` exige `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` y `owner_user_id`; `field` es el primero que falta), `DUPLICATE_RULE` (`stakeholder_type`: ya hay una fila de `prioritization_rule` para cualquier tipo de actor con ese proyecto, grupo de influencia, postura y ámbito). Desde F3: `WORKSHOP_COMMITMENT_BLOCKED` (`origin_engagement`: el proyecto no admite compromisos de talleres ni reuniones informativas), `DUE_DATE_RESCHEDULE_ONLY` (`due_date`: desde `approved`, también en `cancelled`, la fecha solo cambia con una reprogramación), `DEPENDENCY_CYCLE` y `DEPENDENCY_GRAPH_TOO_LARGE` (`depends_on`: la dependencia cerraría un ciclo, o encadena más de 500 compromisos y no se puede verificar), `PROJECT_MISMATCH` (el campo de un compromiso o de un candidato que apunta a un registro de otro proyecto), `DELIVERY_FROZEN` (`commitment`: la entrega de un compromiso cumplido, verificado, cerrado o cancelado no cambia ni se vincula), `FIELD_REQUIRED` también con los campos de la matriz de compromisos y el responsable (el primero que falta), `resource_type` al registrar el cumplimiento, `signed_date`, `effective_date` y `community` de un acuerdo, y `duplicate_of`; `STAKEHOLDER_REQUIRED` (`commitment_stakeholder`), `SENIOR_APPROVAL_REQUIRED` (`origin_engagement` si nace de una mesa de diálogo, `commitment_stakeholder` si lo pide un grupo de interés POLITICO), `INTERNAL_APPROVAL_REQUIRED` (`project`), `SUPPORT_CAP_EXCEEDED` y `CAP_DATA_INVALID` (`exploration_campaign`: la aprobación supera el tope de la campaña, o un monto guardado de la campaña no es válido y el tope no se puede sumar), `DELIVERY_REQUIRED` (`delivery`), `EVIDENCE_REQUIRED` (`evidence_delivery`), `BLOCKING_DEPENDENCY` (`commitment_dependency`), `LAND_PARCEL_REQUIRED` (`agreement_land_parcel`), `ASSEMBLY_REQUIRED` (`assembly_engagement`) y, por los comandos de evidencias, `EVIDENCE_IMMUTABLE` (`file`, `replaces` o `import_batch_id`), `EVIDENCE_NOT_DELETABLE` (`record`) y `EVIDENCE_FILE_IN_USE` (`file`: el archivo ya es de otra evidencia). Desde F4: `PROJECT_MISMATCH` en toda relación de un objeto de F4 hacia un registro de otro proyecto (`origin_engagement`, `origin_question`, `stakeholder`, `social_risk`, `incident`, `duplicate_of`, `reclassified_from_*`, `created_commitment`, `area_of_influence`, `key_theme`, `origin_incident`, `commitment`, `media_mention`, `early_warning` y `early_warning_rule`; una regla del tenant no discrepa), en `commitment.origin_grievance` y `commitment.social_risk` y en los vínculos `social_risk_stakeholder` e `incident_stakeholder` (`ProjectLinkGuard`); `SENIOR_APPROVAL_REQUIRED` (`record`: `resolve` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo); `CONFORMITY_GIVEN` (`complainant_conformity_given`: no se reabre una queja con conformidad); `STATE_MISMATCH` (`created_commitment`: `close_unfulfilled` con el compromiso fuera de `cancelled`); `MEASURE_OWNER_REQUIRED` (`mitigation_measure`: mitigar sin una medida con responsable); `RESIDUAL_ASSESSMENT_REQUIRED` (`risk_assessment`: monitorear sin una revisión residual); `HAS_DEPENDENTS` también en una regla de alerta con alertas; y, por los vínculos de evidencias, `EVIDENCE_NOT_RESTRICTED` (`classification`: una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial) |
```

En `docs/api.md`, reemplazar:

```markdown
  MANTIENE o DISMINUYE según el puntaje; vacía en la primera); (4) crea la `risk_assessment` con quien revisa; (5)
  actualiza los valores vigentes (no residual) o los residuales, y `next_review_date` = `assessment_date` +
  (`review_every_days` o `risk_review_days`), con la marca; (6) en la primera revisión aplica `assess`, una transición
  de comando, con el motivo de la petición.
- La subida de `risk_level` a ALTO o CRITICO publica `risk_escalated` (`RiskNoticeListener`) y, a CRITICO, dispara
```

por:

```markdown
  MANTIENE o DISMINUYE según el puntaje; vacía en la primera); (4) crea la `risk_assessment` con quien revisa; (5)
  actualiza los valores vigentes (no residual) o los residuales, y `next_review_date` = `assessment_date` +
  (`review_every_days` o `risk_review_days`) y `review_status`, con la marca; (6) en la primera revisión aplica `assess`, una transición
  de comando, con el motivo de la petición.
- La subida de `risk_level` a ALTO o CRITICO publica `risk_escalated` (`RiskNoticeListener`) y, a CRITICO, dispara
```

En `docs/workflows.md`, reemplazar:

```markdown
| ID | Trabajo (paquete) | Frecuencia | Qué escribe | Qué nunca hace | Fuente | Fase |
|---|---|---|---|---|---|---|
| JOB-01 | `DeadlineJob` (`sgspe.deadline`) | Cada hora (ASM-006) | `deadline_status` de `commitment`, QCS y `task`; `validity_status` de `agreement`; `review_status` de `social_risk`, y `next_review_date` si cambia `review_every_days`; tareas de escalamiento (`escalation:<objeto>:<id>:<fecha del plazo>:<nivel>`, de compromisos y, desde F4, de quejas y consultas, sin asignado ni fecha) y, desde F4, de revisión de riesgos (`risk_review:<id>:<next_review_date>`, asignada al responsable del riesgo, con fecha `next_review_date` y título «Revisar el riesgo <código>», al pasar a POR REVISAR o REVISION VENCIDA, ASM-099); los avisos de plazo los publica `DeadlineNotices`, una fuente de wasichai-notifications que lee lo que este trabajo escribe, así que llegan a lo sumo una hora después | Cambiar `workflow_state`: un compromiso vencido sigue en `in_progress` o en `blocked` | RN-WFL-001, RF-WFL-004, RN-RSK-001, ASM-006, ASM-030, ASM-070 | F3 (compromisos, acuerdos, tareas), F4 (QCS, riesgos) |
| JOB-02 | `DerivedFieldsJob` (`sgspe.common`) | Cada hora sobre los registros fuente cambiados desde la corrida anterior (por `updated_at`) y una vez al día sobre todos | Los campos derivados de §7.4 del modelo de dominio que mantienen listeners o que no tienen otro escritor: `executed_social_amount`, `other_currency_items_count`, `calculated_area_ha`, conteos de asistentes, `formalized`, `requires_senior_approval` (los vínculos de solicitantes no llegan a los listeners: la vuelta diaria los recoge), `total_amount`, `total_budget` y los plazos de QCS que falten | Escribir campos no derivados | Modelo de dominio §7.4, §7.5 | F2 |
| JOB-03 | `StakeholderFollowUpJob` (`sgspe.stakeholder`) | Una vez al día, configurable (ASM-022): sus parámetros se miden en días | `last_contact_date`; la regla de primer contacto de `relationship_status` (SIN CONTACTO → CONTACTO ESTABLECIDO); desde F3, tareas de evaluación periódica (`stakeholder_review_days`, clave `stakeholder_review:<id>:<fecha>`) y de inactividad (`inactivity_days`, clave `stakeholder_inactivity:<id>:<fecha de referencia>`, la del último contacto o, sin contacto, la del alta) | Mover WF-B o cambiar `relationship_status` fuera de la regla de primer contacto | RF-STK-010, RF-STK-014, ASM-022, ASM-032 | F2 |
```

por:

```markdown
| ID | Trabajo (paquete) | Frecuencia | Qué escribe | Qué nunca hace | Fuente | Fase |
|---|---|---|---|---|---|---|
| JOB-01 | `DeadlineJob` (`sgspe.deadline`) | Cada hora (ASM-006) | `deadline_status` de `commitment`, QCS y `task`; `validity_status` de `agreement`; `review_status` de `social_risk`, y `next_review_date` si cambia su cadencia (`review_every_days` o, sin ella, `risk_review_days` del proyecto); tareas de escalamiento (`escalation:<objeto>:<id>:<fecha del plazo>:<nivel>`, de compromisos y, desde F4, de quejas y consultas, sin asignado ni fecha) y, desde F4, de revisión de riesgos (`risk_review:<id>:<next_review_date>`, asignada al responsable del riesgo, con fecha `next_review_date` y título «Revisar el riesgo <código>», al pasar a POR REVISAR o REVISION VENCIDA, ASM-099); los avisos de plazo los publica `DeadlineNotices`, una fuente de wasichai-notifications que lee lo que este trabajo escribe, así que llegan a lo sumo una hora después | Cambiar `workflow_state`: un compromiso vencido sigue en `in_progress` o en `blocked` | RN-WFL-001, RF-WFL-004, RN-RSK-001, ASM-006, ASM-030, ASM-070 | F3 (compromisos, acuerdos, tareas), F4 (QCS, riesgos) |
| JOB-02 | `DerivedFieldsJob` (`sgspe.common`) | Cada hora sobre los registros fuente cambiados desde la corrida anterior (por `updated_at`) y una vez al día sobre todos | Los campos derivados de §7.4 del modelo de dominio que mantienen listeners o que no tienen otro escritor: `executed_social_amount`, `other_currency_items_count`, `calculated_area_ha`, conteos de asistentes, `formalized`, `requires_senior_approval` (los vínculos de solicitantes no llegan a los listeners: la vuelta diaria los recoge), `total_amount`, `total_budget` y los plazos de QCS que falten | Escribir campos no derivados | Modelo de dominio §7.4, §7.5 | F2 |
| JOB-03 | `StakeholderFollowUpJob` (`sgspe.stakeholder`) | Una vez al día, configurable (ASM-022): sus parámetros se miden en días | `last_contact_date`; la regla de primer contacto de `relationship_status` (SIN CONTACTO → CONTACTO ESTABLECIDO); desde F3, tareas de evaluación periódica (`stakeholder_review_days`, clave `stakeholder_review:<id>:<fecha>`) y de inactividad (`inactivity_days`, clave `stakeholder_inactivity:<id>:<fecha de referencia>`, la del último contacto o, sin contacto, la del alta) | Mover WF-B o cambiar `relationship_status` fuera de la regla de primer contacto | RF-STK-010, RF-STK-014, ASM-022, ASM-032 | F2 |
```

En `docs/workflows.md`, reemplazar:

```markdown
2. para cada proyecto toma sus reglas y, de cada tipo que el proyecto no tiene, las del tenant (sin `project`): una
   regla del proyecto reemplaza a la del tenant del mismo tipo (ASM-097);
3. evalúa cada regla con su evaluador puro y con conteos (`Records.count`), nunca cargando los registros;
4. si dispara, crea la alerta salvo que haya una abierta (fuera de `dismissed` y `closed`) de la misma regla y proyecto
   (y riesgo, en NIVEL DE RIESGO) o una creada en los últimos `window_days` días;
```

por:

```markdown
2. para cada proyecto toma sus reglas y, de cada tipo que el proyecto no tiene, las del tenant (sin `project`): una
   regla del proyecto reemplaza a la del tenant del mismo tipo (ASM-097);
3. evalúa cada regla con su evaluador puro y con conteos (`Records.count`), sin cargar los registros, salvo CONTEO DE QCS,
   que lee los planteamientos de la ventana para contarlos con `AttentionCounting`, y NIVEL DE RIESGO, que lee los
   riesgos revisados del proyecto;
4. si dispara, crea la alerta salvo que haya una abierta (fuera de `dismissed` y `closed`) de la misma regla y proyecto
   (y riesgo, en NIVEL DE RIESGO) o una creada en los últimos `window_days` días;
```

En `docs/workflows.md`, reemplazar:

```markdown
| `WarningNoticeListener` (`sgspe.notification`) | Alta de `early_warning`, por una persona o por JOB-04 como la plataforma; `start_triage` resuelve el aviso | `start_triage` | El módulo wasichai-notifications: publica `warning_new` y lo resuelve | Solo si el alta llega por un endpoint con transacción o por JOB-04, que crea la alerta en la suya | Un aviso mal formado se registra y el alta sigue | Lee `project_assignment` como quien crea la alerta; en las de JOB-04, como la plataforma |

Si una alerta que JOB-04 crea como la plataforma no llegara a `WarningNoticeListener` ni a AUT-09, JOB-04 publica
`warning_new` con la misma clave, sin duplicar, y crea la tarea de triaje con la clave `warning_triage:<id>`; lo decide
la prueba de integración del plan 4.2b.

## Avisos
```

por:

```markdown
| `WarningNoticeListener` (`sgspe.notification`) | Alta de `early_warning`, por una persona o por JOB-04 como la plataforma; `start_triage` resuelve el aviso | `start_triage` | El módulo wasichai-notifications: publica `warning_new` y lo resuelve | Solo si el alta llega por un endpoint con transacción o por JOB-04, que crea la alerta en la suya | Un aviso mal formado se registra y el alta sigue | Lee `project_assignment` como quien crea la alerta; en las de JOB-04, como la plataforma |

El alta que JOB-04 hace como la plataforma llega a `WarningNoticeListener` y a AUT-09: wasichai 0.6.0 avisa a los
listeners y a wasichai-automation de toda escritura de `RecordService`, también de la plataforma, así que JOB-04 no
publica el aviso ni crea la tarea de triaje por su cuenta. `WarningRulesJobApiTest` lo vigila; si una versión de la
plataforma dejara de hacerlo, JOB-04 publicaría `warning_new` con la misma clave y crearía la tarea con la clave
`warning_triage:<id>` (plan 4.2b).

## Avisos
```

En `docs/domain-model.md`, reemplazar:

```markdown
| `engagement_question.referred_at` | Momento en que la pregunta se derivó a una QCS | Endpoint de derivación, en su transacción | RN-ENG-005 |
| `probability`, `impact`, `score`, `risk_level` y los `residual_*` de `social_risk` | Valores de la última revisión, residual o no; el nivel sale de los umbrales del proyecto | Endpoint de revisiones, en su transacción | RN-RSK-002, RN-RSK-003, ASM-004 |
| `social_risk.next_review_date` | Fecha de la última revisión más `review_every_days` | Endpoint de revisiones; `DeadlineJob` si cambia `review_every_days` | RN-RSK-001 |
| `social_risk.review_status` | `next_review_date` frente a la fecha de hoy | `DeadlineJob` | RN-WFL-001 |
| `social_contribution.total_amount` | Suma de `line_amount` de sus ítems | `ContributionTotalsListener`; `DerivedFieldsJob` | RF-INV-001 |
```

por:

```markdown
| `engagement_question.referred_at` | Momento en que la pregunta se derivó a una QCS | Endpoint de derivación, en su transacción | RN-ENG-005 |
| `probability`, `impact`, `score`, `risk_level` y los `residual_*` de `social_risk` | Valores de la última revisión, residual o no; el nivel sale de los umbrales del proyecto | Endpoint de revisiones, en su transacción | RN-RSK-002, RN-RSK-003, ASM-004 |
| `social_risk.next_review_date` | Fecha de la última revisión más `review_every_days` (o, sin ella, `risk_review_days` del proyecto) | Endpoint de revisiones; `DeadlineJob` si cambia la cadencia | RN-RSK-001 |
| `social_risk.review_status` | `next_review_date` frente a la fecha de hoy | `DeadlineJob` | RN-WFL-001 |
| `social_contribution.total_amount` | Suma de `line_amount` de sus ítems | `ContributionTotalsListener`; `DerivedFieldsJob` | RF-INV-001 |
```

En `docs/dashboards.md`, reemplazar:

```markdown
| KPI-ENG-06 | #11 Monitoreos participativos de agua | Monitoreos ambientales del agua hechos con participación de grupos de interés y un agente independiente | Número de `engagement` realizadas de tipo MONITOREO PARTICIPATIVO en el periodo (ASM-029), con sus grupos de interés participantes; el agente independiente y los resultados constan en sus `evidence` | `engagement.engagement_type`, `held_at`, `workflow_state`; M2M `engagement_stakeholder`; `engagement_participant.organization` | Proyecto, periodo, territorio | RQ-DSH-015, ASM-029 | Vivo | F4 |
| KPI-ENG-07 | #12 Comunicados sobre compromisos cumplidos | Comunicados en medios que informan sobre compromisos cumplidos | Número de `media_mention` con `reports_fulfilled_commitment` y `published_date` en el periodo (ASM-029) | `media_mention.reports_fulfilled_commitment`, `published_date`, `commitment`, `outlet` | Proyecto, periodo | RQ-DSH-016, ASM-029 | Vivo | F4 |
| KPI-ENG-08 | #13 Medios con información positiva y negativa | Porcentaje de medios que presentan información positiva o negativa | Menciones del periodo de cada `tone` ÷ menciones del periodo; además, medios distintos (`outlet` u `outlet_name`) con al menos una mención de cada tono ÷ medios con menciones | `media_mention.tone`, `outlet`, `outlet_name`, `published_date`, `topic` | Proyecto, periodo; desglose por medio y tema | RQ-DSH-017, RF-EWS-002 | Vivo | F4 |

### 4.4 Compromisos (`KPI-COM`)
```

por:

```markdown
| KPI-ENG-06 | #11 Monitoreos participativos de agua | Monitoreos ambientales del agua hechos con participación de grupos de interés y un agente independiente | Número de `engagement` realizadas de tipo MONITOREO PARTICIPATIVO en el periodo (ASM-029), con sus grupos de interés participantes; el agente independiente y los resultados constan en sus `evidence` | `engagement.engagement_type`, `held_at`, `workflow_state`; M2M `engagement_stakeholder`; `engagement_participant.organization` | Proyecto, periodo, territorio | RQ-DSH-015, ASM-029 | Vivo | F4 |
| KPI-ENG-07 | #12 Comunicados sobre compromisos cumplidos | Comunicados en medios que informan sobre compromisos cumplidos | Número de `media_mention` con `reports_fulfilled_commitment` y `published_date` en el periodo (ASM-029) | `media_mention.reports_fulfilled_commitment`, `published_date`, `commitment`, `outlet` | Proyecto, periodo | RQ-DSH-016, ASM-029 | Vivo | F4 |
| KPI-ENG-08 | #13 Medios con información positiva y negativa | Porcentaje de medios que presentan información positiva o negativa | Menciones del periodo de cada `tone` ÷ menciones del periodo (el valor es el de NEGATIVO; los otros tonos, en el desglose por tono); además, medios distintos (`outlet` u `outlet_name`) con al menos una mención de cada tono ÷ medios con menciones | `media_mention.tone`, `outlet`, `outlet_name`, `published_date`, `topic` | Proyecto, periodo; desglose por medio y tema | RQ-DSH-017, RF-EWS-002 | Vivo | F4 |

### 4.4 Compromisos (`KPI-COM`)
```

En `CLAUDE.md`, reemplazar:

```markdown
  Gradle 9.8.0. Paquetes propios `sgspe.*` (`docs/architecture.md` §5.2): `common`, `observability`, `scope`, `engagement`,
  `stakeholder`, `territory`, `dashboard`, `privacy`, `prod` y, desde F3, `commitment`, `agreement`, `evidence`, `investment`,
  `deadline` y `notification` y, desde F4, `attention`.
- `model/`: `model.json` (los 72 objetos de F2 a F4), `roles.json`, `workflows/` (los 15 de F2 a F4: WF-A, WF-B, WF-C,
  WF-L, WF-O, WF-D, WF-I, WF-K, WF-N, WF-E1, WF-E2, WF-E3, WF-F, WF-J y WF-M), `document-types/`, `automations/` y los
```

por:

```markdown
  Gradle 9.8.0. Paquetes propios `sgspe.*` (`docs/architecture.md` §5.2): `common`, `observability`, `scope`, `engagement`,
  `stakeholder`, `territory`, `dashboard`, `privacy`, `prod` y, desde F3, `commitment`, `agreement`, `evidence`, `investment`,
  `deadline` y `notification` y, desde F4, `attention`, `risk` y `warning`.
- `model/`: `model.json` (los 72 objetos de F2 a F4), `roles.json`, `workflows/` (los 15 de F2 a F4: WF-A, WF-B, WF-C,
  WF-L, WF-O, WF-D, WF-I, WF-K, WF-N, WF-E1, WF-E2, WF-E3, WF-F, WF-J y WF-M), `document-types/`, `automations/` y los
```

En `CLAUDE.md`, reemplazar:

```markdown
| Evidencias sobre wasichai-files, descarga con `DOWNLOAD_RESTRICTED` | `src/main/kotlin/sgspe/evidence/` |
| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |
| JOB-01 y los avisos de plazo (`DeadlineNotices`) | `src/main/kotlin/sgspe/deadline/` |
| Avisos sobre wasichai-notifications (`Notices`, `ApprovalNoticeListener`, `GraveGrievanceNoticeListener`, `ConfidentialReaders`) | `src/main/kotlin/sgspe/notification/` |
| QCS: `AttentionWriteGuard`, plazos y fechas, reclasificación, aceptación, derivación, `RequestClosureListener`, reporte unificado | `src/main/kotlin/sgspe/attention/` |
| Días hábiles (`HolidayCalendar`, `BusinessDays`, feriados en `sgspe.calendar.holidays`), `Attention` y `AttentionCounting`, `NotReadable` y `readAs` | `src/main/kotlin/sgspe/common/`, `src/main/resources/application.yml` |
```

por:

```markdown
| Evidencias sobre wasichai-files, descarga con `DOWNLOAD_RESTRICTED` | `src/main/kotlin/sgspe/evidence/` |
| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |
| JOB-01 y los avisos de plazo (`DeadlineNotices`), también la revisión de los riesgos y su tarea `risk_review` | `src/main/kotlin/sgspe/deadline/` |
| Avisos sobre wasichai-notifications (`Notices`, `ApprovalNoticeListener`, `GraveGrievanceNoticeListener`, `ConfidentialReaders`, `RiskNoticeListener`, `WarningNoticeListener`) | `src/main/kotlin/sgspe/notification/` |
| Riesgos: niveles y revisión (`Risks.kt`), `RiskWriteGuard`, el endpoint de revisiones (`RiskAssessments`) | `src/main/kotlin/sgspe/risk/` |
| Alerta temprana: `WarningWriteGuard`, JOB-04 (`WarningRulesJob`) y los seis tipos de regla (`WarningEvaluation`) | `src/main/kotlin/sgspe/warning/` |
| QCS: `AttentionWriteGuard`, plazos y fechas, reclasificación, aceptación, derivación, `RequestClosureListener`, reporte unificado | `src/main/kotlin/sgspe/attention/` |
| Días hábiles (`HolidayCalendar`, `BusinessDays`, feriados en `sgspe.calendar.holidays`), `Attention` y `AttentionCounting`, `NotReadable` y `readAs` | `src/main/kotlin/sgspe/common/`, `src/main/resources/application.yml` |
```

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, reemplazar:

```kotlin
        assertTrue(names.containsAll(f3), "$names")
        // y las de F4 que cruzan paquetes: attention, el alcance de las confidenciales, el calendario y los avisos
        val f4 = listOf("AttentionWriteGuard.kt", "ConfidentialAttentionScope.kt", "BusinessDays.kt", "ConfidentialReaders.kt", "AttentionRegister.kt")
        assertTrue(names.containsAll(f4), "$names")
        val testNames = sources(test).map { it.name }
```

por:

```kotlin
        assertTrue(names.containsAll(f3), "$names")
        // y las de F4 que cruzan paquetes: attention, el alcance de las confidenciales, el calendario y los avisos
        val f4 =
            listOf(
                "AttentionWriteGuard.kt",
                "ConfidentialAttentionScope.kt",
                "BusinessDays.kt",
                "ConfidentialReaders.kt",
                "AttentionRegister.kt",
                "RiskAssessments.kt",
                "WarningRulesJob.kt",
                "RiskNotices.kt",
                "RiskRegister.kt"
            )
        assertTrue(names.containsAll(f4), "$names")
        val testNames = sources(test).map { it.name }
```

- [ ] **Paso 2: Todo lo local**

```bash
cd ~/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
(cd model && python3 -m unittest 2>&1 | tail -1 && python3 apply.py --validate-only)
python3 tools/check_traceability.py && yarn format:check
git status --short
```

Esperado: `BUILD SUCCESSFUL` con todas las unitarias en verde (593 en la copia de trabajo de este plan: las 534 que deja el
plan 4.2a y 59 de este plan), `OK` del modelo, `ok: 298 requisitos, todos con su fila`, prettier sin cambios y un `git
status` con solo los seis archivos de este paso.

- [ ] **Paso 3: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add docs/api.md docs/workflows.md docs/domain-model.md docs/dashboards.md CLAUDE.md src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -F - <<'MSG'
docs: cierre del plan 4.2b con las decisiones de riesgos y alertas

api.md pide la decisión de tratamiento, acota review_every_days y dice que la revisión escribe
review_status; workflows.md
explica cómo cuenta JOB-04 y que su alta llega a los listeners; la cadencia efectiva, el valor de
KPI-ENG-08, CLAUDE.md y ArchitectureTest se ponen al día.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

- [ ] **Paso 4: La suite de integración (CI del PR o coordinador)**

Las clases de integración de este plan corren en la CI del PR con Testcontainers, con la suite completa. Si el coordinador
las quiere antes, por el túnel y con `sgspe_sdd_test` libre, juntas en una sola JVM (una aprovisiona el modelo de F4 para
todas):

```bash
cd ~/IdeaProjects/sgspe
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun \
  --tests 'sgspe.risk.*ApiTest' --tests 'sgspe.warning.*ApiTest' --tests 'sgspe.notification.*ApiTest' \
  --tests 'sgspe.deadline.*ApiTest' --tests 'sgspe.dashboard.*ApiTest' --tests 'sgspe.privacy.RiskRegisterApiTest' \
  --tests 'sgspe.workflow.WorkflowMatrixApiTest' --tests 'sgspe.workflow.AutomationsApiTest' \
  --tests 'sgspe.workflow.TaskWorkflowApiTest' --tests 'sgspe.security.PermissionMatrixApiTest'
```

Esperado: verde; la matriz de workflows con `social_risk (9 de 9 transiciones)`, `early_warning (6 de 6)` e
`incident (3 de 3)`, y solo `stakeholder_request.close_accepted` y las de F2 en `DEFERRED`. Si `WarningRulesJobApiTest`
o la prueba de AUT-11 de `RiskAssessmentsApiTest` fallan porque una escritura no llega a los listeners o a las
automatizaciones, no se mezcla la rama (Tarea 12).

- [ ] **Paso 5: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin feat/fase-4-2b-riesgos
gh pr create --base main --title "feat: fase 4, plan 4.2b: backend de riesgos, alertas e incidentes" --body-file - <<'MSG'
## Qué trae

- Riesgos: niveles con los umbrales del proyecto, `RiskWriteGuard` (WF-F y la cadencia propia de 1 a 365 días) y el
  endpoint de revisiones con `assess` como
  transición de comando e `Idempotency-Key`; aviso `risk_escalated` por nivel y por revisión vencida.
- JOB-01 con la revisión de los riesgos: cadencia efectiva, `review_status` y la tarea `risk_review` del responsable.
- Alerta temprana: `WarningWriteGuard` (decisión humana, doble llave, cierre del incidente, reglas válidas),
  `generator_key` y reglas con alertas protegidas, JOB-04 con los seis tipos de regla y el aviso `warning_new`.
- KPI-ENG-06 a 08, los KPI-RSK con el tablero `risks` y la exportación `risk-register` en sus tres niveles.

## Pruebas

- Unitarias: 593 (59 nuevas).
- Integración (esta CI): revisiones, WF-F, WF-J y WF-M, avisos de riesgo y de alerta, JOB-01 y JOB-04 (AUT-09 y
  `warning_new` desde la plataforma, AUT-11 desde el endpoint), tableros y exportación; la matriz de workflows sin WF-F en
  `DEFERRED`.

## Para revisar

- Decisiones 1 (sin salida alternativa para las altas de la plataforma) y 11 (PUBLICO sin cerrados) del plan. Las
  decisiones 2 (quien revisa no recibe `risk_escalated`, aunque sea el responsable), 9 (KPI-ENG-08 es el porcentaje de
  menciones NEGATIVO), 16 (`review_every_days` de 1 a 365), 17 (KPI-ENG-06 por `engagement_stakeholder`) y 18 (una
  regla del tenant alerta en cada proyecto activo o suspendido) son del usuario.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---
## Resumen

| Tarea | Entrega | Grupo | Integración (CI del PR) |
|---|---|---|---|
| 1 | Nombres, niveles y revisión de los riesgos | A | — |
| 2 | `RiskWriteGuard`, la cadencia propia y `Criteria.filled` | B | (Tareas 3 y 4) |
| 3 | Endpoint de revisiones y `assess` como comando | C | `RiskAssessmentsApiTest` |
| 4 | WF-F en la matriz y `SocialRiskWorkflowApiTest` | D | `SocialRiskWorkflowApiTest`, `WorkflowMatrixApiTest` |
| 5 | `risk_escalated` por nivel | D | `RiskNoticesApiTest` |
| 6 | JOB-01 con la revisión de los riesgos y su tarea | B | `DeadlineJobRiskApiTest`, `DeadlineJobApiTest`, `DeadlineJobQcsApiTest` |
| 7 | `risk_escalated` por revisión vencida | C | `RiskReviewNoticesApiTest`, `QcsNoticesApiTest` |
| 8 | `WarningWriteGuard`, WF-J y WF-M | A | `EarlyWarningWorkflowApiTest`, `IncidentWorkflowApiTest`, `WarningRuleApiTest` |
| 9 | `generator_key` de alertas y reglas con alertas | A | `EarlyWarningProtectionApiTest`, `TaskWorkflowApiTest` |
| 10 | Los seis tipos de regla, puros | B | — |
| 11 | `warning_new` | A | `WarningNoticesApiTest` |
| 12 | JOB-04 `WarningRulesJob` | D | `WarningRulesJobApiTest`, `AutomationsApiTest` |
| 13 | KPI-ENG-06 a 08 | A | `EngagementIndicatorsF4ApiTest`, `DashboardsApiTest` |
| 14 | KPI-RSK y tablero `risks` | B | `RisksDashboardApiTest` |
| 15 | `risk-register` | B | `RiskRegisterApiTest` |
| 16 | Documentos, vigía, verificación y PR | E | Suite completa |

## Cobertura del alcance de este plan

| Alcance de 4.2b (especificación §7 y §3) | Dónde |
|---|---|
| `RiskLevels` | Tarea 1 |
| `RiskWriteGuard` (§3.6) | Tarea 2 |
| Endpoint de revisiones, `assess` en `CommandTransitionGuard` (§3.1, §3.6) | Tarea 3 |
| `RiskNoticeListener` (§3.5) | Tarea 5 |
| Prueba de AUT-11 desde el endpoint (§3.5) | Tarea 3 (`RiskAssessmentsApiTest`) |
| JOB-01 para riesgos: situación, cadencia, tarea de revisión (§3.5) | Tarea 6 |
| Revisión vencida en `DeadlineNotices` (§3.5) | Tarea 7 |
| `TASK_REFERENCE` con `social_risk` (y `early_warning`) | Tarea 7 |
| `WarningWriteGuard` (§3.7) | Tarea 8 |
| `GeneratorKeyGuard` de alertas (§3.1) | Tarea 9 |
| Regla con alertas: `409 HAS_DEPENDENTS` (§3.1) | Tarea 9 |
| `WarningRulesJob` con los seis evaluadores (§3.7, §4) | Tareas 10 y 12 |
| `WarningNoticeListener` (§3.5) | Tarea 11 |
| Prueba de AUT-09 y `warning_new` desde JOB-04 (§3.5, §8) | Tarea 12 (`WarningRulesJobApiTest`) |
| KPI-ENG-06 a 08 (§3.8) | Tarea 13 |
| `RiskKpis` y tablero `risks` (§3.8) | Tarea 14 |
| `risk-register` (§5.2) | Tarea 15 |
| Las clases por workflow de WF-F, WF-J y WF-M y su salida de `DEFERRED` | Tareas 4 y 8 |
| Fuentes `sgspe.risks` y `sgspe.warnings` | Tareas 5 y 11 |
| Suite completa y PR | Tarea 16 |
