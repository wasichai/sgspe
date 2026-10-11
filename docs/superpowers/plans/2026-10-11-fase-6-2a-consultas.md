# SGSPE · Fase 6, plan 6.2a (backend: consultas espaciales y KPI-TER): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Escribir en `sgspe.territory` la compuerta única de SQL propio (`SpatialGate`), los filtros por zona
(`ZoneCriterion`) y los 13 endpoints de consulta espacial de `api.md` §4 con sus límites configurables
(`sgspe.territory.queries.*`), y en `sgspe.dashboard` KPI-TER-01 a 04 con el tablero `territory`, más la medición con volumen
de las consultas de la compuerta.

**Architecture:** `SpatialGate` revisa READ y la legibilidad de cada objeto y campo que una consulta nombra
(`Readability`), y arma cada tabla del tenant como una subconsulta filtrada por la organización y por los criterios de todos
los `RecordReadScope` registrados, que pide al compuesto de wasichai (`RecordReadScopes.criteria`, la misma llamada y la
misma regla de omisión que `RecordService`); ejecuta solo `SELECT` con valores enlazados por `DatabaseClient` y devuelve ids y
medidas. Los filtros por zona leen la zona por `Records` como quien llama y la enlazan como GeoJSON en un `RecordCriterion`
(`ZoneCriterion`), así `RecordService` sigue aplicando permisos, campos y alcance. Los atributos personales (titulares) se
leen después por `Records`. `sgspe.dashboard` suma `TerritoryKpis` y el tablero `territory`; KPI-TER-04 y `summary`
comparten `ComponentLand`, que vive en `sgspe.territory` (la dependencia va de `dashboard` a `territory`).

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 1.0.0 (sin
starters nuevos; `wasichai-spring-boot-starter-gis` ya está), R2DBC con `DatabaseClient`, PostGIS 3.6 sobre PostgreSQL 18,
Jackson 3 (`tools.jackson`), JUnit 5 y Mockito con `wasichai-test` (`WasichaiIntegrationTest`).

**Spec:** `docs/superpowers/specs/2026-10-11-fase-6-design.md` (vinculante): restricciones globales; §2.1 decisión 5; §2.2
elecciones 3 (los alcances se componen), 4 (la geometría de referencia viaja como parámetro) y 5 (el filtro de territorio de
los tableros ya existe); §3.1 (`SpatialGate`, `ZoneCriterion`, medidas, endpoints, límites, `ArchitectureTest`); §3.2 (KPI-TER y
tablero `territory`); §5 (filas `SpatialGateApiTest`, `SpatialGateScopeParityTest`, `SpatialQueriesApiTest`,
`TerritoryDashboardApiTest`, `ZoneCriterionTest` y `ArchitectureTest`); §7 (fila 6.2a, la medición con volumen y la definición
de terminado); §8 (consecuencia 4; riesgos 3 y 5). Contrato común de los planes de F6 (la firma de `SpatialGate`, que 6.2b
consume en paralelo, y las ayudas de `F6ApiTest` que crea 6.1). Lo que la especificación asigna a 6.2b (`WmsProxy`,
`MapSettings`, GDAL, exportaciones, `geometry-checks`, `data-quality`) y a 6.3 (portal) queda fuera.

## Global Constraints

- **Repositorio y rama.** Todo el trabajo en `~/IdeaProjects/sgspe`, en la rama `feat/fase-6-2a-consultas` creada desde
  `main` con los planes 6.0 y 6.1 integrados; ramas de tarea `f62a/t<k>` integradas con `--no-ff`. El Paso 1 de la Tarea 1
  lo comprueba. 6.2b trabaja en paralelo en otro worktree y toma `SpatialGate` con un rebase sobre la Tarea 1 de este plan:
  la firma de `GateRead`, `GateTables`, `GateOutcome` y `SpatialGate.select` no cambia después de la Tarea 1.
- **Versiones.** wasichai `1.0.0` exacto, Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`, ktlint `1.7.1`.
  Ningún starter nuevo.
- **Idioma.** Español (es-PE) en documentos, comentarios y `detail`; nombres técnicos en inglés (ADR-0009); constantes de la
  API en inglés UPPER_SNAKE; propiedades del cuerpo en camelCase; atributos con su nombre del modelo en snake_case; valores
  ENUM en español MAYÚSCULAS sin tildes, exactamente como el modelo (`PROPIEDAD`, `FUENTE DE AGUA`, `CASERIO`).
- **Arquitectura.** Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService`
  ni `ObjectSchemaManager`; ningún literal de rol en `src/main/kotlin`; `scope` y `privacy` solo dependen de `common`; los
  tenants solo con `Organizations`; ningún bean de estereotipo con el nombre simple de otro (`ArchitectureTest`). Dentro de
  `sgspe.territory`, solo `SpatialGate` y los escritores que ya existen usan `DatabaseClient`; fuera, nadie nuevo; solo
  `SpatialGate` usa `RecordReadScopes` (Tarea 1). Ningún import sin uso: ktlint de este repositorio no los quita.
- **Lecturas.** Nunca `asPlatform` dentro de una petición: todo se lee como quien llama. Todo SQL propio con valores enlazados
  y nombres de tabla y columna de la definición (`ObjectDefinitions.current`), entrecomillados con `SqlIdentifier.quote`; los
  SRID salen de la metadata del campo (`wasichai.gis.srid`), nunca del cliente. Nada de este plan escribe un registro.
- **Medidas.** Áreas, longitudes y distancias de un proyecto en su `working_crs`; las de registros del tenant (comunidad,
  concesión) en la zona UTM de su centroide (ASM-064); nunca `ST_Area`, `ST_Length` ni `ST_Distance` sobre grados. El
  prefiltro usa el índice GIST en 4326 con una envolvente ampliada (`SpatialSql.degrees`) y la medida exacta va en UTM.
- **Tenants en F5.** Un tenant que todavía no tiene el modelo de F6 sigue con F5: este plan no lee los campos nuevos de 6.1
  (`code` y los de origen de `community` y `environmental_feature`), y lo que un tenant no tiene sale «no disponible» por
  `Readability`, nunca como un 500.
- **Errores.** Todo 400, 403, 404 o 409 propio lleva su código en `errors[0].message` y el campo en `errors[0].field`
  (`Finding.toCodedException`). Los códigos de este plan: `OUT_OF_RANGE`, `INVALID_VALUE`, `INVALID_OF`, `ZONE_NOT_FOUND`,
  `PERIOD_NOT_APPLICABLE`, `PROJECT_NOT_FOUND`, `AREA_NOT_FOUND`, `LOCALITY_NOT_FOUND`, `COMMUNITY_NOT_FOUND`,
  `WORKING_CRS_REQUIRED`, `PROJECT_REQUIRED`, `TOO_MANY_RECORDS` y la razón de indicador `TERRITORY_REQUIRED`. Un parámetro
  desconocido es `422` (`StrictQuery`).
- **Lecturas acotadas.** Las consultas de la compuerta piden `limit + 1` filas y responden `truncated`; las lecturas por
  `Records` usan `Records.all(max)` con `400 TOO_MANY_RECORDS`, nunca un recorte silencioso.
- **Datos.** Ficticios: correos `@sgspe.test`, «Proyecto Ejemplo A», «Caserío Ejemplo», «Comunidad Ejemplo», «Persona
  Ejemplo»; geometrías sintéticas alrededor de `SyntheticGeo.ORIGIN` (RQ-GIS-021). Ningún nombre real en nada versionado.
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde
  `~/IdeaProjects/sgspe`; `./gradlew ktlintFormat` antes de `build`; `yarn format` antes de commitear yaml o json. El token
  nunca va a un archivo ni a la salida. Con poca memoria, `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Integración: la escribe el implementador, la corre el coordinador.** Las clases `*ApiTest` necesitan PostgreSQL con
  PostGIS. Ningún implementador ni subagente corre `integrationTest`, ni toca Docker, el túnel, una base de datos o el tenant
  de desarrollo. El implementador corre las unitarias de su tarea, compila las de integración con
  `./gradlew compileTestKotlin` y entrega la tarea marcada **«integración pendiente: <clases>»**. El coordinador las corre una
  tras otra contra `sgspe_sdd_test` (o las deja a la CI del PR):
  `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'`.
  La primera clase de una JVM aprovisiona el modelo de F6 (más de 10 minutos por el túnel); conviene correr juntas las clases
  de una tarea.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo; al final, tras una línea en
  blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas. Push y PR
  en la Tarea 16.

### Comandos

Unitarias de una clase o de un paquete (sin base):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase o paquete>'
```

Compilar las pruebas de integración sin correrlas (lo único que el implementador hace con ellas):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Build completo (ktlint, todas las unitarias y la compilación de las de integración):

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

## Review Focus

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la
tarea dueña del código:

1. **Un campo GEOMETRY con un SRID distinto de 4326 en la metadata (RQ-GIS-022).** Si el filtro o el cruce supusieran 4326,
   PostGIS rechazaría la operación por SRID mezclados o, peor, compararía grados con metros. Pruebas: `ZoneCriterionTest`
   «la zona se transforma al SRID del campo, nunca se supone 4326» (Tarea 3) y `SpatialGateTest` «srid sale de la metadata
   del campo declarado» (Tarea 1).
2. **Una zona sin geometría (una localidad con solo su punto, una comunidad sin territorio).** El filtro por zona tiene que
   seguir trayendo lo que la nombra por relación, sin un 500 ni una lista vacía por la geometría ausente. Pruebas:
   `ZoneCriterionTest` «una zona sin geometría filtra solo por relación» (Tarea 3) y `SpatialZoneQueriesApiTest` «una
   localidad sin límite trae lo que la nombra» (Tarea 5).
3. **Un proyecto sin `working_crs`.** Las medidas en metros y hectáreas no tienen CRS: tiene que ser un `409
   WORKING_CRS_REQUIRED` claro, nunca una medida en grados. Pruebas: `SpatialLandQueriesApiTest` «un proyecto sin working_crs
   es 409» (Tarea 7) y `SpatialSuggestionsApiTest` «el buffer de un proyecto sin working_crs es 409» (Tarea 6).
4. **Componentes reemplazados por una versión nueva (`superseded`).** Si los cruces contaran las versiones viejas, un predio
   ya liberado seguiría pidiendo permiso. Prueba: `SpatialLandQueriesApiTest` «una plataforma movida deja de cruzar sus
   predios viejos» (Tarea 7).
5. **Un área de influencia sin polígono (es opcional, CON-025).** `member-suggestions` no puede quedar vacío en silencio:
   usa el área de interés y los componentes del proyecto. Prueba: `SpatialSuggestionsApiTest` «un área sin polígono sugiere
   por los componentes del proyecto» (Tarea 6).

---

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | §2.2 elección 3 pide que `SpatialGate` «llame a todos los `RecordReadScope` inyectados» y que la regla de omisión «replique la de wasichai». En wasichai 1.0.0, `RecordService` pide los alcances por `RecordReadScopes.criteria(caller, definition)` (bean único y no reemplazable, `WasichaiDataAutoConfiguration.recordReadScopes`): devuelve vacío si `caller` es null (la plataforma o una automatización) o si `!caller.isAdmin && scopes.isNotEmpty()` es falso (`appliesTo`); si no, cada `RecordReadScope` en `@Order`, con la definición completa (no la proyección legible). `RecordService` además aplica `AccessPolicy.ownerFilter` (`created_by`) y cada criterio entre paréntesis después de `organization_id` (`PhysicalTableRecordStore.whereClause`) | `SpatialGate` llama a ese mismo `RecordReadScopes.criteria` en vez de copiar la regla: es la regla exacta por construcción, y un cambio de wasichai se aplica solo. Suma `AccessPolicy.ownerFilter` (hoy siempre null: ningún rol usa `own_records_only`, ASM-069, `test_apply_roles.py`) y pone cada criterio entre paréntesis después de `organization_id`, en el mismo orden que el store. `SpatialGateScopeParityTest` sigue teniendo sentido: compara las filas de `Records` y de la compuerta para ANALYST, ADMIN y sin asignación (Tareas 1 y 2) |
| 2 | El contrato común dice que quien llama «sale del contexto de la petición, como en `Records`/`Readability`» | `SpatialGate` lo toma con `Callers.require()`, que es `CurrentUser.require()` de wasichai: el `AuthenticatedUser` del token por `ReactiveSecurityContextHolder`, como `RecordService.caller()` para una persona. No tiene modo plataforma: fuera de una petición es el `401` de `UnauthorizedException`, nunca una lectura sin permisos. La legibilidad es `Readability(metadata)` (`MetadataService.definitionOf`, filtrada por quien llama), una por consulta (Tarea 1) |
| 3 | §5 pide en `SpatialGateApiTest` que «un predio de un proyecto no asignado no aparece», pero `land_parcel` es un maestro del tenant (`ProjectScope.TENANT`): ningún alcance lo acota | La prueba exige que **los componentes** de un proyecto no asignado no aparezcan, ni sus cruces con predios, y que el proyecto ajeno sea `404 PROJECT_NOT_FOUND` (Tarea 11) |
| 4 | §5 nombra `land_right.owner`, que no existe | El campo restringido de los titulares es `land_right.holder_person` (`roles.json`: lo leen `SOCIAL_MANAGER`, `COMMUNITY_RELATIONS_OFFICER` y `GIS_ANALYST`); sin él, `component-parcels` responde los cruces con `holders: null`, `available: false` y `missingFields: ["land_right.holder_person"]` (Tareas 7 y 11) |
| 5 | §5 junta los 13 endpoints en una sola clase `SpatialQueriesApiTest` | Una clase por grupo de endpoints, para que cada tarea entregue su prueba y el coordinador las corra por separado: `SpatialZoneQueriesApiTest`, `SpatialSuggestionsApiTest`, `SpatialLandQueriesApiTest`, `SpatialProximityApiTest`, `SpatialAggregatesApiTest` y `TerritoryCardsApiTest`; juntas cubren los casos de la fila (Tareas 5 a 10) |
| 6 | §3.1 dice que en `sgspe.territory` usan `DatabaseClient` «`SpatialGate` y los escritores que ya existen (`TerritoryImports`, `Areas`, `ProjectComponents`, `GeoSql`)»; hoy solo `GeoSql` lo importa (los otros tres pasan por él) | La regla permite la lista de la especificación (no rompe nada y deja a esos escritores como están) y fija fuera de `territory` la lista de hoy: `common/DependentsDeleteGuard`, `DerivedVersions`, `Idempotency`, `UserReferenceGuard` y `evidence/EvidenceWriteGuard` (Tarea 1) |
| 7 | `api.md` da solo la raíz de cada respuesta (`{ rows[] }`, `{ items[] }`, `{ cells[] }`) | Las filas tienen la forma de «Interfaces con el portal»; toda respuesta de la compuerta lleva `available` y, si falta leer algo, `missingFields` (`available: false` quiere decir «lista incompleta»: trae lo que sí se leyó, nunca un cero); las de lista, `truncated`. El plan 6.0 copia estas formas a `api.md`; si no lo hizo, la Tarea 16 las agrega |
| 8 | KPI-TER-01 «no tiene un valor propio» y trae «cada componente con su propio `available`» (`dashboards.md` §4.8), sin forma fijada | KPI-TER-01 sale disponible con `value: null` y un desglose `components` con una fila por componente, en el orden de §4.8: la fila de un KPI es su `IndicatorValue` entero más `key` (su código); `programs` y `lastActivity` llevan `key`, `available`, `value` y, si faltan, `unavailableReason` y `missingFields`. Sin `locality` ni `community`, `available: false` con la razón nueva `TERRITORY_REQUIRED` y `missingFields: ["locality", "community"]` (Tareas 12 y 13) |
| 9 | KPI-ENG-01 dentro de KPI-TER-01 es «de los últimos 90 días (o del periodo pedido)», pero `DashboardFilters` siempre tiene periodo (el mes en curso por defecto) | `DashboardFilters` gana `periodGiven` (vino `periodStart` o `periodEnd`); sin periodo pedido, la fila de KPI-ENG-01 cuenta de hoy menos 89 días a hoy, en Lima (Tareas 12 y 13) |
| 10 | §3.2: el tablero `territory` «exige `project`», pero los tableros resuelven `project` por defecto («mis proyectos») | Sin `project` en la consulta, `GET /api/sgspe/dashboards/territory` es `400 PROJECT_REQUIRED` (`project`); con `locality` o `community`, KPI-TER-01 va primero y después KPI-TER-02 a 04 (Tarea 13) |
| 11 | `summary` da «localidades por tipo de área» y «componentes que cruzan tierras comunales o privadas» sin fijar qué es privado | `localitiesByType` cuenta las localidades miembro de esa versión por `locality_type`; los componentes son los del proyecto del área, con la misma consulta que KPI-TER-04 (`ComponentLand`): comunales los que cruzan `community.territory` o un predio `COMUNAL`; privados, un predio `PROPIEDAD` o `POSESION` (los de `PROPIEDAD DE LA EMPRESA` son de la empresa y no piden permiso de terceros) (Tarea 9) |
| 12 | `concentration` «por localidad, nunca por punto»; la consecuencia 4 de §8 dice que cuentan los que tienen localidad o comunidad | Cada evento cuenta en su `locality` y, sin ella, en su `community`; sin ninguna, no cuenta. La celda trae el punto de su localidad (`location` o un punto sobre `boundary`) o de su comunidad (un punto sobre `territory`). Periodo por defecto, los 12 meses que terminan hoy; a lo sumo 5 años (Tarea 9) |
| 13 | `nearby-conflicts` lista «conflictos locales, incidentes y alertas»; `local_conflict` no tiene geometría ni proyecto y se ata a sus localidades por la M2M `local_conflict_locality`, que `GateTables` no expresa | Incidentes y señales de alerta por la compuerta (su `location` o la de su `locality`); los conflictos `ACTIVO`, por `Records` con sus localidades por `Records.related`, hasta `max-related` (200, `400 TOO_MANY_RECORDS` si pasan), a la distancia de su localidad más cercana, que da una consulta de la compuerta (Tarea 8) |
| 14 | `sensitive-distances` no tiene parámetro en `api.md`, pero el caso 13 de `gis.md` §5.3 es «a menos de una distancia» | Acepta `distanceKm` (el mismo rango de 0.1 a 50; por defecto `default-distance-km`, 5) y considera sensibles `ZONA DE PROTECCION`, `FUENTE DE AGUA`, `SITIO SAGRADO` y `RESTO ARQUEOLOGICO` (no `MICROCUENCA` ni `CERRO`) (Tarea 8) |
| 15 | §3.1 da `limit` hasta 5 000 filas sin decir qué pasa al pasarlo | `limit` es un parámetro opcional de las consultas de la compuerta (de 1 a `max-rows`, por defecto `max-rows`); más filas que `limit` se recortan con `truncated: true`, como las capas de F2. Las lecturas que alimentan cifras (KPI-TER-04, `summary`) no se recortan: `400 TOO_MANY_RECORDS` (Tareas 4, 7 y 9) |
| 16 | `locate` devuelve «la unidad administrativa, la localidad y la comunidad» sin decir cuál si caen varias | Unidad: la de nivel más fino que contiene el punto (DISTRITO, PROVINCIA, DEPARTAMENTO), y a igual nivel la primera por nombre e id; localidad (por su `boundary`) y comunidad (por su `territory`): la primera por nombre e id. Lo que quien llama no puede leer sale `null` y su objeto en `unavailable` (Tarea 5) |

## Lo que este plan toma de los planes 6.0 y 6.1

- **Documentos (6.0).** ASM-122 (la compuerta) y ASM-126 (los límites); los endpoints de `api.md` §4 y los códigos nuevos de
  §3.3; `dashboards.md` §4.8 con `TERRITORY_REQUIRED` e `ignoredFilters` con `community`; `CLAUDE.md` con `SpatialGate`.
- **Modelo y base de pruebas (6.1).** `src/test/kotlin/sgspe/F6ApiTest.kt` (abstracta, sobre `F5ApiTest`) con, en el mismo
  archivo, `object SyntheticGeo` (`ORIGIN = -78.50 to -7.90`, en UTM 17S; `square`, `multiSquare`, `point(lon, lat)`,
  `line(vararg lonLat: Pair<Double, Double>)` y `offset(lon, lat, dxM, dyM): Pair<Double, Double>`) y las ayudas
  `suspend fun utmProject(code: String = …): UUID`, `locality(name, location)`, `communityWithTerritory(name, territory,
  code = null)`, `parcel(code, tenure, boundary, community = null)`, `parcelOwner(parcel, ownerName)`, `component(project,
  code, type, geometry)` (por `POST /api/sgspe/project-components`), `environmentalFeature(name, type, boundary = null,
  location = null, code = null)` y `confidentialGrievance(project, locality)` (la registra un `GRIEVANCE_MANAGER` asignado),
  todas `suspend`, con `UUID` y con las geometrías como `Map<String, Any>`. `offset` pasa metros a grados con la aproximación
  equirectangular local (menos de 0.5 % de error cerca de `ORIGIN`): por eso las pruebas que miden comparan con 1 % de
  tolerancia. Este plan arma sus geometrías solo con `offset` (rectángulos, puntos y líneas en metros desde `ORIGIN`; `square`
  de 6.1 está centrado).

## Interfaces con el portal (plan 6.3)

Lo que el portal consume de este plan (el plan 6.0 lo copia a `api.md`; la Tarea 16 lo comprueba). En toda respuesta de la
compuerta: `available` (falso si falta leer algo: la lista trae lo legible), `missingFields` (solo si falta algo, como
`objeto.campo`) y, en las listas, `truncated`.

- `GET /api/sgspe/territory/zones/{zoneObject}/{id}/records?of=&periodStart=&periodEnd=&page=&size=` → la página de
  registros de wasichai (`content[]`, `page`, `size`, `totalElements`, `totalPages`). `zoneObject`: `locality`, `community`,
  `administrative_unit`, `area_of_influence`; `of`: `stakeholder`, `commitment`, `social_risk`, `grievance`, `inquiry`,
  `stakeholder_request`, `engagement`, `incident`, `early_warning_signal`. Errores: `404 ZONE_NOT_FOUND`, `400 INVALID_OF`,
  `400 PERIOD_NOT_APPLICABLE` (`stakeholder` y `social_risk` no tienen fecha), `400 OUT_OF_RANGE`, `400 INVALID_VALUE`.
- `GET /api/sgspe/territory/locate?lon=&lat=` → `{ administrativeUnit: { id, name, level } | null, locality: { id, name,
  localityType } | null, community: { id, name } | null, unavailable: [objeto] }`.
- `GET /api/sgspe/areas-of-influence/{id}/member-suggestions?bufferM=` → `{ available, missingFields?, suggestions[]: {
  object, recordId, name, relation: INTERSECTS | WITHIN_BUFFER, alreadyMember: Boolean | null } }`; sin `bufferM`, solo
  `INTERSECTS`. `404 AREA_NOT_FOUND`; `409 WORKING_CRS_REQUIRED` si hay `bufferM` y el proyecto no tiene CRS.
- `GET /api/sgspe/territory/projects/{id}/buffer-localities?bufferM=` → `{ localities[]: { id, name, localityType } }`.
- `GET /api/sgspe/territory/projects/{id}/component-parcels?limit=` → `{ available, missingFields?, truncated, rows[]: {
  component: { id, code, componentType }, parcel: { id, code, tenureType }, lengthM, areaHa, holders: [{ object: person |
  organization, id, name }] | null } }`. `lengthM` en accesos (`trace`), `areaHa` en huellas (`footprint`), en el
  `working_crs`.
- `GET /api/sgspe/territory/projects/{id}/component-communal-land?limit=` → `{ …, rows[]: { component: { id, code,
  componentType }, community: { id, name }, lengthM, areaHa } }`.
- `GET /api/sgspe/territory/projects/{id}/concession-territories?limit=` → `{ …, rows[]: { concession: { id, code, name },
  object: community | community_sector | locality, id, name, localityType, areaHa } }` (`areaHa` en la zona UTM del centroide;
  `null` para un punto).
- `GET /api/sgspe/territory/projects/{id}/sensitive-distances?distanceKm=&limit=` → `{ …, rows[]: { component: { id, code,
  componentType }, feature: { id, name, featureType }, distanceM } }`, de la más cercana a la más lejana.
- `GET /api/sgspe/territory/projects/{id}/nearby-conflicts?distanceKm=&limit=` → `{ …, items[]: { object: incident |
  early_warning_signal | local_conflict, id, label, distanceM } }`, ordenados por distancia.
- `GET /api/sgspe/territory/concentration?of=&project=&periodStart=&periodEnd=&limit=` → `{ …, of, periodStart, periodEnd,
  cells[]: { object: locality | community, id, name, point, count } }`; `of`: `engagement`, `grievance`, `inquiry`,
  `stakeholder_request`, `incident`, `early_warning_signal`.
- `GET /api/sgspe/areas-of-influence/{id}/summary` → `{ available, missingFields?, areaHa, localitiesByType[]: {
  localityType, count }, componentsOnCommunalLand, componentsOnPrivateLand }` (un componente de una cifra que falta, `null`).
- `GET /api/sgspe/territory/localities/{id}/card?project=` → `{ locality, geometry: { location, boundary }, administrativeUnit,
  communities[]: { id, name }, areasOfInfluence[]: { id, project, areaType, versionNumber, memberCondition }, unavailable[] }`;
  `GET …/communities/{id}/card?project=` → `{ community, geometry: { territory }, administrativeUnit, sectors[]: { id, name },
  areasOfInfluence[], unavailable[] }`. `locality` y `community` son el registro como lo devuelve la API genérica.
- `GET /api/sgspe/dashboards/territory?project=…` (obligatorio, `400 PROJECT_REQUIRED`) → KPI-TER-02, 03 y 04 y, con
  `locality` o `community`, KPI-TER-01 primero. KPI-TER-01 (`GET /api/sgspe/dashboards/indicators/KPI-TER-01`): `value:
  null`, desglose `components` con las filas `KPI-STK-02`, `KPI-ENG-01`, `KPI-COM-02`, `KPI-COM-03`, `KPI-GRV-02`,
  `KPI-RSK-01`, `programs`, `KPI-INV-03`, `lastActivity`. KPI-TER-02 (`ha`): la suma, desglose `area`. KPI-TER-03
  (`miembros`): desgloses `areaType`, `memberType`, `memberCondition`. KPI-TER-04 (`componentes`): desgloses `tenureType`
  (con `TERRITORIO COMUNAL`) y `componentType`.

---

## Mapa de archivos

Todos bajo la raíz del repositorio. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `src/main/kotlin/sgspe/territory/SpatialGate.kt` (crear), `src/test/kotlin/sgspe/ArchitectureTest.kt` | La compuerta, su SQL puro y la regla de `DatabaseClient` | 1 |
| `src/test/kotlin/sgspe/F6QueryApiTest.kt` (crear), `src/test/kotlin/sgspe/territory/SpatialGateScopeParityTest.kt` (crear) | Base de las pruebas de 6.2a y la paridad con `Records` | 2 |
| `src/main/kotlin/sgspe/territory/ZoneCriterion.kt` (crear) | `Zone`, `ZoneTargets` y los criterios por zona | 3 |
| `src/main/kotlin/sgspe/territory/TerritoryQueries.kt` (crear), `src/main/kotlin/sgspe/territory/SpatialSql.kt` (crear), `src/main/kotlin/sgspe/territory/ProjectFrames.kt` (crear), `src/main/resources/application.yml` | Límites y parámetros, fragmentos de SQL, respuestas y el CRS del proyecto | 4 |
| `src/main/kotlin/sgspe/territory/TerritoryZones.kt` (crear), `src/main/kotlin/sgspe/territory/ZoneQueries.kt` (crear) | `zones/{zoneObject}/{id}/records` y `locate` | 5 |
| `src/main/kotlin/sgspe/territory/MemberSuggestions.kt` (crear) | `member-suggestions` y `buffer-localities` | 6 |
| `src/main/kotlin/sgspe/territory/ComponentLand.kt` (crear), `src/main/kotlin/sgspe/territory/ProjectLandQueries.kt` (crear) | La consulta de KPI-TER-04, `component-parcels` y `component-communal-land` | 7 |
| `src/main/kotlin/sgspe/territory/ProjectProximityQueries.kt` (crear) | `concession-territories`, `sensitive-distances` y `nearby-conflicts` | 8 |
| `src/main/kotlin/sgspe/territory/TerritoryAggregates.kt` (crear) | `concentration` y `summary` | 9 |
| `src/main/kotlin/sgspe/territory/TerritoryCards.kt` (crear) | La parte territorial de la ficha | 10 |
| `src/test/kotlin/sgspe/territory/SpatialGateApiTest.kt` (crear) | Los casos de la compuerta por los endpoints | 11 |
| `src/main/kotlin/sgspe/dashboard/TerritoryKpis.kt` (crear), `KpiEvaluation.kt` (crear), `Indicators.kt`, `DashboardFilters.kt`, `Dashboards.kt` | KPI-TER puros, la evaluación común y `periodGiven` | 12 |
| `src/main/kotlin/sgspe/dashboard/TerritorySummary.kt` (crear), `DashboardData.kt`, `Kpis.kt`, `Dashboards.kt` | Lecturas de territorio, KPI-TER-01 a 04 y el tablero | 13 |
| `src/test/kotlin/sgspe/dashboard/TerritoryDashboardApiTest.kt` (crear) | El tablero y la ficha por la API | 14 |
| — (coordinador) | Medición con volumen | 15 |
| `docs/roadmap.md`, `docs/api.md`, `CLAUDE.md` | Cierre, verificación, push y PR | 16 |

## Orden y paralelismo

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| A | 1 | La rama creada | La compuerta va primero y sola: 6.2b la toma con un rebase en cuanto se integra |
| B | 2, 3, 4, 12 | 1 (la 12, nada) | Paridad, criterios por zona, límites y KPI-TER puros: archivos disjuntos |
| C | 5, 6, 7, 8, 9, 10 | 2, 3 y 4 | Un grupo de endpoints cada una, con su archivo y su prueba |
| D | 11, 13 | 7 y 9 (la 13, también la 12) | Casos de la compuerta por la API; KPI-TER con `ComponentLand` |
| E | 14 | 13 | El tablero por la API |
| F | 15 | 5 a 14 integradas (coordinador) | Medición con volumen por el túnel |
| G | 16 | Todas | Documentos, verificación, push y PR |

Archivos que comparten tareas: `src/main/kotlin/sgspe/dashboard/Dashboards.kt` (12 cambia la evaluación por `KpiEvaluation`;
13 reemplaza después los bloques que dejó la 12, por eso va después) y `src/main/kotlin/sgspe/territory/SpatialSql.kt` (la 3
puede dejar una versión mínima que la 4 reemplaza entera).

---
### Tarea 1: La compuerta `SpatialGate` y su regla en `ArchitectureTest`

**Files:**
- Create: `src/main/kotlin/sgspe/territory/SpatialGate.kt`
- Modify: `src/test/kotlin/sgspe/ArchitectureTest.kt`
- Test: `src/test/kotlin/sgspe/territory/SpatialGateTest.kt`

**Interfaces:**
- Consumes: `sgspe.common.Callers.require()`, `Readability(metadata).canRead(path)`, `ObjectDefinitions.current(org, name)`;
  de wasichai, `RecordReadScopes.criteria(caller: AuthenticatedUser?, definition): List<RecordCriterion>`,
  `AccessPolicy.ownerFilter(user): UUID?`, `WasichaiSchemas.dataTable(table)`, `SqlIdentifier.quote(identifier)` y
  `CustomField.srid` (`wasichai.gis.srid`).
- Produces (vinculante para 6.2b y las Tareas 2 a 13):
  - `data class GateRead(val objectName: String, val fields: Set<String>, val principal: Boolean = false)`
  - `interface GateTables { fun table(objectName: String): String; fun column(objectName: String, field: String): String;
    fun srid(objectName: String, field: String): Int }`
  - `sealed interface GateOutcome<out T> { data class Rows<T>(val rows: List<T>); data class Unavailable(val missingFields:
    List<String>) : GateOutcome<Nothing> }`
  - `@Component class SpatialGate { suspend fun <T : Any> select(reads: List<GateRead>, sql: GateTables.(bind: (Any) ->
    String) -> String, map: (io.r2dbc.spi.Readable) -> T): GateOutcome<T> }` con `SpatialGate.ID = "id"` y
    `SpatialGate.STATE = "workflow_state"`, las dos columnas de sistema que `column` admite sin declararlas.
  - `fun interface GateRunner` (la ejecución, reemplazable en las unitarias).

- [ ] **Paso 1: Comprobar la base**

```bash
cd ~/IdeaProjects/sgspe && git fetch origin && git switch -c feat/fase-6-2a-consultas origin/main && git switch -c f62a/t1
test -f src/test/kotlin/sgspe/F6ApiTest.kt && grep -c "object SyntheticGeo" src/test/kotlin/sgspe/F6ApiTest.kt
grep -c "ASM-122" docs/requirements-analysis.md
grep -c "DatabaseClient" src/main/kotlin/sgspe/territory/GeoSql.kt
```

Esperado: `1`, al menos `1` y al menos `1`. Si `F6ApiTest.kt` no existe, 6.1 no está en `main`: no se sigue.

- [ ] **Paso 2: La prueba unitaria que falla**

Crear `src/test/kotlin/sgspe/territory/SpatialGateTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordCriterion
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import wasichai.gis.GEOMETRY
import wasichai.gis.SRID_COLUMN
import java.util.UUID

// SpatialGate sin base (especificación de F6 §3.1, ASM-122): READ del objeto principal o 403; un objeto o campo secundario
// ilegible, no disponible sin correr nada; cada tabla como subconsulta con la organización, el filtro de dueño y cada
// criterio de alcance entre paréntesis, con la definición completa; columnas y SRID de la metadata y solo SELECT
class SpatialGateTest {
    private val organization = UUID.randomUUID()
    private val analyst = AuthenticatedUser(UUID.randomUUID(), organization, "analista@sgspe.test", listOf("ANALYST"))

    private class Captured {
        var sql: String? = null
        var bindings: Map<String, Any> = emptyMap()
        val scoped = mutableListOf<ObjectDefinition>()
    }

    // un objeto con campos de texto y, si llevan SRID, geometrías; la columna física es c_<campo>
    private fun definition(
        name: String,
        vararg fields: Pair<String, Int?>
    ): ObjectDefinition {
        val objectId = UUID.randomUUID()
        return ObjectDefinition(
            CustomObject(objectId, organization, name, name, name, null, true, "${name}_tbl", null, null),
            fields.mapIndexed { i, (field, srid) ->
                CustomField(
                    id = UUID.randomUUID(),
                    objectId = objectId,
                    name = field,
                    label = field,
                    type = if (srid == null) FieldType.TEXT else GEOMETRY,
                    columnName = "c_$field",
                    required = false,
                    unique = false,
                    defaultValue = null,
                    description = null,
                    position = i,
                    enumOptions = null,
                    relationTargetObjectId = null,
                    attributes = if (srid == null) emptyMap() else mapOf(SRID_COLUMN to srid),
                    visible = true,
                    editable = true
                )
            }
        )
    }

    private val parcel = definition("parcel", "code" to null, "registry" to null, "boundary" to 32718)
    private val right = definition("right", "parcel" to null, "holder" to null)

    private fun gate(
        readable: Set<String>,
        captured: Captured = Captured(),
        scope: RecordCriterion? = null,
        owner: UUID? = null,
        rows: List<Any> = emptyList()
    ) = SpatialGate(
        { analyst },
        { { path -> path in readable } },
        { _, name -> listOf(parcel, right).first { it.obj.name == name } },
        { _, definition ->
            captured.scoped += definition
            listOfNotNull(scope)
        },
        { owner },
        { "\"data\".\"${it.obj.physicalTable}\"" },
        GateRunner { sql, bindings, _ ->
            captured.sql = sql
            captured.bindings = bindings
            rows
        }
    )

    private val all = setOf("parcel", "parcel.code", "parcel.registry", "parcel.boundary", "right", "right.parcel", "right.holder")

