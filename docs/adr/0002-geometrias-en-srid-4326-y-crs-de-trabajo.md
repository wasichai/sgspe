# ADR-0002: Geometrías en SRID 4326 con CRS de trabajo por proyecto

## Estado

Aceptado · 2026-10-07

## Contexto

- El territorio es crítico: el prompt pide no reducirlo a latitud y longitud, usar PostGIS de verdad y no asumir un
  SRID universal, sino uno configurable según el contexto territorial [PRM-01 §6] (RQ-GIS-022).
- En wasichai-gis una geometría es un campo GEOMETRY con su propio tipo, SRID y dimensión, igual para todos los registros
  del tenant (WAS-ADR-0019); la API habla GeoJSON EPSG:4326 y la conversión ocurre en SQL (WAS-ADR-0007); el módulo es
  opcional y no cambia la API (WAS-ADR-0027). Esas son decisiones de la plataforma.
- Un tenant puede tener proyectos en las zonas UTM 17S, 18S y 19S, así que un SRID UTM fijo por campo no sirve
  (CON-037).
- DOC-03 trae sondajes como `Point ZM` en UTM 17S sin datum visible: pueden ser WGS 84 o PSAD56, y confundirlos desplaza
  los puntos cientos de metros (CON-024) [DOC-03]. DOC-01 dibuja su Anexo 1 con grilla UTM [DOC-01 Anexo 1] y DOC-03 informa
  un área "148.00 Ha" que debe coincidir con la del SIG del cliente.
- `community`, `land_parcel` y `mining_concession` no pertenecen a un solo proyecto: una concesión puede vincularse a
  proyectos de zonas distintas.

## Opciones consideradas

### SRID UTM fijo por campo (por ejemplo, EPSG 32717)

- Ventajas: áreas y distancias directas sobre la columna; sin transformaciones al calcular.
- Desventajas: un tenant con proyectos en dos zonas no cabe, porque el SRID es del campo y no del registro (CON-037).

### Guardar cada geometría en su CRS de origen

- Ventajas: conserva el dato tal como llegó.
- Desventajas: imposible con un SRID por campo; mezclaría PSAD56 y WGS 84 en el almacenamiento (CON-024).

### SRID 4326 en el almacenamiento y CRS de trabajo UTM por proyecto (elegida)

- Ventajas: un solo SRID para todo el tenant; los cálculos se hacen en la zona correcta de cada proyecto; el origen se
  conserva como atributos (RQ-GIS-008); se revierte sin cambiar código porque el código lee el SRID de la metadata.
- Desventajas: todo cálculo métrico exige `ST_Transform`; nunca se puede medir sobre la columna en 4326.

## Decisión

**ASSUMPTION (ASM-002)**, aplicada en todo el modelo ([gis.md §3](../gis.md), [domain-model.md §4.3](../domain-model.md)):

- **Almacenamiento.** Toda geometría es un campo GEOMETRY en SRID 4326 y 2D, nunca requerido ni único (RQ-GIS-006). La
  cota, el azimut y la inclinación son atributos de `project_component` (`source_elevation`, `azimuth`, `dip`,
  `depth_m`): wasichai no tiene dimensión M y una columna Z obligaría a dibujar con Z en el navegador.
- **CRS de trabajo.** `project.working_crs` es un ENUM del catálogo "CRS de trabajo" (EPSG 32717, 32718 y 32719, WGS 84
  UTM 17S, 18S y 19S) (RQ-GIS-004). `ProjectWriteGuard` lo exige para llevar el proyecto a `active` (ASM-062).
- **Registros sin un solo proyecto.** `community`, `land_parcel` y `mining_concession` calculan en la zona UTM WGS 84 que
  contiene el centroide de su geometría; un centroide fuera de las tres zonas se marca y no se calcula (ASM-064).
- **PSAD56 solo como entrada.** Los EPSG 24877, 24878 y 24879 del catálogo "CRS de origen" se aceptan al importar, PostGIS
  los transforma a 4326 y nunca se guardan (RQ-GIS-007). Cada registro importado guarda `source_crs`,
  `source_reference` e `import_batch_id`; los puntos, también sus coordenadas originales (RQ-GIS-008, ASM-060).
- **Área.** El área oficial es la plana en hectáreas, con dos decimales, en el CRS de trabajo o en la zona del centroide
  (RQ-GIS-009, ASM-057); la geodésica solo suma entre zonas UTM; el área registral (`registered_area_ha`) se muestra junto
  a la calculada (`calculated_area_ha`, derivado) y nunca la reemplaza.
