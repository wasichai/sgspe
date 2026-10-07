# Design system de SGSPE

Sistema de Gestión Social para Proyectos Extractivos · Diseño UX/UI · etapas 1 y 2 de 3 · versión 0.2 · 2026-10-07

Este documento define los fundamentos visuales, los tokens, el sistema de estados, el catálogo de componentes y los patrones
núcleo del portal. Se apoya en la [arquitectura de UX](ux-architecture.md) y en los diseños de la Fase 0; los IDs se definen
solo en el [análisis](../requirements-analysis.md). Los tokens listos para usar están en [`tokens.css`](tokens.css) y la
versión visual de todo lo que sigue está en el lienzo de diseño (páginas "03 Design system" y "04 Patrones núcleo").

## 1. Base técnica

- El portal se construye con las primitivas de `@wasichai/ui` 0.5.0, Tailwind 4 y `@wasichai/ui/theme.css`, que ya trae temas
  por tokens [WAS-ADR-0035] (platform-fit §6). SGSPE no reemplaza esas primitivas: les da sus tokens y compone patrones con
  ellas (RNF-MNT-001).
- Los tokens de SGSPE son una capa sobre el tema de wasichai: `tokens.css` declara las variables con prefijo propio y las
  conecta con las del tema. La correspondencia exacta de nombres se confirma en F1 al copiar caja-ui (ASM-077, pregunta Q2 de
  la arquitectura de UX).
- Gráficos con ECharts (RQ-DSH-035) y mapas con MapLibre 6.10.0 (RQ-GIS-014), ambos tomando sus colores de los mismos tokens.
- React 19, TypeScript, React Router, TanStack Query, React Hook Form, Zod e i18next, en las versiones de caja-ui
  (platform-fit §6). La interfaz no usa ninguna otra biblioteca de componentes.

## 2. Dirección visual: "Pino y piedra"

**ASSUMPTION (ASM-077):** neutros de piedra con un leve tono verde, un solo acento pino para la acción, estados apagados que se
distinguen también por ícono y etiqueta, arena solo para el territorio y violeta solo para lo que propone la IA. Tipografía IBM
Plex Sans para todo y IBM Plex Mono para códigos, coordenadas y huellas. Se revierte cambiando solo `tokens.css`.

Por qué: la interfaz debe sentirse profesional, confiable, calmada y densa en datos [PRM-02 §7, §43]. Un acento único deja que
el color hable solo cuando importa (estado, plazo, riesgo); la familia Plex tiene cifras tabulares, todos los signos del
español y una lectura técnica sin frialdad. Se evitan las tipografías y los recursos más gastados (Inter, Roboto, degradados,
tarjetas con borde lateral de color).

### 2.1 Marca SGSPE

**ASSUMPTION (ASM-082):** SGSPE tiene marca propia; el usuario eligió la dirección «Sendero» (lienzo de la etapa 3, página 22).

- **Isotipo:** cerros en blanco sobre un cuadrado pino de esquinas redondeadas, con un sendero en arena que sube hacia ellos.
  A 24 px o menos se usa la variante simplificada (trazos más gruesos, un solo tramo de sendero); el favicon sale de ella
  (16 y 32 px) y el ícono de la aplicación de la versión completa (180 y 512 px).
- **Logotipo:** isotipo más «SGSPE» en IBM Plex Sans semibold; con el nombre completo («Gestión social de proyectos de
  exploración») desde 120 px de ancho. Área de respeto: la mitad del isotipo por lado.
- **Versiones:** a color sobre claro; invertida (isotipo blanco con cerros pino y sendero en arena oscura) sobre pino o tinta;
  monocroma en tinta para impresión en blanco y negro.
- **Paleta de marca:** pino #1D5C52, pino profundo #123D36, arena #C9B48A (solo en gráficos: 3.84:1 sobre pino), arena oscura
  #8A6E35 (4.81:1 sobre blanco), piedra #F5F6F3 y tinta #18211D. Coincide con los tokens de §3; los tokens semánticos no
  cambian.
- **Usos incorrectos:** no deformar, no cambiar colores fuera de las tres versiones, no poner el isotipo sobre fotos sin un
  fondo sólido, no usar la arena para texto.
- **Aplicaciones:** barra superior (isotipo de 28 px y «SGSPE»), login, pestaña del navegador, correos de aviso y la portada de
  los entregables, donde manda la marca del cliente y SGSPE figura solo como «Preparado con SGSPE».
- **Voz y tono:** directa, en segunda persona, sin jerga técnica; dice qué pasó y qué hacer.

## 3. Tokens

### 3.1 Color base

Todos los pares de texto superan 4.5:1 y los bordes de controles 3:1, en claro y en oscuro (§10).

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `bg` | `#F5F6F3` | `#111614` | Fondo de la aplicación |
| `surface` | `#FFFFFF` | `#171D1A` | Tarjetas, tablas, paneles, diálogos |
| `surface-sunken` | `#EEF0EC` | `#0D110F` | Encabezados de tabla, controles segmentados, bandas |
| `surface-hover` | `#F9FAF8` | `#1D2420` | Fila o ítem bajo el cursor |
| `border` | `#D9DDD6` | `#2A332E` | Separadores y contorno de tarjetas (decorativo) |
| `border-subtle` | `#E4E7E1` | `#222A26` | Separadores dentro de una tarjeta |
| `border-strong` | `#838B85` | `#5E6A64` | Contorno de controles (3:1) |
| `text` | `#18211D` | `#E7ECE8` | Texto principal |
| `text-secondary` | `#46514B` | `#B5BFB9` | Texto de apoyo |
| `text-muted` | `#5E6862` | `#95A09A` | Metadatos y leyendas (nunca por debajo de 4.5:1) |
| `primary` | `#1D5C52` | `#2A7468` | Acción primaria, selección, navegación activa |
| `primary-hover` | `#164A42` | `#2E8073` | Hover de la acción primaria |
| `primary-soft` | `#E3EFEC` | `#17302B` | Fondo de selección y de navegación activa |
| `primary-text` | `#1D5C52` | `#6FC2B2` | Enlaces y texto de acento |
| `on-primary` | `#FFFFFF` | `#FFFFFF` | Texto sobre `primary` |
| `focus` | `#2F6FB0` | `#7FB0E6` | Anillo de foco (azul, distinto del acento) |

