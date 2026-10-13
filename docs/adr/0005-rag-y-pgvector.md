# ADR-0005: RAG en F7 y pgvector solo con justificación medida

## Estado

Aceptado en parte · 2026-10-12 (F7): la etapa 1 (`knowledge_chunk` con búsqueda de texto completo, JOB-07 y
recuperación con permisos); la etapa 2 (pgvector) sigue sujeta a los criterios de la Decisión

## Contexto

- AI-06 pide responder con citas desde actas, convenios, informes, estudios, documentos internos y evidencias
  [PRM-01 §9] (RF-AIK-006). El prompt pide no introducir pgvector hasta tener una justificación arquitectónica
  [PRM-01 §20]; el boceto de capas del usuario lo pone desde el inicio [ARQ-03]. La contradicción es CON-035.
- La plataforma ya resolvió la parte de base de datos: su imagen de referencia trae pgvector y la migración de core crea
  la extensión `vector` solo cuando el servidor la ofrece (WAS-ADR-0002, WAS-ADR-0026). La imagen
  `postgis/postgis:18-3.6` que usa SGSPE no la trae.
- La recuperación debe filtrar por tenant, proyecto, clasificación y permisos del usuario, y citar sus fuentes
  (RQ-AI-008). wasichai no tiene tipo de campo vector ni JSON, y el alcance de lectura por proyecto lo da
  `ProjectReadScope` (ADR-0029).
- No hay corpus real ni volúmenes (GAP-027), ni proveedor de embeddings: GAP-015 eligió la API de Anthropic para los
  modelos de lenguaje, y la etapa 2 elegirá el de embeddings con el criterio 4.

## Opciones consideradas

### pgvector desde el inicio (ARQ-03)

- Ventajas: búsqueda semántica disponible apenas llegue la IA.
- Desventajas: exige otra imagen de base y un proveedor de embeddings antes de saber si hace falta; contradice
  [PRM-01 §20].

### Diferir AI-06 más allá de F7

- Ventajas: nada que construir ni operar.
- Desventajas: incumple RF-AIK-006, que el roadmap pone en F7 [USR-05].

### Fragmentos como Custom Object

- Ventajas: permisos y auditoría de la plataforma.
- Desventajas: wasichai no tiene tipo de campo vector para la etapa 2 ([domain-model.md §4.2](../domain-model.md)); los
  fragmentos son datos derivados de su evidencia, que se regeneran, y el diseño los deja en una tabla fija como las
  demás tablas técnicas del esquema `sgspe`.

### Tabla fija con búsqueda de texto completo, y vectores solo si una medición los justifica (elegida)

- Ventajas: sin extensiones nuevas en F7; la etapa 2 se agrega con una columna y un índice, sin cambiar la recuperación.
- Desventajas: la búsqueda léxica pierde sinónimos y paráfrasis; hace falta un juego de evaluación para decidir.

## Decisión

AI-06 llega en F7 en dos etapas ([ai-architecture.md §13](../ai-architecture.md)).

**Etapa 1 (F7).**

- **Tabla.** `knowledge_chunk` es una tabla fija del esquema `sgspe`, no un Custom Object, con migración Flyway propia
  (`ModuleMigration` `sgspe_ai`, en `classpath:db/sgspe/ai`); solo `sgspe.ai` la lee y la escribe
  ([database-model.md §2.3 y §20](../database-model.md)).
- **Columnas e índices.** `organization_id`, `project_id`, `evidence_id`, `chunk_index`, `classification`, `content`,
  `embedding` (solo en la etapa 2) y `created_at`; único (`organization_id`, `evidence_id`, `chunk_index`), que hace
  idempotente la reindexación; índice (`organization_id`, `project_id`, `classification`) y GIN de texto completo sobre
  `to_tsvector('spanish', content)`.
- **Indexación (JOB-07).** Trabajo programado de `sgspe.ai` con `ClusterLock` y `RecordService.asPlatform`, periódico
  y configurable, solo en las organizaciones con la IA activada (ASM-006). Lee las `evidence` con texto creadas o
  cambiadas desde la vuelta anterior, extrae el texto con `TextExtraction` (PDFBox y POI, sin OCR, ASM-132), lo segmenta
  en fragmentos de unos 1 500 caracteres con solape y escribe los fragmentos con SQL propio; copia `project_id` y
  `classification` y los resincroniza; borra los fragmentos de una evidencia reemplazada; en la etapa 1 no llama a un
  modelo, así que no deja `ai_interaction`. Sin OCR: un documento sin texto no se indexa.
