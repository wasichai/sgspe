# SGSPE · Fase 8: especificación de diseño

> **Para agentes:** esta especificación es la entrada de los planes 8.0 (documentos), 8.1 (modelo de entregables), 8.2a
> (indicadores y tableros consolidados), 8.2b (motor de entregables y T2), 8.2c (T1 con mapas y PDF) y 8.3 (portal). No
> define IDs: los que se nombran en §6 los agrega el plan 8.0 a `docs/requirements-analysis.md`, y hasta entonces
> `docs/` sigue siendo la fuente de verdad. Donde esta especificación y `docs/` difieren, el plan 8.0 corrige `docs/`
> según lo que aquí se decide.

**Objetivo.** Dejar diseñada de punta a punta la Fase 8 («Tableros consolidados y entregables», segunda y última de la
Release 3) sobre la maquinaria de F2 a F7: el catálogo `indicator` con sus metas, el cierre opcional de periodos, los
tableros `executive`, `project` y `monitoring`, y los entregables T2 y T1 en DOCX y PDF, con WF-H, modelo congelado con
SHA-256, niveles INTERNO y EXTERNO, plantillas Word por organización, mapas de informe por GetMap, constancia de emisión
con correlativo (AUT-12) y borradores de sección asistidos por IA (AI-04).

**Arquitectura.** `sgspe.dashboard` suma KPI-EXE, los tres tableros, el cierre de periodo y un lector explícito para que
el ensamblado de entregables llame a los mismos KPI como la plataforma (RQ-DSH-034). Un paquete nuevo,
`sgspe.deliverable`, tiene una clase por tipo que ensambla el modelo del informe como la plataforma dentro de JOB-05;
`sgspe.privacy` define ese modelo, su JSON canónico y su transformación por nivel de divulgación; Apache POI arma el
DOCX sobre la plantilla de la organización, Gotenberg lo convierte a PDF y GeoServer dibuja el cuerpo de cada mapa, al
que SGSPE agrega leyenda, escala, grilla UTM, norte y fuente. Todos los archivos viven en wasichai-files (el `FileStore`
de las evidencias, ADR-0014): el último borrador en campos `FILE` derivados de `deliverable`, lo emitido en campos
`FILE` de `deliverable_version` y las plantillas en un objeto nuevo, `deliverable_template`. La emisión no genera nada:
en la petición de quien emite copia las candidatas a sus versiones y aplica `issued` en una transacción. AI-04 redacta
sugerencias BORRADOR DE SECCION que solo una persona adopta. El portal suma los tres tableros, el cierre de periodo,
Reportes con el editor de secciones y la administración de plantillas.

**Fuentes.** Las decisiones del usuario del 2026-10-12 y 2026-10-13 (§2.1, vinculantes); `docs/roadmap.md` (F8 y
«Cobertura de entregables por fase»); `docs/deliverables.md` (entero); `docs/dashboards.md` (§4.9, §5, §8, §10, §11 y la
Tabla 9.5); `docs/domain-model.md` (§4.6, §6.15, §7, §8.3); `docs/workflows.md` (WF-H, AUT-12, JOB-05, JOB-08);
`docs/api.md` (§4.11, §4.12); `docs/security.md` (§7.8, §7.9, §7.10, §11.4, §14); `docs/gis.md` §7.3; ADR-0013,
ADR-0014, ADR-0015 (con los resultados de los spikes de F1); `docs/requirements-analysis.md` (GAP-001, 009, 010, 011,
014, 018, 022; ASM-014, 026, 027, 028, 037, 038, 042, 055, 061; RQ-DSH-034; RQ-AI-012; RF-AIK-004); la especificación de
F7 (`2026-10-12-fase-7-design.md`: `ai_suggestion` BORRADOR DE SECCION, PDFBox como dependencia de `main`, POI ya
presente); el módulo de archivos de wasichai 1.2.0 (`docs/modules/files.md`); `docs/develop/README.md`, `compose.yml`,
`build.gradle.kts`; y el código de `sgspe.dashboard`, `sgspe.privacy`, `sgspe.evidence` (`EvidenceContent`,
`EvidenceFileReadPolicy`) y `sgspe.common` (`RetentionJob`, `JobTasks`, `Idempotency`).

## Restricciones globales

