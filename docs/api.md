# API de SGSPE

Sistema de Gestión Social para Proyectos Extractivos · Fase 0 (descubrimiento) · versión 1.0 · 2026-10-07

SGSPE expone dos superficies HTTP bajo `/api`: la **API genérica de wasichai**, que sirve registros, metadata, workflow,
documentos, GIS, auditoría y autenticación para cualquier objeto, y la **API propia `/api/sgspe/**`**, para los comandos y las
consultas que la API genérica no cubre (RNF-INT-004, CON-048). Este documento es el contrato de la API propia y la guía de lo
que SGSPE usa de la genérica. Se escribe en Markdown, como en caja y srtm; OpenAPI queda como opción futura (CON-055). Los IDs
se definen en el [análisis de requisitos](requirements-analysis.md); los objetos, campos y estados son los del
[modelo de dominio](domain-model.md); el contexto técnico está en [Arquitectura de SGSPE](architecture.md) y el encaje con la
plataforma, en [Encaje con wasichai](platform-fit.md).

## 1. Alcance y principios

- **Autenticación.** Todo `/api/**` exige `Authorization: Bearer <token>`, salvo `/api/auth/login`, `/api/auth/token` y
  `/api/health`; `/actuator/health/**` también es público [WAS-DOC api/rest.md] [WAS-DOC security/authentication.md]
  (RQ-SEC-001).
- **Tenant.** La organización sale del token, nunca del cuerpo ni de un parámetro (RQ-SEC-002) [WAS-ADR-0010].
- **Solo lo que la API genérica no puede.** Un endpoint de `/api/sgspe/**` existe para comandos que escriben varios
  registros, cálculos al escribir, archivos con reglas propias, geometrías con reglas y consultas agregadas o
  espaciales. Nunca es un CRUD por objeto (CON-040, RQ-AUD-004).
- **Como el usuario que llama.** El código propio usa `RecordService` en proceso con la identidad de quien llama: aplica los
  mismos permisos de rol, objeto y campo, el mismo tenant y la misma auditoría que la API genérica [WAS-ADR-0038].
- **Objetos `apiOnly`.** Sus escrituras solo pasan por `/api/sgspe/**`; la API genérica responde `403` [WAS-ADR-0040].
- **Un solo cliente.** La API propia la consume `sgspe-ui`, que se publica junto con el backend; no lleva prefijo de versión y
  un cambio incompatible se coordina en los dos repositorios (RNF-MNT-006).
- **Contrato probado.** Cada endpoint tiene pruebas de integración contra PostGIS real, con fixtures ficticios (RNF-TST-001).

## 2. API genérica de wasichai

