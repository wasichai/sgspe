# SGSPE · Fase 6: especificación de diseño

> **Para agentes:** esta especificación es la entrada de los planes 6.0 (documentos), 6.1 (modelo y GeoServer), 6.2a
> (consultas y KPI-TER), 6.2b (servicios SIG) y 6.3 (portal). No define IDs: los que se nombran en §6 los agrega el plan
> 6.0 a `docs/requirements-analysis.md`, y hasta entonces `docs/` sigue siendo la fuente de verdad. Donde esta
> especificación y `docs/` difieren, el plan 6.0 corrige `docs/` según lo que aquí se decide.

**Objetivo.** Dejar diseñada de punta a punta la Fase 6 («SIG avanzado», segunda de la Release 2) sobre wasichai 1.0.0
y la maquinaria de F2 a F5: las consultas espaciales detrás de una compuerta única, el mapa territorial con capas
agrupadas, calor y ficha contextual, KPI-TER-01 a 04 y el tablero `territory`, GeoServer con estilos y un proxy WMS
autenticado, el mapa base por organización, la importación con GDAL (con dos destinos nuevos), las exportaciones
geográficas, la validación de geometrías y el listado de calidad.

**Arquitectura.** `sgspe.territory` suma `SpatialGate`, la única puerta para SQL propio que cruza tablas del tenant, que
compone todos los `RecordReadScope` registrados; sobre ella van los endpoints de consulta, KPI-TER-04, `summary` y
`data-quality`. Los filtros por zona leen la zona como quien llama y la enlazan como parámetro de un `RecordCriterion`.
El proxy WMS habla con GeoServer por `WebClient` y solo deja pasar imágenes de capas públicas del tenant. GDAL
(`ogr2ogr`, `ogrinfo`) convierte los formatos nuevos a GeoJSON en su CRS de origen y los entrega al flujo de
importación de F2; las exportaciones leen por `Records` y escriben los formatos binarios con GDAL. `sgspe.dashboard`
suma KPI-TER y el tablero `territory` con los filtros de territorio que ya existen. El modelo gana `code` y los campos
de origen en `community` y `environmental_feature`; `model/publish_layers.py` publica las capas públicas y carga los
estilos SLD. El portal amplía S09, S10, S12 a S15 y suma el listado de calidad y el tablero territorial.

**Fuentes.** Las decisiones del usuario del 2026-10-11 (§2.1, vinculantes); `docs/roadmap.md` (F6); `docs/gis.md`
(entero); `docs/api.md` (§4, endpoints F6); `docs/dashboards.md` (§4.8, §5); `docs/security.md` (§9.7, §10);
`docs/architecture.md` (§5, §10); `docs/ux/screens.md` (S09, S10, S12 a S15, «Tablero territorial»);
`docs/requirements-analysis.md` (RQ-GIS-002, 005, 011, 012, 013, 017, 018; RQ-SEC-011; RQ-DSH-029; RF-DSH-006;
GAP-011, GAP-012; UPS-13; ASM-038, 048, 050, 056, 057, 064, 085); ADR-0002, ADR-0015, ADR-0019; el módulo GIS de
wasichai (`wasichai/docs/modules/gis.md`, `docs/gis/geometry.md`); y el código de `sgspe.territory`, `sgspe.dashboard`,
`sgspe.privacy`, `sgspe.scope` y `ArchitectureTest`.

## Restricciones globales

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

## 1. Propósito y alcance de F6

| Subsistema | Alcance | Piezas |
|---|---|---|
| Consultas espaciales | Los 16 casos de `gis.md` §5.3 en los 13 endpoints de consulta de `api.md` | `SpatialGate`, `ZoneCriterion`, `TerritoryQueries` |
| Ficha y tablero | KPI-TER-01 a 04; tablero `territory`; la parte territorial de la ficha (`…/card`) | `TerritoryKpis`, `TerritoryCards` |
| GeoServer | Servicio en el compose; capas públicas (LYR-01, 02, 03, 04, 05, 07 y 16) publicadas por script; estilos SLD y paleta; proxy WMS autenticado | `model/publish_layers.py`, `model/styles/`, `WmsProxy` |
| Mapa base | Estilo por organización con valor por defecto; endpoint de ajustes del mapa | `MapSettings` |
| Importación GDAL | Shapefile (ZIP), KML y KMZ, GeoPackage, FileGDB (ZIP) y GPX; CRS detectado como propuesta; elección de capa; destinos nuevos `community.territory` y `environmental_feature.boundary` | `GdalReader`, `ImportFiles`, `TerritoryImports` |
| Exportaciones | GeoJSON, CSV, XLSX, KML, Shapefile y GeoPackage de los objetos de LYR-01 a LYR-16, con nivel de divulgación por capa | `TerritoryExports`, `GdalWriter` |
| Calidad | Validación previa de una geometría; listado de calidad | `GeometryChecks`, `DataQuality` |
| Portal | S12 completo con ficha, S09 con análisis, S10 con sugerencias y resumen, S15 con GDAL, diálogo de exportación, validación al editar, `locate`, listado de calidad (S63), tablero territorial | sgspe-ui |

Fuera de F6: el WFS para analistas (decisión 1, ASM-120); los mapas de informe con GetMap, el spike de elementos en
línea y la grilla UTM (F8, ADR-0015); exportar capas de gestión (LYR-17 a LYR-24, decisión 4); importar áreas de
influencia o el área de interés con CRS de origen (siguen por WF-O y GeoJSON); un mapa base configurable en `MapView`
teselas vectoriales (`gis.md` §6.3); agrupamiento por densidad en el calor;
curvas de nivel y cartografía base (GAP-011).

## 2. Decisiones

### 2.1 Las decisiones del usuario (2026-10-11)

