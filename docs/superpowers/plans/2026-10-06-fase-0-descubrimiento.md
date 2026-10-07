# SGSPE — Fase 0 (descubrimiento y diseño): plan de implementación

> **Para agentes:** SUB-SKILL REQUERIDA: usar superpowers:subagent-driven-development (recomendado) o
> superpowers:executing-plans para implementar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento.

**Objetivo:** producir y versionar la documentación de la Fase 0 que exige el prompt maestro: análisis de requisitos,
glosario, modelo de dominio, arquitectura, modelo de datos, workflows, IA, dashboards, trazabilidad, ADRs y roadmap. Todo
queda adaptado a construir SGSPE sobre wasichai, y no se escribe código.

**Arquitectura:**

- SGSPE es una app wasichai como srtm-backend/caja-backend: starters, modelo de metadata versionado en `model/`, paquetes
  `sgspe.*` solo donde la metadata no alcanza, y una UI propia (portal + `/admin`) en `sgspe-ui`.
- Esta fase solo documenta. Se escribe en tres olas:
  - fuente de verdad (glosario, requisitos, dominio);
  - documentos derivados en paralelo;
  - ADRs, trazabilidad y roadmap.
- Cierra una validación cruzada automatizada más un revisor.

**Stack documentado:**

- wasichai 0.3.2: Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux, JDK 25, PostgreSQL 18 + PostGIS 3.6, GeoServer opcional,
  Embabel en wasichai-agent.
- `@wasichai/*` 0.5.0: React 19.3, Vite 8, Tailwind 4, MapLibre 6.10.0.
- Entregables: Apache POI + Gotenberg.
- Dashboards: ECharts.

**Spec:** plan aprobado en `docs/superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md`, copia versionada de
`~/.claude/plans/analiza-los-documentos-de-hashed-lamport.md` (la crea la Tarea 6).

**Insumos** (scratchpad, no versionados):
el directorio temporal de la sesión de trabajo, abreviado `$SP`.

| Ruta | Contenido |
|---|---|
| `$SP/disenos/contexto-sgspe.md` | Reglas comunes y catálogo canónico de objetos (léelo siempre primero) |
| `$SP/disenos/dominio-blueprint.md` | Requisitos, dominio, workflows, CON/GAP/ASM, ADRs (con las correcciones de la §9 del contexto) |
| `$SP/disenos/entregables-gis-dashboards.md` | Entregables DOCX/PDF, GIS, KPIs |
| `$SP/disenos/fase1-wasichai.md` | Fase 1 sobre wasichai, roles, auditoría y brechas |
| `$SP/fuentes/` | Textos de DOC-01/DOC-02, imagen DOC-03, imágenes del DOCX |
| `$SP/verificacion/check_docs.py` | Verificador de consistencia (etapas: glossary, requirements, domain, wave2, adrs, trace, all) |
| `$SP/verificacion/check_nombres.py` + `nombres-reales.txt` | Verificador de anonimización; la lista nunca entra a git |

## Restricciones globales

- Documentos en español (es-PE).
- Identificadores técnicos en inglés:
  - objetos y campos;
  - estados de workflow en snake_case, con etiqueta en español;
  - roles y acciones declaradas en UPPER_SNAKE;
  - paquetes `sgspe.*` y rutas `/api/sgspe/**`.
- Valores de ENUM en español MAYÚSCULAS (son datos visibles).
- Nombres de objeto exactamente como el catálogo canónico de `$SP/disenos/contexto-sgspe.md` §9.1.
- Cada requisito, regla o decisión cita su fuente:
  - `[DOC-01 §x]`, `[DOC-02 p.x]`, `[DOC-03]`, `[PRM-01 §x]`, `[USR-0x]`, `[WAS-ADR-00xx]`;
  - lo no sustentado se marca como `**ASSUMPTION (ASM-NNN):**`.
- Los IDs (RF/RNF/RN/RQ/CON/GAP/ASM/UPS) se **definen** solo en `docs/requirements-analysis.md`, como primera celda de
  una fila de tabla. Los demás docs solo los referencian. Prohibidos los rangos (`RF-X-001..006`).
- Ningún nombre de persona real de las fuentes en archivos versionados. Se usan cargos, y nombres ficticios en ejemplos.
- No se reproducen DNI, teléfonos ni números de partida o comprobante.
- Nada contradice las reglas de wasichai: sin JPA/Hibernate/Spring Data, sin escaneo de paquetes `wasichai`, DDL solo
  vía `ObjectSchemaManager`, y el agente IA nunca escribe datos.
