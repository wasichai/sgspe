# Auditoría UX del portal y de la administración (etapa A)

> Rediseño integral de UX/UI, entregable 1. Fecha: 2026-10-20. Estado: propuesta, pendiente de revisión.
> Índice del rediseño: [README](README.md).

## 1. Alcance, fuentes y método

**Qué se auditó.** El portal (`/`) y la administración (`/admin`) de `sgspe-ui` en `claude/wonderful-rubin-02oanj`
(commit `13cf03d`, el mismo código que la versión 3.0.0 más la sincronización de diseño, que no toca pantallas).

**Fuentes de evidencia**, en orden de peso:

1. **Capturas reales**: las 56 de `docs/manuals/assets/screenshots/` (42 de usuario y 14 de administración), tomadas
   hoy por el script de los manuales sobre el conjunto ficticio de demostración (`model/demo/`). Son 1440 × 1000 px en
   Chromium, con la configuración regional del navegador en inglés de EE. UU. Muestran el producto tal como se entrega,
   con siete perfiles distintos: gestión social, campo, QCS, riesgos, inversión social, aprobación y administración.
2. **Código del portal**: rutas, componentes y textos. Cada hallazgo de código cita `archivo:línea`.
3. **Diseño vigente**: `docs/ux/` (arquitectura, design system, pantallas S01–S64 y flujos 1 a 18). Sirve para
   distinguir un defecto de implementación de una decisión de diseño.

**Límites.** No se recorrió la aplicación viva contra un backend: el contenedor de esta sesión no tiene la base ni los
servicios. No hay mediciones con personas usuarias (tiempos, errores, entrevistas). Por eso cada hallazgo lleva un
**estado**:

- **C** (confirmado): visible en una captura o demostrable en el código.
- **H** (hipótesis): inferido; requiere validarse con uso real.

Ningún problema se dio por cierto sin haberlo visto.

**Prioridad.** Combina tres ejes, cada uno alto o medio:

- impacto en la tarea (¿impide, retrasa o confunde?);
- frecuencia (¿cuántas personas y cuántas veces al día?);
- riesgo de error (¿lleva a una acción equivocada o a revelar algo que no se debía?).

| Prioridad | Regla |
|---|---|
| **P1** | Alto en dos ejes |
| **P2** | Alto en uno |
| **P3** | Medio en todos |

## 2. Resumen ejecutivo

El portal es **sólido en lo esencial** y su base es buena, así que conviene conservarla. Ya tiene:

- Un sistema de tokens coherente («Pino y piedra»).
- Fichas con «Estado actual / Siguiente acción / Responsable / Vencimiento».
- Trazabilidad «De dónde viene / Qué generó / Lo sustenta».
- Transiciones con «Qué pasará» y motivo obligatorio.
- Formularios largos en pasos.
- Estados de confidencialidad bien resueltos, como «No disponible» con candado y «Registro reservado».
- Búsqueda ⌘K agrupada.
- Navegación filtrada por permisos.

Los problemas no están en las piezas, sino en **cómo se componen**. Los diez de mayor impacto:

| # | Problema | Prioridad |
|---|---|---|
| 1 | **El inicio no responde a la persona.** Gestión social y el agente de campo ven la misma «Revisión ejecutiva». Al agente le muestra 7 de 10 cifras «No disponible» y no le muestra sus tareas ni sus visitas (U-01). | P1 |
| 2 | **La acción principal contradice a la «Siguiente acción».** En la ficha de compromiso la primaria verde es «Bloquear», mientras el panel dice «Siguiente acción: Registrar cumplimiento». En la de riesgo la primaria es «Nueva revisión» y la siguiente es «Decidir mitigar» (U-02). | P1 |
| 3 | **Cabeceras de ficha con 4 a 6 acciones del mismo peso**, con destructivas en rojo contiguas a la primaria («Cancelar el compromiso», «Retirar el grupo de interés», «Anular el entregable») (U-03). | P1 |
| 4 | **Migas y pestaña con nombres técnicos en inglés** en tres fichas («grievances», «requests», «contributions»), sin pestaña marcada (U-04). | P1 |
| 5 | **Muros de enlaces** en el resumen del proyecto y en Relacionamiento: listas largas de títulos subrayados («16 actas por redactar», «85 grupos sin contacto», «55 evaluaciones») que compiten entre sí (U-05). | P2 |
| 6 | **Listados que desbordan a 1440 px**: la tabla de grupos de interés se corta a la derecha, los títulos envuelven en 3–4 líneas y hay dos filas de pestañas sobre cinco filtros (U-06). | P2 |
| 7 | **Fechas en formato del navegador** (`mm/dd/yyyy`, 12 h AM/PM) en 29 campos nativos dentro de una interfaz es-PE, mientras el resto del portal dice «20 oct. 2026» y la administración «20/10/2026» (U-07). | P2 |
| 8 | **La administración es otro producto**: otro shell, otro lema («Plataforma de aplicaciones geoespaciales configurables»), códigos de rol en mayúsculas, verbos en inglés (READ, CREATE) y otro formato de fecha (U-08). | P2 |
| 9 | **Estados de sistema mudos.** «Sin acceso» es una sola línea sin salida. Las acciones deshabilitadas no dicen por qué. «Vencidas 0» con una tarea vencida a la vista (U-09). | P2 |
| 10 | **El mapa no domina la pantalla territorial.** El lienzo ocupa ~45 % del ancho, entre un panel de capas largo y una lista. Sin mapa base configurado, el fondo es gris plano (U-10). | P2 |

## 3. Hallazgos por tema

Columnas: **Ev.** = evidencia (`c.NN` es la captura de usuario `user/NN-*.png`; `a.NN`, la de administración
`admin/NN-*.png`; los archivos citan el código); **P** = prioridad; **E** = estado.

### 3.1 Inicio y orientación

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-01 | El inicio muestra la «Revisión ejecutiva» (S51) a toda cuenta cuando el catálogo del tenant ofrece algún indicador ejecutivo, sin mirar si esa cuenta puede leerlo. El agente de campo ve 7 tarjetas «No disponible», una tabla de candados y ni sus tareas, ni sus visitas, ni sus atajos. «Mi día», que existe, nunca se ve si hay KPI-EXE. `INICIOS = []`: ninguna variante por perfil registrada. | c.02, c.41; `pages/InicioPage.tsx:22-30`, `inicios.ts` | P1 | C |
| U-11 | La revisión ejecutiva abre con filtros (dos fechas nativas, ámbito, «Actualizar») y un desplegable críptico «Proyectos de la tabla (0 elegidos)» antes de cualquier dato. Las 8 cifras van en dos filas iguales, varias en 0. «Proyectos activos 0» convive con un proyecto en Borrador que sí aparece en «Lo que necesita atención». | c.02 | P2 | C |
| U-12 | La barra superior tiene 9 controles: marca con lema, selector de proyecto nativo, Buscar, Preguntar, campana, idioma (select nativo), tema, cuenta y salir. Idioma y tema están en la barra, aunque `ux-architecture.md` §7.1 los ubica en el menú de perfil («nada de eso va suelto en la barra»). | c.03 | P3 | C |
| U-13 | Las tareas «para tomar» (la cola compartida) no se ven en «Mis tareas». Una nota al pie pide elegir un proyecto en el selector: la persona debe recordar dónde está su trabajo. | c.32 | P2 | C |
| U-14 | El rastro de lo visitado y las migas existen, pero en las fichas largas la cabecera y la pestaña se pierden al desplazar: no hay cabecera fija con el título y la acción. | c.17, c.25 | P3 | H |