| # | Decisión | Consecuencia |
|---|---|---|
| 1 | **GeoServer con WMS; WFS después.** GeoServer en el compose, `publish_layers.py`, estilos SLD y el proxy WMS autenticado para las capas públicas; el WFS autenticado de RQ-GIS-018 y ASM-056 se pospone | `GET /api/sgspe/territory/wfs` sale de `api.md` F6 y del roadmap; ASM-120 registra el aplazamiento; las exportaciones cubren a los analistas |
| 2 | **UPS-13 upstream.** El usuario pidió llevarla a wasichai-ui; al revisarlo resultó resuelta: wasichai-ui#38 (cerrado el 2026-10-10) agregó `basemap` e `initialView` a `gisModule` y a `MapView`, y sgspe-ui ya usa `@wasichai/gis` 1.0.0 con ese cambio | F6 adopta `basemap` en todos los mapas (§3.3); UPS-13 pasa a «Resuelta» con wasichai-ui#38; ningún issue nuevo |
| 3 | **Destinos nuevos de importación: territorio comunal y elementos ambientales.** `community` y `environmental_feature` ganan `code` y los campos de origen y se importan con reconciliación por código | §3.4; las áreas de influencia y el área de interés siguen por su flujo |
| 4 | **Exportaciones solo de capas territoriales.** LYR-01 a LYR-16, con `EXPORT`, nivel de divulgación y `export_log`; las capas de gestión no salen como archivo geográfico | §3.5; ASM-124 |
| 5 | **Compuerta híbrida (enfoque C).** Filtros por `Records` con la geometría de referencia enlazada; cruces, medidas y agregaciones por `SpatialGate`, que compone todos los `RecordReadScope` registrados | §3.1; ASM-122 |
| 6 | **Reparto en planes** 6.0, 6.1, 6.2a, 6.2b y 6.3 con el paralelismo de §7 | §7 |

### 2.2 Elecciones de esta especificación

1. **Las capas publicadas son maestros TENANT sin campos restringidos.** LYR-01, 02, 03, 04, 05, 07 y 16 salen de
   `administrative_unit`, `locality`, `road_access`, `community`, `mining_concession`, `school` y `health_facility`,
   todos de `ProjectScope.TENANT`. Una prueba del modelo lo exige para cada capa de la lista de `publish_layers.py`.
2. **El proxy solo entrega imágenes.** wasichai publica la tabla virtual con `id`, los campos simples y la geometría;
   esos atributos solo se verían por GetFeatureInfo o WFS, y el proxy los rechaza. Así el contenido de los campos
   simples de una capa pública no sale nunca de GeoServer.
3. **Los alcances se componen, no se copian.** `SpatialGate` llama a todos los `RecordReadScope` inyectados, como
   wasichai en `RecordService`; un alcance nuevo se aplica solo. La regla de cuándo se omiten (ADMIN y la plataforma)
   replica la de wasichai con una prueba que las compara.
4. **La geometría de referencia viaja como parámetro.** En los filtros por zona, la zona se lee antes como quien llama y
   se enlaza como GeoJSON (`ST_GeomFromGeoJSON(:p)`), nunca como subconsulta a otra tabla, que no pasaría por los
   alcances de esa tabla.
5. **El filtro de territorio de los tableros ya existe desde F2** (`DashboardFilters`: `locality`, `community`,
   `administrativeUnit`, `areaOfInfluence`; `stakeholder` ignora `community`, ruling P27). F6 lo reutiliza; la ficha
   pide KPI-TER-01 a `GET /api/sgspe/dashboards/indicators/KPI-TER-01`, como dice `api.md`.
6. **El CRS detectado es una propuesta.** GDAL lee el CRS del archivo (`.prj`, GPKG); la persona lo confirma igual
   (CON-024). KML, KMZ y GPX son 4326 por definición.
7. **Reconciliación por código, nunca por nombre.** Los destinos nuevos se reconcilian por `code` como
   `mining_concession`; las comunidades ya registradas sin código se completan a mano antes de importar.
8. **Divulgación por capa en las exportaciones** (§3.5): PUBLICO solo capas públicas, EXTERNO suma las internas y los
   predios sin partida, las confidenciales solo en INTERNO, `land_right` nunca.
9. **Mapa base por endpoint.** `GET /api/sgspe/map/settings` reemplaza la variable de build de F2 del portal; el
   operador mantiene el mapa por slug en `application.yml`, como `sgspe.ai.enabled-organizations`. El portal arma
   `gisModule({ basemap })` después del login con esa respuesta, así que `GeometryField`, el componente `MAP` y
   `/admin` usan el mismo fondo; sus mapas propios lo reciben por la prop `basemap`.
10. **GeoServer caído no rompe el mapa.** El proxy responde `503` con un plazo corto y el portal sigue con las capas por
    API (WAS-ADR-0006).

## 3. Diseño por subsistema

### 3.1 `SpatialGate` y las consultas espaciales

**`SpatialGate`** (`sgspe.territory`) es la única clase que ejecuta SQL de lectura que cruza tablas del tenant:

1. **Permisos.** Recibe la lista de objetos y campos que usa la consulta (`GateRead(objectName, fields)`) y los revisa
   con `Readability`. Sin READ sobre el objeto principal, `403`. Sin READ sobre un objeto o campo secundario, el
   componente que lo usa sale con `available: false` y `missingFields`, nunca cero (RQ-DSH-032), con la forma de los
   KPI (`dashboards.md` §8).
2. **Alcance.** Cada tabla entra como subconsulta filtrada:
   `(SELECT * FROM <tabla> WHERE "organization_id" = :org AND <c1> AND <c2> …) AS <alias>`, donde cada `<ci>` sale de
   `scope.criterion(caller, definition)?.condition(definition, bind)` de cada `RecordReadScope` inyectado (hoy
   `ProjectReadScope` y `ConfidentialAttentionScope`). Así los `RecordCriterion` funcionan sin calificar columnas. Para
   ADMIN y la plataforma no se piden, igual que wasichai; `SpatialGateScopeParityTest` lee el mismo objeto por
   `Records` y por la compuerta con un ANALYST, un ADMIN y un usuario sin asignación y exige los mismos ids.
