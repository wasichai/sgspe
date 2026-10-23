# Pantallas de SGSPE · fichas de handoff

Fichas de implementación de las pantallas del portal (`sgspe-ui`). Cubren el inventario completo de
[`ux-architecture.md`](ux-architecture.md) §9: la **etapa 2** (proyectos y Project 360, territorio, grupos de interés,
relacionamiento, compromisos y acuerdos: S06 a S26 y S28 a S33), la **parte 3a** (quejas, consultas y solicitudes;
riesgos y alerta temprana; programas e inversión; documentos, tareas y aprobaciones: S35 a S48, S57 a S61 y la ficha de
incidente) y la **parte 3b** (tableros, capa de IA, inicio y transversales, campo sin conexión: S01, S02, S04, S05, S27,
S34, S49 a S55 y S62, el tablero de riesgos), y S56 (gestionar vistas, de las respuestas del usuario a Q1, Q3, Q6 y Q7).
S63 (calidad de datos, F6) se suma en el plan 6.0, y S64 (plantillas de entregables, F8), en el plan 8.0.
La búsqueda global (S03) está en la etapa 1 (`design-system.md` y `ux-architecture.md` §7.3). El rediseño de UX
([`redesign/`](redesign/README.md)), implementado en `sgspe-ui`, se llevó a estas fichas el 2026-10-23: §2 dice lo común de
listas y fichas, y cada ficha que cambió cita su decisión (`DD-NN`).

- **Lienzo de la etapa 2:** <https://claude.ai/artifact/7RUzqeAhtWx75vCNPCBRWe>; **lienzo de la etapa 3:**
  <https://claude.ai/artifact/LTNxbVoceQL56sJ1F8BERr>. Los dos son privados y se comparten desde su menú. Cada ficha nombra
  su hoja.
- Se lee con [`ux-architecture.md`](ux-architecture.md) (rutas, navegación, hilo de contexto) y
  [`design-system.md`](design-system.md) (componentes, patrones y estados comunes). Los recorridos completos están en
  [`flows.md`](flows.md).
- Las reglas de negocio no se repiten aquí: se citan por su ID y viven en el [análisis](../requirements-analysis.md), los
  [workflows](../workflows.md), la [API](../api.md) y la [seguridad](../security.md).

## 1. Cómo leer una ficha

| Campo | Qué dice |
|---|---|
| Ruta y lienzo | Ruta del portal (ASM-075), hoja del lienzo que la dibuja y fase en que llega cada parte |
| Propósito | La pregunta que la pantalla responde y el trabajo que permite hacer |
| Roles | Quién la lee y quién actúa, según la matriz de `security.md` §7 (abreviaturas de §7.1: PAD, PMG, SMG, CRO, SHM, CMM, GVM, SRM, SIM, GAN, FAG, APR, ANL, RDO). "Todos" son los roles operativos con READ sobre el objeto; `AUDITOR` lee todo y no escribe |
| Acciones | Primaria (la más probable para el rol) y secundarias; cada transición abre el diálogo de `design-system.md` §6.2 |
| Disposición y componentes | Zonas de la pantalla y piezas del catálogo de `design-system.md` §5 |
| Datos y API | Objetos, campos y llamadas. "Genérica" es la API de wasichai (`api.md` §2); los comandos propios van por `/api/sgspe/**` |
| Estados | Lo propio de la pantalla; los estados comunes (vacío, carga, error, sin permiso, solo lectura, campo restringido, módulo no instalado) siguen `design-system.md` §6.5 y no se repiten |
| Permisos | Campos restringidos (`security.md` §7.9), `EDIT_ANY` y reglas de quién puede qué |
| Responsive | Lo que cambia por debajo de `lg` (1024 px) y en teléfono |
| Relacionadas y navegación | De dónde se llega, a dónde se sigue y cómo viaja el contexto |
| Árbol | Componentes React de la ruta, de afuera hacia adentro |

## 2. Convenciones comunes de implementación

- **Pila:** React 19 + TypeScript + Vite + Tailwind 4 (tokens de [`tokens.css`](tokens.css)) + React Router (rutas con
  `loader` para precargar) + TanStack Query + React Hook Form + Zod + i18next. Las etiquetas visibles salen de i18next con
  claves por pantalla (`screens.s08.title`); los valores de ENUM se muestran tal como se guardan (ADR-0009).
- **Shell:** toda ruta vive dentro de `AppShell` (barra superior de seis controles, navegación de 13 secciones en bloques,
  barra inferior en el teléfono; `ux-architecture.md` §7.1) y, bajo `/projects/:projectId`, de `ProjectLayout` (barra de
  contexto y una sola fila de pestañas del proyecto con conteos, DD-03). El contexto de territorio se lee de
  `?territory=<objeto>:<id>` con `useContextFilters()` y se agrega a toda consulta de lista e indicador del proyecto.
- **Consultas:** claves `['records', object, params]`, `['record', object, id]`, `['transitions', object, id]`,
  `['indicator', code, filters]` y `['dashboard', id, filters]`. Los indicadores se guardan en caché un minuto (`api.md`
  §4.11); las fichas se invalidan después de toda mutación sobre el registro o sus hijos.
- **Formularios:** el esquema Zod se arma desde la metadata del objeto (`/api/metadata/objects/**`) más las reglas de la
  pantalla; los errores `400` del servidor se asignan a su campo con `errors[].field`.
- **Escrituras:** toda mutación manda `If-Match` con el `updatedAt` leído (IM), `Idempotency-Key` en los comandos que lo piden
  (IK) y `X-Change-Reason` cuando el objeto es `requiresReason` o la transición exige motivo (CR) (`api.md` §3.5–§3.7).
  Respuestas: `400` al campo o al diálogo; `403` como sin permiso; `409` con su `detail` en lenguaje del dominio; `412` con
  "Otra persona cambió este registro" y "Recargar", sin perder lo escrito; `428` no debería ocurrir (el cliente siempre manda
  `If-Match`).
- **Transiciones:** la lista sale de `GET …/transitions` (las que el rol no tiene no se muestran); antes de confirmar, el
  diálogo llama a `POST /api/sgspe/transition-checks` para mostrar bloqueos y advertencias de las guardas.
- **Listas (P-02):** las listas de trabajo (S04, S16, S22, S28, S35, S38 y S39, S41 y S42) llevan una `BarraDeFiltros`
  (`design-system.md` §5.1, DD-04): primero el `SelectorDeVista` con las vistas y su conteo (`?view=`, DD-03), luego la
  búsqueda (`q`, 300 ms después de la última tecla), hasta tres filtros frecuentes, «Más filtros» en un drawer, un chip por
  filtro aplicado y «Limpiar filtros». Las anchas usan `TablaPriorizada` (DD-05): esenciales siempre, secundarias desde `xl`
  y opcionales en «Columnas»; Enter en una fila abre la ficha y Espacio su vista previa (`?peek=`). En el teléfono, la lista
  pasa a tarjetas (`ListaEnTarjetas`) y los filtros frecuentes, a «Filtros (N)». Las páginas de un módulo con ruta propia
  (Compromisos y Acuerdos; las de Riesgos) van en un `SelectorDePagina` en la cabecera (DD-23).
- **Fichas (P-09):** las de grupo de interés (S17), interacción (S23), compromiso (S29), acuerdo (S33), queja (S36),
  consulta y solicitud (S57), riesgo (S40) y aporte (S44) siguen la anatomía de `design-system.md` §6.1 (DD-02, DD-06):
  `CabeceraDeFicha` con un antetítulo «Tipo · código · categoría», un título que dice qué es y chips de situación (nunca el
  estado); una sola primaria, la siguiente acción del flujo, con lo que la impide debajo; hasta dos secundarias; y «Más
  acciones» con las demás transiciones y, al final, las destructivas (cancelar, anular, retirar, terminar, rechazar y
  desistir, DD-28). El `PanelDeDecision` es el único lugar del estado, con el camino del flujo plegado dentro de «Estado
  actual» («Ver el camino»). El cuerpo va en `Seccion`, con los campos vacíos plegados por `CamposPlegables` («Mostrar los
  N campos sin dato»). Donde una ficha de este documento nombre `EntityHeader`, «camino de estados» o un encabezado «con
  estado», vale esta regla.
- **Responsive:** los puntos de quiebre son los de `tokens.css` (sm 640, md 768, lg 1024, xl 1280, 2xl 1440); lo que va desde
  1536 px usa `min-[96rem]` (DD-24). La fila de pestañas del proyecto se desplaza con la activa siempre a la vista, y los
  paneles laterales de las fichas bajan debajo del contenido por debajo de `lg`.

## 3. Proyectos y Project 360

### S06 · Lista de proyectos

- **Ruta y lienzo:** `/projects` · patrón de lista de la etapa 1 (hoja `Pat-Lista`) · F2.
- **Propósito:** encontrar un proyecto y entrar a su Project 360; ver de un vistazo cuáles piden atención.
- **Roles:** lectura de todos (`project` R). Crear: PAD.
- **Acciones:** primaria "Nuevo proyecto" (PAD). Secundarias: vistas guardadas, filtros.
- **Disposición y componentes:** `SavedViews` ("Mis proyectos", "Activos", "En revisión", "Todos"), `FilterBar` (tipo, etapa,
  fase de exploración, estado, unidad administrativa), `DataTable` con código, nombre, tipo, etapa, estado (`StatusBadge`),
  jefatura (ASM-080), campaña en curso y conteos de atención.
- **Datos y API:** genérica `GET /api/objects/project/records`; jefatura con `project_assignment` activas de rol
  `PROJECT_MANAGER` (ASM-080); conteos de atención con `GET /api/sgspe/dashboards/indicators/KPI-COM-07` (desglose por
  proyecto; trae los 10 primeros, así que la columna solo marca a los que tienen vencidos, F3) y los de QCS desde F4.
- **Estados:** por defecto muestra los proyectos con asignación activa de la persona (ASM-076, RF-PRJ-005); un proyecto sin
  asignación se abre en solo lectura.
- **Permisos:** sin restricciones de campo.
- **Responsive:** en teléfono, tarjetas con nombre, estado y conteos.
- **Relacionadas y navegación:** abre S08; "Nuevo proyecto" abre S07.

```text
ProjectsListPage
├── PageHeader (título · conteo · Button "Nuevo proyecto")
├── SavedViews
├── FilterBar
└── DataTable<ProjectRow> · Pagination
```

### S07 · Crear proyecto

- **Ruta y lienzo:** `/projects/new` · hoja `Proyecto-Nuevo` (paso 7, revisión) · F2.
- **Propósito:** dar de alta un proyecto con lo necesario para operarlo y llevarlo a revisión.
- **Roles:** crea PAD; PMG completa (RU). Enviar a revisión: PMG, PAD. Aprobar o devolver (WF-A): PAD, con `APPROVE` para
  aprobar.
- **Acciones:** primaria "Crear y enviar a revisión"; secundarias "Guardar y salir", "Anterior".
- **Disposición y componentes:** `StepIndicator` lateral con 7 pasos (1 Identificación, 2 Ubicación y CRS de trabajo,
  3 Partes, 4 Campaña, 5 Equipo, 6 Parámetros y políticas, 7 Revisar y enviar); el paso 7 lista los datos y las
  precondiciones de activación (CRS de trabajo y una parte CLIENTE u OPERADOR, ASM-062) con `Callout` "Qué pasará".
- **Datos y API:** genérica `POST /api/objects/project/records` (al salir del paso 1, en `draft`) y escrituras de
  `project_party`, `exploration_campaign` y `project_assignment`; `POST …/transitions/submit_for_review`. Los parámetros
  muestran su `defaultValue` (ASM-019).
- **Estados:** borrador guardado automáticamente; un `409` por `code` repetido vuelve al paso 1 con el campo marcado.
- **Permisos:** PMG no ve el botón "Crear" (no tiene CREATE sobre `project`); entra a completar un borrador existente.
- **Responsive:** el indicador de pasos pasa arriba como "Paso 3 de 7".
- **Relacionadas y navegación:** al enviar, vuelve a S08 del proyecto en `in_review`; la aprobación llega como tarea a PAD.

```text
ProjectWizardPage
├── WizardLayout
│   ├── StepIndicator
│   └── <Step>: IdentificationStep · LocationCrsStep · PartiesStep · CampaignStep · TeamStep · PoliciesStep · ReviewStep
│        └── ReviewStep: ReviewSummary · ActivationPreconditions · Callout "Qué pasará"
└── WizardFooter (Guardar y salir · Anterior · Siguiente / Crear y enviar a revisión)
```

### S08 · Project 360 · Resumen

- **Ruta y lienzo:** `/projects/:projectId/overview` · hoja `P360-Resumen` · F2 (compromisos desde F3, QCS y riesgos desde
  F4, programas desde F5, resumen de IA en F7, tablero de proyecto completo en F8).
- **Propósito:** responder en un minuto cómo está el proyecto y qué pide atención hoy, sin salir del proyecto.
- **Roles:** lectura de todos. Las acciones rápidas aparecen según el rol (registrar interacción: CRO, SHM, SMG, FAG).
- **Acciones:** primaria según el rol ("Registrar interacción" para el equipo de relacionamiento); secundarias "Resumen con IA"
  (F7), "Ver en el mapa".
- **Disposición y componentes:** encabezado con estado del proyecto, jefatura (ASM-080), etapa y fase; una **franja de
  cifras** de salud (P-03): las cinco en una sola superficie, sin tarjetas dentro de tarjetas, y teñidas solo las que piden
  acción (compromisos vencidos y riesgos altos mayores que cero), con un ícono y un texto además del color; la cifra de QCS
  lleva `NotaDeConfidenciales` si no cuenta las confidenciales, sin cuántas. Luego **«Qué requiere atención»**, con una
  `ListaPriorizada` por tipo (actas por redactar, grupos sin contacto, evaluaciones viejas; vencidos y bloqueados desde
  F3): las tres más urgentes con su motivo y «Ver los N» hacia la lista filtrada si existe, o «Mostrar N más» en el lugar
  (DD-13). Después, el paisaje de grupos de interés (mini matriz KPI-STK-03 y barra de posturas KPI-STK-02), reuniones al
  mes frente a la meta (KPI-ENG-02), áreas de influencia vigentes, mini mapa, actividad reciente y `AiSummary` (F7). Las
  listas y los accesos sueltos van sin subrayado.
- **Datos y API:** de F2 a F7, una llamada por indicador con `GET /api/sgspe/dashboards/indicators/{code}?project=` (KPI-STK-01
  a 03, KPI-ENG-01 y 02, KPI-COM-02 a 04 y 06 desde F3); desde F8, `GET /api/sgspe/dashboards/project?project=` en una sola
  llamada. Áreas: genérica `area_of_influence` en `approved`. Actividad: `GET /api/objects/project/records/{id}/history` y los
  registros recientes del proyecto.
- **Estados:** cada `KpiCard` de un módulo no instalado dice "llega en F3/F4"; un indicador sin permiso dice "no disponible"
  (RQ-DSH-032); sin jefatura asignada, el encabezado lo marca.
- **Permisos:** la barra de posturas solo se dibuja para SMG y SHM (`stance`, `security.md` §7.9).
- **Responsive:** una columna; la franja de cifras y «Qué requiere atención» primero.
- **Relacionadas y navegación:** cada fila de atención abre su ficha con el contexto del proyecto; el mini mapa abre S09.

```text
ProjectOverviewPage
├── ProjectHeader (StatusBadge · jefatura · etapa · Button "Resumen con IA")
├── FranjaDeCifras (KpiCard anidada × 5 · NotaDeConfidenciales)
├── QueRequiereAtencion (ListaPriorizada × n)
├── StakeholderLandscape (MiniMatrix · StanceBar)
├── EngagementVsTarget (Chart)
├── ActiveAreasOfInfluence
├── MiniMap
├── RecentActivity
└── AiSummary (F7)
```

### S09 · Project 360 · Mapa

- **Ruta y lienzo:** `/projects/:projectId/map` · hoja `P360-Mapa` · F2 (capas básicas); F6 (análisis espaciales, WMS).
- **Propósito:** ver el proyecto en su territorio: componentes, áreas de influencia, predios, comunidades y gestión.
- **Roles:** lectura de todos; las capas de tenencia con titulares solo para quien lee esos campos. Importar: GAN.
- **Acciones:** primaria "Capturar PNG"; secundarias filtros, "Importar" (GAN, abre S15), acercar a la selección.
- **Disposición y componentes:** `FilterBar` de capas activas; `Map` con controles de zoom, encuadre y captura; barra de
  coordenadas en el CRS de trabajo; `MapLayerControl` en cuatro grupos (Referencia, Proyecto, Tenencia, Gestión) con candado
  en capas sensibles; `DataTable` sincronizada por pestañas (componentes, miembros del área vigente, predios).
- **Datos y API:** `GET /api/sgspe/territory/layers/{layer}?project=&bbox=` (capas `LYR-NN` de `gis.md`); genérica
  `GET /api/gis/objects/{object}/features` para capas temáticas básicas (F2); tablas por la API genérica con `?project=`.
  F6: las capas públicas por WMS a través del proxy (como en S12) y el panel «Análisis» con `component-parcels`,
  `component-communal-land`, `sensitive-distances`, `buffer-localities`, `concession-territories` y `nearby-conflicts`
  de `/api/sgspe/territory/projects/{id}/` (`api.md` §4).
- **Análisis (F6):** panel «Análisis» con seis tablas: predios afectados (con los titulares solo si la persona los lee;
  si no, «no disponible» con el campo que falta), territorio comunal, elementos sensibles cercanos, localidades en un
  buffer (con un control de metros), territorios de las concesiones y conflictos cercanos. Cada fila selecciona su
  registro en el mapa y resalta su geometría; una tabla incompleta dice «no disponible» y no muestra ceros.
- **Exportar (F6):** el diálogo de exportación de S12 también sale aquí (objeto, formato, coordenadas y nivel; ver S12).
- **Estados:** sin geometrías, el mapa muestra el área de interés del proyecto y un `EmptyState` sobre la tabla; capa sin
  permiso con candado y su razón.
- **Permisos:** los grupos de interés solo salen agregados por localidad, nunca como puntos de personas (LYR-17); titulares de
  predios según `security.md` §7.9.
- **Responsive:** en teléfono el panel de capas es un `Drawer` y la tabla pasa debajo del mapa.
- **Relacionadas y navegación:** seleccionar una fila centra el mapa (`?selected=`); un área abre S10; una comunidad abre S13
  con el proyecto en contexto.

```text
ProjectMapPage
├── MapToolbar (FilterBar · Capturar PNG · Importar)
├── MapView (Map · CoordinateBar · Legend)
├── MapLayerControl
├── SyncedTable (Tabs: Componentes · Miembros del AIDS · Predios)
├── AnalysisPanel (seis tablas · BufferMetersControl)
└── ExportDialog
```

### S10 · Área de influencia

- **Ruta y lienzo:** `/projects/:projectId/areas/:areaId` · hoja `Ter-Area` · F2 (sugerencias del cálculo y resumen en F6).
- **Propósito:** definir qué lugares forman cada área de influencia, versionar los cambios y aprobar la vigente.
- **Roles:** lectura de todos. Crear y editar versiones y miembros: SMG, GAN. Aprobar: SMG, PMG (`APPROVE`).
- **Acciones:** primaria "Aprobar versión N" (solo en `draft`); secundarias "Comparar con la vigente", "Agregar miembro",
  "Sugerencias del cálculo" (F6), "Nueva versión" (desde una aprobada).
- **Disposición y componentes:** encabezado con tipo, versión y estado (WF-O); línea de versiones (`Reemplazada`, `Aprobada`,
  `Borrador`); criterios, estudio de origen y área calculada; `DataTable` de miembros con condición (INCLUIDO, PROVISIONAL),
  criterio, justificación, "estudiado" y columna de comparación (igual, nuevo, sale); mapa de las dos versiones; diálogo de
  aprobación con reglas y "Qué pasará".
- **Datos y API:** genérica `area_of_influence` y `area_of_influence_member`; aprobar con
  `POST /api/sgspe/areas-of-influence/{id}/approve` (IM, CR opcional); F6: `GET …/summary` y `GET …/member-suggestions`.