### 3.2 Acciones y jerarquía

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-02 | La primaria no es la siguiente acción. Compromiso: primaria «Bloquear», siguiente «Registrar cumplimiento» (deshabilitada y gris). Riesgo: primaria «Nueva revisión», siguiente «Decidir mitigar». En el aporte sí coinciden, así que es una regla que no se aplica en todas partes, no un caso aislado. Quien actúa por el botón verde bloquea por error. | c.17, c.25, c.29, a.04 | P1 | C |
| U-03 | Cabeceras con 4–6 botones de peso similar más uno destructivo contiguo: compromiso (Ver en el mapa, Editar, Informar riesgo, Bloquear, Registrar cumplimiento, Cancelar el compromiso), grupo de interés (Nueva evaluación + Retirar en rojo), entregable (Enviar a revisión + Anular). En código: `TerritorioPage.tsx:151-187` tiene 8 botones; `EditorDeActaPage.tsx` tiene 12 con varias primarias; `ImportacionPage.tsx` tiene 11. | c.10, c.17, c.36, a.04 | P1 | C |
| U-15 | Hay primarias deshabilitadas sin el porqué junto al botón: «Resolver», «Declinar», «Registrar cumplimiento», «Cerrar sin acta», «Decidir monitorear». El motivo vive en otro panel («Para Resolver falta Resolución») o en ninguna parte. | c.14, c.21, c.23, c.25 | P2 | C |
| U-16 | Varias primarias en una pantalla: Exportaciones (una por tarjeta, 3+), el entregable («Enviar a revisión» y «Pedir borrador»), el equipo del proyecto (10 «Terminar asignación» rojos a la vista). | c.35, c.36, a.01 | P2 | C |
| U-17 | Íconos sin rótulo con función propia: el ojo de vista previa junto a cada título, «…» junto a «Identificar riesgo», lápiz y papelera en la administración. | c.08, c.16, c.24, a.06 | P3 | C |

### 3.3 Navegación y contexto

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-04 | Migas con el segmento de la ruta («grievances», «requests», «contributions») y sin pestaña marcada en esas fichas. `ProyectoLayout.tsx:74,88` pasa el segmento crudo como `pestana`, así que `MigasDelProyecto` (`Migas.tsx:75`) nunca usa `PESTANA_DE_SEGMENTO`. | c.21, c.23, c.29 | P1 | C |
| U-18 | Tres mecanismos de «vista» que se parecen y no son lo mismo: pestañas del proyecto, pestañas de vistas guardadas (con conteos) y conmutadores (Lista/Tablero, Mes/Semana/Lista). En Grupos de interés y Compromisos se apilan dos filas de pestañas. | c.08, c.12, c.16 | P2 | C |
| U-19 | La tira de pestañas del proyecto se corta: «Documentos» pasa a «D…» o «Docume >» cuando aparece «Equipo y parámetros» o «Reportes». La pestaña activa puede quedar fuera de la vista. | c.35, a.01 | P2 | C |
| U-20 | Alerta temprana, Menciones, Señales e Impactos viven bajo la pestaña «Riesgos» sin subnavegación visible: la página dice «Alerta temprana» y la pestaña, «Riesgos». | c.27 | P3 | C |
| U-21 | Por debajo de 1280 px la barra lateral muestra solo íconos para 13 secciones. La etiqueta «QCS» es una sigla que una persona nueva no entiende. | `shell/AppShell.tsx`, `navTree.ts:72` | P2 | C |

### 3.4 Listados, tablas y filtros

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-06 | La tabla de grupos de interés (11 columnas) se corta a la derecha a 1440 px. Los títulos de interacción, QCS y compromiso envuelven en 3–4 líneas con «…». La columna «Responsable» repite el mismo valor en cada fila. | c.08, c.12, c.16, c.20 | P2 | C |
| U-22 | Los filtros son `select` nativos uno tras otro, sin chips de filtros aplicados ni «Limpiar». La búsqueda ocupa un campo más del mismo peso. En Avisos el filtro «Tipo» mide 860 px. | c.08, c.16, c.20, c.40 | P2 | C |
| U-23 | La columna «Plazo» está vacía en todos los compromisos abiertos de la demo, y «Vencidas 0» aparece en «Mis tareas» con una tarea que venció el 17 de octubre. La interfaz muestra una cifra que contradice lo que se ve. Posible causa: JOB-01 no corrió sobre la demo. | c.16, c.32 | P2 | H |
| U-24 | Las KPI de lista (QCS: «0 / 4 · %») y algunos rótulos («Quién la plantea: Identificada») no se entienden sin conocer el modelo. | c.20 | P3 | C |

