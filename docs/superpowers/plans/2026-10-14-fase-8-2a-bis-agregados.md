# SGSPE · Fase 8, plan 8.2a-bis (KPI del tablero executive contados en SQL): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Que `GET /api/sgspe/dashboards/executive` con 10 proyectos activos responda en menos de 3 s en el p95 (criterio de
la especificación de F8 §7) contando en SQL, detrás de `SpatialGate`, los KPI del tablero que hoy leen filas con
`Records`/`RecordService`, con las mismas cifras que hoy en las tiles y en `byProject[]`, sin cambiar la forma de la API.
Medido el 2026-10-14 en `sgspe_perf` por el túnel (backend local; volumen de F6: 10 proyectos, 50 000 quejas, 20 000
interacciones, 5 000 incidentes): 1 proyecto, p50 6,25 s y p95 8,20 s; 3 proyectos, p50 16,6 s y p95 18,7 s; 10 proyectos,
`400 TOO_MANY_RECORDS` aun con un periodo de un mes. El usuario eligió contar en SQL.

**Architecture:** Dos causas. (1) Lecturas sin cota de periodo: `DashboardData.attention()` lee todas las QCS de los
proyectos (KPI-GRV-02 y las quejas de KPI-EXE-03 las necesitan enteras por la regla de reclasificación), y con 10 proyectos
`grievance` pasa el tope de 20 000 filas; KPI-ENG-01 y KPI-EXE-03 leen además 12 meses de interacciones. Todo se lee página a
página por `RecordService`. (2) `byProject[]` recalcula los 8 KPI de cada proyecto con un `DashboardData.with(...)` nuevo
(sin caché compartida): con 10 proyectos, unas 150 lecturas más. El plan agrega en `sgspe.dashboard` un SQL puro
(`AggregateSql`: cada objeto como la tabla de la compuerta, los proyectos y el territorio en el `WHERE`, `GROUP BY` por
proyecto), un único ejecutor (`KpiAggregates`, que llama a `SpatialGate.select` como la persona o a `selectAsPlatform` como
la plataforma), los conteos en `DashboardData` (en su caché por pedido, compartidos entre las tiles y `byProject`) y
funciones puras que sacan de los conteos el mismo `IndicatorResult` que sus gemelas sobre filas (`AggregateKpis`).
`Indicator.computeByProject` da la cifra de cada proyecto de una vez; `KpiEvaluation.evaluateByProject` la usa en la tabla
por proyecto. El camino de `Records` sigue vivo detrás de `sgspe.dashboards.aggregates` (por defecto `true`): lo usan las
pruebas sin base, la prueba de paridad y, si hiciera falta, la vuelta atrás sin desplegar código.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 1.2.0 (sin
starters ni dependencias nuevas), R2DBC PostgreSQL por `DatabaseClient` solo dentro de `SpatialGate`, JUnit 5 con
`wasichai-test` (`WasichaiIntegrationTest`) y Mockito.

