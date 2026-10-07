# ADR-0016: KPIs calculados en vivo y ECharts como librería de gráficos

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F2

## Contexto

- El usuario quiere ver la situación en tableros [USR-02]; el prompt pide al menos seis (ejecutivo, proyecto, compromisos,
  quejas, riesgos y territorial) [PRM-01 §16], y DOC-01 aporta los 13 indicadores de la Tabla 9.5, la evaluación mensual de
  grupos de interés y la revisión semanal de riesgos [DOC-01 Tabla 9.5, §9.3.1.1, §9.3.2.1].
- Los entregables repiten esas cifras, y deben coincidir con las de los tableros (RQ-DSH-034).
- En wasichai el DDL de negocio solo nace de la metadata [WAS-ADR-0003]: no hay vistas materializadas ni tablas de
  instantáneas propias. `RecordService` aplica tenant y permisos de objeto y campo, cuenta en la base y lee por cursor
  [WAS-ADR-0036] [WAS-ADR-0038], y omite en silencio los campos que la persona no puede leer.
- No hay requisitos de volumen ni de rendimiento hasta que se responda GAP-027.
- Ni wasichai-ui ni las apps de referencia traen una biblioteca de gráficos, y los tableros necesitan mapa de calor 5 × 5,
  dispersión con cuadrantes y series (ASM-046).

## Opciones consideradas

### Vistas materializadas o tablas de instantáneas

- A favor: consultas rápidas sobre agregados ya calculados.
- En contra: DDL fuera de la metadata, un segundo motor de permisos para leerlas y datos desfasados; RQ-DSH-031 las excluye.

### SQL de agregación sobre las tablas físicas desde el inicio

- A favor: una consulta por indicador.
- En contra: acopla el código a las tablas `<objeto>__<org8>` y obliga a reimplementar permisos de campo y alcance en cada
  consulta ([architecture.md §14](../architecture.md), riesgo 3); sin una medición que lo justifique.

### Cálculo en vivo con `RecordService` y funciones puras

- A favor: los permisos de la plataforma se aplican solos; un solo código para tableros, entregables e IA.
- En contra: varias consultas por tablero y rendimiento por medir.

### Gráficos con ECharts

- A favor: licencia Apache-2.0; cubre el mapa de calor 5 × 5, la dispersión con cuadrantes y las series que piden los
  tableros (ASM-046); trae descripción ARIA, importación por módulos y renderizador SVG.
- En contra: una dependencia pesada más en el portal, que obliga a cargarla en diferido y a aislarla tras una envoltura.

### Gráficos con Highcharts

- A favor: cubre los mismos tipos de gráfico.
- En contra: excluida por su licencia, como iText y Aspose ([platform-fit.md §6](../platform-fit.md)).

### Gráficos de informe por captura con Gotenberg y Chromium

- A favor: un gráfico del entregable idéntico al del tablero.
- En contra: un segundo renderizador en la ruta de los entregables, que en el MVP usan gráficos nativos y editables de
  Word (RQ-REP-031); queda para un ADR, no antes de F8 ([deliverables.md §7.9](../deliverables.md)).

## Decisión

**Indicadores** ([dashboards.md §6 y §8](../dashboards.md)):

- Cada KPI es una función de `sgspe.dashboard` que recibe los filtros comunes (RQ-DSH-001) y devuelve valor, desgloses, series y
  advertencias. La llaman en proceso los endpoints, el ensamblado de entregables (ADR-0013) y el resumen de IA (RQ-DSH-034).
- Conteos con `RecordService` (página de tamaño 1 con conteo); lo que necesita más que un conteo se lee por cursor sin
  conteo y se agrega en funciones puras de Kotlin, como `PanelService` de srtm.
- SQL agregado solo donde una medición lo justifique, sobre las tablas del propio tenant resueltas por la metadata y
  detrás de la compuerta de permisos de las consultas espaciales (RQ-GIS-017).
- La historia sale de las fechas de los eventos, de las fechas derivadas de transición y de los objetos append-only; los
  plazos, de `deadline_status` (ADR-0012). Sin vistas materializadas ni instantáneas (RQ-DSH-031).
- **Sin permiso no hay cifra.** Antes de calcular, cada KPI comprueba la lectura de sus objetos y campos con la definición
  filtrada para quien llama [WAS-ADR-0020]; si falta, responde `available: false` con `FIELD_NOT_READABLE`, nunca cero
  (RQ-DSH-032). Los de postura solo dan cifras a quien lee `stance` (GAP-024).
