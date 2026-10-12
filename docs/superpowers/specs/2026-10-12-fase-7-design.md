# SGSPE · Fase 7: especificación de diseño

> **Para agentes:** esta especificación es la entrada de los planes 7.0 (documentos), 7.1 (modelo), 7.2a (base de IA y
> asistente), 7.2b (casos con sugerencias), 7.2c (conocimiento y duplicados) y 7.3 (portal). No define IDs: los que se
> nombran en §6 los agrega el plan 7.0 a `docs/requirements-analysis.md`, y hasta entonces `docs/` sigue siendo la fuente
> de verdad. Donde esta especificación y `docs/` difieren, el plan 7.0 corrige `docs/` según lo que aquí se decide.

**Objetivo.** Dejar diseñada de punta a punta la Fase 7 («IA», primera de la Release 3) sobre wasichai 1.2.0 y la
maquinaria de F2 a F6: los casos AI-01 a AI-08 (en AI-04, solo el resumen ejecutivo), con seudonimización antes de cada
llamada, activación por organización, registro de cada interacción, prompts versionados, sugerencias que solo una
persona acepta, el asistente de solo lectura de wasichai-agent con sus puntos de extensión y la recuperación documental
con citas por búsqueda de texto completo.

**Arquitectura.** Un paquete de salida nuevo, `sgspe.ai`, que depende de los paquetes de dominio por sus comandos y
lecturas, y del que no depende ningún paquete de dominio. Los agentes propios corren en Embabel dentro de la petición y
como la persona; un puerto propio, `StructuredModel`, separa la llamada al modelo para que las pruebas usen dobles. AI-05
usa `AgentService` de wasichai-agent en proceso, con `AgentAccessPolicy`, `AgentResultFilter`, `AgentAnswerFilter` y
`AgentRunListener` de SGSPE. La salida de los modelos la valida código determinista y la guarda `SuggestionWriter` como
`ai_suggestion` o `commitment_candidate`; solo un endpoint de aceptación ejecuta un comando de dominio, y solo por una
lista cerrada. JOB-07 indexa el texto de las evidencias en la tabla fija `knowledge_chunk`, que AI-06 consulta con los
filtros de permisos.

**Fuentes.** Las decisiones del usuario del 2026-10-12 (§2.1, vinculantes); `docs/roadmap.md` (F7);
`docs/ai-architecture.md` (entero); `docs/api.md` (§4.14, asistente); `docs/security.md` (§7.8, §7.9, §11.5, §14, §15);
`docs/domain-model.md` (§4.10, §6.5, §6.18, §7.2, §7.3); `docs/workflows.md` (WF-K, JOB-07);
`docs/requirements-analysis.md` (RF-AIK-001 a 009; RQ-AI-001 a 015; GAP-013, GAP-015; UPS-14; ASM-015, ASM-052,
ASM-053); ADR-0005; el módulo de agente de wasichai 1.2.0 (`docs/modules/agent.md`, ADR-014, ADR-015, ADR-056,
`wasichai/agent/AgentExtensions.kt`); y el código de `sgspe.commitment`, `sgspe.evidence`, `sgspe.scope`,
`sgspe.dashboard` y `ArchitectureTest`.

## Restricciones globales

