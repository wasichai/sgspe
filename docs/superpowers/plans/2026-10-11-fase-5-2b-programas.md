# SGSPE · Fase 5, plan 5.2b (backend: programas, tablero y medición): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Escribir el código propio de SGSPE para los programas sociales de la Fase 5 sobre el modelo y el alcance del plan
5.1 y las piezas comunes del plan 5.2a: las reglas de WF-G (G1 a G4) y de los hijos de `social_program` dentro de
`InvestmentWriteGuard`, `ProgramTotalsListener` y su recalculador de JOB-02 para `social_program.total_budget`, el aviso
`approval_pending` de WF-G, los indicadores KPI-INV-01 a 04 con el tablero `investment`, los casos de F5 de
`ProjectReadScopeApiTest`, la columna «otro proyecto» de los hijos de BC-10 en `PermissionMatrixApiTest`,
`SocialProgramWorkflowApiTest` e `InvestmentDashboardApiTest`, y la medición de las subconsultas anidadas del alcance con el
tenant de desarrollo en F5.

**Architecture:** `sgspe.investment` gana `Programs` (nombres y reglas puras de WF-G y de los hijos), dos `InvestmentRule`
que `InvestmentWriteGuard` (plan 5.2a) junta como `CommitmentWriteGuard` junta sus `CommitmentRule` (`ProgramApprovalRule`:
G1 a G4; `ProgramDataRule`: fechas, año, conteos y «mismo programa») y `ProgramTotals` (función pura, escritor, listener y
recalculador). `sgspe.notification` suma WF-G a `ApprovalNoticeListener.STEPS`; `sgspe.deadline` suma `social_program` a
`DeadlineNoticePlan.TASK_REFERENCE`. `sgspe.dashboard` gana los filtros `program` y `year` (solo en el tablero `investment` y
en los KPI-INV), las lecturas de inversión de `DashboardData` y `InvestmentKpis` (funciones puras). Todo escribe por
`sgspe.common.Records` con la marca `SgspeWrite`; las reglas son puras o leen como quien escribe y
`POST /api/sgspe/transition-checks` las corre en seco; solo JOB-02 corre como la plataforma, con su motivo.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 1.0.0 (sin
starters nuevos), R2DBC, Jackson 3 (`tools.jackson`), JUnit 5 y Mockito con `wasichai-test` (`WasichaiIntegrationTest`);
PostgreSQL 18 con PostGIS por el túnel para la medición (solo el coordinador).

**Spec:** `docs/superpowers/specs/2026-10-11-fase-5-design.md` (vinculante): restricciones globales; §2.1 decisión 4; §2.2
(sinergia del programa); §2.3 elecciones 1 a 3 (las cadenas y los padres alternativos que mide este plan), 9, 11 (conteos y
año), 12 (`ProgramTotalsListener`), 14 (avisos de WF-G) y 16; §3.1 (bajas de los hijos del programa); §3.2 (las pruebas de
`ProjectReadScopeApiTest`); §3.6 (WF-G, G1 a G4, reglas de los hijos y totales); §3.8 (aviso de programa); §3.9 (KPI-INV y
tablero `investment`); §5 (filas de `SocialProgramWorkflowApiTest`, `InvestmentDashboardApiTest`, `ProjectReadScopeApiTest`
y matrices); §7 (fila 5.2b, medición y definición de terminado); §8 (consecuencia 6 y riesgos 1, 2 y 4). Los documentos como
los dejan los planes 5.0 (`api.md`, `workflows.md` WF-G, `dashboards.md` §3.2, §4.7 y §5.10, `requirements-analysis.md` con
ASM-110 a ASM-119), 5.1 (modelo, roles, alcance de cadenas y padres alternativos, `F5ApiTest`, `WorkflowMatrixApiTest` con
WF-G y WF-P) y 5.2a (`InvestmentWriteGuard`, `CampaignCommitted`, avisos de WF-P, entrega de aportes). Lo que la
especificación asigna a 5.2a (aportes, tope, lineamientos, entrega, solicitudes, avisos de WF-P, `AmountFieldsGuard`,
`ProjectLinkGuard`) y a 5.3 (portal) queda fuera.

## Global Constraints

- **Repositorio y rama.** Todo el trabajo en `~/IdeaProjects/sgspe`, en la rama `feat/fase-5-2b-programas` creada desde
  `main` con los planes 5.0, 5.1 y 5.2a integrados; ramas de tarea `f52b/t<k>` integradas con `--no-ff`, como en F4. El paso
  1 de la Tarea 1 lo comprueba.
