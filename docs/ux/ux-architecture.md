# Arquitectura de UX de SGSPE

Sistema de Gestión Social para Proyectos Extractivos · Diseño UX/UI · etapas 1 y 2 de 3 · versión 0.3 · 2026-10-23

Este documento fija la visión de experiencia, la arquitectura de información, la navegación y el principio que ordena todo el
portal: **la navegación contextual y la trazabilidad**. Deriva del [análisis de requisitos](../requirements-analysis.md), del
[glosario](../glossary.md), del [modelo de dominio](../domain-model.md) y de los diseños de la Fase 0, que son la fuente de
verdad: los IDs (RF, RNF, RN, RQ, CON, GAP, ASM, UPS) se definen solo en el análisis y aquí solo se citan. El prompt maestro de
UX/UI es la fuente PRM-02 y la decisión del usuario sobre el orden de diseño y la navegación contextual es USR-10. Ningún ejemplo
usa nombres de personas reales [USR-07]: las personas del lienzo son ficticias y aquí se nombran por su cargo.

La parte visual vive en los lienzos de diseño de cada etapa (ver [README](README.md)). El sistema de diseño está en
[`design-system.md`](design-system.md), los tokens en [`tokens.css`](tokens.css), las fichas de handoff por pantalla en
[`screens.md`](screens.md) y los flujos en [`flows.md`](flows.md).

El rediseño de UX del 2026-10-20 ([`redesign/`](redesign/README.md)) está implementado en `sgspe-ui` y llevado a este
documento el 2026-10-23: el shell, el inicio «Mi trabajo», la sub-navegación de una sola fila, la administración en el
portal y los estados del sistema. Cada cambio cita su decisión (`DD-NN`, en
[`redesign/07-decisiones-y-compatibilidad.md`](redesign/07-decisiones-y-compatibilidad.md)); la carpeta queda como historia y
registro de decisiones.

## 1. Alcance y fuentes

- **Qué cubre esta etapa (E1):** dominio visto desde la interfaz, arquitectura de información, navegación, design system y
  patrones núcleo. Las pantallas de los módulos llegan en E2 (Project 360, territorio, grupos de interés, relacionamiento y
  compromisos) y E3 (QCS, riesgos y alertas, programas, documentos, tareas, tableros, capa de IA, móvil y handoff) [USR-10].
- **Fuentes:** PRM-02 (principios, entregables y pantallas pedidas), los diseños de la Fase 0 (qué existe, con qué estados,
  permisos y fases) y dos capturas de referencia de otra plataforma del rubro que el usuario compartió: de ellas se toma
  solo la idea de una sub-navegación por registro con conteos y la de un mapa de grupos de interés por cuadrantes, sin copiar
  su aspecto [PRM-02 §47].
- **Precedencia:** en el diseño de la interfaz rige USR > requisitos y diseños de la Fase 0 > PRM-02 > ASM (análisis §0.3).
  Donde PRM-02 choca con los documentos, cede (§4).
- **Qué no cubre:** el contrato de la API (`api.md`), los permisos por rol y campo (`security.md`), las capas y su
  sensibilidad (`gis.md`), los KPIs (`dashboards.md`) ni el comportamiento de la IA (`ai-architecture.md`). Este documento
  dice cómo la interfaz los presenta.

## 2. Visión de producto

SGSPE no debe parecer un CRM, un ERP, un sistema administrativo ni una hoja de cálculo sofisticada [PRM-02 §2]. Es una
plataforma de **desempeño social** para equipos que gestionan proyectos extractivos complejos, y su experiencia convierte
información social dispersa en una cadena visible:

**Contexto → Comprensión → Decisión → Acción** [PRM-02 §49]

Cada pantalla ayuda a responder, en este orden, las preguntas del hilo:

| Pregunta | Dónde se responde |
|---|---|
| ¿Qué ocurre en este proyecto? | Project 360 · Resumen |
| ¿Dónde ocurre? | Territorio y mapa del proyecto |
| ¿Quién está involucrado? | Grupos de interés |
| ¿Qué se habló? | Relacionamiento (interacciones y actas) |
| ¿Qué se prometió? | Compromisos y acuerdos |
| ¿Qué se ha cumplido? | Entregas y verificación |
| ¿Qué está en riesgo? | QCS, riesgos y alertas |
| ¿Qué evidencia existe? | Documentos y evidencias |
| ¿Qué debo hacer ahora? | Tareas y la "siguiente acción" de cada ficha |

La interfaz transmite **confianza, trazabilidad y control**: sin animaciones decorativas, sin gamificación, sin estética de
trading y con pocos colores [PRM-02 §43].

## 3. Principios

| # | Principio | Qué significa en SGSPE | Fuente |
|---|---|---|---|
| P1 | Contexto primero | Proyecto y territorio están siempre visibles y gobiernan lo que se lista | [PRM-02 §4] USR-10 |
| P2 | Ningún módulo es una aplicación aparte | La misma lista y la misma ficha sirven a la sección global y al proyecto (ASM-076) | USR-10 |
| P3 | La trazabilidad es navegación | Origen y consecuencias de cada registro están a un clic, sin perder el contexto (§8) | [PRM-02 §39] USR-10 |
| P4 | Divulgación progresiva | Pestañas, drawers, secciones plegables y profundidad máxima de tres niveles (§6.4) | [PRM-02 §4] |
| P5 | Densidad sin ruido | Tablas densas por defecto en escritorio, con modo cómodo; jerarquía antes que bordes | [PRM-02 §4, §25] |
| P6 | Buscar primero | Búsqueda global y comandos con ⌘K o Ctrl K en todas las pantallas (§7.3) | [PRM-02 §24] |
| P7 | Mapa y datos sincronizados | Seleccionar en el mapa filtra la lista; seleccionar una fila centra el mapa | [PRM-02 §12] RQ-GIS-014 |
| P8 | Tableros que piden acción | Cada cifra lleva a la lista que la compone; cada gráfico responde una pregunta | [PRM-02 §33] RQ-DSH-035 |
| P9 | Estado claro | Estado, situación de plazo, responsable y siguiente acción siempre a la vista en una ficha | [PRM-02 §15, §34] RN-WFL-001 |
| P10 | Hechos y sugerencias no se mezclan | Toda salida de IA lleva su marca, su evidencia y su confianza; nada sugerido parece un hecho | [PRM-02 §18, §23] RQ-AI-004 |
| P11 | Sin permiso no hay cifra | Lo que la persona no puede leer se muestra como "no disponible", nunca como cero | RQ-DSH-032 |
| P12 | Cada cambio crítico tiene un porqué | Las escrituras críticas piden motivo y lo envían en `X-Change-Reason` | RQ-AUD-002 ADR-0024 |
| P13 | Consistencia | Un patrón por problema: una sola ficha, una sola lista, un solo diálogo de transición | [PRM-02 §26] |
| P14 | Accesible por defecto | WCAG 2.2 AA, teclado completo, sin depender del color (design-system §7) | [PRM-02 §30] |
| P15 | Responsive con propósito | Escritorio primero, tableta después; en el teléfono solo campo, aprobaciones, avisos y consulta | [PRM-02 §31] |