### 3.2 Tonos semánticos

Cada tono es una pareja tinta + fondo suave, usada en chips, insignias, alertas y celdas.

| Tono | Tinta claro / fondo | Tinta oscuro / fondo | Cuándo |
|---|---|---|---|
| `neutral` | `#4F5954` / `#ECEEEA` | `#B5BFB9` / `#242C28` | Estado inicial, borrador, sin evaluar |
| `info` | `#2B5F8F` / `#E6EEF6` | `#86B6E3` / `#1B2A38` | En revisión, esperando decisión, aprobado sin iniciar |
| `progress` | `#1D5C52` / `#E3EFEC` | `#6FC2B2` / `#17302B` | En ejecución, en atención, en mitigación |
| `success` | `#2E6B3E` / `#E5F0E7` | `#86C496` / `#1A2B1F` | Cumplido, verificado, resuelto, al día |
| `warning` | `#7F5300` / `#FBF0D9` | `#E3B65A` / `#33280F` | Por vencer, bloqueado, suspendido, postergado |
| `danger` | `#A33A2A` / `#F8E7E3` | `#EF9583` / `#3A1E19` | Vencido, alto, rechazo, error |
| `critical` | `#FFFFFF` sobre `#7A1F1F` | `#F4A9A9` / `#401A1A` | Crítico: único relleno sólido de la escala |
| `ai` | `#5A4A8C` / `#EEEAF6`, borde discontinuo `#8F80BD` | `#BBAEEA` / `#262138` | Solo propuestas y resúmenes de IA |
| `territory` | `#8A5A00` / `#F3EEE2`, borde `#D8CDB3` | `#E3B65A` / `#2E2717` | Chip y fichas de territorio |

### 3.3 Datos y mapas

| Escala | Valores | Uso |
|---|---|---|
| Categórica | `#2B5F8F` · `#C9772B` · `#1D5C52` · `#8E5A9E` · `#6B7F2A` · `#838B85` | Series sin orden; azul y naranja primero, que se distinguen también en luminosidad |
| Postura (divergente) | A FAVOR `#2B5F8F` · NEUTRAL `#B9BFB6` · EN CONTRA PERO ABIERTO A ESCUCHAR `#D9A35B` · EN CONTRA `#A33A2A` | KPI-STK-02 y la ficha territorial |
| Nivel de riesgo | BAJO `#E5F0E7` · MEDIO `#FBF0D9` · ALTO `#F3C9B8` · CRITICO `#A33A2A` | Mapa de calor 5 × 5 (KPI-RSK-02) |
| Mapa | Base apagada `#E8EBE3`; territorio comunal en arena; área del proyecto en pino discontinuo; selección con borde de 2.5 px | Mapa territorial y del proyecto; la paleta de capas se comparte con los mapas de informe (`gis.md` §7.2) |

### 3.4 Tipografía

| Token | Tamaño / interlineado | Peso | Uso |
|---|---|---|---|
| `display` | 32 / 40 | 600, −0.01em | Título de tablero o de portada |
| `h1` | 24 / 32 | 600 | Título de página o de ficha |
| `h2` | 20 / 28 | 600 | Sección de página |
| `h3` | 16 / 24 | 600 | Tarjeta o bloque |
| `body` | 14 / 20 | 400 | Texto general |
| `body-sm` | 13 / 18 | 400 | Tablas, metadatos, formularios densos |
| `table-dense` | 12 / 16 | 400 | Tabla en modo denso |
| `label` | 13 / 16 | 500 | Etiquetas de campo |
| `caption` | 12 / 16 | 400 | Ayudas y leyendas |
| `overline` | 11 / 16 | 600, +0.04em | Rótulos de bloque escritos en mayúsculas ("DE DÓNDE VIENE") |
| `kpi` | 28 / 32 | 600, cifras tabulares | Indicadores |
| `kpi-sm` | 20 / 28 | 600, cifras tabulares | Indicadores secundarios |
| `code` | 12 / 16 | Plex Mono 400 | Códigos (COM-2026-031), EPSG, huellas, rutas |

Familias: `"IBM Plex Sans", "Segoe UI", system-ui, sans-serif` y `"IBM Plex Mono", ui-monospace, monospace`. Las cifras de
tablas, montos y KPIs usan `font-variant-numeric: tabular-nums`. Los valores de ENUM se muestran tal como se guardan, en
MAYÚSCULAS españolas (ADR-0009).

### 3.5 Espaciado, radios, sombras y bordes

| Grupo | Valores |
|---|---|
| Espaciado (base 4) | 2 · 4 · 6 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64. Ícono–texto 8; dentro de tarjetas densas 12; tarjetas 16–20; entre secciones 24–32 |
| Radios | `xs` 3 (insignias) · `sm` 4 (chips) · `md` 6 (controles) · `lg` 8 (tarjetas) · `xl` 12 (drawers y diálogos) · `full` |
| Sombras | `sm` 0 1px 2px rgba(16,24,20,.06) (pestaña activa) · `md` 0 4px 12px rgba(16,24,20,.08) (menús) · `lg` 0 12px 32px rgba(16,24,20,.12) (drawers) · `overlay` 0 24px 64px rgba(16,24,20,.28) (diálogos) |
| Bordes | 1 px separador y tarjeta; 1 px `border-strong` en controles; 2 px énfasis |
| Foco | Anillo de 2 px `focus` con separación de 2 px, en todo elemento interactivo, también sobre fondos de color |

### 3.6 Breakpoints y layout

| Ancho | Comportamiento |
|---|---|
| ≥ 1440 | Navegación 240 px, sub-navegación de proyecto 224 px, drawer al lado del contenido |
| 1280 | El drawer se superpone al contenido; tablas densas por defecto |
| 1024 | Navegación contraída a 52 px; la sub-navegación de proyecto pasa a un menú desplegable |
| 768 (tableta) | Navegación en un panel superpuesto que se abre con el botón de menú y atrapa el foco; fichas en una columna con la acción primaria fija al pie; el mapa ocupa la pantalla y la ficha contextual es una hoja inferior |
| < 640 (móvil) | Solo tareas de campo, aprobaciones, avisos y consulta rápida; barra inferior de cuatro destinos por rol (`ux-architecture.md` §7.1) [PRM-02 §31] |

