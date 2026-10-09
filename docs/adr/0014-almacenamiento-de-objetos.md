# ADR-0014: Evidencias y entregables en almacenamiento de objetos

## Estado

Aceptado · 2026-10-09 · decisión D1 del usuario: las evidencias usan el campo `FILE` de wasichai-files (UPS-07,
WAS-ADR-0061); el puerto propio queda para los entregables de F8

## Contexto

- SGSPE guarda archivos: evidencias que prueban un hecho (actas, fotos, comprobantes, cartas), con fecha, autor, hash y
  vínculos a varios registros [DOC-01 §9.3.1.2, Tabla 9.5] [DOC-02 p.4] (RF-DOC-001); los originales de cada importación
  (ASM-060); y desde F8 los borradores, las versiones emitidas y las plantillas de los entregables (RQ-REP-017, RQ-REP-027).
- wasichai no tenía campos FILE ni IMAGE (CON-046, UPS-07) [WAS-DOC domain/metadata-model.md]; desde la 0.6.0 los trae
  wasichai-files [WAS-ADR-0061].
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

Los archivos viven en almacenamiento de objetos; la base guarda su descriptor (nombre, tipo, tamaño, SHA-256) y nunca los
bytes (ASM-013). Desde wasichai 0.6.0 la plataforma lo ofrece (UPS-07, WAS-ADR-0061), y SGSPE lo adopta para las evidencias:

- **`evidence.file` es un campo `FILE` de wasichai-files.** Su columna nombra una fila de `stored_files` (organización,
  objeto y campo, clave `<organización>/<id>`, nombre del cliente, tipo detectado por sus bytes, tamaño, SHA-256, quién y
  cuándo). Un registro, su historial y sus listeners ven el descriptor `{id, name, contentType, size, sha256}`, nunca los
  bytes. El almacenamiento es el `FileStore` del módulo: un servicio compatible con S3 (`wasichai.files.store=s3`, AWS SDK
  v2) y MinIO solo en desarrollo, por su licencia AGPL ([architecture.md §10.1](../architecture.md)).
- **Subida en dos pasos.** El portal sube el archivo con `POST /api/objects/evidence/files/file` (wasichai-files: tope de
  tamaño, tipo detectado por sus bytes y comparado con la lista del campo, SHA-256, CREATE sobre `evidence`) y registra la
  evidencia con `POST /api/sgspe/evidence`, que la crea (es `apiOnly`) y la vincula en una transacción, con
  `Idempotency-Key` ([api.md §4.9](../api.md)). `StoredFileGuard` de wasichai solo deja adjuntar un archivo que subió la misma
  persona para `evidence.file`; un archivo que nadie adjunta lo borra la limpieza del módulo pasadas 24 horas.
- **`evidence` inmutable.** Es `apiOnly` con `EvidenceWriteGuard` (`sgspe.evidence`): el archivo, la versión a la que
  reemplaza y la importación de origen no cambian, y la evidencia no se borra; una corrección es una evidencia nueva con
  `replaces`. No es `appendOnly` porque tiene vínculos MANY_TO_MANY con los registros que prueba [WAS-ADR-0040]; los
  vínculos con objetos `requiresReason` llevan motivo [WAS-ADR-0041] ([domain-model.md §6.12](../domain-model.md)).
- **Control de descarga propio.** La descarga nativa de wasichai-files solo mira READ y los permisos de campo. SGSPE
  agrega la clasificación: `GET /api/sgspe/evidence/{id}/content` exige READ sobre `evidence` y, para CONFIDENCIAL y DATOS
  PERSONALES, la acción declarada `DOWNLOAD_RESTRICTED`, también para quien la subió; y la ruta nativa
  `GET /api/objects/evidence/records/{id}/files/{field}` la atiende un controlador de SGSPE con la misma comprobación (el
  segmento literal `evidence` es más específico que `{object}`). Bajar una clasificación restringida exige la misma acción
  (RF-DOC-003, ASM-071) [WAS-ADR-0042]. Cambiar o vincular una evidencia ajena exige `EDIT_ANY` (ASM-069, ADR-0006). La
  descarga responde `attachment`, `nosniff` y `ETag` igual al SHA-256.
