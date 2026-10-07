# ADR-0020: Autenticación con el JWT de wasichai, endurecida

## Estado

Aceptado · 2026-10-07

## Contexto

- SGSPE tiene usuarios locales y usa la autenticación de wasichai [USR-06]. La plataforma ya decidió su mecanismo: usuarios con
  hash BCrypt, login que emite un JWT HS256 con `sub`, `org` y `roles`, validado como servidor de recursos WebFlux, con OIDC
  como camino futuro que solo cambia el decodificador y el login [WAS-ADR-0010]. Este ADR no repite esa decisión: fija cómo
  la usa SGSPE y qué le agrega.
- Lo que trae la plataforma ([security.md §3.1](../security.md)): token de 8 horas por defecto y configurable; arranque que
  falla sin `WASICHAI_JWT_SECRET` o con menos de 32 bytes; usuario semilla solo con `wasichai.seed.dev=true`; token guardado en
  `localStorage`; rutas públicas `/api/auth/login`, `/api/auth/token`, `/api/health` y `/actuator/health/**`.
- Lo que no trae: revocación de tokens, límite de intentos de login ni política de contraseñas (UPS-09). Un token robado vale
  hasta que vence, y deshabilitar a un usuario no corta su token vigente.
- Una decisión anterior del usuario pedía sesión con cookie HttpOnly y CSRF; el usuario aceptó después el JWT de wasichai con
  mitigaciones (CON-056).
- Los datos que protege SGSPE son sensibles: posturas de grupos de interés, territorio comunal, titulares de predios y quejas
  confidenciales ([security.md §2.1](../security.md)).

## Opciones consideradas

### JWT de wasichai tal como viene

- Ventajas: cero trabajo propio.
- Desventajas: 8 horas de vida con el token en `localStorage`, sin límite de intentos ni CSP: las amenazas 1, 2 y 4 de
  [security.md §2.3](../security.md) quedan sin mitigación.

### Sesión propia con cookie HttpOnly y CSRF

- Ventajas: el token no queda al alcance de un script inyectado.
- Desventajas: exige cambiar la cadena de seguridad y el cliente de `@wasichai/*`; reimplementa lo que la plataforma decide y
  rompe el login compartido con `/admin` (RF-IAM-001). Queda como propuesta upstream (CON-056).

### OIDC con un proveedor de identidad desde el MVP

- Ventajas: revocación, MFA y política de contraseñas del proveedor.
- Desventajas: infraestructura nueva sin requisito que la pida; el usuario fijó usuarios locales [USR-06]. wasichai lo deja
  listo para después [WAS-ADR-0010].

### JWT de wasichai con endurecimiento en la configuración y en el proxy (elegida)

- Ventajas: no toca la plataforma; cada brecha tiene su mitigación medible; el portal y `/admin` comparten el login.
- Desventajas: el riesgo de un token robado sigue hasta su vencimiento (UPS-09).

## Decisión

SGSPE usa el JWT de wasichai sin cambios y lo endurece así ([security.md §3.2](../security.md)):