- wasichai `1.2.0` exacto (Embabel `1.5.3` por su BOM, sin fijar otra), `@wasichai/*` de la versión que ya usa
  sgspe-ui, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`; español (es-PE) en documentos, comentarios,
  prompts y `detail`, nombres técnicos en inglés (ADR-0009); valores ENUM en español MAYÚSCULAS sin tildes.
- Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; `scope` y `privacy` solo dependen de `common`; ningún literal de rol en `src/main/kotlin`.
- La IA nunca escribe datos de dominio, nunca acepta sus propias sugerencias, nunca aplica transiciones, nunca confirma
  alertas, nunca fusiona registros, nunca decide pertenencias a un área de influencia y nunca emite entregables
  (`ai-architecture.md` §16).
- Nunca `asPlatform` dentro de una petición: las corridas leen y escriben como la persona. Solo JOB-07 corre como la
  plataforma.
- Ninguna prueba llama al proveedor real; ningún perfil de Spring se llama `test`; las variables de la clave quedan en
  blanco en `integrationTest`.
- La clave del proveedor solo por variable de entorno (`ANTHROPIC_API_KEY`); nunca en la base, en `ai_interaction`, en
  los prompts, en los logs ni en un archivo del repositorio.
- Los logs nunca llevan prompts, fragmentos ni respuestas: solo el id de la `ai_interaction` y el de correlación.
- Fixtures y prompts con datos ficticios (`@sgspe.test`, «Proyecto Ejemplo A», «Comunidad Ejemplo») (RQ-SEC-018).

## 1. Propósito y alcance de F7

| Subsistema | Alcance | Piezas |
|---|---|---|
| Activación y estado | Lista de organizaciones del despliegue; disponibilidad del proveedor; casos ofrecidos; 503 solo en los endpoints que llaman a un modelo | `AiActivation`, `AiStatus` |
| Privacidad | Seudonimización con marcadores por llamada y estables al guardar; enmascarado de los campos restringidos; restauración al responder en AI-05 | `Pseudonymizer`, `PseudonymTable` |
| Registro | `ai_interaction` por llamada, con tokens, latencia y versión de prompt | `Interactions` |
| Prompts | Archivos versionados inmutables con manifiesto SHA-256; versión en uso por configuración | `Prompts` |
| Modelo | Puerto `StructuredModel` sobre Embabel; modelo por caso | `StructuredModel`, `EmbabelStructuredModel` |
| Asistente (AI-05) | `AgentService` en proceso con los cuatro puntos de extensión; `/api/agent/ask` cerrado por HTTP | `AssistantQuestions`, `AiAgentAccess`, `PseudonymizingResultFilter`, `RestoringAnswerFilter`, `InteractionRunListener`, `AgentHttpGate` |
| Casos con sugerencias | AI-01, AI-02, AI-03, AI-04 (resumen ejecutivo) y AI-08 por `POST /ai/runs` | `AiRuns`, un agente por caso, `TextExtraction`, `CitationCheck`, `SuggestionWriter` |
| Aceptación | Lista cerrada de `ai-architecture.md` §4.2; candidatos de IA | `SuggestionAcceptance`, aceptación de candidatos en `sgspe.commitment` |
| Conocimiento (AI-06) | JOB-07 sobre `knowledge_chunk`; recuperación con permisos y citas | `KnowledgeIndexJob`, `KnowledgeSearch`, `KnowledgeQuestions` |
| Duplicados (AI-07) | Reglas deterministas de pares candidatos; agente que compara solo esos pares | `DuplicateCandidates`, agente de AI-07 |
| Portal | Asistente; bandeja de revisión por proyecto; acciones de IA en las fichas | sgspe-ui |

Fuera de F7: los borradores de sección de AI-04 (esperan a `deliverable_section`, F8); pgvector y embeddings (etapa 2
de ADR-0005); OCR; corridas asíncronas; presupuesto de tokens por organización; contenido en quechua (GAP-013).

## 2. Decisiones

### 2.1 Las decisiones del usuario (2026-10-12)

| # | Decisión | Consecuencia |
|---|---|---|
| 1 | **Proveedor: la API de Anthropic** (GAP-015). Es el proveedor por defecto de `wasichai-spring-boot-starter-agent` | GAP-015 se cierra; se seudonimiza antes de cada llamada y la IA viene desactivada; la residencia queda como consecuencia aceptada (§8) |
| 2 | **AI-05 para todos, seudonimizado.** UPS-14 está resuelta en wasichai 1.2.0 (wasichai#59, ADR-056) | AI-05 se ofrece a todo rol que pueda crear `ai_interaction` en una organización activada; los resultados de las herramientas pasan por el `Pseudonymizer`, los campos restringidos se enmascaran siempre y la respuesta se restaura (§3.3); desaparecen las restricciones «hasta UPS-14» |
| 3 | **Activación por lista del despliegue** (`sgspe.ai.enabled-organizations`, por slug), mantenida por el operador junto con la clave | Sin objeto de ajustes nuevo; activar o desactivar es un cambio de configuración |
| 4 | **Modelo según el catálogo.** Haiku 4.5 para AI-07 y AI-08; el modelo más capaz que reconozca el catálogo de Embabel 1.5.3 para AI-01 a AI-06; todo configurable por caso | `sgspe.ai.models.<caso>`; 7.2a anota el modelo elegido; si Embabel suma Claude 5, se cambia por configuración |
| 5 | **Reparto en planes** 7.0, 7.1, 7.2a, 7.2b, 7.2c y 7.3 (enfoque A) | §7 |
| 6 | **Release 3.0.0 al cerrar F8.** El pie `Release-As: 3.0.0` ya está en `main` (sgspe #54, sgspe-ui #27) | El PR de release de release-please queda abierto hasta F8 |

### 2.2 Elecciones de esta especificación

1. **El alcance de AI-05 lo da el permiso, no una lista de roles.** `AiAgentAccess` (la `AgentAccessPolicy` de SGSPE)
   permite a quien tenga CREATE sobre `ai_interaction` en una organización activada. Con la matriz de `security.md`
   §7.8, eso deja fuera a los roles sin CREATE sobre `ai_interaction` (entre ellos `READ_ONLY`, `AUDITOR` y
   `PROJECT_ADMIN`). Ningún literal de rol en el código.
2. **Enmascarado por definición, no por nombre de campo.** El filtro de resultados de AI-05 recorre el JSON de cada
   herramienta y, para el objeto que la herramienta leyó (`input.objectName` o el `object` del registro), reemplaza el
   valor de todo campo restringido de RQ-SEC-010 por su marcador tipado. La lista de campos restringidos sale de una
   sola fuente, `RestrictedFields`, que ya usan los casos propios; una prueba la compara con `security.md` §7.9.
3. **La tabla de equivalencias vive en el contexto de la corrutina.** `AssistantQuestions` abre un `PseudonymTable`
   nuevo por pregunta y lo pone en el contexto antes de llamar a `AgentService.ask`; los filtros, que corren en el mismo
   contexto reactivo que las herramientas, lo toman de ahí. Sin tabla en el contexto, el filtro falla cerrado (lanza y
   nada sale hacia el modelo). La tabla no se guarda ni se registra.
4. **El diccionario de nombres de AI-05 es el de quien pregunta.** Si la persona no puede leer los nombres de `person`,
   las herramientas ya no los devuelven; los nombres escritos en textos libres que no están registrados como `person`
   pueden pasar (riesgo residual, §8). A diferencia de `runs` y `knowledge/questions`, AI-05 no exige leer los nombres.
5. **Un solo puerto para el modelo.** `StructuredModel.complete(request: ModelRequest<T>): ModelResult<T>` recibe el
   prompt ya seudonimizado, la versión del prompt, el modelo y los límites, y devuelve la salida tipada con el uso de
   tokens. `EmbabelStructuredModel` lo implementa sobre la plataforma de agentes; FakeModel y MockModel lo sustituyen
   en las pruebas unitarias, y `ScriptedLlmOperations` de Embabel conduce las corridas completas.
6. **Corridas síncronas con límites del servidor.** `sgspe.ai.limits.*`: tamaño del texto de entrada (por defecto
   200 000 caracteres), tokens de salida y acciones por corrida. Una corrida que se queda sin margen no escribe nada y
   responde `422 AI_INPUT_TOO_LARGE` o `502 AI_RUN_TRUNCATED`.
7. **Extracción de texto con PDFBox y POI, sin OCR.** `TextExtraction` lee PDF con texto y DOCX; un PDF sin texto o un
   formato distinto responde `422 AI_NO_TEXT`. PDFBox pasa a dependencia de `main` (F8 lo preveía solo en pruebas); POI llega
   aquí y F8 lo reutiliza.
8. **La cita se verifica sobre el texto seudonimizado.** `CitationCheck` compara la cita, normalizando espacios, con
   el fragmento que recibió el modelo; una cita que no aparece descarta la propuesta. La cita se guarda con marcadores
   estables.
9. **`knowledge_chunk` con texto completo en español.** Índice GIN sobre `to_tsvector('spanish', content)`; consulta con
   `websearch_to_tsquery('spanish', :q)` y `ts_rank_cd`; los cinco mejores fragmentos que pasan los filtros llegan al
   modelo. La columna `embedding` no se crea en F7.
10. **AI-07 primero determinista.** `DuplicateCandidates` arma pares por nombre normalizado, `alternative_names`, mismo
    proyecto, fecha y localidad, con un tope de pares por corrida; el modelo solo compara esos pares. Las sugerencias
    DUPLICADO siguen la lista de §4.2 (vínculo solo en `grievance` y en `commitment_candidate`; nombres alternativos en
    `organization`; decisión registrada en los demás).
11. **El portal re-identifica al mostrar.** Resuelve `[PERSONA:<id>]` leyendo la `person` con los permisos de quien
    mira; los marcadores sin id no se resuelven.

## 3. Diseño por subsistema

### 3.1 Activación, estado y modelo (plan 7.2a)

- `sgspe.ai.enabled-organizations` (lista de slugs) y la presencia de la clave deciden `enabled` y
  `providerAvailable`. `GET /api/sgspe/ai/status` responde siempre `{ enabled, providerAvailable, useCases[] }`;
  `useCases` lista los casos que la persona puede usar con sus permisos.
- `EmbabelGate` de wasichai ya deja fuera las autoconfiguraciones de Embabel sin clave; `sgspe.ai` obtiene la
  plataforma de agentes de forma opcional (`ObjectProvider`), y sin ella `providerAvailable` es `false`.
- `runs`, `assistant/questions` y `knowledge/questions` responden `503 AI_UNAVAILABLE` si la organización no está
  activada o no hay proveedor; aceptar, rechazar y marcar duplicado siguen funcionando.
- `sgspe.ai.models.<caso>` y `sgspe.ai.prompts.<caso>` son configuración con valor por defecto en `application.yml`.

### 3.2 Seudonimización, interacciones y prompts (plan 7.2a)

- `Pseudonymizer` es Kotlin puro: recibe el texto, el diccionario de nombres (`person` legibles por quien pide, o todas
  en JOB-07) y los valores restringidos conocidos; reemplaza por marcadores por llamada (`[PERSONA_1]`, `[TELEFONO_1]`)
  y detecta por formato documento de identidad, teléfono y correo. `PseudonymTable` guarda las equivalencias de la
  llamada y traduce a marcadores estables al guardar (`[PERSONA:<id>]`, `[PERSONA]`, `[DOCUMENTO]`, `[TELEFONO]`,
  `[CORREO]`) y a valores reales al restaurar la respuesta de AI-05.
- `Interactions` escribe una `ai_interaction` por llamada con `RecordService` como la persona (o como la plataforma en
  JOB-07): `use_case`, `provider`, `model`, `prompt_version`, `input_reference`, `output_summary`, `input_tokens`,
  `output_tokens`, `latency_ms`, `confidence`, `project`, `requested_by_user_*`.
- `Prompts` carga `src/main/resources/sgspe/ai/prompts/<id>/<versión>.md` y su cabecera; `PromptManifestTest` compara
  el SHA-256 de cada versión con `prompts/manifest.json`.

### 3.3 Asistente AI-05 (plan 7.2a)

- `POST /api/sgspe/ai/assistant/questions` `{ question }` → `200 { answer, steps[], truncated, interaction }`. Comprueba
  la activación, abre el `PseudonymTable`, llama a `AgentService.ask` en el mismo contexto y devuelve la respuesta ya
  restaurada.
- `AiAgentAccess` implementa `AgentAccessPolicy`: `Denied` si la organización no está activada o la persona no tiene
  CREATE sobre `ai_interaction`. Así `GET /api/agent/status` también dice `enabled=false` a quien no puede usarlo.
- `PseudonymizingResultFilter` (`AgentResultFilter`) seudonimiza cada resultado de herramienta y el resumen de cada
  paso, enmascara los campos restringidos (§2.2.2) y deja pasar sin cambios el texto que no es JSON salvo por la
  seudonimización de texto libre.
- `RestoringAnswerFilter` (`AgentAnswerFilter`) restaura los marcadores de la respuesta final con la tabla de la
  pregunta. Los campos restringidos enmascarados no se restauran: su marcador queda en la respuesta.
- `InteractionRunListener` (`AgentRunListener`) escribe la `ai_interaction` de AI-05 con `AgentUsage` (modelo y
  tokens), la latencia y el SHA-256 de la pregunta como `input_reference`; también en corridas truncadas o fallidas.
- `AgentHttpGate`, un `WebFilter`, responde `403` a `POST /api/agent/ask` por HTTP; `GET /api/agent/status` queda
  abierto.

### 3.4 Casos con sugerencias (plan 7.2b)

- `POST /api/sgspe/ai/runs` `{ useCase, target: { object, recordId } }` con `Idempotency-Key`. Comprueba la activación,
  READ sobre la fuente, CREATE sobre `ai_interaction` y sobre el objeto de salida, y la lectura de los nombres de
  `person` (si no, `403` antes de llamar al modelo).
- Fuentes por caso: AI-01 y AI-03, una `evidence` con texto (descarga controlada por clasificación, RF-DOC-003) o la
  `minutes` de una `engagement`; AI-02, la `engagement` con participantes, preguntas, temas y notas; AI-04, el `project`
  con los indicadores de `sgspe.dashboard` del periodo; AI-08, una QCS, `engagement_question` o `media_mention`.
- Cadena: lectura de la fuente → `TextExtraction` → segmentación → `Pseudonymizer` → agente → validación determinista
  (esquema, catálogo, destino existente y legible, `CitationCheck`, duplicados frente al proyecto) → `SuggestionWriter`.
- `SuggestionWriter` guarda con `SgspeWrite` como la persona: `ai_suggestion` (EXTRACCION, BORRADOR DE ACTA, RESUMEN
  EJECUTIVO, CLASIFICACION) o `commitment_candidate` (`ai_generated`, `confidence`, `evidence_quote`,
  `source_evidence`, `engagement`, `ai_interaction`, `suggested_owner_user_email` vacío).
- `POST /api/sgspe/ai-suggestions/{id}/accept` ejecuta solo una fila de la lista cerrada de `ai-architecture.md` §4.2,
  con los valores revisados de la petición y el motivo, en una transacción con la transición de WF-K hacia `accepted`.
  RESUMEN EJECUTIVO no se acepta en F7. Rechazar es la transición de WF-K hacia `rejected` por la API de workflow.
- `POST /api/sgspe/commitment-candidates/{id}/accept` (de F3) acepta también los candidatos de IA; el responsable lo
  elige quien acepta.

### 3.5 Conocimiento (AI-06) y duplicados (AI-07) (plan 7.2c)

- `KnowledgeIndexJob` (JOB-07) con `ClusterLock` y `asPlatform`: lee las `evidence` creadas o cambiadas desde su última
  corrida, extrae y segmenta el texto (fragmentos de unos 1 500 caracteres con solape), copia `project_id` y
  `classification`, reemplaza los fragmentos de la evidencia y borra los de una evidencia reemplazada (`replaces`). En
  F7 no llama a un modelo, así que no deja `ai_interaction`.
- `KnowledgeSearch` aplica tenant, proyecto (`ProjectReadScope`), clasificación descargable y relectura de las
  `evidence` con `RecordService` como la persona, en ese orden; `POST /api/sgspe/ai/knowledge/questions`
  `{ question, project }` → `200 { answer, citations[], interaction }`. Sin fragmento que pase los filtros, responde que
  no hay evidencia sin llamar al modelo.
- AI-07 por `POST /ai/runs` con `useCase: AI-07` y `target` el proyecto u objeto: `DuplicateCandidates` arma los pares,
  el agente los compara y `SuggestionWriter` guarda una sugerencia DUPLICADO por par con sus razones.

### 3.6 Portal (sgspe-ui, plan 7.3)

- **Asistente:** panel de preguntas con los pasos y la marca de truncado; solo visible si `ai/status` lo ofrece.
- **Bandeja de revisión por proyecto:** `commitment_candidate` y `ai_suggestion` en `proposed`, por confianza y fecha;
  aceptar con corrección y motivo, rechazar con motivo, marcar duplicado; la cita y el enlace solo para quien puede
  abrir la fuente; marcadores re-identificados al mostrar.
- **Acciones en las fichas:** evidencia (AI-01, AI-03), interacción (AI-02, AI-03), QCS, pregunta y mención (AI-08),
  proyecto (AI-04 resumen ejecutivo, AI-07), y la búsqueda documental (AI-06) en el proyecto.
- Salidas como texto o Markdown sin HTML.

## 4. Cambios en el modelo y en los permisos (plan 7.1)

- `model.json`: `ai_suggestion` (`apiOnly`) y `ai_interaction` (`apiOnly`, `appendOnly`) con los campos de
  `domain-model.md` §6.18 y el catálogo «Tipo de sugerencia de IA»; las M2O de `commitment_candidate` hacia
  `ai_interaction` y `source_evidence` si faltan; ambos objetos clasificados en `ProjectScope`.
- `model/workflows/`: WF-K de `ai_suggestion` (`proposed` → `accepted`, `rejected`, `duplicate`), con los roles de
  `workflows.md`.
- `roles.json`: las filas de `ai_interaction` y `ai_suggestion` de `security.md` §7.8; `evidence_quote` sin lectura
  para los roles sin `DOWNLOAD_RESTRICTED` sobre `evidence`; `test_apply_roles.py` con «ningún rol crea
  `ai_suggestion` sin leer los nombres de `person`».
- Migración Flyway de `knowledge_chunk` (`ModuleMigration` de `sgspe.ai`) con su índice GIN.

## 5. Pruebas

- **Unitarias:** `Pseudonymizer` y `PseudonymTable`; `CitationCheck`; esquemas y catálogos de las salidas;
  `PromptManifestTest`; `RestrictedFields` frente a `security.md` §7.9; `DuplicateCandidates`; `TextExtraction` con
  PDF y DOCX ficticios.
- **Con FakeModel y MockModel (puerto `StructuredModel`):** lo que sale hacia el modelo está seudonimizado y nunca lleva
  un número de documento, teléfono o correo; versión de prompt y límites.
- **Del agente, con `ScriptedLlmOperations`:** AI-03 sobre un acta ficticia produce candidatos con cita y confianza;
  una corrida sin margen no escribe; AI-05 completo: el modelo recibe resultados seudonimizados y sin campos
  restringidos, la respuesta vuelve restaurada, un filtro que falla no envía nada, y la `ai_interaction` queda con
  tokens.
- **De integración** (`ai-architecture.md` §15): aceptación en una transacción; 403 de la API genérica sobre los tres
  objetos y 409 al editar una `ai_interaction`; `CommandTransitionGuard`; pares fuera de la lista; sugerencias sin
  valores restringidos; 403 a quien no lee nombres en `runs` y `knowledge/questions`; 503 sin activación o sin clave con
  `status`, aceptación y rechazo funcionando; 403 de `POST /api/agent/ask` por HTTP; recuperación sin fragmentos de otro
  tenant, de una clasificación no descargable o de una evidencia ilegible; `AiSuggestionWorkflowApiTest`.
- **De inyección:** documentos ficticios con instrucciones incrustadas no producen escrituras.
- **Contra el proveedor real:** etiqueta `ai-live`, fuera de la CI, a pedido, con fixtures ficticios; verifica que la
  salida estructurada se pueda leer.
- **Arquitectura:** `sgspe.ai` como paquete de salida (ningún paquete de dominio depende de él) y los objetos nuevos en
  `ProjectScope`.

## 6. Cambios en los documentos (plan 7.0)

- `requirements-analysis.md`: GAP-015 «Resuelta» (decisión 1); UPS-14 «Resuelta» con wasichai#59 y ADR-056; RF-AIK-005
  sin «hasta UPS-14»; ASM nuevas: activación por lista del despliegue (decisión 3), modelo por caso (decisión 4),
  alcance de AI-05 por permiso con enmascarado (§2.2.1 y §2.2.2), límites de corrida (§2.2.6) y extracción sin OCR con
  PDFBox y POI (§2.2.7).
- `ai-architecture.md`: §8 reescrito con los cuatro puntos de extensión; §9.2 con tokens en AI-05; §11 con el proveedor
  y la residencia decididos y Embabel 1.5.3; §12.1 sin la excepción de AI-05; §13.1 con ADR-0005 aceptado en etapa 1;
  §16 sin la restricción de AI-05.
- `security.md`: §11.5 y §15 con el nuevo alcance de AI-05; §14 con las pruebas de AI-05.
- `api.md`: el asistente sin «mientras no exista UPS-14»; los errores `AI_UNAVAILABLE`, `AI_INPUT_TOO_LARGE`,
  `AI_RUN_TRUNCATED` y `AI_NO_TEXT`.
- ADR-0005 «Aceptada» (etapa 1); `roadmap.md` F7 con la especificación y el reparto; `requirements-traceability.md` y
  `check_traceability.py` en verde.

## 7. Reparto en planes

| Plan | Rama | Depende de | Tareas principales | Estimado |
|---|---|---|---|---|
| 7.0 · Documentos | `docs/fase-7-0-documentos` | Esta especificación | §6; trazabilidad en verde; PR | ~7 tareas |
| 7.1 · Modelo | `feat/fase-7-1-modelo` | 7.0 | §4; `design_docs.py` y `test_model.py`; base `F7ApiTest`; PR | ~8 tareas |
| 7.2a · Base y asistente | `feat/fase-7-2a-base` | 7.1 | Starter de agente; §3.1 a §3.3; `ArchitectureTest`; PR | ~12 tareas |
| 7.2b · Casos con sugerencias | `feat/fase-7-2b-casos` | 7.2a | §3.4; pruebas de inyección; PR | ~14 tareas |
| 7.2c · Conocimiento y duplicados | `feat/fase-7-2c-conocimiento` | 7.2a | §3.5; PR | ~9 tareas |
| 7.3 · Portal | `feat/fase-7-3-portal` en `sgspe-ui` | 7.0 (contrato) | §3.6 con `mockFetch`; Playwright; integración contra el backend real al final; PR | ~12 tareas |

**Paralelismo.** 7.0 primero. Con 7.0 en `main`: 7.1 y 7.3 en paralelo. Con 7.2a en `main`: 7.2b y 7.2c en paralelo,
en worktrees separados. Las integraciones las corre el coordinador contra `sgspe_sdd_test`, o la CI.

**Tareas del coordinador de la fase.** Subir el tenant `demo` a F7 (modelo, roles y workflows); activar la IA en `demo`
por configuración; humo con la clave real que el usuario aporta por variable de entorno (AI-03 sobre un acta ficticia,
AI-05 con una pregunta, AI-06 con una pregunta sobre evidencias ficticias); humo del portal con el navegador.

**Definición de terminado.** La de `roadmap.md` F7, con AI-05 sin restricción de roles y con tokens; el tenant `demo`
en F7; el humo con la clave real anotado en `roadmap.md` F7 «Estado».

## 8. Riesgos y consecuencias aceptadas

**Consecuencias aceptadas.**

1. **Residencia de datos:** los datos seudonimizados se procesan en la infraestructura de Anthropic, fuera del Perú; lo
   que llega al proveedor es lo que la persona puede leer, sin campos restringidos y con los nombres registrados
   reemplazados.
2. **Nombres en textos libres:** un nombre no registrado como `person` puede llegar al proveedor (RQ-REP-033).
3. **Activar la IA pide redesplegar** la configuración.
4. **Sin OCR:** un documento escaneado no se procesa ni se indexa.

**Riesgos.**

1. **Catálogo de Embabel 1.5.3** sin los modelos Claude 5: calidad menor en la extracción; mitigado por la
   configuración por caso.
2. **Inyección de instrucciones** en actas y documentos: herramientas de solo lectura, validación determinista y
   decisión humana; el peor caso es una sugerencia mala que se rechaza.
3. **Fallo del filtro de AI-05:** cierra la corrida sin enviar nada; se ve como error, no como fuga.
4. **Costos** sin presupuesto por organización: los tokens quedan registrados para medirlos.
5. **Tiempos** de las corridas síncronas con documentos largos: acotados por los límites de §2.2.6.
