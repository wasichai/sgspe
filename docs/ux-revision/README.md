# Revisión de UX del portal (2026-10-19)

Revisión de experiencia de uso de `sgspe-ui` contra el diseño de `docs/ux/`, con un plan para implementarla por etapas,
pantalla por pantalla. Las rutas `src/…` y `e2e/…` que se citan son de `sgspe-ui`.

| Archivo | Para qué |
|---|---|
| [`plan.md`](plan.md) | Diagnóstico, defectos, inconsistencias y el plan de mejoras en 8 etapas, con criterios de aceptación |
| [`prompt.md`](prompt.md) | El prompt para que Claude Code implemente una etapa por sesión, más el prompt corto de cada etapa |
| `capturas/` | Capturas del recorrido con la API simulada (1440 y 390 px) que respaldan los hallazgos |

Cómo se hizo: la build de producción (`dist/`) servida en local y recorrida con Playwright y una API simulada en el navegador
(la de `e2e/api.ts`, ampliada con compromisos, QCS, riesgos, programas, tareas y entregables), más la lectura del código de
`src/portal/` y su contraste con `docs/ux/ux-architecture.md`, `design-system.md` y `screens.md`. La build recorrida es
anterior a #49 y #50 (sin «Su trabajo» en el inicio); lo que esos cambios tocan se juzgó por el código. Las cifras de las
tarjetas de los tableros fallan en las capturas porque la API simulada no responde los tableros: eso no es un defecto, pero
el texto con que se muestra el fallo sí lo es (D4).
