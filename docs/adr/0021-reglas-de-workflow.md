# ADR-0021: Reglas de workflow: metadata, guardas y motivo

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt pide workflows explícitos y testeables, no escondidos en condicionales, y propone máquinas de estado en código
  [PRM-01 §8, §19] (CON-042).
- wasichai ya decidió la mecánica, y este ADR no la repite:
  - el estado vive en `workflow_state` del registro y solo una transición lo mueve; no hay motor BPM [WAS-ADR-0013];
  - los workflows se editan en un lienzo y se guardan como metadata [WAS-ADR-0018];
  - una `RecordWriteGuard` ve cada transición antes de que el estado cambie; `appendOnly` rechaza transiciones y `apiOnly` no
    las rechaza [WAS-ADR-0040];
  - `X-Change-Reason` viaja en la transición y es obligatorio en los objetos `requiresReason` [WAS-ADR-0041];
  - las automatizaciones se encolan y corren como la plataforma [WAS-ADR-0016].
- Lo que falta decidir es propio de SGSPE: qué representa un estado, cómo se expresa una regla que depende del rol sin
  comparar nombres de rol, cuándo una transición solo puede llegar desde un comando y cuándo el motivo es obligatorio.
- SGSPE define 19 workflows, uno por objeto, de WF-A a WF-P; WF-K cubre `commitment_candidate` y `ai_suggestion`
  ([workflows.md](../workflows.md), "Resumen de workflows").

## Opciones consideradas

### Máquinas de estado en código Kotlin, como pide el prompt

- Ventajas: reglas y estados juntos y tipados.
- Desventajas: duplica el estado que wasichai ya guarda y mueve; `/admin`, las vistas y la auditoría dejarían de verlo; contradice
  la regla de metadata primero (RNF-MNT-005, CON-040).

### Solo metadata: estados, transiciones y roles, sin código

- Ventajas: cero código propio.
- Desventajas: no expresa precondiciones ("compromiso sin responsable"), segregación de funciones ni transiciones que solo un
  comando puede aplicar; las reglas terminarían en el portal, que la API genérica se salta.

### Metadata para la máquina, guardas por paquete para las reglas y motivo explícito (elegida)

- Ventajas: el workflow sigue siendo de la plataforma; cada regla tiene un lugar único y una prueba; la API genérica y `/admin`
  no pueden saltarse las guardas.
- Desventajas: una regla vive en dos lugares (la definición JSON y la guarda), que las pruebas del modelo deben mantener
  alineados.

## Decisión

Reglas de SGSPE sobre la mecánica de wasichai ([workflows.md](../workflows.md), "Principios"):

1. **Metadata versionada.** Un workflow por objeto en `model/workflows/<objeto>.json`, con nombre `wf-<letra>-<objeto>`, estados
   y transiciones en inglés `snake_case` con etiqueta en español, aplicado por `apply.py` después de los roles (ADR-0007). El
   lienzo de `/admin` no es fuente de verdad (GAP-030). Un estado nunca se renombra: se agrega el nuevo y un script migra
   los registros, como con los valores ENUM (ASM-025).
2. **Un estado registra una decisión humana.** Vencido, por vencer, expirado y revisión pendiente son campos derivados, nunca
   estados (RN-WFL-001, CON-028); lo decide ADR-0012, que también fija que los trabajos nunca aplican transiciones.
3. **Roles por transición.** Ninguna lista queda vacía; `SUPER_ADMIN` y `ORGANIZATION_ADMIN` no figuran porque son `ADMIN`
   (ASM-024); `AUDITOR`, `ANALYST` y `READ_ONLY` nunca aplican transiciones.
4. **Guardas por paquete dueño.** Cada paquete aporta la `RecordWriteGuard` de sus objetos (`CommitmentWriteGuard`,
   `AttentionWriteGuard`…), que reúne las precondiciones numeradas de cada workflow (D1, P1…).
5. **Doble llave en las decisiones.** La transición lista sus roles y la guarda exige además la acción declarada `APPROVE`,
   `VERIFY` o `CONFIRM_ALERT` con `CurrentUser.requirePermission` [WAS-ADR-0042] (RF-WFL-002). El código nunca compara nombres
   de rol (RQ-SEC-005).
6. **Dos transiciones cuando la regla depende del rol**, entre los mismos estados y con roles distintos, y la guarda decide cuál
   admite: `approve` y `approve_senior` en WF-D, `resolve` y `resolve_high_severity` en WF-E1.
7. **Un paso desde varios estados son varias transiciones** con la misma etiqueta y el estado de origen como sufijo
   (`cancel_draft`, `cancel_in_progress`), porque cada transición tiene un solo `from`.