Barra superior de 56 px; contenido de hasta 1440 px con márgenes de 32, 24 y 16 px; drawer de 400 px (vista previa) o 560 px
(formulario); filas de tabla de 32 px (densa) o 44 px (cómoda); objetivo táctil de 44 px; objetivo mínimo de escritorio de 32
px, por encima del 24 × 24 de WCAG 2.2.

### 3.7 Movimiento

120 ms en hover y foco, 200 ms al abrir drawers y diálogos, siempre con `ease-out`. Sin animaciones decorativas, contadores
animados ni brillos de carga. Con `prefers-reduced-motion`, todo cambia sin transición [PRM-02 §43].

### 3.8 Estados de componente

| Estado | Regla |
|---|---|
| Reposo | Colores de §3.1 |
| Hover | Fondo `surface-hover` o el tono `-hover`; nunca solo cambio de cursor |
| Presionado | Un paso más oscuro que hover |
| Foco | Anillo `focus` visible con teclado (`:focus-visible`); nunca se elimina |
| Deshabilitado | Contraste reducido y `cursor: not-allowed`; si la acción no está permitida, se explica en el tooltip por qué. Si el rol nunca la tendrá, la acción no se muestra |
| Cargando | El control conserva su tamaño, muestra un indicador y un texto en gerundio ("Aprobando…") y queda inactivo; `aria-busy` |
| Error | Borde y mensaje `danger` con ícono, debajo del campo, enlazado con `aria-describedby`; el mensaje dice cómo corregir |
| Éxito | Confirmación breve (toast o marca en el campo); nunca bloquea el siguiente paso |

### 3.9 Temas

**ASSUMPTION (ASM-086):** SGSPE ofrece tres temas propios sobre el sistema de temas de wasichai [WAS-ADR-034,
WAS-ADR-035], sin tocar sus paquetes: cada uno es un bloque `[data-theme='<id>']` de [`themes.css`](themes.css) con los 28
tokens de `@wasichai/ui` (18 base y 10 de extensión), IBM Plex Sans y los radios de §3.5, y un `ThemeDefinition` en
`config.themes` del portal y de `WasichaiApp`. Lienzo de la etapa 3, página 25.

