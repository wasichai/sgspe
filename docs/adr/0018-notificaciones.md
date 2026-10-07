# ADR-0018: Notificaciones en la aplicación y abstracción del correo

## Estado

Propuesto · 2026-10-07 · se acepta al iniciar F3

## Contexto

- El prompt pide avisos en la aplicación y por correo (compromiso por vencer y vencido, plazo de queja, riesgo escalado,
  aprobación pendiente, tarea vencida), sin notificar desde las entidades sino desde eventos de aplicación [PRM-01 §12,
  §17] (RF-NTF-001, RN-NTF-001).
- wasichai no tiene módulo de notificaciones (CON-058, UPS-11), una vista de wasichai no puede filtrar por "yo", y las
  automatizaciones no tienen disparadores temporales (CON-043) [WAS-ADR-0016].
- Muchos avisos van a un rol, pero wasichai no le da al código una búsqueda de usuarios por rol ni roles por proyecto
  ([workflows.md](../workflows.md), "Destinatarios por rol"; CON-041, UPS-01), y el código no puede comparar nombres de
  rol (RQ-SEC-005).
- Un listener es atómico con la escritura solo si quien llama abrió una transacción [WAS-ADR-0038], y en PostgreSQL una
  sentencia fallida aborta toda la transacción de quien escribe ([domain-model.md §7.5](../domain-model.md)).
- No hay servidor de correo definido (GAP-021).

## Opciones consideradas

### Avisos como Custom Objects

- A favor: sin migraciones propias; la API genérica y `/admin` los verían.
- En contra: una bandeja "mis avisos" necesita filtrar por el usuario que llama, y una vista de wasichai no puede
  filtrar por "yo" ([domain-model.md §6.14](../domain-model.md)).

### Esperar el módulo upstream (UPS-11)

- A favor: nada propio que mantener.
- En contra: los avisos de plazos hacen falta en F3 (RF-NTF-001), sin plazo para la brecha (GAP-028).

### Tablas fijas propias y envío por SMTP en segundo plano

- A favor: una bandeja por usuario con deduplicación en la base; el envío no frena la petición; se migra al módulo
  upstream cuando exista (ASM-012).
- En contra: una migración Flyway y un paquete propios, y una baja de tenant que debe borrar sus filas (GAP-026).

## Decisión

Avisos en las tablas fijas `notification` y `notification_delivery` del esquema `sgspe`, más SMTP (ASM-012):

- **Tablas fijas.** No son Custom Objects: las crea la migración propia `sgspe_notification` registrada como
  `ModuleMigration` [WAS-ADR-0026], y solo las lee y escribe `sgspe.notification`, con SQL propio y siempre por
  `organization_id` ([database-model.md §2.3 y §18](../database-model.md)). `notification` es la bandeja: destinatario,
  tipo, registro de origen, título, cuerpo, `read_at` y una `dedup_key` única por organización y destinatario, que evita
  repetir el mismo aviso. `notification_delivery` es un envío por canal, con estado, intentos y error; en el MVP el único
  canal es EMAIL.
- **Quién crea avisos.** Solo eventos, nunca las entidades (RN-NTF-001): `NotificationListener` en altas y transiciones, y
  `DeadlineJob` (JOB-01) en los plazos y escalamientos (ADR-0012). Los tipos (`approval_pending`, `commitment_due_soon`,
  `commitment_overdue`, `qcs_deadline`, `risk_escalated`, `task_overdue`, `warning_new`) y sus claves están en
  [workflows.md](../workflows.md) ("Avisos"). Sin broker de mensajes ([platform-fit.md §2.10](../platform-fit.md)).
- **Un aviso nunca hace fallar la escritura que lo dispara.** `NotificationListener` inserta dentro de un savepoint o con
  una conexión independiente; si falla, registra el error y la escritura del usuario sigue. Es atómico con ella solo dentro
  de una transición o de un comando con transacción.
- **Destinatarios por rol** (ASM-070): un aviso nombra una transición, nunca un rol. Lo reciben las asignaciones vigentes
  del proyecto del registro (`project_assignment` activa y dentro de sus fechas) cuyo `role_name` está en la lista
  `roles` de esa transición, leída de `model/workflows/<objeto>.json` cargado como configuración; cambiar esos roles
  cambia los destinatarios sin tocar el código. `AssignmentRoleGuard` (`sgspe.scope`, ADR-0006) valida `role_name` contra `model/roles.json`.
  Quedan fuera quien aplicó la transición, `SUPER_ADMIN`, `ORGANIZATION_ADMIN` y las cuentas de servicio [WAS-ADR-0043].
  Sin coincidencias no se crea el aviso y queda una advertencia en el log.
