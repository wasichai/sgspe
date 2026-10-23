# Diseño UX/UI de SGSPE

Esta carpeta contiene el diseño de la interfaz del portal de SGSPE (`sgspe-ui`, portal en `/`), hecho a partir del prompt
maestro de UX/UI (PRM-02) y validado contra los documentos de la Fase 0, que son la fuente de verdad. La parte visual está en
un lienzo de diseño; aquí está todo lo que el equipo de frontend necesita para implementarlo.

- **Lienzos de diseño** (privados: se comparten desde el menú de cada lienzo):
  - etapa 1: <https://claude.ai/artifact/LMLBimwromQ5dsRQDDeD61>;
  - etapa 2: <https://claude.ai/artifact/7RUzqeAhtWx75vCNPCBRWe>;
  - etapa 3: <https://claude.ai/artifact/LTNxbVoceQL56sJ1F8BERr>.
- **Orden de trabajo y principio rector** [USR-10]: dominio → arquitectura de información → navegación → design system →
  patrones núcleo → Project 360 y territorio → grupos de interés, relacionamiento y compromisos → QCS, riesgos y programas →
  tableros → capa de IA. Ningún módulo es una aplicación aparte: la navegación conserva el contexto de proyecto y territorio y
  recorre la trazabilidad.

## Etapas

| Etapa | Contenido | Lienzo | Documentos | Estado |
|---|---|---|---|---|
| E1 | Visión, dominio, roles, arquitectura de información, navegación, hilo de contexto, design system y patrones núcleo | Páginas 00 a 04 (17 hojas) | [`ux-architecture.md`](ux-architecture.md), [`design-system.md`](design-system.md), [`tokens.css`](tokens.css) | Entregada, en revisión |
| E2 | Project 360 y territorio (GIS), grupos de interés, relacionamiento, compromisos y acuerdos | Lienzo de la etapa 2, páginas 05 a 10 (28 hojas) | [`screens.md`](screens.md) (S06–S26, S28–S33), [`flows.md`](flows.md) (flujos 1–11, 16 y 17) | Entregada, en revisión |
| E3 · parte 3a | QCS, riesgos y alerta temprana, programas e inversión, documentos, tareas y aprobaciones | Lienzo de la etapa 3, páginas 11 a 15 (18 hojas) | [`screens.md`](screens.md) (S35–S48 y la ficha de incidente), [`flows.md`](flows.md) (flujos 12–15) | Entregada, en revisión |
| E3 · parte 3b | Tableros (con S27), capa de IA, inicio, avisos, reportes y entregables, administración, campo sin conexión y handoff | Lienzo de la etapa 3, páginas 16 a 20 (25 hojas) | [`screens.md`](screens.md) completo (S01, S02, S04, S05, S27, S34, S49–S55, S62 y tableros), [`flows.md`](flows.md) (flujo 18 y flujo de campo), ASM-081 | Entregada, en revisión |
| Decisiones Q1, Q3, Q6 y Q7, y temas | Marca propia, idiomas, vistas compartidas, mapa base y tres temas | Lienzo de la etapa 3, páginas 22 a 25 (11 hojas) | [`design-system.md`](design-system.md) §2.1, §5, §6.4 y §9.1; [`screens.md`](screens.md) (S01, S12, S16, S28, S35 y S56); [`../gis.md`](../gis.md) §6.4; [`themes.css`](themes.css) y `design-system.md` §3.9; ASM-082 a ASM-086 | Entregada, en revisión |
| Rediseño de UX | Auditoría, arquitectura de información, design system, componentes y decisiones DD-01 a DD-29, implementados en `sgspe-ui` | [`redesign/04-prototipos/`](redesign/04-prototipos/README.md) (23 capturas) | [`redesign/`](redesign/README.md), llevado a `ux-architecture.md`, `design-system.md`, `screens.md` y `flows.md` el 2026-10-23 | Implementado; la carpeta queda como historia |

## Cómo leer

1. [`ux-architecture.md`](ux-architecture.md): visión, principios, decisiones heredadas de la Fase 0, roles, arquitectura de
   información, rutas, navegación, el hilo de contexto y el inventario de 56 pantallas.
2. [`design-system.md`](design-system.md): tokens, sistema de estados, componentes, patrones núcleo, accesibilidad, responsive y
   guía de implementación.
