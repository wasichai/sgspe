# Dirección visual y design system (etapa C)

> Rediseño integral de UX/UI, entregable 3. Fecha: 2026-10-20. Propuesta, pendiente de revisión.
> Evoluciona [`docs/ux/design-system.md`](../design-system.md) y [`docs/ux/tokens.css`](../tokens.css) sin sustituirlos.
> El sistema sincronizado con Claude Design está en el proyecto «SGSPE Design System»
> (<https://claude.ai/design/p/3e0d73eb-0e55-42d1-8e7f-46566ea93a00>): 61 componentes reales del portal.

## 1. Dirección: «Pino y piedra», más quieta y más clara

La identidad no cambia: neutros de piedra, un solo acento pino (`#1D5C52`), arena solo para territorio, violeta solo
para IA, e IBM Plex Sans y Mono. Lo que cambia es **cuánto se usa cada cosa**:

| Principio | En la práctica |
|---|---|
| **El acento es una señal, no una decoración** | El verde pino solo aparece en la acción principal (una por contexto), el elemento de navegación activo y los enlaces dentro de texto. Las listas de registros no se subrayan ni se pintan de verde (U-05). |
| **Superficies en dos planos** | Fondo `--sg-bg` y superficie `--sg-surface`, sin terceros niveles de tarjeta. Lo que flota (menús, drawers, diálogos) usa `--sg-surface-raised` y sombra. Una `Seccion` dentro de otra pierde el borde (DD-08). |
| **Tipografía que ordena** | Título h1 de 24 px por pantalla; secciones en h3 de 16 px semibold. El mono solo para códigos, huellas y valores técnicos, nunca para antetítulos ni descripciones (U-27). Nada por debajo de 12 px. |
| **El color dice estado, el texto también** | Ningún estado depende solo del color: chip con ícono y etiqueta (se conserva, `design-system.md` §4.1). |
| **Datos sin adornos** | Barras y áreas de color sólido, sin trama diagonal. Ejes y rejillas en `--sg-border-subtle`. Toda serie con «Ver datos». |
| **Movimiento que informa** | 120 ms en hover y foco, 200 ms en drawers y diálogos (se conserva), y nada más. `prefers-reduced-motion` respetado. |

## 2. Tokens: evolución controlada (DD-15)

### 2.1 Se conserva todo lo existente

Los 52 colores, las 4 sombras, la escala tipográfica, los radios y los breakpoints de `docs/ux/tokens.css` no cambian
de nombre ni de valor. El contraste de los pares en uso se verificó con [`contraste.py`](contraste.py) (WCAG 2.2): los
25 pares pasan su mínimo en claro y en oscuro.

| Par | Claro | Oscuro | Mínimo |
|---|---|---|---|
| `text` / `bg` | 15.19 | 15.29 | 4.5 |
| `text-muted` / `surface-sunken` | 5.04 | 7.04 | 4.5 |
| `primary-text` / `primary-soft` | 6.59 | 6.72 | 4.5 |
| `on-primary` / `primary-hover` | 10.04 | 4.72 | 4.5 |
| `territory` / `territory-soft` | 5.12 | 7.83 | 4.5 |
| `focus` / `bg` | 4.81 | 8.06 | 3.0 |
| `border-strong` / `surface` | 3.50 | 3.03 | 3.0 |
| `primary` / `surface` | 7.76 | 3.09 | 3.0 |

La tabla completa sale de `python3 docs/ux/redesign/contraste.py`.

### 2.2 Tokens nuevos

| Token | Claro | Oscuro | Uso | Resuelve |
|---|---|---|---|---|
| `--sg-border-control` | `var(--sg-border-strong)` (#838b85, 3.50:1) | `#66726c` (3.41:1; 3.16:1 sobre `--sg-surface-raised`, DD-21) | Límite de `Input`, `Textarea`, `Select`, `CampoFecha` y casillas | U-40: hoy 1.38:1 |
| `--sg-surface-raised` | `#ffffff` | `#1d2420` | Menús, popovers, drawers y diálogos | En oscuro, la sombra no separa lo que flota |

Se aplican así:

- en `sgspe-ui/src/index.css`, una regla sin capa para los campos de `@wasichai/ui` por su `data-slot` (`input`,
  `textarea`, `native-select` y `select-trigger`) con `border-color: var(--sg-border-control)`, salvo el inválido, que
  conserva su borde `danger`; también alcanza `/admin`. Los campos propios del portal ya usan `border-sg-border-strong`;
- en lo que flota del portal (`Drawer`, el menú de `MasAcciones`, `DialogContent` y el popover de `CampoFecha`), la clase
  `bg-sg-surface-raised`. wasichai no tiene un token de superficie flotante; pedirlo es el ítem W-06 de
  [08](08-cambios-de-contrato-backend.md);
- en el texto de ejemplo, `color: var(--sg-text-muted)` sin opacidad (5.78:1; U-41).

Los dos tokens se agregan primero en `docs/ux/tokens.css` y se copian a `sgspe-ui` con `cmp`. La copia arregla además la
deriva U-39.

### 2.3 Reglas de espaciado (escala de 4 px de Tailwind)

| Contexto | Valor |
|---|---|
| Dentro de un control | `px-3`, alto según §4 |
| Entre etiqueta y control | `space-y-1` (4 px) |
| Entre campos de un grupo | `space-y-4` (16 px) |
| Entre grupos de un formulario | `space-y-6` (24 px) con título h3 |
| Relleno de una `Seccion` | `p-4` (16 px); `p-5` a ≥ 1440 px |
| Entre secciones de una página | `space-y-4` |
| Cabecera de página a contenido | 16 px |
| Celdas de tabla | 44 px de alto por defecto; 32 px en la densidad compacta de los tableros |

## 3. Tipografía

La escala `--text-sg-*` se conserva. Estas son las reglas de uso que hoy no se cumplen:

| Rol | Token | Regla |
|---|---|---|
| Título de pantalla | `h1` 24/32 semibold | Uno por pantalla, por `EncabezadoDePagina` |
| Título de ficha | `h1` | Dice qué es («Mejorar el acceso a la posta»); el código va al antetítulo |
| Antetítulo | `caption` 12/16, sans, `text-muted` | «Compromiso · COM-0142 · Infraestructura»; el código puede ir en mono dentro del antetítulo, nunca toda la línea |
| Título de sección | `h3` 16/24 semibold | Dentro de `Seccion` |
| Rótulo de campo | `label` 13/16 medium | Persistente, sobre el control |
| Texto | `body` 14/20 | Por defecto |
| Metadato | `body-sm` 13/18, `text-secondary` | Fechas y responsables en listas |
| Overline | `overline` 11/16 | Solo encabezados de tabla y rótulos de cifras; nada que haya que leer de corrido |
| Cifra | `kpi` 28/32 tabular | Con su unidad y su periodo |
| Código | `code` 12/16 mono | Códigos, huellas, coordenadas |

## 4. Controles, grid e iconografía

- **Altura de controles**: 36 px (`md`, formularios y acción principal), 32 px (`sm`, cabeceras, tablas y filtros) y 44
  px en táctil (< 768 px). Área táctil mínima de 24 px en escritorio, aunque el ícono sea de 16.
- **Grid**: contenido con `max-w-[1440px]`; 12 columnas con 24 px de canal desde `lg`. Ficha: cuerpo de 8 columnas y
  panel de 4 (≥ 1280); una columna por debajo.
- **Breakpoints**: los de §2 de `design-system.md` (640, 768, 1024, 1280, 1440). Cambia solo cuándo se contrae la barra
  lateral: desde 1024, no desde 1280 (DD-10).
- **Iconos**: `lucide-react` (ya instalado) a 16 px en controles y 20 px en la navegación, con trazo 1.75. Un ícono
  solo, sin texto, únicamente para lo universal: cerrar, buscar, campana, más acciones (con nombre accesible y rótulo
  emergente). Vista previa, editar y eliminar llevan texto o un rótulo visible al pasar (U-17).

## 5. Estados interactivos

| Estado | Tratamiento |
|---|---|
| Hover | Fondo `--sg-surface-hover` en filas, ítems y botones secundarios; la primaria pasa a `--sg-primary-hover` |
| Foco | Anillo de 2 px `--sg-focus` con 2 px de separación, siempre visible con teclado (`:focus-visible`) |
| Activo o seleccionado | `--sg-primary-soft` de fondo y `--sg-primary-text` de texto; en pestañas, línea inferior de 2 px `--sg-primary` |
| Deshabilitado | 50 % de opacidad **y** su motivo visible junto al control (U-15) |
| Cargando | El botón conserva su ancho, muestra un indicador y queda `aria-busy`; el resto de la pantalla sigue usable |
| Error de campo | Borde `--sg-danger`, mensaje debajo con ícono, `aria-invalid` y `aria-describedby` (ya lo hace `Campo`) |

## 6. Componentes

### 6.1 Se reutilizan (61, sincronizados con Claude Design)

Los primitivos de `@wasichai/ui` (Button, Alert, Card, Dialog, ConfirmDialog, Input, Textarea, Label, Select, Table,
Th, Td, Badge, Tabs, Pagination) y los del portal (EncabezadoDePagina, ContenedorDePagina, MasAcciones, PanelDeDecision,
PanelDeTrazabilidad, EstadoChip, SituacionDePlazo, Insignia, KpiCard, Monto, Dia, Correo, Huella, Campo, SelectNativo,
FiltroDeLista, Pasos, FormularioPorPasos, Drawer, ListaEnTarjetas, ListaPlegable, PestanasDesplazables,
VistasDelModulo, AvisoDeConflicto, BotonRecargar y los estados: Cargando, Vacio, ErrorDeCarga, NoEsDeSusProyectos,
Restringido, Reservado, NoDisponible, NotaDeConfidenciales). Ninguno se duplica.

### 6.2 Nuevos

Los ocho se especifican en [05](05-especificacion-de-componentes.md) y componen primitivos existentes.

| Componente | Compone | Por qué no basta lo que hay |
|---|---|---|
| `Seccion` | superficie, `h3`, acciones | 136 tarjetas a mano (U-34); `Card` no tiene acciones de sección ni regla de anidado |
| `CabeceraDeFicha` | `EncabezadoDePagina`, `Button`, `MasAcciones` | La regla de la acción principal (DD-02) necesita leer la siguiente acción y separar lo destructivo |
| `SelectorDeVista` | botones segmentados y menú | Reemplaza la segunda fila de pestañas (DD-03) |
| `BarraDeFiltros` | `SelectorDeVista`, `Input`, `FiltroDeLista`, `Drawer` | Chips de lo aplicado, «Más filtros» y «Limpiar» (U-22) |
| `CampoFecha` | `Input`, popover de calendario | Formato es-PE y 24 h independientes del navegador (U-07) |
| `TablaPriorizada` | `Table`, `Th`, `Td` | Prioridad de columnas y columnas de valor único ocultas (U-06) |
| `ListaPriorizada` | lista semántica | Las tres entradas más urgentes con motivo y «Ver los N» (U-05) |
| `EstadoSinAcceso` | `Vacio` | Qué falta, a quién pedirlo y cómo volver (U-09) |

## 7. Patrones

- **Tablas**:
  - encabezados en `overline`;
  - columna principal con el título en una línea y el código como subtítulo en `caption`;
  - números alineados a la derecha y en tabular;
  - estado con `EstadoChip`;
  - acciones de fila en un menú `⋯` (o al pasar), nunca destructivas a la vista;
  - ordenar desde el encabezado, con `aria-sort`;
  - fila entera con el foco (Enter abre y Espacio abre la vista previa);
  - vacío con `Vacio`, que dice qué hacer.
- **Formularios**:
  - más de 8 campos o más de un tema → pasos (`FormularioPorPasos`);
  - grupos con título h3;
  - etiquetas persistentes;
  - lo opcional marcado «(opcional)», lo obligatorio sin asterisco;
  - validación al salir del campo y al enviar, con un resumen enlazado arriba si hay más de un error;
  - panel lateral «Qué pasará» (el de las QCS) en todo registro que dispare plazos, tareas o avisos;
  - prevención de pérdida (`AvisoAlSalir`, ya existe).
- **Navegación**: [02 §3](02-arquitectura-de-informacion.md#3-shell).
- **Visualización de datos**:
  - barras horizontales para comparar categorías;
  - líneas para series de 12 meses;
  - nunca tortas de más de 3 partes;
  - colores `--sg-data-1…6` en orden fijo;
  - postura y riesgo con sus paletas propias;
  - toda cifra con periodo, unidad y fecha de cálculo;
  - «Sin datos en el periodo» en vez de cero cuando no hay base (ya existe en `KpiCard`).

## 8. Accesibilidad (WCAG 2.2 AA)

Se conserva todo lo de `design-system.md` §7 y se agrega:

- límite de controles a 3:1 (U-40);
- texto de ejemplo a 4.5:1 (U-41);
- motivo visible en todo control deshabilitado (U-15);
- rótulo para los íconos de función propia (U-17);
- orden de foco igual al visual en cabeceras con «Más acciones»;
- `aria-current="page"` en la pestaña de proyecto también en las fichas de `grievances`, `requests` y `contributions`
  (U-04).

## 9. Claro y oscuro

Cada token tiene su par oscuro en `tokens.css`; los nuevos también (§2.2). Reglas:

- en oscuro, la elevación se expresa con `--sg-surface-raised`, no con sombra;
- los suaves semánticos (`*-soft`) son fondos oscuros con tinta clara;
- el mapa usa `--sg-map-ground` en los dos temas;
- la impresión siempre usa el claro (se conserva).

Los prototipos de [04](04-prototipos/README.md) se fotografían en los dos temas.