    @Test
    fun `sin READ sobre el objeto principal es 403 y no corre nada`() {
        val captured = Captured()
        val gate = gate(setOf("right", "right.parcel"), captured)
        assertThrows<ForbiddenException> {
            runBlocking { gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), { "SELECT 1" }) { it } }
        }
        assertNull(captured.sql)
    }

    @Test
    fun `un objeto secundario o un campo ilegible es no disponible con lo que falta, sin correr nada`() {
        val captured = Captured()
        val gate = gate(setOf("parcel", "parcel.code", "parcel.boundary"), captured)
        val outcome =
            runBlocking {
                gate.select(
                    listOf(GateRead("parcel", setOf("code", "registry"), principal = true), GateRead("right", setOf("parcel", "holder"))),
                    { "SELECT 1" }
                ) { it }
            }
        assertEquals(GateOutcome.Unavailable(listOf("parcel.registry", "right.holder", "right.parcel")), outcome)
        assertNull(captured.sql)
    }

    @Test
    fun `cada tabla es una subconsulta con la organizacion y el alcance entre parentesis, con la definicion completa`() {
        val captured = Captured()
        val scope = RecordCriterion { definition, bind -> "${definition.fields.size} = ${bind(7)} OR x" }
        val gate = gate(setOf("parcel", "parcel.code", "parcel.boundary"), captured, scope)
        runBlocking {
            gate.select(listOf(GateRead("parcel", setOf("code", "boundary"), principal = true)), { bind ->
                "SELECT p.${column("parcel", SpatialGate.ID)} FROM ${table("parcel")} p WHERE p.${column("parcel", "code")} = ${bind("P-1")}"
            }) { it }
        }
        assertEquals(
            "SELECT p.\"id\" FROM (SELECT * FROM \"data\".\"parcel_tbl\" WHERE organization_id = :g0 AND (3 = :g1 OR x)) p " +
                "WHERE p.\"c_code\" = :g2",
            captured.sql
        )
        assertEquals(mapOf("g0" to organization, "g1" to 7, "g2" to "P-1"), captured.bindings)
        // el alcance recibe la definición completa (tres campos), no la proyección legible (dos)
        assertSame(parcel, captured.scoped.single())
    }

    @Test
    fun `el filtro de duenio de wasichai va despues de la organizacion y la tabla repetida reusa sus parametros`() {
        val captured = Captured()
        val owner = UUID.randomUUID()
        val gate = gate(all, captured, owner = owner)
        runBlocking {
            gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), {
                "SELECT a.${column("parcel", "code")} FROM ${table("parcel")} a JOIN ${table("parcel")} b ON a.\"id\" = b.\"id\""
            }) { it }
        }
        val inner = "(SELECT * FROM \"data\".\"parcel_tbl\" WHERE organization_id = :g0 AND created_by = :g1)"
        assertEquals("SELECT a.\"c_code\" FROM $inner a JOIN $inner b ON a.\"id\" = b.\"id\"", captured.sql)
        assertEquals(mapOf("g0" to organization, "g1" to owner), captured.bindings)
    }

    @Test
    fun `una columna no declarada es un error de programacion y srid sale de la metadata del campo declarado`() {
        val gate = gate(all)
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), { "SELECT ${column("parcel", "registry")}" }) { it } }
        }
        val captured = Captured()
        runBlocking {
            gate(all, captured).select(listOf(GateRead("parcel", setOf("boundary"), principal = true)), {
                "SELECT ST_Transform(x, ${srid("parcel", "boundary")})"
            }) { it }
        }
        assertEquals("SELECT ST_Transform(x, 32718)", captured.sql)
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", setOf("code"), principal = true)), { "SELECT ${srid("parcel", "code")}" }) { it } }
        }
    }

    @Test
    fun `solo corre una sentencia SELECT o WITH y devuelve lo que mapea`() {
        val gate = gate(all, rows = listOf("fila"))
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", emptySet(), principal = true)), { "DELETE FROM x" }) { it } }
        }
        assertThrows<IllegalArgumentException> {
            runBlocking { gate.select(listOf(GateRead("parcel", emptySet(), principal = true)), { "SELECT 1; DELETE FROM x" }) { it } }
        }
        val outcome = runBlocking { gate.select(listOf(GateRead("parcel", emptySet(), principal = true)), { "WITH a AS (SELECT 1) SELECT * FROM a" }) { it } }
        assertEquals(GateOutcome.Rows(listOf("fila")), outcome)
    }
}
```

- [ ] **Paso 3: Correrla para verla fallar**

Run: el comando de unitarias con `--tests 'sgspe.territory.SpatialGateTest'`.
Expected: FAIL de compilación («Unresolved reference: SpatialGate», «GateRead», «GateRunner»).

- [ ] **Paso 4: La compuerta**

Crear `src/main/kotlin/sgspe/territory/SpatialGate.kt`:

```kotlin
package sgspe.territory

import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.ObjectDefinitions
import sgspe.common.Readability
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordReadScopes
import wasichai.core.identity.AccessPolicy
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import wasichai.core.platform.WasichaiSchemas
import wasichai.gis.srid
import java.util.UUID

// un objeto que lee una consulta de la compuerta y los campos que nombra, geometrías incluidas. principal: sin READ sobre
// él, la consulta entera es 403; si no es principal, le falta READ o un campo, la consulta sale no disponible
data class GateRead(
    val objectName: String,
    val fields: Set<String>,
    val principal: Boolean = false
)

// lo que el SQL de una consulta puede nombrar: cada objeto como subconsulta ya filtrada por la organización y por todos los
// RecordReadScope (sin alias: lo pone quien escribe el SQL), las columnas declaradas en sus GateRead, entrecomilladas, y el
// SRID declarado de cada campo GEOMETRY
interface GateTables {
    fun table(objectName: String): String

    fun column(
        objectName: String,
        field: String
    ): String

    fun srid(
        objectName: String,
        field: String
    ): Int
}

sealed interface GateOutcome<out T> {
    data class Rows<T>(
        val rows: List<T>
    ) : GateOutcome<T>

    // los objeto.campo que quien llama no puede leer: el componente sale available: false, nunca cero (RQ-DSH-032)
    data class Unavailable(
        val missingFields: List<String>
    ) : GateOutcome<Nothing>
}

// quien ejecuta el SQL ya armado con sus valores: DatabaseClient en la aplicación, uno de mentira en SpatialGateTest
fun interface GateRunner {
    suspend fun run(
        sql: String,
        bindings: Map<String, Any>,
        map: (Readable) -> Any
    ): List<Any>
}

// las tablas de una consulta, puro: la subconsulta de cada objeto lleva, en el orden de PhysicalTableRecordStore.whereClause,
// la organización, el filtro de dueño (own_records_only) y cada criterio de alcance entre paréntesis, armado con la
// definición completa del objeto (como RecordReadScopes). los valores van enlazados como :g<n>; la subconsulta de un objeto
// se arma una vez y se repite tal cual si el SQL lo nombra dos veces
internal class GateSql(
    private val declared: Map<String, Set<String>>,
    private val definitions: Map<String, ObjectDefinition>,
    private val criteria: Map<String, List<RecordCriterion>>,
    private val organizationId: UUID,
    private val owner: UUID?,
    private val physical: (ObjectDefinition) -> String
) : GateTables {
    private val values = linkedMapOf<String, Any>()
    private val tables = mutableMapOf<String, String>()

    val bindings: Map<String, Any> get() = values

    fun bind(value: Any): String {
        val name = "g${values.size}"
        values[name] = value
        return ":$name"
    }

    override fun table(objectName: String): String {
        tables[objectName]?.let { return it }
        val definition = definition(objectName)
        val terms = mutableListOf("organization_id = ${bind(organizationId)}")
        owner?.let { terms += "created_by = ${bind(it)}" }
        for (criterion in criteria[objectName].orEmpty()) {
            val condition = criterion.condition(definition, ::bind)
            check(condition.isNotBlank()) { "un RecordReadScope devolvió una condición vacía para $objectName" }
            terms += "($condition)"
        }
        return "(SELECT * FROM ${physical(definition)} WHERE ${terms.joinToString(" AND ")})".also { tables[objectName] = it }
    }

    override fun column(
        objectName: String,
        field: String
    ): String {
        if (field == SpatialGate.ID || field == SpatialGate.STATE) {
            definition(objectName)
            return SqlIdentifier.quote(field)
        }
        return SqlIdentifier.quote(declaredField(objectName, field).columnName)
    }

    override fun srid(
        objectName: String,
        field: String
    ): Int = declaredField(objectName, field).srid ?: throw IllegalArgumentException("$objectName.$field no es una geometría")

    private fun definition(objectName: String): ObjectDefinition =
        definitions[objectName] ?: throw IllegalArgumentException("la consulta no declara $objectName en sus GateRead")

    private fun declaredField(
        objectName: String,
        field: String
    ): CustomField {
        require(field in declared[objectName].orEmpty()) { "la consulta no declara $objectName.$field en sus GateRead" }
        return definition(objectName).fields.firstOrNull { it.name == field } ?: error("$objectName no tiene el campo $field")
    }
}

// la única puerta de SQL de lectura que cruza tablas del tenant (especificación de F6 §3.1, ASM-122, gis.md §5.2 patrón b):
// 1. permisos: READ de cada objeto y legibilidad de cada campo nombrado, con la definición filtrada por quien llama
//    (Readability, la vista de los tableros); sin READ sobre un objeto principal, 403; lo demás que falte, no disponible.
// 2. alcance: los criterios de todos los RecordReadScope registrados, pedidos a RecordReadScopes.criteria, la misma llamada
//    de RecordService: ninguno para ADMIN, para la plataforma ni sin alcances registrados (appliesTo), y cada uno con la
//    definición completa. un alcance nuevo se aplica solo. más el filtro de dueño de AccessPolicy (hoy nulo: ASM-069).
// 3. SQL: tablas y columnas de ObjectDefinitions.current entrecomilladas, valores enlazados, solo SELECT o WITH, una sentencia.
// 4. resultado: ids y medidas; los atributos personales se leen después por Records.
// quien llama es el AuthenticatedUser del token (Callers.require, CurrentUser de wasichai): fuera de una petición es el 401
// de wasichai, nunca la plataforma. SpatialGateScopeParityTest compara sus filas con las de Records
@Component
class SpatialGate internal constructor(
    private val caller: suspend () -> AuthenticatedUser,
    private val readability: () -> suspend (String) -> Boolean,
    private val definition: suspend (UUID, String) -> ObjectDefinition,
    private val scopes: suspend (AuthenticatedUser, ObjectDefinition) -> List<RecordCriterion>,
    private val owner: suspend (AuthenticatedUser) -> UUID?,
    private val physical: (ObjectDefinition) -> String,
    private val runner: GateRunner
) {
    @Autowired
    constructor(
        callers: Callers,
        metadata: MetadataService,
        definitions: ObjectDefinitions,
        readScopes: RecordReadScopes,
        access: AccessPolicy,
        schemas: WasichaiSchemas,
        db: DatabaseClient
    ) : this(
        { callers.require() },
        { Readability(metadata)::canRead },
        { organization, name -> definitions.current(organization, name) },
        { user, definition -> readScopes.criteria(user, definition) },
        { user -> access.ownerFilter(user) },
        { definition -> schemas.dataTable(definition.obj.physicalTable) },
        DatabaseGateRunner(db)
    )

    suspend fun <T : Any> select(
        reads: List<GateRead>,
        sql: GateTables.(bind: (Any) -> String) -> String,
        map: (Readable) -> T
    ): GateOutcome<T> {
        val user = caller()
        val canRead = readability()
        for (read in reads.filter { it.principal }) {
            if (!canRead(read.objectName)) throw ForbiddenException("La consulta exige permiso de lectura sobre ${read.objectName}")
        }
        val missing = mutableListOf<String>()
        for (read in reads) {
            val paths = read.fields.sorted().map { "${read.objectName}.$it" }
            missing +=
                if (!canRead(read.objectName)) {
                    paths.ifEmpty { listOf(read.objectName) }
                } else {
                    paths.filterNot { canRead(it) }
                }
        }
        if (missing.isNotEmpty()) return GateOutcome.Unavailable(missing.distinct().sorted())
        val declared = reads.groupBy { it.objectName }.mapValues { (_, list) -> list.flatMap { it.fields }.toSet() }
        val definitions = declared.keys.associateWith { definition(user.organizationId, it) }
        val criteria = definitions.mapValues { (_, full) -> scopes(user, full) }
        val tables = GateSql(declared, definitions, criteria, user.organizationId, owner(user), physical)
        val text = tables.sql(tables::bind)
        requireReadOnly(text)
        @Suppress("UNCHECKED_CAST")
        return GateOutcome.Rows(runner.run(text, tables.bindings, map) as List<T>)
    }

    companion object {
        // las columnas de sistema que GateTables.column da sin declararlas en un GateRead
        const val ID = "id"
        const val STATE = "workflow_state"

        internal fun requireReadOnly(sql: String) {
            val start = sql.trimStart().uppercase()
            require(start.startsWith("SELECT") || start.startsWith("WITH")) { "SpatialGate solo ejecuta SELECT" }
            require(';' !in sql) { "SpatialGate ejecuta una sola sentencia" }
        }
    }
}

// el SQL de la compuerta por DatabaseClient: los valores por nombre, cada fila por `map`
internal class DatabaseGateRunner(
    private val db: DatabaseClient
) : GateRunner {
    override suspend fun run(
        sql: String,
        bindings: Map<String, Any>,
        map: (Readable) -> Any
    ): List<Any> {
        var spec = db.sql(sql)
        bindings.forEach { (name, value) -> spec = spec.bind(name, value) }
        return spec
            .map { row, _ -> map(row) }
            .all()
            .collectList()
            .awaitSingle()
    }
}
```

Nota para el implementador: la prueba «un objeto secundario o un campo ilegible» espera la lista ordenada
(`parcel.registry`, `right.holder`, `right.parcel`); por eso `missing.distinct().sorted()`.

- [ ] **Paso 5: Correrla para verla pasar**

Run: el comando de unitarias con `--tests 'sgspe.territory.SpatialGateTest'`.
Expected: PASS, 6 pruebas.

- [ ] **Paso 6: La regla en `ArchitectureTest`**

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, inmediatamente antes de la línea que empieza con
`    // el alcance de lectura por proyecto (ASM-106, ASM-114, security.md §9.7). regla 1:`, agregar:

```kotlin
    // la compuerta de SQL propio (especificación de F6 §3.1, ASM-122): dentro de sgspe.territory solo SpatialGate y los
    // escritores que ya existían tocan DatabaseClient; fuera de territory, solo los que ya lo usaban. y solo SpatialGate pide
    // los alcances de lectura de wasichai (RecordReadScopes): nadie más arma SQL que cruce tablas del tenant
    @Test
    fun `solo SpatialGate y los escritores de territorio usan DatabaseClient`() {
        val territory = listOf("SpatialGate.kt", "GeoSql.kt", "TerritoryImports.kt", "Areas.kt", "ProjectComponents.kt").map { "/sgspe/territory/$it" }
        val elsewhere =
            listOf(
                "/sgspe/common/DependentsDeleteGuard.kt",
                "/sgspe/common/DerivedVersions.kt",
                "/sgspe/common/Idempotency.kt",
                "/sgspe/common/UserReferenceGuard.kt",
                "/sgspe/evidence/EvidenceWriteGuard.kt"
            )
        val others = sources(main).filterNot { file -> (territory + elsewhere).any { file.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(others, DATABASE_CLIENT))
        val gate = "/sgspe/territory/SpatialGate.kt"
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(gate) })
        assertEquals(emptyList<String>(), offenders(sources(main).filterNot { it.invariantSeparatorsPath.endsWith(gate) }, READ_SCOPES))
    }

```

En el mismo archivo, en `los vigias reconocen lo prohibido y dejan pasar lo permitido`, después de la línea
`        assertFalse(isCommon(File("src/main/kotlin/sgspe/commonplace/Risks.kt")))`, agregar:

```kotlin
        assertTrue(DATABASE_CLIENT.containsMatchIn("    private val db: DatabaseClient,"))
        assertTrue(DATABASE_CLIENT.containsMatchIn("import org.springframework.r2dbc.core.DatabaseClient"))
        assertFalse(DATABASE_CLIENT.containsMatchIn("class DatabaseGateRunnerFake"))
        assertFalse(flags(DATABASE_CLIENT, "// sin DatabaseClient: por SpatialGate"))
        assertTrue(READ_SCOPES.containsMatchIn("        readScopes: RecordReadScopes,"))
        assertFalse(READ_SCOPES.containsMatchIn("class ProjectReadScope : RecordReadScope"))
```

Y en el `companion object`, después de `        private val LOAD_DEFINITION = Regex("""\bloadDefinition(ById)?\b""")`, agregar:

```kotlin
        private val DATABASE_CLIENT = Regex("""\bDatabaseClient\b""")
        private val READ_SCOPES = Regex("""\bRecordReadScopes\b""")
```

- [ ] **Paso 7: Correr las dos clases**

Run: el comando de unitarias con `--tests 'sgspe.territory.SpatialGateTest' --tests 'sgspe.ArchitectureTest'`.
Expected: PASS. Si la regla nueva nombra un archivo de `main` que no está en las listas, se lee: si es código nuevo de este
plan, va por `SpatialGate`; si ya estaba en `main` antes de 6.2a, se agrega a `elsewhere` y se anota en el PR.

- [ ] **Paso 8: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/SpatialGate.kt src/test/kotlin/sgspe/territory/SpatialGateTest.kt src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -F - <<'MSG'
feat(territory): la compuerta SpatialGate para el SQL propio que cruza tablas

Revisa READ y la legibilidad de lo que nombra la consulta, arma cada tabla como subconsulta con la
organización y los criterios de RecordReadScopes (la regla de RecordService) y ejecuta solo SELECT
con valores enlazados. ArchitectureTest deja DatabaseClient a la compuerta y a los escritores.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

El coordinador integra `f62a/t1` en `feat/fase-6-2a-consultas` con `--no-ff`, empuja la rama y avisa a 6.2b, que hace su
rebase sobre ella.

---

### Tarea 2: La base de pruebas de 6.2a y la paridad de la compuerta con `Records` (`SpatialGateScopeParityTest`)

**Files:**
- Create: `src/test/kotlin/sgspe/F6QueryApiTest.kt`
- Create: `src/test/kotlin/sgspe/territory/SpatialGateScopeParityTest.kt`

**Interfaces:**
- Consumes: `SpatialGate`, `GateRead`, `GateOutcome` (Tarea 1); de `F6ApiTest` (6.1), `SyntheticGeo.ORIGIN`,
  `SyntheticGeo.offset`, `utmProject`, `locality`, `component`, `parcel`, `confidentialGrievance`; de las bases anteriores,
  `member`, `userWithRole`, `createWith`, `createRecord`, `record`, `grievance`, `exchange`, `send`, `tree`, `code`.
- Produces: `abstract class F6QueryApiTest : F6ApiTest()` con `at(dxM, dyM)`, `rect(x0, y0, x1, y1, multi = false)`,
  `path(vararg Pair<Double, Double>)` (todo `Map<String, Any>`, el tipo que piden las ayudas de `F6ApiTest`, en GeoJSON 4326 y
  en metros desde `ORIGIN`),
  `localityWith(name, boundary, location, type, attributes): UUID`, `unit(level, boundary): UUID`, `area(project, type,
  version, boundary): UUID`, `areaMember(area, field, target, condition): UUID`, `approveArea(area, project)`,
  `get(path, token): JsonNode`, `absent(node): Boolean` (null o ausente) y `failing(path, status, token): String` (el código
  de `errors[0].message`). Las Tareas 5 a
  11 y 14 la usan.

- [ ] **Paso 1: La base `F6QueryApiTest`**

Crear `src/test/kotlin/sgspe/F6QueryApiTest.kt`:

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus
import sgspe.common.IfMatch
import tools.jackson.databind.JsonNode
import java.util.UUID

// la base de las pruebas de integración del plan 6.2a (sobre F6ApiTest del plan 6.1): geometrías en metros desde
// SyntheticGeo.ORIGIN (UTM 17S) armadas solo con SyntheticGeo.offset, así no dependen de si square toma la esquina o el
// centro; localidades con límite, unidades, áreas de influencia con sus miembros y su aprobación; y las lecturas de los
// endpoints de consulta
abstract class F6QueryApiTest : F6ApiTest() {
    private fun corner(
        dxM: Double,
        dyM: Double
    ): List<Double> {
        val (lon, lat) = SyntheticGeo.ORIGIN
        val (x, y) = SyntheticGeo.offset(lon, lat, dxM, dyM)
        return listOf(x, y)
    }

    // un punto a dxM al este y dyM al norte del origen
    protected fun at(
        dxM: Double,
        dyM: Double
    ): Map<String, Any> = mapOf("type" to "Point", "coordinates" to corner(dxM, dyM))

    // el rectángulo de (x0, y0) a (x1, y1) metros; `multi` lo da como MultiPolygon de una parte (territorio comunal, áreas)
    protected fun rect(
        x0: Double,
        y0: Double,
        x1: Double,
        y1: Double,
        multi: Boolean = false
    ): Map<String, Any> {
        val ring = listOf(x0 to y0, x1 to y0, x1 to y1, x0 to y1, x0 to y0).map { (dx, dy) -> corner(dx, dy) }
        return if (multi) {
            mapOf("type" to "MultiPolygon", "coordinates" to listOf(listOf(ring)))
        } else {
            mapOf("type" to "Polygon", "coordinates" to listOf(ring))
        }
    }

    protected fun path(vararg points: Pair<Double, Double>): Map<String, Any> =
        mapOf("type" to "LineString", "coordinates" to points.map { (dx, dy) -> corner(dx, dy) })

    protected fun localityWith(
        name: String = "Caserío Ejemplo",
        boundary: Map<String, Any?>? = null,
        location: Map<String, Any?>? = null,
        type: String = "CASERIO",
        attributes: Map<String, Any?> = emptyMap()
    ): UUID =
        UUID.fromString(
            createWith(
                "locality",
                mapOf("name" to name, "locality_type" to type) + attributes,
                listOfNotNull(boundary?.let { "boundary" to it }, location?.let { "location" to it }).toMap()
            )
        )

    protected fun unit(
        level: String,
        boundary: Map<String, Any?>
    ): UUID =
        UUID.fromString(
            createWith(
                "administrative_unit",
                mapOf("ubigeo_code" to uniqueName("U"), "name" to "Unidad Ejemplo $level", "level" to level),
                mapOf("boundary" to boundary),
                headers = withReason("alta de prueba")
            )
        )

    protected fun area(
        project: UUID,
        type: String = "AIDS",
        version: Int = 1,
        boundary: Map<String, Any?>? = null
    ): UUID =
        UUID.fromString(
            createWith(
                "area_of_influence",
                mapOf("project" to project.toString(), "area_type" to type, "version_number" to version),
                if (boundary == null) emptyMap() else mapOf("boundary" to boundary)
            )
        )

    protected fun areaMember(
        area: UUID,
        field: String,
        target: UUID,
        condition: String = "INCLUIDO"
    ): UUID =
        UUID.fromString(
            createRecord("area_of_influence_member", mapOf("area_of_influence" to area.toString(), field to target.toString(), "member_condition" to condition))
        )

    // la aprobación de una versión (WF-O) por una gerencia social asignada al proyecto
    protected fun approveArea(
        area: UUID,
        project: UUID
    ) {
        val manager = member("SOCIAL_MANAGER", project.toString())
        send(
            "POST",
            "/api/sgspe/areas-of-influence/$area/approve",
            null,
            HttpStatus.OK,
            manager.token,
            mapOf(IfMatch.HEADER to "\"${record("area_of_influence", area.toString())["updatedAt"].asString()}\"", REASON to reason("aprobación de prueba"))
        )
    }

    protected fun get(
        path: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", path, null, HttpStatus.OK, token))

    // un valor null en la respuesta: viaja como null o, si el mapeador de wasichai omite los nulos, no viaja
    protected fun absent(node: JsonNode?): Boolean = node == null || node.isNull

    // el código de error de una consulta que tiene que responder `status`
    protected fun failing(
        path: String,
        status: HttpStatus,
        token: String = this.token
    ): String {
        val response = exchange("GET", path, null, token)
        assertEquals(status, response.status, "GET $path: ${response.body}")
        return code(response)
    }
}
```

- [ ] **Paso 2: La prueba de paridad**

Crear `src/test/kotlin/sgspe/territory/SpatialGateScopeParityTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import sgspe.F6QueryApiTest
import sgspe.common.Records
import java.util.UUID

// la paridad de SpatialGate con Records (especificación de F6 §2.2 elección 3 y §5, riesgo 5): el mismo objeto leído por
// Records (RecordService, con sus RecordReadScope) y por la compuerta da los mismos ids, en un objeto raíz (componente), un
// hijo (miembro de área), una QCS confidencial, el proyecto y un maestro del tenant (predio), para una analista asignada,
// el ADMIN y una analista sin asignación. si wasichai cambia cuándo pide los alcances, esta prueba lo nota. la sonda solo
// existe en el contexto de esta clase (@TestConfiguration anidada): una ruta funcional que lee de las dos formas en la
// misma petición, como la misma persona
class SpatialGateScopeParityTest : F6QueryApiTest() {
    @TestConfiguration
    class Probe {
        @Bean
        fun gateParityProbe(
            gate: SpatialGate,
            records: Records
        ): RouterFunction<ServerResponse> =
            coRouter {
                GET("/api/sgspe/test/gate-parity") { request ->
                    val objectName = request.queryParam("object").orElseThrow()
                    val ids = request.queryParam("ids").orElseThrow().split(",").map(UUID::fromString)
                    val viaRecords = records.all(objectName, ids = ids).map { it.id }.sorted()
                    val outcome =
                        gate.select(listOf(GateRead(objectName, emptySet(), principal = true)), { bind ->
                            val id = column(objectName, SpatialGate.ID)
                            "SELECT t.$id AS id FROM ${table(objectName)} t WHERE t.$id = ANY(${bind(ids.toTypedArray())})"
                        }) { row -> row.get("id", UUID::class.java)!!.toString() }
                    val viaGate =
                        when (outcome) {
                            is GateOutcome.Rows -> outcome.rows.sorted()
                            is GateOutcome.Unavailable -> error("una consulta sin campos no puede quedar no disponible")
                        }
                    ServerResponse.ok().bodyValueAndAwait(mapOf("records" to viaRecords, "gate" to viaGate))
                }
            }
    }

    private class Data(
        val a: UUID,
        val b: UUID,
        val componentA: UUID,
        val componentB: UUID,
        val memberA: UUID,
        val memberB: UUID,
        val openA: UUID,
        val hiddenA: UUID,
        val openB: UUID,
        val parcel: UUID
    )

    private fun data(): Data =
        runBlocking {
            val a = utmProject()
            val b = utmProject()
            val place = locality("Caserío Ejemplo", at(50.0, 50.0))
            val componentA = component(a, uniqueName("PLT-"), "PLATAFORMA", rect(0.0, 0.0, 100.0, 100.0))
            val componentB = component(b, uniqueName("PLT-"), "PLATAFORMA", rect(200.0, 0.0, 300.0, 100.0))
            val memberA = areaMember(area(a), "locality", place)
            val memberB = areaMember(area(b), "locality", place)
            val openA = UUID.fromString(grievance(a.toString(), attributes = mapOf("locality" to place.toString())))
            val hiddenA = confidentialGrievance(a, place)
            val openB = UUID.fromString(grievance(b.toString()))
            val parcel = parcel(uniqueName("PR-"), "PROPIEDAD", rect(0.0, 0.0, 500.0, 500.0))
            Data(a, b, componentA, componentB, memberA, memberB, openA, hiddenA, openB, parcel)
        }

    // los ids que leen las dos vías, que tienen que ser los mismos
    private fun parity(
        objectName: String,
        ids: List<UUID>,
        token: String
    ): List<String> {
        val body = get("/api/sgspe/test/gate-parity?object=$objectName&ids=${ids.joinToString(",")}", token)
        val viaRecords = body["records"].values().map { it.asString() }
        assertEquals(viaRecords, body["gate"].values().map { it.asString() }, "$objectName por Records y por la compuerta")
        return viaRecords
    }

    private fun ids(vararg ids: UUID): List<String> = ids.map { it.toString() }.sorted()

    @Test
    fun `una analista asignada a un proyecto lee lo mismo por Records y por la compuerta`() {
        val d = data()
        val analyst = member("ANALYST", d.a.toString()).token
        assertEquals(ids(d.componentA), parity("project_component", listOf(d.componentA, d.componentB), analyst))
        assertEquals(ids(d.memberA), parity("area_of_influence_member", listOf(d.memberA, d.memberB), analyst))
        assertEquals(ids(d.openA), parity("grievance", listOf(d.openA, d.hiddenA, d.openB), analyst))
        assertEquals(ids(d.a), parity("project", listOf(d.a, d.b), analyst))
        assertEquals(ids(d.parcel), parity("land_parcel", listOf(d.parcel), analyst))
    }

    @Test
    fun `el ADMIN lee todo por las dos vias`() {
        val d = data()
        assertEquals(ids(d.componentA, d.componentB), parity("project_component", listOf(d.componentA, d.componentB), token))
        assertEquals(ids(d.memberA, d.memberB), parity("area_of_influence_member", listOf(d.memberA, d.memberB), token))
        assertEquals(ids(d.openA, d.hiddenA, d.openB), parity("grievance", listOf(d.openA, d.hiddenA, d.openB), token))
        assertEquals(ids(d.a, d.b), parity("project", listOf(d.a, d.b), token))
    }

