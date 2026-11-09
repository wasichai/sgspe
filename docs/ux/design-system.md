# Design system de SGSPE

Sistema de Gestión Social para Proyectos Extractivos · Diseño UX/UI · identidad «Civic Modern» · versión 0.4 · 2026-11-09

Este documento define los fundamentos visuales, los tokens, el sistema de estados, el catálogo de componentes y los patrones
núcleo del portal. Se apoya en la [arquitectura de UX](ux-architecture.md) y en los diseños de la Fase 0; los IDs se definen
solo en el [análisis](../requirements-analysis.md). Los tokens listos para usar están en [`tokens.css`](tokens.css), la
versión visual de todo lo que sigue está en el [lienzo de la dirección E](https://claude.ai/artifact/Y6aV29ac54BQMERnieY15K)
y sus capturas de referencia en [`civic/`](civic/README.md).

La identidad «Civic Modern» (dirección E) la eligió el usuario el 2026-11-08 entre cinco direcciones
([ADR-0032](../adr/0032-identidad-visual-civic-modern.md), ASM-149) y reemplaza a «Pino y piedra» (ASM-077) y a los tres
temas de ASM-086. Lo que sigue vigente del rediseño de UX del 2026-10-20 ([`redesign/`](redesign/README.md)) se cita como
`DD-NN` ([`redesign/07`](redesign/07-decisiones-y-compatibilidad.md)): los componentes del portal (§5.1), la regla de la
acción principal (§6.1), los patrones núcleo (§6) y las tres familias de estado (§4).

## 1. Base técnica

- El portal se construye con las primitivas de `@wasichai/ui`, Tailwind 4 y `@wasichai/ui/theme.css`, que ya trae temas por
  tokens [WAS-ADR-0035] (platform-fit §6). SGSPE no reemplaza esas primitivas: les da sus tokens y compone patrones con
  ellas (RNF-MNT-001).
- Los tokens de SGSPE son una capa sobre el tema de wasichai: `tokens.css` declara las variables `--sg-*` y
  `sgspe-ui/src/theme/wasichai.css` apunta a ellas los 28 tokens de `@wasichai/ui` en `light` y en `dark` (§3.9), así que
  las primitivas y `/admin` se ven con la misma paleta que el portal.
- Gráficos con ECharts (RQ-DSH-035) y mapas con MapLibre 6.10.0 (RQ-GIS-014), ambos tomando sus colores de los mismos
  tokens. Por eso **todo color de `tokens.css` va en hexadecimal**: ni MapLibre ni ECharts resuelven `var()` ni `oklch`.
- Fuentes auto-alojadas con `@fontsource` (Public Sans e IBM Plex Mono, OFL), servidas desde el mismo origen, sin CDN, por
  la CSP de ADR-0026.
- React 19, TypeScript, React Router, TanStack Query, React Hook Form, Zod e i18next, en las versiones de caja-ui
  (platform-fit §6). La interfaz no usa ninguna otra biblioteca de componentes.

## 2. Dirección visual: «Civic Modern»

**ASSUMPTION (ASM-149):** SGSPE se trata como un servicio digital del Estado bien hecho, que se usa todos los días. Azul
institucional para la marca y la acción, superficies planas con bordes que se ven y un foco amarillo imposible de perder.
La gerente y el relacionista de campo leen lo mismo, en el mismo orden, con rótulos sobre cada campo y botones que dicen lo
que hacen.

- **La firma** es la franja teal (`stripe`) bajo la cabecera azul. El teal solo reaparece como «progreso».
- **El color de los datos va aparte** del color de la interfaz: la IA siempre en violeta con borde discontinuo
  («propuesta, no hecho») y el territorio siempre en oliva, nunca reutilizados; la paleta del mapa es cartográfica y
  separada de la operativa (§3.3).
- **Cuerpo de 15 px** con interlineado de 1,55 y texto principal en AAA (17,5:1 sobre la superficie).
- **El estado nunca depende del color:** chip con ícono para el estado, reloj y versalitas subrayadas para la situación y
  barras para el nivel (§4).
- **Sin decoración:** sin degradados, sin tarjetas con borde lateral de color, sin sombras en reposo, sin brillos de
  carga ni cursivas decorativas (la cursiva solo marca «Restringido»).

Por qué: la evaluación de las cinco direcciones puso a E primera en accesibilidad y en personas con distinta experiencia
técnica, que es la mezcla real de SGSPE (relacionistas de campo, aprobadores ocasionales, gerencia, auditoría). Su punto
débil, la densidad, lo cubren la densidad compacta por persona (§3.5) y los préstamos (§2.2).

### 2.1 Marca SGSPE

**ASSUMPTION (ASM-082):** SGSPE tiene marca propia, la dirección «Sendero»; desde ASM-149 su paleta es la de «Civic
Modern».

- **Isotipo:** cerros con un sendero, en trazo blanco, dentro de un cuadrado de 36 px con borde blanco de 2 px y radio de
  4 px, sobre la cabecera azul. A 24 px o menos, la variante simplificada (trazo más grueso); el favicon sale de ella
  (16 y 32 px) y el ícono de la aplicación de la versión completa (180 y 512 px), en blanco sobre `#1D4F91`.
- **Logotipo:** isotipo más «SGSPE» en Public Sans 800 de 20 px con +0,02em; debajo, desde 1536 px, el lema «Gestión
  social de proyectos extractivos» en 12,5 px. Área de respeto: la mitad del isotipo por lado.
- **Versiones:** blanca sobre la cabecera (`header`, en claro y en oscuro); azul `#1D4F91` sobre blanco (login, correos);
  monocroma en tinta `#0F1A2A` para impresión en blanco y negro.
- **Paleta de marca:** azul institucional `#1D4F91`, azul profundo `#163D72`, teal de la franja `#14857E`, amarillo del
  foco `#FFD34D` y tinta marina `#0B1D3A`; neutros fríos de `#F3F5F8` a `#0F1A2A`.
- **Usos incorrectos:** no deformar, no cambiar colores fuera de las tres versiones, no poner el isotipo sobre fotos sin un
  fondo sólido, no usar el teal ni el amarillo para texto.
- **Aplicaciones:** cabecera, login, pestaña del navegador, correos de aviso y la portada de los entregables, donde manda
  la marca del cliente y SGSPE figura solo como «Preparado con SGSPE».
- **Voz y tono:** directa, en segunda persona de respeto («Ingrese», «Revise sus respuestas»), sin jerga técnica; dice qué
  pasó y qué hacer, con botones en verbo.

### 2.2 Préstamos de las otras direcciones

Decisión del usuario sobre el comparativo (ADR-0032):

| De | Qué se toma | Dónde se especifica |
|---|---|---|
| B — Geospatial Intelligence | El Mapa SIG a todo el ancho con una paleta cartográfica separada de la operativa | §3.3, §6.7 (S09) |
| B | La vista «Lista + mapa», opcional y nunca por defecto, en Compromisos, QCS, Grupos de interés y Riesgos, ofrecida solo si los registros de la lista traen ubicación | §5.3 «Lista + mapa» |
| D — Executive Operations Center | La vista previa lateral acoplada: el `?peek=` actual como panel de 420 px desde 1280 px | §5.3 «Vista previa acoplada» |
| D | La hora de cálculo visible en los tableros («Calculado hoy 07:40») | §5.3 «KPI», §6.7 (S03) |
| C — Contemporary Editorial | Las notas al pie bajo las tablas («Montos en soles; — = sin monto») | §5.3 «Tablas» |
| C | La anotación directa en los gráficos, en lugar de leyendas separadas cuando caben | §5.3 «Gráficos» |

## 3. Tokens

### 3.1 Color base

Todos los pares de texto superan 4.5:1 y los límites de los controles 3:1, en claro y en oscuro (§10). Los valores
oscuros son los del tema `dark` (y de «Según el sistema» cuando el sistema está en oscuro).

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `bg` | `#F3F5F8` | `#0D131B` | Fondo de la aplicación y de la banda de contexto |
| `surface` | `#FFFFFF` | `#141C27` | Paneles, tablas, navegación, diálogos |
| `surface-sunken` | `#E9EDF2` | `#0A0F15` | Encabezados de tabla, esqueletos, botón deshabilitado |
| `surface-hover` | `#F5F8FC` | `#1A2431` | Fila o ítem bajo el cursor |
| `surface-raised` | `#FFFFFF` | `#1B2533` | Lo que flota: menús, desplegables, popovers, el calendario de `CampoFecha`, cajones y diálogos |
| `border` | `#C9D1DC` | `#2C3A4B` | Contorno de paneles y separadores (decorativo) |
| `border-subtle` | `#DFE4EB` | `#223042` | Separadores dentro de un panel y entre filas |
| `border-strong` | `#66727F` | `#7A8899` | Bordes con énfasis, bajo el encabezado de tabla, menús, panel de decisión (3:1) |
| `border-control` | `#5A6675` | `#7F8EA1` | Límite de `Input`, `Textarea`, `Select`, `CampoFecha` y casillas (2 px) |
| `text` | `#0F1A2A` | `#F1F4F8` | Texto principal (AAA) |
| `text-secondary` | `#35425A` | `#C3CDD9` | Texto de apoyo, encabezados de tabla, ayudas de campo |
| `text-muted` | `#4F5B6C` | `#A2AEBE` | Metadatos y leyendas (nunca por debajo de 4.5:1); también el texto de ejemplo |
| `primary` | `#1D4F91` | `#346DBB` | Acción primaria, selección, navegación activa |
| `primary-hover` | `#163D72` | `#2C5FA6` | Hover y la sombra inferior de 2 px de la primaria |
| `primary-soft` | `#E4ECF7` | `#15263C` | Fondo de selección, de la fila elegida y de la entrada activa de una navegación |
| `primary-text` | `#1B4A88` | `#94BCF0` | Enlaces y texto de acento |
| `on-primary` | `#FFFFFF` | `#FFFFFF` | Texto sobre `primary` |
| `focus` | `#0B1D3A` | `#FFD34D` | Anillo exterior del foco (claro) o el foco entero (oscuro) |
| `halo` | `#FFD34D` | `#FFD34D` | Contorno amarillo de 3 px del foco |
| `header` | `#1D4F91` | `#0E2748` | Fondo de la cabecera institucional |
| `header-2` | `#163D72` | `#0A1D37` | Hover y presionado sobre la cabecera |
| `header-text` | `#FFFFFF` | `#FFFFFF` | Texto e íconos de la cabecera (8,1:1 y 15,0:1) |
| `stripe` | `#14857E` | `#1A9E94` | Franja de marca de 4 px bajo la cabecera; decorativa |
| `ai-header-border` | `#D9C8FF` | `#D9C8FF` | Borde de «Preguntar» en la cabecera (5,3:1 sobre `header`) |

Neutros fríos en 11 pasos (claro): `#FFFFFF` · `#F5F8FC` · `#F3F5F8` · `#E9EDF2` · `#DFE4EB` · `#C9D1DC` · `#9AA4B2`
(decorativo: el borde del atajo ⌘K) · `#66727F` · `#4F5B6C` · `#35425A` · `#0F1A2A`. Oscuro: `#0A0F15` · `#0D131B` ·
`#141C27` · `#1A2431` · `#1B2533` · `#223042` · `#2C3A4B` · `#7A8899` · `#A2AEBE` · `#C3CDD9` · `#F1F4F8`.

### 3.2 Tonos semánticos

Cada tono es una pareja tinta + fondo suave, usada en chips, insignias, alertas y celdas; el contraste medido va en §10.

| Tono | Claro: tinta / fondo | Oscuro: tinta / fondo | Cuándo |
|---|---|---|---|
| `neutral` | `#48546A` / `#EBEEF3` | `#C3CDD9` / `#222D3B` | Estado inicial, borrador, sin evaluar |
| `info` | `#1F5596` / `#E5EEF9` | `#94BCF0` / `#16253A` | En revisión, esperando decisión, aprobado sin iniciar |
| `progress` | `#0C625D` / `#DDF1EE` | `#6FD0C3` / `#0F2C2B` | En ejecución, en atención, en mitigación (el teal de la marca) |
| `success` | `#1C6636` / `#E1F1E6` | `#8BD3A2` / `#13291C` | Cumplido, verificado, resuelto, al día |
| `warning` | `#744D00` / `#FDF0CF`, borde de aviso `#A87400` | `#F2C75C` / `#2F240B`, borde `#A87400` | Por vencer, suspendido, postergado, pendiente de aprobación |
| `danger` | `#A1271B` / `#FBE5E1`; sobre el relleno, `on-danger` `#FFFFFF` | `#FF9D8C` / `#3A1915`; `on-danger` `#1A0B08` | Vencido, bloqueado, rechazo, error; el botón destructivo |
| `critical` | `#7A1212` / `#F6D9D9`; `on-critical` `#FFFFFF` | `#FFB3B3` / `#431515`; `on-critical` `#FFFFFF` | Etiqueta de confidencial y el CRITICO de la escala de nivel |
| `ai` | `#5A3496` / `#F1EBFA`, borde discontinuo `#8E6CC8` | `#C7B2F2` / `#251C3A`, borde `#8566C4` | Solo propuestas y resúmenes de IA |
| `territory` | `#4F5A12` / `#EEF1DA`, borde `#9AA65A` | `#CBD77E` / `#222812`, borde `#6B7536` | Etiqueta, sello y banda de territorio |

`warning-border` existe porque la tinta `warning` como borde de 2 px pesa demasiado en un aviso: `#A87400` da 3,6:1 sobre
el fondo suave en claro y 3,8:1 en oscuro. `on-danger` es propio porque en oscuro el `danger` es rosado y el blanco no se lee
sobre él; `--on-danger` de wasichai lo lee (§9).

### 3.3 Datos y mapas

Los colores de datos y de mapa son utilidades de Tailwind (`@theme inline` de `tokens.css`): `bg-sg-data-1`,
`bg-sg-stance-favor`, `bg-sg-risk-high`, `fill-sg-heat-3`, `stroke-sg-lyr-project`… Los estilos en línea, el SVG,
MapLibre y ECharts leen las variables `--sg-*`. Un filtro nunca repinta lo que queda: el color sigue a la entidad.

| Escala | Tokens | Claro | Oscuro | Uso |
|---|---|---|---|---|
| Categórica (orden fijo) | `data-1` a `data-6` | azul `#2366B8` · naranja `#D2691E` · aguamarina `#159F78` · ocre `#C08A00` · rosa `#D2558A` · verde `#3F7F1A` | `#4A8AD9` · `#D2702C` · `#1A9E78` · `#BB8A0C` · `#D45C8E` · `#4E9A2A` | Series sin orden, siempre en ese orden. Validada para daltonismo (ΔE entre vecinas ≥ 9,4) y ≥ 3:1 sobre la superficie. Sin violeta (IA) ni oliva (territorio) |
| Postura (divergente) | `stance-favor`, `stance-neutral`, `stance-open`, `stance-against` | A FAVOR `#1D6FB8` · NEUTRAL `#B7BFCA` · EN CONTRA PERO ABIERTO A ESCUCHAR `#E3A24A` · EN CONTRA `#B4471F` | `#4A8AD9` · `#6B7686` · `#D9993F` · `#E0663A` | Azul ↔ naranja con gris al centro: KPI-STK-02, la ficha territorial, LYR-17 |
| Nivel (riesgo, severidad, prioridad) | `risk-low`, `risk-medium`, `risk-high`, `risk-critical` | BAJO `#D3EBDB` · MEDIO `#FFDF7A` · ALTO `#F5A66B` · CRITICO `#A11A1A` sólido | `#2F5A3D` · `#C99A12` · `#D9743A` · `#C42323` | La insignia de nivel (§5.3) y LYR-21. MEDIO es amarillo; CRITICO, el único relleno intenso |
| Tinta de nivel | `level-medium-ink` / `level-medium-border`, `level-high-ink` / `level-high-border` | MEDIO `#2A1F00` / `#8A6400` · ALTO `#2A1205` / `#8A3A10` | iguales | La palabra, las barras y el borde de MEDIO y ALTO sobre su relleno (12,4:1 y 8,9:1 en claro; 6,3:1 y 5,5:1 en oscuro). BAJO usa `text` con borde `success`; CRITICO, `on-critical` |
| Calor (un solo tono) | `heat-1` a `heat-4` | 1–2 `#FDE9C8` · 3–5 `#F7B98A` · 6–9 `#E0743F` · 10+ `#A8361B` | `#3A2A14` · `#7A4A1F` · `#C8632F` · `#FF8A5C` | QCS por lugar (LYR-20) y toda magnitud de un solo tono |
| Zonas de la matriz | `zone-1` a `zone-4` | `#E6F3EA` · `#FFF3C4` · `#FDE0C8` · `#F6D9D9` | `#18291E` · `#33290C` · `#3A2314` · `#431515` | Fondo de las celdas de la matriz probabilidad × impacto: BAJO 1–4, MEDIO 5–9, ALTO 10–15, CRITICO 16+. La cifra va en `text` |
| Rejilla | `grid` | `#E3E7ED` | `#243142` | Líneas guía de los gráficos y cuadrícula del mapa sin mapa base |

Los gráficos van en color sólido, sin trama (DD-15): la serie se distingue por su posición, la anotación directa de su
valor, la leyenda en texto y la tabla «Ver datos».

#### Mapa: paleta cartográfica

La paleta del mapa es propia y separada de la operativa (préstamo de B): el suelo, el agua y las curvas no se confunden
con un estado, y las capas de gestión reutilizan las escalas de datos, nunca los tonos semánticos.

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `map-ground` | `#EEF0EA` | `#121A22` | Suelo del mapa sin mapa base y halo de las etiquetas (trazo de 4 px) |
| `map-water` | `#B9D3EA` | `#1D3A55` | Cuerpos de agua del fondo |
| `map-contour` | `#D6DACD` | `#1F2A35` | Curvas de nivel, 1 px |
| `map-road` | `#8A7F6C` | `#8D8470` | Vías del fondo y LYR-04, 3 px |

Capas por grupo (MapLibre: `fill`, `line`, `circle`, `heatmap`), en el orden y los grupos del control de capas:

| Grupo | Capa | Token | Claro | Oscuro | Forma |
|---|---|---|---|---|---|
| Referencia (grises y agua) | LYR-01 Límites administrativos | `lyr-boundary` | `#66727F` | `#7A8899` | Línea de 1,5 px, guion-punto `10 4 2 4` |
| Referencia | LYR-02 Centros poblados | `lyr-place` | `#0F1A2A` | `#F1F4F8` | Círculo de 5 px relleno `surface` con borde de 2 px; la sede, cuadrado de 10 px |
| Referencia | LYR-04 Vías de acceso | `map-road` | `#8A7F6C` | `#8D8470` | Línea de 3 px, extremos redondos |
| Referencia | LYR-08 Elementos ambientales | `lyr-water`, rótulo `lyr-water-label` | `#3D7FBF`, `#2F6AA3` | `#5C9AD6`, `#8DBBE8` | Ríos de 2 px; rótulo de 12 px en cursiva a lo largo del cauce |
| Proyecto (azul institucional) | LYR-09 Área de interés | `lyr-project` | `#1D4F91` | `#6FA0E0` | Línea de 1,5 px punteada `3 3` |
| Proyecto | LYR-13 Áreas de influencia | `lyr-project` | `#1D4F91` | `#6FA0E0` | AIDS: relleno al 12 % y borde de 2,5 px; AIIS: borde de 2 px discontinuo `8 5`; rótulo en `primary-text` |
| Proyecto | LYR-10 Pozos programados | `lyr-access` | `#0B1D3A` | `#F1F4F8` | Cuadrado de 10 px; capa CONFIDENCIAL: casilla con candado y etiqueta `critical` |
| Proyecto | LYR-12 Accesos del proyecto | `lyr-access` | `#0B1D3A` | `#F1F4F8` | Línea de 2 px discontinua `5 4` |
| Tenencia (oliva y siena) | LYR-05 Territorio comunal | `lyr-community`, rótulo `lyr-community-label` | `#6F7D1C`, `#56621A` | `#A9B85A`, `#CBD77E` | Trama a 45° (trazo al 45 %, fondo al 5 %) y borde de 2 px; rótulo en versalitas de 12 px |
| Tenencia | LYR-07 Concesiones | `lyr-concession` | `#8A5A2B` | `#C08A5A` | Línea de 1,8 px `9 3 2 3`, sin relleno |
| Gestión (escalas de datos) | LYR-17 Grupos por localidad | `stance-*` | — | — | Anillo de 14 px por postura con el total al centro |
| Gestión | LYR-19 Compromisos | `data-3` | — | — | Cuadrado de 18 px con su conteo, borde `surface` de 1,5 px |
| Gestión | LYR-20 QCS | `heat-1` a `heat-4` | — | — | Calor por lugar, opacidad 0,7 |
| Gestión | LYR-21 Riesgos | `risk-*` con `level-*-ink` | — | — | Rombo con el relleno del nivel y borde de la tinta; CRITICO con «!» en `on-critical` |

La selección es un anillo de 3 px en `focus` alrededor del elemento y su rótulo en 14 px 800. Las etiquetas llevan halo
`map-ground` de 4 px. Sin mapa base, el mapa muestra las capas sobre la rejilla (`grid`) y un aviso `info` «Sin mapa base…
Configurar mapa base» (ASM-085). Los mapas de informe (`gis.md` §7.2, `model/styles/palette.json`) se alinean con esta
paleta en un cambio aparte.

### 3.4 Tipografía

Familias: `"Public Sans", "Segoe UI", system-ui, sans-serif` (`font-sg-sans`) y `"IBM Plex Mono", ui-monospace,
monospace` (`font-sg-mono`). Public Sans es la familia abierta (OFL) del sistema de diseño federal de EE. UU., hecha para
servicios públicos: alturas de x generosas, cifras tabulares (`tnum`) y los signos del español y del quechua (ñ, á, ’, «»).
Pesos 400, 500, 600, 700 y 800, y la cursiva 400; Plex Mono 400, 500 y 600. Todo el portal usa
`font-variant-numeric: tabular-nums` (`tokens.css`, `@layer base`).

| Token | Tamaño / interlineado | Peso | Uso |
|---|---|---|---|
| `display` | 32 / 40 | 700 | Título de página raíz o de portada |
| `h1` | 28 / 36 | 700, −0,01em | Título de pantalla o de ficha |
| `h2` | 21 / 28 | 700 | Sección de página |
| `h3` | 17 / 24 | 700 | Panel o bloque |
| `body-lg` | 16 / 26 | 400 | Formularios largos, login y teléfono |
| `body` | 15 / 23 | 400 | Texto general (interlineado 1,55) |
| `body-sm` | 13,5 / 20 | 400 | Metadatos, ayudas de campo («Calculado hoy 07:40») |
| `label` | 15 / 22 | 700 | Rótulo de campo, sobre el campo |
| `table` | 14,5 / 20 | 400–600 | Celdas de tabla; el título de la fila en 600 |
| `table-dense` | 14 / 20 | 400–600 | Celdas en la densidad compacta |
| `caption` | 12,5 / 16 | 400 | Leyendas, notas al pie de tabla, ejes de gráfico |
| `overline` | 12,5 / 16 | 600, +0,06em, MAYÚSCULAS | Antetítulos («COMPROMISO · DEMO-C-04»), grupos de capas |
| `kpi` | 30 / 36 | 800, −0,01em | Cifra de un indicador |
| `kpi-sm` | 22 / 28 | 800 | Indicador secundario |
| `code` | 13,5 / 20 (0,92em) | Plex Mono 500 | Códigos (DEMO-C-04, LYR-13), EPSG, huellas, rutas |

Tamaño mínimo 12 px; 11,5 px solo en Plex Mono para un metadato no esencial (el código de una capa o de un indicador) que
también figura en la tabla. Los valores de ENUM se muestran tal como se guardan, en MAYÚSCULAS españolas (ADR-0009). Los
títulos usan `text-wrap: balance`.

### 3.5 Espaciado, medidas y densidad compacta

Base de 4 px: 4 · 8 · 12 · 16 · 20 · 24 · 32 · 40 · 48 · 64. El espaciado usa la escala de Tailwind, sin tokens propios
(DD-15).

| Contexto | Valor |
|---|---|
| Márgenes de página | 32 px desde `lg` (24 px en tableta, 16 px en el teléfono); rejilla de 12 columnas con canal de 24 px a 1440 |
| Entre paneles de una página | 20 px |
| Dentro de un panel | 16–20 px (cabecera del panel 14 × 20 px) |
| Entre rótulo, ayuda y campo | 6 px |
| Entre campos de un grupo | 18–20 px |
| Ícono–texto | 8 px (6 px en chips) |

Medidas por densidad, en `tokens.css` (`--sg-*` y las utilidades `h-sg-*`):

| Elemento | Token | Normal | Compacta | Táctil (teléfono) |
|---|---|---|---|---|
| Fila de tabla | `--sg-row` | 44 px | 36 px | 56 px (tarjeta) |
| Encabezado de tabla (fijo) | `--sg-table-head` | 40 px | 36 px | — |
| Botón | `--sg-control` | 40 px (32 px el `sm`, 48 px el `lg`) | 36 px | 48 px |
| Campo | `--sg-field` | 44 px | 36 px | 48 px |
| Cabecera institucional | `--sg-masthead` | 64 px + 4 px de franja | 52 px + 4 px | 56 px + 4 px |
| Navegación principal | `--sg-nav` | 52 px | 44 px | barra inferior de 64 px |
| Ítem de la navegación local | — | 40 px | 36 px | — |

**Densidad compacta.** Es una preferencia de cada persona: «Densidad: Normal · Compacta» en el menú de la cuenta, y la
casilla «Vista compacta» de una barra de filtros, que cambia la misma preferencia. Se guarda en el navegador
(`localStorage`, `sgspe.densidad`, valores `normal` y `compacta`) y el script de arranque la escribe como
`data-density="compact"` en `<html>` antes de pintar, para que no salte. Cambia filas, controles, campos, cabecera y
navegación; nunca colores, tipografía del texto corrido ni objetivos táctiles en el teléfono, que siguen en 48 px. La
tabla compacta baja su texto a `table-dense` y su relleno a 3 × 10 px. Una vista guardada no guarda la densidad.

### 3.6 Bordes, radios, sombras y foco

| Grupo | Valores |
|---|---|
| Radios | `xs` 2 px (barras de datos, celdas de la matriz) · `sm` 3 px (casillas, esqueleto) · `md` 4 px (todo lo demás: controles, paneles, chips, menús, diálogos) · `lg` y `xl` 4 px, iguales a `md`, para no romper las utilidades existentes · 50 % solo en el avatar y en los números de los pasos |
| Bordes | 1 px separa (paneles, filas, navegación); 2 px delimita lo que se opera (campos, botones, panel de decisión, menús, vista previa acoplada); 3 px el campo con error y el resumen de errores; 4 px los indicadores de selección (borde inferior de la pestaña o de la navegación, borde izquierdo de la entrada activa o de la fila elegida) |
| Sombras | En reposo, ninguna (`sm` es transparente). `md` 0 2px 6px rgb(15 26 42 / .10) para un popover pequeño; `lg` 0 8px 24px / .16 para un cajón; `overlay` 0 24px 64px / .30 para diálogos, menús y desplegables (con borde de 2 px `border-strong`). La primaria lleva una sombra inferior sólida de 2 px en `primary-hover` |
| Foco | En claro, contorno de 3 px `halo` pegado al control (`outline-offset: 0`) y anillo de 5 px `focus` por fuera (`box-shadow`); en oscuro, solo el contorno `halo` separado 2 px. En `@layer base` de `tokens.css`, para `a`, `button`, `input`, `select`, `textarea`, `summary` y `[tabindex]` con `:focus-visible`. Da al menos 3:1 sobre cualquier superficie, también sobre la cabecera azul (5,7:1) |

### 3.7 Breakpoints y layout

Los puntos de corte de `tokens.css`: `sm` 640, `md` 768, `lg` 1024, `xl` 1280 y `2xl` **1440** px. En SGSPE `2xl` no es el
1536 de Tailwind: lo que se ve «desde 1536 px» usa `min-[96rem]` (DD-24).

| Ancho | Comportamiento |
|---|---|
| ≥ 1536 (`min-[96rem]`) | El lema de la marca en la cabecera; las columnas opcionales de `TablaPriorizada` sin elección en «Columnas» |
| ≥ 1440 (`2xl`) | Contenido de hasta 1440 px con márgenes de 32 px |
| ≥ 1280 (`xl`) | Columnas secundarias de `TablaPriorizada`; la vista previa acoplada de 420 px junto a la lista |
| ≥ 1024 (`lg`) | Navegación principal horizontal; navegación local del proyecto de 248 px; ficha con el cuerpo y un panel de 320 px al costado (trazabilidad, «Qué pasará») |
| 768–1023 (`md`) | Cabecera de 60 px con menú; la navegación local del proyecto pasa a una fila de pestañas de 48 px; márgenes de 24 px |
| < 768 | Cabecera de 56 px; la búsqueda de una lista ocupa el ancho y sus filtros frecuentes pasan a «Filtros (N)»; las acciones de una ficha, en una barra fija al pie; márgenes de 16 px |
| < 640 (`sm`) | Barra inferior de cinco destinos (§5.2); las listas, a tarjetas (`ListaEnTarjetas`) [PRM-02 §31] |

Cajón de formulario de 560 px; vista previa de 420 px (acoplada desde `xl`, cajón por debajo); objetivo mínimo de
escritorio de 32 px, por encima del 24 × 24 de WCAG 2.2; 40 px por defecto; 44 px táctil y 48 px en el teléfono.

### 3.8 Movimiento y estados de componente

120 ms en hover y foco, 200 ms al abrir cajones, desplegables y diálogos, siempre con `ease-sg-out`. Sin animaciones
decorativas, contadores animados ni brillos de carga. Con `prefers-reduced-motion`, todo cambia sin transición
[PRM-02 §43].

| Estado | Regla |
|---|---|
| Reposo | Colores de §3.1 |
| Hover | Fondo `surface-hover` o el tono `-hover`; en la cabecera, `header-2`; nunca solo cambio de cursor |
| Presionado | Un paso más oscuro que hover; la primaria pierde su sombra inferior |
| Foco | El foco de §3.6, visible con teclado (`:focus-visible`); nunca se elimina |
| Deshabilitado | Fondo `surface-sunken`, texto `text-muted`, borde `border` y `cursor: not-allowed`; si la acción no está permitida hoy, se explica debajo o en el tooltip por qué. Si el rol nunca la tendrá, la acción no se muestra |
| Cargando | El control conserva su tamaño, muestra un indicador y un texto en gerundio («Aprobando…») y queda inactivo; `aria-busy` |
| Error | Borde `danger` de 3 px y mensaje en negrita `danger` con ícono **sobre** el campo, enlazado con `aria-describedby`; el mensaje dice cómo corregir |
| Éxito | Aviso `success` breve bajo la cabecera con lo que pasó y qué falta; nunca bloquea el siguiente paso |

### 3.9 Temas

**ASSUMPTION (ASM-149):** un solo par de temas, los ids `light` y `dark` de wasichai [WAS-ADR-034, WAS-ADR-035], con los
valores de «Civic Modern». Reemplaza los temas `sgspe-pino`, `sgspe-cielo` y `sgspe-noche` de ASM-086; `themes.css` se
retiró.

- **Por defecto, claro.** El oscuro se elige en el menú de la cuenta (Tema: Según el sistema · Claro · Oscuro), con una
  sola preferencia por cuenta (`/api/auth/me/preferences`). «Según el sistema» se respeta: `@wasichai/core` lo resuelve a
  `light` o `dark`, que ahora son los de SGSPE, así que UPS-19 quedó sin objeto.
- **Selectores de `tokens.css`:** `:root` (claro), `:root[data-theme="dark"]` y, solo sin `data-theme`,
  `@media (prefers-color-scheme: dark) { :root:not([data-theme]) }`, con los mismos valores que el oscuro.
- **El oscuro es un tema diseñado**, no una inversión: datos, postura, nivel, calor, zonas, rejilla y capas del mapa
  tienen valores propios (§3.3). La cabecera pasa a `#0E2748`, el foco a amarillo solo y CRITICO a `#C42323` con texto
  blanco (5,8:1).
- **Marca:** no cambia con el tema; el isotipo va en blanco sobre la cabecera en los dos.
- **Impresión:** los documentos impresos siguen en claro (`PrintableDocumentPage` fija `data-theme="light"`).
- **Pruebas:** un test del portal comprueba que `tokens.css` es copia de `docs/ux/tokens.css` y mide los pares de §10
  en los dos temas.

## 4. Sistema de estados

Tres familias que nunca se confunden [PRM-02 §8]:

1. **Estado de workflow:** decisión humana registrada por una transición. Chip suave con ícono y etiqueta.
2. **Situación derivada:** lo que calcula un trabajo programado con el paso del tiempo (`deadline_status`, `validity_status`,
   `review_status`). Sin caja: reloj y versalitas con subrayado punteado; acompaña al estado y nunca lo reemplaza
   (RN-WFL-001, CON-028).
3. **Nivel:** riesgo, severidad, prioridad. Insignia con barras (1 a 4) además del color y la palabra.

Las medidas de las tres están en §5.3.

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
| Plazo (`deadline_status`) | AL DIA · POR VENCER · VENCIDO | Reloj y versalitas subrayadas: `success`, `warning` y `danger`. Con días: «VENCIDO · 7 DÍAS», «VENCE EN 3 DÍAS HÁBILES» |
| Vigencia (`validity_status`) | VIGENTE · POR VENCER · VENCIDO | Igual que plazo |
| Revisión (`review_status`) | AL DIA · POR REVISAR · REVISION VENCIDA | Igual que plazo |

En listas y fichas, la insignia de plazo va siempre al lado del chip de estado. Un compromiso en borrador o pendiente de
aprobación no muestra plazo, porque no lo tiene (dashboards §3.2).

### 4.3 Nivel, severidad y prioridad

| Escala | Valores | Forma |
|---|---|---|
| Nivel de riesgo y severidad de alerta | BAJO · MEDIO · ALTO · CRITICO | Insignia con 1 a 4 barras sobre el relleno del nivel (`risk-*`); MEDIO amarillo; CRITICO con relleno sólido |
| Severidad de queja | BAJA · MEDIA · ALTA · CRITICA | Igual |
| Prioridad | BAJA · MEDIA · ALTA · MUY ALTA | Las mismas barras en `primary`, sin color de riesgo |

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
| Button | Acciones de página y de diálogo | Primario, secundario, sutil, peligro, IA; 32 · 40 · 48 px (36 en la densidad compacta); cargando (§5.3) | `<button>`; texto en verbo ("Aprobar") | wasichai |
| IconButton | Más acciones, filtrar, cerrar | Con y sin borde | `aria-label` y tooltip obligatorios | wasichai |
| Input, Textarea | Campos | Foco, error, solo lectura, deshabilitado; contador en motivos | `<label>` visible; error con `aria-describedby` | wasichai |
| SearchInput | Búsqueda en listas | Con atajo visible | Etiqueta oculta accesible | wasichai |
| Select, MultiSelect, Combobox | ENUM, relaciones, responsables | Chips quitables; opciones deshabilitadas con razón | Patrón combobox de ARIA | wasichai |
| DatePicker, DateRangePicker | Fechas y periodos: `CampoFecha` (§5.1), en todo el portal; un periodo son dos | dd/mm/aaaa y 24 h; calendario de mes. Un periodo lleva `AtajosDePeriodo` (tableros, reportes y mapa). Marca los feriados del mes con `GET /api/sgspe/calendar/holidays` (un punto `sg-*` y « (feriado)» en el nombre accesible del día); sin respuesta, no marca nada | Tecla por tecla en la grilla | portal |
| Checkbox, Radio, Switch | Opciones | Fieldset con leyenda; una elección con efecto (como «¿Ya se realizó?» de S24) son radios con su efecto debajo | Nativos o `role="switch"` (`ConmutadorDeModo` del modo rápido) | wasichai |
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
| BulkActionBar | Una selección múltiple: `BarraDeSeleccion` | Cuántos van, las acciones sobre ellos, «Elegir los N de esta página», «Quitar la selección» y el resultado del último lote, que queda aunque la selección se vacíe | Región con el conteo por nombre; el conteo en `aria-live`; el resultado en `role="status"` | portal |
| SavedViews | Vistas del sistema, compartidas y mías (ASM-083) | En las listas rediseñadas, el `SelectorDeVista` (§5.1), también Proyectos desde la tercera pasada; Mis tareas y Aprobaciones conservan las pestañas de `VistasGuardadas` | `radiogroup` en el selector; `role="tablist"` en las pestañas | portal |
| SavedViewDialog | Guardar o compartir una vista (ASM-083) | Nombre; visibilidad solo yo, equipo del proyecto u organización (esta última solo para quien puede) | `role="dialog"`; la visibilidad es un `fieldset` de radios | portal |
| LanguageSwitch | Idioma de la interfaz (ASM-084) | Menú del perfil, enlaces del login y «Más» del teléfono; cada idioma en su propia lengua | `lang` en cada opción; la página cambia su `lang` | portal |
| MapAttribution | Atribución del mapa base (ASM-085) | Siempre visible; se pliega a «ⓘ» en pantallas chicas | Texto real enlazable; no tapa los controles | `@wasichai/gis` + portal |
| BulkActionBar | Acciones masivas | Solo acciones válidas para todos los seleccionados | Anuncia "n seleccionados" | portal |
| Pagination | Listas largas | 25, 50, 100 por página | `nav` con página actual | wasichai |
| EmptyState | Vacío inicial y por filtros (`Vacio`); sin acceso, `EstadoSinAcceso` (§5.1) | Con acción principal | Encabezado y texto reales | portal |
| Skeleton | Carga | Con la forma de lo que carga; mensaje y reintento a los 10 s | `aria-busy` | portal |
| Modal, ConfirmationDialog | Decisiones | Diálogo de transición (§6.2); confirmación destructiva con motivo | Foco atrapado; Esc cierra si no hay cambios | wasichai |
| Drawer | Vista previa y formularios cortos | 420 px (la vista previa, acoplada desde 1280 px, §5.3) y 560 px; reemplazable, no apilable | Foco al abrir; vuelve al origen al cerrar | wasichai |
| Toast | Confirmaciones | Éxito con enlace; error persistente; conflicto de versión con "Recargar" | `role="status"` o `alert` | wasichai |
| Alert, Callout | Reglas y avisos en contexto | Peligro, advertencia, información | Texto que dice qué hacer | portal |
| Timeline, ActivityFeed | Historia de un registro | Hechos de negocio en lenguaje natural | Lista ordenada | portal |
| CommentThread | Conversación sobre un registro | Menciones | Cada comentario con autor y hora | portal |
| FileUploader, FilePreview | Evidencias | Tipo de evidencia y clasificación obligatorios; vista previa con metadatos | Zona de arrastre con botón alternativo | wasichai documents + portal |
| ProgressBar, ProgressRing | Tope de campaña, mitigación, metas | Con valor y texto | `role="progressbar"` con valores | portal |
| StepIndicator, WorkflowStepper | Formularios por pasos (`FormularioPorPasos`) y ciclo de un registro (`CaminoDelFlujo`) | El stepper muestra el camino feliz; los desvíos son estados; en una ficha va plegado dentro del panel de decisión (§6.1) | Paso actual con `aria-current="step"`; «Ver el camino» con `aria-expanded` | portal |
| Map, MapLayerControl | Mapa del proyecto y territorial; «Lista + mapa» (§5.3) | Capas en cuatro grupos (Referencia, Proyecto, Tenencia, Gestión) con la paleta cartográfica de §3.3; candado en capas sensibles | Alternativa en tabla sincronizada | `@wasichai/gis` + portal |
| Chart | Gráficos | ECharts con "Ver datos" debajo | Tabla de datos y descripción | portal |
| CommandPalette | Búsqueda global (⌘K) | Recientes, sugeridas, categorías, vista previa | Patrón combobox con listbox | portal |
| ContextBar, TerritoryChip | La banda de contexto con las migas y el sello de alcance (§5.2) | Sello de proyecto, de territorio o global | `nav` "Contexto" | portal |
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
| `SelectorDePagina` | Las páginas de un módulo que tienen ruta propia: Compromisos y Acuerdos (DD-23). Las de Riesgos (registro, alerta temprana, incidentes, señales, menciones, impactos y tablero) son, desde Civic Modern, una fila de pestañas bajo el encabezado, y las vistas del registro, el único control segmentado | El mismo aspecto que el selector de vista; navega entre rutas sin cambiar ninguna; con una sola página no se dibuja | Navegación de enlaces con `aria-current`, no un `radiogroup`: las flechas no sacan a nadie de su página |
| `BarraDeFiltros` | Los filtros de una lista (DD-04) | El selector de vista, la búsqueda, hasta tres filtros frecuentes, «Más filtros» en un drawer, un chip por filtro aplicado y «Limpiar filtros», todo en la URL. La búsqueda se ve al instante y llega a la URL 300 ms después de la última tecla, o con Enter. Por debajo de `md`, los frecuentes pasan al drawer y el botón dice «Filtros (N)» | El «×» de cada chip es un botón con nombre («Quitar el filtro …») |
| `CampoFecha` | Toda fecha y fecha con hora del portal (DD-07) | Muestra y lee `dd/mm/aaaa` y la hora de 24 h en los dos idiomas, sea cual sea el navegador; acepta `20102026`, `20/10/2026` o `20-10-2026` y normaliza al salir; el valor es el día o la hora de Lima (`ZONA_DE_LIMA`). Error de formato y de rango al salir del campo. El botón, que nombra su campo («Elegir Desde en el calendario»), abre `CalendarioDeFecha`, una grilla de mes que escribe el día elegido y conserva la hora. Ningún `<input type="date">` en el portal (`camposDeFecha.test.ts`) | Calendario con el patrón «Date Picker Dialog» de WAI-ARIA, no modal: lunes primero, flechas por día o semana, RePág y AvPág por mes (con Mayús, por año), Inicio y Fin, Enter o Espacio; Esc cierra y devuelve el foco al botón |
| `AtajosDePeriodo` | Junto a los dos `CampoFecha` de un periodo: tableros, reportes de QCS y mapa | «Este mes», «Mes pasado», «Últimos 30 días», «Este año» y «Año pasado», con el día de Lima; un periodo en curso termina hoy y los pasados son completos. Llenan Desde y Hasta; en un reporte vuelven a la primera página | Grupo «Atajos de periodo»; el que coincide con el periodo, también tecleado, lleva `aria-pressed` |
| `TablaPriorizada` | Las listas anchas (DD-05) | Columnas esenciales siempre, secundarias desde `xl` y opcionales desde 1536 px, salvo que «Columnas» las muestre u oculte en todos los anchos; la elección se recuerda por tabla en el navegador (`sgspe.columnas.<tabla>`). Con `ocultarUniformes`, una columna no esencial con un único valor sale y su valor queda en una nota. El orden por columna lo pide quien la usa y viaja en la URL (`?sort=&dir=`, con el id de la columna; DD-37), así que se comparte con el enlace y vuelve al regresar de una ficha: se pide al servidor (`sort` y `dir`) o se aplica en memoria si la lista filtra en el navegador (`ordenEnCliente`, `Intl.Collator('es')`, estable, lo vacío al final). Ordenan Alertas (S41), Interacciones (S22), Programas y Aportes (S42), Compromisos y Acuerdos (S28), Riesgos (S39) y Grupos de interés (S16); la cola de QCS (S35) espera wasichai/sgspe#160 | Encabezado ordenable como botón y `aria-sort` en el `th`; cada fila enfocable: Enter abre la ficha y Espacio, la vista previa (`?peek=`) o, con `detalleDe` (QCS), la fila expandida. Con `seleccion`, una columna de casillas con nombre, «todas» con estado intermedio y `aria-selected` en cada fila |
| `ListaPriorizada` | Lo más urgente de un grupo, en lugar de un muro de enlaces (DD-13) | Las primeras entradas con su motivo y su fecha, y «Ver los N» hacia la lista filtrada; sin lista a la que ir, «Mostrar N más» despliega el resto en el lugar. Con `total` nulo (QCS confidenciales ocultas), sin número | Sin subrayado; el tono es un borde izquierdo y siempre el texto del motivo |
| `EstadoSinAcceso` | Una sección o pantalla que la cuenta no puede abrir (DD-12) | Qué no puede abrir, por qué en lenguaje llano y a quién pedirlo: dentro de un proyecto, la jefatura por su nombre si la cuenta lee las asignaciones; si no, la administración de la organización. Nunca nombra un rol ni un permiso técnico | Su título es el `h1`; la salida («Volver…») es lo único enfocable |
| `CamposPlegables` | Los atributos de una sección de ficha (DD-06) | A la vista, los que tienen dato; los vacíos, tras «Mostrar los N campos sin dato». Un dato restringido no es un vacío: se ve siempre con su candado | El botón dice cuántos campos muestra y lleva `aria-expanded` |

### 5.2 Shell

La forma del shell; qué contiene cada pieza y su comportamiento están en [`ux-architecture.md`](ux-architecture.md) §7.1.
Arriba suman unos 170 px en la densidad normal (cabecera 64 + 4, navegación 52, banda de contexto 44 como mínimo) y unos
150 px en la compacta.

| Pieza | Medidas y aspecto |
|---|---|
| Cabecera institucional | 64 px (52 px compacta) en `header`, con un borde inferior de 4 px `stripe`; relleno lateral de 32 px y separación de 16 px. Marca (isotipo de 36 px, «SGSPE» 20 px 800) · separador vertical de 1 px blanco al 35 % · organización en 13 px 600 (dos líneas, hasta 200 px) · búsqueda: campo blanco de 44 px (40 px compacta), borde blanco de 2 px, hasta 520 px, con el atajo en un `kbd` de Plex Mono · selector de proyecto: botón blanco de 40 px con el código en Plex Mono y el nombre en `header` · «Preguntar»: botón de 40 px con borde de 2 px `ai-header-border` y la chispa · campana: botón de ícono de 40 px con insignia amarilla `halo` (20 px, 12 px 800, borde de 2 px `header`) · avatar de 36 px blanco con las iniciales en `header`. Los demás botones de la cabecera: transparentes con borde blanco al 55 %. En oscuro, la búsqueda pasa a fondo `surface-sunken` con borde `border-control` |
| Navegación principal | 52 px (44 px compacta) en `surface` con borde inferior de 1 px `border`; relleno lateral de 32 px. Cada entrada: 15 px 600 `text`, relleno de 14 px, bordes superior e inferior de 4 px transparentes; la activa, borde inferior `primary`, texto `primary-text` 700. Los bloques llevan un chevrón; Administración va al final, alineada a la derecha |
| Desplegable de bloque | `surface-raised`, borde de 2 px `border-strong`, radio 4, sombra `overlay`, 8 px arriba y abajo, de 360 px de ancho. Cada sección: 10 × 16 px, 15 px, con su ícono, su nombre y, debajo en `body-sm` `text-secondary`, su descripción o su conteo; la actual en `primary-soft` con borde izquierdo de 4 px `primary` |
| Banda de contexto | Mínimo 44 px, relleno de 10 × 32 px, fondo `bg`, borde inferior de 1 px `border`. Migas en 14 px `text-secondary` con enlaces subrayados en `primary-text` y el paso actual en 600 `text`. A la derecha, el **sello de alcance**: 28 px, borde de 1 px, radio 4, 13,5 px 600; de proyecto en `primary-soft` con borde y texto `primary`; de territorio en `territory-soft` con borde `territory-border` (y entonces toda la banda va en `territory-soft` con borde inferior de 2 px `territory-border`); global, «Alcance: Todos mis proyectos (N)» en `surface` |
| Banda del proyecto (Proyecto 360) | Solo en el resumen del proyecto, la banda de contexto crece (las demás pestañas llevan la banda de contexto normal con el sello; en el resumen, la banda de contexto pasa a `surface` sin borde y sin sello, y las dos se leen como una; [`civic/implementacion.md`](civic/implementacion.md) §4): `surface` con borde inferior de 2 px `border-strong`, relleno de 12 × 32 × 16 px: migas, antetítulo, `h1` con el chip de estado, una fila de datos (jefatura, etapa, fase, campaña con su tope) y las acciones a la derecha |
| Navegación local del proyecto | 248 px, `surface`, borde derecho de 1 px `border`, 16 px arriba. Cada entrada: 40 px (36 px compacta), relleno 0 16 0 20 px, 15 px 500 con su ícono, borde izquierdo de 4 px transparente; a la derecha el total en 13 px 700 `text-secondary` y, si pide acción, un punto `danger` con su número en 800. La activa: `primary-soft`, borde `primary`, texto `primary-text` 700. Tras Documentos, el `overline` «MÁS» y Tareas, Bandeja de IA, Reportes y Equipo y parámetros; al pie, la leyenda «● = vencidos o críticos» |
| Tableta (768–1023) | Cabecera de 60 px y relleno de 16 px; botones de 44 px. La navegación local pasa a una fila de pestañas de 48 px (14,5 px 600, borde inferior de 4 px en la activa) con «Más» |
| Teléfono (< 768; barra inferior < 640) | Cabecera de 56 px con botones de ícono de 48 px (menú, buscar, campana) y la marca o el lugar; barra inferior de 64 px en `surface` con borde superior de 2 px `border-strong`, cinco destinos iguales (ícono y rótulo de 12 px 600 `text-secondary`; el activo en `primary-text` 800 con un borde superior interior de 4 px `primary`): **Inicio · Proyectos · Registrar · Mapa · Tareas**. Sin señal, un aviso `warning` a todo el ancho bajo la cabecera, con borde inferior de 2 px |

### 5.3 Especificación visual de los componentes

Las medidas son las de la densidad normal; la compacta cambia solo lo que dice §3.5. Las clases de referencia son las de
la hoja común del lienzo (`civic.css`); en el portal, cada pieza es un componente con sus tokens.

- **Botones.** Alto 40 px (32 px `sm`, 48 px `lg`; 36 px en la densidad compacta, `--sg-control-compact`), relleno de 16 px (12 px `sm`, 22 px `lg`), radio 4, 15 px 700, borde de
  2 px, una línea, verbo en el rótulo («Aprobar compromiso», «Desbloquear y reprogramar»). Variantes:
  - **primaria**: `primary` con texto `on-primary` y una sombra inferior sólida de 2 px `primary-hover`; una sola por
    contexto (§6.1);
  - **secundaria**: `surface` con borde `primary` y texto `primary-text`;
  - **sutil**: sin borde ni fondo, texto `primary-text` subrayado, relleno de 8 px («Ver historial», «Cancelar»);
  - **peligro**: `danger` con texto `on-danger`, solo en un diálogo de confirmación o al final de «Más acciones»;
  - **IA**: secundaria con la chispa y borde discontinuo `ai-border` (dentro de un bloque de IA);
  - **deshabilitado**: §3.8; **ícono**: cuadrado de 40 px (32 px `sm`) con `aria-label` y tooltip.
- **Campos.** Rótulo `label` (15/22 700) arriba, ayuda `body-sm` `text-secondary` debajo del rótulo y el campo debajo: 44 px,
  relleno de 12 px, borde de 2 px `border-control`, radio 4, `surface`. Con error, el mensaje en 14,5 px 700 `danger` con
  ícono va entre el rótulo y el campo, y el borde pasa a 3 px `danger`. `Select` con el chevrón a la derecha;
  `CampoFecha` con el ícono del calendario a la izquierda y el formato en la ayuda («Por ejemplo, 15/12/2026»). Casilla de
  20 px, borde de 2 px `border-control`, radio 3; marcada en `primary` con el check blanco. En el login y en el teléfono,
  campos de 48 px con `body-lg`.
- **Barra de filtros y chips.** Una sola fila en un panel `surface` con borde de 1 px y relleno de 10 × 12 px, separación de
  8 px: búsqueda (campo de 36 px), el **selector de vista** (control segmentado: borde de 2 px `primary`, segmentos de 30 px
  en 14 px 600, el elegido en `primary` con `on-primary`; o un botón «Vista: Abiertos» si hay más de cuatro), hasta tres
  filtros frecuentes como **chips** (32 px, borde de 1 px `border-strong`, radio 4, 14 px, el nombre y, si está aplicado,
  su valor en 700; aplicado, borde de 2 px `primary` sobre `primary-soft`, con «×» que es un botón con nombre), «Más
  filtros», «Limpiar filtros» (sutil) y, a la derecha, «Agrupar por situación» o «Vista compacta» (casilla). Si no cabe,
  los frecuentes pasan a «Filtros (N)».
- **Tablas.** Caja `surface` con borde de 1 px y radio 4. Encabezado fijo de 40 px en `surface-sunken`, 13 px 700
  `text-secondary`, con borde inferior de 2 px `border-strong`; ordenable como botón con `aria-sort`. Filas de 44 px
  (36 px compacta), relleno de 6 × 12 px, 14,5 px, separadas por 1 px `border-subtle`; hover `surface-hover`. La columna del
  título manda: ocupa el ancho sobrante (mínimo 240 px) y es la única que se parte en líneas: código en Plex Mono 12,5 px
  `text-secondary` arriba, título en 600 `text` y una línea de metadatos `caption` debajo; todo lo demás (chips, fechas,
  montos, códigos) va en una línea. Las cifras a la derecha, con cifras tabulares. Fila elegida: `primary-soft` con un
  borde izquierdo interior de 4 px `primary`. Grupos (Vencidos, Por vencer, Al día): fila de 36 px en `bg`, 13,5 px 700
  `text-secondary`, con su conteo y un chevrón para plegar. Sin subrayados dentro de la tabla: la fila entera es el
  enlace. Paginador al pie (botones de 32 px, el actual en `primary`) con «Mostrando 1–8 de 23».
  **Notas al pie** (préstamo de C): bajo la tabla, en `caption` `text-secondary`, lo que la tabla necesita para leerse:
  unidades y monedas («Montos en soles; — = sin monto»), un filtro implícito, lo que no se cuenta («No incluye 1 QCS
  confidencial»). **«—» en columnas numéricas:** un valor numérico vacío (un compromiso sin monto) se escribe «—» en
  `text-muted`, siempre con su nota al pie; un valor que la cuenta no puede leer no es un vacío: lleva el candado de
  «Restringido» o se omite (DD-38).
- **Vista previa acoplada** (préstamo de D). El `?peek=` de las listas, desde 1280 px, es un panel de 420 px a la derecha
  de la tabla (separación de 16 px) con borde de 2 px `border-strong`, que se desplaza con la página: antetítulo, título
  `h2` de 19/26, chips de estado y situación, las acciones de la ficha en `sm`, el motivo de un bloqueo, el panel de
  decisión en 2 × 2, los atributos con datos, la trazabilidad y, si hay, el bloque de IA. Abrirla no cambia la ruta ni la
  posición de la lista; Esc o «×» la cierra y devuelve el foco a la fila. Por debajo de 1280 px es el cajón de 420 px de
  hoy. Es la misma vista en Proyectos, Grupos de interés, Relacionamiento, Compromisos, Riesgos, Programas y Aportes. La
  cola de QCS no la usa: mezcla tres workflows y su fila se expande con el camino del flujo, la siguiente acción y la
  primaria (`TablaPriorizada` con `detalleDe`; Espacio abre la fila y Enter, la ficha; [`civic/implementacion.md`](civic/implementacion.md) §4).
- **Lista + mapa** (préstamo de B). En Compromisos, QCS, Grupos de interés y Riesgos, una vista más del selector, nunca la
  de entrada, y solo si los registros de la lista traen ubicación (lugar, localidad o geometría): la tabla a la izquierda y
  el mapa a la derecha (40 % del ancho, mínimo 420 px), sincronizados por `?selected=`: elegir una fila centra y marca su
  punto y elegir un punto marca su fila. El lugar sale de la capa de gestión que ya ubica el objeto (LYR-19 compromisos,
  LYR-17 grupos por su localidad, nunca un punto propio, LYR-20 QCS y LYR-21 riesgos); en Riesgos, «Vigentes en el mapa» va
  bajo la matriz. Usa la paleta y las formas de §3.3. Sin registros con ubicación, la vista no se ofrece.
- **Chip de estado** (estado de workflow, §4.1). 26 px, relleno de 8 px, radio 4, borde de 1 px del color de la tinta,
  fondo suave del tono, 13,5 px 600, ícono de 14 px a la izquierda y el rótulo en tipo oración («En ejecución»).
- **Situación** (situación derivada, §4.2). Sin caja: reloj de 14 px y el texto en 13 px 800 MAYÚSCULAS con +0,03em y un
  subrayado punteado de 2 px del mismo color: `danger` vencido, `warning` por vencer, `success` al día, `text-muted` sin
  plazo. Con días: «VENCIDO · 54 DÍAS», «VENCE EN 3 DÍAS HÁBILES». Acompaña al chip de estado, debajo en las tablas.
- **Nivel con barras** (§4.3). 24 px, relleno 0 7 0 5 px, radio 4, borde de 1 px, 13 px 800 +0,04em; a la izquierda cuatro
  barras de 4 px de ancho y 5, 8, 11 y 14 px de alto (radio 1), las del nivel llenas y las demás vacías con contorno.
  BAJO: `risk-low` con borde `success`, una barra, texto `text`. MEDIO: `risk-medium` con borde `level-medium-border`,
  dos barras y texto `level-medium-ink`. ALTO: `risk-high` con `level-high-border`, tres barras, `level-high-ink`. CRITICO:
  `risk-critical` sólido con texto y barras `on-critical`. La **prioridad** usa las mismas barras en `primary` sobre
  `surface`, con texto `text` y sin color de riesgo (BAJA · MEDIA · ALTA · MUY ALTA).
- **Postura.** Un círculo de 12 px del color de su `stance-*` con contorno de 1 px `border-strong` y el valor en 13 px 700
  +0,02em, que puede ir en dos líneas en una tabla. En barras apiladas, el número dentro de cada tramo (blanco sobre A FAVOR
  y EN CONTRA, `text` sobre NEUTRAL, `#1D1400` sobre ABIERTO) y la leyenda en texto debajo. Sin permiso para verla:
  «Restringida».
- **Etiquetas.** 24 px, relleno de 7 px, radio 4, 12,5 px 700 +0,03em, borde de 1 px `border-strong` y texto
  `text-secondary`: TERRITORIO en `territory` sobre `territory-soft` con borde `territory-border`; SUGERENCIA IA con borde
  discontinuo de 1,5 px `ai-border` sobre `ai-soft`; CONFIDENCIAL en `critical` sobre `critical-soft`. «Restringido» no es
  una etiqueta: candado y texto en cursiva `text-muted`.
- **Alertas.** Relleno de 14 × 16 px, borde de 2 px, radio 4, ícono de 18 px arriba a la izquierda en la tinta del tono,
  título en 700 y el texto con un enlace que dice qué hacer. Peligro: `danger` sobre `danger-soft`; aviso: borde
  `warning-border` sobre `warning-soft`; información: `info` sobre `info-soft`; éxito: `success` sobre `success-soft`.
- **Resumen de errores.** Arriba del formulario al enviar con errores: borde de 3 px `danger`, `surface`, relleno de
  16 × 20 px, título «Hay 2 problemas para reprogramar DEMO-C-04» y una lista de enlaces en `danger` 700, cada uno al campo
  con su error; recibe el foco.
- **Panel de decisión.** Rejilla de cuatro celdas (dos por dos en la vista previa y en tableta) con borde de 2 px
  `border-strong`, `surface`, radio 4 y separadores de 1 px; cada celda, relleno de 12 × 16 px, el rótulo en `body-sm`
  `text-secondary` y el valor en 700 (el vencimiento vencido en `danger`). Es el único lugar del estado (§6.1).
- **Trazabilidad.** Tres columnas iguales («De dónde viene», «Qué generó», «Lo sustenta») con borde de 1 px, separadores
  de 1 px y relleno de 10 × 14 px, 14 px; cada una con su título en 700 y enlaces a los registros. En una ficha estrecha,
  una lista de definiciones (`dl` de 150 px y el resto).
- **IA.** Todo lo que propone la IA va en un bloque con borde discontinuo de 2 px `ai-border` sobre `ai-soft`, radio 4,
  relleno de 14 × 16 px: un antetítulo en `ai` 12,5 px 800 MAYÚSCULAS con la chispa («RESUMEN IA · PROPUESTA, VERIFIQUE
  ANTES DE USAR», «CANDIDATOS DE IA · 2 PROPUESTAS»), el texto en 15/24, las fuentes («Basada en: …» con «Ver citas») y las
  acciones de una persona («Adoptar», «Revisar candidatos», «Descartar»). Nunca se mezcla con un dato registrado.
- **KPI.** Una fila de tarjetas en una sola caja con borde de 1 px y separadores verticales: relleno de 14 × 18 px, el
  rótulo en 14 px 600 `text-secondary`, la cifra en `kpi` (30/36 800) y, debajo, en 13 px `text-muted`, la meta o el
  desglose («63 de 70 (meta) · 90 % de la meta del mes») y, si hay, la tendencia (flecha y 13 px 700). El código del
  indicador en Plex Mono 11,5 px `text-muted`. La cifra en `danger` o `warning` solo cuando el rótulo ya dice que es lo
  vencido o lo por vencer. Un dato reservado dice «No disponible» y por qué. Los tableros muestran la **hora de cálculo**
  bajo el título («Calculado hoy 07:40 con los datos de los 4 proyectos de su alcance»; préstamo de D).
- **Medidores.** Barra de 12 px (16 px en un medidor principal) con borde de 1 px `border-strong`, radio 2, fondo
  `surface-sunken` y relleno `primary`; el porcentaje y «S/ 1 284 500 de S/ 2 150 000» en texto al lado. Uno por moneda:
  nunca se suman monedas distintas (ASM-063). Las barras de puntuación (influencia, interés) son de 10 px con la cifra a la
  derecha («4,2/5»).
- **Pestañas.** Fila con borde inferior de 2 px `border`; cada pestaña de 10 × 16 px, 15 px 600 `text-secondary` con su
  conteo; la activa en `primary-text` 800 con borde inferior de 4 px `primary`. Solo dentro de una ficha (Resumen,
  Participantes, Acta…); nunca una segunda fila (DD-03).
- **Pasos y «Revise sus respuestas».** Los pasos son una fila de segmentos iguales con un borde superior de 4 px: hecho en
  `progress` con el número en un círculo de 24 px relleno y un check, actual en `primary` y 800, pendiente en `border`. El
  último paso antes de confirmar es **«Revise sus respuestas»**: una lista de definiciones (150 px y el resto) con cada
  respuesta y un enlace «Cambiar» que vuelve a su paso, y los botones «Confirmar y ‹verbo›» y «Cancelar». El camino de un
  workflow (`CaminoDelFlujo`, en una fila de QCS expandida) usa los mismos números en círculo.
- **Tooltip.** Fondo `text`, texto `surface`, radio 4, relleno de 8 × 12 px, 13,5/19, sombra `overlay`, hasta 260 px; sin
  acciones. En un gráfico, el periodo en 700 y las cifras con su meta.
- **Esqueleto.** Barras `surface-sunken` de 12 px (9 px para un código) con radio 3, con la forma de lo que carga (código,
  título, metadatos, chip, cifra), sin brillo ni animación; el texto «Cargando compromisos…» va visible y en
  `role="status"`.
- **Vacío, carga, error y éxito.** El mismo marco en los cuatro: el título y los filtros no se mueven; cambia solo la zona de
  resultados (§6.5).
  - **Vacío por filtro:** un panel con relleno de 40 × 32 px, alineado a la izquierda: «Ningún compromiso coincide con
    estos 3 filtros», qué dicen los filtros en lenguaje llano y lo que sí hay («Hay 13 compromisos abiertos en el
    proyecto»), con «Quitar los filtros» y «Ver los 13 abiertos».
  - **Cargando:** el esqueleto.
  - **Error al cargar:** alerta de peligro con qué pasó («El servidor no respondió en 30 segundos»), que los filtros se
    conservan, el código de soporte en Plex Mono y «Reintentar».
  - **Error al enviar:** el resumen de errores y el error en cada campo.
  - **Éxito:** alerta de éxito con lo que pasó y lo que falta («Se avisó a… Falta comunicar la nueva fecha al comité de
    regantes») y «Ver el compromiso»; la fila cambiada se marca como elegida.
- **Gráficos.** ECharts en color sólido, sin trama. Texto en Public Sans 12,5 px `text-secondary`; líneas guía de 1 px
  `grid`; ejes de 1 px `border-strong`; valores en 13 px 700 `text`. **Anotación directa** (préstamo de C): el valor va
  junto a la marca que importa (el extremo, el último valor, la meta) y la serie se rotula al final de su línea cuando
  caben; la leyenda en texto queda solo si hay dos series o más y no caben. La meta es una línea discontinua de 2 px `text`
  (`4 3`) con su rótulo. Cada gráfico lleva «Ver datos» en su cabecera y la tabla debajo (RQ-DSH-035). Barras con radio 2.
- **Mapa.** Controles en una columna de botones de 36 px con borde de 2 px `border-strong` y separadores de 1 px (acercar,
  alejar, mi ubicación) y el norte en otro de 40 px; la escala numérica y gráfica abajo a la izquierda sobre `surface`;
  «Ver como lista», que abre y enfoca la lista de lugares (la alternativa accesible del mapa). El control de capas es un panel flotante de 300 px con sombra `overlay`: grupos en
  `overline`, cada capa con su casilla, su muestra de 22 × 10 px, su nombre y su código en Plex Mono; una capa
  confidencial sin permiso, deshabilitada con candado y la etiqueta. La lista sincronizada del lugar va en un panel de
  380 px a la derecha con borde izquierdo de 2 px `border-strong` (§6.7, S09).

## 6. Patrones núcleo

### 6.1 Ficha de entidad

La misma anatomía para todo objeto importante [PRM-02 §26], en tres franjas (DD-06):

1. **Contexto:** la banda de contexto (migas y sello de alcance, §5.2); el rastro si se llegó por un recorrido.
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
   Evidencias, Actividad…). Una sección con campos que la ficha deja editar lleva su **«Editar»** junto al título
   (`EditarSeccion`): abre un cajón con solo esos campos y el motivo si el objeto lo pide. El «Editar» de la cabecera sigue
   para todo. En un cajón, las acciones de un formulario largo quedan fijas al pie del área que se desplaza.
   Al llegar de un alta, bajo la cabecera va un **aviso de éxito** (`AvisoDeExito`, una sola vez) con lo que se creó y lo
   que el modelo hizo con ello: «Se registró la queja Q-005. Se creó la tarea de acusar recibo.» (DD-34).
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
- **Los datos del propio paso se piden en el diálogo** (DD-33): la resolución y la respuesta de una queja, la respuesta de una
  consulta y cuándo se comunicó, la decisión de tratamiento y el motivo de cierre de un riesgo, la respuesta inmediata y las
  consecuencias de un incidente, las fechas de una interacción y de un acuerdo. La lista es explícita por objeto: no se piden
  datos que cambian qué reglas se evalúan (el tipo de un aporte decide sus lineamientos). Si solo faltan esos datos, el botón
  de la transición no se deshabilita: el diálogo los pide, los guarda con `If-Match` y aplica la transición sobre la versión
  nueva con un solo motivo. Si lo que falta es otro campo que la persona puede escribir, la ficha ofrece «Completar ‹campo›»
  junto al bloqueo, con solo ese campo (DD-32).