- Nunca se versionan `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni las fuentes de `~/Documents/gspe`.
- `git add` solo con rutas explícitas.
- Commits Conventional Commits con asunto en español, terminados en:

  ```
  Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
  ```

- Rama `fase-0/descubrimiento`. Sin push ni PR hasta el checkpoint.
- Los subagentes no ejecutan git; solo escriben sus archivos asignados.

## Foco de revisión

1. **Nombres reales filtrados** en tablas copiadas de DOC-01 (Tablas 7.1–7.3), en citas textuales ("Teniente
   gobernadora…") o en ejemplos. Se espera cero coincidencias de `check_nombres.py` antes de cada commit.
2. **Deriva de nombres entre documentos**: `grievance` vs `complaint`, `pending_approval` vs `pending`, `stakeholder` vs
   `actor`, roles inventados. Se espera que `check_docs.py` falle ante un objeto o rol fuera del catálogo.
3. **Requisitos sin fuente o funcionalidades inventadas** presentadas como hechos. Cada fila de requisito tiene fuente y
   marcador de origen; si no hay fuente, es ASSUMPTION.
4. **Diseños que contradicen wasichai**: entidades JPA, controladores por objeto, vistas materializadas, estados "vencido"
   en el workflow, el agente creando registros. El revisor busca esas frases y el verificador rechaza estados `overdue`.
5. **Insumos transitorios en el stage** por un `git add` amplio. `check_docs.py` falla si alguno está en el stage o
   versionado.

---

## Plantilla de encargo para subagentes implementadores (se usa en las Tareas 1–5)

Cada encargo se arma con este texto más la sección "Encargo" de su tarea:

```text
Eres un redactor técnico senior de SGSPE (Fase 0, solo documentación, en español).
1. Lee COMPLETO $SP/disenos/contexto-sgspe.md (reglas, IDs, catálogo canónico, correcciones al blueprint).
2. Lee los documentos fuente de verdad ya escritos en ~/IdeaProjects/sgspe/docs/ que tu encargo indique.
3. Usa como insumos: $SP/disenos/dominio-blueprint.md, $SP/disenos/entregables-gis-dashboards.md,
   $SP/disenos/fase1-wasichai.md, las fuentes en $SP/fuentes/ y la documentación de wasichai en
   ~/IdeaProjects/wasichai/docs/.
4. Escribe SOLO los archivos de tu encargo. No ejecutes git. No escribas nombres reales de personas.
5. Antes de terminar, ejecuta:
   python3 -I $SP/verificacion/check_docs.py ~/IdeaProjects/sgspe --stage <etapa de tu encargo>
   python3 -I $SP/verificacion/check_nombres.py ~/IdeaProjects/sgspe $SP/verificacion/nombres-reales.txt
   y corrige lo que dependa de tus archivos.
6. Informe final: archivos escritos; IDs referenciados; IDs nuevos que propones (no los definas fuera del análisis);
   contradicciones o dudas; resultado de ambos verificadores.
