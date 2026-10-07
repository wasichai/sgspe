# ADR-0008: Código propio solo donde la metadata no alcanza, y API del portal

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt pide package-by-domain sin capas `controller/service/repository` para todo, contextos con alta cohesión y
  bajo acoplamiento, comandos, consultas y políticas, sin lógica de negocio en los controladores, y rutas REST como
  `/api/projects/{id}/commitments` documentadas con OpenAPI [PRM-01 §3, §11, §19].
- En wasichai no hay código por Custom Object: la metadata define tablas, API, validación y UI (WAS-ADR-0003), y el núcleo
  se extiende por SPI: guardas, listeners y contribuidores (WAS-ADR-0025). Las apps de wasichai exponen la API genérica
  más `/api/<app>/**`, como `/api/caja/**` y `/api/srtm/**` [USR-03].
- Hay reglas que la metadata no expresa: comandos que escriben varios registros, cálculos al escribir, trabajos
  programados, archivos, geometrías con reglas, consultas agregadas y espaciales, entregables e IA (RNF-MNT-005). Las
  contradicciones son CON-040 (código por módulo), CON-048 (rutas) y CON-055 (OpenAPI).

## Opciones consideradas

### Controlador, servicio y repositorio por entidad (pila del prompt)

- Ventajas: estructura conocida; tipado por entidad.
- Desventajas: duplica lo que la plataforma ya sirve por metadata; contradice WAS-ADR-0003 (CON-040).

### Solo metadata, sin código propio

- Ventajas: nada que mantener fuera del modelo.
- Desventajas: sin comandos atómicos de varios registros, sin campos derivados, sin trabajos de plazos, sin análisis
  espacial ni entregables.

### Metadata primero y paquetes `sgspe.*` por capacidad, con `/api/sgspe/**` (elegida)

- Ventajas: el código existe solo donde hay un caso concreto; cada paquete tiene una razón escrita; la API propia
  complementa a la genérica sin repetirla.
- Desventajas: dos superficies de API que el portal combina; reglas de dependencia que hay que vigilar con pruebas.

## Decisión

**Metadata primero; código Kotlin propio solo donde la metadata no alcanza, en 19 paquetes `sgspe.*` por capacidad,
nunca un controlador por objeto** ([domain-model.md §8.2](../domain-model.md), [architecture.md §5.2](../architecture.md)).

| Paquetes | Por qué no basta la metadata | Desde |
|---|---|---|
| `sgspe.common`, `sgspe.observability` | Marca `SgspeWrite`, guardas transversales, correlativos, idempotencia, precondición, filtros con 422; correlación | F1 |
| `sgspe.scope` | Escritura acotada por asignación y edición de lo propio (ver ADR-0006) | F2 |
| `sgspe.stakeholder`, `sgspe.engagement`, `sgspe.territory` | Cálculos entre registros, reglas de cierre, geometrías que las guardas no ven | F2 |
| `sgspe.commitment`, `sgspe.agreement`, `sgspe.evidence`, `sgspe.deadline`, `sgspe.notification` | Comandos de varios registros, archivos sin campo FILE, plazos sin disparadores temporales, avisos sin módulo | F3–F4 |
| `sgspe.attention`, `sgspe.risk`, `sgspe.warning`, `sgspe.investment` | Reclasificación entre objetos, puntajes, reglas de alerta, topes y sumas | F4–F5 |
| `sgspe.dashboard`, `sgspe.privacy`, `sgspe.deliverable`, `sgspe.ai` | Agregaciones sin vistas materializadas, niveles de divulgación, DOCX y PDF, agentes | F2–F8 |

- **Reglas del código propio.** Nunca vive bajo el paquete `wasichai` ni usa `RecordStore`, `PhysicalTableRecordStore`,
  `AuditService` u `ObjectSchemaManager`; sin JPA, Hibernate ni Spring Data. Escribe con `RecordService` y
  `RelatedRecordService` en proceso, como el usuario y dentro de la transacción del comando (WAS-ADR-0038); como la
  plataforma, solo en trabajos programados (WAS-ADR-0039). Las reglas viven en guardas, servicios de comando y funciones
  puras; los controladores son delgados.
