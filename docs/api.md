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
- **Solo lo que la API genérica no puede.** Un endpoint de `/api/sgspe/**` existe para comandos que escriben varios registros,
  cálculos al escribir, archivos, geometrías con reglas, consultas agregadas o espaciales y la bandeja de avisos. Nunca es un
  CRUD por objeto (CON-040, RQ-AUD-004).
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
| Registros | `GET`, `POST`, `PUT` y `DELETE /api/objects/{object}/records` | Lectura de todos los objetos y escritura de los que no son `apiOnly` | F1 (objetos desde F2) |
| Registros relacionados | `/api/objects/{object}/records/{id}/related/{relationship}` | Vínculos MANY_TO_MANY entre objetos que no son `apiOnly`. Una autorrelación (localidad padre, versión anterior, duplicado) solo se lee desde el lado que apunta: los registros que apuntan a uno se leen filtrando la lista por el campo, por ejemplo `?parent_locality=<id>` (UPS-16) | F2 |
| Workflow | `GET /api/objects/{object}/records/{id}/transitions`, `POST …/transitions/{name}` | Toda transición que no es de comando; la lista explica por qué una transición no está permitida | F2 |
| Documentos emitidos | `POST /api/objects/{object}/records/{id}/documents/{type}`, `GET /api/documents/{id}` | Actas, minutas, cartas y fichas de un registro (RQ-REP-029) | F3 |
| GIS | `GET /api/gis/objects/{object}/features`, `/api/gis/layers/**`, `GET /api/gis/services` | Capas temáticas básicas en el mapa (F2); publicación en GeoServer solo de capas no sensibles, desde `model/publish_layers.py` (F6, RQ-SEC-011) | F2 (features), F6 (`/api/gis/layers/**`) |
| Auditoría | `GET /api/audit`, `GET /api/objects/{object}/records/{id}/history` | `AUDITOR` consulta todo el tenant; las fichas del portal muestran el historial de cada registro (RQ-AUD-005) | F1, F2 |
| Vistas, formularios y páginas | `/api/objects/{object}/views/**`, `/forms/**`, `/pages/**` | Listas y fichas configuradas por metadata, en `/admin` y en el portal | F2 |
| Automatizaciones | `/api/objects/{object}/automations/**`, `GET /api/automation-runs` | Reglas aplicadas desde `model/`; el registro de ejecuciones explica por qué cambió un registro | F3 |
| Asistente | `GET /api/agent/status`, `POST /api/agent/ask` | AI-05, de solo lectura y como el usuario (RF-AIK-005). `sgspe.ai` lo llama en proceso desde `POST /api/sgspe/ai/assistant/questions` (§4.14); por HTTP, un filtro de `sgspe.ai` responde `403`, para que toda pregunta pase por la activación por organización y quede registrada, mientras wasichai-agent no tenga sus puntos de extensión (UPS-14, `ai-architecture.md`) | F7 |

Lo que SGSPE agrega o restringe sobre la API genérica, con guardas y filtros propios:

- `X-Correlation-Id` en toda respuesta (§3.8), también en un `401`.
- `If-Match` opcional en las escrituras del portal, comprobado por una guarda (§3.6).
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
4. Lo que no es un Custom Object (un aviso de la tabla fija `notification`, un indicador) va todo en camelCase, sin
   `attributes`.

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
`ForbiddenException`, `NotFoundException`) o subclases propias de `WasichaiException` para `412`, `422`, `428` y `503`
[WAS-ADR-0040].

| Estado | Cuándo en `/api/sgspe/**` |
|---|---|
| `200` | Lectura; comando que no crea registros; reenvío con la misma `Idempotency-Key` |
| `201` | Comando que crea registros, con `Location` del registro principal |
| `202` | Pedido aceptado para trabajo en segundo plano (borradores de entregable) |
| `204` | Sin cuerpo: marcar un aviso como leído, vincular o desvincular |
| `400` | Cuerpo inválido: valor, campo desconocido, motivo faltante o una guarda que rechaza los datos enviados |
| `401` | Sin token o con un token inválido o vencido; sin token, el cuerpo va vacío (§3.3, «Comportamiento fijado de wasichai 0.3.2») |
| `403` | Falta un permiso o una acción declarada; escritura fuera de las asignaciones; edición de un registro ajeno sin `EDIT_ANY`; escritura genérica sobre un objeto `apiOnly` |
| `404` | El registro no existe o es de otra organización; ruta de un módulo no instalado |
| `409` | Estado incompatible (transición desde otro estado, objeto `appendOnly`, regla que depende de otros registros, borrado de un registro con dependientes: `HAS_DEPENDENTS`), valor único repetido o `Idempotency-Key` reutilizada con otra petición |
| `412` | `If-Match` no coincide con el `updatedAt` actual (§3.6) |
| `422` | Parámetro de consulta desconocido (ASM-054) |
| `428` | Falta `If-Match` en un comando que lo exige (§3.6) |
| `503` | En los endpoints de IA que llaman a un modelo (§4.14): la IA no está activada para la organización o su proveedor no responde, como el asistente de wasichai sin clave [WAS-DOC api/rest.md]; y en una importación cuando ya corren tantas como permite `sgspe.territory.import.max-concurrent` (`IMPORT_BUSY`, §4.2) |

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

**Errores de las guardas de SGSPE en la API genérica.** Toda guarda de SGSPE que rechaza una escritura por `/api/objects/**` lo
hace con su código en `errors[0].message` y el campo en `errors[0].field` (`record` si el hallazgo no apunta a un campo); el
`detail` lleva el texto en español. Así dos `403` o dos `409` se distinguen por código y no solo por estado (ruling P15). El
`detail` y `errors[0].message` de un error de wasichai (permiso, validación de tipo, obligatorio vacío) no siguen esta forma: su
`message` es texto libre. Los códigos que existen hoy, con el estado de la escritura real:

| Estado | Códigos |
|---|---|
| `400` | `DERIVED_FIELD` (se envió un campo derivado), `REASON_REQUIRED` (la transición exige `X-Change-Reason`), `FIELD_REASON_REQUIRED` (cambiar `stage` o `exploration_phase` de un proyecto exige motivo), `EXACTLY_ONE` (debe venir exactamente uno de un grupo de campos), `USER_REFERENCE` (el par `<rol>_user_id` y `<rol>_user_email` no nombra a un usuario de la organización), `UNKNOWN_ROLE` (`role_name` de una asignación no es un rol declarado), `REPRESENTED_PEOPLE` (`represented_people_count` menor que 1), `POSITION_WITHOUT_PERSON` (`position_status`: un cargo sin persona solo puede estar `VACANTE` o `PREVISTO`) |
| `403` | `COMMAND_ONLY` (la transición es de comando), `NOT_ASSIGNED` (sin asignación vigente al proyecto), `ACTION_REQUIRED` (falta la acción declarada `APPROVE`), `NOT_OWN_RECORD` (el objeto declara `EDIT_ANY`, quien escribe no lo tiene y no creó el registro: no lo cambia, mueve, vincula ni borra; `record`), `OBJECT_NOT_READABLE` (quien escribe no lee un objeto que la regla necesita: `project_party`, `project`, `engagement_participant`, `engagement_question` o `area_of_influence`) |
| `409` | `HAS_DEPENDENTS` (el registro tiene dependientes obligatorios y no se borra; `field` es `<objeto>.<campo>` del primer dependiente), `FROZEN_MEMBERS` (los miembros de una versión `approved` o `superseded` del área de influencia no cambian; `field`: `area_of_influence`), `FROZEN_AREA` (su proyecto, tipo y número de versión tampoco; `field` es el campo que cambia), `WORKING_CRS_REQUIRED`, `PARTY_REQUIRED`, `FIELD_REQUIRED` (`scheduled_at`, `held_at`, `minutes` o `cancel_reason` de una interacción), `ATTENDANCE_REQUIRED`, `PENDING_QUESTIONS`, `MINUTES_REQUIRED`, `PROFILE_INCOMPLETE` (`caracterizar` exige `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` y `owner_user_id`; `field` es el primero que falta), `DUPLICATE_RULE` (`stakeholder_type`: ya hay una fila de `prioritization_rule` para cualquier tipo de actor con ese proyecto, grupo de influencia, postura y ámbito) |