```

## Plantilla de encargo para revisores de cumplimiento (por tarea)

```text
Eres revisor de especificación. Verifica que <archivos> cumplen: (a) el encargo de la Tarea N del plan
docs/superpowers/plans/2026-10-06-fase-0-descubrimiento.md; (b) las reglas de $SP/disenos/contexto-sgspe.md;
(c) que todo requisito/regla tenga fuente o marca ASSUMPTION; (d) que nada contradiga wasichai ni el plan aprobado.
No edites archivos: devuelve una lista priorizada de hallazgos (archivo:línea, problema, corrección propuesta) o "OK".
```

---

### Tarea 1: Glosario y análisis de requisitos (pasos 2 y 3 del prompt)

**Archivos:**

- Crear: `docs/glossary.md`
- Crear: `docs/requirements-analysis.md`

**Interfaces:**

- Consume: fuentes DOC-01..03, PRM-01, ARQ-01..04, USR-01..09; blueprint §1–§2 y §6; contexto §5–§9.
- Produce:
  - todos los IDs RF/RNF/RN/RQ/CON/GAP/ASM/UPS;
  - el vocabulario ES↔EN;
  - la clasificación de fuentes;
  - la lista de procesos `P-01..`.

  Los demás documentos solo pueden referenciar estos IDs.

**Encargo** (se añade a la plantilla):

- **`docs/glossary.md`:**
  - Una tabla con las columnas `Término (ES) | Identificador | Concepto del prompt (EN) | Definición | Fuente | Variantes
    / sinónimos | No confundir con`.
  - Agrupada por: Territorio, Actores, Relacionamiento, Compromisos y acuerdos, QCS, Riesgos y alertas, Inversión,
    Evidencias y entregables, Workflow, IA, Plataforma.
  - Obligatorio distinguir:
    - comunidad / localidad / unidad administrativa / área de influencia / grupo de interés;
    - posición del cargo (`position`) / postura frente al proyecto (`stance`);
    - AID/AIDS/AII/AIIS;
    - documento emitido / evidencia / entregable;
    - organización tenant de wasichai / `organization` del dominio.
- **`docs/requirements-analysis.md`:**
  - §0 Alcance, fuentes y precedencia: tabla de fuentes con tipo (normativa, requisito funcional, requisito técnico,
    proceso de negocio, referencia, dato/catálogo, documento histórico), autoridad y justificación; reglas de
    precedencia; convenciones de IDs; marcadores `[EXP] [INF] [PRM] [USR] [PLT] [ASM]`.
  - Luego exactamente 20 secciones `## N. Título`, en este orden y con estas palabras clave en el título:
    1. Resumen ejecutivo
    2. Contexto del negocio
    3. Objetivos del sistema
    4. Actores
    5. Procesos
    6. Requisitos funcionales
    7. Requisitos no funcionales
    8. Reglas de negocio
    9. Entidades identificadas
    10. Catálogos
    11. Workflows
    12. Requisitos GIS
    13. Requisitos de reporting
    14. Requisitos de seguridad
    15. Requisitos de auditoría
    16. Requisitos de IA
    17. Contradicciones
    18. Gaps
    19. Assumptions
    20. Matriz de trazabilidad (resumen; la matriz completa está en `requirements-traceability.md`)
  - Requisitos (§6, §7, §12–§16):
    - en tablas `| ID | Requisito | Fuente | Origen | BC | Fase |`;
    - todos los del blueprint §2.2–§2.4, traducidos a los códigos de contexto ingleses del contexto §5 y con objetos en
      inglés, más los RQ-REP/RQ-GIS/RQ-DSH de `entregables-gis-dashboards.md` (A.1, B, C.2);
    - incluir los requisitos del usuario USR-01..09;
    - el sistema de variables de influencia (contexto §7) como requisitos RF-STK.
  - §17, §18 y §19:
    - CON del blueprint §6.1 más los del contexto §7 (renumerados `CON-NNN` sin duplicados);
    - GAP del blueprint §6.2;
    - ASM del blueprint §6.3, corregidos según el contexto §9: la ASM de entregables pasa a DOCX-first + Gotenberg.
    - Cada CON tiene una resolución o queda en "decisión pendiente".
  - Brechas upstream de wasichai como `UPS-01..UPS-12`:
    - UPS-01 alcance de lectura por proyecto;
    - UPS-02 auditoría de la administración;
    - UPS-03 correlation id;
    - UPS-04 bloqueo optimista;
    - UPS-05 paginación y filtros del audit;
    - UPS-06 capacidades en `/me/permissions`;
    - UPS-07 campo FILE;
    - UPS-08 UI de reglas de escritura;
    - UPS-09 revocación de token, throttling y política de contraseñas;
    - UPS-10 provisión de tenants;
    - UPS-11 módulo de notificaciones;
    - UPS-12 inmutabilidad de `audit_log` en BD.

**Etapa del verificador:** `requirements`

- [ ] **Paso 1:** confirmar el rojo.
  - Comando: `python3 -I $SP/verificacion/check_docs.py ~/IdeaProjects/sgspe --stage requirements`
  - Resultado esperado: FALLA con "falta el archivo docs/glossary.md" y "…requirements-analysis.md".
- [ ] **Paso 2:** despachar un subagente implementador (general-purpose) con la plantilla y el encargo de la Tarea 1.
- [ ] **Paso 3:** ejecutar los dos verificadores.
  - Resultado esperado: `etapa=requirements errores=0` y `coincidencias: 0`.
