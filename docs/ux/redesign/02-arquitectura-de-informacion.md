# Arquitectura de información, navegación y flujos (etapa B)

> Rediseño integral de UX/UI, entregables 2 y 5. Fecha: 2026-10-20. Propuesta, pendiente de revisión.
> Parte de [`docs/ux/ux-architecture.md`](../ux-architecture.md), que sigue vigente: aquí solo se dice qué cambia y por
> qué. Los hallazgos `U-NN` están en [01-auditoria](01-auditoria.md) y las decisiones `DD-NN`, en
> [07-decisiones](07-decisiones-y-compatibilidad.md).

## 1. Qué se conserva

- Los **tres contextos** (global, proyecto y territorio) y la regla de que global y proyecto son la misma página con
  otro alcance (ASM-076).
- Las **13 secciones en orden fijo** (RNF-USA-001), filtradas por permisos.
- Las **rutas**: ninguna cambia ([07 §3](07-decisiones-y-compatibilidad.md#3-compromisos-de-compatibilidad)).
- La **profundidad**: lista o mapa → ficha → drawer o diálogo, sin modales apilados.
- La **ficha con una sola URL** y su apertura por `/r/:object/:recordId`.

## 2. Mapa de patrones

Cada pantalla del inventario S01–S64 usa uno de nueve patrones. Los ocho primeros se prototipan
([04-prototipos](04-prototipos/README.md)); el noveno, la ficha de un registro, comparte cabecera y panel con P-03.

| Patrón | Para qué | Pantallas | Pregunta que responde primero |
|---|---|---|---|
| **P-01 Inicio «Mi trabajo»** | Empezar el día | S02 | ¿Qué me toca hoy y qué espera mi decisión? |
| **P-02 Listado con filtros** | Encontrar y elegir | S06, S16, S22, S28, S35, S38, S42, S45, S47 | ¿Cuáles de estos me importan ahora? |
| **P-03 Ficha 360 del proyecto** | Entender un proyecto | S08 y su sub-navegación | ¿Cómo está el proyecto y qué lo amenaza? |
| **P-04 Formulario complejo** | Registrar con calidad | S07, S20, S24, S30, S37, S44 | ¿Qué falta para registrar y qué pasará? |
| **P-05 Bandeja de trabajo** | Resolver en serie | S04, S47, S48, S52 | ¿Qué decido ahora, con qué contexto? |
| **P-06 Visualización geográfica** | Explorar el territorio | S09, S12, S13, S14 | ¿Qué hay aquí y qué pasa en este lugar? |
| **P-07 Tablero ejecutivo** | Leer resultados | S51, S27, S34, S62 y los tableros | ¿Qué cambió, qué es crítico y quién lo atiende? |
| **P-08 Administración** | Configurar sin romper | S11, S53 y `/admin` | ¿Quién trabaja en qué, y qué cambia si toco esto? |
| **P-09 Ficha de registro** | Avanzar un registro | S17, S23, S29, S36, S40, S43, S44, S46, S57 | ¿En qué estado está y qué sigue? |

## 3. Shell

### 3.1 Barra superior (56 px)

| Hoy | Propuesta | Por qué |
|---|---|---|
| Marca + lema, proyecto (select nativo), Buscar, Preguntar, campana, idioma, tema, cuenta, salir (9) | Marca, proyecto (combobox con búsqueda y «Todos mis proyectos»), Buscar ⌘K, Preguntar, campana y cuenta (6). Idioma, tema, preferencias y salida van al menú de la cuenta. El lema solo a ≥ 1440 px. | U-12; `ux-architecture.md` §7.1 ya lo pide. |

### 3.2 Navegación principal

- **Expandida desde `lg`** (1024 px), con «Contraer» persistente por persona (DD-10). Contraída, cada ícono muestra un
  rótulo emergente con el nombre completo.
- **Títulos de bloque visibles** («Proyectos y territorio», «Trabajo con las comunidades», «Riesgos e inversión
  social», «Documentos y seguimiento»), como ya los define §7.1. Hoy solo existen para lectores de pantalla.
- **Nombre completo de QCS** en la barra: «Quejas y solicitudes». La sigla queda en la pestaña del proyecto.
- **Insignias**: solo lo que pide acción, en rojo (`Insignia` con tono `accion`). Los totales grises no van en la barra
  global.

### 3.3 Sub-navegación del proyecto: una sola fila (DD-03)

```text
Proyectos / DEMO-T1 · Proyecto Wayra Ejemplo                                ● Borrador  [Territorio: Huaylla]
Resumen  Mapa  Grupos de interés 85  Relacionamiento 100  Compromisos 12 ❶  QCS 11 ❸  Riesgos 5  Programas 1  Más ▾
──────────────────────────────────────────────────────────────────────────────────────────────────────────────────
[Cabecera de la página: título · conteo · Lista|Tablero · acción principal]
[Barra de filtros: Vista ▾ (Abiertos 11 · Vencidos 0 · Míos 0 …) · búsqueda · 3 filtros · Más filtros · chips · Limpiar]
```

- La **vista del módulo** (hoy, una segunda fila de pestañas con conteos) pasa a ser el primer control de la barra de
  filtros: un `SelectorDeVista` que muestra hasta cuatro vistas en segmentos y el resto en un menú, cada una con su
  conteo. La URL sigue siendo `?view=…`.
- **«Compromisos / Acuerdos»** (hoy chips sobre la cabecera) también entra al selector de vista.
- **Riesgos**: Registro y matriz · Revisión pendiente · Alerta temprana · Señales · Menciones · Impactos · Reglas, todo
  en el mismo selector (U-20).
- **La pestaña del registro abierto siempre queda marcada**, también en `grievances`, `requests` y `contributions`. Las
  migas dicen su nombre («Quejas y solicitudes»), no el segmento (U-04; tarea 1 del plan).
- **Desborde de la tira**: la pestaña activa nunca queda oculta. Si no cabe, entra a la tira en lugar de la última
  visible y esta pasa a «Más» (U-19).

### 3.4 Cabecera de página

Una sola pieza (`EncabezadoDePagina`, ya existente) para toda pantalla. Las 20+ páginas que hoy escriben su propio `<h1>`
pasan a usarla (U-34).

```text
[antetítulo: tipo · código · categoría — sans, gris]
Título que dice qué es                                     [secundaria] [secundaria] [⋯ Más] [Primaria]
[chips de situación: plazo, vigencia, revisión]  [conteo o metadatos]
```

## 4. Inicio «Mi trabajo» por capacidades (P-01, DD-01)

Hoy `InicioPage` muestra la revisión ejecutiva (S51) a toda cuenta si el catálogo del tenant ofrece KPI-EXE, y «Mi día»
solo si no (`pages/InicioPage.tsx:22-30`). La propuesta invierte el orden: **«Mi trabajo» para todos**, con la visión
ejecutiva como un bloque más, ofrecido a quien puede leerla. Todos los bloques ya existen o se arman con consultas
existentes:

| Orden | Bloque | Se ofrece si la cuenta… | Fuente (existente) | Responde |
|---|---|---|---|---|
| 1 | **Lo que me toca hoy**: vencido y por vencer a mi cargo, máximo 5 con su motivo y fecha, y «Ver todo» | lee `task`, `commitment` o la QCS que atiende | `AtencionRequerida`, `useSuTrabajo` | ¿Qué requiere mi atención? |
| 2 | **Esperan mi decisión**: aprobaciones y alertas por triar | recibe avisos de aprobación o puede confirmar alertas | avisos `ACTION` (S48), `early_warning` | ¿Qué decido hoy? |
| 3 | **Agenda**: interacciones y visitas de los próximos 7 días donde soy responsable | lee `engagement` o `field_visit` | consulta de lista con filtro | ¿Con quién me reúno? |
| 4 | **Para tomar**: tareas sin asignar de mis proyectos | lee `task` y tiene asignación vigente | lista de `task` (hoy solo dentro de cada proyecto, U-13) | ¿Qué puedo tomar? |
| 5 | **Atajos**: las creaciones que la cuenta puede hacer | `can(obj, 'CREATE')` | `Atajos`, `useAtajos` | ¿Cómo registro lo de siempre? |
| 6 | **Mis proyectos**, con lo que pide acción en cada uno | tiene asignaciones o `READ_ALL_PROJECTS` | `MisProyectos` | ¿Qué proyectos puedo consultar? |
| 7 | **Visión ejecutiva**: solo las cifras de KPI-EXE que la cuenta puede leer (`available`), con fecha de cálculo y enlace a la revisión completa | lee al menos una cifra ejecutiva | `useCatalogoDeIndicadores` y los KPI ya leídos | ¿Qué cambió y qué es crítico? |

Reglas del inicio:

- **Un bloque sin datos legibles no se dibuja.** Nunca un muro de «No disponible». Si una cifra suelta no está
  disponible, va con su candado, como hoy.
- **Ningún bloque cuenta lo que la cuenta no lee.** Cada bloque se arma con lo que la API devuelve a quien llama
  (ADR-0029).
- **Orden por urgencia, no por módulo:** vencido > por vencer > espera decisión > agenda > resto.
- **Sin cifras decorativas.** Las cifras del bloque 7 llevan periodo y fecha de cálculo.
- **El ejecutivo puro** (sin tareas ni decisiones a su cargo) ve primero el bloque 7, que se expande a la revisión
  compacta. Es la misma regla de orden: es lo único que tiene.
- **Primera vez sin asignaciones:** la bienvenida con el mapa de módulos y «A quién pedir acceso» (F1, como hoy).

## 5. Navegación entre listas y fichas

- **Vista previa** (`?peek=`): el ojo sin rótulo pasa a ser un botón «Vista previa» de 24 px con su nombre accesible,
  visible al pasar sobre la fila o con el foco en ella (U-17). Enter abre la ficha y Espacio, la vista previa.
- **Volver a la lista**: la ficha guarda la URL de origen (filtros incluidos) y la ofrece como primera miga y como
  «‹ Volver a Compromisos (11)» sobre el antetítulo. Funciona igual desde un aviso, la búsqueda o una tarea.
- **Anterior y siguiente** en la ficha, dentro del resultado filtrado de origen (`J`/`K` con el teclado). Evita el ir y
  volver al revisar una serie de QCS o de aportes. Se ofrece solo si la ficha se abrió desde una lista.

## 6. Flujos prioritarios rehechos

Pasos contados como clics o pulsaciones desde el punto de partida, en escritorio, con los datos de la demo. «Hoy» se
contó sobre las capturas; «Propuesto» se contó sobre los prototipos.

### F-A · Empezar el día (todas las personas)

| | Hoy | Propuesto |
|---|---|---|
| Agente de campo: llegar a la tarea que vence | Inicio (tablero con candados) → Tareas → fila (**2 clics y una búsqueda visual**) | Inicio → «Lo que me toca hoy» → la tarea (**1 clic**) |
| Gestión de QCS: la QCS cuyo plazo vence primero | Inicio → QCS → elegir proyecto → ordenar → fila (**4**) | Inicio → primera fila de «Lo que me toca hoy» (**1**) |

### F-B · Avanzar un registro (compromiso, QCS, riesgo)

1. Ficha → la **primaria es la siguiente acción** («Registrar cumplimiento»). Si está bloqueada, dice qué falta («Falta
   una entrega con conformidad») con el enlace que lo resuelve («Registrar entrega»).
2. Diálogo de transición (se conserva): «Qué pasará», reglas y motivo → Confirmar.
3. Al volver, la ficha muestra el nuevo estado y la nueva siguiente acción; la lista de origen sigue a un clic.

Hoy el botón verde es «Bloquear» (U-02): el riesgo de error desaparece y el número de pasos no cambia.

### F-C · Encontrar y filtrar

| | Hoy | Propuesto |
|---|---|---|
| «Compromisos vencidos de la junta de usuarios» | Pestaña Compromisos → pestaña de vista «Vencidos» → buscar código o título (no hay filtro por grupo de interés) | Compromisos → Vista «Vencidos» → «Más filtros» → Grupo de interés → chip aplicado; la URL se comparte |
| Volver desde la ficha | Atrás del navegador (pierde el desplazamiento) | «‹ Volver a Compromisos (3)» con los filtros intactos |

### F-D · Registrar en campo (interacción y QCS)

- Interacción: los 5 pasos se conservan. El paso 1 se reduce a tipo, título, fecha y hora (`CampoFecha` en 24 h) y «Ya se
  realizó», que se convierte en un selector de dos opciones con su efecto explicado («Se registrará como realizada; podrá
  redactar el acta después»).
- QCS: pasa al mismo asistente en 3 pasos (Qué y quién · Cómo llegó · Revisión), con «Qué pasará al registrar» como panel
  lateral fijo en todos los pasos (U-30).
- Modo rápido de campo: deja de ser un enlace suelto y pasa a un conmutador de la cabecera del asistente.

### F-E · Aprobar (P-05)

| | Hoy | Propuesto |
|---|---|---|
| Decidir un aporte | Bandeja (código, «Revisar») → abrir el aporte → leer ítems y lineamientos → volver → decidir en la ficha (**4+**) | Bandeja con panel de detalle: ítems, monto por moneda, «Antes de aprobar» (lineamientos) y tope de campaña a la vista; Aprobar / Observar / Rechazar con motivo en el mismo panel (**2**) |

La segregación de funciones no cambia: el panel solo ofrece las decisiones que el servidor permite (transiciones
disponibles).

### F-F · Leer el territorio (P-06)

Mapa a pantalla completa → elegir un lugar (en el mapa o en la lista) → panel del lugar con sus registros por módulo,
cada uno con conteo legible → abrir el registro. El panel de capas se abre con «Capas (3)» y se cierra sin perder lo
elegido.

### F-G · Administrar sin romper el modelo (P-08)

- **Equipo del proyecto**: «Terminar asignación» sale de cada fila y pasa al menú de la fila, con confirmación que
  nombra a la persona y el efecto («Perderá el acceso a este proyecto el 20/10/2026»).
- **Pantallas de `/admin` que editan el modelo** (objetos, workflows, roles): aviso fijo «Este cambio no queda en
  `model/`: se pierde en la próxima subida de fase. Cámbielo en el repositorio» (GAP-030). Es un cambio de
  `@wasichai/*` o un tema de SGSPE ([08 §3](08-cambios-de-contrato-backend.md)).

## 7. Responsive

| Ancho | Shell | Listados | Ficha | Mapa |
|---|---|---|---|---|
| ≥ 1440 | Barra lateral 240 px; drawer al lado del contenido | Todas las columnas esenciales y secundarias | Cuerpo + panel de trazabilidad a la derecha | Mapa + paneles superpuestos |
| 1024–1439 | Barra lateral 240 px (contraíble); drawer superpuesto | Columnas secundarias en «Columnas» | Trazabilidad debajo del panel de decisión | Igual |
| 768–1023 | Menú en panel superpuesto | Esenciales; el resto, en la vista previa | Una columna; acciones en barra fija inferior | Lista como hoja inferior |
| < 768 | Barra inferior de 4 destinos (§7.1) | `ListaEnTarjetas` | Barra fija de acciones (ya existe: `e2e/responsivo.spec.ts`) | Mapa y lista alternables |
