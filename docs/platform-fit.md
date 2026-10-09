# Encaje con wasichai

Sistema de Gestión Social para Proyectos Extractivos · Fase 0 (descubrimiento) · versión 1.0 · 2026-10-07

SGSPE se construye sobre wasichai y wasichai-ui, como srtm y caja [USR-03]. El prompt maestro fue escrito para otra pila
(Spring Web, JPA, Flyway para todo, auditoría y RBAC propios), así que cada uno de sus mandatos técnicos y de proceso
necesita una respuesta explícita: qué parte resuelve la plataforma, qué parte agrega SGSPE y qué falta [PRM-01 §3–§27]
(CON-039). Este documento da esa respuesta mandato por mandato, junto con las capacidades de wasichai que usa cada módulo,
las brechas upstream, los patrones de srtm y caja que se reutilizan y las versiones fijadas. Los IDs se definen en el
[análisis de requisitos](requirements-analysis.md); la arquitectura está en [Arquitectura de SGSPE](architecture.md) y el
contrato de la API propia, en [API de SGSPE](api.md).

## 1. Cómo leer este documento

### 1.1 Veredictos y columnas

| Veredicto | Significado |
|---|---|
| Plataforma | wasichai lo resuelve; SGSPE solo lo configura o lo usa |
| Plataforma + SGSPE | wasichai da la base y SGSPE agrega metadata o código propio |
| SGSPE | Lo construye SGSPE, porque wasichai no lo tiene o porque es del dominio |
| Sustituido | El mandato se reemplaza por una decisión de la plataforma o del usuario, registrada como contradicción (CON) |
| Brecha | wasichai no lo ofrece; queda una brecha upstream (UPS) y una mitigación mientras tanto |
| Diferido | Se cumple en una fase posterior del roadmap |
| Proceso | Es una regla de trabajo del proyecto, no una capacidad del producto |

Columnas de las tablas de §2:

- **wasichai:** el ADR o el documento de wasichai que satisface el mandato, citado como `WAS-ADR-00NN` o `WAS-DOC <ruta>`
  (ruta relativa a `wasichai/docs`).
- **ADR de SGSPE:** el ADR de SGSPE que lo decide (§1.2), con enlace a `docs/adr/` desde la tabla de §1.2 y el
  [índice de ADR](adr/README.md).
- **Brecha o CON:** la contradicción, la brecha upstream o el gap que registra lo que falta o lo que cambió.

### 1.2 ADR de SGSPE

Ningún ADR de SGSPE repite una decisión de wasichai: la cita y explica solo lo propio de SGSPE (RNF-MNT-008). Títulos y
estados copiados del [índice de ADR](adr/README.md).

| ADR | Título | Estado |
|---|---|---|
| [ADR-0001](adr/0001-construir-sobre-wasichai.md) | Construir SGSPE sobre wasichai | Aceptado |
| [ADR-0002](adr/0002-geometrias-en-srid-4326-y-crs-de-trabajo.md) | Geometrías en SRID 4326 con CRS de trabajo por proyecto | Aceptado |
| [ADR-0003](adr/0003-abstraccion-de-ia.md) | Abstracción de IA sobre wasichai-agent | Propuesto (se acepta en F7) |
| [ADR-0004](adr/0004-la-sugerencia-de-ia-no-es-un-comando.md) | La sugerencia de IA no es un comando de dominio | Propuesto (se acepta en F3) |
| [ADR-0005](adr/0005-rag-y-pgvector.md) | RAG en F7 y pgvector solo con justificación medida | Propuesto (se acepta en F7) |
| [ADR-0006](adr/0006-autorizacion-y-alcance-por-proyecto.md) | Autorización por roles y alcance por proyecto | Aceptado; el alcance por proyecto, Propuesto (F2, con la opción del checkpoint) |
| [ADR-0007](adr/0007-el-modelo-como-codigo.md) | El modelo como código en `model/` | Aceptado |
| [ADR-0008](adr/0008-codigo-propio-y-api-del-portal.md) | Código propio solo donde la metadata no alcanza, y API del portal | Aceptado |
| [ADR-0009](adr/0009-nombres-tecnicos-en-ingles.md) | Nombres técnicos en inglés; documentación y valores de ENUM en español | Aceptado |
| [ADR-0010](adr/0010-repositorios-sgspe-y-sgspe-ui.md) | Repositorios `sgspe` y `sgspe-ui` | Aceptado |
| [ADR-0011](adr/0011-auditoria.md) | Auditoría sobre el `audit_log` de wasichai | Aceptado |
| [ADR-0012](adr/0012-estados-temporales-materializados.md) | Estados temporales materializados por trabajos programados | Aceptado |
| [ADR-0013](adr/0013-entregables-docx-primero.md) | Entregables DOCX primero, con PDF por Gotenberg | Propuesto (se acepta en F8) |
| [ADR-0014](adr/0014-almacenamiento-de-objetos.md) | Evidencias y entregables en almacenamiento de objetos | Aceptado |
| [ADR-0015](adr/0015-mapas-de-informe-con-geoserver.md) | Mapas de informe con GeoServer GetMap | Propuesto (se acepta en F6) |
| [ADR-0016](adr/0016-kpis-en-vivo-y-echarts.md) | KPIs calculados en vivo y ECharts como librería de gráficos | Propuesto (se acepta en F2) |
| [ADR-0017](adr/0017-datos-personales-y-anonimizacion.md) | Datos personales y anonimización | Aceptado |
| [ADR-0018](adr/0018-notificaciones.md) | Notificaciones en la aplicación y abstracción del correo | Aceptado |
| [ADR-0019](adr/0019-importacion-geoespacial-con-reconciliacion.md) | Importación geoespacial con preparación y reconciliación | Propuesto (se acepta en F2) |
| [ADR-0020](adr/0020-autenticacion-jwt-endurecida.md) | Autenticación con el JWT de wasichai, endurecida | Aceptado |
| [ADR-0021](adr/0021-reglas-de-workflow.md) | Reglas de workflow: metadata, guardas y motivo | Aceptado |
| [ADR-0022](adr/0022-qcs-en-tres-objetos.md) | Quejas, consultas y solicitudes en tres objetos | Aceptado |
| [ADR-0023](adr/0023-separacion-de-actores-y-territorio.md) | Separación de persona, cargo, grupo de interés, comunidad y área de influencia | Aceptado |
| [ADR-0024](adr/0024-interfaz-portal-y-admin.md) | Interfaz: portal en `/` y administración en `/admin` | Aceptado |
| [ADR-0025](adr/0025-concurrencia-e-idempotencia.md) | Concurrencia e idempotencia en la API del portal | Propuesto (se acepta en F2) |
| [ADR-0026](adr/0026-topologia-puertos-y-observabilidad.md) | Topología de despliegue, puertos y observabilidad | Aceptado en parte (puertos, correlación, logs y salud, que rigen desde F1); la topología de producción, Propuesta (al responder GAP-022) |

## 2. Mandatos del prompt (§3 a §27)