    @Test
    fun `una analista sin asignacion no lee nada de proyecto por ninguna via y si los maestros`() {
        val d = data()
        val outsider = userWithRole("ANALYST").token
        assertEquals(emptyList<String>(), parity("project_component", listOf(d.componentA, d.componentB), outsider))
        assertEquals(emptyList<String>(), parity("area_of_influence_member", listOf(d.memberA, d.memberB), outsider))
        assertEquals(emptyList<String>(), parity("grievance", listOf(d.openA, d.hiddenA, d.openB), outsider))
        assertEquals(emptyList<String>(), parity("project", listOf(d.a, d.b), outsider))
        assertEquals(ids(d.parcel), parity("land_parcel", listOf(d.parcel), outsider))
    }
}
```

- [ ] **Paso 3: Compilar**

Run: el comando de `compileTestKotlin`.
Expected: `BUILD SUCCESSFUL`. Si alguna ayuda de `F6ApiTest` tiene otra firma que la del contrato común (por ejemplo
`locality` sin `location` nombrado, o `component` con otro orden), se adapta la llamada aquí y se anota en el informe de la
tarea; la firma de `F6ApiTest` no se cambia en este plan.

- [ ] **Paso 4: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/F6QueryApiTest.kt src/test/kotlin/sgspe/territory/SpatialGateScopeParityTest.kt
git commit -F - <<'MSG'
test(territory): la base de 6.2a y la paridad de SpatialGate con Records

F6QueryApiTest arma geometrías en metros desde el origen sintético, áreas y miembros; la paridad lee
el mismo objeto por Records y por la compuerta como analista asignada, ADMIN y sin asignación.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `SpatialGateScopeParityTest`.

- [ ] **Paso 5: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.SpatialGateScopeParityTest'`.
Expected: PASS, 3 pruebas. Si una vía trae un id que la otra no, no se toca la prueba: se compara el SQL de la compuerta
(con `logging.level.io.r2dbc.postgresql.QUERY=DEBUG`) con el de `RecordService` para ese objeto y se corrige `SpatialGate`.

---

### Tarea 3: Los filtros por zona (`ZoneCriterion`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/ZoneCriterion.kt`
- Test: `src/test/kotlin/sgspe/territory/ZoneCriterionTest.kt`

**Interfaces:**
- Consumes: `RecordCriterion`, `ObjectDefinition`, `CustomField.srid` (`wasichai.gis.srid`), `SqlIdentifier.quote`.
- Produces (Tareas 5, 6, 9 y 10):
  - `data class Zone(val relations: Map<String, Set<UUID>>, val geometry: Map<String, Any?>?)`: los ids de la zona por campo
    de relación (`locality`, `community`, `area_of_influence`) y su geometría en GeoJSON 4326 (null si no tiene).
  - `data class PeriodField(val field: String, val instant: Boolean)`, `data class ZoneTarget(val relations: List<String>,
    val geometries: List<String>, val period: PeriodField?)` y `object ZoneTargets { val ALL: Map<String, ZoneTarget> }`.
  - `object ZoneCriterion { fun of(relations: List<String>, geometries: List<String>, zone: Zone): RecordCriterion; fun
    within(geometries: List<String>, reference: Map<String, Any?>, meters: Number, srid: Int): RecordCriterion; fun
    notTrue(field: String): RecordCriterion }`.

- [ ] **Paso 1: La prueba que falla**

Crear `src/test/kotlin/sgspe/territory/ZoneCriterionTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import wasichai.core.data.RecordCriterion
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import wasichai.gis.GEOMETRY
import wasichai.gis.SRID_COLUMN
import java.util.UUID

// los filtros por zona (especificación de F6 §2.2 elección 4 y §3.1): la zona ya leída como quien llama viaja enlazada como
// GeoJSON, nunca como subconsulta a otra tabla; la relación territorial y la geometría se unen por OR; lo que quien llama no
// lee (la definición que entrega wasichai no lo trae) no filtra, y sin nada que filtrar no pasa ningún registro
class ZoneCriterionTest {
    private val locality = UUID.randomUUID()
    private val sector = UUID.randomUUID()
    private val square = mapOf("type" to "Polygon", "coordinates" to listOf(listOf(listOf(0.0, 0.0), listOf(1.0, 0.0), listOf(1.0, 1.0), listOf(0.0, 0.0))))

    private fun definition(vararg fields: Pair<String, Int?>): ObjectDefinition {
        val objectId = UUID.randomUUID()
        return ObjectDefinition(
            CustomObject(objectId, UUID.randomUUID(), "commitment", "commitment", "commitment", null, true, "commitment_tbl", null, null),
            fields.mapIndexed { i, (field, srid) ->
                CustomField(
                    id = UUID.randomUUID(),
                    objectId = objectId,
                    name = field,
                    label = field,
                    type = if (srid == null) FieldType.TEXT else GEOMETRY,
                    columnName = "c_$field",
                    required = false,
                    unique = false,
                    defaultValue = null,
                    description = null,
                    position = i,
                    enumOptions = null,
                    relationTargetObjectId = null,
                    attributes = if (srid == null) emptyMap() else mapOf(SRID_COLUMN to srid),
                    visible = true,
                    editable = true
                )
            }
        )
    }

    // la condición con sus valores, como los enlazaría wasichai (:c0, :c1, …)
    private fun render(
        criterion: RecordCriterion,
        definition: ObjectDefinition
    ): Pair<String, List<Any>> {
        val values = mutableListOf<Any>()
        val sql =
            criterion.condition(definition) { value ->
                values += value
                ":c${values.size - 1}"
            }
        return sql to values
    }

    @Test
    fun `la relacion territorial y la geometria se unen por OR, con la zona enlazada`() {
        val zone = Zone(mapOf("locality" to setOf(locality, sector), "community" to emptySet()), square)
        val (sql, values) = render(ZoneCriterion.of(listOf("locality", "community"), listOf("location"), zone), definition("locality" to null, "community" to null, "location" to 4326))
        assertEquals("\"c_locality\" = ANY(:c0) OR ST_Intersects(\"c_location\", ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:c1), 4326), 4326))", sql)
        assertEquals(setOf(locality, sector), (values[0] as Array<*>).toSet())
        assertTrue((values[1] as String).contains("\"Polygon\""))
    }

    @Test
    fun `la zona se transforma al SRID del campo, nunca se supone 4326`() {
        val (sql, _) = render(ZoneCriterion.of(emptyList(), listOf("location"), Zone(emptyMap(), square)), definition("location" to 32718))
        assertEquals("ST_Intersects(\"c_location\", ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(:c0), 4326), 32718))", sql)
    }

    @Test
    fun `una zona sin geometria filtra solo por relacion`() {
        val zone = Zone(mapOf("locality" to setOf(locality)), null)
        val (sql, _) = render(ZoneCriterion.of(listOf("locality"), listOf("location"), zone), definition("locality" to null, "location" to 4326))
        assertEquals("\"c_locality\" = ANY(:c0)", sql)
    }

    @Test
    fun `lo que quien llama no lee no filtra y sin nada que filtrar no pasa ninguno`() {
        val zone = Zone(mapOf("locality" to setOf(locality), "community" to setOf(UUID.randomUUID())), square)
        // la definición filtrada no trae locality ni location: queda community
        val (sql, _) = render(ZoneCriterion.of(listOf("locality", "community"), listOf("location"), zone), definition("community" to null))
        assertEquals("\"c_community\" = ANY(:c0)", sql)
        val (nothing, values) = render(ZoneCriterion.of(listOf("locality"), listOf("location"), zone), definition("title" to null))
        assertEquals("FALSE", nothing)
        assertTrue(values.isEmpty())
    }

    @Test
    fun `within prefiltra con el indice en grados y mide en metros en el CRS de trabajo`() {
        val (sql, values) = render(ZoneCriterion.within(listOf("location", "boundary"), square, 500, 32717), definition("location" to 4326, "boundary" to 4326))
        val ref = "ST_SetSRID(ST_GeomFromGeoJSON(:c0), 4326)"
        assertTrue(sql.startsWith("(\"c_location\" && ST_Transform(ST_Expand($ref, :c1), 4326) AND ST_DWithin(ST_Transform(\"c_location\", :c2), "), sql)
        assertTrue(sql.contains(") OR (\"c_boundary\" && "), sql)
        assertEquals(0.005, values[1])
        assertEquals(32717, values[2])
        assertEquals(500.0, values[4])
    }

    @Test
    fun `notTrue deja pasar falso o vacio y falla cerrado sin la columna`() {
        assertEquals("\"c_superseded\" IS NOT TRUE", render(ZoneCriterion.notTrue("superseded"), definition("superseded" to null)).first)
        assertEquals("FALSE", render(ZoneCriterion.notTrue("superseded"), definition("code" to null)).first)
    }

    @Test
    fun `los objetos de una zona: el grupo de interes nunca por geometria y cada fecha con su tipo`() {
        val stakeholder = ZoneTargets.ALL.getValue("stakeholder")
        assertEquals(emptyList<String>(), stakeholder.geometries)
        assertNull(stakeholder.period)
        assertEquals(listOf("locality", "area_of_influence"), stakeholder.relations)
        assertEquals(PeriodField("agreed_date", instant = false), ZoneTargets.ALL.getValue("commitment").period)
        assertEquals(PeriodField("received_at", instant = true), ZoneTargets.ALL.getValue("grievance").period)
        assertNull(ZoneTargets.ALL.getValue("social_risk").period)
        assertTrue(ZoneTargets.ALL.values.all { "locality" in it.relations })
        assertEquals(
            setOf("stakeholder", "commitment", "social_risk", "grievance", "inquiry", "stakeholder_request", "engagement", "incident", "early_warning_signal"),
            ZoneTargets.ALL.keys
        )
    }
}
```

- [ ] **Paso 2: Correrla para verla fallar**

Run: el comando de unitarias con `--tests 'sgspe.territory.ZoneCriterionTest'`.
Expected: FAIL de compilación («Unresolved reference: Zone», «ZoneCriterion», «ZoneTargets»).

- [ ] **Paso 3: Los criterios**

Crear `src/main/kotlin/sgspe/territory/ZoneCriterion.kt`:

```kotlin
package sgspe.territory

import tools.jackson.databind.json.JsonMapper
import wasichai.core.data.RecordCriterion
import wasichai.core.metadata.ObjectDefinition
import wasichai.core.platform.SqlIdentifier
import wasichai.gis.srid
import java.util.UUID

// una zona ya leída como quien llama (TerritoryZones): sus ids por campo de relación (locality con sus sectores, community,
// area_of_influence) y su geometría en GeoJSON EPSG:4326, o null si no tiene
data class Zone(
    val relations: Map<String, Set<UUID>>,
    val geometry: Map<String, Any?>?
)

// el campo de fecha por el que se acota el periodo: DATETIME (instant, un instante de Lima) o DATE
data class PeriodField(
    val field: String,
    val instant: Boolean
)

// cómo se asocia un objeto a una zona: sus relaciones territoriales (gis.md §2.4 y las relaciones locality y community del
// modelo), sus geometrías propias y su fecha para el periodo (null si no tiene)
data class ZoneTarget(
    val relations: List<String>,
    val geometries: List<String>,
    val period: PeriodField?
)

// los objetos que lista GET /api/sgspe/territory/zones/{zoneObject}/{id}/records (casos 2, 3 y 5 de gis.md §5.3, más las QCS,
// las interacciones, los incidentes y las señales). un grupo de interés nunca se ubica por un punto (RQ-GIS-015): solo por su
// localidad y su área de influencia
object ZoneTargets {
    private val TERRITORIAL = listOf("locality", "community")
    private val RECEIVED = PeriodField("received_at", instant = true)

    val ALL: Map<String, ZoneTarget> =
        mapOf(
            "stakeholder" to ZoneTarget(listOf("locality", "area_of_influence"), emptyList(), null),
            "commitment" to ZoneTarget(TERRITORIAL, listOf("location"), PeriodField("agreed_date", instant = false)),
            "social_risk" to ZoneTarget(TERRITORIAL + "area_of_influence", listOf("location"), null),
            "grievance" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED),
            "inquiry" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED),
            "stakeholder_request" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED),
            "engagement" to ZoneTarget(TERRITORIAL, listOf("location"), PeriodField("held_at", instant = true)),
            "incident" to ZoneTarget(TERRITORIAL, listOf("location"), PeriodField("occurred_at", instant = true)),
            "early_warning_signal" to ZoneTarget(TERRITORIAL, listOf("location"), RECEIVED)
        )
}

// los RecordCriterion de los filtros por zona (patrón a de gis.md §5.2): la geometría de referencia viaja enlazada como
// GeoJSON (ST_GeomFromGeoJSON), en el SRID de cada campo; los ids de la zona, como un arreglo enlazado. reciben la definición
// que wasichai filtró por quien llama: un campo que no puede leer no filtra (no sería un oráculo sobre su valor), y si no
// queda nada con qué filtrar, ningún registro pasa (FALSE)
object ZoneCriterion {
    private val JSON: JsonMapper = JsonMapper.builder().build()

    // el registro nombra la zona por alguna relación o alguna de sus geometrías la interseca
    fun of(
        relations: List<String>,
        geometries: List<String>,
        zone: Zone
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val terms = mutableListOf<String>()
            for (field in relations) {
                val ids = zone.relations[field].orEmpty()
                if (ids.isEmpty()) continue
                val column = columnOf(definition, field) ?: continue
                terms += "$column = ANY(${bind(ids.toTypedArray())})"
            }
            val geometry = zone.geometry
            if (geometry != null) {
                for (field in geometries) {
                    val found = definition.fields.firstOrNull { it.name == field } ?: continue
                    val srid = found.srid ?: continue
                    terms += "ST_Intersects(${SqlIdentifier.quote(found.columnName)}, ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(${bind(json(geometry))}), 4326), $srid))"
                }
            }
            if (terms.isEmpty()) NOTHING else terms.joinToString(" OR ")
        }

    // alguna geometría del registro está a menos de `meters` de la referencia, medido en `srid` (el working_crs): el prefiltro
    // usa el índice GIST con la envolvente ampliada en grados (gis.md §3.4) y la medida exacta va en metros
    fun within(
        geometries: List<String>,
        reference: Map<String, Any?>,
        meters: Number,
        srid: Int
    ): RecordCriterion =
        RecordCriterion { definition, bind ->
            val terms =
                geometries.mapNotNull { name ->
                    val found = definition.fields.firstOrNull { it.name == name } ?: return@mapNotNull null
                    val fieldSrid = found.srid ?: return@mapNotNull null
                    val column = SqlIdentifier.quote(found.columnName)
                    val ref = "ST_SetSRID(ST_GeomFromGeoJSON(${bind(json(reference))}), 4326)"
                    "($column && ST_Transform(ST_Expand($ref, ${bind(SpatialSql.degrees(meters))}), $fieldSrid) AND " +
                        "ST_DWithin(ST_Transform($column, ${bind(srid)}), ST_Transform($ref, ${bind(srid)}), ${bind(meters.toDouble())}))"
                }
            if (terms.isEmpty()) NOTHING else terms.joinToString(" OR ")
        }

    // el campo no es verdadero (falso o vacío): las versiones vigentes de project_component. sin la columna, ninguno
    fun notTrue(field: String): RecordCriterion = RecordCriterion { definition, _ -> columnOf(definition, field)?.let { "$it IS NOT TRUE" } ?: NOTHING }

    private const val NOTHING = "FALSE"

    private fun columnOf(
        definition: ObjectDefinition,
        field: String
    ): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }

    private fun json(geometry: Map<String, Any?>): String = JSON.writeValueAsString(geometry)
}
```

`within` usa `SpatialSql.degrees`, que crea la Tarea 4. Si esta tarea se hace antes que la 4, crear en la misma rama el
archivo `src/main/kotlin/sgspe/territory/SpatialSql.kt` con solo:

```kotlin
package sgspe.territory

object SpatialSql {
    // grados del margen del prefiltro en 4326: un grado de longitud mide al menos unos 105 km en el Perú (hasta 18.4° S), así
    // que contar 100 km por grado deja la envolvente un poco más grande de lo necesario, nunca más chica
    const val METERS_PER_DEGREE = 100_000.0

    fun degrees(meters: Number): Double = meters.toDouble() / METERS_PER_DEGREE
}
```

y la Tarea 4 lo completa (su Paso 3 reemplaza el archivo entero).

En la prueba de `within`, los valores enlazados quedan en el orden `:c0` (la referencia), `:c1` (los grados, 0.005 para 500
m), `:c2` y `:c3` (el SRID dos veces) y `:c4` (los metros).

- [ ] **Paso 4: Correrla para verla pasar**

Run: el comando de unitarias con `--tests 'sgspe.territory.ZoneCriterionTest'`.
Expected: PASS, 7 pruebas.

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/ZoneCriterion.kt src/test/kotlin/sgspe/territory/ZoneCriterionTest.kt
# solo si se creó aquí: git add src/main/kotlin/sgspe/territory/SpatialSql.kt
git commit -F - <<'MSG'
feat(territory): los filtros por zona con la geometría enlazada

ZoneCriterion une la relación territorial y la intersección con la zona, en el SRID de cada campo,
y mide un buffer en el CRS de trabajo tras el prefiltro con el índice; ZoneTargets dice cómo se
asocia cada objeto a una zona.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 4: Límites, parámetros y piezas comunes de las consultas

**Files:**
- Create: `src/main/kotlin/sgspe/territory/TerritoryQueries.kt`
- Create (o completa, si la Tarea 3 dejó la versión mínima): `src/main/kotlin/sgspe/territory/SpatialSql.kt`
- Create: `src/main/kotlin/sgspe/territory/ProjectFrames.kt`
- Modify: `src/main/resources/application.yml` (bloque `sgspe.territory.queries`)
- Test: `src/test/kotlin/sgspe/territory/QueryParamsTest.kt`, `src/test/kotlin/sgspe/territory/SpatialSqlTest.kt`

**Interfaces:**
- Consumes: `Finding`, `toCodedException`, `LIMA`, `Records.find`, `Values.text`, `Crs.srid`, `Crs.isWorkingZone`,
  `GateOutcome` (Tarea 1).
- Produces (Tareas 5 a 11 y 13):
  - `@ConfigurationProperties("sgspe.territory.queries") data class TerritoryQueryProperties(minBufferM = 1, maxBufferM =
    20000, defaultBufferM = 500, minDistanceKm = 0.1, maxDistanceKm = 50, defaultDistanceKm = 5, maxRows = 5000,
    maxPeriodYears = 5, maxRelated = 200)`.
  - `data class Period(val start: LocalDate, val end: LocalDate) { fun from(): Instant; fun until(): Instant }`.
  - `object QueryParams { bufferM(raw, limits, default): Int?; distanceM(raw, limits): BigDecimal; limit(raw, limits): Int;
    period(start, end, today, limits): Period; page(page, size): PageRequest; coordinate(name, raw, min, max): Double;
    uuids(name, raws): List<UUID> }`.
  - `object SpatialSql { METERS_PER_DEGREE; degrees(meters); firstOf(geometries, srid); lengthM(a, b, srid); areaHa(a, b,
    srid); distanceM(a, b, srid); centroidZone(geometry); zoneAreaHa(geometry) }`.
  - `class SpatialParts<T> { rows; add(outcome): List<T>; missing(vararg fields); response(key, items, truncated) }`.
  - `object TerritoryNames` con los nombres de objeto (`PROJECT`, `COMPONENT`, `PARCEL`, `RIGHT`, `COMMUNITY`, `SECTOR`,
    `LOCALITY`, `UNIT`, `AREA`, `MEMBER`, `FEATURE`, `CONCESSION`) y `COMPONENT_GEOMETRIES = listOf("footprint", "trace",
    "location")`.
  - `data class ProjectFrame(val id: UUID, val srid: Int)` y `@Component class ProjectFrames { suspend fun of(id: UUID):
    ProjectFrame }` (`404 PROJECT_NOT_FOUND`, `409 WORKING_CRS_REQUIRED`).

- [ ] **Paso 1: Las pruebas que fallan**

Crear `src/test/kotlin/sgspe/territory/QueryParamsTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.math.BigDecimal
import java.time.LocalDate

// los límites de las consultas espaciales (especificación de F6 §3.1, ASM-126): fuera de rango es 400 OUT_OF_RANGE y un valor
// que no se puede leer, 400 INVALID_VALUE, los dos con el parámetro en errors[0].field
class QueryParamsTest {
    private val limits = TerritoryQueryProperties()
    private val today = LocalDate.parse("2026-10-11")

    private fun failure(block: () -> Unit): Pair<String, String> {
        val error = assertThrows<WasichaiException> { block() }
        assertEquals(400, error.status.value())
        val violation = error.violations.single()
        return violation.message to violation.field
    }

    @Test
    fun `bufferM va de 1 a 20000 metros y sin valor toma el que se pida`() {
        assertEquals(500, QueryParams.bufferM(null, limits))
        assertNull(QueryParams.bufferM(null, limits, default = null))
        assertEquals(20000, QueryParams.bufferM("20000", limits))
        assertEquals("OUT_OF_RANGE" to "bufferM", failure { QueryParams.bufferM("0", limits) })
        assertEquals("OUT_OF_RANGE" to "bufferM", failure { QueryParams.bufferM("20001", limits) })
        assertEquals("INVALID_VALUE" to "bufferM", failure { QueryParams.bufferM("cien", limits) })
    }

    @Test
    fun `distanceKm va de 0,1 a 50 y se devuelve en metros`() {
        assertEquals(0, BigDecimal("5000").compareTo(QueryParams.distanceM(null, limits)))
        assertEquals(0, BigDecimal("100").compareTo(QueryParams.distanceM("0.1", limits)))
        assertEquals("OUT_OF_RANGE" to "distanceKm", failure { QueryParams.distanceM("0.05", limits) })
        assertEquals("OUT_OF_RANGE" to "distanceKm", failure { QueryParams.distanceM("50.5", limits) })
        assertEquals("INVALID_VALUE" to "distanceKm", failure { QueryParams.distanceM("lejos", limits) })
    }

    @Test
    fun `limit va de 1 a 5000 y por defecto es el tope`() {
        assertEquals(5000, QueryParams.limit(null, limits))
        assertEquals(1, QueryParams.limit("1", limits))
        assertEquals("OUT_OF_RANGE" to "limit", failure { QueryParams.limit("0", limits) })
        assertEquals("OUT_OF_RANGE" to "limit", failure { QueryParams.limit("5001", limits) })
    }

    @Test
    fun `el periodo por defecto son los 12 meses que terminan hoy y no pasa de 5 anios`() {
        assertEquals(Period(LocalDate.parse("2025-10-12"), today), QueryParams.period(null, null, today, limits))
        assertEquals(
            Period(LocalDate.parse("2021-01-01"), LocalDate.parse("2025-12-31")),
            QueryParams.period("2021-01-01", "2025-12-31", today, limits)
        )
        assertEquals("OUT_OF_RANGE" to "periodEnd", failure { QueryParams.period("2021-01-01", "2026-01-01", today, limits) })
        assertEquals("OUT_OF_RANGE" to "periodStart", failure { QueryParams.period("2026-10-02", "2026-10-01", today, limits) })
        assertEquals("INVALID_VALUE" to "periodStart", failure { QueryParams.period("01/10/2026", null, today, limits) })
    }

    @Test
    fun `el periodo en instantes de Lima cubre el ultimo dia entero`() {
        val period = Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        assertEquals("2026-10-01T05:00:00Z", period.from().toString())
        assertEquals("2026-11-01T05:00:00Z", period.until().toString())
    }

    @Test
    fun `una coordenada es un numero en su rango y una lista de proyectos son UUID`() {
        assertEquals(-78.5, QueryParams.coordinate("lon", "-78.5", -180.0, 180.0))
        assertEquals("INVALID_VALUE" to "lat", failure { QueryParams.coordinate("lat", null, -90.0, 90.0) })
        assertEquals("OUT_OF_RANGE" to "lon", failure { QueryParams.coordinate("lon", "-181", -180.0, 180.0) })
        assertEquals("INVALID_VALUE" to "project", failure { QueryParams.uuids("project", listOf("no-es-uuid")) })
        assertEquals(emptyList<Any>(), QueryParams.uuids("project", null))
    }
}
```

Crear `src/test/kotlin/sgspe/territory/SpatialSqlTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// los fragmentos de SQL de las medidas (gis.md §3.3 y §3.4): el prefiltro en grados nunca achica la envolvente; áreas y
// longitudes van en un CRS UTM; la zona del centroide solo vale en el hemisferio sur y en 17S a 19S
class SpatialSqlTest {
    @Test
    fun `el margen del prefiltro cuenta 100 km por grado`() {
        assertEquals(0.01, SpatialSql.degrees(1000))
        assertEquals(0.5, SpatialSql.degrees(50_000))
    }

    @Test
    fun `las medidas transforman las dos geometrias al CRS pedido`() {
        assertEquals(
            "round(ST_Length(ST_Intersection(ST_Transform(c.t, :s), ST_Transform(p.b, :s)))::numeric, 2)",
            SpatialSql.lengthM("c.t", "p.b", ":s")
        )
        assertEquals(
            "round((ST_Area(ST_Intersection(ST_Transform(c.f, :s), ST_Transform(p.b, :s))) / 10000)::numeric, 4)",
            SpatialSql.areaHa("c.f", "p.b", ":s")
        )
        assertEquals("round(ST_Distance(ST_Transform(a, :s), ST_Transform(b, :s))::numeric, 2)", SpatialSql.distanceM("a", "b", ":s"))
        assertEquals("COALESCE(ST_Transform(c.f, 4326), ST_Transform(c.l, 4326))", SpatialSql.firstOf(listOf("c.f", "c.l"), 4326))
    }

    @Test
    fun `el area del tenant va en la zona UTM de su centroide y solo en 17S a 19S`() {
        val zone = SpatialSql.centroidZone("g")
        assertTrue(zone.contains("WHEN ST_Y(ST_Centroid(ST_Transform(g, 4326))) < 0 THEN 32700 + floor("), zone)
        val area = SpatialSql.zoneAreaHa("g")
        assertTrue(area.contains("ST_Dimension(g) = 2 AND $zone IN (32717, 32718, 32719)"), area)
        assertTrue(area.contains("ST_Area(ST_Transform(g, $zone))"), area)
    }

    @Test
    fun `las partes juntan filas y lo que falta, y la respuesta lo dice sin cero`() {
        val parts = SpatialParts<String>()
        parts.add(GateOutcome.Rows(listOf("a")))
        parts.add(GateOutcome.Unavailable(listOf("land_right.holder_person")))
        parts.missing("locality")
        assertEquals(listOf("a"), parts.rows)
        assertEquals(
            mapOf(
                "available" to false,
                "missingFields" to listOf("land_right.holder_person", "locality"),
                "truncated" to false,
                "rows" to listOf("A")
            ),
            parts.response("rows", listOf("A"), truncated = false)
        )
        assertEquals(mapOf("available" to true, "rows" to emptyList<String>()), SpatialParts<String>().response("rows", emptyList()))
    }
}
```

- [ ] **Paso 2: Correrlas para verlas fallar**

Run: el comando de unitarias con `--tests 'sgspe.territory.QueryParamsTest' --tests 'sgspe.territory.SpatialSqlTest'`.
Expected: FAIL de compilación («Unresolved reference: TerritoryQueryProperties», «QueryParams», «SpatialParts»).

- [ ] **Paso 3: Las piezas**

Crear `src/main/kotlin/sgspe/territory/TerritoryQueries.kt`:

```kotlin
package sgspe.territory

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.toCodedException
import wasichai.core.common.PageRequest
import wasichai.core.common.WasichaiException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

// los límites de las consultas espaciales (especificación de F6 §3.1, ASM-126), configurables por sgspe.territory.queries.*:
// el buffer en metros, la distancia en km, las filas de una consulta de la compuerta, los años de un periodo y las lecturas
// por relación (los conflictos de nearby-conflicts). fuera de rango, 400 OUT_OF_RANGE
@ConfigurationProperties("sgspe.territory.queries")
data class TerritoryQueryProperties(
    val minBufferM: Int = 1,
    val maxBufferM: Int = 20_000,
    val defaultBufferM: Int = 500,
    val minDistanceKm: BigDecimal = BigDecimal("0.1"),
    val maxDistanceKm: BigDecimal = BigDecimal("50"),
    val defaultDistanceKm: BigDecimal = BigDecimal("5"),
    val maxRows: Int = 5_000,
    val maxPeriodYears: Int = 5,
    val maxRelated: Int = 200
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TerritoryQueryProperties::class)
class TerritoryQueriesConfiguration

// un periodo de días de Lima, los dos extremos incluidos
data class Period(
    val start: LocalDate,
    val end: LocalDate
) {
    // el primer instante del periodo y el primero que ya no está, en Lima
    fun from(): Instant = start.atStartOfDay(LIMA).toInstant()

    fun until(): Instant = end.plusDays(1).atStartOfDay(LIMA).toInstant()
}

// los parámetros de las consultas espaciales: un valor que no se puede leer es 400 INVALID_VALUE y uno fuera de su rango, 400
// OUT_OF_RANGE, los dos con el parámetro en errors[0].field (P15)
object QueryParams {
    // null en `raw` toma `default` (el de la configuración, o null donde el buffer es opcional)
    fun bufferM(
        raw: String?,
        limits: TerritoryQueryProperties,
        default: Int? = limits.defaultBufferM
    ): Int? {
        val value = raw?.let { integer(BUFFER, it) } ?: return default
        if (value < limits.minBufferM || value > limits.maxBufferM) throw outOfRange(BUFFER, "de ${limits.minBufferM} a ${limits.maxBufferM} metros")
        return value
    }

    // distanceKm en metros
    fun distanceM(
        raw: String?,
        limits: TerritoryQueryProperties
    ): BigDecimal {
        val km = raw?.let { decimal(DISTANCE, it) } ?: limits.defaultDistanceKm
        if (km < limits.minDistanceKm || km > limits.maxDistanceKm) throw outOfRange(DISTANCE, "de ${limits.minDistanceKm} a ${limits.maxDistanceKm} km")
        return km.multiply(BigDecimal(1000))
    }

    fun limit(
        raw: String?,
        limits: TerritoryQueryProperties
    ): Int {
        val value = raw?.let { integer(LIMIT, it) } ?: return limits.maxRows
        if (value < 1 || value > limits.maxRows) throw outOfRange(LIMIT, "de 1 a ${limits.maxRows} filas")
        return value
    }

    // sin periodEnd, hoy; sin periodStart, un año antes del fin más un día (los 12 meses que terminan en él). a lo sumo
    // maxPeriodYears años
    fun period(
        start: String?,
        end: String?,
        today: LocalDate,
        limits: TerritoryQueryProperties
    ): Period {
        val to = end?.let { date(PERIOD_END, it) } ?: today
        val from = start?.let { date(PERIOD_START, it) } ?: to.minusYears(1).plusDays(1)
        if (from.isAfter(to)) throw outOfRange(PERIOD_START, "no puede ser posterior a periodEnd")
        if (!to.isBefore(from.plusYears(limits.maxPeriodYears.toLong()))) throw outOfRange(PERIOD_END, "el periodo no puede pasar de ${limits.maxPeriodYears} años")
        return Period(from, to)
    }

    fun page(
        page: String?,
        size: String?
    ): PageRequest = PageRequest.of(page?.let { integer("page", it) }, size?.let { integer("size", it) })

    fun coordinate(
        name: String,
        raw: String?,
        min: Double,
        max: Double
    ): Double {
        val value = raw?.trim()?.toDoubleOrNull() ?: throw invalid(name, "debe ser un número")
        if (!value.isFinite() || value < min || value > max) throw outOfRange(name, "de $min a $max")
        return value
    }

    fun uuids(
        name: String,
        raws: List<String>?
    ): List<UUID> = raws.orEmpty().map { raw -> runCatching { UUID.fromString(raw.trim()) }.getOrElse { throw invalid(name, "debe ser un UUID") } }

    private fun integer(
        name: String,
        raw: String
    ): Int = raw.trim().toIntOrNull() ?: throw invalid(name, "debe ser un número entero")

    private fun decimal(
        name: String,
        raw: String
    ): BigDecimal = raw.trim().toBigDecimalOrNull() ?: throw invalid(name, "debe ser un número")

    private fun date(
        name: String,
        raw: String
    ): LocalDate =
        try {
            LocalDate.parse(raw.trim())
        } catch (_: DateTimeParseException) {
            throw invalid(name, "debe ser una fecha AAAA-MM-DD")
        }

    fun invalid(
        name: String,
        message: String
    ): WasichaiException = Finding("INVALID_VALUE", "$name $message", name, status = HttpStatus.BAD_REQUEST).toCodedException()

    fun outOfRange(
        name: String,
        message: String
    ): WasichaiException = Finding("OUT_OF_RANGE", "$name fuera de rango: $message", name, status = HttpStatus.BAD_REQUEST).toCodedException()

    private const val BUFFER = "bufferM"
    private const val DISTANCE = "distanceKm"
    private const val LIMIT = "limit"
    private const val PERIOD_START = "periodStart"
    private const val PERIOD_END = "periodEnd"
}
```

Crear (o reemplazar entero, si la Tarea 3 dejó su versión mínima) `src/main/kotlin/sgspe/territory/SpatialSql.kt`:

```kotlin
package sgspe.territory

// los objetos que nombran las consultas espaciales
object TerritoryNames {
    const val PROJECT = "project"
    const val COMPONENT = "project_component"
    const val PARCEL = "land_parcel"
    const val RIGHT = "land_right"
    const val COMMUNITY = "community"
    const val SECTOR = "community_sector"
    const val LOCALITY = "locality"
    const val UNIT = "administrative_unit"
    const val AREA = "area_of_influence"
    const val MEMBER = "area_of_influence_member"
    const val FEATURE = "environmental_feature"
    const val CONCESSION = "mining_concession"

    // las geometrías de un componente, en el orden en que una cuenta toma la primera que tenga: la huella de una plataforma, el
    // trazo de un acceso, el punto de un sondaje
    val COMPONENT_GEOMETRIES = listOf("footprint", "trace", "location")
}

// los fragmentos de SQL de las medidas (gis.md §3.3 y §3.4): las geometrías se guardan en el SRID de su campo y se miden en un
// CRS UTM: el working_crs del proyecto o, en los registros del tenant, la zona UTM del centroide (ASM-064). `srid` es el
// parámetro ya enlazado (":g3"); las expresiones son columnas ya entrecomilladas de GateTables
object SpatialSql {
    // grados del margen del prefiltro en 4326: un grado de longitud mide al menos unos 105 km en el Perú (hasta 18.4° S), así
    // que contar 100 km por grado deja la envolvente un poco más grande de lo necesario, nunca más chica
    const val METERS_PER_DEGREE = 100_000.0

    fun degrees(meters: Number): Double = meters.toDouble() / METERS_PER_DEGREE

    // la primera geometría con valor, todas llevadas a `srid` (del campo con el que se cruza)
    fun firstOf(
        geometries: List<String>,
        srid: Int
    ): String = "COALESCE(${geometries.joinToString(", ") { "ST_Transform($it, $srid)" }})"

    // la longitud en metros de la parte de `a` dentro de `b`
    fun lengthM(
        a: String,
        b: String,
        srid: String
    ): String = "round(ST_Length(ST_Intersection(ST_Transform($a, $srid), ST_Transform($b, $srid)))::numeric, 2)"

    // el área en ha de la parte de `a` dentro de `b`, con cuatro decimales (una plataforma ocupa centésimas de hectárea)
    fun areaHa(
        a: String,
        b: String,
        srid: String
    ): String = "round((ST_Area(ST_Intersection(ST_Transform($a, $srid), ST_Transform($b, $srid))) / 10000)::numeric, 4)"

    fun distanceM(
        a: String,
        b: String,
        srid: String
    ): String = "round(ST_Distance(ST_Transform($a, $srid), ST_Transform($b, $srid))::numeric, 2)"

    // la zona UTM sur del centroide (gis.md §3.3): 32700 + zona; null al norte del ecuador
    fun centroidZone(geometry: String): String {
        val centroid = "ST_Centroid(ST_Transform($geometry, 4326))"
        return "(CASE WHEN ST_Y($centroid) < 0 THEN 32700 + floor((ST_X($centroid) + 180) / 6)::int + 1 END)"
    }

    // el área plana en ha de un polígono del tenant en la zona de su centroide, con dos decimales; null para puntos y líneas o
    // fuera de 17S a 19S (no se calcula con una zona inesperada)
    fun zoneAreaHa(geometry: String): String {
        val zone = centroidZone(geometry)
        return "(CASE WHEN ST_Dimension($geometry) = 2 AND $zone IN (32717, 32718, 32719) " +
            "THEN round((ST_Area(ST_Transform($geometry, $zone)) / 10000)::numeric, 2) END)"
    }
}

// lo que junta una consulta de una o varias partes de la compuerta: las filas legibles y lo que faltó leer. la respuesta
// dice available: false con missingFields si algo faltó: la lista trae lo que sí se leyó, nunca un cero (RQ-DSH-032)
class SpatialParts<T> {
    val rows = mutableListOf<T>()
    private val absent = linkedSetOf<String>()

    fun add(outcome: GateOutcome<T>): List<T> =
        when (outcome) {
            is GateOutcome.Rows -> outcome.rows.also { rows += it }
            is GateOutcome.Unavailable -> emptyList<T>().also { absent += outcome.missingFields }
        }

    fun missing(vararg fields: String) {
        absent += fields
    }

    fun response(
        key: String,
        items: List<Any?>,
        truncated: Boolean? = null
    ): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>("available" to absent.isEmpty())
        if (absent.isNotEmpty()) out["missingFields"] = absent.toList()
        if (truncated != null) out["truncated"] = truncated
        out[key] = items
        return out
    }
}
```

Crear `src/main/kotlin/sgspe/territory/ProjectFrames.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.toCodedException
import java.util.UUID

// el proyecto de una consulta y el CRS en que se mide (gis.md §3.2)
data class ProjectFrame(
    val id: UUID,
    val srid: Int
)

// lee el proyecto como quien llama: uno que no existe, de otro tenant o fuera de su alcance es 404 PROJECT_NOT_FOUND (no se
// distingue, como la API genérica); sin un working_crs de trabajo (32717 a 32719), 409 WORKING_CRS_REQUIRED: nunca se mide en
// grados
@Component
class ProjectFrames(
    private val records: Records
) {
    suspend fun of(id: UUID): ProjectFrame {
        val project =
            records.find(TerritoryNames.PROJECT, id)
                ?: throw Finding("PROJECT_NOT_FOUND", "El proyecto no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()
        val srid =
            Crs.srid(Values.text(project.attributes["working_crs"]))?.takeIf { Crs.isWorkingZone(it) }
                ?: throw Finding(
                    "WORKING_CRS_REQUIRED",
                    "El proyecto no tiene CRS de trabajo: defina working_crs para medir en metros",
                    "project",
                    status = HttpStatus.CONFLICT
                ).toCodedException()
        return ProjectFrame(id, srid)
    }
}
```

En `src/main/resources/application.yml`, reemplazar:

```yaml
    layers:
      # tope de elementos por capa de GET /api/sgspe/territory/layers/{layer} (LayersProperties)
      max-features: 5000
```

por:

```yaml
    layers:
      # tope de elementos por capa de GET /api/sgspe/territory/layers/{layer} (LayersProperties)
      max-features: 5000
    queries:
      # límites de las consultas espaciales (especificación de F6 §3.1, ASM-126; TerritoryQueryProperties). fuera de rango,
      # 400 OUT_OF_RANGE. max-rows es el tope de filas de una consulta de la compuerta (limit), max-related el de conflictos
      # que nearby-conflicts lee con sus localidades
      min-buffer-m: 1
      max-buffer-m: 20000
      default-buffer-m: 500
      min-distance-km: 0.1
      max-distance-km: 50
      default-distance-km: 5
      max-rows: 5000
      max-period-years: 5
      max-related: 200
```

- [ ] **Paso 4: Correrlas para verlas pasar**

Run: el comando de unitarias con `--tests 'sgspe.territory.QueryParamsTest' --tests 'sgspe.territory.SpatialSqlTest' --tests 'sgspe.territory.ZoneCriterionTest'`.
Expected: PASS (6, 4 y, si la Tarea 3 ya está, 7 pruebas).

- [ ] **Paso 5: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build && yarn format
git add src/main/kotlin/sgspe/territory/TerritoryQueries.kt src/main/kotlin/sgspe/territory/SpatialSql.kt \
  src/main/kotlin/sgspe/territory/ProjectFrames.kt src/main/resources/application.yml \
  src/test/kotlin/sgspe/territory/QueryParamsTest.kt src/test/kotlin/sgspe/territory/SpatialSqlTest.kt
git commit -F - <<'MSG'
feat(territory): límites configurables y piezas comunes de las consultas espaciales

sgspe.territory.queries fija buffer, distancia, filas y periodo (400 OUT_OF_RANGE fuera de rango);
SpatialSql arma las medidas en UTM, SpatialParts junta filas y lo que falta leer, y ProjectFrames
da el CRS de trabajo del proyecto o 409 WORKING_CRS_REQUIRED.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 5: Los registros de una zona y el punto pulsado (`zones/{zoneObject}/{id}/records`, `locate`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/TerritoryZones.kt`
- Create: `src/main/kotlin/sgspe/territory/ZoneQueries.kt`
- Test: `src/test/kotlin/sgspe/territory/SpatialZoneQueriesApiTest.kt`

**Interfaces:**
- Consumes: `Zone`, `ZoneTargets`, `ZoneCriterion.of` (Tarea 3); `TerritoryQueryProperties`, `QueryParams`, `Period`,
  `TerritoryNames` (Tarea 4); `Records.find`, `Records.all`, `Records.page`, `Records.geometriesOf`, `Criteria.anyOf`,
  `Criteria.between`, `Criteria.dateBetween`; `F6QueryApiTest` (Tarea 2).
- Produces: `@Service class TerritoryZones { suspend fun read(zoneObject: String, id: UUID): Zone }` (Tarea 10 la usa) y los endpoints
  `GET /api/sgspe/territory/zones/{zoneObject}/{id}/records` y `GET /api/sgspe/territory/locate`.

- [ ] **Paso 1: La prueba de integración**

Crear `src/test/kotlin/sgspe/territory/SpatialZoneQueriesApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID
import kotlin.random.Random

// los registros de una zona y el punto pulsado (casos 2, 3, 5 y 16 de gis.md §5.3; especificación de F6 §3.1): la zona se lee
// como quien llama y los registros salen por Records con el criterio por zona (su relación territorial o su geometría), con
// los permisos y el alcance de la API genérica
class SpatialZoneQueriesApiTest : F6QueryApiTest() {
    private val owner by lazy { userWithRole("COMMITMENT_MANAGER") }
    private val checker by lazy { userWithRole("COMMITMENT_MANAGER") }

    // un compromiso en draft del proyecto, con atributos y, si viene, su ubicación (responsable y verificador distintos, D11)
    private fun commitmentAt(
        project: UUID,
        attributes: Map<String, Any?> = emptyMap(),
        location: Map<String, Any?>? = null
    ): String =
        createWith(
            "commitment",
            commitmentAttributes(project.toString(), owner, checker) + attributes,
            if (location == null) emptyMap() else mapOf("location" to location),
            headers = withReason("alta de prueba")
        )

    private fun ids(
        zoneObject: String,
        zone: UUID,
        query: String
    ): Set<String> = get("/api/sgspe/territory/zones/$zoneObject/$zone/records?$query")["content"].values().map { it["id"].asString() }.toSet()

    @Test
    fun `una localidad trae lo que la nombra, lo de sus sectores y lo que cae en su limite`() {
        val project = runBlocking { utmProject() }
        val zone = localityWith("Caserío Ejemplo", boundary = rect(0.0, 0.0, 1000.0, 1000.0))
        val sector = localityWith("Sector Ejemplo", location = at(100.0, 100.0), type = "SECTOR", attributes = mapOf("parent_locality" to zone.toString()))
        val named = commitmentAt(project, mapOf("locality" to zone.toString()))
        val inSector = commitmentAt(project, mapOf("locality" to sector.toString()))
        val placed = commitmentAt(project, location = at(500.0, 500.0))
        val outside = commitmentAt(project, location = at(5000.0, 500.0))
        val found = ids("locality", zone, "of=commitment")
        assertTrue(found.containsAll(setOf(named, inSector, placed)), "$found")
        assertTrue(outside !in found)
    }

    @Test
    fun `una comunidad trae lo que la nombra y lo que cae en su territorio`() {
        val project = runBlocking { utmProject() }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(2000.0, 0.0, 3000.0, 1000.0, multi = true)) }
        val named = commitmentAt(project, mapOf("community" to community.toString()))
        val placed = commitmentAt(project, location = at(2500.0, 500.0))
        assertEquals(setOf(named, placed), ids("community", community, "of=commitment").intersect(setOf(named, placed)))
    }

    @Test
    fun `una localidad sin limite trae lo que la nombra`() {
        val project = runBlocking { utmProject() }
        val zone = localityWith("Caserío Ejemplo", location = at(0.0, 0.0))
        val named = UUID.fromString(stakeholder(project.toString(), mapOf("locality" to zone.toString())))
        assertEquals(setOf(named.toString()), ids("locality", zone, "of=stakeholder"))
    }

    @Test
    fun `el periodo acota por la fecha del objeto y no aplica a los que no la tienen`() {
        val project = runBlocking { utmProject() }
        val zone = localityWith("Caserío Ejemplo", boundary = rect(0.0, 0.0, 1000.0, 1000.0))
        val named = commitmentAt(project, mapOf("locality" to zone.toString()))
        assertTrue(named in ids("locality", zone, "of=commitment&periodStart=2026-10-01&periodEnd=2026-10-31"))
        assertTrue(named !in ids("locality", zone, "of=commitment&periodStart=2026-11-01&periodEnd=2026-11-30"))
        assertEquals("PERIOD_NOT_APPLICABLE", failing("/api/sgspe/territory/zones/locality/$zone/records?of=social_risk&periodStart=2026-10-01", HttpStatus.BAD_REQUEST))
    }