- **El botón de confirmar repite el verbo** de la transición («Resolver», «Devolver acta»); el que cierra el registro nombra el
  objeto («Cancelar el compromiso») y va con peligro. **«Qué pasará»** suma lo que el paso hace además del estado, solo donde el
  modelo lo hace (la tarea que crea una automatización, lo que se congela).
- **Después de aplicar** (DD-34), «Listo: el registro está en ‹estado›. Ahora: ‹siguiente acción›» queda hasta la siguiente
  acción y recibe el foco, porque el botón que abrió el diálogo puede irse con el estado nuevo.

### 6.3 Formulario por pasos

- Formularios largos en pasos con un indicador lateral, guardado automático del borrador y "Guardar y salir" [PRM-02 §27].
- Validación en línea con Zod; el paso no avanza con errores, pero se puede volver a cualquier paso ya visitado.
- Los pasos siguen las reglas del dominio. Crear un compromiso: 1 Origen (interacción, solicitud aceptada, cláusula o carta;
  justificación si nace de un taller o una reunión informativa, RN-ENG-001), 2 Qué se promete (descripción, objetivo, recurso,
  cantidad y unidad), 3 Partes y beneficiarios, 4 Plazo e hitos, 5 Monto y campaña (con el tope a la vista, RN-INV-003),
  6 Responsable, 7 Revisar y enviar (con los campos mínimos de la guarda D1). La evidencia no se pide al crear: se exige al
  registrar el cumplimiento (RN-COM-001).
