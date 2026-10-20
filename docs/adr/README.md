# Decisiones de arquitectura (ADR) de SGSPE

Aquí se registran las decisiones de arquitectura propias de SGSPE, en formato MADR y en español. Cada ADR tiene las
secciones Estado, Contexto, Opciones consideradas, Decisión, Consecuencias y Referencias.

- **Decisiones de plataforma.** Las toma wasichai y no se repiten aquí. Se citan como `WAS-ADR-00NN`, el número del ADR
  en `wasichai/docs/adr/`, y cada ADR de SGSPE explica solo lo propio del sistema.
- **Estados:**
  - **Aceptado:** rige desde ya.
  - **Propuesto:** se acepta, o se reemplaza, al iniciar la fase que indica su Estado.
  - Un ADR aceptado no se edita. Si cambia la decisión, se escribe un ADR nuevo que lo reemplaza, y el anterior pasa a
    "Reemplazado por ADR-00NN".
- **Fuente de verdad.** Los ADR resumen decisiones que ya están en los documentos de diseño de la Fase 0. Los requisitos y
  los supuestos se citan por su ID de [`requirements-analysis.md`](../requirements-analysis.md).

| ADR | Título | Estado | Fecha | ADRs de wasichai relacionados |
|---|---|---|---|---|
| [0001](0001-construir-sobre-wasichai.md) | Construir SGSPE sobre wasichai | Aceptado | 2026-10-07 | 0001, 0003, 0004, 0024, 0025, 0032 |
| [0002](0002-geometrias-en-srid-4326-y-crs-de-trabajo.md) | Geometrías en SRID 4326 con CRS de trabajo por proyecto | Aceptado | 2026-10-07 | 0002, 0007, 0019, 0027 |
| [0003](0003-abstraccion-de-ia.md) | Abstracción de IA sobre wasichai-agent | Aceptado | 2026-10-12 | 0014, 0015, 0031, 0056 |
| [0004](0004-la-sugerencia-de-ia-no-es-un-comando.md) | La sugerencia de IA no es un comando de dominio | Propuesto (se acepta en F3) | 2026-10-07 | 0014, 0038, 0040 |
| [0005](0005-rag-y-pgvector.md) | RAG en F7 y pgvector solo con justificación medida | Aceptado en parte (F7: la etapa 1, con `knowledge_chunk` y búsqueda de texto completo; pgvector, la etapa 2, sigue sujeto a sus criterios) | 2026-10-12 | 0002, 0026, 0039 |
| [0006](0006-autorizacion-y-alcance-por-proyecto.md) | Autorización por roles y alcance por proyecto | Aceptado; el alcance por proyecto, sustituido por ADR-0029 | 2026-10-07 | 0004, 0010, 0020, 0025, 0040, 0042 |
| [0007](0007-el-modelo-como-codigo.md) | El modelo como código en `model/` | Aceptado | 2026-10-07 | 0003, 0004, 0026, 0039, 0040 |
| [0008](0008-codigo-propio-y-api-del-portal.md) | Código propio solo donde la metadata no alcanza, y API del portal | Aceptado | 2026-10-07 | 0003, 0025, 0038, 0039, 0040 |
| [0009](0009-nombres-tecnicos-en-ingles.md) | Nombres técnicos en inglés; documentación y valores de ENUM en español | Aceptado | 2026-10-07 | 0003, 0042 |
| [0010](0010-repositorios-sgspe-y-sgspe-ui.md) | Repositorios `sgspe` y `sgspe-ui` | Aceptado | 2026-10-07 | 0024, 0029, 0032 |
| [0011](0011-auditoria.md) | Auditoría sobre el `audit_log` de wasichai | Aceptado | 2026-10-07 | 0013, 0023, 0040, 0041, 0043, 0044 |
| [0012](0012-estados-temporales-materializados.md) | Estados temporales materializados por trabajos programados | Aceptado | 2026-10-07 | 0013, 0016, 0039, 0041 |
| [0013](0013-entregables-docx-primero.md) | Entregables DOCX primero, con PDF por Gotenberg | Aceptado (el PDF, reemplazado por 0030) | 2026-10-13 | 0023, 0038, 0039, 0040, 0042 |
| [0014](0014-almacenamiento-de-objetos.md) | Evidencias y entregables en almacenamiento de objetos | Aceptado (ampliado en F8: los entregables en wasichai-files) | 2026-10-13 | 0040, 0041, 0042, 0061 |
| [0015](0015-mapas-de-informe-con-geoserver.md) | Mapas de informe con GeoServer GetMap | Aceptado (F6: GeoServer interno, estilos SLD y proxy WMS; F8: los mapas de informe, con las decoraciones de SGSPE) | 2026-10-13 | 0006, 0027, 0039 |
| [0016](0016-kpis-en-vivo-y-echarts.md) | KPIs calculados en vivo y ECharts como librería de gráficos | Aceptado (en uso desde F2) | 2026-10-13 | 0003, 0020, 0028, 0034, 0035, 0036, 0038 |
| [0017](0017-datos-personales-y-anonimizacion.md) | Datos personales y anonimización | Aceptado | 2026-10-07 | 0023, 0039, 0042 |
| [0018](0018-notificaciones.md) | Notificaciones en la aplicación y abstracción del correo | Aceptado | 2026-10-09 | 0016, 0026, 0038, 0039, 0043, 0046, 0047, 0060 |
| [0019](0019-importacion-geoespacial-con-reconciliacion.md) | Importación geoespacial con preparación y reconciliación | Aceptado | 2026-10-07 | 0007, 0019, 0038, 0040 |
| [0020](0020-autenticacion-jwt-endurecida.md) | Autenticación con el JWT de wasichai, endurecida | Aceptado | 2026-10-07 | 0010, 0031, 0043 |
| [0021](0021-reglas-de-workflow.md) | Reglas de workflow: metadata, guardas y motivo | Aceptado | 2026-10-07 | 0013, 0016, 0018, 0040, 0041, 0042 |
| [0022](0022-qcs-en-tres-objetos.md) | Quejas, consultas y solicitudes en tres objetos | Aceptado | 2026-10-07 | 0013, 0040 |
| [0023](0023-separacion-de-actores-y-territorio.md) | Separación de persona, cargo, grupo de interés, comunidad y área de influencia | Aceptado | 2026-10-07 | 0003, 0037 |
| [0024](0024-interfaz-portal-y-admin.md) | Interfaz: portal en `/` y administración en `/admin` | Aceptado | 2026-10-07 | 0020, 0028, 0031, 0042 |
| [0025](0025-concurrencia-e-idempotencia.md) | Concurrencia e idempotencia en la API del portal | Propuesto (se acepta en F2) | 2026-10-07 | 0037, 0038, 0039, 0040 |
| [0026](0026-topologia-puertos-y-observabilidad.md) | Topología de despliegue, puertos y observabilidad | Aceptado en parte (puertos, correlación, logs y salud, que rigen desde F1); la topología de producción, Propuesta (al responder GAP-022) | 2026-10-07 | 0006, 0024, 0033, 0039 |
| [0027](0027-if-match-con-saltos-exactos-de-derivados.md) | `If-Match` con saltos exactos de derivados y la comparación de wasichai | Aceptado | 2026-10-09 | 0038, 0040, 0051 |
| [0028](0028-qcs-confidenciales-con-recordreadscope.md) | QCS confidenciales con `RecordReadScope` | Aceptado | 2026-10-09 | 0042, 0048, 0050 |
| [0029](0029-alcance-de-lectura-por-proyecto.md) | Alcance de lectura por proyecto con `RecordReadScope` | Aceptado | 2026-10-10 | 0042, 0048, 0055 |
| [0030](0030-pdf-de-imprenta-con-lualatex.md) | PDF de imprenta con LuaLaTeX y DOCX con diseño editorial | Aceptado | 2026-10-19 | 0023, 0039 |
| [0031](0031-presentaciones-con-remotion.md) | Presentaciones de los entregables con Remotion | Aceptado | 2026-10-20 | 0023, 0039, 0040 |
