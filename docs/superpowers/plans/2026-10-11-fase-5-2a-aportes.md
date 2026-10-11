# SGSPE · Fase 5, plan 5.2a (backend: aportes, tope, solicitudes y avisos): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Escribir el código propio de SGSPE para los aportes sociales de la Fase 5 sobre el modelo y el alcance del plan
5.1: `InvestmentWriteGuard` con las reglas P1 a P9 de WF-P en el orden de la especificación, ítems y términos aprobados
congelados, una sola entrega por aporte, `ContributionGuidelines`, `ContributionCapRule` con `SupportCap` y
`CampaignCommitted` (también en `SupportPolicyGuard`), los montos (con `AmountFieldsGuard`, que deja 5.1) y conteos de F5,
`ContributionTotalsListener` y los recalculadores de JOB-02, el endpoint de entrega de aportes, la aceptación de una
solicitud como aporte y su cierre, y el aviso de aprobación de WF-P al alta.

**Architecture:** `sgspe.investment` pasa de una regla de compromisos a un paquete: `Contributions` (nombres y estados de
WF-P, candado del aporte), `InvestmentWriteGuard` hecho de `InvestmentRule` ordenadas (como `CommitmentWriteGuard`), las
reglas del registro, de aprobación, de lineamientos, del tope y de la entrega, `CampaignCommitted` y `CampaignCap`
(compartidos con `SupportPolicyGuard`), `ContributionDeliveries` (endpoint) y `ContributionTotalsListener` con sus
recalculadores. Cambian `sgspe.common` (`ProjectParameters`), `sgspe.scope`
(`ProjectLinkGuard`), `sgspe.commitment` (`PATCH /api/sgspe/deliveries/{id}` con entregas de aportes),
`sgspe.evidence` (orden de candados), `sgspe.attention` (`Acceptances`, `AttentionRules`, `AttentionWriteGuard`,
`RequestClosureListener`) y `sgspe.notification` (`ApprovalNoticeListener`). Todo escribe por `sgspe.common.Records`
con la marca `SgspeWrite`; las reglas son `WriteRule` que `POST /api/sgspe/transition-checks` corre en seco.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 WebFlux con corrutinas, JDK 25, Gradle 9.8.0, `wasichai-bom` 1.0.0 (sin
starters nuevos), R2DBC, Jackson 3 (`tools.jackson`), JUnit 5 con `wasichai-test` (`WasichaiIntegrationTest`), Mockito.

**Spec:** `docs/superpowers/specs/2026-10-11-fase-5-design.md` (vinculante): §2.1 (decisiones 1 a 3), §2.2 (autoridad
del tope, comprobante, aceptación), §2.3 (elecciones 6 a 14), §3.1 (piezas comunes que toca este plan), §3.3 (WF-P, P1 a
P9, orden y candados, otras reglas), §3.4 (tope y lineamientos), §3.5 (entrega del aporte), §3.7 (solicitudes), §3.8
(aviso de WF-P), §5 (`SocialContributionWorkflowApiTest`, el caso de `StakeholderRequestWorkflowApiTest`, las unitarias)
y §7 (fila 5.2a). Lo que la especificación asigna a 5.1 (modelo, roles, alcance, `F5ApiTest`), a 5.2b (WF-G, programas,
`ProgramTotalsListener`, avisos de WF-G, tablero, matrices de permisos, medición) y a 5.3 (portal) queda fuera.

## Global Constraints

- **Repositorio y rama.** Todo el trabajo en `~/IdeaProjects/sgspe`, en la rama `feat/fase-5-2a-aportes` creada desde
  `main` con los planes 5.0 y 5.1 integrados; ramas de tarea `f52a/t<k>` desde ella, integradas con `--no-ff`. El paso 1
  de la Tarea 1 lo comprueba.
- **Versiones.** wasichai `1.0.0` exacto (desde wasichai/sgspe#30), Kotlin `2.4.20`, Spring Boot `4.1.1`, JDK `25`,
  Gradle `9.8.0`, ktlint `1.7.1`. Ningún starter nuevo.
- **Idioma.** Español (es-PE) en comentarios, mensajes y `detail`; nombres técnicos en inglés (ADR-0009); constantes de la
  API en inglés UPPER_SNAKE; propiedades del cuerpo en camelCase; atributos con su nombre del modelo en snake_case; valores
  ENUM en español MAYÚSCULAS sin tildes, exactamente como el modelo (`MANO DE OBRA`, `DINERO`, `DONACION`,
  `APOYO EN ESPECIE`, `ACTA DE RECEPCION`, `COMPROBANTE`, `VENCIDO`).
- **Arquitectura.** Ningún literal de rol en `src/main/kotlin` (los roles salen de `model/workflows/*.json` y de
  `model/roles.json` por `ModelCatalog`); ningún código bajo el paquete `wasichai`; nunca `RecordStore`,
  `PhysicalTableRecordStore`, `AuditService` ni `ObjectSchemaManager`; `SgspeWrite` y `RecordService` solo en
  `sgspe.common`; `scope` y `privacy` solo dependen de `common`; los tenants solo con `Organizations`; ningún bean de
  estereotipo con el nombre simple de otro (`ArchitectureTest`); `ConfidentialRelease` solo en `RequestClosureListener`;
  `sgspe.commitment` y `sgspe.evidence` no dependen de `sgspe.investment` (nombran el aporte con literales). Ningún import
  sin uso: ktlint de este repositorio no los quita.
- **Lecturas y escrituras.** Nunca `asPlatform` dentro de una petición: guardas, comandos y listeners leen y escriben como
  quien llama; solo JOB-02 corre como la plataforma, con su motivo (`DerivedFieldsJob.reason`). Una regla que no puede leer
  lo que necesita rechaza (`403 OBJECT_NOT_READABLE`), nunca deja pasar. Todo lo que lee objetos de BC-10 desde código de
  fases anteriores pregunta antes a `TenantModel`.
- **Candados.** El del aporte es `social_contribution:<id>` (`Contributions.lock`); el de la campaña,
  `exploration_campaign:<id>` (`CampaignCap.lock`). Orden: siempre aporte y después campaña; las entregas de varios padres,
  por la clave ascendente (`EvidenceRules.lockOrder`). La consulta en seco no toma candados.
- **Errores.** Todo 400, 403 o 409 de una guarda o de un comando lleva su código en `errors[0].message` y el campo en
  `errors[0].field` (`Finding.toCodedException`). Los códigos son los de la especificación y `api.md` §3.3 tras el plan
  5.0: `ITEMS_REQUIRED`, `ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`, `DELIVERY_EXISTS`, `DELIVERY_FROZEN`,
  `DELIVERY_REQUIRED`, `EVIDENCE_REQUIRED`, `FIELD_NOT_ALLOWED`, `FIELD_NOT_READABLE`, `FIELD_REQUIRED`, `PAID_LABOR`,
  `MONETARY_DONATION`, `PRIORITY_NOT_MET`, `PRIORITIES_NOT_CONFIGURED`, `SYNERGY_AGREEMENT_REQUIRED`,
  `INTERNAL_APPROVAL_REQUIRED`, `ACTION_REQUIRED`, `SEGREGATION_OF_DUTIES`, `SUPPORT_CAP_EXCEEDED`, `CAP_DATA_INVALID`,
  `CAP_OTHER_CURRENCY`, `STATE_MISMATCH`, `COMMAND_ONLY`, `INVALID_TARGET`, `PROJECT_MISMATCH`, `OUT_OF_RANGE`,
  `INVALID_NUMBER`.
- **Lecturas acotadas.** `Records.all(max)` con `400 TOO_MANY_RECORDS`, nunca un recorte silencioso; un parámetro de
  consulta desconocido es `422` (`StrictQuery`); una propiedad o un atributo desconocido de un comando es `400`
  (`StrictBody`).
- **Datos.** Ficticios: correos `@sgspe.test`, «Proyecto Ejemplo A», «Asociación Ejemplo», «Persona Ejemplo»; ningún
  nombre real de persona en nada versionado.
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde
  `~/IdeaProjects/sgspe`; `./gradlew ktlintFormat` antes de `build`. El token nunca va a un archivo ni a la salida. Con
  poca memoria, `-Pkotlin.daemon.jvmargs=-Xmx3g`.
- **Integración.** Las clases `*ApiTest` necesitan PostgreSQL: **corren en la CI del PR** y, si se quiere antes, las corre
  el coordinador, nunca el implementador ni un subagente. El implementador compila (`build`, que compila también las de
  integración) y entrega la tarea marcada «integración pendiente» con las clases de su último paso. Con el túnel abierto:
  `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_sdd_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests '<clase>'`.
  La primera clase de una JVM aprovisiona el modelo de F5 (más de 8 minutos por el túnel); conviene correr juntas las
  clases de una tarea. `sgspe_sdd_test` no la usan dos suites a la vez.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres);
  al final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`. `git add` solo con rutas explícitas.
  `yarn format` antes de commitear yaml o json (este plan no toca ninguno). Push y PR en la Tarea 15.

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

1. **Un `total_amount` guardado que no coincide con los ítems.** Si el tope se juzgara con el derivado guardado (dañado,
   o escrito antes de un ítem), un aporte pasaría el tope con un monto que no es el que se aprueba. Esperado: P1 suma los
   `line_amount` leídos en la transacción. Prueba: `SocialContributionWorkflowApiTest` «el tope suma los items leidos en
   la transaccion, no el total guardado» (Tarea 7).
2. **Un reintento del portal tras perder la red al capturar la entrega.** Sin la clave, el segundo envío respondería
   `409 DELIVERY_EXISTS` y el portal mostraría un error sobre algo que sí se guardó. Esperado: con la misma
   `Idempotency-Key`, la misma respuesta y una sola entrega. Prueba: `SocialContributionWorkflowApiTest` «un reenvio de
   la captura con la misma clave devuelve la misma respuesta y deja una sola entrega» (Tarea 9).
3. **El `PUT` del portal sobre un aporte aprobado.** El portal reenvía el registro entero; si eso contara como cambio de
   los términos congelados, ningún aporte aprobado se podría editar (ni su ocasión ni sus beneficiarios). Esperado: reenviar
   lo leído pasa, cambiar un campo no juzgado pasa, cambiar uno juzgado es `409 CONTRIBUTION_FROZEN`. Prueba:
   `SocialContributionWorkflowApiTest` «cambiar la moneda o la campana despues de aprobar es CONTRIBUTION_FROZEN y
   reenviar lo leido pasa» (Tarea 1).
4. **Una justificación escrita para un lineamiento y una política apagada.** Quien justifica la mano de obra también deja
   pasar el tema fuera de prioridades (consecuencia aceptada 4); y un proyecto con `policy_no_paid_labor` en falso no debe
   pedir justificación por MANO DE OBRA. Esperado: con justificación, todos los incumplidos como advertencias; con la
   política apagada, ningún hallazgo. Prueba: `ContributionGuidelinesTest` «una sola justificacion cubre todos los
   lineamientos incumplidos» y «una politica apagada no se evalua» (Tarea 5).
5. **Un tenant que todavía no tiene el modelo de F5.** Un backend de F5 sobre un tenant de F4 sigue aprobando
   compromisos: si `SupportPolicyGuard` leyera `social_contribution` sin preguntar, cada aprobación con campaña
   respondería un error. Esperado: sin el objeto, `CampaignCommitted` solo lee compromisos. Prueba:
   `CampaignCommittedTest` «sin el modelo de F5 solo lee compromisos» (Tarea 6).

---

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | §3.3 pide que la escritura de un ítem tome el candado del aporte «así que un ítem no se cuela entre la suma y la transición», pero la API genérica de wasichai no abre transacción (ADR-038 de wasichai: `RecordService` se une a la de quien llama, y `RecordController` no abre ninguna): el candado que toma la guarda de un ítem se suelta antes de su `INSERT` | La guarda toma el candado igual (serializa los comandos de sgspe y acota la ventana a microsegundos) y lo aprobado no se mueve: `approve` y `approve_direct` escriben `total_amount` en su transacción y JOB-02 solo recalcula los aportes en `requested`. Un ítem colado queda visible en un aporte aprobado sin cambiar el monto aprobado (Tareas 1 y 8). Riesgo residual anotado en el PR |
| 2 | §2.3 elección 12: `ContributionTotalsListener` mantiene el total «con cada ítem y en la aprobación», sin decir qué pasa si falla | En la aprobación, si no puede escribir, lanza y la transición se revierte (lo aprobado queda escrito o no hay aprobación). Con un ítem, lo registra y la escritura sigue: el total solo informa en `requested` y JOB-02 lo corrige (como `requires_senior_approval` en F3) (Tarea 8) |
| 3 | P9 lista `ITEMS_REQUIRED` y `FIELD_REQUIRED` sin orden entre ellos | En el orden de la tabla: primero los ítems, después `contribution_type`; la consulta en seco da los dos (Tarea 4) |
| 4 | P8 dice `403 FIELD_NOT_READABLE` «sobre `conformity_by`» | El campo del hallazgo es `delivery.conformity_by`, como D9 de WF-D (`FulfillmentEvidenceRule`): el portal ya lo interpreta así (Tarea 10) |
| 5 | §3.3 dice que la aprobación toma el candado del aporte; §3.5 congela la entrega en `cancelled` | `ContributionLockRule` toma el candado en `approve`, `approve_direct`, `register_delivery`, `cancel_approved` y `cancel_procured`, como `FulfillmentLockRule` en WF-D: una corrección de la entrega o un vínculo de evidencia no se cruza con la anulación ni con P8 (Tarea 4) |
| 6 | Vincular una evidencia a la entrega de un aporte toma `social_contribution:<id>`; `Evidences.create` ordena solo por el compromiso de cada entrega | `EvidenceRules.lockOrder` ordena por la clave del candado del padre de cada entrega (`commitment:<id>` o `social_contribution:<id>`), así dos altas con entregas de compromisos y de aportes en orden inverso no se interbloquean (Tarea 9) |
| 7 | §3.5 deja `PATCH /api/sgspe/deliveries/{id}` en `sgspe.commitment` para las entregas de aportes, pero su respuesta es `{ delivery, commitment }` | La corrección de la entrega de un aporte responde `{ delivery, contribution }` (`ContributionDeliveryResult`, en `sgspe.commitment`), exige UPDATE sobre `delivery` y `social_contribution`, toma `social_contribution:<id>` y no recalcula ejecutado; la de un compromiso no cambia (Tarea 9) |
| 8 | §3.5: la captura fuera de `procured` es `409 STATE_MISMATCH` «sobre `record`»; la de compromisos lo da sobre `state` | Sobre `record`, como dice la especificación; la de compromisos no cambia (Tarea 9) |
| 9 | §3.5 pone `FIELD_NOT_ALLOWED` sobre `delivered_amount` en el endpoint y en la guarda | El endpoint lo rechaza antes de escribir sobre `attributes.delivered_amount` (como los demás atributos de un comando, `StrictBody`); la guarda, por cualquier otra puerta, sobre `delivered_amount` (Tarea 9) |
| 10 | La especificación daba `AmountFieldsGuard` y el grupo de `task` de `ExactlyOneGuard` a 5.2a (ya corregida: §7 los pone en 5.1), porque `AmountFieldsGuardTest` y `GuardTablesCatalogTest` fallan en cuanto el modelo de F5 entra a `main`: el plan 5.1 los deja en su estado final («Lo que heredan los planes 5.2a, 5.2b y 5.3») | Las Tareas 2 y 3 solo los comprueban (la especificación §7 ya los pone en 5.1) y suman lo que 5.1 no trae: las pruebas de los montos de F5 por la API, las políticas de aportes en `ProjectParameters` y `ProjectLinkGuard`. Si 5.1 no los dejó, se detiene y se avisa al coordinador |
| 11 | El aviso de WF-P suma los roles de `approve_direct` «con `policy_internal_approval` en falso», y el listener corre como quien crea | Lee el proyecto como quien crea; si no puede (NotReadable), el aviso va a la unión de los roles de `approve` y `approve_direct`, como la regla de nivel superior de los compromisos: un aviso de más, nunca un aprobador sin aviso (Tarea 13) |
| 12 | `close_unfulfilled` admite el compromiso cancelado o el aporte rechazado o anulado | Si la solicitud tiene `created_contribution`, se juzga el aporte; si no, el compromiso, como hoy (un tenant de F4 no tiene el campo) (Tarea 11) |
| 13 | P4 (`ACTION_REQUIRED`) no se alcanza por la API con los roles de §7.7: los dos roles de `approve` tienen `APPROVE` | La prueba de integración lo comprueba con la consulta en seco, que corre las guardas sin mirar los roles (como la decisión 12 de 4.2b) (Tarea 4) |
| 14 | §3.4: «la lógica común (lectura del tope, hallazgos, error de datos) sale a una pieza compartida» | `CampaignCap` (lectura de la campaña y del tope, `CampaignCommitted`, hallazgos, política del proyecto y `APPROVE_OVER_CAP` sobre el objeto que se aprueba); `SupportPolicyGuard` y `ContributionCapRule` solo eligen candidato, objeto y candado (Tareas 6 y 7) |
| 15 | `F5ApiTest` (plan 5.1) trae `socialContribution`, `contributionItem`, `approvedContribution`, `contributionDelivery`, `campaign` y `activeAgreement`, pero sin elegir el monto ni el tipo de cada ítem, que P1, P2 y P9 necesitan, y sin la respuesta de un alta que falla | `ContributionApiTest` (sobre `F5ApiTest`) trae ayudantes con nombres que no chocan con los de `F5ApiTest` (`requestedContribution`, `itemFor`, `approvedWithItems`, `procuredWithItems`, `cappedCampaign`, `liveAgreement`, `deliveryEvidence`, `captureDelivery`, `dryRun`): una sobrecarga con el mismo nombre y valores por defecto haría ambigua la llamada (Tarea 1). La matriz (Tarea 14) sí usa `contributionDelivery` de `F5ApiTest` |
| 16 | «Conteos» (`beneficiaries_count`) es de §3.3 y los de programas de §3.6 (5.2b) | `InvestmentCounts.nonNegativeInteger` es la pieza pura que usan los dos planes (Tarea 1) |

## Lo que este plan toma de los planes 5.0 y 5.1

- **Documentos (5.0).** Los códigos de `api.md` §3.3; el contrato de la aceptación como aporte y del endpoint de entrega
  (§4.6); WF-P con P1 a P9, orden y candados en `workflows.md`; ASM-110 a ASM-119 y GAP-033.
- **Modelo y alcance (5.1).** Los objetos de BC-10 con `contribution_item.item_kind` (ENUM `item_kind`),
  `social_contribution.topic` (`contribution_topic`), el único de `delivery.social_contribution`,
  `stakeholder_request.created_contribution`, `task.social_program`, las M2M `evidence_social_contribution`,
  `evidence_social_program` y `project_priority_topic`; WF-P en `model/workflows/social_contribution.json` con
  `requested` inicial; `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted` de WF-E3; `roles.json` con §4.2;
  `ExactlyOneGuard.GROUPS["delivery"] = [[commitment, social_contribution]]`; `ProjectScope` con padres alternativos y
  cadenas, `ProjectResolver` y `Records.linkedProject` (la evidencia de una entrega de aporte es del proyecto del aporte);
  `ExactlyOneGuard.GROUPS["task"]` con `social_program`; `AmountFieldsGuard.FIELDS` y `NOT_MONEY` con los montos de F5;
  `GuardTablesCatalogTest.notYetModeled == setOf("ai_suggestion")`; `F5ApiTest` (`socialContribution`, `contributionItem`,
  `approvedContribution`, `contributionDelivery`, `campaign`, `activeAgreement`, `priorityTopic`…);
  `WorkflowMatrixApiTest` sobre `F5ApiTest` con WF-P y `ROLE_DEFERRED` («`SOCIAL_INVESTMENT_MANAGER` aplica `accept`»).

## Interfaces con el portal (plan 5.3)

Lo que el portal consume de este plan (contrato completo en `api.md` tras el plan 5.0):

- Transiciones de WF-P por la API genérica: `approve` y `approve_direct` → `409 INTERNAL_APPROVAL_REQUIRED` (`project`,
  solo `approve_direct`), `409 ITEMS_REQUIRED` (`contribution_item`), `400 FIELD_REQUIRED` (`contribution_type`), `403
  ACTION_REQUIRED` y `403 SEGREGATION_OF_DUTIES` (`record`, solo `approve`), `409 SYNERGY_AGREEMENT_REQUIRED`
  (`agreement`), `409 PAID_LABOR` y `MONETARY_DONATION` (`contribution_item`) y `409 PRIORITY_NOT_MET` (`topic`) sin
  justificación (advertencias con ella), `PRIORITIES_NOT_CONFIGURED` (`topic`, siempre advertencia), `409
  SUPPORT_CAP_EXCEEDED` y `CAP_DATA_INVALID` (`exploration_campaign`) o advertencias, `CAP_OTHER_CURRENCY` (`currency`,
  advertencia). `register_delivery` → `409 DELIVERY_REQUIRED` (`delivery`), `403 FIELD_NOT_READABLE`
  (`delivery.conformity_by`), `409 EVIDENCE_REQUIRED` (`evidence_delivery`). Todas en `POST /api/sgspe/transition-checks`,
  que junta bloqueos y advertencias («Antes de aprobar» de S44).
- Ítems (`contribution_item`) por la API genérica: alta, cambio o baja con el aporte fuera de `requested`, o cambiar su
  aporte → `409 ITEMS_FROZEN` (`social_contribution`); `line_amount` y `unit_cost` → `400 INVALID_NUMBER` u `OUT_OF_RANGE`.
  `total_amount` del aporte lo mantiene SGSPE: tras escribir un ítem, el portal relee el aporte para mostrar el total.
- Aporte fuera de `requested`: cambiar `currency`, `contribution_type`, `topic`, `exploration_campaign`,
  `has_institutional_synergy`, `agreement`, `guideline_deviation_justification` o `project` → `409 CONTRIBUTION_FROZEN`
  sobre el campo; `beneficiaries_count` negativo o no entero → `400 OUT_OF_RANGE`.
- `POST /api/sgspe/social-contributions/{id}/deliveries` con `{ attributes, geometries, evidence[] }`, `If-Match` del
  aporte (obligatorio), `X-Change-Reason` (obligatorio) e `Idempotency-Key` (opcional) → `201 { delivery, contribution }`
  con `ETag` de la entrega y `Location` de la entrega. Errores: `400` sin código (propiedad o atributo desconocido,
  `commitment` o `social_contribution` en `attributes`, sin motivo), `400 FIELD_NOT_ALLOWED`
  (`attributes.delivered_amount`), `403` sin código (permisos), `409 STATE_MISMATCH` (`record`: el aporte no está en
  `procured`), `409 DELIVERY_EXISTS` (`social_contribution`), `412`/`428` (If-Match).
- `PATCH /api/sgspe/deliveries/{id}` de una entrega de aporte → `200 { delivery, contribution }`; con el aporte fuera de
  `procured` (y los vínculos de evidencia de esa entrega) → `409 DELIVERY_FROZEN` (`social_contribution`).
- `POST /api/sgspe/stakeholder-requests/{id}/accept` con `creates = social_contribution` → `201 { request, contribution }`
  con `Location` del aporte; sin CREATE sobre `social_contribution` → `403`; `creates` de otro objeto → `400
  INVALID_TARGET`. `created_contribution` por la API genérica → `403 COMMAND_ONLY`. `close_unfulfilled` con el aporte
  fuera de `rejected` o `cancelled` → `409 STATE_MISMATCH` (`created_contribution`).
- Aviso `approval_pending:social_contribution:<id>` (fuente `sgspe.approvals`, `ACTION`, enlace al aporte) al alta; se
  resuelve al salir de `requested`.

---

## Mapa de archivos

Todos bajo la raíz del repositorio. «Crear» es un archivo nuevo; «Modificar», uno existente.

| Archivo | Responsabilidad | Tarea |
|---|---|---|
| `src/main/kotlin/sgspe/investment/Contributions.kt` (crear) | `Contributions`, `Records.itemsOf`, `ContributionData`, `InvestmentCounts` | 1 |
| `src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt` (crear) | `InvestmentRule`, `InvestmentRuleOrder`, `InvestmentWriteGuard` | 1 |
| `src/main/kotlin/sgspe/investment/ContributionRecordRules.kt` (crear) | Ítems y términos congelados, conteos | 1 |
| `src/test/kotlin/sgspe/ContributionApiTest.kt` (crear) | Base de las pruebas de aportes | 1 |
| `src/main/kotlin/sgspe/common/ProjectParameters.kt` (`AmountFieldsGuard.kt` solo se comprueba: es del plan 5.1) | Políticas de aportes; montos de F5 probados | 2 |
| `src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt` | «Mismo proyecto» en BC-10 y en los orígenes de una tarea | 3 |
| `src/main/kotlin/sgspe/investment/ContributionApprovalRules.kt` (crear) | Candado, P7, P9, P4 y P5, P6 (`Synergy`) | 4 |
| `src/main/kotlin/sgspe/investment/ContributionGuidelines.kt` (crear) | P2 y P3 | 5 |
| `src/main/kotlin/sgspe/investment/CampaignCap.kt` (crear), `SupportPolicyGuard.kt` | `CampaignCommitted`, `CampaignCap`; D7 con aportes | 6 |
| `src/main/kotlin/sgspe/investment/ContributionCapRule.kt` (crear) | P1 | 7 |
| `src/main/kotlin/sgspe/investment/ContributionTotals.kt` (crear), `CampaignExecution.kt` | `total_amount`; ejecutado de la campaña con aportes | 8 |
| `src/main/kotlin/sgspe/investment/ContributionDeliveries.kt`, `ContributionDeliveryRules.kt` (crear), `src/main/kotlin/sgspe/commitment/Deliveries.kt`, `Commitments.kt`, `src/main/kotlin/sgspe/evidence/EvidenceRules.kt`, `Evidences.kt` | Endpoint de entrega, reglas de la entrega, corrección, orden de candados | 9 |
| `src/main/kotlin/sgspe/investment/ContributionDeliveryRules.kt` | P8 | 10 |
| `src/main/kotlin/sgspe/attention/Acceptances.kt`, `AttentionRules.kt`, `AttentionWriteGuard.kt` | Aceptación como aporte, campo de comando, `close_unfulfilled` | 11 |
| `src/main/kotlin/sgspe/attention/RequestClosureListener.kt` | Cierre de la solicitud por el aporte | 12 |
| `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`, `Notices.kt` | Aviso de WF-P al alta | 13 |
| `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt` | WF-P y WF-E3 con las guardas de 5.2a | 14 |
| — | Verificación final, push y PR | 15 |

Las pruebas viven en `src/test/kotlin/sgspe/<paquete>/` con el nombre que da cada tarea: unitarias `*Test` (sin base) y de
integración `*ApiTest` (sobre `ContributionApiTest` o `F5ApiTest`, en la CI del PR).

## Orden y paralelismo

Cada tarea se hace en su propio worktree (rama `f52a/t<k>`) cuando las tareas de las que depende ya están integradas en
`feat/fase-5-2a-aportes`. Las de un mismo grupo tocan archivos distintos y no usan nada unas de otras.
`SocialContributionWorkflowApiTest` la crean y la amplían las Tareas 1, 4, 5, 7, 9 y 10, siempre al final de la clase:
por eso esas tareas van en grupos sucesivos. `StakeholderRequestWorkflowApiTest` la cambian la 11 y la 12, en ese orden.
La integración la corre la CI del PR (o el coordinador, una clase tras otra, porque todas usan `sgspe_sdd_test`).

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| A | 1, 2, 3 | La rama creada | Guarda y base de pruebas; montos y políticas; orígenes de tarea y «mismo proyecto»: archivos disjuntos |
| B | 4, 8, 11, 13 | 1 | Reglas de aprobación; totales; aceptación; avisos: archivos disjuntos |
| C | 5, 6 | 2 y 4 (la 5); 8 (la 6) | Lineamientos; `CampaignCommitted` y `SupportPolicyGuard` |
| D | 7 | 5 y 6 | P1 |
| E | 9 | 7 | Endpoint y reglas de la entrega |
| F | 10 | 9 | P8 |
| G | 12, 14 | 10 y 11 (la 12); 5, 7, 10 y 11 (la 14) | Cierre de la solicitud; matriz |
| H | 15 | Todas | Verificación, push y PR |

---
### Tarea 1: Los nombres de WF-P, `InvestmentWriteGuard` y las reglas del registro

**Files:**
- Create: `src/main/kotlin/sgspe/investment/Contributions.kt`
- Create: `src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt`
- Create: `src/main/kotlin/sgspe/investment/ContributionRecordRules.kt`
- Create: `src/test/kotlin/sgspe/ContributionApiTest.kt`
- Test: `src/test/kotlin/sgspe/investment/ContributionDataTest.kt`
- Test: `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt` (integración)

**Interfaces:**
- Consumes: `Records.readable`, `readAs`, `NotReadable`, `RequestMemo`, `Transactions.locked`, `WriteView`, `isLink`,
  `RuleGuard`, `Finding`, `GuardOrder`, `Values`, `Amounts` (`sgspe.common`); `ModelCatalog.fromClasspath()`,
  `catalog.states`, `catalog.transitions`, `catalog.enumOf`, `catalog.derivedFields` (prueba); `F5ApiTest` (plan 5.1) con
  lo de `F4ApiTest` (`member`, `move`, `transition`, `edit`, `createRecord`, `exchange`, `code`, `problemFields`,
  `ifMatch`, `withReason`, `organization`, `agreement`, `evidence`, `uniqueName`).
- Produces:
  - `object Contributions` (`CONTRIBUTION`, `ITEM`, `DELIVERY`, `TOPIC`, `PRIORITY_TOPICS`, `PARENT_TOPIC`, `REQUESTED`,
    `PROCURED`, `APPROVE`, `APPROVE_DIRECT`, `REGISTER_DELIVERY`, `CLOSE`, `APPROVALS`, `CANCELS`, `COMMITTED`,
    `EXECUTED`, `FROZEN_TERMS`, `TOTAL`, `LINE_AMOUNT`, `ITEM_KIND`, `TYPE`, `TOPIC_FIELD`, `JUSTIFICATION`, `SYNERGY`,
    `AGREEMENT`, `CAMPAIGN`, `CURRENCY`, `BENEFICIARIES`, `MAX_ITEMS`, `fun lock(id: UUID): String`).
  - `suspend fun Records.itemsOf(contribution: UUID): List<RecordResponse>` (memo de la corrida de la guarda).
  - `object ContributionData` (`itemsFrozen(state: String?): Finding?`, `itemMoved(): Finding`,
    `frozenTerms(state: String?, changed: Set<String>): List<Finding>`,
    `total(items: List<Map<String, Any?>>): BigDecimal`).
  - `object InvestmentCounts` (`nonNegativeInteger(field: String, after: Map<String, Any?>, changed: Set<String>):
    Finding?`), que usa también el plan 5.2b.
  - `interface InvestmentRule { val order: Int; suspend fun findings(change: RecordWrite, dryRun: Boolean):
    List<Finding> }`; `object InvestmentRuleOrder` (`RECORD = 100`, `APPROVAL = 200`, `DELIVERY = 300`,
    `PROGRAM = 400`); el bean `InvestmentWriteGuard(rules: List<InvestmentRule>)` (`@Order(GuardOrder.DOMAIN)`) con
    `InvestmentWriteGuard.OBJECTS: Set<String>` (5.2b le suma los objetos de programas).
  - El bean `ContributionRecordRules(records, view, transactions)` (`InvestmentRuleOrder.RECORD`).
  - `abstract class ContributionApiTest : F5ApiTest()` con `registrar(project)`, `contributionAttributes(project)`,
    `itemFor(contribution, amount, kind, account): Response`, `requestedContribution(project, items, creator,
    attributes): String`, `approvedWithItems(project, items, attributes): String`, `procuredWithItems(project,
    attributes): String`, `deliveryEvidence(project, types): List<String>`, `captureDelivery(contribution, attributes,
    evidence, headers, token): Response`, `deliveredContribution(project): String`, `cappedCampaign(project, cap,
    currency): String`, `liveAgreement(project): String`, `dryRun(id, transition, account): JsonNode`.

Los nombres y conjuntos de WF-P en una sola fuente, la guarda de BC-10 hecha de reglas ordenadas (como
`CommitmentWriteGuard`) y su primera familia, la del registro (§3.3, «Otras reglas»): ítems congelados fuera de
`requested` (también cambiar el aporte de un ítem), términos aprobados congelados (elección 7) y `beneficiaries_count`
entero no negativo (elección 11). La escritura de un ítem toma el candado del aporte (decisión 1).

- [ ] **Paso 1: Comprobar la rama y escribir las pruebas que fallan**

```bash
cd ~/IdeaProjects/sgspe && git switch main && git pull --ff-only && git switch -c feat/fase-5-2a-aportes
test -f src/test/kotlin/sgspe/F5ApiTest.kt && test -f model/workflows/social_contribution.json && grep -q '"item_kind"' model/model.json && echo "5.1 integrado"
git switch -c f52a/t1
```

Esperado: `5.1 integrado`. Si no, 5.1 todavía no está en `main` y este plan espera.

Crear `src/test/kotlin/sgspe/investment/ContributionDataTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.common.ModelCatalog
import java.math.BigDecimal