## 4. Decisiones heredadas de la Fase 0

PRM-02 propone navegación, estados y entidades como punto de partida y pide validarlos contra la documentación [PRM-02 §1, §5].
Donde choca con la Fase 0, la interfaz sigue a la Fase 0:

| Tema | PRM-02 | Fase 0 | Decisión de diseño |
|---|---|---|---|
| Navegación global | 14 secciones, con "AI Assistant" | 13 secciones fijas en español y en orden (RNF-USA-001, ADR-0024) | El asistente no es sección: es un panel global y contextual, y las propuestas se revisan en la bandeja del proyecto (ASM-073) |
| Alerta temprana | Pantalla propia | Vive en la sección Riesgos (architecture §9.2) | Centro de alerta temprana como vista de Riesgos (`/risks/warnings`) |
| Grievances | Un objeto | QCS son tres objetos con flujos propios y reporte unificado (ASM-011, CON-033) | Una lista y un tablero unificados; la ficha cambia según el tipo |
| "Overdue" y "Due soon" | Estados | Situación derivada `deadline_status`, nunca un estado (RN-WFL-001, CON-028) | Insignia de plazo junto al estado, nunca en su lugar |
| Estados | Listas genéricas | Estados de WF-A a WF-P (`workflows.md`) | Se usan los estados reales con su etiqueta en español |
| Stakeholder | Una entidad | Actor del tenant y grupo de interés por proyecto; comunidad ≠ localidad ≠ área de influencia (CON-038, RN-STK-008) | Ficha de actor y ficha de grupo de interés separadas; el territorio tiene fichas propias |
| Stakeholders en el mapa | Capa de puntos | Solo agregados por localidad o comunidad (LYR-17, RQ-GIS-015) | Burbuja con el conteo; nunca un punto por persona |
| Columnas de grupos de interés | Influence, Interest, Relationship, Risk | Interés y poder (1–5), grupo de influencia, postura restringida, estado de la relación (RF-STK-008, RQ-SEC-010) | Esas columnas; "riesgo" es el conteo de riesgos vinculados |
| Executive | Usuario principal | No es uno de los 17 roles | Persona con lectura e inicio propio (ASM-074) |
| Cambio masivo de fecha | Acción masiva | La fecha de un compromiso aprobado solo cambia con una reprogramación (RF-COM-003) | No hay cambio masivo de fecha; se reprograma por compromiso |
| Evidencia al crear un compromiso | Paso 5 del formulario | La evidencia se exige al registrar el cumplimiento (RN-COM-001) | El formulario de alta no pide evidencia (design-system §6.3) |
| Asistente para todos | Global y contextual | AI-05 para todo rol que pueda registrar `ai_interaction`, seudonimizado (ASM-130) | Estado "no disponible para tu rol" diseñado para los demás (`READ_ONLY`, `AUDITOR`, `PROJECT_ADMIN`); resúmenes y propuestas revisables sí disponibles |
| Sincronización móvil | "Sync" | Captura sin conexión fuera del MVP (ASM-021, GAP-008) | El usuario decidió diseñarla (ASM-081): cola local, sincronización y conflictos visibles; se implementa después del MVP |
| Idioma | Etiquetas en inglés | Interfaz en español es-PE con inglés; ENUM en MAYÚSCULAS españolas (RNF-I18N-001, ADR-0009) | El usuario pidió español, inglés y quechua (ASM-084): español por defecto, valores de ENUM traducidos en el portal por clave; `/admin`, metadata y entregables en español; rutas técnicas en inglés (ASM-075) |
| Montos | "Social investment" | Por moneda, nunca sumadas; PEN por defecto (ASM-063) | Cada total con su moneda; lo de otra moneda, como advertencia |

## 5. Usuarios y trabajos por hacer

La interfaz no se diseña como si todos tuvieran las mismas necesidades [PRM-02 §3]. El orden de las secciones es el mismo para
todos (RNF-USA-001); por rol cambian las secciones visibles (RF-IAM-002), lo que cuentan las insignias, los atajos de creación
y qué bloques trae el inicio. El inicio es el mismo para todos, «Mi trabajo» (§7.6, DD-01): sus bloques se ofrecen por lo que
la cuenta lee y puede hacer, nunca por el nombre del rol, y un bloque sin nada legible no se dibuja. El mecanismo de ASM-078
(una variante de inicio que se ofrece con ciertos permisos, `inicios.ts`) sigue disponible, pero no hay ninguna registrada: la
columna «Lo primero del inicio» dice qué bloques llegan con los permisos típicos del rol.