- **Sugerencias del cálculo (F6):** el panel «Sugerencias del cálculo» llama a `member-suggestions` (con `bufferM` si la
  persona fija una distancia; sin ella, solo las que intersecan) y las muestra una a la vez, con la relación (interseca
  o dentro del buffer) y «Ya es miembro» cuando corresponde. «Aceptar» crea el miembro con `spatially_suggested: true` y
  la condición PROVISIONAL por defecto; «Descartar» no escribe nada. Nada se escribe sin «Aceptar». El resumen
  (superficie, localidades por tipo y componentes sobre tierras comunales o privadas) sale de `summary`; una cifra que
  falta dice «no disponible».
- **Estados:** una versión `approved` o `superseded` es de solo lectura en sus miembros (`TerritoryWriteGuard`); sin miembros,
  "Aprobar" sale deshabilitado con la razón; `409` si otra versión se aprobó mientras tanto.
- **Permisos:** GAN prepara pero no aprueba.
- **Responsive:** la tabla de comparación pasa a tarjetas por miembro.
- **Relacionadas y navegación:** cada miembro abre su ficha territorial (S13, S14); el área vigente alimenta el filtro de
  territorio y los tableros.

```text
AreaOfInfluencePage
├── EntityHeader (tipo · versión · StatusBadge · acciones)
├── VersionTimeline
├── CriteriaCard
├── MembersTable (DataTable con columna de comparación)
├── VersionsMap
└── ApproveAreaDialog (reglas · Qué pasará · motivo opcional)
```

### S11 · Equipo y parámetros del proyecto

- **Ruta y lienzo:** `/projects/:projectId/settings` · hoja `P360-Equipo` · F2 (tope de campaña en uso desde F3).
- **Propósito:** quién trabaja en el proyecto y con qué rol, las campañas con su tope, las partes y concesiones, y los
  parámetros y políticas que gobiernan las reglas.
- **Roles:** lectura de todos. Asignaciones: PAD. Partes y campañas: PAD, PMG. Concesiones: PAD, GAN. Parámetros y,
  desde F5, temas priorizados para aportes: PAD, PMG.
- **Acciones:** primaria según la pestaña ("Asignar persona", "Nueva campaña"); editar una asignación y, en el menú de su
  fila («Acciones de <persona>»), «Terminar la asignación…», con un `ConfirmDialog` que nombra a la persona y el efecto
  (U-16, P-08). Sin `UPDATE` sobre la asignación no hay menú. La escritura no cambia.
- **Disposición y componentes:** `Tabs` (Equipo, Campañas, Partes y concesiones, Parámetros y políticas); `DataTable` de
  asignaciones con rol, periodo y estado; campañas con `ProgressBar` del tope y aviso de montos en otra moneda (ASM-063);
  parámetros agrupados por módulo con la marca "por defecto" o "cambiado"; desde F5, en "Parámetros y políticas", la
  lista "Temas priorizados para aportes" (`project_priority_topic`, ASM-112), que vincula y desvincula temas del
  catálogo y avisa que, sin temas, la prioridad de los aportes no se evalúa.
- **Datos y API:** genérica `project_assignment`, `exploration_campaign`, `project_party`, `project_concession` y `project`
  (campos de parámetros); desde F5, el vínculo `project_priority_topic`
  (`/api/objects/project/records/{id}/related/project_priority_topic`).
- **Estados:** una asignación vencida se marca y no da acceso de escritura (RQ-SEC-006).
- **Permisos:** quien no puede escribir ve todo en solo lectura.
- **Responsive:** cada pestaña en una columna.
- **Relacionadas y navegación:** la jefatura que muestra S08 sale de esta pestaña (ASM-080).

```text
ProjectSettingsPage
├── Tabs
│   ├── TeamTab (DataTable<Assignment> · MasAcciones por fila · ConfirmDialog «Terminar la asignación»)
│   ├── CampaignsTab (CampaignCapCard × n)
│   ├── PartiesTab (DataTable<Party> · DataTable<Concession>)
│   └── PoliciesTab (PolicyGroup × n · PriorityTopicsList)
└── AssignmentDrawer
```

## 4. Territorio

### S12 · Mapa territorial

- **Ruta y lienzo:** `/territory` (con `?project=` y `?selected=locality:<id>`) · hoja `Ter-Mapa` (interactiva) · F2
  (capas básicas y selección); F6 (ficha contextual con KPI-TER-01, mapa de calor, WMS, exportación). El modo tablero
  está en `/territory/dashboard` (Tablero territorial) y la calidad de datos en `/territory/quality` (S63).
- **Propósito:** entender qué pasa en cada lugar y pasar del lugar a sus registros sin perder el contexto.
- **Roles:** lectura de todos; capas y cifras según permisos.
- **Acciones:** en la cabecera, una sola primaria, «Nueva comunidad» (flujo 2), el paso entre el mapa y el tablero a la
  vista, y en «Más acciones» la calidad de datos (S63), «Capturar PNG», «Exportar» (F6) e «Importar» (GAN). Sobre el lugar
  elegido: "Abrir la ficha", "Filtrar el proyecto por este lugar" (fija el chip de territorio) y "Mapa de calor" (F6, con
  la concentración de interacciones, QCS, solicitudes, incidentes y señales).
- **Disposición y componentes:** el mapa primero (DD-11, U-10): el lienzo ocupa todo el ancho útil. Las capas y su leyenda
  van juntas en un panel plegable superpuesto a la izquierda (arriba, las capas en el mapa con la muestra de la paleta; el
  resto, tras «Todas las capas»); los lugares visibles (alternativa de teclado) con la ficha contextual (seis cifras, barra
  de posturas y última actividad) van en otro panel plegable a la derecha. Cada panel se abre y se cierra con un botón con
  nombre, y lo plegado viaja en `?hide=layers,places`; elegir un lugar vuelve a abrir el de lugares. Filtros de proyecto
  y periodo; `Map` con lugares seleccionables (conteo de grupos de interés por localidad); tabla de registros del lugar
  debajo del mapa.
- **Capas (F6):** el control las agrupa en Referencia, Proyecto, Tenencia y Gestión. Las públicas (las que GeoServer
  publica) salen por WMS a través del proxy de SGSPE y el resto por GeoJSON (`layers/{layer}`); si el proxy responde
  `503`, las capas WMS vuelven al GeoJSON con un aviso y el botón «Reintentar». Gestión lista las capas LYR-17 a LYR-24
  de `gis.md` (una capa que el backend aún no sirve se oculta) y el calor (`concentration`). La leyenda usa la paleta
  compartida (`gis.md` §7.2).
- **Ficha contextual (F6):** al elegir una localidad o una comunidad muestra la parte territorial (`…/card`: unidad
  administrativa, comunidades o sectores, áreas de influencia), las cifras de KPI-TER-01 (cada componente con su «no
  disponible» y la razón si falta) y los registros de la zona (`zones/…/records?of=`, por tipo de registro y periodo),
  con «Ver resumen» hacia S13 o S14. Sin proyecto o sin permiso sobre un componente, solo esa parte
  dice «no disponible».
- **Datos y API:** capas con `GET /api/sgspe/territory/layers/{layer}`; ficha contextual con
  `GET /api/sgspe/territory/localities/{id}/card` o `…/communities/{id}/card` (F6) más
  `GET /api/sgspe/dashboards/indicators/KPI-TER-01?locality=` o `?community=`; registros del lugar con
  `GET /api/sgspe/territory/zones/{zoneObject}/{id}/records?of=` (F6); mapa de calor con
  `GET /api/sgspe/territory/concentration?of=` (F6; `engagement`, `grievance`, `inquiry`, `stakeholder_request`,
  `incident` o `early_warning_signal`). En F2, la selección abre directamente S13 o S14. El mapa base sale de
  `GET /map/settings`; las capas WMS, del proxy `/api/sgspe/territory/wms` (`api.md` §4).
- **Estados:** sin selección, el panel invita a elegir un lugar; una cifra sin permiso dice "no disponible".
- **Permisos:** la barra de posturas solo para SMG y SHM; los grupos de interés siempre agregados (LYR-17).
- **Mapa base (ASM-085):** MapLibre GL y `@wasichai/gis` como srtm-ui; terra-draw para dibujar. En desarrollo, teselas
  ráster de OpenStreetMap con su atribución; en producción, el estilo configurado, y sin él solo las capas del proyecto
  con el aviso «Sin mapa base». A quien administra, el aviso le dice además quién lo configura («Lo configura quien opera
  SGSPE, en los ajustes del mapa base de la organización»), sin enlace: no hay pantalla que lo configure (DD-25). El
  mapa base de F6 viene de `/map/settings`. «Sin fondo» va sobre el propio mapa, junto
  al aviso «Sin mapa base», y no en el panel de capas, porque S10, S24 y el dibujo no tienen panel de capas; la
  atribución (`MapAttribution`) siempre es visible. Hoja `Mapa-Base` del lienzo de la etapa 3, página 24.
- **Exportar (F6):** el diálogo (también en S09) pide objeto, formato, sistema de coordenadas y nivel de divulgación, y
  ofrece solo los niveles que permite la capa (`gis.md` §6); EXTERNO exige marcar su casilla de confirmación. Sin la
  acción `EXPORT` sobre el objeto, el botón no sale. Las capas de gestión (LYR-17 a LYR-24) no se exportan como archivo.
- **Responsive:** en teléfono la ficha contextual es una hoja inferior que se expande.
- **Relacionadas y navegación:** "Filtrar el proyecto por este lugar" lleva a S16 o S08 con `?territory=`; el rastro anota
  "Mapa territorial".

```text
TerritoryMapPage
├── PageHeader (Nueva comunidad · Mapa | Tablero · MasAcciones)
├── MapFilters (proyecto · periodo · HeatmapToggle)
├── TerritoryMap (Map · PlaceMarkers)
│   ├── PanelDeCapas (capas · Legend; plegable a la izquierda)
│   └── PanelDeLugares (VisiblePlacesNav · ContextCard: KpiCard × 6 · StanceBar · "Abrir la ficha"; plegable a la derecha)
├── ZoneRecordsTable
└── ExportDialog
```

### S13 · Ficha de comunidad

- **Ruta y lienzo:** `/territory/communities/:id` (con `?project=`) · hoja `Ter-Comunidad` · F2 (cifras de la zona en F6).
- **Propósito:** saber quién es la comunidad (registros, vida orgánica, sectores, organización que la representa) y qué tiene
  abierto el proyecto en ella.
- **Roles:** lectura de todos. Editar comunidad y sectores: CRO, GAN (SMG edita la comunidad).
- **Acciones:** primaria "Trabajar en el proyecto con este lugar" (fija el chip y abre S16); secundarias "Ver en el mapa",
  "Editar".
- **Disposición y componentes:** `EntityHeader` (tipo, estado orgánico, marca de partidas por verificar); `Tabs` (Resumen,
  Registros del lugar, Línea base, Documentos, Actividad); en Resumen: cifras del lugar para el proyecto en contexto, tabla de
  grupos de interés del lugar, datos registrales, vida orgánica con los cargos de la organización representante, sectores; a
  la derecha: mapa, áreas de influencia que la incluyen, línea base y "Seguir el hilo".
- **Datos y API:** genérica `community`, `community_sector` (con `community_sector_locality`), `organization` con
  `community=`, `position`; áreas con `area_of_influence_member?community=`; cifras con KPI-TER-01 (`?community=`, F6); grupos
  de interés del lugar con la regla de inclusión de `dashboards.md` §3.1.
- **Estados:** `registry_records_unverified` muestra la marca "PARTIDAS POR VERIFICAR" con su explicación; área calculada
  junto a la inscrita (RQ-GIS-009).
- **Edición de la geometría (F6):** el territorio (`community.territory`) se edita con `GeometryField` y, antes de
  guardar, `geometry-checks` muestra el motivo de cada defecto y la reparación propuesta; la persona decide, y se guarda
  por la API genérica.
- **Permisos:** postura de los grupos de interés solo para SMG y SHM; las áreas de proyectos sin acceso no se listan.
- **Responsive:** los bloques laterales bajan debajo del resumen.
- **Relacionadas y navegación:** grupos de interés → S17; localidades → S14; áreas → S10; actor representante → S18.

```text
CommunityDetailPage
├── ContextBar · Trail
├── EntityHeader
├── EntityTabs
│   └── SummaryTab (PlaceKpis · PlaceStakeholdersTable · RegistryCard · OrganicLifeCard · SectorsTable)
└── SidePanel (PlaceMap · AreasIncludingCard · BaselineCard · FollowTheThread)
```

### S14 · Ficha de localidad y línea base social

- **Ruta y lienzo:** `/territory/localities/:id` (con `?project=`) · hoja `Ter-Localidad` (pestaña Línea base) · F2.
- **Propósito:** conocer el lugar con datos fechados y con su confiabilidad: población, economía, servicios, festividades,
  programas del Estado, conflictos y percepciones.
- **Roles:** lectura de todos. Editar localidad: CRO, GAN. Perfiles: SMG, CRO, SHM. Escuelas, salud y vías: CRO, GAN.
  Festividades, programas y conflictos: CRO. Percepciones: CRO, SHM.
- **Acciones:** primaria "Nuevo perfil" (un dato nuevo es otro perfil, no una corrección); secundarias "Comparar con …",
  selector de perfil.
- **Disposición y componentes:** encabezado (tipo, estado de reconocimiento, padre, código de centro poblado, áreas);
  perfil con fecha, fuente y `StatusBadge` de confiabilidad (REFERENCIAL, VERIFICADO); cifras con su variación frente al perfil
  anterior; servicios y accesos; calendario de festividades; programas; conflictos con tipo y estado; percepciones con cita
  atribuida por cargo.
- **Datos y API:** genérica `locality`, `locality_profile?locality=` (ordenado por `data_date`), `school`, `health_facility`,
  `road_access`, `festivity`, `state_program_locality`, `local_conflict_locality`, `perception` (por estudio).
- **Estados:** una cifra ausente en el perfil anterior dice "no disponible en <fecha>", nunca cero.
- **Edición de la geometría (F6):** `locality.location` y `locality.boundary` se editan con `GeometryField` y, antes de
  guardar, `geometry-checks` muestra el motivo de cada defecto y la reparación propuesta; la persona decide.
- **Permisos:** `religion` y `electoral_process` solo para SMG y SHM; los demás ven "Restringido".
- **Responsive:** cifras en dos columnas; bloques apilados.
- **Relacionadas y navegación:** las festividades aparecen en S21; la comunidad padre abre S13.

```text
LocalityDetailPage
├── ContextBar · Trail
├── EntityHeader
├── EntityTabs
│   └── BaselineTab
│        ├── ProfileSelector · ReliabilityBadge
│        ├── ProfileKpis (KpiCard × 4 con delta)
│        ├── ProfileFields · RestrictedFieldsNotice
│        ├── ServicesCard · FestivityCalendar
│        └── StatePrograms · LocalConflicts · Perceptions
└── SidePanel (PlaceMap · ReadingGuide · UsedIn)
```

### S15 · Importación geoespacial

- **Ruta y lienzo:** `/territory/imports/:id` (asistente; `:id` es el `importBatchId` de la vista previa) · hoja
  `Ter-Importar` · F2 (XLSX, CSV, GeoJSON y latitud y longitud); F6 (formatos por GDAL).
- **Propósito:** cargar coordenadas o geometrías sin errores de datum, ver las diferencias con lo registrado y decidir fila
  por fila antes de escribir.
- **Roles:** GAN (componentes, predios y concesiones); PAD para concesiones.
- **Acciones:** primaria "Confirmar importación"; secundarias "Probar con otro CRS" (recalcula la vista previa),
  "Descargar el reporte de diferencias", "Cancelar".
- **Disposición y componentes:** `StepIndicator` (1 Archivo y destino, 2 CRS de origen y columnas, 3 Vista previa y
  decisiones, 4 Confirmación); resumen del archivo; seis `KpiCard` (filas, nuevas, sin cambio, con diferencias, inválidas,
  fuera de zona); mapa de vista previa; tabla de filas que piden decisión con su `Select`; panel "Al confirmar se escribirá".
- **Datos y API:** `POST /api/sgspe/territory/imports` (multipart con `sourceCrs` obligatorio) → `importBatchId`, `sha256`,
  `invalid[]`, `differences[]`, `outOfZone[]`; `POST /api/sgspe/territory/imports/{importBatchId}/confirm` con el mismo
  archivo, `sha256` y `decisions[]`. Con `Accept` de XLSX, el reporte de diferencias.
- **Estados:** sin CRS de origen no se avanza (RQ-GIS-007, CON-024); "Nada se ha escrito todavía" hasta confirmar; si el
  archivo cambió, la confirmación se rechaza y se vuelve al paso 3.
- **Formatos y destinos (F6):** los formatos salen de `GET /api/sgspe/territory/imports/formats` y el portal elige el
  lector por la extensión del archivo; si el archivo trae varias capas, el paso 1 pide cuál (`LAYER_REQUIRED` trae la
  lista). «Usar el CRS del archivo» solo propone el CRS que declara el archivo: la persona lo confirma o lo cambia. Los
  destinos suman comunidad (`community.territory`) y elemento ambiental (`environmental_feature`) a los de F2.
- **Permisos:** sin CREATE y UPDATE sobre el destino, el destino no se ofrece.
- **Responsive:** asistente de escritorio; en tableta, el mapa pasa debajo de la tabla.
- **Relacionadas y navegación:** al confirmar, vuelve a S09 con las capas recién importadas resaltadas.

```text
GeoImportWizard
├── StepIndicator
├── ImportSummaryBar
├── ImportKpis (KpiCard × 6)
├── PreviewMap
├── DecisionsTable (DataTable con Select por fila)
├── CrsCheckCallout
└── ConfirmPanel (conteos · reglas · Confirmar)
```

### S63 · Calidad de datos

- **Ruta y lienzo:** `/territory/quality` · sin hoja (plan 6.0) · F6.
- **Propósito:** encontrar los registros con datos geográficos dudosos o faltantes y llegar a corregirlos.
- **Roles:** quien lee el objeto listado y los campos de la revisión (`SpatialGate`, ASM-122); en la práctica GAN y SMG.
  La sección Territorio guarda la ruta de esta pantalla.
- **Acciones:** primaria abrir el registro de una fila; secundarias cambiar de revisión, filtrar por objeto y
  por proyecto.
- **Disposición y componentes:** una pestaña por revisión: `INVALID` (geometría inválida), `OUT_OF_ZONE` (fuera de la
  zona UTM esperada), `STALE_AREA` (área guardada distinta de la calculada), `NO_TERRITORY` (QCS, compromisos, riesgos e
  incidentes sin localidad, comunidad ni ubicación) y `NO_GEOMETRY` (localidades y comunidades sin geometría); filtros
  de objeto y de proyecto; lista paginada con objeto, nombre, campo y detalle, y un enlace a la ficha de cada registro.
- **Datos y API:** `GET /api/sgspe/territory/data-quality?check=&object=&project=&page=&size=` (`api.md` §4); `check` es
  obligatorio y una revisión por pedido.
- **Estados:** lista vacía, «Sin hallazgos en esta revisión»; un objeto que la persona no lee no se ofrece en el filtro;
  un objeto sin ficha se lista sin enlace.
- **Permisos:** solo salen los objetos que la persona lee con todos los campos de la revisión.
- **Responsive:** las pestañas pasan a un `Select` y las filas a tarjetas.
- **Relacionadas y navegación:** cada fila abre la ficha del registro (S13, S14, el mapa del proyecto S09 para un
  componente, o la de su objeto); S12 enlaza aquí y al tablero.

```text
DataQualityPage
├── EntityTabs (INVALID · OUT_OF_ZONE · STALE_AREA · NO_TERRITORY · NO_GEOMETRY)
├── FilterBar (objeto · proyecto)
├── FindingsTable (DataTable paginada con enlace a la ficha)
└── Pagination
```

## 5. Grupos de interés

### S16 · Lista de grupos de interés

- **Ruta y lienzo:** `/projects/:projectId/stakeholders` y `/stakeholders` (global, agrega la columna Proyecto; ASM-076) ·
  hoja `GI-Lista` · F2.