- [ ] **Paso 4:** despachar el revisor de cumplimiento sobre los dos archivos. Aplicar las correcciones (el implementador
  o el coordinador) y volver al Paso 3 hasta que quede "OK".
- [ ] **Paso 5:** commit.

  ```bash
  cd ~/IdeaProjects/sgspe
  git add docs/glossary.md docs/requirements-analysis.md
  git commit -m "docs(requisitos): análisis de requisitos y glosario de la fase 0" -m "<trailers>"
  ```

### Tarea 2: Modelo de dominio (paso 4)

**Archivos:**

- Crear: `docs/domain-model.md`

**Interfaces:**

- Consume: `docs/glossary.md`, `docs/requirements-analysis.md` (IDs), blueprint §3–§5, contexto §9.1.
- Produce:
  - BC-01..BC-20 con su clasificación MVP / Fase 2 / Fase 3 y la fase F1..F8;
  - el catálogo canónico de objetos con sus campos clave;
  - los agregados e invariantes y el mecanismo de cada uno: metadata, `RecordWriteGuard`, endpoint `/api/sgspe/**`,
    trabajo programado o listener;
  - los nombres de los estados de cada workflow (lista corta, sin detalle);
  - las relaciones.

**Encargo:**

- Secciones:
  1. Propósito
  2. Mapa de contextos (Mermaid)
  3. Distinciones fundamentales (blueprint §4.1)
  4. Convenciones de modelado sobre wasichai (blueprint §4.2 en inglés)
  5. `## 5. Bounded contexts`: tabla con BC, decisión, release, fase y código propio
  6. `## 6. Catálogo de objetos`: por BC, cada objeto en backticks, con sus campos clave (tipo wasichai), relaciones y
     flags (`appendOnly`, `apiOnly`, `requiresReason`). Debe contener **todos** los objetos del contexto §9.1.
  7. Agregados, invariantes y mecanismo (blueprint §4.4)
  8. Metadata vs código Kotlin propio (blueprint §4.5, con paquetes en inglés: `sgspe.common`, `sgspe.scope`,
     `sgspe.stakeholder`, `sgspe.engagement`, `sgspe.commitment`, `sgspe.attention`, `sgspe.risk`, `sgspe.warning`,
     `sgspe.deadline`, `sgspe.notification`, `sgspe.evidence`, `sgspe.territory`, `sgspe.dashboard`,
     `sgspe.deliverable`, `sgspe.ai`, `sgspe.privacy`)
  9. Diagramas `classDiagram` por BC principal
  10. Cobertura de los entregables DOC-01/DOC-02: qué objetos alimentan cada capítulo
- Los requisitos se referencian por ID.

**Etapa del verificador:** `domain`

- [ ] **Paso 1:** `check_docs.py --stage domain` → FALLA ("falta el archivo docs/domain-model.md").
- [ ] **Paso 2:** despachar el implementador.
- [ ] **Paso 3:** verificadores → `etapa=domain errores=0`; `coincidencias: 0`.
- [ ] **Paso 4:** revisor de cumplimiento; correcciones hasta "OK".
- [ ] **Paso 5:** commit.

  ```bash
  git add docs/domain-model.md
  git commit -m "docs(dominio): modelo de dominio, bounded contexts y mapeo a wasichai" -m "<trailers>"
  ```

### Tarea 3: Documentos derivados en paralelo (pasos 5–9 y §22 del prompt)

Se usa superpowers:dispatching-parallel-agents: seis subagentes a la vez, cada uno con archivos disjuntos.

**Archivos:**

