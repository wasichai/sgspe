# ADR-0015: Mapas de informe con GeoServer GetMap

## Estado

Aceptado · 2026-10-13 (F8). En F6 (2026-10-11) se aceptó la mitad que sirve al portal; F8 acepta los mapas de informe
con el resultado del spike de F1: GetMap por POST XML con SLD en línea y las decoraciones dibujadas por SGSPE (ASM-134)

### Nota de F6 (2026-10-11)

- F6 acepta la mitad de la decisión que sirve al portal: GeoServer solo en la red interna, los estilos SLD y el proxy
  WMS autenticado, que publica solo maestros del tenant sin campos restringidos y entrega solo imágenes (ASM-121).
- El WFS autenticado para analistas queda pospuesto (ASM-120): las exportaciones geográficas cubren el trabajo con
  archivos.
- Los mapas de informe por GetMap, con capas sensibles en línea y decoraciones, siguen propuestos para F8; su spike
  ([gis.md §7.3](../gis.md)) se hace antes de aceptarlos.

### Nota de F8 (2026-10-13)

- F8 acepta los mapas de informe por GetMap con dos cambios que salen del spike de F1 (decisión del usuario del
  2026-10-13, ASM-134): la petición va siempre por POST XML (`ogc:GetMap` con un `StyledLayerDescriptor`: las capas
  publicadas por nombre y las demás como `UserLayer` con `InlineFeature`, GML con `srsName` de URL), sin el límite de
  largo de una URL; y GeoServer dibuja solo el cuerpo del mapa: marco, grilla UTM, leyenda, barra y escala numérica,
  norte de cuadrícula, CRS y fuente los dibuja SGSPE (`MapComposer`), porque la decoración `legend` queda vacía con
  capas en línea y `scaleline` usa otro dpi.
- En EXTERNO rige la divulgación por capa de las exportaciones geográficas (ASM-124); un mapa con más de 5 000 elementos
  en línea falla (`MAP_TOO_MANY_FEATURES`) y bloquea la emisión (`409 MAP_FAILED`).

## Contexto

- Los entregables llevan mapas estáticos con leyenda, norte, grilla, escala y fuente, en el CRS UTM del proyecto: el
  Anexo 1 de DOC-01 con diez capas y el mapa del predio de DOC-02 [DOC-01 Anexo 1] [DOC-02 p.1, p.5] (RQ-REP-008,
  RQ-GIS-019).
- wasichai deja GeoServer como publicador opcional, que nunca está en la ruta de una petición [WAS-ADR-0006]
  [WAS-ADR-0027], y lo publica con un endpoint por geometría de objeto [WAS-DOC api/rest.md]. Pero el WMS de GeoServer no
  autentica y lee la base directamente, saltándose tenencia y permisos (CON-054, RQ-SEC-011).
- Algunas capas de los mapas son sensibles: predios con titulares, partida y ubicaciones que pueden ser viviendas
  ([gis.md §2.1](../gis.md)), y un mapa EXTERNO debe generalizarlas (RQ-REP-014).
- Los entregables se generan en segundo plano, como la plataforma (ADR-0013) [WAS-ADR-0039].

## Opciones consideradas

### GetMap con capas públicas publicadas y capas sensibles en línea

- A favor: GeoServer ya resuelve estilos SLD, etiquetas con control de solapes y decoraciones; las capas sensibles viajan
  dentro de la petición, ya transformadas por nivel, y nunca se publican.
- En contra: GeoServer pasa a ser una dependencia del despliegue donde se emiten mapas, y hay que confirmar que acepta
  elementos en línea con leyenda en EPSG:32717 a 32719.

### Publicar todas las capas, también las sensibles

- A favor: peticiones GetMap más simples.
- En contra: GetCapabilities y GetMap expondrían capas de todos los tenants y datos personales fuera de los permisos de la
  aplicación (CON-054, RQ-SEC-011).

### Otro generador de mapas: una capa interna con una vista SQL limitada, o QGIS Server