### 3.5 Fichas

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-25 | El estado se repite tres veces (chip bajo el título, camino del flujo y panel «Estado actual»), y «Desde —» queda vacío en las fichas de demo. | c.14, c.17, c.21 | P2 | C |
| U-26 | Los atributos se listan en dos columnas sin agrupar: 20+ pares etiqueta/valor, con muchos «—» (Resolución, Resuelta, Respuesta, Respuesta comunicada) a la vista antes de que tengan sentido. | c.17, c.21, c.23, c.29 | P2 | C |
| U-27 | Hay títulos que son códigos («Queja DEMO-Q-03», «Aporte DEMO-AP-02») mientras lo que se planteó va más abajo. Los antetítulos van en tipografía mono («Compromiso · DEMO-C-03 · No material»), como si fueran código. | c.21, c.29, c.10 | P3 | C |
| U-28 | Los niveles del riesgo («Alto · 3 × 4 = 12 · Medio») van sin rótulo de inherente y residual. Siglas sin explicar: AIIS, AIDS. | c.25, c.10 | P3 | C |

### 3.6 Formularios

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-07 | Hay 29 campos `date`/`datetime-local` nativos que se dibujan con la configuración regional del navegador (`mm/dd/yyyy`, `--:-- --` AM/PM) dentro de una interfaz es-PE. En navegadores en español se verá `dd/mm/aaaa`, pero el portal no lo controla. | c.02, c.11, c.15, c.18, c.22, a.14 | P2 | C |
| U-29 | Algunos formularios densos parecen hojas de cálculo. La nueva evaluación tiene 15 escalas de radio en un drawer de 400 px. Registrar interacción tiene 16 campos en una sola pantalla. En código, `NuevoCompromisoPage.tsx` tiene 685 líneas y `ia/AceptarSugerencia.tsx`, `compromisos/Candidatos.tsx` y `atencion/FichaConsultaPage.tsx` tienen 12 campos cada uno. | c.11; código | P2 | C |
| U-30 | El registro de una QCS es un formulario único y largo, mientras compromiso, interacción y proyecto usan pasos: el mismo tipo de tarea tiene dos patrones distintos. | c.22 vs c.15, c.18, a.03 | P3 | C |
| U-31 | En Parámetros, cada campo numérico queda ~700 px a la derecha de su etiqueta, con una explicación larga en gris pequeño debajo. | a.02 | P3 | C |

### 3.7 Estados del sistema

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-09 | «Sin acceso» es una línea en color de advertencia («Su cuenta no puede abrir «Compromisos».») sin explicación, sin a quién pedir el acceso y sin salida. | c.42 | P2 | C |
| U-32 | Sin mapa base, el mapa es un fondo gris con cuadros. El aviso de servicio caído con «Reintentar» está bien, pero nada dice cómo configurar el mapa base. | c.37, c.38 | P2 | C |
| U-33 | Bien resueltos y a conservar: «No disponible» con candado y su motivo, «Registro reservado», `NotaDeConfidenciales` sin conteo, el error de carga con «Reintentar» y «Los filtros se conservan», el borrador guardado al pasar de paso. | c.02, c.41; `componentes/Estados.tsx` | — | C |

### 3.8 Densidad y composición

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-05 | Hay muros de enlaces. «Atención requerida» del proyecto lista 5 títulos de cada uno de 3 grupos en tres columnas, y «Te toca» de Relacionamiento repite la lista de actas. Todo subrayado y del mismo peso. | c.07, c.12, c.13 | P2 | C |
| U-34 | Las tarjetas se escriben a mano: el patrón `rounded-sg-lg border … bg-sg-surface p-4` aparece 136 veces sin componente, con riesgo de tarjetas dentro de tarjetas y espaciados distintos. Hay más de 20 `*Page.tsx` sin `EncabezadoDePagina`, entre ellas `AprobacionesPage.tsx:80`, `RevisionEjecutivaPage.tsx:39`, `IncidentesPage.tsx:46`, Reglas, Señales, Impactos, Menciones y Bandeja de IA. | código | P2 | C |
| U-35 | Los paneles de resumen de Programas muestran barras vacías y «S/ 0.00» antes de la lista. Hay dos listados completos, Programas y Aportes, cada uno con sus pestañas, en la misma página. | c.28 | P3 | C |
| U-36 | Los textos explicativos usan jerga de implementación: «Las aprobaciones son pasos del flujo, no objetos: esta bandeja sale de los avisos…», «Proyecto: …, el de la barra superior». | c.33, c.34 | P3 | C |