// las reglas de datos del aporte y sus ítems (especificación de F5 §3.3, elecciones 7 y 11), sin base
class ContributionDataTest {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `los items cambian solo en requested`() {
        assertNull(ContributionData.itemsFrozen("requested"))
        for (state in listOf("approved", "procured", "delivered", "closed", "rejected", "cancelled", null)) {
            assertEquals("ITEMS_FROZEN" to "social_contribution", ContributionData.itemsFrozen(state)?.let { it.code to it.field }, "$state")
        }
        assertEquals(HttpStatus.CONFLICT, ContributionData.itemMoved().status)
        assertEquals("social_contribution", ContributionData.itemMoved().field)
    }

    @Test
    fun `fuera de requested los terminos aprobados no cambian y los demas campos si`() {
        val changed = setOf("currency", "occasion", "agreement")
        assertEquals(emptyList<String>(), ContributionData.frozenTerms("requested", changed).map { it.field })
        val frozen = ContributionData.frozenTerms("approved", changed)
        assertEquals(listOf("currency", "agreement"), frozen.map { it.field })
        assertTrue(frozen.all { it.code == "CONTRIBUTION_FROZEN" && it.status == HttpStatus.CONFLICT })
        assertEquals(emptyList<String>(), ContributionData.frozenTerms("approved", setOf("occasion", "beneficiaries_count")).map { it.field })
    }

    @Test
    fun `un conteo es un entero de 0 en adelante, vacio pasa y uno que no cambia no se juzga`() {
        fun count(value: Any?) =
            InvestmentCounts.nonNegativeInteger("beneficiaries_count", mapOf("beneficiaries_count" to value), setOf("beneficiaries_count"))?.code
        for (valid in listOf(null, "", 0, 40, "12.00")) assertNull(count(valid), "$valid")
        for (wrong in listOf(-1, "2.5", "muchos")) assertEquals("OUT_OF_RANGE", count(wrong), "$wrong")
        assertNull(InvestmentCounts.nonNegativeInteger("beneficiaries_count", mapOf("beneficiaries_count" to -1), setOf("occasion")))
        val finding = InvestmentCounts.nonNegativeInteger("beneficiaries_count", mapOf("beneficiaries_count" to -1), setOf("beneficiaries_count"))!!
        assertEquals(HttpStatus.BAD_REQUEST, finding.status)
        assertEquals("beneficiaries_count", finding.field)
    }

    @Test
    fun `el total es la suma de line_amount de los items`() {
        val items = listOf(mapOf("line_amount" to 300), mapOf("line_amount" to "150.50"), mapOf<String, Any?>("line_amount" to null))
        assertEquals(0, BigDecimal("450.5").compareTo(ContributionData.total(items)))
        assertEquals(0, BigDecimal.ZERO.compareTo(ContributionData.total(emptyList())))
    }

    // las tablas nombran campos, estados y transiciones por texto: si el modelo renombra uno, la regla dejaría de vigilarlo
    @Test
    fun `los nombres de Contributions existen en el modelo y en WF-P`() {
        val fields =
            catalog.objectOrNull(Contributions.CONTRIBUTION)!!.fields.keys +
                catalog.relationships.filter { it.source == Contributions.CONTRIBUTION }.mapNotNull { it.fieldName }
        assertEquals(emptyList<String>(), Contributions.FROZEN_TERMS.filter { it !in fields })
        assertTrue(Contributions.TOTAL in catalog.derivedFields(Contributions.CONTRIBUTION))
        val states = catalog.states(Contributions.CONTRIBUTION).toSet()
        assertEquals(emptySet<String>(), Contributions.COMMITTED + Contributions.EXECUTED + Contributions.REQUESTED - states)
        val transitions = catalog.transitions(Contributions.CONTRIBUTION).map { it.name }.toSet()
        assertEquals(
            emptySet<String>(),
            Contributions.APPROVALS + Contributions.CANCELS + Contributions.REGISTER_DELIVERY + Contributions.CLOSE - transitions
        )
        assertEquals(listOf("BIEN", "SERVICIO", "DINERO", "MANO DE OBRA"), catalog.enumOf(Contributions.ITEM, Contributions.ITEM_KIND))
    }
}
```

Crear `src/test/kotlin/sgspe/ContributionApiTest.kt`:

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode

// la base de las pruebas de integración del plan 5.2a sobre F5ApiTest del plan 5.1: aportes que ya cumplen las guardas de
// WF-P en todo su ciclo (P9: un ítem BIEN y contribution_type APOYO EN ESPECIE; P5: los registra un
// SOCIAL_INVESTMENT_MANAGER, que no aprueba con approve; P6: sin sinergia; P2 y P3: ni mano de obra ni dinero, y un proyecto
// sin temas priorizados, que solo advierte; P1: sin campaña), la captura de la entrega por su comando con las tres
// evidencias de P8, campañas con tope y acuerdos vigentes. los nombres son propios (decisión 15 del plan 5.2a): no chocan
// con los de F5ApiTest
abstract class ContributionApiTest : F5ApiTest() {
    private val registrars = mutableMapOf<String, Account>()

    // quien registra los aportes de `project`: un SOCIAL_INVESTMENT_MANAGER asignado, el mismo para todo el proyecto
    protected fun registrar(project: String = testProject()): Account = registrars.getOrPut(project) { member("SOCIAL_INVESTMENT_MANAGER", project) }

    protected fun contributionAttributes(project: String): Map<String, Any?> =
        mapOf(
            "project" to project,
            "code" to uniqueName("APO-"),
            "contribution_type" to "APOYO EN ESPECIE",
            "occasion" to "Aniversario de la escuela",
            "declared_purpose" to "Útiles para el aniversario de la escuela.",
            "conditions" to "Se entrega con acta de recepción firmada.",
            "currency" to "PEN",
            "payer_organization" to organization(),
            "has_institutional_synergy" to false
        )

    // un ítem por la API genérica, como `account` (el admin si es null): la respuesta, para las pruebas que esperan un error
    protected fun itemFor(
        contribution: String,
        amount: Any? = 300,
        kind: String = "BIEN",
        account: Account? = null
    ): Response =
        exchange(
            "POST",
            "/api/objects/contribution_item/records",
            mapOf(
                "attributes" to
                    mapOf("social_contribution" to contribution, "description" to "Útiles escolares", "line_amount" to amount, "item_kind" to kind)
            ),
            account?.token ?: token
        )

    // un aporte en requested con sus ítems (monto y tipo), registrado por `creator` (el registrador del proyecto si es null)
    protected fun requestedContribution(
        project: String = testProject(),
        items: List<Pair<Any, String>> = listOf(300 to "BIEN"),
        creator: Account? = null,
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val account = creator ?: registrar(project)
        val id = createRecord("social_contribution", contributionAttributes(project) + attributes, withReason("alta de prueba"), account.token)
        for ((amount, kind) in items) {
            val created = itemFor(id, amount, kind, account)
            assertEquals(HttpStatus.CREATED, created.status, created.body)
        }
        return id
    }

    // aprobado por un APPROVER asignado, que no lo registró (P5)
    protected fun approvedWithItems(
        project: String = testProject(),
        items: List<Pair<Any, String>> = listOf(300 to "BIEN"),
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = requestedContribution(project, items, attributes = attributes)
        move("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")
        return id
    }

    // adquirido por su registrador (register_procurement es de SOCIAL_INVESTMENT_MANAGER)
    protected fun procuredWithItems(
        project: String = testProject(),
        attributes: Map<String, Any?> = emptyMap()
    ): String {
        val id = approvedWithItems(project, attributes = attributes)
        move("social_contribution", id, "register_procurement", registrar(project).token, "compra")
        return id
    }

    // las evidencias de P8, del proyecto del aporte, registradas por el admin
    protected fun deliveryEvidence(
        project: String = testProject(),
        types: List<String> = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE")
    ): List<String> = types.map { evidence(project, it) }

    // la captura de la entrega por su comando (POST /api/sgspe/social-contributions/{id}/deliveries), con conformidad y,
    // salvo `headers`, el If-Match del aporte y el motivo
    protected fun captureDelivery(
        contribution: String,
        attributes: Map<String, Any?> = emptyMap(),
        evidence: List<String> = emptyList(),
        headers: Map<String, String>? = null,
        token: String = this.token
    ): Response =
        exchange(
            "POST",
            "/api/sgspe/social-contributions/$contribution/deliveries",
            mapOf(
                "attributes" to
                    mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva de la escuela") + attributes,
                "evidence" to evidence
            ),
            token,
            headers ?: (ifMatch("social_contribution", contribution, token) + withReason("entrega"))
        )

    // entregado: la captura con sus tres evidencias y register_delivery por el admin
    protected fun deliveredContribution(project: String = testProject()): String {
        val id = procuredWithItems(project)
        val captured = captureDelivery(id, evidence = deliveryEvidence(project))
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        move("social_contribution", id, "register_delivery", reason = "entregado")
        return id
    }

    protected fun cappedCampaign(
        project: String,
        cap: Int,
        currency: String? = "PEN"
    ): String = createRecord("exploration_campaign", mapOf("project" to project, "name" to "Campaña de prueba", "social_support_cap" to cap, "currency" to currency))

    // un acuerdo en active: el CONVENIO firmado de F3ApiTest, activado por el admin
    protected fun liveAgreement(project: String = testProject()): String {
        val id = agreement(project)
        move("agreement", id, "activate_from_draft", reason = "firmado")
        return id
    }

    // la consulta en seco de una transición de WF-P (POST /api/sgspe/transition-checks)
    protected fun dryRun(
        id: String,
        transition: String,
        account: Account? = null
    ): JsonNode =
        tree(
            send(
                "POST",
                "/api/sgspe/transition-checks",
                mapOf("object" to "social_contribution", "recordId" to id, "transition" to transition),
                HttpStatus.OK,
                account?.token ?: token
            )
        )
}
```

Crear `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest

// WF-P (workflows.md, especificación de F5 §3.3 a §3.5, §5): las reglas de InvestmentWriteGuard por la API genérica, por la
// consulta en seco y por el comando de entrega. cada tarea del plan 5.2a suma sus casos al final
class SocialContributionWorkflowApiTest : ContributionApiTest() {
    private fun idOf(response: Response): String = tree(response.body)["id"].asString()

    // decisión 1: después de aprobar los ítems no cambian; un cambio es otro aporte
    @Test
    fun `un item nuevo, cambiado o borrado despues de aprobar es ITEMS_FROZEN`() {
        val id = requestedContribution()
        val item = idOf(itemFor(id, 120))
        move("social_contribution", id, "approve", member("APPROVER").token, "aprobación")
        val added = itemFor(id, 50)
        assertEquals(HttpStatus.CONFLICT, added.status, added.body)
        assertEquals("ITEMS_FROZEN", code(added))
        assertEquals(listOf("social_contribution"), problemFields(added.body))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("line_amount" to 500))))
        val deleted = exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token)
        assertEquals(HttpStatus.CONFLICT, deleted.status, deleted.body)
        assertEquals("ITEMS_FROZEN", code(deleted))
    }

    @Test
    fun `un item no cambia de aporte, y en requested se cambia y se borra`() {
        val id = requestedContribution()
        val other = requestedContribution()
        val item = idOf(itemFor(id, 80))
        assertEquals("ITEMS_FROZEN", code(edit("contribution_item", item, mapOf("social_contribution" to other))))
        assertEquals(HttpStatus.OK, edit("contribution_item", item, mapOf("line_amount" to 90)).status)
        assertTrue(exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token).status.is2xxSuccessful)
    }

    // Review Focus 3 y elección 7: el PUT del portal reenvía lo leído; solo cambiar un término juzgado es CONTRIBUTION_FROZEN
    @Test
    fun `cambiar la moneda o la campana despues de aprobar es CONTRIBUTION_FROZEN y reenviar lo leido pasa`() {
        val project = testProject()
        val id = approvedWithItems(project)
        val currency = edit("social_contribution", id, mapOf("currency" to "USD"), headers = withReason())
        assertEquals(HttpStatus.CONFLICT, currency.status, currency.body)
        assertEquals("CONTRIBUTION_FROZEN", code(currency))
        assertEquals(listOf("currency"), problemFields(currency.body))
        val campaign = edit("social_contribution", id, mapOf("exploration_campaign" to cappedCampaign(project, 1000)), headers = withReason())
        assertEquals("CONTRIBUTION_FROZEN", code(campaign))
        assertEquals(listOf("exploration_campaign"), problemFields(campaign.body))
        assertEquals(HttpStatus.OK, edit("social_contribution", id, emptyMap(), headers = withReason()).status)
        val other = edit("social_contribution", id, mapOf("occasion" to "Día de la comunidad", "beneficiaries_count" to 120), headers = withReason())
        assertEquals(HttpStatus.OK, other.status, other.body)
    }

    @Test
    fun `beneficiaries_count negativo es OUT_OF_RANGE`() {
        val id = requestedContribution()
        val refused = edit("social_contribution", id, mapOf("beneficiaries_count" to -3), headers = withReason())
        assertEquals(HttpStatus.BAD_REQUEST, refused.status, refused.body)
        assertEquals("OUT_OF_RANGE", code(refused))
        assertEquals(listOf("beneficiaries_count"), problemFields(refused.body))
    }
}
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ContributionDataTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ContributionData'`, `'Contributions'`, `'InvestmentCounts'`).

- [ ] **Paso 3: Los nombres, las reglas puras, la guarda y las reglas del registro**

Crear `src/main/kotlin/sgspe/investment/Contributions.kt`:

```kotlin
package sgspe.investment

import org.springframework.http.HttpStatus
import sgspe.common.Amounts
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.RequestMemo
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.util.UUID

// los nombres y los conjuntos de estados de WF-P que comparten la guarda, los comandos, los listeners, JOB-02 y los avisos
// (workflows.md WF-P, dashboards.md §3.2, especificación de F5 §3.3): una sola fuente
object Contributions {
    const val CONTRIBUTION = "social_contribution"
    const val ITEM = "contribution_item"
    const val DELIVERY = "delivery"
    const val TOPIC = "topic"
    const val PRIORITY_TOPICS = "project_priority_topic"
    const val PARENT_TOPIC = "parent_topic"

    const val REQUESTED = "requested"
    const val PROCURED = "procured"
    const val APPROVE = "approve"
    const val APPROVE_DIRECT = "approve_direct"
    const val REGISTER_DELIVERY = "register_delivery"
    const val CLOSE = "close"

    // las transiciones que llegan a approved (P1 a P7 y P9) y las anulaciones, que congelan la entrega
    val APPROVALS: Set<String> = setOf(APPROVE, APPROVE_DIRECT)
    val CANCELS: Set<String> = setOf("cancel_approved", "cancel_procured")

    // comprometido (decisión 1, dashboards.md §3.2): lo que cuenta para el tope de la campaña
    val COMMITTED: Set<String> = setOf("approved", PROCURED, "delivered", "closed")

    // ejecutado (ASM-063): desde la entrega
    val EXECUTED: Set<String> = setOf("delivered", "closed")

    // elección 7: lo que juzgó la aprobación no cambia fuera de requested (CONTRIBUTION_FROZEN)
    val FROZEN_TERMS: List<String> =
        listOf(
            "currency",
            "contribution_type",
            "topic",
            "exploration_campaign",
            "has_institutional_synergy",
            "agreement",
            "guideline_deviation_justification",
            "project"
        )

    const val TOTAL = "total_amount"
    const val LINE_AMOUNT = "line_amount"
    const val ITEM_KIND = "item_kind"
    const val TYPE = "contribution_type"
    const val TOPIC_FIELD = "topic"
    const val JUSTIFICATION = "guideline_deviation_justification"
    const val SYNERGY = "has_institutional_synergy"
    const val AGREEMENT = "agreement"
    const val CAMPAIGN = "exploration_campaign"
    const val CURRENCY = "currency"
    const val BENEFICIARIES = "beneficiaries_count"

    // los ítems de un aporte que se leen como mucho: más es un 400 TOO_MANY_RECORDS, nunca una suma recortada
    const val MAX_ITEMS = 500

    // el candado del aporte (§3.3, «Orden»): lo toman la aprobación, las escrituras de ítems, la captura y la corrección de la
    // entrega, sus vínculos de evidencia, register_delivery y las anulaciones. siempre antes que el de la campaña
    fun lock(id: UUID): String = "$CONTRIBUTION:$id"
}

// los ítems de un aporte como los ve quien escribe, leídos una vez por corrida de InvestmentWriteGuard (que abre un
// RequestMemo): P9, los lineamientos y el tope comparten la lectura. fuera de la guarda (un listener), cada llamada lee
suspend fun Records.itemsOf(contribution: UUID): List<RecordResponse> =
    RequestMemo.remember(Contributions.ITEM to contribution) {
        readAs(Contributions.ITEM) {
            all(
                Contributions.ITEM,
                filters = mapOf(Contributions.CONTRIBUTION to contribution.toString()),
                max = Contributions.MAX_ITEMS,
                field = Contributions.ITEM
            )
        }
    }

// las reglas de datos del aporte y sus ítems, puras (especificación de F5 §3.3, decisión 1 y elección 7)
object ContributionData {
    fun itemsFrozen(state: String?): Finding? =
        if (state == Contributions.REQUESTED) {
            null
        } else {
            Finding(
                "ITEMS_FROZEN",
                "Los ítems de un aporte solo cambian en requested; este está en ${state ?: "ningún estado"}: un cambio después de aprobar es otro aporte (ASM-110)",
                Contributions.CONTRIBUTION
            )
        }

    fun itemMoved(): Finding =
        Finding("ITEMS_FROZEN", "Un ítem no cambia de aporte: bórrelo y regístrelo en el otro mientras esté en requested", Contributions.CONTRIBUTION)

    // los términos que juzgó la aprobación y que la escritura cambia, fuera de requested
    fun frozenTerms(
        state: String?,
        changed: Set<String>
    ): List<Finding> =
        if (state == Contributions.REQUESTED) {
            emptyList()
        } else {
            Contributions.FROZEN_TERMS.filter { it in changed }.map { field ->
                Finding("CONTRIBUTION_FROZEN", "$field no cambia desde la aprobación, que lo juzgó: un aporte distinto se registra de nuevo (ASM-110)", field)
            }
        }

    // total_amount (elección 12): la suma de line_amount, con las cotas de Amounts. AmountFieldsGuard valida cada línea al
    // escribirla, así que un valor guardado no la rompe
    fun total(items: List<Map<String, Any?>>): BigDecimal =
        items.mapNotNull { Amounts.parse(it[Contributions.LINE_AMOUNT], Contributions.LINE_AMOUNT) }.fold(BigDecimal.ZERO, BigDecimal::add)
}

// los conteos de BC-10 (elección 11): beneficiaries_count del aporte y, desde el plan 5.2b, target_count y reached_count
object InvestmentCounts {
    // un entero de 0 en adelante; vacío pasa. se juzga si la escritura lo cambia (en un alta, si llega): 400 OUT_OF_RANGE
    fun nonNegativeInteger(
        field: String,
        after: Map<String, Any?>,
        changed: Set<String>
    ): Finding? {
        if (field !in changed || Values.text(after[field]) == null) return null
        val whole = Values.decimal(after[field])?.let { runCatching { it.stripTrailingZeros().longValueExact() }.getOrNull() }
        if (whole != null && whole >= 0) return null
        return Finding("OUT_OF_RANGE", "$field es un número entero de 0 en adelante", field, status = HttpStatus.BAD_REQUEST)
    }
}
```

Crear `src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt`:

```kotlin
package sgspe.investment

import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.GuardOrder
import sgspe.common.NotReadable
import sgspe.common.RequestMemo
import sgspe.common.RuleGuard
import sgspe.common.toCodedException
import wasichai.core.data.RecordWrite

// una familia de reglas de InvestmentWriteGuard (especificación de F5 §3.3 a §3.6): las del registro (aporte, ítems y
// entrega), las de aprobación (P1 a P7 y P9), la de la entrega (P8) y, desde el plan 5.2b, las de programas. como
// CommitmentRule: no es una WriteRule, la consulta en seco las corre a través de la guarda, una sola vez
//
// ORDEN (§3.3, «Orden»): cada regla declara su `order` y la guarda las corre de menor a mayor; la escritura se detiene en el
// primer hallazgo que bloquea, así que una regla posterior no hace sus lecturas ni toma su candado si una anterior ya
// bloqueó, y la consulta en seco los junta todos
interface InvestmentRule {
    val order: Int

    suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding>
}

// las familias y su lugar: registro, aprobación (candado, P7, P9, P4 y P5, P6, P2 y P3, P1), entrega (P8) y programas (5.2b)
object InvestmentRuleOrder {
    const val RECORD = 100
    const val APPROVAL = 200
    const val DELIVERY = 300
    const val PROGRAM = 400
}

// InvestmentWriteGuard (sgspe.investment): las reglas de BC-10 por cualquier puerta (la API genérica, los comandos y la
// consulta en seco). lo que una regla no puede leer es 403 OBJECT_NOT_READABLE, nunca un pase
@Component
@Order(GuardOrder.DOMAIN)
class InvestmentWriteGuard(
    rules: List<InvestmentRule>
) : RuleGuard() {
    private val ordered = rules.sortedBy { it.order }

    // la consulta en seco: todos los hallazgos, en el orden de las reglas
    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName !in OBJECTS) return emptyList()
        return RequestMemo.within { ordered.flatMap { run(it, change, dryRun) } }
    }

    // la escritura: se detiene en el primer hallazgo que bloquea
    override suspend fun beforeWrite(change: RecordWrite) {
        if (change.objectName !in OBJECTS) return
        RequestMemo.within {
            for (rule in ordered) {
                run(rule, change, dryRun = false).firstOrNull { it.blocking }?.let { throw it.toCodedException() }
            }
        }
    }

    private suspend fun run(
        rule: InvestmentRule,
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        try {
            rule.findings(change, dryRun)
        } catch (e: NotReadable) {
            listOf(
                Finding(
                    "OBJECT_NOT_READABLE",
                    "La regla necesita leer ${e.objectName}: el registro no existe o quien escribe no puede leerlo",
                    e.objectName,
                    status = HttpStatus.FORBIDDEN
                )
            )
        }

    companion object {
        // los objetos de aportes; delivery, por sus entregas de aportes (las reglas miran social_contribution). el plan 5.2b
        // suma los de programas
        val OBJECTS: Set<String> = setOf(Contributions.CONTRIBUTION, Contributions.ITEM, Contributions.DELIVERY)
    }
}
```

Crear `src/main/kotlin/sgspe/investment/ContributionRecordRules.kt`:

```kotlin
package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// InvestmentWriteGuard, registro (especificación de F5 §3.3, «Otras reglas»): beneficiaries_count entero no negativo
// (elección 11); fuera de requested, los términos que juzgó la aprobación no cambian (elección 7, CONTRIBUTION_FROZEN) ni sus
// ítems se crean, cambian o borran (decisión 1, ITEMS_FROZEN); un ítem nunca cambia de aporte. la escritura de un ítem toma
// el candado del aporte, el mismo de la aprobación; la API genérica no abre transacción, así que ese candado acota la
// ventana pero no la cierra (decisión 1 del plan 5.2a). el estado del aporte no viene en `before`: se lee como quien escribe,
// y sin lectura falla cerrado (NotReadable)
@Component
class ContributionRecordRules(
    private val records: Records,
    private val view: WriteView,
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.RECORD

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> =
        when (change.objectName) {
            Contributions.CONTRIBUTION -> contribution(change)
            Contributions.ITEM -> item(change, dryRun)
            else -> emptyList()
        }

    private suspend fun contribution(change: RecordWrite): List<Finding> {
        if (change.kind != RecordChangeKind.CREATED && change.kind != RecordChangeKind.UPDATED) return emptyList()
        if (change.isLink()) return emptyList()
        val after = view.after(change)
        val changed = view.changed(change, after)
        val findings = listOfNotNull(InvestmentCounts.nonNegativeInteger(Contributions.BENEFICIARIES, after, changed)).toMutableList()
        val id = change.recordId
        if (change.kind == RecordChangeKind.UPDATED && id != null && Contributions.FROZEN_TERMS.any { it in changed }) {
            findings += ContributionData.frozenTerms(stateOf(id), changed)
        }
        return findings
    }

    private suspend fun item(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val before = Records.uuid(change.before?.get(Contributions.CONTRIBUTION))
        val after =
            when (change.kind) {
                RecordChangeKind.CREATED, RecordChangeKind.UPDATED -> {
                    if (change.isLink()) return emptyList()
                    Records.uuid(view.after(change)[Contributions.CONTRIBUTION])
                }
                RecordChangeKind.DELETED -> before
                else -> return emptyList()
            }
        if (change.kind == RecordChangeKind.UPDATED && before != after) return listOf(ContributionData.itemMoved())
        val contribution = after ?: return emptyList()
        val state = if (dryRun) stateOf(contribution) else transactions.locked(Contributions.lock(contribution)) { stateOf(contribution) }
        return listOfNotNull(ContributionData.itemsFrozen(state))
    }

    private suspend fun stateOf(id: UUID): String? = records.readable(Contributions.CONTRIBUTION, id).state
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ContributionDataTest` con 5 pruebas en verde; `ArchitectureTest` en verde (los beans
`InvestmentWriteGuard` y `ContributionRecordRules` no repiten nombre).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/Contributions.kt src/main/kotlin/sgspe/investment/InvestmentWriteGuard.kt \
  src/main/kotlin/sgspe/investment/ContributionRecordRules.kt src/test/kotlin/sgspe/ContributionApiTest.kt \
  src/test/kotlin/sgspe/investment/ContributionDataTest.kt src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: guarda de aportes con items y terminos aprobados congelados

InvestmentWriteGuard junta las reglas de BC-10 en orden, como la de compromisos. Fuera de requested
los items de un aporte no se crean, cambian ni borran (ITEMS_FROZEN) y los terminos que juzgo la
aprobacion no cambian (CONTRIBUTION_FROZEN); beneficiaries_count es un entero no negativo.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `SocialContributionWorkflowApiTest` (4 casos de esta tarea).

---

### Tarea 2: Las políticas de aportes y los montos de F5 por la API

**Files:**
- Modify: `src/main/kotlin/sgspe/common/ProjectParameters.kt` (`POLICIES`)
- Verify (no se modifica: lo deja el plan 5.1): `src/main/kotlin/sgspe/common/AmountFieldsGuard.kt` (`FIELDS`, `NOT_MONEY`)
- Test: `src/test/kotlin/sgspe/common/AmountFieldsGuardTest.kt`
- Test: `src/test/kotlin/sgspe/common/ProjectParametersF5Test.kt` (crear)
- Test: `src/test/kotlin/sgspe/common/AmountFieldsF5ApiTest.kt` (crear, integración)

**Interfaces:**
- Consumes: `AmountFieldsGuard.findings`, `FIELDS` y `NOT_MONEY` con los montos de F5 (plan 5.1, «Lo que heredan»);
  `ProjectParameters.policy` (F3); `F5ApiTest` (plan 5.1).
- Produces: `ProjectParameters.policy(project, name)` con `policy_no_paid_labor`, `policy_non_monetary_donations`,
  `policy_synergy_requires_agreement` y `policy_priority_warning` (todas `true` por defecto, `database-model.md` §6);
  las pruebas de los montos de F5 (elección 11).

Elección 11: los montos de F5 con las cotas de `Amounts` (ASM-092) en el alta y en todo cambio de su valor; las cantidades,
líneas base, metas y mediciones no son dinero. El plan 5.1 deja `FIELDS` y `NOT_MONEY` en su estado final para que
`AmountFieldsGuardTest` siga en verde al entrar el modelo (decisión 10): aquí se comprueba y se prueba por la API. Las
cuatro políticas de aportes existen en el modelo desde F3, pero `ProjectParameters` no las conocía (`POLICIES.getValue`
lanzaría): las Tareas 4 y 5 las leen.

- [ ] **Paso 1: Comprobar lo que dejó 5.1 y escribir las pruebas que fallan**

```bash
cd ~/IdeaProjects/sgspe && grep -n '"contribution_item" to setOf("line_amount", "unit_cost")' src/main/kotlin/sgspe/common/AmountFieldsGuard.kt \
  && grep -n '"program_measurement.measured_value"' src/main/kotlin/sgspe/common/AmountFieldsGuard.kt && echo "montos de F5 clasificados"
```

Esperado: `montos de F5 clasificados`. Si no, el plan 5.1 no está integrado como debe (`AmountFieldsGuard` y el grupo de
`task` de `ExactlyOneGuard` son suyos, especificación §7; sin ellos `main` estaría en rojo): detenerse y avisar al
coordinador. Este plan no los edita.

En `src/test/kotlin/sgspe/common/AmountFieldsGuardTest.kt`, agregar antes de la última llave de la clase:

```kotlin

    // elección 11 de la especificación de F5: los montos de BC-10 con las cotas de Amounts; cantidades y valores de indicador no
    @Test
    fun `los montos de F5 se juzgan y las cantidades y los valores de indicador no`() {
        assertEquals(listOf("line_amount" to "OUT_OF_RANGE"), codes("contribution_item", mapOf("line_amount" to -1)))
        assertEquals(listOf("unit_cost" to "INVALID_NUMBER"), codes("contribution_item", mapOf("unit_cost" to "barato")))
        assertEquals(listOf("amount" to "OUT_OF_RANGE"), codes("receipt", mapOf("amount" to "10.12345")))
        assertEquals(
            listOf("allocated_amount" to "OUT_OF_RANGE", "executed_amount" to "OUT_OF_RANGE"),
            codes("program_budget", mapOf("allocated_amount" to -5, "executed_amount" to -1))
        )
        assertEquals(emptyList<Pair<String?, String>>(), codes("contribution_item", mapOf("quantity" to "1.123456", "line_amount" to "1500.50")))
        assertEquals(emptyList<Pair<String?, String>>(), codes("program_measurement", mapOf("measured_value" to -3)))
    }
```

Crear `src/test/kotlin/sgspe/common/ProjectParametersF5Test.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

// los valores por defecto de las políticas de aportes cuando el proyecto trae el parámetro vacío (ASM-019,
// database-model.md §6): existen desde F3 y las lee InvestmentWriteGuard desde F5
class ProjectParametersF5Test {
    private val empty = emptyMap<String, Any?>()
    private val names = listOf("policy_no_paid_labor", "policy_non_monetary_donations", "policy_synergy_requires_agreement", "policy_priority_warning")

    @Test
    fun `las politicas de aportes valen true por defecto y un valor del proyecto manda`() {
        for (name in names) {
            assertEquals(true, ProjectParameters.policy(empty, name), name)
            assertEquals(false, ProjectParameters.policy(mapOf(name to false), name), name)
        }
    }

    @Test
    fun `cada politica de aportes existe en project`() {
        val fields = ModelCatalog.fromClasspath().objectOrNull("project")!!.fields
        for (name in names) assertEquals("BOOLEAN", fields[name]?.type, name)
    }
}
```

