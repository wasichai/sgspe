# SGSPE · Fase 6, plan 6.2b (servicios SIG: proxy WMS, mapa base, GDAL, exportaciones y calidad): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Escribir en `sgspe.territory` los servicios SIG de la Fase 6 que no son consultas: el mapa base por organización
(`GET /api/sgspe/map/settings`), el proxy WMS autenticado hacia GeoServer (`GET /api/sgspe/territory/wms`), la lectura con
GDAL de Shapefile, KML, KMZ, GeoPackage, FileGDB y GPX dentro de la importación de F2 (con `imports/formats`, elección de
capa y CRS detectado), los destinos nuevos `community.territory` y `environmental_feature.boundary`, las exportaciones
geográficas con divulgación por capa (`POST /api/sgspe/territory/exports`, con `GdalWriter`), la validación previa de una
geometría (`POST /api/sgspe/territory/geometry-checks`) y el listado de calidad (`GET /api/sgspe/territory/data-quality`,
sobre `SpatialGate` de 6.2a), más `gdal-bin` en la CI.

**Architecture:** Todo vive en `sgspe.territory`, que lee y escribe por `sgspe.common.Records` como quien llama y usa
`sgspe.privacy` (`Disclosure`, `ExportProjects`, `ExportLog`, `ExportFile`, `Xlsx`) para las exportaciones. Las piezas
puras llevan sus unitarias: `WmsRequestFilter` (qué parámetros WMS pasan), `GdalCommand` y `GdalInfo` (argumentos, entorno
y lectura de `ogrinfo -json`), `ImportFormat` e `ImportArchives` (formato y ZIP), `ExportDisclosure` (divulgación por
capa), `MapSettings` y `DataQualityChecks` (SQL de las cinco revisiones). Los procesos de GDAL los corre `GdalTools` con un
entorno limpio y argumentos fijos, en `Dispatchers.IO` e interrumpibles; el SQL nuevo va por `GeoSql` (que ya puede usar
`DatabaseClient`) o por `SpatialGate`, nunca por un `DatabaseClient` propio. GeoServer se llama con `WebClient` solo desde
`WmsProxy`; su lista de capas publicadas la da `GeoServerClient` de wasichai.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 1.0.0 (sin
starters nuevos; `wasichai-gis` ya está), R2DBC con PostGIS 3.6, Jackson 3 (`tools.jackson`), Apache POI 5.5.1, GDAL ≥ 3.8
(`ogrinfo`, `ogr2ogr`; 3.13.3 en la máquina de desarrollo, en `/opt/homebrew/bin`), JUnit 5 con `wasichai-test`
(`WasichaiIntegrationTest`), `com.sun.net.httpserver.HttpServer` del JDK para el GeoServer de imitación.

**Spec:** `docs/superpowers/specs/2026-10-11-fase-6-design.md` (vinculante): §2.2 (elecciones 1, 2, 6, 7, 8, 9 y 10), §3.3
(proxy WMS y mapa base; el despliegue y `publish_layers.py` son de 6.1), §3.4 (GDAL y destinos nuevos), §3.5
(exportaciones), §3.6 (validación y calidad), §5 (filas `WmsProxyApiTest`, `MapSettingsApiTest`, `MapSettingsProdTest`,
`GdalImportApiTest`, `TerritoryExportsApiTest`, `GeometryChecksApiTest`, `DataQualityApiTest` y las unitarias
`WmsRequestFilterTest`, `GdalCommandTest`, `ExportDisclosureTest`) y §7 (fila 6.2b). Contrato común de los planes de F6:
`SpatialGate`, `GateRead`, `GateTables` y `GateOutcome` (los crea 6.2a en su Tarea 1) y las ayudas de `F6ApiTest` (las crea
6.1). Lo que la especificación asigna a 6.0 (documentos), 6.1 (modelo, roles, `layers.json`, estilos, `publish_layers.py`,
`compose.yml`, `F6ApiTest`), 6.2a (consultas, KPI-TER, `ZoneCriterion`, regla de `ArchitectureTest`) y 6.3 (portal) queda
fuera.

## Global Constraints

Copiadas de la especificación (§«Restricciones globales»), más las del repositorio:

- wasichai `1.0.0` exacto, `@wasichai/*` `1.0.0`, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`,
  maplibre-gl `6.10.0`; español (es-PE) en documentos, comentarios y `detail`, nombres técnicos en inglés (ADR-0009);
  valores ENUM en español MAYÚSCULAS sin tildes.
- Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; `scope` y `privacy` solo dependen de `common`; ningún literal de rol en `src/main/kotlin`.
- Nunca `asPlatform` dentro de una petición: consultas, exportaciones e importaciones leen y escriben como quien llama.
- Todo SQL propio con valores enlazados y nombres de tabla y columna de la definición, entrecomillados; nunca escribe
  fuera de los escritores que ya existen (importación, áreas, componentes).
- GeoServer nunca es público ni recibe una petición del navegador sin pasar por el proxy; nunca publica un objeto de
  `ProjectScope` (proyecto, raíces o hijos) ni uno con campos restringidos.
- GDAL nunca recibe una ruta, una URL ni una opción que venga del cliente.
- Un tenant que todavía no tiene el modelo de F6 sigue con F5: lo que lee los campos nuevos pregunta antes a
  `TenantModel`.
- Datos de prueba ficticios (`@sgspe.test`, «Proyecto Ejemplo A», «Comunidad Ejemplo») y geometrías sintéticas
  (RQ-GIS-021).
- **Repositorio y rama.** Todo en `~/IdeaProjects/sgspe`, rama `feat/fase-6-2b-servicios` creada desde `main` con el plan
  6.1 integrado; ramas de tarea `f62b/t<k>` desde ella, integradas con `--no-ff`. La Tarea 14 empieza con un rebase sobre
  la rama de 6.2a (o sobre `main` si 6.2a ya se integró) para tomar `SpatialGate`.
- **Arquitectura.** Dentro de `sgspe.territory`, `DatabaseClient` solo en `SpatialGate` y en los escritores que ya existen
  (`TerritoryImports`, `Areas`, `ProjectComponents`, `GeoSql`): el SQL nuevo de este plan va en `GeoSql` o por
  `SpatialGate`. Ningún bean de estereotipo con el nombre simple de otro (`ArchitectureTest`). Ningún import sin uso
  (ktlint no los quita). `@ConfigurationProperties` nuevas se registran con `@EnableConfigurationProperties` (sgspe no las
  escanea).
- **Errores.** Los 400, 403, 409, 422 y 503 con código llevan el código en `errors[0].message` y el campo en
  `errors[0].field` (`Finding(...).toCodedException()`); el 422 se escribe `HttpStatus.valueOf(422)`, como `StrictQuery`.
  Códigos nuevos de la especificación: `LAYER_REQUIRED`, `FORMAT_UNAVAILABLE`, `OBJECT_NOT_EXPORTABLE`,
  `DISCLOSURE_NOT_ALLOWED`, `WMS_REQUEST_NOT_ALLOWED`, `WMS_PARAMETER_NOT_ALLOWED`, `GEOSERVER_UNAVAILABLE`; y los de las
  exportaciones que ya existen (`INVALID_DISCLOSURE_LEVEL`, `CHECKLIST_REQUIRED`, `INVALID_FILTER`, `TOO_MANY_RECORDS`,
  `NOT_ASSIGNED`). Un parámetro de consulta desconocido es `422` (`StrictQuery`); una propiedad desconocida del cuerpo,
  `400` (`StrictBody`).
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde
  `~/IdeaProjects/sgspe`; `./gradlew ktlintFormat` antes de `build`. El token nunca va a un archivo ni a la salida.
- **Integración: la corre el coordinador.** Las clases `*ApiTest` necesitan PostgreSQL. El implementador las **escribe y
  compila** (`build` compila también las de integración) y entrega la tarea marcada «integración pendiente: <clases>»;
  **nunca** corre `integrationTest`, ni Docker, ni el túnel, ni una base, ni el tenant de desarrollo. El coordinador las
  corre una tras otra contra `sgspe_sdd_test` o las deja a la CI:
  `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'`.
- **GDAL en las unitarias.** Las unitarias que llaman a GDAL (`GdalReaderTest`, `GdalWriterTest`) sí las corre el
  implementador: GDAL 3.13.3 está en `/opt/homebrew/bin`. Sin GDAL se saltan en la máquina de desarrollo y **fallan** en
  la CI (`GdalFixtures.requireGdal`), que lo instala desde la Tarea 6.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo; al final, tras una línea en
  blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas.
  `yarn format` antes de commitear yaml o json.

### Comandos