| Rol | Su pregunta | Lo primero del inicio | Acciones que definen el rol |
|---|---|---|---|
| Gerente social (`SOCIAL_MANAGER`) | ¿Cómo está la situación social y qué decido hoy? | «Esperan su decisión» (aprobaciones, alertas por triar) y «Lo que me toca hoy» | Aprobar actas, verificar compromisos, decidir mitigación, confirmar alertas |
| Jefe de proyecto (`PROJECT_MANAGER`) | ¿Qué compromisos y riesgos amenazan mi proyecto? | «Mis proyectos» con lo que pide acción en cada uno, que lleva a Project 360 | Aprobar en nivel superior y sobre el tope; cancelar compromisos |
| Relacionista comunitario (`COMMUNITY_RELATIONS_OFFICER`) | ¿Con quién me reúno, qué prometimos y qué quedó pendiente? | «Lo que me toca hoy» y «Agenda de la semana» | Registrar interacciones y actas, acusar quejas, caracterizar grupos de interés |
| Gestor de grupos de interés (`STAKEHOLDER_MANAGER`) | ¿Quiénes son, cuánto pesan y qué postura tienen? | «Lo que me toca hoy» y «Mis proyectos»; la matriz interés × poder, desde el proyecto | Evaluar (escribe la postura), retirar y reincorporar |
| Gestor de compromisos (`COMMITMENT_MANAGER`) | ¿Qué debemos, a quién y desde cuándo? | «Lo que me toca hoy»: sus compromisos vencidos y por vencer, primero | Enviar, iniciar, registrar entrega, verificar lo que no le toca como responsable |
| Gestor de QCS (`GRIEVANCE_MANAGER`) | ¿Qué nos reclaman y qué plazo vence primero? | «Lo que me toca hoy»: sus QCS por el plazo que corre | Evaluar, atender, resolver, rechazar con respuesta, reclasificar |
| Gestor de riesgos sociales (`SOCIAL_RISK_MANAGER`) | ¿Qué puede detener el proyecto y qué hacemos? | «Esperan su decisión» con las alertas por triar, y «Mis proyectos» con los riesgos altos | Revisar riesgos, decidir tratamiento, triaje y confirmación de alertas |
| Gestor de inversión social (`SOCIAL_INVESTMENT_MANAGER`) | ¿Cuánto invertimos frente a lo previsto y a quién llega? | «Lo que me toca hoy» y «Mis proyectos»; Programas sociales por la navegación | Aportes, presupuesto por moneda, beneficiarios e indicadores |
| Analista SIG (`GIS_ANALYST`) | ¿El territorio y las áreas están bien dibujados? | «Mis proyectos»; Territorio por la navegación | Importar con vista previa y reconciliación, editar geometrías, versionar áreas |
| Agente de campo (`FIELD_AGENT`) | ¿Qué tengo que levantar hoy y dónde? | «Lo que me toca hoy» y las visitas que lidera en «Agenda de la semana» | Crear interacción, observación y foto con ubicación; solo edita lo que capturó (ASM-069) |
| Aprobador (`APPROVER`) | ¿Qué espera mi aprobación y cumple las reglas? | «Esperan su decisión», que lleva a Aprobaciones (S48) | Aprobar con segregación de funciones; resolver quejas de severidad alta |
| Ejecutivo (`READ_ONLY` o `ANALYST`, ASM-074) | ¿Qué cambió, qué es crítico, dónde y quién lo atiende? | «Visión ejecutiva» con las cifras que lee; la revisión de 5 minutos en `/dashboards/executive` | Solo lectura y exportación |
| Auditor (`AUDITOR`) | ¿Quién cambió qué, cuándo y por qué? | «Mis proyectos» o, sin asignaciones, los accesos a las secciones que lee | Lectura de todo el tenant |
| Analista (`ANALYST`) | ¿Qué dicen los datos del periodo? | «Visión ejecutiva»; Reportes por la navegación | Usa "Preguntar a los datos" (AI-05, ASM-130), no "Preguntar a los documentos", porque no lee los nombres de `person` |
| Administración (`PROJECT_ADMIN`, `ORGANIZATION_ADMIN`) | ¿Quién trabaja en qué proyecto y con qué permisos? | «Registrar» con «Nuevo proyecto» y «Mis proyectos» | Alta y aprobación de proyectos, equipos por asignación; usuarios y roles desde Administración (S53), que abre `/admin` |

`READ_ONLY` ve los bloques del rol al que acompaña; `SUPER_ADMIN` opera la plataforma y no ve datos de los tenants
(`security.md` §6.2).

## 6. Arquitectura de información

### 6.1 Tres contextos, una aplicación

| Contexto | Pregunta | Qué contiene | Cómo se entra |
|---|---|---|---|
| Global | ¿Qué pasa en mis proyectos? | Las 13 secciones; cada lista muestra por defecto los proyectos con asignación activa de la persona, como los tableros (dashboards §3.1) | Navegación principal |
| Proyecto (Project 360) | ¿Qué pasa aquí? | Resumen, mapa, áreas de influencia y todas las listas del dominio con el alcance del proyecto | Selector de proyecto, lista de proyectos o cualquier ficha con proyecto |
| Territorio | ¿Qué pasa en este lugar? | Mapa territorial, fichas de comunidad, localidad y unidad administrativa, línea base social; un filtro que atraviesa el proyecto | Mapa, búsqueda o un vínculo territorial de cualquier ficha |

Separar global de proyecto no duplica pantallas: **la misma página se monta con otro alcance** (ASM-076). El territorio es
del tenant (`domain-model.md` §2); el proyecto es el lente con que se mira. En Territorio, ese lente es el selector de la
barra superior: lee y escribe `?project=` sin salir de la pantalla y, sin proyecto, dice "Todo el territorio". La pantalla
no tiene un selector de proyecto propio (revisión de UX, segunda pasada del 2026-10-20).

### 6.2 Mapa del sitio y rutas

Rutas técnicas en inglés y kebab-case (ADR-0009, ASM-075). La columna "En el proyecto" es la ruta bajo
`/projects/:projectId`.

| Sección (RNF-USA-001) | Global | En el proyecto | Fase de llegada |
|---|---|---|---|
| Dashboard | `/` (inicio «Mi trabajo»), `/dashboards/executive` (S51, DD-19), `/dashboards/monitoring`, `/dashboards/indicators` | `/overview` (Resumen) | F1 bienvenida; F8 consolidado |
| Proyectos | `/projects`, `/projects/new` | `/overview`, `/map`, `/areas/:areaId`, `/settings`, `/activity` | F2 |
| Territorio | `/territory`, `/territory/dashboard`, `/territory/quality`, `/territory/communities/:id`, `/territory/localities/:id`, `/territory/admin-units/:id`, `/territory/imports/:id` | `/map` | F2; F6 mapa territorial, ficha, tablero y calidad de datos |
| Grupos de interés | `/stakeholders`, `/actors/:actorId` | `/stakeholders`, `/stakeholders/matrix`, `/stakeholders/:stakeholderId` | F2 |
| Relacionamiento | `/engagements`, `/engagements/calendar`, `/field-visits` | `/engagements`, `/engagements/calendar`, `/engagements/:engagementId`, `/engagements/new`, `/engagements/:engagementId/minutes`, `/field-visits/:visitId` | F2 |
| Compromisos | `/commitments`, `/commitments/dashboard`, `/agreements` | `/commitments` (con la vista `?view=candidates`), `/commitments/:commitmentId`, `/commitments/new`, `/agreements`, `/agreements/:agreementId` | F3 |
| Quejas, consultas y solicitudes | `/attention`, `/attention/dashboard` | `/attention/new`, `/grievances/:id`, `/inquiries/:id`, `/requests/:id` | F4 |
| Riesgos | `/risks`, `/risks/register`, `/risks/warnings` | `/risks/:riskId`, `/warnings/:warningId`, `/incidents/:incidentId` | F4 |
| Programas sociales | `/programs`, `/programs/dashboard`, `/contributions` | `/programs/:programId`, `/contributions/:contributionId` | F5 (tope y lineamientos desde F3) |
| Documentos | `/documents` | `/documents/:documentId` | F3 |
| Tareas | `/tasks`, `/tasks/approvals` | `/tasks` (del proyecto) | F3 |
| Reportes | `/reports`, `/reports/deliverables/:deliverableId` | `/reports` (del proyecto) | F2–F4 exportaciones; F8 entregables |
| Administración | `/administration` (S53, DD-20), que abre la consola de wasichai (`/admin`) en otra pestaña | `/settings` (equipo y parámetros del proyecto) | F1 |
| Transversal | `/notifications`, panel del asistente, búsqueda ⌘K, `/field/queue` (cola de campo, móvil) | `/ai-review` (bandeja de revisión de IA) | F3 avisos; F7 IA; campo sin conexión después del MVP (ASM-081) |