3. **SQL.** Tablas y columnas de `ObjectDefinitions.current`, entrecomilladas con `SqlIdentifier.quote`; valores
   enlazados; `DatabaseClient` de solo lectura (`SELECT`); nunca escribe.
4. **Resultado.** Ids y medidas. Los atributos personales (titulares en `land_right`, nombres de personas) se leen
   después con `Records`, con los permisos de campo de quien llama.

**Filtros por zona (`ZoneCriterion`).** `GET /api/sgspe/territory/zones/{zoneObject}/{id}/records?of=` lee la zona
(`locality`, `community`, `administrative_unit` o `area_of_influence`) por `Records` como quien llama (`404` si no la
lee) y lista `of` por `Records` con un `RecordCriterion` que une por OR la relación territorial del objeto (la tabla de
`gis.md` §2.4 y las relaciones `locality` y `community`) con `ST_Intersects(<geometría>, ST_GeomFromGeoJSON(:zona))` en
el SRID del campo. `member-suggestions`, `locate` y `buffer-localities` usan el mismo patrón: la geometría de referencia
se lee primero y se enlaza.

**Medidas.** Áreas, longitudes y distancias en el `working_crs` del proyecto; los registros del tenant (comunidad,
predio, concesión) en la zona UTM de su centroide (ASM-064), con `Crs.kt` y `GeoSql.kt` de F2. El prefiltro usa el
índice GIST en 4326 con una envolvente ampliada y la medida exacta va después en UTM (`gis.md` §3.4).

**Endpoints** (contrato de `api.md` §4; todos `GET`, ninguno escribe):

| Endpoint | Caso de `gis.md` §5.3 | Patrón |
|---|---|---|
| `/api/sgspe/areas-of-influence/{id}/summary` | 15 | compuerta (KPI-TER-02 a 04 del área) |
| `/api/sgspe/areas-of-influence/{id}/member-suggestions` | 1, 9 | zona; `alreadyMember` por `Records` |
| `/api/sgspe/territory/zones/{zoneObject}/{id}/records` | 2, 3, 5 | zona |
| `/api/sgspe/territory/concentration` | 4, 6 | compuerta; conteos por localidad, nunca por punto |
| `/api/sgspe/territory/projects/{id}/nearby-conflicts` | 7 | compuerta; vecino más cercano y medida en UTM |
| `/api/sgspe/territory/projects/{id}/component-parcels` | 10 | compuerta; titulares por `Records` |
| `/api/sgspe/territory/projects/{id}/component-communal-land` | 11 | compuerta |
| `/api/sgspe/territory/projects/{id}/concession-territories` | 12 | compuerta |
| `/api/sgspe/territory/projects/{id}/sensitive-distances` | 13 | compuerta |
| `/api/sgspe/territory/projects/{id}/buffer-localities` | 14 | zona (buffer en UTM, `ST_DWithin`) |
| `/api/sgspe/territory/locate` | 16 | zona (punto en polígono) |
| `/api/sgspe/territory/localities/{id}/card` | ficha | `Records` y zona |
| `/api/sgspe/territory/communities/{id}/card` | ficha | `Records` y zona |

El caso 8 (inversiones por localidad) es la capa LYR-23 de `layers/{layer}` y KPI-INV-03 con el filtro de territorio.

**Límites** (`sgspe.territory.queries.*`, configurables): `bufferM` de 1 a 20 000; `distanceKm` de 0.1 a 50;
`limit` hasta 5 000 filas; periodo de `concentration` hasta 5 años. Fuera de rango, `400 OUT_OF_RANGE`; parámetro
desconocido, `422`. `concentration` acepta `of` en `engagement`, `grievance`, `inquiry`, `stakeholder_request`,
`incident` y `early_warning_signal`.

**`ArchitectureTest`.** Dentro de `sgspe.territory`, solo `SpatialGate` y los escritores que ya existen
(`TerritoryImports`, `Areas`, `ProjectComponents`, `GeoSql`) usan `DatabaseClient`; fuera de `sgspe.territory`, nadie
nuevo.

### 3.2 KPI-TER y tablero `territory`

- **KPI-TER-01** (sin valor propio, `dashboards.md` §4.8): reúne KPI-STK-02 por postura, KPI-ENG-01 de los últimos 90
  días (o del periodo pedido), KPI-COM-02 y 03, KPI-GRV-02, KPI-RSK-01, `programs`, KPI-INV-03 por moneda y
  `lastActivity`, cada uno con su `available` y, si falta, `unavailableReason` y `missingFields`. Exige `locality` o
  `community` (si no, `available: false` con `TERRITORY_REQUIRED`). Con `community`, KPI-STK-02 lo informa en
  `ignoredFilters` (ruling P27).
- **KPI-TER-02:** `calculated_area_ha` de la versión `approved` de cada `area_type` del proyecto, por `Records`.
- **KPI-TER-03:** miembros de la versión aprobada por `area_type`, tipo de miembro y `member_condition`, por `Records`.
- **KPI-TER-04:** `project_component` vigentes (`superseded` falso) que intersecan `community.territory` o
  `land_parcel.boundary`, por `tenure_type` y `component_type`; consulta de `SpatialGate` compartida con `summary`.
- **Tablero `territory`:** `GET /api/sgspe/dashboards/territory` con los filtros comunes; exige `project`; reúne
  KPI-TER-02 a 04 y, con `locality` o `community`, KPI-TER-01. La dependencia va de `sgspe.dashboard` a
  `sgspe.territory` (KPI-TER-04), nunca al revés.

