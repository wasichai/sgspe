# ADR-0032: Identidad visual «Civic Modern» con densidad compacta por persona

## Estado

Aceptado · 2026-11-09, por decisión del usuario del 2026-11-08 sobre el lienzo de la dirección E (ASM-149). Es la
primera decisión de identidad visual que se registra como ADR: reemplaza la dirección «Pino y piedra» (ASM-077), la
paleta de marca de ASM-082 y los tres temas propios de ASM-086, y deja sin objeto UPS-19. No reemplaza a ningún ADR.
Es el paso 0 de la épica wasichai/sgspe-ui#88 (issue wasichai/sgspe#184).

## Contexto

- **El informe del estado actual del portal** (2026-11-08) revisó la interfaz implementada tras el rediseño del
  2026-10-20 y sus tres pasadas, y registró 16 hallazgos (H-01 a H-16). Los que mueven esta decisión:
  - la tabla de Compromisos no cabe a 1440 px, sus filas no tienen una altura fija y los enlaces subrayados en cada
    celda cansan la lectura (H-01, H-05);
  - el nivel ordinal no tiene una escala coherente y la situación derivada se confunde con el estado del flujo
    (H-02, H-03, H-09);
  - el oscuro es una inversión del claro, sin datos, postura, nivel ni mapa propios (H-04);
  - hay varias capas de orientación (barra lateral, barra de contexto, pestañas) y el alcance del proyecto no es
    inequívoco (H-06);
  - los filtros ocupan más de una línea y hay más de un selector de vista por cabecera (H-07);
  - el mapa no manda, sus capas no se distinguen y no tiene un respaldo digno sin mapa base (H-08);
  - los KPI no muestran meta ni tendencia, y los estados de carga, vacío, error y éxito no son uniformes
    (H-10, H-11);
  - los tokens `--sg-*` no cubren el oscuro de los datos ni las capas del mapa (H-12 a H-15).
- **Dos incoherencias documentales** que el informe también registra: `design-system.md` decía que el orden de una
  lista no viaja en la URL, contra DD-37; y `dashboards.md` §9.4 pedía rellenos con patrón en los gráficos, contra
  DD-15.
- **Los temas.** ASM-086 sumó tres temas propios (`sgspe-pino`, `sgspe-cielo`, `sgspe-noche`) sobre el sistema de temas
  de wasichai [WAS-ADR-034, WAS-ADR-035]. Como «Según el sistema» de `@wasichai/core` resuelve siempre a `light` o
  `dark` (UPS-19), quien no elegía un tema veía los de la plataforma, no los de SGSPE. `themes.css` quedó en los
  documentos y el portal nunca lo importó: `sgspe-ui/src/theme/wasichai.css` apunta los 28 tokens de wasichai a los
  `--sg-*`, en `light` y en `dark`.
- **El encargo.** Se prepararon cinco direcciones con el mismo contenido (dos tableros de identidad, 12 pantallas,
  cuatro en el tema contrario, tableta, móvil y estados) y un juego de tokens `--sg-*` que pasa `contraste.py`, y un
  comparativo con una matriz de nueve criterios.

## Opciones consideradas

### A — Institutional Intelligence

Registro oficial: barra lateral marina de 248 px con cuatro bloques, banda de proyecto marina con una fila de
pestañas y banda ocre de territorio; marino #13233F, abeto #1F5A43 para la acción, Plex Sans con Plex Serif en
títulos y cifras.

- **A favor:** la recomendación del comparativo. Ningún criterio por debajo de 4; lidera en uso diario,
  implementación y mantenibilidad; conserva el modelo de navegación de hoy.
- **En contra:** su fuerza es la ejecución y no resuelve la lectura de quien usa el sistema de vez en cuando
  (puntúa 4 en personas con distinta experiencia técnica y en accesibilidad, frente a 5 de E). La barra lateral fija
  de 248 px y los rótulos de bloque en dos líneas siguen costando ancho en toda pantalla, también fuera de un
  proyecto. La serif suma una segunda familia.

### B — Geospatial Intelligence

El territorio como hilo: riel de 56 px con un panel contextual por módulo, selector de alcance con color, vista
lista + mapa, paleta cartográfica separada de la operativa.

- **A favor:** la más diferenciadora para un sistema de gestión social; la mejor en territorio y mapa.
- **En contra:** la implementación más cara (2 de 5) y la mantenibilidad media; depende de que todo registro tenga
  buena geometría, o el mapa queda vacío; el panel contextual cambia por módulo y hay que aprenderlo.

### C — Contemporary Editorial