Crear `src/test/kotlin/sgspe/common/AmountFieldsF5ApiTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F5ApiTest

// AmountFieldsGuard con los montos de BC-10 por la API genérica (elección 11, ASM-092)
class AmountFieldsF5ApiTest : F5ApiTest() {
    private fun create(
        objectName: String,
        attributes: Map<String, Any?>
    ): Response = exchange("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), token)

    @Test
    fun `un item con monto negativo y un comprobante con un texto no se guardan`() {
        val id = socialContribution(testProject())
        val item = create("contribution_item", mapOf("social_contribution" to id, "description" to "Útiles", "item_kind" to "BIEN", "line_amount" to -1))
        assertEquals(HttpStatus.BAD_REQUEST, item.status, item.body)
        assertEquals("OUT_OF_RANGE", code(item))
        assertEquals(listOf("line_amount"), problemFields(item.body))
        val receipt = create("receipt", mapOf("social_contribution" to id, "amount" to "mil", "currency" to "PEN"))
        assertEquals("INVALID_NUMBER", code(receipt))
        assertEquals(listOf("amount"), problemFields(receipt.body))
        assertEquals(HttpStatus.CREATED, create("receipt", mapOf("social_contribution" to id, "amount" to "250.50", "currency" to "PEN")).status)
    }
}
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.common.AmountFieldsGuardTest' --tests 'sgspe.common.ProjectParametersF5Test'
```

Esperado: FAIL en `las politicas de aportes…` (`NoSuchElementException: Key policy_no_paid_labor is missing in the map`);
`los montos de F5…` ya pasa si 5.1 clasificó los montos (es la prueba que lo fija).

- [ ] **Paso 3: Las políticas**

En `src/main/kotlin/sgspe/common/ProjectParameters.kt`, reemplazar:

```kotlin
            // F4: una queja ALTA o CRITICA la resuelve quien tiene APPROVE sobre grievance (RN-GRV-002)
            "policy_high_severity_requires_approver" to true
        )
```

por:

```kotlin
            // F4: una queja ALTA o CRITICA la resuelve quien tiene APPROVE sobre grievance (RN-GRV-002)
            "policy_high_severity_requires_approver" to true,
            // F5: los lineamientos de apoyo de los aportes (RN-INV-001, RN-INV-002, RN-INV-005) y la sinergia (RN-INV-004)
            "policy_no_paid_labor" to true,
            "policy_non_monetary_donations" to true,
            "policy_synergy_requires_agreement" to true,
            "policy_priority_warning" to true
        )
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `AmountFieldsGuardTest` y `ProjectParametersF5Test` en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/ProjectParameters.kt src/test/kotlin/sgspe/common/AmountFieldsGuardTest.kt \
  src/test/kotlin/sgspe/common/ProjectParametersF5Test.kt src/test/kotlin/sgspe/common/AmountFieldsF5ApiTest.kt
git commit -F - <<'MSG'
feat: políticas de aportes y pruebas de los montos de F5

ProjectParameters conoce las cuatro políticas de aportes con su valor por defecto; los montos de
los ítems, del comprobante y del presupuesto de programas se prueban con sus cotas (ASM-092).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```


**Integración pendiente (coordinador):** `AmountFieldsF5ApiTest`.

---

### Tarea 3: «Mismo proyecto» en BC-10 y en los orígenes de una tarea

**Files:**
- Modify: `src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt` (`OBJECTS`)
- Verify: `src/main/kotlin/sgspe/common/ExactlyOneGuard.kt` (`GROUPS["task"]` con `social_program`, plan 5.1)
- Test: `src/test/kotlin/sgspe/scope/ProjectLinkF5Test.kt` (crear)
- Test: `src/test/kotlin/sgspe/scope/ProjectLinkGuardF5ApiTest.kt` (crear, integración)

**Interfaces:**
- Consumes: `ProjectLinks.fieldsOf`, `ProjectScope.covers` y `ROOTS` con BC-10, y `ExactlyOneGuard.GROUPS["task"]` con
  `social_program` entre `agreement` y `stakeholder` (plan 5.1); `F5ApiTest` (`socialContribution`, `campaign`).
- Produces: `ProjectLinkGuard.OBJECTS` con `social_contribution`, `social_program` y `task`.

Elección 5: `PROJECT_MISMATCH` cubre `exploration_campaign`, `stakeholder`, `commitment` y `agreement` del aporte,
`agreement` del programa y los orígenes de la tarea (hasta F4 no se juzgaban). El origen `social_program` de la tarea lo
dejó 5.1 en `ExactlyOneGuard` (decisión 10): aquí solo se comprueba.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/scope/ProjectLinkF5Test.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import sgspe.common.ExactlyOneGuard
import sgspe.common.ModelCatalog

// la regla «mismo proyecto» de BC-10 y de las tareas (elección 5 de la especificación de F5): los campos que juzga salen del
// catálogo, así que basta con que el objeto esté en ProjectLinkGuard.OBJECTS
class ProjectLinkF5Test {
    private val catalog = ModelCatalog.fromClasspath()

    @Test
    fun `el aporte, el programa y la tarea se juzgan`() {
        assertTrue(ProjectLinkGuard.OBJECTS.containsAll(setOf("social_contribution", "social_program", "task")))
        val contribution = ProjectLinks.fieldsOf(catalog, "social_contribution").toMap()
        for (field in listOf("exploration_campaign", "stakeholder", "commitment", "agreement")) assertTrue(field in contribution, field)
        assertEquals("agreement", ProjectLinks.fieldsOf(catalog, "social_program").toMap()["agreement"])
        val task = ProjectLinks.fieldsOf(catalog, "task").toMap()
        for (field in ExactlyOneGuard.GROUPS.getValue("task").single().filter { it in catalog.objects }) assertTrue(field in task, field)
    }

    @Test
    fun `una tarea puede nacer de un programa`() {
        assertTrue("social_program" in ExactlyOneGuard.GROUPS.getValue("task").single())
    }
}
```

Crear `src/test/kotlin/sgspe/scope/ProjectLinkGuardF5ApiTest.kt`:

```kotlin
package sgspe.scope

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.F5ApiTest

// «mismo proyecto» en BC-10 y en las tareas (elección 5, api.md §3.3): un aporte o una tarea solo referencian registros de su
// proyecto; del mismo proyecto, pasan
class ProjectLinkGuardF5ApiTest : F5ApiTest() {
    private fun contribution(extra: Map<String, Any?>): Response =
        exchange(
            "POST",
            "/api/objects/social_contribution/records",
            mapOf(
                "attributes" to
                    mapOf(
                        "project" to testProject(),
                        "code" to uniqueName("APO-"),
                        "declared_purpose" to "Útiles para la escuela.",
                        "conditions" to "Con acta de recepción.",
                        "currency" to "PEN",
                        "payer_organization" to organization()
                    ) + extra
            ),
            token,
            withReason("alta de prueba")
        )

    @Test
    fun `un aporte con la campana o el acuerdo de otro proyecto es PROJECT_MISMATCH`() {
        val other = project()
        val foreignCampaign = contribution(mapOf("exploration_campaign" to campaign(other)))
        assertEquals(HttpStatus.CONFLICT, foreignCampaign.status, foreignCampaign.body)
        assertEquals("PROJECT_MISMATCH", code(foreignCampaign))
        assertEquals(listOf("exploration_campaign"), problemFields(foreignCampaign.body))
        val foreignAgreement = contribution(mapOf("agreement" to agreement(other)))
        assertEquals("PROJECT_MISMATCH", code(foreignAgreement))
        assertEquals(listOf("agreement"), problemFields(foreignAgreement.body))
        assertEquals(HttpStatus.CREATED, contribution(mapOf("exploration_campaign" to campaign(testProject()), "agreement" to agreement(testProject()))).status)
    }

    @Test
    fun `una tarea con el origen de otro proyecto es PROJECT_MISMATCH`() {
        val response =
            exchange(
                "POST",
                "/api/objects/task/records",
                mapOf("attributes" to mapOf("project" to testProject(), "title" to "Coordinar la visita", "engagement" to engagement(project()))),
                token
            )
        assertEquals(HttpStatus.CONFLICT, response.status, response.body)
        assertEquals("PROJECT_MISMATCH", code(response))
        assertEquals(listOf("engagement"), problemFields(response.body))
        task(testProject())
    }
}
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.scope.ProjectLinkF5Test'
```

Esperado: FAIL en `el aporte, el programa y la tarea se juzgan`; `una tarea puede nacer de un programa` pasa (lo dejó 5.1;
si falla, 5.1 no está integrado y esta tarea espera).

- [ ] **Paso 3: La tabla de `ProjectLinkGuard`**

En `src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt`, reemplazar:

```kotlin
        // los objetos de F4 (BC-07, BC-08 y BC-09)
        val OBJECTS: Set<String> =
            setOf(
                "grievance",
```

por:

```kotlin
        // los objetos de F4 (BC-07, BC-08 y BC-09) y, desde F5 (elección 5), el aporte, el programa y la tarea, cuyos orígenes
        // hasta F4 no se juzgaban
        val OBJECTS: Set<String> =
            setOf(
                "social_contribution",
                "social_program",
                "task",
                "grievance",
```

y en el comentario de la clase, cambiar «la regla «mismo proyecto» de los objetos de F4» por «la regla «mismo proyecto»
de los objetos de F4, de BC-10 y de las tareas».

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ProjectLinkF5Test`, `ProjectLinksTest` y `ProjectScopeF4Test` en verde (`task`,
`social_contribution` y `social_program` son raíces de `ProjectScope`).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/scope/ProjectLinkGuard.kt src/test/kotlin/sgspe/scope/ProjectLinkF5Test.kt \
  src/test/kotlin/sgspe/scope/ProjectLinkGuardF5ApiTest.kt
git commit -F - <<'MSG'
feat: mismo proyecto en aportes, programas y tareas

ProjectLinkGuard juzga las relaciones del aporte y del programa y los orígenes de una tarea, que
hasta F4 no se juzgaban (PROJECT_MISMATCH).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `ProjectLinkGuardF5ApiTest` y, por los orígenes de tareas que ahora se juzgan,
`TaskWorkflowApiTest`, `AutomationsApiTest` y `DeadlineJobQcsApiTest`.

---

### Tarea 4: Las reglas de aprobación: candado, P7, P9, P4 y P5, P6

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ContributionApprovalRules.kt`
- Test: `src/test/kotlin/sgspe/investment/ContributionApprovalRulesTest.kt` (crear)
- Test: `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt` (casos al final, integración)

**Interfaces:**
- Consumes: `Contributions`, `Records.itemsOf`, `InvestmentRule`, `InvestmentRuleOrder` (Tarea 1);
  `ProjectParameters.policy` con `policy_synergy_requires_agreement` (Tarea 2); `Records.projectOf` (`sgspe.commitment`);
  `Records.readable`, `readAs`, `NotReadable`, `Callers`, `Permissions`, `Transactions` (`sgspe.common`).
- Produces:
  - `object ContributionApproval` (`RECORD = "record"`, `APPROVE_ACTION = "APPROVE"`,
    `INTERNAL_APPROVAL = "policy_internal_approval"`, `fun target(change: RecordWrite, transitions: Set<String>): UUID?`).
  - `object Synergy` (`POLICY`, `ACTIVE`, `EXPIRED`, `VALIDITY = "validity_status"`, `fun live(state: String?,
    validity: String?): Boolean`, `fun required(why: String): Finding`), que usa también G3 del plan 5.2b.
  - Los beans `ContributionLockRule` (`APPROVAL`), `ContributionDirectApprovalRule` (`APPROVAL + 1`, P7),
    `ContributionItemsRule` (`APPROVAL + 2`, P9), `ContributionKeyRule` (`APPROVAL + 3`, P4 y P5) y
    `ContributionSynergyRule` (`APPROVAL + 4`, P6). Las Tareas 5 y 7 ocupan `APPROVAL + 5` (P2 y P3) y `APPROVAL + 6` (P1).

El orden de §3.3: el candado del aporte (también en `register_delivery` y las anulaciones, decisión 5), P7, P9 (primero
los ítems y después el tipo, decisión 3), P4 y P5 (solo `approve`; `created_by` como D4, también quien aceptó la solicitud
que lo creó), P6 (con `has_institutional_synergy` y la política activa, un acuerdo en `active` y no VENCIDO, sin
justificación posible). P4 no se alcanza por la API con los roles de §7.7: la integración lo prueba en seco (decisión 13).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/investment/ContributionApprovalRulesTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordWrite
import java.util.UUID

// P9 y P6 de WF-P (especificación de F5 §3.3) con un Records de prueba, y las piezas puras que comparten con el plan 5.2b
class ContributionApprovalRulesTest {
    private var items: List<Map<String, Any?>> = emptyList()
    private var agreement: RecordResponse? = null
    private var project: Map<String, Any?> = emptyMap()
    private val projectId = UUID.randomUUID().toString()

    private val records: Records =
        mock(Records::class.java) { call ->
            when (call.method.name) {
                "all" -> items.map { RecordResponse(id = UUID.randomUUID().toString(), createdAt = null, updatedAt = null, attributes = it) }
                "find" ->
                    when (call.arguments[0]) {
                        "agreement" -> agreement
                        "project" -> RecordResponse(id = projectId, createdAt = null, updatedAt = null, attributes = project)
                        else -> null
                    }
                else -> null
            }
        }

    private fun write(
        name: String,
        before: Map<String, Any?>,
        objectName: String = Contributions.CONTRIBUTION,
        kind: RecordChangeKind = RecordChangeKind.TRANSITIONED
    ) = RecordWrite(
        organizationId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        objectId = UUID.randomUUID(),
        objectName = objectName,
        recordId = UUID.randomUUID(),
        kind = kind,
        before = before,
        transition = name
    )

    private fun codes(
        rule: InvestmentRule,
        name: String,
        before: Map<String, Any?>,
        dryRun: Boolean = true
    ) = runBlocking { rule.findings(write(name, before), dryRun) }.map { it.code to it.field }

    @Test
    fun `una regla de aprobacion mira solo las transiciones de WF-P que nombra`() {
        assertEquals(null, ContributionApproval.target(write("approve", emptyMap(), objectName = "commitment"), Contributions.APPROVALS))
        assertEquals(null, ContributionApproval.target(write("reject", emptyMap()), Contributions.APPROVALS))
        assertNull(ContributionApproval.target(write("approve", emptyMap(), kind = RecordChangeKind.UPDATED), Contributions.APPROVALS))
        assertTrue(ContributionApproval.target(write("approve_direct", emptyMap()), Contributions.APPROVALS) != null)
    }

    @Test
    fun `P9 pide items y contribution_type, primero los items y en seco los dos`() {
        val rule = ContributionItemsRule(records)
        assertEquals(listOf("ITEMS_REQUIRED" to "contribution_item", "FIELD_REQUIRED" to "contribution_type"), codes(rule, "approve", emptyMap()))
        assertEquals(listOf("ITEMS_REQUIRED" to "contribution_item"), codes(rule, "approve_direct", emptyMap(), dryRun = false))
        items = listOf(mapOf("line_amount" to 10, "item_kind" to "BIEN"))
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", mapOf("contribution_type" to "DONACION")))
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "reject", emptyMap()))
    }

    @Test
    fun `un acuerdo vigente esta en active y no VENCIDO`() {
        assertTrue(Synergy.live("active", null))
        assertTrue(Synergy.live("active", "POR VENCER"))
        assertFalse(Synergy.live("active", "VENCIDO"))
        assertFalse(Synergy.live("draft", "VIGENTE"))
        assertFalse(Synergy.live(null, null))
    }

    @Test
    fun `P6 exige un acuerdo vigente con sinergia y la politica activa, tambien con justificacion`() {
        val rule = ContributionSynergyRule(records)
        val synergy = mapOf("has_institutional_synergy" to true, "guideline_deviation_justification" to "Convenio en trámite", "project" to projectId)
        assertEquals(listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement"), codes(rule, "approve", synergy))
        val id = UUID.randomUUID().toString()
        agreement = RecordResponse(id = id, createdAt = null, updatedAt = null, attributes = mapOf("validity_status" to "VIGENTE"), state = "draft")
        assertEquals(listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement"), codes(rule, "approve", synergy + ("agreement" to id)))
        agreement = agreement!!.copy(attributes = mapOf("validity_status" to "VENCIDO"), state = "active")
        assertEquals(listOf("SYNERGY_AGREEMENT_REQUIRED" to "agreement"), codes(rule, "approve_direct", synergy + ("agreement" to id)))
        agreement = agreement!!.copy(attributes = mapOf("validity_status" to "POR VENCER"))
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", synergy + ("agreement" to id)))
        // sin sinergia, o con la política apagada, no se exige
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", mapOf("has_institutional_synergy" to false)))
        project = mapOf(Synergy.POLICY to false)
        assertEquals(emptyList<Pair<String, String?>>(), codes(rule, "approve", synergy))
    }
}
```

Agregar al final de `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt`, antes de la última llave:

```kotlin

    // ---- Tarea 4: P4, P5, P6, P7 y P9 ----

    @Test
    fun `aprueban APPROVER y SOCIAL_MANAGER, pero no quien registro el aporte`() {
        val project = testProject()
        for (role in listOf("APPROVER", "SOCIAL_MANAGER")) {
            val id = requestedContribution(project)
            move("social_contribution", id, "approve", member(role, project).token, "aprobación")
            assertEquals("approved", state("social_contribution", id), role)
        }
        val manager = member("SOCIAL_MANAGER", project)
        val own = requestedContribution(project, creator = manager)
        val refused = transition("social_contribution", own, "approve", manager.token, "aprobación")
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("SEGREGATION_OF_DUTIES", code(refused))
        assertEquals(listOf("record"), problemFields(refused.body))
        assertEquals(HttpStatus.OK, transition("social_contribution", own, "approve", member("SOCIAL_MANAGER", project).token, "aprobación").status)
    }

    // decisión 13 del plan 5.2a: los dos roles de approve tienen APPROVE, así que P4 se ve en la consulta en seco
    @Test
    fun `sin la accion APPROVE la consulta en seco da ACTION_REQUIRED`() {
        val id = requestedContribution()
        val check = dryRun(id, "approve", member("SOCIAL_INVESTMENT_MANAGER"))
        assertTrue(check["blocking"].values().any { it["code"].asString() == "ACTION_REQUIRED" && it["field"].asString() == "record" }, check.toString())
    }

    @Test
    fun `sin items es ITEMS_REQUIRED y sin contribution_type es FIELD_REQUIRED`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val empty = requestedContribution(project, items = emptyList())
        val noItems = transition("social_contribution", empty, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, noItems.status, noItems.body)
        assertEquals("ITEMS_REQUIRED", code(noItems))
        assertEquals(listOf("contribution_item"), problemFields(noItems.body))
        val untyped = requestedContribution(project, attributes = mapOf("contribution_type" to null))
        val noType = transition("social_contribution", untyped, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.BAD_REQUEST, noType.status, noType.body)
        assertEquals("FIELD_REQUIRED", code(noType))
        assertEquals(listOf("contribution_type"), problemFields(noType.body))
        assertEquals("requested", state("social_contribution", empty))
    }

    @Test
    fun `approve_direct con la aprobacion interna activa es INTERNAL_APPROVAL_REQUIRED`() {
        val strict = testProject()
        val id = requestedContribution(strict)
        val refused = transition("social_contribution", id, "approve_direct", registrar(strict).token, "aprobación directa")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("INTERNAL_APPROVAL_REQUIRED", code(refused))
        assertEquals(listOf("project"), problemFields(refused.body))
        val relaxed = project(mapOf("policy_internal_approval" to false))
        val direct = requestedContribution(relaxed)
        move("social_contribution", direct, "approve_direct", registrar(relaxed).token, "aprobación directa")
        assertEquals("approved", state("social_contribution", direct))
    }

    // elección 9: la sinergia no es un lineamiento, así que la justificación no la cubre
    @Test
    fun `una sinergia exige un acuerdo vigente, tambien con justificacion`() {
        val project = testProject()
        val approver = member("APPROVER", project)

        fun approve(extra: Map<String, Any?>): Response {
            val attributes = mapOf("has_institutional_synergy" to true, "guideline_deviation_justification" to "Convenio en trámite con la municipalidad.") + extra
            return transition("social_contribution", requestedContribution(project, attributes = attributes), "approve", approver.token, "aprobación")
        }
        for (extra in listOf(emptyMap(), mapOf("agreement" to agreement(project)))) {
            val refused = approve(extra)
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(refused))
            assertEquals(listOf("agreement"), problemFields(refused.body))
        }
        val expired = liveAgreement(project).also { platformPatch("agreement", it, mapOf("validity_status" to "VENCIDO")) }
        assertEquals("SYNERGY_AGREEMENT_REQUIRED", code(approve(mapOf("agreement" to expired))))
        assertEquals(HttpStatus.OK, approve(mapOf("agreement" to liveAgreement(project))).status)
    }
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ContributionApprovalRulesTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ContributionItemsRule'`, `'Synergy'`, `'ContributionApproval'`).

- [ ] **Paso 3: Las reglas**

Crear `src/main/kotlin/sgspe/investment/ContributionApprovalRules.kt`:

```kotlin
package sgspe.investment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.commitment.projectOf
import sgspe.common.Callers
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordCriterion
import wasichai.core.data.RecordWrite
import java.util.UUID

// InvestmentWriteGuard, aprobación (especificación de F5 §3.3): el candado del aporte, P7, P9, P4 y P5 y P6, en ese orden;
// P2 y P3 (ContributionGuidelinesRule) y P1 (ContributionCapRule, la única que toma el candado de la campaña) van después.
// deciden con permisos y datos, nunca con nombres de rol
object ContributionApproval {
    // el campo de los hallazgos sobre quien aprueba (P4, P5): el mismo que toCodedException pone al escribir
    const val RECORD = "record"
    const val APPROVE_ACTION = "APPROVE"
    const val INTERNAL_APPROVAL = "policy_internal_approval"

    // el aporte de la transición si es una de `transitions`; si no, null y la regla no mira la escritura
    fun target(
        change: RecordWrite,
        transitions: Set<String>
    ): UUID? {
        if (change.objectName != Contributions.CONTRIBUTION || change.kind != RecordChangeKind.TRANSITIONED) return null
        if (change.transition !in transitions) return null
        return change.recordId
    }
}

// «acuerdo vigente» (elección 9 de la especificación de F5): en active y con validity_status distinto de VENCIDO. lo usan P6 y,
// desde el plan 5.2b, G3 del programa
object Synergy {
    const val POLICY = "policy_synergy_requires_agreement"
    const val ACTIVE = "active"
    const val EXPIRED = "VENCIDO"
    const val VALIDITY = "validity_status"

    fun live(
        state: String?,
        validity: String?
    ): Boolean = state == ACTIVE && validity != EXPIRED

    fun required(why: String): Finding =
        Finding(
            "SYNERGY_AGREEMENT_REQUIRED",
            "Una sinergia institucional exige un acuerdo vigente, en active y no VENCIDO: $why (RN-INV-004)",
            Contributions.AGREEMENT
        )
}

// el candado del aporte (§3.3, «Orden»; decisión 5 del plan 5.2a): lo toman las aprobaciones, antes de que P9, los
// lineamientos y el tope lean los ítems, register_delivery, antes de que P8 lea la entrega, y las anulaciones, que congelan la
// entrega. es el mismo de las escrituras de ítems y de la entrega. la guarda corre dentro de la transacción de
// WorkflowService.apply, así que el candado se suelta con su commit o su rollback. la consulta en seco no lo toma
@Component
class ContributionLockRule(
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (dryRun) return emptyList()
        val id =
            ContributionApproval.target(change, Contributions.APPROVALS + Contributions.REGISTER_DELIVERY + Contributions.CANCELS)
                ?: return emptyList()
        transactions.locked(Contributions.lock(id)) {}
        return emptyList()
    }
}

// P7: approve_direct solo en un proyecto sin aprobación interna (ASM-008). va primero: decide si la transición tiene sentido en
// el proyecto antes de pedir datos
@Component
class ContributionDirectApprovalRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        ContributionApproval.target(change, setOf(Contributions.APPROVE_DIRECT)) ?: return emptyList()
        if (!ProjectParameters.policy(records.projectOf(change.before.orEmpty()), ContributionApproval.INTERNAL_APPROVAL)) return emptyList()
        return listOf(
            Finding(
                "INTERNAL_APPROVAL_REQUIRED",
                "El proyecto tiene la aprobación interna activa: el aporte se aprueba con approve, con doble llave y segregación (P7, ASM-008)",
                "project"
            )
        )
    }
}

// P9: al menos un ítem (el total de los ítems es lo que se aprueba, decisión 1) y contribution_type (elección 8), en ese orden
@Component
class ContributionItemsRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 2

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val findings = mutableListOf<Finding>()
        if (records.itemsOf(id).isEmpty()) {
            findings += Finding("ITEMS_REQUIRED", "Aprobar un aporte exige al menos un ítem: el total de sus ítems es lo que se aprueba (P9, ASM-110)", Contributions.ITEM)
            if (!dryRun) return findings
        }
        if (Values.text(change.before.orEmpty()[Contributions.TYPE]) == null) {
            findings +=
                Finding(
                    "FIELD_REQUIRED",
                    "Aprobar un aporte exige contribution_type: decide qué lineamientos se evalúan (P9, ASM-112)",
                    Contributions.TYPE,
                    status = HttpStatus.BAD_REQUEST
                )
        }
        return findings
    }
}

// P4 y P5: la doble llave de approve (la acción APPROVE sobre social_contribution, RF-WFL-002) y la segregación: quien aprueba
// no es created_by del aporte (quien lo registró o quien aceptó la solicitud que lo creó, ASM-008). approve_direct no las pide
@Component
class ContributionKeyRule(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 3

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, setOf(Contributions.APPROVE)) ?: return emptyList()
        val user = callers.userOrNull()
        val findings = mutableListOf<Finding>()
        if (user == null || !permissions.has(user, ContributionApproval.APPROVE_ACTION, Contributions.CONTRIBUTION)) {
            findings +=
                Finding(
                    "ACTION_REQUIRED",
                    "Aprobar un aporte exige la acción APPROVE sobre social_contribution (P4)",
                    ContributionApproval.RECORD,
                    status = HttpStatus.FORBIDDEN
                )
            if (!dryRun) return findings
        }
        if (user == null || createdBy(id, user.userId)) {
            findings +=
                Finding(
                    "SEGREGATION_OF_DUTIES",
                    "Quien aprueba un aporte no puede ser quien lo registró o aceptó la solicitud que lo creó (P5, ASM-008)",
                    ContributionApproval.RECORD,
                    status = HttpStatus.FORBIDDEN
                )
        }
        return findings
    }

    // created_by es una columna de sistema: se compara en la base como quien llama, como D4. falla cerrada: si quien aprueba no
    // ve el aporte, el conteo con created_by daría 0 y la segregación pasaría en silencio; es NotReadable
    private suspend fun createdBy(
        id: UUID,
        userId: UUID
    ): Boolean =
        readAs(Contributions.CONTRIBUTION) {
            if (records.count(Contributions.CONTRIBUTION, ids = listOf(id)) == 0L) throw NotReadable(Contributions.CONTRIBUTION)
            records.count(Contributions.CONTRIBUTION, criteria = listOf(RecordCriterion { _, bind -> "created_by = ${bind(userId)}" }), ids = listOf(id)) > 0
        }
}

// P6: con has_institutional_synergy y la política activa, un acuerdo vigente (Synergy.live), con o sin justificación
// (elección 9). que el acuerdo sea del proyecto del aporte lo juzga ProjectLinkGuard; uno que no se lee es NotReadable
@Component
class ContributionSynergyRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 4

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val before = change.before.orEmpty()
        if (Values.bool(before[Contributions.SYNERGY]) != true) return emptyList()
        if (!ProjectParameters.policy(records.projectOf(before), Synergy.POLICY)) return emptyList()
        val id = Records.uuid(before[Contributions.AGREEMENT]) ?: return listOf(Synergy.required("el aporte no tiene acuerdo"))
        val agreement = records.readable(Contributions.AGREEMENT, id)
        val validity = Values.text(agreement.attributes[Synergy.VALIDITY])
        if (Synergy.live(agreement.state, validity)) return emptyList()
        return listOf(Synergy.required("el acuerdo está en ${agreement.state ?: "ningún estado"}${validity?.let { " y $it" } ?: ""}"))
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ContributionApprovalRulesTest` con 4 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ContributionApprovalRules.kt src/test/kotlin/sgspe/investment/ContributionApprovalRulesTest.kt \
  src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: reglas de aprobación de aportes: ítems, doble llave, segregación y sinergia

La aprobación de un aporte toma su candado, exige la aprobación interna apagada en approve_direct
(P7), al menos un ítem y el tipo de aporte (P9), la acción APPROVE y que no apruebe quien lo registró
(P4, P5) y, con sinergia, un acuerdo en active y no VENCIDO aunque haya justificación (P6).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `SocialContributionWorkflowApiTest` (casos de las Tareas 1 y 4).

---

### Tarea 5: Los lineamientos: `ContributionGuidelines` (P2 y P3)

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ContributionGuidelines.kt`
- Test: `src/test/kotlin/sgspe/investment/ContributionGuidelinesTest.kt` (crear)
- Test: `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt` (casos al final, integración)

**Interfaces:**
- Consumes: `Contributions`, `Records.itemsOf`, `InvestmentRule`, `InvestmentRuleOrder` (Tarea 1);
  `ContributionApproval.target` (Tarea 4); `ProjectParameters.policy` con las políticas de aportes (Tarea 2);
  `Records.projectOf` (`sgspe.commitment`); `Records.related`, `Records.readable`, `readAs` (`sgspe.common`).
- Produces: `object ContributionGuidelines` (`NO_PAID_LABOR`, `NON_MONETARY`, `PRIORITY`, `LABOR`, `MONEY`, `DONATION`,
  `MAX_TOPIC_DEPTH = 10`, `MAX_PRIORITIES = 500`, `data class Input(project: Map<String, Any?>, priorities: Set<UUID>,
  lineage: List<UUID>, itemKinds: List<String?>, contributionType: String?, justification: String?)`,
  `fun evaluate(input: Input): List<Finding>`); el bean `ContributionGuidelinesRule(records)` (`APPROVAL + 5`).

Decisión 3 y §3.4: cada política activa se evalúa al aprobar (`approve` y `approve_direct`); cada lineamiento incumplido
bloquea (`409`) sin `guideline_deviation_justification` y es advertencia con ella (una justificación cubre todos, RN-INV-006;
consecuencia aceptada 4); `PRIORITIES_NOT_CONFIGURED` siempre es advertencia; una política apagada no se evalúa. Un tema
cuenta si es priorizado o desciende de uno por `parent_topic`, hasta 10 niveles (elección 8). La sinergia no es un
lineamiento (P6, Tarea 4).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/investment/ContributionGuidelinesTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.common.ModelCatalog
import java.util.UUID

// los lineamientos de apoyo (decisión 3, §3.4 de la especificación de F5; RN-INV-001, 002, 005 y 006), puros
class ContributionGuidelinesTest {
    private val catalog = ModelCatalog.fromClasspath()
    private val education = UUID.randomUUID()
    private val schools = UUID.randomUUID()
    private val roads = UUID.randomUUID()

    private fun input(
        kinds: List<String?> = listOf("BIEN"),
        type: String? = "APOYO EN ESPECIE",
        priorities: Set<UUID> = setOf(education),
        lineage: List<UUID> = listOf(schools, education),
        justification: String? = null,
        project: Map<String, Any?> = emptyMap()
    ) = ContributionGuidelines.Input(project, priorities, lineage, kinds, type, justification)

    private fun found(input: ContributionGuidelines.Input) = ContributionGuidelines.evaluate(input).map { Triple(it.code, it.field, it.blocking) }

    @Test
    fun `un aporte de bienes con tema priorizado o descendiente cumple`() {
        assertEquals(emptyList<Triple<String, String?, Boolean>>(), found(input()))
        assertEquals(emptyList<Triple<String, String?, Boolean>>(), found(input(lineage = listOf(education))))
    }

    @Test
    fun `mano de obra y dinero en una donacion bloquean sin justificacion`() {
        assertEquals(listOf(Triple("PAID_LABOR", "contribution_item", true)), found(input(kinds = listOf("BIEN", "MANO DE OBRA"))))
        assertEquals(listOf(Triple("MONETARY_DONATION", "contribution_item", true)), found(input(kinds = listOf("DINERO"), type = "DONACION")))
        // dinero en un apoyo en especie no es una donación monetaria
        assertEquals(emptyList<Triple<String, String?, Boolean>>(), found(input(kinds = listOf("DINERO"))))
        assertEquals(HttpStatus.CONFLICT, ContributionGuidelines.evaluate(input(kinds = listOf("MANO DE OBRA"))).single().status)
    }

    @Test
    fun `un tema fuera de las prioridades o sin tema es PRIORITY_NOT_MET`() {
        assertEquals(listOf(Triple("PRIORITY_NOT_MET", "topic", true)), found(input(lineage = listOf(roads))))
        assertEquals(listOf(Triple("PRIORITY_NOT_MET", "topic", true)), found(input(lineage = emptyList())))
    }

    @Test
    fun `sin temas priorizados solo se advierte, aunque no haya justificacion`() {
        assertEquals(listOf(Triple("PRIORITIES_NOT_CONFIGURED", "topic", false)), found(input(priorities = emptySet(), lineage = emptyList())))
    }

    // Review Focus 4 y consecuencia aceptada 4: la justificación cubre todos los lineamientos incumplidos a la vez
    @Test
    fun `una sola justificacion cubre todos los lineamientos incumplidos`() {
        val all = input(kinds = listOf("MANO DE OBRA", "DINERO"), type = "DONACION", lineage = listOf(roads), justification = "Faena comunal acordada en asamblea.")
        assertEquals(
            listOf(Triple("PAID_LABOR", "contribution_item", false), Triple("MONETARY_DONATION", "contribution_item", false), Triple("PRIORITY_NOT_MET", "topic", false)),
            found(all)
        )
    }

    // Review Focus 4: una política apagada no se evalúa, ni siquiera como advertencia
    @Test
    fun `una politica apagada no se evalua`() {
        val off =
            mapOf(
                ContributionGuidelines.NO_PAID_LABOR to false,
                ContributionGuidelines.NON_MONETARY to false,
                ContributionGuidelines.PRIORITY to false
            )
        assertEquals(
            emptyList<Triple<String, String?, Boolean>>(),
            found(input(kinds = listOf("MANO DE OBRA", "DINERO"), type = "DONACION", priorities = emptySet(), lineage = emptyList(), project = off))
        )
    }

    @Test
    fun `los valores que nombra la regla existen en el modelo`() {
        assertTrue(ContributionGuidelines.LABOR in catalog.enumOf(Contributions.ITEM, Contributions.ITEM_KIND))
        assertTrue(ContributionGuidelines.MONEY in catalog.enumOf(Contributions.ITEM, Contributions.ITEM_KIND))
        assertTrue(ContributionGuidelines.DONATION in catalog.enumOf(Contributions.CONTRIBUTION, Contributions.TYPE))
        assertTrue(catalog.manyToMany(Contributions.PRIORITY_TOPICS)?.let { it.source == "project" && it.target == Contributions.TOPIC } == true)
    }
}
```

Agregar al final de `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt`, antes de la última llave:

```kotlin

    // ---- Tarea 5: P2 y P3 ----

    private fun topic(parent: String? = null): String =
        createRecord("topic", mapOf("code" to uniqueName("TEM-"), "name" to "Tema de prueba") + listOfNotNull(parent?.let { "parent_topic" to it }))

    // un proyecto con `topic` priorizado (project_priority_topic, RF-INV-002)
    private fun prioritizing(topic: String): String {
        val project = project()
        val linked = linkWithReason("project", project, "project_priority_topic", topic)
        assertEquals(HttpStatus.NO_CONTENT, linked.status, linked.body)
        return project
    }

    @Test
    fun `mano de obra y dinero en una donacion bloquean sin justificacion y pasan con ella`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val labor = requestedContribution(project, items = listOf(200 to "MANO DE OBRA"))
        val refused = transition("social_contribution", labor, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PAID_LABOR", code(refused))
        assertEquals(listOf("contribution_item"), problemFields(refused.body))
        val money = requestedContribution(project, items = listOf(500 to "DINERO"), attributes = mapOf("contribution_type" to "DONACION"))
        assertEquals("MONETARY_DONATION", code(transition("social_contribution", money, "approve", approver.token, "aprobación")))
        val justified = requestedContribution(project, items = listOf(200 to "MANO DE OBRA"), attributes = mapOf("guideline_deviation_justification" to "Faena comunal."))
        val warnings = dryRun(justified, "approve", approver)["warnings"].values().map { it["code"].asString() }
        assertTrue("PAID_LABOR" in warnings, warnings.toString())
        move("social_contribution", justified, "approve", approver.token, "aprobación")
    }

    @Test
    fun `un tema fuera de las prioridades es PRIORITY_NOT_MET y uno que desciende de una prioridad pasa`() {
        val education = topic()
        val project = prioritizing(education)
        val approver = member("APPROVER", project)
        val outside = requestedContribution(project, attributes = mapOf("topic" to topic()))
        val refused = transition("social_contribution", outside, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("PRIORITY_NOT_MET", code(refused))
        assertEquals(listOf("topic"), problemFields(refused.body))
        val inside = requestedContribution(project, attributes = mapOf("topic" to topic(parent = topic(parent = education))))
        move("social_contribution", inside, "approve", approver.token, "aprobación")
    }

    @Test
    fun `un proyecto sin temas priorizados solo advierte PRIORITIES_NOT_CONFIGURED`() {
        val id = requestedContribution()
        val approver = member("APPROVER")
        val check = dryRun(id, "approve", approver)
        assertTrue(check["warnings"].values().any { it["code"].asString() == "PRIORITIES_NOT_CONFIGURED" }, check.toString())
        assertTrue(check["allowed"].asBoolean(), check.toString())
        move("social_contribution", id, "approve", approver.token, "aprobación")
    }
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ContributionGuidelinesTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ContributionGuidelines'`).