- Cancelar un borrador pide confirmación; salir con cambios sin guardar avisa. Con borrador, el aviso de salida dice que lo
  escrito queda en este navegador.
- **El borrador** (`useBorradorLocal`, DD-41) se guarda medio segundo después del último cambio y al salir de la pantalla,
  con los ids elegidos fuera del formulario (los solicitantes de un compromiso). Se recupera con un aviso que dice desde
  cuándo y ofrece descartarlo. Es de quien lo escribió:
  - la clave lleva su cuenta, y otra persona en el mismo navegador no lo ve;
  - «Cerrar sesión» borra los de este navegador. Si la sesión vence, quien vuelve a entrar lo recupera;
  - lo restringido nunca se guarda (la persona de una QCS);
  - una QCS confidencial no deja borrador.
  Lo tienen el compromiso, la interacción (cada modo, el suyo), la QCS y la evaluación.
- **Cambiar de modo no borra lo cargado:** el conmutador del modo rápido lleva al otro modo lo que ese modo sabe recibir (el
  tipo, los grupos presentes, los asistentes contados y el punto). Lo demás queda en el borrador del modo que se deja.
- **Se propone lo que ya se sabe, siempre editable,** y lo propuesto no cuenta como un cambio por avisar:
  - «Sí, ya se realizó» propone la hora de ahora y lo dice bajo el campo;
  - un alta abierta desde una interacción propone su fecha y sus grupos de interés;
  - el responsable ofrece «Asignarme»;
  - «¿En qué proyecto?» propone el último elegido.
