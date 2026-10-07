# ADR-0012: Estados temporales materializados por trabajos programados

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt propone un estado `OVERDUE` almacenado en el compromiso y pide avisos y escalamientos por vencimiento
  [PRM-01 §5, §8, §17]. Las fuentes describen otra cosa: avance, entrega con conformidad y cierre [DOC-01 §9.3.1.2]
  (CON-028).
- En wasichai el estado de un registro solo se mueve con una transición, que es una decisión con roles y motivo
  [WAS-ADR-0013]. Las automatizaciones no tienen disparadores temporales (CON-043) [WAS-ADR-0016].
- El trabajo en segundo plano corre con `RecordService.asPlatform` y un `ClusterLock` [WAS-ADR-0039]. Dentro de
  `asPlatform` solo `RecordService` actúa como la plataforma: `WorkflowService` sigue pidiendo un usuario, así que un
  trabajo no puede aplicar transiciones ni vincular registros ([domain-model.md §8.3](../domain-model.md)).
- Varios objetos tienen plazos: compromisos, quejas, consultas, solicitudes, tareas, la vigencia de los acuerdos y la
  revisión semanal de los riesgos (RN-RSK-001), y los tableros cuentan vencidos y por vencer (RQ-DSH-018).

## Opciones consideradas

### Estados de workflow para el paso del tiempo (`overdue`, `due_soon`)

- A favor: es lo que propone el prompt; el estado se ve en toda lista.
- En contra: mezcla una decisión con un hecho del reloj, cuando un compromiso vencido puede estar en `in_progress` o en
  `blocked`; y ningún trabajo puede aplicar la transición, porque `WorkflowService` exige un usuario [WAS-ADR-0039].

### Calcularlo solo al leer

- A favor: nada que mantener; siempre exacto.
- En contra: no hay evento que dispare el aviso ni el escalamiento (RF-NTF-001, RF-WFL-004), y cada lista, filtro e
  indicador repetiría la regla.

### Campos derivados que mantiene un trabajo programado

- A favor: el estado queda para las decisiones; el plazo es un campo filtrable que comparten listas, tableros y
  entregables; el mismo trabajo crea los avisos y las tareas de escalamiento.
- En contra: el valor puede estar atrasado hasta una vuelta del trabajo, y el trabajo necesita listar los tenants (UPS-17).

## Decisión

**Ningún estado de workflow representa el paso del tiempo** (RN-WFL-001). Vencido, por vencer, expirado y revisión
pendiente son campos derivados que mantiene `DeadlineJob` (JOB-01, `sgspe.deadline`):

| Campo | Catálogo | Objetos | Fecha que mide |
|---|---|---|---|
| `deadline_status` | Situación de plazo: AL DIA, POR VENCER, VENCIDO | `commitment`, `grievance`, `inquiry`, `stakeholder_request`, `task` | `due_date` o el plazo de QCS vigente |
| `validity_status` | Situación de vigencia | `agreement` | `expiry_date` |
| `review_status` | Situación de revisión | `social_risk` | `next_review_date` |

- **Solo en los estados abiertos.** JOB-01 calcula el campo solo en los estados de la tabla "Plazos y escalamiento" de
  [workflows.md](../workflows.md) y lo deja vacío en los demás: VENCIDO siempre significa "abierto y atrasado". Un
  compromiso en `draft` o `pending_approval` no tiene plazo en curso; uno vencido sigue en `in_progress` o `blocked`.
- **Cómo corre.** Cada hora (ASM-006), con `ClusterLock.tryLock` para que una sola réplica haga la vuelta, una organización
  por vez con `RecordService.asPlatform`, y con un motivo que nombra al trabajo, porque su fila de auditoría no tiene
  usuario (ASM-051) [WAS-ADR-0041]. Una organización que falla no detiene a las demás.
- **Qué escribe.** Solo campos derivados, con la marca `SgspeWrite`, y registros nuevos: tareas de escalamiento y de
  revisión con `generator_key` único, y avisos deduplicados (ADR-0018). Recalcula desde cero, así que repetir una vuelta
  no cambia nada. Nunca aplica una transición ni vincula registros (CON-043).