`HAS_DEPENDENTS` rige para todos, `ADMIN` incluido, porque la base rechazaría el borrado igual: wasichai declara cada relación
obligatoria NOT NULL con ON DELETE SET NULL y, sin esta guarda, borrar el padre respondería 500 (UPS-20). Borrar primero a los
dependientes lo resuelve. `STATE_MISMATCH` es un código de `transition-checks` (§4.1) y del comando de evaluaciones (§4.3): la transición real por la API
genérica la rechaza wasichai. Esta tabla crece con cada guarda nueva.

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
`file` y sin escribir nada; `400` `FORMAT_NOT_SUPPORTED` (`file`: un predio o una concesión desde una hoja), `400`
`SHA256_MISMATCH` (`sha256`: el archivo no es el de la vista previa), `409` `IMPORT_CONFLICT` (`file`: lo registrado con
un código del archivo cambió entre el análisis de la confirmación y su candado, por ejemplo otra confirmación del mismo
archivo; no se escribe nada y se vuelve a previsualizar) y `503` `IMPORT_BUSY` (`file`: ya corren `max-concurrent`
importaciones y no se liberó un lugar dentro del plazo). Los límites del multipart de Spring (§3.10) son `400` con `field`
`multipart`, que designa la petición entera y no es el nombre de ninguna parte: `PART_TOO_LARGE` (un campo de texto pasa de
1 MB o los encabezados de una parte pasan de 10 KB) e `INVALID_MULTIPART` (más partes que `max-parts` o un cuerpo que no se
puede leer); solo el tope de disco de la parte de archivo es `FILE_TOO_LARGE`, sobre `file`. Las demás respuestas `400` (una parte que falta, se repite o no
existe, un CRS fuera del catálogo, una decisión mal formada o sobre una fila que no es válida) son de validación, con
texto libre, y un parámetro de consulta es `422`. Una fila que PostGIS no puede transformar ni validar no es un error de
la petición: va a `invalid[]` con el motivo `geometría inválida: …` en español.
Los tableros y sus indicadores (§4.11) responden `404` `DASHBOARD_NOT_FOUND` e `INDICATOR_NOT_FOUND`, `400`
`INVALID_BREAKDOWN` y `400` `TOO_MANY_RECORDS`; el detalle está en §4.11.
Las exportaciones (§4.13) responden `400` `INVALID_DISCLOSURE_LEVEL` (`disclosureLevel`), `CHECKLIST_REQUIRED`
(`checklistConfirmed`: EXTERNO sin la lista de verificación), `INVALID_FILTER` (`filters.<filtro>`) y `TOO_MANY_RECORDS`
(`filters.project`, no `project` como los tableros); `403` `NOT_ASSIGNED` (`filters.project`: un solo proyecto sin asignación
vigente), `404` `EXPORT_NOT_FOUND` (`kind`) y `409` `CATALOGUE_UNAVAILABLE` (el campo cuyo catálogo está vacío, en `PUBLICO`). El detalle
está en §4.13.

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

- Los `POST` de `/api/sgspe/**` que crean registros admiten `Idempotency-Key` (marca `IK` en las tablas de §4)
  (RNF-INT-002). El portal genera una clave por acción del usuario, de 1 a 64 caracteres, y la repite en cada reintento, como
  en caja.
- La clave se honra desde F3, cuando llega la tabla `idempotency_key`. En F2, los endpoints aceptan la cabecera y la
  ignoran: el portal la envía desde F2 y no cambia cuando empieza a honrarse.
- Primera petición con una clave: se procesa y responde como siempre (`201`, o `200` en un comando sin registro principal,
  como la confirmación de una importación).
- Reenvío con la misma clave y la misma petición (misma ruta y mismo SHA-256 del cuerpo): `200` sin escribir nada. La
  respuesta se reconstruye releyendo, en su estado actual, los registros principales que guarda la clave (`created_object`
  y `created_record_ids`) y los que la respuesta nombraba a partir de ellos, como el compromiso de una reprogramación. En la
  confirmación de una importación, se recuentan por `import_batch_id` los registros que escribió; las filas sin cambio o
  rechazadas no dejan registro y la repetición no las informa. La tabla no guarda nada más (ASM-065).
- Las partes calculadas de una respuesta que la relectura no reconstruye se vuelven a calcular si se puede y, si no, la
  repetición las omite. Por ejemplo, `distanceM` de `POST /api/sgspe/project-components` se recalcula con la versión nueva
  y la anterior releídas, y los `warnings[]` de una evaluación (`POST /api/sgspe/stakeholders/{id}/assessments`), que
  dependían del estado de ese momento, se omiten.
- La misma clave con otra petición: `409` que nombra `Idempotency-Key`.
- Dos peticiones simultáneas con la misma clave se serializan con `ClusterLock.withXactLock` sobre la clave: la segunda espera
  y recibe el `200` del reenvío [WAS-ADR-0039].
- La clave vale por organización y usuario: la de un usuario nunca choca con la de otro.
- Sin la cabecera no hay deduplicación. La API genérica de wasichai no conoce `Idempotency-Key`, así que sus altas no son
  idempotentes hasta que exista UPS-18; los únicos compuestos del modelo evitan los duplicados que importan [WAS-ADR-0037].
- Las claves se guardan en la tabla fija `idempotency_key` del esquema `sgspe` (organización, usuario, clave, ruta, SHA-256
  del cuerpo, objeto e identificadores de los registros creados y fecha), escrita en la misma transacción que el comando y
  purgada por JOB-08 después de un plazo configurable (ASM-065) [domain-model §4.10]. No se guarda la clave en un campo de
  cada registro creado, como hace caja con su recibo, porque agregaría un campo a todos los objetos que crean los comandos.

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
- **En la API genérica** (`PUT`, `PATCH`, `DELETE`, transiciones y vínculos), el portal envía la misma cabecera. Un filtro web de
  `sgspe.common` la pone en el contexto de Reactor, como hace el filtro de correlación con su identificador, y una guarda de
  `sgspe.common` la compara con el `updatedAt` almacenado antes de la escritura (`412` si no coincide); la guarda corre en la
  corrutina de la petición, que conserva ese contexto [WAS-ADR-0040]. La API genérica no abre una transacción, así que queda
  una ventana mínima entre la comparación y la escritura hasta que exista UPS-04.
- **Cuando solo cambiaron derivados.** Si el `updatedAt` leído ya no es el actual, la guarda no responde `412` mientras todo
  lo escrito después sean campos derivados o vínculos (los conteos de asistentes que mueve un participante, el área que
  recalcula un listener): lo lee del historial de auditoría con los permisos de quien llama. La versión leída es la
  escritura cuya entrada del historial cae más cerca del `updatedAt`: desde wasichai 0.5.0, `updatedAt` toma la hora de su
  sentencia y la entrada, la del inicio de su transacción, así que cae poco antes dentro de una transacción y poco después
  fuera de ella [WAS-ADR-0051]. **Límite conocido:** el historial
  que lee wasichai 0.3.2 es de a lo sumo 500 entradas, las más recientes; con más de 500 escrituras posteriores a la versión
  leída su entrada queda fuera de la ventana y la respuesta es `412`. Falla cerrado: el portal recarga el registro y repite.