Unitarias de una clase o de un paquete (sin base):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase o paquete>'
```

Build completo (ktlint, todas las unitarias y la compilación de las de integración):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

## Review Focus

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la
tarea dueña del código:

1. **Un GPX o un GeoPackage con capas vacías.** `ogrinfo` lista siempre cinco capas en un GPX (`waypoints`, `routes`,
   `tracks`, `route_points`, `track_points`), aunque el archivo solo traiga puntos; contarlas todas pediría elegir capa en
   cada GPX. Esperado: solo cuentan las capas con elementos; un GPX de puntos se previsualiza sin `layer`. Prueba:
   `GdalReaderTest` «un GPX con solo waypoints no pide capa» (Tarea 7).
2. **Un `.prj` sin código EPSG o con un CRS fuera del catálogo.** Un Shapefile exportado de ArcGIS trae WKT de ESRI; uno
   en Web Mercator trae 3857. Esperado: `detectedCrs` es un valor del catálogo «CRS de origen» o `null`, nunca un SRID que
   la importación no admite, y la confirmación sigue exigiendo `sourceCrs`. Prueba: `GdalCommandTest` «el CRS detectado
   sale de projjson o del ID final del WKT, y fuera del catalogo es null» (Tarea 4).
3. **Parámetros WMS con otra caja o repetidos.** MapLibre manda `bbox` en minúsculas y un atacante puede mandar
   `sld_body` o `LAYERS` dos veces para que GeoServer tome el segundo. Esperado: las claves se comparan sin caja, una
   repetida es `422 WMS_PARAMETER_NOT_ALLOWED` y `sld_body` también. Prueba: `WmsRequestFilterTest` «claves sin caja, y una
   repetida o prohibida en minusculas es 422» (Tarea 2).
4. **Una exportación EXTERNO de componentes sin filtro de tipo.** Si el filtro fijo solo se aplicara al pedirlo, una
   exportación EXTERNO sin `componentType` sacaría sondajes, plataformas y campamento. Esperado: sin filtro, EXTERNO
   exporta solo `ACCESO`; pedir `SONDAJE` es `403 DISCLOSURE_NOT_ALLOWED`. Pruebas: `ExportDisclosureTest` «EXTERNO sin
   filtro de componentes deja solo ACCESO» (Tarea 10) y `TerritoryExportsApiTest` «componentes en EXTERNO sin filtro solo
   accesos, y sondajes 403» (Tarea 12).
5. **GeoServer que responde mal en vez de caerse.** Un GeoServer vivo pero mal configurado responde `200` con un
   `ServiceException` XML; pasarlo tal cual mostraría su texto (con nombres de tablas) en el navegador. Esperado: solo
   pasa una respuesta `image/png` o `image/jpeg`; cualquier otra cosa es `503 GEOSERVER_UNAVAILABLE`, igual que el caído o
   el lento. Prueba: `WmsProxyApiTest` «una respuesta que no es imagen es 503» (Tarea 3).

---

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | §3.4 pone `LIBKML` en la lista blanca y pide `OGR_KML_FOLLOW_LINKS=NO` «o equivalente del driver»; esa variable no existe en GDAL (no aparece en `libgdal` 3.13.3) y `LIBKML` puede resolver `NetworkLink` y estilos externos | Solo el driver `KML`, que no sigue enlaces; el KMZ lo descomprime SGSPE (con los controles de ZIP de F2) y GDAL lee su `doc.kml`. La red queda cortada además por `CPL_VSIL_CURL_ALLOWED_EXTENSIONS=` vacío (GDAL bloquea todo `/vsicurl/` si la variable existe y no nombra extensiones) y por el entorno vacío (sin `GDAL_HTTP_*`, sin `HOME`) (Tarea 4) |
| 2 | §3.4: «Si el archivo tiene más de una capa y no vino `layer`, `400 LAYER_REQUIRED`»; `ogrinfo` lista siempre las cinco capas de un GPX | Solo cuentan las capas con elementos (`featureCount` distinto de 0); un archivo sin ninguna es `400` sobre `file` (Tarea 7) |
| 3 | §2.2.6 y §3.4: el CRS detectado «viaja en la vista previa» y la confirmación exige `sourceCrs`; F2 exige `sourceCrs` ya en la vista previa (CON-024), así que el portal no tendría cómo conocer la propuesta antes de elegir | Para los formatos de GDAL, una vista previa sin `sourceCrs` responde `400` sobre `sourceCrs` (como F2) y el problem+json lleva además `layers: [{ name, geometryType, featureCount, detectedCrs }]`; `LAYER_REQUIRED` lleva lo mismo. La vista previa en `200` trae `format`, `layer` y `detectedCrs`. KML, KMZ y GPX fijan 4326 sin pedirlo; otro `sourceCrs` con ellos es `400` (Tareas 7 y 8) |
| 4 | §3.3: «`GetCapabilities` se arma con la lista blanca, sin consultar a GeoServer», y la lista blanca son las capas «publicadas»; saber cuáles están publicadas exige la REST de GeoServer | La lista de publicadas sale de `GeoServerClient.publishedLayerNames()` con una caché de `sgspe.territory.wms.published-ttl` (60 s); `GetCapabilities` nunca se reenvía a GeoServer. Con GeoServer caído y la caché vencida, todo el proxy (también `GetCapabilities`) es `503` (Tarea 3) |
| 5 | §5 `MapSettingsApiTest` dice «sin estilo, `null`»; §3.3 dice `{ type: 'none' }` | Manda §3.3: `{ basemap: { type: "none" } }`. La respuesta suma `attribution` (texto o `null`), porque §3.3 pide `sgspe.map.attribution` por URL y alguien tiene que leerla; es aditivo para 6.3 (Tarea 1) |
| 6 | `model/layers.json` lo crea 6.1, pero `processResources` solo empaqueta `model.json`, `roles.json` y `workflows/*.json` | La Tarea 3 suma `layers.json` al jar si 6.1 no lo hizo (`WmsLayersCatalog` lo lee del classpath, como `ModelCatalog`). Acepta un arreglo en la raíz o `{ "layers": [...] }` |
| 7 | `wasichai.gis.geoserver.*` en `application.yml` y `application-prod.yml`: §3.3 lo pide y §7 no lo asigna | La Tarea 3 lo escribe si 6.1 no lo dejó; si 6.1 ya lo escribió, no se toca. En producción, `enabled` por defecto `false` y sin URL ni credenciales por defecto (`ProdProfileTest`) |
| 8 | §3.5 «EXTERNO: … `land_parcel` de `tenure_type` PROPIEDAD, POSESION o COMUNAL», «los de EXTERNO» como campos que se quitan, y §3.5 no lista los campos de PUBLICO | `ExportDisclosure`: fuera de INTERNO se quitan `land_parcel.registry_record`, las partidas de `community` (`legal_entity_record`, `property_record`, `parent_record`, `title_reference`) y la procedencia (`source_reference`, `import_batch_id`); `project` fuera de INTERNO solo con `code` y `name` (su ubicación y su área de interés). Sin filtro, EXTERNO aplica el filtro fijo del objeto (ACCESO, o las tres tenencias) en vez de exigirlo (Tarea 10) |
| 9 | §3.5: `csv` y `xlsx` «con `easting`, `northing` (UTM)» también con `coordinates: LATLON` y para objetos del tenant sin proyecto | Con `UTM`, el `working_crs` del único proyecto de `filters.project` (obligatorio, `400` sobre `filters.project`); con `LATLON`, este y norte en la zona UTM del centroide de cada registro (ASM-064), con la columna `utm_zone`; `wkt` en el CRS elegido. `geojson` y `kml` siempre en 4326 (Tarea 12) |
| 10 | El multipart de la confirmación llega a 8 partes con `layer` y `spring.webflux.multipart.max-parts` es 8 | Se mantiene 8 (la confirmación de GDAL usa exactamente 8) y se corrige el comentario de `application.yml` (Tarea 8) |
| 11 | Las ayudas de `F6ApiTest` del contrato son `suspend fun`; las de `F2ApiTest` a `F5ApiTest` son bloqueantes y devuelven `String` | Las pruebas de este plan llaman a las de `F6ApiTest` dentro de `runBlocking { … }` (compila sea `suspend` o no) con los tipos del contrato (`UUID` para proyectos y registros) y pasan a `String` con `.toString()` donde las ayudas de F2 piden texto; si 6.1 cambió un tipo, se ajusta la llamada (Tareas 12 a 14) |
| 12 | §4.2 da `EXPORT` a `GIS_ANALYST`, pero en `roles.json` (F5) `GIS_ANALYST` no tiene CREATE sobre `export_log`, que toda exportación exige | El plan 6.1 se lo da (su decisión 2); las pruebas de exportación usan `SOCIAL_MANAGER`, que ya tenía `export_log` RC, para no depender de ese cambio, y la prueba «sin EXPORT» usa `STAKEHOLDER_MANAGER` (Tarea 12) |
| 13 | §3.6 `data-quality` pagina «las cinco revisiones» sin decir si se mezclan | `check` es obligatorio; dentro de una revisión, los objetos se recorren en un solo `UNION ALL` ordenado por objeto e id, paginado con `page` y `size` (hasta 200) y `hasNext`. Solo entran los objetos que la organización tiene y que quien llama lee con todos los campos de la revisión; un `object` pedido que no lee es `403` (Tarea 14) |

## Lo que este plan toma de los planes 6.0, 6.1 y 6.2a

- **6.0 (documentos).** `api.md` §4 con `/map/settings`, `imports/formats`, los errores nuevos y el contrato de
  exportación; `security.md` §10 con el proxy. Este plan anota en el PR lo que sus decisiones afinan (3, 5, 8, 9, 13) para
  que 6.0 o el cierre de fase lo copien a `api.md`.
- **6.1 (modelo y GeoServer).** `model.json` con `community` y `environmental_feature` con `code` (TEXT, único,
  opcional), `source_crs`, `source_reference` e `import_batch_id`; `EXPORT` sobre los objetos de §3.5 en `model.json`
  (`actions`) y en `roles.json` para `GIS_ANALYST`, `SOCIAL_MANAGER` y `PROJECT_MANAGER`; `model/layers.json` con las siete
  capas públicas, una entrada por estilo (`[{ "layer": "LYR-01", "object": "administrative_unit", "geometry": "boundary",
  "style": "sgspe-lyr-01", "label": "name" }, …]`; LYR-02 y LYR-03 comparten `locality.location`) y en el jar;
  `wasichai.gis.geoserver.*` en los dos yaml con su `ProdProfileTest`; `compose.yml` con `geoserver` y su puerto (ADR-0026); `src/test/kotlin/sgspe/F6ApiTest.kt` con
  `SyntheticGeo` (`square`, `multiSquare`, `point`, `line`, `offset`, `ORIGIN = -78.50 to -7.90`), `utmProject`,
  `locality`, `communityWithTerritory`, `parcel`, `parcelOwner`, `component`, `environmentalFeature` y
  `confidentialGrievance`.
- **6.2a (consultas).** `src/main/kotlin/sgspe/territory/SpatialGate.kt` con `GateRead(objectName, fields, principal)`,
  `GateTables` (`table`, `column`, `srid`), `GateOutcome.Rows` y `GateOutcome.Unavailable`, y
  `SpatialGate.select(reads, sql: GateTables.(bind: (Any) -> String) -> String, map: (io.r2dbc.spi.Readable) -> T)`; y la
  regla de `ArchitectureTest` sobre `DatabaseClient` en `sgspe.territory`.

## Interfaces con el portal (plan 6.3)

- `GET /api/sgspe/map/settings` → `200 { basemap: { type: "style", url } | { type: "none" }, attribution: string | null }`.
- `GET /api/sgspe/territory/imports/formats` → `200 { formats: [{ format, available, multiLayer, crsFromFile }] }` con
  `format` en `xlsx`, `csv`, `geojson`, `shp`, `kml`, `kmz`, `gpkg`, `gdb`, `gpx`.
- `POST /api/sgspe/territory/imports` y `…/{id}/confirm`: parte nueva `layer`; `target` admite además `community` y
  `environmental_feature`; `200` suma `format`, `layer` y `detectedCrs`; `400` con `errors[0] = { field: "layer",
  message: "LAYER_REQUIRED" }` y `layers[]`; `400` sobre `sourceCrs` con `layers[]` (cada una con su `detectedCrs`) para
  un formato de GDAL sin `sourceCrs`; `422 FORMAT_UNAVAILABLE` sobre `file`.
- `GET /api/sgspe/territory/wms?…` → imagen, `GetCapabilities` en XML, `401`, `403`, `422 WMS_REQUEST_NOT_ALLOWED` o
  `WMS_PARAMETER_NOT_ALLOWED`, `503 GEOSERVER_UNAVAILABLE`.
- `POST /api/sgspe/territory/exports` con `{ object, geometry?, format, coordinates, filters: { project?, componentType?,
  tenureType? }, disclosureLevel, checklistConfirmed? }` → archivo (`Content-Disposition` con
  `<objeto>-<nivel>.<ext>`; `shp` como `.zip`).
- `POST /api/sgspe/territory/geometry-checks` con `{ geometry, object, field, project? }` → `200 { valid, reason,
  typeMatches, normalized, repaired, areaHaBefore, areaHaAfter, zone: { srid, outOfZone, outOfRegion } }`.
- `GET /api/sgspe/territory/data-quality?check=&object=&project=&page=&size=` → `200 { check, content: [{ object, id,
  label, field, detail }], page, size, hasNext }`.

## Mapa de archivos

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `src/main/kotlin/sgspe/territory/MapSettings.kt` | `MapProperties`, `MapSettings` (pura), `MapSettingsController` | 1 |
| `src/main/kotlin/sgspe/territory/WmsRequestFilter.kt` | `WmsLayer`, `WmsCall`, `WmsRequestFilter`, `WmsCapabilities` (puras) | 2 |
| `src/main/kotlin/sgspe/territory/WmsProxy.kt` | `WmsProperties`, `WmsLayersCatalog`, `WmsProxy`, `WmsController` | 3 |
| `src/main/kotlin/sgspe/territory/GdalCommand.kt` | `GdalCommand`, `GdalInfo`, `SourceLayer` (puras) | 4 |
| `src/main/kotlin/sgspe/territory/GdalTools.kt` | `GdalProperties`, `GdalStatus`, `GdalTools` (procesos) | 4 |
| `src/main/kotlin/sgspe/territory/ImportFormats.kt` | `ImportFormat`, `ImportArchives` | 5 |
| `src/main/kotlin/sgspe/territory/GdalReader.kt` | `GdalReader`, `GdalSource`, `ImportChoiceRequired` | 7 |
| `src/main/kotlin/sgspe/territory/ImportController.kt`, `TerritoryImports.kt`, `ImportFiles.kt` | formatos, capa, CRS detectado, destinos nuevos | 5, 8, 9 |
| `src/main/kotlin/sgspe/common/TenantModel.kt` | `hasField` | 9 |
| `.github/workflows/ci.yml` | `gdal-bin` ≥ 3.8 en `build` e `integration` | 6 |
| `src/main/kotlin/sgspe/territory/ExportDisclosure.kt` | divulgación por capa (pura) | 10 |
| `src/main/kotlin/sgspe/territory/GdalWriter.kt` | `GeoExportFormat`, `GdalWriter` | 11 |
| `src/main/kotlin/sgspe/territory/GeoSql.kt` | `exportPoints`, `repair` | 11, 13 |
| `src/main/kotlin/sgspe/privacy/ExportFile.kt` | `create(..., entry)` | 11 |
| `src/main/kotlin/sgspe/territory/TerritoryExports.kt` | `TerritoryExports`, `TerritoryExportsController` | 12 |
| `src/main/kotlin/sgspe/territory/GeometryChecks.kt` | `GeometryChecks`, `GeometryCheckRules`, controlador | 13 |
| `src/main/kotlin/sgspe/territory/DataQuality.kt` | `QualityCheck`, `DataQualityChecks`, `DataQuality`, controlador | 14 |
| `src/main/resources/application.yml`, `application-prod.yml` | `sgspe.map.*`, `sgspe.territory.wms.*`, `sgspe.territory.gdal.*`, `wasichai.gis.geoserver.*`, límites de ZIP | 1, 3, 4, 5 |
| `src/test/kotlin/sgspe/territory/GeoServerImitation.kt`, `GdalFixtures.kt` | imitación de GeoServer y fixtures de GDAL | 3, 4 y 7 |

## Orden y paralelismo

Tareas 1, 2 → 3 (mapa base y proxy); 4 → 5 → 6 → 7 → 8 → 9 (GDAL, importación y destinos); 10 → 11 → 12 (exportaciones; 11 usa
`GdalTools` de 4); 13 (validación); 14 al final, tras el rebase sobre `SpatialGate`; 15 cierra. Las cadenas 1-3, 4-8, 10-12
y 13 son independientes entre sí (la 11 solo necesita `GdalTools` de la 4) y pueden ir en paralelo en ramas de tarea; todas se integran en
`feat/fase-6-2b-servicios` antes de la 14.

---

### Tarea 1: El mapa base por organización (`MapSettings`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/MapSettings.kt`
- Modify: `src/main/resources/application.yml` (bloque `sgspe.map`), `src/main/resources/application-prod.yml`
- Test: `src/test/kotlin/sgspe/territory/MapSettingsTest.kt` (unitaria), `src/test/kotlin/sgspe/territory/MapSettingsProdTest.kt`
  (unitaria), `src/test/kotlin/sgspe/territory/MapSettingsApiTest.kt` (integración, la corre el coordinador)

**Interfaces:**
- Consumes: `wasichai.core.organization.OrganizationService.current(): Organization` (con `slug`), `StrictQuery`.
- Produces: `MapProperties(baseStyleUrl: String?, baseStyleUrlByOrganization: Map<String, String>, attribution:
  Map<String, String>)`, `Basemap(type: String, url: String?)`, `MapSettingsResponse(basemap: Basemap, attribution:
  String?)`, `MapSettings.resolve(properties: MapProperties, slug: String?): MapSettingsResponse`; `GET
  /api/sgspe/map/settings`.

- [ ] **Paso 1: Crear la rama del plan y la de la tarea**

```bash
cd ~/IdeaProjects/sgspe && git fetch origin && git switch main && git pull --ff-only
git log --oneline -15 | grep -i "6.1\|fase-6-1" || echo "FALTA 6.1 EN MAIN: avisar al coordinador y no seguir"
test -f model/layers.json && test -f src/test/kotlin/sgspe/F6ApiTest.kt && echo "6.1 presente"
git switch -c feat/fase-6-2b-servicios && git switch -c f62b/t1
```

Esperado: «6.1 presente». Si falta, se detiene el plan.

- [ ] **Paso 2: Escribir las unitarias que fallan**

`src/test/kotlin/sgspe/territory/MapSettingsTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

// el mapa base por organización (gis.md §6.4, RQ-GIS-013, especificación de F6 §3.3): el de la organización, si no el
// general, y si no ninguno; nunca las teselas de OpenStreetMap
class MapSettingsTest {
    private val properties =
        MapProperties(
            baseStyleUrl = "https://estilos.sgspe.test/general.json",
            baseStyleUrlByOrganization = mapOf("demo" to "https://estilos.sgspe.test/demo.json"),
            attribution = mapOf("https://estilos.sgspe.test/demo.json" to "Mapa de prueba")
        )

    @Test
    fun `la organizacion con estilo propio lo recibe con su atribucion`() {
        val settings = MapSettings.resolve(properties, "demo")
        assertEquals(Basemap("style", "https://estilos.sgspe.test/demo.json"), settings.basemap)
        assertEquals("Mapa de prueba", settings.attribution)
    }

    @Test
    fun `otra organizacion recibe el general, sin atribucion configurada`() {
        val settings = MapSettings.resolve(properties, "otra")
        assertEquals(Basemap("style", "https://estilos.sgspe.test/general.json"), settings.basemap)
        assertNull(settings.attribution)
    }

    @Test
    fun `sin estilo general ni propio es none`() {
        val settings = MapSettings.resolve(MapProperties(baseStyleUrl = " "), "demo")
        assertEquals(Basemap("none"), settings.basemap)
        assertNull(settings.attribution)
    }

    // la clave de attribution es una URL: con corchetes conserva los puntos y las barras (así se escribe en el yaml)
    @Test
    fun `el yaml liga la atribucion por URL entre corchetes`() {
        val source =
            MapConfigurationPropertySource(
                mapOf(
                    "sgspe.map.base-style-url" to "https://estilos.sgspe.test/general.json",
                    "sgspe.map.base-style-url-by-organization.demo" to "https://estilos.sgspe.test/demo.json",
                    "sgspe.map.attribution[https://estilos.sgspe.test/demo.json]" to "Mapa de prueba"
                )
            )
        val bound = Binder(source).bind("sgspe.map", Bindable.of(MapProperties::class.java)).get()
        assertEquals("Mapa de prueba", bound.attribution["https://estilos.sgspe.test/demo.json"])
        assertEquals("https://estilos.sgspe.test/demo.json", bound.baseStyleUrlByOrganization["demo"])
    }
}
```

`src/test/kotlin/sgspe/territory/MapSettingsProdTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

// RQ-GIS-013 y ASM-048: producción nunca usa las teselas públicas de OpenStreetMap; el estilo lo nombra el despliegue
class MapSettingsProdTest {
    private val prod = YamlPropertySourceLoader().load("prod", ClassPathResource("application-prod.yml")).single()

    @Test
    fun `produccion no nombra tile openstreetmap org`() {
        val text = ClassPathResource("application-prod.yml").inputStream.use { String(it.readAllBytes()) }
        assertFalse(text.contains("tile.openstreetmap.org"), "application-prod.yml no debe nombrar las teselas de OpenStreetMap")
    }

    @Test
    fun `el estilo de produccion viene del despliegue y no tiene valor por defecto`() {
        assertEquals("\${SGSPE_MAP_BASE_STYLE_URL:}", prod.getProperty("sgspe.map.base-style-url")?.toString())
    }
}
```

- [ ] **Paso 3: Correrlas y ver que fallan**

Run: `./gradlew test --tests 'sgspe.territory.MapSettings*'` (con el prefijo de Gradle de §Comandos)
Expected: FAIL de compilación («Unresolved reference: MapProperties»).

- [ ] **Paso 4: Implementar `MapSettings.kt`**

```kotlin
package sgspe.territory

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.StrictQuery
import wasichai.core.organization.OrganizationService

// el mapa base (gis.md §6.4, RQ-GIS-013, ASM-048, especificación de F6 §3.3 y §2.2.9): un estilo de MapLibre por URL, el
// de la organización (por su slug, que mantiene el operador como sgspe.ai.enabled-organizations) o el general; sin ninguno,
// «none» y el portal muestra «Sin mapa base», nunca las teselas de OpenStreetMap. attribution va por URL de estilo: en el
// yaml la clave va entre corchetes ("[https://…/estilo.json]") para conservar puntos y barras
@ConfigurationProperties("sgspe.map")
data class MapProperties(
    val baseStyleUrl: String? = null,
    val baseStyleUrlByOrganization: Map<String, String> = emptyMap(),
    val attribution: Map<String, String> = emptyMap()
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MapProperties::class)
class MapConfiguration

// la forma de BasemapSpec de @wasichai/gis 1.0.0: { type: 'style', url } o { type: 'none' }
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Basemap(
    val type: String,
    val url: String? = null
)

data class MapSettingsResponse(
    val basemap: Basemap,
    val attribution: String?
)

object MapSettings {
    fun resolve(
        properties: MapProperties,
        slug: String?
    ): MapSettingsResponse {
        val url =
            slug?.let { properties.baseStyleUrlByOrganization[it] }?.trim()?.ifEmpty { null }
                ?: properties.baseStyleUrl?.trim()?.ifEmpty { null }
                ?: return MapSettingsResponse(Basemap("none"), null)
        return MapSettingsResponse(Basemap("style", url), properties.attribution[url])
    }
}

// GET /api/sgspe/map/settings (api.md §4): autenticado (lo exige la seguridad de wasichai para /api/**), sin parámetros
@RestController
@RequestMapping("/api/sgspe/map/settings")
class MapSettingsController(
    private val organizations: OrganizationService,
    private val properties: MapProperties
) {
    @GetMapping
    suspend fun settings(exchange: ServerWebExchange): MapSettingsResponse {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return MapSettings.resolve(properties, organizations.current().slug)
    }
}
```

- [ ] **Paso 5: La configuración**

En `src/main/resources/application.yml`, dentro de `sgspe:` (después de `exports:`):

```yaml
  map:
    # el mapa base (gis.md §6.4, RQ-GIS-013, MapSettings): un estilo de MapLibre por URL. en desarrollo, el de demostración de
    # MapLibre; por organización, el slug como clave; la atribución, por URL de estilo entre corchetes. nunca las teselas de
    # OpenStreetMap (su política de uso no lo admite)
    base-style-url: ${SGSPE_MAP_BASE_STYLE_URL:https://demotiles.maplibre.org/style.json}
    base-style-url-by-organization: {}
    attribution:
      '[https://demotiles.maplibre.org/style.json]': MapLibre
```

En `src/main/resources/application-prod.yml`, al final:

```yaml
sgspe:
  map:
    # el estilo lo nombra el despliegue (GAP-011); sin él, «Sin mapa base». el mapa por organización y la atribución los
    # agrega el operador en su configuración adicional (SPRING_CONFIG_ADDITIONAL_LOCATION). MapSettingsProdTest lo fija
    base-style-url: ${SGSPE_MAP_BASE_STYLE_URL:}
```

Si `application-prod.yml` ya tiene un bloque `sgspe:` (lo puede haber sumado 6.1), el `map:` va dentro de ese bloque.

- [ ] **Paso 6: Correr las unitarias**

Run: `./gradlew test --tests 'sgspe.territory.MapSettings*' --tests 'sgspe.ProdProfileTest'`
Expected: PASS.

- [ ] **Paso 7: Escribir la de integración (la corre el coordinador)**

`src/test/kotlin/sgspe/territory/MapSettingsApiTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.SgspeApiTest

// GET /api/sgspe/map/settings (especificación de F6 §3.3): la organización sembrada (slug demo) con su estilo, otra con el
// general; sin token, 401
@TestPropertySource(
    properties = [
        "sgspe.map.base-style-url=https://estilos.sgspe.test/general.json",
        "sgspe.map.base-style-url-by-organization.demo=https://estilos.sgspe.test/demo.json"
    ]
)
class MapSettingsApiTest : SgspeApiTest() {
    @Test
    fun `cada organizacion recibe su estilo o el general`() {
        val demo = tree(send("GET", "/api/sgspe/map/settings", null, HttpStatus.OK))
        assertEquals("style", demo["basemap"]["type"].asString())
        assertEquals("https://estilos.sgspe.test/demo.json", demo["basemap"]["url"].asString())
        val other = provisionTenant()
        val general = tree(send("GET", "/api/sgspe/map/settings", null, HttpStatus.OK, other.token))
        assertEquals("https://estilos.sgspe.test/general.json", general["basemap"]["url"].asString())
        assertTrue(general["attribution"].isNull)
    }

    @Test
    fun `sin token es 401 y un parametro desconocido es 422`() {
        assertEquals(HttpStatus.UNAUTHORIZED, exchange("GET", "/api/sgspe/map/settings", null, null).status)
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/map/settings?slug=otra").status)
    }
}
```

- [ ] **Paso 8: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
yarn format
git add src/main/kotlin/sgspe/territory/MapSettings.kt src/main/resources/application.yml src/main/resources/application-prod.yml \
  src/test/kotlin/sgspe/territory/MapSettingsTest.kt src/test/kotlin/sgspe/territory/MapSettingsProdTest.kt \
  src/test/kotlin/sgspe/territory/MapSettingsApiTest.kt
git commit -F - <<'MSG'
feat(territory): mapa base por organización en /api/sgspe/map/settings

MapSettings resuelve el estilo de la organización por su slug, el general o ninguno (BasemapSpec de
@wasichai/gis), con la atribución por URL. Producción sin valor por defecto ni teselas de OSM.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t1 -m "Merge f62b/t1: mapa base por organización"
```

Entrega: «integración pendiente: `MapSettingsApiTest`».

---

### Tarea 2: El filtro de peticiones WMS (`WmsRequestFilter`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/WmsRequestFilter.kt`
- Test: `src/test/kotlin/sgspe/territory/WmsRequestFilterTest.kt` (unitaria)

**Interfaces:**
- Consumes: `Finding`, `toCodedException` (`sgspe.common`).
- Produces: `WmsLayer(name: String, objectName: String, geometry: String, styles: List<String>, title: String)`; `sealed
  interface WmsCall { data object Capabilities; data class Image(request: String, layers: List<String>, styles:
  List<String>?, params: Map<String, String>) }`; `WmsRequestFilter.parse(query: Map<String, List<String>>): WmsCall`
  (todo lo sintáctico: 400, 422); `WmsRequestFilter.authorize(call: WmsCall.Image, whitelist: Map<String, WmsLayer>):
  Map<String, String>` (403 y estilos, 422); `WmsCapabilities.xml(layers: Collection<WmsLayer>, onlineResource:
  String): String`; constantes `WmsRequestFilter.MAX_SIZE = 2048`.

- [ ] **Paso 1: Rama de tarea y unitaria que falla**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t2
```

`src/test/kotlin/sgspe/territory/WmsRequestFilterTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ForbiddenException
import wasichai.core.common.WasichaiException

// security.md §10.2 y especificación de F6 §3.3: solo imágenes de capas de la lista blanca, con parámetros en lista blanca.
// los nombres de capa son ficticios
class WmsRequestFilterTest {
    private val units = WmsLayer("t_demo_unidad__limite", "administrative_unit", "boundary", listOf("sgspe-lyr-01"), "LYR-01")
    private val concessions = WmsLayer("t_demo_concesion__poligono", "mining_concession", "boundary", listOf("sgspe-lyr-07"), "LYR-07")
    private val localities = WmsLayer("t_demo_localidad__ubicacion", "locality", "location", listOf("sgspe-lyr-02", "sgspe-lyr-03"), "LYR-02, LYR-03")
    private val whitelist = listOf(units, concessions, localities).associateBy { it.name }

    private fun query(vararg pairs: Pair<String, String>): Map<String, List<String>> =
        pairs.groupBy({ it.first }, { it.second })

    private fun getMap(vararg extra: Pair<String, String>) =
        query(
            "SERVICE" to "WMS",
            "VERSION" to "1.3.0",
            "REQUEST" to "GetMap",
            "LAYERS" to units.name,
            "STYLES" to "",
            "CRS" to "EPSG:3857",
            "BBOX" to "-8740000,-890000,-8730000,-880000",
            "WIDTH" to "256",
            "HEIGHT" to "256",
            "FORMAT" to "image/png",
            "TRANSPARENT" to "TRUE",
            *extra
        )

    private fun code(e: WasichaiException): Pair<Int, String> = e.status.value() to e.violations.single().message

    @Test
    fun `un GetMap de una capa de la lista pasa con sus parametros y el estilo de layers json`() {
        val call = WmsRequestFilter.parse(getMap()) as WmsCall.Image
        val forward = WmsRequestFilter.authorize(call, whitelist)
        assertEquals(units.name, forward["LAYERS"])
        assertEquals("sgspe-lyr-01", forward["STYLES"])
        assertEquals("image/png", forward["FORMAT"])
        assertEquals("EPSG:3857", forward["CRS"])
        assertFalse(forward.keys.any { it.startsWith("SLD") || it == "CQL_FILTER" })
    }

    @Test
    fun `GetCapabilities no necesita mas parametros`() {
        assertEquals(WmsCall.Capabilities, WmsRequestFilter.parse(query("SERVICE" to "WMS", "REQUEST" to "GetCapabilities")))
    }

    @Test
    fun `GetFeatureInfo, DescribeLayer y GetFeature son WMS_REQUEST_NOT_ALLOWED`() {
        for (request in listOf("GetFeatureInfo", "DescribeLayer", "GetFeature")) {
            val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap("REQUEST" to request).mapValues { it.value.takeLast(1) }) }
            assertEquals(422 to "WMS_REQUEST_NOT_ALLOWED", code(e), request)
        }
        val wfs = assertThrows<WasichaiException> { WmsRequestFilter.parse(query("SERVICE" to "WFS", "REQUEST" to "GetCapabilities")) }
        assertEquals(422 to "WMS_REQUEST_NOT_ALLOWED", code(wfs))
    }

    @Test
    fun `SLD, SLD_BODY, CQL_FILTER, FILTER, FEATUREID, ENV y VIEWPARAMS son WMS_PARAMETER_NOT_ALLOWED`() {
        for (name in listOf("SLD", "SLD_BODY", "CQL_FILTER", "FILTER", "FEATUREID", "ENV", "VIEWPARAMS", "EXCEPTIONS")) {
            val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap(name to "x")) }
            assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(e), name)
            assertEquals(name, e.violations.single().field)
        }
    }

    @Test
    fun `claves sin caja, y una repetida o prohibida en minusculas es 422`() {
        val lower = WmsRequestFilter.parse(getMap().mapKeys { it.key.lowercase() }) as WmsCall.Image
        assertEquals(units.name, WmsRequestFilter.authorize(lower, whitelist)["LAYERS"])
        val repeated = getMap() + ("layers" to listOf(concessions.name))
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(assertThrows<WasichaiException> { WmsRequestFilter.parse(repeated) }))
        val twice = getMap().mapValues { (key, values) -> if (key == "LAYERS") values + concessions.name else values }
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(assertThrows<WasichaiException> { WmsRequestFilter.parse(twice) }))
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(assertThrows<WasichaiException> { WmsRequestFilter.parse(getMap("sld_body" to "<x/>")) }))
    }

    @Test
    fun `formato, tamano, CRS y version fuera de la lista son WMS_PARAMETER_NOT_ALLOWED`() {
        val cases = listOf("FORMAT" to "image/gif", "WIDTH" to "4096", "HEIGHT" to "0", "CRS" to "EPSG:32717", "VERSION" to "2.0.0", "TRANSPARENT" to "quizas")
        for ((name, value) in cases) {
            val bad = getMap().mapValues { (key, values) -> if (key == name) listOf(value) else values }
            val e = assertThrows<WasichaiException> { WmsRequestFilter.parse(bad) }
            assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(e), "$name=$value")
        }
        val big = getMap().mapValues { (key, values) -> if (key == "WIDTH") listOf("2048") else values }
        assertTrue(WmsRequestFilter.parse(big) is WmsCall.Image)
    }

    @Test
    fun `una capa fuera de la lista blanca es 403 y un estilo ajeno es 422`() {
        val other = getMap().mapValues { (key, values) -> if (key == "LAYERS") listOf("t_otro_tenant__limite") else values }
        assertThrows<ForbiddenException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(other) as WmsCall.Image, whitelist) }
        val style = getMap().mapValues { (key, values) -> if (key == "STYLES") listOf("estilo-ajeno") else values }
        val e = assertThrows<WasichaiException> { WmsRequestFilter.authorize(WmsRequestFilter.parse(style) as WmsCall.Image, whitelist) }
        assertEquals(422 to "WMS_PARAMETER_NOT_ALLOWED", code(e))
    }

    @Test
    fun `una capa con dos estilos acepta cualquiera de los suyos y sin estilo usa el primero`() {
        val second = getMap().mapValues { (key, values) -> if (key == "LAYERS") listOf(localities.name) else if (key == "STYLES") listOf("sgspe-lyr-03") else values }
        assertEquals("sgspe-lyr-03", WmsRequestFilter.authorize(WmsRequestFilter.parse(second) as WmsCall.Image, whitelist)["STYLES"])
        val none = getMap().mapValues { (key, values) -> if (key == "LAYERS") listOf(localities.name) else values }
        assertEquals("sgspe-lyr-02", WmsRequestFilter.authorize(WmsRequestFilter.parse(none) as WmsCall.Image, whitelist)["STYLES"])
    }

    @Test
    fun `GetLegendGraphic de una capa de la lista`() {
        val call =
            WmsRequestFilter.parse(query("SERVICE" to "WMS", "REQUEST" to "GetLegendGraphic", "LAYER" to concessions.name, "FORMAT" to "image/png"))
        val forward = WmsRequestFilter.authorize(call as WmsCall.Image, whitelist)
        assertEquals(concessions.name, forward["LAYER"])
        assertEquals("sgspe-lyr-07", forward["STYLE"])
    }

    @Test
    fun `las capacidades solo nombran la lista blanca y escapan el texto`() {
        val xml = WmsCapabilities.xml(listOf(units.copy(title = "LYR-01 <límites>"), localities), "/api/sgspe/territory/wms")
        assertTrue(xml.contains("<Name>${units.name}</Name>"))
        assertTrue(xml.contains("<Name>sgspe-lyr-03</Name>"))
        assertTrue(xml.contains("LYR-01 &lt;límites&gt;"))
        assertFalse(xml.contains(concessions.name))
    }
}
```

- [ ] **Paso 2: Correrla y ver que falla**

Run: `./gradlew test --tests 'sgspe.territory.WmsRequestFilterTest'`
Expected: FAIL de compilación («Unresolved reference: WmsLayer»).

- [ ] **Paso 3: Implementar `WmsRequestFilter.kt`**

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException

// una capa de la lista blanca de un tenant (security.md §10.2, especificación de F6 §3.3): su nombre en GeoServer
// (<tabla física>__<columna>, resuelto por la metadata de la organización), su objeto, su geometría y sus estilos de
// model/layers.json, el primero por defecto (LYR-02 y LYR-03 comparten locality.location con dos estilos)
data class WmsLayer(
    val name: String,
    val objectName: String,
    val geometry: String,
    val styles: List<String>,
    val title: String
)

sealed interface WmsCall {
    data object Capabilities : WmsCall

    // GetMap o GetLegendGraphic ya revisados en su sintaxis; las capas y los estilos se autorizan contra la lista blanca
    data class Image(
        val request: String,
        val layers: List<String>,
        val styles: List<String>?,
        val params: Map<String, String>
    ) : WmsCall
}

// qué pasa al GeoServer interno (gis.md §6.2, security.md §10.2, RQ-SEC-011): solo GetMap y GetLegendGraphic con parámetros
// en lista blanca, comparados sin caja y sin repetidos; GetCapabilities lo arma SGSPE. GetFeatureInfo, DescribeLayer, los
// estilos y filtros del cliente (SLD, SLD_BODY, CQL_FILTER, FILTER, FEATUREID, ENV, VIEWPARAMS) y todo lo demás son 422,
// porque los atributos de una capa publicada solo saldrían por ahí (especificación de F6 §2.2.2). puro: no lee nada
object WmsRequestFilter {
    const val MAX_SIZE = 2048
    const val GET_MAP = "GetMap"
    const val GET_LEGEND = "GetLegendGraphic"
    private val ALLOWED =
        setOf("SERVICE", "VERSION", "REQUEST", "LAYERS", "LAYER", "STYLES", "STYLE", "BBOX", "SRS", "CRS", "WIDTH", "HEIGHT", "FORMAT", "TRANSPARENT", "TILED")
    private val FORMATS = setOf("image/png", "image/jpeg")
    private val VERSIONS = setOf("1.0.0", "1.1.1", "1.3.0")
    private val SYSTEMS = setOf("EPSG:3857", "EPSG:4326")
    private val BOOLEANS = setOf("TRUE", "FALSE")

    fun parse(query: Map<String, List<String>>): WmsCall {
        val params = linkedMapOf<String, String>()
        for ((raw, values) in query) {
            val key = raw.uppercase()
            if (key !in ALLOWED) throw parameter(raw, "no se admite en el proxy WMS")
            if (values.size != 1 || key in params) throw parameter(raw, "va una sola vez")
            params[key] = values.single()
        }
        params["SERVICE"]?.let { if (!it.equals("WMS", ignoreCase = true)) throw request("SERVICE", "solo WMS") }
        params["VERSION"]?.let { if (it !in VERSIONS) throw parameter("VERSION", "se admiten ${VERSIONS.joinToString(", ")}") }
        val request = params["REQUEST"] ?: throw missing("REQUEST")
        return when {
            request.equals("GetCapabilities", ignoreCase = true) -> WmsCall.Capabilities
            request.equals(GET_MAP, ignoreCase = true) -> getMap(params)
            request.equals(GET_LEGEND, ignoreCase = true) -> legend(params)
            else -> throw request("REQUEST", "solo GetMap, GetLegendGraphic y GetCapabilities")
        }
    }

    private fun getMap(params: Map<String, String>): WmsCall.Image {
        val layers = (params["LAYERS"] ?: throw missing("LAYERS")).split(',').map(String::trim)
        if (layers.any { it.isEmpty() }) throw parameter("LAYERS", "una capa vacía")
        val styles = params["STYLES"]?.takeIf { it.isNotEmpty() }?.split(',')?.map(String::trim)
        if (styles != null && styles.size != layers.size) throw parameter("STYLES", "un estilo por capa")
        val system = system(params)
        bbox(params["BBOX"] ?: throw missing("BBOX"))
        val out = linkedMapOf("SERVICE" to "WMS", "VERSION" to (params["VERSION"] ?: "1.3.0"), "REQUEST" to GET_MAP)
        out[system.first] = system.second
        out["BBOX"] = params.getValue("BBOX")
        out["WIDTH"] = size(params, "WIDTH", required = true)!!
        out["HEIGHT"] = size(params, "HEIGHT", required = true)!!
        out["FORMAT"] = format(params, required = true)!!
        flag(params, "TRANSPARENT")?.let { out["TRANSPARENT"] = it }
        flag(params, "TILED")?.let { out["TILED"] = it }
        return WmsCall.Image(GET_MAP, layers, styles, out)
    }

    private fun legend(params: Map<String, String>): WmsCall.Image {
        val layer = params["LAYER"]?.trim()?.ifEmpty { null } ?: throw missing("LAYER")
        val out = linkedMapOf("SERVICE" to "WMS", "VERSION" to (params["VERSION"] ?: "1.1.1"), "REQUEST" to GET_LEGEND)
        out["FORMAT"] = format(params, required = true)!!
        size(params, "WIDTH", required = false)?.let { out["WIDTH"] = it }
        size(params, "HEIGHT", required = false)?.let { out["HEIGHT"] = it }
        flag(params, "TRANSPARENT")?.let { out["TRANSPARENT"] = it }
        return WmsCall.Image(GET_LEGEND, listOf(layer), params["STYLE"]?.trim()?.ifEmpty { null }?.let(::listOf), out)
    }

    // las capas en la lista blanca (si no, 403: otro tenant, no publicada o sin READ) y cada estilo, uno de los de la capa en
    // layers.json o vacío (el primero)
    fun authorize(
        call: WmsCall.Image,
        whitelist: Map<String, WmsLayer>
    ): Map<String, String> {
        val layers = call.layers.map { whitelist[it] ?: throw ForbiddenException("La capa pedida no está disponible para su organización o no puede leerla") }
        val styles =
            layers.mapIndexed { i, layer ->
                val asked = call.styles?.getOrNull(i)?.ifEmpty { null } ?: return@mapIndexed layer.styles.first()
                if (asked !in layer.styles) throw parameter(if (call.request == GET_MAP) "STYLES" else "STYLE", "solo los estilos de la capa")
                asked
            }
        val out = LinkedHashMap(call.params)
        if (call.request == GET_MAP) {
            out["LAYERS"] = layers.joinToString(",") { it.name }
            out["STYLES"] = styles.joinToString(",")
        } else {
            out["LAYER"] = layers.single().name
            out["STYLE"] = styles.single()
        }
        return out
    }

    private fun system(params: Map<String, String>): Pair<String, String> {
        val srs = params["SRS"]
        val crs = params["CRS"]
        if (srs != null && crs != null) throw parameter("CRS", "SRS o CRS, no los dos")
        val (key, value) = if (crs != null) "CRS" to crs else "SRS" to (srs ?: throw missing("CRS"))
        if (value.uppercase() !in SYSTEMS) throw parameter(key, "se admiten ${SYSTEMS.joinToString(", ")}")
        return key to value.uppercase()
    }

    private fun bbox(raw: String) {
        val parts = raw.split(',').map { it.trim().toDoubleOrNull() }
        if (parts.size != 4 || parts.any { it == null || !it.isFinite() } || parts[0]!! >= parts[2]!! || parts[1]!! >= parts[3]!!) {
            throw ValidationException("BBOX inválido", "BBOX", "cuatro números minX,minY,maxX,maxY con el mínimo menor que el máximo")
        }
    }

    private fun size(
        params: Map<String, String>,
        key: String,
        required: Boolean
    ): String? {
        val raw = params[key] ?: return if (required) throw missing(key) else null
        val value = raw.toIntOrNull()
        if (value == null || value < 1 || value > MAX_SIZE) throw parameter(key, "de 1 a $MAX_SIZE píxeles")
        return value.toString()
    }

    private fun format(
        params: Map<String, String>,
        required: Boolean
    ): String? {
        val raw = params["FORMAT"] ?: return if (required) throw missing("FORMAT") else null
        if (raw.lowercase() !in FORMATS) throw parameter("FORMAT", "se admiten ${FORMATS.joinToString(", ")}")
        return raw.lowercase()
    }

    private fun flag(
        params: Map<String, String>,
        key: String
    ): String? {
        val raw = params[key] ?: return null
        if (raw.uppercase() !in BOOLEANS) throw parameter(key, "TRUE o FALSE")
        return raw.uppercase()
    }

    private fun parameter(
        field: String,
        reason: String
    ): WasichaiException =
        Finding("WMS_PARAMETER_NOT_ALLOWED", "Parámetro WMS no admitido: $reason", field, status = HttpStatus.valueOf(422)).toCodedException()

    private fun request(
        field: String,
        reason: String
    ): WasichaiException = Finding("WMS_REQUEST_NOT_ALLOWED", "Petición WMS no admitida: $reason", field, status = HttpStatus.valueOf(422)).toCodedException()

    private fun missing(field: String): WasichaiException = ValidationException("Falta un parámetro WMS", field, "es obligatorio")
}

// el GetCapabilities del proxy (security.md §10.2): solo las capas de la lista blanca de quien llama, con su estilo; nunca el
// documento de GeoServer, que listaría las capas de todos los tenants. WMS 1.3.0 mínimo
object WmsCapabilities {
    fun xml(
        layers: Collection<WmsLayer>,
        onlineResource: String
    ): String =
        buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<WMS_Capabilities version="1.3.0" xmlns="http://www.opengis.net/wms" xmlns:xlink="http://www.w3.org/1999/xlink">""")
            append("<Service><Name>WMS</Name><Title>SGSPE</Title>")
            append("""<OnlineResource xlink:type="simple" xlink:href="${escape(onlineResource)}"/></Service>""")
            append("<Capability><Request>")
            append("<GetCapabilities><Format>text/xml</Format>${dcp(onlineResource)}</GetCapabilities>")
            append("<GetMap><Format>image/png</Format><Format>image/jpeg</Format>${dcp(onlineResource)}</GetMap>")
            append("</Request><Exception><Format>XML</Format></Exception>")
            append("<Layer><Title>SGSPE</Title><CRS>EPSG:3857</CRS><CRS>EPSG:4326</CRS>")
            for (layer in layers) {
                append("""<Layer queryable="0"><Name>${escape(layer.name)}</Name><Title>${escape(layer.title)}</Title>""")
                for (style in layer.styles) append("<Style><Name>${escape(style)}</Name><Title>${escape(style)}</Title></Style>")
                append("</Layer>")
            }
            append("</Layer></Capability></WMS_Capabilities>")
        }

    private fun dcp(url: String) = """<DCPType><HTTP><Get><OnlineResource xlink:type="simple" xlink:href="${escape(url)}"/></Get></HTTP></DCPType>"""

    private fun escape(text: String): String =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
```

Nota para el implementador: en la prueba de `GetFeatureInfo`, `getMap("REQUEST" to request)` agrega un segundo valor a
`REQUEST`; el `mapValues { it.value.takeLast(1) }` deja solo el último, así que la prueba mide la petición y no el
repetido.

- [ ] **Paso 4: Correr la unitaria**

Run: `./gradlew test --tests 'sgspe.territory.WmsRequestFilterTest'`
Expected: PASS (10 pruebas).

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/WmsRequestFilter.kt src/test/kotlin/sgspe/territory/WmsRequestFilterTest.kt
git commit -F - <<'MSG'
feat(territory): filtro puro de peticiones WMS y capacidades propias

Solo GetMap y GetLegendGraphic con parámetros en lista blanca, sin caja ni repetidos; GetFeatureInfo,
SLD, filtros y lo demás son 422. GetCapabilities se arma con la lista blanca del tenant.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t2 -m "Merge f62b/t2: filtro WMS"
```

---
### Tarea 3: El proxy WMS (`WmsProxy`) con el GeoServer de imitación

**Files:**
- Create: `src/main/kotlin/sgspe/territory/WmsProxy.kt`
- Modify: `build.gradle.kts` (`processResources` con `layers.json`, si 6.1 no lo hizo), `src/main/resources/application.yml`
  (`sgspe.territory.wms` y, si 6.1 no lo dejó, `wasichai.gis.geoserver.*`), `src/main/resources/application-prod.yml`,
  `src/test/kotlin/sgspe/ProdProfileTest.kt`
- Test: `src/test/kotlin/sgspe/territory/WmsLayersCatalogTest.kt` (unitaria), `src/test/kotlin/sgspe/territory/GeoServerImitation.kt`
  (ayuda), `src/test/kotlin/sgspe/territory/WmsProxyApiTest.kt` (integración, la corre el coordinador)

**Interfaces:**
- Consumes: `WmsRequestFilter.parse`, `WmsRequestFilter.authorize`, `WmsCapabilities.xml`, `WmsLayer`, `WmsCall` (Tarea 2);
  `wasichai.gis.GeoServerClient.publishedLayerNames(): Set<String>`, `wasichai.gis.GeoServerLayers.name(table,
  column)`, `wasichai.gis.GeoServerUrls.wms(base, workspace)`, `wasichai.gis.GeoServerProperties` (`enabled`, `baseUrl`,
  `workspace`); `Callers`, `Permissions.has`, `ObjectDefinitions.current`, `TenantModel.has`.
- Produces: `PublicLayer(layer, objectName, geometry, style)`, `WmsLayersCatalog.ALL: List<PublicLayer>`,
  `WmsLayersCatalog.parse(json: String): List<PublicLayer>`, `WmsProperties(timeout, maxBytes, publishedTtl)`,
  `WmsProxy.whitelist(): Map<String, WmsLayer>`, `WmsProxy.fetch(params: Map<String, String>): Pair<MediaType,
  ByteArray>`, `GET /api/sgspe/territory/wms` (`WmsController.PATH`); la ayuda de prueba `GeoServerImitation`.

- [ ] **Paso 1: Rama de tarea y lo que dejó 6.1**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t3
cat model/layers.json
grep -n 'layers.json' build.gradle.kts || echo "layers.json NO va al jar: lo suma el paso 4"
grep -n -A12 'geoserver:' src/main/resources/application.yml src/main/resources/application-prod.yml
grep -n -B2 -A12 'geoserver' compose.yml | grep -n 'ports\|127.0.0.1' 
```

Anotar el puerto de loopback que publica `compose.yml` para GeoServer (lo fijó 6.1 con ADR-0026); el paso 5 lo usa en lugar
de `8095` si es otro.

- [ ] **Paso 2: La unitaria del catálogo que falla**

`src/test/kotlin/sgspe/territory/WmsLayersCatalogTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// model/layers.json (plan 6.1) en el jar: las capas públicas que el proxy puede servir (especificación de F6 §2.2.1)
class WmsLayersCatalogTest {
    @Test
    fun `lee un arreglo o un objeto con layers`() {
        val entry = """{"layer":"LYR-01","object":"administrative_unit","geometry":"boundary","style":"sgspe-lyr-01"}"""
        val expected = listOf(PublicLayer("LYR-01", "administrative_unit", "boundary", "sgspe-lyr-01"))
        assertEquals(expected, WmsLayersCatalog.parse("[$entry]"))
        assertEquals(expected, WmsLayersCatalog.parse("""{"layers":[$entry]}"""))
    }

    // solo maestros del tenant sin campos restringidos (§2.2.1); test_model.py lo prueba contra roles.json, aquí basta con
    // que el jar traiga las siete capas públicas y ningún objeto de proyecto
    @Test
    fun `el jar trae las capas publicas y ninguna de proyecto`() {
        val all = WmsLayersCatalog.ALL
        assertEquals(setOf("LYR-01", "LYR-02", "LYR-03", "LYR-04", "LYR-05", "LYR-07", "LYR-16"), all.map { it.layer }.toSet())
        val tenant = setOf("administrative_unit", "locality", "road_access", "community", "mining_concession", "school", "health_facility")
        assertTrue(all.all { it.objectName in tenant }, all.toString())
        assertTrue(all.all { it.style.isNotBlank() && it.geometry.isNotBlank() }, all.toString())
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.WmsLayersCatalogTest'`
Expected: FAIL de compilación («Unresolved reference: PublicLayer»).

- [ ] **Paso 3: Implementar `WmsProxy.kt`**

```kotlin
package sgspe.territory

import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withTimeoutOrNull
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientException
import org.springframework.web.reactive.function.client.awaitBody
import org.springframework.web.reactive.function.client.awaitExchangeOrNull
import org.springframework.web.util.UriComponentsBuilder
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.TenantModel
import sgspe.common.toCodedException
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.Actions
import wasichai.core.common.WasichaiException
import wasichai.gis.GeoServerClient
import wasichai.gis.GeoServerException
import wasichai.gis.GeoServerLayers
import wasichai.gis.GeoServerProperties
import wasichai.gis.GeoServerUrls
import java.time.Duration

// una capa pública de model/layers.json (plan 6.1, especificación de F6 §3.3): la capa LYR, su objeto, su geometría y su
// estilo SLD
data class PublicLayer(
    val layer: String,
    val objectName: String,
    val geometry: String,
    val style: String
)

// model/layers.json viaja en el jar como el modelo (processResources de build.gradle.kts): es la misma lista que publica
// publish_layers.py, así que el proxy nunca sirve una capa que no está en ella
object WmsLayersCatalog {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    val ALL: List<PublicLayer> by lazy {
        val text =
            WmsLayersCatalog::class.java.getResourceAsStream("/model/layers.json")?.use { String(it.readAllBytes()) }
                ?: error("falta model/layers.json en el jar")
        parse(text)
    }

    fun parse(json: String): List<PublicLayer> {
        val root = JSON.readTree(json)
        val list = if (root.isArray) root else root.path("layers")
        return list.values().map {
            PublicLayer(it.path("layer").asString(), it.path("object").asString(), it.path("geometry").asString(), it.path("style").asString())
        }
    }
}

// el proxy (sgspe.territory.wms): plazo de GeoServer, tope de una imagen en memoria y cuánto vale la lista de capas
// publicadas que da la REST de GeoServer (no se pide en cada tesela)
@ConfigurationProperties("sgspe.territory.wms")
data class WmsProperties(
    val timeout: Duration = Duration.ofSeconds(5),
    val maxBytes: Int = 16 * 1024 * 1024,
    val publishedTtl: Duration = Duration.ofSeconds(60)
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WmsProperties::class)
class WmsConfiguration

// el proxy WMS autenticado (gis.md §6.2, security.md §10.2, RQ-SEC-011, RQ-GIS-012, especificación de F6 §3.3): la lista
// blanca de quien llama son las capas de layers.json que su organización tiene, que GeoServer tiene publicadas con el nombre
// <tabla física>__<columna> de esa organización y sobre cuyo objeto tiene READ. GeoServer se llama solo aquí, con WebClient y
// un plazo corto: caído, lento o con una respuesta que no es imagen (un ServiceException llega como XML con 200), 503
// GEOSERVER_UNAVAILABLE, y el portal sigue con las capas por API (WAS-ADR-0006). nunca se copian cabeceras de GeoServer
@Service
class WmsProxy(
    private val callers: Callers,
    private val permissions: Permissions,
    private val definitions: ObjectDefinitions,
    private val tenantModel: TenantModel,
    private val geoserver: GeoServerClient,
    private val geoserverProperties: GeoServerProperties,
    private val properties: WmsProperties
) {
    // un WebClient propio: Boot 4 no da un WebClient.Builder como bean (como GeoServerClient de wasichai)
    private val client: WebClient =
        WebClient
            .builder()
            .codecs { it.defaultCodecs().maxInMemorySize(properties.maxBytes) }
            .build()

    private class Published(
        val names: Set<String>,
        val at: Long
    )

    @Volatile
    private var published: Published? = null

    suspend fun whitelist(): Map<String, WmsLayer> {
        val user = callers.require()
        val names = publishedNames()
        val out = linkedMapOf<String, WmsLayer>()
        // una capa de GeoServer por geometría de objeto; varias entradas de layers.json (LYR-02 y LYR-03) le dan sus estilos
        for ((key, entries) in WmsLayersCatalog.ALL.groupBy { it.objectName to it.geometry }) {
            val (objectName, geometry) = key
            if (!tenantModel.has(user.organizationId, objectName)) continue
            if (!permissions.has(user, Actions.READ, objectName)) continue
            val definition = definitions.current(user.organizationId, objectName)
            val field = definition.fields.firstOrNull { it.name == geometry } ?: continue
            val name = GeoServerLayers.name(definition.obj.physicalTable, field.columnName)
            if (name !in names) continue
            out[name] = WmsLayer(name, objectName, geometry, entries.map { it.style }, "${entries.joinToString(", ") { it.layer }} · ${definition.obj.label}")
        }
        return out
    }

    // la imagen de GeoServer y su tipo; null de cualquier lado es 503
    suspend fun fetch(params: Map<String, String>): Pair<MediaType, ByteArray> {
        if (!geoserverProperties.enabled) throw unavailable()
        val builder = UriComponentsBuilder.fromUriString(GeoServerUrls.wms(geoserverProperties.baseUrl, geoserverProperties.workspace))
        params.forEach { (key, value) -> builder.queryParam(key, value) }
        val uri = builder.encode().build().toUri()
        val result =
            try {
                withTimeoutOrNull(properties.timeout.toMillis()) {
                    client.get().uri(uri).awaitExchangeOrNull { response ->
                        val type = response.headers().contentType().orElse(null)
                        if (response.statusCode().is2xxSuccessful && type != null && IMAGES.any { it.isCompatibleWith(type) }) {
                            type to response.awaitBody<ByteArray>()
                        } else {
                            response.releaseBody().awaitSingleOrNull()
                            null
                        }
                    }
                }
            } catch (_: WebClientException) {
                null
            }
        return result ?: throw unavailable()
    }

    private suspend fun publishedNames(): Set<String> {
        if (!geoserverProperties.enabled) throw unavailable()
        val now = System.nanoTime()
        published?.let { if (now - it.at < properties.publishedTtl.toNanos()) return it.names }
        val names =
            try {
                withTimeoutOrNull(properties.timeout.toMillis()) { geoserver.publishedLayerNames() }
            } catch (_: GeoServerException) {
                // GeoServerClient vuelve 502 toda falla de transporte o de estado: para el mapa es el mismo «no disponible»
                null
            } ?: throw unavailable()
        published = Published(names, now)
        return names
    }

    private fun unavailable(): WasichaiException =
        Finding(
            "GEOSERVER_UNAVAILABLE",
            "GeoServer no responde: el mapa sigue con las capas por la API",
            "wms",
            status = HttpStatus.SERVICE_UNAVAILABLE
        ).toCodedException()

    companion object {
        private val IMAGES = listOf(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG)
    }
}

// GET /api/sgspe/territory/wms (api.md §4): la sintaxis primero (401 lo da la seguridad de wasichai; 400 y 422 sin tocar
// GeoServer), después la lista blanca (503 si no se sabe qué está publicado), las capas y los estilos (403, 422) y la imagen
@RestController
@RequestMapping(WmsController.PATH)
class WmsController(
    private val proxy: WmsProxy
) {
    @GetMapping
    suspend fun wms(request: ServerHttpRequest): ResponseEntity<ByteArray> {
        val call = WmsRequestFilter.parse(request.queryParams)
        val whitelist = proxy.whitelist()
        return when (call) {
            WmsCall.Capabilities ->
                ResponseEntity
                    .ok()
                    .contentType(CAPABILITIES)
                    .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                    .body(WmsCapabilities.xml(whitelist.values, PATH).toByteArray())
            is WmsCall.Image -> {
                val (type, bytes) = proxy.fetch(WmsRequestFilter.authorize(call, whitelist))
                ResponseEntity
                    .ok()
                    .contentType(type)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                    .body(bytes)
            }
        }
    }

    companion object {
        const val PATH = "/api/sgspe/territory/wms"
        private val CAPABILITIES = MediaType.parseMediaType("text/xml;charset=UTF-8")
    }
}
```

- [ ] **Paso 4: `layers.json` en el jar (si 6.1 no lo hizo)**

En `build.gradle.kts`, en `tasks.named<ProcessResources>("processResources")`:

```kotlin
    from("model") {
        include("model.json", "roles.json", "layers.json", "workflows/*.json")
        into("model")
    }
```

Run: `./gradlew test --tests 'sgspe.territory.WmsLayersCatalogTest'`
Expected: PASS.

- [ ] **Paso 5: La configuración de GeoServer y del proxy**

En `application.yml`, si 6.1 no dejó `wasichai.gis.geoserver.*`, el bloque `gis.geoserver` existente pasa a (con el puerto
anotado en el paso 1 en lugar de `8095` si es otro):

```yaml
  gis:
    geoserver:
      # GeoServer de compose.yml (F6, ADR-0015), solo en loopback del VPS y por el túnel. apagado, el proxy WMS responde 503
      # y /api/gis/layers lista las capas con published=false. el datastore lo usa GeoServer por la red del compose
      enabled: ${WASICHAI_GIS_GEOSERVER_ENABLED:false}
      url: ${WASICHAI_GIS_GEOSERVER_URL:http://localhost:8095/geoserver}
      username: ${WASICHAI_GIS_GEOSERVER_USERNAME:admin}
      password: ${WASICHAI_GIS_GEOSERVER_PASSWORD:geoserver}
      workspace: ${WASICHAI_GIS_GEOSERVER_WORKSPACE:sgspe}
      datastore:
        name: sgspe-postgis
        host: postgis
        port: 5432
        database: ${WASICHAI_DB_NAME:sgspe}
        username: ${WASICHAI_GIS_GEOSERVER_DB_USERNAME:sgspe}
        password: ${WASICHAI_GIS_GEOSERVER_DB_PASSWORD:sgspe}
```

Y dentro de `sgspe.territory` (después de `layers:`):

```yaml
    wms:
      # el proxy WMS (WmsProxy, security.md §10.2): plazo de GeoServer, tope de una imagen en memoria (bytes) y cuánto vale la
      # lista de capas publicadas que da la REST de GeoServer
      timeout: 5s
      max-bytes: 16777216
      published-ttl: 60s
```

En `application-prod.yml`, dentro de `wasichai:` (si 6.1 no lo dejó):

```yaml
  gis:
    geoserver:
      # GeoServer lo enciende el despliegue (ADR-0015); sin URL ni credenciales de desarrollo (ProdProfileTest)
      enabled: ${WASICHAI_GIS_GEOSERVER_ENABLED:false}
      url: ${WASICHAI_GIS_GEOSERVER_URL:}
      username: ${WASICHAI_GIS_GEOSERVER_USERNAME:}
      password: ${WASICHAI_GIS_GEOSERVER_PASSWORD:}
      workspace: ${WASICHAI_GIS_GEOSERVER_WORKSPACE:sgspe}
      datastore:
        name: ${WASICHAI_GIS_GEOSERVER_DATASTORE:sgspe-postgis}
        host: ${WASICHAI_GIS_GEOSERVER_DB_HOST:}
        port: ${WASICHAI_GIS_GEOSERVER_DB_PORT:5432}
        database: ${WASICHAI_GIS_GEOSERVER_DB_NAME:}
        username: ${WASICHAI_GIS_GEOSERVER_DB_USERNAME:}
        password: ${WASICHAI_GIS_GEOSERVER_DB_PASSWORD:}
```

En `src/test/kotlin/sgspe/ProdProfileTest.kt`, una prueba más (junto a las de S3):

```kotlin
    // F6 (especificación §3.3): GeoServer apagado salvo que el despliegue lo encienda, sin URL ni credenciales de desarrollo
    @Test
    fun `GeoServer sin valores de desarrollo`() {
        assertEquals("\${WASICHAI_GIS_GEOSERVER_ENABLED:false}", value("wasichai.gis.geoserver.enabled"))
        assertEquals("\${WASICHAI_GIS_GEOSERVER_URL:}", value("wasichai.gis.geoserver.url"))
        assertEquals("\${WASICHAI_GIS_GEOSERVER_PASSWORD:}", value("wasichai.gis.geoserver.password"))
        assertEquals("\${WASICHAI_GIS_GEOSERVER_DB_PASSWORD:}", value("wasichai.gis.geoserver.datastore.password"))
    }
```

Si 6.1 ya escribió estos bloques con otros nombres de variable, se ajusta la prueba a los suyos y no se duplica nada.

Run: `./gradlew test --tests 'sgspe.ProdProfileTest' --tests 'sgspe.ProdStartupTest'`
Expected: PASS.

- [ ] **Paso 6: El GeoServer de imitación**

`src/test/kotlin/sgspe/territory/GeoServerImitation.kt`:

```kotlin
package sgspe.territory

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

// un GeoServer de imitación para WmsProxyApiTest con el HttpServer del JDK (ninguna dependencia nueva, ninguna red): responde
// la REST que usa GeoServerClient de wasichai (el datastore y la lista de featuretypes) y el WMS del workspace con un PNG de
// 1×1. `published` son los nombres que dice tener publicados; `down` responde 503 a todo; `delayMillis` demora cada respuesta
// (el plazo del proxy); `wmsContentType` cambia el tipo del WMS (un ServiceException real llega como XML con 200). cada
// respuesta lleva SECRET_HEADER, que el proxy nunca debe copiar
class GeoServerImitation private constructor(
    private val server: HttpServer
) : AutoCloseable {
    val url: String get() = "http://127.0.0.1:${server.address.port}/geoserver"
    val published: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val wmsQueries: MutableList<String> = CopyOnWriteArrayList()

    @Volatile
    var down = false

    @Volatile
    var delayMillis = 0L

    @Volatile
    var wmsContentType = "image/png"

    fun reset() {
        published.clear()
        wmsQueries.clear()
        down = false
        delayMillis = 0
        wmsContentType = "image/png"
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val path = exchange.requestURI.path
            when {
                down -> respond(exchange, 503, "text/plain", "caído".toByteArray())
                path.endsWith("/featuretypes") -> respond(exchange, 200, "application/json", featureTypes().toByteArray())
                path.contains("/rest/workspaces/") && path.contains("/datastores/") -> respond(exchange, 200, "application/json", "{}".toByteArray())
                path.endsWith("/wms") -> {
                    wmsQueries += exchange.requestURI.rawQuery.orEmpty()
                    val body = if (wmsContentType.startsWith("image/")) PNG else "<ServiceException>tabla t_secreta</ServiceException>".toByteArray()
                    respond(exchange, 200, wmsContentType, body)
                }
                else -> respond(exchange, 404, "text/plain", "no existe".toByteArray())
            }
        } finally {
            exchange.close()
        }
    }

    // GeoServer responde {"featureTypes":""} cuando el datastore no tiene nada
    private fun featureTypes(): String =
        if (published.isEmpty()) {
            """{"featureTypes":""}"""
        } else {
            published.joinToString(",", """{"featureTypes":{"featureType":[""", "]}}") { """{"name":"$it"}""" }
        }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        type: String,
        body: ByteArray
    ) {
        exchange.responseHeaders.add("Content-Type", type)
        exchange.responseHeaders.add(SECRET_HEADER, "no-debe-salir")
        exchange.sendResponseHeaders(status, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    override fun close() = server.stop(0)

    companion object {
        const val SECRET_HEADER = "X-Imitacion-GeoServer"
        val PNG: ByteArray = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=")

        fun start(): GeoServerImitation {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            val imitation = GeoServerImitation(server)
            server.createContext("/geoserver") { imitation.handle(it) }
            server.executor = Executors.newCachedThreadPool()
            server.start()
            return imitation
        }
    }
}
```

- [ ] **Paso 7: La prueba de integración del proxy (la corre el coordinador)**

`src/test/kotlin/sgspe/territory/WmsProxyApiTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import sgspe.DomainApiTest

// gis.md §9 «Proxy WMS» y especificación de F6 §5 (RQ-SEC-011): con un GeoServer de imitación. los nombres de las capas del
// tenant salen de /api/gis/layers (la metadata de la organización); el de otro tenant es ficticio
class WmsProxyApiTest : DomainApiTest() {
    companion object {
        private val geoserver = GeoServerImitation.start()

        @JvmStatic
        @DynamicPropertySource
        fun geoserverProperties(registry: DynamicPropertyRegistry) {
            registry.add("wasichai.gis.geoserver.enabled") { "true" }
            registry.add("wasichai.gis.geoserver.url") { geoserver.url }
            registry.add("wasichai.gis.geoserver.workspace") { "sgspe" }
            registry.add("sgspe.territory.wms.timeout") { "1s" }
            registry.add("sgspe.territory.wms.published-ttl") { "0s" }
        }

        @JvmStatic
        @AfterAll
        fun stopGeoServer() = geoserver.close()

        private const val OTHER_TENANT = "t_otro_tenant_unidad__limite"
    }

    private data class Wms(
        val status: HttpStatus,
        val body: ByteArray,
        val headers: HttpHeaders
    ) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    @BeforeEach
    fun resetGeoServer() = geoserver.reset()

    private fun wms(
        query: String,
        token: String? = this.token
    ): Wms {
        val result =
            client
                .get()
                .uri("/api/sgspe/territory/wms?$query")
                .headers { h -> if (token != null) h.set(HttpHeaders.AUTHORIZATION, token) }
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return Wms(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun code(response: Wms): String = tree(response.text)["errors"][0]["message"].asString()

    private fun layerName(
        objectName: String,
        geometry: String
    ): String =
        tree(send("GET", "/api/gis/layers", null, HttpStatus.OK))
            .values()
            .first { it["objectName"].asString() == objectName && it["geometryName"].asString() == geometry }["layerName"]
            .asString()

    private fun getMap(
        layer: String,
        extra: String = ""
    ) = "SERVICE=WMS&VERSION=1.3.0&REQUEST=GetMap&LAYERS=$layer&STYLES=&CRS=EPSG:3857" +
        "&BBOX=-8740000,-890000,-8730000,-880000&WIDTH=256&HEIGHT=256&FORMAT=image/png&TRANSPARENT=TRUE$extra"

    private fun styleOf(objectName: String) = WmsLayersCatalog.ALL.first { it.objectName == objectName }.style

    @Test
    fun `sin token es 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, wms(getMap("t_x__y"), token = null).status)
    }

    @Test
    fun `una capa publica publicada pasa como imagen, sin cabeceras de GeoServer y con su estilo`() {
        val units = layerName("administrative_unit", "boundary")
        geoserver.published += units
        val response = wms(getMap(units))
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertEquals(MediaType.IMAGE_PNG, response.headers.contentType)
        assertEquals("private, max-age=300", response.headers.cacheControl)
        assertNull(response.headers.getFirst(GeoServerImitation.SECRET_HEADER))
        assertArrayEquals(GeoServerImitation.PNG, response.body)
        val forwarded = geoserver.wmsQueries.single()
        assertTrue(forwarded.contains("LAYERS=$units"), forwarded)
        assertTrue(forwarded.contains("STYLES=${styleOf("administrative_unit")}"), forwarded)
    }

    @Test
    fun `una capa de otro tenant o no publicada es 403`() {
        geoserver.published += OTHER_TENANT
        assertEquals(HttpStatus.FORBIDDEN, wms(getMap(OTHER_TENANT)).status)
        // en layers.json pero no publicada en GeoServer
        val localities = layerName("locality", "location")
        assertEquals(HttpStatus.FORBIDDEN, wms(getMap(localities)).status)
        assertTrue(geoserver.wmsQueries.isEmpty())
    }

    @Test
    fun `sin READ sobre el objeto de la capa es 403`() {
        val concessions = layerName("mining_concession", "boundary")
        geoserver.published += concessions
        assertEquals(HttpStatus.OK, wms(getMap(concessions)).status)
        // FIELD_AGENT no tiene READ sobre mining_concession (roles.json)
        val agent = userWithRole("FIELD_AGENT")
        assertEquals(HttpStatus.FORBIDDEN, wms(getMap(concessions), agent.token).status)
    }

    @Test
    fun `SLD_BODY, CQL_FILTER y GetFeatureInfo son 422 sin tocar GeoServer`() {
        geoserver.down = true
        val sld = wms(getMap("t_x__y", "&SLD_BODY=x"))
        assertEquals(HttpStatus.valueOf(422), sld.status, sld.text)
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(sld))
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(wms(getMap("t_x__y", "&CQL_FILTER=INCLUDE"))))
        val info = wms("SERVICE=WMS&VERSION=1.3.0&REQUEST=GetFeatureInfo&LAYERS=t_x__y")
        assertEquals(HttpStatus.valueOf(422), info.status)
        assertEquals("WMS_REQUEST_NOT_ALLOWED", code(info))
    }

    @Test
    fun `formato y tamano fuera de la lista son 422`() {
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(wms(getMap("t_x__y").replace("image/png", "image/gif"))))
        assertEquals("WMS_PARAMETER_NOT_ALLOWED", code(wms(getMap("t_x__y").replace("WIDTH=256", "WIDTH=4096"))))
    }

    @Test
    fun `GetCapabilities lista solo la lista blanca y no se reenvia`() {
        val units = layerName("administrative_unit", "boundary")
        val localities = layerName("locality", "location")
        geoserver.published += setOf(units, OTHER_TENANT)
        val response = wms("SERVICE=WMS&REQUEST=GetCapabilities")
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertTrue(response.text.contains("<Name>$units</Name>"), response.text)
        assertFalse(response.text.contains(OTHER_TENANT))
        assertFalse(response.text.contains(localities))
        assertTrue(geoserver.wmsQueries.isEmpty())
    }

    @Test
    fun `GeoServer caido o lento es 503`() {
        val units = layerName("administrative_unit", "boundary")
        geoserver.published += units
        geoserver.down = true
        val down = wms(getMap(units))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, down.status)
        assertEquals("GEOSERVER_UNAVAILABLE", code(down))
        geoserver.down = false
        geoserver.delayMillis = 3000
        val started = System.nanoTime()
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, wms(getMap(units)).status)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2900, "el plazo del proxy es 1 s")
    }

    @Test
    fun `una respuesta que no es imagen es 503`() {
        val units = layerName("administrative_unit", "boundary")
        geoserver.published += units
        geoserver.wmsContentType = "application/vnd.ogc.se_xml"
        val response = wms(getMap(units))
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.status)
        assertFalse(response.text.contains("t_secreta"))
    }
}
```

- [ ] **Paso 8: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
yarn format
git add src/main/kotlin/sgspe/territory/WmsProxy.kt build.gradle.kts src/main/resources/application.yml \
  src/main/resources/application-prod.yml src/test/kotlin/sgspe/ProdProfileTest.kt \
  src/test/kotlin/sgspe/territory/WmsLayersCatalogTest.kt src/test/kotlin/sgspe/territory/GeoServerImitation.kt \
  src/test/kotlin/sgspe/territory/WmsProxyApiTest.kt
git commit -F - <<'MSG'
feat(territory): proxy WMS autenticado hacia GeoServer

WmsProxy arma la lista blanca con layers.json, las capas publicadas de la organización y READ; reenvía
GetMap y GetLegendGraphic con WebClient y plazo corto, solo imágenes; caído o lento, 503.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t3 -m "Merge f62b/t3: proxy WMS"
```

(Sacar de `git add` los archivos que el paso 4 o el 5 no cambiaron porque 6.1 ya los traía.) Entrega: «integración
pendiente: `WmsProxyApiTest`».

---

### Tarea 4: Los comandos de GDAL (`GdalCommand`, `GdalInfo`, `GdalTools`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/GdalCommand.kt`, `src/main/kotlin/sgspe/territory/GdalTools.kt`
- Modify: `src/main/resources/application.yml` (`sgspe.territory.gdal`)
- Test: `src/test/kotlin/sgspe/territory/GdalCommandTest.kt` (unitaria pura), `src/test/kotlin/sgspe/territory/GdalToolsTest.kt`
  (unitaria con GDAL), `src/test/kotlin/sgspe/territory/GdalFixtures.kt` (ayuda; la Tarea 7 la completa)

**Interfaces:**
- Consumes: `Crs.isCatalogued`, `Crs.value`, `Finding`, `toCodedException`.
- Produces: `SourceLayer(name: String, geometryType: String?, featureCount: Long?, detectedCrs: String?)`;
  `GdalCommand.info(ogrinfo: Path, driver: String, source: Path): List<String>`; `GdalCommand.toGeoJson(ogr2ogr: Path,
  driver: String, source: Path, layer: String, target: Path, limit: Int): List<String>`; `GdalCommand.fromGeoJson(ogr2ogr:
  Path, driver: String, source: Path, target: Path, layerName: String, targetSrid: Int?, layerOptions: List<String>):
  List<String>`; `GdalCommand.environment(server: Map<String, String>): Map<String, String>`; `GdalCommand.version(output:
  String): Pair<Int, Int>?`; `GdalCommand.atLeast(version: Pair<Int, Int>, minimum: String): Boolean`;
  `GdalCommand.drivers(output: String): Set<String>`; `GdalCommand.formatUnavailable(format: String):
  WasichaiException`; `GdalInfo.layers(json: String): List<SourceLayer>`; `GdalInfo.epsg(coordinateSystem: JsonNode):
  Int?`; `GdalProperties(path: String, minVersion: String, timeout: Duration)`; `GdalStatus(available, version, drivers,
  ogrinfo: Path?, ogr2ogr: Path?, reason)` con `supports(driver)`; `GdalTools.status(): GdalStatus`,
  `GdalTools.require(driver: String, format: String): GdalStatus`, `GdalTools.run(command: List<String>, workdir: Path,
  stdout: Path): Int` (bloqueantes); `GdalFixtures.bin: Path?` y `GdalFixtures.requireGdal(): Path`.

- [ ] **Paso 1: Rama de tarea y unitaria pura que falla**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t4
```

`src/test/kotlin/sgspe/territory/GdalCommandTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ValidationException
import java.nio.file.Path

// ASM-127 y especificación de F6 §3.4: GDAL con argumentos fijos más rutas del workspace, sin red y con entorno limpio; nada
// del cliente salvo el nombre de una capa que ya listó ogrinfo
class GdalCommandTest {
    private val ogrinfo = Path.of("/opt/gdal/bin/ogrinfo")
    private val ogr2ogr = Path.of("/opt/gdal/bin/ogr2ogr")
    private val workspace = Path.of("/tmp/sgspe-import-1")
    private val json = JsonMapper.builder().build()

    @Test
    fun `ogrinfo lista las capas con el driver de la lista blanca y la ruta del workspace`() {
        assertEquals(
            listOf("/opt/gdal/bin/ogrinfo", "-json", "-so", "-if", "GPKG", "/tmp/sgspe-import-1/upload-capas.gpkg"),
            GdalCommand.info(ogrinfo, "GPKG", workspace.resolve("upload-capas.gpkg"))
        )
    }

    @Test
    fun `ogr2ogr saca GeoJSON sin reproyectar, en 2D y con tope de elementos`() {
        val args = GdalCommand.toGeoJson(ogr2ogr, "ESRI Shapefile", workspace.resolve("archivo/predios.shp"), "predios", workspace.resolve("gdal-1.geojson"), 10_001)
        assertEquals(
            listOf(
                "/opt/gdal/bin/ogr2ogr",
                "-f",
                "GeoJSON",
                "-if",
                "ESRI Shapefile",
                "-dim",
                "XY",
                "-limit",
                "10001",
                "-lco",
                "RFC7946=NO",
                "/tmp/sgspe-import-1/gdal-1.geojson",
                "/tmp/sgspe-import-1/archivo/predios.shp",
                "predios"
            ),
            args
        )
        assertFalse(args.any { it == "-t_srs" || it == "-s_srs" || it.startsWith("/vsi") || it.startsWith("http") })
    }

    @Test
    fun `una capa que GDAL leeria como opcion o vacia es 400 sobre layer`() {
        for (bad in listOf("-sql", "", "--config")) {
            val e =
                assertThrows<ValidationException> {
                    GdalCommand.toGeoJson(ogr2ogr, "GPKG", workspace.resolve("a.gpkg"), bad, workspace.resolve("b.geojson"), 10)
                }
            assertEquals("layer", e.violations.single().field, bad)
        }
    }

    @Test
    fun `una ruta relativa no llega a GDAL`() {
        assertThrows<IllegalArgumentException> { GdalCommand.info(ogrinfo, "GPKG", Path.of("relativa.gpkg")) }
    }

    @Test
    fun `la escritura reproyecta solo si se pide y con las opciones fijas del formato`() {
        val source = workspace.resolve("capa.geojson")
        assertEquals(
            listOf("/opt/gdal/bin/ogr2ogr", "-f", "GPKG", "-if", "GeoJSON", "-nln", "land_parcel", "-t_srs", "EPSG:32717", "/tmp/sgspe-import-1/capa.gpkg", source.toString()),
            GdalCommand.fromGeoJson(ogr2ogr, "GPKG", source, workspace.resolve("capa.gpkg"), "land_parcel", 32717, emptyList())
        )
        val shp = GdalCommand.fromGeoJson(ogr2ogr, "ESRI Shapefile", source, workspace.resolve("capa.shp"), "land_parcel", null, listOf("ENCODING=UTF-8"))
        assertTrue(shp.containsAll(listOf("-lco", "ENCODING=UTF-8")))
        assertFalse(shp.contains("-t_srs"))
    }

    @Test
    fun `el entorno empieza vacio, corta la red y solo deja pasar los datos de GDAL y PROJ`() {
        val server =
            mapOf(
                "HOME" to "/home/sgspe",
                "PATH" to "/usr/bin",
                "GDAL_HTTP_PROXY" to "http://proxy.sgspe.test",
                "AWS_SECRET_ACCESS_KEY" to "secreto",
                "GDAL_DATA" to "/usr/share/gdal",
                "PROJ_DATA" to "/usr/share/proj",
                "CPL_VSIL_CURL_ALLOWED_EXTENSIONS" to ".tif"
            )
        assertEquals(
            mapOf(
                "GDAL_DATA" to "/usr/share/gdal",
                "PROJ_DATA" to "/usr/share/proj",
                "CPL_VSIL_CURL_ALLOWED_EXTENSIONS" to "",
                "GDAL_DISABLE_READDIR_ON_OPEN" to "EMPTY_DIR",
                "GDAL_PAM_ENABLED" to "NO",
                "CPL_DEBUG" to "OFF"
            ),
            GdalCommand.environment(server)
        )
    }

    @Test
    fun `la version sale de las dos formas de ogrinfo --version y se compara con el minimo`() {
        assertEquals(3 to 8, GdalCommand.version("GDAL 3.8.4, released 2024/02/08"))
        assertEquals(3 to 13, GdalCommand.version("GDAL 3.13.3 \"Iowa City\", released 2026/08/13"))
        assertNull(GdalCommand.version("ogrinfo: command not found"))
        assertTrue(GdalCommand.atLeast(3 to 8, "3.8"))
        assertTrue(GdalCommand.atLeast(3 to 13, "3.8"))
        assertTrue(GdalCommand.atLeast(4 to 0, "3.8"))
        assertFalse(GdalCommand.atLeast(3 to 6, "3.8"))
    }

    @Test
    fun `los drivers vectoriales salen de ogrinfo --formats`() {
        val output =
            """
            Supported Formats: (ro:read-only, rw:read-write, +:write from scratch, u:update, v:virtual-I/O s:subdatasets)
              ESRI Shapefile -vector- (rw+uv): ESRI Shapefile (*.shp, *.dbf, *.shz, *.shp.zip)
              GPKG -raster,vector- (rw+uvs): GeoPackage (*.gpkg, *.gpkg.zip)
              LIBKML -vector- (rw+uv): Keyhole Markup Language (LIBKML) (*.kml, *.kmz)
              GTiff -raster- (rw+uvs): GeoTIFF (*.tif, *.tiff)
            """.trimIndent()
        assertEquals(setOf("ESRI Shapefile", "GPKG", "LIBKML"), GdalCommand.drivers(output))
    }

    @Test
    fun `las capas de ogrinfo -json con su tipo, su cantidad y el CRS detectado`() {
        val output =
            """{"description":"capas.gpkg","driverShortName":"GPKG","layers":[
              {"name":"comunidades","geometryFields":[{"name":"geom","type":"Polygon","coordinateSystem":
                {"wkt":"PROJCRS[\"WGS 84 / UTM zone 17S\",ID[\"EPSG\",32717]]","projjson":{"type":"ProjectedCRS","id":{"authority":"EPSG","code":32717}}}}],
               "featureCount":2,"fields":[]},
              {"name":"vacia","geometryFields":[{"name":"geom","type":"Point","coordinateSystem":{"wkt":""}}],"featureCount":0},
              {"name":"sin_geometria","featureCount":3}]}"""
        assertEquals(
            listOf(
                SourceLayer("comunidades", "Polygon", 2, "EPSG 32717"),
                SourceLayer("vacia", "Point", 0, null),
                SourceLayer("sin_geometria", null, 3, null)
            ),
            GdalInfo.layers(output)
        )
    }

    @Test
    fun `el CRS detectado sale de projjson o del ID final del WKT, y fuera del catalogo es null`() {
        fun epsg(cs: String) = GdalInfo.epsg(json.readTree(cs))
        assertEquals(32718, epsg("""{"wkt":"PROJCRS[\"WGS 84 / UTM zone 18S\",BASEGEOGCRS[\"WGS 84\",ID[\"EPSG\",4326]],ID[\"EPSG\",32718]]"}"""))
        // WKT de ESRI, sin ID: no se adivina
        assertNull(epsg("""{"wkt":"PROJCS[\"WGS_1984_UTM_Zone_17S\",GEOGCS[\"GCS_WGS_1984\"]]"}"""))
        // solo el CRS base tiene ID: no es el del archivo
        assertNull(epsg("""{"wkt":"PROJCRS[\"x\",BASEGEOGCRS[\"WGS 84\",ID[\"EPSG\",4326]],CONVERSION[\"y\"]]"}"""))
        val webMercator = """{"layers":[{"name":"m","geometryFields":[{"type":"Point","coordinateSystem":{"projjson":{"id":{"authority":"EPSG","code":3857}}}}]}]}"""
        assertNull(GdalInfo.layers(webMercator).single().detectedCrs)
        val psad = """{"layers":[{"name":"p","geometryFields":[{"type":"Point","coordinateSystem":{"projjson":{"id":{"authority":"EPSG","code":24877}}}}]}]}"""
        assertEquals("EPSG 24877", GdalInfo.layers(psad).single().detectedCrs)
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.GdalCommandTest'`
Expected: FAIL de compilación («Unresolved reference: GdalCommand»).

- [ ] **Paso 2: Implementar `GdalCommand.kt`**

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.nio.file.Path

// una capa de un archivo según ogrinfo: su nombre, el tipo de su primera geometría, cuántos elementos tiene (null si el driver
// no lo sabe sin recorrerla) y su CRS como valor del catálogo «CRS de origen» («EPSG 32717»), o null si no tiene uno del
// catálogo. el CRS detectado es una propuesta: quien importa lo confirma (CON-024, especificación de F6 §2.2.6)
data class SourceLayer(
    val name: String,
    val geometryType: String?,
    val featureCount: Long?,
    val detectedCrs: String?
)

// los comandos de GDAL (ASM-127, especificación de F6 §3.4): argumentos fijos más rutas absolutas del workspace o de la
// carpeta de una exportación, nunca una URL, una ruta ni una opción del cliente; el único texto que viene de quien sube es el
// nombre de una capa, y solo después de que ogrinfo la listó. el entorno del proceso empieza vacío: sin HOME (ningún gdalrc),
// sin GDAL_HTTP_* ni credenciales, con /vsicurl/ cerrado (CPL_VSIL_CURL_ALLOWED_EXTENSIONS vacío no admite ninguna extensión)
// y sin listar carpetas al abrir. los KML los lee el driver KML, que no sigue NetworkLink (LIBKML sí). puro
object GdalCommand {
    private val VERSION = Regex("""GDAL (\d+)\.(\d+)""")
    private val DRIVER = Regex("""^\s+(.+?) -[a-z,]*vector[a-z,]*- \(""", RegexOption.MULTILINE)
    private val PASSTHROUGH = setOf("GDAL_DATA", "PROJ_DATA", "PROJ_LIB", "TMPDIR")

    fun info(
        ogrinfo: Path,
        driver: String,
        source: Path
    ): List<String> = listOf(ogrinfo.toString(), "-json", "-so", "-if", driver, absolute(source))

    fun toGeoJson(
        ogr2ogr: Path,
        driver: String,
        source: Path,
        layer: String,
        target: Path,
        limit: Int
    ): List<String> {
        requireSafeLayer(layer)
        return listOf(
            ogr2ogr.toString(),
            "-f",
            "GeoJSON",
            "-if",
            driver,
            "-dim",
            "XY",
            "-limit",
            limit.toString(),
            "-lco",
            "RFC7946=NO",
            absolute(target),
            absolute(source),
            layer
        )
    }

    // un GeoJSON EPSG:4326 hacia otro formato; reproyecta solo con targetSrid (GeoJSON se lee siempre como 4326)
    fun fromGeoJson(
        ogr2ogr: Path,
        driver: String,
        source: Path,
        target: Path,
        layerName: String,
        targetSrid: Int?,
        layerOptions: List<String>
    ): List<String> =
        listOf(ogr2ogr.toString(), "-f", driver, "-if", "GeoJSON", "-nln", layerName) +
            (targetSrid?.let { listOf("-t_srs", "EPSG:$it") } ?: emptyList()) +
            layerOptions.flatMap { listOf("-lco", it) } +
            listOf(absolute(target), absolute(source))

    fun environment(server: Map<String, String>): Map<String, String> =
        server.filterKeys { it in PASSTHROUGH } +
            mapOf(
                "CPL_VSIL_CURL_ALLOWED_EXTENSIONS" to "",
                "GDAL_DISABLE_READDIR_ON_OPEN" to "EMPTY_DIR",
                "GDAL_PAM_ENABLED" to "NO",
                "CPL_DEBUG" to "OFF"
            )

    fun version(output: String): Pair<Int, Int>? = VERSION.find(output)?.let { it.groupValues[1].toInt() to it.groupValues[2].toInt() }

    fun atLeast(
        version: Pair<Int, Int>,
        minimum: String
    ): Boolean {
        val (major, minor) = minimum.split('.').map(String::toInt)
        return version.first > major || (version.first == major && version.second >= minor)
    }

    fun drivers(output: String): Set<String> = DRIVER.findAll(output).map { it.groupValues[1].trim() }.toSet()

    fun formatUnavailable(format: String): WasichaiException =
        Finding(
            "FORMAT_UNAVAILABLE",
            "Este servidor no puede leer ni escribir $format: falta GDAL o su driver",
            "file",
            status = HttpStatus.valueOf(422)
        ).toCodedException()

    // el nombre de una capa sale de ogrinfo; aun así, uno que GDAL leería como opción se rechaza
    private fun requireSafeLayer(layer: String) {
        if (layer.isEmpty() || layer.startsWith("-")) throw ValidationException("Capa inválida", "layer", "no es una capa que GDAL pueda leer de este archivo")
    }

    private fun absolute(path: Path): String {
        require(path.isAbsolute) { "GDAL solo recibe rutas absolutas del workspace" }
        return path.toString()
    }
}

// la salida de ogrinfo -json -so: las capas, el tipo de su primera geometría, featureCount y el CRS (projjson.id o el ID
// final del WKT, que es el del CRS del archivo y no el de su CRS base). un CRS fuera del catálogo «CRS de origen» es null
object GdalInfo {
    private val JSON: JsonMapper = JsonMapper.builder().build()
    private val WKT_ID = Regex("""ID\["EPSG",\s*(\d+)]]\s*$""")

    fun layers(json: String): List<SourceLayer> =
        JSON.readTree(json).path("layers").values().map { layer ->
            val geometry = layer.path("geometryFields").values().firstOrNull()
            val count = layer.path("featureCount")
            SourceLayer(
                name = layer.path("name").asString(),
                geometryType = geometry?.path("type")?.takeIf { it.isString }?.asString(),
                featureCount = if (count.isNumber) count.asLong() else null,
                detectedCrs = geometry?.let { epsg(it.path("coordinateSystem")) }?.takeIf(Crs::isCatalogued)?.let(Crs::value)
            )
        }

    fun epsg(coordinateSystem: JsonNode): Int? {
        val id = coordinateSystem.path("projjson").path("id")
        val authority = id.path("authority")
        if (authority.isString && authority.asString() == "EPSG" && id.path("code").isNumber) return id.path("code").asInt()
        val wkt = coordinateSystem.path("wkt").takeIf { it.isString }?.asString() ?: return null
        return WKT_ID.find(wkt)?.groupValues?.get(1)?.toInt()
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.GdalCommandTest'`
Expected: PASS (10 pruebas).

- [ ] **Paso 3: La unitaria de los procesos que falla**

`src/test/kotlin/sgspe/territory/GdalFixtures.kt` (la Tarea 7 le suma los escritores de fixtures):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

// GDAL para las pruebas: la carpeta con ogrinfo y ogr2ogr del PATH o de las rutas de siempre (Homebrew en la máquina de
// desarrollo, /usr/bin en la CI). sin GDAL, una prueba que lo necesita se salta en la máquina de desarrollo y falla en la CI,
// que lo instala (ci.yml, paso GDAL)
object GdalFixtures {
    val bin: Path? by lazy {
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        (path + listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"))
            .map { Path.of(it) }
            .firstOrNull { Files.isExecutable(it.resolve("ogrinfo")) && Files.isExecutable(it.resolve("ogr2ogr")) }
    }

    fun requireGdal(): Path {
        val found = bin
        if (found == null) {
            if (System.getenv("CI") == "true") fail<Unit>("GDAL no está instalado: el paso GDAL de ci.yml lo instala")
            assumeTrue(false, "sin GDAL en el PATH: la prueba se salta")
        }
        return found!!
    }
}
```

`src/test/kotlin/sgspe/territory/GdalToolsTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.WasichaiException
import java.nio.file.Files
import java.nio.file.Path

// GdalTools: la versión mínima y los drivers se leen una vez; sin GDAL, FORMAT_UNAVAILABLE y no un 500
class GdalToolsTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `sin GDAL en la ruta configurada no esta disponible y require es 422 FORMAT_UNAVAILABLE`() {
        val tools = GdalTools(GdalProperties(path = dir.resolve("no-existe").toString()))
        assertFalse(tools.status().available)
        val e = assertThrows<WasichaiException> { tools.require("GPKG", "gpkg") }
        assertEquals(422 to "FORMAT_UNAVAILABLE", e.status.value() to e.violations.single().message)
    }

    @Test
    fun `una version menor que la minima no esta disponible`() {
        val tools = GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString(), minVersion = "99.0"))
        assertFalse(tools.status().available)
        assertTrue(tools.status().reason!!.contains("99.0"))
    }

    @Test
    fun `con GDAL, la version y los drivers de la lista blanca, y un comando corre con el entorno limpio`() {
        val tools = GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString()))
        val status = tools.status()
        assertTrue(status.available, status.reason)
        for (driver in listOf("ESRI Shapefile", "KML", "GPKG", "OpenFileGDB", "GPX", "GeoJSON")) assertTrue(status.supports(driver), driver)
        val out = dir.resolve("version.out")
        assertEquals(0, tools.run(listOf(status.ogrinfo.toString(), "--version"), dir, out))
        assertTrue(Files.readString(out).startsWith("GDAL "))
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.GdalToolsTest'`
Expected: FAIL de compilación («Unresolved reference: GdalTools»).

- [ ] **Paso 4: Implementar `GdalTools.kt`**

```kotlin
package sgspe.territory

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import java.util.concurrent.TimeUnit

// GDAL (sgspe.territory.gdal, ASM-127): la carpeta de ogrinfo y ogr2ogr (vacía: la primera del PATH que tenga los dos), la
// versión mínima y el plazo de una exportación (la importación usa el de ImportProperties)
@ConfigurationProperties("sgspe.territory.gdal")
data class GdalProperties(
    val path: String = "",
    val minVersion: String = "3.8",
    val timeout: Duration = Duration.ofSeconds(60)
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GdalProperties::class)
class GdalConfiguration

data class GdalStatus(
    val available: Boolean,
    val version: String?,
    val drivers: Set<String>,
    val ogrinfo: Path?,
    val ogr2ogr: Path?,
    val reason: String?
) {
    fun supports(driver: String): Boolean = available && driver in drivers
}

// los procesos de GDAL. status() se mide una vez por proceso, en el primer uso: quien lo llama ya está en Dispatchers.IO
// (ImportGate.blocking, una exportación o imports/formats), porque corre ogrinfo. run() corre un comando de GdalCommand con
// el entorno limpio, la salida y el error en archivos del workspace (nada se lee de una tubería que pueda llenarse) y espera
// interrumpible: el plazo vencido (ImportGate o el de la exportación) interrumpe el hilo y el proceso muere con sus hijos
@Component
class GdalTools(
    private val properties: GdalProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val probed: GdalStatus by lazy { probe() }

    fun status(): GdalStatus = probed

    fun require(
        driver: String,
        format: String
    ): GdalStatus {
        val status = status()
        if (!status.supports(driver)) throw GdalCommand.formatUnavailable(format)
        return status
    }

    fun run(
        command: List<String>,
        workdir: Path,
        stdout: Path
    ): Int {
        val stderr = workdir.resolve("gdal-${UUID.randomUUID()}.err")
        val builder = ProcessBuilder(command).directory(workdir.toFile()).redirectOutput(stdout.toFile()).redirectError(stderr.toFile())
        builder.environment().clear()
        builder.environment().putAll(GdalCommand.environment(System.getenv()))
        val process = builder.start()
        try {
            val exit = process.waitFor()
            // el error puede repetir valores del archivo: no va al log (RQ-SEC-010), solo el código
            if (exit != 0) log.warn("GDAL terminó con el código {}", exit)
            return exit
        } catch (e: InterruptedException) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            throw e
        }
    }

    private fun probe(): GdalStatus {
        val dir = locate() ?: return unavailable("no se encontraron ogrinfo y ogr2ogr")
        val ogrinfo = dir.resolve("ogrinfo")
        val output = capture(listOf(ogrinfo.toString(), "--version")) ?: return unavailable("ogrinfo --version no respondió")
        val version = GdalCommand.version(output) ?: return unavailable("no se reconoce la versión de GDAL")
        val label = "${version.first}.${version.second}"
        if (!GdalCommand.atLeast(version, properties.minVersion)) return unavailable("GDAL $label es menor que ${properties.minVersion}", label)
        val drivers = GdalCommand.drivers(capture(listOf(ogrinfo.toString(), "--formats")).orEmpty())
        log.info("GDAL {} en {} con {} drivers vectoriales", label, dir, drivers.size)
        return GdalStatus(true, label, drivers, ogrinfo, dir.resolve("ogr2ogr"), null)
    }

    private fun unavailable(
        reason: String,
        version: String? = null
    ): GdalStatus {
        log.warn("GDAL no disponible: {}; los formatos de GDAL responden FORMAT_UNAVAILABLE", reason)
        return GdalStatus(false, version, emptySet(), null, null, reason)
    }

    private fun locate(): Path? {
        val candidates =
            if (properties.path.isBlank()) {
                System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }.map { Path.of(it) }
            } else {
                listOf(Path.of(properties.path))
            }
        return candidates.firstOrNull { Files.isExecutable(it.resolve("ogrinfo")) && Files.isExecutable(it.resolve("ogr2ogr")) }
    }

    // la salida de un comando corto (la versión, los drivers), con 10 s de plazo
    private fun capture(command: List<String>): String? {
        val out = Files.createTempFile("sgspe-gdal-", ".out")
        return try {
            val builder = ProcessBuilder(command).redirectErrorStream(true).redirectOutput(out.toFile())
            builder.environment().clear()
            builder.environment().putAll(GdalCommand.environment(System.getenv()))
            val process = builder.start()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() == 0) {
                Files.readString(out)
            } else {
                null
            }
        } catch (_: IOException) {
            null
        } finally {
            Files.deleteIfExists(out)
        }
    }
}
```

En `application.yml`, dentro de `sgspe.territory` (después de `wms:` o de `layers:`):

```yaml
    gdal:
      # GDAL de la importación y las exportaciones de F6 (ASM-127): la carpeta de ogrinfo y ogr2ogr (vacía: el PATH), la
      # versión mínima y el plazo de una exportación. sin GDAL, esos formatos responden 422 FORMAT_UNAVAILABLE
      path: ${SGSPE_GDAL_PATH:}
      min-version: '3.8'
      timeout: 60s
```

- [ ] **Paso 5: Correr las unitarias**

Run: `./gradlew test --tests 'sgspe.territory.GdalCommandTest' --tests 'sgspe.territory.GdalToolsTest'`
Expected: PASS (las de `GdalToolsTest` con GDAL corren: está en `/opt/homebrew/bin`).

- [ ] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
yarn format
git add src/main/kotlin/sgspe/territory/GdalCommand.kt src/main/kotlin/sgspe/territory/GdalTools.kt src/main/resources/application.yml \
  src/test/kotlin/sgspe/territory/GdalCommandTest.kt src/test/kotlin/sgspe/territory/GdalToolsTest.kt src/test/kotlin/sgspe/territory/GdalFixtures.kt
git commit -F - <<'MSG'
feat(territory): comandos de GDAL con argumentos fijos y entorno limpio

GdalCommand arma ogrinfo y ogr2ogr solo con rutas del workspace y una capa ya listada; GdalInfo lee
ogrinfo -json con el CRS detectado del catálogo; GdalTools mide versión y drivers y corre sin red.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t4 -m "Merge f62b/t4: comandos de GDAL"
```

---

### Tarea 5: El formato del archivo y los ZIP (`ImportFormat`, `ImportArchives`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/ImportFormats.kt`
- Modify: `src/main/kotlin/sgspe/territory/ImportFiles.kt` (`ImportProperties` con `maxEntries` y `maxInflatedBytes`),
  `src/main/resources/application.yml` (`sgspe.territory.import`)
- Test: `src/test/kotlin/sgspe/territory/ImportFormatsTest.kt` (unitaria)

**Interfaces:**
- Consumes: `ImportWorkspace.resolveEntry(name: String): Path`, `ImportWorkspace.dir`.
- Produces: `enum class ImportFormat(key, driver: String?, multiLayer, crsFromFile, fixedSrid: Int?)` con `XLSX`, `CSV`,
  `GEOJSON`, `SHP`, `KML`, `KMZ`, `GPKG`, `GDB`, `GPX`, `gdal: Boolean` y `ImportFormat.detect(filename: String, file:
  File, maxEntries: Int): ImportFormat`; `ImportArchives.names(zip: File, maxEntries: Int): List<String>`,
  `ImportArchives.extract(zip: File, workspace: ImportWorkspace, folder: String, maxEntries: Int, maxBytes: Long): Path`,
  `ImportArchives.single(root: Path, suffix: String, directory: Boolean): Path`, `ImportArchives.shapefile(root: Path):
  Path`, `ImportArchives.kml(root: Path): Path`; `ImportProperties.maxEntries: Int = 1000`,
  `ImportProperties.maxInflatedBytes: Long = 200 MB`.

- [ ] **Paso 1: Rama de tarea y unitaria que falla**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t5
```

`src/test/kotlin/sgspe/territory/ImportFormatsTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.ValidationException
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// los formatos de F6 (especificación §3.4) y los controles de ZIP de RQ-SEC-020: ninguna entrada fuera de la carpeta, tope de
// entradas y de bytes inflados. nombres ficticios
class ImportFormatsTest {
    @TempDir
    lateinit var dir: File

    private fun zip(
        name: String,
        entries: Map<String, ByteArray>
    ): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { out ->
            for ((entry, bytes) in entries) {
                out.putNextEntry(ZipEntry(entry))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    private val shapefile = mapOf("predios.shp" to byteArrayOf(1), "predios.shx" to byteArrayOf(2), "predios.dbf" to byteArrayOf(3), "predios.prj" to byteArrayOf(4))

    @Test
    fun `la extension decide el formato y un ZIP se mira por dentro`() {
        val empty = File(dir, "x").apply { writeBytes(byteArrayOf(0)) }
        assertEquals(ImportFormat.CSV, ImportFormat.detect("pozos.csv", empty, 10))
        assertEquals(ImportFormat.GEOJSON, ImportFormat.detect("predios.json", empty, 10))
        assertEquals(ImportFormat.KMZ, ImportFormat.detect("Comunidades.KMZ", empty, 10))
        assertEquals(ImportFormat.GPX, ImportFormat.detect("ruta.gpx", empty, 10))
        assertEquals(ImportFormat.SHP, ImportFormat.detect("predios.zip", zip("a.zip", shapefile), 10))
        val gdb = zip("b.zip", mapOf("datos.gdb/a00000001.gdbtable" to byteArrayOf(1), "datos.gdb/gdb" to byteArrayOf(2)))
        assertEquals(ImportFormat.GDB, ImportFormat.detect("datos.zip", gdb, 10))
        assertTrue(ImportFormat.SHP.gdal && !ImportFormat.XLSX.gdal)
        assertEquals(4326, ImportFormat.KML.fixedSrid)
    }

    @Test
    fun `un formato desconocido, un ZIP sin capa o con dos clases y un ZIP roto son 400 sobre file`() {
        val junk = File(dir, "y").apply { writeText("no es un zip") }
        for ((name, file) in listOf("plano.dwg" to junk, "roto.zip" to junk, "vacio.zip" to zip("c.zip", mapOf("leeme.txt" to byteArrayOf(1)))) +
            ("mixto.zip" to zip("d.zip", shapefile + ("datos.gdb/gdb" to byteArrayOf(1))))) {
            assertEquals("file", assertThrows<ValidationException> { ImportFormat.detect(name, file, 10) }.violations.single().field, name)
        }
    }

    @Test
    fun `una entrada con punto punto o absoluta es 400 y no escribe fuera de la carpeta`() {
        for (entry in listOf("../fuera.txt", "archivo/../../fuera.txt", "/etc/fuera.txt")) {
            val bad = zip("malo-${entry.hashCode()}.zip", mapOf(entry to "x".toByteArray()))
            ImportWorkspace.create().use { workspace ->
                assertEquals("file", assertThrows<ValidationException> { ImportArchives.extract(bad, workspace, "archivo", 10, 1_000) }.violations.single().field)
                assertFalse(Files.exists(workspace.dir.parent.resolve("fuera.txt")))
            }
        }
    }

    @Test
    fun `mas entradas o mas bytes inflados que el tope son 400`() {
        val many = zip("muchas.zip", (1..5).associate { "f$it.txt" to byteArrayOf(1) })
        val big = zip("grande.zip", mapOf("ceros.bin" to ByteArray(10_000)))
        ImportWorkspace.create().use { workspace ->
            assertThrows<ValidationException> { ImportArchives.extract(many, workspace, "a", 4, 1_000_000) }
            val e = assertThrows<ValidationException> { ImportArchives.extract(big, workspace, "b", 10, 1_000) }
            assertTrue(e.violations.single().message.contains("descomprimido"))
        }
    }

    @Test
    fun `el Shapefile necesita shx y dbf, y el KMZ su kml`() {
        ImportWorkspace.create().use { workspace ->
            val root = ImportArchives.extract(zip("ok.zip", shapefile), workspace, "ok", 10, 1_000)
            assertEquals("predios.shp", ImportArchives.shapefile(root).fileName.toString())
            val partial = ImportArchives.extract(zip("sin-dbf.zip", shapefile - "predios.dbf"), workspace, "sin", 10, 1_000)
            assertTrue(assertThrows<ValidationException> { ImportArchives.shapefile(partial) }.violations.single().message.contains(".dbf"))
            val kmz = ImportArchives.extract(zip("c.kmz", mapOf("doc.kml" to "<kml/>".toByteArray(), "files/icono.png" to byteArrayOf(1))), workspace, "kmz", 10, 1_000)
            assertEquals("doc.kml", ImportArchives.kml(kmz).fileName.toString())
            val two = ImportArchives.extract(zip("dos.zip", shapefile + ("otros.shp" to byteArrayOf(1))), workspace, "dos", 10, 1_000)
            assertThrows<ValidationException> { ImportArchives.shapefile(two) }
        }
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.ImportFormatsTest'`
Expected: FAIL de compilación («Unresolved reference: ImportFormat»).

- [ ] **Paso 2: Los topes del ZIP en `ImportProperties`**

En `ImportFiles.kt`, `ImportProperties` suma dos campos al final (y su comentario):

```kotlin
    val maxLatitude: Double = 0.1,
    // un ZIP de F6 (Shapefile, FileGDB, KMZ): cuántas entradas y cuántos bytes descomprimidos en total (RQ-SEC-020)
    val maxEntries: Int = 1_000,
    val maxInflatedBytes: Long = 200L * 1024 * 1024
)
```

En `application.yml`, dentro de `sgspe.territory.import` (después de `max-concurrent`):

```yaml
      # un ZIP de F6 (Shapefile, FileGDB, KMZ): entradas y bytes descomprimidos (200 MB) como máximo
      max-entries: 1000
      max-inflated-bytes: 209715200
```

- [ ] **Paso 3: Implementar `ImportFormats.kt`**

```kotlin
package sgspe.territory

import wasichai.core.common.ValidationException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.io.path.isDirectory
import kotlin.io.path.name

// los formatos de la importación (gis.md §4.1, especificación de F6 §3.4): los de F2 los lee ImportReaders; los de F6, GDAL
// con su driver de la lista blanca (ASM-127). KML, KMZ y GPX están siempre en EPSG 4326; Shapefile, GeoPackage y FileGDB
// traen su CRS, que es solo una propuesta. multiLayer: el archivo puede traer varias capas
enum class ImportFormat(
    val key: String,
    val driver: String?,
    val multiLayer: Boolean,
    val crsFromFile: Boolean,
    val fixedSrid: Int?
) {
    XLSX("xlsx", null, false, false, null),
    CSV("csv", null, false, false, null),
    GEOJSON("geojson", null, false, false, null),
    SHP("shp", "ESRI Shapefile", false, true, null),
    KML("kml", "KML", true, false, Crs.WGS84),
    KMZ("kmz", "KML", true, false, Crs.WGS84),
    GPKG("gpkg", "GPKG", true, true, null),
    GDB("gdb", "OpenFileGDB", true, true, null),
    GPX("gpx", "GPX", true, false, Crs.WGS84);

    val gdal: Boolean get() = driver != null

    companion object {
        private const val SUPPORTED = "se importan XLSX, CSV, GeoJSON, Shapefile o FileGDB en ZIP, KML, KMZ, GeoPackage y GPX"

        // por la extensión del nombre; un ZIP, por su contenido (un .shp o una carpeta .gdb)
        fun detect(
            filename: String,
            file: File,
            maxEntries: Int
        ): ImportFormat {
            val name = filename.lowercase()
            return when {
                name.endsWith(".xlsx") -> XLSX
                name.endsWith(".csv") -> CSV
                name.endsWith(".geojson") || name.endsWith(".json") -> GEOJSON
                name.endsWith(".kml") -> KML
                name.endsWith(".kmz") -> KMZ
                name.endsWith(".gpkg") -> GPKG
                name.endsWith(".gpx") -> GPX
                name.endsWith(".zip") -> zipped(file, maxEntries)
                else -> throw ValidationException("Formato no admitido", "file", SUPPORTED)
            }
        }

        private fun zipped(
            file: File,
            maxEntries: Int
        ): ImportFormat {
            val names = ImportArchives.names(file, maxEntries).map { it.lowercase() }
            val shp = names.any { it.endsWith(".shp") }
            val gdb = names.any { name -> name.split('/').dropLast(1).any { it.endsWith(".gdb") } }
            return when {
                shp && gdb -> throw ValidationException("Formato no admitido", "file", "el ZIP trae un Shapefile y una FileGDB: suba uno por archivo")
                shp -> SHP
                gdb -> GDB
                else -> throw ValidationException("Formato no admitido", "file", "un ZIP debe traer un Shapefile (.shp, .shx, .dbf) o una carpeta .gdb")
            }
        }
    }
}

// los ZIP de F6 (Shapefile, FileGDB, KMZ) se descomprimen en el workspace con los controles de RQ-SEC-020: cada entrada se
// resuelve dentro de la carpeta (../ o una ruta absoluta es un 400), con tope de entradas y de bytes inflados (una bomba zip es
// un 400 que nombra la descompresión). GDAL abre después una ruta del workspace, nunca /vsizip/ ni el ZIP del cliente.
// se escribe con FileOutputStream (su canal no se cierra con la interrupción del hilo) y se atiende la interrupción cada tanto
object ImportArchives {
    private const val BUFFER = 64 * 1024

    fun names(
        zip: File,
        maxEntries: Int
    ): List<String> =
        open(zip).use { archive ->
            if (archive.size() > maxEntries) throw tooManyEntries(maxEntries)
            archive.entries().toList().map { it.name }
        }

    fun extract(
        zip: File,
        workspace: ImportWorkspace,
        folder: String,
        maxEntries: Int,
        maxBytes: Long
    ): Path {
        val root = workspace.resolveEntry(folder)
        Files.createDirectories(root)
        var total = 0L
        open(zip).use { archive ->
            if (archive.size() > maxEntries) throw tooManyEntries(maxEntries)
            val buffer = ByteArray(BUFFER)
            for (entry in archive.entries()) {
                val name = entry.name
                // una entrada absoluta o con un tramo .. no se descomprime aunque, normalizada, cayera dentro de la carpeta
                if (name.startsWith("/") || name.startsWith("\\") || name.split('/', '\\').any { it == ".." }) throw outside()
                val target = workspace.resolveEntry("$folder/$name")
                if (!target.startsWith(root)) throw outside()
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                    continue
                }
                Files.createDirectories(target.parent)
                archive.getInputStream(entry).use { input ->
                    FileOutputStream(target.toFile()).use { out ->
                        while (true) {
                            if (Thread.interrupted()) throw InterruptedException("importación cancelada")
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > maxBytes) {
                                throw ValidationException("Archivo demasiado grande", "file", "el ZIP descomprimido pasa del máximo de $maxBytes bytes")
                            }
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
        return root
    }

    // el único archivo (o carpeta, en FileGDB) con esa terminación bajo `root`; ninguno o varios, 400
    fun single(
        root: Path,
        suffix: String,
        directory: Boolean
    ): Path {
        val found =
            Files.walk(root).use { paths ->
                paths.filter { it != root && it.name.lowercase().endsWith(suffix) && it.isDirectory() == directory }.toList()
            }
        return when (found.size) {
            1 -> found.single()
            0 -> throw ValidationException("Archivo inválido", "file", "el archivo no trae un $suffix")
            else -> throw ValidationException("Archivo inválido", "file", "el archivo trae ${found.size} $suffix: suba uno por archivo")
        }
    }

    // el .shp con su .shx y su .dbf al lado (el .prj y el .cpg son opcionales)
    fun shapefile(root: Path): Path {
        val shp = single(root, ".shp", directory = false)
        val base = shp.name.dropLast(4)
        val siblings = Files.list(shp.parent).use { list -> list.map { it.name.lowercase() }.toList() }.toSet()
        val missing = listOf(".shx", ".dbf").filter { "${base.lowercase()}$it" !in siblings }
        if (missing.isNotEmpty()) throw ValidationException("Archivo inválido", "file", "al Shapefile le falta ${missing.joinToString(" y ")}")
        return shp
    }

    // el doc.kml de un KMZ o, si no está, su único .kml
    fun kml(root: Path): Path {
        val doc = root.resolve("doc.kml")
        return if (Files.isRegularFile(doc)) doc else single(root, ".kml", directory = false)
    }

    private fun open(zip: File): ZipFile =
        try {
            ZipFile(zip)
        } catch (_: ZipException) {
            throw ValidationException("Archivo inválido", "file", "no es un ZIP válido")
        } catch (_: IOException) {
            throw ValidationException("Archivo inválido", "file", "no es un ZIP válido")
        }

    private fun tooManyEntries(max: Int) = ValidationException("Archivo inválido", "file", "el ZIP tiene más de $max entradas")

    // no se repite el nombre de la entrada: lo escribe quien sube el archivo
    private fun outside() = ValidationException("Archivo inválido", "file", "una entrada del archivo apunta fuera de su carpeta")
}
```

Nota: `ImportWorkspace.resolveEntry` rechaza lo que sale del workspace; además, una entrada absoluta (`/etc/x`, que
dentro de la carpeta se volvería `archivo/etc/x`) o con un tramo `..` se rechaza de entrada, y el `startsWith(root)`
impide que una entrada caiga en otra carpeta del mismo workspace.

- [ ] **Paso 4: Correr la unitaria**

Run: `./gradlew test --tests 'sgspe.territory.ImportFormatsTest' --tests 'sgspe.territory.ImportFilesTest'`
Expected: PASS.

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
yarn format
git add src/main/kotlin/sgspe/territory/ImportFormats.kt src/main/kotlin/sgspe/territory/ImportFiles.kt src/main/resources/application.yml \
  src/test/kotlin/sgspe/territory/ImportFormatsTest.kt
git commit -F - <<'MSG'
feat(territory): formatos de F6 y descompresión controlada de los ZIP

ImportFormat reconoce Shapefile y FileGDB en ZIP, KML, KMZ, GeoPackage y GPX; ImportArchives
descomprime en el workspace sin rutas fuera de la carpeta y con tope de entradas y de bytes.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t5 -m "Merge f62b/t5: formatos y ZIP"
```

---

### Tarea 6: GDAL en la CI (`gdal-bin` ≥ 3.8)

**Files:**
- Modify: `.github/workflows/ci.yml` (trabajos `build` e `integration`)

**Interfaces:**
- Consumes: nada.
- Produces: `ogrinfo` y `ogr2ogr` en el `PATH` de los dos trabajos, con versión ≥ 3.8 y los drivers `ESRI Shapefile`,
  `KML`, `GPKG`, `OpenFileGDB`, `GPX` y `GeoJSON` verificados; `CI=true` (lo pone GitHub), que hace fallar a
  `GdalFixtures.requireGdal` si falta.

- [ ] **Paso 1: Rama de tarea y el paso de GDAL**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t6
```

En `.github/workflows/ci.yml`, en el trabajo `build` y en el trabajo `integration`, el mismo paso después de
`gradle/actions/setup-gradle`:

```yaml
      # GDAL de la importación y las exportaciones de F6 (ASM-127): ogrinfo y ogr2ogr de gdal-bin, versión mínima 3.8 y los
      # drivers de la lista blanca. las unitarias de GDAL fallan sin él en la CI (GdalFixtures.requireGdal)
      - name: GDAL
        run: |
          sudo apt-get update -qq
          sudo apt-get install -y -qq --no-install-recommends gdal-bin
          ogrinfo --version
          set -- $(ogrinfo --version | sed -E 's/^GDAL ([0-9]+)\.([0-9]+).*/\1 \2/')
          if [ "$1" -lt 3 ] || { [ "$1" -eq 3 ] && [ "$2" -lt 8 ]; }; then echo "GDAL $1.$2 es menor que 3.8"; exit 1; fi
          for driver in 'ESRI Shapefile' KML GPKG OpenFileGDB GPX GeoJSON; do
            ogrinfo --formats | grep -q "^  $driver -" || { echo "falta el driver $driver"; exit 1; }
          done