- [ ] **Paso 3: La pieza pura y la regla**

Crear `src/main/kotlin/sgspe/investment/ContributionGuidelines.kt`:

```kotlin
package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.commitment.projectOf
import sgspe.common.Finding
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.Values
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordWrite
import java.util.UUID

// los lineamientos de apoyo de un aporte, puros (decisión 3 y §3.4 de la especificación de F5): por cada política activa del
// proyecto, un hallazgo si el aporte se aparta. bloquea sin guideline_deviation_justification y es advertencia con ella: la
// justificación cubre todos los incumplidos a la vez (RN-INV-006). sin temas priorizados, la prioridad no se evalúa y solo se
// advierte. «caseríos sin beneficio laboral» no se evalúa: no hay dato (GAP-033)
object ContributionGuidelines {
    const val NO_PAID_LABOR = "policy_no_paid_labor"
    const val NON_MONETARY = "policy_non_monetary_donations"
    const val PRIORITY = "policy_priority_warning"
    const val LABOR = "MANO DE OBRA"
    const val MONEY = "DINERO"
    const val DONATION = "DONACION"

    // los niveles de parent_topic que se suben desde el tema del aporte (elección 8) y los temas priorizados que se leen
    const val MAX_TOPIC_DEPTH = 10
    const val MAX_PRIORITIES = 500

    // `lineage`: el tema del aporte y sus ancestros (vacío sin tema); `itemKinds`: item_kind de cada ítem
    data class Input(
        val project: Map<String, Any?>,
        val priorities: Set<UUID>,
        val lineage: List<UUID>,
        val itemKinds: List<String?>,
        val contributionType: String?,
        val justification: String?
    )

    fun evaluate(input: Input): List<Finding> {
        val justified = Values.text(input.justification) != null
        val findings = mutableListOf<Finding>()

        fun deviation(
            code: String,
            message: String,
            field: String
        ) {
            val tail = if (justified) "se aparta con la justificación registrada (RN-INV-006)" else "informe guideline_deviation_justification para aprobarlo (RN-INV-006)"
            findings += Finding(code, "$message: $tail", field, blocking = !justified)
        }
        if (ProjectParameters.policy(input.project, NO_PAID_LABOR) && LABOR in input.itemKinds) {
            deviation("PAID_LABOR", "Un ítem es mano de obra y el proyecto no financia mano de obra (RN-INV-001)", Contributions.ITEM)
        }
        if (ProjectParameters.policy(input.project, NON_MONETARY) && input.contributionType == DONATION && MONEY in input.itemKinds) {
            deviation("MONETARY_DONATION", "Una donación lleva un ítem de dinero y el proyecto pide donaciones no monetarias (RN-INV-002)", Contributions.ITEM)
        }
        if (ProjectParameters.policy(input.project, PRIORITY)) {
            if (input.priorities.isEmpty()) {
                findings +=
                    Finding(
                        "PRIORITIES_NOT_CONFIGURED",
                        "El proyecto no tiene temas priorizados para aportes: la prioridad del aporte no se evalúa (RN-INV-005)",
                        Contributions.TOPIC_FIELD,
                        blocking = false
                    )
            } else if (input.lineage.none { it in input.priorities }) {
                deviation("PRIORITY_NOT_MET", "El tema del aporte no es uno de los priorizados del proyecto ni desciende de uno (RN-INV-005)", Contributions.TOPIC_FIELD)
            }
        }
        return findings
    }
}

// P2 y P3 (§3.3): los lineamientos al aprobar, con los ítems leídos en la transacción (Records.itemsOf), los temas priorizados
// del proyecto (project_priority_topic) y la ascendencia del tema del aporte, todo como quien aprueba. un tema o una lista que
// no se lee es NotReadable, nunca un pase
@Component
class ContributionGuidelinesRule(
    private val records: Records
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 5

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val before = change.before.orEmpty()
        val project = records.projectOf(before)
        val projectId = Records.uuid(before["project"])
        val priorities =
            if (projectId != null && ProjectParameters.policy(project, ContributionGuidelines.PRIORITY)) priorities(projectId) else emptySet()
        val lineage = if (priorities.isEmpty()) emptyList() else lineage(Records.uuid(before[Contributions.TOPIC_FIELD]), priorities)
        return ContributionGuidelines.evaluate(
            ContributionGuidelines.Input(
                project,
                priorities,
                lineage,
                records.itemsOf(id).map { Values.text(it.attributes[Contributions.ITEM_KIND]) },
                Values.text(before[Contributions.TYPE]),
                Values.text(before[Contributions.JUSTIFICATION])
            )
        )
    }

    private suspend fun priorities(project: UUID): Set<UUID> =
        readAs(Contributions.TOPIC) {
            records.related(
                "project",
                project,
                Contributions.PRIORITY_TOPICS,
                max = ContributionGuidelines.MAX_PRIORITIES,
                field = Contributions.PRIORITY_TOPICS
            )
        }.map { UUID.fromString(it.id) }.toSet()

    // el tema y sus ancestros por parent_topic, hasta MAX_TOPIC_DEPTH niveles, sin ciclos y hasta dar con una prioridad
    private suspend fun lineage(
        topic: UUID?,
        priorities: Set<UUID>
    ): List<UUID> {
        val chain = mutableListOf<UUID>()
        var current = topic
        while (current != null && current !in chain && chain.size <= ContributionGuidelines.MAX_TOPIC_DEPTH) {
            chain += current
            if (current in priorities) break
            current = Records.uuid(records.readable(Contributions.TOPIC, current).attributes[Contributions.PARENT_TOPIC])
        }
        return chain
    }
}
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ContributionGuidelinesTest` con 7 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ContributionGuidelines.kt src/test/kotlin/sgspe/investment/ContributionGuidelinesTest.kt \
  src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: lineamientos de apoyo al aprobar un aporte

ContributionGuidelines evalúa mano de obra, dinero en una donación y el tema fuera de las
prioridades del proyecto (o de sus descendientes) con las políticas activas: bloquea sin
justificación y advierte con ella; sin temas priorizados solo advierte (P2, P3, RN-INV-006).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `SocialContributionWorkflowApiTest` (casos de las Tareas 1, 4 y 5).

---

### Tarea 6: Lo comprometido de una campaña y el tope compartido

**Files:**
- Create: `src/main/kotlin/sgspe/investment/CampaignCap.kt`
- Modify: `src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt`
- Test: `src/test/kotlin/sgspe/investment/CampaignCommittedTest.kt` (crear)
- Test: `src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt`, `SupportCapTest.kt`
- Test: `src/test/kotlin/sgspe/investment/CampaignCapApiTest.kt` (crear, integración)

**Interfaces:**
- Consumes: `Contributions` (Tarea 1); `total_amount` escrito al aprobar (`ContributionTotalsListener`, Tarea 8);
  `SupportCap` (F3); `Commitments.COMMITTED`, `Criteria.inState`, `TenantModel.has`, `Amounts`, `readAs`,
  `NotReadable`, `Callers`, `Permissions`, `ProjectParameters`.
- Produces:
  - `@Component class CampaignCommitted(records: Records, tenant: TenantModel)` con `data class Committed(approved:
    List<SupportCap.Approved>, invalid: Int)`, `data class Parsed(approved: List<SupportCap.Approved>, invalid:
    List<String>)`, `suspend fun of(organizationId: UUID, campaign: UUID, excluding: UUID?): Committed` y, en su companion,
    `CAMPAIGN`, `CONTRIBUTION_STATES` (= `Contributions.COMMITTED`), `MAX_PER_OBJECT = 20_000` y `fun parse(rows:
    List<RecordResponse>, field: String, excluding: UUID?): Parsed`. El plan 5.2b usa `CONTRIBUTION_STATES` en KPI-INV-01.
  - `@Component class CampaignCap(records, callers, permissions, committed)` con `suspend fun findings(change: RecordWrite,
    campaign: UUID, candidate: SupportCap.Approved): List<Finding>` (quien llama ya tomó el candado o corre en seco) y, en
    su companion, `CAMPAIGN`, `CURRENCY`, `OVER_CAP`, `POLICY` y `fun lock(campaign: UUID): String`.
  - `SupportPolicyGuard(cap: CampaignCap, transactions: Transactions)` (antes recibía `records`, `callers` y
    `permissions`).

Elección 6 y §3.4: lo comprometido de una campaña son los compromisos en `Commitments.COMMITTED` (`amount`) y, si el tenant
tiene `social_contribution`, los aportes en `Contributions.COMMITTED` (`total_amount`), en la moneda de cada uno, sin el
registro que se aprueba; un monto guardado inválido se omite y cuenta para `CAP_DATA_INVALID`; el tope de lecturas rige por
objeto. `CampaignCap` es la lógica común de D7 y P1 (decisión 14): la lectura del tope, los hallazgos, la política del
proyecto y `APPROVE_OVER_CAP` sobre el objeto que se aprueba (`commitment` o `social_contribution`, ASM-119).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/investment/CampaignCommittedTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import sgspe.common.TenantModel
import wasichai.core.common.NotFoundException
import wasichai.core.data.RecordResponse
import wasichai.core.metadata.MetadataService
import wasichai.core.metadata.ObjectDefinition
import java.math.BigDecimal
import java.util.UUID

// lo comprometido de una campaña (elección 6 de la especificación de F5): compromisos y, con el modelo de F5, aportes; sin el
// registro que se aprueba; un monto inválido se omite y se cuenta
class CampaignCommittedTest {
    private val campaign = UUID.randomUUID()
    private val approving = UUID.randomUUID()
    private val reads = mutableListOf<String>()

    private fun row(
        id: UUID,
        vararg attributes: Pair<String, Any?>
    ) = RecordResponse(id = id.toString(), createdAt = null, updatedAt = null, attributes = mapOf("currency" to "PEN") + attributes)

    private val rows =
        mapOf(
            "commitment" to
                listOf(
                    row(UUID.randomUUID(), "amount" to 400),
                    row(UUID.randomUUID(), "amount" to "no es un monto"),
                    row(approving, "amount" to 9000)
                ),
            "social_contribution" to
                listOf(
                    row(UUID.randomUUID(), "total_amount" to 300),
                    row(UUID.randomUUID(), "total_amount" to 100, "currency" to "USD")
                )
        )

    private val records: Records =
        mock(Records::class.java) { call ->
            if (call.method.name != "all") error("lectura inesperada: ${call.method.name}")
            val name = call.arguments[0] as String
            reads += name
            rows[name].orEmpty()
        }

    private fun tenant(withF5: Boolean): TenantModel =
        TenantModel(
            mock(MetadataService::class.java) { call ->
                if (call.method.name != "loadDefinition") return@mock null
                if (!withF5 && call.arguments[1] == "social_contribution") throw NotFoundException("sin el modelo de F5")
                mock(ObjectDefinition::class.java)
            }
        )

    @Test
    fun `con el modelo de F5 suma compromisos y aportes comprometidos sin el que se aprueba`() {
        val committed = runBlocking { CampaignCommitted(records, tenant(withF5 = true)).of(UUID.randomUUID(), campaign, approving) }
        assertEquals(setOf("approved", "procured", "delivered", "closed"), CampaignCommitted.CONTRIBUTION_STATES)
        assertEquals(listOf("commitment", "social_contribution"), reads)
        assertEquals(listOf("400" to "PEN", "300" to "PEN", "100" to "USD"), committed.approved.map { it.amount!!.toPlainString() to it.currency })
        assertEquals(1, committed.invalid)
    }

    // Review Focus 5: un tenant que todavía no tiene el modelo de F5 sigue aprobando compromisos
    @Test
    fun `sin el modelo de F5 solo lee compromisos`() {
        val committed = runBlocking { CampaignCommitted(records, tenant(withF5 = false)).of(UUID.randomUUID(), campaign, null) }
        assertEquals(listOf("commitment"), reads)
        assertEquals(2, committed.approved.size)
        assertEquals(0, BigDecimal("9400").compareTo(committed.approved.mapNotNull { it.amount }.fold(BigDecimal.ZERO, BigDecimal::add)))
    }
}
```

En `src/test/kotlin/sgspe/investment/SupportCapTest.kt`, agregar antes de la última llave:

```kotlin

    // elección 6 de F5: el tope no distingue de qué objeto viene un monto comprometido; compromisos y aportes suman igual
    @Test
    fun `un aporte y un compromiso comprometidos suman igual contra el tope`() {
        val committed = listOf(approved("400"), approved("300"))
        assertTrue(SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("400"), committed).exceeded)
        assertFalse(SupportCap.evaluate(BigDecimal("1000"), "PEN", approved("300"), committed).exceeded)
    }
```

En `src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt`, reemplazar:

```kotlin
    private val guard =
        SupportPolicyGuard(
            Records(
                provider(service),
                provider<RelatedRecordService>(null),
                provider<WorkflowService>(null),
                mock(MetadataService::class.java),
                mock(Callers::class.java),
                mock(ModelCatalog::class.java),
                mock(DerivedVersions::class.java)
            ),
            mock(Callers::class.java),
            mock(Permissions::class.java),
            mock(Transactions::class.java)
        )
```

por:

```kotlin
    private val records =
        Records(
            provider(service),
            provider<RelatedRecordService>(null),
            provider<WorkflowService>(null),
            mock(MetadataService::class.java),
            mock(Callers::class.java),
            mock(ModelCatalog::class.java),
            mock(DerivedVersions::class.java)
        )

    // un tenant sin el modelo de F5: lo comprometido son solo los compromisos de `committed`
    private val tenant = TenantModel(mock(MetadataService::class.java) { throw NotFoundException("sin el modelo de F5") })

    private val guard =
        SupportPolicyGuard(
            CampaignCap(records, mock(Callers::class.java), mock(Permissions::class.java), CampaignCommitted(records, tenant)),
            mock(Transactions::class.java)
        )
```

y agregar el import `sgspe.common.TenantModel`.

Crear `src/test/kotlin/sgspe/investment/CampaignCapApiTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest

// SupportPolicyGuard (D7 de WF-D) con lo comprometido de F5: un compromiso que se aprueba cuenta también los aportes
// comprometidos de su campaña, con el mismo candado (elección 6)
class CampaignCapApiTest : ContributionApiTest() {
    @Test
    fun `un compromiso que se aprueba cuenta los aportes comprometidos de la campana`() {
        val project = testProject()
        val campaign = cappedCampaign(project, 1000)
        approvedWithItems(project, items = listOf(700 to "BIEN"), attributes = mapOf("exploration_campaign" to campaign))
        val approver = member("APPROVER", project)
        val over = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 400))
        move("commitment", over, "submit", reason = "envío")
        val refused = transition("commitment", over, "approve", approver.token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refused))
        val fits = commitment(project, attributes = mapOf("exploration_campaign" to campaign, "amount" to 300))
        move("commitment", fits, "submit", reason = "envío")
        assertEquals(HttpStatus.OK, transition("commitment", fits, "approve", approver.token, "aprobación").status)
    }
}
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.*'
```

Esperado: FAIL en la compilación (`Unresolved reference 'CampaignCommitted'`, `'CampaignCap'`).

- [ ] **Paso 3: Las piezas compartidas y la guarda de compromisos**

Crear `src/main/kotlin/sgspe/investment/CampaignCap.kt`:

```kotlin
package sgspe.investment

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import sgspe.commitment.Commitments
import sgspe.common.Amounts
import sgspe.common.Callers
import sgspe.common.Criteria
import sgspe.common.Finding
import sgspe.common.NotReadable
import sgspe.common.Permissions
import sgspe.common.ProjectParameters
import sgspe.common.Records
import sgspe.common.TenantModel
import sgspe.common.Values
import sgspe.common.readAs
import wasichai.core.common.WasichaiException
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordWrite
import java.util.UUID

// lo comprometido de una campaña (especificación de F5 §3.4, elección 6; dashboards.md §3.2): los compromisos en
// approved, in_progress, blocked, fulfilled, verified o closed con su amount y, si el tenant tiene el modelo de F5
// (TenantModel), los aportes en approved, procured, delivered o closed con su total_amount, cada uno en su moneda. sin el
// registro que se aprueba (cuenta como candidato). un monto guardado que Amounts rechaza se omite, se registra con su id y
// se cuenta (CAP_DATA_INVALID). lee como quien aprueba, dentro del candado de la campaña; la lectura es acotada por objeto
// (más de MAX_PER_OBJECT es un 400 TOO_MANY_RECORDS, nunca una suma recortada)
@Component
class CampaignCommitted(
    private val records: Records,
    private val tenant: TenantModel
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Committed(
        val approved: List<SupportCap.Approved>,
        val invalid: Int
    )

    data class Parsed(
        val approved: List<SupportCap.Approved>,
        val invalid: List<String>
    )

    suspend fun of(
        organizationId: UUID,
        campaign: UUID,
        excluding: UUID?
    ): Committed {
        val commitments = parse(read(Commitments.COMMITMENT, Commitments.COMMITTED, campaign), "amount", excluding)
        val contributions =
            if (tenant.has(organizationId, Contributions.CONTRIBUTION)) {
                parse(read(Contributions.CONTRIBUTION, Contributions.COMMITTED, campaign), Contributions.TOTAL, excluding)
            } else {
                Parsed(emptyList(), emptyList())
            }
        for (id in commitments.invalid + contributions.invalid) {
            log.warn("CampaignCommitted: el monto comprometido de {} en la campaña {} no es válido y no se suma al tope", id, campaign)
        }
        return Committed(commitments.approved + contributions.approved, commitments.invalid.size + contributions.invalid.size)
    }

    private suspend fun read(
        objectName: String,
        states: Set<String>,
        campaign: UUID
    ): List<RecordResponse> =
        readAs(objectName) {
            records.all(
                objectName,
                filters = mapOf(CAMPAIGN to campaign.toString()),
                criteria = listOf(Criteria.inState(states)),
                max = MAX_PER_OBJECT
            )
        }

    companion object {
        const val CAMPAIGN = "exploration_campaign"

        // el conjunto «comprometido» de los aportes (dashboards.md §3.2): el mismo que Contributions.COMMITTED, con el nombre
        // que usa KPI-INV-01 (plan 5.2b)
        val CONTRIBUTION_STATES: Set<String> = Contributions.COMMITTED

        // los registros comprometidos de cada objeto que se leen al aprobar
        const val MAX_PER_OBJECT = 20_000

        // pura: el monto `field` y la moneda de cada fila, sin `excluding`; las filas cuyo monto Amounts rechaza, aparte
        fun parse(
            rows: List<RecordResponse>,
            field: String,
            excluding: UUID?
        ): Parsed {
            val approved = mutableListOf<SupportCap.Approved>()
            val invalid = mutableListOf<String>()
            for (row in rows) {
                if (row.id == excluding?.toString()) continue
                try {
                    approved += SupportCap.Approved(Amounts.parse(row.attributes[field], field), Values.text(row.attributes["currency"]))
                } catch (_: WasichaiException) {
                    invalid += row.id
                }
            }
            return Parsed(approved, invalid)
        }
    }
}

// el tope de apoyo social de una campaña al aprobar (RN-INV-003, decisión 14 del plan 5.2a): la lógica común de D7 de WF-D
// (SupportPolicyGuard) y P1 de WF-P (ContributionCapRule). lee la campaña y lo comprometido como quien aprueba; una campaña,
// un proyecto o unos registros que no lee son NotReadable, nunca un pase. una campaña sin tope no se juzga; una sin moneda
// nunca bloquea (CAP_OTHER_CURRENCY, P45). superar el tope, o un dato guardado inválido (CAP_DATA_INVALID), bloquea con
// policy_cap_blocks_approval del proyecto (por defecto, sí) salvo para quien tiene APPROVE_OVER_CAP sobre el objeto que se
// aprueba (commitment o social_contribution: la autoridad difiere, ASM-119); si no, advierte. quien llama ya tomó el candado
// de la campaña (CampaignCap.lock) o corre en seco
@Component
class CampaignCap(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val committed: CampaignCommitted
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun findings(
        change: RecordWrite,
        campaign: UUID,
        candidate: SupportCap.Approved
    ): List<Finding> {
        val record = readAs(CAMPAIGN) { records.find(CAMPAIGN, campaign) } ?: throw NotReadable(CAMPAIGN)
        val cap =
            try {
                Amounts.parse(record.attributes["social_support_cap"], "social_support_cap") ?: return emptyList()
            } catch (e: WasichaiException) {
                log.warn("CampaignCap: el tope de la campaña {} no es un monto válido: {}", campaign, e.message)
                return listOf(invalid(change, "El tope de la campaña no es un monto válido: corrija los datos de la campaña"))
            }
        val currency =
            Values.text(record.attributes[CURRENCY])
                ?: return listOf(Finding("CAP_OTHER_CURRENCY", "La campaña no tiene moneda: el tope no se compara (ASM-063)", CURRENCY, blocking = false))
        val read = committed.of(change.organizationId, campaign, change.recordId)
        val outcome = SupportCap.evaluate(cap, currency, candidate, read.approved)
        val findings = mutableListOf<Finding>()
        if (read.invalid > 0) {
            findings += invalid(change, "${read.invalid} registros comprometidos de la campaña tienen un monto guardado no válido y no se suman al tope")
        }
        if (outcome.otherCurrency > 0 || outcome.incomparable) {
            findings +=
                Finding(
                    "CAP_OTHER_CURRENCY",
                    "Hay montos en una moneda distinta de la de la campaña ($currency): no se suman al tope (ASM-063)",
                    CURRENCY,
                    blocking = false
                )
        }
        if (outcome.exceeded) {
            findings +=
                Finding(
                    "SUPPORT_CAP_EXCEEDED",
                    "La aprobación supera el tope de apoyo social de la campaña: $cap $currency, con ${outcome.total} comprometidos (RN-INV-003)",
                    CAMPAIGN,
                    blocking = blocks(change)
                )
        }
        return findings
    }

    // el error de datos: bloquea como el tope; si no, advierte
    private suspend fun invalid(
        change: RecordWrite,
        message: String
    ): Finding = Finding("CAP_DATA_INVALID", message, CAMPAIGN, blocking = blocks(change))

    private suspend fun blocks(change: RecordWrite): Boolean = policy(change.before.orEmpty()) && !overCap(change.objectName)

    private suspend fun overCap(objectName: String): Boolean {
        val user = callers.userOrNull()
        return user != null && permissions.has(user, OVER_CAP, objectName)
    }

    // la política del proyecto del registro (ASM-019); un proyecto que no se lee es NotReadable, nunca la política por defecto
    private suspend fun policy(before: Map<String, Any?>): Boolean {
        val id = Records.uuid(before["project"]) ?: return ProjectParameters.policy(emptyMap(), POLICY)
        val project = readAs("project") { records.find("project", id) }?.attributes ?: throw NotReadable("project")
        return ProjectParameters.policy(project, POLICY)
    }

    companion object {
        const val CAMPAIGN = "exploration_campaign"
        const val CURRENCY = "currency"
        const val OVER_CAP = "APPROVE_OVER_CAP"
        const val POLICY = "policy_cap_blocks_approval"

        // el candado de la campaña: dos aprobaciones de la misma campaña (compromisos o aportes) no pasan el tope a la vez
        fun lock(campaign: UUID): String = "$CAMPAIGN:$campaign"
    }
}
```

En `src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt`, `SupportCap` no cambia; todo lo que sigue a su cierre (el
comentario de `SupportPolicyGuard` y la clase entera) queda así, y los imports del archivo se reducen a los que usa:

```kotlin
// SupportPolicyGuard, D7 de WF-D (RF-INV-002): al aprobar un compromiso con campaña y tope, CampaignCap juzga el tope contra
// lo comprometido de la campaña (CampaignCommitted: compromisos y, desde F5, aportes; elección 6 de la especificación de F5),
// con APPROVE_OVER_CAP sobre commitment. la escritura toma el candado de la campaña (CampaignCap.lock) dentro de la
// transacción de la transición, así dos aprobaciones de la misma campaña, también la de un aporte, no pasan el tope a la vez;
// la consulta en seco no lo toma. va al final de las reglas de aprobación (P49) porque es la única que toma un candado
@Component
class SupportPolicyGuard(
    private val cap: CampaignCap,
    private val transactions: Transactions
) : CommitmentRule {
    override val order = CommitmentRuleOrder.APPROVAL + 5

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Commitments.COMMITMENT || change.kind != RecordChangeKind.TRANSITIONED) return emptyList()
        if (change.transition !in Commitments.APPROVALS) return emptyList()
        val before = change.before.orEmpty()
        val campaign = Records.uuid(before[CampaignCap.CAMPAIGN]) ?: return emptyList()
        val candidate = SupportCap.Approved(Amounts.parse(before["amount"], "amount"), Values.text(before["currency"]))
        return if (dryRun) {
            cap.findings(change, campaign, candidate)
        } else {
            transactions.locked(CampaignCap.lock(campaign)) { cap.findings(change, campaign, candidate) }
        }
    }
}
```

Los imports de `SupportPolicyGuard.kt` quedan: `org.springframework.stereotype.Component`,
`sgspe.commitment.CommitmentRule`, `sgspe.commitment.CommitmentRuleOrder`, `sgspe.commitment.Commitments`,
`sgspe.common.Amounts`, `sgspe.common.Finding`, `sgspe.common.Records`, `sgspe.common.Transactions`,
`sgspe.common.Values`, `wasichai.core.data.RecordChangeKind`, `wasichai.core.data.RecordWrite`, `java.math.BigDecimal`.

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `CampaignCommittedTest` (2), `SupportCapTest` (4) y `SupportPolicyGuardTest` (4) en verde,
sin cambiar las afirmaciones de esta última.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/CampaignCap.kt src/main/kotlin/sgspe/investment/SupportPolicyGuard.kt \
  src/test/kotlin/sgspe/investment/CampaignCommittedTest.kt src/test/kotlin/sgspe/investment/SupportCapTest.kt \
  src/test/kotlin/sgspe/investment/SupportPolicyGuardTest.kt src/test/kotlin/sgspe/investment/CampaignCapApiTest.kt
git commit -F - <<'MSG'
feat: el tope de la campaña suma compromisos y aportes comprometidos

CampaignCommitted lee lo comprometido de una campaña (compromisos y, con el modelo de F5, aportes) y
CampaignCap junta la lógica del tope que comparten la aprobación de compromisos y la de aportes; un
tenant sin F5 solo lee compromisos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `CampaignCapApiTest` y `SupportPolicyApiTest` (sin cambios: vigila que D7 siga
igual).

---

### Tarea 7: El tope de la campaña al aprobar un aporte (P1)

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ContributionCapRule.kt`
- Test: `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt` (casos al final, integración)