- **La comparación de wasichai no participa.** Desde 0.5.0 wasichai responde `ETag` con el `updatedAt` entre comillas y
  compara `If-Match` en la propia escritura, con igualdad estricta [WAS-ADR-0051]. Esa comparación respondería `412` en el
  caso de los derivados del punto anterior, así que el filtro de `sgspe.common` quita la cabecera de la petición después de
  guardarla y la precondición sigue siendo solo la de la guarda; la decisión de adoptarla está en
  [Encaje con wasichai §4.3](platform-fit.md#43-lo-que-trae-wasichai-060). El `PATCH` de 0.5.0 pasa por la misma guarda.
- **`/admin` no envía la cabecera:** ahí sigue ganando la última escritura (UPS-04).
- **Precisión:** se compara el texto exacto que devuelve wasichai. Si su serialización recorta la precisión de la columna, dos
  escrituras dentro de la misma unidad no se distinguen; el riesgo desaparece con UPS-04, que retira esta comprobación.

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
- Va en la línea de acceso y en los logs del código de SGSPE, y en las llamadas salientes de SGSPE a Gotenberg y a GeoServer;
  llegará a las filas de auditoría cuando exista UPS-03.

### 3.9 Permisos y alcance

- Cada fila de §4 dice qué permiso o acción declarada exige. El endpoint comprueba todos los permisos que necesita antes de la
  primera escritura y responde `403` nombrando el que falta, como en caja; `RecordService` vuelve a comprobarlos en cada
  escritura y aplica los permisos de campo (RQ-SEC-004).
- El código nunca compara nombres de rol (RQ-SEC-005). Cuando un comando aplica una transición, `WorkflowService` exige además
  el rol de esa transición, que fija `workflows.md`.
- Las escrituras sobre objetos de un proyecto pasan por `ProjectScopeGuard`: fuera de las asignaciones de quien llama, `403`.
  Las consultas propias aplican el mismo alcance de lectura que la API genérica según la decisión pendiente CON-041, con la
  recomendación A (lectura a nivel de tenant y escritura acotada por `project_assignment` en el MVP; la extensión UPS-01 antes
  de la producción con varios proyectos), y todas aceptan `project` para acotar (RQ-SEC-006, GAP-025).
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
| `EXPORT` | `stakeholder`, `commitment`, `grievance`, `inquiry`, `stakeholder_request`, `social_risk`, `deliverable` y, en F6, cada objeto de una capa `LYR-NN` (`gis.md` §2.2) que se exporte por `POST /api/sgspe/territory/exports` | `POST /api/sgspe/exports/{kind}`, `POST /api/sgspe/territory/exports`, `GET /api/sgspe/territory/wfs` y la descarga de versiones EXTERNO y PUBLICO |
| `GENERATE` | `deliverable` | `POST /api/sgspe/deliverables/{id}/drafts` |
| `ISSUE` | `deliverable` | `POST /api/sgspe/deliverables/{id}/issue` |
| `DOWNLOAD_RESTRICTED` | `evidence`, `deliverable` | La descarga de evidencias CONFIDENCIAL o DATOS PERSONALES, de borradores y de versiones INTERNO, y bajar la clasificación de una evidencia restringida (`PATCH /api/sgspe/evidence/{id}`, ASM-071) |
| `EDIT_ANY` | `engagement`, `engagement_participant`, `field_visit`, `field_observation`, `evidence` | Cambiar, transicionar, vincular o borrar un registro que creó otra persona (`EditAnyGuard`), por la API genérica o por un comando; agregar participantes u observaciones a una interacción o visita ajena no la exige |

### 3.10 Archivos

- Las subidas usan `multipart/form-data`: una parte `file` y, si hace falta, partes JSON (`attributes`, `links`). El tamaño
  máximo y el tiempo de proceso son configurables; las importaciones corren en una carpeta temporal con control de rutas
  dentro de los ZIP (RQ-SEC-020, ASM-050). En F2, `sgspe.territory.import.*`: `max-bytes` (20 MB), `max-rows` (10 000),
  `timeout` (60 s, lectura y validación) y `max-concurrent` (4 importaciones a la vez); Spring guarda la parte de archivo
  en disco con su propio tope (`spring.webflux.multipart.max-disk-usage-per-part`, 25 MB, con `max-parts` 8), que responde
  igual (400 `FILE_TOO_LARGE`; un campo de texto de más de 1 MB o encabezados de parte de más de 10 KB, `PART_TOO_LARGE`;
  demasiadas partes, `INVALID_MULTIPART`, ambos sobre el campo `multipart`).
- La escritura de la confirmación no está bajo `timeout`: corre después, dentro del candado y de una sola transacción,
  que retienen una conexión del pool hasta el commit. Su cota es `max-rows`: cada fila son unas pocas escrituras con sus
  guardas (del orden de milisegundos con la base local, una estimación sin medir), así que una hoja de 10 000 filas
  puede tardar decenas de segundos o, con versiones nuevas, el doble. El proxy delante del backend debe dejar a
  `POST …/confirm` más tiempo que a las demás rutas (al menos 120 s); si la corta, la transacción se revierte entera y no
  queda nada escrito. Una hoja más grande se parte en varias importaciones.
- Las descargas son en streaming, con `Content-Type`, `Content-Disposition` y, para lo inmutable (evidencias y versiones
  emitidas), `ETag` igual al SHA-256 del archivo.
- Ninguna URL externa se descarga en el servidor (ASM-050).

## 4. Endpoints de `/api/sgspe/**` por bounded context

Columnas: **Encabezados** usa `IK` (admite `Idempotency-Key`, §3.5, que se honra desde F3), `IM` (exige `If-Match`,
§3.6), `IM?` (lo admite sin exigirlo) y `CR` (exige `X-Change-Reason`, §3.7). **Fase** es la del roadmap; una ruta de una
fase futura no existe antes. Las transiciones que no son de comando, las altas y las ediciones de objetos que no son
`apiOnly` usan la API genérica (§2).

### 4.1 Transversal (`sgspe.common`)

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/transition-checks` | Ejecuta en seco las guardas de SGSPE sobre una transición y devuelve lo que la bloquearía y las advertencias, sin escribir | READ sobre el objeto | `{ object, recordId, transition }` → `200 { allowed, blocking[], warnings[] }` | — | F2 |

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

| Código | Tipo | Estado de la escritura real | Cuándo |
|---|---|---|---|
| `STATE_MISMATCH` | bloqueo | 409 | El registro no está en el estado de origen (`from`) de la transición |
| `COMMAND_ONLY` | bloqueo | 403 | La transición es de comando: solo la aplica su endpoint (`assess` de WF-B; `approve` y `supersede` de WF-O) |
| `REASON_REQUIRED` | advertencia (`field`: `reason`) | 400 | La transición exige motivo (`X-Change-Reason`): pídalo antes de confirmar |
| `NOT_ASSIGNED` | bloqueo | 403 | Quien consulta, si no es ADMIN, no tiene una asignación vigente al proyecto del registro |
| `ACTION_REQUIRED` | bloqueo | 403 | `approve` de un proyecto exige la acción declarada `APPROVE` además del rol de la transición |
| `WORKING_CRS_REQUIRED` | bloqueo (`field`: `working_crs`) | 409 | `approve` o `resume` de un proyecto sin `working_crs`, con `policy_activation_preconditions` activa |
| `PARTY_REQUIRED` | bloqueo (`field`: `project_party`) | 409 | `approve` o `resume` de un proyecto sin una parte CLIENTE u OPERADOR, con la misma política activa |
| `NOT_OWN_RECORD` | bloqueo | 403 | El objeto declara `EDIT_ANY`, quien consulta no lo tiene y no creó el registro |
| `PROFILE_INCOMPLETE` | bloqueo (`field`: el primer campo que falta) | 409 | `profile` de un grupo de interés sin `stakeholder_type`, `stakeholder_scope`, `inclusion_reason` o `owner_user_id` |
| `FIELD_REQUIRED` | bloqueo (`field`: `scheduled_at`, `held_at`, `minutes` o `cancel_reason`) | 409 | La transición de una interacción necesita ese campo: programar o reprogramar, registrar la realización, el acta o cancelar |
| `ATTENDANCE_REQUIRED` | bloqueo (`field`: `attended`) | 409 | Registrar la realización sin ningún participante con `attended` |
| `PENDING_QUESTIONS` | bloqueo (`field`: `engagement_question`) | 409 | `close` o `close_light` con preguntas sin respuesta (RN-ENG-005) |
| `MINUTES_REQUIRED` | bloqueo (`field`: `engagement_type`) | 409 | `close_light` de una interacción cuya política exige acta (RN-ENG-004) |
| `OBJECT_NOT_READABLE` | bloqueo (`field`: el objeto que no lee) | 403 | Quien consulta no lee `project_party` (no se verifica `PARTY_REQUIRED`), `engagement_participant` o `engagement_question` (asistencia y preguntas pendientes), o `project` en `close_light` (no se verifica la política de actas: lectura estricta) |

Las reglas de los demás contextos suman sus códigos a esta tabla junto con su guarda.

### 4.2 BC-02 Territorio y SIG

Paquete `sgspe.territory`. Las consultas espaciales cubren los casos de RQ-GIS-002, una por endpoint, con compuerta de
permisos (RQ-GIS-017); las geometrías salen en GeoJSON EPSG:4326 y las áreas, en hectáreas planas (RQ-GIS-009).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/territory/imports` | Vista previa de una importación: lee el archivo de coordenadas o geometrías, lo transforma a 4326, lo valida contra PostGIS y lo concilia con lo registrado; no escribe nada; para `project_component` y `mining_concession` (que al confirmar se vincula al proyecto), quien no es `ADMIN` y no tiene una asignación vigente al proyecto recibe `403` `NOT_ASSIGNED` (`project`) antes de leer el archivo; los predios, que son del tenant, no la exigen | CREATE y UPDATE sobre el objeto destino; CREATE sobre `evidence` desde F3 | multipart: `file`, `target` (`project_component`, `land_parcel` o `mining_concession`), `project`, `sourceCrs` (valor del catálogo "CRS de origen"), `columns` (json `{clave: encabezado}`; claves del portal en camelCase como `drillType` o `depth`, o campos del destino; un valor que empieza con `=` es una constante; sin tipo de componente, `SONDAJE`) → `200 { importBatchId, sha256, target, sourceCrs, rows, valid, invalid[], differences[], outOfZone[] }`, sin geometrías; `differences[]` trae una entrada por fila válida con `outcome` `CREATE`, `UNCHANGED`, `NEW_VERSION` o `UPDATE`; con `Accept` de XLSX, el reporte de diferencias como archivo (hojas Diferencias, Rechazadas y Fuera de zona) | — | F2 (XLSX, CSV, GeoJSON, latitud y longitud); F6 (formatos por GDAL) |
| `POST` | `/api/sgspe/territory/imports/{importBatchId}/confirm` | Confirma: recibe otra vez el mismo archivo y los mismos campos con las decisiones fila por fila (el servidor no guarda nada entre las dos llamadas), exige el SHA-256 de la vista previa, repite la validación y escribe todo en una transacción con candado por destino (y por proyecto en los componentes) y el motivo `importación <id>`; desde F3 guarda el original como `evidence` | Los mismos de la vista previa | multipart: `file`, `target`, `project`, `sourceCrs`, `columns`, `sha256` y `decisions` (json `[{ "row": 7, "action": "DISCARD" }]`, `action` `APPLY` o `DISCARD`; una fila sin decisión aplica su acción propuesta, salvo una de `outOfZone[]`, que solo entra con `APPLY`; una decisión sobre una fila que no es válida es `400`) → `200 { created, newVersions, updated, unchanged, rejected, evidenceId }` | `IK` (desde F3) | F2 |
| `POST` | `/api/sgspe/project-components` | Alta manual de un componente o versión nueva si el código ya existe con otras coordenadas; nunca sobrescribe | CREATE y UPDATE sobre `project_component` | `{ attributes, geometries }` → `201 { component, previousVersion, distanceM }`; las mismas coordenadas (menos de 1 cm) son `409 SAME_COORDINATES`; una geometría que PostGIS no procesa, `400 INVALID_GEOMETRY`; un proyecto que falta es `400 FIELD_REQUIRED` y uno que no es un UUID, `400 INVALID_VALUE`; sin ninguna geometría o con más de una, `400 GEOMETRY_REQUIRED` (`geometries`); un número que no lo es, `400 INVALID_NUMBER`, y fuera de rango, `400 OUT_OF_RANGE` (`attributes.<campo>`); un proyecto que no existe o que no puede leer, `400 PROJECT_NOT_FOUND`; si la versión vigente no tiene geometría, `409 NO_CURRENT_GEOMETRY` (`geometries`), y si otra escritura registró la misma versión mientras tanto, `409 VERSION_CONFLICT` (`attributes.code`); sin asignación vigente al proyecto, `403 NOT_ASSIGNED` antes de cualquier cuenta de PostGIS; toma el mismo candado por proyecto que la confirmación de una importación | `IK` (desde F3) | F2 |
| `PATCH` | `/api/sgspe/project-components/{id}` | Cambia campos descriptivos (estado, campaña) de un componente sin tocar coordenadas; `project_component` es `apiOnly` | UPDATE sobre `project_component` | `{ attributes }` → `200` registro; una geometría en el cuerpo es `400 COORDINATES_NOT_EDITABLE`, y `code`, `project` o un campo de origen, `400 FIELD_NOT_EDITABLE`; solo escribe lo que cambia (un PATCH sin cambios deja el `updatedAt` como estaba); una versión reemplazada (`superseded`) no se cambia: `409 COMPONENT_SUPERSEDED` (`attributes`), antes de comparar `If-Match`, porque RQ-GIS-010 «nunca sobrescribe» y las versiones anteriores son historia de solo consulta (`gis.md` §2.4 y §4.4); sin asignación vigente al proyecto, `403 NOT_ASSIGNED`, aunque el PATCH no cambie nada | `IM` | F2 |
| `POST` | `/api/sgspe/areas-of-influence/{id}/approve` | Aprueba una versión del área de influencia y lleva la aprobada anterior del mismo proyecto y tipo a `superseded`, en una transacción | UPDATE sobre `area_of_influence`, acción declarada `APPROVE` y los roles de las transiciones de comando de WF-O | → `200 { approved, superseded }` | `IM`, `CR` | F2 |
| `GET` | `/api/sgspe/areas-of-influence/{id}/summary` | Tamaño del área: superficie plana en ha, localidades por tipo de área y componentes que cruzan tierras comunales o privadas | READ sobre `area_of_influence` y los objetos resumidos | → `200 { areaHa, localitiesByType[], componentsOnCommunalLand, componentsOnPrivateLand }` | — | F6 |
| `GET` | `/api/sgspe/areas-of-influence/{id}/member-suggestions` | Localidades, comunidades y unidades administrativas que el cálculo espacial sugiere como miembros; no escribe | READ sobre `area_of_influence` y sobre los objetos sugeridos | `?bufferM=` → `200 { suggestions[]: { object, recordId, name, relation, alreadyMember } }` | — | F6 |
| `GET` | `/api/sgspe/territory/zones/{zoneObject}/{id}/records` | Registros de un objeto asociados a una zona (`locality`, `community`, `administrative_unit` o `area_of_influence`), por relación territorial o por geometría | READ sobre el objeto consultado | `?of=commitment&periodStart=&periodEnd=` → página de registros | — | F6 |
| `GET` | `/api/sgspe/territory/concentration` | Concentración de eventos por localidad o por celda, para el mapa de calor | READ sobre el objeto consultado | `?of=grievance&project=&periodStart=&periodEnd=` → `200 { cells[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/nearby-conflicts` | Conflictos locales, incidentes y alertas cercanos al proyecto, ordenados por distancia | READ sobre `project` y los objetos listados | `?distanceKm=` → `200 { items[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/component-parcels` | Componentes que cruzan predios: predios afectados, titulares y longitud de acceso por predio | READ sobre `project_component`, `land_parcel` y `land_right`; los titulares, según el permiso de campo | → `200 { rows[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/component-communal-land` | Componentes que cruzan territorio comunal, con el área afectada | READ sobre `project_component` y `community` | → `200 { rows[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/sensitive-distances` | Distancia de los componentes a los elementos ambientales sensibles | READ sobre `project_component` y `environmental_feature` | → `200 { rows[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/buffer-localities` | Localidades dentro de un buffer de los componentes | READ sobre `project_component` y `locality` | `?bufferM=` → `200 { localities[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/projects/{id}/concession-territories` | Comunidades, sectores comunales y caseríos sobre los que están las concesiones del proyecto | READ sobre `mining_concession`, `community`, `community_sector` y `locality` | → `200 { rows[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/locate` | Punto en polígono: propone la unidad administrativa, la localidad y la comunidad que contienen un punto pulsado en el mapa, para que la persona confirme en el formulario | READ sobre los objetos propuestos | `?lon=&lat=` → `200 { administrativeUnit, locality, community }` | — | F6 |
| `GET` | `/api/sgspe/territory/localities/{id}/card` | Parte territorial de la ficha contextual de una localidad: su identificación, su geometría y sus relaciones territoriales (unidad administrativa, comunidad, sectores y áreas de influencia). Las cifras de la ficha no salen de aquí (ver la nota) | READ sobre `locality` y los objetos relacionados | `?project=` → `200 { locality, geometry, administrativeUnit, communities[], areasOfInfluence[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/communities/{id}/card` | La misma parte territorial para una comunidad, con sus sectores comunales | READ sobre `community` y los objetos relacionados | `?project=` → `200 { community, geometry, administrativeUnit, sectors[], areasOfInfluence[] }` | — | F6 |
| `GET` | `/api/sgspe/territory/layers/{layer}` | Capa temática como GeoJSON autenticado; los grupos de interés salen agregados por localidad, nunca como puntos de personas | READ sobre los objetos de la capa | `?project=&periodStart=&periodEnd=&bbox=` → FeatureCollection | — | F2 (básicas), F6 |
| `GET` | `/api/sgspe/territory/wms` | Proxy WMS autenticado hacia GeoServer, con lista blanca de capas no sensibles por tenant | READ sobre el objeto de la capa, que además debe estar en la lista blanca de la organización (si no, `403`) | GetMap y GetLegendGraphic pasan; GetCapabilities se arma con la lista blanca; GetFeatureInfo y los parámetros `SLD` y `SLD_BODY` son `422` | — | F6 |
| `GET` | `/api/sgspe/territory/wfs` | WFS autenticado e interno para analistas, solo con las capas públicas de la lista blanca; cada extracción queda en `export_log` | READ y acción declarada `EXPORT` sobre el objeto de la capa; CREATE sobre `export_log` | Parámetros WFS de lectura → GeoJSON o GML | — | F6 |
| `POST` | `/api/sgspe/territory/geometry-checks` | Valida una geometría antes de guardarla: motivo de invalidez y reparación propuesta con su cambio de área | Autenticado | `{ geometry, project }` → `200 { valid, reason, repaired, areaHaBefore, areaHaAfter }` | — | F6 |
| `GET` | `/api/sgspe/territory/data-quality` | Listado de geometrías inválidas o fuera de la zona esperada | READ sobre cada objeto listado | `?object=&project=` → página | — | F6 |
| `POST` | `/api/sgspe/territory/exports` | Exporta registros con su geometría en UTM o en latitud y longitud, con el nivel de divulgación elegido, y la registra en `export_log` | READ y acción declarada `EXPORT` sobre el objeto; CREATE sobre `export_log` | `{ object, format, coordinates, filters, disclosureLevel }` → archivo (`geojson`, `csv`, `xlsx`, `kml`, `shp` o `gpkg`) | — | F6 |

- `{layer}` es el identificador `LYR-NN` que fija `gis.md`; el mapa base y las capas de referencia no pasan por aquí
  (RQ-GIS-012, RQ-GIS-013).
- **Capas básicas de F2 (LYR-01 a LYR-16).** Una capa de F6 (LYR-17 en adelante) es `404` `LAYER_NOT_FOUND`. La respuesta es
  `{ type: "FeatureCollection", features[], truncated }` con la forma de wasichai-gis: cada elemento tiene `id`
  (`<registro>:<geometría>`), `geometry` y `properties` con los atributos que quien pide puede leer más `__id`, `__object`,
  `__layer` y `__state` (el estado del workflow, o `null`); en LYR-07 las propiedades traen además `ofProject`, que es
  verdadero si la concesión está vinculada a alguno de los proyectos de `project`. La capa tiene un tope de 5000 elementos
  (`sgspe.territory.layers.max-features`): `truncated` es `true` solo si había más, y entonces se pide por `bbox`. Sin READ
  sobre alguno de los objetos de la capa, `403`. Los filtros de la capa se aplican en SQL: un campo de filtro que quien pide
  no puede leer deja la capa vacía, no es un error.
  - **LYR-13** trae, además del polígono de cada área de influencia en `approved` (`__object`: `area_of_influence`), sus miembros
    (`gis.md` §2.2 y §2.4): un elemento por `area_of_influence_member` con `__object`: `area_of_influence_member`, `__id` del
    miembro, `__target` y `__targetId` (`locality` o `community` y el id de lo que se dibuja), el id `<miembro>:<geometría>` y
    sus atributos legibles (entre ellos `area_of_influence`, `member_condition` y `rationale`). La geometría es el límite de la localidad,
    o su ubicación si no tiene límite, o el territorio de la comunidad, aunque el área no tenga polígono; un miembro cuyo destino
    no tiene geometría, o cuyo campo de relación quien pide no puede leer, no sale. Solo cuentan las versiones `approved`: un borrador o
    una versión reemplazada no aportan ni polígono ni miembros. Las unidades administrativas que son miembros no se dibujan
    (sus límites son LYR-01). Sin READ sobre `area_of_influence_member`, `locality` o `community`, `403`.
  - `project` (puede repetirse, hasta 50) acota las capas de proyecto (LYR-09 a LYR-13) y marca `ofProject` en LYR-07, que muestra todas las
    concesiones; en las demás se acepta y se ignora, porque sus registros no pertenecen a un solo proyecto (`gis.md` §3.3). `periodStart` y `periodEnd` se aceptan y no
    filtran: las capas temáticas con periodo llegan con F6.
  - `bbox=minX,minY,maxX,maxY` en EPSG:4326 es tolerante con lo que manda un mapa alejado: se quitan los espacios, las
    longitudes se recortan a [-180, 180] y las latitudes a [-90, 90] (`-425,-20,275,10` es `-180,-20,180,10`), y si lo
    recortado cubre todo el globo no se filtra. Un mínimo mayor que el máximo después de recortar, o algo que no son cuatro
    números, es `400` `INVALID_BBOX`. Recortar no es envolver: un bbox que cruza el antimeridiano (`170,-10,190,10`) pierde
    el tramo que pasa de 180 y no se parte en dos (irrelevante para el Perú, de -81 a -68). En LYR-13, `bbox` acota los miembros
    por la geometría que se dibuja.
  - Errores (`errors[0].message` lleva el código y `errors[0].field` el campo): `404` `LAYER_NOT_FOUND` (`layer`), `400`
    `INVALID_BBOX` (`bbox`), `400` `INVALID_PROJECT` (`project` no es un UUID) y `400` `TOO_MANY_PROJECTS` (más de 50
    proyectos); un parámetro desconocido es `422`.
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
  predios y concesiones es una actualización con motivo.
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
  vista previa.
- **El reporte XLSX** se pide con `Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,
  application/problem+json`. Sin `application/problem+json`, un error (400, 403 o 409) sale con su estado y el cuerpo
  vacío, sin `errors[]`.
- **Conteos de la confirmación.** `rejected` suma las filas inválidas, las descartadas con `DISCARD` (también una
  `UNCHANGED`) y las de `outOfZone[]` sin `APPLY`; `unchanged`, las `UNCHANGED` que se aplicaron, que no escriben nada.
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
| `POST` | `/api/sgspe/stakeholders/{id}/assessments` | Registra una evaluación: valida los indicadores de 1 a 5, calcula índices, grupo de influencia y prioridad, toma la evaluación anterior como línea base, actualiza los valores vigentes del grupo de interés (el último valor informado de cada uno) y, en la primera, lo lleva a `assessed` | CREATE sobre `stakeholder_assessment`, UPDATE sobre `stakeholder` (con acceso a `stance`) y el rol de la transición de WF-B a `assessed` | `{ attributes }` → `201 { assessment, stakeholder, warnings[] }` | `IK` (desde F3), `IM?`, `CR` | F2 |
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
| `POST` | `/api/sgspe/engagement-questions/{id}/referral` | Deriva una pregunta pendiente a una consulta, una solicitud o una queja, creada y vinculada (`origin_question`) en un solo comando | CREATE sobre el objeto destino; READ sobre `engagement_question` | `{ target, attributes }` con `target` igual a `inquiry`, `stakeholder_request` o `grievance` → `201 { question, created }` | `IK`, `CR` | F4 |

### 4.5 BC-05 Compromisos

Paquete `sgspe.commitment`. Aprobar, iniciar, bloquear, registrar el cumplimiento, verificar, cerrar y cancelar son
transiciones de la API genérica, con sus guardas (`CommitmentWriteGuard`, `SupportPolicyGuard`) y su motivo (RF-COM-002);
aprobar por encima del tope de la campaña exige además `APPROVE_OVER_CAP` (RN-INV-003).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/commitments/{id}/reschedules` | Reprograma: crea `commitment_reschedule` con la fecha anterior y la nueva y cambia `due_date`, en una transacción | CREATE sobre `commitment_reschedule`, UPDATE sobre `commitment` | `{ attributes: { new_due_date, reschedule_reason, agreed_with_stakeholder } }` → `201 { reschedule, commitment }` | `IK`, `IM`, `CR` | F3 |
| `POST` | `/api/sgspe/commitments/{id}/deliveries` | Solo captura datos: crea la entrega con su conformidad y sus vínculos de evidencia (acta de entrega, fotos) en una transacción, y recalcula `executed_amount`; no cambia el estado del compromiso | CREATE sobre `delivery`, UPDATE sobre `commitment` y sobre las `evidence` vinculadas | `{ attributes, geometries, evidence[] }` → `201 { delivery, commitment }` | `IK`, `CR` | F3 |
| `PATCH` | `/api/sgspe/deliveries/{id}` | Corrige los datos de una entrega (`delivery` es `apiOnly`) y recalcula los montos de su compromiso. Responde `409` si su compromiso está en `fulfilled`, `verified` o `closed`, o su aporte en `delivered` o `closed`, porque una transición ya se apoyó en esa entrega; un compromiso cumplido se corrige después de devolverlo a ejecución con `reopen_execution` | UPDATE sobre `delivery` y sobre su compromiso o aporte | `{ attributes }` → `200 { delivery, commitment }` | `IM`, `CR` | F3 |
| `POST` | `/api/sgspe/commitment-candidates` | Alta humana de un candidato a compromiso, con su evidencia | CREATE sobre `commitment_candidate` | `{ attributes }` → `201` registro | `IK` | F3 |
| `POST` | `/api/sgspe/commitment-candidates/{id}/accept` | Acepta: crea el compromiso en `draft` con los valores que la persona revisó, lo vincula (`created_commitment`) y lleva el candidato a `accepted` | UPDATE sobre `commitment_candidate`, CREATE sobre `commitment` y el rol de la transición de WF-K a `accepted` | `{ attributes }` → `201 { commitment, candidate }` | `IK`, `IM`, `CR` | F3 (candidatos de IA en F7) |
| `POST` | `/api/sgspe/commitment-candidates/{id}/duplicate` | Marca el candidato como duplicado de un compromiso existente (`duplicate_of`) | UPDATE sobre `commitment_candidate` y el rol de la transición de WF-K a `duplicate` | `{ duplicateOf }` → `200 { candidate }` | `IM`, `CR` | F3 |

- **Registrar la entrega no cumple el compromiso.** Después de capturarla, una persona aplica la transición explícita
  `register_fulfillment` de WF-D por la API genérica, con motivo; `CommitmentWriteGuard` la rechaza si no existe una entrega
  con conformidad y su evidencia o si queda una dependencia bloqueante abierta (RN-COM-001, RF-COM-004, ASM-009)
  [domain-model §7.2]. Si el recurso es MATERIAL, la evidencia es el acta de entrega y la foto; si es NO MATERIAL,
  `conformity_given` verdadero, `conformity_by` informado y al menos una evidencia vinculada de tipo ACTA, CARTA u OTRO.
- La fecha de vencimiento de un compromiso aprobado solo cambia con una reprogramación (RF-COM-003); el `PUT` genérico que la
  toque es un `400`.
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

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/grievances/{id}/reclassify` | Reclasifica una queja como consulta o solicitud: crea el registro del tipo nuevo, enlazado al original, y cierra el original | CREATE sobre el objeto destino, UPDATE sobre `grievance` y el rol de la transición de cierre | `{ targetType, attributes }` → `201 { original, created }` | `IK`, `IM`, `CR` | F4 |
| `POST` | `/api/sgspe/inquiries/{id}/reclassify` | Reclasifica una consulta como queja o solicitud y cierra la original | CREATE sobre el objeto destino, UPDATE sobre `inquiry` y el rol de la transición de cierre | Igual | `IK`, `IM`, `CR` | F4 |
| `POST` | `/api/sgspe/stakeholder-requests/{id}/reclassify` | Reclasifica una solicitud como queja o consulta y cierra la original | CREATE sobre el objeto destino, UPDATE sobre `stakeholder_request` y el rol de la transición de cierre | Igual | `IK`, `IM`, `CR` | F4 |
| `POST` | `/api/sgspe/stakeholder-requests/{id}/accept` | Acepta la solicitud: crea un compromiso en `draft` o, desde F5, un aporte social, lo vincula (`created_commitment` o `created_contribution`) y lleva la solicitud a `accepted` | UPDATE sobre `stakeholder_request`, CREATE sobre `commitment` o `social_contribution`, el rol de la transición de WF-E3 a `accepted` y, si vincula solicitantes, UPDATE sobre `stakeholder` (basta el de solo vínculo, `u` en `security.md`) | `{ creates, response, respondedAt, attributes }`: `creates` igual a `commitment` o `social_contribution`; `response` (texto) y `respondedAt` (fecha y hora) son la respuesta a la solicitud, y `attributes`, los valores del registro que se crea → `201 { request, commitment }` o `201 { request, contribution }` | `IK`, `IM`, `CR` | F4 (aporte en F5) |
| `GET` | `/api/sgspe/attention/report` | Reporte unificado de quejas, consultas y solicitudes: totales por tipo, estado, canal y categoría, y la lista | READ sobre los tres objetos; lo que no se puede leer sale como no disponible | `?project=&periodStart=&periodEnd=&channel=&category=` → `200 { totals, byType, page }` | — | F4 |

- El registro nuevo de una reclasificación guarda el vínculo `reclassified_from_*` con el original (RF-GRV-007), y el
  original pasa a `closed` con la transición de comando `close_reclassified_*` que fija `workflows.md`: el planteamiento sigue
  vivo en el registro nuevo. Quien reclasifica necesita también el rol de esa transición.
- **La reclasificación conserva la fecha de recepción.** El registro nuevo copia `received_at` del primer registro de la
  cadena, y sus plazos corren desde esa fecha, porque el grupo de interés planteó el caso una sola vez (ASM-072).
- Una QCS anónima no lleva persona ni grupo de interés (`AttentionWriteGuard`, RF-GRV-001).
- **Aceptar una solicitud es responderla.** El endpoint escribe `response` y `responded_at` en la `stakeholder_request`, en la
  misma transacción que la transición a `accepted` y el alta del compromiso o del aporte. Si falta `response` o
  `respondedAt`, `AttentionWriteGuard` rechaza la transición con un `400` que nombra el campo y no se escribe nada. Con
  `responded_at` informado, la solicitud cuenta como absuelta (RF-GRV-004, RQ-DSH-023) (`workflows.md`, WF-E3).

### 4.7 BC-08 Riesgos sociales

Paquete `sgspe.risk`.

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/social-risks/{id}/assessments` | Revisión del riesgo: valida probabilidad e impacto de 1 a 5, calcula puntaje, nivel y tendencia, crea `risk_assessment` y actualiza los valores vigentes (o los residuales); la primera revisión lleva el riesgo a `assessed` | CREATE sobre `risk_assessment`, UPDATE sobre `social_risk` y el rol de la transición de WF-F a `assessed` | `{ attributes: { assessment_date, probability, impact, residual, notes } }` → `201 { assessment, risk }` | `IK`, `CR` | F4 |

El nivel sale de los umbrales del proyecto (RN-RSK-002, ASM-004). Mitigar, monitorear y cerrar son transiciones genéricas con
`RiskWriteGuard` (RN-RSK-003).

### 4.8 BC-10 Inversión social y programas

Paquete `sgspe.investment`. El tope de apoyo y los lineamientos los aplican las guardas al aprobar (RN-INV-003, desde F3).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/social-contributions/{id}/deliveries` | Solo captura datos: crea la entrega del aporte y sus vínculos de evidencia (acta de recepción, fotos y comprobante) en una transacción; no cambia el estado del aporte | CREATE sobre `delivery`, UPDATE sobre `social_contribution` y sobre las `evidence` vinculadas | `{ attributes, geometries, evidence[] }` → `201 { delivery, contribution }` | `IK`, `CR` | F5 |

Llevar el aporte a `delivered` es la transición humana y explícita `register_delivery` de WF-P, por la API genérica y con
motivo; `InvestmentWriteGuard` la rechaza si no existe una entrega con conformidad y su evidencia: acta de recepción, foto y
comprobante (RF-INV-001) [domain-model §7.2]. Aprobar un aporte por encima del tope de la campaña exige `APPROVE_OVER_CAP`
(RN-INV-003). Las correcciones de una entrega usan `PATCH /api/sgspe/deliveries/{id}` (§4.5).

### 4.9 BC-12 Evidencias y documentos

Paquete `sgspe.evidence`. `evidence` es `apiOnly` e inmutable en su archivo: clave, hash, tamaño y tipo no cambian, no se
borra y se reemplaza con una versión nueva (RF-DOC-001). Los documentos emitidos de un registro usan la API de wasichai (§2).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/evidence` | Sube un archivo: calcula SHA-256 y tamaño, lee los EXIF, lo guarda en el almacenamiento de objetos y crea `evidence`; puede vincularlo y reemplazar una versión anterior (`replaces`) | CREATE sobre `evidence`; UPDATE sobre cada registro vinculado | multipart: `file`, `attributes`, `links[]: { object, recordId }` → `201` registro con `sha256` | `IK`; `CR` si un vínculo toca un objeto `requiresReason` | F3 |
| `GET` | `/api/sgspe/evidence/{id}/content` | Descarga el archivo en streaming | READ sobre `evidence`; para las clasificaciones CONFIDENCIAL y DATOS PERSONALES, además `DOWNLOAD_RESTRICTED` (RF-DOC-003) | → archivo con `ETag` igual al SHA-256 | — | F3 |
| `PATCH` | `/api/sgspe/evidence/{id}` | Cambia los datos descriptivos (título, clasificación, fecha, autor, consentimiento de fotos); nunca el archivo | UPDATE sobre `evidence`; `EDIT_ANY` si la evidencia la subió otra persona; bajar la clasificación de una evidencia CONFIDENCIAL o DATOS PERSONALES exige además `DOWNLOAD_RESTRICTED` (ASM-071), para que reclasificar no abra la descarga | `{ attributes }` → `200` registro | `IM`; `CR` si cambia la clasificación | F3 |
| `POST` | `/api/sgspe/evidence/{id}/links` | Vincula la evidencia con un registro de uno de los destinos de `evidence_<objeto>` | UPDATE sobre los dos registros; `EDIT_ANY` sobre `evidence` si la evidencia la subió otra persona (`EditAnyGuard`) | `{ object, recordId }` → `204` | `CR` si el destino es `requiresReason` | F3 |
| `DELETE` | `/api/sgspe/evidence/{id}/links/{object}/{recordId}` | Desvincula | UPDATE sobre los dos registros; `EDIT_ANY` sobre `evidence` si la evidencia la subió otra persona (`EditAnyGuard`) | → `204` | `CR` si el destino es `requiresReason` | F3 |

Una foto de personas sin consentimiento no sale en una salida EXTERNO (RNF-PRIV-004, RQ-REP-014).

### 4.10 BC-14 Notificaciones

Paquete `sgspe.notification`. La bandeja lee la tabla fija `notification`, que no es un Custom Object: por eso las propiedades
de un aviso van en camelCase y sin `attributes` (ASM-012, UPS-11).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `GET` | `/api/sgspe/notifications` | La bandeja de quien llama, de la más reciente a la más antigua | Autenticado; solo sus avisos | `?unread=true&page=&size=` → página de `{ id, notificationType, sourceObject, sourceRecordId, title, body, createdAt, readAt }` | — | F3 |
| `GET` | `/api/sgspe/notifications/unread-count` | Cantidad de avisos sin leer | Autenticado | → `200 { count }` | — | F3 |
| `POST` | `/api/sgspe/notifications/{id}/read` | Marca un aviso como leído | Autenticado; un aviso de otra persona es `404` | → `204` | — | F3 |
| `POST` | `/api/sgspe/notifications/read-all` | Marca como leídos todos los avisos de quien llama | Autenticado | → `204` | — | F3 |

El envío por correo (`notification_delivery`) no tiene endpoint: lo hace un proceso en segundo plano (RF-NTF-001).

### 4.11 BC-15 Indicadores y tableros

Paquete `sgspe.dashboard`. Los indicadores se calculan en vivo con `RecordService`, sin vistas materializadas, con el mismo
código que usan los entregables (RQ-DSH-031, RQ-DSH-034). El portal los guarda en caché un minuto.

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `GET` | `/api/sgspe/dashboards/{dashboard}` | Los indicadores de un tablero con los filtros comunes, en una sola llamada por pantalla | READ sobre los objetos de cada indicador; un indicador sin permiso sale con `available: false` | `?project=&periodStart=&periodEnd=&administrativeUnit=&locality=&community=&areaOfInfluence=&explorationCampaign=&scope=` → `200 { dashboard, filters, computedAt, indicators[] }` | — | Según el tablero |
| `GET` | `/api/sgspe/dashboards/indicators/{code}` | Un indicador con sus desgloses, su serie mensual y su tabla de datos | Igual | Los mismos filtros más `breakdown` → `200 { code, label, available, value, numerator, denominator, unit, breakdowns[], series[], unavailableReason, missingFields[], warnings[], ignoredFilters[] }` | — | Según el indicador |
| `GET` | `/api/sgspe/dashboards/indicators` | Catálogo de indicadores con su disponibilidad para quien llama, para mostrar u ocultar tableros y zonas | Autenticado | → `200 [{ code, label, dashboard, available }]` | — | F2 |
| `POST` | `/api/sgspe/indicators/{id}/measurements` | Cierra un periodo: congela la cifra oficial en `indicator_measurement`. Responde `409` para los indicadores que nunca se congelan (`dashboards.md` §10): KPI-STK-02, KPI-STK-05 y KPI-STK-06, que leen la postura, y KPI-TER-01, que no tiene un valor propio | CREATE sobre `indicator_measurement` | `{ project, attributes: { period_start_date, period_end_date } }` → `201` registro | `IK` | F8 |

- `{code}` es el identificador `KPI-*` que define `dashboards.md`; `scope` toma los valores del catálogo "Ámbito del grupo de
  interés" (RQ-DSH-001). `dashboards.md` fija qué tableros e indicadores exigen un solo `project` (el tablero `project` y
  KPI-STK-03). El mapa de calor de riesgos (KPI-RSK-02) acepta varios, pero solo con uno muestra el nivel de cada celda.
- Sin `project`, el filtro por defecto (RF-PRJ-005) es: para el `ADMIN`, todos los proyectos; para los demás, sus
  asignaciones vigentes (`active` y, si tiene fechas, hoy en Lima entre ellas) o, si no tiene ninguna, todos los proyectos que
  puede leer (`dashboards.md` §3.1). Es una comodidad, no un control de acceso, que sigue RQ-SEC-006. `filters.project` de la
  respuesta trae los proyectos efectivos.
- Un tablero aparece en el portal si quien llama puede leer al menos uno de sus indicadores, según el catálogo (RF-IAM-002).
- Un indicador que no se puede calcular para quien llama responde `available: false`, la constante `unavailableReason` y
  `missingFields`, nunca cero (RQ-DSH-032):
  - `FIELD_NOT_READABLE`: falta un campo que el indicador lee; `missingFields` trae `objeto.campo`.
  - `OBJECT_NOT_READABLE`: falta READ sobre un objeto que el indicador lee; `missingFields` trae sus campos.
  - `SINGLE_PROJECT_REQUIRED`: el indicador exige un solo proyecto (KPI-STK-03); `missingFields` es `["project"]`.
- Forma del indicador: las claves nulas se omiten, salvo `value`, que va siempre en un indicador disponible (`null` si no hay
  cifra, como un porcentaje sin denominador) y nunca en uno no disponible. `unit` es la palabra en español que muestra la
  interfaz (`"compromisos"`), la misma de `dashboards.md`.
- `ignoredFilters` nombra los filtros pedidos que el indicador acepta y no aplica, para que nunca den un cero silencioso
  (ruling P27): los KPI-STK ignoran `community`, porque `stakeholder` no tiene comunidad, y en F2 todos ignoran
  `explorationCampaign`, que solo filtra compromisos e inversión social (`dashboards.md` §3.1). Sin filtros ignorados, la clave
  no viaja.
- En el catálogo, `dashboard` es `stakeholders`, `engagement` o `null`: KPI-STK-04, KPI-STK-05 y KPI-STK-07 no tienen zona en
  un tablero de F2 y solo tienen detalle (`dashboards.md` §11).
- La etiqueta de un punto de KPI-STK-03 que es una persona es su nombre si quien llama puede leerlo; si no, «cargo,
  organización» de su cargo vigente o, sin cargo, «Persona». La clave `stance` del punto se omite a quien no lee la postura.
- El contacto de KPI-STK-04, KPI-STK-05 y KPI-STK-07 (`dashboards.md` §3.3) se arma una vez por pedido y lo usan los tres: el
  territorio y el ámbito filtran al grupo de interés vigente, no a la interacción.
- La lectura es acotada (`dashboards.md` §6.2): 20 000 filas por conjunto leído y 200 interacciones cuyas relaciones M2M
  (`engagement_stakeholder`, `engagement_topic`) se leen. Pasarse no recorta la cifra: el pedido entero responde `400`
  `TOO_MANY_RECORDS` y hay que acotar los proyectos, el periodo o el territorio. Un `ADMIN` sin filtros puede recibirlo; el
  portal lo muestra como «acote los filtros».
- Errores, con el código en `errors[0].message` y el campo en `errors[0].field` (§3.3): `404` `DASHBOARD_NOT_FOUND`
  (`dashboard`: un tablero que no existe o no está en esta fase); `404` `INDICATOR_NOT_FOUND` (`code`); `400`
  `INVALID_BREAKDOWN` (`breakdown`: un desglose que el indicador no ofrece; sobre un indicador no disponible, `breakdown` se
  ignora); `400` `TOO_MANY_RECORDS` (`project`). Un filtro mal formado (`project` u otro UUID, una fecha, `periodStart`
  posterior a `periodEnd`, `scope` fuera del catálogo) es `400` de validación que nombra el parámetro, y un parámetro
  desconocido es `422`.
- El cierre de periodo no congela los indicadores que leen campos restringidos, como los de postura: `indicator_measurement`
  lo leen roles sin acceso a esos campos, y una cifra congelada saldría por la API genérica (`dashboards.md` §10).

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
      "breakdowns": [{ "by": "daysOverdue", "rows": [{ "key": "1-7", "value": 4 }, { "key": "8-15", "value": 3 }] }],
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

Paquete `sgspe.deliverable`. Enviar a revisión, observar, aprobar, anular y abrir una revisión nueva son transiciones de la API
genérica; `DeliverableWriteGuard` rechaza la aprobación si el borrador no está LISTO o si una sección cambió después de
congelarse el modelo (RQ-REP-015, RQ-REP-026). Las secciones narrativas se editan por la API genérica (RQ-REP-032).

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `POST` | `/api/sgspe/deliverables/{id}/drafts` | Pide un borrador, solo en `draft` u `observed` (si no, `409`): escribe únicamente `generation_requested_at`; el trabajador de generación lo toma después | UPDATE sobre `deliverable` y acción declarada `GENERATE` | → `202 { deliverable }` con `Location` del estado | `CR` | F8 |
| `GET` | `/api/sgspe/deliverables/{id}/draft` | Estado del último borrador y niveles de divulgación preparados; de cada versión candidata solo informa su SHA-256 | READ sobre `deliverable` | → `200 { status, requestedAt, generatedAt, modelSha256, pending, levels[]: { level, candidateSha256 }, warnings[] }` | — | F8 |
| `GET` | `/api/sgspe/deliverables/{id}/draft/content` | Descarga un archivo del último borrador de un nivel, siempre con la marca de agua BORRADOR, y registra la descarga en `export_log` | READ y `DOWNLOAD_RESTRICTED` sobre `deliverable`; CREATE sobre `export_log` | `?level=` (nivel de divulgación) y `?format=pdf` o, solo en INTERNO, `?format=docx` → archivo; el DOCX de un borrador EXTERNO o PUBLICO es `404` | — | F8 |
| `POST` | `/api/sgspe/deliverables/{id}/issue` | Emite: verifica `approved`, borrador LISTO, el mismo `draft_model_sha256` que se revisó, ningún mapa fallido y ninguna sección cambiada después de aprobar (si cambió alguna, `409` con la lista de secciones cambiadas, y la persona aplica `return_to_draft` de WF-H y pide un borrador nuevo; `deliverables.md` §8.4); copia el modelo, sus recursos y las versiones candidatas a la carpeta de la versión y, en una transacción, lleva el entregable a `issued`, crea una `deliverable_version` por nivel (la INTERNO como base y las demás con `derived_from`), apunta a la base y escribe una fila de `export_log` por versión | UPDATE sobre `deliverable`, CREATE sobre `deliverable_version` y `export_log`, acción declarada `ISSUE` y el rol de la transición de comando de WF-H | `{ draftModelSha256, checklistConfirmed }` → `201 { deliverable, versions[] }` | `IK`, `IM`, `CR` | F8 |
| `GET` | `/api/sgspe/deliverables/{id}/versions/{versionNumber}/content` | Descarga una versión emitida y registra la descarga en `export_log` | INTERNO: READ y `DOWNLOAD_RESTRICTED` sobre `deliverable`. EXTERNO y PUBLICO: READ y `EXPORT` sobre `deliverable`. En los dos casos, CREATE sobre `export_log` | `?format=pdf` o, solo en INTERNO, `?format=docx` → archivo con `ETag` igual al SHA-256; el DOCX de una versión EXTERNO o PUBLICO y `?format=model` son `404` | — | F8 |
| `POST` | `/api/sgspe/deliverable-templates` | Sube la plantilla Word de la organización y la valida: sin macros, con los estilos requeridos, solo fuentes de la lista permitida, sin objetos OLE ni vínculos externos y con un tamaño máximo | `MANAGE_METADATA` sobre `deliverable` | multipart `file` → `201 { templateKey, sha256, warnings[] }` | `IK` | F8 |
| `GET` | `/api/sgspe/deliverable-templates` | Plantillas de la organización | READ sobre `deliverable` | → `200 [{ templateKey, fileName, uploadedAt, sha256 }]` | — | F8 |

- **La emisión no genera nada.** El trabajador, en segundo plano, prepara desde el mismo modelo congelado el borrador y la
  versión candidata sin marca de agua de cada nivel de divulgación del tipo, con los mapas de GeoServer ya incrustados: DOCX
  y PDF/A-2b en INTERNO, y solo PDF/A-2b en EXTERNO y PUBLICO. Emitir solo copia y registra. Por eso la petición no llama a
  Gotenberg ni a GeoServer, y reemitir nunca lee datos vivos (RQ-REP-026, ASM-014) (`deliverables.md`).
- **Nada sin marca antes de emitir.** Ninguna ruta sirve una versión candidata: todo lo no emitido lleva la marca BORRADOR
  (RQ-REP-021). Se revisa el borrador, que tiene el mismo contenido, y de la candidata solo se muestra su SHA-256.
- **Solo PDF fuera de INTERNO.** Los borradores y las versiones EXTERNO y PUBLICO se sirven solo en PDF, y pedir su DOCX
  responde `404`; los de INTERNO tienen DOCX y PDF.
- **El modelo congelado no se sirve.** El `model.json` de un borrador o de una versión es de uso interno: sirve para volver
  a generar y para verificar los SHA-256, y ninguna ruta lo entrega en el MVP: `?format=model` nombra un archivo que existe
  pero no se sirve, así que responde `404` y no el `422` de un valor desconocido. Es el modelo previo a la divulgación; si
  algún día se sirviera, exigiría READ y `DOWNLOAD_RESTRICTED` en todo nivel.
- `GENERATE`, `APPROVE`, `ISSUE` y `DOWNLOAD_RESTRICTED` sobre `deliverable` se conceden solo a roles que pueden leer los
  campos sensibles, porque el contenido de un entregable no se filtra por permisos de campo (GAP-024); los roles concretos
  están en `security.md`.
- Ningún trabajo aplica la transición a `issued`: ocurre en la petición de la persona que emite [domain-model §8.3].
- Si el tipo prepara el nivel EXTERNO, emitir exige `checklistConfirmed: true` (RQ-REP-033). Los niveles toman los valores
  del catálogo "Nivel de divulgación" y se aplican al modelo antes de generar (RQ-REP-014); el código y la revisión siguen
  ASM-027.
- Si la transacción falla, los archivos copiados quedan sin referencia y los borra la retención de borradores (RQ-REP-027).

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
| `attention-register` | Quejas, consultas y solicitudes | `grievance`, `inquiry` y `stakeholder_request` | F4 |
| `risk-register` | Matriz de riesgos con su última revisión | `social_risk` | F4 |

**`stakeholder-register` (F2).**

- **Cuerpo.** `{ filters, disclosureLevel, checklistConfirmed }`. `disclosureLevel` es `INTERNO`, `EXTERNO` o `PUBLICO`.
  `filters` es `{ project: string[], scope, stakeholderType, influenceGroup, locality, includeRetired }`: `project` y `locality`
  son UUID (o listas de UUID, sin repetidos), `scope`, `stakeholderType` e `influenceGroup` son valores de su catálogo e
  `includeRetired` es booleano. Una propiedad desconocida del cuerpo o de `filters` es `400` que la nombra, y un parámetro de
  consulta, `422` (ASM-054).
- **Proyectos.** Una lista vacía (o sin `project`) es «mis proyectos»: las asignaciones vigentes de quien exporta, hoy en
  America/Lima; el `ADMIN`, todos. Con **un solo** proyecto (los repetidos cuentan una vez), `export_log.project` lleva ese
  proyecto y quien no es `ADMIN` necesita una asignación vigente a él: si no, `403` `NOT_ASSIGNED`. Con dos o más proyectos
  explícitos no se exige asignación y `export_log.project` queda vacío: con la opción A (`security.md` §9.2) la lectura es de
  todo el tenant y la asignación no es una frontera de confidencialidad, solo la exige la escritura de `export_log` acotada
  por proyecto.
- **Contenido.** Se lee como quien exporta: un campo que no puede leer sale `no disponible`. `INTERNO` nombra a las
  personas que quien exporta puede leer; `EXTERNO` las cita por código `GI-` (un seudónimo, `deliverables.md` §9.1) y por
  cargo, y exige `checklistConfirmed: true`; `PUBLICO` solo trae conteos por grupo de influencia y postura y por ámbito, sobre la grilla
  completa de los valores del catálogo (con las combinaciones sin ningún grupo de interés; un valor fuera del catálogo cuenta como
  `no disponible`) y sin totales; todo conteo menor que 5, el cero incluido, sale con la marca neutra `reservado`, que no dice qué
  valor esconde, y lo mismo una celda visible cuando ocultarla hace falta para que restar no despeje otra (ASM-088). Nunca sale el documento de identidad.
- **Errores.** Con su código en `errors[0].message` (ruling P15): `400` `INVALID_DISCLOSURE_LEVEL` (`disclosureLevel`),
  `CHECKLIST_REQUIRED` (`checklistConfirmed`), `INVALID_FILTER` (`filters.project`, `filters.locality`, `filters.scope`,
  `filters.stakeholderType`, `filters.influenceGroup` o `filters.includeRetired`) y `TOO_MANY_RECORDS` (`filters.project`);
  `403` `NOT_ASSIGNED`, `404` `EXPORT_NOT_FOUND` (una exportación que aún no existe en la fase) y `409` `CATALOGUE_UNAVAILABLE` (`PUBLICO`
  con el catálogo de un campo vacío; `field` es el campo). Sin READ ni `EXPORT` sobre
  `stakeholder`, o sin CREATE sobre `export_log`, `403` sin código.
- **Tope.** La lectura es acotada: `sgspe.exports.max-rows` (20 000 por defecto) por conjunto leído. Pasarse es
  `TOO_MANY_RECORDS`: hay que acotar los proyectos o los filtros. No se recorta en silencio.
- **`export_log`.** Una fila por exportación, con `export_kind = EXPORTACION`, escrita como quien exporta antes de entregar
  el archivo; si algo falla antes, no hay archivo ni fila. `exported_at` es el instante en que se generó el archivo, no el de
  su entrega, y `row_count` cuenta grupos de interés, no filas del archivo (en `PUBLICO`, los contados en los agregados). `filters_description` (`export_log` no tiene otro campo de filtros) lleva la lista completa de proyectos y el resto de los filtros.

### 4.14 BC-18 IA y conocimiento

Paquete `sgspe.ai`, desde F7. Ningún endpoint de IA escribe datos de dominio: guarda `ai_interaction` y sugerencias o
candidatos, y solo la aceptación de una persona ejecuta el comando (RQ-AI-001, RQ-AI-003). Si la IA no está activada para la
organización o su proveedor no responde, los tres endpoints que llaman a un modelo (`runs`, `assistant/questions` y
`knowledge/questions`) responden `503` (RQ-AI-009, ASM-015); `GET /api/sgspe/ai/status` lo informa, y aceptar una sugerencia
o un candidato ya guardado sigue funcionando sin proveedor. Usar la IA exige poder crear `ai_interaction` y el objeto de
salida. Los casos, la seudonimización y los flujos los detalla `ai-architecture.md`.

| Método | Ruta | Propósito | Permiso o acción declarada | Cuerpo y respuesta | Encabezados | Fase |
|---|---|---|---|---|---|---|
| `GET` | `/api/sgspe/ai/status` | Si la organización tiene la IA activada, si el proveedor está disponible y qué casos se ofrecen | Autenticado | → `200 { enabled, providerAvailable, useCases[] }` | — | F7 |
| `POST` | `/api/sgspe/ai/runs` | Ejecuta un caso de IA (AI-01, AI-02, AI-03, AI-04, AI-07 o AI-08) sobre una fuente, con texto seudonimizado, y guarda sus sugerencias o candidatos; los borradores de sección de AI-04 llegan en F8, con `deliverable_section` (RQ-AI-012) | READ sobre la fuente; CREATE sobre `ai_interaction` y sobre `ai_suggestion` o `commitment_candidate`; READ de los nombres de `person`, que la seudonimización necesita (si no, `403` antes de llamar al modelo) | `{ useCase, target: { object, recordId } }` → `201 { interaction, suggestions[], candidates[] }` | `IK` | F7 |
| `POST` | `/api/sgspe/ai/assistant/questions` | AI-05: pregunta al asistente de solo lectura de wasichai-agent, como la persona que pregunta, y registra la interacción. Mientras no exista UPS-14, se ofrece solo a roles sin acceso a campos restringidos (hoy, `ANALYST`), una decisión del checkpoint (`security.md` §15) | CREATE sobre `ai_interaction` | `{ question }` → `200 { answer, steps[], truncated, interaction }` | — | F7 |
| `POST` | `/api/sgspe/ai/knowledge/questions` | AI-06: responde con citas sobre los documentos que quien pregunta puede leer; sin fragmento que pase los filtros, dice que no hay evidencia | CREATE sobre `ai_interaction`; READ sobre `evidence` y, para fragmentos de evidencias CONFIDENCIAL o DATOS PERSONALES, `DOWNLOAD_RESTRICTED`; READ de los nombres de `person` (si no, `403` antes de llamar al modelo) | `{ question, project }` → `200 { answer, citations[]: { evidenceId, chunkIndex, quote }, interaction }` | — | F7 |
| `POST` | `/api/sgspe/ai-suggestions/{id}/accept` | Acepta una sugerencia: ejecuta el comando de dominio con la autorización de quien acepta y lleva la sugerencia a `accepted`. Solo ejecuta lo que permite la lista cerrada de `ai-architecture.md` §4.2 (tipo, objeto destino y campo o comando); todo lo demás, y todo campo derivado u objeto `apiOnly`, se rechaza | UPDATE sobre `ai_suggestion`, el permiso del comando que ejecuta y el rol de la transición de WF-K a `accepted` | `{ attributes }` con los valores revisados → `200 { suggestion, result }` | `IK`, `IM`, `CR` | F7 |

Los candidatos a compromiso que produce la IA se aceptan o se marcan como duplicados con los endpoints de §4.5. Rechazar una
sugerencia o un candidato, o marcar como duplicada una sugerencia, son transiciones de la API genérica, siempre humanas
(RQ-AI-015).

**Las salidas de la IA solo llevan marcadores.** `suggested_value` y `evidence_quote` de `ai_suggestion`, y `proposed_text`,
`evidence_quote` y `beneficiaries_text` de `commitment_candidate`, se guardan seudonimizados: una persona registrada queda
como `[PERSONA:<id>]`; los nombres de participantes, beneficiarios y personas no registradas, como `[PERSONA]` sin id; y lo
detectado por formato, como marcador sin valor (`[DOCUMENTO]`, `[TELEFONO]`, `[CORREO]`). La respuesta de `runs`, la API
genérica y los endpoints de candidatos devuelven solo esos marcadores. Un permiso de campo oculta `evidence_quote` a los
roles sin `DOWNLOAD_RESTRICTED` sobre `evidence`, también en la API genérica (`security.md` §7.9); además, el portal
re-identifica al mostrar, con los permisos de quien mira, y la cita solo la ve quien puede descargar la evidencia fuente
(READ y, si es CONFIDENCIAL o DATOS PERSONALES, `DOWNLOAD_RESTRICTED`). Aceptar escribe los valores que la persona revisó,
así que ningún marcador llega a un registro de dominio (`ai-architecture.md` §12.2, `security.md` §7.8).

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
| F3 | Reprogramaciones y entregas de compromisos; `PATCH` de entregas; candidatos (alta, aceptación y duplicado); evidencias (subida, descarga, `PATCH` y vínculos); bandeja de notificaciones; tablero `commitments`; exportación `commitment-matrix` |
| F4 | Derivación de preguntas; reclasificación y aceptación de QCS; reporte unificado de QCS; revisiones de riesgo; tableros `attention` y `risks`; exportaciones `attention-register` y `risk-register` |
| F5 | Entregas de aportes sociales; aceptación de solicitudes como aporte; tablero `investment` |
| F6 | Sugerencias de miembros y resumen del área de influencia, consultas espaciales, punto en polígono, fichas territoriales, proxies WMS y WFS, validación y calidad de geometrías, exportaciones geográficas, formatos de importación por GDAL; tablero `territory` |
| F7 | Estado de la IA, ejecución de casos, preguntas al asistente y sobre los documentos, aceptación de sugerencias |
| F8 | Borradores, emisión y descargas de entregables, plantillas Word; cierre de periodos de indicadores; tableros `executive`, `project` y `monitoring` |