| Subagente | Archivos | Contenido mínimo |
|---|---|---|
| 3a | `docs/architecture.md`, `docs/platform-fit.md`, `docs/api.md` | **Arquitectura**: drivers; C4 contexto y contenedores (portal + admin, app WebFlux, PostgreSQL/PostGIS, GeoServer, S3/MinIO, Gotenberg, SMTP, LLM); starters por fase; paquetes `sgspe.*`; ruta de una petición; DATA/WORKFLOW/AI → AUDIT; trabajos en segundo plano (`ClusterLock` + `asPlatform`); frontend (portal + `/admin`, `@wasichai/*` 0.5.0); observabilidad; entornos (Docker remoto, puertos 8094/5182/5435); riesgos. **Encaje con wasichai** (platform-fit): cada mandato del prompt §3–§27 → ADR de wasichai / ADR de SGSPE / CON, capacidades usadas por módulo, brechas UPS, patrones srtm/caja reutilizados, versiones fijadas. **API**: la API genérica de wasichai (enlace a su `rest.md`) y `/api/sgspe/**` por BC (comandos, consultas, KPI, evidencias, importaciones, entregables, bandeja), con convenciones (Problem Details, paginación, `X-Change-Reason`, `Idempotency-Key`, precondición `updatedAt`, 422 ante un parámetro desconocido) |
| 3b | `docs/database-model.md` | Principios (WAS-ADR-0003/0004/0026); convenciones de `model.json` (nombres, reservados, ENUM, geometrías SRID 4326, flags, índices y únicos declarados); **una ficha `### \`objeto\`` por cada objeto del catálogo canónico** con campos (nombre, tipo, requerido, único, descripción), relaciones y flags; ENUMs compartidos (valores en español MAYÚSCULAS); tablas fijas `notification`, `notification_delivery`, `knowledge_chunk` (esquema `sgspe`); `apply.py` (sincroniza, nunca renombra); fixtures ficticios; retención |
| 3c | `docs/workflows.md` | Principios (blueprint §5.0); formato JSON de workflow de wasichai; secciones `## WF-A` … `## WF-G` del prompt (`## WF-E` cubre QCS con subsecciones `### WF-E1` queja, `### WF-E2` consulta, `### WF-E3` solicitud) y además `WF-H` (entregables), `WF-I` (acuerdos), `WF-J` (alertas), `WF-K` (candidatos y sugerencias IA), `WF-L` (visitas), `WF-M` (incidentes), `WF-N` (tareas), `WF-O` (áreas de influencia) y `WF-P` (aportes). Cada una lleva una línea `**Objeto:** \`nombre\``, un `stateDiagram-v2` y la tabla `Transición \| De \| A \| Roles \| Guarda \| Motivo \| Efectos \| Validación vs fuentes`. Además: conciliación con la matriz de DOC-01 §9.3.1.2; automatizaciones `AUT-NN`; trabajos `JOB-NN`; listeners. Los plazos son `deadline_status` y nunca un estado |
| 3d | `docs/security.md` | Amenazas; autenticación (WAS-ADR-0010; JWT en `localStorage`; mitigaciones; OIDC futuro); tenancy; permisos (rol, objeto, acción; campo; `own_records_only`; acciones declaradas); **tabla de los 17 roles del prompt** (rol en backticks, descripción, mapeo a wasichai: SUPER_ADMIN y ORGANIZATION_ADMIN → ADMIN, fase de concesión de permisos, ámbito requerido); matriz rol × objeto inicial; ámbito por proyecto y territorio (UPS-01; opciones A/B); GeoServer nunca público; datos personales (Ley 29733 como GAP, minimización, permisos de campo, exportes, IA seudonimizada); anonimización de artefactos versionados; auditoría (WHO/WHAT/WHEN/WHY/FROM/TO con `audit_log`); pruebas de seguridad |
| 3e | `docs/gis.md`, `docs/deliverables.md` | **GIS**: rol del SIG; capas `LYR-NN` (catálogo de B.2 más las capas del Anexo 1 de DOC-01); SRID (4326 + `working_crs` 32717/32718/32719; PSAD56 solo como entrada; área oficial plana en ha); importación (XLSX/CSV E/N, GeoJSON en F2; GDAL en F6; reconciliación por el hallazgo de DOC-03); consultas espaciales (casos de uso de PRM §6 + derivados de los documentos); servicio al navegador; mapa base; pruebas. **Entregables**: USR-01; tipos (T1 diagnóstico, T2 informe de campo, exportes XLSX y los ASSUMPTION); anatomía capítulo por capítulo de DOC-01 y DOC-02 (sección → objetos/campos → narrativa, tabla, mapa o foto → autoría → fase); DOCX-first (POI) + Gotenberg; constructos Word permitidos y fuentes; mapas por GeoServer GetMap; gráficos nativos; ciclo de vida `WF-H`; emisión congelada con hash; niveles de divulgación; almacenamiento; criterios de aceptación con fixtures ficticios |
| 3f | `docs/ai-architecture.md`, `docs/dashboards.md` | **IA**: principios (la IA sugiere, el humano decide; RN de IA); AI-01..AI-08 → mecanismo (asistente de solo lectura de wasichai-agent para AI-05; agentes Embabel propios en `sgspe.ai` que escriben `ai_suggestion` / `commitment_candidate` solo vía código propio); flujo ARQ-02; `ai_interaction` (proveedor, modelo, versión de prompt, tokens, latencia, confianza, validación); versionado de prompts; RAG (pgvector diferido a F7 con criterio de introducción); privacidad e inyección; FakeModel/StubModel/MockModel; límites. **Dashboards**: principios; catálogo de KPIs `KPI-*` con definición, fórmula, fuente y fase (incluye los 13 indicadores de la Tabla 9.5 y los de PRM §16); tableros ejecutivo, de proyecto, de compromisos, de QCS, de riesgos (heatmap P×I), territorial, de grupos de interés y de inversión; arquitectura de consultas (`RecordService`, evaluaciones append-only, sin vistas materializadas); ECharts; accesibilidad; fases |