### 3.3 GeoServer, proxy WMS y mapa base

**Despliegue en desarrollo.** Servicio `geoserver` en `compose.yml` con la imagen del roadmap
(`docker.osgeo.org/geoserver:3.0.1`; el plan 6.1 comprueba que la etiqueta existe y, si no, fija la última 3.0.x
publicada), solo en loopback del VPS con un puerto libre fijado por ADR-0026, `CORS_ENABLED` apagado (el navegador
nunca llega a GeoServer), datastore hacia `postgis:5432` por la red del compose, volumen con nombre y sin registro de
cuerpos de petición. El backend lo configura con `wasichai.gis.geoserver.*` (`application.yml` para desarrollo por el
túnel; `application-prod.yml` sin valores por defecto, por variables de entorno). Levantarlo en el VPS lo hace el
coordinador con permiso del usuario.

**Publicación (`model/publish_layers.py`).** Biblioteca estándar, patrón de `apply.py`, idempotente:

1. lee la lista de capas públicas de `model/layers.json` (`[{ "layer": "LYR-01", "object": "administrative_unit",
   "geometry": "boundary", "style": "sgspe-lyr-01" }, …]`);
2. consulta `GET /api/gis/layers` y publica las que falten con `POST /api/gis/layers/{object}/{geometry}` (token ADMIN
   de la organización);
3. carga o actualiza los SLD de `model/styles/*.sld` por la REST de GeoServer (credenciales del despliegue por
   variables de entorno, nunca en archivo), únicos por despliegue;
4. tiene `--dry-run` y `--validate-only` y sus pruebas con un GeoServer y un core de imitación.

`provision_tenant.py` y `upgrade_tenants.py` suman el paso al final (opcional con `--skip-layers` si el despliegue no
tiene GeoServer). `test_model.py` exige que cada capa de `layers.json` sea un objeto de `ProjectScope.TENANT`
(`design_docs.py` lo lee de `security.md` §9.7), con una geometría declarada y sin campos con permisos restringidos en
`roles.json`.

**Estilos.** Un SLD por capa en `model/styles/`, con reglas de texto para los nombres (`gis.md` §7.2); los colores salen
de `model/styles/palette.json`, que el portal copia en su build (`src/portal/mapa/paleta.json`) con una prueba que
compara ambos archivos en la CI de sgspe-ui.

**Proxy WMS (`WmsProxy`, `GET /api/sgspe/territory/wms`).**

- Exige el token de wasichai (`401` sin él).
- La lista blanca es el conjunto de capas publicadas de la organización del token que están en `layers.json`, con su
  nombre `<tabla física>__<columna>` resuelto por la metadata de esa organización; cada capa exige READ sobre su objeto.
  Capa fuera de la lista o sin READ, `403`.
- `REQUEST=GetMap` y `GetLegendGraphic` pasan con parámetros en lista blanca (`SERVICE`, `VERSION`, `REQUEST`,
  `LAYERS`, `LAYER`, `STYLES`, `STYLE`, `BBOX`, `SRS`, `CRS`, `WIDTH`, `HEIGHT`, `FORMAT`, `TRANSPARENT`, `TILED`);
  `STYLES` solo con los estilos de `layers.json`; `FORMAT` solo `image/png` y `image/jpeg`; `WIDTH` y `HEIGHT` hasta
  2048.
- `GetCapabilities` se arma con la lista blanca, sin consultar a GeoServer.
- `GetFeatureInfo`, `DescribeLayer`, `SLD`, `SLD_BODY`, `CQL_FILTER`, `FILTER`, `FEATUREID`, `ENV`, `VIEWPARAMS` y
  cualquier otro parámetro: `422` con su código (`WMS_REQUEST_NOT_ALLOWED`, `WMS_PARAMETER_NOT_ALLOWED`).
- Llama a GeoServer con `WebClient`, plazo `sgspe.territory.wms.timeout` (por defecto 5 s); caído o vencido, `503
  GEOSERVER_UNAVAILABLE`; no copia cabeceras de GeoServer salvo `Content-Type`, y agrega `Cache-Control: private,
  max-age=300`.

**Mapa base.** `sgspe.map.base-style-url` (por defecto) y `sgspe.map.base-style-url-by-organization` (mapa slug →
URL), más `sgspe.map.attribution` por URL. `GET /api/sgspe/map/settings` (autenticado) devuelve
`{ basemap }` para la organización de quien llama, con la forma de `BasemapSpec` de `@wasichai/gis`
(`{ type: 'style', url }`, o `{ type: 'none' }` si no hay estilo). El portal lo pasa a `gisModule` al montar la
sesión y a sus `MapView` por prop; la CSP de producción permite el host del estilo según el README de
`@wasichai/gis` («Content-Security-Policy»). `MapSettingsProdTest`
exige que `application-prod.yml` no contenga `tile.openstreetmap.org` (RQ-GIS-013).

### 3.4 Importación con GDAL y destinos nuevos

**Formatos.** `POST /api/sgspe/territory/imports` acepta además `shp` (ZIP con `.shp`, `.shx`, `.dbf` y opcionalmente
`.prj` y `.cpg`), `kml`, `kmz`, `gpkg`, `gdb` (ZIP con una carpeta `.gdb`, driver `OpenFileGDB`) y `gpx`.

**`GdalReader`.**

1. El archivo ya está en el `ImportWorkspace` (límites de tamaño, filas, ZIP sin rutas fuera de la carpeta).
2. `ogrinfo -json -so` con el driver de la lista blanca (`-if ESRI Shapefile|KML|LIBKML|GPKG|OpenFileGDB|GPX`) lista
   las capas, su tipo de geometría, su CRS y sus campos.
3. Si el archivo tiene más de una capa y no vino `layer`, la vista previa devuelve `layers[]` y pide elegir
   (`400 LAYER_REQUIRED`).
