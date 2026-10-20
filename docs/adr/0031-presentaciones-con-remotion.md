# ADR-0031: Presentaciones de los entregables con Remotion

## Estado

Aceptado · 2026-10-20, por decisión del usuario del 2026-10-20 (RF-REP-008, ASM-143 a ASM-147). No reemplaza a ningún
ADR: ADR-0013 y ADR-0030 siguen rigiendo el DOCX y el PDF del entregable.

## Contexto

- **Para qué sirve.** Un T1 tiene más de 120 páginas, y una institución o una asamblea no las leen. El usuario describe
  el recorrido así:
  1. la presentación se envía por correo a una institución, que la revisa;
  2. después, un sociólogo la presenta desde su laptop en una asamblea pública, donde puede no haber internet.
- **Mismos hechos, otro relato.** La presentación cuenta los hechos de un entregable ya emitido, con otro relato según
  quién la mira. Ese quién (dirección, campo, investigación, instituciones o comunidad) es una dimensión distinta del
  nivel de divulgación, que dice qué se puede contar.
- **Lo que hay en el código.** No existe ningún concepto de presentación ni de público. El modelo congelado
  (`ReportModel`) guarda todo como texto con el formato de `ReportFormat` (Locale.US, `1,234.50`, y vacío si falta): las
  unidades van en los encabezados y no hay denominadores. `ReportDisclosure` solo prepara INTERNO y EXTERNO
  (`require(level != PUBLICO)`), y `SmallCells` exige la grilla completa del catálogo, que las tablas del modelo no
  traen.
- **El usuario pide Remotion** (React) como motor de composición. Remotion no produce PPTX ni un PDF de varias páginas:
  `renderStill` compone una lámina, a PNG o a PDF con `Page.printToPDF` de Chrome.

## Opciones consideradas

### Reusar `sgspe-latex` con la clase `beamer`

- **A favor:** no suma servicio ni lenguaje y la tipografía es de calidad.
- **En contra:**
  - no hay presentación en el navegador ni animación;
  - el diseño editorial de láminas (retícula, cifras grandes, gráficos con jerarquía) es torpe en beamer;
  - el usuario pidió Remotion.

### Componentes React propios pasados a PDF con Chromium (Playwright)

- **A favor:** no tiene licencia comercial y la imagen es más chica.
- **En contra:**
  - no tiene línea de tiempo, así que no hay video ni animación coherente entre el navegador y el PDF;
  - habría que reconstruir lo que Remotion ya resuelve: esperar las fuentes, cuadro por cuadro y el reproductor.

### Remotion en un servicio propio y su reproductor en `sgspe-ui` (elegida)

- **A favor:**
  - una sola composición da el PDF (el último fotograma de cada lámina), la presentación animada en el navegador
    (`@remotion/player`) y, más adelante, el MP4 (`renderMedia`);
  - React y SVG para gráficos rigurosos;
  - Chrome compone el texto como vector, con las fuentes incrustadas.
- **En contra:**
  - la licencia de Remotion no es libre (ver Decisión);
  - suma un servicio Node con Chrome, una imagen pesada y Chrome sin sandbox propio dentro del contenedor;
  - Chrome no garantiza bytes idénticos entre corridas.

## Decisión

- **Qué es una presentación** (ASM-143):
  - una salida **derivada** de una versión ya emitida, de su modelo congelado INTERNO, cuyo SHA-256 se verifica;
  - no tiene aprobación propia, no cambia los hechos y no es una emisión;
  - no lleva la marca BORRADOR, pero su pie dice «Derivada de <código> rev. <r>, nivel X; no es un documento emitido».
- **Formatos:**
  - un **PDF de láminas** 16:9 (1920 × 1080 px, 1440 × 810 pt), la copia de referencia y la que funciona sin
    conexión;
  - la **presentación en el navegador** con `@remotion/player` en `sgspe-ui`;
  - el **MP4** queda para después, con la misma composición;
  - **sin PPTX**: ni Remotion ni el código lo producen, y simular un archivo editable sería engañoso. Si hace falta, va
    en un ADR aparte.