**Interfaces:**
- Consumes: `Contributions`, `Records.itemsOf`, `ContributionData.total`, `InvestmentRule`, `InvestmentRuleOrder`
  (Tarea 1); `ContributionApproval.target` (Tarea 4); `CampaignCap.findings` y `CampaignCap.lock` (Tarea 6); `SupportCap`;
  `total_amount` escrito al aprobar (Tarea 8, para el aporte ya aprobado que cuenta).
- Produces: el bean `ContributionCapRule(records, cap, transactions)` (`InvestmentRuleOrder.APPROVAL + 6`, la última de
  la aprobación).

§3.4: candidato = suma de `line_amount` de los ítems leídos en la transacción (nunca el `total_amount` guardado, Review
Focus 1), en la moneda del aporte; `APPROVE_OVER_CAP` sobre `social_contribution`; un aporte sin campaña o una campaña sin
tope no se juzgan. Toma el candado de la campaña después del del aporte (ya tomado por `ContributionLockRule`).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Agregar al final de `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt`, antes de la última llave:

```kotlin

    // ---- Tarea 7: P1 ----

    @Test
    fun `con un tope que bloquea approve por APPROVER se rechaza, por SOCIAL_MANAGER pasa y approve_direct se rechaza`() {
        val project = project(mapOf("policy_internal_approval" to false))
        val over = mapOf("exploration_campaign" to cappedCampaign(project, 500))
        val id = requestedContribution(project, items = listOf(400 to "BIEN", 200 to "SERVICIO"), attributes = over)
        val refused = transition("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refused))
        assertEquals(listOf("exploration_campaign"), problemFields(refused.body))
        assertEquals(HttpStatus.OK, transition("social_contribution", id, "approve", member("SOCIAL_MANAGER", project).token, "sobre el tope").status)
        val direct = requestedContribution(project, items = listOf(100 to "BIEN"), attributes = over)
        val refusedDirect = transition("social_contribution", direct, "approve_direct", registrar(project).token, "aprobación directa")
        assertEquals(HttpStatus.CONFLICT, refusedDirect.status, refusedDirect.body)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(refusedDirect))
    }

    @Test
    fun `el tope cuenta un compromiso comprometido y otro aporte aprobado de la misma campana`() {
        val project = testProject()
        val campaign = mapOf("exploration_campaign" to cappedCampaign(project, 1000))
        approvedCommitment(project, attributes = campaign + ("amount" to 400), start = false)
        approvedWithItems(project, items = listOf(300 to "BIEN"), attributes = campaign)
        val approver = member("APPROVER", project)
        val over = requestedContribution(project, items = listOf(400 to "BIEN"), attributes = campaign)
        assertEquals("SUPPORT_CAP_EXCEEDED", code(transition("social_contribution", over, "approve", approver.token, "aprobación")))
        val fits = requestedContribution(project, items = listOf(300 to "BIEN"), attributes = campaign)
        move("social_contribution", fits, "approve", approver.token, "aprobación")
    }

    // Review Focus 1: el monto que se juzga es el de los ítems, aunque el total guardado diga otra cosa
    @Test
    fun `el tope suma los items leidos en la transaccion, no el total guardado`() {
        val project = testProject()
        val id = requestedContribution(project, items = listOf(600 to "BIEN"), attributes = mapOf("exploration_campaign" to cappedCampaign(project, 500)))
        platformPatch("social_contribution", id, mapOf("total_amount" to 0))
        assertEquals("SUPPORT_CAP_EXCEEDED", code(transition("social_contribution", id, "approve", member("APPROVER", project).token, "aprobación")))
    }

    @Test
    fun `con la politica apagada el tope superado es una advertencia y lo aprobado queda escrito`() {
        val project = project(mapOf("policy_cap_blocks_approval" to false))
        val id = requestedContribution(project, items = listOf(600 to "BIEN"), attributes = mapOf("exploration_campaign" to cappedCampaign(project, 500)))
        val approver = member("APPROVER", project)
        val check = dryRun(id, "approve", approver)
        assertTrue(check["warnings"].values().any { it["code"].asString() == "SUPPORT_CAP_EXCEEDED" }, check.toString())
        move("social_contribution", id, "approve", approver.token, "aprobación")
        assertEquals(0, java.math.BigDecimal("600").compareTo(record("social_contribution", id)["attributes"]["total_amount"].decimalValue()))
    }
```

- [ ] **Paso 2: Compilar para ver que la regla falta**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Esperado: compila (las pruebas son de integración y la regla aún no existe); sin la regla, en la CI fallarían los cuatro
casos nuevos con `200` en lugar de `409`. Esta tarea no tiene unitaria propia: la lógica es `CampaignCap` (Tarea 6) y
`ContributionData.total` (Tarea 1), ya probadas.

- [ ] **Paso 3: La regla**

Crear `src/main/kotlin/sgspe/investment/ContributionCapRule.kt`:

```kotlin
package sgspe.investment

import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordWrite

// P1 de WF-P (especificación de F5 §3.3 y §3.4): al aprobar un aporte con campaña, CampaignCap juzga el tope contra lo
// comprometido de la campaña con el candidato = la suma de line_amount de los ítems leídos en la transacción, en la moneda
// del aporte (nunca el total_amount guardado), y APPROVE_OVER_CAP sobre social_contribution (SOCIAL_MANAGER, ASM-119). es la
// última regla de la aprobación y la única que toma el candado de la campaña, después del del aporte (ContributionLockRule):
// el orden de candados es siempre aporte y después campaña. la consulta en seco no lo toma
@Component
class ContributionCapRule(
    private val records: Records,
    private val cap: CampaignCap,
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.APPROVAL + 6

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, Contributions.APPROVALS) ?: return emptyList()
        val before = change.before.orEmpty()
        val campaign = Records.uuid(before[Contributions.CAMPAIGN]) ?: return emptyList()
        val candidate =
            SupportCap.Approved(ContributionData.total(records.itemsOf(id).map { it.attributes }), Values.text(before[Contributions.CURRENCY]))
        return if (dryRun) {
            cap.findings(change, campaign, candidate)
        } else {
            transactions.locked(CampaignCap.lock(campaign)) { cap.findings(change, campaign, candidate) }
        }
    }
}
```

- [ ] **Paso 4: Correr el build**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ContributionCapRule.kt src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: el tope de la campaña al aprobar un aporte

ContributionCapRule juzga la suma de los ítems leídos en la transacción contra lo comprometido de
la campaña, con el candado de la campaña tras el del aporte; superarlo con la política que bloquea
exige APPROVE_OVER_CAP sobre social_contribution (P1, ASM-119).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `SocialContributionWorkflowApiTest` (casos de las Tareas 1, 4, 5 y 7) y
`CampaignCapApiTest`.

---

### Tarea 8: El total del aporte y el ejecutado de la campaña con aportes

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ContributionTotals.kt`
- Modify: `src/main/kotlin/sgspe/investment/CampaignExecution.kt` (`CampaignExecutionRecalculator`)
- Test: `src/test/kotlin/sgspe/investment/ContributionTotalsListenerTest.kt` (crear)
- Test: `src/test/kotlin/sgspe/investment/ContributionTotalsApiTest.kt` (crear, integración)

**Interfaces:**
- Consumes: `Contributions`, `Records.itemsOf`, `ContributionData.total` (Tarea 1); `DerivedRecalculator`,
  `DerivedFieldsJob.reason`, `Criteria`, `TenantModel`, `Transactions.locked`, `Records.patch`, `Values.same`.
- Produces: el bean `ContributionTotalsListener(records)` (`@Order(125)`, `REASON = "derivado: total del aporte"`); el
  bean `ContributionTotalsRecalculator(records, transactions)` (`sourceObject = social_contribution`, `REASON`);
  `CampaignExecutionRecalculator(records, transactions, tenant)` con los aportes en `Contributions.EXECUTED` como segunda
  fuente.

Elección 12 y decisiones 1 y 2: el listener escribe `total_amount` con cada alta, cambio o baja de un ítem (si falla, lo
registra y JOB-02 lo corrige) y en `approve` y `approve_direct` (si falla, lanza y la aprobación se revierte: lo aprobado
queda escrito). JOB-02 recalcula solo los aportes en `requested`: después, el total es lo aprobado. El ejecutado de la
campaña suma, además de los compromisos, el `total_amount` de los aportes en `delivered` o `closed` en su moneda
(ASM-063), si el tenant tiene el modelo de F5.

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/investment/ContributionTotalsListenerTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import sgspe.common.Records
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordResponse
import java.math.BigDecimal
import java.util.UUID

// ContributionTotalsListener sin base: qué cambios recalculan el total y que solo escribe si cambia
class ContributionTotalsListenerTest {
    private val contribution = UUID.randomUUID()
    private val patches = mutableListOf<Map<*, *>>()
    private var stored: Map<String, Any?> = mapOf("total_amount" to 100)

    private fun response(attributes: Map<String, Any?>) = RecordResponse(id = contribution.toString(), createdAt = null, updatedAt = null, attributes = attributes)

    private val records: Records =
        mock(Records::class.java) { call ->
            when (call.method.name) {
                "find" -> response(stored)
                "all" -> listOf(response(mapOf("line_amount" to 300)), response(mapOf("line_amount" to "150.5")))
                "patch" -> {
                    patches += call.arguments[2] as Map<*, *>
                    response(stored)
                }
                else -> null
            }
        }

    private fun change(
        objectName: String,
        kind: RecordChangeKind,
        transition: String? = null
    ) = RecordChange(
        organizationId = UUID.randomUUID(),
        userId = UUID.randomUUID(),
        objectId = UUID.randomUUID(),
        objectName = objectName,
        recordId = if (objectName == Contributions.CONTRIBUTION) contribution else UUID.randomUUID(),
        kind = kind,
        before = if (objectName == Contributions.ITEM) mapOf("social_contribution" to contribution.toString()) else null,
        after = if (objectName == Contributions.ITEM && kind != RecordChangeKind.DELETED) mapOf("social_contribution" to contribution.toString()) else null,
        transition = transition
    )

    private fun run(change: RecordChange) = runBlocking { ContributionTotalsListener(records).recordChanged(change) }

    @Test
    fun `un item creado, cambiado o borrado y la aprobacion recalculan el total`() {
        for (kind in listOf(RecordChangeKind.CREATED, RecordChangeKind.UPDATED, RecordChangeKind.DELETED)) run(change(Contributions.ITEM, kind))
        run(change(Contributions.CONTRIBUTION, RecordChangeKind.TRANSITIONED, "approve"))
        assertEquals(4, patches.size)
        assertEquals(0, BigDecimal("450.5").compareTo(patches.first()[Contributions.TOTAL] as BigDecimal))
    }

    @Test
    fun `un total igual no se escribe y otras transiciones no recalculan`() {
        stored = mapOf("total_amount" to "450.50")
        run(change(Contributions.ITEM, RecordChangeKind.UPDATED))
        stored = mapOf("total_amount" to 1)
        run(change(Contributions.CONTRIBUTION, RecordChangeKind.TRANSITIONED, "register_procurement"))
        run(change(Contributions.CONTRIBUTION, RecordChangeKind.UPDATED))
        assertEquals(0, patches.size)
    }
}
```

Crear `src/test/kotlin/sgspe/investment/ContributionTotalsApiTest.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest
import sgspe.common.DerivedFieldsJob
import java.math.BigDecimal

// total_amount del aporte (elección 12): lo mantiene ContributionTotalsListener con cada ítem y en la aprobación, y JOB-02 lo
// corrige mientras el aporte está en requested
class ContributionTotalsApiTest : ContributionApiTest() {
    @Autowired
    private lateinit var job: DerivedFieldsJob

    private fun total(id: String): BigDecimal = record("social_contribution", id)["attributes"]["total_amount"].decimalValue()

    @Test
    fun `cada item mueve el total del aporte y la aprobacion lo deja escrito`() {
        val id = requestedContribution(items = listOf(300 to "BIEN", 150 to "SERVICIO"))
        assertEquals(0, BigDecimal("450").compareTo(total(id)))
        val item = tree(itemFor(id, "49.50").body)["id"].asString()
        assertEquals(0, BigDecimal("499.5").compareTo(total(id)))
        assertTrue(exchange("DELETE", "/api/objects/contribution_item/records/$item", null, token).status.is2xxSuccessful)
        assertEquals(0, BigDecimal("450").compareTo(total(id)))
        move("social_contribution", id, "approve", member("APPROVER").token, "aprobación")
        assertEquals(0, BigDecimal("450").compareTo(total(id)))
    }

    @Test
    fun `JOB-02 corrige el total de un aporte en requested con el motivo del trabajo`() {
        val id = requestedContribution(items = listOf(300 to "BIEN"))
        platformPatch("social_contribution", id, mapOf("total_amount" to 1))
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        assertEquals(0, BigDecimal("300").compareTo(total(id)))
        val history = tree(send("GET", "/api/objects/social_contribution/records/$id/history", null, HttpStatus.OK))
        assertEquals(ContributionTotalsRecalculator.REASON, history.values().first()["reason"].asString())
    }
}
```

- [ ] **Paso 2: Correr la unitaria para verla fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ContributionTotalsListenerTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ContributionTotalsListener'`, `'ContributionTotalsRecalculator'`).

- [ ] **Paso 3: El listener, el recalculador y la segunda fuente de la campaña**

Crear `src/main/kotlin/sgspe/investment/ContributionTotals.kt`:

```kotlin
package sgspe.investment

import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import sgspe.common.Criteria
import sgspe.common.DerivedFieldsJob
import sgspe.common.DerivedRecalculator
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import wasichai.core.data.RecordChange
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordChangeListener
import java.time.Instant
import java.util.UUID

// ContributionTotalsListener (elección 12 de la especificación de F5, domain-model §7.5): total_amount = la suma de line_amount
// de los ítems del aporte, escrito como quien escribe, con la marca y solo si cambia. con cada alta, cambio o baja de un ítem
// (el total solo informa mientras el aporte está en requested: si falla, se registra y JOB-02 lo corrige) y en approve y
// approve_direct, en la transacción de la transición: si falla, lanza y la aprobación se revierte, así lo aprobado queda
// escrito (decisión 2 del plan 5.2a)
@Component
@Order(125)
class ContributionTotalsListener(
    private val records: Records
) : RecordChangeListener {
    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun recordChanged(change: RecordChange) {
        if (change.objectName == Contributions.ITEM && change.kind in ITEM_CHANGES) {
            val contributions =
                listOfNotNull(change.before?.get(Contributions.CONTRIBUTION), change.after?.get(Contributions.CONTRIBUTION))
                    .mapNotNull { Records.uuid(it) }
                    .distinct()
            for (id in contributions) {
                try {
                    refresh(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.warn("ContributionTotalsListener: no se actualizó total_amount del aporte {}; JOB-02 lo corregirá", id, e)
                }
            }
            return
        }
        if (change.objectName == Contributions.CONTRIBUTION && change.kind == RecordChangeKind.TRANSITIONED && change.transition in Contributions.APPROVALS) {
            refresh(change.recordId)
        }
    }

    private suspend fun refresh(id: UUID) {
        val current = records.find(Contributions.CONTRIBUTION, id) ?: return
        val total = ContributionData.total(records.itemsOf(id).map { it.attributes })
        if (!Values.same(current.attributes[Contributions.TOTAL], total)) {
            records.patch(Contributions.CONTRIBUTION, id, mapOf(Contributions.TOTAL to total), REASON)
        }
    }

    companion object {
        const val REASON = "derivado: total del aporte"
        private val ITEM_CHANGES = setOf(RecordChangeKind.CREATED, RecordChangeKind.UPDATED, RecordChangeKind.DELETED)
    }
}

// el recalculador de JOB-02 para total_amount: los aportes en requested cambiados desde la vuelta anterior, o con un ítem
// cambiado, y todos los de requested en la vuelta diaria (la baja de un ítem solo la ve la diaria). fuera de requested el
// total es lo aprobado y no se toca (decisión 1 del plan 5.2a). cada aporte, dentro de su candado
@Component
class ContributionTotalsRecalculator(
    private val records: Records,
    private val transactions: Transactions
) : DerivedRecalculator {
    private val log = LoggerFactory.getLogger(javaClass)

    override val sourceObject: String = Contributions.CONTRIBUTION

    override suspend fun recalculate(
        organizationId: UUID,
        since: Instant?
    ): Int {
        val requested = Criteria.inState(setOf(Contributions.REQUESTED))
        val ids =
            if (since == null) {
                records.all(Contributions.CONTRIBUTION, criteria = listOf(requested)).map { it.id }
            } else {
                val changed = records.all(Contributions.CONTRIBUTION, criteria = listOf(requested, Criteria.updatedSince(since))).map { it.id }
                val viaItems =
                    records
                        .all(Contributions.ITEM, criteria = listOf(Criteria.updatedSince(since)))
                        .mapNotNull { Records.uuid(it.attributes[Contributions.CONTRIBUTION]) }
                        .distinct()
                val stillRequested = records.all(Contributions.CONTRIBUTION, criteria = listOf(requested), ids = viaItems).map { it.id }
                (changed + stillRequested).distinct()
            }
        var written = 0
        var failed = 0
        for (raw in ids) {
            try {
                val id = UUID.fromString(raw)
                val changed =
                    transactions.locked(Contributions.lock(id)) {
                        val current = records.find(Contributions.CONTRIBUTION, id) ?: return@locked false
                        val total = ContributionData.total(records.itemsOf(id).map { it.attributes })
                        if (Values.same(current.attributes[Contributions.TOTAL], total)) {
                            false
                        } else {
                            records.patch(Contributions.CONTRIBUTION, id, mapOf(Contributions.TOTAL to total), REASON)
                            true
                        }
                    }
                if (changed) written++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                log.warn("ContributionTotalsRecalculator: no se recalculó el aporte {}", raw, e)
            }
        }
        check(failed == 0) { "no se recalcularon $failed de ${ids.size} aportes (se escribieron $written)" }
        return written
    }

    companion object {
        val REASON = DerivedFieldsJob.reason("total del aporte")
    }
}
```

En `src/main/kotlin/sgspe/investment/CampaignExecution.kt`:

1. En el comentario de `CampaignExecution`, reemplazar «desde F5 se suman los aportes en delivered o closed» por «desde F5
   se suman también los aportes en delivered o closed, con su total_amount (CampaignExecutionRecalculator)», y renombrar el
   parámetro `commitments` de `of` a `executed` (también en su cuerpo).
2. La clase `CampaignExecutionRecalculator` recibe el `TenantModel` y lee la segunda fuente. Reemplazar:

```kotlin
class CampaignExecutionRecalculator(
    private val records: Records,
    private val transactions: Transactions
) : DerivedRecalculator {
```

por:

```kotlin
class CampaignExecutionRecalculator(
    private val records: Records,
    private val transactions: Transactions,
    private val tenant: TenantModel
) : DerivedRecalculator {
```

reemplazar `if (refresh(UUID.fromString(campaign.id))) written++` por
`if (refresh(organizationId, UUID.fromString(campaign.id))) written++`, y reemplazar la función `refresh` entera por:

```kotlin
    // todo dentro del candado de la campaña: la moneda, los compromisos, los aportes y la escritura se leen con la aprobación
    // que los suma ya confirmada o aún esperando, nunca a medias. una campaña sin moneda (null) no suma nada y cuenta todo lo
    // ejecutado como de otra moneda (P45). desde F5, y solo si el tenant tiene el modelo, los aportes en delivered o closed
    // (ASM-063, elección 12 de la especificación de F5)
    private suspend fun refresh(
        organizationId: UUID,
        id: UUID
    ): Boolean =
        transactions.locked("$CAMPAIGN:$id") {
            val current = records.find(CAMPAIGN, id)?.attributes ?: return@locked false
            val commitments =
                records
                    .all(Commitments.COMMITMENT, filters = mapOf(CAMPAIGN to id.toString()))
                    .map {
                        CampaignExecution.Executed(
                            Amounts.parse(it.attributes["executed_amount"], "executed_amount"),
                            Values.text(it.attributes["currency"])
                        )
                    }
            val contributions =
                if (!tenant.has(organizationId, Contributions.CONTRIBUTION)) {
                    emptyList()
                } else {
                    records
                        .all(
                            Contributions.CONTRIBUTION,
                            filters = mapOf(CAMPAIGN to id.toString()),
                            criteria = listOf(Criteria.inState(Contributions.EXECUTED))
                        ).map {
                            CampaignExecution.Executed(
                                Amounts.parse(it.attributes[Contributions.TOTAL], Contributions.TOTAL),
                                Values.text(it.attributes[Contributions.CURRENCY])
                            )
                        }
                }
            val wanted = CampaignExecution.of(Values.text(current["currency"]), commitments + contributions)
            val changes = wanted.filter { (field, value) -> !Values.same(current[field], value) }
            if (changes.isEmpty()) {
                false
            } else {
                records.patch(CAMPAIGN, id, changes, REASON)
                true
            }
        }
```

y sumar los imports `sgspe.common.Criteria` y `sgspe.common.TenantModel`. La integración de esta segunda fuente la prueba
la Tarea 10, que es la primera con aportes entregados.

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ContributionTotalsListenerTest` (2) y `SupportCapTest` en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ContributionTotals.kt src/main/kotlin/sgspe/investment/CampaignExecution.kt \
  src/test/kotlin/sgspe/investment/ContributionTotalsListenerTest.kt src/test/kotlin/sgspe/investment/ContributionTotalsApiTest.kt
git commit -F - <<'MSG'
feat: total del aporte y ejecutado de la campaña con aportes

ContributionTotalsListener mantiene total_amount con cada ítem y lo escribe al aprobar; JOB-02 lo
corrige en requested y suma al ejecutado de la campaña los aportes entregados o cerrados en su
moneda (ASM-063).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `ContributionTotalsApiTest` y `CampaignExecutionApiTest` (sin cambios).

---

### Tarea 9: La entrega del aporte: endpoint, corrección y reglas

**Files:**
- Create: `src/main/kotlin/sgspe/investment/ContributionDeliveryRules.kt`
- Create: `src/main/kotlin/sgspe/investment/ContributionDeliveries.kt`
- Modify: `src/main/kotlin/sgspe/commitment/Commitments.kt` (`SOCIAL_CONTRIBUTION`)
- Modify: `src/main/kotlin/sgspe/commitment/Deliveries.kt` (`CorrectedDelivery`, `ContributionDeliveryResult`,
  `CommitmentDeliveries.patch`, `evidenceIds`, `DeliveryController.patch`)
- Modify: `src/main/kotlin/sgspe/evidence/EvidenceRules.kt` (`deliveryLock`, `lockOrder`), `Evidences.kt` (`locksOf`)
- Test: `src/test/kotlin/sgspe/investment/ContributionDeliveryTest.kt` (crear)
- Test: `src/test/kotlin/sgspe/evidence/EvidenceRulesTest.kt`
- Test: `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt` (casos al final, integración)

**Interfaces:**
- Consumes: `Contributions`, `InvestmentRule`, `InvestmentRuleOrder` (Tarea 1); `CommitmentDeliveries.FIXED`,
  `CommandAnswer`, `Idempotency`, `IdempotencyKey`, `IfMatch`, `Versions`, `StrictBody`, `StrictQuery`, `Permissions`,
  `Transactions`, `ModelCatalog`; `ExactlyOneGuard` de `delivery` y el alcance de la entrega por su aporte (plan 5.1).
- Produces:
  - `object ContributionDelivery` (`AMOUNT`, `CONFORMITY_BY`, `EVIDENCE_DELIVERY`, `fun exists(): Finding`,
    `fun frozen(state: String?): Finding?`, `fun amountNotAllowed(field: String = AMOUNT): Finding`); la Tarea 10 le suma
    P8.
  - El bean `ContributionDeliveryRecordRules(records, view, transactions)` (`InvestmentRuleOrder.RECORD + 1`).
  - `@Service class ContributionDeliveries` con `suspend fun create(contributionId: UUID, body: Map<String, Any?>,
    reason: String?, ifMatch: String?): CommandAnswer`; `@RestController class ContributionDeliveryController` con
    `POST /api/sgspe/social-contributions/{id}/deliveries`.
  - En `sgspe.commitment`: `const val Commitments.SOCIAL_CONTRIBUTION = "social_contribution"`; `sealed interface
    CorrectedDelivery { val delivery: RecordResponse }`; `data class DeliveryResult(override val delivery, val
    commitment): CorrectedDelivery`; `data class ContributionDeliveryResult(override val delivery: RecordResponse, val
    contribution: RecordResponse): CorrectedDelivery`; `CommitmentDeliveries.patch(...): CorrectedDelivery`;
    `CommitmentDeliveries.evidenceIds(raw: Any?): List<UUID>` (companion).
  - En `sgspe.evidence`: `EvidenceRules.deliveryLock(attributes: Map<String, Any?>): String?` y
    `EvidenceRules.lockOrder(links: List<EvidenceLink>, locks: Map<UUID, String>): List<EvidenceLink>`.

§3.5 y decisión 2: la captura solo en `procured` (`409 STATE_MISMATCH` sobre `record`, decisión 8), una sola entrega
(`409 DELIVERY_EXISTS`, con el candado del aporte; el único del modelo es la última barrera), sin `delivered_amount` (`400
FIELD_NOT_ALLOWED`, decisión 9), con `If-Match` del aporte, motivo e `Idempotency-Key`; no cambia el estado. La corrección
por `PATCH /api/sgspe/deliveries/{id}` (decisión 7) y, por cualquier puerta, la entrega y sus vínculos de evidencia
congelados con el aporte fuera de `procured` (`409 DELIVERY_FROZEN` sobre `social_contribution`). Vincular evidencias toma
los candados en el orden de sus claves (decisión 6).

- [ ] **Paso 1: Escribir las pruebas que fallan**

Crear `src/test/kotlin/sgspe/investment/ContributionDeliveryTest.kt`:

```kotlin
package sgspe.investment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.commitment.Commitments

// las reglas de la entrega de un aporte (especificación de F5 §3.5), puras
class ContributionDeliveryTest {
    @Test
    fun `la entrega cambia solo con el aporte en procured`() {
        assertNull(ContributionDelivery.frozen("procured"))
        for (state in listOf("requested", "approved", "delivered", "closed", "cancelled", null)) {
            assertEquals("DELIVERY_FROZEN" to "social_contribution", ContributionDelivery.frozen(state)?.let { it.code to it.field }, "$state")
        }
    }

    @Test
    fun `una segunda entrega y el monto entregado tienen su codigo y su campo`() {
        assertEquals(Triple("DELIVERY_EXISTS", "social_contribution", HttpStatus.CONFLICT), ContributionDelivery.exists().let { Triple(it.code, it.field, it.status) })
        assertEquals(Triple("FIELD_NOT_ALLOWED", "delivered_amount", HttpStatus.BAD_REQUEST), ContributionDelivery.amountNotAllowed().let { Triple(it.code, it.field, it.status) })
        assertEquals("attributes.delivered_amount", ContributionDelivery.amountNotAllowed("attributes.delivered_amount").field)
    }

    // sgspe.commitment no depende de sgspe.investment: nombra el aporte con su propia constante, que debe ser la misma
    @Test
    fun `el candado del aporte es el mismo en los dos paquetes`() {
        assertEquals(Contributions.CONTRIBUTION, Commitments.SOCIAL_CONTRIBUTION)
    }
}
```

En `src/test/kotlin/sgspe/evidence/EvidenceRulesTest.kt`, reemplazar la prueba
`el alta vincula las entregas por el id ascendente de su compromiso, despues de lo que no toma candado` entera (con su
comentario) por:

```kotlin
    // vincular una entrega toma el candado de su padre hasta el fin de la transacción (DELIVERY_FROZEN): commitment:<id> o, desde
    // F5, social_contribution:<id>. el alta vincula las entregas por la clave ascendente de ese candado y, dentro de una, por su
    // id, así dos altas con los mismos padres en orden inverso no se interbloquean (decisión 6 del plan 5.2a)
    @Test
    fun `el alta vincula las entregas por la clave del candado de su padre, despues de lo que no toma candado`() {
        val low = UUID.fromString("00000000-0000-0000-0000-00000000000a")
        val high = UUID.fromString("f0000000-0000-0000-0000-000000000000")
        val d1 = EvidenceLink("delivery", UUID.fromString("90000000-0000-0000-0000-000000000001"))
        val d2 = EvidenceLink("delivery", UUID.fromString("10000000-0000-0000-0000-000000000002"))
        val d3 = EvidenceLink("delivery", UUID.fromString("50000000-0000-0000-0000-000000000003"))
        val unknown = EvidenceLink("delivery", UUID.fromString("20000000-0000-0000-0000-000000000004"))
        val engagement = EvidenceLink("engagement", UUID.fromString("30000000-0000-0000-0000-000000000005"))
        val ofContribution = EvidenceLink("delivery", UUID.fromString("00000000-0000-0000-0000-000000000006"))
        val locks =
            mapOf(
                d1.recordId to "commitment:$low",
                d2.recordId to "commitment:$high",
                d3.recordId to "commitment:$low",
                ofContribution.recordId to "social_contribution:$low"
            )
        val expected = listOf(unknown, engagement, d3, d1, d2, ofContribution)
        assertEquals(expected, EvidenceRules.lockOrder(listOf(ofContribution, d2, unknown, d1, engagement, d3), locks))
        // el orden pedido no importa: la misma lista en orden inverso da el mismo orden de candados
        assertEquals(listOf(d3, d1, d2), EvidenceRules.lockOrder(listOf(d1, d3, d2), locks))
        assertEquals(EvidenceRules.lockOrder(listOf(d2, d1), locks), EvidenceRules.lockOrder(listOf(d1, d2), locks))
    }

    @Test
    fun `el candado de una entrega es el de su compromiso o el de su aporte`() {
        val id = UUID.randomUUID()
        assertEquals("commitment:$id", EvidenceRules.deliveryLock(mapOf("commitment" to id.toString())))
        assertEquals("social_contribution:$id", EvidenceRules.deliveryLock(mapOf("social_contribution" to id.toString())))
        assertEquals(null, EvidenceRules.deliveryLock(emptyMap()))
    }