**Spec:** `docs/superpowers/specs/2026-10-13-fase-8-design.md` (vinculante): «Restricciones globales»; §3.1 («Tableros»:
`executive` con `byProject[]` y el tope de §2.2.16; «Lector»: `KpiReader`, `SpatialGate`, `DashboardDeliverableParityTest`);
§7 (la medición del tablero `executive` con 10 proyectos en `sgspe_perf`, p95 bajo 3 s, tarea del coordinador). Plan 8.2a
(`docs/superpowers/plans/2026-10-13-fase-8-2a-tableros.md`, en `main` desde #65): decisiones 6 (forma de `byProject[]`), 7
(KPI-EXE-03 y la regla de reclasificación de las tres QCS), 12 (`selectAsPlatform`), 24 (R6: tope de 25 proyectos), 25
(R4: el cierre sin QCS confidenciales) y 18 (R9: la medición es del coordinador). Documentos: `docs/dashboards.md` §3.1
(territorio y ámbito), §3.2 (estados), §3.3 (regla de conteo de las QCS), §4.1, §5.1, §6 (punto 3, «SQL agregado solo con
medición»: esta es la medición) y §8 (no disponible, nunca cero); `docs/architecture.md` (la regla de `SpatialGate`,
ASM-122, RQ-GIS-017); `docs/api.md` §4.11; RQ-DSH-032 y RQ-DSH-034.

## Global Constraints

Las restricciones globales de la especificación, copiadas tal cual (rigen en cada tarea aunque no las nombre):

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

Las de este plan:

- **Repositorio y ramas.** Todo el trabajo en el worktree `~/IdeaProjects/sgspe-wt/f82abis`, en la rama
  `feat/fase-8-2a-bis-agregados` creada desde `main` (con este plan integrado). Cada tarea en su rama `f82abis/t<k>`, creada
  desde `feat/fase-8-2a-bis-agregados` con las tareas de las que depende ya integradas, y se integra con
  `git merge --no-ff f82abis/t<k>`. En los comandos, `$REPO` es `~/IdeaProjects/sgspe-wt/f82abis`. `~/IdeaProjects/sgspe` no
  se toca.
- **Arquitectura propia.** El SQL propio sigue pasando solo por `SpatialGate` (ningún `DatabaseClient` ni
  `RecordReadScopes` nuevo). `KpiAggregates` es la única pieza de `sgspe.dashboard` que llama a la compuerta, y solo
  `DashboardData` (con `KpiContext.platform`) y `Dashboards` (que lo inyecta) la nombran; `selectAsPlatform` lo llaman solo
  `ComponentLand` y `KpiAggregates` (`ArchitectureTest`, Tarea 3). Ningún import sin uso: ktlint de este repositorio no los
  quita.
- **Paridad antes que optimización.** Cada cifra que pase a SQL da exactamente lo mismo que hoy: mismas filas (organización,
  alcances de lectura, proyecto, territorio, ámbito, `excludeConfidential`), misma regla de reclasificación, mismos meses de
  Lima, mismo orden de los desgloses, mismos tipos (`Int`). Un campo que quien lee no puede leer sale `NULL` (como
  `RecordService`, que lo quita de los atributos), nunca un «no disponible» que hoy no sale; un campo por el que se filtra en
  la base y no se lee es el mismo `ValidationException` («Filtro inválido») de `Criteria`. Ninguna forma de la API cambia.
- **Errores.** Los de hoy: `400 TOO_MANY_RECORDS` sobre `project` con más proyectos pedidos que
  `sgspe.dashboards.executive.max-projects` (R6) sigue igual; las lecturas que quedan en `Records` mantienen su tope.
- **Datos de prueba.** Ficticios: correos `@sgspe.test`, «Proyecto Ejemplo A», «Caserío Ejemplo», geometrías de
  `SyntheticGeo`. Ningún nombre real en nada versionado.
- **Integración: la escribe el implementador, la corre el coordinador.** Las clases `*ApiTest` necesitan PostgreSQL con
  PostGIS. Ningún implementador ni subagente corre `integrationTest`, ni toca Docker, el túnel, una base de datos o el
  tenant de desarrollo. El implementador corre las unitarias de su tarea, compila las de integración con
  `./gradlew compileTestKotlin` (o `build`) y entrega la tarea marcada **«integración pendiente: <clases>»**. El coordinador
  las corre contra `sgspe_sdd_test` (o las deja a la CI del PR) y hace la medición en `sgspe_perf` (Tarea 8).
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde `$REPO`;
  `./gradlew ktlintFormat` antes de `build`. El token nunca se imprime ni va a un archivo. Con poca memoria,
  `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres);
  al final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM`. `git add` solo con rutas explícitas; nunca
  `initial-promp.md`, `arch-*.md`, `tech-landing.txt`, `.playwright-mcp/` ni nada de `~/Documents/gspe`. `yarn format`
  antes de commitear yaml o json fuera de `model/` (`application.yml` lo formatea prettier). El cuerpo del PR termina con
  `🤖 Generated with [Claude Code](https://claude.com/claude-code)` y la línea de la sesión. Push y PR solo en la Tarea 8.

### Comandos

Unitarias de una clase y build completo (ktlint, unitarias y compilación de las de integración):

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase>'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Integración (coordinador, túnel 5435 abierto y `sgspe_sdd_test` libre):

```bash
cd $REPO && WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'
```

## Review Focus

Las cinco entradas que más pueden morder a quien use esto, cada una con su prueba en la tarea dueña del código:

1. **El SQL propio sin el alcance de lectura.** Un conteo que no pase por la tabla de la compuerta le daría a un relacionista
   las QCS confidenciales o los proyectos que no tiene asignados. Se espera que cada objeto de cada consulta sea la
   subconsulta de `SpatialGate` con la organización y todos los `RecordReadScope` (el de proyecto y el de las QCS
   confidenciales), y que un relacionista de A que pide A y B vea lo mismo que con `Records`. Pruebas: `KpiAggregatesTest`
   «como la persona la tabla lleva su alcance de lectura» (Tarea 3) y `ExecutiveAggregatesParityApiTest` (el relacionista
   con una queja confidencial en A y B pedido, Tarea 6).
2. **La plataforma dentro de una petición.** `selectAsPlatform` sin alcances, llamado mientras se atiende a una persona,
   le daría todo el tenant. Se espera que `KpiAggregates` con `platform` falle con `IllegalStateException` sin correr nada
   si hay un usuario en el contexto, que `DashboardData` solo pase la plataforma que trae `KpiContext` y que ninguna otra
   pieza nombre `KpiAggregates`. Pruebas: `KpiAggregatesTest` «como la plataforma no pide alcances y no corre con una
   persona en el contexto» (Tarea 3), `DashboardDataAggregatesTest` «con la plataforma los conteos van por
   selectAsPlatform» (Tarea 4) y `ArchitectureTest` «solo ComponentLand y KpiAggregates usan selectAsPlatform y solo
   DashboardData les pasa la plataforma» (Tarea 3).
3. **Una cifra distinta de la de antes.** La regla de reclasificación de las tres QCS, los meses de Lima de `held_at` y
   `received_at`, el orden de los desgloses y un campo ilegible (que hoy es un atributo ausente) son donde un conteo en
   SQL se aparta en silencio. Se espera que cada función de `AggregateKpis` dé el mismo `IndicatorResult` que su gemela
   sobre filas agrupadas como las agrupa el SQL, que los límites del periodo y de la serie sean medianoche de Lima, y que
   un campo ilegible no se declare ni se nombre. Pruebas: `AggregateKpisTest` (KPI-GRV-02 y KPI-EXE-03, KPI-ENG-01,
   KPI-COM-02 y 03, KPI-RSK-01 y KPI-EXE-02, Tarea 1), `AggregateSqlTest` «las QCS cuentan con la regla de reclasificacion
   de los tres objetos y sin campos ilegibles» y «las interacciones van desde el primer dia del periodo o de la serie hasta
   el fin del periodo en Lima» (Tarea 2), `DashboardDataAggregatesTest` «un campo ilegible no se nombra y uno por el que se
   filtra es el 400 de Criteria» (Tarea 4) y `ExecutiveAggregatesParityApiTest` (Tarea 6).
4. **`byProject[]` distinto de «ese proyecto solo».** Hoy cada fila es el KPI con el filtro de ese proyecto: con el ámbito
   AIDS o AIIS el territorio es el del área de ese proyecto (no el de todos), y una QCS reclasificada desde otro proyecto
   cuenta en la fila de su proyecto. Se espera lo mismo con una sola consulta agrupada por proyecto (`tile`/`own`,
   `gone`/`gone_own`) y que un KPI sin `computeByProject` siga calculándose por proyecto. Pruebas: `AggregateKpisTest` «la
   fila de un proyecto cuenta su reclasificacion solo si el registro nuevo es de ese proyecto» (Tarea 1), `AggregateSqlTest`
   «sin ambito tile y own son TRUE y con AIDS own es el territorio de cada proyecto» (Tarea 2), `KpiEvaluationByProjectTest`
   (Tarea 5) y `ExecutiveAggregatesParityApiTest` con `scope=AIDS` (Tarea 6).
5. **El cierre (R4) y los topes (R6).** El cierre calcula los KPI de QCS sin las confidenciales también para quien las ve;
   un conteo que no aplique `excludeConfidential` congelaría una cifra oficial que las incluye. Se espera
   `confidential IS NOT TRUE` en los tres objetos solo con `excludeConfidential`, la misma cifra que con `Records` en el
   cierre como la plataforma, y que pedir más de 25 proyectos siga siendo `400 TOO_MANY_RECORDS`. Pruebas: `AggregateSqlTest`
   «excludeConfidential deja fuera las confidenciales solo si se lee la marca» (Tarea 2), `DashboardDataAggregatesTest`
   «excludeConfidential deja fuera las QCS confidenciales en los tres objetos» (Tarea 4), el bucle del cierre de
   `ExecutiveAggregatesParityApiTest` (Tarea 6) y, por regresión, `ExecutiveDashboardApiTest` «más proyectos pedidos que
   el tope es 400 TOO_MANY_RECORDS sobre project» (Tarea 8).

---

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | Qué lecturas dominan el tablero `executive` (las 9 zonas de `ExecutiveKpis.TILES` y los 8 KPI de `BY_PROJECT`) | Pasan a SQL las que crecen con la operación y no tienen cota de periodo o se repiten por proyecto: **KPI-GRV-02** (todas las QCS de los tres objetos), **KPI-ENG-01** (interacciones del periodo y de 12 meses), **KPI-COM-02**, **KPI-COM-03**, **KPI-RSK-01**, **KPI-EXE-02** y, de **KPI-EXE-03**, las series de interacciones y de quejas (reusan las consultas de KPI-ENG-01 y KPI-GRV-02). Quedan en `Records`: **KPI-EXE-01** (los proyectos del filtro, 25 como mucho por R6), **KPI-INV-03** (sumas por moneda de seis objetos con las reglas de ASM-063; volumen de inversión pequeño) y la serie de vencidos de KPI-EXE-03 (KPI-COM-11: reconstruye el plazo vigente con las reprogramaciones, decisión de §4.4 de `dashboards.md`); para esos dos, `byProject` reparte una sola lectura por proyecto en vez de releer por proyecto (Tareas 1 a 5) |
| 2 | Dónde vive el SQL propio: `SpatialGate` es de `sgspe.territory` y hasta hoy solo lo usan las consultas territoriales y `ComponentLand` | En `sgspe.dashboard`, que ya depende de `sgspe.territory` (nunca al revés): `AggregateSql` (puro, como `ComponentLand.sql`) y `KpiAggregates` (el único que llama a `select`/`selectAsPlatform`). `ArchitectureTest` suma `KpiAggregates` a la regla de la decisión 12 de 8.2a y vigila que solo `DashboardData` y `Dashboards` lo nombren (Tarea 3) |
| 3 | `SpatialGate.select` da «no disponible» si falta un campo declarado; `RecordService` en cambio quita el campo de los atributos y el KPI sigue (p. ej. `engagement.locality` no está en los `requires` de KPI-ENG-01) | Un objeto de una consulta (`AggregateSource`) declara solo sus campos legibles; los demás salen `NULL`, como el atributo ausente (una localidad `NULL` no casa con un filtro de territorio, igual que en Kotlin). Los campos por los que `Records` filtra en la base (`project`, `held_at`, `exploration_campaign`, `owner_user_id`) se exigen legibles con el mismo `ValidationException("Filtro inválido", …)` de `Criteria` (Tareas 2 y 4) |
| 4 | La regla de conteo de `dashboards.md` §3.3 (un original reclasificado no cuenta si el registro nuevo está en la lista) cruza los tres objetos | Una sola consulta con un `WITH` de las tres QCS (`UNION ALL`), los vínculos `reclassified_from_*` como `from_<objeto>` y dos `LEFT JOIN` contra los originales apuntados (sin subconsultas por fila): `gone` (apuntado desde la lista de las tiles) y `gone_own` (desde la de su mismo proyecto), para que `byProject` cuente como «ese proyecto solo» aunque la reclasificación cruce proyectos (Tarea 2) |
| 5 | Con el ámbito AIDS o AIIS, el territorio de las tiles es el de las áreas aprobadas de todos los proyectos, y el de la fila de un proyecto en `byProject`, el de las suyas (`DashboardData.with` con ese proyecto) | Cada fila agregada lleva `tile` (pasa el territorio de las tiles) y `own` (pasa el de su proyecto); sin ese ámbito los dos son `TRUE`. `DashboardData.scopeTerritories()` lee las áreas una vez y arma el territorio de cada proyecto con `areaTerritory` (Tareas 2 y 4) |
| 6 | Los meses y los límites del periodo son de Lima (`held(...)`, `QcsDueDates.receivedDate`, `EngagementKpis.months`) | `held_at` y `received_at` (`timestamptz`) se comparan con medianoche de Lima enlazada como `OffsetDateTime` en UTC (como `Criteria.between`) y su mes es `to_char(… AT TIME ZONE 'America/Lima', 'YYYY-MM')` con la zona enlazada; `closed_date` y `due_date` son `date`. Los meses de las quejas salen solo dentro de los 12 de la serie (fuera, `NULL`), para que el `GROUP BY` no crezca con la historia (Tarea 2) |
| 7 | `byProject[]` hoy recalcula cada KPI por proyecto con un `DashboardData` nuevo | `Indicator.computeByProject(data)` (por defecto `null`) da la cifra de cada proyecto de una vez desde los mismos conteos cacheados de las tiles (una consulta agrupada por proyecto por KPI); `KpiEvaluation.evaluateByProject` aplica la misma disponibilidad a todas las filas y, si el KPI no la trae, evalúa cada proyecto como antes. `Dashboards.byProject` solo lo usa con los conteos en la base (Tarea 5) |
| 8 | La paridad exige comparar el camino de hoy con el nuevo sobre los mismos datos | El camino de `Records` se queda: `DashboardData(aggregates = null)` (las pruebas sin base, `KpiFixtureData`, `ConfidentialExclusionTest`) y `sgspe.dashboards.aggregates: false`. `Dashboards.dashboard(…, aggregated)` y `Dashboards.value(…, aggregated)` son `internal` para la prueba de paridad. **Pregunta al usuario**: dejar la propiedad como vuelta atrás o borrarla con el camino viejo cuando la medición pase (Tareas 5 y 8) |
| 9 | Los KPI movidos también salen en otros tableros | Un KPI es uno solo (`Kpis.BY_CODE`): con los conteos en la base cambian también KPI-GRV-02 en `attention` y `project`, KPI-ENG-01 en `engagement` y `project`, KPI-COM-02 y 03 en `commitments` y `project`, KPI-RSK-01 en `risks` y `project`, la ficha KPI-TER-01 del tablero `territory` (sus filas KPI-ENG-01, KPI-COM-02, KPI-COM-03 y KPI-GRV-02), el detalle de cada uno y el cierre de periodo. `monitoring` no usa ninguno. La prueba de paridad los recorre (Tarea 6) |
| 10 | `400 TOO_MANY_RECORDS` con 10 proyectos | Los KPI movidos no cargan filas: no hay tope que pasar. Lo que el tablero `executive` todavía lee con `Records` (los proyectos, los seis conjuntos de KPI-INV-03 y los compromisos y reprogramaciones de KPI-EXE-03) conserva el tope de 20 000 filas: con más compromisos que eso el tablero sigue siendo `400` (riesgo 2). R6 no cambia (Tarea 5) |
| 11 | KPI-ENG-05 y la marca `confidentialHidden` | KPI-ENG-05 no se mueve (no está en el tablero `executive` y no lee QCS: cuenta por `referred_at`, `Kpis.closingRequires`). La marca la sigue poniendo `Dashboards.mark` con `Kpis.confidentialSensitive`, igual con los dos caminos (Tarea 5) |
| 12 | `dashboards.md` §6 punto 3 deja el SQL agregado «solo si una medición lo justifica» | La medición del 2026-10-14 lo justifica: la Tarea 7 lo anota en §6 (diagrama y punto 3), en `api.md` §4.11, en `architecture.md` y en `CLAUDE.md`; la Tarea 8 anota la medición nueva en `roadmap.md` F8 «Estado» |
| 13 | Índices | Ninguno nuevo: con 10 proyectos se leen casi todas las filas del tenant y un barrido de 50 000 quejas es de decenas de ms. Si la medición no pasa, el coordinador mira `EXPLAIN ANALYZE` de cada consulta antes de tocar nada (**decisión del usuario** si hiciera falta un índice declarado en `model.json`) |

## Mapa de archivos

| Archivo | Tarea | Qué |
|---|---|---|
| `src/main/kotlin/sgspe/dashboard/AggregateKpis.kt` (nuevo), `Indicators.kt` (`Numbers.tally`), `EngagementKpis.kt` (`NO_TYPE`), `ExecutiveKpis.kt` (`NO_DATA` público), `src/test/kotlin/sgspe/dashboard/AggregateKpisTest.kt` (nuevo) | 1 | Las cifras desde conteos, puras |
| `src/main/kotlin/sgspe/dashboard/AggregateSql.kt` (nuevo), `src/test/kotlin/sgspe/dashboard/AggregateSqlTest.kt` (nuevo) | 2 | El SQL de los conteos, puro |
| `src/main/kotlin/sgspe/dashboard/KpiAggregates.kt` (nuevo), `src/test/kotlin/sgspe/dashboard/KpiAggregatesTest.kt` (nuevo), `src/test/kotlin/sgspe/ArchitectureTest.kt` | 3 | El ejecutor por la compuerta y su vigía |
| `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, `RiskKpis.kt` (`ACTIVE_STATES`), `src/test/kotlin/sgspe/dashboard/DashboardDataAggregatesTest.kt` (nuevo) | 4 | Los conteos de un pedido |
| `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `KpiEvaluation.kt`, `Dashboards.kt`, `DashboardsProperties.kt`, `src/main/resources/application.yml`, `src/test/kotlin/sgspe/dashboard/KpiEvaluationByProjectTest.kt` (nuevo), `DashboardsPropertiesTest.kt` | 5 | Los KPI, `byProject` y la propiedad |
| `src/test/kotlin/sgspe/dashboard/ExecutiveAggregatesParityApiTest.kt` (nuevo) | 6 | La paridad contra la base |
| `docs/dashboards.md` (§6), `docs/api.md` (§4.11), `docs/architecture.md`, `CLAUDE.md` | 7 | Documentos |
| `docs/roadmap.md` (F8 «Estado») | 8 | Integración, medición y PR (coordinador) |

## Orden y paralelismo

```
Tarea 1 ── Tarea 2 ── Tarea 3 ── Tarea 4 ── Tarea 5 ── Tarea 6 ── Tarea 7 ── Tarea 8
```

Una cadena: cada tarea usa los tipos de la anterior (`AttentionCount`, `HeldCount` y `GroupCount` nacen en la 1; `AggregateQuery`
en la 2; `KpiAggregates` en la 3; los conteos de `DashboardData` en la 4; `Dashboards.dashboard(…, aggregated)` en la 5). La
7 puede ir en paralelo con la 6 si el coordinador lo prefiere (solo toca documentos).

---

### Tarea 1: Las cifras de los KPI desde conteos agrupados (`AggregateKpis`)

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/AggregateKpis.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/Indicators.kt`, `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt`,
  `src/main/kotlin/sgspe/dashboard/ExecutiveKpis.kt`
- Test: `src/test/kotlin/sgspe/dashboard/AggregateKpisTest.kt` (nuevo)

**Interfaces:**
- Consumes: `AttentionKpis.open` y `CLOSED`, `AttentionCounting.originals`, `Attention.isOpen`, `EngagementKpis.held` y
  `months`, `CommitmentKpis.open`, `overdue`, `BUCKET_*`, `DEFAULT_*`, `RiskKpis.high` y `NO_DATA`,
  `ExecutiveKpis.activeWarnings` y `trend`, `Numbers.counts`.
- Produces: `AttentionCount(project, type, state, tile, own, gone, goneOwn, received: YearMonth?, closed: YearMonth?, n)`,
  `HeldCount(project, type, month, inPeriod, tile, own, n)`, `GroupCount(project, keys: List<Any?>, tile, own, n)`;
  `AggregateKpis.open`, `openByProject`, `held`, `heldByProject`, `openCommitments`, `overdueCommitments`, `highRisks`,
  `activeWarnings`, `groupedByProject`, `trend`; `Numbers.tally(Map<String, Int>)`; `EngagementKpis.NO_TYPE`;
  `ExecutiveKpis.NO_DATA` público.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-bis-agregados && git switch -c f82abis/t1
```

- [ ] **Paso 2: La prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/AggregateKpisTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sgspe.commitment.Commitments
import sgspe.common.Attention
import sgspe.common.AttentionCounting
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

// plan 8.2a-bis: cada KPI desde los conteos de la base da lo mismo que su gemela sobre filas, con filas al azar (semillas fijas)
// agrupadas como las agrupa AggregateSql (las funciones `group…` de abajo son el GROUP BY en Kotlin). la paridad del SQL con
// esas agrupaciones la prueba ExecutiveAggregatesParityApiTest contra la base
class AggregateKpisTest {
    private val end = LocalDate.parse("2026-09-30")
    private val start = LocalDate.parse("2026-09-01")
    private val today = LocalDate.parse("2026-10-14")
    private val months = EngagementKpis.months(end)
    private val seeds = listOf(1, 7, 42, 2026)

    private fun <T> Random.pick(list: List<T>): T = list[nextInt(list.size)]

    private fun day(random: Random): LocalDate = LocalDate.parse("2025-06-01").plusDays(random.nextLong(520))

    // ---- QCS ----

    private val states: Map<String, List<String?>> =
        mapOf(
            Attention.GRIEVANCE to listOf("received", "in_review", "resolved", "closed", "rejected", "withdrawn", null),
            Attention.INQUIRY to listOf("received", "answered", "closed"),
            Attention.REQUEST to listOf("received", "in_evaluation", "closed")
        )

    private fun qcs(
        random: Random,
        count: Int,
        prefix: String = "q"
    ): List<AttentionRow> {
        val rows = mutableListOf<AttentionRow>()
        repeat(count) { i ->
            val type = random.pick(Attention.OBJECTS)
            val state = random.pick(states.getValue(type))
            // un tercio apunta a una QCS anterior de otro tipo: la reclasificó
            val previous = rows.filter { it.type != type }.takeIf { it.isNotEmpty() && random.nextInt(3) == 0 }?.let { random.pick(it) }
            val reclassified = previous?.let { mapOf(Attention.reclassifiedField(type, it.type) to it.id) } ?: emptyMap()
            rows +=
                AttentionRow(
                    type,
                    "$prefix$i",
                    state,
                    day(random).takeIf { random.nextInt(10) > 0 },
                    null,
                    null,
                    day(random).takeIf { random.nextBoolean() },
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    reclassified
                )
        }
        return rows
    }

    // el GROUP BY de AggregateSql.attention para un proyecto, sin ámbito (tile y own verdaderos)
    private fun groupAttention(
        project: String,
        rows: List<AttentionRow>
    ): List<AttentionCount> {
        val gone = AttentionCounting.originals(rows.map { it.raised() })
        return rows
            .groupingBy { r ->
                val grievance = r.type == Attention.GRIEVANCE
                val received = r.received?.let(YearMonth::from)?.takeIf { grievance && it in months }
                val closed = r.closed?.let(YearMonth::from)?.takeIf { grievance && r.state == AttentionKpis.CLOSED && it in months }
                val out = (r.type to r.id) in gone
                AttentionCount(project, r.type, r.state, true, true, out, out, received, closed, 0)
            }.eachCount()
            .map { (key, n) -> key.copy(n = n) }
    }

    @Test
    fun `KPI-GRV-02 y las quejas de KPI-EXE-03 desde los conteos son las de las filas`() {
        for (seed in seeds) {
            val rows = qcs(Random(seed), 300)
            val counts = groupAttention("p", rows)
            assertEquals(AttentionKpis.open(rows), AggregateKpis.open(counts), "semilla $seed")
            val engagements = IndicatorResult(0, series = months.map { mapOf("period" to it.toString(), "value" to it.monthValue) })
            val overdue = IndicatorResult(0, series = months.map { mapOf("period" to it.toString(), "value" to it.year % 7) })
            assertEquals(ExecutiveKpis.trend(engagements, rows, overdue, end), AggregateKpis.trend(engagements, counts, overdue, end), "semilla $seed")
        }
    }

    @Test
    fun `la fila de un proyecto cuenta su reclasificacion solo si el registro nuevo es de ese proyecto`() {
        val random = Random(3)
        val a = qcs(random, 120, "a")
        val b = qcs(random, 120, "b")
        // b reclasificó un original de a: en las tiles el original no cuenta; en la fila de a, sí (como el filtro de a solo)
        val reclassifiedInA = AttentionCounting.originals(a.map { it.raised() })
        val original = a.first { it.type == Attention.GRIEVANCE && it.open && (it.type to it.id) !in reclassifiedInA }
        val successor = b.first { it.type == Attention.INQUIRY }.let { it.copy(reclassified = mapOf("reclassified_from_grievance" to original.id)) }
        val bRows = b.map { if (it.id == successor.id) successor else it }
        val all = a + bRows
        val gone = AttentionCounting.originals(all.map { it.raised() })
        // gone: apuntado por cualquier fila de las tiles; goneOwn: por una de su proyecto (lo que da el GROUP BY de la base)
        val marked =
            all.map { r ->
                val project = if (r in a) "a" else "b"
                val own = AttentionCounting.originals((if (project == "a") a else bRows).map { it.raised() })
                AttentionCount(project, r.type, r.state, true, true, (r.type to r.id) in gone, (r.type to r.id) in own, null, null, 1)
            }
        assertEquals(AttentionKpis.open(all).value, AggregateKpis.open(marked).value)
        val split = AggregateKpis.openByProject(marked, listOf("a", "b"))
        assertEquals(AttentionKpis.open(a).value, split.getValue("a").value)
        assertEquals(AttentionKpis.open(bRows).value, split.getValue("b").value)
        assertEquals(AttentionKpis.open(all).value, split.getValue("a").value as Int + split.getValue("b").value as Int - 1)
    }

    // ---- interacciones ----

    @Test
    fun `KPI-ENG-01 desde los conteos es el de las filas`() {
        for (seed in seeds) {
            val random = Random(seed)
            val periodStart = LocalDate.parse("2025-08-15")
            val windowStart = months.first().atDay(1)
            val rows =
                (0 until 400)
                    .map { i ->
                        EngagementRow("e$i", "p", random.pick(listOf("REUNION", "TALLER", "REUNION INFORMATIVA", null)), null, day(random), emptyMap())
                    }.filter { !it.heldDate.isBefore(minOf(periodStart, windowStart)) && !it.heldDate.isAfter(end) }
            val inPeriod = rows.filter { !it.heldDate.isBefore(periodStart) }
            val window = rows.filter { !it.heldDate.isBefore(windowStart) }
            val counts =
                rows
                    .groupingBy { HeldCount("p", it.type, YearMonth.from(it.heldDate), !it.heldDate.isBefore(periodStart), true, true, 0) }
                    .eachCount()
                    .map { (key, n) -> key.copy(n = n) }
            assertEquals(EngagementKpis.held(inPeriod, window, end), AggregateKpis.held(counts, end), "semilla $seed")
            assertEquals(inPeriod.size, AggregateKpis.heldByProject(counts, listOf("p", "q")).getValue("p").value)
            assertEquals(0, AggregateKpis.heldByProject(counts, listOf("p", "q")).getValue("q").value)
        }
    }

    // ---- compromisos ----

    private fun commitment(
        random: Random,
        i: Int
    ) = CommitmentRow(
        "c$i",
        random.pick(listOf("p1", "p2", "p3")),
        random.pick(CommitmentKpis.STATES + listOf<String?>(null)),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        day(random).takeIf { random.nextInt(8) > 0 },
        null,
        null,
        random.pick(listOf(CommitmentKpis.OVERDUE, CommitmentKpis.DUE_SOON, null)),
        null,
        null,
        null
    )

    @Test
    fun `KPI-COM-02 y KPI-COM-03 desde los conteos son los de las filas, tambien con umbrales distintos por proyecto`() {
        for (seed in seeds) {
            val random = Random(seed)
            val rows = (0 until 300).map { commitment(random, it) }
            val open =
                rows
                    .filter { it.state !in Commitments.CLOSED }
                    .groupingBy { it.project!! }
                    .eachCount()
                    .map { (p, n) -> GroupCount(p, emptyList(), true, true, n) }
            assertEquals(CommitmentKpis.open(rows), AggregateKpis.openCommitments(open), "semilla $seed")
            val overdue =
                rows
                    .filter { it.state in Commitments.PENDING && it.deadlineStatus == CommitmentKpis.OVERDUE }
                    .groupingBy { it.project!! to it.dueDate }
                    .eachCount()
                    .map { (key, n) -> GroupCount(key.first, listOf(key.second), true, true, n) }
            for (thresholds in listOf(mapOf<String, Escalation>(), mapOf("p2" to Escalation(3, 30)))) {
                val escalation: (String?) -> Escalation = { thresholds[it] ?: Escalation(7, 15) }
                assertEquals(CommitmentKpis.overdue(rows, today, escalation), AggregateKpis.overdueCommitments(overdue, today, escalation), "semilla $seed")
            }
            val split = AggregateKpis.groupedByProject(overdue, listOf("p1", "p2", "p3", "p4"))
            for (p in listOf("p1", "p2", "p3", "p4")) {
                assertEquals(CommitmentKpis.overdue(rows.filter { it.project == p }, today) { Escalation(7, 15) }.value, split.getValue(p).value)
            }
        }
    }

    // ---- riesgos y alertas ----

    @Test
    fun `KPI-RSK-01 y KPI-EXE-02 desde los conteos son los de las filas`() {
        for (seed in seeds) {
            val random = Random(seed)
            val risks =
                (0 until 200).map {
                    RiskRow(
                        "r$it",
                        "p",
                        null,
                        random.pick(listOf("EXPECTATIVAS Y BENEFICIOS", "AGUA", null)),
                        null,
                        null,
                        random.pick(listOf("BAJO", "MEDIO", "ALTO", "CRITICO", null)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                    )
                }
            val high =
                risks
                    .filter { it.level == "ALTO" || it.level == "CRITICO" }
                    .groupingBy { it.category }
                    .eachCount()
                    .map { (category, n) -> GroupCount("p", listOf(category), true, true, n) }
            assertEquals(RiskKpis.high(risks), AggregateKpis.highRisks(high), "semilla $seed")
            val warnings =
                (0 until 200).map {
                    WarningRow("w$it", random.pick(ExecutiveKpis.ACTIVE_WARNING_STATES.toList() + "closed"), random.pick(listOf("ALTO", "MEDIO", null)))
                }
            val active =
                warnings
                    .filter { it.state in ExecutiveKpis.ACTIVE_WARNING_STATES }
                    .groupingBy { it.severity to it.state }
                    .eachCount()
                    .map { (key, n) -> GroupCount("p", listOf(key.first, key.second), true, true, n) }
            assertEquals(ExecutiveKpis.activeWarnings(warnings), AggregateKpis.activeWarnings(active), "semilla $seed")
        }
    }

    @Test
    fun `las tiles cuentan las filas tile y cada proyecto las suyas own`() {
        val rows =
            listOf(
                GroupCount("a", emptyList(), tile = true, own = true, n = 2),
                GroupCount("a", emptyList(), tile = true, own = false, n = 3),
                GroupCount("b", emptyList(), tile = false, own = true, n = 5)
            )
        assertEquals(5, AggregateKpis.openCommitments(rows).value)
        val split = AggregateKpis.groupedByProject(rows, listOf("a", "b", "c"))
        assertEquals(listOf(2, 5, 0), listOf("a", "b", "c").map { split.getValue(it).value })
    }

    @Test
    fun `tally ordena como counts`() {
        val keys = listOf("b", "a", "c", "a", "b", "a", "d")
        assertEquals(Numbers.counts(keys), Numbers.tally(keys.groupingBy { it }.eachCount() + ("z" to 0)))
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.AggregateKpisTest'
```

Esperado: falla la compilación (`AttentionCount`, `AggregateKpis`, `Numbers.tally` no existen).

- [ ] **Paso 3: La implementación**

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, al final de `object Numbers`, después de `counts`:

```kotlin
    // lo mismo desde los totales ya contados por clave (los conteos de la base, plan 8.2a-bis): las claves sin cuenta no salen
    fun tally(totals: Map<String, Int>): List<Map<String, Any?>> =
        totals.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { mapOf("key" to it.key, "value" to it.value) }
```

En `src/main/kotlin/sgspe/dashboard/EngagementKpis.kt`, al principio de `object EngagementKpis`:

```kotlin
    // el tipo de una interacción sin engagement_type en el desglose de KPI-ENG-01
    const val NO_TYPE = "SIN TIPO"

```

y en `held(...)`, `Numbers.counts(inPeriod.map { it.type ?: "SIN TIPO" })` pasa a
`Numbers.counts(inPeriod.map { it.type ?: NO_TYPE })`.

En `src/main/kotlin/sgspe/dashboard/ExecutiveKpis.kt`, `private const val NO_DATA = "SIN DATO"` pasa a
`const val NO_DATA = "SIN DATO"`.

Crear `src/main/kotlin/sgspe/dashboard/AggregateKpis.kt`:

```kotlin
package sgspe.dashboard

import sgspe.common.Attention
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

// cada fila agregada lleva `tile` (cuenta en las tiles) y `own` (cuenta en la fila de su proyecto en byProject): solo
// difieren con el ámbito AIDS o AIIS y varios proyectos
data class AttentionCount(
    val project: String,
    val type: String,
    val state: String?,
    val tile: Boolean,
    val own: Boolean,
    // un registro de la lista de las tiles (gone) o de la de su proyecto (goneOwn) lo reclasificó: no cuenta (§3.3)
    val gone: Boolean,
    val goneOwn: Boolean,
    // solo de las quejas: el mes de Lima de received_at y el de closed_date (si está en closed) dentro de los 12 de la serie
    val received: YearMonth?,
    val closed: YearMonth?,
    val n: Int
)

data class HeldCount(
    val project: String,
    val type: String?,
    val month: YearMonth,
    val inPeriod: Boolean,
    val tile: Boolean,
    val own: Boolean,
    val n: Int
)

// un conteo por proyecto y por las claves de la consulta, en su orden (texto o fecha)
data class GroupCount(
    val project: String,
    val keys: List<Any?>,
    val tile: Boolean,
    val own: Boolean,
    val n: Int
)

// los KPI del tablero executive desde los conteos de la base (AggregateSql, plan 8.2a-bis), puros: cada función da lo mismo
// que su gemela sobre filas (AttentionKpis.open, EngagementKpis.held, CommitmentKpis.open y overdue, RiskKpis.high,
// ExecutiveKpis.activeWarnings y trend) con las filas agrupadas como las agrupa el SQL (AggregateKpisTest). las tiles cuentan
// las filas con `tile`; la fila de un proyecto en byProject, las suyas con `own`, solo con su valor (sin desgloses ni series)
object AggregateKpis {
    private fun sum(rows: List<GroupCount>): Int = rows.sumOf { it.n }

    private fun byProject(
        projects: List<String>,
        value: (String) -> Int
    ): Map<String, IndicatorResult> = projects.associateWith { IndicatorResult(value(it)) }

    private fun <T> totals(
        rows: List<T>,
        key: (T) -> String,
        n: (T) -> Int
    ): Map<String, Int> = rows.groupBy(key).mapValues { (_, list) -> list.sumOf(n) }

    // KPI-GRV-02: abiertas hoy, por tipo (en el orden de los tres objetos) y por «<tipo>.<estado>»
    fun open(rows: List<AttentionCount>): IndicatorResult {
        val open = rows.filter { it.tile && !it.gone && Attention.isOpen(it.type, it.state) }
        val types = totals(open, { it.type }, { it.n })
        return IndicatorResult(
            open.sumOf { it.n },
            breakdowns =
                listOf(
                    Breakdown("type", Attention.OBJECTS.filter { it in types }.map { mapOf("key" to it, "value" to types.getValue(it)) }),
                    Breakdown("workflow_state", Numbers.tally(totals(open, { "${it.type}.${it.state}" }, { it.n })))
                )
        )
    }

    fun openByProject(
        rows: List<AttentionCount>,
        projects: List<String>
    ): Map<String, IndicatorResult> =
        byProject(projects) { p -> rows.filter { it.project == p && it.own && !it.goneOwn && Attention.isOpen(it.type, it.state) }.sumOf { it.n } }

    // KPI-ENG-01: las realizadas en el periodo por tipo y la serie de los 12 meses que terminan en `end`
    fun held(
        rows: List<HeldCount>,
        end: LocalDate
    ): IndicatorResult {
        val tile = rows.filter { it.tile }
        val period = tile.filter { it.inPeriod }
        return IndicatorResult(
            period.sumOf { it.n },
            breakdowns = listOf(Breakdown("engagement_type", Numbers.tally(totals(period, { it.type ?: EngagementKpis.NO_TYPE }, { it.n })))),
            series =
                EngagementKpis.months(end).map { month ->
                    mapOf("period" to month.toString(), "value" to tile.filter { it.month == month }.sumOf { it.n })
                }
        )
    }

    fun heldByProject(
        rows: List<HeldCount>,
        projects: List<String>
    ): Map<String, IndicatorResult> = byProject(projects) { p -> rows.filter { it.project == p && it.own && it.inPeriod }.sumOf { it.n } }

    // KPI-COM-02: los abiertos (la consulta ya deja fuera closed y cancelled)
    fun openCommitments(rows: List<GroupCount>): IndicatorResult = IndicatorResult(sum(rows.filter { it.tile }))

    // KPI-COM-03: los pendientes vencidos (la consulta ya los filtra), por días de atraso desde su due_date (la clave 0) con
    // los umbrales de su proyecto, con las claves y etiquetas de CommitmentKpis.overdue
    fun overdueCommitments(
        rows: List<GroupCount>,
        today: LocalDate,
        escalation: (String?) -> Escalation
    ): IndicatorResult {
        val tile = rows.filter { it.tile }
        val thresholds = tile.map { escalation(it.project) }.distinct()
        val counts = mutableMapOf<String, Int>()
        for (row in tile) {
            val (first, second) = escalation(row.project)
            val days = (row.keys[0] as LocalDate?)?.let { ChronoUnit.DAYS.between(it, today) } ?: Long.MAX_VALUE
            val bucket =
                when {
                    days <= first -> CommitmentKpis.BUCKET_FIRST
                    days <= second -> CommitmentKpis.BUCKET_SECOND
                    else -> CommitmentKpis.BUCKET_OVER
                }
            counts[bucket] = (counts[bucket] ?: 0) + row.n
        }
        val uniform = thresholds.singleOrNull() ?: Escalation(CommitmentKpis.DEFAULT_FIRST, CommitmentKpis.DEFAULT_SECOND).takeIf { thresholds.isEmpty() }
        val labels =
            mapOf(
                CommitmentKpis.BUCKET_FIRST to (uniform?.let { "1-${it.first}" } ?: "Hasta el primer umbral"),
                CommitmentKpis.BUCKET_SECOND to (uniform?.let { "${it.first + 1}-${it.second}" } ?: "Hasta el segundo umbral"),
                CommitmentKpis.BUCKET_OVER to (uniform?.let { "más de ${it.second}" } ?: "Más del segundo umbral")
            )
        val out =
            listOf(CommitmentKpis.BUCKET_FIRST, CommitmentKpis.BUCKET_SECOND, CommitmentKpis.BUCKET_OVER)
                .filter { it in counts }
                .map { mapOf("key" to it, "label" to labels.getValue(it), "value" to counts.getValue(it)) }
        return IndicatorResult(sum(tile), breakdowns = listOf(Breakdown("daysOverdue", out)))
    }

    // KPI-RSK-01: los activos con nivel ALTO o CRITICO (la consulta los filtra), por categoría (la clave 0)
    fun highRisks(rows: List<GroupCount>): IndicatorResult {
        val tile = rows.filter { it.tile }
        return IndicatorResult(
            sum(tile),
            breakdowns = listOf(Breakdown("category", Numbers.tally(totals(tile, { it.keys[0] as String? ?: RiskKpis.NO_DATA }, { it.n }))))
        )
    }

    // KPI-EXE-02: las activas (la consulta las filtra), por severidad (la clave 0) y por estado (la clave 1)
    fun activeWarnings(rows: List<GroupCount>): IndicatorResult {
        val tile = rows.filter { it.tile }
        return IndicatorResult(
            sum(tile),
            breakdowns =
                listOf(
                    Breakdown("severity", Numbers.tally(totals(tile, { it.keys[0] as String? ?: ExecutiveKpis.NO_DATA }, { it.n }))),
                    Breakdown("workflow_state", Numbers.tally(totals(tile, { it.keys[1] as String? ?: ExecutiveKpis.NO_DATA }, { it.n })))
                )
        )
    }

    // la cifra de cada proyecto de un conteo agrupado (KPI-COM-02, KPI-COM-03, KPI-RSK-01, KPI-EXE-02)
    fun groupedByProject(
        rows: List<GroupCount>,
        projects: List<String>
    ): Map<String, IndicatorResult> = byProject(projects) { p -> sum(rows.filter { it.project == p && it.own }) }

    // KPI-EXE-03: la serie de ExecutiveKpis.trend con las quejas desde los conteos (la regla de conteo de las tres QCS ya
    // aplicada en la base: gone)
    fun trend(
        engagements: IndicatorResult,
        attention: List<AttentionCount>,
        overdue: IndicatorResult,
        end: LocalDate
    ): IndicatorResult {
        val grievances = attention.filter { it.tile && !it.gone && it.type == Attention.GRIEVANCE }
        val held = engagements.series.orEmpty().associate { it["period"] to it["value"] }
        val late = overdue.series.orEmpty().associate { it["period"] to it["value"] }
        val series =
            EngagementKpis.months(end).map { month ->
                val period = month.toString()
                mapOf(
                    "period" to period,
                    "engagements" to (held[period] ?: 0),
                    "grievancesReceived" to grievances.filter { it.received == month }.sumOf { it.n },
                    "grievancesClosed" to grievances.filter { it.closed == month }.sumOf { it.n },
                    "overdueCommitments" to (late[period] ?: 0)
                )
            }
        return IndicatorResult(null, series = series)
    }
}
```

- [ ] **Paso 4: En verde, build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.AggregateKpisTest'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `7 tests completed`, sin fallas; `BUILD SUCCESSFUL`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/AggregateKpis.kt src/main/kotlin/sgspe/dashboard/Indicators.kt \
  src/main/kotlin/sgspe/dashboard/EngagementKpis.kt src/main/kotlin/sgspe/dashboard/ExecutiveKpis.kt \
  src/test/kotlin/sgspe/dashboard/AggregateKpisTest.kt
git commit -m "feat: KPI del tablero executive desde conteos agrupados" -m "AggregateKpis da desde conteos por proyecto lo mismo que AttentionKpis.open, EngagementKpis.held,
CommitmentKpis.open y overdue, RiskKpis.high y ExecutiveKpis.activeWarnings y trend.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-bis-agregados && git merge --no-ff f82abis/t1 -m "Merge branch 'f82abis/t1' into feat/fase-8-2a-bis-agregados"
```

---

### Tarea 2: El SQL de los conteos (`AggregateSql`)

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/AggregateSql.kt`
- Test: `src/test/kotlin/sgspe/dashboard/AggregateSqlTest.kt` (nuevo)

**Interfaces:**
- Consumes: `GateRead`, `GateTables`, `SpatialGate.ID`, `SpatialGate.STATE` y `SpatialGate.requireReadOnly` (de
  `sgspe.territory`); `DashboardData.Territory`; `Attention.OBJECTS`, `RECLASSIFIED_FROM`, `RECEIVED_AT`, `CONFIDENTIAL`;
  `LIMA`; los tipos de la Tarea 1.
- Produces: `AggregateQuery<T>(key, reads, sql, map)`; `AggregateSource(objectName, readable, alias = "x")` con `read()`,
  `column(t, field): String?` y `system(t, field)`; `AggregateFilter(projects, territories, scope: ScopeTerritories? =
  null)`; `ScopeTerritories(all, byProject: Map<UUID, Territory>)`; `AggregateSql.attention(sources, filter, months,
  excludeConfidential)`, `held(source, filter, states, periodStart, periodEnd, windowStart)` y `grouped(key, source,
  filter, keys: List<Pair<String, String>>, conditions)`; internos `inTerritory` y `where`.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-bis-agregados && git switch -c f82abis/t2
```

- [ ] **Paso 2: La prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/AggregateSqlTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.Attention
import sgspe.territory.GateTables
import sgspe.territory.SpatialGate
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

// el SQL de los conteos del tablero executive (plan 8.2a-bis) sin base: cada objeto como la tabla de la compuerta, los
// proyectos y el territorio en el WHERE, un campo ilegible como NULL (nunca declarado), la regla de conteo de las QCS en la
// base, los límites de Lima enlazados y solo SELECT o WITH
class AggregateSqlTest {
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
            ) = error("sin geometrías")
        }

    private class Bound {
        val values = mutableListOf<Any>()

        fun bind(value: Any): String {
            values += value
            return ":g${values.size - 1}"
        }
    }

    private val p1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val p2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val locality = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val community = UUID.fromString("00000000-0000-0000-0000-0000000000c1")

    private fun <T : Any> render(query: AggregateQuery<T>): Pair<String, List<Any>> {
        val bound = Bound()
        val sql = query.sql(tables, bound::bind)
        SpatialGate.requireReadOnly(sql)
        return sql to bound.values
    }

    @Test
    fun `el territorio casa por localidad o comunidad, una columna ilegible no casa y sin nada nada casa`() {
        val bound = Bound()
        val both = DashboardData.Territory(setOf(locality.toString()), setOf(community.toString()), null)
        assertEquals(
            "COALESCE(x.\"locality\" = ANY(:g0) OR x.\"community\" = ANY(:g1), FALSE)",
            AggregateSql.inTerritory("x.\"locality\"", "x.\"community\"", both, bound::bind)
        )
        assertEquals("COALESCE(x.\"community\" = ANY(:g2), FALSE)", AggregateSql.inTerritory(null, "x.\"community\"", both, bound::bind))
        assertEquals("FALSE", AggregateSql.inTerritory(null, null, both, bound::bind))
        assertEquals("FALSE", AggregateSql.inTerritory("x.\"locality\"", "x.\"community\"", DashboardData.Territory(emptySet(), emptySet(), null), bound::bind))
    }

    @Test
    fun `sin ambito tile y own son TRUE y con AIDS own es el territorio de cada proyecto`() {
        val source = AggregateSource("social_risk", setOf("project", "locality", "community"))
        val plain = AggregateSql.where(tables, Bound()::bind, source, AggregateFilter(listOf(p1, p2), emptyList()))
        assertEquals("TRUE", plain.tile)
        assertEquals("TRUE", plain.own)
        assertEquals("x.\"project\" = ANY(:g0) AND ((TRUE) OR (TRUE))", plain.condition)
        val own1 = DashboardData.Territory(setOf(locality.toString()), emptySet(), null)
        val own2 = DashboardData.Territory(emptySet(), setOf(community.toString()), null)
        val all = DashboardData.Territory(own1.localities, own2.communities, null)
        val scoped =
            AggregateSql.where(
                tables,
                Bound()::bind,
                source,
                AggregateFilter(listOf(p1, p2), emptyList(), ScopeTerritories(all, mapOf(p2 to own2, p1 to own1)))
            )
        assertTrue(scoped.tile.startsWith("COALESCE(x.\"locality\" = ANY("), scoped.tile)
        assertTrue(scoped.own.startsWith("(x.\"project\" = :g"), scoped.own)
        assertEquals(2, Regex("""\(x\."project" = :g\d+ AND COALESCE""").findAll(scoped.own).count(), scoped.own)
        assertTrue(scoped.condition.endsWith("AND ((${scoped.tile}) OR (${scoped.own}))"), scoped.condition)
    }

    @Test
    fun `las QCS cuentan con la regla de reclasificacion de los tres objetos y sin campos ilegibles`() {
        // inquiry no deja leer su vínculo con la queja ni su closed_date; nadie lee confidential
        val sources =
            Attention.OBJECTS.associateWith { type ->
                val links =
                    Attention.RECLASSIFIED_FROM.getValue(type).keys - (if (type == Attention.INQUIRY) setOf("reclassified_from_grievance") else emptySet())
                AggregateSource(
                    type,
                    setOf("project", "locality", "community", Attention.RECEIVED_AT) + links +
                        (if (type == Attention.INQUIRY) emptySet() else setOf("closed_date"))
                )
            }
        val query =
            AggregateSql.attention(
                sources,
                AggregateFilter(listOf(p1), emptyList()),
                EngagementKpis.months(LocalDate.parse("2026-09-30")),
                excludeConfidential = true
            )
        val (sql, values) = render(query)
        assertTrue(sql.startsWith("WITH q AS (SELECT "), sql)
        assertEquals(3, Regex("FROM <(grievance|inquiry|stakeholder_request)> x WHERE").findAll(sql).count(), sql)
        assertTrue(sql.contains("CAST(NULL AS uuid) AS from_grievance"), sql)
        assertTrue(sql.contains("LEFT JOIN (SELECT DISTINCT type, id FROM pointed WHERE tile) g ON g.type = q.type AND g.id = q.id "), sql)
        assertTrue(
            sql.contains(
                "LEFT JOIN (SELECT DISTINCT type, id, project FROM pointed WHERE own) o ON o.type = q.type AND o.id = q.id AND o.project = q.project)"
            ),
            sql
        )
        assertFalse(sql.contains("confidential"), sql)
        assertTrue(sql.endsWith("GROUP BY project, type, state, tile, own, gone, gone_own, received, closed"), sql)
        // solo la queja lleva los meses de la serie: de octubre de 2025 a septiembre de 2026, en Lima
        assertTrue(values.contains(OffsetDateTime.parse("2025-10-01T05:00Z")), values.toString())
        assertTrue(values.contains(OffsetDateTime.parse("2026-10-01T05:00Z")), values.toString())
        assertTrue(values.contains(LocalDate.parse("2025-10-01")) && values.contains(LocalDate.parse("2026-09-30")), values.toString())
        assertTrue("America/Lima" in values)
        assertEquals(sources.values.map { it.read() }, query.reads)
        assertFalse(
            query.reads
                .single { it.objectName == Attention.INQUIRY }
                .fields
                .contains("closed_date")
        )
    }

    @Test
    fun `excludeConfidential deja fuera las confidenciales solo si se lee la marca`() {
        val sources =
            Attention.OBJECTS.associateWith {
                AggregateSource(it, setOf("project", Attention.RECEIVED_AT, Attention.CONFIDENTIAL) + Attention.RECLASSIFIED_FROM.getValue(it).keys)
            }
        val months = EngagementKpis.months(LocalDate.parse("2026-09-30"))
        val (excluded, _) = render(AggregateSql.attention(sources, AggregateFilter(listOf(p1), emptyList()), months, excludeConfidential = true))
        assertEquals(3, Regex("""x\."confidential" IS NOT TRUE""").findAll(excluded).count(), excluded)
        val (kept, _) = render(AggregateSql.attention(sources, AggregateFilter(listOf(p1), emptyList()), months, excludeConfidential = false))
        assertFalse(kept.contains("IS NOT TRUE"), kept)
    }

    @Test
    fun `las interacciones van desde el primer dia del periodo o de la serie hasta el fin del periodo en Lima`() {
        val source = AggregateSource("engagement", setOf("project", "held_at", "engagement_type"))
        val query =
            AggregateSql.held(
                source,
                AggregateFilter(listOf(p1), emptyList()),
                setOf("held", "closed"),
                LocalDate.parse("2024-01-01"),
                LocalDate.parse("2026-09-30"),
                LocalDate.parse("2025-10-01")
            )
        val (sql, values) = render(query)
        assertTrue(sql.contains("CAST(x.\"engagement_type\" AS text) AS type"), sql)
        assertTrue(sql.endsWith("GROUP BY 1, 2, 3, 4, 5, 6"), sql)
        assertTrue(values.contains(OffsetDateTime.parse("2024-01-01T05:00Z")), values.toString())
        assertTrue(values.contains(OffsetDateTime.parse("2026-10-01T05:00Z")), values.toString())
        assertFalse(values.contains(OffsetDateTime.parse("2025-10-01T05:00Z")), values.toString())
        assertTrue(values.any { it is Array<*> && it.toList() == listOf("closed", "held") }, values.toString())
        // sin leer engagement_type, el tipo sale NULL y no se declara
        val blind = AggregateSource("engagement", setOf("project", "held_at"))
        assertTrue(
            render(
                AggregateSql.held(
                    blind,
                    AggregateFilter(listOf(p1), emptyList()),
                    setOf("held"),
                    LocalDate.parse("2026-09-01"),
                    LocalDate.parse("2026-09-30"),
                    LocalDate.parse("2025-10-01")
                )
            ).first.contains("CAST(NULL AS text) AS type")
        )
    }

    @Test
    fun `un conteo agrupado agrupa por proyecto, sus claves, tile y own`() {
        val source = AggregateSource("early_warning", setOf("project", "severity"))
        val query =
            AggregateSql.grouped(
                "warnings",
                source,
                AggregateFilter(listOf(p1), emptyList()),
                listOf("severity" to "text", SpatialGate.STATE to "text")
            ) { bind ->
                listOf("x.\"workflow_state\" = ANY(${bind(arrayOf("detected"))})")
            }
        val (sql, _) = render(query)
        assertTrue(
            sql.startsWith(
                "SELECT x.\"project\" AS project, CAST(x.\"severity\" AS text) AS k0, CAST(x.\"workflow_state\" AS text) AS k1, TRUE AS tile, TRUE AS own, count(*) AS n FROM <early_warning> x WHERE "
            ),
            sql
        )
        assertTrue(sql.endsWith(" AND x.\"workflow_state\" = ANY(:g1) GROUP BY 1, 2, 3, 4, 5"), sql)
        assertEquals("warnings", query.key)
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.AggregateSqlTest'
```

Esperado: falla la compilación (`AggregateSql`, `AggregateSource`, `AggregateFilter`, `ScopeTerritories` no existen).

- [ ] **Paso 3: La implementación**

Crear `src/main/kotlin/sgspe/dashboard/AggregateSql.kt`:

```kotlin
package sgspe.dashboard

import io.r2dbc.spi.Readable
import sgspe.common.Attention
import sgspe.common.LIMA
import sgspe.territory.GateRead
import sgspe.territory.GateTables
import sgspe.territory.SpatialGate
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

// una consulta agregada de los KPI por la compuerta de sgspe.territory: lo que nombra (GateRead), su SQL y cómo leer cada
// fila. `key` la distingue en la caché de DashboardData
class AggregateQuery<T : Any>(
    val key: String,
    val reads: List<GateRead>,
    val sql: GateTables.((Any) -> String) -> String,
    val map: (Readable) -> T
)

// un objeto en una consulta agregada: sus campos legibles por quien lee se declaran y salen como columna; los demás salen
// como NULL, lo mismo que hace RecordService al quitarlos de los atributos (nunca un no disponible que el camino de Records
// no daba). el proyecto, la campaña y el responsable se filtran en la base y quien arma la consulta exige que se lean
class AggregateSource(
    val objectName: String,
    val readable: Set<String>,
    val alias: String = "x"
) {
    fun read(): GateRead = GateRead(objectName, readable)

    // la columna del campo o null si no se lee
    fun column(
        t: GateTables,
        field: String
    ): String? = if (field in readable) "$alias.${t.column(objectName, field)}" else null

    fun system(
        t: GateTables,
        field: String
    ): String = "$alias.${t.column(objectName, field)}"
}

// el filtro que comparten las consultas agregadas: los proyectos, los filtros de territorio (todos se cumplen) y, con el ámbito
// AIDS o AIIS, el territorio del ámbito de todos los proyectos (las tiles) y el de cada uno (su fila de byProject, como
// DashboardData.with con ese proyecto solo)
class AggregateFilter(
    val projects: List<UUID>,
    val territories: List<DashboardData.Territory>,
    val scope: ScopeTerritories? = null
)

// el ámbito AIDS o AIIS: el territorio de todos los proyectos (las tiles, DashboardData.scopeTerritory) y el de cada uno (su
// fila de byProject, que antes salía de DashboardData.with con ese proyecto solo)
class ScopeTerritories(
    val all: DashboardData.Territory,
    val byProject: Map<UUID, DashboardData.Territory>
)

// el SQL de los conteos de los KPI del tablero executive (plan 8.2a-bis), puro: cada objeto como la subconsulta de la
// compuerta (organización y alcances de lectura), los proyectos y el territorio en el WHERE y un GROUP BY por proyecto
object AggregateSql {
    const val TILE = "tile"
    const val OWN = "own"

    // el registro cae en el territorio: su localidad entre las del territorio o su comunidad entre las suyas
    // (DashboardData.inTerritory). una columna ilegible no casa, como un atributo ausente en Kotlin; sin localidades ni
    // comunidades, nada casa
    internal fun inTerritory(
        locality: String?,
        community: String?,
        territory: DashboardData.Territory,
        bind: (Any) -> String
    ): String {
        val terms =
            listOfNotNull(
                locality?.takeIf { territory.localities.isNotEmpty() }?.let { "$it = ANY(${bind(uuids(territory.localities))})" },
                community?.takeIf { territory.communities.isNotEmpty() }?.let { "$it = ANY(${bind(uuids(territory.communities))})" }
            )
        return if (terms.isEmpty()) "FALSE" else "COALESCE(${terms.joinToString(" OR ")}, FALSE)"
    }

    private fun uuids(ids: Set<String>): Array<UUID> = ids.map(UUID::fromString).sorted().toTypedArray()

    // el WHERE común y las expresiones de tile y own
    internal class Where(
        val condition: String,
        val tile: String,
        val own: String
    )

    internal fun where(
        t: GateTables,
        bind: (Any) -> String,
        source: AggregateSource,
        filter: AggregateFilter
    ): Where {
        val project = source.column(t, "project") ?: error("${source.objectName}.project se exige legible antes de armar la consulta")
        val locality = source.column(t, "locality")
        val community = source.column(t, "community")
        val territories = filter.territories.map { inTerritory(locality, community, it, bind) }
        val scope = filter.scope
        val tile = scope?.let { inTerritory(locality, community, it.all, bind) } ?: "TRUE"
        val own =
            scope?.let { s ->
                s.byProject.entries
                    .sortedBy { it.key }
                    .joinToString(" OR ") { (p, territory) -> "($project = ${bind(p)} AND ${inTerritory(locality, community, territory, bind)})" }
                    .ifEmpty { "FALSE" }
            } ?: "TRUE"
        val terms = listOf("$project = ANY(${bind(filter.projects.toTypedArray())})") + territories + "(($tile) OR ($own))"
        return Where(terms.joinToString(" AND "), tile, own)
    }

    private fun flag(
        row: Readable,
        name: String
    ): Boolean = row.get(name) as Boolean? == true

    private fun count(row: Readable): Int = (row.get("n") as Number).toInt()

    private fun instant(at: Instant): OffsetDateTime = OffsetDateTime.ofInstant(at, ZoneOffset.UTC)

    private fun lima(day: LocalDate): OffsetDateTime = instant(day.atStartOfDay(LIMA).toInstant())

    // KPI-GRV-02 y las quejas de KPI-EXE-03: las QCS de los tres objetos, con la regla de conteo de §3.3 en la base. `months`
    // son los 12 meses de la serie (EngagementKpis.months); fuera de ellos el mes sale nulo
    fun attention(
        sources: Map<String, AggregateSource>,
        filter: AggregateFilter,
        months: List<YearMonth>,
        excludeConfidential: Boolean
    ): AggregateQuery<AttentionCount> {
        val pointers = Attention.OBJECTS.associateWith { "from_$it" }
        val sql: GateTables.((Any) -> String) -> String = { bind ->
            val branches =
                Attention.OBJECTS.map { type ->
                    val s = sources.getValue(type)
                    val w = where(this, bind, s, filter)
                    val links = Attention.RECLASSIFIED_FROM.getValue(type)
                    val from =
                        Attention.OBJECTS.joinToString(", ") { source ->
                            val field = links.entries.firstOrNull { it.value == source }?.key
                            (field?.let { s.column(this, it) } ?: "CAST(NULL AS uuid)") + " AS ${pointers.getValue(source)}"
                        }
                    val received = s.column(this, Attention.RECEIVED_AT)
                    val closedDate = s.column(this, "closed_date")
                    val state = s.system(this, SpatialGate.STATE)
                    val receivedMonth =
                        if (type == Attention.GRIEVANCE && received != null) {
                            "CASE WHEN $received >= ${bind(
                                lima(months.first().atDay(1))
                            )} AND $received < ${bind(lima(months.last().plusMonths(1).atDay(1)))} " +
                                "THEN to_char($received AT TIME ZONE ${bind(LIMA.id)}, 'YYYY-MM') END"
                        } else {
                            "CAST(NULL AS text)"
                        }
                    val closedMonth =
                        if (type == Attention.GRIEVANCE && closedDate != null) {
                            "CASE WHEN $state = ${bind(AttentionKpis.CLOSED)} AND $closedDate >= ${bind(months.first().atDay(1))} " +
                                "AND $closedDate <= ${bind(months.last().atEndOfMonth())} THEN to_char($closedDate, 'YYYY-MM') END"
                        } else {
                            "CAST(NULL AS text)"
                        }
                    val confidential =
                        s
                            .column(this, Attention.CONFIDENTIAL)
                            ?.takeIf { excludeConfidential }
                            ?.let { " AND $it IS NOT TRUE" }
                            .orEmpty()
                    "SELECT CAST(${bind(type)} AS text) AS type, ${s.system(this, SpatialGate.ID)} AS id, ${s.column(this, "project")} AS project, " +
                        "CAST($state AS text) AS state, ${w.tile} AS $TILE, ${w.own} AS $OWN, $from, $receivedMonth AS received, $closedMonth AS closed " +
                        "FROM ${table(s.objectName)} ${s.alias} WHERE ${w.condition}$confidential"
                }
            val pointed =
                Attention.OBJECTS.joinToString(" UNION ALL ") { source ->
                    val column = pointers.getValue(source)
                    "SELECT CAST(${bind(source)} AS text) AS type, $column AS id, project, $TILE, $OWN FROM q WHERE $column IS NOT NULL"
                }
            // los originales apuntados desde la lista de las tiles (g) y desde la de su mismo proyecto (o), sin repetir, por
            // hash join: nunca una subconsulta por fila
            "WITH q AS (${branches.joinToString(" UNION ALL ")}), pointed AS ($pointed), marked AS (" +
                "SELECT q.project, q.type, q.state, q.$TILE, q.$OWN, g.id IS NOT NULL AS gone, o.id IS NOT NULL AS gone_own, q.received, q.closed " +
                "FROM q LEFT JOIN (SELECT DISTINCT type, id FROM pointed WHERE $TILE) g ON g.type = q.type AND g.id = q.id " +
                "LEFT JOIN (SELECT DISTINCT type, id, project FROM pointed WHERE $OWN) o ON o.type = q.type AND o.id = q.id AND o.project = q.project) " +
                "SELECT project, type, state, $TILE, $OWN, gone, gone_own, received, closed, count(*) AS n FROM marked " +
                "GROUP BY project, type, state, $TILE, $OWN, gone, gone_own, received, closed"
        }
        return AggregateQuery(
            "attention:$excludeConfidential",
            Attention.OBJECTS.map { sources.getValue(it).read() },
            sql
        ) { row ->
            AttentionCount(
                row.get("project", UUID::class.java)!!.toString(),
                row.get("type", String::class.java)!!,
                row.get("state", String::class.java),
                flag(row, TILE),
                flag(row, OWN),
                flag(row, "gone"),
                flag(row, "gone_own"),
                row.get("received", String::class.java)?.let(YearMonth::parse),
                row.get("closed", String::class.java)?.let(YearMonth::parse),
                count(row)
            )
        }
    }

    // KPI-ENG-01 y la serie de KPI-EXE-03: las interacciones realizadas (EngagementWriteGuard.HELD_STATES) con held_at entre
    // el primer día del periodo o de la serie (el que vaya antes) y el fin del periodo, por proyecto, tipo, mes de Lima y si
    // caen en el periodo
    fun held(
        source: AggregateSource,
        filter: AggregateFilter,
        states: Set<String>,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        windowStart: LocalDate
    ): AggregateQuery<HeldCount> {
        val sql: GateTables.((Any) -> String) -> String = { bind ->
            val w = where(this, bind, source, filter)
            val heldAt = source.column(this, "held_at") ?: error("engagement.held_at se exige legible")
            val until = bind(lima(periodEnd.plusDays(1)))
            "SELECT ${source.column(this, "project")} AS project, CAST(${source.column(this, "engagement_type") ?: "NULL"} AS text) AS type, " +
                "to_char($heldAt AT TIME ZONE ${bind(LIMA.id)}, 'YYYY-MM') AS month, " +
                "($heldAt >= ${bind(lima(periodStart))} AND $heldAt < $until) AS in_period, ${w.tile} AS $TILE, ${w.own} AS $OWN, count(*) AS n " +
                "FROM ${table(source.objectName)} ${source.alias} WHERE ${w.condition} " +
                "AND ${source.system(this, SpatialGate.STATE)} = ANY(${bind(states.sorted().toTypedArray())}) " +
                "AND $heldAt >= ${bind(lima(minOf(periodStart, windowStart)))} AND $heldAt < $until " +
                "GROUP BY 1, 2, 3, 4, 5, 6"
        }
        return AggregateQuery("held:$periodStart:$periodEnd", listOf(source.read()), sql) { row ->
            HeldCount(
                row.get("project", UUID::class.java)!!.toString(),
                row.get("type", String::class.java),
                YearMonth.parse(row.get("month", String::class.java)!!),
                flag(row, "in_period"),
                flag(row, TILE),
                flag(row, OWN),
                count(row)
            )
        }
    }

    // un conteo por proyecto y por `keys` (campos del objeto, legibles o NULL, como texto salvo los DATE) de los registros que
    // pasan el filtro y `conditions`
    fun grouped(
        key: String,
        source: AggregateSource,
        filter: AggregateFilter,
        keys: List<Pair<String, String>>,
        conditions: GateTables.((Any) -> String) -> List<String>
    ): AggregateQuery<GroupCount> {
        val sql: GateTables.((Any) -> String) -> String = { bind ->
            val w = where(this, bind, source, filter)
            val columns =
                keys.mapIndexed { i, (field, type) ->
                    val column = if (field == SpatialGate.STATE) source.system(this, field) else source.column(this, field) ?: "NULL"
                    "CAST($column AS $type) AS k$i"
                }
            val extra = conditions(bind).joinToString("") { " AND $it" }
            val groups = (1..keys.size + 3).joinToString(", ")
            "SELECT ${source.column(
                this,
                "project"
            )} AS project, ${(columns + listOf("${w.tile} AS $TILE", "${w.own} AS $OWN")).joinToString(", ")}, count(*) AS n " +
                "FROM ${table(source.objectName)} ${source.alias} WHERE ${w.condition}$extra GROUP BY $groups"
        }
        return AggregateQuery(key, listOf(source.read()), sql) { row ->
            GroupCount(
                row.get("project", UUID::class.java)!!.toString(),
                keys.indices.map { row.get("k$it") },
                flag(row, TILE),
                flag(row, OWN),
                count(row)
            )
        }
    }
}
```

Notas para quien implementa: las columnas son las de la metadata (`GateTables.column`, ya entrecomilladas); `DATETIME` es
`timestamptz`, `DATE` es `date`, `ENUM` y `TEXT` son `text` y `RELATION` es `uuid` en wasichai 1.2.0, de ahí los `CAST`. La
consulta de las QCS empieza con `WITH`, que `SpatialGate.requireReadOnly` acepta; ninguna lleva `;`.

- [ ] **Paso 4: En verde, build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.AggregateSqlTest'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `6 tests completed`, sin fallas; `BUILD SUCCESSFUL`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/AggregateSql.kt src/test/kotlin/sgspe/dashboard/AggregateSqlTest.kt
git commit -m "feat: SQL de los conteos del tablero executive" -m "AggregateSql cuenta por proyecto sobre las tablas de la compuerta: las QCS con la regla de
reclasificación, las interacciones por mes de Lima y los conteos agrupados, con tile y own.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-bis-agregados && git merge --no-ff f82abis/t2 -m "Merge branch 'f82abis/t2' into feat/fase-8-2a-bis-agregados"
```