8. **Transiciones de comando.** Las que escriben varios registros a la vez (`assess` de WF-B, `close_reclassified_*`, `accept`
   de WF-E3 y de WF-K, `close_accepted`, `issue`, `approve` y `supersede` de WF-O) solo llegan desde un endpoint o un listener
   de SGSPE: `CommandTransitionGuard` las rechaza sin la marca `SgspeWrite`, que es lo que ocurre por la API genérica. El
   comando aplica la transición como la persona, que necesita el rol (ADR-0008).
9. **Motivo.** Toda transición de los ocho objetos `requiresReason` lleva motivo (`commitment`, `agreement`, `grievance`,
   `inquiry`, `stakeholder_request`, `social_risk`, `social_contribution`, `deliverable`); en los demás, `TransitionReasonGuard`
   lo exige al devolver, rechazar, suspender, cancelar, retirar y reabrir (RQ-AUD-002, ASM-058). La auditoría es la de ADR-0011.
10. **Políticas con desvío explícito.** Si un proyecto desactiva la aprobación interna, la guarda admite `approve_direct` o
    `plan_direct` (ASM-008). WF-A nunca se desactiva: una organización sin revisión usa una variante de su definición
    (ASM-010).
11. **Excepción documentada:** WF-B no tiene estado final, porque `retired` es reincorporable (RF-STK-013).
12. **Antes de pedir, preguntar.** El portal consulta `POST /api/sgspe/transition-checks`, que ejecuta las guardas en seco, y
    explica un botón deshabilitado con el `allowed` y el `reason` de la lista de transiciones.

El alcance por asignación (`ProjectScopeGuard`) y la edición de lo ajeno (`EditAnyGuard`) también ven cada transición; los
decide ADR-0006. La IA nunca aplica transiciones; aceptar una sugerencia es la transición de comando `accept` (ADR-0004).

## Consecuencias

### Positivas

- `/admin`, las vistas, el historial y la auditoría ven el mismo estado que el portal.
- Cada regla es una guarda con su número y su prueba; la API genérica no puede saltarla.
- Las reglas que dependen del rol cambian editando roles en la metadata, sin tocar código.

### Negativas y riesgos

- El JSON del workflow y la guarda deben coincidir en los nombres de transición; `test_model.py` lo verifica.
- `/admin` no pide el motivo, así que una transición con motivo obligatorio falla ahí con `400`: las transiciones críticas se
  hacen desde el portal (UPS-08).
- Sin temporizadores en el motor, todo efecto por tiempo depende de los trabajos de ADR-0012.

### Seguimiento

- Abiertos: UPS-08, GAP-030, ASM-008, ASM-010, ASM-058. Aceptado; cada workflow se aplica en la fase que crea su objeto.
- Pruebas de [workflows.md](../workflows.md), "Pruebas": `test_model.py` (un `INITIAL`, estados de §7.3 del modelo de dominio,
  roles existentes, ningún estado de plazo, ningún objeto con workflow `appendOnly`) y las pruebas de integración sobre
  `SgspeApiTest`, una clase por workflow (`CommitmentWorkflowApiTest`, `GrievanceWorkflowApiTest`…), con permitida, denegada,
  doble llave y transición de comando por la API genérica rechazada.

## Referencias

- Requisitos: RN-WFL-001, RF-WFL-002, RF-STK-013, RQ-AUD-002, RQ-AUD-004, RQ-SEC-005, RNF-MNT-005, RNF-TST-001; CON-028,
  CON-040, CON-042; GAP-030; UPS-08; ASM-008, ASM-010, ASM-024, ASM-025, ASM-058.
- Documentos: [workflows.md](../workflows.md) ("Principios", "Transiciones de comando", "Transiciones con motivo obligatorio",
  "Formato JSON de un workflow", "Pruebas"), [domain-model.md §7.1 y §7.3](../domain-model.md),
  [security.md §5.7](../security.md), [api.md §2](../api.md).
- ADR de SGSPE: ADR-0004, ADR-0006, ADR-0007, ADR-0008, ADR-0011, ADR-0012.
- WAS-ADR-0013 (Workflow state lives on the record, transitions are the only way to move it), WAS-ADR-0016 (Automations are
  queued work that runs as the platform), WAS-ADR-0018 (Workflows are edited on a canvas, and the canvas remembers where things
  are), WAS-ADR-0040 (Append-only objects, a pre-write guard SPI, and api-only objects), WAS-ADR-0041 (A change reason on
  record writes, required per object), WAS-ADR-0042 (An object declares its own actions, and they are granted, checked and
  listed like CRUD).
- Fuentes: [PRM-01 §8, §18, §19], [USR-09].