3. [`tokens.css`](tokens.css): los tokens listos para Tailwind 4, como capa sobre `@wasichai/ui/theme.css`, y
   [`themes.css`](themes.css): los temas Pino y piedra, Cielo y Pino de noche con los 28 tokens de wasichai.
4. [`screens.md`](screens.md): la ficha de handoff de cada pantalla (ruta, roles, acciones, datos y API, estados, permisos,
   responsive, navegación y árbol de componentes React).
5. [`flows.md`](flows.md): los flujos núcleo paso a paso, con la pantalla, la transición o el endpoint y la regla de cada paso.
6. [`redesign/`](redesign/README.md): el rediseño de UX del 2026-10-20, implementado y llevado a los documentos anteriores el
   2026-10-23. Queda como historia y como registro de las decisiones `DD-NN` que esos documentos citan.

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

## Lienzo de la etapa 3

| Página | Hojas |
|---|---|
| 11 Quejas, consultas y solicitudes | Índice de la etapa · Lista unificada (S35) · Registrar QCS (S37) · Ficha de queja, interactiva (S36) · Fichas de consulta y de solicitud (S57) |
| 12 Riesgos y alerta temprana | Registro y matriz, interactiva (S38–S39) · Ficha de riesgo (S40) · Centro de alerta temprana, interactiva (S41) · Ficha de incidente |
| 13 Programas e inversión | Programas y aportes (S42) · Ficha de programa (S43) · Aporte social con sus políticas (S44) |
| 14 Documentos, tareas y aprobaciones | Biblioteca (S45) · Ficha de evidencia (S46) · Mis tareas en lista, tablero y calendario, interactiva (S47) · Aprobaciones y escalamientos (S48) |
| 15 Flujos | Flujos 12 y 13 (queja) · Flujos 14 y 15 (riesgo y mitigación), en carriles por rol |
| 16 Tableros | Revisión ejecutiva de 5 minutos (S51) · Monitoreo de la Tabla 9.5, interactiva · Compromisos (S27) · QCS (S34) · Riesgos (S62) · Territorial · Inversión social |
| 17 Capa de IA | Panel del asistente, interactiva (S05) · Respuesta con citas (AI-06) · Bandeja de revisión (S52) · Posible duplicado (AI-07) · Resumen ejecutivo y borrador de sección (AI-04) |
| 18 Inicio y transversales | Login (S01) · Inicio por rol, interactiva (S02) · Avisos (S04) · Reportes y exportaciones (S49) · Entregable por secciones (S50) · Administración (S53) · Plantillas de entregables (S64) |
| 19 Campo sin conexión | Mis tareas y visita (S54) · Interacción, observación y foto (S55) · Estado de conexión y cola · Sincronización y conflictos |
| 20 Flujos y handoff | Flujo 18 (preguntar a la IA) · Flujo de campo (sincronización) · Mapa de rutas y árbol de módulos del frontend |
| 21 Shell responsive | Shell en tableta (panel de navegación) · Shell en teléfono (barra inferior por rol, «Más» y pantallas de escritorio) |
| 22 Marca SGSPE | Tres direcciones (elegida: C, «Sendero») · Sistema de marca: logotipo, área de respeto, versiones, tamaños mínimos, paleta, tipografía, usos incorrectos, voz y tono · Aplicaciones: barra superior, favicon, login, correo, portada de entregable y teléfono |
| 23 Idiomas | Misma pantalla en español, inglés y quechua, interactiva · Selector de idioma y reglas (textos que se alargan, traducción faltante, claves de ENUM) |
| 24 Vistas compartidas y mapa base | Guardar y compartir una vista, interactiva (S56) · Gestionar vistas (S56) · Mapa base por entorno (S12) |
| 25 Temas | Tres temas de SGSPE · Probador con la misma pantalla en cada tema y los de wasichai, interactiva · Selector e implementación |

## Convenciones

- Español para documentos, etiquetas y valores de ENUM; nombres técnicos, rutas y componentes en inglés (ADR-0009).
- Los IDs (RF, RNF, RN, RQ, CON, GAP, ASM, UPS) se definen solo en el [análisis](../requirements-analysis.md); los supuestos de
  esta carpeta son ASM-073 a ASM-086. Los identificadores de pantalla `S01` a `S56` se definen en el inventario de
  `ux-architecture.md` §9.
- Los datos de ejemplo del lienzo son ficticios: proyectos, comunidades, caseríos y personas inventados. En estos documentos
  las personas se nombran por su cargo [USR-07].