**Interfaces:**

- Consume: glosario, análisis y dominio (fuente de verdad), y los tres diseños.
- Produce: los diez documentos.
- Si un subagente necesita un ID que no existe en el análisis, lo **propone** en su informe y no lo define.

**Etapa del verificador:** `wave2`

- [ ] **Paso 1:** `check_docs.py --stage wave2` → FALLA (faltan 10 archivos).
- [ ] **Paso 2:** despachar los 6 implementadores en paralelo, en un solo mensaje.
- [ ] **Paso 3:** consolidar los IDs propuestos.
  - Si hacen falta, agregarlos a `docs/requirements-analysis.md` (coordinador). El commit va con su documento
    consumidor.
- [ ] **Paso 4:** verificadores → `etapa=wave2 errores=0`; `coincidencias: 0`.
- [ ] **Paso 5:** despachar un revisor de cumplimiento por subagente (6, en paralelo) y aplicar las correcciones.
- [ ] **Paso 6:** commits en el orden de pasos del prompt.

  ```bash
  git add docs/architecture.md docs/platform-fit.md docs/api.md
  git commit -m "docs(arquitectura): arquitectura sobre wasichai, encaje con la plataforma y API" -m "<trailers>"

  git add docs/database-model.md
  git commit -m "docs(datos): modelo de metadatos y datos" -m "<trailers>"

  git add docs/workflows.md
  git commit -m "docs(workflows): máquinas de estado, automatizaciones y trabajos programados" -m "<trailers>"

  git add docs/ai-architecture.md
  git commit -m "docs(ia): arquitectura de IA con validación humana" -m "<trailers>"

  git add docs/dashboards.md
  git commit -m "docs(dashboards): catálogo de KPIs y tableros" -m "<trailers>"

  git add docs/security.md docs/gis.md docs/deliverables.md
  git commit -m "docs: seguridad, GIS y entregables DOCX/PDF" -m "<trailers>"
  ```

  Si el Paso 3 modificó `docs/requirements-analysis.md`, ese archivo se agrega al commit de su consumidor.

### Tarea 4: ADRs iniciales (paso 11)

**Archivos:**

- Crear: `docs/adr/README.md` (índice: número, título, estado, fecha, ADRs de wasichai relacionados)
- Crear: `docs/adr/0001-…md` … `docs/adr/0025-…md`

**Interfaces:**

- Consume: todos los documentos anteriores y la lista de decisiones del plan aprobado (sección 0.3), ampliada así:

| # | Decisión | Estado |
|---|---|---|
| 0001 | Construir sobre wasichai | Aceptado |
| 0002 | PostGIS / SRID 4326 + CRS de trabajo | Aceptado |
| 0003 | Abstracción de IA | Propuesto |
| 0004 | Embabel: sugerencia ≠ comando | Propuesto |
| 0005 | RAG / pgvector en F7 | Propuesto |
| 0006 | Autorización y alcance | Aceptado; el alcance por proyecto queda Propuesto |
| 0007 | Modelo como código | Aceptado |
| 0008 | Código propio solo donde la metadata no alcanza; API del portal | Aceptado |
| 0009 | Nombres técnicos en inglés, docs y ENUM en español | Aceptado |
| 0010 | Repositorios `sgspe` + `sgspe-ui` | Aceptado |
| 0011 | Auditoría | Aceptado |
| 0012 | Estados temporales materializados (`deadline_status`) | Aceptado |
| 0013 | Entregables DOCX-first | Propuesto |
| 0014 | Evidencias y almacenamiento de objetos | Propuesto |
| 0015 | Mapas de informe vía GeoServer GetMap | Propuesto |
| 0016 | KPIs en vivo + ECharts | Propuesto |
| 0017 | Datos personales y anonimización | Aceptado |
| 0018 | Notificaciones | Propuesto |
| 0019 | Importación geoespacial con reconciliación | Propuesto |
| 0020 | Autenticación JWT de wasichai endurecida | Aceptado |
| 0021 | Reglas de workflow: metadata + guardas + motivo | Aceptado |
| 0022 | QCS en tres objetos | Aceptado |
| 0023 | Separación actor–cargo–grupo de interés–comunidad–área de influencia | Aceptado |
| 0024 | Interfaz: portal + `/admin` | Aceptado |
| 0025 | Concurrencia e idempotencia del portal | Propuesto |