| Medida | Detalle | Requisito |
|---|---|---|
| TTL corto en producción | `wasichai.security.jwt.ttl` fijado en `PT4H` por `application-prod.yml`; el despliegue puede cambiarlo (`WASICHAI_SECURITY_JWT_TTL`), pero `ProdGuard` rechaza 8 horas o más | RQ-SEC-001 |
| Perfil de producción | `wasichai.seed.dev=false` y `WASICHAI_JWT_SECRET` sin valor por defecto en `application-prod.yml`, más `ProdGuard` (abajo), que hace fallar el arranque si algo lo contradice | RQ-SEC-016 |
| CSP en el proxy | La política de [security.md §3.2](../security.md), primero en modo `Content-Security-Policy-Report-Only` y ajustada en F1 | RQ-SEC-001 |
| Límite de intentos | En el proxy, por dirección IP y por cuenta, sobre `/api/auth/login` y `/api/auth/token` | RQ-SEC-015, ASM-049 |
| Cabeceras | `Strict-Transport-Security`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` y `Cache-Control: no-store` en `/api/**` | RQ-SEC-001, RQ-SEC-010 |
| Sin contenido activo | Textos en Markdown sin HTML; evidencias como adjunto; redirección posterior al login solo a rutas internas | RF-DOC-001, RF-DOC-003 |
| Secretos fuera del repositorio | Variables de entorno o almacén de secretos del despliegue; nunca en el repositorio, los logs ni `ai_interaction` | RQ-AI-006, RQ-AI-009 |

- **Un solo login** para el portal y `/admin`: el mismo cliente, el mismo prefijo de almacenamiento `sgspe` y la misma clave del
  token. Un `401` a mitad de sesión vuelve al login [WAS-ADR-0031]. La interfaz la decide ADR-0024.
- **Guarda de arranque (`ProdGuard`, `sgspe.prod`):** con el perfil `prod` activo, el arranque falla con un mensaje que nombra
  la propiedad y la variable si ocurre cualquiera de estas tres cosas:
  - `wasichai.seed.dev` es verdadero (`WASICHAI_SEED_DEV=true`). Una variable de entorno gana a `application-prod.yml`, y
    `develop/example.env` trae `true`: sin la guarda, un arranque de producción con ese archivo crearía en la base el usuario
    `admin@wasichai.local`, porque en wasichai 0.3.2 la semilla es una migración de Flyway;
  - `wasichai.security.jwt.secret` falta o es el secreto de desarrollo de `application.yml`, que es público
    (`WASICHAI_JWT_SECRET`);
  - `wasichai.security.jwt.ttl` es de 8 horas o más, o falta (`WASICHAI_SECURITY_JWT_TTL`).

  Es un `EnvironmentPostProcessor` registrado en `META-INF/spring.factories`, con el último orden: corre cuando ya se
  cargaron `application-prod.yml`, las variables de entorno y los argumentos, y antes de crear el contexto. Flyway es un bean
  del contexto, así que la guarda llega antes de cualquier migración y, por tanto, antes de la semilla. Un bean con
  `@PostConstruct` llegaría tarde. El límite: la guarda solo actúa con el perfil `prod`; que el despliegue lo active
  (`SPRING_PROFILES_ACTIVE=prod`) sigue siendo una condición operativa.
- **Tenant del token:** la organización sale del claim `org`, nunca de la petición (RQ-SEC-002); la autorización la decide
  ADR-0006.
- **Cuentas de servicio** [WAS-ADR-0043]: solo para integraciones de sistema a sistema, que hoy no hay. Si GAP-020 abre el canal
  de contratistas, la cuenta recibe un rol declarado propio, nunca `ADMIN` ni roles de transición de WF-J o WF-K
  ([security.md §3.3](../security.md), RQ-SEC-014).
- **Topología:** las medidas que viven en el proxy (TLS, CSP, límite de intentos y cabeceras) rigen con el proxy de producción
  de la parte Propuesta de ADR-0026, que se acepta al responder GAP-022. En F1 solo se verifican en desarrollo, hasta donde lo
  permite el servidor de Vite; las de configuración (TTL, perfil de producción, secretos) rigen desde F1.
- **Futuro:** OIDC y la cookie HttpOnly quedan fuera del MVP; ningún issue upstream se abre sin la aprobación del usuario
  (GAP-028).

## Consecuencias

### Positivas

- La plataforma sigue siendo dueña de la autenticación; actualizar wasichai no exige tocar código de login.
- Un arranque con el perfil `prod` y el secreto por defecto, el usuario semilla o un token de 8 horas o más falla antes de las
  migraciones y de servir una petición (`ProdGuard`). Sin el perfil `prod`, nada lo impide.
- Las mitigaciones viven en configuración y en el proxy, y se retiran cuando UPS-09 llegue.

### Negativas y riesgos

- Token en `localStorage` expuesto a XSS y sin revocación: la CSP y el TTL corto acotan, no eliminan, el riesgo
  ([architecture.md §14](../architecture.md), riesgo 9).
- Deshabilitar a un usuario no corta su sesión hasta que el token vence.
- El TTL de producción queda fijado en `PT4H` por `application-prod.yml`, como tope provisional de F1. El despliegue puede
  cambiarlo, y la guarda solo exige que sea menor que 8 horas ([security.md §15](../security.md)).

### Seguimiento

- Abiertos: UPS-09 (antes de producción), CON-056 (cookie HttpOnly como propuesta upstream), GAP-020 (canal de contratistas),
  GAP-028.
- Aceptado desde F1. Se revisa si llega UPS-09 o si se adopta OIDC.
- Riesgo abierto: mientras GAP-022 no fije el proxy de producción, la CSP, el límite de intentos y las cabeceras no tienen
  dónde aplicarse fuera de desarrollo (ADR-0026).
- Pruebas de [security.md §14](../security.md): `SecurityApiTest` (credenciales erradas, usuario deshabilitado, token alterado o
  vencido), `ProdProfileTest` (el yaml del perfil), `ProdStartupTest` (la app de verdad no arranca con el secreto ausente o de desarrollo, la
  semilla encendida o un token de 8 horas) y `ProdGuardTest`, `TenantIsolationApiTest` y, desde F4, la de cuentas de
  servicio.

## Referencias

- Requisitos: RQ-SEC-001, RQ-SEC-002, RQ-SEC-010, RQ-SEC-014, RQ-SEC-015, RQ-SEC-016, RQ-SEC-017, RF-IAM-001, RF-DOC-001,
  RF-DOC-003, RQ-AI-006, RQ-AI-009; CON-056; UPS-09; GAP-020, GAP-022, GAP-028; ASM-049.
- Documentos: [security.md §2, §3, §4, §14 y §15](../security.md), [architecture.md §9.1, §10.2 y §14](../architecture.md),
  [api.md §1](../api.md).
- ADR de SGSPE: ADR-0006, ADR-0024, ADR-0026.
- WAS-ADR-0010 (Own JWT for the MVP, OIDC-ready), WAS-ADR-0031 (Where wasichai deliberately behaves differently from the
  original), WAS-ADR-0043 (Service accounts sign in with client credentials, as a user row that is not a person).
- Fuentes: [USR-03], [USR-06], [PRM-01 §7].
