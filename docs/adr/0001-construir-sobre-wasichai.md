# ADR-0001: Construir SGSPE sobre wasichai

## Estado

Aceptado · 2026-10-07

## Contexto

- El prompt maestro fija una pila propia: Spring Web, Spring Data JPA e Hibernate, Flyway para todo cambio de esquema,
  Bean Validation, OpenAPI, ArchUnit, auditoría y RBAC propios, Spring AI y Embabel [PRM-01 §3, §19, §20].
- El usuario decidió construir SGSPE sobre wasichai y wasichai-ui, "tal como lo hicieron srtm-backend y srtm-ui", con
  caja-backend y caja-ui (wasichai 0.3.2, `@wasichai/*` 0.5.0) como plantilla vigente [USR-03] (RNF-MNT-001).
- wasichai es una plataforma guiada por metadata y distribuida como librerías y starters. Su forma la deciden sus propios
  ADR, que SGSPE cita y no repite (RNF-MNT-008): monolito modular (WAS-ADR-0001), la metadata como programa
  (WAS-ADR-0003), una tabla física por objeto (WAS-ADR-0004), librerías con starters y BOM (WAS-ADR-0024) y extensión por
  SPI (WAS-ADR-0025).
- La pila del prompt choca con esas reglas: sin JPA, Hibernate ni Spring Data; el DDL de negocio solo lo emite
  `ObjectSchemaManager`; nunca hay código por Custom Object (CON-039, CON-040). Además, la plataforma ya trae la
  seguridad, los usuarios, la auditoría y los errores que el prompt pone en F1 (CON-053).
- Este ADR registra qué significa para SGSPE esa decisión del usuario y qué deja abierto. El encaje mandato por mandato
  está en [platform-fit.md §2](../platform-fit.md).

## Opciones consideradas

### Pila del prompt (Spring Web, JPA, Flyway, auditoría y RBAC propios)

- Ventajas: es lo que el prompt describe literalmente; tipado en tiempo de compilación por entidad.
- Desventajas: contradice USR-03; reconstruye lo que la plataforma ya resuelve (login, tenancy, permisos, auditoría,
  workflows, GIS, agente de IA); cada entidad lleva controlador, servicio y repositorio, y un cambio de campo pasa por
  compilar y desplegar.

### Partir de srtm-backend y srtm-ui como plantilla

- Ventajas: srtm ya tiene las piezas de GIS, importación y emisión que SGSPE necesita.
- Desventajas: srtm sigue en wasichai 0.2.0 y arrastra rodeos que 0.3.x ya no necesita (escritura directa en tablas
  físicas por falta de `asPlatform`, decorador de almacenamiento, identidad de emisión) ([platform-fit.md §5](../platform-fit.md)).

### Construir sobre wasichai 0.3.2 con caja como plantilla (elegida)

- Ventajas: cumple USR-03; la metadata define tablas, API, validación y UI en tiempo de ejecución; SGSPE solo agrega
  metadata y código donde la metadata no alcanza; de srtm se toman solo las piezas que siguen vigentes.
- Desventajas: dependencia de una plataforma en versiones 0.x; brechas upstream (UPS) que SGSPE mitiga mientras
  tanto; algunas mitigaciones tocan detalles de la plataforma.

## Decisión

SGSPE es **una app Spring Boot con `@WasichaiApplication`** y los starters de wasichai, armada como caja, con el modelo
en `model/` y paquetes Kotlin `sgspe.*` propios (RNF-MNT-001) [USR-03].

| Aspecto | Qué decide SGSPE | Fuente |
|---|---|---|
| Versiones | wasichai 0.3.2 y `@wasichai/*` 0.5.0, fijadas exactas y actualizadas juntas; Kotlin 2.4.20, JDK 25 y Spring Boot 4.1.1 WebFlux del BOM | [platform-fit.md §6](../platform-fit.md), RNF-MNT-001 |
| Starters por fase | F1: core, views, forms y pages; F2: gis y workflow; F3–F4: automation y documents; F7: agent | [architecture.md §5.1](../architecture.md) |
| Plantilla | caja para el arranque, `model/`, `apply.py`, la API del portal, las pruebas de arquitectura y la CI; de srtm, solo GIS, importadores y emisión, sin sus rodeos de 0.2.0 | [platform-fit.md §5](../platform-fit.md) |
| Pila del prompt | Sustituida por la plataforma; cada mandato tiene su veredicto (Plataforma, SGSPE, Sustituido, Brecha, Diferido) | CON-039 |
| Alcance de F1 | Arranque de la app, roles como datos, pruebas de contrato con la plataforma, observabilidad y CI | CON-053, RNF-MNT-003 |
| Extensión | Solo por las SPI de wasichai (guardas, listeners, contribuidores); nunca se bifurca la plataforma | RNF-MNT-005 |
| Brechas | Lo que falta se registra como UPS con su mitigación; los issues upstream se abren solo con la aprobación del usuario | GAP-028 |