### 6.3 Fichas con una sola URL

- Todo registro que pertenece a un proyecto tiene su ficha bajo ese proyecto: `/projects/:projectId/commitments/:commitmentId`
  [PRM-02 §45]. Abrirlo desde cualquier lista entra al contexto del proyecto, aunque se venga de la sección global.
- Los maestros del tenant tienen ficha propia fuera de los proyectos: actores (`/actors/:actorId`) y territorio
  (`/territory/...`). Pueden llevar `?project=:projectId` para mostrar lo de ese proyecto primero.
- Un enlace compartido abre la misma vista con los permisos de quien lo abre; si no puede leer el registro, ve «El registro no
  existe o no es de sus proyectos» (§7.7), sin saber cuál de las dos.

### 6.4 Profundidad

1. **Lista o mapa** de una sección o pestaña.
2. **Ficha** del registro.
3. **Drawer de vista previa** (un registro relacionado sin salir) o **diálogo de decisión** (una transición).

Nunca un modal sobre otro modal. Un drawer se reemplaza por otro, no se apila; el rastro (§8) recuerda lo recorrido.

## 7. Navegación

### 7.1 Shell

- **Barra superior (56 px), seis controles** (DD-09): marca, selector de proyecto, «Buscar» (⌘K), «Preguntar» del
  asistente (§7.5), la campana de avisos y la cuenta con el nombre y los rótulos de sus roles. El selector de proyecto es un
  combobox con búsqueda: «Todos mis proyectos» o uno; en Territorio, «Todo el territorio» o el proyecto que hace de lente
  (§6.1). El idioma (cada uno nombrado en su propia lengua), el tema y «Salir» van en el menú de la cuenta, con los datos
  de la cuenta; nada de eso va suelto en la barra. El lema de la marca se ve solo desde 1536 px (`min-[96rem]`, DD-24): en
  SGSPE, `2xl` es 1440 px, y a ese ancho la barra lleva solo sus seis controles. Antes de ingresar, el idioma se elige en
  el login (ASM-084).
- **Navegación principal:** las 13 secciones en su orden, con ícono y etiqueta, y «Administración» (S53, `/administration`),
  visible solo para quien administra (ADR-0024, DD-20). Sin cambiar ese orden, las secciones van en bloques contiguos con
  título visible: Inicio; «Proyectos y territorio» (Proyectos, Territorio); «Trabajo con las comunidades» (Grupos de
  interés, Relacionamiento, Compromisos, QCS); «Riesgos e inversión social» (Riesgos, Programas sociales); «Documentos y
  seguimiento» (Documentos, Tareas, Reportes); y Administración aparte, sin título. Un bloque sin secciones para la cuenta no
  se dibuja. En la barra, QCS se dice «Quejas y solicitudes», con el nombre completo en el `title`; las migas, el título del
  documento y la guarda siguen con «Quejas, consultas y solicitudes» (DD-10). Las insignias cuentan solo lo que pide acción
  (las tareas pendientes).
- **Ancho de la navegación:** expandida a 240 px desde `lg` (1024 px), con «Contraer» al pie, que la deja en 52 px con solo
  los íconos y el nombre en el `title`, y una línea entre bloques. La elección se recuerda en el navegador
  (`localStorage`, `sgspe.navegacion`; DD-22), no en las preferencias de la cuenta. Entre 768 y 1023 px la barra lateral
  muestra solo los íconos.
- **Tableta y teléfono (< 768 px):** la barra lateral desaparece y el botón de menú de la barra superior abre un panel con
  los datos de la cuenta, el selector de proyecto, las secciones en sus bloques y, al pie, idioma, tema y «Salir». El panel
  se cierra con ×, Esc, tocando fuera o al elegir una sección, y devuelve el foco al botón. En la barra superior quedan el
  menú, la marca, un chip con el alcance actual (abre el panel), «Buscar» y «Preguntar» como íconos, la campana y, desde
  640 px, la cuenta.
- **Teléfono (< 640 px):** una barra inferior de cuatro destinos con objetivos de 48 px, la misma para toda cuenta (DD-17):
  Inicio · Tareas (si la cuenta tiene la sección) · Avisos · Más, con las insignias de tareas y avisos. «Más» abre el panel
  del menú. Una ficha con barra fija de acciones la deja sobre la barra inferior. Los destinos propios del campo sin conexión
  (Capturar, Cola) y las variantes por rol de la hoja `Shell-Telefono` (lienzo de la etapa 3, página 21) son del diseño de
  ASM-081 y no están implementados.
- **Sub-navegación de proyecto:** dentro de un proyecto, la navegación principal marca la sección del módulo abierto
  (`/projects/:projectId/commitments/…` marca Compromisos, no Proyectos), y sobre el contenido van la barra de contexto
  (migas, estado y chip de territorio) y **una sola fila de pestañas** (DD-03). Cada pestaña lleva el total del alcance
  (gris) y una insignia con lo que pide acción (vencidos, fuera de plazo, alertas, propuestas de IA por revisar). La pestaña
  del registro abierto queda marcada también en las fichas que viven en otro segmento (`grievances`, `inquiries` y `requests`
  marcan QCS; `contributions`, Programas), y las migas la nombran. Si la fila no cabe, se desplaza, y la pestaña activa queda
  siempre a la vista, también cuando las pestañas y sus conteos llegan después.
- **Pestañas de Project 360:** nueve visibles y «Más»: Resumen · Mapa · Grupos de interés · Relacionamiento · Compromisos ·
  QCS · Riesgos · Programas · Documentos · «Más» (Tareas, Bandeja de IA, Reportes, Equipo y parámetros). Si la abierta está
  en «Más», el botón la nombra y queda marcado. Una pestaña de otra sección se ofrece con los permisos de esa sección. Los
  conteos se leen con `size=1` y `totalElements` por objeto.
- **Vistas y páginas de un módulo:** nunca una segunda fila de pestañas. Las vistas de una lista (Abiertos, Vencidos,
  Míos…) van en el `SelectorDeVista`, el primer control de su barra de filtros, con su conteo y `?view=` en la URL (DD-03).
  Compromisos y Acuerdos, y las páginas de Riesgos (registro y matriz, alerta temprana, señales, menciones, impactos,
  incidentes y reglas), son rutas distintas: van en un `SelectorDePagina` en la cabecera, que navega entre ellas sin cambiar
  ninguna ruta (DD-23). Fuera de un proyecto, Riesgos ofrece solo el registro y las reglas de la organización.
