# ADR-0018: Notificaciones en la aplicación y abstracción del correo

## Estado

Aceptado · 2026-10-09 · decisión D2 del usuario: los avisos los entrega wasichai-notifications (UPS-11, WAS-ADR-0046,
WAS-ADR-0047, WAS-ADR-0060); SGSPE decide los destinatarios y los textos

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

Desde wasichai 0.6.0 la plataforma tiene el módulo de notificaciones que faltaba (UPS-11): bandeja con estado por persona,
SSE, canal de correo con reintentos, preferencias y la acción `NOTIFY` de las automatizaciones. SGSPE lo adopta y no tiene
tablas ni bandeja propias (se retiran `notification`, `notification_delivery`, JOB-06 y `/api/sgspe/notifications*`):

- **SGSPE decide a quién y qué dice.** `sgspe.notification` publica con `Notifications.publish` del módulo, con una clave
  estable por aviso: publicar la misma clave no duplica (RF-NTF-001) y la bandeja muestra lo que sigue siendo cierto. Los
  textos van en español y nombran el registro y el evento, nunca valores restringidos (ADR-0017).
- **Quién crea avisos.** Solo eventos, nunca las entidades (RN-NTF-001): `ApprovalNoticeListener` al enviar un proyecto, un
  acta o un compromiso a aprobación (aviso `ACTION`, fuente `sgspe.approvals`, que se resuelve cuando el registro sale del
  estado pendiente), y `DeadlineNotices`, una fuente del módulo (`sgspe.deadlines`) que cada hora devuelve todos los avisos
  de plazo vigentes según lo que JOB-01 escribió; lo que deja de estar vencido se resuelve solo. Los avisos y sus claves
  están en [workflows.md](../workflows.md) ("Avisos").
- **Destinatarios por rol** (ASM-070): un aviso nombra una transición, nunca un rol. Lo reciben las asignaciones vigentes del
  proyecto del registro cuyo `role_name` está en la lista `roles` de esa transición, leída de `model/workflows/<objeto>.json`;
  cambiar esos roles cambia los destinatarios sin tocar el código. Quedan fuera quien aplicó la transición, `SUPER_ADMIN`,
  `ORGANIZATION_ADMIN` y las cuentas de servicio [WAS-ADR-0043]. Sin coincidencias no se crea el aviso y queda una advertencia
  en el log. El aviso va a personas concretas (`Audience.User`), nunca a un rol de wasichai: los roles de wasichai son del
  tenant, no del proyecto.
- **Transacción.** `Notifications.publish` se une a la transacción de quien escribe: el aviso se confirma o se revierte con la
  transición. Un aviso mal formado (un error de SGSPE) se registra y la escritura sigue; un destinatario que ya no existe lo
  descarta el módulo con una advertencia.
- **Correo.** Es el canal `email` del módulo (Spring Mail, `wasichai.notifications.email.*`), con reintentos y preferencias
  por persona; el servidor SMTP es configurable, con uno de prueba en desarrollo (GAP-021). Un aviso nunca lleva al correo un
  enlace `RECORD`: el módulo lo quita.
- **Bandeja.** La del módulo: `GET /api/auth/me/notifications`, su resumen, leer, descartar, posponer, leer todo y el flujo
  SSE ([api.md §2](../api.md)). La bandeja quita un enlace `RECORD` a quien no puede leer el objeto.
- **Escalamiento.** En los niveles 1 y 2 de un compromiso vencido, JOB-01 crea además una tarea sin asignado ni fecha, con
  `generator_key` único, que toma uno de los avisados (RF-WFL-004, ASM-030).

## Consecuencias

### Positivas

- Sin tablas, bandeja, envío por correo ni reintentos propios: SGSPE mantiene solo los destinatarios y los textos.
- Cada aviso llega una sola vez a las personas del proyecto con el rol de la siguiente decisión, sin nombres de rol en el
  código, y se resuelve solo cuando deja de ser cierto.

### Negativas y riesgos

- Un fallo de la base al guardar un aviso revierte la transición que lo dispara, como cualquier escritura de esa transacción.
- Un aviso de plazo llega hasta una hora después del cambio que lo causa (el intervalo de la fuente).
- Quien recibe un aviso actúa con sus roles de wasichai: si `role_name` no coincide con ellos, recibe el aviso y la
  transición le responde 403.

### Seguimiento

- Se acepta al iniciar F3, con los avisos de aprobación, compromisos y tareas; los de QCS, riesgos y alertas se suman en F4.
- Abiertos: GAP-014, GAP-021. UPS-11 queda resuelta por wasichai 0.6.0.
- Pruebas: `ApprovalNoticesApiTest`, `DeadlineJobApiTest` y `NoticesTest`; "Destinatarios (ASM-070)" de
  [workflows.md](../workflows.md); `ArchitectureTest` no encuentra nombres de rol en `sgspe.notification`.

## Referencias

- Requisitos: RF-NTF-001, RN-NTF-001, RF-WFL-004, RQ-SEC-005, CON-041, CON-043, CON-058, ASM-012, ASM-030, ASM-070,
  UPS-01, UPS-11, UPS-17, GAP-014, GAP-021, GAP-026, GAP-028.
- Documentos: [workflows.md](../workflows.md) ("Listeners", "Avisos", "Destinatarios por rol", "Pruebas"),
  [domain-model.md §4.10, §6.14 y §7.5](../domain-model.md), [database-model.md §2.3, §18, §24 y §26](../database-model.md),
  [api.md §4.10](../api.md), [architecture.md §5.2 y §8](../architecture.md), [security.md §11.6](../security.md).
- wasichai: `WAS-ADR-0016` (Automations are queued work that runs as the platform), `WAS-ADR-0026` (Each module owns its migrations and its Flyway history),
  `WAS-ADR-0038` (RecordService joins the caller's transaction, and that is supported API), `WAS-ADR-0039` (Background work
  runs RecordService as the platform, and takes a cluster lock), `WAS-ADR-0043` (Service accounts sign in with client
  credentials, as a user row that is not a person), `WAS-ADR-0046` (A notifications module), `WAS-ADR-0047` (Server push over SSE),
  `WAS-ADR-0060` (Delivery channels and automation NOTIFY).
- ADR de SGSPE: ADR-0006, ADR-0012, ADR-0017, ADR-0021.
- Fuentes: [PRM-01 §12, §17].
