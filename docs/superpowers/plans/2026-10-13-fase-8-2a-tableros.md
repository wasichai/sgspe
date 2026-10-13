# SGSPE · Fase 8, plan 8.2a (indicadores y tableros consolidados): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Dejar en `~/IdeaProjects/sgspe` el catálogo `indicator` con las metas de DOC-01 y su carga
(`model/indicators.json`, `model/load_indicators.py`), la medición oficial `indicator_measurement` con el cierre opcional
de periodos (`POST /api/sgspe/indicators/{id}/measurements`), el lector explícito de los KPI (`KpiReader`: la persona o
la plataforma de una organización), KPI-EXE-01 a KPI-EXE-03 y los tableros `executive` (con `byProject[]`), `project` y
`monitoring` (la Tabla 9.5 con meta, origen de la meta y cifra oficial), y la prueba de paridad entre el tablero y el
lector de la plataforma (RQ-DSH-034) que usará el ensamblado de entregables de 8.2b.

**Architecture:** El modelo de los indicadores viaja con este plan (§2.2.1 de la especificación): dos objetos en
`model.json`, sus filas de `security.md` §7.8 en `roles.json`, el catálogo generado una vez desde `dashboards.md` §4 y un
script de carga de solo biblioteca estándar con el patrón de `load_ubigeo.py`, que corre después del ubigeo. En
`sgspe.dashboard`, `KpiReaders` arma el contexto de lectura de cada `KpiReader` (quién lee, qué campos son legibles,
`confidentialHidden`, los proyectos por defecto) y, para la plataforma, envuelve el cálculo en `Records.asPlatform`, que
solo se permite fuera de una petición; `Dashboards` calcula todo tablero, KPI y cierre con ese contexto, y `SpatialGate`
gana una vía de plataforma sin alcances para KPI-TER-04. `ExecutiveKpis` y `MonitoringTable` son funciones puras con sus
unitarias; `IndicatorMeasurements` cierra un periodo como la persona, bajo un candado, con las reglas «cifra completa o
ninguna» de ASM-136. Nada nuevo escribe como la plataforma.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 1.2.0 (sin
starters ni dependencias nuevas), Jackson 3 (`tools.jackson`), JUnit 5 con `wasichai-test` (`WasichaiIntegrationTest`) y
Mockito; Python 3.11 o superior, solo biblioteca estándar (`model/`).