- **Público y nivel son dimensiones distintas** (ASM-145).
  - El nivel se aplica primero, con `ReportDisclosure.apply` sobre el modelo nominal. Después se extrae el material y
    después se planifican las láminas.
  - El público decide el relato, la selección, la densidad, el vocabulario y el tono visual, y solo puede restringir
    el nivel, nunca relajarlo.
  - Los componentes React solo pintan lo que trae el spec: no consultan, no anonimizan, no interpretan HTML y no
    cargan nada externo.
- **PUBLICO para las presentaciones** (ASM-146). Sale del modelo nominal como una lista permitida que falla cerrada:
  - solo entran hechos tipados (`facts`, campo nuevo y opcional del modelo) pasados por `SmallCells`;
  - no entran párrafos, citas, menciones, mapas ni fotos con personas;
  - un modelo sin `facts`, congelado antes de este cambio, no admite PUBLICO.
- **Las capas**, todas en `sgspe.deliverable` salvo la divulgación, que vive en `sgspe.privacy`:
  1. `DeckMaterial` extrae el material tipado de cada tipo;
  2. `DeckPlanner`, puro y determinista, arma el arco del público y elige los layouts. Mide el texto con las mismas
     fuentes que el renderer y divide en vez de encoger;
  3. `DeckSpec` es un JSON canónico con versión de esquema, que trae las imágenes incrustadas.
  
  Las cifras salen ya formateadas en es-PE del backend. El spike mostró que el ICU del Chrome headless no formatea
  es-PE.
- **El servicio `sgspe-slides`.**
  - Su imagen se construye desde `slides/Dockerfile`, sobre Node LTS en Debian bookworm fijado por digest, con Remotion
    fijado en una versión exacta de al menos dos semanas (4.0.533 al adoptarlo).
  - El bundle y el Chrome headless-shell se preparan al construir la imagen.
  - Expone `POST /render` (un tar con `deck.json` entra, un tar con `deck.pdf` sale) y `GET /health`.
  - Cada lámina se compone con un `renderStill` a PDF con sus props, en un solo navegador, y las láminas se unen con
    pdf-lib con metadatos fijos.
  - Corre con a lo más `SLIDES_CONCURRENCY` composiciones a la vez y con un plazo.
  - Una verificación dentro de la composición (desborde, solape, tamaño mínimo, fuente cargada, imagen deformada,
    lámina vacía) cancela el render con su código y el id de la lámina. Nunca devuelve un archivo parcial ni texto del
    documento.
  - Corre con usuario sin privilegios, de solo lectura, con tmpfs en `/tmp` y `/dev/shm`, sin capacidades y sin red de
    salida en producción. En desarrollo publica el puerto **8099** del loopback del VPS (ADR-0026).
  - Solo lo llama JOB-09, nunca una petición.
- **Biblioteca compartida.**
  - Los tokens, los layouts y las composiciones viven en `slides/` y se publican como `@wasichai/sgspe-slides`, con la
    versión del backend (release-please, `extra-files`).
  - `sgspe-ui` los usa con `@remotion/player` sobre el spec que sirve el backend.
  - El PDF es la referencia, y el reproductor acepta la versión de esquema vigente y la anterior.
- **Licencia** (decisión del usuario del 2026-10-20: la organización califica para la licencia Free).
  - Se usa la licencia Free de Remotion: personas, organizaciones sin fines de lucro y empresas de hasta 3 personas,
    contando toda la empresa y, desde la v5, también a los contratistas.
  - Si el operador deja de calificar, o si cada tenant instala su propio SGSPE, cada operador necesita la licencia de
    empresa («Automators»: por render, con un mínimo mensual), y el reproductor en la interfaz también cuenta.
  - En la v4 no se pasa `licenseKey`. Con clave, el renderer intenta enviar telemetría a `www.remotion.pro`, lo que la
    red sin salida bloquea (spike del 2026-10-20). Una clave paga exigiría un reporte alternativo, como prevé la
    licencia.
- **Identidad y reproducibilidad.**
  - Lo que identifica a una presentación es `model_sha256`, `spec_sha256`, `planner_version`, `design_version` y
    `renderer_version`.
  - Regenerar desde la misma versión emitida da el mismo spec. Los bytes del PDF pueden diferir entre corridas de
    Chrome, así que su SHA-256 solo sirve como integridad del archivo guardado.
  - En la composición no se usan `Date`, `Math.random` ni el entorno.