- **Propósito:** ver a quiénes se gestiona, priorizar y actuar sobre varios a la vez.
- **Roles:** lectura de todos. Crear: CRO, SHM. Exportar: SMG, SHM, ANL (`EXPORT`).
- **Acciones:** primaria "Nuevo grupo de interés"; secundarias "Ver como matriz" (S19), "Exportar"; masivas "Reasignar
  responsable", "Registrar una interacción con ellos" (abre S24 con los seleccionados) y "Exportar".
- **Disposición y componentes:** `BarraDeFiltros` (§2) con el `SelectorDeVista` (del sistema: "Prioridad MUY ALTA y ALTA",
  "Míos", "Sin evaluar", "Sin contacto en N días", "Todos"; compartidas y mías, ASM-083), la búsqueda, tres filtros a la
  vista (grupo de influencia, postura, estado de la relación) y el ámbito y el tipo en «Más filtros»; `BulkActionBar`;
  `TablaPriorizada` con grupo de interés (actor y tipo), grupo de influencia (marca "ajustado" si `group_overridden`),
  postura, prioridad y estado (WF-B) como esenciales; territorio y último contacto como secundarias; y la relación, el
  responsable y la evaluación como opcionales, en «Columnas». A 1440 px no hay desplazamiento horizontal
  (CA-1.6.2).
- **Datos y API:** genérica `GET /api/objects/stakeholder/records?project=` con los valores vigentes derivados; la vista
  "Sin contacto" usa `inactivity_days` del proyecto.
- **Estados:** sin evaluar, el grupo y la prioridad dicen "Sin evaluar"; los retirados solo en su vista.
- **Permisos:** sin `stance`, la columna dice "Restringida" y no se filtra ni ordena por ella; sin nombres de personas, la fila
  muestra el cargo.
- **Responsive:** en teléfono, tarjeta con nombre, grupo, postura (si se puede) y último contacto.
- **Relacionadas y navegación:** fila → S17; con el chip de territorio, la lista se limita al lugar (`dashboards.md` §3.1).

```text
StakeholdersListPage
├── PageHeader (conteo · Ver como matriz · Exportar · Nuevo)
├── BarraDeFiltros (SelectorDeVista · búsqueda · filtros · chips)
├── BulkActionBar
└── TablaPriorizada<StakeholderRow> (Columnas) · Pagination
```

### S17 · Ficha de grupo de interés

- **Ruta y lienzo:** `/projects/:projectId/stakeholders/:stakeholderId` · hoja `GI-Ficha` · F2 (compromisos desde F3, QCS
  desde F4).
- **Propósito:** decidir cómo relacionarse con este grupo de interés: su peso, su postura, su historia y lo pendiente.
- **Roles:** lectura de todos. Caracterizar: SHM, CRO, SMG. Evaluar (`assess` y endpoint): SHM, SMG. Retirar y reincorporar:
  SHM, SMG, con motivo.
- **Acciones:** primaria "Registrar interacción"; secundarias "Nueva evaluación" (S20), "Más": reasignar, retirar.
- **Disposición y componentes:** `EntityHeader` con estado (WF-B), tipo, ámbito, responsable y actor; panel "Para decidir"
  (grupo e índices, postura y su cambio, confianza, pendientes); preocupaciones y expectativas; evaluaciones como línea de
  tiempo hasta la línea base; caracterización; a la derecha `TraceabilityPanel` ("Qué generó": interacciones, compromisos,
  acuerdos), relaciones del actor y "Próximo".
- **Datos y API:** genérica `stakeholder`, `stakeholder_assessment?stakeholder=` (ordenadas), `engagement_stakeholder`,
  `commitment_stakeholder` y `actor_relationship` del actor.
- **Estados:** sin evaluaciones, el panel invita a evaluar; un `STANCE_DIVERGENCE` de la última evaluación se muestra como
  advertencia.
- **Permisos:** postura, razones, preocupaciones y expectativas solo para SMG y SHM; los demás ven el candado.
- **Responsive:** el panel de trazabilidad baja al final.
- **Relacionadas y navegación:** actor → S18; interacción → S23; compromiso → S29; el rastro conserva el lugar de origen.

```text
StakeholderDetailPage
├── ContextBar · Trail
├── EntityHeader (StatusBadge · EntityActions)
├── DecisionPanel (InfluenceCard · StanceCard · TrustCard · PendingCard)
├── ConcernsExpectations
├── AssessmentTimeline
├── ProfileCard
└── SidePanel (TraceabilityPanel · ActorRelations · NextEngagement)
```

### S18 · Ficha de actor

- **Ruta y lienzo:** `/actors/:actorId` (con `?project=`) · hoja `GI-Actor` · F2.
- **Propósito:** conocer a la persona u organización detrás de uno o varios grupos de interés: cargos y vacantes, afiliación,
  contactos con consentimiento, proyectos y red.
- **Roles:** lectura de todos. Personas: CRO, SHM, GVM. Organizaciones: CRO, SHM, SIM. Cargos, contactos y relaciones: CRO,
  SHM.
- **Acciones:** primaria "Editar"; secundarias "Agregar cargo", "Incluir en otro proyecto" (crea el `stakeholder`).
- **Disposición y componentes:** encabezado (tipo de organización, afiliación, nivel territorial, comunidad que representa);
  tabla de cargos con estado (VIGENTE, VACANTE, EN LICENCIA, PREVISTO, CONCLUIDO); red de relaciones (diagrama con tabla
  equivalente); a la derecha, proyectos donde es grupo de interés, medios de contacto con consentimiento, redes comunitarias.
- **Datos y API:** genérica `organization` o `person`, `position?organization=`, `contact_method`, `actor_relationship`,
  `network_node`, `stakeholder?organization=`.