```

Agregar al final de `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt`, antes de la última llave
(con el import `sgspe.common.IdempotencyKey` y `org.junit.jupiter.api.Assertions.assertArrayEquals`):

```kotlin

    // ---- Tarea 9: la entrega del aporte ----

    private fun deliveriesOf(contribution: String): Int =
        tree(send("GET", "/api/objects/delivery/records?social_contribution=$contribution", null, HttpStatus.OK))["totalElements"].asInt()

    @Test
    fun `capturar la entrega fuera de procured es STATE_MISMATCH y en procured no cambia el estado`() {
        val project = testProject()
        val approved = approvedWithItems(project)
        val early = captureDelivery(approved, evidence = deliveryEvidence(project))
        assertEquals(HttpStatus.CONFLICT, early.status, early.body)
        assertEquals("STATE_MISMATCH", code(early))
        assertEquals(listOf("record"), problemFields(early.body))
        assertEquals(0, deliveriesOf(approved))
        val id = procuredWithItems(project)
        val evidence = deliveryEvidence(project)
        val captured = captureDelivery(id, evidence = evidence)
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        val body = tree(captured.body)
        assertEquals("procured", body["contribution"]["state"].asString())
        assertEquals(id, body["delivery"]["attributes"]["social_contribution"].asString())
        val delivery = body["delivery"]["id"].asString()
        val linked =
            tree(send("GET", "/api/objects/delivery/records/$delivery/related/evidence_delivery", null, HttpStatus.OK))["content"]
                .values()
                .map { it["id"].asString() }
                .toSet()
        assertEquals(evidence.toSet(), linked)
    }

    @Test
    fun `una segunda entrega es DELIVERY_EXISTS y delivered_amount no se admite`() {
        val id = procuredWithItems()
        val amount = captureDelivery(id, mapOf("delivered_amount" to 100))
        assertEquals(HttpStatus.BAD_REQUEST, amount.status, amount.body)
        assertEquals("FIELD_NOT_ALLOWED", code(amount))
        assertEquals(listOf("attributes.delivered_amount"), problemFields(amount.body))
        assertEquals(HttpStatus.CREATED, captureDelivery(id).status)
        val second = captureDelivery(id)
        assertEquals(HttpStatus.CONFLICT, second.status, second.body)
        assertEquals("DELIVERY_EXISTS", code(second))
        assertEquals(listOf("social_contribution"), problemFields(second.body))
        assertEquals(1, deliveriesOf(id))
    }

    // Review Focus 2: el portal reintenta con la misma clave tras perder la red
    @Test
    fun `un reenvio de la captura con la misma clave devuelve la misma respuesta y deja una sola entrega`() {
        val id = procuredWithItems()
        val headers = ifMatch("social_contribution", id) + withReason("entrega") + (IdempotencyKey.HEADER to uniqueName("en-"))
        val first = captureDelivery(id, headers = headers)
        assertEquals(HttpStatus.CREATED, first.status, first.body)
        val again = captureDelivery(id, headers = headers)
        assertEquals(HttpStatus.CREATED, again.status, again.body)
        assertEquals("true", again.headers.getFirst("Idempotent-Replayed"))
        assertArrayEquals(first.body.toByteArray(), again.body.toByteArray())
        assertEquals(1, deliveriesOf(id))
    }

    @Test
    fun `la captura exige If-Match del aporte`() {
        val id = procuredWithItems()
        assertEquals(HttpStatus.PRECONDITION_REQUIRED, captureDelivery(id, headers = withReason("entrega")).status)
        assertEquals(0, deliveriesOf(id))
    }

    @Test
    fun `la entrega se corrige en procured y queda congelada al anular el aporte`() {
        val project = testProject()
        val id = procuredWithItems(project)
        val foto = evidence(project, "FOTO")
        val delivery = tree(captureDelivery(id, evidence = listOf(foto)).body)["delivery"]["id"].asString()
        val fixed = patch("/api/sgspe/deliveries/$delivery", mapOf("attributes" to mapOf("place" to "Local comunal")), token, ifMatch("delivery", delivery) + withReason())
        assertEquals(HttpStatus.OK, fixed.status, fixed.body)
        assertEquals(id, tree(fixed.body)["contribution"]["id"].asString())
        assertEquals("Local comunal", tree(fixed.body)["delivery"]["attributes"]["place"].asString())
        move("social_contribution", id, "cancel_procured", member("APPROVER", project).token, "se anula")
        val frozen = patch("/api/sgspe/deliveries/$delivery", mapOf("attributes" to mapOf("place" to "Otro local")), token, ifMatch("delivery", delivery) + withReason())
        assertEquals(HttpStatus.CONFLICT, frozen.status, frozen.body)
        assertEquals("DELIVERY_FROZEN", code(frozen))
        assertEquals(listOf("social_contribution"), problemFields(frozen.body))
        assertEquals("DELIVERY_FROZEN", code(exchange("DELETE", "/api/sgspe/evidence/$foto/links/delivery/$delivery", null, token)))
    }
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ContributionDeliveryTest' --tests 'sgspe.evidence.EvidenceRulesTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'ContributionDelivery'`, `'SOCIAL_CONTRIBUTION'`,
`'deliveryLock'`; `lockOrder` espera `Map<UUID, UUID>`).

- [ ] **Paso 3: Las reglas, el endpoint, la corrección y el orden de candados**

Crear `src/main/kotlin/sgspe/investment/ContributionDeliveryRules.kt`:

```kotlin
package sgspe.investment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import sgspe.common.Finding
import sgspe.common.Records
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.WriteView
import sgspe.common.isLink
import sgspe.common.readAs
import sgspe.common.readable
import wasichai.core.data.RecordChangeKind
import wasichai.core.data.RecordWrite
import java.util.UUID

// las reglas de la entrega de un aporte, puras (especificación de F5 §3.5, decisión 2, ASM-111)
object ContributionDelivery {
    const val AMOUNT = "delivered_amount"
    const val CONFORMITY_BY = "conformity_by"
    const val EVIDENCE_DELIVERY = "evidence_delivery"

    fun exists(): Finding =
        Finding(
            "DELIVERY_EXISTS",
            "El aporte ya tiene su entrega: corríjala; lo que se entrega en partes son aportes distintos (ASM-111)",
            Contributions.CONTRIBUTION
        )

    // la entrega y sus vínculos de evidencia cambian solo con el aporte en procured; desde delivered (y en cancelled) quedan fijos
    fun frozen(state: String?): Finding? =
        if (state == Contributions.PROCURED) {
            null
        } else {
            Finding(
                "DELIVERY_FROZEN",
                "La entrega de un aporte solo cambia con el aporte en procured; este está en ${state ?: "ningún estado"} (ASM-111)",
                Contributions.CONTRIBUTION
            )
        }

    fun amountNotAllowed(field: String = AMOUNT): Finding =
        Finding(
            "FIELD_NOT_ALLOWED",
            "La entrega de un aporte no lleva delivered_amount: el ejecutado del aporte es su total_amount (ASM-063)",
            field,
            status = HttpStatus.BAD_REQUEST
        )
}

// InvestmentWriteGuard, la entrega de un aporte por cualquier puerta (§3.5): el alta de una segunda entrega del mismo aporte
// (DELIVERY_EXISTS, con el candado del aporte; el único del modelo es la última barrera), delivered_amount informado
// (FIELD_NOT_ALLOWED) y, con el aporte fuera de procured, un cambio de la entrega o un vínculo de evidencia que se agrega o se
// quita en el extremo delivery (DELIVERY_FROZEN, con el candado del aporte, el mismo de register_delivery y de las
// anulaciones). las entregas de compromisos (sin social_contribution) no se miran: son de CommitmentRecordRules. el estado
// del aporte se lee como quien escribe; sin lectura, NotReadable. la consulta en seco no toma el candado
@Component
class ContributionDeliveryRecordRules(
    private val records: Records,
    private val view: WriteView,
    private val transactions: Transactions
) : InvestmentRule {
    override val order = InvestmentRuleOrder.RECORD + 1

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        if (change.objectName != Contributions.DELIVERY) return emptyList()
        return when (change.kind) {
            RecordChangeKind.CREATED -> created(change, dryRun)
            RecordChangeKind.UPDATED, RecordChangeKind.DELETED -> changed(change, dryRun)
            else -> emptyList()
        }
    }

    private suspend fun created(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val after = view.after(change)
        val contribution = Records.uuid(after[Contributions.CONTRIBUTION]) ?: return emptyList()
        if (Values.text(after[ContributionDelivery.AMOUNT]) != null) return listOf(ContributionDelivery.amountNotAllowed())
        val taken = if (dryRun) hasDelivery(contribution) else transactions.locked(Contributions.lock(contribution)) { hasDelivery(contribution) }
        return if (taken) listOf(ContributionDelivery.exists()) else emptyList()
    }

    private suspend fun changed(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val contribution = Records.uuid(change.before?.get(Contributions.CONTRIBUTION)) ?: return emptyList()
        if (change.kind == RecordChangeKind.UPDATED && !change.isLink()) {
            val after = view.after(change)
            if (ContributionDelivery.AMOUNT in view.changed(change, after) && Values.text(after[ContributionDelivery.AMOUNT]) != null) {
                return listOf(ContributionDelivery.amountNotAllowed())
            }
        }
        val state = if (dryRun) stateOf(contribution) else transactions.locked(Contributions.lock(contribution)) { stateOf(contribution) }
        return listOfNotNull(ContributionDelivery.frozen(state))
    }

    private suspend fun hasDelivery(contribution: UUID): Boolean =
        readAs(Contributions.DELIVERY) {
            records.count(Contributions.DELIVERY, filters = mapOf(Contributions.CONTRIBUTION to contribution.toString()))
        } > 0

    private suspend fun stateOf(contribution: UUID): String? = records.readable(Contributions.CONTRIBUTION, contribution).state
}
```

Crear `src/main/kotlin/sgspe/investment/ContributionDeliveries.kt`:

```kotlin
package sgspe.investment

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebExchange
import sgspe.commitment.CommitmentDeliveries
import sgspe.commitment.ContributionDeliveryResult
import sgspe.common.Callers
import sgspe.common.CommandAnswer
import sgspe.common.Finding
import sgspe.common.Idempotency
import sgspe.common.IdempotencyKey
import sgspe.common.IfMatch
import sgspe.common.ModelCatalog
import sgspe.common.Permissions
import sgspe.common.Records
import sgspe.common.StrictBody
import sgspe.common.StrictQuery
import sgspe.common.Transactions
import sgspe.common.Values
import sgspe.common.Versions
import sgspe.common.toCodedException
import wasichai.core.common.Actions
import wasichai.core.common.ValidationException
import wasichai.core.data.ChangeReason
import java.util.UUID

// la entrega de un aporte (especificación de F5 §3.5, api.md §4.6): solo captura datos. en una transacción y con el candado
// del aporte comprueba que está en procured y que no tiene entrega, crea la delivery (apiOnly) con su social_contribution y la
// vincula con sus evidencias (evidence_delivery: el acta de recepción firmada, la foto y el comprobante, ya subidos con POST
// /api/sgspe/evidence). no cambia el estado: register_delivery es la transición humana que sigue (P8). sin delivered_amount:
// el ejecutado de un aporte es su total_amount (ASM-063). el «Acta de recepción» emitida (AUT-04) espera a UPS-23. todo como
// quien llama, nunca como la plataforma
@Service
class ContributionDeliveries(
    private val records: Records,
    private val callers: Callers,
    private val permissions: Permissions,
    private val transactions: Transactions,
    private val versions: Versions,
    private val catalog: ModelCatalog
) {
    suspend fun create(
        contributionId: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes", "geometries", "evidence"))
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, Contributions.DELIVERY, attributes, CommitmentDeliveries.FIXED)
        if (Values.text(attributes[ContributionDelivery.AMOUNT]) != null) {
            throw ContributionDelivery.amountNotAllowed("attributes.${ContributionDelivery.AMOUNT}").toCodedException()
        }
        val geometries = StrictBody.objectOf(body, "geometries")
        val evidence = CommitmentDeliveries.evidenceIds(body["evidence"])
        if (reason.isNullOrBlank()) throw ValidationException("Registrar una entrega exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        val grants =
            listOf(Actions.CREATE to Contributions.DELIVERY, Actions.UPDATE to Contributions.CONTRIBUTION) +
                if (evidence.isEmpty()) emptyList() else listOf(Actions.UPDATE to EVIDENCE, Actions.UPDATE to Contributions.DELIVERY)
        permissions.require(user, "Registrar la entrega de un aporte", *grants.toTypedArray())
        val result =
            transactions.locked(Contributions.lock(contributionId)) {
                val contribution = records.get(Contributions.CONTRIBUTION, contributionId)
                versions.check(Contributions.CONTRIBUTION, contributionId, expected, contribution.updatedAt, "El aporte")
                if (contribution.state != Contributions.PROCURED) {
                    throw Finding(
                        "STATE_MISMATCH",
                        "La entrega se registra con el aporte adquirido (procured); este está en ${contribution.state}",
                        "record"
                    ).toCodedException()
                }
                if (records.count(Contributions.DELIVERY, filters = mapOf(Contributions.CONTRIBUTION to contributionId.toString())) > 0) {
                    throw ContributionDelivery.exists().toCodedException()
                }
                val delivery =
                    records.create(Contributions.DELIVERY, attributes + (Contributions.CONTRIBUTION to contributionId.toString()), reason, geometries)
                val deliveryId = UUID.fromString(delivery.id)
                evidence.forEach { records.link(EVIDENCE, it, ContributionDelivery.EVIDENCE_DELIVERY, deliveryId, reason) }
                ContributionDeliveryResult(records.get(Contributions.DELIVERY, deliveryId), records.get(Contributions.CONTRIBUTION, contributionId))
            }
        return CommandAnswer(HttpStatus.CREATED, result, result.delivery.updatedAt, "/api/objects/${Contributions.DELIVERY}/records/${result.delivery.id}")
    }

    companion object {
        const val EVIDENCE = "evidence"
    }
}

// api.md §4.6: el alta honra Idempotency-Key, como la de las entregas de compromisos
@RestController
class ContributionDeliveryController(
    private val deliveries: ContributionDeliveries,
    private val idempotency: Idempotency
) {
    @PostMapping("/api/sgspe/social-contributions/{id}/deliveries")
    suspend fun create(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        @RequestHeader(name = IdempotencyKey.HEADER, required = false) key: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<ByteArray> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        return idempotency.respond(key, "POST", exchange.request.path.value(), body) {
            deliveries.create(id, body, ChangeReason.fromHeader(reason), ifMatch)
        }
    }
}
```

En `src/main/kotlin/sgspe/commitment/Commitments.kt`, después de `const val EVIDENCE_DELIVERY = "evidence_delivery"`,
agregar:

```kotlin

    // el otro padre de una entrega desde F5 (sgspe.commitment no depende de sgspe.investment): la corrección de una entrega de
    // aporte toma su candado, social_contribution:<id>, el mismo que Contributions.lock
    const val SOCIAL_CONTRIBUTION = "social_contribution"
```

En `src/main/kotlin/sgspe/commitment/Deliveries.kt`:

1. Reemplazar:

```kotlin
data class DeliveryResult(
    val delivery: RecordResponse,
    val commitment: RecordResponse
)
```

por:

```kotlin
// lo que responde la corrección de una entrega: la entrega y su padre
sealed interface CorrectedDelivery {
    val delivery: RecordResponse
}

data class DeliveryResult(
    override val delivery: RecordResponse,
    val commitment: RecordResponse
) : CorrectedDelivery

// la entrega de un aporte (desde F5): la captura (ContributionDeliveries) y su corrección responden { delivery, contribution }
data class ContributionDeliveryResult(
    override val delivery: RecordResponse,
    val contribution: RecordResponse
) : CorrectedDelivery
```

2. Reemplazar la función `patch` de `CommitmentDeliveries` entera (con su comentario) por:

```kotlin
    // corrige los datos de una entrega; solo escribe lo que cambia. el padre de la entrega no cambia nunca (FIXED). la de un
    // compromiso recalcula su ejecutado con el candado del compromiso; la de un aporte (desde F5, decisión 7 del plan 5.2a)
    // toma el candado del aporte, no recalcula nada y responde { delivery, contribution }: sus reglas (DELIVERY_FROZEN sobre
    // social_contribution, sin delivered_amount) son de InvestmentWriteGuard
    suspend fun patch(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CorrectedDelivery {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf("attributes", "geometries"))
        val changes = amount(StrictBody.objectOf(body, "attributes"))
        val geometries = StrictBody.objectOf(body, "geometries")
        requireReason(reason)
        val user = callers.require()
        val read = records.get(Commitments.DELIVERY, id)
        val commitmentId = Records.uuid(read.attributes["commitment"])
        if (commitmentId == null) {
            val contributionId =
                Records.uuid(read.attributes[Commitments.SOCIAL_CONTRIBUTION])
                    ?: throw Finding("STATE_MISMATCH", "La entrega no es de un compromiso ni de un aporte", "commitment").toCodedException()
            permissions.require(user, "Corregir una entrega", Actions.UPDATE to Commitments.DELIVERY, Actions.UPDATE to Commitments.SOCIAL_CONTRIBUTION)
            return transactions.locked("${Commitments.SOCIAL_CONTRIBUTION}:$contributionId") {
                ContributionDeliveryResult(correct(id, changes, geometries, reason, expected), records.get(Commitments.SOCIAL_CONTRIBUTION, contributionId))
            }
        }
        permissions.require(user, "Corregir una entrega", Actions.UPDATE to Commitments.DELIVERY, Actions.UPDATE to Commitments.COMMITMENT)
        return transactions.locked(Reschedules.lock(commitmentId)) {
            val written = correct(id, changes, geometries, reason, expected)
            DeliveryResult(written, refreshExecuted(commitmentId, reason))
        }
    }

    // la entrega leída dentro del candado, comparada con If-Match y escrita solo si algo cambia
    private suspend fun correct(
        id: UUID,
        changes: Map<String, Any?>,
        geometries: Map<String, Any?>,
        reason: String?,
        expected: java.time.Instant
    ): RecordResponse {
        val delivery = records.get(Commitments.DELIVERY, id)
        versions.check(Commitments.DELIVERY, id, expected, delivery.updatedAt, "La entrega")
        val diff = changes.filter { (field, value) -> !Values.same(delivery.attributes[field], value) }
        return if (diff.isEmpty() && geometries.isEmpty()) delivery else records.patch(Commitments.DELIVERY, id, diff, reason, geometries)
    }
```

3. Reemplazar la función privada `evidence(raw: Any?)` de `CommitmentDeliveries` por un uso del companion: en `create`,
   `val evidence = evidence(body["evidence"])` pasa a `val evidence = evidenceIds(body["evidence"])`; la función se borra
   de la clase y se agrega en el `companion object`:

```kotlin
        // los ids de evidence de un cuerpo de entrega (también el de la entrega de un aporte): una lista de a lo sumo
        // MAX_EVIDENCE UUID, sin repetidos
        fun evidenceIds(raw: Any?): List<UUID> {
            if (raw == null) return emptyList()
            val list = raw as? List<*> ?: throw ValidationException("Evidencias inválidas", "evidence", "debe ser una lista de ids de evidence")
            if (list.size > MAX_EVIDENCE) throw ValidationException("Demasiadas evidencias", "evidence", "a lo sumo $MAX_EVIDENCE por entrega")
            return list
                .mapIndexed { index, item ->
                    Records.uuid(item) ?: throw ValidationException("Evidencia inválida", "evidence[$index]", "debe ser el id de una evidence")
                }.distinct()
        }
```

4. En `DeliveryController.patch`, reemplazar la firma y el cuerpo por:

```kotlin
    @PatchMapping("/api/sgspe/deliveries/{id}")
    suspend fun patch(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        @RequestHeader(name = ChangeReason.HEADER, required = false) reason: String?,
        @RequestHeader(name = IfMatch.HEADER, required = false) ifMatch: String?,
        exchange: ServerWebExchange
    ): ResponseEntity<Any> {
        StrictQuery.requireOnly(exchange.request.queryParams, emptySet())
        val result = deliveries.patch(id, body, ChangeReason.fromHeader(reason), ifMatch)
        return ResponseEntity.ok().eTag("\"${result.delivery.updatedAt}\"").body(result)
    }
```

(el cuerpo se declara `Any` para que Jackson escriba la clase concreta: `{ delivery, commitment }` o
`{ delivery, contribution }`). Cambiar también el comentario de `FIXED` por «el padre de la entrega lo fija el comando:
commitment en las de compromisos y social_contribution en las de aportes (F5)».

En `src/main/kotlin/sgspe/evidence/EvidenceRules.kt`, reemplazar `fun lockOrder` entero (con su comentario) por:

```kotlin
    // la clave del candado del padre de una entrega (Transactions, «el orden acordado»): el compromiso (DELIVERY_FROZEN de WF-D)
    // o, desde F5, el aporte (DELIVERY_FROZEN de WF-P). null sin padre. los nombres son literales: sgspe.evidence no depende de
    // sgspe.commitment ni de sgspe.investment
    fun deliveryLock(attributes: Map<String, Any?>): String? =
        Records.uuid(attributes["commitment"])?.let { "commitment:$it" }
            ?: Records.uuid(attributes["social_contribution"])?.let { "social_contribution:$it" }

    // el orden en que el alta vincula: vincular una entrega toma el candado de su padre hasta el fin de la transacción, así que
    // dos altas con entregas de los mismos padres en orden inverso se interbloquearían. primero va lo que no toma candado, en el
    // orden pedido (los demás vínculos y una entrega sin padre conocido, cuyo vínculo falla por su cuenta), y después las
    // entregas por la clave ascendente del candado de su padre y, dentro de una, por su id. `locks`: esa clave, por entrega
    fun lockOrder(
        links: List<EvidenceLink>,
        locks: Map<UUID, String>
    ): List<EvidenceLink> {
        val (locking, free) = links.partition { it.objectName == DELIVERY && it.recordId in locks }
        return free + locking.sortedWith(compareBy({ locks.getValue(it.recordId) }, { it.recordId.toString() }))
    }
```

con el import `sgspe.common.Records`. En `src/main/kotlin/sgspe/evidence/Evidences.kt`, en `create` cambiar
`EvidenceRules.lockOrder(links, commitmentsOf(links))` por `EvidenceRules.lockOrder(links, locksOf(links))` y reemplazar
`commitmentsOf` entera (con su comentario) por:

```kotlin
    // la clave del candado del padre de cada entrega pedida, leída como quien llama (su padre no cambia nunca); una entrega que no
    // existe o no se lee queda fuera y su vínculo falla al escribirse. a lo sumo MAX_LINKS lecturas
    private suspend fun locksOf(links: List<EvidenceLink>): Map<UUID, String> =
        links
            .filter { it.objectName == EvidenceRules.DELIVERY }
            .mapNotNull { link ->
                records.find(EvidenceRules.DELIVERY, link.recordId)?.let { delivery ->
                    EvidenceRules.deliveryLock(delivery.attributes)?.let { link.recordId to it }
                }
            }.toMap()
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ContributionDeliveryTest` (3) y `EvidenceRulesTest` en verde; `ArchitectureTest` en verde
(`sgspe.commitment` y `sgspe.evidence` no importan `sgspe.investment`; `ContributionDeliveries` y
`ContributionDeliveryController` no repiten el nombre de otro bean).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ContributionDeliveryRules.kt src/main/kotlin/sgspe/investment/ContributionDeliveries.kt \
  src/main/kotlin/sgspe/commitment/Commitments.kt src/main/kotlin/sgspe/commitment/Deliveries.kt \
  src/main/kotlin/sgspe/evidence/EvidenceRules.kt src/main/kotlin/sgspe/evidence/Evidences.kt \
  src/test/kotlin/sgspe/investment/ContributionDeliveryTest.kt src/test/kotlin/sgspe/evidence/EvidenceRulesTest.kt \
  src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: entrega de un aporte, una sola y congelada fuera de procured

POST /api/sgspe/social-contributions/{id}/deliveries captura la entrega del aporte en procured con
sus evidencias, sin delivered_amount y una sola vez (DELIVERY_EXISTS); la corrección responde
{ delivery, contribution } y fuera de procured la entrega y sus vínculos quedan congelados.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `SocialContributionWorkflowApiTest` (casos hasta la Tarea 9), `DeliveryApiTest`
(sin cambios: la corrección de una entrega de compromiso responde igual) y `EvidenceApiTest`.

---

### Tarea 10: La entrega con conformidad y evidencia (P8)

**Files:**
- Modify: `src/main/kotlin/sgspe/investment/ContributionDeliveryRules.kt` (P8 en `ContributionDelivery` y
  `ContributionDeliveryRule`)
- Test: `src/test/kotlin/sgspe/investment/ContributionDeliveryTest.kt`
- Test: `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt` (casos al final, integración)

**Interfaces:**
- Consumes: `ContributionApproval.target` y el candado de `register_delivery` (`ContributionLockRule`, Tarea 4);
  `ContributionDelivery` (Tarea 9); `Fulfillment.DeliveryEvidence`, `Fulfillment.conforms`, `Fulfillment.countedTypes`
  (`sgspe.commitment`); `Readability`, `Records.all`, `Records.related`, `readAs`; `CampaignExecutionRecalculator` con
  aportes (Tarea 8).
- Produces: `ContributionDelivery.REQUIRED_TYPES`, `ContributionDelivery.MAX_EVIDENCE`,
  `ContributionDelivery.missing(types: Set<String>): List<String>`,
  `ContributionDelivery.registration(delivery: Fulfillment.DeliveryEvidence?): Finding?`; el bean
  `ContributionDeliveryRule(records, metadata)` (`InvestmentRuleOrder.DELIVERY`).

P8 (§3.3): `register_delivery` exige la única entrega del aporte con `conformity_given` verdadero y `conformity_by`
(`409 DELIVERY_REQUIRED` sobre `delivery`), que quien aplica lea `conformity_by` (`403 FIELD_NOT_READABLE` sobre
`delivery.conformity_by`, decisión 4) y, vinculadas a ella, al menos una ACTA DE RECEPCION, una FOTO y un COMPROBANTE del
proyecto del aporte (`409 EVIDENCE_REQUIRED` sobre `evidence_delivery`), con el candado del aporte ya tomado.

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/investment/ContributionDeliveryTest.kt`, agregar antes de la última llave (con los imports
`sgspe.commitment.Fulfillment`, `sgspe.common.ModelCatalog` y `org.junit.jupiter.api.Assertions.assertTrue`):

```kotlin

    private fun delivery(
        given: Boolean = true,
        by: String? = "Junta directiva",
        types: Set<String> = ContributionDelivery.REQUIRED_TYPES.toSet()
    ) = Fulfillment.DeliveryEvidence(given, by, types)

    @Test
    fun `P8 pide la entrega con conformidad y quienes la dieron`() {
        for (missing in listOf(null, delivery(given = false), delivery(by = null), delivery(by = " "))) {
            assertEquals("DELIVERY_REQUIRED" to "delivery", ContributionDelivery.registration(missing)?.let { it.code to it.field }, "$missing")
        }
        assertNull(ContributionDelivery.registration(delivery()))
    }

    @Test
    fun `P8 pide acta de recepcion, foto y comprobante`() {
        for (absent in ContributionDelivery.REQUIRED_TYPES) {
            val finding = ContributionDelivery.registration(delivery(types = ContributionDelivery.REQUIRED_TYPES.toSet() - absent))!!
            assertEquals("EVIDENCE_REQUIRED" to "evidence_delivery", finding.code to finding.field)
            assertTrue(absent in finding.message, finding.message)
        }
        assertEquals(listOf("FOTO", "COMPROBANTE"), ContributionDelivery.missing(setOf("ACTA DE RECEPCION", "ACTA DE ENTREGA")))
    }

    @Test
    fun `los tipos de P8 existen en el modelo`() {
        val types = ModelCatalog.fromClasspath().enumOf("evidence", "evidence_type")
        assertEquals(emptyList<String>(), ContributionDelivery.REQUIRED_TYPES.filter { it !in types })
    }
```

Agregar al final de `src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt`, antes de la última llave
(con los imports `kotlinx.coroutines.runBlocking`, `org.junit.jupiter.api.Assertions.assertNotNull`,
`org.springframework.beans.factory.annotation.Autowired` y `sgspe.common.DerivedFieldsJob`):

```kotlin

    // ---- Tarea 10: P8 ----

    @Autowired
    private lateinit var job: DerivedFieldsJob

    private fun linkTo(
        delivery: String,
        evidence: String
    ): Response = exchange("POST", "/api/sgspe/evidence/$evidence/links", mapOf("object" to "delivery", "recordId" to delivery), token)

    @Test
    fun `register_delivery exige la entrega con conformidad`() {
        val project = testProject()
        val id = procuredWithItems(project)
        val none = transition("social_contribution", id, "register_delivery", token, "entregado")
        assertEquals(HttpStatus.CONFLICT, none.status, none.body)
        assertEquals("DELIVERY_REQUIRED", code(none))
        assertEquals(listOf("delivery"), problemFields(none.body))
        val delivery = tree(captureDelivery(id, mapOf("conformity_given" to false), deliveryEvidence(project)).body)["delivery"]["id"].asString()
        assertEquals("DELIVERY_REQUIRED", code(transition("social_contribution", id, "register_delivery", token, "entregado")))
        val given = patch("/api/sgspe/deliveries/$delivery", mapOf("attributes" to mapOf("conformity_given" to true)), token, ifMatch("delivery", delivery) + withReason())
        assertEquals(HttpStatus.OK, given.status, given.body)
        move("social_contribution", id, "register_delivery", reason = "entregado")
        assertEquals("delivered", state("social_contribution", id))
    }

    @Test
    fun `register_delivery sin acta de recepcion, sin foto o sin comprobante es EVIDENCE_REQUIRED`() {
        val project = testProject()
        val all = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE")
        for (absent in all) {
            val id = procuredWithItems(project)
            val delivery = tree(captureDelivery(id, evidence = deliveryEvidence(project, all - absent)).body)["delivery"]["id"].asString()
            val refused = transition("social_contribution", id, "register_delivery", token, "entregado")
            assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
            assertEquals("EVIDENCE_REQUIRED", code(refused), absent)
            assertEquals(listOf("evidence_delivery"), problemFields(refused.body))
            assertEquals(HttpStatus.NO_CONTENT, linkTo(delivery, evidence(project, absent)).status, absent)
            move("social_contribution", id, "register_delivery", reason = "entregado")
        }
    }

    @Test
    fun `la entrega y sus vinculos de evidencia no cambian desde delivered`() {
        val project = testProject()
        val id = procuredWithItems(project)
        val attached = deliveryEvidence(project)
        val delivery = tree(captureDelivery(id, evidence = attached).body)["delivery"]["id"].asString()
        move("social_contribution", id, "register_delivery", reason = "entregado")
        val frozen = patch("/api/sgspe/deliveries/$delivery", mapOf("attributes" to mapOf("place" to "Otro local")), token, ifMatch("delivery", delivery) + withReason())
        assertEquals("DELIVERY_FROZEN", code(frozen))
        assertEquals("DELIVERY_FROZEN", code(exchange("DELETE", "/api/sgspe/evidence/${attached.first()}/links/delivery/$delivery", null, token)))
        assertEquals("DELIVERY_FROZEN", code(linkTo(delivery, evidence(project, "FOTO"))))
    }

    // ASM-063 y elección 12: el aporte entregado cuenta como ejecutado de su campaña, con su total_amount
    @Test
    fun `JOB-02 suma el aporte entregado al ejecutado de la campana`() {
        val project = testProject()
        val campaign = cappedCampaign(project, 10_000)
        val id = procuredWithItems(project, attributes = mapOf("exploration_campaign" to campaign))
        assertEquals(HttpStatus.CREATED, captureDelivery(id, evidence = deliveryEvidence(project)).status)
        move("social_contribution", id, "register_delivery", reason = "entregado")
        assertNotNull(runBlocking { job.runOnce(full = true) }, "otro nodo o la programación tenía el candado")
        val executed = record("exploration_campaign", campaign)["attributes"]["executed_social_amount"].decimalValue()
        assertEquals(0, java.math.BigDecimal("300").compareTo(executed))
    }
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.investment.ContributionDeliveryTest'
```

Esperado: FAIL en la compilación (`Unresolved reference 'registration'`, `'REQUIRED_TYPES'`, `'missing'`).

- [ ] **Paso 3: P8**

En `src/main/kotlin/sgspe/investment/ContributionDeliveryRules.kt`, agregar al final de `object ContributionDelivery`
(antes de su llave de cierre):