```

- [ ] **Paso 2: Comprobar el script en local y el formato**

```bash
cd ~/IdeaProjects/sgspe && set -- $(ogrinfo --version | sed -E 's/^GDAL ([0-9]+)\.([0-9]+).*/\1 \2/') && echo "$1.$2"
for driver in 'ESRI Shapefile' KML GPKG OpenFileGDB GPX GeoJSON; do ogrinfo --formats | grep -q "^  $driver -" && echo "ok $driver"; done
yarn format && yarn format:check
```

Expected: `3.13` y seis «ok»; `format:check` sin cambios pendientes. La versión del runner (`ubuntu-latest`: 3.8.4 en 24.04)
la confirma el primer verde de la CI del PR (Tarea 15, paso 3: buscar la línea de `ogrinfo --version` en el registro).

- [ ] **Paso 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -F - <<'MSG'
ci: instala gdal-bin 3.8 o superior en build e integración

Las unitarias y las pruebas de integración de la importación y las exportaciones de F6 llaman a
ogrinfo y ogr2ogr; el paso verifica la versión mínima y los drivers de la lista blanca.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t6 -m "Merge f62b/t6: GDAL en la CI"
```

---
### Tarea 7: La lectura con GDAL (`GdalReader`) y los fixtures sintéticos

**Files:**
- Create: `src/main/kotlin/sgspe/territory/GdalReader.kt`
- Modify: `src/test/kotlin/sgspe/territory/GdalFixtures.kt` (los escritores de fixtures)
- Test: `src/test/kotlin/sgspe/territory/GdalReaderTest.kt` (unitaria con GDAL; la corre el implementador)