- **Estados:** un medio sin consentimiento se marca "no se usa para avisos" (RNF-PRIV-004).
- **Permisos:** nombres de personas y valores de contacto según `security.md` §7.9 (se muestran enmascarados o como "Persona
  registrada"); proyectos sin acceso no se listan.
- **Responsive:** el diagrama de red se reemplaza por su tabla en teléfono.
- **Relacionadas y navegación:** cada proyecto abre su S17.

```text
ActorDetailPage
├── EntityHeader
├── PositionsTable
├── RelationsNetwork (diagrama · tabla equivalente)
└── SidePanel (StakeholderInProjects · ContactMethods · CommunityNetworks · Achievements)
```

### S19 · Matriz interés × poder

- **Ruta y lienzo:** `/projects/:projectId/stakeholders/matrix` · hoja `GI-Matriz` (interactiva) · F2.
- **Propósito:** ver de un vistazo quién pesa más y cómo se movió desde la línea base.
- **Roles:** lectura de todos.
- **Acciones:** primaria "Abrir la ficha" del punto; secundarias "Nueva evaluación", "Ver como tabla", "Movimiento desde la
  línea base".
- **Disposición y componentes:** `Chart` de dispersión con umbrales del proyecto y cuadrantes rotulados con los grupos de
  influencia (`dashboards.md` §5.3); puntos de un solo color, anillo para grupo ajustado; panel de detalle; lista de puntos
  como alternativa de teclado.
- **Datos y API:** `GET /api/sgspe/dashboards/indicators/KPI-STK-03?project=` (un solo proyecto); umbrales
  `interest_threshold` y `power_threshold`.
- **Estados:** los grupos de interés sin evaluar no aparecen y se cuentan arriba.
- **Permisos:** la etiqueta del punto es el nombre si se puede leer y, si no, el cargo o la organización; la postura solo en el
  detalle y solo para SMG y SHM.
- **Responsive:** en teléfono, la matriz cede el lugar a la tabla.
- **Relacionadas y navegación:** punto → S17.

```text
StakeholderMatrixPage
├── PageHeader (umbrales · Ver como tabla · TrailToggle)
├── FilterBar
├── InterestPowerChart (Quadrants · Points · BaselineTrail)
└── PointDetailPanel · PointsListNav
```

### S20 · Nueva evaluación

- **Ruta y lienzo:** `Drawer` sobre S17 (`?drawer=assessment`) · hoja `GI-Evaluacion` · F2.
- **Propósito:** registrar una evaluación inmutable con el cálculo a la vista antes de guardar.
- **Roles:** SHM, SMG.
- **Acciones:** primaria "Guardar evaluación"; secundaria "Cancelar".
- **Disposición y componentes:** fecha (`CampoFecha`), base y cargo evaluado; las 15 escalas agrupadas en **cinco temas de
  tres** (DD-29, U-29): Interés (Afectación, Oportunidad) y Poder (Recursos de poder, Acción y organización, Redes y
  movilización), plegables y uno abierto a la vez, cada uno con `Radio` de 1 a 5, el valor anterior y su avance, y arriba
  el avance total («N de 5 temas calificados»). Calificar un tema abre el siguiente; guardar con temas sin calificar abre
  el primero que falta. Lo que se escribe no cambia. Luego postura,
  apertura, confianza, razones, preocupaciones y expectativas; motivo obligatorio; vista previa con índices, mini matriz,
  grupo, prioridad y "Ajustar el grupo a mano" (pide justificación).
- **Datos y API:** `POST /api/sgspe/stakeholders/{id}/assessments/preview` al cambiar un indicador (con espera de 300 ms);
  `POST /api/sgspe/stakeholders/{id}/assessments` (IK desde F3, IM opcional, CR).
- **Estados:** advertencia `STANCE_DIVERGENCE` de la vista previa antes de guardar; `409` si el grupo de interés está
  `retired`.
- **Permisos:** la pantalla no se ofrece a quien no puede escribir `stance`.
- **Responsive:** el drawer ocupa la pantalla en tableta; la vista previa pasa arriba como resumen fijo.
- **Relacionadas y navegación:** al guardar vuelve a S17 con la nueva evaluación arriba de la línea de tiempo.

```text
AssessmentDrawer
├── AssessmentForm (RHF + Zod)
│   ├── BasisFields
│   ├── AvanceDeTemas («N de 5 temas calificados»)
│   ├── TemaPlegable × 5 (IndicatorRadio × 3)
│   ├── StanceFieldset (restringido)
│   └── ReasonField
├── AssessmentPreview (Indices · MiniMatrix · GroupOverride · Warnings)
└── DrawerFooter
```

## 6. Relacionamiento

### S21 y S22 · Calendario y lista de interacciones

- **Ruta y lienzo:** `/projects/:projectId/engagements/calendar` y `/projects/:projectId/engagements` (también globales) ·
  hoja `Rel-Calendario` · F2.
- **Propósito:** planificar y seguir las interacciones del mes frente a la meta, con las festividades de la línea base.
- **Roles:** lectura de todos. Registrar: CRO, SHM, FAG (SMG edita). Visitas: CRO, FAG.
- **Acciones:** primaria "Registrar interacción"; secundarias "Nueva visita de campo", vistas Mes, Semana y Lista.
- **Disposición y componentes:** calendario mensual con eventos por estado (WF-C) y tipo; festividades como franja de contexto;
  leyenda; la misma consulta como `DataTable`; a la derecha, meta de reuniones (KPI-ENG-02), "Te toca" (actas por redactar,
  postergadas, preguntas pendientes) y festividades del mes.
- **Datos y API:** genérica `GET /api/objects/engagement/records?project=&scheduled_at=` y `held_at`; `festivity` de las
  localidades del proyecto; KPI-ENG-02 con `GET /api/sgspe/dashboards/indicators/KPI-ENG-02`.
- **Estados:** una interacción formal realizada sin acta se marca (RN-ENG-004); cancelada, tachada con su motivo al pasar el
  cursor.
- **Permisos:** FAG solo mueve las que creó (`EditAnyGuard`).
- **Responsive:** en teléfono, la vista Lista o agenda del día.
- **Relacionadas y navegación:** evento → S23; "Te toca" → S25 o la ficha.

```text
EngagementCalendarPage
├── PageHeader (Registrar · Nueva visita)
├── ViewSwitcher (Mes · Semana · Lista) · MonthNav · FilterBar
├── MonthCalendar (EngagementEvent · FestivityBand) | EngagementList
└── SidePanel (MeetingsTarget · MyPending · MonthFestivities)
```

### S23 · Ficha de interacción

- **Ruta y lienzo:** `/projects/:projectId/engagements/:engagementId` · hoja `Rel-Ficha` · F2 (acuerdos y compromisos desde F3,
  derivación de preguntas desde F4).
- **Propósito:** qué pasó, con quién, qué se preguntó y qué se acordó, con su evidencia.
- **Roles:** lectura de todos. Editar: CRO, SHM, SMG con `EDIT_ANY`; FAG lo suyo. Aprobar y devolver el acta: SMG.
- **Acciones:** primaria la siguiente transición de WF-C para el rol (por ejemplo "Cerrar la interacción"); secundarias "Ver el
  acta", responder o derivar preguntas.
- **Disposición y componentes:** encabezado (tipo, estado, fecha, lugar, convocante); `Tabs` (Resumen, Participantes,
  Preguntas, Acta, Acuerdos, Evidencias, Actividad); asistentes por sexo y ámbito con `Chart` y tabla de filas (personas y
  grupos sin nombres); preguntas pendientes con "Responder", "Usar respuesta alineada" y "Derivar" (F4); a la derecha, "Qué
  generó" (compromisos por `origin_engagement`, candidatos), evidencias y "De dónde viene".
- **Datos y API:** genérica `engagement`, `engagement_participant`, `engagement_question`, `engagement_topic`;
  `POST /api/sgspe/engagement-questions/{id}/referral` (F4); `POST /api/sgspe/transition-checks` para el cierre (RN-ENG-005).
- **Estados:** "Cerrar" deshabilitado con la razón mientras haya preguntas sin respuesta ni derivación; los conteos se
  recalculan y nunca se teclean.
- **Permisos:** nombres y contactos de asistentes solo para SMG, CRO, SHM y FAG.
- **Responsive:** pestañas como menú; el panel de trazabilidad al final.
- **Relacionadas y navegación:** compromisos → S29; acta → S25; convocante → S17.

```text
EngagementDetailPage
├── ContextBar · Trail
├── EntityHeader (StatusBadge · TransitionButton con razón)
├── EntityTabs
│   ├── SummaryTab (AttendanceCharts · ParticipantsTable · QuestionsList)
│   └── … Participantes · Preguntas · Acta · Acuerdos · Evidencias · Actividad
└── SidePanel (TraceabilityPanel · EvidenceList · OriginCard)
```

### S24 · Registrar interacción

- **Ruta y lienzo:** `/projects/:projectId/engagements/new` · hoja `Rel-Registrar` (escritorio y modo rápido de campo) · F2.
- **Propósito:** registrar una interacción planificada o ya realizada, con asistentes sin pedir datos personales que no hacen
  falta.
- **Roles:** CRO, SHM, FAG.
- **Acciones:** primaria "Siguiente" y, al final, "Guardar como realizada" (`register_held_unplanned`) o "Programar"
  (`schedule`); secundaria "Guardar y completar después".
- **Disposición y componentes:** `StepIndicator` (1 Tipo y fecha, 2 Lugar y grupos de interés, 3 Participantes, 4 Preguntas y
  temas, 5 Revisión); en el paso 3, filas de persona o representante y de grupo sin nombres (sexo, ámbito, personas ≥ 1) con
  totales en vivo. Modo rápido en teléfono: una sola pantalla con tipo, ubicación del teléfono, grupos presentes, contadores
  por sexo y ámbito, foto y nota; objetivos de 44 px.
- **Datos y API:** genérica `POST /api/objects/engagement/records`, `engagement_participant` y `engagement_question`;
  `POST /api/sgspe/evidence` para la foto (F3); la transición elegida al final. Abierta desde S16 con seleccionados, trae los
  grupos de interés precargados.
- **Estados:** `represented_people_count` menor que 1 marca el campo (lo rechaza `EngagementWriteGuard`); sin conexión no se
  guarda (el modo sin conexión no está en el MVP, Q5).
- **Lugar (F6):** en el paso 2, un clic en el mapa marca el punto y llama a `GET /api/sgspe/territory/locate?lon=&lat=`,
  que propone unidad administrativa, localidad y comunidad; la persona elige «Usar» para cada una y nada se fija solo.
  En el formulario rápido de campo el punto es el del teléfono y se propone lo mismo. Lo que no puede leer sale
  «no disponible».
- **Permisos:** FAG solo cambia después lo que registró.
- **Responsive:** el modo rápido es la versión de teléfono.
- **Relacionadas y navegación:** al guardar abre S23; el territorio sale del chip de contexto.

```text
EngagementWizardPage
├── StepIndicator
├── <Step>: TypeDateStep · PlaceStakeholdersStep · ParticipantsStep · QuestionsTopicsStep · ReviewStep
│    └── ParticipantsStep: ParticipantRow · GroupRow · AttendanceTotals
├── WizardFooter
└── FieldQuickCapture (teléfono)
```

### S25 · Editor de acta

- **Ruta y lienzo:** `/projects/:projectId/engagements/:engagementId/minutes` · hoja `Rel-Acta` · F2 (proponer compromiso
  desde F3, borrador de IA y candidatos de IA en F7).
- **Propósito:** redactar el acta con estructura, enviarla a revisión y proponer compromisos desde su texto.
- **Roles:** redactar y enviar: CRO, SHM, SMG, FAG (lo suyo). Aprobar o devolver: SMG. Proponer compromiso: SMG, CRO, CMM.
- **Acciones:** primaria "Enviar a revisión" (`submit_minutes`); secundarias "Vista previa", "Proponer compromiso" sobre una
  selección, "Insertar asistentes" y "Insertar preguntas"; IA: "Insertar y editar" o "Descartar con motivo".
- **Disposición y componentes:** estructura lateral (Datos, Asistentes, Agenda, Desarrollo, Preguntas, Acuerdos, Cierre);
  editor Markdown sin HTML; popover de propuesta (qué, fecha sugerida, responsable sugerido); a la derecha, `AiSuggestionCard`
  del borrador (F7), candidatos propuestos desde esta acta y el bloque de revisión.
- **Datos y API:** genérica `PUT` de `engagement.minutes` y transiciones `draft_minutes`, `submit_minutes`,
  `return_minutes` (CR) y `approve_minutes`; `POST /api/sgspe/commitment-candidates` con `proposed_text` y `evidence_quote`;
  borrador de IA: `ai_suggestion` BORRADOR DE ACTA (AI-02, F7).
- **Estados:** guardado automático; una devolución muestra su motivo arriba del editor; el acta firmada se sube como evidencia
  ACTA, no es un estado (CON-032).
- **Permisos:** la sugerencia de IA nunca escribe el acta sola; aceptar o descartar queda en la auditoría.
- **Responsive:** de escritorio; en tableta, la estructura pasa a un menú.
- **Relacionadas y navegación:** candidatos → vista Candidatos de S28; vuelve a S23.

```text
MinutesEditorPage
├── EditorHeader (StatusBadge · guardado · Vista previa · Enviar a revisión)
├── MinutesOutline
├── MarkdownEditor (Toolbar · ProposeCommitmentPopover)
└── SidePanel (AiSuggestionCard · CandidatesFromMinutes · ReviewInfo)
```

### S26 · Visita de campo

- **Ruta y lienzo:** `/projects/:projectId/field-visits/:visitId` · hoja `Rel-Visita` · F2.
- **Propósito:** seguir una salida a campo: ruta, interacciones, observaciones geolocalizadas y cómo terminó.
- **Roles:** lectura de todos. Editar y transicionar: CRO, FAG (lo suyo), SMG. Observaciones: CRO, FAG.
- **Acciones:** primaria "Agregar observación aquí"; secundarias "Registrar interacción de la visita", "Concluir…".
- **Disposición y componentes:** encabezado con estado (WF-L) y `WorkflowStepper` (Planificada → En curso → Concluida o
  Parcial); mapa con la ruta, interacciones y observaciones; listas de interacciones y observaciones; objetivo e itinerario;
  diálogo de conclusión (Concluida o Parcial, resumen del resultado).
- **Datos y API:** genérica `field_visit`, `field_observation?field_visit=`, `engagement?field_visit=`; transiciones `start`,
  `complete`, `complete_partial`, `cancel` (CR).
- **Estados:** concluir no cambia las interacciones pendientes: cada una se posterga o cancela con motivo.
- **Permisos:** FAG sin `EDIT_ANY` solo cambia lo que creó.
- **Responsive:** en teléfono, mapa arriba y botón fijo "Agregar observación".
- **Relacionadas y navegación:** interacciones → S23; el resumen alimenta el informe de actividades.

```text
FieldVisitPage
├── EntityHeader (StatusBadge · WorkflowStepper · acciones)
├── VisitMap (ruta · interacciones · observaciones)
├── VisitEngagementsList · ObservationsList
└── SidePanel (ObjectiveItinerary · ConcludeVisitDialog)
```

## 7. Compromisos y acuerdos

### S28 · Lista de compromisos y vista Candidatos

- **Ruta y lienzo:** `/projects/:projectId/commitments` y `/commitments` (global); vista `?view=candidates` · hojas `Pat-Lista`
  (etapa 1) y `Com-Candidatos` · F3 (candidatos de IA en F7).
- **Propósito:** saber qué se debe, a quién y desde cuándo, con lo vencido primero; decidir sobre los posibles compromisos de
  las actas.
- **Roles:** lectura de todos salvo PAD y FAG. Crear: SMG, CRO, CMM. Exportar: SMG, CMM, ANL. Candidatos (aceptar, rechazar,
  duplicado): CMM, SMG, CRO.
- **Acciones:** primaria "Nuevo compromiso"; masivas "Reasignar responsable", "Asignar verificador", "Cambiar prioridad" y
  "Exportar" (no hay cambio masivo de fecha, RF-COM-003). En Candidatos: "Aceptar y crear borrador", "Es duplicado de…",
  "Rechazar".
- **Disposición y componentes:** patrón de lista (`design-system.md` §6.4, con vistas compartidas y mías, ASM-083): en la
  cabecera, el `SelectorDePagina` «Compromisos · Acuerdos» (DD-23) y «Lista · Tablero»; la `BarraDeFiltros` con las vistas
  en el `SelectorDeVista` (Abiertos, Vencidos, Míos, Candidatos…; «Más vistas» con el resto), la búsqueda, el estado como
  filtro frecuente y los chips; agrupación Vencidos, Por vencer, Bloqueados; la vista Candidatos lista los propuestos con
  su procedencia (humano o IA con confianza) y los decididos; panel de revisión
  con la cita, campos corregibles y las tres salidas.
- **Datos y API:** genérica `GET /api/objects/commitment/records?project=`; candidatos con
  `GET /api/objects/commitment_candidate/records?project=&state=proposed`; `POST /api/sgspe/commitment-candidates/{id}/accept`
  (IK, IM, CR), `POST …/duplicate` (`duplicateOf`, CR) y la transición `reject` con motivo.
- **Estados:** aceptar crea el compromiso en `draft` y deja el candidato en `accepted` con su enlace; la IA nunca crea un
  compromiso.
- **Permisos:** la cita (`evidence_quote`) solo para los roles con `DOWNLOAD_RESTRICTED` (`security.md` §7.9); el verificador
  asignado no puede ser el responsable (D11).
- **Responsive:** tarjetas con plazo y estado; el panel de candidato a pantalla completa.
- **Relacionadas y navegación:** fila → S29; candidato aceptado → su compromiso en S29 o S30 para completar.

```text
CommitmentsListPage
├── PageHeader (SelectorDePagina Compromisos · Acuerdos · Lista | Tablero · Nuevo compromiso)
├── BarraDeFiltros (SelectorDeVista … · Candidatos) · BulkActionBar
├── DataTable<CommitmentRow> (grupos por atención)
└── CandidatesView
     ├── CandidatesList
     └── CandidateReviewPanel (EvidenceQuote · CorrectionFields · DecisionRadio · DuplicatePicker · RejectReason)
```

### S29 · Ficha de compromiso

- **Ruta y lienzo:** `/projects/:projectId/commitments/:commitmentId` · hojas `Pat-Ficha` (etapa 1, Resumen) y
  `Com-Pestanas` (Acciones e hitos, Entregas, Reprogramaciones, Dependencias) · F3.
- **Propósito:** llevar el compromiso hasta su cierre con evidencia, sin perder de dónde vino.
- **Roles:** lectura de todos salvo PAD y FAG. Transiciones según WF-D. Acciones: CRO, CMM. Hitos y dependencias: CMM.
  Reprogramar: CMM. Entregas: CRO, CMM.
- **Acciones:** primaria la siguiente transición de WF-D para el rol; secundarias por pestaña: "+ Acción", "+ Hito",
  "Reprogramar", "Registrar entrega", "+ Dependencia", "Riesgo de retraso informado".
- **Disposición y componentes:** anatomía de `design-system.md` §6.1; pestañas Resumen, Acciones e hitos, Entregas,
  Reprogramaciones, Dependencias, Evidencias, Actividad; ejecutado frente al monto; diálogo de reprogramación con fecha
  anterior, nueva, motivo y "acordado con el grupo de interés".
- **Datos y API:** genérica `commitment`, `commitment_action`, `commitment_milestone`, `commitment_dependency`, `delivery`;
  `POST /api/sgspe/commitments/{id}/reschedules` (IK, IM, CR); transiciones por la API genérica con CR.
- **Estados:** después de `approve`, `due_date` no se edita: solo por reprogramación (un `PUT` que la toque es `400`); una
  dependencia bloqueante abierta se avisa en la pestaña y en el diálogo de cumplimiento (D10).
- **Permisos:** `beneficiaries_detail` y los datos de conformidad según `security.md` §7.9.
- **Responsive:** pestañas como menú; diálogos a pantalla completa en teléfono.
- **Relacionadas y navegación:** "De dónde viene" (interacción, cláusula, solicitud) y "Qué generó" (tareas, entregas,
  evidencias) en el panel de trazabilidad.

```text
CommitmentDetailPage
├── ContextBar · Trail
├── EntityHeader · WorkflowStepper · DecisionPanel
├── EntityTabs
│   ├── SummaryTab
│   ├── ActionsMilestonesTab
│   ├── DeliveriesTab (DeliveryCard · ExecutedProgress)
│   ├── ReschedulesTab (RescheduleHistory · RescheduleDialog)
│   ├── DependenciesTab
│   └── EvidenceTab · ActivityTab
└── TraceabilityPanel
```

### S30 · Crear compromiso

- **Ruta y lienzo:** `/projects/:projectId/commitments/new` (también desde un candidato aceptado) · hoja `Com-Crear` (pasos 5
  y 7) · F3.
- **Propósito:** registrar un compromiso completo según la matriz y enviarlo a aprobación.
- **Roles:** crear y enviar: SMG, CRO, CMM. Aprobar sin revisión interna (`approve_direct`, solo con la política desactivada):
  CMM, SMG.
- **Acciones:** primaria "Enviar a aprobación" (`submit`); secundarias "Guardar borrador", "Guardar y salir".
- **Disposición y componentes:** pasos de `design-system.md` §6.3; paso 5 con monto obligatorio, moneda, campaña y la barra del
  tope (aprobado, este compromiso, lo que queda) y el aviso de lo que pasa si se supera; paso 7 con la lista D1 y D2, "Qué pasará"
  y el motivo.
- **Datos y API:** genérica `POST /api/objects/commitment/records` (en `draft`), `commitment_stakeholder`; tope con
  `exploration_campaign.social_support_cap` y los aprobados de la campaña; `POST /api/sgspe/transition-checks` en el paso 7;
  transición `submit` con CR.
- **Estados:** "Enviar" deshabilitado mientras falte un dato de D1, con enlace al paso; la justificación de origen se pide si
  nace de un taller o una reunión informativa (RN-ENG-001, advertencia por defecto).
- **Permisos:** la evidencia no se pide al crear (RN-COM-001).
- **Responsive:** como todo formulario por pasos.
- **Relacionadas y navegación:** al enviar, abre S29 en `pending_approval`; la tarea de aprobación llega a S31.

```text
CommitmentWizardPage
├── StepIndicator (7 pasos)
├── <Step> … AmountCampaignStep (CampaignCapBar · OverCapCallout) … ReviewSubmitStep (D1Checklist · Callout · ReasonField)
└── WizardFooter
```

### S31 · Aprobación de compromiso

- **Ruta y lienzo:** `/tasks/approvals/:taskId` · hoja `Com-Aprobacion` · F3.
- **Propósito:** decidir con las reglas a la vista: aprobar, devolver o rechazar.
- **Roles:** APR (`approve`, `return_to_draft`, cancelar); PMG (`approve_senior`, con `APPROVE_OVER_CAP` para el tope).
- **Acciones:** primaria "Aprobar"; secundarias "Devolver a borrador", "Rechazar y cancelar", "Después".
- **Disposición y componentes:** bandeja a la izquierda (todas o asignadas a mí, con la razón cuando la persona no puede
  aprobar); compromiso con su cita de origen; decisión con motivo obligatorio; a la derecha, reglas D1 a D7 con su resultado y
  los otros casos (tope superado, mesa de diálogo, registrado por la misma persona).
- **Datos y API:** tareas con `task` de aprobación; compromiso por la API genérica; `POST /api/sgspe/transition-checks` con la
  transición del rol; transiciones `approve`, `approve_senior`, `return_to_draft`, `cancel_pending_approval` (todas con CR).
- **Estados:** si la persona registró el compromiso (D4) o exige nivel superior (D5), "Aprobar" no aparece y se explica por qué.
- **Permisos:** quien decide no edita los datos del compromiso (`u` de APR, `security.md` §7.1).
- **Responsive:** la bandeja pasa a una lista previa a la decisión.
- **Relacionadas y navegación:** al aprobar, la tarea de ejecución llega a la persona responsable (AUT-01).

```text
ApprovalTaskPage
├── ApprovalsInbox
├── CommitmentApprovalSummary (OriginQuote · OriginTrail)
├── DecisionForm (DecisionRadio · ReasonField · Aprobar)
└── RulesPanel (RuleCheck × 7 · OtherCasesNote)
```

### S32 · Entrega, cumplimiento y verificación

- **Ruta y lienzo:** `Drawer` en S29 (`?drawer=delivery` y `?drawer=verify`) · hoja `Com-Entrega` (interactiva) · F3.
- **Propósito:** capturar la entrega con su evidencia, afirmar el cumplimiento y que otra persona lo verifique.
- **Roles:** entrega: CRO, CMM. Registrar cumplimiento: CMM, CRO, SMG. Devolver a ejecución: CMM, SMG. Verificar: CMM, SMG con
  `VERIFY` y que no sean responsables. Cerrar: CMM, SMG.
- **Acciones:** "Guardar entrega" → "Registrar cumplimiento" → "Verificar" (o "Devolver a ejecución…") → "Cerrar el
  compromiso".
- **Disposición y componentes:** formulario de entrega (fecha, lugar, qué, monto, conformidad y de quién) con `FileUploader`
  de evidencias según el tipo de recurso; lista de reglas D9 y D10 antes del cumplimiento; lista de revisión del verificador;
  estado bloqueado con explicación para la responsable.
- **Datos y API:** `POST /api/sgspe/commitments/{id}/deliveries` (IK, CR) con `evidence[]` subidas por
  `POST /api/sgspe/evidence`; `PATCH /api/sgspe/deliveries/{id}` para corregir (`409` si ya está cumplido); transiciones
  `register_fulfillment`, `reopen_execution`, `verify`, `close` con CR; `POST /api/sgspe/transition-checks` antes de cada una.
- **Estados:** guardar la entrega no cambia el estado; MATERIAL exige ACTA DE ENTREGA y FOTO, NO MATERIAL ACTA, CARTA u OTRO
  (D9, ASM-009).
- **Permisos:** `recipients` y `conformity_by` según `security.md` §7.9; D11 en el servidor aunque la interfaz oculte el botón.
- **Responsive:** en teléfono, captura de entrega en una pantalla con cámara para las fotos.
- **Relacionadas y navegación:** vuelve a S29; las evidencias quedan en "Qué generó".

```text
DeliveryDrawer
├── DeliveryForm (RHF + Zod · FileUploader por tipo de recurso)
├── FulfillmentCheck (RuleCheck D9 · D10 · ReasonField)
└── VerificationPanel (ReviewChecklist · ReasonField · SegregationNotice)
```

### S33 · Acuerdo o convenio

- **Ruta y lienzo:** `/projects/:projectId/agreements/:agreementId` · hoja `Com-Acuerdo` · F3.
- **Propósito:** tener el acuerdo con sus partes, cláusulas, compromisos por cláusula, vigencia, predios y asamblea que lo
  autoriza.
- **Roles:** lectura de todos salvo FAG y PAD. Crear y editar: SMG, CRO (partes y cláusulas). Transiciones de WF-I:
  iniciar negociación SMG, CRO; registrar vigencia, suspender, reanudar y concluir SMG, PMG; resolver PMG; anular SMG, PMG.
- **Acciones:** primaria "Registrar compromiso de la cláusula N" cuando una cláusula marcada no tiene compromiso;
  secundarias las transiciones del rol.
- **Disposición y componentes:** encabezado con tipo, estado (WF-I) y situación de vigencia (VIGENTE, POR VENCER, VENCIDO) como
  insignia derivada; barra de vigencia; tabla de cláusulas con sus compromisos y estado; a la derecha, partes con firma,
  titular comunal, asamblea que autoriza, predios con mapa y evidencias.
- **Datos y API:** genérica `agreement`, `agreement_party`, `agreement_clause`, `agreement_land_parcel`,
  `commitment?agreement=`; transiciones con CR.
- **Estados:** registrar vigencia exige predios y titular en permisos de uso y servidumbres, y la asamblea en autorizaciones
  (`AgreementWriteGuard`); vencer no concluye el acuerdo.
- **Permisos:** evidencias CONFIDENCIAL solo se descargan con `DOWNLOAD_RESTRICTED`.
- **Responsive:** cláusulas como tarjetas.
- **Relacionadas y navegación:** compromisos → S29; asamblea → S23; titular → S13.

```text
AgreementDetailPage
├── ContextBar
├── EntityHeader (StatusBadge · ValidityBadge · acciones)
├── ValidityBar
├── ClausesTable (ClauseRow · ClauseCommitments)
└── SidePanel (PartiesCard · AssemblyCard · ParcelsMap · EvidenceList)
```

## 8. Quejas, consultas y solicitudes

Lienzo de la etapa 3, página 11. Tres objetos con ciclos distintos (WF-E1, WF-E2, WF-E3; ASM-011) que la lista une para
atender por plazo. El plazo es `deadline_status`, nunca un estado (RN-WFL-001).

- **Registro reservado (ASM-095).** Una QCS confidencial que la persona no lee no aparece en las listas, los tableros ni
  los reportes. Un vínculo a ella desde otra pantalla (tarea, evidencia, compromiso, pregunta de reunión) muestra
  «Registro reservado», sin enlace ni datos, y los tableros y el reporte avisan cuando la cifra excluye confidenciales
  (`confidentialHidden`).
- **Registro reservado general y proyectos (ASM-106, plan 4.4b).** Todo vínculo cuyo destino responde `404` (otro
  proyecto o una QCS confidencial) muestra «Registro reservado»; una ficha que responde `404` dice «El registro no existe o
  no es de sus proyectos», sin distinguir. El selector de proyectos lista los `project` que la cuenta lee y, con
  `READ_ALL_PROJECTS` en `/api/auth/me/permissions`, ofrece «Todos los proyectos»; sin asignaciones ni la acción, dice «No
  tiene proyectos asignados. Pida una asignación al administrador de proyectos».

### S35 · Lista unificada de QCS

- **Ruta y lienzo:** `/projects/:projectId/attention` (y `/attention` entre proyectos) · hoja `QCS-Lista` · F4.
- **Propósito:** ¿qué QCS vence primero y quién la tiene? Atender por plazo sin mezclar los ciclos de los tres tipos.
- **Roles:** lectura para todos los roles con READ sobre `grievance`, `inquiry` y `stakeholder_request`; registran CRO y GVM;
  exportan SMG, GVM y ANL (`EXPORT`).
- **Acciones:** primaria "Registrar QCS" (S37); secundarias "Reporte unificado" y abrir una QCS.
- **Disposición y componentes:** cuatro indicadores (abiertas por tipo, vencidas, por vencer en 7 días, absueltas en 90 días),
  cada uno dicho con su periodo para que se entienda (U-24) y con `NotaDeConfidenciales` si excluye QCS confidenciales;
  `BarraDeFiltros` (§2) con las vistas en el `SelectorDeVista` (del sistema: Atención, Quejas, Consultas, Solicitudes, Mis
  QCS, Todas; compartidas y mías, ASM-083, hoja `Vistas-Compartidas`), la búsqueda, los filtros de canal, categoría,
  severidad y responsable, y los chips; `TablaPriorizada` agrupada por situación de plazo (VENCIDAS, POR VENCER, AL DÍA O
  SIN PLAZO) con tipo, canal, categoría, severidad, lugar, estado, plazo que corre y responsable, y la elección de
  «Columnas» compartida entre los grupos.
- **Datos y API:** genérica sobre los tres objetos con el filtro de territorio; `GET /api/sgspe/attention/report` para el
  reporte (totales por tipo, estado, canal y categoría). Indicadores KPI-GRV-02, KPI-GRV-06 y KPI-GRV-07.
- **Estados:** el plazo mostrado es el que corre en el estado: acuse o resolución en la queja, respuesta en la consulta; las
  solicitudes no tienen plazo por defecto (ASM-005). Una QCS anónima muestra "anónima" en lugar de la persona.
- **Permisos:** la persona que plantea una QCS es un campo restringido (`security.md` §7.9): solo SMG, CRO y GVM la ven.
- **Responsive:** por debajo de `lg` la tabla pasa a tarjetas con tipo, título, plazo y estado; los indicadores, a una fila
  desplazable.
- **Relacionadas y navegación:** S36 (queja) o S57 (consulta y solicitud) por fila; S37; el tablero de QCS (S34, etapa
  3b); la capa LYR-20 en el mapa.

```text
AttentionListPage
├── AttentionKpis (KpiCard × 4)
├── BarraDeFiltros (SelectorDeVista · búsqueda · filtros · chips)
└── AttentionTable (DeadlineGroup × 3 · TablaPriorizada · DeadlineChip · TypeBadge)
```

### S37 · Registrar QCS

- **Ruta y lienzo:** `/projects/:projectId/attention/new?type=grievance|inquiry|request` · hoja `QCS-Registrar` · F4.
- **Propósito:** registrar lo que se planteó, por el canal que llegó, sin decidir todavía su tratamiento.
- **Roles:** CRO y GVM crean los tres objetos. Es la única entrada de QCS: no hay formulario público (ASM-104, UPS-25),
  y un contratista reporta a través del equipo social con el canal CONTRATISTA (ASM-043).
- **Acciones:** primaria "Registrar queja" (o consulta, o solicitud); secundaria "Cancelar".
- **Disposición y componentes:** el asistente por pasos de las demás altas (`FormularioPorPasos`, P-04, U-30), en tres
  pasos, con la elección del tipo (su definición y sus plazos) arriba: 1 **«Qué se plantea»** (descripción, categoría,
  severidad en la queja); 2 **«Cómo llegó y quién»** (canal, fecha de recepción en `CampoFecha` de 24 h, interacción o
  pregunta de origen, anonimato, confidencialidad, grupo de interés, persona marcada como dato restringido, lugar);
  3 **«Revisión»**, que resume lo escrito y pide prioridad, responsable y motivo; adjuntos con tipo y clasificación de
  evidencia. A la derecha, en todos los pasos, "Qué pasará al registrar" con el código, el estado y los plazos
  calculados. Cada paso valida lo suyo al avanzar, y un error del servidor vuelve al paso de su campo. Lo que se manda al
  servidor no cambia.
  El interruptor «Confidencial» explica quién podrá leerla (gestión de quejas, su responsable, quien la recibe y, si es
  grave, quien aprueba) y deja a quien registra como quien la recibió (`received_by_user_id`, ASM-095).
- **Datos y API:** `POST` genérico del objeto; plazos derivados desde `received_at` con los parámetros del proyecto
  (ASM-005): el plazo de acuse lo calcula el backend en días hábiles con los feriados configurados (ASM-096), y la ficha
  lo muestra al registrar; adjuntos con `POST /api/sgspe/evidence` (IK, CR por ser destinos `requiresReason`). AUT-02
  crea la tarea de acusar recibo.
- **Estados:** anónima oculta persona y grupo de interés; severidad ALTA o CRITICA advierte que la resolución la aprueba otra
  persona (ASM-036). La IA no actúa en el formulario: sus sugerencias (AI-07, AI-08) llegan después, en S36 (F7).
- **Permisos:** la persona que plantea es campo restringido; los adjuntos siguen RF-DOC-003.
- **Responsive:** en teléfono, una sola pantalla para el relacionista: tipo, canal, lugar del dispositivo, descripción y foto;
  el resto lo completa quien gestiona QCS. Sin conexión entra en la cola de campo de la etapa 3b.
- **Relacionadas y navegación:** se llega desde S35, desde una interacción (S23) o desde una pregunta pendiente de reunión;
  al registrar se abre S36 o S57.

```text
AttentionCreatePage
├── TypePicker
├── FormularioPorPasos
│   ├── Paso «Qué se plantea» (DescriptionField · CategoryField · SeverityField)
│   ├── Paso «Cómo llegó y quién» (ChannelField · CampoFecha · OriginPicker · AnonymityToggle · ConfidentialToggle ·
│   │   StakeholderPicker · PlacePicker)
│   └── Paso «Revisión» (resumen · PriorityField · OwnerField · ReasonField · EvidenceDropzone)
└── WhatHappensPanel (Seccion aside, en todos los pasos)
```

### S36 · Ficha de queja

- **Ruta y lienzo:** `/projects/:projectId/grievances/:id` · hoja `QCS-Queja` (interactiva) · F4; sugerencias de IA F7.
- **Propósito:** llevar la queja de la recepción al cierre con sus plazos, su evidencia y su decisión a la vista.
- **Roles:** GVM acusa, evalúa, investiga, atiende, resuelve y cierra; CRO acusa y registra desistimiento; APR resuelve las
  ALTA y CRITICA (`resolve_high_severity`, `APPROVE`); GVM y SMG verifican.
- **Acciones:** la primaria es la transición siguiente del estado; secundarias "Rechazar…", "Registrar desistimiento…",
  "Reclasificar…", "Reabrir…", "Subir cargo firmado" y "Crear compromiso desde esta queja", que abre S30 con
  `origin_grievance` y el proyecto de la queja ya puestos (alta genérica del compromiso en `draft`).
- **Disposición y componentes:** encabezado con estado, severidad, situación de plazo, categoría, canal y responsable;
  camino de estados (investigación y verificación aparecen solo si se usan); panel de la acción con motivo obligatorio;
  el caso, la persona (restringida), acuse y plazo con su escalamiento, resolución y respuesta; a la derecha "De dónde viene",
  "Qué alimenta" (riesgo vinculado), la tarjeta de IA y "Plazos y escalamiento".
- **Datos y API:** genérica con `transitions`; `POST /api/sgspe/transition-checks`; `POST
  /api/sgspe/grievances/{id}/reclassify` (ASM-072); M2O `social_risk` e `incident`; `ai_suggestion` DUPLICADO (AI-07) y de
  clasificación (AI-08). El cargo de queja no se emite mientras UPS-23 siga abierta (ASM-098): "Subir cargo firmado" lo
  sube con `POST /api/sgspe/evidence` como evidencia FORMULARIO vinculada a la queja, con clasificación DATOS PERSONALES
  por defecto.
- **Estados:** con `policy_high_severity_requires_approver` activo, GVM no ve "Resolver" en una ALTA o CRITICA y se
  explica que la tiene aprobación, y APR la encuentra en S48 (`approval_pending`, ASM-102); una queja confidencial
  muestra quién puede leerla; sin conformidad del reclamante se puede reabrir; aceptar un duplicado rechaza la más nueva
  con `duplicate_of` y respuesta.
- **Permisos:** la persona que plantea es restringida; la hoja alterna "Gestión de QCS" y "Aprobación" para mostrar la
  segregación.
- **Responsive:** el panel lateral baja debajo del caso; la acción queda fija al pie.
- **Relacionadas y navegación:** el riesgo (S40), la interacción de origen (S23), el compromiso creado (S29), la evidencia
  (S46). Flujos 12 y 13.

```text
GrievancePage
├── RecordHeader (StateBadge · SeverityBadge · DeadlineChip)
├── StatePath
├── TransitionPanel (TransitionChecks · ReasonField)
├── CaseSection · ComplainantField (restricted) · DeadlineSection · ResolutionSection
└── SidePanel (OriginTrail · FeedsRiskCard · AiSuggestionCard · EscalationNote)
```

### S57 · Ficha de consulta y ficha de solicitud

- **Ruta y lienzo:** `/projects/:projectId/inquiries/:id` y `/projects/:projectId/requests/:id` · hoja
  `QCS-Consulta-Solicitud` · F4; aceptar como aporte desde F5.
- **Propósito:** la misma anatomía que la queja con su propio ciclo: responder o derivar una consulta; evaluar y aceptar o
  declinar una solicitud.
- **Roles:** CRO y GVM responden y derivan consultas; las solicitudes las evalúan y aceptan los roles de WF-E3; aceptar como
  compromiso lo hacen SMG y CMM desde F4 (`security.md` §7.5), y como aporte, desde F5, SMG y SIM, que tienen CREATE
  sobre `social_contribution`; CMM solo acepta como compromiso (ASM-117).
- **Acciones:** consulta: "Responder", "Derivar a otra entidad…", "Reclasificar…", "Subir carta firmada". Solicitud:
  "Aceptar y crear el compromiso" (el aporte, desde F5), "Declinar con respuesta…", "Reclasificar…", "Subir carta
  firmada".
- **Disposición y componentes:** encabezado y camino de estados; origen (pregunta de reunión o carta); respuesta con
  "Usar respuesta alineada" (mensajes del proyecto); en la solicitud, "Aceptar: ¿qué se crea?" con las dos opciones;
  "Aporte" solo aparece a quien tiene CREATE sobre `social_contribution`.
- **Datos y API:** transiciones de WF-E2 y WF-E3; `POST /api/sgspe/stakeholder-requests/{id}/accept` (crea `commitment` en
  `draft` o, desde F5, `social_contribution` en `requested`); reclasificación por su endpoint. Las cartas no se emiten
  mientras UPS-23 siga abierta (ASM-098): "Subir carta firmada" la sube como evidencia CARTA vinculada a la QCS, con
  clasificación DATOS PERSONALES por defecto.
- **Estados:** una consulta con fecha de comunicación cuenta como absuelta (KPI-GRV-07); la solicitud aceptada se cierra sola
  al cerrarse su compromiso o su aporte (ASM-068) y se cierra a mano "sin ejecución" si aquel se cancela.
- **Permisos:** como S36 queja.
- **Responsive:** como S36 queja.
- **Relacionadas y navegación:** al aceptar se abre el compromiso (S29, S30) o el aporte (S44); la pregunta de reunión
  derivada deja cerrar la reunión (S23).

```text
InquiryPage | RequestPage
├── RecordHeader · StatePath
├── OriginTrail
├── AnswerSection (AlignedMessagePicker · ReasonField)   // consulta
└── AcceptSection (AcceptTargetRadio · AnswerField · ReasonField)   // solicitud
```

## 9. Riesgos y alerta temprana

Lienzo de la etapa 3, página 12. El riesgo se mide con revisiones inmutables (P × I, ASM-004); solo una persona confirma una
alerta (RN-EWS-001).

### S38 y S39 · Registro y matriz de riesgos

- **Ruta y lienzo:** `/projects/:projectId/risks/register` · hoja `Rsk-Registro` (interactiva) · F4. El tablero de
  riesgos (S62, con tendencia) llega en la etapa 3b.
- **Propósito:** ¿qué riesgos hay, de qué nivel y cuáles necesitan revisión? Elegir una celda del mapa de calor filtra la
  tabla.
- **Roles:** lectura para todos con READ sobre `social_risk`; SRM crea y evalúa; SMG decide el tratamiento y exporta.
- **Acciones:** primaria "Identificar riesgo"; secundarias filtrar por celda, vistas (Registro y matriz, Revisión vencida,
  Mis riesgos, Incidentes, Cerrados) y "Alerta temprana" con su conteo.
- **Disposición y componentes:** mapa de calor 5 × 5 con conteo por celda y color por nivel; tabla con riesgo, categoría,
  P × I, nivel, tendencia, estado y situación de revisión.
- **Datos y API:** genérica sobre `social_risk` con sus valores vigentes derivados; KPI-RSK-01 y KPI-RSK-02.
- **Estados:** un riesgo sin revisión no entra al mapa y se cuenta aparte; una celda elegida muestra "Quitar filtro de celda".
- **Permisos:** sin campos restringidos propios.
- **Responsive:** por debajo de `lg` el mapa pasa arriba a ancho completo y la tabla a tarjetas.
- **Relacionadas y navegación:** S40 por fila; S41; capa LYR-21.

```text
RiskRegisterPage
├── RiskViewTabs
├── RiskHeatmap (HeatCell × 25 · LevelLegend)
└── RiskTable (RiskRow · LevelBadge · TrendIcon · ReviewChip)
```

### S40 · Ficha de riesgo

- **Ruta y lienzo:** `/projects/:projectId/risks/:riskId` · hoja `Rsk-Ficha` · F4.
- **Propósito:** ver el nivel vigente, cómo llegó ahí y qué se hace para bajarlo.
- **Roles:** SRM evalúa, decide, agrega medidas y cierra; SMG decide el tratamiento y cierra; el resto lee.
- **Acciones:** primaria "Nueva revisión"; secundarias "Pasar a monitoreo", "Volver a mitigar", "Cerrar…", "+ Medida",
  "+ Impacto potencial" (S61).
- **Medidas de mitigación:** "+ Medida" abre un panel dentro de S40 con descripción, responsable, fecha prevista y
  `completed`; SRM las crea, cambia y borra por la API genérica, y decidir mitigar o volver a mitigar exige al menos una
  con responsable (`409 MEASURE_OWNER_REQUIRED`, RN-RSK-003). Una medida atrasada se marca en la lista.
- **Disposición y componentes:** encabezado con estado, nivel, P × I y tendencia, lugar, responsable y próxima revisión;
  camino de estados; gráfico de revisiones con su comentario; medidas de mitigación con responsable, fecha y avance;
  controles actuales, su suficiencia y fuentes de monitoreo; fuente del riesgo; a la derecha "Lo alimentan" (QCS y alertas),
  contexto (conflicto local, tema clave, grupos de interés, lugar) y el diálogo de revisión.
- **Datos y API:** `POST /api/sgspe/social-risks/{id}/assessments` (crea `risk_assessment`, aplica `assess` en la primera);
  `mitigation_measure` por la API genérica; transiciones de WF-F con motivo. AUT-11 si el nivel pasa a CRITICO.
- **Estados:** sin revisión residual, "Pasar a monitoreo" se explica bloqueado (RN-RSK-003); una revisión no se edita: se
  corrige con otra.
- **Permisos:** `mitigation_measure` la escribe solo SRM.
- **Responsive:** el gráfico pasa a una lista de revisiones; el panel lateral baja.
- **Relacionadas y navegación:** QCS de origen (S36), alerta (S41), incidente de origen, tareas de revisión (S47). Flujos 14 y
  15.

```text
RiskPage
├── RecordHeader (StateBadge · LevelBadge · TrendIcon · ReviewChip)
├── StatePath · TransitionBar
├── AssessmentHistoryChart · MitigationList (MeasureRow)
├── ControlsSection · SourceSection
└── SidePanel (FedByList · RiskContext · AssessmentDialog)
```

### S41 · Centro de alerta temprana

- **Ruta y lienzo:** `/projects/:projectId/risks/warnings` · hoja `Rsk-Alertas` (interactiva) · F4.
- **Propósito:** decidir en el triaje si la alerta es real, con las señales que la dispararon a la vista.
- **Roles:** SRM y SMG confirman y descartan (`CONFIRM_ALERT`); CRO lee, inicia el triaje y la respuesta; nunca la
  plataforma, una regla, la IA ni una cuenta de servicio (RN-EWS-001).
- **Acciones:** en triaje, "Confirmar" (con vínculo opcional al riesgo), "Descartar…" (motivo obligatorio) y "Vincular
  señal…", que guarda `early_warning_signal.early_warning` en una señal del mismo proyecto (S59); luego "Iniciar
  respuesta", "Resolver", "Cerrar".
- **Disposición y componentes:** encabezado con la regla que la creó; camino de estados; señales con fuente, fecha y texto;
  panel de decisión; regla y su umbral; nota de IA; capa del mapa.
- **Datos y API:** transiciones de WF-J; `early_warning_signal` y `early_warning_rule` (JOB-04 crea las alertas en
  `detected`); AUT-09 crea la tarea de triaje.
- **Estados:** sin `CONFIRM_ALERT` se muestra la razón en lugar de los botones (la hoja alterna "Gestión de riesgos" y
  "Relacionista").
- **Permisos:** la acción declarada `CONFIRM_ALERT` sobre `early_warning`.
- **Responsive:** las señales pasan a acordeón; la decisión queda fija al pie.
- **Relacionadas y navegación:** el riesgo vinculado (S40); capa LYR-22. Flujo 14.

```text
WarningCenterPage
├── WarningHeader · StatePath
├── SignalList (SignalCard)
├── TriageDecisionPanel (RiskLinkPicker · ReasonField)
└── RuleCard · AiNote
```

### Ficha de incidente

- **Ruta y lienzo:** `/projects/:projectId/incidents/:incidentId` · hoja `Rsk-Incidente` · F4.
- **Propósito:** registrar qué pasó, la respuesta inmediata y sus consecuencias, y llevarlo a una alerta o a un riesgo.
- **Roles:** CRO y SRM crean y transicionan; SMG actualiza; el resto lee.
- **Acciones:** primaria según el estado ("Cerrar incidente" en respuesta); secundarias "Identificar un riesgo desde este
  incidente" y "Guardar".
- **Disposición y componentes:** encabezado con tipo, fecha, lugar y quién reportó; camino de estados; qué pasó e
  involucrados; respuesta inmediata y consecuencias; a la derecha "Qué generó", "Abierto en el mismo lugar" (registros abiertos
  del mismo lugar, sin vínculo modelado) y evidencias.
- **Datos y API:** transiciones de WF-M; el riesgo nuevo guarda `origin_incident`; una queja puede apuntar al incidente (M2O
  `incident`).
- **Estados:** cerrar exige respuesta inmediata y consecuencias.
- **Permisos:** las fotos con personas son DATOS PERSONALES o CONFIDENCIAL (RF-DOC-003, RNF-PRIV-004).
- **Responsive:** como S40.
- **Relacionadas y navegación:** riesgo (S40), alerta (S41), compromisos del lugar (S29).

```text
IncidentPage
├── RecordHeader · StatePath
├── WhatHappenedSection · ResponseSection
└── SidePanel (GeneratedLinks · SamePlaceOpenRecords · EvidenceList)
```

### S58 · Menciones en medios

- **Ruta y lienzo:** `/projects/:projectId/risks/mentions` y `/projects/:projectId/risks/mentions/:id` · sin hoja propia
  (sigue la anatomía de la ficha de incidente) · F4.
- **Propósito:** registrar a mano lo que dicen los medios del proyecto, con su tono, para los indicadores y las reglas
  de alerta.
- **Roles:** CRO y SRM crean y cambian; el resto lee.
- **Acciones:** primaria "Registrar mención"; secundarias "Guardar" y "Registrar señal desde esta mención" (S59).
- **Disposición y componentes:** lista con fecha, medio, titular y tono; la ficha con medio (organización o texto),
  fecha de publicación, titular, resumen, enlace, tema, tono y "Informa sobre un compromiso cumplido" con el compromiso.
- **Datos y API:** genérica sobre `media_mention`; KPI-ENG-07 y KPI-ENG-08; la regla MENCIONES NEGATIVAS de JOB-04.
- **Estados:** el enlace se muestra como texto con su dominio y se abre en otra pestaña; el servidor nunca lo descarga
  (ASM-050).
- **Permisos:** sin campos restringidos propios.
- **Responsive:** la lista pasa a tarjetas con fecha, titular y tono.
- **Relacionadas y navegación:** el compromiso (S29), la señal (S59), el centro de alerta (S41).

```text
MediaMentionPage
├── MentionList (MentionRow · ToneBadge)
└── MentionForm (OutletField · HeadlineField · ToneField · CommitmentPicker · UrlField)
```

### S59 · Señales de alerta

- **Ruta y lienzo:** `/projects/:projectId/risks/signals` y `/projects/:projectId/risks/signals/:id` · sin hoja propia ·
  F4.
- **Propósito:** registrar a mano lo que puede anticipar un conflicto, con su fuente, para que una persona lo lleve a
  una alerta o una regla lo cuente.
- **Roles:** CRO y SRM crean y cambian; quien hace el triaje la vincula a la alerta desde S41.
- **Acciones:** primaria "Registrar señal"; secundarias "Guardar" y "Abrir la alerta" si tiene una.
- **Disposición y componentes:** lista con fecha, fuente, lugar y alerta; la ficha con fuente, fecha de recepción,
  descripción, cómo llegó, lugar y los vínculos a mención, incidente y alerta.
- **Datos y API:** genérica sobre `early_warning_signal`; la regla CONTEO DE SEÑALES de JOB-04; los vínculos del mismo
  proyecto (`409 PROJECT_MISMATCH`).
- **Estados:** una señal sin alerta se marca "Sin alerta".
- **Permisos:** sin campos restringidos propios; el texto no lleva nombres de personas (RQ-SEC-010).
- **Responsive:** la lista pasa a tarjetas.
- **Relacionadas y navegación:** la mención (S58), el incidente, la alerta (S41).

```text
SignalPage
├── SignalList (SignalRow · SourceBadge)
└── SignalForm (SourceField · DescriptionField · PlacePicker · MentionPicker · IncidentPicker)
```

### S60 · Reglas de alerta

- **Ruta y lienzo:** `/risks/rules` (las del tenant) y `/projects/:projectId/risks/rules` · sin hoja propia · F4.
- **Propósito:** configurar qué dispara una alerta: el tipo de regla, su umbral y su ventana.
- **Roles:** SRM crea, cambia, desactiva y borra; el resto lee.
- **Acciones:** primaria "Nueva regla"; secundarias "Desactivar" y "Borrar" (una regla con alertas no se borra: se
  desactiva).
- **Disposición y componentes:** lista con nombre, tipo, umbral, ventana, severidad, alcance (tenant o proyecto) y
  estado; el formulario explica, para el tipo elegido, qué cuenta, qué significa el umbral y qué ventana usa
  (`workflows.md`, «Tipos de regla de JOB-04»), y que una regla del proyecto reemplaza a la del tenant del mismo tipo.
- **Datos y API:** genérica sobre `early_warning_rule`; `threshold` entero y `window_days` de 1 a 365
  (`400 OUT_OF_RANGE`); `409 HAS_DEPENDENTS` al borrar una con alertas.
- **Estados:** sin CAMBIO DE POSTURA en el MVP (ASM-097); una regla desactivada no se evalúa.
- **Permisos:** solo SRM escribe.
- **Responsive:** la lista pasa a tarjetas; el formulario, a una columna.
- **Relacionadas y navegación:** el centro de alerta (S41) filtra por regla.

```text
WarningRulesPage
├── RuleList (RuleRow · ScopeBadge)
└── RuleForm (RuleKindField · ThresholdField · WindowField · SeverityField · KindExplanation)
```

### S61 · Impactos potenciales

- **Ruta y lienzo:** `/projects/:projectId/risks/impacts` y el panel "Impactos" de S40 · sin hoja propia · F4.
- **Propósito:** registrar qué actividad puede causar qué impacto, de qué tipo, naturaleza y signo, y a qué riesgo
  alimenta.
- **Roles:** SRM crea, cambia y borra; el resto lee.
- **Acciones:** primaria "+ Impacto potencial"; secundarias "Guardar" y "Borrar".
- **Disposición y componentes:** tabla con actividad, tipo, naturaleza, signo, descripción y riesgo.
- **Datos y API:** genérica sobre `potential_impact`; el riesgo del mismo proyecto.
- **Estados:** un impacto sin riesgo se marca "Sin riesgo vinculado".
- **Permisos:** solo SRM escribe.
- **Responsive:** la tabla pasa a tarjetas.
- **Relacionadas y navegación:** el riesgo (S40).

```text
PotentialImpactsPage
└── ImpactTable (ImpactRow · ImpactForm)
```

## 10. Programas sociales

Lienzo de la etapa 3, página 13. Los montos se muestran por moneda y nunca se suman entre monedas (ASM-063).

### S42 · Programas sociales

- **Ruta y lienzo:** `/projects/:projectId/programs` (programas y aportes en una vista) · hoja `Inv-Lista` · F5; tope desde F3.
- **Propósito:** ¿cuánto del tope de la campaña queda, cómo avanzan los programas y qué aportes están en curso?
- **Roles:** SIM crea y lleva los programas, y SMG conduce su evaluación, suspensión y cierre; SMG, CRO y SIM registran
  aportes; APR y SMG aprueban; el resto lee.
- **Acciones:** primaria "Nuevo aporte"; secundaria "Nuevo programa".
- **Disposición y componentes:** una sola vista a la vez en el `SelectorDeVista` (DD-27, U-35): «Programas» (por defecto,
  `/programs` sin `?view=`) y los aportes en «Aportes en curso», «Aportes terminados» y «Todos los aportes»
  (`?view=enCurso`, `terminados` y `todos`, que llevan al mismo lugar que antes). Cada vista trae sus cifras: los programas,
  el presupuesto ejecutado del año y los beneficiarios; los aportes, el tope de la campaña (comprometido frente al tope y
  ejecutado, con advertencia de otra moneda). Una cifra que la cuenta no lee dice «No disponible»; una zona sin datos no
  se dibuja. `TablaPriorizada` de programas (socio, estado, presupuesto del año, ejecutado, beneficiarios, indicadores con
  medición) o de aportes de la campaña (tipo, tema, quién recibe, estado, total de los ítems, origen).
- **Datos y API:** genérica sobre `social_program`, `program_budget`, `beneficiary_group` y `social_contribution`;
  KPI-INV-01, KPI-INV-02 y KPI-INV-04; `exploration_campaign.executed_social_amount` y `other_currency_items_count`.
- **Estados:** el tope se verifica al aprobar contra lo comprometido de compromisos y aportes de la campaña (ASM-110);
  lo ejecutado es otra cifra (ASM-063). Los programas no pertenecen a una campaña y no cuentan para el tope.
- **Permisos:** sin campos restringidos propios.
- **Responsive:** los indicadores se apilan; las tablas pasan a tarjetas.
- **Relacionadas y navegación:** S43, S44; solicitud de origen (S57); tablero de inversión (etapa 3b).

```text
InvestmentPage
├── BarraDeFiltros (SelectorDeVista: Programas · Aportes en curso · Aportes terminados · Todos los aportes)
├── CifrasDeLaVista (ProgramBudgetCard · BeneficiariesCard | CampaignCapCard)
└── ProgramTable | ContributionTable (TablaPriorizada)
```

### S43 · Ficha de programa

- **Ruta y lienzo:** `/projects/:projectId/programs/:programId` · hoja `Inv-Programa` · F5.
- **Propósito:** seguir presupuesto, indicadores, hitos y beneficiarios de un programa con su socio y su convenio.
- **Roles:** SIM crea y envía el programa y es el único dueño de sus datos (presupuesto y ejecutado, indicadores,
  mediciones, iniciativas, hitos y beneficiarios); APR aprueba (G1 y G2); SMG conduce la evaluación, la suspensión y el
  cierre, con RU sobre el programa y solo lectura de sus hijos (`security.md` §7.7).
- **Acciones:** primaria "Registrar medición"; secundarias "Pasar a monitoreo", "Suspender…" y las transiciones de WF-G del
  estado.
- **Disposición y componentes:** encabezado con estado, socio, convenio de sinergia y aprobación; camino de estados;
  presupuesto por año y moneda, con el ejecutado que SIM registra a mano (ASM-113) y los de otra moneda aparte;
  indicadores con línea base, meta y última medición con su evidencia; iniciativas e hitos; a la derecha grupos
  beneficiarios (meta y alcanzados), territorio y trazabilidad.
- **Datos y API:** genérica sobre `social_program`, `program_budget`, `program_indicator`, `program_measurement`
  (`appendOnly`), `program_milestone`, `social_initiative` y `beneficiary_group`.
- **Estados:** un indicador sin mediciones se marca; el total del programa (`total_budget`) suma solo los presupuestos
  en su moneda (ASM-113); con socio y la política activa, "Aprobar" y "Planificar" piden un convenio de sinergia vigente
  con el socio como parte (G3, ASM-115).
- **Permisos:** las mediciones no se editan ni se borran; un indicador con mediciones no se borra; SMG ve los datos del
  programa en solo lectura.
- **Responsive:** el panel lateral baja; las tablas pasan a tarjetas.
- **Relacionadas y navegación:** convenio (S33), evidencias (S45, S46), tareas (S47).

```text
ProgramPage
├── RecordHeader · StatePath
├── BudgetTable · IndicatorTable (MeasurementDialog) · InitiativeMilestoneList
└── SidePanel (BeneficiaryGroups · TerritoryMiniMap · TraceList)
```

### S44 · Aporte social

- **Ruta y lienzo:** `/projects/:projectId/contributions/:contributionId` · hoja `Inv-Aporte` · F5.
- **Propósito:** registrar, aprobar, adquirir y entregar un aporte puntual con los lineamientos y el tope a la vista.
- **Roles:** SMG, CRO y SIM registran el aporte y cargan sus ítems; APR y SMG aprueban (`approve`); SIM aprueba sin
  revisión interna si la política lo permite (`approve_direct`); SIM registra la adquisición; SIM y CRO registran la
  entrega.
- **Acciones:** en `requested`, "Agregar ítem", "Aprobar…" y "Rechazar…"; luego "Registrar adquisición", "Registrar
  entrega" (una vez), "Subir acta de recepción firmada", "Cerrar" y "Anular…"; todas las transiciones con motivo.
- **Disposición y componentes:** encabezado con estado, tipo, tema, campaña y quién registró; camino de estados; "Para
  qué y para quién" (propósito, ocasión o festividad, quién recibe, localidad, condiciones, beneficiarios); ítems con su
  tipo (BIEN, SERVICIO, DINERO o MANO DE OBRA) y el total que se aprobará, editables solo en `requested`; la entrega con
  sus requisitos (conformidad, quiénes la dieron, acta de recepción, foto y comprobante); a la derecha "Antes de
  aprobar" y trazabilidad.
- **"Antes de aprobar":** `POST /api/sgspe/transition-checks` lista P1 a P9 con su estado: el tope de la campaña (P1,
  con lo comprometido y la advertencia de otra moneda), cada lineamiento incumplido (`PAID_LABOR`, `MONETARY_DONATION`,
  `PRIORITY_NOT_MET`) o la advertencia `PRIORITIES_NOT_CONFIGURED`, la sinergia (P6, `SYNERGY_AGREEMENT_REQUIRED`) y
  los ítems y el tipo de aporte (P9, `ITEMS_REQUIRED`). Si algún lineamiento se incumple, pide
  `guideline_deviation_justification`, que los cubre a todos; la sinergia no se justifica.
- **Datos y API:** genérica sobre `social_contribution`, `contribution_item` y `receipt`;
  `POST /api/sgspe/social-contributions/{id}/deliveries` captura la única entrega en `procured`, con sus evidencias, y
  luego `register_delivery` (P8). El "Acta de recepción" no se emite mientras UPS-23 siga abierta (ASM-116): "Subir acta
  de recepción firmada" la sube como evidencia ACTA DE RECEPCION vinculada a la entrega, con clasificación DATOS
  PERSONALES por defecto.
- **Estados:** si supera un tope que bloquea, "Aprobar" solo aparece para SMG (`APPROVE_OVER_CAP`, ASM-119); quien
  registró el aporte o aceptó la solicitud que lo creó no lo aprueba (P5); fuera de `requested`, los ítems y los
  términos aprobados son de solo lectura (`ITEMS_FROZEN`, `CONTRIBUTION_FROZEN`, ASM-110); "Registrar entrega" se
  ofrece una vez, solo en `procured`, y después muestra la entrega, que no cambia desde `delivered` (ASM-111). Los
  comprobantes (`receipt`) se cargan antes de "Registrar entrega": tampoco se crean, cambian ni borran desde `delivered`
  ni en `closed`, `rejected` o `cancelled` (`409 DELIVERY_FROZEN` sobre `social_contribution`).
- **Permisos:** las fotos de la entrega con personas y el acta firmada son DATOS PERSONALES; `recipients` y
  `conformity_by` son restringidos (`security.md` §7.9).
- **Responsive:** "Antes de aprobar" sube antes de los ítems en teléfono.
- **Relacionadas y navegación:** solicitud de origen (S57), bandeja de aprobaciones (S48); al cerrar, la solicitud se
  cierra sola (ASM-068, ASM-117).

```text
ContributionPage
├── RecordHeader · StatePath
├── PurposeSection · ItemTable (ItemKindSelect · TotalToApprove)
├── DeliverySection (DeliveryRequirements · SignedReceiptUpload)
└── SidePanel (PolicyChecklist P1–P9 · JustificationField · TraceList)
```

## 11. Documentos, tareas y aprobaciones

Lienzo de la etapa 3, página 14. Las secciones globales Documentos y Tareas usan la navegación de todos los proyectos;
el proyecto viaja como filtro.

### S45 · Biblioteca de documentos

- **Ruta y lienzo:** `/documents` y `/projects/:projectId/documents` · hoja `Doc-Biblioteca` · F3.
- **Propósito:** encontrar una evidencia por lo que sustenta, su tipo o su clasificación.
- **Roles:** todos los que leen `evidence`; suben los que tienen CRU.
- **Acciones:** primaria "Subir evidencia"; secundarias buscar y filtrar (tipo, clasificación, registro que sustenta, fecha,
  fotos con personas sin consentimiento).
- **Disposición y componentes:** buscador, filtros, tabla con evidencia (archivo, tamaño, versión), tipo, clasificación,
  registros que sustenta como etiquetas, fecha y quién subió.
- **Datos y API:** genérica sobre `evidence` con sus M2M `evidence_<objeto>`; `POST /api/sgspe/evidence` para subir (IK).
- **Estados:** un archivo no se edita ni se borra: se reemplaza con una versión nueva (`replaces`).
- **Permisos:** CONFIDENCIAL y DATOS PERSONALES se descargan solo con `DOWNLOAD_RESTRICTED` (RF-DOC-003).
- **Responsive:** la tabla pasa a tarjetas; los filtros, a un panel.
- **Relacionadas y navegación:** S46 por fila; cada etiqueta abre el registro sustentado.

```text
DocumentsPage
├── SearchField · FilterBar
└── EvidenceTable (EvidenceRow · ClassificationBadge · LinkTags)
```

### S46 · Ficha de evidencia

- **Ruta y lienzo:** `/projects/:projectId/documents/:documentId` · hoja `Doc-Evidencia` · F3.
- **Propósito:** ver qué sustenta una evidencia, quién puede descargarla y si puede salir en un reporte externo.
- **Roles:** como S45; `EDIT_ANY` para cambiar o vincular la de otra persona.
- **Acciones:** primaria "Descargar"; secundarias "Subir versión nueva", "Vincular a un registro…", "Desvincular…",
  "cambiar clasificación…", "Registrar consentimiento".
- **Disposición y componentes:** vista previa (rostros difuminados si hay personas), datos EXIF, huella SHA-256; "Sustenta";
  versiones; a la derecha clasificación y privacidad, "Así la ve el equipo de análisis" (sin `DOWNLOAD_RESTRICTED`) y datos
  descriptivos.
- **Datos y API:** `GET /api/sgspe/evidence/{id}/content` (`ETag` = SHA-256), `PATCH /api/sgspe/evidence/{id}` (IM; CR si
  cambia la clasificación), `POST` y `DELETE` de vínculos (CR si el destino es `requiresReason`).
- **Estados:** bajar la clasificación de una restringida exige `DOWNLOAD_RESTRICTED` (ASM-071); una foto con personas sin
  consentimiento no sale en una salida EXTERNO (RNF-PRIV-004).
- **Permisos:** sin `DOWNLOAD_RESTRICTED`, la persona ve título, tipo, fecha y vínculos, sin vista previa ni descarga.
- **Responsive:** el panel lateral baja debajo de la vista previa.
- **Relacionadas y navegación:** los registros que sustenta; S45.

```text
EvidencePage
├── RecordHeader (ClassificationBadge)
├── EvidencePreview (RestrictedPreview) · LinkList · VersionList
└── SidePanel (PrivacySection · RoleViewNote · DescriptiveFields)
```

### S47 · Mis tareas

- **Ruta y lienzo:** `/tasks` · hoja `Tar-Mis` (interactiva: lista, tablero y calendario) · F3.
- **Propósito:** ¿qué tengo que hacer hoy y qué se me venció?
- **Roles:** todos los roles operativos (WF-N); APR actualiza; ANL y RDO leen.
- **Acciones:** por fila, las transiciones de WF-N ("Empezar", "Hecha", "Bloquear", "Desbloquear"); "Tomar la tarea" en
  "Para tomar".
- **Disposición y componentes:** cambio de vista; conteos de vencidas, por vencer y abiertas; lista con origen, prioridad,
  vencimiento con su situación, estado y acción; tablero por estado; calendario del mes por vencimiento; "Para tomar" con las
  tareas de escalamiento sin asignado de los roles de la persona.
- **Datos y API:** genérica sobre `task` filtrada por `assignee_user_id`; transiciones de WF-N; tomar una tarea es UPDATE de
  `assignee_user_id` (IM).
- **Estados:** una tarea vencida sigue en su estado; en el tablero, arrastrar a una columna sin transición devuelve la
  tarjeta; con teclado, el menú de la tarjeta ofrece las mismas transiciones.
- **Permisos:** solo la persona asignada y los roles con UPDATE cambian la tarea.
- **Responsive:** en teléfono solo la lista; es la entrada del móvil de campo (S54, etapa 3b).
- **Relacionadas y navegación:** cada tarea abre su origen; S48.

```text
MyTasksPage
├── ViewTabs (Lista · Tablero · Calendario)
├── DeadlineCounters
├── TaskTable | TaskBoard (TaskColumn × 4) | TaskCalendar (DayCell)
└── ClaimableTasks
```

### S48 · Aprobaciones y escalamientos

- **Ruta y lienzo:** `/tasks/approvals` · hoja `Tar-Aprobaciones` · F3; QCS F4; programas y aportes F5.
- **Propósito:** ver juntas las decisiones que esperan a mi rol, de cualquier objeto, y lo que está escalando.
- **Roles:** APR, SMG y PMG según el objeto; el resto ve los escalamientos de sus registros.
- **Acciones:** elegir una aprobación de la lista y decidirla en el **detalle al lado** (P-05, flujo F-E): un compromiso, un
  aporte o un programa se deciden ahí, con el diálogo de transición (motivo, reglas en seco,
  `If-Match` y el `412` con `AvisoDeConflicto`) y solo con las decisiones que el servidor permite. La queja grave, el acta
  y la revisión de un proyecto se eligen aquí pero se deciden en su ficha («Revisar»). Tras decidir, la bandeja relee sus
  avisos.
- **Disposición y componentes:** bandejas "Por aprobar", "Escalamientos del proyecto" y "Decididas por mí"; a la izquierda,
  la lista por antigüedad (tarjetas con registro, qué se decide, monto en su moneda, espera y "¿Puede decidir?" con la
  razón); a la derecha, lo elegido (`?selected=objeto:id`): un aporte muestra sus ítems con el total que se aprobará, el
  tope de la campaña y «Antes de aprobar»; un compromiso y un programa, su monto. Tarjetas de escalamiento con niveles 0, 1
  y 2.
- **Datos y API:** las aprobaciones son transiciones, no objetos (`domain-model.md` §6.13): la bandeja consulta los registros
  en estados de aprobación cuyas transiciones el rol tiene, más los avisos `approval_pending` (desde F5, también los de
  `submit` de WF-G y los del alta de un aporte, que llegan antes de sus ítems); los escalamientos vienen de
  JOB-01 (avisos y tareas por nivel, ASM-030, ASM-070).
- **Estados:** la razón por la que no se puede decidir (tope, nivel superior, segregación) se muestra siempre.
- **Permisos:** se comprueban otra vez al confirmar (`transition-checks`).
- **Responsive:** en el teléfono, la lista o el detalle a pantalla completa, con «Volver a la lista»; es parte del móvil
  (aprobaciones en teléfono).
- **Relacionadas y navegación:** S31, S36, S43, S44, S47.

```text
ApprovalsPage
├── InboxTabs
├── ListaDeAprobaciones (tarjeta × n · CanDecideCell)
├── DetalleDeAprobacion (?selected=: ítems · tope · «Antes de aprobar» · TransitionDialog)
└── EscalationCards (EscalationLevels)
```

## 12. Tableros

Lienzo de la etapa 3, página 16. Los tableros siguen `dashboards.md` §5: la fila de filtros gobierna todos los gráficos,
cada gráfico tiene su tabla "Ver datos" (que se copia, no se descarga) y un KPI que la persona no puede calcular dice "no
disponible", nunca cero (RQ-DSH-032). Las cifras salen de `GET /api/sgspe/dashboards/{dashboard}` y
`GET /api/sgspe/dashboards/indicators/{code}`, las mismas que usan las fichas (RQ-DSH-034).

### S51 · Revisión ejecutiva de 5 minutos

- **Ruta y lienzo:** `/dashboards/executive` (DD-19); el inicio «Mi trabajo» (S02) la resume y la enlaza (ASM-074) · hoja
  `Tab-Ejecutivo` · F8; compacta desde el rediseño (P-07).
- **Propósito:** ¿qué cambió, qué es crítico, dónde y quién lo atiende? Leerlo en cinco minutos.
- **Roles:** el ejecutivo (`READ_ONLY` o `ANALYST`); también SMG y PMG como vista. Lectura.
- **Acciones:** primaria abrir lo que necesita atención; secundarias filtros de periodo y proyectos, "Monitoreo de la Tabla
  9.5" y el asistente (si el
  ejecutivo es `ANALYST`, que puede registrar `ai_interaction`; ASM-073, ASM-130).