- **Escalamiento.** Por días de atraso, con los parámetros del proyecto `due_soon_days` (7), `escalation_first_days` (7)
  y `escalation_second_days` (15): nivel 1, los roles de la transición de verificación o revisión; nivel 2, los de
  `approve_senior`, resueltos con las asignaciones del proyecto (ASM-030, ASM-070).
- **Protección.** `DerivedFieldsGuard` rechaza toda escritura de esos campos sin la marca, y en `model/model.json` se marcan
  con `"derived": true`, nunca con `editable: false` ([domain-model.md §4.6 y §7.4](../domain-model.md)).
- **El mismo principio para el resto de lo derivado.** `DerivedFieldsJob` (JOB-02) recalcula cada hora los derivados que
  mantienen listeners y que pudieron quedar pendientes por la API genérica; lo calculado al leer queda en los indicadores
  (ADR-0016). Los conjuntos de estados de [dashboards.md §3.2](../dashboards.md) son los mismos que usa JOB-01.
- **Todo cambio de estado automático ocurre en la petición de una persona**, como listener de su transición; el único del
  MVP es el cierre de una solicitud aceptada (RN-GRV-001) ([domain-model.md §8.3](../domain-model.md)).
- **Recorrido de tenants.** Hasta UPS-17, los trabajos siguen el precedente de caja, `findAllOrganizations()`, aislado en
  `sgspe.common` como excepción vigilada a RNF-MNT-005 ([architecture.md §8.1 y §14](../architecture.md)).

## Consecuencias

### Positivas

- El workflow solo registra decisiones humanas auditadas; el reloj nunca mueve un estado.
- Listas, filtros, tableros, entregables y avisos leen el mismo campo, con un solo cálculo.
- Los trabajos son idempotentes y seguros con varias réplicas [WAS-ADR-0039].

### Negativas y riesgos

- La situación de plazo puede atrasarse hasta una hora; los tableros lo declaran como "vivo (plazo horario)"
  ([dashboards.md §3.4](../dashboards.md)).
- Recorrer los tenants depende de una lectura interna de la plataforma hasta UPS-17
  ([architecture.md §14](../architecture.md), riesgo 3).
- Vueltas horarias sobre todos los tenants sin volúmenes conocidos (GAP-027): se mide antes de optimizar.

### Seguimiento

- Abiertos: UPS-17, GAP-027, GAP-005 (plazos de QCS y de solicitudes, con los valores de ASM-005 mientras tanto).
- Rige desde F3 (compromisos, acuerdos y tareas) y F4 (QCS y riesgos); JOB-02 desde F2.
- Pruebas: después de cada vuelta de JOB-01 a JOB-05, JOB-07 y JOB-08 ningún `workflow_state` cambió, repetir la vuelta no
  crea tareas y el motivo nombra al trabajo; `test_model.py` verifica que ningún estado representa un plazo
  ([workflows.md](../workflows.md), "Pruebas"); WF-I prueba que vencer solo cambia `validity_status`.

## Referencias

- Requisitos: RN-WFL-001, RF-WFL-004, RF-NTF-001, RN-RSK-001, RN-GRV-001, RQ-DSH-018, RNF-MNT-005, CON-028, CON-043,
  ASM-005, ASM-006, ASM-030, ASM-051, ASM-070, UPS-17, GAP-005, GAP-027.
- Documentos: [workflows.md](../workflows.md) ("Reglas de SGSPE", "Trabajos programados", "Plazos y escalamiento",
  "Pruebas"), [domain-model.md §4.6, §7.4 y §8.3](../domain-model.md), [dashboards.md §3](../dashboards.md),
  [architecture.md §8](../architecture.md), [requirements-analysis.md §17.2](../requirements-analysis.md).
- wasichai: `WAS-ADR-0013` (Workflow state lives on the record, transitions are the only way to move it), `WAS-ADR-0016`
  (Automations are queued work that runs as the platform), `WAS-ADR-0039` (Background work runs RecordService as the
  platform, and takes a cluster lock), `WAS-ADR-0041` (A change reason on record writes, required per object).
- ADR de SGSPE: ADR-0016, ADR-0018, ADR-0021 (reglas de workflow).
- Fuentes: [PRM-01 §5, §8, §17], [DOC-01 §9.3.1.2, §9.3.2.1].
