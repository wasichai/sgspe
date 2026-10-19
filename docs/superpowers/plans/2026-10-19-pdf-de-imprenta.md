# Entregables de calidad editorial: PDF con LuaLaTeX y DOCX pulido, plan de implementación

> **Para agentes:** SUB-SKILL REQUERIDA: superpowers:executing-plans, tarea por tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** que los entregables T1 y T2 tengan la calidad de una editorial profesional. Hoy se ven abarrotados y sus
tablas se leen mal.

**Arquitectura** (ADR-0030, ASM-139 a ASM-141):
- **DOCX.** Lo arma Apache POI sobre la plantilla Word, solo en INTERNO, con un diseño tipográfico común
  (`ReportDesign`) y anchos de columna según el contenido (`ColumnWidths`).
- **PDF.** Se compone con LuaLaTeX desde el mismo modelo por nivel. `LatexWriter` arma el paquete con la clase
  `sgspe-informe.cls`, y el servicio `sgspe-latex` (puerto 8097, solo loopback) lo compone.
- **Gotenberg** se retira.

**Tecnologías:** Kotlin 2.4 y Spring Boot 4.1 WebFlux sobre wasichai 1.3.0, Apache POI 5.5.1, commonmark-java,
PDFBox, TeX Live de Debian trixie (LuaHBTeX 1.18), KOMA-Script, Python 3 (stdlib).

**Especificación:** ADR-0030 y `docs/deliverables.md` §7. Las decisiones del usuario del 2026-10-19 son:

- el PDF se compone con LaTeX;
- el DOCX también se pule;
- la clase es de SGSPE, con el logo y el acento del tenant;
- LuaLaTeX corre en un contenedor propio.

## Restricciones globales

- **El escape.** Todo texto del modelo entra a TeX por `LatexText`; ninguna interpolación lo esquiva.
- **Determinismo.** El paquete de LaTeX es una función pura del modelo y de los bytes de las imágenes: los mismos bytes
  en cada armado.
- **El modelo.** La forma canónica de los modelos ya congelados no cambia. Los campos nuevos (`columns` y `brand`) son
  opcionales y el JSON canónico no escribe nulos.
- **Dentro del contenedor.** Sin `shell-escape`, con `--nosocket`, `openout_any=p`, sin red de
  salida, con el sistema de archivos de solo lectura y como usuario sin privilegios.
- **Las fuentes del PDF:** solo Source Serif 4 y Source Sans 3. Las de las plantillas Word, solo las de
  `TemplateStyles.FONTS`.

## Tareas

### 1. Diseño y spike

- [x] Spike en el VPS (2026-10-19). PDF/A-2b, solo las fuentes de la clase, BORRADOR en cada página, sin cajas
  desbordadas y 96 páginas en 87 s. `--safer` rompe luaotfload.
- [x] ADR-0030, ASM-139 a ASM-141, `deliverables.md` §7 y las menciones de Gotenberg en los demás documentos.

### 2. Anchos de columna y DOCX pulido

- [x] `ColumnWidths` (puro) con `ColumnWidthsTest`:
  - las cifras, porcentajes y fechas, a la derecha;
  - la palabra más larga como mínimo;
  - el reparto por la raíz del ancho natural;
  - `colSpan` ignorado al medir;
  - los pesos de `ColumnSpec` cuando la tabla los trae.
- [x] `ReportDesign`: los tokens de página, tamaños, espacios y colores.
- [x] `BaseTemplate`:
  - márgenes, Normal justificado con interlineado y espacios, y guiones automáticos;
  - títulos con `keepNext` y color;
  - los estilos opcionales «SGSPE Tabla», «Fuente» y «Título de lista»;
  - Caladea en el cuerpo y Carlito en los títulos y las tablas.
- [x] `DocxWriter`:
  - tablas al 100 %, con `tblLayout` fijo, `gridCol`, `tcMar`, alineación por columna y 9 pt;
  - listas con sangría francesa;
  - títulos de lista y fuente con estilo.
- [x] `TemplateStyles` y `TemplateValidator`: aceptan los estilos opcionales.

### 3. LaTeX

- [x] `LatexText` con `LatexTextTest`, con entradas hostiles.
- [x] `sgspe-informe.cls` en `src/main/resources/sgspe/deliverable/latex/`.
- [x] `LatexWriter` y `LatexBundle` con `LatexWriterTest`. Un caso por tipo de bloque, más:
  - leyendas iguales a las de `CaptionNumbering`;
  - el horizontal;
  - la narrativa profunda;
  - el determinismo.

### 4. Servicio y corte

- [x] `develop/latex/Dockerfile`, `server.py` y `test_server.py`, y el servicio `latex` de `compose.yml`.
- [x] `PdfRenderer` y `LatexConverter` con `LatexConverterTest`.
- [x] Las propiedades `sgspe.deliverable.latex.*`.
- [x] La lista de fuentes del PDF en `PdfChecks`.
- [x] JOB-05: el DOCX solo en INTERNO, y el PDF con un pedido por nivel (candidata y borrador).
- [x] `LatexPdfTest` (etiqueta `latex`) con la paridad de contenido, y la CI. `SlowSuiteTest`.
- [x] Retirar Gotenberg: `GotenbergConverter`, sus pruebas, el servicio y la configuración.

### 5. Marca

- [ ] `deliverable_template` suma `logo` y `accent_color`. Hay que validarlos al subir y congelarlos en `meta.brand`.

### 6. Revisión visual

- [ ] Componer T1 (con el conjunto de demostración) y T2, revisar las páginas y ajustar.