- **Disposición y componentes:** abre con las cifras: bajo el título, su periodo (sin fechas, el mes en curso de Lima) y su
  fecha de cálculo, como lapso mientras es de hoy y con aviso si tiene más de 24 horas (U-11, U-23); los filtros de periodo
  y proyectos, plegados. Tiles de KPI-EXE-01, KPI-COM-02, KPI-COM-03, KPI-GRV-02, KPI-RSK-01, KPI-EXE-02 y KPI-ENG-01, las
  disponibles primero y las no disponibles al final (la de postura, siempre "no disponible"); «Qué es crítico y dónde se
  atiende», una tabla corta (regla fija, no IA) cuyo asunto lleva a la lista donde se atiende, con su proyecto y su cifra:
  dice dónde y no quién, porque el contrato del tablero no trae a la persona responsable; medidor de KPI-INV-03 por moneda;
  KPI-EXE-03 en tres gráficos pequeños, uno por medida, en color sólido y sin trama (DD-15); tabla por proyecto con una
  columna por medida.
- **Datos y API:** tablero `executive` (`dashboards.md` §5.1, `api.md` §4.11): las tiles, KPI-INV-03 por moneda,
  KPI-EXE-03 y `byProject[]`.
- **Estados:** sin proyectos asignados, vacío con enlace a Proyectos; un KPI sin permiso, "no disponible"; más de 25
  proyectos, `400 TOO_MANY_RECORDS`: «acote los filtros».