- **Dependencias en una sola dirección** ([architecture.md §5.3](../architecture.md)): los paquetes de dominio dependen
  de `sgspe.common` y `sgspe.scope`, nunca de `sgspe.dashboard`, `sgspe.deliverable` ni `sgspe.ai`; `sgspe.ai` depende
  del dominio a través de sus comandos; `sgspe.observability` no depende de ningún paquete de dominio. Una regla que mira
  otro contexto es una guarda del paquete dueño de la regla, que wasichai reúne sin que el paquete que escribe la
  importe (WAS-ADR-0040).
- **API del portal `/api/sgspe/**`** ([api.md §1 y §3](../api.md)):
  - existe solo para lo que la API genérica no puede: comandos de varios registros, cálculos al escribir, archivos,
    geometrías con reglas, consultas agregadas o espaciales y la bandeja de avisos; nunca un CRUD por objeto;
  - recursos en inglés, en plural y kebab-case; un comando es un subrecurso con `POST`; las consultas que cruzan objetos
    van bajo el prefijo de su capacidad (`territory`, `dashboards`, `attention`, `exports`, `ai`);
  - la misma forma de registro que la API genérica (`id`, `createdAt`, `updatedAt`, `state`, `attributes`,
    `geometries`), sin capa de DTO por objeto: propiedades de la API en camelCase y campos del modelo con su nombre;
  - errores como Problem Details del manejador de wasichai; paginación con los parámetros de wasichai; un parámetro de
    consulta desconocido es un 422 (ASM-054) y una propiedad desconocida en el cuerpo, un 400;
  - permisos comprobados antes de la primera escritura, para responder un 403 que nombra lo que falta y no un comando a
    medias;
  - un solo cliente, `sgspe-ui`, sin prefijo de versión: un cambio incompatible se coordina en los dos repositorios
    (RNF-MNT-006);
  - contrato en Markdown en `api.md`, como caja y srtm; OpenAPI queda como opción futura (CON-055).
- F1 no tiene endpoints propios: la API genérica más el filtro de correlación ([api.md §5](../api.md)).
- Idempotencia y concurrencia de `/api/sgspe/**`: ver ADR-0025. Correlación y logs: ver ADR-0026. Reglas de workflow y
  guardas: ver ADR-0021.

## Consecuencias

### Positivas

- La mayor parte de SGSPE es metadata; el código propio es pequeño y cada paquete tiene una razón.
- Tableros y entregables usan el mismo código de indicadores, y la IA nunca entra al dominio por la puerta de atrás.
- Las dos superficies comparten permisos, tenant y auditoría, porque el código propio escribe como el usuario.

### Negativas y riesgos

- Excepción vigilada a RNF-MNT-005: para recorrer las organizaciones, los trabajos siguen el precedente de caja
  (`CustomObjectRepository.findAllOrganizations()`), aislado en `sgspe.common` hasta UPS-17
  ([architecture.md §14](../architecture.md), riesgo 3).
- El SQL de agregación sobre tablas físicas depende de detalles de la plataforma; vive detrás de una compuerta de
  permisos y resuelve las tablas por la metadata.
- Sin OpenAPI, el cliente del portal se mantiene a mano.

### Seguimiento

- `ArchitectureTest` (escaneo de fuentes, como caja): sin código en `wasichai`, sin APIs internas, sin literales de rol,
  dominio sin dependencias de reportes ni de IA ([architecture.md §13](../architecture.md)).
- Pruebas de integración por endpoint contra PostGIS real con fixtures ficticios, y `ErrorContractApiTest`
  (RNF-TST-001, RNF-INT-001).
- UPS-17 antes de producción.

## Referencias

- Requisitos: RNF-MNT-005, RNF-MNT-006, RNF-INT-001, RNF-INT-004, RNF-TST-001, RQ-AUD-004, CON-040, CON-048, CON-055,
  ASM-054, UPS-17.
- Documentos: [domain-model.md](../domain-model.md) §8; [architecture.md](../architecture.md) §5.2, §5.3, §6 y §14;
  [api.md](../api.md) §1, §2, §3 y §5; [platform-fit.md §2.1, §2.9 y §2.14](../platform-fit.md).
- wasichai: WAS-ADR-0003 (*Metadata-driven architecture*), WAS-ADR-0025 (*The core is extended through SPIs, never by
  knowing its modules*), WAS-ADR-0038 (*RecordService joins the caller's transaction, and that is supported API*),
  WAS-ADR-0039 (*Background work runs RecordService as the platform, and takes a cluster lock*), WAS-ADR-0040
  (*Append-only objects, a pre-write guard SPI, and api-only objects*).
- Fuentes: [PRM-01 §3, §11, §19], [USR-03], [USR-09].
