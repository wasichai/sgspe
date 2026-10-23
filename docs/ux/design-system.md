# Design system de SGSPE

Sistema de Gestión Social para Proyectos Extractivos · Diseño UX/UI · etapas 1 y 2 de 3 · versión 0.3 · 2026-10-23

Este documento define los fundamentos visuales, los tokens, el sistema de estados, el catálogo de componentes y los patrones
núcleo del portal. Se apoya en la [arquitectura de UX](ux-architecture.md) y en los diseños de la Fase 0; los IDs se definen
solo en el [análisis](../requirements-analysis.md). Los tokens listos para usar están en [`tokens.css`](tokens.css) y la
versión visual de todo lo que sigue está en el lienzo de diseño (páginas "03 Design system" y "04 Patrones núcleo").

El rediseño de UX del 2026-10-20 ([`redesign/03`](redesign/03-direccion-visual-y-design-system.md) y
[`redesign/05`](redesign/05-especificacion-de-componentes.md)) está implementado en `sgspe-ui` y llevado aquí el 2026-10-23:
los tokens nuevos (§3.1 y §3.3), las reglas de espaciado (§3.5), los puntos de corte (§3.6), los componentes del portal
(§5.1), la regla de la acción principal (§6.1) y el contraste (§10). Las decisiones se citan como `DD-NN`
([`redesign/07`](redesign/07-decisiones-y-compatibilidad.md)).

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

Todos los pares de texto superan 4.5:1 y los bordes de controles 3:1, en claro y en oscuro (§10). Ningún token existente
cambió de valor con el rediseño; se agregaron `surface-raised` y `border-control` (DD-15).

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `bg` | `#F5F6F3` | `#111614` | Fondo de la aplicación |
| `surface` | `#FFFFFF` | `#171D1A` | Tarjetas, tablas, paneles, diálogos |
| `surface-sunken` | `#EEF0EC` | `#0D110F` | Encabezados de tabla, controles segmentados, bandas |
| `surface-hover` | `#F9FAF8` | `#1D2420` | Fila o ítem bajo el cursor |
| `surface-raised` | `#FFFFFF` | `#1D2420` | Lo que flota: menús, popovers, el calendario de `CampoFecha`, drawers y diálogos. En oscuro la sombra no separa; esta superficie sí (DD-15) |
| `border` | `#D9DDD6` | `#2A332E` | Separadores y contorno de tarjetas (decorativo) |
| `border-subtle` | `#E4E7E1` | `#222A26` | Separadores dentro de una tarjeta |
| `border-strong` | `#838B85` | `#5E6A64` | Bordes con énfasis y contorno de los controles propios del portal (3:1) |
| `border-control` | `var(--sg-border-strong)` | `#66726C` | Límite de `Input`, `Textarea`, `Select` y `CampoFecha`, también los de `@wasichai/ui` por su `data-slot` (salvo el inválido, que conserva su borde `danger`). En oscuro tiene valor propio: el borde fuerte daba 2.81:1 sobre `surface-raised` (DD-21) |
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

Desde el rediseño, los colores de datos son utilidades de Tailwind (`@theme inline` de `tokens.css`): `bg-sg-data-1`,
`bg-sg-stance-favor`, `bg-sg-risk-high`, `bg-sg-map-ground`… Los estilos en línea, el SVG, MapLibre y ECharts siguen
leyendo las variables `--sg-*`.

| Escala | Tokens | Valores | Uso |
|---|---|---|---|
| Categórica | `data-1` a `data-6` | `#2B5F8F` · `#C9772B` · `#1D5C52` · `#8E5A9E` · `#6B7F2A` · `#838B85` | Series sin orden, en ese orden fijo; azul y naranja primero, que se distinguen también en luminosidad |
| Postura (divergente) | `stance-favor`, `stance-neutral`, `stance-open`, `stance-against` | A FAVOR `#2B5F8F` · NEUTRAL `#B9BFB6` · EN CONTRA PERO ABIERTO A ESCUCHAR `#D9A35B` · EN CONTRA `#A33A2A` | KPI-STK-02 y la ficha territorial |
| Nivel de riesgo | `risk-low`, `risk-medium`, `risk-high`, `risk-critical` | BAJO `#E5F0E7` · MEDIO `#FBF0D9` · ALTO `#F3C9B8` · CRITICO `#A33A2A` | Mapa de calor 5 × 5 (KPI-RSK-02) |
| Mapa | `map-ground` | Suelo `#E8EBE3` (oscuro `#1B211E`); territorio comunal en arena; área del proyecto en pino discontinuo; selección con borde de 2.5 px | Mapa territorial y del proyecto; la paleta de capas se comparte con los mapas de informe (`gis.md` §7.2) |

