# Diseño UX/UI de SGSPE

Esta carpeta contiene el diseño de la interfaz del portal de SGSPE (`sgspe-ui`, portal en `/`), hecho a partir del prompt
maestro de UX/UI (PRM-02) y validado contra los documentos de la Fase 0, que son la fuente de verdad. La parte visual está en
un lienzo de diseño; aquí está todo lo que el equipo de frontend necesita para implementarlo.

- **Lienzo de diseño:** <https://claude.ai/artifact/LMLBimwromQ5dsRQDDeD61> (privado: se comparte desde el menú del lienzo).
- **Orden de trabajo y principio rector** [USR-10]: dominio → arquitectura de información → navegación → design system →
  patrones núcleo → Project 360 y territorio → grupos de interés, relacionamiento y compromisos → QCS, riesgos y programas →
  tableros → capa de IA. Ningún módulo es una aplicación aparte: la navegación conserva el contexto de proyecto y territorio y
  recorre la trazabilidad.

## Etapas

| Etapa | Contenido | Lienzo | Documentos | Estado |
|---|---|---|---|---|
| E1 | Visión, dominio, roles, arquitectura de información, navegación, hilo de contexto, design system y patrones núcleo | Páginas 00 a 04 (17 hojas) | [`ux-architecture.md`](ux-architecture.md), [`design-system.md`](design-system.md), [`tokens.css`](tokens.css) | Entregada, en revisión |
| E2 | Project 360 y territorio (GIS), grupos de interés, relacionamiento y compromisos | Páginas 05 a 08 | `screens.md` y `flows.md` de esos módulos | Pendiente |
| E3 | QCS, riesgos y alerta temprana, programas, documentos, tareas y aprobaciones, tableros, capa de IA, experiencia móvil de campo y handoff | Páginas 09 a 13 | `screens.md` y `flows.md` completos | Pendiente |

## Cómo leer

1. [`ux-architecture.md`](ux-architecture.md): visión, principios, decisiones heredadas de la Fase 0, roles, arquitectura de
   información, rutas, navegación, el hilo de contexto y el inventario de 55 pantallas.
2. [`design-system.md`](design-system.md): tokens, sistema de estados, componentes, patrones núcleo, accesibilidad, responsive y
   guía de implementación.
3. [`tokens.css`](tokens.css): los tokens listos para Tailwind 4, como capa sobre `@wasichai/ui/theme.css`.

## Lienzo de la etapa 1

| Página | Hojas |
|---|---|
| 00 Visión y dominio | Visión UX · Mapa del dominio · Roles y trabajos por hacer |
| 01 Arquitectura de información | Tres contextos: global, proyecto, territorio · Inventario de pantallas |
| 02 Navegación y contexto | Shell de la aplicación · Navegación por rol y sub-navegación de proyecto · Hilo de contexto (prototipo navegable) · Búsqueda global y comandos |
| 03 Design system | Fundamentos y tokens · Sistema de estados · Componentes de acciones, entradas y feedback · Componentes de datos, contenido, mapa e IA |
| 04 Patrones núcleo | Ficha de entidad · Transición con motivo y formulario por pasos · Lista con vistas guardadas y acciones masivas · Vacío, carga, error, permisos y auditoría |

## Convenciones

- Español para documentos, etiquetas y valores de ENUM; nombres técnicos, rutas y componentes en inglés (ADR-0009).
- Los IDs (RF, RNF, RN, RQ, CON, GAP, ASM, UPS) se definen solo en el [análisis](../requirements-analysis.md); los supuestos de
  esta carpeta son ASM-073 a ASM-079. Los identificadores de pantalla `S01` a `S55` se definen en el inventario de
  `ux-architecture.md` §9.
- Los datos de ejemplo del lienzo son ficticios: proyectos, comunidades, caseríos y personas inventados. En estos documentos
  las personas se nombran por su cargo [USR-07].
