# ADR-0014: Evidencias y entregables en almacenamiento de objetos

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F3

## Contexto

- SGSPE guarda archivos: evidencias que prueban un hecho (actas, fotos, comprobantes, cartas), con fecha, autor, hash y
  vínculos a varios registros [DOC-01 §9.3.1.2, Tabla 9.5] [DOC-02 p.4] (RF-DOC-001); los originales de cada importación
  (ASM-060); y desde F8 los borradores, las versiones emitidas y las plantillas de los entregables (RQ-REP-017, RQ-REP-027).
- wasichai no tiene campos FILE ni IMAGE (CON-046, UPS-07) [WAS-DOC domain/metadata-model.md].
- La clasificación de una evidencia decide quién la descarga (RF-DOC-003, ASM-071), y las fotos de personas llevan su
  consentimiento (RNF-PRIV-004).
- srtm ya resolvió el almacenamiento de sus emisiones con un puerto propio (`AlmacenEmision`) con implementación local y
  S3 ([platform-fit.md §5](../platform-fit.md)).

## Opciones consideradas

### Archivos como bytes en la base

- A favor: un solo respaldo y transacción con el registro.
- En contra: no hay tipo de campo para ellos en wasichai y el DDL de negocio solo nace de la metadata (RNF-MNT-005); los
  archivos grandes (fotos, entregables de unas 100 páginas) inflarían la base y su respaldo.

### Esperar el campo FILE de wasichai (UPS-07)

- A favor: sin código propio de almacenamiento.
- En contra: las evidencias hacen falta en F3 (RF-DOC-001) y no hay plazo para la brecha (GAP-028).

### Puerto de almacenamiento de objetos propio, local o compatible con S3

- A favor: patrón probado en srtm; claves y hashes en el registro, archivos fuera de la base; se migra al campo FILE
  cuando exista.
- En contra: un servicio más en el despliegue y archivos cuyo ciclo de vida no gobierna la plataforma.

## Decisión

Los archivos viven en almacenamiento de objetos y el registro guarda su clave, tamaño y SHA-256 (ASM-013):

- **Puerto en `sgspe.evidence`**, copiado del patrón de srtm sin sus rodeos de wasichai 0.2.0: claves relativas con
  segmentos `[A-Za-z0-9._-]`; guardar, abrir en flujo, traer, borrar, existir, tamaño y listar; E/S bloqueante en
  `Dispatchers.IO`; y una prueba de contrato que pasan las implementaciones local y S3. `sgspe.deliverable` usa el mismo
  puerto ([deliverables.md §11](../deliverables.md)).
- **Implementaciones.** Sistema de archivos local o un servicio compatible con S3 (AWS SDK v2, versión que se fija en F3);
  MinIO solo en desarrollo, por su licencia AGPL ([architecture.md §10.1](../architecture.md)).
- **`evidence` inmutable.** Es `apiOnly` con `EvidenceWriteGuard`: clave, hash, tamaño y tipo no cambian y la evidencia no
  se borra; una corrección es una evidencia nueva con `replaces`. No es `appendOnly` porque tiene vínculos MANY_TO_MANY con
  los registros que prueba [WAS-ADR-0040]; los vínculos con objetos `requiresReason` llevan motivo [WAS-ADR-0041]
  ([domain-model.md §6.12](../domain-model.md)).
- **Endpoints** de [api.md §4.9](../api.md): subida `multipart/form-data` que calcula SHA-256 y tamaño y lee los EXIF;
  descarga en streaming con `ETag` igual al SHA-256; cambio de datos descriptivos, nunca del archivo; vínculos y
  desvínculos. Ninguna URL externa se descarga en el servidor (ASM-050).
- **Control de descarga.** READ sobre `evidence` y, para CONFIDENCIAL y DATOS PERSONALES, la acción declarada
  `DOWNLOAD_RESTRICTED`, también para quien la subió; bajar esa clasificación exige la misma acción (RF-DOC-003, ASM-071)
  [WAS-ADR-0042]. Cambiar o vincular una evidencia ajena exige `EDIT_ANY` (ASM-069, ADR-0006).
- **Importaciones.** En F2 las cargas usan una carpeta temporal con límites (RQ-SEC-020); desde F3 el archivo original se
  guarda como `evidence` con su `import_batch_id` (ASM-060, ADR-0019).
- **Entregables.** Borradores en `deliverables/<id>/drafts/<instante>/`, versiones emitidas en `deliverables/<id>/v<n>/` y
  plantillas en `templates/<template_key>/<sha256>.dotx`, con los SHA-256 en `deliverable_version` (ADR-0013).
- **Retención.** Las evidencias y lo emitido no se purgan; lo emitido admite bloqueo WORM opcional de S3. JOB-08 conserva
  los N últimos borradores de cada entregable y borra los archivos sin referencia pasado un margen, nunca lo que referencia
  una `deliverable_version` o un `draft_storage_key` (RQ-REP-027, ASM-028)
  ([database-model.md §26](../database-model.md)).
- **Baja de un tenant.** Sus archivos se conservan hasta que se defina la retención (GAP-026, GAP-014).

## Consecuencias

### Positivas

- La base guarda metadatos, hashes y vínculos; los archivos grandes no la inflan.
- Una evidencia es verificable por su hash y su historia de reemplazos queda completa.
- El mismo puerto y la misma prueba de contrato sirven a evidencias, importaciones y entregables.

### Negativas y riesgos

- El almacenamiento de objetos contiene datos personales: su cifrado, su respaldo y su retención dependen del despliegue
  (GAP-022, GAP-014) ([security.md §11.6](../security.md)).
- Base y almacenamiento no comparten transacción: una emisión fallida deja archivos sin registro, que limpia JOB-08.
- Migrar al campo FILE, cuando exista, exige mover las claves de `evidence` (UPS-07).

### Seguimiento

- Abiertos: UPS-07, GAP-014, GAP-022, GAP-026.
- Se acepta al iniciar F3, cuando llegan `evidence` y el servicio S3 de desarrollo; se revisa si UPS-07 llega antes.
- Pruebas: la de contrato del puerto con las implementaciones local y S3 (MinIO); la de "Descargas" de
  [security.md §14](../security.md); las de JOB-08 en [workflows.md](../workflows.md) ("Pruebas de trabajos").

## Referencias

- Requisitos: RF-DOC-001, RF-DOC-003, RNF-PRIV-004, RNF-MNT-005, RQ-REP-017, RQ-REP-027, RQ-SEC-020, CON-046, ASM-013,
  ASM-028, ASM-050, ASM-060, ASM-069, ASM-071, UPS-07, GAP-014, GAP-022, GAP-026, GAP-028.
- Documentos: [deliverables.md §11](../deliverables.md), [domain-model.md §6.12](../domain-model.md),
  [database-model.md §16, §24 y §26](../database-model.md), [api.md §3.10 y §4.9](../api.md),
  [architecture.md §4 y §10](../architecture.md), [platform-fit.md §4.1 y §5](../platform-fit.md).
- wasichai: `WAS-ADR-0040` (Append-only objects, a pre-write guard SPI, and api-only objects), `WAS-ADR-0041` (A change
  reason on record writes, required per object), `WAS-ADR-0042` (An object declares its own actions, and they are granted,
  checked and listed like CRUD).
- ADR de SGSPE: ADR-0006, ADR-0013, ADR-0019.
- Fuentes: [PRM-01 §5], [DOC-01 §9.3.1.2, Tabla 9.5], [DOC-02 p.4], [USR-07].