Los gráficos van en color sólido, sin trama (DD-15): la serie se distingue por su posición, su etiqueta, la leyenda en texto
y la tabla «Ver datos».

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
| Bordes | 1 px separador y tarjeta; 1 px `border-control` en controles; 2 px énfasis |
| Foco | Anillo de 2 px `focus` con separación de 2 px, en todo elemento interactivo, también sobre fondos de color |

El espaciado no tiene tokens propios: usa la escala de 4 px de Tailwind con estas reglas (DD-15):

| Contexto | Valor |
|---|---|
| Dentro de un control | `px-3`; alto de 36 px en formularios y acción principal, 32 px en cabeceras, tablas y filtros, 44 px en táctil |
| Entre etiqueta y control | `space-y-1` (4 px) |
| Entre campos de un grupo | `space-y-4` (16 px) |
| Entre grupos de un formulario | `space-y-6` (24 px), con título `h3` |
| Relleno de una `Seccion` | `p-4` (16 px) en todos los anchos |
| Dentro de una `Seccion` | `space-y-2` entre el encabezado y el contenido |
| Entre secciones de una página | `space-y-4`; el inicio, `space-y-6` |
| Cabecera de página a contenido | 16 px |
| Celdas de tabla | 44 px de alto por defecto; 32 px en la densidad compacta de los tableros |

### 3.6 Breakpoints y layout

Los puntos de corte de `tokens.css`: `sm` 640, `md` 768, `lg` 1024, `xl` 1280 y `2xl` **1440** px. En SGSPE `2xl` no es el
1536 de Tailwind: lo que se ve «desde 1536 px» usa `min-[96rem]` (DD-24).

| Ancho | Comportamiento |
|---|---|
| ≥ 1536 (`min-[96rem]`) | El lema de la marca en la barra superior; las columnas opcionales de `TablaPriorizada` sin elección en «Columnas» |
| ≥ 1440 (`2xl`) | Contenido de hasta 1440 px; márgenes de 32 px; la barra superior con solo sus seis controles |
| ≥ 1280 (`xl`) | Columnas secundarias de `TablaPriorizada` |
| ≥ 1024 (`lg`) | Navegación expandida a 240 px con títulos de bloque y «Contraer» (DD-10); ficha con el cuerpo y un panel de 320 px al costado (trazabilidad, «Qué pasará») |
| 768–1023 (`md`) | Navegación de 52 px con solo los íconos; márgenes de 24 px |
| < 768 | Sin barra lateral: el botón de menú abre el panel con la cuenta, el proyecto y las secciones; la búsqueda de una lista ocupa el ancho y sus filtros frecuentes pasan a «Filtros (N)»; las acciones de una ficha, en una barra fija al pie; márgenes de 16 px |
| < 640 (`sm`) | Barra inferior de cuatro destinos, la misma para toda cuenta (`ux-architecture.md` §7.1, DD-17); la cuenta pasa al panel del menú; las listas, a tarjetas (`ListaEnTarjetas`) [PRM-02 §31] |

