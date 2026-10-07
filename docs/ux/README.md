# Diseño UX/UI de SGSPE

Esta carpeta contiene el diseño de la interfaz del portal de SGSPE (`sgspe-ui`, portal en `/`), hecho a partir del prompt
maestro de UX/UI (PRM-02) y validado contra los documentos de la Fase 0, que son la fuente de verdad. La parte visual está en
un lienzo de diseño; aquí está todo lo que el equipo de frontend necesita para implementarlo.

- **Lienzos de diseño** (privados: se comparten desde el menú de cada lienzo):
  - etapa 1: <https://claude.ai/artifact/LMLBimwromQ5dsRQDDeD61>;
  - etapa 2: <https://claude.ai/artifact/7RUzqeAhtWx75vCNPCBRWe>.
- **Orden de trabajo y principio rector** [USR-10]: dominio → arquitectura de información → navegación → design system →
  patrones núcleo → Project 360 y territorio → grupos de interés, relacionamiento y compromisos → QCS, riesgos y programas →
  tableros → capa de IA. Ningún módulo es una aplicación aparte: la navegación conserva el contexto de proyecto y territorio y
  recorre la trazabilidad.

## Etapas

| Etapa | Contenido | Lienzo | Documentos | Estado |
|---|---|---|---|---|
| E1 | Visión, dominio, roles, arquitectura de información, navegación, hilo de contexto, design system y patrones núcleo | Páginas 00 a 04 (17 hojas) | [`ux-architecture.md`](ux-architecture.md), [`design-system.md`](design-system.md), [`tokens.css`](tokens.css) | Entregada, en revisión |
| E2 | Project 360 y territorio (GIS), grupos de interés, relacionamiento, compromisos y acuerdos | Lienzo de la etapa 2, páginas 05 a 10 (28 hojas) | [`screens.md`](screens.md) (S06–S26, S28–S33), [`flows.md`](flows.md) (flujos 1–11, 16 y 17) | Entregada, en revisión |
| E3 | QCS, riesgos y alerta temprana, programas, documentos, tareas y aprobaciones, tableros (con S27), capa de IA, experiencia móvil de campo y handoff | Lienzo de la etapa 3 | `screens.md` y `flows.md` completos | Pendiente |

## Cómo leer

1. [`ux-architecture.md`](ux-architecture.md): visión, principios, decisiones heredadas de la Fase 0, roles, arquitectura de
   información, rutas, navegación, el hilo de contexto y el inventario de 55 pantallas.
2. [`design-system.md`](design-system.md): tokens, sistema de estados, componentes, patrones núcleo, accesibilidad, responsive y
   guía de implementación.
3. [`tokens.css`](tokens.css): los tokens listos para Tailwind 4, como capa sobre `@wasichai/ui/theme.css`.
4. [`screens.md`](screens.md): la ficha de handoff de cada pantalla (ruta, roles, acciones, datos y API, estados, permisos,
   responsive, navegación y árbol de componentes React).
5. [`flows.md`](flows.md): los flujos núcleo paso a paso, con la pantalla, la transición o el endpoint y la regla de cada paso.

## Lienzo de la etapa 1

| Página | Hojas |
|---|---|
| 00 Visión y dominio | Visión UX · Mapa del dominio · Roles y trabajos por hacer |
| 01 Arquitectura de información | Tres contextos: global, proyecto, territorio · Inventario de pantallas |
| 02 Navegación y contexto | Shell de la aplicación · Navegación por rol y sub-navegación de proyecto · Hilo de contexto (prototipo navegable) · Búsqueda global y comandos |
| 03 Design system | Fundamentos y tokens · Sistema de estados · Componentes de acciones, entradas y feedback · Componentes de datos, contenido, mapa e IA |
| 04 Patrones núcleo | Ficha de entidad · Transición con motivo y formulario por pasos · Lista con vistas guardadas y acciones masivas · Vacío, carga, error, permisos y auditoría |

## Lienzo de la etapa 2

| Página | Hojas |
|---|---|
| 05 Proyectos y Project 360 | Índice de la etapa · Crear proyecto (S07) · Resumen (S08) · Mapa del proyecto (S09) · Equipo y parámetros (S11) |
| 06 Territorio | Mapa territorial, interactivo (S12) · Ficha de comunidad (S13) · Ficha de localidad y línea base (S14) · Área de influencia (S10) · Importación geoespacial (S15) |
| 07 Grupos de interés | Lista (S16) · Matriz interés × poder, interactiva (S19) · Ficha (S17) · Nueva evaluación (S20) · Ficha de actor (S18) |
| 08 Relacionamiento | Calendario y lista (S21, S22) · Ficha de interacción (S23) · Registrar interacción y modo rápido de campo (S24) · Editor de acta (S25) · Visita de campo (S26) |
| 09 Compromisos | Pestañas de la ficha (S29) · Crear, pasos 5 y 7 (S30) · Aprobación (S31) · Entrega, cumplimiento y verificación, interactiva (S32) · Candidatos (vista de S28) · Acuerdo (S33) |
| 10 Flujos | Flujos 1, 2, 3, 16 y 17 · Flujos 4 a 11 en carriles por rol |

La lista de compromisos (S28) y la pestaña Resumen de su ficha (S29) usan las hojas "Lista" y "Ficha" de los patrones de la
etapa 1.

## Convenciones

- Español para documentos, etiquetas y valores de ENUM; nombres técnicos, rutas y componentes en inglés (ADR-0009).
- Los IDs (RF, RNF, RN, RQ, CON, GAP, ASM, UPS) se definen solo en el [análisis](../requirements-analysis.md); los supuestos de
  esta carpeta son ASM-073 a ASM-080. Los identificadores de pantalla `S01` a `S55` se definen en el inventario de
  `ux-architecture.md` §9.
- Los datos de ejemplo del lienzo son ficticios: proyectos, comunidades, caseríos y personas inventados. En estos documentos
  las personas se nombran por su cargo [USR-07].
