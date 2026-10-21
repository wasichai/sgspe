# Especificación de componentes, estados, interacciones y criterios de aceptación (etapas E y F)

> Rediseño integral de UX/UI, entregables 6 y 8. Fecha: 2026-10-21. Propuesta, pendiente de revisión.
> Los componentes nuevos viven en `sgspe-ui/src/portal/componentes/`, componen primitivos de `@wasichai/ui`, llevan sus
> textos en `textos/comun.ts` (ES y EN) y se escriben con prueba primero (Vitest, Testing Library, `mockFetch`). Su
> versión de prototipo está en `sgspe-ui/.design-sync/prototipos/piezas.tsx`.

Cada criterio de aceptación (**CA**) es verificable por una prueba unitaria (**u**), una prueba e2e (**e**) o una
inspección con la herramienta indicada (**i**).

## 1. Componentes nuevos

### 1.1 `Seccion`

Superficie con título para toda página. Reemplaza el patrón `rounded-sg-lg border … bg-sg-surface p-4` escrito a mano
136 veces (U-34).

| Prop | Tipo | Nota |
|---|---|---|
| `titulo?` | `ReactNode` | Se dibuja como `h2` con estilo `h3` |
| `descripcion?` | `ReactNode` | `body-sm`, `text-secondary` |
| `acciones?` | `ReactNode` | A la derecha del título; máximo dos |
| `anidada?` | `boolean` | Sin borde ni relleno |
| `nivel?` | `2 \| 3` | Nivel del encabezado (por defecto 2) |

- **Estados:** ninguno propio; el contenido trae los suyos.
- **Teclado:** no es enfocable.
- **Responsive:** el relleno pasa de `p-4` a `p-5` desde `2xl`.
- **CA-1.1.1 (u):** con `titulo`, el encabezado es el nombre accesible de la región (`aria-labelledby`).
- **CA-1.1.2 (u):** con `anidada`, no tiene clase de borde.
- **CA-1.1.3 (i):** el patrón de clases a mano baja de 136 a 0 (`grep -c "rounded-sg-lg border border-sg-border bg-sg-surface p-4" src/portal`), salvo las excepciones listadas en el plan.

### 1.2 `CabeceraDeFicha`

La cabecera de una ficha con la regla de la acción principal (DD-02). Compone `EncabezadoDePagina`, `Button` y
`MasAcciones`.

| Prop | Tipo | Nota |
|---|---|---|
| `antetitulo` | `string` | «Tipo · código · categoría», en sans |
| `titulo` | `string` | Dice qué es |
| `chips?` | `ReactNode` | Situación de plazo, vigencia y revisión; **no** el estado de workflow, que vive en `PanelDeDecision` |
| `siguiente` | `{ transicion?: string; etiqueta: string; falta?: string; resolver?: { etiqueta: string; a: string } } \| null` | La misma «Siguiente acción» del `PanelDeDecision` |
| `transiciones` | `TransicionDisponible[]` | De `useAvailableTransitions` |
| `secundarias?` | `AccionDeFicha[]` | Máximo dos visibles; el resto va a «Más» |
| `volver?` | `{ etiqueta: string; a: string }` | La lista de origen con sus filtros |

**Reglas:**

1. **La primaria** es la transición cuyo nombre coincide con `siguiente.transicion`.
2. **Si no está disponible** para la cuenta, o `falta` tiene valor, se dibuja deshabilitada, y `falta` va debajo con el
   enlace `resolver`.
3. **Si la cuenta no puede darla nunca** (sin permiso), no se dibuja primaria, y las secundarias quedan como están.
4. **Destructivas**: las transiciones a estados finales de cierre (`cancelled`, `rejected`, `withdrawn`, `annulled`) y
   las acciones «Retirar», «Anular» y «Terminar» van siempre en «Más acciones», con el texto en `--sg-danger` y un
   `ConfirmDialog` que nombra el registro y el efecto.

**Teclado:** el orden de foco es volver, secundarias, «Más acciones», primaria (el visual). «Más acciones» se abre con
Enter o Espacio, se recorre con flechas y se cierra con Esc, devolviendo el foco.