Barra superior de 56 px; drawer de 400 px (vista previa) o 560 px (formulario); filas de tabla de 32 px (densa) o 44 px
(cómoda); objetivo táctil de 44 px (48 px en la barra inferior); objetivo mínimo de escritorio de 32 px, por encima del 24 × 24
de WCAG 2.2.

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
| DatePicker, DateRangePicker | Fechas y periodos: `CampoFecha` (§5.1), en todo el portal; un periodo son dos | dd/mm/aaaa y 24 h; calendario de mes. Los atajos de periodo y los feriados marcados no están hechos | Tecla por tecla en la grilla | portal |
| Checkbox, Radio, Switch | Opciones | Fieldset con leyenda | Nativos o `role="switch"` | wasichai |
| Badge | Conteos | Neutro, peligro, advertencia, IA | Texto que explica el conteo en `title` y en el nombre accesible del enlace | portal |
| StatusBadge | Estado, situación, nivel, procedencia (§4) | Las cuatro familias | Ícono + etiqueta; nunca solo color | portal |
| Avatar, AvatarGroup | Personas del equipo | Iniciales; "+n" | Nombre completo accesible | wasichai |
| Tooltip, Popover | Explicar sin navegar | El tooltip no contiene acciones | Aparece con foco y con hover | wasichai |
| Dropdown | Más acciones (`MasAcciones`) | Solo transiciones permitidas al rol; las destructivas al final, en rojo (§6.1) | `role="menu"`; se cierra al elegir | wasichai |
| Tabs | Secciones de una ficha | Con conteo | `role="tablist"`; flechas para moverse | wasichai |
| Breadcrumbs y Rastro | Dónde vive el registro y por dónde se llegó | El rastro aparece con más de un salto | `nav` con `aria-label` distinto | portal |
| Accordion | Bloques plegables de una ficha | — | `aria-expanded` | wasichai |
| Card, MetricCard, KpiCard | Bloques e indicadores: toda superficie con título es una `Seccion` (§5.1) | KPI con delta y desglose; "n de N" con barra; "no disponible"; `KpiCard` anidada, sin caja propia, dentro de una sección | La cifra y su contexto se leen juntos | portal |
| DataTable | Toda lista: las anchas, con `TablaPriorizada` (§5.1) | Densa y cómoda; prioridad de columnas y «Columnas»; orden; selección; grupos por prioridad | `<table>` real; `aria-sort`; encabezados de grupo | wasichai + portal |
| FilterBar, FilterChip | Filtros en la URL: `BarraDeFiltros` (§5.1) | Vista, búsqueda, tres frecuentes, «Más filtros», chips con valor y «Limpiar filtros» | Cada chip quitable con teclado | portal |
| SavedViews | Vistas del sistema, compartidas y mías (ASM-083) | En las listas rediseñadas, el `SelectorDeVista` (§5.1); Proyectos, Mis tareas y Aprobaciones conservan las pestañas de `VistasGuardadas` | `radiogroup` en el selector; `role="tablist"` en las pestañas | portal |
| SavedViewDialog | Guardar o compartir una vista (ASM-083) | Nombre; visibilidad solo yo, equipo del proyecto u organización (esta última solo para quien puede) | `role="dialog"`; la visibilidad es un `fieldset` de radios | portal |
| LanguageSwitch | Idioma de la interfaz (ASM-084) | Menú del perfil, enlaces del login y «Más» del teléfono; cada idioma en su propia lengua | `lang` en cada opción; la página cambia su `lang` | portal |
| MapAttribution | Atribución del mapa base (ASM-085) | Siempre visible; se pliega a «ⓘ» en pantallas chicas | Texto real enlazable; no tapa los controles | `@wasichai/gis` + portal |
| BulkActionBar | Acciones masivas | Solo acciones válidas para todos los seleccionados | Anuncia "n seleccionados" | portal |
| Pagination | Listas largas | 25, 50, 100 por página | `nav` con página actual | wasichai |
| EmptyState | Vacío inicial y por filtros (`Vacio`); sin acceso, `EstadoSinAcceso` (§5.1) | Con acción principal | Encabezado y texto reales | portal |
| Skeleton | Carga | Con la forma de lo que carga; mensaje y reintento a los 10 s | `aria-busy` | portal |
| Modal, ConfirmationDialog | Decisiones | Diálogo de transición (§6.2); confirmación destructiva con motivo | Foco atrapado; Esc cierra si no hay cambios | wasichai |
| Drawer | Vista previa y formularios cortos | 400 y 560 px; reemplazable, no apilable | Foco al abrir; vuelve al origen al cerrar | wasichai |
| Toast | Confirmaciones | Éxito con enlace; error persistente; conflicto de versión con "Recargar" | `role="status"` o `alert` | wasichai |
| Alert, Callout | Reglas y avisos en contexto | Peligro, advertencia, información | Texto que dice qué hacer | portal |
| Timeline, ActivityFeed | Historia de un registro | Hechos de negocio en lenguaje natural | Lista ordenada | portal |
| CommentThread | Conversación sobre un registro | Menciones | Cada comentario con autor y hora | portal |
| FileUploader, FilePreview | Evidencias | Tipo de evidencia y clasificación obligatorios; vista previa con metadatos | Zona de arrastre con botón alternativo | wasichai documents + portal |
| ProgressBar, ProgressRing | Tope de campaña, mitigación, metas | Con valor y texto | `role="progressbar"` con valores | portal |
| StepIndicator, WorkflowStepper | Formularios por pasos (`FormularioPorPasos`) y ciclo de un registro (`CaminoDelFlujo`) | El stepper muestra el camino feliz; los desvíos son estados; en una ficha va plegado dentro del panel de decisión (§6.1) | Paso actual con `aria-current="step"`; «Ver el camino» con `aria-expanded` | portal |
| Map, MapLayerControl | Mapa del proyecto y territorial | Capas en cuatro grupos (Referencia, Proyecto, Tenencia, Gestión); candado en capas sensibles | Alternativa en tabla sincronizada | `@wasichai/gis` + portal |
| Chart | Gráficos | ECharts con "Ver datos" debajo | Tabla de datos y descripción | portal |
| CommandPalette | Búsqueda global (⌘K) | Recientes, sugeridas, categorías, vista previa | Patrón combobox con listbox | portal |
| ContextBar, TerritoryChip | Contexto de proyecto y territorio | Chip quitable | `nav` "Contexto" | portal |
| TraceabilityPanel | Origen y consecuencias de un registro | Dos bloques navegables | Encabezados reales | portal |
| AiSuggestionCard, AiSummary, AiAnswer | Propuestas, resúmenes y respuestas de IA | Con evidencia, confianza, modelo y fuentes; estado "no disponible" | Marca de procedencia en el nombre accesible | portal |
| AssistantPanel | Asistente global y contextual (S05) | Modos datos y documentos; "no disponible" con la razón; organización sin IA | `complementary` con encabezado; la respuesta se anuncia con `role="status"` | portal |
| Citation, SourceCard | Fuentes de una respuesta de AI-06 | Llamada numerada; cita literal; clasificación; "fuente restringida" | La llamada enlaza a su fuente; la cita en `blockquote` | portal |
| ConnectionIndicator | Estado de conexión en el móvil | En línea, sin señal, enviando, conflicto, sesión vencida | Texto además del color; `role="status"` al cambiar | portal |
| SyncQueueItem, ConflictResolver | Cola local y conflictos de campo (ASM-081) | Por enviar, enviando, enviado, conflicto, error; versión de la oficina frente a la del teléfono | Cada estado con texto; dos salidas explícitas | portal |
| CurrencyGauge | Topes y presupuestos | Uno por moneda; marca de lo aprobado; advertencia de otra moneda | `role="progressbar"` con valor y texto | portal |