- A favor: es la alternativa registrada si GeoServer no basta (ASM-038).
- En contra: una vista SQL fuera de la metadata o un servidor más que operar; queda como plan B del spike.

## Decisión

Los mapas de informe salen de GeoServer con GetMap y estilos SLD, en el `working_crs` del proyecto, con las decoraciones
dibujadas por SGSPE (RQ-REP-030, ASM-038, ASM-134) ([gis.md §7](../gis.md)):

- **GeoServer nunca es público.** Vive en la red interna (topología en ADR-0026) y el proxy inverso no tiene ruta hacia
  él. La app solo lo llama desde el trabajo en segundo plano y desde el proxy WMS autenticado de `sgspe.territory`, que
  exige el token, READ sobre el objeto de cada capa y la lista blanca del tenant ([gis.md §6.2](../gis.md)) (RQ-GIS-012).
- **Solo capas no sensibles publicadas.** `model/publish_layers.py`, con el patrón de `apply.py`, publica las capas
  públicas de [gis.md §2.2](../gis.md) con el endpoint de wasichai y un token de `ADMIN`, es idempotente y corre al
  aprovisionar cada organización y en cada actualización de F6. En el mismo paso carga los estilos SLD, únicos por
  despliegue, que el proxy WMS y el trabajador de entregables piden por nombre. SGSPE no tiene endpoint propio para esto.
- **Una paleta compartida.** Los SLD toman sus colores de un JSON de paleta que también usan las capas MapLibre del
  portal; las etiquetas de localidades y distritos son reglas de texto de los SLD ([gis.md §7.2](../gis.md)).
- **Cómo se arma un mapa** (JOB-05, ADR-0013):
  1. encuadre por regla del tipo ([gis.md §7.3](../gis.md)), con 10 % de margen, ajustado a la escala estándar siguiente
     para el marco del tipo (16 × 12 cm, o 24 × 16 cm en el Anexo 1 de T1) a 300 dpi;
  2. capas públicas por nombre con sus estilos;
  3. capas no públicas leídas con `RecordService`, transformadas por el nivel de divulgación (ADR-0017) y enviadas en
     línea por POST XML (`GetMapRequest`), con las geometrías simplificadas a medio píxel y un tope de 5 000 elementos;
  4. GeoServer devuelve solo el cuerpo del mapa; marco, grilla UTM, leyenda, barra y escala numérica, norte de
     cuadrícula, CRS y fuente los dibuja SGSPE con `MapComposer` y la paleta compartida (ASM-134);
  5. un PNG con su SHA-256, congelado en el modelo del entregable: volver a generar una versión emitida no vuelve a pedirlo.
- **Fallas.** Si un mapa falla, el borrador muestra el aviso `MAP_FAILED` en su lugar y la emisión responde
  `409 MAP_FAILED` (RQ-REP-030). Sin GeoServer el resto de SGSPE funciona.
- **Sin rastro de lo sensible.** El GeoServer del despliegue no registra los cuerpos de las peticiones ni guarda en caché
  esas imágenes; la caché de teselas es solo para el WMS público (RQ-SEC-010).
- **Sin mapa base en línea** en los mapas de informe del MVP: capas vectoriales de referencia, como el mapa de DOC-02
  (GAP-011).

## Consecuencias

### Positivas

- Un solo lenguaje de estilos para el WMS del portal, los informes y los analistas que usan QGIS.
- Ninguna capa con datos personales se publica, y el nivel de divulgación se aplica antes de dibujar.
- Llamar a GeoServer solo en segundo plano respeta WAS-ADR-0006; el proxy WMS es la excepción acotada.

### Negativas y riesgos

- GeoServer es necesario para emitir mapas: si cae, la emisión queda bloqueada
  ([architecture.md §14](../architecture.md), riesgo 8).
- Un GetMap con geometrías en línea lleva datos confidenciales por la red interna.
- La cartografía base, sus licencias y la disponibilidad de GeoServer en producción siguen abiertas (GAP-011).

