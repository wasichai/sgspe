# ADR-0030: PDF de imprenta con LuaLaTeX y DOCX con diseño editorial

## Estado

Aceptado · 2026-10-19, por decisión del usuario del 2026-10-19 (ASM-139 a ASM-141). Reemplaza la parte de ADR-0013 que
convierte a PDF el mismo DOCX con Gotenberg; lo demás de ADR-0013 sigue vigente: el modelo congelado, el DOCX con
Apache POI, la plantilla Word de la organización para el DOCX, la generación en JOB-05, WF-H y la emisión.

## Contexto

- Los entregables emitidos en el T1 de demostración (2026-10-18, 120 páginas en INTERNO) se ven abarrotados y sus
  tablas se leen mal. El usuario pide una calidad de editorial profesional y sugiere evaluar LaTeX o AsciiDoc.
- Las causas están en el código de F8. La plantilla base (`BaseTemplate`) solo define tamaño y negrita: no tiene
  márgenes, interlineado, espacio entre párrafos ni `keepNext` en los títulos. `DocxWriter` no fija el ancho de una
  tabla ni el de sus columnas, ni su relleno, alineación o tamaño de letra. Además, busca el estilo `Table Grid`, que
  la base no define.
- LibreOffice, el motor de Gotenberg, compone párrafos línea por línea, sin guiones de buena calidad ni
  microtipografía. Aunque se arregle el DOCX, el PDF que sale de él nunca tendrá la calidad de una composición
  tipográfica.
- RF-REP-001 sigue pidiendo el DOCX, y la plantilla Word de cada organización (ASM-135) sigue dando su aspecto.

## Opciones consideradas

### Pulir el DOCX y seguir convirtiéndolo con Gotenberg

- **A favor:** un solo motor, DOCX y PDF idénticos y ningún servicio nuevo.
- **En contra:** el PDF queda limitado a lo que compone LibreOffice: sin cortes de párrafo óptimos, con guiones
  pobres y sin protrusión ni expansión.

### PDF con Typst

- **A favor:** tipografía de alto nivel con un binario chico y rápido, y PDF/A-2b.
- **En contra:** el ecosistema para tablas largas con encabezado repetido, celdas combinadas en varias filas y
  gráficos es menos maduro, y aún no tiene la trayectoria de LaTeX en informes técnicos.

### PDF con AsciiDoc (asciidoctor-pdf)

- **A favor:** escribir el contenido es sencillo.
- **En contra:** Prawn compone con menos calidad que TeX, suma Ruby al despliegue y sus tablas largas y combinadas
  son limitadas.

### PDF con LuaLaTeX en un contenedor propio, con el DOCX pulido (elegida)

- **A favor:**
  - el algoritmo de párrafos de Knuth y Plass, guiones en español, microtype con protrusión y expansión, y tablas
    booktabs largas con encabezado repetido (`xltabular`);
  - gráficos vectoriales con pgfplots;
  - PDF/A-2b nativo con `\DocumentMetadata`, y una clase propia con portada, encabezado y pie de editorial.
- **En contra:**
  - dos motores de composición, así que el DOCX y el PDF tienen el mismo contenido pero distinta paginación;
  - una imagen de 1 GB, y componer es más lento que convertir (unas tres pasadas);
  - el texto del modelo entra a un lenguaje de macros y hay que escaparlo sin excepción;
  - la plantilla Word del tenant no rige el PDF.

## Decisión

- **Dos salidas desde el mismo modelo congelado.** `ReportDisclosure` transforma el modelo por nivel y de ahí salen
  dos documentos:
  - el DOCX, con `DocxWriter` (Apache POI) sobre la plantilla Word, solo en INTERNO;
  - el PDF, con `LatexWriter`, que arma un paquete (`main.tex`, la clase `sgspe-informe.cls` versionada en el backend,
    las fotos reducidas, los mapas y el logo) que compone el servicio `sgspe-latex`.
  
  El DOCX de EXTERNO deja de generarse, porque solo servía como paso hacia el PDF.
- **Mismo contenido, distinta paginación.** Los dos salen del mismo modelo y del mismo `CaptionNumbering`, así que
  tienen los mismos títulos, leyendas con el mismo número literal, tablas, celdas y textos. La prueba de paridad
  compara en orden los títulos, las leyendas y los textos de las celdas del DOCX (POI) y del PDF (PDFBox), y
  reemplaza a la prueba de «mismo texto» de ADR-0013.
