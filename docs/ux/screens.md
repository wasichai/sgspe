# Pantallas de SGSPE · fichas de handoff

Fichas de implementación de las pantallas del portal (`sgspe-ui`). Esta versión cubre la **etapa 2** del diseño: proyectos y
Project 360, territorio, grupos de interés, relacionamiento, compromisos y acuerdos (S06 a S26 y S28 a S33). La etapa 3 agrega
el resto del inventario de [`ux-architecture.md`](ux-architecture.md) §9.

- **Lienzo de la etapa 2:** <https://claude.ai/artifact/7RUzqeAhtWx75vCNPCBRWe> (privado: se comparte desde su menú). Cada
  ficha nombra su hoja.
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
| Roles | Quién la lee y quién actúa, según la matriz de `security.md` §7 (abreviaturas de §7.1: PAD, PMG, SMG, CRO, SHM, CMM, GAN, FAG, APR, ANL, RDO). "Todos" son los roles operativos con READ sobre el objeto; `AUDITOR` lee todo y no escribe |
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
- **Shell:** toda ruta vive dentro de `AppShell` (barra superior, `ContextBar`, navegación de 13 secciones) y, bajo
  `/projects/:projectId`, de `ProjectLayout` (sub-navegación del proyecto con conteos). El contexto de territorio se lee de
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
- **Responsive:** los puntos de quiebre son los de `tokens.css` (sm 640, md 768, lg 1024, xl 1280, 2xl 1440). Por debajo de
  `lg`, la sub-navegación del proyecto pasa a un menú y los paneles laterales de las fichas bajan debajo del contenido.

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
- **Disposición y componentes:** encabezado con estado del proyecto, jefatura (ASM-080), etapa y fase; "Atención requerida"
  (lista priorizada de vencidos, por vencer, bloqueados y actas pendientes); cinco `KpiCard` de salud; paisaje de grupos de
  interés (mini matriz KPI-STK-03 y barra de posturas KPI-STK-02); reuniones al mes frente a la meta (KPI-ENG-02); áreas de
  influencia vigentes; mini mapa; actividad reciente; `AiSummary` (F7).
- **Datos y API:** de F2 a F7, una llamada por indicador con `GET /api/sgspe/dashboards/indicators/{code}?project=` (KPI-STK-01
  a 03, KPI-ENG-01 y 02, KPI-COM-02 a 04 y 06 desde F3); desde F8, `GET /api/sgspe/dashboards/project?project=` en una sola
  llamada. Áreas: genérica `area_of_influence` en `approved`. Actividad: `GET /api/objects/project/records/{id}/history` y los
  registros recientes del proyecto.
- **Estados:** cada `KpiCard` de un módulo no instalado dice "llega en F3/F4"; un indicador sin permiso dice "no disponible"
  (RQ-DSH-032); sin jefatura asignada, el encabezado lo marca.
- **Permisos:** la barra de posturas solo se dibuja para SMG y SHM (`stance`, `security.md` §7.9).
- **Responsive:** una columna; "Atención requerida" primero.
- **Relacionadas y navegación:** cada fila de atención abre su ficha con el contexto del proyecto; el mini mapa abre S09.

```text
ProjectOverviewPage
├── ProjectHeader (StatusBadge · jefatura · etapa · Button "Resumen con IA")
├── AttentionList
├── HealthKpis (KpiCard × 5)
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
└── SyncedTable (Tabs: Componentes · Miembros del AIDS · Predios)
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
- **Roles:** lectura de todos. Asignaciones: PAD. Partes y campañas: PAD, PMG. Concesiones: PAD, GAN. Parámetros: PAD, PMG.
- **Acciones:** primaria según la pestaña ("Asignar persona", "Nueva campaña"); secundarias editar y terminar una asignación.
- **Disposición y componentes:** `Tabs` (Equipo, Campañas, Partes y concesiones, Parámetros y políticas); `DataTable` de
  asignaciones con rol, periodo y estado; campañas con `ProgressBar` del tope y aviso de montos en otra moneda (ASM-063);
  parámetros agrupados por módulo con la marca "por defecto" o "cambiado".
- **Datos y API:** genérica `project_assignment`, `exploration_campaign`, `project_party`, `project_concession` y `project`
  (campos de parámetros).
- **Estados:** una asignación vencida se marca y no da acceso de escritura (RQ-SEC-006).
- **Permisos:** quien no puede escribir ve todo en solo lectura.
- **Responsive:** cada pestaña en una columna.
- **Relacionadas y navegación:** la jefatura que muestra S08 sale de esta pestaña (ASM-080).

```text
ProjectSettingsPage
├── Tabs
│   ├── TeamTab (DataTable<Assignment>)
│   ├── CampaignsTab (CampaignCapCard × n)
│   ├── PartiesTab (DataTable<Party> · DataTable<Concession>)
│   └── PoliciesTab (PolicyGroup × n)
└── AssignmentDrawer
```

## 4. Territorio

### S12 · Mapa territorial

- **Ruta y lienzo:** `/territory` (con `?project=`) · hoja `Ter-Mapa` (interactiva) · F2 (capas básicas y selección); F6
  (ficha contextual con KPI-TER-01, mapa de calor, WMS).
- **Propósito:** entender qué pasa en cada lugar y pasar del lugar a sus registros sin perder el contexto.
- **Roles:** lectura de todos; capas y cifras según permisos.
- **Acciones:** primaria "Abrir la ficha" del lugar seleccionado; secundarias "Filtrar el proyecto por este lugar" (fija el
  chip de territorio), "Mapa de calor de QCS" (F6, con QCS desde F4), "Capturar PNG", "Importar" (GAN).
- **Disposición y componentes:** filtros (proyecto, periodo, capas); `Map` con lugares seleccionables (conteo de grupos de
  interés por localidad); panel lateral de ficha contextual con seis cifras, barra de posturas y última actividad; lista de
  lugares visibles (alternativa de teclado); tabla de registros del lugar debajo del mapa.
- **Datos y API:** capas con `GET /api/sgspe/territory/layers/{layer}`; ficha contextual con
  `GET /api/sgspe/territory/localities/{id}/card` o `…/communities/{id}/card` (F6) más
  `GET /api/sgspe/dashboards/indicators/KPI-TER-01?locality=` o `?community=`; registros del lugar con
  `GET /api/sgspe/territory/zones/{zoneObject}/{id}/records?of=` (F6); mapa de calor con
  `GET /api/sgspe/territory/concentration?of=grievance` (F6). En F2, la selección abre directamente S13 o S14.
- **Estados:** sin selección, el panel invita a elegir un lugar; una cifra sin permiso dice "no disponible".
- **Permisos:** la barra de posturas solo para SMG y SHM; los grupos de interés siempre agregados (LYR-17).
- **Responsive:** en teléfono la ficha contextual es una hoja inferior que se expande.
- **Relacionadas y navegación:** "Filtrar el proyecto por este lugar" lleva a S16 o S08 con `?territory=`; el rastro anota
  "Mapa territorial".

```text
TerritoryMapPage
├── MapFilters (proyecto · periodo · capas · HeatmapToggle)
├── TerritoryMap (Map · PlaceMarkers · Legend)
├── ContextCard (KpiCard × 6 · StanceBar · Button "Abrir la ficha")
├── VisiblePlacesNav
└── ZoneRecordsTable
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