- Una lista de grupos de interés con seis o más trae su búsqueda (`EleccionDeGrupos`): por el nombre que ve quien mira, sin
  tildes. Lo elegido no se esconde al buscar.
- Una lista hija que se carga de a uno (los ítems de un aporte) deja el cajón abierto tras guardar, con el formulario vacío
  para el siguiente y «Listo» para cerrar.

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
- Montos con su moneda; nunca se suman monedas distintas (ASM-063). Un monto vacío es «—» con su nota al pie (§5.3).
- **Vista previa** (`?peek=`): Espacio sobre una fila o clic en ella la abre acoplada a la derecha desde 1280 px (§5.3); Enter
  abre la ficha. En la cola de QCS, Espacio expande la fila (§5.3).

### 6.5 Vacío, carga, error y permisos

| Situación | Qué se muestra |
|---|---|
| Vacío inicial | Qué es la sección, cómo nace el primer registro y la acción para crearlo [PRM-02 §29] |
| Vacío por filtros | Los filtros activos, una pista ("sin el filtro de territorio hay 3") y "Limpiar filtros" |
| Cargando | Esqueleto con la forma real, sin brillo, y el texto «Cargando…» en `role="status"`; el encabezado de una ficha aparece primero; reintento a los 10 s |
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

### 6.7 Notas por pantalla del lienzo