    @Test
    fun `los errores dicen que falta y que no existe`() {
        val zone = localityWith("Caserío Ejemplo", boundary = rect(0.0, 0.0, 1000.0, 1000.0))
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/zones/locality/$zone/records", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/zones/locality/$zone/records?of=land_parcel", HttpStatus.BAD_REQUEST))
        assertEquals("ZONE_NOT_FOUND", failing("/api/sgspe/territory/zones/project/$zone/records?of=commitment", HttpStatus.NOT_FOUND))
        assertEquals("ZONE_NOT_FOUND", failing("/api/sgspe/territory/zones/locality/${UUID.randomUUID()}/records?of=commitment", HttpStatus.NOT_FOUND))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/zones/locality/$zone/records?of=commitment&radio=5").status)
    }

    @Test
    fun `locate propone la unidad mas fina, la localidad y la comunidad que contienen el punto`() {
        // una banda propia, de 100 a 900 km al sur del origen: otra prueba (o una corrida anterior en la misma base) no deja ahí
        // una unidad, una localidad ni una comunidad que contengan el punto
        val y = -(100_000.0 + Random.nextInt(0, 800) * 1_000.0)
        val district = unit("DISTRITO", rect(-500.0, y - 500, 1500.0, y + 1500, multi = true))
        unit("PROVINCIA", rect(-5000.0, y - 5000, 5000.0, y + 5000, multi = true))
        val place = localityWith("Caserío Ejemplo", boundary = rect(0.0, y, 1000.0, y + 1000))
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(0.0, y, 1000.0, y + 1000, multi = true)) }
        val point = at(500.0, y + 500)
        @Suppress("UNCHECKED_CAST")
        val (lon, lat) = point["coordinates"] as List<Double>
        val body = get("/api/sgspe/territory/locate?lon=$lon&lat=$lat")
        assertEquals(district.toString(), body["administrativeUnit"]["id"].asString())
        assertEquals("DISTRITO", body["administrativeUnit"]["level"].asString())
        assertEquals(place.toString(), body["locality"]["id"].asString())
        assertEquals(community.toString(), body["community"]["id"].asString())
        assertEquals(0, body["unavailable"].size())
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/locate?lon=-181&lat=$lat", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_VALUE", failing("/api/sgspe/territory/locate?lon=$lon", HttpStatus.BAD_REQUEST))
    }
}
```

`commitmentAttributes`, `withReason` y `stakeholder` vienen de `F3ApiTest` y `DomainApiTest`; `communityWithTerritory` y
`utmProject`, de `F6ApiTest`. Las demás pruebas
de la clase comparan solo los ids que crean (`containsAll`, `intersect`, `in`), así toleran las geometrías sintéticas que otras
pruebas dejan alrededor de `ORIGIN` en la misma base.

- [ ] **Paso 2: La zona leída como quien llama**

Crear `src/main/kotlin/sgspe/territory/TerritoryZones.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.toCodedException
import wasichai.core.data.RecordCriterion
import java.util.UUID

// una zona de las consultas (especificación de F6 §2.2 elección 4): se lee por Records como quien llama (404 si no existe o
// no está a su alcance; 403 sin READ sobre el objeto) y se resuelve a sus ids por relación, como el filtro de territorio de
// los tableros (dashboards.md §3.1): una localidad con sus sectores (parent_locality); una unidad administrativa con sus
// unidades hijas, sus localidades y sus comunidades; un área de influencia con sus miembros (y las unidades de ellos). la
// geometría es la de su límite (boundary o territory); una localidad sin límite filtra solo por relación
@Service
class TerritoryZones(
    private val records: Records,
    private val limits: TerritoryQueryProperties
) {
    suspend fun read(
        zoneObject: String,
        id: UUID
    ): Zone {
        val geometryField = GEOMETRY[zoneObject] ?: throw notFound(zoneObject)
        val zone = records.find(zoneObject, id) ?: throw notFound(zoneObject)
        val geometry = Records.geometriesOf(zone)[geometryField]
        val relations =
            when (zoneObject) {
                TerritoryNames.LOCALITY -> mapOf(TerritoryNames.LOCALITY to withSectors(setOf(id)))
                TerritoryNames.COMMUNITY -> mapOf(TerritoryNames.COMMUNITY to setOf(id))
                TerritoryNames.UNIT -> unitRelations(setOf(id))
                else -> areaRelations(id)
            }
        return Zone(relations, geometry)
    }

    private suspend fun withSectors(localities: Set<UUID>): Set<UUID> {
        if (localities.isEmpty()) return emptySet()
        return localities + ids(TerritoryNames.LOCALITY, Criteria.anyOf("parent_locality", localities))
    }

    private suspend fun unitRelations(roots: Set<UUID>): Map<String, Set<UUID>> {
        val units = roots.toMutableSet()
        var frontier: Set<UUID> = roots
        while (frontier.isNotEmpty()) {
            val children = ids(TerritoryNames.UNIT, Criteria.anyOf("parent_unit", frontier)) - units
            units += children
            frontier = children
        }
        return mapOf(
            TerritoryNames.LOCALITY to withSectors(ids(TerritoryNames.LOCALITY, Criteria.anyOf(TerritoryNames.UNIT, units))),
            TerritoryNames.COMMUNITY to ids(TerritoryNames.COMMUNITY, Criteria.anyOf(TerritoryNames.UNIT, units))
        )
    }

    private suspend fun areaRelations(area: UUID): Map<String, Set<UUID>> {
        val members = records.all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(TerritoryNames.AREA, listOf(area))), max = limits.maxRows)

        fun of(field: String): Set<UUID> = members.mapNotNull { Records.uuid(it.attributes[field]) }.toSet()
        val units = of(TerritoryNames.UNIT)
        val fromUnits = if (units.isEmpty()) emptyMap() else unitRelations(units)
        return mapOf(
            TerritoryNames.LOCALITY to withSectors(of(TerritoryNames.LOCALITY)) + fromUnits[TerritoryNames.LOCALITY].orEmpty(),
            TerritoryNames.COMMUNITY to of(TerritoryNames.COMMUNITY) + fromUnits[TerritoryNames.COMMUNITY].orEmpty(),
            TerritoryNames.AREA to setOf(area)
        )
    }

    private suspend fun ids(
        objectName: String,
        criterion: RecordCriterion
    ): Set<UUID> = records.all(objectName, criteria = listOf(criterion), max = limits.maxRows).mapNotNull { Records.uuid(it.id) }.toSet()

    private fun notFound(zoneObject: String) =
        Finding("ZONE_NOT_FOUND", "La zona $zoneObject no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()

    companion object {
        // el límite de cada objeto que puede ser zona
        val GEOMETRY: Map<String, String> =
            mapOf(
                TerritoryNames.LOCALITY to "boundary",
                TerritoryNames.COMMUNITY to "territory",
                TerritoryNames.UNIT to "boundary",
                TerritoryNames.AREA to "boundary"
            )
    }
}
```

- [ ] **Paso 3: Los dos endpoints**

Crear `src/main/kotlin/sgspe/territory/ZoneQueries.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageRequest
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los registros de una zona (casos 2, 3 y 5 de gis.md §5.3) y el punto pulsado (caso 16): patrón a de gis.md §5.2. la zona y
// el punto viajan enlazados en un RecordCriterion (ZoneCriterion) y la lista sale de Records, con permisos de objeto y de
// campo, alcance, paginación y el conteo de la API genérica. nada se escribe: locate solo propone, la persona confirma
@Service
class ZoneQueries(
    private val records: Records,
    private val zones: TerritoryZones,
    private val limits: TerritoryQueryProperties
) {
    suspend fun records(
        zoneObject: String,
        id: UUID,
        of: String?,
        period: Period?,
        page: PageRequest
    ): PageResponse<RecordResponse> {
        val target =
            of?.let { ZoneTargets.ALL[it] }
                ?: throw Finding(
                    "INVALID_OF",
                    "of debe ser uno de ${ZoneTargets.ALL.keys.sorted().joinToString(", ")}",
                    "of",
                    status = HttpStatus.BAD_REQUEST
                ).toCodedException()
        val zone = zones.read(zoneObject, id)
        val criteria = mutableListOf(ZoneCriterion.of(target.relations, target.geometries, zone))
        if (period != null) {
            val field =
                target.period
                    ?: throw Finding("PERIOD_NOT_APPLICABLE", "$of no tiene una fecha por la que acotar el periodo", "periodStart", status = HttpStatus.BAD_REQUEST)
                        .toCodedException()
            criteria +=
                if (field.instant) {
                    Criteria.between(field.field, period.from(), period.until())
                } else {
                    Criteria.dateBetween(field.field, period.start, period.end)
                }
        }
        return records.page(of, RecordQuery(page = page, sort = "id", criteria = criteria))
    }

    // la unidad de nivel más fino, la localidad (por su límite) y la comunidad (por su territorio) que contienen el punto;
    // a igual nivel, la primera por nombre e id. lo que quien llama no puede leer sale null y su objeto en `unavailable`
    suspend fun locate(
        longitude: Double,
        latitude: Double
    ): Map<String, Any?> {
        val zone = Zone(emptyMap(), mapOf("type" to "Point", "coordinates" to listOf(longitude, latitude)))
        val unavailable = mutableListOf<String>()

        suspend fun containing(
            objectName: String,
            geometry: String
        ): List<RecordResponse> =
            try {
                records.all(objectName, criteria = listOf(ZoneCriterion.of(emptyList(), listOf(geometry), zone)), max = limits.maxRows, field = "lon")
            } catch (_: ForbiddenException) {
                unavailable += objectName
                emptyList()
            }
        val byName = compareBy<RecordResponse>({ Values.text(it.attributes["name"]) }, { it.id })
        val byLevel = compareBy<RecordResponse> { LEVELS.indexOf(Values.text(it.attributes["level"])).let { i -> if (i < 0) LEVELS.size else i } }
        val unit = containing(TerritoryNames.UNIT, "boundary").sortedWith(byLevel.then(byName)).firstOrNull()
        val locality = containing(TerritoryNames.LOCALITY, "boundary").sortedWith(byName).firstOrNull()
        val community = containing(TerritoryNames.COMMUNITY, "territory").sortedWith(byName).firstOrNull()
        return linkedMapOf(
            "administrativeUnit" to unit?.let { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"]), "level" to Values.text(it.attributes["level"])) },
            "locality" to
                locality?.let { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"]), "localityType" to Values.text(it.attributes["locality_type"])) },
            "community" to community?.let { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"])) },
            "unavailable" to unavailable
        )
    }

    companion object {
        // de la unidad más fina a la más gruesa
        val LEVELS = listOf("DISTRITO", "PROVINCIA", "DEPARTAMENTO")
    }
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory")
class ZoneQueriesController(
    private val queries: ZoneQueries,
    private val limits: TerritoryQueryProperties,
    private val clock: Clock
) {
    @GetMapping("/zones/{zoneObject}/{id}/records")
    suspend fun records(
        @PathVariable zoneObject: String,
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): PageResponse<RecordResponse> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("of", "periodStart", "periodEnd", "page", "size"))
        val start = params.getFirst("periodStart")
        val end = params.getFirst("periodEnd")
        val period = if (start == null && end == null) null else QueryParams.period(start, end, LocalDate.now(clock.withZone(LIMA)), limits)
        return queries.records(zoneObject, id, params.getFirst("of"), period, QueryParams.page(params.getFirst("page"), params.getFirst("size")))
    }

    @GetMapping("/locate")
    suspend fun locate(request: ServerHttpRequest): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("lon", "lat"))
        return queries.locate(
            QueryParams.coordinate("lon", params.getFirst("lon"), -180.0, 180.0),
            QueryParams.coordinate("lat", params.getFirst("lat"), -90.0, 90.0)
        )
    }
}
```

En `ZoneQueries.kt`, `Records` se usa solo como tipo inyectado y `records.page`; si ktlint marca algún import sin uso, se
quita. El orden de las comprobaciones es: parámetros desconocidos (`422`), `of` (`400 INVALID_OF`), la zona (`404`), el
periodo (`400`).

- [ ] **Paso 4: Compilar y build**

Run: `./gradlew ktlintFormat build` (el comando de build).
Expected: `BUILD SUCCESSFUL` con las unitarias en verde y la prueba nueva compilada.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/territory/TerritoryZones.kt src/main/kotlin/sgspe/territory/ZoneQueries.kt \
  src/test/kotlin/sgspe/territory/SpatialZoneQueriesApiTest.kt
git commit -F - <<'MSG'
feat(territory): los registros de una zona y el punto pulsado

zones/{zoneObject}/{id}/records lista por Records lo que nombra la zona o cae en ella, con su
periodo; locate propone la unidad, la localidad y la comunidad que contienen un punto. La zona se
lee como quien llama y viaja enlazada.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `SpatialZoneQueriesApiTest`.

- [ ] **Paso 6: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.SpatialZoneQueriesApiTest'`.
Expected: PASS, 6 pruebas.

---

### Tarea 6: Las sugerencias de miembros y las localidades en el buffer (`member-suggestions`, `buffer-localities`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/MemberSuggestions.kt`
- Test: `src/test/kotlin/sgspe/territory/SpatialSuggestionsApiTest.kt`

**Interfaces:**
- Consumes: `Zone`, `ZoneCriterion.of`, `ZoneCriterion.within`, `ZoneCriterion.notTrue` (Tarea 3); `TerritoryQueryProperties`,
  `QueryParams.bufferM`, `ProjectFrames`, `SpatialParts`, `TerritoryNames` (Tarea 4); `Records.find`, `Records.all`,
  `Records.rows`, `Records.geometriesOf`, `Criteria.anyOf`; `F6QueryApiTest` (Tarea 2).
- Produces: `@Component class ReferenceGeometries { suspend fun components(project: UUID): Map<String, Any?>?; suspend fun
  projectAndComponents(project: RecordResponse): Map<String, Any?>? }` (una `GeometryCollection` GeoJSON 4326 o null) y los
  endpoints `GET /api/sgspe/areas-of-influence/{id}/member-suggestions` y
  `GET /api/sgspe/territory/projects/{id}/buffer-localities`.

- [ ] **Paso 1: La prueba de integración**

Crear `src/test/kotlin/sgspe/territory/SpatialSuggestionsApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID

// las sugerencias de miembros de un área de influencia (casos 1 y 9 de gis.md §5.3, RN-TER-004) y las localidades en el
// buffer de los componentes (caso 14): solo sugieren, nunca escriben; la persona acepta una en S10 y el miembro nace con
// spatially_suggested
class SpatialSuggestionsApiTest : F6QueryApiTest() {
    private fun suggestions(
        area: UUID,
        query: String = ""
    ): Map<String, JsonNode> =
        get("/api/sgspe/areas-of-influence/$area/member-suggestions$query")["suggestions"].values().associateBy { it["recordId"].asString() }

    private fun memberCount(area: UUID): Long =
        get("/api/objects/area_of_influence_member/records?area_of_influence=$area&size=1")["totalElements"].asLong()

    @Test
    fun `sugiere lo que cruza el area, con el buffer lo cercano, y no escribe nada`() {
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, 0.0, 1000.0, 1000.0, multi = true))
        val inside = localityWith("Caserío Ejemplo", location = at(500.0, 500.0))
        val near = localityWith("Caserío Ejemplo Cercano", location = at(1200.0, 500.0))
        val far = localityWith("Caserío Ejemplo Lejano", location = at(9000.0, 500.0))
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(800.0, 0.0, 1800.0, 1000.0, multi = true)) }
        val district = unit("DISTRITO", rect(-500.0, -500.0, 1500.0, 1500.0, multi = true))
        areaMember(area, "locality", inside)
        val before = memberCount(area)

        val plain = suggestions(area)
        assertEquals("INTERSECTS", plain.getValue(inside.toString())["relation"].asString())
        assertTrue(plain.getValue(inside.toString())["alreadyMember"].asBoolean())
        assertEquals("community", plain.getValue(community.toString())["object"].asString())
        assertEquals(false, plain.getValue(community.toString())["alreadyMember"].asBoolean())
        assertEquals("administrative_unit", plain.getValue(district.toString())["object"].asString())
        assertTrue(near.toString() !in plain && far.toString() !in plain)

        val buffered = suggestions(area, "?bufferM=300")
        assertEquals("WITHIN_BUFFER", buffered.getValue(near.toString())["relation"].asString())
        assertEquals("INTERSECTS", buffered.getValue(inside.toString())["relation"].asString())
        assertTrue(far.toString() !in buffered)
        assertEquals(before, memberCount(area))
    }

    @Test
    fun `un area sin poligono sugiere por los componentes del proyecto`() {
        val project = runBlocking { utmProject() }
        runBlocking { component(project, uniqueName("PLT-"), "PLATAFORMA", rect(20000.0, 0.0, 20200.0, 200.0)) }
        val area = area(project)
        val under = localityWith("Caserío Ejemplo Bajo", location = at(20100.0, 100.0))
        assertEquals("INTERSECTS", suggestions(area).getValue(under.toString())["relation"].asString())
    }

    @Test
    fun `los errores de las sugerencias`() {
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, 0.0, 1000.0, 1000.0, multi = true))
        assertEquals("AREA_NOT_FOUND", failing("/api/sgspe/areas-of-influence/${UUID.randomUUID()}/member-suggestions", HttpStatus.NOT_FOUND))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/areas-of-influence/$area/member-suggestions?bufferM=0", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/areas-of-influence/$area/member-suggestions?radio=5").status)
    }

    @Test
    fun `buffer-localities trae las localidades a menos del buffer de los componentes`() {
        val project = runBlocking { utmProject() }
        runBlocking { component(project, uniqueName("ACC-"), "ACCESO", path(30000.0 to 0.0, 31000.0 to 0.0)) }
        val near = localityWith("Caserío Ejemplo Junto", location = at(30500.0, 300.0))
        val far = localityWith("Caserío Ejemplo Aparte", location = at(30500.0, 2000.0))

        fun found(bufferM: Int): Set<String> =
            get("/api/sgspe/territory/projects/$project/buffer-localities?bufferM=$bufferM")["localities"].values().map { it["id"].asString() }.toSet()
        assertTrue(near.toString() in found(500))
        assertTrue(far.toString() !in found(500))
        assertTrue(near.toString() !in found(100))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/$project/buffer-localities?bufferM=20001", HttpStatus.BAD_REQUEST))
    }

    @Test
    fun `el buffer de un proyecto sin working_crs es 409`() {
        val project = project()
        assertEquals("WORKING_CRS_REQUIRED", failing("/api/sgspe/territory/projects/$project/buffer-localities?bufferM=500", HttpStatus.CONFLICT))
        val area = area(UUID.fromString(project), boundary = rect(0.0, 0.0, 1000.0, 1000.0, multi = true))
        assertEquals("WORKING_CRS_REQUIRED", failing("/api/sgspe/areas-of-influence/$area/member-suggestions?bufferM=500", HttpStatus.CONFLICT))
    }
}
```

`project()` es el de `F2ApiTest` (sin `working_crs`). Las coordenadas lejanas (20 km y 30 km al este de `ORIGIN`) separan
estas geometrías de las de otras pruebas que comparten la base.

- [ ] **Paso 2: Las geometrías de referencia, las sugerencias y el buffer**

Crear `src/main/kotlin/sgspe/territory/MemberSuggestions.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageRequest
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import java.util.UUID

// las geometrías de referencia de un proyecto, leídas por Records como quien llama: las versiones vigentes de sus
// componentes (la huella, el trazo o el punto) y su área de interés, juntas en una GeometryCollection GeoJSON 4326 que se
// enlaza como parámetro (especificación de F6 §2.2 elección 4). null si no hay ninguna
@Component
class ReferenceGeometries(
    private val records: Records,
    private val limits: TerritoryQueryProperties
) {
    suspend fun components(project: UUID): Map<String, Any?>? = collection(componentShapes(project))

    suspend fun projectAndComponents(project: RecordResponse): Map<String, Any?>? =
        collection(listOfNotNull(Records.geometriesOf(project)["interest_area"]) + componentShapes(UUID.fromString(project.id)))

    private suspend fun componentShapes(project: UUID): List<Map<String, Any?>> {
        val shapes = mutableListOf<Map<String, Any?>>()
        val criteria = listOf(Criteria.anyOf("project", listOf(project)), ZoneCriterion.notTrue("superseded"))
        var page = 0
        do {
            val (_, rows) =
                records.rows(
                    TerritoryNames.COMPONENT,
                    RecordQuery(page = PageRequest.of(page, PageRequest.MAX_SIZE), sort = "id", criteria = criteria, count = false)
                )
            for (row in rows) {
                @Suppress("UNCHECKED_CAST")
                val geometries = row.sections[Records.GEOMETRIES] as Map<String, Map<String, Any?>?>?
                TerritoryNames.COMPONENT_GEOMETRIES.firstNotNullOfOrNull { geometries?.get(it) }?.let(shapes::add)
            }
            if (shapes.size > limits.maxRows) throw Records.tooMany(TerritoryNames.COMPONENT, limits.maxRows, "id")
            page++
        } while (rows.size >= PageRequest.MAX_SIZE)
        return shapes
    }

    private fun collection(shapes: List<Map<String, Any?>>): Map<String, Any?>? =
        if (shapes.isEmpty()) null else mapOf("type" to "GeometryCollection", "geometries" to shapes)
}

// las sugerencias de miembros de un área de influencia (casos 1 y 9 de gis.md §5.3, RN-TER-004) y las localidades en un
// buffer de los componentes (caso 14): patrón a de gis.md §5.2, por Records con la referencia enlazada. nada se escribe.
// la referencia de un área es su polígono; sin él (es opcional, CON-025), el área de interés y los componentes de su
// proyecto. INTERSECTS: el candidato cruza la referencia; WITHIN_BUFFER: no la cruza pero está a menos de bufferM, medido en
// el working_crs. un candidato que quien llama no puede leer no se sugiere y su objeto va en missingFields
@Service
class MemberSuggestions(
    private val records: Records,
    private val references: ReferenceGeometries,
    private val frames: ProjectFrames,
    private val limits: TerritoryQueryProperties
) {
    private class Candidate(
        val objectName: String,
        val geometries: List<String>,
        val filters: Map<String, String> = emptyMap()
    )

    suspend fun members(
        areaId: UUID,
        bufferM: Int?
    ): Map<String, Any?> {
        val area =
            records.find(TerritoryNames.AREA, areaId)
                ?: throw Finding("AREA_NOT_FOUND", "El área de influencia no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND)
                    .toCodedException()
        val projectId = Records.uuid(area.attributes["project"])
        val srid =
            if (bufferM == null) {
                null
            } else {
                frames.of(projectId ?: throw Finding("PROJECT_NOT_FOUND", "El área no tiene proyecto legible", "project", status = HttpStatus.NOT_FOUND).toCodedException()).srid
            }
        val parts = SpatialParts<Map<String, Any?>>()
        val reference =
            Records.geometriesOf(area)["boundary"]
                ?: projectId?.let { id -> records.find(TerritoryNames.PROJECT, id)?.let { references.projectAndComponents(it) } }
                ?: return parts.response("suggestions", emptyList())
        val already = already(areaId, parts)
        val rows = mutableListOf<Map<String, Any?>>()
        for (candidate in CANDIDATES) {
            try {
                val crossing =
                    records.all(
                        candidate.objectName,
                        candidate.filters,
                        listOf(ZoneCriterion.of(emptyList(), candidate.geometries, Zone(emptyMap(), reference))),
                        max = limits.maxRows,
                        field = "bufferM"
                    )
                val near =
                    if (bufferM == null || srid == null) {
                        emptyList()
                    } else {
                        records.all(candidate.objectName, candidate.filters, listOf(ZoneCriterion.within(candidate.geometries, reference, bufferM, srid)), max = limits.maxRows, field = "bufferM")
                    }
                val crossingIds = crossing.map { it.id }.toSet()
                rows += crossing.map { suggestion(candidate, it, INTERSECTS, already) }
                rows += near.filter { it.id !in crossingIds }.map { suggestion(candidate, it, WITHIN_BUFFER, already) }
            } catch (_: ForbiddenException) {
                parts.missing(candidate.objectName)
            }
        }
        return parts.response("suggestions", rows)
    }

    suspend fun bufferLocalities(
        projectId: UUID,
        bufferM: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val reference = references.components(projectId) ?: return mapOf("localities" to emptyList<Any>())
        val found =
            records.all(
                TerritoryNames.LOCALITY,
                criteria = listOf(ZoneCriterion.within(listOf("location", "boundary"), reference, bufferM, frame.srid)),
                max = limits.maxRows,
                field = "bufferM"
            )
        return mapOf(
            "localities" to
                found
                    .sortedWith(compareBy({ Values.text(it.attributes["name"]) }, { it.id }))
                    .map { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"]), "localityType" to Values.text(it.attributes["locality_type"])) }
        )
    }

    // los miembros que el área ya tiene, como "<objeto>:<id>"; null si quien llama no puede leer los miembros
    private suspend fun already(
        areaId: UUID,
        parts: SpatialParts<*>
    ): Set<String>? =
        try {
            records
                .all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(TerritoryNames.AREA, listOf(areaId))), max = limits.maxRows)
                .flatMap { member -> MEMBER_FIELDS.mapNotNull { field -> Values.text(member.attributes[field])?.let { "$field:$it" } } }
                .toSet()
        } catch (_: ForbiddenException) {
            parts.missing(TerritoryNames.MEMBER)
            null
        }

    private fun suggestion(
        candidate: Candidate,
        record: RecordResponse,
        relation: String,
        already: Set<String>?
    ): Map<String, Any?> =
        linkedMapOf(
            "object" to candidate.objectName,
            "recordId" to record.id,
            "name" to Values.text(record.attributes["name"]),
            "relation" to relation,
            "alreadyMember" to already?.contains("${candidate.objectName}:${record.id}")
        )

    companion object {
        const val INTERSECTS = "INTERSECTS"
        const val WITHIN_BUFFER = "WITHIN_BUFFER"
        private val MEMBER_FIELDS = listOf(TerritoryNames.LOCALITY, TerritoryNames.COMMUNITY, TerritoryNames.UNIT)

        // los distritos, no las provincias ni los departamentos, que contendrían cualquier área
        private val CANDIDATES =
            listOf(
                Candidate(TerritoryNames.LOCALITY, listOf("location", "boundary")),
                Candidate(TerritoryNames.COMMUNITY, listOf("territory")),
                Candidate(TerritoryNames.UNIT, listOf("boundary"), mapOf("level" to "DISTRITO"))
            )
    }
}

// api.md §4 (F6)
@RestController
class MemberSuggestionsController(
    private val suggestions: MemberSuggestions,
    private val limits: TerritoryQueryProperties
) {
    @GetMapping("/api/sgspe/areas-of-influence/{id}/member-suggestions")
    suspend fun members(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("bufferM"))
        return suggestions.members(id, QueryParams.bufferM(request.queryParams.getFirst("bufferM"), limits, default = null))
    }

    @GetMapping("/api/sgspe/territory/projects/{id}/buffer-localities")
    suspend fun bufferLocalities(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("bufferM"))
        return suggestions.bufferLocalities(id, QueryParams.bufferM(request.queryParams.getFirst("bufferM"), limits)!!)
    }
}
```

`Records.rows` devuelve `Pair<ObjectDefinition, List<RecordRow>>`; `RecordRow.sections` trae las geometrías por campo como
en `Layers.shapes`. El `!!` del buffer es seguro: con el valor por defecto de la configuración, `bufferM` nunca es null ahí.

- [ ] **Paso 3: Build**

Run: el comando de build.
Expected: `BUILD SUCCESSFUL`.

- [ ] **Paso 4: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/territory/MemberSuggestions.kt src/test/kotlin/sgspe/territory/SpatialSuggestionsApiTest.kt
git commit -F - <<'MSG'
feat(territory): sugerencias de miembros y localidades en el buffer de los componentes

member-suggestions propone localidades, comunidades y distritos que cruzan el área (o, sin polígono,
los componentes del proyecto) y, con bufferM, los cercanos; buffer-localities mide en el CRS de
trabajo. Ninguna de las dos escribe.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `SpatialSuggestionsApiTest`.

- [ ] **Paso 5: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.SpatialSuggestionsApiTest'`.
Expected: PASS, 5 pruebas.

---

### Tarea 7: Componentes sobre predios y territorio comunal (`ComponentLand`, `component-parcels`, `component-communal-land`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/ComponentLand.kt`
- Create: `src/main/kotlin/sgspe/territory/ProjectLandQueries.kt`
- Test: `src/test/kotlin/sgspe/territory/ComponentLandSqlTest.kt`, `src/test/kotlin/sgspe/territory/SpatialLandQueriesApiTest.kt`

**Interfaces:**
- Consumes: `SpatialGate`, `GateRead`, `GateTables`, `GateOutcome` (Tarea 1); `SpatialSql`, `SpatialParts`, `ProjectFrames`,
  `QueryParams.limit`, `TerritoryNames`, `TerritoryQueryProperties` (Tarea 4); `Readability`, `Records.all`,
  `Criteria.anyOf`; `F6QueryApiTest` (Tarea 2) y de `F6ApiTest`, `parcel`, `parcelOwner`, `component`,
  `communityWithTerritory`.
- Produces (Tareas 9 y 13):
  - `data class ComponentLandRow(val component: UUID, val project: UUID, val componentType: String?, val land: String, val
    landId: UUID)` (`land`: `TERRITORIO COMUNAL` o la `tenure_type` del predio).
  - `@Service class ComponentLand { suspend fun rows(projects: Collection<UUID>): GateOutcome<ComponentLandRow> }` con
    `ComponentLand.COMMUNAL = "TERRITORIO COMUNAL"` y `ComponentLand.READS` (todas no principales: si falta algo, no
    disponible, nunca 403). Más de `max-rows` cruces es `400 TOO_MANY_RECORDS`.
  - Los endpoints `GET /api/sgspe/territory/projects/{id}/component-parcels` y `…/component-communal-land`.

- [ ] **Paso 1: La prueba unitaria del SQL de `ComponentLand`**

Crear `src/test/kotlin/sgspe/territory/ComponentLandSqlTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

// la consulta de KPI-TER-04 y de summary (especificación de F6 §3.2): los componentes vigentes de los proyectos que cruzan
// territorio comunal o un predio, cada lado en el SRID del campo con que se cruza, y una fila más que el tope
class ComponentLandSqlTest {
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "<$objectName>"

            override fun column(
                objectName: String,
                field: String
            ) = "\"$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = if (objectName == TerritoryNames.COMMUNITY) 4326 else 32718
        }

    @Test
    fun `cruza las versiones vigentes con el territorio comunal y con los predios`() {
        val values = mutableListOf<Any>()
        val sql =
            ComponentLand.sql(tables, { value ->
                values += value
                ":g${values.size - 1}"
            }, listOf(UUID.randomUUID()), 10)
        assertTrue(sql.contains("FROM <project_component> c JOIN <community> m ON ST_Intersects(COALESCE(ST_Transform(c.\"footprint\", 4326), "), sql)
        assertTrue(sql.contains("FROM <project_component> c JOIN <land_parcel> p ON ST_Intersects(COALESCE(ST_Transform(c.\"footprint\", 32718), "), sql)
        assertTrue(sql.contains("c.\"superseded\" IS NOT TRUE"), sql)
        assertTrue(sql.contains(" UNION ALL "), sql)
        assertTrue(sql.endsWith("LIMIT :g${values.size - 1}"), sql)
        assertEquals(11, values.last())
        assertTrue(ComponentLand.COMMUNAL in values)
    }
}
```

- [ ] **Paso 2: Correrla para verla fallar**

Run: el comando de unitarias con `--tests 'sgspe.territory.ComponentLandSqlTest'`.
Expected: FAIL de compilación («Unresolved reference: ComponentLand»).

- [ ] **Paso 3: `ComponentLand`**

Crear `src/main/kotlin/sgspe/territory/ComponentLand.kt`:

```kotlin
package sgspe.territory

import org.springframework.stereotype.Service
import sgspe.common.Records
import java.util.UUID

// un componente vigente que cruza tierra de terceros: TERRITORIO COMUNAL (community.territory) o la tenencia del predio
// (land_parcel.tenure_type)
data class ComponentLandRow(
    val component: UUID,
    val project: UUID,
    val componentType: String?,
    val land: String,
    val landId: UUID
)

// la consulta de KPI-TER-04 y de summary (especificación de F6 §3.2, caso 15 de gis.md §5.3): los project_component vigentes
// (superseded no verdadero) de los proyectos que intersecan community.territory o land_parcel.boundary, por la compuerta.
// ningún objeto es principal: un tablero o un resumen sin permiso sale no disponible, nunca 403. no se recorta: más de
// max-rows cruces es 400 TOO_MANY_RECORDS (la cifra no puede salir parcial)
@Service
class ComponentLand(
    private val gate: SpatialGate,
    private val limits: TerritoryQueryProperties
) {
    suspend fun rows(projects: Collection<UUID>): GateOutcome<ComponentLandRow> {
        if (projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val outcome =
            gate.select(READS, { bind -> sql(this, bind, projects, limits.maxRows) }) { row ->
                ComponentLandRow(
                    row.get("component", UUID::class.java)!!,
                    row.get("project", UUID::class.java)!!,
                    row.get("component_type", String::class.java),
                    row.get("land", String::class.java)!!,
                    row.get("land_id", UUID::class.java)!!
                )
            }
        if (outcome is GateOutcome.Rows && outcome.rows.size > limits.maxRows) throw Records.tooMany(TerritoryNames.COMPONENT, limits.maxRows)
        return outcome
    }

    companion object {
        const val COMMUNAL = "TERRITORIO COMUNAL"

        private val COMPONENT_FIELDS = setOf("project", "component_type", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES

        val READS: List<GateRead> =
            listOf(
                GateRead(TerritoryNames.COMPONENT, COMPONENT_FIELDS),
                GateRead(TerritoryNames.COMMUNITY, setOf("territory")),
                GateRead(TerritoryNames.PARCEL, setOf("tenure_type", "boundary"))
            )

        internal fun sql(
            t: GateTables,
            bind: (Any) -> String,
            projects: Collection<UUID>,
            limit: Int
        ): String {
            val c = TerritoryNames.COMPONENT

            fun shape(srid: Int) = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, srid)

            fun vigentes() = "c.${t.column(c, "project")} = ANY(${bind(projects.toTypedArray())}) AND c.${t.column(c, "superseded")} IS NOT TRUE"
            val head = "SELECT c.${t.column(c, SpatialGate.ID)} AS component, c.${t.column(c, "project")} AS project, c.${t.column(c, "component_type")}::text AS component_type"
            val territory = "m.${t.column(TerritoryNames.COMMUNITY, "territory")}"
            val boundary = "p.${t.column(TerritoryNames.PARCEL, "boundary")}"
            return "$head, CAST(${bind(COMMUNAL)} AS text) AS land, m.${t.column(TerritoryNames.COMMUNITY, SpatialGate.ID)} AS land_id " +
                "FROM ${t.table(c)} c JOIN ${t.table(TerritoryNames.COMMUNITY)} m " +
                "ON ST_Intersects(${shape(t.srid(TerritoryNames.COMMUNITY, "territory"))}, $territory) WHERE ${vigentes()} " +
                "UNION ALL $head, p.${t.column(TerritoryNames.PARCEL, "tenure_type")}::text AS land, p.${t.column(TerritoryNames.PARCEL, SpatialGate.ID)} AS land_id " +
                "FROM ${t.table(c)} c JOIN ${t.table(TerritoryNames.PARCEL)} p " +
                "ON ST_Intersects(${shape(t.srid(TerritoryNames.PARCEL, "boundary"))}, $boundary) WHERE ${vigentes()} " +
                "LIMIT ${bind(limit + 1)}"
        }
    }
}
```

- [ ] **Paso 4: Correr la unitaria**

Run: el comando de unitarias con `--tests 'sgspe.territory.ComponentLandSqlTest'`.
Expected: PASS.

- [ ] **Paso 5: La prueba de integración de los dos endpoints**