- **Permisos:** la postura (`stance`) no llega a los roles que no la leen (`dashboards.md` §8).
- **Responsive:** las cifras primero; tiles en dos columnas; la tabla pasa a tarjetas por proyecto.
- **Relacionadas y navegación:** cada cifra abre su lista filtrada; S27, S34, S62, monitoreo.

```text
ExecutiveReviewPage
├── EncabezadoDePagina (periodo · fecha de cálculo · filtros plegados)
├── KpiRow (KpiCard · NotAvailable al final)
├── QueEsCritico (tabla corta)
├── CurrencyGauge × moneda · TrendSparklines (× 3, sin trama)
└── ProjectTable (una columna por medida)
```

### Monitoreo de la Tabla 9.5

- **Ruta y lienzo:** `/dashboards/monitoring` (sección Dashboard) · hoja `Tab-Monitoreo` (interactiva: periodo y rol) · F8.
- **Propósito:** ¿cómo van los 13 indicadores del plan de relaciones comunitarias en el periodo?
- **Roles:** lectura para quien lee al menos uno de sus KPI.
- **Acciones:** elegir periodo; abrir el detalle de un indicador; «Cerrar periodo» para quien tiene CREATE sobre
  `indicator_measurement`, un diálogo con el periodo y el proyecto o todo el tenant que explica el `409
  INDICATOR_NOT_CLOSABLE` con su razón, el `400 PERIOD_NOT_ENDED` y el `409 MEASUREMENT_EXISTS` y el `403
  ACTION_REQUIRED` (todo el tenant sin ser `ADMIN`, opción que solo ve el `ADMIN`; `READ_ALL_PROJECTS` no basta); «Catálogo de
  indicadores», la lista de `indicator` con edición de `target_value` y `frequency` para quien tiene UPDATE.