Lo propio de cada tablero del lienzo de la dirección E ([`civic/`](civic/README.md)); la ficha de handoff completa de cada
pantalla sigue en [`screens.md`](screens.md). Lo que la implementación decidió distinto, la migración por pantalla y los
límites conocidos están en [`civic/implementacion.md`](civic/implementacion.md). Los tableros S01 a S12 son 1440 px de ancho.

| Tablero | Pantalla | Notas |
|---|---|---|
| S01 | Ingreso | Sin navegación: la cabecera con la marca, dos columnas. A la izquierda, `overline` «Acceso del personal», título de 36/44, campos de 48 px (la contraseña con «Mostrar» pegado a la derecha), «Olvidé mi contraseña» y la primaria `lg` «Ingresar» de 200 px como mínimo; debajo, un aviso `info` de acceso autorizado. A la derecha, un panel con borde superior de 6 px `primary`: qué encontrará al ingresar. Pie con borde superior de 4 px `primary`, enlaces a privacidad y accesibilidad y la versión |
| S02 | Inicio «Mi trabajo» | Saludo y resumen en una línea («Tiene 4 tareas vencidas o por vencer y 6 avisos sin leer») con el sello «Alcance: Todos mis proyectos (4)» y la fecha de Lima. Dos columnas (el resto y 440 px): «Lo que me toca hoy» (cada tarea con su verbo, su registro, el chip de situación y su botón) y «Qué es crítico y dónde se atiende» con la hora de cálculo; a la derecha la agenda de la semana (día en bloque de fecha) y «Mis proyectos» como tabla compacta. El tablero muestra el desplegable de «Trabajo con las comunidades» abierto con las descripciones y conteos de §5.2 |
| S03 | Revisión ejecutiva | Título con periodo y «Calculado hoy 07:40 con los datos de los 4 proyectos de su alcance»; «Descargar PDF». Ocho KPI en dos filas de cuatro, cada uno con su código, su meta o desglose (vencidos por tramo como barra de tres segmentos) y «No disponible» con el motivo para la postura agregada. Tres gráficos con anotación directa y «Ver datos»; lo crítico ordenado por gravedad y la inversión ejecutada con un medidor por moneda y la nota «Las monedas no se suman» |
| S04 | Portafolio | «Tabla · Mapa» como selector en la cabecera, barra de filtros en una fila con «Vista compacta», la tabla de proyectos (los encabezados de cifras en dos líneas, cifras a la derecha) y la vista previa del proyecto elegido debajo, en cuatro columnas, con «Ver en el mapa» y «Abrir Proyecto 360». «—» en «Campaña en curso» lleva su nota al pie |
| S05 | Proyecto 360 | La banda del proyecto (§5.2) y la navegación local de 248 px. Cinco KPI con meta; dos columnas: «Qué requiere atención» (filas de 52 px con la cifra a la izquierda en 20 px 800 y su acción), el resumen de IA con sus fuentes y la actividad reciente; a la derecha, el paisaje de grupos (barra de postura apilada con números y la matriz interés × poder en cuatro cuadrantes), reuniones frente a la meta y las áreas de influencia con su estado |
| S06 | Grupos de interés | Selector de vista con conteos (Prioridad MUY ALTA y ALTA, Todos, Sin contacto en más de 90 días, Evaluación de más de 180 días), filtros y la tabla con el nombre, su tipo, lugar y grupo de influencia en la línea de metadatos, postura, prioridad con barras y «Estado y último contacto». La ficha acoplada de 360 px muestra la evaluación vigente con barras de puntuación y la trazabilidad. Una persona sin permiso sobre la postura ve «Restringida» |
| S07 | Relacionamiento | «Lista · Mes» en la cabecera; una franja de semana con los días como columnas, el día de hoy marcado y los feriados y festividades locales como etiqueta; la tabla con fecha y hora, interacción, estado y asistentes por sexo; la interacción elegida acoplada (420 px) con su panel de decisión 2 × 2, sus pestañas y la trazabilidad |
| S08 | Compromisos | La lista agrupada por situación (Vencidos, Por vencer, Al día, con «Mostrar 5 compromisos más al día»; en el portal, con el orden por vencimiento se agrupan las filas de la página, con la nota «Agrupados por situación en esta página», hasta que el servidor dé el total por situación) y la vista previa acoplada de 420 px: chip de estado y situación, la primaria «Desbloquear y reprogramar» con dos secundarias y «Más acciones», el motivo del bloqueo en una alerta de peligro, el panel de decisión, monto, grupo de interés y lugar (etiqueta de territorio), la trazabilidad en tres columnas y los candidatos de IA. «—» en Monto lleva su nota al pie |
| S09 | Mapa territorial | El mapa a todo el ancho bajo la banda de contexto de territorio (con «Exportar»; el periodo, en el portal, va en una línea bajo el mapa para dejarle el alto útil); el control de capas flotante en cuatro grupos, el aviso «Sin mapa base», los controles y la escala 1:50 000. A la derecha, 380 px: el lugar elegido (tabla compacta de conteos, barra de postura, última actividad, «Abrir ficha del lugar» y «Registrar interacción») y la lista de lugares sincronizada. Usa solo la paleta cartográfica de §3.3 |
| S10 | Riesgos y alerta temprana | Alerta de aviso «Alerta DEMO-A-02 por triar» con «Triar alerta»; la matriz probabilidad × impacto de 420 px (celdas de 64 × 50 px, la cifra en 17 px 800, el cero en 13 px `text-muted`, fondos `zone-*`, la celda con riesgo crítico con contorno de 2 px y la cifra sobre `risk-critical`) con su leyenda por rango; las alertas activas; y el registro con P × I, nivel con barras y tendencia (AUMENTA, SE MANTIENE, DISMINUYE con flecha) y la revisión como situación. La nota «Las alertas no cambian el nivel del riesgo por sí solas» |
| S11 | QCS | Cuatro KPI con la nota con candado de las confidenciales no contadas; el selector «Atención · Todas · Cerradas»; la tabla agrupada por plazo, con «Restringido» en Plantea, severidad con barras y el plazo en días hábiles; la fila elegida se expande con el camino del workflow (pasos numerados), la siguiente acción con su responsable y su botón |
| S12 | Reportes | Pestañas Entregables · Exportaciones · Historial; la tabla de entregables con el estado del borrador (LISTO, EN PROCESO, FALLIDO con su causa) y los niveles como etiquetas. El entregable abierto: pasos de WF-H, tres columnas (secciones con su origen, la sección con la sugerencia de IA y sus acciones, y la lista de verificación de EXTERNO con «21 secciones sin revisar»), y lo emitido con su huella en Plex Mono |
| D13–D16 | Oscuro | S03, S09, S08 y S10 con `data-theme="dark"`: cabecera `#0E2748`, foco amarillo solo, CRITICO `#C42323` con texto blanco y la paleta cartográfica oscura |
| T17 | Tableta | Proyecto 360 a 1024 × 768: cabecera de 60 px, migas de 13,5 px, título de 24/30 con la primaria de 44 px, pestañas del proyecto de 48 px y los KPI con relleno de 10 × 12 px |
| M17, M17b | Teléfono | 390 × 844, texto de 16 px: «Lo que me toca hoy» con cada tarea y su botón de 48 px a todo el ancho; la ficha de compromiso con el título de 22/29, el motivo del bloqueo, los datos en filas, el bloque de IA y la barra fija de acciones (dos botones de 48 px apilados) sobre la barra inferior |
| E18 | Estados | Los cuatro estados de §5.3 sobre la lista de Compromisos |

