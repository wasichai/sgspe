# ADR-0013: Entregables DOCX primero, con PDF por Gotenberg

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F8

## Contexto

- El usuario pide que el sistema genere entregables como DOC-01 (DOCX) y DOC-02 (PDF), en ambos formatos [USR-01]
  (RF-REP-001, RF-REP-002, RQ-REP-001). Son informes de varias secciones, con índice, tablas numeradas por capítulo, fotos,
  mapas y gráficos, y DOC-01 usa campos de Word para su índice y su numeración [DOC-01 índice] [DOC-02].
- wasichai-documents emite una plantilla congelada de un solo registro que se imprime desde el navegador; no genera DOCX
  ni PDF en el servidor ni informes con mapas [WAS-ADR-0023] (CON-045). Esos documentos siguen siendo la vía de las actas,
  minutas y cartas de un registro (RQ-REP-029).
- Un entregable emitido debe poder reproducirse tal cual, con hashes, y solo se emite lo aprobado (RQ-REP-017,
  RQ-REP-026); lo no emitido lleva la marca BORRADOR (RQ-REP-021).

## Opciones consideradas

### Ampliar el uso de wasichai-documents

- A favor: ya congela, numera y audita la emisión [WAS-ADR-0023].
- En contra: un solo registro por documento, impresión desde el navegador y sin DOCX; no cubre informes de varias
  secciones con mapas (CON-045).

### HTML a PDF en el servidor, la ruta de srtm (Thymeleaf y OpenHTMLtoPDF)

- A favor: conocida en el ecosistema.
- En contra: el DOCX saldría de otro renderizador y los dos formatos divergirían; no conserva los campos de Word de DOC-01.
  ASM-014 la reemplaza.

### DOCX primero con Apache POI y PDF por Gotenberg desde ese mismo DOCX

- A favor: un solo documento fuente para los dos formatos, el mismo argumento de un solo renderizador de WAS-ADR-0023;
  conserva índices y campos de Word; POI sirve también para las exportaciones XLSX.
- En contra: LibreOffice pagina distinto que Word; exige acotar las construcciones de Word y las fuentes, y suma un
  servicio de conversión al despliegue. Se descartan además los motores de plantillas sobre Word (uno atado a POI 5.2,
  otro con expresiones de Spring dentro de archivos del tenant) y las bibliotecas AGPL o comerciales (iText, Aspose).

## Decisión

DOCX primero (ASM-014), en el paquete propio `sgspe.deliverable` ([deliverables.md §7](../deliverables.md)):

- **Tres etapas.** *Ensamblar*: una clase Kotlin por tipo de entregable lee con `RecordService`, `sgspe.territory` y el
  mismo código de indicadores de los tableros (RQ-DSH-034, ADR-0016). *Generar*: Apache POI 5.4 o posterior arma el DOCX
  sobre la plantilla Word de la organización. *Convertir*: Gotenberg (`gotenberg/gotenberg:8-libreoffice`, red interna)
  convierte ese DOCX a PDF, con `updateIndexes` y PDF/A-2b en lo que se emite.
- **Estructura en código, aspecto en Word, palabras en Markdown.** La narrativa es `deliverable_section.content` en
  Markdown (CommonMark con tablas GFM), escrita por personas; la IA solo sugiere (RQ-REP-011, RQ-REP-032, RQ-AI-012).
- **Modelo congelado.** El ensamblado produce un JSON canónico; su SHA-256 es la identidad de lo emitido, porque los bytes
  de DOCX y PDF no son deterministas. El modelo es interno y la API nunca lo sirve; volver a generar una versión emitida
  usa ese modelo, nunca datos vivos (RQ-REP-026).
- **Construcciones acotadas** a lo que LibreOffice convierte con fidelidad: estilos, imágenes en línea, tablas con celdas
  combinadas, saltos de sección, notas al pie simples y los campos TOC, SEQ, STYLEREF, PAGE, NUMPAGES y PAGEREF. Fuentes con
  métricas compatibles (Carlito, Caladea, Liberation); plantillas validadas al subir: sin macros, con los estilos
  requeridos y solo fuentes permitidas (RQ-SEC-019, ASM-055).
- **Gráficos y mapas.** Gráficos nativos de Word en la primera versión (RQ-REP-031); mapas PNG de GeoServer GetMap
  (ADR-0015).
- **Generación en segundo plano.** El trabajador JOB-05 corre como la plataforma con `ClusterLock` [WAS-ADR-0039], congela
  el modelo y genera, por cada nivel de divulgación del tipo, el borrador con marca de agua y la versión candidata sin ella.
  Los borradores no son registros: son los campos derivados `draft_*` de `deliverable` (ASM-061). Ninguna petición de un
  usuario llama a Gotenberg ni a GeoServer.
