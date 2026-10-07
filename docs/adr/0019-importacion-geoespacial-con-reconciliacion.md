# ADR-0019: Importación geoespacial con preparación y reconciliación

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F2

## Contexto

- Los componentes del proyecto, los predios y las concesiones llegan en hojas y archivos de un SIG externo, no se dibujan a
  mano [DOC-03]. El prompt pide tratar el territorio con PostGIS de verdad [PRM-01 §6].
- En DOC-03 los mismos sondajes tienen coordenadas distintas en la hoja Excel y en la tabla de ArcMap, con diferencias de
  ≈ 1 m a ≈ 216 m (CON-023). Son versiones del programa de perforación: pisarlas en silencio borraría historia (RQ-GIS-010).
- DOC-03 no indica su datum: los valores encajan en UTM 17S, pero pueden ser WGS 84 o PSAD56, y confundirlos desplaza los puntos
  cientos de metros (CON-024, RQ-GIS-007).
- wasichai-gis no importa nada: guarda geometrías como campos y habla GeoJSON EPSG:4326 [WAS-ADR-0019] [WAS-ADR-0007]. srtm
  importaba fuera de línea con `ogr2ogr` ([gis.md §1.3](../gis.md)).
- Las guardas de escritura no reciben geometrías [WAS-ADR-0040], así que validez, versión y área no pueden vivir en una guarda
  (RQ-GIS-011).
- La auditoría registra "geometry updated" sin coordenadas, así que la historia de las posiciones debe guardarse aparte
  (RQ-AUD-013).
- RQ-GIS-003 pide carga intermedia, validación, vista previa en el mapa y confirmación. El almacenamiento en SRID 4326 y el CRS
  de trabajo los decide ADR-0002; aquí solo se decide cómo entran los datos.

## Opciones consideradas

### Importación fuera de línea con `ogr2ogr`, como srtm

- Ventajas: cero código propio; GDAL lee todos los formatos.
- Desventajas: escribe directo en las tablas físicas y se salta `RecordService`, los permisos, la auditoría y el motivo
  (RNF-MNT-005); no reconcilia ni deja reporte de diferencias; exige acceso a la base a un analista SIG.

### Carga intermedia persistida: tablas de preparación o un objeto "lote de importación"

- Ventajas: la vista previa sobrevive entre peticiones; el lote sería un registro consultable.
- Desventajas: el DDL de negocio solo nace de la metadata, y una tabla fija del esquema `sgspe` (CON-052) no se justifica para
  un dato que dura minutos; un objeto de lote no lo pide ningún requisito (ASM-060).

### Alta o actualización directa por código (upsert)

- Ventajas: la más simple.
- Desventajas: sobrescribe en silencio lo que DOC-03 muestra como versiones distintas; incumple RQ-GIS-010 y CON-023.

### Vista previa sin estado y confirmación con el mismo archivo, con reconciliación por código (elegida)

- Ventajas: funciona con varias réplicas sin tablas intermedias; todo pasa por `RecordService` con permisos, guardas, motivo y
  auditoría; el lote entero se escribe o no se escribe.
- Desventajas: el archivo viaja dos veces; la importación con CRS de origen cubre solo tres objetos destino.

## Decisión

- **Dos endpoints de `sgspe.territory`** ([api.md §4.2](../api.md)):
  - `POST /api/sgspe/territory/imports`: vista previa. Lee el archivo en una carpeta temporal con límites de tamaño y tiempo y
    control de rutas dentro de los ZIP (RQ-SEC-020, ASM-050), calcula su SHA-256, transforma cada geometría a 4326 en PostGIS,
    valida y concilia con lo registrado. Devuelve `importBatchId`, SHA-256, errores, diferencias y entidades fuera de zona, y
    no escribe nada.
  - `POST /api/sgspe/territory/imports/{importBatchId}/confirm`: recibe otra vez el mismo archivo con las decisiones fila por
    fila, exige el SHA-256 de la vista previa, repite la validación y escribe todo en una transacción (`TransactionalOperator`)
    [WAS-ADR-0038], con el motivo `importación <id>` (ASM-051).
- **La carga intermedia de RQ-GIS-003 es el archivo en la carpeta temporal y su lectura validada.** La importación no es un
  objeto: cada registro guarda su `import_batch_id` (ASM-060).
- **Destinos con CRS de origen:** `project_component` (`apiOnly`, también con alta y versión manual por
  `POST /api/sgspe/project-components`), `mining_concession` y `land_parcel`. Las demás geometrías llegan por GeoJSON en
  EPSG:4326 por la API genérica, por script o por dibujo ([gis.md §4.1](../gis.md)).