Composición editorial con filetes en vez de tarjetas, navegación superior de dos líneas, Source Serif 4 en títulos.

- **A favor:** la más distintiva; tablas «de libro», notas al pie y anotación directa en los gráficos.
- **En contra:** la navegación superior de dos líneas lucha con 13 secciones más las pestañas del proyecto; la serif
  en tablas y fichas resta eficiencia; agradable para leer, más lenta para operar (3 en idoneidad y en mapa).

### D — Executive Operations Center

Centro de operaciones oscuro por defecto: riel de 52 px, barra de comandos con el reloj de Lima y la hora de
cálculo, franja global de pendientes, vista previa lateral, filas de 32 px.

- **A favor:** la mejor en monitoreo ejecutivo y en oscuro; máxima densidad bien organizada.
- **En contra:** pensada primero para el oscuro, que no encaja con la oficina y el campo a pleno día; el riel de
  íconos pide memorizar; la densidad excluye a quien entra de vez en cuando (2 en personas con distinta experiencia
  técnica); la franja global necesita conteos nuevos del backend; el móvil es solo una regla de apilado.

### E — Civic Modern (elegida)

Servicio digital público: cabecera azul institucional con franja teal, navegación horizontal por bloques con
desplegables, una sola banda de contexto, navegación local lateral solo dentro del proyecto, superficies planas con
bordes visibles y un foco amarillo imposible de perder; Public Sans con cuerpo de 15 px e IBM Plex Mono.

- **A favor:** la más accesible (5 en accesibilidad y en personas con distinta experiencia técnica): foco grueso,
  cuerpo de 15 px con texto principal en AAA, errores enlazados a su campo, pasos y «Revise sus respuestas». Patrones
  conocidos y documentados públicamente (mantenibilidad 4). El móvil de campo mejor resuelto, con aviso de sin señal.
- **En contra:** ocupa unos 170 px arriba (cabecera, navegación, contexto) y es la de menor densidad, así que frena
  a quien trabaja el día entero en tablas; puede parecer un servicio al ciudadano más que una herramienta de empresa;
  suma una familia tipográfica nueva; puntúa 3 en territorio y en monitoreo ejecutivo.

## Decisión

- **E es la identidad del portal** (ASM-149), con sus contras atendidos por los préstamos y por la densidad
  compacta. El usuario prefirió la legibilidad para todos los perfiles (relacionistas de campo, aprobadores
  ocasionales, gerencia) sobre la densidad por defecto.
- **Densidad compacta por persona.** Además de la normal, cada persona puede elegir la compacta, que el portal
  escribe como `data-density="compact"` en `<html>` y recuerda en el navegador (`localStorage`, `sgspe.densidad`):
  filas de 36 px, controles y campos de 36 px y una cabecera reducida (52 px y navegación de 44 px). Cambia solo
  medidas, nunca colores ni tipografía. Responde a la debilidad de E para quien trabaja en tablas.
- **Préstamos de las otras direcciones:**
  - de **B**, el Mapa SIG a todo el ancho con una paleta cartográfica separada de la operativa (`--sg-map-*` y
    `--sg-lyr-*`), y la vista «Lista + mapa» opcional, nunca por defecto, en Compromisos, QCS, Grupos de interés y
    Riesgos, ofrecida solo si los registros de la lista traen ubicación;
  - de **D**, la vista previa lateral acoplada (el `?peek=` actual, como panel de 420 px desde 1280 px y como cajón
    por debajo) y la hora de cálculo visible en los tableros;
  - de **C**, las notas al pie bajo las tablas y la anotación directa en los gráficos.
- **Un solo par de temas.** Los ids `light` y `dark` de wasichai llevan los valores de E: claro por defecto, oscuro a
  elección y «Según el sistema» respetado. Se retiran `docs/ux/themes.css` y el alias `sgspe-noche`; el selector de
  `docs/ux/tokens.css` para el oscuro es solo `:root[data-theme="dark"]` (y el sistema sin `data-theme`).
- **Conflictos del lienzo.** En oscuro rigen los valores de la hoja común (`civic.css`): `--sg-on-critical` es
  `#ffffff` y `--sg-risk-critical` `#c42323` (no `#0d131b` ni `#ff6b6b` de `tokens-e.css`).
- **Tipografía:** Public Sans (OFL, auto-alojada con `@fontsource`, sin CDN por la CSP de ADR-0026) para todo, con
  cifras tabulares, e IBM Plex Mono para códigos.
