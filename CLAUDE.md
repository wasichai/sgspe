# CLAUDE.md

Guía para agentes que trabajan en este repositorio. El diseño manda: `docs/` es la fuente de verdad y
`docs/requirements-analysis.md` es el único lugar donde se definen IDs (RF, RNF, RN, RQ, CON, GAP, ASM, UPS).

## Qué hay aquí

- Backend de SGSPE en la raíz: Spring Boot 4.1.1 WebFlux sobre wasichai 0.6.0 (`wasichai-bom`), Kotlin 2.4.20, JDK 25,
  Gradle 9.8.0. Paquetes propios `sgspe.*` (`docs/architecture.md` §5.2): `common`, `observability`, `scope`, `engagement`,
  `stakeholder`, `territory`, `dashboard`, `privacy`, `prod` y, desde F3, `commitment`, `agreement`, `evidence`, `investment`,
  `deadline` y `notification`.
- `model/`: `model.json` (los 72 objetos de F2 a F4), `roles.json`, `workflows/` (los 15 de F2 a F4: WF-A, WF-B, WF-C,
  WF-L, WF-O, WF-D, WF-I, WF-K, WF-N, WF-E1, WF-E2, WF-E3, WF-F, WF-J y WF-M), `document-types/`, `automations/` y los
  scripts de Python (solo biblioteca estándar) que los aplican por REST, más `load_ubigeo.py` y `design_docs.py`, que lee
  los documentos para las pruebas del modelo.
- `docs/`: análisis, diseño, ADR, trazabilidad, roadmap y planes. La interfaz vive en `../sgspe-ui`.

## Reglas

- Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009).
- Nunca versionar `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni nada de `~/Documents/gspe`. `git add` siempre con
  rutas explícitas.
- Ningún nombre real de persona en nada versionado; usuarios de prueba ficticios `@sgspe.test`.
- Conventional Commits con asunto en español y cuerpo de 100 columnas como máximo (commitlint con husky).
- Ningún código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; ningún literal de rol en `src/main/kotlin`. `ArchitectureTest` lo vigila, y desde F2 también que
  `scope` y `privacy` solo dependan de `common`, que `findAllOrganizations` no aparezca en ningún archivo y que
  `TenantDirectory` solo viva en `common/Organizations.kt`.
- Roles solo en `model/roles.json` y modelo solo en `model/model.json`: `/admin` no es fuente de verdad (GAP-030).
  Aprovisionar siempre en el orden `apply.py` → `apply_roles.py` → `apply.py --workflows` (workflows, tipos de documento y
  automatizaciones) → `load_ubigeo.py`. Por el
  túnel, los tres primeros pasos con el modelo de F2 tardan de 5 a 8 minutos, y la suite de integración completa, de
  30 minutos a más de una hora (la matriz de permisos, entre 11 y 58 minutos según la carga del túnel,
  aprovisionamiento incluido).
- Gradle: `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …`. El token nunca va a un archivo.
- Docker es remoto: las pruebas de integración corren en la CI o con `WASICHAI_TEST_DB_*` contra `sgspe_test` por el túnel
  del puerto 5435 (`docs/develop/README.md` §6).
- TDD: la prueba primero. `./gradlew ktlintFormat` antes de `build`; `yarn format` antes de commitear yaml o json.
- Puertos: backend 8094, interfaz 5182, PostGIS 5435; en desarrollo, MinIO 9095 (consola 9096) y Mailpit 1029 (web 8029).
- Archivos: `evidence.file` es un campo `FILE` de wasichai-files (D1, ADR-0014); un documento emitido congela el registro
  entero (UPS-23): ningún tipo de documento sobre un objeto con campos restringidos.
  La descarga de un campo `FILE` con datos restringidos pasa por SGSPE (`EvidenceContent`), e `IssuedDocumentsPrivacyTest`
  vigila la regla de los documentos del lado de la aplicación.

## Comandos

| Para | Comando |
|---|---|
| Build y pruebas unitarias | `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build` |
| Integración (base externa) | `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun` |
| Modelo | `cd model && python3 -m unittest -v && python3 apply.py --validate-only && python3 load_ubigeo.py --dry-run` |
| Trazabilidad | `python3 tools/check_traceability.py` |
| Formato | `yarn format:check` |

## Dónde está cada cosa

| Tema | Ruta |
|---|---|
| App | `src/main/kotlin/sgspe/SgspeApplication.kt`, `src/main/resources/application*.yml` |
| Correlación y línea de acceso | `src/main/kotlin/sgspe/observability/` |
| Marca de escritura, `Records`, Lima, 422, catálogo del modelo, reglas y guardas transversales, `If-Match`, consulta en seco, `Positions`, `Readability`, `RequestMemo`, JOB-02 | `src/main/kotlin/sgspe/common/` |
| Alcance por proyecto, asignaciones, destinatarios por rol, `EDIT_ANY`, WF-A | `src/main/kotlin/sgspe/scope/` |
| Evaluaciones, índices y grupo, prioridad, WF-B, JOB-03 | `src/main/kotlin/sgspe/stakeholder/` |
| WF-C, conteos de asistentes | `src/main/kotlin/sgspe/engagement/` |
| CRS, área oficial, áreas de influencia, importaciones, componentes, capas LYR-01 a LYR-16 (`sgspe.territory.import.*`, `sgspe.territory.layers.*`) | `src/main/kotlin/sgspe/territory/` |
| KPI-STK, KPI-ENG y tableros | `src/main/kotlin/sgspe/dashboard/` |
| Exportaciones con nivel de divulgación, celdas pequeñas (`SmallCells`) y `export_log` | `src/main/kotlin/sgspe/privacy/` |
| Idempotencia de los comandos (`Idempotency`, tabla `sgspe.idempotency_key`), JOB-08, `JobTasks`, `Amounts` | `src/main/kotlin/sgspe/common/`, `src/main/resources/db/sgspe/common/` |
| Compromisos: `CommitmentWriteGuard` y sus reglas, reprogramaciones, entregas, candidatos, derivados | `src/main/kotlin/sgspe/commitment/` |
| Acuerdos (`AgreementWriteGuard`) | `src/main/kotlin/sgspe/agreement/` |
| Evidencias sobre wasichai-files, descarga con `DOWNLOAD_RESTRICTED` | `src/main/kotlin/sgspe/evidence/` |
| Tope de apoyo social (`SupportPolicyGuard`) | `src/main/kotlin/sgspe/investment/` |
| JOB-01 y los avisos de plazo (`DeadlineNotices`) | `src/main/kotlin/sgspe/deadline/` |
| Avisos sobre wasichai-notifications (`Notices`, `ApprovalNoticeListener`) | `src/main/kotlin/sgspe/notification/` |
| Base de las pruebas de integración del plan 3.2 (sobre `F3ApiTest`: evidencias y compromisos de los comandos) y de las de `Idempotency-Key` sin el modelo de F3 | `src/test/kotlin/sgspe/F3DomainApiTest.kt`, `src/test/kotlin/sgspe/common/AssessmentCommandApiTest.kt` |
| Base de las pruebas de integración de F2 | `src/test/kotlin/sgspe/DomainApiTest.kt` |
| Límites de las importaciones: tamaño, filas, plazo `IMPORT_TIMEOUT` y concurrencia | `src/test/kotlin/sgspe/territory/ImportTimeoutApiTest.kt`, `ImportLimitsApiTest.kt` |
| Base de las pruebas de contrato | `src/test/kotlin/sgspe/SgspeApiTest.kt` y `src/test/kotlin/sgspe/contract/` |
| Base de las pruebas de F2 (registros mínimos, asignaciones, transiciones) | `src/test/kotlin/sgspe/F2ApiTest.kt` |
| Base de las pruebas de F3 del modelo (compromisos, acuerdos y tareas listos para sus guardas, altas apiOnly como la plataforma, espera de automatizaciones) | `src/test/kotlin/sgspe/F3ApiTest.kt` |
| Base de las pruebas de F4 (QCS, riesgos, incidentes, menciones, señales, reglas y alertas listos para las guardas de 4.2; revisiones de riesgo y derivados como la plataforma) | `src/test/kotlin/sgspe/F4ApiTest.kt` |
| Tipos de documento y automatizaciones (AUT-NN) | `model/document-types/`, `model/automations/`; `src/test/kotlin/sgspe/workflow/AutomationsApiTest.kt` |
| Workflows (`WorkflowMatrixApiTest` sale de `model/workflows/*.json`), matriz de permisos y permisos de campo | `src/test/kotlin/sgspe/workflow/`, `src/test/kotlin/sgspe/security/` |
| Modelo frente a los documentos | `model/test_model.py`, `model/design_docs.py` |
| Reglas de arquitectura | `src/test/kotlin/sgspe/ArchitectureTest.kt` |
| Plan de la fase en curso | `docs/superpowers/plans/` |