### Seguimiento

- **Spike técnico de F1:** hecho el 2026-10-07 (el resultado, abajo); F8 adopta su alternativa: SGSPE dibuja la leyenda,
  la escala y el resto de las decoraciones (ASM-134).
- Aceptado en parte en F6 (GeoServer, `publish_layers.py`, los estilos y el proxy WMS) y entero en F8 (2026-10-13), con
  los mapas de informe (RQ-REP-030), que implementa el plan 8.2c.
- Abiertos: GAP-011, GAP-022. UPS-13 (mapa base de `MapView`) está resuelta con wasichai-ui#38 y la adopta ASM-125.
- Pruebas: `MapFramingTest`, `MapComposerTest` y `GetMapRequestTest`; GetMap contra un GeoServer real en la suite lenta
  (`slowTest`: el PNG se decodifica y la leyenda que dibuja `MapComposer` tiene píxeles) y el proxy WMS de
  [gis.md §9](../gis.md); el criterio 7 de [deliverables.md §13.2](../deliverables.md).
- **Resultado del spike de F1 (2026-10-07).** GeoServer 3.0.1 con capas en línea por `SLD_BODY`: M1 EPSG:32717 PASS,
  EPSG:32718 PASS, EPSG:32719 PASS (PNG de 1890 × 1417, centro #3366CC, de 0,15 a 1,2 s), siempre que el polígono en
  línea declare `srsName="http://www.opengis.net/gml/srs/epsg.xml#32717"` (o solo el código): con `EPSG:32717` el lector
  de elementos en línea de GeoTools falla con `NumberFormatException`; M3 decoraciones (leyenda, escala, norte y fuente)
  FAIL: norte, fuente y escala numérica (1 : 24997) salen, pero la leyenda queda vacía porque la decoración `legend`
  busca la capa en el catálogo y una capa en línea no tiene `LayerInfo` (`NullPointerException`, la decoración se omite;
  con una capa del catálogo la leyenda sí sale, y la opción `layers` de la decoración deja fuera las capas en línea), y
  la barra `scaleline` con `dpi:300` dibuja «500 m» sobre unos 150 m reales (usa 90,7 dpi salvo que el diseño le pase
  `dpi`, y entonces queda en una barra de 100 m de 47 px); M4 POST con 300 polígonos PASS por POST XML (`ogc:GetMap`,
  139 KB, menos de 0,5 s). La grilla UTM la sigue dibujando SGSPE. Consecuencia: M3 falla, así que se anota como
  pendiente de F6 la alternativa de ASM-038 (otro generador de mapas, o que SGSPE dibuje la leyenda de las capas en
  línea y la barra de escala como ya dibuja la grilla); ADR-0015 no cambia (según la regla de decisión del plan de F1).
  F8 eligió la segunda alternativa (ASM-134).

## Referencias

- Requisitos: RQ-REP-008, RQ-REP-014, RQ-REP-019, RQ-REP-030, RQ-GIS-012, RQ-GIS-019, RQ-SEC-010, RQ-SEC-011, CON-054,
  ASM-038, ASM-124, ASM-125, ASM-134, GAP-011, GAP-022, UPS-13 (resuelta).
- Documentos: [gis.md §1.4, §2, §6.2, §7, §9 y §11](../gis.md), [deliverables.md §7.8 y §9.2](../deliverables.md),
  [architecture.md §5.4, §10 y §14](../architecture.md), [security.md §10](../security.md).
- wasichai: `WAS-ADR-0006` (GeoServer as publisher, not as the core), `WAS-ADR-0027` (GIS is optional, and the API is
  unchanged when it is present), `WAS-ADR-0039` (Background work runs RecordService as the platform, and takes a cluster
  lock).
- ADR de SGSPE: ADR-0002, ADR-0013, ADR-0017, ADR-0026.
- Fuentes: [DOC-01 Anexo 1], [DOC-02 p.1, p.5], [DOC-03], [USR-01].