- **Aterrizaje de cada módulo:** la lista de trabajo, con «Lista · Tablero» como conmutador de la cabecera
  (`VistasDelModulo`). Riesgos abre el registro y la matriz (S38 y S39) y su tablero (S62) es la vista «Tablero». Programas
  sociales abre la vista «Programas»; los aportes son otras vistas del mismo selector (DD-27).

### 7.2 Por rol

- **Visibilidad:** una sección se dibuja si tiene pantalla registrada y la persona tiene sus permisos (RF-IAM-002). Mientras no
  exista UPS-06, el portal deduce las capacidades de `GET /api/auth/me/permissions` (architecture §9.1).
- **Orden:** fijo (RNF-USA-001). No se reordena por rol.
- **Inicio:** «Mi trabajo» para todos, con bloques que se ofrecen por capacidad (§7.6, DD-01). El mecanismo de variantes
  de ASM-078 no tiene ninguna registrada.
- **Insignias:** cuentan lo que la cuenta debe resolver, nunca un total: en la navegación, las tareas pendientes; en las
  pestañas del proyecto, lo vencido o por atender del módulo.
- **Atajos:** las creaciones que la cuenta puede hacer, en el bloque «Registrar» del inicio: «Registrar interacción»,
  «Registrar QCS», «Nuevo compromiso» y «Nuevo proyecto», cada una si la cuenta tiene `CREATE` sobre su objeto.

### 7.3 Búsqueda global y comandos

- Se abre con ⌘K o Ctrl K desde cualquier pantalla [PRM-02 §24]. Busca proyectos, territorio, grupos de interés, actores,
  compromisos, QCS, riesgos, programas y documentos, solo entre lo que la persona puede leer.
- Antes de escribir: recientes, búsquedas sugeridas por rol e "Ir a…".
- Al escribir: resultados agrupados por categoría con conteo, pestañas por categoría, vista previa del resultado activo y
  acciones contextuales (filtrar el proyecto por un lugar, registrar una interacción allí, preguntar a la IA, si está
  disponible).
- Teclado: ↑ ↓ para moverse, ↵ abre la ficha, ⇧ ↵ la abre en el drawer, ⌘ ↵ filtra el proyecto por el lugar, Tab cambia de
  categoría, # busca por código (COM-, QCS-, RSK-).
- Dentro de un proyecto, la búsqueda prioriza el proyecto y ofrece "buscar en todos".

### 7.4 Avisos

Bandeja en la barra superior y página `/notifications` (RF-NTF-001): por vencer, vencido, aprobación pendiente, riesgo escalado,
alerta nueva. Cada aviso lleva a la ficha con el contexto puesto y se marca como leído al abrirlo. La campana muestra los cinco
últimos; el diseño está en [`screens.md`](screens.md) S04.

### 7.5 El asistente de IA en la navegación

No es una sección (ASM-073). El botón "Preguntar" de la barra superior abre el asistente global; dentro de una ficha, el botón
"Explicar" o "Preguntar sobre…" abre el mismo panel con el registro como contexto. Las preguntas a los
datos (AI-05) están disponibles para todo rol que pueda registrar `ai_interaction` (RF-AIK-005, ASM-130), y las
preguntas a los documentos (AI-06), para quien además lee los nombres de `person`; el resto ve el estado "no disponible
para tu rol" con la razón, y sigue viendo los resúmenes y las propuestas revisables. El panel tiene dos modos:
"Preguntar a los datos" (AI-05) y "Preguntar a los documentos"
(AI-06, con citas y nunca sin fuente); su diseño está en [`screens.md`](screens.md) S05 y el recorrido, en el flujo 18.

### 7.6 Inicio «Mi trabajo»

El inicio (`/`, S02) es el mismo para toda cuenta y contesta por urgencia, no por módulo (DD-01). Cada bloque se ofrece por
lo que la cuenta lee y puede hacer, y no se dibuja si no tiene nada legible: el inicio nunca es un muro de «No disponible».
Ningún bloque cuenta lo que la cuenta no lee, porque cada uno se arma con lo que la API le devuelve (ADR-0029). La revisión
ejecutiva (S51) ya no es el inicio de nadie: vive en `/dashboards/executive` (DD-19, ASM-074).

| Orden | Bloque | Se ofrece si la cuenta… | Qué muestra |
|---|---|---|---|
| 1 | **Lo que me toca hoy** | Tiene a su cargo tareas, compromisos o QCS abiertos, o tareas sin asignar en sus proyectos | Lo abierto a su cargo, con lo vencido primero, luego lo por vencer y lo demás por fecha: cinco a la vista con su motivo y su fecha, y el resto desplegable en el lugar. Debajo, «Para tomar en …»: las tareas abiertas sin asignar de cada proyecto con asignación vigente, tres por proyecto, con «Ver los N» hacia la lista de tareas del proyecto |
| 2 | **Esperan su decisión** | Recibe avisos de aprobación, puede confirmar alertas tempranas o tiene avisos que piden acción sin leer | Aprobaciones (como S48), alertas por triar y avisos que piden acción, cada grupo con lo más urgente, su motivo y su enlace (`ListaPriorizada`) |
| 3 | **Agenda de la semana** | Lee visitas de campo o interacciones | De hoy a siete días: las visitas que lidera y las interacciones programadas en sus proyectos con asignación vigente, porque la interacción no tiene responsable (DD-26) |
| 4 | **Registrar** | Tiene `CREATE` sobre alguno de sus objetos | «Registrar interacción», «Registrar QCS», «Nuevo compromiso» y «Nuevo proyecto»; si hace falta, piden el proyecto entre los que puede escribir |
| 5 | **Mis proyectos** | Tiene asignaciones vigentes | Una tarjeta por proyecto con su estado, su jefatura (ASM-080) y lo que pide acción: compromisos vencidos, QCS vencidas y riesgos altos o críticos, cada cifra hacia su lista. Una cifra en cero no se dibuja; una que no lee o no llegó es «—», nunca cero. Con más de seis, los seis con más pendientes y «Ver todos» |
| 6 | **Visión ejecutiva** | Lee al menos una cifra del tablero ejecutivo que el catálogo ofrece | Solo las cifras disponibles para la cuenta, con su fecha de cálculo y «Ver la revisión ejecutiva» |