**Interfaces:**
- Consumes: `GdalTools.require`, `GdalTools.run`, `GdalCommand.info`, `GdalCommand.toGeoJson`, `GdalInfo.layers`,
  `SourceLayer` (Tarea 4); `ImportFormat`, `ImportArchives` (Tarea 5); `ImportWorkspace`, `ImportProperties`
  (`maxRows`, `maxEntries`, `maxInflatedBytes`), `ImportReaders.geojson`.
- Produces: `GdalSource(geojson: File, layer: SourceLayer, layers: List<SourceLayer>)`;
  `GdalReader.read(format: ImportFormat, upload: File, workspace: ImportWorkspace, layer: String?): GdalSource`
  (bloqueante; quien llama la corre en `ImportGate.blocking`); `class ImportChoiceRequired(field, reason, message, layers:
  List<SourceLayer>) : WasichaiException` con `ImportChoiceRequired.layer(layers)` (`400`, `errors[0] = { layer,
  LAYER_REQUIRED }`) y `ImportChoiceRequired.sourceCrs(layers, catalogued: List<String>)` (`400` sobre `sourceCrs`);
  `GdalFixtures.collection`, `utmSquare`, `lonLatSquare`, `point`, `shapefileZip`, `geopackage`, `fileGdbZip`, `kmz`,
  `gpx`, `zip`, `info`.

**Decisión de fixtures.** Los archivos de cada formato se generan en la propia prueba con `ogr2ogr` a partir de un
GeoJSON sintético (`GdalFixtures`), en un `@TempDir`: ningún binario versionado (un FileGDB son decenas de archivos, un
GeoPackage es SQLite), el fixture sale del mismo GDAL que lo va a leer (la CI con 3.8, el desarrollo con 3.13) y la
geometría queda escrita en Kotlin, donde se revisa. Un script versionado no aportaría nada: la prueba ya necesita GDAL.

- [ ] **Paso 1: Rama de tarea y los escritores de fixtures**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t7
```

`GdalFixtures.kt` queda así (se conserva `bin` y `requireGdal` de la Tarea 4):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import tools.jackson.databind.json.JsonMapper
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// GDAL para las pruebas: la carpeta con ogrinfo y ogr2ogr del PATH o de las rutas de siempre (Homebrew en la máquina de
// desarrollo, /usr/bin en la CI), y los fixtures de cada formato generados con ogr2ogr a partir de GeoJSON sintético
// (RQ-GIS-021): ningún binario versionado. sin GDAL, una prueba que lo necesita se salta en la máquina de desarrollo y falla
// en la CI, que lo instala (ci.yml, paso GDAL)
object GdalFixtures {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    val bin: Path? by lazy {
        val path = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        (path + listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"))
            .map { Path.of(it) }
            .firstOrNull { Files.isExecutable(it.resolve("ogrinfo")) && Files.isExecutable(it.resolve("ogr2ogr")) }
    }

    fun requireGdal(): Path {
        val found = bin
        if (found == null) {
            if (System.getenv("CI") == "true") fail<Unit>("GDAL no está instalado: el paso GDAL de ci.yml lo instala")
            assumeTrue(false, "sin GDAL en el PATH: la prueba se salta")
        }
        return found!!
    }

    // ---- geometrías y colecciones sintéticas ----

    fun collection(features: List<Pair<Map<String, Any?>, Map<String, Any?>>>): String =
        JSON.writeValueAsString(
            mapOf("type" to "FeatureCollection", "features" to features.map { (properties, geometry) -> mapOf("type" to "Feature", "properties" to properties, "geometry" to geometry) })
        )

    // un cuadrado con su esquina en (x, y): metros en UTM o grados en 4326, según quien lo use
    private fun square(
        x: Double,
        y: Double,
        side: Double
    ): Map<String, Any?> =
        mapOf("type" to "Polygon", "coordinates" to listOf(listOf(listOf(x, y), listOf(x + side, y), listOf(x + side, y + side), listOf(x, y + side), listOf(x, y))))

    fun utmSquare(
        easting: Double,
        northing: Double,
        sideM: Double
    ) = square(easting, northing, sideM)

    fun lonLatSquare(
        longitude: Double,
        latitude: Double,
        sideDegrees: Double
    ) = square(longitude, latitude, sideDegrees)

    fun point(
        x: Double,
        y: Double
    ): Map<String, Any?> = mapOf("type" to "Point", "coordinates" to listOf(x, y))

    // ---- un archivo por formato ----

    // un ZIP con <layer>.shp, .shx, .dbf, .prj (del EPSG asignado, sin reproyectar) y .cpg UTF-8
    fun shapefileZip(
        dir: File,
        layer: String,
        geojson: String,
        epsg: Int
    ): File {
        val work = work(dir, "shp-$layer")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        ogr2ogr("-f", "ESRI Shapefile", "-a_srs", "EPSG:$epsg", "-lco", "ENCODING=UTF-8", File(work, "$layer.shp").path, source.path)
        return zip(File(dir, "$layer.zip"), work.listFiles()!!.filter { it.name.startsWith("$layer.") }.associate { it.name to it.readBytes() })
    }

    // un GeoPackage con una capa por entrada de `layers` (nombre de capa -> GeoJSON), todas con el mismo EPSG
    fun geopackage(
        dir: File,
        name: String,
        layers: Map<String, String>,
        epsg: Int
    ): File {
        val work = work(dir, "gpkg-$name")
        val target = File(dir, "$name.gpkg")
        layers.entries.forEachIndexed { i, (layer, geojson) ->
            val source = File(work, "capa-$i.geojson").apply { writeText(geojson) }
            val update = if (i == 0) emptyArray() else arrayOf("-update")
            ogr2ogr("-f", "GPKG", *update, "-a_srs", "EPSG:$epsg", "-nln", layer, target.path, source.path)
        }
        return target
    }

    // un ZIP con la carpeta datos.gdb (driver OpenFileGDB, que escribe desde GDAL 3.6)
    fun fileGdbZip(
        dir: File,
        layer: String,
        geojson: String,
        epsg: Int
    ): File {
        val work = work(dir, "gdb-$layer")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        val gdb = File(work, "datos.gdb")
        ogr2ogr("-f", "OpenFileGDB", "-a_srs", "EPSG:$epsg", "-nln", layer, gdb.path, source.path)
        return zip(File(dir, "$layer-gdb.zip"), gdb.listFiles()!!.associate { "datos.gdb/${it.name}" to it.readBytes() })
    }

    // un KMZ con doc.kml (4326); las propiedades Name y Description van a los campos del mismo nombre del driver KML
    fun kmz(
        dir: File,
        layer: String,
        geojson: String
    ): File {
        val work = work(dir, "kmz-$layer")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        val kml = File(work, "doc.kml")
        ogr2ogr("-f", "KML", "-nln", layer, kml.path, source.path)
        return zip(File(dir, "$layer.kmz"), mapOf("doc.kml" to kml.readBytes()))
    }

    // un GPX de puntos (4326): la propiedad name va al nombre del waypoint
    fun gpx(
        dir: File,
        name: String,
        geojson: String
    ): File {
        val work = work(dir, "gpx-$name")
        val source = File(work, "fuente.geojson").apply { writeText(geojson) }
        val target = File(dir, "$name.gpx")
        ogr2ogr("-f", "GPX", target.path, source.path)
        return target
    }

    fun zip(
        target: File,
        entries: Map<String, ByteArray>
    ): File {
        ZipOutputStream(target.outputStream()).use { out ->
            for ((entry, bytes) in entries) {
                out.putNextEntry(ZipEntry(entry))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return target
    }

    // las capas de un archivo según ogrinfo, para revisar lo que escribió una exportación
    fun info(
        file: File,
        driver: String
    ): List<SourceLayer> = GdalInfo.layers(gdal("ogrinfo", "-json", "-so", "-if", driver, file.path))

    private fun work(
        dir: File,
        name: String
    ): File = File(dir, name).apply { mkdirs() }

    private fun ogr2ogr(vararg args: String) {
        gdal("ogr2ogr", *args)
    }

    private fun gdal(
        tool: String,
        vararg args: String
    ): String {
        val process = ProcessBuilder(listOf(requireGdal().resolve(tool).toString()) + args).redirectErrorStream(true).start()
        val output = process.inputStream.readAllBytes().toString(Charsets.UTF_8)
        check(process.waitFor(60, TimeUnit.SECONDS) && process.exitValue() == 0) { "$tool ${args.joinToString(" ")}: $output" }
        return output
    }
}
```

- [ ] **Paso 2: La unitaria del lector que falla**

`src/test/kotlin/sgspe/territory/GdalReaderTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.ValidationException
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files

// la lectura con GDAL (gis.md §4.1, especificación de F6 §3.4) sobre fixtures sintéticos en UTM 17S cerca de
// SyntheticGeo.ORIGIN (-78.50, -7.90 ≈ E 775 649, N 9 125 930): sin reproyectar, con el CRS detectado como propuesta y la
// capa elegida. códigos y nombres ficticios
class GdalReaderTest {
    @TempDir
    lateinit var dir: File

    private fun reader(properties: ImportProperties = ImportProperties()) =
        GdalReader(GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString())), properties)

    private fun upload(
        workspace: ImportWorkspace,
        file: File
    ): File = Files.copy(file.toPath(), workspace.resolveEntry("upload-${file.name}")).toFile()

    private val parcels =
        GdalFixtures.collection(
            listOf(
                mapOf("codigo" to "PRD-01", "tenencia" to "PROPIEDAD") to GdalFixtures.utmSquare(776_000.0, 9_126_000.0, 100.0),
                mapOf("codigo" to "PRD-02", "tenencia" to "COMUNAL") to GdalFixtures.utmSquare(776_200.0, 9_126_000.0, 100.0)
            )
        )

    @Suppress("UNCHECKED_CAST")
    private fun firstX(row: SourceRow): Double = ((row.geometry!!["coordinates"] as List<List<List<BigDecimal>>>)[0][0][0]).toDouble()

    @Test
    fun `un Shapefile UTM 17S se lee sin reproyectar y propone EPSG 32717`() {
        val reader = reader()
        val zip = GdalFixtures.shapefileZip(dir, "predios", parcels, 32717)
        ImportWorkspace.create().use { workspace ->
            val source = reader.read(ImportFormat.SHP, upload(workspace, zip), workspace, null)
            assertEquals("predios", source.layer.name)
            assertEquals("EPSG 32717", source.layer.detectedCrs)
            val rows = ImportReaders.geojson(source.geojson, 10)
            assertEquals(listOf("PRD-01", "PRD-02"), rows.map { it.values["codigo"] })
            assertEquals(776_000.0, firstX(rows.first()), 0.001)
        }
    }

    @Test
    fun `un GeoPackage con dos capas pide elegir y con la capa la convierte`() {
        val reader = reader()
        val communities = GdalFixtures.collection(listOf(mapOf("codigo" to "COM-01", "nombre" to "Comunidad Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_126_500.0, 300.0)))
        val gpkg = GdalFixtures.geopackage(dir, "capas", linkedMapOf("comunidades" to communities, "predios" to parcels), 32717)
        ImportWorkspace.create().use { workspace ->
            val file = upload(workspace, gpkg)
            val e = assertThrows<ImportChoiceRequired> { reader.read(ImportFormat.GPKG, file, workspace, null) }
            assertEquals("layer" to "LAYER_REQUIRED", e.violations.single().let { it.field to it.message })
            assertEquals(setOf("comunidades", "predios"), e.layers.map { it.name }.toSet())
            assertEquals(setOf("EPSG 32717"), e.layers.map { it.detectedCrs }.toSet())
            val source = reader.read(ImportFormat.GPKG, file, workspace, "comunidades")
            assertEquals("Comunidad Ejemplo", ImportReaders.geojson(source.geojson, 10).single().values["nombre"])
        }
    }

    @Test
    fun `una capa pedida que no esta en el archivo es 400 sobre layer`() {
        val gpkg = GdalFixtures.geopackage(dir, "una", mapOf("predios" to parcels), 32717)
        ImportWorkspace.create().use { workspace ->
            val e = assertThrows<ValidationException> { reader().read(ImportFormat.GPKG, upload(workspace, gpkg), workspace, "-sql") }
            assertEquals("layer", e.violations.single().field)
        }
    }

    @Test
    fun `un GPX con solo waypoints no pide capa`() {
        val points =
            GdalFixtures.collection(
                listOf(mapOf("name" to "DDH-01") to GdalFixtures.point(-78.50, -7.90), mapOf("name" to "DDH-02") to GdalFixtures.point(-78.49, -7.90))
            )
        val gpx = GdalFixtures.gpx(dir, "sondajes", points)
        ImportWorkspace.create().use { workspace ->
            val source = reader().read(ImportFormat.GPX, upload(workspace, gpx), workspace, null)
            assertEquals("waypoints", source.layer.name)
            assertEquals(listOf("DDH-01", "DDH-02"), ImportReaders.geojson(source.geojson, 10).map { it.values["name"] })
        }
    }

    @Test
    fun `un KMZ se descomprime y su kml se lee en grados`() {
        val kml =
            GdalFixtures.collection(
                listOf(mapOf("Name" to "COM-KMZ-01", "Description" to "Comunidad Ejemplo") to GdalFixtures.lonLatSquare(-78.50, -7.90, 0.01))
            )
        val kmz = GdalFixtures.kmz(dir, "comunidades", kml)
        ImportWorkspace.create().use { workspace ->
            val source = reader().read(ImportFormat.KMZ, upload(workspace, kmz), workspace, null)
            val row = ImportReaders.geojson(source.geojson, 10).single()
            assertEquals("COM-KMZ-01", row.values["Name"])
            assertEquals(-78.50, firstX(row), 0.000001)
        }
    }

    @Test
    fun `una FileGDB en ZIP se lee con OpenFileGDB`() {
        val features = GdalFixtures.collection(listOf(mapOf("codigo" to "AMB-01", "nombre" to "Zona de protección Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_127_000.0, 200.0)))
        val zip = GdalFixtures.fileGdbZip(dir, "elementos", features, 32717)
        ImportWorkspace.create().use { workspace ->
            val source = reader().read(ImportFormat.GDB, upload(workspace, zip), workspace, null)
            assertEquals("EPSG 32717", source.layer.detectedCrs)
            assertEquals("AMB-01", ImportReaders.geojson(source.geojson, 10).single().values["codigo"])
        }
    }

    @Test
    fun `una capa con mas elementos que el maximo es 400 sin convertirla y un archivo roto es 400 sobre file`() {
        val gpkg = GdalFixtures.geopackage(dir, "grande", mapOf("predios" to parcels), 32717)
        ImportWorkspace.create().use { workspace ->
            val e = assertThrows<ValidationException> { reader(ImportProperties(maxRows = 1)).read(ImportFormat.GPKG, upload(workspace, gpkg), workspace, null) }
            assertEquals("file", e.violations.single().field)
            val broken = File(dir, "roto.gpkg").apply { writeText("no es un GeoPackage") }
            assertEquals("file", assertThrows<ValidationException> { reader().read(ImportFormat.GPKG, upload(workspace, broken), workspace, null) }.violations.single().field)
            assertNull(Files.list(workspace.dir).use { list -> list.filter { it.fileName.toString().endsWith(".geojson") }.findFirst().orElse(null) })
        }
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.GdalReaderTest'`
Expected: FAIL de compilación («Unresolved reference: GdalReader»).

- [ ] **Paso 3: Implementar `GdalReader.kt`**

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import wasichai.core.common.FieldViolation
import wasichai.core.common.ValidationException
import wasichai.core.common.WasichaiException
import java.io.File
import java.nio.file.Files
import java.util.UUID

// lo que GDAL dejó listo para el flujo de F2: el GeoJSON de la capa elegida en su CRS de origen, la capa y todas las capas con
// elementos del archivo
data class GdalSource(
    val geojson: File,
    val layer: SourceLayer,
    val layers: List<SourceLayer>
)

// una elección que falta para leer el archivo: la capa (LAYER_REQUIRED) o el CRS de origen de un formato de GDAL. es un 400
// con el problem+json de siempre más las capas del archivo y su CRS detectado (ImportController lo agrega como `layers`)
class ImportChoiceRequired(
    field: String,
    reason: String,
    message: String,
    val layers: List<SourceLayer>
) : WasichaiException(HttpStatus.BAD_REQUEST, message, listOf(FieldViolation(field, reason))) {
    companion object {
        fun layer(layers: List<SourceLayer>) = ImportChoiceRequired("layer", "LAYER_REQUIRED", "El archivo tiene varias capas: elija una", layers)

        fun sourceCrs(
            layers: List<SourceLayer>,
            catalogued: List<String>
        ) = ImportChoiceRequired(
            "sourceCrs",
            "es obligatorio y sale del catálogo «CRS de origen» (${catalogued.joinToString(", ")}): confirme el CRS detectado o elija otro",
            "CRS de origen inválido",
            layers
        )
    }
}