- Caché solo en la interfaz, 60 segundos; el servidor no guarda resultados en el MVP.
- **Cifras oficiales opcionales** (opcional, llega en F8, ASM-042). El catálogo `indicator` documenta cada KPI; cerrar
  un periodo congela la cifra en `indicator_measurement` (`appendOnly`, `apiOnly`), que responde 409 para los KPI de
  postura (KPI-STK-02, KPI-STK-05 y KPI-STK-06) y para KPI-TER-01 (RQ-DSH-037).
- Endpoints: `GET /api/sgspe/dashboards/{dashboard}`, `GET /api/sgspe/dashboards/indicators/{code}`,
  `GET /api/sgspe/dashboards/indicators` y `POST /api/sgspe/indicators/{id}/measurements` ([api.md §4.11](../api.md)).

**Gráficos** ([dashboards.md §9](../dashboards.md)):

- ECharts 6 es la única biblioteca de gráficos (RQ-DSH-035, ASM-046), cargada solo en las rutas de tableros, con
  importación por módulos y el renderizador SVG [WAS-ADR-0028]. Una envoltura propia y delgada de React la aísla:
  cambiarla es la forma de revertir ASM-046.
- Colores desde los tokens del tema, claro y oscuro [WAS-ADR-0034] [WAS-ADR-0035]; colores de estado reservados y siempre
  con su texto; descripción ARIA y una tabla "Ver datos" bajo cada gráfico (ASM-047).
- Los mapas no son de ECharts sino de MapLibre (RQ-GIS-014), y los entregables usan gráficos nativos de Word
  (RQ-REP-031).

## Consecuencias

### Positivas

- Tableros, entregables y resumen de IA dan las mismas cifras, con los permisos de quien mira.
- Ningún indicador inventa un cero por falta de permiso.
- Una sola biblioteca de gráficos, accesible y aislada detrás de una envoltura.

### Negativas y riesgos

- El rendimiento con volúmenes reales es desconocido (GAP-027): se mide antes de optimizar, y un SQL agregado acopla a
  detalles de la plataforma ([architecture.md §14](../architecture.md), riesgos 3 y 13).
- Una cifra viva puede cambiar si llegan registros tardíos; la interfaz dice si muestra la viva o la oficial.
- Mientras rija la lectura a nivel de tenant (CON-041), el filtro de proyecto por defecto es una comodidad, no un control
  de acceso (ADR-0006).

### Seguimiento

- Se acepta al iniciar F2, con los primeros KPI de grupos de interés y relacionamiento y el primer uso de ECharts; cada
  módulo trae los suyos y F8 los consolida (RQ-DSH-036).
- Abiertos: GAP-027, GAP-018 (metas y frecuencias), GAP-024, CON-041, UPS-01.
- Antes de F2 se valida la paleta (contraste y daltonismo, en claro y oscuro).
- Pruebas: unitarias de las funciones puras de agregación; de integración de los endpoints con permisos de campo
  ("no disponible"); y el criterio 19 de [deliverables.md §13.2](../deliverables.md) (cifras iguales a las del tablero).

## Referencias

- Requisitos: RQ-DSH-001, RQ-DSH-031, RQ-DSH-032, RQ-DSH-034, RQ-DSH-035, RQ-DSH-036, RQ-DSH-037, RQ-GIS-014, RQ-GIS-017,
  RQ-REP-031, CON-041, ASM-042, ASM-046, ASM-047, GAP-018, GAP-024, GAP-027, UPS-01.
- Documentos: [dashboards.md §2, §3, §6, §7, §8, §9, §10 y §11](../dashboards.md), [api.md §4.11](../api.md),
  [architecture.md §9 y §14](../architecture.md), [platform-fit.md §2.12 y §6](../platform-fit.md),
  [security.md §7.8 y §7.9](../security.md).
- wasichai: `WAS-ADR-0003` (Metadata-driven architecture), `WAS-ADR-0020` (The caller can ask what they may do, and asking
  grants nothing), `WAS-ADR-0028` (Frontend modules plug into a registry), `WAS-ADR-0034` (A stored resource for user
  preferences, starting with theme and locale), `WAS-ADR-0035` (Themes get extension tokens and data-slot hooks, and the
  library ships themes as optional sheets), `WAS-ADR-0036` (Declared indexes, an optional count and keyset reads),
  `WAS-ADR-0038` (RecordService joins the caller's transaction, and that is supported API).
- ADR de SGSPE: ADR-0006, ADR-0012, ADR-0013.
- Fuentes: [USR-02], [USR-05], [PRM-01 §15, §16], [DOC-01 Tabla 9.5, §9.3.1.1, §9.3.2.1].