---

### Tarea 3: `KpiAggregates` por la compuerta y su vigía

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/KpiAggregates.kt`
- Modify: `src/test/kotlin/sgspe/ArchitectureTest.kt`
- Test: `src/test/kotlin/sgspe/dashboard/KpiAggregatesTest.kt` (nuevo)

**Interfaces:**
- Consumes: `SpatialGate.select` y `selectAsPlatform`, el constructor `internal` de `SpatialGate` y `GateRunner` (en la
  prueba); `AggregateQuery` (Tarea 2).
- Produces: `@Component class KpiAggregates(gate: SpatialGate)` con `suspend fun <T : Any> select(query: AggregateQuery<T>,
  platform: UUID?): GateOutcome<T>`.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-bis-agregados && git switch -c f82abis/t3
```

- [ ] **Paso 2: Las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/KpiAggregatesTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import sgspe.territory.GateOutcome
import sgspe.territory.GateRunner
import sgspe.territory.SpatialGate
import wasichai.core.data.RecordCriterion
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// plan 8.2a-bis (Review Focus 1): los conteos del tablero executive corren por la compuerta como la persona, con sus alcances
// de lectura en la tabla de cada objeto, o como la plataforma por selectAsPlatform, sin alcances y nunca con una persona en el
// contexto
class KpiAggregatesTest {
    private val organization = UUID.randomUUID()
    private val officer = AuthenticatedUser(UUID.randomUUID(), organization, "relacionista@sgspe.test", listOf("COMMUNITY_RELATIONS_OFFICER"))
    private val project = UUID.randomUUID()