## Consecuencias

### Positivas

- Un mismo entregable emitido se cuenta a cinco públicos sin duplicar cálculos ni motores. Las cifras salen de los
  lectores del dominio.
- La comunidad recibe una versión PUBLICO que cumple ASM-088. Antes, ningún entregable preparaba PUBLICO.
- La misma composición sirve para el PDF, el navegador y, después, el video.

### Negativas y riesgos

- **La licencia.** La elegibilidad Free depende del tamaño del operador, y la v5 endurece las condiciones. Hay que
  revisarla en cada subida de versión mayor.
- **Chrome sin sandbox propio.** El aislamiento lo da el contenedor. El spec no admite URL, el servicio no tiene red de
  salida en producción y las imágenes viajan incrustadas y validadas.
- **La imagen es grande** (Node, Chrome y bibliotecas): se construye en dos etapas.
- **Subir Remotion o Chrome puede mover la composición.** La versión va fijada, y cada subida pasa por la revisión
  visual.
- **Los modelos congelados antes de este cambio** (`typeVersion` 1) dan presentaciones INTERNO y EXTERNO leídas del
  texto, pero no PUBLICO.

### Seguimiento

- **Spike del 2026-10-20** (Remotion 4.0.533, Node 24, en el VPS):
  - **El PDF.** `renderStill` a PDF da texto vectorial seleccionable con subconjuntos incrustados de Source Sans 3 y
    Source Serif 4 Display, a 1440 × 810 pt.
  - **El tiempo:** de 0,5 a 1,8 s por lámina.
  - **El aislamiento.** Corre con `--network none`, `--read-only` y `--cap-drop ALL`. Con `licenseKey` intenta enviar
    telemetría y falla sin cortar el render.
  - **Lo que hay que cuidar:**
    - el nombre de una familia tipográfica debe ser una sola palabra: «Serif Display» no se aplicó;
    - el control de desborde mide la caja de las líneas contra el área asignada, no `scrollHeight`, que cuenta la tinta
      de los glifos;
    - el espaciado entre letras parte el texto que extrae PDFBox;
    - las cifras se formatean en el backend.
- **Implementación del servicio (2026-10-20).** El contrato quedó más simple que el tar de la Decisión: `POST /render`
  recibe la especificación en JSON, que ya trae las imágenes incrustadas, y devuelve el PDF. Una prueba con Chrome real
  encontró que reusar la composición elegida para la primera lámina componía esa lámina en todas las páginas: la
  composición se elige por lámina. La revisión visual de las tres presentaciones de demostración llevó a dar a cada
  público rasgos propios: la voz del título (serif de titulares, serif de texto o sans con filete), el cartel de cifras
  y las columnas sin ejes de COMUNIDAD, y la banda de INSTITUCIONES.
- **El planificador (2026-10-20).** Un modelo congelado sin hechos tipados (`typeVersion` 1) no da presentación en ningún
  nivel, no solo en PUBLICO: todavía no hay despliegue (GAP-022), así que leer el texto de los modelos v1 no tiene a quién
  servir. Las especificaciones que genera el planificador con datos sintéticos (`DeckPlannerTest`) se guardan en
  `slides/fixtures/generated/`, y el paquete las valida con su esquema y las compone: el contrato entre Kotlin y TypeScript
  queda vigilado en las dos puntas.
- **El MP4** va en un plan posterior.
- **La interfaz** va en `sgspe-ui`, en un PR aparte.

## Referencias

- Requisitos: RF-REP-008, RQ-REP-014, RQ-REP-021, RQ-REP-026, RQ-REP-033, RQ-REP-034 a RQ-REP-038, RQ-AUD-011,
  ASM-037, ASM-088, ASM-143 a ASM-147.
- Documentos: [deliverables.md §16](../deliverables.md), [architecture.md](../architecture.md),
  [security.md](../security.md), [api.md §4.12](../api.md).
- ADR de SGSPE: ADR-0013, ADR-0016, ADR-0017, ADR-0026, ADR-0030.
- Remotion: [licencia](https://github.com/remotion-dev/remotion/blob/main/LICENSE.md),
  [renderStill](https://www.remotion.dev/docs/renderer/render-still), [Docker](https://www.remotion.dev/docs/docker).