- **Disposición y componentes:** aviso de periodo (cerrado: cifra oficial; en curso: cifra viva); tabla con número, indicador,
  valor, meta y su origen (`targetSource`), cifra oficial marcada cuando el pedido coincide con la medición, desglose,
  medio de verificación y tipo de entregable (`dashboards.md` §4.9 y §5.5).
- **Datos y API:** tablero `monitoring` (cada fila con `official`); `POST
  /api/sgspe/indicators/{id}/measurements` para cerrar; `indicator` por la API genérica (`dashboards.md` §10).
- **Estados:** #2 y #3 leen la postura: "no disponible" a quien no la lee, también con el periodo cerrado.
- **Permisos:** como S51.
- **Responsive:** la tabla pasa a tarjetas por indicador.
- **Relacionadas y navegación:** S51; entregables (S50) que usan la tabla.

```text
MonitoringPage
├── PeriodTabs · PeriodBanner
└── MonitoringTable (IndicatorRow · NotAvailable)
```

### S27 · Tablero de compromisos

- **Ruta y lienzo:** `/commitments/dashboard` · hoja `Tab-Compromisos` · F3, F8.
- **Propósito:** ¿qué debemos, a quién y desde cuándo?
- **Roles:** lectura para quien lee `commitment`.
- **Acciones:** filtros de periodo, campaña y responsable; abrir pendientes.
- **Disposición y componentes:** tiles KPI-COM-01 a KPI-COM-06 (vencidos con desglose por días); barras KPI-COM-07; tile
  KPI-COM-09 frente al plazo vigente y al original; barras por moneda KPI-COM-10; barras KPI-COM-08; línea KPI-COM-11; tabla de
  pendientes.
- **Datos y API:** tablero `commitments` (`dashboards.md` §5.6).
- **Estados:** la historia anterior a la puesta en marcha se marca como aproximada.
- **Permisos:** como S51.
- **Responsive:** tiles en dos columnas; gráficos uno debajo de otro.
- **Relacionadas y navegación:** S28, S29.

```text
CommitmentsDashboard
├── FilterBar · KpiRow
├── BreakdownBars · OnTimeTile · AmountByCurrencyBars · FormalByTopicBars · OverdueHistoryLine
└── PendingTable
```

### S34 · Tablero de QCS

- **Ruta y lienzo:** `/attention/dashboard` · hoja `Tab-QCS` · F4, F8.
- **Propósito:** ¿qué nos piden y reclaman, y cómo respondemos?
- **Roles:** lectura para quien lee los tres objetos de BC-07.
- **Acciones:** filtros; "Ir a la lista" (S35); abrir el tablero territorial.
- **Disposición y componentes:** tabla de flujo por tipo (KPI-GRV-01 a KPI-GRV-06); tile KPI-GRV-07; tiempos KPI-GRV-10;
  barras KPI-GRV-08; dos líneas en un eje KPI-GRV-09; mapa de calor de concentración (LYR-20).
- **Datos y API:** tablero `attention` (`dashboards.md` §5.7).
- **Estados:** cada planteamiento cuenta una vez, aunque se haya reclasificado.
- **Permisos:** como S51; la persona que plantea nunca aparece.
- **Responsive:** la tabla de flujo pasa a una tarjeta por tipo.
- **Relacionadas y navegación:** S35, tablero territorial.

```text
AttentionDashboard
├── FilterBar · FlowByTypeTable
├── ResolvedTile · TimesTile · CategoryBars
└── TrendLines · ConcentrationMiniMap
```

### S62 · Tablero de riesgos

- **Ruta y lienzo:** `/risks` · hoja `Tab-Riesgos` · F4, F8. Es la vista "Tablero" de Riesgos: la sección aterriza en el
  registro (S38 y S39, ux-architecture §7.1).
- **Propósito:** ¿qué puede impedir el proyecto y qué hacemos?
- **Roles:** lectura para quien lee `social_risk`.
- **Acciones:** "Ir al registro" (S38 y S39); abrir medidas atrasadas.
- **Disposición y componentes:** mapa de calor 5 × 5 con conteo y nivel (KPI-RSK-02); tiles KPI-RSK-01, KPI-RSK-05 y revisiones
  vencidas; barras apiladas por semana KPI-RSK-04; distribución por territorio KPI-RSK-03; medidas atrasadas.
- **Datos y API:** tablero `risks` (`dashboards.md` §5.8).
- **Estados:** un riesgo sin revisión no entra al mapa y se dice cuántos hay.
- **Permisos:** como S51.
- **Responsive:** el mapa de calor arriba a ancho completo.
- **Relacionadas y navegación:** el registro y matriz (S38 y S39), S40.

```text
RisksDashboard
├── RiskHeatmap · KpiRow
├── WeeklyLevelBars · TerritoryDistribution
└── OverdueMeasuresList
```

### Tablero territorial

- **Ruta y lienzo:** `/territory/dashboard` (S12 en modo tablero; exige `?project=`) · hoja `Tab-Territorial` · F6, F8.
- **Propósito:** ¿qué ocurre en cada lugar?
- **Roles:** lectura para quien lee el territorio del proyecto.
- **Acciones:** elegir un proyecto y, si se quiere, una localidad o comunidad; capas; "Capturar PNG".
- **Disposición y componentes:** mapa con capas y paleta compartida (RQ-GIS-014); ficha contextual con KPI-TER-01 (solo
  con localidad o comunidad); tiles KPI-TER-02, KPI-TER-03 y KPI-TER-04.
- **Datos y API:** tablero `territory` (`dashboards.md` §5.9): `GET /api/sgspe/dashboards/territory?project=` exige el
  proyecto (`400 PROJECT_REQUIRED`) y suma KPI-TER-01 con `locality` o `community`; las cifras de la ficha salen de
  `sgspe.dashboard`, no del mapa.
- **Estados:** sin proyecto, la pantalla pide elegirlo y no consulta; sin lugar, los tiles de KPI-TER-02 a 04 sin ficha
  (el tablero omite KPI-TER-01 sin lugar, y solo `GET …/indicators/KPI-TER-01` responde `TERRITORY_REQUIRED`); la línea
  de posturas de la ficha dice "no disponible" a quien no lee `stance`.
- **Permisos:** sin puntos de personas: los grupos de interés se agregan por lugar (RQ-GIS-015).
- **Responsive:** mapa a pantalla completa con la ficha como hoja inferior.
- **Relacionadas y navegación:** S13, S14, S12 (el mapa de `/territory`) y S63; las cifras abren listas filtradas por
  el lugar.

```text
TerritoryDashboard
├── MapLibreLazy (LayerPanel · Legend · PngCapture)
├── ContextualCard (KpiTile × 6)
└── TerritoryStats
```

### Tablero de inversión social

- **Ruta y lienzo:** `/programs/dashboard` · hoja `Tab-Inversion` · F5, F8.
- **Propósito:** ¿cuánto invertimos frente a lo previsto y a quién llega?
- **Roles:** lectura para quien lee BC-10.
- **Acciones:** "Ir a programas y aportes" (S42).
- **Disposición y componentes:** un medidor por campaña frente a su tope (KPI-INV-01), con la marca de lo comprometido
  y la de lo ejecutado y la advertencia de otra moneda; barras por programa y año (KPI-INV-02); consolidado por moneda
  y fuente (KPI-INV-03); beneficiarios en tres bloques que no se suman (KPI-INV-04).
- **Datos y API:** tablero `investment` (`dashboards.md` §5.10), con los filtros de proyecto, campaña, programa, año,
  periodo y localidad; montos según ASM-063 y ASM-113.
- **Estados:** un programa por aprobar no muestra ejecución.
- **Permisos:** como S51.
- **Responsive:** medidores apilados.
- **Relacionadas y navegación:** S42, S43, S44.

```text
InvestmentDashboard
├── CampaignGauges (CampaignGauge × campaña)
├── ProgramBudgetBars · ConsolidatedByCurrency
└── BeneficiariesTable
```

## 13. Capa de IA

Lienzo de la etapa 3, página 17. La IA propone y una persona decide (RQ-AI-001, RQ-AI-003); toda salida de IA lleva la marca de
procedencia de `design-system.md` §4.4 y se muestra como texto, nunca como HTML (RQ-AI-011).

### S05 · Panel del asistente

- **Ruta y lienzo:** panel lateral global (barra superior) y contextual (desde una ficha) · hoja `IA-Panel` (interactiva:
  rol y estado de la organización) · F7.
- **Propósito:** preguntar a los datos o a los documentos sin salir de lo que se está haciendo.
- **Roles:** "Preguntar a los datos" (AI-05) a los roles que pueden registrar `ai_interaction` (`ai-architecture.md` §8,
  ASM-130); "Preguntar a los documentos" (AI-06) a esos roles si además leen los nombres de `person`.
- **Acciones:** "Preguntar"; cambiar de modo; abrir un registro o una fuente citada; cerrar.
- **Disposición y componentes:** encabezado con el chip de contexto (proyecto y territorio, ASM-075); pestañas de modo;
  conversación con la pregunta y la respuesta (`AiAnswer`) con enlaces y "Cómo lo obtuvo"; campo de pregunta con la nota de
  solo lectura.
- **Datos y API:** `GET /api/sgspe/ai/status`; `POST /api/sgspe/ai/assistant/questions` (AI-05);
  `POST /api/sgspe/ai/knowledge/questions` (AI-06). Cada pregunta queda en `ai_interaction`.
- **Estados:** organización sin IA (lo dice, y la bandeja sigue funcionando); "no disponible para tu rol" con la razón; sin
  proveedor o con el proveedor caído (`503 AI_UNAVAILABLE`, `provider`) con reintento.
- **Permisos:** el asistente lee como la persona; nunca escribe.
- **Responsive:** en teléfono, a pantalla completa.
- **Relacionadas y navegación:** S52; los enlaces de la respuesta abren la ficha con el contexto. Flujo 18.

```text
AssistantPanel
├── PanelHeader (ContextChip · Close)
├── ModeTabs (Datos · Documentos)
├── Conversation (UserBubble · AiAnswer (RecordLinks · ToolSteps) | UnavailableNotice)
└── AskForm
```

### Respuesta con citas (AI-06)

- **Ruta y lienzo:** dentro del panel S05, modo documentos · hoja `IA-Citas` · F7.
- **Propósito:** responder desde actas, convenios, informes y evidencias con la fuente de cada afirmación.
- **Roles:** como S05, modo documentos.
- **Acciones:** abrir cada fuente; reintentar si el proveedor no responde.
- **Disposición y componentes:** respuesta con llamadas numeradas; lista de fuentes con título, tipo, clasificación, cita
  literal y "Abrir el documento".
- **Datos y API:** recuperación filtrada por tenant, proyecto y clasificación descargable (`ai-architecture.md` §13.3).
- **Estados:** sin fuentes, "No encontré documentos que respondan esto" (nunca responde sin fuente); proveedor no disponible.
- **Permisos:** solo documentos que la persona puede descargar (RF-DOC-003); nombres re-identificados solo para quien los lee.
- **Responsive:** fuentes en acordeón.
- **Relacionadas y navegación:** S46.

```text
AiAnswer (variant="cited")
├── AnswerText (CitationRef)
└── SourceList (SourceCard · QuoteBlock)
```

### S52 · Bandeja de revisión de IA

- **Ruta y lienzo:** `/projects/:projectId/ai-review` · hoja `IA-Bandeja` · F7 (candidatos humanos desde F3).
- **Propósito:** decidir sobre lo que la IA propuso en el proyecto: aceptar, corregir, rechazar o marcar duplicado.
- **Roles:** los de la transición hacia `accepted` de WF-K más los permisos del comando destino (`ai-architecture.md` §4.2).
- **Acciones:** por tarjeta, "Aceptar…" (con corrección y motivo donde el destino lo pide), "Rechazar…", "marcar duplicado…",
  "Comparar lado a lado".
- **Disposición y componentes:** filtros por tipo y origen; tarjetas `AiSuggestionCard` ordenadas por confianza con tipo,
  confianza, fuente, cita, modelo y versión de la instrucción; reglas de la bandeja.
- **Datos y API:** `commitment_candidate` y `ai_suggestion` en `proposed`; `POST /api/sgspe/commitment-candidates/{id}/accept`,
  `POST /api/sgspe/commitment-candidates/{id}/duplicate`, `POST /api/sgspe/ai-suggestions/{id}/accept`, transición de WF-K a
  `rejected` (CR).
- **Estados:** sin propuestas, vacío; una propuesta cuyo destino la persona no lee no aparece.
- **Permisos:** la cita y el enlace a la fuente solo si la persona puede abrirla; si no, "fuente restringida".
- **Responsive:** tarjetas a ancho completo.
- **Relacionadas y navegación:** el candidato aceptado abre S30; el duplicado, la comparación.

```text
AiReviewPage
├── FilterBar
├── SuggestionList (AiSuggestionCard · CandidateCard)
└── ReviewRules
```

### Posible duplicado (AI-07)

- **Ruta y lienzo:** `/projects/:projectId/ai-review/:suggestionId` · hoja `IA-Duplicados` · F7.
- **Propósito:** decidir si dos registros son el mismo planteamiento, con los campos lado a lado.
- **Roles:** GVM para QCS (UPDATE con motivo); los roles de cada objeto para los demás pares.
- **Acciones:** "Es duplicado" o "No es duplicado", con respuesta opcional (en QCS) y motivo; "Confirmar".
- **Disposición y componentes:** tabla comparativa con las coincidencias marcadas; decisión; "Qué pasa al confirmar".
- **Datos y API:** `POST /api/sgspe/ai-suggestions/{id}/accept`; en una queja escribe `duplicate_of` y, solo si trae respuesta, la rechaza con ella por WF-E1 en la misma transacción
  (pide el rol del rechazo; `409 STATE_MISMATCH` sobre `attributes.response` si ya no se puede rechazar); en `person`, `stakeholder`, `commitment`, `inquiry` y `stakeholder_request` solo registra la
  decisión (`ai-architecture.md` §4.2).
- **Estados:** si algo falla, nada cambia.
- **Permisos:** la persona que plantea sigue restringida.
- **Responsive:** la tabla pasa a dos tarjetas apiladas.
- **Relacionadas y navegación:** S36 o S57, S52.

```text
DuplicateReviewPage
├── ComparisonTable (MatchBadge)
└── DecisionForm (DecisionRadio · ResponseField · ReasonField) · OutcomeNote
```

### Resumen ejecutivo y borrador de sección (AI-04)

- **Ruta y lienzo:** tarjeta en S08 (Resumen del proyecto) y en el editor de secciones de S50 · hoja `IA-Resumen` · F7 (resumen),
  F8 (sección).
- **Propósito:** partir de un borrador redactado con las cifras del periodo.
- **Roles:** quien lee el proyecto para el resumen; quien tiene UPDATE sobre `deliverable_section` para usar el borrador.
- **Acciones:** resumen: "Copiar", "Usar en un entregable…", "Generar de nuevo". Sección: "Usar y editar…" (motivo),
  "Descartar…".
- **Disposición y componentes:** `AiSummary` con fecha de los datos y lista de fuentes de cifras; en la sección, contenido
  actual y la propuesta con el motivo.
- **Datos y API:** `POST /api/sgspe/ai/runs` (AI-04); RESUMEN EJECUTIVO no se acepta mientras solo informa; BORRADOR DE SECCION
  escribe `content` con `content_origin` ASISTIDO POR IA (RQ-AI-012).
- **Estados:** sin IA, la tarjeta no aparece; generación en curso con su estado.
- **Permisos:** el resumen solo cuenta lo que la persona lee; no menciona posturas.
- **Responsive:** la tarjeta va primero en el Resumen.
- **Relacionadas y navegación:** S08, S50.

```text
AiSummary (variant="executive") · SectionDraftCard (ReasonField)
```

## 14. Inicio y transversales

Lienzo de la etapa 3, página 18.

### S01 · Login

- **Ruta y lienzo:** `/login` · hoja `Ini-Login` · F1.
- **Propósito:** ingresar con correo y contraseña al portal y a `/admin` con un mismo ingreso.
- **Roles:** todos.
- **Acciones:** "Ingresar"; cambiar idioma (Español · English · Runasimi; ASM-084).
- **Disposición y componentes:** columna de presentación y formulario; estados de error.
- **Datos y API:** `POST /api/auth/login` → token en `sgspe.token` (`architecture.md` §9.1); `GET /api/auth/me` y
  `/api/auth/me/permissions` para la navegación.
- **Estados:** credenciales incorrectas (sin decir cuál falló); demasiados intentos (`429` del proxy, RQ-SEC-015); sesión
  vencida (un `401` vuelve aquí y, al ingresar, a la ruta donde estaba).
- **Permisos:** sin recuperación de contraseña propia: la restablece la administración en `/admin`.
- **Idioma:** antes de ingresar sale del navegador y se cambia con los enlaces; al ingresar manda la preferencia de la cuenta.
  Lienzo de la etapa 3, página 23.
- **Responsive:** en teléfono, solo el formulario.
- **Relacionadas y navegación:** S02.

```text
LoginPage
├── BrandPanel
└── LoginForm (EmailField · PasswordField · ErrorAlert · LanguageSwitch)
```

### S02 · Inicio «Mi trabajo»

- **Ruta y lienzo:** `/` · hoja `Ini-Inicio` (interactiva: relacionista, gerencia social, aprobación) · F1 bienvenida; F8
  completo; rediseño P-01 (DD-01, DD-26).
- **Propósito:** contestar, apenas se ingresa, qué le toca hoy a la persona y qué espera su decisión.
- **Roles:** todos, con el mismo inicio: cada bloque se ofrece por lo que la cuenta lee y puede hacer, nunca por el nombre
  del rol (`ux-architecture.md` §5 y §7.6). Quien lee cifras ejecutivas ve además la visión ejecutiva: las de S51 que lee,
  con su fecha de cálculo y el enlace a S51 (ASM-074, DD-19).
- **Acciones:** abrir cada elemento; los atajos de creación que la cuenta tiene; a quien lee los indicadores, «Monitoreo» y
  «Catálogo de indicadores» en la cabecera.
- **Disposición y componentes:** cabecera «Inicio» con el saludo; luego, en este orden y solo si tienen algo legible:
  «Lo que me toca hoy» (lo abierto a su cargo, vencido primero, cinco a la vista y el resto desplegable; «Para tomar en …»
  con las tareas sin asignar de sus proyectos), «Esperan su decisión» (aprobaciones, alertas por triar y avisos que piden
  acción, en `ListaPriorizada`), «Agenda de la semana» (visitas que lidera e interacciones programadas en sus proyectos),
  «Registrar» (atajos), «Mis proyectos» (tarjetas con estado, jefatura y lo que pide acción) y «Visión ejecutiva». Los
  datos de la cuenta viven en el menú de la cuenta.
- **Datos y API:** `task`, `commitment` y las tres QCS a su cargo; los avisos de wasichai-notifications (aprobaciones y los
  que piden acción); `early_warning` por triar si la cuenta puede confirmarlas; `field_visit` y `engagement` de los siete
  días; sus `project_assignment` vigentes y los conteos de lo vencido por proyecto; el tablero `executive` si el catálogo
  lo ofrece.
- **Estados:** un bloque sin nada legible no se dibuja, nunca un «No disponible»; una fecha inválida no tumba el inicio. Sin
  ningún bloque, la bienvenida de F1 con las secciones que la cuenta puede abrir.
