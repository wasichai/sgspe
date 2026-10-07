# Historia de cambios

La entrada más reciente va primero. El razonamiento de arquitectura está en [`adr/`](adr/README.md); este archivo
registra lo entregado.

## 2026-10-07 — Diseño UX/UI, etapa 3, parte 3b: tableros, capa de IA, transversales, campo sin conexión y handoff

Segunda parte de la última etapa, después de la revisión de la 3a. Con ella el inventario de 55 pantallas queda diseñado.

- **Lienzo de la etapa 3**, páginas 16 a 20, con 25 hojas: tableros (revisión ejecutiva de 5 minutos, monitoreo de la Tabla
  9.5 con cifra oficial o viva y "no disponible", compromisos, QCS, riesgos, territorial e inversión social), capa de IA (panel
  del asistente por rol y estado de la organización, respuesta con citas, bandeja de revisión, comparación de duplicados,
  resumen ejecutivo y borrador de sección), inicio y transversales (login, inicio por rol, avisos, reportes y exportaciones con
  nivel de divulgación, entregable por secciones, administración), campo sin conexión en el teléfono (tareas y visita,
  captura, cola y conflictos de sincronización) y handoff (flujo 18, flujo de campo, mapa de rutas y árbol de módulos).
- **[`ux/screens.md`](ux/screens.md):** fichas de S01, S02, S04, S05, S27, S34, S38, S49 a S55, del monitoreo, del tablero
  territorial, del de inversión, de la respuesta con citas, del duplicado, del resumen de IA y de la cola de campo.
- **[`ux/flows.md`](ux/flows.md):** flujo 18 (preguntar a la IA) y flujo de campo sin conexión.
- **Decisión del usuario:** la captura de campo sin conexión se diseña. ASM-081 la registra, responde GAP-008 y deja su
  implementación después del MVP, con un ADR de sincronización; el análisis pasa a 489 IDs. Se actualizan Q5 y las rutas en
  `ux-architecture.md`, los componentes de IA, conexión y sincronización en `design-system.md` y el roadmap.

## 2026-10-07 — Diseño UX/UI, etapa 3, parte 3a: QCS, riesgos, programas, documentos y tareas

Primera de las dos partes de la última etapa, en el orden de USR-10: QCS ← riesgos → programas, más las piezas transversales
de documentos, tareas y aprobaciones.

- **Lienzo de la etapa 3** con 18 hojas en cinco páginas: QCS (lista unificada por plazo, registro por tipo, ficha de queja
  interactiva con la segregación de la resolución ALTA y CRITICA, consulta y solicitud con aceptación como compromiso o
  aporte), riesgos (registro con mapa de calor interactivo, ficha con revisiones inmutables y mitigación, centro de alerta
  temprana interactivo con la confirmación solo por una persona, incidente), programas e inversión (lista con el tope de la
  campaña, ficha de programa, aporte con las políticas P1 a P8), documentos (biblioteca y ficha de evidencia con descarga
  restringida), tareas (lista, tablero y calendario interactivos; aprobaciones y escalamientos) y dos hojas de flujos.
- **[`ux/screens.md`](ux/screens.md):** fichas de S35 a S48 y de la ficha de incidente, con el mismo formato que la etapa 2.
- **[`ux/flows.md`](ux/flows.md):** flujos 12 a 15 (crear y resolver una queja, identificar un riesgo y mitigarlo).
- **Decisión del usuario para la parte 3b:** la captura de campo sin conexión entra al diseño; el supuesto que la registra y
  sus cambios en el análisis se agregan con esa parte.

## 2026-10-07 — Fase 1: fundación

La Fase 1 deja SGSPE compilando y ejecutable sobre wasichai 0.3.2 y `@wasichai/*` 0.5.0, todavía sin objetos de negocio:
el arranque de la app, los roles como datos, el contrato con la plataforma, la observabilidad y la CI de los dos
repositorios. El plan ejecutable está en
[`superpowers/plans/2026-10-07-fase-1-fundacion.md`](superpowers/plans/2026-10-07-fase-1-fundacion.md).