Crear `src/test/kotlin/sgspe/territory/SpatialLandQueriesApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

// componentes × predios y × territorio comunal (casos 10 y 11 de gis.md §5.3 y su prueba de §9): una plataforma que corta dos
// predios y un acceso que cruza tres dan los predios y las longitudes esperadas, medidas en el working_crs; los titulares
// salen por Records con permiso de campo. tres predios de 1 km de lado en fila a 40 km al este del origen, para no cruzarse
// con las geometrías de otras pruebas
class SpatialLandQueriesApiTest : F6QueryApiTest() {
    private val x = 40_000.0

    private class Land(
        val project: UUID,
        val p1: UUID,
        val p2: UUID,
        val p3: UUID,
        val platform: UUID,
        val access: UUID,
        val platformCode: String
    )

    private fun land(): Land =
        runBlocking {
            val project = utmProject()
            val p1 = parcel(uniqueName("PR-"), "PROPIEDAD", rect(x, 0.0, x + 1000, 1000.0))
            val p2 = parcel(uniqueName("PR-"), "POSESION", rect(x + 1000, 0.0, x + 2000, 1000.0))
            val p3 = parcel(uniqueName("PR-"), "COMUNAL", rect(x + 2000, 0.0, x + 3000, 1000.0))
            parcelOwner(p1, "Persona Ejemplo")
            val code = uniqueName("PLT-")
            // 200 m × 200 m sobre el lindero de P1 y P2: 2 ha en cada uno
            val platform = component(project, code, "PLATAFORMA", rect(x + 900, 400.0, x + 1100, 600.0))
            // de 250 m a 2 750 m: 750 m en P1, 1 000 m en P2 y 750 m en P3
            val access = component(project, uniqueName("ACC-"), "ACCESO", path(x + 250 to 500.0, x + 2750 to 500.0))
            Land(project, p1, p2, p3, platform, access, code)
        }

    private fun rows(
        project: UUID,
        endpoint: String,
        token: String = this.token
    ): List<JsonNode> = get("/api/sgspe/territory/projects/$project/$endpoint", token)["rows"].values()

    private fun near(
        expected: Double,
        actual: JsonNode?
    ) = assertTrue(actual != null && abs(actual.asDouble() - expected) <= expected * 0.01, "esperado $expected ± 1 %, llegó $actual")

    @Test
    fun `una plataforma corta dos predios y un acceso cruza tres, con sus medidas y titulares`() {
        val l = land()
        val body = get("/api/sgspe/territory/projects/${l.project}/component-parcels")
        assertTrue(body["available"].asBoolean())
        assertEquals(false, body["truncated"].asBoolean())
        // solo los predios de esta prueba: otras (y corridas anteriores en la misma base) dejan predios del tenant en el mismo lugar
        val mine = setOf(l.p1, l.p2, l.p3).map { it.toString() }
        val byPair =
            body["rows"]
                .values()
                .filter { it["parcel"]["id"].asString() in mine }
                .associateBy { it["component"]["id"].asString() to it["parcel"]["id"].asString() }
        assertEquals(5, byPair.size)
        near(2.0, byPair.getValue(l.platform.toString() to l.p1.toString())["areaHa"])
        near(2.0, byPair.getValue(l.platform.toString() to l.p2.toString())["areaHa"])
        near(750.0, byPair.getValue(l.access.toString() to l.p1.toString())["lengthM"])
        near(1000.0, byPair.getValue(l.access.toString() to l.p2.toString())["lengthM"])
        near(750.0, byPair.getValue(l.access.toString() to l.p3.toString())["lengthM"])
        assertEquals("PROPIEDAD", byPair.getValue(l.platform.toString() to l.p1.toString())["parcel"]["tenureType"].asString())
        val holders = byPair.getValue(l.access.toString() to l.p1.toString())["holders"].values()
        assertEquals(listOf("person"), holders.map { it["object"].asString() })
        assertTrue(byPair.getValue(l.access.toString() to l.p2.toString())["holders"].isEmpty)
    }

    @Test
    fun `el acceso cruza el territorio comunal con su longitud`() {
        val l = land()
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(x + 1500, 0.0, x + 2500, 1000.0, multi = true)) }
        val crossing = rows(l.project, "component-communal-land").filter { it["community"]["id"].asString() == community.toString() }
        assertEquals(listOf(l.access.toString()), crossing.map { it["component"]["id"].asString() })
        near(1000.0, crossing.single()["lengthM"])
    }

    @Test
    fun `una plataforma movida deja de cruzar sus predios viejos`() {
        val l = land()
        val moved = runBlocking { component(l.project, l.platformCode, "PLATAFORMA", rect(x + 5000, 5000.0, x + 5200, 5200.0)) }
        val components = rows(l.project, "component-parcels").map { it["component"]["id"].asString() }.toSet()
        assertTrue(l.platform.toString() !in components && moved.toString() !in components, "$components")
        assertTrue(l.access.toString() in components)
    }

    @Test
    fun `un proyecto sin working_crs es 409 y uno ajeno o inexistente 404`() {
        val plain = project()
        assertEquals("WORKING_CRS_REQUIRED", failing("/api/sgspe/territory/projects/$plain/component-parcels", HttpStatus.CONFLICT))
        assertEquals("PROJECT_NOT_FOUND", failing("/api/sgspe/territory/projects/${UUID.randomUUID()}/component-parcels", HttpStatus.NOT_FOUND))
    }

    @Test
    fun `limit recorta con truncated y fuera de rango es 400`() {
        val l = land()
        val one = get("/api/sgspe/territory/projects/${l.project}/component-parcels?limit=1")
        assertEquals(1, one["rows"].size())
        assertTrue(one["truncated"].asBoolean())
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/${l.project}/component-parcels?limit=0", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/projects/${l.project}/component-communal-land?radio=1").status)
    }
}
```

`component(project, code, …)` con un código que ya existe y otra geometría crea la versión nueva y marca la anterior
`superseded` (`POST /api/sgspe/project-components`, F2); la plataforma movida ya no cruza ningún predio de la prueba.

- [ ] **Paso 6: Los dos endpoints**

Crear `src/main/kotlin/sgspe/territory/ProjectLandQueries.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.util.UUID

// un componente que cruza un predio o un territorio comunal, con la longitud de su trazo o el área de su huella dentro de él,
// en el working_crs del proyecto
data class LandCrossing(
    val component: UUID,
    val componentCode: String?,
    val componentType: String?,
    val land: UUID,
    val landCode: String?,
    val landKind: String?,
    val lengthM: BigDecimal?,
    val areaHa: BigDecimal?
)

// componentes × predios (caso 10 de gis.md §5.3: quién debe dar el permiso de uso) y × territorio comunal (caso 11: qué
// comunidad debe autorizar en asamblea), por la compuerta. los titulares de land_right se leen después por Records con los
// permisos de campo de quien llama: sin land_right.holder_person, holders es null y missingFields lo dice
@Service
class ProjectLandQueries(
    private val gate: SpatialGate,
    private val frames: ProjectFrames,
    private val records: Records,
    private val metadata: MetadataService,
    private val limits: TerritoryQueryProperties
) {
    suspend fun parcels(
        projectId: UUID,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<LandCrossing>()
        parts.add(gate.select(PARCEL_READS, { bind -> crossingSql(this, bind, frame, TerritoryNames.PARCEL, "boundary", "code", "tenure_type", limit) }, ::crossing))
        val crossings = parts.rows.take(limit)
        val holders = holders(crossings.map { it.land }.distinct(), parts)
        val rows =
            crossings.map { c ->
                linkedMapOf(
                    "component" to component(c),
                    "parcel" to mapOf("id" to c.land.toString(), "code" to c.landCode, "tenureType" to c.landKind),
                    "lengthM" to c.lengthM,
                    "areaHa" to c.areaHa,
                    "holders" to holders?.let { it[c.land].orEmpty() }
                )
            }
        return parts.response("rows", rows, truncated = parts.rows.size > limit)
    }

    suspend fun communalLand(
        projectId: UUID,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<LandCrossing>()
        parts.add(gate.select(COMMUNAL_READS, { bind -> crossingSql(this, bind, frame, TerritoryNames.COMMUNITY, "territory", "name", null, limit) }, ::crossing))
        val rows =
            parts.rows.take(limit).map { c ->
                linkedMapOf(
                    "component" to component(c),
                    "community" to mapOf("id" to c.land.toString(), "name" to c.landCode),
                    "lengthM" to c.lengthM,
                    "areaHa" to c.areaHa
                )
            }
        return parts.response("rows", rows, truncated = parts.rows.size > limit)
    }

    // los titulares de cada predio: personas y organizaciones de sus land_right, con su nombre si quien llama puede leerlo
    private suspend fun holders(
        parcels: List<UUID>,
        parts: SpatialParts<*>
    ): Map<UUID, List<Map<String, Any?>>>? {
        val readable = Readability(metadata)
        val missing = readable.missing(HOLDER_FIELDS)
        if (missing.isNotEmpty()) {
            parts.missing(*missing.toTypedArray())
            return null
        }
        if (parcels.isEmpty()) return emptyMap()
        val rights = records.all(TerritoryNames.RIGHT, criteria = listOf(Criteria.anyOf("land_parcel", parcels)), max = limits.maxRows, field = "limit")
        val people = names("person", rights.mapNotNull { Values.text(it.attributes["holder_person"]) }, readable, listOf("given_names", "surnames"))
        val organizations = names("organization", rights.mapNotNull { Values.text(it.attributes["holder_organization"]) }, readable, listOf("name"))
        return rights
            .groupBy { UUID.fromString(Values.text(it.attributes["land_parcel"])) }
            .mapValues { (_, list) ->
                list.flatMap { right ->
                    listOfNotNull(
                        Values.text(right.attributes["holder_person"])?.let { mapOf("object" to "person", "id" to it, "name" to people[it]) },
                        Values.text(right.attributes["holder_organization"])?.let { mapOf("object" to "organization", "id" to it, "name" to organizations[it]) }
                    )
                }
            }
    }

    // el nombre de cada registro, de los campos que quien llama puede leer; ninguno si no lee el objeto o esos campos
    private suspend fun names(
        objectName: String,
        ids: List<String>,
        readable: Readability,
        fields: List<String>
    ): Map<String, String> {
        if (ids.isEmpty() || !fields.all { readable.canRead("$objectName.$it") }) return emptyMap()
        return records
            .all(objectName, ids = ids.distinct().map(UUID::fromString), max = limits.maxRows, field = "limit")
            .associate { record -> record.id to fields.mapNotNull { Values.text(record.attributes[it]) }.joinToString(" ") }
            .filterValues { it.isNotBlank() }
    }

    private fun component(c: LandCrossing): Map<String, Any?> = mapOf("id" to c.component.toString(), "code" to c.componentCode, "componentType" to c.componentType)

    private fun crossing(row: io.r2dbc.spi.Readable): LandCrossing =
        LandCrossing(
            row.get("component", UUID::class.java)!!,
            row.get("component_code", String::class.java),
            row.get("component_type", String::class.java),
            row.get("land", UUID::class.java)!!,
            row.get("land_code", String::class.java),
            row.get("land_kind", String::class.java),
            row.get("length_m", BigDecimal::class.java),
            row.get("area_ha", BigDecimal::class.java)
        )

    companion object {
        private val COMPONENT_READ =
            GateRead(TerritoryNames.COMPONENT, setOf("code", "component_type", "project", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES, principal = true)
        val PARCEL_READS = listOf(COMPONENT_READ, GateRead(TerritoryNames.PARCEL, setOf("code", "tenure_type", "boundary"), principal = true))
        val COMMUNAL_READS = listOf(COMPONENT_READ, GateRead(TerritoryNames.COMMUNITY, setOf("name", "territory"), principal = true))
        private val HOLDER_FIELDS = listOf("land_right.land_parcel", "land_right.holder_person", "land_right.holder_organization")

        // los componentes vigentes del proyecto que cruzan `geometry` de `land`, con la longitud del trazo y el área de la huella
        // dentro de ella en el working_crs; `kind` es la columna que se devuelve como land_kind (la tenencia), o ninguna
        internal fun crossingSql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            land: String,
            geometry: String,
            code: String,
            kind: String?,
            limit: Int
        ): String {
            val c = TerritoryNames.COMPONENT
            val srid = bind(frame.srid)
            val target = "x.${t.column(land, geometry)}"
            val shape = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, t.srid(land, geometry))
            val trace = "c.${t.column(c, "trace")}"
            val footprint = "c.${t.column(c, "footprint")}"
            val kindColumn = kind?.let { "x.${t.column(land, it)}::text" } ?: "NULL::text"
            return "SELECT c.${t.column(c, SpatialGate.ID)} AS component, c.${t.column(c, "code")} AS component_code, " +
                "c.${t.column(c, "component_type")}::text AS component_type, x.${t.column(land, SpatialGate.ID)} AS land, " +
                "x.${t.column(land, code)}::text AS land_code, $kindColumn AS land_kind, " +
                "CASE WHEN $trace IS NOT NULL THEN ${SpatialSql.lengthM(trace, target, srid)} END AS length_m, " +
                "CASE WHEN $footprint IS NOT NULL THEN ${SpatialSql.areaHa(footprint, target, srid)} END AS area_ha " +
                "FROM ${t.table(c)} c JOIN ${t.table(land)} x ON ST_Intersects($shape, $target) " +
                "WHERE c.${t.column(c, "project")} = ${bind(frame.id)} AND c.${t.column(c, "superseded")} IS NOT TRUE " +
                "ORDER BY 2, 5, 1, 4 LIMIT ${bind(limit + 1)}"
        }
    }
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory/projects")
class ProjectLandQueriesController(
    private val queries: ProjectLandQueries,
    private val limits: TerritoryQueryProperties
) {
    @GetMapping("/{id}/component-parcels")
    suspend fun parcels(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("limit"))
        return queries.parcels(id, QueryParams.limit(request.queryParams.getFirst("limit"), limits))
    }

    @GetMapping("/{id}/component-communal-land")
    suspend fun communalLand(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("limit"))
        return queries.communalLand(id, QueryParams.limit(request.queryParams.getFirst("limit"), limits))
    }
}
```

El orden de las comprobaciones es `422`, `limit` (`400`), el proyecto (`404`), su CRS (`409`) y después la compuerta (`403` sin
READ sobre el componente o el predio). `land_kind` y `land_code` se devuelven como texto (`::text`) para que la columna ENUM
o TEXT de cada tabla se lea igual con `String`.

- [ ] **Paso 7: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/ComponentLand.kt src/main/kotlin/sgspe/territory/ProjectLandQueries.kt \
  src/test/kotlin/sgspe/territory/ComponentLandSqlTest.kt src/test/kotlin/sgspe/territory/SpatialLandQueriesApiTest.kt
git commit -F - <<'MSG'
feat(territory): componentes sobre predios y territorio comunal por la compuerta

component-parcels da los predios que corta cada componente vigente, con la longitud del acceso y el
área de la huella en el CRS de trabajo y los titulares con permiso de campo; component-communal-land
hace lo mismo con el territorio comunal. ComponentLand es la consulta de KPI-TER-04.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `SpatialLandQueriesApiTest`.

- [ ] **Paso 8: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.SpatialLandQueriesApiTest'`.
Expected: PASS, 5 pruebas. Si las medidas difieren más del 1 %, se mira primero cómo `SyntheticGeo.offset` pasa metros a
grados (una aproximación esférica separa unos metros de lo que mide UTM): se ajusta la tolerancia de `near` y se anota, nunca
la consulta.

---

### Tarea 8: Concesiones, elementos sensibles y conflictos cercanos (`concession-territories`, `sensitive-distances`, `nearby-conflicts`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/ProjectProximityQueries.kt`
- Test: `src/test/kotlin/sgspe/territory/ProximitySqlTest.kt`, `src/test/kotlin/sgspe/territory/SpatialProximityApiTest.kt`

**Interfaces:**
- Consumes: `SpatialGate`, `GateRead`, `GateTables`, `GateOutcome` (Tarea 1); `SpatialSql`, `SpatialParts`, `ProjectFrames`,
  `QueryParams.limit`, `QueryParams.distanceM`, `TerritoryNames`, `TerritoryQueryProperties` (Tarea 4); `Records.find`,
  `Records.all`, `Records.related`; `F6QueryApiTest` (Tarea 2) y de `F6ApiTest`, `component`, `communityWithTerritory`,
  `environmentalFeature`, `locality`.
- Produces: los endpoints `GET /api/sgspe/territory/projects/{id}/concession-territories`, `…/sensitive-distances` y
  `…/nearby-conflicts`, y `ProjectProximityQueries.reference(t, bind, project)`, el `WITH ref AS (…)` con el área de interés y
  los componentes vigentes del proyecto en 4326.

- [ ] **Paso 1: La prueba unitaria del SQL**

Crear `src/test/kotlin/sgspe/territory/ProximitySqlTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

// el SQL de las consultas de cercanía (casos 7, 12 y 13 de gis.md §5.3): prefiltro con el índice en 4326 y la envolvente
// ampliada, medida exacta en el working_crs; el área de una concesión sobre el tenant, en la zona de su centroide
class ProximitySqlTest {
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "<$objectName>"

            override fun column(
                objectName: String,
                field: String
            ) = "\"$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = 4326
        }

    private class Bound {
        val values = mutableListOf<Any>()

        fun bind(value: Any): String {
            values += value
            return ":g${values.size - 1}"
        }
    }

    @Test
    fun `los elementos sensibles se prefiltran en grados y se miden en el CRS de trabajo`() {
        val bound = Bound()
        val sql = ProjectProximityQueries.sensitiveSql(tables, bound::bind, ProjectFrame(UUID.randomUUID(), 32717), BigDecimal("1000"), 50)
        assertTrue(sql.contains("&& ST_Expand(COALESCE(ST_Transform(c.\"footprint\", 4326), "), sql)
        assertTrue(sql.contains("ST_DWithin(ST_Transform(COALESCE("), sql)
        assertTrue(sql.contains("ORDER BY distance_m"), sql)
        assertTrue(32717 in bound.values)
        assertTrue(0.01 in bound.values)
        assertEquals(ProjectProximityQueries.SENSITIVE, (bound.values.first { it is Array<*> } as Array<*>).toList())
    }

    @Test
    fun `la referencia junta el area de interes y los componentes vigentes`() {
        val bound = Bound()
        val project = UUID.randomUUID()
        val sql = ProjectProximityQueries.reference(tables, bound::bind, project)
        assertTrue(sql.startsWith("WITH ref AS (SELECT ST_Collect(s.g) AS g FROM ("), sql)
        assertTrue(sql.contains("ST_Transform(p.\"interest_area\", 4326)"), sql)
        assertTrue(sql.contains("c.\"superseded\" IS NOT TRUE"), sql)
        assertEquals(listOf<Any>(project, project), bound.values)
    }

    @Test
    fun `el area de una concesion sobre una comunidad va en la zona del centroide`() {
        val sql = ProjectProximityQueries.concessionSql(tables, Bound()::bind, listOf(UUID.randomUUID()), ProjectProximityQueries.TERRITORIES.first(), 10)
        assertTrue(sql.contains("IN (32717, 32718, 32719)"), sql)
        assertTrue(sql.contains("JOIN <community> x ON (ST_Intersects(ST_Transform(k.\"boundary\", 4326), x.\"territory\"))"), sql)
    }
}
```

- [ ] **Paso 2: Correrla para verla fallar**

Run: el comando de unitarias con `--tests 'sgspe.territory.ProximitySqlTest'`.
Expected: FAIL de compilación («Unresolved reference: ProjectProximityQueries»).

- [ ] **Paso 3: Las tres consultas**

Crear `src/main/kotlin/sgspe/territory/ProjectProximityQueries.kt`:

```kotlin
package sgspe.territory

import io.r2dbc.spi.Readable
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import java.math.BigDecimal
import java.util.UUID

// algo cercano a un proyecto: un incidente, una señal de alerta o un conflicto local, a la distancia de su punto (o el de su
// localidad) a la referencia del proyecto, en metros en el working_crs
data class Nearby(
    val objectName: String,
    val id: UUID,
    val label: String?,
    val distanceM: BigDecimal
)

// las consultas de cercanía de un proyecto, por la compuerta (patrón b de gis.md §5.2):
// - concession-territories (caso 12): comunidades, sectores comunales y localidades sobre las concesiones del proyecto
//   (project_concession, leída por Records), con el área del cruce en la zona UTM de su centroide (ASM-064)
// - sensitive-distances (caso 13): los componentes vigentes a menos de distanceKm de un elemento ambiental sensible
// - nearby-conflicts (caso 7): incidentes y señales de alerta (su ubicación o la de su localidad) y los conflictos locales
//   ACTIVO (por sus localidades, que se leen por Records.related) a menos de distanceKm del área de interés o de los
//   componentes del proyecto, del más cercano al más lejano
// cada parte que quien llama no puede leer sale en missingFields y la lista trae las demás
@Service
class ProjectProximityQueries(
    private val gate: SpatialGate,
    private val frames: ProjectFrames,
    private val records: Records,
    private val limits: TerritoryQueryProperties
) {
    suspend fun concessionTerritories(
        projectId: UUID,
        limit: Int
    ): Map<String, Any?> {
        records.find(TerritoryNames.PROJECT, projectId)
            ?: throw Finding("PROJECT_NOT_FOUND", "El proyecto no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()
        val concessions =
            records.related(TerritoryNames.PROJECT, projectId, "project_concession", max = limits.maxRelated, field = "id").map { UUID.fromString(it.id) }
        val parts = SpatialParts<Map<String, Any?>>()
        if (concessions.isEmpty()) return parts.response("rows", emptyList(), truncated = false)
        for (target in TERRITORIES) {
            val reads =
                listOf(
                    GateRead(TerritoryNames.CONCESSION, setOf("code", "name", "boundary"), principal = true),
                    GateRead(target.objectName, target.geometries.toSet() + "name" + listOfNotNull(target.kind))
                )
            parts.add(gate.select(reads, { bind -> concessionSql(this, bind, concessions, target, limit) }) { row -> territoryRow(row, target) })
        }
        val sorted =
            parts.rows.sortedWith(
                compareBy({ (it["concession"] as Map<*, *>)["code"]?.toString() }, { it["object"].toString() }, { it["name"]?.toString() }, { it["id"].toString() })
            )
        return parts.response("rows", sorted.take(limit), truncated = sorted.size > limit)
    }

    suspend fun sensitiveDistances(
        projectId: UUID,
        meters: BigDecimal,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<Map<String, Any?>>()
        val reads =
            listOf(
                GateRead(TerritoryNames.COMPONENT, setOf("code", "component_type", "project", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES, principal = true),
                GateRead(TerritoryNames.FEATURE, setOf("name", "feature_type") + FEATURE_GEOMETRIES, principal = true)
            )
        parts.add(
            gate.select(reads, { bind -> sensitiveSql(this, bind, frame, meters, limit) }) { row ->
                linkedMapOf(
                    "component" to
                        mapOf(
                            "id" to row.get("component", UUID::class.java).toString(),
                            "code" to row.get("component_code", String::class.java),
                            "componentType" to row.get("component_type", String::class.java)
                        ),
                    "feature" to
                        mapOf(
                            "id" to row.get("feature", UUID::class.java).toString(),
                            "name" to row.get("feature_name", String::class.java),
                            "featureType" to row.get("feature_type", String::class.java)
                        ),
                    "distanceM" to row.get("distance_m", BigDecimal::class.java)
                )
            }
        )
        return parts.response("rows", parts.rows.take(limit), truncated = parts.rows.size > limit)
    }

    suspend fun nearbyConflicts(
        projectId: UUID,
        meters: BigDecimal,
        limit: Int
    ): Map<String, Any?> {
        val frame = frames.of(projectId)
        val parts = SpatialParts<Nearby>()
        for ((objectName, label) in EVENTS) {
            val reads = REFERENCE_READS + PLACE_READ + GateRead(objectName, setOf(label, "location", "locality"))
            parts.add(gate.select(reads, { bind -> eventSql(this, bind, frame, meters, limit, objectName, label) }, ::nearby))
        }
        val distances =
            when (
                val outcome =
                    gate.select(REFERENCE_READS + PLACE_READ, { bind -> localitySql(this, bind, frame, meters, limits.maxRows) }) { row ->
                        row.get("id", UUID::class.java)!! to row.get("distance_m", BigDecimal::class.java)!!
                    }
            ) {
                is GateOutcome.Rows -> outcome.rows.toMap()
                is GateOutcome.Unavailable -> {
                    parts.missing(*outcome.missingFields.toTypedArray())
                    null
                }
            }
        if (distances != null) parts.rows += conflicts(distances, parts)
        val sorted = parts.rows.sortedWith(compareBy({ it.distanceM }, { it.objectName }, { it.id }))
        val items = sorted.take(limit).map { mapOf("object" to it.objectName, "id" to it.id.toString(), "label" to it.label, "distanceM" to it.distanceM) }
        return parts.response("items", items, truncated = sorted.size > limit)
    }

    // los conflictos ACTIVO a la distancia de su localidad más cercana; uno sin localidad cercana no sale
    private suspend fun conflicts(
        distances: Map<UUID, BigDecimal>,
        parts: SpatialParts<Nearby>
    ): List<Nearby> {
        if (distances.isEmpty()) return emptyList()
        val active =
            try {
                records.all(CONFLICT, filters = mapOf("conflict_status" to "ACTIVO"), max = limits.maxRelated, field = "distanceKm")
            } catch (_: ForbiddenException) {
                parts.missing(CONFLICT)
                return emptyList()
            }
        return active.mapNotNull { conflict ->
            val id = UUID.fromString(conflict.id)
            records
                .related(CONFLICT, id, "local_conflict_locality", max = limits.maxRelated, field = "distanceKm")
                .mapNotNull { distances[UUID.fromString(it.id)] }
                .minOrNull()
                ?.let { Nearby(CONFLICT, id, Values.text(conflict.attributes["title"]), it) }
        }
    }

    private fun nearby(row: Readable): Nearby =
        Nearby(
            row.get("object", String::class.java)!!,
            row.get("id", UUID::class.java)!!,
            row.get("label", String::class.java),
            row.get("distance_m", BigDecimal::class.java)!!
        )

    private fun territoryRow(
        row: Readable,
        target: Territory
    ): Map<String, Any?> =
        linkedMapOf(
            "concession" to
                mapOf(
                    "id" to row.get("concession", UUID::class.java).toString(),
                    "code" to row.get("concession_code", String::class.java),
                    "name" to row.get("concession_name", String::class.java)
                ),
            "object" to target.objectName,
            "id" to row.get("id", UUID::class.java).toString(),
            "name" to row.get("name", String::class.java),
            "localityType" to row.get("kind", String::class.java),
            "areaHa" to row.get("area_ha", BigDecimal::class.java)
        )

    // un objeto del tenant que puede estar sobre una concesión, con sus geometrías y la columna de su tipo
    class Territory(
        val objectName: String,
        val geometries: List<String>,
        val kind: String?
    )

    companion object {
        const val CONFLICT = "local_conflict"
        private val FEATURE_GEOMETRIES = listOf("boundary", "trace", "location")

        // los elementos ambientales sensibles del caso 13 (DOC-01 §7.1.2, Tabla 8.1); MICROCUENCA y CERRO no lo son
        val SENSITIVE = listOf("ZONA DE PROTECCION", "FUENTE DE AGUA", "SITIO SAGRADO", "RESTO ARQUEOLOGICO")

        // los eventos de nearby-conflicts y la columna que los nombra
        private val EVENTS = listOf("incident" to "code", "early_warning_signal" to "source_type")

        val TERRITORIES =
            listOf(
                Territory(TerritoryNames.COMMUNITY, listOf("territory"), null),
                Territory(TerritoryNames.SECTOR, listOf("boundary"), null),
                Territory(TerritoryNames.LOCALITY, listOf("boundary", "location"), "locality_type")
            )

        val REFERENCE_READS =
            listOf(
                GateRead(TerritoryNames.PROJECT, setOf("interest_area")),
                GateRead(TerritoryNames.COMPONENT, setOf("project", "superseded") + TerritoryNames.COMPONENT_GEOMETRIES)
            )
        private val PLACE_READ = GateRead(TerritoryNames.LOCALITY, setOf("location", "boundary"))

        // la referencia del proyecto: su área de interés y las versiones vigentes de sus componentes, juntas en 4326; NULL si
        // no tiene ninguna, y entonces nada está cerca
        internal fun reference(
            t: GateTables,
            bind: (Any) -> String,
            project: UUID
        ): String {
            val p = TerritoryNames.PROJECT
            val c = TerritoryNames.COMPONENT
            val component = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, 4326)
            return "WITH ref AS (SELECT ST_Collect(s.g) AS g FROM (" +
                "SELECT ST_Transform(p.${t.column(p, "interest_area")}, 4326) AS g FROM ${t.table(p)} p WHERE p.${t.column(p, SpatialGate.ID)} = ${bind(project)} " +
                "UNION ALL SELECT $component AS g FROM ${t.table(c)} c " +
                "WHERE c.${t.column(c, "project")} = ${bind(project)} AND c.${t.column(c, "superseded")} IS NOT TRUE" +
                ") s WHERE s.g IS NOT NULL) "
        }

        // el punto de un registro de la localidad: su ubicación o un punto sobre su límite, en 4326
        private fun localityPoint(
            t: GateTables,
            alias: String
        ): String {
            val l = TerritoryNames.LOCALITY
            return "ST_Transform($alias.${t.column(l, "location")}, 4326), ST_PointOnSurface(ST_Transform($alias.${t.column(l, "boundary")}, 4326))"
        }

        // que `place` (en 4326) esté a menos de `meters` de la referencia: prefiltro con la envolvente en grados y medida en UTM
        private fun near(
            place: String,
            bind: (Any) -> String,
            srid: String,
            meters: BigDecimal
        ): String =
            "$place && ST_Expand(ref.g, ${bind(SpatialSql.degrees(meters))}) " +
                "AND ST_DWithin(ST_Transform($place, $srid), ST_Transform(ref.g, $srid), ${bind(meters.toDouble())})"

        internal fun eventSql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            meters: BigDecimal,
            limit: Int,
            objectName: String,
            label: String
        ): String {
            val srid = bind(frame.srid)
            val place = "COALESCE(ST_Transform(e.${t.column(objectName, "location")}, 4326), ${localityPoint(t, "l")})"
            return reference(t, bind, frame.id) +
                "SELECT CAST(${bind(objectName)} AS text) AS object, e.${t.column(objectName, SpatialGate.ID)} AS id, " +
                "e.${t.column(objectName, label)}::text AS label, ${SpatialSql.distanceM(place, "ref.g", srid)} AS distance_m " +
                "FROM ${t.table(objectName)} e LEFT JOIN ${t.table(TerritoryNames.LOCALITY)} l " +
                "ON l.${t.column(TerritoryNames.LOCALITY, SpatialGate.ID)} = e.${t.column(objectName, "locality")} CROSS JOIN ref " +
                "WHERE ${near(place, bind, srid, meters)} ORDER BY distance_m LIMIT ${bind(limit + 1)}"
        }

        internal fun localitySql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            meters: BigDecimal,
            limit: Int
        ): String {
            val srid = bind(frame.srid)
            val place = "COALESCE(${localityPoint(t, "l")})"
            return reference(t, bind, frame.id) +
                "SELECT l.${t.column(TerritoryNames.LOCALITY, SpatialGate.ID)} AS id, ${SpatialSql.distanceM(place, "ref.g", srid)} AS distance_m " +
                "FROM ${t.table(TerritoryNames.LOCALITY)} l CROSS JOIN ref WHERE ${near(place, bind, srid, meters)} LIMIT ${bind(limit + 1)}"
        }

        internal fun sensitiveSql(
            t: GateTables,
            bind: (Any) -> String,
            frame: ProjectFrame,
            meters: BigDecimal,
            limit: Int
        ): String {
            val c = TerritoryNames.COMPONENT
            val f = TerritoryNames.FEATURE
            val srid = bind(frame.srid)
            val component = SpatialSql.firstOf(TerritoryNames.COMPONENT_GEOMETRIES.map { "c.${t.column(c, it)}" }, 4326)
            val feature = SpatialSql.firstOf(FEATURE_GEOMETRIES.map { "f.${t.column(f, it)}" }, 4326)
            return "SELECT c.${t.column(c, SpatialGate.ID)} AS component, c.${t.column(c, "code")}::text AS component_code, " +
                "c.${t.column(c, "component_type")}::text AS component_type, f.${t.column(f, SpatialGate.ID)} AS feature, " +
                "f.${t.column(f, "name")}::text AS feature_name, f.${t.column(f, "feature_type")}::text AS feature_type, " +
                "${SpatialSql.distanceM(component, feature, srid)} AS distance_m " +
                "FROM ${t.table(c)} c JOIN ${t.table(f)} f ON f.${t.column(f, "feature_type")} = ANY(${bind(SENSITIVE.toTypedArray())}) " +
                "AND $feature && ST_Expand($component, ${bind(SpatialSql.degrees(meters))}) " +
                "AND ST_DWithin(ST_Transform($component, $srid), ST_Transform($feature, $srid), ${bind(meters.toDouble())}) " +
                "WHERE c.${t.column(c, "project")} = ${bind(frame.id)} AND c.${t.column(c, "superseded")} IS NOT TRUE " +
                "ORDER BY distance_m, 2, 5 LIMIT ${bind(limit + 1)}"
        }

        internal fun concessionSql(
            t: GateTables,
            bind: (Any) -> String,
            concessions: List<UUID>,
            target: Territory,
            limit: Int
        ): String {
            val k = TerritoryNames.CONCESSION
            val x = target.objectName
            val boundary = "k.${t.column(k, "boundary")}"
            val crossing = target.geometries.joinToString(" OR ") { g -> "ST_Intersects(ST_Transform($boundary, ${t.srid(x, g)}), x.${t.column(x, g)})" }
            val shape = SpatialSql.firstOf(target.geometries.map { "x.${t.column(x, it)}" }, 4326)
            val piece = "ST_Intersection(ST_Transform($boundary, 4326), $shape)"
            val kind = target.kind?.let { "x.${t.column(x, it)}::text" } ?: "NULL::text"
            return "SELECT k.${t.column(k, SpatialGate.ID)} AS concession, k.${t.column(k, "code")}::text AS concession_code, " +
                "k.${t.column(k, "name")}::text AS concession_name, x.${t.column(x, SpatialGate.ID)} AS id, x.${t.column(x, "name")}::text AS name, " +
                "$kind AS kind, ${SpatialSql.zoneAreaHa(piece)} AS area_ha " +
                "FROM ${t.table(k)} k JOIN ${t.table(x)} x ON ($crossing) " +
                "WHERE k.${t.column(k, SpatialGate.ID)} = ANY(${bind(concessions.toTypedArray())}) LIMIT ${bind(limit + 1)}"
        }
    }
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory/projects")
class ProjectProximityQueriesController(
    private val queries: ProjectProximityQueries,
    private val limits: TerritoryQueryProperties
) {
    @GetMapping("/{id}/concession-territories")
    suspend fun concessionTerritories(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("limit"))
        return queries.concessionTerritories(id, QueryParams.limit(request.queryParams.getFirst("limit"), limits))
    }

    @GetMapping("/{id}/sensitive-distances")
    suspend fun sensitiveDistances(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("distanceKm", "limit"))
        return queries.sensitiveDistances(id, QueryParams.distanceM(params.getFirst("distanceKm"), limits), QueryParams.limit(params.getFirst("limit"), limits))
    }

    @GetMapping("/{id}/nearby-conflicts")
    suspend fun nearbyConflicts(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("distanceKm", "limit"))
        return queries.nearbyConflicts(id, QueryParams.distanceM(params.getFirst("distanceKm"), limits), QueryParams.limit(params.getFirst("limit"), limits))
    }
}
```

En la prueba unitaria del SQL de los sensibles, `0.01` es el margen en grados de 1 000 m (`SpatialSql.degrees`) y `32717` el
SRID enlazado del proyecto.

- [ ] **Paso 4: Correr la unitaria**

Run: el comando de unitarias con `--tests 'sgspe.territory.ProximitySqlTest'`.
Expected: PASS, 3 pruebas.

- [ ] **Paso 5: La prueba de integración**

Crear `src/test/kotlin/sgspe/territory/SpatialProximityApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID
import kotlin.math.abs

// concesiones × territorios, elementos sensibles y conflictos cercanos (casos 12, 13 y 7 de gis.md §5.3). cada prueba en su
// banda al sur del origen, y las aserciones solo miran los ids que crea
class SpatialProximityApiTest : F6QueryApiTest() {
    private fun near(
        expected: Double,
        actual: JsonNode?
    ) = assertTrue(actual != null && abs(actual.asDouble() - expected) <= expected * 0.01, "esperado $expected ± 1 %, llegó $actual")

    @Test
    fun `las comunidades, los sectores y las localidades sobre las concesiones del proyecto`() {
        val y = -50_000.0
        val project = runBlocking { utmProject() }
        val concession =
            createWith(
                "mining_concession",
                mapOf("code" to uniqueName("CM-"), "name" to "Concesión Ejemplo"),
                mapOf("boundary" to rect(0.0, y, 2000.0, y + 1000, multi = true)),
                headers = withReason("alta de prueba")
            )
        assertTrue(link("project", project.toString(), "project_concession", concession).status.is2xxSuccessful)
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(1000.0, y, 3000.0, y + 1000, multi = true)) }
        val sector =
            createWith(
                "community_sector",
                mapOf("community" to community.toString(), "name" to "Sector Comunal Ejemplo"),
                mapOf("boundary" to rect(1200.0, y + 200, 1400.0, y + 400)),
                headers = withReason("alta de prueba")
            )
        val place = localityWith("Caserío Ejemplo", location = at(500.0, y + 500))
        val rows = get("/api/sgspe/territory/projects/$project/concession-territories")["rows"].values().filter { it["concession"]["id"].asString() == concession }
        val byId = rows.associateBy { it["id"].asString() }
        near(100.0, byId.getValue(community.toString())["areaHa"])
        assertEquals("community_sector", byId.getValue(sector)["object"].asString())
        near(4.0, byId.getValue(sector)["areaHa"])
        assertEquals("CASERIO", byId.getValue(place.toString())["localityType"].asString())
        assertTrue(absent(byId.getValue(place.toString())["areaHa"]))
    }

    @Test
    fun `los componentes a menos de la distancia de un elemento sensible, del mas cercano al mas lejano`() {
        val y = -60_000.0
        val project = runBlocking { utmProject() }
        val platform = runBlocking { component(project, uniqueName("PLT-"), "PLATAFORMA", rect(0.0, y, 100.0, y + 100)) }
        val water = runBlocking { environmentalFeature("Fuente de agua Ejemplo", "FUENTE DE AGUA", location = at(600.0, y + 50)) }
        val hill = runBlocking { environmentalFeature("Cerro Ejemplo", "CERRO", location = at(300.0, y + 50)) }
        val sacred = runBlocking { environmentalFeature("Sitio sagrado Ejemplo", "SITIO SAGRADO", location = at(5000.0, y)) }
        val rows = get("/api/sgspe/territory/projects/$project/sensitive-distances?distanceKm=1")["rows"].values().filter { it["component"]["id"].asString() == platform.toString() }
        val features = rows.map { it["feature"]["id"].asString() }
        assertTrue(water.toString() in features && hill.toString() !in features && sacred.toString() !in features, "$features")
        near(500.0, rows.first { it["feature"]["id"].asString() == water.toString() }["distanceM"])
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/$project/sensitive-distances?distanceKm=60", HttpStatus.BAD_REQUEST))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/projects/$project/sensitive-distances?distanceKm=0.05", HttpStatus.BAD_REQUEST))
    }

    @Test
    fun `incidentes, senales y conflictos cercanos, ordenados por distancia`() {
        val y = -70_000.0
        val project = runBlocking { utmProject() }
        runBlocking { component(project, uniqueName("PLT-"), "PLATAFORMA", rect(0.0, y, 100.0, y + 100)) }
        val place = runBlocking { locality("Caserío Ejemplo", at(2000.0, y + 50)) }
        val close = incident(project.toString())
        assertEquals(HttpStatus.OK, edit("incident", close, emptyMap(), headers = withReason("ubicación de prueba"), geometries = mapOf("location" to at(1000.0, y + 50))).status)
        val far = incident(project.toString())
        assertEquals(HttpStatus.OK, edit("incident", far, emptyMap(), headers = withReason("ubicación de prueba"), geometries = mapOf("location" to at(20000.0, y))).status)
        val signal = warningSignal(project.toString(), attributes = mapOf("locality" to place.toString()))
        val conflict = fixture("local_conflict", mapOf("conflict_status" to "ACTIVO")).id
        assertTrue(linkWithReason("local_conflict", conflict, "local_conflict_locality", place.toString()).status.is2xxSuccessful)

        val items =
            get("/api/sgspe/territory/projects/$project/nearby-conflicts?distanceKm=5")["items"]
                .values()
                .filter { it["id"].asString() in setOf(close, far, signal, conflict) }
        assertEquals(close, items.first()["id"].asString())
        near(900.0, items.first()["distanceM"])
        assertEquals(setOf(signal, conflict), items.drop(1).map { it["id"].asString() }.toSet())
        assertTrue(items.drop(1).all { abs(it["distanceM"].asDouble() - 1900.0) <= 19.0 })
        assertTrue(far !in items.map { it["id"].asString() })
    }
}
```