**Spec:** `docs/superpowers/specs/2026-10-13-fase-8-design.md` (vinculante): «Restricciones globales»; §1 (filas
«Indicadores» y «Tableros consolidados»); §2.1 decisiones 6 y 7; §2.2 elecciones 1 («el modelo de los indicadores viaja
con 8.2a»), 15 («una cifra oficial es completa o no existe») y 16 («los KPI reciben su lector»); §3.1 entero (es este
plan); §4.1; §5 (filas `IndicatorMeasurementsApiTest`, `ExecutiveDashboardApiTest`, `ProjectDashboardApiTest`,
`MonitoringDashboardApiTest`, `DashboardDeliverableParityTest`, `test_load_indicators.py` y la base `F8IndicatorsApiTest`);
§7 (fila 8.2a, «Paralelismo», la medición del tablero `executive` y el orden de despliegue); §8 (consecuencia aceptada 5).
Documentos como los dejó el plan 8.0 (en `main` desde sgspe #58): `docs/dashboards.md` §3, §4 (entero), §5.1, §5.2, §5.5,
§6, §7, §8, §10 y §11; `docs/api.md` §3.3 (errores de F8) y §4.11; `docs/requirements-analysis.md` ASM-042 y ASM-136,
RQ-DSH-002, RQ-DSH-003, RQ-DSH-034, RQ-DSH-037; `docs/database-model.md` §19 (fichas de `indicator` e
`indicator_measurement`) y §25; `docs/security.md` §7.8, §8.2, §9.7 y §14; `docs/ux/screens.md` (S51 y «Monitoreo de la
Tabla 9.5»); y el plan 8.0 («Interfaces con los otros planes de F8», punto 8.2a). Lo que la especificación asigna a 8.1
(objetos de entregable), 8.2b (`sgspe.privacy`, JOB-05, T2), 8.2c y 8.3 queda fuera.

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

- **Repositorio y ramas.** Todo el trabajo en el worktree `~/IdeaProjects/sgspe-wt/f82a`, en la rama
  `feat/fase-8-2a-tableros` creada desde `main` (con el plan 8.0 integrado; este plan se versiona en ella). Cada tarea en
  su rama `f82a/t<k>`, creada desde `feat/fase-8-2a-tableros` con las tareas de las que depende ya integradas, y se
  integra con `git merge --no-ff f82a/t<k>`. En los comandos, `$REPO` es `~/IdeaProjects/sgspe-wt/f82a` y `$SCRATCH` el
  scratchpad de la sesión de quien ejecuta (`export SCRATCH=<scratchpad>/f8-2a && mkdir -p "$SCRATCH"`), fuera del
  repositorio. `~/IdeaProjects/sgspe` no se toca.
- **Arquitectura propia.** `KpiReaders` es la única pieza de `sgspe.dashboard` que llama a `Records.asPlatform`
  (`ArchitectureTest`, Tarea 11), y su `platform(...)` falla si hay un usuario en el contexto. «Lee todos los proyectos»
  se pregunta con `ProjectReach.mine(user) == null`, nunca con `readsAll` fuera de `common` y `scope` (`ArchitectureTest`).
  Ningún `DatabaseClient` ni `RecordReadScopes` nuevo: el SQL propio sigue pasando por `SpatialGate`. Ningún import sin
  uso: ktlint de este repositorio no los quita.
- **Modelo.** `model/` está fuera de prettier: los JSON van con el formato a mano de este plan. Ningún campo con
  `editable`; ninguna acción declarada, workflow, tipo de documento ni automatización nuevos. `PHASE` de
  `test_model.py` sigue en `"F7"`: los dos objetos de este plan se adelantan con `EARLY` (decisión 1), y 8.1 lo borra al
  poner `PHASE = "F8"`.
- **Errores.** Todo 400, 403, 404 o 409 propio lleva su código en `errors[0].message` y el campo en `errors[0].field`
  (`Finding.toCodedException`), con los estados de `api.md` §3.3: `400 PROJECT_REQUIRED` y `400 TOO_MANY_RECORDS`
  (`project`), `409 INDICATOR_NOT_CLOSABLE` (`indicator`, con el miembro `reason` del cuerpo: `STANCE`,
  `NO_SCALAR_VALUE`, `UNAVAILABLE` o `CONFIDENTIAL_HIDDEN`), `400 PERIOD_NOT_ENDED` (`attributes.period_end_date`),
  `409 MEASUREMENT_EXISTS` (`attributes.period_start_date`), `403 ACTION_REQUIRED` (`project`) y `400 FIELD_NOT_EDITABLE`
  (`code`). Un parámetro o una propiedad desconocidos son `422` y `400` como siempre (`StrictQuery`, `StrictBody`).
- **Datos de prueba.** Ficticios: correos `@sgspe.test`, «Proyecto Ejemplo A», «Comunidad Ejemplo», «Persona Ejemplo 1»,
  «Asociación Ejemplo», geometrías de `SyntheticGeo`. Ningún nombre real en nada versionado.
- **Integración: la escribe el implementador, la corre el coordinador.** Las clases `*ApiTest` necesitan PostgreSQL con
  PostGIS. Ningún implementador ni subagente corre `integrationTest`, ni toca Docker, el túnel, una base de datos o el
  tenant de desarrollo. El implementador corre las pruebas de Python y las unitarias de su tarea, compila las de
  integración con `./gradlew compileTestKotlin` (o `build`) y entrega la tarea marcada **«integración pendiente:
  <clases>»**. El coordinador las corre una tras otra contra `sgspe_sdd_test` (o las deja a la CI del PR).
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde `$REPO`;
  `./gradlew ktlintFormat` antes de `build`. El token nunca se imprime ni va a un archivo. Con poca memoria,
  `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Aprovisionamiento.** Desde este plan: `apply.py` → `apply_roles.py` → `apply.py --workflows` → `load_ubigeo.py` →
  `load_indicators.py` → `publish_layers.py` (omitible con `--skip-layers`), también en `tenant_steps.py`.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres);
  al final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM`. `git add` solo con rutas explícitas; nunca
  `initial-promp.md`, `arch-*.md`, `tech-landing.txt`, `.playwright-mcp/` ni nada de `~/Documents/gspe`. `yarn format`
  antes de commitear yaml o json fuera de `model/`. El cuerpo del PR termina con
  `🤖 Generated with [Claude Code](https://claude.com/claude-code)` y la línea de la sesión. Push y PR solo en la Tarea 13.

### Comandos

Pruebas de Python (sin red fuera de `127.0.0.1`):

```bash
cd $REPO/model && python3 -m unittest 2>&1 | tail -3
cd $REPO/model && python3 apply.py --validate-only && python3 load_ubigeo.py --dry-run > /dev/null && \
  python3 load_indicators.py --validate-only && echo ok
```

Unitarias de una clase y build completo (ktlint, unitarias y compilación de las de integración):

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests '<clase>'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Integración (coordinador, túnel 5435 abierto y `sgspe_sdd_test` libre):

```bash
cd $REPO && WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'
```

La primera clase de la JVM que extiende `SgspeApiTest` aprovisiona el modelo (86 objetos: más de 10 minutos por el
túnel), y la primera que extiende `F8IndicatorsApiTest` corre además `load_indicators.py`; conviene correr juntas las
clases de una tarea.

## Review Focus

Las cinco entradas que la especificación implica y que más pueden morder a quien use esto, cada una con su prueba en la
tarea dueña del código:

1. **El lector de la plataforma dentro de una petición.** Un endpoint (este o uno de 8.2b) que pase
   `KpiReader.Platform` mientras atiende a una persona le daría la postura, las QCS confidenciales y los proyectos que no
   tiene asignados. Se espera que `KpiReaders.platform(...)` y `KpiReaders.reading(...)` con la plataforma lancen
   `IllegalStateException` si hay un usuario en el contexto, sin leer nada, y que ninguna otra pieza de
   `sgspe.dashboard` llame a `asPlatform`. Pruebas: `KpiReadersTest` «la plataforma no se usa con una persona en el
   contexto» (Tarea 3) y `ArchitectureTest` «solo KpiReaders lee como la plataforma en sgspe.dashboard» (Tarea 11).
2. **Volver a correr `load_indicators.py` en un tenant que ya editó sus metas** (la subida de fase de cada tenant lo
   corre). Se espera que una meta cambiada o borrada y una frecuencia cambiada o borrada por el cliente sigan igual
   aunque la documentación de `dashboards.md` cambie, que un indicador agregado por el cliente no se borre y que una
   segunda corrida no escriba. Pruebas: `test_load_indicators.LoadTests` «una meta o una frecuencia editada por el
   cliente no se toca ni cuando cambian los textos», «un indicador que el cliente agregó no se borra» y «la segunda
   corrida no escribe nada» (Tarea 1).
3. **Una cifra oficial mostrada fuera de su medición o a quien no lee la postura.** La medición del proyecto A de
   septiembre no debe salir en el tablero de A con un filtro de localidad, ni en el de A y B, ni en el de octubre, ni la
   del tenant en el tablero de un rol que solo ve sus proyectos; y #2 y #3 dicen «no disponible» a quien no lee `stance`
   aunque el periodo esté cerrado. Pruebas: `MonitoringTableTest` «la cifra oficial casa solo con el mismo alcance,
   periodo y sin otros filtros» y «sin el KPI disponible no hay cifra oficial» (Tarea 7) y `MonitoringDashboardApiTest`
   «la cifra oficial sale solo con el pedido de su medición» y «#2 y #3 no disponibles sin stance, también cerrados»
   (Tarea 8).
4. **Dos cierres del mismo periodo, o un cierre de todo el tenant por quien no lo lee entero.** La unicidad compuesta no
   ve dos mediciones del tenant (los NULL son distintos, WAS-ADR-0037), y dos peticiones simultáneas podrían pasar la
   comprobación a la vez. Se espera `409 MEASUREMENT_EXISTS` en la segunda, por proyecto y por tenant, una sola
   medición guardada, y `403 ACTION_REQUIRED` para quien no tiene `READ_ALL_PROJECTS` ni es `ADMIN`. Pruebas:
   `IndicatorMeasurementsApiTest` «un periodo se cierra una vez por proyecto y una vez para el tenant» y «todo el tenant
   exige leer todos los proyectos y cerrar exige CREATE» (Tarea 10).
5. **Un tablero `executive` con muchos proyectos, sin proyectos o en un tenant sin el modelo de F8.** Se espera `400
   TOO_MANY_RECORDS` sobre `project` con más de `sgspe.dashboards.executive.max-projects` (también por «mis proyectos»
   por defecto), un tablero vacío y sin error sin proyectos, y que `monitoring` en un tenant de F7 (sin `indicator`) o
   ante quien no lee el catálogo responda sin metas ni cifras oficiales en vez de un 500. Pruebas:
   `ExecutiveDashboardApiTest` «más proyectos que el tope es 400 TOO_MANY_RECORDS sobre project» y «sin proyectos las
   tiles salen en cero y byProject vacío» (Tarea 5), `MonitoringTableTest` «sin catálogo, sin metas ni cifras oficiales»
   (Tarea 7) y `MonitoringDashboardApiTest` «sin leer el catálogo el tablero sale sin metas ni cifras oficiales» (Tarea 8).

---

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | §2.2.1 adelanta `indicator` e `indicator_measurement` (F8 en sus fichas) a este plan, pero `test_model.py` compara el modelo con las fichas hasta `PHASE` y exige «nada de una fase posterior»; poner `PHASE = "F8"` exigiría también los entregables, que son de 8.1 | `PHASE` sigue en `"F7"` y `test_model.py` gana `EARLY = {"indicator": "F8", "indicator_measurement": "F8"}` con `limit(name)`: esos dos objetos se comparan hasta su fase, los demás hasta `PHASE`. 8.1 borra `EARLY` al poner `PHASE = "F8"` (Tarea 1) |
| 2 | `NO_SCALAR_VALUE` cubre «KPI-TER-01, KPI-EXE-03 y los que solo dan series», sin lista de esos últimos; varios KPI de serie (KPI-STK-08, KPI-COM-11, KPI-GRV-09, KPI-RSK-04) sí traen un valor del último mes | La razón sale de dos lados: la lista fija `Kpis.NO_SCALAR` (KPI-TER-01 y KPI-EXE-03) y, después de calcular, un `value` nulo (un porcentaje sin denominador, una serie sin escalar). Los KPI de serie con valor se cierran con ese valor (Tarea 9) |
| 3 | «Manda el parámetro del proyecto cuando existe; si no, `target_value`» (§3.1), pero KPI-ENG-02 ya calcula su meta del periodo con el parámetro y, si está vacío, con el valor por defecto de ASM-019 (2), y `target_value` es mensual | #5 (KPI-ENG-02): con el parámetro del proyecto o un plan con meta en algún mes del periodo, la meta es el `denominator` de KPI-ENG-02 y el origen `PROJECT`; si no, `target_value` × meses que toca el periodo × proyectos, origen `CATALOG`; sin ella, `NONE`. Las demás filas: `target_value` tal cual (`CATALOG`) o `NONE`. Con el catálogo inicial (2) las dos cifras coinciden; si el cliente edita la meta del catálogo, el detalle de KPI-ENG-02 sigue con el valor por defecto de ASM-019, y el monitoreo sigue al catálogo (Tarea 7) |
| 4 | «Sin filtros de territorio» para la cifra oficial; los demás filtros (`scope`, `explorationCampaign`, `owner`, `program`, `year`) también recortan la cifra | La cifra oficial sale solo si el pedido no trae ningún filtro fuera de `project` y el periodo (`DashboardFilters.given()` vacío), el periodo es exactamente el de la medición y el alcance coincide: un solo `project` pedido para una medición de ese proyecto, o ningún `project` pedido por quien lee todos los proyectos (el lector de la plataforma o `ProjectReach.mine(user) == null`) para una del tenant (Tarea 7) |
| 5 | §3.1 dice que cada fila de `monitoring` lleva valor, desgloses y meta, sin fijar la forma; la respuesta de los tableros es `DashboardResponse` con `indicators[]` | `monitoring` responde `DashboardResponse` con `indicators[]` (los 13 KPI en el orden de §4.9, con sus desgloses y su disponibilidad, como todo tablero) y `rows[]` en el mismo orden: `{ number, code, label, verificationMeans, deliverable, available, value, target, targetSource, official }`. `official` es `{ measurement, closedAt, value }` o `null` (Tareas 7 y 8; Tarea 12 lo escribe en `api.md`) |
| 6 | `byProject[]` es «una fila por proyecto con las mismas cifras», sin forma | `{ project, code, name, indicators[] }`, con los 8 KPI de las tiles con valor (KPI-EXE-01, KPI-COM-02, KPI-COM-03, KPI-GRV-02, KPI-RSK-01, KPI-ENG-01, KPI-EXE-02 y KPI-INV-03), sin desgloses ni series; cada fila calcula con las mismas funciones sobre el filtro de ese proyecto (Tarea 5) |
| 7 | KPI-EXE-03 son «tres series», y `IndicatorResult` lleva una sola serie | Una serie de 12 filas `{ period, engagements, grievancesReceived, grievancesClosed, overdueCommitments }` y `value: null`. Lee QCS, así que lleva `confidentialHidden` como los KPI-GRV. Las quejas se cuentan con la regla de reclasificación de las tres QCS y después se filtran a `grievance` (`AttentionKpis.counted`), para que un original reclasificado a consulta no cuente (Tarea 4) |
| 8 | ASM-136: «las evaluaciones de grupos de interés, mensuales; las revisiones de riesgo, semanales», sin decir qué KPI son; `frequency` es TEXT | Mensual: los 13 de la Tabla 9.5 y KPI-STK-01, 02, 03 y 08 (los que leen las evaluaciones); Semanal: KPI-RSK-01 a 05; los demás sin frecuencia. El texto es `Mensual` o `Semanal`. Solo KPI-ENG-02 trae meta (2) (Tarea 1) |
| 9 | El catálogo debe tener «cada KPI de `dashboards.md` §4» con sus textos | `model/indicators.json` se genera una vez con `$SCRATCH/gen_indicators_json.py` desde las filas de §4 (nombre sin «#n», definición, fórmula, «Filtros y desgloses» como `disaggregation`) y §4.9 (medio de verificación y número): 53 indicadores. `test_load_indicators.py` exige que sigan siendo los de §4 en su orden (Tarea 1) |
| 10 | El cierre de todo el tenant exige `READ_ALL_PROJECTS` o `ADMIN`, pero `ArchitectureTest` prohíbe `readsAll` fuera de `common` y `scope` | Se pregunta `ProjectReach.mine(user) == null`, que es lo mismo (Tarea 10). Con los roles de hoy solo `SOCIAL_MANAGER` tiene CREATE sobre `indicator_measurement` y no tiene `READ_ALL_PROJECTS`: todo el tenant lo cierra, en la práctica, `ADMIN` (como la consecuencia 5 de la especificación para los KPI de QCS) |
| 11 | `indicator_measurement` es raíz con `project` opcional (`ProjectScope.ROOTS`) e `indicator` es maestro del tenant (§4.2 de la especificación); `security.md` §9.7 no los nombra | `ProjectScope.ROOTS` suma `indicator_measurement` (36 raíces) y `ProjectScope.TENANT` suma `indicator`; `security.md` §9.7 cambia en el mismo commit que `model.json` (la fila de las raíces y «Maestros del tenant», que lee `design_docs.py`), como pidió el plan 8.0 (Tarea 1) |
| 12 | `SpatialGate` lee siempre como la persona del token («fuera de una petición es el 401»), y KPI-TER-04 lo usa: con el lector de la plataforma, §2.2.16 pide «`SpatialGate` no pide alcances» | `SpatialGate.selectAsPlatform(organizationId, reads, sql, map)`: sin permisos de persona ni alcances ni dueño, con la organización enlazada; un objeto o campo que el tenant no tiene es no disponible. `ComponentLand.rows(projects, platform)` la usa cuando `DashboardData` trae la organización de la plataforma (Tarea 3) |
| 13 | `INDICATOR_NOT_CLOSABLE` lleva «el miembro `reason` del cuerpo» y `Finding` solo da `errors[]` | `IndicatorNotClosable` (una `WasichaiException` con `errors[0]` = `indicator`/`INDICATOR_NOT_CLOSABLE`) y un `@ExceptionHandler` del controlador que agrega `reason` al problem+json, como `ImportChoiceRequired` agrega `layers` (Tarea 10) |
| 14 | Un `indicator` creado por el cliente con un código que `Kpis` no calcula | El cierre responde `409 INDICATOR_NOT_CLOSABLE` con `UNAVAILABLE`: no hay cifra que congelar. El tablero `monitoring` no lo muestra (sus filas son las 13 de §4.9) (Tarea 10) |
| 15 | El periodo «tiene que haber terminado» | Termina antes de hoy en Lima: `period_end_date` < hoy. Un periodo con inicio posterior al fin es `400 OUT_OF_RANGE` (`attributes.period_start_date`); una fecha ilegible, `400 INVALID_VALUE`; una que falta, `400 FIELD_REQUIRED` (Tarea 10) |
| 16 | `GET /api/sgspe/dashboards/indicators` «ubica KPI-STK-04, 05 y 07 y KPI-ENG-06, 07 y 08 en `monitoring`», y `DashboardsApiTest` y `EngagementKpisF4Test` exigen hoy `dashboard: null` | Su `dashboard` pasa a `monitoring` y KPI-EXE a `executive`; las dos pruebas cambian en la Tarea 4 y la Tarea 8 |
| 17 | La base de pruebas `F8IndicatorsApiTest` va «sobre `F6ApiTest`» y los tableros necesitan el catálogo cargado | La base corre `model/load_indicators.py` una vez por JVM contra la app de la prueba (como el aprovisionamiento de `SgspeApiTest`), y trae `activeProject`, `indicatorId`, `get`, `closePeriod` y `lastMonth` (Tarea 2) |
| 18 | La medición del tablero `executive` («10 proyectos en `sgspe_perf`, p95 bajo 3 s») es de §7 | Paso del coordinador en la Tarea 13, con permiso del usuario para tocar `sgspe_perf`; el resultado va al cuerpo del PR y a `roadmap.md` F8 «Estado» en un commit aparte |

## Interfaces con los otros planes de F8

Este plan va después del 8.0 y en paralelo con F7 (ya integrada) y con 8.3; 8.1 lo rebasa o lo rebasa este (los dos
tocan `model.json`, `roles.json` y `test_model.py`: el segundo en mezclar corre la suite de Python y ajusta las cuentas),
y 8.2b parte de los dos integrados en `main`. Si un nombre de aquí y uno de los documentos difieren, gana el documento y
se corrige este plan.

### El modelo (`model/`)

- **86 objetos** (los 84 de F7 y, entre `task` y `export_log`, `indicator` e `indicator_measurement`), 278 relaciones,
  39 acciones declaradas. `apply.py` sobre un tenant nuevo: `done: 403 created, 27 updated, 0 skipped`.
- **`indicator`** «Indicador» / «Indicadores» (TENANT, editable): `code`! TEXT único, `name`! TEXT, `definition`,
  `formula`, `verification_means` LONG_TEXT, `disaggregation` TEXT, `target_value` DECIMAL, `frequency` TEXT,
  `source_reference` TEXT.
- **`indicator_measurement`** «Medición de indicador» / «Mediciones de indicador» (`apiOnly`, `appendOnly`, raíz con
  `project` opcional; único `(indicator, project, period_start_date)`): `period_start_date`! y `period_end_date`! DATE,
  `measured_value`! DECIMAL, `numerator` y `denominator` DECIMAL, `breakdown` LONG_TEXT, `closed_by_user_id`! UUID,
  `closed_by_user_email`! EMAIL; `indicator_measurement_indicator` (requerida) e `indicator_measurement_project`.
- `roles.json`: READ sobre los dos para PMG, CRO, SHM, CMM, GVM, SRM, SIM, GAN, APR, ANL y RDO; `SOCIAL_MANAGER` RU sobre
  `indicator` y CR sobre `indicator_measurement`.
- `model/indicators.json` (`{"indicators": [53 elementos]}`) y `model/load_indicators.py`
  (`read_catalog(path)`, `load(client, items)`, `main(argv)`; salida `done: <c> created, <u> updated, <s> skipped`),
  paso `load_indicators.py` de `tenant_steps.STEPS`, entre `load_ubigeo.py` y `publish_layers.py`.
- `test_model.EARLY`: 8.1 la borra al poner `PHASE = "F8"`.

### Kotlin (lo que consume 8.2b y 8.2c)

- `sgspe.dashboard.KpiReader` (sealed): `KpiReader.Person(val user: AuthenticatedUser)` y
  `KpiReader.Platform(override val organizationId: UUID)`, los dos con `val organizationId: UUID`.
- `sgspe.dashboard.KpiReaders` (`@Component`): `suspend fun person(): KpiReader.Person`; `suspend fun
  platform(organizationId: UUID): KpiReader.Platform` (falla con `IllegalStateException` dentro de una petición);
  `suspend fun <T> reading(reader: KpiReader, block: suspend (KpiContext) -> T): T` (con la plataforma, dentro de
  `Records.asPlatform`).
- `sgspe.dashboard.KpiContext`: `reader`, `readable: suspend (String) -> Boolean`, `confidentialHidden: Boolean`,
  `tenantWide: Boolean`, `platform: UUID?`, `suspend fun defaultProjects(): List<UUID>`.
- `sgspe.dashboard.Dashboards`: `suspend fun dashboard(name: String, params: MultiValueMap<String, String>, reader:
  KpiReader? = null): DashboardResponse` (sin lector, la persona: `RunSources` de F7 no cambia); `suspend fun
  indicator(code: String, params: MultiValueMap<String, String>, reader: KpiReader? = null): IndicatorValue`;
  `suspend fun value(code: String, projects: List<UUID>?, start: LocalDate, end: LocalDate, reader: KpiReader):
  IndicatorValue` (sin `projects`, los proyectos por defecto del lector). T1 (8.2c, 9.3.1) pide
  `dashboard("monitoring", params, readers.platform(org))` y lee `rows[]` con su `official`.
- `DashboardResponse` suma `byProject: List<ProjectFigures>?` y `rows: List<MonitoringRow>?` (no viajan si son nulos).
- `sgspe.dashboard.MonitoringTable.ROWS: List<MonitoringSpec>` (`number`, `code`, `label`, `verificationMeans`,
  `deliverable`), `ExecutiveKpis.TILES` y `ExecutiveKpis.BY_PROJECT`.
- `DashboardFilters.of(projects: List<UUID>, start: LocalDate, end: LocalDate): DashboardFilters`.
- `SpatialGate.selectAsPlatform(organizationId: UUID, reads: List<GateRead>, sql: GateTables.((Any) -> String) ->
  String, map: (Readable) -> T): GateOutcome<T>` y `ComponentLand.rows(projects: Collection<UUID>, platform: UUID? =
  null)`.
- `POST /api/sgspe/indicators/{id}/measurements` (`IndicatorMeasurements`), como lo fija `api.md` §4.11.

### Pruebas

- `sgspe.F8IndicatorsApiTest : F6ApiTest()`: `activeProject(code: String = uniqueName("PRY-EJ-"), attributes:
  Map<String, Any?> = emptyMap()): String`,
  `indicatorId(code: String): String`, `get(path: String, token: String = this.token): JsonNode`,
  `closePeriod(indicator: String, project: String?, start: LocalDate, end: LocalDate, token: String = this.token,
  key: String? = null): Response`, `lastMonth(): Pair<LocalDate, LocalDate>`, `pastMonth(): Pair<LocalDate, LocalDate>`. 8.1 crea `F8ApiTest : F7ApiTest()` aparte;
  8.2b puede subir estas ayudas si las necesita.

## Mapa de archivos

| Archivo | Tarea | Qué |
|---|---|---|
| `model/model.json`, `model/roles.json` (con `$SCRATCH/gen_indicators_f8.py`), `model/indicators.json` (con `$SCRATCH/gen_indicators_json.py`) | 1 | Los dos objetos, sus permisos y el catálogo |
| `model/load_indicators.py`, `model/test_load_indicators.py` (nuevos), `model/tenant_steps.py`, `model/provision_tenant.py`, `model/upgrade_tenants.py` | 1 | La carga y su paso |
| `model/test_model.py`, `model/test_apply.py`, `model/test_apply_roles.py`, `model/test_publish_layers.py`, `model/test_design_docs.py`, `model/test_provision_tenant.py`, `model/test_upgrade_tenants.py` | 1 | `EARLY`, cuentas y subidas de fase |
| `docs/security.md` (§9.7) | 1 | 36 raíces e `indicator` entre los maestros del tenant |
| `src/main/kotlin/sgspe/scope/ProjectScope.kt`, `src/main/kotlin/sgspe/common/AmountFieldsGuard.kt`, `src/test/kotlin/sgspe/scope/ProjectScopeF7Test.kt`, `src/test/kotlin/sgspe/scope/ProjectScopeF8Test.kt` (nuevo), `src/test/kotlin/sgspe/contract/TenantProvisioningApiTest.kt` | 1 | Clasificación, decimales que no son dinero y el alta contra wasichai |
| `src/main/kotlin/sgspe/dashboard/IndicatorWriteGuard.kt`, `src/test/kotlin/sgspe/dashboard/IndicatorWriteGuardTest.kt`, `src/test/kotlin/sgspe/F8IndicatorsApiTest.kt`, `src/test/kotlin/sgspe/dashboard/IndicatorModelApiTest.kt` (nuevos) | 2 | La guarda del código, la base y el modelo contra wasichai |
| `src/main/kotlin/sgspe/dashboard/KpiReader.kt` (nuevo), `Dashboards.kt`, `DashboardData.kt`, `src/main/kotlin/sgspe/territory/SpatialGate.kt`, `ComponentLand.kt`, `src/test/kotlin/sgspe/dashboard/KpiReadersTest.kt` (nuevo), `src/test/kotlin/sgspe/territory/SpatialGateTest.kt` | 3 | El lector |
| `src/main/kotlin/sgspe/dashboard/ExecutiveKpis.kt` (nuevo), `Kpis.kt`, `DashboardData.kt`, `src/test/kotlin/sgspe/dashboard/ExecutiveKpisTest.kt` (nuevo), `DashboardsApiTest.kt` | 4 | KPI-EXE |
| `Dashboards.kt`, `Indicators.kt`, `DashboardFilters.kt`, `src/main/resources/application.yml`, `src/test/kotlin/sgspe/dashboard/ExecutiveDashboardApiTest.kt` (nuevo) | 5 | Tablero `executive` |
| `Dashboards.kt`, `Kpis.kt`, `src/test/kotlin/sgspe/dashboard/ProjectDashboardApiTest.kt` (nuevo) | 6 | Tablero `project` |
| `src/main/kotlin/sgspe/dashboard/MonitoringTable.kt` (nuevo), `src/test/kotlin/sgspe/dashboard/MonitoringTableTest.kt` (nuevo) | 7 | Filas, metas y cifra oficial, puras |
| `Dashboards.kt`, `DashboardData.kt`, `Kpis.kt`, `Indicators.kt`, `src/test/kotlin/sgspe/dashboard/MonitoringDashboardApiTest.kt` (nuevo), `EngagementKpisF4Test.kt` | 8 | Tablero `monitoring` |
| `src/main/kotlin/sgspe/dashboard/Closability.kt` (nuevo), `src/test/kotlin/sgspe/dashboard/ClosabilityTest.kt` (nuevo) | 9 | Reglas puras del cierre |
| `src/main/kotlin/sgspe/dashboard/IndicatorMeasurements.kt` (nuevo), `src/test/kotlin/sgspe/dashboard/IndicatorMeasurementsApiTest.kt` (nuevo) | 10 | El endpoint de cierre |
| `src/test/kotlin/sgspe/dashboard/DashboardDeliverableParityTest.kt` (nuevo), `src/test/kotlin/sgspe/ArchitectureTest.kt` | 11 | Paridad y la regla de `asPlatform` |
| `docs/api.md`, `docs/dashboards.md`, `docs/database-model.md` (§19, notas), `docs/roadmap.md`, `docs/requirements-traceability.md`, `CLAUDE.md`, `docs/develop/README.md` | 12 | Documentos |
| — | 13 | Integración, medición, push y PR (coordinador) |

## Orden y paralelismo

```
Tarea 1 ── Tarea 2 ──┐
Tarea 3 ── Tarea 4 ──┼── Tarea 5 ── Tarea 6 ──┐
Tarea 7 ─────────────┴── Tarea 8 ─────────────┼── Tarea 10 ── Tarea 11 ── Tarea 12 ── Tarea 13
Tarea 9 ──────────────────────────────────────┘
```

- La 1 va primero (el modelo). La 3 (lector) y la 7 y la 9 (funciones puras) no dependen del modelo y pueden ir en
  paralelo con la 1 y la 2; la 3 y la 4 tocan `DashboardData.kt` y `Kpis.kt` por turnos.
- La 5, la 6 y la 8 editan `Dashboards.kt` por turnos, en ese orden. La 10 necesita la 2 (base), la 3 (`value`) y la 9.
- La 11 necesita todo el Kotlin; la 12, los contratos finales; la 13 es del coordinador.

---
### Tarea 1: El modelo de los indicadores, sus permisos, el catálogo y su carga

**Files:**
- Modify: `model/model.json`, `model/roles.json` (con `$SCRATCH/gen_indicators_f8.py`, una vez)
- Create: `model/indicators.json` (con `$SCRATCH/gen_indicators_json.py`, una vez), `model/load_indicators.py`,
  `model/test_load_indicators.py`
- Modify: `model/tenant_steps.py`, `model/provision_tenant.py` (docstring), `model/upgrade_tenants.py` (docstring)
- Modify: `model/test_model.py`, `model/test_apply.py`, `model/test_apply_roles.py`, `model/test_publish_layers.py`,
  `model/test_design_docs.py`, `model/test_provision_tenant.py`, `model/test_upgrade_tenants.py`
- Modify: `docs/security.md` (§9.7: la fila de las raíces y «Maestros del tenant»)
- Modify: `src/main/kotlin/sgspe/scope/ProjectScope.kt`, `src/main/kotlin/sgspe/common/AmountFieldsGuard.kt`
- Create: `src/test/kotlin/sgspe/scope/ProjectScopeF8Test.kt`
- Modify: `src/test/kotlin/sgspe/scope/ProjectScopeF7Test.kt`, `src/test/kotlin/sgspe/contract/TenantProvisioningApiTest.kt`

**Interfaces:**
- Consumes: nada de otras tareas.
- Produces: «El modelo (`model/`)» de «Interfaces»; `ProjectScope.ROOTS` con `indicator_measurement` (36) y
  `ProjectScope.TENANT` con `indicator` (21); `AmountFieldsGuard.NOT_MONEY` con los cuatro DECIMAL nuevos.

Esta tarea es grande porque el modelo no se cambia a medias: con `indicator_measurement` en `model.json` y sin
clasificar, `ArchitectureTest` falla; sin su fila en `NOT_MONEY`, `AmountFieldsGuardTest` también. Va en tres tramos con
su commit cada uno (modelo y permisos; catálogo y carga; Kotlin), y el build queda en verde al final del tercero.

- [ ] **Paso 1: El worktree, la rama y el plan**

```bash
cd ~/IdeaProjects/sgspe && git fetch origin && git worktree add ~/IdeaProjects/sgspe-wt/f82a -b feat/fase-8-2a-tableros origin/main
cd ~/IdeaProjects/sgspe-wt/f82a && git show origin/docs/plan-8-2a:docs/superpowers/plans/2026-10-13-fase-8-2a-tableros.md \
  > docs/superpowers/plans/2026-10-13-fase-8-2a-tableros.md
grep -c "ASM-136" docs/requirements-analysis.md
grep -c '"name": "indicator"' model/model.json
grep -c "class KnowledgeMigration" src/main/kotlin/sgspe/ai/KnowledgeMigration.kt
```

Esperado: `1` o más, `0` y `1` (8.0 y F7 en `main`; los indicadores todavía no). Si el plan ya está en `main`, el
`git show` se omite. Si `model.json` ya tiene `indicator`, 8.1 lo trajo antes: se lee su `test_model.py` y se adapta el
Paso 3 en vez de repetirlo.

```bash
git add docs/superpowers/plans/2026-10-13-fase-8-2a-tableros.md
git commit -m "docs: plan 8.2a de indicadores y tableros" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch -c f82a/t1
```

#### Tramo A: el modelo y los permisos

- [ ] **Paso 2: Las pruebas de Python que fallan**

En `model/test_model.py`:

1. Debajo de `PHASE = "F7"`, agregar:

```python
# §2.2.1 de la especificación de F8: el modelo de los indicadores viaja con el plan 8.2a, antes que los entregables (8.1).
# 8.1 borra EARLY al poner PHASE = "F8"
EARLY = {"indicator": "F8", "indicator_measurement": "F8"}


def limit(name):
    """La fase hasta la que se compara un objeto: la suya si se adelanta (EARLY), si no PHASE."""
    return EARLY.get(name, PHASE)
```

2. En `ModelAgainstCardsTests`: `if card.phase <= PHASE and name in catalog]` pasa a
   `if card.phase <= limit(name) and name in catalog]`; `self.assertEqual(len(wanted), 84)` a
   `self.assertEqual(len(wanted), 86)`; `design_docs.field_phase(card, f) <= PHASE]` a
   `design_docs.field_phase(card, f) <= limit(name)]`; `design_docs.relationship_phase(self.cards, card, rel) <= PHASE:`
   a `design_docs.relationship_phase(self.cards, card, rel) <= limit(name):`; y
   `design_docs.action_phase(card, a) <= PHASE], name)` a `design_docs.action_phase(card, a) <= limit(name)], name)`.
3. `AiModelTests.test_f7_trae_dos_objetos_y_cuatro_relaciones` cuenta lo que había hasta F7 por las fases de las fichas:

```python
    def test_f7_trae_dos_objetos_y_cuatro_relaciones(self):
        # lo que había hasta F7, por las fases de las fichas: 8.2a suma los indicadores (IndicatorModelTests)
        cards = design_docs.cards()
        self.assertEqual(len([n for n in self.objects if cards[n].phase <= "F7"]), 84)
        relationships = [r for r in self.model["relationships"]
                         if design_docs.relationship_phase(cards, cards[r["source"]],
                                                           next(x for x in cards[r["source"]].relationships
                                                                if x["name"] == r["name"])) <= "F7"]
        self.assertEqual(len(relationships), 276)
        self.assertEqual(sum(len(o.get("actions", [])) for o in self.model["objects"]), 39)
```

4. Antes de `# la migración de la tabla fija de sgspe.ai`, agregar:

```python
class IndicatorModelTests(unittest.TestCase):
    """§4.1 de la especificación de F8 (plan 8.2a): indicator, catálogo del tenant, e indicator_measurement (apiOnly y
    appendOnly, con project opcional), sus dos relaciones, los permisos de security.md §7.8 y ninguna acción, workflow ni
    campo restringido."""

    @classmethod
    def setUpClass(cls):
        cls.model = load("model.json")
        cls.objects = {o["name"]: o for o in cls.model["objects"]}
        cls.relationships = {r["name"]: r for r in cls.model["relationships"]}
        cls.roles = load("roles.json")

    def test_el_modelo_trae_86_objetos_y_278_relaciones(self):
        self.assertEqual(len(self.objects), 86)
        self.assertEqual(len(self.model["relationships"]), 278)
        self.assertEqual([n for n in self.objects if n.startswith("indicator")], ["indicator", "indicator_measurement"])

    def test_el_catalogo_es_editable_y_la_medicion_inmutable_y_solo_por_su_endpoint(self):
        indicator, measurement = self.objects["indicator"], self.objects["indicator_measurement"]
        self.assertEqual((indicator.get("apiOnly", False), indicator.get("appendOnly", False)), (False, False))
        self.assertEqual((measurement.get("apiOnly"), measurement.get("appendOnly")), (True, True))
        self.assertEqual(measurement["uniqueConstraints"], [["indicator", "project", "period_start_date"]])
        code = next(f for f in indicator["fields"] if f["name"] == "code")
        self.assertEqual((code["type"], code.get("required"), code.get("unique")), ("TEXT", True, True))
        for name in ("indicator", "indicator_measurement"):
            self.assertFalse(os.path.isfile(os.path.join(HERE, "workflows", f"{name}.json")), name)
            self.assertNotIn("actions", self.objects[name], name)

    def test_la_medicion_cuelga_de_su_indicador_y_puede_ser_del_tenant(self):
        wanted = {"indicator_measurement_indicator": ("indicator", "indicator", True),
                  "indicator_measurement_project": ("project", "project", False)}
        for name, (field, target, required) in wanted.items():
            rel = self.relationships[name]
            self.assertEqual((rel["source"], rel["fieldName"], rel["target"], rel.get("required", False)),
                             ("indicator_measurement", field, target, required), name)

    def test_solo_social_manager_edita_el_catalogo_y_cierra_periodos(self):
        # security.md §7.8: READ para todos menos PROJECT_ADMIN y FIELD_AGENT; ADMIN, además, por ser ADMIN
        editors = {r["name"] for r in self.roles["roles"] if "UPDATE" in r["permisos"].get("indicator", [])}
        closers = {r["name"] for r in self.roles["roles"] if "CREATE" in r["permisos"].get("indicator_measurement", [])}
        self.assertEqual((editors, closers), ({"SOCIAL_MANAGER"}, {"SOCIAL_MANAGER"}))
        blind = {r["name"] for r in self.roles["roles"] if "indicator" not in r["permisos"] and "*" not in r["permisos"]}
        self.assertEqual(blind, {"PROJECT_ADMIN", "FIELD_AGENT"})

    def test_ningun_campo_restringido(self):
        # indicator_measurement la leen roles sin acceso a la postura: por eso el cierre no congela KPI-STK-02, 05 ni 06
        self.assertEqual([e for e in self.roles["restrictedFields"] if e["object"].startswith("indicator")], [])
```

En `model/test_apply.py`:

1. En `ModelOfF7Tests`, la primera prueba pasa a:

```python
    def test_the_model_has_the_84_objects_of_f7_and_the_two_indicators_and_is_valid(self):
        model = load(MODEL_PATH)
        self.assertEqual(len(model["objects"]), 86)
        self.assertEqual(apply.validate(model), [])
```

2. En `test_a_new_tenant_is_provisioned_in_order_and_again_is_a_no_op`: `"done: 399 created, 26 updated, 0 skipped",
   runs[0][1]` pasa a `"done: 403 created, 27 updated, 0 skipped", runs[0][1]` y `"done: 0 created, 0 updated, 399
   skipped", again[0][1]` a `"done: 0 created, 0 updated, 403 skipped", again[0][1]`.
3. `test_a_tenant_of_f2_moves_to_f7_in_one_run_and_a_second_run_changes_nothing` pasa a
   `test_a_tenant_of_f2_moves_to_the_model_in_one_run_and_a_second_run_changes_nothing`, con el comentario `# un tenant
   que quedó en F2 sube de una vez, sin pasar por F3 a F7`, y desde `f7 = quiet(apply.main, ["--model", MODEL_PATH,
   *login])` hasta el final de la prueba:

```python
        now = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertEqual(now[0], 0, now)
        self.assertIn("done: 266 created, 16 updated, 165 skipped", now[1])
        again = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertEqual(again[0], 0, again)
        self.assertIn("done: 0 created, 0 updated, 403 skipped", again[1])
```

4. En `test_a_tenant_of_f6_moves_to_f7_adding_only_the_two_objects_and_their_relationships`, la subida va al subconjunto
   de F7 (el modelo completo ya trae los indicadores). Desde `core.requests.clear()` hasta el final de la prueba:

```python
        core.requests.clear()
        with tempfile.TemporaryDirectory() as folder:
            f7_model = write_model(folder, phase_subset(load(MODEL_PATH), "F7"))
            f7 = quiet(apply.main, ["--model", f7_model, *login])
            again = quiet(apply.main, ["--model", f7_model, *login])
        self.assertEqual(f7[0], 0, f7)
        self.assertIn("done: 6 created, 0 updated, 393 skipped", f7[1])
        written = [line for line in f7[1].splitlines() if line.startswith(("create", "add", "update"))]
        self.assertEqual(written, ["create object ai_suggestion (201)", "create object ai_interaction (201)",
                                   "create relationship candidate_ai_interaction (201)",
                                   "create relationship ai_suggestion_project (201)",
                                   "create relationship ai_suggestion_ai_interaction (201)",
                                   "create relationship ai_interaction_project (201)"])
        self.assertEqual([p for m, p, _, _ in core.requests if m == "PUT" and p.startswith("/api/objects/")], [])
        self.assertIn("done: 0 created, 0 updated, 399 skipped", again[1])

    def test_a_tenant_of_f7_gets_the_indicators_and_nothing_else(self):
        # §2.2.1 de la especificación de F8 (plan 8.2a): el tenant demo, en F7, suma indicator, indicator_measurement, sus
        # dos relaciones y el único compuesto de la medición (el PUT de la fase 3, que nombra el campo de una relación);
        # ningún campo nuevo en un objeto anterior, y una segunda corrida no escribe
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        with tempfile.TemporaryDirectory() as folder:
            f7 = quiet(apply.main, ["--model", write_model(folder, phase_subset(load(MODEL_PATH), "F7")), *login])
        self.assertEqual(f7[0], 0, f7)
        self.assertIn("done: 399 created, 26 updated, 0 skipped", f7[1])
        core.requests.clear()
        now = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertEqual(now[0], 0, now)
        self.assertIn("done: 4 created, 1 updated, 399 skipped", now[1])
        written = [line for line in now[1].splitlines() if line.startswith(("create", "add", "update"))]
        self.assertEqual(written, ["create object indicator (201)", "create object indicator_measurement (201)",
                                   "create relationship indicator_measurement_indicator (201)",
                                   "create relationship indicator_measurement_project (201)",
                                   'update object indicator_measurement (uniqueConstraints=[["indicator", "project", '
                                   '"period_start_date"]], indexes=[])'])
        self.assertEqual([p for m, p, _, _ in core.requests if m == "PUT" and p.startswith("/api/objects/")],
                         ["/api/objects/indicator_measurement"])
        again = quiet(apply.main, ["--model", MODEL_PATH, *login])
        self.assertIn("done: 0 created, 0 updated, 403 skipped", again[1])
```

En `model/test_apply_roles.py`:

1. En `test_desde_f5_todos_los_roles_tienen_permisos`, el comentario dice `# la fase del modelo es la de su objeto más
   tardío: F8 desde el plan 8.2a (los indicadores); SOCIAL_INVESTMENT_MANAGER (F5) fue el` y
   `self.assertEqual(phase, "F7")` pasa a `self.assertEqual(phase, "F8")`.
2. En `test_un_tenant_de_f6_suma_la_ia`, la subida termina en el subconjunto de F7. Desde
   `self.assertEqual(test_apply.quiet(apply.main, ["--model", MODEL_PATH, *login])[0], 0)` (la última línea del `with`)
   hasta el final de la prueba:

```python
            f7_model = test_apply.write_model(folder, test_apply.phase_subset(load(MODEL_PATH), "F7"))
            f7_roles = os.path.join(folder, "roles-f7.json")
            with open(f7_roles, "w", encoding="utf-8") as f:
                json.dump(phase_roles(load(ROLES_PATH), load(f7_model)), f, ensure_ascii=False)
            self.assertEqual(test_apply.quiet(apply.main, ["--model", f7_model, *login])[0], 0)
            code, out, err = self.run_cli(roles_path=f7_roles, model_path=f7_model)
            again = self.run_cli(roles_path=f7_roles, model_path=f7_model)
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 13 updated, 2 skipped", out)
        self.assertEqual(sorted(line.split()[2] for line in out.splitlines() if line.startswith("skip   role")),
                         ["GIS_ANALYST", "SOCIAL_INVESTMENT_MANAGER"])
        self.assertIn("done: 0 created, 0 updated, 15 skipped", again[1])

    def test_un_tenant_de_f7_suma_los_indicadores(self):
        # plan 8.2a: apply_roles.py da READ sobre indicator e indicator_measurement a los doce roles de security.md §7.8 (y
        # UPDATE y CREATE a SOCIAL_MANAGER); PROJECT_ADMIN, FIELD_AGENT y AUDITOR («*» READ) no cambian
        import apply
        import test_apply
        login = ["--core", self.core.base_url, "--email", "admin@wasichai.local", "--password", "admin"]
        with tempfile.TemporaryDirectory() as folder:
            f7_model = test_apply.write_model(folder, test_apply.phase_subset(load(MODEL_PATH), "F7"))
            f7_roles = os.path.join(folder, "roles.json")
            with open(f7_roles, "w", encoding="utf-8") as f:
                json.dump(phase_roles(load(ROLES_PATH), load(f7_model)), f, ensure_ascii=False)
            self.assertEqual(test_apply.quiet(apply.main, ["--model", f7_model, *login])[0], 0)
            self.assertEqual(self.run_cli(roles_path=f7_roles, model_path=f7_model)[0], 0)
            self.assertEqual(test_apply.quiet(apply.main, ["--model", MODEL_PATH, *login])[0], 0)
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 12 updated, 3 skipped", out)
        self.assertEqual(sorted(line.split()[2] for line in out.splitlines() if line.startswith("skip   role")),
                         ["AUDITOR", "FIELD_AGENT", "PROJECT_ADMIN"])
        again = self.run_cli()
        self.assertIn("done: 0 created, 0 updated, 15 skipped", again[1])
```

En `model/test_publish_layers.py`, `test_la_particion_del_modelo_es_la_de_project_scope` pasa a:

```python
    def test_la_particion_del_modelo_es_la_de_project_scope(self):
        # Decisión 7 del plan 6.1: lo que no llega a project por sus campos RELATION son los 20 maestros del tenant y,
        # desde el plan 8.2a, el catálogo indicator (sin geometría: nunca es una capa)
        names = {o["name"] for o in MODEL["objects"]}
        tenant = names - publish_layers.project_bound(MODEL)
        self.assertEqual(len(tenant), 21)
        self.assertIn("indicator", tenant)
        self.assertLessEqual({e["object"] for e in LAYERS}, tenant)
```

En `model/test_design_docs.py`, en `test_los_maestros_del_tenant_de_security_9_7`, la lista termina en
`"local_conflict", "topic", "indicator"]))`.

- [ ] **Paso 3: Verlas fallar**

```bash
cd $REPO/model && python3 -m unittest test_model test_apply test_apply_roles test_publish_layers test_design_docs 2>&1 | \
  grep -E '^(FAIL|ERROR)' | sort | uniq -c | sort -rn | head -20
```

Esperado: fallan las pruebas de objetos, campos y relaciones de `ModelAgainstCardsTests` e `IndicatorModelTests`
(`KeyError: 'indicator'` o cuentas de 84 y 276), las cuentas de `test_apply.py` y `test_apply_roles.py`, la de
`test_publish_layers.py` (20) y la de `test_design_docs.py` (falta `indicator` en §9.7).

- [ ] **Paso 4: El script de un solo uso del modelo y los permisos**

Crear `$SCRATCH/gen_indicators_f8.py` (no se versiona):

```python
"""Plan 8.2a, Tarea 1 (de un solo uso, no se versiona): agrega indicator e indicator_measurement a model/model.json y
sus permisos de security.md §7.8 a model/roles.json. Correr una vez desde la raíz del repositorio."""
import json
import sys

OBJECTS = '''    {
      "name": "indicator",
      "label": "Indicador",
      "pluralLabel": "Indicadores",
      "description": "Indicador de monitoreo o KPI con definición, fórmula, desagregación, meta y frecuencia; objeto catálogo (BC-20).",
      "fields": [
        {"name": "code", "label": "Código del KPI", "type": "TEXT", "required": true, "unique": true, "description": "ID del KPI que define dashboards.md."},
        {"name": "name", "label": "Nombre", "type": "TEXT", "required": true, "description": "Nombre."},
        {"name": "definition", "label": "Definición", "type": "LONG_TEXT", "description": "Definición."},
        {"name": "formula", "label": "Fórmula", "type": "LONG_TEXT", "description": "Fórmula."},
        {"name": "verification_means", "label": "Medio de verificación", "type": "LONG_TEXT", "description": "Medio de verificación."},
        {"name": "disaggregation", "label": "Desagregación", "type": "TEXT", "description": "Desagregación."},
        {"name": "target_value", "label": "Meta", "type": "DECIMAL", "description": "Meta (GAP-018)."},
        {"name": "frequency", "label": "Frecuencia", "type": "TEXT", "description": "Frecuencia."},
        {"name": "source_reference", "label": "Fuente del indicador", "type": "TEXT", "description": "Fuente del indicador (por ejemplo, la Tabla 9.5 de DOC-01)."}
      ]
    },
    {
      "name": "indicator_measurement",
      "label": "Medición de indicador",
      "pluralLabel": "Mediciones de indicador",
      "description": "Cifra oficial e inmutable de un indicador en un periodo cerrado, por proyecto o para todo el tenant (ASM-042).",
      "appendOnly": true,
      "apiOnly": true,
      "uniqueConstraints": [["indicator", "project", "period_start_date"]],
      "fields": [
        {"name": "period_start_date", "label": "Inicio del periodo", "type": "DATE", "required": true, "description": "Inicio del periodo."},
        {"name": "period_end_date", "label": "Fin del periodo", "type": "DATE", "required": true, "description": "Fin del periodo."},
        {"name": "measured_value", "label": "Valor oficial", "type": "DECIMAL", "required": true, "description": "Valor oficial."},
        {"name": "numerator", "label": "Numerador", "type": "DECIMAL", "description": "Numerador."},
        {"name": "denominator", "label": "Denominador", "type": "DECIMAL", "description": "Denominador."},
        {"name": "breakdown", "label": "Desglose", "type": "LONG_TEXT", "description": "Desagregación en Markdown."},
        {"name": "closed_by_user_id", "label": "Usuario que cerró", "type": "UUID", "required": true, "description": "Usuario que cerró el periodo (ASM-023)."},
        {"name": "closed_by_user_email", "label": "Correo de quien cerró", "type": "EMAIL", "required": true, "description": "Correo de quien cerró el periodo."}
      ]
    },
'''
RELATIONSHIPS = '''    {"name": "indicator_measurement_indicator", "label": "Indicador", "inverseLabel": "Mediciones de indicador", "source": "indicator_measurement", "target": "indicator", "fieldName": "indicator", "required": true},
    {"name": "indicator_measurement_project", "label": "Proyecto", "inverseLabel": "Mediciones de indicador", "source": "indicator_measurement", "target": "project", "fieldName": "project"},
'''
OBJECT_ANCHOR = '    {\n      "name": "export_log",\n'
RELATIONSHIP_ANCHOR = '    {"name": "export_log_project",'

# security.md §7.8: R para todos menos PROJECT_ADMIN y FIELD_AGENT; SOCIAL_MANAGER edita el catálogo y cierra periodos
READERS = ["PROJECT_MANAGER", "COMMUNITY_RELATIONS_OFFICER", "STAKEHOLDER_MANAGER", "COMMITMENT_MANAGER",
           "GRIEVANCE_MANAGER", "SOCIAL_RISK_MANAGER", "SOCIAL_INVESTMENT_MANAGER", "GIS_ANALYST", "APPROVER", "ANALYST",
           "READ_ONLY"]
GRANTS = {name: ('["READ"]', '["READ"]') for name in READERS}
GRANTS["SOCIAL_MANAGER"] = ('["READ", "UPDATE"]', '["READ", "CREATE"]')


def model():
    with open("model/model.json", encoding="utf-8") as f:
        text = f.read()
    if '"name": "indicator"' in text:
        sys.exit("model.json ya tiene indicator")
    assert text.count(OBJECT_ANCHOR) == 1 and text.count(RELATIONSHIP_ANCHOR) == 1
    text = text.replace(OBJECT_ANCHOR, OBJECTS + OBJECT_ANCHOR).replace(RELATIONSHIP_ANCHOR, RELATIONSHIPS + RELATIONSHIP_ANCHOR)
    json.loads(text)
    with open("model/model.json", "w", encoding="utf-8") as f:
        f.write(text)


def roles():
    with open("model/roles.json", encoding="utf-8") as f:
        lines = f.read().split("\n")
    out, role, seen = [], None, set()
    for line in lines:
        stripped = line.strip()
        if stripped.startswith('"name": "'):
            role = stripped.split('"')[3]
        # justo después de "task": las filas de BC-15 van en el orden de las fichas (§19), antes de export_log
        if role in GRANTS and stripped.startswith('"task": ['):
            indent = line[: len(line) - len(line.lstrip())]
            last = not stripped.endswith(",")
            indicator, measurement = GRANTS[role]
            out += [line + ("," if last else ""), f'{indent}"indicator": {indicator},',
                    f'{indent}"indicator_measurement": {measurement}' + ("" if last else ",")]
            seen.add(role)
            continue
        out.append(line)
    assert seen == set(GRANTS), sorted(set(GRANTS) - seen)
    text = "\n".join(out)
    json.loads(text)
    with open("model/roles.json", "w", encoding="utf-8") as f:
        f.write(text)


if __name__ == "__main__":
    model()
    roles()
    print("ok")
```

Correrlo una vez:

```bash
cd $REPO && python3 "$SCRATCH/gen_indicators_f8.py" && git diff --stat model/
```

Esperado: `ok`, y `model/model.json` y `model/roles.json` cambiados (dos objetos y dos relaciones; 24 líneas de permisos
en 12 roles).

- [ ] **Paso 5: `security.md` §9.7**

En `docs/security.md`, en la fila de `ProjectScope.ROOTS` de la tabla de §9.7, el texto
`35 desde F7, con `ai_suggestion` y `ai_interaction`) | Su `project` es de mis proyectos; `evidence`,
`early_warning_rule`, `export_log` y, desde F7, `ai_suggestion` y `ai_interaction` sin `project`, siempre |` pasa a
`35 desde F7, con `ai_suggestion` y `ai_interaction`; 36 desde F8, con `indicator_measurement`) | Su `project` es de mis
proyectos; `evidence`, `early_warning_rule`, `export_log`, desde F7, `ai_suggestion` y `ai_interaction` y, desde F8,
`indicator_measurement` sin `project`, siempre |` (la fila sigue en una sola línea). En el bullet «Maestros del tenant»,
`` `local_conflict`, `topic`. Son los únicos objetos `` pasa a `` `local_conflict`, `topic` y, desde F8, `indicator`. Son
los únicos objetos ``, reenvolviendo el bullet a 120 columnas.

- [ ] **Paso 6: Verlas pasar y commit**

```bash
cd $REPO/model && python3 -m unittest test_model test_apply test_apply_roles test_publish_layers test_design_docs 2>&1 | tail -3
cd $REPO/model && python3 apply.py --validate-only && echo valid
cd $REPO && python3 tools/check_traceability.py
```

Esperado: `OK`, `valid` y `ok: 298 requisitos, todos con su fila`.

```bash
cd $REPO && git add model/model.json model/roles.json model/test_model.py model/test_apply.py model/test_apply_roles.py \
  model/test_publish_layers.py model/test_design_docs.py docs/security.md
git commit -m "feat: indicator e indicator_measurement en el modelo con sus permisos" -m "El modelo de los indicadores viaja con 8.2a (§2.2.1 de la especificación de F8): EARLY en
test_model.py, las filas de security.md §7.8 y los dos objetos clasificados en §9.7.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
```

#### Tramo B: el catálogo y su carga

- [ ] **Paso 7: Las pruebas de la carga y del catálogo, que fallan**

Crear `model/test_load_indicators.py`:

```python
"""Pruebas de load_indicators.py y de model/indicators.json (RQ-DSH-037, ASM-136; dashboards.md §4 y §10): el catálogo
tiene cada KPI de dashboards.md §4 con las metas y frecuencias de DOC-01, y la carga crea lo que falta, actualiza solo los
textos de documentación y nunca sobrescribe la meta ni la frecuencia que el cliente editó.

Correr: cd model && python3 -m unittest -v test_load_indicators
"""
import io
import json
import os
import re
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import load_indicators
from fake_core import FakeCore

HERE = os.path.dirname(os.path.abspath(__file__))
DASHBOARDS = os.path.join(os.path.dirname(HERE), "docs", "dashboards.md")
SAMPLE = {"indicators": [
    {"code": "KPI-ENG-02", "name": "Reuniones sostenidas al mes", "definition": "Reuniones frente a la meta",
     "formula": "Por mes", "disaggregation": "Proyecto, periodo", "verification_means": "Reporte de reuniones mensuales",
     "source_reference": "dashboards.md §4.3; DOC-01 Tabla 9.5, #5", "frequency": "Mensual", "target_value": 2},
    {"code": "KPI-COM-02", "name": "Compromisos abiertos", "definition": "Los que no se cierran", "formula": "Número",
     "disaggregation": "Proyecto", "verification_means": None, "source_reference": "dashboards.md §4.4",
     "frequency": None, "target_value": None},
]}


def doc_kpis():
    """Los KPI de dashboards.md §4 (primera celda de cada fila) y {KPI: (número, medio de verificación)} de §4.9."""
    with open(DASHBOARDS, encoding="utf-8") as f:
        lines = f.read().splitlines()
    body = lines[lines.index("## 4. Catálogo de KPIs"):lines.index("## 5. Tableros")]
    codes = [line.split("|")[1].strip() for line in body if line.startswith("| KPI-")]
    table = {}
    for line in body:
        if re.match(r"^\| \d+ \|", line):
            cells = [c.strip() for c in line.strip().strip("|").split("|")]
            table[cells[2]] = (int(cells[0]), cells[4])
    return codes, table


def write(folder, data):
    path = os.path.join(folder, "indicators.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    return path


class CatalogFileTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.items = load_indicators.read_catalog(os.path.join(HERE, "indicators.json"))
        cls.by_code = {i["code"]: i for i in cls.items}
        cls.codes, cls.table95 = doc_kpis()

    def test_trae_cada_kpi_de_dashboards_4_en_su_orden(self):
        self.assertEqual([i["code"] for i in self.items], self.codes)
        self.assertEqual(len(self.items), 53)

    def test_las_trece_filas_de_la_tabla_9_5_llevan_su_medio_de_verificacion_y_su_numero(self):
        self.assertEqual(len(self.table95), 13)
        for code, (number, means) in self.table95.items():
            self.assertEqual(self.by_code[code]["verification_means"], means, code)
            self.assertIn(f"DOC-01 Tabla 9.5, #{number}", self.by_code[code]["source_reference"], code)
        others = [c for c, i in self.by_code.items() if c not in self.table95 and i["verification_means"] is not None]
        self.assertEqual(others, [])

    def test_las_metas_y_frecuencias_son_las_de_doc_01(self):
        # ASM-136: KPI-ENG-02, dos al mes; evaluaciones de grupos de interés y Tabla 9.5, mensuales; riesgos, semanales
        self.assertEqual({c: i["target_value"] for c, i in self.by_code.items() if i["target_value"] is not None},
                         {"KPI-ENG-02": 2})
        monthly = set(self.table95) | {"KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-08"}
        weekly = {f"KPI-RSK-0{n}" for n in range(1, 6)}
        self.assertEqual({c for c, i in self.by_code.items() if i["frequency"] == "Mensual"}, monthly)
        self.assertEqual({c for c, i in self.by_code.items() if i["frequency"] == "Semanal"}, weekly)
        self.assertEqual({c for c, i in self.by_code.items() if i["frequency"] is None},
                         set(self.by_code) - monthly - weekly)

    def test_ningun_nombre_ni_texto_real_de_persona(self):
        # RQ-SEC-018: solo textos de dashboards.md, sin correos ni nombres
        text = json.dumps(self.items, ensure_ascii=False)
        self.assertNotIn("@", text)


class ReadCatalogTests(unittest.TestCase):
    def refused(self, data):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(load_indicators.CatalogError) as raised:
                load_indicators.read_catalog(write(folder, data))
        return str(raised.exception)

    def test_rechaza_un_codigo_mal_formado_o_repetido_una_meta_negativa_y_una_clave_desconocida(self):
        item = SAMPLE["indicators"][0]
        self.assertIn("KPI-XXX-NN", self.refused({"indicators": [{**item, "code": "KPI-1"}]}))
        self.assertIn("repeated", self.refused({"indicators": [item, item]}))
        self.assertIn("target_value", self.refused({"indicators": [{**item, "target_value": -1}]}))
        self.assertIn("target_value", self.refused({"indicators": [{**item, "target_value": "2"}]}))
        self.assertIn("unknown keys", self.refused({"indicators": [{**item, "unit": "reuniones"}]}))
        self.assertIn("name is required", self.refused({"indicators": [{**item, "name": " "}]}))
        self.assertIn("non-empty", self.refused({"indicators": []}))


class LoadTests(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)
        self.folder = tempfile.TemporaryDirectory()
        self.addCleanup(self.folder.cleanup)
        self.path = write(self.folder.name, SAMPLE)

    def run_cli(self, *extra):
        args = ["--file", self.path, "--core", self.core.base_url, "--email", "admin@wasichai.local",
                "--password", "admin", *extra]
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = load_indicators.main(args)
        return code, out.getvalue(), err.getvalue()

    def stored(self):
        return {r["attributes"]["code"]: r["attributes"] for r in self.core.records.get("indicator", [])}

    def writes(self):
        return [(m, p) for m, p, _, _ in self.core.requests if m in ("POST", "PUT") and p != "/api/auth/login"]

    def test_crea_los_que_faltan_con_su_meta_y_frecuencia_iniciales_y_sin_los_null(self):
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 2 created, 0 updated, 0 skipped", out)
        stored = self.stored()
        self.assertEqual((stored["KPI-ENG-02"]["target_value"], stored["KPI-ENG-02"]["frequency"]), (2, "Mensual"))
        self.assertNotIn("target_value", stored["KPI-COM-02"])

    def test_la_segunda_corrida_no_escribe_nada(self):
        self.run_cli()
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 0 updated, 2 skipped", out)
        self.assertEqual(self.writes(), [])

    def test_una_meta_o_una_frecuencia_editada_por_el_cliente_no_se_toca_ni_cuando_cambian_los_textos(self):
        # Review Focus: el ADMIN subió la meta a 3, borró la frecuencia y la documentación de dashboards.md cambió
        self.run_cli()
        ours = self.stored()["KPI-ENG-02"]
        ours["target_value"] = 3
        ours.pop("frequency")
        changed = json.loads(json.dumps(SAMPLE))
        changed["indicators"][0]["definition"] = "Reuniones realizadas frente a la meta del mes"
        self.path = write(self.folder.name, changed)
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 1 updated, 1 skipped", out)
        after = self.stored()["KPI-ENG-02"]
        self.assertEqual(after["definition"], "Reuniones realizadas frente a la meta del mes")
        self.assertEqual(after["target_value"], 3)
        self.assertIsNone(after.get("frequency"))
        self.assertEqual(len(self.writes()), 1)

    def test_un_indicador_que_el_cliente_agrego_no_se_borra(self):
        self.core.add_record("indicator", {"code": "KPI-XYZ-01", "name": "Indicador propio del cliente"})
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertIn("KPI-XYZ-01", self.stored())
        self.assertEqual([m for m, _, _, _ in self.core.requests if m == "DELETE"], [])

    def test_un_rechazo_de_core_sale_con_1(self):
        self.core.fail_on_record = "indicator"
        code, _, err = self.run_cli()
        self.assertEqual(code, 1)
        self.assertIn("error -> 400", err)

    def test_dry_run_y_validate_only_no_llaman_a_core(self):
        for flag in ("--dry-run", "--validate-only"):
            code, out, err = self.run_cli(flag)
            self.assertEqual(code, 0, err)
        self.assertEqual(self.core.requests, [])

    def test_un_archivo_invalido_sale_con_2_sin_llamar_a_core(self):
        self.path = write(self.folder.name, {"indicators": [{"code": "x"}]})
        code, _, err = self.run_cli()
        self.assertEqual(code, 2)
        self.assertIn("KPI-XXX-NN", err)
        self.assertEqual(self.core.requests, [])


if __name__ == "__main__":
    unittest.main()
```

En `model/test_provision_tenant.py`, cada lista de pasos suma `"load_indicators.py"` después de `"load_ubigeo.py"` (las
líneas 33, 53, 63 y 66: la tupla de `steps`, las dos listas de `calls` y `names` de
`test_los_pasos_reales_terminan_con_la_publicacion_de_las_capas`), el docstring dice «los seis pasos» y
`test_crea_el_tenant_como_el_operador_y_corre_los_cinco_pasos_como_su_administrador` pasa a
`test_crea_el_tenant_como_el_operador_y_corre_los_seis_pasos_como_su_administrador`.

En `model/test_upgrade_tenants.py`: el docstring de `steps` dice `"""Los seis pasos; …`; la tupla de nombres suma
`"load_indicators.py"` antes de `"publish_layers.py"`; en `test_recorre_cada_tenant_con_su_administrador_y_en_orden`,
`15` pasa a `18`, `self.calls[:5]` a `self.calls[:6]` (con `"load_indicators.py"` en la lista esperada, antes de
`"publish_layers.py"`) y `self.calls[::5]` a `self.calls[::6]`; en `test_sin_geoserver_cada_tenant_sube_sin_publicar_las_capas`,
`12` pasa a `15`.

```bash
cd $REPO/model && python3 -m unittest test_load_indicators test_provision_tenant test_upgrade_tenants 2>&1 | tail -3
```

Esperado: `ModuleNotFoundError: No module named 'load_indicators'` y fallas de las listas de pasos.

- [ ] **Paso 8: El catálogo, generado una vez desde `dashboards.md`**

Crear `$SCRATCH/gen_indicators_json.py` (no se versiona):

```python
"""Plan 8.2a, Tarea 1 (de un solo uso, no se versiona): escribe model/indicators.json desde docs/dashboards.md §4 (las
filas de cada KPI) y §4.9 (los medios de verificación de la Tabla 9.5), con las metas y frecuencias de DOC-01 (ASM-136).
Correr una vez desde la raíz del repositorio."""
import json
import re

DOC = "docs/dashboards.md"
# ASM-136 (GAP-018 resuelta con DOC-01): KPI-ENG-02 dos reuniones al mes; las evaluaciones de grupos de interés y los
# indicadores de la Tabla 9.5, mensuales; las revisiones de riesgo, semanales; los demás, sin meta ni frecuencia
ASSESSMENTS = {"KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-STK-08"}
RISK_REVIEWS = {"KPI-RSK-01", "KPI-RSK-02", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05"}


def cells(line):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def main():
    lines = open(DOC, encoding="utf-8").read().splitlines()
    start = lines.index("## 4. Catálogo de KPIs")
    end = lines.index("## 5. Tableros")
    section, rows, table95 = None, [], {}
    for line in lines[start:end]:
        if line.startswith("### "):
            section = line[4:].split(" ")[0]
        elif line.startswith("| KPI-"):
            rows.append((section, cells(line)))
        elif re.match(r"^\| \d+ \|", line):
            number, _label, kpi, _rq, means, _deliverable, _phase = cells(line)
            table95[kpi] = (int(number), means)
    out = []
    for section, (code, name, definition, formula, _source, filters, *_rest) in rows:
        number, means = table95.get(code, (None, None))
        reference = f"dashboards.md §{section}" + (f"; DOC-01 Tabla 9.5, #{number}" if number else "")
        monthly = number is not None or code in ASSESSMENTS
        out.append({
            "code": code,
            # el nombre sin el «#n » de la Tabla 9.5, que va en source_reference
            "name": re.sub(r"^#\d+ ", "", name),
            "definition": definition,
            "formula": formula,
            "disaggregation": filters,
            "verification_means": means,
            "source_reference": reference,
            "frequency": "Mensual" if monthly else "Semanal" if code in RISK_REVIEWS else None,
            "target_value": 2 if code == "KPI-ENG-02" else None,
        })
    with open("model/indicators.json", "w", encoding="utf-8") as f:
        json.dump({"indicators": out}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"{len(out)} indicadores, {len(table95)} de la Tabla 9.5")


if __name__ == "__main__":
    main()
```

```bash
cd $REPO && python3 "$SCRATCH/gen_indicators_json.py"
```

Esperado: `53 indicadores, 13 de la Tabla 9.5`. Desde aquí `model/indicators.json` se edita a mano; un KPI nuevo de
`dashboards.md` §4 hace fallar `test_trae_cada_kpi_de_dashboards_4_en_su_orden` hasta que se agrega.

- [ ] **Paso 9: `load_indicators.py` y el paso de aprovisionamiento**

Crear `model/load_indicators.py`:

```python
#!/usr/bin/env python3
"""Carga el catálogo de indicadores en indicator (RQ-DSH-037, ASM-136; dashboards.md §10).

Solo biblioteca estándar, con el patrón de load_ubigeo.py. Lee model/indicators.json ({"indicators": [...]}, un elemento
por KPI de dashboards.md §4 con code, name, definition, formula, disaggregation, verification_means, source_reference,
frequency y target_value) y, por código:

- un indicador que core no tiene se crea con todos sus campos, también la meta y la frecuencia iniciales de DOC-01;
- uno que ya existe se actualiza solo si cambió un texto de documentación (name, definition, formula, disaggregation,
  verification_means, source_reference), con un PUT que repite lo guardado de target_value y frequency: la meta y la
  frecuencia son del cliente desde que se cargan, y el script nunca las sobrescribe ni las borra;
- los demás se saltan. Nunca borra un indicador ni toca indicator_measurement.

Corre después de load_ubigeo.py (security.md §4.1), con el administrador del tenant.

Correr: python3 load_indicators.py [--file F] [--dry-run | --validate-only] [--core URL] [--email E] [--password P]
Salida: 0 si va bien, 1 si core rechaza algo o no responde, 2 si el archivo falta o no es válido.
"""
import argparse
import json
import os
import re
import sys

from core_client import Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))
OBJECT = "indicator"
CODE = re.compile(r"^KPI-[A-Z]{3}-\d{2}$")
DOCUMENTATION = ("name", "definition", "formula", "disaggregation", "verification_means", "source_reference")
# del cliente desde que se cargan (ASM-136): solo van en el alta
OWNED = ("target_value", "frequency")
KEYS = {"code", *DOCUMENTATION, *OWNED}


class CatalogError(Exception):
    pass


def read_catalog(path):
    """Los indicadores del archivo en su orden, validados: código KPI-XXX-NN único, nombre, textos o null, una meta
    numérica no negativa o null y una frecuencia de texto o null. Lanza CatalogError con todos los problemas."""
    try:
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError) as e:
        raise CatalogError(f"{path}: {e}") from e
    items = data.get("indicators") if isinstance(data, dict) else None
    if not isinstance(items, list) or not items:
        raise CatalogError(f"{path}: needs a non-empty 'indicators' list")
    errors, seen = [], set()
    for i, item in enumerate(items):
        where = f"indicators[{i}]"
        if not isinstance(item, dict):
            errors.append(f"{where}: must be an object")
            continue
        unknown = sorted(set(item) - KEYS)
        if unknown:
            errors.append(f"{where}: unknown keys {unknown}")
        code = item.get("code")
        if not isinstance(code, str) or not CODE.fullmatch(code):
            errors.append(f"{where}: code must look like KPI-XXX-NN")
        elif code in seen:
            errors.append(f"{where}: code {code} is repeated")
        else:
            seen.add(code)
        if not isinstance(item.get("name"), str) or not item["name"].strip():
            errors.append(f"{where}: name is required")
        for key in (*DOCUMENTATION[1:], "frequency"):
            if item.get(key) is not None and not isinstance(item[key], str):
                errors.append(f"{where}: {key} must be text or null")
        target = item.get("target_value")
        if target is not None and (isinstance(target, bool) or not isinstance(target, (int, float)) or target < 0):
            errors.append(f"{where}: target_value must be a number >= 0 or null")
    if errors:
        raise CatalogError("; ".join(errors))
    return items


def attributes_of(item):
    """Lo que se manda en el alta: todos los campos, sin los null."""
    return {k: item.get(k) for k in ("code", *DOCUMENTATION, *OWNED) if item.get(k) is not None}


def load(client, items):
    """Crea, actualiza o salta cada indicador. Devuelve (creados, actualizados, saltados)."""
    existing = {r["attributes"].get("code"): r for r in client.list_all(OBJECT)}
    created = updated = skipped = 0
    for item in items:
        current = existing.get(item["code"])
        if current is None:
            client.post(f"/api/objects/{OBJECT}/records", {"attributes": attributes_of(item)})
            print(f"create {item['code']}")
            created += 1
            continue
        stored = current["attributes"]
        if all(stored.get(k) == item.get(k) for k in DOCUMENTATION):
            skipped += 1
            continue
        # el PUT de wasichai reemplaza cada campo: se repite lo guardado de la meta y la frecuencia, tal cual
        attributes = {"code": item["code"], **{k: item.get(k) for k in DOCUMENTATION}, **{k: stored.get(k) for k in OWNED}}
        client.put(f"/api/objects/{OBJECT}/records/{current['id']}", {"attributes": attributes})
        print(f"update {item['code']} (documentation)")
        updated += 1
    return created, updated, skipped


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Carga el catálogo de indicadores en indicator.")
    p.add_argument("--file", default=os.path.join(HERE, "indicators.json"))
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    mode = p.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="cuenta lo que cargaría; no llama a core")
    mode.add_argument("--validate-only", action="store_true", help="solo valida el archivo; no llama a core")
    return p.parse_args(argv)


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    try:
        items = read_catalog(args.file)
    except CatalogError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    if args.validate_only:
        return 0
    targets = sum(1 for i in items if i.get("target_value") is not None)
    print(f"indicators: {len(items)} ({targets} with a target)")
    if args.dry_run:
        return 0
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
        created, updated, skipped = load(client, items)
    except CoreError as e:
        print(f"error -> {e.status}\n{e.body}", file=sys.stderr)
        return 1
    print(f"done: {created} created, {updated} updated, {skipped} skipped")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

En `model/tenant_steps.py`: la primera frase del docstring dice «…los workflows (con los tipos de documento y las
automatizaciones), el ubigeo, desde F8 el catálogo de indicadores y, desde F6, las capas públicas de GeoServer.»; se
importa `load_indicators` (en orden alfabético, antes de `load_ubigeo`), y `STEPS` suma
`("load_indicators.py", lambda argv: load_indicators.main(argv)),` entre la línea de `load_ubigeo.py` y la de `LAYERS`.

En `model/provision_tenant.py`, el paso 2 del docstring nombra `load_ubigeo.py, load_indicators.py y publish_layers.py`;
en `model/upgrade_tenants.py`, la primera línea del docstring dice «…repite en cada uno los pasos 2 a 7 de security.md
§4.1 (ASM-109).» (con `load_indicators.py` son siete).

- [ ] **Paso 10: Verlas pasar y commit**

```bash
cd $REPO/model && python3 -m unittest 2>&1 | tail -3
cd $REPO/model && python3 load_indicators.py --validate-only && python3 load_indicators.py --dry-run
```

Esperado: `Ran 322 tests` y `OK` (si 8.1 llegó antes a `main`, otra cifra, siempre `OK`), y `indicators: 53 (1 with a
target)`.

```bash
cd $REPO && git add model/indicators.json model/load_indicators.py model/test_load_indicators.py model/tenant_steps.py \
  model/provision_tenant.py model/upgrade_tenants.py model/test_provision_tenant.py model/test_upgrade_tenants.py
git commit -m "feat: catálogo de indicadores con las metas de DOC-01 y su carga" -m "model/indicators.json sale de dashboards.md §4; load_indicators.py crea lo que falta, actualiza
la documentación y nunca toca target_value ni frequency (ASM-136). Paso nuevo tras load_ubigeo.py.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
```

#### Tramo C: la clasificación en Kotlin

- [ ] **Paso 11: Las pruebas de Kotlin que fallan**

Crear `src/test/kotlin/sgspe/scope/ProjectScopeF8Test.kt`:

```kotlin
package sgspe.scope

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// la clasificación de los indicadores (§4.1 de la especificación de F8, security.md §9.7): el catálogo indicator es un maestro
// del tenant, que todos leen sin asignación; la medición es una raíz con `project` opcional: sin proyecto es del tenant, con
// proyecto se acota por él. desde 8.2a, 36 raíces y 21 maestros
class ProjectScopeF8Test {
    private val project = UUID.randomUUID()
    private val catalog = ModelCatalog.fromClasspath()

    private fun resolve(before: Map<String, Any?>) =
        runBlocking {
            val write =
                RecordWrite(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    MEASUREMENT,
                    UUID.randomUUID(),
                    RecordChangeKind.UPDATED,
                    before,
                    null,
                    null,
                    null
                )
            ProjectResolver.resolve(write, before, catalog::isRequiredRelation) { _, _ -> null }
        }

    @Test
    fun `el catalogo es del tenant y la medicion una raiz con proyecto opcional`() {
        assertEquals(ReadGroup.TENANT, ProjectScope.readGroup("indicator"))
        assertEquals(ReadGroup.ROOT, ProjectScope.readGroup(MEASUREMENT))
        assertFalse(catalog.isRequiredRelation(MEASUREMENT, ProjectScope.PROJECT))
        assertEquals(36, ProjectScope.ROOTS.size)
        assertEquals(21, ProjectScope.TENANT.size)
    }

    @Test
    fun `una medicion del tenant no pide asignacion y una de proyecto se acota por el`() {
        val tenant = resolve(mapOf("project" to null))
        assertFalse(tenant.unresolved)
        assertTrue(tenant.projects.isEmpty())
        assertEquals(setOf(project), resolve(mapOf("project" to project.toString())).projects)
    }

    companion object {
        private const val MEASUREMENT = "indicator_measurement"
    }
}
```

En `src/test/kotlin/sgspe/scope/ProjectScopeF7Test.kt`: el comentario termina en «…con proyecto, se acotan por él.
desde F7, 35 raíces y 27 hijos; desde 8.2a, 36 raíces (ProjectScopeF8Test)» y `assertEquals(35,
ProjectScope.ROOTS.size)` pasa a `assertEquals(36, ProjectScope.ROOTS.size)`.

En `src/test/kotlin/sgspe/contract/TenantProvisioningApiTest.kt`: el comentario de clase dice «…con el modelo, los
roles, los workflows, los tipos de documento, las automatizaciones y el catálogo de indicadores de F8»; el comentario del
modelo empieza `// F8 (8.2a): 86 objetos, 39 acciones declaradas (13 EXPORT de las capas territoriales) y 278 relaciones;
26 objetos reciben`; `"done: 399 created, 26 updated, 0 skipped"` pasa a `"done: 403 created, 27 updated, 0 skipped"` y
`"done: 0 created, 0 updated, 399 skipped"` a `"done: 0 created, 0 updated, 403 skipped"`; y antes de `val declared =`
se agrega:

```kotlin
        // el catálogo de indicadores (dashboards.md §10, ASM-136): 53 creados y, otra vez, ninguno escrito
        val indicators = runScript(listOf("model/load_indicators.py"), admin)
        assertEquals(0, indicators.exit, indicators.output)
        assertTrue(indicators.output.contains("done: 53 created, 0 updated, 0 skipped"), indicators.output)
        val indicatorsAgain = runScript(listOf("model/load_indicators.py"), admin)
        assertTrue(indicatorsAgain.output.contains("done: 0 created, 0 updated, 53 skipped"), indicatorsAgain.output)
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.ProjectScopeF8Test' --tests 'sgspe.ArchitectureTest' --tests 'sgspe.common.AmountFieldsGuardTest'
```

Esperado: falla `ProjectScopeF8Test` (`UNCLASSIFIED`), `ArchitectureTest` «cada objeto del modelo esta clasificado en
ProjectScope» y la prueba de `AmountFieldsGuardTest` que exige clasificar cada DECIMAL.

- [ ] **Paso 12: La clasificación y los decimales**

En `src/main/kotlin/sgspe/scope/ProjectScope.kt`: el comentario de la cabecera dice «…(export_log y, desde F3, evidence,
desde F4, early_warning_rule, desde F7, ai_suggestion y ai_interaction y, desde F8, indicator_measurement) tienen
`project` opcional…»; en `ROOTS`, después de `"ai_interaction"`, agregar:

```kotlin
            "ai_interaction",
            // F8 (plan 8.2a): la cifra oficial de un periodo, de un proyecto o, sin project, de todo el tenant
            "indicator_measurement"
```

(la coma nueva va en la línea de `"ai_interaction"`), y en `TENANT`, después de `"topic"`:

```kotlin
            "topic",
            // F8 (plan 8.2a): el catálogo de los KPI con sus metas (dashboards.md §10), compartido por los proyectos
            "indicator"
```

En `src/main/kotlin/sgspe/common/AmountFieldsGuard.kt`, al final de `NOT_MONEY` (después de la fila de
`ai_interaction.confidence`, que gana su coma):

```kotlin
                "ai_interaction.confidence" to "confianza de la llamada a la IA, de 0 a 1",
                "indicator.target_value" to "meta de un KPI en su unidad: un conteo o un porcentaje",
                "indicator_measurement.measured_value" to "cifra oficial de un KPI en su unidad",
                "indicator_measurement.numerator" to "numerador de la cifra oficial: un conteo",
                "indicator_measurement.denominator" to "denominador de la cifra oficial: un conteo o una meta"
```

- [ ] **Paso 13: Verlas pasar, build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.contract.TenantProvisioningApiTest`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/scope/ProjectScope.kt src/main/kotlin/sgspe/common/AmountFieldsGuard.kt \
  src/test/kotlin/sgspe/scope/ProjectScopeF8Test.kt src/test/kotlin/sgspe/scope/ProjectScopeF7Test.kt \
  src/test/kotlin/sgspe/contract/TenantProvisioningApiTest.kt
git commit -m "feat: los indicadores clasificados en ProjectScope" -m "indicator es maestro del tenant e indicator_measurement raíz con project opcional; sus DECIMAL
no son dinero; el alta de un tenant contra wasichai carga el catálogo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t1 -m "Merge branch 'f82a/t1' into feat/fase-8-2a-tableros"
```

---

### Tarea 2: La base `F8IndicatorsApiTest`, `IndicatorWriteGuard` y el modelo contra wasichai

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/IndicatorWriteGuard.kt`
- Test: `src/test/kotlin/sgspe/dashboard/IndicatorWriteGuardTest.kt`, `src/test/kotlin/sgspe/F8IndicatorsApiTest.kt`,
  `src/test/kotlin/sgspe/dashboard/IndicatorModelApiTest.kt`

**Interfaces:**
- Consumes: el modelo de la Tarea 1; de las bases, `project`, `member`, `organization`, `move`, `record`, `sample`,
  `createRecord`, `exchange`, `send`, `tree`, `runScript`, `platformCreate`, `platformRecords`, `organizationId` y
  `uniqueName`.
- Produces: `IndicatorWriteGuard` (`400 FIELD_NOT_EDITABLE` sobre `code`) y la base de «Pruebas» de «Interfaces».

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t2
```

- [ ] **Paso 2: La unitaria de la guarda, que falla**

Crear `src/test/kotlin/sgspe/dashboard/IndicatorWriteGuardTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.WriteView
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// dashboards.md §10: el código de un indicador es el ID de su KPI y no cambia (400 FIELD_NOT_EDITABLE sobre code); la meta,
// la frecuencia y los textos sí
class IndicatorWriteGuardTest {
    // WriteView como la real en lo que mira la guarda: un cambio es lo que manda quien escribe
    private val view: WriteView =
        mock(WriteView::class.java) { call ->
            if (call.method.name != "after") return@mock null
            (call.arguments[0] as RecordWrite).attributes.orEmpty()
        }
    private val guard = IndicatorWriteGuard(view)

    private fun findings(
        objectName: String,
        kind: RecordChangeKind,
        before: Map<String, Any?>?,
        after: Map<String, Any?>
    ) = runBlocking {
        guard.findings(RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), kind, before, after))
    }

    @Test
    fun `cambiar el codigo es 400 FIELD_NOT_EDITABLE y cambiar la meta pasa`() {
        val before = mapOf("code" to "KPI-ENG-02", "name" to "Reuniones", "target_value" to 2)
        val code = findings("indicator", RecordChangeKind.UPDATED, before, before + ("code" to "KPI-ENG-99")).single()
        assertEquals(Triple("FIELD_NOT_EDITABLE", "code", 400), Triple(code.code, code.field, code.status.value()))
        assertEquals(emptyList<Any>(), findings("indicator", RecordChangeKind.UPDATED, before, before + ("target_value" to 3)))
    }

    @Test
    fun `el alta, otro objeto y un vinculo no se juzgan`() {
        val created = mapOf("code" to "KPI-XYZ-01", "name" to "Propio")
        assertEquals(emptyList<Any>(), findings("indicator", RecordChangeKind.CREATED, null, created))
        assertEquals(emptyList<Any>(), findings("topic", RecordChangeKind.UPDATED, mapOf("code" to "A"), mapOf("code" to "B")))
        assertEquals(emptyList<Any>(), findings("indicator", RecordChangeKind.UPDATED, mapOf("code" to "KPI-ENG-02"), mapOf("rel:x" to "y")))
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.IndicatorWriteGuardTest'
```

Esperado: no compila (`Unresolved reference: IndicatorWriteGuard`).

- [ ] **Paso 3: La guarda**

Crear `src/main/kotlin/sgspe/dashboard/IndicatorWriteGuard.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.RuleGuard
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// el código de un indicador es el ID de su KPI en dashboards.md (§10): los tableros, el cierre y load_indicators.py lo buscan
// por ese código, así que no cambia (400 FIELD_NOT_EDITABLE). la meta, la frecuencia y los textos los edita quien tiene UPDATE
@Component
@Order(GuardOrder.DOMAIN)
class IndicatorWriteGuard(
    private val view: WriteView
) : RuleGuard() {
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != INDICATOR || change.kind != RecordChangeKind.UPDATED || change.isLink()) return emptyList()
        val before = Values.text(change.before?.get(CODE)) ?: return emptyList()
        if (Values.text(view.after(change)[CODE]) == before) return emptyList()
        return listOf(
            Finding("FIELD_NOT_EDITABLE", "El código de un indicador no cambia: es el ID de su KPI en dashboards.md", CODE, status = HttpStatus.BAD_REQUEST)
        )
    }

    companion object {
        const val INDICATOR = "indicator"
        const val CODE = "code"
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.IndicatorWriteGuardTest'
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 4: La base de las pruebas de integración**

Crear `src/test/kotlin/sgspe/F8IndicatorsApiTest.kt`:

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.http.HttpStatus
import sgspe.common.LIMA
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

// la base de las pruebas de integración del plan 8.2a (especificación de F8 §5): lo de F6ApiTest más el catálogo de
// indicadores cargado una vez por JVM con model/load_indicators.py (como el aprovisionamiento de SgspeApiTest), un proyecto
// en active, el id de un indicador por su código, el cierre de un periodo por su endpoint y el mes pasado de Lima (un periodo
// que ya terminó). todo ficticio; las ayudas son bloqueantes y escriben como el admin salvo que se pase otro token
abstract class F8IndicatorsApiTest : F6ApiTest() {
    @BeforeEach
    fun indicatorCatalog() {
        synchronized(CATALOG_LOCK) {
            if (!loaded) {
                val run = runScript(listOf("model/load_indicators.py"))
                assertEquals(0, run.exit, "python3 model/load_indicators.py\n${run.output}")
                loaded = true
            }
        }
    }

    // el id del indicador con ese código (KPI-…), leído como el admin
    protected fun indicatorId(code: String): String =
        tree(send("GET", "/api/objects/indicator/records?code=$code", null, HttpStatus.OK))["content"].values().single()["id"].asString()

    protected fun get(
        path: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", path, null, HttpStatus.OK, token))

    // «Proyecto Ejemplo A» en active (WF-A): con CRS de trabajo y una parte CLIENTE, como pide approve (ASM-062), enviado y
    // aprobado por un PROJECT_ADMIN asignado; `attributes` suma parámetros (meetings_per_month_target)
    protected fun activeProject(
        code: String = uniqueName("PRY-EJ-"),
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val project = project(mapOf("code" to code, "name" to "Proyecto Ejemplo A", "working_crs" to "EPSG 32717") + attributes)
        createRecord("project_party", mapOf("project" to project, "organization" to organization(), "party_role" to "CLIENTE"))
        val admin = member("PROJECT_ADMIN", project)
        move("project", project, "submit_for_review", admin.token)
        move("project", project, "approve", admin.token)
        return project
    }

    // el mes pasado en Lima: un periodo que ya terminó (PERIOD_NOT_ENDED no aplica)
    protected fun lastMonth(): Pair<LocalDate, LocalDate> {
        val month = YearMonth.now(LIMA).minusMonths(1)
        return month.atDay(1) to month.atEndOfMonth()
    }

    // un mes ya terminado al azar entre 1950 y 2019: las mediciones del tenant (sin project) no chocan entre clases ni entre
    // corridas sobre la misma base, porque el único compuesto no compara los NULL (WAS-ADR-0037) y el cierre sí
    protected fun pastMonth(): Pair<LocalDate, LocalDate> {
        val month = YearMonth.of(1950 + Random.nextInt(70), 1 + Random.nextInt(12))
        return month.atDay(1) to month.atEndOfMonth()
    }

    // POST /api/sgspe/indicators/{id}/measurements; sin project, todo el tenant
    protected fun closePeriod(
        indicator: String,
        project: String?,
        start: LocalDate,
        end: LocalDate,
        token: String = this.token,
        key: String? = null
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/indicators/$indicator/measurements",
            mapOf("project" to project, "attributes" to mapOf("period_start_date" to start.toString(), "period_end_date" to end.toString())),
            token,
            key?.let { mapOf("Idempotency-Key" to it) }.orEmpty()
        )

    companion object {
        private val CATALOG_LOCK = Any()

        @Volatile
        private var loaded = false
    }
}
```

- [ ] **Paso 5: El modelo contra wasichai**

Crear `src/test/kotlin/sgspe/dashboard/IndicatorModelApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest
import wasichai.core.common.ConflictException

// el modelo de los indicadores contra wasichai (§4.1 de la especificación de F8): el catálogo cargado, SOCIAL_MANAGER edita la
// meta pero no el código, la API genérica no escribe mediciones (ni al admin) y una medición es inmutable, también como la
// plataforma
class IndicatorModelApiTest : F8IndicatorsApiTest() {
    @Test
    fun `el catalogo cargado trae los 53 KPI con la meta de KPI-ENG-02`() {
        val all = get("/api/objects/indicator/records?size=200")["content"].values().associateBy { it["attributes"]["code"].asString() }
        assertEquals(true, all.size >= 53, all.keys.toString())
        val meetings = all.getValue("KPI-ENG-02")["attributes"]
        assertEquals(2, meetings["target_value"].asInt())
        assertEquals("Mensual", meetings["frequency"].asString())
    }

    @Test
    fun `SOCIAL_MANAGER edita la meta pero no el codigo`() {
        val social = member("SOCIAL_MANAGER")
        val id = indicatorId("KPI-COM-02")
        val stored = attributesOf(record("indicator", id))
        val path = "/api/objects/indicator/records/$id"
        assertEquals(HttpStatus.OK, exchange("PUT", path, mapOf("attributes" to stored + ("target_value" to 5)), social.token).status)
        val renamed = exchange("PUT", path, mapOf("attributes" to stored + ("code" to "KPI-COM-99")), social.token)
        assertEquals(HttpStatus.BAD_REQUEST, renamed.status, renamed.body)
        assertEquals("FIELD_NOT_EDITABLE", code(renamed))
        assertEquals("KPI-COM-02", record("indicator", id)["attributes"]["code"].asString())
        assertEquals(HttpStatus.FORBIDDEN, exchange("PUT", path, mapOf("attributes" to stored), member("READ_ONLY").token).status)
        // vuelve a su meta (null) para no tocar las demás pruebas de la JVM
        send("PUT", path, mapOf("attributes" to stored), HttpStatus.OK)
    }

    @Test
    fun `la API generica no escribe mediciones, ni al admin`() {
        val attributes = sample("indicator_measurement")
        for (caller in listOf(token, member("SOCIAL_MANAGER").token)) {
            val created = exchange("POST", "/api/objects/indicator_measurement/records", mapOf("attributes" to attributes), caller)
            assertEquals(HttpStatus.FORBIDDEN, created.status, created.body)
        }
    }

    @Test
    fun `una medicion no cambia ni se borra, tampoco como la plataforma`() {
        val (start, end) = lastMonth()
        val measurement =
            platformCreate(
                "indicator_measurement",
                mapOf(
                    "indicator" to indicatorId("KPI-COM-01"),
                    "project" to testProject(),
                    "period_start_date" to start.toString(),
                    "period_end_date" to end.toString(),
                    "measured_value" to 4,
                    "closed_by_user_id" to "00000000-0000-0000-0000-000000000001",
                    "closed_by_user_email" to "gerente-social@sgspe.test"
                )
            )
        assertEquals(HttpStatus.FORBIDDEN, exchange("DELETE", "/api/objects/indicator_measurement/records/$measurement").status)
        assertThrows<ConflictException> { platformUpdate(measurement, mapOf("measured_value" to 5)) }
        assertEquals(4, record("indicator_measurement", measurement)["attributes"]["measured_value"].asInt())
    }

    private fun platformUpdate(
        id: String,
        attributes: Map<String, Any?>
    ) {
        kotlinx.coroutines.runBlocking {
            platformRecords.asPlatform(organizationId()) {
                platformRecords.patch("indicator_measurement", java.util.UUID.fromString(id), attributes, "cambio de prueba como la plataforma")
            }
        }
    }
}
```

(`sample("indicator_measurement")` crea la relación requerida a `indicator` con `fixture`, porque `indicator` no es
`apiOnly`; si `F2ApiTest.sample` no supiera armarla, se pasa el mapa a mano como en la última prueba.)

- [ ] **Paso 6: Build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.dashboard.IndicatorModelApiTest`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/IndicatorWriteGuard.kt src/test/kotlin/sgspe/dashboard/IndicatorWriteGuardTest.kt \
  src/test/kotlin/sgspe/F8IndicatorsApiTest.kt src/test/kotlin/sgspe/dashboard/IndicatorModelApiTest.kt
git commit -m "feat: el código de un indicador no cambia y la base F8IndicatorsApiTest" -m "IndicatorWriteGuard responde 400 FIELD_NOT_EDITABLE sobre code; la base carga el catálogo una vez
por JVM y trae un proyecto activo, el cierre y el mes pasado.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t2 -m "Merge branch 'f82a/t2' into feat/fase-8-2a-tableros"
```

---

### Tarea 3: `KpiReader`: los KPI leen como la persona o como la plataforma

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/KpiReader.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, `src/main/kotlin/sgspe/dashboard/DashboardData.kt`,
  `src/main/kotlin/sgspe/territory/SpatialGate.kt`, `src/main/kotlin/sgspe/territory/ComponentLand.kt`
- Test: `src/test/kotlin/sgspe/dashboard/KpiReadersTest.kt` (nuevo), `src/test/kotlin/sgspe/territory/SpatialGateTest.kt`

**Interfaces:**
- Consumes: `Records`, `Readability`, `ObjectDefinitions`, `Permissions.confidentialHidden`, `ProjectReach.mine`,
  `Callers` (`sgspe.common`).
- Produces: `KpiReader`, `KpiContext`, `KpiReaders` y `Dashboards.dashboard/indicator/value` como los fija «Interfaces»;
  `DashboardData(…, platform: UUID? = null)` (último parámetro, opcional: las unitarias que ya la arman no cambian);
  `SpatialGate.selectAsPlatform` y `ComponentLand.rows(projects, platform)`.

El comportamiento de los tableros de F2 a F6 no cambia: con la persona, `KpiReaders` arma exactamente lo que hoy arma
`Dashboards` (Readability, `Permissions.confidentialHidden`, «mis proyectos» de `ProjectReach` o todos los que lee). La
diferencia es que ahora el lector se nombra, y la plataforma tiene su contexto: lee todo campo que el tenant tiene, sin
`confidentialHidden`, con todos los proyectos por defecto, dentro de `Records.asPlatform`, y nunca con una persona en el
contexto (Review Focus 1).

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t3
```

- [ ] **Paso 2: Las unitarias que fallan**

Crear `src/test/kotlin/sgspe/dashboard/KpiReadersTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.NotFoundException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.CustomField
import wasichai.core.metadata.CustomObject
import wasichai.core.metadata.FieldType
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// especificación de F8 §2.2.16: los KPI reciben su lector. la persona lee con sus permisos, su confidentialHidden y «mis
// proyectos»; la plataforma lee todo campo que el tenant tiene, sin ocultar nada, con todos los proyectos, dentro de
// asPlatform y nunca con una persona en el contexto (Review Focus 1)
class KpiReadersTest {
    private val organization = UUID.randomUUID()
    private val person = AuthenticatedUser(UUID.randomUUID(), organization, "analista@sgspe.test", listOf("ANALYST"))
    private val mine = listOf(UUID.randomUUID())
    private val all = listOf(UUID.randomUUID(), UUID.randomUUID())
    private val platformRuns = mutableListOf<UUID>()

    private fun definition(
        name: String,
        vararg fields: String
    ): ObjectDefinition {
        val objectId = UUID.randomUUID()
        return ObjectDefinition(
            CustomObject(objectId, organization, name, name, name, null, true, "${name}_tbl", null, null),
            fields.mapIndexed { i, field ->
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

    private fun readers(user: AuthenticatedUser?) =
        KpiReaders(
            { user },
            { user ?: error("sin usuario") },
            { org, block ->
                platformRuns += org
                block()
            },
            { { path -> path == "stakeholder" } },
            { _, name -> if (name == "stakeholder") definition("stakeholder", "stance", "influence_group") else throw NotFoundException("no está") },
            { true },
            { mine },
            { all }
        )

    @Test
    fun `la persona lee con sus permisos, su marca y sus proyectos, sin asPlatform`() {
        val readers = readers(person)
        runBlocking {
            readers.reading(readers.person()) { ctx ->
                assertTrue(ctx.readable("stakeholder"))
                assertFalse(ctx.readable("stakeholder.stance"))
                assertTrue(ctx.confidentialHidden)
                assertFalse(ctx.tenantWide)
                assertNull(ctx.platform)
                assertEquals(mine, ctx.defaultProjects())
            }
        }
        assertEquals(emptyList<UUID>(), platformRuns)
    }

    @Test
    fun `la plataforma lee todo campo que el tenant tiene, sin marca y con todos los proyectos`() {
        val readers = readers(null)
        runBlocking {
            readers.reading(readers.platform(organization)) { ctx ->
                assertTrue(ctx.readable("stakeholder.stance"))
                assertFalse(ctx.readable("stakeholder.religion"))
                assertFalse(ctx.readable("deliverable.code"))
                assertFalse(ctx.confidentialHidden)
                assertTrue(ctx.tenantWide)
                assertEquals(organization, ctx.platform)
                assertEquals(all, ctx.defaultProjects())
            }
        }
        assertEquals(listOf(organization), platformRuns)
    }

    @Test
    fun `la plataforma no se usa con una persona en el contexto`() {
        val readers = readers(person)
        assertThrows<IllegalStateException> { runBlocking { readers.platform(organization) } }
        var ran = false
        assertThrows<IllegalStateException> { runBlocking { readers.reading(KpiReader.Platform(organization)) { ran = true } } }
        assertFalse(ran)
        assertEquals(emptyList<UUID>(), platformRuns)
    }
}
```

En `src/test/kotlin/sgspe/territory/SpatialGateTest.kt`, al final de la clase:

```kotlin
    // especificación de F8 §2.2.16: con el lector de la plataforma, la compuerta no pide permisos de persona ni alcances ni
    // dueño; un objeto o campo que el tenant no tiene es no disponible, sin correr nada
    @Test
    fun `la plataforma no pide alcances y lo que el tenant no tiene es no disponible`() {
        val captured = Captured()
        val scope = RecordCriterion { _, _ -> "c_code = 'x'" }
        val gate = gate(emptySet(), captured, scope = scope, owner = UUID.randomUUID())
        val outcome =
            runBlocking {
                gate.selectAsPlatform(organization, listOf(GateRead("parcel", setOf("code"), principal = true)), { "SELECT ${table("parcel")}.x" }) { it }
            }
        assertTrue(outcome is GateOutcome.Rows)
        assertTrue(captured.scoped.isEmpty())
        assertFalse(captured.sql!!.contains("c_code = 'x'"))
        val missing =
            runBlocking { gate(emptySet()).selectAsPlatform(organization, listOf(GateRead("parcel", setOf("religion"))), { "SELECT 1" }) { it } }
        assertEquals(GateOutcome.Unavailable(listOf("parcel.religion")), missing)
    }
```

(`assertFalse`, `assertTrue` y `RecordCriterion` se importan si faltan; `GateOutcome.Unavailable` es una `data class`
sin parámetro de tipo, así que se compara por igualdad.)

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.KpiReadersTest' --tests 'sgspe.territory.SpatialGateTest'
```

Esperado: no compila (`Unresolved reference: KpiReaders`, `selectAsPlatform`).

- [ ] **Paso 3: El lector**

Crear `src/main/kotlin/sgspe/dashboard/KpiReader.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import sgspe.common.Callers
import sgspe.common.ObjectDefinitions
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Readability
import sgspe.common.Records
import wasichai.core.common.ForbiddenException
import wasichai.core.common.NotFoundException
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import java.util.UUID

// quién lee los KPI (especificación de F8 §2.2.16, RQ-DSH-034): la persona que pide (tableros, cierre de periodo, AI-04) o la
// plataforma de una organización (JOB-05, que ensambla los entregables). las dos calculan con las mismas funciones de Kpis
sealed interface KpiReader {
    val organizationId: UUID

    data class Person(
        val user: AuthenticatedUser
    ) : KpiReader {
        override val organizationId: UUID get() = user.organizationId
    }

    data class Platform(
        override val organizationId: UUID
    ) : KpiReader
}

// lo que un lector deja ver al calcular: qué objeto.campo lee, si se le ocultan QCS confidenciales, si lee todo el tenant
// (para la cifra oficial del tenant) y los proyectos por defecto. `platform` es la organización si lee la plataforma
class KpiContext(
    val reader: KpiReader,
    val readable: suspend (String) -> Boolean,
    val confidentialHidden: Boolean,
    val tenantWide: Boolean,
    private val defaults: suspend () -> List<UUID>
) {
    val platform: UUID? get() = (reader as? KpiReader.Platform)?.organizationId

    suspend fun defaultProjects(): List<UUID> = defaults()
}

// arma el contexto de cada lector. la persona: Readability (la definición filtrada por sus permisos, dashboards.md §8),
// Permissions.confidentialHidden y «mis proyectos» de ProjectReach o, si lee todos, todos los que lee. la plataforma: todo
// campo que el tenant tiene (ObjectDefinitions, sin permisos), sin confidentialHidden, todos los proyectos, y el cálculo
// entero dentro de Records.asPlatform (WAS-ADR-0039). la plataforma nunca se usa con una persona en el contexto: es la única
// pieza de sgspe.dashboard que llama a asPlatform (ArchitectureTest), y lo comprueba antes de leer nada (Review Focus 1)
@Component
class KpiReaders internal constructor(
    private val userOrNull: suspend () -> AuthenticatedUser?,
    private val requireUser: suspend () -> AuthenticatedUser,
    private val asPlatform: suspend (UUID, suspend () -> Any?) -> Any?,
    private val readability: () -> suspend (String) -> Boolean,
    private val definition: suspend (UUID, String) -> ObjectDefinition,
    private val hidden: suspend (AuthenticatedUser) -> Boolean,
    private val mine: suspend (AuthenticatedUser) -> List<UUID>?,
    private val allProjects: suspend () -> List<UUID>
) {
    @Autowired
    constructor(
        callers: Callers,
        records: Records,
        metadata: MetadataService,
        definitions: ObjectDefinitions,
        permissions: Permissions,
        reach: ProjectReach
    ) : this(
        { callers.userOrNull() },
        { callers.require() },
        { organization, block -> records.asPlatform(organization) { block() } },
        { Readability(metadata)::canRead },
        { organization, name -> definitions.current(organization, name) },
        { permissions.confidentialHidden(it) },
        { reach.mine(it) },
        {
            try {
                records.all("project", max = DashboardData.MAX_ROWS).map { UUID.fromString(it.id) }
            } catch (_: ForbiddenException) {
                emptyList()
            }
        }
    )

    suspend fun person(): KpiReader.Person = KpiReader.Person(requireUser())

    // el lector de la plataforma, solo fuera de una petición (JOB-05, las pruebas de paridad)
    suspend fun platform(organizationId: UUID): KpiReader.Platform {
        outsideRequest()
        return KpiReader.Platform(organizationId)
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> reading(
        reader: KpiReader,
        block: suspend (KpiContext) -> T
    ): T =
        when (reader) {
            is KpiReader.Person -> {
                val readsAll = mine(reader.user) == null
                block(KpiContext(reader, readability(), hidden(reader.user), readsAll) { mine(reader.user) ?: allProjects() })
            }
            is KpiReader.Platform -> {
                outsideRequest()
                asPlatform(reader.organizationId) {
                    block(KpiContext(reader, existing(reader.organizationId), confidentialHidden = false, tenantWide = true) { allProjects() })
                } as T
            }
        }

    private suspend fun outsideRequest() {
        check(userOrNull() == null) { "El lector de la plataforma no se usa dentro de una petición: solo JOB-05 lee como la plataforma" }
    }

    // objeto.campo existe en el tenant (sin permisos: la plataforma no los tiene); una vez por objeto en cada cálculo
    private fun existing(organizationId: UUID): suspend (String) -> Boolean {
        val fields = mutableMapOf<String, Set<String>?>()
        return { path ->
            val objectName = path.substringBefore('.')
            val field = path.substringAfter('.', "")
            val known =
                if (objectName in fields) {
                    fields[objectName]
                } else {
                    val loaded =
                        try {
                            definition(organizationId, objectName).fields.map { it.name }.toSet()
                        } catch (_: NotFoundException) {
                            null
                        }
                    loaded.also { fields[objectName] = it }
                }
            known != null && (field.isEmpty() || field in known)
        }
    }
}
```

- [ ] **Paso 4: La compuerta y la consulta de KPI-TER-04 con la plataforma**

En `src/main/kotlin/sgspe/territory/SpatialGate.kt`, el comentario de la clase suma al final «Con el lector de la
plataforma de los KPI (especificación de F8 §2.2.16), selectAsPlatform: sin permisos de persona, sin alcances ni dueño, con
la organización enlazada; solo fuera de una petición (KpiReaders lo comprueba).» Dentro de la clase, después de
`select(...)`:

```kotlin
    // la misma consulta como la plataforma de `organizationId`: lo que el tenant no tiene (objeto o campo) es no disponible
    suspend fun <T : Any> selectAsPlatform(
        organizationId: UUID,
        reads: List<GateRead>,
        sql: GateTables.(bind: (Any) -> String) -> String,
        map: (Readable) -> T
    ): GateOutcome<T> {
        val declared = reads.groupBy { it.objectName }.mapValues { (_, list) -> list.flatMap { it.fields }.toSet() }
        val definitions = mutableMapOf<String, ObjectDefinition>()
        val missing = mutableListOf<String>()
        for ((name, fields) in declared) {
            val loaded =
                try {
                    definition(organizationId, name)
                } catch (_: NotFoundException) {
                    missing += fields.sorted().map { "$name.$it" }.ifEmpty { listOf(name) }
                    continue
                }
            definitions[name] = loaded
            missing += fields.filter { field -> loaded.fields.none { it.name == field } }.sorted().map { "$name.$it" }
        }
        if (missing.isNotEmpty()) return GateOutcome.Unavailable(missing.distinct().sorted())
        val tables = GateSql(declared, definitions, definitions.mapValues { emptyList() }, organizationId, null, physical)
        val text = tables.sql(tables::bind)
        requireReadOnly(text)
        @Suppress("UNCHECKED_CAST")
        return GateOutcome.Rows(runner.run(text, tables.bindings, map) as List<T>)
    }
```

(`NotFoundException` se importa de `wasichai.core.common` si falta.)

En `src/main/kotlin/sgspe/territory/ComponentLand.kt`, `rows` gana el parámetro y elige la vía:

```kotlin
    // `platform`: la organización cuando lee el lector de la plataforma de los KPI (DashboardData); null, la persona
    suspend fun rows(
        projects: Collection<UUID>,
        platform: UUID? = null
    ): GateOutcome<ComponentLandRow> {
        if (projects.isEmpty()) return GateOutcome.Rows(emptyList())
        val sql: GateTables.((Any) -> String) -> String = { bind -> sql(this, bind, projects, limits.maxRows) }
        val map: (Readable) -> ComponentLandRow = { row ->
            ComponentLandRow(
                row.get("component", UUID::class.java)!!,
                row.get("project", UUID::class.java)!!,
                row.get("component_type", String::class.java),
                tenureOf(row.get("land", String::class.java)),
                row.get("land_id", UUID::class.java)!!
            )
        }
        val outcome = if (platform == null) gate.select(READS, sql, map) else gate.selectAsPlatform(platform, READS, sql, map)
        if (outcome is GateOutcome.Rows && outcome.rows.size > limits.maxRows) throw Records.tooMany(TerritoryNames.COMPONENT, limits.maxRows)
        return outcome
    }
```

(`io.r2dbc.spi.Readable` es el tipo que ya usa `GateRunner`; se importa si falta.)

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`: el constructor gana, después de `componentLand`, el parámetro

```kotlin
    private val componentLand: ComponentLand? = null,
    // F8: la organización si lee el lector de la plataforma (KpiContext.platform): KPI-TER-04 va por la vía de la plataforma
    // de la compuerta. null, la persona
    private val platform: UUID? = null
```

`with(filters)` pasa a `DashboardData(records, filters, readable, today, maxRows, maxRelated, componentLand, platform)` y,
en `landCrossings()`, `source.rows(filters.projects)` pasa a `source.rows(filters.projects, platform)`.

- [ ] **Paso 5: `Dashboards` calcula con el contexto del lector**

Reemplazar `src/main/kotlin/sgspe/dashboard/Dashboards.kt` hasta el final de la clase `Dashboards` (el controlador no
cambia) por:

```kotlin
package sgspe.dashboard

import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.LIMA
import sgspe.common.ModelCatalog
import sgspe.common.Readability
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.toCodedException
import sgspe.territory.ComponentLand
import wasichai.core.metadata.MetadataService
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// los indicadores de módulo y sus tableros (api.md §4.11, dashboards.md §6 a §8). cada cálculo lleva su lector (especificación
// de F8 §2.2.16): sin lector, la persona que llama, con sus permisos, su confidentialHidden y «mis proyectos»; con
// KpiReader.Platform, la plataforma de JOB-05, fuera de una petición (KpiReaders). antes de calcular, cada KPI comprueba que
// el lector puede leer los objetos y campos que usa; si no, sale no disponible con la razón y los campos que faltan, nunca
// cero (RQ-DSH-032). nada se guarda en el servidor (§6.8). los errores llevan su código en errors[0].message (P15): 404
// DASHBOARD_NOT_FOUND e INDICATOR_NOT_FOUND, 400 INVALID_BREAKDOWN, PROJECT_REQUIRED y TOO_MANY_RECORDS
@Service
class Dashboards(
    private val records: Records,
    private val metadata: MetadataService,
    private val callers: Callers,
    private val catalog: ModelCatalog,
    private val clock: Clock,
    private val componentLand: ComponentLand,
    private val readers: KpiReaders
) {
    suspend fun catalog(): List<CatalogEntry> {
        callers.require()
        val readable = Readability(metadata)
        return Kpis.ALL.map { CatalogEntry(it.code, it.label, it.dashboard, readable.missing(it.requires).isEmpty()) }
    }

    suspend fun dashboard(
        name: String,
        params: MultiValueMap<String, String>,
        reader: KpiReader? = null
    ): DashboardResponse {
        val codes =
            Kpis.DASHBOARDS[name]
                ?: throw notFound("DASHBOARD_NOT_FOUND", "El tablero $name no existe o no está disponible en esta fase", "dashboard")
        // el tablero territorial exige el proyecto (especificación de F6 §3.2): sus cifras son de un proyecto, no del tenant
        if (name == Kpis.TERRITORY && params["project"].isNullOrEmpty()) {
            throw Finding("PROJECT_REQUIRED", "El tablero territorial exige al menos un project", "project", status = HttpStatus.BAD_REQUEST).toCodedException()
        }
        return readers.reading(reader ?: readers.person()) { ctx ->
            val today = today()
            val filters = filters(params, DashboardFilters.COMMON + Kpis.extraFilters(codes), today, ctx)
            val data = data(ctx, filters, today)
            // con locality o community, la ficha (KPI-TER-01) va primero
            val shown = if (name == Kpis.TERRITORY && (filters.locality != null || filters.community != null)) listOf(TerritorySummary.CODE) + codes else codes
            val indicators = shown.map { mark(KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(it), data), ctx.confidentialHidden) }
            DashboardResponse(name, filters.echo(), clock.instant().toString(), indicators, if (name == Kpis.ATTENTION) ctx.confidentialHidden else null)
        }
    }

    suspend fun indicator(
        code: String,
        params: MultiValueMap<String, String>,
        reader: KpiReader? = null
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw notFound("INDICATOR_NOT_FOUND", "El indicador $code no existe o no está disponible en esta fase", "code")
        return readers.reading(reader ?: readers.person()) { ctx ->
            val today = today()
            val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN + Kpis.extraFilters(listOf(code)), today, ctx)
            val value = mark(KpiEvaluation.evaluate(kpi, data(ctx, filters, today)), ctx.confidentialHidden)
            val by = filters.breakdown
            when {
                by == null || !value.available -> value
                by !in value.breakdowns.orEmpty().map { it.by } -> {
                    val offered = value.breakdowns.orEmpty().map { it.by }
                    val hint = if (offered.isEmpty()) "$code no tiene desgloses" else "$code se desglosa por ${offered.joinToString(", ")}"
                    throw Finding("INVALID_BREAKDOWN", "Desglose inválido: $hint", BREAKDOWN, status = HttpStatus.BAD_REQUEST).toCodedException()
                }
                else -> value.copy(breakdowns = value.breakdowns.orEmpty().filter { it.by == by })
            }
        }
    }

    // un KPI sobre proyectos y periodo dados, sin otros filtros (el cierre de periodo, el ensamblado de 8.2b): sin `projects`,
    // los proyectos por defecto del lector (todos, para la plataforma y para quien lee todos los proyectos)
    suspend fun value(
        code: String,
        projects: List<UUID>?,
        start: LocalDate,
        end: LocalDate,
        reader: KpiReader
    ): IndicatorValue {
        val kpi = Kpis.BY_CODE[code] ?: throw notFound("INDICATOR_NOT_FOUND", "El indicador $code no existe o no está disponible en esta fase", "code")
        return readers.reading(reader) { ctx ->
            val filters = DashboardFilters.of(projects ?: ctx.defaultProjects(), start, end)
            mark(KpiEvaluation.evaluate(kpi, data(ctx, filters, today())), ctx.confidentialHidden)
        }
    }

    private fun data(
        ctx: KpiContext,
        filters: DashboardFilters,
        today: LocalDate
    ): DashboardData = DashboardData(records, filters, ctx.readable, today, componentLand = componentLand, platform = ctx.platform)

    // la marca en un indicador que lee QCS, solo si es cierta (los demás no la llevan)
    private fun mark(
        value: IndicatorValue,
        hidden: Boolean
    ): IndicatorValue = if (hidden && Kpis.confidentialSensitive(value.code)) value.copy(confidentialHidden = true) else value

    // hoy en Lima, del reloj de la aplicación: una sola vez por petición
    private fun today(): LocalDate = LocalDate.now(clock.withZone(LIMA))

    private suspend fun filters(
        params: MultiValueMap<String, String>,
        allowed: Set<String>,
        today: LocalDate,
        ctx: KpiContext
    ): DashboardFilters = DashboardFilters.parse(params, allowed, catalog, today) { ctx.defaultProjects() }.resolve()

    private fun notFound(
        code: String,
        message: String,
        field: String
    ) = Finding(code, message, field, status = HttpStatus.NOT_FOUND).toCodedException()

    companion object {
        const val BREAKDOWN = "breakdown"
    }
}
```

En `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`, en el `companion object`, después de `MAX_YEAR`:

```kotlin
        // los filtros de un cálculo sin pedido HTTP (el cierre de periodo, el ensamblado de entregables): proyectos y periodo
        fun of(
            projects: List<UUID>,
            start: LocalDate,
            end: LocalDate
        ): DashboardFilters = DashboardFilters(projects.distinct(), start, end, null, null, null, null, null, null, null, periodGiven = true)
```

`ProjectReach`, `Permissions` y `ForbiddenException` ya no se importan en `Dashboards.kt` (ktlint no quita imports sin
uso: se borran a mano).

- [ ] **Paso 6: Verlas pasar, el build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.*' --tests 'sgspe.territory.SpatialGateTest' --tests 'sgspe.territory.*Test'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; las unitarias de los tableros de F2 a F6 siguen en verde sin tocarlas. Integración
pendiente (regresión): `sgspe.dashboard.DashboardsApiTest`, `sgspe.dashboard.TerritoryDashboardApiTest`,
`sgspe.dashboard.AttentionDashboardApiTest` y `sgspe.ai.AiRunsApiTest` (AI-04 lee los tableros por
`RunSources.projectSummary`).

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/KpiReader.kt src/main/kotlin/sgspe/dashboard/Dashboards.kt \
  src/main/kotlin/sgspe/dashboard/DashboardData.kt src/main/kotlin/sgspe/dashboard/DashboardFilters.kt \
  src/main/kotlin/sgspe/territory/SpatialGate.kt src/main/kotlin/sgspe/territory/ComponentLand.kt \
  src/test/kotlin/sgspe/dashboard/KpiReadersTest.kt src/test/kotlin/sgspe/territory/SpatialGateTest.kt
git commit -m "feat: KpiReader, los KPI leen como la persona o como la plataforma" -m "KpiReaders arma el contexto de cada lector; la plataforma solo fuera de una petición y dentro de
asPlatform. SpatialGate gana selectAsPlatform para KPI-TER-04 (especificación de F8 §2.2.16).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t3 -m "Merge branch 'f82a/t3' into feat/fase-8-2a-tableros"
```

---

### Tarea 4: KPI-EXE-01 a KPI-EXE-03 (`ExecutiveKpis`)

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/ExecutiveKpis.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `src/main/kotlin/sgspe/dashboard/DashboardData.kt`
- Test: `src/test/kotlin/sgspe/dashboard/ExecutiveKpisTest.kt` (nuevo), `src/test/kotlin/sgspe/dashboard/TerritoryDashboardTest.kt`

**Interfaces:**
- Consumes: `AttentionKpis.counted`, `EngagementKpis.held` y `months`, `CommitmentKpis.overdueSeries`, `RescheduleRead.warn`,
  `Numbers.counts` (sgspe.dashboard, F2 a F4); `DashboardData` de la Tarea 3.
- Produces: `ProjectRow`, `WarningRow`, `ExecutiveKpis` (`ACTIVE_PROJECTS`, `ACTIVE_WARNINGS`, `TREND`, `TILES`,
  `BY_PROJECT`, `activeProjects`, `activeWarnings`, `trend`); `Kpis.EXECUTIVE`, `Kpis.PROJECT`, `Kpis.MONITORING`;
  `DashboardData.projectRows()` y `DashboardData.earlyWarnings()`.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t4
```

- [ ] **Paso 2: Las unitarias que fallan**

Crear `src/test/kotlin/sgspe/dashboard/ExecutiveKpisTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

// KPI-EXE-01 a 03 (dashboards.md §4.1) sin base: proyectos en active por etapa y tipo, alertas activas por severidad y estado,
// y la tendencia de 12 meses sin valor escalar, con las quejas contadas una vez (un original reclasificado no cuenta)
class ExecutiveKpisTest {
    private val end = LocalDate.parse("2026-09-30")

    private fun qcs(
        type: String,
        id: String,
        state: String,
        received: String?,
        closed: String? = null,
        reclassified: Map<String, String> = emptyMap()
    ) = AttentionRow(
        type,
        id,
        state,
        received?.let(LocalDate::parse),
        null,
        null,
        closed?.let(LocalDate::parse),
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

    @Test
    fun `KPI-EXE-01 cuenta los proyectos en active con su etapa y su tipo`() {
        val rows =
            listOf(
                ProjectRow("a", "active", "EXPLORACION", "MINERO"),
                ProjectRow("b", "active", null, "MINERO"),
                ProjectRow("c", "draft", "EXPLORACION", "MINERO"),
                ProjectRow("d", "suspended", "EXPLORACION", "MINERO")
            )
        val result = ExecutiveKpis.activeProjects(rows)
        assertEquals(2, result.value)
        assertEquals(listOf("stage", "project_type"), result.breakdowns.map { it.by })
        assertEquals(setOf("EXPLORACION", "SIN DATO"), result.breakdowns[0].rows.map { it["key"] }.toSet())
        assertEquals(listOf(mapOf("key" to "MINERO", "value" to 2)), result.breakdowns[1].rows)
    }

    @Test
    fun `KPI-EXE-02 cuenta las alertas activas por severidad y estado`() {
        val rows =
            listOf(
                WarningRow("1", "detected", "ALTO"),
                WarningRow("2", "responding", "ALTO"),
                WarningRow("3", "confirmed", null),
                WarningRow("4", "dismissed", "ALTO"),
                WarningRow("5", "closed", "MEDIO")
            )
        val result = ExecutiveKpis.activeWarnings(rows)
        assertEquals(3, result.value)
        assertEquals(listOf(mapOf("key" to "ALTO", "value" to 2), mapOf("key" to "SIN DATO", "value" to 1)), result.breakdowns[0].rows)
        assertEquals(3, result.breakdowns[1].rows.size)
    }

    @Test
    fun `KPI-EXE-03 son tres medidas por mes sin valor escalar y una queja reclasificada no cuenta dos veces`() {
        val held = EngagementKpis.held(emptyList(), emptyList(), end).copy(
            series = EngagementKpis.months(end).map { mapOf("period" to it.toString(), "value" to if (it.toString() == "2026-09") 4 else 0) }
        )
        val overdue = IndicatorResult(2, series = EngagementKpis.months(end).map { mapOf("period" to it.toString(), "value" to 2) })
        val attention =
            listOf(
                qcs("grievance", "q1", "closed", "2026-09-02", "2026-09-20"),
                qcs("grievance", "q2", "received", "2026-09-05"),
                // el original reclasificado a consulta: no cuenta como queja recibida
                qcs("grievance", "q3", "closed", "2026-09-06", "2026-09-07"),
                qcs("inquiry", "c1", "received", "2026-09-06", reclassified = mapOf("reclassified_from_grievance" to "q3")),
                qcs("inquiry", "c2", "received", "2026-09-08")
            )
        val result = ExecutiveKpis.trend(held, attention, overdue, end)
        assertNull(result.value)
        val series = result.series!!
        assertEquals(12, series.size)
        assertEquals(
            mapOf("period" to "2026-09", "engagements" to 4, "grievancesReceived" to 2, "grievancesClosed" to 1, "overdueCommitments" to 2),
            series.last()
        )
        assertEquals("2025-10", series.first()["period"])
    }

    @Test
    fun `los KPI-EXE estan en el catalogo, en el tablero ejecutivo, y KPI-EXE-03 lleva la marca de las QCS`() {
        for (code in listOf("KPI-EXE-01", "KPI-EXE-02", "KPI-EXE-03")) assertEquals(Kpis.EXECUTIVE, Kpis.BY_CODE.getValue(code).dashboard, code)
        assertTrue(Kpis.confidentialSensitive("KPI-EXE-03"))
        assertEquals(ExecutiveKpis.TILES - ExecutiveKpis.TREND, ExecutiveKpis.BY_PROJECT)
        assertTrue("periodStart" in Kpis.BY_CODE.getValue("KPI-EXE-01").ignores)
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.ExecutiveKpisTest'
```

Esperado: no compila (`Unresolved reference: ExecutiveKpis`).

- [ ] **Paso 3: Las funciones puras**

Crear `src/main/kotlin/sgspe/dashboard/ExecutiveKpis.kt`:

```kotlin
package sgspe.dashboard

import sgspe.common.Attention
import java.time.LocalDate
import java.time.YearMonth

// un proyecto como lo lee KPI-EXE-01: su estado de WF-A, su etapa y su tipo
data class ProjectRow(
    val id: String,
    val state: String?,
    val stage: String?,
    val type: String?
)

// una alerta temprana como la lee KPI-EXE-02: su estado de WF-J y su severidad
data class WarningRow(
    val id: String,
    val state: String?,
    val severity: String?
)

// los KPI ejecutivos (dashboards.md §4.1) como funciones puras, y las zonas del tablero executive (§5.1)
object ExecutiveKpis {
    const val ACTIVE_PROJECTS = "KPI-EXE-01"
    const val ACTIVE_WARNINGS = "KPI-EXE-02"
    const val TREND = "KPI-EXE-03"
    const val ACTIVE = "active"
    private const val NO_DATA = "SIN DATO"

    // dashboards.md §3.2: alerta activa
    val ACTIVE_WARNING_STATES: Set<String> = setOf("detected", "in_triage", "confirmed", "responding")

    // las zonas de §5.1 en su orden: las tiles, la inversión y la tendencia
    val TILES: List<String> =
        listOf(ACTIVE_PROJECTS, "KPI-COM-02", "KPI-COM-03", "KPI-GRV-02", "KPI-RSK-01", "KPI-ENG-01", ACTIVE_WARNINGS, "KPI-INV-03", TREND)

    // la tabla por proyecto: las mismas cifras con valor (la tendencia no tiene valor escalar)
    val BY_PROJECT: List<String> = TILES - TREND

    // KPI-EXE-01: los proyectos en active, por etapa y por tipo
    fun activeProjects(rows: List<ProjectRow>): IndicatorResult {
        val active = rows.filter { it.state == ACTIVE }
        return IndicatorResult(
            active.size,
            breakdowns =
                listOf(
                    Breakdown("stage", Numbers.counts(active.map { it.stage ?: NO_DATA })),
                    Breakdown("project_type", Numbers.counts(active.map { it.type ?: NO_DATA }))
                )
        )
    }

    // KPI-EXE-02: las alertas activas, por severidad y por estado
    fun activeWarnings(rows: List<WarningRow>): IndicatorResult {
        val active = rows.filter { it.state in ACTIVE_WARNING_STATES }
        return IndicatorResult(
            active.size,
            breakdowns =
                listOf(
                    Breakdown("severity", Numbers.counts(active.map { it.severity ?: NO_DATA })),
                    Breakdown("workflow_state", Numbers.counts(active.map { it.state ?: NO_DATA }))
                )
        )
    }

    // KPI-EXE-03: una fila por mes de los 12 que terminan en `end`, con las interacciones realizadas (la serie de KPI-ENG-01),
    // las quejas recibidas y cerradas (como KPI-GRV-09 pero solo grievance, con la regla de reclasificación de las tres QCS) y
    // los compromisos vencidos al cierre (la serie de KPI-COM-11). sin valor escalar: no se cierra (NO_SCALAR_VALUE)
    fun trend(
        engagements: IndicatorResult,
        attention: List<AttentionRow>,
        overdue: IndicatorResult,
        end: LocalDate
    ): IndicatorResult {
        val grievances = AttentionKpis.counted(attention).filter { it.type == Attention.GRIEVANCE }
        val held = engagements.series.orEmpty().associate { it["period"] to it["value"] }
        val late = overdue.series.orEmpty().associate { it["period"] to it["value"] }
        val series =
            EngagementKpis.months(end).map { month ->
                val period = month.toString()
                mapOf(
                    "period" to period,
                    "engagements" to (held[period] ?: 0),
                    "grievancesReceived" to grievances.count { it.received != null && YearMonth.from(it.received) == month },
                    "grievancesClosed" to grievances.count { it.state == CLOSED && it.closed != null && YearMonth.from(it.closed) == month },
                    "overdueCommitments" to (late[period] ?: 0)
                )
            }
        return IndicatorResult(null, series = series)
    }

    private const val CLOSED = "closed"
}
```

- [ ] **Paso 4: Las lecturas y los tres KPI en `Kpis`**

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, en la sección «territorio (F6: KPI-TER)», antes de
`companion object`:

```kotlin
    // ---- ejecutivos (F8: KPI-EXE, dashboards.md §4.1) ----

    // los proyectos del filtro con su estado, su etapa y su tipo (KPI-EXE-01)
    suspend fun projectRows(): List<ProjectRow> =
        projectRecords().map { ProjectRow(it.id, it.state, Values.text(it.attributes["stage"]), Values.text(it.attributes["project_type"])) }

    // las alertas activas de los proyectos en el territorio pedido (KPI-EXE-02): por su localidad o su comunidad, sin el ámbito
    suspend fun earlyWarnings(): List<WarningRow> =
        cached("earlyWarnings") {
            if (filters.projects.isEmpty()) return@cached emptyList<WarningRow>()
            read(
                "early_warning",
                criteria = listOf(Criteria.anyOf("project", filters.projects), Criteria.inState(ExecutiveKpis.ACTIVE_WARNING_STATES))
            ).filter { inTerritory(Values.text(it.attributes["locality"]), Values.text(it.attributes["community"])) }
                .map { WarningRow(it.id, it.state, Values.text(it.attributes["severity"])) }
        }
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`:

1. Después de `const val TERRITORY = "territory"`:

```kotlin
    // F8: los tableros consolidados (dashboards.md §5.1, §5.2 y §5.5)
    const val EXECUTIVE = "executive"
    const val PROJECT = "project"
    const val MONITORING = "monitoring"
```

2. `confidentialSensitive` suma KPI-EXE-03, que lee las quejas:

```kotlin
    fun confidentialSensitive(code: String): Boolean =
        code.startsWith("KPI-GRV-") || code == "KPI-ENG-05" || code == TerritorySummary.CODE || code == ExecutiveKpis.TREND
```

3. Antes de `val ALL: List<Indicator> =`:

```kotlin
    // F8: KPI-EXE-01 se filtra solo por proyecto; KPI-EXE-02, por proyecto y territorio (sin ámbito); los dos son conteos de
    // hoy, así que un periodo pedido se declara ignorado. KPI-EXE-03 aplica proyecto, territorio y ámbito (dashboards.md §4.1)
    private val EXECUTIVE_WARNING_IGNORES: Set<String> = setOf("periodStart", "periodEnd", "explorationCampaign", "scope", "owner")
```

4. Al final de la lista `ALL`, después del `Kpi("KPI-TER-04", …)` (que gana su coma):

```kotlin
            // ---- F8: ejecutivos (dashboards.md §4.1) ----
            Kpi(ExecutiveKpis.ACTIVE_PROJECTS, "Proyectos activos", EXECUTIVE, "proyectos", listOf("project.stage", "project.project_type"), ignores = TERRITORY_IGNORES) {
                ExecutiveKpis.activeProjects(it.projectRows())
            },
            Kpi(
                ExecutiveKpis.ACTIVE_WARNINGS,
                "Alertas activas",
                EXECUTIVE,
                "alertas",
                listOf("early_warning.project", "early_warning.severity", "early_warning.locality", "early_warning.community"),
                ignores = EXECUTIVE_WARNING_IGNORES
            ) { ExecutiveKpis.activeWarnings(it.earlyWarnings()) },
            Kpi(
                ExecutiveKpis.TREND,
                "Tendencia mensual",
                EXECUTIVE,
                "cifras",
                (
                    HELD + QCS + qcs("closed_date") + COMMITMENT +
                        listOf(
                            "commitment.agreed_date",
                            "commitment.due_date",
                            "commitment.fulfilled_date",
                            "commitment.cancelled_date",
                            "commitment_reschedule.previous_due_date",
                            "commitment_reschedule.new_due_date",
                            "commitment_reschedule.commitment"
                        )
                ).distinct(),
                ignores = setOf("explorationCampaign", "owner")
            ) { data ->
                val commitments = data.commitments(campaign = false, owner = false)
                val reschedules = data.reschedules(commitments.map { it.id })
                reschedules.warn(
                    ExecutiveKpis.trend(
                        EngagementKpis.held(data.inPeriod(), data.window(), data.filters.periodEnd),
                        data.attention(),
                        CommitmentKpis.overdueSeries(commitments, reschedules.rows, data.filters.periodEnd, data.today),
                        data.filters.periodEnd
                    )
                )
            }
```

(`TERRITORY_IGNORES`, `HELD`, `QCS`, `qcs` y `COMMITMENT` ya están en `Kpis`; `TERRITORY_IGNORES` incluye el periodo,
como pide KPI-EXE-01: «Proyecto».)

- [ ] **Paso 5: Verlas pasar, el build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.*'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: falla `TerritoryDashboardTest` en la prueba que exige que solo KPI-TER-02 a 04 declaren el periodo como
ignorado: KPI-EXE-01 y KPI-EXE-02 también lo declaran (son conteos de hoy). En esa línea (~70), el conjunto pasa a
`setOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04", "KPI-EXE-01", "KPI-EXE-02")`, con el comentario «desde F8, también
KPI-EXE-01 y 02», y se vuelve a correr: `BUILD SUCCESSFUL`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/ExecutiveKpis.kt src/main/kotlin/sgspe/dashboard/Kpis.kt \
  src/main/kotlin/sgspe/dashboard/DashboardData.kt src/test/kotlin/sgspe/dashboard/ExecutiveKpisTest.kt \
  src/test/kotlin/sgspe/dashboard/TerritoryDashboardTest.kt
git commit -m "feat: KPI-EXE-01 a 03, proyectos, alertas y tendencia mensual" -m "ExecutiveKpis como funciones puras sobre las lecturas de DashboardData; la tendencia son tres
medidas por mes sin valor escalar y con la regla de reclasificación de las quejas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t4 -m "Merge branch 'f82a/t4' into feat/fase-8-2a-tableros"
```

---

### Tarea 5: El tablero `executive` con `byProject[]` y su tope

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, `src/main/kotlin/sgspe/dashboard/Indicators.kt`,
  `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `src/main/resources/application.yml`
- Create: `src/main/kotlin/sgspe/dashboard/DashboardsProperties.kt`
- Test: `src/test/kotlin/sgspe/dashboard/DashboardsPropertiesTest.kt`, `src/test/kotlin/sgspe/dashboard/ExecutiveDashboardApiTest.kt` (nuevos)

**Interfaces:**
- Consumes: `ExecutiveKpis.TILES` y `BY_PROJECT`, `Kpis.EXECUTIVE` (Tarea 4); `KpiReaders`, `Dashboards` (Tarea 3);
  `Records.tooMany`; `F8IndicatorsApiTest` (Tarea 2).
- Produces: `GET /api/sgspe/dashboards/executive`; `ProjectFigures(project: String, code: String?, name: String?,
  indicators: List<IndicatorValue>)`; `DashboardResponse.byProject`; `DashboardsProperties`
  (`sgspe.dashboards.executive.max-projects`, 25).

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t5
```

- [ ] **Paso 2: Las pruebas que fallan**

Crear `src/test/kotlin/sgspe/dashboard/DashboardsPropertiesTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

// especificación de F8 §2.2.16: la tabla por proyecto admite hasta sgspe.dashboards.executive.max-projects (25)
class DashboardsPropertiesTest {
    @Test
    fun `el tope por defecto es 25, el de application yml tambien, y no puede ser menor que 1`() {
        assertEquals(25, DashboardsProperties().executive.maxProjects)
        val yml = File("src/main/resources/application.yml").readText()
        assertEquals(true, Regex("""dashboards:\s+executive:\s+max-projects: 25""").containsMatchIn(yml), "falta sgspe.dashboards.executive.max-projects")
        assertThrows<IllegalArgumentException> { DashboardsProperties.Executive(maxProjects = 0) }
    }
}
```

Crear `src/test/kotlin/sgspe/dashboard/ExecutiveDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest
import tools.jackson.databind.JsonNode
import java.util.UUID

// el tablero executive (dashboards.md §5.1, api.md §4.11): las tiles en su orden con fixtures, la tendencia sin valor escalar,
// la tabla por proyecto con las mismas cifras que cada proyecto pedido solo, el tope de proyectos (Review Focus 5) y un
// tablero vacío sin proyectos
class ExecutiveDashboardApiTest : F8IndicatorsApiTest() {
    private val september = "periodStart=2026-09-01&periodEnd=2026-09-30"

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    // un proyecto activo con una interacción realizada en septiembre, un compromiso, una queja y una alerta detectada
    private fun busyProject(): String {
        val project = activeProject()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        engagement(project, "REUNION", officer.token, mapOf("held_at" to "2026-09-10T15:00:00Z")).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        commitment(project)
        grievance(project)
        earlyWarning(project)
        return project
    }

    @Test
    fun `las tiles van en su orden con las cifras del proyecto y la tendencia no tiene valor`() {
        val project = busyProject()
        val body = get("/api/sgspe/dashboards/executive?project=$project&$september")
        assertEquals("executive", body["dashboard"].asString())
        assertEquals(ExecutiveKpis.TILES, body["indicators"].values().map { it["code"].asString() })
        assertEquals(1, indicator(body, "KPI-EXE-01")["value"].asInt())
        assertEquals(1, indicator(body, "KPI-EXE-02")["value"].asInt())
        assertEquals(1, indicator(body, "KPI-ENG-01")["value"].asInt())
        assertEquals(1, indicator(body, "KPI-GRV-02")["value"].asInt())
        val trend = indicator(body, "KPI-EXE-03")
        assertTrue(trend["available"].asBoolean())
        assertTrue(trend["value"].isNull)
        assertEquals(12, trend["series"].size())
        assertEquals(1, trend["series"].values().last()["engagements"].asInt())
    }

    @Test
    fun `byProject trae una fila por proyecto con las mismas cifras que cada uno pedido solo`() {
        val busy = busyProject()
        val quiet = activeProject()
        val body = get("/api/sgspe/dashboards/executive?project=$busy&project=$quiet&$september")
        val rows = body["byProject"].values().associateBy { it["project"].asString() }
        assertEquals(setOf(busy, quiet), rows.keys)
        for (project in listOf(busy, quiet)) {
            val alone = get("/api/sgspe/dashboards/executive?project=$project&$september")
            val row = rows.getValue(project)
            assertEquals(ExecutiveKpis.BY_PROJECT, row["indicators"].values().map { it["code"].asString() })
            for (code in ExecutiveKpis.BY_PROJECT) {
                assertEquals(indicator(alone, code)["value"], row["indicators"].values().first { it["code"].asString() == code }["value"], "$project $code")
            }
            assertTrue(row["indicators"].values().none { it.has("breakdowns") || it.has("series") })
        }
        assertEquals("Proyecto Ejemplo A", rows.getValue(busy)["name"].asString())
    }

    // Review Focus 5
    @Test
    fun `mas proyectos que el tope es 400 TOO_MANY_RECORDS sobre project`() {
        val query = (1..26).joinToString("&") { "project=${UUID.randomUUID()}" }
        val response = exchange("GET", "/api/sgspe/dashboards/executive?$query")
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("TOO_MANY_RECORDS", code(response))
        assertEquals(listOf("project"), problemFields(response.body))
    }

    @Test
    fun `sin proyectos las tiles salen en cero y byProject vacio`() {
        val loner = userWithRole("ANALYST")
        val body = get("/api/sgspe/dashboards/executive?$september", loner.token)
        assertEquals(0, indicator(body, "KPI-EXE-01")["value"].asInt())
        assertEquals(0, indicator(body, "KPI-COM-02")["value"].asInt())
        assertEquals(0, body["byProject"].size())
    }

    @Test
    fun `el catalogo informa los KPI-EXE disponibles en el tablero ejecutivo`() {
        val catalog = get("/api/sgspe/dashboards/indicators").values().associateBy { it["code"].asString() }
        for (code in listOf("KPI-EXE-01", "KPI-EXE-02", "KPI-EXE-03")) {
            assertTrue(catalog.getValue(code)["available"].asBoolean(), code)
            assertEquals("executive", catalog.getValue(code)["dashboard"].asString(), code)
        }
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.DashboardsPropertiesTest'
```

Esperado: no compila (`Unresolved reference: DashboardsProperties`).

- [ ] **Paso 3: La configuración, la respuesta y el tablero**

Crear `src/main/kotlin/sgspe/dashboard/DashboardsProperties.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

// los límites de los tableros consolidados (especificación de F8 §2.2.16): la tabla por proyecto del tablero executive calcula
// cada fila con las mismas funciones, así que admite hasta max-projects proyectos; más es 400 TOO_MANY_RECORDS sobre project
@ConfigurationProperties("sgspe.dashboards")
data class DashboardsProperties(
    val executive: Executive = Executive()
) {
    data class Executive(
        val maxProjects: Int = 25
    ) {
        init {
            require(maxProjects >= 1) { "sgspe.dashboards.executive.max-projects debe ser al menos 1" }
        }
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DashboardsProperties::class)
class DashboardsConfiguration
```

En `src/main/resources/application.yml`, bajo `sgspe:` y antes de `territory:`:

```yaml
sgspe:
  dashboards:
    executive:
      # especificación de F8 §2.2.16: proyectos de la tabla por proyecto (byProject[]) del tablero executive
      max-projects: 25
  territory:
```

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, `DashboardResponse` pasa a:

```kotlin
// una fila de la tabla por proyecto del tablero executive (dashboards.md §5.1): las cifras de las tiles con valor, sin
// desgloses ni series, calculadas con las mismas funciones sobre el filtro de ese proyecto
data class ProjectFigures(
    val project: String,
    val code: String?,
    val name: String?,
    val indicators: List<IndicatorValue>
)

data class DashboardResponse(
    val dashboard: String,
    val filters: Map<String, Any?>,
    val computedAt: String,
    val indicators: List<IndicatorValue>,
    // F4: en el tablero attention, true si quien mira no ve las QCS confidenciales (dashboards.md §8); no viaja en los demás
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val confidentialHidden: Boolean? = null,
    // F8: la tabla por proyecto del tablero executive; no viaja en los demás
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val byProject: List<ProjectFigures>? = null
)
```

(La Tarea 8 suma `rows`, con el tipo de la Tarea 7.)

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `DASHBOARDS` suma, después de la fila de `TERRITORY`:

```kotlin
            TERRITORY to listOf("KPI-TER-02", "KPI-TER-03", "KPI-TER-04"),
            // F8, dashboards.md §5.1: las tiles, la inversión por moneda y la tendencia
            EXECUTIVE to ExecutiveKpis.TILES
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`:

1. El constructor suma `private val properties: DashboardsProperties` al final.
2. Dentro de `dashboard(...)`, en el bloque `readers.reading(...)`, después de `val filters = …`:

```kotlin
            // la tabla por proyecto calcula una fila por proyecto: más que el tope es 400, también por «mis proyectos»
            if (name == Kpis.EXECUTIVE && filters.projects.size > properties.executive.maxProjects) {
                throw Records.tooMany("project", properties.executive.maxProjects)
            }
```

   y la última expresión del bloque pasa a:

```kotlin
            val byProject = if (name == Kpis.EXECUTIVE) byProject(data, ctx.confidentialHidden) else null
            DashboardResponse(
                name,
                filters.echo(),
                clock.instant().toString(),
                indicators,
                if (name == Kpis.ATTENTION) ctx.confidentialHidden else null,
                byProject
            )
```

3. Después de `value(...)`:

```kotlin
    // dashboards.md §5.1: una fila por proyecto del filtro con las cifras de ExecutiveKpis.BY_PROJECT, calculadas con las
    // mismas funciones y los mismos permisos sobre el filtro de ese proyecto (DashboardData.with), sin desgloses ni series
    private suspend fun byProject(
        data: DashboardData,
        hidden: Boolean
    ): List<ProjectFigures> {
        val ids = data.filters.projects.map { it.toString() }
        val codes = data.names("project", ids, "code")
        val names = data.names("project", ids)
        return data.filters.projects.map { project ->
            val one = data.with(data.filters.copy(projects = listOf(project)))
            ProjectFigures(
                project.toString(),
                codes[project.toString()],
                names[project.toString()],
                ExecutiveKpis.BY_PROJECT.map { code ->
                    mark(KpiEvaluation.evaluate(Kpis.BY_CODE.getValue(code), one), hidden).copy(breakdowns = null, series = null)
                }
            )
        }
    }
```

- [ ] **Paso 4: Verlas pasar, el build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.*'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.dashboard.ExecutiveDashboardApiTest`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/DashboardsProperties.kt src/main/kotlin/sgspe/dashboard/Dashboards.kt \
  src/main/kotlin/sgspe/dashboard/Indicators.kt src/main/kotlin/sgspe/dashboard/Kpis.kt src/main/resources/application.yml \
  src/test/kotlin/sgspe/dashboard/DashboardsPropertiesTest.kt src/test/kotlin/sgspe/dashboard/ExecutiveDashboardApiTest.kt
git commit -m "feat: tablero executive con la tabla por proyecto y su tope" -m "GET /api/sgspe/dashboards/executive: las tiles de §5.1, KPI-INV-03 y KPI-EXE-03, y byProject[]
con las mismas funciones; más de sgspe.dashboards.executive.max-projects es 400 TOO_MANY_RECORDS.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t5 -m "Merge branch 'f82a/t5' into feat/fase-8-2a-tableros"
```

---

### Tarea 6: El tablero `project`

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, `src/main/kotlin/sgspe/dashboard/Kpis.kt`
- Test: `src/test/kotlin/sgspe/dashboard/ProjectDashboardApiTest.kt` (nuevo)

**Interfaces:**
- Consumes: `Kpis.PROJECT` (Tarea 4), `Dashboards.dashboard` (Tareas 3 y 5).
- Produces: `GET /api/sgspe/dashboards/project` (un solo `project`, `400 PROJECT_REQUIRED`).

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t6
```

- [ ] **Paso 2: La prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/ProjectDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest

// el tablero project (dashboards.md §5.2): exige un solo project (400 PROJECT_REQUIRED, también sin project y con «mis
// proyectos»), trae las zonas de §5.2 en su orden y la postura «no disponible» a quien no la lee; el mapa lo arma el portal
class ProjectDashboardApiTest : F8IndicatorsApiTest() {
    private val zones =
        listOf(
            "KPI-STK-01", "KPI-STK-02", "KPI-STK-03", "KPI-ENG-01", "KPI-ENG-02", "KPI-COM-02", "KPI-COM-03", "KPI-COM-04",
            "KPI-COM-06", "KPI-GRV-01", "KPI-GRV-02", "KPI-GRV-06", "KPI-RSK-01", "KPI-RSK-02", "KPI-INV-02", "KPI-INV-03",
            "KPI-INV-04"
        )

    @Test
    fun `sin un solo project es 400 PROJECT_REQUIRED`() {
        val a = activeProject()
        val b = activeProject()
        for (query in listOf("", "?project=$a&project=$b")) {
            val response = exchange("GET", "/api/sgspe/dashboards/project$query")
            assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
            assertEquals("PROJECT_REQUIRED", code(response))
            assertEquals(listOf("project"), problemFields(response.body))
        }
    }

    @Test
    fun `trae las zonas en su orden y la postura no disponible a quien no la lee`() {
        val project = activeProject()
        commitment(project)
        val body = get("/api/sgspe/dashboards/project?project=$project")
        assertEquals(zones, body["indicators"].values().map { it["code"].asString() })
        assertEquals(1, body["indicators"].values().first { it["code"].asString() == "KPI-COM-02" }["value"].asInt())
        val analyst = member("ANALYST", project)
        val stance = get("/api/sgspe/dashboards/project?project=$project", analyst.token)["indicators"].values().first { it["code"].asString() == "KPI-STK-02" }
        assertFalse(stance["available"].asBoolean())
        assertEquals("FIELD_NOT_READABLE", stance["unavailableReason"].asString())
    }
}
```

- [ ] **Paso 3: El tablero**

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `DASHBOARDS` suma, después de la fila de `EXECUTIVE`:

```kotlin
            EXECUTIVE to ExecutiveKpis.TILES,
            // F8, dashboards.md §5.2: paisaje de grupos de interés, relacionamiento, compromisos, QCS, riesgos y programas
            PROJECT to
                listOf(
                    "KPI-STK-01",
                    "KPI-STK-02",
                    "KPI-STK-03",
                    "KPI-ENG-01",
                    "KPI-ENG-02",
                    "KPI-COM-02",
                    "KPI-COM-03",
                    "KPI-COM-04",
                    "KPI-COM-06",
                    "KPI-GRV-01",
                    "KPI-GRV-02",
                    "KPI-GRV-06",
                    "KPI-RSK-01",
                    "KPI-RSK-02",
                    "KPI-INV-02",
                    "KPI-INV-03",
                    "KPI-INV-04"
                )
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, en `dashboard(...)`, después de la comprobación del tablero
territorial:

```kotlin
        // el tablero de un proyecto (dashboards.md §5.2): exactamente un project pedido, nunca «mis proyectos»
        if (name == Kpis.PROJECT && params["project"].orEmpty().size != 1) {
            throw Finding("PROJECT_REQUIRED", "El tablero de proyecto exige un solo project", "project", status = HttpStatus.BAD_REQUEST).toCodedException()
        }
```

- [ ] **Paso 4: Build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.dashboard.ProjectDashboardApiTest`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/Dashboards.kt src/main/kotlin/sgspe/dashboard/Kpis.kt \
  src/test/kotlin/sgspe/dashboard/ProjectDashboardApiTest.kt
git commit -m "feat: tablero project con un solo proyecto" -m "GET /api/sgspe/dashboards/project con las zonas de dashboards.md §5.2; sin un solo project, 400
PROJECT_REQUIRED.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t6 -m "Merge branch 'f82a/t6' into feat/fase-8-2a-tableros"
```

---

### Tarea 7: `MonitoringTable`: las 13 filas, sus metas y la cifra oficial, puras

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/MonitoringTable.kt`
- Test: `src/test/kotlin/sgspe/dashboard/MonitoringTableTest.kt` (nuevo)

**Interfaces:**
- Consumes: `IndicatorValue`, `DashboardFilters` (con `of` de la Tarea 3; si esta tarea va antes que la 3, la prueba arma
  `DashboardFilters` con su constructor).
- Produces: `MonitoringSpec`, `CatalogIndicator(id: String, code: String, target: BigDecimal?, verificationMeans:
  String?)`, `MeasurementRow(id: String, indicator: String, project: String?, start: LocalDate, end: LocalDate, value:
  BigDecimal?, closedAt: Instant?)`, `Official(measurement: String, closedAt: String?, value: Number?)`,
  `OfficialScope(project: String?, start: LocalDate, end: LocalDate)`, `MonitoringRow` y `MonitoringTable` (`ROWS`,
  `CODES`, `PROJECT`, `CATALOG`, `NONE`, `scope`, `official`, `target`, `months`, `rows`).

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t7
```

- [ ] **Paso 2: La unitaria que falla**

Crear `src/test/kotlin/sgspe/dashboard/MonitoringTableTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// el tablero monitoring sin base (dashboards.md §4.9, §5.5 y §10; especificación de F8 §3.1): las 13 filas de la Tabla 9.5
// en su orden, la meta y su origen (decisión 3 del plan 8.2a) y la cifra oficial solo cuando el pedido coincide con la
// medición (decisión 4; Review Focus 3)
class MonitoringTableTest {
    private val start = LocalDate.parse("2026-09-01")
    private val end = LocalDate.parse("2026-09-30")
    private val project = UUID.randomUUID()
    private val filters = DashboardFilters.of(listOf(project), start, end)

    private fun value(
        code: String,
        value: Number? = 4,
        available: Boolean = true,
        denominator: Number? = null
    ) = IndicatorValue(code, code, available, if (available) value else null, denominator = denominator)

    private val values = MonitoringTable.CODES.map { value(it) }

    private fun catalog(vararg targets: Pair<String, BigDecimal?>): Map<String, CatalogIndicator> =
        MonitoringTable.CODES.associateWith { code -> CatalogIndicator("id-$code", code, targets.toMap()[code], null) }

    private fun measurement(
        code: String,
        project: String?,
        start: LocalDate = this.start,
        end: LocalDate = this.end
    ) = MeasurementRow("m-$code-$project-$start", "id-$code", project, start, end, BigDecimal("7"), Instant.parse("2026-10-02T15:00:00Z"))

    @Test
    fun `las 13 filas son las de dashboards md 4 9 en su orden`() {
        val doc =
            File("docs/dashboards.md")
                .readLines()
                .filter { Regex("""^\| \d+ \| """).containsMatchIn(it) }
                .map { line -> line.trim().trim('|').split("|").map { it.trim() } }
        assertEquals(13, doc.size)
        assertEquals(doc.map { listOf(it[0].toInt(), it[1], it[2], it[4], it[5]) }, MonitoringTable.ROWS.map { listOf(it.number, it.label, it.code, it.verificationMeans, it.deliverable) })
    }

    @Test
    fun `la meta de 5 es la del proyecto si la tiene, si no la del catalogo por mes y proyecto, si no ninguna`() {
        val meetings = MonitoringTable.ROWS.single { it.code == "KPI-ENG-02" }
        val withTarget = value("KPI-ENG-02", 5, denominator = 6)
        val two = CatalogIndicator("i", "KPI-ENG-02", BigDecimal("2"), null)
        assertEquals(6 to MonitoringTable.PROJECT, MonitoringTable.target(meetings, withTarget, two, projectDefined = true, months = 3, projects = 1))
        assertEquals(BigDecimal("12") to MonitoringTable.CATALOG, MonitoringTable.target(meetings, withTarget, two, projectDefined = false, months = 3, projects = 2))
        assertEquals(null to MonitoringTable.NONE, MonitoringTable.target(meetings, withTarget, two.copy(target = null), projectDefined = false, months = 3, projects = 1))
        val other = MonitoringTable.ROWS.first()
        assertEquals(BigDecimal("80") to MonitoringTable.CATALOG, MonitoringTable.target(other, value(other.code), CatalogIndicator("x", other.code, BigDecimal("80"), null), false, 3, 1))
        assertEquals(3, MonitoringTable.months(LocalDate.parse("2026-07-15"), LocalDate.parse("2026-09-02")))
    }

    // Review Focus 3
    @Test
    fun `la cifra oficial casa solo con el mismo alcance, periodo y sin otros filtros`() {
        val code = "KPI-COM-08"
        val ofProject = listOf(measurement(code, project.toString()))
        val ofTenant = listOf(measurement(code, null))
        val indicator = CatalogIndicator("id-$code", code, null, null)
        val one = MonitoringTable.scope(listOf(project.toString()), filters, tenantWide = false)
        assertEquals(Official("m-$code-$project-$start", "2026-10-02T15:00:00Z", BigDecimal("7")), MonitoringTable.official(indicator, value(code), ofProject, one))
        // otro periodo, otro alcance, un filtro de territorio, dos proyectos o el tenant sin leerlo entero: ninguna
        assertNull(MonitoringTable.official(indicator, value(code), listOf(measurement(code, project.toString(), start.minusMonths(1), end.minusMonths(1))), one))
        assertNull(MonitoringTable.official(indicator, value(code), ofTenant, one))
        assertNull(MonitoringTable.scope(listOf(project.toString()), filters.copy(locality = UUID.randomUUID()), tenantWide = true))
        assertNull(MonitoringTable.scope(listOf(project.toString()), filters.copy(scope = "AIDS"), tenantWide = true))
        assertNull(MonitoringTable.scope(listOf(project.toString(), UUID.randomUUID().toString()), filters, tenantWide = true))
        assertNull(MonitoringTable.scope(emptyList(), filters, tenantWide = false))
        val tenant = MonitoringTable.scope(emptyList(), filters, tenantWide = true)
        assertEquals("m-$code-null-$start", MonitoringTable.official(indicator, value(code), ofTenant, tenant)?.measurement)
        assertNull(MonitoringTable.official(indicator, value(code), ofProject, tenant))
    }

    // Review Focus 3: #2 y #3 a quien no lee stance, también con el periodo cerrado
    @Test
    fun `sin el KPI disponible no hay cifra oficial`() {
        val code = "KPI-STK-05"
        val scope = MonitoringTable.scope(listOf(project.toString()), filters, tenantWide = false)
        val hidden = value(code, available = false)
        assertNull(MonitoringTable.official(CatalogIndicator("id-$code", code, null, null), hidden, listOf(measurement(code, project.toString())), scope))
        val rows = MonitoringTable.rows(MonitoringTable.CODES.map { if (it == code) hidden else value(it) }, catalog(), listOf(measurement(code, project.toString())), scope, false, filters)
        val row = rows.single { it.code == code }
        assertEquals(false to null, row.available to row.value)
        assertNull(row.official)
    }

    // Review Focus 5: un tenant de F7 o quien no lee indicator
    @Test
    fun `sin catalogo, sin metas ni cifras oficiales`() {
        val scope = MonitoringTable.scope(listOf(project.toString()), filters, tenantWide = false)
        val rows = MonitoringTable.rows(values, emptyMap(), emptyList(), scope, projectDefined = false, filters = filters)
        assertEquals((1..13).toList(), rows.map { it.number })
        assertEquals(setOf(MonitoringTable.NONE), rows.map { it.targetSource }.toSet())
        assertEquals(listOf<Official?>(null), rows.map { it.official }.distinct())
        assertEquals(MonitoringTable.ROWS.map { it.verificationMeans }, rows.map { it.verificationMeans })
    }

    @Test
    fun `el catalogo pone la meta y el medio de verificacion editado`() {
        val edited = catalog("KPI-ENG-02" to BigDecimal("2")) + ("KPI-STK-04" to CatalogIndicator("id-KPI-STK-04", "KPI-STK-04", BigDecimal("30"), "Padrón de contactos"))
        val rows = MonitoringTable.rows(values, edited, emptyList(), null, projectDefined = false, filters = filters).associateBy { it.code }
        assertEquals(BigDecimal("2") to MonitoringTable.CATALOG, rows.getValue("KPI-ENG-02").target to rows.getValue("KPI-ENG-02").targetSource)
        assertEquals(BigDecimal("30") to "Padrón de contactos", rows.getValue("KPI-STK-04").target to rows.getValue("KPI-STK-04").verificationMeans)
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.MonitoringTableTest'
```

Esperado: no compila (`Unresolved reference: MonitoringTable`).

- [ ] **Paso 3: La tabla**

Crear `src/main/kotlin/sgspe/dashboard/MonitoringTable.kt`:

```kotlin
package sgspe.dashboard

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// una fila de la Tabla 9.5 de DOC-01 como la fija dashboards.md §4.9: número, indicador, KPI, medio de verificación y
// entregable que la produciría
data class MonitoringSpec(
    val number: Int,
    val code: String,
    val label: String,
    val verificationMeans: String,
    val deliverable: String
)

// un registro de indicator como lo usa el tablero: su id, su código, su meta y su medio de verificación (editables)
data class CatalogIndicator(
    val id: String,
    val code: String,
    val target: BigDecimal?,
    val verificationMeans: String?
)

// una indicator_measurement: su indicador, su proyecto (null, todo el tenant), su periodo, su valor y su cierre
data class MeasurementRow(
    val id: String,
    val indicator: String,
    val project: String?,
    val start: LocalDate,
    val end: LocalDate,
    val value: BigDecimal?,
    val closedAt: Instant?
)

// la cifra oficial de una fila (api.md §4.11): la medición, el instante del cierre y el valor
data class Official(
    val measurement: String,
    val closedAt: String?,
    val value: Number?
)

// lo que el pedido es para la cifra oficial: un proyecto o, con project null, todo el tenant, y un periodo
data class OfficialScope(
    val project: String?,
    val start: LocalDate,
    val end: LocalDate
)

// una fila del tablero monitoring (decisión 5 del plan 8.2a): su número y su texto de la Tabla 9.5, el medio de verificación
// (el del catálogo si lo editaron), el entregable, la disponibilidad y el valor del KPI (sus desgloses van en indicators[] del
// mismo orden), la meta con su origen y la cifra oficial, o null
data class MonitoringRow(
    val number: Int,
    val code: String,
    val label: String,
    val verificationMeans: String,
    val deliverable: String,
    val available: Boolean,
    val value: Number?,
    val target: Number?,
    val targetSource: String,
    val official: Official?
)

// el tablero monitoring sin base (dashboards.md §5.5 y §10, ASM-136)
object MonitoringTable {
    const val PROJECT = "PROJECT"
    const val CATALOG = "CATALOG"
    const val NONE = "NONE"
    private const val MEETINGS = "KPI-ENG-02"

    val ROWS: List<MonitoringSpec> =
        listOf(
            MonitoringSpec(1, "KPI-STK-04", "Grupos de interés contactados, por sexo, ámbito e institución", "Reporte de cobertura de grupos de interés contactados", "T5"),
            MonitoringSpec(
                2,
                "KPI-STK-05",
                "Porcentaje de grupos de interés de oposición que aceptaron conversar con la empresa",
                "Reporte de cobertura de grupos de interés contactados",
                "T5"
            ),
            MonitoringSpec(3, "KPI-STK-06", "Porcentaje de grupos de interés de oposición que variaron su posición", "Reporte de cobertura de grupos de interés contactados", "T5"),
            MonitoringSpec(
                4,
                "KPI-STK-07",
                "Porcentaje de grupos de interés que aceptaron más de un contacto",
                "Reporte de reuniones mensuales; minuta de reunión; sistematización de percepciones y posición",
                "T3, T6"
            ),
            MonitoringSpec(5, "KPI-ENG-02", "Reuniones sostenidas al mes (al menos 2)", "Reporte de reuniones mensuales; minuta de reunión", "T3"),
            MonitoringSpec(6, "KPI-GRV-07", "Porcentaje de quejas, consultas o solicitudes absueltas", "Reporte de quejas, consultas y solicitudes", "T4"),
            MonitoringSpec(
                7,
                "KPI-ENG-03",
                "Asistentes por reunión informativa, por sexo y ámbito, con la organización que representan",
                "Reporte de reuniones informativas y de recojo de sugerencias; lista de asistentes",
                "T10"
            ),
            MonitoringSpec(8, "KPI-ENG-04", "Temas consultados durante la reunión", "Reporte de reuniones informativas y de recojo de sugerencias", "T10"),
            MonitoringSpec(
                9,
                "KPI-ENG-05",
                "Porcentaje de inquietudes resueltas en las reuniones informativas",
                "Reporte de reuniones informativas; evaluaciones de los participantes de talleres",
                "T10"
            ),
            MonitoringSpec(10, "KPI-COM-08", "Compromisos formales (escritos) según tema", "Matriz de seguimiento de compromisos", "T7"),
            MonitoringSpec(
                11,
                "KPI-ENG-06",
                "Monitoreos ambientales del agua participativos y con agente independiente",
                "Reporte de los resultados de los monitoreos de agua",
                "T9"
            ),
            MonitoringSpec(12, "KPI-ENG-07", "Comunicados en medios sobre compromisos cumplidos", "Matriz de seguimiento de compromisos (difusión)", "T7"),
            MonitoringSpec(13, "KPI-ENG-08", "Porcentaje de medios con información positiva y negativa", "Seguimiento de medios informativos [DOC-01 §9.3.1]", "—")
        )

    val CODES: List<String> = ROWS.map { it.code }

    // a qué medición puede casar el pedido (decisión 4 del plan 8.2a): ningún filtro fuera de project y el periodo; un solo
    // project pedido, o ninguno por quien lee todo el tenant. si no, ninguna cifra oficial
    fun scope(
        requestedProjects: List<String>,
        filters: DashboardFilters,
        tenantWide: Boolean
    ): OfficialScope? =
        when {
            filters.given().isNotEmpty() -> null
            requestedProjects.size == 1 -> OfficialScope(requestedProjects.single(), filters.periodStart, filters.periodEnd)
            requestedProjects.isEmpty() && tenantWide -> OfficialScope(null, filters.periodStart, filters.periodEnd)
            else -> null
        }

    // la medición del indicador con el mismo alcance y el mismo periodo, solo si el KPI está disponible para el lector (el
    // chequeo de dashboards.md §8 antes de mostrar una cifra oficial)
    fun official(
        indicator: CatalogIndicator?,
        value: IndicatorValue,
        measurements: List<MeasurementRow>,
        scope: OfficialScope?
    ): Official? {
        if (indicator == null || scope == null || !value.available) return null
        val match =
            measurements.firstOrNull {
                it.indicator == indicator.id && it.project == scope.project && it.start == scope.start && it.end == scope.end
            } ?: return null
        return Official(match.id, match.closedAt?.toString(), match.value)
    }

    // los meses de calendario que toca el periodo, los dos extremos incluidos
    fun months(
        start: LocalDate,
        end: LocalDate
    ): Int = (YearMonth.from(start).until(YearMonth.from(end), java.time.temporal.ChronoUnit.MONTHS) + 1).toInt()

    // la meta de la fila y su origen (decisión 3 del plan 8.2a). #5: la del proyecto (el denominador de KPI-ENG-02) si un
    // proyecto del filtro o un plan la define; si no, la del catálogo por mes y por proyecto; si no, ninguna. las demás: la
    // del catálogo tal cual, o ninguna
    fun target(
        spec: MonitoringSpec,
        value: IndicatorValue,
        indicator: CatalogIndicator?,
        projectDefined: Boolean,
        months: Int,
        projects: Int
    ): Pair<Number?, String> =
        when {
            spec.code == MEETINGS && projectDefined && value.available -> value.denominator to PROJECT
            indicator?.target == null -> null to NONE
            spec.code == MEETINGS -> indicator.target.multiply(BigDecimal(months.toLong() * projects)) to CATALOG
            else -> indicator.target to CATALOG
        }

    // las 13 filas en su orden. `values`: los 13 KPI ya evaluados para el lector; `catalog`: los indicator por código (vacío en
    // un tenant de F7 o si el lector no lee indicator)
    fun rows(
        values: List<IndicatorValue>,
        catalog: Map<String, CatalogIndicator>,
        measurements: List<MeasurementRow>,
        scope: OfficialScope?,
        projectDefined: Boolean,
        filters: DashboardFilters
    ): List<MonitoringRow> {
        val months = months(filters.periodStart, filters.periodEnd)
        return ROWS.map { spec ->
            val value = values.first { it.code == spec.code }
            val indicator = catalog[spec.code]
            val (target, source) = target(spec, value, indicator, projectDefined, months, filters.projects.size)
            MonitoringRow(
                spec.number,
                spec.code,
                spec.label,
                indicator?.verificationMeans?.ifBlank { null } ?: spec.verificationMeans,
                spec.deliverable,
                value.available,
                if (value.available) value.value else null,
                target,
                source,
                official(indicator, value, measurements, scope)
            )
        }
    }
}
```

- [ ] **Paso 4: Verla pasar, build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.MonitoringTableTest'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Si la prueba contra `dashboards.md` §4.9 falla por un texto, gana el documento: se corrige
`ROWS`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/MonitoringTable.kt src/test/kotlin/sgspe/dashboard/MonitoringTableTest.kt
git commit -m "feat: MonitoringTable con las filas de la Tabla 9.5, sus metas y la cifra oficial" -m "Funciones puras: la meta y su origen (PROJECT, CATALOG o NONE) y la cifra oficial solo con el
mismo alcance, el mismo periodo y sin otros filtros (ASM-136).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t7 -m "Merge branch 'f82a/t7' into feat/fase-8-2a-tableros"
```

---

### Tarea 8: El tablero `monitoring`

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, `src/main/kotlin/sgspe/dashboard/DashboardData.kt`,
  `src/main/kotlin/sgspe/dashboard/Kpis.kt`, `src/main/kotlin/sgspe/dashboard/Indicators.kt`
- Test: `src/test/kotlin/sgspe/dashboard/MonitoringDashboardApiTest.kt` (nuevo),
  `src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt`, `src/test/kotlin/sgspe/dashboard/EngagementKpisF4Test.kt`

**Interfaces:**
- Consumes: `MonitoringTable` (Tarea 7), `Dashboards` (Tareas 3, 5 y 6), `TenantModel` (`sgspe.common`),
  `F8IndicatorsApiTest` (Tarea 2).
- Produces: `GET /api/sgspe/dashboards/monitoring` con `rows[]`; `DashboardResponse.rows`;
  `DashboardData.indicatorCatalog()`, `DashboardData.measurements(start: LocalDate)` y
  `DashboardData.meetingTargetDefined()`; el `dashboard` del catálogo de KPI-STK-04, 05, 07 y KPI-ENG-06, 07 y 08 es
  `monitoring`.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t8
```

- [ ] **Paso 2: Las pruebas que fallan**

En `src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt` (~l. 175-177), las tres líneas
`assertTrue(catalog.getValue("KPI-STK-0X")["dashboard"].isNull)` pasan a
`assertEquals("monitoring", catalog.getValue("KPI-STK-0X")["dashboard"].asString())` (X = 4, 5 y 7), con el comentario
«desde F8 tienen zona en el tablero de monitoreo (dashboards.md §11)». En
`src/test/kotlin/sgspe/dashboard/EngagementKpisF4Test.kt` (~l. 97), `assertEquals(null, Kpis.BY_CODE.getValue(code).dashboard,
code)` pasa a `assertEquals(Kpis.MONITORING, Kpis.BY_CODE.getValue(code).dashboard, code)`.

Crear `src/test/kotlin/sgspe/dashboard/MonitoringDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.F8IndicatorsApiTest
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID

// el tablero monitoring (dashboards.md §5.5 y §10, api.md §4.11): las 13 filas en su orden con su meta y su origen, la cifra
// oficial solo con el pedido de su medición (Review Focus 3), #2 y #3 «no disponible» sin stance también cerrados, y sin leer
// el catálogo el tablero sale sin metas ni cifras oficiales (Review Focus 5). las mediciones se crean como la plataforma: el
// endpoint de cierre llega en la Tarea 10 y tiene su propia prueba
class MonitoringDashboardApiTest : F8IndicatorsApiTest() {
    private fun board(
        query: String,
        token: String = this.token
    ): Map<String, JsonNode> = get("/api/sgspe/dashboards/monitoring?$query", token)["rows"].values().associateBy { it["code"].asString() }

    private fun period(
        start: LocalDate,
        end: LocalDate
    ) = "periodStart=$start&periodEnd=$end"

    private fun measurement(
        code: String,
        project: String?,
        start: LocalDate,
        end: LocalDate,
        value: Int
    ): String =
        platformCreate(
            "indicator_measurement",
            mapOf(
                "indicator" to indicatorId(code),
                "project" to project,
                "period_start_date" to start.toString(),
                "period_end_date" to end.toString(),
                "measured_value" to value,
                "closed_by_user_id" to "00000000-0000-0000-0000-000000000001",
                "closed_by_user_email" to "gerente-social@sgspe.test"
            )
        )

    @Test
    fun `las 13 filas van en su orden con la meta del proyecto o la del catalogo`() {
        val (start, end) = lastMonth()
        val own = activeProject(attributes = mapOf("meetings_per_month_target" to 3))
        val plain = activeProject()
        val body = get("/api/sgspe/dashboards/monitoring?project=$own&${period(start, end)}")
        assertEquals(MonitoringTable.CODES, body["indicators"].values().map { it["code"].asString() })
        assertEquals((1..13).toList(), body["rows"].values().map { it["number"].asInt() })
        val meetings = body["rows"].values().single { it["code"].asString() == "KPI-ENG-02" }
        assertEquals(3 to "PROJECT", meetings["target"].asInt() to meetings["targetSource"].asString())
        val catalog = board("project=$plain&${period(start, end)}").getValue("KPI-ENG-02")
        assertEquals(2 to "CATALOG", catalog["target"].asInt() to catalog["targetSource"].asString())
        val first = board("project=$plain&${period(start, end)}").getValue("KPI-STK-04")
        assertEquals("NONE", first["targetSource"].asString())
        assertTrue(first["target"].isNull)
        assertEquals("Reporte de cobertura de grupos de interés contactados", first["verificationMeans"].asString())
    }

    // Review Focus 3
    @Test
    fun `la cifra oficial sale solo con el pedido de su medicion`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val other = activeProject()
        val id = measurement("KPI-COM-08", project, start, end, 7)
        val official = board("project=$project&${period(start, end)}").getValue("KPI-COM-08")["official"]
        assertEquals(id to 7, official["measurement"].asString() to official["value"].asInt())
        for (query in listOf(
            "project=$project&${period(start, end)}&locality=${UUID.randomUUID()}",
            "project=$project&project=$other&${period(start, end)}",
            "project=$project&${period(start.minusMonths(1), start.minusDays(1))}",
            "project=$other&${period(start, end)}"
        )) {
            assertTrue(board(query).getValue("KPI-COM-08")["official"].isNull, query)
        }
        // la del tenant (un mes viejo al azar: sin project, solo para quien lee todos los proyectos
        val (oldStart, oldEnd) = pastMonth()
        measurement("KPI-COM-08", null, oldStart, oldEnd, 9)
        assertEquals(9, board(period(oldStart, oldEnd)).getValue("KPI-COM-08")["official"]["value"].asInt())
        val social = member("SOCIAL_MANAGER", project)
        assertTrue(board(period(oldStart, oldEnd), social.token).getValue("KPI-COM-08")["official"].isNull)
    }

    // Review Focus 3: dashboards.md §8
    @Test
    fun `2 y 3 no disponibles sin stance, tambien cerrados`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        measurement("KPI-STK-05", project, start, end, 50)
        val admin = board("project=$project&${period(start, end)}").getValue("KPI-STK-05")
        assertEquals(50, admin["official"]["value"].asInt())
        val analyst = board("project=$project&${period(start, end)}", member("ANALYST", project).token)
        for (code in listOf("KPI-STK-05", "KPI-STK-06")) {
            val row = analyst.getValue(code)
            assertFalse(row["available"].asBoolean(), code)
            assertTrue(row["value"].isNull, code)
            assertTrue(row["official"].isNull, code)
        }
    }

    // Review Focus 5: FIELD_AGENT no lee indicator ni indicator_measurement (security.md §7.8)
    @Test
    fun `sin leer el catalogo el tablero sale sin metas ni cifras oficiales`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        measurement("KPI-COM-08", project, start, end, 7)
        val rows = board("project=$project&${period(start, end)}", member("FIELD_AGENT", project).token)
        assertEquals(13, rows.size)
        assertTrue(rows.values.all { it["targetSource"].asString() == "NONE" && it["official"].isNull })
    }

    @Test
    fun `el catalogo ubica los KPI sin zona propia en monitoring`() {
        val catalog = get("/api/sgspe/dashboards/indicators").values().associateBy { it["code"].asString() }
        for (code in listOf("KPI-STK-04", "KPI-STK-05", "KPI-STK-07", "KPI-ENG-06", "KPI-ENG-07", "KPI-ENG-08")) {
            assertEquals("monitoring", catalog.getValue(code)["dashboard"].asString(), code)
        }
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.EngagementKpisF4Test'
```

Esperado: falla (`expected: <monitoring> but was: <null>`).

- [ ] **Paso 3: El catálogo, las mediciones y las metas en `DashboardData`**

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, después de la sección de los ejecutivos (Tarea 4):

```kotlin
    // ---- monitoreo (F8: dashboards.md §5.5 y §10) ----

    // los indicator del tenant por código, como los lee quien mira: vacío si no lee indicator.code (la meta, solo si lee
    // target_value). quien llama comprueba antes que el tenant tenga el objeto (TenantModel)
    suspend fun indicatorCatalog(): Map<String, CatalogIndicator> {
        if (!readable("indicator.code")) return emptyMap()
        val targets = readable("indicator.target_value")
        val means = readable("indicator.verification_means")
        return read("indicator")
            .mapNotNull { r ->
                val code = Values.text(r.attributes["code"]) ?: return@mapNotNull null
                CatalogIndicator(
                    r.id,
                    code,
                    if (targets) Values.decimal(r.attributes["target_value"]) else null,
                    if (means) Values.text(r.attributes["verification_means"]) else null
                )
            }.associateBy { it.code }
    }

    // las mediciones que empiezan en `start`, como las lee quien mira (las de proyecto, por el alcance; las del tenant, todos
    // los que leen el objeto); vacío si no lee indicator_measurement
    suspend fun measurements(start: LocalDate): List<MeasurementRow> {
        if (!readable("indicator_measurement.measured_value") || !readable("indicator_measurement.period_end_date")) return emptyList()
        return read("indicator_measurement", criteria = listOf(Criteria.dateBetween("period_start_date", start, start))).mapNotNull { r ->
            val a = r.attributes
            MeasurementRow(
                r.id,
                Values.text(a["indicator"]) ?: return@mapNotNull null,
                Values.text(a["project"]),
                Values.text(a["period_start_date"])?.let(LocalDate::parse) ?: return@mapNotNull null,
                Values.text(a["period_end_date"])?.let(LocalDate::parse) ?: return@mapNotNull null,
                Values.decimal(a["measured_value"]),
                r.createdAt
            )
        }
    }

    // si la meta de reuniones del periodo la define el proyecto (decisión 3 del plan 8.2a): algún proyecto del filtro con
    // meetings_per_month_target o algún plan con meta vigente en un mes del periodo. sin lectura de esos campos, no
    suspend fun meetingTargetDefined(): Boolean {
        if (readable("project.meetings_per_month_target") &&
            projectRecords().any { Values.decimal(it.attributes["meetings_per_month_target"]) != null }
        ) {
            return true
        }
        if (filters.projects.isEmpty() || !readable("engagement_plan.meetings_per_month_target")) return false
        return read("engagement_plan", criteria = listOf(Criteria.anyOf("project", filters.projects))).any { p ->
            val a = p.attributes
            val from = Values.text(a["start_date"])?.let(LocalDate::parse)
            val until = Values.text(a["end_date"])?.let(LocalDate::parse)
            Values.decimal(a["meetings_per_month_target"]) != null &&
                (from == null || !from.isAfter(filters.periodEnd)) &&
                (until == null || !until.isBefore(filters.periodStart))
        }
    }
```

(`Criteria.dateBetween` es el criterio de fechas que ya usa `mentions()`.)

- [ ] **Paso 4: El tablero**

En `src/main/kotlin/sgspe/dashboard/Indicators.kt`, `DashboardResponse` suma, después de `byProject`:

```kotlin
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val byProject: List<ProjectFigures>? = null,
    // F8: las 13 filas de la Tabla 9.5 del tablero monitoring (MonitoringTable), en el orden de indicators[]; no viaja en los
    // demás
    @get:JsonInclude(JsonInclude.Include.NON_NULL) val rows: List<MonitoringRow>? = null
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`:

1. `DASHBOARDS` suma, después de la fila de `PROJECT`:

```kotlin
            // F8, dashboards.md §5.5: las 13 filas de la Tabla 9.5 en su orden (§4.9)
            MONITORING to MonitoringTable.CODES
```

2. En las entradas de `ALL`, el tercer argumento (`dashboard`) de KPI-STK-04, KPI-STK-05, KPI-STK-07, KPI-ENG-06,
   KPI-ENG-07 y KPI-ENG-08 pasa de `null` a `MONITORING` (dashboards.md §11: desde F8 tienen pantalla en el monitoreo), y
   el comentario `// ---- F4: relacionamiento (dashboards.md §4.3, #11 a #13 de la Tabla 9.5): sin zona en un tablero hasta F8 (§11) ----`
   termina en «…: en el tablero de monitoreo desde F8 (§11) ----».

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`:

1. El constructor suma `private val tenantModel: TenantModel` (de `sgspe.common`).
2. En `dashboard(...)`, la última expresión del bloque de `readers.reading` pasa a:

```kotlin
            val byProject = if (name == Kpis.EXECUTIVE) byProject(data, ctx.confidentialHidden) else null
            val rows = if (name == Kpis.MONITORING) monitoringRows(ctx, data, params["project"].orEmpty(), indicators) else null
            DashboardResponse(
                name,
                filters.echo(),
                clock.instant().toString(),
                indicators,
                if (name == Kpis.ATTENTION) ctx.confidentialHidden else null,
                byProject,
                rows
            )
```

3. Después de `byProject(...)`:

```kotlin
    // dashboards.md §5.5: las 13 filas con su meta y su cifra oficial. un tenant sin el modelo de F8 (TenantModel sin
    // indicator) no lee metas ni mediciones; quien no lee el catálogo, tampoco (DashboardData.indicatorCatalog)
    private suspend fun monitoringRows(
        ctx: KpiContext,
        data: DashboardData,
        requestedProjects: List<String>,
        values: List<IndicatorValue>
    ): List<MonitoringRow> {
        val withModel = tenantModel.has(ctx.reader.organizationId, INDICATOR)
        val catalog = if (withModel) data.indicatorCatalog() else emptyMap()
        val measurements = if (withModel && catalog.isNotEmpty()) data.measurements(data.filters.periodStart) else emptyList()
        val scope = MonitoringTable.scope(requestedProjects, data.filters, ctx.tenantWide)
        return MonitoringTable.rows(values, catalog, measurements, scope, data.meetingTargetDefined(), data.filters)
    }
```

   y en el `companion object`, `const val INDICATOR = "indicator"`.

- [ ] **Paso 5: Verlas pasar, el build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.*'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.dashboard.MonitoringDashboardApiTest`,
`sgspe.dashboard.DashboardsApiTest`, `sgspe.dashboard.EngagementIndicatorsF4ApiTest`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/Dashboards.kt src/main/kotlin/sgspe/dashboard/DashboardData.kt \
  src/main/kotlin/sgspe/dashboard/Kpis.kt src/main/kotlin/sgspe/dashboard/Indicators.kt \
  src/test/kotlin/sgspe/dashboard/MonitoringDashboardApiTest.kt src/test/kotlin/sgspe/dashboard/DashboardsApiTest.kt \
  src/test/kotlin/sgspe/dashboard/EngagementKpisF4Test.kt
git commit -m "feat: tablero monitoring con la Tabla 9.5, sus metas y la cifra oficial" -m "GET /api/sgspe/dashboards/monitoring con rows[]: meta y origen, medio de verificación del
catálogo y la medición oficial solo con su pedido; un tenant de F7 responde sin metas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t8 -m "Merge branch 'f82a/t8' into feat/fase-8-2a-tableros"
```

---

### Tarea 9: Las reglas puras del cierre (`Closability`)

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/Closability.kt`
- Test: `src/test/kotlin/sgspe/dashboard/ClosabilityTest.kt` (nuevo)

**Interfaces:**
- Consumes: `Kpis.BY_CODE`, `Kpis.ALL`, `TerritorySummary.CODE`, `ExecutiveKpis.TREND` (Tarea 4), `IndicatorValue`,
  `Breakdown`.
- Produces: `ClosingPeriod(project: UUID?, start: LocalDate, end: LocalDate)` con `ClosingPeriod.parse(body: Map<String,
  Any?>)`; `Closability` (`STANCE`, `NO_SCALAR_VALUE`, `UNAVAILABLE`, `CONFIDENTIAL_HIDDEN`, `STANCE_KPIS`, `NO_SCALAR`,
  `before(code: String): String?`, `after(value: IndicatorValue): String?`, `breakdown(value: IndicatorValue): String?`).

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t9
```

- [ ] **Paso 2: La unitaria que falla**

Crear `src/test/kotlin/sgspe/dashboard/ClosabilityTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import wasichai.core.common.WasichaiException
import java.time.LocalDate
import java.util.UUID

// una cifra oficial es completa o no existe (especificación de F8 §2.2.15, ASM-136): las razones antes y después de calcular,
// el desglose principal como tabla Markdown y el cuerpo del cierre
class ClosabilityTest {
    @Test
    fun `antes de calcular, la postura, lo que no tiene escalar y lo que Kpis no calcula`() {
        for (code in listOf("KPI-STK-02", "KPI-STK-05", "KPI-STK-06")) assertEquals(Closability.STANCE, Closability.before(code), code)
        for (code in listOf("KPI-TER-01", "KPI-EXE-03")) assertEquals(Closability.NO_SCALAR_VALUE, Closability.before(code), code)
        assertEquals(Closability.UNAVAILABLE, Closability.before("KPI-XYZ-01"))
        assertNull(Closability.before("KPI-COM-01"))
        // la lista de postura es exactamente la de los KPI que leen stance (dashboards.md §8)
        assertEquals(Closability.STANCE_KPIS, Kpis.ALL.filter { kpi -> kpi.requires.any { it.endsWith(".stance") } }.map { it.code }.toSet())
    }

    @Test
    fun `despues de calcular, no disponible, confidencial oculta y sin valor`() {
        assertEquals(Closability.UNAVAILABLE, Closability.after(IndicatorValue("KPI-COM-10", "x", false, unavailableReason = "FIELD_NOT_READABLE")))
        assertEquals(Closability.CONFIDENTIAL_HIDDEN, Closability.after(IndicatorValue("KPI-GRV-01", "x", true, 3, confidentialHidden = true)))
        assertEquals(Closability.NO_SCALAR_VALUE, Closability.after(IndicatorValue("KPI-STK-07", "x", true, null)))
        assertNull(Closability.after(IndicatorValue("KPI-COM-01", "x", true, 0)))
    }

    @Test
    fun `el desglose principal es una tabla Markdown sin romperse con barras ni saltos`() {
        val value =
            IndicatorValue(
                "KPI-COM-01",
                "x",
                true,
                3,
                breakdowns =
                    listOf(
                        Breakdown("workflow_state", listOf(mapOf("key" to "draft", "value" to 2), mapOf("key" to "a|b\nc", "label" to "Etiqueta", "value" to 1))),
                        Breakdown("otro", listOf(mapOf("key" to "z", "value" to 9)))
                    )
            )
        assertEquals("| workflow_state | Valor |\n|---|---|\n| draft | 2 |\n| Etiqueta | 1 |", Closability.breakdown(value))
        assertNull(Closability.breakdown(IndicatorValue("KPI-COM-02", "x", true, 3)))
    }

    @Test
    fun `el cuerpo del cierre trae el periodo y, si es de un proyecto, su id`() {
        val project = UUID.randomUUID()
        val body = mapOf("project" to project.toString(), "attributes" to mapOf("period_start_date" to "2026-09-01", "period_end_date" to "2026-09-30"))
        assertEquals(ClosingPeriod(project, LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")), ClosingPeriod.parse(body))
        assertNull(ClosingPeriod.parse(body - "project").project)
        fun code(body: Map<String, Any?>) = assertThrows<WasichaiException> { ClosingPeriod.parse(body) }.violations.single().let { it.field to it.message }
        assertEquals("attributes.period_end_date" to "FIELD_REQUIRED", code(mapOf("attributes" to mapOf("period_start_date" to "2026-09-01"))))
        assertEquals("attributes.period_start_date" to "INVALID_VALUE", code(mapOf("attributes" to mapOf("period_start_date" to "1/9/2026", "period_end_date" to "2026-09-30"))))
        assertEquals("attributes.period_start_date" to "OUT_OF_RANGE", code(mapOf("attributes" to mapOf("period_start_date" to "2026-10-01", "period_end_date" to "2026-09-30"))))
        assertThrows<WasichaiException> { ClosingPeriod.parse(body + ("note" to "x")) }
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.ClosabilityTest'
```

Esperado: no compila (`Unresolved reference: Closability`). (Si `WasichaiException` no expone `violations` con ese
nombre, se usa el de `RiskAssessmentInputTest`, que ya lee los `errors[]` de una `Finding`.)

- [ ] **Paso 3: Las reglas**

Crear `src/main/kotlin/sgspe/dashboard/Closability.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.ValidationException
import java.time.LocalDate
import java.util.UUID

// el periodo que se cierra (api.md §4.11): `{ project, attributes: { period_start_date, period_end_date } }`; sin project, todo
// el tenant. una fecha que falta es 400 FIELD_REQUIRED, una ilegible 400 INVALID_VALUE y un inicio posterior al fin 400
// OUT_OF_RANGE (decisión 15 del plan 8.2a)
data class ClosingPeriod(
    val project: UUID?,
    val start: LocalDate,
    val end: LocalDate
) {
    companion object {
        private val DATES = setOf("period_start_date", "period_end_date")

        fun parse(body: Map<String, Any?>): ClosingPeriod {
            StrictBody.requireOnly(body, setOf("project", "attributes"))
            val attributes = StrictBody.objectOf(body, "attributes")
            StrictBody.requireOnly(attributes, DATES, "attributes.")
            val project =
                body["project"]?.let { Records.uuid(it) ?: throw ValidationException("Proyecto inválido", "project", "debe ser un UUID") }
            val start = date(attributes, "period_start_date")
            val end = date(attributes, "period_end_date")
            if (start.isAfter(end)) {
                throw bad("OUT_OF_RANGE", "period_start_date no puede ser posterior a period_end_date", "period_start_date")
            }
            return ClosingPeriod(project, start, end)
        }

        private fun date(
            attributes: Map<String, Any?>,
            name: String
        ): LocalDate {
            val raw = Values.text(attributes[name]) ?: throw bad("FIELD_REQUIRED", "El cierre exige $name", name)
            return runCatching { LocalDate.parse(raw) }.getOrNull() ?: throw bad("INVALID_VALUE", "$name debe ser una fecha AAAA-MM-DD", name)
        }

        private fun bad(
            code: String,
            message: String,
            name: String
        ) = Finding(code, message, "attributes.$name", status = HttpStatus.BAD_REQUEST).toCodedException()
    }
}

// las reglas de «una cifra oficial es completa o no existe» (especificación de F8 §2.2.15, ASM-136; decisiones 2 y 14 del plan
// 8.2a): antes de calcular, por el código; después, por lo que dio el KPI calculado como quien cierra
object Closability {
    const val STANCE = "STANCE"
    const val NO_SCALAR_VALUE = "NO_SCALAR_VALUE"
    const val UNAVAILABLE = "UNAVAILABLE"
    const val CONFIDENTIAL_HIDDEN = "CONFIDENTIAL_HIDDEN"

    // los que leen la postura: una cifra congelada la leería cualquiera con READ sobre indicator_measurement (security.md §7.9)
    val STANCE_KPIS: Set<String> = setOf("KPI-STK-02", "KPI-STK-05", "KPI-STK-06")

    // los que nunca tienen un valor escalar
    val NO_SCALAR: Set<String> = setOf(TerritorySummary.CODE, ExecutiveKpis.TREND)

    // la razón por el código, o null si se puede calcular: un código que Kpis no calcula (un indicador propio del cliente) no
    // tiene cifra que congelar
    fun before(code: String): String? =
        when {
            code !in Kpis.BY_CODE -> UNAVAILABLE
            code in STANCE_KPIS -> STANCE
            code in NO_SCALAR -> NO_SCALAR_VALUE
            else -> null
        }

    // la razón por el KPI calculado, o null si es una cifra completa
    fun after(value: IndicatorValue): String? =
        when {
            !value.available -> UNAVAILABLE
            value.confidentialHidden == true -> CONFIDENTIAL_HIDDEN
            value.value == null -> NO_SCALAR_VALUE
            else -> null
        }

    // el primer desglose como tabla Markdown («| <by> | Valor |»), con la etiqueta de cada fila o su clave; null sin desglose
    fun breakdown(value: IndicatorValue): String? {
        val first = value.breakdowns?.firstOrNull() ?: return null
        if (first.rows.isEmpty()) return null
        val lines = first.rows.map { row -> "| ${cell(row["label"] ?: row["key"])} | ${cell(row["value"])} |" }
        return (listOf("| ${cell(first.by)} | Valor |", "|---|---|") + lines).joinToString("\n")
    }

    private fun cell(value: Any?): String = (value?.toString() ?: "").replace("|", "\\|").replace("\n", " ")
}
```

- [ ] **Paso 4: Verla pasar y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.ClosabilityTest'
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. (La prueba de la tabla espera `| a\|b c |` solo si la fila no trae `label`; la del ejemplo
lo trae, así que sale `Etiqueta`.)

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/Closability.kt src/test/kotlin/sgspe/dashboard/ClosabilityTest.kt
git commit -m "feat: las reglas puras del cierre de periodo" -m "Closability decide STANCE, NO_SCALAR_VALUE, UNAVAILABLE y CONFIDENTIAL_HIDDEN antes y después de
calcular, y arma el desglose en Markdown; ClosingPeriod valida el cuerpo (ASM-136).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t9 -m "Merge branch 'f82a/t9' into feat/fase-8-2a-tableros"
```

---

### Tarea 10: El cierre de periodo (`IndicatorMeasurements`)

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/IndicatorMeasurements.kt`
- Test: `src/test/kotlin/sgspe/dashboard/IndicatorMeasurementsApiTest.kt` (nuevo)

**Interfaces:**
- Consumes: `ClosingPeriod`, `Closability` (Tarea 9); `Dashboards.value`, `KpiReader.Person` (Tarea 3); `Records`,
  `Transactions.locked`, `Permissions.require`, `ProjectReach.mine`, `Idempotency`, `CommandAnswer`, `IdempotencyKey`,
  `StrictQuery`, `Callers` (`sgspe.common`); `GlobalExceptionHandler` de wasichai; `F8IndicatorsApiTest` (Tarea 2).
- Produces: `POST /api/sgspe/indicators/{id}/measurements` → `201` con la `indicator_measurement`;
  `IndicatorNotClosable(reason: String, message: String)`.

El orden de las comprobaciones: el cuerpo (400), CREATE sobre `indicator_measurement` (403), todo el tenant sin leer todos
los proyectos (`403 ACTION_REQUIRED`), el proyecto y el indicador leídos como quien cierra (404), el periodo terminado
(`400 PERIOD_NOT_ENDED`), la razón por el código (`409 INDICATOR_NOT_CLOSABLE`) y, bajo el candado
`indicator_measurement:<indicador>:<proyecto|tenant>:<inicio>`, la medición repetida (`409 MEASUREMENT_EXISTS`), el
cálculo como quien cierra, la razón por el resultado y el alta. Todo como la persona (Restricciones globales).

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t10
```

- [ ] **Paso 2: La prueba de integración**

Crear `src/test/kotlin/sgspe/dashboard/IndicatorMeasurementsApiTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F8IndicatorsApiTest
import sgspe.common.LIMA
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

// el cierre de periodo (api.md §4.11, dashboards.md §10, security.md §14; especificación de F8 §5): una cifra escalar se
// congela con su numerador, denominador y desglose; 409 INDICATOR_NOT_CLOSABLE con su razón (postura, sin escalar, sin
// disponibilidad, confidencial oculta); 400 PERIOD_NOT_ENDED; 409 MEASUREMENT_EXISTS por proyecto y por tenant (Review Focus
// 4); 403 ACTION_REQUIRED para todo el tenant sin leer todos los proyectos; la medición es appendOnly
class IndicatorMeasurementsApiTest : F8IndicatorsApiTest() {
    private fun reason(response: Response): String = tree(response.body)["reason"].asString()

    @Test
    fun `una cifra escalar se cierra con su desglose y queda inmutable`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        commitment(project)
        val social = member("SOCIAL_MANAGER", project)
        val created = closePeriod(indicatorId("KPI-COM-01"), project, start, end, social.token)
        assertEquals(HttpStatus.CREATED, created.status, created.body)
        val attributes = tree(created.body)["attributes"]
        assertEquals(1, attributes["measured_value"].asInt())
        assertEquals(project, attributes["project"].asString())
        assertEquals(social.email, attributes["closed_by_user_email"].asString())
        assertTrue(attributes["breakdown"].asString().startsWith("| workflow_state | Valor |\n|---|---|\n"), attributes["breakdown"].asString())
        val id = tree(created.body)["id"].asString()
        val changed = exchange("PUT", "/api/objects/indicator_measurement/records/$id", mapOf("attributes" to mapOf("measured_value" to 9)))
        assertEquals(HttpStatus.FORBIDDEN, changed.status, changed.body)
    }

    @Test
    fun `409 INDICATOR_NOT_CLOSABLE con su razon`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val cases =
            listOf(
                "KPI-STK-02" to Closability.STANCE,
                "KPI-STK-05" to Closability.STANCE,
                "KPI-TER-01" to Closability.NO_SCALAR_VALUE,
                "KPI-EXE-03" to Closability.NO_SCALAR_VALUE
            )
        for ((code, why) in cases) {
            val response = closePeriod(indicatorId(code), project, start, end)
            assertEquals(HttpStatus.CONFLICT, response.status, "$code: ${response.body}")
            assertEquals("INDICATOR_NOT_CLOSABLE", code(response), code)
            assertEquals(listOf("indicator"), problemFields(response.body), code)
            assertEquals(why, reason(response), code)
        }
        // un indicador propio del cliente, que Kpis no calcula: no hay cifra que congelar
        val own = createRecord("indicator", mapOf("code" to "KPI-ZZZ-${(10..99).random()}", "name" to "Indicador propio de la Asociación Ejemplo"))
        assertEquals(Closability.UNAVAILABLE, reason(closePeriod(own, project, start, end)))
        // SOCIAL_MANAGER no tiene READ_CONFIDENTIAL: KPI-GRV-01 le sale con confidentialHidden (en la práctica lo cierra ADMIN)
        val social = member("SOCIAL_MANAGER", project)
        val hidden = closePeriod(indicatorId("KPI-GRV-01"), project, start, end, social.token)
        assertEquals(HttpStatus.CONFLICT, hidden.status, hidden.body)
        assertEquals(Closability.CONFIDENTIAL_HIDDEN, reason(hidden))
        assertEquals(HttpStatus.CREATED, closePeriod(indicatorId("KPI-GRV-01"), project, start, end).status)
    }

    @Test
    fun `un periodo abierto es 400 PERIOD_NOT_ENDED`() {
        val month = YearMonth.now(LIMA)
        val response = closePeriod(indicatorId("KPI-COM-01"), activeProject(), month.atDay(1), month.atEndOfMonth())
        assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
        assertEquals("PERIOD_NOT_ENDED", code(response))
        assertEquals(listOf("attributes.period_end_date"), problemFields(response.body))
    }

    // Review Focus 4
    @Test
    fun `un periodo se cierra una vez por proyecto y una vez para el tenant`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val indicator = indicatorId("KPI-COM-01")
        assertEquals(HttpStatus.CREATED, closePeriod(indicator, project, start, end).status)
        val again = closePeriod(indicator, project, start, end)
        assertEquals(HttpStatus.CONFLICT, again.status, again.body)
        assertEquals("MEASUREMENT_EXISTS", code(again))
        assertEquals(listOf("attributes.period_start_date"), problemFields(again.body))
        val (oldStart, oldEnd) = pastMonth()
        val tenant = indicatorId("KPI-ENG-01")
        val first = closePeriod(tenant, null, oldStart, oldEnd, key = "cierre-${UUID.randomUUID()}")
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        assertEquals("MEASUREMENT_EXISTS", code(closePeriod(tenant, null, oldStart, oldEnd)))
        val stored =
            get("/api/objects/indicator_measurement/records?indicator=$tenant&size=200")["content"].values().filter {
                it["attributes"]["period_start_date"].asString() == oldStart.toString() && it["attributes"]["project"].let { p -> p == null || p.isNull }
            }
        assertEquals(1, stored.size)
    }

    @Test
    fun `todo el tenant exige leer todos los proyectos y cerrar exige CREATE`() {
        val (start, end) = pastMonth()
        val project = activeProject()
        val social = member("SOCIAL_MANAGER", project)
        val tenant = closePeriod(indicatorId("KPI-ENG-01"), null, start, end, social.token)
        assertEquals(HttpStatus.FORBIDDEN, tenant.status, tenant.body)
        assertEquals("ACTION_REQUIRED", code(tenant))
        assertEquals(listOf("project"), problemFields(tenant.body))
        val reader = closePeriod(indicatorId("KPI-COM-01"), project, start, end, member("READ_ONLY", project).token)
        assertEquals(HttpStatus.FORBIDDEN, reader.status, reader.body)
    }

    @Test
    fun `un cuerpo con una propiedad desconocida o fechas ilegibles es 400`() {
        val indicator = indicatorId("KPI-COM-01")
        val unknown = exchange("POST", "/api/sgspe/indicators/$indicator/measurements", mapOf("attributes" to mapOf("period_start_date" to "2026-01-01", "period_end_date" to "2026-01-31"), "note" to "x"))
        assertEquals(HttpStatus.BAD_REQUEST, unknown.status, unknown.body)
        val bad = closePeriod(indicator, activeProject(), LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-31"))
        assertEquals("OUT_OF_RANGE", code(bad))
    }
}
```

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Esperado: compila (la prueba solo usa HTTP); fallaría por `404` en la integración hasta el Paso 3.

- [ ] **Paso 3: El endpoint**

Crear `src/main/kotlin/sgspe/dashboard/IndicatorMeasurements.kt`:

```kotlin
package sgspe.dashboard

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.LIMA
import sgspe.common.Permissions
import sgspe.common.ProjectReach
import sgspe.common.Records
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.FieldViolation
import wasichai.core.common.GlobalExceptionHandler
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

// 409 INDICATOR_NOT_CLOSABLE (api.md §3.3): errors[0] es indicator/INDICATOR_NOT_CLOSABLE y el problem+json suma el miembro
// `reason` (STANCE, NO_SCALAR_VALUE, UNAVAILABLE o CONFIDENTIAL_HIDDEN), que agrega el controlador
class IndicatorNotClosable(
    val reason: String,
    message: String
) : WasichaiException(HttpStatus.CONFLICT, message, listOf(FieldViolation("indicator", "INDICATOR_NOT_CLOSABLE")))

// el cierre de un periodo (api.md §4.11, dashboards.md §10, ASM-136): congela en indicator_measurement la cifra del KPI
// calculada como quien cierra, con su KpiReader de persona, o ninguna. todo como la persona, con la marca de escritura de
// Records; el candado por indicador, alcance e inicio cubre lo que el único compuesto no ve (dos mediciones del tenant, porque
// los NULL son distintos, WAS-ADR-0037)
@Service
class IndicatorMeasurements(
    private val records: Records,
    private val dashboards: Dashboards,
    private val permissions: Permissions,
    private val reach: ProjectReach,
    private val transactions: Transactions,
    private val callers: Callers,
    private val clock: Clock
) {
    suspend fun close(
        indicatorId: UUID,
        body: Map<String, Any?>
    ): RecordResponse {
        val period = ClosingPeriod.parse(body)
        val user = callers.require()
        permissions.require(user, "Cerrar un periodo", Actions.CREATE to MEASUREMENT)
        // todo el tenant: solo quien lee todos los proyectos (decisión 10 del plan 8.2a: mine == null, sin readsAll)
        if (period.project == null && reach.mine(user) != null) {
            throw Finding(
                "ACTION_REQUIRED",
                "Cerrar un periodo de todo el tenant exige leer todos los proyectos (READ_ALL_PROJECTS sobre project) o ser ADMIN",
                "project",
                status = HttpStatus.FORBIDDEN
            ).toCodedException()
        }
        period.project?.let { records.get("project", it) }
        val code = Values.text(records.get(INDICATOR, indicatorId).attributes["code"]).orEmpty()
        if (!period.end.isBefore(LocalDate.now(clock.withZone(LIMA)))) {
            throw Finding(
                "PERIOD_NOT_ENDED",
                "El periodo termina el ${period.end}: se cierra desde el día siguiente",
                "attributes.period_end_date",
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        Closability.before(code)?.let { throw notClosable(it, code) }
        return transactions.locked("$MEASUREMENT:$indicatorId:${period.project ?: "tenant"}:${period.start}") {
            if (exists(indicatorId, period)) {
                throw Finding(
                    "MEASUREMENT_EXISTS",
                    "El periodo que empieza el ${period.start} ya está cerrado para este indicador y este alcance",
                    "attributes.period_start_date"
                ).toCodedException()
            }
            val value = dashboards.value(code, period.project?.let { listOf(it) }, period.start, period.end, KpiReader.Person(user))
            Closability.after(value)?.let { throw notClosable(it, code) }
            records.create(
                MEASUREMENT,
                mapOf(
                    INDICATOR to indicatorId.toString(),
                    "project" to period.project?.toString(),
                    "period_start_date" to period.start.toString(),
                    "period_end_date" to period.end.toString(),
                    "measured_value" to value.value,
                    "numerator" to value.numerator,
                    "denominator" to value.denominator,
                    "breakdown" to Closability.breakdown(value),
                    "closed_by_user_id" to user.userId.toString(),
                    "closed_by_user_email" to user.email
                ).filterValues { it != null },
                null
            )
        }
    }

    // la medición del mismo indicador, alcance e inicio, leída como quien cierra (la de un proyecto suyo o la del tenant)
    private suspend fun exists(
        indicatorId: UUID,
        period: ClosingPeriod
    ): Boolean =
        records
            .all(MEASUREMENT, mapOf(INDICATOR to indicatorId.toString()), listOf(Criteria.dateBetween("period_start_date", period.start, period.start)))
            .any { Records.uuid(it.attributes["project"]) == period.project }

    private fun notClosable(
        reason: String,
        code: String
    ) = IndicatorNotClosable(reason, "$code no se cierra ($reason): una cifra oficial es completa o no existe (ASM-136)")

    companion object {
        const val INDICATOR = "indicator"
        const val MEASUREMENT = "indicator_measurement"
    }
}

// api.md §4.11: POST /api/sgspe/indicators/{id}/measurements, con Idempotency-Key: un reenvío con la misma clave devuelve el
// 201 de la primera vez sin cerrar otra vez
@RestController
@RequestMapping("/api/sgspe/indicators/{id}/measurements")
class IndicatorMeasurementsController(
    private val measurements: IndicatorMeasurements,
    private val idempotency: Idempotency,
    private val problems: GlobalExceptionHandler
) {
    @PostMapping
    suspend fun close(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            val record = measurements.close(id, body)
            CommandAnswer(HttpStatus.CREATED, record, record.updatedAt, "/api/objects/${IndicatorMeasurements.MEASUREMENT}/records/${record.id}")
        }
    }

    @ExceptionHandler(IndicatorNotClosable::class)
    fun notClosable(e: IndicatorNotClosable): ProblemDetail = problems.handleWasichai(e).apply { setProperty("reason", e.reason) }
}
```

(Si `CommandAnswer.etag` no acepta el tipo de `updatedAt`, se pasa `null`, como hacen los comandos que no exponen
`ETag`.)

- [ ] **Paso 4: Build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.dashboard.IndicatorMeasurementsApiTest`.

```bash
cd $REPO && git add src/main/kotlin/sgspe/dashboard/IndicatorMeasurements.kt src/test/kotlin/sgspe/dashboard/IndicatorMeasurementsApiTest.kt
git commit -m "feat: cierre de periodo con la cifra oficial completa o ninguna" -m "POST /api/sgspe/indicators/{id}/measurements calcula como quien cierra y responde 409
INDICATOR_NOT_CLOSABLE con reason, 400 PERIOD_NOT_ENDED, 409 MEASUREMENT_EXISTS y 403 ACTION_REQUIRED.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t10 -m "Merge branch 'f82a/t10' into feat/fase-8-2a-tableros"
```

---

### Tarea 11: La paridad del tablero y el lector de la plataforma, y la regla de `asPlatform`

**Files:**
- Test: `src/test/kotlin/sgspe/dashboard/DashboardDeliverableParityTest.kt` (nuevo), `src/test/kotlin/sgspe/ArchitectureTest.kt`

**Interfaces:**
- Consumes: `Dashboards.dashboard(name, params, reader)`, `KpiReaders.platform` (Tarea 3); los tableros de las Tareas 5, 6 y
  8; `F8IndicatorsApiTest`.
- Produces: la prueba de RQ-DSH-034 que 8.2b reutiliza al ensamblar, y el vigía de `asPlatform` en `sgspe.dashboard`.

- [ ] **Paso 1: La rama**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t11
```

- [ ] **Paso 2: El vigía, que falla con un ejemplo y pasa con el código**

En `src/test/kotlin/sgspe/ArchitectureTest.kt`, después de `solo sgspe ai usa el modulo de agente y Embabel`:

```kotlin
    // especificación de F8 §2.2.16 (Review Focus 1 del plan 8.2a): en sgspe.dashboard solo KpiReaders lee como la plataforma,
    // y lo hace solo fuera de una petición (KpiReadersTest). un tablero o un endpoint nuevo que llame a asPlatform le daría a
    // una persona la postura, las QCS confidenciales o los proyectos que no tiene
    @Test
    fun `solo KpiReaders lee como la plataforma en sgspe dashboard`() {
        val reader = "/sgspe/dashboard/KpiReader.kt"
        val others = sources(File(main, "sgspe/dashboard")).filterNot { it.invariantSeparatorsPath.endsWith(reader) }
        assertEquals(emptyList<String>(), offenders(others, AS_PLATFORM))
        assertTrue(sources(main).any { it.invariantSeparatorsPath.endsWith(reader) })
        assertTrue(flags(AS_PLATFORM, "records.asPlatform(organization) { block() }"))
        assertFalse(flags(AS_PLATFORM, "// nunca asPlatform dentro de una petición"))
    }
```

y en el `companion object`, junto a las demás expresiones: `private val AS_PLATFORM = Regex("""\basPlatform\s*[({]""")`.

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ArchitectureTest'
```

Esperado: `BUILD SUCCESSFUL` (el vigía ya ve el código de la Tarea 3; para verlo fallar, agregar a mano
`records.asPlatform(x) { }` en `Dashboards.kt`, correr, ver la línea en el mensaje y quitarla).

- [ ] **Paso 3: La paridad**

Crear `src/test/kotlin/sgspe/dashboard/DashboardDeliverableParityTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.LinkedMultiValueMap
import sgspe.F8IndicatorsApiTest
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

// RQ-DSH-034 (especificación de F8 §3.1 y §5): las cifras de un ADMIN por el endpoint son las del lector de la plataforma, que
// usará el ensamblado de entregables (8.2b), en los tableros consolidados y en los de módulo, con la postura, una queja, un
// compromiso, una interacción, un riesgo, una alerta y una medición cerrada. el lector de la plataforma se pide fuera de una
// petición, como lo hará JOB-05
class DashboardDeliverableParityTest : F8IndicatorsApiTest() {
    @Autowired
    private lateinit var dashboards: Dashboards

    @Autowired
    private lateinit var readers: KpiReaders

    private fun withoutClock(node: JsonNode): JsonNode = (node.deepCopy() as ObjectNode).also { it.remove("computedAt") }

    @Test
    fun `el endpoint del ADMIN y el lector de la plataforma dan las mismas cifras`() {
        val (start, end) = lastMonth()
        val project = activeProject()
        val officer = member("COMMUNITY_RELATIONS_OFFICER", project)
        val held = start.plusDays(5).toString() + "T15:00:00Z"
        engagement(project, "REUNION", officer.token, mapOf("held_at" to held)).also {
            participant(it, officer.token)
            move("engagement", it, "register_held_unplanned", officer.token)
        }
        stakeholder(project)
        commitment(project)
        grievance(project)
        socialRisk(project)
        earlyWarning(project)
        assertEquals(201, closePeriod(indicatorId("KPI-COM-01"), project, start, end).status.value())
        val query = mapOf("project" to listOf(project), "periodStart" to listOf(start.toString()), "periodEnd" to listOf(end.toString()))
        for (name in listOf(Kpis.EXECUTIVE, Kpis.PROJECT, Kpis.MONITORING, Kpis.STAKEHOLDERS, Kpis.ATTENTION, Kpis.RISKS, Kpis.COMMITMENTS)) {
            val endpoint = get("/api/sgspe/dashboards/$name?project=$project&periodStart=$start&periodEnd=$end")
            val platform =
                runBlocking {
                    val params = LinkedMultiValueMap<String, String>().apply { query.forEach { (k, v) -> addAll(k, v) } }
                    dashboards.dashboard(name, params, readers.platform(organizationId()))
                }
            assertEquals(withoutClock(endpoint), withoutClock(JSON.valueToTree(platform)), name)
        }
    }
}
```

(`socialRisk(project)` es la ayuda de `F4ApiTest`; si pide más argumentos, se pasan como en `RisksDashboardApiTest`.)

- [ ] **Paso 4: Build y commit**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`. Integración pendiente: `sgspe.dashboard.DashboardDeliverableParityTest`.

```bash
cd $REPO && git add src/test/kotlin/sgspe/dashboard/DashboardDeliverableParityTest.kt src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -m "test: paridad entre el tablero y el lector de la plataforma" -m "RQ-DSH-034: un ADMIN por el endpoint y KpiReader.Platform dan las mismas cifras en siete tableros;
ArchitectureTest deja asPlatform de sgspe.dashboard solo a KpiReaders.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t11 -m "Merge branch 'f82a/t11' into feat/fase-8-2a-tableros"
```

---

### Tarea 12: Los indicadores y los tableros consolidados en los documentos

**Files:**
- Modify: `docs/api.md` (§4.11), `docs/dashboards.md` (§5.1, §5.5, §10), `docs/database-model.md` (§19, notas de
  `indicator_measurement`), `docs/roadmap.md` (F8, «Estado»), `docs/requirements-traceability.md`, `CLAUDE.md`,
  `docs/develop/README.md`

**Interfaces:**
- Consumes: los contratos finales de las Tareas 1 a 11.
- Produces: los documentos al día para 8.2b, 8.2c y 8.3.

- [ ] **Paso 1: La rama y una comprobación que falla**

```bash
cd $REPO && git switch feat/fase-8-2a-tableros && git switch -c f82a/t12
python3 - <<'PY'
def norm(path): return ' '.join(open(path, encoding='utf-8').read().split())
api, dash, dbm, claude, dev = (norm(p) for p in ('docs/api.md', 'docs/dashboards.md', 'docs/database-model.md', 'CLAUDE.md', 'docs/develop/README.md'))
need = [(api, 'rows[]'), (api, '{ project, code, name, indicators[] }'), (api, 'targetSource'),
        (dash, 'decisión 3 del plan 8.2a'), (dash, 'KpiReaders'), (dbm, 'NO_SCALAR_VALUE'), (claude, '86 objetos'),
        (dev, '86 objetos')]
missing = [n for d, n in need if n not in d]
print('\n'.join(missing) or 'ok'); raise SystemExit(1 if missing else 0)
PY
```

Esperado: falla y lista lo que falta (`rows[]`, `{ project, code, name, indicators[] }`, `decisión 3 del plan 8.2a`,
`KpiReaders`, `NO_SCALAR_VALUE` en `database-model.md`, `86 objetos`).

- [ ] **Paso 2: Los textos**

- `docs/api.md` §4.11, bullet «Los tableros de F8»: `executive` suma «`byProject[]` es `[{ project, code, name,
  indicators[] }]`, con los 8 KPI con valor de las tiles, sin desgloses ni series»; `monitoring` suma «la respuesta trae
  `indicators[]` (los 13 KPI en el orden de §4.9, con sus desgloses) y `rows[]` en el mismo orden, cada una `{ number,
  code, label, verificationMeans, deliverable, available, value, target, targetSource, official }`; la cifra oficial sale
  solo sin filtros fuera de `project` y el periodo, con el periodo exacto de la medición y un solo `project` (o ninguno,
  para la del tenant, a quien lee todos los proyectos)». En la fila de `POST /api/sgspe/indicators/{id}/measurements`,
  agregar al final de la descripción: «Un indicador que SGSPE no calcula (uno propio del cliente) es `UNAVAILABLE`; un
  periodo con inicio posterior al fin, `400 OUT_OF_RANGE`; una fecha ilegible, `400 INVALID_VALUE`.»
- `docs/dashboards.md` §5.5: después del bullet de `targetSource`, «La meta de #5 es el denominador de KPI-ENG-02 si un
  proyecto del filtro o un plan define `meetings_per_month_target` (`PROJECT`); si no, `target_value` × meses que toca el
  periodo × proyectos (`CATALOG`); si no, ninguna. Las demás filas usan `target_value` tal cual (decisión 3 del plan
  8.2a).» §10, bullet «Cierre de periodo»: «…con su `KpiReader` (`KpiReaders` arma el contexto de la persona o de la
  plataforma, que solo se usa fuera de una petición)…». §6 punto 6: «El trabajador de entregables corre como la
  plataforma con `KpiReader.Platform` [WAS-ADR-0039]…».
- `docs/database-model.md` §19, ficha de `indicator_measurement`, último bullet: «El endpoint de cierre responde `409
  INDICATOR_NOT_CLOSABLE` con su razón (`dashboards.md` §10, ASM-136): `STANCE` (KPI-STK-02, KPI-STK-05 y KPI-STK-06),
  `NO_SCALAR_VALUE` (KPI-TER-01, KPI-EXE-03 y los que salen sin valor), `UNAVAILABLE` y `CONFIDENTIAL_HIDDEN`.» (Es un
  bullet de notas: `design_docs.py` no lo lee; la suite del modelo sigue igual.)
- `docs/roadmap.md` F8 «Estado»: «Plan 8.2a integrado (PR #…): catálogo `indicator` con las metas de DOC-01 y
  `load_indicators.py`, `KpiReader`, KPI-EXE, tableros `executive`, `project` y `monitoring` y cierre de periodo; la
  medición del tablero `executive` en `sgspe_perf`, pendiente del coordinador (Tarea 13).»
- `docs/requirements-traceability.md`: las filas de RQ-DSH-002, RQ-DSH-003, RQ-DSH-034, RQ-DSH-037, RF-DSH-001,
  RF-DSH-002 y RF-DSH-008 suman sus pruebas (`ExecutiveDashboardApiTest`, `ExecutiveKpisTest`, `ProjectDashboardApiTest`,
  `MonitoringDashboardApiTest`, `MonitoringTableTest`, `DashboardDeliverableParityTest`, `IndicatorMeasurementsApiTest`,
  `ClosabilityTest`, `test_load_indicators.py`) y el módulo `sgspe.dashboard`; ninguna fila cambia de estado.
- `CLAUDE.md`: «`model.json` (los 84 objetos de F2 a F7)» pasa a «`model.json` (los 84 objetos de F2 a F7 y, desde el
  plan 8.2a, `indicator` e `indicator_measurement`: 86)», y suma «`indicators.json` y `load_indicators.py`» a la lista de
  scripts; «Dónde está cada cosa» suma la fila «Base de las pruebas de integración del plan 8.2a (catálogo cargado,
  proyecto activo, cierre, mes pasado y meses viejos al azar) | `src/test/kotlin/sgspe/F8IndicatorsApiTest.kt`» y, en la
  fila de F8 de indicadores, `KpiReaders`, `Closability` y `DashboardsProperties`.
- `docs/develop/README.md`: «15 roles por 84 objetos» pasa a «15 roles por 86 objetos», y el orden de aprovisionamiento
  nombra `load_indicators.py` después de `load_ubigeo.py`.

- [ ] **Paso 3: Las compuertas y commit**

```bash
cd $REPO && python3 - <<'PY'
def norm(path): return ' '.join(open(path, encoding='utf-8').read().split())
api, dash, dbm, claude, dev = (norm(p) for p in ('docs/api.md', 'docs/dashboards.md', 'docs/database-model.md', 'CLAUDE.md', 'docs/develop/README.md'))
need = [(api, 'rows[]'), (api, '{ project, code, name, indicators[] }'), (api, 'targetSource'), (dash, 'decisión 3 del plan 8.2a'),
        (dash, 'KpiReaders'), (dbm, 'NO_SCALAR_VALUE'), (claude, '86'), (dev, '86 objetos')]
missing = [n for d, n in need if n not in d]
print('\n'.join(missing) or 'ok'); raise SystemExit(1 if missing else 0)
PY
cd $REPO/model && python3 -m unittest 2>&1 | tail -1 && cd $REPO && python3 tools/check_traceability.py && yarn -s format:check
```

Esperado: `ok`, `OK`, `ok: 298 requisitos, todos con su fila` y `All matched files use Prettier code style!`.

```bash
cd $REPO && git add docs/api.md docs/dashboards.md docs/database-model.md docs/roadmap.md docs/requirements-traceability.md \
  CLAUDE.md docs/develop/README.md
git commit -m "docs: indicadores y tableros consolidados de 8.2a en los documentos" -m "Contratos de executive y monitoring, la meta de #5, KpiReaders, las razones del cierre en la ficha,
la trazabilidad y las cifras de 86 objetos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM"
git switch feat/fase-8-2a-tableros && git merge --no-ff f82a/t12 -m "Merge branch 'f82a/t12' into feat/fase-8-2a-tableros"
```

---

### Tarea 13: Integración, medición, push y PR (coordinador)

**Files:** ninguno nuevo (un commit de `docs/roadmap.md` con la medición, si se hace).

- [ ] **Paso 1: Verificación completa**

```bash
cd $REPO && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
cd $REPO/model && python3 -m unittest 2>&1 | tail -3 && python3 apply.py --validate-only && python3 load_indicators.py --validate-only
cd $REPO && python3 tools/check_traceability.py && yarn -s format:check && git status --short
```

Esperado: `BUILD SUCCESSFUL`, `OK`, sin salida de los validadores, la trazabilidad en verde, prettier en verde y el árbol
limpio (salvo `.playwright-mcp/`, que no se versiona).

- [ ] **Paso 2: Integración (coordinador, túnel 5435 y `sgspe_sdd_test` libre)**

Una clase tras otra con el comando de «Comandos», empezando por la que aprovisiona y carga el catálogo:
`sgspe.contract.TenantProvisioningApiTest`, `sgspe.dashboard.IndicatorModelApiTest`,
`sgspe.dashboard.ExecutiveDashboardApiTest`, `sgspe.dashboard.ProjectDashboardApiTest`,
`sgspe.dashboard.MonitoringDashboardApiTest`, `sgspe.dashboard.IndicatorMeasurementsApiTest`,
`sgspe.dashboard.DashboardDeliverableParityTest` y, por regresión, `sgspe.dashboard.*ApiTest`, `sgspe.ai.AiRunsApiTest`,
`sgspe.security.PermissionMatrixApiTest` (86 objetos) y `sgspe.workflow.WorkflowMatrixApiTest`. Una falla vuelve a la
tarea dueña con la clase y el mensaje; no se arregla en esta rama sin su tarea.

- [ ] **Paso 3: La medición del tablero `executive` (coordinador, con permiso del usuario para `sgspe_perf`)**

Con el usuario de acuerdo: aprovisionar `sgspe_perf` hasta `load_indicators.py`, sembrar 10 proyectos activos con el
volumen de la medición de F6 (interacciones, compromisos, QCS, riesgos y aportes ficticios) y pedir 50 veces
`GET /api/sgspe/dashboards/executive?project=…×10&periodStart=…&periodEnd=…` como `ADMIN`; criterio: p95 bajo 3 s
(especificación de F8 §7). El resultado (p50, p95, máximo, fecha) va al cuerpo del PR y a `docs/roadmap.md` F8 «Estado»
en un commit `docs: medición del tablero executive en sgspe_perf`. Si no se aprueba tocar `sgspe_perf`, el PR lo dice y
queda para la tarea del coordinador de la fase.

- [ ] **Paso 4: Push y PR**

```bash
cd $REPO && git push -u origin feat/fase-8-2a-tableros
gh pr create --base main --title "feat: plan 8.2a, indicadores y tableros consolidados" --body "$(cat <<'BODY'
## Qué trae

- `indicator` e `indicator_measurement` en el modelo con sus permisos (security.md §7.8) y su clasificación; el catálogo
  `model/indicators.json` (53 KPI) con las metas de DOC-01 y `load_indicators.py` en el aprovisionamiento.
- `KpiReader` (persona o plataforma), `SpatialGate.selectAsPlatform`, KPI-EXE-01 a 03 y los tableros `executive`
  (con `byProject[]` y su tope), `project` y `monitoring` (Tabla 9.5 con meta, origen y cifra oficial).
- `POST /api/sgspe/indicators/{id}/measurements`: una cifra completa o ninguna (ASM-136).
- Paridad entre el tablero y el lector de la plataforma (RQ-DSH-034).

## Pruebas

Python (`Ran 322 tests`, OK), unitarias y build en verde; integración: la lista de la Tarea 13. Medición: (resultado o
pendiente).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
BODY
)"
```

Esperado: el PR abierto y la CI en verde; se mezcla según el flujo autorizado del proyecto.

---

## Qué trae

El catálogo `indicator` con sus metas editables y su carga idempotente, el cierre opcional de periodos con cifras
oficiales completas o ninguna, el lector explícito de los KPI que 8.2b usará como la plataforma, KPI-EXE y los tres
tableros consolidados de F8, con la prueba de paridad de RQ-DSH-034.

## Pruebas

| Clase | Tarea | Tipo |
|---|---|---|
| `test_model.IndicatorModelTests`, `test_apply` (F7 → 8.2a), `test_apply_roles` (F7 → 8.2a), `test_load_indicators` | 1 | Python |
| `ProjectScopeF8Test`, `TenantProvisioningApiTest` | 1 | Unitaria e integración |
| `IndicatorWriteGuardTest`, `IndicatorModelApiTest` | 2 | Unitaria e integración |
| `KpiReadersTest`, `SpatialGateTest` | 3 | Unitarias |
| `ExecutiveKpisTest` | 4 | Unitaria |
| `DashboardsPropertiesTest`, `ExecutiveDashboardApiTest` | 5 | Unitaria e integración |
| `ProjectDashboardApiTest` | 6 | Integración |
| `MonitoringTableTest` | 7 | Unitaria |
| `MonitoringDashboardApiTest` | 8 | Integración |
| `ClosabilityTest` | 9 | Unitaria |
| `IndicatorMeasurementsApiTest` | 10 | Integración |
| `DashboardDeliverableParityTest`, `ArchitectureTest` | 11 | Integración y escaneo |

## Cobertura de la especificación

| Especificación | Tarea |
|---|---|
| §2.2.1, §4.1 (modelo, roles, `indicators.json`, `load_indicators.py`, orden de aprovisionamiento) | 1 |
| §3.1 «Catálogo» (`IndicatorWriteGuard`, metas y frecuencias de DOC-01, nunca sobrescribir) | 1, 2 |
| §3.1 «Metas» (`targetSource`) | 7, 8 |
| §3.1 «KPI-EXE» | 4 |
| §3.1 «Tableros» (`executive`, `project`, `monitoring`, catálogo de indicadores) | 5, 6, 8 |
| §3.1 «Cierre», §2.2.15 | 9, 10 |
| §3.1 «Lector», §2.2.16 (`KpiReader`, `SpatialGate`, tope de 25) | 3, 5, 11 |
| §5 (bases y clases de 8.2a) | 2, 5 a 11 |
| §7 (fila 8.2a: medición, PR) | 13 |
| Restricciones globales (asPlatform, `TenantModel`, datos ficticios) | 3, 8, 11 y todas |
