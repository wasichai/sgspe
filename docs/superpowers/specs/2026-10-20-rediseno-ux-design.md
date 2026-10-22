# SGSPE · Rediseño integral de UX/UI: especificación de diseño

> **Para agentes:** esta especificación es la entrada del plan `docs/superpowers/plans/2026-10-21-rediseno-ux-portal.md`.
> No define IDs de requisitos: solo cita los de `docs/requirements-analysis.md`. Los identificadores `U-NN` (hallazgos),
> `DD-NN`/`DP-NN` (decisiones) y `P-NN` (patrones) son propios del rediseño. El detalle vive en `docs/ux/redesign/`. Donde
> esta especificación y `docs/ux/` difieren, `docs/ux/` sigue vigente hasta que la tarea final del plan lo actualice.

**Objetivo.** Que cada persona encuentre lo que le toca, entienda el estado de una situación y complete su tarea con el
mínimo esfuerzo razonable. La meta no es modernizar la superficie: es corregir cómo se componen piezas que ya son buenas.

**Diagnóstico** (detalle en [01-auditoria](../../ux/redesign/01-auditoria.md)). 43 hallazgos (los 38 del recorrido más los 5 de la adenda §7), de los que 5 son P1:

- el inicio no responde a la persona (U-01);
- la acción principal contradice a la «Siguiente acción» (U-02);
- cabeceras con 4 a 6 acciones y destructivas contiguas (U-03);
- migas y pestaña en inglés en tres fichas (U-04);
- la administración edita lo que no es fuente de verdad sin advertirlo (U-37).

**Arquitectura de la solución.** Tres capas, en este orden, porque cada una se apoya en la anterior:

1. **Patrones transversales** en `src/portal/componentes/`:
   - `Seccion`;
   - `CabeceraDeFicha`, con la regla de la acción principal;
   - `SelectorDeVista`;
   - `BarraDeFiltros` con chips;
   - `CampoFecha`;
   - `TablaPriorizada` (prioridad de columnas);
   - `EstadoSinAcceso`;
   - `ListaPriorizada` (en lugar de los muros de enlaces).

   Todos componen primitivos de `@wasichai/ui`.
2. **Shell y navegación**:
   - barra superior con idioma y tema en el menú de la cuenta;
   - barra lateral expandida desde `lg`, con títulos de bloque visibles;
   - una sola fila de pestañas;
   - migas correctas.
3. **Áreas**, replicando los patrones:
   - el inicio «Mi trabajo» por capacidades;
   - la ficha 360 del proyecto;
   - listados;
   - formularios;
   - la bandeja de aprobaciones;
   - el mapa primero;
   - la revisión ejecutiva compacta;
   - el tema de la administración.

**Fuentes.** El encargo del 2026-10-20 (rediseño integral, diez entregables, etapas A–F); `docs/ux/` completo;
`sgspe-ui/CLAUDE.md` (reglas de permisos, escritura, confidencialidad, i18n y tokens); `docs/security.md` §3.2 y
§5.10; las 56 capturas de `docs/manuals/assets/screenshots/`; el kit de design-sync de `sgspe-ui`.

## 1. Decisiones

Las tomó el agente con criterio propio por pedido expreso del usuario, que estuvo ausente. Están registradas con su
porqué y lo descartado en [07-decisiones-y-compatibilidad](../../ux/redesign/07-decisiones-y-compatibilidad.md):

- **de proceso:** DP-01 a DP-06;
- **de diseño:** DD-01 a DD-21.

Son vinculantes para el plan **solo cuando el usuario las apruebe**.

## 2. Entregables

| # | Entregable | Archivo |
|---|---|---|
| 1 | Auditoría con problemas confirmados, impacto y prioridad | `docs/ux/redesign/01-auditoria.md` |
| 2 y 5 | Arquitectura de información, navegación y flujos de las tareas prioritarias | `docs/ux/redesign/02-arquitectura-de-informacion.md` |
| 3 | Dirección visual y design system (evolución de tokens con contraste verificado) | `docs/ux/redesign/03-direccion-visual-y-design-system.md` |
| 4 | Prototipos de los ocho patrones, en claro y oscuro, en español e inglés | `docs/ux/redesign/04-prototipos/` |
| 6 y 8 | Especificación de componentes, estados, interacciones y criterios de aceptación | `docs/ux/redesign/05-especificacion-de-componentes.md` |
| 7 | Plan de implementación por fases | `docs/ux/redesign/06-plan-de-implementacion.md` y el plan con casillas |
| 9 | Decisiones y compatibilidad | `docs/ux/redesign/07-decisiones-y-compatibilidad.md` |
| 10 | Cambios que tocan el contrato del backend o de `@wasichai/*` | `docs/ux/redesign/08-cambios-de-contrato-backend.md` |

## 3. Restricciones

- **Cero cambios de ruta**, como compromiso de compatibilidad (07 §3).
- Nada que nombre roles en `src/`.
- Confidencialidad intacta: ningún bloque cuenta lo ilegible.
- Escritura por `datos/`.
- Tokens `--sg-*` existentes sin cambios de valor.
- Textos en los dos idiomas y sin códigos.

## 4. Cómo se verifica

- **Auditoría:** cada hallazgo cita una captura o un `archivo:línea`.
- **Prototipos:** se fotografían con `node .design-sync/prototipos/fotografiar.mjs` desde los componentes reales, en
  1440 px, claro y oscuro, español e inglés.
- **Contrastes:** se verifican con `docs/ux/redesign/contraste.py` (WCAG 2.2 AA).
- **Plan:** prueba primero en cada tarea y la suite completa del portal
  (`yarn format && yarn lint && yarn typecheck && yarn test && yarn build && yarn e2e`).