### 2.1 §3 Arquitectura: tecnologías y estilo

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Java o Kotlin según el repositorio | Sustituido | Kotlin 2.4.20 sobre JDK 25, como wasichai y caja | WAS-ADR-0024; WAS-DOC guides/build-your-app.md | ADR-0001 | CON-039 | RNF-MNT-001 |
| Spring Boot | Plataforma | Spring Boot 4.1.1, alineado por `wasichai-bom` 0.6.0 | WAS-ADR-0024 | ADR-0001 | — | RNF-MNT-001 |
| Spring Web | Sustituido | Spring WebFlux reactivo; la E/S bloqueante del código propio va a `Dispatchers.IO` | WAS-DOC architecture/overview.md | ADR-0001 | CON-039 | RNF-MNT-005 |
| Spring Security | Plataforma | Servidor de recursos OAuth2 de WebFlux que valida el JWT HS256 de wasichai; la cadena de core va en `@Order(0)` | WAS-ADR-0010; WAS-DOC security/authentication.md | ADR-0020 | CON-056 | RQ-SEC-001 |
| Spring Data JPA e Hibernate | Sustituido | Prohibidos: SQL por `DatabaseClient`, registros dinámicos por `RecordService`, una tabla física por objeto | WAS-ADR-0003; WAS-ADR-0004; WAS-DOC architecture/overview.md | ADR-0001, ADR-0008 | CON-039 | RNF-MNT-005 |
| PostgreSQL | Plataforma | PostgreSQL 18 | WAS-ADR-0002 | ADR-0002 | — | RNF-MNT-001 |
| PostGIS | Plataforma + SGSPE | PostGIS 3.6 con wasichai-gis; análisis espacial propio en `sgspe.territory` | WAS-ADR-0002; WAS-ADR-0019; WAS-ADR-0027 | ADR-0002 | CON-036 | RQ-GIS-006, RQ-GIS-017 |
| Flyway | Plataforma + SGSPE | Flyway por módulo para los esquemas de la plataforma; el esquema de negocio es `model/*.json` aplicado con `apply.py`; Flyway propio solo para las tablas fijas del esquema `sgspe` | WAS-ADR-0008; WAS-ADR-0026 | ADR-0007 | CON-052 | RNF-MNT-002 |
| Bean Validation | Sustituido | Validación en tiempo de ejecución desde la metadata (en el servidor y en el cliente), más guardas y validación en los endpoints propios | WAS-ADR-0003; WAS-ADR-0040 | ADR-0021 | CON-039 | RQ-AUD-004 |
| OpenAPI | Sustituido | La API se documenta en Markdown, como en caja y srtm; OpenAPI queda como opción futura | WAS-DOC api/rest.md | ADR-0008 | CON-055 | RNF-INT-004 |
| Testcontainers | Plataforma | `WasichaiIntegrationTest` con `postgis/postgis:18-3.6`; con Docker remoto, en CI o contra una base externa | WAS-DOC modules/testing.md | — | — | RNF-TST-001 |
| JUnit | Plataforma | JUnit Platform, con la etiqueta `integration` para las pruebas contra la base | WAS-DOC modules/testing.md | — | — | RNF-TST-001 |
| ArchUnit | Sustituido | Pruebas de arquitectura por escaneo de fuentes, como en caja, con autoprueba de cada regla | — | ADR-0008 | CON-039 | RNF-MNT-005, RQ-SEC-005 |
| Actuator | Plataforma | Viene en el starter; `/actuator/health/**` es público | WAS-DOC modules/core.md | ADR-0026 | — | RNF-OBS-001 |
| Spring AI | Sustituido | Embabel dentro de wasichai-agent, neutral al proveedor | WAS-ADR-0015; WAS-ADR-0031 | ADR-0003 | CON-039 | RQ-AI-005 |
| Embabel | Plataforma + SGSPE | Runtime del asistente de wasichai y de los agentes propios de `sgspe.ai` (F7) | WAS-ADR-0015 | ADR-0004 | — | RQ-AI-014 |
| Abstracción de LLM | Plataforma | El proveedor es configuración de Embabel; Anthropic por defecto | WAS-ADR-0015; WAS-DOC modules/agent.md | ADR-0003 | GAP-015 | RQ-AI-005 |
| Embeddings, RAG y búsqueda semántica | Diferido | F7, con pgvector solo si una medición lo justifica; la migración de core crea la extensión `vector` cuando el servidor la trae | WAS-ADR-0002; WAS-ADR-0026 | ADR-0005 | CON-035 | RQ-AI-008, RF-AIK-006 |
| Tool calling | Plataforma + SGSPE | Herramientas de solo lectura del asistente; los agentes propios también solo leen | WAS-ADR-0014 | ADR-0004 | CON-044 | RQ-AI-011, RQ-AI-013 |
| Salidas estructuradas | SGSPE | Los agentes devuelven datos estructurados que el código de SGSPE guarda como `ai_suggestion` o `commitment_candidate` (F7) | WAS-ADR-0015 | ADR-0004 | — | RQ-AI-001 |
| React 19 | Plataforma + SGSPE | React 19.3 con `@wasichai/*` 0.5.0 | WAS-ADR-0028 | ADR-0024 | — | RNF-MNT-001 |
| TypeScript, Vite y React Router | Plataforma | TypeScript 7.0, Vite 8.3 y React Router 8.4, las versiones de caja-ui (§6) | WAS-ADR-0028 | ADR-0024 | — | RNF-MNT-001 |
| Tailwind CSS | Plataforma | Tailwind 4 con `@wasichai/ui/theme.css` y temas por tokens | WAS-ADR-0035; WAS-DOC modules/core.md | ADR-0024 | — | RNF-MNT-001 |
| i18n | Plataforma + SGSPE | i18next, español por defecto e inglés; las etiquetas de la metadata existen solo en español | WAS-ADR-0028 | ADR-0009 | CON-051 | RNF-I18N-001 |
| TanStack Query | Plataforma | TanStack Query 5, sin reintentos ante un 4xx | WAS-ADR-0028 | ADR-0024 | — | RNF-MNT-001 |
| Zod y React Hook Form | SGSPE | Con los primeros formularios propios del portal (F2); los formularios de metadata los dibuja wasichai | WAS-ADR-0003; WAS-ADR-0012 | ADR-0024 | — | RNF-MNT-001 |
| Mapas interactivos y capas territoriales | Plataforma + SGSPE | `MapView`, `GeometryField` y el componente `MAP` de wasichai; mapa territorial propio con MapLibre 6.10.0 | WAS-ADR-0005; WAS-DOC modules/gis.md | ADR-0002 | CON-060 | RQ-GIS-001, RQ-GIS-014 |
| Geometrías | Plataforma | Campo GEOMETRY con su propio SRID; la API habla GeoJSON EPSG:4326 | WAS-ADR-0007; WAS-ADR-0019 | ADR-0002 | CON-037 | RQ-GIS-006, RNF-INT-005 |
| Consultas espaciales, buffers, intersecciones y proximidad | SGSPE | SQL de PostGIS detrás de una compuerta de permisos en `sgspe.territory` (F6); `RecordService` solo filtra por `bbox` | WAS-ADR-0004; WAS-ADR-0038 | ADR-0002 | — | RQ-GIS-002, RQ-GIS-017 |
| Áreas de influencia | SGSPE | `area_of_influence` versionada con miembros curados; el polígono solo sugiere | — | ADR-0023 | CON-025 | RF-TER-005, RN-TER-004 |
| Monolito modular | Plataforma | Un desplegable; los módulos de wasichai son librerías con starters | WAS-ADR-0001; WAS-ADR-0024 | ADR-0001 | — | RNF-MNT-001 |
| No dividir en microservicios | Plataforma | El mismo desplegable; extraer un servicio sería mover un paquete detrás de un puerto | WAS-ADR-0001 | ADR-0001 | — | RNF-MNT-001 |
| Package-by-domain, sin capas `controller/service/repository` para todo | SGSPE | Paquetes `sgspe.*` por capacidad del dominio; nunca código por objeto | WAS-ADR-0003 | ADR-0008 | CON-040 | RNF-MNT-005 |
| Alta cohesión, bajo acoplamiento, bounded contexts claros, módulos independientes y servicios extraíbles a futuro | SGSPE | Un paquete por capacidad, con dependencias en una sola dirección: los paquetes de dominio no dependen de `sgspe.dashboard`, `sgspe.deliverable` ni `sgspe.ai`, y una prueba de arquitectura lo verifica ([Arquitectura §5.3](architecture.md#53-reglas-de-dependencia-entre-paquetes)) | WAS-ADR-0001 | ADR-0008 | — | RNF-MNT-005, RNF-TST-001 |

### 2.2 §4 Bounded contexts

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Proponer y validar contra los documentos los 18 contextos | SGSPE | Veinte contextos: BC-11 se fusiona en BC-04 para el MVP y se agregan BC-19 y BC-20 [domain-model §5] | — | ADR-0023 | — | ASM-045 |
| Definir MVP, Fase 2 y Fase 3 | SGSPE | Release 1 (MVP) = F1–F4; Release 2 = F5–F6; Release 3 = F7–F8 | — | — | — | ASM-044 |
| No implementar todos los contextos de inmediato | Proceso | Cada contexto llega en la fase de su roadmap | — | — | — | RNF-MNT-007 |

### 2.3 §5 Modelo de dominio

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Modelo de dominio explícito, validado contra los documentos | SGSPE | 89 objetos con campos, agregados, invariantes y el mecanismo de cada una [domain-model §6, §7] | WAS-ADR-0003 | ADR-0023 | — | — |
| Las entidades sugeridas son un punto de partida | SGSPE | Destino de cada entidad del prompt [requirements-analysis §9.2] | — | ADR-0022, ADR-0023 | CON-031, CON-033, CON-038 | — |
| Estado `OVERDUE` almacenado en el compromiso | Sustituido | `deadline_status` derivado por un trabajo programado | WAS-ADR-0013; WAS-ADR-0039 | ADR-0012 | CON-028 | RN-WFL-001 |
| Sin tabla genérica de contactos | SGSPE | `contact_method` de una persona u organización, con consentimiento | — | ADR-0023 | — | RF-STK-001, RNF-PRIV-004 |
| No confundir comunidad, área geográfica, área de influencia y grupo de interés | SGSPE | Objetos separados con sus reglas | — | ADR-0023 | CON-038 | RN-STK-008, RN-TER-001 |
| Documentos con historial, versiones y relaciones con varios registros | Plataforma + SGSPE | `evidence` inmutable con `replaces` y vínculos M2M; documentos de un registro con wasichai-documents | WAS-ADR-0023; WAS-ADR-0040 | ADR-0014 | CON-046, UPS-07 | RF-DOC-001, RQ-REP-029 |
| Evaluar la captura sin conexión si los documentos la requieren | Diferido | Fuera del MVP | — | — | GAP-008 | ASM-021 |
| Task, WorkflowInstance, Approval, Escalation y Notification | Plataforma + SGSPE | `task` con WF-N, estado en el registro, transiciones y `APPROVE`, escalamiento por trabajos, avisos en tablas fijas | WAS-ADR-0013; WAS-ADR-0042 | ADR-0018, ADR-0021 | UPS-11, CON-058 | RF-WFL-001, RF-WFL-002, RF-WFL-004, RF-NTF-001 |
| AuditEvent con actor, acción, entidad, fecha, valores anterior y nuevo, origen y correlationId, append-only | Plataforma + SGSPE | `audit_log` append-only; el origen va en el motivo y la correlación en los logs mientras falten en la fila | WAS-ADR-0040; WAS-ADR-0041 | ADR-0011 | CON-057, UPS-03, UPS-12 | RQ-AUD-001, RQ-AUD-006, RQ-AUD-007, RQ-AUD-010 |
| En la alerta temprana la IA detecta señales y la decisión es humana | SGSPE | `WarningWriteGuard` y roles humanos en WF-J | WAS-ADR-0043 | ADR-0004 | — | RN-EWS-001, RQ-SEC-014 |

### 2.4 §6 GIS y PostGIS

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| No tratar el GIS como latitud y longitud opcionales | Plataforma + SGSPE | Geometrías tipadas por campo, índices GIST y CRS de trabajo por proyecto | WAS-ADR-0019 | ADR-0002 | — | RQ-GIS-004, RQ-GIS-006 |
| Evaluar Point, MultiPoint, Polygon, MultiPolygon, LineString y MultiLineString | Plataforma + SGSPE | La API y la importación aceptan `MULTI*`; la interfaz de wasichai no las dibuja | WAS-DOC gis/geometry.md | ADR-0002 | CON-060 | RQ-GIS-020 |
| Operaciones intersects, contains, within, distance, buffer, nearest y agregación | SGSPE | SQL de PostGIS en `sgspe.territory` (F6), con compuerta de permisos | WAS-ADR-0004 | ADR-0002 | — | RQ-GIS-002, RQ-GIS-017 |
| Los nueve casos de uso espaciales | SGSPE | Un endpoint por consulta ([API §4.2](api.md#42-bc-02-territorio-y-sig)) y la ficha territorial, cuyas cifras son KPI-TER-01 de `sgspe.dashboard` | — | ADR-0002 | — | RQ-GIS-002, RQ-GIS-005, RQ-GIS-014, RQ-DSH-034 |
| Índices espaciales | Plataforma | GIST automático por geometría; índices declarados por proyecto, estado, fechas y códigos | WAS-DOC gis/geometry.md; WAS-ADR-0036 | ADR-0007 | — | RQ-GIS-016 |
| SRID configurable según el territorio, sin SRID universal | Plataforma + SGSPE | Almacenamiento en 4326 y `working_crs` por proyecto; el código lee el SRID de la metadata | WAS-ADR-0019; WAS-ADR-0027 | ADR-0002 | CON-037 | RQ-GIS-022, ASM-002 |
| ADR de arquitectura GIS | SGSPE | ADR-0002, con ADR-0015 (Mapas de informe con GeoServer GetMap) y ADR-0019 (Importación geoespacial con preparación y reconciliación) | WAS-ADR-0005; WAS-ADR-0006 | ADR-0002, ADR-0015, ADR-0019 | — | RNF-MNT-008 |

### 2.5 §7 Roles y seguridad

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| RBAC | Plataforma + SGSPE | Permisos (rol, objeto, acción), permisos por campo y las acciones declaradas del catálogo de RQ-SEC-004. Sin `own_records_only`: quien no tiene `EDIT_ANY` solo cambia o borra lo que creó, y lo aplica `EditAnyGuard` (ASM-069) | WAS-ADR-0010; WAS-ADR-0020; WAS-ADR-0042 | ADR-0006 | — | RQ-SEC-004, RQ-SEC-008 |
| Limitar por organización | Plataforma | Tenant tomado del token; tablas físicas por organización | WAS-ADR-0004; WAS-ADR-0010 | ADR-0006 | — | RQ-SEC-002 |
| Limitar por proyecto, región y territorio | Brecha | Escritura acotada por asignación (`ProjectScopeGuard`). La lectura es una decisión pendiente en el checkpoint, con recomendación A: a nivel de tenant en el MVP con un solo cliente, y UPS-01 antes de la producción con varios proyectos | — | ADR-0006 | CON-041, UPS-01, GAP-025 | RQ-SEC-006 |
| Limitar por módulo y operación | Plataforma | Un módulo es un conjunto de objetos; una operación es una acción CRUD o declarada | WAS-ADR-0042 | ADR-0006 | UPS-06 | RQ-SEC-004, RF-IAM-002 |
| Los 17 roles iniciales | Plataforma + SGSPE | 15 roles declarados en `model/roles.json`; `SUPER_ADMIN` y `ORGANIZATION_ADMIN` como `ADMIN` | WAS-ADR-0020; WAS-DOC api/rest.md | ADR-0006 | CON-059, UPS-10 | RQ-SEC-003, ASM-024 |
| Evitar `if role == ...` disperso; autorización centralizada | SGSPE | El código decide por permisos y acciones declaradas; una prueba de arquitectura busca literales de rol | WAS-ADR-0042 | ADR-0006 | — | RQ-SEC-005 |

### 2.6 §8 Workflows

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Workflows explícitos y testeables, no escondidos en if/else | Plataforma + SGSPE | Workflows en metadata con roles por transición, guardas y pruebas por transición permitida y denegada | WAS-ADR-0013; WAS-ADR-0018; WAS-ADR-0040 | ADR-0021 | CON-042 | RNF-TST-001 |
| WF-A creación de proyecto | SGSPE | WF-A siempre activo: una organización que no revisa proyectos usa una variante de su definición, sin `submit_for_review` ni `return_to_draft`, y nunca un workflow desactivado, que dejaría los registros nuevos sin estado; precondición para llegar a `active` | WAS-ADR-0013 | ADR-0021 | — | RF-PRJ-003, ASM-010, ASM-062 |
| WF-B grupo de interés | SGSPE | Sin estado de monitoreo; la relación va en `relationship_status` | WAS-ADR-0013 | ADR-0021 | CON-030 | RF-STK-014 |
| WF-C interacción | SGSPE | El flujo depende del tipo; la cancelación es final y con motivo | WAS-ADR-0013 | ADR-0021 | CON-032 | RN-ENG-004, RF-ENG-006 |
| WF-D compromiso con vencimiento, escalamiento, bloqueo, reprogramación, dependencia, evidencia y aprobación | Plataforma + SGSPE | `deadline_status` y escalamiento por `DeadlineJob`; estado `blocked`; `commitment_reschedule`; `commitment_dependency`; `delivery` con `evidence`; aprobación con segregación de funciones | WAS-ADR-0039; WAS-ADR-0042 | ADR-0012, ADR-0021 | CON-027, CON-028 | RF-COM-002, RF-COM-003, RF-COM-004, RF-WFL-004, RN-COM-001, RN-COM-005 |
| WF-E queja | SGSPE | WF-E1 (`grievance`), WF-E2 (`inquiry`) y WF-E3 (`stakeholder_request`), un workflow por objeto | WAS-ADR-0013 | ADR-0022 | CON-029, CON-033 | RF-GRV-002, RF-GRV-003, RF-GRV-004 |
| WF-F riesgo social | SGSPE | La revisión semanal es un trabajo y una tarea, no un estado | WAS-ADR-0039 | ADR-0021 | — | RF-RSK-003, RN-RSK-001 |
| WF-G programa social | SGSPE | Ciclo del prompt con la guarda del acuerdo de sinergia | WAS-ADR-0013 | ADR-0021 | GAP-007 | RF-INV-003, RN-INV-004 |

Los workflows que el prompt no tiene (WF-H a WF-P) siguen las mismas reglas [requirements-analysis §11].

### 2.7 §9 IA, Spring AI y Embabel

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| La IA complementa el sistema y no reemplaza decisiones de negocio | SGSPE | Sugerencias guardadas aparte; solo una persona acepta | WAS-ADR-0014 | ADR-0004 | — | RQ-AI-001, RQ-AI-003 |
| AI-01 extraer de documentos | Diferido | Agente propio que guarda sugerencias de extracción (F7) | WAS-ADR-0015 | ADR-0004 | — | RF-AIK-001 |
| AI-02 borrador de acta | Diferido | Sugerencia de tipo BORRADOR DE ACTA para la minuta de la interacción (F7) | WAS-ADR-0015 | ADR-0004 | — | RF-AIK-002 |
| AI-03 posibles compromisos, con validación humana | Diferido | `commitment_candidate` con evidencia y confianza; solo una persona lo acepta (F7; el alta humana existe desde F3) | WAS-ADR-0015 | ADR-0004 | — | RF-AIK-003, RF-COM-010 |
| AI-04 resumen ejecutivo | Diferido | Sugerencias de resumen (F7) y de secciones de entregable (F8, cuando existe `deliverable_section`), que una persona copia o edita | WAS-ADR-0015 | ADR-0004 | — | RF-AIK-004, RQ-AI-012 |
| AI-05 preguntas sobre los datos del sistema | Plataforma + SGSPE | Asistente de solo lectura de wasichai-agent, como el usuario que pregunta, llamado en proceso por `sgspe.ai`, que verifica la activación por organización y registra la interacción (F7). Mientras no exista UPS-14, solo en organizaciones con la IA activada y para roles sin lectura de los campos restringidos (hoy, `ANALYST`), una decisión del checkpoint (`security.md` §15) | WAS-ADR-0014 | ADR-0003 | UPS-14 | RF-AIK-005, RQ-AI-009 |
| AI-06 RAG documental | Diferido | Fragmentos con citas, filtrados por tenant, proyecto, clasificación y permisos (F7) | WAS-ADR-0002 | ADR-0005 | CON-035 | RF-AIK-006, RQ-AI-008 |
| AI-07 detección de duplicados | Diferido | Propone fusiones o vínculos y nunca fusiona sola (F7) | WAS-ADR-0015 | ADR-0004 | — | RF-AIK-007, RQ-AI-015 |
| AI-08 clasificación asistida con resultado, evidencia, confianza y modelo | Diferido | Sugerencias de clasificación con su evidencia y su modelo (F7) | WAS-ADR-0015 | ADR-0004 | — | RF-AIK-008, RQ-AI-004 |
| Embabel para flujos agénticos explícitos | Plataforma + SGSPE | Documento, extracción, entidades, candidatos, validación, aprobación humana y comando de dominio | WAS-ADR-0015 | ADR-0004 | — | RQ-AI-014 |
| Ningún agente modifica la base sin comandos de dominio y autorización | Plataforma + SGSPE | Agentes de solo lectura; el código de SGSPE guarda sugerencias; aceptar ejecuta el comando como la persona | WAS-ADR-0014 | ADR-0004 | CON-044 | RQ-AI-001, RQ-AUD-012 |
| Herramientas bien definidas, incluidas `createDraftCommitment` y `createDraftMeetingSummary` | SGSPE | Herramientas de lectura; las de borrador producen sugerencias, nunca registros | WAS-ADR-0014 | ADR-0004 | CON-044 | RQ-AI-013 |
| Separar la sugerencia del comando de dominio | SGSPE | `ai_suggestion` y `commitment_candidate` son `apiOnly`; aceptar es un endpoint propio | WAS-ADR-0040 | ADR-0004 | — | RQ-AI-001, RQ-AI-003 |

### 2.8 §10 Pasarela de IA

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Cambiar entre Anthropic, OpenAI u otros sin reescribir el dominio | Plataforma | El proveedor es un starter de Embabel; Anthropic viene por defecto y se puede excluir | WAS-ADR-0015; WAS-ADR-0031 | ADR-0003 | GAP-015 | RQ-AI-005 |
| Guardar la metadata de cada interacción (proveedor, modelo, versión de prompt, tokens, latencia, confianza, validación) | SGSPE | `ai_interaction` append-only; la validación humana y el resultado se leen de la sugerencia | WAS-ADR-0040 | ADR-0003 | — | RF-AIK-009, RQ-AUD-003 |
| Nunca almacenar secretos | SGSPE | Las claves llegan por variables de entorno y no se guardan ni se registran | WAS-DOC modules/agent.md | ADR-0003 | — | RQ-AI-006, RQ-AI-009 |
| Versionar prompts | SGSPE | Prompts en el repositorio, referenciados en cada `ai_interaction` | — | ADR-0003 | — | RQ-AI-006 |

### 2.9 §11 API

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| API REST consistente, con rutas como `/api/projects/{id}/commitments` | Plataforma + SGSPE | API genérica de wasichai más `/api/sgspe/**` con recursos en inglés | WAS-DOC api/rest.md | ADR-0008 | CON-048 | RNF-INT-004 |
| Semántica REST | SGSPE | Lecturas con `GET`; comandos como subrecursos con `POST`; estados HTTP de [API §3](api.md#3-convenciones-de-apisgspe) | WAS-DOC api/rest.md | ADR-0008 | — | RNF-INT-004 |
| Paginación, orden y filtros | Plataforma + SGSPE | `page`, `size`, `sort`, `dir`, `q`, `count` y `after`; en la API propia, un parámetro desconocido es 422 | WAS-ADR-0036 | ADR-0008 | — | RNF-INT-001, ASM-054 |
| Validación | Plataforma + SGSPE | `400` con `errors[]` por campo, de la metadata, de las guardas y de los endpoints | WAS-ADR-0003; WAS-ADR-0040 | ADR-0021 | — | RNF-INT-001 |
| Manejo de errores con Problem Details | Plataforma | `application/problem+json` con `type`, `title`, `status`, `detail`, `instance` y `errors[]` | WAS-DOC api/rest.md | ADR-0008 | — | RNF-INT-001 |
| Idempotencia cuando corresponda | SGSPE | `Idempotency-Key` en las creaciones de `/api/sgspe/**`, con la tabla fija `idempotency_key` (ASM-065); las altas de la API genérica no son idempotentes | — | ADR-0025 | UPS-18 | RNF-INT-002, ASM-065 |
| Bloqueo optimista | Brecha | Precondición sobre `updatedAt` mientras la plataforma no lo tenga | — | ADR-0025 | CON-047, UPS-04 | RNF-INT-003, ASM-035 |
| Correlation IDs | Plataforma + SGSPE | `X-Correlation-Id` en peticiones, respuestas y logs; todavía no en la auditoría | — | ADR-0011, ADR-0026 | CON-057, UPS-03 | RQ-AUD-006, ASM-034 |

### 2.10 §12 Eventos de dominio

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Eventos de dominio donde aporten, sin arquitectura de eventos por moda | Plataforma + SGSPE | Listeners (`RecordChangeListener`), automatizaciones encoladas, transiciones auditadas y trabajos programados; sin broker de mensajes | WAS-ADR-0016; WAS-ADR-0025 | ADR-0018, ADR-0021 | CON-043 | RN-NTF-001 |

Cada evento de ejemplo del prompt tiene su mecanismo; los avisos que generan los fija `workflows.md`:

| Evento del prompt | Mecanismo en SGSPE | Fuente |
|---|---|---|
| ProjectCreated | Alta de `project`, auditada como `CREATE` | RQ-AUD-001 |
| EngagementCompleted | Transición de `engagement` a `held` o a `closed`, con sus guardas | RN-ENG-004, RN-ENG-005 |
| CommitmentCreated | Alta de `commitment` por la API genérica o por la aceptación de un candidato o una solicitud | RF-COM-010, RF-GRV-004 |
| CommitmentDueSoon y CommitmentOverdue | `DeadlineJob` cambia `deadline_status` a POR VENCER o VENCIDO y crea el aviso | RN-WFL-001, RF-NTF-001 |
| CommitmentFulfilled | Transición humana `register_fulfillment` de WF-D a `fulfilled`: `CommitmentWriteGuard` exige una entrega con conformidad y su evidencia, y `CommitmentDerivationsListener` fija `fulfilled_date`. Registrar la entrega no la aplica | RN-COM-001, RF-COM-004 |
| GrievanceCreated | Alta de `grievance` con la tarea de acuse por automatización | RF-GRV-002 |
| GrievanceEscalated | Escalamiento por días de atraso en `DeadlineJob` | RF-GRV-006, RF-WFL-004 |
| RiskIdentified y RiskEscalated | Alta de `social_risk`; un nivel ALTO o CRITICO en una revisión genera el aviso | RF-RSK-001, RF-NTF-001 |
| EarlyWarningDetected | `WarningRulesJob` crea `early_warning` en `detected` | RF-EWS-004 |
| ProgramApproved | Transición de WF-G a `planned` | RF-INV-003 |
| EvidenceSubmitted | Alta de `evidence` por su endpoint | RF-DOC-001 |
| ApprovalRequested | Transición a `pending_approval` con su aviso de aprobación pendiente | RF-NTF-001, RF-WFL-002 |

### 2.11 §13 Frontend y §14 UX

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| React 19 con arquitectura por dominio | SGSPE | Portal con una carpeta por sección del dominio y `/admin` de wasichai | WAS-ADR-0028 | ADR-0024 | — | RF-IAM-001 |
| No crear un componente gigante por pantalla | Proceso | Pantallas compuestas con las primitivas de `@wasichai/ui` | WAS-ADR-0028 | ADR-0024 | — | RNF-MNT-001 |
| React Router, TanStack Query, React Hook Form, Zod, Tailwind e i18next | Plataforma + SGSPE | Versiones de caja-ui (§6) | WAS-ADR-0028 | ADR-0024 | — | RNF-MNT-001 |
| Sentirse como una plataforma enterprise | Plataforma + SGSPE | Primitivas y temas de wasichai-ui; tablas de datos y etiquetas ARIA en los gráficos; texto alternativo en los entregables | WAS-ADR-0034; WAS-ADR-0035 | ADR-0024 | — | RQ-DSH-035, RQ-REP-028 |
| Navegación principal de 13 secciones | SGSPE | Árbol del portal en el orden del prompt, con etiquetas en español y hojas según permisos | WAS-ADR-0020 | ADR-0024 | UPS-06 | RNF-USA-001, RF-IAM-002 |

### 2.12 §15 Mapa y §16 Dashboards

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| GIS dashboard con capas de proyectos, áreas, comunidades, centros poblados, grupos de interés, quejas, compromisos, riesgos, programas e inversión | Plataforma + SGSPE | Capas temáticas como GeoJSON autenticado; capas de referencia por WMS detrás del proxy autenticado | WAS-ADR-0006; WAS-ADR-0019 | ADR-0002, ADR-0015 | CON-054 | RQ-GIS-001, RQ-GIS-012, RQ-GIS-014 |
| Ficha contextual al seleccionar una geometría | SGSPE | Ficha de localidad o comunidad (F6): `sgspe.territory` da la geometría y las relaciones, y las cifras son KPI-TER-01 de `sgspe.dashboard`, las mismas de los tableros | — | ADR-0016 | — | RQ-GIS-005, RQ-DSH-034 |
| Tablero ejecutivo | SGSPE | F8, con tendencia mensual | — | ADR-0016 | — | RF-DSH-001, RQ-DSH-002, RQ-DSH-003 |
| Tablero de proyecto | SGSPE | F8 | — | ADR-0016 | — | RF-DSH-002 |
| Tablero de compromisos | SGSPE | Indicadores en F3, consolidado en F8 | — | ADR-0016 | — | RF-DSH-003, RQ-DSH-018 |
| Tablero de quejas | SGSPE | F4, consolidado en F8 | — | ADR-0016 | — | RF-DSH-004, RQ-DSH-022 |
| Tablero de riesgos con mapa de calor probabilidad × impacto | SGSPE | F4, consolidado en F8 | — | ADR-0016 | — | RF-DSH-005, RQ-DSH-026, RQ-DSH-027 |
| Tablero territorial | SGSPE | F6, consolidado en F8 | — | ADR-0016 | — | RF-DSH-006, RQ-DSH-029 |
| Cálculo de los indicadores | Plataforma + SGSPE | En vivo por `RecordService`, sin vistas materializadas; el mismo código para tableros y entregables | WAS-ADR-0036; WAS-ADR-0038 | ADR-0016 | — | RQ-DSH-031, RQ-DSH-034 |
| Gráficos | SGSPE | ECharts como única biblioteca, cargada solo en las rutas de tableros | WAS-ADR-0028 | ADR-0016 | — | RQ-DSH-035, ASM-046 |

### 2.13 §17 Notificaciones y §18 Auditabilidad

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Notificaciones en la aplicación y abstracción de correo | Plataforma + SGSPE | wasichai-notifications (bandeja, SSE y correo); SGSPE decide destinatarios y textos | WAS-ADR-0046; WAS-ADR-0060 | ADR-0018 | CON-058, UPS-11 | RF-NTF-001, ASM-012 |
| Por vencer, vencido, plazo de queja, escalamiento de riesgo, aprobación pendiente y tarea vencida | SGSPE | `DeadlineJob` (JOB-01) y la fuente de avisos de plazo `DeadlineNotices` de wasichai-notifications, con clave estable por aviso | WAS-ADR-0039 | ADR-0018 | — | RF-NTF-001, RF-WFL-004 |
| No notificar desde las entidades; usar eventos de aplicación | SGSPE | Solo listeners y trabajos crean avisos | WAS-ADR-0025 | ADR-0018 | — | RN-NTF-001 |
| Responder WHO, WHAT, WHEN, WHY, FROM y TO | Plataforma | `audit_log` (tabla siguiente) | WAS-ADR-0040; WAS-ADR-0041 | ADR-0011 | — | RQ-AUD-001, RQ-AUD-002 |
| Implementar auditoría | Plataforma + SGSPE | `requiresReason` en los objetos críticos, `appendOnly` en la historia y `export_log` para las salidas | WAS-ADR-0040; WAS-ADR-0041 | ADR-0011 | — | RQ-AUD-003, RQ-AUD-011, ASM-058 |
| No confiar únicamente en logs | Plataforma | La auditoría de negocio vive en la base | WAS-ADR-0040 | ADR-0011 | — | RQ-AUD-014 |
| Auditar toda acción crítica, incluida la administración | Brecha | Modelo y roles solo desde el repositorio; historial por registro | — | ADR-0011 | UPS-02, UPS-05, UPS-12 | RQ-AUD-008, RQ-AUD-009, RQ-AUD-010 |

Dónde vive cada pregunta de auditoría [PRM-01 §18]:

| Pregunta | Dónde vive | Fuente |
|---|---|---|
| Quién | Usuario de `audit_log` (`userEmail`) o cuenta de servicio (`serviceAccount`); nulo para la plataforma y las automatizaciones, que se nombran en el motivo | WAS-ADR-0043; ASM-051 |
| Qué | Objeto, registro y operación (`CREATE`, `UPDATE`, `DELETE`, `ISSUE`); las transiciones y los vínculos son `UPDATE` | WAS-DOC api/rest.md |
| Cuándo | `occurredAt` de la fila | WAS-DOC api/rest.md |
| Por qué | `reason`, del encabezado `X-Change-Reason`, obligatorio en los objetos `requiresReason` | WAS-ADR-0041; RQ-AUD-002 |
| Desde y hacia | `changes` con `before` y `after`, filtrados por los permisos de campo de quien lee | WAS-DOC api/rest.md |
| Correlación y origen | Hoy en los logs y en el motivo; en la fila cuando exista UPS-03 | RQ-AUD-006, RQ-AUD-007 |

### 2.14 §19 Calidad de código y §20 Base de datos

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| SOLID, DRY, KISS, Clean Code, DDD, principios hexagonales e inversión de dependencias | SGSPE | Agregados con invariantes y mecanismo; puertos para el almacenamiento, la conversión a PDF, los mapas y el LLM | WAS-ADR-0025 | ADR-0008 | — | RNF-MNT-005 |
| Evitar clases y servicios dios, utilitarios genéricos y repositorios gigantes | SGSPE | Sin repositorios propios: los registros se leen y escriben con `RecordService`; paquetes por capacidad | WAS-ADR-0003 | ADR-0008 | — | RNF-MNT-005 |
| Evitar máquinas de estado con strings y strings mágicos | Plataforma + SGSPE | Estados definidos en metadata con nombres canónicos; roles leídos de `roles.json`, sin literales en el código | WAS-ADR-0013 | ADR-0009, ADR-0021 | — | RNF-MNT-004, RQ-SEC-005 |
| Evitar lógica de negocio en los controladores | SGSPE | Controladores delgados; las reglas viven en guardas, servicios de comando y funciones puras | WAS-ADR-0040 | ADR-0008 | — | RQ-AUD-004 |
| Evitar lógica de workflow anémica | Plataforma + SGSPE | Estados, transiciones y roles en la metadata de cada workflow, y sus reglas en las guardas del paquete dueño, que corren en cada transición; todo cambio de estado pasa por `WorkflowService` y sus guardas, también el que aplica un comando | WAS-ADR-0013; WAS-ADR-0040 | ADR-0021 | — | RF-WFL-002, RQ-AUD-004 |
| Evitar el mapeo duplicado de DTO | Plataforma + SGSPE | Una sola forma de registro en las dos superficies (`id`, `state`, `attributes`, `geometries`) y sin entidades propias: los comandos devuelven registros de wasichai, sin una capa de DTO por objeto ([API §3.2](api.md#32-formato-json)) | WAS-ADR-0003 | ADR-0008 | — | RNF-INT-004 |
| Preferir comandos, consultas, servicios de dominio, objetos de valor, agregados, eventos y políticas | SGSPE | Comandos y consultas de `/api/sgspe/**`; políticas como campos del proyecto con valor por defecto | — | ADR-0008 | — | ASM-019, RNF-INT-004 |
| PostgreSQL y PostGIS | Plataforma | PostgreSQL 18 con PostGIS 3.6 | WAS-ADR-0002 | ADR-0002 | — | RNF-MNT-001 |
| Flyway: cada cambio de esquema versionado | Sustituido | `model/*.json` versionado y aplicado con `apply.py`; Flyway propio solo para el esquema `sgspe` | WAS-ADR-0026 | ADR-0007 | CON-052 | RNF-MNT-002 |
| pgvector solo cuando se justifique | Diferido | F7, con criterio de introducción medido | WAS-ADR-0002 | ADR-0005 | CON-035 | RQ-AI-008 |
| Índices convencionales, GIST, unicidad y claves foráneas | Plataforma | Índices declarados, únicos compuestos, GIST automático y claves foráneas `ON DELETE SET NULL` en cada `RELATION` | WAS-ADR-0036; WAS-ADR-0037 | ADR-0007 | — | RQ-GIS-016 |
| Optimistic locking | Brecha | Precondición sobre `updatedAt` en el portal y en los comandos | — | ADR-0025 | CON-047, UPS-04 | RNF-INT-003, ASM-035 |
| Soft delete solo donde tenga sentido | Plataforma + SGSPE | Sin borrado lógico genérico: la historia es `appendOnly`, los ciclos de vida terminan en estados finales y un objeto se retira con `enabled: false` | WAS-ADR-0017; WAS-ADR-0040 | ADR-0011 | — | RQ-AUD-003 |

### 2.15 §21 Testing

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| Backend: unitarias, integración, repositorio, controlador, workflow, seguridad, GIS, IA y arquitectura | Plataforma + SGSPE | wasichai-test; sin repositorios propios, las pruebas de repositorio y de controlador son pruebas de integración por endpoint | WAS-DOC modules/testing.md | ADR-0008 | CON-039 | RNF-TST-001, RQ-SEC-017, RQ-GIS-021, RQ-AI-007 |
| Frontend: unitarias, componentes, integración y flujos E2E críticos | Plataforma + SGSPE | Vitest y Testing Library con `mockFetch`; E2E con Playwright desde F2 y, en F1, una prueba de humo que ejecuta el agente ([Arquitectura §13](architecture.md#13-estrategia-de-pruebas)) | WAS-DOC modules/testing.md | ADR-0024 | — | RNF-TST-001, ASM-066 |
| Testcontainers para PostgreSQL y PostGIS | Plataforma | `postgis/postgis:18-3.6`, la misma imagen del entorno | WAS-DOC modules/testing.md | ADR-0002 | — | RNF-TST-001 |
| IA sin llamadas reales al LLM: FakeModel, StubModel y MockModel | Plataforma + SGSPE | `ScriptedLlmOperations` de Embabel y modelos guionados | WAS-ADR-0015 | ADR-0003 | — | RQ-AI-007 |

### 2.16 §22 Documentación, §23 Trazabilidad y §24 Roadmap

| Mandato | Veredicto | Cómo se cumple en SGSPE | wasichai | ADR de SGSPE | Brecha o CON | Requisitos |
|---|---|---|---|---|---|---|
| `docs/` con análisis, dominio, arquitectura, datos, API, seguridad, GIS, IA, workflows, tableros y `adr/` | Proceso | Esos diez documentos más `platform-fit.md`, `deliverables.md`, `roadmap.md` y `requirements-traceability.md` | — | — | — | RNF-MNT-008 |
| Un ADR por decisión importante | Proceso | ADR de SGSPE en `docs/adr/`, que citan los de wasichai sin repetirlos (§1.2) | WAS-DOC adr/README.md | — | — | RNF-MNT-008 |
| Ejemplos ADR-001 Modular Monolith, ADR-002 PostGIS, ADR-003 Spring AI abstraction, ADR-004 Embabel orchestration, ADR-005 RAG architecture y ADR-006 Authorization model | Plataforma + SGSPE | ADR-0001 (con WAS-ADR-0001), ADR-0002, ADR-0003, ADR-0004, ADR-0005 y ADR-0006 | WAS-ADR-0001; WAS-ADR-0015 | ADR-0001, ADR-0002, ADR-0003, ADR-0004, ADR-0005, ADR-0006 | — | RNF-MNT-008 |
| `docs/requirements-traceability.md` con Requirement, Source, Module, Entity, API, UI y Test | Proceso | Matriz completa con fase y estado, actualizada en cada fase | — | — | — | RNF-MNT-009 |
| No implementar funcionalidades sin saber de dónde vienen | Proceso | Cada requisito cita su fuente y su marcador de origen [requirements-analysis §0.6] | — | — | — | RNF-MNT-009 |
| Fases 0 a 8, en orden | Proceso | El roadmap del prompt, con pausa de revisión al terminar F0 | — | — | — | RNF-MNT-007 |
| Cada fase deja el sistema ejecutable | Proceso | Starters, modelo y paquetes se agregan por fase | WAS-ADR-0024 | ADR-0001 | — | RNF-MNT-003 |
| F1 Foundation: base de datos, seguridad, usuarios, roles, auditoría, errores, observabilidad y CI | Plataforma + SGSPE | La plataforma trae seguridad, usuarios, auditoría y errores; F1 se concentra en el arranque, los roles como datos, el contrato con la plataforma, la observabilidad y la CI | WAS-ADR-0024 | ADR-0001, ADR-0026 | CON-053 | RNF-MNT-003, RNF-OBS-001 |
| F6 GIS | Plataforma + SGSPE | Geometrías y mapa básico desde F2; el GIS avanzado en F6 | WAS-ADR-0027 | ADR-0002 | CON-036 | RQ-GIS-001 |

### 2.17 §25 Definición de terminado

| Criterio del prompt | Cómo se cumple sobre wasichai | Requisitos |
|---|---|---|
| Backend implementado | Metadata en `model/` más el código del paquete `sgspe.*` que corresponda | RNF-MNT-002, RNF-MNT-005 |
| API implementada | API genérica de wasichai y, si hace falta, el endpoint de `/api/sgspe/**` documentado en `api.md` | RNF-INT-004 |
| Frontend implementado | Pantalla del portal o configuración de `/admin` | RF-IAM-001 |
| Validaciones implementadas | Metadata (`required`, únicos, ENUM), guardas y validación del endpoint | RQ-AUD-004 |
| Autorización implementada | Permisos en `roles.json`, acciones declaradas y alcance por asignación | RQ-SEC-004, RQ-SEC-006 |
| Migración de base creada | Cambio en `model/*.json` aplicado con `apply.py`; migración Flyway solo para tablas fijas del esquema `sgspe` | RNF-MNT-002, CON-052 |
| Auditoría considerada | Flags `requiresReason` y `appendOnly`, motivo en las transiciones sensibles, `export_log` en las salidas | RQ-AUD-002, RQ-AUD-003 |
| Tests creados | Los niveles de [Arquitectura §13](architecture.md#13-estrategia-de-pruebas) | RNF-TST-001 |
| Documentación actualizada | `docs/` y, si cambia una decisión, un ADR nuevo | RNF-MNT-008 |
| Trazabilidad actualizada | Fila en `requirements-traceability.md` | RNF-MNT-009 |
| Errores manejados | Problem Details con `errors[]`; nada se esconde | RNF-INT-001 |
| Observabilidad considerada | Correlación, línea de acceso y logs estructurados, según la línea base de ADR-0026 | RNF-OBS-001, RQ-AUD-006 |

Cada fase cumple esta definición y la de wasichai, y deja el sistema compilando y ejecutable (RNF-MNT-003).

### 2.18 §26 Reglas para Claude Code y §27 Orden de ejecución

| Regla | Cómo se cumple | Fuente |
|---|---|---|
| 1. Inspeccionar el repositorio antes de modificar código | Cada tarea empieza leyendo el repositorio y las fuentes de verdad | [PRM-01 §26] |
| 2. No borrar funcionalidades sin justificarlo | Un objeto se retira con `enabled: false` y una decisión, con un ADR nuevo | WAS-ADR-0017; RNF-MNT-008 |
| 3. No introducir dependencias innecesarias | La pila la fija `wasichai-bom`; cada dependencia nueva llega con su paquete, versión y licencia (§6) | WAS-ADR-0024 |
| 4. No convertir el sistema en microservicios | Un solo desplegable | WAS-ADR-0001 |
| 5. No acoplar el dominio al proveedor de IA | Embabel y la abstracción de wasichai-agent | RQ-AI-005 |
| 6. Un agente nunca modifica datos críticos | Agentes de solo lectura y sugerencias aparte | RQ-AI-001; WAS-ADR-0014 |
| 7. Toda operación de negocio crítica pasa por la capa de dominio o de aplicación | Objetos `apiOnly` con guardas y comandos propios | RQ-AUD-004 |
| 8. Toda acción crítica es auditable | `audit_log`, motivo y `export_log` | RQ-AUD-001, RQ-AUD-011 |
| 9. No inventar requisitos documentales | Marcadores de origen y supuestos registrados | [requirements-analysis §0.6] |
| 10. Ante una ambigüedad: identificar, documentar el supuesto y seguir con una solución reversible | CON, GAP y ASM con su forma de revertirse | [requirements-analysis §17–§19] |
| 11. Un ADR por decisión relevante | ADR de SGSPE (§1.2) | RNF-MNT-008 |
| 12. Mantener el sistema compilando en cada etapa | CI en los dos repositorios | RNF-MNT-003 |
| 13. Ejecutar las pruebas después de cambios relevantes | CI de build, integración, modelo y UI | RNF-TST-001 |
| 14. No esconder errores | Problem Details; los listeners de derivados registran su fallo y el trabajo los corrige | RNF-INT-001 [domain-model §7.5] |
| 15. No sobreingenierizar | Metadata primero; código propio solo con un caso concreto | RNF-MNT-005 |

Los pasos 1 a 13 del orden de ejecución [PRM-01 §27] son la Fase 0: inspección del repositorio y de las fuentes, el análisis,
el modelo de dominio, esta arquitectura, el modelo de datos, los workflows, la IA, los tableros, la trazabilidad, los ADR,
el plan de implementación y la validación de consistencia. El paso 14 (empezar la Fase 1) espera la revisión del usuario
[USR-04], y el paso 15 (compilar, probar, inspeccionar y actualizar después de cada fase) es la definición de terminado de
§2.17 (RNF-MNT-003, RNF-MNT-007).

## 3. Capacidades de wasichai por módulo

| BC | Metadata (objetos y flags) | Workflow | Automatización y documentos | GIS | Guardas, listeners y trabajos | Código propio | Desde |
|---|---|---|---|---|---|---|---|
| BC-01 Proyectos | `project`, `project_party`, `exploration_campaign`, `project_assignment`, `mining_concession` | WF-A | — | Ubicación y área de interés del proyecto; polígono de concesión | `ProjectWriteGuard`, `ProjectScopeGuard`, `FieldChangeReasonGuard` | `sgspe.scope`, `sgspe.common` | F2 |
| BC-02 Territorio y SIG | Objetos del territorio; `project_component` `apiOnly` | WF-O | — | GEOMETRY, `bbox`, features, capas no sensibles en GeoServer, `MAP` | `TerritoryWriteGuard`, `ExactlyOneGuard`, `AreaListener`, `DerivedFieldsJob` | `sgspe.territory` | F2, F6 |
| BC-03 Grupos de interés | Actores, cargos, grupos de interés; `stakeholder_assessment` `appendOnly`, `apiOnly` y `requiresReason` | WF-B | Ficha de grupo de interés (documento emitido) | Por relación territorial, nunca puntos de personas | `StakeholderWriteGuard`, `StakeholderFollowUpJob` | `sgspe.stakeholder` | F2 |
| BC-04 Relacionamiento | `engagement` y sus hijos, plan, mensajes, alianzas, visitas | WF-C, WF-L | Minuta y lista de asistentes (documentos emitidos) | Ubicación de interacciones y observaciones | `EngagementWriteGuard`, `AttendanceCountsListener` | `sgspe.engagement` | F2 |
| BC-05 Compromisos | `commitment` `requiresReason`; `commitment_reschedule` `appendOnly` y `apiOnly`; `delivery` y `commitment_candidate` `apiOnly` | WF-D, WF-K | Tarea al aprobar; acta de entrega al crear la entrega | Ubicación del compromiso | `CommitmentWriteGuard`, `SupportPolicyGuard`, `CommitmentDerivationsListener`, `DeadlineJob` | `sgspe.commitment` | F3 |
| BC-06 Acuerdos | `agreement` `requiresReason`, partes y cláusulas | WF-I | — | Predios afectados por M2M | `AgreementWriteGuard`, `DeadlineJob` (vigencia) | `sgspe.agreement` | F3 |
| BC-07 QCS | `grievance`, `inquiry`, `stakeholder_request`, todos `requiresReason` | WF-E1, WF-E2, WF-E3 | Tarea de acuse; cargo de queja y carta de respuesta | Ubicación de la QCS | `AttentionWriteGuard`, `QcsDueDatesListener`, `QcsTransitionDatesListener`, `RequestClosureListener` (también al cerrarse el aporte, desde F5; ASM-068), `DeadlineJob` | `sgspe.attention` | F4 |
| BC-08 Riesgos | `social_risk` `requiresReason`; `risk_assessment` `appendOnly` y `apiOnly` | WF-F | — | Ubicación del riesgo | `RiskWriteGuard`, `DeadlineJob` (revisión y su tarea) | `sgspe.risk` | F4 |
| BC-09 Alerta temprana | Incidentes, menciones, señales, reglas y alertas | WF-J, WF-M | — | Ubicación de señales, incidentes y alertas | `WarningWriteGuard`, `WarningRulesJob` | `sgspe.warning` | F4 |
| BC-10 Inversión social | `social_contribution` `requiresReason`; `program_measurement` `appendOnly` | WF-G, WF-P | Acta de recepción al entregar el aporte | Ubicación de iniciativas | `SupportPolicyGuard`, `InvestmentWriteGuard`, `ContributionTotalsListener` | `sgspe.investment` | F5 (tope en F3) |
| BC-12 Evidencias | `evidence` `apiOnly` con guarda de inmutabilidad | — | Documentos emitidos de wasichai-documents | Ubicación de la foto | `EvidenceWriteGuard` | `sgspe.evidence` | F3 |
| BC-13 Tareas | `task` con único `generator_key` | WF-N | Las automatizaciones crean tareas | — | `ExactlyOneGuard`, `DeadlineJob` | `sgspe.deadline` | F3 |
| BC-14 Notificaciones | Tablas fijas, no Custom Objects | — | — | — | `ApprovalNoticeListener`, `DeadlineNotices` | `sgspe.notification` | F3 |
| BC-15 Reportes y entregables | `indicator`; `indicator_measurement`, `deliverable_version` y `export_log` `appendOnly` y `apiOnly`; `deliverable` `requiresReason`, con las acciones `APPROVE`, `GENERATE`, `ISSUE`, `EXPORT` y `DOWNLOAD_RESTRICTED` | WF-H | — | GetMap de GeoServer para mapas de informe | `DeliverableWriteGuard`, trabajador de generación | `sgspe.dashboard`, `sgspe.deliverable`, `sgspe.privacy` | F2–F8 |
| BC-16 Identidad | Usuarios, roles, permisos, acciones declaradas y cuentas de servicio de wasichai; `project_assignment` | — | — | — | `ProjectScopeGuard`, `AssignmentRoleGuard`, `EditAnyGuard` | `sgspe.scope` | F1 |
| BC-17 Auditoría | `audit_log` de wasichai; `export_log` | — | — | Sin coordenadas en la auditoría | — | `sgspe.observability`, `sgspe.privacy` | F1 |
| BC-18 IA | `ai_suggestion` `apiOnly`; `ai_interaction` `appendOnly` y `apiOnly`; tabla fija `knowledge_chunk` | WF-K | — | — | `CommandTransitionGuard` | `sgspe.ai`; wasichai-agent para AI-05 | F7 |
| BC-19 Línea base social | Doce objetos, solo metadata | — | Ficha de localidad (documento emitido) | Escuelas, establecimientos y vías | — | Ninguno | F2 |
| BC-20 Catálogos | ENUM compartidos; `topic`, `administrative_unit` e `indicator` como objetos catálogo | — | — | Límites del ubigeo | — | Script de carga del ubigeo | F2 |

BC-11 (operaciones de campo) no tiene fila propia: sus objetos viven en BC-04 durante el MVP (ASM-045). Los nombres de las
automatizaciones (`AUT-NN`) y los documentos emitidos por tipo los fija `workflows.md` [domain-model §6.12, §7.3].

## 4. Brechas upstream

### 4.1 Las dieciocho brechas registradas

Las brechas se definen en [requirements-analysis §18.2](requirements-analysis.md#182-brechas-upstream-de-wasichai-ups). Esta
tabla agrega dónde vive la mitigación en la arquitectura y qué se retira cuando llegue la solución upstream. Los issues
upstream se abren solo con la aprobación del usuario en el checkpoint (GAP-028).

| Brecha | Necesaria en | Mitigación en SGSPE | Dónde vive | Qué se retira cuando llegue |
|---|---|---|---|---|
| UPS-01 Alcance de lectura por proyecto, territorio o región | Antes de la producción con varios proyectos (crítica) | Decisión pendiente en el checkpoint (CON-041), con recomendación A: escritura acotada por asignación y lectura a nivel de tenant en el MVP con un solo cliente | `ProjectScopeGuard` en `sgspe.scope` | El adaptador de la extensión reemplaza la lectura a nivel de tenant; la guarda se queda |
| UPS-02 Auditoría de la administración de usuarios, roles, permisos y metadata | F1 | Modelo y roles solo desde el repositorio | `model/`, `apply.py`, `apply_roles.py` | Nada: la auditoría upstream complementa |
| UPS-03 Correlation id y origen del cambio en las filas de auditoría | F1 | `X-Correlation-Id` en los logs y en la auditoría; origen `job:<nombre>` en los trabajos y nombre del trabajo en el motivo | `sgspe.observability`, `Records.asPlatform(…, source, …)`, convención de motivos (ASM-051) | La convención de motivos de importaciones y automatizaciones |
| UPS-04 Bloqueo optimista y actualización parcial | F2 | Precondición sobre `updatedAt` con `If-Match` | `sgspe.common`; [API §3.6](api.md#36-concurrencia-precondición-sobre-updatedat) | La comprobación propia |
| UPS-05 Paginación y filtros por fecha y usuario en `/api/audit` | F1 | Historial por registro en las fichas | Portal | — |
| UPS-06 Capacidades de tenant (y alcance) en `/me/permissions` | F2 | El portal deduce las capacidades de los permisos por objeto | `sgspe-ui` | La deducción del portal |
| UPS-07 Campo FILE e IMAGE | F3 | Resuelta en wasichai 0.6.0: `evidence.file` es un campo `FILE` de wasichai-files; SGSPE reemplaza la descarga nativa de `evidence` para aplicar `DOWNLOAD_RESTRICTED` (ADR-0014) | `model/model.json`; `sgspe.evidence` | Nada |
| UPS-08 Interfaz de reglas de escritura en wasichai-ui | F2 | Las escrituras críticas se hacen desde el portal, que pide el motivo | `sgspe-ui` | Ediciones con motivo en `/admin` |
| UPS-09 Revocación de tokens, límite de intentos de login y política de contraseñas | Antes de producción | Revocación, límite de intentos y política de contraseñas de wasichai, más TTL corto y límite en el proxy | `application-prod.yml`, proxy inverso | El límite del proxy queda como defensa extra |
| UPS-10 Provisión de tenants separada de la administración de la organización | Operación multicliente | `SUPER_ADMIN` y `ORGANIZATION_ADMIN` como `ADMIN` | `model/roles.json` | El mapeo de los dos roles |
| UPS-11 Módulo de notificaciones | F3–F4 | Resuelta: wasichai-notifications (ADR-0018); SGSPE decide destinatarios y textos (ASM-012) | `sgspe.notification` | Nada |
| UPS-12 Inmutabilidad de `audit_log` en la base | Endurecimiento | Ningún código de SGSPE escribe en la auditoría | Prueba de arquitectura | — |
| UPS-13 Mapa base configurable en `MapView` de `@wasichai/gis` | Antes de producción | El mapa territorial del portal aplica su propio estilo configurable (ASM-048); la CSP de producción no admite las teselas de OpenStreetMap, así que los mapas de edición con `MapView` se ven sin mapa base | `sgspe-ui` y la CSP del proxy | Los mapas de edición recuperan el mapa base, con el estilo del portal |
| UPS-14 Puntos de extensión de wasichai-agent: seudonimizar los resultados de las herramientas, activarse por organización e informar los tokens | F7 | AI-05 solo en organizaciones con la IA activada, con el proveedor decidido (GAP-015) y para roles sin lectura de los campos restringidos (hoy, `ANALYST`); `POST /api/agent/ask` cerrado por HTTP | `sgspe.ai` | Las restricciones de AI-05 y el filtro que cierra `/api/agent/ask` |
| UPS-15 `defaultValue` se guarda pero no se aplica | F2 | El código de SGSPE aplica los valores por defecto: el portal los propone al crear y el código que lee un parámetro vacío usa el mismo valor (ASM-019) | `sgspe-ui` y `sgspe.common` | Los valores por defecto escritos en el código |
| UPS-16 Lectura de las autorrelaciones desde el lado destino | F2 | Los registros que apuntan a uno se leen filtrando la lista por el campo, por ejemplo `?parent_locality=<id>` | `sgspe-ui` | El filtro del portal |
| UPS-17 Forma soportada de listar los tenants desde un trabajo en segundo plano | Antes de producción | `TenantDirectory.organizationsWithObject` [WAS-ADR-0057], aislado en `Organizations` | `sgspe.common` | Nada |
| UPS-18 Idempotencia en la API genérica (`Idempotency-Key`) | F3 | Resuelta en el alta genérica (WAS-ADR-0058); los comandos de `/api/sgspe/**` usan la tabla fija `idempotency_key` con el mismo contrato (ASM-065) | `sgspe.common`; [API §3.5](api.md#35-idempotencia-idempotency-key) | La tabla `idempotency_key`, cuando la plataforma ofrezca la clave a las rutas de una app |

### 4.2 Otras diferencias con la plataforma ya registradas

| Diferencia | Registro | Mitigación |
|---|---|---|
| Las opciones ENUM no tienen etiqueta: el valor se muestra tal cual | CON-050, ASM-025 | Valores en español MAYÚSCULAS; decisión pendiente en el checkpoint |
| Las etiquetas de la metadata tienen un solo idioma | CON-051 | Interfaz bilingüe; metadata en español |
| La interfaz no dibuja geometrías `MULTI*` | CON-060 | Se aceptan por API e importación |
| Las automatizaciones no tienen disparadores temporales | CON-043 | Trabajos programados con `ClusterLock` |
| wasichai-documents no genera DOCX ni PDF en el servidor | CON-045 | `sgspe.deliverable` con Apache POI y Gotenberg |
| El token viaja en `localStorage`, no en una cookie HttpOnly | CON-056, RQ-SEC-001 | CSP, TTL corto; la cookie queda como propuesta upstream |
| El WMS de GeoServer no autentica | RQ-SEC-011, CON-054 | Proxy autenticado con lista blanca por tenant, que exige READ sobre el objeto de cada capa |

### 4.3 Lo que trae wasichai 0.6.0

El 2026-10-09 el backend pasa de wasichai 0.3.2 a 0.6.0 (0.3.3, 0.4.0, 0.5.0 y 0.6.0; el `CHANGELOG.md` de wasichai tiene
el detalle). Kotlin, Spring Boot y el JDK no cambian. Las migraciones de core (`V9` a `V17` y el repetible
`R__audit_purge_role`) y de automation (`V2`) corren solas al arrancar. La actualización neutraliza lo que choca con
SGSPE y, salvo el identificador de correlación, no adopta nada nuevo por sí misma: cada adopción es una decisión aparte (UPS-03, UPS-09 y UPS-17 están adoptadas, y UPS-07, UPS-11 y UPS-18 desde F3), y esta tabla
dice qué ofrece la plataforma para cada brecha y qué haría falta para retirar la mitigación.

| Brecha | Qué trae wasichai | Estado en SGSPE tras la actualización | Para adoptarla |
|---|---|---|---|
| UPS-01 | 0.5.0: SPI `RecordReadScope`, que acota las lecturas de una persona en registros, relacionados, auditoría y transiciones [WAS-ADR-0048] | Sin cambios: lectura a nivel de tenant (CON-041, opción A) | Un `RecordReadScope` en `sgspe.scope` con las asignaciones vigentes, cacheadas por petición; decide el checkpoint de CON-041 |
| UPS-02 | 0.5.0: filas de auditoría para usuarios, roles, permisos y metadata [WAS-ADR-0049] | Resuelta por la actualización | Nada |
| UPS-03 | 0.5.0: `correlation_id` y `source` en cada fila de `audit_log`, y el identificador en el MDC de todos los logs [WAS-ADR-0050] | Adoptada. `CorrelationIdFilter` corre después del filtro de la plataforma y usa su identificador, así la respuesta, la línea de acceso y la fila de auditoría coinciden. JOB-02 y JOB-03 escriben con `asPlatform(organización, source = "job:derived-fields" / "job:stakeholder-follow-up")`; el motivo conserva el nombre del trabajo, que es lo que lee la persona (ASM-051) | Nada. Las importaciones y las automatizaciones aún no se etiquetan con un origen propio |
| UPS-04 | 0.5.0: `ETag`, `If-Match` comparado en la propia escritura con igualdad estricta, `412` y `PATCH` con mezcla [WAS-ADR-0051] | Neutralizada: `IfMatchFilter` quita `If-Match` antes de la plataforma, porque su comparación estricta respondería `412` donde SGSPE tolera escrituras posteriores de derivados y vínculos (§3.6 de la API), como el área o los conteos que escribe un listener tras el alta. `PATCH` ya responde y pasa por las mismas guardas y la misma precondición de SGSPE | Decidir entre la comparación atómica (sin la tolerancia: el portal vuelve a leer tras cada escritura de un listener) y la propia; `Records.patch` puede pasar a `RecordService.patch` |
| UPS-05 | 0.5.0: `/api/audit` paginado por cursor y filtrado por período y usuario [WAS-ADR-0052] | Disponible, sin uso | Consulta de auditoría en `sgspe-ui`; `Versions` puede recorrer el historial con `historyPage` y levantar el tope de 500 entradas de §3.6 de la API |
| UPS-06 | 0.5.0: `capabilities` en `GET /api/auth/me/permissions` [WAS-ADR-0053] | Disponible, sin uso | El portal lee las capacidades en lugar de deducirlas |
| UPS-07 | 0.6.0: módulo `wasichai-files`, campos `FILE` e `IMAGE` con un SPI de almacenamiento [WAS-ADR-0061] | Adoptada en F3 (decisión D1): `evidence.file` es un campo `FILE`; SGSPE reemplaza la descarga nativa de `evidence` para aplicar `DOWNLOAD_RESTRICTED` (ADR-0014) | Nada |
| UPS-09 | 0.6.0: revocación de tokens, límite de intentos de login y política de contraseñas, apagados por defecto [WAS-ADR-0059] | Adoptada en `application-prod.yml`: revocación de tokens, 5 y 20 intentos de login por ventana de 15 minutos y política de contraseñas de 12 caracteres ([Seguridad §3.2](security.md#32-endurecimiento-de-sgspe)); `ProdProfileTest` la fija | Nada. El límite del proxy queda como defensa extra |
| UPS-10 | 0.5.0: acción `MANAGE_TENANTS` y `wasichai.organizations.separate-provisioning` [WAS-ADR-0055] | Disponible, sin uso (por defecto, como antes) | Separar `SUPER_ADMIN` de `ORGANIZATION_ADMIN` en `model/roles.json` |
| UPS-11 | 0.4.0 y 0.6.0: módulo `wasichai-notifications`, con bandeja, SSE, canales de entrega y preferencias [WAS-ADR-0046] [WAS-ADR-0060] | Adoptada en F3 (decisión D2): bandeja, correo y fuentes del módulo; SGSPE publica a personas resueltas por las asignaciones (ADR-0018) | Nada |
| UPS-12 | 0.5.0: `audit_log` de solo inserción en la base, por disparadores; la purga, solo con `wasichai.audit.purge-role` [WAS-ADR-0054] | Resuelta por la actualización (migración `V13`) | Nada |
| UPS-14 | 0.6.0: filtro de los resultados de las herramientas, activación por quien llama y tokens por respuesta en wasichai-agent [WAS-ADR-0056] | Disponible, sin uso | Diseño de AI-05 en F7 |
| UPS-15 | 0.5.0: un alta que omite un campo recibe su `defaultValue` | Resuelta para las altas; el código que lee un parámetro vacío (ASM-019) se queda para los valores borrados | Nada para las altas |
| UPS-16 | 0.5.0: `direction=forward\|inverse` en los relacionados de una autorrelación | Disponible, sin uso | El portal deja el filtro por campo |
| UPS-17 | 0.5.0: `TenantDirectory.organizationsWithObject` para los trabajos de fondo [WAS-ADR-0057]; `findAllOrganizations` queda obsoleto | Adoptada: `Organizations.having` usa `TenantDirectory.organizationsWithObject`; `ArchitectureTest` rechaza `findAllOrganizations` en todo el código y `TenantDirectory` fuera de `Organizations` | Nada |
| UPS-18 | 0.6.0: `Idempotency-Key` en el alta de la API genérica [WAS-ADR-0058] | Adoptada en F3 (decisión D3) en el alta genérica; los comandos de `/api/sgspe/**` usan la tabla fija `idempotency_key` con el mismo contrato (ASM-065) | Retirar `idempotency_key` cuando la plataforma ofrezca la clave a las rutas de una app |
| UPS-21 | 0.4.0: `UserDirectory.idsByEmail` por organización (solo usuarios habilitados) | Disponible, sin uso | `UserReferenceGuard` puede usarlo si un usuario deshabilitado deja de valer como referencia |
| UPS-20, UPS-22 | Sin cambios: la columna de una relación obligatoria sigue con `ON DELETE SET NULL`, y la API genérica no abre una transacción de la petición | Siguen las mitigaciones | — |

UPS-08, UPS-13 y UPS-19 son de wasichai-ui y se evalúan con la actualización de `@wasichai/*` en `sgspe-ui`.

Además, sin relación con una brecha: `updated_at` toma la hora de cada sentencia (`clock_timestamp()`), así que dos
escrituras de una misma transacción ya no comparten `updatedAt`; cada respuesta de un registro lleva `ETag`; y la
plataforma suma `RecordService.createAll`, la acción `NOTIFY` de las automatizaciones y las unidades organizacionales
(`/api/org-units`), que SGSPE no usa.

## 5. Patrones de srtm y caja que se reutilizan

caja (wasichai 0.3.2, `@wasichai/*` 0.5.0) es la plantilla vigente; de srtm (todavía en 0.2.0) se toman sobre todo las piezas
de GIS, importación y emisión, sin copiar los rodeos que 0.3.x ya no necesita [USR-03].

| Patrón | Origen | Uso en SGSPE | Cambio respecto del origen | Fuente |
|---|---|---|---|---|
| `model/model.json` más `apply.py` (con `core_client.py`, `fake_core.py` y sus pruebas): aplicación idempotente por REST, `--validate-only` y `--dry-run` | caja | El modelo de los 89 objetos, sus ENUM, acciones declaradas, relaciones, flags, índices y únicos compuestos | URL por defecto en el puerto 8094; desde F2, geometrías e índices declarados como en el `apply.py` de srtm; un segundo pase, `apply.py --workflows`, aplica después de `apply_roles.py` los workflows, tipos de documento, automatizaciones y vistas, formularios y páginas, cada uno desde su fase, porque wasichai rechaza un workflow con roles inexistentes; `test_apply.py` cubre un tenant nuevo ([Arquitectura §5.4](architecture.md#54-modelo-como-código-model)) | RNF-MNT-002, CON-052 |
| `model/roles.json` más `apply_roles.py` | caja | 15 roles declarados con etiqueta en español; `roles.json` es la fuente de verdad | Acciones declaradas por rol (incluida `EDIT_ANY`) y ningún `ownRecordsOnly`; la clave `"*"` como permiso de todo el tenant y una lista `mappedToAdmin` para los dos roles que son `ADMIN` | RQ-SEC-003, ASM-024, ASM-069 |
| API del portal `/api/<app>/**` con `RecordService` en proceso, como el usuario que llama | caja (`/api/caja/**`), srtm (`/api/srtm/**`) | `/api/sgspe/**` para comandos y consultas | Recursos en inglés (USR-09) | RNF-INT-004, CON-048 |
| Marca de escritura propia (`EscrituraDeCaja`, `EscrituraDeSrtm`) y su guarda | caja, srtm | `SgspeWrite` con `DerivedFieldsGuard` y `CommandTransitionGuard` | Nombre en inglés | [domain-model §4.5] RQ-AUD-004 |
| Envoltura de `RecordService` para el código propio (`Registros`) | srtm, caja | Lecturas y escrituras tipadas del código propio, con la marca | En `sgspe.common` | [domain-model §8.2] |
| Resolución de la tabla física por la metadata (`TablasCore`) | srtm | Solo SQL de agregación de solo lectura detrás de una compuerta de permisos | srtm escribía directo en las tablas porque 0.2.0 no tenía `asPlatform`; SGSPE nunca escribe así | RQ-GIS-017, RQ-DSH-031 [WAS-ADR-0039] |
| Puerto de almacenamiento (`AlmacenEmision`, con implementaciones local y S3, claves relativas, descarga en streaming y prueba de contrato) | srtm | Archivos de evidencias y de entregables | En `sgspe.evidence`; sin el decorador `AlmacenGuardado` ni `IdentidadEmision`, rodeos de 0.2.0 | ASM-013, ASM-014 |
| Trabajos de generación con candado y retención de borradores (`EmisionMasiva`, `RetencionEmision`) | srtm | Trabajador de borradores de entregables | Sin lotes: un pedido por entregable (ASM-061) | RQ-REP-021, RQ-REP-027 |
| `Idempotency-Key` de 1 a 64 caracteres; reenvío con la misma clave responde `200` con el resultado de la primera vez | caja (cobros) | Creaciones de `/api/sgspe/**` | La clave se guarda aparte del registro creado, en la tabla fija `idempotency_key`; se honra desde F3 (en F2 la cabecera se ignora), y un reenvío reconstruye su respuesta releyendo los registros ([API §3.5](api.md#35-idempotencia-idempotency-key)) | RNF-INT-002, ASM-065 |
| Candados por entidad sobre los candados consultivos de PostgreSQL (`Candados`) | caja, srtm | `ClusterLock.withXactLock` para precondiciones y correlativos | — | [WAS-ADR-0039] |
| Parámetro de consulta desconocido como 422 que lo nombra | srtm | Toda la API propia | — | ASM-054 |
| Permisos comprobados antes de la primera escritura ("403 antes de empezar") | caja | Todos los comandos propios | — | RQ-SEC-004 |
| Recorrido de organizaciones de un trabajo de fondo (`findAllOrganizations` filtrado por un objeto del modelo) | caja (publicador del buzón) | Trabajos programados de SGSPE | `TenantDirectory`, aislado en `sgspe.common` y vigilado por `ArchitectureTest` | UPS-17 [WAS-ADR-0039] [WAS-ADR-0057] |
| Importador del ubigeo del INEI | srtm | Catálogo `administrative_unit` | En Python con solo la biblioteca estándar, idempotente | RF-TER-001, ASM-016 |
| Una aplicación web con dos partes (`main.tsx` elige `/admin` o el portal), cliente de API, sesión en `<prefijo>.token`, `RequireSession`, árbol de navegación por permisos y pruebas con `mockFetch` | caja-ui | `sgspe-ui` | Prefijo `sgspe`; 13 secciones del prompt | RF-IAM-001, RNF-USA-001 |
| Componente de mapa con MapLibre cargado de forma diferida | srtm-ui | Mapa territorial del portal | Capas agrupadas, leyenda de paleta compartida y ficha contextual | RQ-GIS-014 |
| Pruebas de arquitectura por escaneo de fuentes y CI con acciones fijadas por SHA | caja | Backend y `sgspe-ui` | Reglas propias de SGSPE (§2.1) | RNF-MNT-005 |

No se reutilizan la ruta de PDF de srtm (Thymeleaf y OpenHTMLtoPDF), reemplazada por DOCX primero con Gotenberg (ASM-014),
ni el buzón de salida de caja, porque el MVP no integra sistemas externos.

## 6. Versiones fijadas

Las versiones de wasichai (0.6.0) y de `@wasichai/*` (0.5.0) son líneas independientes [WAS-ADR-0032]: se fijan exactas, se
actualizan juntas y las pruebas de contrato con la plataforma detectan cambios de comportamiento (RNF-MNT-001). El backend
pasó de 0.3.2 a 0.6.0 el 2026-10-09 (§4.3); `@wasichai/*` se actualiza en `sgspe-ui`.

| Componente | Versión | Fase | Fuente |
|---|---|---|---|
| wasichai (`wasichai-bom` y starters) | 0.6.0 (0.3.2 hasta el 2026-10-09) | F2 | [USR-03] RNF-MNT-001 |
| `@wasichai/*` | 0.5.0 | F1 | [USR-03] RNF-MNT-001 |
| Kotlin | 2.4.20 | F1 | WAS-DOC guides/build-your-app.md |
| Spring Boot | 4.1.1 | F1 | WAS-DOC guides/build-your-app.md |
| JDK (toolchain de Gradle) | 25 | F1 | WAS-DOC guides/build-your-app.md |
| Gradle (wrapper de caja) | 9.8.0 | F1 | caja-backend |
| PostgreSQL y PostGIS | 18 y 3.6 (`postgis/postgis:18-3.6`) | F1 | WAS-DOC modules/gis.md |
| Embabel | 1.5.2, a través de wasichai-agent | F7 | WAS-ADR-0015 |
| `maplibre-gl` | 6.10.0, la que fija `@wasichai/gis` | F2 | WAS-DOC modules/gis.md; RQ-GIS-014 |
| React y React DOM | 19.3.0 | F1 | caja-ui |
| React Router | 8.4.0 | F1 | caja-ui |
| TanStack Query | 5.104.0 | F1 | caja-ui |
| i18next y react-i18next | 26.4.2 y 17.0.15 | F1 | caja-ui |
| Vite y Vitest | 8.3.1 y 5.0.2 | F1 | caja-ui |
| TypeScript | 7.0.2 | F1 | caja-ui |
| Tailwind CSS | 4.3.3 | F1 | caja-ui |
| Node y Yarn | Node 26 o superior; Yarn 1 (`yarn.lock` v1) | F1 | WAS-DOC guides/build-your-app.md (Node); caja-ui (Yarn) |
| Apache POI (`poi-ooxml`) | 5.4 o superior (incluye la corrección de CVE-2025-31672) | F2 (exportaciones XLSX) | RQ-REP-022, ASM-014 |
| commonmark-java con tablas GFM | Por fijar en F8 | F8 | ASM-014 |
| AWS SDK v2 (S3) | 2.55.10 (`software.amazon.awssdk:bom`), el cliente asíncrono que usa wasichai-files | F3 | ASM-013 |
| PDFBox | 3, solo en pruebas | F8 | RQ-REP-017 |
| ECharts | 6 | F2 | RQ-DSH-035, ASM-046 |
| GDAL (`gdal-bin` en la imagen del backend) | Por fijar en F6 | F6 | RQ-GIS-018, ASM-056 |
| Gotenberg | `gotenberg/gotenberg:8-libreoffice` | F8 (pico técnico en F1) | ASM-014 |
| GeoServer | `docker.osgeo.org/geoserver:3.0.1` | F6 | ASM-038 |
| MinIO | `pgsty/minio:RELEASE.2026-08-04T00-00-00Z` y `pgsty/mc:RELEASE.2026-09-16T00-00-00Z`, solo en desarrollo y en las pruebas, por su licencia AGPL | F3 | ASM-013, GAP-022 |
| Mailpit (SMTP de prueba) | `axllent/mailpit:v1.31.2`, solo en desarrollo | F3 | GAP-021 |

No se usan iText (AGPL), Aspose, Highcharts ni fuentes de Microsoft sin licencia dentro de los contenedores; los mapas base
de Esri sin licencia tampoco (RQ-GIS-013, ASM-055).