**Encargo:**

- Formato MADR en español, con secciones `## Estado`, `## Contexto`, `## Opciones consideradas`, `## Decisión`,
  `## Consecuencias` y `## Referencias`.
- Se citan los ADR de wasichai como `WAS-ADR-00NN` y los requisitos por ID.
- Ningún ADR repite una decisión de plataforma. Si wasichai ya la tomó, se cita y se explica solo lo propio de SGSPE.

**Etapa del verificador:** `adrs`

- [ ] **Paso 1:** `check_docs.py --stage adrs` → FALLA (falta `docs/adr/README.md`).
- [ ] **Paso 2:** despachar el implementador.
- [ ] **Paso 3:** verificadores → `errores=0`, `coincidencias: 0`.
- [ ] **Paso 4:** revisor de cumplimiento; correcciones.
- [ ] **Paso 5:** commit.

  ```bash
  git add docs/adr/
  git commit -m "docs(adr): decisiones de arquitectura iniciales de SGSPE" -m "<trailers>"
  ```

### Tarea 5: Trazabilidad (paso 10) y roadmap (paso 12)

**Archivos:**

- Crear: `docs/requirements-traceability.md`
- Crear: `docs/roadmap.md`

**Interfaces:**

- Consume: todos los IDs, objetos, endpoints, pantallas y fases de los documentos anteriores.
- Produce: la matriz completa y el plan de implementación conciso.

**Encargo:**

- **Trazabilidad:**
  - Tabla con la cabecera exacta `| Requirement | Source | Module | Entity | API | UI | Test |` más las columnas
    `| Fase | Estado |`.
  - Una fila por cada RF y RQ (y por los RNF y RN relevantes), con el ID como primera celda.
  - Module = BC + unidad (`model.json` / `sgspe.x`).
  - API = genérica de wasichai o `/api/sgspe/...`.
  - UI = pantalla del portal o de `/admin`.
  - Test = nombre previsto (`*ApiTest`, `test_model.py`, `*.test.tsx`).
  - Además: la trazabilidad inversa por fuente (DOC-01 capítulo → requisitos; DOC-02 → requisitos; USR → requisitos) y
    los huérfanos.
- **Roadmap:**
  - releases MVP / Fase 2 / Fase 3 frente a F0–F8, en el orden del prompt;
  - por fase: alcance, objetos, starters, código propio, KPIs, DoD y riesgos;
  - la Fase 1 detallada según el plan aprobado (pistas A, B y C);
  - cobertura de entregables por fase;
  - decisiones pendientes para el checkpoint;
  - una sección "Validación de la Fase 0" que la Tarea 7 completa.

**Etapa del verificador:** `trace`

- [ ] **Paso 1:** `check_docs.py --stage trace` → FALLA.
- [ ] **Paso 2:** despachar el implementador.
- [ ] **Paso 3:** verificadores → `errores=0` (cada RF/RQ tiene su fila), `coincidencias: 0`.
- [ ] **Paso 4:** revisor de cumplimiento; correcciones.
- [ ] **Paso 5:** dos commits.

  ```bash
  git add docs/requirements-traceability.md
  git commit -m "docs(trazabilidad): matriz requisito-fuente-módulo-entidad-API-UI-test" -m "<trailers>"

  git add docs/roadmap.md
  git commit -m "docs(roadmap): fases F0-F8 y plan de implementación" -m "<trailers>"
  ```

### Tarea 6: Índice, historia y plan versionado (coordinador)