## 5. Grupos de interés

### S16 · Lista de grupos de interés

- **Ruta y lienzo:** `/projects/:projectId/stakeholders` y `/stakeholders` (global, agrega la columna Proyecto; ASM-076) ·
  hoja `GI-Lista` · F2.
- **Propósito:** ver a quiénes se gestiona, priorizar y actuar sobre varios a la vez.
- **Roles:** lectura de todos. Crear: CRO, SHM. Exportar: SMG, SHM, ANL (`EXPORT`).
- **Acciones:** primaria "Nuevo grupo de interés"; secundarias "Ver como matriz" (S19), "Exportar"; masivas "Reasignar
  responsable", "Registrar una interacción con ellos" (abre S24 con los seleccionados) y "Exportar".
- **Disposición y componentes:** `SavedViews` (del sistema: "Prioridad MUY ALTA y ALTA", "Míos", "Sin evaluar", "Sin contacto
  en N días", "Todos"; personales, ASM-079); `FilterBar` (grupo de influencia, postura, ámbito, estado de la relación,
  responsable, tipo); `BulkActionBar`; `DataTable` con grupo de interés (actor y tipo), territorio, grupo de influencia (marca
  "ajustado" si `group_overridden`), postura, prioridad, estado (WF-B), relación, responsable, evaluación y último contacto.
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
├── SavedViews
├── FilterBar
├── BulkActionBar
└── DataTable<StakeholderRow> · Pagination
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
- **Disposición y componentes:** fecha, base y cargo evaluado; 6 indicadores de interés (afectación y oportunidad) y 9 de poder
  (control de recursos, capacidad de acción, redes y movilización) con `Radio` de 1 a 5 y el valor anterior; postura,
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
│   ├── IndicatorGroup × 5 (IndicatorRadio × 3)
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
- **Disposición y componentes:** patrón de lista (`design-system.md` §6.4) con agrupación Vencidos, Por vencer, Bloqueados;
  la vista Candidatos lista los propuestos con su procedencia (humano o IA con confianza) y los decididos; panel de revisión
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
├── PageHeader · SavedViews (… · Candidatos)
├── FilterBar · BulkActionBar
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

## 8. Trazabilidad

- **Requisitos:** RF-PRJ-001 a RF-PRJ-006, RF-TER-001 a RF-TER-008, RF-LBS-001 a RF-LBS-009, RF-STK-001 a RF-STK-014,
  RF-ENG-001 a RF-ENG-012, RF-FLD-001, RF-COM-001 a RF-COM-010, RF-AGR-001 a RF-AGR-003, RQ-GIS-002 a RQ-GIS-011,
  RQ-DSH-032, RQ-SEC-010, RN-ENG-001, RN-ENG-004, RN-ENG-005, RN-COM-001, RN-COM-005, RN-INV-003.
- **Supuestos:** ASM-003, ASM-008, ASM-009, ASM-019, ASM-062, ASM-063, ASM-067, ASM-069, ASM-075, ASM-076, ASM-079, ASM-080.
- **Documentos:** [workflows.md](../workflows.md) (WF-A, WF-B, WF-C, WF-D, WF-I, WF-K, WF-L, WF-O),
  [api.md](../api.md) §2–§4, [security.md](../security.md) §7, [dashboards.md](../dashboards.md) §3 y §5,
  [gis.md](../gis.md), [domain-model.md](../domain-model.md) §6.
