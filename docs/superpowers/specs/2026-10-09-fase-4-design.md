# SGSPE · Fase 4: especificación de diseño

> **Para agentes:** esta especificación es la entrada de los planes 4.0 (documentos), 4.1 (modelo), 4.2 (backend) y 4.3
> (portal). No define IDs: los que se nombran en §6 los agrega el plan 4.0 a `docs/requirements-analysis.md`, y hasta
> entonces `docs/` sigue siendo la fuente de verdad. Donde esta especificación y `docs/` difieren, el plan 4.0 corrige
> `docs/` según lo que aquí se decide.

**Objetivo.** Dejar diseñada de punta a punta la Fase 4 («Quejas, consultas y solicitudes, riesgos y alertas») sobre
wasichai 0.6.0 y la maquinaria de F3: los doce objetos de BC-07, BC-08 y BC-09, sus seis workflows, la confidencialidad
por registro de las QCS, el calendario de días hábiles, los plazos y el escalamiento de QCS y riesgos, las alertas por
regla (JOB-04), los indicadores y tableros `attention` y `risks`, las exportaciones `attention-register` y
`risk-register`, y los dos pendientes de sgspe#14 que entran en el portal.

**Arquitectura.** Paquetes nuevos `sgspe.attention` (QCS), `sgspe.risk` (riesgos) y `sgspe.warning` (alertas e
incidentes); cambios en `sgspe.common` (calendario de días hábiles, conteo de planteamientos, parámetros), `sgspe.scope`
(regla «mismo proyecto» general y el `RecordReadScope` de las QCS confidenciales), `sgspe.deadline` (JOB-01 suma QCS y
riesgos), `sgspe.notification` (avisos de riesgo, de alerta y de queja grave), `sgspe.engagement` (derivación de
preguntas), `sgspe.dashboard` y `sgspe.privacy`. Todo escribe por `sgspe.common.Records` con la marca `SgspeWrite`; las
reglas son `RecordWriteGuard` hechas de `WriteRule` puras que `POST /api/sgspe/transition-checks` corre en seco, como en
F3. Ningún literal de rol en `src/main/kotlin`: lo que depende del rol se decide con acciones declaradas y con
`model/roles.json` leído por `ModelCatalog`.

**Fuentes.** `.superpowers/sdd/fase-4/readiness.md` (huecos C1 a C18), `.superpowers/sdd/fase-4/decisiones.md`
(decisiones del usuario del 2026-10-09, vinculantes), `docs/roadmap.md` (F4 y «Respuestas del checkpoint»),
`docs/workflows.md` (WF-E, WF-F, WF-J, WF-M, WF-N, «Automatizaciones», «Trabajos programados», «Listeners», «Avisos»,
«Plazos y escalamiento»), `docs/domain-model.md` (§6.7 a §6.9, §7, §8), `docs/database-model.md` (§6, §12 a §14, §16,
§17), `docs/security.md` (§5.3, §7.6, §7.9, §9, §11), `docs/dashboards.md` (§3, §4.3, §4.5, §4.6, §5.7, §5.8, §8),
`docs/deliverables.md` (§9, §10), `docs/api.md` (§3.3, §4.4, §4.6, §4.7, §4.11, §4.13), `docs/ux/screens.md` (§8, §9,
§11), wasichai 0.6.0 (`RecordReadScope`, WAS-ADR-0048) y sgspe#14.

## Restricciones globales