// la lectura con GDAL de los formatos de F6 (gis.md §4.1, ASM-127, especificación de F6 §3.4): el archivo ya está en el
// workspace (sus límites de tamaño); los ZIP y el KMZ se descomprimen ahí con ImportArchives; ogrinfo lista las capas con el
// driver de la lista blanca; solo cuentan las que tienen elementos (un GPX lista siempre cinco); con varias y sin `layer`, 400
// LAYER_REQUIRED; una capa con más elementos que maxRows es 400 sin convertirla; ogr2ogr saca la capa elegida a GeoJSON sin
// reproyectar (ImportReaders la lee después con el CRS de origen confirmado). bloqueante e interrumpible: quien llama la corre
// en ImportGate.blocking, dentro del plazo de la importación
@Component
class GdalReader(
    private val tools: GdalTools,
    private val properties: ImportProperties
) {
    fun read(
        format: ImportFormat,
        upload: File,
        workspace: ImportWorkspace,
        layer: String?
    ): GdalSource {
        val driver = format.driver ?: error("$format no se lee con GDAL")
        val status = tools.require(driver, format.key)
        val source =
            when (format) {
                ImportFormat.SHP -> ImportArchives.shapefile(extract(upload, workspace))
                ImportFormat.GDB -> ImportArchives.single(extract(upload, workspace), ".gdb", directory = true)
                ImportFormat.KMZ -> ImportArchives.kml(extract(upload, workspace))
                else -> upload.toPath()
            }
        val listing = workspace.resolveEntry("gdal-${UUID.randomUUID()}.json")
        if (tools.run(GdalCommand.info(status.ogrinfo!!, driver, source), workspace.dir, listing) != 0) {
            throw ValidationException("Archivo inválido", "file", "GDAL no pudo leer el archivo como ${format.key.uppercase()}")
        }
        val layers =
            try {
                GdalInfo.layers(Files.readString(listing))
            } catch (_: JacksonException) {
                throw ValidationException("Archivo inválido", "file", "GDAL no pudo listar las capas del archivo")
            }.filter { it.featureCount != 0L }
        if (layers.isEmpty()) throw ValidationException("Archivo vacío", "file", "el archivo no tiene elementos en ninguna capa")
        val chosen =
            when {
                layer != null -> layers.firstOrNull { it.name == layer } ?: throw ValidationException("Capa inválida", "layer", "no es una capa con elementos de este archivo")
                layers.size == 1 -> layers.single()
                else -> throw ImportChoiceRequired.layer(layers)
            }
        chosen.featureCount?.let {
            if (it > properties.maxRows) throw ValidationException("Archivo demasiado grande", "file", "la capa tiene $it elementos; el máximo es ${properties.maxRows}")
        }
        val target = workspace.resolveEntry("gdal-${UUID.randomUUID()}.geojson")
        val log = workspace.resolveEntry("gdal-${UUID.randomUUID()}.out")
        val exit = tools.run(GdalCommand.toGeoJson(status.ogr2ogr!!, driver, source, chosen.name, target, properties.maxRows + 1), workspace.dir, log)
        if (exit != 0 || !Files.exists(target)) throw ValidationException("Archivo inválido", "file", "GDAL no pudo convertir la capa elegida")
        return GdalSource(target.toFile(), chosen, layers)
    }

    private fun extract(
        upload: File,
        workspace: ImportWorkspace
    ) = ImportArchives.extract(upload, workspace, "archivo", properties.maxEntries, properties.maxInflatedBytes)
}
```

- [ ] **Paso 4: Correr la unitaria**

Run: `./gradlew test --tests 'sgspe.territory.GdalReaderTest' --tests 'sgspe.territory.GdalToolsTest'`
Expected: PASS (7 pruebas de `GdalReaderTest` con GDAL 3.13.3).

Si `un archivo roto` deja un `.geojson`: `ogrinfo` falla antes de `ogr2ogr`, así que no debe; si aparece, revisar el orden.

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/GdalReader.kt src/test/kotlin/sgspe/territory/GdalFixtures.kt src/test/kotlin/sgspe/territory/GdalReaderTest.kt
git commit -F - <<'MSG'
feat(territory): lectura con GDAL de Shapefile, KML, KMZ, GeoPackage, FileGDB y GPX

GdalReader lista las capas con elementos, pide elegir con LAYER_REQUIRED, propone el CRS detectado
y saca la capa a GeoJSON sin reproyectar. Fixtures sintéticos generados con ogr2ogr en la prueba.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t7 -m "Merge f62b/t7: lectura con GDAL"
```

---

### Tarea 8: GDAL en la importación (`ImportController`, `TerritoryImports`, `imports/formats`)

**Files:**
- Modify: `src/main/kotlin/sgspe/territory/ImportController.kt`, `src/main/kotlin/sgspe/territory/TerritoryImports.kt`,
  `src/main/resources/application.yml` (comentario de `max-parts`)
- Test: `src/test/kotlin/sgspe/territory/GdalImportApiTest.kt`, `src/test/kotlin/sgspe/territory/GdalMissingApiTest.kt`
  (integración, las corre el coordinador)

**Interfaces:**
- Consumes: `GdalReader.read`, `GdalSource`, `ImportChoiceRequired` (Tarea 7); `ImportFormat.detect` (Tarea 5);
  `GdalTools.status`, `GdalTools.require` (Tarea 4); `wasichai.core.common.GlobalExceptionHandler.handleWasichai(ex):
  ProblemDetail`.
- Produces: `ImportRequest` con `layer: String?` y `workspace: ImportWorkspace?`; `ImportPreview` con `format: String`,
  `layer: String?`, `detectedCrs: String?`; la parte multipart `layer`; `GET /api/sgspe/territory/imports/formats` →
  `{ formats: [{ format, available, multiLayer, crsFromFile }] }`; el `400` con `layers` (`LAYER_REQUIRED` y el CRS que
  falta); `422 FORMAT_UNAVAILABLE`. Dentro de `TerritoryImports`: `Prepared`, `prepare(request): Prepared`, `open(prepared):
  Context`, `Context` con `format` y `layer`.

- [ ] **Paso 1: Rama de tarea y la prueba de integración (la corre el coordinador)**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t8
```

`src/test/kotlin/sgspe/territory/GdalImportApiTest.kt` (la Tarea 9 le suma los destinos nuevos):

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest
import tools.jackson.databind.JsonNode
import java.io.File

// gis.md §9 y especificación de F6 §5 «GdalImportApiTest»: un fixture sintético por formato, generado con ogr2ogr en la prueba
// (GdalFixtures), en UTM 17S cerca de SyntheticGeo.ORIGIN; elección de capa, CRS detectado como propuesta y ZIP con ../.
// necesita GDAL en el servidor de la prueba (la CI lo instala)
class GdalImportApiTest : DomainApiTest() {
    @TempDir
    lateinit var dir: File

    private fun bytes(file: File) = file.name to file.readBytes()

    private fun preview(
        project: String,
        file: File,
        target: String,
        token: String,
        sourceCrs: String? = null,
        layer: String? = null,
        columns: String? = null
    ) = multipart(
        "/api/sgspe/territory/imports",
        mapOf("file" to bytes(file)),
        listOfNotNull("target" to target, "project" to project, sourceCrs?.let { "sourceCrs" to it }, layer?.let { "layer" to it }, columns?.let { "columns" to it }).toMap(),
        token
    )

    private fun confirm(
        body: JsonNode,
        project: String,
        file: File,
        target: String,
        token: String,
        sourceCrs: String? = null,
        layer: String? = null,
        columns: String? = null
    ) = multipart(
        "/api/sgspe/territory/imports/${body["importBatchId"].asString()}/confirm",
        mapOf("file" to bytes(file)),
        listOfNotNull(
            "target" to target,
            "project" to project,
            "sha256" to body["sha256"].asString(),
            sourceCrs?.let { "sourceCrs" to it },
            layer?.let { "layer" to it },
            columns?.let { "columns" to it }
        ).toMap(),
        token
    )

    private fun codes(body: String): List<String> = tree(body)["errors"]?.values()?.map { it["message"].asString() }.orEmpty()

    // dos predios de 100 m en UTM 17S, con códigos únicos de esta prueba
    private fun parcels(prefix: String): String =
        GdalFixtures.collection(
            listOf(
                mapOf("codigo" to "$prefix-1", "tenencia" to "PROPIEDAD") to GdalFixtures.utmSquare(776_000.0, 9_126_000.0, 100.0),
                mapOf("codigo" to "$prefix-2", "tenencia" to "COMUNAL") to GdalFixtures.utmSquare(776_200.0, 9_126_000.0, 100.0)
            )
        )

    private val parcelColumns = """{"code": "codigo", "tenureType": "tenencia"}"""

    @Test
    fun `los formatos del servidor incluyen los de GDAL disponibles`() {
        GdalFixtures.requireGdal()
        val formats = tree(send("GET", "/api/sgspe/territory/imports/formats", null, HttpStatus.OK))["formats"].values().associateBy { it["format"].asString() }
        assertEquals(setOf("xlsx", "csv", "geojson", "shp", "kml", "kmz", "gpkg", "gdb", "gpx"), formats.keys)
        assertTrue(formats.values.all { it["available"].asBoolean() })
        assertTrue(formats.getValue("gpkg")["multiLayer"].asBoolean())
        assertTrue(formats.getValue("shp")["crsFromFile"].asBoolean())
        assertEquals(false, formats.getValue("kml")["crsFromFile"].asBoolean())
    }

    @Test
    fun `un Shapefile con prj UTM 17S propone su CRS, lo exige y se confirma`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val prefix = uniqueName("SHP")
        val zip = GdalFixtures.shapefileZip(dir, "predios", parcels(prefix), 32717)
        val missing = preview(project, zip, "land_parcel", analyst.token, columns = parcelColumns)
        assertEquals(HttpStatus.BAD_REQUEST, missing.status, missing.text)
        assertEquals(listOf("sourceCrs"), problemFields(missing.text))
        assertEquals("EPSG 32717", tree(missing.text)["layers"][0]["detectedCrs"].asString())
        val response = preview(project, zip, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = parcelColumns)
        assertEquals(HttpStatus.OK, response.status, response.text)
        val body = tree(response.text)
        assertEquals("shp", body["format"].asString())
        assertEquals("EPSG 32717", body["detectedCrs"].asString())
        assertEquals(2, body["valid"].asInt())
        val done = confirm(body, project, zip, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = parcelColumns)
        assertEquals(HttpStatus.OK, done.status, done.text)
        assertEquals(2, tree(done.text)["created"].asInt())
        val parcel = tree(send("GET", "/api/objects/land_parcel/records?code=$prefix-1", null, HttpStatus.OK))["content"][0]["attributes"]
        assertEquals("EPSG 32717", parcel["source_crs"].asString())
        assertTrue(parcel["source_reference"].asString().contains("capa predios"), parcel.toString())
        assertEquals(1.0, parcel["calculated_area_ha"].asDouble(), 0.01)
    }

    @Test
    fun `un GeoPackage con dos capas pide elegir con LAYER_REQUIRED`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val gpkg = GdalFixtures.geopackage(dir, "capas", linkedMapOf("predios_a" to parcels(uniqueName("GPA")), "predios_b" to parcels(uniqueName("GPB"))), 32717)
        val response = preview(project, gpkg, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = parcelColumns)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.text)
        assertEquals(listOf("LAYER_REQUIRED"), codes(response.text))
        assertEquals(setOf("predios_a", "predios_b"), tree(response.text)["layers"].values().map { it["name"].asString() }.toSet())
        val chosen = preview(project, gpkg, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", layer = "predios_b", columns = parcelColumns)
        assertEquals(HttpStatus.OK, chosen.status, chosen.text)
        assertEquals("predios_b", tree(chosen.text)["layer"].asString())
    }

    @Test
    fun `KMZ y GPX fijan 4326 y otro CRS es 400`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val prefix = uniqueName("KMZ")
        val kml = GdalFixtures.collection(listOf(mapOf("Name" to "$prefix-1", "Description" to "PROPIEDAD") to GdalFixtures.lonLatSquare(-78.50, -7.90, 0.001)))
        val kmz = GdalFixtures.kmz(dir, "predios", kml)
        val columns = """{"code": "Name", "tenureType": "Description"}"""
        val response = preview(project, kmz, "land_parcel", analyst.token, columns = columns)
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertEquals("EPSG 4326", tree(response.text)["sourceCrs"].asString())
        val wrong = preview(project, kmz, "land_parcel", analyst.token, sourceCrs = "EPSG 32717", columns = columns)
        assertEquals(HttpStatus.BAD_REQUEST, wrong.status)
        assertEquals(listOf("sourceCrs"), problemFields(wrong.text))
        val points =
            GdalFixtures.collection(
                listOf(mapOf("name" to uniqueName("DDH")) to GdalFixtures.point(-78.50, -7.90), mapOf("name" to uniqueName("DDH")) to GdalFixtures.point(-78.499, -7.90))
            )
        val gpx = preview(project, GdalFixtures.gpx(dir, "sondajes", points), "project_component", analyst.token, columns = """{"code": "name"}""")
        assertEquals(HttpStatus.OK, gpx.status, gpx.text)
        assertEquals(2, tree(gpx.text)["valid"].asInt())
    }

    @Test
    fun `una FileGDB con concesiones MULTIPOLYGON se previsualiza`() {
        val project = projectWithCrs()
        val features = GdalFixtures.collection(listOf(mapOf("codigo" to uniqueName("CON"), "nombre" to "Concesión Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_128_000.0, 500.0)))
        val zip = GdalFixtures.fileGdbZip(dir, "concesiones", features, 32717)
        val response = preview(project, zip, "mining_concession", token, sourceCrs = "EPSG 32717", columns = """{"code": "codigo", "name": "nombre"}""")
        assertEquals(HttpStatus.OK, response.status, response.text)
        assertEquals("gdb", tree(response.text)["format"].asString())
        assertEquals(1, tree(response.text)["valid"].asInt())
    }

    @Test
    fun `un ZIP con una entrada fuera de la carpeta es 400 sobre file`() {
        GdalFixtures.requireGdal()
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val zip = GdalFixtures.zip(File(dir, "malo.zip"), mapOf("predios.shp" to byteArrayOf(1), "../../fuera.shx" to byteArrayOf(2), "predios.dbf" to byteArrayOf(3)))
        val response = preview(project, zip, "land_parcel", analyst.token, sourceCrs = "EPSG 32717")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.text)
        assertEquals(listOf("file"), problemFields(response.text))
    }
}
```

`src/test/kotlin/sgspe/territory/GdalMissingApiTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.DomainApiTest

// especificación de F6 §3.4: sin GDAL, los formatos de GDAL responden 422 FORMAT_UNAVAILABLE e imports/formats lo dice; los
// de F2 siguen. el servidor de la prueba apunta GDAL a una carpeta que no existe
@TestPropertySource(properties = ["sgspe.territory.gdal.path=/sgspe-sin-gdal"])
class GdalMissingApiTest : DomainApiTest() {
    @Test
    fun `sin GDAL los formatos de F6 no estan disponibles y los de F2 si`() {
        val formats = tree(send("GET", "/api/sgspe/territory/imports/formats", null, HttpStatus.OK))["formats"].values().associateBy { it["format"].asString() }
        assertFalse(formats.getValue("shp")["available"].asBoolean())
        assertTrue(formats.getValue("csv")["available"].asBoolean())
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val gpkg = "capas.gpkg" to "no importa: GDAL no está".toByteArray()
        val response =
            multipart("/api/sgspe/territory/imports", mapOf("file" to gpkg), mapOf("target" to "land_parcel", "project" to project, "sourceCrs" to "EPSG 32717"), analyst.token)
        assertEquals(HttpStatus.valueOf(422), response.status, response.text)
        assertEquals("FORMAT_UNAVAILABLE", tree(response.text)["errors"][0]["message"].asString())
        val csv = "pozos.csv" to "code;easting;northing\nTST-SIN-GDAL;812345;9123456".toByteArray()
        val ok = multipart("/api/sgspe/territory/imports", mapOf("file" to csv), mapOf("target" to "project_component", "project" to project, "sourceCrs" to "EPSG 32717"), analyst.token)
        assertEquals(HttpStatus.OK, ok.status, ok.text)
    }
}
```

- [ ] **Paso 2: `ImportController`: la capa, el workspace, los formatos y el problema con capas**

Cambios en `ImportController.kt`:

1. Constructor:

```kotlin
class ImportController(
    private val imports: TerritoryImports,
    private val properties: ImportProperties,
    private val tools: GdalTools,
    private val problems: GlobalExceptionHandler
) {
```

2. En `request(...)`, el `ImportRequest` lleva la capa (acotada como el nombre del archivo) y el workspace:

```kotlin
        return ImportRequest(
            target = field(parts, "target"),
            project = field(parts, "project"),
            sourceCrs = field(parts, "sourceCrs"),
            columns = field(parts, "columns"),
            file = stored,
            filename = filename,
            sha256 = sha256,
            layer = field(parts, "layer")?.take(MAX_FILENAME),
            workspace = workspace
        )
```

3. Dos métodos nuevos en la clase:

```kotlin
    // GET /api/sgspe/territory/imports/formats (api.md §4.2): qué formatos lee este servidor; los de GDAL, solo si hay GDAL de
    // la versión mínima con su driver (ASM-127). status() corre ogrinfo la primera vez: va en el despachador de E/S
    @GetMapping("/formats")
    suspend fun formats(exchange: ServerWebExchange): Map<String, Any> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val status = withContext(Dispatchers.IO) { tools.status() }
        return mapOf(
            "formats" to
                ImportFormat.entries.map {
                    mapOf(
                        "format" to it.key,
                        "available" to (it.driver?.let(status::supports) ?: true),
                        "multiLayer" to it.multiLayer,
                        "crsFromFile" to it.crsFromFile
                    )
                }
        )
    }

    // LAYER_REQUIRED y el CRS de origen que falta en un formato de GDAL: el problem+json de wasichai más las capas del archivo
    // con su CRS detectado, para que el portal ofrezca elegir y proponga el CRS (especificación de F6 §2.2.6)
    @ExceptionHandler(ImportChoiceRequired::class)
    fun choiceRequired(e: ImportChoiceRequired): ProblemDetail = problems.handleWasichai(e).apply { setProperty("layers", e.layers) }
```

4. `PREVIEW_PARTS` suma `layer`:

```kotlin
        val PREVIEW_PARTS = setOf("file", "target", "project", "sourceCrs", "columns", "layer")
```

Imports nuevos: `org.springframework.http.ProblemDetail`, `org.springframework.web.bind.annotation.ExceptionHandler`,
`org.springframework.web.bind.annotation.GetMapping`, `wasichai.core.common.GlobalExceptionHandler`. El comentario de la
clase suma: «desde F6, la parte `layer` (la capa elegida de un archivo de varias) y los formatos de GDAL».

En `application.yml`, el comentario de `max-parts` pasa a: `# la confirmación usa 7 partes y, con GDAL, 8 (layer): 8 acota
el disco que una petición puede ocupar (8 × 20 MB)`.

- [ ] **Paso 3: `TerritoryImports`: preparar, abrir y leer**

1. `ImportRequest` y `ImportPreview`:

```kotlin
// lo que llega en las partes del multipart, ya leído. desde F6, la capa elegida y la carpeta temporal donde GDAL descomprime y
// convierte (la crea y la borra ImportController)
class ImportRequest(
    val target: String?,
    val project: String?,
    val sourceCrs: String?,
    val columns: String?,
    val file: File,
    val filename: String,
    val sha256: String,
    val layer: String? = null,
    val workspace: ImportWorkspace? = null
)
```

Y al final de `ImportPreview`:

```kotlin
    val outOfZone: List<OutOfZoneRow>,
    // desde F6: el formato, la capa leída y su CRS detectado, una propuesta (CON-024)
    val format: String = ImportFormat.CSV.key,
    val layer: String? = null,
    val detectedCrs: String? = null
)
```

2. Constructor de `TerritoryImports`: suma `private val gdal: GdalReader` y `private val tools: GdalTools`.

3. `preview` y `confirm` abren el archivo dentro del plazo:

```kotlin
    suspend fun preview(request: ImportRequest): ImportPreview =
        gate.admit {
            val prepared = prepare(request)
            gate.timed {
                val context = open(prepared)
                val analysis = reconcile(context, analyze(context))
                ImportPreview(
                    importBatchId = UUID.randomUUID(),
                    sha256 = request.sha256,
                    target = context.target.name,
                    sourceCrs = Crs.value(context.sourceSrid),
                    rows = analysis.rows,
                    valid = analysis.candidates.size,
                    invalid = analysis.invalid,
                    differences = analysis.candidates.map { it.difference },
                    outOfZone = analysis.candidates.mapNotNull { c -> c.parsed.outOfZone?.let { OutOfZoneRow(c.parsed.row, c.parsed.code, it) } },
                    format = context.format.key,
                    layer = context.layer?.name,
                    detectedCrs = context.layer?.detectedCrs
                )
            }
        }
```

En `confirm`, las dos primeras líneas de `gate.admit { … }` pasan a:

```kotlin
        return gate.admit {
            val prepared = prepare(request)
            val (context, analysis) = gate.timed { open(prepared).let { it to reconcile(it, analyze(it)) } }
```

(el resto de `confirm` no cambia).

4. `Context` suma el formato y la capa, y aparece `Prepared`:

```kotlin
    private class Context(
        val target: Target,
        val project: UUID,
        val sourceSrid: Int,
        val workingSrid: Int?,
        val columns: ColumnMapping,
        // el archivo que se lee: el subido (F2) o el GeoJSON de la capa elegida (GDAL, en su CRS de origen)
        val file: File,
        val filename: String,
        val organization: UUID,
        val format: ImportFormat,
        val layer: SourceLayer?
    )

    // lo que se revisa antes de leer el archivo: destino, proyecto, permisos, formato, CRS pedido y GDAL
    private class Prepared(
        val target: Target,
        val project: UUID,
        val requestedSrid: Int?,
        val workingSrid: Int?,
        val columns: ColumnMapping,
        val format: ImportFormat,
        val request: ImportRequest,
        val organization: UUID
    )
```

5. `prepare` devuelve `Prepared` (el formato se mira después de los permisos: un ZIP se abre para ver qué trae):

```kotlin
    private suspend fun prepare(request: ImportRequest): Prepared {
        val target =
            TARGETS[request.target] ?: throw ValidationException("Destino inválido", "target", "debe ser uno de ${TARGETS.keys.joinToString(", ")}")
        val project = Records.uuid(request.project) ?: throw ValidationException("Falta el proyecto", "project", "debe ser el id de un proyecto")
        val catalogued = catalog.enumValues(SOURCE_CRS)
        // un valor fuera del catálogo es siempre 400 (CON-024); que falte se decide con el formato
        val requestedSrid = request.sourceCrs?.let { value -> value.takeIf { it in catalogued }?.let(Crs::srid) ?: throw invalidCrs(catalogued) }
        val user = callers.require()
        val grants = mutableListOf(Actions.CREATE to target.name, Actions.UPDATE to target.name)
        if (target.name == CONCESSION) grants += Actions.UPDATE to "project"
        permissions.require(user, "Importar ${target.name}", *grants.toTypedArray())
        if (target.tables || target.name == CONCESSION) assignments.requireAssigned(user, project)
        val format = ImportGate.blocking { ImportFormat.detect(request.filename, request.file, properties.maxEntries) }
        // F2 exige el CRS ya aquí; GDAL lo pide después de listar las capas, para proponer el detectado; KML, KMZ y GPX están
        // siempre en EPSG 4326
        if (requestedSrid == null && !format.gdal) throw invalidCrs(catalogued)
        format.fixedSrid?.let { fixed ->
            if (requestedSrid != null && requestedSrid != fixed) {
                throw ValidationException("CRS de origen inválido", "sourceCrs", "un archivo ${format.key.uppercase()} está siempre en ${Crs.value(fixed)}")
            }
        }
        format.driver?.let { driver -> ImportGate.blocking { tools.require(driver, format.key) } }
        val projectRecord = records.get("project", project)
        // solo una zona de trabajo: GeoSql mide en ellas y nada más (P19)
        val workingSrid = Crs.srid(Values.text(projectRecord.attributes["working_crs"]))?.takeIf(Crs::isWorkingZone)
        return Prepared(target, project, requestedSrid, workingSrid, ColumnMapping.parse(request.columns), format, request, user.organizationId)
    }

    private fun invalidCrs(catalogued: List<String>) =
        ValidationException("CRS de origen inválido", "sourceCrs", "es obligatorio y sale del catálogo «CRS de origen»: ${catalogued.joinToString(", ")}")

    // el archivo listo para leer y el CRS de origen resuelto: el subido (F2) o el GeoJSON que GDAL sacó de la capa elegida. un
    // formato de GDAL sin sourceCrs es 400 con las capas y su CRS detectado (ImportChoiceRequired); KML, KMZ y GPX, 4326
    private suspend fun open(prepared: Prepared): Context {
        val request = prepared.request
        val source =
            if (prepared.format.gdal) {
                val workspace = request.workspace ?: error("una importación con GDAL necesita su carpeta temporal")
                ImportGate.blocking { gdal.read(prepared.format, request.file, workspace, request.layer) }
            } else {
                null
            }
        val sourceSrid =
            prepared.format.fixedSrid ?: prepared.requestedSrid
                ?: throw ImportChoiceRequired.sourceCrs(source?.layers.orEmpty(), catalog.enumValues(SOURCE_CRS))
        return Context(
            prepared.target,
            prepared.project,
            sourceSrid,
            prepared.workingSrid,
            prepared.columns,
            source?.geojson ?: request.file,
            request.filename,
            prepared.organization,
            prepared.format,
            source?.layer
        )
    }
```

6. `analyze` lee según el formato y el mensaje de `FORMAT_NOT_SUPPORTED` nombra los formatos de F6:

```kotlin
        val rows =
            ImportGate.blocking {
                if (context.format.gdal) {
                    ImportReaders.geojson(context.file, properties.maxRows)
                } else {
                    ImportReaders.read(context.file, context.filename, properties.maxRows)
                }
            }
        if (!context.target.tables && rows.any { it.geometry == null }) {
            throw importError("FORMAT_NOT_SUPPORTED", "file", "Este destino necesita un polígono: importe GeoJSON, Shapefile, KML, KMZ, GeoPackage o FileGDB")
        }
```

7. En `write`, la procedencia nombra la capa:

```kotlin
                "source_reference" to "${context.filename}${context.layer?.let { ", capa ${it.name}" }.orEmpty()}, fila ${row.row}",
```

Nota: la detección del formato lee el directorio central de un ZIP, así que va después de los permisos; por eso un
archivo de F2 sin `sourceCrs` de alguien sin permiso pasa a ser `403` antes que `400` (el valor fuera del catálogo sigue
siendo `400` antes de los permisos). Un `.dwg` ya era `400` sobre `file` en F2 (lo dice ahora `ImportFormat.detect`, con
el mismo campo). `ImportReaders.read` sigue solo con los formatos de F2 (lo prueba `ImportFilesTest`).

- [ ] **Paso 4: Compilar y correr las unitarias de la importación**

Run: `./gradlew test --tests 'sgspe.territory.*'`
Expected: PASS (`TerritoryImportsTest` construye `ImportPreview` con nombres y los campos nuevos tienen valor por
defecto). Las de integración compilan con `build`.

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
yarn format
git add src/main/kotlin/sgspe/territory/ImportController.kt src/main/kotlin/sgspe/territory/TerritoryImports.kt \
  src/main/resources/application.yml src/test/kotlin/sgspe/territory/GdalImportApiTest.kt src/test/kotlin/sgspe/territory/GdalMissingApiTest.kt
git commit -F - <<'MSG'
feat(territory): importación de los formatos de GDAL con capa y CRS detectado

La vista previa y la confirmación aceptan layer; sin capa o sin CRS en un formato de GDAL responden
400 con las capas y su CRS detectado; KML, KMZ y GPX fijan 4326; imports/formats dice qué hay.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t8 -m "Merge f62b/t8: GDAL en la importación"
```

Entrega: «integración pendiente: `GdalImportApiTest`, `GdalMissingApiTest`, `TerritoryImportApiTest`,
`ImportLimitsApiTest`, `ImportTimeoutApiTest`» (las tres últimas vigilan que F2 no cambió).

---

### Tarea 9: Los destinos nuevos `community` y `environmental_feature`

**Files:**
- Modify: `src/main/kotlin/sgspe/territory/TerritoryImports.kt`, `src/main/kotlin/sgspe/common/TenantModel.kt`
- Test: `src/test/kotlin/sgspe/territory/TerritoryImportsTest.kt` (unitaria), `src/test/kotlin/sgspe/common/TenantModelTest.kt`
  (unitaria), `src/test/kotlin/sgspe/territory/GdalImportApiTest.kt` (integración, la corre el coordinador)

**Interfaces:**
- Consumes: `TenantModel.has`, `ObjectDefinitions.current`; lo de la Tarea 8.
- Produces: `TenantModel.hasField(organizationId: UUID, objectName: String, field: String): Boolean`;
  `ImportRules.missingRequired(required: Set<String>, attributes: Map<String, Any?>): List<String>`; los destinos
  `community` (`territory`, MULTIPOLYGON) y `environmental_feature` (`boundary`, MULTIPOLYGON) con reconciliación por
  `code`; `TerritoryImports.COMMUNITY = "community"`, `TerritoryImports.FEATURE = "environmental_feature"`.

- [ ] **Paso 1: Rama de tarea y las unitarias que fallan**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t9
```

En `TerritoryImportsTest.kt`, una prueba más:

```kotlin
    // especificación de F6 §3.4: el alta de una comunidad o de un elemento ambiental por importación lleva el nombre y, el
    // elemento, su tipo (columna elegida o valor fijo); sin ellos la fila se rechaza nombrando el campo
    @Test
    fun `los atributos obligatorios de un destino que faltan se nombran en orden`() {
        assertEquals(emptyList<String>(), ImportRules.missingRequired(setOf("name"), mapOf("code" to "COM-01", "name" to "Comunidad Ejemplo")))
        assertEquals(listOf("feature_type", "name"), ImportRules.missingRequired(setOf("name", "feature_type"), mapOf("code" to "AMB-01", "name" to null)))
    }
```

En `src/test/kotlin/sgspe/common/TenantModelTest.kt`, una prueba más (con los imports `wasichai.core.metadata.CustomField`,
`wasichai.core.metadata.CustomObject` y `wasichai.core.metadata.FieldType`):

```kotlin
    // un tenant que no tiene el modelo de F6 tiene community sin code: la importación no ofrece ese destino
    @Test
    fun `un campo se pregunta en la definicion de la organizacion`() {
        fun field(name: String) =
            CustomField(UUID.randomUUID(), UUID.randomUUID(), name, name, FieldType.TEXT, name, false, false, null, null, 0, null, null, emptyMap(), true, true)
        val f5: ObjectDefinitions =
            mock(ObjectDefinitions::class.java) { invocation ->
                if (invocation.method.name != "current") return@mock null
                ObjectDefinition(
                    CustomObject(UUID.randomUUID(), organization, "community", "community", "community", null, true, "community_tbl", null, null),
                    listOf(field("name"), field("territory"))
                )
            }
        val model = TenantModel(f5)
        runBlocking {
            assertTrue(model.hasField(organization, "community", "name"))
            assertFalse(model.hasField(organization, "community", "code"))
        }
    }
```

Run: `./gradlew test --tests 'sgspe.territory.TerritoryImportsTest' --tests 'sgspe.common.TenantModelTest'`
Expected: FAIL de compilación («Unresolved reference: missingRequired», «hasField»).

- [ ] **Paso 2: `TenantModel.hasField`**

En `TenantModel.kt`:

```kotlin
    // si la organización tiene el campo (un campo nuevo de una fase, como community.code de F6): la definición de la petición,
    // sin mirar los permisos de quien llama
    suspend fun hasField(
        organizationId: UUID,
        objectName: String,
        field: String
    ): Boolean = has(organizationId, objectName) && definitions.current(organizationId, objectName).fields.any { it.name == field }
```

- [ ] **Paso 3: Los destinos en `TerritoryImports`**

1. `ImportRules` suma:

```kotlin
    // los atributos sin los que no se crea un registro del destino, en orden
    fun missingRequired(
        required: Set<String>,
        attributes: Map<String, Any?>
    ): List<String> = required.filter { attributes[it] == null }.sorted()
```

2. `Target` suma tres propiedades:

```kotlin
    private class Target(
        val name: String,
        // campo por tipo GeoJSON: el de los componentes depende de la geometría; los demás tienen uno solo
        val fields: Map<String, Pair<String, String>>,
        val attributes: List<String>,
        val tables: Boolean,
        // lo que una fila debe traer (columna elegida o valor fijo del formulario); si no, se rechaza nombrándolo
        val required: Set<String> = emptySet(),
        // calculated_area_ha se escribe con la marca en la transacción (Areas.GEOMETRY): predios, concesiones y comunidades
        val writesArea: Boolean = !tables,
        // el destino llega con el modelo de F6 (code y los campos de origen): una organización sin él no lo tiene
        val sinceF6: Boolean = false
    )
```

3. `TARGETS` suma los dos destinos (y las constantes en el `companion object`):

```kotlin
        const val COMMUNITY = "community"
        const val FEATURE = "environmental_feature"
```

```kotlin
                COMMUNITY to
                    Target(COMMUNITY, mapOf("Polygon" to ("territory" to "MultiPolygon")), listOf("name"), tables = false, required = setOf("name"), sinceF6 = true),
                FEATURE to
                    Target(
                        FEATURE,
                        mapOf("Polygon" to ("boundary" to "MultiPolygon")),
                        listOf("name", "feature_type"),
                        tables = false,
                        required = setOf("name", "feature_type"),
                        writesArea = false,
                        sinceF6 = true
                    )
```

4. En `prepare`, después de `permissions.require(...)`:

```kotlin
        if (target.sinceF6 && !tenantModel.hasField(user.organizationId, target.name, "code")) {
            throw ValidationException("Destino inválido", "target", "${target.name} se importa con el modelo de F6, que esta organización todavía no tiene")
        }
```

y el constructor suma `private val tenantModel: TenantModel` (import `sgspe.common.TenantModel`).

5. En `attributes(...)`, antes de `if (!ok) return null`:

```kotlin
        for (name in ImportRules.missingRequired(target.required, out)) {
            invalid += InvalidRow(row.row, name, "es obligatorio: elija su columna o un valor fijo")
            ok = false
        }
```

6. En `write(...)`, el área solo donde el destino la tiene:

```kotlin
            CREATE -> {
                val record =
                    if (target == COMPONENT) {
                        versions.first(context.project, row.attributes + origin, reason, geometries)
                    } else {
                        // el área va con la marca: AreaListener omite estas escrituras (Areas.FIELD, P11)
                        val area = if (context.target.writesArea) listOfNotNull(row.areaHa?.let { Areas.FIELD to it }).toMap() else emptyMap()
                        records.create(target, row.attributes + origin + area, reason, geometries)
                    }
                if (target == CONCESSION) records.link("project", context.project, PROJECT_CONCESSION, UUID.fromString(record.id), reason)
            }
```

```kotlin
                val area =
                    if (context.target.writesArea && !Values.same(existing.attributes[Areas.FIELD], row.areaHa)) mapOf(Areas.FIELD to row.areaHa) else emptyMap()
```

El comentario de la clase `TerritoryImports` cambia «tres destinos» por «cinco destinos: project_component, land_parcel,
mining_concession y, desde F6, community y environmental_feature (reconciliados por code como las concesiones; una fila
sin code se rechaza)».

- [ ] **Paso 4: Correr las unitarias**

Run: `./gradlew test --tests 'sgspe.territory.TerritoryImportsTest' --tests 'sgspe.common.TenantModelTest'`
Expected: PASS.

- [ ] **Paso 5: Los destinos en la prueba de integración (la corre el coordinador)**

En `GdalImportApiTest.kt`, dos pruebas más:

```kotlin
    // especificación de F6 §3.4: comunidades por código desde un GeoPackage (alta, actualización con cambio de área y distancia
    // de Hausdorff, fila sin código rechazada) y desde un KMZ
    @Test
    fun `comunidades por codigo desde un GeoPackage y un KMZ`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val code = uniqueName("COM")
        val first =
            GdalFixtures.collection(
                listOf(
                    mapOf("codigo" to code, "nombre" to "Comunidad Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_126_500.0, 300.0),
                    mapOf("codigo" to null, "nombre" to "Comunidad sin código") to GdalFixtures.utmSquare(777_000.0, 9_126_500.0, 300.0)
                )
            )
        val columns = """{"code": "codigo", "name": "nombre"}"""
        val gpkg = GdalFixtures.geopackage(dir, "comunidades-1", linkedMapOf("comunidades" to first), 32717)
        val body = tree(preview(project, gpkg, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)
        assertEquals(1, body["valid"].asInt())
        assertEquals("code", body["invalid"][0]["field"].asString())
        val created = confirm(body, project, gpkg, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns)
        assertEquals(1, tree(created.text)["created"].asInt(), created.text)
        val community = tree(send("GET", "/api/objects/community/records?code=$code", null, HttpStatus.OK))["content"][0]["attributes"]
        assertEquals(9.0, community["calculated_area_ha"].asDouble(), 0.01)
        assertEquals("EPSG 32717", community["source_crs"].asString())

        val moved = GdalFixtures.collection(listOf(mapOf("codigo" to code, "nombre" to "Comunidad Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_126_500.0, 400.0)))
        val second = GdalFixtures.geopackage(dir, "comunidades-2", linkedMapOf("comunidades" to moved), 32717)
        val diff = tree(preview(project, second, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)
        val update = diff["differences"][0]
        assertEquals("UPDATE", update["outcome"].asString())
        assertEquals(9.0, update["areaHaBefore"].asDouble(), 0.01)
        assertEquals(16.0, update["areaHaAfter"].asDouble(), 0.01)
        assertTrue(update["distanceM"].asDouble() > 100, update.toString())
        assertEquals(1, tree(confirm(diff, project, second, "community", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)["updated"].asInt())

        val kmzCode = uniqueName("COMKMZ")
        val kml = GdalFixtures.collection(listOf(mapOf("Name" to kmzCode, "Description" to "Comunidad Ejemplo") to GdalFixtures.lonLatSquare(-78.48, -7.88, 0.002)))
        val kmz = GdalFixtures.kmz(dir, "comunidades", kml)
        val kmzColumns = """{"code": "Name", "name": "Description"}"""
        val kmzBody = tree(preview(project, kmz, "community", analyst.token, columns = kmzColumns).text)
        assertEquals(1, tree(confirm(kmzBody, project, kmz, "community", analyst.token, columns = kmzColumns).text)["created"].asInt())
    }

    // un elemento ambiental exige su tipo: desde una FileGDB con un valor fijo del formulario; sin tipo, la fila se rechaza
    @Test
    fun `elementos ambientales desde una FileGDB con el tipo fijo`() {
        val project = projectWithCrs()
        val analyst = member("GIS_ANALYST", project)
        val code = uniqueName("AMB")
        val features = GdalFixtures.collection(listOf(mapOf("codigo" to code, "nombre" to "Fuente de agua Ejemplo") to GdalFixtures.utmSquare(776_000.0, 9_127_000.0, 200.0)))
        val zip = GdalFixtures.fileGdbZip(dir, "elementos", features, 32717)
        val withoutType = tree(preview(project, zip, "environmental_feature", analyst.token, sourceCrs = "EPSG 32717", columns = """{"code": "codigo", "name": "nombre"}""").text)
        assertEquals("feature_type", withoutType["invalid"][0]["field"].asString())
        val columns = """{"code": "codigo", "name": "nombre", "featureType": "=FUENTE DE AGUA"}"""
        val body = tree(preview(project, zip, "environmental_feature", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)
        assertEquals(1, body["valid"].asInt())
        assertEquals(1, tree(confirm(body, project, zip, "environmental_feature", analyst.token, sourceCrs = "EPSG 32717", columns = columns).text)["created"].asInt())
        val feature = tree(send("GET", "/api/objects/environmental_feature/records?code=$code", null, HttpStatus.OK))["content"][0]
        assertEquals("FUENTE DE AGUA", feature["attributes"]["feature_type"].asString())
        assertEquals("MultiPolygon", feature["geometries"]["boundary"]["type"].asString())
    }
```