- **Sin SRID supuesto.** El código lee el SRID de cada campo desde la metadata y nunca calcula `ST_Area` ni `ST_Length`
  sobre una columna en 4326 (RQ-GIS-022). Así, un tenant de una sola zona puede aplicar el modelo con un SRID UTM sin
  cambiar código, que es la forma de revertir ASM-002.
- **`MULTI*`.** Se acepta por API e importación aunque la interfaz de wasichai no la dibuje (RQ-GIS-020, CON-060).

| Operación | CRS |
|---|---|
| Almacenar | 4326, 2D |
| Intercambiar por la API | GeoJSON EPSG:4326 (plataforma, RNF-INT-005) |
| Área, longitudes, distancias y buffers de un proyecto | `working_crs` del proyecto |
| Área de `community`, `land_parcel` y `mining_concession` | Zona UTM del centroide |
| Totales entre zonas | Geodésico |
| Importar | CRS de origen declarado, obligatorio |
| Mostrar y exportar este y norte; mapas de informe | `working_crs` |

- Las consultas espaciales filtran con el índice GIST sobre la columna en 4326 y miden en UTM; ningún buffer se guarda
  como geometría; no hay vistas materializadas (RQ-GIS-016). Las uniones espaciales viven en `sgspe.territory` detrás de
  una compuerta de permisos (RQ-GIS-017, F6).
- La importación con reconciliación: ver ADR-0019. Los mapas de informe, la publicación en GeoServer y el proxy WMS: ver
  ADR-0015.

## Consecuencias

### Positivas

- Un tenant puede tener proyectos en tres zonas UTM con un solo modelo.
- Las áreas coinciden con lo que muestran ArcGIS y QGIS en UTM [DOC-03].
- La historia de las coordenadas se conserva en los datos de origen, aunque la auditoría de wasichai no guarde
  coordenadas (RQ-AUD-013).

### Negativas y riesgos

- Cada cálculo métrico lleva una transformación; un error de zona daría áreas falsas, por eso la zona del centroide se
  valida contra 32717, 32718 y 32719.
- El CRS de origen obligatorio agrega un paso a la importación, necesario mientras no se conozca el datum real (GAP-012).
- La cartografía base y las fuentes oficiales siguen abiertas (GAP-011).

### Seguimiento

- F1: imagen `postgis/postgis:18-3.6` en el entorno y en las pruebas. F2: starter gis, `working_crs`, área oficial. F6:
  consultas espaciales.
- Pruebas de [gis.md §9](../gis.md): ida y vuelta UTM ↔ 4326 en las tres zonas con error de 1 cm o menos; un cuadrado de
  1 km da 100.00 ha; zona del centroide; desplazamiento PSAD56; "SRID desde la metadata" (un objeto en 32718 funciona
  igual que uno en 4326); área derivada recalculada por `AreaListener` y corregida por `DerivedFieldsJob` (RQ-GIS-021).
- Pendientes: GAP-011, GAP-012 y GAP-019 (proyecto de DOC-03, solo ejemplo de formato).

## Referencias

- Requisitos: RQ-GIS-004, RQ-GIS-006, RQ-GIS-007, RQ-GIS-008, RQ-GIS-009, RQ-GIS-016, RQ-GIS-017, RQ-GIS-020,
  RQ-GIS-021, RQ-GIS-022, RNF-INT-005, RQ-AUD-013, CON-024, CON-037, CON-060, ASM-002, ASM-057, ASM-060, ASM-062,
  ASM-064, GAP-011, GAP-012, GAP-019.
- Documentos: [gis.md](../gis.md) §1.3, §3 y §9; [domain-model.md](../domain-model.md) §4.3, §6.1 y §6.2;
  [database-model.md §3.7](../database-model.md); [requirements-analysis.md §10 y §12](../requirements-analysis.md).
- wasichai: WAS-ADR-0002 (*PostgreSQL 18 + PostGIS (+ pgvector)*), WAS-ADR-0007 (*Geometry over R2DBC as GeoJSON in
  SQL*), WAS-ADR-0019 (*A geometry is a field, so an object can have more than one*), WAS-ADR-0027 (*GIS is optional,
  and the API is unchanged when it is present*).
- Fuentes: [PRM-01 §6], [DOC-01 Anexo 1], [DOC-03], [USR-02].