- Las mismas de F3: wasichai `0.6.0` exacto, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`; español
  (es-PE) en documentos, comentarios y `detail`, nombres técnicos en inglés (ADR-0009); valores ENUM en español
  MAYÚSCULAS sin tildes.
- Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; `scope` y `privacy` solo dependen de `common`; los tenants solo con `Organizations`.
- Nunca `asPlatform` dentro de una petición: los comandos, guardas y listeners leen y escriben como quien llama; solo
  JOB-01, JOB-02, JOB-04 y la fuente `DeadlineNotices` corren como la plataforma, con `source = job:<nombre>` y un
  motivo que nombra al trabajo.
- Los trabajos nunca aplican transiciones ni vinculan registros existentes: escriben campos derivados y crean registros
  nuevos (tareas y alertas) con sus relaciones ya puestas al crearlos.
- Datos de prueba ficticios (`@sgspe.test`, «Persona Ejemplo N», «Asociación Ejemplo»).

## 1. Propósito y alcance de F4

F4 cierra la Release 1 (MVP). Trae:

| Subsistema | Alcance | Objetos | Workflows y trabajos |
|---|---|---|---|
| QCS | Registro por el equipo social; quejas con acuse, evaluación, investigación opcional, atención, resolución con aprobación para las graves, verificación opcional, cierre, rechazo, desistimiento y reapertura; consultas respondidas o derivadas; solicitudes aceptadas como compromiso o declinadas; reclasificación entre los tres tipos; derivación de preguntas de reunión; reporte unificado; confidencialidad por registro | `grievance`, `inquiry`, `stakeholder_request` | WF-E1, WF-E2, WF-E3; AUT-02 |
| Riesgos | Matriz de riesgos con revisiones inmutables (puntaje P × I, nivel, tendencia y residual), revisión semanal con tarea y escalamiento, tratamiento con medidas e impactos potenciales | `social_risk`, `risk_assessment`, `mitigation_measure`, `potential_impact` | WF-F; AUT-11 |
| Alerta temprana | Incidentes, menciones en medios y señales registrados a mano; reglas configurables de seis tipos que JOB-04 evalúa cada hora; alertas con triaje y decisión humana | `incident`, `media_mention`, `early_warning_signal`, `early_warning_rule`, `early_warning` | WF-J, WF-M; AUT-09; JOB-04 |
| Plazos y escalamiento | Plazos de acuse (días hábiles), respuesta y resolución; revisión vencida de riesgos; niveles 0, 1 y 2 con avisos y tareas | Campos derivados de las QCS y de `social_risk` | JOB-01 (QCS y riesgos), `DeadlineNotices` |
| Indicadores y tableros | KPI-GRV-01 a 10, KPI-RSK-01 a 05, KPI-ENG-06 a 08 (solo por el endpoint de indicadores hasta F8), datos de KPI-EXE-02; tableros `attention` y `risks` | — | — |
| Exportaciones | `attention-register` y `risk-register` en INTERNO, EXTERNO y PUBLICO | `export_log` | — |
| sgspe#14 | Ocultar en el portal la huella SHA-256 de una evidencia restringida a quien no la descarga; codificar con `encodeURIComponent` los ids de las rutas de F2 | — | — |

Fuera de F4: canal público de recepción y avisos al reclamante (candidatos a brecha upstream, §2); los documentos
emitidos «Cargo de queja» y «Carta de respuesta» (esperan UPS-23); el tipo de regla CAMBIO DE POSTURA (§4); el original
de una importación como evidencia (ASM-060, pasa a F6); la capa del mapa de LYR-20 a LYR-22 (F6; sus datos sí llegan en
F4); el aporte social como destino de una solicitud aceptada (F5).

## 2. Decisiones

### 2.1 Las ocho decisiones del usuario

| # | Decisión | Modelo (4.1) | Backend (4.2) | Portal (4.3) |
|---|---|---|---|---|
| 1 | Solo el equipo social registra QCS; el formulario público es una brecha upstream candidata, posterior al MVP | Sin objeto ni rol nuevos; CREATE sobre las QCS solo para `COMMUNITY_RELATIONS_OFFICER` y `GRIEVANCE_MANAGER` (§7.6 de `security.md`) | Ninguna cadena de seguridad propia ni ruta pública; `CONTRATISTA` sigue siendo un canal que registra el equipo (ASM-043) | S37 es la única entrada; el canal se elige de la lista |
| 2 | QCS confidencial: `RecordReadScope` limitado a las QCS con `confidential` verdadero, que solo leen `GRIEVANCE_MANAGER`, su responsable y `ADMIN`; reabre la respuesta del checkpoint. Ampliada por el usuario: la leen también `APPROVER` (solo ALTA o CRITICA) y quien la recibió | Acción declarada nueva `READ_CONFIDENTIAL` en `grievance`, `inquiry` y `stakeholder_request`, concedida solo a `GRIEVANCE_MANAGER` en `roles.json` | `ConfidentialAttentionScope` en `sgspe.scope` (§3.3); reglas de escritura de la confidencialidad en `AttentionWriteGuard`; destinatarios de avisos filtrados; exportaciones y tableros leen como quien llama | Interruptor «Confidencial» con explicación; quien registra queda como quien la recibió; «Registro reservado» en los vínculos a una QCS que no se lee; aviso de exclusión en tableros y reportes |
| 3 | «Cargo de queja» y «Carta de respuesta» no se emiten mientras UPS-23 siga abierta; el documento firmado se sube como evidencia de la QCS | AUT-06, AUT-07, AUT-08 y sus tipos de documento no entran en `model/`; `test_model.py` los trata como «F4, tras UPS-23», igual que AUT-03 | Ningún código de documentos; `EvidenceWriteGuard` acepta los vínculos con las tres QCS | En S36, «Subir cargo firmado» (evidencia FORMULARIO) y «Subir carta firmada» (evidencia CARTA), con clasificación DATOS PERSONALES por defecto |
| 4 | Alertas: alta manual más seis tipos de regla, sin CAMBIO DE POSTURA; la semántica de cada regla se escribe en `workflows.md` antes del plan | `warning_rule_kind` sin CAMBIO DE POSTURA; `early_warning.generator_key` | `WarningRulesJob` con un evaluador puro por tipo (§4) | Formulario de reglas que explica el umbral y la ventana de cada tipo |
| 5 | Días hábiles: feriados nacionales por año en configuración; si falta el año, lunes a viernes con aviso en el log | Ninguno | `HolidayCalendar` y `BusinessDays` en `sgspe.common` (§3.4) | S37 muestra el plazo de acuse calculado por el backend |
| 6 | `attention-register` y `risk-register` como `commitment-matrix`: tres niveles; EXTERNO sin QCS confidenciales ni texto libre; PUBLICO con conteos y celdas pequeñas | Ninguno | `AttentionRegister` y `RiskRegister` en `sgspe.privacy` (§5) | S49 con los filtros de cada tipo |
| 7 | Sin avisos al reclamante en el MVP; brecha upstream candidata | Ninguno | wasichai-notifications solo avisa a usuarios | El cargo y la carta se entregan en papel y se suben firmados |
| 8 | Una queja ALTA o CRITICA avisa también a `APPROVER` | Ninguno | `approval_pending:grievance:<id>` a los roles de `resolve_high_severity` (§3.5) | S48 lista las quejas graves por resolver |

### 2.2 Valores por defecto aceptados

| Tema | Valor | Consecuencia |
|---|---|---|
| Revisiones de riesgo | `risk_assessment` `appendOnly` + `apiOnly`; una revisión se corrige con otra; `assess` es transición de comando | `CommandTransitionGuard` suma `assess` de WF-F; el endpoint de revisiones es la única vía |
| Tarea de revisión de riesgo | Clave `risk_review:<id>:<next_review_date>`, asignada al responsable del riesgo, con fecha `next_review_date` | JOB-01 la crea con `JobTasks` (§3.5) |
| Plazos de QCS | En JOB-01, sin pausa en `in_investigation`; las solicitudes no tienen plazo | `response_due_date` y `deadline_status` de `stakeholder_request` quedan vacíos en el MVP |
| Mismo proyecto | `409 PROJECT_MISMATCH` en todas las relaciones de F4 hacia objetos con alcance de proyecto, también las M2M | `ProjectLinkGuard` general en `sgspe.scope` (§3.1) |
| Incidentes | Sin severidad ni escalamiento; sin `requiresReason` | WF-M queda como está diseñado |
| Contradicciones menores | Se corrigen en el plan 4.0 | §6 |

### 2.3 Elecciones de esta especificación más allá de las decisiones

1. **La acción `READ_CONFIDENTIAL`.** La decisión 2 nombra un rol; el código no puede compararlo (RQ-SEC-005). Se modela
   como acción declarada, igual que `CONFIRM_ALERT`: quitarla en `roles.json` retira la capacidad sin tocar el código.
2. **`APPROVER` lee las quejas confidenciales ALTA o CRITICA.** La decisión 8 le avisa, y RN-GRV-002 le exige
   resolverlas con `resolve_high_severity`; sin lectura no podría. Quien tiene `APPROVE` sobre `grievance` ve una queja
   confidencial solo si su severidad es ALTA o CRITICA. Ampliación de la decisión 2 confirmada por el usuario.
3. **Quien recibió una QCS confidencial la sigue leyendo** (respuesta del usuario). Quien la registra es quien la
   recibe: el alta de una QCS confidencial lleva `received_by_user_id` igual a quien escribe, salvo que tenga
   `READ_CONFIDENTIAL`. Así quien registra siempre puede hacerlo, no la pierde al guardarla y los listeners, que
   escriben como esa persona, la pueden actualizar. Cambiar el responsable o quien la recibió de una QCS confidencial, o
   quitarle la marca, exige `READ_CONFIDENTIAL`.
4. **Una regla de alerta del proyecto reemplaza a la del tenant del mismo tipo** para ese proyecto, y `window_days` es
   también el silencio tras una alerta (§4). Con eso, las reglas no necesitan parámetros nuevos en `project`.
5. **`engagement_question.referred_at`** (derivado, lo escribe el endpoint de derivación): `EngagementWriteGuard` decide
   RN-ENG-005 sin leer la QCS, que puede ser confidencial.
6. **POR VENCER de una QCS** usa `due_soon_days`, pero nunca más de la mitad del plazo medido, en días calendario y
   redondeada hacia abajo: con los valores por defecto, el acuse de 5 días hábiles no nace POR VENCER.
7. **POR REVISAR de un riesgo** empieza el día anterior a `next_review_date`; REVISION VENCIDA, el día siguiente.
8. **El reporte y los tableros nunca cuentan lo que quien llama no lee**, y lo dicen con `confidentialHidden`, un
   booleano sin conteo (§3.3).
9. **4.2 se divide en 4.2a (QCS) y 4.2b (riesgos y alertas)** (§7).
10. **Aviso genérico de nivel 2** (respuesta del usuario): en el nivel 2 de una QCS confidencial, los roles de
    `approve_senior` de WF-D que no la ven reciben `qcs_confidential_overdue`, un aviso sin datos del registro (§3.5).

## 3. Diseño por subsistema

### 3.1 Piezas comunes

- **Alcance por proyecto (`ProjectScope`).** Raíces nuevas: `grievance`, `inquiry`, `stakeholder_request`,
  `social_risk`, `potential_impact`, `incident`, `media_mention`, `early_warning_signal` y `early_warning`. Hijos
  nuevos: `risk_assessment` y `mitigation_measure` (por `social_risk`). `early_warning_rule` tiene `project` opcional,
  como `evidence` y `export_log`: una regla sin proyecto es del tenant y `ProjectScopeGuard` no la acota.
- **Mismo proyecto (`ProjectLinkGuard`, `sgspe.scope`).** `ProjectLinks` sale de `sgspe.commitment` a `sgspe.scope`
  (depende solo de `ProjectScope` y de `common`) y una guarda general la aplica a los objetos de F4 y a
  `commitment.origin_grievance` y `commitment.social_risk`. Los campos juzgados salen del catálogo
  (`ModelCatalog.manyToOneFields` filtrado por `ProjectScope.covers`): `origin_engagement`, `origin_question`,
  `stakeholder`, `social_risk`, `incident`, `duplicate_of`, `reclassified_from_*`, `created_commitment`,
  `area_of_influence`, `key_theme`, `origin_incident`, `commitment`, `media_mention`, `early_warning` y
  `early_warning_rule` (una regla del tenant no discrepa). Además juzga los vínculos M2M `social_risk_stakeholder` e
  `incident_stakeholder`. Responde `409 PROJECT_MISMATCH` con el campo, como `CommitmentWriteGuard` en F3, y lee los
  destinos como quien escribe. `CommitmentWriteGuard` y `CandidateLinkRule` pasan a usar la misma pieza.
- **Origen único de una tarea (`ExactlyOneGuard`).** El grupo de `task` suma `grievance`, `inquiry`,
  `stakeholder_request`, `social_risk` y `early_warning`.
- **Vínculos de evidencias.** `POST /api/sgspe/evidence/{id}/links` acepta `grievance`, `inquiry`,
  `stakeholder_request`, `social_risk`, `incident` y `media_mention`. Vincular una evidencia a una QCS confidencial
  exige que su clasificación sea CONFIDENCIAL o DATOS PERSONALES: si no, `409 EVIDENCE_NOT_RESTRICTED`.
- **Bajas (`DependentsDeleteGuard`).** Lo cubre el catálogo. Nadie tiene D sobre las raíces de F4; `SOCIAL_RISK_MANAGER`
  borra medidas, impactos y reglas, y una regla con alertas responde `409 HAS_DEPENDENTS` (se desactiva con `enabled`
  falso, para no perder el origen de sus alertas).
- **Parámetros (`ProjectParameters`).** Suma los ocho de F4 con su valor por defecto: `grievance_ack_days` 5,
  `inquiry_response_days` 15, `grievance_resolution_days` 30, `policy_high_severity_requires_approver` verdadero,
  `risk_review_days` 7, `risk_medium_min_score` 5, `risk_high_min_score` 10 y `risk_critical_min_score` 15.
  `ProjectWriteGuard` rechaza con `400 OUT_OF_RANGE` un plazo o una cadencia menor que 1 o mayor que 365, y umbrales
  fuera de 1 a 25 o que no crezcan estrictamente (medio < alto < crítico).
- **Transiciones de comando (`CommandTransitionGuard`).** Suma `close_reclassified_*` de WF-E1, `close_reclassified` de
  WF-E2, `close_reclassified_*` y `accept` de WF-E3, `close_accepted` de WF-E3 (la aplica `RequestClosureListener`) y
  `assess` de WF-F. Por la API genérica responden `409 COMMAND_ONLY`.
- **Idempotencia.** Todos los comandos `POST` nuevos honran `Idempotency-Key` con `Idempotency` de F3: derivación,
  las tres reclasificaciones, la aceptación y las revisiones de riesgo.
- **`generator_key` de las alertas.** `GeneratorKeyGuard` suma `early_warning.generator_key`: solo lo escribe JOB-04, y
  una persona que lo fija, lo cambia o lo vacía recibe `403 GENERATOR_KEY_PLATFORM_ONLY`.
- **Conteo de planteamientos (`AttentionCounting`, `sgspe.common`).** Una función pura con la regla de §3.3 de
  `dashboards.md`: cada planteamiento cuenta una vez, como el último registro de la cadena; los originales a los que
  apunta un `reclassified_from_*` no cuentan. La usan el reporte, los KPI-GRV, la exportación PUBLICO y la regla CONTEO
  DE QCS de JOB-04. Vive en `common` porque `privacy` solo depende de `common`.

### 3.2 Quejas, consultas y solicitudes

**Objetos.** Las tres fichas de `database-model.md` §12, sin cambios salvo:

- `grievance.response_due_date` queda siempre vacío: la queja mide `ack_due_date` y `resolution_due_date`, y el campo
  común solo lo usan las consultas.
- `stakeholder_request.response_due_date` y su `deadline_status` quedan vacíos mientras GAP-005 no fije un plazo de
  solicitudes.
- La acción declarada `READ_CONFIDENTIAL` se suma a las tres (`grievance`: `APPROVE`, `EXPORT`, `READ_CONFIDENTIAL`;
  `inquiry` y `stakeholder_request`: `EXPORT`, `READ_CONFIDENTIAL`).
- `person` de las tres es campo restringido: lo leen `SOCIAL_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` y
  `GRIEVANCE_MANAGER` (§7.9 de `security.md`).

**Workflows.** WF-E1, WF-E2 y WF-E3 como los diseña `workflows.md`, con dos cambios: `accept` y `close_accepted` de
WF-E3 pierden `SOCIAL_INVESTMENT_MANAGER` hasta F5 (regla de fase de `task.json`), y los roles `GRIEVANCE_MANAGER` y
`SOCIAL_RISK_MANAGER` entran en las transiciones de WF-N.

**`AttentionWriteGuard` (`sgspe.attention`).** Reglas puras con su código de error:

| Regla | Cuándo | Error |
|---|---|---|
| Anónima sin identidad | Alta o cambio con `anonymous` verdadero y `person` o `stakeholder` informado | `400 ANONYMOUS_WITH_IDENTITY` sobre el campo |
| Confidencial con receptor | Alta de una QCS con `confidential` verdadero cuyo `received_by_user_id` está vacío o no es quien escribe, sin `READ_CONFIDENTIAL`; el portal lo llena siempre con quien registra, así que registrar nunca se rechaza | `400 CONFIDENTIAL_RECEIVER_REQUIRED` sobre `received_by_user_id` |
| Confidencialidad protegida | Quitar `confidential`, o cambiar `owner_user_id` o `received_by_user_id` de una QCS confidencial, sin `READ_CONFIDENTIAL` | `403 ACTION_REQUIRED` sobre el campo |
| Atender con datos | `investigate` y `start_action` sin `category` o sin `severity` | `400 FIELD_REQUIRED` |
| Resolver con datos | `resolve` y `resolve_high_severity` sin `resolution` o sin `response` | `400 FIELD_REQUIRED` |
| Queja grave | `resolve` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo | `409 SENIOR_APPROVAL_REQUIRED` |
| Doble llave | `resolve_high_severity` sin `APPROVE` sobre `grievance` | `403 ACTION_REQUIRED` |
| Reapertura | `reopen` con `complainant_conformity_given` verdadero | `409 CONFORMITY_GIVEN` |
| Respuesta comunicada | `close_resolved`, `close`, `reject_*` (WF-E1), `answer`, `close_referred` (WF-E2), `accept`, `close_declined` (WF-E3) sin `responded_at`; `reject_*`, `answer`, `refer`, `decline`, `close_unfulfilled` y `accept` sin `response` | `400 FIELD_REQUIRED` |
| Duplicado | `duplicate_of` que apunta a la misma queja | `400 INVALID_LINK` |
| Sin ejecución | `close_unfulfilled` con el compromiso creado fuera de `cancelled` | `409 STATE_MISMATCH` sobre `created_commitment` |

**Comandos.**

| Comando | Qué hace en una transacción | Errores propios |
|---|---|---|
| `POST /api/sgspe/grievances/{id}/reclassify` y sus pares | Crea la QCS de `targetType` copiando los campos comunes (canal, `received_at` del primer registro de la cadena, quien recibió, anonimato, confidencialidad, categoría, descripción, prioridad, responsable, ubicación, grupo de interés, persona, localidad, comunidad, tema, interacción y pregunta de origen) más `reclassified_from_<tipo>`; `attributes` puede completar o cambiar los campos no derivados salvo `received_at` y los vínculos de reclasificación; aplica `close_reclassified_*` al original con el motivo de la petición | `400 INVALID_TARGET` sobre `targetType` si es el mismo tipo o no es uno de los tres; `409 STATE_MISMATCH` si el original no está en un estado con transición de reclasificación |
| `POST /api/sgspe/stakeholder-requests/{id}/accept` | Escribe `response` y `responded_at`, crea el compromiso en `draft` con `attributes` y el vínculo, y aplica `accept` | `400 INVALID_TARGET` sobre `creates` si no es `commitment` (el aporte llega en F5); `409 STATE_MISMATCH` fuera de `under_evaluation` |
| `POST /api/sgspe/engagement-questions/{id}/referral` | Crea la QCS de `target` con `attributes` (sujetos a `AttentionWriteGuard`) más `origin_question`, `origin_engagement` (la de la pregunta), el proyecto de la interacción, canal REUNION y `received_at` igual al `held_at` de la interacción; escribe `referred_at` en la pregunta con la marca | `409 ALREADY_REFERRED` si la pregunta ya tiene `referred_at`; `409 STATE_MISMATCH` si la interacción no está entre `held` y `minutes_approved`; `400 INVALID_TARGET` sobre `target` |

**Listeners.** `QcsDueDatesListener` (alta de las tres; con el calendario de §3.4), `QcsTransitionDatesListener`
(`resolved_date` y `closed_date`) y `RequestClosureListener` (cierre del compromiso), como los diseña `workflows.md`.
JOB-02 completa los plazos que falten. `EngagementWriteGuard` (RN-ENG-005) cuenta como atendida una pregunta con
`referred_at`.

**Reporte unificado.** `GET /api/sgspe/attention/report` con los parámetros `project`, `periodStart`, `periodEnd`,
`type`, `channel`, `category`, `page` y `size` lee como quien llama y responde
`200 { totals, byType, confidentialHidden, page }`:

- `totals` y cada fila de `byType`: `received`, `open`, `closed`, `rejected`, `withdrawn`, `absolved` y `overdue`, con
  `AttentionCounting`; `byType` además `byState`, `byChannel` y `byCategory`;
- `page`: `items` con `type`, `id`, `code`, `state`, `channel`, `category`, `severity`, `receivedAt`, `deadlineStatus`,
  `dueDate` (el plazo que corre en el estado), `ownerEmail`, `respondedAt` y `anonymous`; sin texto libre ni persona;
- un tipo que quien llama no lee sale `available: false`; el tope es `sgspe.exports.max-rows` (`400 TOO_MANY_RECORDS`);
  un parámetro desconocido es `422` (ASM-054).

### 3.3 Confidencialidad de las QCS (`RecordReadScope`)

**Quién ve qué.** Para `grievance`, `inquiry` y `stakeholder_request`:

| Quien llama | Ve una QCS confidencial |
|---|---|
| `ADMIN` (también `SUPER_ADMIN` y `ORGANIZATION_ADMIN`) | Siempre: wasichai no aplica los `RecordReadScope` al `ADMIN` |
| Con `READ_CONFIDENTIAL` sobre el objeto (`GRIEVANCE_MANAGER`) | Siempre |
| Su responsable (`owner_user_id` igual a quien llama) | Siempre |
| Quien la recibió (`received_by_user_id` igual a quien llama) | Siempre |
| Con `APPROVE` sobre `grievance` (`APPROVER`) | Solo una queja con `severity` ALTA o CRITICA |
| Cualquier otro rol con READ | Nunca |

Una QCS sin `confidential` verdadero la ven todos los que tienen READ, como hasta ahora.

**Componente.** `ConfidentialAttentionScope` (`sgspe.scope`) implementa `RecordReadScope.criterion(user, definition)`:

- para un objeto que no es una de las tres QCS, no restringe;
- con `READ_CONFIDENTIAL` sobre el objeto, no restringe;
- si no, devuelve la condición
  `confidential IS NOT TRUE OR owner_user_id = <quien llama> OR received_by_user_id = <quien llama>` y, en
  `grievance` con `APPROVE`, además `OR severity IN ('ALTA', 'CRITICA')`, con los nombres de columna de la definición
  y valores enlazados (nunca concatenados);
- las acciones de quien llama salen de la misma consulta de permisos que usa `sgspe.common.Permissions`, memorizada por
  petición (`RequestMemo`);
- **falla cerrado:** si la definición no trae `confidential`, `owner_user_id` o `received_by_user_id`, o si la consulta
  de permisos falla, devuelve una condición que no admite ningún registro del objeto y deja una advertencia en el log
  sin datos del registro.

**Dónde rige.** wasichai lo aplica en listas, detalle, relacionados, historial, auditoría, transiciones, features GIS y
agente. Consecuencias:

- **Escrituras.** Quien no lee una QCS no la cambia ni la transiciona (wasichai la trata como inexistente). Las reglas
  de `AttentionWriteGuard` de §3.2 cubren la marca y el responsable.
- **Vínculos desde otros objetos.** Una tarea, una evidencia, un compromiso (`origin_grievance`) o una pregunta pueden
  apuntar a una QCS que quien lee no ve: el id es visible, el registro no. El portal muestra «Registro reservado». Los
  títulos de las tareas automáticas solo llevan el código de la QCS.
- **Listeners y comandos.** Corren como quien escribe; por la regla «confidencial con receptor», quien registra la
  ve.
- **Trabajos.** JOB-01, JOB-02, JOB-04 y `DeadlineNotices` leen como la plataforma, sin el alcance: ven todo, y lo que
  producen se filtra en la salida (avisos) o es agregado (conteos de JOB-04).
- **Indicadores y reporte.** Leen como quien llama: excluyen lo que no ve. La respuesta del tablero `attention`, de cada
  KPI-GRV y del reporte lleva `confidentialHidden: true` cuando quien llama no es `ADMIN` ni tiene `READ_CONFIDENTIAL`
  sobre los tres objetos; nunca el número de excluidas. KPI-ENG-05 no puede contar como absuelta una pregunta derivada
  a una QCS que quien llama no ve, así que lleva la misma marca.
- **Exportaciones.** INTERNO y PUBLICO leen como quien exporta; EXTERNO excluye toda QCS confidencial, también para
  `GRIEVANCE_MANAGER` (§5).
- **Avisos.** Un aviso sobre una QCS confidencial (`qcs_deadline`, `approval_pending` de queja grave y `task_overdue` de
  una tarea vinculada a ella) solo va a quien la vería: su responsable, quien la recibió, los asignados cuyo `role_name`
  tiene `READ_CONFIDENTIAL` sobre ese objeto en `model/roles.json` y, en una queja ALTA o CRITICA, los que tienen
  `APPROVE` sobre `grievance`. `ModelCatalog` expone las concesiones de cada rol para decidirlo sin literales. Si un
  nivel queda sin destinatarios, no se crea el aviso y queda una advertencia en el log, como hoy; la tarea de
  escalamiento se crea igual. Excepción del nivel 2: los roles de `approve_senior` de WF-D que no la ven reciben el
  aviso genérico `qcs_confidential_overdue` (§3.5).
- **Capa LYR-20.** Lee como quien llama: hereda el alcance.

**Pruebas.** `ConfidentialAttentionScopeTest` (unitaria: condición por caso, falla cerrada) y
`ConfidentialAttentionApiTest` (integración: un relacionista que no es responsable ni la recibió no la ve en lista,
detalle, historial, relacionados ni transiciones; el responsable, quien la recibió, el gestor de quejas y el `ADMIN`
sí; el aprobador solo la grave; el tablero la excluye con `confidentialHidden`; EXTERNO la excluye también para el
gestor; un aviso con datos no llega a quien no la ve y el nivel 2 genérico no lleva código, título ni enlace; quitar
la marca sin la acción es `403`).

### 3.4 Calendario de días hábiles

**Forma de la configuración.** Una lista de feriados nacionales por año, por despliegue (no por organización), en
`application.yml`:

```yaml
sgspe:
  calendar:
    holidays:
      '2026':
        - 2026-01-01
        - 2026-07-28