Nota: `ColumnMapping.canonical("featureType")` es `feature_type`; un valor que empieza con `=` es una constante (F2).
Si la respuesta de la API genérica no lleva las geometrías en `geometries`, se cambia esa línea por la sección que use
`TerritoryImportApiTest` para leer la geometría de un predio.

- [ ] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/TerritoryImports.kt src/main/kotlin/sgspe/common/TenantModel.kt \
  src/test/kotlin/sgspe/territory/TerritoryImportsTest.kt src/test/kotlin/sgspe/common/TenantModelTest.kt \
  src/test/kotlin/sgspe/territory/GdalImportApiTest.kt
git commit -F - <<'MSG'
feat(territory): importa territorio comunal y elementos ambientales por código

community.territory y environmental_feature.boundary se concilian por code como las concesiones;
el alta lleva nombre y, el elemento, su tipo. Un tenant sin el modelo de F6 no ofrece el destino.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t9 -m "Merge f62b/t9: destinos nuevos de importación"
```

Entrega: «integración pendiente: `GdalImportApiTest`, `TerritoryImportApiTest`».

---
### Tarea 10: La divulgación por capa (`ExportDisclosure`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/ExportDisclosure.kt`
- Test: `src/test/kotlin/sgspe/territory/ExportDisclosureTest.kt` (unitaria)

**Interfaces:**
- Consumes: `sgspe.privacy.Disclosure`; `Finding`, `toCodedException`.
- Produces: `ExportDisclosure.EXPORTABLE: Set<String>` (los 13 objetos de LYR-01 a LYR-16), `ExportDisclosure.PUBLIC`,
  `ExportDisclosure.COMPONENT = "project_component"`, `ExportDisclosure.PARCEL = "land_parcel"`,
  `ExportDisclosure.allows(objectName: String, level: Disclosure): Boolean`, `ExportDisclosure.check(objectName: String,
  level: Disclosure, requested: Map<String, Set<String>>): Map<String, Set<String>>` (los filtros fijos efectivos; `403
  DISCLOSURE_NOT_ALLOWED`), `ExportDisclosure.columns(objectName: String, level: Disclosure, candidates: List<String>):
  List<String>`.

- [ ] **Paso 1: Rama de tarea y unitaria que falla**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t10
```

`src/test/kotlin/sgspe/territory/ExportDisclosureTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.privacy.Disclosure
import wasichai.core.common.WasichaiException

// la divulgación por capa de las exportaciones geográficas (especificación de F6 §3.5 y §2.2.8, ASM-124): PUBLICO solo
// capas públicas; EXTERNO suma las internas, los accesos y los predios sin partida; las confidenciales solo en INTERNO;
// land_right nunca
class ExportDisclosureTest {
    private val publicLayers = setOf("administrative_unit", "locality", "road_access", "community", "mining_concession", "school", "health_facility")
    private val internalLayers = setOf("community_sector", "environmental_feature", "project", "project_component", "area_of_influence", "land_parcel")

    private fun code(e: WasichaiException) = Triple(e.status.value(), e.violations.single().message, e.violations.single().field)

    @Test
    fun `solo las capas LYR-01 a LYR-16, nunca land_right ni las de gestion`() {
        assertEquals(publicLayers + internalLayers, ExportDisclosure.EXPORTABLE)
        assertFalse("land_right" in ExportDisclosure.EXPORTABLE)
        assertFalse("grievance" in ExportDisclosure.EXPORTABLE)
    }

    @Test
    fun `PUBLICO solo las capas publicas`() {
        assertTrue(publicLayers.all { ExportDisclosure.allows(it, Disclosure.PUBLICO) })
        assertTrue(internalLayers.none { ExportDisclosure.allows(it, Disclosure.PUBLICO) })
        val e = assertThrows<WasichaiException> { ExportDisclosure.check("land_parcel", Disclosure.PUBLICO, emptyMap()) }
        assertEquals(Triple(403, "DISCLOSURE_NOT_ALLOWED", "disclosureLevel"), code(e))
    }

    @Test
    fun `EXTERNO sin filtro de componentes deja solo ACCESO`() {
        assertEquals(mapOf("component_type" to setOf("ACCESO")), ExportDisclosure.check("project_component", Disclosure.EXTERNO, emptyMap()))
        assertEquals(
            mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL")),
            ExportDisclosure.check("land_parcel", Disclosure.EXTERNO, emptyMap())
        )
    }

    @Test
    fun `EXTERNO con sondajes o con predios de la empresa es 403 sobre el filtro`() {
        val drill = assertThrows<WasichaiException> { ExportDisclosure.check("project_component", Disclosure.EXTERNO, mapOf("component_type" to setOf("SONDAJE"))) }
        assertEquals(Triple(403, "DISCLOSURE_NOT_ALLOWED", "filters.componentType"), code(drill))
        val company =
            assertThrows<WasichaiException> {
                ExportDisclosure.check("land_parcel", Disclosure.EXTERNO, mapOf("tenure_type" to setOf("COMUNAL", "PROPIEDAD DE LA EMPRESA")))
            }
        assertEquals(Triple(403, "DISCLOSURE_NOT_ALLOWED", "filters.tenureType"), code(company))
        assertEquals(mapOf("tenure_type" to setOf("COMUNAL")), ExportDisclosure.check("land_parcel", Disclosure.EXTERNO, mapOf("tenure_type" to setOf("COMUNAL"))))
    }

    @Test
    fun `INTERNO exporta lo pedido sin filtro fijo`() {
        val drill = mapOf("component_type" to setOf("SONDAJE", "PLATAFORMA", "CAMPAMENTO"))
        assertEquals(drill, ExportDisclosure.check("project_component", Disclosure.INTERNO, drill))
        assertEquals(emptyMap<String, Set<String>>(), ExportDisclosure.check("land_parcel", Disclosure.INTERNO, emptyMap()))
    }

