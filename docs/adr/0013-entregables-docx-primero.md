# ADR-0013: Entregables DOCX primero, con PDF por Gotenberg

## Estado

Aceptado · 2026-10-13 (F8), con las leyendas de número literal, marcador y PAGEREF que eligió el spike de F1 y las
decisiones del usuario del 2026-10-12 y del 2026-10-13 (ASM-133, ASM-135)

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
  mismo código de indicadores de los tableros (RQ-DSH-034, ADR-0016). *Generar*: Apache POI 5.5.1 (`DocxWriter`) arma el
  DOCX sobre la plantilla Word de la organización. *Convertir*: Gotenberg (`gotenberg/gotenberg:8-libreoffice`, que el
  plan 8.2c fija en la etiqueta 8.x exacta; red interna, y en desarrollo el puerto 8096 del loopback del VPS, ADR-0026)
  convierte ese DOCX a PDF, con `updateIndexes` y PDF/A-2b en lo que se emite.
- **Estructura en código, aspecto en Word, palabras en Markdown.** La narrativa es `deliverable_section.content` en
  Markdown (CommonMark con tablas GFM), escrita por personas; la IA solo sugiere (RQ-REP-011, RQ-REP-032, RQ-AI-012).
- **Modelo congelado.** El ensamblado produce un JSON canónico; su SHA-256 es la identidad de lo emitido, porque los bytes
  de DOCX y PDF no son deterministas. El modelo es interno y la API nunca lo sirve; volver a generar una versión emitida
  usa ese modelo, nunca datos vivos (RQ-REP-026).
- **Construcciones acotadas** a lo que LibreOffice convierte con fidelidad: estilos, imágenes en línea, tablas con
  celdas combinadas, saltos de sección, notas al pie simples y los campos TOC, PAGE, NUMPAGES y PAGEREF. Las leyendas
  llevan número literal y marcador, con listas propias de PAGEREF, porque el spike mostró que LibreOffice no numera por
  capítulo con SEQ y STYLEREF. Fuentes con métricas compatibles (Carlito, Caladea, Liberation); plantillas por
  organización en `deliverable_template`, validadas al subir (`TemplateValidator`): sin macros, objetos OLE, ActiveX,
  controles de contenido ni vínculos externos, con los estilos requeridos, solo los marcadores fijos y solo fuentes
  permitidas (RQ-SEC-019, ASM-055, ASM-135).
- **Gráficos y mapas.** Gráficos nativos de Word en la primera versión (RQ-REP-031); mapas PNG de GeoServer GetMap
  (ADR-0015).
- **Generación en segundo plano.** El trabajador JOB-05 corre como la plataforma con `ClusterLock` [WAS-ADR-0039],
  congela el modelo y genera, por cada nivel de divulgación del tipo, el borrador con marca de agua y la versión
  candidata sin ella. Los borradores no son registros: son los campos derivados `draft_*` de `deliverable`, con siete
  campos `FILE` de wasichai-files, y solo se conserva el último (ASM-061, ASM-133). Ninguna petición de un usuario llama
  a Gotenberg ni a GeoServer.
- **Ciclo de vida WF-H y emisión.** Aprobar exige el borrador LISTO y sin secciones cambiadas después de congelarlo.
  `POST /api/sgspe/deliverables/{id}/issue` solo copia y registra: sube como la persona una copia de cada candidata y,
  en una transacción [WAS-ADR-0038], crea una `deliverable_version` (`appendOnly`, `apiOnly`) por nivel, escribe
  `export_log` y aplica `issued` al final, que dispara la constancia de emisión de AUT-12. `CommandTransitionGuard`
  rechaza `issue` por la ruta genérica [WAS-ADR-0040].
- **Formatos por nivel.** INTERNO lleva DOCX y PDF; EXTERNO, solo PDF (ningún tipo de F8 prepara PUBLICO), y su DOCX es
  un paso intermedio que no se guarda. Qué contiene cada nivel lo decide ADR-0017; los archivos viven en wasichai-files
  (ADR-0014).
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
- Un DOCX editado fuera del sistema no es una versión y nunca se reimporta: solo se emite lo generado (GAP-010,
  resuelta; ASM-135).

### Seguimiento

- **Spike técnico de F1:** hecho el 2026-10-07 (el resultado, abajo); E2 falló y F8 adopta la alternativa de las
  leyendas. La alternativa para las leyendas (número literal más PAGEREF) está en [deliverables.md
  §7.5](../deliverables.md).