## 7. Accesibilidad (WCAG 2.2 AA)

- **Teclado:** todo se opera con teclado; orden de foco lógico; atajos documentados (⌘K, ↑ ↓, ↵, Esc, «?»); sin trampas
  salvo en diálogos, que devuelven el foco al cerrarse. Los desplegables de la navegación siguen el patrón «disclosure
  navigation» de WAI-ARIA (`aria-expanded`, Esc cierra y devuelve el foco).
- **Foco visible:** halo amarillo de 3 px con anillo marino de 5 px en claro, y el halo separado 2 px en oscuro (§3.6); al
  menos 3:1 sobre cualquier fondo, también sobre la cabecera azul; nunca se quita.
- **Contraste:** texto ≥ 4.5:1 (el principal, AAA), texto grande y límites de controles ≥ 3:1, verificado por script (§10).
- **Sin depender del color:** estados con ícono y etiqueta; situación con reloj y texto; niveles con barras y palabra; en
  las listas priorizadas, el texto del motivo además del borde; series de gráficos con anotación directa, leyenda en texto
  y «Ver datos», en color sólido y sin trama (DD-15); capas del mapa con forma y trazo propios, no solo color.
- **Etiquetas y ARIA:** `<label>` visible sobre cada campo; error enlazado con `aria-describedby` y resumen de errores con
  enlaces a cada campo; nombres accesibles en botones de ícono; landmarks (`header`, `nav` principal, `nav` de migas, `nav`
  «Secciones del proyecto», `main`, `aside`); tablas con `<th scope>` y `aria-sort`.