**Archivos:**

- Crear: `docs/README.md`: índice, cómo leer, convenciones (IDs, marcadores, idioma, anonimización), estado de la Fase
  0, fuentes (sin copiarlas).
- Crear: `docs/HISTORY.md`: la entrada de la Fase 0, la más reciente primero, como en wasichai.
- Crear: `docs/superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md`: copia del plan aprobado.
- Modificar: `README.md`: qué es SGSPE, estado, enlaces a `docs/` y a `sgspe-ui`.

**Etapa del verificador:** `all`

- [ ] **Paso 1:** `check_docs.py --stage all` → FALLA (faltan `docs/README.md` y `docs/HISTORY.md`).
- [ ] **Paso 2:** escribir los cuatro archivos y copiar el plan.

  ```bash
  cp ~/.claude/plans/analiza-los-documentos-de-hashed-lamport.md \
    docs/superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md
  ```

- [ ] **Paso 3:** verificadores → `etapa=all errores=0`; `coincidencias: 0`.
- [ ] **Paso 4:** commit.

  ```bash
  git add README.md docs/README.md docs/HISTORY.md docs/superpowers/plans/
  git commit -m "docs: índice, historia y planes de la fase 0" -m "<trailers>"
  ```

### Tarea 7: Validación de consistencia (paso 13)

**Archivos:** cualquiera de `docs/**` según los hallazgos; `docs/roadmap.md` (sección "Validación de la Fase 0").

- [ ] **Paso 1:** ejecutar los dos verificadores en la etapa `all` → `errores=0`, `coincidencias: 0`.
- [ ] **Paso 2:** despachar un revisor general (superpowers:requesting-code-review, adaptado a documentos) con este
  checklist:
  - [ ] cada BC aparece en la arquitectura, el catálogo, los roles y el roadmap;
  - [ ] cada workflow tiene un único estado inicial y sus estados finales, y sus roles existen;
  - [ ] ningún objeto `appendOnly` tiene relaciones M2M;
  - [ ] cada objeto `apiOnly` tiene su endpoint en `api.md`;
  - [ ] cada geometría está en SRID 4326 y no es requerida ni única;
  - [ ] ninguna capa sensible se publica en GeoServer;
  - [ ] cada CON tiene resolución o una decisión pendiente; cada ASM indica cómo revertirse; cada GAP tiene su pregunta;
  - [ ] cada ADR de wasichai citado existe (0001–0044);
  - [ ] pgvector solo aparece en F7;
  - [ ] están cubiertos los mandatos del prompt (§0, §23, §25, §26) y USR-01..09;
  - [ ] las versiones son las mismas en todos los documentos;
  - [ ] los diagramas Mermaid son válidos.
- [ ] **Paso 3:** aplicar las correcciones y volver al Paso 1 hasta que quede limpio.
- [ ] **Paso 4:** completar "Validación de la Fase 0" en `docs/roadmap.md` con el resultado y la fecha.
- [ ] **Paso 5:** superpowers:verification-before-completion. Volver a ejecutar ambos verificadores y además:
  - `git ls-files`: sin transitorios;
  - `git log --format=%s main..HEAD`: todos los asuntos son Conventional Commits.
- [ ] **Paso 6:** commit.

  ```bash
  git add <archivos corregidos explícitos> docs/roadmap.md
  git commit -m "docs: validación de consistencia de la fase 0" -m "<trailers>"
  ```

### Tarea 8: Checkpoint con el usuario (pausa obligatoria; no hay F1 sin OK)

- [ ] **Paso 1:** resumen en el chat:
  - hallazgos principales;
  - CON y GAP críticos;
  - ADRs;
  - el plan conciso de implementación (paso 12);
  - las brechas UPS.
- [ ] **Paso 2:** preguntar (AskUserQuestion):
  1. aprobación de los documentos o cambios;
  2. push de la rama y PR;
  3. abrir issues upstream UPS-01..12;
  4. BD de desarrollo en el VPS y secreto `PACKAGES_TOKEN`;
  5. spike opcional de entregables/GetMap;
  6. opción para el alcance por proyecto (UPS-01): esperar la extensión o arrancar F2 solo con org/rol.
- [ ] **Paso 3:** registrar las respuestas en `docs/HISTORY.md` y en `docs/roadmap.md` (commit) antes de abrir la Fase 1.