    private val warning: ObjectDefinition =
        UUID.randomUUID().let { objectId ->
            ObjectDefinition(
                CustomObject(objectId, organization, "early_warning", "Alerta", "Alertas", null, true, "early_warning_tbl", null, null),
                listOf("project", "severity", "locality", "community").mapIndexed { i, field ->
                    CustomField(
                        id = UUID.randomUUID(),
                        objectId = objectId,
                        name = field,
                        label = field,
                        type = FieldType.TEXT,
                        columnName = "c_$field",
                        required = false,
                        unique = false,
                        defaultValue = null,
                        description = null,
                        position = i,
                        enumOptions = null,
                        relationTargetObjectId = null,
                        attributes = emptyMap(),
                        visible = true,
                        editable = true
                    )
                }
            )
        }

    private class Captured {
        var sql: String? = null
        val scoped = mutableListOf<String>()
    }

    private fun aggregates(
        captured: Captured,
        userOrNull: suspend () -> AuthenticatedUser?
    ) = KpiAggregates(
        SpatialGate(
            { officer },
            { { true } },
            { _, _ -> warning },
            { _, definition ->
                captured.scoped += definition.obj.name
                listOf(RecordCriterion { _, bind -> "c_project = ANY(${bind(arrayOf(project))})" })
            },
            { null },
            { "\"data\".\"${it.obj.physicalTable}\"" },
            GateRunner { sql, _, _ ->
                captured.sql = sql
                emptyList()
            },
            userOrNull
        )
    )