- La cabecera dice «Inicio» con el saludo y, a quien lee los indicadores, los accesos al monitoreo y al catálogo.
- Sin ningún bloque (una cuenta nueva, sin asignaciones ni permisos de creación), el inicio es la bienvenida de F1: las
  secciones que la cuenta puede abrir, como accesos.
- Los datos de la cuenta (usuario, organización y roles por su rótulo) viven en el menú de la cuenta, no en el inicio.

### 7.7 Estados del sistema

Cada estado tiene una pieza y una salida (DD-12, `redesign/05` §3). La confidencialidad no cambia: ningún estado distingue
«no existe» de «no es suyo», ningún conteo incluye lo ilegible, y una cifra que excluye QCS confidenciales lleva
`NotaDeConfidenciales` sin número.

| Estado | Pieza | Qué dice | Salida |
|---|---|---|---|
| Cargando | `Cargando` | Esqueleto; «Cargando…» solo en lo compacto | — |
| Guardando | `Button` con `aria-busy` | La etiqueta no cambia | El doble envío se bloquea (`useComando`) |
| Vacío | `Vacio` | Qué falta y por qué importa | La acción de creación, si la cuenta puede |
| Vacío por filtros | `Vacio` | Que ningún registro coincide con los filtros | «Limpiar filtros» |
| Error de carga | `ErrorDeCarga` | Qué no se pudo cargar; en una lista, los filtros siguen a la vista | «Reintentar» |
| Sin acceso | `EstadoSinAcceso` | «No puede abrir «…»», por qué en lenguaje llano (sin nombrar roles ni permisos) y a quién pedirlo: dentro de un proyecto, la jefatura por su nombre si la cuenta lee las asignaciones; si no, la administración de la organización | «Volver al resumen del proyecto» o «Volver al inicio», lo primero que alcanza el teclado |
| Registro reservado | `Reservado` y `RegistroVinculado` | «Registro reservado» | Ninguna: no se revela nada |
| No existe o no es suyo | `NoEsDeSusProyectos` | Que el registro no existe o no es de sus proyectos | «Ir a Proyectos» |
| Conflicto (412) | `AvisoDeConflicto`, `BotonRecargar` | Que otra persona cambió el registro y qué se conserva | «Recargar» |
| Éxito | `Alert` o aviso breve | Qué pasó y qué sigue | El enlace a lo siguiente |

## 8. Hilo de contexto y trazabilidad

Es el principio que más distingue a SGSPE de un CRM [USR-10]. La persona debe poder pasar de una comunidad en el mapa a sus
grupos de interés, a una interacción, al compromiso que generó y a la evidencia que lo sustenta **sin perder el contexto del
proyecto y del territorio**, y volver a cualquier punto.

### 8.1 Piezas

| Pieza | Qué hace | Dónde vive el estado |
|---|---|---|
| Barra de contexto | Muestra el proyecto y el chip de territorio; quitar el chip devuelve el proyecto entero sin cambiar de pantalla | Ruta (`/projects/:projectId`) y consulta (`?territory=community:<id>`) |
| Chip de territorio | Filtra todas las listas y tableros del proyecto por una comunidad, localidad, unidad administrativa o área de influencia, con la misma regla de inclusión de los tableros (dashboards §3.1) | Consulta de la URL |
| Rastro | Lista lo recorrido (por ejemplo, Mapa · CC Tunas Pampa › Junta directiva › Asamblea › COM-2026-031 › Acta); cada paso vuelve a esa vista con sus filtros | Estado del historial del navegador (sesión) |
| Breadcrumbs | Dicen dónde vive el registro (Proyectos / Pukaqaqa / Compromisos / COM-2026-031); no cambian con el recorrido | Derivado de la ruta |
| Drawer de vista previa | Abre un registro relacionado sin salir de la lista o del mapa; "Abrir ficha" lo convierte en la vista principal | Consulta de la URL (`?peek=engagement:<id>`) |
| Panel de trazabilidad | En toda ficha: "De dónde viene" (origen), "Qué generó" (consecuencias) y "Lo sustenta" (evidencias y documentos que lo respaldan), cada vínculo navegable | Relaciones del modelo (§8.2) |
| Mapa y lista sincronizados | Seleccionar una geometría filtra la lista; seleccionar una fila centra y resalta el mapa | Consulta de la URL (`?selected=`) |

### 8.2 Cadenas que la interfaz recorre

Cada eslabón es una relación real del modelo, así que cada flecha es un enlace en pantalla [PRM-02 §39].

**Del territorio a la evidencia**

| Desde | Hacia | Por | Dónde se muestra |
|---|---|---|---|
| Proyecto | Área de influencia vigente y sus miembros | `area_of_influence`, `area_of_influence_member` (versión `approved`) | Project 360 · Mapa y ficha del área |
| Comunidad o localidad | Grupos de interés del lugar | Relación territorial del `stakeholder` (`locality`, `area_of_influence`), con la regla de inclusión de los tableros (dashboards §3.1) | Ficha contextual del mapa y ficha territorial |
| Grupo de interés | Interacciones | M2M `engagement_stakeholder`, `engagement_participant` | Ficha del grupo de interés › Relacionamiento |
| Interacción | Compromisos generados | `commitment.origin_engagement` | Ficha de la interacción › Acuerdos |
| Compromiso | Tareas, entregas y evidencias | `task` de origen, `delivery`, `evidence_delivery` y vínculos de evidencia | Panel de trazabilidad de la ficha |
| Evidencia | Todo lo que sustenta | Vínculos de `evidence` | Ficha o drawer de la evidencia › Sustenta |

**De la queja a la mitigación**

| Desde | Hacia | Por |
|---|---|---|
| Pregunta de una interacción | QCS derivada | `origin_question` |
| Queja | Riesgo que alimenta | `grievance.social_risk` |
| Solicitud aceptada | Compromiso o aporte que originó | `stakeholder_request.created_commitment` (RF-GRV-004) |
| Alerta confirmada | Riesgo | `early_warning` → `social_risk` |
| Riesgo | Medidas de mitigación y revisiones | `mitigation_measure`, `risk_assessment` |

**De la inversión a los indicadores**

| Desde | Hacia | Por |
|---|---|---|
| Proyecto | Programas e iniciativas | `social_program`, `social_initiative` |
| Programa | Presupuesto por moneda | `program_budget` |
| Programa o iniciativa | Beneficiarios | `beneficiary_group` |
| Programa | Indicadores y mediciones | `program_indicator`, `program_measurement` |

### 8.3 Recorrido de referencia

El lienzo trae un prototipo navegable de este recorrido ("Hilo de contexto"):

1. **Mapa del proyecto.** Se selecciona la CC Tunas Pampa; la ficha contextual muestra las cifras de KPI-TER-01 y "Ver sus 12
   grupos de interés".