```kotlin

    // P8: la evidencia mínima de la entrega de un aporte [DOC-02 p.4], del proyecto del aporte
    val REQUIRED_TYPES: List<String> = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE")

    // las evidencias de la entrega que P8 lee como mucho (las lecturas M2M son acotadas): más es un 400 TOO_MANY_RECORDS
    const val MAX_EVIDENCE = 500

    fun missing(types: Set<String>): List<String> = REQUIRED_TYPES.filter { it !in types }

    // P8 sobre la única entrega del aporte (null si no tiene): con conformidad y quiénes la dieron, y las tres evidencias
    fun registration(delivery: Fulfillment.DeliveryEvidence?): Finding? {
        if (delivery == null || !Fulfillment.conforms(delivery)) {
            return Finding(
                "DELIVERY_REQUIRED",
                "Registrar la entrega exige la entrega del aporte con conformidad del grupo de interés y quiénes la dieron (P8)",
                Contributions.DELIVERY
            )
        }
        val missing = missing(delivery.evidenceTypes)
        if (missing.isEmpty()) return null
        return Finding(
            "EVIDENCE_REQUIRED",
            "A la entrega le falta evidencia vinculada del proyecto del aporte: ${missing.joinToString(", ")} (P8)",
            EVIDENCE_DELIVERY
        )
    }
```

y agregar al final del archivo:

```kotlin

// P8 de WF-P (§3.3): register_delivery con la entrega del aporte, su conformidad y sus tres evidencias del proyecto del aporte
// (Fulfillment.countedTypes: una evidencia de otro proyecto o sin proyecto no cuenta). lee la entrega y sus evidencias como
// quien aplica la transición: sin lectura, OBJECT_NOT_READABLE; sin lectura de conformity_by (restringido), FIELD_NOT_READABLE.
// el candado del aporte ya lo tomó ContributionLockRule, así que la entrega no cambia mientras se juzga
@Component
class ContributionDeliveryRule(
    private val records: Records,
    private val metadata: MetadataService
) : InvestmentRule {
    override val order = InvestmentRuleOrder.DELIVERY

    override suspend fun findings(
        change: RecordWrite,
        dryRun: Boolean
    ): List<Finding> {
        val id = ContributionApproval.target(change, setOf(Contributions.REGISTER_DELIVERY)) ?: return emptyList()
        val delivery =
            readAs(Contributions.DELIVERY) {
                records.all(
                    Contributions.DELIVERY,
                    filters = mapOf(Contributions.CONTRIBUTION to id.toString()),
                    max = 1,
                    field = Contributions.DELIVERY
                )
            }.firstOrNull() ?: return listOfNotNull(ContributionDelivery.registration(null))
        if (!Readability(metadata).canRead("${Contributions.DELIVERY}.${ContributionDelivery.CONFORMITY_BY}")) {
            return listOf(
                Finding(
                    "FIELD_NOT_READABLE",
                    "Registrar la entrega exige leer quiénes dieron la conformidad de la entrega",
                    "${Contributions.DELIVERY}.${ContributionDelivery.CONFORMITY_BY}",
                    status = HttpStatus.FORBIDDEN
                )
            )
        }
        val row =
            Fulfillment.DeliveryEvidence(
                Values.bool(delivery.attributes["conformity_given"]) == true,
                Values.text(delivery.attributes[ContributionDelivery.CONFORMITY_BY]),
                emptySet()
            )
        if (!Fulfillment.conforms(row)) return listOfNotNull(ContributionDelivery.registration(row))
        val evidence =
            readAs(ContributionDeliveries.EVIDENCE) {
                records.related(
                    Contributions.DELIVERY,
                    UUID.fromString(delivery.id),
                    ContributionDelivery.EVIDENCE_DELIVERY,
                    max = ContributionDelivery.MAX_EVIDENCE,
                    field = ContributionDelivery.EVIDENCE_DELIVERY
                )
            }
        val counted = row.copy(evidenceTypes = Fulfillment.countedTypes(Records.uuid(change.before.orEmpty()["project"]), evidence.map { it.attributes }))
        return listOfNotNull(ContributionDelivery.registration(counted))
    }
}
```

con los imports `sgspe.commitment.Fulfillment`, `sgspe.common.Readability` y `wasichai.core.metadata.MetadataService`.

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ContributionDeliveryTest` con 6 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/investment/ContributionDeliveryRules.kt src/test/kotlin/sgspe/investment/ContributionDeliveryTest.kt \
  src/test/kotlin/sgspe/investment/SocialContributionWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: registrar la entrega de un aporte exige conformidad, acta, foto y comprobante

register_delivery exige la entrega del aporte con conformidad y quiénes la dieron, que quien la
registra los lea y, vinculadas, un acta de recepción, una foto y un comprobante del proyecto del
aporte (P8).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `SocialContributionWorkflowApiTest` (todos sus casos) y
`CampaignExecutionApiTest`.

---

### Tarea 11: Aceptar una solicitud como aporte

**Files:**
- Modify: `src/main/kotlin/sgspe/attention/Acceptances.kt`
- Modify: `src/main/kotlin/sgspe/attention/AttentionRules.kt` (`CREATED_CONTRIBUTION`, `COMMAND_FIELDS`)
- Modify: `src/main/kotlin/sgspe/attention/AttentionWriteGuard.kt` (`close_unfulfilled`)
- Test: `src/test/kotlin/sgspe/attention/AttentionRulesTest.kt`
- Test: `src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt` (integración)
- Modify: `src/test/kotlin/sgspe/investment/InvestmentModelApiTest.kt` (plan 5.1: la línea que escribe
  `created_contribution` por la API genérica)

**Interfaces:**
- Consumes: `ContributionApiTest` (Tarea 1: `contributionAttributes`, `itemFor`, `requestedContribution`); WF-E3
  con `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted` y `stakeholder_request.created_contribution` (plan 5.1);
  `AttentionCommandFieldsGuard` (sin cambios: lee `AttentionRules.COMMAND_FIELDS`).
- Produces: `AttentionRules.CREATED_CONTRIBUTION = "created_contribution"`; `data class ContributionAcceptance(request:
  RecordResponse, contribution: RecordResponse)`; `Acceptances.CONTRIBUTION = "social_contribution"`;
  `POST /api/sgspe/stakeholder-requests/{id}/accept` con `creates = social_contribution` → `201 { request,
  contribution }`; `AttentionWriteGuard.CONTRIBUTION_UNFULFILLED = setOf("rejected", "cancelled")`.

§3.7 y elección 13: la aceptación valida `attributes` contra el objeto que crea (sin `project`), exige UPDATE sobre
`stakeholder_request`, CREATE sobre ese objeto y el rol de `accept` (un `COMMITMENT_MANAGER` recibe `403` al crear un
aporte, §2.2), escribe la respuesta, crea el aporte en `requested` con el proyecto de la solicitud, guarda
`created_contribution` y aplica `accept`; el aporte lo «registró» quien aceptó (P5). `created_contribution` es campo de
comando (`403 COMMAND_ONLY`). `close_unfulfilled` admite el aporte en `rejected` o `cancelled` (decisión 12).

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/attention/AttentionRulesTest.kt`, agregar antes de la última llave:

```kotlin

    // F5 (§3.7 de la especificación de F5): created_contribution también lo escribe solo la aceptación
    @Test
    fun `created_contribution es un campo de comando de la solicitud`() {
        assertEquals(
            setOf("created_commitment", "created_contribution"),
            AttentionRules.COMMAND_FIELDS.getValue("stakeholder_request").filter { it.startsWith("created_") }.toSet()
        )
        assertEquals(
            "COMMAND_ONLY" to "created_contribution",
            AttentionRules.commandOnly("stakeholder_request", setOf("created_contribution"), person = true, marked = false)?.let { it.code to it.field }
        )
    }
```

En `src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt`:

1. Cambiar la base: `import sgspe.F4ApiTest` por `import sgspe.ContributionApiTest` y
   `class StakeholderRequestWorkflowApiTest : F4ApiTest()` por `class StakeholderRequestWorkflowApiTest : ContributionApiTest()`;
   en el comentario de la clase, agregar «; desde F5, aceptar crea también un aporte (creates = social_contribution), que
   cierra la solicitud al cerrarse».
2. Reemplazar:

```kotlin
    fun `un aporte social, una solicitud fuera de evaluacion o sin respuesta no se aceptan`() {
        val id = underEvaluation(mapOf("response" to null, "responded_at" to null))
        val contribution = accept(id, mapOf("creates" to "social_contribution", "response" to "x", "respondedAt" to "2026-10-08T15:00:00Z"))
        assertEquals(HttpStatus.BAD_REQUEST, contribution.status, contribution.body)
        assertEquals("INVALID_TARGET", code(contribution))
        assertEquals(listOf("creates"), problemFields(contribution.body))
```

por:

```kotlin
    fun `un destino que no es compromiso ni aporte, una solicitud fuera de evaluacion o sin respuesta no se aceptan`() {
        val id = underEvaluation(mapOf("response" to null, "responded_at" to null))
        val other = accept(id, mapOf("creates" to "agreement", "response" to "x", "respondedAt" to "2026-10-08T15:00:00Z"))
        assertEquals(HttpStatus.BAD_REQUEST, other.status, other.body)
        assertEquals("INVALID_TARGET", code(other))
        assertEquals(listOf("creates"), problemFields(other.body))
```

3. Agregar antes del `companion object`:

```kotlin

    // ---- F5: la aceptación como aporte (§3.7 de la especificación de F5) ----

    private fun acceptAsContribution(
        id: String,
        account: Account
    ): Response =
        accept(
            id,
            mapOf(
                "creates" to "social_contribution",
                "response" to "Se atenderá con un aporte de útiles escolares.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to contributionAttributes(testProject()) - "project"
            ),
            account
        )

    @Test
    fun `aceptar como aporte crea el aporte en requested con created_contribution`() {
        val id = underEvaluation()
        val response = acceptAsContribution(id, member("SOCIAL_INVESTMENT_MANAGER"))
        assertEquals(HttpStatus.CREATED, response.status, response.body)
        val body = tree(response.body)
        val contribution = body["contribution"]["id"].asString()
        assertEquals("accepted", body["request"]["state"].asString())
        assertEquals("requested", body["contribution"]["state"].asString())
        assertEquals(contribution, body["request"]["attributes"]["created_contribution"].asString())
        assertEquals(testProject(), body["contribution"]["attributes"]["project"].asString())
        assertTrue(response.headers.getFirst("Location")!!.endsWith("/api/objects/social_contribution/records/$contribution"))
    }

    // §2.2: COMMITMENT_MANAGER tiene el rol de accept pero no CREATE sobre social_contribution
    @Test
    fun `COMMITMENT_MANAGER no acepta una solicitud como aporte`() {
        val id = underEvaluation()
        val refused = acceptAsContribution(id, member("COMMITMENT_MANAGER"))
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("under_evaluation", state("stakeholder_request", id))
    }

    @Test
    fun `created_contribution lo escribe solo la aceptacion`() {
        val id = underEvaluation()
        val refused = edit("stakeholder_request", id, mapOf("created_contribution" to requestedContribution()), token, withReason())
        assertEquals(HttpStatus.FORBIDDEN, refused.status, refused.body)
        assertEquals("COMMAND_ONLY", code(refused))
        assertEquals(listOf("created_contribution"), problemFields(refused.body))
    }

    @Test
    fun `close_unfulfilled con el aporte rechazado pasa y con el aporte aprobado es STATE_MISMATCH`() {
        val investment = member("SOCIAL_INVESTMENT_MANAGER")
        val rejected = underEvaluation()
        val first = tree(acceptAsContribution(rejected, investment).body)["contribution"]["id"].asString()
        move("social_contribution", first, "reject", member("APPROVER").token, "no procede")
        move("stakeholder_request", rejected, "close_unfulfilled", grievances.token, "sin ejecución")
        val approved = underEvaluation()
        val second = tree(acceptAsContribution(approved, investment).body)["contribution"]["id"].asString()
        assertEquals(HttpStatus.CREATED, itemFor(second).status)
        move("social_contribution", second, "approve", member("APPROVER").token, "aprobación")
        val refused = transition("stakeholder_request", approved, "close_unfulfilled", grievances.token, "sin ejecución")
        assertEquals(HttpStatus.CONFLICT, refused.status, refused.body)
        assertEquals("STATE_MISMATCH", code(refused))
        assertEquals(listOf("created_contribution"), problemFields(refused.body))
    }
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.attention.AttentionRulesTest'
```

Esperado: FAIL en `created_contribution es un campo de comando de la solicitud` (`COMMAND_FIELDS` solo trae
`created_commitment`).

- [ ] **Paso 3: La aceptación, el campo de comando y el cierre sin ejecución**

En `src/main/kotlin/sgspe/attention/AttentionRules.kt`, después de `const val CREATED_COMMITMENT = "created_commitment"`,
agregar `const val CREATED_CONTRIBUTION = "created_contribution"`; en el comentario de `COMMAND_FIELDS`, cambiar
«created_commitment de la solicitud (Acceptances)» por «created_commitment y, desde F5, created_contribution de la
solicitud (Acceptances)», y reemplazar:

```kotlin
            Attention.RECLASSIFIED_FROM.getValue(objectName).keys + if (objectName == Attention.REQUEST) setOf(CREATED_COMMITMENT) else emptySet()
```

por:

```kotlin
            Attention.RECLASSIFIED_FROM.getValue(objectName).keys +
                if (objectName == Attention.REQUEST) setOf(CREATED_COMMITMENT, CREATED_CONTRIBUTION) else emptySet()
```

En `src/main/kotlin/sgspe/attention/Acceptances.kt`:

1. Después de `data class RequestAcceptance(...)`, agregar:

```kotlin

// desde F5 (§3.7 de la especificación de F5): la aceptación que crea un aporte social
data class ContributionAcceptance(
    val request: RecordResponse,
    val contribution: RecordResponse
)
```

2. En el comentario de `Acceptances`, reemplazar «crea el compromiso en draft con `attributes` (sigue WF-D, con su
   aprobación), guarda created_commitment» por «crea el compromiso en draft o, desde F5, el aporte en requested con
   `attributes` (siguen WF-D o WF-P, con su aprobación), guarda created_commitment o created_contribution» y borrar «el
   aporte social (creates igual a social_contribution) llega en F5: hoy es 400 INVALID_TARGET.».
3. Reemplazar la función `accept` entera por:

```kotlin
    suspend fun accept(
        id: UUID,
        body: Map<String, Any?>,
        reason: String?,
        ifMatch: String?
    ): CommandAnswer {
        val expected = IfMatch.required(ifMatch)
        StrictBody.requireOnly(body, setOf(CREATES, "response", RESPONDED_AT, "attributes"))
        val creates = Values.text(body[CREATES])
        if (creates != COMMITMENT && creates != CONTRIBUTION) {
            throw Finding(
                "INVALID_TARGET",
                "Una solicitud aceptada crea un compromiso o un aporte social (creates igual a commitment o social_contribution)",
                CREATES,
                status = HttpStatus.BAD_REQUEST
            ).toCodedException()
        }
        val attributes = StrictBody.objectOf(body, "attributes")
        StrictBody.requireAttributes(catalog, creates, attributes, setOf("project"))
        if (reason.isNullOrBlank()) throw ValidationException("Aceptar una solicitud exige un motivo", "reason", "envíe la cabecera X-Change-Reason")
        val user = callers.require()
        permissions.require(user, "Aceptar una solicitud", Actions.UPDATE to Attention.REQUEST, Actions.CREATE to creates)
        TransitionRoles.require(catalog, user, Attention.REQUEST, listOf(ACCEPT))
        val field = if (creates == COMMITMENT) AttentionRules.CREATED_COMMITMENT else AttentionRules.CREATED_CONTRIBUTION
        val (answer, accepted, created) =
            transactions.locked("${Attention.REQUEST}:$id") {
                val request = records.get(Attention.REQUEST, id)
                versions.check(Attention.REQUEST, id, expected, request.updatedAt, "La solicitud")
                if (request.state != UNDER_EVALUATION) {
                    throw Finding("STATE_MISMATCH", "Solo se acepta una solicitud en evaluación; esta está en ${request.state}", "record").toCodedException()
                }
                // la respuesta primero: AttentionWriteGuard exige response y responded_at para aplicar accept. un respondedAt mal
                // formado lo rechaza la validación de wasichai sobre responded_at: el 400 nombra la propiedad del cuerpo
                try {
                    records.patch(Attention.REQUEST, id, mapOf("response" to body["response"], Attention.RESPONDED_AT to body[RESPONDED_AT]), reason)
                } catch (e: ValidationException) {
                    throw bodyFields(e)
                }
                // el compromiso nace en draft y el aporte en requested; quien acepta es su created_by (P5 de WF-P, D4 de WF-D)
                val record = records.create(creates, attributes + ("project" to request.attributes["project"]), reason)
                records.patch(Attention.REQUEST, id, mapOf(field to record.id), reason)
                records.transition(Attention.REQUEST, id, ACCEPT, reason)
                val acceptedRequest = records.get(Attention.REQUEST, id)
                val createdRecord = records.get(creates, UUID.fromString(record.id))
                val answer: Any =
                    if (creates == COMMITMENT) RequestAcceptance(acceptedRequest, createdRecord) else ContributionAcceptance(acceptedRequest, createdRecord)
                Triple(answer, acceptedRequest, createdRecord)
            }
        return CommandAnswer(HttpStatus.CREATED, answer, accepted.updatedAt, "/api/objects/$creates/records/${created.id}")
    }
```

4. En el `companion object`, después de `const val COMMITMENT = "commitment"`, agregar
   `const val CONTRIBUTION = "social_contribution"`.

En `src/main/kotlin/sgspe/attention/AttentionWriteGuard.kt`, reemplazar el bloque de `close_unfulfilled`:

```kotlin
        if (change.objectName == Attention.REQUEST && name == AttentionRules.CLOSE_UNFULFILLED) {
            val commitment = Records.uuid(record[AttentionRules.CREATED_COMMITMENT])?.let { records.readable(COMMITMENT, it) }
            if (commitment?.state != CANCELLED) {
                findings +=
                    Finding(
                        "STATE_MISMATCH",
                        "close_unfulfilled cierra una solicitud cuyo compromiso está cancelado; el suyo está en ${commitment?.state ?: "ninguno"}",
                        AttentionRules.CREATED_COMMITMENT
                    )
            }
        }
```

por:

```kotlin
        if (change.objectName == Attention.REQUEST && name == AttentionRules.CLOSE_UNFULFILLED) findings += unfulfilled(record)
```

y agregar, antes de `holds`:

```kotlin
    // close_unfulfilled (ASM-068; §3.7 de la especificación de F5): con el aporte creado, si lo hay, en rejected o cancelled; si
    // no, con el compromiso creado cancelado. un tenant sin el modelo de F5 no tiene created_contribution: juzga el compromiso
    private suspend fun unfulfilled(record: Map<String, Any?>): List<Finding> {
        val contribution = Records.uuid(record[AttentionRules.CREATED_CONTRIBUTION])
        if (contribution != null) {
            val state = records.readable(CONTRIBUTION, contribution).state
            if (state in CONTRIBUTION_UNFULFILLED) return emptyList()
            return listOf(
                Finding(
                    "STATE_MISMATCH",
                    "close_unfulfilled cierra una solicitud cuyo aporte está rechazado o anulado; el suyo está en ${state ?: "ninguno"}",
                    AttentionRules.CREATED_CONTRIBUTION
                )
            )
        }
        val commitment = Records.uuid(record[AttentionRules.CREATED_COMMITMENT])?.let { records.readable(COMMITMENT, it) }
        if (commitment?.state == CANCELLED) return emptyList()
        return listOf(
            Finding(
                "STATE_MISMATCH",
                "close_unfulfilled cierra una solicitud cuyo compromiso está cancelado; el suyo está en ${commitment?.state ?: "ninguno"}",
                AttentionRules.CREATED_COMMITMENT
            )
        )
    }
```

y en el `companion object` agregar:

```kotlin
        const val CONTRIBUTION = "social_contribution"

        // el aporte que no se ejecutará: rechazado o anulado (WF-P)
        val CONTRIBUTION_UNFULFILLED = setOf("rejected", "cancelled")
```

En el comentario de la clase, cambiar «el cierre sin ejecución (close_unfulfilled con el compromiso cancelado)» por «el
cierre sin ejecución (close_unfulfilled con el compromiso cancelado o, desde F5, el aporte rechazado o anulado)».

En `src/test/kotlin/sgspe/investment/InvestmentModelApiTest.kt` (plan 5.1, «los vinculos de F5 en objetos anteriores
existen en el tenant»), la solicitud escribía `created_contribution` por la API genérica, que desde aquí es
`403 COMMAND_ONLY`. Reemplazar:

```kotlin
        // una tarea con un programa de origen (ExactlyOneGuard) y una solicitud que nombra el aporte creado
        createRecord("task", mapOf("project" to project, "title" to "Preparar la evaluación del programa", "social_program" to program))
        val request = stakeholderRequest(project, attributes = mapOf("created_contribution" to contribution))
        assertEquals(contribution, record("stakeholder_request", request)["attributes"]["created_contribution"].asString())
```

por:

```kotlin
        // una tarea con un programa de origen (ExactlyOneGuard). created_contribution existe en el tenant, pero es campo de
        // comando desde el plan 5.2a: lo escribe la aceptación como aporte (StakeholderRequestWorkflowApiTest), no esta prueba
        createRecord("task", mapOf("project" to project, "title" to "Preparar la evaluación del programa", "social_program" to program))
        assertTrue("created_contribution" in tree(send("GET", "/api/metadata/objects/stakeholder_request/fields", null, HttpStatus.OK)).values().map { it["name"].asString() })
```

(si `assertTrue` o `tree` no están importados en la clase, se importan; `contribution` sigue usándose en las M2M de
evidencias).

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `AttentionRulesTest` y `AcceptancesTest` en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/Acceptances.kt src/main/kotlin/sgspe/attention/AttentionRules.kt \
  src/main/kotlin/sgspe/attention/AttentionWriteGuard.kt src/test/kotlin/sgspe/attention/AttentionRulesTest.kt \
  src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt \
  src/test/kotlin/sgspe/investment/InvestmentModelApiTest.kt
git commit -F - <<'MSG'
feat: aceptar una solicitud como aporte social

La aceptación con creates = social_contribution crea el aporte en requested, guarda
created_contribution (campo de comando) y responde { request, contribution }; exige CREATE sobre el
aporte. close_unfulfilled admite el aporte rechazado o anulado.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `StakeholderRequestWorkflowApiTest` (todos sus casos, también los de F4, sobre
la base nueva) e `InvestmentModelApiTest`.

---

### Tarea 12: Cerrar el aporte cierra la solicitud que lo originó

**Files:**
- Modify: `src/main/kotlin/sgspe/attention/RequestClosureListener.kt`
- Test: `src/test/kotlin/sgspe/attention/RequestClosureListenerTest.kt`
- Test: `src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt` (casos al final, integración)

**Interfaces:**
- Consumes: `Acceptances.COMMITMENT`, `Acceptances.CONTRIBUTION`, `AttentionRules.CREATED_COMMITMENT`,
  `AttentionRules.CREATED_CONTRIBUTION` (Tarea 11); `acceptAsContribution` y la base `ContributionApiTest` en
  `StakeholderRequestWorkflowApiTest` (Tarea 11); `captureDelivery`, `deliveryEvidence`, `itemFor` (Tarea 1); P8
  (Tarea 10).
- Produces: `RequestClosureListener.ORIGINS: Map<String, Pair<String, String>>` (objeto → campo de la solicitud y nombre
  en el motivo); el motivo «RN-GRV-001: aporte <código> cerrado».

§3.7 y elección 13: en `close` de WF-P, como en el de WF-D, el listener abre
`ConfidentialRelease.within(stakeholder_request, created_contribution, <aporte>)` y aplica `close_accepted` a la solicitud
aceptada con ese `created_contribution`, con el origen `listener:request-closure`; si no puede, el cierre del aporte se
revierte. Un tenant sin `stakeholder_request` no busca. `ConfidentialRelease` sigue solo en este archivo
(`ArchitectureTest`).

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/attention/RequestClosureListenerTest.kt`, agregar antes de la última llave:

```kotlin

    // F5 (§3.7): el cierre de un aporte busca las solicitudes aceptadas que lo crearon, por created_contribution
    @Test
    fun `cerrar un aporte busca las solicitudes aceptadas por created_contribution`() {
        val filters = mutableListOf<Map<*, *>>()
        val capturing: Records =
            mock(Records::class.java) { invocation ->
                if (invocation.method.name != "all") error("lectura inesperada: ${invocation.method.name}")
                filters += invocation.arguments[1] as Map<*, *>
                emptyList<Any>()
            }
        val contribution = close.copy(objectName = Acceptances.CONTRIBUTION)
        runBlocking { RequestClosureListener(capturing, model(withF4 = true)).recordChanged(contribution) }
        assertEquals(listOf(mapOf(AttentionRules.CREATED_CONTRIBUTION to contribution.recordId.toString())), filters)
    }

    @Test
    fun `otra transicion u otro objeto no buscan solicitudes`() {
        runBlocking { RequestClosureListener(records, model(withF4 = true)).recordChanged(close.copy(transition = "register_delivery")) }
        runBlocking { RequestClosureListener(records, model(withF4 = true)).recordChanged(close.copy(objectName = "agreement")) }
        assertEquals(emptyList<String>(), reads)
    }
```

En `src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt`, agregar antes del `companion object`:

```kotlin

    // ---- F5: el cierre del aporte cierra la solicitud (RN-GRV-001, §3.7 de la especificación de F5) ----

    // lleva el aporte creado al aceptar hasta closed: un ítem, la aprobación de un APPROVER, la adquisición, la entrega con
    // sus tres evidencias (P8), register_delivery y close por `closer`
    private fun closeContribution(
        contribution: String,
        closer: Account
    ) {
        assertEquals(HttpStatus.CREATED, itemFor(contribution).status)
        move("social_contribution", contribution, "approve", member("APPROVER").token, "aprobación")
        move("social_contribution", contribution, "register_procurement", reason = "compra")
        val captured = captureDelivery(contribution, evidence = deliveryEvidence())
        assertEquals(HttpStatus.CREATED, captured.status, captured.body)
        move("social_contribution", contribution, "register_delivery", reason = "entregado")
        move("social_contribution", contribution, "close", closer.token, "cerrado")
    }

    private fun closingByContribution(id: String): JsonNode {
        val history = tree(send("GET", "/api/objects/stakeholder_request/records/$id/history", null, HttpStatus.OK))
        return history.values().firstOrNull { it["reason"]?.asString()?.startsWith("RN-GRV-001: aporte") == true }
            ?: error("sin la entrada del cierre automático por el aporte: $history")
    }

    @Test
    fun `cerrar el aporte creado cierra la solicitud, como accion del sistema y con el motivo`() {
        val id = underEvaluation()
        val contribution = tree(acceptAsContribution(id, member("SOCIAL_INVESTMENT_MANAGER")).body)["contribution"]["id"].asString()
        closeContribution(contribution, member("SOCIAL_MANAGER"))
        assertEquals("closed", state("social_contribution", contribution))
        assertEquals("closed", state("stakeholder_request", id))
        assertEquals(RequestClosureListener.SOURCE, closingByContribution(id)["source"].asString())
    }

    // decisión 4 del plan 4.2a con el aporte: el cierre automático no depende de que quien cierra el aporte lea la solicitud
    @Test
    fun `una solicitud confidencial se cierra con su aporte aunque quien lo cierra no la lea`() {
        val id = stakeholderRequest(testProject(), member("COMMUNITY_RELATIONS_OFFICER"))
        move("stakeholder_request", id, "start_evaluation", grievances.token, "evaluación")
        val contribution = tree(acceptAsContribution(id, member("SOCIAL_INVESTMENT_MANAGER")).body)["contribution"]["id"].asString()
        platformPatch("stakeholder_request", id, mapOf("confidential" to true))
        val closer = member("SOCIAL_INVESTMENT_MANAGER")
        val path = "/api/objects/stakeholder_request/records/$id"
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, closer.token).status)
        closeContribution(contribution, closer)
        assertEquals("closed", state("stakeholder_request", id))
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", path, null, closer.token).status)
    }
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.attention.RequestClosureListenerTest'
```

Esperado: FAIL en `cerrar un aporte busca…` (el listener solo escucha `commitment`: `filters` queda vacío).

- [ ] **Paso 3: El listener con los dos orígenes**

En `src/main/kotlin/sgspe/attention/RequestClosureListener.kt`, reemplazar el comentario de la clase desde
«RequestClosureListener (RN-GRV-001, ASM-068, workflows.md WF-E3): en la transacción de `close` de WF-D» por
«RequestClosureListener (RN-GRV-001, ASM-068, workflows.md WF-E3): en la transacción de `close` de WF-D y, desde F5, de
WF-P», y «con el motivo «RN-GRV-001: compromiso <código> cerrado»» por «con el motivo «RN-GRV-001: compromiso <código>
cerrado» o «RN-GRV-001: aporte <código> cerrado»». Reemplazar `recordChanged` entera por:

```kotlin
    override suspend fun recordChanged(change: RecordChange) {
        if (change.kind != RecordChangeKind.TRANSITIONED || change.transition != CLOSE) return
        val (field, noun) = ORIGINS[change.objectName] ?: return
        if (!tenant.has(change.organizationId, Attention.REQUEST)) return
        val code = Values.text((change.after ?: change.before).orEmpty()["code"]) ?: change.recordId.toString()
        ConfidentialRelease.within(Attention.REQUEST, field, change.recordId) {
            ChangeOrigin.within(SOURCE) {
                val requests =
                    records.all(
                        Attention.REQUEST,
                        filters = mapOf(field to change.recordId.toString()),
                        criteria = listOf(Criteria.inState(listOf(ACCEPTED)))
                    )
                for (request in requests) {
                    records.transition(Attention.REQUEST, UUID.fromString(request.id), CLOSE_ACCEPTED, "RN-GRV-001: $noun $code cerrado")
                }
            }
        }
    }
```

y en el `companion object` agregar:

```kotlin
        // lo que una solicitud aceptada crea y cierra al cerrarse (WF-D y, desde F5, WF-P): el campo de la solicitud que lo apunta
        // y cómo lo nombra el motivo
        val ORIGINS: Map<String, Pair<String, String>> =
            mapOf(
                Acceptances.COMMITMENT to (AttentionRules.CREATED_COMMITMENT to "compromiso"),
                Acceptances.CONTRIBUTION to (AttentionRules.CREATED_CONTRIBUTION to "aporte")
            )
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `RequestClosureListenerTest` con 4 pruebas en verde; `ArchitectureTest` en verde
(`ConfidentialRelease.within` sigue solo en este archivo).

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/attention/RequestClosureListener.kt src/test/kotlin/sgspe/attention/RequestClosureListenerTest.kt \
  src/test/kotlin/sgspe/attention/StakeholderRequestWorkflowApiTest.kt
git commit -F - <<'MSG'
feat: cerrar un aporte cierra la solicitud que lo originó

RequestClosureListener también escucha close de WF-P: aplica close_accepted a la solicitud aceptada
con ese created_contribution, como acción del sistema y también si es confidencial (RN-GRV-001).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `StakeholderRequestWorkflowApiTest` (todos sus casos).

---

### Tarea 13: El aviso de aprobación de WF-P al alta

**Files:**
- Modify: `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`
- Modify: `src/main/kotlin/sgspe/notification/Notices.kt` (`NoticeTexts.approvalPending`)
- Test: `src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt`
- Test: `src/test/kotlin/sgspe/notification/ContributionApprovalNoticesApiTest.kt` (crear, integración)

**Interfaces:**
- Consumes: `ContributionApiTest` (Tarea 1); `Assignments.recipients(project, objectName, transition, exclude)`,
  `Notices.publish`/`resolve`, `ModelCatalog.transition`, `ProjectParameters.policy`, `Records.readable`, `NotReadable`.