- **Importaciones.** Desde F6, con los formatos de GDAL, el archivo original de una importación se guarda como `evidence`
  con su `import_batch_id` (ASM-060, ADR-0019); hasta entonces la importación lee de una carpeta temporal con límites
  (RQ-SEC-020).
- **Entregables.** Sus borradores, versiones y plantillas no son campos de un registro: en F8 usan un `FileStore` del mismo
  módulo o un puerto propio en `sgspe.deliverable`, que se decide con su plan; las claves y los SHA-256 siguen en
  `deliverable_version` (ADR-0013).
- **Retención.** Las evidencias no se purgan; un archivo que deja de nombrar un registro lo borra la limpieza de
  wasichai-files (`wasichai.files.cleanup.*`). JOB-08 conserva los N últimos borradores de cada entregable desde F8
  (RQ-REP-027, ASM-028) ([database-model.md §26](../database-model.md)).
- **Baja de un tenant.** `stored_files` no tiene clave foránea a la organización: la limpieza del módulo borra los archivos de
  un tenant borrado (WAS-ADR-0061).

## Consecuencias

### Positivas

- Sin código propio de almacenamiento ni de subida para las evidencias: tamaño, tipo, hash, permisos, auditoría y limpieza
  son de la plataforma.
- Una evidencia es verificable por su hash, que viaja en el registro, y su historia de reemplazos queda completa.
- Los permisos de la escritura del archivo son los del registro (WAS-ADR-0061): `apiOnly`, `EDIT_ANY` y el alcance por
  proyecto valen igual.

### Negativas y riesgos

- La descarga nativa no conoce la clasificación: SGSPE la reemplaza para `evidence`. Un objeto nuevo con un campo `FILE` y
  datos restringidos necesitará lo mismo.
- El archivo se sostiene en memoria mientras se valida (`wasichai.files.max-bytes`, 20 MB en SGSPE).
- El almacenamiento de objetos contiene datos personales: su cifrado, su respaldo y su retención dependen del despliegue
  (GAP-022, GAP-014) ([security.md §11.6](../security.md)).

### Seguimiento

- Abiertos: GAP-014, GAP-022, GAP-026. UPS-07 queda resuelta por wasichai 0.6.0.
- Pruebas: `EvidenceApiTest` (alta, inmutabilidad, reclasificación y las dos rutas de descarga); la de "Descargas" de
  [security.md §14](../security.md).

## Referencias

- Requisitos: RF-DOC-001, RF-DOC-003, RNF-PRIV-004, RNF-MNT-005, RQ-REP-017, RQ-REP-027, RQ-SEC-020, CON-046, ASM-013,
  ASM-028, ASM-050, ASM-060, ASM-069, ASM-071, UPS-07, GAP-014, GAP-022, GAP-026, GAP-028.
- Documentos: [deliverables.md §11](../deliverables.md), [domain-model.md §6.12](../domain-model.md),
  [database-model.md §16, §24 y §26](../database-model.md), [api.md §3.10 y §4.9](../api.md),
  [architecture.md §4 y §10](../architecture.md), [platform-fit.md §4.1 y §5](../platform-fit.md).
- wasichai: `WAS-ADR-0040` (Append-only objects, a pre-write guard SPI, and api-only objects), `WAS-ADR-0041` (A change
  reason on record writes, required per object), `WAS-ADR-0042` (An object declares its own actions, and they are granted,
  checked and listed like CRUD), `WAS-ADR-0061` (FILE and IMAGE fields, stored through a FileStore SPI and written as record
  writes).
- ADR de SGSPE: ADR-0006, ADR-0013, ADR-0019.
- Fuentes: [PRM-01 §5], [DOC-01 §9.3.1.2, Tabla 9.5], [DOC-02 p.4], [USR-07].