| Tema | `id` | `colorScheme` | Carácter |
|---|---|---|---|
| Pino y piedra | `sgspe-pino` | `light` | El actual y el predeterminado: barra superior blanca, acento pino, neutros de piedra |
| Cielo | `sgspe-cielo` | `light` | Fondo blanco y azul (`brand` #1F5FA8), barra superior azul profundo (#0E3A66), como el portal tributario de srtm |
| Pino de noche | `sgspe-noche` | `dark` | Grafito verdoso con acento jade (#6FC2B2): se distingue del oscuro de wasichai, que es gris azulado |

- **Selector:** Perfil › Tema en el portal y en `/admin`, con una sola preferencia por cuenta
  (`/api/auth/me/preferences`). Ofrece «Según el sistema», los tres de SGSPE y Claro y Oscuro de wasichai, que la plataforma
  siempre incluye.
- **Marca:** no cambia con el tema. El isotipo va a color sobre una barra clara e invertido sobre una oscura (§2.1).
- **Colores propios de SGSPE:** cada bloque apunta los `--sg-*` de base y acción a los tokens de wasichai; los de IA,
  territorio, nivel de riesgo y datos toman los valores claros de [`tokens.css`](tokens.css), o los oscuros en
  `sgspe-noche`.
- **Impresión:** los documentos impresos siguen en claro (`PrintableDocumentPage` fija `data-theme="light"`).
- **Brecha:** «Según el sistema» resuelve hoy a `light` o `dark` de wasichai, y la preferencia por defecto es `system`. Para
  que el portal abra en Pino y piedra y siga al sistema con Pino de noche hace falta UPS-19, pedida en
  [wasichai/wasichai-ui#39](https://github.com/wasichai/wasichai-ui/issues/39). Mientras tanto el portal no fuerza un
  tema: la preferencia del servidor manda (sin fila responde `system`) y pisa la copia local, así que un usuario sin
  preferencia ve Claro u Oscuro de wasichai y elige el de SGSPE en Perfil › Tema. No se escribe la preferencia en el primer
  ingreso, porque no distingue a quien nunca eligió de quien eligió «Según el sistema».
- **Pruebas:** como `tokens.test.tsx` de srtm-ui, un test comprueba que cada tema fija los 28 tokens y mide los pares de
  la tabla.

Contraste medido (WCAG 2.2 AA):

| Par | Mínimo | Pino y piedra | Cielo | Pino de noche |
|---|---|---|---|---|
| `ink` / `surface` | 4.5:1 | 16.48 | 16.50 | 14.31 |
| `ink-muted` / `surface` | 4.5:1 | 5.78 | 6.43 | 6.73 |
| `brand-strong` / `surface` | 4.5:1 | 10.04 | 8.68 | 10.34 |
| `danger` / `surface` | 4.5:1 | 6.57 | 6.36 | 7.58 |
| `success` / `surface` | 4.5:1 | 6.39 | 6.64 | 8.43 |
| `warning` / `surface` | 4.5:1 | 6.69 | 6.31 | 9.05 |
| `link` / `surface` | 4.5:1 | 7.76 | 6.84 | 9.27 |
| `brand` / `surface` | 4.5:1 | 7.76 | 6.44 | 8.18 |
| `ink` / `surface-muted` | 4.5:1 | 15.19 | 15.22 | 15.29 |
| `ink-muted` / `surface-muted` | 4.5:1 | 5.33 | 5.93 | 7.19 |
| `on-brand` / `brand` | 4.5:1 | 7.76 | 6.44 | 9.04 |
| `shell-ink` / `shell` | 4.5:1 | 16.48 | 11.57 | 16.14 |
| `shell-muted` / `shell` | 4.5:1 | 5.78 | 7.11 | 7.59 |
| `on-danger` / `danger` | 4.5:1 | 6.57 | 6.36 | 8.36 |
| `success` / `success-soft` | 4.5:1 | 5.46 | 5.77 | 7.34 |
| `warning` / `warning-soft` | 4.5:1 | 5.91 | 5.67 | 7.65 |
| `danger` / `danger-soft` | 4.5:1 | 5.49 | 5.46 | 6.74 |
| `notice` / `notice-soft` | 4.5:1 | 6.22 | 6.20 | 8.69 |
| `brand-strong` / `brand-soft` | 4.5:1 | 8.52 | 7.55 | 8.50 |
| `ink` / `table-stripe` | 4.5:1 | 15.74 | 15.77 | 14.76 |
| `ink-muted` / `table-head` | 4.5:1 | 5.04 | 5.76 | 6.23 |
| `focus` / `surface` | 3:1 | 5.22 | 4.48 | 7.54 |
| `focus` / `surface-muted` | 3:1 | 4.81 | 4.13 | 8.06 |
| `map-selected` / `surface` | 3:1 | 3.58 | 3.58 | 6.40 |

## 4. Sistema de estados

Tres familias que nunca se confunden [PRM-02 §8]:

1. **Estado de workflow:** decisión humana registrada por una transición. Chip suave con ícono y etiqueta.
2. **Situación derivada:** lo que calcula un trabajo programado con el paso del tiempo (`deadline_status`, `validity_status`,
   `review_status`). Insignia redondeada con reloj; nunca reemplaza al estado (RN-WFL-001, CON-028).
3. **Nivel:** riesgo, severidad, prioridad. Insignia con barras (1 a 4) además del color.

Al pasar el cursor o al enfocar, un chip de estado dice desde cuándo, quién lo cambió y con qué motivo.

### 4.1 Estados de workflow

| Objeto (WF) | Estado → tono · ícono |
|---|---|
| Proyecto (WF-A) | Borrador → neutral · círculo punteado; En revisión → info · reloj; Activo → success · círculo con check; Suspendido → warning · pausa; Cerrado → final · candado; Anulado → final · aspa |
| Grupo de interés (WF-B) | Identificado → neutral; Caracterizado → info · ficha; Evaluado → success · círculo con check; Retirado → final · aspa (reincorporable) |
| Interacción (WF-C) | Planificada → neutral; Programada → info · reloj; Realizada → progress · check; Acta en borrador → progress · ficha; Acta en revisión → info · reloj; Acta aprobada → success · círculo con check; Postergada → warning · pausa; Cerrada → final · candado; Cancelada → final · aspa |
| Compromiso (WF-D) | Borrador → neutral; Pendiente de aprobación → info · reloj; Aprobado → info · círculo con check; En ejecución → progress · play; Bloqueado → warning · pausa; Cumplido → success · check; Verificado → success · escudo; Cerrado → final · candado; Cancelado → final · aspa |
| Queja (WF-E1) | Recibida → neutral; Acusada → info · check; En evaluación y En investigación → info · lupa; En atención → progress · play; Resuelta → success · check; Verificada → success · escudo; Cerrada → final · candado; Rechazada y Desistida → final · aspa |
| Consulta y solicitud (WF-E2, WF-E3) | Recibida → neutral; En evaluación → info · lupa; Respondida y Aceptada → success; Derivada → info · flecha; Declinada → final · aspa; Cerrada → final · candado |
| Riesgo social (WF-F) | Identificado → neutral; Evaluado → info · lupa; En mitigación → progress · play; En monitoreo → info · ojo; Cerrado → final · candado |
| Alerta temprana (WF-J) | Detectada → warning · triángulo; En triaje → info · reloj; Confirmada → danger · triángulo (solo una persona); Descartada → final · aspa; En respuesta → progress · play; Resuelta → success · check; Cerrada → final · candado |
| Programa social (WF-G) | Borrador → neutral; Pendiente de aprobación → info; Planificado → info; En ejecución → progress; En monitoreo → info · ojo; En evaluación → info · lupa; Suspendido → warning; Cerrado y Cancelado → final |
| Tarea (WF-N) | Abierta → neutral; En curso → progress; Bloqueada → warning; Hecha → final · check; Cancelada → final · aspa |
| Área de influencia (WF-O) | Borrador → neutral; Aprobada → success (versión vigente); Reemplazada → final · candado |
| Propuesta de IA (WF-K) | Propuesta → ai · chispa; Aceptada → success; Rechazada y Duplicada → final · aspa |
| Acuerdo (WF-I) | Borrador → neutral; En negociación → info · flechas; Vigente → success · círculo con check; Suspendido → warning · pausa; Concluido → final · check; Resuelto y Anulado → final · aspa |
| Entregable (WF-H) | Borrador → neutral; En revisión → info · reloj; Observado → warning · globo; Aprobado → success · círculo con check; Emitido → final · candado (versión congelada); Anulado → final · aspa |
| Visita de campo (WF-L) | Planificada → neutral; En curso → progress · play; Concluida → final · check; Parcial → final · media luna; Cancelada → final · aspa |
| Incidente (WF-M) | Reportado → warning · triángulo; En revisión → info · lupa; En respuesta → progress · play; Cerrado → final · candado |
| Aporte social (WF-P) | Solicitado → neutral; Aprobado → info · círculo con check; Adquirido → info · caja; Entregado → success · check (cuenta como ejecutado, ASM-063); Cerrado → final · candado; Rechazado y Anulado → final · aspa |

"Final" es contorno gris con candado o aspa: el registro deja de pedir atención. Con estas filas, los 18 workflows de
`workflows.md` tienen su tono y su ícono.

### 4.2 Situación derivada

| Situación | Valores (catálogo §10 del análisis) | Forma |
|---|---|---|
| Plazo (`deadline_status`) | AL DIA · POR VENCER · VENCIDO | Contorno verde; ámbar con fondo; relleno rojo. Con días: "VENCIDO · 7 días", "POR VENCER · 3 días" |
| Vigencia (`validity_status`) | VIGENTE · POR VENCER · VENCIDO | Igual que plazo |
| Revisión (`review_status`) | AL DIA · POR REVISAR · REVISION VENCIDA | Igual que plazo |

En listas y fichas, la insignia de plazo va siempre al lado del chip de estado. Un compromiso en borrador o pendiente de
aprobación no muestra plazo, porque no lo tiene (dashboards §3.2).

### 4.3 Nivel, severidad y prioridad

| Escala | Valores | Forma |
|---|---|---|
| Nivel de riesgo y severidad de alerta | BAJO · MEDIO · ALTO · CRITICO | Insignia con 1 a 4 barras; CRITICO con relleno sólido |
| Severidad de queja | BAJA · MEDIA · ALTA · CRITICA | Igual |
| Prioridad | BAJA · MEDIA · ALTA · MUY ALTA | Igual |

### 4.4 Procedencia del dato

| Marca | Significado | Forma |
|---|---|---|
| VERIFICADO | Comprobado por otra persona (verificación con segregación de funciones) | Contorno verde con escudo |
| REGISTRADO | Ingresado por una persona, con autor y fecha | Contorno gris con persona |
| CALCULADO | Lo deriva el sistema: plazos, áreas, indicadores, montos ejecutados | Contorno azul con signo de suma |
| SUGERENCIA IA | Propuesta con evidencia y confianza; aceptarla es un comando humano (RQ-AI-003) | Fondo violeta, borde discontinuo, chispa |
| RESUMEN IA | Texto generado; solo entra a un registro o entregable si una persona lo copia y lo edita (RQ-AI-012) | Fondo violeta, borde discontinuo, chispa con línea |

Las marcas son discretas: aparecen donde la procedencia cambia la lectura (un monto ejecutado, un resumen, una propuesta), no
en cada campo [PRM-02 §23]. Corresponden al catálogo "Origen del contenido" (HUMANO, CALCULADO, ASISTIDO POR IA) más la
verificación de WF-D y WF-E1.

## 5. Componentes

Catálogo mínimo de PRM-02 §7, con lo que SGSPE agrega. "Base" indica si la pieza viene de `@wasichai/ui` (a confirmar en F1)
o se compone en el portal.

| Componente | Uso en SGSPE | Variantes y estados | Accesibilidad | Base |
|---|---|---|---|---|
| Button | Acciones de página y de diálogo | Primario, secundario, sutil, peligro, IA; 32 · 36 · 44 px; cargando | `<button>`; texto en verbo ("Aprobar") | wasichai |
| IconButton | Más acciones, filtrar, cerrar | Con y sin borde | `aria-label` y tooltip obligatorios | wasichai |
| Input, Textarea | Campos | Foco, error, solo lectura, deshabilitado; contador en motivos | `<label>` visible; error con `aria-describedby` | wasichai |
| SearchInput | Búsqueda en listas | Con atajo visible | Etiqueta oculta accesible | wasichai |
| Select, MultiSelect, Combobox | ENUM, relaciones, responsables | Chips quitables; opciones deshabilitadas con razón | Patrón combobox de ARIA | wasichai |
| DatePicker, DateRangePicker | Fechas y periodos | Atajos (este mes, 90 días, campaña); feriados marcados | Tecla por tecla en la grilla; formato dd/mm/aaaa | wasichai |
| Checkbox, Radio, Switch | Opciones | Fieldset con leyenda | Nativos o `role="switch"` | wasichai |
| Badge | Conteos | Neutro, peligro, advertencia, IA | Texto que explica el conteo en `title` y en el nombre accesible del enlace | portal |
| StatusBadge | Estado, situación, nivel, procedencia (§4) | Las cuatro familias | Ícono + etiqueta; nunca solo color | portal |
| Avatar, AvatarGroup | Personas del equipo | Iniciales; "+n" | Nombre completo accesible | wasichai |
| Tooltip, Popover | Explicar sin navegar | El tooltip no contiene acciones | Aparece con foco y con hover | wasichai |
| Dropdown | Más acciones | Solo transiciones permitidas al rol; la destructiva al final, en rojo | `role="menu"` | wasichai |
| Tabs | Secciones de una ficha | Con conteo | `role="tablist"`; flechas para moverse | wasichai |
| Breadcrumbs y Rastro | Dónde vive el registro y por dónde se llegó | El rastro aparece con más de un salto | `nav` con `aria-label` distinto | portal |
| Accordion | Bloques plegables de una ficha | — | `aria-expanded` | wasichai |
| Card, MetricCard, KpiCard | Bloques e indicadores | KPI con delta y desglose; "n de N" con barra; "no disponible" | La cifra y su contexto se leen juntos | portal |
| DataTable | Toda lista | Densa y cómoda; encabezado y primera columna fijos; orden; columnas; selección; grupos por prioridad | `<table>` real; `aria-sort`; encabezados de grupo | wasichai + portal |
| FilterBar, FilterChip | Filtros en la URL | Chips con valor; "+ Filtro"; limpiar | Cada chip quitable con teclado | portal |
| SavedViews | Vistas del sistema, compartidas y mías (ASM-083) | Pestañas agrupadas; chip de visibilidad; guardar vista | `role="tablist"` | portal |
| SavedViewDialog | Guardar o compartir una vista (ASM-083) | Nombre; visibilidad solo yo, equipo del proyecto u organización (esta última solo para quien puede) | `role="dialog"`; la visibilidad es un `fieldset` de radios | portal |
| LanguageSwitch | Idioma de la interfaz (ASM-084) | Menú del perfil, enlaces del login y «Más» del teléfono; cada idioma en su propia lengua | `lang` en cada opción; la página cambia su `lang` | portal |
| MapAttribution | Atribución del mapa base (ASM-085) | Siempre visible; se pliega a «ⓘ» en pantallas chicas | Texto real enlazable; no tapa los controles | `@wasichai/gis` + portal |
| BulkActionBar | Acciones masivas | Solo acciones válidas para todos los seleccionados | Anuncia "n seleccionados" | portal |
| Pagination | Listas largas | 25, 50, 100 por página | `nav` con página actual | wasichai |
| EmptyState | Vacío inicial, por filtros y sin permiso | Con acción principal | Encabezado y texto reales | portal |
| Skeleton | Carga | Con la forma de lo que carga; mensaje y reintento a los 10 s | `aria-busy` | portal |
| Modal, ConfirmationDialog | Decisiones | Diálogo de transición (§6.2); confirmación destructiva con motivo | Foco atrapado; Esc cierra si no hay cambios | wasichai |
| Drawer | Vista previa y formularios cortos | 400 y 560 px; reemplazable, no apilable | Foco al abrir; vuelve al origen al cerrar | wasichai |
| Toast | Confirmaciones | Éxito con enlace; error persistente; conflicto de versión con "Recargar" | `role="status"` o `alert` | wasichai |
| Alert, Callout | Reglas y avisos en contexto | Peligro, advertencia, información | Texto que dice qué hacer | portal |
| Timeline, ActivityFeed | Historia de un registro | Hechos de negocio en lenguaje natural | Lista ordenada | portal |
| CommentThread | Conversación sobre un registro | Menciones | Cada comentario con autor y hora | portal |
| FileUploader, FilePreview | Evidencias | Tipo de evidencia y clasificación obligatorios; vista previa con metadatos | Zona de arrastre con botón alternativo | wasichai documents + portal |
| ProgressBar, ProgressRing | Tope de campaña, mitigación, metas | Con valor y texto | `role="progressbar"` con valores | portal |
| StepIndicator, WorkflowStepper | Formularios por pasos y ciclo de un registro | El stepper muestra el camino feliz; los desvíos son estados | Paso actual con `aria-current="step"` | portal |
| Map, MapLayerControl | Mapa del proyecto y territorial | Capas en cuatro grupos (Referencia, Proyecto, Tenencia, Gestión); candado en capas sensibles | Alternativa en tabla sincronizada | `@wasichai/gis` + portal |
| Chart | Gráficos | ECharts con "Ver datos" debajo | Tabla de datos y descripción | portal |
| CommandPalette | Búsqueda global (⌘K) | Recientes, sugeridas, categorías, vista previa | Patrón combobox con listbox | portal |
| ContextBar, TerritoryChip | Contexto de proyecto y territorio | Chip quitable | `nav` "Contexto" | portal |
| TraceabilityPanel | Origen y consecuencias de un registro | Dos bloques navegables | Encabezados reales | portal |
| AiSuggestionCard, AiSummary, AiAnswer | Propuestas, resúmenes y respuestas de IA | Con evidencia, confianza, modelo y fuentes; estado "no disponible" | Marca de procedencia en el nombre accesible | portal |
| AssistantPanel | Asistente global y contextual (S05) | Modos datos y documentos; "no disponible" con razón y alternativa; organización sin IA | `complementary` con encabezado; la respuesta se anuncia con `role="status"` | portal |
| Citation, SourceCard | Fuentes de una respuesta de AI-06 | Llamada numerada; cita literal; clasificación; "fuente restringida" | La llamada enlaza a su fuente; la cita en `blockquote` | portal |
| ConnectionIndicator | Estado de conexión en el móvil | En línea, sin señal, enviando, conflicto, sesión vencida | Texto además del color; `role="status"` al cambiar | portal |
| SyncQueueItem, ConflictResolver | Cola local y conflictos de campo (ASM-081) | Por enviar, enviando, enviado, conflicto, error; versión de la oficina frente a la del teléfono | Cada estado con texto; dos salidas explícitas | portal |
| CurrencyGauge | Topes y presupuestos | Uno por moneda; marca de lo aprobado; advertencia de otra moneda | `role="progressbar"` con valor y texto | portal |

## 6. Patrones núcleo

### 6.1 Ficha de entidad

La misma anatomía para todo objeto importante [PRM-02 §26]:

1. **Contexto:** barra de contexto (proyecto y territorio) y breadcrumbs; el rastro si se llegó por un recorrido.
2. **Encabezado:** tipo y código en `code`, título, chips de estado, plazo y prioridad, territorio; acciones a la derecha: la
   primaria (la siguiente transición más probable para el rol), secundarias, "Más" y el botón de IA contextual.
3. **WorkflowStepper** con el camino del objeto.
4. **Panel de decisión:** cuatro tarjetas fijas que responden sin buscar: estado actual (desde cuándo y quién), siguiente
   acción (con lo que falta para darla), responsable y vencimiento con escalamiento [PRM-02 §15].
5. **Pestañas** del objeto (por ejemplo, en un compromiso: Resumen, Acciones, Entregas, Evidencias, Presupuesto, Riesgo,
   Actividad, Auditoría).
6. **Panel de trazabilidad** a la derecha: "De dónde viene" y "Qué generó" (ux-architecture §8).
7. **Últimos movimientos** y enlace a la actividad completa y a la auditoría.

Árbol de componentes de referencia (se detalla por pantalla en `screens.md`):

```
EntityDetailPage
 ├── ContextBar
 ├── Breadcrumbs · Trail
 ├── EntityHeader
 │    ├── StatusBadge (estado · plazo · prioridad)
 │    └── EntityActions (primaria · secundarias · Más · IA)
 ├── WorkflowStepper
 ├── DecisionPanel (estado · siguiente acción · responsable · vencimiento)
 ├── EntityTabs
 │    └── <pestañas del objeto>
 └── TraceabilityPanel · RecentActivity
```

### 6.2 Transición de workflow con motivo

- Toda transición se abre en un diálogo con: estado de origen → estado de destino, **reglas** que se cumplen o no (las guardas
  del servidor anticipadas en lenguaje del dominio), **qué pasará** (tareas, documentos emitidos, avisos) y el **motivo**,
  obligatorio cuando el objeto es `requiresReason` o la transición lo exige; se envía en `X-Change-Reason` (RQ-AUD-002).
- Si una regla no se cumple, el botón de confirmar no se habilita y el diálogo dice qué falta y a quién acudir. Si el servidor
  rechaza igual (400 por los datos o 409 por el estado, `api.md` §3.3), su `detail` se muestra en lenguaje del dominio; nunca
  solo el código de la regla.
- Una transición con motivo opcional muestra el campo como opcional, no lo esconde.
- Conflicto de versión (412, ADR-0025): "Otra persona cambió este registro" con "Recargar"; lo escrito en el diálogo se
  conserva.
- Las transiciones que el rol no tiene no aparecen; las que tiene pero hoy no puede dar aparecen deshabilitadas con la razón.

### 6.3 Formulario por pasos

- Formularios largos en pasos con un indicador lateral, guardado automático del borrador y "Guardar y salir" [PRM-02 §27].
- Validación en línea con Zod; el paso no avanza con errores, pero se puede volver a cualquier paso ya visitado.
- Los pasos siguen las reglas del dominio. Crear un compromiso: 1 Origen (interacción, solicitud aceptada, cláusula o carta;
  justificación si nace de un taller o una reunión informativa, RN-ENG-001), 2 Qué se promete (descripción, objetivo, recurso,
  cantidad y unidad), 3 Partes y beneficiarios, 4 Plazo e hitos, 5 Monto y campaña (con el tope a la vista, RN-INV-003),
  6 Responsable, 7 Revisar y enviar (con los campos mínimos de la guarda D1). La evidencia no se pide al crear: se exige al
  registrar el cumplimiento (RN-COM-001).
- Cancelar un borrador pide confirmación; salir con cambios sin guardar avisa.

### 6.4 Lista con vistas guardadas y acciones masivas

- Encabezado con título, conteo y alcance ("17 en el proyecto"), acción de creación y exportación.
- **Vistas guardadas** en pestañas: las del sistema por rol (por ejemplo, "Atención", "Mis compromisos", "Vencen este mes",
  "Prioridad MUY ALTA", "Todos"), las compartidas y las mías (ASM-083). Una vista guarda filtros, columnas, orden y densidad,
  nunca datos: cada persona ve solo los registros que su rol puede leer.
- **Compartir:** «Guardar esta vista…» abre `SavedViewDialog` con la visibilidad solo yo, equipo del proyecto u organización.
  Cada pestaña compartida lleva un chip con su alcance y, al abrirla, quién la compartió. Solo quien la creó la cambia o la
  borra; los demás guardan una copia propia o la ocultan de sus pestañas. Borrar una vista que otros usan avisa y pide
  confirmación. «Gestionar vistas» reúne todo esto (`screens.md`, Gestionar vistas; lienzo de la etapa 3, página 24).
- **Filtros** como chips con su valor, en la URL; "+ Filtro" y "Limpiar".
- **Agrupación por atención** cuando la vista lo pide: Vencidos, Por vencer, Bloqueados, en ese orden [PRM-02 §15].
- **Selección** con casillas; la barra de acciones masivas ofrece solo lo válido para todos los seleccionados (reasignar,
  asignar verificador, cambiar prioridad, exportar). No hay cambio masivo de fecha de vencimiento (RF-COM-003), y el
  verificador asignado nunca es el responsable del compromiso (RN-COM-005).
- Antes de una operación masiva, un diálogo dice cuántos registros cambiarán y cuáles no se pueden cambiar y por qué; después,
  el resultado por registro, con los fallidos seleccionados para reintentar. Cada registro lleva su clave de idempotencia
  (ADR-0025).
- Montos con su moneda; nunca se suman monedas distintas (ASM-063).

### 6.5 Vacío, carga, error y permisos

| Situación | Qué se muestra |
|---|---|
| Vacío inicial | Qué es la sección, cómo nace el primer registro y la acción para crearlo [PRM-02 §29] |
| Vacío por filtros | Los filtros activos, una pista ("sin el filtro de territorio hay 3") y "Limpiar filtros" |
| Cargando | Skeleton con la forma real; el encabezado de una ficha aparece primero; reintento a los 10 s |
| Error de carga | Qué falló, que los filtros se conservan, "Reintentar" y el identificador de correlación (`X-Correlation-Id`) |
| Sin permiso (403) | Por qué no puede ver el registro y a quién pedir acceso, sin revelar datos |
| Solo lectura | Aviso "no estás asignado a este proyecto"; las acciones de escritura no aparecen (alcance A, `security.md` §9) |
| Campo o cifra restringidos | Candado y "no disponible", nunca vacío ni cero (RQ-DSH-032) |
| Sesión vencida (401) | Volver a ingresar sin perder el borrador (WAS-ADR-0031) |
| Módulo no instalado (404) | "Esta sección llega en una próxima fase", sin error técnico (ADR-0024) |
| IA no disponible | Por rol (UPS-14), por organización o por falla del proveedor, con la razón |

### 6.6 Auditoría legible

Tabla y línea de tiempo con: **cuándo**, **quién** (persona, sistema o trabajo programado), **qué hizo** en lenguaje del
dominio, **antes → después**, **por qué** (el motivo) y **desde dónde** (portal, API, automatización, trabajo programado o "IA
aceptada por persona") [PRM-02 §42]. Filtros por actor, acción, campo y fecha; exportación. Las descargas de evidencias
restringidas y las emisiones de entregables se consultan en el registro de exportaciones (`export_log`, RQ-AUD-011).

## 7. Accesibilidad (WCAG 2.2 AA)

- **Teclado:** todo se opera con teclado; orden de foco lógico; atajos documentados (⌘K, ↑ ↓, ↵, Esc); sin trampas salvo en
  diálogos, que devuelven el foco al cerrarse.
- **Foco visible:** anillo de 2 px que cumple 3:1 sobre cualquier fondo; nunca se quita.
- **Contraste:** texto ≥ 4.5:1, texto grande y bordes de controles ≥ 3:1, verificado por script (§10).
- **Sin depender del color:** estados con ícono y etiqueta; niveles con barras; series de gráficos con forma o patrón además
  del color.
- **Etiquetas y ARIA:** `<label>` visible en cada campo; nombres accesibles en botones de ícono; landmarks (`header`, `nav`,
  `main`, `aside`); tablas con `<th scope>` y `aria-sort`.
- **Gráficos y mapas:** tabla "Ver datos" bajo cada gráfico (RQ-DSH-035); el mapa siempre tiene la lista sincronizada como
  alternativa; leyendas en texto.
- **Objetivos:** mínimo 24 × 24 (2.5.8); 32 px en escritorio denso y 44 px en táctil.
- **Arrastrar:** toda acción de arrastre tiene alternativa sin arrastre (2.5.7), como subir archivos o reordenar.
- **Ayuda y autenticación:** la ayuda está en el mismo lugar en todas las páginas (3.2.6); el login permite pegar y usar
  gestores de contraseñas (3.3.8).
- **Movimiento:** se respeta `prefers-reduced-motion`.
- **Idioma:** `lang="es-PE"` y cambio a inglés sin recargar (RNF-I18N-001).

## 8. Responsive

- **Escritorio (≥ 1280):** experiencia completa.
- **Tableta (768–1279):** navegación en panel, fichas en una columna, mapa a pantalla completa con hoja inferior; tablas con
  desplazamiento horizontal dentro de su caja y la primera columna fija.
- **Móvil (< 640):** no se mete el sistema de escritorio en el teléfono [PRM-02 §31]. Solo: mis tareas, visitas de campo,
  registrar interacción y observación con foto y ubicación, aprobaciones, avisos y consulta rápida. En el MVP, en línea
  (ASM-021); la captura sin conexión está diseñada para después (ASM-081, `screens.md` §15). Objetivos táctiles de 48 px.
- **Pantallas de escritorio en el teléfono:** una pantalla que no está pensada para el teléfono (editor de acta, importación
  geoespacial, formulario por pasos, tableros con mapa) no se encoge: muestra «Esta pantalla es para una pantalla más grande»
  con la lectura que sí cabe y el enlace para copiar. Lo dibuja la hoja `Shell-Telefono` del lienzo de la etapa 3.

## 9. Guía de implementación

- **Tokens:** importar `docs/ux/tokens.css` y `docs/ux/themes.css` (copiados al portal) después de
  `@wasichai/ui/theme.css`. Usar siempre los tokens,
  nunca colores sueltos; los colores de ECharts y MapLibre se leen de las mismas variables.
- **Componentes de estado:** un solo `StatusBadge` que recibe el objeto, el estado y la situación, con un mapa de tono e ícono
  por workflow tomado de §4.1; las etiquetas de estados y valores salen del catálogo i18n del portal por clave (§9.1), con la
  etiqueta de la metadata como respaldo en español (CON-051).
- **Alcance:** las páginas de lista y de ficha reciben `scope` (`{ kind: 'mine' }` o `{ kind: 'project', projectId }`) y leen
  el territorio y los filtros de la URL (ASM-075, ASM-076).
- **Sin literales de rol:** la visibilidad de acciones se decide con permisos y acciones declaradas, nunca comparando nombres
  de rol; `boundaries.test` lo verifica (RQ-SEC-005).
- **Motivo de cambio:** un hook único de transición abre el diálogo de §6.2, envía `X-Change-Reason`, `Idempotency-Key` y la
  precondición de versión, y traduce los errores 400, 403, 409, 412 y 428 (`api.md` §3.3).
- **Pruebas:** Vitest y Testing Library para componentes y estados; Playwright desde F2 (ASM-066) para los recorridos del
  hilo de contexto.

### 9.1 Idiomas

**ASSUMPTION (ASM-084):** el portal está en español (por defecto), inglés y quechua (lienzo de la etapa 3, página 23).

- **Catálogo:** i18next con react-i18next, como srtm-ui; un archivo por idioma y espacio (`es`, `en`, `qu`, con la variante
  del quechua como subetiqueta cuando se confirme, por ejemplo `quz` o `quy`). Números, fechas y monedas en formato es-PE en
  los tres idiomas.
- **Valores de ENUM:** se guardan en español (ASM-025) y se traducen con una clave por valor, por ejemplo
  `enum.grievance_severity.ALTA`; lo mismo para estados y transiciones de workflow. Sin clave, se muestra el valor guardado.
- **Lo que no se traduce:** `/admin` y las vistas generadas por metadata (CON-051), los entregables y los datos que escriben
  las personas (descripciones, actas, comentarios). Se muestran tal cual, con `lang="es"` si la página está en otro idioma.
- **Traducción faltante:** se muestra el texto en español con una marca discreta de idioma, nunca la clave técnica; un informe
  del catálogo lista lo que falta por idioma antes de cada versión.
- **Textos que se alargan:** el quechua y el inglés pueden ser más largos; ningún botón ni columna tiene ancho fijo, las
  etiquetas pasan a dos líneas antes que cortarse y el truncado con «…» solo se usa en celdas de tabla, con el texto completo
  en el título.
- **Traducción profesional:** las cadenas en quechua las hace y revisa un traductor; el lienzo usa textos de ejemplo.

## 10. Verificación de contraste

Un script compara cada par de texto y fondo de §3 y del lienzo. Resultado de esta etapa: todos los pares de texto entre 5.04:1 y
16.48:1 en claro y entre 4.72:1 (blanco sobre `primary-hover` oscuro) y 14.31:1 en oscuro; foco 5.22:1 (claro) y 7.54:1
(oscuro); bordes de controles entre 3.05:1 y 3.50:1 en claro (sobre `surface-sunken`, `bg` y `surface`) y entre 3.03:1 y
3.24:1 en oscuro. Ningún par queda por debajo del mínimo.

## 11. Trazabilidad

- **Requisitos:** RNF-USA-001, RNF-I18N-001, RNF-MNT-001, RQ-DSH-032, RQ-DSH-035, RQ-GIS-014, RQ-GIS-015, RQ-AUD-002,
  RQ-AUD-011, RQ-AI-003, RQ-AI-004, RQ-AI-012, RQ-SEC-005, RN-WFL-001, RN-COM-001, RN-ENG-001, RN-INV-003, RF-COM-003.
- **Supuestos:** ASM-021, ASM-025, ASM-063, ASM-066, ASM-075, ASM-076, ASM-077, ASM-079, ASM-081, ASM-082, ASM-083, ASM-084,
  ASM-085, ASM-086.
- **Documentos:** [workflows.md](../workflows.md), [dashboards.md §3 y §9](../dashboards.md), [gis.md §2 y §7.2](../gis.md),
  [security.md §5 y §9](../security.md), [api.md §3](../api.md), [ADR-0024](../adr/0024-interfaz-portal-y-admin.md),
  [ADR-0025](../adr/0025-concurrencia-e-idempotencia.md).
- **Fuentes:** [PRM-02 §4, §7, §8, §23, §25–§31, §35, §42, §43], [WAS-ADR-0035].
