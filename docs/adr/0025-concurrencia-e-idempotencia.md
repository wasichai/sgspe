# ADR-0025: Concurrencia e idempotencia en la API del portal

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F2

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

- Los `POST` de `/api/sgspe/**` que crean registros (marca `IK`) admiten `Idempotency-Key`, de 1 a 64 caracteres, que el
  portal genera por acción del usuario y repite en cada reintento.
- Se honra desde F3, cuando llega la tabla fija `idempotency_key` ([database-model.md](../database-model.md)); en F2 la cabecera
  se acepta y se ignora, así que el portal la envía desde F2 sin cambiar después.
- Primera petición: se procesa como siempre. Reenvío con la misma ruta y el mismo SHA-256 del cuerpo: `200` sin escribir nada,
  con la respuesta reconstruida **releyendo** en su estado actual los registros que guarda la clave (`created_object`,
  `created_record_ids`); lo calculado que no se reconstruye se recalcula si se puede y, si no, se omite. La misma clave con otra
  petición: `409` que nombra `Idempotency-Key`.
- Dos peticiones simultáneas con la misma clave se serializan con `ClusterLock.withXactLock` sobre la clave.
- La clave vale por organización y usuario, se escribe en la transacción del comando, no guarda el cuerpo sino su SHA-256 y se
  purga después de un plazo configurable.
- Las altas de la API genérica no son idempotentes hasta UPS-18.

## Consecuencias

### Positivas

- Un cambio hecho sobre datos viejos se rechaza con un mensaje que pide recargar, en vez de pisar a otra persona.
- Un reintento de un comando no duplica registros ni repite efectos, en una réplica o en varias.
- Ningún objeto del modelo lleva campos técnicos de concurrencia o idempotencia.

### Negativas y riesgos

- Por la API genérica queda una ventana entre la comparación y la escritura hasta UPS-04
  ([architecture.md §14](../architecture.md), riesgo 5).
- Si la serialización de wasichai recorta la precisión de `updatedAt`, dos escrituras en la misma unidad no se distinguen.
- Un reenvío devuelve el estado actual de los registros, no el de la primera respuesta.
- Ningún trabajo de [workflows.md](../workflows.md) ("Trabajos programados") tiene asignada la purga de `idempotency_key`; queda
  por asignar.

### Seguimiento

- Abiertos: UPS-04 (F2) y UPS-18 (F3), que retiran la comprobación propia y la tabla; CON-047; GAP-028.
- Se acepta al iniciar F2 (precondición, y la cabecera de idempotencia aceptada); la idempotencia se honra desde F3.
- Pruebas: las de integración de cada endpoint de `/api/sgspe/**` contra PostGIS real (RNF-TST-001), que cubren `428`, `412`,
  el reenvío con `200` y la clave repetida con `409`.

## Referencias

- Requisitos: RNF-INT-002, RNF-INT-003, RNF-INT-004, RNF-TST-001; CON-047; UPS-04, UPS-18; GAP-028; ASM-035, ASM-065.
- Documentos: [api.md §3.5, §3.6 y §4](../api.md), [architecture.md §5.5, §6.2 y §14](../architecture.md),
  [database-model.md](../database-model.md) (`idempotency_key`), [platform-fit.md §4.1 y §5](../platform-fit.md).
- ADR de SGSPE: ADR-0008, ADR-0019 (confirmación de importaciones con `Idempotency-Key`), ADR-0021.
- WAS-ADR-0037 (Composite unique constraints, and a repeated unique value as a 409), WAS-ADR-0038 (RecordService joins the
  caller's transaction, and that is supported API), WAS-ADR-0039 (Background work runs RecordService as the platform, and
  takes a cluster lock), WAS-ADR-0040 (Append-only objects, a pre-write guard SPI, and api-only objects).
- Fuentes: [PRM-01 §11, §20].