- Aceptado en F8 (2026-10-13): primero T2 (plan 8.2b) y después T1 (plan 8.2c); T3 a T10 quedan fuera de F8 (ASM-026).
- Abiertos: GAP-009 (marca y tenant), GAP-022 (despliegue) y UPS-31 (la plataforma adjunta archivos). GAP-010 quedó
  resuelta (ASM-135).
- Pruebas: los criterios de aceptación y las pruebas de [deliverables.md §13](../deliverables.md) (lectura del DOCX con
  POI, PDF con PDFBox, guardas de WF-H) y `DeliverableWorkflowApiTest` ([workflows.md](../workflows.md));
  `TemplateValidatorTest`, la etiqueta `gotenberg` de la CI y la suite lenta (`slowTest`).
- **Resultado del spike de F1 (2026-10-07).** POI 5.4.1 genera el DOCX y Gotenberg (`gotenberg/gotenberg:8-libreoffice`)
  lo convierte: E1 índice PASS (el índice de la página 2 dice 3, 5 y 7, las páginas reales de los tres capítulos;
  `updateIndexes` vale `true` por defecto y con `false` queda el texto precalculado del campo TOC); E2 leyendas por
  capítulo FAIL (LibreOffice 26.8 resuelve `STYLEREF 1 \s` con el texto del título, «Tabla Introducción.1», e ignora el
  `\s 1` de `SEQ`, que sigue 1 a 6 sin reiniciar por capítulo; sin «Error!»); E2b `PAGEREF` PASS (la frase dice «página
  5» y la leyenda marcada está en la página 5; con números literales salen «Tabla 1.1» a «Figura 3.1»); E3 paginación
  PASS («Página N de 8» en las 8 páginas); E4 marca «BORRADOR» PASS (en las 8 páginas); E5 fuentes PASS (subconjuntos
  incrustados de Carlito, Caladea y LiberationSans, sin sustituciones); E6 PDF/A-2b PASS (`pdfaid:part` 2 y
  `pdfaid:conformance` B); E7 102 páginas con 40 fotos en 8 s. Hallazgo aparte: `STYLEREF 1 \n` da el número del
  capítulo y `SEQ Tabla \r 1` en la primera leyenda de cada capítulo reinicia la cuenta; con eso las siete leyendas de E2
  salen bien en LibreOffice (no se probó en Word). Consecuencia: E2 falla y E2b pasa, así que las leyendas usan la
  alternativa de [deliverables.md §7.5](../deliverables.md) (número literal, marcador y `PAGEREF`); ADR-0013 no cambia
  (según la regla de decisión del plan de F1).

## Referencias

- Requisitos: RF-REP-001, RF-REP-002, RQ-REP-001, RQ-REP-002, RQ-REP-004, RQ-REP-011, RQ-REP-015, RQ-REP-017,
  RQ-REP-019, RQ-REP-021, RQ-REP-022, RQ-REP-026, RQ-REP-029, RQ-REP-031, RQ-REP-032, RQ-AI-012, RQ-DSH-034, RQ-SEC-004,
  RQ-SEC-019, CON-045, ASM-014, ASM-026, ASM-055, ASM-061, ASM-133, ASM-135, GAP-009, GAP-010, GAP-022, UPS-31.
- Documentos: [deliverables.md §2, §7, §8, §12, §13 y §15](../deliverables.md), [api.md §4.12](../api.md),
  [security.md §7.8, §8.2 y §11.4](../security.md), [architecture.md §4, §8 y §14](../architecture.md),
  [platform-fit.md §5 y §6](../platform-fit.md).
- wasichai: `WAS-ADR-0023` (A document is frozen when it is issued), `WAS-ADR-0038` (RecordService joins the caller's
  transaction, and that is supported API), `WAS-ADR-0039` (Background work runs RecordService as the platform, and takes a
  cluster lock), `WAS-ADR-0040` (Append-only objects, a pre-write guard SPI, and api-only objects), `WAS-ADR-0042` (An
  object declares its own actions, and they are granted, checked and listed like CRUD).
- ADR de SGSPE: ADR-0014, ADR-0015, ADR-0016, ADR-0017.
- Fuentes: [USR-01], [DOC-01], [DOC-02], [PRM-01 §18].