- **Gráficos y mapas:** tabla "Ver datos" bajo cada gráfico (RQ-DSH-035); el mapa siempre tiene la lista sincronizada y
  «Ver como tabla» como alternativa; leyendas en texto.
- **Objetivos:** mínimo 24 × 24 (2.5.8); 32 px en escritorio denso, 40 px por defecto, 44 px en tableta y 48 px en el
  teléfono. La densidad compacta no baja de 32 px.
- **Texto:** cuerpo de 15 px, mínimo de 12 px (11,5 px solo en códigos no esenciales en Plex Mono).
- **Arrastrar:** toda acción de arrastre tiene alternativa sin arrastre (2.5.7), como subir archivos o reordenar.
- **Ayuda y autenticación:** la ayuda está en el mismo lugar en todas las páginas (3.2.6); el login permite pegar y usar
  gestores de contraseñas y muestra la contraseña a pedido (3.3.8).
- **Movimiento:** se respeta `prefers-reduced-motion`.
- **Idioma:** `lang="es-PE"` y cambio a inglés sin recargar (RNF-I18N-001).

## 8. Responsive

- **Escritorio (≥ 1024):** experiencia completa: cabecera, navegación principal horizontal, banda de contexto y, en un
  proyecto, la navegación local de 248 px; la ficha con su panel al costado y, desde 1280 px, la vista previa acoplada.