- **Versiones.** wasichai `1.0.0` exacto (desde wasichai/sgspe#30), Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle
  `9.8.0`, ktlint `1.7.1`. Ningún starter nuevo.
- **Idioma.** Español (es-PE) en documentos, comentarios y `detail`; nombres técnicos en inglés (ADR-0009); constantes de la
  API en inglés UPPER_SNAKE; propiedades del cuerpo en camelCase; atributos con su nombre del modelo en snake_case; valores
  ENUM en español MAYÚSCULAS sin tildes, exactamente como el modelo (`VENCIDO`, `DONACION`, `ACTA DE RECEPCION`).
- **Arquitectura.** Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService`
  ni `ObjectSchemaManager`; ningún literal de rol en `src/main/kotlin` (los roles salen de `model/workflows/*.json` y de
  `model/roles.json` por `ModelCatalog`); `scope` y `privacy` solo dependen de `common`; los tenants solo con
  `Organizations`; ningún bean de estereotipo con el nombre simple de otro (`ArchitectureTest`). Ningún import sin uso:
  ktlint de este repositorio no los quita.
- **Lecturas y escrituras.** Nunca `asPlatform` dentro de una petición: guardas, listeners y tableros leen y escriben como
  quien llama; solo JOB-02 (`ProgramTotalsRecalculator`) corre como la plataforma en este plan, con su motivo
  (`DerivedFieldsJob.reason`). Toda condición SQL del alcance con valores enlazados y nombres de tabla y columna de la
  definición, entrecomillados.
- **Tenants en F4.** Un tenant que todavía no tiene el modelo de F5 sigue con F4: este plan solo lee objetos de BC-10 desde
  código que únicamente se dispara con ellos (reglas y listeners sobre `social_program` y sus hijos, recalculador con
  `sourceObject = social_program`, KPI-INV que piden leer campos de BC-10 y salen no disponibles sin ellos).
- **Errores.** Todo 400, 403 o 409 de una regla lleva su código en `errors[0].message` y el campo en `errors[0].field`
  (`Finding.toCodedException`). Los códigos, los de `api.md` §3.3 tras el plan 5.0: `ACTION_REQUIRED`,
  `SEGREGATION_OF_DUTIES`, `SYNERGY_AGREEMENT_REQUIRED`, `INTERNAL_APPROVAL_REQUIRED`, `PROGRAM_MISMATCH`, `OUT_OF_RANGE`,
  `OBJECT_NOT_READABLE` y `REASON_REQUIRED` en sus casos de WF-G.
- **Lecturas acotadas.** `Records.all(max)` con `400 TOO_MANY_RECORDS`, nunca un recorte silencioso (los tableros, con
  `DashboardData.MAX_ROWS`); un parámetro de consulta desconocido es `422` (`StrictQuery`).
- **Datos.** Ficticios: correos `@sgspe.test`, «Proyecto Ejemplo A», «Proyecto Ejemplo B», «Asociación Ejemplo»,
  «Programa Ejemplo», «Persona Ejemplo»; ningún nombre real de persona en nada versionado.
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde
  `~/IdeaProjects/sgspe`; `./gradlew ktlintFormat` antes de `build`; `yarn format` antes de commitear yaml o json. El token
  nunca va a un archivo ni a la salida. Con poca memoria, `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Integración.** Las clases `*ApiTest` necesitan PostgreSQL (Testcontainers o el túnel): **corren en la CI del PR** y, si se
  quiere antes, las corre el coordinador, nunca el implementador ni un subagente. El implementador compila
  (`compileTestKotlin` o `build`, que compila también las de integración) y entrega la tarea marcada «integración pendiente»
  con las clases de su último paso. Con el túnel abierto:
  `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'`.
  La primera clase de una JVM aprovisiona el modelo de F5 (diez objetos más que F4: más de 10 minutos por el túnel); conviene
  correr juntas las clases de una tarea. `sgspe_sdd_test` no la usan dos suites a la vez.
- **Base, túnel, Docker y tenant de desarrollo.** Solo el coordinador principal los toca (Tarea 13). Aprovisionamiento
  siempre en el orden `apply.py` → `apply_roles.py` → `apply.py --workflows` → `load_ubigeo.py`.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres); al
  final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas. Push y PR
  en la Tarea 14.

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

1. **Cambiar la moneda de un programa que ya tiene presupuestos.** Si `total_budget` solo se recalculara con los
   presupuestos, seguiría sumando la moneda vieja hasta la vuelta de JOB-02, y S43 mostraría un total en PEN de un programa en
   USD. Pruebas: `ProgramTotalsListenerTest` «cambiar la moneda del programa lo recalcula y cambiar su nombre no» (Tarea 4) y
   `SocialProgramWorkflowApiTest` «total_budget solo suma la moneda del programa y sigue su cambio de moneda» (Tarea 9).
2. **Un presupuesto que se pasa de un programa a otro.** El total del programa de origen quedaría con un presupuesto que ya no
   tiene. Prueba: `ProgramTotalsListenerTest` «mover un presupuesto recalcula el programa de origen y el de destino»
   (Tarea 4).
3. **Quien aprueba no lee las partes del acuerdo.** G3 contaría cero partes o, peor, dejaría pasar sin mirar; tiene que
   rechazar con `403 OBJECT_NOT_READABLE`. Pruebas: `ProgramApprovalRuleTest` «lo que G3 no puede leer es NotReadable, nunca
   un pase» (Tarea 2).
4. **Un grupo de beneficiarios que cambia de programa y conserva su iniciativa.** Mirar solo el cambio de
   `social_initiative` dejaría al grupo con la iniciativa de otro programa. Prueba: `ProgramDataRuleTest` «cambiar el programa
   de un grupo también se juzga contra su iniciativa» (Tarea 3).
5. **`program` o `year` pedidos en un tablero que no los aplica.** Aceptarlos en silencio daría cifras que parecen
   filtradas; un año fuera de rango también. Pruebas: `DashboardFiltersTest` «program y year solo los acepta quien los permite
   y viajan en el eco» y «year es un año entero de 2000 a 2100 y program un UUID» (Tarea 6) e `InvestmentDashboardApiTest`
   «program y year en otro tablero son 422 y un año fuera de rango es 400» (Tarea 10).

---

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | §3.9 de la especificación nombra el filtro de campaña `campaign`, pero el filtro común de los tableros desde F3 es `explorationCampaign` (`DashboardFilters.COMMON`, `dashboards.md` §3.1: «Solo en compromisos e inversión social») | Los KPI-INV usan `explorationCampaign`: un segundo nombre para el mismo filtro duplicaría la validación y el eco. El plan 5.0 ya lo escribe así en `dashboards.md` §3.1 y `api.md` §4.11 (la Tarea 14 lo comprueba), la especificación §3.9 se corrigió y el plan 5.3 lo consume |
| 2 | §3.9 suma `program` y `year` a los filtros; si fueran comunes, los tableros de F2 a F4 los recibirían sin aplicarlos | `DashboardFilters.INVESTMENT = {program, year}`: los acepta solo el tablero `investment` y `GET /api/sgspe/dashboards/indicators/KPI-INV-*` (`Kpis.extraFilters`); en otro tablero o KPI son `422`. `year` es un entero de 2000 a 2100 (`400` si no), como `budget_year`. Cada KPI-INV declara en `ignores` lo que no aplica (Tareas 6 y 8) |
| 3 | §3.9 da la forma de cada KPI-INV pero no su cifra titular (`value`) | La cifra titular sigue `dashboards.md` §3.3 («la consolidación usa PEN por defecto»): KPI-INV-01, el ejecutado sobre el tope de las campañas en PEN (%); KPI-INV-02, el ejecutado sobre el asignado de los presupuestos en PEN (%); KPI-INV-03, el ejecutado en PEN (monto); KPI-INV-04, los alcanzados de los grupos de beneficiarios (personas). Lo demás va en desgloses por campaña, programa, fuente y proyecto, con su moneda; lo que no es PEN se advierte (Tarea 7) |
| 4 | §3.9 pide lo comprometido de KPI-INV-01 «con `CampaignCommitted`», pero ese lector (plan 5.2a) lee una campaña a la vez dentro de su candado y excluye el registro que se aprueba | KPI-INV-01 lee los compromisos y los aportes de todas las campañas del tablero de una vez (`DashboardData.committedOf`) con las mismas definiciones de «comprometido» que `CampaignCommitted`: `Commitments.COMMITTED` y `Contributions.COMMITTED` (plan 5.2a). Una prueba unitaria fija que el tablero usa esas constantes (Tarea 8) |
| 5 | KPI-INV-03 acepta territorio (`dashboards.md` §4.7), pero `program_budget` no tiene territorio | Con `locality`, los presupuestos de programas no se suman y una advertencia lo dice; los compromisos y aportes se filtran por su localidad (con sectores). Los KPI-INV aplican solo `locality` de los filtros de territorio (§3.9) y declaran `community`, `administrativeUnit`, `areaOfInfluence` y `scope` en `ignoredFilters` (Tareas 7 y 8) |
| 6 | §3.6 dice que `ProgramTotalsListener` corre con el alta, el cambio o la baja de `program_budget`; la moneda del programa también mueve el total | El listener corre también con el alta de un programa y con el cambio de su `currency` (Review Focus 1) y, si un presupuesto cambia de programa, recalcula los dos. Un programa con moneda y sin presupuestos en ella tiene `total_budget` 0; sin moneda, vacío. La escritura es solo de derivados (`Records.patch`, con su salto en `derived_version`), así que el `If-Match` de quien editaba el programa sigue valiendo (ADR-0027) (Tarea 4) |
| 7 | La especificación pone las reglas de WF-G «en `InvestmentWriteGuard`», archivo que crea el plan 5.2a | Las reglas de programas son `InvestmentRule` en archivos propios (`ProgramApprovalRule`, `ProgramDataRule`) que `InvestmentWriteGuard` junta por inyección, como `CommitmentWriteGuard` junta sus `CommitmentRule`: 5.2b no edita el archivo de la guarda salvo, si hiciera falta, su lista `OBJECTS` (Tarea 2, «Dependencias de otros planes») |
| 8 | G1 (`ACTION_REQUIRED`) no se alcanza por la API con los roles de hoy: los dos roles de `approve` de WF-G son `APPROVER`, que tiene `APPROVE`, y nadie más | La prueba de integración lo comprueba con la consulta en seco (`POST /api/sgspe/transition-checks`), que corre las reglas sin mirar los roles, como la decisión 12 del plan 4.2b; la regla tiene su prueba unitaria (Tareas 2 y 9) |
| 9 | §3.1 dice que «un indicador con mediciones responde `409 HAS_DEPENDENTS`» | `program_measurement` es `appendOnly`: la plataforma rechaza antes la baja del padre (`AppendOnlyReferences`, `409` sin código) y `DependentsDeleteGuard` no cuenta esas relaciones (P57, `DependentsDeleteGuardTest`). La prueba exige el `409` y que el indicador siga; el código del cuerpo es el de wasichai. El plan 5.0 lo dice en `api.md` §3.3 (la Tarea 14 lo comprueba) y el plan 5.3 trata ese `409` sin depender de un código |
| 10 | §3.2 pide en `ProjectReadScopeApiTest` que «`PROJECT_ADMIN` lee todas» las entregas | `PROJECT_ADMIN` no tiene READ sobre `delivery` en `roles.json` (§7.5 de `security.md`). La prueba usa `AUDITOR`, que tiene `*` READ y `READ_ALL_PROJECTS` (decisión del coordinador: la especificación §3.2 y el plan 5.1 ya lo dicen así; `PROJECT_ADMIN` no gana READ de `delivery`) (Tarea 11) |
| 11 | `workflows.md` («Plazos y escalamiento») escala una tarea vinculada a un programa a los roles de `close` de WF-G, y `task.social_program` llega con 5.1 y 5.2a, pero `DeadlineNoticePlan.TASK_REFERENCE` no la tiene | `TASK_REFERENCE` suma `social_program → close`: sin ella, el nivel 1 de una tarea vencida de un programa no llega a nadie (Tarea 5) |
| 12 | Las pruebas de este plan necesitan acuerdos vigentes con partes y aportes de monto y moneda conocidos llevados hasta entregados por el endpoint; `F5ApiTest` (plan 5.1) ya trae los programas y sus hijos, la campaña y un `activeAgreement` sin partes | Una base propia, `F5ProgramApiTest` sobre `F5ApiTest`, solo con lo que falta y con nombres que no chocan con los de 5.1 (`activeAgreementWith`, `registerContribution`, `registerApprovedContribution`, `registerDeliveredContribution`); los programas usan las ayudas de 5.1 (Tarea 9) |
| 13 | `F2ApiTest.sample` da a un `INTEGER` de un único el número de secuencia (`n`), y `program_budget` tiene el único (`social_program`, `budget_year`): con la regla del año, el alta mínima de la matriz de permisos sería `400` | El plan 5.1 ya fija `budget_year` en 2026 en `F2ApiTest.FIXED` (su decisión 10); la Tarea 3 lo comprueba. Cada alta mínima crea su propio programa, así que el único no choca |
| 14 | §3.6 pide `PROGRAM_MISMATCH` «sobre `social_initiative`» y no dice cuándo | En el alta y cuando la escritura cambia `social_initiative` o `social_program` del grupo (Review Focus 4); la iniciativa se lee como quien escribe y una que no se lee es `403 OBJECT_NOT_READABLE` (Tarea 3) |
| 15 | Una entrega huérfana (sin compromiso ni aporte) no se puede crear: `ExactlyOneGuard` la rechaza | La prueba la produce como la produce la base: borra el compromiso en borrador de una entrega (`ON DELETE SET NULL` de wasichai; `delivery` no es `requiresReason`, así que `DependentsDeleteGuard` no vigila esa relación) (Tarea 11) |
| 16 | §7 pide, si la disyunción de `delivery` no usa los índices, reescribirla con `UNION ALL` | Lo decide la medición (Tarea 13): con la cifra en la mano, la reescritura es un paso condicional con su prueba y su código; si no hace falta, el PR lo dice y el roadmap anota las cifras |

## Lo que este plan toma de los planes 5.0, 5.1 y 5.2a

- **Documentos (5.0).** Los códigos de `api.md` §3.3; WF-G con G1 a G4 en `workflows.md`; `dashboards.md` §3.2 (conjunto
  «comprometido» y «ejecutado» de `social_contribution`), §4.7 (KPI-INV-01 con lo comprometido, KPI-INV-04 en tres bloques) y
  §5.10; ASM-113 (ejecución manual por año), ASM-114 (cadenas y padres alternativos) y ASM-115 (sinergia del programa).
- **Modelo y alcance (5.1).** Los diez objetos de BC-10 con `program_measurement` `appendOnly` y su único (`program_indicator`,
  `period_start_date`), `program_budget` con su único (`social_program`, `budget_year`), `social_program.total_budget`
  derivado y la acción `APPROVE`; `model/workflows/social_program.json` (WF-G) y `social_contribution.json` (WF-P);
  `roles.json` con `SOCIAL_INVESTMENT_MANAGER`; `ProjectScope.CHILDREN: Map<String, List<Pair<String, String>>>` con las
  cadenas y los padres alternativos, `ProjectReadCondition`, `ProjectReadScope`, `ProjectResolver` y
  `ProjectLinks.linkedProject` con ellos; `ArchitectureTest` (§3.2); `F5ApiTest` (sobre `F4ApiTest`) con sus ayudas de
  BC-10; `F2ApiTest.FIXED` con `budget_year`; `ExactlyOneGuard` de `task` con `social_program`; `AmountFieldsGuard` con los
  montos de `program_budget`; `WorkflowMatrixApiTest` con WF-G y WF-P.
- **Aportes (5.2a).** `InvestmentRule`, `InvestmentRuleOrder.PROGRAM`, `InvestmentWriteGuard.OBJECTS` (que este plan amplía),
  `Synergy` (G3), `InvestmentCounts.nonNegativeInteger`, `Contributions.COMMITTED` y `EXECUTED`, las políticas de F5 en
  `ProjectParameters.POLICIES`, `ProjectLinkGuard` con `social_program`, `ApprovalStep` con `submit` opcional y `direct`, y el
  endpoint `POST /api/sgspe/social-contributions/{id}/deliveries` (lo usan las pruebas de los tableros). El detalle está en
  «Dependencias de otros planes», al final.

## Interfaces con el portal (plan 5.3)

Lo que el portal consume de este plan, además de lo de 5.2a (contrato completo en `api.md` tras el plan 5.0):

- Transiciones de WF-G por la API genérica: `approve` → `403 ACTION_REQUIRED` (`record`, sin `APPROVE`), `403
  SEGREGATION_OF_DUTIES` (`record`, quien aprueba registró el programa), `409 SYNERGY_AGREEMENT_REQUIRED` (`agreement`: con
  socio y `policy_synergy_requires_agreement` activa, falta el acuerdo, no está en `active`, está VENCIDO o el socio no es una
  de sus partes); `plan_direct` → `409 INTERNAL_APPROVAL_REQUIRED` (`project`) y el mismo `409 SYNERGY_AGREEMENT_REQUIRED`;
  `return_to_draft`, `suspend` y las `cancel_*` sin motivo → `400 REASON_REQUIRED` (`reason`). Lo que la regla no puede
  leer, `403 OBJECT_NOT_READABLE` con el objeto en el campo. Todas en la consulta en seco («Antes de aprobar» en S43 y S48).
- Hijos del programa: `400 OUT_OF_RANGE` sobre `end_date` (programa e iniciativa) y `period_end_date` (medición) anteriores
  al inicio, sobre `budget_year` fuera de 2000 a 2100 o no entero y sobre `target_count` o `reached_count` negativos o no
  enteros; `409 PROGRAM_MISMATCH` (`social_initiative`) si la iniciativa del grupo es de otro programa; una medición repetida
  en el mismo periodo, `409` del único; cambiar o borrar una medición, o borrar un indicador con mediciones, `409` de
  wasichai (`appendOnly`).
- `social_program.total_budget`: derivado de solo lectura, la suma de `allocated_amount` de los presupuestos en la moneda del
  programa (0 sin presupuestos en ella, vacío sin moneda); los de otra moneda S43 los muestra aparte.
- Aviso `approval_pending:social_program:<id>` (fuente `sgspe.approvals`, `ACTION`, enlace al programa) para los asignados
  con un rol de `approve`, al enviar; se resuelve al salir de `pending_approval`. Texto: «código · nombre».
- `GET /api/sgspe/dashboards/investment` → KPI-INV-01, 02, 03 y 04 en el orden de `dashboards.md` §5.10, con
  `?project=&periodStart=&periodEnd=&locality=&explorationCampaign=&program=&year=` (más los filtros comunes, que cada KPI
  declara en `ignoredFilters` si no los aplica). Formas:
  - KPI-INV-01 (`unit` «%»): `value` = ejecutado ÷ tope de las campañas en PEN; desglose `campaign` con filas
    `{ key, label, currency, cap, committed, executed, committedPercent, executedPercent, committedOtherCurrencyCount,
    otherCurrencyCount }`.
  - KPI-INV-02 (`unit` «%»): `value` = ejecutado ÷ asignado de los presupuestos en PEN; desglose `program` con filas
    `{ key: "<programa>:<año>:<moneda>", program, label, year, currency, allocated, executed, percent, state }` (`state`, el
    estado de WF-G del programa, para que S43 y el tablero no muestren ejecución de un programa por aprobar).
  - KPI-INV-03 (`unit` «monto»): `value` = ejecutado en PEN del periodo; desgloses `source` (`{ key: "<fuente>:<moneda>",
    source: COMPROMISOS | APORTES | PROGRAMAS, currency, value }`) y `project` (`{ key, project, label, currency, executed,
    commitments, contributions, programs, budget, cap }`).
  - KPI-INV-04 (`unit` «beneficiarios»): `value` = alcanzados de los grupos de beneficiarios; desgloses `source`
    (`PROGRAMAS` con `value`, `target` y `count`; `APORTES` y `COMPROMISOS` con `value` y `count`, sin sumarse) y `program`
    (`{ key, label, target, reached }`).
  - `warnings` en español cuando hay montos en otra moneda o, en KPI-INV-03, programas excluidos por `locality`.
  - `program` o `year` en otro tablero o KPI: `422`; `year` fuera de 2000 a 2100 o `program` que no es un UUID: `400`.

---

## Mapa de archivos

Todos bajo la raíz del repositorio. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `src/main/kotlin/sgspe/investment/Programs.kt` (crear) | `Programs` (nombres) y `ProgramRules` (reglas puras y hallazgos) | 1 |
| `src/main/kotlin/sgspe/investment/ProgramApprovalRule.kt` (crear) | G1 a G4 como `InvestmentRule` | 2 |
| `src/main/kotlin/sgspe/investment/ProgramDataRule.kt` (crear), `src/test/kotlin/sgspe/F2ApiTest.kt` (`FIXED`) | Fechas, año, conteos y «mismo programa» | 3 |
| `src/main/kotlin/sgspe/investment/ProgramTotals.kt` (crear) | `ProgramTotals`, `ProgramTotalsWriter`, `ProgramTotalsListener`, `ProgramTotalsRecalculator` | 4 |
| `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`, `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt` | Aviso de WF-G y tarea de un programa | 5 |
| `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`, `Dashboards.kt`, `Kpis.kt` (`ignoredFilters`, `extraFilters`) | Filtros `program` y `year` | 6 |
| `src/main/kotlin/sgspe/dashboard/InvestmentKpis.kt` (crear), `CommitmentKpis.kt` (`CommitmentRow.beneficiaries`) | KPI-INV-01 a 04, puros | 7 |
| `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, `Kpis.kt` (KPI-INV y tablero) | Lecturas de inversión y tablero `investment` | 8 |
| `src/test/kotlin/sgspe/F5ProgramApiTest.kt` (crear), `src/test/kotlin/sgspe/investment/SocialProgramWorkflowApiTest.kt` (crear) | Base de las pruebas de 5.2b y WF-G por la API | 9 |
| `src/test/kotlin/sgspe/dashboard/InvestmentDashboardApiTest.kt` (crear) | Tablero `investment` por la API | 10 |
| `src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt` | Casos de F5 del alcance | 11 |
| `src/test/kotlin/sgspe/security/PermissionMatrixApiTest.kt` | «Otro proyecto» de los hijos de BC-10 | 12 |
| — (coordinador) | Tenant de desarrollo en F5 y medición de las subconsultas | 13 |
| `docs/api.md`, `docs/dashboards.md`, `docs/roadmap.md`, `CLAUDE.md` | Cierre, verificación, suite y PR | 14 |

Las pruebas viven en `src/test/kotlin/sgspe/<paquete>/` con el nombre que da cada tarea: unitarias `*Test` (sin base) y de
integración `*ApiTest` (sobre `F5ProgramApiTest` o `F2ApiTest`, en la CI del PR).

## Orden y paralelismo

Cada tarea se puede hacer en su propio worktree cuando las tareas de las que depende ya están integradas en
`feat/fase-5-2b-programas`. Las de un mismo grupo tocan archivos distintos y no usan nada unas de otras; las que comparten
un archivo en grupos distintos lo editan en puntos separados, así sus bloques «reemplazar» no dependen del orden. La
integración la corre la CI del PR (o el coordinador, una clase tras otra, porque todas usan `sgspe_sdd_test`).

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| A | 1, 6, 7 | La rama creada | Nombres y reglas puras de programas, filtros del tablero y KPI-INV puros: archivos disjuntos |
| B | 2, 3, 4, 5 | 1 | Reglas de aprobación, reglas de los hijos, totales y avisos: archivos disjuntos |
| C | 8 | 6 y 7 | Lecturas y tablero: usan los filtros y las funciones puras; edita `Kpis.kt` después de la 6 |
| D | 9 | 2, 3, 4 y 5 | La base de pruebas y WF-G por la API |
| E | 10, 11, 12 | 8 y 9 (la 12, solo la 3) | Pruebas de integración en archivos disjuntos |
| F | 13 | 2 a 12 integradas (coordinador) | Tenant de desarrollo y medición por el túnel |
| G | 14 | Todas | Documentos, verificación, suite en la CI, push y PR |

Archivos que comparten tareas: `src/main/kotlin/sgspe/dashboard/Kpis.kt` (6 → 8, en puntos distintos),
`src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt` (7) y `DashboardData.kt` (8, que llena el campo que suma la 7).

---
### Tarea 1: Los nombres y las reglas puras de los programas

**Files:**
- Create: `src/main/kotlin/sgspe/investment/Programs.kt`
- Test: `src/test/kotlin/sgspe/investment/ProgramRulesTest.kt`

**Interfaces:**
- Consumes: `Finding`, `Values`, `ModelCatalog`, `ProjectParameters.policy`; del plan 5.2a, `Synergy` (`POLICY`, `EXPIRED`,
  `VALIDITY`, `live`, `required`) e `InvestmentCounts.nonNegativeInteger(field, after, changed): Finding?`; el modelo de 5.1
  (los siete objetos, WF-G, `agreement_party`).
- Produces: `object Programs` (`PROGRAM`, `INITIATIVE`, `GROUP`, `BUDGET`, `INDICATOR`, `MEASUREMENT`, `MILESTONE`,
  `OBJECTS: Set<String>`, `SUBMIT`, `APPROVE`, `PLAN_DIRECT`, `PENDING`, `CLOSE`, `PROJECT`, `PARTNER`, `AGREEMENT`,
  `AGREEMENT_PARTY`, `PARTY_ORGANIZATION`, `CURRENCY`, `TOTAL_BUDGET`, `ALLOCATED`, `YEAR`, `INTERNAL_POLICY`,
  `APPROVE_ACTION`, `RECORD`); `object ProgramRules` (`MIN_YEAR`, `MAX_YEAR`, `PERIODS: Map<String, Pair<String, String>>`,
  `COUNTS: Map<String, List<String>>`, `data(objectName: String, after: Map<String, Any?>, changed: Set<String>):
  List<Finding>`, `internalApproval()`, `actionRequired()`, `segregation()`, `programMismatch()`: `Finding`). El «acuerdo
  vigente» y su hallazgo son `Synergy.live` y `Synergy.required` de 5.2a (elección 9: el mismo criterio para P6 y G3).

- [ ] **Paso 1: Comprobar la rama**

```bash
cd ~/IdeaProjects/sgspe && git fetch origin && git switch -c feat/fase-5-2b-programas origin/main
test -f src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt && test -f model/workflows/social_program.json \
  && test -f src/test/kotlin/sgspe/F5ApiTest.kt && grep -q "object Synergy" src/main/kotlin/sgspe/investment/*.kt \
  && echo "5.1 y 5.2a integrados"
```

Esperado: `5.1 y 5.2a integrados`. Si falta algo, no se sigue: 5.2b parte de 5.2a integrada (especificación §7).

- [ ] **Paso 2: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/investment/ProgramRulesTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog
import sgspe.common.ProjectParameters

// las reglas de datos de los programas (especificación de F5 §3.6), sin base: las fechas en orden, el año presupuestal y los
// conteos; y que los nombres de Programs existen en el modelo. el acuerdo vigente de G3 es Synergy.live (plan 5.2a)
class ProgramRulesTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val none = emptyList<Pair<String, String?>>()

    private fun codes(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String> = after.keys
    ) = ProgramRules.data(objectName, after, changed).map { it.code to it.field }

    @Test
    fun `una fecha final anterior a la inicial es OUT_OF_RANGE sobre la final`() {
        assertEquals(
            listOf("OUT_OF_RANGE" to "period_end_date"),
            codes(Programs.MEASUREMENT, mapOf("period_start_date" to "2026-04-01", "period_end_date" to "2026-03-31"))
        )
        assertEquals(none, codes(Programs.MEASUREMENT, mapOf("period_start_date" to "2026-04-01", "period_end_date" to "2026-04-01")))
        assertEquals(listOf("OUT_OF_RANGE" to "end_date"), codes(Programs.PROGRAM, mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01")))
        assertEquals(listOf("OUT_OF_RANGE" to "end_date"), codes(Programs.INITIATIVE, mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01")))
        // sin una de las dos no hay qué comparar; si la escritura no cambia ninguna, no se juzga
        assertEquals(none, codes(Programs.PROGRAM, mapOf("end_date" to "2026-01-01")))
        assertEquals(none, codes(Programs.PROGRAM, mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01"), setOf("name")))
    }

    @Test
    fun `el anio presupuestal va de 2000 a 2100 y es entero`() {
        for (valid in listOf<Any>(2000, 2026, "2100", "2026.00")) {
            assertEquals(none, codes(Programs.BUDGET, mapOf("budget_year" to valid)), "$valid")
        }
        for (wrong in listOf<Any>(1999, 2101, "2026.5", "dos mil")) {
            assertEquals(listOf("OUT_OF_RANGE" to "budget_year"), codes(Programs.BUDGET, mapOf("budget_year" to wrong)), "$wrong")
        }
        // un año guardado antes de la regla no bloquea otra escritura del presupuesto
        assertEquals(none, codes(Programs.BUDGET, mapOf("budget_year" to 1999), setOf("allocated_amount")))
    }

    @Test
    fun `los conteos de un grupo son enteros de 0 o mas`() {
        assertEquals(none, codes(Programs.GROUP, mapOf("target_count" to 0, "reached_count" to "40")))
        assertEquals(
            listOf("OUT_OF_RANGE" to "target_count", "OUT_OF_RANGE" to "reached_count"),
            codes(Programs.GROUP, mapOf("target_count" to -1, "reached_count" to "4.5"))
        )
        assertEquals(none, codes(Programs.GROUP, mapOf("target_count" to null, "name" to "Familias")))
    }

    @Test
    fun `los hallazgos de G1, G2, G4 y de mismo programa llevan su codigo, su campo y su estado`() {
        assertEquals("INTERNAL_APPROVAL_REQUIRED" to "project", ProgramRules.internalApproval().let { it.code to it.field })
        assertEquals(403, ProgramRules.actionRequired().status.value())
        assertEquals("record", ProgramRules.actionRequired().field)
        assertEquals(403, ProgramRules.segregation().status.value())
        assertEquals("PROGRAM_MISMATCH" to "social_initiative", ProgramRules.programMismatch().let { it.code to it.field })
        assertEquals(409, ProgramRules.programMismatch().status.value())
    }

    // un renombre en el modelo dejaría de vigilar un campo o una transición en silencio
    @Test
    fun `los nombres de Programs existen en el modelo`() {
        for (name in Programs.OBJECTS + Programs.AGREEMENT_PARTY) assertNotNull(catalog.objectOrNull(name), name)
        assertTrue(Programs.TOTAL_BUDGET in catalog.derivedFields(Programs.PROGRAM))
        assertTrue(Programs.APPROVE_ACTION in catalog.objectOrNull(Programs.PROGRAM)!!.actions)
        for (name in listOf(Programs.SUBMIT, Programs.APPROVE, Programs.PLAN_DIRECT, Programs.CLOSE)) {
            assertNotNull(catalog.transition(Programs.PROGRAM, name), name)
        }
        assertEquals(Programs.PENDING, catalog.transition(Programs.PROGRAM, Programs.SUBMIT)!!.to)
        assertEquals(Programs.PENDING, catalog.transition(Programs.PROGRAM, Programs.APPROVE)!!.from)
        assertEquals(Programs.PARTNER, catalog.relationField(Programs.PROGRAM, "organization"))
        assertEquals(Programs.AGREEMENT, catalog.relationField(Programs.PROGRAM, Programs.AGREEMENT))
        assertEquals(Programs.PARTY_ORGANIZATION, catalog.relationField(Programs.AGREEMENT_PARTY, "organization"))
        assertEquals(Programs.INITIATIVE, catalog.relationField(Programs.GROUP, Programs.INITIATIVE))
        assertEquals(Programs.PROGRAM, catalog.relationField(Programs.BUDGET, Programs.PROGRAM))
        assertTrue(Synergy.EXPIRED in catalog.enumOf(Programs.AGREEMENT, Synergy.VALIDITY))
        val fields = { name: String -> catalog.objectOrNull(name)!!.fields.keys }
        for ((name, period) in ProgramRules.PERIODS) assertTrue(fields(name).containsAll(period.toList()), name)
        for ((name, counts) in ProgramRules.COUNTS) assertTrue(fields(name).containsAll(counts), name)
        assertTrue(Programs.YEAR in fields(Programs.BUDGET))
        assertTrue(fields(Programs.BUDGET).containsAll(listOf(Programs.ALLOCATED, Programs.CURRENCY)))
    }

    // las dos políticas de WF-G valen true si el proyecto las deja vacías (database-model.md §6, ASM-019)
    @Test
    fun `las politicas de sinergia y de aprobacion interna valen true por defecto`() {
        assertTrue(ProjectParameters.policy(emptyMap(), Synergy.POLICY))
        assertTrue(ProjectParameters.policy(emptyMap(), Programs.INTERNAL_POLICY))
    }
}
```

- [ ] **Paso 3: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ProgramRulesTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'Programs'` y `'ProgramRules'`).

- [ ] **Paso 4: Los nombres y las reglas**

Crear `src/main/kotlin/sgspe/investment/Programs.kt`:

```kotlin
package sgspe.investment

import org.springframework.http.HttpStatus
import sgspe.common.Finding
import sgspe.common.Values
import java.time.LocalDate

// los nombres de WF-G y de los hijos de social_program (workflows.md WF-G, database-model.md §15, especificación de F5 §3.6):
// una sola lista para las reglas de InvestmentWriteGuard, el listener de totales, el aviso de aprobación y los tableros
object Programs {
    const val PROGRAM = "social_program"
    const val INITIATIVE = "social_initiative"
    const val GROUP = "beneficiary_group"
    const val BUDGET = "program_budget"
    const val INDICATOR = "program_indicator"
    const val MEASUREMENT = "program_measurement"
    const val MILESTONE = "program_milestone"

    // el programa y sus hijos: los objetos de BC-10 que juzgan ProgramApprovalRule y ProgramDataRule
    val OBJECTS: Set<String> = setOf(PROGRAM, INITIATIVE, GROUP, BUDGET, INDICATOR, MEASUREMENT, MILESTONE)

    // las transiciones de WF-G que miran la guarda, el aviso de aprobación y el escalamiento de las tareas
    const val SUBMIT = "submit"
    const val APPROVE = "approve"
    const val PLAN_DIRECT = "plan_direct"
    const val PENDING = "pending_approval"
    const val CLOSE = "close"

    const val PROJECT = "project"
    const val PARTNER = "partner_organization"
    const val AGREEMENT = "agreement"
    const val AGREEMENT_PARTY = "agreement_party"
    const val PARTY_ORGANIZATION = "organization"
    const val CURRENCY = "currency"
    const val TOTAL_BUDGET = "total_budget"
    const val ALLOCATED = "allocated_amount"
    const val YEAR = "budget_year"

    const val INTERNAL_POLICY = "policy_internal_approval"
    const val APPROVE_ACTION = "APPROVE"

    // el campo de los hallazgos sobre quien aprueba (G1, G2), como ApprovalRules.RECORD de los compromisos
    const val RECORD = "record"
}

// las reglas de datos de WF-G y de los hijos, puras (especificación de F5 §3.6): las fechas en orden, el año presupuestal y los
// conteos de personas (InvestmentCounts, plan 5.2a). una regla de datos se juzga en el alta y cuando la escritura cambia alguno
// de sus campos: un valor guardado antes de la regla no bloquea otra escritura del registro (como RiskRules.reviewEvery). el
// acuerdo vigente de G3 es Synergy.live, el mismo de P6 (elección 9)
object ProgramRules {
    const val MIN_YEAR = 2000L
    const val MAX_YEAR = 2100L

    // el inicio y el fin de cada objeto con periodo: el fin no puede ser anterior al inicio
    val PERIODS: Map<String, Pair<String, String>> =
        mapOf(
            Programs.PROGRAM to ("start_date" to "end_date"),
            Programs.INITIATIVE to ("start_date" to "end_date"),
            Programs.MEASUREMENT to ("period_start_date" to "period_end_date")
        )

    // los conteos de personas: enteros, 0 o más
    val COUNTS: Map<String, List<String>> = mapOf(Programs.GROUP to listOf("target_count", "reached_count"))

    // los 400 OUT_OF_RANGE de las fechas, del año y de los conteos del objeto, con el registro como queda (`after`) y los campos
    // que la escritura cambia (`changed`; en un alta, los que llegan con valor)
    fun data(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> = listOfNotNull(period(objectName, after, changed), year(objectName, after, changed)) + counts(objectName, after, changed)

    private fun period(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): Finding? {
        val (startField, endField) = PERIODS[objectName] ?: return null
        if (startField !in changed && endField !in changed) return null
        val start = date(after[startField]) ?: return null
        val end = date(after[endField]) ?: return null
        return if (end.isBefore(start)) outOfRange(endField, "$endField no puede ser anterior a $startField") else null
    }

    private fun year(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): Finding? {
        if (objectName != Programs.BUDGET || Programs.YEAR !in changed || Values.text(after[Programs.YEAR]) == null) return null
        val year = whole(after[Programs.YEAR])
        if (year != null && year in MIN_YEAR..MAX_YEAR) return null
        return outOfRange(Programs.YEAR, "budget_year es un año entero de $MIN_YEAR a $MAX_YEAR")
    }

    private fun counts(
        objectName: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): List<Finding> = COUNTS[objectName].orEmpty().mapNotNull { InvestmentCounts.nonNegativeInteger(it, after, changed) }

    // el número entero que trae el valor, o null si no es un número entero
    private fun whole(value: Any?): Long? = Values.decimal(value)?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }

    private fun date(value: Any?): LocalDate? = Values.text(value)?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

    private fun outOfRange(
        field: String,
        message: String
    ): Finding = Finding("OUT_OF_RANGE", message, field, status = HttpStatus.BAD_REQUEST)

    // G4: plan_direct solo sin aprobación interna (ASM-008)
    fun internalApproval(): Finding =
        Finding(
            "INTERNAL_APPROVAL_REQUIRED",
            "El proyecto tiene la aprobación interna activa: el programa se envía a aprobación (G4, ASM-008)",
            Programs.PROJECT
        )

    // G1: la doble llave (RF-WFL-002)
    fun actionRequired(): Finding =
        Finding(
            "ACTION_REQUIRED",
            "Aprobar un programa exige la acción APPROVE sobre social_program (G1, RF-WFL-002)",
            Programs.RECORD,
            status = HttpStatus.FORBIDDEN
        )

    // G2: la segregación (ASM-008)
    fun segregation(): Finding =
        Finding(
            "SEGREGATION_OF_DUTIES",
            "Quien aprueba un programa no puede ser quien lo registró (G2, ASM-008)",
            Programs.RECORD,
            status = HttpStatus.FORBIDDEN
        )

    // la iniciativa de un grupo de beneficiarios es de su mismo programa (elección 5 de la especificación)
    fun programMismatch(): Finding =
        Finding("PROGRAM_MISMATCH", "La iniciativa del grupo de beneficiarios pertenece a otro programa", Programs.INITIATIVE)
}
```

- [ ] **Paso 5: Correr la prueba para verla pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.investment.ProgramRulesTest'
```

Esperado: `BUILD SUCCESSFUL`, 6 pruebas en verde.

- [ ] **Paso 6: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/Programs.kt src/test/kotlin/sgspe/investment/ProgramRulesTest.kt
git commit -F - <<'MSG'
feat: nombres y reglas puras de los programas sociales (WF-G)

Programs reúne los nombres de WF-G y de los hijos de social_program; ProgramRules, las fechas en
orden, el año presupuestal de 2000 a 2100, los conteos enteros (InvestmentCounts) y los hallazgos de
G1, G2, G4 y PROGRAM_MISMATCH; G3 usa Synergy de 5.2a.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 2: G1 a G4 en `InvestmentWriteGuard` (`ProgramApprovalRule`)

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ProgramApprovalRule.kt`
- Modify: `src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt` (solo `OBJECTS`)
- Test: `src/test/kotlin/sgspe/investment/ProgramApprovalRuleTest.kt`

**Interfaces:**
- Consumes: `Programs` y `ProgramRules` (Tarea 1); del plan 5.2a, `interface InvestmentRule { val order: Int; suspend fun
  findings(change: RecordWrite, dryRun: Boolean): List<Finding> }`, `object InvestmentRuleOrder` con `PROGRAM = 400`,
  `InvestmentWriteGuard.OBJECTS: Set<String>` (que esta tarea amplía con los objetos de programas) y `Synergy` (`POLICY`,
  `VALIDITY`, `live`, `required`); `Records.projectOf` (`sgspe.commitment`), `Records.readable`, `readAs`,
  `NotReadable`, `Callers`, `Permissions`, `ProjectParameters.policy`.
- Produces: el bean `ProgramApprovalRule(records: Records, callers: Callers, permissions: Permissions) : InvestmentRule`
  (`order = InvestmentRuleOrder.PROGRAM`).

G4 primero (como D8 en compromisos: decide si la transición tiene sentido en el proyecto), después G1 y G2 en `approve` y al
final G3, la que más lee. La escritura se detiene en el primer bloqueo; la consulta en seco los junta todos. Lo que la regla
no puede leer lanza `NotReadable`, que `InvestmentWriteGuard` vuelve `403 OBJECT_NOT_READABLE` (plan 5.2a).

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/investment/ProgramApprovalRuleTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.Records
import wasichai.core.common.ForbiddenException
import wasichai.core.common.PageResponse
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordQuery
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RelatedRecordService
import wasichai.core.identity.AuthenticatedUser
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID

// G1 a G4 de WF-G sin base, con un RecordService de mentira (como SupportPolicyGuardTest): lo que cada transición exige, el
// orden en la escritura y en seco, y lo que no se puede leer (NotReadable, nunca un pase)
class ProgramApprovalRuleTest {
    private val user = AuthenticatedUser(UUID.randomUUID(), UUID.randomUUID(), "aprobador@sgspe.test", listOf("ROL_DE_PRUEBA"), null)
    private val partner = UUID.randomUUID().toString()
    private val agreement = UUID.randomUUID().toString()
    private var project: Map<String, Any?> = emptyMap()
    private var agreementState: String? = "active"
    private var validity: String? = "VIGENTE"
    private var agreementDenied = false
    private var parties = 1L
    private var partiesDenied = false
    private var approves = true
    private var creator = false

    // get: el proyecto o el acuerdo; list (los conteos de Records.count): las partes del acuerdo con el socio, y el programa por
    // su id (sin criterios: existe) o por su id y created_by (con criterios: lo registró quien aprueba)
    private val service: RecordService =
        mock(RecordService::class.java) { call ->
            when (call.method.name) {
                "get" ->
                    if (call.arguments[0] == Programs.AGREEMENT) {
                        if (agreementDenied) throw ForbiddenException("sin lectura")
                        RecordResponse(agreement, null, null, mapOf(Synergy.VALIDITY to validity), agreementState)
                    } else {
                        RecordResponse(UUID.randomUUID().toString(), null, null, project)
                    }
                "list" -> {
                    val query = call.arguments[1] as RecordQuery
                    val total =
                        if (call.arguments[0] == Programs.AGREEMENT_PARTY) {
                            if (partiesDenied) throw ForbiddenException("sin lectura")
                            parties
                        } else if (query.criteria.isEmpty() || creator) {
                            1L
                        } else {
                            0L
                        }
                    PageResponse.of(emptyList<RecordResponse>(), 0, 1, total)
                }
                else -> null
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val rule =
        ProgramApprovalRule(
            Records(
                provider(service),
                provider<RelatedRecordService>(null),
                provider<WorkflowService>(null),
                mock(MetadataService::class.java),
                mock(Callers::class.java),
                mock(ModelCatalog::class.java),
                mock(DerivedVersions::class.java)
            ),
            mock(Callers::class.java) { call -> if (call.method.name == "userOrNull") user else null },
            mock(Permissions::class.java) { call -> if (call.method.name == "has") approves else null }
        )

    private val base = mapOf<String, Any?>(Programs.PROJECT to UUID.randomUUID().toString())
    private val partnered = base + mapOf(Programs.PARTNER to partner, Programs.AGREEMENT to agreement)

    private fun change(
        transition: String,
        before: Map<String, Any?>,
        objectName: String = Programs.PROGRAM
    ) = RecordWrite(
        UUID.randomUUID(),
        user.userId,
        UUID.randomUUID(),
        objectName,
        UUID.randomUUID(),
        RecordChangeKind.TRANSITIONED,
        before,
        null,
        transition
    )

    private fun findings(
        transition: String,
        before: Map<String, Any?> = base,
        dryRun: Boolean = true
    ) = runBlocking { rule.findings(change(transition, before), dryRun) }

    private fun codes(
        transition: String,
        before: Map<String, Any?> = base,
        dryRun: Boolean = true
    ) = findings(transition, before, dryRun).map { it.code to it.field }

    private val none = emptyList<Pair<String, String?>>()

    @Test
    fun `plan_direct con la aprobacion interna activa es INTERNAL_APPROVAL_REQUIRED y sin ella pasa`() {
        assertEquals(listOf("INTERNAL_APPROVAL_REQUIRED" to "project"), codes(Programs.PLAN_DIRECT))
        project = mapOf(Programs.INTERNAL_POLICY to false)
        assertEquals(none, codes(Programs.PLAN_DIRECT))
    }

    @Test
    fun `approve sin APPROVE y por quien lo registro son 403 y la escritura se detiene en el primero`() {
        approves = false
        creator = true
        assertEquals(listOf("ACTION_REQUIRED" to "record", "SEGREGATION_OF_DUTIES" to "record"), codes(Programs.APPROVE))
        assertTrue(findings(Programs.APPROVE).all { it.status.value() == 403 })
        assertEquals(listOf("ACTION_REQUIRED" to "record"), codes(Programs.APPROVE, dryRun = false))
        approves = true
        assertEquals(listOf("SEGREGATION_OF_DUTIES" to "record"), codes(Programs.APPROVE))
        creator = false
        assertEquals(none, codes(Programs.APPROVE))
    }

    @Test
    fun `con socio y la politica activa G3 exige un acuerdo en active, no VENCIDO y con el socio como parte`() {
        val required = listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement")
        assertEquals(required, codes(Programs.APPROVE, base + (Programs.PARTNER to partner)))
        agreementState = "draft"
        assertEquals(required, codes(Programs.APPROVE, partnered))
        agreementState = "active"
        validity = "VENCIDO"
        assertEquals(required, codes(Programs.APPROVE, partnered))
        validity = null
        parties = 0
        assertEquals(required, codes(Programs.APPROVE, partnered))
        parties = 1
        assertEquals(none, codes(Programs.APPROVE, partnered))
        // también al planificar sin aprobación interna
        project = mapOf(Programs.INTERNAL_POLICY to false)
        parties = 0
        assertEquals(required, codes(Programs.PLAN_DIRECT, partnered))
    }

    @Test
    fun `sin socio o con la politica apagada G3 no lee nada`() {
        agreementDenied = true
        partiesDenied = true
        assertEquals(none, codes(Programs.APPROVE, base + (Programs.AGREEMENT to agreement)))
        project = mapOf(Synergy.POLICY to false)
        assertEquals(none, codes(Programs.APPROVE, partnered))
    }

    // la entrada 3 del Review Focus: un acuerdo o unas partes que quien aprueba no lee no se cuentan como cero
    @Test
    fun `lo que G3 no puede leer es NotReadable, nunca un pase`() {
        agreementDenied = true
        assertEquals(Programs.AGREEMENT, assertThrows<NotReadable> { findings(Programs.APPROVE, partnered) }.objectName)
        agreementDenied = false
        partiesDenied = true
        assertEquals(Programs.AGREEMENT_PARTY, assertThrows<NotReadable> { findings(Programs.APPROVE, partnered) }.objectName)
    }

    @Test
    fun `otras transiciones y otros objetos no se miran`() {
        approves = false
        assertEquals(none, codes(Programs.SUBMIT))
        assertEquals(none, runBlocking { rule.findings(change(Programs.APPROVE, base, "commitment"), true) }.map { it.code to it.field })
    }

    // InvestmentWriteGuard (plan 5.2a) solo corre sus reglas sobre OBJECTS: sin los de programas, G1 a G4 no correrían nunca. la
    // prueba falla hasta que esta tarea los suma
    @Test
    fun `InvestmentWriteGuard corre sus reglas sobre los objetos de programas`() {
        assertTrue(InvestmentWriteGuard.OBJECTS.containsAll(Programs.OBJECTS), "${InvestmentWriteGuard.OBJECTS}")
        assertEquals(InvestmentRuleOrder.PROGRAM, rule.order)
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ProgramApprovalRuleTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ProgramApprovalRule'`).

- [ ] **Paso 3: La regla**

Crear `src/main/kotlin/sgspe/investment/ProgramApprovalRule.kt`:

```kotlin
package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.commitment.projectOf
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import java.util.UUID

// InvestmentWriteGuard, WF-G (especificación de F5 §3.6, workflows.md WF-G): G4 (plan_direct solo sin aprobación interna,
// ASM-008) primero; G1 (la acción APPROVE, doble llave, RF-WFL-002) y G2 (quien aprueba no registró el programa, ASM-008) en
// approve; G3 (con socio y la política activa, un acuerdo en active, no VENCIDO y con el socio como parte, RN-INV-004) en
// approve y plan_direct, al final porque es la que más lee. lee como quien transiciona: lo que no lee es NotReadable
// (OBJECT_NOT_READABLE en InvestmentWriteGuard), nunca un pase. la escritura se detiene en el primer bloqueo; la consulta en
// seco los junta todos. decide con permisos y datos, nunca con nombres de rol
@Component
class ProgramApprovalRule(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.PROGRAM

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Programs.PROGRAM || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        val name = change.transition
        if (name != Programs.APPROVE && name != Programs.PLAN_DIRECT) return emptyList()
        val id = change.recordId ?: return emptyList()
        val before = change.before.orEmpty()
        val findings = mutableListOf<Finding>()
        if (name == Programs.PLAN_DIRECT && ProjectParameters.policy(records.projectOf(before), Programs.INTERNAL_POLICY)) {
            findings += ProgramRules.internalApproval()
            if (!dryRun) return findings
        }
        if (name == Programs.APPROVE) {
            val user = callers.userOrNull()
            if (user == null || !permissions.has(user, Programs.APPROVE_ACTION, Programs.PROGRAM)) {
                findings += ProgramRules.actionRequired()
                if (!dryRun) return findings
            }
            if (user == null || createdBy(id, user.userId)) {
                findings += ProgramRules.segregation()
                if (!dryRun) return findings
            }
        }
        synergy(before)?.let { findings += it }
        return findings
    }

    // G3: null si el programa no tiene socio, si la política está apagada o si el acuerdo cumple
    private suspend fun synergy(before: Map<String, Any?>): Finding? {
        val partner = Records.uuid(before[Programs.PARTNER]) ?: return null
        if (!ProjectParameters.policy(records.projectOf(before), Synergy.POLICY)) return null
        val id = Records.uuid(before[Programs.AGREEMENT]) ?: return Synergy.required("el programa tiene socio y no tiene acuerdo")
        val agreement = records.readable(Programs.AGREEMENT, id)
        if (!Synergy.live(agreement.state, Values.text(agreement.attributes[Synergy.VALIDITY]))) {
            return Synergy.required("el acuerdo del programa no está vigente")
        }
        val parties =
            readAs(Programs.AGREEMENT_PARTY) {
                records.count(
                    Programs.AGREEMENT_PARTY,
                    mapOf(Programs.AGREEMENT to id.toString(), Programs.PARTY_ORGANIZATION to partner.toString())
                )
            }
        return if (parties > 0) null else Synergy.required("el socio del programa no es parte del acuerdo")
    }

    // created_by es una columna de sistema: se compara en la base como quien llama, como ApprovalKeyRule de compromisos. falla
    // cerrada: si quien aprueba no ve el programa, el conteo con created_by daría 0 y G2 pasaría en silencio; es NotReadable
    private suspend fun createdBy(
        id: UUID,
        userId: UUID
    ): Boolean =
        readAs(Programs.PROGRAM) {
            if (records.count(Programs.PROGRAM, ids = listOf(id)) == 0L) throw NotReadable(Programs.PROGRAM)
            records.count(Programs.PROGRAM, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(id)) > 0
        }
}
```

En `src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt` (plan 5.2a), reemplazar:

```kotlin
        // los objetos de aportes; delivery, por sus entregas de aportes (las reglas miran social_contribution). el plan 5.2b
        // suma los de programas
        val OBJECTS: Set<String> = setOf(Contributions.CONTRIBUTION, Contributions.ITEM, Contributions.DELIVERY)
```

por:

```kotlin
        // los objetos de aportes; delivery, por sus entregas de aportes (las reglas miran social_contribution); y, desde el plan
        // 5.2b, el programa y sus hijos (ProgramApprovalRule y ProgramDataRule)
        val OBJECTS: Set<String> = setOf(Contributions.CONTRIBUTION, Contributions.ITEM, Contributions.DELIVERY) + Programs.OBJECTS
```

Nada más de ese archivo cambia. Si el plan 5.2a movió `projectOf` a `sgspe.common`, el import es `sgspe.common.projectOf` (la
función es la misma).

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.investment.*'
```

Esperado: `BUILD SUCCESSFUL`; `ProgramApprovalRuleTest` con 7 pruebas en verde y las demás de `sgspe.investment` como en
`main`.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ProgramApprovalRule.kt src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt \
  src/test/kotlin/sgspe/investment/ProgramApprovalRuleTest.kt
git commit -F - <<'MSG'
feat: G1 a G4 de WF-G en InvestmentWriteGuard

ProgramApprovalRule rechaza plan_direct con la aprobación interna activa, approve sin la acción
APPROVE o por quien registró el programa y, con socio y la política activa, la aprobación o la
planificación sin un acuerdo vigente con el socio como parte (RN-INV-004), también en seco.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** la cubre `SocialProgramWorkflowApiTest` (Tarea 9).

---

### Tarea 3: Las reglas de los hijos del programa (`ProgramDataRule`)

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ProgramDataRule.kt`
- Check: `src/test/kotlin/sgspe/F2ApiTest.kt` (`FIXED`, que deja el plan 5.1)
- Test: `src/test/kotlin/sgspe/investment/ProgramDataRuleTest.kt`

**Interfaces:**
- Consumes: `Programs`, `ProgramRules.data`, `ProgramRules.programMismatch` (Tarea 1); `InvestmentRule`,
  `InvestmentRuleOrder.PROGRAM` (plan 5.2a); `WriteView`, `isLink`, `Records.readable`.
- Produces: el bean `ProgramDataRule(records: Records, view: WriteView) : InvestmentRule`
  (`order = InvestmentRuleOrder.PROGRAM + 10`).

En el alta y el cambio de un programa o de un hijo: las fechas en orden, el año y los conteos (`400 OUT_OF_RANGE`) y, en un
grupo de beneficiarios, que su iniciativa sea de su mismo programa (`409 PROGRAM_MISMATCH`, decisión 14).

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/investment/ProgramDataRuleTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.springframework.beans.factory.ObjectProvider
import sgspe.common.Callers
import sgspe.common.DerivedVersions
import sgspe.common.ModelCatalog
import sgspe.common.NotReadable
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.WriteView
import wasichai.core.common.ForbiddenException
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RelatedRecordService
import wasichai.core.metadata.MetadataService
import wasichai.workflow.WorkflowService
import java.util.UUID

// las reglas de los hijos del programa sin base (especificación de F5 §3.6): las de datos llegan por la regla y la de «mismo
// programa» lee la iniciativa como quien escribe
class ProgramDataRuleTest {
    private val program = UUID.randomUUID().toString()
    private val other = UUID.randomUUID().toString()
    private val initiative = UUID.randomUUID().toString()
    private var initiativeProgram: String = program
    private var initiativeDenied = false

    private val service: RecordService =
        mock(RecordService::class.java) { call ->
            if (call.method.name != "get") return@mock null
            if (initiativeDenied) throw ForbiddenException("sin lectura")
            RecordResponse(initiative, null, null, mapOf(Programs.PROGRAM to initiativeProgram))
        }

    // WriteView como la real: un alta es lo que manda; un cambio, lo que manda contra lo guardado
    @Suppress("UNCHECKED_CAST")
    private val view: WriteView =
        mock(WriteView::class.java) { call ->
            val change = call.arguments[0] as RecordWrite
            when (call.method.name) {
                "after" -> change.attributes.orEmpty()
                "changed" -> {
                    val after = call.arguments[1] as Map<String, Any?>
                    val before = change.before.orEmpty()
                    if (change.kind == RecordChangeKind.CREATED) {
                        after.filterValues { Values.text(it) != null }.keys
                    } else {
                        (after.keys + before.keys).filter { !Values.same(after[it], before[it]) }.toSet()
                    }
                }
                else -> null
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> provider(value: T?): ObjectProvider<T> = mock(ObjectProvider::class.java) { value } as ObjectProvider<T>

    private val rule =
        ProgramDataRule(
            Records(
                provider(service),
                provider<RelatedRecordService>(null),
                provider<WorkflowService>(null),
                mock(MetadataService::class.java),
                mock(Callers::class.java),
                mock(ModelCatalog::class.java),
                mock(DerivedVersions::class.java)
            ),
            view
        )

    private fun codes(
        objectName: String,
        attributes: Map<String, Any?>,
        before: Map<String, Any?>? = null
    ): List<Pair<String, String?>> {
        val kind = if (before == null) RecordChangeKind.CREATED else RecordChangeKind.UPDATED
        val change = RecordWrite(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, UUID.randomUUID(), kind, before, attributes)
        return runBlocking { rule.findings(change, dryRun = true) }.map { it.code to it.field }
    }

    private val none = emptyList<Pair<String, String?>>()

    @Test
    fun `un grupo con una iniciativa de otro programa es PROGRAM_MISMATCH sobre social_initiative`() {
        val group = mapOf(Programs.PROGRAM to program, Programs.INITIATIVE to initiative, "name" to "Familias")
        assertEquals(none, codes(Programs.GROUP, group))
        initiativeProgram = other
        assertEquals(listOf("PROGRAM_MISMATCH" to "social_initiative"), codes(Programs.GROUP, group))
        assertEquals(none, codes(Programs.GROUP, mapOf(Programs.PROGRAM to program, "name" to "Sin iniciativa")))
    }

    // la entrada 4 del Review Focus
    @Test
    fun `cambiar el programa de un grupo tambien se juzga contra su iniciativa`() {
        val before = mapOf<String, Any?>(Programs.PROGRAM to program, Programs.INITIATIVE to initiative, "name" to "Familias")
        assertEquals(listOf("PROGRAM_MISMATCH" to "social_initiative"), codes(Programs.GROUP, before + (Programs.PROGRAM to other), before))
        // un cambio que no toca el programa ni la iniciativa no la lee
        initiativeDenied = true
        assertEquals(none, codes(Programs.GROUP, before + ("name" to "Familias usuarias"), before))
    }

    @Test
    fun `una iniciativa que quien escribe no lee es NotReadable`() {
        initiativeDenied = true
        val group = mapOf(Programs.PROGRAM to program, Programs.INITIATIVE to initiative)
        assertEquals(Programs.INITIATIVE, assertThrows<NotReadable> { codes(Programs.GROUP, group) }.objectName)
    }

    @Test
    fun `las reglas de datos llegan por la regla en el alta y en el cambio`() {
        assertEquals(listOf("OUT_OF_RANGE" to "budget_year"), codes(Programs.BUDGET, mapOf(Programs.PROGRAM to program, "budget_year" to 1999)))
        val measurement = mapOf<String, Any?>("period_start_date" to "2026-04-01", "period_end_date" to "2026-04-30")
        assertEquals(
            listOf("OUT_OF_RANGE" to "period_end_date"),
            codes(Programs.MEASUREMENT, measurement + ("period_end_date" to "2026-03-31"))
        )
        val group = mapOf<String, Any?>(Programs.PROGRAM to program, "target_count" to 10)
        assertEquals(listOf("OUT_OF_RANGE" to "target_count"), codes(Programs.GROUP, group + ("target_count" to -3), group))
    }

    @Test
    fun `un vinculo, una transicion y otro objeto no se juzgan`() {
        initiativeDenied = true
        val link =
            RecordWrite(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Programs.PROGRAM,
                UUID.randomUUID(),
                RecordChangeKind.UPDATED,
                emptyMap(),
                mapOf("rel:evidence_social_program" to "x")
            )
        assertEquals(emptyList<Any>(), runBlocking { rule.findings(link, dryRun = true) })
        assertEquals(none, codes("commitment", mapOf("start_date" to "2026-06-01", "end_date" to "2026-01-01")))
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ProgramDataRuleTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ProgramDataRule'`).

- [ ] **Paso 3: La regla y el valor de prueba del año**

Crear `src/main/kotlin/sgspe/investment/ProgramDataRule.kt`:

```kotlin
package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite

// InvestmentWriteGuard, hijos del programa (especificación de F5 §3.6, «Otras reglas de los hijos»): en el alta y el cambio
// del programa y de sus hijos, las fechas en orden, el año presupuestal y los conteos (ProgramRules.data, 400 OUT_OF_RANGE);
// en un grupo de beneficiarios, que su iniciativa sea de su mismo programa (409 PROGRAM_MISMATCH), en el alta y cuando la
// escritura cambia la iniciativa o el programa del grupo. la iniciativa se lee como quien escribe: una que no lee es
// NotReadable (OBJECT_NOT_READABLE en InvestmentWriteGuard). la iniciativa y el grupo son del mismo proyecto por construcción:
// los dos cuelgan de un programa
@Component
class ProgramDataRule(
    private val records: Records,
    private val view: WriteView
) : InvestmentRule {
    override val order = InvestmentRuleOrder.PROGRAM + 10

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in Programs.OBJECTS || change.isLink()) return emptyList()
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        val after = view.after(change)
        val changed = view.changed(change, after)
        val findings = ProgramRules.data(change.objectName, after, changed).toMutableList()
        if (findings.isNotEmpty() && !dryRun) return findings
        if (change.objectName == Programs.GROUP && (Programs.INITIATIVE in changed || Programs.PROGRAM in changed)) {
            sameProgram(after)?.let { findings += it }
        }
        return findings
    }

    private suspend fun sameProgram(after: Map<String, Any?>): Finding? {
        val initiative = Records.uuid(after[Programs.INITIATIVE]) ?: return null
        val owner = Values.text(records.readable(Programs.INITIATIVE, initiative).attributes[Programs.PROGRAM])
        return if (owner == Values.text(after[Programs.PROGRAM])) null else ProgramRules.programMismatch()
    }
}
```

El registro mínimo de `program_budget` ya lleva `budget_year` 2026 (`F2ApiTest.FIXED`, decisión 10 del plan 5.1); se
comprueba:

```bash
cd ~/IdeaProjects/sgspe && grep -n '"program_budget" to mapOf("budget_year" to 2026)' src/test/kotlin/sgspe/F2ApiTest.kt
```

Esperado: una línea. Si no está, se agrega como última entrada de `FIXED` (con la coma que pida la lista) y
`src/test/kotlin/sgspe/F2ApiTest.kt` entra en el commit: sin ella, el alta mínima de la matriz de permisos sería `400
OUT_OF_RANGE`.

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ProgramDataRuleTest` con 5 pruebas en verde (y la compilación de las de integración, por
`F2ApiTest`).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ProgramDataRule.kt src/test/kotlin/sgspe/investment/ProgramDataRuleTest.kt
git commit -F - <<'MSG'
feat: reglas de los hijos del programa en InvestmentWriteGuard

ProgramDataRule rechaza fechas finales anteriores a las iniciales, un año presupuestal fuera de
2000 a 2100, conteos de personas negativos o no enteros y un grupo de beneficiarios cuya
iniciativa es de otro programa (PROGRAM_MISMATCH), también al cambiar su programa.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** la cubren `SocialProgramWorkflowApiTest` (Tarea 9) y `PermissionMatrixApiTest`
(Tarea 12).

---

### Tarea 4: El presupuesto total del programa (`ProgramTotalsListener` y su recalculador)

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ProgramTotals.kt`
- Test: `src/test/kotlin/sgspe/investment/ProgramTotalsTest.kt`, `src/test/kotlin/sgspe/investment/ProgramTotalsListenerTest.kt`

**Interfaces:**
- Consumes: `Programs` (Tarea 1); `Records`, `Transactions.locked`, `Amounts.parse`, `Values`, `Criteria.updatedSince`,
  `DerivedRecalculator`, `DerivedFieldsJob.reason`.
- Produces: `object ProgramTotals` (`data class Budget(allocated: BigDecimal?, currency: String?)`,
  `total(currency: String?, budgets: List<Budget>): BigDecimal?`, `affects(before: Map<String, Any?>?, after: Map<String,
  Any?>?): Boolean`); los beans `ProgramTotalsWriter(records, transactions)` con `suspend fun refresh(program: UUID, reason:
  String): Boolean` y `REASON`, `ProgramTotalsListener(writer)` y `ProgramTotalsRecalculator(records, writer)` con
  `sourceObject = social_program` y `REASON`.

`total_budget` = suma de `allocated_amount` de los presupuestos en la moneda del programa; 0 con moneda y sin presupuestos en
ella; vacío sin moneda (especificación §3.6, decisión 6). El listener corre en el alta, el cambio y la baja de un
presupuesto, en el alta de un programa y en el cambio de su moneda; si no puede escribir lo registra y la escritura sigue:
JOB-02 lo corrige (como `AttendanceCountsListener`).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/investment/ProgramTotalsTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

// total_budget de un programa (especificación de F5 §3.6, decisión 4, ASM-063), puro
class ProgramTotalsTest {
    private fun budget(
        amount: String?,
        currency: String?
    ) = ProgramTotals.Budget(amount?.let(::BigDecimal), currency)

    @Test
    fun `suma solo los presupuestos en la moneda del programa`() {
        val total = ProgramTotals.total("PEN", listOf(budget("1000", "PEN"), budget("500.50", "PEN"), budget("300", "USD"), budget(null, "PEN")))
        assertEquals(0, BigDecimal("1500.50").compareTo(total))
    }

    @Test
    fun `sin moneda del programa no hay total y sin presupuestos en ella es cero`() {
        assertNull(ProgramTotals.total(null, listOf(budget("1000", "PEN"))))
        assertEquals(0, BigDecimal.ZERO.compareTo(ProgramTotals.total("USD", listOf(budget("1000", "PEN")))))
        assertEquals(0, BigDecimal.ZERO.compareTo(ProgramTotals.total("PEN", emptyList())))
    }

    @Test
    fun `un cambio de presupuesto mueve el total solo si toca programa, monto asignado o moneda`() {
        val stored = mapOf<String, Any?>("social_program" to "p1", "allocated_amount" to 1000, "currency" to "PEN", "executed_amount" to 10)
        assertTrue(ProgramTotals.affects(null, stored))
        assertTrue(ProgramTotals.affects(stored, null))
        assertTrue(ProgramTotals.affects(stored, stored + ("allocated_amount" to "1000.5")))
        assertTrue(ProgramTotals.affects(stored, stored + ("social_program" to "p2")))
        assertFalse(ProgramTotals.affects(stored, stored + ("executed_amount" to 500)))
        assertFalse(ProgramTotals.affects(stored, stored + ("allocated_amount" to "1000.00")))
    }
}
```

Crear `src/test/kotlin/sgspe/investment/ProgramTotalsListenerTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import java.util.UUID

// ProgramTotalsListener sin base: a qué programas recalcula cada escritura (decisión 6 del plan 5.2b) y que un fallo del
// escritor no rompe la escritura que lo disparó
class ProgramTotalsListenerTest {
    private val p1 = UUID.randomUUID()
    private val p2 = UUID.randomUUID()
    private val refreshed = mutableListOf<UUID>()
    private var failing = false

    private val writer: ProgramTotalsWriter =
        mock(ProgramTotalsWriter::class.java) { call ->
            if (call.method.name != "refresh") return@mock null
            refreshed += call.arguments[0] as UUID
            if (failing) throw IllegalStateException("sin escritura")
            true
        }
    private val listener = ProgramTotalsListener(writer)

    private fun changed(
        objectName: String,
        kind: RecordChangeKind,
        before: Map<String, Any?>?,
        after: Map<String, Any?>?,
        id: UUID = UUID.randomUUID()
    ): List<UUID> {
        refreshed.clear()
        runBlocking {
            listener.recordChanged(RecordChange(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), objectName, id, kind, before, after))
        }
        return refreshed.toList()
    }

    private fun budget(program: UUID) = mapOf<String, Any?>("social_program" to program.toString(), "allocated_amount" to 1000, "currency" to "PEN")

    @Test
    fun `el alta y la baja de un presupuesto recalculan su programa`() {
        assertEquals(listOf(p1), changed(Programs.BUDGET, RecordChangeKind.CREATED, null, budget(p1)))
        assertEquals(listOf(p1), changed(Programs.BUDGET, RecordChangeKind.DELETED, budget(p1), null))
    }

    // la entrada 2 del Review Focus
    @Test
    fun `mover un presupuesto recalcula el programa de origen y el de destino`() {
        assertEquals(setOf(p1, p2), changed(Programs.BUDGET, RecordChangeKind.UPDATED, budget(p1), budget(p2)).toSet())
    }

    @Test
    fun `un cambio del ejecutado no recalcula nada`() {
        assertEquals(emptyList<UUID>(), changed(Programs.BUDGET, RecordChangeKind.UPDATED, budget(p1), budget(p1) + ("executed_amount" to 400)))
    }

    // la entrada 1 del Review Focus
    @Test
    fun `cambiar la moneda del programa lo recalcula y cambiar su nombre no`() {
        val program = mapOf<String, Any?>("currency" to "PEN", "name" to "Programa Ejemplo")
        assertEquals(listOf(p1), changed(Programs.PROGRAM, RecordChangeKind.UPDATED, program, program + ("currency" to "USD"), p1))
        assertEquals(emptyList<UUID>(), changed(Programs.PROGRAM, RecordChangeKind.UPDATED, program, program + ("name" to "Otro"), p1))
        assertEquals(listOf(p1), changed(Programs.PROGRAM, RecordChangeKind.CREATED, null, program, p1))
        assertEquals(emptyList<UUID>(), changed(Programs.PROGRAM, RecordChangeKind.TRANSITIONED, program, program, p1))
    }

    @Test
    fun `un escritor que falla no rompe la escritura`() {
        failing = true
        assertEquals(setOf(p1, p2), changed(Programs.BUDGET, RecordChangeKind.UPDATED, budget(p1), budget(p2)).toSet())
    }
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ProgramTotals*'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ProgramTotals'`, `'ProgramTotalsWriter'` y
`'ProgramTotalsListener'`).

- [ ] **Paso 3: La función, el escritor, el listener y el recalculador**

Crear `src/main/kotlin/sgspe/investment/ProgramTotals.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Amounts
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// el presupuesto total de un programa, puro (especificación de F5 §3.6, decisión 4, ASM-063): la suma de allocated_amount de
// sus presupuestos en la moneda del programa; los de otra moneda no se suman (S43 los muestra aparte) y el ejecutado no se
// deriva. sin moneda del programa no hay total; con moneda y sin presupuestos en ella, 0
object ProgramTotals {
    data class Budget(
        val allocated: BigDecimal?,
        val currency: String?
    )

    // los campos del presupuesto de los que depende el total
    private val SOURCES = listOf(Programs.PROGRAM, Programs.ALLOCATED, Programs.CURRENCY)

    fun total(
        currency: String?,
        budgets: List<Budget>
    ): BigDecimal? {
        if (currency == null) return null
        return budgets.filter { it.currency == currency }.mapNotNull { it.allocated }.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    // si el cambio de un presupuesto puede mover algún total: un alta y una baja siempre; un cambio solo si toca su programa, su
    // monto asignado o su moneda (el ejecutado registrado no lo mueve)
    fun affects(
        before: Map<String, Any?>?,
        after: Map<String, Any?>?
    ): Boolean = before == null || after == null || SOURCES.any { !Values.same(before[it], after[it]) }
}

// recalcula y escribe total_budget de un programa con Records (la marca SgspeWrite y el motivo), como quien escribió o como la
// plataforma. escribe solo si cambia: un valor igual no toca updatedAt ni deja historia. es una escritura de solo derivados
// (Records.patch, ADR-0027): el If-Match de quien editaba el programa sigue valiendo. la lectura y la escritura van dentro de
// Transactions.locked("social_program:<id>"): dos presupuestos que entran a la vez se suman los dos
@Component
class ProgramTotalsWriter(
    private val records: Records,
    private val transactions: Transactions
) {
    // true si escribió. reason: el motivo de la historia; el listener manda REASON y JOB-02 el que nombra al trabajo
    suspend fun refresh(
        program: UUID,
        reason: String
    ): Boolean =
        transactions.locked("${Programs.PROGRAM}:$program") {
            val current = records.find(Programs.PROGRAM, program)
            if (current == null) {
                false
            } else {
                val budgets =
                    records.all(Programs.BUDGET, filters = mapOf(Programs.PROGRAM to program.toString())).map {
                        ProgramTotals.Budget(
                            Amounts.parse(it.attributes[Programs.ALLOCATED], Programs.ALLOCATED),
                            Values.text(it.attributes[Programs.CURRENCY])
                        )
                    }
                val wanted = ProgramTotals.total(Values.text(current.attributes[Programs.CURRENCY]), budgets)
                if (Values.same(current.attributes[Programs.TOTAL_BUDGET], wanted)) {
                    false
                } else {
                    records.patch(Programs.PROGRAM, program, mapOf(Programs.TOTAL_BUDGET to wanted), reason)
                    true
                }
            }
        }

    companion object {
        const val REASON = "derivado: presupuesto total del programa"
    }
}

// ProgramTotalsListener (especificación de F5 §3.6, decisión 6 del plan 5.2b): al crear, cambiar o borrar un presupuesto
// recalcula su programa (los dos, si cambió de programa); al crear un programa o cambiar su moneda, ese programa. corre como
// quien escribió, dentro de la petición; si no puede, lo registra y la escritura sigue: JOB-02 corrige el valor en la hora
@Component
@Order(110)
class ProgramTotalsListener(
    private val writer: ProgramTotalsWriter
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind == RecordChangeKind.TRANSITIONED) return
        val programs =
            when (change.objectName) {
                Programs.BUDGET ->
                    if (ProgramTotals.affects(change.before, change.after)) {
                        setOfNotNull(Records.uuid(change.before?.get(Programs.PROGRAM)), Records.uuid(change.after?.get(Programs.PROGRAM)))
                    } else {
                        emptySet()
                    }
                Programs.PROGRAM ->
                    if (change.kind == RecordChangeKind.CREATED || !Values.same(change.before?.get(Programs.CURRENCY), change.after?.get(Programs.CURRENCY))) {
                        if (change.kind == RecordChangeKind.DELETED) emptySet() else setOf(change.recordId)
                    } else {
                        emptySet()
                    }
                else -> emptySet()
            }
        for (program in programs) {
            try {
                writer.refresh(program, ProgramTotalsWriter.REASON)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.warn("ProgramTotalsListener: no se actualizó total_budget del programa {}", program, e)
            }
        }
    }
}

// el recalculador de JOB-02 para total_budget: los programas cuyos presupuestos o cuya moneda cambiaron desde la vuelta anterior,
// o todos en la vuelta diaria (un presupuesto borrado solo lo ve la vuelta diaria)
@Component
class ProgramTotalsRecalculator(
    private val records: Records,
    private val writer: ProgramTotalsWriter
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Programs.PROGRAM

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val programs =
            if (since == null) {
                records.all(Programs.PROGRAM).map { UUID.fromString(it.id) }
            } else {
                val changed = listOf(Criteria.updatedSince(since))
                (
                    records.all(Programs.BUDGET, criteria = changed).mapNotNull { Records.uuid(it.attributes[Programs.PROGRAM]) } +
                        records.all(Programs.PROGRAM, criteria = changed).map { UUID.fromString(it.id) }
                ).distinct()
            }
        var written = 0
        var failed = 0
        for (program in programs) {
            try {
                if (writer.refresh(program, REASON)) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("ProgramTotalsRecalculator: no se recalculó total_budget del programa {}", program, e)
            }
        }
        check(failed == 0) { "no se recalculó total_budget de $failed de ${programs.size} programas (se escribieron $written)" }
        return written
    }

    companion object {
        // JOB-02 firma sus escrituras: «derivado: presupuesto total del programa (JOB-02 DerivedFieldsJob)»
        val REASON = DerivedFieldsJob.reason("presupuesto total del programa")
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.investment.ProgramTotals*' --tests 'sgspe.ArchitectureTest'
```

Esperado: `BUILD SUCCESSFUL`; `ProgramTotalsTest` con 3 pruebas y `ProgramTotalsListenerTest` con 5, en verde;
`ArchitectureTest` sigue en verde (ningún bean con el nombre simple de otro).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ProgramTotals.kt src/test/kotlin/sgspe/investment/ProgramTotalsTest.kt \
  src/test/kotlin/sgspe/investment/ProgramTotalsListenerTest.kt
git commit -F - <<'MSG'
feat: presupuesto total del programa con su listener y JOB-02

ProgramTotalsListener mantiene social_program.total_budget con los presupuestos en la moneda del
programa: al crear, cambiar o borrar un presupuesto, al moverlo de programa y al cambiar la moneda
del programa. ProgramTotalsRecalculator lo corrige en JOB-02 con el motivo del trabajo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** la cubre `SocialProgramWorkflowApiTest` «total_budget solo suma la moneda del
programa y sigue su cambio de moneda» y «JOB-02 corrige total_budget con el motivo del trabajo» (Tarea 9).

---

### Tarea 5: El aviso de aprobación de WF-G y las tareas de un programa

**Files:**
- Modify: `src/main/kotlin/sgspe/notification/ApprovalNotices.kt` (`STEPS`, `label`), `src/main/kotlin/sgspe/notification/Notices.kt`
  (`NoticeTexts.approvalPending`), `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt` (`TASK_REFERENCE`)
- Test: `src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt`, `src/test/kotlin/sgspe/deadline/ProgramTaskReferenceTest.kt` (crear)

**Interfaces:**
- Consumes: `Programs` (Tarea 1); del plan 5.2a, `ApprovalStep(objectName, submit: String?, pending, approvals, direct =
  emptyList())` y `ApprovalNoticeListener(notices, assignments, catalog, senior, records)` con WF-P en `STEPS`;
  `DeadlineNoticePlan.TASK_REFERENCE`; `task.social_program` (plan 5.1).
- Produces: `ApprovalNoticeListener.STEPS` con `ApprovalStep(Programs.PROGRAM, Programs.SUBMIT, Programs.PENDING,
  listOf(Programs.APPROVE))`; `ApprovalNoticeListener.label("social_program", …)` = «código · nombre»;
  `NoticeTexts.approvalPending("social_program", label)`; `DeadlineNoticePlan.TASK_REFERENCE["social_program"] =
  "social_program" to "close"`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt`, agregar antes de la última `}` de la clase:

```kotlin

    // especificación de F5 §3.8: el envío de un programa (submit de WF-G) avisa a los roles de approve, y el texto nombra el
    // programa por su código y su nombre, nunca por su descripción
    @Test
    fun `enviar un programa avisa a los roles de approve y se nombra por codigo y nombre`() {
        val asked = mutableListOf<String>()
        val assignments =
            mock(Assignments::class.java) { call: InvocationOnMock ->
                if (call.method.name == "recipients") {
                    asked += call.arguments[2] as String
                    emptyList<Any>()
                } else {
                    null
                }
            }
        val listener =
            ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, mock(SeniorApprovalReader::class.java), mock(Records::class.java))
        val change =
            RecordChange(
                organizationId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                objectId = UUID.randomUUID(),
                objectName = "social_program",
                recordId = UUID.randomUUID(),
                kind = RecordChangeKind.TRANSITIONED,
                after = mapOf("project" to UUID.randomUUID().toString(), "code" to "PRG-1", "name" to "Programa Ejemplo"),
                transition = "submit"
            )
        runBlocking { listener.recordChanged(change) }
        assertEquals(listOf("approve"), asked)
        assertEquals(
            "PRG-1 · Programa Ejemplo",
            ApprovalNoticeListener.label("social_program", mapOf("code" to "PRG-1", "name" to "Programa Ejemplo", "description" to "no va"))
        )
        assertEquals(
            "Programa por aprobar: PRG-1 · Programa Ejemplo" to "El programa social espera su aprobación.",
            NoticeTexts.approvalPending("social_program", "PRG-1 · Programa Ejemplo")
        )
    }
```

Si el archivo todavía no importa `sgspe.common.Records` (el plan 5.2a lo suma para el quinto parámetro del constructor), se
agrega el import en orden alfabético.

Crear `src/test/kotlin/sgspe/deadline/ProgramTaskReferenceTest.kt`:

```kotlin
package sgspe.deadline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import sgspe.common.ModelCatalog

// una tarea vinculada a un programa escala, en su nivel 1, a los roles de close de WF-G (workflows.md, «Plazos y
// escalamiento»; decisión 11 del plan 5.2b)
class ProgramTaskReferenceTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `una tarea de un programa escala a close de WF-G`() {
        val (objectName, transition) = DeadlineNoticePlan.TASK_REFERENCE.getValue("social_program")
        assertEquals("social_program", objectName)
        assertEquals("close", transition)
        assertNotNull(catalog.transition(objectName, transition))
        // el campo de la tarea por el que DeadlineNotices reconoce el origen existe en el modelo
        assertEquals("social_program", catalog.relationField("task", "social_program"))
    }
}
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.notification.ApprovalNoticeListenerTest' --tests 'sgspe.deadline.ProgramTaskReferenceTest'
```

Esperado: FAIL: `asked` vacío (WF-G no está en `STEPS`), el texto genérico «Aprobación pendiente» y `NoSuchElementException`
en `TASK_REFERENCE`.

- [ ] **Paso 3: El paso de WF-G, su texto y la referencia de la tarea**

En `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`, agregar `import sgspe.investment.Programs` entre los imports
(en orden alfabético, después de los de `sgspe.common`). En `STEPS`, agregar como último elemento de la lista, después del de
WF-P que sumó 5.2a (con la coma que pida la lista):

```kotlin
                // F5: WF-G (especificación §3.8): al enviar, para los roles de approve; se resuelve al salir de pending_approval
                ApprovalStep(Programs.PROGRAM, Programs.SUBMIT, Programs.PENDING, listOf(Programs.APPROVE))
```

En `label`, reemplazar:

```kotlin
                PROJECT -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["name"]))
```

por:

```kotlin
                PROJECT, Programs.PROGRAM -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["name"]))
```

En `src/main/kotlin/sgspe/notification/Notices.kt`, en `approvalPending`, después de la línea

```kotlin
            "commitment" -> "Compromiso por aprobar: $label" to "El compromiso espera su aprobación."
```

agregar:

```kotlin
            "social_program" -> "Programa por aprobar: $label" to "El programa social espera su aprobación."
```

En `src/main/kotlin/sgspe/deadline/DeadlineNotices.kt`, en `TASK_REFERENCE`, reemplazar:

```kotlin
            "early_warning" to ("early_warning" to "confirm")
        )
```

por:

```kotlin
            "early_warning" to ("early_warning" to "confirm"),
            // F5: una tarea de un programa escala a quien lo cierra (workflows.md, «Plazos y escalamiento»)
            "social_program" to ("social_program" to "close")
        )
```

Si el plan 5.2a ya sumó una entrada después de `early_warning`, la de `social_program` va al final de la lista con la misma
forma.

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.notification.*' --tests 'sgspe.deadline.*'
```

Esperado: `BUILD SUCCESSFUL`; la prueba nueva de `ApprovalNoticeListenerTest`, `ProgramTaskReferenceTest` y la vecina «cada
paso de STEPS existe en model workflows…» (que ahora también lee `model/workflows/social_program.json`) en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/notification/ApprovalNotices.kt src/main/kotlin/sgspe/notification/Notices.kt \
  src/main/kotlin/sgspe/deadline/DeadlineNotices.kt src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt \
  src/test/kotlin/sgspe/deadline/ProgramTaskReferenceTest.kt
git commit -F - <<'MSG'
feat: aviso de aprobación de WF-G y escalamiento de las tareas de un programa

Enviar un programa a aprobación avisa a los asignados con un rol de approve, con su código y su
nombre, y el aviso se resuelve al salir de pending_approval; una tarea vencida de un programa
escala en su nivel 1 a los roles de close de WF-G.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** la cubre `SocialProgramWorkflowApiTest` «el envío avisa a los APPROVER asignados y
la aprobación resuelve el aviso» (Tarea 9).

---
### Tarea 6: Los filtros `program` y `year` del tablero de inversión

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`, `src/main/kotlin/sgspe/dashboard/Dashboards.kt`,
  `src/main/kotlin/sgspe/dashboard/Kpis.kt` (`ignoredFilters` y `extraFilters`)
- Test: `src/test/kotlin/sgspe/dashboard/DashboardFiltersTest.kt`

**Interfaces:**
- Consumes: `DashboardFilters`, `Kpis.ignoredFilters`, `Dashboards` (F2 a F4).
- Produces: `DashboardFilters.program: UUID?` y `year: Int?` (con valor por defecto `null`, al final del constructor);
  `DashboardFilters.INVESTMENT: Set<String>` (`program`, `year`), `MIN_YEAR = 2000`, `MAX_YEAR = 2100`;
  `Kpis.extraFilters(codes: Collection<String>): Set<String>` (`INVESTMENT` si algún código es `KPI-INV-*`);
  `Kpis.ignoredFilters` mira también `INVESTMENT`.

Decisión 2: `program` y `year` los acepta solo quien los permite (el tablero `investment` y los KPI-INV); en otro tablero o
KPI son `422`, como cualquier parámetro desconocido. `year` es un entero de 2000 a 2100; `program`, un UUID; si no, `400`.

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/dashboard/DashboardFiltersTest.kt`, agregar después de la función `parse`:

```kotlin

    private fun parseInvestment(vararg pairs: Pair<String, String>): DashboardFilters =
        runBlocking {
            val params = LinkedMultiValueMap<String, String>()
            pairs.forEach { (k, v) -> params.add(k, v) }
            DashboardFilters.parse(params, DashboardFilters.COMMON + DashboardFilters.INVESTMENT, catalog, today) { listOf(assigned) }.resolve()
        }
```

y antes de la última `}` de la clase:

```kotlin

    // la entrada 5 del Review Focus: fuera del tablero investment, program y year son desconocidos (422), nunca un filtro que
    // parece aplicado
    @Test
    fun `program y year solo los acepta quien los permite y viajan en el eco`() {
        assertThrows<UnprocessableContentException> { parse("program" to UUID.randomUUID().toString()) }
        assertThrows<UnprocessableContentException> { parse("year" to "2026") }
        val program = UUID.randomUUID()
        val filters = parseInvestment("program" to program.toString(), "year" to "2026")
        assertEquals(program, filters.program)
        assertEquals(2026, filters.year)
        assertEquals(program.toString(), filters.echo()["program"])
        assertEquals(2026, filters.echo()["year"])
        assertEquals(setOf("program", "year"), filters.given())
        assertEquals(null, parseInvestment().year)
    }

    @Test
    fun `year es un anio entero de 2000 a 2100 y program un UUID`() {
        assertEquals(2000, parseInvestment("year" to "2000").year)
        assertEquals(2100, parseInvestment("year" to "2100").year)
        for (wrong in listOf("1999", "2101", "2026.5", "dos mil")) {
            val error = assertThrows<ValidationException> { parseInvestment("year" to wrong) }
            assertEquals(listOf("year"), error.violations.map { it.field }, wrong)
        }
        assertEquals(listOf("program"), assertThrows<ValidationException> { parseInvestment("program" to "x") }.violations.map { it.field })
    }

    @Test
    fun `los KPI-INV piden program y year y los demas no`() {
        assertEquals(DashboardFilters.INVESTMENT, Kpis.extraFilters(listOf("KPI-INV-02")))
        assertEquals(DashboardFilters.INVESTMENT, Kpis.extraFilters(listOf("KPI-INV-01", "KPI-INV-04")))
        assertEquals(emptySet<String>(), Kpis.extraFilters(listOf("KPI-COM-01", "KPI-RSK-01")))
    }
```

- [ ] **Paso 2: Correr las pruebas para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.DashboardFiltersTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'INVESTMENT'`, `'program'`, `'year'` y `'extraFilters'`).

- [ ] **Paso 3: Los filtros**

En `src/main/kotlin/sgspe/dashboard/DashboardFilters.kt`:

Reemplazar:

```kotlin
    // F3: el responsable de un compromiso (owner_user_id), en los KPI-COM que lo aceptan (dashboards.md §4.4)
    val owner: UUID? = null
) {
```

por:

```kotlin
    // F3: el responsable de un compromiso (owner_user_id), en los KPI-COM que lo aceptan (dashboards.md §4.4)
    val owner: UUID? = null,
    // F5: el programa y el año presupuestal, solo en el tablero investment y en los KPI-INV (INVESTMENT, decisión 2 del plan 5.2b)
    val program: UUID? = null,
    val year: Int? = null
) {
```

Reemplazar:

```kotlin
            "owner" to owner?.toString()
        ).filterValues { it != null }
```

por:

```kotlin
            "owner" to owner?.toString(),
            "program" to program?.toString(),
            "year" to year
        ).filterValues { it != null }
```

Reemplazar:

```kotlin
            owner?.let { "owner" }
        ).toSet()
```

por:

```kotlin
            owner?.let { "owner" },
            program?.let { "program" },
            year?.let { "year" }
        ).toSet()
```

Reemplazar:

```kotlin
                "scope",
                "owner"
            )

        fun parse(
```

por:

```kotlin
                "scope",
                "owner"
            )

        // F5: los filtros que solo aceptan el tablero investment y los KPI-INV (Kpis.extraFilters); en otro, 422
        val INVESTMENT: Set<String> = setOf("program", "year")

        // el año presupuestal, como program_budget.budget_year (ProgramRules)
        const val MIN_YEAR = 2000
        const val MAX_YEAR = 2100

        fun parse(
```

Reemplazar:

```kotlin
            if (scope != null && scopes.isNotEmpty() && scope !in scopes) violations += FieldViolation("scope", "debe ser uno de ${scopes.joinToString(", ")}")
            val pending =
```

por:

```kotlin
            if (scope != null && scopes.isNotEmpty() && scope !in scopes) violations += FieldViolation("scope", "debe ser uno de ${scopes.joinToString(", ")}")
            val year =
                params.getFirst("year")?.let { raw ->
                    raw.trim().toIntOrNull()?.takeIf { it in MIN_YEAR..MAX_YEAR }
                        ?: run {
                            violations += FieldViolation("year", "debe ser un año entero de $MIN_YEAR a $MAX_YEAR")
                            null
                        }
                }
            val pending =
```

Reemplazar:

```kotlin
                    uuid("owner"),
                    defaultProjects
                )
```

por:

```kotlin
                    uuid("owner"),
                    uuid("program"),
                    year,
                    defaultProjects
                )
```

Reemplazar:

```kotlin
        private val owner: UUID?,
        private val defaults: suspend () -> List<UUID>
```

por:

```kotlin
        private val owner: UUID?,
        private val program: UUID?,
        private val year: Int?,
        private val defaults: suspend () -> List<UUID>
```

Reemplazar:

```kotlin
                breakdown,
                owner
            )
    }
```

por:

```kotlin
                breakdown,
                owner,
                program,
                year
            )
    }
```

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, reemplazar:

```kotlin
    // los filtros pedidos que el indicador ignora, en el orden de DashboardFilters.COMMON; vacío si ninguno
    fun ignoredFilters(
        indicator: Indicator,
        filters: DashboardFilters
    ): List<String> = DashboardFilters.COMMON.filter { it in indicator.ignores && it in filters.given() }
```

por:

```kotlin
    // los filtros pedidos que el indicador ignora, en el orden de DashboardFilters.COMMON y después INVESTMENT; vacío si ninguno
    fun ignoredFilters(
        indicator: Indicator,
        filters: DashboardFilters
    ): List<String> = (DashboardFilters.COMMON + DashboardFilters.INVESTMENT).filter { it in indicator.ignores && it in filters.given() }

    // los filtros que, además de los comunes, aceptan los indicadores `codes`: program y year si alguno es un KPI-INV (decisión 2
    // del plan 5.2b); ninguno en los demás, así que en otro tablero o KPI son 422
    fun extraFilters(codes: Collection<String>): Set<String> =
        if (codes.any { it.startsWith(INVESTMENT_PREFIX) }) DashboardFilters.INVESTMENT else emptySet()

    private const val INVESTMENT_PREFIX = "KPI-INV-"
```

En `src/main/kotlin/sgspe/dashboard/Dashboards.kt`, reemplazar:

```kotlin
        val filters = filters(params, DashboardFilters.COMMON, today)
```

por:

```kotlin
        val filters = filters(params, DashboardFilters.COMMON + Kpis.extraFilters(codes), today)
```

y reemplazar:

```kotlin
        val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN, today)
```

por:

```kotlin
        val filters = filters(params, DashboardFilters.COMMON + BREAKDOWN + Kpis.extraFilters(listOf(code)), today)
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.dashboard.*'
```

Esperado: `BUILD SUCCESSFUL`; las 3 pruebas nuevas de `DashboardFiltersTest` en verde y las demás de `sgspe.dashboard` como en
`main` (`DashboardDataTest` construye `DashboardFilters` con diez argumentos: los dos nuevos tienen valor por defecto).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/dashboard/DashboardFilters.kt src/main/kotlin/sgspe/dashboard/Dashboards.kt \
  src/main/kotlin/sgspe/dashboard/Kpis.kt src/test/kotlin/sgspe/dashboard/DashboardFiltersTest.kt
git commit -F - <<'MSG'
feat: filtros program y year del tablero de inversión

Los aceptan solo el tablero investment y los KPI-INV; en otro tablero son 422 como cualquier
parámetro desconocido. year es un año entero de 2000 a 2100 y program un UUID; los dos viajan en
el eco de los filtros e ignoredFilters los nombra si un KPI no los aplica.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 7: KPI-INV-01 a 04, puros (`InvestmentKpis`)

**Files:**
- Create: `src/main/kotlin/sgspe/dashboard/InvestmentKpis.kt`
- Modify: `src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt` (`CommitmentRow.beneficiaries`)
- Test: `src/test/kotlin/sgspe/dashboard/InvestmentKpisTest.kt`

**Interfaces:**
- Consumes: `IndicatorResult`, `Breakdown`, `Numbers.percent`, `CommitmentRow`, `CommitmentKpis.DEFAULT_CURRENCY`,
  `Commitments.FULFILLED`, `Amounts.MAX_SCALE`; `Contributions.EXECUTED` (plan 5.2a: `delivered` y `closed`).
- Produces: `CommitmentRow.beneficiaries: Long?` (último parámetro, por defecto `null`); las filas `CampaignRow(id, project,
  name, cap, currency, executed, otherCurrencyItems: Int)`, `CommittedAmount(campaign, amount, currency)`, `ProgramRow(id,
  project, code, name, currency, state = null)`, `BudgetRow(program, year: Int?, allocated, executed, currency)`, `GroupRow(program,
  target: Long?, reached: Long?)`, `ContributionRow(id, project, state, total, currency, beneficiaries: Long?)`,
  `DeliveryRow(parent, date: LocalDate?, amount)`; `object InvestmentKpis` con `PEN`, `COMMITMENTS`, `CONTRIBUTIONS`,
  `PROGRAMS`, `EXECUTED: Set<String>` y `campaigns(campaigns, committed): IndicatorResult`, `programs(programs, budgets,
  year: Int?): IndicatorResult`, `executed(commitments, commitmentDeliveries, contributions, contributionDeliveries, programs,
  budgets, campaigns, start: LocalDate, end: LocalDate, withPrograms: Boolean, projectCodes: Map<String, String>):
  IndicatorResult`, `beneficiaries(programs, groups, contributions, commitments): IndicatorResult`.

La cifra titular sigue la decisión 3 (PEN por defecto, `dashboards.md` §3.3); los montos salen con la escala de `Amounts`
(4 decimales) y los porcentajes con uno (`Numbers.percent`).

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/InvestmentKpisTest.kt`:

```kotlin
package sgspe.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

// los KPI-INV (dashboards.md §4.7, especificación de F5 §3.9, decisión 3 del plan 5.2b), sin base: la moneda de cada cifra,
// lo que no se suma y los desgloses
class InvestmentKpisTest {
    private fun amount(text: String?) = text?.let(::BigDecimal)

    private fun same(
        expected: String,
        actual: Any?
    ) = assertEquals(0, BigDecimal(expected).compareTo(actual as BigDecimal), "$actual")

    private fun commitment(
        id: String,
        state: String,
        currency: String? = "PEN",
        beneficiaries: Long? = null
    ) = CommitmentRow(
        id = id,
        project = "p1",
        state = state,
        ownerEmail = null,
        locality = null,
        community = null,
        topic = null,
        theme = null,
        formality = null,
        agreedDate = null,
        dueDate = null,
        fulfilledDate = null,
        cancelledDate = null,
        deadlineStatus = null,
        amount = null,
        currency = currency,
        executed = null,
        beneficiaries = beneficiaries
    )

    private fun rows(
        result: IndicatorResult,
        by: String
    ) = result.breakdowns.first { it.by == by }.rows

    @Test
    fun `KPI-INV-01 compara lo comprometido y lo ejecutado de cada campana con su tope, en su moneda`() {
        val campaigns =
            listOf(
                CampaignRow("c1", "p1", "Campaña A", amount("10000"), "PEN", amount("2500"), 1),
                CampaignRow("c2", "p1", "Campaña B", amount("500"), "USD", amount("0"), 0),
                CampaignRow("c3", "p1", "Campaña C", null, "PEN", null, 0)
            )
        val committed =
            listOf(
                CommittedAmount("c1", amount("3000"), "PEN"),
                CommittedAmount("c1", amount("2000"), "PEN"),
                CommittedAmount("c1", amount("400"), "USD"),
                CommittedAmount("c1", null, "PEN"),
                CommittedAmount("c2", amount("100"), "USD")
            )
        val result = InvestmentKpis.campaigns(campaigns, committed)
        // la cifra: lo ejecutado sobre el tope de las campañas en PEN con tope (solo c1)
        assertEquals(BigDecimal("25.0"), result.value)
        same("2500", result.numerator)
        same("10000", result.denominator)
        val byKey = rows(result, "campaign").associateBy { it["key"] }
        assertEquals(listOf("c1", "c2", "c3"), rows(result, "campaign").map { it["key"] })
        same("5000", byKey.getValue("c1")["committed"])
        assertEquals(BigDecimal("50.0"), byKey.getValue("c1")["committedPercent"])
        assertEquals(BigDecimal("25.0"), byKey.getValue("c1")["executedPercent"])
        assertEquals(1, byKey.getValue("c1")["committedOtherCurrencyCount"])
        assertEquals(1, byKey.getValue("c1")["otherCurrencyCount"])
        same("100", byKey.getValue("c2")["committed"])
        assertEquals(BigDecimal("20.0"), byKey.getValue("c2")["committedPercent"])
        assertNull(byKey.getValue("c3")["committedPercent"])
        assertEquals(1, result.warnings.size)
    }

    @Test
    fun `KPI-INV-01 sin campanas en PEN no tiene cifra y una campana sin moneda no compara lo comprometido`() {
        val result =
            InvestmentKpis.campaigns(
                listOf(CampaignRow("c1", "p1", "Campaña A", amount("100"), null, null, 0)),
                listOf(CommittedAmount("c1", amount("50"), "PEN"))
            )
        assertNull(result.value)
        val row = rows(result, "campaign").single()
        assertNull(row["committed"])
        assertEquals(1, row["committedOtherCurrencyCount"])
    }

    @Test
    fun `KPI-INV-02 da el avance por programa, anio y moneda y la cifra solo en PEN`() {
        val programs = listOf(ProgramRow("g1", "p1", "PRG-1", "Programa Ejemplo", "PEN", "executing"), ProgramRow("g2", "p1", "PRG-2", "Otro programa", "PEN"))
        val budgets =
            listOf(
                BudgetRow("g1", 2026, amount("1000"), amount("250"), "PEN"),
                BudgetRow("g1", 2027, amount("400"), amount("100"), "USD"),
                BudgetRow("g2", 2026, amount("3000"), amount("750"), "PEN"),
                BudgetRow("gx", 2026, amount("9999"), amount("9999"), "PEN")
            )
        val all = InvestmentKpis.programs(programs, budgets, null)
        assertEquals(BigDecimal("25.0"), all.value)
        assertEquals(listOf("g1:2026:PEN", "g1:2027:USD", "g2:2026:PEN"), rows(all, "program").map { it["key"] })
        assertEquals("PRG-1 · Programa Ejemplo", rows(all, "program").first()["label"])
        assertEquals(BigDecimal("25.0"), rows(all, "program").first()["percent"])
        assertEquals("executing", rows(all, "program").first()["state"])
        assertEquals(1, all.warnings.size)
        val only2027 = InvestmentKpis.programs(programs, budgets, 2027)
        assertNull(only2027.value)
        assertEquals(listOf("g1:2027:USD"), rows(only2027, "program").map { it["key"] })
    }

    @Test
    fun `KPI-INV-03 suma lo ejecutado del periodo por fuente y por proyecto y aparte las otras monedas`() {
        val start = LocalDate.parse("2026-10-01")
        val end = LocalDate.parse("2026-10-31")
        val commitments = listOf(commitment("m1", "in_progress"), commitment("m2", "in_progress", "USD"))
        val commitmentDeliveries =
            listOf(
                DeliveryRow("m1", LocalDate.parse("2026-10-05"), amount("700")),
                DeliveryRow("m1", LocalDate.parse("2026-09-30"), amount("999")),
                DeliveryRow("m2", LocalDate.parse("2026-10-10"), amount("50"))
            )
        val contributions =
            listOf(
                ContributionRow("s1", "p1", "delivered", amount("2000"), "PEN", 30),
                ContributionRow("s2", "p1", "closed", amount("300"), "PEN", 5),
                ContributionRow("s3", "p1", "procured", amount("900"), "PEN", 9)
            )
        val contributionDeliveries =
            listOf(
                DeliveryRow("s1", LocalDate.parse("2026-10-10"), null),
                DeliveryRow("s2", LocalDate.parse("2026-11-02"), null),
                DeliveryRow("s3", LocalDate.parse("2026-10-12"), null)
            )
        val programs = listOf(ProgramRow("g1", "p1", "PRG-1", "Programa Ejemplo", "PEN"))
        val budgets = listOf(BudgetRow("g1", 2026, amount("1000"), amount("250"), "PEN"), BudgetRow("g1", 2025, amount("800"), amount("800"), "PEN"))
        val campaigns = listOf(CampaignRow("c1", "p1", "Campaña A", amount("10000"), "PEN", null, 0))
        val codes = mapOf("p1" to "PRY-1")
        val result =
            InvestmentKpis.executed(
                commitments, commitmentDeliveries, contributions, contributionDeliveries,
                programs, budgets, campaigns, start, end, true, codes
            )
        same("2950", result.value)
        assertEquals(
            listOf("COMPROMISOS:PEN", "COMPROMISOS:USD", "APORTES:PEN", "PROGRAMAS:PEN"),
            rows(result, "source").map { it["key"] }
        )
        val pen = rows(result, "project").first { it["key"] == "p1:PEN" }
        assertEquals("PRY-1", pen["label"])
        same("2950", pen["executed"])
        same("700", pen["commitments"])
        same("2000", pen["contributions"])
        same("250", pen["programs"])
        same("1000", pen["budget"])
        same("10000", pen["cap"])
        same("50", rows(result, "project").first { it["key"] == "p1:USD" }["executed"])
        assertEquals(1, result.warnings.size)
        // con un filtro de localidad los programas, que no tienen territorio, no se suman y se advierte
        val local =
            InvestmentKpis.executed(
                commitments, commitmentDeliveries, contributions, contributionDeliveries,
                emptyList(), emptyList(), campaigns, start, end, false, codes
            )
        same("2700", local.value)
        assertTrue(local.warnings.any { it.startsWith("Los programas") }, "${local.warnings}")
    }

    @Test
    fun `KPI-INV-04 da tres bloques de beneficiarios sin sumarlos`() {
        val programs = listOf(ProgramRow("g1", "p1", "PRG-1", "Programa Ejemplo", "PEN"), ProgramRow("g2", "p1", "PRG-2", "Otro programa", "PEN"))
        val groups = listOf(GroupRow("g1", 100, 40), GroupRow("g1", 50, null), GroupRow("g2", null, 5), GroupRow("gx", 1000, 1000))
        val contributions =
            listOf(
                ContributionRow("s1", "p1", "delivered", null, "PEN", 30),
                ContributionRow("s2", "p1", "closed", null, "PEN", 20),
                ContributionRow("s3", "p1", "approved", null, "PEN", 99)
            )
        val commitments =
            listOf(
                commitment("m1", "fulfilled", beneficiaries = 12),
                commitment("m2", "verified", beneficiaries = 8),
                commitment("m3", "in_progress", beneficiaries = 100)
            )
        val result = InvestmentKpis.beneficiaries(programs, groups, contributions, commitments)
        assertEquals(45L, result.value)
        val sources = rows(result, "source").associateBy { it["key"] }
        assertEquals(45L, sources.getValue("PROGRAMAS")["value"])
        assertEquals(150L, sources.getValue("PROGRAMAS")["target"])
        assertEquals(50L, sources.getValue("APORTES")["value"])
        assertEquals(2, sources.getValue("APORTES")["count"])
        assertEquals(20L, sources.getValue("COMPROMISOS")["value"])
        val g1 = rows(result, "program").first { it["key"] == "g1" }
        assertEquals(150L, g1["target"])
        assertEquals(40L, g1["reached"])
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.InvestmentKpisTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'InvestmentKpis'`, `'CampaignRow'` y el parámetro `beneficiaries`
de `CommitmentRow`).

- [ ] **Paso 3: Las filas y los cuatro KPI**

En `src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt`, reemplazar:

```kotlin
    val currency: String?,
    val executed: BigDecimal?
)
```

por:

```kotlin
    val currency: String?,
    val executed: BigDecimal?,
    // F5: los beneficiarios del compromiso (KPI-INV-04); los KPI-COM no lo leen
    val beneficiaries: Long? = null
)
```

Crear `src/main/kotlin/sgspe/dashboard/InvestmentKpis.kt`:

```kotlin
package sgspe.dashboard

import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.investment.Contributions
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// una campaña como la leen los KPI-INV: su tope, su moneda y su ejecutado derivado (CampaignExecutionRecalculator, ASM-063)
data class CampaignRow(
    val id: String,
    val project: String?,
    val name: String?,
    val cap: BigDecimal?,
    val currency: String?,
    val executed: BigDecimal?,
    val otherCurrencyItems: Int
)

// un monto comprometido de una campaña: un compromiso o un aporte en su conjunto «comprometido» (dashboards.md §3.2)
data class CommittedAmount(
    val campaign: String,
    val amount: BigDecimal?,
    val currency: String?
)

// `state`: el estado de WF-G, para que el portal no muestre ejecución de un programa por aprobar (S43, plan 5.3)
data class ProgramRow(
    val id: String,
    val project: String?,
    val code: String?,
    val name: String?,
    val currency: String?,
    val state: String? = null
)

// un presupuesto anual de un programa, con su ejecutado registrado a mano (decisión 4 de la especificación, ASM-063)
data class BudgetRow(
    val program: String,
    val year: Int?,
    val allocated: BigDecimal?,
    val executed: BigDecimal?,
    val currency: String?
)

data class GroupRow(
    val program: String,
    val target: Long?,
    val reached: Long?
)

data class ContributionRow(
    val id: String,
    val project: String?,
    val state: String?,
    val total: BigDecimal?,
    val currency: String?,
    val beneficiaries: Long?
)

// una entrega de un compromiso o de un aporte (`parent`), con su fecha y su monto entregado (vacío en las de aportes)
data class DeliveryRow(
    val parent: String,
    val date: LocalDate?,
    val amount: BigDecimal?
)

// los KPI-INV (dashboards.md §4.7, especificación de F5 §3.9) como funciones puras. las filas llegan leídas como quien mira y
// acotadas a los proyectos y, donde aplica, a la localidad. solo se suman montos de una misma moneda: la cifra titular va en PEN
// (dashboards.md §3.3, decisión 3 del plan 5.2b) y cada desglose lleva su moneda; lo que está en otra se advierte (ASM-063)
object InvestmentKpis {
    const val PEN = CommitmentKpis.DEFAULT_CURRENCY
    const val COMMITMENTS = "COMPROMISOS"
    const val CONTRIBUTIONS = "APORTES"
    const val PROGRAMS = "PROGRAMAS"

    // los estados en que un aporte cuenta como ejecutado (dashboards.md §3.2, ASM-063), los mismos de Contributions (plan 5.2a)
    val EXECUTED: Set<String> = Contributions.EXECUTED

    private val SOURCES = listOf(COMMITMENTS, CONTRIBUTIONS, PROGRAMS)

    // un monto de una fuente en un proyecto
    private data class Amount(
        val project: String,
        val source: String,
        val amount: BigDecimal?,
        val currency: String?
    )

    // KPI-INV-01: por campaña, el tope, lo comprometido (en la moneda de la campaña) y lo ejecutado; la cifra, lo ejecutado
    // sobre el tope de las campañas en PEN que tienen tope
    fun campaigns(
        campaigns: List<CampaignRow>,
        committed: List<CommittedAmount>
    ): IndicatorResult {
        val byCampaign = committed.filter { it.amount != null }.groupBy { it.campaign }
        val rows =
            campaigns.sortedWith(compareBy<CampaignRow>({ it.name.orEmpty() }, { it.id })).map { c ->
                val own = byCampaign[c.id].orEmpty()
                val same = if (c.currency == null) emptyList() else own.filter { it.currency == c.currency }
                val amount = if (c.currency == null) null else sum(same.map { it.amount })
                val executed = c.executed?.let(::scaled)
                linkedMapOf<String, Any?>(
                    "key" to c.id,
                    "label" to (c.name ?: c.id),
                    "currency" to c.currency,
                    "cap" to c.cap?.let(::scaled),
                    "committed" to amount,
                    "executed" to executed,
                    "committedPercent" to percentOf(amount, c.cap),
                    "executedPercent" to percentOf(executed, c.cap),
                    "committedOtherCurrencyCount" to (own.size - same.size),
                    "otherCurrencyCount" to c.otherCurrencyItems
                )
            }
        val pen = campaigns.filter { it.currency == PEN && it.cap != null }
        val cap = sum(pen.map { it.cap })
        val executed = sum(pen.map { it.executed })
        val other = rows.sumOf { (it["committedOtherCurrencyCount"] as Int) + (it["otherCurrencyCount"] as Int) }
        return IndicatorResult(
            if (pen.isEmpty()) null else Numbers.percent(executed, cap),
            numerator = if (pen.isEmpty()) null else executed,
            denominator = if (pen.isEmpty()) null else cap,
            breakdowns = listOf(Breakdown("campaign", rows)),
            warnings = if (other > 0) listOf("$other montos en una moneda distinta de la de su campaña no se suman (ASM-063)") else emptyList()
        )
    }

    // KPI-INV-02: por programa, año y moneda, el asignado y el ejecutado registrado; con `year`, solo ese año. la cifra, el
    // ejecutado sobre el asignado de los presupuestos en PEN
    fun programs(
        programs: List<ProgramRow>,
        budgets: List<BudgetRow>,
        year: Int?
    ): IndicatorResult {
        val byId = programs.associateBy { it.id }
        val chosen = budgets.filter { it.program in byId && it.currency != null && (year == null || it.year == year) }
        val rows =
            chosen
                .groupBy { Triple(it.program, it.year, it.currency!!) }
                .entries
                .sortedWith(compareBy({ label(byId[it.key.first]) }, { it.key.second ?: 0 }, { it.key.third }))
                .map { (key, list) ->
                    val allocated = sum(list.map { it.allocated })
                    val executed = sum(list.map { it.executed })
                    linkedMapOf<String, Any?>(
                        "key" to "${key.first}:${key.second}:${key.third}",
                        "program" to key.first,
                        "label" to label(byId[key.first]),
                        "year" to key.second,
                        "currency" to key.third,
                        "allocated" to allocated,
                        "executed" to executed,
                        "percent" to Numbers.percent(executed, allocated),
                        "state" to byId[key.first]?.state
                    )
                }
        val pen = chosen.filter { it.currency == PEN }
        val allocated = sum(pen.map { it.allocated })
        val executed = sum(pen.map { it.executed })
        val other = chosen.size - pen.size
        return IndicatorResult(
            if (pen.isEmpty()) null else Numbers.percent(executed, allocated),
            numerator = if (pen.isEmpty()) null else executed,
            denominator = if (pen.isEmpty()) null else allocated,
            breakdowns = listOf(Breakdown("program", rows)),
            warnings = if (other > 0) listOf("$other presupuestos en una moneda distinta de PEN no se suman a la cifra (ASM-063)") else emptyList()
        )
    }

    // KPI-INV-03: lo ejecutado en el periodo por fuente: el delivered_amount de las entregas del periodo de los compromisos (en la
    // moneda del compromiso), el total_amount de los aportes ejecutados cuya entrega cae en el periodo y el executed_amount de los
    // presupuestos de los años que cruzan el periodo; por proyecto y moneda, frente al asignado de esos presupuestos y a los
    // topes de sus campañas. sin `withPrograms` (filtro de localidad) los programas no se suman. la cifra, lo ejecutado en PEN
    fun executed(
        commitments: List<CommitmentRow>,
        commitmentDeliveries: List<DeliveryRow>,
        contributions: List<ContributionRow>,
        contributionDeliveries: List<DeliveryRow>,
        programs: List<ProgramRow>,
        budgets: List<BudgetRow>,
        campaigns: List<CampaignRow>,
        start: LocalDate,
        end: LocalDate,
        withPrograms: Boolean,
        projectCodes: Map<String, String>
    ): IndicatorResult {
        fun inPeriod(date: LocalDate?) = date != null && !date.isBefore(start) && !date.isAfter(end)
        val byCommitment = commitments.associateBy { it.id }
        val done = mutableListOf<Amount>()
        for (delivery in commitmentDeliveries.filter { inPeriod(it.date) }) {
            val c = byCommitment[delivery.parent] ?: continue
            done += Amount(c.project.orEmpty(), COMMITMENTS, delivery.amount, c.currency)
        }
        val delivered = contributionDeliveries.filter { inPeriod(it.date) }.map { it.parent }.toSet()
        for (s in contributions.filter { it.state in EXECUTED && it.id in delivered }) {
            done += Amount(s.project.orEmpty(), CONTRIBUTIONS, s.total, s.currency)
        }
        val byProgram = programs.associateBy { it.id }
        val years = start.year..end.year
        val inYears = if (withPrograms) budgets.filter { it.program in byProgram && it.year != null && it.year in years } else emptyList()
        for (b in inYears) done += Amount(byProgram[b.program]?.project.orEmpty(), PROGRAMS, b.executed, b.currency)
        val budget = inYears.map { Amount(byProgram[it.program]?.project.orEmpty(), PROGRAMS, it.allocated, it.currency) }
        val caps = campaigns.map { Amount(it.project.orEmpty(), "", it.cap, it.currency) }
        val shown = done.filter { it.amount != null && it.currency != null }
        val sources =
            shown
                .groupBy { it.source to it.currency!! }
                .entries
                .sortedWith(compareBy({ SOURCES.indexOf(it.key.first) }, { it.key.second }))
                .map { (key, list) ->
                    linkedMapOf<String, Any?>(
                        "key" to "${key.first}:${key.second}",
                        "source" to key.first,
                        "currency" to key.second,
                        "value" to sum(list.map { it.amount })
                    )
                }
        val keys =
            (shown + budget + caps)
                .filter { it.currency != null }
                .map { it.project to it.currency!! }
                .distinct()
                .sortedWith(compareBy({ projectCodes[it.first] ?: it.first }, { it.second }))
        val projects =
            keys.map { (project, currency) ->
                fun of(
                    list: List<Amount>,
                    source: String? = null
                ) = sum(list.filter { it.project == project && it.currency == currency && (source == null || it.source == source) }.map { it.amount })
                linkedMapOf<String, Any?>(
                    "key" to "$project:$currency",
                    "project" to project,
                    "label" to (projectCodes[project] ?: project),
                    "currency" to currency,
                    "executed" to of(shown),
                    "commitments" to of(shown, COMMITMENTS),
                    "contributions" to of(shown, CONTRIBUTIONS),
                    "programs" to of(shown, PROGRAMS),
                    "budget" to of(budget),
                    "cap" to of(caps)
                )
            }
        val other = shown.count { it.currency != PEN }
        val warnings =
            listOfNotNull(
                if (other > 0) "$other montos ejecutados en una moneda distinta de PEN no se suman a la cifra (ASM-063)" else null,
                if (withPrograms) null else "Los programas no tienen territorio: con un filtro de localidad no se suman"
            )
        return IndicatorResult(
            sum(shown.filter { it.currency == PEN }.map { it.amount }),
            breakdowns = listOf(Breakdown("source", sources), Breakdown("project", projects)),
            warnings = warnings
        )
    }

    // KPI-INV-04: tres bloques que no se suman, porque una persona puede estar en varios (dashboards.md §4.7): los alcanzados y la
    // meta de los grupos de beneficiarios de cada programa, los beneficiarios de los aportes ejecutados y los de los compromisos
    // cumplidos. la cifra, los alcanzados de los programas
    fun beneficiaries(
        programs: List<ProgramRow>,
        groups: List<GroupRow>,
        contributions: List<ContributionRow>,
        commitments: List<CommitmentRow>
    ): IndicatorResult {
        val byId = programs.associateBy { it.id }
        val own = groups.filter { it.program in byId }
        val rows =
            own
                .groupBy { it.program }
                .entries
                .sortedBy { label(byId[it.key]) }
                .map { (program, list) ->
                    linkedMapOf<String, Any?>(
                        "key" to program,
                        "label" to label(byId[program]),
                        "target" to list.sumOf { it.target ?: 0L },
                        "reached" to list.sumOf { it.reached ?: 0L }
                    )
                }
        val reached = own.sumOf { it.reached ?: 0L }
        val executed = contributions.filter { it.state in EXECUTED }
        val fulfilled = commitments.filter { it.state in Commitments.FULFILLED }
        val sources =
            listOf(
                linkedMapOf<String, Any?>("key" to PROGRAMS, "value" to reached, "target" to own.sumOf { it.target ?: 0L }, "count" to own.size),
                linkedMapOf<String, Any?>("key" to CONTRIBUTIONS, "value" to executed.sumOf { it.beneficiaries ?: 0L }, "count" to executed.size),
                linkedMapOf<String, Any?>("key" to COMMITMENTS, "value" to fulfilled.sumOf { it.beneficiaries ?: 0L }, "count" to fulfilled.size)
            )
        return IndicatorResult(reached, breakdowns = listOf(Breakdown("source", sources), Breakdown("program", rows)))
    }

    // cómo se nombra un programa: código y nombre, o su id si no se lee ninguno
    private fun label(program: ProgramRow?): String =
        listOfNotNull(program?.code, program?.name).joinToString(" · ").ifEmpty { program?.id.orEmpty() }

    private fun percentOf(
        part: BigDecimal?,
        whole: BigDecimal?
    ): BigDecimal? = if (part == null || whole == null) null else Numbers.percent(part, whole)

    private fun sum(values: List<BigDecimal?>): BigDecimal = scaled(values.filterNotNull().fold(BigDecimal.ZERO, BigDecimal::add))

    // P44: los montos salen con la escala de Amounts, para que el JSON no cambie con la fila de origen
    private fun scaled(value: BigDecimal): BigDecimal = value.setScale(Amounts.MAX_SCALE, RoundingMode.HALF_UP)
}
```

- [ ] **Paso 4: Correr la prueba para verla pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat test --tests 'sgspe.dashboard.*'
```

Esperado: `BUILD SUCCESSFUL`; `InvestmentKpisTest` con 5 pruebas en verde y las demás de `sgspe.dashboard` como antes
(`CommitmentKpisTest` construye `CommitmentRow` sin el campo nuevo, que tiene valor por defecto).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/dashboard/InvestmentKpis.kt src/main/kotlin/sgspe/dashboard/CommitmentKpis.kt \
  src/test/kotlin/sgspe/dashboard/InvestmentKpisTest.kt
git commit -F - <<'MSG'
feat: KPI-INV-01 a 04 como funciones puras

InvestmentKpis compara lo comprometido y lo ejecutado de cada campaña con su tope, da el avance
de los programas por año y moneda, suma lo ejecutado del periodo por fuente y por proyecto y da
los beneficiarios en tres bloques sin sumarlos; la cifra titular va en PEN (ASM-063).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

### Tarea 8: Las lecturas de inversión y el tablero `investment`

**Files:**
- Modify: `src/main/kotlin/sgspe/dashboard/DashboardData.kt` (sección de inversión, `commitmentRow`),
  `src/main/kotlin/sgspe/dashboard/Kpis.kt` (`INVESTMENT`, `DASHBOARDS`, los cuatro `Kpi`)
- Test: `src/test/kotlin/sgspe/dashboard/InvestmentDashboardTest.kt` (crear)

**Interfaces:**
- Consumes: `InvestmentKpis` y sus filas (Tarea 7); `DashboardFilters.program`, `year`, `Kpis.extraFilters` (Tarea 6);
  `Programs` (Tarea 1); `Commitments.COMMITTED`; `Contributions.CONTRIBUTION` y `Contributions.COMMITTED` (plan 5.2a).
- Produces: en `DashboardData`, `investmentLocalities(): Set<String>`, `investmentCampaigns(campaign: Boolean):
  List<CampaignRow>`, `committedOf(campaigns: Collection<String>): List<CommittedAmount>`, `programs(program: Boolean):
  List<ProgramRow>`, `budgetsOf(programs: Collection<String>): List<BudgetRow>`, `groupsOf(programs: Collection<String>):
  List<GroupRow>`, `investmentContributions(): List<ContributionRow>`, `investmentCommitments(): List<CommitmentRow>`,
  `deliveriesOf(field: String, parents: Collection<String>): List<DeliveryRow>`; `Kpis.INVESTMENT = "investment"`,
  `Kpis.DASHBOARDS["investment"]` y los cuatro KPI-INV en `Kpis.ALL`.

- [ ] **Paso 1: Escribir la prueba que falla**

Crear `src/test/kotlin/sgspe/dashboard/InvestmentDashboardTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import sgspe.investment.Contributions
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

// el tablero investment sin base (dashboards.md §5.10, especificación de F5 §3.9): sus cuatro KPI en orden, lo que cada uno
// lee y los filtros que no aplica, y que lo comprometido usa las mismas definiciones de estado que CampaignCommitted (decisión
// 4 del plan 5.2b: Commitments.COMMITTED y Contributions.COMMITTED)
class InvestmentDashboardTest {
    private val all =
        DashboardFilters(
            listOf(UUID.randomUUID()),
            LocalDate.parse("2026-10-01"),
            LocalDate.parse("2026-10-31"),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "AIDS",
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            2026
        )

    private fun ignored(code: String) = Kpis.ignoredFilters(Kpis.BY_CODE.getValue(code), all)

    @Test
    fun `el tablero investment trae los cuatro KPI-INV en el orden de sus zonas`() {
        assertEquals(listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04"), Kpis.DASHBOARDS.getValue(Kpis.INVESTMENT))
        for (code in Kpis.DASHBOARDS.getValue(Kpis.INVESTMENT)) {
            assertEquals(Kpis.INVESTMENT, Kpis.BY_CODE.getValue(code).dashboard, code)
        }
        assertTrue(Kpis.BY_CODE.getValue("KPI-INV-01").requires.containsAll(listOf("social_contribution.total_amount", "commitment.amount")))
        assertTrue(Kpis.BY_CODE.getValue("KPI-INV-02").requires.containsAll(listOf("program_budget.allocated_amount", "program_budget.executed_amount")))
        assertTrue(Kpis.BY_CODE.getValue("KPI-INV-03").requires.containsAll(listOf("delivery.delivery_date", "delivery.social_contribution")))
        assertTrue(Kpis.BY_CODE.getValue("KPI-INV-04").requires.containsAll(listOf("beneficiary_group.reached_count", "commitment.beneficiaries_count")))
    }

    // §3.9: project, explorationCampaign, program, year, el periodo y locality; cada KPI declara los que no aplica (P27)
    @Test
    fun `cada KPI-INV declara los filtros que no aplica`() {
        assertEquals(listOf("administrativeUnit", "locality", "community", "areaOfInfluence", "scope", "owner", "program", "year"), ignored("KPI-INV-01"))
        assertEquals(listOf("administrativeUnit", "locality", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner"), ignored("KPI-INV-02"))
        assertEquals(
            listOf("administrativeUnit", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner", "program", "year"),
            ignored("KPI-INV-03")
        )
        assertEquals(listOf("administrativeUnit", "community", "areaOfInfluence", "explorationCampaign", "scope", "owner", "year"), ignored("KPI-INV-04"))
    }

    @Test
    fun `lo comprometido de una campana usa los estados de CampaignCommitted`() {
        assertEquals(setOf("approved", "procured", "delivered", "closed"), Contributions.COMMITTED)
        val campaign = UUID.randomUUID().toString()

        fun row(
            state: String,
            amount: String,
            field: String
        ) = RecordResponse(UUID.randomUUID().toString(), null, null, mapOf("exploration_campaign" to campaign, field to amount, "currency" to "PEN"), state)
        val records =
            mock(Records::class.java) { call ->
                if (call.method.name != "all") return@mock null
                when (call.arguments[0]) {
                    "commitment" -> listOf(row("draft", "1", "amount"), row("approved", "10", "amount"), row("closed", "20", "amount"))
                    "social_contribution" ->
                        listOf(
                            row("requested", "100", "total_amount"),
                            row("approved", "200", "total_amount"),
                            row("delivered", "300", "total_amount"),
                            row("rejected", "400", "total_amount")
                        )
                    else -> emptyList<RecordResponse>()
                }
            }
        val data = DashboardData(records, all, { true }, LocalDate.parse("2026-10-15"))
        val amounts = runBlocking { data.committedOf(listOf(campaign)) }.map { it.amount }
        assertEquals(listOf("10", "20", "200", "300").map(::BigDecimal), amounts)
    }
}
```

- [ ] **Paso 2: Correr la prueba para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.dashboard.InvestmentDashboardTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'INVESTMENT'` en `Kpis` y `'committedOf'`).

- [ ] **Paso 3: Las lecturas**

En `src/main/kotlin/sgspe/dashboard/DashboardData.kt`, agregar entre los imports (en orden alfabético):

```kotlin
import sgspe.commitment.Commitments
import sgspe.investment.Contributions
import sgspe.investment.Programs
```

En `commitmentRow`, reemplazar:

```kotlin
            Values.text(a["currency"]),
            Values.decimal(a["executed_amount"])
        )
    }
```

por:

```kotlin
            Values.text(a["currency"]),
            Values.decimal(a["executed_amount"]),
            Values.decimal(a["beneficiaries_count"])?.toLong()
        )
    }
```

Agregar antes de la línea `    companion object {` de la clase:

```kotlin
    // ---- inversión social (F5, dashboards.md §4.7, especificación de F5 §3.9) ----

    // las localidades del filtro locality con sus sectores; vacío si no vino. los KPI-INV aplican solo locality de los filtros de
    // territorio (decisión 5 del plan 5.2b) y declaran los demás en ignoredFilters
    suspend fun investmentLocalities(): Set<String> =
        cached("investmentLocalities") { filters.locality?.let { withSectors(setOf(it.toString())) } ?: emptySet() }

    private suspend fun inLocality(locality: String?): Boolean = filters.locality == null || (locality != null && locality in investmentLocalities())

    // las campañas de los proyectos; con `campaign`, solo la pedida en explorationCampaign (KPI-INV-01; KPI-INV-03 no la aplica)
    suspend fun investmentCampaigns(campaign: Boolean): List<CampaignRow> =
        cached("investmentCampaigns:$campaign") {
            if (filters.projects.isEmpty()) return@cached emptyList<CampaignRow>()
            val ids = if (campaign) filters.explorationCampaign?.let { listOf(it) } else null
            read("exploration_campaign", criteria = listOf(Criteria.anyOf("project", filters.projects)), ids = ids).map {
                val a = it.attributes
                CampaignRow(
                    it.id,
                    Values.text(a["project"]),
                    Values.text(a["name"]),
                    Values.decimal(a["social_support_cap"]),
                    Values.text(a["currency"]),
                    Values.decimal(a["executed_social_amount"]),
                    Values.decimal(a["other_currency_items_count"])?.toInt() ?: 0
                )
            }
        }

    // lo comprometido de esas campañas con las definiciones de CampaignCommitted (decisión 4 del plan 5.2b): los compromisos en
    // Commitments.COMMITTED con su amount y los aportes en Contributions.COMMITTED con su total_amount
    suspend fun committedOf(campaigns: Collection<String>): List<CommittedAmount> {
        fun of(
            rows: List<RecordResponse>,
            states: Set<String>,
            field: String
        ) = rows.filter { it.state in states }.map {
            CommittedAmount(
                Values.text(it.attributes["exploration_campaign"]).orEmpty(),
                Values.decimal(it.attributes[field]),
                Values.text(it.attributes["currency"])
            )
        }
        return of(readIn(Commitments.COMMITMENT, "exploration_campaign", campaigns), Commitments.COMMITTED, "amount") +
            of(readIn(Contributions.CONTRIBUTION, "exploration_campaign", campaigns), Contributions.COMMITTED, "total_amount")
    }

    // los programas de los proyectos; con `program`, solo el pedido (KPI-INV-02 y 04; KPI-INV-03 no lo aplica)
    suspend fun programs(program: Boolean): List<ProgramRow> =
        cached("programs:$program") {
            if (filters.projects.isEmpty()) return@cached emptyList<ProgramRow>()
            val ids = if (program) filters.program?.let { listOf(it) } else null
            read(Programs.PROGRAM, criteria = listOf(Criteria.anyOf("project", filters.projects)), ids = ids).map {
                val a = it.attributes
                ProgramRow(it.id, Values.text(a["project"]), Values.text(a["code"]), Values.text(a["name"]), Values.text(a["currency"]), it.state)
            }
        }

    suspend fun budgetsOf(programs: Collection<String>): List<BudgetRow> =
        readIn(Programs.BUDGET, Programs.PROGRAM, programs).map {
            val a = it.attributes
            BudgetRow(
                Values.text(a[Programs.PROGRAM]).orEmpty(),
                Values.decimal(a[Programs.YEAR])?.toInt(),
                Values.decimal(a[Programs.ALLOCATED]),
                Values.decimal(a["executed_amount"]),
                Values.text(a[Programs.CURRENCY])
            )
        }

    // los grupos de beneficiarios de esos programas, por su localidad si vino el filtro
    suspend fun groupsOf(programs: Collection<String>): List<GroupRow> =
        readIn(Programs.GROUP, Programs.PROGRAM, programs)
            .filter { inLocality(Values.text(it.attributes["locality"])) }
            .map {
                val a = it.attributes
                GroupRow(Values.text(a[Programs.PROGRAM]).orEmpty(), Values.decimal(a["target_count"])?.toLong(), Values.decimal(a["reached_count"])?.toLong())
            }

    // los aportes de los proyectos, por su localidad si vino el filtro (todos sus estados: cada KPI decide)
    suspend fun investmentContributions(): List<ContributionRow> =
        cached("investmentContributions") {
            if (filters.projects.isEmpty()) return@cached emptyList<ContributionRow>()
            read(Contributions.CONTRIBUTION, criteria = listOf(Criteria.anyOf("project", filters.projects)))
                .filter { inLocality(Values.text(it.attributes["locality"])) }
                .map {
                    val a = it.attributes
                    ContributionRow(
                        it.id,
                        Values.text(a["project"]),
                        it.state,
                        Values.decimal(a["total_amount"]),
                        Values.text(a["currency"]),
                        Values.decimal(a["beneficiaries_count"])?.toLong()
                    )
                }
        }

    // los compromisos de los proyectos, solo por su localidad: los KPI-INV no aplican los demás filtros de territorio, el ámbito,
    // la campaña ni el responsable
    suspend fun investmentCommitments(): List<CommitmentRow> =
        cached("investmentCommitments") {
            if (filters.projects.isEmpty()) return@cached emptyList<CommitmentRow>()
            read(Commitments.COMMITMENT, criteria = listOf(Criteria.anyOf("project", filters.projects)))
                .filter { inLocality(Values.text(it.attributes["locality"])) }
                .map { commitmentRow(it) }
        }

    // las entregas de `parents` por su campo `field` (commitment o social_contribution)
    suspend fun deliveriesOf(
        field: String,
        parents: Collection<String>
    ): List<DeliveryRow> =
        readIn(Commitments.DELIVERY, field, parents).map {
            val a = it.attributes
            DeliveryRow(
                Values.text(a[field]).orEmpty(),
                Values.text(a["delivery_date"])?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() },
                Values.decimal(a["delivered_amount"])
            )
        }

```

- [ ] **Paso 4: Los cuatro KPI y el tablero**

En `src/main/kotlin/sgspe/dashboard/Kpis.kt`, agregar `import sgspe.investment.Contributions` e
`import sgspe.investment.Programs` entre los imports (después de `import sgspe.engagement.AttendanceCounts`), y reemplazar:

```kotlin
    const val RISKS = "risks"
```

por:

```kotlin
    const val RISKS = "risks"
    const val INVESTMENT = "investment"
```

Reemplazar:

```kotlin
            RISKS to listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05")
        )
```

por:

```kotlin
            RISKS to listOf("KPI-RSK-02", "KPI-RSK-01", "KPI-RSK-03", "KPI-RSK-04", "KPI-RSK-05"),
            // dashboards.md §5.10: campañas, programas, consolidado y beneficiarios
            INVESTMENT to listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04")
        )

    // F5: los KPI-INV aplican de los filtros de territorio solo locality (decisión 5 del plan 5.2b); ninguno aplica el ámbito
    // ni el responsable de un compromiso. cada uno suma lo que su fila de dashboards.md §4.7 no aplica
    private val INV_IGNORES: Set<String> = setOf("administrativeUnit", "community", "areaOfInfluence", "scope", "owner")

    // lo que leen todos los KPI-INV de los programas
    private val PROGRAM =
        listOf("${Programs.PROGRAM}.project", "${Programs.PROGRAM}.code", "${Programs.PROGRAM}.name", "${Programs.PROGRAM}.currency")
    private val BUDGET =
        listOf(
            "${Programs.BUDGET}.social_program",
            "${Programs.BUDGET}.budget_year",
            "${Programs.BUDGET}.allocated_amount",
            "${Programs.BUDGET}.executed_amount",
            "${Programs.BUDGET}.currency"
        )
```

Reemplazar el cierre de `ALL` (el final del último KPI-RSK):

```kotlin
                RiskKpis.mitigation(data.measures(rows.map { it.id }), rows.associate { it.id to it.code }, data.today)
            }
        )
```

por:

```kotlin
                RiskKpis.mitigation(data.measures(rows.map { it.id }), rows.associate { it.id to it.code }, data.today)
            },
            // ---- F5: inversión social (dashboards.md §4.7, especificación de F5 §3.9); leen como quien mira ----
            Kpi(
                "KPI-INV-01",
                "Ejecución de la campaña frente a su tope",
                INVESTMENT,
                "%",
                listOf(
                    "exploration_campaign.project",
                    "exploration_campaign.name",
                    "exploration_campaign.social_support_cap",
                    "exploration_campaign.currency",
                    "exploration_campaign.executed_social_amount",
                    "exploration_campaign.other_currency_items_count",
                    "commitment.exploration_campaign",
                    "commitment.amount",
                    "commitment.currency",
                    "social_contribution.exploration_campaign",
                    "social_contribution.total_amount",
                    "social_contribution.currency"
                ),
                ignores = INV_IGNORES + setOf("locality", "program", "year")
            ) { data ->
                val campaigns = data.investmentCampaigns(campaign = true)
                InvestmentKpis.campaigns(campaigns, data.committedOf(campaigns.map { it.id }))
            },
            Kpi(
                "KPI-INV-02",
                "Ejecución de programas frente a presupuesto",
                INVESTMENT,
                "%",
                PROGRAM + BUDGET,
                ignores = INV_IGNORES + setOf("locality", "explorationCampaign")
            ) { data ->
                val programs = data.programs(program = true)
                InvestmentKpis.programs(programs, data.budgetsOf(programs.map { it.id }), data.filters.year)
            },
            Kpi(
                "KPI-INV-03",
                "Inversión social ejecutada",
                INVESTMENT,
                "monto",
                PROGRAM + BUDGET +
                    listOf(
                        "commitment.project",
                        "commitment.locality",
                        "commitment.currency",
                        "delivery.commitment",
                        "delivery.social_contribution",
                        "delivery.delivery_date",
                        "delivery.delivered_amount",
                        "social_contribution.project",
                        "social_contribution.locality",
                        "social_contribution.total_amount",
                        "social_contribution.currency",
                        "exploration_campaign.project",
                        "exploration_campaign.social_support_cap",
                        "exploration_campaign.currency",
                        "project.code"
                    ),
                ignores = INV_IGNORES + setOf("explorationCampaign", "program", "year")
            ) { data ->
                val commitments = data.investmentCommitments()
                val contributions = data.investmentContributions()
                val withPrograms = data.filters.locality == null
                val programs = if (withPrograms) data.programs(program = false) else emptyList()
                InvestmentKpis.executed(
                    commitments,
                    data.deliveriesOf("commitment", commitments.map { it.id }),
                    contributions,
                    data.deliveriesOf(Contributions.CONTRIBUTION, contributions.map { it.id }),
                    programs,
                    data.budgetsOf(programs.map { it.id }),
                    data.investmentCampaigns(campaign = false),
                    data.filters.periodStart,
                    data.filters.periodEnd,
                    withPrograms,
                    data.names("project", data.filters.projects.map { it.toString() }, "code")
                )
            },
            Kpi(
                "KPI-INV-04",
                "Beneficiarios",
                INVESTMENT,
                "beneficiarios",
                PROGRAM +
                    listOf(
                        "beneficiary_group.social_program",
                        "beneficiary_group.target_count",
                        "beneficiary_group.reached_count",
                        "beneficiary_group.locality",
                        "social_contribution.project",
                        "social_contribution.locality",
                        "social_contribution.beneficiaries_count",
                        "commitment.project",
                        "commitment.locality",
                        "commitment.beneficiaries_count"
                    ),
                ignores = INV_IGNORES + setOf("explorationCampaign", "year")
            ) { data ->
                val programs = data.programs(program = true)
                InvestmentKpis.beneficiaries(programs, data.groupsOf(programs.map { it.id }), data.investmentContributions(), data.investmentCommitments())
            }
        )
```

- [ ] **Paso 5: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `InvestmentDashboardTest` con 3 pruebas en verde y todas las demás como antes.

- [ ] **Paso 6: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/dashboard/DashboardData.kt src/main/kotlin/sgspe/dashboard/Kpis.kt \
  src/test/kotlin/sgspe/dashboard/InvestmentDashboardTest.kt
git commit -F - <<'MSG'
feat: tablero investment con KPI-INV-01 a 04

DashboardData lee como quien mira las campañas con lo comprometido, los programas con sus
presupuestos y grupos, los aportes, los compromisos y sus entregas; el tablero investment junta
los cuatro KPI-INV y cada uno declara los filtros que no aplica.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** la cubre `InvestmentDashboardApiTest` (Tarea 10).

---
### Tarea 9: La base de pruebas de 5.2b y WF-G por la API (`SocialProgramWorkflowApiTest`)

**Files:**
- Create: `src/test/kotlin/sgspe/F5ProgramApiTest.kt`, `src/test/kotlin/sgspe/investment/SocialProgramWorkflowApiTest.kt`

**Interfaces:**
- Consumes: `F5ApiTest` (plan 5.1, sobre `F4ApiTest`) con sus ayudas de BC-10, que este plan usa tal cual:
  `socialProgram(project, creator, attributes)`, `programBudget(program, year, allocated: Number, currency, executed:
  Number?, creator)`, `programIndicator(program, creator, attributes)`, `programMeasurement(indicator, start, end, value,
  creator)`, `socialInitiative(program, creator, attributes)`, `beneficiaryGroup(program, initiative, creator, attributes)`,
  `socialContribution(project, creator, attributes, items)`, `contributionItem(contribution, creator, attributes)` y
  `campaign(project, cap, currency)`; de las bases anteriores, `project`, `member`, `userWithRoles`, `assign`, `createRecord`,
  `move`, `transition`, `edit`, `record`, `code`, `withReason`, `organization`, `agreement`, `evidence`, `platformPatch`,
  `exchange`, `send`, `tree`, `state`; el endpoint de entrega de aportes y WF-P (plan 5.2a); las Tareas 2 a 5.
- Produces: `abstract class F5ProgramApiTest : F5ApiTest()` con `projectWithoutInternalApproval(attributes)`,
  `activeAgreementWith(project, parties)` (un acuerdo en `active` con esas organizaciones como partes, que `activeAgreement`
  de 5.1 no trae), `registerContribution(project, creator, currency, lineAmount, attributes)`,
  `registerApprovedContribution(…)` y `registerDeliveredContribution(…, date)` (un aporte con un solo ítem de monto y
  moneda conocidos, llevado por WF-P y por el endpoint de entrega): todas devuelven el id. Los nombres no chocan con los de
  `F5ApiTest` (decisión 12).

Las pruebas son de integración: el implementador compila y deja la tarea «integración pendiente».

- [ ] **Paso 1: La base**

Crear `src/test/kotlin/sgspe/F5ProgramApiTest.kt`:

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus

// la base de las pruebas de integración del plan 5.2b, sobre F5ApiTest (plan 5.1), que ya trae los programas y sus hijos: aquí
// solo lo que esa base no da. acuerdos en active con sus partes para G3, y aportes de un solo ítem con monto y moneda conocidos
// llevados por WF-P hasta aprobados o entregados con sus tres evidencias (P8) por el endpoint de 5.2a, como el portal; los
// tableros los suman. los nombres no chocan con los de F5ApiTest (decisión 12 del plan 5.2b)
abstract class F5ProgramApiTest : F5ApiTest() {
    // un proyecto sin aprobación interna: plan_direct (G4) y approve_direct (P7) pasan
    protected fun projectWithoutInternalApproval(attributes: Map<String, Any?> = emptyMap()): String =
        project(mapOf("policy_internal_approval" to false) + attributes)

    // un acuerdo en active (AgreementWriteGuard: el CONVENIO firmado y vigente de F3ApiTest.agreement) con `parties` como sus
    // partes, cargadas antes de activarlo
    protected fun activeAgreementWith(
        project: String,
        parties: List<String> = emptyList()
    ): String {
        val id = agreement(project)
        for (party in parties) createRecord("agreement_party", mapOf("agreement" to id, "organization" to party, "signed" to true))
        move("agreement", id, "activate_from_draft", reason = "vigencia de prueba")
        return id
    }

    // un aporte en requested con un solo ítem BIEN de `lineAmount` en `currency` y 30 beneficiarios (F5ApiTest.socialContribution
    // sin ítems y F5ApiTest.contributionItem), registrado por `creator`
    protected fun registerContribution(
        project: String,
        creator: Account,
        currency: String = "PEN",
        lineAmount: Number = 2000,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = socialContribution(project, creator, mapOf("currency" to currency, "beneficiaries_count" to 30) + attributes, items = 0)
        contributionItem(id, creator, mapOf("item_kind" to "BIEN", "line_amount" to lineAmount))
        return id
    }

    // un aporte aprobado sin revisión interna (approve_direct, P7: el proyecto de `investor` no tiene aprobación interna)
    protected fun registerApprovedContribution(
        project: String,
        investor: Account,
        currency: String = "PEN",
        lineAmount: Number = 2000,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = registerContribution(project, investor, currency, lineAmount, attributes)
        move("social_contribution", id, "approve_direct", investor.token, "aprobación de prueba")
        return id
    }

    // un aporte entregado: adquirido, con su entrega capturada en `date` por el endpoint de 5.2a con conformidad, acta de
    // recepción, foto y comprobante del proyecto (P8) y register_delivery aplicado
    protected fun registerDeliveredContribution(
        project: String,
        investor: Account,
        date: String = "2026-10-10",
        currency: String = "PEN",
        lineAmount: Number = 2000,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = registerApprovedContribution(project, investor, currency, lineAmount, attributes)
        move("social_contribution", id, "register_procurement", investor.token, "compra de prueba")
        val evidence = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE").map { evidence(project, it, account = investor) }
        val captured =
            exchange(
                "POST",
                "/api/sgspe/social-contributions/$id/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to date, "conformity_given" to true, "conformity_by" to "Persona Ejemplo"),
                    "evidence" to evidence
                ),
                investor.token,
                withReason("entrega de prueba")
            )
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        move("social_contribution", id, "register_delivery", investor.token, "entrega registrada")
        return id
    }
}
```


- [ ] **Paso 2: Las pruebas de WF-G**

Crear `src/test/kotlin/sgspe/investment/SocialProgramWorkflowApiTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F5ProgramApiTest
import sgspe.common.DerivedFieldsJob
import java.math.BigDecimal

// WF-G por la API (workflows.md «Pruebas», especificación de F5 §5): G1 a G4, el motivo de suspender y cancelar, las reglas de
// los hijos, las mediciones inmutables, total_budget con su listener y JOB-02, y el aviso de aprobación. testProject() tiene la
// aprobación interna activa (por defecto); projectWithoutInternalApproval() no
class SocialProgramWorkflowApiTest : F5ProgramApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    private val investor by lazy { member("SOCIAL_INVESTMENT_MANAGER") }
    private val approver by lazy { member("APPROVER") }

    private fun submitted(
        attributes: Map<String, Any?> = emptyMap(),
        project: String = testProject(),
        creator: Account = investor
    ): String {
        val id = socialProgram(project, creator, attributes)
        move("social_program", id, "submit", creator.token)
        return id
    }

    private fun total(program: String): BigDecimal? = record("social_program", program)["attributes"]["total_budget"]?.takeUnless { it.isNull }?.decimalValue()

    private fun inbox(token: String) = tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, token))["content"].values()

    private fun pendingFor(
        token: String,
        id: String
    ): Boolean = inbox(token).any { it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION" }

    @Test
    fun `approve por un APPROVER que no registro el programa lo planifica`() {
        val id = submitted()
        move("social_program", id, "approve", approver.token)
        assertEquals("planned", state("social_program", id))
    }

    @Test
    fun `approve por quien registro el programa es 403 SEGREGATION_OF_DUTIES`() {
        // una persona con los dos roles: registra y envía como SOCIAL_INVESTMENT_MANAGER y aprueba como APPROVER
        val both = userWithRoles(listOf("SOCIAL_INVESTMENT_MANAGER", "APPROVER")).also { assign(it, testProject(), "APPROVER") }
        val id = submitted(creator = both)
        val refused = transition("social_program", id, "approve", both.token)
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(refused))
        assertEquals("pending_approval", state("social_program", id))
    }

    // decisión 8 del plan 5.2b: G1 no se alcanza por la API con los roles de hoy; la consulta en seco corre la regla sin los roles
    @Test
    fun `sin APPROVE la consulta en seco de approve da ACTION_REQUIRED`() {
        val id = submitted()
        val check =
            tree(
                send(
                    "POST",
                    "/api/sgspe/transition-checks",
                    mapOf("object" to "social_program", "recordId" to id, "transition" to "approve"),
                    HttpStatus.OK,
                    investor.token
                )
            )
        assertFalse(check["allowed"].asBoolean())
        assertTrue(check["blocking"].values().any { it["code"].asString() == "ACTION_REQUIRED" }, check.toString())
    }

    @Test
    fun `con socio y la politica activa approve exige un acuerdo vigente con el socio como parte`() {
        val partner = organization("Asociación Ejemplo")
        val project = testProject()

        fun refusedWith(attributes: Map<String, Any?>) {
            val id = submitted(mapOf("partner_organization" to partner) + attributes)
            val refused = transition("social_program", id, "approve", approver.token)
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(refused))
            assertEquals("agreement", tree(refused.body)["errors"][0]["field"].asString())
        }
        refusedWith(emptyMap())
        refusedWith(mapOf("agreement" to agreement(project)))
        refusedWith(mapOf("agreement" to activeAgreementWith(project)))
        val expired = activeAgreementWith(project, listOf(partner))
        platformPatch("agreement", expired, mapOf("validity_status" to "VENCIDO"))
        refusedWith(mapOf("agreement" to expired))
        val id = submitted(mapOf("partner_organization" to partner, "agreement" to activeAgreementWith(project, listOf(partner))))
        move("social_program", id, "approve", approver.token)
        assertEquals("planned", state("social_program", id))
    }

    @Test
    fun `con la politica de sinergia apagada el socio sin acuerdo no bloquea`() {
        val project = project(mapOf("policy_synergy_requires_agreement" to false))
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val id = submitted(mapOf("partner_organization" to organization("Asociación Ejemplo")), project, investor)
        move("social_program", id, "approve", member("APPROVER", project).token)
        assertEquals("planned", state("social_program", id))
    }

    @Test
    fun `plan_direct con la aprobacion interna activa es 409 y sin ella pasa, con la sinergia de G3`() {
        val strict = socialProgram(testProject(), investor)
        val refused = transition("social_program", strict, "plan_direct", investor.token)
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        val relaxed = projectWithoutInternalApproval()
        val planner = member("SOCIAL_INVESTMENT_MANAGER", relaxed)
        val plain = socialProgram(relaxed, planner)
        move("social_program", plain, "plan_direct", planner.token)
        assertEquals("planned", state("social_program", plain))
        val partnered = socialProgram(relaxed, planner, mapOf("partner_organization" to organization("Asociación Ejemplo")))
        assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(transition("social_program", partnered, "plan_direct", planner.token)))
    }

    @Test
    fun `suspend, return_to_draft y las cancel_ sin motivo son 400 y con motivo pasan`() {
        val relaxed = projectWithoutInternalApproval()
        val planner = member("SOCIAL_INVESTMENT_MANAGER", relaxed)
        val manager = member("SOCIAL_MANAGER", relaxed)
        val running = socialProgram(relaxed, planner)
        move("social_program", running, "plan_direct", planner.token)
        move("social_program", running, "start_execution", planner.token)
        val noReason = transition("social_program", running, "suspend", manager.token)
        assertEquals(HttpStatus.BAD_REQUEST, noReason.status, noReason.body)
        assertEquals("REASON_REQUIRED", code(noReason))
        move("social_program", running, "suspend", manager.token, "falta de acceso a la comunidad")
        assertEquals("REASON_REQUIRED", code(transition("social_program", running, "cancel_suspended", manager.token)))
        move("social_program", running, "cancel_suspended", manager.token, "la comunidad desistió")
        val draft = socialProgram(relaxed, planner)
        assertEquals("REASON_REQUIRED", code(transition("social_program", draft, "cancel_draft", planner.token)))
        val pending = submitted(project = relaxed, creator = planner)
        val approverOfRelaxed = member("APPROVER", relaxed)
        assertEquals("REASON_REQUIRED", code(transition("social_program", pending, "return_to_draft", approverOfRelaxed.token)))
        assertEquals("REASON_REQUIRED", code(transition("social_program", pending, "cancel_pending_approval", approverOfRelaxed.token)))
        move("social_program", pending, "approve", approverOfRelaxed.token)
        assertEquals("REASON_REQUIRED", code(transition("social_program", pending, "cancel_planned", approverOfRelaxed.token)))
        move("social_program", pending, "cancel_planned", approverOfRelaxed.token, "sin presupuesto")
        assertEquals("cancelled", state("social_program", pending))
    }

    // la entrada 1 del Review Focus
    @Test
    fun `total_budget solo suma la moneda del programa y sigue su cambio de moneda`() {
        val id = socialProgram(testProject(), investor, mapOf("currency" to "PEN"))
        assertEquals(0, BigDecimal.ZERO.compareTo(total(id)))
        programBudget(id, 2026, 1000, creator = investor)
        programBudget(id, 2027, 500.5, creator = investor)
        val usd = programBudget(id, 2028, 300, "USD", creator = investor)
        assertEquals(0, BigDecimal("1500.5").compareTo(total(id)))
        val edited = edit("social_program", id, mapOf("currency" to "USD"), investor.token)
        assertEquals(HttpStatus.OK, edited.status, edited.body)
        assertEquals(0, BigDecimal("300").compareTo(total(id)))
        // la baja del presupuesto en USD (SOCIAL_INVESTMENT_MANAGER no borra presupuestos: CRU) la hace el admin
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/program_budget/records/$usd").status)
        assertEquals(0, BigDecimal.ZERO.compareTo(total(id)))
    }

    @Test
    fun `JOB-02 corrige total_budget con el motivo del trabajo y una vuelta sin cambios no escribe`() {
        val id = socialProgram(testProject(), investor, mapOf("currency" to "PEN"))
        programBudget(id, 2026, 1000, creator = investor)
        platformPatch("social_program", id, mapOf("total_budget" to 999), "prueba: total dañado")
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(0, BigDecimal("1000").compareTo(total(id)))
        val history = tree(send("GET", "/api/objects/social_program/records/$id/history", null, HttpStatus.OK))
        assertEquals(ProgramTotalsRecalculator.REASON, history.values().first()["reason"].asString())
        val updatedAt = record("social_program", id)["updatedAt"].asString()
        assertNotNull(runBlocking { job.runOnce(full = true) })
        assertEquals(updatedAt, record("social_program", id)["updatedAt"].asString())
    }

    @Test
    fun `un anio fuera de 2000 a 2100, un conteo negativo y fechas al reves son 400 OUT_OF_RANGE`() {
        val id = socialProgram(testProject(), investor)

        fun refused(
            objectName: String,
            attributes: Map<String, Any?>,
            field: String
        ) {
            val response = exchange("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), investor.token)
            assertEquals(HttpStatus.BAD_REQUEST, response.status, response.body)
            assertEquals("OUT_OF_RANGE", code(response))
            assertEquals(field, tree(response.body)["errors"][0]["field"].asString())
        }
        refused("program_budget", mapOf("social_program" to id, "budget_year" to 1999, "allocated_amount" to 10, "currency" to "PEN"), "budget_year")
        refused("beneficiary_group", mapOf("social_program" to id, "name" to "Familias", "target_count" to -1), "target_count")
        refused("social_initiative", mapOf("social_program" to id, "name" to "Riego", "start_date" to "2026-06-01", "end_date" to "2026-01-01"), "end_date")
        val indicator = programIndicator(id, investor)
        refused(
            "program_measurement",
            mapOf("program_indicator" to indicator, "period_start_date" to "2026-04-01", "period_end_date" to "2026-03-31", "measured_value" to 1),
            "period_end_date"
        )
    }

    @Test
    fun `una medicion repetida en el periodo es 409 y una medicion no se cambia ni se borra`() {
        val indicator = programIndicator(socialProgram(testProject(), investor), investor)
        val measurement = programMeasurement(indicator, creator = investor)
        val repeated =
            exchange(
                "POST",
                "/api/objects/program_measurement/records",
                mapOf(
                    "attributes" to
                        mapOf("program_indicator" to indicator, "period_start_date" to "2026-01-01", "period_end_date" to "2026-02-28", "measured_value" to 20)
                ),
                investor.token
            )
        assertEquals(HttpStatus.CONFLICT, repeated.status, repeated.body)
        // appendOnly (RQ-AUD-003): la plataforma lo rechaza también al admin
        assertEquals(HttpStatus.CONFLICT, edit("program_measurement", measurement, mapOf("notes" to "corrección")).status)
        assertEquals(HttpStatus.CONFLICT, exchange("DELETE", "/api/objects/program_measurement/records/$measurement").status)
        // decisión 9 del plan 5.2b: un indicador con mediciones no se borra (409 de wasichai, AppendOnlyReferences)
        assertEquals(HttpStatus.CONFLICT, exchange("DELETE", "/api/objects/program_indicator/records/$indicator").status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/program_indicator/records/$indicator").status)
    }

    @Test
    fun `un grupo de beneficiarios con una iniciativa de otro programa es 409 PROGRAM_MISMATCH`() {
        val mine = socialProgram(testProject(), investor)
        val other = socialProgram(testProject(), investor)
        val initiative = socialInitiative(other, investor)
        val refused =
            exchange(
                "POST",
                "/api/objects/beneficiary_group/records",
                mapOf("attributes" to mapOf("social_program" to mine, "social_initiative" to initiative, "name" to "Familias")),
                investor.token
            )
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PROGRAM_MISMATCH", code(refused))
        val group = beneficiaryGroup(other, initiative, investor)
        val moved = edit("beneficiary_group", group, mapOf("social_program" to mine), investor.token)
        assertEquals("PROGRAM_MISMATCH", code(moved))
    }

    @Test
    fun `el envio avisa a los APPROVER asignados y la aprobacion resuelve el aviso`() {
        val other = member("GIS_ANALYST")
        val id = submitted()
        assertTrue(pendingFor(approver.token, id))
        assertFalse(pendingFor(other.token, id))
        assertFalse(pendingFor(investor.token, id))
        move("social_program", id, "approve", approver.token)
        assertFalse(pendingFor(approver.token, id))
    }
}
```

`state` es de `F2ApiTest` (el estado del registro como lo lee el admin).

- [ ] **Paso 3: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL` (las de integración compilan; no corren en `build`).

- [ ] **Paso 4: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/F5ProgramApiTest.kt src/test/kotlin/sgspe/investment/SocialProgramWorkflowApiTest.kt
git commit -F - <<'MSG'
test: WF-G por la API y la base de pruebas del plan 5.2b

F5ProgramApiTest arma programas con sus hijos, acuerdos vigentes con sus partes y aportes hasta
entregados; SocialProgramWorkflowApiTest prueba G1 a G4, el motivo de suspender y cancelar, las
reglas de los hijos, las mediciones inmutables, total_budget con JOB-02 y el aviso de aprobación.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `SocialProgramWorkflowApiTest`.

---

### Tarea 10: El tablero `investment` por la API (`InvestmentDashboardApiTest`)

**Files:**
- Create: `src/test/kotlin/sgspe/dashboard/InvestmentDashboardApiTest.kt`

**Interfaces:**
- Consumes: `F5ProgramApiTest` (Tarea 9); `approvedCommitment` (`F3DomainApiTest`), `POST
  /api/sgspe/commitments/{id}/deliveries`, `DerivedFieldsJob` (el ejecutado de la campaña con la segunda fuente de 5.2a); el
  tablero de la Tarea 8.
- Produces: la prueba de §5 de la especificación («KPI-INV-01 a 04 con montos en dos monedas; un proyecto ajeno da conjuntos
  vacíos»).

- [ ] **Paso 1: La prueba**

Crear `src/test/kotlin/sgspe/dashboard/InvestmentDashboardApiTest.kt`:

```kotlin
package sgspe.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.F5ProgramApiTest
import sgspe.common.DerivedFieldsJob
import tools.jackson.databind.JsonNode
import java.math.BigDecimal

// el tablero investment (dashboards.md §5.10, especificación de F5 §3.9 y §5): los cuatro KPI-INV con montos en dos monedas,
// los filtros program y year y un proyecto ajeno con conjuntos vacíos. las fechas caen en octubre de 2026
class InvestmentDashboardApiTest : F5ProgramApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    private val period = "periodStart=2026-10-01&periodEnd=2026-10-31"

    private fun dashboard(
        query: String,
        token: String = this.token
    ): JsonNode = tree(send("GET", "/api/sgspe/dashboards/investment?$query", null, HttpStatus.OK, token))

    private fun indicator(
        body: JsonNode,
        code: String
    ): JsonNode = body["indicators"].values().first { it["code"].asString() == code }

    private fun rows(
        kpi: JsonNode,
        by: String
    ): List<JsonNode> = kpi["breakdowns"].values().first { it["by"].asString() == by }["rows"].values()

    private fun same(
        expected: String,
        node: JsonNode?
    ) = assertEquals(0, BigDecimal(expected).compareTo(node!!.decimalValue()), "$node")

    @Test
    fun `el tablero trae los cuatro KPI-INV en orden y el catalogo los da disponibles`() {
        val body = dashboard("project=${projectWithoutInternalApproval()}&$period")
        assertEquals(listOf("KPI-INV-01", "KPI-INV-02", "KPI-INV-03", "KPI-INV-04"), body["indicators"].values().map { it["code"].asString() })
        val catalog =
            tree(send("GET", "/api/sgspe/dashboards/indicators", null, HttpStatus.OK)).values().filter { it["code"].asString().startsWith("KPI-INV-") }
        assertEquals(4, catalog.size)
        assertTrue(catalog.all { it["available"].asBoolean() && it["dashboard"].asString() == "investment" })
    }

    @Test
    fun `KPI-INV-01 suma lo comprometido de compromisos y aportes en la moneda de la campana y cuenta el resto`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        // F5ApiTest.campaign: una campaña del proyecto con tope de 10 000 PEN
        val withCap = campaign(project, 10000, "PEN")
        approvedCommitment(project, attributes = mapOf("exploration_campaign" to withCap, "amount" to 3000))
        registerApprovedContribution(project, investor, lineAmount = 2000, attributes = mapOf("exploration_campaign" to withCap))
        registerApprovedContribution(project, investor, currency = "USD", lineAmount = 500, attributes = mapOf("exploration_campaign" to withCap))
        registerContribution(project, investor, lineAmount = 999, attributes = mapOf("exploration_campaign" to withCap))
        val kpi = indicator(dashboard("project=$project&$period"), "KPI-INV-01")
        val row = rows(kpi, "campaign").single()
        assertEquals(withCap, row["key"].asString())
        same("10000", row["cap"])
        same("5000", row["committed"])
        assertEquals(1, row["committedOtherCurrencyCount"].asInt())
        same("50.0", row["committedPercent"])
        assertTrue(kpi["warnings"].values().isNotEmpty())
    }

    @Test
    fun `KPI-INV-02 da el avance por programa, anio y moneda, con program y year`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val program = socialProgram(project, investor)
        programBudget(program, 2026, 1000, executed = 250, creator = investor)
        programBudget(program, 2027, 400, "USD", executed = 100, creator = investor)
        socialProgram(project, investor).also { programBudget(it, 2026, 3000, executed = 750, creator = investor) }
        val all = indicator(dashboard("project=$project&$period"), "KPI-INV-02")
        same("25.0", all["value"])
        assertEquals(3, rows(all, "program").size)
        val one = indicator(dashboard("project=$project&$period&program=$program&year=2027"), "KPI-INV-02")
        assertTrue(one["value"].isNull)
        assertEquals(listOf("$program:2027:USD"), rows(one, "program").map { it["key"].asString() })
        val ignored = indicator(dashboard("project=$project&program=$program&year=2027"), "KPI-INV-01")["ignoredFilters"]
        assertEquals(listOf("program", "year"), ignored.values().map { it.asString() })
    }

    @Test
    fun `KPI-INV-03 suma lo ejecutado del periodo por fuente y KPI-INV-04 da los beneficiarios en tres bloques`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        val commitment = approvedCommitment(project, attributes = mapOf("amount" to 3000))
        val delivered =
            exchange(
                "POST",
                "/api/sgspe/commitments/$commitment/deliveries",
                mapOf(
                    "attributes" to mapOf("delivery_date" to "2026-10-05", "conformity_given" to true, "delivered_amount" to 700),
                    "evidence" to emptyList<String>()
                ),
                token,
                withReason("entrega de prueba")
            )
        assertEquals(HttpStatus.CREATED, delivered.status, delivered.body)
        registerDeliveredContribution(project, investor, "2026-10-10", lineAmount = 2000)
        registerDeliveredContribution(project, investor, "2026-10-12", currency = "USD", lineAmount = 50)
        val program = socialProgram(project, investor)
        programBudget(program, 2026, 1000, executed = 250, creator = investor)
        beneficiaryGroup(program, creator = investor, attributes = mapOf("target_count" to 100, "reached_count" to 40))
        val body = dashboard("project=$project&$period")
        val executed = indicator(body, "KPI-INV-03")
        same("2950", executed["value"])
        val sources = rows(executed, "source").associate { it["key"].asString() to it["value"] }
        same("700", sources["COMPROMISOS:PEN"])
        same("2000", sources["APORTES:PEN"])
        same("50", sources["APORTES:USD"])
        same("250", sources["PROGRAMAS:PEN"])
        assertTrue(executed["warnings"].values().isNotEmpty())
        val beneficiaries = indicator(body, "KPI-INV-04")
        assertEquals(40, beneficiaries["value"].asInt())
        val blocks = rows(beneficiaries, "source").associateBy { it["key"].asString() }
        assertEquals(60, blocks.getValue("APORTES")["value"].asInt())
        assertEquals(0, blocks.getValue("COMPROMISOS")["value"].asInt())
    }

    @Test
    fun `KPI-INV-01 muestra el ejecutado de la campana que deja JOB-02`() {
        val project = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", project)
        // F5ApiTest.campaign: una campaña del proyecto con tope de 10 000 PEN
        val withCap = campaign(project, 10000, "PEN")
        registerDeliveredContribution(project, investor, lineAmount = 2500, attributes = mapOf("exploration_campaign" to withCap))
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        val kpi = indicator(dashboard("project=$project&$period"), "KPI-INV-01")
        same("25.0", kpi["value"])
        same("2500", rows(kpi, "campaign").single()["executed"])
    }

    @Test
    fun `un proyecto ajeno da conjuntos vacios`() {
        val mine = projectWithoutInternalApproval()
        val theirs = projectWithoutInternalApproval()
        val investor = member("SOCIAL_INVESTMENT_MANAGER", theirs)
        val program = socialProgram(theirs, investor)
        programBudget(program, 2026, 1000, executed = 250, creator = investor)
        beneficiaryGroup(program, creator = investor, attributes = mapOf("target_count" to 100, "reached_count" to 40))
        registerApprovedContribution(theirs, investor)
        val outsider = member("SOCIAL_INVESTMENT_MANAGER", mine)
        val body = dashboard("project=$theirs&$period", outsider.token)
        for (code in listOf("KPI-INV-01", "KPI-INV-02")) {
            val kpi = indicator(body, code)
            assertTrue(kpi["available"].asBoolean(), code)
            assertTrue(kpi["value"].isNull, code)
            assertTrue(kpi["breakdowns"].values().all { it["rows"].isEmpty }, code)
        }
        same("0", indicator(body, "KPI-INV-03")["value"])
        assertEquals(0, indicator(body, "KPI-INV-04")["value"].asInt())
    }

    // la entrada 5 del Review Focus
    @Test
    fun `program y year en otro tablero son 422 y un anio fuera de rango es 400`() {
        val project = projectWithoutInternalApproval()
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/dashboards/risks?project=$project&year=2026").status)
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exchange("GET", "/api/sgspe/dashboards/indicators/KPI-COM-01?program=$project").status)
        assertEquals(HttpStatus.BAD_REQUEST, exchange("GET", "/api/sgspe/dashboards/investment?project=$project&year=1999").status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/sgspe/dashboards/indicators/KPI-INV-02?project=$project&year=2026").status)
    }
}
```

`approvedCommitment` lo aprueba un `APPROVER` asignado y lo deja en ejecución (`F3DomainApiTest`), así que acepta la entrega.
La campaña de KPI-INV-01 tiene tope de sobra: ningún aporte lo supera (P1 de 5.2a no bloquea).

- [ ] **Paso 2: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 3: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/dashboard/InvestmentDashboardApiTest.kt
git commit -F - <<'MSG'
test: tablero investment por la API

InvestmentDashboardApiTest prueba KPI-INV-01 a 04 con montos en PEN y USD, los filtros program y
year (y su 422 en otro tablero), el ejecutado de la campaña tras JOB-02 y los conjuntos vacíos de
un proyecto ajeno.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `InvestmentDashboardApiTest`.

---

### Tarea 11: Los casos de F5 de `ProjectReadScopeApiTest`

**Files:**
- Modify: `src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt`

**Interfaces:**
- Consumes: `F5ProgramApiTest` (Tarea 9); el alcance de cadenas y padres alternativos del plan 5.1 (`ProjectReadCondition`
  con la cadena `program_measurement → program_indicator → social_program` y la disyunción de `delivery`); `platformCreate`
  (`F3ApiTest`), `commitment` (`F3DomainApiTest`).
- Produces: los casos de §3.2 de la especificación: una persona asignada a A lee la entrega del compromiso y la del aporte de
  A, no las de B; lee las mediciones de A y no las de B; una entrega huérfana no la lee; quien lee todo las lee todas.

- [ ] **Paso 1: La base nueva y los casos**

En `src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt`, reemplazar:

```kotlin
import sgspe.F4ApiTest
```

por:

```kotlin
import sgspe.F5ProgramApiTest
```

y reemplazar:

```kotlin
class ProjectReadScopeApiTest : F4ApiTest() {
```

por:

```kotlin
class ProjectReadScopeApiTest : F5ProgramApiTest() {
```

En el comentario de la clase, reemplazar `ConfidentialRelease dentro de A lo prueba StakeholderRequestWorkflowApiTest` por:

```kotlin
// ConfidentialRelease dentro de A lo prueba StakeholderRequestWorkflowApiTest. desde F5 (especificación §3.2): las mediciones
// (nietas de social_program) y las entregas con dos padres (commitment o social_contribution) se leen por el padre que tienen,
// nunca «sin padre, todos»
```

Agregar antes de la última `}` de la clase:

```kotlin

    // una entrega como la plataforma (delivery es apiOnly; ExactlyOneGuard pide uno de sus dos padres)
    private fun deliveryOf(
        field: String,
        parent: String
    ): String = platformCreate("delivery", mapOf(field to parent, "delivery_date" to "2026-10-10", "conformity_given" to true))

    @Test
    fun `quien es de A lee las entregas de compromisos y de aportes de A y no las de B, y una huerfana solo quien lee todo`() {
        val investorA = member("SOCIAL_INVESTMENT_MANAGER", a)
        val investorB = member("SOCIAL_INVESTMENT_MANAGER", b)
        val mine = setOf(deliveryOf("commitment", commitment(a)), deliveryOf("social_contribution", registerContribution(a, investorA)))
        val theirs = setOf(deliveryOf("commitment", commitment(b)), deliveryOf("social_contribution", registerContribution(b, investorB)))
        // la huérfana: su compromiso en borrador se borra y wasichai deja la relación vacía (decisión 15 del plan 5.2b)
        val doomed = commitment(a)
        val orphan = deliveryOf("commitment", doomed)
        assertEquals(HttpStatus.NO_CONTENT, exchange("DELETE", "/api/objects/commitment/records/$doomed", null, token, withReason("borrado de prueba")).status)
        val everything = mine + theirs + orphan
        assertEquals(mine, ids(listed("delivery", officer)) intersect everything)
        for (id in theirs + orphan) {
            assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/delivery/records/$id", officer), id)
        }
        for (id in mine) assertEquals(HttpStatus.OK, status("GET", "/api/objects/delivery/records/$id", officer), id)
        // decisión 10 del plan 5.2b: AUDITOR lee todo (`*` READ y READ_ALL_PROJECTS), también la huérfana
        val auditor = userWithRole("AUDITOR")
        assertEquals(everything, ids(listed("delivery", auditor)) intersect everything)
    }

    @Test
    fun `quien es de A lee las mediciones de A y no las de B`() {
        val investorA = member("SOCIAL_INVESTMENT_MANAGER", a)
        val investorB = member("SOCIAL_INVESTMENT_MANAGER", b)
        val mine = programMeasurement(programIndicator(socialProgram(a, investorA), investorA), creator = investorA)
        val theirs = programMeasurement(programIndicator(socialProgram(b, investorB), investorB), creator = investorB)
        assertEquals(setOf(mine), ids(listed("program_measurement", officer)) intersect setOf(mine, theirs))
        assertEquals(HttpStatus.NOT_FOUND, status("GET", "/api/objects/program_measurement/records/$theirs", officer))
        assertEquals(HttpStatus.OK, status("GET", "/api/objects/program_measurement/records/$mine", officer))
        assertEquals(setOf(mine, theirs), ids(listed("program_measurement", userWithRole("AUDITOR"))) intersect setOf(mine, theirs))
    }
```

`officer` (`COMMUNITY_RELATIONS_OFFICER` asignado a A), `listed`, `ids` y `status` ya están en la clase; el oficial lee
`delivery` y `program_measurement` (`roles.json`).

- [ ] **Paso 2: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL` (la clase compila sobre la base nueva; sus pruebas de F4 no cambian).

- [ ] **Paso 3: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/scope/ProjectReadScopeApiTest.kt
git commit -F - <<'MSG'
test: alcance de las mediciones y de las entregas con dos padres

ProjectReadScopeApiTest suma que quien es de A lee las entregas de compromisos y de aportes y las
mediciones de A y no las de B, que una entrega huérfana no la lee nadie salvo quien lee todo, y que
AUDITOR las lee todas (especificación de F5 §3.2).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `ProjectReadScopeApiTest` entera (sus casos de F4 corren sobre la base nueva).

---

### Tarea 12: Las matrices: «otro proyecto» de los hijos de BC-10 y «no asignado» de WF-G y WF-P

**Files:**
- Modify: `src/test/kotlin/sgspe/security/PermissionMatrixApiTest.kt`
- Test: `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt` (solo se comprueba, no cambia)

**Interfaces:**
- Consumes: `ProjectScope.ROOTS` y `ProjectScope.CHILDREN: Map<String, List<Pair<String, String>>>` (plan 5.1); la regla del
  año y `FIXED` (Tarea 3); `WorkflowMatrixApiTest` con WF-G y WF-P (plan 5.1).
- Produces: la prueba parametrizada `ningun rol lee ni da de alta un hijo de BC-10 de un proyecto al que no esta asignado`.

La columna «otro proyecto» de hoy prueba las raíces cuyo registro mínimo solo nombra maestros del tenant, así que ya cubre
`social_contribution` (pagador: `organization`) y `social_program`. Los ocho hijos de BC-10 cuelgan de un padre de proyecto
(`program_measurement`, de un abuelo): la prueba nueva arma el padre en el otro proyecto y espera `404` en la lectura y el
`400` de wasichai sobre el campo del padre en el alta. La entrega de un aporte (`apiOnly`) la prueba la Tarea 11. La columna
«no asignado» de `WorkflowMatrixApiTest` recorre todos los workflows de `model/workflows/`, así que WF-G y WF-P entran sin
cambio de código.

- [ ] **Paso 1: La prueba nueva**

En `src/test/kotlin/sgspe/security/PermissionMatrixApiTest.kt`, agregar antes de `    private fun problemFields(body: String)`:

```kotlin
    // la columna «otro proyecto» de los hijos de BC-10 (especificación de F5 §5): un hijo cuyo padre (o abuelo, en
    // program_measurement) es de un proyecto al que el rol no está asignado no se lee (404) y su alta con ese padre es el 400 de
    // wasichai sobre el campo del padre (ASM-108); quien lee todos los proyectos lo lee (200) y no da de alta en él sin
    // asignación (403). la entrega de un aporte (apiOnly) la prueba ProjectReadScopeApiTest
    @ParameterizedTest(name = "{0}")
    @MethodSource("roles")
    fun `ningun rol lee ni da de alta un hijo de BC-10 de un proyecto al que no esta asignado`(role: String) {
        val account = member(role)
        val grants = Model.roles.getValue(role)
        val readsTenant = grants["*"]?.contains("READ") == true
        val readsAll = "READ_ALL_PROJECTS" in grants["project"].orEmpty()
        val other = SHARED.shared.getOrPut(OTHER) { Fixture(project(), emptyMap()) }.id
        val failures = mutableListOf<String>()

        fun expect(
            what: String,
            wanted: HttpStatus,
            response: Response,
            field: String? = null
        ) {
            if (response.status != wanted) {
                failures += "$what: $wanted esperado, ${response.status} ${response.body.take(300)}"
            } else if (field != null && field !in problemFields(response.body)) {
                failures += "$what: el error no nombra $field: ${response.body.take(300)}"
            }
        }
        for (name in BC10_CHILDREN) {
            val (field, parent) = ProjectScope.CHILDREN.getValue(name).first()
            val actions = grants[name].orEmpty()
            val records = "/api/objects/$name/records"
            if ("READ" in actions || readsTenant) {
                val foreign =
                    try {
                        foreign(name, other)
                    } catch (e: Throwable) {
                        failures += "$name: no se pudo armar el registro del otro proyecto, ${e.message?.take(300)}"
                        null
                    }
                if (foreign != null) {
                    expect(
                        "$name READ (otro proyecto)",
                        if (readsAll) HttpStatus.OK else HttpStatus.NOT_FOUND,
                        exchange("GET", "$records/${foreign.id}", token = account.token)
                    )
                }
            }
            if ("CREATE" in actions) {
                val parentRecord =
                    try {
                        foreign(parent, other)
                    } catch (e: Throwable) {
                        failures += "$name: no se pudo armar el padre del otro proyecto, ${e.message?.take(300)}"
                        null
                    }
                if (parentRecord != null) {
                    val created = exchange("POST", records, mapOf("attributes" to sample(name) + (field to parentRecord.id)), account.token)
                    if (readsAll) {
                        expect("$name CREATE (otro proyecto)", HttpStatus.FORBIDDEN, created)
                    } else {
                        expect("$name CREATE (otro proyecto)", HttpStatus.BAD_REQUEST, created, field)
                    }
                }
            }
        }
        assertEquals(emptyList<String>(), failures, "$role:\n${failures.joinToString("\n")}")
    }

    // el registro mínimo de `name` en el otro proyecto, compartido entre los roles: una raíz con ese `project`; un hijo, colgado
    // del registro de su primer padre en el otro proyecto (program_measurement sube dos saltos)
    private fun foreign(
        name: String,
        other: String
    ): Fixture =
        SHARED.shared.getOrPut("$OTHER:$name") {
            if (name in ProjectScope.ROOTS) {
                fixture(name, mapOf("project" to other))
            } else {
                val (field, parent) = ProjectScope.CHILDREN.getValue(name).first()
                fixture(name, mapOf(field to foreign(parent, other).id))
            }
        }

```

En el `companion object`, después de `private const val OTHER = "\u0000other"`, agregar:

```kotlin

        // los hijos de BC-10 (especificación de F5 §2.3, elección 4): por social_contribution, social_program o program_indicator
        private val BC10_CHILDREN =
            listOf(
                "contribution_item",
                "receipt",
                "social_initiative",
                "beneficiary_group",
                "program_budget",
                "program_indicator",
                "program_measurement",
                "program_milestone"
            )
```

- [ ] **Paso 2: «No asignado» de WF-G y WF-P sin diferidas de 5.2b**

```bash
cd ~/IdeaProjects/sgspe
grep -n '"social_program\|"social_contribution' src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
grep -n 'social_program\.\|social_contribution\.' src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt | grep -i 'deferred\|5.2' || echo "sin diferidas de F5"
```

Esperado: las dos líneas de `create` y de la cuenta de transiciones de WF-G y WF-P que dejó el plan 5.1, y `sin diferidas de
F5`. Si 5.1 dejó en `DEFERRED` alguna transición de `social_program` «hasta 5.2b», se quita de la lista en este paso (la regla
que esperaba ya está en las Tareas 2 y 3), con su línea de comentario, y el archivo entra en el commit; las de
`social_contribution` son de 5.2a y no se tocan.

- [ ] **Paso 3: Compilar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 4: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/security/PermissionMatrixApiTest.kt
git commit -F - <<'MSG'
test: columna otro proyecto de los hijos de BC-10 en la matriz de permisos

Un hijo de BC-10 cuyo padre (o abuelo, en program_measurement) es de un proyecto al que el rol no
está asignado no se lee (404) y su alta con ese padre es el 400 de wasichai sobre el campo del
padre; quien lee todos los proyectos lo lee y no da de alta en él sin asignación.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `PermissionMatrixApiTest` (entre 11 y 58 minutos por el túnel según la carga, más
con los diez objetos de F5) y `WorkflowMatrixApiTest`. Para acotar una corrida local: `-Dmatrix.roles=SOCIAL_INVESTMENT_MANAGER,COMMUNITY_RELATIONS_OFFICER,AUDITOR`.

---
### Tarea 13: Tenant de desarrollo en F5 y medición de las subconsultas (coordinador)

**Files:**
- Ninguno versionado salvo, si la medición lo pide, `src/main/kotlin/sgspe/scope/ProjectReadCondition.kt`,
  `src/main/kotlin/sgspe/scope/ProjectReadScope.kt` y `src/test/kotlin/sgspe/scope/ProjectReadConditionTest.kt` (Paso 6). Las
  cifras las anota la Tarea 14 en `docs/roadmap.md`.

**Interfaces:**
- Consumes: las Tareas 2 a 12 integradas en la rama; el alcance de cadenas y padres alternativos del plan 5.1
  (`ProjectReadCondition`); `model/upgrade_tenants.py` (plan 4.4a); el túnel (puerto 5435) y el backend de desarrollo (8094).
- Produces: el tenant de desarrollo en F5 (definición de terminado de §7) y las cifras de la medición de §7 que la Tarea 14
  anota en el roadmap, con la decisión sobre la disyunción de `delivery`.

**Solo el coordinador principal**: exige base, túnel y el tenant de desarrollo. Ningún subagente la ejecuta, salvo el Paso 6
(código), que se delega si la medición lo pide.

- [ ] **Paso 1: El tenant de desarrollo en F5 (coordinador)**

Con el túnel abierto y el backend de la rama corriendo contra la base de desarrollo `sgspe` (`docs/develop/README.md` §4), el
coordinador escribe un inventario **fuera del repositorio** con la forma de `model/tenants.example.json` (por ejemplo
`$TMPDIR/sgspe-tenants-dev.json`, con el slug y el correo del administrador del tenant de desarrollo) y sube el tenant de fase
con el orden de `CLAUDE.md` (`apply.py` → `apply_roles.py` → `apply.py --workflows` → `load_ubigeo.py`, que
`upgrade_tenants.py` repite por tenant):

```bash
cd ~/IdeaProjects/sgspe/model
python3 upgrade_tenants.py --core http://localhost:8094 --inventory "$TMPDIR/sgspe-tenants-dev.json"
python3 upgrade_tenants.py --core http://localhost:8094 --inventory "$TMPDIR/sgspe-tenants-dev.json"
```

La contraseña llega por `SGSPE_TENANT_PASSWORD_<SLUG>` o por consola, nunca por argumento. Esperado: la primera corrida
termina con salida 0 y crea los diez objetos de BC-10, WF-G y WF-P, y los permisos de `SOCIAL_INVESTMENT_MANAGER`; la
segunda responde todo `skipped`. Comprobación:

```bash
TOKEN=$(curl -s localhost:8094/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"<correo del admin del tenant>","password":"<su contraseña>"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
for o in social_program program_measurement social_contribution; do
  curl -s -o /dev/null -w "$o %{http_code}\n" "localhost:8094/api/objects/$o/records?size=1" -H "Authorization: Bearer $TOKEN"
done
curl -s localhost:8094/api/sgspe/dashboards/investment -H "Authorization: Bearer $TOKEN" | python3 -c '
import json,sys; print([i["code"] for i in json.load(sys.stdin)["indicators"]])'
```

Esperado: `200` en los tres objetos y `['KPI-INV-01', 'KPI-INV-02', 'KPI-INV-03', 'KPI-INV-04']`. El inventario no se
versiona ni se copia al repositorio.

- [ ] **Paso 2: Las plantillas de la medición en `sgspe_test` (coordinador)**

Con el backend de la rama apuntado a `sgspe_test` (`WASICHAI_DB_NAME=sgspe_test`, el resto como en `docs/develop/README.md`
§2) y el modelo de F5 aplicado ahí (`apply.py` → `apply_roles.py` → `apply.py --workflows`), el admin crea por la API, como en
la Tarea 17 del plan 4.4a:

1. «Proyecto Ejemplo A» con `policy_internal_approval` en falso (`POST /api/objects/project/records`) y tres personas de
   prueba con un rol cada una, asignadas a él: `analista-medicion@sgspe.test` (`ANALYST`), `aprobador-medicion@sgspe.test`
   (`APPROVER`) e `inversion-medicion@sgspe.test` (`SOCIAL_INVESTMENT_MANAGER`).
2. Como `inversion-medicion`: un programa, un indicador y una medición (`POST /api/objects/<objeto>/records`); un aporte con un
   ítem BIEN, `approve_direct`, `register_procurement` y su entrega capturada sin evidencias
   (`POST /api/sgspe/social-contributions/{id}/deliveries`: la captura no las exige, las exige `register_delivery`, que no hace
   falta aquí).
3. Como el admin: un compromiso con un grupo de interés solicitante, `submit`; como `aprobador-medicion`, `approve`; como el
   admin, `start` y su entrega (`POST /api/sgspe/commitments/{id}/deliveries` con `delivered_amount` 100).

Sus ids son las plantillas `:tpl_project`, `:tpl_program`, `:tpl_indicator`, `:tpl_measurement`, `:tpl_commitment`,
`:tpl_contribution`, `:tpl_delivery_c` (la del compromiso) y `:tpl_delivery_s` (la del aporte).

- [ ] **Paso 3: El volumen de §7 (coordinador)**

En `psql` contra `sgspe_test` (`wasichai` es el esquema de metadata y `data` el de datos por defecto):

```sql
\set org '<id de la organización>'
\set me '<id de analista-medicion>'
SELECT name, physical_table FROM wasichai.custom_objects
 WHERE organization_id = :'org'
   AND name IN ('project', 'project_assignment', 'social_program', 'program_indicator', 'program_measurement',
                'commitment', 'social_contribution', 'delivery');
-- con lo que devuelve, una variable por tabla: \set proj '<tabla>', \set pa …, \set sp …, \set pi …, \set pm …,
-- \set cm …, \set sc … y \set dl …
CREATE TEMP TABLE med_projects AS SELECT gen_random_uuid() AS id, g FROM generate_series(1, 10) g;
INSERT INTO data.:"proj" SELECT (jsonb_populate_record(p, jsonb_build_object('id', m.id, 'code', 'MED-' || m.g,
  'name', 'Proyecto de medición ' || m.g))).* FROM data.:"proj" p, med_projects m WHERE p.id = :'tpl_project';
-- 500 programas, 50 por proyecto
CREATE TEMP TABLE med_programs AS SELECT gen_random_uuid() AS id, m.id AS project, row_number() OVER () AS n
  FROM med_projects m, generate_series(1, 50);
INSERT INTO data.:"sp" SELECT (jsonb_populate_record(s, jsonb_build_object('id', g.id, 'project', g.project,
  'code', 'PRG-MED-' || g.n))).* FROM data.:"sp" s, med_programs g WHERE s.id = :'tpl_program';
-- 5 000 indicadores, 10 por programa
CREATE TEMP TABLE med_indicators AS SELECT gen_random_uuid() AS id, g.id AS program FROM med_programs g, generate_series(1, 10);
INSERT INTO data.:"pi" SELECT (jsonb_populate_record(i, jsonb_build_object('id', x.id, 'social_program', x.program))).*
  FROM data.:"pi" i, med_indicators x WHERE i.id = :'tpl_indicator';
-- 50 000 mediciones, 10 por indicador, una por mes (único program_indicator, period_start_date)
INSERT INTO data.:"pm" SELECT (jsonb_populate_record(m, jsonb_build_object('id', gen_random_uuid(), 'program_indicator', x.id,
  'period_start_date', (DATE '2020-01-01' + make_interval(months => k))::date,
  'period_end_date', (DATE '2020-01-28' + make_interval(months => k))::date))).*
  FROM data.:"pm" m, med_indicators x, generate_series(1, 10) k WHERE m.id = :'tpl_measurement';
-- 20 000 compromisos (2 000 por proyecto) y 5 000 aportes (500 por proyecto), con su código único por proyecto
CREATE TEMP TABLE med_commitments AS SELECT gen_random_uuid() AS id, m.id AS project, row_number() OVER () AS n
  FROM med_projects m, generate_series(1, 2000);
INSERT INTO data.:"cm" SELECT (jsonb_populate_record(c, jsonb_build_object('id', x.id, 'project', x.project,
  'code', 'COM-MED-' || x.n))).* FROM data.:"cm" c, med_commitments x WHERE c.id = :'tpl_commitment';
CREATE TEMP TABLE med_contributions AS SELECT gen_random_uuid() AS id, m.id AS project, row_number() OVER () AS n
  FROM med_projects m, generate_series(1, 500);
INSERT INTO data.:"sc" SELECT (jsonb_populate_record(s, jsonb_build_object('id', x.id, 'project', x.project,
  'code', 'APO-MED-' || x.n))).* FROM data.:"sc" s, med_contributions x WHERE s.id = :'tpl_contribution';
-- 25 000 entregas: una por compromiso y una por aporte (único social_contribution)
INSERT INTO data.:"dl" SELECT (jsonb_populate_record(d, jsonb_build_object('id', gen_random_uuid(), 'commitment', x.id))).*
  FROM data.:"dl" d, med_commitments x WHERE d.id = :'tpl_delivery_c';
INSERT INTO data.:"dl" SELECT (jsonb_populate_record(d, jsonb_build_object('id', gen_random_uuid(), 'social_contribution', x.id))).*
  FROM data.:"dl" d, med_contributions x WHERE d.id = :'tpl_delivery_s';
ANALYZE data.:"proj"; ANALYZE data.:"pa"; ANALYZE data.:"sp"; ANALYZE data.:"pi"; ANALYZE data.:"pm";
ANALYZE data.:"cm"; ANALYZE data.:"sc"; ANALYZE data.:"dl";
```

Después, por la API, el admin asigna `analista-medicion-2@sgspe.test` (`ANALYST`) al proyecto `MED-1`, que tiene la décima
parte del volumen: la medición mira a una persona con un proyecto casi vacío (la plantilla) y a otra con uno lleno. Si un
objeto tiene otro único por organización en `model.json`, su columna va en `jsonb_build_object` con un valor distinto por fila.

- [ ] **Paso 4: `EXPLAIN ANALYZE` con y sin alcance (coordinador)**

Primero se confirma que el SQL que se mide es el que arma el backend: con `logging.level.io.r2dbc.postgresql.QUERY=DEBUG`,
`GET /api/objects/program_measurement/records?size=50` y `GET /api/objects/delivery/records?size=50` como
`analista-medicion`, y se compara la condición del log con la de abajo (los nombres de tabla y columna, los alias `t1` y
`t2` y la disyunción entre paréntesis, §3.2 de la especificación). Si difiere, se mide la del log. Luego, en `psql`, «mis
proyectos» como vista temporal (el planificador la expande igual que la subconsulta):

```sql
CREATE TEMP VIEW med_mine AS SELECT a."project" FROM data.:"pa" a
 WHERE a.organization_id = :'org' AND a."member_user_id" = :'me' AND a."active" IS TRUE
   AND (a."start_date" IS NULL OR a."start_date" <= CURRENT_DATE) AND (a."end_date" IS NULL OR a."end_date" >= CURRENT_DATE);
-- M1: la página de mediciones sin alcance y con la cadena de dos saltos
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"pm" WHERE organization_id = :'org' ORDER BY id LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"pm" WHERE organization_id = :'org'
  AND ("program_indicator" IN (SELECT t1."id" FROM data.:"pi" t1 WHERE t1.organization_id = :'org'
    AND t1."social_program" IN (SELECT t2."id" FROM data.:"sp" t2 WHERE t2.organization_id = :'org'
      AND t2."project" IN (SELECT project FROM med_mine))))
  ORDER BY id LIMIT 50;
-- M2: su conteo
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM data.:"pm" WHERE organization_id = :'org';
EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM data.:"pm" WHERE organization_id = :'org'
  AND ("program_indicator" IN (SELECT t1."id" FROM data.:"pi" t1 WHERE t1.organization_id = :'org'
    AND t1."social_program" IN (SELECT t2."id" FROM data.:"sp" t2 WHERE t2.organization_id = :'org'
      AND t2."project" IN (SELECT project FROM med_mine))));
-- M3: la página de entregas sin alcance y con la disyunción de los dos padres
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"dl" WHERE organization_id = :'org' ORDER BY id LIMIT 50;
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"dl" WHERE organization_id = :'org'
  AND (("commitment" IN (SELECT t1."id" FROM data.:"cm" t1 WHERE t1.organization_id = :'org' AND t1."project" IN (SELECT project FROM med_mine)))
    OR ("social_contribution" IN (SELECT t1."id" FROM data.:"sc" t1 WHERE t1.organization_id = :'org' AND t1."project" IN (SELECT project FROM med_mine))))
  ORDER BY id LIMIT 50;
-- M4: su conteo (las dos consultas de M3 con SELECT count(*), sin ORDER BY ni LIMIT)
-- M5: la reescritura de respaldo de §7, para comparar con M3 y M4
EXPLAIN (ANALYZE, BUFFERS) SELECT id FROM data.:"dl" WHERE organization_id = :'org'
  AND ("id" IN (SELECT c."id" FROM data.:"dl" c WHERE c.organization_id = :'org'
      AND c."commitment" IN (SELECT t1."id" FROM data.:"cm" t1 WHERE t1.organization_id = :'org' AND t1."project" IN (SELECT project FROM med_mine))
    UNION ALL SELECT c."id" FROM data.:"dl" c WHERE c.organization_id = :'org'
      AND c."social_contribution" IN (SELECT t1."id" FROM data.:"sc" t1 WHERE t1.organization_id = :'org' AND t1."project" IN (SELECT project FROM med_mine))))
  ORDER BY id LIMIT 50;
```

Cada consulta tres veces, con `:me` de `analista-medicion` y después con el de `analista-medicion-2` (`\set me …` y
`CREATE OR REPLACE TEMP VIEW med_mine …`); se anota la mediana. Además, con el backend de la rama y `curl`, las dos listas
como cada persona y como el admin, cinco veces cada una (`?size=50` y `?size=1`, que trae el conteo).

**Criterio (§7 de la especificación).** En M1, M2, M3 y M4 con alcance: ningún `Seq Scan` sobre las tablas de los padres
(`data.:"pi"`, `data.:"sp"`, `data.:"cm"`, `data.:"sc"`) ni sobre la de asignaciones, y la página con alcance (M1 y M3) a lo
sumo un 20 % más lenta que sin él. Si solo falla el 20 % por pocos milisegundos (como en la medición del plan 4.4: 6 a 8 ms
frente a 1 ms), se anota tal cual y se consulta al usuario antes de cerrar; no se reescribe nada por eso.

- [ ] **Paso 5: La decisión sobre la disyunción (coordinador)**

- Si M3 y M4 cumplen el criterio: la condición se queda como la dejó 5.1, el Paso 6 no se hace y el PR lo dice.
- Si M3 o M4 hacen `Seq Scan` sobre `data.:"cm"` o `data.:"sc"` (o pasan el 20 % por mucho) y M5 no: se hace el Paso 6.
- Si la cadena (M1 o M2) no usa los índices de las relaciones: no hay reescritura prevista; se anota y se decide con el usuario
  (un índice declarado iría en `model.json` `indexes`, nunca con SQL propio, con su prueba en `test_model.py`).

- [ ] **Paso 6: Solo si el Paso 5 lo pide: la reescritura `UNION ALL` (subagente)**

En `src/test/kotlin/sgspe/scope/ProjectReadConditionTest.kt`, la prueba de la disyunción de `delivery` que dejó 5.1 (la que
arma un hijo con los dos padres) cambia lo que espera: la condición de un hijo con varios padres es
`"id" IN (SELECT c."id" FROM <tabla del hijo> c WHERE c.organization_id = <org> AND c.<columna del primer padre> IN (<cadena del
primer padre>) UNION ALL SELECT c."id" FROM <tabla del hijo> c WHERE c.organization_id = <org> AND c.<columna del segundo
padre> IN (<cadena del segundo padre>))`. Se agrega, con el armado de esa prueba:

```kotlin
    // la reescritura de §7 de la especificación (Tarea 13 del plan 5.2b): la disyunción de dos padres como UNION ALL sobre la
    // tabla del hijo, con el mismo resultado; el NULL de un padre ausente sigue sin cumplir su rama
    @Test
    fun `un hijo con dos padres se lee con una union de sus dos ramas sobre su propia tabla`() {
        val sql = condition(ReadGroup.CHILD, delivery)
        assertTrue(sql.startsWith("\"id\" IN (SELECT c.\"id\" FROM ${delivery.table} c WHERE c.organization_id = "), sql)
        assertTrue(sql.contains(" UNION ALL SELECT c.\"id\" FROM ${delivery.table} c WHERE "), sql)
        assertTrue(sql.contains("c.\"commitment\" IN (SELECT t1.\"id\" FROM "), sql)
        assertTrue(sql.contains("c.\"social_contribution\" IN (SELECT t1.\"id\" FROM "), sql)
        assertFalse(sql.contains(") OR ("), sql)
    }
```

(`condition` y `delivery` son el ayudante y la `ReadColumns` de la prueba de la disyunción de 5.1; si se llaman distinto, se
usan los suyos). `ReadColumns` gana `val table: String? = null`, la tabla física del hijo entrecomillada, que
`ProjectReadScope` llena con `WasichaiSchemas.dataTable` y `SqlIdentifier.quote` como llena las de los saltos; sin ella, un
hijo con varios padres es `NOTHING` (falla cerrada). En `ProjectReadCondition`, la rama de un hijo con más de un padre arma
cada término como hoy pero con la columna calificada `c.<columna>` y los une con ` UNION ALL SELECT c."id" FROM <tabla> c WHERE
c.organization_id = <org enlazado> AND ` dentro de `"id" IN (…)`; la de un único padre no cambia. Commit
`perf: el alcance de una entrega con dos padres como UNION ALL` con el formato de los demás, y la medición M3 y M4 se repite
con la condición nueva antes de anotarla.

---

### Tarea 14: Documentos, verificación final, suite de integración, push y PR

**Files:**
- Modify: `docs/dashboards.md` (§7; §3.1 solo se comprueba), `docs/api.md` (§4.11; §3.3 solo se comprueba), `docs/roadmap.md` (F5, «Estado»), `CLAUDE.md` («Dónde
  está cada cosa»)

**Interfaces:**
- Consumes: todo lo anterior y las cifras de la Tarea 13.
- Produces: la rama `feat/fase-5-2b-programas` en verde y el PR.

- [ ] **Paso 1: `dashboards.md`**

El plan 5.0 ya escribió en `docs/dashboards.md` §3.1 la campaña con KPI-INV-01 y las filas `| Programa | \`program\` |` y
`| Año | \`year\` |` (un `social_program` por UUID, el año de 2000 a 2100, `422` fuera del tablero `investment` y de los
KPI-INV), y en `api.md` §3.3 el `409` sin código de un indicador con mediciones. Comprobarlo:

```bash
cd ~/IdeaProjects/sgspe
grep -c "desde F5 KPI-INV-01" docs/dashboards.md
grep -n "^| Programa | \`program\` |\|^| Año | \`year\` |" docs/dashboards.md | cut -c1-120
grep -c "AppendOnlyReferences" docs/api.md
```

Esperado: `1`, las dos filas y al menos `1`. Si una fila dice otra cosa que este plan (decisiones 1, 2 y 9), se corrige el
documento en este paso con la forma de este plan y se anota en el PR; nada se agrega dos veces.

En §7, reemplazar:

```markdown
`community`, `administrativeUnit`, `areaOfInfluence`, `explorationCampaign` y `scope`. Reglas:
```

por:

```markdown
`community`, `administrativeUnit`, `areaOfInfluence`, `explorationCampaign`, `scope` y `owner`; desde F5, en el tablero
`investment` y en los KPI-INV, también `program` y `year`. Reglas:
```

y agregar, como última viñeta de esa lista de reglas:

```markdown
- Los KPI-INV aplican de los filtros de territorio solo `locality`, con sus sectores; KPI-INV-03 no suma los presupuestos de
  programas cuando viene, porque no tienen territorio, y lo advierte. La cifra titular de KPI-INV-01 a 03 va en PEN (§3.3);
  la de KPI-INV-04 son los alcanzados de los programas, y los aportes y compromisos van aparte en su desglose `source`.
```

- [ ] **Paso 2: `api.md`**

En `docs/api.md` §4.11, en la fila de `GET /api/sgspe/dashboards/{dashboard}`, inmediatamente después del texto
`&scope=&owner=` (y antes de ` → 200`), agregar:

```markdown
 (desde F5, en `investment`, también `&program=&year=`; en otro tablero, `422`)
```

- [ ] **Paso 3: El roadmap y `CLAUDE.md`**

En `docs/roadmap.md`, en la tabla de F5: si tiene una fila `| Estado |`, agregar al final de su celda; si no, agregar después
de la fila `| IDs abiertos | … |` la fila `| Estado | … |` con este texto y las cifras de la Tarea 13:
`` medición del plan 5.2b (2026-10-…): página de 50 mediciones con la cadena de dos saltos <a> ms frente a <b> ms sin alcance y su conteo <c> ms frente a <d> ms; página de 50 entregas con la disyunción de dos padres <e> ms frente a <f> ms y su conteo <g> ms frente a <h> ms, <con o sin> barrido secuencial sobre los padres; <«la disyunción se queda» o «la disyunción pasó a UNION ALL»>; tenant de desarrollo en F5 `` (los valores entre `<>` son los que midió el
coordinador; si la medición no se pudo hacer, «medición pendiente», y el cierre de F5 la sigue exigiendo).

En `CLAUDE.md`, en «Dónde está cada cosa», después de la fila que empieza con «| Inversión social:» (la que el plan 5.0 puso en
lugar de «| Tope de apoyo social (`SupportPolicyGuard`)»; estas filas la detallan, no la repiten), agregar:

```markdown
| Programas sociales: nombres y reglas de WF-G (`Programs`, `ProgramRules`), G1 a G4 (`ProgramApprovalRule`), reglas de los hijos (`ProgramDataRule`), `total_budget` (`ProgramTotalsListener`, `ProgramTotalsRecalculator`) | `src/main/kotlin/sgspe/investment/` |
| KPI-INV y el tablero `investment` (`InvestmentKpis`, filtros `program` y `year`) | `src/main/kotlin/sgspe/dashboard/` |
| Base de las pruebas de integración del plan 5.2b (programas con sus hijos, acuerdos vigentes con partes, aportes hasta entregados) | `src/test/kotlin/sgspe/F5ProgramApiTest.kt` |
```

- [ ] **Paso 4: Todo lo local**

```bash
cd ~/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
(cd model && python3 -m unittest 2>&1 | tail -1 && python3 apply.py --validate-only && python3 load_ubigeo.py --dry-run | tail -1)
python3 tools/check_traceability.py && yarn format:check
git status --short
```

Esperado: `BUILD SUCCESSFUL` con todas las unitarias en verde (las de `main` más 39 de este plan: 6 de `ProgramRulesTest`, 7
de `ProgramApprovalRuleTest`, 5 de `ProgramDataRuleTest`, 3 de `ProgramTotalsTest`, 5 de `ProgramTotalsListenerTest`, 1 de
`ApprovalNoticeListenerTest`, 1 de `ProgramTaskReferenceTest`, 3 de `DashboardFiltersTest`, 5 de `InvestmentKpisTest` y 3 de
`InvestmentDashboardTest`); el modelo `OK`; `check_traceability.py` en verde; prettier sin cambios; un `git
status` vacío tras el commit del Paso 5.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add docs/dashboards.md docs/api.md docs/roadmap.md CLAUDE.md
git commit -F - <<'MSG'
docs: cierre del plan 5.2b con los programas, el tablero y la medición

dashboards.md y api.md dicen los filtros program y year del tablero investment y la cifra de cada
KPI-INV; el roadmap anota la medición de las subconsultas del alcance y el tenant de desarrollo en
F5; CLAUDE.md dice dónde viven las piezas de programas y de KPI-INV.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

- [ ] **Paso 6: La suite de integración (CI del PR o coordinador)**

Las clases de integración corren en la CI del PR con Testcontainers. Si el coordinador las quiere antes, por el túnel y con
`sgspe_sdd_test` libre, en este orden (las de una línea juntas, en una sola JVM):

1. `SocialProgramWorkflowApiTest`, `InvestmentDashboardApiTest` y `ProjectReadScopeApiTest`.
2. `WorkflowMatrixApiTest`.
3. `PermissionMatrixApiTest` (la más larga).
4. La suite entera (`./gradlew integrationTest --rerun`), de 30 minutos a más de una hora por el túnel.

Esperado: verde. Si `ProjectReadScopeApiTest` falla en una ruta de wasichai (la cadena o la disyunción), no se mezcla la rama:
se registra la diferencia con 5.1 y se decide con el usuario.

- [ ] **Paso 7: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin feat/fase-5-2b-programas
gh pr create --base main --title "feat: fase 5, plan 5.2b: programas sociales, tablero de inversión y medición" --body-file - <<'MSG'
## Qué trae

- WF-G en `InvestmentWriteGuard`: G1 (`APPROVE`), G2 (segregación), G3 (sinergia con el socio: acuerdo en `active`, no
  VENCIDO y con el socio como parte) y G4 (`plan_direct` sin aprobación interna), también en la consulta en seco.
- Reglas de los hijos del programa: fechas en orden, año presupuestal de 2000 a 2100, conteos enteros y `PROGRAM_MISMATCH`.
- `social_program.total_budget` con `ProgramTotalsListener` (presupuestos y moneda del programa) y su recalculador de JOB-02.
- Aviso `approval_pending` de WF-G y escalamiento de las tareas de un programa a `close`.
- KPI-INV-01 a 04 y el tablero `investment`, con los filtros `program` y `year` solo ahí.
- Pruebas: `SocialProgramWorkflowApiTest`, `InvestmentDashboardApiTest`, los casos de F5 de `ProjectReadScopeApiTest` y la
  columna «otro proyecto» de los hijos de BC-10 en `PermissionMatrixApiTest`.
- Medición de las subconsultas del alcance (cadena de dos saltos y disyunción de `delivery`) y tenant de desarrollo en F5.

## Pruebas

- Unitarias: las de `main` y 39 nuevas.
- Integración (esta CI): las cuatro clases de arriba, `WorkflowMatrixApiTest` y la suite completa.

## Para revisar

- Decisión 1: el filtro de campaña es `explorationCampaign`, no `campaign` (la especificación §3.9 decía `campaign`).
- Decisión 3: la cifra titular de cada KPI-INV (PEN por defecto) y la forma de sus desgloses, que consume 5.3.
- Decisión 9: borrar un indicador con mediciones es el `409` de wasichai sin código, no `HAS_DEPENDENTS`.
- Decisión 10: la prueba del alcance usa `AUDITOR` (`PROJECT_ADMIN` no lee `delivery`), como la especificación §3.2.
- Decisión 11: `TASK_REFERENCE` con `social_program → close`.
- Medición: las cifras del roadmap y si la disyunción de `delivery` pasó a `UNION ALL`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

---

## Dependencias de otros planes

Lo que este plan supone que entregan los planes anteriores, con el nombre y la forma que usa. Si alguno difiere, la tarea que
lo consume lo ajusta en su paso de implementación y el coordinador lo registra como diferencia en el PR.

**Plan 5.0 (documentos).** `api.md` §3.3 con `SYNERGY_AGREEMENT_REQUIRED`, `PROGRAM_MISMATCH` e `INTERNAL_APPROVAL_REQUIRED`
para WF-G; `workflows.md` WF-G con G1 a G4; `dashboards.md` §3.2 (conjuntos «comprometido» y «ejecutado» de
`social_contribution`), §4.7 y §5.10; ASM-113 a ASM-115 en `requirements-analysis.md`; las filas `program` y
`year` de `dashboards.md` §3.1 (con `explorationCampaign` en KPI-INV-01) y el `409` sin código de un indicador con
mediciones en `api.md` §3.3, que la Tarea 14 solo comprueba. La Tarea 14 suma lo demás de las decisiones 2 y 3
(`dashboards.md` §7 y `api.md` §4.11).

**Plan 5.1 (modelo y alcance)** (contrastado con `2026-10-11-fase-5-1-modelo.md`, «Lo que heredan los planes 5.2a, 5.2b y
5.3»).

- `model.json` con los diez objetos de BC-10 como `database-model.md` §15: `social_program` (con `total_budget` derivado,
  `currency`, `start_date`, `end_date`, `partner_organization`, `agreement`, la acción `APPROVE`), `program_budget` (único
  `social_program`, `budget_year`), `program_measurement` (`appendOnly`, único `program_indicator`, `period_start_date`),
  `beneficiary_group` (`social_initiative` y `locality` opcionales), `contribution_item.item_kind` (ENUM con `BIEN`),
  `delivery.social_contribution`, `task.social_program`.
- `model/workflows/social_program.json` (WF-G con `submit` → `pending_approval`, `approve`, `plan_direct`,
  `start_execution`, `suspend`, `return_to_draft`, `close` y las `cancel_*`) y `social_contribution.json` (WF-P).
- `roles.json` con `SOCIAL_INVESTMENT_MANAGER` (CRU sobre `social_program`, CRU sobre `program_budget`, CR sobre
  `program_measurement`, CRUD sobre los demás hijos, CRU sobre `social_contribution` y `delivery`, CRUD sobre
  `contribution_item`, lectura de F2 a F4, incluidos `agreement`, `agreement_party`, `commitment` y
  `exploration_campaign`, CREATE sobre `evidence` y lector de `delivery.conformity_by`, sin `READ_CONFIDENTIAL`);
  `APPROVER` con `Ru` y `APPROVE` sobre `social_program` y READ sobre `agreement` y `agreement_party`;
  `COMMUNITY_RELATIONS_OFFICER` con READ sobre `delivery` y `program_measurement`; `AUDITOR` con `*` READ y
  `READ_ALL_PROJECTS`.
- `ProjectScope.CHILDREN: Map<String, List<Pair<String, String>>>`: `program_measurement` →
  `[(program_indicator, program_indicator)]`, `program_indicator` → `[(social_program, social_program)]`, `delivery` →
  `[(commitment, commitment), (social_contribution, social_contribution)]`; `social_contribution` y `social_program` en
  `ROOTS`. `ProjectReadCondition` con la cadena de dos saltos (alias `t1`, `t2`) y la disyunción entre paréntesis
  (`ParentPath(column, hops: List<Hop>)`, `Hop(table, column)`), con su prueba de la disyunción de `delivery`, que el Paso 6
  de la Tarea 13 reutiliza si hace falta.
- `F5ApiTest` (sobre `F4ApiTest`) con `socialProgram`, `programBudget`, `programIndicator`, `programMeasurement`,
  `socialInitiative`, `beneficiaryGroup`, `socialContribution(project, creator, attributes, items)`, `contributionItem` y
  `campaign` con las firmas de su plan; `socialContribution` admite `items = 0` y deja el aporte en `requested` con
  `contribution_type` y sin lineamientos incumplidos; `contributionItem` acepta `item_kind` y `line_amount` en sus
  `attributes`.
- `F2ApiTest.FIXED["program_budget"] == mapOf("budget_year" to 2026)` (la Tarea 3 lo comprueba).
- `WorkflowMatrixApiTest` con WF-G y WF-P y sin transiciones de `social_program` diferidas a 5.2b (la Tarea 12 lo
  comprueba); sus aportes y programas los registra el primer rol que los lleva (`SEGREGATED`), así que G2 no la rompe.

**Plan 5.2a (aportes y piezas comunes)** (contrastado con `2026-10-11-fase-5-2a-aportes.md`, «Lo que este plan entrega a
5.2b»).

- `InvestmentWriteGuard.kt`: `interface InvestmentRule { val order: Int; suspend fun findings(change: RecordWrite, dryRun:
  Boolean): List<Finding> }`, `object InvestmentRuleOrder` con `PROGRAM = 400`, y el bean `InvestmentWriteGuard(rules:
  List<InvestmentRule>)` que corre sus reglas en orden sobre `OBJECTS`, se detiene en el primer bloqueo al escribir, las
  junta en seco y vuelve `NotReadable` un `403 OBJECT_NOT_READABLE`; su `OBJECTS` es
  `setOf(Contributions.CONTRIBUTION, Contributions.ITEM, Contributions.DELIVERY)`, que la Tarea 2 amplía con
  `Programs.OBJECTS`.
- `object Synergy` (`POLICY`, `ACTIVE`, `EXPIRED`, `VALIDITY`, `live(state, validity)`, `required(why)` sobre `agreement`):
  G3 usa el mismo criterio que P6.
- `object InvestmentCounts` con `nonNegativeInteger(field, after, changed): Finding?` (los conteos de los grupos).
- `object Contributions` con `CONTRIBUTION`, `COMMITTED` (`approved`, `procured`, `delivered`, `closed`) y `EXECUTED`
  (`delivered`, `closed`): el tablero los usa como define lo comprometido y lo ejecutado.
- `ProjectParameters.POLICIES` con `policy_synergy_requires_agreement` en `true`.
- `ApprovalStep(objectName, submit: String?, pending, approvals, direct = emptyList())` y
  `ApprovalNoticeListener(notices, assignments, catalog, senior, records)` con WF-P en `STEPS`; la Tarea 5 suma WF-G al final
  de la lista y su etiqueta.
- `Records.projectOf` sigue en `sgspe.commitment` (o pasa a `sgspe.common` con la misma firma).
- `ProjectLinkGuard` con `social_program.agreement` (mismo proyecto); `CampaignExecutionRecalculator` con los aportes como
  segunda fuente (KPI-INV-01 muestra el ejecutado tras JOB-02).
- `POST /api/sgspe/social-contributions/{id}/deliveries` con `{ attributes, evidence: [<id>] }` (`geometries` opcional, como
  el de compromisos), que captura la entrega en `procured` sin exigir evidencias; P8 las exige en `register_delivery`.
  `InvestmentWriteGuard` no exige `procured` en el alta de una `delivery` hecha como la plataforma (eso lo exige el endpoint,
  §3.5 paso 1): `ProjectReadScopeApiTest` la crea así.
- WF-P aplicable por `SOCIAL_INVESTMENT_MANAGER` de punta a punta en un proyecto sin aprobación interna: alta y un ítem BIEN en
  `requested`, `approve_direct`, `register_procurement`, la captura y `register_delivery`.

**Plan 5.3 (portal).** Consume lo de «Interfaces con el portal (plan 5.3)»; no entrega nada a este plan.

---

## Resumen

| Tarea | Entrega | Grupo | Integración (CI del PR) |
|---|---|---|---|
| 1 | `Programs` y `ProgramRules` | A | — |
| 2 | G1 a G4 (`ProgramApprovalRule`) | B | `SocialProgramWorkflowApiTest` |
| 3 | Reglas de los hijos (`ProgramDataRule`) y `FIXED` | B | `SocialProgramWorkflowApiTest`, `PermissionMatrixApiTest` |
| 4 | `total_budget`: listener y JOB-02 | B | `SocialProgramWorkflowApiTest` |
| 5 | Aviso de WF-G y tareas de un programa | B | `SocialProgramWorkflowApiTest` |
| 6 | Filtros `program` y `year` | A | `InvestmentDashboardApiTest` |
| 7 | KPI-INV puros | A | `InvestmentDashboardApiTest` |
| 8 | Lecturas y tablero `investment` | C | `InvestmentDashboardApiTest` |
| 9 | `F5ProgramApiTest` y `SocialProgramWorkflowApiTest` | D | `SocialProgramWorkflowApiTest` |
| 10 | `InvestmentDashboardApiTest` | E | `InvestmentDashboardApiTest` |
| 11 | Casos de F5 de `ProjectReadScopeApiTest` | E | `ProjectReadScopeApiTest` |
| 12 | «Otro proyecto» de los hijos de BC-10; «no asignado» comprobado | E | `PermissionMatrixApiTest`, `WorkflowMatrixApiTest` |
| 13 | Tenant de desarrollo en F5 y medición (coordinador) | F | — |
| 14 | Documentos, verificación, suite y PR | G | Suite completa |

## Cobertura de la especificación

| Especificación | Dónde |
|---|---|
| §2.1 decisión 4 (ejecución manual por año; KPI-INV-02 por programa, año y moneda) | Tareas 7 y 8 |
| §2.2 sinergia del programa | Tareas 1 y 2 |
| §2.3 elecciones 1 a 3 (cadenas, padres alternativos, medición en 5.2b) | Tareas 11, 12 y 13 |
| §2.3 elección 5 (`PROGRAM_MISMATCH`) | Tarea 3 |
| §2.3 elección 9 (acuerdo vigente) | Tarea 1 |
| §2.3 elección 11 (conteos y año) | Tareas 1 y 3 |
| §2.3 elección 12 (`ProgramTotalsListener` y su recalculador) | Tarea 4 |
| §2.3 elección 14 (avisos de WF-G) | Tarea 5 |
| §3.1 bajas de los hijos del programa | Tarea 9 (indicador con mediciones), decisión 9 |
| §3.2 pruebas de `ProjectReadScopeApiTest` | Tarea 11 |
| §3.6 WF-G (G1 a G4), otras reglas de los hijos, totales, `program_measurement` `appendOnly` y único | Tareas 1 a 4 y 9 |
| §3.8 aviso de programa | Tarea 5 |
| §3.9 KPI-INV y tablero `investment` | Tareas 6, 7, 8 y 10 |
| §5 `SocialProgramWorkflowApiTest`, `InvestmentDashboardApiTest`, `ProjectReadScopeApiTest`, matrices | Tareas 9, 10, 11 y 12 |
| §7 fila 5.2b, medición, tenant de desarrollo, definición de terminado | Tareas 13 y 14 |
| §8 consecuencia 6 (mediciones que no se corrigen) y riesgos 1, 2 y 4 | Tarea 9 (mediciones), Tareas 7 y 8 (otras monedas), Tarea 13 (costo de las subconsultas) |
