# CLAUDE.md

Guía para agentes que trabajan en este repositorio. El diseño manda: `docs/` es la fuente de verdad y
`docs/requirements-analysis.md` es el único lugar donde se definen IDs (RF, RNF, RN, RQ, CON, GAP, ASM, UPS).

## Qué hay aquí

- Backend de SGSPE en la raíz: Spring Boot 4.1.1 WebFlux sobre wasichai 0.3.2 (`wasichai-bom`), Kotlin 2.4.20, JDK 25,
  Gradle 9.8.0. Paquetes propios `sgspe.*` (`docs/architecture.md` §5.2); en F1, `sgspe.common` y `sgspe.observability`.
- `model/`: `model.json`, `roles.json` y los scripts de Python (solo biblioteca estándar) que los aplican por REST.
- `docs/`: análisis, diseño, ADR, trazabilidad, roadmap y planes. La interfaz vive en `../sgspe-ui`.

## Reglas

- Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009).
- Nunca versionar `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni nada de `~/Documents/gspe`. `git add` siempre con
  rutas explícitas.
- Ningún nombre real de persona en nada versionado; usuarios de prueba ficticios `@sgspe.test`.
- Conventional Commits con asunto en español y cuerpo de 100 columnas como máximo (commitlint con husky).
- Ningún código bajo el paquete `wasichai`; nunca `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni
  `ObjectSchemaManager`; ningún literal de rol en `src/main/kotlin`. `ArchitectureTest` lo vigila.
- Roles solo en `model/roles.json` y modelo solo en `model/model.json`: `/admin` no es fuente de verdad (GAP-030).
  Aprovisionar siempre en el orden `apply.py` → `apply_roles.py` → `apply.py --workflows`.
- Gradle: `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …`. El token nunca va a un archivo.
- Docker es remoto: las pruebas de integración corren en la CI o con `WASICHAI_TEST_DB_*` contra `sgspe_test` por el túnel
  del puerto 5435 (`docs/develop/README.md` §6).
- TDD: la prueba primero. `./gradlew ktlintFormat` antes de `build`; `yarn format` antes de commitear yaml o json.
- Puertos: backend 8094, interfaz 5182, PostGIS 5435.

## Comandos

| Para | Comando |
|---|---|
| Build y pruebas unitarias | `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build` |
| Integración (base externa) | `WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun` |
| Modelo | `cd model && python3 -m unittest -v && python3 apply.py --validate-only` |
| Trazabilidad | `python3 tools/check_traceability.py` |
| Formato | `yarn format:check` |

## Dónde está cada cosa

| Tema | Ruta |
|---|---|
| App | `src/main/kotlin/sgspe/SgspeApplication.kt`, `src/main/resources/application*.yml` |
| Correlación y línea de acceso | `src/main/kotlin/sgspe/observability/` |
| Marca de escritura, `Records`, Lima, 422 | `src/main/kotlin/sgspe/common/` |
| Base de las pruebas de contrato | `src/test/kotlin/sgspe/SgspeApiTest.kt` y `src/test/kotlin/sgspe/contract/` |
| Reglas de arquitectura | `src/test/kotlin/sgspe/ArchitectureTest.kt` |
| Plan de la fase en curso | `docs/superpowers/plans/` |