2. **Grupos de interés.** La lista hereda el chip "CC Tunas Pampa"; una fila abre la vista previa con evaluación vigente e
   interacciones recientes.
3. **Interacción.** La asamblea muestra etapas, participantes, temas, evidencias y los acuerdos que generaron compromisos.
4. **Compromiso.** Estado, plazo, responsable y siguiente acción a la vista; el panel de trazabilidad enlaza origen y
   consecuencias.
5. **Evidencia.** Se abre en el drawer sobre el compromiso: huella, clasificación y "Sustenta". El rastro permite volver a
   cualquiera de los pasos.

### 8.4 Reglas

- **Nada cambia el contexto sin que se vea.** Entrar a una ficha de otro proyecto cambia la barra de contexto y lo anuncia; el
  chip de territorio se conserva solo si el registro pertenece a ese lugar.
- **Volver nunca pierde filtros.** El rastro y el botón atrás del navegador devuelven la lista con sus filtros, su orden y su
  posición.
- **El contexto es compartible.** Proyecto, territorio, filtros, vista previa y selección viven en la URL; el rastro, no.
- **Los permisos mandan.** Un eslabón que la persona no puede leer se muestra como "no disponible", sin ocultar que existe,
  salvo cuando su sola existencia revela datos personales (por ejemplo, una QCS anónima).
- **La IA respeta el hilo.** El asistente contextual recibe el registro abierto y el contexto de la barra, y sus respuestas
  enlazan a registros del mismo hilo (E3).

## 9. Inventario de pantallas

Las 38 pantallas de PRM-02 §37, ajustadas a la Fase 0: 56 pantallas (S56 se suma con las vistas compartidas, ASM-083),
más S57 a S62 de la Fase 4 (plan 4.0): la ficha de consulta y solicitud, que compartía S36 con la queja, las de
menciones, señales, reglas e impactos, y el tablero de riesgos, que compartía S38 con el registro y matriz. S63, la
calidad de datos de Territorio, se suma en la Fase 6 (plan 6.0), y S64, las plantillas de entregables, en la Fase 8
(plan 8.0). "Etapa" es la etapa de diseño en que se dibujan; "Fase", cuándo llega el módulo. La ficha de handoff de cada
pantalla (ruta, roles, datos, API, estados, permisos, árbol de componentes) está en [`screens.md`](screens.md), completa
desde la etapa 3.

| ID | Pantalla | Ruta | Fase | Etapa |
|---|---|---|---|---|
| S01 | Login | `/login` | F1 | E3 |
| S02 | Inicio «Mi trabajo» | `/` | F1, F8 | E3 |
| S03 | Búsqueda global y comandos | ⌘K | F2 | E1 |
| S04 | Avisos | `/notifications` | F3 | E3 |
| S05 | Asistente de IA (panel) | panel lateral | F7 | E3 |
| S06 | Lista de proyectos | `/projects` | F2 | E2 |
| S07 | Crear proyecto | `/projects/new` | F2 | E2 |
| S08 | Project 360 · Resumen | `/projects/:projectId/overview` | F2, F8 | E2 |
| S09 | Project 360 · Mapa | `/projects/:projectId/map` | F2, F6 | E2 |
| S10 | Área de influencia | `/projects/:projectId/areas/:areaId` | F2 | E2 |
| S11 | Equipo y parámetros del proyecto | `/projects/:projectId/settings` | F2 | E2 |
| S12 | Mapa territorial | `/territory` | F2, F6 | E2 |
| S13 | Ficha de comunidad | `/territory/communities/:id` | F2, F6 | E2 |
| S14 | Ficha de localidad | `/territory/localities/:id` | F2, F6 | E2 |
| S15 | Importación geoespacial | `/territory/imports/:id` | F2, F6 | E2 |
| S16 | Lista de grupos de interés | `/stakeholders` | F2 | E2 |
| S17 | Ficha de grupo de interés | `/projects/:projectId/stakeholders/:stakeholderId` | F2 | E2 |
| S18 | Ficha de actor | `/actors/:actorId` | F2 | E2 |
| S19 | Matriz interés × poder | `/projects/:projectId/stakeholders/matrix` | F2 | E2 |
| S20 | Nueva evaluación | drawer en la ficha | F2 | E2 |
| S21 | Calendario de relacionamiento | `/engagements/calendar` | F2 | E2 |
| S22 | Lista de interacciones | `/engagements` | F2 | E2 |
| S23 | Ficha de interacción | `/projects/:projectId/engagements/:engagementId` | F2 | E2 |
| S24 | Registrar interacción | `/projects/:projectId/engagements/new` | F2 | E2 |
| S25 | Editor de acta | `/projects/:projectId/engagements/:engagementId/minutes` | F2, F7 | E2 |
| S26 | Visita de campo | `/projects/:projectId/field-visits/:visitId` | F2 | E2 |
| S27 | Tablero de compromisos | `/commitments/dashboard` | F3, F8 | E3 |
| S28 | Lista de compromisos | `/commitments` | F3 | E2 |
| S29 | Ficha de compromiso | `/projects/:projectId/commitments/:commitmentId` | F3 | E2 |
| S30 | Crear compromiso | `/projects/:projectId/commitments/new` | F3 | E2 |
| S31 | Aprobación de compromiso | `/tasks/approvals/:taskId` | F3 | E2 |
| S32 | Entrega y verificación | drawer en la ficha | F3 | E2 |
| S33 | Acuerdo o convenio | `/projects/:projectId/agreements/:agreementId` | F3 | E2 |
| S34 | Tablero de QCS | `/attention/dashboard` | F4, F8 | E3 |
| S35 | Lista unificada de QCS | `/attention` | F4 | E3 |
| S36 | Ficha de queja | `/projects/:projectId/grievances/:id` | F4 | E3 |
| S37 | Registrar QCS | `/projects/:projectId/attention/new` | F4 | E3 |
| S38 | Matriz de riesgos (con el registro) | `/risks/register` | F4 | E3 |
| S39 | Registro de riesgos | `/risks/register` | F4 | E3 |
| S40 | Ficha de riesgo | `/projects/:projectId/risks/:riskId` | F4 | E3 |
| S41 | Centro de alerta temprana | `/risks/warnings` | F4 | E3 |
| S42 | Programas sociales | `/programs` | F5 | E3 |
| S43 | Ficha de programa | `/projects/:projectId/programs/:programId` | F5 | E3 |
| S44 | Aporte social | `/projects/:projectId/contributions/:contributionId` | F5 | E3 |
| S45 | Biblioteca de documentos | `/documents` | F3 | E3 |
| S46 | Ficha de documento o evidencia | `/projects/:projectId/documents/:documentId` | F3 | E3 |
| S47 | Mis tareas (lista, tablero, calendario) | `/tasks` | F3 | E3 |
| S48 | Aprobaciones y escalamientos | `/tasks/approvals` | F3 | E3 |
| S49 | Reportes y exportaciones | `/reports` | F2, F8 | E3 |
| S50 | Entregable por secciones | `/reports/deliverables/:deliverableId` | F8 | E3 |
| S51 | Revisión ejecutiva de 5 minutos | `/dashboards/executive` | F8 | E3 |
| S52 | Bandeja de revisión de IA | `/projects/:projectId/ai-review` | F7 | E3 |
| S53 | Administración | `/administration` (abre la consola `/admin`) | F1 | E3 |
| S54 | Campo: mis tareas y visita | `/tasks`, `/field-visits` (móvil) | F2, F3 | E3 |
| S55 | Campo: interacción, observación y foto | `/projects/:projectId/engagements/new` (móvil) | F2 | E3 |
| S56 | Gestionar vistas (ASM-083) | `/views` y diálogo en cada lista | F2 | E3 |
| S57 | Ficha de consulta y ficha de solicitud | `/projects/:projectId/inquiries/:id`, `/projects/:projectId/requests/:id` | F4 | E3 |
| S58 | Menciones en medios | `/projects/:projectId/risks/mentions` | F4 | Plan 4.0, sin hoja |
| S59 | Señales de alerta | `/projects/:projectId/risks/signals` | F4 | Plan 4.0, sin hoja |
| S60 | Reglas de alerta | `/risks/rules`, `/projects/:projectId/risks/rules` | F4 | Plan 4.0, sin hoja |
| S61 | Impactos potenciales | `/projects/:projectId/risks/impacts` | F4 | Plan 4.0, sin hoja |
| S62 | Tablero de riesgos | `/risks` | F4, F8 | E3 |
| S63 | Calidad de datos | `/territory/quality` | F6 | Plan 6.0, sin hoja |
| S64 | Plantillas de entregables | `/reports/templates` | F8 | Plan 8.0, sin hoja |