- wasichai `1.2.0` exacto hasta que 8.2b suba a la versión que resuelva la UPS nueva de §2.2.2 (también exacta);
  `@wasichai/*` de la versión que ya usa sgspe-ui, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`,
  Apache POI `5.5.1` (ya en `build.gradle.kts`), PDFBox en `main` desde F7; español (es-PE) en documentos, comentarios,
  textos fijos de los tipos y `detail`, nombres técnicos en inglés (ADR-0009); valores ENUM en español MAYÚSCULAS sin
  tildes.
- Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; `scope` y `privacy` solo dependen de `common`; ningún literal de rol en `src/main/kotlin`;
  ningún paquete de dominio depende de `sgspe.dashboard`, `sgspe.deliverable` ni `sgspe.ai`.
- Nunca `asPlatform` dentro de una petición: el ensamblado y la generación corren solo en JOB-05; pedir borradores,
  emitir, descargar, cerrar periodos, consultar tableros y adoptar sugerencias corren como la persona.
- Ninguna petición llama a Gotenberg ni a GeoServer por un entregable: solo JOB-05 (el proxy WMS de F6 sigue siendo la
  única excepción, y no toca entregables).
- Nada se sirve sin la marca BORRADOR antes de emitir; la versión candidata y el modelo congelado nunca se sirven.
- Gotenberg y GeoServer nunca son públicos; Gotenberg nunca recibe una URL, una ruta ni una opción que venga del cliente
  y no descarga nada de la red.
- En ningún nivel salen el documento de identidad ni los teléfonos de una persona; EXTERNO sale solo en PDF y exige la
  lista de verificación.
- Los logs nunca llevan narrativa, contenido de un entregable ni datos personales: solo ids, códigos de aviso y el id de
  correlación.
- Un tenant que todavía no tiene el modelo de F8 sigue con F7: JOB-05 recorre `Organizations.having("deliverable")` y
  los tableros nuevos preguntan a `TenantModel` por `indicator` antes de leer metas o cifras oficiales.
- Fixtures, plantillas de prueba y textos fijos con datos ficticios (`@sgspe.test`, «Proyecto Ejemplo A», «Comunidad
  Ejemplo», «Persona Ejemplo 1», «Asociación Ejemplo») y topónimos reales (RQ-SEC-018).

## 1. Propósito y alcance de F8

| Subsistema | Alcance | Piezas |
|---|---|---|
| Indicadores | Catálogo `indicator` cargado por script con las metas de DOC-01, editable; cierre opcional de periodos en `indicator_measurement` | `model/indicators.json`, `model/load_indicators.py`, `IndicatorMeasurements` |
| Tableros consolidados | KPI-EXE-01 a 03; tableros `executive` (con la tabla por proyecto), `project` y `monitoring` (la Tabla 9.5 con meta y cifra oficial); KPI con lector explícito | `ExecutiveKpis`, `Dashboards`, `MonitoringTable`, `KpiReader` |
| Modelo del informe | JSON canónico con SHA-256, elementos marcados por sensibilidad, transformación INTERNO y EXTERNO, menciones de personas | `ReportModel`, `CanonicalJson`, `ReportDisclosure` (`sgspe.privacy`) |
| Generación | JOB-05 con candados; ensamblado por tipo; DOCX con POI y commonmark-java; PDF con Gotenberg y su verificación con PDFBox | `DeliverableGenerationJob`, `DocxWriter`, `PdfConverter`, `GotenbergConverter` |
| Plantillas | Subida validada de .dotx por organización; plantilla base de SGSPE | `deliverable_template`, `TemplateValidator`, `DeliverableTemplates` |
| Almacenamiento y descargas | Campos `FILE` en `deliverable` (borrador), `deliverable_version` (emitido) y `deliverable_template`; rutas de descarga con `export_log` | `DeliverableFiles`, `DeliverableContent` |
| Ciclo de vida | WF-H, `DeliverableWriteGuard`, emisión en una transacción, AUT-12 con constancia y correlativo, avisos de aprobación | `DeliverableIssue`, `model/workflows/deliverable.json` |
| Tipos | T2 (DOC-02) primero y T1 (DOC-01) después | `FieldReportType`, `SocialDiagnosisType` |
| Mapas de informe | GetMap con capas públicas y en línea en el UTM del proyecto; decoraciones dibujadas por SGSPE | `ReportMaps`, `GetMapRequest`, `MapComposer` |
| IA | AI-04 borradores de sección; adopción humana con motivo; `ai_assisted_sections` | agente de AI-04 en `sgspe.ai`, `DeliverableSections` |
| Portal | Tableros ejecutivo, de proyecto y de monitoreo; cierre de periodo; Reportes (S49, S50) con editor de secciones, sugerencias de IA, borradores y emisión; plantillas | sgspe-ui |

Fuera de F8: T3 a T10 (siguen probables, ASM-026); un tipo que prepare PUBLICO; reimportar o emitir un DOCX editado
fuera del sistema; volver a generar una versión emitida por la API (se prueba, no se expone); imágenes de ECharts en los
informes (RQ-REP-031 sigue con gráficos nativos); mapa base en los mapas de informe (GAP-011); PDF etiquetado para
accesibilidad (ASM-047, opcional); bloqueo de objetos WORM; un historial de borradores (solo el último, §2.2.3); aviso
al terminar un borrador (el portal consulta el estado); exportar tableros a archivo.

## 2. Decisiones

### 2.1 Las decisiones del usuario (2026-10-12 y 2026-10-13)

| # | Decisión | Consecuencia |
|---|---|---|
| 1 | **Solo T2 y T1 en F8, T2 primero.** T3 a T10 siguen «probables» (ASM-026) y quedan fuera | El catálogo «Tipo de entregable» queda con DIAGNOSTICO SOCIAL e INFORME DE CAMPO Y AVANCE; `roadmap.md` F8 deja de prometer T3 a T10 |
| 2 | **Mapas de informe por GetMap con capas en línea** (SLD en línea, `SLD_BODY`) en el UTM del proyecto; leyenda, barra de escala y grilla UTM las dibuja SGSPE con Java 2D sobre la imagen; sin mapa base | ADR-0015 se acepta para los mapas de informe con el resultado del spike (leyenda vacía para capas en línea, `scaleline` con dpi errado); `gis.md` §7.3 paso 4 cambia (§3.7) |
| 3 | **Plantillas Word por organización** (.dotx), subidas y validadas: sin macros, con los estilos requeridos y fuentes conocidas; sin plantilla, la de SGSPE; un DOCX emitido nunca se reimporta | GAP-010 se cierra; ASM-055 rige tal cual; §3.3 |
| 4 | **Niveles de divulgación:** T1 y T2 preparan INTERNO y EXTERNO; EXTERNO solo en PDF y con lista de verificación; consentimiento por foto (una foto sin consentimiento nunca va en EXTERNO); PUBLICO solo con agregados | ASM-037 deja de ser «propuesta» para T1 y T2; ningún campo ni archivo PUBLICO en F8; §3.2 |
| 5 | **Almacenamiento en wasichai-files** (el `FileStore` de las evidencias, ADR-0014): borradores en campos `FILE` `draft_*` de `deliverable` (ASM-061), versiones emitidas y plantillas en campos `FILE` de `deliverable_version` y de un registro de plantilla; las descargas reutilizan `DOWNLOAD_RESTRICTED` y `export_log` | `deliverables.md` §11 cambia; los `*_storage_key` desaparecen; la plantilla es el objeto `deliverable_template` (§2.2.4); la plataforma necesita adjuntar archivos (§2.2.2) |
| 6 | **Cierre de periodo opcional; los indicadores de postura nunca se congelan** (409 para KPI-STK-02, 05, 06 y KPI-TER-01); metas iniciales de DOC-01, editables en el catálogo `indicator` (ASM-042, valores por defecto de GAP-018) | §3.1; GAP-018 se cierra con los valores de DOC-01 |
| 7 | **Reparto en planes como F6:** 8.0 documentos (ADR-0013 y ADR-0015 aceptados), 8.1 modelo, 8.2a indicadores y tableros consolidados, 8.2b motor de entregables y T2, 8.2c T1 con mapas y PDF (Gotenberg, borradores de sección de AI-04), 8.3 portal | §7; el modelo de los indicadores viaja con 8.2a (§2.2.1) |
| 8 | **AUT-12 activada:** cada emisión genera su constancia con correlativo | Deja de ser «opcional»; tipo de documento «Constancia de emisión» sobre `deliverable` (§3.5) |
| 9 | **Release 3.0.0 al cerrar F8** (`Release-As: 3.0.0` ya está en `main` de los dos repositorios); mezclar el PR de release exige la confirmación del usuario | §7, definición de terminado |

Hechos de orden que la especificación respeta: 8.0 puede ir ya; 8.2a no depende de F7 y corre en paralelo con F7; 8.1
necesita el modelo de F7 (`ai_suggestion`) en `main`; 8.2c depende de AI-04 de F7; Gotenberg
(`gotenberg/gotenberg:8-libreoffice`) entra al compose, solo alcanzable por la app, y a la CI. Los spikes de F1 fijan:
`STYLEREF`/`SEQ` por capítulo falla en LibreOffice y las leyendas van con número literal, marcador y `PAGEREF`; fuentes
Carlito, Caladea y Liberation; PDF/A-2b; 102 páginas con 40 fotos en 8 s; la decoración `legend` falla con capas en
línea y `scaleline` usa otro dpi, así que las dibuja SGSPE.

### 2.2 Elecciones de esta especificación

1. **El modelo de los indicadores viaja con 8.2a, no con 8.1.** `indicator`, `indicator_measurement`, sus filas de
   `roles.json`, `model/indicators.json` y `load_indicators.py` son la primera tarea de 8.2a, que así corre tras 8.0 y
   en paralelo con F7; 8.1 queda solo con los objetos de entregable, que necesitan `ai_suggestion`. Consecuencia:
   `model.json` y `roles.json` cambian a la vez en 8.2a y en el plan 7.1; el segundo en mezclar rebasa.
2. **La plataforma necesita adjuntar archivos: UPS nueva (la asigna 8.0).** wasichai-files 1.2.0 dice «Only a person can
   upload: the platform (`asPlatform`) and automations cannot attach a file», y JOB-05, que escribe los `draft_*`, corre
   como la plataforma. Se pide a wasichai que `FileService` permita, dentro de `asPlatform`, subir (`stage`) y adjuntar
   un archivo en la misma escritura de la plataforma (con `created_by` vacío y `StoredFileGuard` aceptando la subida de
   esa corrida) y abrir uno (`open`) sin usuario. Consecuencias: el issue en wasichai solo se abre con la aprobación del
   usuario (`security.md` §3.4, GAP-028); 8.2b sube a la versión de wasichai que lo publique; mientras tanto, 8.2b
   avanza todo lo que no adjunta archivos. Si el usuario no aprueba la UPS, el respaldo es el diseño anterior solo para
   el borrador: JOB-05 escribe en el `FileStore` con claves propias (`draft_storage_key`) y JOB-08 gana el paso de
   borradores; versiones y plantillas no cambian, porque las escribe una persona. Hasta que la UPS llegue, JOB-05 lee
   las fotos de las evidencias con `FileStore.open("<organización>/<id del archivo>")` y comprueba su SHA-256 contra el
   descriptor.
3. **El borrador son siete campos `FILE` derivados, y solo se conserva el último.** `draft_model` (el modelo congelado),
   `draft_internal_docx`, `draft_internal_pdf`, `draft_external_pdf` (con la marca BORRADOR) y
   `draft_candidate_internal_docx`, `draft_candidate_internal_pdf`, `draft_candidate_external_pdf` (sin marca, para
   emitir). Se suman `draft_revision` (la letra) y `draft_warnings` (avisos, sin datos personales); desaparece
   `draft_storage_key`. Un borrador nuevo reemplaza los siete en una sola escritura, y `StoredFileCleanup` de wasichai
   borra los bytes reemplazados pasado `wasichai.files.cleanup.delay` (24 h); el historial conserva sus descriptores.
   Consecuencias: ASM-028 queda en N = 1 para los borradores (todo lo emitido se conserva); JOB-08 no gana un paso de
   entregables en F8; ningún tipo de F8 prepara PUBLICO, así que no hay campos PUBLICO hasta que uno lo declare.
4. **La plantilla es un objeto, `deliverable_template`, no una tabla fija.** Con un campo `FILE` reutiliza el
   almacenamiento, el SHA-256, el historial, los permisos, la limpieza y la descarga nativa de wasichai-files, sin
   migración Flyway, SQL propio ni purga; una tabla fija necesitaría las cinco cosas. Es `apiOnly` (solo la crea `POST
   /api/sgspe/deliverable-templates` después de validar) y `appendOnly` (una plantilla no se edita ni se borra: subir
   otra con la misma `template_key` la reemplaza para los borradores siguientes), de `ProjectScope.TENANT`.
   `deliverable.template_key` sigue siendo TEXT: el borrador toma la plantilla más reciente de esa clave y congela su id
   y su SHA-256. Consecuencias: el catálogo pasa a 90 objetos; una plantilla vieja no se retira, solo deja de elegirse.
5. **Lo emitido son campos `FILE` de `deliverable_version`, subidos por quien emite.** `docx` (vacío fuera de INTERNO),
   `pdf` y `model` (vacío en la versión derivada, que toma el de su base por `derived_from`) reemplazan a los
   `*_storage_key`; los `*_sha256` TEXT se conservan y `DeliverableWriteGuard` los compara con los descriptores. La
   emisión copia los bytes de las candidatas con `FileService.stage` como la persona (lo que `appendOnly` exige: el
   archivo al crear) y, en una transacción, crea las versiones, apunta `current_version` y `current_revision` y aplica
   `issued` **al final**, para que AUT-12 vea el registro completo. Una emisión que falla deja subidas sin adjuntar, que
   borra `StoredFileCleanup`.
6. **SGSPE atiende las rutas nativas de archivos de los tres objetos**, como hizo con `evidence` (el segmento literal
   gana a `{object}`): en `deliverable`, los borradores con las comprobaciones de `GET …/draft/content` y su fila de
   `export_log`, y las candidatas y `draft_model`, `404`; en `deliverable_version`, las de `GET …/versions/{n}/content`,
   y `model`, `404`; la subida y el reemplazo (`POST
   /api/objects/{deliverable|deliverable_version|deliverable_template}/files/{field}` y `…/records/{id}/files/{field}`),
   `403 FILE_WRITE_NOT_ALLOWED`. La descarga nativa de una plantilla queda como la de wasichai (READ). Consecuencia: el
   portal no puede saltarse `export_log` ni la marca de agua por la API genérica.
7. **El modelo congelado es autocontenido y lo define `sgspe.privacy`.** `ReportModel`, `CanonicalJson` y
   `ReportDisclosure` viven en `sgspe.privacy` (que sigue dependiendo solo de `common`); `sgspe.deliverable` los arma.
   Los mapas PNG van dentro del modelo en base64 con su SHA-256; las fotos van por id de evidencia y SHA-256, porque una
   evidencia es inmutable y nunca se purga. Así un solo archivo, `draft_model`, basta para volver a generar, y su
   SHA-256 de archivo es igual a `draft_model_sha256`. Consecuencia: un modelo de T1 puede pesar unos MB.
8. **Tope de archivo de 64 MB para lo que sube el servidor, 20 MB para lo que sube el navegador.**
   `wasichai.files.max-bytes` (el techo) pasa a 64 MB y los campos `FILE` de entregables declaran `maxBytes` 64 MB;
   `spring.webflux.multipart.max-disk-usage-per-part` sigue en 20 MB, así que evidencias, importaciones y plantillas
   siguen en 20 MB por HTTP. Un artefacto más grande deja el borrador FALLIDO con `FILE_TOO_LARGE`. Consecuencia: se
   rompe la regla «los tres topes iguales» de `application.yml`, y se documenta por qué.
9. **Menciones y datos personales en la narrativa.** Una mención es un token Markdown `[[person:<id>]]`,
   `[[position:<id>]]` o `[[stakeholder:<id>]]`; INTERNO lo resuelve al nombre, EXTERNO al cargo y la organización o al
   código `GI-`, y sin cargo a «una persona de <localidad>». Al adoptar una sugerencia de IA, `[PERSONA:<id>]` pasa a
   `[[person:<id>]]` y un `[PERSONA]` sin id responde `422 UNRESOLVED_PSEUDONYM`. Para la lista de EXTERNO, el
   ensamblado avisa (`POSSIBLE_PERSONAL_DATA` con la clave de la sección) donde encuentra, en narrativa o textos de
   registro, nombres de `person` del proyecto, números de 8 dígitos, teléfonos o correos; es una ayuda determinista, no
   una garantía.
10. **EXTERNO falla cerrado en fotos y mapas.** Una foto entra si `people_identifiable` es falso o `photo_consent_given`
    es verdadero; un valor vacío cuenta como «identificable sin consentimiento». La imagen de un comprobante nunca va en
    EXTERNO (van sus datos estructurados sin número ni emisor). En los mapas EXTERNO rige la divulgación por capa de las
    exportaciones geográficas de F6: sin LYR-10, LYR-11 ni LYR-15, y los predios sin titulares ni partida; la leyenda
    dice «Capas omitidas por nivel de divulgación» cuando quitó alguna.
11. **GetMap siempre por POST XML, y todas las decoraciones las dibuja SGSPE.** El `ogc:GetMap` lleva un
    `StyledLayerDescriptor` con las capas publicadas por nombre y las demás como `UserLayer` con `InlineFeature` (GML
    con `srsName="http://www.opengis.net/gml/srs/epsg.xml#<srid>"`, como exigió el spike); es el mismo SLD en línea de
    la decisión 2 sin el límite de largo de una URL. GeoServer devuelve solo el cuerpo del mapa; `MapComposer` dibuja
    marco, grilla UTM con etiquetas, leyenda, barra y escala numérica, norte de cuadrícula, CRS y fuente, con los
    colores de `model/styles/palette.json`, que el build copia a los recursos. Las geometrías en línea se simplifican a
    medio píxel y un mapa con más de 5 000 elementos falla (`MAP_TOO_MANY_FEATURES`). Consecuencia: ningún diseño de
    decoración en GeoServer.
12. **La plantilla aporta aspecto, no lógica.** La portada, el encabezado y el pie de la plantilla llevan marcadores
    fijos (`{{titulo}}`, `{{proyecto}}`, `{{preparado_para}}`, `{{suscrito_por}}`, `{{direccion_autora}}`, `{{fecha}}`,
    `{{codigo}}`, `{{revision}}`, `{{nivel}}`, `{{mapa_portada}}`), sin lenguaje de expresiones; un marcador desconocido
    rechaza la plantilla. Los estilos requeridos son una lista de `TemplateStyles`; una fuente fuera de la lista en un
    estilo o en el texto rechaza la plantilla, y una que solo figura en la tabla de fuentes es un aviso. Las leyendas de
    tablas y figuras llevan número literal y marcador, y sus listas son propias con `PAGEREF`; el índice de títulos es
    el campo TOC (spike de ADR-0013).
13. **Cada tipo fija sus parámetros y sus secciones.** T2 exige `project`, `community` y periodo; T1, `project`,
    `study`, periodo, `cutoff_date` y un AIDS aprobado; los dos, el `working_crs` del proyecto. Faltar uno al pedir un
    borrador es `422 DELIVERABLE_PARAMETERS_MISSING` con los campos. `deliverable_type` no cambia después del alta, y un
    listener crea las secciones del tipo en la transacción del alta, como quien la da de alta.
14. **La asistencia de IA no se borra, y AI-04 no lee el modelo congelado.** Una sección con `content_origin` ASISTIDO
    POR IA no vuelve a HUMANO en la misma revisión (`422 AI_ORIGIN_LOCKED`), así que `ai_assisted_sections` nunca omite
    una sección asistida. AI-04 redacta una sección leyendo como la persona, seudonimizados, los registros que el tipo
    declara para esa sección y los KPI del periodo; nunca el modelo congelado, que se leyó como la plataforma.
15. **Una cifra oficial es completa o no existe.** El cierre calcula el KPI como quien cierra y responde `409` si el KPI
    no tiene valor escalar (KPI-TER-01, KPI-EXE-03 y los que solo dan series), si lee la postura (KPI-STK-02, 05 y 06),
    si quien cierra no lo tiene disponible o si `confidentialHidden` es verdadero; el cierre de todo el tenant exige
    `READ_ALL_PROJECTS` o `ADMIN`; el periodo tiene que haber terminado. El tablero muestra la cifra oficial solo cuando
    el pedido coincide con la medición (mismo proyecto o todo el tenant, mismo periodo, sin filtros de territorio).
    `load_indicators.py` nunca sobrescribe `target_value` ni `frequency` ya cargados. Consecuencia: los KPI de QCS los
    cierra en la práctica `ADMIN`.
16. **Los KPI reciben su lector.** `KpiReader` es la persona (tableros, cierre, AI-04) o la plataforma de una
    organización (JOB-05); con la plataforma, `Readability` deja leer todo y `SpatialGate` no pide alcances, como ya
    hace con la plataforma. La tabla por proyecto del tablero `executive` calcula cada fila con las mismas funciones y
    admite hasta 25 proyectos (`sgspe.dashboards.executive.max-projects`); más es `400 TOO_MANY_RECORDS` sobre
    `project`.
17. **JOB-05 es recuperable y sus servicios están detrás de puertos.** Un pedido en EN COLA o EN PROCESO hace más de
    `sgspe.deliverable.generation.stale-after` (15 min) se vuelve a tomar (el candado por entregable evita dos a la
    vez); lo pendiente se calcula en Kotlin sobre los entregables en `draft` u `observed`. El borrador es PDF común y la
    candidata PDF/A-2b. `PdfConverter` (Gotenberg) y `ReportMaps` (GeoServer) tienen dobles para las pruebas; Gotenberg
    escucha en el loopback del VPS en el puerto 8096 (el plan 8.0 lo fija en ADR-0026) y corre como servicio del trabajo
    de integración de la CI.

## 3. Diseño por subsistema

### 3.1 Indicadores, cierres y tableros consolidados (plan 8.2a)

- **Catálogo.** `model/indicators.json` lista cada KPI de `dashboards.md` §4 con `code`, `name`, `definition`,
  `formula`, `disaggregation`, `verification_means`, `source_reference`, `frequency` y `target_value`. Metas y
  frecuencias de DOC-01 (GAP-018): KPI-ENG-02, 2 reuniones al mes; las evaluaciones de grupos de interés, mensuales; las
  revisiones de riesgo, semanales; los indicadores de la Tabla 9.5, mensuales; los demás, sin meta.
  `model/load_indicators.py` (biblioteca estándar, patrón de `load_ubigeo.py`, `--dry-run` y `--validate-only`) crea los
  que faltan y actualiza los textos de documentación, nunca `target_value` ni `frequency` de uno existente. Va después
  de `load_ubigeo.py` en el aprovisionamiento y en `provision_tenant.py` y `upgrade_tenants.py`. `IndicatorWriteGuard`
  rechaza cambiar `code`.
- **Metas.** Manda el parámetro del proyecto cuando existe (`meetings_per_month_target`); si no, `target_value`; si no,
  «sin meta». La respuesta lo dice en `targetSource` (`PROJECT`, `CATALOG`, `NONE`).
- **KPI-EXE.** KPI-EXE-01 cuenta `project` en `active` con desgloses por `stage` y `project_type`; KPI-EXE-02 cuenta
  `early_warning` activas por `severity` y estado; KPI-EXE-03 son tres series de 12 meses (KPI-ENG-01 por mes, quejas
  recibidas frente a cerradas solo de `grievance`, KPI-COM-11), sin valor escalar.
- **Tableros.** `GET /api/sgspe/dashboards/{executive|project|monitoring}` con los filtros comunes:
  - `executive` (`dashboards.md` §5.1): las tiles, KPI-INV-03 por moneda, KPI-EXE-03 y `byProject[]` (una fila por
    proyecto con las mismas cifras), con el tope de §2.2.16;
  - `project` (§5.2): exige un solo `project` (`400 PROJECT_REQUIRED`); el mapa lo arma el portal con las capas de F6;
  - `monitoring` (§5.5): las 13 filas de §4.9 en su orden, cada una con `value`, `target`, `targetSource`,
    `verificationMeans`, los desgloses que pide la Tabla 9.5 (sexo, ámbito, institución) y `official` (`{ measurement,
    closedAt, value }` o `null`); #2 y #3 dicen «no disponible» a quien no lee `stance`, también con cifra oficial.
  - `GET /api/sgspe/dashboards/indicators` informa disponibles KPI-EXE desde F8 y ubica KPI-STK-04, 05 y 07 y
    KPI-ENG-06, 07 y 08 en `monitoring`.
- **Cierre.** `POST /api/sgspe/indicators/{id}/measurements` con `{ project, attributes: { period_start_date,
  period_end_date } }` e `Idempotency-Key`: lee el `indicator` como quien llama, aplica las reglas de §2.2.15 (`409
  INDICATOR_NOT_CLOSABLE` con la razón: `STANCE`, `NO_SCALAR_VALUE`, `UNAVAILABLE`, `CONFIDENTIAL_HIDDEN`; `400
  PERIOD_NOT_ENDED`; `403` sin `READ_ALL_PROJECTS` para el tenant entero), calcula con `KpiReader` de la persona y crea
  la `indicator_measurement` con valor, numerador, denominador y el desglose principal como tabla Markdown. La unicidad
  (`indicator`, `project`, `period_start_date`) más la guarda del proyecto vacío responden `409 MEASUREMENT_EXISTS`.
- **Lector.** `KpiReader` (§2.2.16) entra en todas las funciones de `Kpis`; `DashboardDeliverableParityTest` compara las
  cifras de un ADMIN por el endpoint con las del lector de la plataforma (RQ-DSH-034).

### 3.2 Modelo del informe y divulgación (plan 8.2b)

- **`ReportModel`** (`sgspe.privacy`): metadatos (tipo y versión de la clase, código, revisión, nivel, periodo, corte,
  alcance, plantilla por id y SHA-256), secciones con bloques (título, párrafo Markdown con menciones, tabla con celdas
  combinadas, figura, par de fotos, mapa, salto de sección horizontal, nota al pie), los mapas en base64, las fotos por
  evidencia y SHA-256, `aiAssistedSections` y `warnings`. Cada elemento sensible lleva su marca: `Mention`,
  `Restricted(<campo>)` (religión, proceso electoral, razones de postura, nombres de beneficiarios),
  `Photo(identifiable, consent, kind)`, `ReceiptImage`, `HolderName`, `RegistryNumber`.
- **`CanonicalJson`:** claves ordenadas, UTF-8, sin espacios, DECIMAL como texto; su SHA-256 es `draft_model_sha256`.
- **`ReportDisclosure.apply(model, level)`** devuelve el modelo del nivel sin saber de tipos: INTERNO resuelve las
  menciones al nombre y no quita nada; EXTERNO aplica §2.2.9, §2.2.10 y `deliverables.md` §9.2 (quita las marcas
  `Restricted`, `ReceiptImage`, `HolderName` y `RegistryNumber`, cita personas por cargo o `GI-`). Ningún ensamblador
  lee documento de identidad ni teléfono: `ReportSourcesTest` lo comprueba sobre la lista de campos que declara cada
  tipo.

### 3.3 Generación: JOB-05, DOCX, plantillas y PDF (planes 8.2b y 8.2c)

**JOB-05 (`DeliverableGenerationJob`).** Sondeo cada `sgspe.deliverable.generation.poll` (5 s) en una réplica
(`ClusterLock` `sgspe.deliverable`), por `Organizations.having("deliverable")`, como la plataforma; candado por
entregable `deliverable:<id>`. Por pedido:

1. EN COLA; lee el entregable, sus secciones, el proyecto y la plantilla.
2. Ensambla con la clase del tipo (Records como la plataforma, `sgspe.territory`, `sgspe.dashboard` con `KpiReader` de
   la plataforma), copia el texto de las secciones, pide los mapas (§3.7), congela el modelo: EN PROCESO,
   `draft_generated_at` (el instante de congelar), `draft_model_sha256`, `draft_revision`.
3. Por nivel (INTERNO y EXTERNO): `ReportDisclosure` → `DocxWriter` dos veces (borrador con marca y etiqueta de letra;
   candidata sin marca con la revisión de emisión siguiente) → `PdfConverter` de cada DOCX → verificación con PDFBox
   (fuentes incrustadas de la lista, páginas, PDF/A-2b en la candidata; si no, `PDF_CHECK_FAILED`). En EXTERNO el DOCX
   no se guarda.
4. Sube y adjunta los siete archivos y escribe LISTO y `draft_warnings` en una sola escritura de la plataforma.
   Cualquier error deja FALLIDO con su código en `draft_warnings`; nunca aplica transiciones ni escribe secciones.

**`DocxWriter`** (POI sobre la plantilla, `Dispatchers.IO`): rellena los marcadores de §2.2.12 (normalizando corridas
partidas); recorre el Markdown con commonmark-java y su extensión de tablas GFM hacia los estilos de la plantilla; arma
tablas con encabezado repetido, celdas combinadas, título arriba y `Fuente: Elaboración <organización autora>` abajo;
numera leyendas por capítulo con número literal y marcador y escribe las listas de tablas y de figuras con `PAGEREF`;
pone el campo TOC de cinco niveles (solo T1); inserta fotos reducidas (lado mayor 2000 px, JPEG 85, configurables) de a
dos por fila en una tabla sin bordes, con texto alternativo; usa secciones horizontales para mapas y tablas anchas;
agrega «Página X de Y», el código, la revisión y, fuera de INTERNO, el nivel; y en el borrador la marca BORRADOR en el
encabezado de cada sección. Los gráficos son tablas nativas (5 × 5 de riesgos, cuadrante 2 × 2 de interés y poder); la
tendencia de 9.3.1.1 es un gráfico nativo de Word con XDDF de POI.

**Plantillas.** `POST /api/sgspe/deliverable-templates` (multipart `file` y `templateKey`, `name`; `MANAGE_METADATA`
sobre `deliverable`; `Idempotency-Key`): `TemplateValidator` abre el OOXML con límites de entradas y de bytes
descomprimidos y rechaza `vbaProject.bin`, tipos con macros (.dotm, .docm), objetos OLE, ActiveX, controles de
contenido, relaciones `TargetMode="External"`, marcadores desconocidos, estilos requeridos faltantes y fuentes fuera de
la lista (Carlito, Caladea, Liberation Sans, Liberation Serif); responde `422 TEMPLATE_INVALID` con `violations[]`, o
sube el archivo como quien llama y crea el `deliverable_template` → `201 { id, templateKey, sha256, warnings[] }`. `GET
/api/sgspe/deliverable-templates` lista la más reciente de cada clave (`READ` sobre `deliverable`). La base de SGSPE
(`src/main/resources/sgspe/deliverable/templates/sgspe-base.dotx`, solo fuentes libres) rige sin `template_key` y
`TemplateValidatorTest` la valida.

**PDF (plan 8.2c).** `GotenbergConverter` llama a `POST /forms/libreoffice/convert` con `WebClient`: `updateIndexes`
activo, `pdfa` PDF/A-2b en las candidatas, metadatos (título, código y revisión), el id de correlación y plazo
`sgspe.deliverable.gotenberg.timeout` (180 s); sin respuesta, FALLIDO con `PDF_CONVERTER_UNAVAILABLE`. El servicio
`gotenberg` del compose (`gotenberg/gotenberg:8-libreoffice`, que 8.2c fija en la etiqueta 8.x exacta publicada,
`127.0.0.1:${SGSPE_GOTENBERG_PORT:-8096}:3000`, sin volúmenes, sin webhooks ni descargas remotas; las banderas exactas
las fija 8.2c para esa versión) es el mismo servicio que corre en la CI; `application-prod.yml` sin valor por defecto
para `sgspe.deliverable.gotenberg.url`.

### 3.4 Almacenamiento y descargas (plan 8.2b)

| Campo `FILE` | Objeto | Contenido | Se sirve |
|---|---|---|---|
| `draft_model` | `deliverable` | Modelo congelado (JSON canónico con mapas) | Nunca (`404`) |
| `draft_internal_docx`, `draft_internal_pdf` | `deliverable` | Borrador INTERNO con marca | READ y `DOWNLOAD_RESTRICTED`; `export_log` |
| `draft_external_pdf` | `deliverable` | Borrador EXTERNO con marca | READ y `DOWNLOAD_RESTRICTED`; `export_log` |
| `draft_candidate_*` (tres) | `deliverable` | Candidatas sin marca | Nunca; de ellas solo se muestra el SHA-256 |
| `docx`, `pdf`, `model` | `deliverable_version` | Versión emitida | INTERNO: READ y `DOWNLOAD_RESTRICTED`; EXTERNO: READ y `EXPORT`; `model` nunca; `export_log` |
| `file` | `deliverable_template` | Plantilla .dotx | READ, por la ruta nativa |

- `GET /api/sgspe/deliverables/{id}/draft` → `200 { status, requestedAt, generatedAt, revision, modelSha256, pending,
  levels[]: { level, candidateSha256 }, warnings[] }`.
- `GET /api/sgspe/deliverables/{id}/draft/content?level=&format=` y `GET
  /api/sgspe/deliverables/{id}/versions/{versionNumber}/content?format=` (`DeliverableContent`): leen el registro como
  quien llama, comprueban la acción, escriben la fila de `export_log` (`export_kind` DESCARGA, con `deliverable` o
  `deliverable_version`) y transmiten el archivo con `FileService.open`, `attachment`, `nosniff`, `no-store` y `ETag`
  igual al SHA-256, como `EvidenceContent`. `format=docx` fuera de INTERNO y `format=model`, `404`; otro valor, `422`.
- `ExportLog` suma `EMISION` y `DESCARGA` a `EXPORTACION`.

### 3.5 WF-H, emisión, AUT-12 y avisos (plan 8.2b)

- `model/workflows/deliverable.json` con las 12 transiciones de `workflows.md` WF-H; `issue` es de comando
  (`CommandTransitionGuard`).
- `POST /api/sgspe/deliverables/{id}/drafts` (`GENERATE`): solo en `draft` u `observed` (si no, `409`), con los
  parámetros del tipo (§2.2.13); escribe solo `generation_requested_at` → `202`.
- **`DeliverableWriteGuard`:** hacia `in_review` (`submit_for_review`, `resubmit`) y `approved`, el borrador LISTO y
  ninguna sección con `updated_at` posterior a `draft_generated_at` (`409 SECTIONS_CHANGED` con las claves); `approve`
  además con `APPROVE`; motivo cuando `content_origin` es ASISTIDO POR IA y el cerrojo de §2.2.14; `deliverable_type`
  inmutable; en `deliverable_version`, DOCX obligatorio en INTERNO y los SHA-256 iguales a los descriptores.
- **`POST /api/sgspe/deliverables/{id}/issue`** (`ISSUE`, motivo, `Idempotency-Key`, `If-Match`; `{ draftModelSha256,
  checklistConfirmed }`): verifica `approved`, LISTO, el mismo SHA-256 (`409 MODEL_MISMATCH`), ningún mapa fallido (`409
  MAP_FAILED`), ninguna sección cambiada (`409 SECTIONS_CHANGED`) y la lista confirmada (`400 CHECKLIST_REQUIRED`, como
  las exportaciones; decisión 4 del plan 8.0); sube las candidatas como la persona; y en una transacción con
  `TransactionalOperator` crea la versión INTERNO y la EXTERNO (`derived_from`, misma `revision`, `ai_assisted_sections`
  del modelo), escribe `current_version` y `current_revision`, escribe una fila de `export_log` (EMISION) por versión
  con la confirmación en `filters_description` y aplica `issued` → `201 { deliverable, versions[] }`.
- **AUT-12** (`model/automations/deliverable.json`, `enabled: true`): `STATE_ENTERED` `issued` → `GENERATE_DOCUMENT` del
  tipo «Constancia de emisión» (`deliverable-issue-certificate`, prefijo `CE`, en
  `model/document-types/deliverable.json`), que imprime código, título, tipo, revisión, fecha de emisión, quién emitió y
  los SHA-256 de las versiones. `deliverable` y `deliverable_version` no tienen campos restringidos, así que cumple la
  regla de UPS-23 que exige `test_model.py`.
- **Avisos:** `ApprovalNoticeListener` suma WF-H `submit_for_review` y `resubmit` hacia los roles de `approve`
  (`workflows.md`).

### 3.6 T2 · Informe de campo y avance (planes 8.2b y 8.2c)

`FieldReportType` (`INFORME DE CAMPO Y AVANCE`, versión 1), sin índice, con número de página:

| `section_key` | Contenido (DOC-02, `deliverables.md` §6) | Tipo | IA |
|---|---|---|---|
| `portada` | Título con el proyecto, mes y año del fin del periodo, mapa de ubicación (§3.7) | CALCULADA | — |
| `antecedentes` | `incident` EXPULSION y RETENCION DE VEHICULO O EQUIPOS del proyecto hasta el corte; `relationship_status` de los grupos de interés; narrativa | MIXTA | Sí |
| `objetivos` | `engagement_plan.strategic_objective` y `tactical_objectives` | CALCULADA | — |
| `estrategias` | `engagement_plan.action_axes`; narrativa | MIXTA | Sí |
| `actividades` | `field_visit` y `engagement` del periodo en la comunidad, con las canceladas y su motivo | MIXTA | Sí |
| `redes` | `community_network` y `network_node` de la comunidad, con cargos | MIXTA | — |
| `relaciones` | `engagement` CONTACTO CARA A CARA con sus grupos de interés; `position` VIGENTE o PREVISTO | MIXTA | Sí |
| `apoyo` | `social_contribution` entregados en el periodo, ítems por tipo, `receipt`, `delivery` y el par de fotos (entrega y comprobante) | MIXTA | — |
| `predio` | Concesiones del proyecto y su relación con el territorio comunal y los caseríos (consultas de F6); datos registrales de la comunidad (inscrita al lado de calculada, emisor tal como figura y normalizado); sectores; mapa del predio | CALCULADA | — |
| `conclusiones`, `recomendaciones` | Narrativa en lista numerada, con vínculos a `social_risk` y `agreement` | NARRATIVA | Sí |

8.2b entrega T2 entero salvo los mapas y el PDF reales, que cubren los dobles de `ReportMaps` y `PdfConverter`; con 8.2c
(Gotenberg y GeoServer) T2 se cierra antes de empezar T1.

### 3.7 Mapas de informe (plan 8.2c)

1. **Encuadre** en el `working_crs` del proyecto (sin él, `422 DELIVERABLE_PARAMETERS_MISSING`): T2 portada, el
   territorio comunal más la ubicación del proyecto; T2 predio, el territorio comunal; T1 áreas, AIDS y AIIS aprobadas;
   T1 Anexo 1, el área de interés y las concesiones del proyecto. Margen de 10 % y la escala siguiente de 1:10 000, 1:25
   000, 1:50 000 o 1:100 000 para el marco del tipo (16 × 12 cm, o 24 × 16 cm horizontal en el Anexo 1) a 300 dpi.
2. **Capas** (`gis.md` §2.2): publicadas por nombre (`<tabla física>__<columna>` de la organización) y con su estilo
   SLD; las demás leídas con `Records` como la plataforma, transformadas por nivel (§2.2.10) y enviadas en línea. T2
   portada: LYR-05, 07, 02, 04, 01 y los ríos de LYR-08; T2 predio: LYR-05, 07, 02, 04, 01 y 06; T1 áreas: LYR-13, 02,
   10, 05 y 01; T1 Anexo 1: LYR-10, 02, 03, 04, 09, 07, 05, 14, 15 y 01. Las concesiones del proyecto van en línea con
   estilo propio.
3. **Petición:** `GetMapRequest` arma el `ogc:GetMap` de §2.2.11 con fondo blanco, PNG y la resolución de 300 dpi (8.2c
   comprueba cómo la recibe el POST XML; si no, el SLD en línea escala grosores y textos); `WebClient` con plazo
   `sgspe.deliverable.maps.timeout` (30 s) contra `wasichai.gis.geoserver.url`.
4. **Composición:** `MapComposer` (Java 2D sin pantalla, con Liberation Sans de los recursos y su licencia OFL) pone el
   cuerpo en un lienzo con marco, grilla UTM con etiquetas en el borde (formato es-PE, «9,245,000 N»), leyenda de las
   capas presentes, barra y escala numérica, norte de cuadrícula, «WGS 84 / UTM zona 17S» y `Fuente: Elaboración
   <organización autora>`. PNG con `pHYs` de 300 dpi y su SHA-256, que va al modelo.
5. **Fallas:** sin GeoServer, vencido o con más elementos que el tope, el bloque del mapa queda como aviso
   (`MAP_FAILED`), el borrador sale LISTO con el aviso y la emisión responde `409 MAP_FAILED`.

### 3.8 T1 · Diagnóstico social (plan 8.2c)

`SocialDiagnosisType` (`DIAGNOSTICO SOCIAL`, versión 1) sigue `deliverables.md` §5 fila por fila: portada, índice con
TOC y listas de tablas y de figuras, capítulos 1 a 9 y anexos 10.1 a 10.3, con una sección por fila de esa tabla (el
plan fija las `section_key`). Reglas propias:

- 4.1 y 4.2 leen las versiones `approved` de AIDS y AIIS y sus miembros; el mapa de áreas va al final del capítulo 4.
- 5.1.8 y 5.1.9: `electoral_process` y `religion` llevan la marca `Restricted` (solo INTERNO).
- 6.1 a 6.3: perfiles por cargo; las razones de postura llevan `Restricted`.
- Tablas 7.1 a 7.3: nombre con `Mention` (código `GI-` en EXTERNO).
- 9.3.1 Tabla 9.5: las 13 filas del tablero `monitoring` con el lector de la plataforma y, si el periodo está cerrado,
  la cifra oficial; 9.3.1.1 con su tendencia; 9.3.1.2 con el resumen de la matriz y los lineamientos de la campaña (la
  matriz completa sigue siendo el XLSX de F3); 9.3.2 y 9.3.2.1 con la matriz 5 × 5.
- Textos fijos del tipo (capítulos 6, 7 y 7.2, Anexo 3) en `src/main/resources/sgspe/deliverable/t1/`, sin el resto de
  otra plantilla que trae DOC-01 (CON-012) y con AIDS y AIIS canónicos (CON-001).
- Secciones con IA: 1, 4, 5, 7.1, 7.2.1 a 7.2.3, 9, 9.1 y 9.4 (las narrativas y mixtas).
- Prueba de volumen: un T1 con fixtures de unas 100 páginas y 40 fotos dentro de 180 s (suite lenta).

### 3.9 Borradores de sección con IA (AI-04, plan 8.2c)

- `POST /api/sgspe/ai/runs` con `{ useCase: "AI-04", target: { object: "deliverable_section", recordId } }`: además de
  lo de F7, exige READ sobre la sección y que el tipo la marque con IA (`422 SECTION_NOT_ASSISTED`). Fuentes: la guía de
  la sección, los registros que el tipo declara para ella leídos como la persona y los KPI del periodo con su lector;
  todo seudonimizado. Salida: `ai_suggestion` BORRADOR DE SECCION en Markdown sin HTML.
- `POST /api/sgspe/ai-suggestions/{id}/accept` con el texto revisado y el motivo escribe `content`, `content_origin`
  ASISTIDO POR IA y el vínculo `ai_suggestion` con `DeliverableSections.adopt` (`sgspe.deliverable`), en la transacción
  de WF-K hacia `accepted`, y convierte los marcadores (§2.2.9). `sgspe.ai` depende de `sgspe.deliverable`, nunca al
  revés.
- El RESUMEN EJECUTIVO de F7 se puede copiar a una sección por el mismo comando.

### 3.10 Portal (sgspe-ui, plan 8.3)

Todo sigue `docs/ux/screens.md`.

- **Dashboard:** S51 con el tablero `executive` y su tabla por proyecto; la zona de tablero de S08 con `project`; la
  pantalla «Monitoreo de la Tabla 9.5» con meta, origen de la meta, cifra viva u oficial (marcada) y el diálogo «Cerrar
  periodo» para quien tiene CREATE sobre `indicator_measurement`, con el `409` explicado.
- **Indicadores:** lista del catálogo con edición de `target_value` y `frequency` para quien tiene UPDATE.
- **S49 Reportes:** «Nuevo entregable» con tipo, código, título, periodo, corte, alcance según el tipo, organizaciones y
  plantilla.
- **S50 Entregable por secciones:** editor de sección con tiptap y su extensión de Markdown, selector de menciones
  (inserta `[[…:<id>]]`), insignia de origen, «Sugerir con IA» (AI-04) con la sugerencia al lado y «Usar» con motivo;
  panel del borrador que consulta `GET …/draft` cada 5 s mientras está pendiente, abre el PDF de cada nivel en
  `PdfDialog`, muestra avisos y el SHA-256 de cada candidata; transiciones de WF-H con motivo, y «Aprobar» con el
  `draftModelSha256` en el motivo; «Emitir» con la lista de verificación EXTERNO; versiones con sus descargas y la
  constancia.
- **Plantillas** (pantalla nueva en Administración, número que asigna 8.0): subir con la lista de violaciones o avisos;
  solo para `ADMIN`.

## 4. Cambios en el modelo y en los permisos

### 4.1 Indicadores (plan 8.2a)

- `model.json`: `indicator` (catálogo, `ProjectScope.TENANT`) e `indicator_measurement` (`apiOnly`, `appendOnly`, raíz
  con `project` opcional) con los campos de `domain-model.md` §6.15; `model/indicators.json`; `load_indicators.py` y sus
  pruebas.
- `roles.json`: las filas de `indicator` e `indicator_measurement` de `security.md` §7.8.

### 4.2 Entregables (plan 8.1)

- `deliverable`: los campos de `domain-model.md` §6.15 sin `draft_storage_key`, más los siete `FILE` derivados de §2.2.3
  (`maxBytes` 64 MB; el plan fija los tipos de contenido después de comprobar lo que detecta el sniffer de
  wasichai-files para JSON, DOCX y PDF), `draft_revision` (TEXT, derivado) y `draft_warnings` (LONG_TEXT, derivado);
  `requiresReason`; acciones `GENERATE`, `APPROVE`, `ISSUE`, `EXPORT`, `DOWNLOAD_RESTRICTED`.
- `deliverable_section`: como está, con M2O `ai_suggestion`; único (`deliverable`, `section_key`).
- `deliverable_version`: sin `*_storage_key`, con `FILE` `docx`, `pdf` y `model`.
- `deliverable_template` (nuevo, `apiOnly`, `appendOnly`, TENANT): `template_key` TEXT requerido, `name` TEXT, `file`
  FILE requerido (.dotx y .docx, 20 MB), `validation_warnings` LONG_TEXT.
- `export_log`: M2O `deliverable` y `deliverable_version` si faltan; `export_kind` EXPORTACION, EMISION o DESCARGA.
- Catálogo «Tipo de entregable» con DIAGNOSTICO SOCIAL e INFORME DE CAMPO Y AVANCE solamente.
- `model/workflows/deliverable.json` (WF-H), `model/automations/deliverable.json` (AUT-12) y
  `model/document-types/deliverable.json` (constancia).
- `roles.json`: las filas de `deliverable`, `deliverable_section`, `deliverable_version` y `export_log` de §7.8; READ
  sobre `deliverable_template` para los roles que leen `deliverable`; nadie lo crea salvo `ADMIN` (por el endpoint).
- `ProjectScope`: `deliverable` raíz, `deliverable_section` y `deliverable_version` hijos de `deliverable`,
  `deliverable_template` e `indicator` TENANT.
- `application.yml`: `sgspe.deliverable.*` (sondeo, plazos, `stale-after`, fotos, tope de elementos en línea, esquema de
  revisiones por organización como los mapas por slug), `wasichai.files.max-bytes: 64MB`.

## 5. Pruebas

| Clase | Casos propios |
|---|---|
| `IndicatorMeasurementsApiTest` | Cierre de un KPI escalar; `409` con postura, sin valor escalar, sin disponibilidad y con `confidentialHidden`; periodo abierto `400`; duplicado `409`; tenant entero sin `READ_ALL_PROJECTS` `403`; `appendOnly` |
| `ExecutiveDashboardApiTest`, `ProjectDashboardApiTest`, `MonitoringDashboardApiTest` | Tiles y series con fixtures; tope de proyectos; `PROJECT_REQUIRED`; meta del proyecto frente a la del catálogo; cifra oficial solo con filtros iguales; #2 y #3 «no disponible» sin `stance`, también cerrados |
| `DashboardDeliverableParityTest` | Mismas cifras por el endpoint (ADMIN) y por el lector de la plataforma (RQ-DSH-034) |
| `DeliverableWorkflowApiTest` | La de `workflows.md` WF-H, más `MODEL_MISMATCH`, `MAP_FAILED` y `CHECKLIST_REQUIRED` |
| `DeliverableDraftsApiTest` | Pedido fuera de `draft`/`observed` `409`; parámetros faltantes `422`; JOB-05 deja LISTO con los siete archivos; FALLIDO con su código; pedido viejo retomado; nadie escribe `draft_*` por la API genérica |
| `DeliverableIssueApiTest` | Dos versiones con la misma revisión; SHA-256 de modelo iguales a `draft_model_sha256`; `appendOnly`; `export_log` EMISION; AUT-12 crea la constancia con correlativo |
| `DeliverableDownloadsApiTest` | Las filas de «Descargas» de `security.md` §14 sobre las rutas propias y las nativas; candidata y modelo `404`; subidas por la API genérica `403` |
| `DeliverableTemplatesApiTest`, `TemplateValidatorTest` | .dotm, `vbaProject.bin`, OLE, vínculo externo, marcador desconocido, estilo faltante y fuente prohibida rechazados; aviso por la tabla de fuentes; la base de SGSPE válida |
| Unitarias del motor | `CanonicalJsonTest`, `ReportDisclosureTest` (fixtures con «Persona Ejemplo 1»: ni nombres, ni fotos sin consentimiento, ni comprobante, ni restringidos en EXTERNO), `MentionsTest`, `RevisionLabelsTest`, `CaptionNumberingTest`, `MarkdownToDocxTest`, `ReportSourcesTest`, `PersonalDataScanTest` |
| Tipos | `FieldReportTypeTest` y `SocialDiagnosisTypeTest`: ensamblado con fixtures y lectura del DOCX con POI (orden de secciones y capítulos, «Tabla 5.1, 5.2…», celdas combinadas, `Fuente:`, fotos de a dos con texto alternativo, marca en el borrador) |
| Mapas | `MapFramingTest` (escala estándar), `MapComposerTest` (grilla, barra, leyenda no vacía), `GetMapRequestTest` (SLD en línea, `srsName`, nada sensible en EXTERNO) |
| PDF (etiqueta `gotenberg`, en la CI) | Texto del PDF con las entradas del índice y su página; «BORRADOR» en cada página del borrador; PDF/A-2b en la candidata; solo fuentes permitidas; el texto del PDF coincide con el del DOCX |
| Suite lenta (`slowTest`, a pedido) | GetMap contra GeoServer real (el PNG se decodifica y la leyenda tiene píxeles); T1 de unas 100 páginas en menos de 180 s |
| IA | `SectionDraftApiTest` con FakeModel: sugerencia seudonimizada, adopción con motivo y origen ASISTIDO POR IA, `UNRESOLVED_PSEUDONYM`, `AI_ORIGIN_LOCKED`, `ai_assisted_sections` en la versión |
| Modelo y arquitectura | `test_model.py` (objetos, FILE, constancia sin restringidos), `test_load_indicators.py`; `ArchitectureTest`: `sgspe.deliverable` sin dependientes de dominio, `sgspe.privacy` solo con `common`, `WebClient` hacia Gotenberg y GeoServer solo en `sgspe.deliverable` (más el proxy WMS de F6), objetos nuevos en `ProjectScope` |

Bases: `F8IndicatorsApiTest` (sobre `F6ApiTest`, plan 8.2a) y `F8ApiTest` (sobre `F7ApiTest`, plan 8.1) con «Proyecto
Ejemplo A» en UTM 17S, «Comunidad Ejemplo» con territorio, sectores, redes y nodos, un incidente de expulsión, un aporte
entregado con fotos con y sin consentimiento y comprobante, áreas aprobadas, un estudio con instrumentos y evaluaciones,
todo ficticio.

## 6. Cambios en los documentos (plan 8.0)

**IDs nuevos en `docs/requirements-analysis.md`** (los números los asigna 8.0 después de los de F7):

| ID | Contenido |
|---|---|
| ASM nueva | Archivos de entregables en wasichai-files: borrador en siete campos `FILE` derivados, solo el último (reemplaza la parte de almacenamiento de ASM-061 y deja ASM-028 en N = 1 para borradores); versiones con `FILE`; plantillas en `deliverable_template` (decisión 5, §2.2.3 a §2.2.6) |
| ASM nueva | Mapas de informe: GetMap por POST XML con SLD en línea y decoraciones de SGSPE; divulgación por capa de F6 (decisión 2, §2.2.10, §2.2.11) |
| ASM nueva | Plantillas por organización con marcadores fijos, estilos requeridos y fuentes permitidas; base de SGSPE (decisión 3, §2.2.12) |
| ASM nueva | Cierre de periodo: cifra completa o nada, reglas de §2.2.15; metas de DOC-01 editables y no sobrescritas (decisión 6) |
| ASM nueva | Menciones `[[…:<id>]]`, conversión de marcadores de IA y aviso determinista de datos personales (§2.2.9); `ASISTIDO POR IA` no se borra (§2.2.14) |
| ASM nueva | Tope de 64 MB para archivos que sube el servidor y 20 MB por HTTP (§2.2.8) |
| UPS nueva | wasichai-files: la plataforma sube, adjunta y abre archivos dentro de `asPlatform` (§2.2.2); el issue se abre con la aprobación del usuario |

**Cambios de estado.** GAP-010 «Resuelta» (decisión 3); GAP-018 «Resuelta» con los valores de DOC-01 (decisión 6);
GAP-014 confirma el consentimiento por foto (decisión 4) y sigue abierta en retención; ASM-026, ASM-028, ASM-037,
ASM-042 y ASM-061 se ajustan a §2; ADR-0013 «Aceptado» con las leyendas de §2.2.12; ADR-0015 «Aceptado» completo con
§2.2.11; ADR-0014 con los entregables en wasichai-files y `deliverable_template`; ADR-0016 pasa a «Aceptado» (en uso
desde F2).

**Huecos y contradicciones que cierra.**

- **C1 · Almacenamiento.** `deliverables.md` §11 (claves `deliverables/<id>/…`, `templates/<key>/<sha>.dotx`), §8.4
  («copia a la carpeta de la versión»), `domain-model.md` §6.15 y §7.4 (`*_storage_key`), `api.md` §4.12 y ADR-0014
  («FileStore o puerto propio») pasan a §2.2.3 a §2.2.6.
- **C2 · JOB-08 en F8.** `workflows.md` (JOB-08, «Trabajos programados»), `domain-model.md` §8.3, `security.md` §7.10,
  `roadmap.md` F8 y ADR-0014 daban a JOB-08 un paso de borradores: lo hace `StoredFileCleanup` (§2.2.3).
- **C3 · AUT-12 opcional.** `workflows.md` y `roadmap.md` F8 pasan a activa (decisión 8).
- **C4 · PDFBox «en pruebas».** `roadmap.md` F8: dependencia de `main` desde F7.
- **C5 · Decoraciones de GeoServer.** ADR-0015 «Decisión», `gis.md` §7.3 paso 4 y `deliverables.md` §7.8: las dibuja
  SGSPE.
- **C6 · Leyendas con SEQ y STYLEREF.** `deliverables.md` §7.5 y su tabla de construcciones: número literal, marcador y
  `PAGEREF` (resultado del spike).
- **C7 · Lo que no se cierra.** `dashboards.md` §10, `api.md`, `domain-model.md` §7.2 y `security.md` §8.2 listaban solo
  postura y KPI-TER-01: suman la regla de §2.2.15.
- **C8 · T3 a T10 y PUBLICO.** `roadmap.md` F8, `deliverables.md` §3 y §14 los dejan fuera de F8 (decisión 1).
- **C9 · Orden de la emisión.** `deliverables.md` §8.4, `domain-model.md` §7.2 y `api.md`: transición al final (§2.2.5).
- **C10 · Plantilla por clave.** `domain-model.md` §6.15 y `deliverables.md` §7.6 suman `deliverable_template`; el
  catálogo de `security.md` §7.1 pasa a 90 objetos.

**Además.** `api.md` §4.11 y §4.12 con los contratos de §3 y los errores nuevos (`DELIVERABLE_PARAMETERS_MISSING`,
`SECTIONS_CHANGED`, `MODEL_MISMATCH`, `MAP_FAILED`, `CHECKLIST_REQUIRED`, `TEMPLATE_INVALID`, `FILE_WRITE_NOT_ALLOWED`,
`INDICATOR_NOT_CLOSABLE`, `PERIOD_NOT_ENDED`, `MEASUREMENT_EXISTS`, `SECTION_NOT_ASSISTED`, `UNRESOLVED_PSEUDONYM`,
`AI_ORIGIN_LOCKED`); `dashboards.md` §5 y §10; `security.md` §7.8 (fila de `deliverable_template`), §11.4 y §14;
`deliverables.md` §3, §7, §8, §9, §11, §13 y §15; `domain-model.md` §6.15 y §8.2; ADR-0026 con el puerto 8096;
`architecture.md` con Gotenberg en el compose y la CI; `docs/develop/README.md` (Gotenberg por el túnel, `slowTest`);
`ux/screens.md` (pantalla de plantillas); `roadmap.md` F8 (alcance, objetos 6, endpoints, definición de terminado de
§7); `requirements-traceability.md` y `check_traceability.py` en verde; `CLAUDE.md` («Dónde está cada cosa» con
`sgspe.deliverable`, el modelo del informe y el puerto de Gotenberg; el paso `load_indicators.py` en el orden de
aprovisionamiento).

## 7. Reparto en planes

| Plan | Rama | Depende de | Tareas principales | Estimado |
|---|---|---|---|---|
| 8.0 · Documentos | `docs/fase-8-0-documentos` | Esta especificación | §6; ADR-0013 y ADR-0015 aceptados; trazabilidad en verde; PR | ~9 tareas |
| 8.1 · Modelo de entregables | `feat/fase-8-1-modelo` | 8.0; plan 7.1 en `main` | §4.2; `design_docs.py` y `test_model.py`; base `F8ApiTest`; PR | ~8 tareas |
| 8.2a · Indicadores y tableros | `feat/fase-8-2a-tableros` | 8.0 | §4.1 (primera tarea); `KpiReader`; KPI-EXE; tableros `executive`, `project` y `monitoring`; cierre; paridad; medición; PR | ~12 tareas |
| 8.2b · Motor y T2 | `feat/fase-8-2b-motor` | 8.1, 8.2a (`KpiReader`); la UPS de §2.2.2 para adjuntar | `sgspe.privacy` (§3.2); JOB-05; `DocxWriter`; plantillas; almacenamiento y rutas; WF-H, emisión y AUT-12; T2 con dobles de mapas y PDF; subida de wasichai; PR | ~16 tareas |
| 8.2c · T1, mapas, PDF e IA | `feat/fase-8-2c-t1` | 8.2b; plan 7.2b (AI-04) en `main` | Gotenberg (compose, CI, `GotenbergConverter`, PDFBox); mapas (§3.7); cierre de T2; T1; AI-04 de sección; suite lenta; PR | ~15 tareas |
| 8.3 · Portal | `feat/fase-8-3-portal` en `sgspe-ui` | 8.0 (contrato) | §3.10 con `mockFetch`; Playwright de tableros, editor y emisión; integración contra el backend real al final; PR | ~14 tareas |

**Paralelismo.** 8.0 primero, sola. Con 8.0 en `main`: 8.2a y 8.3, en paralelo con F7. Con el plan 7.1 en `main`: 8.1.
Con 8.1 y 8.2a en `main`: 8.2b (sus tareas de adjuntar archivos esperan la versión de wasichai con la UPS). Con 8.2b y
el plan 7.2b en `main`: 8.2c. Las integraciones las corre el coordinador una tras otra contra `sgspe_sdd_test`, o la CI.

**Tareas del coordinador de la fase.** Pedir al usuario que apruebe el issue de la UPS de §2.2.2 en wasichai apenas 8.0
esté en `main` y, si lo aprueba, seguirlo hasta su release; si no, avisar a 8.2b para el respaldo de §2.2.2. Levantar
Gotenberg en el VPS (con permiso del usuario). Subir el tenant `demo` a F8 (modelo, roles, workflows,
`load_indicators.py`) y cargar una plantilla ficticia. Medir con volumen el tablero `executive` con 10 proyectos en
`sgspe_perf` (criterio: p95 bajo 3 s). Humo: un T2 y un T1 en `demo` con datos ficticios, emitidos en INTERNO y EXTERNO,
con la constancia, el PDF/A comprobado y los mapas dibujados por el GeoServer del VPS; humo del portal con el navegador.

**Definición de terminado.** La de `roadmap.md` F8 sin T3 a T10: las pruebas de §5 y las de `deliverables.md` §13.3
(lectura del DOCX, PDF con PDFBox, guardas, almacenamiento y la suite lenta de GetMap y 100 páginas);
`DeliverableWorkflowApiTest`; tableros y entregables con las mismas cifras; objetos nuevos en `ProjectScope` y
`ArchitectureTest` con las reglas de §5; el tenant `demo` en F8; la medición y el humo anotados en `roadmap.md` F8
«Estado». Al cerrar F8, la release 3.0.0 (sgspe y sgspe-ui con `Release-As: 3.0.0`), con la confirmación del usuario
antes de mezclar.

**Orden de despliegue.** Modelo y roles (`apply.py` → `apply_roles.py` → `apply.py --workflows`, que trae WF-H, AUT-12 y
la constancia), `load_ubigeo.py`, `load_indicators.py` y `publish_layers.py`; después el backend con Gotenberg y
GeoServer arriba. Un backend de F8 sin Gotenberg deja los borradores FALLIDO con `PDF_CONVERTER_UNAVAILABLE`, y sin
GeoServer, LISTO con `MAP_FAILED` y la emisión bloqueada; el resto de SGSPE funciona.

## 8. Riesgos y consecuencias aceptadas

**Consecuencias aceptadas.**

1. **Solo el último borrador** se conserva; los anteriores quedan en el historial como descriptores sin bytes.
2. **Una plantilla no se edita ni se retira:** se sube otra con la misma clave.
3. **EXTERNO pierde contenido:** fotos sin consentimiento o con el dato vacío, la imagen del comprobante, sondajes,
   plataformas, campamento y predios de la empresa en los mapas; un Anexo 1 EXTERNO no muestra los sondajes.
4. **La lista de verificación EXTERNO sigue siendo humana:** el aviso de datos personales no ve nombres que no están
   registrados como `person` ni datos escritos con otra forma (RQ-REP-033).
5. **Los KPI de QCS solo los cierra `ADMIN`** mientras ningún otro rol tenga a la vez `READ_CONFIDENTIAL` y CREATE sobre
   `indicator_measurement`; los de postura nunca.
6. **El norte del mapa es el de cuadrícula UTM**, sin convergencia ni declinación; sin mapa base (GAP-011).
7. **F8 depende de una versión nueva de wasichai** si el usuario aprueba la UPS; si no, el borrador queda fuera de
   wasichai-files.

**Riesgos.**

1. **Fidelidad de LibreOffice:** paginación distinta de Word y numeración de leyendas; mitigado por las construcciones
   acotadas, las leyendas literales con `PAGEREF`, `updateIndexes` y la prueba de texto entre DOCX y PDF. Un DOCX
   abierto en Word puede paginar distinto que su PDF.
2. **Licencias de fuentes:** solo Carlito, Caladea y Liberation (OFL); una organización con fuentes corporativas
   necesita su propia imagen de Gotenberg y una ampliación de la lista, que es un cambio de configuración y de
   despliegue.
3. **GeoServer como dependencia para emitir:** sin él no se emite ningún T1 ni T2 (los dos llevan mapas); acotado por el
   aviso y el bloqueo; la disponibilidad en producción sigue en GAP-011 y GAP-022.
4. **Tiempos y memoria de Gotenberg y de wasichai-files:** cuatro conversiones por borrador y archivos en memoria hasta
   64 MB; mitigado por fotos reducidas, un pedido a la vez, plazo de 180 s y la suite lenta de 100 páginas.
5. **Datos personales en la narrativa y en las fotos:** menciones como referencias, consentimiento por foto con falla
   cerrada, lista de verificación, `export_log` y el aviso determinista; un nombre tecleado puede pasar igual.
6. **La UPS de wasichai-files** puede tardar o no aprobarse: 8.2b avanza sin ella y tiene el respaldo de §2.2.2.
7. **Etiqueta flotante de Gotenberg** (`8-libreoffice`): un cambio de LibreOffice puede mover la paginación; 8.2c fija
   la versión publicada en el compose y en la CI, y la prueba de texto lo detecta.
8. **GetMap con elementos en línea** lleva geometrías sensibles por la red interna; GeoServer no registra cuerpos ni
   guarda en caché esas imágenes (ADR-0015).
9. **Choque de cambios del modelo** entre 8.2a y el plan 7.1 en `model.json` y `roles.json`: el segundo rebasa y corre
   `test_model.py`.