### 5.1 Componentes del portal del rediseño

Viven en `sgspe-ui/src/portal/componentes/`, componen primitivos de `@wasichai/ui`, llevan sus textos en `textos/comun.ts` en
los dos idiomas y tienen sus pruebas al lado. Su especificación y criterios de aceptación están en
[`redesign/05`](redesign/05-especificacion-de-componentes.md) §1; aquí, lo que quedó.

| Componente | Para qué | Reglas | Accesibilidad |
|---|---|---|---|
| `Seccion` | Toda superficie con título de una página (DD-08) | Borde, `p-4` y fondo `surface`; título `h2` con estilo `h3`, descripción y hasta dos acciones a la derecha. Dentro de otra `Seccion` pierde la caja y baja a `h3`. `elemento="aside"` para un panel al costado. La caja no se escribe a mano fuera de ella (`superficies.test.ts`, con cinco excepciones: tarjetas clicables, ítems de lista y un panel desplegable) | El título nombra la región (`aria-labelledby`); sin título, `etiqueta` |
| `CabeceraDeFicha` | La cabecera de una ficha con la regla de la acción principal (§6.1, DD-02) | Antetítulo «Tipo · código · categoría» en sans; título que dice qué es; chips de situación, nunca el estado del flujo; una primaria, hasta dos secundarias y «Más acciones»; lo que impide la primaria, debajo | Orden visual y de foco: secundarias, «Más acciones», primaria; la primaria deshabilitada se describe con lo que falta |
| `SelectorDeVista` | Las vistas de una lista en una fila, con su conteo (DD-03) | Hasta cuatro a la vista y el resto en «Más vistas»; la actual nunca queda escondida; escribe `?view=` y conserva los demás parámetros | `radiogroup` con tabulación itinerante: flechas, Home y End |
| `SelectorDePagina` | Las páginas de un módulo que tienen ruta propia: Compromisos y Acuerdos, las de Riesgos (DD-23) | El mismo aspecto que el selector de vista; navega entre rutas sin cambiar ninguna; con una sola página no se dibuja | Navegación de enlaces con `aria-current`, no un `radiogroup`: las flechas no sacan a nadie de su página |
| `BarraDeFiltros` | Los filtros de una lista (DD-04) | El selector de vista, la búsqueda, hasta tres filtros frecuentes, «Más filtros» en un drawer, un chip por filtro aplicado y «Limpiar filtros», todo en la URL. La búsqueda se ve al instante y llega a la URL 300 ms después de la última tecla, o con Enter. Por debajo de `md`, los frecuentes pasan al drawer y el botón dice «Filtros (N)» | El «×» de cada chip es un botón con nombre («Quitar el filtro …») |
| `CampoFecha` | Toda fecha y fecha con hora del portal (DD-07) | Muestra y lee `dd/mm/aaaa` y la hora de 24 h en los dos idiomas, sea cual sea el navegador; acepta `20102026`, `20/10/2026` o `20-10-2026` y normaliza al salir; el valor es el día o la hora de Lima (`ZONA_DE_LIMA`). Error de formato y de rango al salir del campo. El botón, que nombra su campo («Elegir Desde en el calendario»), abre `CalendarioDeFecha`, una grilla de mes que escribe el día elegido y conserva la hora. Ningún `<input type="date">` en el portal (`camposDeFecha.test.ts`) | Calendario con el patrón «Date Picker Dialog» de WAI-ARIA, no modal: lunes primero, flechas por día o semana, RePág y AvPág por mes (con Mayús, por año), Inicio y Fin, Enter o Espacio; Esc cierra y devuelve el foco al botón |
| `TablaPriorizada` | Las listas anchas (DD-05) | Columnas esenciales siempre, secundarias desde `xl` y opcionales desde 1536 px, salvo que «Columnas» las muestre u oculte en todos los anchos; la elección se recuerda por tabla en el navegador (`sgspe.columnas.<tabla>`). Con `ocultarUniformes`, una columna no esencial con un único valor sale y su valor queda en una nota. El orden por columna lo pide quien la usa al servidor; hoy solo en Alertas (S41), y no viaja en la URL: dura la visita | Encabezado ordenable como botón y `aria-sort` en el `th`; cada fila enfocable: Enter abre la ficha y Espacio, la vista previa (`?peek=`) |
| `ListaPriorizada` | Lo más urgente de un grupo, en lugar de un muro de enlaces (DD-13) | Las primeras entradas con su motivo y su fecha, y «Ver los N» hacia la lista filtrada; sin lista a la que ir, «Mostrar N más» despliega el resto en el lugar. Con `total` nulo (QCS confidenciales ocultas), sin número | Sin subrayado; el tono es un borde izquierdo y siempre el texto del motivo |
| `EstadoSinAcceso` | Una sección o pantalla que la cuenta no puede abrir (DD-12) | Qué no puede abrir, por qué en lenguaje llano y a quién pedirlo: dentro de un proyecto, la jefatura por su nombre si la cuenta lee las asignaciones; si no, la administración de la organización. Nunca nombra un rol ni un permiso técnico | Su título es el `h1`; la salida («Volver…») es lo único enfocable |
| `CamposPlegables` | Los atributos de una sección de ficha (DD-06) | A la vista, los que tienen dato; los vacíos, tras «Mostrar los N campos sin dato». Un dato restringido no es un vacío: se ve siempre con su candado | El botón dice cuántos campos muestra y lleva `aria-expanded` |

