# Presentaciones de los entregables con Remotion, plan de implementación

> **Para agentes:** SUB-SKILL REQUERIDA: superpowers:executing-plans, tarea por tarea. Los pasos usan casillas (`- [ ]`).

**Objetivo:** contar una versión emitida de T1 o T2 como presentación, para un público y un nivel de divulgación. Sale
un PDF de láminas, que se envía y sirve sin conexión, y una presentación en el navegador, con la misma composición.
La calidad tiene que ser la de un estudio editorial, y los datos, los mismos del entregable.

**Arquitectura** (ADR-0031, `deliverables.md` §16, RF-REP-008, RQ-REP-034 a RQ-REP-038, ASM-143 a ASM-147):

```
versión emitida ─► modelo INTERNO ─► ReportDisclosure (nivel) ─► DeckMaterial ─► DeckPlanner (público) ─► DeckSpec
   ─► SlidesConverter ─► sgspe-slides (Remotion) ─► PDF ─► DeckChecks          ─► GET …/spec ─► sgspe-ui <Player>
```

**Tecnologías:**

- Kotlin 2.4 y Spring Boot 4.1 WebFlux sobre wasichai 1.3.0, con PDFBox;
- TypeScript con React 19, Remotion 4.0.533 (versión exacta) y pdf-lib, sobre Node LTS en Debian bookworm;
- las fuentes Source Serif 4 y Source Sans 3 (OFL).

**Decisiones del usuario del 2026-10-20:**

- la presentación sale solo de una versión emitida, sin aprobación propia;
- esta entrega trae el PDF y el navegador; el MP4 va después;
- hay un nivel PUBLICO nuevo para la comunidad;
- la organización califica para la licencia Free de Remotion.

## Restricciones globales

- **El orden.** Primero el nivel, sobre el modelo nominal; después la extracción; al final el público. Ninguna capa ve
  lo que otra quitó, y los componentes React solo pintan.
- **Los modelos congelados no cambian.** `facts` y `key` son opcionales y nulos por defecto, y una prueba dorada vigila
  los bytes de un modelo v1.
- **Nada se calcula de nuevo.** Las cifras salen de los lectores del dominio, formateadas en es-PE en el backend (el ICU
  de Chrome headless no trae es-PE).
- **Determinismo.** La especificación es una función pura del modelo y de las versiones. En la composición no se usan
  `Date`, `Math.random` ni el entorno.
- **Dentro del contenedor:** sin red de salida, de solo lectura, sin capacidades, como usuario sin privilegios, sin URL
  en la especificación y sin `licenseKey` (en la v4 intentaría enviar telemetría).
- **Dividir, no encoger.** Ninguna lámina baja del tamaño mínimo, y una que no cabe falla sin archivo parcial.

## Tareas

### 1. Diseño y spike

- [x] Spike en el VPS (2026-10-20), con Remotion 4.0.533 y Node 24:
  - `renderStill` a PDF da texto vectorial y fuentes incrustadas en subconjunto, a 1440 × 810 pt;
  - cada lámina tarda de 0,5 a 1,8 s;
  - corre con `--network none`, `--read-only` y `--cap-drop ALL`.
  
  Hallazgos: el nombre de una familia tipográfica va en una sola palabra, el desborde se mide por la caja de las
  líneas, el espaciado entre letras parte el texto extraído y las cifras se formatean en el backend.
- [x] ADR-0031, RF-REP-008, RQ-REP-034 a RQ-REP-038 y ASM-143 a ASM-147.
- [x] Documentos: `deliverables.md` §16, `api.md` §4.12, `security.md`, `architecture.md`, `glossary.md`, ADR-0026 y
  `CLAUDE.md`.

### 2. Privacidad y modelo (`sgspe.privacy`)

- [x] `Fact` y `ReportModel.facts` opcional, y `key` opcional en `Table`, `LineChart`, `ScatterChart` y `MapFigure`.
  Prueba dorada sobre los bytes de un modelo v1.
- [x] `ReportDisclosure.apply(model, PUBLICO)` como lista permitida, con `SmallCells` sobre los hechos y los promedios de
  menos de 5 casos como «reservado». `Mentions` gana su rama PUBLICO.
- [x] `PublicDisclosureTest` con marcadores ficticios hostiles.

### 3. Hechos tipados en T1 y T2

- [x] Los tipos emiten `key` y `facts` con la grilla completa de cada catálogo y `n` por promedio, y pasan a
  `typeVersion` 2.
- [x] La paridad del DOCX y el PDF y `LatexPdfTest` siguen en verde.

### 4. Público, material y planificador (`sgspe.deliverable`)

- [x] `Audience`, con el arco, la densidad, el vocabulario, el tono y los niveles; y el glosario fijo para COMUNIDAD.
- [x] `DeckMaterial`: las historias de T1 y T2 desde los `facts` y los `key` de v2; un modelo v1 no da presentación
  (ASM-146, sin despliegue todavía).
- [x] `DeckPlanner` con las métricas de las fuentes, la división, los títulos afirmativos calculables, las fuentes y
  notas, y el descarte de lo «reservado».
- [x] `DeckSpec` en JSON canónico y el acento con contraste; las especificaciones generadas van a
  `slides/fixtures/generated/`, que el paquete valida con su esquema y compone.
- [x] Pruebas por público, layout, formato, ausencia, división, determinismo (hash dorado) y las negativas.

### 5. `slides/`: biblioteca y servicio

- [x] El paquete `@wasichai/sgspe-slides`:
  - `spec`, `design` (tokens), `layouts`, `charts` (SVG) y `compositions`;
  - las fuentes vendidas con `OFL.txt` y una prueba de SHA-256.
- [x] La verificación en la composición: desborde, solapes, tamaño mínimo, fuentes, imágenes y láminas vacías.
- [x] El servidor `node:http`, con `POST /render` (JSON adentro, PDF afuera), `GET /health`, los topes, la concurrencia,
  el plazo y los errores tipados.
- [x] `slides/Dockerfile` en dos etapas, el servicio `slides` de `compose.yml` (8099) y la CI (el job `slides`, la
  integración y `develop/latex/test_server.py`).
- [x] `yarn review` para la hoja de contacto.

### 6. Ciclo de vida

- [ ] `model.json`: `deliverable_presentation`, `presentation_audience`, `presentation_status`, la relación con
  `export_log`, `roles.json`, `database-model.md`, `domain-model.md` y los catálogos de §10.
- [ ] `ExportLog` con el objeto como parámetro; las rutas nativas cerradas.
- [ ] `DeliverablePresentations`: el pedido, la lista, el estado, `content` y `spec`.
- [ ] `SlidesConverter`, `DeckChecks`, JOB-09 (`DeckGenerationJob`) y las propiedades `sgspe.deliverable.slides.*`.
- [ ] `ArchitectureTest`, las pruebas de integración con el doble y `SlidesPdfTest` (etiqueta `slides`).

### 7. Publicación del paquete

- [ ] `extra-files` de release-please con `slides/package.json`, y `npm publish` en `publish.yml`.

### 8. `sgspe-ui`

- [ ] La página de presentaciones de la versión emitida, con `<Player>`, la lista de verificación, el PDF y la prueba
  de versiones.

### 9. Demostración y revisión visual

- [ ] Un T2 sintético en `model/demo/` y `load_demo.py --issue`.
- [ ] Componer T1 para DIRECCION, CAMPO, INVESTIGACION y COMUNIDAD, y T2 para INSTITUCIONES. Revisar las hojas de
  contacto, corregir y volver a componer. Entregar las muestras.
