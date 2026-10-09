# ADR-0025: Concurrencia e idempotencia en la API del portal

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F2. La idempotencia la reemplaza en F3 la decisión D3 del usuario
(2026-10-09): el contrato de la cabecera nativa de wasichai (WAS-ADR-0058), que describe abajo «Idempotencia». La
concurrencia la completa ADR-0027

## Contexto

- El prompt pide idempotencia cuando corresponda y bloqueo optimista [PRM-01 §11, §20].
- wasichai no tiene bloqueo optimista: su `PUT` reemplaza el registro entero y `version` es un nombre reservado sin columna, así
  que dos escrituras concurrentes se pisan y gana la última (CON-047, UPS-04).
- La API genérica no conoce `Idempotency-Key`: un reintento de una creación puede duplicar el registro (UPS-18). Los únicos
  compuestos del modelo evitan los duplicados que más importan [WAS-ADR-0037].
- La plataforma sí ofrece las piezas: `RecordService` se une a la transacción de quien llama [WAS-ADR-0038], y
  `ClusterLock.withXactLock` serializa sobre los candados consultivos de PostgreSQL en todas las réplicas [WAS-ADR-0039].
- Los comandos de `/api/sgspe/**` escriben varios registros en una transacción (ADR-0008); un reintento por una red inestable
  en terreno no debe crear dos compromisos ni dos reprogramaciones.
- caja ya resolvió la idempotencia de sus cobros con `Idempotency-Key` de 1 a 64 caracteres, guardando la clave en el recibo
  ([platform-fit.md §5](../platform-fit.md)).

## Opciones consideradas

### Aceptar el comportamiento de wasichai

- Ventajas: cero código propio.
- Desventajas: se pierden cambios en silencio y los reintentos duplican registros; incumple RNF-INT-002 y RNF-INT-003.

### Esperar UPS-04 y UPS-18

- Ventajas: una sola solución, en la plataforma.
- Desventajas: no tienen fecha (GAP-028), y F2 ya tiene escrituras concurrentes del equipo social.

### Precondición sobre `updatedAt` y la clave de idempotencia en un campo de cada registro creado, como caja

- Ventajas: sin tablas nuevas.
- Desventajas: agregaría un campo a todos los objetos que crean los comandos, y no sirve a un comando sin registro principal,
  como la confirmación de una importación (ASM-065).

### Precondición sobre `updatedAt` con `If-Match` y claves en la tabla fija `idempotency_key` (elegida)

- Ventajas: cubre comandos y API genérica sin tocar el modelo; la tabla se retira cuando llegue UPS-18.
- Desventajas: una tabla fija más; por la API genérica queda una ventana mínima entre comparar y escribir.

## Decisión

**Concurrencia** ([api.md §3.6](../api.md)) (RNF-INT-003, ASM-035):

- El portal envía `If-Match` con el `updatedAt` que leyó, entre comillas, como una etiqueta de entidad.
- En los comandos propios que cambian un registro que el cliente eligió (marca `IM` en [api.md §4](../api.md)), `If-Match` es
  obligatorio: sin él, `428`. El comando toma `ClusterLock.withXactLock` sobre `<objeto>:<id>`, relee el registro dentro de su
  transacción y compara; si no coincide, `412` con el valor actual en `errors[]`, sin escribir nada.
- En la API genérica (`PUT`, `DELETE`, transiciones y vínculos), un filtro web de `sgspe.common` pone la cabecera en el contexto
  de Reactor y una guarda de `sgspe.common` la compara antes de la escritura [WAS-ADR-0040]; `412` si no coincide.
- `/admin` no envía la cabecera: ahí sigue ganando la última escritura.
- Se compara el texto exacto que devuelve wasichai.

**Idempotencia** ([api.md §3.5](../api.md)) (RNF-INT-002, ASM-065):

- Los comandos de `/api/sgspe/**` marcados `IK` honran `Idempotency-Key`, de 1 a 128 caracteres ASCII imprimibles, que
  el portal genera por acción del usuario y repite en cada reintento.
- Desde F3, con el contrato de la cabecera nativa de wasichai (decisión D3, WAS-ADR-0058): la primera petición corre y
  su respuesta se guarda en la tabla fija `idempotency_key` en la misma transacción; un reenvío con la misma petición
  (mismo método, ruta y cuerpo) recibe esa respuesta byte por byte con `Idempotent-Replayed: true`, sin correr nada; la
  misma clave con otra petición es un `422`, y una petición con la clave en curso, un `409` con `Retry-After`, porque el
  candado de la clave no espera.
- La clave vale por organización y usuario, no guarda el cuerpo de la petición sino su SHA-256, y JOB-08 la purga
  después de `wasichai.idempotency.ttl`, el mismo plazo de la clave nativa.
- El alta de la API genérica la honra con la tabla nativa de wasichai (UPS-18 resuelta en 0.6.0).
- Antes de D3, esta decisión pedía 64 caracteres, un `409` para otra petición, esperar el candado y reconstruir el
  reenvío releyendo los registros (`created_object`, `created_record_ids`): todo eso se retiró.

## Consecuencias

### Positivas

- Un cambio hecho sobre datos viejos se rechaza con un mensaje que pide recargar, en vez de pisar a otra persona.
- Un reintento de un comando no duplica registros ni repite efectos, en una réplica o en varias.
- Ningún objeto del modelo lleva campos técnicos de concurrencia o idempotencia.

### Negativas y riesgos

- Por la API genérica queda una ventana entre la comparación y la escritura hasta UPS-04
  ([architecture.md §14](../architecture.md), riesgo 5).
- Si la serialización de wasichai recorta la precisión de `updatedAt`, dos escrituras en la misma unidad no se distinguen.
- Un reenvío devuelve la primera respuesta, no el estado actual de los registros; la tabla la guarda, con los campos que
  quien llamó podía leer, hasta el fin del plazo ([security.md §11.6](../security.md)).

### Seguimiento

- La purga de `idempotency_key` la hace JOB-08 desde F3 ([workflows.md](../workflows.md)).
- Abiertos: UPS-04 (F2) y UPS-18 (F3), que retiran la comprobación propia y la tabla; CON-047; GAP-028.
- Se acepta al iniciar F2 (precondición, y la cabecera de idempotencia aceptada); la idempotencia se honra desde F3.
- Pruebas: las de integración de cada endpoint de `/api/sgspe/**` contra PostGIS real (RNF-TST-001), que cubren `428`,
  `412`, el reenvío con los mismos bytes, la clave repetida con `422` y la clave en curso con `409`
  (`IdempotencyApiTest`).

## Referencias

- Requisitos: RNF-INT-002, RNF-INT-003, RNF-INT-004, RNF-TST-001; CON-047; UPS-04, UPS-18; GAP-028; ASM-035, ASM-065.
- Documentos: [api.md §3.5, §3.6 y §4](../api.md), [architecture.md §5.5, §6.2 y §14](../architecture.md),
  [database-model.md](../database-model.md) (`idempotency_key`), [platform-fit.md §4.1 y §5](../platform-fit.md).
- ADR de SGSPE: ADR-0008, ADR-0019 (confirmación de importaciones con `Idempotency-Key`), ADR-0021.
- WAS-ADR-0037 (Composite unique constraints, and a repeated unique value as a 409), WAS-ADR-0038 (RecordService joins the
  caller's transaction, and that is supported API), WAS-ADR-0039 (Background work runs RecordService as the platform, and
  takes a cluster lock), WAS-ADR-0040 (Append-only objects, a pre-write guard SPI, and api-only objects).
- Fuentes: [PRM-01 §11, §20].