- **Validaciones** ([gis.md §4.3](../gis.md)): CRS de origen obligatorio del catálogo "CRS de origen", sin valor por defecto
  silencioso, con el `working_crs` propuesto y confirmado por el usuario (CON-024); tipo de geometría igual al del campo; validez
  con `ST_IsValidReason` y reparación solo con consentimiento; zona y región; ejes invertidos; código obligatorio y sin repetir.
  PSAD56 solo como entrada, nunca almacenado (RQ-GIS-007).
- **Reconciliación de `project_component`**, medida en el `working_crs`:

  | Caso | Al confirmar |
  |---|---|
  | Código nuevo | Crea la versión 1 |
  | Mismo código, desplazamiento menor de 1 cm | Nada; figura en el reporte |
  | Mismo código, desplazamiento de 1 cm o más | Versión nueva con `previous_version`; la anterior pasa a `superseded`, o se descarta la fila |
  | Mismo código y mismas coordenadas, otros atributos | Actualiza la versión vigente con motivo |

- `land_parcel` y `mining_concession` no tienen versiones: otra geometría con el mismo `code` es una actualización con motivo, y
  el reporte muestra el cambio de área y la distancia de Hausdorff.
- **Trazabilidad:** cada registro guarda `source_crs`, `source_reference` e `import_batch_id`; los puntos, además, sus
  coordenadas originales. Desde F3 el archivo original queda como `evidence` con el mismo `import_batch_id`, en el
  almacenamiento de objetos de ADR-0014 (RQ-GIS-008, RQ-AUD-013).
- **Formatos:** XLSX, CSV (código, este, norte, cota, azimut e inclinación, o latitud y longitud) y GeoJSON con MULTI* en F2;
  Shapefile, KML y KMZ, GeoPackage, FileGDB y GPX con GDAL en F6 (ASM-056, RQ-GIS-018, RQ-GIS-020).
- Desde F3 la confirmación honra `Idempotency-Key`, según ADR-0025. Los permisos son CREATE y UPDATE sobre el objeto destino
  y, desde F3, CREATE sobre `evidence` ([api.md §4.2](../api.md)); la matriz por rol es de ADR-0006.

## Consecuencias

### Positivas

- Ninguna coordenada se pisa en silencio: cada diferencia queda como versión o como cambio auditado, con su distancia.
- Sin estado en el servidor entre la vista previa y la confirmación, el diseño vale igual con una réplica o con varias.
- Las importaciones respetan permisos, guardas y auditoría, porque escriben con `RecordService` como el usuario.

### Negativas y riesgos

- El umbral de 1 cm es un valor por defecto propuesto y configurable; si el ruido de transformación lo supera, aparecerían
  versiones falsas. La prueba de ida y vuelta lo vigila.
- Los objetos MULTIPOLYGON sin campos de origen (`community`, `area_of_influence`, `project.interest_area`) no se importan con
  CRS de origen; llevarlos exige agregar esos campos al modelo de dominio primero, como propuesta para F6.
- Un archivo grande viaja dos veces; los límites de RQ-SEC-020 acotan el costo.

### Seguimiento

- Abiertos: GAP-012 (formatos, volúmenes, CRS y datum reales), GAP-019 y CON-026 (DOC-03 solo sirve como ejemplo de formato),
  ASM-056 (formatos), ASM-060 (en F6 un ADR puede agregar un objeto de importación) y ASM-050 (límites).
- Se acepta al iniciar F2; se revisa en F6, con GDAL.
- Pruebas de [gis.md §9](../gis.md): "Importación tipo DOC-03", "CRS de origen", "Confirmación", "PSAD56", "MULTI*" e
  "Ida y vuelta UTM ↔ 4326"; y la prueba "Importaciones" de [security.md §14](../security.md) (rutas en ZIP, tamaño y tiempo).

## Referencias

- Requisitos: RQ-GIS-003, RQ-GIS-007, RQ-GIS-008, RQ-GIS-010, RQ-GIS-011, RQ-GIS-018, RQ-GIS-020, RQ-SEC-020, RQ-AUD-007,
  RQ-AUD-013, RNF-MNT-005; CON-023, CON-024, CON-026, CON-052; GAP-012, GAP-019; ASM-050, ASM-051, ASM-056, ASM-060.
- Documentos: [gis.md §1.2, §3.5, §4 y §9](../gis.md), [api.md §4.2](../api.md),
  [domain-model.md §6.2 y §7.2](../domain-model.md), [security.md §14](../security.md).
- ADR de SGSPE: ADR-0002, ADR-0006, ADR-0008, ADR-0011, ADR-0014, ADR-0025.
- WAS-ADR-0007 (Geometry over R2DBC as GeoJSON in SQL), WAS-ADR-0019 (A geometry is a field, so an object can have more than
  one), WAS-ADR-0038 (RecordService joins the caller's transaction, and that is supported API), WAS-ADR-0040 (Append-only
  objects, a pre-write guard SPI, and api-only objects).
- Fuentes: [DOC-03], [PRM-01 §6], [USR-03].
