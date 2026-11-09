# Implementación de «Civic Modern» en el portal

Cierre de la épica wasichai/sgspe-ui#88 (fase 13b, wasichai/sgspe#186): qué quedó en `sgspe-ui`, con qué piezas, en qué
pantallas, qué se decidió distinto del lienzo y qué falta. La especificación sigue en [`design-system.md`](../design-system.md)
y [`tokens.css`](../tokens.css) ([ADR-0032](../../adr/0032-identidad-visual-civic-modern.md)); este documento describe la
implementación al 2026-11-09 (`sgspe-ui` en `d0f818c`, PR #115).

Dos numeraciones conviven: los **tableros** del lienzo (S01 a S12, D13 a D16, T17, M17, M17b y E18, en
[`README.md`](README.md)) y las **pantallas** del inventario de [`ux-architecture.md`](../ux-architecture.md) §9 (S01 a S64).
Aquí, «tablero S08» es el lienzo y «S28» es la pantalla.

## 1. Resumen

- Trece fases, cada una con su issue y su PR, fusionadas con la CI en verde entre el 2026-11-08 y el 2026-11-09:
  ADR y documentación (wasichai/sgspe#185), capturas «antes» (#90), fundamentos (#92), componentes base (#96), shell (#97),
  inicio y tableros (#100), portafolio y Proyecto 360 (#102), grupos y relacionamiento (#104), compromisos y acuerdos
  (#106), riesgos y QCS (#108), territorio y «Lista + mapa» (#109), programas, documentos y tareas (#112), reportes, IA y
  secundarias (#113) y pulido con capturas «después» (#115). Los PR citados sin repositorio son de wasichai/sgspe-ui.
- Regla de la épica, cumplida: mejora visual y de UX; se conservan rutas, contratos, permisos, i18n, GIS, gráficos, IA y
  seguridad, y ninguna prueba se debilitó (las ajustadas verifican lo mismo con la anatomía nueva; cada PR las lista).
- `src/theme/tokens.css` es copia byte a byte de [`tokens.css`](../tokens.css) (lo vigila `themes.test.ts`); el puente
  `src/theme/wasichai.css` hace que `@wasichai/ui` y `/admin` lean `--sg-*`, y `src/theme/componentes.css` da a las
  primitivas de wasichai la forma de E (botones, avisos, campos, encabezados de tabla, pestañas).
- Todas las pantallas del inventario que existen en el portal quedaron migradas; las desviaciones del lienzo están en §4 y
  lo que falta, con su issue, en §8.

## 2. Inventario de componentes

Piezas propias del portal (`sgspe-ui/src/portal/`) que llevan la identidad, con los tokens que más usan (utilidades
`*-sg-*` o variables `--sg-*`) y los módulos que las importan (entre paréntesis, cuántos archivos). Las primitivas de
`@wasichai/ui` (Button, Input, Select, Dialog, Table, Tabs, Alert) toman la forma de E por `componentes.css` y no se
repiten aquí.

### 2.1 Shell y contexto

| Componente | Archivo | Para qué | Variantes | Tokens | Pantallas |
|---|---|---|---|---|---|
| `Cabecera` | `shell/Cabecera.tsx` | Cabecera institucional: marca, organización, búsqueda ⌘K, alcance, «Preguntar», campana y avatar | 64 / 52 compacta / 60 tableta / 56 teléfono; lema desde 1536 px; organización desde `xl` | `header`, `header-2`, `header-text`, `stripe`, `masthead` | Todas (shell) |
| `NavegacionPrincipal` | `shell/NavegacionPrincipal.tsx` | Navegación horizontal por bloques con desplegable (patrón «disclosure navigation») | Bloque con desplegable, bloque de una sección como enlace, Administración a la derecha | `surface`, `surface-hover`, `primary`, `primary-text`, `text-secondary` | Todas desde `lg` |
| `BarraInferior` | `shell/BarraInferior.tsx` | Cinco destinos del teléfono: Inicio · Proyectos · Registrar · Mapa · Tareas | Destino deshabilitado con su motivo; «Registrar» con las altas permitidas | `primary`, `primary-text`, `border-strong`, `text-muted` | Todas bajo `sm` |
| `SelectorDeDensidad`, `useDensidad` | `shell/densidad.tsx` | Densidad «Normal · Compacta» por persona (`sgspe.densidad`, `data-density`) | Grupo de radios en el menú de la cuenta y en «Más» | `text`, `primary` | Menú de la cuenta |
| `BarraDeContexto` | `contexto/BarraDeContexto.tsx` | Banda única: migas, «‹ Volver a …», sello de alcance (global, proyecto, territorio) | Global en `surface`; proyecto; territorio en `territory-soft`; en el Resumen, `surface` sin sello | `territory`, `territory-soft`, `territory-border`, `bg`, `border` | Todas |
| `ProyectoLayout` | `contexto/ProyectoLayout.tsx` | Navegación local del proyecto de 248 px con totales y «● N» | Columna (`lg`), pestañas de 48 px con «Más ▾» (tableta), botón «Sección del proyecto» (teléfono) | `primary-soft`, `primary-text`, `surface-hover`, `danger` | Todo `/projects/:id/*` (53 archivos) |
| `Rastro`, `EnlaceConRastro` | `contexto/Rastro.tsx` | «Volver a …» y el recorrido entero a pedido | Con un solo paso, solo «Volver» | `surface-raised`, `border-strong`, `overlay` | Fichas de todos los módulos (52) |
| `Migas`, `MigasGlobales`, `MigasDelProyecto` | `componentes/Migas.tsx` | Dónde vive el registro | Global o de proyecto | `text-secondary`, `primary-text` | Banda de contexto |
| `NoEncontrada` | `shell/NoEncontrada.tsx` | 404 como bloque de estado con salidas | — | `surface`, `surface-sunken`, `border-strong` | Rutas inexistentes, registros que no existen |
| `EstadoSinAcceso` | `componentes/EstadoSinAcceso.tsx` | 403 o sección no ofrecida: qué, por qué y a quién pedir | — | `surface-sunken`, `border-strong` | Guardas de sección |

### 2.2 Página, paneles y estados

| Componente | Archivo | Para qué | Variantes | Tokens | Pantallas |
|---|---|---|---|---|---|
| `ContenedorDePagina`, `ContenedorSiFalta` | `componentes/Pagina.tsx` | Márgenes 16/24/32 y 24 arriba, una sola vez aunque se aniden; reemplazó `MARGENES_DE_RESPALDO` (#115) | Ancho de 1440 o «proyecto» a todo el ancho | — | Todas (85 importaciones) |
| `EncabezadoDePagina`, `MasAcciones` | `componentes/Pagina.tsx` | Overline, `h1` 28/36, descripción, acciones; barra fija de acciones en el teléfono (M17b: primaria arriba a todo el ancho) | Con o sin overline, con «Más acciones» | `overline`, `h1`, `text-secondary`, `border` | Todas |
| `Seccion` | `componentes/Seccion.tsx` | Toda superficie con título | `fuerte` (borde 2 px `border-strong`), anidada sin caja, `aside` | `surface`, `border`, `border-strong`, `h3` | 55 importaciones |
| `CabeceraDeFicha` | `componentes/CabeceraDeFicha.tsx` | Antetítulo, título y chips de una ficha con la regla de la primaria | — | `caption`, `text-secondary` | Fichas (15) |
| `PanelDeDecision` | `componentes/PanelDeDecision.tsx` | Estado, siguiente acción, responsable y vencimiento | 4 columnas; 2 × 2 en vista previa y tableta; el chip se parte en su celda (#115) | `border-strong`, `surface`, `warning`, `text-muted` | Fichas de compromiso, grupo, interacción, riesgo, QCS, aporte, S31 (16) |
| `PanelDeTrazabilidad` | `componentes/PanelDeTrazabilidad.tsx` | «De dónde viene», «Qué generó», «Lo sustenta» | Tres columnas; apilado bajo 448 px de contenedor (#115) | `border`, `surface`, `text-secondary` | Fichas y vistas previas (12) |
| `RejillaDeKpi`, `KpiCard`, `CifraFallida` | `componentes/KpiCard.tsx` | Cifras en una caja con separadores, código, meta con medidor y hora de cálculo | 4 columnas, 5 (P360); tono `danger`/`warning` solo si el rótulo lo dice | `surface`, `border`, `text-muted`, `warning` | Inicio, revisión ejecutiva, P360, QCS, tableros (11) |
| `Cargando`, `ErrorDeCarga`, `Vacio`, `Consulta`, `Restringido`, `NoDisponible` | `componentes/Estados.tsx` | Esqueleto con estructura sin brillo, error con código y «Reintentar», vacío alineado a la izquierda con ícono (E18) | `titulo` propio en el error; `Restringido` y `Restringida`; vacío por filtros con conteo | `surface-sunken`, `text-muted`, `border` | 123 importaciones |
| `ResumenDeErrores` | `componentes/ResumenDeErrores.tsx` | Errores de un envío con enlaces a cada campo | — | `danger`, `surface` | `FormularioPorPasos`, `FormularioDeRegistro` |
| `RevisionDeRespuestas` | `componentes/RevisionDeRespuestas.tsx` | «Revise sus respuestas» con «Cambiar» por paso | — | `border`, `text-secondary`, `primary-text` | Alta de QCS, registro de interacción (los demás asistentes la arman igual) |
| `Pasos`, `FormularioPorPasos` | `componentes/Pasos.tsx`, `FormularioPorPasos.tsx` | Segmentos de E con borde superior de 4 px | Hecho, actual, pendiente | `progress`, `primary`, `border` | Nuevo proyecto, compromiso, interacción, QCS, importación |
| `CaminoDelFlujo` | `componentes/CaminoDelFlujo.tsx` | Camino feliz de un workflow con números en círculo | Plegado en el panel de decisión; abierto en la fila de QCS | `progress`, `on-primary`, `border` | Fichas, cola de QCS |
| `AvisoDeExito`, `AvisoDeConflicto`, `AvisoDeBorrador` | `componentes/` | Éxito con foco y `role=status`; conflicto de versión con «Recargar»; borrador local | — | Alertas de `componentes.css` | 34, 7 y 5 importaciones |

### 2.3 Listas y filtros

| Componente | Archivo | Para qué | Variantes | Tokens | Pantallas |
|---|---|---|---|---|---|
| `TablaPriorizada` | `componentes/TablaPriorizada.tsx` | Toda lista ancha: columna de título, números a la derecha con «—», fila elegida, grupos, nota al pie, selección | `grupos`, `notaAlPie`, `detalleDe` (fila expandida), `seleccion` (#115), `enDosLineas`, prioridad de columnas | `surface`, `surface-hover`, `primary-soft`, `primary`, `border-strong`, `text-secondary` | Compromisos, acuerdos, QCS, riesgos, alertas, grupos, interacciones, programas, aportes, proyectos, entregables, tareas |
| `TablaDeRegistros`, `Paginador`, `VistasGuardadas` | `componentes/Lista.tsx` | Tabla genérica y paginador de 32 px con «Mostrando…» | — | `primary`, `primary-text`, `border` | Listas secundarias (19) |
| `ListaEnTarjetas` | `componentes/ListaEnTarjetas.tsx` | Las listas en tarjetas bajo `sm` | — | `surface`, `border`, `caption` | 14 importaciones |
| `ListaPriorizada` | `componentes/ListaPriorizada.tsx` | Lo urgente con su motivo | `variante="filas"` (inicio: ícono, chips, un verbo por fila) | `danger`, `primary-text`, `text-secondary` | Inicio, Resumen del proyecto |
| `BarraDeFiltros`, `CajaDeBusqueda` | `componentes/BarraDeFiltros.tsx` | Una fila: búsqueda de 36 px, selector de vista, chips, «Limpiar filtros» | Frecuentes en «Filtros (N)» bajo `md` | `surface`, `border-control`, `primary-text` | 16 importaciones |
| `SelectorDeVista`, `SelectorDePagina` | `componentes/SelectorDeVista.tsx` | Vistas segmentadas con conteo (`?view=`) y páginas con ruta propia | Hasta cuatro y «Más vistas» | `primary`, `primary-soft`, `surface-hover` | Compromisos, QCS, riesgos, grupos, interacciones, programas, tareas, aprobaciones, avisos, acuerdos |
| `FiltroDeLista`, `SelectorDeGrupo` | `componentes/FiltroDeLista.tsx`, `FiltroDeGrupo.tsx` | Chip «Estado: Todos ▾»; aplicado con borde de 2 px `primary` | — | `primary-soft`, `border-strong`, `surface` | 11 y 8 importaciones |
| `BarraDeSeleccion` | `componentes/BarraDeSeleccion.tsx` | Acciones sobre una selección múltiple | — | `primary-soft`, `primary-text` | Grupos, tareas |
| `VistaPrevia`, `useCuerpoDeVistaPrevia`, `AccionesDeVistaPrevia` | `componentes/VistaPrevia.tsx`, `AccionesDeVistaPrevia.tsx` | `?peek=`: panel acoplado de 420 px desde `xl`, cajón por debajo; cuerpo propio por objeto | Genérica; propia de compromiso, grupo, interacción, proyecto, programa y aporte | `surface`, `border`, `overline`, `text-secondary` | 17 importaciones |
| `ListaConMapa` | `mapa/ListaConMapa.tsx` | «Lista + mapa»: listbox sincronizado con el mapa (40 %, mín. 420 px) por `?selected=` | Ubicación por capa de gestión (LYR-19, LYR-17, LYR-20, LYR-21); `objetoDe` para la cola mixta de QCS | `surface`, `border`, `border-subtle`, `primary` | Compromisos, grupos, riesgos, QCS |

### 2.4 Estados, niveles y datos

| Componente | Archivo | Para qué | Variantes | Tokens | Pantallas |
|---|---|---|---|---|---|
| `EstadoChip`, `ChipDeEstado` | `componentes/EstadoChip.tsx` | Estado de workflow: chip de 26 px con borde de su tinta | Por tono (§4.1 de design-system) | Tonos semánticos `*-soft` y su tinta | 45 importaciones |
| `SituacionDePlazo`, `useDiasDelPlazo` | `componentes/SituacionDePlazo.tsx`, `DiasDelPlazo.tsx` | Situación derivada sin caja: reloj, MAYÚSCULAS 800, subrayado punteado, días | Con `detalle` («VENCIDO · 31 DÍAS», hábiles en el acuse) | `danger`, `warning`, `success`, `text-muted` | Compromisos, QCS, acuerdos, tareas, riesgos |
| `Nivel`, `Postura` | `componentes/Nivel.tsx` | Nivel con 4 barras (riesgo, severidad, prioridad) y postura con círculo | `escala="prioridad"` en `primary`; MEDIO ámbar; CRITICO sólido | `risk-*`, `level-medium-ink`, `level-high-ink`, `on-critical`, `stance-*` | Riesgos, alertas, QCS, grupos, tareas |
| `Etiqueta` | `componentes/Etiqueta.tsx` | Etiquetas de 24 px (T1/T2, niveles de divulgación, TERRITORIO) | — | `border-strong`, `surface`, `mono` | Reportes, entregables |
| `Insignia` | `componentes/Insignia.tsx` | Conteos en la campana y la navegación | Sobre `danger` con `on-danger` | `danger`, `on-danger` | Shell |
| `BarraDePuntaje`, `BarraPorSexo` | `componentes/Puntaje.tsx` | Puntajes 1–5 y asistentes por sexo (dos tramos) | — | `primary`, `data-1`, `data-2`, `surface-sunken` | Grupos, relacionamiento |
| `MedidorDeMonto` | `componentes/MedidorDeMonto.tsx` | Un medidor por moneda frente a su tope; sin ejecutado, «—» | — | `primary`, `surface-sunken`, `border-strong` | Programa, revisión ejecutiva |
| `Monto` | `componentes/Monto.tsx` | Montos con su moneda, nunca sumados | — | — | Compromisos, programas, aportes |
| `NotaDeConfidenciales` | `componentes/NotaDeConfidenciales.tsx` | Nota con candado de lo que no se cuenta | — | `caption`, `text-muted` | QCS, P360, tableros, mapa |
| `HoraDeCalculo`, `NotaAlPie`, `SerieConMeta`, `Barras` | `tableros/comunes.tsx` | Hora de cálculo (préstamo de D), notas al pie (de C), gráficos con meta | — | `body-sm`, `caption`, `text-secondary` | Tableros, inicio, P360 (23) |
| `ListaDeLoCritico` | `tableros/LoCritico.tsx` | Lo crítico como lista ordenada por gravedad | — | `border-subtle`, `mono` | Inicio, revisión ejecutiva |
| `Grafico` | `graficos/Grafico.tsx` | ECharts con tema `--sg-*`, valor rotulado, meta punteada, «Ver datos» como botón | Barras, líneas, apiladas | `grid`, `border-strong`, `text`, `font-sans` | Tableros, riesgos, grupos |
| `BarraDePosturas`, `MiniMatriz` | `graficos/` | Postura apilada con cifras; matriz interés × poder | — | `stance-*`, `data-1`, `data-2` | P360, grupos |
| `MatrizDeRiesgo` | `riesgos/` | Matriz 5 × 5 con zonas, ceros atenuados, celda crítica | En el registro, el tablero y P360 | `zone-1`…`zone-4`, `risk-critical` | Riesgos, P360 |

### 2.5 Mapa

| Componente | Archivo | Para qué | Variantes | Tokens | Pantallas |
|---|---|---|---|---|---|
| `Mapa`, `MapaImpl` | `mapa/Mapa.tsx` | MapLibre con controles de 36 px, norte de 40 px, escala y atribución con tokens | Panel derecho abierto corre los controles (`--sg-mapa-derecha`) | `surface`, `border-strong`, `mapa-izquierda`, `mapa-derecha` | Territorio, mapa del proyecto, áreas, lista + mapa |
| `ControlDeCapas` | `mapa/ControlDeCapas.tsx` | Panel de 300 px con las capas en cuatro grupos, muestra 22 × 10, código y candado | Plegable (`?hide=layers`) | `surface`, `overlay`, `overline`, `mono` | Territorio, mapa del proyecto |
| `Leyenda`, `MuestraDeCapa` | `mapa/Leyenda.tsx` | Leyenda y muestra de cada capa | — | `lyr-*` | Mapas |
| `TablaDeCalor`, `SelectorDeCalor` | `mapa/Calor.tsx` | Calor de QCS por lugar con su tabla | — | `heat-1`…`heat-4` | Territorio |
| `estilos.ts`, `colores.ts` | `mapa/` | Estilos de capa por `--sg-lyr-*` y escalas de datos; `colorPor` (LYR-21 por nivel) y `conPostura` (anillo de LYR-17) | Se repintan al cambiar de tema | `lyr-*`, `heat-*`, `risk-*`, `stance-*` | Mapas |

## 3. Color y tipografía, como quedaron

La especificación es [`design-system.md`](../design-system.md) §3 con [`tokens.css`](../tokens.css), y el portal la usa sin
cambios: `tokens.css` es la misma hoja, Public Sans (400 a 800 y la cursiva 400) e IBM Plex Mono (400 a 600) se sirven
desde el mismo origen con `@fontsource` (latín y latín extendido; `font-src 'self'`), las cifras son tabulares y el foco es
el de §3.6, en la capa base, sin contornos sueltos en los componentes (`themes.test.ts` lo vigila). El contraste de los
pares de §10 se mide en los dos temas (`contraste.py` aquí y `themes.test.ts` en el portal).

Desvíos de la implementación:

| Tema | Especificación | Implementado | Motivo |
|---|---|---|---|
| Velo de los diálogos | — (`tokens.css` no tiene uno) | `--overlay` sigue siendo el de wasichai | No hay token equivalente (#92) |
| Insignias sobre `danger` | `on-critical` | `on-danger` | `on-critical` daba blanco sobre rosado en oscuro (#92) |
| LYR-09 y LYR-13 | Mismo `lyr-project` | Mismo color, distinguidas por la forma (punteado `3 3` sin relleno frente a relleno al 12 %) | Como pide §3.3; LYR-09, LYR-12 y LYR-18 ya no comparten color (#92) |
| Capas de `paleta.json` (LYR-01 a LYR-08 y las que pinta GeoServer) | Paleta cartográfica de §3.3 (LYR-02 en `lyr-place`) | Colores de `model/styles/palette.json`: LYR-02 en rojo `#b8452e` | La paleta de los mapas de informe se alinea aparte (§3.3; wasichai/sgspe-ui#125) |
| Rótulos y símbolos del mapa | Halo `map-ground` de 4 px; conteos en LYR-17 y LYR-19; rombo con «!» en LYR-21 | Sin texto ni símbolos: LYR-21 con el color de su nivel y LYR-17 con un anillo de la postura que más pesa | MapLibre necesita glifos y sprite del propio origen (wasichai/sgspe-ui#120) |
| Campos de la cabecera en oscuro | Búsqueda en `surface-sunken` con borde `border-control` | Igual, también el proyecto elegido (`[data-campo-de-cabecera]`) | #115 |
| Campo de 48 px | Login y teléfono | Utilidad `.h-12` en `componentes.css` | #100 |
| Avatar en oscuro | Avatar blanco de 36 px | Borde de poco contraste con la cabecera | wasichai/sgspe-ui#119 |

## 4. Decisiones distintas del lienzo

Las que quedaron así y ya se llevaron a [`design-system.md`](../design-system.md):

- **Banda del proyecto solo en el Resumen** (#102): las demás pestañas del proyecto llevan la banda de contexto normal con
  el sello; en el Resumen, la banda de contexto pasa a `surface` sin borde y no repite el sello.
- **QCS con fila expandida** (#108), no con vista previa acoplada: la cola mezcla tres workflows y el tablero S11 lo muestra
  en la fila. Espacio abre la fila; Enter, la ficha. La vista previa sigue en Compromisos, Grupos, Relacionamiento,
  Riesgos, Proyectos, Programas y Aportes.
- **«Ver como lista»** en el mapa (#109), no «Ver como tabla»: abre y enfoca la lista de lugares, la alternativa accesible.
- **Periodo del mapa bajo el mapa** (#109, en una línea desde #115), no en la banda de territorio: el lienzo gana alto útil.
- **«Lista + mapa» por capa de gestión** (#109): el lugar sale de la capa que ya ubica el objeto (LYR-19 compromisos,
  LYR-17 grupos, LYR-20 QCS, LYR-21 riesgos), nunca de un punto propio del grupo. En Riesgos, «Vigentes en el mapa» va bajo
  la matriz; en QCS, «Atención en el mapa».
- **Densidad compacta de botones a 36 px** (`--sg-control-compact`), elegida solo en el menú de la cuenta.
- **Grupos de Compromisos por página** (#106): con el orden por vencimiento se agrupan las filas de la página con la nota
  «Agrupados por situación en esta página», sin «Mostrar N más al día» (wasichai/sgspe-ui#124). El orden se elige en el
  control «Orden» y no en los encabezados (el monto mezcla monedas).
- **Riesgos**: las páginas del módulo son una fila de pestañas y las vistas del registro, el único control segmentado; las
  reglas de alerta son la acción «Ver reglas de alerta» (#108).
- **Entregable** (#113): «Adoptar en la sección», «Adoptar y editar» y «Descartar» en el bloque de IA; la lista de EXTERNO
  marca ✓/✕ solo lo que el portal sabe y deja como regla lo que juzga una persona (§8).
- **Ingreso** (#100): sin «Olvidé mi contraseña» ni enlaces a privacidad y accesibilidad, porque esas páginas no existen.

## 5. Migración por pantalla

✓ = migrada a la anatomía de E; parcial = migrada con un faltante anotado. Las rutas de proyecto se abren también desde
las listas globales.

| Pantalla | Ruta | Fase / PR | Estado | Notas |
|---|---|---|---|---|
| S01 Login (tablero S01) | `/login` | 5 · #100 | parcial | Sin «Olvidé mi contraseña» ni enlaces a privacidad y accesibilidad (no existen); versión al pie |
| S02 Inicio (tablero S02, M17) | `/` | 5 · #100 | ✓ | Saludo por la hora de Lima, filas de trabajo, lo crítico con hora de cálculo, Mis proyectos como tabla (a 390 sin desplazamiento, #115) |
| S03 Búsqueda global | ⌘K | 3 · #97 | ✓ | Campo ancho en la cabecera (`BotonBuscar`) |
| S04 Avisos | `/notifications` | 12 · #113 | ✓ | Un panel con borde de 4 px por tipo |
| S05 Asistente de IA | panel | 12 · #113 | ✓ | «Fuentes» en el color de IA; pregunta con borde |
| S06 Lista de proyectos (tablero S04) | `/projects` | 6 · #102 | parcial | Tres columnas de cifras y vista previa bajo la tabla desde `xl`; sin «Tabla · Mapa» ni «Vista compacta» (#123) |
| S07 Crear proyecto | `/projects/new` | 6 · #102 | ✓ | «Revise sus respuestas» con «Cambiar» |
| S08 Proyecto 360 · Resumen (tableros S05, T17) | `/projects/:id/overview` | 6 · #102 | parcial | Banda del proyecto, cinco cifras, paisaje; columna izquierda corta a 1440 (#118) |
| S09 Proyecto 360 · Mapa | `/projects/:id/map` | 6 · #102 | ✓ | Contenedor y encabezado comunes |
| S10 Área de influencia | `/projects/:id/areas/:areaId` | 6 · #102 | ✓ | Versión dibujada con el estilo de LYR-13 |
| S11 Equipo y parámetros | `/projects/:id/settings` | 6 · #102 | ✓ | |
| S12 Mapa territorial (tableros S09, D14) | `/territory` | 9 · #109 | parcial | Paneles de 300 y 380 px, controles de E; sin rótulos ni símbolos (#120); capas de referencia con `paleta.json` (#125) |
| S13 Ficha de comunidad | `/territory/communities/:id` | 9 · #109 | ✓ | |
| S14 Ficha de localidad | `/territory/localities/:id` | 9 · #109 | ✓ | |
| S15 Importación geoespacial | `/territory/imports/:id` | 9 · #109 | ✓ | Pestañas de §5.3 y filtros en chip |
| S16 Lista de grupos de interés (tablero S06) | `/stakeholders` | 7 · #104 | ✓ | Cuatro columnas esenciales, vista previa con puntajes, «Lista + mapa» (#109) |
| S17 Ficha de grupo de interés | `/projects/:id/stakeholders/:sid` | 7 · #104, #115 | ✓ | Panel de decisión, evaluación con barras, trazabilidad |
| S18 Ficha de actor | `/actors/:actorId` | 7 · #104 | ✓ | |
| S19 Matriz interés × poder | `/projects/:id/stakeholders/matrix` | 7 · #104 | ✓ | Damero y grupos por cuadrante |
| S20 Nueva evaluación | cajón | 7 · #104 | ✓ | Avance «N de 5 temas» |
| S21 Calendario de relacionamiento | `/engagements/calendar` | 7 · #104 | ✓ | Tira semanal con hoy y festividades rayadas |
| S22 Lista de interacciones (tablero S07) | `/engagements` | 7 · #104 | ✓ | Asistentes por sexo, vista previa |
| S23 Ficha de interacción | `/projects/:id/engagements/:eid` | 7 · #104, #115 | ✓ | Barra por sexo |
| S24 Registrar interacción | `/projects/:id/engagements/new` | 7 · #104 | ✓ | `RevisionDeRespuestas` |
| S25 Editor de acta | `.../minutes` | 7 · #104 | ✓ | |
| S26 Visita de campo | `/projects/:id/field-visits/:vid` | 7 · #104 | ✓ | |
| S27 Tablero de compromisos | `/commitments/dashboard` | 5 · #100 | ✓ | Revisado en #106 sin cambios |
| S28 Lista de compromisos (tableros S08, D15, E18) | `/commitments` | 8 · #106 | parcial | Grupos por página sin conteo del servidor (#124); selector cortado a 390 (#116) |
| S29 Ficha de compromiso (M17b) | `/projects/:id/commitments/:cid` | 8 · #106, #115 | ✓ | Motivo del bloqueo; barra móvil de M17b (#115) |
| S30 Crear compromiso | `.../commitments/new` | 8 · #106 | ✓ | Tope de la campaña en la revisión |
| S31 Aprobación de compromiso | `/tasks/approvals/:taskId` | 11 · #112, #115 | ✓ | Un solo `h1`, panel de decisión |
| S32 Entrega y verificación | cajón | 8 · #106 | ✓ | |
| S33 Acuerdo o convenio | `/projects/:id/agreements/:aid` | 8 · #106 | ✓ | Lista con «Estado y vigencia» |
| S34 Tablero de QCS | `/attention/dashboard` | 5 · #100 | ✓ | |
| S35 Lista unificada de QCS (tablero S11) | `/attention` | 10 · #108 | ✓ | Fila expandida con el camino; «Atención en el mapa» (#109); selector cortado a 390 (#116) |
| S36 Ficha de queja | `/projects/:id/grievances/:id` | 10 · #108 | ✓ | Severidad con `Nivel` |
| S37 Registrar QCS | `/projects/:id/attention/new` | 10 · #108 | ✓ | |
| S38–S39 Matriz y registro de riesgos (tableros S10, D16) | `/risks/register` | 10 · #108 | ✓ | Matriz 5 × 5, tendencia, «Vigentes en el mapa» (#109) |
| S40 Ficha de riesgo | `/projects/:id/risks/:rid` | 10 · #108 | ✓ | Historial como gráfico con «Ver datos» |
| S41 Centro de alerta temprana | `/risks/warnings` | 10 · #108 | ✓ | |
| S42 Programas sociales | `/programs` | 11 · #112, #115 | parcial | Vistas previas propias (#115); tabla de presupuesto se desplaza a 390 (#117) |
| S43 Ficha de programa | `/projects/:id/programs/:pid` | 11 · #112 | ✓ | Medidor por moneda |
| S44 Aporte social | `/projects/:id/contributions/:cid` | 11 · #112 | ✓ | «Entrega congelada» |
| S45 Biblioteca de documentos | `/documents` | 11 · #112 | ✓ | |
| S46 Ficha de evidencia | `/projects/:id/documents/:did` | 11 · #112 | ✓ | Clasificación con candado |
| S47 Mis tareas | `/tasks` | 11 · #112, #115 | ✓ | `TablaPriorizada` con selección (#115) |
| S48 Aprobaciones y escalamientos | `/tasks/approvals` | 11 · #112, #115 | ✓ | Tarjeta del aporte con su propósito |
| S49 Reportes (tablero S12) | `/reports` | 12 · #113 | ✓ | Entregables en `TablaPriorizada` con etiquetas |
| S50 Entregable por secciones | `/reports/deliverables/:id` | 12 · #113 | parcial | Lista de EXTERNO con lo calculable (§8) |
| S51 Revisión ejecutiva (tableros S03, D13) | `/dashboards/executive` | 5 · #100 | parcial | Sin «Descargar PDF» ni vencidos por tramo (#122) |
| S52 Bandeja de revisión de IA | `/projects/:id/ai-review` | 12 · #113 | ✓ | |
| S53 Administración | `/administration` y `/admin` | 12 · #113 | parcial | `/admin` toma E por el puente; apretado a 390 hasta subir `@wasichai` (#121) |
| S54–S55 Campo (móvil) | `/tasks`, `/field-visits` | 3 · #97; 7 · #104 | ✓ | Barra inferior, campos de 48 px |
| S56 Gestionar vistas | diálogo en cada lista | 4 · #96 | ✓ | Las vistas van en el `SelectorDeVista`; Mis tareas y Aprobaciones, también desde #112 |
| S57 Fichas de consulta y solicitud | `/projects/:id/inquiries/:id`, `.../requests/:id` | 10 · #108 | ✓ | |
| S58–S61 Menciones, señales, reglas e impactos | `/projects/:id/risks/*`, `/risks/rules` | 10 · #108 | ✓ | Pestañas de Riesgos, encabezado común |
| S62 Tablero de riesgos | `/risks` | 10 · #108 | ✓ | La misma `MatrizDeRiesgo` |
| S63 Calidad de datos | `/territory/quality` | 9 · #109 | ✓ | |
| S64 Plantillas de entregables | `/reports/templates` | 12 · #113 | ✓ | |
| Incidentes | `/projects/:id/incidents` | 10 · #108 | ✓ | En las pestañas de Riesgos |
| Reporte unificado de QCS | `/projects/:id/attention/report` | 10 · #108 | ✓ | Encabezado y tablas de E |
| Tableros de módulo, monitoreo, catálogo y detalle de indicador | `/dashboards/monitoring`, `/dashboards/indicators[/:code]`, `…/dashboard` de cada módulo | 5 · #100 | ✓ | Hora de cálculo y notas al pie |
| Aportes | `/programs` (vista de aportes) | 11 · #112 | ✓ | «Aporte» como columna de título |
| Duplicado de IA | ruta de la bandeja | 12 · #113 | ✓ | Razones en el bloque de IA |
| 404, sin acceso y `/r/:object/:id` | — | 12 · #113 | ✓ | Dentro de `ContenedorDePagina` |

## 6. Antes y después

Las 19 comparaciones lado a lado (izquierda antes, en el commit `b952cd5` de `sgspe-ui` con el escenario de capturas
actual; derecha después, en `d0f818c`), en JPEG reducido a 2000 px de ancho. Los datos son los del escenario ficticio de
`e2e/capturas/`. Las 347 capturas sueltas (173 antes y 174 después) no se versionan: se regeneran con `yarn capturas`.

| Pantalla | Comparación |
|---|---|
| Login, 1440 claro | [`login-1440-light-es`](comparacion/login-1440-light-es-comparacion.jpg) |
| Inicio, 1440 claro | [`inicio-1440-light-es`](comparacion/inicio-1440-light-es-comparacion.jpg) |
| Inicio, 1440 oscuro | [`inicio-1440-dark-es`](comparacion/inicio-1440-dark-es-comparacion.jpg) |
| Inicio, 390 | [`inicio-390-light-es`](comparacion/inicio-390-light-es-comparacion.jpg) |
| Revisión ejecutiva | [`revision-ejecutiva-1440-light-es`](comparacion/revision-ejecutiva-1440-light-es-comparacion.jpg) |
| Proyectos | [`proyectos-1440-light-es`](comparacion/proyectos-1440-light-es-comparacion.jpg) |
| Proyecto 360, Resumen | [`p360-resumen-1440-light-es`](comparacion/p360-resumen-1440-light-es-comparacion.jpg) |
| Grupos de interés | [`grupos-1440-light-es`](comparacion/grupos-1440-light-es-comparacion.jpg) |
| Relacionamiento | [`relacionamiento-1440-light-es`](comparacion/relacionamiento-1440-light-es-comparacion.jpg) |
| Compromisos, 1440 claro | [`compromisos-1440-light-es`](comparacion/compromisos-1440-light-es-comparacion.jpg) |
| Compromisos, 1440 oscuro | [`compromisos-1440-dark-es`](comparacion/compromisos-1440-dark-es-comparacion.jpg) |
| Compromisos, 390 | [`compromisos-390-light-es`](comparacion/compromisos-390-light-es-comparacion.jpg) |
| Territorio | [`territorio-1440-light-es`](comparacion/territorio-1440-light-es-comparacion.jpg) |
| Riesgos | [`riesgos-1440-light-es`](comparacion/riesgos-1440-light-es-comparacion.jpg) |
| QCS | [`qcs-1440-light-es`](comparacion/qcs-1440-light-es-comparacion.jpg) |
| Programas | [`programas-1440-light-es`](comparacion/programas-1440-light-es-comparacion.jpg) |
| Reportes, entregables | [`entregables-1440-light-es`](comparacion/entregables-1440-light-es-comparacion.jpg) |
| Entregable | [`entregable-1440-light-es`](comparacion/entregable-1440-light-es-comparacion.jpg) |
| Administración | [`administracion-1440-light-es`](comparacion/administracion-1440-light-es-comparacion.jpg) |

## 7. Pruebas

- **Cifras finales** (#115, local): Vitest con 230 archivos y **2855 pruebas**, y **97 pruebas e2e** de Playwright
  (proyecto `chromium`), más format, lint, typecheck y build, todo en verde. Antes de la épica, `yarn e2e` corría 93 casos.
- **CI por PR**: los trece PR de wasichai/sgspe-ui (#90 a #115) se fusionaron con todos sus checks en verde; en #92 y #109
  hay una ejecución cancelada, reemplazada por la del último commit.
- **Pruebas que vigilan la identidad**: `themes.test.ts` (copia de `tokens.css`, puente, densidad, foco, contraste de los
  dos temas), `paleta.test.ts` (capas LYR-01 a LYR-24 con tokens), `superficies.test.ts` (ninguna caja, `<h1>` ni
  `max-w-[…]` escritos a mano fuera de los componentes) y las e2e del shell (1024, 800 y 390) y del mapa (controles de
  36 px en los dos temas).
- **Capturas**: el proyecto de Playwright `capturas` (#90) saca fotos a página completa sobre la API simulada de
  `e2e/api.ts` y el escenario de `e2e/capturas/`, con la hora fija, el tema y el idioma fijados antes de cargar y sin pausas
  fijas. Se corre así:

  ```sh
  SGSPE_E2E_PORT=5192 yarn capturas                    # todas, en capturas/ (ignorada por git)
  SGSPE_CAPTURAS=/ruta/despues SGSPE_E2E_PORT=5192 yarn capturas
  yarn capturas --list                                 # lo que corre la CI: arma los casos sin navegador
  ```

  El workflow `capturas.yml` también las genera a mano (`workflow_dispatch`) o en un PR con la etiqueta `capturas`, y las
  sube como artefacto. Los nombres son `<pantalla>-<ancho>-<tema>-<idioma>.png`.

## 8. Límites conocidos

| Límite | Detalle | Issue |
|---|---|---|
| Rótulos y símbolos del mapa | Sin rótulos con halo, sin conteos en LYR-17 y LYR-19 ni rombo con «!» en LYR-21: MapLibre necesita glifos y sprite servidos por el propio origen | wasichai/sgspe-ui#120 |
| Centros poblados en rojo | LYR-02 y las demás capas de `paleta.json` usan la paleta de los SLD (`#b8452e`), no la cartográfica de §3.3 | wasichai/sgspe-ui#125 |
| Revisión ejecutiva | «Descargar PDF» y la barra de vencidos por tramo (1–7, 8–30, más de 30 días) no se dibujan: no hay dato ni PDF | wasichai/sgspe-ui#122 |
| Lista de EXTERNO | Solo se marcan ✓/✕ las secciones revisadas, las cambiadas tras el borrador, el borrador listo, los avisos de datos personales y los mapas fallidos. La narrativa, las fotos, los mapas y los anexos no son calculables: van como regla que se confirma al emitir | — (por diseño) |
| `/admin` a 390 px | Barra lateral fija de 240 px y contenido apretado hasta subir `@wasichai/*` de 1.1.0 a una versión con el cajón (≥ 1.3.1) y su trampa de foco (wasichai/wasichai-ui#180, en 1.8.2) | wasichai/sgspe-ui#121 |
| Selector de vistas a 390 px | Se desplaza y corta la última vista en Compromisos, QCS, Programas y Proyectos | wasichai/sgspe-ui#116 |
| «Presupuesto de programas» a 390 px | La tabla se desplaza dentro de su caja | wasichai/sgspe-ui#117 |
| Resumen del proyecto a 1440 px | Columna izquierda corta con mucho blanco | wasichai/sgspe-ui#118 |
| Avatar en oscuro | Borde de poco contraste con la cabecera | wasichai/sgspe-ui#119 |
| Portafolio | Sin «Tabla · Mapa» ni casilla «Vista compacta» | wasichai/sgspe-ui#123 |
| Grupos de Compromisos | Sin total por situación del servidor ni «Mostrar N más al día» | wasichai/sgspe-ui#124 |
| Vínculos sueltos | «Volver a…» y «Abrir la ficha completa» siguen subrayados | — (lo pide design-system) |
| Compromisos, vista previa | «Adjuntar evidencia» y «Registrar avance» siguen en las pestañas de la ficha; las secundarias son transiciones reales | — (decisión de #106) |
| Ingreso | Sin «Olvidé mi contraseña» ni enlaces a privacidad y accesibilidad | — (las páginas no existen) |

## 9. Siguientes mejoras recomendadas

1. Cerrar los cuatro del barrido de #115 (wasichai/sgspe-ui#116 a #119): son pequeños y afectan a la lectura diaria,
   sobre todo en el teléfono.
2. Subir `@wasichai/*` a 1.8.2 en cuanto se publique (#121): trae el cajón de `/admin` y siete versiones de correcciones;
   conviene hacerlo antes de que la distancia crezca.
3. Glifos y sprite propios para el mapa (#120) y la paleta de los SLD alineada con §3.3 (#125): con eso el mapa del portal
   y los mapas de informe hablan el mismo lenguaje.
4. Decidir el PDF y los tramos de la revisión ejecutiva (#122) y el conteo por situación de Compromisos (#124): piden dato
   del servidor, así que empiezan en sgspe.
5. Sumar a `yarn capturas` las pantallas que hoy no tienen foto (tableros de módulo a 390, Programas con sus cifras a 390)
   y correrlo en cada PR visual con la etiqueta `capturas`.