### 3.9 Mapa y territorio

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-10 | El lienzo del mapa ocupa ~45 % del ancho entre un panel de capas de 30 entradas con candados y la lista de lugares. Arriba hay cuatro acciones secundarias y ninguna primaria. Capas y leyenda son dos paneles apilados que repiten colores. | c.37, c.38 | P2 | C |

### 3.10 Administración frente al portal

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-08 | `/admin` es `WasichaiApp` y se ve como otro producto. Cambia el shell (sin barra superior, pie con usuario, tema «Sistema» y «EN»), el lema («Plataforma de aplicaciones geoespaciales configurables», el de wasichai) y el inicio (90 tarjetas de objetos). Muestra códigos de rol (`STAKEHOLDER_MANAGER`), verbos en inglés (`READ`, `MANAGE_METADATA`) y UUID partidos en 4 líneas en la auditoría. Las fechas van en `20/10/2026` y hay ítems de menú deshabilitados sin explicación. | a.05 a a.13 | P2 | C |
| U-37 | La administración permite editar objetos y workflows, que según `GAP-030` no son la fuente de verdad (lo son `model/*.json`), sin advertirlo en la pantalla. «Eliminar flujo» en rojo sólido junto a «Guardar flujo». | a.10, a.12 | P1 | C |
| U-38 | En Capas GIS, 13 botones «Publicar» deshabilitados repiten lo que ya dice el aviso único de GeoServer. | a.13 | P3 | C |

## 4. Lo que hay que conservar

- **Tokens y tipografía «Pino y piedra»**: buen contraste, un único acento y semánticos bien separados (IA en violeta,
  territorio en arena).
- **El panel de decisión de la ficha** (`PanelDeDecision`): las cuatro preguntas son el mejor patrón del portal; solo
  hay que alinear la acción primaria con él.
- **El diálogo de transición** con «Qué pasará», reglas cumplidas y motivo obligatorio (c.19, c.26).
- **La trazabilidad** «De dónde viene / Qué generó / Lo sustenta».
- **Los asistentes por pasos**, con borrador local y «Guardar y salir».
- **«Qué pasará al registrar»** en el registro de una QCS (c.22): un patrón a extender a los demás formularios.
- **La búsqueda ⌘K agrupada** (c.05) y la **matriz interés × poder** con lista sincronizada y «Ver datos».
- **La confidencialidad en la interfaz**: «No disponible» con su motivo, «Registro reservado», campos restringidos con
  candado y navegación filtrada por permisos.

## 5. Respuestas a las preguntas del encargo

1. **Pantallas con mayor carga visual:**
   - el resumen del proyecto (c.07);
   - la lista de grupos de interés (c.08);
   - la ficha de compromiso (c.17);
   - la nueva evaluación (c.11);
   - el mapa territorial (c.37);
   - la revisión ejecutiva (c.02);
   - la matriz de permisos de la administración (a.08).
2. **Información que compite:**
   - los muros de enlaces (U-05);
   - el estado repetido tres veces en la ficha (U-25);
   - las cifras en 0 sobre las tareas (U-11).
3. **Flujos que obligan a recordar:**
   - las tareas para tomar viven en cada proyecto (U-13);
   - por qué una acción está deshabilitada (U-15);
   - desde dónde se llega a un registro de QCS o de aporte (U-04).
4. **Primarias que no destacan o engañan:** U-02, U-03 y U-16.
5. **Navegación y orientación:** U-04, U-18, U-19, U-20 y U-21.
6. **Formularios largos:** U-29 (evaluación, interacción, compromiso) y U-30.
7. **Jerarquía en filtros, tablas, fichas y paneles:** U-06, U-22, U-26 y U-27.
8. **Estados que no ayudan:**
   - sin acceso (U-09);
   - acciones deshabilitadas (U-15);
   - cifras que contradicen lo visible (U-23);
   - mapa sin fondo (U-32).