## 6. Patrones núcleo

### 6.1 Ficha de entidad

La misma anatomía para todo objeto importante [PRM-02 §26], en tres franjas (DD-06):

1. **Contexto:** barra de contexto (proyecto y territorio) y breadcrumbs; el rastro si se llegó por un recorrido.
2. **Cabecera** (`CabeceraDeFicha`): antetítulo «Tipo · código · categoría» en la letra del texto (el código puede ir en
   `code`, nunca toda la línea); un título que dice qué es (lo que plantea una QCS, el propósito de un aporte; sin texto, el
   tipo y el código); chips de situación (plazo, vigencia, revisión, nivel, monto), **nunca el estado del flujo**, que vive en
   el panel de decisión; y las acciones a la derecha, con la regla de la acción principal (DD-02):
   - una sola **primaria**: la siguiente transición del camino, la misma «Siguiente acción» del panel de decisión
     (`siguienteTransicion`, `fichas/siguienteAccion.ts`). Si la cuenta la tiene pero hoy no puede darla, se dibuja
     deshabilitada y lo que falta va debajo, en la misma cabecera. Si la cuenta nunca puede darla, no hay primaria;
   - la acción propia de la ficha (una nueva revisión de un riesgo, registrar una interacción) es primaria solo cuando no hay
     transición del camino; si la hay, pasa a secundaria;
   - hasta dos **secundarias** a la vista: primero las de la ficha (editar, informar un riesgo, ver en el mapa) y después las
     demás transiciones;
   - **«Más acciones»** con su texto: el resto de las transiciones y, al final y en rojo, las **destructivas**, que nunca van a
     la vista: las que llevan a `cancelled`, `voided`, `retired`, `terminated`, `rejected` y `withdrawn` (cancelar, anular,
     retirar, terminar, rechazar y desistir; DD-28), cada una con su diálogo de confirmación o de transición.

   El orden visual y de foco es secundarias, «Más acciones», primaria. En el teléfono, la barra fija lleva la primaria y «Más
   acciones». El botón de IA contextual acompaña al encabezado.