- **Permisos:** solo lo que la API devuelve a quien mira; ningún bloque cuenta lo que no lee (ADR-0029).
- **Responsive:** los bloques en una columna; en el teléfono, Inicio es el primer destino de la barra inferior.
- **Relacionadas y navegación:** S47, S48, S51, S41, S08 y las fichas de cada entrada.

```text
InicioPage
├── EncabezadoDePagina (Inicio · Monitoreo · Catálogo de indicadores)
├── LoQueMeToca (ListaPriorizada · Para tomar)
├── EsperanSuDecision (ListaPriorizada × 3)
├── Agenda
├── Atajos
├── MisProyectos (tarjeta × n)
└── VisionEjecutiva (KpiCard × n)
```

### S04 · Avisos

- **Ruta y lienzo:** `/notifications` y el menú de la campana · hoja `Ini-Avisos` · F3.
- **Propósito:** ver qué pasó que me toca y llegar al registro.
- **Roles:** cada persona ve solo sus avisos.
- **Acciones:** abrir (marca como leído); "Solo sin leer"; "Marcar todos como leídos".
- **Disposición y componentes:** lista con tipo por color e ícono, título, detalle y hora; punto de no leído; nota de origen.
- **Datos y API:** la bandeja de wasichai-notifications (`api.md` §4.10): `GET /api/auth/me/notifications`,
  `GET …/summary`, `POST …/{id}/read`, `POST …/read-all` y `GET …/stream` (SSE), con los componentes de `@wasichai/*`;
  tipos de `workflows.md` "Avisos".
- **Estados:** sin avisos, vacío; la campana muestra los últimos cinco.
- **Permisos:** cada persona solo ve sus avisos; la bandeja quita el enlace a un registro que no puede leer.
- **Responsive:** igual en teléfono.
- **Relacionadas y navegación:** el registro de cada aviso.

```text
NotificationsPage
├── NotificationFilters
└── NotificationList (NotificationItem)
NotificationBell (NotificationMenu)
```

### S49 · Reportes y exportaciones

- **Ruta y lienzo:** `/reports` y `/projects/:projectId/reports` · hoja `Rep-Reportes` · F2–F4 exportaciones; F8 entregables.
- **Propósito:** generar un entregable o exportar datos al nivel de divulgación correcto.
- **Roles:** entregables: SMG y SHM (`GENERATE`, `APPROVE`, `ISSUE`); exportaciones: los roles con `EXPORT` sobre cada objeto.
- **Acciones:** "Nuevo entregable" (tipo, código, título, periodo, corte, alcance según el tipo, organizaciones y
  plantilla); elegir exportación, nivel y confirmar la lista de verificación; "Exportar"; "Plantillas" (S64, solo
  `ADMIN`).
- **Disposición y componentes:** tabla de entregables (tipo, corte, estado, borrador, revisión, niveles); exportación con
  opciones, nivel de divulgación y lista de verificación EXTERNO; explicación de los niveles.
- **Datos y API:** `deliverable`; `GET /api/sgspe/deliverable-templates` para elegir la plantilla; exportaciones XLSX de
  `deliverables.md` §10, con fila en `export_log`.
- **Estados:** "Exportar" queda deshabilitado con la razón hasta confirmar la lista EXTERNO.
- **Permisos:** la exportación lee como la persona: lo que no lee sale "no disponible"; el documento de identidad nunca se
  exporta.
- **Responsive:** la tabla pasa a tarjetas.
- **Relacionadas y navegación:** S50, S64.

```text
ReportsPage
├── DeliverableTable
└── ExportPanel (ExportPicker · DisclosureLevelRadio · ExternalChecklist)
```

### S50 · Entregable por secciones

- **Ruta y lienzo:** `/reports/deliverables/:deliverableId` · hoja `Rep-Entregable` · F8.
- **Propósito:** revisar, aprobar y emitir un entregable sabiendo que se emite exactamente lo revisado.
- **Roles:** SMG y SHM en las transiciones de WF-H; `DOWNLOAD_RESTRICTED` para ver borradores.
- **Acciones:** según el estado: "Enviar a revisión", "Observar…", "Aprobar…", "Emitir…", "Generar otro borrador",
  "Nueva revisión…"; en cada sección, "Sugerir con IA" (AI-04, solo en las que el tipo marca con IA) y "Usar…" con
  motivo.
- **Disposición y componentes:** encabezado con estado y niveles; camino de WF-H; editor de sección con tiptap y su
  extensión de Markdown, con un selector de menciones que inserta `[[person:<id>]]`, `[[position:<id>]]` o
  `[[stakeholder:<id>]]` y la sugerencia de IA al lado; secciones con su insignia de origen (humana, calculada,
  asistida por IA); panel del borrador con su estado, la letra, los avisos (`draft_warnings`), el PDF de cada nivel en
  `PdfDialog` y el SHA-256 de cada candidata; "Al emitir", con la lista de verificación EXTERNO; versiones emitidas
  con sus descargas y la constancia de emisión.
- **Datos y API:** `POST /api/sgspe/deliverables/{id}/drafts`; `GET …/draft` cada 5 s mientras está pendiente;
  `GET …/draft/content`; transiciones de WF-H con motivo, y "Aprobar" con el `draftModelSha256` en el motivo;
  `POST …/issue` (motivo, `draftModelSha256` y la lista EXTERNO); `GET …/versions/{n}/content`;
  `POST /api/sgspe/ai/runs` (AI-04) y `POST /api/sgspe/ai-suggestions/{id}/accept`.
- **Estados:** EN COLA, EN PROCESO, LISTO, FALLIDO (con su código); un mapa fallido (`MAP_FAILED`) deja el borrador
  LISTO pero bloquea la emisión; una sección editada después del borrador bloquea el envío, la aprobación y la
  emisión (`409 SECTIONS_CHANGED`, con las secciones); otro modelo, `409 MODEL_MISMATCH`; sin la lista, `400
  CHECKLIST_REQUIRED`; una sección asistida no vuelve a humana (`422 AI_ORIGIN_LOCKED`).
- **Permisos:** la versión candidata no se descarga; los borradores llevan marca BORRADOR; EXTERNO, solo en PDF.
- **Responsive:** el panel del borrador baja.
- **Relacionadas y navegación:** el editor de una sección (con AI-04), S49.

```text
DeliverablePage
├── RecordHeader · StatePath
├── SectionList (SectionRow · OriginBadge)
├── SectionEditor (MarkdownEditor · MentionPicker · AiSuggestionPanel)
├── DraftPanel (GenerationStatus · DraftWarnings · DraftLinks · CandidateHash) · IssueDialog (ExternalChecklist)
└── VersionList (VersionDownloads · IssueCertificate)
```

### S53 · Administración

- **Ruta y lienzo:** `/administration`, la sección Administración del portal, que abre `/admin` en otra pestaña (DD-20) ·
  hoja `Adm-Admin` · F1; rediseño P-08.
- **Propósito:** saber qué se administra en el portal (con motivo y reglas) y qué en la consola de la plataforma.
- **Roles:** PAD y la administración de la organización.
- **Acciones:** en «En el portal», los enlaces a Proyectos (con su Equipo y parámetros, S11), las reglas de alerta de la
  organización (S60) y las plantillas de entregables (S64, desde F8), cada uno solo si la cuenta lo abre; "Abrir la
  consola" (`/admin`, otra pestaña).
- **Disposición y componentes:** dos `Seccion`: «En el portal» (cada cambio sigue las reglas, pide su motivo y queda en el
  historial) y «En la consola de la plataforma» (usuarios y roles de cada cuenta, roles y permisos, la organización, las
  capas y los servicios del mapa). Antes del botón «Abrir la consola», el aviso «Cuidados»: la estructura de SGSPE (tipos
  de datos, flujos de aprobación, roles y tipos de documento) no se cambia en la consola, porque se pierde en la próxima
  actualización, y la consola no pide motivo (U-08, U-37).
- **Datos y API:** ninguna propia; `/admin` es `WasichaiApp` (`architecture.md` §9.1).
- **Estados:** la sección no aparece para quien no administra; sin ninguna pantalla del portal que la cuenta abra, «En el
  portal» lo dice.
- **Permisos:** `/admin` no pide motivo todavía (UPS-08): las escrituras críticas se hacen en el portal; lo editado a mano en el
  modelo se pierde al reaplicar (GAP-030).
- **Responsive:** columnas apiladas.
- **Relacionadas y navegación:** S06, S11, S64.

```text
AdministracionPage
├── Seccion «En el portal» (enlaces según permisos)
└── Seccion «En la consola de la plataforma» (lista · Alert «Cuidados» · Button «Abrir la consola»)
```

### S64 · Plantillas de entregables

- **Ruta y lienzo:** `/reports/templates`, enlazada desde Administración (S53) y desde Reportes (S49) · sin hoja
  (plan 8.0) · F8.
- **Propósito:** subir la plantilla Word de la organización y ver qué plantilla rige cada clave.
- **Roles:** solo `ADMIN`, que tiene `MANAGE_METADATA` sobre `deliverable`; los demás roles que leen `deliverable` ven
  las plantillas en el selector de S49.
- **Acciones:** primaria "Subir plantilla" (archivo .dotx o .docx de hasta 20 MB, clave y nombre); secundaria
  descargar una plantilla.
- **Disposición y componentes:** lista con la plantilla más reciente de cada clave (clave, nombre, SHA-256, fecha y
  avisos); el diálogo de subida muestra las violaciones de `422 TEMPLATE_INVALID` o los avisos de una subida aceptada;
  un texto fijo con los marcadores admitidos, los estilos requeridos y las fuentes permitidas (Carlito, Caladea,
  Liberation Sans y Liberation Serif).
- **Datos y API:** `GET /api/sgspe/deliverable-templates`; `POST /api/sgspe/deliverable-templates` (multipart, con
  `Idempotency-Key`); la descarga, por la ruta nativa de wasichai-files.
- **Estados:** sin plantillas, «rige la plantilla base de SGSPE»; una subida rechazada lista cada violación; una
  plantilla nueva con una clave existente reemplaza a la anterior solo para los borradores siguientes.
- **Permisos:** la sección no aparece para quien no administra; una plantilla no se edita ni se borra.
- **Responsive:** la lista pasa a tarjetas.
- **Relacionadas y navegación:** S53, S49.

```text
DeliverableTemplatesPage
├── TemplateList (TemplateRow · TemplateWarnings)
└── TemplateUploadDialog (FileDrop · ViolationList)
```

### S56 · Gestionar vistas

- **Ruta y lienzo:** diálogo «Guardar esta vista…» en cada lista y `/views?object=` para gestionar · hojas
  `Vistas-Compartidas` (interactiva) y `Vistas-Gestionar` del lienzo de la etapa 3, página 24 · F2.
- **Propósito:** guardar una combinación de filtros, columnas y orden y compartirla con el equipo del proyecto o la
  organización (ASM-083).
- **Roles:** todos guardan vistas para sí y las comparten con el equipo de sus proyectos; compartir con la organización solo
  la gerencia social y la administración (con permiso, nunca con literales de rol).
- **Acciones:** en el diálogo, "Guardar" con nombre y visibilidad (solo yo, equipo del proyecto, organización). En la gestión,
  sobre las propias: "Renombrar", "Cambiar visibilidad", "Copiar enlace", "Borrar…"; sobre las ajenas: "Guardar una copia",
  "Ocultar de mis pestañas".
- **Disposición y componentes:** `SavedViewDialog`; tabla con vista (y su resumen de filtros), visibilidad, quién la creó, uso
  y acciones; reglas al pie.
- **Datos y API:** requiere el objeto o tabla fija `saved_view` (propietario, objeto, alcance, proyecto, definición en JSON,
  ocultas por persona), que define la sesión de implementación; la definición guarda filtros, columnas y orden, nunca datos.
- **Estados:** sin vistas propias, invita a guardar la primera desde una lista; una vista compartida cuyo proyecto ya no está
  asignado deja de aparecer; un enlace a una vista sin acceso muestra «sin permiso».
- **Permisos:** solo quien creó la vista la cambia o la borra; borrar una que otros usan avisa y pide confirmación. Los
  registros que muestra dependen del rol de quien la abre.
- **Responsive:** en teléfono las vistas son un selector y la gestión es de solo lectura, salvo ocultar.
- **Relacionadas y navegación:** S16, S28, S35 y toda lista con el patrón de `design-system.md` §6.4.

```text
SavedViewDialog (NameField · VisibilityFieldset)
ManageViewsPage
├── SavedViewsTable (VisibilityChip · OwnerCell · RowActions)
└── SavedViewRules
```

## 15. Campo sin conexión

Lienzo de la etapa 3, página 19. Decisión del usuario: la captura de campo sin conexión entra al diseño (ASM-081). El teléfono
guarda una cola local de interacciones, participantes, observaciones y fotos y la envía al volver la señal; el servidor aplica
las mismas reglas que en línea. La arquitectura de la sincronización se fija en un ADR antes de implementarla.

### S54 · Campo: mis tareas y visita

- **Ruta y lienzo:** `/tasks` y `/projects/:projectId/field-visits/:visitId` en el diseño móvil (`FieldLayout`) · hoja
  `Cam-Tareas` · F2, F3; sin conexión según ASM-081.
- **Propósito:** saber qué levantar hoy y dónde, con o sin señal.
- **Roles:** FAG y CRO.
- **Acciones:** abrir una tarea; "Registrar en esta parada"; "Actualizar descarga".
- **Disposición y componentes:** barra con indicador de conexión; aviso "Trabajas sin señal"; lista de tareas con su
  situación; visita con paradas sobre el mapa base descargado; descarga para campo.
- **Datos y API:** `task`, `field_visit`; el paquete de descarga trae tareas y visitas de 7 días, lugares, grupos de interés y
  actores (nombre y cargo) y catálogos, sin campos restringidos aunque el rol los lea.
- **Estados:** en línea, sin señal, enviando, conflicto, sesión vencida.
- **Permisos:** solo lo que el rol lee; nada restringido sale al teléfono.
- **Responsive:** diseño solo para teléfono (< 640), con objetivos de 48 px.
- **Relacionadas y navegación:** S55, cola.

```text
FieldLayout (ConnectionIndicator · OfflineBanner · FieldNav)
├── FieldTasksPage (FieldTaskItem)
├── FieldVisitPage (OfflineMap · StopList)
└── FieldDownloadPage
```

### S55 · Campo: interacción, observación y foto

- **Ruta y lienzo:** `/projects/:projectId/engagements/new` y la captura de observación en el diseño móvil · hoja
  `Cam-Captura` · F2; sin conexión según ASM-081.
- **Propósito:** capturar en una pantalla y seguir.
- **Roles:** FAG y CRO; FAG solo cambia lo que capturó (ASM-069).
- **Acciones:** "Guardar en el teléfono"; agregar participantes y preguntas; tomar foto.
- **Disposición y componentes:** formulario corto (tipo, lugar por GPS, participantes, lo que se habló, preguntas); observación
  con foto, personas en la foto y consentimiento, clasificación; confirmación con lo que queda en la cola.
- **Datos y API:** al enviar, los mismos endpoints que en línea: `POST` genérico de `engagement`, `engagement_participant`,
  `field_observation`; `POST /api/sgspe/evidence` para las fotos, cada uno con su `Idempotency-Key` creado en el teléfono.
- **Estados:** guardado en el teléfono; cerrar sesión con elementos por enviar pide confirmación.
- **Permisos:** sin `DOWNLOAD_RESTRICTED`, FAG no vuelve a abrir desde la oficina una foto DATOS PERSONALES.
- **Responsive:** solo teléfono.
- **Relacionadas y navegación:** cola; S23 en la oficina.

```text
FieldCapturePage
├── QuickEngagementForm (PlaceFromGps · ParticipantPicker · QuestionList)
├── ObservationForm (PhotoCapture · PeopleConsentFieldset · ClassificationSelect)
└── SavedConfirmation
```

### Estado de conexión, cola y sincronización

- **Ruta y lienzo:** `/field/queue` en el diseño móvil · hojas `Cam-Cola` y `Cam-Sync` · según ASM-081.
- **Propósito:** saber qué espera envío y decidir sobre lo que el servidor no aceptó, sin perder nada.
- **Roles:** quien capturó.
- **Acciones:** reintentar; "Enviarlo como nota…", "Guardar como observación nueva…", "Descartar mi cambio"; "Subir fotos solo
  con wifi".
- **Disposición y componentes:** indicador de conexión con cinco estados (texto, no solo color, anunciado con `role="status"`);
  cola en orden de envío (interacción, lo que cuelga de ella, fotos al final); conflicto con lo de la oficina y lo del teléfono
  lado a lado; fotos con progreso reanudable.
- **Datos y API:** un reintento con el mismo `Idempotency-Key` no duplica; `412` (cambió en la oficina), `409` (el estado no lo
  admite) y `403` (registro ajeno, `EditAnyGuard`) dejan el elemento en conflicto con su razón.
- **Estados:** sesión vencida sin señal: la cola se conserva y se envía al volver a ingresar con la misma cuenta.
- **Permisos:** los permisos se comprueban al enviar, no al capturar.
- **Responsive:** solo teléfono.
- **Relacionadas y navegación:** S54, S55. Flujo de campo.

```text
FieldQueuePage
├── QueueList (QueueItem · SyncStatusBadge)
├── ConflictResolver (ServerVersion · LocalChange · ResolutionActions)
└── PendingUploads (UploadProgress · WifiOnlySwitch)
```

## 16. Trazabilidad

- **Requisitos:** RF-PRJ-001 a RF-PRJ-006, RF-TER-001 a RF-TER-008, RF-LBS-001 a RF-LBS-009, RF-STK-001 a RF-STK-014,
  RF-ENG-001 a RF-ENG-012, RF-FLD-001, RF-COM-001 a RF-COM-010, RF-AGR-001 a RF-AGR-003, RQ-GIS-002 a RQ-GIS-011,
  RQ-DSH-032, RQ-SEC-010, RN-ENG-001, RN-ENG-004, RN-ENG-005, RN-COM-001, RN-COM-005, RN-INV-003. Parte 3a: RF-GRV-001,
  RF-GRV-002, RN-GRV-002, RF-RSK-001 a RF-RSK-003, RN-RSK-001 a RN-RSK-003, RN-EWS-001, RF-EWS-004, RF-INV-001, RF-INV-004,
  RN-INV-001 a RN-INV-004, RN-INV-006, RF-DOC-001, RF-DOC-003, RNF-PRIV-004, RF-WFL-001, RF-WFL-002, RF-WFL-004, RN-WFL-001. Parte 3b: RF-DSH-001
  a RF-DSH-010, RQ-DSH-032, RQ-DSH-034, RQ-GIS-014, RQ-GIS-015, RF-AIK-005, RF-AIK-006, RQ-AI-001, RQ-AI-003, RQ-AI-011,
  RQ-AI-012, RF-NTF-001, RF-IAM-001, RF-IAM-002, RQ-SEC-015, RQ-REP-014, RQ-REP-022, RQ-REP-033, RF-FLD-001, RNF-PRIV-004.
- **Supuestos:** ASM-003, ASM-008, ASM-009, ASM-019, ASM-062, ASM-063, ASM-067, ASM-069, ASM-075, ASM-076, ASM-079,
  ASM-080. Parte 3a: ASM-004, ASM-005, ASM-011, ASM-030, ASM-036, ASM-068, ASM-070, ASM-071, ASM-072, ASM-095, ASM-096,
  ASM-097, ASM-098, ASM-102, ASM-104. Parte 3b: ASM-063, ASM-069, ASM-073, ASM-074, ASM-078, ASM-081.
- **Documentos:** [workflows.md](../workflows.md) (WF-A, WF-B, WF-C, WF-D, WF-I, WF-K, WF-L, WF-O; en la parte 3a, WF-E1,
  WF-E2, WF-E3, WF-F, WF-G, WF-J, WF-M, WF-N y WF-P), [api.md](../api.md) §2–§4, [security.md](../security.md) §7,
  [dashboards.md](../dashboards.md) §3 y §5, [gis.md](../gis.md), [domain-model.md](../domain-model.md) §6,
  [ai-architecture.md](../ai-architecture.md) §4, §8, §9.3 y §13, [deliverables.md](../deliverables.md) §8 a §10,
  [architecture.md](../architecture.md) §9.1.
