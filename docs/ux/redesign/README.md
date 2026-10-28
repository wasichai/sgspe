# Rediseño integral de UX/UI de SGSPE

> **Implementado y llevado a `docs/ux/` el 2026-10-23.** El rediseño se aprobó, se implementó en `sgspe-ui` (fases 1 a 5
> del plan) y lo que quedó está en [`ux-architecture.md`](../ux-architecture.md), [`design-system.md`](../design-system.md),
> [`screens.md`](../screens.md) y [`flows.md`](../flows.md), que son la fuente de verdad de la interfaz. Esta carpeta queda
> como historia de la propuesta y como registro de decisiones: [07](07-decisiones-y-compatibilidad.md) sigue siendo el lugar
> de las `DD-NN` que esos documentos citan. Quedan abiertas la regeneración de las capturas de los manuales (7.2, necesita
> la demo viva) y las dependencias externas de [08](08-cambios-de-contrato-backend.md) (fase 6).
>
> Propuesta del 2026-10-20 y 2026-10-21. La especificación es
> [`docs/superpowers/specs/2026-10-20-rediseno-ux-design.md`](../../superpowers/specs/2026-10-20-rediseno-ux-design.md) y
> el plan con casillas es
> [`docs/superpowers/plans/2026-10-21-rediseno-ux-portal.md`](../../superpowers/plans/2026-10-21-rediseno-ux-portal.md).

> **Tercera pasada (2026-10-28).** Revisa el portal tal como quedó y recupera lo que se perdió al integrar (A13, A24 y la razón
> a la vista de A3). La auditoría es `sgspe-ui/docs/rediseno-ux/tercera-pasada.md`; sus decisiones son DD-30 a DD-40
> ([07 §4](07-decisiones-y-compatibilidad.md#4-tercera-pasada-2026-10-28)) y su pedido al backend, B-05 ([08](08-cambios-de-contrato-backend.md)).

## Entregables

| # | Entregable | Archivo |
|---|---|---|
| 1 | Auditoría: 43 hallazgos con evidencia, impacto y prioridad (5 P1) | [01-auditoria.md](01-auditoria.md) |
| 2 y 5 | Arquitectura de información, navegación y los siete flujos prioritarios rehechos | [02-arquitectura-de-informacion.md](02-arquitectura-de-informacion.md) |
| 3 | Dirección visual y design system, con los contrastes verificados por script | [03-direccion-visual-y-design-system.md](03-direccion-visual-y-design-system.md), [contraste.py](contraste.py) |
| 4 | Prototipos de alta fidelidad de los ocho patrones, más la ficha y los estados del sistema (23 capturas) | [04-prototipos/](04-prototipos/README.md) |
| 6 y 8 | Componentes, estados, interacciones, criterios de aceptación y la validación de los prototipos | [05-especificacion-de-componentes.md](05-especificacion-de-componentes.md) |
| 7 | Plan de implementación por fases | [06-plan-de-implementacion.md](06-plan-de-implementacion.md) |
| 9 | Decisiones (con su porqué y lo descartado) y compromisos de compatibilidad | [07-decisiones-y-compatibilidad.md](07-decisiones-y-compatibilidad.md) |
| 10 | Cambios que tocan el backend o `@wasichai/*`, separados de los visuales | [08-cambios-de-contrato-backend.md](08-cambios-de-contrato-backend.md) |

## Cómo leerlo

- **Para decidir:** empezar por el resumen de [01 §2](01-auditoria.md), las decisiones de [07](07-decisiones-y-compatibilidad.md)
  y los prototipos.
- **Para implementar:** empezar por el plan con casillas, con [05](05-especificacion-de-componentes.md) como fuente de los
  criterios.
- **Identificadores propios del rediseño:**
  - `U-NN`: hallazgos;
  - `P-NN`: patrones;
  - `F-X`: flujos;
  - `DD-NN` y `DP-NN`: decisiones;
  - `V-NN`: validación;
  - `CA-N.N.N`: criterios de aceptación;
  - `B-NN` y `W-NN`: cambios de contrato.

  Los IDs de requisitos (RF, RN, UPS…) solo se citan: se definen en `docs/requirements-analysis.md`.

## Material relacionado

- **El design system en Claude Design:** proyecto «SGSPE Design System», sincronizado desde `sgspe-ui/.design-sync/`
  (61 componentes con sus previews y su documentación).
- **El código de los prototipos:** `sgspe-ui/.design-sync/prototipos/`, con los componentes reales del kit.