El contrato completo está en la documentación de wasichai:
[REST API de wasichai](https://github.com/wasichai/wasichai/blob/main/docs/api/rest.md) [WAS-DOC api/rest.md]. SGSPE usa:

| Superficie | Rutas | Uso en SGSPE | Desde |
|---|---|---|---|
| Autenticación | `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/auth/me/permissions`, `GET` y `PUT /api/auth/me/preferences` | Login compartido por el portal y `/admin`; el portal dibuja la navegación según los permisos (RF-IAM-002) | F1 |
| Cuentas de servicio | `POST /api/auth/token`, `/api/service-accounts` | Integraciones futuras de sistema a sistema; hoy ninguna (RQ-SEC-014, GAP-020) | — |
| Organizaciones | `GET /api/organizations/current`, `POST /api/organizations` | Datos del tenant; alta de tenants por el `ADMIN` de la organización operadora (GAP-026) | F1 |
| Metadata | `/api/objects`, `/api/metadata/objects/**`, `/api/relationships`, acciones declaradas | Las usan `apply.py` y `/admin`; el portal lee definiciones para dibujar formularios | F1 |
| Usuarios y roles | `/api/users`, `/api/roles` | `/admin` y `apply_roles.py` | F1 |
| Registros | `GET`, `POST`, `PUT` y `DELETE /api/objects/{object}/records` | Lectura de todos los objetos y escritura de los que no son `apiOnly`; el alta admite `Idempotency-Key` nativa (WAS-ADR-0058, §3.5) | F1 (objetos desde F2) |
| Registros relacionados | `/api/objects/{object}/records/{id}/related/{relationship}` | Vínculos MANY_TO_MANY entre objetos que no son `apiOnly`. Una autorrelación (localidad padre, versión anterior, duplicado) solo se lee desde el lado que apunta: los registros que apuntan a uno se leen filtrando la lista por el campo, por ejemplo `?parent_locality=<id>` (UPS-16) | F2 |
| Workflow | `GET /api/objects/{object}/records/{id}/transitions`, `POST …/transitions/{name}` | Toda transición que no es de comando; la lista explica por qué una transición no está permitida | F2 |
| Documentos emitidos | `GET /api/objects/{object}/records/{id}/documents`, `POST …/documents/{type}`, `GET /api/documents/{id}` | Actas, minutas, cartas y fichas de un registro (RQ-REP-029), emitidos a mano o por una automatización (en F3, la minuta y la lista de asistentes de AUT-05). La copia congelada lleva el registro entero sin permisos de campo, así que un tipo solo se declara sobre un objeto sin campos restringidos (`security.md` §11.4, UPS-23, ASM-090). El JSON no trae el nombre técnico del tipo: el prefijo de `number` (`MR-`, `LA-`) y `snapshot.platform.documentName` (la etiqueta) lo identifican. Emitir a mano pide lo mismo que cambiar el registro (§3.3) | F3 |
| Archivos | `POST /api/objects/{object}/files/{field}` (subir para un alta), `GET /api/objects/{object}/records/{id}/files/{field}` (descargar) | El primer paso del alta de una evidencia (§4.9), con un tope de 20 MB (el tope de Spring por parte: `evidence.file` no declara `maxBytes`, y desde F8 `wasichai.files.max-bytes` es 64 MB, ASM-138): más es `400` `FILE_TOO_LARGE` sobre `file`, y una parte de texto o unos encabezados demasiado grandes, `400` `PART_TOO_LARGE` sobre `multipart`. La descarga de `evidence` la atiende SGSPE con su propia comprobación de la clasificación | F3 |
| Avisos | `GET /api/auth/me/notifications`, `…/summary`, `…/{id}/read`, `…/{id}/dismiss`, `…/{id}/snooze`, `…/read-all`, `…/stream` (SSE); `GET` y `PUT /api/auth/me/notification-preferences` | La bandeja, el contador, el flujo en vivo y las preferencias de correo de wasichai-notifications (§4.10, ADR-0018, ASM-012) | F3 |
| GIS | `GET /api/gis/objects/{object}/features`, `/api/gis/layers/**`, `GET /api/gis/services` | Capas temáticas básicas en el mapa (F2); publicación en GeoServer solo de capas no sensibles, desde `model/publish_layers.py` (F6, RQ-SEC-011) | F2 (features), F6 (`/api/gis/layers/**`) |
| Auditoría | `GET /api/audit`, `GET /api/objects/{object}/records/{id}/history` | `AUDITOR` consulta todo el tenant; las fichas del portal muestran el historial de cada registro (RQ-AUD-005) | F1, F2 |
| Vistas, formularios y páginas | `/api/objects/{object}/views/**`, `/forms/**`, `/pages/**` | Listas y fichas configuradas por metadata, en `/admin` y en el portal | F2 |
| Automatizaciones | `/api/objects/{object}/automations/**`, `GET /api/automation-runs` | Reglas aplicadas desde `model/`; el registro de ejecuciones explica por qué cambió un registro | F3 |
| Asistente | `GET /api/agent/status`, `POST /api/agent/ask` | AI-05, de solo lectura y como el usuario (RF-AIK-005). `sgspe.ai` lo llama en proceso desde `POST /api/sgspe/ai/assistant/questions` (§4.14); por HTTP, `AgentHttpGate` de `sgspe.ai` responde `403` a `POST /api/agent/ask`, para que toda pregunta pase por la activación por organización, la seudonimización y el registro; `GET /api/agent/status` queda abierto y dice `enabled=false` a quien no puede usarlo (`AiAgentAccess`, ASM-130, `ai-architecture.md` §8) | F7 |

Lo que SGSPE agrega o restringe sobre la API genérica, con guardas y filtros propios:

- `X-Correlation-Id` en toda respuesta (§3.8), también en un `401`.
- `If-Match` opcional en las escrituras del portal, comprobado por una guarda y por la propia escritura de wasichai
  (§3.6).
- `X-Change-Reason` obligatorio en los objetos `requiresReason` [WAS-ADR-0041] y, además, en las transiciones de devolver,
  rechazar, suspender, cancelar, retirar y reabrir, aunque el objeto no lo exija (`TransitionReasonGuard`, RQ-AUD-002).
- Un campo derivado enviado por la API genérica es un `400` que lo nombra (`DerivedFieldsGuard`), y una escritura sobre un
  objeto de proyecto sin asignación es un `403` (`ProjectScopeGuard`, RQ-SEC-006) [domain-model §7.1].
- En los objetos que declaran `EDIT_ANY`, quien no tiene esa acción solo cambia, transiciona, vincula o borra los registros
  que creó: hacerlo con uno ajeno es un `403` (`EditAnyGuard`, RQ-SEC-008, ASM-069). Sí puede agregar participantes u
  observaciones a una interacción o una visita ajena que puede leer; al agregar un participante, `AttendanceCountsListener`
  actualiza los conteos de la interacción como escritura derivada con la marca de SGSPE, que la guarda no frena. Ningún rol
  de SGSPE usa `own_records_only`.
- Ningún objeto `apiOnly` se escribe por la API genérica, tampoco en una migración de datos: esas migraciones corren en
  proceso, dentro del paquete `sgspe.*` dueño del objeto, como un trabajo que escribe con `RecordService` como la
  plataforma, con motivo y con las guardas activas; las de objetos `appendOnly` exigen además un ADR explícito
  ([Arquitectura §5.4](architecture.md#54-modelo-como-código-model)) [WAS-ADR-0040].
- Las transiciones de comando (por ejemplo, la aceptación de un candidato) solo llegan desde su endpoint
  (`CommandTransitionGuard`); por la API genérica se rechazan.
- Desde F3, las reglas de los compromisos, los acuerdos, los montos y las tareas también corren en la API genérica, con
  los códigos de §3.3: `CommitmentWriteGuard`, `SupportPolicyGuard`, `AgreementWriteGuard`, `AmountFieldsGuard` y
  `GeneratorKeyGuard` (`generator_key` de una tarea solo lo escribe un trabajo; desde F4 también el de una alerta y,
  desde la ronda branchfix de 4.2b, su `early_warning_rule` y su `detected_at`).

Forma de un registro, la misma en las dos superficies: `id`, `createdAt`, `updatedAt`, `state` (estado de workflow),
`attributes` (los campos con su nombre del modelo) y `geometries` (GeoJSON EPSG:4326, aparte de `attributes`)
[WAS-DOC api/rest.md] [WAS-ADR-0019].

## 3. Convenciones de `/api/sgspe/**`

### 3.1 Recursos y rutas

- Recursos en inglés, en plural y en kebab-case, nombrados por el objeto del modelo: `/api/sgspe/commitment-candidates`,
  `/api/sgspe/social-risks` [USR-09] (RNF-INT-004).
- Un comando es un subrecurso con `POST`: `POST /api/sgspe/commitments/{id}/reschedules`; `{id}` es el UUID del registro.
- Las consultas que cruzan objetos van bajo el prefijo de su capacidad: `territory`, `dashboards`, `attention`, `exports`,
  `ai`.
- Cuando una ruta o un parámetro nombra un objeto o un campo del modelo, usa su nombre técnico tal cual
  (`/zones/locality/{id}`, `?of=commitment`), como la API genérica.

### 3.2 Formato JSON

**Contrato JSON**, el mismo de wasichai en las dos superficies (RNF-INT-004, RNF-INT-005) [WAS-DOC api/rest.md]:

1. Las propiedades de la API van en camelCase: las del sobre de un registro (`id`, `createdAt`, `updatedAt`, `state`), las de
   una página (`totalElements`, `nextCursor`), los parámetros de un comando (`importBatchId`, `draftModelSha256`) y los
   resultados calculados (`computedAt`, `available`).
2. Dentro de `attributes`, cada valor de un campo del modelo va con su nombre del modelo, en snake_case: `due_date`,
   `deadline_status`, `stance`. Las geometrías van aparte, en `geometries`, también con su nombre del modelo.
3. Un comando que crea o cambia registros recibe sus valores en `attributes` y devuelve cada registro afectado con la forma de
   registro de §2, bajo un nombre que dice qué es: `{ "reschedule": {…}, "commitment": {…} }`.
4. Lo que no es un Custom Object (un indicador, el descriptor de un archivo) va todo en camelCase, sin `attributes`.

Además:

- Fechas `DATE` como `AAAA-MM-DD`; instantes `DATETIME` en ISO 8601 con `Z` (UTC); la interfaz los muestra en America/Lima
  (RNF-I18N-002). Montos como números JSON junto a su `currency`. UUID como cadenas.
- Valores ENUM exactamente como se guardan, en español MAYÚSCULAS (ASM-025): `"stance": "EN CONTRA PERO ABIERTO A ESCUCHAR"`.
  Las constantes propias de la API (códigos de advertencia, resultados de una conciliación) van en inglés UPPER_SNAKE, como las
  acciones declaradas: `NEW_VERSION`, `STANCE_DIVERGENCE`.
- Una propiedad desconocida en el cuerpo de un comando es un `400` que la nombra. Es una regla de SGSPE más estricta que la
  de la API genérica, que según su documentación rechaza los nombres de atributo desconocidos [WAS-DOC api/rest.md] pero en
  wasichai 0.3.2 los ignora con un `201` (§3.3, «Comportamiento fijado de wasichai 0.3.2»), y que trata el cuerpo como un
  mapa de secciones e ignora lo que no es una sección [WAS-ADR-0041].
- Un campo que el usuario no puede leer no aparece en la respuesta, igual que en `RecordService`; un indicador que lo
  necesita sale como no disponible (RQ-DSH-032) [WAS-ADR-0020].
- Los `detail` de los errores propios van en español, como en caja y srtm; los de la plataforma, como los escribe wasichai.

### 3.3 Errores (Problem Details)

Los errores usan `application/problem+json` según RFC 9457, que reemplaza a RFC 7807 con el mismo formato: `type`, `title`,
`status`, `detail`, `instance` y `errors[]` con `field` y `message` (RNF-INT-001) [WAS-DOC api/rest.md]. Los produce el manejador
de wasichai: el código propio lanza las excepciones de la plataforma (`ValidationException`, `ConflictException`,
`ForbiddenException`, `NotFoundException`) o subclases propias de `WasichaiException` para `412`, `422`, `428`, `502`
y `503` [WAS-ADR-0040].

| Estado | Cuándo en `/api/sgspe/**` |
|---|---|
| `200` | Lectura; comando que no crea registros; reenvío con la misma `Idempotency-Key` |
| `201` | Comando que crea registros, con `Location` del registro principal |
| `202` | Pedido aceptado para trabajo en segundo plano (borradores de entregable) |
| `204` | Sin cuerpo: vincular o desvincular |
| `400` | Cuerpo inválido: valor, campo desconocido, motivo faltante o una guarda que rechaza los datos enviados; desde F6, un parámetro de una consulta espacial fuera de rango o ilegible (`OUT_OF_RANGE`, `INVALID_VALUE`, `INVALID_OF`, `PERIOD_NOT_APPLICABLE`), una cifra que no puede salir parcial (`TOO_MANY_RECORDS`), el tablero `territory` sin `project` (`PROJECT_REQUIRED`), un archivo de varias capas sin `layer` (`LAYER_REQUIRED`) y una exportación geográfica mal pedida (`OBJECT_NOT_EXPORTABLE`, `INVALID_FORMAT`, `INVALID_COORDINATES`, `INVALID_GEOMETRY`); desde F8, la emisión EXTERNO sin la lista de verificación (`CHECKLIST_REQUIRED`, como las exportaciones), el tablero `project` sin un solo `project` (`PROJECT_REQUIRED`), más proyectos que `sgspe.dashboards.executive.max-projects` en el tablero `executive` (`TOO_MANY_RECORDS`) y el cierre de un periodo que no terminó (`PERIOD_NOT_ENDED`) |
| `401` | Sin token o con un token inválido o vencido; sin token, el cuerpo va vacío (§3.3, «Comportamiento fijado de wasichai 0.3.2») |
| `403` | Falta un permiso o una acción declarada; escritura fuera de las asignaciones; edición de un registro ajeno sin `EDIT_ANY`; escritura genérica sobre un objeto `apiOnly`; desde F6, un objeto o un filtro fuera del nivel de divulgación de una exportación geográfica (`DISCLOSURE_NOT_ALLOWED`) y una capa WMS fuera de la lista blanca o sin READ (sin código); desde F8, una subida o un reemplazo de un campo `FILE` de entregables por la API genérica (`FILE_WRITE_NOT_ALLOWED`) y el cierre de todo el tenant sin ser `ADMIN` (`ACTION_REQUIRED`) |
| `404` | El registro no existe o es de otra organización; ruta de un módulo no instalado; desde F6, la zona, el proyecto, el área, la localidad o la comunidad de una consulta espacial que no existe o que quien llama no lee (`ZONE_NOT_FOUND`, `PROJECT_NOT_FOUND`, `AREA_NOT_FOUND`, `LOCALITY_NOT_FOUND`, `COMMUNITY_NOT_FOUND`); desde F8, un archivo de entregable que nunca se sirve (una candidata, un modelo congelado, el DOCX fuera de INTERNO), sin código |
| `409` | Estado incompatible (transición desde otro estado, objeto `appendOnly`, regla que depende de otros registros, borrado de un registro con dependientes: `HAS_DEPENDENTS`), valor único repetido o una petición con la misma `Idempotency-Key` todavía en curso (`IDEMPOTENCY_IN_FLIGHT`, §3.5); desde F6, una consulta que mide en metros sobre un proyecto sin CRS de trabajo (`WORKING_CRS_REQUIRED`); desde F8, un borrador pedido o una emisión fuera de su estado (`STATE_MISMATCH`), secciones cambiadas después de congelar el modelo (`SECTIONS_CHANGED`), otro modelo que el revisado (`MODEL_MISMATCH`), un mapa fallido (`MAP_FAILED`), un indicador que no se cierra (`INDICATOR_NOT_CLOSABLE`) y un periodo ya cerrado (`MEASUREMENT_EXISTS`) |
| `412` | `If-Match` no coincide con el `updatedAt` actual (§3.6) |
| `422` | Parámetro de consulta desconocido (ASM-054); la misma `Idempotency-Key` con otra petición (`IDEMPOTENCY_KEY_REUSED`, §3.5); desde F6, un formato de GDAL en un servidor sin GDAL o sin su driver (`FORMAT_UNAVAILABLE`, ASM-127) y una petición o un parámetro WMS fuera de la lista blanca (`WMS_REQUEST_NOT_ALLOWED`, `WMS_PARAMETER_NOT_ALLOWED`, ASM-121); desde F7, una fuente de IA cuyo texto pasa el tamaño de `sgspe.ai.limits` (`AI_INPUT_TOO_LARGE`, ASM-131) o que no tiene texto que extraer, como un PDF escaneado (`AI_NO_TEXT`, ASM-132); desde F8, un borrador sin los parámetros de su tipo (`DELIVERABLE_PARAMETERS_MISSING`), una plantilla que no pasa la validación (`TEMPLATE_INVALID`, ASM-135), una corrida de AI-04 sobre una sección que el tipo no marca con IA (`SECTION_NOT_ASSISTED`), una sugerencia con un `[PERSONA]` sin id (`UNRESOLVED_PSEUDONYM`) y una sección asistida que se quiere volver a HUMANO (`AI_ORIGIN_LOCKED`) (ASM-137) |
| `428` | Falta `If-Match` en un comando que lo exige (§3.6) |
| `500` | Error interno, sin detalles en la respuesta (la causa queda en el registro del servidor); desde F6, una exportación geográfica que GDAL no pudo escribir (`EXPORT_FAILED`) |
| `502` | Desde F7, una corrida de IA que se queda sin margen de tokens o de acciones y no guarda sugerencias ni candidatos (`AI_RUN_TRUNCATED`, ASM-131) |
| `503` | En los endpoints de IA que llaman a un modelo (§4.14): la IA no está activada para la organización o no hay proveedor (`AI_UNAVAILABLE`, desde F7, ASM-128), como el asistente de wasichai sin clave [WAS-DOC api/rest.md], y, desde el plan 7.2b, ya corren tantas corridas como permite `sgspe.ai.limits.max-concurrent-runs` (`AI_BUSY`, con `Retry-After`); y en una importación cuando ya corren tantas como permite `sgspe.territory.import.max-concurrent` (`IMPORT_BUSY`, §4.2); desde F6, GeoServer caído o vencido en el proxy WMS (`GEOSERVER_UNAVAILABLE`) y una exportación geográfica que pasa del plazo de GDAL (`EXPORT_TIMEOUT`) |

- Un `detail` nunca lleva datos personales ni el valor que repitió un único; los logs tampoco (RQ-SEC-010).
- La respuesta de error también lleva `X-Correlation-Id`, que el usuario puede citar al pedir ayuda (RQ-AUD-006).

```json
{
  "type": "https://wasichai.dev/problems/422",
  "title": "Unprocessable Content",
  "status": 422,
  "detail": "Parámetro de consulta desconocido",
  "instance": "/api/sgspe/dashboards/commitments",
  "errors": [
    { "field": "projectId", "message": "no es un parámetro de esta consulta; se admiten project, periodStart, periodEnd, administrativeUnit, locality, community, areaOfInfluence, explorationCampaign y scope" }
  ]
}
```

**Errores de las guardas de SGSPE en la API genérica.** Toda guarda de SGSPE que rechaza una escritura por
`/api/objects/**` lo hace con su código en `errors[0].message` y el campo en `errors[0].field` (`record` si el hallazgo
no apunta a un campo); el `detail` lleva el texto en español. Así dos `403` o dos `409` se distinguen por código y no
solo por estado. El `detail` y `errors[0].message` de un error de wasichai (permiso, validación de tipo, obligatorio
vacío) no siguen esta forma: su `message` es texto libre. Los códigos que existen hoy, con el estado de la escritura
real:

| Estado | Códigos |
|---|---|
| `400` | `DERIVED_FIELD` (se envió un campo derivado), `REASON_REQUIRED` (la transición exige `X-Change-Reason`), `FIELD_REASON_REQUIRED` (cambiar `stage` o `exploration_phase` de un proyecto exige motivo), `EXACTLY_ONE` (debe venir exactamente uno de un grupo de campos), `USER_REFERENCE` (el par `<rol>_user_id` y `<rol>_user_email` no nombra a un usuario de la organización), `UNKNOWN_ROLE` (`role_name` de una asignación no es un rol declarado), `REPRESENTED_PEOPLE` (`represented_people_count` menor que 1), `POSITION_WITHOUT_PERSON` (`position_status`: un cargo sin persona solo puede estar `VACANTE` o `PREVISTO`), `INVALID_NUMBER` (un monto que no es un número) y `OUT_OF_RANGE` (un monto negativo, con más de 15 dígitos enteros o con más de 4 decimales tras quitar los ceros finales), sobre el campo: `exploration_campaign.social_support_cap`, `commitment.amount`, `delivery.delivered_amount` y `agreement.amount`, en el alta y en todo cambio de su valor (`AmountFieldsGuard`, ASM-092), `JUSTIFICATION_REQUIRED` (`origin_justification`: un compromiso que nace de un taller o de una reunión informativa, RN-ENG-001) y `DEPENDENCY_SELF` (`depends_on`: un compromiso no depende de sí mismo). Desde F4: `ANONYMOUS_WITH_IDENTITY` (`person` o `stakeholder` de una QCS anónima), `CONFIDENTIAL_RECEIVER_REQUIRED` (`received_by_user_id`: el alta de una QCS confidencial, sin `READ_CONFIDENTIAL`, lleva como receptor a quien escribe; el alta que hace la reclasificación de §4.6 conserva el receptor del original), `INVALID_LINK` (`duplicate_of`: una queja no es duplicado de sí misma), `FIELD_REQUIRED` (`category` y `severity` para atender una queja; `resolution` y `response` para resolverla; `responded_at` y `response` en las transiciones que comunican una respuesta; probabilidad, impacto y `owner_user_id` al evaluar un riesgo; `treatment_decision` MITIGAR al decidir mitigar y MONITOREAR o ACEPTAR al decidir monitorear; `close_reason` al cerrarlo; `immediate_response` y `consequences` al cerrar un incidente) y `OUT_OF_RANGE` también en los plazos y la cadencia del proyecto (de 1 a 365), sus umbrales de riesgo (de 1 a 25 y crecientes), `threshold` y `window_days` de una regla de alerta (ASM-097; una regla sin `threshold` también responde `OUT_OF_RANGE`, no `FIELD_REQUIRED`) y la cadencia propia de un riesgo (`review_every_days`: entera de 1 a 365, en el alta y en todo cambio de su valor; vacía vale `risk_review_days` del proyecto). Desde F5: `INVALID_NUMBER` y `OUT_OF_RANGE` también en `contribution_item.line_amount` y `unit_cost`, `receipt.amount` y `program_budget.allocated_amount` y `executed_amount` (ASM-118); `OUT_OF_RANGE` en `beneficiaries_count`, `target_count` y `reached_count` (enteros no negativos), en `budget_year` (de 2000 a 2100) y en la fecha final de una medición, un programa o una iniciativa anterior a la inicial; `FIELD_REQUIRED` (`contribution_type`: aprobar un aporte sin tipo, P9); y `FIELD_NOT_ALLOWED` (`delivered_amount` en la entrega de un aporte, ASM-111). Desde F8: `FIELD_NOT_EDITABLE` (`deliverable_type` de un `deliverable`, `section_key` y `deliverable` de una `deliverable_section` y `code` de un `indicator`, que no cambian después del alta; `DeliverableWriteGuard`, `IndicatorWriteGuard`); `DERIVED_FIELD` también en `deliverable.current_version`, que solo escribe la emisión (un `PUT` que la omite o la cambia responde `400`; reenviarla tal como se leyó no es cambiarla); `SHA256_MISMATCH` (`docx_sha256`, `pdf_sha256` o `model_sha256` de una `deliverable_version` distinto del SHA-256 del descriptor de su archivo); `FIELD_REQUIRED` (`docx` o `docx_sha256` de una versión INTERNO); y `FILE_TOO_LARGE` (`file`: una copia de la emisión pasa de `sgspe.deliverable.max-file-bytes`) |
| `403` | `COMMAND_ONLY` (la transición es de comando), `NOT_ASSIGNED` (sin asignación vigente al proyecto, para quien lo lee: desde el plan 4.4, quien no lo lee recibe el `404` o el `400` de wasichai, ASM-108), `ACTION_REQUIRED` (falta la acción declarada: `APPROVE` de un proyecto o de un compromiso y `VERIFY` de un compromiso, sobre `record`; `DOWNLOAD_RESTRICTED` para bajar la clasificación de una evidencia restringida, sobre `classification`), `NOT_OWN_RECORD` (el objeto declara `EDIT_ANY`, quien escribe no lo tiene y no creó el registro: no lo cambia, mueve, vincula ni borra; `record`), `SEGREGATION_OF_DUTIES` (quien aprueba un compromiso lo registró, `record`; quien lo verifica es su responsable, `owner_user_id`; RN-COM-005), `FIELD_NOT_READABLE` (`delivery.conformity_by`: registrar el cumplimiento necesita leer quiénes dieron la conformidad), `GENERATOR_KEY_PLATFORM_ONLY` (`generator_key`: una persona fija, cambia o vacía la clave de una tarea, también con un `PUT` que la omite; reenviarla tal como se leyó no es cambiarla; desde F4, la excepción es el código de SGSPE con la marca, que escribe como quien llama solo las claves de las tareas genéricas de una QCS confidencial, `escalation:<objeto>:<huella>:<fecha>:<1 o 2>` y `ack:grievance:<huella>`, D-1; nunca las vacía), `OBJECT_NOT_READABLE` (quien escribe no lee un objeto que la regla necesita: `project_party`, `project`, `engagement_participant`, `engagement_question` o `area_of_influence`; desde F3 también `commitment`, `engagement`, `exploration_campaign`, `delivery`, `evidence`, `commitment_dependency` o `stakeholder`, y `agreement_land_parcel` al activar un acuerdo; desde F4 también `grievance`, `inquiry`, `stakeholder_request`, `social_risk`, `incident`, `media_mention`, `early_warning`, `early_warning_rule` y `key_theme`, que leen la regla «mismo proyecto» (`ProjectLinkGuard`, `commitment.origin_grievance` y `commitment.social_risk`) y los vínculos de una evidencia con una QCS (`EvidenceWriteGuard`); `field` es el objeto). Desde F4: `ACTION_REQUIRED` también sin `READ_CONFIDENTIAL` al quitar `confidential` o cambiar `owner_user_id` o `received_by_user_id` de una QCS confidencial (sobre el campo); `CONFIDENTIAL_MARK_FORBIDDEN` (`confidential`: marcar confidencial una QCS existente sin ser su receptor ni su responsable guardados y sin `READ_CONFIDENTIAL`, también si quien escribe se pone de responsable en la misma escritura, D-3; el alta ya confidencial sigue con `CONFIDENTIAL_RECEIVER_REQUIRED` y quitar la marca, con `ACTION_REQUIRED`); sin `APPROVE` en `resolve_high_severity` y sin `CONFIRM_ALERT` en `confirm` y `dismiss` (`record`); `HUMAN_DECISION_REQUIRED` (`record`: confirmar o descartar una alerta sin usuario o con una cuenta de servicio, RN-EWS-001); `COMMAND_ONLY` también en `close_reclassified` y `close_reclassified_*`, `accept` y `close_accepted` de WF-E3 y `assess` de WF-F, y en los campos `reclassified_from_*` de las tres QCS y `created_commitment` de `stakeholder_request`, que solo escriben sus comandos (la reclasificación y la aceptación): una persona que los fija, cambia o vacía, también con un `PUT` que los omite, recibe el 403 sobre el campo; reenviarlos tal como se leyeron no es cambiarlos, y la plataforma sí los escribe; `GENERATOR_KEY_PLATFORM_ONLY` también en `early_warning.generator_key`; `PLATFORM_ONLY` (sobre el campo: `early_warning.early_warning_rule` y `early_warning.detected_at` solo los escribe la plataforma; una persona que los fija, cambia o vacía, también con un `PUT` que los omite, recibe el 403, y reenviarlos tal como se leyeron no es cambiarlos; una alerta manual se da de alta sin ellos y SGSPE le pone la hora del alta en la misma transacción, después de que wasichai arma la respuesta: el `201` del alta no trae `detected_at` y su `ETag` ya quedó superado, así que el portal relee la alerta antes de mostrarla o de editarla, o su `If-Match` recibe `412`; `GeneratorKeyGuard`, ronda branchfix de 4.2b). Desde F5: `COMMAND_ONLY` también en `stakeholder_request.created_contribution`, que solo escribe la aceptación (ASM-117); `ACTION_REQUIRED` y `SEGREGATION_OF_DUTIES` (`record`) al aprobar un aporte (P4, P5) o un programa (G1, G2) sin `APPROVE` o habiéndolo registrado (en el aporte, también habiendo aceptado la solicitud que lo creó); `FIELD_NOT_READABLE` (`delivery.conformity_by`) también en `register_delivery` de WF-P (P8); `OBJECT_NOT_READABLE` también sobre `social_contribution`, `contribution_item`, `agreement`, `agreement_party`, `topic` y `project_priority_topic`, que leen las reglas de WF-P y WF-G. Desde F8: `OBJECT_NOT_READABLE` también sobre `deliverable_version` en `submit_for_review`, `resubmit`, `approve` y la emisión, que cuentan las versiones emitidas del entregable como quien transiciona (#83) |
| `409` | `HAS_DEPENDENTS` (el registro tiene dependientes obligatorios, o uno opcional de un objeto `requiresReason`, y no se borra; `field` es `<objeto>.<campo>` del primer dependiente), `FROZEN_MEMBERS` (los miembros de una versión `approved` o `superseded` del área de influencia no cambian; `field`: `area_of_influence`), `FROZEN_AREA` (su proyecto, tipo y número de versión tampoco; `field` es el campo que cambia), `WORKING_CRS_REQUIRED`, `PARTY_REQUIRED`, `FIELD_REQUIRED` (`scheduled_at`, `held_at`, `minutes` o `cancel_reason` de una interacción), `ATTENDANCE_REQUIRED`, `PENDING_QUESTIONS`, `MINUTES_REQUIRED`, `PROFILE_INCOMPLETE` (`caracterizar` exige `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` y `owner_user_id`; `field` es el primero que falta), `DUPLICATE_RULE` (`stakeholder_type`: ya hay una fila de `prioritization_rule` para cualquier tipo de actor con ese proyecto, grupo de influencia, postura y ámbito). Desde F3: `WORKSHOP_COMMITMENT_BLOCKED` (`origin_engagement`: el proyecto no admite compromisos de talleres ni reuniones informativas), `DUE_DATE_RESCHEDULE_ONLY` (`due_date`: desde `approved`, también en `cancelled`, la fecha solo cambia con una reprogramación), `DEPENDENCY_CYCLE` y `DEPENDENCY_GRAPH_TOO_LARGE` (`depends_on`: la dependencia cerraría un ciclo, o encadena más de 500 compromisos y no se puede verificar), `PROJECT_MISMATCH` (el campo de un compromiso o de un candidato que apunta a un registro de otro proyecto), `DELIVERY_FROZEN` (`commitment`: la entrega de un compromiso cumplido, verificado, cerrado o cancelado no cambia ni se vincula), `FIELD_REQUIRED` también con los campos de la matriz de compromisos y el responsable (el primero que falta), `resource_type` al registrar el cumplimiento, `signed_date`, `effective_date` y `community` de un acuerdo, y `duplicate_of`; `STAKEHOLDER_REQUIRED` (`commitment_stakeholder`), `SENIOR_APPROVAL_REQUIRED` (`origin_engagement` si nace de una mesa de diálogo, `commitment_stakeholder` si lo pide un grupo de interés POLITICO), `INTERNAL_APPROVAL_REQUIRED` (`project`), `SUPPORT_CAP_EXCEEDED` y `CAP_DATA_INVALID` (`exploration_campaign`: la aprobación supera el tope de la campaña, o un monto guardado de la campaña no es válido y el tope no se puede sumar), `DELIVERY_REQUIRED` (`delivery`), `EVIDENCE_REQUIRED` (`evidence_delivery`), `BLOCKING_DEPENDENCY` (`commitment_dependency`), `LAND_PARCEL_REQUIRED` (`agreement_land_parcel`), `ASSEMBLY_REQUIRED` (`assembly_engagement`) y, por los comandos de evidencias, `EVIDENCE_IMMUTABLE` (`file`, `replaces` o `import_batch_id`), `EVIDENCE_NOT_DELETABLE` (`record`) y `EVIDENCE_FILE_IN_USE` (`file`: el archivo ya es de otra evidencia). Desde F4: `PROJECT_MISMATCH` en toda relación de un objeto de F4 hacia un registro de otro proyecto (`origin_engagement`, `origin_question`, `stakeholder`, `social_risk`, `incident`, `duplicate_of`, `reclassified_from_*`, `created_commitment`, `area_of_influence`, `key_theme`, `origin_incident`, `commitment`, `media_mention`, `early_warning` y `early_warning_rule`; una regla del tenant no discrepa), en `commitment.origin_grievance` y `commitment.social_risk` y en los vínculos `social_risk_stakeholder` e `incident_stakeholder` (`ProjectLinkGuard`); `SENIOR_APPROVAL_REQUIRED` (`record`: `resolve` de una queja ALTA o CRITICA con `policy_high_severity_requires_approver` activo); `CONFORMITY_GIVEN` (`complainant_conformity_given`: no se reabre una queja con conformidad); `STATE_MISMATCH` (`created_commitment`: `close_unfulfilled` con el compromiso fuera de `cancelled`); `MEASURE_OWNER_REQUIRED` (`mitigation_measure`: mitigar sin una medida con responsable); `RESIDUAL_ASSESSMENT_REQUIRED` (`risk_assessment`: monitorear sin una revisión residual); `HAS_DEPENDENTS` también en una regla de alerta con alertas; y, por los vínculos de evidencias, `EVIDENCE_NOT_RESTRICTED` (`classification`: una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial, y una ya vinculada a una QCS confidencial, con los vínculos leídos como quien escribe, no baja a una clasificación no restringida por `PATCH /api/sgspe/evidence/{id}`). Desde F5 (`InvestmentWriteGuard`, `workflows.md` WF-P y WF-G): `ITEMS_REQUIRED` (`contribution_item`: aprobar un aporte sin ítems, P9); `ITEMS_FROZEN` (`social_contribution`: crear, cambiar o borrar un ítem de un aporte fuera de `requested`, o cambiar el aporte de un ítem); `CONTRIBUTION_FROZEN` (el campo: un término aprobado del aporte que cambia fuera de `requested`); `PAID_LABOR` y `MONETARY_DONATION` (`contribution_item`) y `PRIORITY_NOT_MET` (`topic`), lineamientos incumplidos sin `guideline_deviation_justification` (con ella son advertencias, y `PRIORITIES_NOT_CONFIGURED` lo es siempre, §4.1); `SYNERGY_AGREEMENT_REQUIRED` (`agreement`: P6 del aporte y G3 del programa); `DELIVERY_EXISTS` (`social_contribution`: una segunda entrega del aporte); `DELIVERY_FROZEN` también sobre `social_contribution` (la entrega de un aporte fuera de `procured` no cambia ni se vincula, y su comprobante `receipt` no se crea, cambia ni borra con el aporte en `delivered` o después); `PROGRAM_MISMATCH` (`social_initiative`: un grupo de beneficiarios con una iniciativa de otro programa); `SUPPORT_CAP_EXCEEDED`, `CAP_DATA_INVALID`, `INTERNAL_APPROVAL_REQUIRED`, `DELIVERY_REQUIRED` y `EVIDENCE_REQUIRED` también en WF-P (P1, P7 y P8) e `INTERNAL_APPROVAL_REQUIRED` en `plan_direct` de WF-G (G4); `STATE_MISMATCH` también sobre `created_contribution` (`close_unfulfilled` con el aporte fuera de `rejected` y `cancelled`); un indicador de programa con mediciones no da `HAS_DEPENDENTS`: como `program_measurement` es `appendOnly`, wasichai rechaza antes su baja con un `409` sin código (`AppendOnlyReferences`); y `PROJECT_MISMATCH` en las relaciones de BC-10 hacia otro proyecto (`exploration_campaign`, `stakeholder`, `commitment` y `agreement` del aporte, `agreement` del programa) y en los orígenes de una tarea (`ProjectLinkGuard`); desde F8, de `DeliverableWriteGuard` en `submit_for_review`, `resubmit` y `approve` de WF-H, `SECTIONS_CHANGED` (un error por sección, `deliverable_section.<section_key>`: cambió después de congelar el modelo) y `STATE_MISMATCH` (`draft`: el borrador no está LISTO o ya se emitió, porque hay una versión con `issued_at` posterior a `draft_generated_at`) |
| `422` | Desde F8: `TYPE_NOT_AVAILABLE` (`deliverable_type`: pedir un borrador de un tipo que esta versión todavía no genera; DIAGNOSTICO SOCIAL hasta el plan 8.2c), `TEMPLATE_UNAVAILABLE` (`template_key`: pedir un borrador con una clave sin plantilla) y `AI_ORIGIN_LOCKED` (`content_origin`: una `deliverable_section` ASISTIDO POR IA no vuelve a otro origen ni se borra, en toda la vida del entregable; una sección asistida tampoco se borra; `DeliverableWriteGuard`, ASM-137, decisión del usuario del 2026-10-15) y `MENTION_NOT_READABLE` (`content`: una mención nueva de una `deliverable_section` cita un registro que quien escribe no lee, o a una persona cuyos nombres no lee; no existir da el mismo error; #81) |

Desde el plan 4.4, el alcance de lectura por proyecto (`security.md` §9.7, ASM-108) cambia lo que ve quien no está
asignado a un proyecto, y no tiene `READ_ALL_PROJECTS`:

| Caso | Respuesta |
|---|---|
| Leer, cambiar, borrar o transicionar un registro de otro proyecto, o su historial o sus documentos | `404`, como un registro que no existe |
| Alta o cambio con un campo `RELATION` hacia un registro de otro proyecto (también `project`) | `400` de wasichai: `errors[{field: "<campo>", message: "no record with this id"}]` |
| `POST` o `DELETE .../related` con un extremo de otro proyecto | `404` |
| Vincular dos registros de proyectos distintos que quien llama lee | `409 PROJECT_MISMATCH`, como hasta F4 |
| Un entregable con `area_of_influence`, `study` o `current_version` de otro proyecto (desde F8), también para quien lee todos | `409 PROJECT_MISMATCH` en `area_of_influence`, `study` y `current_version` de un entregable (`ProjectLinkGuard`) |
| Escribir sin asignación quien lee todos los proyectos (`PROJECT_ADMIN`) | `403 NOT_ASSIGNED` |
| Tablero, indicador o reporte con `project` de otro proyecto | `200` con conjuntos vacíos |
| Borrar un registro con dependientes en otro proyecto | `409 HAS_DEPENDENTS`; el `detail` no nombra el dependiente, el `field` sigue siendo `<objeto>.<campo>` |

Los comandos de F4 suman sus propios códigos, con la misma forma: `INVALID_TARGET`, `ALREADY_REFERRED`,
`STATE_MISMATCH`, `ASSESSMENT_OUT_OF_ORDER`, `FIRST_ASSESSMENT_NOT_RESIDUAL` y `RISK_THRESHOLDS_INVALID`; §4.4, §4.6 y
§4.7 dicen cuándo. El endpoint de entrega de aportes de F5 usa `STATE_MISMATCH`, `DELIVERY_EXISTS` y
`FIELD_NOT_ALLOWED` (§4.8).

La emisión manual de un documento (`POST /api/objects/{object}/records/{id}/documents/{type}`, §2) no es una escritura
de `RecordService`, pero archiva el documento vigente del tipo: `IssueDocumentFilter` (`sgspe.scope`) le pide lo mismo
que a cambiar el registro, UPDATE sobre el objeto, una asignación vigente al proyecto del registro y, en los objetos que
declaran `EDIT_ANY`, esa acción o haber creado el registro (`security.md` §11.4), con la misma forma de error: `403`
`ACTION_REQUIRED` (`record`: falta UPDATE), `OBJECT_NOT_READABLE` (`record`: quien emite no lee el registro),
`NOT_ASSIGNED` (el campo de alcance: sin asignación vigente, o el proyecto no se resuelve) y `NOT_OWN_RECORD` (`record`:
sin `EDIT_ANY`, el registro es de otra persona). Un registro que no existe o que quien emite no lee (otro proyecto, una QCS
confidencial ajena) responde el `404` de wasichai; `OBJECT_NOT_READABLE` queda para quien no tiene READ sobre el objeto.
Las dos lecturas, `GET /api/objects/{object}/records/{id}/documents` y `GET /api/documents/{id}`, responden `404` a quien
no lee el registro del documento (UPS-28).

`HAS_DEPENDENTS` rige para todos, `ADMIN` incluido, porque la base rechazaría el borrado igual: wasichai declara cada
relación obligatoria NOT NULL con ON DELETE SET NULL y, sin esta guarda, borrar el padre respondería 500 (UPS-20).
Borrar primero a los dependientes lo resuelve. Desde F3 cuenta también el vínculo opcional de un objeto `requiresReason`
(`commitment` y `agreement`: por ejemplo `commitment.agreement_clause`, `commitment.origin_engagement` o
`agreement.assembly_engagement`): ahí la base no falla, pero el borrado cambiaría ese registro sin motivo ni auditoría.
Para borrar una cláusula o una interacción que un compromiso cita, primero se vacía el vínculo en el compromiso, con un
cambio con motivo. Lo que apunta un objeto `appendOnly` no se borra nunca, porque ese registro no se puede cambiar: un
cargo que cita una evaluación (`stakeholder_assessment.position`) o un proyecto que cita `export_log`. Ahí responde
antes wasichai con su propio `409`, sin código ni `errors[]`: solo el `detail`, que nombra el objeto y el campo que lo
apuntan. Un cargo así se da por terminado con `end_date` y su `position_status`, no se borra. `STATE_MISMATCH` es un
código de `transition-checks` (§4.1), del comando de evaluaciones (§4.3) y de los comandos de F3 (§4.5): la transición
real por la API genérica la rechaza wasichai. Esta tabla crece con cada guarda nueva.

Los comandos de §4 usan la misma forma de error y agregan sus propios códigos. `POST /api/sgspe/stakeholders/{id}/assessments`
(§4.3) responde `403` `ROLE_REQUIRED` (quien evalúa no tiene el rol de la transición de WF-B, que también se exige al
reevaluar), `403` `FIELD_NOT_WRITABLE` (quien evalúa no puede escribir la postura) y `409` `STATE_MISMATCH` (el grupo de interés
retirado no se evalúa).
`POST /api/sgspe/areas-of-influence/{id}/approve` (§4.2) responde `428` sin `If-Match`, `412` si el `updatedAt` cambió, `400`
sobre `reason` sin motivo, `403` (sin código) si falta UPDATE sobre `area_of_influence`, `403` `ACTION_REQUIRED` (falta la
acción declarada `APPROVE`), `403` `ROLE_REQUIRED` (falta el rol de `approve` o de `supersede`), `403` `NOT_ASSIGNED` (sin
asignación vigente al proyecto), `409` `STATE_MISMATCH` (`field`: `state`; la versión no está en `draft`) y `409`
`MEMBERS_REQUIRED` (`field`: `area_of_influence_member`; una versión sin miembros no se aprueba). Un parámetro de consulta
desconocido es `422` y una propiedad desconocida del cuerpo, `400`.
`POST /api/sgspe/territory/imports` y su confirmación (§4.2) responden `400` `FILE_TOO_LARGE` (el archivo pasa de
`sgspe.territory.import.max-bytes`) e `IMPORT_TIMEOUT` (la lectura y la validación pasan de `timeout`), los dos con `field`
`file` y sin escribir nada; `400` `FORMAT_NOT_SUPPORTED` (`file`: un predio, una concesión, una comunidad o un rasgo
ambiental desde una hoja), `400` `SHA256_MISMATCH` (`sha256`: el archivo no es el de la vista previa), `409`
`IMPORT_CONFLICT` (`file`: lo registrado con un código del archivo cambió entre el análisis de la confirmación y su
candado, por ejemplo otra confirmación del mismo archivo; no se escribe nada y se vuelve a previsualizar), desde F3 `409`
`IMPORT_BATCH_USED` (`importBatchId`: el lote ya tiene su archivo original como `evidence` y esta confirmación escribiría
registros con él; no se escribe nada y se vuelve a previsualizar, que propone un lote nuevo) y `503`
`IMPORT_BUSY` (`file`: ya corren `max-concurrent` importaciones y no se liberó un lugar dentro del plazo). Los límites
del multipart de Spring (§3.10) son `400` con `field` `multipart`, que designa la petición entera y no es el nombre de
ninguna parte: `PART_TOO_LARGE` (un campo de texto pasa de 1 MB o los encabezados de una parte pasan de 10 KB) e
`INVALID_MULTIPART` (más partes que `max-parts` o un cuerpo que no se puede leer); solo el tope de disco de la parte de
archivo es `FILE_TOO_LARGE`, sobre `file`. Las demás respuestas `400` (una parte que falta, se repite o no existe, un
CRS fuera del catálogo, una decisión mal formada o sobre una fila que no es válida) son de validación, con texto libre,
y un parámetro de consulta es `422`. Una fila que PostGIS no puede transformar ni validar no es un error de la petición:
va a `invalid[]` con el motivo `geometría inválida: …` en español. Los tableros y sus indicadores (§4.11) responden
`404` `DASHBOARD_NOT_FOUND` e `INDICATOR_NOT_FOUND`, `400` `INVALID_BREAKDOWN` y `400` `TOO_MANY_RECORDS`; el detalle
está en §4.11. Las exportaciones (§4.13) responden `400` `INVALID_DISCLOSURE_LEVEL` (`disclosureLevel`),
`CHECKLIST_REQUIRED` (`checklistConfirmed`: EXTERNO sin la lista de verificación), `INVALID_FILTER` (`filters.<filtro>`)
y `TOO_MANY_RECORDS` (`filters.project`, no `project` como los tableros); `403` `NOT_ASSIGNED` (`filters.project`: un
solo proyecto sin asignación vigente), `404` `EXPORT_NOT_FOUND` (`kind`) y `409` `CATALOGUE_UNAVAILABLE` (el campo cuyo
catálogo está vacío, en `PUBLICO`). El detalle está en §4.13.
Los endpoints de F6 (§4.2 y §4.11) usan la misma forma. Las consultas espaciales responden `400` `OUT_OF_RANGE` (el
parámetro: `bufferM`, `distanceKm`, `limit`, `periodStart`, `periodEnd`, `lon`, `lat`, `page` o `size`; ASM-126; en
`data-quality`, un `page` o un `size` que no es un número también es `OUT_OF_RANGE`, no `INVALID_VALUE`),
`INVALID_VALUE` (el parámetro que no se puede leer: un número, una fecha o un UUID mal escritos, o `lon` o `lat` que
faltan), `INVALID_OF` (`of`), `PERIOD_NOT_APPLICABLE` (`periodStart`: `stakeholder` y `social_risk` no tienen fecha) y
`TOO_MANY_RECORDS` (una cifra que no puede salir parcial, o más de `max-related` registros relacionados, sobre el
parámetro que hay que acotar); `404` `ZONE_NOT_FOUND`, `PROJECT_NOT_FOUND`, `AREA_NOT_FOUND`, `LOCALITY_NOT_FOUND` y
`COMMUNITY_NOT_FOUND` (`id`: no existe o quien llama no lo lee); y `409` `WORKING_CRS_REQUIRED` (`project`: la consulta
mide en metros y el proyecto no tiene CRS de trabajo). El tablero `territory` responde `400` `PROJECT_REQUIRED`
(`project`), y KPI-TER-01 sin `locality` ni `community` sale no disponible con la razón `TERRITORY_REQUIRED`, que no es
un estado HTTP (§4.11). La importación con GDAL responde `400` `LAYER_REQUIRED` (`layer`, con `layers[]` en el cuerpo)
y `422` `FORMAT_UNAVAILABLE` (`file`); el proxy WMS, `422` `WMS_REQUEST_NOT_ALLOWED` y `WMS_PARAMETER_NOT_ALLOWED` (el
parámetro) y `503` `GEOSERVER_UNAVAILABLE` (`wms`); la exportación geográfica, `400` `OBJECT_NOT_EXPORTABLE`
(`object`), `INVALID_FORMAT` (`format`), `INVALID_COORDINATES` (`coordinates`) e `INVALID_GEOMETRY` (`geometry`), `403`
`DISCLOSURE_NOT_ALLOWED` (`disclosureLevel`, `filters.componentType` o `filters.tenureType`), `422`
`FORMAT_UNAVAILABLE` (`format`, no `file` como en la importación), `500` `EXPORT_FAILED` (`format`) y `503`
`EXPORT_TIMEOUT` (`format`), además de los códigos de las exportaciones de F2. El detalle está en §4.2.

Los endpoints de F7 (§4.14) usan la misma forma. `runs`, `assistant/questions` y `knowledge/questions` responden
`503` `AI_UNAVAILABLE` (`organization` si la organización no tiene la IA activada, `provider` si no hay
proveedor configurado o no responde; ASM-128). `assistant/questions` responde además `422` `AI_INPUT_TOO_LARGE`
(`question`: la pregunta pasa `sgspe.ai.limits.question-chars`) y `403` `PERSON_REQUIRED` a una cuenta de
servicio, y `500` si el filtro de resultados del asistente falla cerrado (no
sale ningún dato hacia el modelo). `runs`
responde además `422` `AI_INPUT_TOO_LARGE` (`target`: el texto de la fuente pasa el tamaño
de `sgspe.ai.limits`, antes de llamar al modelo; ASM-131), `422` `AI_NO_TEXT` (`target`: un PDF sin texto o un formato
que no es PDF ni DOCX; ASM-132) y `502` `AI_RUN_TRUNCATED` (`useCase`: la corrida se quedó sin margen de tokens o de
acciones y no guardó sugerencias ni candidatos; ASM-131). El detalle está en §4.14.

Los endpoints de F8 (§4.11, §4.12 y §4.14) usan la misma forma. Los tableros responden `400` `PROJECT_REQUIRED`
(`project`: el tablero `project` sin un solo proyecto) y `TOO_MANY_RECORDS` (`project`: más de
`sgspe.dashboards.executive.max-projects` proyectos pedidos de forma explícita en `executive`). El cierre de periodo
responde `409` `INDICATOR_NOT_CLOSABLE`
(`indicator`, con el miembro `reason` del cuerpo: `STANCE`, `NO_SCALAR_VALUE`, `UNAVAILABLE` o `NOT_PERIOD_BOUND`;
ASM-136), `400` `PERIOD_NOT_ENDED` (`attributes.period_end_date`, que se comprueba antes que cualquier razón), `409`
`MEASUREMENT_EXISTS`
(`attributes.period_start_date`) y `403` `ACTION_REQUIRED` (`project`: todo el tenant sin ser
`ADMIN`). Los entregables responden `422` `DELIVERABLE_PARAMETERS_MISSING` (un error por parámetro: `project`,
`community`, `study`, `area_of_influence`, `period_start_date`, `period_end_date`, `cutoff_date` o
`project.working_crs`), `409` `STATE_MISMATCH` (`state`: un borrador fuera de `draft` u `observed`, o una emisión fuera
de `approved`; `draft`: el borrador no está LISTO), `409` `SECTIONS_CHANGED` (un error por sección:
`deliverable_section.<section_key>`), `409` `MODEL_MISMATCH` (`draftModelSha256`), `409` `MAP_FAILED` (`draft`), `400`
`CHECKLIST_REQUIRED` (`checklistConfirmed`), `422` `TEMPLATE_INVALID` (`file`, con `violations[]` en el cuerpo; ASM-135)
y `403` `FILE_WRITE_NOT_ALLOWED` (el campo `FILE`: una subida por la API genérica o por las rutas nativas de wasichai-files; ASM-133; un `PUT` por la API genérica sobre `deliverable_version`, que es `apiOnly`, también es `403`). El pedido de un borrador responde además `422` `TYPE_NOT_AVAILABLE` (`deliverable_type`) y `TEMPLATE_UNAVAILABLE` (`template_key`). AI-04 responde `422`
`SECTION_NOT_ASSISTED` (`target.recordId`; al copiar un resumen ejecutivo a una sección sin IA, sobre `attributes.section`); aceptar una sugerencia de sección, `422` `UNRESOLVED_PSEUDONYM`
(`attributes.content`); y `DeliverableWriteGuard` en la API genérica, `422` `AI_ORIGIN_LOCKED` (`content_origin`, también al
borrar una sección asistida) y `MENTION_NOT_READABLE` (`content`) (ASM-137). Un `format` desconocido en una descarga es el `422` de validación sobre `format`, sin código, y un archivo
que nunca se sirve, `404` sin código. El detalle está en §4.11, §4.12 y §4.14.

Los comandos de F3 (§4.5 y §4.9) responden además `400` `SAME_DUE_DATE` (`attributes.new_due_date`), `FIELD_REQUIRED`
(`attributes.<campo>`, o `due_date` si el compromiso no tiene fecha que reprogramar), `FIELD_NOT_EDITABLE`
(`attributes.<campo>`), `INVALID_LINK` (`links[i].object` u `object`), `REASON_REQUIRED` (`reason`: un vínculo de
evidencia con un objeto `requiresReason`), `INVALID_NUMBER` y `OUT_OF_RANGE` (`attributes.delivered_amount`: un monto de
más de 15 dígitos enteros, de más de 4 decimales o negativo), `DUPLICATE_NOT_FOUND` y `DUPLICATE_OTHER_PROJECT`
(`duplicateOf`); `403` `PERSON_REQUIRED` (una cuenta de servicio no decide sobre un candidato), `ROLE_REQUIRED` (falta
el rol de `accept` o de `mark_duplicate`) y `ACTION_REQUIRED` (`classification`: descargar una evidencia restringida sin
`DOWNLOAD_RESTRICTED`); y `409` `STATE_MISMATCH` (`state`; `commitment` si la entrega que se corrige no es de un
compromiso ni de un aporte). El motivo que falta en una reprogramación, una entrega, una aceptación o un duplicado es un
`400` de validación sobre `reason`, con texto libre. Una evidencia de `evidence[]` que no existe en el alta de una
entrega responde el `404` de wasichai, sin código, y no escribe nada. La idempotencia (§3.5) responde `400` de
validación sobre `Idempotency-Key` (mal formada), `409` `IDEMPOTENCY_IN_FLIGHT` con `Retry-After: 1` y `422`
`IDEMPOTENCY_KEY_REUSED`, los dos con `field` `Idempotency-Key`.

**Comportamiento fijado de wasichai 0.3.2.** Dos respuestas no son las que describe la tabla de arriba; las pruebas de
contrato las fijan para que un cambio de wasichai avise:

- Un `401` sin token lo emite el punto de entrada de Spring Security: lleva `WWW-Authenticate: Bearer` y el cuerpo va
  vacío, no es Problem Details. El `401` del login sí es `application/problem+json`. Prueba: `ErrorContractApiTest`
  (`401 sin token lo emite spring security con WWW-Authenticate y sin cuerpo` y `401 del login es Problem Details`).
- Un atributo que el objeto no tiene se ignora al crear un registro: la respuesta es `201` y el atributo no se guarda, no
  un `400`. El `400` llega con un valor inválido de un campo que sí existe. Prueba: `ErrorContractApiTest`
  (`un atributo que el objeto no tiene se ignora sin error` y `400 nombra el campo con un valor invalido`). El código de
  SGSPE manda solo campos del modelo.

### 3.4 Paginación, orden y filtros

- Las listas usan los parámetros y la forma de página de wasichai: `page` (desde 0), `size` (de 1 a 200; 25 por defecto),
  `sort`, `dir` (`asc` o `desc`), `count` y `after`; la página es `{ content, page, size, totalElements, totalPages,
  nextCursor }` [WAS-ADR-0036] (RNF-INT-001).
- `count=false` evita el conteo total; `after=<nextCursor>` lee por cursor sin `OFFSET`, con el mismo `sort`, `dir` y filtros.
- Se ordena por campos del modelo o por `created_at`, `updated_at` e `id`; los empates se ordenan por `id`.
- Los filtros de cada endpoint se listan en su fila: los que comparan un campo del modelo usan su nombre (`deadline_status`),
  los propios de la consulta van en camelCase (`periodStart`, `periodEnd`). Un filtro que admite varios valores se repite
  (`project=…&project=…`).
- **Un parámetro desconocido es un `422` que lo nombra en `errors[]`, nunca un filtro ignorado** (ASM-054). La API
  genérica, en cambio, responde `400` a un filtro por un campo que no existe [WAS-DOC api/rest.md].

### 3.5 Idempotencia (`Idempotency-Key`)

Desde F3, con el contrato de la cabecera nativa de wasichai (WAS-ADR-0058, decisión D3, ASM-065):

- **Dónde.** La API genérica la honra en `POST /api/objects/{object}/records` (tabla nativa `idempotency_keys`). Los
  comandos de `/api/sgspe/**` marcados `IK` en §4 la honran con la tabla fija `sgspe.idempotency_key`
  ([database-model.md §22](database-model.md)). El portal genera una clave por acción del usuario y la repite en cada
  reintento. Las dos tablas son espacios de claves separados: la misma clave usada en un alta genérica y en un comando
  corre las dos peticiones, así que una clave nunca se reutiliza entre acciones.
- **Forma.** De 1 a 128 caracteres ASCII imprimibles; otra cosa es un `400` sobre `Idempotency-Key`. La clave vale por
  organización y usuario: la de otra persona es otra clave.
- **Primera petición.** El comando corre como sin clave (permisos, reglas, guardas, auditoría y listeners) y su
  respuesta se guarda con la clave en la misma transacción: sin comando no hay clave. Un comando que falla no deja
  clave: se corrige y se reenvía con la misma. La respuesta guardada tiene un tope de 2 MiB; un comando que respondiera
  más fallaría con `500` y no escribiría nada (un comando responde uno o dos registros, muy por debajo).
- **Reenvío con la misma petición** (mismo método, ruta y cuerpo, con las claves ordenadas: el orden y los espacios no
  cuentan): la respuesta guardada, byte por byte, con su estado, su `ETag` y su `Location`, y la cabecera
  `Idempotent-Replayed: true`. No corre nada, ni permisos ni guardas: devuelve a quien llama su propia respuesta, tal
  como la recibió, aunque el registro haya cambiado después o quien llama haya dejado de leer alguno de sus campos.
- **La misma clave con otra petición:** `422` `IDEMPOTENCY_KEY_REUSED` sobre `Idempotency-Key`.
- **En curso.** Mientras la primera corre, otra con la misma clave recibe `409` `IDEMPOTENCY_IN_FLIGHT` con
  `Retry-After: 1`: el candado es de transacción y no espera, así que nunca retiene una conexión.
- **Plazo.** `wasichai.idempotency.ttl` (24 h por defecto), el mismo para las dos tablas. Pasado el plazo la clave no
  existe aunque la purga no haya pasado; JOB-08 borra cada día las vencidas de `sgspe.idempotency_key`. Hasta entonces
  la tabla guarda la respuesta entera, con los campos restringidos que quien llamó podía leer (por ejemplo, `recipients`
  y `conformity_by` de una entrega): es un límite aceptado (`security.md` §11.6).
- **Sin `IK`.** La confirmación de una importación y el alta de componentes no la honran: son idempotentes por
  conciliación (un reintento encuentra las filas `UNCHANGED` o responde `409` `SAME_COORDINATES`), y envolverlos en la
  transacción de la clave pondría dentro de ella las cuentas de PostGIS, que corren fuera de toda transacción. Las
  exportaciones y las descargas no crean registros de negocio.
- **ETag.** Un comando responde `ETag` con el `updatedAt` del registro que el portal sigue editando: el compromiso en
  una reprogramación y en la aceptación de un candidato, la entrega en su alta y su corrección, la evidencia en su alta
  y su cambio, el candidato al marcarlo como duplicado y el grupo de interés en una evaluación. El portal lo usa como el
  próximo `If-Match`.

### 3.6 Concurrencia: precondición sobre `updatedAt`

wasichai no tiene bloqueo optimista y su `PUT` reemplaza el registro entero (UPS-04, CON-047). Mientras tanto, el portal
envía como precondición el `updatedAt` que leyó (RNF-INT-003, ASM-035). El mecanismo es una propuesta de este documento, que
decide ADR-0025 (Propuesto, se acepta en F2).

- **Cabecera:** `If-Match` con el `updatedAt` del registro tal como lo devolvió la API, entre comillas, como una etiqueta de
  entidad: `If-Match: "2026-10-07T14:03:11.482913Z"`.
- **En los comandos propios** que cambian un registro existente que el cliente eligió (marca `IM` en §4), `If-Match` es
  obligatorio: sin él, `428`. El comando toma `ClusterLock.withXactLock` sobre `<objeto>:<id>`, relee el registro dentro de su
  transacción y compara; si no coincide, `412` con el valor actual en `errors[]`, y no escribe nada. Dos comandos sobre el
  mismo registro quedan serializados en todas las réplicas, así que la comprobación es atómica [WAS-ADR-0039].
- **En la API genérica** (`PUT`, `PATCH`, `DELETE`, transiciones, vínculos y archivos), el portal envía la misma cabecera.
  Un filtro web de `sgspe.common`, que corre después de la cadena de seguridad, lee las versiones aceptadas (el punto
  siguiente) y con ellas:
  - Las pone en el contexto de Reactor, como hace el filtro de correlación con su identificador. Una guarda de
    `sgspe.common` las compara con el `updatedAt` almacenado antes de la escritura y responde el `412` con el valor actual;
    la guarda corre en la corrutina de la petición, que conserva ese contexto [WAS-ADR-0040].
  - Cambia `If-Match` por esa lista de etiquetas fuertes. wasichai vuelve a comparar en el propio `UPDATE`, `DELETE`,
    transición o subida de archivo [WAS-ADR-0051]: una escritura de otro que cae entre la guarda y la escritura recibe el
    `412` de la plataforma, con `errors[0].field = "If-Match"`, y no se pierde nada. Un vínculo no tiene esa segunda
    comparación, pero tampoco mueve `updatedAt` ni reemplaza nada.
  - Un valor que no es un instante sale de la petición y la guarda responde `400`; `*` no compara.
  - Si no puede leer las versiones aceptadas (la base no responde), el filtro responde el `500` de wasichai en
    problem+json y la petición no sigue.
- **Cuando solo cambiaron derivados** (ADR-0027). Si el `updatedAt` leído ya no es el actual, no hay `412` mientras todo lo
  escrito después sean campos derivados: los conteos de asistentes que mueve un participante, el área que recalcula un
  listener, lo que escribe JOB-02.
  - Cada escritura de solo derivados (`Records.patch`) se condiciona al `updatedAt` que leyó y deja su salto exacto, de
    qué versión a qué versión, en la tabla fija `derived_version`.
  - Las versiones aceptadas son la leída y las que la siguieron por esos saltos. Una escritura que no es un salto (la de
    otra persona, un cambio de geometría, una transición) corta la cadena aunque caiga a un milisegundo, porque no se
    mira la hora de la auditoría.
  - Un vínculo no mueve `updatedAt` [WAS-ADR-0051], así que no hace falta tolerarlo.
  - **Límites conocidos:** la cadena llega hasta 500 saltos, y un salto se guarda 7 días (ASM-091). Más allá, la
    respuesta es `412`: falla cerrado, y el portal recarga el registro y repite.
- **`/admin` no envía la cabecera:** ahí sigue ganando la última escritura (UPS-04).
- **Precisión:** se comparan instantes al microsegundo, la precisión de la columna y del `ETag` de wasichai
  [WAS-ADR-0051], y cada escritura toma la hora de su propia sentencia, así que dos escrituras no comparten `updatedAt`.
- **Comandos y API genérica a la vez:** un comando compara con su candado, pero su escritura aún no lleva la versión, y la
  API genérica no toma ese candado; entre las dos queda la ventana de ADR-0025 (ADR-0027, seguimiento).

```json
{
  "type": "https://wasichai.dev/problems/412",
  "title": "Precondition Failed",
  "status": 412,
  "detail": "El compromiso cambió después de que lo leyeras: vuelve a cargarlo",
  "instance": "/api/sgspe/commitments/3f2a9c1e-5b7d-4e2f-9a1c-7d6e5f4a3b21/reschedules",
  "errors": [{ "field": "updatedAt", "message": "valor actual 2026-10-07T14:05:42.100371Z" }]
}
```

### 3.7 Motivo de cambio (`X-Change-Reason`)

- La misma cabecera de wasichai: texto de hasta 500 caracteres; lo que no es ASCII viaja en la forma RFC 8187
  `UTF-8''<texto codificado>` (en JavaScript, `"UTF-8''" + encodeURIComponent(motivo)`) [WAS-ADR-0041] [WAS-DOC api/rest.md].
- Un comando pasa el mismo motivo a cada escritura y transición que hace, así que aparece en cada fila de auditoría del comando.
- Es obligatorio (marca `CR` en §4) cuando el comando escribe un objeto `requiresReason` o aplica una transición de devolver,
  rechazar, suspender, cancelar, retirar o reabrir; sin él, `400` sobre `reason` y nada se guarda (RQ-AUD-002, ASM-058).
- Los registros que escribe una importación llevan el motivo `importación <id>` y los recalculadores, `derivado: <regla>`
  (las que escribe la vuelta de JOB-02 añaden el sufijo ` (JOB-02 DerivedFieldsJob)`), mientras la auditoría
  no tenga un campo de origen (ASM-051, UPS-03) [domain-model §7.4].

### 3.8 Identificador de correlación (`X-Correlation-Id`)

- Se acepta el que manda el cliente si cumple `^[A-Za-z0-9._-]{1,64}$`; si no viene o no cumple, se genera un UUID. Un valor
  inválido nunca se devuelve tal cual (RQ-AUD-006, ASM-034).
- Toda respuesta lo lleva, también un `401`, porque el filtro va antes de la cadena de seguridad.
- Va en la línea de acceso y en los logs del código de SGSPE, y en las llamadas salientes de SGSPE a `sgspe-latex` y a
  GeoServer; llegará a las filas de auditoría cuando exista UPS-03.

### 3.9 Permisos y alcance

- Cada fila de §4 dice qué permiso o acción declarada exige. El endpoint comprueba todos los permisos que necesita antes de la
  primera escritura y responde `403` nombrando el que falta, como en caja; `RecordService` vuelve a comprobarlos en cada
  escritura y aplica los permisos de campo (RQ-SEC-004).
- El código nunca compara nombres de rol (RQ-SEC-005). Cuando un comando aplica una transición, `WorkflowService` exige además
  el rol de esa transición, que fija `workflows.md`.
- Las escrituras sobre objetos de un proyecto pasan por `ProjectScopeGuard`: fuera de las asignaciones de quien llama,
  `403`. La emisión manual de un documento pasa por `IssueDocumentFilter`, que además exige UPDATE sobre el objeto y,
  con `EDIT_ANY`, que el registro sea propio (§3.3). Las consultas propias aplican el mismo alcance de lectura que la
  API genérica: el alcance de lectura por proyecto `ProjectReadScope` (`security.md` §9.7, ADR-0029) y la escritura
  acotada por `project_assignment`; todas aceptan `project` para acotar (RQ-SEC-006, GAP-025).
- Ningún rol usa `own_records_only`. `FIELD_AGENT` solo cambia o borra lo que creó porque no tiene `EDIT_ANY`, y eso lo
  aplica `EditAnyGuard` (`sgspe.scope`) con el permiso, nunca con el nombre del rol; sí agrega participantes u
  observaciones a las interacciones y visitas que puede leer (RQ-SEC-008, ASM-069).
- Toda exportación, toda emisión y toda descarga de un archivo de entregable (borradores y versiones, en todo nivel)
  escriben una fila de `export_log`, cuyo `export_kind` distingue exportación, emisión y descarga; una salida de nivel
  EXTERNO exige `checklistConfirmed: true` (RQ-AUD-011, RQ-REP-033).

Las acciones declaradas son las del catálogo de RQ-SEC-004, con los objetos que fija `security.md` y que replica
[domain-model §8.1.1]; esta API las usa tal cual:

| Acción | Objetos que la declaran | Dónde la exige esta API |
|---|---|---|
| `APPROVE` | `project`, `area_of_influence`, `commitment`, `grievance`, `social_contribution`, `social_program`, `deliverable` | `POST /api/sgspe/areas-of-influence/{id}/approve` y las transiciones de aprobación de la API genérica, incluida la resolución de una queja ALTA o CRITICA (RN-GRV-002) |
| `VERIFY` | `commitment` | La transición a `verified` |
| `APPROVE_OVER_CAP` | `commitment`, `social_contribution` | La transición de aprobación cuando se supera el tope de apoyo social de la campaña (`SupportPolicyGuard`, RN-INV-003) |
| `CONFIRM_ALERT` | `early_warning` | Las transiciones a `confirmed` y `dismissed` (RN-EWS-001) |
| `EXPORT` | `stakeholder`, `commitment`, `grievance`, `inquiry`, `stakeholder_request`, `social_risk`, `deliverable` y, desde F6, los 13 objetos de las capas LYR-01 a LYR-16 (`gis.md` §2.2): `administrative_unit`, `locality`, `road_access`, `community`, `community_sector`, `mining_concession`, `environmental_feature`, `project`, `project_component`, `area_of_influence`, `land_parcel`, `school` y `health_facility`, nunca `land_right`; las capas de gestión (LYR-17 a LYR-24) no salen como archivo geográfico (ASM-124) | `POST /api/sgspe/exports/{kind}`, `POST /api/sgspe/territory/exports` y la descarga de versiones EXTERNO y PUBLICO |
| `GENERATE` | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts` |
| `ISSUE` | `deliverable` | `POST /api/sgspe/deliverables/{id}/issue` |
| `DOWNLOAD_RESTRICTED` | `evidence`, `deliverable` | La descarga de evidencias CONFIDENCIAL o DATOS PERSONALES, de borradores y de versiones INTERNO, y bajar la clasificación de una evidencia restringida (`PATCH /api/sgspe/evidence/{id}`, ASM-071) |
| `EDIT_ANY` | `engagement`, `engagement_participant`, `field_visit`, `field_observation`, `evidence` | Cambiar, transicionar, vincular o borrar un registro que creó otra persona (`EditAnyGuard`), por la API genérica o por un comando; agregar participantes u observaciones a una interacción o visita ajena no la exige |

### 3.10 Archivos

- Las importaciones usan `multipart/form-data` con una parte `file` y sus campos (§4.2). El tamaño máximo y el tiempo de
  proceso son configurables; corren en una carpeta temporal con control de rutas dentro de los ZIP (RQ-SEC-020,
  ASM-050). En F2, `sgspe.territory.import.*`: `max-bytes` (20 MB), `max-rows` (10 000), `timeout` (60 s, lectura y
  validación) y `max-concurrent` (4 importaciones a la vez); Spring guarda la parte de archivo en disco con su propio
  tope (`spring.webflux.multipart.max-disk-usage-per-part`, 20 MB, con `max-parts` 8), que responde igual (400
  `FILE_TOO_LARGE`; un campo de texto de más de 1 MB o encabezados de parte de más de 10 KB, `PART_TOO_LARGE`;
  demasiadas partes, `INVALID_MULTIPART`, ambos sobre el campo `multipart`).
- Los archivos de una evidencia los sube la ruta de wasichai-files (`POST /api/objects/evidence/files/file`, parte
  `file`), con el tope y los tipos del campo `evidence.file` (20 MB, el mismo tope de Spring por parte, así que pasarse
  es siempre `400` `FILE_TOO_LARGE` sobre `file`; desde F8, `wasichai.files.max-bytes` sube a 64 MB para los archivos
  que sube el servidor, y toda subida del navegador sigue topada en 20 MB, ASM-138), el tipo detectado por sus bytes y
  el SHA-256 calculado en el servidor (WAS-ADR-0061). El descriptor del archivo (`{ id, name, contentType, size, sha256
  }`) lo lee todo lector del registro, salvo en una evidencia restringida (ver abajo).
- La escritura de la confirmación no está bajo `timeout`: corre después, dentro del candado y de una sola transacción,
  que retienen una conexión del pool hasta el commit. Su cota es `max-rows`: cada fila son unas pocas escrituras con sus
  guardas (del orden de milisegundos con la base local, una estimación sin medir), así que una hoja de 10 000 filas
  puede tardar decenas de segundos o, con versiones nuevas, el doble. El proxy delante del backend debe dejar a
  `POST …/confirm` más tiempo que a las demás rutas (al menos 120 s); si la corta, la transacción se revierte entera y no
  queda nada escrito. Una hoja más grande se parte en varias importaciones.
- Las descargas son en streaming, con `Content-Type`, `Content-Disposition` y, para lo inmutable (evidencias y versiones
  emitidas), `ETag` igual al SHA-256 del archivo. Las de evidencias y de entregables (borradores y versiones) son
  siempre `attachment`, con `X-Content-Type-Options: nosniff` y `Cache-Control: no-store`.
- Los archivos de entregables (§4.12) son campos `FILE` de wasichai-files (ASM-133): los del borrador los sube JOB-05
  como la plataforma (UPS-31, desde wasichai 1.3.0), los de una versión quien emite y el de una plantilla `POST
  /api/sgspe/deliverable-templates`. Las rutas nativas de archivos de esos objetos las atiende SGSPE.
- Ninguna URL externa se descarga en el servidor (ASM-050).

## 4. Endpoints de `/api/sgspe/**` por bounded context

Columnas: **Encabezados** usa `IK` (honra `Idempotency-Key`, §3.5), `IM` (exige `If-Match`, §3.6), `IM?` (lo admite sin
exigirlo) y `CR` (exige `X-Change-Reason`, §3.7). **Fase** es la del roadmap; una ruta de una fase futura no existe
antes. Las transiciones que no son de comando, las altas y las ediciones de objetos que no son `apiOnly` usan la API
genérica (§2).

### 4.1 Transversal (`sgspe.common`)

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/transition-checks` | Ejecuta en seco las guardas de SGSPE sobre una transición y devuelve lo que la bloquearía y las advertencias, sin escribir | READ sobre el objeto | `{ object, recordId, transition }` → `200 { allowed, blocking[], warnings[] }` | — | F2 |
| `GET` | `/api/sgspe/people` | El nombre visible de las personas de la organización de quien llama, por su correo (ASM-142) | Autenticado | `?email=…&email=…` (hasta 100; sin distinguir mayúsculas) → `200 [{ email, displayName }]`; un correo desconocido o de otro tenant no sale; más de 100 o un parámetro desconocido, 422 | — | F8 |
| `GET` | `/api/sgspe/codes/next` | El código que el portal propone en el alta de un registro: `PREFIJO-AAAA-NNN` con el año de Lima y el siguiente número de ese prefijo y año en el proyecto (decisión 6 de la revisión de UX); el campo sigue editable | `READ` sobre el objeto | `?object=…&project=<id>`, con `object` uno de `commitment`, `agreement`, `grievance`, `inquiry`, `stakeholder_request`, `social_risk`, `incident`, `social_contribution` o `social_program` → `200 { code }`; cuenta solo lo que quien llama lee; otro objeto o un proyecto que no es un id, 400; un proyecto que no lee, 404; un parámetro desconocido, 422 | — | F8 |

La lista de transiciones de wasichai ya explica las que el rol no permite [WAS-DOC api/rest.md]; esta consulta agrega las
reglas de las guardas (por ejemplo, el cierre de una interacción con preguntas pendientes, RN-ENG-005, o un tope de campaña
superado sin `APPROVE_OVER_CAP`, RN-INV-003) y muestra antes de confirmar las reglas de tipo advertencia, que piden una
justificación pero no bloquean (RN-ENG-001, RN-INV-005, RN-INV-006).

**Forma de la respuesta de `transition-checks`.** `allowed` es verdadero si `blocking` está vacío. Cada ítem de `blocking[]` y
`warnings[]` es `{ code, message, field }`: `code` es una constante en inglés UPPER_SNAKE, `message` va en español y sin datos
personales, y `field` es el campo que el portal pinta, o `null` si el hallazgo no apunta a uno. Corre las mismas reglas que la
escritura real, y la consulta no escribe nada. Lee todo como quien llama, con sus permisos de objeto y de campo: sin READ sobre
el objeto responde 403 o 404 y no dice nada del registro; sin token, 401 con el cuerpo vacío (§3.3). Un parámetro de consulta
desconocido es 422 y una propiedad desconocida del cuerpo, 400.

**No evalúa los roles de la transición** ni el permiso UPDATE: los explica la lista de transiciones de wasichai
(`GET /api/objects/{objeto}/records/{id}/transitions`, con `allowed` y `reason` por transición). El portal combina las dos.
Las reglas de cada transición corren aunque el registro esté en otro estado, así que con `STATE_MISMATCH` pueden salir también
bloqueos hipotéticos.

**Las reglas de un compromiso** corren en un orden fijo: las de su aprobación primero D8 (`approve_direct`), después D1
y D2, D3 y D4, D5 y al final D7, la única que toma un candado; las del cumplimiento, D9, D10 y D11. La escritura real se
detiene en el primer bloqueo y la consulta los junta todos, en ese orden, sin tomar candados: si los datos cambian entre
las dos, la escritura puede responder otro código de la lista. `mark_duplicate` de un candidato sin `duplicate_of`
informa siempre `FIELD_REQUIRED`: la transición real la aplica el comando de duplicado (§4.5), que escribe
`duplicate_of` antes, así que el portal no deshabilita el botón por ese bloqueo.

| Código | Tipo | Estado de la escritura real | Cuándo |
|---|---|---|---|
| `STATE_MISMATCH` | bloqueo | 409 | El registro no está en el estado de origen (`from`) de la transición |
| `COMMAND_ONLY` | bloqueo | 403 | La transición es de comando: solo la aplica su endpoint (`assess` de WF-B; `approve` y `supersede` de WF-O; `accept` de WF-K) |
| `REASON_REQUIRED` | advertencia (`field`: `reason`) | 400 | La transición exige motivo (`X-Change-Reason`): pídalo antes de confirmar |
| `NOT_ASSIGNED` | bloqueo | 403 | Quien consulta, si no es ADMIN, no tiene una asignación vigente al proyecto del registro |
| `ACTION_REQUIRED` | bloqueo (`field`: `record` en un compromiso) | 403 | `approve` de un proyecto exige la acción declarada `APPROVE` además del rol de la transición; aprobar un compromiso sin `APPROVE` (D3) o verificarlo sin `VERIFY` (D11) |
| `WORKING_CRS_REQUIRED` | bloqueo (`field`: `working_crs`) | 409 | `approve` o `resume` de un proyecto sin `working_crs`, con `policy_activation_preconditions` activa |
| `PARTY_REQUIRED` | bloqueo (`field`: `project_party`) | 409 | `approve` o `resume` de un proyecto sin una parte CLIENTE u OPERADOR, con la misma política activa |
| `NOT_OWN_RECORD` | bloqueo | 403 | El objeto declara `EDIT_ANY`, quien consulta no lo tiene y no creó el registro |
| `PROFILE_INCOMPLETE` | bloqueo (`field`: el primer campo que falta) | 409 | `profile` de un grupo de interés sin `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` o `owner_user_id` |
| `FIELD_REQUIRED` | bloqueo (`field`: `scheduled_at`, `held_at`, `minutes` o `cancel_reason`; desde F3, el primer campo de la matriz o del responsable que falta, `resource_type`, `signed_date`, `effective_date`, `community` o `duplicate_of`) | 409 | La transición de una interacción necesita ese campo: programar o reprogramar, registrar la realización, el acta o cancelar. Desde F3: `submit` y las aprobaciones de un compromiso (D1, D2); `register_fulfillment` sin tipo de recurso (D9); la activación de un acuerdo; `mark_duplicate` de un candidato |
| `ATTENDANCE_REQUIRED` | bloqueo (`field`: `attended`) | 409 | Registrar la realización sin ningún participante con `attended` |
| `PENDING_QUESTIONS` | bloqueo (`field`: `engagement_question`) | 409 | `close` o `close_light` con preguntas sin respuesta (RN-ENG-005) |
| `MINUTES_REQUIRED` | bloqueo (`field`: `engagement_type`) | 409 | `close_light` de una interacción cuya política exige acta (RN-ENG-004) |
| `OBJECT_NOT_READABLE` | bloqueo (`field`: el objeto que no lee) | 403 | Quien consulta no lee `project_party` (no se verifica `PARTY_REQUIRED`), `engagement_participant` o `engagement_question` (asistencia y preguntas pendientes), o `project` en `close_light` (no se verifica la política de actas: lectura estricta). Desde F3, lo que leen las reglas de un compromiso o de un acuerdo: `commitment`, `engagement`, `project`, `exploration_campaign`, `stakeholder`, `delivery`, `evidence`, `commitment_dependency` o `agreement_land_parcel`; una regla que no puede leer nunca deja pasar |
| `STAKEHOLDER_REQUIRED` | bloqueo (`field`: `commitment_stakeholder`) | 409 | Un compromiso sin solicitante (D1) |
| `SEGREGATION_OF_DUTIES` | bloqueo (`field`: `record` u `owner_user_id`) | 403 | Quien aprueba registró el compromiso (D4); quien verifica es su responsable (D11) |
| `SENIOR_APPROVAL_REQUIRED` | bloqueo (`field`: `origin_engagement` si nace de una mesa de diálogo, `commitment_stakeholder` si lo pide un grupo de interés POLITICO) | 409 | `approve` o `approve_direct` de un compromiso que exige nivel superior (D5, RN-ENG-002) |
| `INTERNAL_APPROVAL_REQUIRED` | bloqueo (`field`: `project`) | 409 | `approve_direct` con la aprobación interna activa (D8) |
| `SUPPORT_CAP_EXCEEDED` | bloqueo, o advertencia con `policy_cap_blocks_approval` en falso o para quien tiene `APPROVE_OVER_CAP` (`field`: `exploration_campaign`) | 409 | La aprobación supera el tope de la campaña (D7) |
| `CAP_DATA_INVALID` | Como `SUPPORT_CAP_EXCEEDED` (`field`: `exploration_campaign`) | 409 | Un monto guardado de la campaña (el tope o el de otro compromiso) no es un monto válido: se omite de la suma y la aprobación no puede comparar el tope |
| `CAP_OTHER_CURRENCY` | advertencia (`field`: `currency`) | — | Hay montos de la campaña en otra moneda, que no se suman al tope, o la campaña no tiene moneda y el tope no se compara |
| `DELIVERY_REQUIRED` | bloqueo (`field`: `delivery`) | 409 | `register_fulfillment` sin una entrega con conformidad y quiénes la dieron (D9) |
| `EVIDENCE_REQUIRED` | bloqueo (`field`: `evidence_delivery`) | 409 | A la entrega con conformidad le falta la evidencia de su tipo de recurso, del mismo proyecto del compromiso (D9) |
| `FIELD_NOT_READABLE` | bloqueo (`field`: `delivery.conformity_by`) | 403 | Quien consulta no lee quiénes dieron la conformidad, y D9 no se puede verificar |
| `BLOCKING_DEPENDENCY` | bloqueo (`field`: `commitment_dependency`) | 409 | Una dependencia bloqueante sigue abierta: el compromiso del que depende no está cumplido, verificado, cerrado ni cancelado (D10) |
| `LAND_PARCEL_REQUIRED` | bloqueo (`field`: `agreement_land_parcel`) | 409 | Un permiso de uso de terreno o una servidumbre sin predios |
| `ASSEMBLY_REQUIRED` | bloqueo (`field`: `assembly_engagement`) | 409 | Una autorización de asamblea sin la interacción de tipo ASAMBLEA |
| `ITEMS_REQUIRED` | bloqueo (`field`: `contribution_item`) | 409 | `approve` o `approve_direct` de un aporte sin ítems (P9) |
| `PAID_LABOR` | bloqueo sin `guideline_deviation_justification`, advertencia con ella (`field`: `contribution_item`) | 409 | Algún ítem es MANO DE OBRA y `policy_no_paid_labor` está activa (P2, RN-INV-001) |
| `MONETARY_DONATION` | Como `PAID_LABOR` (`field`: `contribution_item`) | 409 | Una DONACION con algún ítem DINERO y `policy_non_monetary_donations` activa (P2, RN-INV-002) |
| `PRIORITY_NOT_MET` | Como `PAID_LABOR` (`field`: `topic`) | 409 | Con `policy_priority_warning` activa y temas priorizados en el proyecto, el aporte no tiene tema o su tema no es priorizado ni desciende de uno (P3, RN-INV-005) |
| `PRIORITIES_NOT_CONFIGURED` | advertencia (`field`: `topic`) | — | Con `policy_priority_warning` activa, el proyecto no tiene temas priorizados y la prioridad no se evalúa; nunca exige justificación |
| `SYNERGY_AGREEMENT_REQUIRED` | bloqueo (`field`: `agreement`) | 409 | Un aporte con sinergia (P6) o un programa con socio (G3), con `policy_synergy_requires_agreement` activa, sin un acuerdo en `active` y no VENCIDO (en el programa, además, con el socio como parte); la justificación no lo cubre |

Las reglas de los demás contextos suman sus códigos a esta tabla junto con su guarda. Desde F5, las de un aporte (P1 a
P9) y las de un programa (G1 a G4) usan también, con el mismo tipo y el estado de su escritura real,
`ACTION_REQUIRED` y `SEGREGATION_OF_DUTIES` (`record`), `INTERNAL_APPROVAL_REQUIRED` (`project`),
`SUPPORT_CAP_EXCEEDED`, `CAP_DATA_INVALID` y `CAP_OTHER_CURRENCY` (P1), `FIELD_REQUIRED` (`contribution_type`, P9,
`400`), `DELIVERY_REQUIRED`, `EVIDENCE_REQUIRED` y `FIELD_NOT_READABLE` (P8, con las evidencias ACTA DE RECEPCION, FOTO
y COMPROBANTE) y `OBJECT_NOT_READABLE`. **Las reglas de un aporte** corren en el orden de `workflows.md` (WF-P): P7,
P9, P4 y P5, P6, P2 y P3 y al final P1; la consulta las junta todas sin tomar los candados del aporte y de la campaña,
así que el portal muestra cada lineamiento incumplido y pide la justificación antes de aprobar.

### 4.2 BC-02 Territorio y SIG

Paquete `sgspe.territory`. Las consultas espaciales cubren los casos de RQ-GIS-002, una por endpoint, con compuerta de
permisos (RQ-GIS-017); las geometrías salen en GeoJSON EPSG:4326 y las áreas, en hectáreas planas (RQ-GIS-009).

**Regla común de las consultas espaciales (F6, ASM-122 y ASM-126).** Ninguna escribe y todas leen como quien llama:

- Las que pasan por `SpatialGate` (`summary`, `component-parcels`, `component-communal-land`,
  `concession-territories`, `sensitive-distances`, `nearby-conflicts`, `concentration` y `data-quality`) componen los
  alcances de lectura de wasichai (`ProjectReadScope`, `ConfidentialAttentionScope`) y revisan READ sobre cada objeto y
  campo que usan. Sin READ sobre el objeto principal, `403`. `member-suggestions`, `buffer-localities`, `locate` y
  `zones/{zoneObject}/{id}/records` no usan la compuerta: filtran por `ZoneCriterion` y leen por `Records`, con los
  permisos y el alcance de la API genérica.
- `data-quality` es la excepción a las dos reglas que siguen: responde una página
  `{ check, content, page, size, hasNext }` sin `available`, `missingFields` ni `truncated`, se pagina con `page` y
  `size` (no con `limit`) y deja fuera los objetos que quien llama no lee con todos los campos de la revisión, o
  responde `403` si pidió uno de ellos en `object`.
- Toda otra respuesta de la compuerta lleva `available` y, si falta leer un objeto o un campo secundario,
  `missingFields` (`objeto.campo`). `available: false` quiere decir «lista incompleta»: trae lo que sí se leyó, nunca un
  cero (RQ-DSH-032). Las listas llevan `truncated`.
- `limit` es opcional, de 1 a `max-rows` (5 000, que es también el valor por defecto); más filas que `limit` se recortan
  con `truncated: true`, como las capas de F2. Las lecturas que dan cifras (`summary`, KPI-TER-04) no se recortan: más
  de `max-rows` es `400 TOO_MANY_RECORDS`.
- Los límites de ASM-126 (`sgspe.territory.queries.*`): `bufferM` de 1 a 20 000 m (500 por defecto), `distanceKm` de
  0.1 a 50 km (5 por defecto), el periodo de `concentration` hasta 5 años (12 meses que terminan hoy, en Lima, por
  defecto) y hasta 200 registros relacionados por fila (`max-related`). Fuera de rango, `400 OUT_OF_RANGE` sobre el
  parámetro (también una fecha con año fuera de 1900 a 9999); un valor que no se puede leer, `400
  INVALID_VALUE`; un parámetro desconocido, `422`.
- Las áreas, las longitudes y las distancias se miden en el `working_crs` del proyecto (sin él, `409
  WORKING_CRS_REQUIRED` sobre `project`) y las de los registros del tenant (comunidad, predio, concesión), en la zona
  UTM de su centroide (ASM-064); nunca en grados.
- `400 TOO_MANY_RECORDS` nombra en `field` el parámetro que acota la lectura (`limit`, `distanceKm`, `bufferM`, `id` o
  `lon`) o, si ninguno la acota, `project`.
- Un componente que solo toca el borde de un predio o de un territorio no cuenta como cruce: lo mismo en
  `component-parcels`, `component-communal-land`, `concession-territories`, KPI-TER-04, `summary` y las comunidades de
  la ficha de una localidad.
- Un proyecto, un área, una zona, una localidad o una comunidad que no existe o que quien llama no lee es `404` con su
  código (`PROJECT_NOT_FOUND`, `AREA_NOT_FOUND`, `ZONE_NOT_FOUND`, `LOCALITY_NOT_FOUND`, `COMMUNITY_NOT_FOUND`) sobre
  `id`. Los componentes de un proyecto que quien llama no lee no aparecen en ningún cruce (`security.md` §9.7).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/territory/imports` | Vista previa de una importación: lee el archivo de coordenadas o geometrías, lo transforma a 4326, lo valida contra PostGIS y lo concilia con lo registrado; no escribe nada; para `project_component` y `mining_concession` (que al confirmar se vincula al proyecto), quien no es `ADMIN` y no tiene una asignación vigente al proyecto recibe `403` `NOT_ASSIGNED` (`project`) antes de leer el archivo; los predios, las comunidades y los elementos ambientales, que son del tenant, no la exigen | CREATE y UPDATE sobre el objeto destino y, desde F3, CREATE y READ sobre `evidence` (la confirmación guarda el original y busca la evidencia del lote; la vista previa lo exige igual, antes de leer el archivo) | multipart: `file`, `target` (`project_component`, `land_parcel` o `mining_concession`; desde F6, también `community` y `environmental_feature`, ASM-123), `project`, `sourceCrs` (valor del catálogo "CRS de origen"), `columns` (json `{clave: encabezado}`; claves del portal en camelCase como `drillType` o `depth`, o campos del destino; un valor que empieza con `=` es una constante; sin tipo de componente, `SONDAJE`) y, desde F6, `layer` (la capa elegida de un archivo de varias) → `200 { importBatchId, sha256, target, sourceCrs, rows, valid, invalid[], differences[], outOfZone[] }`, sin geometrías, y desde F6 también `format`, `layer` y `detectedCrs` (el CRS que propone el archivo, EPSG o `null`); `differences[]` trae una entrada por fila válida con `outcome` `CREATE`, `UNCHANGED`, `NEW_VERSION` o `UPDATE`; con `Accept` de XLSX, el reporte de diferencias como archivo (hojas Diferencias, Rechazadas y Fuera de zona). Desde F6 (ASM-127): un archivo con más de una capa con elementos y sin `layer` es `400 LAYER_REQUIRED` (`layer`) con `layers[]`, cada capa solo con `{ name, geometryType, featureCount, detectedCrs }`; `sourceCrs` se exige aunque haya `detectedCrs` (sin él, `400` sobre `sourceCrs` con el mismo `layers[]`); KML, KMZ y GPX fijan EPSG 4326 sin pedirlo y otro `sourceCrs` con ellos es `400`; un formato de GDAL sin GDAL o sin su driver es `422 FORMAT_UNAVAILABLE` (`file`); `target` `community` o `environmental_feature` en una organización que todavía no tiene el modelo de F6 (un tenant de F5, sin su campo `code`) es `400` sobre `target`, antes de leer el archivo | — | F2 (XLSX, CSV, GeoJSON, latitud y longitud); F6 (Shapefile, KML, KMZ, GeoPackage, FileGDB y GPX por GDAL; destinos `community` y `environmental_feature`) |
| `POST` | `/api/sgspe/territory/imports/{importBatchId}/confirm` | Confirma: recibe otra vez el mismo archivo y los mismos campos con las decisiones fila por fila (el servidor no guarda nada entre las dos llamadas), exige el SHA-256 de la vista previa, repite la validación y escribe todo en una transacción con candado por destino (y por proyecto en los componentes) y el motivo `importación <id>`. Desde F3 guarda en esa misma transacción el archivo original como `evidence`, creada como quien confirma (ASM-060): `evidence_type` `MAPA`, `classification` `INTERNA` en los componentes y `DATOS PERSONALES` en los demás destinos (el original trae todas las columnas del archivo, también titulares, DNI o partidas que no se importan: su descarga exige `DOWNLOAD_RESTRICTED` y quien no la tiene lee el descriptor sin nombre ni huella), `title` «Importación de <destino> del <fecha>» (nunca el nombre del archivo), `document_date` de hoy en Lima, `import_batch_id` del lote y `project` solo en los componentes y las concesiones (los predios, las comunidades y los elementos ambientales son del tenant, y su evidencia también); su `id` es `evidenceId`. Lo que `evidence.file` admite (CSV, XLSX y, en UTF-8, GeoJSON, KML y GPX) va tal cual, con el SHA-256 de la vista previa; lo demás (el ZIP de un Shapefile o una FileGDB, KMZ, GeoPackage, un CSV que no es UTF-8) va adjunto byte a byte en un PDF cuya portada lleva el lote, el destino, el formato, el tamaño y ese SHA-256, nunca el nombre del archivo (`gis.md` §4.5). Un ZIP o un KMZ con una entrada que su formato no usa, o un `.gpkg` que no es una base SQLite, es `400` sobre `file` ya en la vista previa. Sin nada que escribir (todas las filas `UNCHANGED`, descartadas o rechazadas), no hay evidencia nueva: `evidenceId` es la del lote si ya la tiene (una confirmación repetida devuelve la misma) o `null`; en un tenant sin el modelo de F3, siempre `null` | Los mismos de la vista previa | multipart: `file`, `target`, `project`, `sourceCrs`, `columns`, `sha256`, `decisions` (json `[{ "row": 7, "action": "DISCARD" }]`, `action` `APPLY` o `DISCARD`; una fila sin decisión aplica su acción propuesta, salvo una de `outOfZone[]`, que solo entra con `APPLY`; una decisión sobre una fila que no es válida es `400`) y, desde F6, `layer` (la misma de la vista previa; con ella, la confirmación llega a las 8 partes de `max-parts`) → `200 { created, newVersions, updated, unchanged, rejected, evidenceId }`; los errores de F6 de la vista previa valen igual | — | F2; F6 (`layer` y los destinos `community` y `environmental_feature`) |
| `GET` | `/api/sgspe/territory/imports/formats` | Los formatos de importación que lee este servidor: los de GDAL, solo si hay `ogrinfo` y `ogr2ogr` de la versión mínima con su driver (ASM-127) | Autenticado | → `200 { formats: [{ format, available, multiLayer, crsFromFile }] }` con `format` en `xlsx`, `csv`, `geojson`, `shp`, `kml`, `kmz`, `gpkg`, `gdb` y `gpx`; `multiLayer` dice si el archivo puede traer varias capas y `crsFromFile` si trae su CRS, que es solo una propuesta (CON-024); un parámetro de consulta es `422` | — | F6 |
| `POST` | `/api/sgspe/project-components` | Alta manual de un componente o versión nueva si el código ya existe con otras coordenadas; nunca sobrescribe | CREATE y UPDATE sobre `project_component` | `{ attributes, geometries }` → `201 { component, previousVersion, distanceM }`; las mismas coordenadas (menos de 1 cm) son `409` `SAME_COORDINATES`; una geometría que PostGIS no procesa, `400 INVALID_GEOMETRY`; un proyecto que falta es `400 FIELD_REQUIRED` y uno que no es un UUID, `400 INVALID_VALUE`; sin ninguna geometría o con más de una, `400 GEOMETRY_REQUIRED` (`geometries`); un número que no lo es, `400 INVALID_NUMBER`, y fuera de rango, `400 OUT_OF_RANGE` (`attributes.<campo>`); un proyecto que no existe o que no puede leer, `400 PROJECT_NOT_FOUND`; si la versión vigente no tiene geometría, `409` `NO_CURRENT_GEOMETRY` (`geometries`), y si otra escritura registró la misma versión mientras tanto, `409` `VERSION_CONFLICT` (`attributes.code`); sin asignación vigente al proyecto, `403 NOT_ASSIGNED` antes de cualquier cuenta de PostGIS; toma el mismo candado por proyecto que la confirmación de una importación | — | F2 |
| `PATCH` | `/api/sgspe/project-components/{id}` | Cambia campos descriptivos (estado, campaña) de un componente sin tocar coordenadas; `project_component` es `apiOnly` | UPDATE sobre `project_component` | `{ attributes }` → `200` registro; una geometría en el cuerpo es `400 COORDINATES_NOT_EDITABLE`, y `code`, `project` o un campo de origen, `400 FIELD_NOT_EDITABLE`; solo escribe lo que cambia (un PATCH sin cambios deja el `updatedAt` como estaba); una versión reemplazada (`superseded`) no se cambia: `409` `COMPONENT_SUPERSEDED` (`attributes`), antes de comparar `If-Match`, porque RQ-GIS-010 «nunca sobrescribe» y las versiones anteriores son historia de solo consulta (`gis.md` §2.4 y §4.4); sin asignación vigente al proyecto, `403 NOT_ASSIGNED`, aunque el PATCH no cambie nada | `IM` | F2 |
| `POST` | `/api/sgspe/areas-of-influence/{id}/approve` | Aprueba una versión del área de influencia y lleva la aprobada anterior del mismo proyecto y tipo a `superseded`, en una transacción | UPDATE sobre `area_of_influence`, acción declarada `APPROVE` y los roles de las transiciones de comando de WF-O | → `200 { approved, superseded }` | `IM`, `CR` | F2 |
| `GET` | `/api/sgspe/areas-of-influence/{id}/summary` | Tamaño del área: superficie plana en ha, localidades miembro de esa versión por `locality_type` y componentes vigentes del proyecto del área que cruzan tierra comunal (`community.territory` o un predio `COMUNAL`) o privada (un predio `PROPIEDAD` o `POSESION`; los de `PROPIEDAD DE LA EMPRESA` no cuentan), con la misma consulta que KPI-TER-04 | READ sobre `area_of_influence` y los objetos resumidos | → `200 { available, missingFields?, areaHa, localitiesByType[]: { localityType, count }, componentsOnCommunalLand, componentsOnPrivateLand }`; una cifra que quien llama no puede leer sale `null` con `missingFields`, nunca cero; no se recorta: más de `max-rows` cruces es `400 TOO_MANY_RECORDS`; `404 AREA_NOT_FOUND` (`id`); sin parámetros (uno cualquiera es `422`) | — | F6 |
| `GET` | `/api/sgspe/areas-of-influence/{id}/member-suggestions` | Localidades, comunidades y unidades administrativas que el cálculo espacial sugiere como miembros; no escribe. Un área sin polígono usa el área de interés y los componentes vigentes del proyecto | READ sobre `area_of_influence` y sobre los objetos sugeridos | `?bufferM=` (de 1 a 20 000) → `200 { available, missingFields?, suggestions[]: { object, recordId, name, relation: INTERSECTS o WITHIN_BUFFER, alreadyMember: true, false o null } }`; sin `bufferM`, solo `INTERSECTS`; `alreadyMember` es `null` si quien llama no lee los miembros; `404 AREA_NOT_FOUND` (`id`); con `bufferM` y un proyecto sin `working_crs`, `409 WORKING_CRS_REQUIRED`; un área sin polígono cuyo proyecto quien llama no lee sale `available: false` con `missingFields: ["area_of_influence.project"]`; más de `max-rows` componentes o localidades candidatas, `400 TOO_MANY_RECORDS` (`bufferM` o `id`) | — | F6 |
| `GET` | `/api/sgspe/territory/zones/{zoneObject}/{id}/records` | Registros de un objeto asociados a una zona (`locality`, `community`, `administrative_unit` o `area_of_influence`), por relación territorial o por intersección con la geometría de la zona, que se lee antes como quien llama y viaja como parámetro (`ZoneCriterion`, ASM-122); una zona sin geometría filtra solo por relación | READ sobre la zona y sobre el objeto consultado | `?of=&periodStart=&periodEnd=&page=&size=` → la página de registros de wasichai (`content[]`, `page`, `size`, `totalElements`, `totalPages`) más `available` y, si el filtro de la zona usa un campo que quien llama no lee, `missingFields`; `of`: `stakeholder`, `commitment`, `social_risk`, `grievance`, `inquiry`, `stakeholder_request`, `engagement`, `incident` o `early_warning_signal`. En este orden: un parámetro desconocido, `422`; `periodStart` y `periodEnd`, y después `page` y `size`, ilegibles (`400 INVALID_VALUE`) o fuera de rango (`400 OUT_OF_RANGE`) sobre el parámetro; `of` que falta o es otro, `400 INVALID_OF` (`of`); la zona que no existe o que queda fuera del alcance de quien llama, `404 ZONE_NOT_FOUND` (`id`), y sin READ sobre el objeto de la zona, `403`; un periodo con `stakeholder` o `social_risk`, que no tienen fecha, `400 PERIOD_NOT_APPLICABLE` (`periodStart`); una zona que reúne más de `max-rows` registros relacionados, `400 TOO_MANY_RECORDS` | — | F6 |
| `GET` | `/api/sgspe/territory/concentration` | Concentración de eventos por localidad y, sin ella, por comunidad, para el mapa de calor; nunca por punto: un evento sin `locality` ni `community` no cuenta | READ sobre el objeto de `of` (sin él, `403`) y sobre `locality` y `community` | `?of=&project=&periodStart=&periodEnd=&limit=` (`project` puede repetirse) → `200 { available, missingFields?, truncated, of, periodStart, periodEnd, cells[]: { object: locality o community, id, name, point, count } }`; `of`: `engagement`, `grievance`, `inquiry`, `stakeholder_request`, `incident` o `early_warning_signal` (otro, `400 INVALID_OF`); `point` es la ubicación de la localidad (`location` o un punto sobre `boundary`) o un punto sobre el territorio de la comunidad; por defecto, los 12 meses que terminan hoy; el periodo se valida antes que `of` (más de 5 años, `400 OUT_OF_RANGE`); con `engagement` cuenta solo las interacciones realizadas (los estados de KPI-ENG); con `of` en una QCS, `confidentialHidden: true` si quien llama no es `ADMIN` ni tiene `READ_CONFIDENTIAL` sobre los tres objetos (un booleano, nunca el número de excluidas, como los KPI-GRV) | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/nearby-conflicts` | Incidentes, señales de alerta y conflictos locales `ACTIVO` cercanos al área de interés y a los componentes vigentes del proyecto, ordenados por distancia: los incidentes y las señales por su `location` o la de su `locality`, y los conflictos, que no tienen geometría ni proyecto, por `Records` con sus localidades, a la distancia de la más cercana | READ sobre `project` y los objetos listados | `?distanceKm=&limit=` (5 km por defecto) → `200 { available, missingFields?, truncated, items[]: { object: incident, early_warning_signal o local_conflict, id, label, distanceM } }`; más de `max-related` (200) conflictos o localidades de un conflicto, `400 TOO_MANY_RECORDS`; `404 PROJECT_NOT_FOUND`; `409 WORKING_CRS_REQUIRED` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/component-parcels` | Componentes vigentes (no `superseded`) que cruzan predios: el predio, sus titulares y la longitud del acceso o el área de la huella sobre él | READ sobre `project_component`, `land_parcel` y `land_right`; los titulares, según el permiso de campo de `land_right.holder_person` | `?limit=` → `200 { available, missingFields?, truncated, rows[]: { component: { id, code, componentType }, parcel: { id, code, tenureType }, lengthM, areaHa, holders: [{ object: person o organization, id, name }] o null } }`; `lengthM` en los accesos (`trace`) y `areaHa` en las huellas (`footprint`), en el `working_crs`; los titulares son los derechos vigentes del predio (sin fecha de fin o con fin de hoy en adelante, en Lima), sin repetir, y salen de `land_right.holder_person` por `Records`: sin ese campo, `holders: null`, `available: false` y `missingFields: ["land_right.holder_person"]`; `404 PROJECT_NOT_FOUND`; `409 WORKING_CRS_REQUIRED` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/component-communal-land` | Componentes vigentes que cruzan territorio comunal, con la longitud o el área afectada | READ sobre `project_component` y `community` | `?limit=` → `200 { available, missingFields?, truncated, rows[]: { component: { id, code, componentType }, community: { id, name }, lengthM, areaHa } }`, medidos en el `working_crs`; `404 PROJECT_NOT_FOUND`; `409 WORKING_CRS_REQUIRED` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/sensitive-distances` | Distancia de los componentes vigentes a los elementos ambientales sensibles que están a menos de `distanceKm`: `ZONA DE PROTECCION`, `FUENTE DE AGUA`, `SITIO SAGRADO` y `RESTO ARQUEOLOGICO` (no `MICROCUENCA` ni `CERRO`) | READ sobre `project_component` y `environmental_feature` | `?distanceKm=&limit=` (5 km por defecto) → `200 { available, missingFields?, truncated, rows[]: { component: { id, code, componentType }, feature: { id, name, featureType }, distanceM } }`, de la más cercana a la más lejana; `404 PROJECT_NOT_FOUND`; `409 WORKING_CRS_REQUIRED` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/buffer-localities` | Localidades dentro de un buffer de los componentes vigentes, medido en el `working_crs` (`ST_DWithin`) | READ sobre `project_component` y `locality` | `?bufferM=` (500 m por defecto) → `200 { available, missingFields?, localities[]: { id, name, localityType } }` (sin `truncated`: más de `max-rows` localidades, `400 TOO_MANY_RECORDS` sobre `bufferM`); `404 PROJECT_NOT_FOUND`; `409 WORKING_CRS_REQUIRED` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/concession-territories` | Comunidades, sectores comunales y caseríos sobre los que están las concesiones del proyecto, con el área de cruce | READ sobre `mining_concession`, `community`, `community_sector` y `locality` | `?limit=` → `200 { available, missingFields?, truncated, rows[]: { concession: { id, code, name }, object: community, community_sector o locality, id, name, localityType, areaHa } }`; `areaHa` en la zona UTM del centroide, `null` para un punto; más de `max-related` (200) concesiones del proyecto, `400 TOO_MANY_RECORDS`; `404 PROJECT_NOT_FOUND` | — | F6 |
| `GET` | `/api/sgspe/territory/locate` | Punto en polígono: propone la unidad administrativa, la localidad y la comunidad que contienen un punto pulsado en el mapa, para que la persona confirme en el formulario. Si caen varias: la unidad de nivel más fino (DISTRITO, PROVINCIA, DEPARTAMENTO) y, a igual nivel, la primera por nombre e id; la localidad (por su `boundary`) y la comunidad (por su `territory`), la primera por nombre e id | READ sobre los objetos propuestos | `?lon=&lat=` → `200 { administrativeUnit: { id, name, level } o null, locality: { id, name, localityType } o null, community: { id, name } o null, unavailable: [objeto] }`; lo que quien llama no puede leer sale `null` y su objeto va en `unavailable` (solo nombres de objeto); `lon` de -180 a 180 y `lat` de -90 a 90 (`400 OUT_OF_RANGE`); uno que falta o no es un número, `400 INVALID_VALUE` | — | F6 |
| `GET` | `/api/sgspe/territory/localities/{id}/card` | Parte territorial de la ficha contextual de una localidad: su identificación, su geometría y sus relaciones territoriales (unidad administrativa, comunidad, sectores y áreas de influencia). Las cifras de la ficha no salen de aquí (ver la nota) | READ sobre `locality` y los objetos relacionados | `?project=` → `200 { locality, geometry: { location, boundary }, administrativeUnit, communities[]: { id, name }, areasOfInfluence[]: { id, project, areaType, versionNumber, memberCondition }, unavailable[] }`; `locality` es el registro como lo devuelve la API genérica; `unavailable[]` mezcla nombres de objeto y rutas `objeto.campo`; `404 LOCALITY_NOT_FOUND` (`id`); `project` que no es un UUID o repetido, `400 INVALID_VALUE` | — | F6 |
| `GET` | `/api/sgspe/territory/communities/{id}/card` | La misma parte territorial para una comunidad, con sus sectores comunales | READ sobre `community` y los objetos relacionados | `?project=` → `200 { community, geometry: { territory }, administrativeUnit, sectors[]: { id, name }, areasOfInfluence[], unavailable[] }`; `community` es el registro como lo devuelve la API genérica; `unavailable[]` mezcla nombres de objeto y rutas `objeto.campo`; `404 COMMUNITY_NOT_FOUND` (`id`); `project` que no es un UUID o repetido, `400 INVALID_VALUE` | — | F6 |
| `GET` | `/api/sgspe/territory/layers/{layer}` | Capa temática como GeoJSON autenticado; los grupos de interés salen agregados por localidad, nunca como puntos de personas | READ sobre los objetos de la capa | `?project=&periodStart=&periodEnd=&bbox=` → FeatureCollection (en las de gestión, además `available`, `missingFields?` y `periodNotApplied?`) | — | F2 (básicas, LYR-01 a LYR-16), F6 (gestión, LYR-17 a LYR-24) |
| `GET` | `/api/sgspe/map/settings` | Mapa base de la organización de quien llama, con la forma de `BasemapSpec` de `@wasichai/gis`; el portal lo aplica a `gisModule` después del login y a sus mapas por la prop `basemap` (ASM-125) | Autenticado | → `200 { basemap: { type: "style", url } o { type: "none" }, attribution }`; `url` sale de `sgspe.map.base-style-url-by-organization` (por el slug de la organización) o, si no, de `sgspe.map.base-style-url`; sin estilo, `{ type: "none" }`; `attribution` es el texto de `sgspe.map.attribution` para esa URL, o `null`; un parámetro de consulta es `422` | — | F6 |
| `GET` | `/api/sgspe/territory/wms` | Proxy WMS autenticado hacia GeoServer, solo con imágenes de las capas públicas de la lista blanca: las publicadas de la organización del token que están en `model/layers.json`, con el nombre `<tabla física>__<columna>` (ASM-121) | Token de wasichai (`401` sin él) y READ sobre el objeto de cada capa pedida; una capa fuera de la lista blanca o sin READ, `403` sin código | `REQUEST=GetMap` y `GetLegendGraphic` → la imagen (`FORMAT` `image/png` o `image/jpeg`, `WIDTH` y `HEIGHT` hasta 2048, `STYLES` solo los de `layers.json`); `GetCapabilities` → XML que arma SGSPE con la lista blanca, nunca el de GeoServer (las capas publicadas salen de la REST de GeoServer con una caché de 60 s), con `Cache-Control: private, no-store`; un parámetro obligatorio que falta o un `BBOX` mal formado, `400` de validación; `GetFeatureInfo`, `DescribeLayer` u otro servicio, `422 WMS_REQUEST_NOT_ALLOWED`; `SLD`, `SLD_BODY`, `CQL_FILTER`, `FILTER`, `FEATUREID`, `ENV`, `VIEWPARAMS`, cualquier otro parámetro, uno repetido o un valor fuera de la lista blanca, `422 WMS_PARAMETER_NOT_ALLOWED` (el parámetro); el orden es primero la sintaxis de la petición (`400`/`422`), luego la lista de capas publicadas (`503` si GeoServer no responde) y después cada capa pedida contra la lista blanca (`403`), así que con la lista vacía toda capa es `403`; GeoServer caído o vencido (`sgspe.territory.wms.timeout`, 5 s), una respuesta que no es una imagen o que no se puede leer, o la caché vencida sin GeoServer, `503 GEOSERVER_UNAVAILABLE` (`wms`); de GeoServer solo se copia `Content-Type`, y la imagen lleva `Cache-Control: private, max-age=300` | — | F6 |
| `POST` | `/api/sgspe/territory/geometry-checks` | Valida una geometría antes de guardarla: el tipo contra el `geometryType` del campo, el motivo de invalidez en español (`ST_IsValidReason`), la reparación propuesta (`ST_MakeValid`) con su cambio de área en el CRS de cálculo, un MultiPolygon de una parte normalizado y la zona UTM y la región esperadas (`gis.md` §4.3); no escribe | READ sobre el objeto (sin él, `403`) | `{ geometry, object, field, project? }` → `200 { valid, reason, typeMatches, normalized, repaired, areaHaBefore, areaHaAfter, zone: { srid, outOfZone, outOfRegion } }`; `outOfZone` compara con el `working_crs` de `project`; `object` o `field` que faltan, un `field` que no es una geometría del objeto, un `geometry` que no es GeoJSON o un `project` que no es un UUID, `400` de validación sobre esa propiedad; un `project` que no existe o que quien llama no ve, `404`, y sin READ sobre `project`, `403` (los de wasichai, sin código); una propiedad desconocida, `400` | — | F6 |
| `GET` | `/api/sgspe/territory/data-quality` | Listado de calidad por `SpatialGate`, una revisión de `gis.md` §8 por pedido: `INVALID` (geometría inválida), `OUT_OF_ZONE` (fuera de la zona UTM esperada), `STALE_AREA` (área guardada que no coincide con la calculada), `NO_TERRITORY` (QCS, compromisos, riesgos e incidentes sin `locality`, `community` ni `location`) y `NO_GEOMETRY` (localidades y comunidades sin geometría) | READ sobre cada objeto listado y los campos de la revisión | `?check=&object=&project=&page=&size=` → `200 { check, content: [{ object, id, label, field, detail }], page, size, hasNext }`; `check` es obligatorio (otro o ninguno, `400` sobre `check`); `object` debe ser uno de los que cubre la revisión (si no, `400`); sin `object`, entran los objetos que la organización tiene y que quien llama lee con todos los campos de la revisión, ordenados por objeto e id; un `object` pedido que no lee, `403`; las revisiones que comparan con la zona del proyecto (`OUT_OF_ZONE` de los objetos que tienen proyecto y `STALE_AREA` de `area_of_influence`) exigen además leer `project.working_crs`: sin él, esos objetos se omiten del listado, y pedir uno en `object` es `403`; con `project`, solo entran los objetos que tienen proyecto (componentes, áreas de influencia, QCS, compromisos, riesgos e incidentes) y el proyecto mismo, y los del tenant se omiten (un `object` del tenant con `project` responde una página vacía); `project` que no es un UUID, `400` de validación sobre `project`; `page` desde 0 y `size` de 1 a 200 (50 por defecto), fuera de rango `400 OUT_OF_RANGE` | — | F6 |
| `POST` | `/api/sgspe/territory/exports` | Exporta los registros de un objeto de las capas LYR-01 a LYR-16 con su geometría, en UTM o en latitud y longitud, con el nivel de divulgación elegido, y la registra en `export_log` antes de entregar el archivo (ASM-124; ver la nota) | READ y acción declarada `EXPORT` sobre el objeto; CREATE sobre `export_log`; los tres antes de leer nada (sin alguno, `403` sin código) | `{ object, geometry?, format, coordinates?, filters: { project?, componentType?, tenureType? }, disclosureLevel, checklistConfirmed? }` → `200` archivo en streaming (`geojson`, `csv`, `xlsx`, `kml`, `shp` como `.zip` o `gpkg`) con `Content-Disposition` `<objeto>-<nivel>.<ext>`; `coordinates` es `UTM` o `LATLON` (por defecto, `LATLON`); `400 OBJECT_NOT_EXPORTABLE` (`object`), `INVALID_FORMAT` (`format`), `INVALID_COORDINATES` (`coordinates`: otro valor, o `UTM` con `geojson` o `kml`, que salen siempre en EPSG 4326) e `INVALID_GEOMETRY` (`geometry`); `403 DISCLOSURE_NOT_ALLOWED`; `422 FORMAT_UNAVAILABLE` (`format`) para `kml`, `shp` o `gpkg` sin GDAL o sin su driver; `500 EXPORT_FAILED` (`format`: GDAL no pudo escribir el archivo; la respuesta no trae rutas ni la salida del proceso); `503 EXPORT_TIMEOUT` (`format`); y los de las exportaciones de F2: `400 INVALID_DISCLOSURE_LEVEL`, `CHECKLIST_REQUIRED`, `INVALID_FILTER`, `TOO_MANY_RECORDS` y `403 NOT_ASSIGNED` | — | F6 |

- `{layer}` es el identificador `LYR-NN` que fija `gis.md`; el mapa base y las capas de referencia no pasan por aquí
  (RQ-GIS-012, RQ-GIS-013).
- **Capas básicas de F2 (LYR-01 a LYR-16).** Una capa fuera de LYR-01 a LYR-24 es `404` `LAYER_NOT_FOUND`. La respuesta es `{ type: "FeatureCollection", features[], truncated }` con la forma
  de wasichai-gis: cada elemento tiene `id` (`<registro>:<geometría>`), `geometry` y `properties` con los atributos que
  quien pide puede leer más `__id`, `__object`, `__layer` y `__state` (el estado del workflow, o `null`); en LYR-07 las
  propiedades traen además `ofProject`, que es verdadero si la concesión está vinculada a alguno de los proyectos de
  `project`. La capa tiene un tope de 5000 elementos (`sgspe.territory.layers.max-features`): `truncated` es `true` solo
  si había más, y entonces se pide por `bbox`. Sin READ sobre alguno de los objetos de la capa, `403`. Los filtros de la
  capa se aplican en SQL: un campo de filtro que quien pide no puede leer deja la capa vacía, no es un error.
  - **LYR-13** trae, además del polígono de cada área de influencia en `approved` (`__object`: `area_of_influence`), sus miembros
    (`gis.md` §2.2 y §2.4): un elemento por `area_of_influence_member` con `__object`: `area_of_influence_member`, `__id` del
    miembro, `__target` y `__targetId` (`locality` o `community` y el id de lo que se dibuja), el id `<miembro>:<geometría>` y
    sus atributos legibles (entre ellos `area_of_influence`, `member_condition` y `rationale`). La geometría es el límite de la localidad,
    o su ubicación si no tiene límite, o el territorio de la comunidad, aunque el área no tenga polígono; un miembro cuyo destino
    no tiene geometría, o cuyo campo de relación quien pide no puede leer, no sale. Solo cuentan las versiones `approved`: un borrador o
    una versión reemplazada no aportan ni polígono ni miembros. Las unidades administrativas que son miembros no se dibujan
    (sus límites son LYR-01). Sin READ sobre `area_of_influence_member`, `locality` o `community`, `403`.
  - `project` (puede repetirse, hasta 50) acota las capas de proyecto (LYR-09 a LYR-13) y marca `ofProject` en LYR-07, que muestra todas las
    concesiones; en las demás se acepta y se ignora, porque sus registros no pertenecen a un solo proyecto (`gis.md` §3.3). `periodStart` y `periodEnd` se validan (`400 INVALID_VALUE` u `OUT_OF_RANGE`, a lo sumo 5 años) y no filtran estas capas, que no tienen fecha: con un periodo, la respuesta nombra sus objetos en `periodNotApplied`.
  - `bbox=minX,minY,maxX,maxY` en EPSG:4326 es tolerante con lo que manda un mapa alejado: se quitan los espacios, las
    longitudes se recortan a [-180, 180] y las latitudes a [-90, 90] (`-425,-20,275,10` es `-180,-20,180,10`), y si lo
    recortado cubre todo el globo no se filtra. Un mínimo mayor que el máximo después de recortar, o algo que no son cuatro
    números, es `400` `INVALID_BBOX`. Recortar no es envolver: un bbox que cruza el antimeridiano (`170,-10,190,10`) pierde
    el tramo que pasa de 180 y no se parte en dos (irrelevante para el Perú, de -81 a -68). En LYR-13, `bbox` acota los miembros
    por la geometría que se dibuja.
  - Errores (`errors[0].message` lleva el código y `errors[0].field` el campo): `404` `LAYER_NOT_FOUND` (`layer`), `400`
    `INVALID_BBOX` (`bbox`), `400` `INVALID_PROJECT` (`project` no es un UUID) y `400` `TOO_MANY_PROJECTS` (más de 50
    proyectos); un parámetro desconocido es `422`.
- **Capas de gestión de F6 (LYR-17 a LYR-24, plan 6.2c).** Misma ruta y mismos parámetros. La respuesta suma `available` y,
  si falta leer un campo o un objeto secundario, `missingFields` (la capa trae lo que sí se leyó, nunca un cero), y
  `periodNotApplied` con los objetos sin fecha cuando vino un periodo (`stakeholder` en LYR-17 y `social_risk` en LYR-21),
  que no se acotan. Cada registro se dibuja con su geometría (id `<registro>:<geometría>`) o, sin ella, en el punto de la
  primera relación que tenga geometría: la ubicación de su localidad o un punto sobre su límite, un punto sobre el territorio
  de su comunidad y, en LYR-21, sobre el límite de su área de influencia. Ese elemento lleva id `<registro>:<relación>` y
  trae `__target` y `__targetId`; sin nada que dibujar, el registro no sale. LYR-17 y los aportes de LYR-23 salen por
  localidad, nunca por persona: `__object: locality`, `__aggregate` (`stakeholder` o `social_contribution`), `name` y
  `locality_type`. LYR-17 trae `total` y `byStance` (por postura, `SIN DATO` si falta), que es `null` con
  `missingFields: ["stakeholder.stance"]` para quien no lee la postura. Los aportes traen
  `contributions[]: { currency, count, totalAmount }`: los `delivered` y `closed`, por moneda sin mezclarlas y, con periodo,
  los entregados en él. LYR-24 no trae el descriptor del archivo y marca `__downloadRestricted` (CONFIDENCIAL, DATOS
  PERSONALES o sin clasificación legible: la descarga exige `DOWNLOAD_RESTRICTED`). Las fechas del periodo son `held_at`, el
  tramo `actual_start_date` a `actual_end_date` de las visitas, `observed_at`, `agreed_date`, `received_at`, `occurred_at`,
  `detected_at`, el tramo `start_date` a `end_date` de las iniciativas, `delivery_date` y `captured_at`; un tramo cuenta si
  cruza el periodo; con periodo, una visita sin `actual_start_date` o una iniciativa sin `start_date` no sale. Con solo
  `periodEnd`, el periodo son los 12 meses que terminan en esa fecha; con solo `periodStart`, llega hasta hoy. Se leen como quien pide, con sus alcances: una QCS confidencial solo sale a quien la lee, y LYR-20
  lleva `confidentialHidden: true` cuando a quien pide se le ocultan (`dashboards.md` §8). Comparten el
  tope de 5000 elementos con `truncated`. Sin READ sobre un objeto de la capa o sobre los destinos de sus relaciones, `403`.
  No se exportan (ASM-124).
- **La ficha contextual la arman dos paquetes.** `sgspe.territory` solo aporta la geometría y los filtros espaciales del
  territorio elegido (su identificación y las zonas que lo contienen). Todas sus cifras (grupos de interés por postura,
  interacciones recientes, compromisos abiertos y vencidos, QCS abiertas, riesgos altos, programas, inversión y última
  actividad) son KPI-TER-01, que calcula `sgspe.dashboard` y que el portal pide a `GET /api/sgspe/dashboards/indicators/{code}`
  con el filtro `locality` o `community` para mostrarlo en el mismo panel (RQ-GIS-005, RQ-DSH-034). Así la ficha da las mismas
  cifras que los tableros, y la dependencia va de `sgspe.dashboard` hacia `sgspe.territory`, nunca al revés (`dashboards.md`).
  La postura sale como no disponible para quien no puede leer `stance` (RQ-DSH-032).
- **La importación no deja estado en el servidor.** No es un objeto (ASM-060): la vista previa devuelve el `importBatchId` y el
  SHA-256 del archivo, y la confirmación vuelve a mandar el mismo archivo, que el servidor valida otra vez. Así funciona igual
  con varias réplicas y sin tablas intermedias; cada registro importado guarda el lote en `import_batch_id` (RQ-GIS-008).
- La conciliación compara por código: un código nuevo se crea, uno sin desplazamiento queda igual y uno con coordenadas
  distintas genera en `project_component` una versión con su distancia, nunca una sobrescritura (RQ-GIS-010, CON-023); en
  predios y concesiones y, desde F6, en comunidades y elementos ambientales (ASM-123), es una actualización con motivo.
  Una comunidad o un elemento ambiental sin `code` en el archivo es una fila rechazada: nunca se concilia por nombre.
- El CRS de origen es obligatorio, sin valor por defecto silencioso; PSAD56 se acepta solo como origen y se transforma con
  PostGIS (RQ-GIS-003, RQ-GIS-007, CON-024).
- **Entre la vista previa y la confirmación puede cambiar lo registrado.** Sin estado en el servidor, la confirmación
  concilia de nuevo al llegar, y solo detecta (`409 IMPORT_CONFLICT`) lo que cambia entre su propio análisis y su
  candado. Si otra persona registra un código después de la vista previa, una fila vista como `CREATE` se confirma como
  `UPDATE` (en un predio o una concesión, reemplaza su contorno) o como `NEW_VERSION` (en un componente, que nunca se
  sobrescribe). Queda auditada con el motivo `importación <id>` y aparece en `updated` o `newVersions` de la respuesta,
  que el portal compara con la vista previa.
- **`importBatchId` lo elige el cliente.** La vista previa propone uno al azar, pero el servidor no lo guarda ni lo
  verifica: la confirmación usa el de la ruta solo como procedencia (`import_batch_id`) y en el motivo
  `importación <id>`. Dos confirmaciones con el mismo id comparten procedencia en la auditoría; el portal manda el de su
  vista previa. Desde F3 un lote tiene a lo sumo una evidencia con su original: una confirmación que escribiría registros
  con un lote que ya la tiene es `409 IMPORT_BATCH_USED`. La confirmación toma un candado por lote antes del de su
  destino, así que dos confirmaciones del mismo lote, también a otro destino o a otro proyecto, quedan en fila. La
  evidencia del lote se busca como la lee quien confirma: otra persona que reusa un lote ajeno cuya evidencia no lee (de
  un proyecto que no es suyo) puede dejar una segunda; el portal siempre usa el lote de su vista previa.
- **El reporte XLSX** se pide con `Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,
  application/problem+json`. Sin `application/problem+json`, un error (400, 403 o 409) sale con su estado y el cuerpo
  vacío, sin `errors[]`.
- **Conteos de la confirmación.** `rejected` suma las filas inválidas, las descartadas con `DISCARD` (también una
  `UNCHANGED`) y las de `outOfZone[]` sin `APPLY`; `unchanged`, las `UNCHANGED` que se aplicaron, que no escriben nada.
- **Importación con GDAL (F6, ASM-127).** Shapefile (ZIP con `.shp`, `.shx` y `.dbf`), KML, KMZ (que SGSPE descomprime
  y GDAL lee como KML), GeoPackage, FileGDB (ZIP con una carpeta `.gdb`) y GPX entran al mismo flujo de F2: GDAL pasa la
  capa elegida a GeoJSON sin reproyectar y la transformación la hace PostGIS desde el `sourceCrs` confirmado. Solo
  cuentan las capas con elementos: un archivo sin ninguna es `400` sobre `file`, y uno con varias pide `layer`
  (`400 LAYER_REQUIRED`). El CRS que GDAL detecta (`detectedCrs`) es solo una propuesta: la persona confirma
  `sourceCrs` del catálogo (CON-024), y por eso una vista previa de un formato de GDAL sin `sourceCrs` responde `400`
  con las capas del archivo y el CRS detectado de cada una, para que el portal lo proponga. Sin GDAL, o sin su driver,
  esos formatos son `422 FORMAT_UNAVAILABLE` y `GET /api/sgspe/territory/imports/formats` dice cuáles hay.
- **Exportaciones geográficas (F6, ASM-124).** Solo los 13 objetos de las capas LYR-01 a LYR-16 (§3.9); `land_right`
  y los objetos de las capas de gestión son `400 OBJECT_NOT_EXPORTABLE`. La lectura va por `Records` como quien
  exporta (permisos de campo y alcance), con el tope `sgspe.exports.max-rows` (`400 TOO_MANY_RECORDS` sobre
  `filters.project`).
  - La exportación entrega la capa como se ve, en todos los niveles: `project_component` solo con sus versiones
    vigentes (`superseded` no verdadero, como LYR-10 a LYR-12; si quien exporta no lee `superseded`, no sale ninguno) y
    `area_of_influence` solo en `approved`, como LYR-13.
  - Proyectos: `project`, `project_component` y `area_of_influence` se acotan a los de `filters.project` o, sin él, a
    los de quien exporta (la regla de §4.13); `mining_concession`, maestro del tenant (LYR-07), se acota a las
    concesiones vinculadas a los proyectos de `filters.project` solo si llega, y sin él salen todas las que quien
    exporta lee; los demás objetos no se acotan por proyecto. `filters.project` se valida igual en todos (`403
    NOT_ASSIGNED` con un solo proyecto sin asignación vigente), aunque el objeto no se filtre por él.
  - `geometry` es el campo de geometría que se exporta; se puede omitir si el objeto tiene una sola. `geojson` y `kml`
    salen siempre en EPSG 4326; `gpkg` y `shp` (un ZIP con `.cpg` UTF-8), en el CRS de `coordinates`. Con `UTM`, el
    `working_crs` del único proyecto de `filters.project`, que entonces es obligatorio (`400` sobre `filters.project`
    sin él, con más de uno o con un proyecto sin `working_crs`); con `LATLON`, EPSG 4326. `csv` y `xlsx` traen las
    columnas `id`, los atributos que salen en el nivel, `latitude` y `longitude` del punto o del centroide, `utm_zone`,
    `easting` y `northing` (con `LATLON`, en la zona UTM del centroide de cada registro; ASM-064) y `wkt` de las
    líneas y los polígonos en el CRS elegido.
  - `shp` tiene los límites del formato: GDAL recorta los nombres de campo a 10 caracteres y los textos a 254 bytes.
    Para los datos completos, `gpkg`.
  - Divulgación por capa: `PUBLICO`, solo los objetos de las capas públicas (LYR-01 a 05, 07 y 16); `EXTERNO`, además
    `community_sector`, `environmental_feature`, `project` (su ubicación y su área de interés), los accesos de
    `project_component` (`component_type` ACCESO), `area_of_influence` y los predios PROPIEDAD, POSESION o COMUNAL, con
    `checklistConfirmed: true`; `INTERNO`, todos, también sondajes, plataformas, campamentos y los predios de la
    empresa. Un objeto fuera del nivel es `403 DISCLOSURE_NOT_ALLOWED` sobre `disclosureLevel`, y un filtro fuera del
    nivel, sobre `filters.componentType` o `filters.tenureType`. Sin filtro o con uno vacío (`[]`, que cuenta como
    ausente), `EXTERNO` aplica el filtro fijo del objeto (ACCESO, o las tres tenencias) en vez de exigirlo.
  - Fuera de `INTERNO` se quitan `land_parcel.registry_record`, los cuatro campos registrales de `community`
    (`legal_entity_record`, `property_record`, `parent_record` y `title_reference`) y la procedencia
    (`source_reference` e `import_batch_id`), y `project` sale solo con `code` y `name`; `PUBLICO` quita además lo que
    ya quita su regla.
  - Una fila de `export_log` con el objeto, el formato, el nivel, los filtros y el número de registros, escrita como
    quien exporta antes de entregar el archivo. Un archivo que GDAL no termina dentro de su plazo es `503
    EXPORT_TIMEOUT`, y uno que GDAL no pudo escribir, `500 EXPORT_FAILED` sin detalles (la causa queda en el registro
    del servidor); en los dos casos no se entrega nada.
- **WFS, pospuesto (ASM-120).** F6 no sirve `GET /api/sgspe/territory/wfs`: el proxy WMS solo da imágenes de las capas
  públicas, y quien trabaja con QGIS o ArcGIS usa las exportaciones geográficas.
- Casos de uso de `gis.md` y su endpoint: miembros de un área y relación entre proyectos y comunidades,
  `member-suggestions`; grupos de interés, compromisos, QCS y riesgos por zona, `zones/{zoneObject}/{id}/records`;
  concentración de eventos, `concentration`; conflictos cercanos, `nearby-conflicts`; inversiones sociales, su capa en
  `layers/{layer}`; componentes y predios, `component-parcels`; componentes y territorio comunal, `component-communal-land`;
  concesiones y territorios, `concession-territories`; elementos sensibles, `sensitive-distances`; buffer de componentes,
  `buffer-localities`; tamaño del área de influencia, `summary`; punto pulsado, `locate` (RQ-GIS-002, RQ-GIS-005).

```json
{
  "importBatchId": "8c1d2e3f-4a5b-4c6d-8e7f-9a0b1c2d3e4f",
  "sha256": "…",
  "target": "project_component",
  "sourceCrs": "EPSG 32717",
  "rows": 24,
  "valid": 23,
  "invalid": [{ "row": 7, "field": "source_northing", "message": "fuera de la zona UTM esperada" }],
  "differences": [{ "code": "SDJ-004", "currentVersion": 1, "distanceM": 93.4, "outcome": "NEW_VERSION" }],
  "outOfZone": []
}
```

### 4.3 BC-03 Grupos de interés

Paquete `sgspe.stakeholder`. Las evaluaciones son inmutables: una corrección es una evaluación nueva (RF-STK-005).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/stakeholders/{id}/assessments` | Registra una evaluación: valida los indicadores de 1 a 5, calcula índices, grupo de influencia y prioridad, toma la evaluación anterior como línea base, actualiza los valores vigentes del grupo de interés (el último valor informado de cada uno) y, en la primera, lo lleva a `assessed` | CREATE sobre `stakeholder_assessment`, UPDATE sobre `stakeholder` (con acceso a `stance`) y el rol de la transición de WF-B a `assessed` | `{ attributes }` → `201 { assessment, stakeholder, warnings[] }`, con `ETag` del grupo de interés | `IK`, `IM?`, `CR` | F2 |
| `POST` | `/api/sgspe/stakeholders/{id}/assessments/preview` | Calcula índices, grupo y prioridad sin guardar nada, para revisar antes de una escritura inmutable | READ sobre `stakeholder`, `prioritization_rule` y `stakeholder_assessment` (lee el historial para la línea base; sin él, `403` sin código) | `{ attributes }` → `200 { computed, warnings[] }` | — | F2 |

- El cálculo sigue RF-STK-008, RF-STK-009 y RF-STK-011 con los pesos y umbrales del proyecto (ASM-003, ASM-031); el ajuste
  manual del grupo exige `override_reason`.
- Las advertencias de `warnings[]` son `STANCE_DIVERGENCE`, `BACKDATED_ASSESSMENT` (hay evaluaciones posteriores: esta no
  cambia los índices ni el grupo vigentes) y `NO_PRIORITY_RULE` (ninguna fila de la matriz coincide: la prioridad queda vacía).
- Dos evaluaciones cercanas con posturas distintas devuelven la advertencia `STANCE_DIVERGENCE` (CON-006). La línea base de
  la postura es la evaluación anterior más reciente que la informó; una evaluación sin postura o sin confianza no borra el
  valor vigente del grupo de interés.
- El endpoint toma `ClusterLock.withXactLock` sobre el grupo de interés, así que dos evaluadores simultáneos no pierden su línea
  base; por eso `If-Match` es opcional.

```json
{
  "attributes": {
    "assessment_date": "2026-05-12",
    "basis": "ENTREVISTA",
    "affect_environment": 4, "affect_livelihood": 3, "affect_property_status": 2,
    "opportunity_work": 4, "opportunity_development": 3, "opportunity_benefits": 2,
    "power_legal_action": 2, "power_credibility": 3, "power_permit_control": 4,
    "power_action_scope": 2, "power_organization_level": 3, "power_convening_capacity": 3,
    "power_network_membership": 2, "power_group_mobilization": 3, "power_other_pressure": 2,
    "stance": "EN CONTRA PERO ABIERTO A ESCUCHAR",
    "open_to_dialogue": true,
    "trust_score": 2
  }
}
```

Con los pesos iguales de ASM-031 y el umbral 3 de ASM-003, la respuesta trae el índice de interés 3,0, el de poder 2,67, la
influencia 5,67 y el grupo PARA MONITOREO:

```json
{
  "assessment": {
    "id": "a7d0c3b2-1e4f-4a6b-9c8d-0e1f2a3b4c5d",
    "createdAt": "2026-05-12T15:04:11Z",
    "updatedAt": "2026-05-12T15:04:11Z",
    "attributes": {
      "interest_index": 3.0, "power_index": 2.67, "influence_score": 5.67,
      "influence_group": "PARA MONITOREO", "stance_changed": true
    },
    "geometries": {}
  },
  "stakeholder": {
    "id": "6f1c2d3e-4b5a-4c6d-8e9f-0a1b2c3d4e5f",
    "updatedAt": "2026-05-12T15:04:11Z",
    "state": "assessed",
    "attributes": { "influence_group": "PARA MONITOREO", "information_priority": "ALTA", "trust_score": 2 }
  },
  "warnings": []
}
```

### 4.4 BC-04 Relacionamiento

Paquete `sgspe.engagement`. Las interacciones, sus participantes y sus preguntas se escriben por la API genérica; el cierre lo
valida `EngagementWriteGuard` y los conteos los mantiene `AttendanceCountsListener`. Quien no tiene `EDIT_ANY` no cambia ni
borra una interacción ajena (`EditAnyGuard`, ASM-069), pero sí le agrega participantes, y el listener actualiza los conteos
de esa interacción como escritura derivada [domain-model §7.2, §7.5].

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/engagement-questions/{id}/referral` | Deriva una pregunta pendiente a una consulta, una solicitud o una queja, creada y vinculada (`origin_question`) en un solo comando | CREATE sobre el objeto destino; READ y UPDATE sobre `engagement_question`, porque escribe `referred_at` como quien deriva: `COMMUNITY_RELATIONS_OFFICER` con su CRU y `GRIEVANCE_MANAGER` con `u`, que solo le deja escribir los derivados (`security.md` §7.4) | `{ target, attributes }` con `target` igual a `inquiry`, `stakeholder_request` o `grievance` → `201 { question, created }` | `IK`, `CR` | F4 |

- **Derivación (F4).** En una transacción, crea la QCS de `target` con `attributes` (sujetos a `AttentionWriteGuard`)
  más `origin_question`, `origin_engagement` (la interacción de la pregunta), el `project` de la interacción, `channel`
  REUNION y `received_at` igual al `held_at` de la interacción, y escribe `referred_at` en la pregunta con la marca
  `SgspeWrite`. `EngagementWriteGuard` cuenta como atendida una pregunta con `referred_at` (RN-ENG-005) sin leer la QCS,
  que puede ser confidencial. Errores: `409 ALREADY_REFERRED` (`record`) si la pregunta ya tiene `referred_at`; `409
  STATE_MISMATCH` (`record`) si ya está respondida (`answered_on_site` verdadero o `answered_at` informado: solo se deriva
  una pregunta pendiente); `409 STATE_MISMATCH` (`engagement`) si la interacción no está entre `held` y
  `minutes_approved`; `400 INVALID_TARGET` (`target`); `403` sin UPDATE sobre `engagement_question` o sin CREATE sobre el
  destino.

### 4.5 BC-05 Compromisos

Paquete `sgspe.commitment`. Aprobar, iniciar, bloquear, registrar el cumplimiento, verificar, cerrar y cancelar son
transiciones de la API genérica, con sus guardas (`CommitmentWriteGuard`, `SupportPolicyGuard`) y su motivo
(RF-COM-002); aprobar por encima del tope de la campaña exige además `APPROVE_OVER_CAP` (RN-INV-003). Los comandos de
esta tabla leen y escriben como quien llama; los que tocan un compromiso toman su candado (`commitment:<id>`), el mismo
de `register_fulfillment`, las cancelaciones y JOB-01.

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/commitments/{id}/reschedules` | Reprograma: crea `commitment_reschedule` con la fecha anterior y la nueva y cambia `due_date`, en una transacción, solo en ejecución; `deadline_status` se recalcula en la misma transacción | CREATE sobre `commitment_reschedule`, UPDATE sobre `commitment` | `{ attributes: { new_due_date, reschedule_reason, agreed_with_stakeholder } }` → `201 { reschedule, commitment }`, con `ETag` del compromiso; fuera de `approved`, `in_progress` o `blocked`, `409 STATE_MISMATCH`; la misma fecha, `400 SAME_DUE_DATE`; un atributo que falta, `400 FIELD_REQUIRED` (`attributes.<campo>`), y un compromiso sin fecha que reprogramar, `400 FIELD_REQUIRED` (`due_date`) | `IK`, `IM`, `CR` | F3 |
| `POST` | `/api/sgspe/commitments/{id}/deliveries` | Solo captura datos: crea la entrega, la vincula con sus evidencias (`evidence_delivery`) y recalcula `executed_amount`, en una transacción; no cambia el estado. El «Acta de entrega» emitida (AUT-03) espera a UPS-23: el acta firmada es una evidencia ACTA DE ENTREGA | CREATE sobre `delivery`, UPDATE sobre `commitment` y, si vincula, sobre `delivery` y `evidence` | `{ attributes, geometries?, evidence?: [<id de evidence>] }` (hasta 50 evidencias; `commitment` lo fija el comando) → `201 { delivery, commitment }`, con `ETag` de la entrega; fuera de ejecución, `409 STATE_MISMATCH`; `delivered_amount` que no es un número, `400 INVALID_NUMBER`, y fuera de cotas, `400 OUT_OF_RANGE` | `IK`, `CR` | F3 |
| `PATCH` | `/api/sgspe/deliveries/{id}` | Corrige los datos de una entrega y recalcula el ejecutado de su compromiso; solo escribe lo que cambia, y el compromiso de la entrega no cambia nunca. Con el compromiso en `fulfilled`, `verified`, `closed` o `cancelled`, `409 DELIVERY_FROZEN`: una entrega que sostiene un cumplimiento se corrige después de `reopen_execution`, y la de un compromiso cancelado ya no cambia. Desde F5 corrige también la entrega de un aporte, sin recalcular ningún ejecutado, mientras el aporte siga en `procured` (si no, `409 DELIVERY_FROZEN` sobre `social_contribution`) y sin `delivered_amount` (`400 FIELD_NOT_ALLOWED`); su `social_contribution` no cambia (ASM-111) | UPDATE sobre `delivery` y sobre su compromiso o, en la de un aporte, sobre `social_contribution` | `{ attributes, geometries? }` → `200 { delivery, commitment }` (la de un aporte, `200 { delivery, contribution }`), con `ETag` de la entrega | `IM`, `CR` | F3 |
| `POST` | `/api/sgspe/commitment-candidates` | Alta humana de un candidato a compromiso (`ai_generated` en falso) | CREATE sobre `commitment_candidate` | `{ attributes }` (sin `ai_generated`, `confidence`, `ai_interaction`, `created_commitment` ni `duplicate_of`: `400`) → `201` registro; un vínculo a un registro de otro proyecto, `409 PROJECT_MISMATCH` | `IK` | F3 |
| `POST` | `/api/sgspe/commitment-candidates/{id}/accept` | Acepta: crea el compromiso en `draft` con los valores que la persona revisó, lo guarda en `created_commitment` y lleva el candidato a `accepted`. El proyecto sale del candidato, y la interacción de origen también si no se manda; un `origin_engagement` en `null` explícito quita el origen heredado | UPDATE sobre `commitment_candidate`, CREATE sobre `commitment` y el rol de `accept`; una persona, nunca una cuenta de servicio (`403 PERSON_REQUIRED`) | `{ attributes }` (los del compromiso, sin `project`) → `201 { commitment, candidate }`, con `ETag` del compromiso; ya decidido, `409 STATE_MISMATCH`; sin el rol, `403 ROLE_REQUIRED`; el compromiso pasa por las reglas de alta (por ejemplo, `400 JUSTIFICATION_REQUIRED` si el origen es un taller); desde el plan 7.2b, un marcador de seudonimización (`[PERSONA]`, `[PERSONA:<id>]`…) en cualquier valor de `attributes`, también dentro de una lista o un objeto, es `400 PSEUDONYM_NOT_ALLOWED` sobre `attributes.<ruta>` antes de escribir | `IK`, `IM`, `CR` | F3 (candidatos de IA en F7) |
| `POST` | `/api/sgspe/commitment-candidates/{id}/duplicate` | Marca el candidato como duplicado de un compromiso del mismo proyecto (`duplicate_of`) | UPDATE sobre `commitment_candidate` y el rol de `mark_duplicate`; una persona | `{ duplicateOf }` → `200 { candidate }`, con `ETag` del candidato; un compromiso que no existe o no se lee, `400 DUPLICATE_NOT_FOUND`; de otro proyecto, `400 DUPLICATE_OTHER_PROJECT`; ya decidido, `409 STATE_MISMATCH` | `IM`, `CR` | F3 |

- **Registrar la entrega no cumple el compromiso.** Después de capturarla, una persona aplica la transición explícita
  `register_fulfillment` de WF-D por la API genérica, con motivo; `CommitmentWriteGuard` la rechaza si no existe una
  entrega con conformidad y su evidencia o si queda una dependencia bloqueante abierta (RN-COM-001, RF-COM-004, ASM-009)
  [domain-model §7.2]. Si el recurso es MATERIAL, la evidencia es el acta de entrega y la foto; si es NO MATERIAL,
  `conformity_given` verdadero, `conformity_by` informado y al menos una evidencia vinculada de tipo ACTA, CARTA u OTRO.
  Solo cuenta la evidencia del mismo proyecto del compromiso: una de otro proyecto, o sin proyecto, no sostiene el
  cumplimiento.
- **Una entrega cumplida o cancelada no cambia.** Su corrección, sus vínculos de evidencia nuevos y sus desvínculos
  responden `409 DELIVERY_FROZEN` mientras el compromiso esté en `fulfilled`, `verified`, `closed` o `cancelled`.
- La fecha de vencimiento de un compromiso aprobado solo cambia con una reprogramación (RF-COM-003): desde `approved`,
  también en `cancelled`, un cambio de `due_date` por la API genérica es un `409 DUE_DATE_RESCHEDULE_ONLY`.
- **Un compromiso y un candidato solo apuntan a registros de su proyecto.** Cada relación hacia un objeto de proyecto
  (`exploration_campaign`, `origin_engagement`, `agreement`, `agreement_clause` y las del candidato) se juzga al crear,
  al cambiar el vínculo y al cambiar el proyecto: otra es un `409 PROJECT_MISMATCH` sobre ese campo. **Límite
  conocido:** la regla mira desde el compromiso; mover el destino a otro proyecto (una campaña, un acuerdo o una
  interacción) no se bloquea y deja el vínculo cruzado.
- **Dependencias** (`commitment_dependency`, RF-COM-004): un compromiso no depende de sí mismo (`400 DEPENDENCY_SELF`)
  ni cierra un ciclo (`409 DEPENDENCY_CYCLE`). La búsqueda recorre hasta 500 compromisos (más es
  `409 DEPENDENCY_GRAPH_TOO_LARGE`). **Límite conocido:** lee como quien escribe, así que no ve las dependencias de
  compromisos que no puede leer, y no se serializa: dos altas simultáneas, `a → b` y `b → a`, pueden pasar las dos.
- Rechazar un candidato es una transición de la API genérica, con motivo; aceptarlo nunca: la transición a `accepted` solo
  llega desde su endpoint (`CommandTransitionGuard`, RF-COM-010).

```http
POST /api/sgspe/commitments/3f2a9c1e-5b7d-4e2f-9a1c-7d6e5f4a3b21/reschedules
Idempotency-Key: 0b8e9d2a-3c1f-4c5e-9f7a-2d6b8e1c4a90
If-Match: "2026-10-07T14:03:11.482913Z"
X-Change-Reason: UTF-8''lluvias%20en%20la%20zona%20de%20entrega
Content-Type: application/json

{ "attributes": { "new_due_date": "2026-12-15", "reschedule_reason": "Lluvias en la zona de entrega", "agreed_with_stakeholder": true } }
```

### 4.6 BC-07 Quejas, consultas y solicitudes

Paquete `sgspe.attention`. Recibir, acusar, evaluar, atender, resolver, verificar y cerrar son transiciones de la API genérica.
Los plazos los calcula `QcsDueDatesListener`, y las fechas de resolución y de cierre, `QcsTransitionDatesListener`. Una
solicitud aceptada la cierra `RequestClosureListener` al cerrarse su compromiso o, desde F5, su aporte; la que tiene su
compromiso o su aporte cancelado o rechazado se cierra a mano con la transición `close_unfulfilled` (RN-GRV-001, ASM-068).
Las tareas automáticas de una QCS confidencial (el acuse de una queja y el escalamiento) las mantiene genéricas, sin
vínculo con la QCS, `ConfidentialTasksListener` (D-1, `security.md` §9.6).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/grievances/{id}/reclassify` | Reclasifica una queja como consulta o solicitud: crea el registro del tipo nuevo, enlazado al original, y cierra el original | CREATE sobre el objeto destino, UPDATE sobre `grievance` y el rol de la transición de cierre | `{ targetType, attributes }` → `201 { original, created }` | `IK`, `IM`, `CR` | F4 |
| `POST` | `/api/sgspe/inquiries/{id}/reclassify` | Reclasifica una consulta como queja o solicitud y cierra la original | CREATE sobre el objeto destino, UPDATE sobre `inquiry` y el rol de la transición de cierre | Igual | `IK`, `IM`, `CR` | F4 |
| `POST` | `/api/sgspe/stakeholder-requests/{id}/reclassify` | Reclasifica una solicitud como queja o consulta y cierra la original | CREATE sobre el objeto destino, UPDATE sobre `stakeholder_request` y el rol de la transición de cierre | Igual | `IK`, `IM`, `CR` | F4 |
| `POST` | `/api/sgspe/stakeholder-requests/{id}/accept` | Acepta la solicitud: crea un compromiso en `draft` o, desde F5, un aporte social, lo vincula (`created_commitment` o `created_contribution`) y lleva la solicitud a `accepted` | UPDATE sobre `stakeholder_request`, CREATE sobre el objeto que crea (`commitment` o `social_contribution`) y el rol de la transición de WF-E3 a `accepted`: `COMMITMENT_MANAGER`, sin CREATE sobre `social_contribution`, acepta solo como compromiso (ASM-117) | `{ creates, response, respondedAt, attributes }`: `creates` igual a `commitment` o `social_contribution`; `response` (texto) y `respondedAt` (fecha y hora) son la respuesta a la solicitud, y `attributes`, los valores del registro que se crea → `201 { request, commitment }` o `201 { request, contribution }`, con `Location` del registro creado | `IK`, `IM`, `CR` | F4 (aporte en F5) |
| `GET` | `/api/sgspe/attention/report` | Reporte unificado de quejas, consultas y solicitudes: totales por tipo, estado, canal y categoría, y la lista | READ sobre los tres objetos; lo que no se puede leer sale como no disponible | `?project=&periodStart=&periodEnd=&type=&channel=&category=&page=&size=` → `200 { totals, byType, confidentialHidden, page }` | — | F4 |

- El registro nuevo de una reclasificación guarda el vínculo `reclassified_from_*` con el original (RF-GRV-007), y el
  original pasa a `closed` con la transición de comando `close_reclassified_*` que fija `workflows.md`: el planteamiento sigue
  vivo en el registro nuevo. Quien reclasifica necesita también el rol de esa transición.
- **La reclasificación conserva la fecha de recepción.** El registro nuevo copia `received_at` del primer registro de la
  cadena, y sus plazos corren desde esa fecha, porque el grupo de interés planteó el caso una sola vez (ASM-072).
- Una QCS anónima no lleva persona ni grupo de interés (`AttentionWriteGuard`, RF-GRV-001).
- **Aceptar una solicitud es responderla.** El endpoint escribe `response` y `responded_at` en la `stakeholder_request`,
  en la misma transacción que la transición a `accepted` y el alta del compromiso o del aporte. Si falta `response` o
  `respondedAt`, `AttentionWriteGuard` rechaza la transición con un `400` que nombra el campo y no se escribe nada. Con
  `responded_at` informado, la solicitud cuenta como absuelta (RF-GRV-004, RQ-DSH-023) (`workflows.md`, WF-E3).
- **Reclasificación, contrato.** En una transacción, crea la QCS de `targetType` copiando los campos comunes (canal,
  `received_at` del primer registro de la cadena, quien recibió, anonimato, confidencialidad, categoría, descripción,
  prioridad, responsable, ubicación, grupo de interés, persona, localidad, comunidad, tema, interacción y pregunta de
  origen) más `reclassified_from_<tipo>`; `attributes` puede completar o cambiar los campos no derivados salvo
  `received_at`, el proyecto, quien recibió (`received_by_user_id` y `received_by_user_email`) y los vínculos de
  reclasificación (`400` sobre `attributes.<campo>`); aplica `close_reclassified_*` al original con el motivo de la
  petición. Errores: `400 INVALID_TARGET` (`targetType`: el mismo tipo, o uno que no es de los tres) y
  `409 STATE_MISMATCH` (`record`: el original no está en un estado con transición de reclasificación).
- **Aceptación, errores.** `400 INVALID_TARGET` (`creates` distinto de `commitment` y `social_contribution`; en un
  tenant que todavía no tiene el modelo de F5, todo lo distinto de `commitment`), `403` sin CREATE sobre el objeto que
  se crea y `409 STATE_MISMATCH` (`record`) fuera de `under_evaluation`.
- **Aceptación como aporte (ASM-117).** Valida `attributes` contra `social_contribution`, sin `project`; en una
  transacción escribe la respuesta, crea el aporte en `requested` con el proyecto de la solicitud, guarda
  `created_contribution` y aplica `accept`. El aporte creado lo «registró» quien aceptó, así que esa persona no lo
  aprueba (P5); sus ítems se cargan después, todavía en `requested`, y el aviso de aprobación sale al crearlo.
  `created_contribution` es un campo de comando: una persona que lo fija, cambia o vacía por la API genérica, también
  con un `PUT` que lo omite, recibe `403 COMMAND_ONLY`. Cerrar el aporte (`close` de WF-P) cierra la solicitud en la
  misma transacción (`RequestClosureListener`, dentro de `ConfidentialRelease`, con el motivo «RN-GRV-001: aporte
  <código> cerrado» y el origen `listener:request-closure`); con el aporte en `rejected` o `cancelled`, la solicitud se
  cierra a mano con `close_unfulfilled`.
- **Reporte unificado.** Lee como quien llama. `totals` y cada fila de `byType` traen `received`, `open`, `closed`,
  `rejected`, `withdrawn`, `absolved` y `overdue`, contados con `AttentionCounting` (cada planteamiento una vez,
  `dashboards.md` §3.3); `byType` suma `byState`, `byChannel` y `byCategory`. `page.items` lleva `type`, `id`, `code`,
  `state`, `channel`, `category`, `severity`, `receivedAt`, `deadlineStatus`, `dueDate` (el plazo que corre en el
  estado), `ownerEmail`, `respondedAt` y `anonymous`: nunca texto libre ni persona. Un tipo que quien llama no lee sale
  `available: false`. `confidentialHidden` es `true` cuando quien llama no es `ADMIN` ni tiene `READ_CONFIDENTIAL` sobre
  los tres objetos: un booleano, nunca el número de excluidas. Tope: `sgspe.exports.max-rows`
  (`400 TOO_MANY_RECORDS`); un valor inválido es `400 INVALID_FILTER` sobre el parámetro (`project` que no es un UUID,
  `type` fuera de las tres QCS, `channel` o `category` fuera de su catálogo, una fecha que no es `AAAA-MM-DD`,
  `periodStart` posterior a `periodEnd`, `page` o `size` fuera de rango, o un parámetro de valor único repetido), y uno
  desconocido es `422` (ASM-054).
- **Confidencialidad (ASM-095).** Una QCS confidencial que quien llama no lee no aparece en ninguna ruta (lista,
  detalle, relacionados, historial, transiciones, features ni agente): wasichai le aplica `ConfidentialAttentionScope`
  (`security.md` §9.6), y quien no la lee tampoco la cambia ni la transiciona. Un vínculo desde otro objeto (tarea,
  evidencia, compromiso o pregunta) deja ver el id, no el registro.
- **Mismo proyecto.** Toda relación de una QCS hacia un registro con alcance de proyecto, también la que escribe un
  comando, debe ser del mismo proyecto: si no, `409 PROJECT_MISMATCH` sobre el campo (`ProjectLinkGuard`).
- **Idempotencia.** La derivación, las reclasificaciones y la aceptación honran `Idempotency-Key` (§3.5).

### 4.7 BC-08 Riesgos sociales

Paquete `sgspe.risk`.

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/social-risks/{id}/assessments` | Revisión del riesgo: valida probabilidad e impacto de 1 a 5, calcula puntaje, nivel y tendencia, crea `risk_assessment` y actualiza los valores vigentes (o los residuales); la primera revisión lleva el riesgo a `assessed` | CREATE sobre `risk_assessment`, UPDATE sobre `social_risk` y el rol de la transición de WF-F a `assessed` | `{ attributes: { assessment_date, probability, impact, residual, notes } }` → `201 { assessment, risk }` | `IK`, `CR` | F4 |

El nivel sale de los umbrales del proyecto (RN-RSK-002, ASM-004). Mitigar, monitorear y cerrar son transiciones
genéricas con `RiskWriteGuard` (RN-RSK-003).

- **Revisiones, contrato (ASM-099).** En una transacción: (1) valida `probability` e `impact` enteros de 1 a 5
  (`400 OUT_OF_RANGE`), `assessment_date` no futura en Lima (`400 OUT_OF_RANGE`) y no anterior a la última revisión del
  mismo tipo, residual o no (`409 ASSESSMENT_OUT_OF_ORDER`); un riesgo `closed` responde `409 STATE_MISMATCH`, y la
  primera revisión no puede ser residual (`409 FIRST_ASSESSMENT_NOT_RESIDUAL`); (2) calcula `score` = P × I y el nivel
  con `RiskLevels`: CRITICO desde `risk_critical_min_score`, ALTO desde `risk_high_min_score`, MEDIO desde
  `risk_medium_min_score` y BAJO por debajo; umbrales inválidos guardados antes de la regla de `ProjectWriteGuard`
  responden `409 RISK_THRESHOLDS_INVALID`; (3) calcula `trend` frente a la revisión anterior del mismo tipo (AUMENTA, SE
  MANTIENE o DISMINUYE según el puntaje; vacía en la primera); (4) crea la `risk_assessment` con quien revisa; (5)
  actualiza los valores vigentes (no residual) o los residuales, y `next_review_date` = `assessment_date` +
  (`review_every_days` o `risk_review_days`) y `review_status` (con la misma función que JOB-01), con la marca; (6) en
  la primera revisión aplica `assess`, una transición de comando, con el motivo de la petición; (7) deja hechas las
  tareas de revisión abiertas del riesgo (`risk_review:<id>:*`, de JOB-01) con `complete_open`, `complete` o `unblock`
  y `complete` según su estado, como quien revisa y con el motivo de la petición (ronda branchfix de 4.2b): quien
  revisa necesita UPDATE sobre `task` y un rol de esas transiciones, y los roles que revisan los tienen; si faltaran, el
  `403` revierte la revisión entera.
- La subida de `risk_level` a ALTO o CRITICO publica `risk_escalated` (`RiskNoticeListener`) y, a CRITICO, dispara
  AUT-11. Un cambio de umbrales del proyecto no recalcula los niveles guardados: los cambia la revisión siguiente.
- `risk_assessment` es `appendOnly` y `apiOnly`: una revisión se corrige con otra, nunca se cambia ni se borra.

### 4.8 BC-10 Inversión social y programas

Paquete `sgspe.investment`. El tope de apoyo y los lineamientos los aplican las guardas al aprobar (RN-INV-003): en
compromisos desde F3 (`SupportPolicyGuard`) y en aportes desde F5 (`InvestmentWriteGuard` con `ContributionCapRule` y
`ContributionGuidelines`, `workflows.md` WF-P).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/social-contributions/{id}/deliveries` | Solo captura datos: crea la única entrega del aporte y sus vínculos de evidencia (ACTA DE RECEPCION, FOTO y COMPROBANTE) en una transacción, con el candado del aporte; no cambia su estado | CREATE sobre `delivery`, UPDATE sobre `social_contribution` y sobre las `evidence` vinculadas | `{ attributes, geometries?, evidence?: [<id de evidence>] }` (sin `delivered_amount`, `commitment` ni `social_contribution`: el comando fija el aporte) → `201 { delivery, contribution }`, con `Location` y `ETag` de la entrega | `IK`, `IM` (del aporte), `CR` | F5 |

- **Entrega, contrato (ASM-111).** En una transacción y con el candado del aporte: (1) el aporte está en `procured`; si
  no, `409 STATE_MISMATCH` sobre `record`; (2) no tiene entrega; si no, `409 DELIVERY_EXISTS` sobre
  `social_contribution`; (3) `attributes` no trae `delivered_amount` (`400 FIELD_NOT_ALLOWED` sobre
  `attributes.delivered_amount`; por la API genérica, la guarda lo da sobre `delivered_amount`), porque el ejecutado de
  un aporte es su `total_amount` (ASM-063), ni `commitment` ni `social_contribution`; (4) crea la `delivery` con
  `social_contribution` y vincula cada evidencia, y `EvidenceWriteGuard` exige que sea del proyecto del aporte; (5) no
  cambia el estado.
- Llevar el aporte a `delivered` es la transición humana `register_delivery` de WF-P, por la API genérica y con motivo:
  `InvestmentWriteGuard` la rechaza sin la entrega con conformidad y sus evidencias ACTA DE RECEPCION, FOTO y
  COMPROBANTE (P8) [domain-model §7.2]. El «Acta de recepción» emitida (AUT-04) espera a UPS-23: el acta firmada es una
  evidencia ACTA DE RECEPCION (ASM-116).
- La entrega se corrige con `PATCH /api/sgspe/deliveries/{id}` (§4.5) mientras el aporte siga en `procured`; desde
  `delivered`, y en `cancelled`, ni ella ni sus vínculos de evidencia cambian (`409 DELIVERY_FROZEN` sobre
  `social_contribution`).
- Aprobar un aporte por encima de un tope que bloquea exige `APPROVE_OVER_CAP`, que tiene `SOCIAL_MANAGER` (RN-INV-003,
  ASM-119). Las reglas de la aprobación (P1 a P9), sus códigos y su orden están en `workflows.md` (WF-P) y en §4.1; las
  del programa (G1 a G4), en WF-G.
- Los ítems del aporte, sus términos, los programas y sus hijos se escriben por la API genérica, con
  `InvestmentWriteGuard`: `ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`, `PROGRAM_MISMATCH` y los rangos de ASM-118 (§3.3).
- **Mismo proyecto.** Las relaciones del aporte hacia `exploration_campaign`, `stakeholder`, `commitment` y `agreement`,
  la del programa hacia `agreement` y los orígenes de una tarea son del mismo proyecto: si no, `409 PROJECT_MISMATCH`
  sobre el campo (`ProjectLinkGuard`). Los temas priorizados son el vínculo `project_priority_topic` del proyecto con
  `topic`, un maestro del tenant, sin esa regla (`/api/objects/project/records/{id}/related/project_priority_topic`).
- **Idempotencia.** El endpoint de entrega honra `Idempotency-Key` (§3.5), como la aceptación.

### 4.9 BC-12 Evidencias y documentos

Paquete `sgspe.evidence`. El archivo es el campo `evidence.file` de wasichai-files (decisión D1, ADR-0014): se sube con
la ruta del módulo y el registro, `apiOnly` e inmutable en su archivo, lo crea el comando de alta; no se borra
(`409 EVIDENCE_NOT_DELETABLE`) y una corrección es una evidencia nueva con `replaces` (RF-DOC-001). Los EXIF los lee el
portal y llegan como `captured_at` y `geometries.location`. Los documentos emitidos de un registro usan la API de
wasichai (§2).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/objects/evidence/files/file` (wasichai-files) | Sube el archivo: tope de 20 MB, tipo detectado por sus bytes contra los 8 `contentTypes` de `evidence.file`, SHA-256; queda sin adjuntar hasta el alta | CREATE sobre `evidence` | multipart `file` → `201 { id, name, contentType, size, sha256 }`; más de 20 MB, `400 FILE_TOO_LARGE` (`file`) | — | F3 |
| `POST` | `/api/sgspe/evidence` | Registra la evidencia con el archivo subido por la misma persona y la vincula, en una transacción | CREATE sobre `evidence`; UPDATE sobre `evidence` y cada objeto vinculado | `{ attributes: { file: <id>, evidence_type, classification, … }, geometries?, links?: [{ object, recordId }] }` (hasta 50 vínculos) → `201` registro, con `attributes.file` = el descriptor y `ETag`; un archivo que subió otra persona, `400` sobre `file`; un archivo que ya es de otra evidencia, `409 EVIDENCE_FILE_IN_USE` (`file`) | `IK`; `CR` si un vínculo toca un objeto `requiresReason` | F3 |
| `GET` | `/api/sgspe/evidence/{id}/content` | Descarga el archivo en streaming; la ruta nativa `GET /api/objects/evidence/records/{id}/files/{field}` aplica la misma comprobación, también con `HEAD` | READ sobre `evidence`; para CONFIDENCIAL y DATOS PERSONALES, o si quien llama no lee la clasificación, además `DOWNLOAD_RESTRICTED` (`403 ACTION_REQUIRED` sobre `classification`, RF-DOC-003) | → archivo `attachment` con `ETag` igual al SHA-256 | — | F3 |
| `PATCH` | `/api/sgspe/evidence/{id}` | Cambia los datos descriptivos (`title`, `classification`, `document_date`, `author`, `captured_at`, `people_identifiable`, `photo_consent_given`); nunca el archivo; solo escribe lo que cambia | UPDATE sobre `evidence`; `EDIT_ANY` si la subió otra persona; bajar una clasificación restringida exige `DOWNLOAD_RESTRICTED` (`403 ACTION_REQUIRED`, ASM-071), para que reclasificar no abra la descarga | `{ attributes }` → `200` registro, con `ETag`; otro campo, `400 FIELD_NOT_EDITABLE` (`attributes.<campo>`); cambiar la clasificación sin motivo, `400 FIELD_REASON_REQUIRED` (`reason`) | `IM`; `CR` si cambia la clasificación | F3 |
| `POST` | `/api/sgspe/evidence/{id}/links` | Vincula la evidencia con un registro de un destino `evidence_<objeto>` | UPDATE sobre los dos registros; `EDIT_ANY` sobre `evidence` si la subió otra persona (`EditAnyGuard`) | `{ object, recordId }` → `204`; un objeto que no es destino, `400 INVALID_LINK` (`object`); una entrega cumplida o cancelada, o desde F5 la de un aporte fuera de `procured`, `409 DELIVERY_FROZEN`; desde F5 acepta también `social_contribution` y `social_program`, con el proyecto del aporte para una entrega de aporte; desde F4 acepta `grievance`, `inquiry`, `stakeholder_request`, `social_risk`, `incident` y `media_mention`, y una evidencia que no es CONFIDENCIAL ni DATOS PERSONALES no se vincula a una QCS confidencial (`409 EVIDENCE_NOT_RESTRICTED`, `classification`) | `CR` si el destino es `requiresReason` (sin él, `400 REASON_REQUIRED`) | F3 |
| `DELETE` | `/api/sgspe/evidence/{id}/links/{object}/{recordId}` | Desvincula; una entrega de un compromiso cumplido o cancelado, o desde F5 la de un aporte fuera de `procured`, no pierde evidencias (`409 DELIVERY_FROZEN`) | Igual | → `204` | `CR` si el destino es `requiresReason` | F3 |

- Un reintento repite solo el segundo paso, con la misma `Idempotency-Key` y el mismo `file`: el archivo no se vuelve a
  subir.
- **El descriptor de una evidencia restringida llega sin nombre ni huella.** Si la evidencia es CONFIDENCIAL o de DATOS
  PERSONALES, o le falta la clasificación, quien no tiene `DOWNLOAD_RESTRICTED` sobre `evidence` lee el descriptor del
  campo `file` como `{ id, contentType, size }`, sin `name` ni `sha256` (`EvidenceFileReadPolicy`, UPS-24, ASM-093). Rige
  en el registro, la lista, los vínculos (related), las respuestas de escritura, la historia y `/api/audit`; ADMIN lo lee
  entero. La respuesta de la subida (`201`, aún sin registro) no cambia. El portal sigue subiendo las restringidas con un
  nombre neutro (`evidencia-<aaaammdd-hhmmss>.<ext>`) como defensa adicional.
- **Límite conocido:** el alta acepta `replaces` e `import_batch_id` de cualquier persona con CREATE: nada comprueba que
  la evidencia reemplazada sea de quien sube ni que la importación exista (ASM-060). Después del alta no cambian
  (`409 EVIDENCE_IMMUTABLE`).
- Una foto de personas sin consentimiento no sale en una salida EXTERNO (RNF-PRIV-004, RQ-REP-014).

### 4.10 BC-14 Notificaciones

Sin endpoints propios (decisión D2, ADR-0018): la bandeja, el resumen, leer, descartar, posponer, el flujo SSE y las
preferencias son de wasichai-notifications (§2); `@wasichai/*` trae sus componentes. SGSPE publica con las fuentes
`sgspe.approvals` (aprobación pendiente, `ACTION`, clave `approval_pending:<objeto>:<id>`) y `sgspe.deadlines` (por
vencer, `WARNING`; vencido y escalamientos, `ACTION`, claves de `workflows.md`, «Avisos»), siempre a personas concretas
y con un enlace `RECORD` al registro, que la bandeja quita a quien no puede leerlo. Un aviso de una fuente no se edita
ni se borra a mano, y uno `ACTION` no se descarta: se resuelve cuando deja de ser cierto (el envío sale del estado
pendiente; el compromiso o la tarea dejan de estar vencidos).

### 4.11 BC-15 Indicadores y tableros

Paquete `sgspe.dashboard`. Los indicadores se calculan en vivo con `RecordService`, sin vistas materializadas, con el
mismo código que usan los entregables (RQ-DSH-031, RQ-DSH-034). El portal los guarda en caché un minuto. Cada función de
KPI recibe su lector, `KpiReader`: la persona (tableros, cierre de periodo y AI-04) o la plataforma de una organización
(JOB-05); con la plataforma, `Readability` deja leer todo y `SpatialGate` no pide alcances, y
`DashboardDeliverableParityTest` compara las dos cifras (RQ-DSH-034).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `GET` | `/api/sgspe/dashboards/{dashboard}` | Los indicadores de un tablero con los filtros comunes, en una sola llamada por pantalla | READ sobre los objetos de cada indicador; un indicador sin permiso sale con `available: false` | `?project=&periodStart=&periodEnd=&administrativeUnit=&locality=&community=&areaOfInfluence=&explorationCampaign=&scope=&owner=` (desde F5, en `investment`, también `&program=&year=`; en otro tablero, `422`) → `200 { dashboard, filters, computedAt, indicators[] }` | — | Según el tablero |
| `GET` | `/api/sgspe/dashboards/indicators/{code}` | Un indicador con sus desgloses, su serie mensual y su tabla de datos | Igual | Los mismos filtros (con `owner`) más `breakdown` → `200 { code, label, available, value, numerator, denominator, unit, breakdowns[], series[], unavailableReason, missingFields[], warnings[], ignoredFilters[] }` | — | Según el indicador |
| `GET` | `/api/sgspe/dashboards/indicators` | Catálogo de indicadores con su disponibilidad para quien llama, para mostrar u ocultar tableros y zonas | Autenticado | → `200 [{ code, label, dashboard, available }]` | — | F2 |
| `POST` | `/api/sgspe/indicators/{id}/measurements` | Cierra un periodo (`IndicatorMeasurements`): lee el `indicator` como quien llama, calcula el KPI con su `KpiReader` y congela en `indicator_measurement` el valor, el numerador, el denominador y el desglose principal como tabla Markdown. Una cifra completa o ninguna (ASM-136): `409 INDICATOR_NOT_CLOSABLE` con la razón `STANCE` (KPI-STK-02, KPI-STK-05 y KPI-STK-06), `NO_SCALAR_VALUE` (KPI-TER-01, KPI-EXE-03 y los que solo dan series), `UNAVAILABLE` (quien cierra no lo tiene disponible o, en un KPI que lee QCS, no lee la marca `confidential` de los tres objetos) o `NOT_PERIOD_BOUND` (un KPI que no depende del periodo, la foto de hoy, solo se cierra para un mes calendario entero, el pasado o el actual en Lima; en la práctica, el mes pasado, porque el actual aún no terminó y el cierre lo rechaza antes con `400 PERIOD_NOT_ENDED`); `400 PERIOD_NOT_ENDED` si el periodo no terminó (se comprueba primero); `409 MEASUREMENT_EXISTS` si ya se cerró. Los KPI que leen QCS se congelan siempre sin las QCS confidenciales, quien sea que cierre: la cifra oficial las excluye y no revela cuántas son; el tablero no la muestra a quien se le ocultan QCS confidenciales. El desglose principal en Markdown solo se congela si es agregado (lista de permitidos de `Closability`); un desglose por registro (grupos de interés, interacciones, medidas atrasadas, responsables, riesgos) no se congela y la medición lleva solo el valor | CREATE sobre `indicator_measurement`; sin `project` (todo el tenant), además `ADMIN` (`403 ACTION_REQUIRED`; `READ_ALL_PROJECTS` no basta, decisión conservadora del plan 8.2a) | `{ project, attributes: { period_start_date, period_end_date } }` → `201` registro | `IK` | F8 |

Sin `project`, los proyectos por defecto son «mis proyectos» de `ProjectReach`: todos los que lee quien lee todos los
proyectos (`ADMIN` y `READ_ALL_PROJECTS`) y, si no, sus asignaciones vigentes; sin asignaciones, ninguno (la respuesta trae
`filters.project` vacío y los indicadores, conjuntos vacíos). Un proyecto pedido que quien mira no lee da conjuntos vacíos,
sin error. Las excepciones son el tablero `territory` (F6) y, desde F8, el tablero `project`, que exigen `project`.

- `{code}` es el identificador `KPI-*` que define `dashboards.md`; `scope` toma los valores del catálogo "Ámbito del grupo de
  interés" (RQ-DSH-001). `dashboards.md` fija qué tableros e indicadores exigen un solo `project` (el tablero `project` y
  KPI-STK-03). El mapa de calor de riesgos (KPI-RSK-02) acepta varios, pero solo con uno muestra el nivel de cada celda.
- Un tablero aparece en el portal si quien llama puede leer al menos uno de sus indicadores, según el catálogo (RF-IAM-002).
- Un indicador que no se puede calcular para quien llama responde `available: false`, la constante `unavailableReason` y
  `missingFields`, nunca cero (RQ-DSH-032):
  - `FIELD_NOT_READABLE`: falta un campo que el indicador lee; `missingFields` trae `objeto.campo`.
  - `OBJECT_NOT_READABLE`: falta READ sobre un objeto que el indicador lee; `missingFields` trae sus campos.
  - `SINGLE_PROJECT_REQUIRED`: el indicador exige un solo proyecto (KPI-STK-03); `missingFields` es `["project"]`.
  - `TERRITORY_REQUIRED` (F6): KPI-TER-01 sin `locality` ni `community`; `missingFields` es `["locality", "community"]`.
- Forma del indicador: las claves nulas se omiten, salvo `value`, que va siempre en un indicador disponible (`null` si no hay
  cifra, como un porcentaje sin denominador) y nunca en uno no disponible. `unit` es la palabra en español que muestra la
  interfaz (`"compromisos"`), la misma de `dashboards.md`.
- `ignoredFilters` nombra los filtros pedidos que el indicador acepta y no aplica, para que nunca den un cero
  silencioso: los KPI-STK ignoran `community`, porque `stakeholder` no tiene comunidad, y los KPI-STK y KPI-ENG ignoran
  `explorationCampaign` y `owner`, que solo filtran compromisos e inversión social (`dashboards.md` §3.1). Sin filtros
  ignorados, la clave no viaja.
- `owner` (UUID de `owner_user_id`) es el responsable de un compromiso: lo aplican KPI-COM-03, KPI-COM-04, KPI-COM-06 y
  KPI-COM-09 (`dashboards.md` §4.4); los demás indicadores lo devuelven en `ignoredFilters`. `explorationCampaign` lo
  aplican KPI-COM-01 a KPI-COM-05 y KPI-COM-10; los demás KPI-COM y todos los de F2 lo devuelven en `ignoredFilters`.
- El tablero `commitments` (`dashboards.md` §5.6) trae KPI-COM-01 a KPI-COM-11. Desgloses: KPI-COM-01 `workflow_state`;
  KPI-COM-03 `daysOverdue`, con las claves `OVERDUE_FIRST`, `OVERDUE_SECOND` y `OVERDUE_OVER` según los umbrales de
  escalamiento del proyecto de cada compromiso, y la etiqueta `1-<primero>`, `<primero+1>-<segundo>` y
  `más de <segundo>` si todos los compromisos comparten umbrales («Hasta el primer umbral», «Hasta el segundo umbral» y
  «Más del segundo umbral» si no); KPI-COM-07 `owner`, `project`, `territory` y `topic`, con filas
  `{ key, label, value, overdue }`, las 10 de más abiertos y «Otros»; KPI-COM-08 `commitment_theme`; KPI-COM-09
  `against` con `current` y `original` (`value`, `numerator`, `denominator`); KPI-COM-10 `currency` (`value` =
  `committed`, `executed`); KPI-COM-11 `series` `{ period, value }` de 12 meses.
- En el catálogo, `dashboard` es `stakeholders`, `engagement` o `null`: KPI-STK-04, KPI-STK-05 y KPI-STK-07 no tienen zona en
  un tablero de F2 y solo tienen detalle (`dashboards.md` §11).
- La etiqueta de un punto de KPI-STK-03 que es una persona es su nombre si quien llama puede leerlo; si no, «cargo,
  organización» de su cargo vigente o, sin cargo, «Persona». La clave `stance` del punto se omite a quien no lee la postura.
- El contacto de KPI-STK-04, KPI-STK-05 y KPI-STK-07 (`dashboards.md` §3.3) se arma una vez por pedido y lo usan los tres: el
  territorio y el ámbito filtran al grupo de interés vigente, no a la interacción.
- La lectura es acotada (`dashboards.md` §6.2): 20 000 filas por conjunto leído y 200 interacciones cuyas relaciones M2M
  (`engagement_stakeholder`, `engagement_topic`) se leen. Pasarse no recorta la cifra: el pedido entero responde `400`
  `TOO_MANY_RECORDS` y hay que acotar los proyectos, el periodo o el territorio. Un `ADMIN` sin filtros puede recibirlo; el
  portal lo muestra como «acote los filtros». Desde el plan 8.2a-bis, KPI-GRV-02, KPI-ENG-01, KPI-COM-02, KPI-COM-03,
  KPI-RSK-01, KPI-EXE-02 y las series de interacciones y de quejas de KPI-EXE-03 cuentan en la base (`dashboards.md`
  §6.3) y no leen filas, así que no dan `TOO_MANY_RECORDS`. El tablero `executive` todavía puede responderlo por la serie de
  vencidos de KPI-EXE-03 y por KPI-INV-03, que siguen leyendo filas.
- Errores, con el código en `errors[0].message` y el campo en `errors[0].field` (§3.3): `404` `DASHBOARD_NOT_FOUND`
  (`dashboard`: un tablero que no existe o no está en esta fase); `404` `INDICATOR_NOT_FOUND` (`code`); `400`
  `INVALID_BREAKDOWN` (`breakdown`: un desglose que el indicador no ofrece; sobre un indicador no disponible,
  `breakdown` se ignora); `400` `TOO_MANY_RECORDS` (`project`); desde F6, `400` `PROJECT_REQUIRED` (`project`: el
  tablero `territory` sin `project`, y desde F8 el tablero `project` sin un solo proyecto). Un filtro mal formado
  (`project` u otro UUID, una fecha, `periodStart` posterior a `periodEnd`, `scope` fuera del catálogo) es `400` de
  validación que nombra el parámetro, y un parámetro desconocido es `422`.
- El cierre de periodo no congela los indicadores que leen campos restringidos, como los de postura:
  `indicator_measurement` lo leen roles sin acceso a esos campos, y una cifra congelada saldría por la API genérica
  (`dashboards.md` §10). Tampoco congela una cifra incompleta: sin valor escalar, no disponible para quien cierra o con
  `confidentialHidden` (ASM-136); por eso los KPI de QCS los cierra en la práctica `ADMIN`.

- Los tableros `attention` y `risks` (F4) son internos y no aplican celdas pequeñas. La respuesta de `attention`, la de
  cada KPI-GRV y la de KPI-ENG-05 llevan `confidentialHidden: true` cuando quien llama no es `ADMIN` ni tiene
  `READ_CONFIDENTIAL` sobre los tres objetos de QCS; nunca el número de excluidas (`dashboards.md` §8). KPI-ENG-06 a
  KPI-ENG-08 solo se piden por `GET /api/sgspe/dashboards/indicators/{code}` hasta F8.
- El tablero `investment` (F5, `dashboards.md` §4.7 y §5.10) trae KPI-INV-01 a KPI-INV-04 (`InvestmentKpis`) y lee como
  quien llama, así que hereda el alcance; por defecto, «mis proyectos» de `ProjectReach`. Además de `project`,
  `periodStart`, `periodEnd`, `locality` y `explorationCampaign` (la campaña), el tablero y sus indicadores aceptan
  `program` y `year` (`dashboards.md` §3.1; en otro tablero o indicador son `422`); un filtro desconocido es `422`. La
  cifra titular de KPI-INV-01 a 03 va en PEN (`dashboards.md` §3.3) y cada desglose lleva su moneda. KPI-INV-01
  informa por campaña el tope, la moneda, lo comprometido (`CampaignCommitted`), lo ejecutado
(`executed_social_amount`) y los conteos en otra moneda; KPI-INV-02, por programa, año y moneda, lo asignado y lo
ejecutado registrado; KPI-INV-03, por proyecto y moneda, lo ejecutado por fuente frente a presupuesto y topes;
KPI-INV-04, tres bloques que no se suman. Los programas cuentan en KPI-INV-02 a 04 solo desde «Planificado» de WF-G
(`planned`, `executing`, `monitoring`, `evaluation`, `suspended` y `closed`; decisión del usuario, 2026-10-11): sus
presupuestos y sus grupos de beneficiarios en `draft`, `pending_approval`, `cancelled` o cualquier otro estado no se
leen, y un `program` que apunta a uno de ellos da conjuntos vacíos, no un error. Cada fila de KPI-INV-02 lleva el
  `state` del programa. Es interno y no aplica celdas pequeñas.
- El tablero `territory` (F6, `dashboards.md` §4.8) exige `project`: sin él, `400 PROJECT_REQUIRED` (`project`). Trae
  KPI-TER-02 a KPI-TER-04 y, con `locality` o `community`, KPI-TER-01 primero. La dependencia va de `sgspe.dashboard` a
  `sgspe.territory` (KPI-TER-04), nunca al revés.
  - KPI-TER-01 (también por `GET /api/sgspe/dashboards/indicators/KPI-TER-01`, que pide la ficha contextual de §4.2) no
    tiene valor propio: sale disponible con `value: null` y un desglose `by: "components"` con una fila por componente,
    en este orden: `KPI-STK-02`, `KPI-ENG-01`, `KPI-COM-02`, `KPI-COM-03`, `KPI-GRV-02`, `KPI-RSK-01`, `programs`,
    `KPI-INV-03` y `lastActivity`. La fila de un KPI es su indicador entero más `key` (su código); `programs` y
    `lastActivity` llevan `key`, `available`, `value` y, si faltan, `unavailableReason` y `missingFields`. Cada fila
    está disponible o no por su cuenta. KPI-ENG-01 cuenta, sin periodo pedido, los últimos 90 días (de hoy menos 89 días
    a hoy, en Lima), y con `periodStart` o `periodEnd`, el periodo pedido. Con `community`, KPI-STK-02 lo informa en
    `ignoredFilters`. Sin `locality` ni `community`, KPI-TER-01 sale no disponible con `TERRITORY_REQUIRED`.
  - KPI-TER-02 (`ha`): la suma de `calculated_area_ha` de la versión `approved` de cada tipo de área, con el desglose
    `area`. KPI-TER-03 (`miembros`): los miembros de las versiones aprobadas, con los desgloses `areaType`, `memberType`
    y `memberCondition`. KPI-TER-04 (`componentes`): los componentes vigentes que cruzan territorio comunal o predios,
    con los desgloses `tenureType` (con `TERRITORIO COMUNAL`) y `componentType`; es la consulta de `SpatialGate` que
    comparte con `summary` (§4.2) y no se recorta (`400 TOO_MANY_RECORDS`).
- Los tableros de F8 (`dashboards.md` §5.1, §5.2 y §5.5) leen como quien llama, con los filtros comunes:
  - `executive`: las tiles de KPI-EXE-01, KPI-COM-02, KPI-COM-03, KPI-GRV-02, KPI-RSK-01, KPI-ENG-01 y KPI-EXE-02
    (`ExecutiveKpis`), KPI-INV-03 por moneda, KPI-EXE-03 (tres series de 12 meses, sin valor escalar) y `byProject[]`.
    `byProject[]` es `[{ project, code, name, indicators[] }]`, con los 8 KPI con valor de las tiles, sin desgloses ni
    series, con las mismas cifras que el filtro de cada proyecto solo (los KPI contados en la base, en una consulta
    agrupada por proyecto). Pedir más proyectos que
    `sgspe.dashboards.executive.max-projects` (25) es `400 TOO_MANY_RECORDS` (`project`); tenerlos por defecto («mis
    proyectos» o todos) da las tiles sin `byProject[]` y con `byProjectOmitted: true`;
  - `project`: exige un solo `project` (`400 PROJECT_REQUIRED`); el mapa lo arma el portal con las capas de F6;
  - `monitoring` (`MonitoringTable`): la respuesta trae `indicators[]` (los 13 KPI en el orden de `dashboards.md`
    §4.9, con sus desgloses: sexo, ámbito e institución donde la Tabla 9.5 los pide) y `rows[]` en el mismo orden,
    cada una `{ number, code, indicatorId, label, verificationMeans, deliverable, available, value, target,
    targetSource, official }`. `indicatorId` es el id del `indicator` del catálogo con ese código, o `null` si el
    catálogo no lo trae o el lector no lee `indicator`. `targetSource` es `PROJECT`, `CATALOG`, `DEFAULT` o `NONE`, y `official`, `{ measurement, closedAt,
    value }` o `null`. La cifra oficial sale solo sin filtros fuera de `project` y el periodo, con el periodo exacto
    de la medición y un solo `project` (o ninguno, para la del tenant, a quien lee todos los proyectos); #2 y #3
    dicen «no disponible» a quien no lee `stance`, también con cifra oficial, y la de una fila de QCS no sale a quien
    se le ocultan QCS confidenciales. Un tenant sin el
    modelo de F8 (`TenantModel` sin `indicator`) responde sin metas ni cifras oficiales.
- Desde F8, `GET /api/sgspe/dashboards/indicators` informa disponibles KPI-EXE-01 a KPI-EXE-03 y ubica KPI-STK-04,
  KPI-STK-05, KPI-STK-07, KPI-ENG-06, KPI-ENG-07 y KPI-ENG-08 en `monitoring`.

| `{dashboard}` | Tablero | Requisito | Desde |
|---|---|---|---|
| `stakeholders` | Grupos de interés | RF-DSH-007 | F2 |
| `engagement` | Relacionamiento | RF-DSH-009 | F2 |
| `commitments` | Compromisos | RF-DSH-003 | F3 |
| `attention` | Quejas, consultas y solicitudes | RF-DSH-004 | F4 |
| `risks` | Riesgos | RF-DSH-005 | F4 |
| `investment` | Inversión social | RF-DSH-010 | F5 |
| `territory` | Territorial | RF-DSH-006 | F6 |
| `executive` | Ejecutivo | RF-DSH-001 | F8 |
| `project` | Proyecto | RF-DSH-002 | F8 |
| `monitoring` | Monitoreo de la Tabla 9.5 de DOC-01 | RF-DSH-008 | F8 |

```json
{
  "dashboard": "commitments",
  "computedAt": "2026-10-07T15:04:05Z",
  "filters": { "project": ["9b8a7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"], "periodStart": "2026-09-01", "periodEnd": "2026-09-30" },
  "indicators": [
    {
      "code": "KPI-COM-03",
      "label": "Compromisos vencidos",
      "available": true,
      "value": 7,
      "unit": "compromisos",
      "breakdowns": [
        {
          "by": "daysOverdue",
          "rows": [
            { "key": "OVERDUE_FIRST", "label": "1-7", "value": 4 },
            { "key": "OVERDUE_SECOND", "label": "8-15", "value": 3 }
          ]
        }
      ],
      "warnings": []
    },
    {
      "code": "KPI-COM-10",
      "label": "Monto comprometido frente a ejecutado",
      "available": false,
      "unavailableReason": "FIELD_NOT_READABLE",
      "missingFields": ["commitment.amount"]
    }
  ]
}
```

### 4.12 BC-15 Entregables

Paquete `sgspe.deliverable`. Enviar a revisión, observar, aprobar, anular y abrir una revisión nueva son transiciones de
la API genérica; `DeliverableWriteGuard` rechaza el envío a revisión y la aprobación si el borrador no está LISTO
(`409 STATE_MISMATCH` sobre `draft`) o si una sección cambió después de congelarse el modelo (`409 SECTIONS_CHANGED`)
(RQ-REP-015, RQ-REP-026). Las secciones narrativas se editan por la API genérica (RQ-REP-032), y un listener crea las
secciones del tipo en la transacción del alta del entregable.

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/deliverables/{id}/drafts` | Pide un borrador, solo en `draft` u `observed` (si no, `409 STATE_MISMATCH` sobre `record`) y con los parámetros de su tipo (si falta uno, o en T1 el AIDS no está aprobado, `422 DELIVERABLE_PARAMETERS_MISSING`): escribe únicamente `generation_requested_at`; JOB-05 lo toma después | UPDATE sobre `deliverable` y acción declarada `GENERATE` | → `202 { deliverable }` con `Location` del estado | `IK`, `CR` | F8 |
| `GET` | `/api/sgspe/deliverables/{id}/draft` | Estado del último borrador y niveles preparados; de cada candidata solo informa su SHA-256 | READ sobre `deliverable` | → `200 { status, requestedAt, generatedAt, revision, modelSha256, pending, levels[]: { level, candidateSha256 }, warnings[] }` | — | F8 |
| `GET` | `/api/sgspe/deliverables/{id}/draft/content` | Descarga un archivo del último borrador de un nivel, siempre con la marca de agua BORRADOR, y registra la descarga en `export_log` (`export_kind` DESCARGA, con `deliverable`) | READ y `DOWNLOAD_RESTRICTED` sobre `deliverable`; CREATE sobre `export_log` | `?level=` (INTERNO o EXTERNO) y `?format=pdf` o, solo en INTERNO, `?format=docx` → archivo `attachment` con `nosniff`, `no-store` y `ETag` igual al SHA-256; el DOCX de un borrador EXTERNO y `?format=model` son `404`; otro `format`, `422` | — | F8 |
| `POST` | `/api/sgspe/deliverables/{id}/issue` | Emite (`DeliverableIssue`): verifica `approved` y el borrador LISTO (si no, `409 STATE_MISMATCH`), el mismo `draft_model_sha256` que se revisó (`409 MODEL_MISMATCH`), ningún mapa fallido (`409 MAP_FAILED`), ninguna sección cambiada (`409 SECTIONS_CHANGED`, y la persona aplica `return_to_draft` de WF-H y pide un borrador nuevo; `deliverables.md` §8.4) y, si el tipo prepara EXTERNO, la lista confirmada (`400 CHECKLIST_REQUIRED`); sube como la persona una copia de cada candidata y, en una transacción, crea la `deliverable_version` INTERNO (la base) y la EXTERNO (con `derived_from`), apunta a la base, escribe una fila de `export_log` (EMISION) por versión y aplica `issued` al final | UPDATE sobre `deliverable`, CREATE sobre `deliverable_version` y `export_log`, acción declarada `ISSUE` y el rol de la transición de comando de WF-H | `{ draftModelSha256, checklistConfirmed }` → `201 { deliverable, versions[] }` | `IK`, `IM`, `CR` | F8 |
| `GET` | `/api/sgspe/deliverables/{id}/versions/{versionNumber}/content` | Descarga una versión emitida y registra la descarga en `export_log` (DESCARGA, con `deliverable_version`) | INTERNO: READ y `DOWNLOAD_RESTRICTED` sobre `deliverable`. EXTERNO: READ y `EXPORT` sobre `deliverable`. En los dos casos, CREATE sobre `export_log` | `?format=pdf` o, solo en INTERNO, `?format=docx` → archivo `attachment` con `ETag` igual al SHA-256; el DOCX de una versión EXTERNO y `?format=model` son `404`; otro `format`, `422` | — | F8 |
| `POST` | `/api/sgspe/deliverable-templates` | Sube una plantilla Word de la organización y la valida (`TemplateValidator`, ASM-135): sin macros, objetos OLE, ActiveX, controles de contenido ni vínculos externos, con los estilos requeridos, solo los marcadores fijos y solo fuentes de la lista; si pasa, sube el archivo como quien llama y crea el `deliverable_template` | `MANAGE_METADATA` sobre `deliverable`; sin ella, `403` antes de leer el cuerpo | multipart `file` (hasta 20 MB), `templateKey` y `name`, y desde ASM-140 `logo` (PNG o JPEG, hasta 2 MB y 4000 px por lado, opcional) y `accentColor` (`RRGGBB`, opcional) → `201 { id, templateKey, sha256, warnings[] }`; si no pasa, `422 TEMPLATE_INVALID` con `violations[]` y `warnings[]` (`{ code, detail }`; códigos `LOGO_INVALID`, `ACCENT_INVALID`, `MACROS`, `OLE_OBJECT`, `ACTIVEX`, `CONTENT_CONTROL`, `EXTERNAL_RELATIONSHIP`, `EXTERNAL_CONTENT`, `FIELD_NOT_ALLOWED`, `UNKNOWN_PLACEHOLDER`, `MISSING_STYLE`, `FONT_NOT_ALLOWED`, `NOT_WORD`, `TOO_LARGE`; aviso `FONT_IN_TABLE`) | `IK` | F8 |
| `GET` | `/api/sgspe/deliverable-templates` | La plantilla más reciente de cada clave de la organización | READ sobre `deliverable` | → `200 [{ id, templateKey, name, sha256, createdAt, warnings[], hasLogo, accentColor }]` | — | F8 |
| `GET` | `/api/sgspe/deliverable-types` | Los tipos de entregable que registra el backend (`DeliverableTypes`), para que el portal no copie sus secciones (`DeliverableTypeCatalog`): los tipos por nombre y sus secciones por `order`; `assisted` dice si la sección admite AI-04 | READ sobre `deliverable`, como la persona (si no, `403`) | → `200 [{ type, version, sections[]: { key, title, order, kind, required, assisted, guide } }]`; un parámetro de consulta, `422` | — | F8 |

- **Avisos del borrador.** `draft_warnings` lleva un aviso por línea, `CÓDIGO` o `CÓDIGO sección`: `MAP_FAILED`,
  `POSSIBLE_PERSONAL_DATA`, `PDF_CHECK_FAILED` (con el nivel INTERNO o EXTERNO en el lugar de la sección),
  `PDF_CONVERTER_UNAVAILABLE`, `FILE_TOO_LARGE`, `TEMPLATE_UNAVAILABLE` (la plantilla de la clave no existe o su archivo
  no se lee), `TYPE_NOT_AVAILABLE` (el tipo ya no está disponible cuando JOB-05 toma el pedido), `DELIVERABLE_PARAMETERS_MISSING
  area_of_influence` (el AIDS de un T1 dejó de estar aprobado) y `GENERATION_FAILED` (un
  error inesperado). `GET …/draft` los devuelve en
  `warnings[]` como `{ code, section }`, y `levels[].candidateSha256` es el del PDF candidato de cada nivel.
- **Un pedido, un intento.** `draft_generated_at` es el instante en que JOB-05 tomó el pedido (el de congelar el modelo,
  si llegó a hacerlo); se escribe también en FALLIDO, así un FALLIDO no se reintenta sin un pedido nuevo. Un intento
  fallido después de congelar deja su `draft_model_sha256` y consume su letra de revisión: la letra y el SHA-256 del
  modelo se escriben al pasar a EN PROCESO, y el pedido siguiente usa la letra que sigue.
- **Qué se sirve fuera de LISTO.** Los siete archivos solo cambian cuando un intento termina LISTO. Mientras un intento
  nuevo está EN COLA o EN PROCESO, o si terminó FALLIDO, `GET …/draft/content` y las rutas nativas sirven los del último
  borrador LISTO, y `levels[].candidateSha256` de `GET …/draft` son los de ese borrador, mientras `revision`,
  `modelSha256` y `generatedAt` ya pueden ser los del intento nuevo. La fila DESCARGA de `export_log` se escribe después
  de abrir el archivo y nombra lo servido por su SHA-256 (el `ETag`): con LISTO, «borrador <letra> del intento del
  <instante>, archivo con SHA-256 <sha>»; fuera de LISTO, «último borrador LISTO (el borrador está ahora <estado>),
  archivo con SHA-256 <sha>», sin atribuirle la letra del intento nuevo. La de una versión lleva también el SHA-256 del
  archivo servido. Enviar a revisión, aprobar y emitir piden LISTO, así que nunca operan sobre esa mezcla.
- **Un borrador emitido no se vuelve a revisar.** Tras `issue` y `start_revision`, el borrador sigue LISTO con el modelo
  ya emitido: `submit_for_review`, `resubmit`, `approve` y `POST …/issue` responden `409 STATE_MISMATCH` sobre `draft`
  si hay una `deliverable_version` con `issued_at` posterior a `draft_generated_at`. Hace falta pedir un borrador nuevo,
  que reinicia la letra y lleva la revisión de emisión siguiente (RQ-REP-016, ASM-027).
- **La emisión acota y comprueba antes de copiar.** Cada copia se acota a `sgspe.deliverable.max-file-bytes` (`400
  FILE_TOO_LARGE` sobre `file`) y `If-Match` se comprueba antes de copiar nada, así un `If-Match` viejo no deja copias
  huérfanas.
- **`HEAD` no se sirve.** Las rutas de descarga responden `405` a `HEAD`: cada descarga deja su fila de `export_log`.
- **La emisión no genera nada.** El trabajador, en segundo plano, prepara desde el mismo modelo congelado el borrador y
  la versión candidata sin marca de agua de cada nivel de divulgación del tipo, con los mapas de GeoServer ya
  incrustados: DOCX y PDF/A-2b en INTERNO, y solo PDF/A-2b en EXTERNO, en los siete campos `FILE` del borrador
  (ASM-133). El DOCX sale con Apache POI y el PDF se compone con LuaLaTeX en `sgspe-latex` desde el mismo modelo, no
  desde el DOCX (ASM-139). Emitir solo copia y registra. Por eso la petición no llama a `sgspe-latex` ni a GeoServer, y
  reemitir nunca lee datos vivos (RQ-REP-026, ASM-014) (`deliverables.md`).
- **Nada sin marca antes de emitir.** Ninguna ruta sirve una versión candidata: todo lo no emitido lleva la marca BORRADOR
  (RQ-REP-021). Se revisa el borrador, que tiene el mismo contenido, y de la candidata solo se muestra su SHA-256.
- **Solo PDF fuera de INTERNO.** Los borradores y las versiones EXTERNO se sirven solo en PDF, y pedir su DOCX responde
  `404`; los de INTERNO tienen DOCX y PDF.
- **El modelo congelado no se sirve.** El modelo de un borrador (`draft_model`) o de una versión (`model`) es de uso
  interno: sirve para volver a generar y para verificar los SHA-256, y ninguna ruta lo entrega, tampoco la nativa de
  wasichai-files: `?format=model` nombra un archivo que existe pero no se sirve, así que responde `404` y no el `422` de
  un valor desconocido. Es el modelo previo a la divulgación; si algún día se sirviera, exigiría READ y
  `DOWNLOAD_RESTRICTED` en todo nivel.
- **Rutas nativas de archivos (ASM-133).** SGSPE atiende las de `deliverable`, `deliverable_version` y
  `deliverable_template`, como las de `evidence` (§4.9): `GET /api/objects/deliverable/records/{id}/files/{field}` de
  un borrador aplica las comprobaciones de `GET …/draft/content` y escribe su fila de `export_log`, y la de una
  candidata o de `draft_model` responde `404`; la de `deliverable_version` aplica las de `GET …/versions/{n}/content`,
  y `model` responde `404`; la de `deliverable_template` queda como la de wasichai (READ). La subida y el reemplazo
  (`POST /api/objects/{deliverable|deliverable_version|deliverable_template}/files/{field}` y
  `…/records/{id}/files/{field}`) responden `403 FILE_WRITE_NOT_ALLOWED`. Las descargas (`DeliverableContent`)
  responden `attachment`, `nosniff`, `no-store` y `ETag` igual al SHA-256, como `EvidenceContent`.
- **Qué rechaza `TemplateValidator`.** Falla cerrada y por listas: nada de lo que el archivo declara de sí mismo decide qué
  se revisa. Toda parte del paquete se analiza como XML sin DTD, cualquiera sea su tipo declarado o su carpeta; un binario
  solo se acepta si se declara imagen raster (png, jpeg, gif, bmp, tiff, emf, wmf) y sus bytes mágicos coinciden; SVG no
  se permite (usar PNG) y lo demás (por ejemplo `printerSettings.bin` o fuentes `.odttf`) es `NOT_WORD`, igual que un
  elemento con dos atributos del mismo nombre local. Los campos permitidos son una lista cerrada (PAGE, NUMPAGES,
  SECTIONPAGES, SECTION, DATE, TIME, CREATEDATE, SAVEDATE, PRINTDATE, TITLE, SUBJECT, AUTHOR, DOCPROPERTY, STYLEREF, REF,
  PAGEREF, NOTEREF, SEQ, TOC y TC); cualquier otro (MERGEFIELD, INCLUDEPICTURE, LINK, DDE, DATABASE, RD…), un campo sin
  nombre, un campo anidado antes del nombre, texto de instrucción fuera de un campo o un campo sin cerrar es
  `FIELD_NOT_ALLOWED`. Un atributo cuyo valor parece una dirección (`esquema:`, `//`, `\\`) es `EXTERNAL_RELATIONSHIP`,
  salvo las declaraciones de namespace, `xsi:schemaLocation`, `noNamespaceSchemaLocation`, `xsi:type`, `Relationship/@Type`,
  `graphicData/@uri`, `schemaRef/@uri`, `ext/@uri` y el texto alternativo `descr` o `title` de `docPr` y `cNvPr`; y toda
  relación con `TargetMode="External"` también lo es (incluye los hipervínculos externos). Los estilos requeridos se
  buscan solo en la parte de estilos del documento principal; las fuentes, en todas las partes.
- `GENERATE`, `APPROVE`, `ISSUE` y `DOWNLOAD_RESTRICTED` sobre `deliverable` se conceden solo a roles que pueden leer los
  campos sensibles, porque el contenido de un entregable no se filtra por permisos de campo (GAP-024); los roles concretos
  están en `security.md`.
- Ningún trabajo aplica la transición a `issued`: ocurre en la petición de la persona que emite [domain-model §8.3].
- Si el tipo prepara el nivel EXTERNO, emitir exige `checklistConfirmed: true` (RQ-REP-033); sin ella, `400
  CHECKLIST_REQUIRED`, como en las exportaciones, y la confirmación queda en `filters_description` del `export_log` de
  la emisión. Los niveles toman los valores del catálogo "Nivel de divulgación" y se aplican al modelo antes de generar
  (RQ-REP-014); el código y la revisión siguen ASM-027.
- Si la transacción falla, las subidas quedan sin adjuntar y las borra `StoredFileCleanup` de wasichai-files
  (RQ-REP-027, ASM-133).

```json
{
  "deliverable": { "id": "5d4c3b2a-1f0e-4d9c-8b7a-6f5e4d3c2b1a", "updatedAt": "2026-10-07T16:20:05Z", "state": "issued", "attributes": { "current_revision": "0" } },
  "versions": [
    {
      "id": "e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b",
      "createdAt": "2026-10-07T16:20:05Z",
      "attributes": {
        "version_number": 1, "revision": "0", "disclosure_level": "INTERNO",
        "docx_sha256": "…", "pdf_sha256": "…", "model_sha256": "…", "template_sha256": "…"
      }
    }
  ]
}
```

### 4.13 BC-15 y BC-17 Exportaciones

Paquete `sgspe.privacy`. Las exportaciones XLSX aplican el nivel de divulgación al modelo antes de escribir el archivo y
quedan en `export_log` (RQ-REP-022, RQ-AUD-011).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/exports/{kind}` | Genera la exportación en XLSX y la registra | READ y acción declarada `EXPORT` sobre el objeto principal del tipo; CREATE sobre `export_log` | `{ filters, disclosureLevel, checklistConfirmed }` → `200` archivo XLSX en streaming | — | Según el tipo |

| `{kind}` | Contenido | Objeto principal | Desde |
|---|---|---|---|
| `stakeholder-register` | Registro de grupos de interés con su evaluación vigente | `stakeholder` | F2 |
| `commitment-matrix` | Matriz de compromisos con los campos de DOC-01 §9.3.1.2 (RF-COM-001), sin el documento de identidad de los solicitantes, que nunca se exporta en ningún nivel (RQ-REP-022) | `commitment` | F3 |
| `attention-register` | Quejas, consultas y solicitudes; EXTERNO sin las confidenciales, aunque quien exporta las lea (ASM-101) | `grievance`, `inquiry` y `stakeholder_request` | F4 |
| `risk-register` | Matriz de riesgos con sus revisiones, medidas e impactos (ASM-101) | `social_risk` | F4 |

**`stakeholder-register` (F2).**

- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`. `disclosureLevel` es `INTERNO`, `EXTERNO` o `PUBLICO`.
  `filters` es `{ project: string[], scope, stakeholderType, influenceGroup, locality, includeRetired }`: `project` y `locality`
  son UUID (o listas de UUID, sin repetidos), `scope`, `stakeholderType` e `influenceGroup` son valores de su catálogo e
  `includeRetired` es booleano. Una propiedad desconocida del cuerpo o de `filters` es `400` que la nombra, y un parámetro de
  consulta, `422` (ASM-054).
- **Proyectos.** Una lista vacía (o sin `project`) es «mis proyectos» de `ProjectReach`: las asignaciones vigentes de quien
  exporta, hoy en America/Lima; quien lee todos los proyectos (`ADMIN` y `READ_ALL_PROJECTS`), todos. Con **un solo**
  proyecto (los repetidos cuentan una vez), quien no lo lee todo necesita una asignación vigente a él: si no, `403`
  `NOT_ASSIGNED`; `export_log.project` lleva ese proyecto si quien exporta es `ADMIN` o está asignado, y si no queda vacío y
  el proyecto va en `filters_description`. Con dos o más proyectos explícitos no se exige asignación y `export_log.project`
  queda vacío; las filas de los proyectos que quien exporta no lee no salen (`security.md` §9.7).
- **Contenido.** Se lee como quien exporta: un campo que no puede leer sale `no disponible`. `INTERNO` nombra a las
  personas que quien exporta puede leer; `EXTERNO` las cita por código `GI-` (un seudónimo, `deliverables.md` §9.1) y por
  cargo, y exige `checklistConfirmed: true`; `PUBLICO` solo trae conteos por grupo de influencia y postura y por ámbito, sobre la grilla
  completa de los valores del catálogo (con las combinaciones sin ningún grupo de interés; un valor fuera del catálogo cuenta como
  `no disponible`) y sin totales; todo conteo menor que 5, el cero incluido, sale con la marca neutra `reservado`, que no dice qué
  valor esconde, y lo mismo una celda visible cuando ocultarla hace falta para que restar no despeje otra (ASM-088). Nunca sale el documento de identidad.
- **Errores.** Con su código en `errors[0].message` y el campo en `errors[0].field` (§3.3): `400`
  `INVALID_DISCLOSURE_LEVEL` (`disclosureLevel`), `CHECKLIST_REQUIRED` (`checklistConfirmed`), `INVALID_FILTER`
  (`filters.project`, `filters.locality`, `filters.scope`, `filters.stakeholderType`, `filters.influenceGroup` o
  `filters.includeRetired`) y `TOO_MANY_RECORDS` (`filters.project`); `403` `NOT_ASSIGNED`, `404` `EXPORT_NOT_FOUND`
  (una exportación que aún no existe en la fase) y `409` `CATALOGUE_UNAVAILABLE` (`PUBLICO` con el catálogo de un campo
  vacío; `field` es el campo). Sin READ ni `EXPORT` sobre `stakeholder`, o sin CREATE sobre `export_log`, `403` sin
  código.
- **Tope.** La lectura es acotada: `sgspe.exports.max-rows` (20 000 por defecto) por conjunto leído. Pasarse es
  `TOO_MANY_RECORDS`: hay que acotar los proyectos o los filtros. No se recorta en silencio.
- **`export_log`.** Una fila por exportación, con `export_kind = EXPORTACION`, escrita como quien exporta antes de entregar
  el archivo; si algo falla antes, no hay archivo ni fila. `exported_at` es el instante en que se generó el archivo, no el de
  su entrega, y `row_count` cuenta grupos de interés, no filas del archivo (en `PUBLICO`, los contados en los agregados). `filters_description` (`export_log` no tiene otro campo de filtros) lleva la lista completa de proyectos y el resto de los filtros.

**`commitment-matrix` (F3).**

- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`; `filters` es
  `{ project: string[], explorationCampaign, state: string[] }`: `explorationCampaign` es una sola campaña y `state`
  toma los estados de WF-D (`400 INVALID_FILTER` sobre `filters.explorationCampaign` o `filters.state`). Los proyectos
  siguen la regla de `stakeholder-register`.
- **Contenido.** INTERNO: hojas «Matriz» (los campos de DOC-01 §9.3.1.2, los solicitantes por nombre si se leen, hitos,
  reprogramaciones, acciones y el responsable) y «Entregas» (con quiénes dieron la conformidad y los receptores si se
  leen, y los conteos de actas, fotos y otras evidencias). EXTERNO: lo mismo sin el responsable ni los campos
  restringidos (`beneficiaries_detail`, `conformity_by`, `recipients`), con los solicitantes por código `GI-` y el lugar
  de cada entrega generalizado a la localidad del compromiso (`deliverables.md` §9.1); exige `checklistConfirmed`.
  PUBLICO: hojas «Por estado y tema» y «Por tipo de recurso», sobre la grilla completa con la marca `reservado`
  (ASM-088). Nunca el documento de identidad de los solicitantes (CON-014). `deadline_status` sale solo en un compromiso
  pendiente (`approved`, `in_progress` o `blocked`): uno cumplido o cancelado no sale VENCIDO aunque JOB-01 todavía no
  lo haya vaciado.
- **Tope.** `sgspe.exports.max-rows` por conjunto y 2 000 compromisos o entregas cuyos solicitantes y evidencias se leen
  uno por uno; pasarse es `400 TOO_MANY_RECORDS` sobre `filters.project`. Cada lectura por registro tiene además su tope
  de 200: un compromiso con más de 200 solicitantes, o una entrega con más de 200 evidencias, responde el mismo `400`
  filtre como filtre (límite aceptado en F3).
- **Errores.** Los de `stakeholder-register`; `export_log` lleva `object_name` `commitment` y `row_count` cuenta
  compromisos.

**`attention-register` (F4).**

- **Permiso.** READ y `EXPORT` sobre los tres objetos (hoy `SOCIAL_MANAGER`, `GRIEVANCE_MANAGER` y `ANALYST`) y CREATE
  sobre `export_log`; sin alguno, `403` sin código: un registro unificado con un tipo de menos engañaría.
- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`; `filters` es `{ project: string[], periodStart,
  periodEnd, type: string[], state: string[], channel, category, includeReclassified }`. El periodo filtra `received_at`
  en fechas de Lima; `state` toma estados de los tres workflows; `includeReclassified` (falso por defecto) agrega los
  originales reclasificados. Un filtro inválido es `400 INVALID_FILTER` sobre el filtro. Los proyectos siguen la regla
  de `stakeholder-register`.
- **Contenido.** Las hojas y columnas de cada nivel están en `deliverables.md` §10. INTERNO incluye las QCS
  confidenciales que quien exporta lee; EXTERNO excluye toda QCS confidencial (aunque quien exporta la lea), todo texto
  libre, la persona, quien recibió y el responsable, y exige `checklistConfirmed`; PUBLICO cuenta planteamientos
  (`AttentionCounting`) sobre la grilla completa con la marca `reservado` (ASM-088), sin totales, territorio ni grupo de
  interés, y su hoja «Notas» dice si el conteo incluye las confidenciales.
- **Tope.** `sgspe.exports.max-rows` (20 000 por defecto) por conjunto: las QCS de los tipos pedidos, juntas, y los
  registros que nombran sus vínculos (grupos de interés, personas, organizaciones, localidades, comunidades, temas,
  compromisos y originales reclasificados), que se leen por id en tramos de 500, nunca uno por uno. Pasarse es `400
  TOO_MANY_RECORDS` sobre `filters.project`; no hay tope por registro.
- **Errores.** Los de `stakeholder-register`; `export_log` lleva `object_name` `grievance,inquiry,stakeholder_request` y
  `row_count` cuenta planteamientos.

**`risk-register` (F4).**

- **Permiso.** READ y `EXPORT` sobre `social_risk` (`SOCIAL_MANAGER`, `SOCIAL_RISK_MANAGER` y `ANALYST`) y CREATE sobre
  `export_log`.
- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`; `filters` es `{ project: string[], state: string[],
  category: string[], riskLevel: string[], includeClosed }`, con `includeClosed` falso por defecto (`400 INVALID_FILTER`
  sobre el filtro).
- **Contenido.** Las hojas y columnas de cada nivel están en `deliverables.md` §10: INTERNO con «Matriz», «Revisiones»,
  «Medidas» e «Impactos»; EXTERNO sin texto libre ni correos, con los grupos de interés por código `GI-` y las medidas
  contadas en la «Matriz»; PUBLICO con conteos de riesgos activos sobre la grilla completa y `SmallCells`.
- **Tope.** `sgspe.exports.max-rows` (20 000 por defecto) por conjunto: los riesgos, y sus revisiones, medidas,
  impactos y registros vinculados, que se leen por id en tramos de 500. Los grupos de interés de cada riesgo (un vínculo
  M2M) se leen riesgo por riesgo, con un tope de 2 000 riesgos que rige solo en INTERNO y EXTERNO (PUBLICO cuenta y no
  lee vínculos) y de 200 vínculos por riesgo. Pasarse es `400 TOO_MANY_RECORDS` sobre `filters.project`.
- **Errores.** Los de `stakeholder-register`; `export_log` lleva `object_name` `social_risk` y `row_count` cuenta
  riesgos.

### 4.14 BC-18 IA y conocimiento

Paquete `sgspe.ai`, desde F7. Ningún endpoint de IA escribe datos de dominio: guarda `ai_interaction` y sugerencias o
candidatos, y solo la aceptación de una persona ejecuta el comando (RQ-AI-001, RQ-AI-003). Si la IA no está activada
para la organización o su proveedor no responde, los tres endpoints que llaman a un modelo (`runs`,
`assistant/questions` y `knowledge/questions`) responden `503 AI_UNAVAILABLE` (RQ-AI-009, ASM-015, ASM-128); `GET
/api/sgspe/ai/status` lo informa, y aceptar una sugerencia o un candidato ya guardado sigue funcionando sin proveedor.
Usar la IA exige poder crear `ai_interaction` y el objeto de salida. Los casos, la seudonimización y los flujos los
detalla `ai-architecture.md`.

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `GET` | `/api/sgspe/ai/status` | Si la organización tiene la IA activada, si el proveedor está disponible y qué casos se ofrecen | Autenticado | → `200 { enabled, providerAvailable, useCases[] }` | — | F7 |
| `POST` | `/api/sgspe/ai/runs` | Ejecuta un caso de IA (AI-01, AI-02, AI-03, AI-04, AI-07 o AI-08) sobre una fuente, con texto seudonimizado, y guarda sus sugerencias o candidatos; desde F8, AI-04 redacta borradores de sección sobre `{ object: "deliverable_section", recordId }`: exige además READ sobre la sección (quien no lee el proyecto del entregable recibe el `404` o `403` de wasichai), que el tipo la marque con IA (`422 SECTION_NOT_ASSISTED`) y que el entregable tenga los parámetros de su tipo (`422 DELIVERABLE_PARAMETERS_MISSING`, como `POST …/drafts`), y lee como la persona, seudonimizados, los registros que el tipo declara para esa sección y los KPI del periodo, nunca el modelo congelado (RQ-AI-012); si una fuente supera el tope de lectura, la corrida falla con `400 TOO_MANY_RECORDS` en vez de redactar sobre tablas vacías; una mención va por nombre solo si una sola variante del diccionario de la corrida cubre el nombre entero y es de la persona mencionada, y si no, como la cita EXTERNO. AI-07 corre sobre un `project` o un registro de `person`, `organization`, `stakeholder`, `grievance`, `inquiry`, `stakeholder_request` o `commitment`; sin pares candidatos no llama al modelo y responde `interaction: null`; con más de `sgspe.ai.duplicates.max-records` registros de un objeto, `400 TOO_MANY_RECORDS` sobre `target`; el tope `AI_BUSY` rige también para AI-07 | READ sobre la fuente; CREATE sobre `ai_interaction` y sobre `ai_suggestion` o `commitment_candidate`; READ de los nombres de `person`, que la seudonimización necesita (si no, `403` antes de llamar al modelo) | `{ useCase, target: { object, recordId } }` → `201 { interaction, interactions[], suggestions[], candidates[] }`: `interaction` es la `ai_interaction` de la primera llamada e `interactions`, las de todas (AI-01 hace dos: la extracción y los compromisos de AI-03); `suggestions` y `candidates`, lo guardado, como lo devuelve la API genérica. A lo más `sgspe.ai.limits.max-concurrent-runs` corridas a la vez por instancia (3); una más, `503 AI_BUSY` con `Retry-After` | `IK` | F7 |
| `POST` | `/api/sgspe/ai/assistant/questions` | AI-05: pregunta al asistente de solo lectura de wasichai-agent, como la persona que pregunta, con los resultados de sus herramientas seudonimizados y los campos restringidos enmascarados, y registra la interacción con sus tokens; la respuesta vuelve con los marcadores restaurados, salvo los de los campos restringidos (ASM-130, `ai-architecture.md` §8) | CREATE sobre `ai_interaction` (sin él, `403`; a una cuenta de servicio, `403 PERSON_REQUIRED`); no exige leer los nombres de `person` | `{ question }`, hasta `sgspe.ai.limits.question-chars` (4 000 caracteres; si no, `422 AI_INPUT_TOO_LARGE`; `500` si el filtro de resultados falla cerrado y no envía datos) → `200 { answer, steps[], truncated, interaction }`, con los pasos restaurados e `interaction`, el id de su `ai_interaction` | — | F7 |
| `POST` | `/api/sgspe/ai/knowledge/questions` | AI-06: responde con citas sobre los documentos que quien pregunta puede leer; sin fragmento que pase los filtros, dice que no hay evidencia | CREATE sobre `ai_interaction`; READ sobre `evidence` y, para fragmentos de evidencias CONFIDENCIAL o DATOS PERSONALES, `DOWNLOAD_RESTRICTED`; READ de los nombres de `person` (si no, `403` antes de llamar al modelo) | `{ question, project }` (la pregunta hasta `sgspe.ai.limits.question-chars`; si no, `422 AI_INPUT_TOO_LARGE`) → `200 { answer, citations[]: { evidenceId, chunkIndex, quote }, interaction }`, con marcadores estables en `answer` y `quote`; sin fragmento que pase los filtros, `citations: []` e `interaction: null` (no se llama al modelo); `404` si quien pregunta no lee el proyecto; `403` si no lee el objeto `evidence`. Una cita vale solo si aparece en su fragmento y tiene al menos 15 caracteres y tres palabras. No pasa por el tope `AI_BUSY` de `runs` (`RunGate`): no abre una transacción ni retiene una conexión mientras llama al modelo | — | F7 |
| `POST` | `/api/sgspe/ai-suggestions/{id}/accept` | Acepta una sugerencia: ejecuta el comando de dominio con la autorización de quien acepta y lleva la sugerencia a `accepted`. Solo ejecuta lo que permite la lista cerrada de `ai-architecture.md` §4.2 (tipo, objeto destino y campo o comando); todo lo demás, y todo campo derivado u objeto `apiOnly`, se rechaza; desde F8, una BORRADOR DE SECCION (o una RESUMEN EJECUTIVO copiada a una sección) escribe `content`, `content_origin` ASISTIDO POR IA y `ai_suggestion` con `DeliverableSections.adopt`, en la transacción de WF-K hacia `accepted` y condicionada al `updatedAt` de la sección que leyó (si otra escritura la cambió entre medio, `412` con el valor actual en `errors[]`, nada se escribe y la sugerencia sigue `proposed`), y convierte `[PERSONA:<id>]`, también entre «», que se quitan, en `[[person:<id>]]` (`[PERSONA]` o `[PERSONA_n]`, `422 UNRESOLVED_PSEUDONYM`; cualquier otro marcador, `400 PSEUDONYM_NOT_ALLOWED`; ASM-137); una RESUMEN EJECUTIVO se acepta con `{ section, content }` y se copia a esa sección con `DeliverableSections.adopt` (`409 PROJECT_MISMATCH` si la sección no es del proyecto de la sugerencia; `422 SECTION_NOT_ASSISTED` si el tipo no la marca con IA; sin `section`, `400 FIELD_REQUIRED` sobre `attributes.section`) y una BORRADOR DE SECCION, con `{ content }` | UPDATE sobre `ai_suggestion`, el permiso del comando que ejecuta y el rol de la transición de WF-K a `accepted` | `{ attributes }` con la forma de `suggested_value` de su fila (`ai-architecture.md` §4.2) y los valores revisados: CLASIFICACION `{ field, value }`; tema `{ item, topic }`; lugar y fecha `{ item, field, value }` (en CLASIFICACION, lugar y fecha quien acepta corrige `value`, no `field`: si viene, `field` debe ser el guardado en `suggested_value`, y si no, `400 FIELD_NOT_ALLOWED` sobre `attributes.field`; omitido, vale el guardado); cláusula `{ item, clause_number, clause_text, creates_commitment }`; organización `{ item, kind, name, organization_type?, locality?, stakeholder? }` (con `target_record_id` se usa la existente y esos tres campos no se escriben); persona nueva `{ item, kind, given_names, surnames, sex?, residence_locality?, stakeholder? }` y existente `{ item, kind, stakeholder? }`; BORRADOR DE ACTA `{ minutes }`; DUPLICADO `{ other_record_id, response? }` (`response`, solo en una queja: además la rechaza como duplicado) → `200 { suggestion, result, stakeholder? }`, con `ETag` de la sugerencia; `result` es el registro que el comando creó o cambió (`null` si solo se registra la decisión) y `stakeholder`, el grupo de interés de un actor | `IK`, `IM`, `CR` | F7 |

Los candidatos a compromiso que produce la IA se aceptan o se marcan como duplicados con los endpoints de §4.5. Rechazar una
sugerencia o un candidato, o marcar como duplicada una sugerencia, son transiciones de la API genérica, siempre humanas
(RQ-AI-015): una cuenta de servicio no las aplica sobre una sugerencia (`403 PERSON_REQUIRED`, `SuggestionDecisionGuard`).

**Errores.** Además de los de §3.3, los tres endpoints que llaman a un modelo responden `503 AI_UNAVAILABLE` sin
activación o sin proveedor, `assistant/questions` responde `422 AI_INPUT_TOO_LARGE` (`question`) y `500` si su
filtro falla cerrado, y `runs` responde `422 AI_INPUT_TOO_LARGE`, `422 AI_NO_TEXT` y `502 AI_RUN_TRUNCATED` con
los campos de §3.3 (ASM-128, ASM-131, ASM-132). Ninguno de esos errores guarda una sugerencia o un candidato; la
`ai_interaction` de cada llamada hecha queda registrada.

Desde el plan 7.2b, `runs` responde además, antes de llamar al modelo, `400` sobre `useCase`, `target.object` o
`target.recordId` (un caso que no es de las corridas o un objeto que no es el suyo), `400 PROJECT_REQUIRED` (`target`:
AI-03 sobre una evidencia del tenant), `403 PERSON_REQUIRED` a una cuenta de servicio, `403 FIELD_NOT_READABLE`
(`person.given_names`) a quien no lee los nombres de `person`, `422 AI_INPUT_TOO_LARGE` también si el archivo de la
evidencia pasa `sgspe.ai.limits.file-bytes`, `422 AI_NO_TEXT` (`target`) si la evidencia no tiene archivo o su archivo no
tiene texto que enviar y `503 AI_BUSY` (`useCase`, con `Retry-After`) si ya corren en la instancia
`sgspe.ai.limits.max-concurrent-runs` corridas (3 por defecto): no espera un lugar, no abre la transacción de la
`Idempotency-Key` ni registra una `ai_interaction`. Después de llamar, `502 AI_RUN_TRUNCATED` si el modelo cortó la salida
por el tope de tokens. La aceptación responde `409 STATE_MISMATCH` (`state`), `409
SUGGESTION_NOT_ACCEPTABLE` (`suggestion_kind`: una fila fuera de la lista; desde 8.2c, RESUMEN EJECUTIVO se acepta con
`{ section, content }`), `400` sobre `reason` o sobre `attributes.<campo>` (`FIELD_REQUIRED`, `FIELD_NOT_ALLOWED`, `INVALID_VALUE`,
`DUPLICATE_NOT_FOUND`, `PROJECT_REQUIRED` y `PSEUDONYM_NOT_ALLOWED` si queda un marcador, también dentro de una lista o de
`stakeholder`; `FIELD_NOT_ALLOWED` sobre `attributes.field` si cambia el campo guardado; `INVALID_VALUE` si un texto llega como
lista u objeto; `FIELD_REQUIRED` sobre `attributes.stakeholder.stakeholder_type` si se pide el grupo de interés sin tipo, antes
de escribir nada), `403 PERSON_REQUIRED`,
`ROLE_REQUIRED`, `OBJECT_NOT_READABLE` (`target_record_id`) y `FIELD_NOT_WRITABLE` (`attributes.given_names` o
`surnames`), `409 PROJECT_MISMATCH` (`attributes.other_record_id`) y `409 STATE_MISMATCH` (`attributes.response`: un
DUPLICADO de queja con `response` cuando la queja ya no se puede rechazar desde su estado). Al copiar a una sección (un
RESUMEN EJECUTIVO o un BORRADOR DE SECCION) responde además `422 UNRESOLVED_PSEUDONYM` (`attributes.content`), `403
OBJECT_NOT_READABLE` y `409 PROJECT_MISMATCH` (`attributes.section`) y, solo un RESUMEN EJECUTIVO, `422
SECTION_NOT_ASSISTED` (`attributes.section`) si el tipo no marca esa sección con IA, todos antes de escribir.

**Las salidas de la IA solo llevan marcadores.** `suggested_value` y `evidence_quote` de `ai_suggestion`, y `proposed_text`,
`evidence_quote` y `beneficiaries_text` de `commitment_candidate`, se guardan con marcadores estables en lugar de lo que la
seudonimización reconoció: una persona registrada queda como `[PERSONA:<id>]`; los nombres de participantes y beneficiarios
(valores restringidos de la fuente), como `[PERSONA]` sin id; y lo detectado por formato, como marcador sin valor
(`[DOCUMENTO]`, `[TELEFONO]`, `[CORREO]`). Solo el `name` de un actor persona de AI-01 es siempre un marcador
(`[PERSONA:<id>]` o `[PERSONA]`); en el texto libre, un nombre que no está registrado como `person` ni es un valor
restringido de la fuente queda tal cual: el enmascarado es de mejor esfuerzo (`security.md` §7.9, RQ-REP-033). La respuesta
de `runs`, la API genérica y los endpoints de candidatos devuelven esos marcadores, nunca los valores que reemplazan. Un permiso de campo oculta `evidence_quote` a los
roles sin `DOWNLOAD_RESTRICTED` sobre `evidence`, también en la API genérica (`security.md` §7.9); además, el portal
re-identifica al mostrar, con los permisos de quien mira, y la cita solo la ve quien puede descargar la evidencia fuente
(READ y, si es CONFIDENCIAL o DATOS PERSONALES, `DOWNLOAD_RESTRICTED`). Aceptar escribe los valores que la persona revisó,
así que ningún marcador llega a un registro de dominio: la aceptación de una sugerencia y la de un candidato (§4.5)
rechazan un marcador en cualquier valor de `attributes`, también dentro de una lista o un objeto, con `400
PSEUDONYM_NOT_ALLOWED` sobre `attributes.<ruta>` (`ai-architecture.md` §12.2, `security.md` §7.8).

### 4.15 Contextos sin endpoints propios

| BC | Cómo se opera | Fuente |
|---|---|---|
| BC-01 Proyectos | API genérica; `ProjectWriteGuard` exige el CRS de trabajo y una parte CLIENTE u OPERADOR para llegar a `active` | RF-PRJ-003, ASM-062 |
| BC-06 Acuerdos y convenios | API genérica; `AgreementWriteGuard` exige los predios en los permisos de uso de terreno y la asamblea en sus autorizaciones | RF-AGR-002, RF-AGR-003 |
| BC-09 Alerta temprana | API genérica; las alertas las crea `WarningRulesJob` y solo una persona con `CONFIRM_ALERT` las confirma o descarta | RF-EWS-004, RN-EWS-001 |
| BC-13 Tareas | API genérica, con el filtro `assignee_user_id` para la bandeja "mis tareas"; las tareas las crean automatizaciones y trabajos | RF-WFL-001 |
| BC-16 Identidad y acceso | `/api/auth/**`, `/api/users` y `/api/roles` de wasichai; `project_assignment` por la API genérica | RF-IAM-001, RF-PRJ-005 |
| BC-17 Auditoría | `/api/audit` y el historial por registro de wasichai | RQ-AUD-005 |
| BC-19 Línea base social | API genérica | RF-LBS-001 |
| BC-20 Catálogos | API genérica y el script de carga del ubigeo | RF-CAT-001, RF-TER-001 |

BC-11 (operaciones de campo) vive en BC-04 durante el MVP (ASM-045).

## 5. Endpoints por fase

| Fase | Endpoints nuevos de `/api/sgspe/**` |
|---|---|
| F1 | Ninguno: la API genérica de wasichai más el filtro de correlación |
| F2 | `transition-checks`; importación del territorio (vista previa y confirmación); `project-components` (alta, versión y `PATCH`); aprobación de áreas de influencia; capas temáticas básicas; evaluaciones de grupos de interés y su vista previa; catálogo de indicadores y tableros `stakeholders` y `engagement`; exportación `stakeholder-register` |
| F3 | Reprogramaciones y entregas de compromisos; `PATCH` de entregas; candidatos (alta, aceptación y duplicado); evidencias (alta, descarga, `PATCH` y vínculos); tablero `commitments`; exportación `commitment-matrix`. Los avisos, la subida de archivos y los documentos emitidos son de wasichai |
| F4 | Derivación de preguntas; reclasificación y aceptación de QCS; reporte unificado de QCS; revisiones de riesgo; tableros `attention` y `risks`; exportaciones `attention-register` y `risk-register` |
| F5 | Entregas de aportes sociales; aceptación de solicitudes como aporte; tablero `investment` |
| F6 | Sugerencias de miembros y resumen del área de influencia, consultas espaciales, punto en polígono, fichas territoriales, mapa base por organización (`map/settings`), proxy WMS (el WFS, pospuesto por ASM-120), validación y calidad de geometrías, exportaciones geográficas, formatos de importación por GDAL (`imports/formats`) y destinos `community` y `environmental_feature`, capas de gestión en `layers/{layer}`; tablero `territory` |
| F7 | Estado de la IA, ejecución de casos, preguntas al asistente y sobre los documentos, aceptación de sugerencias |
| F8 | Borradores, emisión y descargas de entregables, plantillas Word; cierre de periodos de indicadores; tableros `executive`, `project` y `monitoring`. SGSPE atiende además las rutas nativas de archivos de los objetos de entregables, que no son endpoints nuevos |