**Responsive:** por debajo de `md`, la primaria y «Más» pasan a la barra fija inferior que ya existe
(`e2e/responsivo.spec.ts`).

- **CA-1.2.1 (u):** con la siguiente acción `fulfill` disponible y otras tres transiciones, la única `Button` con
  `data-variant="primary"` es la de `fulfill`.
- **CA-1.2.2 (u):** con `falta`, la primaria tiene `disabled` y su `aria-describedby` apunta a un texto que contiene
  `falta`.
- **CA-1.2.3 (u):** una transición a `cancelled` no aparece fuera de «Más acciones», y elegirla abre un `ConfirmDialog`.
- **CA-1.2.4 (u):** nunca hay más de dos secundarias visibles.
- **CA-1.2.5 (e):** en la ficha de compromiso de la demo (en ejecución), el botón primario dice «Registrar cumplimiento»
  y no «Bloquear».

### 1.3 `SelectorDeVista`

Vistas de un módulo en una sola fila, con su conteo (DD-03). Reemplaza la segunda fila de pestañas.

| Prop | Tipo | Nota |
|---|---|---|
| `etiqueta` | `string` | Nombre accesible del grupo |
| `vistas` | `{ id: string; etiqueta: string; n?: number \| null }[]` | `n` nulo no se dibuja |
| `actual` | `string` | Se refleja en `?view=` |
| `visibles?` | `number` | Por defecto 4; el resto va en «Más vistas» |

- **Semántica:** `radiogroup` con `radio`, y `aria-checked` en la actual.
- **Teclado:** flechas para moverse, Enter o Espacio para elegir y Home/End para los extremos.
- **Responsive:** desplazamiento horizontal sin romper la línea.
- **CA-1.3.1 (u):** elegir «Vencidos» cambia `?view=vencidos` y conserva los demás parámetros.
- **CA-1.3.2 (u):** con 7 vistas y `visibles=4`, tres van en el menú «Más vistas», con sus conteos.
- **CA-1.3.3 (e):** las listas de Grupos de interés, Compromisos, QCS y Riesgos tienen una sola fila de `tablist` o
  `navigation` sobre el contenido.

### 1.4 `BarraDeFiltros`

Búsqueda, filtros frecuentes, «Más filtros» y chips de lo aplicado (DD-04). Compone `SelectorDeVista`, `Input`,
`FiltroDeLista` y `Drawer`.

| Prop | Tipo | Nota |
|---|---|---|
| `vista?` | `ReactNode` | El `SelectorDeVista` |
| `busqueda` | `{ etiqueta: string; valor: string; alCambiar }` | Con espera de 300 ms antes de pedir |
| `frecuentes` | `FiltroDef[]` | Máximo tres a la vista |
| `todos` | `FiltroDef[]` | En «Más filtros» (drawer de 400 px) |
| `aplicados` | `{ clave: string; etiqueta: string }[]` | Un chip por filtro, con «×» |

- **Estados:** sin filtros, no hay fila de chips; con alguno, «Limpiar filtros».
- **Teclado:** el «×» de cada chip es un botón con nombre («Quitar el filtro Campaña: perforación 2026»).
- **Responsive:** por debajo de `md`, la búsqueda ocupa el ancho y los frecuentes pasan al drawer («Filtros (2)»).
- **CA-1.4.1 (u):** quitar un chip quita ese parámetro de la URL y no los demás.
- **CA-1.4.2 (u):** «Limpiar filtros» deja la URL con `view` y sin filtros.
- **CA-1.4.3 (u):** el número de «Filtros (N)» en el teléfono es igual al de chips.

### 1.5 `CampoFecha`

Fecha (y hora) propia del portal: `dd/mm/aaaa` en es-PE, `dd/mm/yyyy` en inglés y 24 h (DD-07). Reemplaza los 33
`<input type="date|datetime-local">`.

| Prop | Tipo | Nota |
|---|---|---|
| `valor` | `string \| null` | ISO `aaaa-mm-dd`, o un instante ISO si `conHora` |
| `alCambiar` | `(iso: string \| null) => void` | Con hora: el instante de Lima (`ZONA_DE_LIMA`), como escribe el portal |
| `conHora?` | `boolean` | Agrega el campo `HH:mm` |
| `min?` / `max?` | `string` | Fuera de rango, error de campo |