- La WebFlux de la plataforma obliga a que la E/S bloqueante del código propio (Apache POI, almacenamiento, GDAL) corra
  en `Dispatchers.IO` ([architecture.md §2.3](../architecture.md)).
- Qué código propio existe y cómo se organiza: ver ADR-0008. El modelo como código: ver ADR-0007. Repositorios: ver
  ADR-0010. Interfaz: ver ADR-0024. Despliegue y observabilidad: ver ADR-0026.

## Consecuencias

### Positivas

- Identidad, tenancy, permisos, auditoría, workflows, automatizaciones, documentos emitidos, GIS y el asistente de IA
  vienen de la plataforma; SGSPE se concentra en el dominio.
- Un campo nuevo es un cambio de metadata, no una entidad, un repositorio y un controlador nuevos (CON-040).
- Cada fase agrega sus starters y paquetes y deja el sistema ejecutable (RNF-MNT-003).

### Negativas y riesgos

- wasichai está en 0.x y sus líneas de backend (0.3.2) y frontend (0.5.0) son independientes: un cambio de contrato
  puede romper SGSPE ([architecture.md §14](../architecture.md), riesgo 2).
- Dieciocho brechas upstream, de UPS-01 (alcance de lectura por proyecto, crítica antes de la producción con varios
  proyectos) a UPS-18, con mitigaciones propias que se retiran cuando llegue la solución
  ([platform-fit.md §4.1](../platform-fit.md)).
- Acoplamiento vigilado a detalles de la plataforma: SQL de agregación sobre tablas físicas detrás de una compuerta de
  permisos y la enumeración de organizaciones de los trabajos, que sigue el precedente de caja hasta UPS-17
  ([architecture.md §14](../architecture.md), riesgo 3).
- Sin tipado por objeto en tiempo de compilación: las pruebas cubren los intérpretes de la metadata.

### Seguimiento

- Pruebas de contrato con la plataforma en F1 (forma de los errores, motivo, append-only, aislamiento de tenants,
  permisos), `ArchitectureTest` (sin código bajo `wasichai`, sin APIs internas) y `ErrorContractApiTest`
  ([architecture.md §13](../architecture.md), [security.md §14](../security.md)).
- Cada subida de versión de wasichai actualiza las dos líneas juntas y vuelve a correr las pruebas de contrato.
- Las brechas UPS-01 a UPS-18 se revisan en el checkpoint de F0 (GAP-028); UPS-17 antes de producción.

## Referencias

- Requisitos: RNF-MNT-001, RNF-MNT-003, RNF-MNT-005, RNF-MNT-008, CON-039, CON-040, CON-053, GAP-028, UPS-01, UPS-17,
  UPS-18.
- Documentos: [platform-fit.md](../platform-fit.md) §2, §4, §5 y §6; [architecture.md](../architecture.md) §2, §5.1, §13
  y §14; [requirements-analysis.md §17.3](../requirements-analysis.md).
- wasichai: WAS-ADR-0001 (*Modular monolith*), WAS-ADR-0003 (*Metadata-driven architecture*), WAS-ADR-0004 (*One
  physical table per Custom Object*), WAS-ADR-0024 (*Wasichai ships as libraries: starters, a BOM and explicit
  auto-configuration*), WAS-ADR-0025 (*The core is extended through SPIs, never by knowing its modules*), WAS-ADR-0032
  (*Rebrand to wasichai and split into two repositories*, versiones independientes).
- Fuentes: [USR-03], [PRM-01 §3, §19, §20, §26].