`link` (de `DomainApiTest`) y `linkWithReason` (de `F3ApiTest`) devuelven la respuesta; `fixture` es el de `F2ApiTest` (un
`local_conflict` mínimo válido con los valores dados). `edit` reenvía el incidente con su ubicación por la API genérica.

- [ ] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/ProjectProximityQueries.kt src/test/kotlin/sgspe/territory/ProximitySqlTest.kt \
  src/test/kotlin/sgspe/territory/SpatialProximityApiTest.kt
git commit -F - <<'MSG'
feat(territory): concesiones, elementos sensibles y conflictos cercanos al proyecto

concession-territories da las comunidades, sectores y localidades sobre las concesiones, con el
área en la zona del centroide; sensitive-distances y nearby-conflicts miden en el CRS de trabajo
tras el prefiltro con el índice, del más cercano al más lejano.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `SpatialProximityApiTest`.

- [ ] **Paso 7: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.SpatialProximityApiTest'`.
Expected: PASS, 3 pruebas.

---

### Tarea 9: La concentración de eventos y el resumen del área (`concentration`, `summary`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/TerritoryAggregates.kt`
- Test: `src/test/kotlin/sgspe/territory/ConcentrationSqlTest.kt`, `src/test/kotlin/sgspe/territory/SpatialAggregatesApiTest.kt`

**Interfaces:**
- Consumes: `SpatialGate`, `GateRead`, `GateTables`, `GateOutcome` (Tarea 1); `SpatialParts`, `Period`, `QueryParams`,
  `TerritoryNames`, `TerritoryQueryProperties` (Tarea 4); `ComponentLand`, `ComponentLandRow` (Tarea 7); `Readability`,
  `Records.find`, `Records.all`, `Criteria.anyOf`; `F6QueryApiTest` (Tarea 2).
- Produces: los endpoints `GET /api/sgspe/territory/concentration` y `GET /api/sgspe/areas-of-influence/{id}/summary`, y
  `TerritoryAggregates.EVENTS` (el objeto de cada evento y su fecha) y `TerritoryAggregates.PRIVATE` (`PROPIEDAD`, `POSESION`).

- [ ] **Paso 1: La prueba unitaria del SQL**

Crear `src/test/kotlin/sgspe/territory/ConcentrationSqlTest.kt`:

```kotlin
package sgspe.territory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// la concentración por localidad, nunca por punto (caso 4 y 6 de gis.md §5.3, consecuencia 4 de la especificación de F6): cada
// evento cuenta en su localidad o, sin ella, en su comunidad; los que no tienen ninguna no cuentan; el periodo en instantes
// de Lima y el proyecto solo si se pidió
class ConcentrationSqlTest {
    private val tables =
        object : GateTables {
            override fun table(objectName: String) = "<$objectName>"

            override fun column(
                objectName: String,
                field: String
            ) = "\"$field\""

            override fun srid(
                objectName: String,
                field: String
            ) = 4326
        }

    private fun render(projects: List<UUID>): Pair<String, List<Any>> {
        val values = mutableListOf<Any>()
        val period = Period(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"))
        val sql =
            TerritoryAggregates.concentrationSql(tables, { value ->
                values += value
                ":g${values.size - 1}"
            }, "grievance", "received_at", projects, period, 100)
        return sql to values
    }

    @Test
    fun `agrupa por la localidad o la comunidad del evento y deja fuera los que no tienen territorio`() {
        val (sql, values) = render(emptyList())
        assertTrue(sql.contains("CASE WHEN e.\"locality\" IS NOT NULL THEN 'locality' ELSE 'community' END AS kind"), sql)
        assertTrue(sql.contains("AND (e.\"locality\" IS NOT NULL OR e.\"community\" IS NOT NULL) GROUP BY 1, 2"), sql)
        assertFalse(sql.contains("e.\"project\""), sql)
        assertEquals(OffsetDateTime.parse("2026-10-01T05:00Z"), values[0])
        assertEquals(OffsetDateTime.parse("2026-11-01T05:00Z"), values[1])
        assertEquals(101, values.last())
    }

    @Test
    fun `con proyectos acota por ellos`() {
        val project = UUID.randomUUID()
        val (sql, values) = render(listOf(project))
        assertTrue(sql.contains("AND e.\"project\" = ANY(:g2)"), sql)
        assertEquals(listOf(project), (values[2] as Array<*>).toList())
    }
}
```

- [ ] **Paso 2: Correrla para verla fallar**

Run: el comando de unitarias con `--tests 'sgspe.territory.ConcentrationSqlTest'`.
Expected: FAIL de compilación («Unresolved reference: TerritoryAggregates»).

- [ ] **Paso 3: Las dos consultas**

Crear `src/main/kotlin/sgspe/territory/TerritoryAggregates.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import tools.jackson.databind.json.JsonMapper
import wasichai.core.metadata.MetadataService
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

// las agregaciones territoriales (patrón b de gis.md §5.2):
// - concentration (casos 4 y 6): eventos por localidad o, sin ella, por comunidad, para el mapa de calor; nunca por punto. la
//   celda trae el punto de su localidad (su ubicación o un punto sobre su límite) o de su comunidad. las QCS confidenciales
//   que quien llama no ve no cuentan: la compuerta aplica ConfidentialAttentionScope como Records
// - summary (caso 15, RQ-DSH-029): la superficie oficial del área, sus localidades miembro por tipo y los componentes del
//   proyecto sobre tierra comunal o privada (la consulta de KPI-TER-04, ComponentLand). una cifra que quien llama no puede
//   leer sale null con missingFields, nunca cero
@Service
class TerritoryAggregates(
    private val gate: SpatialGate,
    private val records: Records,
    private val metadata: MetadataService,
    private val componentLand: ComponentLand,
    private val limits: TerritoryQueryProperties
) {
    suspend fun concentration(
        of: String?,
        projects: List<UUID>,
        period: Period,
        limit: Int
    ): Map<String, Any?> {
        val dateField =
            of?.let { EVENTS[it] }
                ?: throw Finding("INVALID_OF", "of debe ser uno de ${EVENTS.keys.sorted().joinToString(", ")}", "of", status = HttpStatus.BAD_REQUEST)
                    .toCodedException()
        val reads =
            listOf(
                GateRead(of, setOf("project", TerritoryNames.LOCALITY, TerritoryNames.COMMUNITY, dateField), principal = true),
                GateRead(TerritoryNames.LOCALITY, setOf("name", "location", "boundary")),
                GateRead(TerritoryNames.COMMUNITY, setOf("name", "territory"))
            )
        val parts = SpatialParts<Map<String, Any?>>()
        parts.add(
            gate.select(reads, { bind -> concentrationSql(this, bind, of, dateField, projects, period, limit) }) { row ->
                linkedMapOf(
                    "object" to row.get("kind", String::class.java),
                    "id" to row.get("zone", UUID::class.java).toString(),
                    "name" to row.get("name", String::class.java),
                    "point" to row.get("point", String::class.java)?.let { JSON.readValue(it, Map::class.java) },
                    "count" to row.get("n", java.lang.Long::class.java)?.toLong()
                )
            }
        )
        return linkedMapOf<String, Any?>("of" to of, "periodStart" to period.start.toString(), "periodEnd" to period.end.toString()) +
            parts.response("cells", parts.rows.take(limit), truncated = parts.rows.size > limit)
    }

    suspend fun summary(areaId: UUID): Map<String, Any?> {
        val area =
            records.find(TerritoryNames.AREA, areaId)
                ?: throw Finding("AREA_NOT_FOUND", "El área de influencia no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND)
                    .toCodedException()
        val readable = Readability(metadata)
        val missing = linkedSetOf<String>()
        val areaHa: BigDecimal? =
            if (readable.canRead("${TerritoryNames.AREA}.calculated_area_ha")) {
                Values.decimal(area.attributes["calculated_area_ha"])
            } else {
                missing += "${TerritoryNames.AREA}.calculated_area_ha"
                null
            }
        val byType = localitiesByType(areaId, readable, missing)
        var communalLand: Int? = null
        var privateLand: Int? = null
        when (val outcome = componentLand.rows(listOfNotNull(Records.uuid(area.attributes["project"])))) {
            is GateOutcome.Rows -> {
                communalLand = outcome.rows.filter { it.land == ComponentLand.COMMUNAL || it.land == COMMUNAL_PARCEL }.map { it.component }.distinct().size
                privateLand = outcome.rows.filter { it.land in PRIVATE }.map { it.component }.distinct().size
            }
            is GateOutcome.Unavailable -> missing += outcome.missingFields
        }
        val out = linkedMapOf<String, Any?>("available" to missing.isEmpty())
        if (missing.isNotEmpty()) out["missingFields"] = missing.toList()
        out["areaHa"] = areaHa
        out["localitiesByType"] = byType
        out["componentsOnCommunalLand"] = communalLand
        out["componentsOnPrivateLand"] = privateLand
        return out
    }

    // las localidades miembro de esta versión, por locality_type; null si quien llama no puede leer lo que hace falta
    private suspend fun localitiesByType(
        areaId: UUID,
        readable: Readability,
        missing: MutableSet<String>
    ): List<Map<String, Any?>>? {
        val absent = readable.missing(listOf("${TerritoryNames.MEMBER}.area_of_influence", "${TerritoryNames.MEMBER}.locality", "${TerritoryNames.LOCALITY}.locality_type"))
        if (absent.isNotEmpty()) {
            missing += absent
            return null
        }
        val localities =
            records
                .all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(TerritoryNames.AREA, listOf(areaId))), max = limits.maxRows)
                .mapNotNull { Records.uuid(it.attributes[TerritoryNames.LOCALITY]) }
                .distinct()
        if (localities.isEmpty()) return emptyList()
        return records
            .all(TerritoryNames.LOCALITY, ids = localities, max = limits.maxRows)
            .groupingBy { Values.text(it.attributes["locality_type"]) ?: NONE }
            .eachCount()
            .toSortedMap()
            .map { (type, count) -> mapOf("localityType" to type, "count" to count) }
    }

    companion object {
        private val JSON: JsonMapper = JsonMapper.builder().build()
        private const val NONE = "SIN DATO"
        private const val COMMUNAL_PARCEL = "COMUNAL"

        // las tenencias privadas: piden permiso de terceros. PROPIEDAD DE LA EMPRESA es de la empresa
        val PRIVATE = setOf("PROPIEDAD", "POSESION")

        // los eventos de concentration y su fecha (DATETIME)
        val EVENTS: Map<String, String> =
            mapOf(
                "engagement" to "held_at",
                "grievance" to "received_at",
                "inquiry" to "received_at",
                "stakeholder_request" to "received_at",
                "incident" to "occurred_at",
                "early_warning_signal" to "received_at"
            )

        internal fun concentrationSql(
            t: GateTables,
            bind: (Any) -> String,
            of: String,
            dateField: String,
            projects: List<UUID>,
            period: Period,
            limit: Int
        ): String {
            val l = TerritoryNames.LOCALITY
            val m = TerritoryNames.COMMUNITY
            val locality = "e.${t.column(of, l)}"
            val community = "e.${t.column(of, m)}"
            val date = "e.${t.column(of, dateField)}"
            val from = bind(OffsetDateTime.ofInstant(period.from(), ZoneOffset.UTC))
            val until = bind(OffsetDateTime.ofInstant(period.until(), ZoneOffset.UTC))
            val project = if (projects.isEmpty()) "" else " AND e.${t.column(of, "project")} = ANY(${bind(projects.toTypedArray())})"
            val point =
                "COALESCE(ST_Transform(l.${t.column(l, "location")}, 4326), ST_PointOnSurface(ST_Transform(l.${t.column(l, "boundary")}, 4326)), " +
                    "ST_PointOnSurface(ST_Transform(m.${t.column(m, "territory")}, 4326)))"
            return "SELECT k.kind AS kind, k.zone AS zone, k.n AS n, COALESCE(l.${t.column(l, "name")}, m.${t.column(m, "name")})::text AS name, " +
                "ST_AsGeoJSON($point, 9) AS point FROM (" +
                "SELECT CASE WHEN $locality IS NOT NULL THEN 'locality' ELSE 'community' END AS kind, COALESCE($locality, $community) AS zone, count(*) AS n " +
                "FROM ${t.table(of)} e WHERE $date >= $from AND $date < $until$project " +
                "AND ($locality IS NOT NULL OR $community IS NOT NULL) GROUP BY 1, 2) k " +
                "LEFT JOIN ${t.table(l)} l ON k.kind = 'locality' AND l.${t.column(l, SpatialGate.ID)} = k.zone " +
                "LEFT JOIN ${t.table(m)} m ON k.kind = 'community' AND m.${t.column(m, SpatialGate.ID)} = k.zone " +
                "ORDER BY k.n DESC, k.zone LIMIT ${bind(limit + 1)}"
        }
    }
}

// api.md §4 (F6)
@RestController
class TerritoryAggregatesController(
    private val aggregates: TerritoryAggregates,
    private val limits: TerritoryQueryProperties,
    private val clock: Clock
) {
    @GetMapping("/api/sgspe/territory/concentration")
    suspend fun concentration(request: ServerHttpRequest): Map<String, Any?> {
        val params = request.queryParams
        StrictQuery.requireOnly(params, setOf("of", "project", "periodStart", "periodEnd", "limit"))
        val period = QueryParams.period(params.getFirst("periodStart"), params.getFirst("periodEnd"), LocalDate.now(clock.withZone(LIMA)), limits)
        return aggregates.concentration(params.getFirst("of"), QueryParams.uuids("project", params["project"]), period, QueryParams.limit(params.getFirst("limit"), limits))
    }

    @GetMapping("/api/sgspe/areas-of-influence/{id}/summary")
    suspend fun summary(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, emptySet())
        return aggregates.summary(id)
    }
}
```

Nota: `GateTables.column(of, "locality")` exige que `locality` esté declarado en el `GateRead` de `of`; por eso el `GateRead`
del evento nombra `TerritoryNames.LOCALITY` y `TerritoryNames.COMMUNITY` como campos (son los nombres de las relaciones).

- [ ] **Paso 4: Correr la unitaria**

Run: el comando de unitarias con `--tests 'sgspe.territory.ConcentrationSqlTest'`.
Expected: PASS, 2 pruebas.

- [ ] **Paso 5: La prueba de integración**

Crear `src/test/kotlin/sgspe/territory/SpatialAggregatesApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID

// la concentración de eventos para el mapa de calor (casos 4 y 6 de gis.md §5.3) y el resumen del área (caso 15). las
// quejas de attentionAttributes se reciben el 2026-10-05
class SpatialAggregatesApiTest : F6QueryApiTest() {
    private val october = "periodStart=2026-10-01&periodEnd=2026-10-31"

    @Test
    fun `las QCS cuentan en su localidad o, sin ella, en su comunidad, y sin territorio no cuentan`() {
        val y = -80_000.0
        val project = runBlocking { utmProject() }
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, y)) }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(1000.0, y, 2000.0, y + 1000, multi = true)) }
        repeat(3) { grievance(project.toString(), attributes = mapOf("locality" to place.toString())) }
        grievance(project.toString(), attributes = mapOf("community" to community.toString()))
        grievance(project.toString())
        val body = get("/api/sgspe/territory/concentration?of=grievance&project=$project&$october")
        assertTrue(body["available"].asBoolean())
        val cells = body["cells"].values().associateBy { it["id"].asString() }
        assertEquals(2, cells.size)
        assertEquals(3, cells.getValue(place.toString())["count"].asInt())
        assertEquals("locality", cells.getValue(place.toString())["object"].asString())
        assertEquals("Point", cells.getValue(place.toString())["point"]["type"].asString())
        assertEquals(1, cells.getValue(community.toString())["count"].asInt())
        assertEquals("community", cells.getValue(community.toString())["object"].asString())
        assertEquals(0, get("/api/sgspe/territory/concentration?of=grievance&project=$project&periodStart=2026-11-01&periodEnd=2026-11-30")["cells"].size())
    }

    @Test
    fun `los incidentes tambien se concentran y los errores dicen que falta`() {
        val project = runBlocking { utmProject() }
        val place = runBlocking { locality("Caserío Ejemplo", at(0.0, -81_000.0)) }
        incident(project.toString(), attributes = mapOf("locality" to place.toString()))
        val cells = get("/api/sgspe/territory/concentration?of=incident&project=$project&$october")["cells"].values()
        assertEquals(listOf(place.toString() to 1), cells.map { it["id"].asString() to it["count"].asInt() })
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/concentration?of=commitment", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_OF", failing("/api/sgspe/territory/concentration", HttpStatus.BAD_REQUEST))
        assertEquals("OUT_OF_RANGE", failing("/api/sgspe/territory/concentration?of=grievance&periodStart=2020-01-01&periodEnd=2026-10-31", HttpStatus.BAD_REQUEST))
        assertEquals("INVALID_VALUE", failing("/api/sgspe/territory/concentration?of=grievance&project=no-es-uuid", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/concentration?of=grievance&radio=1").status)
    }

    @Test
    fun `el resumen del area da su superficie, sus localidades por tipo y los componentes sobre tierra comunal o privada`() {
        val y = -90_000.0
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, y, 1000.0, y + 1000, multi = true))
        listOf(
            localityWith("Caserío Ejemplo A", location = at(100.0, y + 100)),
            localityWith("Caserío Ejemplo B", location = at(200.0, y + 100)),
            localityWith("Centro Poblado Ejemplo", location = at(300.0, y + 100), type = "CENTRO POBLADO")
        ).forEach { areaMember(area, "locality", it) }
        runBlocking {
            parcel(uniqueName("PR-"), "PROPIEDAD", rect(2000.0, y, 3000.0, y + 1000))
            communityWithTerritory("Comunidad Ejemplo", rect(4000.0, y, 5000.0, y + 1000, multi = true))
            component(project, uniqueName("PLT-"), "PLATAFORMA", rect(2400.0, y + 400, 2600.0, y + 600))
            component(project, uniqueName("ACC-"), "ACCESO", path(3900.0 to y + 500, 4500.0 to y + 500))
        }
        val body = get("/api/sgspe/areas-of-influence/$area/summary")
        assertTrue(body["available"].asBoolean())
        assertEquals(record("area_of_influence", area.toString())["attributes"]["calculated_area_ha"].asDouble(), body["areaHa"].asDouble())
        assertEquals(
            listOf("CASERIO" to 2, "CENTRO POBLADO" to 1),
            body["localitiesByType"].values().map { it["localityType"].asString() to it["count"].asInt() }
        )
        assertEquals(1, body["componentsOnPrivateLand"].asInt())
        assertEquals(1, body["componentsOnCommunalLand"].asInt())
        assertEquals("AREA_NOT_FOUND", failing("/api/sgspe/areas-of-influence/${UUID.randomUUID()}/summary", HttpStatus.NOT_FOUND))
    }
}
```

`incident(project, creator, attributes)` es el de `F4ApiTest` (ocurrido el 2026-10-06); `grievance`, el de `F4ApiTest`. Los
conteos por componente son distintos: un componente que cruza dos predios cuenta una vez.

- [ ] **Paso 6: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/TerritoryAggregates.kt src/test/kotlin/sgspe/territory/ConcentrationSqlTest.kt \
  src/test/kotlin/sgspe/territory/SpatialAggregatesApiTest.kt
git commit -F - <<'MSG'
feat(territory): concentración de eventos por localidad y resumen del área

concentration cuenta los eventos del periodo en su localidad o su comunidad, con el punto de cada
una, por la compuerta; summary da la superficie oficial, las localidades miembro por tipo y los
componentes sobre tierra comunal o privada con la consulta de KPI-TER-04.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `SpatialAggregatesApiTest`.

- [ ] **Paso 7: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.SpatialAggregatesApiTest'`.
Expected: PASS, 3 pruebas.

---

### Tarea 10: La parte territorial de la ficha (`localities/{id}/card`, `communities/{id}/card`)

**Files:**
- Create: `src/main/kotlin/sgspe/territory/TerritoryCards.kt`
- Test: `src/test/kotlin/sgspe/territory/TerritoryCardsApiTest.kt`

**Interfaces:**
- Consumes: `Zone`, `ZoneCriterion.of` (Tarea 3); `TerritoryNames`, `TerritoryQueryProperties`, `QueryParams.uuids` (Tarea
  4); `Records.find`, `Records.all`, `Records.geometriesOf`, `Criteria.anyOf`, `Criteria.inState`; `F6QueryApiTest`
  (Tarea 2).
- Produces: los endpoints `GET /api/sgspe/territory/localities/{id}/card` y `GET /api/sgspe/territory/communities/{id}/card`
  (las cifras de la ficha son KPI-TER-01, Tarea 13).

- [ ] **Paso 1: La prueba de integración**

Crear `src/test/kotlin/sgspe/territory/TerritoryCardsApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID

// la parte territorial de la ficha contextual (api.md §4, RQ-GIS-005): identificación, geometría y relaciones de una
// localidad o una comunidad, leídas como quien llama. las cifras no salen de aquí: son KPI-TER-01 (sgspe.dashboard)
class TerritoryCardsApiTest : F6QueryApiTest() {
    private val y = -100_000.0

    private fun ids(
        node: tools.jackson.databind.JsonNode,
        key: String
    ): Set<String> = node[key].values().map { it["id"].asString() }.toSet()

    @Test
    fun `la ficha de una localidad trae su unidad, las comunidades que la contienen y sus areas aprobadas`() {
        val (project, other) = runBlocking { utmProject() to utmProject() }
        val district = unit("DISTRITO", rect(-500.0, y - 500, 1500.0, y + 1500, multi = true))
        val place =
            localityWith(
                "Caserío Ejemplo",
                boundary = rect(0.0, y, 500.0, y + 500),
                location = at(250.0, y + 250),
                attributes = mapOf("administrative_unit" to district.toString())
            )
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(-100.0, y - 100, 1000.0, y + 1000, multi = true)) }
        val mine = area(project).also { areaMember(it, "locality", place) }
        approveArea(mine, project)
        val theirs = area(other).also { areaMember(it, "locality", place) }
        approveArea(theirs, other)
        area(project, type = "AIIS").also { areaMember(it, "locality", place) }

        val card = get("/api/sgspe/territory/localities/$place/card")
        assertEquals(place.toString(), card["locality"]["id"].asString())
        assertEquals("Polygon", card["geometry"]["boundary"]["type"].asString())
        assertEquals("Point", card["geometry"]["location"]["type"].asString())
        assertEquals(district.toString(), card["administrativeUnit"]["id"].asString())
        assertTrue(community.toString() in ids(card, "communities"))
        assertEquals(setOf(mine.toString(), theirs.toString()), ids(card, "areasOfInfluence"))
        assertEquals(0, card["unavailable"].size())
        assertEquals(setOf(mine.toString()), ids(get("/api/sgspe/territory/localities/$place/card?project=$project"), "areasOfInfluence"))
    }

    @Test
    fun `la ficha de una comunidad trae su territorio, sus sectores y sus areas`() {
        val project = runBlocking { utmProject() }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(0.0, y - 5000, 1000.0, y - 4000, multi = true)) }
        val sector =
            createWith(
                "community_sector",
                mapOf("community" to community.toString(), "name" to "Sector Comunal Ejemplo"),
                headers = withReason("alta de prueba")
            )
        val mine = area(project).also { areaMember(it, "community", community) }
        approveArea(mine, project)
        val card = get("/api/sgspe/territory/communities/$community/card")
        assertEquals(community.toString(), card["community"]["id"].asString())
        assertEquals("MultiPolygon", card["geometry"]["territory"]["type"].asString())
        assertEquals(setOf(sector), ids(card, "sectors"))
        assertEquals(setOf(mine.toString()), ids(card, "areasOfInfluence"))
    }

    @Test
    fun `los errores de la ficha`() {
        val place = localityWith("Caserío Ejemplo", location = at(0.0, y))
        assertEquals("LOCALITY_NOT_FOUND", failing("/api/sgspe/territory/localities/${UUID.randomUUID()}/card", HttpStatus.NOT_FOUND))
        assertEquals("COMMUNITY_NOT_FOUND", failing("/api/sgspe/territory/communities/${UUID.randomUUID()}/card", HttpStatus.NOT_FOUND))
        assertEquals("INVALID_VALUE", failing("/api/sgspe/territory/localities/$place/card?project=no-es-uuid", HttpStatus.BAD_REQUEST))
        assertEquals(HttpStatus.valueOf(422), exchange("GET", "/api/sgspe/territory/localities/$place/card?radio=1").status)
    }
}
```

- [ ] **Paso 2: La ficha**

Crear `src/main/kotlin/sgspe/territory/TerritoryCards.kt`:

```kotlin
package sgspe.territory

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordResponse
import java.util.UUID

// la parte territorial de la ficha contextual (api.md §4, dashboards.md §5.9): sgspe.territory solo aporta la identificación,
// la geometría y las relaciones territoriales del lugar elegido, leídas por Records como quien llama; las cifras son
// KPI-TER-01. las comunidades de una localidad son las que contienen su límite o su punto (patrón a, la geometría enlazada);
// las áreas de influencia, las versiones aprobadas que la tienen como miembro (con `project`, solo las de ese proyecto). lo
// que quien llama no puede leer sale vacío y su objeto en `unavailable`
@Service
class TerritoryCards(
    private val records: Records,
    private val limits: TerritoryQueryProperties
) {
    suspend fun locality(
        id: UUID,
        project: UUID?
    ): Map<String, Any?> {
        val locality = records.find(TerritoryNames.LOCALITY, id) ?: throw notFound("LOCALITY_NOT_FOUND", "La localidad")
        val geometries = Records.geometriesOf(locality)
        val unavailable = mutableListOf<String>()
        val shape = geometries["boundary"] ?: geometries["location"]
        val communities =
            guarded(TerritoryNames.COMMUNITY, unavailable) {
                if (shape == null) {
                    emptyList()
                } else {
                    val inside = ZoneCriterion.of(emptyList(), listOf("territory"), Zone(emptyMap(), shape))
                    records.all(TerritoryNames.COMMUNITY, criteria = listOf(inside), max = limits.maxRows).sortedWith(BY_NAME).map(::brief)
                }
            }
        return linkedMapOf(
            "locality" to locality,
            "geometry" to mapOf("location" to geometries["location"], "boundary" to geometries["boundary"]),
            "administrativeUnit" to unitOf(locality, unavailable),
            "communities" to communities,
            "areasOfInfluence" to areas(TerritoryNames.LOCALITY, id, project, unavailable),
            "unavailable" to unavailable
        )
    }

    suspend fun community(
        id: UUID,
        project: UUID?
    ): Map<String, Any?> {
        val community = records.find(TerritoryNames.COMMUNITY, id) ?: throw notFound("COMMUNITY_NOT_FOUND", "La comunidad")
        val unavailable = mutableListOf<String>()
        val sectors =
            guarded(TerritoryNames.SECTOR, unavailable) {
                records
                    .all(TerritoryNames.SECTOR, criteria = listOf(Criteria.anyOf(TerritoryNames.COMMUNITY, listOf(id))), max = limits.maxRows)
                    .sortedWith(BY_NAME)
                    .map(::brief)
            }
        return linkedMapOf(
            "community" to community,
            "geometry" to mapOf("territory" to Records.geometriesOf(community)["territory"]),
            "administrativeUnit" to unitOf(community, unavailable),
            "sectors" to sectors,
            "areasOfInfluence" to areas(TerritoryNames.COMMUNITY, id, project, unavailable),
            "unavailable" to unavailable
        )
    }

    private suspend fun unitOf(
        record: RecordResponse,
        unavailable: MutableList<String>
    ): Map<String, Any?>? {
        val id = Records.uuid(record.attributes[TerritoryNames.UNIT]) ?: return null
        return guarded(TerritoryNames.UNIT, unavailable) { listOfNotNull(records.find(TerritoryNames.UNIT, id)) }
            .firstOrNull()
            ?.let { mapOf("id" to it.id, "name" to Values.text(it.attributes["name"]), "level" to Values.text(it.attributes["level"])) }
    }

    // las versiones aprobadas de las áreas que tienen el lugar como miembro, con la condición del miembro
    private suspend fun areas(
        field: String,
        id: UUID,
        project: UUID?,
        unavailable: MutableList<String>
    ): List<Map<String, Any?>> {
        val members =
            guarded(TerritoryNames.MEMBER, unavailable) {
                records.all(TerritoryNames.MEMBER, criteria = listOf(Criteria.anyOf(field, listOf(id))), max = limits.maxRows)
            }
        val conditions = members.mapNotNull { m -> Records.uuid(m.attributes[TerritoryNames.AREA])?.let { it to Values.text(m.attributes["member_condition"]) } }.toMap()
        if (conditions.isEmpty()) return emptyList()
        val criteria = listOfNotNull(Criteria.inState(setOf(APPROVED)), project?.let { Criteria.anyOf("project", listOf(it)) })
        val areas =
            guarded(TerritoryNames.AREA, unavailable) {
                records.all(TerritoryNames.AREA, criteria = criteria, ids = conditions.keys.toList(), max = limits.maxRows)
            }
        return areas
            .sortedWith(compareBy({ Values.text(it.attributes["area_type"]) }, { it.id }))
            .map { area ->
                linkedMapOf(
                    "id" to area.id,
                    "project" to Values.text(area.attributes["project"]),
                    "areaType" to Values.text(area.attributes["area_type"]),
                    "versionNumber" to Values.decimal(area.attributes["version_number"])?.toInt(),
                    "memberCondition" to conditions[UUID.fromString(area.id)]
                )
            }
    }

    private suspend fun <T> guarded(
        objectName: String,
        unavailable: MutableList<String>,
        block: suspend () -> List<T>
    ): List<T> =
        try {
            block()
        } catch (_: ForbiddenException) {
            unavailable += objectName
            emptyList()
        }

    private fun brief(record: RecordResponse): Map<String, Any?> = mapOf("id" to record.id, "name" to Values.text(record.attributes["name"]))

    private fun notFound(
        code: String,
        what: String
    ) = Finding(code, "$what no existe o no está a su alcance", "id", status = HttpStatus.NOT_FOUND).toCodedException()

    companion object {
        private const val APPROVED = "approved"
        private val BY_NAME = compareBy<RecordResponse>({ Values.text(it.attributes["name"]) }, { it.id })
    }
}

// api.md §4 (F6)
@RestController
@RequestMapping("/api/sgspe/territory")
class TerritoryCardsController(
    private val cards: TerritoryCards
) {
    @GetMapping("/localities/{id}/card")
    suspend fun locality(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("project"))
        return cards.locality(id, QueryParams.uuids("project", request.queryParams["project"]).firstOrNull())
    }

    @GetMapping("/communities/{id}/card")
    suspend fun community(
        @PathVariable id: UUID,
        request: ServerHttpRequest
    ): Map<String, Any?> {
        StrictQuery.requireOnly(request.queryParams, setOf("project"))
        return cards.community(id, QueryParams.uuids("project", request.queryParams["project"]).firstOrNull())
    }
}
```

- [ ] **Paso 3: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/territory/TerritoryCards.kt src/test/kotlin/sgspe/territory/TerritoryCardsApiTest.kt
git commit -F - <<'MSG'
feat(territory): la parte territorial de la ficha de una localidad o una comunidad

La ficha trae el registro, su geometría, su unidad administrativa, las comunidades que contienen la
localidad o los sectores de la comunidad y las áreas aprobadas que la tienen como miembro, leídos
como quien llama; las cifras siguen en KPI-TER-01.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `TerritoryCardsApiTest`.

- [ ] **Paso 4: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.TerritoryCardsApiTest'`.
Expected: PASS, 3 pruebas.

---

### Tarea 11: Los casos de la compuerta por los endpoints (`SpatialGateApiTest`)

**Files:**
- Test: `src/test/kotlin/sgspe/territory/SpatialGateApiTest.kt`

**Interfaces:**
- Consumes: `component-parcels` (Tarea 7) y `concentration` (Tarea 9); `F6QueryApiTest` (Tarea 2); de `F6ApiTest`,
  `utmProject`, `parcel`, `parcelOwner`, `component`, `locality`, `confidentialGrievance`; `member`, `provisionTenant`.
- Produces: la fila `SpatialGateApiTest` de §5 de la especificación.

- [ ] **Paso 1: La prueba**

Crear `src/test/kotlin/sgspe/territory/SpatialGateApiTest.kt`:

```kotlin
package sgspe.territory

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import java.util.UUID

// la compuerta por los endpoints (especificación de F6 §5, gis.md §9 «Compuerta de permisos»): sin READ sobre el predio, 403;
// el titular ilegible, no disponible y nunca vacío como si no hubiera; los componentes de un proyecto no asignado no aparecen
// (land_parcel es un maestro del tenant: lo que se acota es el componente, decisión 3); las QCS confidenciales no cuentan
// para quien no las ve; otro tenant no ve nada; el ADMIN ve todo
class SpatialGateApiTest : F6QueryApiTest() {
    private val y = -110_000.0

    private class Fixture(
        val a: UUID,
        val b: UUID,
        val parcel: UUID,
        val inA: UUID,
        val place: UUID
    )

    private fun fixture(): Fixture =
        runBlocking {
            val a = utmProject()
            val b = utmProject()
            val parcel = parcel(uniqueName("PR-"), "PROPIEDAD", rect(0.0, y, 1000.0, y + 1000))
            parcelOwner(parcel, "Persona Ejemplo")
            val inA = component(a, uniqueName("PLT-"), "PLATAFORMA", rect(400.0, y + 400, 600.0, y + 600))
            component(b, uniqueName("PLT-"), "PLATAFORMA", rect(400.0, y + 400, 600.0, y + 600))
            Fixture(a, b, parcel, inA, locality("Caserío Ejemplo", at(0.0, y)))
        }

    private fun parcelsPath(project: UUID) = "/api/sgspe/territory/projects/$project/component-parcels"

    @Test
    fun `sin READ sobre land_parcel la consulta de predios es 403`() {
        val f = fixture()
        val agent = member("FIELD_AGENT", f.a.toString())
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", parcelsPath(f.a), null, agent.token).status)
    }

    @Test
    fun `el titular ilegible sale no disponible con los cruces y sin titulares`() {
        val f = fixture()
        val analyst = member("ANALYST", f.a.toString())
        val body = get(parcelsPath(f.a), analyst.token)
        assertFalse(body["available"].asBoolean())
        assertTrue("land_right.holder_person" in body["missingFields"].values().map { it.asString() })
        val row = body["rows"].values().single { it["parcel"]["id"].asString() == f.parcel.toString() }
        assertEquals(f.inA.toString(), row["component"]["id"].asString())
        assertTrue(absent(row["holders"]))
    }

    @Test
    fun `los componentes de un proyecto no asignado no aparecen`() {
        val f = fixture()
        val analyst = member("ANALYST", f.a.toString())
        assertEquals("PROJECT_NOT_FOUND", failing(parcelsPath(f.b), HttpStatus.NOT_FOUND, analyst.token))
        grievance(f.a.toString(), attributes = mapOf("locality" to f.place.toString()))
        grievance(f.b.toString(), attributes = mapOf("locality" to f.place.toString()))
        val cells = get("/api/sgspe/territory/concentration?of=grievance&periodStart=2026-10-01&periodEnd=2026-10-31", analyst.token)["cells"].values()
        assertEquals(1, cells.single { it["id"].asString() == f.place.toString() }["count"].asInt())
    }

    @Test
    fun `una QCS confidencial no cuenta para quien no la ve y si para quien tiene READ_CONFIDENTIAL`() {
        val f = fixture()
        grievance(f.a.toString(), attributes = mapOf("locality" to f.place.toString()))
        runBlocking { confidentialGrievance(f.a, f.place) }
        val query = "/api/sgspe/territory/concentration?of=grievance&project=${f.a}&periodStart=2026-10-01&periodEnd=2026-10-31"

        fun count(token: String): Int = get(query, token)["cells"].values().single { it["id"].asString() == f.place.toString() }["count"].asInt()
        assertEquals(1, count(member("ANALYST", f.a.toString()).token))
        assertEquals(2, count(member("GRIEVANCE_MANAGER", f.a.toString()).token))
        assertEquals(2, count(token))
    }

    @Test
    fun `otro tenant no ve el proyecto y el ADMIN ve los cruces con sus titulares`() {
        val f = fixture()
        val stranger = provisionTenant()
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", parcelsPath(f.a), null, stranger.token).status)
        val body = get(parcelsPath(f.a))
        assertTrue(body["available"].asBoolean())
        val row = body["rows"].values().single { it["parcel"]["id"].asString() == f.parcel.toString() }
        assertEquals(listOf("person"), row["holders"].values().map { it["object"].asString() })
    }
}
```

