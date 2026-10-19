# ADR-0026: Topología de despliegue, puertos y observabilidad

## Estado

Aceptado en parte (en F1: puertos, correlation id, logs ECS y health checks; topología de producción: Propuesta) · 2026-10-07 · se acepta al responder GAP-022

## Contexto

- El destino de producción, el almacenamiento de objetos y los respaldos no están definidos (GAP-022), y no hay metas de
  disponibilidad ni de volumen (GAP-027).
- wasichai no trae infraestructura: sus librerías no asumen un despliegue y cada app fija el suyo, incluido el host del
  datastore de GeoServer [WAS-ADR-0033]. Se distribuye como librerías [WAS-ADR-0024], y una app es un solo `app.jar` [WAS-ADR-0033]; GeoServer es un
  publicador opcional que nunca está en la ruta de una petición [WAS-ADR-0006]; el trabajo de fondo usa `ClusterLock`, así
  que admite varias réplicas [WAS-ADR-0039].
- SGSPE necesita servicios de apoyo que llegan por fase: almacenamiento de objetos y SMTP (F3), GeoServer (F6), Gotenberg (F8) y,
  desde F7, un proveedor de LLM ([architecture.md §4](../architecture.md)).
- El entorno de desarrollo es compartido: el demonio Docker es remoto, alcanzado por un túnel SSH, y en él corren otras apps de
  wasichai; Testcontainers no alcanza sus puertos ([architecture.md §11](../architecture.md)).
- El prompt pide observabilidad en F1 sin detallarla [PRM-01 §24, §25] (RNF-OBS-001), y correlation ids en peticiones, respuestas
  y logs [PRM-01 §11] (RQ-AUD-006). wasichai no tiene identificador de correlación (UPS-03).

## Opciones consideradas

### Topología: frontend y API en orígenes distintos

- Ventajas: el frontend podría servirse desde una CDN.
- Desventajas: CORS, cookies o tokens entre orígenes y dos políticas de seguridad; complica la CSP de ADR-0020.

### Topología: orquestador de contenedores desde el inicio

- Ventajas: réplicas y reinicios gestionados.
- Desventajas: infraestructura sin requisito que la pida mientras GAP-022 siga abierto.

### Topología: un solo origen detrás de un proxy inverso, con los servicios de apoyo en una red interna (elegida)

- Ventajas: sin CORS; TLS, CSP y límite de tasa en un solo lugar; GeoServer y Gotenberg nunca expuestos; sirve igual en una
  máquina o en un orquestador.
- Desventajas: el proxy es una pieza más que configurar en cada entorno.

### Observabilidad: métricas y trazas distribuidas desde F1

- Ventajas: visibilidad completa desde el primer día.
- Desventajas: costo sin requisito ni volumen conocido; ASM-059 las difiere.

### Observabilidad: health checks, logs estructurados e identificador de correlación propio (elegida)

- Ventajas: cubre RNF-OBS-001 y RQ-AUD-006 con poco código, en `sgspe.observability`.
- Desventajas: la correlación no llega a las filas de auditoría hasta UPS-03.

## Decisión

**Aceptado en F1: puertos y entornos** ([architecture.md §11](../architecture.md)):

| Entorno | Dónde corre | Puertos |
|---|---|---|
| Desarrollo | Backend con `bootRun` y UI con Vite en la máquina local; PostGIS y los servicios de apoyo en el Docker remoto | Backend 8094, UI 5182, PostGIS 5435 |
| Pruebas de integración | CI con Testcontainers; en local, una base externa por túnel (`WASICHAI_TEST_DB_*`, nombre terminado en `_test`) | Aleatorios en CI; 5435 por túnel |
| CI | GitHub Actions en `sgspe` y en `sgspe-ui` | — |

- Los puertos 8094, 5182 y 5435 se eligieron libres frente a las otras apps de wasichai del mismo entorno. Vite reenvía `/api`
  al 8094: mismo origen, sin CORS.