- **Ciclo de vida WF-H y emisión.** Aprobar exige el borrador LISTO y sin secciones cambiadas después de congelarlo.
  `POST /api/sgspe/deliverables/{id}/issue` solo copia y registra: en una transacción [WAS-ADR-0038] aplica `issued`, crea
  una `deliverable_version` (`appendOnly`, `apiOnly`) por nivel y escribe `export_log`. `CommandTransitionGuard` rechaza
  `issue` por la ruta genérica [WAS-ADR-0040].
- **Formatos por nivel.** INTERNO lleva DOCX y PDF; EXTERNO y PUBLICO, solo PDF, y su DOCX es un paso intermedio que no
  se guarda. Qué contiene cada nivel lo decide ADR-0017; los archivos viven en el almacenamiento de ADR-0014.
- **Permisos.** Acciones declaradas sobre `deliverable` [WAS-ADR-0042]: `GENERATE`, `APPROVE`, `ISSUE` y
  `DOWNLOAD_RESTRICTED` solo para los roles que leen los campos sensibles, porque el contenido no se filtra por permisos de
  campo; `EXPORT` para las versiones EXTERNO y PUBLICO (RQ-SEC-004) ([security.md §7.8 y §8.2](../security.md)).

## Consecuencias

### Positivas

- DOCX y PDF tienen el mismo texto, y lo emitido se reproduce y se verifica por hash.
- Tableros y entregables dan las mismas cifras.
- Las exportaciones XLSX reutilizan POI desde F2 (RQ-REP-022).

### Negativas y riesgos

- Paginación y numeración de leyendas pueden diferir de Word; las fuentes de Microsoft no se usan dentro del contenedor
  ([architecture.md §14](../architecture.md), riesgo 7).
- Gotenberg puede tardar con un documento de unas 100 páginas con fotos: tiempo de espera configurable, fotos reducidas y
  generación asíncrona (riesgo 11). Cada borrador cuesta dos conversiones por nivel.
- La emisión de mapas depende de GeoServer (ADR-0015).
- Un DOCX editado fuera del sistema no es una versión: en el MVP solo se emite lo generado (GAP-010).

### Seguimiento

- **Spike técnico, recomendado al inicio de F1 si el checkpoint lo aprueba:** una plantilla con lo que usa DOC-01,
  generada con POI y convertida con Gotenberg; verifica números de página, leyendas, fuentes incrustadas y PDF/A. Si
  falla, un ADR nuevo reemplaza a este sin cambiar el modelo de datos (ASM-014). La alternativa para las leyendas (número
  literal más PAGEREF) está en [deliverables.md §7.5](../deliverables.md).
- Se acepta al iniciar F8, donde se genera primero T2 y después T1; T3 a T10 dependen de ASM-026.
- Abiertos: GAP-009 (marca y tenant), GAP-010 (plantillas corporativas), GAP-022 (despliegue).
- Pruebas: los criterios de aceptación y las pruebas de [deliverables.md §13](../deliverables.md) (lectura del DOCX con
  POI, PDF con PDFBox, guardas de WF-H) y `DeliverableWorkflowApiTest` ([workflows.md](../workflows.md)).

## Referencias

- Requisitos: RF-REP-001, RF-REP-002, RQ-REP-001, RQ-REP-002, RQ-REP-004, RQ-REP-011, RQ-REP-015, RQ-REP-017, RQ-REP-019,
  RQ-REP-021, RQ-REP-022, RQ-REP-026, RQ-REP-029, RQ-REP-031, RQ-REP-032, RQ-AI-012, RQ-DSH-034, RQ-SEC-004, RQ-SEC-019,
  CON-045, ASM-014, ASM-026, ASM-055, ASM-061, GAP-009, GAP-010, GAP-022.
- Documentos: [deliverables.md §2, §7, §8, §12, §13 y §15](../deliverables.md), [api.md §4.12](../api.md),
  [security.md §7.8, §8.2 y §11.4](../security.md), [architecture.md §4, §8 y §14](../architecture.md),
  [platform-fit.md §5 y §6](../platform-fit.md).
- wasichai: `WAS-ADR-0023` (A document is frozen when it is issued), `WAS-ADR-0038` (RecordService joins the caller's
  transaction, and that is supported API), `WAS-ADR-0039` (Background work runs RecordService as the platform, and takes a
  cluster lock), `WAS-ADR-0040` (Append-only objects, a pre-write guard SPI, and api-only objects), `WAS-ADR-0042` (An
  object declares its own actions, and they are granted, checked and listed like CRUD).
- ADR de SGSPE: ADR-0014, ADR-0015, ADR-0016, ADR-0017.
- Fuentes: [USR-01], [DOC-01], [DOC-02], [PRM-01 §18].