- **Tableta (768–1023):** cabecera de 60 px con menú; la navegación principal en el panel del menú; la navegación local
  del proyecto como fila de pestañas de 48 px; fichas en una columna; tablas sin las columnas secundarias, que quedan en
  la vista previa (cajón).
- **Por debajo de 768:** cabecera de 56 px; la búsqueda de una lista ocupa el ancho y los filtros frecuentes pasan a
  «Filtros (N)»; las acciones de una ficha (la primaria y «Más acciones»), en una barra fija al pie.
- **Móvil (< 640):** no se mete el sistema de escritorio en el teléfono [PRM-02 §31]: barra inferior de cinco destinos
  (**Inicio · Proyectos · Registrar · Mapa · Tareas**, `ux-architecture.md` §7.1), listas en tarjetas, botones de 48 px a
  todo el ancho, texto de 16 px y la barra fija de una ficha sobre la barra inferior. Los avisos van en la campana de la
  cabecera. En el MVP, en línea (ASM-021); el aviso «Sin señal» y la captura sin conexión están diseñados para después
  (ASM-081, `screens.md` §15).
- **Pantallas de escritorio en el teléfono:** una pantalla que no está pensada para el teléfono (editor de acta, importación
  geoespacial, formulario por pasos, tableros con mapa) no se encoge: muestra «Esta pantalla es para una pantalla más
  grande» con la lectura que sí cabe y el enlace para copiar.

## 9. Guía de implementación

- **Tokens:** copiar `docs/ux/tokens.css` al portal (`sgspe-ui/src/theme/tokens.css`, comprobado con `cmp`) e importarlo
  después de `@wasichai/ui/theme.css`. Usar siempre los tokens, nunca colores sueltos; los colores de ECharts y MapLibre se
  leen de las mismas variables con `getComputedStyle`, y se vuelven a leer al cambiar el tema.
- **Tokens de wasichai:** `sgspe-ui/src/theme/wasichai.css` apunta los 28 tokens de `@wasichai/ui` a los `--sg-*` en
  `light` y `dark`, como hoy, con dos cambios: `--shell`, `--shell-ink` y `--shell-muted` leen `header` y `header-text`, y
  `--on-danger` lee `on-danger` (no `on-critical`). No se registran temas propios: `config.themes` queda con `light` y
  `dark`.
- **Fuentes:** `@fontsource/public-sans` (400, 500, 600, 700, 800 y la cursiva 400) e `@fontsource/ibm-plex-mono` (400,
  500, 600), importadas en `index.css`; se retiran las de IBM Plex Sans.
- **Densidad:** el script de arranque lee `sgspe.densidad` y escribe `data-density` en `<html>` antes de pintar, como hace
  con el tema; un hook `useDensidad` la cambia desde el menú de la cuenta y desde «Vista compacta». Los componentes usan
  `h-sg-row`, `h-sg-control` y `h-sg-field`, nunca alturas fijas.
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
  hilo de contexto y las capturas en claro, oscuro y compacta.

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
fondo y de límite y fondo, en claro (`:root`) y en oscuro (`:root[data-theme="dark"]`) (WCAG 2.2: 4.5:1 para texto, 3:1
para foco y límites de controles, 1.4.11). Se corre con `python3 docs/ux/redesign/contraste.py` y termina en `ok` solo si
ningún par obligatorio queda bajo su mínimo. Resultado del 2026-11-09:

| Par | Claro | Oscuro | Mínimo |
|---|---|---|---|
| `text` / `bg` | 16.00 | 16.90 | 4.5 |
| `text` / `surface` | 17.48 | 15.54 | 4.5 |
| `text-secondary` / `surface` | 10.10 | 10.66 | 4.5 |
| `text-secondary` / `bg` | 9.25 | 11.59 | 4.5 |
| `text-muted` / `surface` | 6.90 | 7.62 | 4.5 |
| `text-muted` / `bg` | 6.31 | 8.29 | 4.5 |
| `text-muted` / `surface-sunken` | 5.86 | 8.55 | 4.5 |
| `primary-text` / `surface` | 8.82 | 8.74 | 4.5 |
| `primary-text` / `primary-soft` | 7.41 | 7.79 | 4.5 |
| `on-primary` / `primary` | 8.14 | 5.18 | 4.5 |
| `on-primary` / `primary-hover` | 10.80 | 6.37 | 4.5 |
| `info` / `info-soft` | 6.41 | 7.88 | 4.5 |
| `success` / `success-soft` | 5.97 | 8.77 | 4.5 |
| `warning` / `warning-soft` | 6.61 | 9.51 | 4.5 |
| `danger` / `danger-soft` | 6.17 | 7.86 | 4.5 |
| `critical` / `critical-soft` | 8.24 | 9.10 | 4.5 |
| `neutral` / `neutral-soft` | 6.56 | 8.67 | 4.5 |
| `progress` / `progress-soft` | 6.13 | 8.11 | 4.5 |
| `ai` / `ai-soft` | 7.61 | 8.48 | 4.5 |
| `territory` / `territory-soft` | 6.50 | 9.82 | 4.5 |
| `danger` / `surface` | 7.44 | 8.53 | 4.5 |
| `focus` / `surface` | 16.79 | 11.98 | 3.0 |
| `focus` / `bg` | 15.37 | 13.03 | 3.0 |
| `border-strong` / `surface` | 4.91 | 4.74 | 3.0 |
| `primary` / `surface` | 8.14 | 3.31 | 3.0 |
| `border-control` / `surface` | 5.85 | 5.13 | 3.0 |
| `border-control` / `bg` | 5.35 | 5.58 | 3.0 |
| `border-control` / `surface-sunken` | 4.97 | 5.76 | 3.0 |
| `border-control` / `surface-raised` | 5.85 | 4.63 | 3.0 |
| `text` / `surface-raised` | 17.48 | 14.01 | 4.5 |
| `text-secondary` / `surface-raised` | 10.10 | 9.61 | 4.5 |
| `text-muted` / `surface-raised` | 6.90 | 6.87 | 4.5 |
| `primary-text` / `surface-raised` | 8.82 | 7.88 | 4.5 |
| `focus` / `surface-raised` | 16.79 | 10.80 | 3.0 |

En resumen: el texto queda entre 5.86:1 y 17.48:1 en claro y entre 5.18:1 (blanco sobre `primary` oscuro) y 16.90:1 en
oscuro; el foco, entre 15.37:1 y 16.79:1 en claro y entre 10.80:1 y 13.03:1 en oscuro; el límite de los controles, entre
4.97:1 y 5.85:1 en claro y entre 4.63:1 y 5.76:1 en oscuro. `surface-raised` frente a `surface` y `bg` (1.00 a 1.21:1) es
informativo: lo que flota se separa por su borde de 2 px y su sombra.

Pares que el script no mide y se midieron a mano: `header-text` / `header` 8.14 y 14.97; `halo` / `header` 5.69 y 10.47;
`ai-header-border` / `header` 5.29 y 9.74; `on-critical` / `risk-critical` 7.84 y 5.81; `level-medium-ink` /
`risk-medium` 12.43 y 6.27; `level-high-ink` / `risk-high` 8.89 y 5.48; `warning-border` / `warning-soft` 3.59 y 3.75;
`on-danger` / `danger` 7.44 (blanco) y 9.54 (`#1A0B08`).

## 11. Trazabilidad

- **Requisitos:** RNF-USA-001, RNF-I18N-001, RNF-MNT-001, RQ-DSH-032, RQ-DSH-035, RQ-GIS-014, RQ-GIS-015, RQ-AUD-002,
  RQ-AUD-011, RQ-AI-003, RQ-AI-004, RQ-AI-012, RQ-SEC-005, RN-WFL-001, RN-COM-001, RN-ENG-001, RN-INV-003, RF-COM-003.
- **Decisiones:** [ADR-0032](../adr/0032-identidad-visual-civic-modern.md) (identidad «Civic Modern»);
  [decisiones DD-01 a DD-46](redesign/07-decisiones-y-compatibilidad.md),
  [especificación de componentes del rediseño](redesign/05-especificacion-de-componentes.md).
- **Supuestos:** ASM-021, ASM-025, ASM-063, ASM-066, ASM-075, ASM-076, ASM-081, ASM-082, ASM-083, ASM-084, ASM-085,
  ASM-149 (reemplaza ASM-077 y ASM-086).
- **Documentos:** [workflows.md](../workflows.md), [dashboards.md §3 y §9](../dashboards.md), [gis.md §2 y §7.2](../gis.md),
  [security.md §5 y §9](../security.md), [api.md §3](../api.md), [ADR-0024](../adr/0024-interfaz-portal-y-admin.md),
  [ADR-0025](../adr/0025-concurrencia-e-idempotencia.md).
- **Lienzo y capturas:** [dirección E](https://claude.ai/artifact/Y6aV29ac54BQMERnieY15K), [`civic/`](civic/README.md).
- **Fuentes:** [PRM-02 §4, §7, §8, §23, §25–§31, §35, §42, §43], [WAS-ADR-0035].