- **Recuperación con permisos.** Filtro de tenant; de proyecto, con `ProjectReadScope` (ADR-0029); de clasificación
  descargable (RF-DOC-003); relectura de las `evidence` candidatas con `RecordService` como la persona, descartando todo
  fragmento cuya evidencia no vuelve; búsqueda con `websearch_to_tsquery('spanish', …)` ordenada por `ts_rank_cd`, y los
  cinco mejores fragmentos que pasan los filtros llegan al modelo; seudonimización antes del modelo (RQ-AI-009);
  respuesta con cita de cada fuente, o "no hay evidencia", nunca sin fuente; una `ai_interaction` de AI-06.
- **Endpoint.** `POST /api/sgspe/ai/knowledge/questions` (ver ADR-0003 para la activación y el `503 AI_UNAVAILABLE`).
- **Retención.** Los fragmentos son derivados: se regeneran o se borran con su evidencia; la baja de un tenant borra sus
  filas por `organization_id` (GAP-026).

**Etapa 2.** La columna `embedding` y pgvector se agregan solo si se cumplen **todos** estos criterios:

| # | Criterio |
|---|---|
| 1 | AI-06 está activo en al menos una organización y hay un corpus de `evidence` con texto |
| 2 | Un juego de preguntas de evaluación con sus documentos esperados, sobre fixtures ficticios o el corpus anonimizado que autorice la organización, muestra que la búsqueda de texto no alcanza la meta de cobertura y precisión |
| 3 | El volumen (GAP-027) y el beneficio medido justifican el costo de vectorizar y mantener los vectores |
| 4 | El proveedor de embeddings cumple la política de residencia (GAP-015) o el modelo de embeddings es autoalojado |
| 5 | La base tiene pgvector |

- En la etapa 2, JOB-07 seudonimiza el texto antes de vectorizarlo.
- Ni la Fase 0 ni F7 fijan la meta numérica de cobertura y precisión, la dimensión ni el índice vectorial: la meta se
  fija con el juego de evaluación cuando se proponga la etapa 2, que F7 no construye; si la etapa 2 se adopta, un ADR
  nuevo registra la dimensión, el índice y el modelo de embeddings.

## Consecuencias

### Positivas

- F7 no depende de otra imagen de base ni de un proveedor de embeddings.
- La recuperación usa el mismo motor de permisos que el resto: un fragmento solo llega si su evidencia vuelve al
  releerla como la persona.
- La decisión de vectorizar se toma con datos, no por anticipado (CON-035).

### Negativas y riesgos

- Sin vectores, preguntas formuladas con otras palabras pueden no encontrar el documento.
- El filtro de proyecto es `ProjectReadScope` (ADR-0029); territorio y región siguen sin filtro propio (UPS-01).
- JOB-07 corre como la plataforma y su diccionario de seudonimización tiene todas las `person` del tenant.

### Seguimiento

- Etapa 2: fijar la meta del criterio 2 con el juego de evaluación y elegir el proveedor de embeddings (criterio 4);
  GAP-027.
- Pruebas de [ai-architecture.md §15](../ai-architecture.md): la recuperación nunca devuelve fragmentos de otro tenant,
  de una clasificación que la persona no puede descargar ni de una evidencia que no puede leer; quien no lee los nombres
  de `person` recibe 403 en `knowledge/questions`; sin IA activada, `503 AI_UNAVAILABLE` (RQ-AI-007).

## Referencias

- Requisitos: RF-AIK-006, RQ-AI-007, RQ-AI-008, RQ-AI-009, RF-DOC-003, RQ-SEC-002, RQ-SEC-006, CON-035, CON-041,
  GAP-015, GAP-026, GAP-027, ASM-006, ASM-132, UPS-01; ADR-0029.
- Documentos: [ai-architecture.md](../ai-architecture.md) §12.2, §13, §15 y §16; [database-model.md](../database-model.md)
  §2.3, §20, §24 y §26; [architecture.md §8.2](../architecture.md); [workflows.md](../workflows.md) (trabajos
  programados).
- wasichai: WAS-ADR-0002 (*PostgreSQL 18 + PostGIS (+ pgvector)*), WAS-ADR-0026 (*Each module owns its migrations and
  its Flyway history*), WAS-ADR-0039 (*Background work runs RecordService as the platform, and takes a cluster lock*).
- Fuentes: [PRM-01 §9, §20], [ARQ-03], [USR-05].