Absorbidas o movidas respecto de PRM-02: "Project Detail" y "Project Dashboard" son el Resumen de Project 360; "Early Warning
Center" vive en Riesgos; "Grievance Dashboard" es el tablero de QCS; "Approval Center" es Tareas › Aprobaciones. Agregadas por
la Fase 0: área de influencia, ficha de actor, visita de campo, editor de acta, entrega y verificación, acuerdos, aportes,
importación geoespacial, bandeja de revisión de IA y entregables.

## 10. Supuestos y preguntas abiertas

Supuestos de esta etapa (definidos en el análisis §19): ASM-073 (asistente como panel), ASM-074 (ejecutivo sin rol propio),
ASM-075 (rutas y contexto en la URL), ASM-076 (misma página con otro alcance), ASM-077 (dirección visual y tokens), ASM-078
(inicio «Mi trabajo» con bloques por permisos) y ASM-079 (vistas guardadas, reemplazado por ASM-083). La etapa 2 agrega ASM-080 (la jefatura del proyecto es la asignación activa
con el rol `PROJECT_MANAGER`). Los tableros de módulo, como S27, pasan a E3 junto con los demás tableros [USR-10]. La etapa 3
agrega ASM-081 (la captura de campo sin conexión se diseña; se implementa después del MVP). Las respuestas del usuario a Q1,
Q3, Q6 y Q7 agregan ASM-082 (marca propia), ASM-083 (vistas compartidas), ASM-084 (español, inglés y quechua) y ASM-085
(mapa base por entorno), con sus hojas en las páginas 22 a 24 del lienzo de la etapa 3. ASM-086 suma tres temas propios
(página 25).

| # | Pregunta | Valor con el que se diseña | Relacionado |
|---|---|---|---|
| Q1 | ¿El cliente tiene marca o guía visual propia para el portal? | Respondida por el usuario: marca propia de SGSPE, dirección «Sendero» sobre la paleta «Pino y piedra» | ASM-077, ASM-082 |
| Q2 | ¿Qué nombres de tokens y qué primitivas (tabla, drawer, combobox, calendario) trae `@wasichai/ui` 0.5.0? | Tokens respondidos: los 28 de WAS-ADR-035, que fijan los tres temas de `themes.css` (ASM-086); las primitivas se confirman en F1 al copiar caja-ui | ASM-077, ASM-086, RNF-MNT-001 |
| Q3 | ¿Las vistas guardadas deben compartirse entre personas o dispositivos? | Respondida por el usuario: sí; visibilidad solo yo, equipo del proyecto u organización; requiere `saved_view` | ASM-079, ASM-083 |
| Q4 | ¿El ejecutivo necesita un rol propio? | Lectura con `READ_ONLY` o `ANALYST` | ASM-074 |
| Q5 | ¿El trabajo de campo debe operar sin conexión? | Respondida por el usuario: sí, se diseña; se implementa después del MVP, con un ADR de sincronización | GAP-008, ASM-021, ASM-081 |
| Q6 | ¿Se requieren contenidos en quechua en la interfaz de campo? | Respondida por el usuario: toda la interfaz del portal en español, inglés y quechua; la variante se confirma con el traductor | GAP-013, ASM-084 |
| Q7 | ¿Qué mapa base usa el portal? | Respondida por el usuario: las librerías de srtm-ui; OpenStreetMap ráster solo en desarrollo, estilo configurable en producción | UPS-13, ASM-048, ASM-085, `gis.md` §6.4 |

## 11. Trazabilidad

- **Requisitos:** RNF-USA-001, RF-IAM-001, RF-IAM-002, RNF-I18N-001, RNF-I18N-002, RQ-GIS-014, RQ-GIS-015, RQ-DSH-032,
  RQ-DSH-035, RQ-AUD-002, RQ-AI-004, RF-AIK-005, RF-COM-003, RN-COM-001, RN-WFL-001, RF-NTF-001.
- **Documentos:** [architecture.md §9](../architecture.md), [workflows.md](../workflows.md), [security.md §6](../security.md),
  [gis.md §2 y §6.5](../gis.md), [dashboards.md §3 y §5](../dashboards.md), [ai-architecture.md §8 y §9.3](../ai-architecture.md),
  [ADR-0009](../adr/0009-nombres-tecnicos-en-ingles.md), [ADR-0024](../adr/0024-interfaz-portal-y-admin.md).
- **Rediseño de UX:** [decisiones DD-01 a DD-29](redesign/07-decisiones-y-compatibilidad.md) y
  [arquitectura de información del rediseño](redesign/02-arquitectura-de-informacion.md).
- **Fuentes:** [PRM-02 §1–§49], [USR-07], [USR-09], [USR-10].