- `compose.yml` usa la imagen `postgis/postgis:18-3.6`, volúmenes con nombre y ningún bind mount, porque el demonio es remoto;
  los servicios de apoyo se agregan en la fase que los usa, y el pico técnico de F1 los levanta solo para probar la ruta de
  entregables.
- Las credenciales de GitHub Packages van en variables de entorno, nunca en el repositorio.
- **Desde F6 (2026-10-11), GeoServer en la topología:** servicio `geoserver` (`sgspe-geoserver`), solo en loopback del
  VPS en el puerto **8095** (`SGSPE_GEOSERVER_PORT`; el contenedor escucha en 8080), sin CORS y nunca alcanzable por el
  navegador; solo el backend lo llama, por el proxy WMS y por `publish_layers.py` (ASM-121).
- **Desde F8 (2026-10-13), Gotenberg en la topología:** servicio `gotenberg` (`sgspe-gotenberg`, imagen
  `gotenberg/gotenberg:8.37.0-libreoffice`, la etiqueta exacta del plan 8.2c, con `--api-disable-download-from` y `--webhook-disable`),
  solo en el loopback del VPS en el puerto **8096** (`SGSPE_GOTENBERG_PORT`; el contenedor escucha en 3000), sin
  volúmenes, sin webhooks ni descargas remotas; solo lo llama JOB-05. Es también un servicio del trabajo de integración
  de la CI. `application-prod.yml` no da valor por defecto a `sgspe.deliverable.gotenberg.url`.
- **Desde el 2026-10-19 (ADR-0030), `sgspe-latex` en la topología, en lugar de Gotenberg:** servicio `latex`
  (`sgspe-latex`, imagen propia construida desde `develop/latex/Dockerfile` sobre `debian:trixie-slim` fijada por
  digest), solo en el loopback del VPS en el puerto **8097** (`SGSPE_LATEX_PORT`), sin volúmenes, con el sistema de
  archivos de solo lectura y sin red de salida; solo lo llama JOB-05, para componer con LuaLaTeX el PDF de los
  entregables (ASM-139). Es también un servicio del trabajo de integración de la CI (etiqueta `latex`).
  `application-prod.yml` no da valor por defecto a `sgspe.deliverable.latex.url`. Gotenberg se retira del compose, de la
  CI y de la configuración, y el 8096 queda libre, sin reutilizarse en esta fase.
- Puertos ya tomados en el entorno compartido, que no se reutilizan: 8094 (backend), 5182 (interfaz), 5435 (PostGIS),
  8095 (GeoServer), 8097 (`sgspe-latex`), 9095 y 9096 (MinIO y su consola), 1029 y 8029 (Mailpit y su web), 3094 y 8194
  (picos) y 8098 (medición de F5); del 8081 al 8086 son de otras apps del mismo Docker. El 8096, de Gotenberg hasta el
  2026-10-19, queda libre.

**Aceptado en F1: observabilidad** ([architecture.md §12](../architecture.md), [api.md §3.8](../api.md)):

- **Salud:** `/actuator/health/liveness` y `/readiness`, públicos.
- **Correlación:** `X-Correlation-Id` aceptado si cumple `^[A-Za-z0-9._-]{1,64}$` y generado como UUID si no; un valor inválido
  nunca se devuelve. El filtro de `sgspe.observability` va antes de la cadena de seguridad, así que toda respuesta lo lleva,
  también un `401`. Viaja en el contexto de Reactor, en la línea de acceso, en los logs de SGSPE y en las llamadas a `sgspe-latex`
  (antes, a Gotenberg) y a GeoServer (ASM-034).
- **Logs:** JSON estructurado (ECS) en producción, sin datos personales; una línea de acceso por petición con método, ruta sin
  la cadena de consulta, estado, duración e identificador ([security.md §11.6](../security.md)). Cada vuelta de un trabajo
  registra una línea por organización.
- Métricas y trazas distribuidas, diferidas (ASM-059). La auditoría de negocio vive en la base, no en los logs (RQ-AUD-014,
  ADR-0011).

**Propuesto: topología de producción** ([architecture.md §10](../architecture.md)):