3. **Panel de decisión**, el único lugar del estado: cuatro tarjetas fijas que responden sin buscar: estado actual (desde
   cuándo y quién), siguiente acción (con lo que falta para darla), responsable y vencimiento con escalamiento
   [PRM-02 §15]. El **camino del flujo** (`CaminoDelFlujo`) va plegado dentro de «Estado actual», tras «Ver el camino».
4. **Cuerpo en secciones** (`Seccion`): los atributos de cada sección con `CamposPlegables`, que pliega los vacíos tras
   «Mostrar los N campos sin dato»; las pestañas del objeto cuando las tiene (en un compromiso: Resumen, Entregas,
   Evidencias, Actividad…).
5. **Panel de trazabilidad** al costado desde `lg`, con tres grupos fijos: "De dónde viene" (origen), "Qué generó"
   (consecuencias) y "Lo sustenta" (evidencias y documentos que lo respaldan) (ux-architecture §8). Por debajo, pasa bajo el
   contenido.
6. **Últimos movimientos** y enlace a la actividad completa y a la auditoría.

Árbol de componentes de referencia (se detalla por pantalla en `screens.md`):

```
FichaPage (PorRuta)
 ├── BarraDeContexto · Migas · Rastro
 ├── CabeceraDeFicha
 │    ├── antetítulo · título · chips de situación
 │    └── secundarias · MasAcciones (otras y destructivas) · primaria
 ├── PanelDeDecision (estado actual con CaminoDelFlujo plegado · siguiente acción · responsable · vencimiento)
 ├── Seccion × n
 │    └── CamposPlegables
 └── PanelDeTrazabilidad · Historia
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
- Las transiciones que el rol no tiene no aparecen; las que tiene pero hoy no puede dar aparecen deshabilitadas con la razón
  **a la vista** (dentro de "Más acciones", bajo su nombre), no solo en un `title`. Elegir una transición del menú lo cierra, y
  al cerrar el diálogo el foco vuelve a "Más acciones".

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
- **Una sola fila de pestañas** (DD-03): las del proyecto. Las vistas de la lista (por ejemplo, "Atención", "Mis
  compromisos", "Vencidos", "Todos") van en el `SelectorDeVista`, el primer control de la `BarraDeFiltros`, con su conteo y
  `?view=` en la URL; las compartidas y las mías siguen ASM-083. Una vista guarda filtros, columnas, orden y densidad,
  nunca datos: cada persona ve solo los registros que su rol puede leer.
- **Compartir:** «Guardar esta vista…» abre `SavedViewDialog` con la visibilidad solo yo, equipo del proyecto u organización.
  Cada pestaña compartida lleva un chip con su alcance y, al abrirla, quién la compartió. Solo quien la creó la cambia o la
  borra; los demás guardan una copia propia o la ocultan de sus pestañas. Borrar una vista que otros usan avisa y pide
  confirmación. «Gestionar vistas» reúne todo esto (`screens.md`, Gestionar vistas; lienzo de la etapa 3, página 24).
- **Barra de filtros** (`BarraDeFiltros`, DD-04): búsqueda, hasta tres filtros frecuentes, «Más filtros» en un drawer y un
  chip por filtro aplicado con su valor, todo en la URL, y «Limpiar filtros».
- **Tabla con prioridad de columnas** (`TablaPriorizada`, DD-05): el título en una línea con el código como subtítulo; las
  secundarias desde `xl`, las opcionales en «Columnas»; acciones de fila en un menú, nunca destructivas a la vista. En el
  teléfono, la lista pasa a tarjetas (`ListaEnTarjetas`).
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
| Error de carga | Qué falló, "Reintentar" y el identificador de correlación (`X-Correlation-Id`). En una lista, los filtros siguen a la vista (en la URL y como chips); el texto no promete conservarlos donde no hay filtros (una ficha, una tarjeta, un diálogo) |
| Sin acceso (403 o sección no ofrecida) | `EstadoSinAcceso` (§5.1): qué no puede abrir, por qué y a quién pedirlo, sin revelar datos, con la salida al resumen del proyecto o al inicio (`ux-architecture.md` §7.7) |
| Solo lectura | Aviso "no estás asignado a este proyecto"; las acciones de escritura no aparecen (alcance A, `security.md` §9) |
| Campo o cifra restringidos | Candado y "no disponible", nunca vacío ni cero (RQ-DSH-032) |
| Sesión vencida (401) | Volver a ingresar sin perder el borrador (WAS-ADR-0031) |
| Módulo no instalado (404) | "No está disponible en su organización", sin error técnico ni "Reintentar"; también una lista cuyo objeto el servidor no tiene (ADR-0024) |
| Ruta que no existe | "Esta página no existe", por qué puede pasar y cómo seguir: la lista de su sección y el inicio; nunca una página en blanco |
| IA no disponible | Por rol (sin CREATE sobre `ai_interaction`, ASM-130), por organización o por falla del proveedor (`503 AI_UNAVAILABLE`), con la razón |

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
- **Sin depender del color:** estados con ícono y etiqueta; niveles con barras; en las listas priorizadas, el texto del motivo
  además del borde; series de gráficos con etiqueta, leyenda en texto y «Ver datos», en color sólido y sin trama (DD-15).
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

- **Escritorio (≥ 1024):** experiencia completa, con la navegación expandida (contraíble) y la ficha con su panel al costado.
- **Tableta (768–1023):** navegación de 52 px con solo los íconos; fichas en una columna; tablas sin las columnas secundarias,
  que quedan en la vista previa.
- **Por debajo de 768:** el menú en un panel; la búsqueda de una lista ocupa el ancho y los filtros frecuentes pasan a
  «Filtros (N)»; las acciones de una ficha (la primaria y «Más acciones»), en una barra fija al pie.
- **Móvil (< 640):** no se mete el sistema de escritorio en el teléfono [PRM-02 §31]: barra inferior de cuatro destinos
  (Inicio, Tareas, Avisos y Más, DD-17), listas en tarjetas y la barra fija de una ficha sobre la barra inferior. En el MVP,
  en línea (ASM-021); la captura sin conexión está diseñada para después (ASM-081, `screens.md` §15). Objetivos táctiles de
  48 px.
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

[`redesign/contraste.py`](redesign/contraste.py) lee los valores de [`tokens.css`](tokens.css) y mide cada par de texto y
fondo y de límite y fondo, en claro y en oscuro (WCAG 2.2: 4.5:1 para texto, 3:1 para foco y límites de controles,
1.4.11). Se corre con `python3 docs/ux/redesign/contraste.py` y termina en `ok` solo si ningún par obligatorio queda bajo su
mínimo. Resultado del 2026-10-23:

| Par | Claro | Oscuro | Mínimo |
|---|---|---|---|
| `text` / `bg` | 15.19 | 15.29 | 4.5 |
| `text` / `surface` | 16.48 | 14.31 | 4.5 |
| `text-secondary` / `surface` | 8.27 | 9.06 | 4.5 |
| `text-secondary` / `bg` | 7.62 | 9.68 | 4.5 |
| `text-muted` / `surface` | 5.78 | 6.33 | 4.5 |
| `text-muted` / `bg` | 5.33 | 6.77 | 4.5 |
| `text-muted` / `surface-sunken` | 5.04 | 7.04 | 4.5 |
| `primary-text` / `surface` | 7.76 | 8.18 | 4.5 |
| `primary-text` / `primary-soft` | 6.59 | 6.72 | 4.5 |
| `on-primary` / `primary` | 7.76 | 5.53 | 4.5 |
| `on-primary` / `primary-hover` | 10.04 | 4.72 | 4.5 |
| `info` / `info-soft` | 5.72 | 6.84 | 4.5 |
| `success` / `success-soft` | 5.46 | 7.34 | 4.5 |
| `warning` / `warning-soft` | 5.91 | 7.65 | 4.5 |
| `danger` / `danger-soft` | 5.49 | 6.74 | 4.5 |
| `critical` / `critical-soft` | 7.87 | 8.04 | 4.5 |
| `neutral` / `neutral-soft` | 6.22 | 7.58 | 4.5 |
| `progress` / `progress-soft` | 6.59 | 6.72 | 4.5 |
| `ai` / `ai-soft` | 6.35 | 7.63 | 4.5 |
| `territory` / `territory-soft` | 5.12 | 7.83 | 4.5 |
| `danger` / `surface` | 6.57 | 7.58 | 4.5 |
| `text` / `surface-raised` | 16.48 | 13.25 | 4.5 |
| `text-secondary` / `surface-raised` | 8.27 | 8.39 | 4.5 |
| `text-muted` / `surface-raised` | 5.78 | 5.86 | 4.5 |
| `primary-text` / `surface-raised` | 7.76 | 7.57 | 4.5 |
| `focus` / `surface` | 5.22 | 7.54 | 3.0 |
| `focus` / `bg` | 4.81 | 8.06 | 3.0 |
| `focus` / `surface-raised` | 5.22 | 6.98 | 3.0 |
| `border-strong` / `surface` | 3.50 | 3.03 | 3.0 |
| `primary` / `surface` | 7.76 | 3.09 | 3.0 |
| `border-control` / `surface` | 3.50 | 3.41 | 3.0 |
| `border-control` / `bg` | 3.23 | 3.65 | 3.0 |
| `border-control` / `surface-sunken` | 3.05 | 3.79 | 3.0 |
| `border-control` / `surface-raised` | 3.50 | 3.16 | 3.0 |

En resumen: el texto queda entre 5.04:1 y 16.48:1 en claro y entre 4.72:1 (blanco sobre `primary-hover` oscuro) y 15.29:1
en oscuro; el foco, entre 4.81:1 y 5.22:1 en claro y entre 6.98:1 y 8.06:1 en oscuro; el límite de los controles, entre
3.05:1 y 3.50:1 en claro y entre 3.16:1 y 3.79:1 en oscuro (DD-21). `surface-raised` frente a `surface` y `bg` (1.00 a
1.15:1) es informativo: lo que flota se separa también por su borde y su sombra. Los pares de los temas de `themes.css`
están en §3.9.

## 11. Trazabilidad

- **Requisitos:** RNF-USA-001, RNF-I18N-001, RNF-MNT-001, RQ-DSH-032, RQ-DSH-035, RQ-GIS-014, RQ-GIS-015, RQ-AUD-002,
  RQ-AUD-011, RQ-AI-003, RQ-AI-004, RQ-AI-012, RQ-SEC-005, RN-WFL-001, RN-COM-001, RN-ENG-001, RN-INV-003, RF-COM-003.
- **Rediseño de UX:** [decisiones DD-01 a DD-29](redesign/07-decisiones-y-compatibilidad.md),
  [especificación de componentes](redesign/05-especificacion-de-componentes.md).
- **Supuestos:** ASM-021, ASM-025, ASM-063, ASM-066, ASM-075, ASM-076, ASM-077, ASM-079, ASM-081, ASM-082, ASM-083, ASM-084,
  ASM-085, ASM-086.
- **Documentos:** [workflows.md](../workflows.md), [dashboards.md §3 y §9](../dashboards.md), [gis.md §2 y §7.2](../gis.md),
  [security.md §5 y §9](../security.md), [api.md §3](../api.md), [ADR-0024](../adr/0024-interfaz-portal-y-admin.md),
  [ADR-0025](../adr/0025-concurrencia-e-idempotencia.md).
- **Fuentes:** [PRM-02 §4, §7, §8, §23, §25–§31, §35, §42, §43], [WAS-ADR-0035].