`confidentialGrievance` (6.1) la recibe el admin el 2026-10-05 como las de `attentionAttributes`; si 6.1 le puso otra fecha,
el periodo de la consulta se ajusta a esa fecha y se anota. `provisionTenant` (de `SgspeApiTest`) crea un tenant sin el
modelo: su `project` no existe y la lectura es `404`.

- [ ] **Paso 2: Compilar y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat compileTestKotlin
git add src/test/kotlin/sgspe/territory/SpatialGateApiTest.kt
git commit -F - <<'MSG'
test(territory): los casos de la compuerta por los endpoints de consulta

Sin READ sobre el predio, 403; el titular ilegible, no disponible; los componentes de un proyecto no
asignado y las QCS confidenciales ajenas no cuentan; otro tenant no ve nada y el ADMIN ve todo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `SpatialGateApiTest`.

- [ ] **Paso 3: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.territory.SpatialGateApiTest'`.
Expected: PASS, 5 pruebas. Si `confidentialGrievance` deja como responsable o receptor a una de las cuentas de la prueba,
`ConfidentialAttentionScope` se la muestra (es su responsable): la cuenta ANALYST de la prueba es nueva y no lo es.

---

### Tarea 12: KPI-TER puros, la evaluación común de un indicador y `periodGiven`

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/TerritoryKpis.kt`
- Create: `src/main/kotlin/sgspe/dashboard/KpiEvaluation.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/Indicators.kt` (`UnavailableReason`), `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`,
  `src/main/kotlin/sgspe/dashboard/Dashboards.kt` (usa `KpiEvaluation`)
- Test: `src/test/kotlin/sgspe/dashboard/TerritoryKpisTest.kt`

**Interfaces:**
- Consumes: `Indicator`, `Kpi`, `IndicatorResult`, `IndicatorValue`, `Breakdown`, `Numbers.counts`, `Kpis.ignoredFilters`,
  `DashboardData`, `DashboardFilters` (F2 a F5).
- Produces (Tarea 13):
  - `data class AreaRow(val id: String, val project: String?, val areaType: String?, val versionNumber: Int?, val areaHa:
    BigDecimal?)`, `data class MemberRow(val area: String, val memberType: String?, val condition: String?)`, `data class
    LandRow(val component: String, val componentType: String?, val land: String)`.
  - `object TerritoryKpis { surface(areas): IndicatorResult; members(areas, members): IndicatorResult;
    componentsOnLand(rows): IndicatorResult; suspend component(kpi, data): Map<String, Any?>; suspend unavailableRow(key,
    missing, data): Map<String, Any?>; NONE; COMPONENTS }`.
  - `object KpiEvaluation { suspend fun evaluate(kpi: Indicator, data: DashboardData): IndicatorValue; suspend fun
    reason(missing: List<String>, data: DashboardData): String }`.
  - `UnavailableReason.TERRITORY_REQUIRED`; `DashboardFilters.periodGiven: Boolean` (vino `periodStart` o `periodEnd`).

- [ ] **Paso 1: La prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/TerritoryKpisTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.util.LinkedMultiValueMap
import sgspe.common.ModelCatalog
import sgspe.common.Records
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// KPI-TER-02 a 04 puros y la evaluación de una fila de KPI-TER-01 (dashboards.md §4.8 y §8, especificación de F6 §3.2): la
// superficie suma solo lo medido y lo advierte; los miembros y los componentes se cuentan una vez; una fila sin permiso sale
// no disponible con su razón, nunca cero, y con los filtros que ignora
class TerritoryKpisTest {
    private val today = LocalDate.parse("2026-10-11")

    private fun filters(community: UUID? = null) =
        DashboardFilters(listOf(UUID.randomUUID()), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null, null, community, null, null, null, null)

    private fun data(
        readable: Set<String>,
        community: UUID? = null
    ) = DashboardData(mock(Records::class.java), filters(community), { it in readable }, today)

    private fun rows(
        result: IndicatorResult,
        by: String
    ): List<Map<String, Any?>> = result.breakdowns.single { it.by == by }.rows

    @Test
    fun `KPI-TER-02 suma la superficie medida de las areas aprobadas y advierte las que no la tienen`() {
        val result =
            TerritoryKpis.surface(
                listOf(
                    AreaRow("a1", "p", "AIDS", 1, BigDecimal("100.00")),
                    AreaRow("a2", "p", "AIIS", 2, BigDecimal("50.50")),
                    AreaRow("a3", "p", "AID AMBIENTAL", 1, null)
                )
            )
        assertEquals(BigDecimal("150.50"), result.value)
        assertEquals(listOf("a1", "a2", "a3"), rows(result, "area").map { it["key"] })
        assertEquals(1, result.warnings.size)
        assertEquals(BigDecimal("0.00"), TerritoryKpis.surface(emptyList()).value)
    }

    @Test
    fun `KPI-TER-03 cuenta los miembros de las areas aprobadas por tipo de area, de miembro y condicion`() {
        val areas = listOf(AreaRow("a1", "p", "AIDS", 1, null), AreaRow("a2", "p", "AIIS", 1, null))
        val members =
            listOf(
                MemberRow("a1", "locality", "INCLUIDO"),
                MemberRow("a1", "community", "PROVISIONAL"),
                MemberRow("a2", "locality", "INCLUIDO"),
                MemberRow("otra", "locality", "INCLUIDO")
            )
        val result = TerritoryKpis.members(areas, members)
        assertEquals(3, result.value)
        assertEquals(listOf(mapOf("key" to "AIDS", "value" to 2), mapOf("key" to "AIIS", "value" to 1)), rows(result, "areaType"))
        assertEquals(listOf(mapOf("key" to "locality", "value" to 2), mapOf("key" to "community", "value" to 1)), rows(result, "memberType"))
        assertEquals(listOf(mapOf("key" to "INCLUIDO", "value" to 2), mapOf("key" to "PROVISIONAL", "value" to 1)), rows(result, "memberCondition"))
    }

    @Test
    fun `KPI-TER-04 cuenta cada componente una vez y lo desglosa por tierra y por tipo`() {
        val result =
            TerritoryKpis.componentsOnLand(
                listOf(
                    LandRow("c1", "PLATAFORMA", "PROPIEDAD"),
                    LandRow("c1", "PLATAFORMA", "POSESION"),
                    LandRow("c1", "PLATAFORMA", "PROPIEDAD"),
                    LandRow("c2", "ACCESO", "TERRITORIO COMUNAL")
                )
            )
        assertEquals(2, result.value)
        assertEquals(
            listOf(mapOf("key" to "POSESION", "value" to 1), mapOf("key" to "PROPIEDAD", "value" to 1), mapOf("key" to "TERRITORIO COMUNAL", "value" to 1)),
            rows(result, "tenureType")
        )
        assertEquals(listOf(mapOf("key" to "ACCESO", "value" to 1), mapOf("key" to "PLATAFORMA", "value" to 1)), rows(result, "componentType"))
    }

    @Test
    fun `una fila de KPI-TER-01 sin permiso sale no disponible con su razon y los filtros que ignora`() {
        val kpi =
            Kpi("KPI-X", "Prueba", null, "cosas", listOf("stakeholder.stance"), ignores = setOf("community")) {
                IndicatorResult(7)
            }
        val community = UUID.randomUUID()
        val hidden = runBlocking { TerritoryKpis.component(kpi, data(setOf("stakeholder"), community)) }
        assertEquals("KPI-X", hidden["key"])
        assertEquals(false, hidden["available"])
        assertEquals(UnavailableReason.FIELD_NOT_READABLE, hidden["unavailableReason"])
        assertEquals(listOf("stakeholder.stance"), hidden["missingFields"])
        assertEquals(listOf("community"), hidden["ignoredFilters"])
        val objectless = runBlocking { TerritoryKpis.component(kpi, data(emptySet())) }
        assertEquals(UnavailableReason.OBJECT_NOT_READABLE, objectless["unavailableReason"])
        val shown = runBlocking { TerritoryKpis.component(kpi, data(setOf("stakeholder", "stakeholder.stance"))) }
        assertEquals(true, shown["available"])
        assertEquals(7, shown["value"])
        assertFalse("ignoredFilters" in shown)
        val row = runBlocking { TerritoryKpis.unavailableRow("programs", listOf("social_program.name"), data(setOf("social_program"))) }
        assertEquals(mapOf("key" to "programs", "available" to false, "unavailableReason" to "FIELD_NOT_READABLE", "missingFields" to listOf("social_program.name")), row)
    }

    @Test
    fun `periodGiven dice si vino el periodo`() {
        val catalog = ModelCatalog.fromClasspath()

        fun parse(vararg pairs: Pair<String, String>): DashboardFilters {
            val params = LinkedMultiValueMap<String, String>()
            pairs.forEach { (k, v) -> params.add(k, v) }
            return runBlocking { DashboardFilters.parse(params, DashboardFilters.COMMON, catalog, today) { emptyList() }.resolve() }
        }
        assertFalse(parse().periodGiven)
        assertTrue(parse("periodStart" to "2026-10-01").periodGiven)
        assertTrue(parse("periodEnd" to "2026-10-31").periodGiven)
    }
}
```

- [ ] **Paso 2: Correrla para verla fallar**

Run: el comando de unitarias con `--tests 'sgspe.dashboard.TerritoryKpisTest'`.
Expected: FAIL de compilación («Unresolved reference: TerritoryKpis», «AreaRow», «periodGiven»).

- [ ] **Paso 3: La razón nueva y `periodGiven`**

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, reemplazar:

```kotlin
    const val SINGLE_PROJECT_REQUIRED = "SINGLE_PROJECT_REQUIRED"
}
```

por:

```kotlin
    const val SINGLE_PROJECT_REQUIRED = "SINGLE_PROJECT_REQUIRED"

    // F6: KPI-TER-01 sin locality ni community (dashboards.md §4.8)
    const val TERRITORY_REQUIRED = "TERRITORY_REQUIRED"
}
```

En `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`, reemplazar:

```kotlin
    val program: UUID? = null,
    val year: Int? = null
) {
```

por:

```kotlin
    val program: UUID? = null,
    val year: Int? = null,
    // F6: si vino periodStart o periodEnd. sin periodo pedido, KPI-TER-01 cuenta las interacciones de los últimos 90 días
    val periodGiven: Boolean = false
) {
```

y, en el mismo archivo, reemplazar:

```kotlin
                    uuid("program"),
                    year,
                    defaultProjects
                )
```

por:

```kotlin
                    uuid("program"),
                    year,
                    params.getFirst("periodStart") != null || params.getFirst("periodEnd") != null,
                    defaultProjects
                )
```

reemplazar:

```kotlin
        private val year: Int?,
        private val defaults: suspend () -> List<UUID>
    ) {
```

por:

```kotlin
        private val year: Int?,
        private val periodGiven: Boolean,
        private val defaults: suspend () -> List<UUID>
    ) {
```

y reemplazar:

```kotlin
                program,
                year
            )
    }
```

por:

```kotlin
                program,
                year,
                periodGiven
            )
    }
```

- [ ] **Paso 4: La evaluación común**

Crear `src/main/kotlin/sgspe/dashboard/KpiEvaluation.kt`:

```kotlin
package sgspe.dashboard

// la evaluación de un indicador con los permisos de quien mira (dashboards.md §8): primero la disponibilidad (los objetos y
// campos que lee, un solo proyecto si lo exige) y después el cálculo; los filtros pedidos que no aplica van en ignoredFilters
// (ruling P27: nunca un cero silencioso). la usan el tablero, el detalle de un indicador y cada fila de KPI-TER-01
object KpiEvaluation {
    suspend fun evaluate(
        kpi: Indicator,
        data: DashboardData
    ): IndicatorValue {
        val missing = kpi.requires.filterNot { data.readable(it) }
        val result =
            when {
                missing.isNotEmpty() -> IndicatorResult.unavailable(reason(missing, data), missing)
                kpi.singleProject && data.filters.projects.size != 1 ->
                    IndicatorResult.unavailable(UnavailableReason.SINGLE_PROJECT_REQUIRED, listOf("project"))
                else -> kpi.compute(data)
            }
        val ignored = Kpis.ignoredFilters(kpi, data.filters)
        return result.toValue(kpi.code, kpi.label, kpi.unit).copy(ignoredFilters = ignored.ifEmpty { null })
    }

    // OBJECT_NOT_READABLE si falta READ sobre alguno de los objetos; si no, FIELD_NOT_READABLE
    suspend fun reason(
        missing: List<String>,
        data: DashboardData
    ): String = if (missing.any { !data.readable(it.substringBefore('.')) }) UnavailableReason.OBJECT_NOT_READABLE else UnavailableReason.FIELD_NOT_READABLE
}
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
        val indicators = codes.map { mark(evaluate(Kpis.BY_CODE.getValue(it), data, readable), hidden) }
```

por:

```kotlin
        val indicators = codes.map { mark(KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(it), data), hidden) }
```

reemplazar:

```kotlin
            mark(evaluate(kpi, DashboardData(records, filters, readable::canRead, today), readable), if (Kpis.confidentialSensitive(code)) hidden() else false)
```

por:

```kotlin
            mark(KpiEvaluation.evaluate(kpi, DashboardData(records, filters, readable::canRead, today)), if (Kpis.confidentialSensitive(code)) hidden() else false)
```

y borrar entera la función privada `evaluate` (desde su comentario `// la disponibilidad primero (permisos, un solo proyecto) y
después el cálculo; los filtros que el KPI ignora van en` hasta su llave de cierre, antes de
`// si a quien mira se le ocultan QCS confidenciales`). `Readability.missing` y `objectMissing` dan lo mismo que
`DashboardData.readable` (es `readable::canRead`): el comportamiento del tablero no cambia y `DashboardsApiTest` lo sigue
cubriendo.

- [ ] **Paso 5: KPI-TER puros**

Crear `src/main/kotlin/sgspe/dashboard/TerritoryKpis.kt`:

```kotlin
package sgspe.dashboard

import java.math.BigDecimal
import java.math.RoundingMode

// una versión aprobada de un área de influencia (KPI-TER-02 y 03): su proyecto, su tipo, su versión y su superficie oficial
data class AreaRow(
    val id: String,
    val project: String?,
    val areaType: String?,
    val versionNumber: Int?,
    val areaHa: BigDecimal?
)

// un miembro de una de esas áreas: qué relación tiene llena (locality, community o administrative_unit) y su condición
data class MemberRow(
    val area: String,
    val memberType: String?,
    val condition: String?
)

// un componente vigente sobre tierra de terceros (KPI-TER-04): TERRITORIO COMUNAL o la tenencia del predio
data class LandRow(
    val component: String,
    val componentType: String?,
    val land: String
)

// KPI-TER-02 a 04 puros y las filas de KPI-TER-01 (dashboards.md §4.8; especificación de F6 §3.2). las cifras se calculan
// desde filas ya leídas como quien mira (DashboardData y, en KPI-TER-04, la compuerta de sgspe.territory)
object TerritoryKpis {
    const val NONE = "SIN DATO"
    const val COMPONENTS = "components"

    // la suma de calculated_area_ha de las áreas aprobadas, con dos decimales; las que no la tienen no se suman y se advierte
    fun surface(areas: List<AreaRow>): IndicatorResult {
        val measured = areas.mapNotNull { it.areaHa }
        val total = measured.fold(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP)
        val rows =
            areas
                .sortedWith(compareBy({ it.project }, { it.areaType }, { it.id }))
                .map { mapOf("key" to it.id, "project" to it.project, "areaType" to it.areaType, "versionNumber" to it.versionNumber, "value" to it.areaHa) }
        val unmeasured = areas.size - measured.size
        val warnings = if (unmeasured > 0) listOf("$unmeasured área(s) aprobada(s) sin superficie calculada: no se suman") else emptyList()
        return IndicatorResult(total, breakdowns = listOf(Breakdown("area", rows)), warnings = warnings)
    }

    // los miembros de las áreas aprobadas (los de otras áreas no cuentan), por tipo de área, de miembro y condición
    fun members(
        areas: List<AreaRow>,
        members: List<MemberRow>
    ): IndicatorResult {
        val types = areas.associate { it.id to it.areaType }
        val counted = members.filter { it.area in types }
        return IndicatorResult(
            counted.size,
            breakdowns =
                listOf(
                    Breakdown("areaType", Numbers.counts(counted.map { types[it.area] ?: NONE })),
                    Breakdown("memberType", Numbers.counts(counted.map { it.memberType ?: NONE })),
                    Breakdown("memberCondition", Numbers.counts(counted.map { it.condition ?: NONE }))
                )
        )
    }

    // los componentes distintos que cruzan tierra de terceros; un componente cuenta una vez por tierra y una vez por tipo
    fun componentsOnLand(rows: List<LandRow>): IndicatorResult {
        val components = rows.distinctBy { it.component }
        return IndicatorResult(
            components.size,
            breakdowns =
                listOf(
                    Breakdown("tenureType", Numbers.counts(rows.distinctBy { it.component to it.land }.map { it.land })),
                    Breakdown("componentType", Numbers.counts(components.map { it.componentType ?: NONE }))
                )
        )
    }

    // la fila de KPI-TER-01 de un KPI: su valor entero, como lo da el indicador por su cuenta, con su código en `key`
    suspend fun component(
        kpi: Indicator,
        data: DashboardData
    ): Map<String, Any?> = linkedMapOf<String, Any?>("key" to kpi.code) + KpiEvaluation.evaluate(kpi, data).json()

    // la fila de una cifra sin código (programs, lastActivity) que quien mira no puede leer
    suspend fun unavailableRow(
        key: String,
        missing: List<String>,
        data: DashboardData
    ): Map<String, Any?> = linkedMapOf("key" to key, "available" to false, "unavailableReason" to KpiEvaluation.reason(missing, data), "missingFields" to missing)
}
```

En la prueba, `Numbers.counts` ordena de más a menos y, a igual cuenta, por clave: por eso `POSESION` va antes que
`PROPIEDAD` y `ACCESO` antes que `PLATAFORMA`.

- [ ] **Paso 6: Correr las unitarias del paquete**

Run: el comando de unitarias con `--tests 'sgspe.dashboard.*'`.
Expected: PASS, con las 5 nuevas de `TerritoryKpisTest` y las que ya estaban (`DashboardFiltersTest`, `DashboardDataTest`,
`IndicatorsTest` y las de los KPI) en verde.

- [ ] **Paso 7: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/dashboard/TerritoryKpis.kt src/main/kotlin/sgspe/dashboard/KpiEvaluation.kt \
  src/main/kotlin/sgspe/dashboard/Indicators.kt src/main/kotlin/sgspe/dashboard/DashboardFilters.kt \
  src/main/kotlin/sgspe/dashboard/Dashboards.kt src/test/kotlin/sgspe/dashboard/TerritoryKpisTest.kt
git commit -F - <<'MSG'
feat(dashboard): KPI-TER-02 a 04 puros y la evaluación común de un indicador

TerritoryKpis suma la superficie aprobada, cuenta los miembros y los componentes sobre tierra de
terceros; KpiEvaluation saca de Dashboards la disponibilidad y los filtros ignorados para usarla en
las filas de KPI-TER-01. DashboardFilters dice si vino el periodo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 13: Las lecturas de territorio, KPI-TER-01 a 04 y el tablero `territory`

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/TerritorySummary.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, `src/main/kotlin/sgspe/dashboard/Kpis.kt`,
  `src/main/kotlin/sgspe/dashboard/Dashboards.kt`
- Test: `src/test/kotlin/sgspe/dashboard/TerritoryDashboardTest.kt`

**Interfaces:**
- Consumes: `TerritoryKpis`, `AreaRow`, `MemberRow`, `LandRow`, `KpiEvaluation`, `UnavailableReason.TERRITORY_REQUIRED`,
  `DashboardFilters.periodGiven` (Tarea 12); `ComponentLand.rows`, `ComponentLandRow`, `GateOutcome` (Tareas 1 y 7);
  `DashboardData.programs(program)`, `ProgramRow`, `Programs.PROGRAM`, `Programs.INITIATIVE`, `Programs.GROUP`,
  `EngagementWriteGuard.HELD_STATES`, `FollowUp.dateOf`, `Criteria.filled` (F2 a F5).
- Produces (Tarea 14 y plan 6.3): `DashboardData.with(filters)`, `inTerritory(locality, community)`, `approvedAreas()`,
  `areaMembers(areas)`, `landCrossings()`, `programsInTerritory()`, `lastHeld()`; `object TerritorySummary { CODE =
  "KPI-TER-01"; summary(data, indicators): IndicatorResult }`; `Kpis.TERRITORY = "territory"` con KPI-TER-01 a 04 en
  `Kpis.ALL`; `GET /api/sgspe/dashboards/territory` (exige `project`, `400 PROJECT_REQUIRED`).

- [ ] **Paso 1: La prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/TerritoryDashboardTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import sgspe.common.Records
import java.time.LocalDate
import java.util.UUID