- Un solo origen: el proxy inverso hace TLS, CSP y límite de tasa (ADR-0020), sirve `sgspe-ui/dist` con fallback de SPA
  (incluido `/admin/*`) y reenvía solo `/api/**` y `/actuator/health/**`. Solo el proxy expone 443.
- Red interna: una o más réplicas de la app sobre la misma base PostgreSQL 18 con PostGIS 3.6; GeoServer (ADR-0015) y
  `sgspe-latex` (ADR-0030; antes, Gotenberg de ADR-0013) solo alcanzables por la app y sin ruta en el proxy; GeoServer sin
  registro de cuerpos de petición ([gis.md §7.4](../gis.md)).
- Almacenamiento compatible con S3 en producción y MinIO solo en desarrollo, por su licencia AGPL (ADR-0014).
- Salidas a Internet: SMTP y, desde F7, el proveedor de LLM con texto seudonimizado (ADR-0003).
- Configuración de producción: `application-prod.yml` sin perfil `test`, `wasichai.seed.dev: false`, `WASICHAI_JWT_SECRET`
  obligatorio, TTL corto, logs ECS e IA desactivada por defecto ([architecture.md §10.2](../architecture.md)).
- Respaldos de la base y del almacenamiento de objetos, según la política que fije GAP-022.

## Consecuencias

### Positivas

- El navegador habla con un solo host; ni GeoServer ni `sgspe-latex` (antes, Gotenberg) quedan expuestos.
- Un `401` o un `500` se rastrea en los logs por su identificador, sin datos personales.
- La topología sirve con una réplica o con varias, sin cambios en el código.

### Negativas y riesgos

- La correlación no llega a `audit_log` ni a los logs propios de wasichai hasta UPS-03.
- Pruebas de integración lentas o imposibles en local por el Docker remoto; se mitigan con CI y la base externa
  ([architecture.md §14](../architecture.md), riesgo 10).
- Sin métricas, una degradación se detecta por los logs, no por alertas.
- La topología de producción puede cambiar cuando se responda GAP-022.

### Seguimiento

- Abiertos: GAP-022 (destino, almacenamiento y respaldos), GAP-027, GAP-011 (GeoServer en producción), UPS-03, ASM-059.
  GAP-015 quedó resuelta el 2026-10-12 (la API de Anthropic, `ai-architecture.md` §11).
- La parte aceptada rige desde F1; la topología de producción se acepta o se reemplaza al responder GAP-022.
- Pruebas: `ObservabilityApiTest` (identificador también en un `401`) y `ProdProfileTest` ([security.md §14](../security.md));
  la prueba de humo de F1 (arranque, login, roles y `/admin`; ASM-066).

## Referencias

- Requisitos: RNF-OBS-001, RQ-AUD-006, RQ-AUD-014, RQ-SEC-001, RQ-SEC-010, RQ-SEC-011, RQ-SEC-015, RQ-SEC-016, RNF-MNT-006;
  GAP-011, GAP-015, GAP-022, GAP-027; UPS-03; ASM-014, ASM-034, ASM-038, ASM-049, ASM-059, ASM-066.
- Documentos: [architecture.md §4, §10, §11, §12 y §14](../architecture.md), [api.md §3.8](../api.md),
  [security.md §3.2, §11.6 y §14](../security.md), [gis.md §7.4](../gis.md), [platform-fit.md §2.1 y §6](../platform-fit.md).
- ADR de SGSPE: ADR-0003, ADR-0010, ADR-0011, ADR-0013, ADR-0014, ADR-0015, ADR-0020, ADR-0024.
- WAS-ADR-0006 (GeoServer as publisher, not as the core), WAS-ADR-0024 (Wasichai ships as libraries: starters, a BOM and
  explicit auto-configuration), WAS-ADR-0033 (Samples and infrastructure live in their own repositories; the libraries are
  infra-agnostic), WAS-ADR-0039 (Background work runs RecordService as the platform, and takes a cluster lock).
- Fuentes: [PRM-01 §11, §24, §25], [USR-03], [USR-08].