- **Escalamiento.** En los niveles 1 y 2 de un compromiso o una QCS vencidos, JOB-01 crea además una tarea sin asignado,
  con `generator_key` único, que toma uno de los destinatarios (RF-WFL-004, ASM-030).
- **Correo.** `NotificationMailJob` (JOB-06) envía los `notification_delivery` pendientes por SMTP con reintentos, con un
  sondeo continuo y `ClusterLock` [WAS-ADR-0039]; no escribe registros de wasichai. El servidor SMTP es configurable, con
  uno de prueba en desarrollo (GAP-021). El envío está separado del aviso: `notification_delivery` lleva el canal, así
  que la bandeja no depende del correo.
- **Bandeja.** `GET /api/sgspe/notifications`, el conteo de no leídos y marcar como leído, solo sobre los avisos de quien
  llama ([api.md §4.10](../api.md)).
- **Datos personales.** Los avisos no pasan por los permisos de campo: su título y su cuerpo nombran el registro y el
  evento, nunca valores restringidos (ADR-0017); `error_detail` no lleva datos personales.

## Consecuencias

### Positivas

- Cada aviso llega una sola vez a las personas del proyecto con el rol de la siguiente decisión, sin nombres de rol en el
  código.
- Un aviso fallido nunca revierte una decisión de negocio.
- La salida al módulo upstream está acotada a un paquete y dos tablas.

### Negativas y riesgos

- Quien recibe un aviso actúa con sus roles de wasichai: si `role_name` no coincide con ellos, recibe el aviso y la
  transición le responde 403; quien forma los equipos mantiene las asignaciones al día.
- Un aviso que no se puede guardar se pierde: queda el error en el log y la escritura sigue.
- Las tablas fijas no se borran con la organización: la baja del tenant incluye un paso que las borra (GAP-026), y su
  retención espera GAP-014.
- JOB-01 y JOB-06 recorren los tenants con la excepción vigilada de UPS-17.

### Seguimiento

- Se acepta al iniciar F3, con los avisos de compromisos y tareas; los de QCS, riesgos y alertas se suman en F4.
- Abiertos: UPS-11, UPS-17, GAP-014, GAP-021, GAP-026; los valores de `notification_type` son una propuesta que se puede
  cambiar antes de F3 ([workflows.md](../workflows.md), "Avisos").
- Pruebas: "Destinatarios (ASM-070)" y "Listeners" de [workflows.md](../workflows.md) ("Pruebas de trabajos, listeners y
  automatizaciones"); "Destinatarios por rol" de [security.md §14](../security.md); `ArchitectureTest` no encuentra
  nombres de rol en `sgspe.notification`.

## Referencias

- Requisitos: RF-NTF-001, RN-NTF-001, RF-WFL-004, RQ-SEC-005, CON-041, CON-043, CON-058, ASM-012, ASM-030, ASM-070,
  UPS-01, UPS-11, UPS-17, GAP-014, GAP-021, GAP-026, GAP-028.
- Documentos: [workflows.md](../workflows.md) ("Listeners", "Avisos", "Destinatarios por rol", "Pruebas"),
  [domain-model.md §4.10, §6.14 y §7.5](../domain-model.md), [database-model.md §2.3, §18, §24 y §26](../database-model.md),
  [api.md §4.10](../api.md), [architecture.md §5.2 y §8](../architecture.md), [security.md §11.6](../security.md).
- wasichai: `WAS-ADR-0016` (Automations are queued work that runs as the platform), `WAS-ADR-0026` (Each module owns its migrations and its Flyway history),
  `WAS-ADR-0038` (RecordService joins the caller's transaction, and that is supported API), `WAS-ADR-0039` (Background work
  runs RecordService as the platform, and takes a cluster lock), `WAS-ADR-0043` (Service accounts sign in with client
  credentials, as a user row that is not a person).
- ADR de SGSPE: ADR-0006, ADR-0012, ADR-0017, ADR-0021.
- Fuentes: [PRM-01 §12, §17].