    private val query =
        AggregateSql.grouped(
            "warnings",
            AggregateSource("early_warning", setOf("project", "severity")),
            AggregateFilter(listOf(project), emptyList()),
            listOf("severity" to "text")
        ) { emptyList() }

    @Test
    fun `como la persona la tabla lleva su alcance de lectura`() {
        val captured = Captured()
        val outcome = runBlocking { aggregates(captured) { officer }.select(query, platform = null) }
        assertEquals(GateOutcome.Rows(emptyList<GroupCount>()), outcome)
        assertEquals(listOf("early_warning"), captured.scoped)
        // la tabla de la compuerta con la organización y el alcance, y después el filtro de los proyectos
        val table = Regex("""FROM \(SELECT \* FROM "data"\."early_warning_tbl" WHERE organization_id = :g\d+ AND \(c_project = ANY\(:g\d+\)\)\) x WHERE """)
        assertTrue(table.containsMatchIn(captured.sql!!), captured.sql)
    }

    @Test
    fun `como la plataforma no pide alcances y no corre con una persona en el contexto`() {
        val captured = Captured()
        runBlocking { aggregates(captured) { null }.select(query, platform = organization) }
        assertTrue(captured.scoped.isEmpty())
        assertFalse(captured.sql!!.contains("c_project = ANY"), captured.sql)
        val blocked = Captured()
        assertThrows<IllegalStateException> { runBlocking { aggregates(blocked) { officer }.select(query, platform = organization) } }
        assertNull(blocked.sql)
    }
}
```

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, reemplazar la prueba
`solo ComponentLand usa selectAsPlatform y solo DashboardData le pasa la plataforma` por:

```kotlin
    // decisión 12: la vía de plataforma de la compuerta solo la llaman ComponentLand y, desde el plan 8.2a-bis, KpiAggregates,
    // y la organización de la plataforma solo se la pasa DashboardData (la que arma KpiReaders): ningún endpoint ni otro
    // dominio lee SQL propio como la plataforma
    @Test
    fun `solo ComponentLand y KpiAggregates usan selectAsPlatform y solo DashboardData les pasa la plataforma`() {
        val gate = "/sgspe/territory/SpatialGate.kt"
        val land = "/sgspe/territory/ComponentLand.kt"
        val aggregates = "/sgspe/dashboard/KpiAggregates.kt"
        val data = "/sgspe/dashboard/DashboardData.kt"
        val notGateOrLand = sources(main).filterNot { f -> listOf(gate, land, aggregates).any { f.invariantSeparatorsPath.endsWith(it) } }
        val notLandOrData = sources(main).filterNot { f -> listOf(land, data).any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(notGateOrLand, SELECT_AS_PLATFORM))
        assertEquals(emptyList<String>(), offenders(notLandOrData, ROWS_WITH_PLATFORM))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(data) })
        assertTrue(flags(SELECT_AS_PLATFORM, "gate.selectAsPlatform(platform, READS, query, map)"))
        assertTrue(flags(ROWS_WITH_PLATFORM, "source.rows(filters.projects, platform)"))
        assertFalse(flags(ROWS_WITH_PLATFORM, "source.rows(filters.projects)"))
        // KpiAggregates solo lo usan DashboardData (con KpiContext.platform) y Dashboards (que lo inyecta)
        val notAggregatesUsers =
            sources(main).filterNot { f -> listOf(aggregates, data, "/sgspe/dashboard/Dashboards.kt").any { f.invariantSeparatorsPath.endsWith(it) } }
        assertEquals(emptyList<String>(), offenders(notAggregatesUsers, KPI_AGGREGATES))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(aggregates) })
        assertTrue(flags(KPI_AGGREGATES, "private val aggregates: KpiAggregates"))
        assertFalse(flags(KPI_AGGREGATES, "// los conteos de AggregateSql"))
    }
```

y en el `companion object`, después de `ROWS_WITH_PLATFORM`:

```kotlin
        private val KPI_AGGREGATES = Regex("""\bKpiAggregates\b""")
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.KpiAggregatesTest' --tests 'sgspe.ArchitectureTest'
```

Esperado: falla la compilación (`KpiAggregates` no existe).

- [ ] **Paso 3: La implementación**

Crear `src/main/kotlin/sgspe/dashboard/KpiAggregates.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.stereotype.Component
import sgspe.territory.GateOutcome
import sgspe.territory.SpatialGate
import java.util.UUID

// las consultas agregadas de los KPI (AggregateSql) por la compuerta de SQL propio (SpatialGate, especificación de F6 §3.1):
// como la persona del token, con su legibilidad y todos los RecordReadScope (el alcance por proyecto y el de las QCS
// confidenciales), o, con `platform` (KpiContext.platform, solo DashboardData lo pasa), por selectAsPlatform: sin alcances y
// con la organización enlazada, fuera de una petición (plan 8.2a-bis)
@Component
class KpiAggregates(
    private val gate: SpatialGate
) {
    suspend fun <T : Any> select(
        query: AggregateQuery<T>,
        platform: UUID?
    ): GateOutcome<T> =
        if (platform == null) {
            gate.select(query.reads, query.sql, query.map)
        } else {
            gate.selectAsPlatform(platform, query.reads, query.sql, query.map)
        }
}
```

- [ ] **Paso 4: En verde, build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.KpiAggregatesTest' --tests 'sgspe.ArchitectureTest'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `KpiAggregatesTest` 2 pruebas y `ArchitectureTest` en verde; `BUILD SUCCESSFUL`. Para ver fallar el vigía, agregar
a mano `gate.selectAsPlatform(x, emptyList(), { "" }) { it }` en `Dashboards.kt`, correr, ver la línea en el mensaje y
quitarla.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/KpiAggregates.kt src/test/kotlin/sgspe/dashboard/KpiAggregatesTest.kt \
  src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -m "feat: KpiAggregates por la compuerta de SQL propio" -m "Los conteos corren por SpatialGate como la persona, con sus alcances, o por selectAsPlatform fuera
de una petición; ArchitectureTest suma KpiAggregates a la regla de selectAsPlatform.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-bis-agregados && git merge --no-ff f82abis/t3 -m "Merge branch 'f82abis/t3' into feat/fase-8-2a-bis-agregados"
```

---

### Tarea 4: Los conteos de un pedido en `DashboardData`

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, `src/main/kotlin/sgspe/dashboard/RiskKpis.kt`
- Test: `src/test/kotlin/sgspe/dashboard/DashboardDataAggregatesTest.kt` (nuevo)

**Interfaces:**
- Consumes: `KpiAggregates` (Tarea 3), `AggregateSql` (Tarea 2), `territories()`, `scopeTerritory()`, `areaTerritory()`,
  `cached()`, `read()` (privadas de `DashboardData`), `EngagementWriteGuard.HELD_STATES`, `Commitments.CLOSED` y
  `PENDING`, `Risks.ESCALATED`, `ExecutiveKpis.ACTIVE_WARNING_STATES`.
- Produces: el parámetro `aggregates: KpiAggregates? = null` (último, opcional) de `DashboardData`, que `with(...)`
  propaga; `val aggregated: Boolean`; `suspend fun scopeTerritories(): ScopeTerritories?`; `attentionCounts()`,
  `heldCounts()`, `openCommitmentCounts()`, `overdueCommitmentCounts()`, `highRiskCounts()`, `warningCounts()` (cada una
  `GateOutcome<…>`, cacheada por pedido); `RiskKpis.ACTIVE_STATES`.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-bis-agregados && git switch -c f82abis/t4
```

- [ ] **Paso 2: La prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/DashboardDataAggregatesTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import sgspe.common.ModelCatalog
import sgspe.common.Records
import sgspe.territory.GateOutcome
import sgspe.territory.GateRunner
import sgspe.territory.SpatialGate
import wasichai.core.common.ValidationException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.time.LocalDate
import java.util.UUID

// plan 8.2a-bis: los conteos de DashboardData por la compuerta, sin base. un campo que quien lee no puede leer no se declara
// ni se nombra (sale NULL, como en Records); un campo por el que se filtra en la base tiene que leerse (el mismo 400 de
// Criteria); sin proyectos no corre nada; cada consulta corre una vez por pedido; con la plataforma va por selectAsPlatform
class DashboardDataAggregatesTest {
    private val organization = UUID.randomUUID()
    private val person = AuthenticatedUser(UUID.randomUUID(), organization, "analista@sgspe.test", listOf("ANALYST"))
    private val catalog = ModelCatalog.fromClasspath()
    private val sql = mutableListOf<String>()

    // la definición de cada objeto con los campos del modelo y los de sus relaciones, columna c_<campo>
    private fun definition(objectName: String): ObjectDefinition {
        val objectId = UUID.randomUUID()
        val relations = catalog.relationships.filter { it.source == objectName }.mapNotNull { it.fieldName }
        return ObjectDefinition(
            CustomObject(objectId, organization, objectName, objectName, objectName, null, true, "${objectName}_tbl", null, null),
            (
                catalog.objects
                    .getValue(objectName)
                    .fields.keys + relations
            ).distinct().mapIndexed { i, name ->
                CustomField(UUID.randomUUID(), objectId, name, name, FieldType.TEXT, "c_$name", false, false, null, null, i, null, null, emptyMap(), true, true)
            }
        )
    }

    private fun aggregates(user: AuthenticatedUser?) =
        KpiAggregates(
            SpatialGate(
                { person },
                { { true } },
                { _, name -> definition(name) },
                { _, _ -> emptyList() },
                { null },
                { "\"data\".\"${it.obj.physicalTable}\"" },
                GateRunner { text, _, _ ->
                    sql += text
                    emptyList()
                },
                { user }
            )
        )

    private fun data(
        unreadable: Set<String> = emptySet(),
        projects: List<UUID> = listOf(UUID.randomUUID()),
        excludeConfidential: Boolean = false,
        platform: UUID? = null
    ) = DashboardData(
        mock(Records::class.java),
        DashboardFilters.of(projects, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
        { it !in unreadable },
        LocalDate.parse("2026-10-14"),
        platform = platform,
        excludeConfidential = excludeConfidential,
        aggregates = aggregates(if (platform == null) person else null)
    )

    @Test
    fun `un campo ilegible no se nombra y uno por el que se filtra es el 400 de Criteria`() {
        runBlocking { data(unreadable = setOf("engagement.engagement_type")).heldCounts() }
        assertFalse(sql.single().contains("c_engagement_type"), sql.single())
        assertTrue(sql.single().contains("CAST(NULL AS text) AS type"), sql.single())
        val error = assertThrows<ValidationException> { runBlocking { data(unreadable = setOf("engagement.held_at")).heldCounts() } }
        assertEquals("Filtro inválido", error.message)
    }

    @Test
    fun `excludeConfidential deja fuera las QCS confidenciales en los tres objetos`() {
        runBlocking { data(excludeConfidential = true).attentionCounts() }
        assertEquals(3, Regex("""x\."c_confidential" IS NOT TRUE""").findAll(sql.single()).count(), sql.single())
        sql.clear()
        runBlocking { data().attentionCounts() }
        assertFalse(sql.single().contains("IS NOT TRUE"), sql.single())
    }

    @Test
    fun `sin proyectos no corre nada y cada consulta corre una vez por pedido`() {
        assertEquals(GateOutcome.Rows(emptyList<GroupCount>()), runBlocking { data(projects = emptyList()).warningCounts() })
        assertTrue(sql.isEmpty())
        val once = data()
        runBlocking {
            once.attentionCounts()
            once.attentionCounts()
            once.highRiskCounts()
        }
        assertEquals(2, sql.size)
    }

    @Test
    fun `con la plataforma los conteos van por selectAsPlatform`() {
        runBlocking { data(platform = organization).openCommitmentCounts() }
        assertTrue(sql.single().contains("organization_id = :g"), sql.single())
        // con una persona en el contexto, la vía de la plataforma no corre
        val leaked =
            DashboardData(
                mock(Records::class.java),
                DashboardFilters.of(listOf(UUID.randomUUID()), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
                { true },
                LocalDate.parse("2026-10-14"),
                platform = organization,
                aggregates = aggregates(person)
            )
        assertThrows<IllegalStateException> { runBlocking { leaked.openCommitmentCounts() } }
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.DashboardDataAggregatesTest'
```

Esperado: falla la compilación (`DashboardData` no tiene `aggregates` ni los conteos).

- [ ] **Paso 3: La implementación**

En `src/main/kotlin/sgspe/dashboard/RiskKpis.kt`, después de `const val WEEKS = 12L`:

```kotlin

    // los estados de un riesgo activo (todos salvo closed, dashboards.md §3.2)
    val ACTIVE_STATES: Set<String> = setOf("identified", "assessed", "mitigating", "monitoring")
```

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`:

1. Imports: `sgspe.risk.Risks`, `sgspe.territory.SpatialGate` y `wasichai.core.common.ValidationException` (ktlintFormat
   los ordena).
2. El último parámetro del constructor pasa a ser:

```kotlin
    // F8 (decisión 25, R4): el cierre de periodo calcula los KPI de QCS sin las confidenciales, también para quien las ve
    private val excludeConfidential: Boolean = false,
    // plan 8.2a-bis: los conteos en la base de los KPI del tablero executive (KpiAggregates); null, esos KPI leen filas con
    // Records como antes (las pruebas sin base y la prueba de paridad)
    private val aggregates: KpiAggregates? = null
) {
```

3. En `risks()`, `Criteria.inState(setOf("identified", "assessed", "mitigating", "monitoring"))` pasa a
   `Criteria.inState(RiskKpis.ACTIVE_STATES)`.
4. `with(filters)` pasa a:

```kotlin
    fun with(filters: DashboardFilters): DashboardData =
        DashboardData(records, filters, readable, today, maxRows, maxRelated, componentLand, platform, hasCatalog, excludeConfidential, aggregates)
```

5. Antes de `// ---- monitoreo (F8: dashboards.md §5.5 y §10) ----`, la sección nueva:

```kotlin
    // ---- conteos en la base (plan 8.2a-bis: KPI-GRV-02, KPI-ENG-01, KPI-COM-02, KPI-COM-03, KPI-RSK-01, KPI-EXE-02 y
    // KPI-EXE-03) ----

    // si los KPI del tablero executive cuentan en la base; si no, leen filas como antes
    val aggregated: Boolean get() = aggregates != null

    // el ámbito AIDS o AIIS de las tiles y de cada proyecto (ScopeTerritories); null si el ámbito no filtra
    suspend fun scopeTerritories(): ScopeTerritories? {
        val scope = filters.scope
        if (scope != AIDS && scope != AIIS) return null
        return cached("scopeTerritories") {
            val areas =
                read(
                    "area_of_influence",
                    mapOf("area_type" to scope),
                    listOf(Criteria.inState(setOf(APPROVED)), Criteria.anyOf("project", filters.projects))
                )
            ScopeTerritories(
                scopeTerritory().single(),
                filters.projects.associateWith { p -> areaTerritory(areas.filter { Values.text(it.attributes["project"]) == p.toString() }.map { it.id }) }
            )
        }
    }

    // un objeto de una consulta agregada: los campos de `filtered` se filtran en la base y, como en Criteria, tienen que
    // leerse (si no, el mismo 400 de un filtro sobre un campo ilegible); de `fields`, solo los legibles salen como columna
    private suspend fun source(
        objectName: String,
        filtered: List<String>,
        fields: List<String>
    ): AggregateSource {
        for (field in filtered) {
            if (!readable("$objectName.$field")) throw ValidationException("Filtro inválido", field, "no es un campo de $objectName o no puede leerlo")
        }
        return AggregateSource(objectName, (filtered + fields.filter { readable("$objectName.$it") }).toSet())
    }

    private suspend fun aggregateFilter(withScope: Boolean): AggregateFilter =
        AggregateFilter(filters.projects, territories(), if (withScope) scopeTerritories() else null)

    private suspend fun <T : Any> aggregate(query: AggregateQuery<T>): GateOutcome<T> {
        val source = aggregates ?: error("DashboardData sin KpiAggregates: Dashboards lo inyecta para los conteos en la base")
        return cached("aggregate:${query.key}") { source.select(query, platform) }
    }

    // las QCS de los tres objetos con la regla de conteo, la marca de abiertas y los meses de las quejas (KPI-GRV-02 y
    // KPI-EXE-03), con el territorio y el ámbito como attention()
    suspend fun attentionCounts(): GateOutcome<AttentionCount> {
        if (filters.projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val sources =
            Attention.OBJECTS.associateWith { type ->
                source(
                    type,
                    listOf("project"),
                    listOf("locality", "community", Attention.RECEIVED_AT, "closed_date", Attention.CONFIDENTIAL) +
                        Attention.RECLASSIFIED_FROM.getValue(type).keys
                )
            }
        return aggregate(AggregateSql.attention(sources, aggregateFilter(withScope = true), EngagementKpis.months(filters.periodEnd), excludeConfidential))
    }

    // las interacciones realizadas del periodo y de los 12 meses de la serie (KPI-ENG-01 y KPI-EXE-03), como inPeriod() y window()
    suspend fun heldCounts(): GateOutcome<HeldCount> {
        if (filters.projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val source = source("engagement", listOf("project", "held_at"), listOf("engagement_type", "locality", "community"))
        val windowStart = EngagementKpis.months(filters.periodEnd).first().atDay(1)
        return aggregate(
            AggregateSql.held(source, aggregateFilter(withScope = true), EngagementWriteGuard.HELD_STATES, filters.periodStart, filters.periodEnd, windowStart)
        )
    }

    // los compromisos abiertos (KPI-COM-02), con la campaña pedida como commitments(campaign = true, owner = false)
    suspend fun openCommitmentCounts(): GateOutcome<GroupCount> {
        if (filters.projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val campaign = filters.explorationCampaign
        val source = source(Commitments.COMMITMENT, listOfNotNull("project", campaign?.let { "exploration_campaign" }), listOf("locality", "community"))
        val query =
            AggregateSql.grouped("openCommitments", source, aggregateFilter(withScope = true), emptyList()) { bind ->
                val state = source.system(this, SpatialGate.STATE)
                listOfNotNull(
                    "($state IS NULL OR $state <> ALL(${bind(Commitments.CLOSED.sorted().toTypedArray())}))",
                    campaign?.let { "${source.column(this, "exploration_campaign")} = ${bind(it)}" }
                )
            }
        return aggregate(query)
    }

    // los compromisos pendientes vencidos por proyecto y due_date (KPI-COM-03), con la campaña y el responsable pedidos como
    // commitments(campaign = true, owner = true)
    suspend fun overdueCommitmentCounts(): GateOutcome<GroupCount> {
        if (filters.projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val campaign = filters.explorationCampaign
        val owner = filters.owner
        val filtered = listOfNotNull("project", campaign?.let { "exploration_campaign" }, owner?.let { "owner_user_id" })
        val source = source(Commitments.COMMITMENT, filtered, listOf("locality", "community", "deadline_status", "due_date"))
        val query =
            AggregateSql.grouped("overdueCommitments", source, aggregateFilter(withScope = true), listOf("due_date" to "date")) { bind ->
                listOfNotNull(
                    "${source.system(this, SpatialGate.STATE)} = ANY(${bind(Commitments.PENDING.sorted().toTypedArray())})",
                    source.column(this, "deadline_status")?.let { "CAST($it AS text) = ${bind(CommitmentKpis.OVERDUE)}" } ?: "FALSE",
                    campaign?.let { "${source.column(this, "exploration_campaign")} = ${bind(it)}" },
                    owner?.let { "${source.column(this, "owner_user_id")} = ${bind(it)}" }
                )
            }
        return aggregate(query)
    }

    // los riesgos activos con nivel ALTO o CRITICO por proyecto y categoría (KPI-RSK-01), como risks()
    suspend fun highRiskCounts(): GateOutcome<GroupCount> {
        if (filters.projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val source = source("social_risk", listOf("project"), listOf("locality", "community", "risk_level", "category"))
        val query =
            AggregateSql.grouped("highRisks", source, aggregateFilter(withScope = true), listOf("category" to "text")) { bind ->
                listOf(
                    "${source.system(this, SpatialGate.STATE)} = ANY(${bind(RiskKpis.ACTIVE_STATES.sorted().toTypedArray())})",
                    source.column(this, "risk_level")?.let { "CAST($it AS text) = ANY(${bind(Risks.ESCALATED.sorted().toTypedArray())})" } ?: "FALSE"
                )
            }
        return aggregate(query)
    }

    // las alertas activas por proyecto, severidad y estado (KPI-EXE-02), en el territorio pedido y sin el ámbito, como
    // earlyWarnings()
    suspend fun warningCounts(): GateOutcome<GroupCount> {
        if (filters.projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val source = source("early_warning", listOf("project"), listOf("locality", "community", "severity"))
        val query =
            AggregateSql.grouped("warnings", source, aggregateFilter(withScope = false), listOf("severity" to "text", SpatialGate.STATE to "text")) { bind ->
                listOf("${source.system(this, SpatialGate.STATE)} = ANY(${bind(ExecutiveKpis.ACTIVE_WARNING_STATES.sorted().toTypedArray())})")
            }
        return aggregate(query)
    }

    // ---- monitoreo
```

(La última línea del bloque, `// ---- monitoreo`, es la que ya existe: la sección va justo antes.)

Notas: cada conteo aplica el territorio que hoy aplica su conjunto de filas (`attention()`, `held(...)`, `commitments(...)`
y `risks()` usan `engagementTerritories()`, es decir, `territories()` más el ámbito; `earlyWarnings()` solo
`territories()`, de ahí `withScope = false` en `warningCounts`). Los filtros por estado y por campo que hoy hace Kotlin
(`state in PENDING`, `deadline_status == VENCIDO`, `risk_level in ESCALATED`) van al `WHERE`; si el campo no se lee, la
condición es `FALSE`, como un atributo ausente.

- [ ] **Paso 4: En verde, build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.DashboardDataAggregatesTest' --tests 'sgspe.dashboard.*Test'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `DashboardDataAggregatesTest` 4 pruebas y las demás unitarias de `sgspe.dashboard` en verde; `BUILD SUCCESSFUL`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/DashboardData.kt src/main/kotlin/sgspe/dashboard/RiskKpis.kt \
  src/test/kotlin/sgspe/dashboard/DashboardDataAggregatesTest.kt
git commit -m "feat: conteos en la base en DashboardData" -m "DashboardData cuenta en la base por KpiAggregates las QCS, las interacciones, los compromisos, los
riesgos y las alertas del tablero executive, con su territorio y su ámbito, una vez por pedido.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-bis-agregados && git merge --no-ff f82abis/t4 -m "Merge branch 'f82abis/t4' into feat/fase-8-2a-bis-agregados"
```

---

### Tarea 5: Los KPI, `byProject[]` y la propiedad `sgspe.dashboards.aggregates`

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `src/main/kotlin/sgspe/dashboard/KpiEvaluation.kt`,
  `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, `src/main/kotlin/sgspe/dashboard/DashboardsProperties.kt`,
  `src/main/resources/application.yml`, `src/test/kotlin/sgspe/dashboard/DashboardsPropertiesTest.kt`
- Test: `src/test/kotlin/sgspe/dashboard/KpiEvaluationByProjectTest.kt` (nuevo)

**Interfaces:**
- Consumes: los conteos de `DashboardData` (Tarea 4), `AggregateKpis` (Tarea 1), `KpiAggregates` (Tarea 3).
- Produces: `Indicator.computeByProject(data): Map<String, IndicatorResult>?` (por defecto `null`) y el parámetro
  `split` de `Kpi` (antes de `block`); `KpiEvaluation.evaluateByProject(kpi, data): Map<String, IndicatorValue>`;
  `DashboardsProperties.aggregates: Boolean = true` (`sgspe.dashboards.aggregates`); `internal suspend fun
  Dashboards.dashboard(name, params, reader: KpiReader?, aggregated: Boolean)` y `internal suspend fun
  Dashboards.value(code, projects, start, end, reader, excludeConfidential: Boolean, aggregated: Boolean)`. Las firmas
  públicas de `Dashboards` no cambian.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-bis-agregados && git switch -c f82abis/t5
```

- [ ] **Paso 2: Las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/KpiEvaluationByProjectTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import java.time.LocalDate
import java.util.UUID

// plan 8.2a-bis: la tabla por proyecto con una cifra por proyecto de una vez (Indicator.computeByProject), la misma
// disponibilidad en todas las filas y, sin ella, cada proyecto evaluado aparte con DashboardData.with, como antes
class KpiEvaluationByProjectTest {
    private val a = UUID.fromString("00000000-0000-0000-0000-00000000000a")
    private val b = UUID.fromString("00000000-0000-0000-0000-00000000000b")

    private fun data(readable: (String) -> Boolean = { true }) =
        DashboardData(
            mock(Records::class.java),
            DashboardFilters.of(listOf(a, b), LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")),
            readable,
            LocalDate.parse("2026-10-14")
        )

    private val calls = mutableListOf<List<UUID>>()

    private fun kpi(split: (suspend (DashboardData) -> Map<String, IndicatorResult>)?) =
        Kpi("KPI-PRUEBA", "Prueba", null, "registros", listOf("grievance.project"), split = split) { data ->
            calls += data.filters.projects
            IndicatorResult(data.filters.projects.size * 10, breakdowns = listOf(Breakdown("x", emptyList())))
        }

    @Test
    fun `con computeByProject una sola llamada da todas las filas`() {
        val split = kpi { data -> data.filters.projects.associate { it.toString() to IndicatorResult(if (it == a) 1 else 2) } }
        val rows = runBlocking { KpiEvaluation.evaluateByProject(split, data()) }
        assertEquals(mapOf(a.toString() to 1, b.toString() to 2), rows.mapValues { it.value.value })
        assertEquals(emptyList<List<UUID>>(), calls)
    }

    @Test
    fun `sin computeByProject cada proyecto se evalua solo`() {
        val rows = runBlocking { KpiEvaluation.evaluateByProject(kpi(null), data()) }
        assertEquals(mapOf(a.toString() to 10, b.toString() to 10), rows.mapValues { it.value.value })
        assertEquals(listOf(listOf(a), listOf(b)), calls)
    }

    @Test
    fun `sin leer lo que el KPI lee todas las filas salen no disponibles sin calcular`() {
        val rows = runBlocking { KpiEvaluation.evaluateByProject(kpi { error("no se calcula") }, data { it != "grievance.project" }) }
        assertEquals(setOf(a.toString(), b.toString()), rows.keys)
        rows.values.forEach {
            assertFalse(it.available)
            assertEquals(listOf("grievance.project"), it.missingFields)
        }
        assertEquals(emptyList<List<UUID>>(), calls)
    }
}
```

En `src/test/kotlin/sgspe/dashboard/DashboardsPropertiesTest.kt`, después de la prueba que hay:

```kotlin

    // plan 8.2a-bis: los KPI del tablero executive cuentan en la base por defecto, también en application.yml
    @Test
    fun `los conteos en la base vienen encendidos`() {
        assertEquals(true, DashboardsProperties().aggregates)
        val yml = File("src/main/resources/application.yml").readText()
        assertEquals(true, Regex("""max-projects: 25\s+(#[^\n]*\n\s*)*aggregates: true""").containsMatchIn(yml), "falta sgspe.dashboards.aggregates")
    }
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.KpiEvaluationByProjectTest' --tests 'sgspe.dashboard.DashboardsPropertiesTest'
```

Esperado: falla la compilación (`split`, `evaluateByProject` y `aggregates` no existen).

- [ ] **Paso 3: La propiedad**

`src/main/kotlin/sgspe/dashboard/DashboardsProperties.kt`: el comentario de la clase suma la línea
`// aggregates (plan 8.2a-bis): los KPI del tablero executive cuentan en la base; en false vuelven a leer filas con Records`
(la línea anterior termina en `decisión 24 del plan 8.2a).`) y la clase pasa a:

```kotlin
@ConfigurationProperties("sgspe.dashboards")
data class DashboardsProperties(
    val executive: Executive = Executive(),
    val aggregates: Boolean = true
) {
```

`src/main/resources/application.yml`, bajo `sgspe.dashboards`, después de `max-projects: 25`:

```yaml
    # plan 8.2a-bis: los KPI del tablero executive cuentan en la base (KpiAggregates); false vuelve a leer filas con Records
    aggregates: true
```

(dos espacios menos que `max-projects`: es hermano de `executive`). `yarn format` antes de commitear.

- [ ] **Paso 4: `Indicator`, `Kpi` y `KpiEvaluation`**

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `interface Indicator` suma, después de `compute`:

```kotlin

    // plan 8.2a-bis: la cifra de cada proyecto del filtro de una vez (la tabla por proyecto del tablero executive, solo con
    // los conteos en la base), con el id del proyecto como clave; null, cada proyecto se calcula aparte (DashboardData.with)
    suspend fun computeByProject(data: DashboardData): Map<String, IndicatorResult>? = null
```

y `class Kpi` suma el parámetro `split` antes de `block` y su `override`:

```kotlin
    override val ignores: Set<String> = emptySet(),
    // plan 8.2a-bis: Indicator.computeByProject; solo los KPI de ExecutiveKpis.BY_PROJECT lo traen
    private val split: (suspend (DashboardData) -> Map<String, IndicatorResult>)? = null,
    private val block: suspend (DashboardData) -> IndicatorResult
) : Indicator {
    override suspend fun compute(data: DashboardData): IndicatorResult = block(data)

    override suspend fun computeByProject(data: DashboardData): Map<String, IndicatorResult>? = split?.invoke(data)
}
```

En `src/main/kotlin/sgspe/dashboard/KpiEvaluation.kt`, antes de `reason(...)`:

```kotlin
    // la tabla por proyecto del tablero executive (plan 8.2a-bis): la misma disponibilidad para todas las filas y la cifra de
    // cada proyecto de Indicator.computeByProject; sin ella, cada proyecto se evalúa aparte con DashboardData.with, como antes.
    // la clave es el id del proyecto, una por proyecto del filtro
    suspend fun evaluateByProject(
        kpi: Indicator,
        data: DashboardData
    ): Map<String, IndicatorValue> {
        val projects = data.filters.projects
        val missing = kpi.requires.distinct().filterNot { data.readable(it) }
        val ignored = Kpis.ignoredFilters(kpi, data.filters).ifEmpty { null }

        fun value(result: IndicatorResult) = result.toValue(kpi.code, kpi.label, kpi.unit).copy(ignoredFilters = ignored)
        if (missing.isNotEmpty()) {
            val unavailable = value(IndicatorResult.unavailable(reason(missing, data), missing))
            return projects.associate { it.toString() to unavailable }
        }
        val split = if (kpi.singleProject) null else kpi.computeByProject(data)
        return projects.associate { p ->
            val key = p.toString()
            key to
                (
                    split?.let { value(it[key] ?: error("${kpi.code} no dio la cifra del proyecto $key")) }
                        ?: evaluate(kpi, data.with(data.filters.copy(projects = listOf(p))))
                )
        }
    }
```

- [ ] **Paso 5: Los KPI en `Kpis.ALL`**

En `object Kpis`, después de `EXECUTIVE_WARNING_IGNORES`, las dos ayudas:

```kotlin
    // un conteo de la base como resultado: no disponible si la compuerta no deja leer algo (no pasa tras KpiEvaluation, que ya
    // comprobó lo que el KPI lee, pero nunca un cero)
    private inline fun <T> counted(
        outcome: GateOutcome<T>,
        block: (List<T>) -> IndicatorResult
    ): IndicatorResult =
        when (outcome) {
            is GateOutcome.Rows -> block(outcome.rows)
            is GateOutcome.Unavailable -> IndicatorResult.unavailable(UnavailableReason.FIELD_NOT_READABLE, outcome.missingFields)
        }

    // lo mismo para la tabla por proyecto: no disponible en todas sus filas
    private inline fun <T> countedByProject(
        data: DashboardData,
        outcome: GateOutcome<T>,
        block: (List<T>, List<String>) -> Map<String, IndicatorResult>
    ): Map<String, IndicatorResult> {
        val projects = data.filters.projects.map { it.toString() }
        return when (outcome) {
            is GateOutcome.Rows -> block(outcome.rows, projects)
            is GateOutcome.Unavailable ->
                projects.associateWith { IndicatorResult.unavailable(UnavailableReason.FIELD_NOT_READABLE, outcome.missingFields) }
        }
    }
```

Y en `ALL`, cada KPI movido (el resto de su definición no cambia):

- **KPI-ENG-01** reemplaza `Kpi("KPI-ENG-01", "Interacciones realizadas", ENGAGEMENT, "interacciones", HELD, ignores =
  IGNORED_IN_F2) { EngagementKpis.held(it.inPeriod(), it.window(), it.filters.periodEnd) },` por:

```kotlin
            Kpi(
                "KPI-ENG-01",
                "Interacciones realizadas",
                ENGAGEMENT,
                "interacciones",
                HELD,
                ignores = IGNORED_IN_F2,
                split = { data -> countedByProject(data, data.heldCounts(), AggregateKpis::heldByProject) }
            ) { data -> heldResult(data) },
```

- **KPI-COM-02** reemplaza su `Kpi("KPI-COM-02", …) { CommitmentKpis.open(it.commitments(campaign = true, owner = false)) },`
  por:

```kotlin
            Kpi(
                "KPI-COM-02",
                "Compromisos abiertos",
                COMMITMENTS,
                "compromisos",
                COMMITMENT + CAMPAIGN,
                ignores = commitmentIgnores("explorationCampaign"),
                split = { data -> countedByProject(data, data.openCommitmentCounts(), AggregateKpis::groupedByProject) }
            ) { data ->
                if (data.aggregated) {
                    counted(data.openCommitmentCounts(), AggregateKpis::openCommitments)
                } else {
                    CommitmentKpis.open(data.commitments(campaign = true, owner = false))
                }
            },
```

- **KPI-COM-03**: `ignores = commitmentIgnores("explorationCampaign", "owner")` suma `split` y el cuerpo pasa a:

```kotlin
                ignores = commitmentIgnores("explorationCampaign", "owner"),
                split = { data -> countedByProject(data, data.overdueCommitmentCounts(), AggregateKpis::groupedByProject) }
            ) { data ->
                val escalation = data.escalation()
                if (data.aggregated) {
                    counted(
                        data.overdueCommitmentCounts()
                    ) { rows -> AggregateKpis.overdueCommitments(rows, data.today) { escalation[it] ?: Escalation(7, 15) } }
                } else {
                    CommitmentKpis.overdue(data.commitments(campaign = true, owner = true), data.today) { escalation[it] ?: Escalation(7, 15) }
                }
            },
```

- **KPI-GRV-02** reemplaza su línea `Kpi("KPI-GRV-02", "QCS abiertas", …) { AttentionKpis.open(it.attention()) },` por:

```kotlin
            Kpi(
                "KPI-GRV-02",
                "QCS abiertas",
                ATTENTION,
                "QCS",
                QCS,
                ignores = ATTENTION_IGNORES,
                split = { data -> countedByProject(data, data.attentionCounts(), AggregateKpis::openByProject) }
            ) { data -> if (data.aggregated) counted(data.attentionCounts(), AggregateKpis::open) else AttentionKpis.open(data.attention()) },
```

- **KPI-RSK-01** reemplaza su `Kpi("KPI-RSK-01", …) { RiskKpis.high(it.risks()) },` por:

```kotlin
            Kpi(
                "KPI-RSK-01",
                "Riesgos altos",
                RISKS,
                "riesgos",
                RISK + listOf("social_risk.risk_level", "social_risk.category"),
                ignores = RISK_IGNORES,
                split = { data -> countedByProject(data, data.highRiskCounts(), AggregateKpis::groupedByProject) }
            ) { data -> if (data.aggregated) counted(data.highRiskCounts(), AggregateKpis::highRisks) else RiskKpis.high(data.risks()) },
```

- **KPI-INV-03**: `ignores = INV_IGNORES + setOf("explorationCampaign", "program", "year")` pasa a

```kotlin
                ignores = INV_IGNORES + setOf("explorationCampaign", "program", "year"),
                split = { data -> executedByProject(data) }
```

  (el cálculo de las tiles no cambia: sigue en `Records`).
- **KPI-EXE-01**: `ignores = TERRITORY_IGNORES` pasa a

```kotlin
                ignores = TERRITORY_IGNORES,
                split = { data ->
                    val rows = data.projectRows()
                    data.filters.projects
                        .map { it.toString() }
                        .associateWith { p -> ExecutiveKpis.activeProjects(rows.filter { it.id == p }) }
                }
```

- **KPI-EXE-02**: `ignores = EXECUTIVE_WARNING_IGNORES` y su cuerpo pasan a

```kotlin
                ignores = EXECUTIVE_WARNING_IGNORES,
                split = { data -> countedByProject(data, data.warningCounts(), AggregateKpis::groupedByProject) }
            ) { data ->
                if (data.aggregated) counted(data.warningCounts(), AggregateKpis::activeWarnings) else ExecutiveKpis.activeWarnings(data.earlyWarnings())
            },
```

- **KPI-EXE-03**: el cuerpo pasa a

```kotlin
            ) { data ->
                val commitments = data.commitments(campaign = false, owner = false)
                val reschedules = data.reschedules(commitments.map { it.id })
                val overdue = CommitmentKpis.overdueSeries(commitments, reschedules.rows, data.filters.periodEnd, data.today)
                val engagements = heldResult(data)
                when {
                    !engagements.available -> engagements
                    data.aggregated ->
                        counted(data.attentionCounts()) { rows -> reschedules.warn(AggregateKpis.trend(engagements, rows, overdue, data.filters.periodEnd)) }
                    else -> reschedules.warn(ExecutiveKpis.trend(engagements, data.attention(), overdue, data.filters.periodEnd))
                }
            }
```

Y después de `val BY_CODE`, las dos funciones privadas:

```kotlin
    // KPI-INV-03 de cada proyecto del filtro con una sola lectura de cada conjunto, repartida por proyecto: lo mismo que leer
    // cada proyecto solo, porque cada conjunto se filtra por su proyecto (o por el de su padre) y la localidad (plan 8.2a-bis)
    private suspend fun executedByProject(data: DashboardData): Map<String, IndicatorResult> {
        val commitments = data.investmentCommitments()
        val contributions = data.investmentContributions()
        val withPrograms = data.filters.locality == null
        val programs = if (withPrograms) data.programs(program = false) else emptyList()
        val commitmentDeliveries = data.deliveriesOf("commitment", commitments.map { it.id })
        val contributionDeliveries = data.deliveriesOf(Contributions.CONTRIBUTION, contributions.map { it.id })
        val budgets = data.budgetsOf(programs.map { it.id })
        val campaigns = data.investmentCampaigns(campaign = false)
        val codes = data.names("project", data.filters.projects.map { it.toString() }, "code")
        return data.filters.projects.map { it.toString() }.associateWith { p ->
            val own = commitments.filter { it.project == p }
            val ownIds = own.map { it.id }.toSet()
            val gifts = contributions.filter { it.project == p }
            val giftIds = gifts.map { it.id }.toSet()
            val ownPrograms = programs.filter { it.project == p }
            val programIds = ownPrograms.map { it.id }.toSet()
            InvestmentKpis.executed(
                own,
                commitmentDeliveries.filter { it.parent in ownIds },
                gifts,
                contributionDeliveries.filter { it.parent in giftIds },
                ownPrograms,
                budgets.filter { it.program in programIds },
                campaigns.filter { it.project == p },
                data.filters.periodStart,
                data.filters.periodEnd,
                withPrograms,
                codes.filterKeys { it == p }
            )
        }
    }

    // KPI-ENG-01 (y la serie de interacciones de KPI-EXE-03): desde los conteos de la base o desde las filas
    private suspend fun heldResult(data: DashboardData): IndicatorResult =
        if (data.aggregated) {
            counted(data.heldCounts()) { AggregateKpis.held(it, data.filters.periodEnd) }
        } else {
            EngagementKpis.held(data.inPeriod(), data.window(), data.filters.periodEnd)
        }
```

Notas: con `data.aggregated` falso cada KPI hace exactamente lo de hoy. `executedByProject` reparte por proyecto lo que
`InvestmentKpis.executed` recibiría con ese proyecto solo (cada conjunto se filtra por su proyecto o por el de su padre, y
la localidad no depende del proyecto); los códigos de proyecto solo etiquetan desgloses, que `byProject` quita.

- [ ] **Paso 6: `Dashboards`**

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`:

1. El constructor suma `private val aggregates: KpiAggregates` al final.
2. `dashboard(...)` público delega en el `internal` con la propiedad, y el cuerpo de hoy pasa al `internal`:

```kotlin
    suspend fun dashboard(
        name: String,
        params: MultiValueMap<String, String>,
        reader: KpiReader? = null
    ): DashboardResponse = dashboard(name, params, reader, properties.aggregates)

    // `aggregated`: los KPI del tablero executive cuentan en la base (KpiAggregates, plan 8.2a-bis) o leen filas con Records
    // como antes; la prueba de paridad compara los dos caminos
    internal suspend fun dashboard(
        name: String,
        params: MultiValueMap<String, String>,
        reader: KpiReader?,
        aggregated: Boolean
    ): DashboardResponse {
```

   y dentro, `val data = data(ctx, filters, today)` pasa a `val data = data(ctx, filters, today, aggregated = aggregated)`.
3. En `indicator(...)`, `data(ctx, filters, today)` pasa a `data(ctx, filters, today, aggregated = properties.aggregates)`.
4. `value(...)` público delega en el `internal`:

```kotlin
    suspend fun value(
        code: String,
        projects: List<UUID>?,
        start: LocalDate,
        end: LocalDate,
        reader: KpiReader,
        excludeConfidential: Boolean = false
    ): IndicatorValue = value(code, projects, start, end, reader, excludeConfidential, properties.aggregates)

    internal suspend fun value(
        code: String,
        projects: List<UUID>?,
        start: LocalDate,
        end: LocalDate,
        reader: KpiReader,
        excludeConfidential: Boolean,
        aggregated: Boolean
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw notFound("INDICATOR_NOT_FOUND", "El indicador $code no existe o no está disponible en esta fase", "code")
        return readers.reading(reader) { ctx ->
            val filters = DashboardFilters.of(projects ?: ctx.defaultProjects(), start, end)
            val also = if (excludeConfidential) Kpis.closingRequires(code) else emptyList()
            mark(KpiEvaluation.evaluate(kpi, data(ctx, filters, today(), excludeConfidential, aggregated), also), ctx.confidentialHidden)
        }
    }
```

5. `byProject(...)` pasa a:

```kotlin
    // dashboards.md §5.1: una fila por proyecto del filtro con las cifras de ExecutiveKpis.BY_PROJECT, las mismas que daría el
    // filtro de ese proyecto solo, sin desgloses ni series. con los conteos en la base, una consulta por KPI agrupada por
    // proyecto (KpiEvaluation.evaluateByProject, plan 8.2a-bis); sin ellos, cada proyecto con DashboardData.with, como antes
    private suspend fun byProject(
        data: DashboardData,
        hidden: Boolean
    ): List<ProjectFigures> {
        val ids = data.filters.projects.map { it.toString() }
        val codes = data.names("project", ids, "code")
        val names = data.names("project", ids)
        val split =
            if (data.aggregated) {
                ExecutiveKpis.BY_PROJECT.associateWith { KpiEvaluation.evaluateByProject(Kpis.BY_CODE.getValue(it), data) }
            } else {
                null
            }
        return data.filters.projects.map { project ->
            val one by lazy { data.with(data.filters.copy(projects = listOf(project))) }
            ProjectFigures(
                project.toString(),
                codes[project.toString()],
                names[project.toString()],
                ExecutiveKpis.BY_PROJECT.map { code ->
                    val value = split?.getValue(code)?.getValue(project.toString()) ?: KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(code), one)
                    mark(value, hidden).copy(breakdowns = null, series = null)
                }
            )
        }
    }
```

6. `data(...)` suma `aggregated: Boolean = properties.aggregates` después de `excludeConfidential` y pasa
   `aggregates = if (aggregated) aggregates else null` a `DashboardData`.

- [ ] **Paso 7: En verde, build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.*Test' --tests 'sgspe.ArchitectureTest'
cd $REPO && yarn format && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `KpiEvaluationByProjectTest` 3 pruebas, `DashboardsPropertiesTest` 2 y todas las unitarias de `sgspe.dashboard`
(las de hoy sin tocar: `DashboardDataTest`, `ClosabilityKpisTest`, `ConfidentialExclusionTest`, `ExecutiveKpisTest`…) en
verde; `BUILD SUCCESSFUL` con las de integración compiladas. Integración pendiente (Tarea 8):
`sgspe.dashboard.ExecutiveDashboardApiTest`, `ProjectDashboardApiTest`, `AttentionDashboardApiTest`,
`CommitmentsDashboardApiTest`, `RisksDashboardApiTest`, `TerritoryDashboardApiTest`, `EngagementIndicatorsF4ApiTest`,
`IndicatorMeasurementsApiTest`, `DashboardDeliverableParityTest`, `DashboardsApiTest`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/Kpis.kt src/main/kotlin/sgspe/dashboard/KpiEvaluation.kt \
  src/main/kotlin/sgspe/dashboard/Dashboards.kt src/main/kotlin/sgspe/dashboard/DashboardsProperties.kt \
  src/main/resources/application.yml src/test/kotlin/sgspe/dashboard/KpiEvaluationByProjectTest.kt \
  src/test/kotlin/sgspe/dashboard/DashboardsPropertiesTest.kt
git commit -m "feat: tablero executive y byProject desde los conteos en la base" -m "Siete KPI del tablero executive cuentan en la base y byProject sale de una consulta agrupada por
proyecto; sgspe.dashboards.aggregates en false vuelve a la lectura con Records.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-bis-agregados && git merge --no-ff f82abis/t5 -m "Merge branch 'f82abis/t5' into feat/fase-8-2a-bis-agregados"
```

---

### Tarea 6: La paridad contra la base (`ExecutiveAggregatesParityApiTest`)

**Files:**
- Test: `src/test/kotlin/sgspe/dashboard/ExecutiveAggregatesParityApiTest.kt` (nuevo)

**Interfaces:**
- Consumes: `Dashboards.dashboard(…, aggregated)` y `Dashboards.value(…, aggregated)` (Tarea 5), `KpiReaders.person()` y
  `platform(...)`; de `F6QueryApiTest`: `utmProject`, `area`, `localityWith`, `areaMember`, `approveArea`, `rect`, `at`;
  de las bases anteriores: `member`, `engagement`, `participant`, `move`, `grievance`, `inquiry`, `platformPatch`,
  `commitment`, `socialRisk`, `earlyWarning`, `organizationId`, `JSON`, `tree`; `ReactiveJwtDecoder` como en
  `F7ApiTest.asUser`.
- Produces: la prueba que compara, sobre los mismos datos, el camino de `Records` y el de los conteos.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-bis-agregados && git switch -c f82abis/t6
```

- [ ] **Paso 2: La prueba**

Crear `src/test/kotlin/sgspe/dashboard/ExecutiveAggregatesParityApiTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.reactor.asCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.util.LinkedMultiValueMap
import sgspe.F6QueryApiTest
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.time.YearMonth
import java.util.UUID

// plan 8.2a-bis: los KPI del tablero executive contados en la base dan lo mismo que leyendo filas con Records, en las tiles y
// en byProject, como el ADMIN, como un relacionista asignado a un solo proyecto (alcance por proyecto y QCS confidencial
// oculta), como la plataforma, con locality, con el ámbito AIDS (donde las tiles y la fila de cada proyecto usan territorios
// distintos) y en el cierre sin las confidenciales (R4). también en los tableros project, attention, engagement, commitments,
// risks y territory (la ficha KPI-TER-01), que comparten esos KPI
class ExecutiveAggregatesParityApiTest : F6QueryApiTest() {
    @Autowired
    private lateinit var dashboards: Dashboards

    @Autowired
    private lateinit var readers: KpiReaders

    @Autowired
    private lateinit var jwtDecoder: ReactiveJwtDecoder

    private val month: YearMonth = YearMonth.now(LIMA).minusMonths(1)
    private val start = month.atDay(1)
    private val end = month.atEndOfMonth()

    // un servicio como la persona del token, como lo llama un controlador (F7ApiTest.asUser)
    private fun <T> asToken(
        token: String,
        block: suspend () -> T
    ): T {
        val jwt = jwtDecoder.decode(token.removePrefix("Bearer ")).block()!!
        return runBlocking(ReactiveSecurityContextHolder.withAuthentication(JwtAuthenticationToken(jwt)).asCoroutineContext()) { block() }
    }

    private fun json(value: Any): JsonNode = (tree(JSON.writeValueAsString(value)) as ObjectNode).also { it.remove("computedAt") }

    private fun params(query: Map<String, List<String>>) = LinkedMultiValueMap<String, String>().apply { query.forEach { (k, v) -> addAll(k, v) } }

    // el tablero por los dos caminos: como la persona del token o, sin token, como la plataforma (fuera de una petición)
    private fun parity(
        name: String,
        query: Map<String, List<String>>,
        token: String?
    ): JsonNode {
        fun run(aggregated: Boolean): JsonNode =
            if (token == null) {
                json(runBlocking { dashboards.dashboard(name, params(query), readers.platform(organizationId()), aggregated) })
            } else {
                json(asToken(token) { dashboards.dashboard(name, params(query), readers.person(), aggregated) })
            }
        val counted = run(aggregated = true)
        assertEquals(run(aggregated = false), counted, "$name $query")
        return counted
    }

    private fun held(
        project: String,
        type: String,
        day: Int,
        attributes: Map<String, Any?> = emptyMap(),
        monthOf: YearMonth = month
    ) {
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        engagement(project, type, officer.token, mapOf("held_at" to "${monthOf.atDay(day)}T15:00:00Z") + attributes).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
    }

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    private fun row(
        body: JsonNode,
        project: UUID,
        code: String
    ): JsonNode =
        body["byProject"].values().first { it["project"].asString() == project.toString() }["indicators"].values().first {
            it["code"].asString() ==
                code
        }

    @Test
    fun `los dos caminos dan las mismas cifras en las tiles, en byProject y en los tableros que comparten los KPI`() {
        val y = -140_000.0
        val a = runBlocking { utmProject() }
        val b = runBlocking { utmProject() }
        val area = area(a, boundary = rect(0.0, y, 1000.0, y + 1000, multi = true))
        val place = localityWith("Caserío Ejemplo", location = at(100.0, y + 100))
        areaMember(area, "locality", place)
        approveArea(area, a)
        val receivedAt = "${start.plusDays(3)}T15:00:00Z"
        // interacciones: en el periodo con y sin la localidad del área de A, y una de hace tres meses; B, una en la localidad
        held(a.toString(), "REUNION", 5, mapOf("locality" to place.toString()))
        held(a.toString(), "TALLER", 6)
        held(a.toString(), "REUNION", 7, monthOf = month.minusMonths(3))
        held(b.toString(), "REUNION", 8, mapOf("locality" to place.toString()))
        // QCS: una abierta, una confidencial, una consulta que reclasificó una queja (el original no cuenta) y una de B
        val receiver = member("COMMUNITY_RELATIONS_OFFICER", a.toString())
        grievance(a.toString(), receiver, mapOf("received_at" to receivedAt, "locality" to place.toString()))
        grievance(a.toString(), receiver, mapOf("received_at" to receivedAt, "confidential" to true))
        val original = grievance(a.toString(), receiver, mapOf("received_at" to receivedAt))
        platformPatch("inquiry", inquiry(a.toString(), receiver, mapOf("received_at" to receivedAt)), mapOf("reclassified_from_grievance" to original))
        grievance(b.toString(), attributes = mapOf("received_at" to receivedAt, "locality" to place.toString()))
        // un compromiso abierto, un riesgo ALTO y una alerta en cada proyecto
        commitment(a.toString())
        commitment(b.toString())
        platformPatch("social_risk", socialRisk(a.toString()), mapOf("risk_level" to "ALTO"))
        earlyWarning(a.toString())
        earlyWarning(b.toString())

        val both = mapOf("project" to listOf(a.toString(), b.toString()), "periodStart" to listOf(start.toString()), "periodEnd" to listOf(end.toString()))
        val officer = member("COMMUNITY_RELATIONS_OFFICER", a.toString())
        for (caller in listOf(token, officer.token, null)) {
            parity(Kpis.EXECUTIVE, both, caller)
        }
        parity(Kpis.EXECUTIVE, both + ("locality" to listOf(place.toString())), token)
        val aids = parity(Kpis.EXECUTIVE, both + ("scope" to listOf("AIDS")), token)
        // con AIDS, las tiles miran el territorio de los dos proyectos y cada fila el de su proyecto: B no tiene área aprobada
        assertEquals(2, indicator(aids, "KPI-ENG-01")["value"].asInt())
        assertEquals(1, row(aids, a, "KPI-ENG-01")["value"].asInt())
        assertEquals(0, row(aids, b, "KPI-ENG-01")["value"].asInt())
        val one = mapOf("project" to listOf(a.toString()), "periodStart" to listOf(start.toString()), "periodEnd" to listOf(end.toString()))
        for (name in listOf(Kpis.PROJECT, Kpis.ATTENTION, Kpis.ENGAGEMENT, Kpis.COMMITMENTS, Kpis.RISKS)) {
            parity(name, one, token)
            parity(name, one, officer.token)
        }
        // la ficha de la localidad (KPI-TER-01) reúne KPI-ENG-01, KPI-COM-02, KPI-COM-03 y KPI-GRV-02
        parity(Kpis.TERRITORY, one + ("locality" to listOf(place.toString())), token)
        // el cierre (R4): sin las confidenciales, también para la plataforma
        for (code in listOf("KPI-GRV-02", ExecutiveKpis.TREND, "KPI-ENG-01", "KPI-COM-02", "KPI-COM-03", "KPI-RSK-01", ExecutiveKpis.ACTIVE_WARNINGS)) {
            val reader = runBlocking { readers.platform(organizationId()) }
            val values =
                listOf(true, false).map { aggregated ->
                    json(runBlocking { dashboards.value(code, listOf(a, b), start, end, reader, excludeConfidential = true, aggregated = aggregated) })
                }
            assertEquals(values[1], values[0], code)
        }
    }
}
```

Qué cubre: dos proyectos UTM, un área AIDS aprobada solo en A con «Caserío Ejemplo» como miembro, interacciones en el
periodo con y sin esa localidad y una de hace tres meses (la serie), una queja abierta con localidad, una confidencial, una
consulta que reclasificó una queja (el original no cuenta), una queja de B en la localidad, un compromiso por proyecto, un
riesgo ALTO y alertas. Compara las respuestas enteras (sin `computedAt`) de `executive` con A y B como el ADMIN, como un
relacionista de A (alcance y QCS confidencial oculta) y como la plataforma, con `locality` y con `scope=AIDS` (y comprueba
que ahí las tiles cuentan 2 interacciones y las filas 1 y 0); los tableros `project`, `attention`, `engagement`,
`commitments`, `risks` y `territory` con locality; y el cierre de los KPI movidos con `excludeConfidential` como la
plataforma. `DashboardDeliverableParityTest` sigue igual y debe seguir en verde.

- [ ] **Paso 3: Build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL` (la clase compila). Integración pendiente: `sgspe.dashboard.ExecutiveAggregatesParityApiTest`.

```bash
cd $REPO && git add src/test/kotlin/sgspe/dashboard/ExecutiveAggregatesParityApiTest.kt
git commit -m "test: paridad entre los conteos en la base y la lectura con Records" -m "Las mismas cifras por los dos caminos en executive, byProject, los tableros que comparten esos KPI
y el cierre, como el ADMIN, un relacionista y la plataforma, con locality y con el ámbito AIDS.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-bis-agregados && git merge --no-ff f82abis/t6 -m "Merge branch 'f82abis/t6' into feat/fase-8-2a-bis-agregados"
```

---

### Tarea 7: Los documentos

**Files:**
- Modify: `docs/dashboards.md` (§6), `docs/api.md` (§4.11), `docs/architecture.md`, `CLAUDE.md`

**Interfaces:**
- Consumes: los nombres de las Tareas 1 a 5. No define IDs (`docs/requirements-analysis.md` no cambia).

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-bis-agregados && git switch -c f82abis/t7
```

- [ ] **Paso 2: `docs/dashboards.md` §6**

En el diagrama, la línea
`CHK -.->|"solo si una medición lo justifica"| SQL["SQL agregado detrás de la compuerta de permisos"]` pasa a
`CHK -->|"KPI del tablero executive (medición del 2026-10-14)"| SQL["SQL agregado detrás de la compuerta de permisos"]`.

El punto 3 entero pasa a:

```markdown
3. **SQL agregado solo con medición.** Donde una medición muestre que los dos caminos anteriores no alcanzan, se usa SQL de
   agregación sobre las tablas del propio tenant, resueltas desde la metadata, detrás de la misma compuerta de permisos que
   las consultas espaciales (`SpatialGate`): READ sobre cada objeto, lectura de cada campo usado, registros propios y
   alcance (RQ-GIS-017). Un campo que quien mira no lee sale como nulo, igual que en las filas; uno por el que se filtra
   tiene que leerse. La medición del 2026-10-14 en `sgspe_perf` (10 proyectos, 50 000 quejas, 20 000 interacciones) dio
   p95 de 8,2 s con un proyecto y `400 TOO_MANY_RECORDS` con diez, así que desde el plan 8.2a-bis cuentan en la base
   KPI-GRV-02, KPI-ENG-01, KPI-COM-02, KPI-COM-03, KPI-RSK-01, KPI-EXE-02 y las series de interacciones y de quejas de
   KPI-EXE-03 (`AggregateSql`, `KpiAggregates`), en todo tablero que los muestre, en su detalle y en el cierre; la tabla
   por proyecto sale de una consulta agrupada por proyecto con las mismas cifras que el filtro de cada proyecto solo. La
   regla de conteo de §3.3 se aplica en la base. KPI-EXE-01, KPI-INV-03 y la serie de vencidos de KPI-EXE-03 siguen en el
   punto 2. `sgspe.dashboards.aggregates: false` vuelve al punto 2.
```

- [ ] **Paso 3: `docs/api.md` §4.11**

Al final del punto «La lectura es acotada (`dashboards.md` §6.2) …» (después de «el portal lo muestra como «acote los
filtros».»), agregar: «Desde el plan 8.2a-bis, KPI-GRV-02, KPI-ENG-01, KPI-COM-02, KPI-COM-03, KPI-RSK-01, KPI-EXE-02 y las
series de interacciones y de quejas de KPI-EXE-03 cuentan en la base (`dashboards.md` §6.3) y no leen filas, así que no
dan `TOO_MANY_RECORDS`.» En la viñeta de `executive`, «calculados con las mismas funciones sobre el filtro de cada
proyecto» pasa a «con las mismas cifras que el filtro de cada proyecto solo (los KPI contados en la base, en una consulta
agrupada por proyecto)».

- [ ] **Paso 4: `docs/architecture.md` y `CLAUDE.md`**

En la fila de `sgspe.dashboard` de la tabla de paquetes, «conteos con `RecordService`» pasa a «conteos con `RecordService`
y, desde el plan 8.2a-bis, los del tablero `executive` en SQL por `SpatialGate` (`AggregateSql`, `KpiAggregates`)». En la
viñeta «Desde F6, `sgspe.dashboard` (`TerritoryKpis`) depende de `sgspe.territory` …», después de «(ASM-122).», agregar:
«Desde el plan 8.2a-bis, `KpiAggregates` también consulta por `SpatialGate` (como la persona o, en JOB-05, por
`selectAsPlatform`), y solo `DashboardData` y `Dashboards` lo nombran.»

En `CLAUDE.md`, la fila «Desde F8: indicadores …» de «Dónde está cada cosa» suma al final de su primera columna: «; desde
el plan 8.2a-bis, los conteos en SQL del tablero `executive` (`AggregateSql`, `AggregateKpis`, `KpiAggregates`,
`sgspe.dashboards.aggregates`)».

- [ ] **Paso 5: Verificación y commit**

```bash
cd $REPO && python3 tools/check_traceability.py && yarn -s format:check && git diff --stat
```

Esperado: la trazabilidad en verde, prettier en verde, cuatro archivos.

```bash
cd $REPO && git add docs/dashboards.md docs/api.md docs/architecture.md CLAUDE.md
git commit -m "docs: conteos en SQL de los KPI del tablero executive" -m "dashboards.md §6.3 anota la medición que justifica el SQL agregado y los KPI que cuentan en la base;
api.md, architecture.md y CLAUDE.md los nombran.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-bis-agregados && git merge --no-ff f82abis/t7 -m "Merge branch 'f82abis/t7' into feat/fase-8-2a-bis-agregados"
```

---

### Tarea 8: Integración, medición, push y PR (coordinador)

**Files:** ninguno nuevo (un commit de `docs/roadmap.md` con la medición).

- [ ] **Paso 1: Verificación completa**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
cd $REPO && python3 tools/check_traceability.py && yarn -s format:check && git status --short
```

Esperado: `BUILD SUCCESSFUL`, la trazabilidad y prettier en verde y el árbol limpio (salvo `.playwright-mcp/`).

- [ ] **Paso 2: Integración (coordinador, túnel 5435 y `sgspe_sdd_test` libre)**

Una clase tras otra con el comando de «Comandos»: `sgspe.dashboard.ExecutiveAggregatesParityApiTest` primero y, por
regresión, `ExecutiveDashboardApiTest` (incluida «más proyectos pedidos que el tope es 400 TOO_MANY_RECORDS sobre
project»), `ProjectDashboardApiTest`, `AttentionDashboardApiTest`, `CommitmentsDashboardApiTest`, `RisksDashboardApiTest`,
`TerritoryDashboardApiTest`, `EngagementIndicatorsF4ApiTest`, `DashboardsApiTest`, `MonitoringDashboardApiTest`,
`IndicatorMeasurementsApiTest` y `DashboardDeliverableParityTest`. Una falla de paridad vuelve a la tarea dueña con el
JSON de los dos caminos; no se arregla en esta rama sin su tarea. Si el SQL falla por un tipo (p. ej. un `ENUM` que no sea
`text` o un `RELATION` que no sea `uuid`), el `CAST` va en `AggregateSql` (Tarea 2).

- [ ] **Paso 3: La medición (coordinador)**

Contra `sgspe_perf` (el volumen de la medición del 2026-10-14: 10 proyectos, 50 000 quejas, 20 000 interacciones, 5 000
incidentes), con el backend de esta rama en el puerto 8098 apuntando a esa base como lo levanta el coordinador, y el
modelo de F8 ya aplicado allí: 50 peticiones `GET /api/sgspe/dashboards/executive?project=…×10&periodStart=…&periodEnd=…`
como `ADMIN` con un mes (el pasado) y otras 50 con un año, más una tanda de 50 con `sgspe.dashboards.aggregates=false`
para comparar. Criterio: p95 bajo 3 s con 10 proyectos (especificación de F8 §7). Anotar p50, p95, máximo, fecha y el
número de consultas por petición si se midió. Si no pasa: `EXPLAIN ANALYZE` de cada consulta de `AggregateSql` (el texto
sale con el log de depuración de `DatabaseClient`) y de vuelta al usuario antes de agregar índices (decisión 13).

El resultado va a `docs/roadmap.md` F8 «Estado» (después de «la medición del tablero `executive` en `sgspe_perf`,
pendiente del coordinador (Tarea 13)», que pasa a decir el resultado) y al cuerpo del PR, en un commit
`docs: medición del tablero executive con los conteos en SQL`.

- [ ] **Paso 4: Push y PR**

```bash
cd $REPO && git push -u origin feat/fase-8-2a-bis-agregados
gh pr create --base main --title "feat: plan 8.2a-bis, KPI del tablero executive contados en SQL" --body "$(cat <<'BODY'
## Qué trae

- `AggregateSql` y `KpiAggregates`: KPI-GRV-02, KPI-ENG-01, KPI-COM-02, KPI-COM-03, KPI-RSK-01, KPI-EXE-02 y las series de
  interacciones y quejas de KPI-EXE-03 cuentan en la base por `SpatialGate`, como la persona o como la plataforma.
- `byProject[]` desde una consulta agrupada por proyecto por KPI (`Indicator.computeByProject`); KPI-EXE-01 y KPI-INV-03
  reparten una sola lectura.
- `sgspe.dashboards.aggregates` (por defecto `true`) para volver a la lectura con `Records`.
- Sin cambios en la API.

## Pruebas

Unitarias y build en verde (`AggregateKpisTest`, `AggregateSqlTest`, `KpiAggregatesTest`, `DashboardDataAggregatesTest`,
`KpiEvaluationByProjectTest`, `ArchitectureTest`); integración: `ExecutiveAggregatesParityApiTest` y la regresión de la
Tarea 8. Medición en `sgspe_perf` con 10 proyectos: (resultado).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
BODY
)"
```

Esperado: el PR abierto y la CI en verde; se mezcla según el flujo autorizado del proyecto.

---

## Riesgos

1. **Parity por tipos de columna.** El SQL supone los tipos físicos de wasichai 1.2.0 (`timestamptz`, `date`, `text`,
   `uuid`); un cambio de wasichai los rompería en silencio solo si cambiara la semántica, no la sintaxis. Lo vigila
   `ExecutiveAggregatesParityApiTest` en la CI.
2. **Lo que queda en `Records`.** KPI-INV-03 y la serie de vencidos de KPI-EXE-03 siguen leyendo filas con el tope de
   20 000: un tenant con más compromisos que eso sigue viendo `400 TOO_MANY_RECORDS` en `executive`. `sgspe_perf` no tiene
   ese volumen de compromisos; si hace falta, es otro plan (contar KPI-COM-11 en SQL exige reconstruir el plazo vigente con
   las reprogramaciones).
3. **Otros tableros siguen leyendo filas.** `attention` (KPI-GRV-01 y 03 a 10), `project` (KPI-GRV-01 y 06) y `monitoring`
   siguen en el punto 2 de `dashboards.md` §6: con 50 000 quejas, `attention` de 10 proyectos sigue siendo `400`. Fuera del
   alcance de este plan.
4. **Costo por el túnel.** Cada consulta de la compuerta es una ida y vuelta; el tablero queda en unas 20 (6 conteos más
   las lecturas de proyectos, inversión y compromisos). Si el p95 no baja de 3 s, la medición lo dirá antes del PR.
5. **El ámbito AIDS/AIIS** arma el territorio de cada proyecto con `areaTerritory` (unas pocas lecturas por proyecto) solo
   cuando se pide ese ámbito.

## Pruebas

| Clase | Tarea | Tipo |
|---|---|---|
| `AggregateKpisTest` | 1 | Unitaria |
| `AggregateSqlTest` | 2 | Unitaria |
| `KpiAggregatesTest`, `ArchitectureTest` | 3 | Unitaria y escaneo |
| `DashboardDataAggregatesTest` | 4 | Unitaria |
| `KpiEvaluationByProjectTest`, `DashboardsPropertiesTest` | 5 | Unitarias |
| `ExecutiveAggregatesParityApiTest` | 6 | Integración |
| Regresión de los tableros, el cierre y `DashboardDeliverableParityTest` | 8 | Integración |

## Cobertura

| Pedido | Tarea |
|---|---|
| Qué lecturas dominan y cuáles pasan a SQL (decisión 1) | 1 a 5 |
| SQL por `SpatialGate` con los mismos `RecordReadScopes`, la regla de QCS confidenciales, la legibilidad y la plataforma | 2, 3, 4 |
| `byProject`: una consulta agrupada por KPI, no un recálculo por proyecto | 2, 4, 5 |
| Sin `TOO_MANY_RECORDS` en los KPI movidos; R6 igual | 5, 8 |
| Paridad: unitarias de los constructores de SQL y de las cifras, integración de los dos caminos, `DashboardDeliverableParityTest` | 1, 2, 6, 8 |
| `excludeConfidential` (R4), KPI-ENG-05, meses de Lima | 2, 4, 5, 6 |
| Alcance mínimo y tableros que comparten los KPI (decisión 9), sin cambios de API | 5, 6 |
| Medición en `sgspe_perf` y `roadmap.md` | 8 |
