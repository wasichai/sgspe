# ADR-0010: Repositorios `sgspe` y `sgspe-ui`

## Estado

Aceptado · 2026-10-07

## Contexto

- SGSPE se construye sobre wasichai y wasichai-ui, como srtm y caja, con caja-backend y caja-ui como plantilla vigente
  [USR-03] (RNF-MNT-001). Esas apps viven en dos repositorios: el backend con su modelo y la web aparte.
- El usuario fijó los repositorios: `wasichai/sgspe` lleva el backend y toda la documentación con los ADR, y
  `wasichai/sgspe-ui` lleva la web [USR-08] (RNF-MNT-006).
- wasichai ya separó su backend y su frontend en dos repositorios con líneas de versión independientes, y dejó toda su
  documentación en el repositorio del backend [WAS-ADR-0032]. Dentro de cada repositorio rige una sola versión para todos
  sus artefactos [WAS-ADR-0029] [WAS-ADR-0024].
- Por eso SGSPE depende de dos versiones que no avanzan juntas por sí solas: wasichai 0.3.2 (Maven, `wasichai-bom`) y
  `@wasichai/*` 0.5.0 (npm) ([platform-fit.md §6](../platform-fit.md)). Un cambio de contrato entre ellas es un riesgo
  registrado ([architecture.md §14](../architecture.md), riesgo 2).
- La web es una sola aplicación Vite con dos partes, el portal en `/` y `WasichaiApp` en `/admin`, con el mismo login
  ([architecture.md §9.1](../architecture.md)); esa división la decide ADR-0024 y no cambia el número de repositorios.

## Opciones consideradas

### Un solo repositorio con backend y web

- A favor: un cambio que cruza el contrato REST es un solo pull request, y una sola CI ve las dos partes.
- En contra: cada trabajo de CI mezcla Gradle y yarn, el mismo problema que llevó a wasichai a separarse
  [WAS-ADR-0032]; se aparta de la plantilla de caja y srtm y de la decisión del usuario [USR-08].

### Dos repositorios, con toda la documentación en `sgspe`

- A favor: sigue a caja, srtm y wasichai; cada repositorio tiene su propia cadena de herramientas y su CI; las
  decisiones viven en un solo lugar.
- En contra: un cambio que toca la API propia y el portal son dos pull requests, y las versiones de wasichai y de
  `@wasichai/*` deben actualizarse juntas a mano.

### Dos repositorios, cada uno con la documentación de su parte

- A favor: quien trabaja solo en la web encuentra sus decisiones al lado del código.
- En contra: el análisis, el modelo de dominio y los ADR son transversales; repartirlos duplica la fuente de verdad que
  la Fase 0 fija en `docs/` (RNF-MNT-008, RNF-MNT-009).

## Decisión

Dos repositorios, con toda la documentación y los ADR en `wasichai/sgspe` [USR-08] (RNF-MNT-006):

| Repositorio | Contenido | Plantilla | CI |
|---|---|---|---|
| `wasichai/sgspe` | App Spring Boot en la raíz (Kotlin 2.4.20, Gradle con el wrapper de caja), paquetes `sgspe.*`, `model/` con `model.json`, `roles.json` y los scripts de Python, `compose.yml` de desarrollo, `docs/` con el análisis, los diseños y `docs/adr/` | caja-backend | Build, integración, modelo y formato; Conventional Commits verificados |
| `wasichai/sgspe-ui` | Una aplicación Vite (React 19.3, `@wasichai/*` 0.5.0) con el portal y `/admin`; su README remite a la documentación de `sgspe` (propuesto, como wasichai-ui [WAS-ADR-0032]) | caja-ui | Lint, typecheck, pruebas y build |

- **Versiones fijadas y actualizadas juntas.** wasichai 0.3.2 y `@wasichai/*` 0.5.0 se fijan exactas; las demás
  dependencias, también exactas, son las de [platform-fit.md §6](../platform-fit.md). Una actualización de la plataforma
  sube las dos líneas en el mismo ciclo, y las pruebas de contrato con la plataforma de F1 detectan cambios de
  comportamiento ([architecture.md §13](../architecture.md)) (RNF-MNT-001) [WAS-ADR-0032].
- **El contrato entre repositorios es la API.** El portal habla con la API genérica de wasichai y con `/api/sgspe/**`,
  cuyo contrato fija `api.md` (ADR-0008); la web nunca importa código del backend.
- **Credenciales fuera del repositorio.** Las de GitHub Packages llegan por variables de entorno. Los paquetes Maven de
  wasichai son públicos y los npm son internos, así que la CI de `sgspe-ui` necesita un token de lectura de paquetes
  ([architecture.md §11](../architecture.md)).
- **Lo que nunca entra en `sgspe`:** `initial-promp.md`, los bocetos `arch-*.md`, `tech-landing.txt` y las fuentes; se
  citan por su ID y se excluyen localmente ([security.md §12](../security.md)) (RQ-SEC-018).
- **Reglas que cruzan los dos repositorios:** sin literales de rol en el backend ni en la web (`ArchitectureTest` y
  `boundaries.test`, RQ-SEC-005) y CI con acciones fijadas por SHA, como en caja ([platform-fit.md §5](../platform-fit.md)).
- Los puertos de desarrollo y la topología de cada entorno los fija ADR-0026.

## Consecuencias

### Positivas

- Cada repositorio se construye con una sola cadena de herramientas, como caja y srtm, y copia su andamiaje.
- Un solo lugar para las decisiones: el análisis, los diseños, la trazabilidad y los ADR viven en `sgspe`.
- La deriva entre las dos líneas de la plataforma se ve en las pruebas de contrato, no en producción.

### Negativas y riesgos

- Un cambio de la API propia que el portal usa son dos pull requests coordinados.
- wasichai está en versiones 0.x y sus dos líneas son independientes: una actualización a medias rompe el portal
  ([architecture.md §14](../architecture.md), riesgo 2).
- Dos conjuntos de CI y de secretos que mantener.

### Seguimiento

- Se materializa en F1: andamiaje de `sgspe` desde caja-backend y de `sgspe-ui` desde caja-ui (RNF-MNT-001, RNF-MNT-006).
- Se revisa en cada actualización de wasichai o de `@wasichai/*`; un cambio de esta decisión es un ADR nuevo
  (RNF-MNT-008).
- Pruebas que la fijan: las de contrato con la plataforma, `ArchitectureTest`, `boundaries.test` y las CI de los dos
  repositorios ([architecture.md §13](../architecture.md)) (RNF-TST-001).
- Pendiente relacionado: el destino de producción, que decide dónde se sirven los estáticos de `sgspe-ui` (GAP-022,
  ADR-0026).

## Referencias

- Requisitos: RNF-MNT-001, RNF-MNT-006, RNF-MNT-008, RNF-MNT-009, RNF-TST-001, RQ-SEC-005, RQ-SEC-018,
  GAP-022.
- Documentos: [architecture.md §2.1, §9, §11, §13 y §14](../architecture.md), [platform-fit.md §5 y §6](../platform-fit.md),
  [security.md §12](../security.md), [requirements-analysis.md §7](../requirements-analysis.md).
- wasichai: `WAS-ADR-0024` (Wasichai ships as libraries: starters, a BOM and explicit auto-configuration), `WAS-ADR-0029`
  (One repository, one version, published to GitHub Packages), `WAS-ADR-0032` (Rebrand to wasichai and split into two
  repositories).
- ADR de SGSPE: ADR-0001, ADR-0008, ADR-0024, ADR-0026.
- Fuentes: [USR-03], [USR-08], [PRM-01 §22].
