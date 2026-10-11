# SGSPE · Fase 5: especificación de diseño

> **Para agentes:** esta especificación es la entrada de los planes 5.0 (documentos), 5.1 (modelo y alcance), 5.2a
> (aportes), 5.2b (programas y tablero) y 5.3 (portal). No define IDs: los que se nombran en §6 los agrega el plan 5.0 a
> `docs/requirements-analysis.md`, y hasta entonces `docs/` sigue siendo la fuente de verdad. Donde esta especificación
> y `docs/` difieren, el plan 5.0 corrige `docs/` según lo que aquí se decide.

**Objetivo.** Dejar diseñada de punta a punta la Fase 5 («Programas e inversión social», primera de la Release 2) sobre
wasichai 1.0.0 y la maquinaria de F3, F4 y el plan 4.4: los diez objetos de BC-10, WF-G y WF-P, el monto que se aprueba
y se compara con el tope de la campaña, los lineamientos de apoyo evaluados por tipo de ítem y por tema, la entrega del
aporte con su acta, foto y comprobante, la aceptación de una solicitud como aporte y su cierre, el alcance de lectura de
hijos de hijos y de una entrega con dos padres, los indicadores KPI-INV-01 a 04 y el tablero `investment`.

**Arquitectura.** `sgspe.investment` pasa de una regla de compromisos (`SupportPolicyGuard`) a un paquete completo:
`InvestmentWriteGuard` (WF-P, WF-G, ítems y entregas), la regla del tope para aportes junto a la de compromisos, el
endpoint de entrega de aportes, `ContributionTotalsListener`, `ProgramTotalsListener` y sus recalculadores de JOB-02.
Cambian `sgspe.scope` (`ProjectScope` con cadenas y padres alternativos, y su lectura y escritura), `sgspe.attention`
(aceptación como aporte y cierre de la solicitud por el aporte), `sgspe.notification` (avisos de aprobación de WF-G y
WF-P), `sgspe.dashboard` (KPI-INV y tablero `investment`) y `sgspe.common` (montos y orígenes de tareas). Todo escribe
por `sgspe.common.Records` con la marca `SgspeWrite`; las reglas son `RecordWriteGuard` hechas de `WriteRule` puras que
`POST /api/sgspe/transition-checks` corre en seco. Ningún literal de rol en `src/main/kotlin`.

**Fuentes.** Las decisiones del usuario del 2026-10-11 (§2.1, vinculantes); `docs/roadmap.md` (F5); `docs/workflows.md`
(WF-E3, WF-G, WF-P, AUT-04, «Avisos», «Pruebas»); `docs/domain-model.md` (§4.7, §6.10, §7.2, §7.4, §7.5, §8.2);
`docs/database-model.md` (§15 y `delivery`); `docs/requirements-analysis.md` (RF-INV-001 a 004, RN-INV-001 a 006,
GAP-006, GAP-007, GAP-032, ASM-008, ASM-019, ASM-063, ASM-068, ASM-090, ASM-092); `docs/dashboards.md` (§3.2, §4.7,
§5.10); `docs/ux/screens.md` (S42, S43, S44, S57, tablero de inversión); `docs/security.md` (§7.7, §7.9, §8, §9.7);
`docs/api.md` (§3.3, §4.6); `model/roles.json`; las especificaciones de F4 y del plan 4.4; y el código de
`sgspe.investment`, `sgspe.scope`, `sgspe.attention` y `ArchitectureTest`.

## Restricciones globales