```

El ejemplo muestra la forma; el plan 4.2a escribe las listas completas de 2026 y 2027 verificadas contra la norma
vigente publicada en El Peruano, y cada año alguien de operación agrega el siguiente.

**Componentes (`sgspe.common`).**

- `HolidayCalendar` (`@ConfigurationProperties("sgspe.calendar")`): valida al arrancar que cada fecha pertenezca a su
  año y que no se repita; una fecha inválida impide arrancar.
- `BusinessDays` (pura): un día es hábil si es de lunes a viernes y no está en la lista de su año.
  `addBusinessDays(start, n)` devuelve el n-ésimo día hábil posterior a `start`: el día de recepción no cuenta.
- **Año faltante.** Si el año de un día evaluado no está en la configuración, ese año usa lunes a viernes y se registra
  una advertencia `sgspe.calendar: sin feriados configurados para <año>; se usan lunes a viernes`, una sola vez por año
  y proceso. Al arrancar se advierte igual si falta el año en curso.
- **Zona.** La fecha de recepción es `received_at` llevado a America/Lima (`LIMA`); los plazos son fechas de Lima.

**Uso.** `ack_due_date = addBusinessDays(fecha de recepción, grievance_ack_days)`; `resolution_due_date` e
`inquiry.response_due_date` suman días calendario. Se calculan al recibir (o los completa JOB-02) y no se recalculan si
después cambia la lista o un parámetro.

### 3.5 Plazos, escalamiento, tareas y avisos

**JOB-01 (`DeadlineJob`).** Recorre por objeto: `Organizations.having("grievance")` para las QCS y
`having("social_risk")` para los riesgos, así un tenant que todavía no tiene el modelo de F4 sigue con F3. Escribe como
la plataforma, solo si cambia y con el motivo `situación de plazo (JOB-01 DeadlineJob)`.

| Objeto | Mide | En | Escribe | Tarea |
|---|---|---|---|---|
| `grievance` | `ack_due_date` en `received`; `resolution_due_date` en `acknowledged`, `in_assessment`, `in_investigation`, `in_action` | Esos estados; vacío en los demás | `deadline_status` | `escalation:grievance:<id>:<fecha del plazo>:<nivel>` en los niveles 1 y 2, sin asignado ni fecha |
| `inquiry` | `response_due_date` en `received` | `received` | `deadline_status` | `escalation:inquiry:<id>:<fecha del plazo>:<nivel>` |
| `stakeholder_request` | — | — | Nada en el MVP | — |
| `social_risk` | `next_review_date` en `assessed`, `mitigating`, `monitoring` | Esos estados | `review_status`; `next_review_date` si cambió `review_every_days` (última revisión + cadencia) | `risk_review:<id>:<next_review_date>` al pasar a POR REVISAR o REVISION VENCIDA, asignada al responsable, con fecha `next_review_date` y título «Revisar el riesgo <código>» |

- **Situación de una QCS.** AL DIA, POR VENCER o VENCIDO con `Deadlines.status`; POR VENCER empieza
  `min(due_soon_days, ⌊d / 2⌋)` días antes del plazo, donde `d` son los días calendario entre la fecha de recepción y
  el plazo.
- **Situación de revisión.** AL DIA; POR REVISAR desde el día anterior a `next_review_date` y ese día; REVISION VENCIDA
  desde el día siguiente.
- **Niveles.** 0 al vencer; 1 desde `escalation_first_days` días de atraso; 2 desde `escalation_second_days`
  (`Deadlines.level`), con la misma regla para la revisión vencida.

**Avisos.**

| Aviso | Origen | Disparador | Destinatarios | Clave | Se resuelve |
|---|---|---|---|---|---|
| `qcs_deadline` | `DeadlineNotices` | `deadline_status` de una queja o consulta en POR VENCER (situación) o VENCIDO (niveles 0, 1 y 2) | Nivel 0: responsable o, sin él, los roles de la transición que sigue (`workflows.md`); nivel 1: roles de `verify` de WF-E1; nivel 2: roles de `approve_senior` de WF-D; con el filtro de §3.3 | `qcs_deadline:<objeto>:<id>:<fecha del plazo>:<POR VENCER o nivel>` | Cuando la situación deja de ser cierta |
| `risk_escalated` por nivel | `RiskNoticeListener` (`sgspe.notification`), en la transacción del endpoint de revisiones | Una revisión no residual sube `risk_level` a ALTO o CRITICO (de un nivel menor, o de ALTO a CRITICO) | Responsable del riesgo y roles de la transición de tratamiento que sigue | `risk_escalated:<id>:<nivel de riesgo>:<assessment_date>` | Con la revisión siguiente o el cierre del riesgo |
| `risk_escalated` por revisión vencida | `DeadlineNotices` | `review_status` REVISION VENCIDA (niveles 0, 1 y 2) | Nivel 0: responsable; nivel 1: roles de la transición de tratamiento que sigue; nivel 2: roles de `approve_senior` de WF-D | `risk_escalated:<id>:<nivel>:<next_review_date>` | Con la revisión que mueve `next_review_date` |
| `warning_new` | `WarningNoticeListener` (`sgspe.notification`) | Alta de `early_warning`, por una persona o por JOB-04 | Responsable de la alerta, si lo tiene, y roles de `start_triage` | `warning_new:<id>` | Con `start_triage` |
| `approval_pending` de queja grave | `ApprovalNoticeListener` (paso nuevo) | `start_action` o `start_action_after_investigation` de una queja ALTA o CRITICA con la política activa; o un cambio de `severity` a ALTA o CRITICA en `in_action` | Roles de `resolve_high_severity` | `approval_pending:grievance:<id>` | Al salir de `in_action` o si la severidad baja de ALTA |
| `qcs_confidential_overdue` | `DeadlineNotices` | Nivel 2 de `qcs_deadline` de una QCS confidencial | Los asignados con un rol de `approve_senior` de WF-D que no la verían por §3.3 (los que sí la ven reciben `qcs_deadline`) | `qcs_confidential_overdue:<proyecto>:<huella>`, donde `<huella>` son los 16 primeros dígitos hexadecimales del SHA-256 de `<objeto>:<id>:<fecha del plazo>`: un aviso por QCS y plazo sin poner su id, tipo ni fecha en la clave | Cuando la QCS deja el nivel 2 o el estado con plazo |

Los textos (`NoticeTexts`) llevan el código, el tipo y la fecha; nunca descripción, persona ni texto libre. El de
`qcs_confidential_overdue` es fijo salvo el nombre del proyecto: «Una QCS confidencial del proyecto <proyecto> superó
su plazo. Pida el detalle al equipo de quejas.» No lleva código, título, tipo, fecha, nivel, categoría ni enlace al
registro, y es `ACTION`; una prueba de `NoticeTexts` verifica que ningún dato del registro entra en el texto ni en la
clave. La tarea de escalamiento del nivel 2 se crea igual, con su clave `escalation:<objeto>:<id>:<fecha>:2`.

**Automatizaciones.** AUT-02 (tarea de acuse), AUT-09 (tarea de triaje) y AUT-11 (tarea de riesgo crítico), como las
diseña `workflows.md`. El plan 4.2b prueba que la escritura de `risk_level` del endpoint dispara AUT-11 y que una alerta
creada por JOB-04 como la plataforma dispara AUT-09 y `warning_new` (§8, riesgos).

### 3.6 Riesgos

**Objetos.** Las cuatro fichas de `database-model.md` §13. `risk_assessment` es `appendOnly` + `apiOnly`; nadie la
borra ni la cambia.

**WF-F y `RiskWriteGuard` (`sgspe.risk`).** Como lo diseña `workflows.md`, con `assess` como transición de comando:

| Regla | Transiciones | Error |
|---|---|---|
| Evaluado con responsable | `assess`: probabilidad, impacto y `owner_user_id` informados | `400 FIELD_REQUIRED` |
| Mitigar con medida | `decide_mitigation` exige `treatment_decision` MITIGAR; `decide_mitigation` y `resume_mitigation` exigen al menos una `mitigation_measure` con responsable | `409 MEASURE_OWNER_REQUIRED` |
| Monitorear con residual | `decide_monitoring` (con `treatment_decision` MONITOREAR o ACEPTAR) y `start_monitoring`: al menos una revisión residual | `409 RESIDUAL_ASSESSMENT_REQUIRED` |
| Cierre con motivo | `close_*`: `close_reason` informado | `400 FIELD_REQUIRED` |

**Endpoint de revisiones.** `POST /api/sgspe/social-risks/{id}/assessments` con
`{ attributes: { assessment_date, probability, impact, residual, notes } }`, en una transacción:

1. valida `probability` e `impact` enteros de 1 a 5 (`400 OUT_OF_RANGE`), `assessment_date` no futura en Lima
   (`400 OUT_OF_RANGE`) y no anterior a la última revisión del mismo tipo, residual o no
   (`409 ASSESSMENT_OUT_OF_ORDER`); un riesgo `closed` responde `409 STATE_MISMATCH`, y la primera revisión no puede
   ser residual (`409 FIRST_ASSESSMENT_NOT_RESIDUAL`);
2. calcula `score` = P × I y el nivel con `RiskLevels` (pura): CRITICO desde `risk_critical_min_score`, ALTO desde
   `risk_high_min_score`, MEDIO desde `risk_medium_min_score`, BAJO por debajo; umbrales inválidos guardados antes de la
   regla de §3.1 responden `409 RISK_THRESHOLDS_INVALID`;
3. calcula `trend` frente a la revisión anterior del mismo tipo: AUMENTA, SE MANTIENE o DISMINUYE según el puntaje;
   vacía en la primera;
4. crea la `risk_assessment` con quien revisa;
5. actualiza los valores vigentes (no residual) o los residuales, y `next_review_date` = `assessment_date` +
   (`review_every_days` o `risk_review_days`), con la marca;
6. en la primera revisión aplica `assess` con el motivo de la petición.

Permisos: CREATE sobre `risk_assessment`, UPDATE sobre `social_risk` y, en la primera, el rol de `assess`. Respuesta
`201 { assessment, risk }`. Un cambio de umbrales del proyecto no recalcula los niveles guardados: los cambia la
revisión siguiente.

### 3.7 Alerta temprana e incidentes

**Objetos.** Las cinco fichas de `database-model.md` §14, más `early_warning.generator_key` (TEXT, único, como
`task.generator_key`). Las menciones se registran a mano y su URL nunca se descarga; las señales también se registran a
mano, y quien hace el triaje las vincula a la alerta (`early_warning_signal.early_warning`) desde S41.

**WF-J y WF-M (`WarningWriteGuard`, `sgspe.warning`).**

| Regla | Dónde | Error |
|---|---|---|
| Decisión humana | `confirm` y `dismiss` sin usuario, o con una cuenta de servicio (correo `@service-accounts.invalid`) | `403 HUMAN_DECISION_REQUIRED` |
| Doble llave | `confirm` y `dismiss` sin `CONFIRM_ALERT` | `403 ACTION_REQUIRED` |
| Cierre del incidente | `close` de WF-M sin `immediate_response` o sin `consequences` | `400 FIELD_REQUIRED` |
| Regla válida | Alta o cambio de `early_warning_rule` con `threshold` vacío, no entero o fuera de rango (§4), o `window_days` fuera de 1 a 365 | `400 OUT_OF_RANGE` sobre el campo |

`dismiss` exige motivo (`TransitionReasonGuard`).

**JOB-04 (`WarningRulesJob`).** Cada hora (configurable), una réplica (`ClusterLock` con la clave `sgspe.warning`),
`Organizations.having("early_warning_rule")`, como la plataforma de cada organización con `source = job:warning-rules`
y el motivo `alerta por regla (JOB-04 WarningRulesJob)`. Una organización que falla se registra y no detiene a las
demás, como `DeadlineSweep`. Por organización:

1. lee las reglas con `enabled` verdadero y los proyectos en `active` o `suspended`;
2. para cada proyecto, las reglas que aplican: las del proyecto y, de cada tipo que el proyecto no tiene, las del
   tenant;
3. evalúa cada regla con su evaluador puro (§4), con conteos (`Records.count`), nunca cargando los registros;
4. si dispara, crea la alerta salvo que haya una abierta (fuera de `dismissed` y `closed`) de la misma regla y proyecto
   (y riesgo, en NIVEL DE RIESGO) o una creada en los últimos `window_days` días;
5. la alerta nace en `detected` con `title` = «<nombre de la regla>: <tipo>», `trigger_description` con el conteo, el
   umbral y la ventana (§4), `severity` de la regla (MEDIO si está vacía), `detected_at` = ahora, `early_warning_rule`,
   `project`, `ai_detected` falso y `generator_key` = `warning_rule:<regla>:<proyecto>[:<riesgo>]:<fecha de Lima>`; el
   único frena una carrera entre réplicas, como en `JobTasks`.

Nunca aplica transiciones ni confirma (RN-EWS-001), y ningún texto que escribe lleva datos personales, texto libre de
otros registros ni valores de campos restringidos.

### 3.8 Indicadores y tableros

- `AttentionKpis` (KPI-GRV-01 a 10), `RiskKpis` (KPI-RSK-01 a 05) y `EngagementKpis` (suma KPI-ENG-06 a 08, solo por
  `GET /api/sgspe/dashboards/indicators/{code}`), con el marco de F3 (`Kpis`, `Indicators`, `Dashboards`,
  `DashboardData`, `DashboardFilters`). Los tableros `attention` y `risks` siguen `dashboards.md` §5.7 y §5.8.
- KPI-GRV-10 mide las quejas contra `resolution_due_date` y las consultas contra `response_due_date`; las solicitudes,
  solo la mediana.
- Los tableros son internos y no aplican celdas pequeñas: KPI-GRV-08 por grupo de interés lee como quien llama, así que
  las QCS confidenciales ya quedan fuera para quien no las ve. `confidentialHidden` va en la respuesta del tablero
  `attention`, de cada KPI-GRV y de KPI-ENG-05.
- KPI-EXE-02 llega en F8; F4 deja sus datos (`early_warning` con `severity`, `detected_at` y territorio).
- Pruebas: `AttentionDashboardApiTest` y `RisksDashboardApiTest`.

### 3.9 Documentos emitidos

Ningún tipo de documento ni `GENERATE_DOCUMENT` en F4. `workflows.md` nombra los tres tipos para cuando se resuelva
UPS-23: «Cargo de queja» (`grievance-receipt`, prefijo `CQ`, `grievance`), «Carta de respuesta» de consulta
(`inquiry-answer-letter`, `CC`, `inquiry`) y de solicitud (`request-answer-letter`, `CS`, `stakeholder_request`), con
AUT-06, AUT-07 y AUT-08 marcadas «F4, tras UPS-23». Mientras tanto el cargo y la carta se imprimen fuera de SGSPE, se
entregan en papel y, firmados, se suben como evidencia FORMULARIO (cargo) o CARTA (carta) vinculada a la QCS.

## 4. Los seis tipos de regla de alerta

Reglas comunes:

- `threshold` es un número entero; un DECIMAL con parte fraccionaria se rechaza (§3.7). `window_days` vacío vale 7.
- La ventana de los tipos por eventos es de `window_days` días de Lima que terminan hoy: desde el inicio del día
  `hoy − window_days + 1` hasta ahora.
- En todos los tipos, `window_days` es también el silencio tras una alerta de la misma regla y proyecto (y riesgo, en
  NIVEL DE RIESGO) (§3.7, paso 4).
- Dispara cuando el valor medido es mayor o igual que `threshold`.
- Una regla del tenant crea una alerta por cada proyecto que supera el umbral; una del proyecto, solo para el suyo.

| Tipo | Entrada (proyecto P) | `threshold` | Ventana | Deduplicación | Crea |
|---|---|---|---|---|---|
| CONTEO DE SEÑALES | `early_warning_signal` de P con `received_at` en la ventana, de cualquier fuente | Conteo, entero ≥ 1 | Sí | Regla y proyecto | Alerta con «<n> señales en <W> días (umbral <T>)» |
| CONTEO DE QCS | Planteamientos de P (las tres QCS con `AttentionCounting`, confidenciales incluidas) con `received_at` en la ventana | Conteo, entero ≥ 1 | Sí | Regla y proyecto | Alerta con «<n> QCS recibidas en <W> días (umbral <T>)», sin desglose |
| COMPROMISOS VENCIDOS | `commitment` de P en `approved`, `in_progress` o `blocked` con `deadline_status` VENCIDO, en el momento de la corrida | Conteo, entero ≥ 1 | No (solo silencio) | Regla y proyecto | Alerta con «<n> compromisos vencidos (umbral <T>)» |
| MENCIONES NEGATIVAS | `media_mention` de P con `tone` NEGATIVO y `published_date` en la ventana | Conteo, entero ≥ 1 | Sí | Regla y proyecto | Alerta con «<n> menciones negativas en <W> días (umbral <T>)» |
| INCIDENTES | `incident` de P con `occurred_at` en la ventana, de cualquier tipo y estado | Conteo, entero ≥ 1 | Sí | Regla y proyecto | Alerta con «<n> incidentes en <W> días (umbral <T>)» |
| NIVEL DE RIESGO | Cada `social_risk` de P en `assessed`, `mitigating` o `monitoring` con `score` vigente | Puntaje P × I mínimo, entero de 1 a 25 | No (solo silencio) | Regla, proyecto y riesgo | Una alerta por riesgo con `social_risk`, su `locality` y su `community`, y «Riesgo <código> con puntaje <s> (umbral <T>)» |

**Por qué no CAMBIO DE POSTURA.** Esa regla leería `stakeholder.stance` o `stakeholder_assessment.stance`, campos
restringidos a `SOCIAL_MANAGER` y `STAKEHOLDER_MANAGER` (GAP-024). JOB-04 lee como la plataforma, sin permisos de campo,
y la alerta que crearía diría en su título o en `trigger_description` qué grupo de interés cambió de postura y hacia
dónde. `early_warning` lo leen todos los roles operativos, y `warning_new` lo llevaría a la bandeja: la regla
publicaría un dato restringido. Queda fuera del MVP y el valor sale del catálogo del modelo; vuelve cuando la alerta
pueda llevar campos restringidos o un alcance por registro propio.

## 5. Exportaciones

Reglas comunes, las de `stakeholder-register` y `commitment-matrix`: `POST /api/sgspe/exports/{kind}` con
`{ filters, disclosureLevel, checklistConfirmed }`; proyectos con `ExportProjects` («mis proyectos» por defecto,
`403 NOT_ASSIGNED` con un solo proyecto ajeno); lectura como quien exporta, con «no disponible» en lo ilegible; tope
`sgspe.exports.max-rows` por conjunto y 2 000 registros cuyos vínculos se leen uno por uno, con 200 por registro
(`400 TOO_MANY_RECORDS`); EXTERNO exige `checklistConfirmed`; PUBLICO sin totales, sobre la grilla completa del catálogo
y con `SmallCells` (`reservado`); `safeCell` en todo texto; nunca el documento de identidad, teléfonos ni correos de
personas; una fila de `export_log` antes de entregar el archivo.

**Texto libre.** Los campos LONG_TEXT: en las QCS `description`, `response`, `resolution` y `requested_resource`; en los
riesgos `risk_source`, `treatment_rationale`, `current_controls`, `monitoring_sources` y `close_reason`; en las
revisiones `notes`; en medidas e impactos `description`. EXTERNO no lleva ninguno.

### 5.1 `attention-register`

- **Permiso.** READ y `EXPORT` sobre los tres objetos (hoy `SOCIAL_MANAGER`, `GRIEVANCE_MANAGER` y `ANALYST`); sin
  alguno, `403`. Un registro unificado con un tipo de menos engañaría.
- **Filtros.** `project` (lista), `periodStart`, `periodEnd`, `type` (lista), `state` (lista), `channel`, `category`
  e `includeReclassified`: el periodo filtra `received_at` en fechas de Lima; `state` toma estados de los tres
  workflows; `includeReclassified` (falso por defecto) agrega los originales reclasificados. `400 INVALID_FILTER`
  sobre el filtro.

| Nivel | Hojas | Columnas y exclusiones |
|---|---|---|
| INTERNO | «Quejas», «Consultas», «Solicitudes», «Resumen» | Comunes: código, canal, recibida (fecha y hora de Lima), recibida por (correo), anónima, confidencial, categoría, prioridad, estado, situación de plazo (solo en estados con plazo), plazo que corre, responsable (correo), grupo de interés (nombre), persona (nombres y apellidos si los lee; «anónima»; «no disponible»), localidad, comunidad, tema, descripción, respuesta, respondida, cerrada, reclasificada de (código). Queja: severidad, acuse, plazo de resolución, resuelta, resolución, conformidad. Solicitud: medio, recurso pedido, compromiso creado (código). «Resumen»: los conteos del reporte. Las confidenciales salen si quien exporta las ve |
| EXTERNO | «Quejas», «Consultas», «Solicitudes», «Notas» (sin «Resumen», cuyos conteos leen como quien exporta) | Sin QCS confidenciales (aunque quien exporta las vea), sin texto libre, sin persona, sin quien recibió ni responsable; grupo de interés por código `GI-`; una hoja «Notas» dice que las confidenciales y el texto libre no se incluyen, sin conteos |
| PUBLICO | «Por tipo y estado», «Por tipo y canal», «Por tipo y categoría», «Absueltas por tipo» | Conteos de planteamientos (`AttentionCounting`) del periodo: estado agrupado (abiertas, cerradas, rechazadas, desistidas, declinadas), canal y categoría sobre la grilla completa; «Absueltas por tipo» con recibidas y absueltas por tipo. Cuenta lo que quien exporta lee, confidenciales incluidas si las ve; «Notas» dice si el conteo incluye las confidenciales (sí o no). Sin territorio ni grupo de interés |

- **`export_log`.** `object_name` = `grievance,inquiry,stakeholder_request`; `row_count` cuenta planteamientos.

### 5.2 `risk-register`

- **Permiso.** READ y `EXPORT` sobre `social_risk` (`SOCIAL_MANAGER`, `SOCIAL_RISK_MANAGER`, `ANALYST`).
- **Filtros.** `{ project: string[], state: string[], category: string[], riskLevel: string[], includeClosed }`;
  `includeClosed` falso por defecto.

| Nivel | Hojas | Columnas y exclusiones |
|---|---|---|
| INTERNO | «Matriz», «Revisiones», «Medidas», «Impactos» | «Matriz» con los campos de DOC-01 §9.3.2: código, título, fuente, categoría, tipo y naturaleza del impacto, P, I, puntaje, nivel, tendencia y fecha de la última revisión, decisión de tratamiento y su razón, acciones actuales y su adecuación, fuentes de monitoreo, P, I, puntaje y nivel residuales, próxima revisión, situación de revisión (solo en estados que la calculan), estado, responsable (correo), localidad, comunidad, área de influencia, conflicto local, tema clave, incidente de origen (código), grupos de interés (nombres), motivo de cierre. «Revisiones»: todas, con quien revisó. «Medidas» y «Impactos» completos |
| EXTERNO | «Matriz», «Revisiones», «Impactos» | Sin texto libre ni correos; grupos de interés por código `GI-`; la «Matriz» suma medidas totales, completadas y atrasadas en lugar de la hoja «Medidas»; «Impactos» con actividad, tipo, naturaleza, signo y riesgo |
| PUBLICO | «Por nivel y categoría», «Por tratamiento y nivel» | Conteos de riesgos activos sobre la grilla completa (nivel × categoría; decisión de tratamiento, con «sin decidir», × nivel), con `SmallCells` |

- **`export_log`.** `object_name` = `social_risk`; `row_count` cuenta riesgos.
- Pruebas: `AttentionRegisterApiTest` y `RiskRegisterApiTest`, con un caso por nivel, la exclusión de confidenciales y
  de texto libre en EXTERNO, la grilla con `reservado` y los topes.

## 6. Cambios en los documentos (plan 4.0)

**IDs nuevos en `docs/requirements-analysis.md`** (siguientes libres: ASM-095 y UPS-25; §20.1 pasa de ASM 94 a 104, de
UPS 24 a 26 y de 508 a 520 en total, y §20.4 los consolida):

| ID | Contenido |
|---|---|
| ASM-095 | Confidencialidad por registro de las QCS (§3.3): quién ve, la acción `READ_CONFIDENTIAL`, el aprobador en las quejas graves, quien la recibió, el registrante como receptor; reversible quitando el alcance o con la opción B de CON-041 |
| ASM-096 | Días hábiles (§3.4): lunes a viernes menos los feriados nacionales configurados por año y despliegue; año faltante con lunes a viernes y advertencia; el día de recepción no cuenta; los plazos no se recalculan; reversible a un catálogo por organización (ASM-017) |
| ASM-097 | Semántica de los seis tipos de regla, precedencia de la regla del proyecto, silencio de `window_days` y CAMBIO DE POSTURA fuera del MVP (§4) |
| ASM-098 | «Cargo de queja» y «Carta de respuesta» esperan UPS-23; el firmado se sube como evidencia FORMULARIO o CARTA; sin avisos al reclamante en el MVP (amplía ASM-090) |
| ASM-099 | Revisiones de riesgo: cadencia desde la última revisión, `assess` por comando, revisiones en orden de fecha, la primera no residual, POR REVISAR desde el día anterior, tarea `risk_review:<id>:<next_review_date>` |
| ASM-100 | Plazos de QCS: acuse en días hábiles, respuesta y resolución en días calendario desde la fecha de recepción en Lima; sin pausa; solicitudes sin plazo; `grievance.response_due_date` vacío; tope de POR VENCER a la mitad del plazo |
| ASM-101 | Contenido de `attention-register` y `risk-register` por nivel (§5), con `EXPORT` sobre los tres objetos de QCS |
| ASM-102 | Avisos de una QCS confidencial solo a quien la vería, salvo el aviso genérico `qcs_confidential_overdue` del nivel 2, sin datos del registro; queja ALTA o CRITICA avisa a los roles de `resolve_high_severity` (§3.5) |
| ASM-103 | Los incidentes no tienen severidad, `requiresReason` ni escalamiento en el MVP |
| ASM-104 | Solo el equipo social (CRO y GVM) registra QCS; sin canal público ni recepción en línea en el MVP |
| UPS-25 | Candidata: alta pública acotada por tenant y objeto, sin usuario (cadena de seguridad, captcha y límite de tasa de la plataforma); necesaria después del MVP; sin issue hasta la aprobación del usuario |
| UPS-26 | Candidata: avisos a destinatarios que no son usuarios (correo, SMS o WhatsApp) en wasichai-notifications; necesaria después del MVP; sin issue hasta la aprobación del usuario |

**Huecos que cierra.**

- **C1** (documentos frente a UPS-23): `workflows.md` (AUT-06 a AUT-08 «F4, tras UPS-23» y los tres tipos de documento
  en su tabla), `roadmap.md` F4 «Entregables», `security.md` §11.4 y `domain-model.md` §7.3; §10 del análisis marca
  CARGO DE QUEJA y CARTA DE RESPUESTA «tras UPS-23».
- **C7** (días hábiles): `architecture.md` (`HolidayCalendar` y `BusinessDays` en `sgspe.common`), `database-model.md`
  (`grievance_ack_days`), la fila de RNF-I18N-002 en la trazabilidad pasa de «Upstream (UPS-15)» a «Diseñado» y nombra
  a `BusinessDaysTest` y `QcsDueDatesApiTest`.
- **C10** (reglas de alerta): `workflows.md` gana la sección «Tipos de regla de JOB-04» con la tabla de §4 y la fila de
  JOB-04 se reescribe (precedencia, silencio, clave); §10 del análisis quita CAMBIO DE POSTURA de los valores del MVP;
  `database-model.md` suma `early_warning.generator_key`. Cierra también **C6**.
- **C12** (exportaciones): `api.md` §4.13 y `deliverables.md` §10 ganan el contrato de §5.

**Contradicciones menores.**

- **C2:** la trazabilidad pasa RF-GRV-001, RF-GRV-006, RF-RSK-001 y RN-RSK-002 de «Decisión pendiente» a «Diseñado».
- **C3:** `roadmap.md` F3 «Estado» registra el portal integrado y deja pendientes el tenant de desarrollo y MinIO.
- **C4:** `workflows.md` «Avisos»: `qcs_deadline` y la revisión vencida de `risk_escalated` salen de `DeadlineNotices`;
  la subida de nivel, de `RiskNoticeListener`; `warning_new`, de `WarningNoticeListener`; la regla 6 de «Reglas de
  SGSPE» y la tabla de «Listeners» los nombran; suma la fila de `approval_pending` de queja grave (decisión 8).
- **C5:** WF-E3 deja `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted` marcado «desde F5».
- **C8:** clave, responsable y fecha de la tarea de revisión en la fila de JOB-01.
- **C9:** `assess` de WF-F entra en «Transiciones de comando» y sale de «Otras transiciones».
- **C11:** `ux/screens.md` da a la ficha de consulta y solicitud su propio id (el siguiente libre), agrega fichas de
  menciones, señales, reglas e impactos, describe las medidas dentro de S40 y el flujo «Crear compromiso desde esta
  queja» (S30 con `origin_grievance`), el interruptor de confidencialidad de S37 y «Registro reservado».
- **C13:** `api.md` §3.3 y §4.6 nombran `PROJECT_MISMATCH` en las relaciones de F4.
- **C14:** `dashboards.md` §4.5 y §8: los tableros son internos, sin celdas pequeñas, y excluyen lo que quien llama no
  ve, con `confidentialHidden`.
- **C15:** `workflows.md` «Plazos y escalamiento»: la solicitud no tiene plazo en el MVP y `grievance.response_due_date`
  queda vacío.
- **C16:** `roadmap.md` F4: el escalamiento cubre QCS y riesgos; los incidentes no escalan.
- **C17:** `roadmap.md` F4 «Definición de terminado» suma la actualización del tenant de desarrollo a F4, la
  comprobación de MinIO y la decisión de UPS-01 antes de la producción con varios proyectos.

**Además.**

- `security.md`: §5.3 suma `READ_CONFIDENTIAL` al catálogo de RQ-SEC-004; §7.6 la concede a `GRIEVANCE_MANAGER`; §9
  explica que F4 adopta `RecordReadScope` solo para las QCS confidenciales y que CON-041 sigue en la opción A para el
  alcance por proyecto; §11.3 reemplaza el riesgo residual por el diseño de §3.3 (queda el vínculo `stakeholder` de una
  QCS no confidencial); §11.4 suma las reglas de §5; §14 suma las pruebas de §3.3.
- `roadmap.md` «Respuestas del checkpoint»: la protección de quien plantea pasa de «sin restringir» a la decisión 2.
- `api.md`: los contratos de los comandos y del reporte (§3.2 de esta especificación) y de las revisiones (§3.6), y en
  su §3.3 los códigos nuevos:
  `ANONYMOUS_WITH_IDENTITY`, `CONFIDENTIAL_RECEIVER_REQUIRED`, `CONFORMITY_GIVEN`, `INVALID_TARGET`, `ALREADY_REFERRED`,
  `ASSESSMENT_OUT_OF_ORDER`, `FIRST_ASSESSMENT_NOT_RESIDUAL`, `RISK_THRESHOLDS_INVALID`, `MEASURE_OWNER_REQUIRED`,
  `RESIDUAL_ASSESSMENT_REQUIRED`, `HUMAN_DECISION_REQUIRED` y `EVIDENCE_NOT_RESTRICTED`.
- `database-model.md`: `engagement_question.referred_at`; `READ_CONFIDENTIAL` en las tres QCS; las notas de
  `early_warning_rule` (tenant, precedencia).
- `domain-model.md` §8.1.1: el catálogo de acciones; §7.5: los listeners nuevos.
- `gis.md`: LYR-20 hereda el alcance de las QCS confidenciales.
- `glossary.md`: «QCS confidencial» y «día hábil».

## 7. Reparto en planes

| Plan | Rama | Depende de | Tareas principales |
|---|---|---|---|
| 4.0 · Documentos | `docs/fase-4-documentos` desde `main` | Esta especificación | Los cambios de §6 por documento; IDs ASM-095 a ASM-104, UPS-25 y UPS-26; `python3 tools/check_traceability.py` en verde; PR |
| 4.1 · Modelo | `feat/fase-4-1-modelo` desde `main` con 4.0 | 4.0 | `design_docs.py` lee lo nuevo de 4.0; `model.json` con los 12 objetos, los 8 parámetros, las relaciones de F4 en `commitment`, `evidence`, `task` y `stakeholder`, `early_warning.generator_key`, `engagement_question.referred_at`, los ENUM y la acción `READ_CONFIDENTIAL`; los 6 workflows (sin SIM en WF-E3; GVM y SRM en `task.json`); `roles.json` con §7.6 y los campos `person` restringidos; AUT-02, AUT-09 y AUT-11; `test_model.py` y `test_apply_roles.py`; base `F4ApiTest` (QCS, riesgos y alertas que cumplen las guardas de 4.2; revisiones `apiOnly` como la plataforma); `WorkflowMatrixApiTest` difiere `accept`, `close_accepted`, `close_reclassified*` y `assess`; actualizar el tenant de desarrollo de F2 a F4 y comprobar MinIO (coordinador, por el túnel); PR |
| 4.2a · Backend de QCS | `feat/fase-4-2a-qcs` desde `main` con 4.1 | 4.1 | Piezas comunes de §3.1 (`ProjectScope`, `ProjectLinkGuard`, `ExactlyOneGuard`, vínculos de evidencias, parámetros, `CommandTransitionGuard`, `AttentionCounting`); `HolidayCalendar` y `BusinessDays`; `ConfidentialAttentionScope` y las reglas de confidencialidad; `AttentionWriteGuard`; `QcsDueDatesListener` y JOB-02; `QcsTransitionDatesListener`; reclasificación; aceptación y `RequestClosureListener`; derivación, `referred_at` y RN-ENG-005; JOB-01 para QCS (multiobjeto con `Organizations.having`); `qcs_deadline` y el filtro de destinatarios; `approval_pending` de queja grave; reporte unificado; `AttentionKpis` y tablero `attention`; `attention-register`; PR |
| 4.2b · Backend de riesgos y alertas | `feat/fase-4-2b-riesgos` desde `main` con 4.2a | 4.2a (piezas comunes, JOB-01 multiobjeto, avisos) | `RiskLevels`, `RiskWriteGuard` y el endpoint de revisiones; `RiskNoticeListener` y la prueba de AUT-11; JOB-01 para riesgos (situación, cadencia, tarea de revisión, revisión vencida en `DeadlineNotices`); `WarningWriteGuard` y `GeneratorKeyGuard` de alertas; `WarningRulesJob` con los seis evaluadores; `WarningNoticeListener` y la prueba de AUT-09 desde JOB-04; `RiskKpis`, tablero `risks` y KPI-ENG-06 a 08; `risk-register`; suite completa; PR |
| 4.3 · Portal | `feat/fase-4-3-portal` en `sgspe-ui` | 4.2a para QCS; 4.2b para riesgos y alertas | Navegación y permisos; S35, S37 (confidencial y anónima), S36 queja (transiciones, reclasificar, aprobación, cargo firmado, «Crear compromiso desde esta queja»), ficha de consulta y solicitud (aceptar, carta firmada); «Derivar» en S23; S34 con `confidentialHidden`; S38/S39, S40 (revisión, medidas, impactos), S41 (señales y decisión), ficha de incidente, menciones, señales, reglas e impactos; tablero de riesgos; S47 y S48 (tareas, escalamientos y quejas graves); S04 con los avisos nuevos; S49 con las dos exportaciones; S11 con los 8 parámetros; «Registro reservado»; sgspe#14 (huella SHA-256 oculta en S46 y en todo descriptor de una evidencia restringida a quien no tiene `DOWNLOAD_RESTRICTED`; `encodeURIComponent` en las rutas de F2 con una prueba que lo vigile); PR; cierre de la Release 1 |

**¿Se divide 4.2?** Sí. Con la confidencialidad por registro (decisión 2) y los seis evaluadores de JOB-04 (decisión 4),
un solo 4.2 pasaría de las 30 tareas, más que 3.2. La división sigue la frontera natural: 4.2a trae las piezas comunes
y todo lo de QCS; 4.2b parte de 4.2a ya integrada, porque reutiliza JOB-01 multiobjeto, `ProjectLinkGuard` y el filtro
de destinatarios. No se paralelizan: comparten `sgspe_sdd_test` y el coordinador corre las integraciones una tras otra.
El portal puede empezar las pantallas de QCS cuando 4.2a esté en `main`.

## 8. Riesgos y consecuencias aceptadas

**Respuestas del usuario (aplicadas).** El aprobador lee las quejas confidenciales ALTA o CRITICA (§2.3, elección 2);
quien recibió una QCS confidencial la sigue leyendo y quien registra es siempre su receptor (elección 3); el nivel 2
de una QCS confidencial avisa con `qcs_confidential_overdue`, sin datos del registro (elección 10, §3.5).

**Consecuencias aceptadas.**

1. **PUBLICO de `attention-register` depende de quién exporta.** Solo es completo si lo genera quien ve las
   confidenciales; «Notas» lo dice (§5.1).
2. **CONTEO DE QCS cuenta confidenciales.** La alerta solo lleva un número sin desglose, y la ven todos los lectores
   de `early_warning` (§4).

**Riesgos.**

1. **`RecordReadScope` en escrituras y en la respuesta de un alta.** La especificación supone que una QCS que no se lee
   no se puede cambiar ni transicionar y que el alta de una confidencial devuelve el registro a quien la recibió. Lo
   prueba la primera tarea de integración de 4.2a; si la plataforma difiere, `AttentionWriteGuard` suma la regla que
   falte.
2. **Escrituras de la plataforma y listeners.** Si una alerta creada por JOB-04 como la plataforma no llega a
   `WarningNoticeListener` o a AUT-09, JOB-04 publica `warning_new` con `Notices` (misma clave, sin duplicar) y crea la
   tarea de triaje con `JobTasks` (clave `warning_triage:<id>`). Lo decide la prueba de 4.2b.
3. **Derivaciones a QCS confidenciales.** `referred_at` evita derivar dos veces la misma pregunta y deja cerrar la
   interacción, pero quien no ve la QCS derivada no sabe si se respondió: KPI-ENG-05 cuenta de menos para esa persona.
4. **Feriados.** Si nadie agrega el año siguiente, el acuse cuenta lunes a viernes con una advertencia por año: un plazo
   puede vencer un día feriado. Se vigila con la advertencia de arranque.
5. **Tiempo del túnel.** Doce objetos más alargan el aprovisionamiento (hoy de 5 a 8 minutos) y la matriz de permisos
   (de 11 a 58 minutos); conviene correr juntas las clases de cada tarea.
6. **Cierre de la Release 1.** Depende del tenant de desarrollo en F4, de MinIO comprobado y de decidir UPS-01 antes de
   la producción con varios proyectos: la lectura sigue siendo de todo el tenant salvo las QCS confidenciales.
7. **UPS-23 y UPS-24 siguen abiertas.** Sin ellas no hay documentos emitidos sobre QCS y el nombre de una evidencia
   restringida subida por la API sigue visible (ASM-093); la huella se oculta solo en el portal.