Estado: las pistas 0, A, B y C están en verde en local y en la CI de los dos PR (wasichai/sgspe#2: build, integración, modelo,
trazabilidad, formato y commits; wasichai/sgspe-ui#1: web y commits), el 2026-10-07. «Implementado» en la matriz de
trazabilidad significa que pasa en la CI de su fase. Dependabot de `sgspe` también necesita `PACKAGES_TOKEN`, como secreto de Dependabot del repositorio
(`docs/develop/README.md`, sección 1); sin él, sus actualizaciones de Gradle no leen GitHub Packages.

### Backend (`sgspe`)

- **App.** `sgspe.SgspeApplication` con `@WasichaiApplication` y los starters core, views, forms y pages; puerto 8094;
  sondas `/actuator/health/liveness` y `/readiness`; `compose.yml` con `postgis/postgis:18-3.6` en el 5435 del VPS.
- **Modelo como código.** `model.json` sin objetos; `roles.json` con los 15 roles declarados y su etiqueta en español, y
  `SUPER_ADMIN` y `ORGANIZATION_ADMIN` mapeados a `ADMIN`; solo `AUDITOR` lee todo el tenant. `apply_roles.py` entiende `"*"`,
  `u` y `"derived": true`, y deshace `ownRecordsOnly`; `apply.py --workflows` es el segundo pase del aprovisionamiento.
- **Contrato con la plataforma.** `TenantIsolationApiTest`, `TenantProvisioningApiTest`, `SecurityApiTest`,
  `RolesApiTest`, `AuditApiTest` y `ErrorContractApiTest`, sobre `SgspeApiTest`, que aprovisiona con los scripts reales.
- **Observabilidad y producción.** `X-Correlation-Id` antes de la seguridad, también en un 401, y una línea de acceso sin
  la cadena de consulta; `application-prod.yml` sin semilla, con secreto obligatorio, token de 4 horas y logs ECS, y
  `ProdGuard`, que con el perfil `prod` impide el arranque, antes de Flyway, si la semilla está encendida, si el secreto es el
  de desarrollo o si el token dura 8 horas o más.
- **`sgspe.common`.** La marca `SgspeWrite`, `Records` sobre `RecordService`, la hora de Lima y el 422 de parámetros
  desconocidos.
- **Arquitectura y CI.** `ArchitectureTest`; CI con build, integración, modelo, trazabilidad (`tools/check_traceability.py`)
  y formato.

### Interfaz (`sgspe-ui`)

- Una app Vite con el portal y `/admin` (`WasichaiApp` con views, forms y pages), login compartido en `sgspe.token`,
  `RequireSession` y `safeNext`; el tema «Pino y piedra» de `docs/ux/tokens.css` en claro y oscuro, sobre el de
  `@wasichai/ui` y con IBM Plex servida desde el mismo origen, con el script de arranque autorizado por hash en la CSP; el
  shell y la navegación de `docs/ux/ux-architecture.md` (las 13 secciones con sus rutas y su fase, «Preguntar»
  deshabilitado hasta F7); español e inglés con números y fechas es-PE; la bienvenida de F1 con el registro del inicio
  por rol; CI con `PACKAGES_TOKEN` y `boundaries.test`.

### Spike y hallazgos

- El spike de entregables y GetMap dejó su resultado en el «Seguimiento» de ADR-0013 y ADR-0015.
- wasichai 0.3.2 responde el 401 sin token con `WWW-Authenticate: Bearer` y cuerpo vacío, no con Problem Details;
  `ErrorContractApiTest` fija ese contrato.
- wasichai 0.3.2 ignora sin error un atributo que el objeto no tiene; `ErrorContractApiTest` también fija ese contrato.
- Un token emitido antes de deshabilitar a un usuario sigue valiendo hasta que vence (UPS-09, wasichai#55);
  `SecurityApiTest` lo fija.
- Las entradas de permisos de campo no se pueden borrar: un PUT con lista vacía no hace nada y no existe DELETE.
  `apply_roles.py` las neutraliza con lectura y escritura en verdadero.
- `apply.py` de caja rechazaba un modelo sin objetos; la copia de SGSPE lo acepta.

### Verificación

- Pista C: aprovisionamiento en orden y prueba de humo con Playwright MCP, con la app arrancada en unos 11 s contra el
  túnel y PostGIS. El aprovisionamiento es idempotente: `apply.py` sin altas, cambios ni omitidos, `apply_roles.py` con
  «15 skipped» y `--workflows` en cero.
- `X-Correlation-Id`: sin cabecera se genera un UUID, `humo-1` se conserva y un valor hostil se reemplaza por un UUID.
- Como `ADMIN`: login con «Pino y piedra» (fondo rgb 245, 246, 243), IBM Plex cargada y cero peticiones a otro host;
  Dashboard con la organización «Demo», el rol ADMIN, la tabla «Módulos por fase» de 13 filas, navegación con Dashboard y
  Administración y «Preguntar» deshabilitado; `/admin/admin/roles` abre sin otro login y muestra las 15 etiquetas en
  español más ADMIN.
- Como `AUDITOR`: la navegación muestra solo Dashboard y Administración dice «Solo para administración»;
  `/admin/admin/audit` carga sin error de permisos pero muestra «Sin resultados», porque el alta de usuarios y de roles no
  se audita (UPS-02, wasichai#49); por la API, auditoría 200, usuarios 403 y roles 403.
- CSP en solo informe en desarrollo, con el hash del script; sin violaciones en la consola con la compilación de
  producción (`vite preview`).
- Suites: `build` en verde; `integrationTest` con 34 pruebas y 0 fallas; modelo y herramientas de Python en verde;
  trazabilidad con 298 requisitos; prettier en verde; interfaz con lint y typecheck en verde, 11 archivos con 109 pruebas tras el cierre (107 en la prueba de humo) y
  `build` en verde.

## 2026-10-07 — Diseño UX/UI, etapa 2: Project 360, territorio, grupos de interés, relacionamiento y compromisos

Segunda de las tres etapas, en el orden que fijó el usuario (USR-10): los objetos que definen la navegación posterior.

- **Lienzo de la etapa 2** con 28 hojas en seis páginas: Project 360 (crear proyecto, Resumen, mapa, equipo y parámetros),
  territorio (mapa territorial interactivo, fichas de comunidad y de localidad con línea base, área de influencia con
  versiones y aprobación, importación geoespacial con CRS de origen y decisiones), grupos de interés (lista, matriz interés ×
  poder interactiva, ficha, evaluación con vista previa, ficha de actor), relacionamiento (calendario, ficha de interacción,
  registro con modo rápido de campo, editor de acta, visita de campo), compromisos (pestañas de la ficha, creación, aprobación,
  entrega → cumplimiento → verificación interactiva, candidatos, acuerdos) y dos hojas de flujos.
- **[`ux/screens.md`](ux/screens.md):** ficha de handoff de S06 a S26 y de S28 a S33: ruta, propósito, roles según la matriz de
  seguridad, acciones, disposición, datos y API, estados, permisos, responsive, navegación y árbol de componentes React.
- **[`ux/flows.md`](ux/flows.md):** flujos 1 a 11, 16 y 17, paso a paso, con pantalla, transición o endpoint y regla.
- **Ajustes:** rutas de lista bajo el proyecto para relacionamiento y compromisos, el tablero de compromisos (S27) pasa a la
  etapa 3 con los demás tableros, la acción masiva "Asignar verificador" en el patrón de lista y el supuesto ASM-080 (la
  jefatura del proyecto es la asignación activa con `PROJECT_MANAGER`) en el análisis (488 IDs).

## 2026-10-07 — Diseño UX/UI, etapa 1: arquitectura de UX, navegación y design system

El usuario pidió la UX/UI completa del portal con un prompt maestro de UX/UI (PRM-02) y fijó el orden de trabajo y el principio
de navegación contextual (USR-10). El diseño se entrega en tres etapas con revisión; esta es la primera.

- **Lienzo de diseño** con 17 hojas: visión UX, mapa del dominio, roles, tres contextos de navegación, inventario de 55
  pantallas, shell, navegación por rol, prototipo navegable del hilo de contexto (comunidad → grupo de interés → interacción →
  compromiso → evidencia), búsqueda global, fundamentos, sistema de estados, componentes y cuatro patrones núcleo.
- **[`ux/ux-architecture.md`](ux/ux-architecture.md):** visión, principios, decisiones heredadas de la Fase 0 donde PRM-02 choca
  con los documentos, roles y trabajos por hacer, arquitectura de información, rutas, navegación, hilo de contexto y
  trazabilidad, inventario de pantallas y preguntas abiertas.
- **[`ux/design-system.md`](ux/design-system.md) y [`ux/tokens.css`](ux/tokens.css):** dirección visual "Pino y piedra",
  tokens en claro y oscuro con contraste verificado, sistema de estados por workflow, situación derivada, nivel y procedencia,
  catálogo de componentes, patrones núcleo, accesibilidad WCAG 2.2 AA, responsive y guía de implementación.
- **[`requirements-analysis.md`](requirements-analysis.md):** fuente PRM-02, decisión USR-10, regla de precedencia para el diseño
  de la interfaz y supuestos ASM-073 a ASM-079 (487 IDs).

## 2026-10-07 — Fase 0: descubrimiento y diseño

La Fase 0 entrega el diseño completo de SGSPE, sin código, sobre wasichai 0.3.2 (backend) y `@wasichai/*` 0.5.0 (UI).

### Fuentes y requisitos

- **Fuentes analizadas.** Se citan por su ID y no se versionan:
  - DOC-01: diagnóstico social de áreas de influencia de un proyecto de exploración, 2018;
  - DOC-02: informe de trabajo de campo y avance, 2026;
  - DOC-03: captura de ArcMap con sondajes, AID y predios;
  - PRM-01: el prompt maestro;
  - USR: los pedidos del usuario.
- **Pedidos del usuario incorporados** (USR):
  - generar entregables como DOC-01 (DOCX) y DOC-02 (PDF);
  - ver la situación en GIS y en tableros;
  - construir sobre wasichai como srtm y caja (la plantilla vigente es caja-backend y caja-ui);
  - nombres técnicos en inglés;
  - anonimizar los datos personales.
- **[`requirements-analysis.md`](requirements-analysis.md):** 480 IDs, entre ellos:
  - requisitos funcionales, no funcionales, reglas y RQ de GIS, entregables, tableros, seguridad, auditoría e IA;
  - 60 contradicciones, 32 vacíos, 72 supuestos y 18 brechas de wasichai (UPS).
  - [`glossary.md`](glossary.md) da la equivalencia español–inglés.

### Diseño

- **Dominio.** [`domain-model.md`](domain-model.md) define 20 contextos delimitados y 89 objetos, con sus reglas, guardas,
  campos derivados, listeners, paquetes `sgspe.*` y trabajos programados. Es la base de los demás documentos:
  - [`architecture.md`](architecture.md), [`platform-fit.md`](platform-fit.md) y [`api.md`](api.md): arquitectura sobre
    wasichai, encaje con la plataforma y API del portal `/api/sgspe/**`;
  - [`database-model.md`](database-model.md): modelo de metadata (fichas de objeto, relaciones, índices, banderas y
    migraciones);
  - [`workflows.md`](workflows.md): workflows WF-A a WF-P, destinatarios por rol, escalamiento y JOB-01 a JOB-08;
  - [`security.md`](security.md): 17 roles, matriz de permisos y de campos, alcance por proyecto y datos personales;
  - [`gis.md`](gis.md): capas LYR-01 a LYR-24, SRID 4326 con CRS UTM por proyecto, importación con reconciliación y
    GeoServer;
  - [`deliverables.md`](deliverables.md): entregables DOCX primero, PDF por Gotenberg, niveles de divulgación y emisión
    congelada;
  - [`dashboards.md`](dashboards.md): catálogo de KPIs calculados en vivo y tableros;
  - [`ai-architecture.md`](ai-architecture.md): IA con validación humana, lista cerrada de aceptación y
    seudonimización.
- **Decisiones.** [`adr/`](adr/README.md) registra ADR-0001 a ADR-0026. Las decisiones de plataforma se citan como
  `WAS-ADR-00NN`.
- **Trazabilidad y plan.** [`requirements-traceability.md`](requirements-traceability.md) cubre 298 requisitos y
  [`roadmap.md`](roadmap.md) las fases F0 a F8, el plan de la Fase 1 y las decisiones del checkpoint.
- **Planes.** [`superpowers/plans/`](superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md) guarda el plan aprobado de las
  fases 0 y 1 y el plan ejecutable de la Fase 0.

### Proceso

- Cada documento lo escribió un subagente y lo revisó otro.
- Los documentos derivados se escribieron en paralelo y se conciliaron entre sí.
- Una verificación automática comprueba que:
  - los IDs, las secciones, los objetos, los roles y los enlaces sean coherentes;
  - no aparezca ningún nombre real de las fuentes;
  - no se versione ningún insumo transitorio.
- La Fase 1 empieza solo después del checkpoint con el usuario.

### Checkpoint

El 2026-10-07 el usuario aprobó los documentos y autorizó F1, con todos los valores por defecto del diseño. También pidió
abrir los 18 issues upstream, preparar la BD de desarrollo en el VPS y hacer el spike de entregables y GetMap. Las respuestas
están en [`roadmap.md`](roadmap.md) ("Respuestas del checkpoint").