4. `ogr2ogr -f GeoJSON` de la capa elegida, **sin reproyectar**, a un archivo del workspace; ese GeoJSON entra al flujo
   de F2 con el CRS de origen confirmado.
5. El CRS detectado viaja en la vista previa como `detectedCrs` (EPSG o `null`); la confirmación exige `sourceCrs`
   del catálogo «CRS de origen» igual que en F2. KML, KMZ y GPX fijan 4326.

Seguridad: el ejecutable sale de `sgspe.territory.gdal.path` (por defecto el del `PATH`); variables de entorno del
proceso limpias, con `GDAL_HTTP_*` vacías, `CPL_VSIL_CURL_ALLOWED_EXTENSIONS=` vacío, `GDAL_DISABLE_READDIR_ON_OPEN`
y `OGR_KML_FOLLOW_LINKS=NO` (o equivalente del driver); argumentos fijos más rutas del workspace, nunca texto del
cliente; corre en `Dispatchers.IO`, con el plazo `IMPORT_TIMEOUT` y la concurrencia de F2; el proceso se mata al
vencer. Sin GDAL instalado, esos formatos responden `422 FORMAT_UNAVAILABLE`, y `GET
/api/sgspe/territory/imports/formats` devuelve los formatos disponibles. La CI instala `gdal-bin` (versión mínima
3.8, que el plan comprueba) en los trabajos de integración.

**Destinos nuevos.** `community.territory` y `environmental_feature.boundary` (MULTIPOLYGON), con `code` (TEXT, único,
opcional), `source_crs`, `source_reference` e `import_batch_id` en ambos objetos (§4.1). La reconciliación es la de
`mining_concession` (`gis.md` §4.4): mismo `code` con otra geometría, actualización con motivo `importación <id>`,
cambio de área y distancia de Hausdorff en el reporte; código nuevo, alta; sin `code`, fila rechazada. El alta de una
comunidad o de un elemento ambiental por importación lleva solo `code`, el nombre (columna elegida) y la geometría;
lo demás se completa en su ficha. `environmental_feature` importado exige `feature_type` (columna elegida o valor fijo
del formulario). `calculated_area_ha` de `community` se escribe en la transacción, como en los otros destinos.

### 3.5 Exportaciones geográficas

`POST /api/sgspe/territory/exports` con `{ object, geometry, format, coordinates, filters, disclosureLevel,
checklistConfirmed }`.

- **Objetos:** los de LYR-01 a LYR-16 (`administrative_unit`, `locality`, `road_access`, `community`,
  `community_sector`, `mining_concession`, `environmental_feature`, `project`, `project_component`,
  `area_of_influence`, `land_parcel`, `school`, `health_facility`); otro, `400 OBJECT_NOT_EXPORTABLE`.
- **Permisos:** READ y `EXPORT` sobre el objeto y CREATE sobre `export_log`, antes de leer nada; la lectura va por
  `Records` como quien exporta (permisos de campo y alcance), con tope `sgspe.exports.max-rows`.
- **Formatos:** `geojson` y `kml` en 4326; `gpkg` y `shp` (ZIP con `.cpg` UTF-8) en el CRS de `coordinates`
  (`UTM`: `working_crs` del proyecto filtrado, obligatorio; `LATLON`: 4326); `csv` y `xlsx` con `easting`,
  `northing` (UTM) y `latitude`, `longitude` del punto o del centroide, más `wkt` de la geometría en el CRS elegido para
  líneas y polígonos. GDAL escribe `kml`, `gpkg` y `shp` desde un GeoJSON temporal; `csv` y `xlsx` los escribe el
  código, como las demás exportaciones.
- **Divulgación por capa** (`Disclosure` de `sgspe.privacy`):

| Nivel | Objetos permitidos | Campos que se quitan |
|---|---|---|
| PUBLICO | Los de capas públicas (LYR-01 a 05, 07, 16) | Los que la regla de PUBLICO ya quita |
| EXTERNO | Los anteriores, `community_sector`, `environmental_feature`, `project` (ubicación y área de interés), los accesos de `project_component` (`component_type` ACCESO) y `area_of_influence`; `land_parcel` de `tenure_type` PROPIEDAD, POSESION o COMUNAL | `registry_record` de los predios; los de EXTERNO; exige `checklistConfirmed` como la matriz de compromisos |
| INTERNO | Todos los de la lista, también sondajes, plataformas y campamento (`project_component`) y los predios de la empresa | Ninguno más allá de los permisos de campo |

  Un objeto o filtro fuera del nivel elegido, `403 DISCLOSURE_NOT_ALLOWED`. `land_right` nunca se exporta.
- **Registro:** fila de `export_log` antes de entregar el archivo, con objeto, formato, nivel, filtros y número de
  registros, como `CommitmentMatrix`.
- El código vive en `sgspe.territory` y usa `sgspe.privacy` (`Disclosure`, `ExportProjects`, el registro); `privacy`
  no gana dependencias.

### 3.6 Validación de geometrías y listado de calidad

- **`POST /api/sgspe/territory/geometry-checks`** con `{ geometry, object, field, project? }` devuelve `{ valid,
  reason, typeMatches, normalized, repaired, areaHaBefore, areaHaAfter, zone: { srid, outOfZone, outOfRegion } }`:
  tipo contra el `geometryType` del campo; `ST_IsValidReason`; reparación propuesta con `ST_MakeValid` y su cambio de
  área en el CRS de cálculo; MultiPolygon de una parte normalizado; zona y región (`gis.md` §4.3). No escribe; exige
  READ sobre el objeto.