- **Entrada:** se escribe `20102026`, `20/10/2026` o `20-10-2026` y se normaliza a `20/10/2026`. El botón de calendario
  abre una grilla de mes accesible (la del patrón de diálogo de fecha de WAI-ARIA).
- **Errores:** «Escriba la fecha como día/mes/año, por ejemplo 20/10/2026» (sin código).
- **Teclado:** en la grilla, flechas por día, RePág y AvPág por mes, y Enter para elegir.
- **CA-1.5.1 (u):** `20/10/2026 10:15` con `conHora` llama a `alCambiar` con `2026-10-20T15:15:00Z`, el mismo instante
  que `ahoraEnLima` produce para esa hora.
- **CA-1.5.2 (u):** con el navegador en `en-US`, el campo muestra `20/10/2026` en español y `20/10/2026` en inglés,
  nunca `10/20/2026`.
- **CA-1.5.3 (i):** `grep -rn 'type="date"\|type="datetime-local"' src/portal` da 0 resultados fuera de `CampoFecha`.

### 1.6 `TablaPriorizada`

Tabla con prioridad de columnas (DD-05). Compone `Table`, `Th` y `Td`.

| Prop | Tipo | Nota |
|---|---|---|
| `columnas` | `{ id; encabezado; prioridad: 'esencial' \| 'secundaria' \| 'opcional'; numerica?; ordenable? }[]` | |
| `filas` | `T[]` | |
| `celda` | `(fila, columna) => ReactNode` | |
| `ocultarUniformes?` | `boolean` | Oculta una columna no esencial con un único valor en la página |

- **Responsive:** las secundarias se ocultan por debajo de `xl` y aparecen en la vista previa. Las opcionales se eligen
  en «Columnas».
- **Ordenar:** el encabezado es un botón con `aria-sort`.
- **Teclado:** cada fila es enfocable. Enter abre la ficha y Espacio, la vista previa.
- **CA-1.6.1 (u):** con «Responsable» igual en todas las filas y `ocultarUniformes`, la columna no se dibuja y el
  encabezado de la página dice «Responsable: Gestión de compromisos (demo)».
- **CA-1.6.2 (e):** a 1440 px, la lista de Grupos de interés de la demo no tiene desplazamiento horizontal.

### 1.7 `ListaPriorizada`

Las entradas más urgentes de un grupo, con motivo y «Ver los N» (DD-13). Reemplaza los muros de enlaces.

| Prop | Tipo | Nota |
|---|---|---|
| `titulo` | `string` | |
| `total` | `number \| null` | Nulo, sin conteo (p. ej., cuando hay QCS confidenciales ocultas: `NotaDeConfidenciales`) |
| `entradas` | `{ titulo; a; motivo; cuando?; tono }[]` | Máximo tres; ordenadas por quien llama |
| `verTodos` | `{ etiqueta; a }` | La lista filtrada |

- **CA-1.7.1 (u):** con 16 entradas y límite 3, se dibujan 3 y «Ver las 16».
- **CA-1.7.2 (u):** los títulos no se subrayan; el tono se marca con un borde izquierdo y además con el texto del motivo
  (no solo con color).

### 1.8 `EstadoSinAcceso`

Pantalla o bloque sin permiso, con salida (DD-12). Reemplaza la línea suelta de c.42.

| Prop | Tipo | Nota |
|---|---|---|
| `que` | `string` | El nombre de la sección |
| `aQuien` | `string \| null` | La jefatura del proyecto si se conoce (de `project.manager`), si no «la administración» |
| `volver` | `{ etiqueta; a }` | El resumen del proyecto o el inicio |

- **CA-1.8.1 (u):** el texto no nombra roles ni permisos técnicos.
- **CA-1.8.2 (u):** el botón «Volver…» es el primer elemento enfocable.

## 2. Cambios en componentes existentes