- **El servicio `sgspe-latex`.** Imagen propia (`develop/latex/Dockerfile`) sobre `debian:trixie-slim` fijada por
  digest, con TeX Live de Debian (LuaHBTeX 1.18), `latexmk`, Source Sans 3 de Debian y Source Serif 4 descargada con
  SHA-256 fijo. Un envoltorio HTTP en Python de biblioteca estándar recibe un tar con topes de bytes y de entradas, sin
  rutas absolutas ni `..`. Compone en una carpeta temporal con:
  - `-no-shell-escape`, `--nosocket`, `openin_any=p` y `openout_any=p`;
  - un plazo y una corrida a la vez;
  - usuario sin privilegios, sistema de archivos de solo lectura, tmpfs y sin red de salida.
  
  Devuelve el PDF o un error con su código, nunca con texto del documento. El borrador y la candidata de un nivel
  salen de un mismo pedido: la candidata compone completa, y el borrador reutiliza sus archivos auxiliares en una
  pasada más con la marca, porque la marca no cambia la paginación. En desarrollo escucha en el puerto **8097** del
  loopback del VPS (ADR-0026). Solo lo llama JOB-05, nunca una petición.
- **Escape sin excepciones.** Todo texto del modelo pasa por `LatexText`, la única puerta hacia TeX. Escapa
  `\ { } $ & # ^ _ % ~`: `^` siempre, lo que cierra la notación `^^`. También quita los caracteres de control. Nada se
  interpola sin pasar por ella, y lo vigilan pruebas con entradas hostiles. `--safer` no se usa porque rompe
  luaotfload (spike del 2026-10-19); el aislamiento lo da el contenedor.
- **Clase propia, marca del tenant.** `sgspe-informe.cls` (KOMA `scrreprt`) es de SGSPE, se versiona con el backend
  y viaja en cada paquete. Su SHA-256 se congela en el modelo. Cada organización aporta solo un logo (PNG o JPEG) y un
  color de acento junto con su plantilla (ASM-140); nunca código TeX.
- **Diseño tipográfico compartido** (ASM-141). Lo comparten la clase, la plantilla base y `DocxWriter`:
  - márgenes, interlineado, espacios y tamaños comunes (`ReportDesign`);
  - anchos de columna según el contenido (`ColumnWidths`);
  - tablas sin filetes verticales.

  Las fuentes son Source Serif 4 y Source Sans 3 en el PDF, y Caladea y Carlito en el DOCX (la lista de ASM-135).
- **Gotenberg se retira** de `compose.yml`, de la CI y de la configuración cuando el corte esté verde.

## Consecuencias

### Positivas

- El PDF tiene calidad de imprenta: párrafos justificados con guiones, tablas legibles, portada y encabezado y pie
  de editorial, gráficos vectoriales y PDF/A-2b.
- El DOCX mejora también y sigue editable sobre la plantilla del tenant.
- El contenido de lo emitido sigue identificado por el SHA-256 del modelo (RQ-REP-026). La clase y la imagen quedan
  fijadas, así que volver a generar da el mismo contenido.

### Negativas y riesgos

- El número de página de una leyenda o de un título puede diferir entre el DOCX y el PDF. El índice de cada formato
  es correcto para ese formato.
- Componer es más lento. El spike dio 87 s para 96 páginas con 30 gráficos en tres pasadas, y un pedido por nivel
  hace la candidata y el borrador. Lo mitigan el plazo por pedido, la generación en segundo plano y la suite lenta.
- Hay un servicio y una imagen nuevos que mantener, y una actualización de TeX Live puede mover la paginación: la
  imagen se fija por digest, como Gotenberg.
- La plantilla Word del tenant ya no da el aspecto del PDF, solo el del DOCX.

### Seguimiento

- **Spike del 2026-10-19, en el VPS.** Un documento de prueba con la clase compuso con PDF/A-2b (`pdfaid:part` 2 y
  `pdfaid:conformance` B). Solo incrustó subconjuntos de Source Serif 4 y Source Sans 3, puso «BORRADOR» en cada
  página, mostró el índice y las listas de tablas y figuras con su página, y no tuvo cajas desbordadas. pgfplots no
  incrustó fuentes matemáticas con `assume math mode`. 96 páginas compusieron en 87 s, y `--safer` impide cargar las
  fuentes.
- **El PDF etiquetado** (RQ-REP-028, opcional) no se probó en el spike y sigue pendiente, como en ADR-0013.
- **La interfaz para subir el logo y el acento** va en `sgspe-ui`, en un issue aparte.

## Referencias

- Requisitos: RF-REP-001, RF-REP-002, RQ-REP-001, RQ-REP-004, RQ-REP-006, RQ-REP-010, RQ-REP-017, RQ-REP-019,
  RQ-REP-021, RQ-REP-026, RQ-REP-028, RQ-REP-031, RQ-SEC-019, ASM-014, ASM-135, ASM-139, ASM-140, ASM-141, GAP-009.
- Documentos: [deliverables.md §7](../deliverables.md), [architecture.md §8 y §11](../architecture.md),
  [security.md](../security.md).
- ADR de SGSPE: ADR-0013 (reemplazado en parte), ADR-0017, ADR-0026.
- wasichai: `WAS-ADR-0023` (A document is frozen when it is issued), `WAS-ADR-0039` (Background work runs RecordService
  as the platform, and takes a cluster lock).