    @Test
    fun `fuera de INTERNO no salen partidas ni procedencia y el proyecto solo con codigo y nombre`() {
        val parcel = listOf("code", "tenure_type", "registry_record", "registered_area_ha", "calculated_area_ha", "source_crs", "source_reference", "import_batch_id")
        assertEquals(
            listOf("code", "tenure_type", "registered_area_ha", "calculated_area_ha", "source_crs"),
            ExportDisclosure.columns("land_parcel", Disclosure.EXTERNO, parcel)
        )
        assertEquals(parcel, ExportDisclosure.columns("land_parcel", Disclosure.INTERNO, parcel))
        val community = listOf("name", "legal_entity_record", "property_record", "parent_record", "title_reference", "registered_area_ha", "code")
        assertEquals(listOf("name", "registered_area_ha", "code"), ExportDisclosure.columns("community", Disclosure.PUBLICO, community))
        assertEquals(
            listOf("code", "name"),
            ExportDisclosure.columns("project", Disclosure.EXTERNO, listOf("code", "name", "description", "working_crs", "policy_no_paid_labor"))
        )
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.ExportDisclosureTest'`
Expected: FAIL de compilación («Unresolved reference: ExportDisclosure»).

- [ ] **Paso 2: Implementar `ExportDisclosure.kt`**

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.toCodedException
import sgspe.privacy.Disclosure
import wasichai.core.common.WasichaiException

// la divulgación por capa de las exportaciones geográficas (especificación de F6 §3.5 y §2.2.8, ASM-124, deliverables.md §9):
// - PUBLICO: solo los objetos de las capas públicas (LYR-01 a 05, 07 y 16);
// - EXTERNO: además community_sector, environmental_feature, project (ubicación y área de interés, con código y nombre), los
//   accesos de project_component y area_of_influence; land_parcel de tenencia PROPIEDAD, POSESION o COMUNAL. sin filtro, el
//   filtro fijo del objeto se aplica solo (nunca salen sondajes, plataformas ni campamento en EXTERNO); pedir algo fuera de él
//   es 403 DISCLOSURE_NOT_ALLOWED sobre el filtro;
// - INTERNO: todos, sin campos quitados más allá de los permisos de campo.
// fuera de INTERNO no salen las partidas registrales (registry_record de los predios; las de community) ni la procedencia de
// una importación (source_reference, import_batch_id). land_right no se exporta nunca: no está en EXPORTABLE. puro
object ExportDisclosure {
    const val COMPONENT = "project_component"
    const val PARCEL = "land_parcel"

    val PUBLIC: Set<String> = setOf("administrative_unit", "locality", "road_access", "community", "mining_concession", "school", "health_facility")

    val EXPORTABLE: Set<String> = PUBLIC + setOf("community_sector", "environmental_feature", "project", COMPONENT, "area_of_influence", PARCEL)

    private val EXTERNAL_FIXED: Map<String, Map<String, Set<String>>> =
        mapOf(
            COMPONENT to mapOf("component_type" to setOf("ACCESO")),
            PARCEL to mapOf("tenure_type" to setOf("PROPIEDAD", "POSESION", "COMUNAL"))
        )

    // el nombre del filtro en el cuerpo, para el campo del 403
    private val FILTER_FIELDS = mapOf("component_type" to "filters.componentType", "tenure_type" to "filters.tenureType")

    private val REGISTRY: Map<String, Set<String>> =
        mapOf(PARCEL to setOf("registry_record"), "community" to setOf("legal_entity_record", "property_record", "parent_record", "title_reference"))
    private val PROVENANCE = setOf("source_reference", "import_batch_id")
    private val PROJECT_OUTSIDE = setOf("code", "name")

    fun allows(
        objectName: String,
        level: Disclosure
    ): Boolean =
        when (level) {
            Disclosure.INTERNO, Disclosure.EXTERNO -> objectName in EXPORTABLE
            Disclosure.PUBLICO -> objectName in PUBLIC
        }

    fun check(
        objectName: String,
        level: Disclosure,
        requested: Map<String, Set<String>>
    ): Map<String, Set<String>> {
        if (!allows(objectName, level)) throw notAllowed("$objectName no se exporta en ${level.name}", "disclosureLevel")
        if (level != Disclosure.EXTERNO) return requested
        val out = LinkedHashMap(requested)
        for ((field, allowed) in EXTERNAL_FIXED[objectName].orEmpty()) {
            val asked = requested[field]
            if (asked != null && !allowed.containsAll(asked)) {
                throw notAllowed("En EXTERNO, $objectName solo sale con ${allowed.joinToString(", ")}", FILTER_FIELDS.getValue(field))
            }
            out[field] = asked ?: allowed
        }
        return out
    }

    fun columns(
        objectName: String,
        level: Disclosure,
        candidates: List<String>
    ): List<String> =
        when {
            level == Disclosure.INTERNO -> candidates
            objectName == "project" -> candidates.filter { it in PROJECT_OUTSIDE }
            else -> candidates.filter { it !in REGISTRY[objectName].orEmpty() && it !in PROVENANCE }
        }

    private fun notAllowed(
        message: String,
        field: String
    ): WasichaiException = Finding("DISCLOSURE_NOT_ALLOWED", message, field, status = HttpStatus.FORBIDDEN).toCodedException()
}
```

- [ ] **Paso 3: Correr la unitaria**

Run: `./gradlew test --tests 'sgspe.territory.ExportDisclosureTest'`
Expected: PASS (7 pruebas).

- [ ] **Paso 4: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/ExportDisclosure.kt src/test/kotlin/sgspe/territory/ExportDisclosureTest.kt
git commit -F - <<'MSG'
feat(territory): divulgación por capa de las exportaciones geográficas

PUBLICO solo capas públicas, EXTERNO suma las internas con accesos y predios sin partida (filtro fijo
aplicado solo), INTERNO todo; fuera de INTERNO no salen partidas ni procedencia.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t10 -m "Merge f62b/t10: divulgación por capa"
```

---

### Tarea 11: La escritura con GDAL (`GdalWriter`), las coordenadas de la exportación y `ExportFile`

**Files:**
- Create: `src/main/kotlin/sgspe/territory/GdalWriter.kt`
- Modify: `src/main/kotlin/sgspe/territory/GeoSql.kt` (`ExportPoint`, `exportPoints`), `src/main/kotlin/sgspe/privacy/ExportFile.kt`
  (`create(..., entry)`)
- Test: `src/test/kotlin/sgspe/territory/GdalWriterTest.kt` (unitaria con GDAL), `src/test/kotlin/sgspe/privacy/ExportFileTest.kt`
  (unitaria)

**Interfaces:**
- Consumes: `GdalTools.require`, `GdalTools.run`, `GdalCommand.fromGeoJson` (Tarea 4); `GdalFixtures.info` (Tarea 7).
- Produces: `enum class GeoExportFormat(key, extension, mediaType, driver: String?, layerOptions: List<String>)` con
  `GEOJSON`, `CSV`, `XLSX`, `KML`, `GPKG`, `SHP` y `GeoExportFormat.parse(raw: Any?): GeoExportFormat?`;
  `GdalWriter.write(format: GeoExportFormat, geojson: Path, layerName: String, targetSrid: Int?, out: Path)` (bloqueante);
  `ExportPoint(latitude: BigDecimal?, longitude: BigDecimal?, srid: Int?, easting: BigDecimal?, northing: BigDecimal?, wkt:
  String?, wktUtm: String?)`; `GeoSql.exportPoints(geometries: List<Map<String, Any?>>, srid: Int?): List<ExportPoint>`;
  `ExportFile.create(filename: String, now: Instant, root: Path = tmp, entry: String = "registro.xlsx"): ExportFile`.

- [ ] **Paso 1: Rama de tarea y unitarias que fallan**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t11
```

`src/test/kotlin/sgspe/territory/GdalWriterTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import wasichai.core.common.WasichaiException
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

// la escritura de KML, GeoPackage y Shapefile desde un GeoJSON EPSG:4326 (especificación de F6 §3.5): KML siempre en grados,
// GeoPackage y Shapefile en el CRS pedido, el Shapefile en ZIP con .cpg UTF-8. predios ficticios con tildes
class GdalWriterTest {
    @TempDir
    lateinit var dir: Path

    private val writer by lazy { GdalWriter(GdalTools(GdalProperties(path = GdalFixtures.requireGdal().toString()))) }

    private fun source(): Path =
        dir.resolve("capa.geojson").also {
            Files.writeString(
                it,
                GdalFixtures.collection(
                    listOf(
                        mapOf("code" to "PRD-01", "name" to "Predio Añaño") to GdalFixtures.lonLatSquare(-78.50, -7.90, 0.001),
                        mapOf("code" to "PRD-02", "name" to "Predio Ñuñoa") to GdalFixtures.lonLatSquare(-78.49, -7.90, 0.001)
                    )
                )
            )
        }

    @Test
    fun `un GeoPackage en UTM 17S con sus dos registros`() {
        val out = dir.resolve("salida.gpkg")
        writer.write(GeoExportFormat.GPKG, source(), "land_parcel", 32717, out)
        val layer = GdalFixtures.info(out.toFile(), "GPKG").single()
        assertEquals("land_parcel", layer.name)
        assertEquals(2L, layer.featureCount)
        assertEquals("EPSG 32717", layer.detectedCrs)
    }

    @Test
    fun `un KML siempre en grados aunque se pida UTM`() {
        val out = dir.resolve("salida.kml")
        writer.write(GeoExportFormat.KML, source(), "land_parcel", 32717, out)
        val layer = GdalFixtures.info(out.toFile(), "KML").single()
        assertEquals(2L, layer.featureCount)
        assertEquals("EPSG 4326", layer.detectedCrs)
    }

    @Test
    fun `un Shapefile sale como ZIP con cpg UTF-8 y prj`() {
        val out = dir.resolve("salida.zip")
        writer.write(GeoExportFormat.SHP, source(), "land_parcel", 32717, out)
        val names = ZipFile(out.toFile()).use { zip -> zip.entries().toList().map { it.name }.toSet() }
        assertTrue(names.containsAll(setOf("land_parcel.shp", "land_parcel.shx", "land_parcel.dbf", "land_parcel.prj", "land_parcel.cpg")), names.toString())
        val cpg = ZipFile(out.toFile()).use { zip -> zip.getInputStream(zip.getEntry("land_parcel.cpg")).readAllBytes().toString(Charsets.UTF_8) }
        assertEquals("UTF-8", cpg.trim())
        val unzipped = dir.resolve("shp").also { Files.createDirectories(it) }
        ZipFile(out.toFile()).use { zip -> zip.entries().toList().forEach { e -> Files.copy(zip.getInputStream(e), unzipped.resolve(e.name)) } }
        val layer = GdalFixtures.info(unzipped.resolve("land_parcel.shp").toFile(), "ESRI Shapefile").single()
        assertEquals(2L, layer.featureCount)
        assertEquals("EPSG 32717", layer.detectedCrs)
    }

    @Test
    fun `sin GDAL es FORMAT_UNAVAILABLE`() {
        val missing = GdalWriter(GdalTools(GdalProperties(path = dir.resolve("sin-gdal").toString())))
        val e = assertThrows<WasichaiException> { missing.write(GeoExportFormat.GPKG, source(), "land_parcel", null, dir.resolve("x.gpkg")) }
        assertEquals("FORMAT_UNAVAILABLE", e.violations.single().message)
    }
}
```

En `src/test/kotlin/sgspe/privacy/ExportFileTest.kt`, una prueba más (con el estilo de las que ya tiene; `root` es la
carpeta temporal que use la clase):

```kotlin
    // las exportaciones geográficas (F6) escriben otros formatos: el archivo dentro de la carpeta privada lleva su extensión
    @Test
    fun `el archivo de dentro puede tener otro nombre`() {
        val export = ExportFile.create("predios-interno.gpkg", Instant.parse("2026-10-11T12:00:00Z"), root, entry = "capa.gpkg")
        assertEquals("capa.gpkg", export.file.name)
        assertEquals("predios-interno.gpkg", export.filename)
        export.discard()
    }
```

(Si `ExportFileTest` nombra distinto su carpeta temporal, se usa ese nombre en lugar de `root`.)

Run: `./gradlew test --tests 'sgspe.territory.GdalWriterTest' --tests 'sgspe.privacy.ExportFileTest'`
Expected: FAIL de compilación («Unresolved reference: GdalWriter», «entry»).

- [ ] **Paso 2: `ExportFile.create` con el nombre de dentro**

En `src/main/kotlin/sgspe/privacy/ExportFile.kt`:

```kotlin
        // barre las carpetas viejas y crea un archivo vacío dentro de una carpeta nueva solo de quien corre el servidor. `entry`
        // es el nombre del archivo dentro de la carpeta: el XLSX de siempre o, en las exportaciones geográficas, su formato
        fun create(
            filename: String,
            now: Instant,
            root: Path = Path.of(System.getProperty("java.io.tmpdir")),
            entry: String = "registro.xlsx"
        ): ExportFile {
            sweep(root, now)
            val directory = Files.createTempDirectory(root, PREFIX, PRIVATE_DIRECTORY)
            return ExportFile(Files.createFile(directory.resolve(entry), PRIVATE_FILE).toFile(), filename)
        }
```

- [ ] **Paso 3: Implementar `GdalWriter.kt`**

```kotlin
package sgspe.territory

import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// los formatos de una exportación geográfica (especificación de F6 §3.5): geojson, csv y xlsx los escribe el código; kml,
// gpkg y shp, GDAL desde un GeoJSON EPSG:4326 (driver de la lista blanca). shp sale como ZIP con .cpg UTF-8
enum class GeoExportFormat(
    val key: String,
    val extension: String,
    val mediaType: String,
    val driver: String?,
    val layerOptions: List<String> = emptyList()
) {
    GEOJSON("geojson", "geojson", "application/geo+json", null),
    CSV("csv", "csv", "text/csv;charset=UTF-8", null),
    XLSX("xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", null),
    KML("kml", "kml", "application/vnd.google-earth.kml+xml", "KML"),
    GPKG("gpkg", "gpkg", "application/geopackage+sqlite3", "GPKG"),
    SHP("shp", "zip", "application/zip", "ESRI Shapefile", listOf("ENCODING=UTF-8"));

    companion object {
        fun parse(raw: Any?): GeoExportFormat? = entries.firstOrNull { it.key == raw }
    }
}

// la escritura con GDAL de una exportación: ogr2ogr en una carpeta propia junto al archivo de salida (la de ExportFile, 0700),
// con argumentos fijos (GdalCommand.fromGeoJson): -nln con el nombre del objeto, -t_srs solo para gpkg y shp en UTM, KML
// siempre en grados. el resultado se mueve al archivo de salida (o se comprime, el Shapefile) y la carpeta se borra.
// bloqueante: quien llama la corre en el despachador de E/S, interrumpible y con el plazo de sgspe.territory.gdal.timeout
@Component
class GdalWriter(
    private val tools: GdalTools
) {
    fun write(
        format: GeoExportFormat,
        geojson: Path,
        layerName: String,
        targetSrid: Int?,
        out: Path
    ) {
        val driver = format.driver ?: error("$format lo escribe el código, no GDAL")
        val status = tools.require(driver, format.key)
        val work = Files.createTempDirectory(out.toAbsolutePath().parent, "gdal-")
        try {
            val target = if (format == GeoExportFormat.SHP) work.resolve("$layerName.shp") else work.resolve("capa.${format.extension}")
            val srid = targetSrid.takeIf { format != GeoExportFormat.KML }
            val command = GdalCommand.fromGeoJson(status.ogr2ogr!!, driver, geojson.toAbsolutePath(), target, layerName, srid, format.layerOptions)
            val exit = tools.run(command, work, work.resolve("ogr2ogr.out"))
            check(exit == 0 && Files.exists(target)) { "GDAL no pudo escribir ${format.key}" }
            if (format == GeoExportFormat.SHP) zip(work, layerName, out) else Files.move(target, out, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            work.toFile().deleteRecursively()
        }
    }

    private fun zip(
        dir: Path,
        layer: String,
        out: Path
    ) {
        val parts = Files.list(dir).use { files -> files.filter { it.fileName.toString().startsWith("$layer.") }.sorted().toList() }
        ZipOutputStream(Files.newOutputStream(out)).use { zip ->
            for (part in parts) {
                zip.putNextEntry(ZipEntry(part.fileName.toString()))
                Files.copy(part, zip)
                zip.closeEntry()
            }
        }
    }
}
```

- [ ] **Paso 4: `GeoSql.exportPoints`**

En `GeoSql.kt`, la clase de datos junto a `UtmPoint`:

```kotlin
// las coordenadas de un registro exportado (especificación de F6 §3.5): latitud y longitud del punto o del centroide, su este
// y norte en `srid` (el working_crs pedido o la zona UTM del centroide, ASM-064; null fuera de las zonas de trabajo) y el WKT
// de líneas y polígonos en grados y en ese UTM
data class ExportPoint(
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val srid: Int?,
    val easting: BigDecimal?,
    val northing: BigDecimal?,
    val wkt: String?,
    val wktUtm: String?
)
```

Y el método en la clase (después de `utm`), con los imports `kotlinx.coroutines.reactor.awaitSingle`:

```kotlin
    // las coordenadas de varias geometrías EPSG:4326 en una consulta por tramo (sin una consulta por registro). `srid` es la
    // zona de trabajo pedida; null, la zona del centroide de cada una. el orden de salida es el de entrada
    suspend fun exportPoints(
        geometries: List<Map<String, Any?>>,
        srid: Int?
    ): List<ExportPoint> {
        srid?.let(::requireWorkingZone)
        val out = ArrayList<ExportPoint>(geometries.size)
        for (chunk in geometries.chunked(EXPORT_CHUNK)) {
            out +=
                guarded {
                    db
                        .sql(
                            """
                            SELECT n, round(ST_Y(c)::numeric, 9) AS lat, round(ST_X(c)::numeric, 9) AS lon,
                              CASE WHEN z IN (32717, 32718, 32719) THEN z END AS z,
                              CASE WHEN z IN (32717, 32718, 32719) THEN round(ST_X(ST_Transform(c, z))::numeric, 3) END AS e,
                              CASE WHEN z IN (32717, 32718, 32719) THEN round(ST_Y(ST_Transform(c, z))::numeric, 3) END AS nn,
                              CASE WHEN ST_Dimension(g) > 0 THEN ST_AsText(g, 9) END AS wkt,
                              CASE WHEN ST_Dimension(g) > 0 AND z IN (32717, 32718, 32719) THEN ST_AsText(ST_Transform(g, z), 3) END AS wkt_utm
                            FROM (
                              SELECT n, g, c,
                                CASE WHEN :srid > 0 THEN :srid WHEN ST_Y(c) <= 0 THEN 32700 + floor((ST_X(c) + 180) / 6)::int + 1 ELSE 0 END AS z
                              FROM (
                                SELECT t.n, ST_SetSRID(ST_GeomFromGeoJSON(t.g), 4326) AS g, ST_Centroid(ST_SetSRID(ST_GeomFromGeoJSON(t.g), 4326)) AS c
                                FROM unnest(CAST(:gs AS text[])) WITH ORDINALITY AS t(g, n)
                              ) s
                            ) s2
                            ORDER BY n
                            """.trimIndent()
                        ).bind("gs", chunk.map(::json).toTypedArray())
                        .bind("srid", srid ?: 0)
                        .map { row, _ ->
                            ExportPoint(
                                latitude = row.get("lat", BigDecimal::class.java),
                                longitude = row.get("lon", BigDecimal::class.java),
                                srid = (row.get("z") as Number?)?.toInt(),
                                easting = row.get("e", BigDecimal::class.java),
                                northing = row.get("nn", BigDecimal::class.java),
                                wkt = row.get("wkt", String::class.java),
                                wktUtm = row.get("wkt_utm", String::class.java)
                            )
                        }.all()
                        .collectList()
                        .awaitSingle()
                }
        }
        return out
    }
```

y en el `companion object`: `private const val EXPORT_CHUNK = 500`. No hay unitaria de SQL: la cubre
`TerritoryExportsApiTest` (Tarea 12, CSV con latitud, este y WKT).

- [ ] **Paso 5: Correr las unitarias**

Run: `./gradlew test --tests 'sgspe.territory.GdalWriterTest' --tests 'sgspe.privacy.ExportFileTest' --tests 'sgspe.territory.GeoSqlTest'`
Expected: PASS.

- [ ] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/GdalWriter.kt src/main/kotlin/sgspe/territory/GeoSql.kt src/main/kotlin/sgspe/privacy/ExportFile.kt \
  src/test/kotlin/sgspe/territory/GdalWriterTest.kt src/test/kotlin/sgspe/privacy/ExportFileTest.kt
git commit -F - <<'MSG'
feat(territory): escritura de KML, GeoPackage y Shapefile con GDAL

GdalWriter escribe desde un GeoJSON 4326 con argumentos fijos, reproyecta solo gpkg y shp en UTM y
entrega el Shapefile en ZIP con .cpg UTF-8. GeoSql.exportPoints da latitud, UTM y WKT por tramos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t11 -m "Merge f62b/t11: escritura con GDAL"
```

---

### Tarea 12: Las exportaciones geográficas (`TerritoryExports`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/TerritoryExports.kt`
- Test: `src/test/kotlin/sgspe/territory/TerritoryExportsApiTest.kt`, `src/test/kotlin/sgspe/territory/TerritoryExportsLimitApiTest.kt`
  (integración, las corre el coordinador)

**Interfaces:**
- Consumes: `ExportDisclosure` (Tarea 10); `GeoExportFormat`, `GdalWriter.write`, `GeoSql.exportPoints`, `ExportPoint`,
  `ExportFile.create(..., entry)` (Tarea 11); `GdalProperties.timeout` (Tarea 4); `sgspe.privacy.ExportLog.export(user,
  objectName, format, disclosure, filters, rows, project)`, `sgspe.privacy.ExportProjects.resolve(user, requested):
  List<UUID>?`, `sgspe.privacy.Xlsx.write`, `sgspe.privacy.XlsxSheet`, `sgspe.privacy.Disclosure`; `Records.all`,
  `Records.related`, `Records.geometriesOf`, `Criteria.filled`, `Criteria.textIn`, `Criteria.anyOf`, `Readability`,
  `safeCell`; de `F6ApiTest`: `utmProject`, `parcel`, `component`, `SyntheticGeo`.
- Produces: `TerritoryExports.export(body: Map<String, Any?>): TerritoryExports.Export(file: ExportFile, format:
  GeoExportFormat)`; `POST /api/sgspe/territory/exports`; código nuevo `EXPORT_TIMEOUT` (`503`) y los de validación del
  cuerpo `INVALID_FORMAT`, `INVALID_COORDINATES`, `INVALID_GEOMETRY` (`400`).

- [ ] **Paso 1: Rama de tarea y la prueba de integración (la corre el coordinador)**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t12
grep -n "fun utmProject\|fun parcel\|fun component\|object SyntheticGeo\|fun square\|fun line\|fun offset\|ORIGIN" src/test/kotlin/sgspe/F6ApiTest.kt
```

Anotar si las ayudas son `suspend` y qué devuelven: las pruebas las llaman dentro de `runBlocking { … }` y con
`.toString()`, que compila en los dos casos (decisión 11).

`src/test/kotlin/sgspe/territory/TerritoryExportsApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import sgspe.F6ApiTest
import sgspe.SyntheticGeo
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile

// especificación de F6 §5 «TerritoryExportsApiTest» (RQ-GIS-018, RQ-REP-022, RQ-AUD-011): cada formato abre y trae los
// registros; predios en EXTERNO sin partida; sondajes en EXTERNO 403; sin EXPORT 403; la fila de export_log. los predios son
// del tenant y la base es compartida: cada prueba busca sus códigos dentro del archivo
class TerritoryExportsApiTest : F6ApiTest() {
    @TempDir
    lateinit var dir: File

    private class Download(
        val status: HttpStatus,
        val body: ByteArray,
        val headers: HttpHeaders
    ) {
        val text: String get() = String(body, Charsets.UTF_8)
    }

    private fun export(
        body: Map<String, Any?>,
        token: String
    ): Download {
        val result =
            client
                .post()
                .uri("/api/sgspe/territory/exports")
                .header(HttpHeaders.AUTHORIZATION, token)
                .bodyValue(body)
                .exchange()
                .expectBody(ByteArray::class.java)
                .returnResult()
        return Download(HttpStatus.valueOf(result.status.value()), result.responseBody ?: ByteArray(0), result.responseHeaders)
    }

    private fun code(download: Download) = tree(download.text)["errors"][0]["message"].asString()

    private fun saved(
        download: Download,
        name: String
    ): File = File(dir, name).apply { writeBytes(download.body) }

    // dos predios sintéticos (uno PROPIEDAD con partida, otro de la empresa) y un SOCIAL_MANAGER asignado al proyecto
    private class Setup(
        val projectId: UUID,
        val project: String,
        val manager: Account,
        val owned: String,
        val company: String
    )

    private fun setup(): Setup =
        runBlocking {
            val projectId = utmProject()
            val project = projectId.toString()
            val manager = member("SOCIAL_MANAGER", project)
            val (lon, lat) = SyntheticGeo.ORIGIN
            val owned = uniqueName("PRD-EXP")
            val company = uniqueName("PRD-EMP")
            // con partida registral (ficticia), por la API genérica: la ayuda parcel de F6ApiTest no la escribe
            createWith(
                "land_parcel",
                mapOf("code" to owned, "tenure_type" to "PROPIEDAD", "registry_record" to "P-0001-EJEMPLO"),
                mapOf("boundary" to SyntheticGeo.square(lon, lat, 100.0))
            )
            parcel(company, "PROPIEDAD DE LA EMPRESA", SyntheticGeo.square(lon + 0.01, lat, 100.0))
            Setup(projectId, project, manager, owned, company)
        }

    private fun body(
        objectName: String,
        format: String,
        level: String,
        filters: Map<String, Any?> = emptyMap(),
        coordinates: String = "LATLON"
    ) = mapOf(
        "object" to objectName,
        "format" to format,
        "coordinates" to coordinates,
        "filters" to filters,
        "disclosureLevel" to level,
        "checklistConfirmed" to (level == "EXTERNO")
    )

    @Test
    fun `GeoJSON INTERNO trae los predios con su partida y CSV EXTERNO sin partida ni predios de la empresa`() {
        val s = setup()
        val internal = export(body("land_parcel", "geojson", "INTERNO"), s.manager.token)
        assertEquals(HttpStatus.OK, internal.status, internal.text)
        assertTrue(internal.headers.contentDisposition.filename!!.endsWith(".geojson"))
        val features = tree(internal.text)["features"].values().associateBy { it["properties"]["code"].asString() }
        assertEquals("P-0001-EJEMPLO", features.getValue(s.owned)["properties"]["registry_record"].asString())
        assertTrue(s.company in features)

        val external = export(body("land_parcel", "csv", "EXTERNO"), s.manager.token)
        assertEquals(HttpStatus.OK, external.status, external.text)
        val lines = external.text.removePrefix("﻿").lines()
        val header = lines.first().split(",")
        assertFalse("registry_record" in header)
        assertTrue(header.containsAll(listOf("latitude", "longitude", "utm_zone", "easting", "northing", "wkt")))
        val mine = lines.first { it.contains(s.owned) }
        assertTrue(mine.contains("EPSG 32717"), mine)
        assertTrue(mine.contains("POLYGON"), mine)
        assertFalse(lines.any { it.contains(s.company) })
    }

    @Test
    fun `XLSX, KML, GeoPackage y Shapefile abren y traen los registros`() {
        val s = setup()
        val filters = mapOf("project" to s.project)
        val xlsx = export(body("land_parcel", "xlsx", "INTERNO", filters, coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.OK, xlsx.status, xlsx.text)
        XSSFWorkbook(ByteArrayInputStream(xlsx.body)).use { book ->
            val sheet = book.getSheetAt(0)
            val cells = (0..sheet.lastRowNum).flatMap { r -> sheet.getRow(r)?.let { row -> (0 until row.lastCellNum).mapNotNull { row.getCell(it)?.toString() } }.orEmpty() }
            assertTrue(s.owned in cells)
            assertTrue("easting" in cells && "wkt" in cells)
        }
        val kml = export(body("land_parcel", "kml", "INTERNO"), s.manager.token)
        assertEquals(HttpStatus.OK, kml.status, kml.text)
        assertTrue(GdalFixtures.info(saved(kml, "predios.kml"), "KML").single().featureCount!! >= 2)
        val gpkg = export(body("land_parcel", "gpkg", "INTERNO", filters, coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.OK, gpkg.status, gpkg.text)
        assertEquals("EPSG 32717", GdalFixtures.info(saved(gpkg, "predios.gpkg"), "GPKG").single().detectedCrs)
        val shp = export(body("land_parcel", "shp", "INTERNO", filters, coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.OK, shp.status, shp.text)
        val names = ZipFile(saved(shp, "predios.zip")).use { zip -> zip.entries().toList().map { it.name }.toSet() }
        assertTrue(names.containsAll(setOf("land_parcel.shp", "land_parcel.dbf", "land_parcel.cpg", "land_parcel.prj")), names.toString())
    }

    @Test
    fun `componentes en EXTERNO sin filtro solo accesos, y sondajes 403`() {
        val s = setup()
        val (lon, lat) = SyntheticGeo.ORIGIN
        val access = uniqueName("ACC")
        val drill = uniqueName("DDH")
        runBlocking {
            component(s.projectId, access, "ACCESO", SyntheticGeo.line(lon to lat, lon + 0.002 to lat))
            // un sondaje con traza (una perforación inclinada dibujada como línea): sale por la misma geometría que el acceso,
            // así que solo el filtro fijo de EXTERNO lo deja fuera
            component(s.projectId, drill, "SONDAJE", SyntheticGeo.line(lon to lat + 0.001, lon + 0.002 to lat + 0.001))
        }
        val filters = mapOf("project" to s.project)
        val external = export(body("project_component", "geojson", "EXTERNO", filters) + ("geometry" to "trace"), s.manager.token)
        assertEquals(HttpStatus.OK, external.status, external.text)
        val codes = tree(external.text)["features"].values().map { it["properties"]["code"].asString() }
        assertEquals(listOf(access), codes)
        val drills = export(body("project_component", "geojson", "EXTERNO", filters + ("componentType" to listOf("SONDAJE"))) + ("geometry" to "trace"), s.manager.token)
        val internal = export(body("project_component", "geojson", "INTERNO", filters) + ("geometry" to "trace"), s.manager.token)
        assertEquals(setOf(access, drill), tree(internal.text)["features"].values().map { it["properties"]["code"].asString() }.toSet())
        assertEquals(HttpStatus.FORBIDDEN, drills.status)
        assertEquals("DISCLOSURE_NOT_ALLOWED", code(drills))
    }

    @Test
    fun `sin EXPORT es 403, un objeto fuera de las capas es 400 y PUBLICO de predios es 403`() {
        val s = setup()
        // STAKEHOLDER_MANAGER lee predios y crea export_log, pero no tiene EXPORT sobre land_parcel (roles.json de 6.1)
        val reader = member("STAKEHOLDER_MANAGER", s.project)
        assertEquals(HttpStatus.FORBIDDEN, export(body("land_parcel", "geojson", "INTERNO"), reader.token).status)
        val rights = export(body("land_right", "geojson", "INTERNO"), s.manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, rights.status)
        assertEquals("OBJECT_NOT_EXPORTABLE", code(rights))
        assertEquals("DISCLOSURE_NOT_ALLOWED", code(export(body("land_parcel", "geojson", "PUBLICO"), s.manager.token)))
        val unchecked = export(body("land_parcel", "geojson", "EXTERNO") + ("checklistConfirmed" to false), s.manager.token)
        assertEquals("CHECKLIST_REQUIRED", code(unchecked))
        val utmWithout = export(body("land_parcel", "gpkg", "INTERNO", coordinates = "UTM"), s.manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, utmWithout.status)
        assertEquals(listOf("filters.project"), problemFields(utmWithout.text))
    }

    @Test
    fun `cada exportacion deja su fila en export_log`() {
        val s = setup()
        assertEquals(HttpStatus.OK, export(body("land_parcel", "kml", "INTERNO", mapOf("project" to s.project)), s.manager.token).status)
        val logs =
            tree(send("GET", "/api/objects/export_log/records?exporter_user_email=${s.manager.email}&object_name=land_parcel", null, HttpStatus.OK))["content"]
        val row = logs.values().single()["attributes"]
        assertEquals("KML", row["file_format"].asString())
        assertEquals("INTERNO", row["disclosure_level"].asString())
        assertTrue(row["row_count"].asInt() >= 2)
        assertTrue(row["filters_description"].asString().contains("geometría=boundary"), row.toString())
    }
}
```

`src/test/kotlin/sgspe/territory/TerritoryExportsLimitApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import sgspe.F6ApiTest
import sgspe.SyntheticGeo

// el tope de filas de una exportación (sgspe.exports.max-rows): pasarse es 400 TOO_MANY_RECORDS sobre filters.project y no
// escribe export_log. con componentes de un proyecto propio, para no depender de lo que otras pruebas dejaron en el tenant
@TestPropertySource(properties = ["sgspe.exports.max-rows=3"])
class TerritoryExportsLimitApiTest : F6ApiTest() {
    @Test
    fun `mas filas que el tope es 400 TOO_MANY_RECORDS`() {
        val projectId = runBlocking { utmProject() }
        val project = projectId.toString()
        val manager = member("SOCIAL_MANAGER", project)
        val (lon, lat) = SyntheticGeo.ORIGIN
        runBlocking { repeat(4) { component(projectId, uniqueName("DDH"), "SONDAJE", SyntheticGeo.point(lon + it * 0.001, lat)) } }
        val body =
            mapOf(
                "object" to "project_component",
                "geometry" to "location",
                "format" to "geojson",
                "coordinates" to "LATLON",
                "filters" to mapOf("project" to project),
                "disclosureLevel" to "INTERNO"
            )
        val response = exchange("POST", "/api/sgspe/territory/exports", body, manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("TOO_MANY_RECORDS", tree(response.body)["errors"][0]["message"].asString())
        assertEquals("filters.project", tree(response.body)["errors"][0]["field"].asString())
        val logs = tree(send("GET", "/api/objects/export_log/records?exporter_user_email=${manager.email}", null, HttpStatus.OK))["totalElements"].asInt()
        assertEquals(0, logs)
    }
}
```

Notas para el implementador: si `SyntheticGeo` no está en el paquete `sgspe` (contrato: «en el mismo archivo» que
`F6ApiTest`), se ajusta el import. Si `POST /api/sgspe/project-components` no admite una línea para un `SONDAJE`, el
componente de la segunda prueba pasa a `PLATAFORMA` con la misma línea: lo que se prueba es que en EXTERNO solo sale
`ACCESO`.

- [ ] **Paso 2: Implementar `TerritoryExports.kt`**

```kotlin
package sgspe.territory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Flux
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.safeCell
import sgspe.common.toCodedException
import sgspe.privacy.Disclosure
import sgspe.privacy.ExportFile
import sgspe.privacy.ExportLog
import sgspe.privacy.ExportProjects
import sgspe.privacy.Xlsx
import sgspe.privacy.XlsxSheet
import tools.jackson.databind.json.JsonMapper
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.UUID

// POST /api/sgspe/territory/exports (api.md §4, gis.md §4.6, especificación de F6 §3.5, RQ-GIS-018, ASM-124): los objetos de
// LYR-01 a LYR-16 con una de sus geometrías, en geojson y kml (4326), gpkg y shp (UTM del proyecto filtrado o 4326) o csv y
// xlsx (latitud, longitud, este y norte del punto o del centroide y WKT). antes de leer nada exige READ y EXPORT sobre el
// objeto y CREATE sobre export_log; lee por Records como quien exporta (permisos de campo, alcance de proyecto) con el tope
// sgspe.exports.max-rows; aplica la divulgación por capa (ExportDisclosure) y deja su fila de export_log antes de entregar el
// archivo, como CommitmentMatrix. los proyectos siguen ExportProjects. una exportación no crea registros de negocio: sin
// Idempotency-Key (api.md §3.5)
@Service
class TerritoryExports(
    private val records: Records,
    private val permissions: Permissions,
    private val callers: Callers,
    private val catalog: ModelCatalog,
    private val metadata: MetadataService,
    private val log: ExportLog,
    private val scope: ExportProjects,
    private val geo: GeoSql,
    private val writer: GdalWriter,
    private val gdal: GdalProperties,
    private val clock: Clock,
    @Value("\${sgspe.exports.max-rows:20000}") private val maxRows: Int
) {
    class Export(
        val file: ExportFile,
        val format: GeoExportFormat
    )

    suspend fun export(body: Map<String, Any?>): Export {
        val user = callers.require()
        StrictBody.requireOnly(body, BODY)
        val objectName = body["object"] as? String
        if (objectName == null || objectName !in ExportDisclosure.EXPORTABLE) {
            throw bad("OBJECT_NOT_EXPORTABLE", "Se exportan los objetos de las capas LYR-01 a LYR-16: ${ExportDisclosure.EXPORTABLE.sorted().joinToString(", ")}", "object")
        }
        permissions.require(user, "Exportar $objectName", Actions.READ to objectName, EXPORT to objectName, Actions.CREATE to ExportLog.OBJECT)
        val format = GeoExportFormat.parse(body["format"]) ?: throw bad("INVALID_FORMAT", "El formato es ${GeoExportFormat.entries.joinToString(", ") { it.key }}", "format")
        val coordinates = body["coordinates"] ?: LATLON
        if (coordinates != UTM && coordinates != LATLON) throw bad("INVALID_COORDINATES", "coordinates es UTM o LATLON", "coordinates")
        val disclosure =
            Disclosure.parse(body["disclosureLevel"])
                ?: throw bad("INVALID_DISCLOSURE_LEVEL", "El nivel de divulgación debe ser INTERNO, EXTERNO o PUBLICO", "disclosureLevel")
        if (disclosure == Disclosure.EXTERNO && body["checklistConfirmed"] != true) {
            throw bad("CHECKLIST_REQUIRED", "Una exportación EXTERNO exige confirmar la lista de verificación (ASM-037)", "checklistConfirmed")
        }
        val filters = StrictBody.objectOf(body, "filters")
        StrictBody.requireOnly(filters, FILTERS, "filters.")
        val requested =
            buildMap {
                texts(filters["componentType"], "filters.componentType", objectName, ExportDisclosure.COMPONENT, "component_type")?.let { put("component_type", it) }
                texts(filters["tenureType"], "filters.tenureType", objectName, ExportDisclosure.PARCEL, "tenure_type")?.let { put("tenure_type", it) }
            }
        val fixed = ExportDisclosure.check(objectName, disclosure, requested)
        val geometry = geometryOf(objectName, body["geometry"])
        val requestedProjects = uuids(filters["project"])
        val projects = scope.resolve(user, requestedProjects)
        val utm = if (coordinates == UTM) workingSrid(requestedProjects) else null
        val rows = read(objectName, geometry, fixed, projects)
        val readability = Readability(metadata)
        val candidates = columnsOf(objectName).filter { readability.canRead("$objectName.$it") }
        val columns = ExportDisclosure.columns(objectName, disclosure, candidates)
        val filename = "${objectName.replace('_', '-')}-${disclosure.name.lowercase()}.${format.extension}"
        val export = withContext(Dispatchers.IO) { ExportFile.create(filename, clock.instant(), entry = "capa.${format.extension}") }
        try {
            write(format, export.file.toPath(), objectName, geometry, rows, columns, utm)
            val description = description(objectName, geometry, format, coordinates.toString(), projects, fixed, disclosure)
            log.export(user, objectName, format.key.uppercase(), disclosure, description, rows.size, projects?.singleOrNull())
            return Export(export, format)
        } catch (e: Throwable) {
            export.discard()
            throw e
        }
    }

    // ---- lectura ----

    private suspend fun read(
        objectName: String,
        geometry: String,
        fixed: Map<String, Set<String>>,
        projects: List<UUID>?
    ): List<RecordResponse> {
        val criteria = mutableListOf<RecordCriterion>(Criteria.filled(geometry))
        fixed.forEach { (field, values) -> criteria += Criteria.textIn(field, values) }
        var ids: List<UUID>? = null
        if (projects != null) {
            when (objectName) {
                ExportDisclosure.COMPONENT, "area_of_influence" -> criteria += Criteria.anyOf("project", projects)
                "project" -> ids = projects
                "mining_concession" -> ids = projects.flatMap { records.related("project", it, "project_concession") }.mapNotNull { Records.uuid(it.id) }.distinct()
            }
        }
        return records.all(objectName, criteria = criteria, ids = ids, max = maxRows, field = TOO_MANY_FIELD)
    }

    private suspend fun workingSrid(requested: List<UUID>): Int {
        val project =
            requested.singleOrNull()
                ?: throw ValidationException("Proyecto requerido", "filters.project", "con coordenadas UTM, filtre un solo proyecto: su CRS de trabajo es el de la exportación")
        return Crs.srid(Values.text(records.get("project", project).attributes["working_crs"]))?.takeIf(Crs::isWorkingZone)
            ?: throw ValidationException("Proyecto sin CRS de trabajo", "filters.project", "el proyecto no tiene working_crs: exporte en LATLON")
    }

    // ---- escritura ----

    private suspend fun write(
        format: GeoExportFormat,
        out: Path,
        objectName: String,
        geometry: String,
        rows: List<RecordResponse>,
        columns: List<String>,
        utm: Int?
    ) {
        val shapes = rows.map { Records.geometriesOf(it)[geometry]!! }
        when (format) {
            GeoExportFormat.CSV, GeoExportFormat.XLSX -> {
                val points = geo.exportPoints(shapes, utm)
                val header = listOf("id") + columns + listOf("latitude", "longitude", "utm_zone", "easting", "northing", "wkt")
                val lines =
                    rows.mapIndexed { i, record ->
                        val point = points[i]
                        listOf<Any?>(record.id) + columns.map { record.attributes[it] } +
                            listOf(point.latitude, point.longitude, point.srid?.let(Crs::value), point.easting, point.northing, if (utm != null) point.wktUtm else point.wkt)
                    }
                withContext(Dispatchers.IO) {
                    if (format == GeoExportFormat.CSV) {
                        Csv.write(header, lines, out)
                    } else {
                        Files.newOutputStream(out).use { Xlsx.write(listOf(XlsxSheet(objectName, header, lines.asSequence())), it) }
                    }
                }
            }
            else -> {
                val features =
                    rows.mapIndexed { i, record ->
                        val properties = columns.associateWith { record.attributes[it] } + listOfNotNull(record.state?.let { "workflow_state" to it })
                        mapOf("type" to "Feature", "id" to record.id, "geometry" to shapes[i], "properties" to properties)
                    }
                val collection = mapOf("type" to "FeatureCollection", "features" to features)
                if (format == GeoExportFormat.GEOJSON) {
                    withContext(Dispatchers.IO) { JSON.writeValue(out.toFile(), collection) }
                } else {
                    gdalWrite(format, collection, objectName, utm, out)
                }
            }
        }
    }

    private suspend fun gdalWrite(
        format: GeoExportFormat,
        collection: Map<String, Any?>,
        objectName: String,
        utm: Int?,
        out: Path
    ) {
        val source = out.resolveSibling("fuente.geojson")
        try {
            withContext(Dispatchers.IO) { JSON.writeValue(source.toFile(), collection) }
            withTimeout(gdal.timeout.toMillis()) { runInterruptible(Dispatchers.IO) { writer.write(format, source, objectName, utm, out) } }
        } catch (_: TimeoutCancellationException) {
            throw Finding("EXPORT_TIMEOUT", "La exportación tardó más de ${gdal.timeout.seconds} s", "format", status = HttpStatus.SERVICE_UNAVAILABLE).toCodedException()
        } finally {
            withContext(Dispatchers.IO) { Files.deleteIfExists(source) }
        }
    }

    // ---- el cuerpo ----

    // los campos del objeto (sin las geometrías) y sus relaciones MANY_TO_ONE, en el orden del modelo
    private fun columnsOf(objectName: String): List<String> =
        catalog.objectOrNull(objectName)!!.fields.values.filter { it.type != "GEOMETRY" }.map { it.name } + catalog.manyToOneFields(objectName).map { it.first }

    private fun geometryOf(
        objectName: String,
        raw: Any?
    ): String {
        val geometries = catalog.objectOrNull(objectName)!!.fields.values.filter { it.type == "GEOMETRY" }.map { it.name }
        val chosen = raw as? String ?: geometries.singleOrNull()
        return chosen?.takeIf { it in geometries }
            ?: throw bad("INVALID_GEOMETRY", "Elija la geometría de $objectName: ${geometries.joinToString(", ")}", "geometry")
    }

    private fun texts(
        raw: Any?,
        field: String,
        objectName: String,
        owner: String,
        enumField: String
    ): Set<String>? {
        if (raw == null) return null
        if (objectName != owner) throw bad("INVALID_FILTER", "El filtro $field es solo de $owner", field)
        val values = (raw as? List<*> ?: listOf(raw)).map { it as? String ?: throw bad("INVALID_FILTER", "El filtro $field es una lista de textos", field) }
        val known = catalog.enumOf(owner, enumField)
        values.firstOrNull { it !in known }?.let { throw bad("INVALID_FILTER", "El filtro $field admite ${known.joinToString(", ")}", field) }
        return values.toSet()
    }

    private fun uuids(raw: Any?): List<UUID> {
        val values =
            when (raw) {
                null -> emptyList()
                is List<*> -> raw
                else -> listOf(raw)
            }
        return values.map { Records.uuid(it) ?: throw bad("INVALID_FILTER", "El filtro filters.project debe ser un UUID o una lista de UUID", "filters.project") }.distinct()
    }

    private fun description(
        objectName: String,
        geometry: String,
        format: GeoExportFormat,
        coordinates: String,
        projects: List<UUID>?,
        fixed: Map<String, Set<String>>,
        disclosure: Disclosure
    ): String =
        buildString {
            append("filtros: objeto=").append(objectName)
            append("; geometría=").append(geometry)
            append("; formato=").append(format.key)
            append("; coordenadas=").append(coordinates)
            append("; proyectos=").append(projects?.joinToString(",") ?: "todos")
            fixed.toSortedMap().forEach { (field, values) -> append("; ").append(field).append('=').append(values.sorted().joinToString(",")) }
            if (disclosure == Disclosure.EXTERNO) append("; lista de verificación de EXTERNO confirmada")
        }

    private fun bad(
        code: String,
        message: String,
        field: String
    ) = Finding(code, message, field, status = HttpStatus.BAD_REQUEST).toCodedException()

    companion object {
        const val EXPORT = "EXPORT"
        const val UTM = "UTM"
        const val LATLON = "LATLON"
        private const val TOO_MANY_FIELD = "filters.project"
        private val BODY = setOf("object", "geometry", "format", "coordinates", "filters", "disclosureLevel", "checklistConfirmed")
        private val FILTERS = setOf("project", "componentType", "tenureType")
        private val JSON: JsonMapper = JsonMapper.builder().build()
    }
}

// CSV RFC 4180 con BOM UTF-8 (Excel lo abre con las tildes), coma de separador y punto decimal; cada texto pasa por safeCell
// (security.md §11.4: un código o un nombre que vienen de un archivo importado no deben volverse fórmula)
internal object Csv {
    fun write(
        header: List<String>,
        rows: List<List<Any?>>,
        out: Path
    ) {
        Files.newBufferedWriter(out, Charsets.UTF_8).use { writer ->
            writer.write("﻿")
            for (line in listOf(header) + rows) {
                writer.write(line.joinToString(",") { cell(it) })
                writer.write("\r\n")
            }
        }
    }

    private fun cell(value: Any?): String =
        when (value) {
            null -> ""
            is BigDecimal -> value.toPlainString()
            is Number -> value.toString()
            else -> quote(safeCell(value.toString()))
        }

    private fun quote(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}

// la respuesta: el archivo en streaming con su fila de export_log ya escrita; se borra al terminar el envío (ExportFile)
@RestController
@RequestMapping("/api/sgspe/territory/exports")
class TerritoryExportsController(
    private val exports: TerritoryExports
) {
    @PostMapping
    suspend fun export(
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): ResponseEntity<Flux<DataBuffer>> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val export = exports.export(body)
        return ResponseEntity
            .ok()
            .contentType(MediaType.parseMediaType(export.format.mediaType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${export.file.filename}\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(export.file.stream())
    }
}
```

Notas: `Criteria.filled(geometry)` es `400` si quien exporta no lee esa geometría (no se exporta lo que no se ve). La
divulgación se aplica antes de leer (`check`) y al escribir (`columns`), así que lo que no sale nunca se lee en un
filtro. `PUBLICO` sobre las capas públicas no tiene filtro fijo.

- [ ] **Paso 3: Build y vigía de dependencias**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
grep -rn "import sgspe.territory" src/main/kotlin/sgspe/privacy | head
```

Expected: `BUILD SUCCESSFUL` (`ArchitectureTest` incluida: `privacy` sigue sin depender de `territory`); el `grep` sin
salida.

- [ ] **Paso 4: Commit**

```bash
git add src/main/kotlin/sgspe/territory/TerritoryExports.kt src/test/kotlin/sgspe/territory/TerritoryExportsApiTest.kt \
  src/test/kotlin/sgspe/territory/TerritoryExportsLimitApiTest.kt
git commit -F - <<'MSG'
feat(territory): exportaciones geográficas con divulgación por capa

POST /api/sgspe/territory/exports exporta las capas LYR-01 a LYR-16 en GeoJSON, CSV, XLSX, KML,
GeoPackage y Shapefile, como quien exporta, con EXPORT, divulgación por capa y su fila de export_log.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t12 -m "Merge f62b/t12: exportaciones geográficas"
```

Entrega: «integración pendiente: `TerritoryExportsApiTest`, `TerritoryExportsLimitApiTest`, `CommitmentMatrixApiTest`,
`StakeholderRegisterApiTest`» (las dos últimas vigilan el cambio de `ExportFile`).

---
### Tarea 13: La validación previa de una geometría (`GeometryChecks`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/GeometryChecks.kt`
- Modify: `src/main/kotlin/sgspe/territory/GeoSql.kt` (`repair`)
- Test: `src/test/kotlin/sgspe/territory/GeometryCheckRulesTest.kt` (unitaria), `src/test/kotlin/sgspe/territory/GeometryChecksApiTest.kt`
  (integración, la corre el coordinador)

**Interfaces:**
- Consumes: `GeoSql.validity`, `asType`, `centroid`, `areaHa`, `zoneOf`, `InvalidGeometryException`, `ImportRules.unprocessable`,
  `ImportProperties` (envolvente); `ObjectDefinitions.current`, `wasichai.gis.geometryType` (extensión de `CustomField`),
  `wasichai.gis.GeometryType`; `Permissions.require`, `Records.get`.
- Produces: `GeoSql.repair(geojson: Map<String, Any?>, dimension: Int): Map<String, Any?>?`;
  `GeometryCheckRules.geoJsonType(type: GeometryType): String`, `GeometryCheckRules.extractDimension(type: GeometryType):
  Int`, `GeometryCheckRules.outOfRegion(centroid: Pair<Double, Double>?, properties: ImportProperties): Boolean`,
  `GeometryCheckRules.outOfZone(centroid: Pair<Double, Double>?, workingSrid: Int?): Boolean`; `GeometryZone(srid,
  outOfZone, outOfRegion)`, `GeometryCheck(valid, reason, typeMatches, normalized, repaired, areaHaBefore, areaHaAfter,
  zone)`; `POST /api/sgspe/territory/geometry-checks`.

- [ ] **Paso 1: Rama de tarea y unitaria que falla**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git switch -c f62b/t13
```

`src/test/kotlin/sgspe/territory/GeometryCheckRulesTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.gis.GeometryType

// las reglas puras de la validación previa (gis.md §4.3 y §8, especificación de F6 §3.6)
class GeometryCheckRulesTest {
    private val properties = ImportProperties()

    @Test
    fun `el tipo GeoJSON y la dimension que se extrae de la reparacion`() {
        assertEquals("MultiPolygon", GeometryCheckRules.geoJsonType(GeometryType.MULTIPOLYGON))
        assertEquals("LineString", GeometryCheckRules.geoJsonType(GeometryType.LINESTRING))
        assertEquals(3, GeometryCheckRules.extractDimension(GeometryType.POLYGON))
        assertEquals(2, GeometryCheckRules.extractDimension(GeometryType.MULTILINESTRING))
        assertEquals(1, GeometryCheckRules.extractDimension(GeometryType.POINT))
    }

    @Test
    fun `fuera de la envolvente del Peru y fuera de la zona del proyecto`() {
        assertFalse(GeometryCheckRules.outOfRegion(-78.50 to -7.90, properties))
        assertTrue(GeometryCheckRules.outOfRegion(10.0 to 10.0, properties))
        assertTrue(GeometryCheckRules.outOfRegion(null, properties))
        assertFalse(GeometryCheckRules.outOfZone(-78.50 to -7.90, 32717))
        assertTrue(GeometryCheckRules.outOfZone(-75.50 to -7.90, 32717))
        assertFalse(GeometryCheckRules.outOfZone(-75.50 to -7.90, null))
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.GeometryCheckRulesTest'`
Expected: FAIL de compilación («Unresolved reference: GeometryCheckRules»).

- [ ] **Paso 2: `GeoSql.repair`**

En `GeoSql.kt`, después de `asType`:

```kotlin
    // la reparación propuesta (RQ-GIS-011, gis.md §4.3): ST_MakeValid y, de lo que quede, solo las partes de la dimensión del
    // campo (1 puntos, 2 líneas, 3 polígonos; ST_CollectionExtract). null si no queda nada. no escribe: la persona la acepta
    @Suppress("UNCHECKED_CAST")
    suspend fun repair(
        geojson: Map<String, Any?>,
        dimension: Int
    ): Map<String, Any?>? {
        val out =
            guarded {
                db
                    .sql(
                        "SELECT CASE WHEN ST_IsEmpty(r) THEN NULL ELSE ST_AsGeoJSON(r, 9) END AS out " +
                            "FROM (SELECT ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(:g), 4326)), :dim) AS r) s"
                    ).bind("g", json(geojson))
                    .bind("dim", dimension)
                    .map { row, _ -> Optional.ofNullable(row.get("out", String::class.java)) }
                    .one()
                    .awaitSingleOrNull()
                    ?.orElse(null)
            } ?: return null
        return JSON.readValue(out, Map::class.java) as Map<String, Any?>
    }
```

- [ ] **Paso 3: Implementar `GeometryChecks.kt`**

```kotlin
package sgspe.territory

import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Values
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.gis.GeometryType
import wasichai.gis.geometryType
import java.math.BigDecimal

data class GeometryZone(
    val srid: Int?,
    val outOfZone: Boolean,
    val outOfRegion: Boolean
)

data class GeometryCheck(
    val valid: Boolean,
    val reason: String?,
    val typeMatches: Boolean,
    val normalized: Map<String, Any?>?,
    val repaired: Map<String, Any?>?,
    val areaHaBefore: BigDecimal?,
    val areaHaAfter: BigDecimal?,
    val zone: GeometryZone
)

// las reglas puras de la validación previa: el tipo GeoJSON de un campo, qué se extrae de la reparación y la zona y la región
// (gis.md §4.3: envolvente del Perú de ImportProperties; zona del working_crs del proyecto)
object GeometryCheckRules {
    fun geoJsonType(type: GeometryType): String =
        when (type) {
            GeometryType.POINT -> "Point"
            GeometryType.LINESTRING -> "LineString"
            GeometryType.POLYGON -> "Polygon"
            GeometryType.MULTIPOINT -> "MultiPoint"
            GeometryType.MULTILINESTRING -> "MultiLineString"
            GeometryType.MULTIPOLYGON -> "MultiPolygon"
        }

    fun extractDimension(type: GeometryType): Int =
        when (type) {
            GeometryType.POINT, GeometryType.MULTIPOINT -> 1
            GeometryType.LINESTRING, GeometryType.MULTILINESTRING -> 2
            GeometryType.POLYGON, GeometryType.MULTIPOLYGON -> 3
        }

    fun outOfRegion(
        centroid: Pair<Double, Double>?,
        properties: ImportProperties
    ): Boolean {
        val (x, y) = centroid ?: return true
        return x < properties.minLongitude || x > properties.maxLongitude || y < properties.minLatitude || y > properties.maxLatitude
    }

    fun outOfZone(
        centroid: Pair<Double, Double>?,
        workingSrid: Int?
    ): Boolean = workingSrid != null && (centroid == null || GeoSql.zoneOf(centroid.first, centroid.second) != workingSrid)
}

// POST /api/sgspe/territory/geometry-checks (especificación de F6 §3.6, gis.md §8, RQ-GIS-011): antes de guardar una geometría
// editada (S13, S14), su tipo contra el geometryType del campo, ST_IsValidReason en español, la reparación propuesta con
// ST_MakeValid y su cambio de área en el CRS de cálculo (el working_crs del proyecto o la zona del centroide, ASM-064), la
// normalización de un MultiPolygon de una parte y la zona y la región. no escribe nada; exige READ sobre el objeto
@Service
class GeometryChecks(
    private val geo: GeoSql,
    private val callers: Callers,
    private val permissions: Permissions,
    private val definitions: ObjectDefinitions,
    private val records: Records,
    private val properties: ImportProperties
) {
    suspend fun check(body: Map<String, Any?>): GeometryCheck {
        StrictBody.requireOnly(body, setOf("geometry", "object", "field", "project"))
        val user = callers.require()
        val objectName = body["object"] as? String ?: throw ValidationException("Falta el objeto", "object", "es el objeto del campo")
        permissions.require(user, "Validar una geometría de $objectName", Actions.READ to objectName)
        val fieldName = body["field"] as? String ?: throw ValidationException("Falta el campo", "field", "es el campo de geometría")
        val expected =
            definitions.current(user.organizationId, objectName).fields.firstOrNull { it.name == fieldName }?.geometryType
                ?: throw ValidationException("Campo inválido", "field", "no es una geometría de $objectName")
        val geometry =
            StrictBody.objectOf(body, "geometry").takeIf { it["type"] is String }
                ?: throw ValidationException("Geometría inválida", "geometry", "debe ser un GeoJSON con type y coordinates")
        val workingSrid =
            body["project"]?.let { raw ->
                val id = Records.uuid(raw) ?: throw ValidationException("Proyecto inválido", "project", "debe ser el id de un proyecto")
                Crs.srid(Values.text(records.get("project", id).attributes["working_crs"]))?.takeIf(Crs::isWorkingZone)
            }
        return try {
            val type = GeometryCheckRules.geoJsonType(expected)
            val validity = geo.validity(geometry)
            val normalized = geo.asType(geometry, type)
            val repaired =
                if (validity.valid) null else geo.repair(geometry, GeometryCheckRules.extractDimension(expected))?.let { geo.asType(it, type) ?: it }
            val centroid = geo.centroid(geometry)
            val srid = workingSrid ?: centroid?.let { GeoSql.zoneOf(it.first, it.second) }
            GeometryCheck(
                valid = validity.valid,
                reason = validity.reason,
                typeMatches = normalized != null,
                normalized = normalized,
                repaired = repaired,
                areaHaBefore = srid?.let { geo.areaHa(geometry, it) },
                areaHaAfter = if (repaired != null && srid != null) geo.areaHa(repaired, srid) else null,
                zone = GeometryZone(srid, GeometryCheckRules.outOfZone(centroid, workingSrid), GeometryCheckRules.outOfRegion(centroid, properties))
            )
        } catch (e: InvalidGeometryException) {
            throw ValidationException("Geometría inválida", "geometry", ImportRules.unprocessable(e))
        }
    }
}

@RestController
@RequestMapping("/api/sgspe/territory/geometry-checks")
class GeometryChecksController(
    private val checks: GeometryChecks
) {
    @PostMapping
    suspend fun check(
        @RequestBody body: Map<String, Any?>,
        exchange: ServerWebExchange
    ): GeometryCheck {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return checks.check(body)
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.GeometryCheckRulesTest' --tests 'sgspe.territory.GeoSqlTest'`
Expected: PASS.

- [ ] **Paso 4: La prueba de integración (la corre el coordinador)**

`src/test/kotlin/sgspe/territory/GeometryChecksApiTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.DomainApiTest

// gis.md §9 «Polígono inválido» y especificación de F6 §5 «GeometryChecksApiTest» (RQ-GIS-011, RQ-GIS-020): lazo inválido con
// su motivo y la reparación con su cambio de área; MultiPolygon de una parte normalizado; fuera de zona y de región; no
// escribe. geometrías sintéticas cerca de (-78.50, -7.90), UTM 17S
class GeometryChecksApiTest : DomainApiTest() {
    private fun check(
        body: Map<String, Any?>,
        token: String = this.token
    ) = exchange("POST", "/api/sgspe/territory/geometry-checks", body, token)

    private val bowtie =
        mapOf(
            "type" to "Polygon",
            "coordinates" to listOf(listOf(listOf(-78.50, -7.90), listOf(-78.49, -7.89), listOf(-78.49, -7.90), listOf(-78.50, -7.89), listOf(-78.50, -7.90)))
        )

    @Test
    fun `un lazo es invalido con su motivo y la reparacion cambia el area`() {
        val project = projectWithCrs()
        val response = check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary", "project" to project))
        assertEquals(HttpStatus.OK, response.status, response.body)
        val body = tree(response.body)
        assertFalse(body["valid"].asBoolean())
        assertEquals("autointersección", body["reason"].asString())
        assertEquals("MultiPolygon", body["repaired"]["type"].asString())
        assertTrue(body["areaHaAfter"].asDouble() > body["areaHaBefore"].asDouble() + 10, body.toString())
        assertEquals(32717, body["zone"]["srid"].asInt())
        assertFalse(body["zone"]["outOfZone"].asBoolean())
        val parcels = tree(send("GET", "/api/objects/land_parcel/records?size=1", null, HttpStatus.OK))["totalElements"].asLong()
        check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary"))
        assertEquals(parcels, tree(send("GET", "/api/objects/land_parcel/records?size=1", null, HttpStatus.OK))["totalElements"].asLong())
    }

    @Test
    fun `un MultiPolygon de una parte se normaliza y un punto no es del tipo`() {
        val square = listOf(listOf(listOf(-78.50, -7.90), listOf(-78.49, -7.90), listOf(-78.49, -7.89), listOf(-78.50, -7.89), listOf(-78.50, -7.90)))
        val one = tree(check(mapOf("geometry" to mapOf("type" to "MultiPolygon", "coordinates" to listOf(square)), "object" to "land_parcel", "field" to "boundary")).body)
        assertTrue(one["valid"].asBoolean())
        assertTrue(one["typeMatches"].asBoolean())
        assertEquals("Polygon", one["normalized"]["type"].asString())
        assertTrue(one["repaired"].isNull)
        val point = tree(check(mapOf("geometry" to mapOf("type" to "Point", "coordinates" to listOf(-78.5, -7.9)), "object" to "land_parcel", "field" to "boundary")).body)
        assertFalse(point["typeMatches"].asBoolean())
    }

    @Test
    fun `fuera de la zona del proyecto y fuera de la region`() {
        val project = projectWithCrs()
        val zone18 = mapOf("type" to "Point", "coordinates" to listOf(-75.50, -9.00))
        val outside = tree(check(mapOf("geometry" to zone18, "object" to "locality", "field" to "location", "project" to project)).body)
        assertTrue(outside["zone"]["outOfZone"].asBoolean())
        assertFalse(outside["zone"]["outOfRegion"].asBoolean())
        val far = tree(check(mapOf("geometry" to mapOf("type" to "Point", "coordinates" to listOf(10.0, 10.0)), "object" to "locality", "field" to "location")).body)
        assertTrue(far["zone"]["outOfRegion"].asBoolean())
    }

    @Test
    fun `sin READ es 403, un campo que no es geometria es 400 y una propiedad desconocida tambien`() {
        val auditor = userWithRole("AUDITOR")
        assertEquals(HttpStatus.FORBIDDEN, check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary"), auditor.token).status)
        val code = check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "code"))
        assertEquals(HttpStatus.BAD_REQUEST, code.status)
        assertEquals(listOf("field"), problemFields(code.body))
        assertEquals(HttpStatus.BAD_REQUEST, check(mapOf("geometry" to bowtie, "object" to "land_parcel", "field" to "boundary", "repair" to true)).status)
    }
}
```

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/GeometryChecks.kt src/main/kotlin/sgspe/territory/GeoSql.kt \
  src/test/kotlin/sgspe/territory/GeometryCheckRulesTest.kt src/test/kotlin/sgspe/territory/GeometryChecksApiTest.kt
git commit -F - <<'MSG'
feat(territory): validación previa de una geometría antes de guardarla

geometry-checks da el tipo contra el campo, el motivo de invalidez en español, la reparación propuesta
con su cambio de área, el MultiPolygon de una parte normalizado y la zona y la región. No escribe.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t13 -m "Merge f62b/t13: validación de geometrías"
```

Entrega: «integración pendiente: `GeometryChecksApiTest`, `GeoSqlApiTest`».

---

### Tarea 14: El listado de calidad (`DataQuality`) sobre `SpatialGate`

**Files:**
- Create: `src/main/kotlin/sgspe/territory/DataQuality.kt`
- Test: `src/test/kotlin/sgspe/territory/DataQualityChecksTest.kt` (unitaria), `src/test/kotlin/sgspe/territory/DataQualityApiTest.kt`
  (integración, la corre el coordinador)

**Interfaces:**
- Consumes: de 6.2a, `SpatialGate.select(reads: List<GateRead>, sql: GateTables.(bind: (Any) -> String) -> String, map:
  (io.r2dbc.spi.Readable) -> T): GateOutcome<T>`, `GateRead(objectName, fields, principal)`, `GateTables` (`table`,
  `column`, `srid`), `GateOutcome.Rows`, `GateOutcome.Unavailable(missingFields)`; `Areas.GEOMETRY`, `Areas.FIELD`,
  `GeoSql.reasonLabel`, `ImportProperties` (envolvente); `Readability`, `TenantModel.present`, `StrictQuery`; de
  `F6ApiTest`: `utmProject`, `locality`, `communityWithTerritory`, `parcel`, `SyntheticGeo`, `platformPatch` (de
  `F4ApiTest`).
- Produces: `enum class QualityCheck { INVALID, OUT_OF_ZONE, STALE_AREA, NO_TERRITORY, NO_GEOMETRY }`; `QualityRaw(objectName,
  id, label, field, reason, zone, expected, stored, computed)`; `QualityRow(objectName (json «object»), id, label, field,
  detail)`; `QualityPage(check, content, page, size, hasNext)`; `DataQualityChecks.objects(check)`, `fields(check,
  objectName, byProject)`, `needsProject(check, objects)`, `sql(tables, bind, check, objects, project, envelope, limit,
  offset)`, `detail(check, raw)`; `DataQuality.page(check, objectName, project, page, size)`; `GET
  /api/sgspe/territory/data-quality`.

- [ ] **Paso 1: Rebase sobre `SpatialGate`**

```bash
cd ~/IdeaProjects/sgspe && git fetch origin
git switch feat/fase-6-2b-servicios
# si 6.2a ya está en main, rebase sobre main; si no, sobre su rama (con al menos su Tarea 1)
git log origin/main --oneline -30 | grep -qi "SpatialGate\|fase-6-2a" && BASE=origin/main || BASE=origin/feat/fase-6-2a-consultas
git rebase "$BASE"
grep -n "class SpatialGate\|data class GateRead\|interface GateTables\|sealed interface GateOutcome\|fun <T : Any> select" src/main/kotlin/sgspe/territory/SpatialGate.kt
git switch -c f62b/t14
```

Expected: el rebase sin conflictos (6.2a no toca los archivos de las Tareas 1 a 13 salvo `application.yml`, donde suma
`sgspe.territory.queries`; un conflicto ahí se resuelve conservando los dos bloques) y los cinco `grep` encontrados con las
firmas del contrato. Si una firma difiere, se adapta el código de este paso a la real y se anota en la entrega. Después del
rebase, `./gradlew ktlintFormat build` en verde antes de seguir.

- [ ] **Paso 2: La unitaria que falla**

`src/test/kotlin/sgspe/territory/DataQualityChecksTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

// las cinco revisiones de gis.md §8 (especificación de F6 §3.6): qué objetos y campos lee cada una y su SQL sobre las tablas
// de la compuerta, con todos los valores enlazados. GateTables es un doble que nombra tablas y columnas de prueba
class DataQualityChecksTest {
    private val binds = mutableListOf<Any>()
    private val bind: (Any) -> String = {
        binds += it
        "\$${binds.size}"
    }
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "(SELECT * FROM t_$objectName)"

            override fun column(
                objectName: String,
                field: String
            ) = "\"c_$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = 4326
        }
    private val envelope = ImportProperties()

    @Test
    fun `los objetos de cada revision`() {
        assertEquals(listOf("grievance", "inquiry", "stakeholder_request", "commitment", "social_risk", "incident"), DataQualityChecks.objects(QualityCheck.NO_TERRITORY))
        assertEquals(listOf("locality", "community"), DataQualityChecks.objects(QualityCheck.NO_GEOMETRY))
        assertEquals(Areas.GEOMETRY.keys.toList(), DataQualityChecks.objects(QualityCheck.STALE_AREA))
        assertTrue("land_parcel" in DataQualityChecks.objects(QualityCheck.INVALID))
        assertFalse("land_right" in DataQualityChecks.objects(QualityCheck.INVALID))
    }

    @Test
    fun `los campos que lee cada revision, con el proyecto si se filtra`() {
        assertEquals(setOf("code", "locality", "community", "location"), DataQualityChecks.fields(QualityCheck.NO_TERRITORY, "grievance", byProject = false))
        assertTrue("project" in DataQualityChecks.fields(QualityCheck.NO_TERRITORY, "grievance", byProject = true))
        assertEquals(setOf("name", "territory"), DataQualityChecks.fields(QualityCheck.NO_GEOMETRY, "community", byProject = true))
        assertEquals(setOf("code", "boundary", "calculated_area_ha"), DataQualityChecks.fields(QualityCheck.STALE_AREA, "land_parcel", byProject = false))
        assertTrue(DataQualityChecks.needsProject(QualityCheck.OUT_OF_ZONE, listOf("project_component")))
        assertFalse(DataQualityChecks.needsProject(QualityCheck.OUT_OF_ZONE, listOf("locality")))
    }

    @Test
    fun `el SQL de geometrias invalidas usa las tablas de la compuerta y enlaza todo`() {
        val project = UUID.randomUUID()
        val sql = DataQualityChecks.sql(tables, bind, QualityCheck.INVALID, listOf("land_parcel", "grievance"), project, envelope, 51, 50)
        assertTrue(sql.contains("FROM (SELECT * FROM t_land_parcel) t"), sql)
        assertTrue(sql.contains("NOT ST_IsValid(t.\"c_boundary\")"), sql)
        assertTrue(sql.contains(" UNION ALL "), sql)
        assertTrue(sql.contains("t.\"c_project\" = "), sql)
        assertFalse(sql.contains(project.toString()), "el proyecto va enlazado")
        assertTrue(binds.containsAll(listOf("land_parcel", "grievance", project, 51, 50)), binds.toString())
    }

    @Test
    fun `fuera de zona une el proyecto y enlaza la envolvente`() {
        val sql = DataQualityChecks.sql(tables, bind, QualityCheck.OUT_OF_ZONE, listOf("project_component"), null, envelope, 11, 0)
        assertTrue(sql.contains("LEFT JOIN (SELECT * FROM t_project) p ON p.id = t.\"c_project\""), sql)
        assertTrue(binds.containsAll(listOf(envelope.minLongitude, envelope.maxLongitude, envelope.minLatitude, envelope.maxLatitude)), binds.toString())
    }

    @Test
    fun `el detalle de cada revision en espanol`() {
        fun raw(
            reason: String? = null,
            zone: Int? = null,
            expected: Int? = null,
            stored: BigDecimal? = null,
            computed: BigDecimal? = null
        ) = QualityRaw("land_parcel", UUID.randomUUID(), "PRD-01", "boundary", reason, zone, expected, stored, computed)
        assertEquals("autointersección", DataQualityChecks.detail(QualityCheck.INVALID, raw(reason = "Self-intersection[-78.5 -7.9]")))
        assertEquals("zona 18S; el proyecto trabaja en 17S", DataQualityChecks.detail(QualityCheck.OUT_OF_ZONE, raw(zone = 32718, expected = 32717)))
        assertEquals("fuera de la región esperada (envolvente del Perú)", DataQualityChecks.detail(QualityCheck.OUT_OF_ZONE, raw(zone = 32718)))
        assertEquals("guardada 1.23 ha; recalculada 9.00 ha", DataQualityChecks.detail(QualityCheck.STALE_AREA, raw(stored = BigDecimal("1.23"), computed = BigDecimal("9.00"))))
        assertEquals("sin área calculada; recalculada 9.00 ha", DataQualityChecks.detail(QualityCheck.STALE_AREA, raw(computed = BigDecimal("9.00"))))
    }
}
```

Run: `./gradlew test --tests 'sgspe.territory.DataQualityChecksTest'`
Expected: FAIL de compilación («Unresolved reference: DataQualityChecks»).

- [ ] **Paso 3: Implementar `DataQuality.kt`**

```kotlin
package sgspe.territory

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.TenantModel
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.ValidationException
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.util.UUID

// las cinco revisiones del listado de calidad (gis.md §8, RQ-GIS-011, RQ-GIS-015, especificación de F6 §3.6)
enum class QualityCheck { INVALID, OUT_OF_ZONE, STALE_AREA, NO_TERRITORY, NO_GEOMETRY }

// una fila tal como sale del SQL, antes de armar su detalle
data class QualityRaw(
    val objectName: String,
    val id: UUID,
    val label: String?,
    val field: String?,
    val reason: String?,
    val zone: Int?,
    val expected: Int?,
    val stored: BigDecimal?,
    val computed: BigDecimal?
)

data class QualityRow(
    @get:JsonProperty("object") val objectName: String,
    val id: UUID,
    val label: String?,
    val field: String?,
    val detail: String?
)

data class QualityPage(
    val check: String,
    val content: List<QualityRow>,
    val page: Int,
    val size: Int,
    val hasNext: Boolean
)

// qué lee cada revisión y su SQL sobre las tablas de SpatialGate (cada una ya filtrada por organización y por todos los
// RecordReadScope; ASM-122). los nombres de tabla y columna salen de GateTables; los valores, todos enlazados. una fila por
// registro y campo; varias tablas en un solo UNION ALL ordenado por objeto, id y campo
object DataQualityChecks {
    private const val PROJECT = "project"
    private const val AREA_OF_INFLUENCE = "area_of_influence"
    val MANAGEMENT = listOf("grievance", "inquiry", "stakeholder_request", "commitment", "social_risk", "incident")

    // las geometrías revisadas: las de las capas LYR-01 a LYR-16 y la ubicación de los registros de gestión
    private val GEOMETRIES: Map<String, List<String>> =
        linkedMapOf(
            "administrative_unit" to listOf("boundary"),
            "locality" to listOf("location", "boundary"),
            "road_access" to listOf("trace"),
            "community" to listOf("territory"),
            "community_sector" to listOf("boundary"),
            "mining_concession" to listOf("boundary"),
            "environmental_feature" to listOf("location", "trace", "boundary"),
            PROJECT to listOf("location", "interest_area"),
            "project_component" to listOf("location", "trace", "footprint"),
            AREA_OF_INFLUENCE to listOf("boundary"),
            "land_parcel" to listOf("boundary"),
            "school" to listOf("location"),
            "health_facility" to listOf("location")
        ) + MANAGEMENT.associateWith { listOf("location") }

    // los que tienen proyecto: su zona esperada es la del working_crs de su proyecto
    private val BY_PROJECT: Set<String> = setOf("project_component", AREA_OF_INFLUENCE) + MANAGEMENT
    private val WITHOUT_GEOMETRY: Map<String, List<String>> = linkedMapOf("locality" to listOf("location", "boundary"), "community" to listOf("territory"))

    // el texto que nombra un registro en la fila: su código o su nombre (road_access no tiene ninguno)
    private val CODED: Set<String> = setOf("land_parcel", "mining_concession", PROJECT, "project_component") + MANAGEMENT
    private val LABEL: Map<String, String> =
        GEOMETRIES.keys.filter { it !in setOf("road_access", AREA_OF_INFLUENCE) }.associateWith { if (it in CODED) "code" else "name" }

    fun objects(check: QualityCheck): List<String> =
        when (check) {
            QualityCheck.INVALID, QualityCheck.OUT_OF_ZONE -> GEOMETRIES.keys.toList()
            QualityCheck.STALE_AREA -> Areas.GEOMETRY.keys.toList()
            QualityCheck.NO_TERRITORY -> MANAGEMENT
            QualityCheck.NO_GEOMETRY -> WITHOUT_GEOMETRY.keys.toList()
        }

    fun fields(
        check: QualityCheck,
        objectName: String,
        byProject: Boolean
    ): Set<String> =
        buildSet {
            LABEL[objectName]?.let(::add)
            when (check) {
                QualityCheck.INVALID -> addAll(GEOMETRIES.getValue(objectName))
                QualityCheck.OUT_OF_ZONE -> {
                    addAll(GEOMETRIES.getValue(objectName))
                    if (objectName in BY_PROJECT) add(PROJECT)
                    if (objectName == PROJECT) add("working_crs")
                }
                QualityCheck.STALE_AREA -> {
                    add(Areas.GEOMETRY.getValue(objectName))
                    add(Areas.FIELD)
                    if (objectName == AREA_OF_INFLUENCE) add(PROJECT)
                }
                QualityCheck.NO_TERRITORY -> addAll(listOf("locality", "community", "location"))
                QualityCheck.NO_GEOMETRY -> addAll(WITHOUT_GEOMETRY.getValue(objectName))
            }
            if (byProject && objectName in BY_PROJECT) add(PROJECT)
        }

    fun needsProject(
        check: QualityCheck,
        objects: List<String>
    ): Boolean =
        (check == QualityCheck.OUT_OF_ZONE && objects.any { it in BY_PROJECT }) || (check == QualityCheck.STALE_AREA && AREA_OF_INFLUENCE in objects)

    fun sql(
        tables: GateTables,
        bind: (Any) -> String,
        check: QualityCheck,
        objects: List<String>,
        project: UUID?,
        envelope: ImportProperties,
        limit: Int,
        offset: Int
    ): String {
        val parts =
            objects.flatMap { objectName ->
                when (check) {
                    QualityCheck.INVALID -> GEOMETRIES.getValue(objectName).map { invalid(tables, bind, objectName, it, project) }
                    QualityCheck.OUT_OF_ZONE -> GEOMETRIES.getValue(objectName).map { outOfZone(tables, bind, objectName, it, project, envelope) }
                    QualityCheck.STALE_AREA -> listOf(staleArea(tables, bind, objectName, project))
                    QualityCheck.NO_TERRITORY -> listOf(missing(tables, bind, objectName, listOf("locality", "community", "location"), project))
                    QualityCheck.NO_GEOMETRY -> listOf(missing(tables, bind, objectName, WITHOUT_GEOMETRY.getValue(objectName), project))
                }
            }
        return "SELECT * FROM (${parts.joinToString(" UNION ALL ")}) q ORDER BY q.\"object\", q.id, q.field LIMIT ${bind(limit)} OFFSET ${bind(offset)}"
    }

    fun detail(
        check: QualityCheck,
        raw: QualityRaw
    ): String? =
        when (check) {
            QualityCheck.INVALID -> GeoSql.reasonLabel(raw.reason)
            QualityCheck.OUT_OF_ZONE ->
                if (raw.expected != null && raw.zone != null && raw.zone != raw.expected) {
                    "zona ${raw.zone - 32700}S; el proyecto trabaja en ${raw.expected - 32700}S"
                } else {
                    "fuera de la región esperada (envolvente del Perú)"
                }
            QualityCheck.STALE_AREA ->
                if (raw.stored == null) "sin área calculada; recalculada ${raw.computed?.toPlainString()} ha" else "guardada ${raw.stored.toPlainString()} ha; recalculada ${raw.computed?.toPlainString()} ha"
            QualityCheck.NO_TERRITORY -> "sin localidad, comunidad ni ubicación"
            QualityCheck.NO_GEOMETRY -> "sin geometría: no se dibuja ni sirve para ubicar por relación"
        }

    // ---- las partes del UNION ALL: object, id, label, field, reason, zone, expected, stored, computed ----

    private fun head(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        field: String?
    ): String {
        val label = LABEL[objectName]?.let { "CAST(t.${tables.column(objectName, it)} AS text)" } ?: "CAST(NULL AS text)"
        val named = field?.let { "CAST(${bind(it)} AS text)" } ?: "CAST(NULL AS text)"
        return "SELECT CAST(${bind(objectName)} AS text) AS \"object\", t.id AS id, $label AS label, $named AS field"
    }

    private fun projectFilter(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        project: UUID?
    ): String =
        when {
            project == null -> ""
            objectName in BY_PROJECT -> " AND t.${tables.column(objectName, PROJECT)} = ${bind(project)}"
            objectName == PROJECT -> " AND t.id = ${bind(project)}"
            else -> ""
        }

    // el SRID del working_crs («EPSG 32717» -> 32717) del proyecto unido como p, o del proyecto mismo
    private fun expectedZone(
        tables: GateTables,
        objectName: String
    ): Pair<String, String> =
        when (objectName) {
            in BY_PROJECT ->
                "LEFT JOIN ${tables.table(PROJECT)} p ON p.id = t.${tables.column(objectName, PROJECT)}" to
                    "CAST(substring(p.${tables.column(PROJECT, "working_crs")} FROM '[0-9]+') AS int)"
            PROJECT -> "" to "CAST(substring(t.${tables.column(PROJECT, "working_crs")} FROM '[0-9]+') AS int)"
            else -> "" to "CAST(NULL AS int)"
        }

    private fun invalid(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        field: String,
        project: UUID?
    ): String {
        val g = "t.${tables.column(objectName, field)}"
        return "${head(tables, bind, objectName, field)}, ST_IsValidReason($g) AS reason, CAST(NULL AS int) AS zone, CAST(NULL AS int) AS expected, " +
            "CAST(NULL AS numeric) AS stored, CAST(NULL AS numeric) AS computed FROM ${tables.table(objectName)} t " +
            "WHERE $g IS NOT NULL AND NOT ST_IsValid($g)${projectFilter(tables, bind, objectName, project)}"
    }

    private fun outOfZone(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        field: String,
        project: UUID?,
        envelope: ImportProperties
    ): String {
        val g = "t.${tables.column(objectName, field)}"
        val (join, expected) = expectedZone(tables, objectName)
        return "${head(tables, bind, objectName, field)}, CAST(NULL AS text) AS reason, z.zone AS zone, z.expected AS expected, " +
            "CAST(NULL AS numeric) AS stored, CAST(NULL AS numeric) AS computed FROM ${tables.table(objectName)} t $join " +
            "CROSS JOIN LATERAL (SELECT ST_X(ST_Centroid($g)) AS x, ST_Y(ST_Centroid($g)) AS y) c " +
            "CROSS JOIN LATERAL (SELECT CASE WHEN c.y <= 0 THEN 32700 + floor((c.x + 180) / 6)::int + 1 END AS zone, $expected AS expected) z " +
            "WHERE $g IS NOT NULL${projectFilter(tables, bind, objectName, project)} AND (c.x < ${bind(envelope.minLongitude)} OR c.x > ${bind(envelope.maxLongitude)} " +
            "OR c.y < ${bind(envelope.minLatitude)} OR c.y > ${bind(envelope.maxLatitude)} OR (z.expected IS NOT NULL AND z.zone IS DISTINCT FROM z.expected))"
    }

    // el área como la calcula Areas: el working_crs del proyecto para un área de influencia, si no la zona del centroide
    private fun staleArea(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        project: UUID?
    ): String {
        val field = Areas.GEOMETRY.getValue(objectName)
        val g = "t.${tables.column(objectName, field)}"
        val area = "t.${tables.column(objectName, Areas.FIELD)}"
        val (join, expected) = if (objectName == AREA_OF_INFLUENCE) expectedZone(tables, objectName) else "" to "CAST(NULL AS int)"
        return "${head(tables, bind, objectName, field)}, CAST(NULL AS text) AS reason, a.zone AS zone, CAST(NULL AS int) AS expected, " +
            "$area AS stored, a.computed AS computed FROM ${tables.table(objectName)} t $join " +
            "CROSS JOIN LATERAL (SELECT COALESCE($expected, CASE WHEN ST_Y(ST_Centroid($g)) <= 0 THEN 32700 + floor((ST_X(ST_Centroid($g)) + 180) / 6)::int + 1 END) AS zone) zz " +
            "CROSS JOIN LATERAL (SELECT zz.zone AS zone, CASE WHEN zz.zone IN (32717, 32718, 32719) " +
            "THEN round((ST_Area(ST_Transform($g, zz.zone)) / 10000)::numeric, 2) END AS computed) a " +
            "WHERE $g IS NOT NULL${projectFilter(tables, bind, objectName, project)} AND a.computed IS NOT NULL AND ($area IS NULL OR abs($area - a.computed) > 0.01)"
    }

    private fun missing(
        tables: GateTables,
        bind: (Any) -> String,
        objectName: String,
        fields: List<String>,
        project: UUID?
    ): String {
        val empty = fields.joinToString(" AND ") { "t.${tables.column(objectName, it)} IS NULL" }
        return "${head(tables, bind, objectName, null)}, CAST(NULL AS text) AS reason, CAST(NULL AS int) AS zone, CAST(NULL AS int) AS expected, " +
            "CAST(NULL AS numeric) AS stored, CAST(NULL AS numeric) AS computed FROM ${tables.table(objectName)} t " +
            "WHERE $empty${projectFilter(tables, bind, objectName, project)}"
    }
}

// GET /api/sgspe/territory/data-quality: una revisión por página, por SpatialGate, solo con los objetos que la organización
// tiene (TenantModel) y que quien llama lee con todos los campos de la revisión; un objeto pedido que no lee es 403
@Service
class DataQuality(
    private val gate: SpatialGate,
    private val callers: Callers,
    private val metadata: MetadataService,
    private val tenantModel: TenantModel,
    private val properties: ImportProperties
) {
    suspend fun page(
        check: QualityCheck,
        objectName: String?,
        project: UUID?,
        page: Int,
        size: Int
    ): QualityPage {
        val user = callers.require()
        val candidates = DataQualityChecks.objects(check)
        if (objectName != null && objectName !in candidates) {
            throw ValidationException("Objeto inválido", "object", "la revisión ${check.name} cubre ${candidates.joinToString(", ")}")
        }
        val byProject = project != null
        val present = tenantModel.present(user.organizationId, objectName?.let(::listOf) ?: candidates)
        val readability = Readability(metadata)
        val readable = present.filter { o -> DataQualityChecks.fields(check, o, byProject).all { readability.canRead("$o.$it") } }
        if (objectName != null && objectName !in readable) {
            throw ForbiddenException("La revisión ${check.name} de $objectName exige leer ${DataQualityChecks.fields(check, objectName, byProject).joinToString(", ")}")
        }
        if (readable.isEmpty()) return QualityPage(check.name, emptyList(), page, size, false)
        val reads =
            readable.map { GateRead(it, DataQualityChecks.fields(check, it, byProject), principal = true) } +
                listOfNotNull(GateRead("project", setOf("working_crs")).takeIf { DataQualityChecks.needsProject(check, readable) })
        val outcome =
            gate.select(reads, { bind -> DataQualityChecks.sql(this, bind, check, readable, project, properties, size + 1, page * size) }) { row ->
                QualityRaw(
                    objectName = row.get("object", String::class.java)!!,
                    id = row.get("id", UUID::class.java)!!,
                    label = row.get("label", String::class.java),
                    field = row.get("field", String::class.java),
                    reason = row.get("reason", String::class.java),
                    zone = (row.get("zone") as Number?)?.toInt(),
                    expected = (row.get("expected") as Number?)?.toInt(),
                    stored = row.get("stored", BigDecimal::class.java),
                    computed = row.get("computed", BigDecimal::class.java)
                )
            }
        val rows =
            when (outcome) {
                is GateOutcome.Rows -> outcome.rows
                is GateOutcome.Unavailable -> throw ForbiddenException("La revisión ${check.name} exige leer ${outcome.missingFields.joinToString(", ")}")
            }
        return QualityPage(
            check.name,
            rows.take(size).map { QualityRow(it.objectName, it.id, it.label, it.field, DataQualityChecks.detail(check, it)) },
            page,
            size,
            rows.size > size
        )
    }
}

@RestController
@RequestMapping("/api/sgspe/territory/data-quality")
class DataQualityController(
    private val quality: DataQuality
) {
    @GetMapping
    suspend fun list(request: ServerHttpRequest): QualityPage {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("check", "object", "project", "page", "size"))
        val check =
            params.getFirst("check")?.let { raw -> QualityCheck.entries.firstOrNull { it.name == raw } }
                ?: throw ValidationException("Revisión inválida", "check", "es una de ${QualityCheck.entries.joinToString(", ")}")
        val project = params.getFirst("project")?.let { Records.uuid(it) ?: throw ValidationException("Proyecto inválido", "project", "debe ser un UUID") }
        val page = number(params.getFirst("page"), "page", 0, 0, MAX_PAGE)
        val size = number(params.getFirst("size"), "size", DEFAULT_SIZE, 1, MAX_SIZE)
        return quality.page(check, params.getFirst("object"), project, page, size)
    }

    private fun number(
        raw: String?,
        field: String,
        default: Int,
        min: Int,
        max: Int
    ): Int {
        if (raw == null) return default
        val value = raw.toIntOrNull()
        if (value == null || value < min || value > max) {
            throw Finding("OUT_OF_RANGE", "$field va de $min a $max", field, status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        return value
    }

    companion object {
        private const val DEFAULT_SIZE = 50
        private const val MAX_SIZE = 200
        private const val MAX_PAGE = 10_000
    }
}
```

Nota: si la `GateRead` de 6.2a no tiene `principal` con valor por defecto, la del proyecto se escribe
`GateRead("project", setOf("working_crs"), principal = false)`.

Run: `./gradlew test --tests 'sgspe.territory.DataQualityChecksTest'`
Expected: PASS (5 pruebas).

- [ ] **Paso 4: La prueba de integración (la corre el coordinador)**

`src/test/kotlin/sgspe/territory/DataQualityApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6ApiTest
import sgspe.SyntheticGeo
import tools.jackson.databind.JsonNode

// especificación de F6 §5 «DataQualityApiTest» (gis.md §8): las cinco revisiones con registros sintéticos. el tenant es
// compartido: cada prueba busca sus ids recorriendo las páginas (o filtra por proyecto)
class DataQualityApiTest : F6ApiTest() {
    private fun all(
        check: String,
        extra: String = "",
        token: String = this.token
    ): List<JsonNode> {
        val rows = mutableListOf<JsonNode>()
        var page = 0
        do {
            val body = tree(send("GET", "/api/sgspe/territory/data-quality?check=$check&size=200&page=$page$extra", null, HttpStatus.OK, token))
            rows += body["content"].values()
            page++
        } while (body["hasNext"].asBoolean())
        return rows
    }

    private fun row(
        rows: List<JsonNode>,
        id: String
    ): JsonNode? = rows.firstOrNull { it["id"].asString() == id }

    @Test
    fun `un predio con un lazo sale como INVALID con su motivo`() {
        val (lon, lat) = SyntheticGeo.ORIGIN
        val bowtie = mapOf("type" to "Polygon", "coordinates" to listOf(listOf(listOf(lon, lat), listOf(lon + 0.01, lat + 0.01), listOf(lon + 0.01, lat), listOf(lon, lat + 0.01), listOf(lon, lat))))
        val id = createWith("land_parcel", mapOf("code" to uniqueName("PRD-LAZO"), "tenure_type" to "PROPIEDAD"), mapOf("boundary" to bowtie))
        val found = row(all("INVALID", "&object=land_parcel"), id)!!
        assertEquals("land_parcel", found["object"].asString())
        assertEquals("boundary", found["field"].asString())
        assertEquals("autointersección", found["detail"].asString())
    }

    @Test
    fun `una localidad fuera del Peru sale como OUT_OF_ZONE`() {
        val id = runBlocking { locality(uniqueName("Localidad lejana"), SyntheticGeo.point(10.0, 10.0)).toString() }
        val found = row(all("OUT_OF_ZONE", "&object=locality"), id)!!
        assertEquals("fuera de la región esperada (envolvente del Perú)", found["detail"].asString())
    }

    @Test
    fun `un area guardada distinta del recalculo sale como STALE_AREA`() {
        val (lon, lat) = SyntheticGeo.ORIGIN
        val id = runBlocking { communityWithTerritory(uniqueName("Comunidad Ejemplo"), SyntheticGeo.multiSquare(lon, lat + 0.05, 300.0)).toString() }
        platformPatch("community", id, mapOf("calculated_area_ha" to 1.23))
        val found = row(all("STALE_AREA", "&object=community"), id)!!
        val detail = found["detail"].asString()
        assertTrue(detail.startsWith("guardada 1.23 ha; recalculada "), detail)
        // el cuadrado de SyntheticGeo es de 300 m con la aproximación equirectangular: 9 ha con menos de 1 % de error
        assertEquals(9.0, detail.substringAfter("recalculada ").substringBefore(" ha").toDouble(), 0.1)
    }

    @Test
    fun `una queja sin localidad, comunidad ni ubicacion sale como NO_TERRITORY y con proyecto se filtra`() {
        val projectId = runBlocking { utmProject() }
        val project = projectId.toString()
        val grievance = grievance(project)
        val rows = all("NO_TERRITORY", "&project=$project")
        assertEquals(setOf(grievance), rows.map { it["id"].asString() }.toSet())
        assertEquals("sin localidad, comunidad ni ubicación", rows.single()["detail"].asString())
    }

    @Test
    fun `localidades y comunidades sin geometria salen como NO_GEOMETRY y el chequeo se valida`() {
        val locality = runBlocking { locality(uniqueName("Localidad sin punto"), null).toString() }
        val community = runBlocking { communityWithTerritory(uniqueName("Comunidad sin territorio"), null).toString() }
        val rows = all("NO_GEOMETRY")
        assertTrue(row(rows, locality) != null && row(rows, community) != null)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/territory/data-quality?check=OTRO").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/territory/data-quality?check=INVALID&object=land_right").status)
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/data-quality?check=INVALID&orden=id").status)
        val auditor = userWithRole("AUDITOR")
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/sgspe/territory/data-quality?check=INVALID&object=land_parcel", null, auditor.token).status)
    }
}
```

Notas: `grievance(project)` es la ayuda de `F4ApiTest` (`attentionAttributes` sin `location`, `locality` ni
`community`). El área esperada del
cuadrado de 300 m es 9 ha (`gis.md` §9 «Área de un cuadrado»), con la tolerancia de `SyntheticGeo` (plan 6.1).

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/DataQuality.kt src/test/kotlin/sgspe/territory/DataQualityChecksTest.kt \
  src/test/kotlin/sgspe/territory/DataQualityApiTest.kt
git commit -F - <<'MSG'
feat(territory): listado de calidad de datos sobre SpatialGate

data-quality pagina las cinco revisiones de gis.md §8 (inválidas, fuera de zona, áreas
desactualizadas, sin territorio, sin geometría) con los alcances de lectura de quien llama.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
git switch feat/fase-6-2b-servicios && git merge --no-ff f62b/t14 -m "Merge f62b/t14: listado de calidad"
```

Entrega: «integración pendiente: `DataQualityApiTest`, `SpatialGateApiTest`, `ArchitectureTest` (unitaria, ya en
`build`)».

---

### Tarea 15: Verificación final, push y PR

**Files:**
- Ninguno nuevo. Verifica `feat/fase-6-2b-servicios` con las Tareas 1 a 14 integradas.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: el PR de `feat/fase-6-2b-servicios` contra `main`.

- [ ] **Paso 1: Build completo y vigías**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-6-2b-servicios && git status --short
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
grep -rn "DatabaseClient" src/main/kotlin/sgspe/territory | grep -v "SpatialGate.kt\|TerritoryImports.kt\|Areas.kt\|ProjectComponents.kt\|GeoSql.kt"
grep -rn "asPlatform" src/main/kotlin/sgspe/territory | head
grep -rn "import sgspe.territory" src/main/kotlin/sgspe/privacy src/main/kotlin/sgspe/scope src/main/kotlin/sgspe/common | head
grep -rn "tile.openstreetmap.org" src/main/resources/application-prod.yml
cd model && python3 -m unittest -v 2>&1 | tail -3 && cd ..
python3 tools/check_traceability.py
yarn format:check
```

Expected: `BUILD SUCCESSFUL` (con `ArchitectureTest` y las unitarias de GDAL corridas, no saltadas: buscar en
`build/reports/tests/test` que `GdalReaderTest` y `GdalWriterTest` no estén «skipped»); los cuatro `grep` sin salida; las
pruebas del modelo en `OK`; `check_traceability.py` sin errores; `format:check` limpio.

- [ ] **Paso 2: Lista de integración para el coordinador**

Clases nuevas o cambiadas, para correr juntas (o dejarlas a la CI): `MapSettingsApiTest`, `WmsProxyApiTest`,
`GdalImportApiTest`, `GdalMissingApiTest`, `TerritoryExportsApiTest`, `TerritoryExportsLimitApiTest`,
`GeometryChecksApiTest`, `DataQualityApiTest`; y las que vigilan que nada de F2 a F5 cambió: `TerritoryImportApiTest`,
`ImportLimitsApiTest`, `ImportTimeoutApiTest`, `GeoSqlApiTest`, `TerritoryLayersApiTest`, `CommitmentMatrixApiTest`,
`StakeholderRegisterApiTest`, `AttentionRegisterApiTest`, `RiskRegisterApiTest`. El coordinador corre GDAL local
(`/opt/homebrew/bin` en el `PATH` del JVM de Gradle) o deja estas clases a la CI.

- [ ] **Paso 3: Push y PR**

Si la Tarea 14 se rebasó sobre la rama de 6.2a, el PR espera a que 6.2a esté en `main` y la rama se rebasa sobre
`origin/main` (con `build` en verde otra vez) antes del push: así el PR solo trae los commits de 6.2b.

```bash
cd ~/IdeaProjects/sgspe && git fetch origin && git rebase origin/main && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git push -u origin feat/fase-6-2b-servicios
gh pr create --base main --head feat/fase-6-2b-servicios --title "feat: servicios SIG de F6: WMS, mapa base, GDAL, exportaciones y calidad (plan 6.2b)" --body-file - <<'BODY'
## Qué trae

- `GET /api/sgspe/map/settings`: mapa base por organización (`BasemapSpec`), sin OpenStreetMap en producción.
- `GET /api/sgspe/territory/wms`: proxy WMS autenticado; lista blanca de `layers.json` con las capas publicadas de la
  organización y READ; solo GetMap y GetLegendGraphic con parámetros en lista blanca; GetCapabilities propio; GeoServer
  caído, lento o con una respuesta que no es imagen, `503 GEOSERVER_UNAVAILABLE`.
- Importación con GDAL (`GdalReader`, `GdalCommand`, `GdalTools`): Shapefile y FileGDB en ZIP, KML, KMZ, GeoPackage y
  GPX; `imports/formats`; elección de capa (`LAYER_REQUIRED`) y CRS detectado como propuesta; sin GDAL,
  `422 FORMAT_UNAVAILABLE`. Destinos nuevos `community` y `environmental_feature` por código.
- `POST /api/sgspe/territory/exports`: GeoJSON, CSV, XLSX, KML, GeoPackage y Shapefile de LYR-01 a LYR-16 con divulgación
  por capa (`ExportDisclosure`) y `export_log`.
- `POST /api/sgspe/territory/geometry-checks` y `GET /api/sgspe/territory/data-quality` (sobre `SpatialGate`).
- `gdal-bin` ≥ 3.8 en los trabajos `build` e `integration` de la CI.

## Pruebas

Unitarias en verde con `./gradlew build` (las de GDAL con GDAL instalado). Integración en la CI (lista en la Tarea 15).

## Para revisar

- Decisión 1: solo el driver `KML` (sin `LIBKML`), el KMZ lo descomprime SGSPE; `OGR_KML_FOLLOW_LINKS` no existe en GDAL.
- Decisión 3: un formato de GDAL sin `sourceCrs` responde `400` con `layers[]` y su `detectedCrs`; `api.md` §4.2 lo
  necesita (plan 6.0 o cierre de fase).
- Decisión 4: la lista de capas publicadas se toma de la REST de GeoServer con caché de 60 s.
- Decisión 8: los campos que se quitan fuera de INTERNO (partidas y procedencia; el proyecto con código y nombre).
- Código nuevo no listado en la especificación: `EXPORT_TIMEOUT` (`503`), y los de validación del cuerpo de exportación
  `INVALID_FORMAT`, `INVALID_COORDINATES`, `INVALID_GEOMETRY`.
- `security.md` §10.2 pide limitar la tasa de peticiones del proxy; la especificación de F6 no lo asigna y queda para el
  proxy inverso del despliegue.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
BODY
```

Expected: la URL del PR. En el primer verde de la CI, comprobar en el registro del paso «GDAL» la versión del runner (≥ 3.8)
y anotarla en el PR. Con la CI en verde y el plan revisado, se integra según la memoria del proyecto (push y PR
autorizados; merge tras CI verde).

---

## Dependencias de otros planes

- **6.0.** `api.md` §4 con los endpoints y errores de este plan; las decisiones 3, 5, 8, 9 y 13 afinan el contrato y la
  Tarea 15 las anota en el PR.
- **6.1.** `model.json` (campos de `community` y `environmental_feature`; `EXPORT` en los objetos de §3.5), `roles.json`
  (`EXPORT` para `GIS_ANALYST`, `SOCIAL_MANAGER`, `PROJECT_MANAGER`; `GIS_ANALYST` con CREATE sobre `export_log`, su
  decisión 2; `STAKEHOLDER_MANAGER` sin `EXPORT` sobre `land_parcel`, que usa la prueba «sin EXPORT»), `model/layers.json`,
  `compose.yml` con GeoServer y su puerto, `F6ApiTest` con las ayudas del contrato. Si 6.1 ya dejó `layers.json` en el jar
  o `wasichai.gis.geoserver.*` en los yaml, la Tarea 3 no los duplica.
- **6.2a.** `SpatialGate` con la firma del contrato (Tarea 14, rebase) y la regla de `ArchitectureTest` sobre
  `DatabaseClient`, que este plan respeta (el SQL nuevo va en `GeoSql` o por `SpatialGate`).
- **6.3.** Consume las interfaces de la sección «Interfaces con el portal».

## Cobertura del alcance de este plan

| Especificación | Tarea |
|---|---|
| §3.3 mapa base, `MapSettingsApiTest`, `MapSettingsProdTest` | 1 |
| §3.3 proxy WMS, `WmsRequestFilterTest`, `WmsProxyApiTest`, configuración de GeoServer | 2, 3 |
| §3.4 `GdalReader`, entorno y argumentos (`GdalCommandTest`), versión mínima, `FORMAT_UNAVAILABLE`, `imports/formats` | 4, 5, 7, 8 |
| §3.4 `gdal-bin` en la CI con versión mínima 3.8 | 6 |
| §3.4 elección de capa, CRS detectado, ZIP con `../`, `GdalImportApiTest` | 5, 7, 8 |
| §3.4 destinos `community` y `environmental_feature` | 9 |
| §3.5 divulgación por capa, `ExportDisclosureTest` | 10 |
| §3.5 formatos y `GdalWriter`, coordenadas UTM y latitud y longitud | 11, 12 |
| §3.5 permisos, `export_log`, tope de filas, `TerritoryExportsApiTest` | 12 |
| §3.6 `geometry-checks`, `GeometryChecksApiTest` | 13 |
| §3.6 `data-quality`, `DataQualityApiTest` | 14 |
| §7 fila 6.2b, PR | 15 |