- **`GET /api/sgspe/territory/data-quality?check=&object=&project=`** pagina, por `SpatialGate`, las cinco revisiones de
  `gis.md` §8: `INVALID`, `OUT_OF_ZONE`, `STALE_AREA`, `NO_TERRITORY` (QCS, compromisos, riesgos e incidentes sin
  `locality`, `community` ni `location`) y `NO_GEOMETRY` (localidades y comunidades). Cada fila trae objeto, id,
  nombre o código y el detalle de la revisión.

### 3.7 Portal (sgspe-ui, plan 6.3)

Todo sigue `docs/ux/screens.md` y sus lienzos.

- **S12 Mapa territorial (`/territory`).** Mapa base de `/map/settings` o «Sin mapa base»; control de capas por grupo
  (Referencia, Proyecto, Tenencia, Gestión): las capas públicas por WMS (`wmsTileUrl` hacia el proxy, token por
  `transformRequest`) y el resto por GeoJSON; si el proxy da `503`, las WMS se apagan con un aviso y esas capas vuelven
  al GeoJSON de F2; leyenda con `paleta.json`; filtros de proyecto y periodo; calor con `concentration`; captura PNG.
  **Ficha contextual:** panel lateral con la parte territorial (`…/card`) y las cifras de KPI-TER-01 (`indicators`),
  cada componente con su «no disponible»; desde la ficha, los registros de la zona (`zones/…/records?of=`).
- **S09 Mapa del proyecto.** Panel «Análisis» con seis tablas (predios afectados con titulares si hay permiso,
  territorio comunal, elementos sensibles, localidades en un buffer con control de metros, territorios de las
  concesiones, conflictos cercanos); cada fila resalta su geometría.
- **S10 Área de influencia.** «Sugerencias del cálculo» con `member-suggestions`; aceptar una crea el miembro con
  `spatially_suggested` (nada se escribe sin esa acción); resumen con `summary`.
- **S15 Importación.** Formatos de `imports/formats`; elección de capa; CRS detectado como propuesta; destinos
  comunidad y elemento ambiental.
- **Exportar.** Diálogo desde S12 y S09: objeto, formato, coordenadas y nivel (solo los permitidos por la capa, con la
  casilla de EXTERNO).
- **Edición.** Antes de guardar una geometría editada en S13 y S14, `geometry-checks` con el motivo y la reparación
  propuesta; en los formularios con ubicación, un clic llama a `locate` y propone unidad, localidad y comunidad, que la
  persona confirma.
- **S63 Calidad de datos** (nueva, en Territorio): lista paginada por revisión con enlace a cada registro.
- **Tablero territorial:** S12 en modo tablero con los tiles de KPI-TER-02 a 04.

## 4. Cambios en el modelo y en los permisos (plan 6.1)

### 4.1 `model.json`

- `community`: `code` (TEXT, único, opcional), `source_crs` (ENUM «CRS de origen»), `source_reference` (TEXT),
  `import_batch_id` (UUID).
- `environmental_feature`: los mismos cuatro campos.
- Ningún objeto nuevo; ninguna relación nueva.

### 4.2 `roles.json`, `layers.json` y estilos

- `EXPORT` sobre los objetos de §3.5 para los roles que ya exportan o administran territorio (`GIS_ANALYST`,
  `SOCIAL_MANAGER`, `PROJECT_MANAGER`; el plan 6.0 fija la lista en `security.md` §7 con su fuente); `AUDITOR` sin
  `EXPORT`.
- `model/layers.json`, `model/styles/*.sld` y `model/styles/palette.json` (§3.3).
- `compose.yml` con `geoserver`; `develop/example.env` con sus variables.

## 5. Pruebas

| Clase | Casos propios |
|---|---|
| `SpatialGateApiTest` | Sin READ sobre `land_parcel`, `403`; `land_right.owner` ilegible, «no disponible»; predio de un proyecto no asignado no aparece; QCS confidencial no cuenta en `concentration` para quien no la lee; otro tenant invisible; ADMIN ve todo |
| `SpatialGateScopeParityTest` | Mismos ids por `Records` y por la compuerta para ANALYST, ADMIN y sin asignación, en un objeto raíz, un hijo y una QCS confidencial |
| `SpatialQueriesApiTest` | Los 13 endpoints con geometrías sintéticas: una plataforma que corta dos predios y un acceso que cruza tres (predios y longitudes, `gis.md` §9); sugerencias que no escriben; buffer con y sin localidades; vecino más cercano ordenado; límites fuera de rango, `400` |
| `TerritoryDashboardApiTest` | Ficha de una localidad con datos de cada tipo y de otra sin datos (ceros); sin leer `stance`; proyecto no asignado; KPI-TER-04 con plataforma sobre predio PROPIEDAD y acceso sobre territorio comunal; `TERRITORY_REQUIRED` |
| `WmsProxyApiTest` | GeoServer de imitación: sin token `401`; capa de otro tenant o no publicada `403`; sin READ `403`; `SLD_BODY`, `CQL_FILTER` y GetFeatureInfo `422`; GetCapabilities con la lista blanca; GeoServer caído `503`; `FORMAT` y tamaño fuera de la lista `422` |
| `MapSettingsApiTest`, `MapSettingsProdTest` | Estilo por organización y por defecto; sin estilo, `null`; producción sin OSM |
| `GdalImportApiTest` | Un fixture sintético por formato (Shapefile con `.prj` UTM 17S, GPKG con dos capas, KMZ, FileGDB, GPX); `LAYER_REQUIRED`; CRS detectado propuesto pero exigido; ZIP con `../` rechazado; GDAL ausente `422 FORMAT_UNAVAILABLE`; destinos `community` y `environmental_feature` con alta, actualización por código y fila sin código rechazada |
| `TerritoryExportsApiTest` | Cada formato abre y trae los registros; predios en EXTERNO sin `registry_record`; sondajes en EXTERNO `403`; sin `EXPORT` `403`; fila de `export_log`; tope de filas |
| `GeometryChecksApiTest`, `DataQualityApiTest` | Lazo inválido con su motivo y reparación con cambio de área; MultiPolygon de una parte normalizado; fuera de zona; las cinco revisiones del listado |
| Unitarias | `WmsRequestFilterTest`, `GdalCommandTest` (argumentos y entorno), `ExportDisclosureTest`, `ZoneCriterionTest` |
| Modelo | `test_model.py` (capas TENANT sin campos restringidos, campos nuevos), `test_publish_layers.py` |
| `ArchitectureTest` | `DatabaseClient` en `sgspe.territory` solo en `SpatialGate` y los escritores existentes |