9. **Portal frente a administración:** U-08 y U-37.
10. **Simplificación sin perder capacidades:**
    - un inicio por perfil hecho con piezas que ya existen (U-01);
    - una regla de acción primaria (U-02, U-03);
    - un componente `Seccion` en lugar de tarjetas a mano (U-34);
    - filtros con chips;
    - fechas propias del portal;
    - una subnavegación de módulo que no se apile.

## 6. Flujos de mayor impacto

Ordenados por frecuencia × riesgo. Son los que se rediseñan primero y los que se prototipan:

1. **Empezar el día** (todas las personas): inicio → lo que me toca → abrir el registro. Hoy falla para campo, QCS,
   riesgos e inversión (U-01).
2. **Avanzar un registro** (gestión de compromisos, QCS, riesgos): ficha → siguiente acción → transición. Hoy la primaria
   engaña (U-02).
3. **Encontrar y filtrar** (todas): listado → filtros → ficha → volver. Hoy hay dos filas de pestañas, filtros nativos y
   tablas que desbordan (U-06, U-18, U-22).
4. **Registrar en campo** (agente y relacionista): registrar una interacción o una QCS con fechas y lugar (U-07, U-29,
   U-30).
5. **Aprobar** (aprobación): bandeja → contexto → decidir sin abrir tres pantallas (U-36).
6. **Leer el territorio** (gestión social, SIG): mapa → lugar → registros del lugar (U-10).
7. **Administrar sin romper el modelo** (administración): equipo, parámetros, usuarios y roles con advertencias de fuente
   de verdad (U-08, U-37).

## 7. Adenda: hallazgos encontrados al preparar el design system y los prototipos

Con ellos, la auditoría suma 43 hallazgos; los P1 siguen siendo cinco.

| ID | Hallazgo | Ev. | P | E |
|---|---|---|---|---|
| U-39 | `sgspe-ui/src/theme/tokens.css` no es copia de `docs/ux/tokens.css`, aunque `sgspe-ui/CLAUDE.md` pide copiarlo y verificarlo con `cmp`. Los valores son iguales; cambian el comentario y dos selectores del oscuro: la copia no tiene `:root[data-theme="sgspe-noche"]`, y su guarda del modo del sistema es `:not([data-theme="light"])` en lugar de `:not([data-theme])`. Es la brecha conocida UPS-19 («Según el sistema») sin cerrar en el código. | `diff docs/ux/tokens.css sgspe-ui/src/theme/tokens.css` | P3 | C |
| U-40 | El borde de los campos de formulario (`Input`, `Textarea` y `Select` de `@wasichai/ui` con `border-border`, que SGSPE asigna a `--sg-border`) contrasta 1.38:1 sobre la superficie en claro y 1.31:1 en oscuro, bajo el 3:1 que WCAG 1.4.11 pide para el límite de un control. Sobre el fondo `--sg-bg` el campo blanco apenas se distingue (1.08:1). | `contraste.py`; `wasichai.css:12`; `packages/ui/src/input.tsx:10` | P2 | C |
| U-41 | El texto de ejemplo de los campos usa `text-ink-muted/60`: unos 2.5:1. Los ejemplos que funcionan como pista («Correo de la persona», «Buscar interacción por título») quedan por debajo de 4.5:1. | `packages/ui/src/input.tsx:10`; c.11, c.18 | P3 | C |
| U-42 | Las etiquetas de los estados de workflow (`EstadoChip`, el camino del flujo, los filtros de estado) salen de la metadata del modelo (`model/workflows/*.json`, `label`), que solo está en español. Con la interfaz en inglés, los estados siguen en español («Solicitado», «En ejecución»). | `componentes/EstadoChip.tsx:11`; prototipo P05 en inglés | P3 | C |
| U-43 | Los tokens de datos, postura, riesgo y suelo del mapa (`--sg-data-*`, `--sg-stance-*`, `--sg-risk-*`, `--sg-map-ground`) no se exponen como utilidades de Tailwind en el `@theme inline` de `tokens.css`; las pantallas los usan por `var(--sg-…)` a mano o con colores sueltos. | `src/theme/tokens.css:175` | P3 | C |