- wasichai `1.0.0` exacto (desde wasichai/sgspe#30), Kotlin
  `2.4.20`, Spring Boot `4.1.1`, JDK `25`, Gradle `9.8.0`; español (es-PE) en documentos, comentarios y `detail`,
  nombres técnicos en inglés (ADR-0009); valores ENUM en español MAYÚSCULAS sin tildes.
- Nunca código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; `scope` y `privacy` solo dependen de `common`; los tenants solo con `Organizations`.
- Nunca `asPlatform` dentro de una petición: guardas, comandos y listeners leen y escriben como quien llama; solo JOB-02
  corre como la plataforma en F5, con su motivo.
- Toda condición SQL del alcance con valores enlazados y nombres de tabla y columna de la definición, entrecomillados.
- Un tenant que todavía no tiene el modelo de F5 sigue con F4: todo lo que lee objetos de BC-10 desde código de fases
  anteriores pregunta antes a `TenantModel`.
- Datos de prueba ficticios (`@sgspe.test`, «Proyecto Ejemplo A», «Asociación Ejemplo», «Programa Ejemplo»).

## 1. Propósito y alcance de F5

| Subsistema | Alcance | Objetos | Workflows y piezas |
|---|---|---|---|
| Aportes | Registro con propósito, condiciones, pagador, tipo y tema; ítems por tipo antes de aprobar; aprobación con tope, lineamientos, segregación y sinergia; adquisición; una entrega con conformidad, acta de recepción, foto y comprobante; cierre que cierra la solicitud de origen | `social_contribution`, `contribution_item`, `receipt`; `delivery` como segundo padre | WF-P; `InvestmentWriteGuard`, `ContributionCapRule`, `ContributionTotalsListener` |
| Programas | Programa con socio, convenio de sinergia, presupuesto por año, iniciativas, beneficiarios, indicadores con línea base y meta, mediciones inmutables e hitos | `social_program`, `social_initiative`, `beneficiary_group`, `program_budget`, `program_indicator`, `program_measurement`, `program_milestone` | WF-G; `InvestmentWriteGuard`, `ProgramTotalsListener` |
| Consolidación | Tope y ejecutado de la campaña con compromisos y aportes; ejecución de programas registrada a mano por año (ASM-063) | `exploration_campaign` (derivados) | `SupportPolicyGuard`, recalculadores de JOB-02 |
| Solicitudes | Aceptar una solicitud como aporte; cerrar la solicitud al cerrar su aporte; cerrarla sin ejecución si el aporte se rechaza o se anula | `stakeholder_request.created_contribution` | WF-E3; `Acceptances`, `RequestClosureListener` |
| Alcance por proyecto | Hijos de hijos (`program_measurement`) y entrega con dos padres; mismo proyecto en los vínculos de BC-10 | — | `ProjectScope`, `ProjectReadCondition`, `ProjectResolver`, `ProjectLinks`, `ArchitectureTest` |
| Indicadores y tablero | KPI-INV-01 a 04; tablero `investment` | — | `InvestmentKpis` |

Fuera de F5: el documento emitido «Acta de recepción» y AUT-04 (esperan UPS-23, §2.2); un vínculo de aporte a programa o
un ejecutado de programa calculado (decisión 4); los hitos de programa con plazo y escalamiento (JOB-01 no los mide); la
regla de RN-INV-005 sobre «caseríos sin beneficio laboral» (sin dato, GAP-033 propuesta en §6); una exportación de
inversión (T2 la consume en F8); un tipo de cambio para sumar monedas (ASM-063).

## 2. Decisiones

### 2.1 Las cuatro decisiones del usuario (2026-10-11)

| # | Decisión | Modelo (5.1) | Backend (5.2) | Portal (5.3) |
|---|---|---|---|---|
| 1 | **Monto del aporte.** Los ítems se registran en `requested`, antes de aprobar; `total_amount` (suma de `line_amount`) es lo que se aprueba y se compara con el tope de la campaña. Tras aprobar, los ítems quedan congelados: crear, cambiar o borrar un ítem de un aporte fuera de `requested` es `409`; un cambio es otro aporte. Cuentan como «comprometido» `approved`, `procured`, `delivered` y `closed` | `register_procurement` deja de exigir ítems; conjunto «comprometido» de `social_contribution` en `dashboards.md` §3.2 | Regla P9 (ítems al aprobar) e `ITEMS_FROZEN` en `InvestmentWriteGuard`; el tope suma compromisos y aportes comprometidos (§3.4) | S44 edita los ítems solo en `requested` y muestra el total que se aprobará |
| 2 | **Una sola entrega por aporte.** Un aporte tiene como máximo una `delivery`; registrarla (con conformidad y evidencias ACTA DE RECEPCION, FOTO y COMPROBANTE, P8) lo lleva a `delivered`. Lo que se entrega en partes son aportes distintos | Único (`social_contribution`) en `delivery` | `409 DELIVERY_EXISTS`; la captura solo en `procured`; P8 sobre esa entrega (§3.5) | S44 ofrece «Registrar entrega» una vez y después la muestra |
| 3 | **Lineamientos por tipo de ítem.** Cada ítem declara `item_kind` (BIEN, SERVICIO, DINERO, MANO DE OBRA) y el aporte un tema (`topic`, como `social_program.topic`). Las políticas se evalúan solas al aprobar: `policy_no_paid_labor` (ningún ítem MANO DE OBRA), `policy_non_monetary_donations` (ningún ítem DINERO en una DONACION), `policy_priority_warning` (tema fuera de las prioridades, RN-INV-005). Cualquiera de esas tres incumplida exige `guideline_deviation_justification` para aprobar (P3). La sinergia es un bloque aparte: si el aporte la tiene, exige un acuerdo vigente (P6), sin justificación posible | ENUM nuevo `item_kind`; `contribution_item.item_kind` obligatorio; `social_contribution.topic`; M2M `project_priority_topic` (§3.1) | `ContributionGuidelines` puro (§3.4) dentro de `InvestmentWriteGuard`; P6 como regla propia | «Antes de aprobar» en S44 lista cada lineamiento y pide la justificación; S11 edita los temas priorizados |
| 4 | **Ejecución del programa manual por año.** `program_budget.executed_amount` se registra a mano (ASM-063 tal cual); KPI-INV-02 lo compara con `allocated_amount` por año y moneda. Sin vínculo aporte → programa en F5 | Ninguno | KPI-INV-02 por programa, año y moneda | S43 edita el ejecutado en la tabla de presupuestos |

### 2.2 Valores por defecto aceptados

| Tema | Valor | Consecuencia |
|---|---|---|
| Autoridad sobre montos (GAP-006, sigue abierta) | Un solo nivel: aprueba quien tiene `APPROVE`; superar un tope que bloquea exige `APPROVE_OVER_CAP` sobre `social_contribution`, que tiene `SOCIAL_MANAGER` (`security.md` §7.7) | La autoridad del tope difiere entre compromisos (`PROJECT_MANAGER`) y aportes (`SOCIAL_MANAGER`): diferencia documentada y aceptada (ASM-119) |
| Ciclo de los programas (GAP-007) e indicadores (GAP-032) | WF-G con el ciclo del prompt; indicadores con línea base y meta | Las dos brechas siguen abiertas; F5 no agrega estados ni tipos de indicador |
| Sinergia del programa | Se exige un acuerdo al aprobar o planificar si el programa tiene `partner_organization` y `policy_synergy_requires_agreement` está activa | Regla G3 de §3.6 |
| «Acta de recepción» y AUT-04 | Esperan UPS-23, como AUT-03 (ASM-090): `delivery` tiene `recipients` y `conformity_by` restringidos. El acta firmada se sube como evidencia ACTA DE RECEPCION (P8) | `test_model.py` trata AUT-04 como «F5, tras UPS-23»; se corrigen `roadmap.md` F5 y S44, que la daban por hecha (§6, C4) |
| Comprobante | `receipt` estructurado es opcional; P8 exige la evidencia COMPROBANTE | Ninguna regla compara `receipt.amount` con `total_amount` |
| Aceptar una solicitud como aporte | Solo quien tiene CREATE sobre `social_contribution` y el rol de `accept`; `COMMITMENT_MANAGER` tiene `accept` pero no CREATE: `403` coherente | `api.md` §4.6 y S57 lo dicen |

### 2.3 Elecciones de esta especificación más allá de las decisiones

1. **Cadenas en el alcance por proyecto.** `ProjectScope.CHILDREN` admite un padre que a su vez es hijo, siempre que la
   cadena llegue a una raíz en a lo sumo dos saltos y sin ciclos (`program_measurement` → `program_indicator` →
   `social_program`). La condición anida una subconsulta por salto y `ProjectResolver` y `linkedProject` suben la
   cadena. No se copia `project` en los nietos: un dato repetido podría discrepar del padre y obligaría a una regla más
   (§3.2).
2. **Padres alternativos.** `CHILDREN` pasa a dar, por hijo, una lista de padres posibles; `delivery` tiene dos
   (`commitment` y `social_contribution`, exactamente uno, `ExactlyOneGuard`). La lectura es la disyunción por el padre
   presente; un hijo con varios padres nunca se lee «sin padre, todos». La forma «sin padre, del tenant» queda solo para
   un hijo con un único padre opcional (`locality_profile`) (§3.2).
3. **El alcance llega con el modelo (plan 5.1).** El reparto propuesto ponía `ProjectScope` y `ArchitectureTest` en 5.1
   y el resto del alcance en 5.2. Se mueve todo el alcance a 5.1: en cuanto `model.json` tiene
   `delivery.social_contribution` y `program_measurement`, una entrega de aporte se leería en todo el tenant y
   `ArchitectureTest` falla a propósito, así que `main` no puede esperar a 5.2. La medición de las subconsultas queda en
   5.2b (§7).
4. **Clasificación de BC-10.** Raíces: `social_contribution` y `social_program`. Hijos: `contribution_item` y `receipt`
   (por `social_contribution`); `social_initiative`, `beneficiary_group`, `program_budget`, `program_indicator` y
   `program_milestone` (por `social_program`); `program_measurement` (por `program_indicator`). Quedan 33 raíces y 27
   hijos.
5. **Mismo proyecto en BC-10.** `ProjectLinkGuard.OBJECTS` suma `social_contribution`, `social_program` y `task` (que
   gana `social_program`; hasta F4 sus orígenes no se juzgaban). `PROJECT_MISMATCH` cubre `exploration_campaign`,
   `stakeholder`, `commitment` y `agreement` del aporte, `agreement` del programa y los orígenes de la tarea. Dentro de
   un programa, `beneficiary_group.social_initiative` debe ser del mismo programa: `409 PROGRAM_MISMATCH`.
6. **Un tope, dos objetos.** El tope de la campaña suma los compromisos comprometidos (`amount`) y los aportes
   comprometidos (`total_amount`) en la moneda de la campaña, con el mismo candado `exploration_campaign:<id>`.
   `SupportPolicyGuard` (compromisos) gana los aportes en la suma y una regla gemela, `ContributionCapRule`, juzga la
   aprobación del aporte con `APPROVE_OVER_CAP` sobre `social_contribution`. Las dos usan la misma pieza pura
   (`SupportCap`) y el mismo lector de lo comprometido (`CampaignCommitted`). El aporte que se aprueba cuenta con la
   suma de sus ítems leída en la transacción, nunca con el derivado guardado.
7. **Términos aprobados congelados.** Además de los ítems, fuera de `requested` no cambian los campos que juzgó la
   aprobación: `currency`, `contribution_type`, `topic`, `exploration_campaign`, `has_institutional_synergy`,
   `agreement`, `guideline_deviation_justification` y `project` (`409 CONTRIBUTION_FROZEN`). Sin esto, la decisión 1 se
   saltaría cambiando la moneda o la campaña.
8. **Datos que los lineamientos necesitan.** `contribution_type` se exige al aprobar (`400 FIELD_REQUIRED`); las
   prioridades de RN-INV-005 son los temas priorizados del proyecto (M2M `project_priority_topic`, RF-INV-002) y un tema
   cuenta si es priorizado o desciende de uno (`parent_topic`, hasta 10 niveles). Un proyecto sin temas priorizados no
   evalúa la prioridad y lo advierte (`PRIORITIES_NOT_CONFIGURED`, sin bloquear). «Caseríos sin beneficio laboral» no se
   evalúa: no hay dato (GAP-033).
9. **La sinergia del aporte es un bloque propio (P6).** No entra en los lineamientos que se justifican: con
   `has_institutional_synergy` verdadero y `policy_synergy_requires_agreement` activa, `approve` y `approve_direct`
   exigen un acuerdo vigente (`409 SYNERGY_AGREEMENT_REQUIRED` sobre `agreement`), con o sin justificación. «Acuerdo
   vigente», con el mismo criterio que G3 del programa: en `active` y con `validity_status` distinto de VENCIDO. El
   programa exige además que el socio sea parte del acuerdo (G3); el aporte no tiene socio y no lo exige.
10. **Entrega del aporte.** Se captura solo en `procured` (`409 STATE_MISMATCH` en otro estado) por
    `POST /api/sgspe/social-contributions/{id}/deliveries`; se corrige con `PATCH /api/sgspe/deliveries/{id}` mientras
    el aporte siga en `procured`; desde `delivered` (y en `cancelled`) la entrega y sus vínculos de evidencia quedan
    congelados (`409 DELIVERY_FROZEN` sobre `social_contribution`). Su `delivered_amount` va vacío
    (`400 FIELD_NOT_ALLOWED`): el ejecutado de un aporte es su `total_amount` (ASM-063).
11. **Montos y conteos de F5.** `AmountFieldsGuard` (ASM-092) suma `contribution_item.line_amount` y `unit_cost`,
    `receipt.amount`, `program_budget.allocated_amount` y `executed_amount`. `contribution_item.quantity`,
    `program_indicator.baseline_value` y `target_value` y `program_measurement.measured_value` van a `NOT_MONEY`. Los
    conteos (`beneficiaries_count`, `target_count`, `reached_count`) son enteros no negativos y `budget_year` va de 2000
    a 2100 (`400 OUT_OF_RANGE`). La moneda ya es obligatoria en el modelo donde hay dinero.
12. **Totales derivados.** `ContributionTotalsListener` mantiene `total_amount` con cada ítem y en `approve` y
    `approve_direct` (así lo aprobado queda escrito); `ProgramTotalsListener` mantiene `social_program.total_budget` con
    los presupuestos en la moneda del programa (sin moneda del programa, vacío). JOB-02 tiene un recalculador por cada
    uno y `CampaignExecutionRecalculator` suma una segunda fuente, `social_contribution`.
13. **Solicitudes.** `Acceptances` acepta `creates = social_contribution` (`201 { request, contribution }`);
    `created_contribution` pasa a campo de comando (`403 COMMAND_ONLY`); `RequestClosureListener` corre también en
    `close` de WF-P; WF-E3 suma `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted`; `close_unfulfilled` admite
    el aporte en `rejected` o `cancelled`.
14. **Avisos de aprobación.** `ApprovalNoticeListener.STEPS` suma WF-G (`submit` → roles de `approve`) y WF-P en el alta
    (`ApprovalStep` gana el modo «al crear»): roles de `approve` y, con `policy_internal_approval` en falso, también los
    de `approve_direct`. El aviso de WF-P se resuelve al salir de `requested`.
15. **Permisos que no cerraban.** Registrar el aporte exige poder cargar sus ítems antes de aprobar: `SOCIAL_MANAGER` y
    `COMMUNITY_RELATIONS_OFFICER` pasan de R a CRUD en `contribution_item`, y `COMMUNITY_RELATIONS_OFFICER`, que
    registra entregas, de R a CRU en `receipt`. En los programas, `SOCIAL_MANAGER` conserva RU sobre `social_program` y
    R sobre sus hijos: conduce la evaluación, la suspensión y el cierre, y los datos del programa (presupuesto y
    ejecutado, indicadores, mediciones, iniciativas, hitos y beneficiarios) tienen un solo dueño,
    `SOCIAL_INVESTMENT_MANAGER`. S43 se corrige en ese sentido (§4.2).
16. **Plan 5.2 dividido** en 5.2a (aportes, tope, solicitudes y avisos) y 5.2b (programas, indicadores, tablero,
    matrices y medición) (§7).

## 3. Diseño por subsistema

### 3.1 Piezas comunes

- **Orígenes de una tarea (`ExactlyOneGuard`).** El grupo de `task` suma `social_program`; el de `delivery` pasa a
  (`commitment`, `social_contribution`).
- **Vínculos de evidencias.** `POST /api/sgspe/evidence/{id}/links` toma sus destinos del modelo (`evidence_<objeto>`):
  con `evidence_social_contribution` y `evidence_social_program` en `model.json` no cambia código. `EvidenceWriteGuard`
  sigue exigiendo que la evidencia sea del proyecto del destino; el de una entrega de aporte sale de la cadena (§3.2).
- **Montos (`AmountFieldsGuard`).** Elección 11: `INVALID_NUMBER` u `OUT_OF_RANGE` sobre el campo, en el alta y en todo
  cambio de su valor; `AmountFieldsGuardTest` sigue exigiendo que cada DECIMAL no derivado esté en `FIELDS` o en
  `NOT_MONEY`.
- **Temas priorizados.** M2M `project_priority_topic` (`project` → `topic`, «Temas priorizados para aportes»): lo
  vinculan quienes editan las políticas del proyecto (`PROJECT_ADMIN`, `PROJECT_MANAGER`), con UPDATE sobre `project` y
  `u` sobre `topic` (§5.6 de `security.md`). `topic` es un maestro del tenant: no hay regla «mismo proyecto».
- **Campos de comando.** `AttentionCommandFieldsGuard` suma `stakeholder_request.created_contribution`: solo lo escribe
  la aceptación; una persona que lo fija, cambia o vacía recibe `403 COMMAND_ONLY` sobre el campo.
- **Transiciones de comando.** Ninguna nueva: WF-G y WF-P son transiciones humanas. `accept` y `close_accepted` de WF-E3
  ya lo son.
- **Motivo de transición.** WF-P es `requiresReason`; WF-G ya está en `TransitionReasonGuard.REQUIRED`.
- **Idempotencia.** El endpoint de entrega de aportes honra `Idempotency-Key`; la aceptación ya lo hace.
- **Bajas.** `DependentsDeleteGuard` lo cubre con el catálogo. `SOCIAL_INVESTMENT_MANAGER` borra iniciativas, grupos de
  beneficiarios, indicadores e hitos; un indicador con mediciones no se borra: como `program_measurement` es
  `appendOnly`, wasichai rechaza antes la baja con un `409` sin código (`AppendOnlyReferences`), no `HAS_DEPENDENTS`.
  Un ítem se borra solo en `requested` (§3.3).

### 3.2 Alcance por proyecto: cadenas y padres alternativos

**Forma.** `ProjectScope.CHILDREN: Map<String, List<Pair<String, String>>>`: para cada hijo, sus padres posibles en
orden, como (campo RELATION, objeto padre). Todos los hijos de hoy tienen una sola entrada; `delivery` tiene
`[commitment → commitment, social_contribution → social_contribution]`. `ProjectScope.chain(child, field)` da los saltos
hasta la raíz y `ProjectScope.parentOf(record)` el padre presente.

**Lectura (`ProjectReadCondition`).** Para un hijo, por cada padre posible `(f, P)`:

- si `P` es una raíz, como hoy:
  `"f" IN (SELECT t1."id" FROM <P> t1 WHERE t1.organization_id = :org AND t1."project" IN (<mis proyectos>))`;
- si `P` es un hijo con padre `(g, Q)`: la misma forma con `t1."g" IN (<la subconsulta de Q con alias t2>)`, un alias
  por salto.

La condición del hijo es la disyunción, cada término entre paréntesis. Ejemplos:

```sql
-- program_measurement (dos saltos)
"program_indicator" IN (SELECT t1."id" FROM <program_indicator> t1 WHERE t1.organization_id = :org
  AND t1."social_program" IN (SELECT t2."id" FROM <social_program> t2 WHERE t2.organization_id = :org
  AND t2."project" IN (<mis proyectos>)))
-- delivery (dos padres)
("commitment" IN (SELECT t1."id" FROM <commitment> t1 WHERE t1.organization_id = :org
  AND t1."project" IN (<mis proyectos>)))
OR ("social_contribution" IN (SELECT t1."id" FROM <social_contribution> t1 WHERE t1.organization_id = :org
  AND t1."project" IN (<mis proyectos>)))
```

- Un `NULL` en el campo de un término no lo cumple: una entrega huérfana (los dos vacíos, por el `ON DELETE SET NULL` de
  wasichai) no la lee nadie salvo `ADMIN` y quien tiene `READ_ALL_PROJECTS`.
- `"f" IS NULL OR …` solo para un hijo con un único padre opcional (`locality_profile`), como hoy.
- `ProjectReadScope` resuelve con `MetadataService` la definición de cada objeto de la cadena (en caché de wasichai); si
  a una le falta la columna de su salto o `project`, `NOTHING` con advertencia (falla cerrado).
- `ReadColumns.parent` pasa a `parents: List<ParentPath>`, y cada `ParentPath` lleva la columna del hijo y los saltos
  (tabla y columna de cada uno) hasta la columna `project` de la raíz.

**Escritura (`ProjectResolver`).** Para un hijo toma el padre presente antes y después (de cualquier alternativa) y sube
la cadena leyendo como quien escribe; `field` es el campo del padre presente (el primero de la lista si no hay ninguno).
Un registro existente de un hijo con varios padres que no tiene ninguno es `unresolved` (como un hijo con padre
obligatorio huérfano); un alta sin ninguno la rechaza antes `ExactlyOneGuard` (`400 EXACTLY_ONE`).

**Vínculos (`ProjectLinks.linkedProject`).** Para un destino hijo toma el padre presente y sube la cadena, leyendo cada
salto como quien escribe; un salto que no se lee es `NotReadable`.

**`ArchitectureTest`.** Reemplaza las dos pruebas del alcance por estas reglas:

1. Todo objeto de `model.json` está en un solo grupo; `ProjectScope` no nombra objetos ausentes (sin cambio).
2. Los objetos con relación `project` son exactamente `ROOTS` más `project_assignment` (sin cambio).
3. Cada padre posible `(f, P)` de un hijo es una relación `(hijo, f, P)` del modelo, y `P` está en `ROOTS` o en
   `CHILDREN`.
4. Desde cada hijo, toda cadena llega a una raíz en a lo sumo dos saltos y ningún objeto es su propio ancestro.
5. Un hijo que es padre de otro (`program_indicator`) tiene un solo padre posible, con relación obligatoria.
6. Un hijo con varios padres tiene todas esas relaciones opcionales, forman exactamente un grupo de `ExactlyOneGuard` de
   ese objeto, y no tiene otra relación hacia un objeto de proyecto fuera de sus padres (hoy, solo `delivery`).
7. Los hijos con un único padre opcional son exactamente `{locality_profile}` y no tienen otra relación hacia un objeto
   de proyecto.

**Pruebas.** `ProjectReadConditionTest` suma la cadena de dos saltos, la disyunción, la entrega huérfana (no admite) y
la columna faltante en un salto (`NOTHING`). `ProjectResolverTest` y la prueba de `linkedProject` suman la cadena y los
dos padres. `ProjectReadScopeApiTest` (5.2b) suma: una persona asignada a A lee la entrega del compromiso y la del
aporte de A, no las de B; lee las mediciones de A y no las de B; una entrega huérfana no la lee; `AUDITOR` (READ sobre
todo y `READ_ALL_PROJECTS`) lee todas. `PROJECT_ADMIN` no tiene READ sobre `delivery`.

### 3.3 Aportes: objetos y WF-P

**Objetos.** Las fichas de `database-model.md` §15, con estos cambios:

- `social_contribution`: relación nueva `contribution_topic` (`topic` → `topic`, opcional), como `social_program.topic`;
  `total_amount` sigue derivado.
- `contribution_item`: campo nuevo `item_kind` (ENUM `item_kind`: BIEN · SERVICIO · DINERO · MANO DE OBRA, obligatorio).
  `line_amount` es el monto que cuenta; `quantity` y `unit_cost` son informativos y no se comparan con él.
- `delivery`: único sobre `social_contribution`, declarado como `"unique": true` en la relación
  `delivery_social_contribution` (wasichai 1.0.0 rechaza un `uniqueConstraints` de un solo campo; las entregas de
  compromisos lo llevan vacío y no chocan, porque un `UNIQUE` no compara los `NULL`). Si wasichai no lo aplicara, queda
  la regla con candado de §3.5.
- `stakeholder_request.created_contribution` (ya diseñado «desde F5»); `task.social_program`; las M2M
  `evidence_social_contribution` y `evidence_social_program`; la M2M `project_priority_topic`.

**WF-P.** Como lo diseña `workflows.md`, con estos cambios: `register_procurement` ya no exige ítems (los exige la
aprobación, P9); `register_delivery` exige P8 sobre la única entrega; AUT-04 sale de los efectos hasta UPS-23. Las
reglas P1 a P9:

| Regla | Transiciones o escrituras | Pieza | Error |
|---|---|---|---|
| P1 · Tope de la campaña | `approve`, `approve_direct` | `ContributionCapRule` (§3.4) | `409 SUPPORT_CAP_EXCEEDED` o `CAP_DATA_INVALID` sobre `exploration_campaign` si la política bloquea y quien aprueba no tiene `APPROVE_OVER_CAP`; si no, advertencia; `CAP_OTHER_CURRENCY` siempre advertencia |
| P2 y P3 · Lineamientos | `approve`, `approve_direct` | `ContributionGuidelines` (§3.4) | Por cada lineamiento incumplido, `PAID_LABOR` o `MONETARY_DONATION` (sobre `contribution_item`) o `PRIORITY_NOT_MET` (sobre `topic`): `409` si `guideline_deviation_justification` está vacía, advertencia si está informada |
| P4 · Doble llave | `approve` | `InvestmentWriteGuard` | `403 ACTION_REQUIRED` sobre `record` sin `APPROVE` sobre `social_contribution` |
| P5 · Segregación | `approve` | `InvestmentWriteGuard` | `403 SEGREGATION_OF_DUTIES` sobre `record` si quien aprueba es `created_by` del aporte (quien lo registró o quien aceptó la solicitud que lo creó), como D4 |
| P6 · Sinergia | `approve`, `approve_direct` | `InvestmentWriteGuard`, fuera de `ContributionGuidelines` (elección 9) | Con `has_institutional_synergy` verdadero y `policy_synergy_requires_agreement` activa: `409 SYNERGY_AGREEMENT_REQUIRED` sobre `agreement` si el aporte no tiene acuerdo, o no está en `active`, o su `validity_status` es VENCIDO; la justificación no lo cubre |
| P7 · Sin aprobación interna | `approve_direct` | `InvestmentWriteGuard` | `409 INTERNAL_APPROVAL_REQUIRED` sobre `project` con `policy_internal_approval` activa |
| P8 · Entrega con conformidad y evidencia | `register_delivery` | `InvestmentWriteGuard`, con el candado del aporte | `409 DELIVERY_REQUIRED` sobre `delivery` sin la entrega, sin `conformity_given` verdadero o sin `conformity_by`; `403 FIELD_NOT_READABLE` sobre `delivery.conformity_by` si quien la aplica no lo lee; `409 EVIDENCE_REQUIRED` sobre `evidence_delivery` sin al menos una ACTA DE RECEPCION, una FOTO y un COMPROBANTE del proyecto del aporte vinculados a la entrega |
| P9 · Ítems al aprobar | `approve`, `approve_direct` | `InvestmentWriteGuard` | `409 ITEMS_REQUIRED` sobre `contribution_item` sin ítems; `400 FIELD_REQUIRED` sobre `contribution_type` vacío |

**Orden.** En una aprobación: P7, P9, P4 y P5, P6, P2/P3 y al final P1, la única que toma el candado de la campaña.
Antes, la aprobación toma el candado del aporte (`social_contribution:<id>`), el mismo de las escrituras de ítems, así
que un ítem no se cuela entre la suma y la transición; el orden de candados es siempre aporte y después campaña. La
escritura se detiene en el primer bloqueo; la consulta en seco las junta todas. Una regla que no puede leer lo que
necesita rechaza (`403 OBJECT_NOT_READABLE`), nunca deja pasar.

**Otras reglas de `InvestmentWriteGuard` sobre aportes.**

| Regla | Cuándo | Error |
|---|---|---|
| Ítems congelados | Alta, cambio o baja de un `contribution_item` cuyo aporte no está en `requested`; cambiar el `social_contribution` de un ítem en cualquier estado. Toma el candado del aporte | `409 ITEMS_FROZEN` sobre `social_contribution` |
| Términos aprobados | Fuera de `requested`, un cambio de `currency`, `contribution_type`, `topic`, `exploration_campaign`, `has_institutional_synergy`, `agreement`, `guideline_deviation_justification` o `project` del aporte | `409 CONTRIBUTION_FROZEN` sobre el campo |
| Conteos | `beneficiaries_count` negativo o no entero | `400 OUT_OF_RANGE` sobre el campo |

### 3.4 Tope y lineamientos

**Lo comprometido de una campaña (`CampaignCommitted`, `sgspe.investment`).** Lee como quien aprueba, dentro del candado
de la campaña:

- compromisos de la campaña en `approved`, `in_progress`, `blocked`, `fulfilled`, `verified` o `closed`, con `amount` y
  `currency` (como hoy);
- desde F5 y solo si el tenant tiene `social_contribution` (`TenantModel`), aportes de la campaña en `approved`,
  `procured`, `delivered` o `closed`, con `total_amount` y `currency`;
- excluye el registro que se aprueba; un monto guardado inválido se omite y cuenta para `CAP_DATA_INVALID`, como hoy;
- el tope de lecturas (`MAX_COMMITMENTS`, 20 000) rige por objeto.

**`SupportPolicyGuard` (compromisos)** usa `CampaignCommitted` en lugar de su propia lectura: un compromiso que se
aprueba cuenta también los aportes comprometidos. Sin otro cambio.

**`ContributionCapRule` (aportes)** es la misma regla para `approve` y `approve_direct` de WF-P: candidato = suma de
`line_amount` de los ítems leída en la transacción, en la moneda del aporte; `SupportCap.evaluate` contra el tope;
`blocks` lee `policy_cap_blocks_approval` del proyecto; `overCap` pregunta `APPROVE_OVER_CAP` sobre
`social_contribution`. Un aporte sin campaña o una campaña sin tope no se juzgan; una campaña sin moneda nunca bloquea
(`CAP_OTHER_CURRENCY`). La lógica común (lectura del tope, hallazgos, error de datos) sale a una pieza compartida por
las dos reglas.

**`ContributionGuidelines` (pura).** Recibe las políticas del proyecto, los temas priorizados (con sus ancestros
resueltos), los ítems (`item_kind`), `contribution_type`, `topic` y la justificación, y devuelve un hallazgo por
lineamiento:

| Política (activa) | Se incumple si | Código y campo |
|---|---|---|
| `policy_no_paid_labor` | Algún ítem es MANO DE OBRA | `PAID_LABOR`, `contribution_item` |
| `policy_non_monetary_donations` | `contribution_type` es DONACION y algún ítem es DINERO | `MONETARY_DONATION`, `contribution_item` |
| `policy_priority_warning` | El proyecto tiene temas priorizados y el aporte no tiene `topic` o su tema no es priorizado ni desciende de uno | `PRIORITY_NOT_MET`, `topic` |
| `policy_priority_warning` | El proyecto no tiene temas priorizados | `PRIORITIES_NOT_CONFIGURED`, `topic`; siempre advertencia, nunca exige justificación |

Cada incumplimiento bloquea (`409`) si `guideline_deviation_justification` está vacía y es advertencia si está
informada: la justificación cubre todos los lineamientos incumplidos a la vez, como pide RN-INV-006. Una política
apagada no se evalúa. La sinergia no es un lineamiento: es P6 (§3.3), cuyo acuerdo es del mismo proyecto por
`ProjectLinkGuard`.

### 3.5 Entrega del aporte

**`POST /api/sgspe/social-contributions/{id}/deliveries`** (`ContributionDeliveries`, `sgspe.investment`), con
`{ attributes, geometries, evidence[] }` → `201 { delivery, contribution }`, `Idempotency-Key`, `If-Match` del aporte
y `X-Change-Reason`. En una transacción y con el candado del aporte:

1. el aporte está en `procured`; si no, `409 STATE_MISMATCH` sobre `record`;
2. no tiene entrega; si no, `409 DELIVERY_EXISTS` sobre `social_contribution`;
3. `attributes` sin `delivered_amount` (`400 FIELD_NOT_ALLOWED` sobre `attributes.delivered_amount`), sin `commitment`
   ni `social_contribution` (los fija el comando);
4. crea la `delivery` con `social_contribution` y vincula cada evidencia (`EvidenceWriteGuard` exige el proyecto del
   aporte);
5. no cambia el estado: `register_delivery` es la transición humana que sigue (P8).

Permisos: CREATE sobre `delivery`, UPDATE sobre `social_contribution` y sobre las `evidence` vinculadas
(`SOCIAL_INVESTMENT_MANAGER`, `COMMUNITY_RELATIONS_OFFICER`).

**Corrección.** `PATCH /api/sgspe/deliveries/{id}` (`sgspe.commitment`) admite la entrega de un aporte: no recalcula
ningún ejecutado y no cambia `social_contribution` (ya es fijo). Las reglas de la entrega de un aporte viven en
`InvestmentWriteGuard`, que ve también los vínculos de evidencia en el extremo `delivery`:

| Regla | Cuándo | Error |
|---|---|---|
| Una sola entrega | Alta de una `delivery` con `social_contribution` que ya tiene una (la guarda toma el candado del aporte; el único del modelo es la última barrera) | `409 DELIVERY_EXISTS` sobre `social_contribution` |
| Entrega congelada | Cambio de la entrega, o alta o baja de un vínculo `evidence_delivery`, con el aporte fuera de `procured` | `409 DELIVERY_FROZEN` sobre `social_contribution` |
| Comprobante congelado | `receipt`: alta, cambio o baja con el aporte en `delivered` o después (`closed`, `rejected`, `cancelled`); un cambio de aporte se juzga contra los dos; decisión del usuario del 2026-10-11 | `409 DELIVERY_FROZEN` sobre `social_contribution` |
| Sin monto entregado | `delivered_amount` informado en una entrega de aporte | `400 FIELD_NOT_ALLOWED` sobre `delivered_amount` |

`sgspe.commitment` no depende de `sgspe.investment`: el `PATCH` es genérico sobre la entrega y su regla de compromisos
(`DELIVERY_FROZEN` sobre `commitment`) solo mira las entregas con `commitment`.

### 3.6 Programas: WF-G y sus hijos

**Objetos.** Las fichas de `database-model.md` §15 sin campos nuevos. `program_measurement` es `appendOnly`; una
medición se corrige con otra de otro periodo o, si es el mismo, no se corrige (único `program_indicator`,
`period_start_date`): consecuencia aceptada de RQ-AUD-003.

**WF-G.** Como lo diseña `workflows.md`. `InvestmentWriteGuard`:

| Regla | Transiciones | Error |
|---|---|---|
| G1 · Doble llave | `approve` | `403 ACTION_REQUIRED` sobre `record` sin `APPROVE` sobre `social_program` |
| G2 · Segregación | `approve` | `403 SEGREGATION_OF_DUTIES` sobre `record` si quien aprueba registró el programa |
| G3 · Sinergia | `approve`, `plan_direct` | Con `partner_organization` informado y `policy_synergy_requires_agreement` activa: `agreement` informado, en `active`, con `validity_status` distinto de VENCIDO y con un `agreement_party` cuya `organization` es el socio; si no, `409 SYNERGY_AGREEMENT_REQUIRED` sobre `agreement` |
| G4 · Sin aprobación interna | `plan_direct` | `409 INTERNAL_APPROVAL_REQUIRED` sobre `project` con `policy_internal_approval` activa |

**Otras reglas de los hijos.**

| Regla | Cuándo | Error |
|---|---|---|
| Mismo programa | `beneficiary_group.social_initiative` de otro programa | `409 PROGRAM_MISMATCH` sobre `social_initiative` |
| Periodo | `program_measurement.period_end_date` anterior a `period_start_date`; `end_date` anterior a `start_date` en programa e iniciativa | `400 OUT_OF_RANGE` sobre la fecha final |
| Año | `budget_year` fuera de 2000 a 2100 | `400 OUT_OF_RANGE` |
| Conteos | `target_count` o `reached_count` negativos o no enteros | `400 OUT_OF_RANGE` |

**Totales.** `ProgramTotalsListener` (alta, cambio o baja de `program_budget`) escribe `total_budget` = suma de
`allocated_amount` de los presupuestos en la moneda del programa, con la marca y solo si cambia; los de otra moneda no
se suman y S43 los muestra aparte. `ProgramTotalsRecalculator` hace lo mismo en JOB-02. El ejecutado del programa no se
deriva (decisión 4).

### 3.7 Solicitudes aceptadas como aporte

- **`POST /api/sgspe/stakeholder-requests/{id}/accept`** con `creates = social_contribution`: valida `attributes` contra
  `social_contribution` (sin `project`), exige UPDATE sobre `stakeholder_request`, CREATE sobre `social_contribution` y
  el rol de `accept`; en la transacción escribe la respuesta, crea el aporte en `requested` con el proyecto de la
  solicitud, guarda `created_contribution` y aplica `accept`. Responde `201 { request, contribution }` con `Location`
  del aporte. `creates` distinto de `commitment` y `social_contribution` sigue siendo `400 INVALID_TARGET`. Los ítems se
  cargan después en S44, todavía en `requested`. El aporte creado lo «registró» quien aceptó (P5).
- **`RequestClosureListener`** también en `close` de `social_contribution`: abre
  `ConfidentialRelease.within(stakeholder_request, created_contribution, <aporte>)` y aplica `close_accepted` a la
  solicitud en `accepted` con ese `created_contribution`, con el motivo «RN-GRV-001: aporte <código> cerrado» y el
  origen `listener:request-closure`. Un tenant sin `stakeholder_request` no busca.
- **WF-E3.** `accept` y `close_accepted` suman `SOCIAL_INVESTMENT_MANAGER` en
  `model/workflows/stakeholder_request.json`.
- **`close_unfulfilled`.** `AttentionWriteGuard` lo admite con el compromiso en `cancelled` o con el aporte en
  `rejected` o `cancelled`; si no, `409 STATE_MISMATCH` sobre el campo creado (`created_commitment` o
  `created_contribution`).

### 3.8 Avisos

| Aviso | Disparador | Destinatarios | Clave | Se resuelve |
|---|---|---|---|---|
| `approval_pending` de programa | `submit` de WF-G | Roles de `approve` de WF-G | `approval_pending:social_program:<id>` | Al salir de `pending_approval` |
| `approval_pending` de aporte | Alta de `social_contribution` (por la API genérica o por la aceptación) | Roles de `approve` de WF-P y, con `policy_internal_approval` en falso, también los de `approve_direct` | `approval_pending:social_contribution:<id>` | Al salir de `requested` |

El texto lleva el código y el nombre (programa) o el código y el tipo (aporte); nunca propósito, condiciones ni
receptor. El aviso del aporte llega al alta, antes de los ítems: quien aprueba ve el total al abrirlo y la aprobación
sin ítems responde `409 ITEMS_REQUIRED` (consecuencia aceptada, §8). F5 no agrega avisos de plazo: los hitos de programa
no escalan.

### 3.9 Indicadores y tablero `investment`

`InvestmentKpis` (`sgspe.dashboard`) con el marco de F3 (`Kpis`, `Indicators`, `Dashboards`, `DashboardData`,
`DashboardFilters`) y `ProjectReach` para los proyectos por defecto. Lee como quien llama: hereda el alcance. Filtros:
`project`, `explorationCampaign` (el filtro común de campaña, `api.md` §4.11), `program`, `year`, `periodStart`,
`periodEnd` y `locality`; `program` y `year` solo los acepta el tablero `investment` y los KPI-INV, y un filtro
desconocido es `422`. La cifra titular y la forma de cada desglose las fija el plan 5.2b («Interfaces con el portal»).

| KPI | Respuesta | Notas |
|---|---|---|
| KPI-INV-01 | Por campaña: tope, moneda, comprometido (con `CampaignCommitted`), ejecutado (`executed_social_amount`) y `otherCurrencyCount` | Lo comprometido y lo ejecutado son cifras distintas (ASM-063) |
| KPI-INV-02 | Por programa, año y moneda: asignado y ejecutado registrado | Sin conversión de monedas |
| KPI-INV-03 | Por proyecto y moneda: ejecutado por fuente (compromisos, aportes en `delivered` o `closed`, presupuestos de programas) frente a presupuesto y topes, con el periodo de `dashboards.md` §4.7 (el aporte cuenta si su entrega cae en el periodo) | Otras monedas como advertencia |
| KPI-INV-04 | Tres bloques sin sumar: por programa (meta y alcanzados de sus grupos), aportes ejecutados (`beneficiaries_count`) y compromisos cumplidos (`beneficiaries_count`) | Una persona puede estar en varias fuentes |

`dashboards.md` §3.2 suma el conjunto «comprometido» de `social_contribution` (`approved`, `procured`, `delivered`,
`closed`). El tablero es interno y no aplica celdas pequeñas. Prueba: `InvestmentDashboardApiTest`.

### 3.10 Documentos emitidos

Ningún tipo de documento en F5. AUT-04 y el tipo «Acta de recepción» quedan nombrados en `workflows.md` como «F5, tras
UPS-23», como AUT-03: un documento de `delivery` congelaría `recipients` y `conformity_by`, restringidos (ASM-090).
Mientras tanto el acta se imprime fuera de SGSPE, se firma en la entrega y se sube como evidencia ACTA DE RECEPCION
vinculada a la entrega, con clasificación DATOS PERSONALES por defecto si lleva nombres o firmas.

## 4. Cambios en el modelo y en los permisos (plan 5.1)

### 4.1 `model.json`, workflows y automatizaciones

- Los diez objetos de §15 con los cambios de §3.3; ENUM nuevo `item_kind`; `contribution_topic`; único de
  `delivery.social_contribution`; `stakeholder_request.created_contribution`; `task.social_program`; las M2M de
  evidencias y `project_priority_topic`.
- `model/workflows/social_program.json` (WF-G) y `social_contribution.json` (WF-P); `stakeholder_request.json` con
  `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted`; `task.json` con `SOCIAL_INVESTMENT_MANAGER` en las
  transiciones de WF-N que ya tienen los demás roles operativos.
- AUT-04 no entra en `model/automations/`; `test_model.py` la trata como «F5, tras UPS-23».
- `test_model.py` suma: `item_kind` con sus cuatro valores; `contribution_item.item_kind` obligatorio; el único de
  `delivery`; WF-G y WF-P frente a `workflows.md`.

### 4.2 `roles.json` y `security.md` §7.7

`SOCIAL_INVESTMENT_MANAGER` recibe sus permisos: la columna SIM de `security.md` §7.2 a §7.8 (lectura de F2 a F4,
`stakeholder_request` RU, `delivery` CRU, `evidence` con `EDIT_ANY` y `DOWNLOAD_RESTRICTED`, `task` CRU) y la de §7.7,
con estas correcciones de §7.7 (elección 15):

| Objeto | SMG | CRO | SIM | Por qué |
|---|---|---|---|---|
| `contribution_item` | R → CRUD | R → CRUD | CRUD | Quien registra el aporte carga sus ítems antes de aprobar (decisión 1); fuera de `requested` los congela la guarda |
| `receipt` | R | R → CRU | CRU | `COMMUNITY_RELATIONS_OFFICER` registra la entrega y puede cargar el comprobante con ella |
| Hijos de `social_program` | R | R | CRUD (CRU en presupuestos, CR en mediciones) | Un solo dueño de los datos del programa; SMG conduce el ciclo con RU sobre `social_program` |

`PROJECT_ADMIN` y `PROJECT_MANAGER` reciben `u` sobre `topic` si no lo tienen, para vincular los temas priorizados.
`test_apply_roles.py` comprueba que quien crea `social_contribution` crea `contribution_item`, que `APPROVE_OVER_CAP`
sobre `social_contribution` solo lo tiene `SOCIAL_MANAGER` y que todo rol de `accept` de WF-E3 con CREATE sobre
`social_contribution` tiene UPDATE sobre `stakeholder_request`.

## 5. Pruebas

| Clase | Casos propios |
|---|---|
| `SocialContributionWorkflowApiTest` | `approve` por `APPROVER` y por `SOCIAL_MANAGER`; sin ítems (P9) → `409`; sin `contribution_type` → `400`; ítem nuevo o cambiado después de aprobar → `409 ITEMS_FROZEN`; cambiar la moneda o la campaña después de aprobar → `409 CONTRIBUTION_FROZEN`; con un tope que bloquea (P1): `approve` por `APPROVER` → `409`, por `SOCIAL_MANAGER` → permitido, `approve_direct` → `409`; el tope cuenta un compromiso comprometido y otro aporte aprobado de la misma campaña; MANO DE OBRA, DINERO en DONACION y tema no priorizado: sin justificación → `409` con el código de cada uno, con justificación → permitido con advertencias; aporte con sinergia sin acuerdo, con acuerdo fuera de `active` o VENCIDO (P6) → `409 SYNERGY_AGREEMENT_REQUIRED` también con justificación, y con acuerdo vigente → permitido; aprobar por quien registró (P5) → `403`; `approve_direct` con la aprobación interna activa (P7) → `409`; capturar la entrega fuera de `procured` → `409`; una segunda entrega → `409 DELIVERY_EXISTS`; `delivered_amount` informado → `400`; registrar la entrega no cambia el estado; `register_delivery` sin conformidad, sin acta, sin foto o sin comprobante (P8) → `409`; la entrega y sus vínculos de evidencia no cambian desde `delivered`; `close` cierra la solicitud de origen |
| `SocialProgramWorkflowApiTest` | `approve` sin acuerdo de sinergia con socio (política activa) → `409`; con acuerdo vencido o sin el socio como parte → `409`; por quien registró → `403`; `plan_direct` con la política de aprobación interna activa → `409`; `suspend` y los `cancel_*` sin motivo → `400`; `total_budget` solo suma la moneda del programa |
| `StakeholderRequestWorkflowApiTest` (casos de F5) | Aceptar como aporte por `SOCIAL_INVESTMENT_MANAGER` crea el aporte en `requested` con `created_contribution`; por `COMMITMENT_MANAGER` → `403`; `created_contribution` por la API genérica → `403 COMMAND_ONLY`; cerrar el aporte cierra la solicitud en la misma transacción, también confidencial; `close_unfulfilled` con el aporte rechazado → permitido, con el aporte aprobado → `409` |
| `InvestmentDashboardApiTest` | KPI-INV-01 a 04 con montos en dos monedas; un proyecto ajeno da conjuntos vacíos |
| `ProjectReadScopeApiTest` | Los casos de §3.2 |
| Unitarias | `ContributionGuidelinesTest`, `SupportCapTest` con aportes, `CampaignCommittedTest`, `ProjectReadConditionTest`, `ProjectResolverTest`, `AmountFieldsGuardTest` |
| Matrices | `PermissionMatrixApiTest` con los diez objetos y las columnas «otro proyecto» (lectura `404`, alta `400`) y «no asignado»; `WorkflowMatrixApiTest` con WF-G y WF-P desde `model/workflows/*.json` |

Base: `F5ApiTest` (sobre `F4ApiTest`) con aportes con ítems, campaña con tope, acuerdo vigente, entrega con sus tres
evidencias y programas con presupuesto, listos para las guardas.

## 6. Cambios en los documentos (plan 5.0)

**IDs nuevos en `docs/requirements-analysis.md`** (siguientes libres: ASM-110, GAP-033 y UPS-29; §20.1 pasa de ASM 109 a
119, de GAP 32 a 33 y de 527 a 538 en total). Ninguna brecha upstream nueva.

| ID | Contenido |
|---|---|
| ASM-110 | Monto del aporte (decisión 1): ítems solo en `requested`; `total_amount` de los ítems es lo aprobado; ítems y términos aprobados congelados después; «comprometido» = `approved`, `procured`, `delivered`, `closed`; el tope suma compromisos y aportes comprometidos con el mismo candado |
| ASM-111 | Una sola entrega por aporte (decisión 2): captura en `procured`, congelada desde `delivered`, sin `delivered_amount`; lo que se entrega en partes son aportes distintos |
| ASM-112 | Lineamientos por tipo de ítem y tema (decisión 3): `item_kind`, `topic`, temas priorizados del proyecto con sus descendientes; las tres políticas (mano de obra, donaciones no monetarias y tema priorizado) como desviaciones que se justifican con `guideline_deviation_justification`; la sinergia del aporte queda fuera, como bloqueo propio (P6: acuerdo en `active` y no VENCIDO); `contribution_type` obligatorio al aprobar |
| ASM-113 | Ejecución de programas registrada a mano por año y moneda (decisión 4, refuerza ASM-063); `total_budget` solo en la moneda del programa; sin vínculo aporte → programa |
| ASM-114 | Alcance por proyecto de hijos de hijos (a lo sumo dos saltos) y de hijos con varios padres (disyunción, nunca «sin padre, todos»); amplía ASM-106 |
| ASM-115 | Sinergia del programa: con socio y política activa, acuerdo en `active`, no VENCIDO y con el socio como parte, al aprobar o planificar |
| ASM-116 | «Acta de recepción» y AUT-04 esperan UPS-23; el acta firmada es una evidencia ACTA DE RECEPCION; `receipt` opcional y P8 exige la evidencia COMPROBANTE (amplía ASM-090) |
| ASM-117 | Aceptar una solicitud como aporte exige CREATE sobre `social_contribution`; `created_contribution` es campo de comando; el aporte cierra la solicitud |
| ASM-118 | Montos y conteos de F5 bajo ASM-092 (elección 11) |
| ASM-119 | La autoridad para superar el tope difiere: `PROJECT_MANAGER` en compromisos, `SOCIAL_MANAGER` en aportes (GAP-006 sigue abierta) |
| GAP-033 | ¿Qué caseríos no tienen beneficio laboral (RN-INV-005)? Valor por defecto: no se evalúa; la prioridad se mide solo por tema |

**Huecos y contradicciones que cierra.**

- **C1 · Tope sin monto evaluable.** WF-P exigía los ítems en `register_procurement`, después de aprobar, así que P1
  comparaba un `total_amount` vacío. `workflows.md` WF-P mueve la exigencia a P9 en la aprobación y suma los ítems
  congelados; `domain-model.md` §7.2 y §8.2 y `database-model.md` §15 (`contribution_item`) lo dicen.
- **C2 · `program_measurement` es nieto.** `ArchitectureTest` exigía que el padre de cada hijo fuera una raíz.
  `security.md` §9.7, `domain-model.md` §4.7 y la especificación 4.4 §3.1 suman las cadenas (§3.2).
- **C3 · `delivery` con dos padres.** La lectura «sin compromiso, del tenant» abriría las entregas de aportes.
  `security.md` §9.7 y `domain-model.md` §4.7 pasan `delivery` a hijo con dos padres; los conteos pasan a 33 raíces y 27
  hijos; «hijos con padre opcional» queda solo para `locality_profile`.
- **C4 · AUT-04 frente a UPS-23.** `roadmap.md` F5 («Workflows», «Entregables»), S44 («AUT-04 emite el acta»), el
  párrafo final de WF-P y `domain-model.md` §6.12 la daban por hecha: pasan a «tras UPS-23» con la evidencia ACTA DE
  RECEPCION.
- **C5 · Permisos que no cerraban.** S44 decía que SMG y CRO registran el aporte, pero solo leían ítems y comprobantes;
  S43 decía que SMG «actualiza» sin decir qué; `COMMITMENT_MANAGER` tiene `accept` pero no puede crear el aporte.
  `security.md` §7.7, S43, S44 y S57 se corrigen con §4.2 y §2.2.
- **C6 · Autoridad del tope distinta.** `security.md` §7.5 y §7.7 y `workflows.md` D7 y P1 la nombran por separado;
  ASM-119 la registra como aceptada.
- **C7 · `InvestmentWriteGuard` a medias.** `domain-model.md` §8.2 solo le daba la sinergia del programa y la entrega;
  la tabla de invariantes de §7.2 ya pedía P6 en el aporte. Se escriben sus reglas completas (§3.3 a §3.6), con P6 como
  bloqueo propio del aporte, fuera de los lineamientos que se justifican (elección 9).
- **C8 · P2, P3 y P5 sin datos.** No había tipo de ítem, tema del aporte ni temas priorizados, y «caseríos sin beneficio
  laboral» no tiene campo. `database-model.md` suma `item_kind`, `contribution_topic` y `project_priority_topic`; P5 usa
  `created_by` como D4; GAP-033 registra lo que falta.
- **C9 · Montos sin validación.** ASM-092 y `api.md` §3.3 suman los montos de F5 (elección 11); el comentario de
  `AmountFieldsGuard` («los agrega su fase») se cumple.
- **C10 · `COMMAND_ONLY` sin `created_contribution`.** `api.md` §3.3 (fila `403`) lo suma.
- **C11 · WF-E3 sin SIM.** `workflows.md` WF-E3 quita «desde F5» y `stakeholder_request.json` suma el rol.
- **C12 · `RequestClosureListener` solo mira compromisos.** El código y `domain-model.md` §7.5 suman `close` de WF-P.
- **C13 · Escritores de `ContributionTotalsListener`.** `security.md` §8.1 nombraba solo a SIM; suma SMG y CRO, que
  cargan ítems, y el disparo en la aprobación.
- **C14 · Aviso de aprobación al alta.** `ApprovalNoticeListener` solo escucha transiciones; `workflows.md` («Avisos»)
  ya pedía el aviso al alta del aporte. `domain-model.md` §7.5 y `security.md` §8.1 suman WF-G y el modo «al crear».
- **C15 · Conjunto «comprometido» de aportes.** `dashboards.md` §3.2 lo suma; KPI-INV-01 suma lo comprometido.

**Además.**

- `api.md` §4.6: el contrato de la aceptación como aporte (§3.7) y del endpoint de entrega de aportes (§3.5, con sus
  errores); §3.3: `ITEMS_REQUIRED`, `ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`, `DELIVERY_EXISTS`, `FIELD_NOT_ALLOWED`,
  `PROGRAM_MISMATCH`, `PAID_LABOR`, `MONETARY_DONATION`, `PRIORITY_NOT_MET`, `PRIORITIES_NOT_CONFIGURED` y
  `SYNERGY_AGREEMENT_REQUIRED`, y `PROJECT_MISMATCH` en las relaciones de BC-10.
- `workflows.md`: WF-P (P1 a P9 como §3.3, orden y candados), WF-G (G1 a G4), «Pruebas» con la tabla de §5,
  «Destinatarios por rol» con los dos avisos.
- `database-model.md`: §15 con los cambios de §3.3; `delivery` (único, regla de congelamiento de aportes, dos padres);
  `project` con `project_priority_topic`; el catálogo de ENUM con `item_kind`.
- `requirements-analysis.md` §10: «Tipo de ítem del aporte» (BIEN · SERVICIO · DINERO · MANO DE OBRA); RF-INV-002 nombra
  los temas priorizados.
- `dashboards.md` §4.7 y §5.10: KPI-INV-01 con lo comprometido, KPI-INV-04 en tres bloques.
- `ux/screens.md`: S42 a S44 y S57 con §3 y §4.2; S11 con los temas priorizados; S48 con las aprobaciones de programas y
  aportes.
- `roadmap.md` F5: «Definición de terminado» con la de §7; «Estado» al cerrar; `CLAUDE.md` con `sgspe.investment`
  completo en «Dónde está cada cosa».
- `requirements-traceability.md`: RF-INV-001 a 004 y RN-INV-001 a 006 pasan a «Diseñado» con sus pruebas.
- `glossary.md`: «comprometido», «tema priorizado» y «desviación de lineamientos».

## 7. Reparto en planes

| Plan | Rama | Depende de | Tareas principales | Estimado |
|---|---|---|---|---|
| 5.0 · Documentos | `docs/fase-5-0-documentos` desde `main` | Esta especificación | Los cambios de §6 por documento; IDs ASM-110 a ASM-119 y GAP-033; `check_traceability.py` en verde; PR | ~10 tareas |
| 5.1 · Modelo y alcance | `feat/fase-5-1-modelo` desde `main` con 5.0 | 5.0 | `design_docs.py` lee lo nuevo; `model.json` (§4.1); WF-G, WF-P y los roles de WF-E3 y WF-N; `roles.json` (§4.2); `test_model.py` y `test_apply_roles.py`; `ProjectScope` con padres alternativos y cadenas, `ProjectReadCondition`, `ProjectReadScope`, `ProjectResolver`, `linkedProject` y sus unitarias; `ExactlyOneGuard` de `delivery` y de `task`; `AmountFieldsGuard` (listas de la elección 11: sus pruebas fallan en cuanto el modelo entra a `main`); `ArchitectureTest` (§3.2); base `F5ApiTest`; `WorkflowMatrixApiTest`; PR | ~14 tareas |
| 5.2a · Aportes | `feat/fase-5-2a-aportes` desde `main` con 5.1 | 5.1 | Comprobar `AmountFieldsGuard` y `ExactlyOneGuard` de `task` (los deja 5.1) y probar los montos por la API; `ProjectLinkGuard`; `CampaignCommitted` y `SupportPolicyGuard`; `ContributionCapRule`; `InvestmentWriteGuard` (ítems y términos congelados, P4, P5, P7, P9); `ContributionGuidelines` y temas priorizados; entrega de aportes y sus reglas; P8; `ContributionTotalsListener` y recalculadores (incluido el ejecutado de la campaña); aceptación como aporte, campo de comando y `close_unfulfilled`; `RequestClosureListener`; avisos de WF-P; `SocialContributionWorkflowApiTest` y el caso de `StakeholderRequestWorkflowApiTest`; PR | ~17 tareas |
| 5.2b · Programas y tablero | `feat/fase-5-2b-programas` desde `main` con 5.2a | 5.2a (guarda, avisos, `CampaignCommitted`) | WF-G (G1 a G4) y las reglas de los hijos; `ProgramTotalsListener` y su recalculador; avisos de WF-G; `InvestmentKpis` y tablero `investment`; `ProjectReadScopeApiTest` de §3.2; columnas «otro proyecto» y «no asignado» de las matrices; medición de las subconsultas; actualizar el tenant de desarrollo a F5 (coordinador, por el túnel); suite completa; PR | ~13 tareas |
| 5.3 · Portal | `feat/fase-5-3-portal` en `sgspe-ui` | 5.2a para aportes y la aceptación; 5.2b para programas y tablero | S42; S44 (ítems en `requested`, «Antes de aprobar» con `transition-checks`, justificación, captura de la entrega con sus tres evidencias, «Subir acta de recepción firmada»); S43 (presupuesto por año y moneda con el ejecutado, indicadores y mediciones, iniciativas, hitos, beneficiarios, convenio); S57 «Aceptar y crear el aporte» solo con CREATE sobre `social_contribution`; S11 temas priorizados; S48 aprobaciones de programas y aportes; tablero de inversión; navegación y permisos; pruebas con Vitest; PR | ~12 tareas |

**¿Se divide 5.2?** Sí: un solo 5.2 tendría unas 30 tareas. La frontera sigue la de F4: 5.2a trae lo que comparten los
dos (la guarda, el tope y los avisos) y todo lo de aportes; 5.2b parte de 5.2a integrada. No se paralelizan: comparten
`sgspe_sdd_test` y el coordinador corre las integraciones una tras otra. El portal puede escribirse en paralelo contra la
API documentada (sus pruebas simulan la API) o empezar tras 5.2b; su PR se integra cuando 5.2a y 5.2b están en `main`.

**Medición (5.2b), como el plan 4.4.** Con «Proyecto Ejemplo A» y «Proyecto Ejemplo B» y un volumen representativo en
`sgspe_test` (10 proyectos, 500 programas, 5 000 indicadores, 50 000 mediciones; 20 000 compromisos, 5 000 aportes y
25 000 entregas), `EXPLAIN ANALYZE` de la lista paginada y de su conteo de `program_measurement` y de `delivery` con y
sin alcance. Criterio: sin barrido secuencial de las tablas de los padres y menos de 20 % más de tiempo en la lista
paginada. Si la disyunción de `delivery` impide usar los índices, la condición se reescribe como
`"id" IN (SELECT … WHERE "commitment" IN (…) UNION ALL SELECT … WHERE "social_contribution" IN (…))`, con el mismo
resultado; el plan elige con la medición y anota las cifras en `roadmap.md` F5 «Estado».

**Definición de terminado.** La de `roadmap.md` F5: `SocialProgramWorkflowApiTest`, `SocialContributionWorkflowApiTest`
y el caso de `StakeholderRequestWorkflowApiTest` que cierra la solicitud al cerrar su aporte; los objetos nuevos
clasificados en `ProjectScope` (`ArchitectureTest`); `delivery` con dos padres leída por el padre que tenga, nunca «sin
padre, todos». Además: las columnas «otro proyecto» y «no asignado» de `PermissionMatrixApiTest` y
`WorkflowMatrixApiTest` en verde con los diez objetos y la entrega de un aporte; `InvestmentDashboardApiTest`; la
medición anotada; el tenant de desarrollo en F5 (`upgrade_tenants.py`).

**Orden de despliegue.** Modelo y roles antes que el backend (`apply.py` → `apply_roles.py` → `apply.py --workflows`):
un backend de F5 sobre un tenant de F4 sigue funcionando por `TenantModel`, pero el portal no muestra BC-10 hasta
aplicar el modelo.

## 8. Riesgos y consecuencias aceptadas

**Consecuencias aceptadas.**

1. **Un cambio en un aporte aprobado es otro aporte** (decisión 1): no se corrige un ítem mal cargado después de
   aprobar; se anula y se registra de nuevo.
2. **Lo comprometido frena aportes que quizá no se ejecuten:** un aporte aprobado y nunca anulado sigue contando para el
   tope hasta su anulación.
3. **Aviso de aprobación antes de los ítems** (§3.8).
4. **La justificación cubre todos los lineamientos:** una sola justificación escrita para el primero también deja pasar
   uno que aparezca después mientras el aporte siga en `requested`; la consulta en seco los lista todos antes de
   aprobar.
5. **Segregación y tope en un mismo rol:** un aporte que registró o aceptó un `SOCIAL_MANAGER` y supera un tope que
   bloquea solo lo aprueba otro `SOCIAL_MANAGER` (P5 y P1).
6. **Mediciones que no se corrigen** en el mismo periodo (`appendOnly`, §3.6).
7. **El comprobante estructurado no se compara** con lo aprobado (§2.2).

**Riesgos.**

1. **GAP-007 y GAP-032 siguen abiertas.** Si la empresa gestiona programas con otro ciclo u otros indicadores, WF-G y
   `program_indicator` cambian; se mitiga con el ciclo del prompt y sin campos de más.
2. **Montos en otra moneda que no se suman** (ASM-063): un tope en PEN con aportes en USD solo advierte; un tipo de
   cambio queda fuera.
3. **AUT-04 pospuesta** mientras UPS-23 siga abierta: el acta de recepción se imprime fuera de SGSPE.
4. **Costo de las subconsultas anidadas** y de la disyunción de `delivery`: lo decide la medición de §7, con la
   reescritura como respaldo.
5. **Único sobre una relación opcional:** si wasichai no lo admite en `delivery.social_contribution`, la única barrera
   es la regla con candado de §3.5; la primera tarea de 5.1 lo comprueba.
6. **Alcance en 5.1** (elección 3): cambia el reparto propuesto; si se prefiere el original, 5.1 no puede integrarse
   en `main` sin el alcance de cadenas y dos padres.
7. **Tiempo del túnel.** Diez objetos más alargan el aprovisionamiento y la matriz de permisos; conviene correr juntas
   las clases de cada tarea.