- Produces: `data class ApprovalStep(objectName: String, submit: String?, pending: String, approvals: List<String>,
  direct: List<String> = emptyList())` (`submit` null: el aviso sale con el alta); `ApprovalNoticeListener(notices,
  assignments, catalog, senior, records)`; `ApprovalNoticeListener.STEPS` con WF-P; el texto «Aporte por aprobar». El
  plan 5.2b suma WF-G a `STEPS` y su etiqueta.

§3.8 y elección 14: al alta de un `social_contribution` (por la API genérica o por la aceptación), un aviso
`approval_pending:social_contribution:<id>` a los asignados con un rol de `approve` y, con `policy_internal_approval` en
falso, también de `approve_direct`, salvo quien lo crea; se resuelve con cualquier transición que sale de `requested`. El
texto lleva el código y el tipo, nunca propósito, condiciones ni receptor. Si quien crea no lee el proyecto, el aviso va a
la unión de los roles (decisión 11).

- [ ] **Paso 1: Escribir las pruebas que fallan**

En `src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt`:

1. En la primera prueba, reemplazar `ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, senior)` por
   `ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, senior, mock(Records::class.java))` y agregar
   el import `sgspe.common.Records`.
2. Reemplazar la prueba `cada paso de STEPS existe en model workflows con su estado pendiente y sus transiciones` entera
   por:

```kotlin
    @Test
    fun `cada paso de STEPS existe en model workflows con su estado pendiente y sus transiciones`() {
        val mapper = JsonMapper.builder().build()
        for (step in ApprovalNoticeListener.STEPS) {
            val file = File("model/workflows/${step.objectName}.json")
            assertTrue(file.exists(), "falta ${file.path}")
            val definition = mapper.readTree(file).path("definition")
            val transitions = definition.path("transitions").map { it }
            if (step.submit == null) {
                // el aviso sale con el alta: el estado pendiente es el inicial del workflow
                val initial = definition.path("states").map { it }.firstOrNull { it.path("type").asString() == "INITIAL" }
                assertEquals(step.pending, initial?.path("name")?.asString(), "${step.objectName}: estado inicial")
            } else {
                val submit = transitions.firstOrNull { it.path("name").asString() == step.submit }
                assertTrue(submit != null, "${step.objectName}: falta la transición ${step.submit}")
                assertEquals(step.pending, submit!!.path("to").asString(), "${step.objectName}: estado pendiente")
            }
            for (approval in step.approvals + step.direct) {
                val t = transitions.firstOrNull { it.path("name").asString() == approval }
                assertTrue(t != null, "${step.objectName}: falta la transición $approval")
                assertEquals(step.pending, t!!.path("from").asString(), "${step.objectName}: $approval sale del pendiente")
            }
        }
    }
```

3. Agregar antes de la última llave:

```kotlin

    // elección 14 de F5: el alta de un aporte avisa a los roles de approve y, sin aprobación interna, también a los de
    // approve_direct; si quien crea no lee el proyecto, a la unión (decisión 11 del plan 5.2a)
    @Test
    fun `el alta de un aporte avisa a approve y, sin aprobacion interna o sin leer el proyecto, tambien a approve_direct`() {
        val project = UUID.randomUUID()
        var projectAttributes: Map<String, Any?>? = emptyMap()
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
        val records =
            mock(Records::class.java) { call: InvocationOnMock ->
                if (call.method.name == "find") projectAttributes?.let { RecordResponse(id = project.toString(), createdAt = null, updatedAt = null, attributes = it) } else null
            }
        val listener = ApprovalNoticeListener(mock(Notices::class.java), assignments, catalog, mock(SeniorApprovalReader::class.java), records)
        val created =
            RecordChange(
                organizationId = UUID.randomUUID(),
                userId = UUID.randomUUID(),
                objectId = UUID.randomUUID(),
                objectName = "social_contribution",
                recordId = UUID.randomUUID(),
                kind = RecordChangeKind.CREATED,
                after = mapOf("project" to project.toString(), "code" to "APO-001", "contribution_type" to "DONACION")
            )
        runBlocking { listener.recordChanged(created) }
        assertEquals(listOf("approve"), asked)
        asked.clear()
        projectAttributes = mapOf("policy_internal_approval" to false)
        runBlocking { listener.recordChanged(created) }
        assertEquals(listOf("approve", "approve_direct"), asked)
        asked.clear()
        projectAttributes = null
        runBlocking { listener.recordChanged(created) }
        assertEquals(listOf("approve", "approve_direct"), asked)
        assertEquals("APO-001 · DONACION", ApprovalNoticeListener.label("social_contribution", created.after!!))
    }
```

con los imports `wasichai.core.data.RecordResponse`.

Crear `src/test/kotlin/sgspe/notification/ContributionApprovalNoticesApiTest.kt`:

```kotlin
package sgspe.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.ContributionApiTest

// el aviso «aprobación pendiente» de un aporte (§3.8 de la especificación de F5): llega al alta a los asignados con un rol de
// approve (y de approve_direct si el proyecto no tiene aprobación interna), nunca a quien lo registró, y se resuelve al salir
// de requested
class ContributionApprovalNoticesApiTest : ContributionApiTest() {
    private fun pendingFor(
        token: String,
        id: String
    ): Boolean =
        tree(send("GET", "/api/auth/me/notifications?state=active", null, HttpStatus.OK, token))["content"].values().any {
            it["link"]?.get("recordId")?.asString() == id && it["kind"].asString() == "ACTION"
        }

    @Test
    fun `registrar un aporte avisa a quienes lo aprueban y aprobarlo lo resuelve`() {
        val project = testProject()
        val approver = member("APPROVER", project)
        val manager = member("SOCIAL_MANAGER", project)
        val direct = member("SOCIAL_INVESTMENT_MANAGER", project)
        val id = requestedContribution(project)
        assertTrue(pendingFor(approver.token, id))
        assertTrue(pendingFor(manager.token, id))
        assertFalse(pendingFor(direct.token, id), "con aprobación interna, approve_direct no aplica")
        assertFalse(pendingFor(registrar(project).token, id), "quien lo registra no recibe su aviso")
        move("social_contribution", id, "approve", approver.token, "aprobación")
        assertFalse(pendingFor(manager.token, id))
    }

    @Test
    fun `sin aprobacion interna avisa tambien a los roles de approve_direct`() {
        val project = project(mapOf("policy_internal_approval" to false))
        val direct = member("SOCIAL_INVESTMENT_MANAGER", project)
        val id = requestedContribution(project)
        assertTrue(pendingFor(direct.token, id))
        move("social_contribution", id, "reject", member("APPROVER", project).token, "no procede")
        assertFalse(pendingFor(direct.token, id))
    }
}
```

- [ ] **Paso 2: Correr las unitarias para verlas fallar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.notification.ApprovalNoticeListenerTest'
```

Esperado: FAIL en la compilación (`ApprovalNoticeListener` no recibe `records`; `ApprovalStep` no tiene `direct`).

- [ ] **Paso 3: El modo «al crear»**

En `src/main/kotlin/sgspe/notification/ApprovalNotices.kt`:

1. Reemplazar `data class ApprovalStep(...)` (con su comentario) por:

```kotlin
// un envío a aprobación y lo que lo resuelve (workflows.md, «Avisos» y «Destinatarios por rol»). `submit` null: el aviso sale
// con el alta del registro (WF-P, elección 14 de la especificación de F5) y `pending` es el estado inicial. `direct`: las
// transiciones cuyos roles también reciben el aviso si el proyecto no tiene aprobación interna (approve_direct de WF-P)
data class ApprovalStep(
    val objectName: String,
    val submit: String?,
    val pending: String,
    val approvals: List<String>,
    val direct: List<String> = emptyList()
)
```

2. El constructor suma `private val records: Records` (después de `senior`) y `recordChanged` queda:

```kotlin
    override suspend fun recordChanged(change: RecordChange) {
        val step = STEPS.firstOrNull { it.objectName == change.objectName } ?: return
        when (change.kind) {
            RecordChangeKind.CREATED -> if (step.submit == null) publish(step, change)
            RecordChangeKind.TRANSITIONED -> {
                val name = change.transition ?: return
                when {
                    step.submit != null && name == step.submit -> publish(step, change)
                    catalog.transition(step.objectName, name)?.from == step.pending ->
                        notices.resolve(change.organizationId, Notices.APPROVALS, key(step, change.recordId))
                }
            }
            else -> Unit
        }
    }
```

3. Reemplazar `approvals` entera (con su comentario) por:

```kotlin
    // las transiciones cuyos roles reciben el aviso. un compromiso que exige nivel superior, solo approve_senior; si quien envió
    // no puede leer lo que la regla necesita (NotReadable), no se sabe qué nivel exige: el aviso va a la unión de los roles de
    // approve y de approve_senior, nunca a nadie ni solo a uno de los dos. un paso con `direct` (WF-P) suma sus roles si el
    // proyecto no tiene aprobación interna; si quien crea no lee el proyecto, también los suma (decisión 11 del plan 5.2a).
    // quien no corresponde recibe un aviso de más, que la guarda de la aprobación deja sin efecto; callar dejaría el registro
    // sin aprobador que lo sepa
    private suspend fun approvals(
        step: ApprovalStep,
        id: UUID,
        attributes: Map<String, Any?>
    ): List<String> {
        if (step.objectName == Commitments.COMMITMENT) return commitmentApprovals(step, id, attributes)
        if (step.direct.isEmpty()) return step.approvals
        val project = Records.uuid(attributes[PROJECT]) ?: return step.approvals + step.direct
        val internal =
            try {
                ProjectParameters.policy(records.readable(PROJECT, project).attributes, INTERNAL_APPROVAL)
            } catch (e: NotReadable) {
                log.warn("ApprovalNoticeListener: quien registró {} {} no lee su proyecto; el aviso va también a {}", step.objectName, id, step.direct)
                return step.approvals + step.direct
            }
        return if (internal) step.approvals else step.approvals + step.direct
    }

    private suspend fun commitmentApprovals(
        step: ApprovalStep,
        id: UUID,
        attributes: Map<String, Any?>
    ): List<String> {
        val required =
            try {
                senior.required(id, attributes)
            } catch (e: NotReadable) {
                log.warn(
                    "ApprovalNoticeListener: nivel de {} {} no evaluable ({}); el aviso va a todos los roles de {}",
                    step.objectName,
                    id,
                    e.message,
                    step.approvals
                )
                return step.approvals
            }
        return if (required) listOf(SENIOR) else step.approvals
    }
```

4. En el `companion object`: agregar `const val CONTRIBUTION = "social_contribution"` y
   `const val INTERNAL_APPROVAL = "policy_internal_approval"`; sumar a `STEPS`
   `ApprovalStep(CONTRIBUTION, null, "requested", listOf("approve"), direct = listOf("approve_direct"))`; y en `label`
   agregar la rama `CONTRIBUTION -> listOfNotNull(Values.text(attributes["code"]), Values.text(attributes["contribution_type"]))`
   (el código y el tipo: nunca propósito, condiciones ni receptor).
5. Sumar los imports `sgspe.common.ProjectParameters` y `sgspe.common.readable`; en el comentario de la clase, cambiar
   «al enviar un proyecto, un acta o un compromiso a aprobación» por «al enviar un proyecto, un acta o un compromiso a
   aprobación y al registrar un aporte (F5)».

En `src/main/kotlin/sgspe/notification/Notices.kt`, en `NoticeTexts.approvalPending`, agregar antes de `else`:

```kotlin
            "social_contribution" -> "Aporte por aprobar: $label" to "El aporte espera su aprobación: revise sus ítems y los lineamientos."
```

- [ ] **Paso 4: Correr las pruebas para verlas pasar**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
```

Esperado: `BUILD SUCCESSFUL`; `ApprovalNoticeListenerTest` con 3 pruebas en verde.

- [ ] **Paso 5: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/notification/ApprovalNotices.kt src/main/kotlin/sgspe/notification/Notices.kt \
  src/test/kotlin/sgspe/notification/ApprovalNoticeListenerTest.kt src/test/kotlin/sgspe/notification/ContributionApprovalNoticesApiTest.kt
git commit -F - <<'MSG'
feat: aviso de aprobación pendiente al registrar un aporte

ApprovalNoticeListener avisa al alta de un aporte a los roles de approve y, sin aprobación interna,
también a los de approve_direct; el aviso se resuelve al salir de requested.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `ContributionApprovalNoticesApiTest` y `ApprovalNoticesApiTest` (sin cambios).

---

### Tarea 14: WF-P y WF-E3 en la matriz de workflows con las guardas de 5.2a

**Files:**
- Modify: `src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt` (`ROLE_DEFERRED`, `create` si hace falta, `prepare`,
  `accept`, comentario de la clase)

**Interfaces:**
- Consumes: `WorkflowMatrixApiTest` sobre `F5ApiTest` con WF-P y `ROLE_DEFERRED` (plan 5.1, decisión 8 de su plan);
  `F5ApiTest.socialContribution` y `F5ApiTest.contributionDelivery` (plan 5.1); P5, P8 y P9 (Tareas 4 y 10); el endpoint
  de entrega (Tarea 9); la aceptación como aporte (Tarea 11); `Model.roles`, `organization`, `ifMatch`, `exchange`.
- Produces: la matriz de WF-P en verde con las guardas de 5.2a, y `accept` de WF-E3 por cada rol de su lista, también
  `SOCIAL_INVESTMENT_MANAGER`, que crea un aporte.

El plan 5.1 dejó la matriz sin guardas de WF-P y difirió solo «`SOCIAL_INVESTMENT_MANAGER` aplica `accept`»
(`ROLE_DEFERRED`), a la espera de la aceptación como aporte; y pidió a 5.2a que, cuando exista P8, `prepare` registre la
entrega de `register_delivery` con `contributionDelivery`. Con las guardas de este plan, además, `approve` exige que no
apruebe quien registró el aporte (P5) y un ítem con su tipo (P9).

- [ ] **Paso 1: Las ramas de la matriz**

1. En `ROLE_DEFERRED`, quitar la entrada del caso «`SOCIAL_INVESTMENT_MANAGER` aplica `accept`» (si el conjunto queda
   vacío, se deja vacío y su comentario dice «ninguno desde el plan 5.2a»).
2. En `create`, comprobar que la rama de `social_contribution` registra el aporte con `socialContribution` de `F5ApiTest`
   (un ítem BIEN y `contribution_type`, P9) y con una cuenta que nunca aplica `approve` (P5). Si la registra con
   `actor(workflow, creatorRole, null)` y ese rol puede ser `SOCIAL_MANAGER` o `APPROVER`, la rama queda:

```kotlin
            // F5: un aporte que cumple P9 (socialContribution de F5ApiTest: un ítem BIEN y su tipo), registrado por una cuenta
            // propia, un SOCIAL_INVESTMENT_MANAGER asignado que nunca lo aprueba con approve (P5, plan 5.2a)
            "social_contribution" -> {
                val contributor = accounts.getOrPut("\u0000contributor") { userWithRole("SOCIAL_INVESTMENT_MANAGER") }
                if (assigned.add("\u0000contributor" to testProject())) assign(contributor, testProject(), "SOCIAL_INVESTMENT_MANAGER")
                socialContribution(testProject(), contributor)
            }
```

3. En `prepare`, reemplazar la rama de `stakeholder_request.close_unfulfilled` por:

```kotlin
        if (workflow.key(rule) == "stakeholder_request.close_unfulfilled" && state(workflow.objectName, id) == rule.from) {
            val attributes = record(workflow.objectName, id)["attributes"]
            val contribution = attributes["created_contribution"]?.takeUnless { it.isNull }?.asString()
            if (contribution != null) {
                move("social_contribution", contribution, "reject", reason = TEXT)
            } else {
                move("commitment", attributes["created_commitment"].asString(), "cancel_draft", reason = TEXT)
            }
            return
        }
        // P8 (plan 5.2a): la entrega del aporte con conformidad y sus tres evidencias, por el comando de entrega
        if (workflow.key(rule) == "social_contribution.register_delivery" && state(workflow.objectName, id) == rule.from) {
            contributionDelivery(id, testProject())
            return
        }
```

   y en su comentario agregar «; en WF-P (plan 5.2a), la entrega del aporte con sus tres evidencias (P8)». Si
   `contributionDelivery` de `F5ApiTest` no captura por `POST /api/sgspe/social-contributions/{id}/deliveries` (la
   plataforma no vincula evidencias con su candado), se usa en su lugar:

```kotlin
            val evidence = listOf("ACTA DE RECEPCION", "FOTO", "COMPROBANTE").map { evidence(testProject(), it) }
            val attributes = mapOf("delivery_date" to "2026-10-01", "conformity_given" to true, "conformity_by" to "Junta directiva")
            val delivery =
                exchange(
                    "POST",
                    "/api/sgspe/social-contributions/$id/deliveries",
                    mapOf("attributes" to attributes, "evidence" to evidence),
                    token,
                    ifMatch("social_contribution", id) + withReason(TEXT)
                )
            assertEquals(HttpStatus.CREATED, delivery.status, delivery.body)
```

4. Reemplazar la función `accept` entera (con su comentario) por:

```kotlin
    // la aceptación de una solicitud (POST /api/sgspe/stakeholder-requests/{id}/accept): su respuesta y lo que crea quien
    // acepta, según sus permisos (§2.2 de la especificación de F5): el compromiso en draft o, a quien no crea compromisos
    // (SOCIAL_INVESTMENT_MANAGER), el aporte en requested
    private fun accept(
        id: String,
        who: Account
    ): Response {
        val role = accounts.entries.first { it.value == who }.key
        val commitment = "CREATE" in Model.roles.getValue(role)["commitment"].orEmpty()
        val attributes =
            if (commitment) {
                mapOf("code" to uniqueName("COM-"), "title" to "Materiales para el canal de riego", "amount" to 15000, "currency" to "PEN")
            } else {
                mapOf(
                    "code" to uniqueName("APO-"),
                    "contribution_type" to "APOYO EN ESPECIE",
                    "declared_purpose" to "Útiles para la escuela.",
                    "conditions" to "Con acta de recepción.",
                    "currency" to "PEN",
                    "payer_organization" to organization()
                )
            }
        return exchange(
            "POST",
            "/api/sgspe/stakeholder-requests/$id/accept",
            mapOf(
                "creates" to if (commitment) "commitment" else "social_contribution",
                "response" to "Se atenderá con lo que se crea.",
                "respondedAt" to "2026-10-08T15:00:00Z",
                "attributes" to attributes
            ),
            who.token,
            ifMatch("stakeholder_request", id, who.token) + withReason(TEXT)
        )
    }
```

5. En el comentario de la clase, agregar al final «; los aportes de WF-P traen un ítem y su tipo, los registra una cuenta
   que no los aprueba y su entrega lleva las tres evidencias (plan 5.2a)».

- [ ] **Paso 2: Compilar y comprobar la clase**

```bash
cd ~/IdeaProjects/sgspe && GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
grep -n 'ROLE_DEFERRED' -A4 src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt | head
grep -n 'social_contribution.register_delivery\|"creates" to if' src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
```

Esperado: `BUILD SUCCESSFUL`; `ROLE_DEFERRED` ya no nombra `SOCIAL_INVESTMENT_MANAGER`; las ramas de `prepare` y de
`accept` aparecen.

- [ ] **Paso 3: Commit**

```bash
cd ~/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/workflow/WorkflowMatrixApiTest.kt
git commit -F - <<'MSG'
test: matriz de WF-P y WF-E3 con las guardas de aportes

La aceptación de WF-E3 crea un aporte cuando quien acepta no crea compromisos, así que el caso de
SOCIAL_INVESTMENT_MANAGER deja de estar diferido; la entrega de register_delivery lleva acta de
recepción, foto y comprobante (P8) y el aporte lo registra una cuenta que no lo aprueba (P5).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
MSG
```

**Integración pendiente (coordinador):** `WorkflowMatrixApiTest` (entera; con el túnel, entre 11 y 58 minutos).

---

### Tarea 15: Verificación final, push y PR

**Files:**
- Ninguno nuevo. Verifica la rama `feat/fase-5-2a-aportes` con las Tareas 1 a 14 integradas.

**Interfaces:**
- Consumes: todo lo anterior.
- Produces: el PR de `feat/fase-5-2a-aportes` contra `main`.

- [ ] **Paso 1: Build completo y vigías**

```bash
cd ~/IdeaProjects/sgspe && git switch feat/fase-5-2a-aportes && git status --short
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat build
grep -rnE '"(SOCIAL_MANAGER|SOCIAL_INVESTMENT_MANAGER|APPROVER|COMMUNITY_RELATIONS_OFFICER|COMMITMENT_MANAGER)"' src/main/kotlin | head
grep -rn "sgspe.investment" src/main/kotlin/sgspe/commitment src/main/kotlin/sgspe/evidence src/main/kotlin/sgspe/attention src/main/kotlin/sgspe/scope src/main/kotlin/sgspe/common | head
cd model && python3 -m unittest -v 2>&1 | tail -3 && cd ..
python3 tools/check_traceability.py
```

Esperado: `BUILD SUCCESSFUL` (`ArchitectureTest` incluida); los dos `grep` sin salida; las pruebas del modelo en `OK`;
`check_traceability.py` sin errores. Si un `grep` encuentra algo, se corrige en la tarea dueña antes de seguir.

- [ ] **Paso 2: Lista de integración para el coordinador**

Las clases de integración nuevas o cambiadas, para correr juntas (o dejarlas a la CI):
`SocialContributionWorkflowApiTest`, `StakeholderRequestWorkflowApiTest`, `CampaignCapApiTest`,
`ContributionTotalsApiTest`, `ContributionApprovalNoticesApiTest`, `AmountFieldsF5ApiTest`,
`ProjectLinkGuardF5ApiTest`, `WorkflowMatrixApiTest`, y las que vigilan que nada de F3 y F4 cambió:
`SupportPolicyApiTest`, `CampaignExecutionApiTest`, `DeliveryApiTest`, `EvidenceApiTest`, `ApprovalNoticesApiTest`,
`TaskWorkflowApiTest`, `AutomationsApiTest`, `DeadlineJobQcsApiTest`.

- [ ] **Paso 3: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin feat/fase-5-2a-aportes
gh pr create --base main --head feat/fase-5-2a-aportes --title "feat: aportes sociales, tope, solicitudes y avisos (plan 5.2a)" --body-file - <<'BODY'
## Qué trae

- `InvestmentWriteGuard` con las reglas de WF-P en el orden de la especificación de F5: candado del aporte, P7, P9, P4 y
  P5, P6, P2 y P3 (`ContributionGuidelines`), P1 (`ContributionCapRule`), y P8 en `register_delivery`; ítems y términos
  aprobados congelados; una sola entrega por aporte, congelada fuera de `procured`.
- `CampaignCommitted` y `CampaignCap`: el tope de la campaña suma compromisos y aportes comprometidos, también al aprobar
  compromisos (`SupportPolicyGuard`); un tenant sin F5 solo lee compromisos.
- `POST /api/sgspe/social-contributions/{id}/deliveries`; la corrección por `PATCH /api/sgspe/deliveries/{id}` responde
  `{ delivery, contribution }` para un aporte.
- `ContributionTotalsListener` y `ContributionTotalsRecalculator` (`total_amount`); el ejecutado de la campaña suma los
  aportes entregados.
- `AmountFieldsGuard` con los montos de F5; políticas de aportes en `ProjectParameters`; «mismo proyecto» en aportes,
  programas y tareas.
- Aceptar una solicitud como aporte (`created_contribution` como campo de comando), `close_unfulfilled` con el aporte
  rechazado o anulado y `RequestClosureListener` en `close` de WF-P.
- Aviso `approval_pending` al registrar un aporte.

## Pruebas

Unitarias en verde con `./gradlew build`. Integración en la CI (lista en la Tarea 15 del plan).

## Para revisar

- Decisión 1 del plan: la API genérica no abre transacción, así que el candado del aporte que toma la escritura de un ítem
  acota la ventana frente a la aprobación pero no la cierra; lo aprobado no se mueve (el total se escribe al aprobar y
  JOB-02 solo recalcula en `requested`).
- Decisión 6: el orden de candados de las evidencias vinculadas a entregas pasa a la clave del padre.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
BODY
```

Esperado: la URL del PR. La CI corre la suite de integración; con la CI en verde y el plan revisado, se integra según la
memoria del proyecto (push y PR autorizados; merge tras CI verde).

---

## Dependencias de otros planes

Lo que este plan supone que entrega cada plan, con el nombre y la forma de la especificación (y, donde ya están escritos,
de los planes 5.1 y 5.2b):

- **Plan 5.0 (documentos).** `api.md` §3.3 con `ITEMS_REQUIRED`, `ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`,
  `DELIVERY_EXISTS`, `FIELD_NOT_ALLOWED`, `PAID_LABOR`, `MONETARY_DONATION`, `PRIORITY_NOT_MET`,
  `PRIORITIES_NOT_CONFIGURED`, `SYNERGY_AGREEMENT_REQUIRED` y `COMMAND_ONLY` con `created_contribution`; §4.6 con la
  aceptación como aporte y el endpoint de entrega (con `If-Match` del aporte, elección 10 y §3.5); `workflows.md` WF-P con
  P1 a P9, orden y candados; `CLAUDE.md` con `sgspe.investment` completo. Las decisiones 1, 4, 6, 7, 8 y 9 de este plan
  afinan el contrato: la Tarea 15 las anota en el PR para ajustar `api.md` y `workflows.md` en 5.2b o en el cierre de fase.
- **Plan 5.1 (modelo y alcance).**
  - `model/model.json`: `social_contribution` (con `total_amount` derivado, `topic` por `contribution_topic`,
    `contribution_type` opcional con DONACION y APOYO EN ESPECIE, `has_institutional_synergy`, `agreement`,
    `exploration_campaign`, `guideline_deviation_justification`, `beneficiaries_count` INTEGER, `currency`),
    `contribution_item` (`item_kind` obligatorio con el ENUM `item_kind` BIEN · SERVICIO · DINERO · MANO DE OBRA,
    `line_amount`, `unit_cost`, `quantity`), `receipt`, `delivery.social_contribution` con su único,
    `stakeholder_request.created_contribution` (MANY_TO_ONE), `task.social_program`, las M2M
    `evidence_social_contribution` y `project_priority_topic` (`project` → `topic`), y los objetos de programas.
  - `model/workflows/social_contribution.json` (WF-P, `requested` inicial, transiciones `approve`, `approve_direct`,
    `reject`, `register_procurement`, `register_delivery`, `close`, `cancel_approved`, `cancel_procured`);
    `stakeholder_request.json` con `SOCIAL_INVESTMENT_MANAGER` en `accept` y `close_accepted`.
  - `model/roles.json` con §4.2: `SOCIAL_MANAGER` y `COMMUNITY_RELATIONS_OFFICER` con CRUD en `contribution_item`;
    `APPROVE` sobre `social_contribution` para `APPROVER` y `SOCIAL_MANAGER`, `APPROVE_OVER_CAP` solo para
    `SOCIAL_MANAGER`; `SOCIAL_INVESTMENT_MANAGER` con CRU en `social_contribution` y `delivery`, CRUD en
    `contribution_item`, READ sobre `agreement`, `agreement_party`, `commitment` y `exploration_campaign`, `evidence` con
    CREATE y `EDIT_ANY`, RU en `stakeholder_request` y sin `READ_CONFIDENTIAL` (lo supone la prueba de la solicitud confidencial de
    la Tarea 12); los roles de `register_delivery` lectores de `delivery.conformity_by`; `COMMITMENT_MANAGER` sin CREATE
    sobre `social_contribution`.
  - `ExactlyOneGuard.GROUPS["delivery"] = [[commitment, social_contribution]]` y `GROUPS["task"]` con `social_program`;
    `AmountFieldsGuard.FIELDS` y `NOT_MONEY` con los montos de F5; `GuardTablesCatalogTest.notYetModeled ==
    setOf("ai_suggestion")` (las Tareas 2 y 3 lo comprueban; si falta, se avisa al coordinador: es del plan 5.1).
  - `ProjectScope` con `delivery` de dos padres y la cadena de `program_measurement`; `ProjectResolver` y
    `Records.linkedProject` que suben al proyecto del aporte (lo usan `ProjectScopeGuard`, `ProjectLinkGuard` y
    `EvidenceWriteGuard` al vincular evidencias a la entrega de un aporte); `ArchitectureTest` con las reglas de §3.2.
  - `src/test/kotlin/sgspe/F5ApiTest.kt` (sobre `F4ApiTest`), base de `ContributionApiTest`, con `socialContribution`,
    `contributionItem`, `approvedContribution`, `contributionDelivery`, `campaign` y `activeAgreement` (los nombres de
    `ContributionApiTest` no chocan con ellos, decisión 15).
  - `InvestmentModelApiTest` (plan 5.1, Tarea 4), cuya línea que escribe `created_contribution` por la API genérica cambia
    la Tarea 11 (desde aquí es `403 COMMAND_ONLY`).
  - `WorkflowMatrixApiTest` sobre `F5ApiTest` con WF-P y `ROLE_DEFERRED` con el caso «`SOCIAL_INVESTMENT_MANAGER` aplica
    `accept`», que la Tarea 14 quita.
- **Lo que este plan entrega a 5.2b** (contrastado con su plan): `InvestmentRule`, `InvestmentRuleOrder.PROGRAM`,
  `InvestmentWriteGuard.OBJECTS` (solo aportes y `delivery`: 5.2b le suma `Programs.OBJECTS`, como prevé),
  `CampaignCommitted.CONTRIBUTION_STATES` y `CampaignCommitted.of`, `InvestmentCounts.nonNegativeInteger`, `Synergy.live`
  (el criterio de «acuerdo vigente» de G3), `ProjectParameters` con las cuatro políticas, `ProjectLinkGuard.OBJECTS` con
  `social_program`, `ApprovalStep(objectName, submit: String?, pending, approvals, direct = emptyList())` con WF-P al alta y
  el constructor `ApprovalNoticeListener(notices, assignments, catalog, senior, records)` (un parámetro nuevo, `records`,
  que la prueba de 5.2b simula con `mock(Records::class.java)`), `CampaignExecutionRecalculator` con los aportes como
  segunda fuente, `Records.projectOf` sin moverse de `sgspe.commitment` y el endpoint de entrega. Una `delivery` creada como
  la plataforma no pasa por el chequeo de `procured` (solo lo hace el endpoint), como pide `ProjectReadScopeApiTest` de
  5.2b; sí por `DELIVERY_EXISTS` y `FIELD_NOT_ALLOWED`.
- **Lo que este plan entrega a 5.3 (portal).** El contrato de «Interfaces con el portal».

## Cobertura del alcance de este plan

| Pieza de la especificación (§7, fila 5.2a) | Tarea |
|---|---|
| `AmountFieldsGuard` (elección 11): lo deja 5.1; aquí se comprueba y se prueba | 2 |
| `ExactlyOneGuard` de `task` (lo deja 5.1; se comprueba) y `ProjectLinkGuard` (elección 5) | 3 |
| `CampaignCommitted` y `SupportPolicyGuard` (elección 6) | 6 |
| `ContributionCapRule` (P1) | 7 |
| `InvestmentWriteGuard`: ítems y términos congelados, conteos | 1 |
| P4, P5, P7, P9 y P6 | 4 |
| `ContributionGuidelines` y temas priorizados (P2, P3) | 5 |
| Entrega de aportes y sus reglas (`DELIVERY_EXISTS`, `DELIVERY_FROZEN`, `delivered_amount`) | 9 |
| P8 | 10 |
| `ContributionTotalsListener` y recalculadores (también el ejecutado de la campaña) | 8 (y su integración en 10) |
| Aceptación como aporte, campo de comando y `close_unfulfilled` | 11 |
| `RequestClosureListener` en `close` de WF-P | 12 |
| Avisos de WF-P | 13 |
| `SocialContributionWorkflowApiTest` y el caso de `StakeholderRequestWorkflowApiTest` | 1, 4, 5, 7, 9, 10; 11 y 12 |
| Matriz de workflows con las guardas | 14 |
| PR | 15 |