Base: `F6ApiTest` (sobre `F5ApiTest`) con un proyecto en UTM 17S, predios, territorio comunal, componentes, elementos
ambientales, localidades con y sin geometría y una QCS confidencial, todo sintético.

## 6. Cambios en los documentos (plan 6.0)

**IDs nuevos en `docs/requirements-analysis.md`** (siguiente libre: ASM-120; ningún GAP, CON ni UPS nuevo; UPS-13
pasa a «Resuelta» con wasichai-ui#38; §20.1 pasa de ASM 119 a 127).

| ID | Contenido |
|---|---|
| ASM-120 | WFS pospuesto (decisión 1): F6 entrega el proxy WMS; las exportaciones cubren a los analistas; RQ-GIS-018 y ASM-056 se leen sin el WFS hasta una fase que lo pida |
| ASM-121 | Capas publicadas en GeoServer: solo maestros de `ProjectScope.TENANT` sin campos restringidos, en `model/layers.json`; el proxy solo entrega imágenes (sin GetFeatureInfo, filtros ni SLD del cliente) |
| ASM-122 | `SpatialGate` compone todos los `RecordReadScope` registrados y es la única vía de SQL de lectura que cruza tablas del tenant; las geometrías de referencia viajan como parámetro |
| ASM-123 | Destinos nuevos de importación `community.territory` y `environmental_feature.boundary` con `code` y campos de origen, reconciliados por código (decisión 3) |
| ASM-124 | Exportaciones geográficas solo de LYR-01 a LYR-16, con divulgación por capa (§3.5; decisión 4) |
| ASM-125 | Mapa base por organización servido por `GET /api/sgspe/map/settings` como `BasemapSpec`, aplicado a `gisModule` tras el login y a los mapas propios por prop (decisión 2) |
| ASM-126 | Límites de las consultas espaciales (§3.1), configurables |
| ASM-127 | GDAL: versión mínima, drivers en lista blanca, sin red, CRS detectado como propuesta; sin GDAL, `422 FORMAT_UNAVAILABLE` |

**Huecos y contradicciones que cierra.**

- **C1 · WFS en F6.** `roadmap.md` F6, `api.md` (`GET /api/sgspe/territory/wfs`, acción `EXPORT`), `gis.md` §4.6 y
  §10 y RQ-GIS-018 lo daban en F6: pasan a «pospuesto (ASM-120)».
- **C2 · Importación con CRS a MULTIPOLYGON.** `gis.md` §4.1 lo dejaba como propuesta para F6 «agregando primero los
  campos al modelo de dominio»: `domain-model.md` y `database-model.md` suman los campos de §4.1 y `gis.md` §4.1 los dos
  destinos.
- **C3 · Ficha contextual.** `screens.md` S12 y `api.md` ya piden KPI-TER-01 por `indicators/{code}`; `dashboards.md`
  §4.8 lo confirma y suma `TERRITORY_REQUIRED` e `ignoredFilters` con `community`.
- **C4 · Mapa base por organización y UPS-13.** `gis.md` §6.4 nombraba la propiedad pero no cómo llega al portal;
  suma `/map/settings` (`api.md`) y el portal deja la variable de build de F2. UPS-13 (`requirements-analysis.md`,
  `gis.md` §1.3, §6.4 y §11, ASM-048, ASM-085, `roadmap.md` F6 «Riesgos») pasa a «Resuelta con wasichai-ui#38 en
  `@wasichai/gis` 1.0.0»: ya no hay mapas sin fondo en producción.
- **C5 · Lista de capas públicas.** `gis.md` §2.2 y §7.1 la dan en prosa; pasa a `model/layers.json` con su prueba.
- **C6 · Exportaciones de capas de gestión.** `gis.md` §4.6 y `api.md` hablaban de «cada objeto de una capa»: se acotan
  a LYR-01 a LYR-16 y la acción `EXPORT` en `api.md` §2 y `security.md` §7 nombra los objetos.
- **C7 · ADR-0015.** «Se acepta al iniciar F6»: se acepta en la parte de GeoServer interno, estilos y proxy WMS; los
  mapas de informe por GetMap siguen para F8 con su spike. ADR-0019 sigue «Propuesto · se acepta al iniciar F2»
  aunque la importación está en uso desde F2: pasa a «Aceptado».
- **C8 · `ArchitectureTest` y SQL propio.** `gis.md` §5.2 describe la compuerta sin decir dónde vive ni cómo se vigila:
  suma `SpatialGate` y la regla.
- **C9 · Pantalla de calidad.** No tenía número: S63 en `screens.md`.

**Además.** `api.md` §4 con `/map/settings`, `imports/formats`, los errores nuevos (`LAYER_REQUIRED`,
`FORMAT_UNAVAILABLE`, `OBJECT_NOT_EXPORTABLE`, `DISCLOSURE_NOT_ALLOWED`, `WMS_REQUEST_NOT_ALLOWED`,
`WMS_PARAMETER_NOT_ALLOWED`, `GEOSERVER_UNAVAILABLE`, `TERRITORY_REQUIRED`) y el contrato de exportación de §3.5;
`security.md` §10 con el proxy y la lista blanca; `architecture.md` con GeoServer en el compose y GDAL en la CI;
`gis.md` §4, §6, §7.1, §8, §10 y §11; `dashboards.md` §4.8; `roadmap.md` F6 (alcance, endpoints, definición de
terminado de §7 y, al cerrar, «Estado»); `requirements-traceability.md` (RQ-GIS de F6 a «Diseñado» con sus pruebas);
`CLAUDE.md` («Dónde está cada cosa» con `SpatialGate`, el proxy, GDAL y `publish_layers.py`; el puerto de GeoServer);
`docs/develop/README.md` (GeoServer y GDAL en desarrollo).

## 7. Reparto en planes

| Plan | Rama | Depende de | Tareas principales | Estimado |
|---|---|---|---|---|
| 6.0 · Documentos | `docs/fase-6-0-documentos` | Esta especificación | §6 por documento; ASM-120 a 127; `check_traceability.py` en verde; PR | ~9 tareas |
| 6.1 · Modelo y GeoServer | `feat/fase-6-1-modelo` | 6.0 | `design_docs.py`; `model.json` (§4.1); `roles.json` (§4.2); `layers.json`, SLD y paleta; `publish_layers.py` y sus pruebas; pasos en `provision_tenant.py` y `upgrade_tenants.py`; `test_model.py`; `compose.yml` y `example.env`; base `F6ApiTest`; PR | ~10 tareas |
| 6.2a · Consultas y KPI-TER | `feat/fase-6-2a-consultas` | 6.1 | `SpatialGate` y su paridad (primera tarea); `ArchitectureTest`; `ZoneCriterion`; los 13 endpoints; KPI-TER-01 a 04 y tablero `territory`; medición con volumen (coordinador); PR | ~14 tareas |
| 6.2b · Servicios SIG | `feat/fase-6-2b-servicios` | 6.1; `SpatialGate` de 6.2a para `data-quality` | `WmsProxy`; `MapSettings`; `GdalReader` y formatos; destinos nuevos; `TerritoryExports` y `GdalWriter`; `geometry-checks`; `data-quality` (tras rebase sobre la primera tarea de 6.2a); `gdal-bin` en la CI; PR | ~13 tareas |
| 6.3 · Portal | `feat/fase-6-3-portal` en `sgspe-ui` | 6.0 (contrato) | §3.7 completo con `mockFetch`; `paleta.json` y su prueba; Playwright de ficha y exportación; integración contra el backend real al final; PR | ~13 tareas |

**Paralelismo.** 6.0 primero, sola. Con 6.0 en `main`: 6.1 y 6.3 en paralelo. Con 6.1 en `main`: 6.2a y 6.2b en
paralelo, en worktrees separados; 6.2b toma `SpatialGate` con un rebase sobre la primera tarea de 6.2a antes de
`data-quality`. Las integraciones las corre el coordinador una tras otra contra `sgspe_sdd_test`, o la CI.

**Tareas del coordinador de la fase.** GeoServer en el VPS (con permiso del usuario); subir el tenant `demo` a F6 y
correr `publish_layers.py`; medir las consultas de la compuerta con volumen en una base propia (10 proyectos, 20 000
predios, 5 000 componentes, 2 000 comunidades, 50 000 QCS; criterio: `component-parcels` y `concentration` bajo 1 s en
el p95 con el índice GIST, sin barrido secuencial de las tablas con geometría); humo del portal con el navegador.

**Definición de terminado.** La de `roadmap.md` F6 sin el WFS: pruebas de `gis.md` §9 de F6 (proxy WMS, compuerta,
componentes × predios, sugerencias, mapa base) y las de §5; `ArchitectureTest` con la regla de `SpatialGate`; capas
publicadas en el tenant de desarrollo y vistas en el mapa por el proxy; la medición anotada en `roadmap.md` F6
«Estado»; el tenant `demo` en F6. Al cerrar F6, la release 2.0.0 (sgspe #36 y sgspe-ui #23 con `Release-As: 2.0.0`),
con la confirmación del usuario antes de mezclar.

**Orden de despliegue.** Modelo y roles (`apply.py` → `apply_roles.py` → `apply.py --workflows`), después
`publish_layers.py` con GeoServer arriba; un backend de F6 sin GeoServer funciona y el proxy responde `503`.

## 8. Riesgos y consecuencias aceptadas

**Consecuencias aceptadas.**

1. **Sin WFS** hasta que una fase lo pida: un analista con QGIS trabaja con archivos exportados, no con una conexión
   viva.
2. **Comunidades sin código no se importan** hasta completar su `code` a mano.
3. **Las capas de gestión no se exportan** como archivo geográfico; se ven en el mapa con permisos.
4. **El calor agrega por localidad:** registros sin localidad ni comunidad no cuentan (aparecen en el listado de
   calidad como `NO_TERRITORY`).

**Riesgos.**

1. **GAP-011 y GAP-012 siguen abiertas:** mapa base, fuentes y licencias, y formatos reales; todo queda configurable y
   los formatos se limitan si GAP-012 lo pide.
2. **GDAL como dependencia nativa:** versiones distintas entre la CI, el desarrollo y un despliegue futuro sin imagen;
   se mitiga con versión mínima, `imports/formats` y `422 FORMAT_UNAVAILABLE`.
3. **Costo de las subconsultas con alcance en los cruces espaciales:** lo decide la medición de §7; respaldo, prefiltro
   por proyecto antes de la intersección.
4. **GeoServer lee la base sin permisos (CON-054):** acotado por §2.2 (capas TENANT sin campos restringidos, solo
   imágenes); una capa mal declarada en `layers.json` la detiene `test_model.py`.
5. **Paridad de `SpatialGate` con wasichai:** si wasichai cambia cuándo pide los `RecordReadScope`, la prueba de paridad
   lo detecta.
6. **Etiqueta de la imagen de GeoServer:** si `3.0.1` no existe, 6.1 fija la publicada y lo anota.