| Componente | Cambio | CA |
|---|---|---|
| `MigasDelProyecto` y `ProyectoLayout` | La pestaña se calcula con `PESTANA_DE_SEGMENTO` **antes** de marcarla y de pasarla a las migas (`ProyectoLayout.tsx:74,88`) | (u) en `/projects/p1/grievances/q1`, la pestaña marcada es QCS y la miga dice «Quejas, consultas y solicitudes»; lo mismo con `requests` y `contributions` |
| `InicioPage` | «Mi trabajo» para toda cuenta, con la visión ejecutiva como bloque (02 §4) | (u) una cuenta sin lectura de KPI-EXE no ve ningún «No disponible» en el inicio; (u) una con lectura ve el bloque con su fecha de cálculo |
| `EncabezadoDePagina` | Antetítulo en sans; lo usan todas las páginas | (i) ningún `<h1` fuera de `EncabezadoDePagina` y `CabeceraDeFicha` en `src/portal` |
| `PanelDeDecision` | El camino del flujo se pliega dentro de «Estado actual» | (u) un solo `EstadoChip` por ficha |
| Barra superior (`AppShell`) | Idioma y tema al menú de la cuenta; selector de proyecto como combobox | (e) la barra tiene 6 controles a 1440 px |
| Navegación (`navTree`, `AppShell`) | Expandida desde `lg`, títulos de bloque visibles, «Quejas y solicitudes» | (e) a 1024 px se ven las etiquetas |
| Controles de formulario | `--sg-border-control` (03 §2.2) | (i) `contraste.py` con el par `border-control/surface` ≥ 3:1 |

## 3. Estados del sistema

| Estado | Pieza | Texto (es) | Siguiente paso |
|---|---|---|---|
| Cargando | `Cargando` | Esqueleto; «Cargando…» solo en `compacto` | — |
| Guardando | `Button` con `aria-busy` | La etiqueta no cambia | Se bloquea el doble envío (ya lo hace `useComando`) |
| Vacío | `Vacio` | Qué falta y por qué importa | La acción de creación si la cuenta puede |
| Vacío por filtros | `Vacio` | «Ningún … coincide con los filtros» | «Limpiar filtros» |
| Error de carga | `ErrorDeCarga` | «No se pudo cargar… Los filtros se conservan.» | «Reintentar» |
| Sin permiso | `EstadoSinAcceso` | «Su cuenta no puede abrir …» | A quién pedirlo y «Volver» |
| Registro reservado | `Reservado` | «Registro reservado» | Ninguno (no se revela nada) |
| No existe o no es suyo | `NoEsDeSusProyectos` | «El registro no existe o no es de sus proyectos.» | «Ir a Proyectos» |
| Conflicto (412) | `AvisoDeConflicto` | Quién cambió y qué se conserva | «Recargar» |
| Éxito | `Alert` `success` o aviso efímero | Qué pasó **y qué sigue** | Enlace a lo siguiente |

**Confidencialidad (se conserva):** ningún estado distingue «no existe» de «no es suyo», ningún conteo incluye lo
ilegible, y una cifra que excluye QCS confidenciales lleva `NotaDeConfidenciales` sin número.

## 4. Criterios por patrón

| Patrón | Criterios de aceptación |
|---|---|
| P-01 Inicio | (e) el agente de campo de la demo ve «Lo que me toca hoy» y sus visitas, y ningún «No disponible»; (e) gestión social ve «Esperan su decisión» con el aporte DEMO-AP-01 |
| P-02 Listado | (e) una fila de pestañas; (u) chips y «Limpiar»; (e) sin desborde a 1440 px; (e) tarjetas a 390 px |
| P-03 Ficha 360 | (u) «Qué requiere atención» muestra ≤ 3 entradas por grupo; (i) ninguna lista de más de 3 enlaces subrayados |
| P-04 Formulario | (u) registrar QCS en tres pasos con «Qué pasará» visible en cada uno; (u) `CampoFecha` en 24 h |
| P-05 Bandeja | (u) aprobar un aporte desde la bandeja con motivo, sin navegar a la ficha; (u) solo se ofrecen las decisiones que el servidor permite |
| P-06 Mapa | (e) el lienzo ocupa ≥ 75 % del ancho del contenido a 1440 px; (u) sin mapa base, el aviso nombra dónde se configura |
| P-07 Tablero | (u) toda cifra lleva periodo y fecha de cálculo; (i) ningún gráfico con trama |
| P-08 Administración | (u) «Terminar la asignación» solo en el menú de la fila y con confirmación que nombra a la persona |
| P-09 Ficha | CA-1.2.1 a CA-1.2.5; (u) campos vacíos plegados tras «Mostrar los N campos sin dato» |