// el tablero territory y KPI-TER-01 sin base (dashboards.md §4.8 y §5.9): KPI-TER-02 a 04 en el tablero; KPI-TER-01 se pide
// por la ficha y, sin localidad ni comunidad, no está disponible antes de leer nada
class TerritoryDashboardTest {
    @Test
    fun `el tablero territory reune KPI-TER-02 a 04 y los cuatro KPI-TER son suyos`() {
        assertEquals(listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), Kpis.DASHBOARDS.getValue(Kpis.TERRITORY))
        val territorial = Kpis.ALL.filter { it.code.startsWith("KPI-TER-") }
        assertEquals(listOf("KPI-TER-01", "KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), territorial.map { it.code })
        assertTrue(territorial.all { it.dashboard == Kpis.TERRITORY })
        assertTrue(Kpis.confidentialSensitive(TerritorySummary.CODE))
        assertEquals(emptyList<String>(), Kpis.BY_CODE.getValue(TerritorySummary.CODE).requires)
    }

    @Test
    fun `KPI-TER-01 sin localidad ni comunidad no esta disponible y no lee nada`() {
        val records = mock(Records::class.java)
        val filters =
            DashboardFilters(listOf(UUID.randomUUID()), LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), null, null, null, null, null, null, null)
        val value = runBlocking { KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(TerritorySummary.CODE), DashboardData(records, filters, { true }, LocalDate.parse("2026-10-11"))) }
        assertFalse(value.available)
        assertEquals(UnavailableReason.TERRITORY_REQUIRED, value.unavailableReason)
        assertEquals(listOf("locality", "community"), value.missingFields)
        verifyNoInteractions(records)
    }
}
```

- [ ] **Paso 2: Correrla para verla fallar**

Run: el comando de unitarias con `--tests 'sgspe.dashboard.TerritoryDashboardTest'`.
Expected: FAIL de compilación («Unresolved reference: TERRITORY», «TerritorySummary»).

- [ ] **Paso 3: Las lecturas de territorio en `DashboardData`**

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, reemplazar:

```kotlin
import sgspe.stakeholder.FollowUp
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordResponse
```

por:

```kotlin
import sgspe.stakeholder.FollowUp
import sgspe.territory.ComponentLand
import sgspe.territory.GateOutcome
import wasichai.core.common.PageRequest
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.platform.SqlIdentifier
```

reemplazar:

```kotlin
    private val maxRows: Int = MAX_ROWS,
    private val maxRelated: Int = MAX_RELATED
) {
```

por:

```kotlin
    private val maxRows: Int = MAX_ROWS,
    private val maxRelated: Int = MAX_RELATED,
    // F6: la consulta de KPI-TER-04 detrás de la compuerta de sgspe.territory; Dashboards la inyecta y las pruebas sin base la
    // dejan en null
    private val componentLand: ComponentLand? = null
) {
```

y reemplazar:

```kotlin
    companion object {
        const val CHUNK = 500
```

por:

```kotlin
    // ---- territorio (F6: KPI-TER) ----

    // otra lectura con otros filtros y los mismos permisos, topes y fuentes: las filas de KPI-TER-01 (el periodo de 90 días y
    // sin los filtros que la ficha no aplica)
    fun with(filters: DashboardFilters): DashboardData = DashboardData(records, filters, readable, today, maxRows, maxRelated, componentLand)

    // si un registro con esa localidad o comunidad cae en todos los filtros de territorio pedidos
    suspend fun inTerritory(
        locality: String?,
        community: String?
    ): Boolean = territories().all { t -> (locality != null && locality in t.localities) || (community != null && community in t.communities) }

    // las versiones aprobadas de las áreas de influencia de los proyectos (KPI-TER-02 y 03)
    suspend fun approvedAreas(): List<AreaRow> =
        cached("approvedAreas") {
            if (filters.projects.isEmpty()) return@cached emptyList<AreaRow>()
            read(AREA, criteria = listOf(Criteria.inState(setOf(APPROVED)), Criteria.anyOf("project", filters.projects))).map {
                val a = it.attributes
                AreaRow(it.id, Values.text(a["project"]), Values.text(a["area_type"]), Values.decimal(a["version_number"])?.toInt(), Values.decimal(a["calculated_area_ha"]))
            }
        }

    // los miembros de esas áreas, con la relación que tienen llena y su condición
    suspend fun areaMembers(areas: Collection<String>): List<MemberRow> =
        readIn(MEMBER, AREA, areas).map {
            val a = it.attributes
            MemberRow(Values.text(a[AREA]).orEmpty(), MEMBER_TYPES.firstOrNull { field -> Values.text(a[field]) != null }, Values.text(a["member_condition"]))
        }

    // los componentes vigentes de los proyectos sobre territorio comunal o predios (KPI-TER-04), por la compuerta de
    // sgspe.territory (ComponentLand): quien no puede leer algo de la consulta la recibe no disponible
    suspend fun landCrossings(): GateOutcome<LandRow> {
        val source = componentLand ?: error("DashboardData sin ComponentLand: Dashboards lo inyecta para KPI-TER-04")
        return when (val outcome = source.rows(filters.projects)) {
            is GateOutcome.Rows -> GateOutcome.Rows(outcome.rows.map { LandRow(it.component.toString(), it.componentType, it.land) })
            is GateOutcome.Unavailable -> outcome
        }
    }

    // los programas de los proyectos (en los estados que cuentan) con una iniciativa en el territorio pedido (por su localidad
    // o su comunidad) o un grupo de beneficiarios (por su localidad): la fila programs de KPI-TER-01 (dashboards.md §4.8)
    suspend fun programsInTerritory(): List<ProgramRow> {
        val programs = programs(program = false)
        if (programs.isEmpty()) return emptyList()
        val ids = programs.map { it.id }
        val initiatives = readIn(Programs.INITIATIVE, Programs.PROGRAM, ids).filter { inTerritory(Values.text(it.attributes["locality"]), Values.text(it.attributes["community"])) }
        val groups = readIn(Programs.GROUP, Programs.PROGRAM, ids).filter { inTerritory(Values.text(it.attributes["locality"]), null) }
        val chosen = (initiatives + groups).mapNotNull { Values.text(it.attributes[Programs.PROGRAM]) }.toSet()
        return programs.filter { it.id in chosen }
    }

    // el held_at más reciente de las interacciones realizadas de los proyectos en el territorio pedido, de cualquier fecha: una
    // sola fila, ordenada en la base (la fila lastActivity de KPI-TER-01)
    suspend fun lastHeld(): LocalDate? {
        if (filters.projects.isEmpty()) return null
        val criteria =
            listOf(
                Criteria.anyOf("project", filters.projects),
                Criteria.inState(EngagementWriteGuard.HELD_STATES),
                Criteria.filled("held_at"),
                territoryCriterion(territories())
            )
        val page = records.page("engagement", RecordQuery(page = PageRequest.of(0, 1), sort = "held_at", descending = true, criteria = criteria, count = false))
        return page.content.firstOrNull()?.let { FollowUp.dateOf(it.attributes["held_at"]) }
    }

    // el registro nombra, en cada filtro de territorio, una de sus localidades o de sus comunidades; sin filtros, todos. un
    // campo que quien mira no lee no filtra, y sin ninguno no pasa nada (como los demás criterios)
    private fun territoryCriterion(territories: List<Territory>): RecordCriterion =
        RecordCriterion { definition, bind ->
            fun column(field: String): String? = definition.fields.firstOrNull { it.name == field }?.let { SqlIdentifier.quote(it.columnName) }
            val locality = column("locality")
            val community = column("community")
            if (territories.isEmpty()) {
                "TRUE"
            } else {
                territories.joinToString(" AND ") { t ->
                    val terms =
                        listOfNotNull(
                            locality?.takeIf { t.localities.isNotEmpty() }?.let { "$it = ANY(${bind(t.localities.map(UUID::fromString).toTypedArray())})" },
                            community?.takeIf { t.communities.isNotEmpty() }?.let { "$it = ANY(${bind(t.communities.map(UUID::fromString).toTypedArray())})" }
                        )
                    if (terms.isEmpty()) "FALSE" else "(${terms.joinToString(" OR ")})"
                }
            }
        }

    companion object {
        const val CHUNK = 500
        private const val AREA = "area_of_influence"
        private const val MEMBER = "area_of_influence_member"
        private val MEMBER_TYPES = listOf("locality", "community", "administrative_unit")
```

`territories()`, `programs(program)`, `read`, `readIn`, `cached`, `APPROVED` y la clase `Territory` ya existen en
`DashboardData`; `records.page` es `Records.page`.

- [ ] **Paso 4: KPI-TER-01**

Crear `src/main/kotlin/sgspe/dashboard/TerritorySummary.kt`:

```kotlin
package sgspe.dashboard

// KPI-TER-01 (dashboards.md §4.8; especificación de F6 §3.2): no tiene un valor propio. reúne en el desglose `components` las
// cifras de la ficha contextual con el filtro de territorio elegido, una fila por cifra y cada una con su available: los KPI
// con su código (su IndicatorValue entero), programs y lastActivity con su clave. así quien no lee stance solo pierde
// KPI-STK-02, y quien no lee social_program solo pierde programs. sin locality ni community, TERRITORY_REQUIRED. la fila de
// KPI-ENG-01 cuenta los últimos 90 días si no se pidió un periodo; las demás no aplican la unidad, el área, la campaña, el
// ámbito ni el responsable (la ficha mira un lugar)
object TerritorySummary {
    const val CODE = "KPI-TER-01"
    const val PROGRAMS = "programs"
    const val LAST_ACTIVITY = "lastActivity"
    const val ENGAGEMENT_DAYS = 90L

    // los filtros comunes que KPI-TER-01 acepta y no aplica
    val IGNORES: Set<String> = setOf("administrativeUnit", "areaOfInfluence", "explorationCampaign", "scope", "owner")

    private val PROGRAM_REQUIRES =
        listOf(
            "social_program.project",
            "social_program.name",
            "social_initiative.social_program",
            "social_initiative.locality",
            "social_initiative.community",
            "beneficiary_group.social_program",
            "beneficiary_group.locality"
        )
    private val LAST_ACTIVITY_REQUIRES = listOf("engagement.project", "engagement.held_at", "engagement.locality", "engagement.community")

    suspend fun summary(
        data: DashboardData,
        indicators: Map<String, Indicator>
    ): IndicatorResult {
        val f = data.filters
        if (f.locality == null && f.community == null) {
            return IndicatorResult.unavailable(UnavailableReason.TERRITORY_REQUIRED, listOf("locality", "community"))
        }
        val base = data.with(f.copy(administrativeUnit = null, areaOfInfluence = null, explorationCampaign = null, scope = null, owner = null))
        val recent = if (f.periodGiven) base else base.with(base.filters.copy(periodStart = data.today.minusDays(ENGAGEMENT_DAYS - 1), periodEnd = data.today))
        val rows =
            listOf(
                TerritoryKpis.component(indicators.getValue("KPI-STK-02"), base),
                TerritoryKpis.component(indicators.getValue("KPI-ENG-01"), recent),
                TerritoryKpis.component(indicators.getValue("KPI-COM-02"), base),
                TerritoryKpis.component(indicators.getValue("KPI-COM-03"), base),
                TerritoryKpis.component(indicators.getValue("KPI-GRV-02"), base),
                TerritoryKpis.component(indicators.getValue("KPI-RSK-01"), base),
                programs(base),
                TerritoryKpis.component(indicators.getValue("KPI-INV-03"), base),
                lastActivity(base)
            )
        return IndicatorResult(null, breakdowns = listOf(Breakdown(TerritoryKpis.COMPONENTS, rows)))
    }

    private suspend fun programs(data: DashboardData): Map<String, Any?> {
        val missing = PROGRAM_REQUIRES.filterNot { data.readable(it) }
        if (missing.isNotEmpty()) return TerritoryKpis.unavailableRow(PROGRAMS, missing, data)
        val programs = data.programsInTerritory()
        return linkedMapOf(
            "key" to PROGRAMS,
            "available" to true,
            "value" to programs.size,
            "breakdowns" to listOf(Breakdown("program", programs.map { mapOf("key" to it.id, "label" to it.name) }))
        )
    }

    private suspend fun lastActivity(data: DashboardData): Map<String, Any?> {
        val missing = LAST_ACTIVITY_REQUIRES.filterNot { data.readable(it) }
        if (missing.isNotEmpty()) return TerritoryKpis.unavailableRow(LAST_ACTIVITY, missing, data)
        return linkedMapOf("key" to LAST_ACTIVITY, "available" to true, "value" to data.lastHeld()?.toString())
    }
}
```

- [ ] **Paso 5: Los KPI y el tablero en `Kpis`**

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
import sgspe.risk.RiskLevels
```

por:

```kotlin
import sgspe.risk.RiskLevels
import sgspe.territory.GateOutcome
```

reemplazar:

```kotlin
    const val INVESTMENT = "investment"
```

por:

```kotlin
    const val INVESTMENT = "investment"
    const val TERRITORY = "territory"
```

reemplazar:

```kotlin
    fun confidentialSensitive(code: String): Boolean = code.startsWith("KPI-GRV-") || code == "KPI-ENG-05"
```

por:

```kotlin
    fun confidentialSensitive(code: String): Boolean = code.startsWith("KPI-GRV-") || code == "KPI-ENG-05" || code == TerritorySummary.CODE
```

reemplazar:

```kotlin
            INVESTMENT to listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04")
        )
```

por:

```kotlin
            INVESTMENT to listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04"),
            // dashboards.md §5.9: las estadísticas del tablero territorial; KPI-TER-01 lo agrega Dashboards con locality o
            // community (es la ficha)
            TERRITORY to listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04")
        )

    // F6: KPI-TER-02 a 04 se filtran solo por proyecto (dashboards.md §4.8); los demás filtros comunes los declaran ignorados
    private val TERRITORY_IGNORES: Set<String> =
        setOf("administrativeUnit", "locality", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner")
    private val AREA = listOf("area_of_influence.project", "area_of_influence.area_type", "area_of_influence.version_number")
    private val LAND =
        listOf(
            "project_component.project",
            "project_component.component_type",
            "project_component.superseded",
            "project_component.location",
            "project_component.trace",
            "project_component.footprint",
            "community.territory",
            "land_parcel.tenure_type",
            "land_parcel.boundary"
        )
```

y reemplazar el cierre de `ALL` (el final de KPI-INV-04):

```kotlin
            ) { data ->
                val programs = data.programs(program = true)
                InvestmentKpis.beneficiaries(programs, data.groupsOf(programs.map { it.id }), data.investmentContributions(), data.investmentCommitments())
            }
        )

    val BY_CODE: Map<String, Indicator> = ALL.associateBy { it.code }
```

por:

```kotlin
            ) { data ->
                val programs = data.programs(program = true)
                InvestmentKpis.beneficiaries(programs, data.groupsOf(programs.map { it.id }), data.investmentContributions(), data.investmentCommitments())
            },
            // F6: el territorio (dashboards.md §4.8). KPI-TER-01 no lee nada por sí mismo: cada fila comprueba lo suyo
            Kpi(TerritorySummary.CODE, "Resumen de una localidad o comunidad", TERRITORY, "cifras", emptyList(), ignores = TerritorySummary.IGNORES) {
                TerritorySummary.summary(it, BY_CODE)
            },
            Kpi("KPI-TER-02", "Superficie del área de influencia", TERRITORY, "ha", AREA + "area_of_influence.calculated_area_ha", ignores = TERRITORY_IGNORES) {
                TerritoryKpis.surface(it.approvedAreas())
            },
            Kpi(
                "KPI-TER-03",
                "Miembros por tipo de área",
                TERRITORY,
                "miembros",
                AREA +
                    listOf(
                        "area_of_influence_member.area_of_influence",
                        "area_of_influence_member.locality",
                        "area_of_influence_member.community",
                        "area_of_influence_member.administrative_unit",
                        "area_of_influence_member.member_condition"
                    ),
                ignores = TERRITORY_IGNORES
            ) { data ->
                val areas = data.approvedAreas()
                TerritoryKpis.members(areas, data.areaMembers(areas.map { it.id }))
            },
            Kpi("KPI-TER-04", "Componentes sobre tierras comunales o privadas", TERRITORY, "componentes", LAND, ignores = TERRITORY_IGNORES) { data ->
                when (val outcome = data.landCrossings()) {
                    is GateOutcome.Rows -> TerritoryKpis.componentsOnLand(outcome.rows)
                    is GateOutcome.Unavailable -> IndicatorResult.unavailable(UnavailableReason.FIELD_NOT_READABLE, outcome.missingFields)
                }
            }
        )

    val BY_CODE: Map<String, Indicator> = ALL.associateBy { it.code }
```

`TERRITORY_IGNORES`, `AREA` y `LAND` van antes de `ALL` (el bloque de `DASHBOARDS` está antes), así están inicializados cuando
se arma la lista.

- [ ] **Paso 6: El tablero en `Dashboards`**

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, agregar `import sgspe.territory.ComponentLand` (después de
`import sgspe.common.toCodedException`), y reemplazar:

```kotlin
    private val permissions: Permissions
) {
```

por:

```kotlin
    private val permissions: Permissions,
    private val componentLand: ComponentLand
) {
```

reemplazar (lo que dejó la Tarea 12):

```kotlin
        val today = today()
        val filters = filters(params, DashboardFilters.COMMON + Kpis.extraFilters(codes), today)
        val readable = Readability(metadata)
        val data = DashboardData(records, filters, readable::canRead, today)
        val hidden = hidden()
        val indicators = codes.map { mark(KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(it), data), hidden) }
```

por:

```kotlin
        // el tablero territorial exige el proyecto (especificación de F6 §3.2): sus cifras son de un proyecto, no del tenant
        if (name == Kpis.TERRITORY && params["project"].isNullOrEmpty()) {
            throw Finding("PROJECT_REQUIRED", "El tablero territorial exige al menos un project", "project", status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        val today = today()
        val filters = filters(params, DashboardFilters.COMMON + Kpis.extraFilters(codes), today)
        val readable = Readability(metadata)
        val data = DashboardData(records, filters, readable::canRead, today, componentLand = componentLand)
        val hidden = hidden()
        // con locality o community, la ficha (KPI-TER-01) va primero
        val shown = if (name == Kpis.TERRITORY && (filters.locality != null || filters.community != null)) listOf(TerritorySummary.CODE) + codes else codes
        val indicators = shown.map { mark(KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(it), data), hidden) }
```

y reemplazar:

```kotlin
            mark(KpiEvaluation.evaluate(kpi, DashboardData(records, filters, readable::canRead, today)), if (Kpis.confidentialSensitive(code)) hidden() else false)
```

por:

```kotlin
            mark(
                KpiEvaluation.evaluate(kpi, DashboardData(records, filters, readable::canRead, today, componentLand = componentLand)),
                if (Kpis.confidentialSensitive(code)) hidden() else false
            )
```

`confidentialHidden` sale en KPI-TER-01 (que lee QCS en su fila KPI-GRV-02) cuando quien mira no ve las confidenciales; la
respuesta del tablero `territory` no lleva la marca de tablero (`confidentialHidden` de `DashboardResponse` sigue solo en
`attention`).

- [ ] **Paso 7: Correr las unitarias del paquete**

Run: el comando de unitarias con `--tests 'sgspe.dashboard.*'`.
Expected: PASS, con las 2 de `TerritoryDashboardTest` y las anteriores. Si alguna prueba de otro paquete arma `Dashboards`
a mano (grep `Dashboards(` en `src/test`), se le agrega el `ComponentLand` (un `mock(ComponentLand::class.java)`).

- [ ] **Paso 8: Build y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
git add src/main/kotlin/sgspe/dashboard/TerritorySummary.kt src/main/kotlin/sgspe/dashboard/DashboardData.kt \
  src/main/kotlin/sgspe/dashboard/Kpis.kt src/main/kotlin/sgspe/dashboard/Dashboards.kt \
  src/test/kotlin/sgspe/dashboard/TerritoryDashboardTest.kt
git commit -F - <<'MSG'
feat(dashboard): KPI-TER-01 a 04 y el tablero territory

KPI-TER-01 reúne las cifras de la ficha por fila, cada una con su disponibilidad; KPI-TER-02 y 03
leen las áreas aprobadas y sus miembros, y KPI-TER-04 usa la consulta de componentes sobre tierra de
terceros de sgspe.territory. El tablero exige el proyecto y pone la ficha primero con un territorio.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 14: El tablero territorial y la ficha por la API (`TerritoryDashboardApiTest`)

**Files:**
- Test: `src/test/kotlin/sgspe/dashboard/TerritoryDashboardApiTest.kt`

**Interfaces:**
- Consumes: el tablero `territory` y KPI-TER-01 a 04 (Tarea 13); `F6QueryApiTest` (Tarea 2); de las bases anteriores,
  `utmProject`, `locality`, `communityWithTerritory`, `parcel`, `component`, `stakeholder`, `engagement`, `participant`,
  `move`, `commitment`, `grievance`, `member`, `userWithRole`.
- Produces: la fila `TerritoryDashboardApiTest` de §5 de la especificación.

- [ ] **Paso 1: La prueba**

Crear `src/test/kotlin/sgspe/dashboard/TerritoryDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F6QueryApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID

// el tablero territorial y la ficha contextual (dashboards.md §4.8 y §5.9; especificación de F6 §3.2 y §5): KPI-TER-01 con
// datos de cada tipo en una localidad y ceros en otra; la postura no disponible para quien no la lee; un proyecto no asignado
// da ceros; KPI-TER-04 con una plataforma sobre un predio PROPIEDAD y un acceso sobre territorio comunal; TERRITORY_REQUIRED
class TerritoryDashboardApiTest : F6QueryApiTest() {
    private val october = "periodStart=2026-10-01&periodEnd=2026-10-31"

    private fun summary(
        query: String,
        token: String = this.token
    ): Map<String, JsonNode> =
        get("/api/sgspe/dashboards/indicators/KPI-TER-01?$query", token)["breakdowns"]
            .values()
            .single { it["by"].asString() == "components" }["rows"]
            .values()
            .associateBy { it["key"].asString() }

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    private class Place(
        val project: UUID,
        val place: UUID,
        val empty: UUID
    )

    // una localidad con un grupo de interés, una interacción realizada el 2026-10-06, un compromiso y una queja; y otra vacía
    private fun place(): Place {
        val (project, place, empty) =
            runBlocking { Triple(utmProject(), locality("Caserío Ejemplo", at(0.0, -120_000.0)), locality("Caserío Ejemplo Vacío", at(500.0, -120_000.0))) }
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project.toString())
        stakeholder(project.toString(), mapOf("locality" to place.toString()))
        engagement(project.toString(), "REUNION", officer.token, mapOf("held_at" to "2026-10-06T15:00:00Z", "locality" to place.toString())).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        commitment(project.toString(), mapOf("locality" to place.toString()))
        grievance(project.toString(), attributes = mapOf("locality" to place.toString()))
        return Place(project, place, empty)
    }

    @Test
    fun `la ficha de una localidad con datos y la de otra sin datos`() {
        val p = place()
        val full = summary("project=${p.project}&locality=${p.place}&$october")
        assertEquals(
            listOf("KPI-STK-02", "KPI-ENG-01", "KPI-COM-02", "KPI-COM-03", "KPI-GRV-02", "KPI-RSK-01", "programs", "KPI-INV-03", "lastActivity"),
            full.keys.toList()
        )
        assertTrue(full.values.all { it["available"].asBoolean() }, "$full")
        assertEquals(1, full.getValue("KPI-STK-02")["value"].asInt())
        assertEquals(1, full.getValue("KPI-ENG-01")["value"].asInt())
        assertEquals(1, full.getValue("KPI-COM-02")["value"].asInt())
        assertEquals("2026-10-06", full.getValue("lastActivity")["value"].asString())
        val none = summary("project=${p.project}&locality=${p.empty}&$october")
        assertEquals(0, none.getValue("KPI-STK-02")["value"].asInt())
        assertEquals(0, none.getValue("KPI-ENG-01")["value"].asInt())
        assertEquals(0, none.getValue("KPI-COM-02")["value"].asInt())
        assertEquals(0, none.getValue("programs")["value"].asInt())
        assertTrue(absent(none.getValue("lastActivity")["value"]))
    }

    @Test
    fun `quien no lee la postura solo pierde KPI-STK-02 y un proyecto no asignado da ceros`() {
        val p = place()
        val analyst = member("ANALYST", p.project.toString())
        val rows = summary("project=${p.project}&locality=${p.place}&$october", analyst.token)
        val stance = rows.getValue("KPI-STK-02")
        assertFalse(stance["available"].asBoolean())
        assertEquals("FIELD_NOT_READABLE", stance["unavailableReason"].asString())
        assertEquals(listOf("stakeholder.stance"), stance["missingFields"].values().map { it.asString() })
        assertEquals(1, rows.getValue("KPI-ENG-01")["value"].asInt())
        val outsider = userWithRole("ANALYST")
        val foreign = summary("project=${p.project}&locality=${p.place}&$october", outsider.token)
        assertEquals(0, foreign.getValue("KPI-ENG-01")["value"].asInt())
        assertEquals(0, foreign.getValue("KPI-COM-02")["value"].asInt())
    }

    @Test
    fun `con una comunidad la postura dice que ignora el filtro y sin territorio es TERRITORY_REQUIRED`() {
        val project = runBlocking { utmProject() }
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(0.0, -121_000.0, 500.0, -120_500.0, multi = true)) }
        val rows = summary("project=$project&community=$community&$october")
        assertEquals(listOf("community"), rows.getValue("KPI-STK-02")["ignoredFilters"].values().map { it.asString() })
        val bare = get("/api/sgspe/dashboards/indicators/KPI-TER-01?project=$project")
        assertFalse(bare["available"].asBoolean())
        assertEquals("TERRITORY_REQUIRED", bare["unavailableReason"].asString())
    }

    @Test
    fun `el tablero exige el proyecto, trae KPI-TER-02 a 04 y con territorio la ficha primero`() {
        val y = -130_000.0
        val project = runBlocking { utmProject() }
        val area = area(project, boundary = rect(0.0, y, 1000.0, y + 1000, multi = true))
        val place = localityWith("Caserío Ejemplo", location = at(100.0, y + 100))
        val community = runBlocking { communityWithTerritory("Comunidad Ejemplo", rect(3000.0, y, 4000.0, y + 1000, multi = true)) }
        areaMember(area, "locality", place)
        areaMember(area, "community", community)
        approveArea(area, project)
        runBlocking {
            parcel(uniqueName("PR-"), "PROPIEDAD", rect(1500.0, y, 2500.0, y + 1000))
            component(project, uniqueName("PLT-"), "PLATAFORMA", rect(1900.0, y + 400, 2100.0, y + 600))
            component(project, uniqueName("ACC-"), "ACCESO", path(3200.0 to y + 500, 3800.0 to y + 500))
        }
        assertEquals("PROJECT_REQUIRED", failing("/api/sgspe/dashboards/territory", HttpStatus.BAD_REQUEST))
        val board = get("/api/sgspe/dashboards/territory?project=$project")
        assertEquals(listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), board["indicators"].values().map { it["code"].asString() })
        val surface = indicator(board, "KPI-TER-02")
        assertEquals(record("area_of_influence", area.toString())["attributes"]["calculated_area_ha"].asDouble(), surface["value"].asDouble())
        val members = indicator(board, "KPI-TER-03")
        assertEquals(2, members["value"].asInt())
        val land = indicator(board, "KPI-TER-04")
        assertEquals(2, land["value"].asInt())
        val tenure = land["breakdowns"].values().single { it["by"].asString() == "tenureType" }["rows"].values().associate { it["key"].asString() to it["value"].asInt() }
        assertEquals(1, tenure["PROPIEDAD"])
        assertEquals(1, tenure["TERRITORIO COMUNAL"])
        val withPlace = get("/api/sgspe/dashboards/territory?project=$project&locality=$place")
        assertEquals(listOf("KPI-TER-01", "KPI-TER-02", "KPI-TER-03", "KPI-TER-04"), withPlace["indicators"].values().map { it["code"].asString() })
        assertEquals(listOf("locality"), indicator(withPlace, "KPI-TER-04")["ignoredFilters"].values().map { it.asString() })
    }
}
```

`commitment(project, attributes)` es el de `F3DomainApiTest` (en `draft`, cuenta como abierto); `grievance`, el de
`F4ApiTest`; `engagement`, `participant` y `move`, los de `DomainApiTest` y `F2ApiTest`. Los conteos de KPI-TER-04 son por
componente: la plataforma puede cruzar también predios que otras corridas dejaron en el mismo lugar y sigue contando una vez.

- [ ] **Paso 2: Compilar y commit**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat compileTestKotlin
git add src/test/kotlin/sgspe/dashboard/TerritoryDashboardApiTest.kt
git commit -F - <<'MSG'
test(dashboard): el tablero territorial y la ficha por la API

KPI-TER-01 con datos y sin ellos, sin la postura para quien no la lee, con ceros en un proyecto no
asignado y TERRITORY_REQUIRED sin territorio; el tablero exige el proyecto y KPI-TER-04 cuenta una
plataforma sobre un predio y un acceso sobre territorio comunal.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Integración pendiente: `TerritoryDashboardApiTest`.

- [ ] **Paso 3: Correr la integración (coordinador)**

Run: el comando de integración con `--tests 'sgspe.dashboard.TerritoryDashboardApiTest' --tests 'sgspe.dashboard.DashboardsApiTest'`.
Expected: PASS (4 pruebas nuevas; `DashboardsApiTest` sigue en verde tras sacar la evaluación a `KpiEvaluation`).

---

### Tarea 15: La medición de la compuerta con volumen (coordinador)

**Files:**
- Ninguno versionado salvo, si la medición lo pide, `src/main/kotlin/sgspe/territory/ProjectLandQueries.kt` y
  `src/main/kotlin/sgspe/territory/TerritoryAggregates.kt` con sus pruebas (Paso 6). Las cifras las anota la Tarea 16 en
  `docs/roadmap.md`.

**Interfaces:**
- Consumes: las Tareas 1 a 14 integradas en la rama; el túnel (puerto 5435) y el backend de la rama (8094).
- Produces: las cifras de §7 de la especificación (p95 de `component-parcels` y `concentration`, planes sin barrido
  secuencial de las tablas con geometría) y la decisión sobre el respaldo «prefiltro por proyecto» del riesgo 3.

**Solo el coordinador principal**: exige una base propia, el túnel y el backend. Crear la base `sgspe_perf` en el PostgreSQL
del VPS se hace **con permiso del usuario**; ningún subagente la ejecuta, salvo el Paso 6 (código), que se delega si la
medición lo pide.

- [ ] **Paso 1: La base propia (coordinador, con permiso del usuario)**

Con el túnel abierto, en `psql -h localhost -p 5435 -U sgspe postgres`:

```sql
CREATE DATABASE sgspe_perf OWNER sgspe;
\c sgspe_perf
CREATE EXTENSION IF NOT EXISTS postgis;
```

Arrancar el backend de la rama contra ella (`WASICHAI_DB_NAME=sgspe_perf`, el resto como en `docs/develop/README.md` §2, con
`logging.level.io.r2dbc.postgresql.QUERY=DEBUG`) y aplicar el modelo de F6 en el orden de `CLAUDE.md`: `apply.py` →
`apply_roles.py` → `apply.py --workflows`.

- [ ] **Paso 2: Las plantillas (coordinador)**

Por la API, como el admin del tenant de la base: «Proyecto Ejemplo A» con `working_crs` `EPSG 32717`; una persona
`analista-medicion@sgspe.test` (`ANALYST`) asignada a él; un predio `PROPIEDAD` de 450 m de lado en `SyntheticGeo.ORIGIN`
(`-78.50, -7.90`); una comunidad con un territorio de 900 m de lado; una localidad con su ubicación; una plataforma de 50 m
de lado con `POST /api/sgspe/project-components`; una queja con esa localidad, recibida el 2026-10-05. Sus ids son
`:tpl_project`, `:tpl_parcel`, `:tpl_community`, `:tpl_locality`, `:tpl_component` y `:tpl_grievance`.

- [ ] **Paso 3: El volumen de §7 (coordinador)**

En `psql` contra `sgspe_perf` (`wasichai` es el esquema de metadata y `data` el de datos):

```sql
\set org '<id de la organización>'
SELECT name, physical_table FROM wasichai.custom_objects
 WHERE organization_id = :'org'
   AND name IN ('project', 'project_assignment', 'land_parcel', 'community', 'locality', 'project_component', 'grievance');
-- una variable por tabla: \set proj …, \set pa …, \set lp …, \set cm …, \set lo …, \set pc … y \set gr …
-- 10 proyectos
CREATE TEMP TABLE perf_projects AS SELECT gen_random_uuid() AS id, g FROM generate_series(1, 10) g;
INSERT INTO data.:"proj" SELECT (jsonb_populate_record(p, jsonb_build_object('id', m.id, 'code', 'PERF-' || m.g,
  'name', 'Proyecto de medición ' || m.g))).* FROM data.:"proj" p, perf_projects m WHERE p.id = :'tpl_project';
-- 20 000 predios en una grilla de 200 × 100 celdas de unos 500 m (0.0045°)
INSERT INTO data.:"lp" SELECT (jsonb_populate_record(t, jsonb_build_object('id', gen_random_uuid(), 'code', 'PR-PERF-' || i || '-' || j,
  'boundary', ST_AsEWKT(ST_Translate(t.boundary, i * 0.0045, -j * 0.0045))))).*
  FROM data.:"lp" t, generate_series(0, 199) i, generate_series(0, 99) j WHERE t.id = :'tpl_parcel';
-- 2 000 comunidades en una grilla de 50 × 40 celdas de 1 km (0.009°)
INSERT INTO data.:"cm" SELECT (jsonb_populate_record(t, jsonb_build_object('id', gen_random_uuid(), 'name', 'Comunidad de medición ' || i || '-' || j,
  'territory', ST_AsEWKT(ST_Translate(t.territory, i * 0.009, -j * 0.009))))).*
  FROM data.:"cm" t, generate_series(0, 49) i, generate_series(0, 39) j WHERE t.id = :'tpl_community';
-- 2 000 localidades, una por comunidad
CREATE TEMP TABLE perf_localities AS SELECT gen_random_uuid() AS id, i, j, row_number() OVER () AS n FROM generate_series(0, 49) i, generate_series(0, 39) j;
INSERT INTO data.:"lo" SELECT (jsonb_populate_record(t, jsonb_build_object('id', x.id, 'name', 'Localidad de medición ' || x.n,
  'location', ST_AsEWKT(ST_Translate(t.location, x.i * 0.009, -x.j * 0.009))))).* FROM data.:"lo" t, perf_localities x WHERE t.id = :'tpl_locality';
-- 5 000 componentes, 500 por proyecto, sobre la grilla de predios
INSERT INTO data.:"pc" SELECT (jsonb_populate_record(t, jsonb_build_object('id', gen_random_uuid(), 'project', m.id,
  'code', 'PLT-PERF-' || k, 'footprint', ST_AsEWKT(ST_Translate(t.footprint, ((m.g * 500 + k) % 200) * 0.0045, -((m.g * 500 + k) / 200 % 100) * 0.0045))))).*
  FROM data.:"pc" t, perf_projects m, generate_series(0, 499) k WHERE t.id = :'tpl_component';
-- 50 000 quejas repartidas entre los 10 proyectos y las 2 000 localidades, en 2026
INSERT INTO data.:"gr" SELECT (jsonb_populate_record(t, jsonb_build_object('id', gen_random_uuid(), 'code', 'QJ-PERF-' || q,
  'project', (SELECT id FROM perf_projects WHERE g = 1 + q % 10), 'locality', (SELECT id FROM perf_localities WHERE n = 1 + q % 2000),
  'received_at', (TIMESTAMPTZ '2026-01-01' + make_interval(hours => q % 6500))::text))).*
  FROM data.:"gr" t, generate_series(1, 50000) q WHERE t.id = :'tpl_grievance';
ANALYZE data.:"proj"; ANALYZE data.:"lp"; ANALYZE data.:"cm"; ANALYZE data.:"lo"; ANALYZE data.:"pc"; ANALYZE data.:"gr";
```

Si un objeto tiene otro único por organización en `model.json` (un `code`), su columna va en `jsonb_build_object` con un valor
distinto por fila, como arriba. Después, por la API, el admin asigna `analista-medicion` también a `PERF-1`.

- [ ] **Paso 4: Las consultas y sus planes (coordinador)**

Como `analista-medicion` y como el admin, veinte veces cada una:

```bash
for i in $(seq 20); do
  curl -s -o /dev/null -w "%{time_total}\n" "localhost:8094/api/sgspe/territory/projects/<id de PERF-1>/component-parcels" -H "Authorization: Bearer $TOKEN"
done | sort -n | sed -n '19p'
for i in $(seq 20); do
  curl -s -o /dev/null -w "%{time_total}\n" "localhost:8094/api/sgspe/territory/concentration?of=grievance&periodStart=2026-01-01&periodEnd=2026-12-31" -H "Authorization: Bearer $TOKEN"
done | sort -n | sed -n '19p'
```

(la fila 19 de 20 ordenadas es el p95). Del log del backend se copia el SQL de cada consulta con sus valores y se corre en
`psql` con `EXPLAIN (ANALYZE, BUFFERS)`, tres veces, anotando la mediana.

**Criterio (§7 de la especificación).** `component-parcels` y `concentration` bajo 1 s en el p95, con las dos personas, y en
sus planes ningún `Seq Scan` sobre las tablas con geometría (`data.:"lp"`, `data.:"cm"`, `data.:"lo"`, `data.:"pc"`): los
cruces usan el índice GIST de wasichai. La tabla de quejas puede leerse por su índice de proyecto o por barrido: no tiene
geometría en la consulta, y lo que diga su plan se anota.

- [ ] **Paso 5: La decisión (coordinador)**

- Si las dos cumplen: nada cambia y el PR lo dice con las cifras.
- Si `component-parcels` hace `Seq Scan` sobre los predios o pasa de 1 s: se hace el Paso 6 (el respaldo del riesgo 3).
- Si `concentration` pasa de 1 s por la lectura de las quejas: no hay reescritura prevista en este plan; se anota y se decide
  con el usuario (un índice declarado sobre `received_at` iría en `model.json` `indexes`, nunca con SQL propio).

- [ ] **Paso 6: Solo si el Paso 5 lo pide: el prefiltro por proyecto (subagente)**

En `ProjectLandQueries.crossingSql`, los componentes del proyecto se materializan antes del cruce y el predio se prefiltra
con su columna desnuda, para que el planificador use el índice GIST del lado grande:

```kotlin
            return "WITH c AS MATERIALIZED (SELECT * FROM ${t.table(c)} WHERE ${t.column(c, "project")} = ${bind(frame.id)} " +
                "AND ${t.column(c, "superseded")} IS NOT TRUE) " +
                "SELECT c.${t.column(c, SpatialGate.ID)} AS component, c.${t.column(c, "code")} AS component_code, " +
                "c.${t.column(c, "component_type")}::text AS component_type, x.${t.column(land, SpatialGate.ID)} AS land, " +
                "x.${t.column(land, code)}::text AS land_code, $kindColumn AS land_kind, " +
                "CASE WHEN $trace IS NOT NULL THEN ${SpatialSql.lengthM(trace, target, srid)} END AS length_m, " +
                "CASE WHEN $footprint IS NOT NULL THEN ${SpatialSql.areaHa(footprint, target, srid)} END AS area_ha " +
                "FROM c JOIN ${t.table(land)} x ON $target && $shape AND ST_Intersects($shape, $target) " +
                "ORDER BY 2, 5, 1, 4 LIMIT ${bind(limit + 1)}"
```

Con una prueba unitaria nueva en `src/test/kotlin/sgspe/territory/ComponentLandSqlTest.kt` que exige
`WITH c AS MATERIALIZED (SELECT * FROM <project_component> WHERE "project" = ` al comienzo de
`ProjectLandQueries.crossingSql(...)` y `x."boundary" && COALESCE(` en el `JOIN`. Commit
`perf(territory): los componentes del proyecto se materializan antes del cruce con los predios`, y la medición se repite con
el cambio antes de anotarla. `SpatialLandQueriesApiTest` y `SpatialGateApiTest` se vuelven a correr (coordinador).

---

### Tarea 16: Documentos, verificación final, suite de integración, push y PR

**Files:**
- Modify: `docs/roadmap.md` (F6, «Estado»), `docs/api.md` (§4, solo si el plan 6.0 no dejó las formas de «Interfaces con el
  portal»), `CLAUDE.md` («Dónde está cada cosa»)

**Interfaces:**
- Consumes: todo lo anterior y las cifras de la Tarea 15.
- Produces: la rama `feat/fase-6-2a-consultas` en verde y el PR.

- [ ] **Paso 1: `api.md` y `dashboards.md` frente a este plan**

El plan 6.0 escribió los endpoints de F6 y los códigos nuevos. Comprobar:

```bash
cd ~/IdeaProjects/sgspe
for c in OUT_OF_RANGE INVALID_OF ZONE_NOT_FOUND PERIOD_NOT_APPLICABLE WORKING_CRS_REQUIRED PROJECT_REQUIRED TERRITORY_REQUIRED; do
  printf '%s %s\n' "$c" "$(grep -c "$c" docs/api.md docs/dashboards.md | paste -sd' ' -)"
done
grep -n "missingFields\|truncated" docs/api.md | grep -c "territory"
```

Cada código debe aparecer en `api.md` (`TERRITORY_REQUIRED` también en `dashboards.md` §4.8). Si falta alguno, o si las filas
de los 13 endpoints de `api.md` §4 no dicen la forma de sus filas, se agregan en este paso con las formas de «Interfaces con
el portal» (una oración por endpoint en su celda de respuesta) y se anota en el PR; nada se agrega dos veces.

- [ ] **Paso 2: El roadmap y `CLAUDE.md`**

En `docs/roadmap.md`, en la tabla de F6: si tiene una fila `| Estado |`, agregar al final de su celda; si no, agregar después
de la fila `| IDs abiertos | … |` la fila `| Estado | … |` con este texto y las cifras de la Tarea 15:
`` medición del plan 6.2a (2026-10-…): con 10 proyectos, 20 000 predios, 5 000 componentes, 2 000 comunidades y 50 000 QCS, component-parcels <a> ms en el p95 (<b> ms como ADMIN) y concentration <c> ms (<d> ms), <con o sin> barrido secuencial de las tablas con geometría; <«sin cambios» o «componentes materializados antes del cruce»> `` (si
no se pudo medir, «medición pendiente», y el cierre de F6 la sigue exigiendo).

En `CLAUDE.md`, en «Dónde está cada cosa», después de la fila que nombra `SpatialGate` (la puso el plan 6.0; si no está,
después de la fila que empieza con «| CRS, área oficial, áreas de influencia»), agregar las filas que falten:

```markdown
| Consultas espaciales: `SpatialGate` (la única puerta de SQL propio, con `RecordReadScopes`), `ZoneCriterion` y `TerritoryZones`, límites `sgspe.territory.queries.*` (`TerritoryQueryProperties`, `QueryParams`), medidas (`SpatialSql`), los 13 endpoints (`ZoneQueries`, `MemberSuggestions`, `ProjectLandQueries`, `ProjectProximityQueries`, `TerritoryAggregates`, `TerritoryCards`) y `ComponentLand` | `src/main/kotlin/sgspe/territory/` |
| KPI-TER y el tablero `territory` (`TerritoryKpis`, `TerritorySummary`, `KpiEvaluation`) | `src/main/kotlin/sgspe/dashboard/` |
| Base de las pruebas de integración del plan 6.2a (geometrías en metros desde el origen sintético, áreas y miembros) | `src/test/kotlin/sgspe/F6QueryApiTest.kt` |
```

- [ ] **Paso 3: Todo lo local**

```bash
cd ~/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
(cd model && python3 -m unittest 2>&1 | tail -1 && python3 apply.py --validate-only)
python3 tools/check_traceability.py && yarn format:check
git status --short
```

Esperado: `BUILD SUCCESSFUL` con todas las unitarias en verde (las de `main` más las de este plan: 6 de `SpatialGateTest`, 7
de `ZoneCriterionTest`, 6 de `QueryParamsTest`, 4 de `SpatialSqlTest`, 1 de `ComponentLandSqlTest`, 3 de `ProximitySqlTest`,
2 de `ConcentrationSqlTest`, 5 de `TerritoryKpisTest`, 2 de `TerritoryDashboardTest` y la regla nueva de `ArchitectureTest`);
el modelo `OK`; `check_traceability.py` en verde; prettier sin cambios.

- [ ] **Paso 4: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add docs/roadmap.md CLAUDE.md
# solo si el Paso 1 lo cambió: git add docs/api.md docs/dashboards.md
git commit -F - <<'MSG'
docs: cierre del plan 6.2a con las consultas espaciales y la medición

El roadmap anota la medición de la compuerta con volumen; CLAUDE.md dice dónde viven SpatialGate,
los filtros por zona, los 13 endpoints de consulta y KPI-TER.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

- [ ] **Paso 5: La suite de integración (CI del PR o coordinador)**

Las clases de integración corren en la CI del PR con Testcontainers. Si el coordinador las quiere antes, por el túnel y con
`sgspe_sdd_test` libre, en este orden (las de una línea juntas, en una sola JVM):

1. `SpatialGateScopeParityTest` y `SpatialGateApiTest`.
2. `SpatialZoneQueriesApiTest`, `SpatialSuggestionsApiTest`, `SpatialLandQueriesApiTest`, `SpatialProximityApiTest`,
   `SpatialAggregatesApiTest` y `TerritoryCardsApiTest`.
3. `TerritoryDashboardApiTest`, `DashboardsApiTest`, `InvestmentDashboardApiTest`, `RisksDashboardApiTest` y
   `AttentionDashboardApiTest` (la evaluación común no cambia sus cifras).
4. La suite entera (`./gradlew integrationTest --rerun`), de 30 minutos a más de una hora por el túnel.

Esperado: verde. Si `SpatialGateScopeParityTest` falla, no se mezcla la rama: se compara el SQL de la compuerta con el de
`RecordService` para ese objeto y se corrige `SpatialGate` (el contrato con 6.2b no cambia).

- [ ] **Paso 6: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin feat/fase-6-2a-consultas
gh pr create --base main --title "feat: fase 6, plan 6.2a: consultas espaciales, KPI-TER y tablero territorial" --body-file - <<'MSG'
## Qué trae

- `SpatialGate` (`sgspe.territory`): la única puerta de SQL propio que cruza tablas del tenant. Revisa READ y la
  legibilidad de lo que nombra la consulta (403 sin el objeto principal, «no disponible» sin uno secundario), arma cada
  tabla como subconsulta con la organización y los criterios de `RecordReadScopes` (la regla de `RecordService`: ninguno para
  ADMIN ni la plataforma) y ejecuta solo `SELECT` con valores enlazados. `ArchitectureTest` deja `DatabaseClient` a la
  compuerta y a los escritores.
- `ZoneCriterion` y `TerritoryZones`: los filtros por zona con la geometría enlazada como GeoJSON, en el SRID de cada campo.
- Los 13 endpoints de consulta de `api.md` §4 con sus límites configurables (`sgspe.territory.queries.*`, `400 OUT_OF_RANGE`).
- KPI-TER-01 a 04 y el tablero `territory` (exige `project`; con territorio, la ficha primero).

## Pruebas

- Unitarias: `SpatialGateTest`, `ZoneCriterionTest`, `QueryParamsTest`, `SpatialSqlTest`, los SQL de componentes, cercanía y
  concentración, `TerritoryKpisTest`, `TerritoryDashboardTest` y `ArchitectureTest`.
- Integración: `SpatialGateScopeParityTest`, `SpatialGateApiTest`, las seis clases de consultas y `TerritoryDashboardApiTest`.
- Medición con volumen: <cifras de la Tarea 15>.

## Para revisar

- La compuerta llama a `RecordReadScopes.criteria` en vez de copiar la regla de omisión (decisión 1 del plan).
- `land_parcel` es maestro del tenant: lo que se acota en los cruces es el componente (decisión 3).
- Las formas de las filas y `available`/`missingFields`/`truncated` (decisión 7) y KPI-TER-01 por filas (decisión 8).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

Con el PR en verde se mezcla según `push-pr-authorized` (memoria del proyecto) y se avisa al coordinador de 6.2b y 6.3.

---

## Dependencias de otros planes

| De | Qué | Dónde se usa | Si no está |
|---|---|---|---|
| 6.0 | ASM-122 y ASM-126; endpoints y códigos de `api.md` §4 y §3.3; `dashboards.md` §4.8 con `TERRITORY_REQUIRED` | Comentarios, Tarea 16 | La Tarea 16 los agrega con las formas de este plan |
| 6.1 | `F6ApiTest` con `SyntheticGeo` y las ayudas del contrato común | Tareas 2, 5 a 11 y 14 | No se empieza (Paso 1 de la Tarea 1) |
| 6.1 | `community.territory` y `environmental_feature` con sus campos de F6 | Ninguna lectura de este plan los exige | — |
| 6.2b | Toma `SpatialGate` con un rebase sobre la Tarea 1 para `data-quality` | — | La firma de la Tarea 1 no cambia después |
| 6.3 | Consume las formas de «Interfaces con el portal» | — | — |

## Resumen

| Tarea | Entrega | Pruebas | Quién corre la integración |
|---|---|---|---|
| 1 | `SpatialGate` y la regla de `ArchitectureTest` | `SpatialGateTest`, `ArchitectureTest` | — |
| 2 | `F6QueryApiTest` y la paridad | `SpatialGateScopeParityTest` | Coordinador |
| 3 | `ZoneCriterion`, `ZoneTargets` | `ZoneCriterionTest` | — |
| 4 | Límites, `QueryParams`, `SpatialSql`, `SpatialParts`, `ProjectFrames` | `QueryParamsTest`, `SpatialSqlTest` | — |
| 5 | `zones/…/records`, `locate` | `SpatialZoneQueriesApiTest` | Coordinador |
| 6 | `member-suggestions`, `buffer-localities` | `SpatialSuggestionsApiTest` | Coordinador |
| 7 | `ComponentLand`, `component-parcels`, `component-communal-land` | `ComponentLandSqlTest`, `SpatialLandQueriesApiTest` | Coordinador |
| 8 | `concession-territories`, `sensitive-distances`, `nearby-conflicts` | `ProximitySqlTest`, `SpatialProximityApiTest` | Coordinador |
| 9 | `concentration`, `summary` | `ConcentrationSqlTest`, `SpatialAggregatesApiTest` | Coordinador |
| 10 | Fichas de localidad y comunidad | `TerritoryCardsApiTest` | Coordinador |
| 11 | Casos de la compuerta | `SpatialGateApiTest` | Coordinador |
| 12 | KPI-TER puros, `KpiEvaluation`, `periodGiven` | `TerritoryKpisTest` | — |
| 13 | Lecturas, KPI-TER-01 a 04, tablero | `TerritoryDashboardTest` | — |
| 14 | El tablero por la API | `TerritoryDashboardApiTest` | Coordinador |
| 15 | Medición con volumen | — | Coordinador |
| 16 | Documentos, verificación, PR | Todas | CI o coordinador |

## Cobertura de la especificación

| Especificación | Tarea |
|---|---|
| §2.2 elección 3 (los alcances se componen; regla de omisión de wasichai) | 1 (decisión 1), 2 |
| §2.2 elección 4 (la geometría de referencia viaja como parámetro) | 3, 5, 6, 10 |
| §2.2 elección 5 (el filtro de territorio de los tableros ya existe; KPI-TER-01 por `indicators/{code}`) | 13, 14 |
| §3.1 `SpatialGate` 1 a 4 | 1, 7 (titulares después por `Records`), 11 |
| §3.1 `ZoneCriterion` y los endpoints de patrón zona | 3, 5, 6, 10 |
| §3.1 medidas (working_crs, zona del centroide, prefiltro GIST) | 4, 6, 7, 8, 9 |
| §3.1 los 13 endpoints | 5 (2), 6 (2), 7 (2), 8 (3), 9 (2), 10 (2) |
| §3.1 límites y `of` de `concentration` | 4, 9 |
| §3.1 `ArchitectureTest` | 1 |
| §3.2 KPI-TER-01 a 04 y tablero `territory` | 12, 13, 14 |
| §5 `SpatialGateApiTest`, `SpatialGateScopeParityTest`, `SpatialQueriesApiTest`, `TerritoryDashboardApiTest`, `ZoneCriterionTest` | 11, 2, 5 a 10, 14, 3 |
| §7 fila 6.2a y la medición con volumen | 1 a 16, 15 |
| §8 consecuencia 4 (el calor agrega por localidad) | 9 |
| §8 riesgos 3 (costo de las subconsultas) y 5 (paridad) | 15, 2 |