- **«—» en columnas numéricas.** Se admite en una columna numérica de tabla, con una nota al pie que lo explica
  («— = sin monto»); fuera de las tablas sigue DD-38 (lo que la cuenta no lee se omite).
- **Todos los colores en hexadecimal** en `tokens.css`, porque MapLibre y ECharts leen las variables y no resuelven
  `var()` ni `oklch`; cada `--sg-*` de color tiene su utilidad `--color-sg-*`.
- **La especificación** (paleta claro y oscuro, datos, capas, tipografía, medidas, shell, componentes y notas por
  pantalla) está en [`docs/ux/design-system.md`](../ux/design-system.md); los tokens en
  [`docs/ux/tokens.css`](../ux/tokens.css); las capturas de referencia en [`docs/ux/civic/`](../ux/civic/README.md).

## Consecuencias

### Positivas

- El oscuro deja de ser una inversión: datos, postura, nivel, calor, zonas y capas tienen valores propios.
- El mapa sigue al tema y sus capas se distinguen por grupo; los mapas de informe pueden tomar la misma paleta.
- El foco (halo amarillo de 3 px con anillo marino de 5 px en claro) da al menos 3:1 sobre toda superficie, también
  sobre la cabecera azul; el texto principal queda en AAA (17,5:1 sobre la superficie).
- Quien no elige un tema ya ve el de SGSPE: UPS-19 deja de hacer falta y wasichai/wasichai-ui#39 puede cerrarse.
- Un solo par de temas simplifica las pruebas de contraste y el selector de la cuenta.

### Negativas y riesgos

- La implementación en `sgspe-ui` es de complejidad media: un shell nuevo (cabecera, navegación horizontal con
  desplegables, banda de contexto, navegación local), una familia tipográfica nueva y la densidad compacta.
- Cambian decisiones de la navegación del rediseño: la barra lateral expandida y «Contraer» (DD-10, DD-22) pasan a la
  navegación horizontal; la fila de pestañas del proyecto (DD-03) pasa a la navegación local lateral desde `lg` y
  vuelve a ser una fila de pestañas en tableta; la barra inferior del teléfono (DD-17) pasa a Inicio · Proyectos ·
  Registrar · Mapa · Tareas, con los avisos en la campana de la cabecera. La arquitectura de información (secciones,
  orden, bloques y rutas) no cambia.
- Todas las capturas de los manuales y las pruebas visuales del portal se rehacen.
- La altura de la cabecera sigue siendo mayor que la de hoy en la densidad normal.

### Seguimiento

- El plan por etapas de la épica wasichai/sgspe-ui#88: tokens y fuentes, shell, componentes, mapa y paleta
  cartográfica, oscuro completo y pantallas.
- `sgspe-ui/src/theme/wasichai.css` apunta `--shell` a `--sg-header` y `--on-danger` a `--sg-on-danger` (nuevo: en
  oscuro, `--sg-on-critical` blanco no se lee sobre el `danger` rosado).
- La paleta de los mapas de informe (`model/styles/palette.json`, `gis.md` §7.2) se alinea con `--sg-lyr-*` en un
  cambio aparte.
- DD-40 (el nombre corto de las QCS) sigue abierta.

## Referencias

- Supuestos y brechas: ASM-149, ASM-077 (reemplazado), ASM-082 (paleta reemplazada), ASM-086 (reemplazado), UPS-19
  (sin objeto), ASM-085, ASM-047.
- Requisitos: RNF-USA-001, RNF-MNT-001, RNF-I18N-001, RQ-DSH-035, RQ-GIS-014, RQ-GIS-015.
- Documentos: [`docs/ux/design-system.md`](../ux/design-system.md),
  [`docs/ux/ux-architecture.md`](../ux/ux-architecture.md) §7.1, [`docs/ux/tokens.css`](../ux/tokens.css),
  [`docs/ux/redesign/07-decisiones-y-compatibilidad.md`](../ux/redesign/07-decisiones-y-compatibilidad.md),
  [`docs/dashboards.md`](../dashboards.md) §9.4.
- Lienzo de la dirección E: <https://claude.ai/artifact/Y6aV29ac54BQMERnieY15K>; informe del estado actual:
  <https://claude.ai/artifact/AUGqkLX83MjLzTZ3iQqoS8>; comparativo: <https://claude.ai/artifact/LmpPse2hcohT92ToLkdKyQ>.
- ADR de SGSPE: ADR-0024 (portal y `/admin`), ADR-0026 (CSP y mismo origen).
- wasichai: `WAS-ADR-0034` y `WAS-ADR-0035` (temas por tokens).