## 5. Validación de los prototipos (etapa E)

Se recorrieron los once prototipos y sus 23 capturas en claro, oscuro, inglés y 390 px.

| # | Problema encontrado | Dónde | Corrección |
|---|---|---|---|
| V-01 | Tarjetas de cifras dentro de una sección con borde (tarjeta en tarjeta) | P-01 | La sección de la visión ejecutiva pasa a `anidada` |
| V-02 | Los colores de postura, datos y suelo del mapa no se dibujaban: no son utilidades de Tailwind | P-03, P-06, P-07 | Se usan por `var(--sg-…)` y se registra U-43 (exponerlos en `@theme inline`) |
| V-03 | La cabecera de ficha bajaba las acciones bajo un título largo | P-09 | Columna derecha fija para acciones y motivo del bloqueo |
| V-04 | El paso 2 se llamaba «Cómo llegó», pero también pedía quién la plantea | P-04 | Pasos «Qué se plantea · Cómo llegó y quién · Revisión» |
| V-05 | Las iniciales de «Aprobación (demo)» salían «A(» | Shell | Se ignora lo que va entre paréntesis |
| V-06 | La tabla por proyecto ponía cuatro medidas distintas en una misma escala de barras | P-07 | Tabla con una columna por medida |
| V-07 | El menú de fila quedaba cortado por el contenedor de la tabla | P-08 | El menú se abre hacia donde hay espacio (en producto: un popover que no recorta) |
| V-08 | A 390 px, el shell no se adaptaba (barra lateral fija y desborde de 1385 px) | Shell, P-02, P-09 | Barra superior compacta, barra inferior de 4 destinos, lista en tarjetas y acciones fijas sobre la barra de accesos |
| V-09 | Los estados de workflow seguían en español con la interfaz en inglés | P-05 en inglés | Es el comportamiento real del portal: U-42 y [08](08-cambios-de-contrato-backend.md) |
| V-10 | Los controles reales de `@wasichai/ui` seguían con borde de 1.38:1 | P-04 | La regla de `--sg-border-control` aplicada a `input`, `textarea` y `select` (la de 03 §2.2) |

**Pasos de los flujos.** Contados sobre los prototipos, coinciden con
[02 §6](02-arquitectura-de-informacion.md#6-flujos-prioritarios-rehechos):

| Flujo | Hoy | Propuesto |
|---|---|---|
| F-A, campo | 2 clics y una búsqueda visual | 1 clic |
| F-A, QCS | 4 | 1 |
| F-E | 4+ | 2 (elegir y aprobar) |
| F-B | Mismo número de pasos | Sin la primaria engañosa |

**Accesibilidad revisada.**

- Contraste con `contraste.py`: 25 pares sin fallas, más el borde de control a 3.50:1 en claro y 3.03:1 en oscuro.
- Foco visible en las piezas del kit (`focus-visible`, anillo `--sg-focus`).
- Nombres accesibles en los íconos sin texto (menú, buscar, acercar, alejar, lista y acciones de fila).
- `aria-current` en la pestaña de proyecto y en la navegación.
- `radiogroup` en los selectores de vista.

**Pendiente de verificar en el producto:** el recorrido completo con teclado y lector de pantalla. Los prototipos no
tienen lógica de foco en menús y diálogos propios; la tienen las piezas del kit.

**Confidencialidad revisada:**

- en P-01 y P-07, ninguna cifra cuenta lo que no se lee; la inversión no disponible queda como tal;
- en P-07, «QCS abiertas» lleva `NotaDeConfidenciales` sin número;
- en P-04, el dato de la persona se marca como restringido;
- en el estado de sistema, «Registro reservado» no revela nada.
