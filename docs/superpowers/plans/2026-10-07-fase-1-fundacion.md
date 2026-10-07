# SGSPE · Fase 1 (Fundación): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea de código sigue superpowers:test-driven-development: prueba que falla, implementación mínima,
> prueba en verde, commit.

**Goal:** Dejar SGSPE compilando y ejecutable sobre wasichai 0.3.2 y `@wasichai/*` 0.5.0: la app `sgspe` con
`@WasichaiApplication`, el modelo como código con los 17 roles, las pruebas de contrato con la plataforma, la correlación y
el perfil de producción, las pruebas de arquitectura, la CI de los dos repositorios y `sgspe-ui` con portal, `/admin` y
login compartido.

**Architecture:** El backend vive en la raíz de `/Users/jorge/IdeaProjects/sgspe`, copiado de caja-backend@dc9e391: una
app Spring Boot WebFlux armada solo con starters de wasichai (core, `-views`, `-forms`, `-pages`), más dos paquetes propios
(`sgspe.observability` y la base de `sgspe.common`) y `model/` con los scripts de Python que aprovisionan un tenant en el
orden `apply.py` → `apply_roles.py` → `apply.py --workflows`. La interfaz vive en `/Users/jorge/IdeaProjects/sgspe-ui`,
copiada de caja-ui@a45b224: una sola app Vite cuyo `src/main.tsx` elige entre el portal (`/`) y `WasichaiApp` (`/admin`),
con el mismo cliente, el mismo `storagePrefix` `sgspe` y por tanto el mismo token `sgspe.token`. Sin endpoints propios en
F1: la API genérica de wasichai más el filtro `X-Correlation-Id`.

**Tech Stack:** Kotlin 2.4.20, Spring Boot 4.1.1 (WebFlux), JDK 25, Gradle 9.8.0, `wasichai-bom` 0.3.2, JUnit 5 con
`wasichai-test` (Testcontainers), PostgreSQL 18 con PostGIS 3.6, Python 3 (solo biblioteca estándar), React 19.3.0, Vite
8.3.1, Vitest 5.0.2, TypeScript 7.0.2, Tailwind 4.3.3, `@wasichai/*` 0.5.0, i18next 26.4.2, GitHub Actions.

**Spec:** `docs/roadmap.md`, sección «F1 · Fundación (detalle)»: pistas 0, A, B y C, definición de terminado, riesgos,
«Cambios respecto del plan aprobado» y «Respuestas del checkpoint». Donde el plan aprobado
(`docs/superpowers/plans/2026-10-06-sgspe-fase-0-y-1.md`, «Fase 1») difiere, gana el roadmap. Además:
`docs/security.md` §3.2, §4.1, §6, §13 y §14; `docs/architecture.md` §5, §9, §11, §12 y §13; `docs/platform-fit.md` §5 y §6;
`docs/api.md` §2, §3.3 y §3.8; ADR-0007, ADR-0010, ADR-0013, ADR-0015, ADR-0020, ADR-0024 y ADR-0026.

## Global Constraints

- **Repositorios.** Backend y documentos en `/Users/jorge/IdeaProjects/sgspe`, rama `feat/fase-1-fundacion` creada desde `fase-0/descubrimiento`. Interfaz en `/Users/jorge/IdeaProjects/sgspe-ui`: primero `chore: commit inicial` (vacío) en `main`, después todo el trabajo en `feat/fase-1-fundacion` creada desde ese `main`.
- **Plantillas.** caja-backend en el commit `dc9e391` y caja-ui en el commit `a45b224`. «Copiar de caja» significa `cp` desde esas rutas y luego exactamente las ediciones que lista el paso.
- **Versiones del backend.** `wasichai-bom` 0.3.2; starters `wasichai-spring-boot-starter`, `-views`, `-forms` y `-pages`; `wasichai-test` en pruebas; Kotlin 2.4.20; Spring Boot 4.1.1; plugin `org.jlleitschuh.gradle.ktlint` 14.2.0 con ktlint 1.7.1; JDK 25 (toolchain); Gradle 9.8.0 (wrapper de caja).
- **Versiones de la interfaz.** `@wasichai/core`, `ui`, `views`, `forms`, `pages` y `testing` en 0.5.0; React y React DOM 19.3.0; React Router 8.4.0; TanStack Query 5.104.0; i18next 26.4.2; react-i18next 17.0.15; Vite 8.3.1; Vitest 5.0.2; TypeScript 7.0.2; Tailwind CSS 4.3.3; Node 26 o superior; Yarn 1 (`yarn.lock` v1).
- **Base de datos.** Imagen `postgis/postgis:18-3.6` en el compose y en las pruebas de integración (`wasichai.test.db.image`).
- **Puertos.** Backend 8094, interfaz 5182, PostGIS 5435 (publicado en el loopback del VPS y traído por túnel SSH).
- **Bases.** Desarrollo: base `sgspe`, usuario `sgspe`, clave `sgspe` (solo loopback). Pruebas externas: base `sgspe_test` en el mismo contenedor.
- **Docker es remoto.** `DOCKER_HOST=unix:///tmp/docker.sock` es un socket reenviado por SSH al VPS: Testcontainers no alcanza sus contenedores desde esta máquina. Ningún `compose.yml` usa bind mounts; los puertos se publican en `127.0.0.1` del VPS y llegan con `ssh -fN -L`.
- **Alias del VPS.** `$SGSPE_VPS` es el alias SSH del VPS (el mismo host del reenvío de `/tmp/docker.sock`); lo exporta el usuario en su shell. Los pasos que abren túneles los ejecuta el usuario.
- **Pruebas de integración.** Corren en la CI (Testcontainers) y, en local, contra la base externa `sgspe_test` por el túnel, con las cinco variables `WASICHAI_TEST_DB_HOST=localhost`, `WASICHAI_TEST_DB_PORT=5435`, `WASICHAI_TEST_DB_NAME=sgspe_test`, `WASICHAI_TEST_DB_USERNAME=sgspe` y `WASICHAI_TEST_DB_PASSWORD=sgspe` (el nombre termina en `_test`; la suite la vacía una vez por JVM) y `--rerun`.
- **Sin túnel.** Si el túnel al 5435 no está disponible, las pruebas de integración pueden correr contra un clúster desechable de PostgreSQL 18 local (Homebrew `postgresql@18`, como la opción 3 de §6 en `docs/develop/README.md` de caja-backend): F1 no usa PostGIS. La CI sigue siendo la referencia.
- **Gradle.** Siempre `GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew …` desde `/Users/jorge/IdeaProjects/sgspe`. El token nunca se escribe en un archivo, ni en `develop/.env`.
- **npm.** El `.npmrc` del proyecto es solo la línea `@wasichai:registry=https://npm.pkg.github.com`, copiada de caja-ui. La autenticación vive en `~/.npmrc` (ya tiene la línea de `npm.pkg.github.com`); los comandos de instalación se corren como `NODE_AUTH_TOKEN=$(gh auth token) yarn install`.
- **CI de la interfaz.** El secreto `PACKAGES_TOKEN` (PAT con `read:packages`) lo carga el usuario en Actions y en Dependabot de `wasichai/sgspe-ui`.
- **Commits.** Conventional Commits con asunto en español, líneas del cuerpo de 100 columnas como máximo (commitlint), y al final, tras una línea en blanco, estas dos líneas exactas: `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y `Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U`.
- **Staging.** `git add` solo con rutas explícitas; nunca `git add -A` ni `git add .`. Nunca se versionan `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni nada de `~/Documents/gspe`.
- **Git y push.** Los implementadores hacen commits locales; `push` y PR solo en la Tarea 21, con la aprobación del usuario.
- **Datos personales.** Ningún nombre real. Usuarios de prueba ficticios con correo `@sgspe.test`; el único correo fuera de ese dominio es el administrador semilla de desarrollo `admin@wasichai.local` / `admin`.
- **Idioma.** Documentos, comentarios del código nuevo, mensajes y textos de interfaz en español (es-PE); lo copiado de caja conserva su idioma (los scripts de `model/` siguen en inglés). Nombres técnicos en inglés (ADR-0009): paquetes `sgspe.*`, clases, rutas propias en kebab-case, claves de las secciones.
- **Roles.** 15 declarados en `model/roles.json` más `SUPER_ADMIN` y `ORGANIZATION_ADMIN` en `mappedToAdmin` (17). Ningún literal de rol en `src/main/kotlin` ni en `sgspe-ui/src` fuera de las pruebas.
- **Fronteras con wasichai.** Ningún código bajo el paquete `wasichai`; nunca se importan `RecordStore`, `PhysicalTableRecordStore`, `AuditService` ni `ObjectSchemaManager`, tampoco en las pruebas.
- **Formato.** `./gradlew ktlintFormat` antes de cada `build`; Kotlin sin comas finales (`.editorconfig` de caja, 160 columnas); `yarn format` antes de `yarn lint` en las dos raíces de Node.
- **Python.** Solo biblioteca estándar, Python 3.11 o superior, como en caja.

## Review Focus

1. **Redirección después del login a otro sitio.** Un `next` como `//otro.example`, `/\otro.example` o `/` seguido de un tabulador y `/otro.example` (los navegadores leen las tres como otro host) debe llevar a `/`. Prueba: `safeNext` en `src/portal/auth/RequireSession.test.tsx` (Tarea 14).
2. **Identificador de correlación hostil.** Un `X-Correlation-Id` con saltos de línea, espacios, `<script>` o más de 64 caracteres nunca se devuelve ni se escribe en el log: se reemplaza por un UUID, y la línea de acceso no incluye la cadena de consulta. Pruebas: `CorrelationIdFilterTest` y `ObservabilityApiTest` (Tarea 8).
3. **Roles editados a mano en `/admin`.** Si alguien activa `ownRecordsOnly`, agrega un permiso o cambia una etiqueta, volver a correr `apply_roles.py` deja el rol como dice `roles.json`, y la corrida siguiente no escribe nada («15 skipped»). Prueba: `test_apply_roles.py` (Tarea 6).
4. **Sesión vencida a mitad de uso.** Un `401` en cualquier llamada del portal borra `sgspe.token` y vuelve al login, que después regresa a la ruta interna donde estaba. Prueba: `src/portal/auth/RequireSession.test.tsx` (Tarea 14).
5. **Segundo tenant.** Un tenant aprovisionado después recibe el modelo y los 15 roles en el orden fijo, sin errores y de forma idempotente, y no ve nada del primero. Pruebas: `TenantProvisioningApiTest` y `TenantIsolationApiTest` (Tarea 7).

## Decisiones de este plan frente a las plantillas

| # | Hallazgo en la plantilla o la plataforma | Decisión |
|---|---|---|
| 1 | `apply.py` de caja rechaza un `model.json` sin objetos («objects must be a non-empty list») | La copia acepta una lista vacía: F1 no tiene objetos (Tarea 6) |
| 2 | `test_apply.py` de caja está atado a los 13 objetos de caja | SGSPE escribe su `test_apply.py` sobre el modelo vacío y un modelo de prueba en `model/fixture_model.py` (Tarea 6) |
| 3 | `roles.json` de caja no tiene `"*"`, `mappedToAdmin`, `u` ni permisos de campo | `apply_roles.py` se reescribe a partir del de caja con esas reglas de `security.md` §6.3 (Tarea 6) |
| 4 | caja-backend no tiene una clase `ArchitectureTest`: sus pruebas de arquitectura escanean fuentes (`PurezaDeLaRecaudacionTest`) | `ArchitectureTest` sigue ese patrón de escaneo de fuentes (Tarea 10) |
| 5 | `CajaApiTest` porta `apply.py` a Kotlin y usa `PhysicalTableRecordStore`, una API interna | `SgspeApiTest` corre los scripts reales de `model/` contra la app de la prueba y no importa internos (Tarea 7) |
| 6 | caja-ui usa Renovate; el roadmap pide `PACKAGES_TOKEN` en Dependabot | `sgspe-ui` usa Dependabot con un registro npm autenticado por `PACKAGES_TOKEN` (Tarea 19) |
| 7 | En wasichai 0.3.2 el `401` sin token lo emite el punto de entrada de Spring Security: `WWW-Authenticate: Bearer` y cuerpo vacío, no `problem+json` (`api.md` §3.3 dice Problem Details) | `ErrorContractApiTest` fija el comportamiento real (cuerpo vacío más `WWW-Authenticate`) y `problem+json` en el `401` del login; si wasichai cambia, la prueba avisa (Tarea 7) |
| 8 | `safeNext` de caja-ui acepta `/\otro.example` | El de SGSPE lo rechaza, con los caracteres de control (Tarea 14) |
| 9 | caja prueba contra `postgres:18` | SGSPE fija `postgis/postgis:18-3.6` en `integrationTest` y en la CI (Tareas 3 y 11) |
| 10 | En wasichai 0.3.2 un atributo que el objeto no tiene se ignora al crear un registro (`201`, sin guardarlo); `api.md` §3.3 y la documentación de wasichai hablan de `400` por «campo desconocido» | `ErrorContractApiTest` prueba el `400` con un valor inválido y fija aparte que el atributo desconocido se ignora; el código de SGSPE manda solo campos del modelo (Tarea 7) |

---

## Mapa de archivos

### `/Users/jorge/IdeaProjects/sgspe` (backend en la raíz)

| Ruta | Responsabilidad | Tarea |
|---|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*` | Build y wrapper de caja; tareas `test` e `integrationTest` | 3 |
| `.gitignore`, `.editorconfig` | Copiados de caja | 3 |
| `src/main/kotlin/sgspe/SgspeApplication.kt` | La app con `@WasichaiApplication` | 3 |
| `src/main/resources/application.yml` | Puerto 8094, base por defecto, semilla y secreto de desarrollo, sondas | 3 |
| `src/test/kotlin/sgspe/SgspeSmokeTest.kt` | Arranque, módulos instalados y no instalados | 3 |
| `compose.yml`, `develop/example.env`, `docs/develop/README.md` | PostGIS en el VPS, variables, guía de desarrollo | 1, 4 |
| `package.json`, `yarn.lock`, `commitlint.config.mjs`, `.husky/commit-msg`, `.prettierrc.json`, `.prettierignore`, `SECURITY.md` | Herramientas de Node y política de seguridad | 5 |
| `model/model.json`, `model/roles.json` | Modelo vacío de F1 y los 15 roles | 6 |
| `model/apply.py`, `model/apply_roles.py`, `model/core_client.py`, `model/fake_core.py`, `model/fixture_model.py` | Aprovisionamiento y su doble de prueba | 6 |
| `model/test_apply.py`, `model/test_apply_roles.py` | Pruebas de los scripts | 6 |
| `src/test/kotlin/sgspe/SgspeApiTest.kt` | Base de las pruebas de contrato: aprovisiona con los scripts reales | 7 |
| `src/test/kotlin/sgspe/contract/*ApiTest.kt` | `TenantIsolationApiTest`, `TenantProvisioningApiTest`, `SecurityApiTest`, `RolesApiTest`, `AuditApiTest`, `ErrorContractApiTest` | 7 |
| `src/main/kotlin/sgspe/observability/CorrelationId.kt`, `CorrelationIdFilter.kt` | Correlación y línea de acceso | 8 |
| `src/main/resources/application-prod.yml` | Perfil de producción | 8 |
| `src/test/kotlin/sgspe/observability/*Test.kt`, `src/test/kotlin/sgspe/ProdProfileTest.kt` | Pruebas de la correlación y del perfil | 8 |
| `src/main/kotlin/sgspe/common/SgspeWrite.kt`, `Records.kt`, `Lima.kt`, `StrictQuery.kt` | Base de `sgspe.common` | 9 |
| `src/test/kotlin/sgspe/common/*Test.kt` | Pruebas de `sgspe.common` | 9 |
| `src/test/kotlin/sgspe/ArchitectureTest.kt` | Reglas de arquitectura por escaneo de fuentes | 10 |
| `.github/workflows/ci.yml`, `.github/workflows/commits.yml`, `.github/dependabot.yml` | CI | 11 |
| `tools/check_traceability.py`, `tools/test_check_traceability.py` | Toda RF, RNF, RN y RQ tiene fila en la matriz | 11 |
| `docs/adr/0013-entregables-docx-primero.md`, `docs/adr/0015-mapas-de-informe-con-geoserver.md` | Resultado del spike en «Seguimiento» | 2 |
| `README.md`, `CLAUDE.md`, `docs/README.md`, `docs/HISTORY.md`, `docs/requirements-traceability.md`, `docs/roadmap.md` | Cierre documental de F1 | 21 |

### `/Users/jorge/IdeaProjects/sgspe-ui`

| Ruta | Responsabilidad | Tarea |
|---|---|---|
| `package.json`, `yarn.lock`, `.npmrc`, `.editorconfig`, `.gitignore`, `.prettierrc.json`, `commitlint.config.mjs`, `.husky/commit-msg`, `tsconfig.json`, `tsconfig.build.json`, `vite.config.ts`, `index.html`, `SECURITY.md` | Andamiaje copiado de caja-ui | 12 |
| `src/main.tsx`, `src/index.css`, `src/test/setup.ts`, `src/viteConfig.test.ts` | Entrada, estilos y configuración de pruebas | 12 |
| `src/admin/AdminApp.tsx`, `src/admin/AdminApp.test.tsx` | `/admin` con `WasichaiApp` | 13 |
| `src/portal/api.ts`, `src/portal/PortalApp.tsx`, `src/portal/i18n.ts`, `src/portal/textos/es.ts` | Cliente, app del portal e i18n | 14 |
| `src/portal/auth/LoginPage.tsx`, `src/portal/auth/RequireSession.tsx`, `src/portal/auth/RequireSession.test.tsx`, `src/portal/login.test.tsx` | Login compartido, sesión y `safeNext` | 14 |
| `src/portal/shell/AppShell.tsx`, `src/portal/pages/InicioPage.tsx`, `src/test/portal.ts` | Marco del portal, inicio y ayudas de prueba | 14, 16, 18 |
| `csp.ts`, `src/csp.test.ts`, `src/themes.test.ts`, `src/portal/shell/ThemeMenu.tsx`, `src/portal/shell/ThemeMenu.test.tsx` | Temas claro y oscuro, script de arranque y CSP | 15 |
| `src/portal/shell/navTree.ts`, `src/portal/pantallas.ts`, `src/portal/shell/GuardaDeSeccion.tsx`, `src/portal/shell/Navigation.test.tsx` | Árbol de navegación de las 13 secciones | 16 |
| `src/portal/textos/en.ts`, `src/portal/shell/LanguageMenu.tsx`, `src/portal/I18n.test.tsx` | Inglés, números y fechas es-PE | 17 |
| `src/portal/queries.ts`, `src/portal/pages/Inicio.test.tsx` | Dashboard de F1 | 18 |
| `.github/workflows/ci.yml`, `.github/workflows/commits.yml`, `.github/dependabot.yml`, `src/boundaries.test.ts` | CI y fronteras | 19 |
| `README.md`, `CLAUDE.md`, `HISTORY.md` | Cierre documental | 21 |

### Fuera de los repositorios

| Ruta | Responsabilidad | Tarea |
|---|---|---|
| `/Users/jorge/IdeaProjects/sgspe-spike/` | Spike desechable de POI, Gotenberg y GetMap; nunca es un repositorio y se borra al terminar | 2 |

## Orden y paralelismo

| Pista | Tareas | Quién | Depende de |
|---|---|---|---|
| 0 · Precondiciones | 1 | Coordinador y usuario | Checkpoint aprobado |
| Spike | 2 | Un implementador propio (o el coordinador) | Tarea 1 (Docker remoto) |
| A · `sgspe` | 3, 4, 5, 6, 7, 8, 9, 10, 11 (en ese orden) | Un implementador en `sgspe` | Tarea 1 |
| B · `sgspe-ui` | 12, 13, 14, 15, 16, 17, 18, 19 (en ese orden) | Un implementador en `sgspe-ui` | Tarea 1 |
| C · Verificación | 20 | Coordinador | Pistas A y B en verde |
| Cierre | 21 | Coordinador | Tarea 20; Tarea 2 para los ADR |

Las pistas A y B corren en paralelo, un implementador por repositorio: cada repositorio es ya un árbol de trabajo
aislado, así que cada implementador trabaja directamente en `/Users/jorge/IdeaProjects/sgspe` o en
`/Users/jorge/IdeaProjects/sgspe-ui` (las rutas absolutas del plan), y el coordinador no toca esos directorios mientras
corre su pista. La Tarea 2 toca solo `/Users/jorge/IdeaProjects/sgspe-spike` y, al final, dos ADR de `sgspe`, que se
commitean entre dos tareas de la pista A, nunca con una tarea a medias.

---

### Tarea 1: Pista 0 · Precondiciones (T0.1 a T0.4)

**Pista:** 0. **Responsable:** coordinador; los pasos marcados **[USUARIO]** los hace o los aprueba el usuario.

**Files:**
- Create (sin commit todavía; se versiona en la Tarea 4): `/Users/jorge/IdeaProjects/sgspe/compose.yml`
- Modify (local, no versionado): `/Users/jorge/IdeaProjects/sgspe/.git/info/exclude` (solo comprobar)

**Interfaces:**
- Consumes: el checkpoint aprobado (`docs/roadmap.md`, «Respuestas del checkpoint»).
- Produces: rama `feat/fase-1-fundacion` en los dos repositorios; PostGIS de desarrollo en `localhost:5435` por túnel, con
  las bases `sgspe` y `sgspe_test`; `PACKAGES_TOKEN` en Actions y Dependabot de `wasichai/sgspe-ui`.

- [ ] **Paso 1 (T0.1): exclusiones y rama en `sgspe`**

```bash
cd /Users/jorge/IdeaProjects/sgspe
grep -cE '^/(initial-promp\.md|arch-concept\.md|arch-flow\.md|arch-init\.md|tech-landing\.txt)$' .git/info/exclude
git status --short
git switch fase-0/descubrimiento
git switch -c feat/fase-1-fundacion
```

Esperado: `5`; `git status --short` vacío; `Switched to a new branch 'feat/fase-1-fundacion'`.

- [ ] **Paso 2 (T0.1): commit inicial y rama en `sgspe-ui`**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git status --short
git commit --allow-empty -F - <<'EOF'
chore: commit inicial

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
git log --oneline
```

Esperado: `git status --short` vacío (el repositorio no tiene archivos); el log muestra un solo commit,
`chore: commit inicial`, en `main`.

- [ ] **Paso 3 [USUARIO]: publicar `main` de `sgspe-ui`**

El PR de la Tarea 21 necesita que `main` exista en el remoto. Con la aprobación del usuario:

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git push -u origin main
git switch -c feat/fase-1-fundacion
```

Esperado: `branch 'main' set up to track 'origin/main'` y `Switched to a new branch 'feat/fase-1-fundacion'`.

- [ ] **Paso 4 (T0.2): credenciales de GitHub Packages**

```bash
gh auth status 2>&1 | grep -i "token scopes"
gh api user -q .login >/dev/null && echo actor-ok
grep -c '//npm.pkg.github.com/:_authToken' ~/.npmrc
```

Esperado: la línea de alcances incluye `read:packages`; `actor-ok`; `1` (la línea de autenticación de npm existe en
`~/.npmrc`; no se imprime su contenido). Si falta `read:packages`: **[USUARIO]** `gh auth refresh -s read:packages`.

- [ ] **Paso 5 (T0.3) [USUARIO aprueba: infraestructura compartida]: comprobar el Docker remoto y los puertos**

```bash
docker info --format '{{.ServerVersion}}'
docker ps --format '{{.Names}} {{.Ports}}' | grep -E ':(5435|3094|8194)->' || echo puertos-libres
```

Esperado: una versión del servidor y `puertos-libres`. Si `docker info` falla con «Cannot connect to the Docker daemon»,
**[USUARIO]** vuelve a abrir el reenvío del socket `/tmp/docker.sock` hacia `$SGSPE_VPS` como lo hace siempre.

- [ ] **Paso 6 (T0.3): escribir `compose.yml`**

Crear `/Users/jorge/IdeaProjects/sgspe/compose.yml` (adaptado de caja-backend@dc9e391 `compose.yml`: imagen PostGIS,
nombre `sgspe`, puerto 5435):

```yaml
name: sgspe

services:
  # postgis desde F1: instalar wasichai-gis en F2 solo crea la extensión, sin migrar datos (architecture.md §5.1).
  # los servicios de apoyo llegan con su fase: S3 y SMTP de prueba en F3, GeoServer en F6 y Gotenberg en F8
  postgis:
    image: postgis/postgis:18-3.6
    container_name: sgspe-postgis
    environment:
      POSTGRES_USER: sgspe
      POSTGRES_PASSWORD: sgspe
      POSTGRES_DB: sgspe
    ports:
      # 5435, nunca 5432 ni 5434: son de otras apps del mismo entorno (ADR-0026).
      # solo loopback: con el DOCKER_HOST remoto esto abriría la base en la ip pública del VPS;
      # se llega por un túnel ssh (ssh -fN -L 5435:localhost:5435 "$SGSPE_VPS")
      - '127.0.0.1:${SGSPE_PG_PORT:-5435}:5432'
    volumes:
      # las imágenes de pg18 quieren el montaje un nivel más arriba; un volumen con nombre, nunca un bind mount
      - postgis-data:/var/lib/postgresql
    healthcheck:
      test: ['CMD-SHELL', 'pg_isready -U sgspe -d sgspe']
      interval: 5s
      timeout: 5s
      retries: 20

volumes:
  postgis-data:
```

- [ ] **Paso 7 (T0.3): levantar PostGIS en el VPS**

```bash
cd /Users/jorge/IdeaProjects/sgspe
docker compose config -q && echo compose-ok
docker compose up -d
docker compose ps --format '{{.Name}} {{.Status}}'
```

Esperado: `compose-ok`; después de unos segundos, `sgspe-postgis Up … (healthy)` (repetir `docker compose ps` hasta ver
`healthy`).

- [ ] **Paso 8 (T0.3) [USUARIO]: túnel al puerto 5435**

```bash
ssh -fN -o ExitOnForwardFailure=yes -L 5435:localhost:5435 "$SGSPE_VPS"
nc -z localhost 5435 && echo tunel-ok
```

Esperado: `tunel-ok`.

- [ ] **Paso 9 (T0.3): base de pruebas `sgspe_test`**

```bash
cd /Users/jorge/IdeaProjects/sgspe
docker compose exec postgis createdb -U sgspe sgspe_test
docker compose exec postgis psql -U sgspe -d sgspe_test -tAc 'select 1'
```

Esperado: `1`. La base termina en `_test`, como exige el modo externo de `WasichaiTestDatabase`.

- [ ] **Paso 10 (T0.4) [USUARIO]: secreto `PACKAGES_TOKEN` en `sgspe-ui`**

El usuario pega un PAT clásico con solo `read:packages` cuando cada comando lo pide:

```bash
gh secret set PACKAGES_TOKEN --repo wasichai/sgspe-ui
gh secret set PACKAGES_TOKEN --app dependabot --repo wasichai/sgspe-ui
gh secret list --repo wasichai/sgspe-ui
gh secret list --app dependabot --repo wasichai/sgspe-ui
```

Esperado: las dos listas muestran `PACKAGES_TOKEN`.

- [ ] **Paso 11: nada que commitear**

`compose.yml` queda sin versionar hasta la Tarea 4. Comprobar que no se agregó nada:

```bash
cd /Users/jorge/IdeaProjects/sgspe && git status --short
```

Esperado: `?? compose.yml` y nada más.

---

### Tarea 2: Spike de entregables (POI y Gotenberg) y de GetMap

**Pista:** spike, en paralelo con A y B. **Lugar:** `/Users/jorge/IdeaProjects/sgspe-spike`, fuera de los dos
repositorios; nunca se hace `git init` ahí y se borra al terminar. Lo único que se versiona es la nota de hallazgos en
ADR-0013 y ADR-0015.

**Files:**
- Create (desechables): `/Users/jorge/IdeaProjects/sgspe-spike/settings.gradle.kts`, `build.gradle.kts`, `gradlew`,
  `gradlew.bat`, `gradle/wrapper/*`, `compose.yml`, `layouts/sgspe.xml`, `src/main/kotlin/Spike.kt`
- Modify: `/Users/jorge/IdeaProjects/sgspe/docs/adr/0013-entregables-docx-primero.md` (sección «### Seguimiento»)
- Modify: `/Users/jorge/IdeaProjects/sgspe/docs/adr/0015-mapas-de-informe-con-geoserver.md` (sección «### Seguimiento»)

**Interfaces:**
- Consumes: Docker remoto y túneles de la Tarea 1.
- Produces: `out/resultados.txt` con una línea `PASS` o `FAIL` por pregunta, y la nota de hallazgos en los dos ADR.

**Preguntas y criterios de aprobación.** El spike responde exactamente esto:

| ID | ADR | Pregunta | Pasa si |
|---|---|---|---|
| E1 | 0013 | ¿Gotenberg con `updateIndexes` reconstruye el índice (campo TOC) con las páginas reales? | En el PDF del documento pequeño, la página 2 lista cada capítulo («1 Introducción», «2 Objetivos», «3 Metodología») con el número de la página donde de verdad empieza |
| E2 | 0013 | ¿LibreOffice numera las leyendas por capítulo con `STYLEREF 1 \s` y `SEQ … \s 1`? | El texto del PDF contiene «Tabla 1.1», «Tabla 1.2», «Tabla 2.1», «Tabla 2.2», «Tabla 3.1», «Figura 1.1» y «Figura 3.1», y no contiene «Error!» |
| E2b | 0013 | Si E2 falla, ¿sirve la alternativa de `deliverables.md` §7.5 (número literal, marcador y `PAGEREF`)? | La frase «La Tabla 2.1 está en la página N» trae la página real de la Tabla 2.1 |
| E3 | 0013 | ¿`PAGE` y `NUMPAGES` del pie salen bien? | Cada página N dice «Página N de T», con T el total |
| E4 | 0013 | ¿La marca «BORRADOR» del encabezado insertada con POI llega a cada página? | Todas las páginas contienen «BORRADOR» |
| E5 | 0013 | ¿Las fuentes Carlito, Caladea y Liberation se incrustan, sin sustituciones? | Toda fuente del PDF está incrustada y su nombre (sin el prefijo de subconjunto) empieza por Carlito, Caladea, LiberationSans o LiberationSerif |
| E6 | 0013 | ¿`pdfa=PDF/A-2b` produce PDF/A-2b? | El XMP declara `pdfaid:part` 2 y `pdfaid:conformance` B |
| E7 | 0013 | ¿Un documento de unas 100 páginas con 40 fotos convierte a tiempo? | Al menos 90 páginas en 180 s o menos |
| M1 | 0015 | ¿GetMap con una capa en línea (`InlineFeature` en `SLD_BODY`) dibuja en EPSG:32717, 32718 y 32719? | `200 image/png` de 1890 × 1417 px, el centro del polígono con el color #3366CC (± 40 por canal) y 10 s o menos por mapa |
| M3 | 0015 | ¿El diseño de decoración `layout:sgspe` dibuja leyenda (con la capa en línea), escala, norte y fuente? | Cada esquina esperada tiene tinta: leyenda arriba a la derecha más del 1 %, escala abajo a la izquierda más del 0,5 %, norte arriba a la izquierda más del 1 % y fuente abajo a la derecha más del 0,5 % |
| M4 | 0015 | ¿GeoServer acepta un GetMap por POST con 300 polígonos en línea (más de 8 KB)? | `200 image/png` por POST XML (`ogc:GetMap`) o, si ese falla, por POST con los parámetros en `application/x-www-form-urlencoded` (M4b) |

Regla de decisión, que se escribe en los ADR:

- Todo pasa: ADR-0013 y ADR-0015 siguen como están («Propuesto»; se aceptan en F8 y F6).
- E2 falla y E2b pasa: las leyendas usan la alternativa de `deliverables.md` §7.5; ADR-0013 no cambia.
- E5 falla: se anota que la imagen de Gotenberg necesita las fuentes; ADR-0013 no cambia.
- E1, E3, E4 o E6 fallan: se registra como riesgo del roadmap y la decisión de un ADR nuevo va al usuario; no se cambia
  ningún ADR en F1.
- M1 o M3 fallan: se anota la alternativa de ASM-038 (otro generador de mapas) como pendiente de F6; ADR-0015 no cambia.
- M4 falla en sus dos formas: se anota que los mapas con muchas capas en línea necesitan otra vía (ASM-038).

- [ ] **Paso 1: crear el proyecto desechable**

```bash
mkdir -p /Users/jorge/IdeaProjects/sgspe-spike/gradle /Users/jorge/IdeaProjects/sgspe-spike/layouts \
  /Users/jorge/IdeaProjects/sgspe-spike/src/main/kotlin
cp -R /Users/jorge/IdeaProjects/caja-backend/gradle/wrapper /Users/jorge/IdeaProjects/sgspe-spike/gradle/
cp /Users/jorge/IdeaProjects/caja-backend/gradlew /Users/jorge/IdeaProjects/caja-backend/gradlew.bat \
  /Users/jorge/IdeaProjects/sgspe-spike/
grep distributionUrl /Users/jorge/IdeaProjects/sgspe-spike/gradle/wrapper/gradle-wrapper.properties
```

Esperado: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip`.

`/Users/jorge/IdeaProjects/sgspe-spike/settings.gradle.kts`:

```kotlin
rootProject.name = "sgspe-spike"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

`/Users/jorge/IdeaProjects/sgspe-spike/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.4.20"
    application
}

dependencies {
    // poi-ooxml-full en lugar de poi-ooxml-lite: el spike usa clases del esquema que la versión lite no trae
    implementation("org.apache.poi:poi-ooxml:5.4.1") {
        exclude(group = "org.apache.poi", module = "poi-ooxml-lite")
    }
    implementation("org.apache.poi:poi-ooxml-full:5.4.1")
    // la misma versión que caja-backend@dc9e391 (gradle/libs.versions.toml)
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("SpikeKt")
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}
```

`/Users/jorge/IdeaProjects/sgspe-spike/compose.yml`:

```yaml
name: sgspe-spike

services:
  # gotenberg y geoserver solo para el spike (architecture.md §11); nunca en el compose de sgspe hasta su fase
  gotenberg:
    image: gotenberg/gotenberg:8-libreoffice
    # el documento de 100 páginas tarda más que los 30 s por defecto
    command: ['gotenberg', '--api-timeout=300s']
    ports:
      - '127.0.0.1:3094:3000'
  geoserver:
    image: docker.osgeo.org/geoserver:3.0.1
    environment:
      SKIP_DEMO_DATA: 'true'
    ports:
      - '127.0.0.1:8194:8080'
    volumes:
      - geoserver-data:/opt/geoserver_data

volumes:
  geoserver-data:
```

`/Users/jorge/IdeaProjects/sgspe-spike/layouts/sgspe.xml`:

```xml
<layout>
  <decoration type="legend" affinity="top,right" offset="12,12" size="auto"/>
  <decoration type="scaleline" affinity="bottom,left" offset="12,12"/>
  <decoration type="scaleratio" affinity="bottom,left" offset="12,70"/>
  <decoration type="image" affinity="top,left" offset="12,12" size="90,120">
    <option name="url" value="norte.png"/>
  </decoration>
  <decoration type="text" affinity="bottom,right" offset="12,12">
    <option name="message" value="Fuente: elaboración propia con datos de prueba"/>
    <option name="font-size" value="28"/>
  </decoration>
</layout>
```

- [ ] **Paso 2: escribir `src/main/kotlin/Spike.kt`**

```kotlin
// spike desechable de F1 (ADR-0013 y ADR-0015): DOCX con Apache POI, PDF con Gotenberg y mapas con GeoServer GetMap.
// nunca se versiona: sus hallazgos van al «Seguimiento» de los dos ADR. datos de prueba, sin nombres reales
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.util.Units
import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xwpf.usermodel.BreakType
import org.apache.poi.xwpf.usermodel.Document
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFStyle
import org.apache.poi.xwpf.usermodel.XWPFStyles
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.random.Random
import kotlin.system.exitProcess

val GOTENBERG: String = System.getenv("SPIKE_GOTENBERG") ?: "http://localhost:3094"
val GEOSERVER: String = System.getenv("SPIKE_GEOSERVER") ?: "http://localhost:8194/geoserver"
val OUT = File("out").apply { mkdirs() }
val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
val results = mutableListOf<String>()

val TITLES =
    listOf(
        "Introducción",
        "Objetivos",
        "Metodología",
        "Áreas de influencia social",
        "Contexto socioeconómico",
        "Identificación de grupos de interés",
        "Percepciones",
        "Temas clave",
        "Estrategias de relacionamiento",
        "Anexos"
    )

const val PARAGRAPH =
    "El equipo social recorrió las localidades del área de influencia y registró las reuniones, los acuerdos y las " +
        "preguntas de la población. Este párrafo de prueba ocupa varias líneas para que el documento tenga un largo " +
        "parecido al de un diagnóstico real, con títulos numerados, tablas con leyenda, figuras y notas de fuente, " +
        "sin ningún dato de una persona ni de una comunidad concreta."

fun verdict(
    id: String,
    ok: Boolean,
    detail: String
) {
    val line = "${if (ok) "PASS" else "FAIL"} $id: $detail"
    results += line
    println(line)
}

fun main(args: Array<String>) {
    when (args.firstOrNull() ?: "all") {
        "north" -> north()
        "docx" -> deliverables()
        "getmap" -> maps()
        "all" -> {
            deliverables()
            maps()
        }
        else -> error("uso: north | docx | getmap | all")
    }
    if (results.isEmpty()) return
    File(OUT, "resultados.txt").appendText(results.joinToString("\n", postfix = "\n"))
    if (results.any { it.startsWith("FAIL") }) exitProcess(1)
}

// --- ADR-0013: DOCX con POI y PDF con Gotenberg ---

class Docx(
    private val chapters: Int,
    private val photosPerChapter: Int,
    private val paragraphsPerChapter: Int
) {
    private val doc = XWPFDocument()
    private var numId: BigInteger = BigInteger.ZERO

    fun build(): ByteArray {
        styles()
        numbering()
        headerAndFooter()
        // la plantilla pide a Word que actualice los campos al abrir; LibreOffice los actualiza con updateIndexes
        doc.enforceUpdateFields()
        doc.createParagraph().createRun().apply {
            setText("Diagnóstico social de prueba")
            isBold = true
            fontSize = 24
            addBreak(BreakType.PAGE)
        }
        doc.createParagraph().createRun().apply {
            setText("Índice")
            isBold = true
        }
        field(doc.createParagraph(), "TOC \\o \"1-2\" \\h \\z \\u", "Actualice el índice")
        doc.createParagraph().createRun().addBreak(BreakType.PAGE)
        for (chapter in 1..chapters) chapter(chapter)
        return ByteArrayOutputStream().also { doc.write(it) }.toByteArray()
    }

    private fun styles() {
        val styles = doc.createStyles()
        styles.setDefaultFonts(
            CTFonts.Factory.newInstance().apply {
                ascii = "Carlito"
                hAnsi = "Carlito"
                cs = "Carlito"
                eastAsia = "Carlito"
            }
        )
        style(styles, "Heading1", "heading 1", 0, 16, "Caladea")
        style(styles, "Heading2", "heading 2", 1, 13, "Caladea")
        style(styles, "Caption", "caption", null, 10, "Liberation Sans")
    }

    private fun style(
        styles: XWPFStyles,
        id: String,
        name: String,
        level: Int?,
        size: Int,
        font: String
    ) {
        val ct = CTStyle.Factory.newInstance()
        ct.styleId = id
        ct.type = STStyleType.PARAGRAPH
        ct.addNewName().`val` = name
        ct.addNewQFormat()
        val ppr = ct.addNewPPr()
        if (level != null) ppr.addNewOutlineLvl().`val` = BigInteger.valueOf(level.toLong())
        val rpr = ct.addNewRPr()
        rpr.addNewRFonts().apply {
            ascii = font
            hAnsi = font
        }
        rpr.addNewSz().`val` = BigInteger.valueOf(size * 2L)
        rpr.addNewB()
        styles.addStyle(XWPFStyle(ct))
    }

    // numeración multinivel de los títulos: "1" y "1.1". STYLEREF 1 \s lee el número del título 1 más cercano
    private fun numbering() {
        val abstract = CTAbstractNum.Factory.newInstance()
        abstract.abstractNumId = BigInteger.ONE
        listOf("Heading1" to "%1", "Heading2" to "%1.%2").forEachIndexed { level, (styleId, text) ->
            val lvl = abstract.addNewLvl()
            lvl.ilvl = BigInteger.valueOf(level.toLong())
            lvl.addNewStart().`val` = BigInteger.ONE
            lvl.addNewNumFmt().`val` = STNumberFormat.DECIMAL
            lvl.addNewLvlText().`val` = text
            lvl.addNewPStyle().`val` = styleId
        }
        val numbering = doc.createNumbering()
        numId = numbering.addNum(numbering.addAbstractNum(XWPFAbstractNum(abstract)))
    }

    // la marca de los borradores va en el encabezado de cada sección (deliverables.md §7.5); el pie, "Página X de Y"
    private fun headerAndFooter() {
        doc.createHeader(HeaderFooterType.DEFAULT).createParagraph().apply {
            alignment = ParagraphAlignment.RIGHT
            createRun().apply {
                setText("BORRADOR")
                isBold = true
                color = "C00000"
            }
        }
        doc.createFooter(HeaderFooterType.DEFAULT).createParagraph().apply {
            alignment = ParagraphAlignment.CENTER
            createRun().setText("Página ")
            field(this, "PAGE", "1")
            createRun().setText(" de ")
            field(this, "NUMPAGES", "1")
        }
    }

    private fun heading(
        text: String,
        level: Int
    ) {
        val p = doc.createParagraph()
        p.style = if (level == 0) "Heading1" else "Heading2"
        p.numID = numId
        // POI nombra distinto el getter (getNumIlvl) y el setter (setNumILvl): se llama al setter
        p.setNumILvl(BigInteger.valueOf(level.toLong()))
        p.createRun().setText(text)
    }

    private fun chapter(n: Int) {
        heading(TITLES[n - 1], 0)
        heading("Alcance del capítulo", 1)
        repeat(paragraphsPerChapter) { doc.createParagraph().createRun().setText(PARAGRAPH) }
        table(n, 1)
        table(n, 2)
        repeat(photosPerChapter) { photo(n, it + 1) }
        if (n == 2) reference()
        doc.createParagraph().createRun().addBreak(BreakType.PAGE)
    }

    private fun table(
        chapter: Int,
        number: Int
    ) {
        val caption = doc.createParagraph()
        caption.style = "Caption"
        if (chapter == 2 && number == 1) {
            caption.ctp.addNewBookmarkStart().apply {
                name = "_Tabla_2_1"
                id = BigInteger.ONE
            }
            caption.ctp.addNewBookmarkEnd().id = BigInteger.ONE
        }
        caption.createRun().setText("Tabla ")
        field(caption, "STYLEREF 1 \\s", "$chapter")
        caption.createRun().setText(".")
        field(caption, "SEQ Tabla \\* ARABIC \\s 1", "$number")
        caption.createRun().setText(" Población de prueba por localidad")
        val table = doc.createTable(4, 3)
        table.getRow(0).isRepeatHeader = true
        listOf("Localidad", "Hombres", "Mujeres").forEachIndexed { i, text -> table.getRow(0).getCell(i).text = text }
        for (row in 1..3) {
            listOf("Localidad $row", "${10 * row}", "${11 * row}").forEachIndexed { i, text -> table.getRow(row).getCell(i).text = text }
        }
        // celdas combinadas en la última fila
        val last = table.getRow(3)
        val cell = last.getCell(0).ctTc
        (cell.tcPr ?: cell.addNewTcPr()).addNewGridSpan().`val` = BigInteger.TWO
        last.removeCell(1)
        doc.createParagraph().createRun().setText("Fuente: elaboración propia con datos de prueba")
    }

    private fun photo(
        chapter: Int,
        n: Int
    ) {
        val image = BufferedImage(2000, 1500, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        val random = Random(chapter * 100 + n)
        repeat(200) {
            g.color = Color(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            g.fillRect(random.nextInt(2000), random.nextInt(1500), 200, 150)
        }
        g.dispose()
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "jpg", it) }.toByteArray()
        doc.createParagraph().createRun().addPicture(
            ByteArrayInputStream(bytes),
            Document.PICTURE_TYPE_JPEG,
            "foto-$chapter-$n.jpg",
            Units.toEMU(400.0),
            Units.toEMU(300.0)
        )
        val caption = doc.createParagraph()
        caption.style = "Caption"
        caption.createRun().setText("Figura ")
        field(caption, "STYLEREF 1 \\s", "$chapter")
        caption.createRun().setText(".")
        field(caption, "SEQ Figura \\* ARABIC \\s 1", "$n")
        caption.createRun().setText(" Registro fotográfico de prueba")
    }

    // la alternativa de deliverables.md §7.5: número literal, marcador y PAGEREF
    private fun reference() {
        val p = doc.createParagraph()
        p.createRun().setText("La Tabla 2.1 está en la página ")
        field(p, "PAGEREF _Tabla_2_1 \\h", "0")
        p.createRun().setText(".")
    }
}

// un campo complejo de Word: begin, instrucción, separate, resultado precalculado, end
fun field(
    p: XWPFParagraph,
    instruction: String,
    cached: String
) {
    p.createRun().ctr.addNewFldChar().fldCharType = STFldCharType.BEGIN
    p.createRun().ctr.addNewInstrText().apply {
        stringValue = " $instruction "
        space = SpaceAttribute.Space.PRESERVE
    }
    p.createRun().ctr.addNewFldChar().fldCharType = STFldCharType.SEPARATE
    p.createRun().setText(cached)
    p.createRun().ctr.addNewFldChar().fldCharType = STFldCharType.END
}

fun convert(
    docx: ByteArray,
    name: String,
    pdfa: Boolean
): Pair<ByteArray, Long> {
    val boundary = "----sgspe-spike-${System.nanoTime()}"
    val body = ByteArrayOutputStream()

    fun part(
        headers: String,
        content: ByteArray
    ) {
        body.write("--$boundary\r\n$headers\r\n\r\n".toByteArray())
        body.write(content)
        body.write("\r\n".toByteArray())
    }
    part(
        "Content-Disposition: form-data; name=\"files\"; filename=\"$name.docx\"\r\n" +
            "Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        docx
    )
    part("Content-Disposition: form-data; name=\"updateIndexes\"", "true".toByteArray())
    if (pdfa) part("Content-Disposition: form-data; name=\"pdfa\"", "PDF/A-2b".toByteArray())
    body.write("--$boundary--\r\n".toByteArray())
    val request =
        HttpRequest
            .newBuilder(URI("$GOTENBERG/forms/libreoffice/convert"))
            .timeout(Duration.ofSeconds(300))
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
            .build()
    val start = System.nanoTime()
    val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
    val millis = (System.nanoTime() - start) / 1_000_000
    check(response.statusCode() == 200) { "Gotenberg respondió ${response.statusCode()}: ${String(response.body()).take(500)}" }
    return response.body() to millis
}

fun pageTexts(pdf: PDDocument): List<String> =
    (1..pdf.numberOfPages).map { n ->
        PDFTextStripper()
            .apply {
                startPage = n
                endPage = n
            }.getText(pdf)
    }

fun inspect(pdf: PDDocument) {
    val pages = pageTexts(pdf)
    val total = pages.size
    val all = pages.joinToString("\n")

    // E1: la página donde empieza cada capítulo y la que dice el índice (página 2)
    val found =
        TITLES.take(3).mapIndexed { i, title ->
            val heading = Regex("""^\s*${i + 1}\s+${Regex.escape(title)}\s*$""", RegexOption.MULTILINE)
            val listed = Regex("""^\s*${i + 1}\s+${Regex.escape(title)}\D*?(\d+)\s*$""", RegexOption.MULTILINE)
            val start = pages.indexOfFirst { page -> heading.containsMatchIn(page) && !page.contains("Índice") } + 1
            Triple(title, start, listed.find(pages[1])?.groupValues?.get(1)?.toInt())
        }
    verdict("E1 índice", found.all { (_, start, listed) -> start > 2 && listed == start }, found.joinToString { (t, s, l) -> "$t: página $s, índice $l" })

    // E2 y E2b: leyendas por capítulo, y la alternativa con PAGEREF
    val expected = listOf("Tabla 1.1", "Tabla 1.2", "Tabla 2.1", "Tabla 2.2", "Tabla 3.1", "Figura 1.1", "Figura 3.1")
    val missing = expected.filter { !all.contains(it) }
    verdict("E2 leyendas por capítulo", missing.isEmpty() && !all.contains("Error!"), if (missing.isEmpty()) "todas presentes" else "faltan $missing")
    val table21 = pages.indexOfFirst { it.contains("2.1 Población de prueba") } + 1
    val reference = Regex("""La Tabla 2\.1 está en la página (\d+)""").find(all)?.groupValues?.get(1)?.toInt()
    verdict("E2b PAGEREF", table21 > 0 && reference == table21, "la Tabla 2.1 está en la página $table21; la referencia dice $reference")

    // E3 y E4: pie y marca en cada página
    val badFooter = pages.withIndex().filter { (i, text) -> !Regex("""Página\s+${i + 1}\s+de\s+$total\b""").containsMatchIn(text) }.map { it.index + 1 }
    verdict("E3 paginación", badFooter.isEmpty(), if (badFooter.isEmpty()) "$total páginas con «Página N de $total»" else "pie incorrecto en las páginas $badFooter")
    val unmarked = pages.withIndex().filter { (_, text) -> !text.contains("BORRADOR") }.map { it.index + 1 }
    verdict("E4 marca BORRADOR", unmarked.isEmpty(), if (unmarked.isEmpty()) "en las $total páginas" else "falta en las páginas $unmarked")

    // E5: fuentes incrustadas y permitidas
    val fonts = mutableSetOf<Pair<String, Boolean>>()
    for (page in pdf.pages) {
        val resources = page.resources
        for (name in resources.fontNames) {
            val font = resources.getFont(name)
            fonts += (font.name ?: "?") to font.isEmbedded
        }
    }
    val allowed = Regex("""^([A-Z]{6}\+)?(Carlito|Caladea|LiberationSans|LiberationSerif)""")
    val bad = fonts.filter { (name, embedded) -> !embedded || !allowed.containsMatchIn(name) }
    verdict("E5 fuentes", fonts.isNotEmpty() && bad.isEmpty(), "fuentes ${fonts.map { it.first }.sorted()}; no válidas $bad")

    // E6: PDF/A-2b en el XMP
    val xmp =
        pdf.documentCatalog.metadata
            ?.exportXMPMetadata()
            ?.readAllBytes()
            ?.let { String(it) } ?: ""
    val part = Regex("""pdfaid:part(="|>)\s*2""").containsMatchIn(xmp)
    val conformance = Regex("""pdfaid:conformance(="|>)\s*B""").containsMatchIn(xmp)
    verdict("E6 PDF/A-2b", part && conformance, if (xmp.isEmpty()) "sin metadatos XMP" else "part 2: $part; conformance B: $conformance")
}

fun deliverables() {
    val small = Docx(chapters = 3, photosPerChapter = 1, paragraphsPerChapter = 12).build()
    File(OUT, "pequeno.docx").writeBytes(small)
    val (pdf, _) = convert(small, "pequeno", pdfa = true)
    File(OUT, "pequeno.pdf").writeBytes(pdf)
    Loader.loadPDF(pdf).use { inspect(it) }

    val large = Docx(chapters = 10, photosPerChapter = 4, paragraphsPerChapter = 90).build()
    File(OUT, "grande.docx").writeBytes(large)
    val (largePdf, millis) = convert(large, "grande", pdfa = false)
    File(OUT, "grande.pdf").writeBytes(largePdf)
    val pages = Loader.loadPDF(largePdf).use { it.numberOfPages }
    verdict("E7 tiempo", pages >= 90 && millis <= 180_000, "$pages páginas en ${millis / 1000} s (umbral: 90 páginas o más en 180 s o menos)")
}

// --- ADR-0015: GetMap con capas en línea y decoraciones ---

const val W = 1890
const val H = 1417

// 4000 × 3000 m, la proporción del marco de 16 × 12 cm. el área de prueba ocupa el centro
val BBOX = listOf(778500.0, 9129000.0, 782500.0, 9132000.0)

fun north() {
    val image = BufferedImage(90, 120, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    g.color = Color.BLACK
    g.fillPolygon(intArrayOf(45, 75, 45, 15), intArrayOf(5, 80, 60, 80), 4)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 30)
    g.drawString("N", 34, 115)
    g.dispose()
    ImageIO.write(image, "png", File(OUT, "norte.png"))
    println("out/norte.png listo")
}

fun square(
    e: Double,
    n: Double,
    side: Double
) = "$e,$n ${e + side},$n ${e + side},${n + side} $e,${n + side} $e,$n"

fun feature(
    epsg: Int,
    ring: String,
    name: String
) = "<featureMember><area><geometry><gml:Polygon srsName=\"EPSG:$epsg\"><gml:outerBoundaryIs><gml:LinearRing>" +
    "<gml:coordinates>$ring</gml:coordinates></gml:LinearRing></gml:outerBoundaryIs></gml:Polygon></geometry>" +
    "<nombre>$name</nombre></area></featureMember>"

fun sld(
    epsg: Int,
    polygons: Int
): String {
    val random = Random(7)
    val features =
        buildString {
            append(feature(epsg, square(780000.0, 9130000.0, 1000.0), "Área de prueba"))
            repeat(polygons - 1) {
                append(feature(epsg, square(779000.0 + random.nextDouble(2800.0), 9129500.0 + random.nextDouble(400.0), 60.0), "Predio de prueba $it"))
            }
        }
    return "<StyledLayerDescriptor version=\"1.0.0\" xmlns=\"http://www.opengis.net/sld\" " +
        "xmlns:ogc=\"http://www.opengis.net/ogc\" xmlns:gml=\"http://www.opengis.net/gml\">" +
        "<UserLayer><Name>area_de_influencia</Name><InlineFeature><FeatureCollection>$features</FeatureCollection></InlineFeature>" +
        "<UserStyle><FeatureTypeStyle><Rule><Name>aid</Name><Title>Área de influencia de prueba</Title><PolygonSymbolizer>" +
        "<Fill><CssParameter name=\"fill\">#3366CC</CssParameter></Fill><Stroke><CssParameter name=\"stroke\">#1F3D7A</CssParameter>" +
        "<CssParameter name=\"stroke-width\">2</CssParameter></Stroke></PolygonSymbolizer></Rule></FeatureTypeStyle></UserStyle>" +
        "</UserLayer></StyledLayerDescriptor>"
}

fun kvp(
    epsg: Int,
    polygons: Int,
    layout: Boolean
): String {
    val params =
        mutableMapOf(
            "SERVICE" to "WMS",
            "VERSION" to "1.1.1",
            "REQUEST" to "GetMap",
            "SRS" to "EPSG:$epsg",
            "BBOX" to BBOX.joinToString(","),
            "WIDTH" to "$W",
            "HEIGHT" to "$H",
            "FORMAT" to "image/png",
            "TRANSPARENT" to "false",
            "BGCOLOR" to "0xFFFFFF",
            "SLD_BODY" to sld(epsg, polygons)
        )
    if (layout) params["format_options"] = "layout:sgspe;dpi:300"
    return params.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, Charsets.UTF_8)}" }
}

fun timed(request: HttpRequest): Pair<HttpResponse<ByteArray>, Long> {
    val start = System.nanoTime()
    val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
    return response to (System.nanoTime() - start) / 1_000_000
}

fun png(response: HttpResponse<ByteArray>): BufferedImage? {
    val type = response.headers().firstValue("Content-Type").orElse("")
    if (response.statusCode() != 200 || !type.startsWith("image/png")) return null
    return ImageIO.read(ByteArrayInputStream(response.body()))
}

fun inked(
    image: BufferedImage,
    x0: Int,
    y0: Int,
    x1: Int,
    y1: Int
): Double {
    var dark = 0
    var all = 0
    for (x in x0 until x1) {
        for (y in y0 until y1) {
            all++
            val c = Color(image.getRGB(x, y))
            if (c.red + c.green + c.blue < 600) dark++
        }
    }
    return dark.toDouble() / all
}

fun maps() {
    val cx = ((780500 - BBOX[0]) / (BBOX[2] - BBOX[0]) * W).toInt()
    val cy = ((BBOX[3] - 9130500) / (BBOX[3] - BBOX[1]) * H).toInt()
    for (epsg in listOf(32717, 32718, 32719)) {
        val (response, millis) = timed(HttpRequest.newBuilder(URI("$GEOSERVER/wms?${kvp(epsg, 1, layout = false)}")).timeout(Duration.ofSeconds(60)).GET().build())
        val image = png(response)
        if (image == null) {
            verdict("M1 EPSG:$epsg", false, "HTTP ${response.statusCode()}: ${String(response.body()).take(400)}")
            continue
        }
        File(OUT, "m1-$epsg.png").writeBytes(response.body())
        val fill = Color(image.getRGB(cx, cy))
        val blue = abs(fill.red - 0x33) < 40 && abs(fill.green - 0x66) < 40 && abs(fill.blue - 0xCC) < 40
        verdict(
            "M1 EPSG:$epsg",
            image.width == W && image.height == H && blue && millis <= 10_000,
            "%d×%d, centro #%02X%02X%02X, %d ms".format(image.width, image.height, fill.red, fill.green, fill.blue, millis)
        )
    }

    val (decorated, _) = timed(HttpRequest.newBuilder(URI("$GEOSERVER/wms?${kvp(32717, 1, layout = true)}")).timeout(Duration.ofSeconds(60)).GET().build())
    val image = png(decorated)
    if (image == null) {
        verdict("M3 decoraciones", false, "HTTP ${decorated.statusCode()}: ${String(decorated.body()).take(400)}")
    } else {
        File(OUT, "m3-decoraciones.png").writeBytes(decorated.body())
        val legend = inked(image, W * 3 / 4, 0, W, H * 2 / 5)
        val scale = inked(image, 0, H * 4 / 5, W / 3, H)
        val arrow = inked(image, 0, 0, W / 6, H / 5)
        val source = inked(image, W * 2 / 3, H * 9 / 10, W, H)
        verdict(
            "M3 decoraciones",
            legend > 0.01 && scale > 0.005 && arrow > 0.01 && source > 0.005,
            "tinta: leyenda %.3f, escala %.3f, norte %.3f, fuente %.3f".format(legend, scale, arrow, source)
        )
    }

    val xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ogc:GetMap xmlns:ogc=\"http://www.opengis.net/ows\" " +
            "xmlns:gml=\"http://www.opengis.net/gml\" version=\"1.2.0\" service=\"WMS\">${sld(32717, 300)}" +
            "<BoundingBox srsName=\"http://www.opengis.net/gml/srs/epsg.xml#32717\">" +
            "<gml:coord><gml:X>${BBOX[0]}</gml:X><gml:Y>${BBOX[1]}</gml:Y></gml:coord>" +
            "<gml:coord><gml:X>${BBOX[2]}</gml:X><gml:Y>${BBOX[3]}</gml:Y></gml:coord></BoundingBox>" +
            "<Output><Format>image/png</Format><Transparent>false</Transparent><Size><Width>$W</Width><Height>$H</Height></Size>" +
            "</Output></ogc:GetMap>"
    val (post, postMillis) =
        timed(
            HttpRequest
                .newBuilder(URI("$GEOSERVER/wms"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/xml")
                .POST(HttpRequest.BodyPublishers.ofString(xml))
                .build()
        )
    if (png(post) != null) {
        File(OUT, "m4-post-xml.png").writeBytes(post.body())
        verdict("M4 POST XML", true, "${xml.length / 1024} KB en ${postMillis} ms")
        return
    }
    val form = kvp(32717, 300, layout = false)
    val (formPost, formMillis) =
        timed(
            HttpRequest
                .newBuilder(URI("$GEOSERVER/wms"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()
        )
    val ok = png(formPost) != null
    if (ok) File(OUT, "m4b-post-form.png").writeBytes(formPost.body())
    verdict(
        "M4 POST",
        ok,
        "XML: HTTP ${post.statusCode()} ${String(post.body()).take(200)}; formulario (M4b, ${form.length / 1024} KB): HTTP ${formPost.statusCode()} en $formMillis ms"
    )
}
```

- [ ] **Paso 3: compilar y generar la flecha de norte**

```bash
cd /Users/jorge/IdeaProjects/sgspe-spike
./gradlew -q run --args=north
ls out/norte.png
```

Esperado: `out/norte.png listo` y el archivo listado. El spike usa solo Maven Central: no necesita `GITHUB_TOKEN`.

- [ ] **Paso 4: levantar Gotenberg y GeoServer en el VPS y cargar el diseño de decoración**

```bash
cd /Users/jorge/IdeaProjects/sgspe-spike
docker compose up -d
docker compose exec geoserver mkdir -p /opt/geoserver_data/layouts
docker compose cp layouts/sgspe.xml geoserver:/opt/geoserver_data/layouts/sgspe.xml
docker compose cp out/norte.png geoserver:/opt/geoserver_data/layouts/norte.png
docker compose ps --format '{{.Name}} {{.Status}}'
```

Esperado: los dos contenedores `Up`. GeoServer tarda hasta dos minutos en responder la primera vez.

- [ ] **Paso 5 [USUARIO]: túneles del spike**

```bash
ssh -fN -o ExitOnForwardFailure=yes -L 3094:localhost:3094 -L 8194:localhost:8194 "$SGSPE_VPS"
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:3094/health
curl -s -o /dev/null -w '%{http_code}\n' 'http://localhost:8194/geoserver/wms?SERVICE=WMS&REQUEST=GetCapabilities'
```

Esperado: `200` y `200`.

- [ ] **Paso 6: correr el spike**

```bash
cd /Users/jorge/IdeaProjects/sgspe-spike
rm -f out/resultados.txt
./gradlew -q run --args=all; echo "salida: $?"
cat out/resultados.txt
```

Esperado: una línea `PASS` o `FAIL` por cada pregunta (E1, E2, E2b, E3, E4, E5, E6, E7, M1 × 3, M3, M4) y `salida: 0` si
todo pasa (`1` si algo falla). Ante un `FAIL`, abrir el archivo de `out/` correspondiente (`pequeno.pdf`, `grande.pdf`,
`m1-*.png`, `m3-decoraciones.png`) y confirmar a ojo que el fallo es del motor y no de la extracción de texto, antes de
concluir. El hallazgo cita lo que se vio.

- [ ] **Paso 7: escribir los hallazgos en los ADR**

En `/Users/jorge/IdeaProjects/sgspe/docs/adr/0013-entregables-docx-primero.md`, al final de la lista de «### Seguimiento»
(después de la viñeta «Pruebas: …»), agregar una viñeta con esta forma, con la fecha del día (`date +%F`), cada resultado
copiado de `out/resultados.txt` y la regla de decisión que aplica:

```markdown
- **Resultado del spike de F1 (AAAA-MM-DD).** POI 5.4.1 genera el DOCX y Gotenberg (`gotenberg/gotenberg:8-libreoffice`)
  lo convierte: E1 índice …; E2 leyendas por capítulo …; E2b `PAGEREF` …; E3 paginación …; E4 marca «BORRADOR» …;
  E5 fuentes …; E6 PDF/A-2b …; E7 N páginas en S s. Consecuencia: … (según la regla de decisión del plan de F1).
```

En `/Users/jorge/IdeaProjects/sgspe/docs/adr/0015-mapas-de-informe-con-geoserver.md`, igual al final de «### Seguimiento»:

```markdown
- **Resultado del spike de F1 (AAAA-MM-DD).** GeoServer 3.0.1 con capas en línea por `SLD_BODY`: M1 EPSG:32717 …,
  EPSG:32718 …, EPSG:32719 …; M3 decoraciones (leyenda, escala, norte y fuente) …; M4 POST con 300 polígonos ….
  La grilla UTM la sigue dibujando SGSPE. Consecuencia: … (según la regla de decisión del plan de F1).
```

En las dos viñetas, `AAAA-MM-DD` es la salida de `date +%F` y cada `…` es el texto real (`PASS` con su detalle, o `FAIL`
con lo que se vio). No se cambia el estado de ningún ADR.

- [ ] **Paso 8: verificar el formato de los ADR**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git diff --stat docs/adr/0013-entregables-docx-primero.md docs/adr/0015-mapas-de-informe-con-geoserver.md
grep -c 'Resultado del spike de F1' docs/adr/0013-entregables-docx-primero.md docs/adr/0015-mapas-de-informe-con-geoserver.md
```

Esperado: dos archivos con líneas agregadas; `1` en cada uno.

- [ ] **Paso 9: commit en `sgspe`**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add docs/adr/0013-entregables-docx-primero.md docs/adr/0015-mapas-de-informe-con-geoserver.md
git commit -F - <<'EOF'
docs(adr): resultado del spike de entregables y GetMap

El spike de F1 probó POI, Gotenberg y GeoServer GetMap fuera de los repositorios.
Cada ADR registra sus resultados y la consecuencia en «Seguimiento».

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

- [ ] **Paso 10: desmontar el spike**

```bash
cd /Users/jorge/IdeaProjects/sgspe-spike
docker compose down -v
pkill -f 'ssh -fN -o ExitOnForwardFailure=yes -L 3094:localhost:3094' || true
cd /Users/jorge/IdeaProjects && rm -rf sgspe-spike
ls /Users/jorge/IdeaProjects | grep -c sgspe-spike
```

Esperado: `0`. El túnel del 5435 de la Tarea 1 sigue abierto.

---

### Tarea 3: A1 · Build de Gradle, app `sgspe` y `SgspeSmokeTest`

**Pista:** A (`/Users/jorge/IdeaProjects/sgspe`, rama `feat/fase-1-fundacion`).

**Files:**
- Create (copia exacta de caja-backend@dc9e391): `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`,
  `gradle/wrapper/gradle-wrapper.properties`, `.editorconfig`, `.gitignore`
- Create: `settings.gradle.kts`, `build.gradle.kts`
- Create: `src/main/kotlin/sgspe/SgspeApplication.kt`, `src/main/resources/application.yml`
- Test: `src/test/kotlin/sgspe/SgspeSmokeTest.kt`

**Interfaces:**
- Consumes: PostGIS y `sgspe_test` por el túnel de la Tarea 1.
- Produces: clase `sgspe.SgspeApplication` (la `@SpringBootConfiguration` que encuentran todas las pruebas de integración
  bajo el paquete `sgspe`); tareas Gradle `test` (sin la etiqueta `integration`) e `integrationTest` (solo esa etiqueta,
  con `postgis/postgis:18-3.6`); la app en el puerto 8094.

- [ ] **Paso 1: copiar el wrapper y los archivos de editor de caja**

```bash
cd /Users/jorge/IdeaProjects/sgspe
mkdir -p gradle
cp -R /Users/jorge/IdeaProjects/caja-backend/gradle/wrapper gradle/
cp /Users/jorge/IdeaProjects/caja-backend/gradlew /Users/jorge/IdeaProjects/caja-backend/gradlew.bat .
cp /Users/jorge/IdeaProjects/caja-backend/.editorconfig /Users/jorge/IdeaProjects/caja-backend/.gitignore .
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```

Esperado: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip`. Ninguna edición: el
`.gitignore` de caja ya ignora `build/`, `.gradle/`, `.kotlin/`, `.env`, `node_modules/` y `__pycache__/`.

- [ ] **Paso 2: `settings.gradle.kts`** (copiado de caja-backend@dc9e391 `settings.gradle.kts`; única edición:
  `rootProject.name`)

```kotlin
rootProject.name = "sgspe"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // wasichai's libraries. GitHub Packages wants a token even to read: read:packages is enough
        maven {
            name = "wasichai"
            url = uri("https://maven.pkg.github.com/wasichai/wasichai")
            credentials {
                username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
                password = providers.gradleProperty("gpr.key").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
            content { includeGroup("wasichai") }
        }
        // fallback until a wasichai release is published: ./gradlew publishToMavenLocal in a wasichai checkout
        mavenLocal {
            content { includeGroup("wasichai") }
        }
    }
}
```

- [ ] **Paso 3: `build.gradle.kts`** (adaptado de caja-backend@dc9e391 `build.gradle.kts`: sin las librerías de emisión
  ni `mockwebserver`, PostGIS en `integrationTest`, entradas de los scripts de `model/`)

```kotlin
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    id("org.springframework.boot") version "4.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "sgspe"
description = "sgspe: gestión social para proyectos extractivos sobre wasichai (core, views, forms, pages)"

val wasichaiVersion = "0.3.2"

dependencies {
    implementation(platform("wasichai:wasichai-bom:$wasichaiVersion"))
    implementation("wasichai:wasichai-spring-boot-starter")
    implementation("wasichai:wasichai-spring-boot-starter-views")
    implementation("wasichai:wasichai-spring-boot-starter-forms")
    implementation("wasichai:wasichai-spring-boot-starter-pages")

    // WasichaiIntegrationTest; brings spring-boot-starter-test, webflux-test and testcontainers
    testImplementation("wasichai:wasichai-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// ktlint lee .editorconfig; la misma versión que wasichai y caja
ktlint {
    version.set("1.7.1")
}

// las pruebas unitarias corren siempre; las que necesitan base van por integrationTest
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
    // ArchitectureTest lee las fuentes y model/roles.json: un cambio en ellos vuelve a correr las pruebas
    inputs.dir("src/main/kotlin")
    inputs.file("model/roles.json").optional()
}

// WASICHAI_TEST_DB_* (si están) apuntan la suite a una base externa en lugar de Testcontainers
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "pruebas de integración contra PostgreSQL 18 con PostGIS 3.6"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    // la imagen del compose desde F1 (architecture.md §5.1); una base externa (WASICHAI_TEST_DB_*) ya la tiene
    systemProperty("wasichai.test.db.image", "postgis/postgis:18-3.6")
    // la app registra en stdout; stderr lleva solo lo que una prueba reporta
    testLogging {
        events("standard_error")
    }
    // SgspeApiTest aprovisiona con los scripts y los json de model/: un cambio en ellos vuelve a correr la suite, nunca un
    // verde de caché
    inputs.files(fileTree("model") { include("*.py", "*.json") })
    shouldRunAfter(tasks.named("test"))
}

// un jar ejecutable con nombre fijo
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

// una app, no una librería: sin el jar plano junto al boot jar
tasks.named<Jar>("jar") {
    enabled = false
}
```

- [ ] **Paso 4: escribir la prueba que falla, `src/test/kotlin/sgspe/SgspeSmokeTest.kt`**

```kotlin
package sgspe

import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import wasichai.test.WasichaiIntegrationTest
import java.util.UUID

// la app misma (SgspeApplication) sobre PostGIS: su salud responde, los módulos que instala responden, los que deja
// fuera no, y lo propio de sgspe exige token (adaptado de caja-backend@dc9e391, src/test/kotlin/caja/CajaSmokeTest.kt)
class SgspeSmokeTest : WasichaiIntegrationTest() {
    @Test
    fun `la salud responde UP`() {
        client
            .get()
            .uri("/actuator/health")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.status")
            .isEqualTo("UP")
    }

    @Test
    fun `los modulos instalados responden y los que se dejan fuera no`() {
        val token = bearer()
        val name = uniqueName("objeto")
        client
            .post()
            .uri("/api/objects")
            .header(HttpHeaders.AUTHORIZATION, token)
            .bodyValue(
                mapOf(
                    "name" to name,
                    "label" to name,
                    "pluralLabel" to name,
                    "fields" to listOf(mapOf("name" to "code", "label" to "Código", "type" to "TEXT"))
                )
            ).exchange()
            .expectStatus()
            .isCreated
        // views, forms y pages: los starters de F1
        listOf("/api/objects/$name/views", "/api/objects/$name/forms", "/api/pages").forEach { path ->
            client
                .get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isOk
        }
        // documents, workflow, gis, automation y agent llegan en su fase: hoy sus rutas no existen
        listOf(
            "/api/objects/$name/document-types",
            "/api/objects/$name/records/${UUID.randomUUID()}/transitions",
            "/api/gis/layers",
            "/api/automation-runs",
            "/api/agent/status"
        ).forEach { path ->
            client
                .get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, token)
                .exchange()
                .expectStatus()
                .isNotFound
        }
    }

    @Test
    fun `una ruta de sgspe sin token da 401`() {
        client
            .get()
            .uri("/api/sgspe/no-existe")
            .exchange()
            .expectStatus()
            .isUnauthorized
    }
}
```

- [ ] **Paso 5: correr la prueba y ver que falla**

```bash
cd /Users/jorge/IdeaProjects/sgspe
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) \
./gradlew integrationTest --tests 'sgspe.SgspeSmokeTest' --rerun
```

Esperado: FAIL, con `Unable to find a @SpringBootConfiguration` en el reporte (todavía no hay `SgspeApplication`). Si el
túnel no está abierto, el error es de conexión a `localhost:5435`: abrirlo (Tarea 1, Paso 8) antes de seguir.

- [ ] **Paso 6: `src/main/kotlin/sgspe/SgspeApplication.kt`** (copiado de caja-backend@dc9e391
  `src/main/kotlin/caja/CajaApplication.kt`; ediciones: paquete `sgspe`, clase `SgspeApplication`, comentario)

```kotlin
package sgspe

import org.springframework.boot.runApplication
import wasichai.core.autoconfigure.WasichaiApplication

// el servidor entero: los starters de wasichai en el classpath hacen el resto.
// nunca bajo el paquete `wasichai`: el escaneo de componentes registraría dos veces los controladores de la librería
// (WAS-ADR-0024). ArchitectureTest lo vigila
@WasichaiApplication
class SgspeApplication

fun main(args: Array<String>) {
    runApplication<SgspeApplication>(*args)
}
```

- [ ] **Paso 7: `src/main/resources/application.yml`** (adaptado de caja-backend@dc9e391
  `src/main/resources/application.yml`: puerto 8094, base `sgspe` en 5435, secreto de desarrollo propio, sondas; sin las
  claves `caja.*` ni `logging` de openhtmltopdf)

```yaml
server:
  port: 8094

spring:
  application:
    name: sgspe

wasichai:
  database:
    host: ${WASICHAI_DB_HOST:localhost}
    # compose.yml publica PostGIS en 127.0.0.1:5435 del VPS y llega por el túnel ssh. nunca 5432 ni 5434: son de otras apps
    port: ${WASICHAI_DB_PORT:5435}
    name: ${WASICHAI_DB_NAME:sgspe}
    username: ${WASICHAI_DB_USERNAME:sgspe}
    password: ${WASICHAI_DB_PASSWORD:sgspe}
  seed:
    # admin@wasichai.local / admin. solo para desarrollo: application-prod.yml lo apaga
    dev: ${WASICHAI_SEED_DEV:true}
  security:
    jwt:
      # solo para desarrollo. producción exige WASICHAI_JWT_SECRET (>= 32 bytes) sin valor por defecto (application-prod.yml)
      secret: ${WASICHAI_JWT_SECRET:sgspe-dev-only-secret-change-me-0123456789}

management:
  endpoint:
    health:
      # /actuator/health/liveness y /actuator/health/readiness, públicos en la cadena de wasichai (ADR-0026)
      probes:
        enabled: true
```

- [ ] **Paso 8: correr la prueba y verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) \
./gradlew integrationTest --tests 'sgspe.SgspeSmokeTest' --rerun
```

Esperado: `BUILD SUCCESSFUL`, 3 pruebas en verde.

- [ ] **Paso 9: formato y build**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build
```

Esperado: `BUILD SUCCESSFUL` (ktlint limpio; ninguna prueba unitaria todavía).

- [ ] **Paso 10: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar gradle/wrapper/gradle-wrapper.properties \
  .editorconfig .gitignore settings.gradle.kts build.gradle.kts \
  src/main/kotlin/sgspe/SgspeApplication.kt src/main/resources/application.yml src/test/kotlin/sgspe/SgspeSmokeTest.kt
git commit -F - <<'EOF'
build: app sgspe sobre wasichai 0.3.2 con su prueba de humo

Gradle 9.8.0 y los starters core, views, forms y pages, como caja-backend.
integrationTest corre sobre postgis/postgis:18-3.6; la app escucha en el puerto 8094.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 4: A2 · Compose, variables de desarrollo y guía `docs/develop/README.md`

**Pista:** A.

**Files:**
- Commit: `compose.yml` (escrito en la Tarea 1, Paso 6; sin cambios)
- Create: `develop/example.env`, `docs/develop/README.md`

**Interfaces:**
- Consumes: `compose.yml` (Tarea 1); `application.yml` (Tarea 3).
- Produces: la guía que usan la Tarea 20 y los README de la Tarea 21; las variables `WASICHAI_*` y `SGSPE_PG_PORT`.

Esta tarea no tiene prueba automática: su verificación son `docker compose config -q` y las sondas de salud del Paso 4.

- [ ] **Paso 1: `develop/example.env`** (adaptado de caja-backend@dc9e391 `develop/example.env`)

```bash
# Variables de ejemplo para desarrollar sgspe en local (adaptado de caja-backend).
#
#   cp develop/example.env develop/.env      # develop/.env está en .gitignore: nunca se versiona
#   set -a; source develop/.env; set +a      # exporta las variables al shell actual
#   GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun
#
# El token de GitHub Packages nunca va en este archivo. Guía completa: docs/develop/README.md

# --- Base de datos (compose.yml: postgis/postgis:18-3.6 en 127.0.0.1:5435 del VPS, por túnel ssh) ---
WASICHAI_DB_HOST=localhost
WASICHAI_DB_PORT=5435
WASICHAI_DB_NAME=sgspe
WASICHAI_DB_USERNAME=sgspe
WASICHAI_DB_PASSWORD=sgspe
# puerto que publica compose.yml (debe coincidir con WASICHAI_DB_PORT)
SGSPE_PG_PORT=5435

# --- Seguridad (solo desarrollo) ---
# >= 32 bytes. en cualquier entorno real, uno propio y secreto (application-prod.yml lo exige)
WASICHAI_JWT_SECRET=sgspe-dev-only-secret-change-me-0123456789
# crea admin@wasichai.local / admin. apagarlo (false) fuera de desarrollo
WASICHAI_SEED_DEV=true

# --- Scripts del modelo (model/apply.py y model/apply_roles.py) ---
WASICHAI_CORE=http://localhost:8094
WASICHAI_EMAIL=admin@wasichai.local
WASICHAI_PASSWORD=admin

# --- Pruebas de integración contra la base externa del VPS (Docker remoto). El nombre debe terminar en _test ---
# WASICHAI_TEST_DB_HOST=localhost
# WASICHAI_TEST_DB_PORT=5435
# WASICHAI_TEST_DB_NAME=sgspe_test
# WASICHAI_TEST_DB_USERNAME=sgspe
# WASICHAI_TEST_DB_PASSWORD=sgspe
```

- [ ] **Paso 2: `docs/develop/README.md`** (adaptado de caja-backend@dc9e391 `docs/develop/README.md`)

````markdown
# Desarrollo local

Cómo preparar, iniciar y probar `sgspe` en una máquina de desarrollo. La descripción del proyecto está en el
[README principal](../../README.md) y el diseño en [`docs/`](../README.md).

## 1. Requisitos

| Herramienta | Versión | Para |
|---|---|---|
| JDK | 25 | el servidor (toolchain de Gradle) |
| Docker | cualquiera reciente, local o remoto | PostGIS (`compose.yml`) |
| Python | 3.11 o superior, solo biblioteca estándar | los scripts de `model/` |
| Node y Yarn | Node 26 o superior, Yarn 1 | commitlint, husky y prettier; la interfaz es `../sgspe-ui` |
| GitHub CLI | con el alcance `read:packages` | el token de GitHub Packages |

Las librerías de wasichai (`wasichai:wasichai-bom:0.3.2` y sus starters) se descargan de GitHub Packages, que pide un
token aunque sea para leer. El token nunca se escribe en un archivo del repositorio: se antepone a cada comando de Gradle.

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build
```

Si falta el alcance: `gh auth refresh -s read:packages`.

## 2. Variables de entorno

```bash
cp develop/example.env develop/.env      # develop/.env está en .gitignore
set -a; source develop/.env; set +a
```

| Variable | Ejemplo | Uso |
|---|---|---|
| `WASICHAI_DB_HOST` / `_PORT` / `_NAME` | `localhost` / `5435` / `sgspe` | a qué PostgreSQL se conecta el servidor |
| `WASICHAI_DB_USERNAME` / `_PASSWORD` | `sgspe` / `sgspe` | credenciales de esa base |
| `SGSPE_PG_PORT` | `5435` | puerto que publica `compose.yml` en `127.0.0.1` |
| `WASICHAI_JWT_SECRET` | valor de desarrollo | firma de los tokens; 32 bytes o más; en producción, obligatorio y sin valor por defecto |
| `WASICHAI_SEED_DEV` | `true` | crea `admin@wasichai.local` / `admin`; producción lo apaga |
| `WASICHAI_CORE` | `http://localhost:8094` | URL de los scripts de `model/` |
| `WASICHAI_EMAIL` / `WASICHAI_PASSWORD` | el administrador de desarrollo | login de esos scripts |
| `WASICHAI_TEST_DB_*` | comentadas | pruebas de integración contra la base externa (sección 6) |

Todas tienen el mismo valor por defecto en `src/main/resources/application.yml`: con la base del compose y el túnel, el
servidor arranca sin cargar nada.

## 3. Base de datos

El Docker de desarrollo es remoto: `DOCKER_HOST` es un socket reenviado por SSH al VPS. El contenedor corre allá y publica
su puerto en el loopback del VPS; a esta máquina llega por un túnel.

```bash
docker compose up -d                               # postgis/postgis:18-3.6, contenedor sgspe-postgis, base sgspe
docker compose ps                                  # debe decir healthy
ssh -fN -L 5435:localhost:5435 "$SGSPE_VPS"        # el alias SSH del VPS
docker compose exec postgis createdb -U sgspe sgspe_test   # una sola vez: la base de las pruebas externas
```

- `compose.yml` usa un volumen con nombre y ningún bind mount, porque el demonio es remoto.
- Al arrancar, el servidor crea el esquema con las migraciones de wasichai. SGSPE no tiene migraciones propias en F1.
- Los servicios de apoyo llegan con su fase: almacenamiento S3 y SMTP de prueba en F3, GeoServer en F6 y Gotenberg en F8.
- Empezar de cero: `docker compose down -v` borra el volumen y todos los datos.

## 4. Iniciar el backend

```bash
set -a; source develop/.env; set +a
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun
curl -s http://localhost:8094/actuator/health/liveness     # {"status":"UP"}
curl -s http://localhost:8094/actuator/health/readiness    # {"status":"UP"}
```

La clase principal es `sgspe.SgspeApplication`. Toda respuesta trae `X-Correlation-Id` (sección 7).

## 5. Modelo y roles

El modelo vive en `model/` y se aplica por REST, con el servidor arriba, siempre en este orden (`security.md` §4.1):

```bash
cd model
python3 apply.py --validate-only        # el modelo cumple las reglas de wasichai (no necesita servidor)
python3 apply.py                        # objetos, acciones, relaciones e índices. En F1: "done: 0 created, 0 updated, 0 skipped"
python3 apply_roles.py                  # los 15 roles declarados. La segunda vez: "done: 0 created, 0 updated, 15 skipped"
python3 apply.py --workflows            # workflows, tipos de documento, automatizaciones, vistas, formularios y páginas (desde F2)
python3 -m unittest -v                  # las pruebas de los scripts, contra un core falso
```

- `roles.json` es la fuente de verdad: volver a correr `apply_roles.py` deshace lo editado a mano en `/admin`.
- `SUPER_ADMIN` y `ORGANIZATION_ADMIN` no se declaran: son el `ADMIN` de wasichai (`mappedToAdmin`).
- Un tenant nuevo se prepara con `POST /api/organizations` y después estos tres pasos, con el administrador del tenant.

## 6. Pruebas

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat   # formatea el Kotlin
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build          # ktlint y pruebas unitarias
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Las pruebas de integración llevan `@Tag("integration")` (lo heredan de `WasichaiIntegrationTest`): `build` las excluye e
`integrationTest` las corre. Levantan la app entera y aprovisionan el modelo con los scripts reales de `model/`, así que
necesitan `python3` en el `PATH`.

**Por qué no corren con Testcontainers en esta máquina.** Testcontainers crea el contenedor en el demonio remoto, pero lo
busca en `localhost`: ni Ryuk ni PostgreSQL responden. Hay dos caminos:

1. La CI: el trabajo `integration` de `.github/workflows/ci.yml` corre con Testcontainers en el runner.
2. La base externa del VPS por el túnel: descomentar `WASICHAI_TEST_DB_*` en `develop/.env`. Su nombre termina en `_test`
   porque la suite la vacía una vez por JVM.

   ```bash
   set -a; source develop/.env; set +a
   GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun
   ```

   `--rerun` evita que la caché de Gradle devuelva un verde viejo. Detalles en
   `wasichai/docs/development/getting-started.md#integration-tests`.

## 7. Observabilidad

- `X-Correlation-Id`: se acepta el del cliente si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, se genera un UUID. Va en toda
  respuesta, también en un `401`, y en la línea de acceso (`sgspe.access`): método, ruta sin la cadena de consulta, estado,
  duración e identificador.
- Producción (`application-prod.yml`): sin semilla, `WASICHAI_JWT_SECRET` obligatorio, token de 4 horas y logs JSON ECS.

## 8. Con la interfaz (`../sgspe-ui`)

El servidor de Vite (puerto 5182) reenvía `/api` a `http://localhost:8094`: mismo origen, sin CORS.

```bash
# terminal 1
set -a; source develop/.env; set +a
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun

# terminal 2
cd ../sgspe-ui && yarn dev
```

Entrar con `admin@wasichai.local` / `admin`.

## Problemas comunes

| Síntoma | Causa probable |
|---|---|
| `Could not find wasichai:wasichai-spring-boot-starter…` | falta el prefijo `GITHUB_ACTOR=… GITHUB_TOKEN=…` o el alcance `read:packages` |
| `Connection refused` a `localhost:5435` | el contenedor no está arriba o falta el túnel |
| El servidor no arranca: falta `wasichai.security.jwt.secret` | perfil `prod` sin `WASICHAI_JWT_SECRET` |
| Puerto 8094 ocupado | otro backend corriendo: `lsof -iTCP:8094 -sTCP:LISTEN` |
| `integrationTest` no arranca (Ryuk o PostgreSQL no responden) | Docker remoto sin `WASICHAI_TEST_DB_*` (sección 6) |
````

- [ ] **Paso 3: comprobar el compose**

```bash
cd /Users/jorge/IdeaProjects/sgspe
docker compose config -q && echo compose-ok
```

Esperado: `compose-ok`.

- [ ] **Paso 4: arrancar la app y comprobar las sondas**

En segundo plano (herramienta Bash con `run_in_background`):

```bash
cd /Users/jorge/IdeaProjects/sgspe
cp develop/example.env develop/.env
set -a; source develop/.env; set +a
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun
```

Cuando el log diga `Started SgspeApplication`:

```bash
curl -s http://localhost:8094/actuator/health/liveness; echo
curl -s http://localhost:8094/actuator/health/readiness; echo
```

Esperado: `{"status":"UP"}` dos veces. Detener `bootRun` después.

- [ ] **Paso 5: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git status --short develop/
git add compose.yml develop/example.env docs/develop/README.md
git commit -F - <<'EOF'
build: compose de PostGIS en el VPS y guía de desarrollo

PostGIS 18-3.6 en 127.0.0.1:5435 con volumen con nombre, por túnel ssh.
develop/example.env y docs/develop/README.md siguen el patrón de caja-backend.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

Esperado antes del commit: `git status --short develop/` muestra `?? develop/example.env` y no muestra `develop/.env`
(lo ignora `.gitignore`).

---

### Tarea 5: A3 · Herramientas de Node: prettier, commitlint, husky y `SECURITY.md`

**Pista:** A.

**Files:**
- Create (copia exacta de caja-backend@dc9e391): `commitlint.config.mjs`, `.husky/commit-msg`, `.prettierrc.json`,
  `.prettierignore`
- Create: `package.json`, `yarn.lock` (generado), `SECURITY.md` (copiado con una edición)

**Interfaces:**
- Produces: `yarn format`, `yarn format:check` y el gancho `commit-msg` que valida cada commit siguiente de `sgspe`.

- [ ] **Paso 1: comprobar que todavía no hay herramientas**

```bash
cd /Users/jorge/IdeaProjects/sgspe
yarn -s format:check; echo "salida: $?"
```

Esperado: error «Couldn't find a package.json file» y `salida: 1`.

- [ ] **Paso 2: copiar de caja**

```bash
cd /Users/jorge/IdeaProjects/sgspe
mkdir -p .husky
cp /Users/jorge/IdeaProjects/caja-backend/commitlint.config.mjs /Users/jorge/IdeaProjects/caja-backend/.prettierrc.json \
  /Users/jorge/IdeaProjects/caja-backend/.prettierignore /Users/jorge/IdeaProjects/caja-backend/SECURITY.md .
cp /Users/jorge/IdeaProjects/caja-backend/.husky/commit-msg .husky/commit-msg
sed -i '' 's#wasichai/caja-backend/security#wasichai/sgspe/security#' SECURITY.md
grep -c 'wasichai/sgspe/security/advisories/new' SECURITY.md
```

Esperado: `1`. Ninguna otra edición: `.prettierignore` de caja ya deja fuera `*.md`, `*.kt`, `*.kts`, `*.py`, `docs/`,
`model/`, `gradle/`, `build/` y `yarn.lock`.

- [ ] **Paso 3: `package.json`** (adaptado de caja-backend@dc9e391 `package.json`: solo el nombre)

```json
{
    "name": "sgspe-build",
    "private": true,
    "version": "0.1.0",
    "engines": {
        "node": ">=26"
    },
    "scripts": {
        "prepare": "husky",
        "format": "prettier --write .",
        "format:check": "prettier --check ."
    },
    "devDependencies": {
        "@commitlint/cli": "21.2.3",
        "@commitlint/config-conventional": "21.2.3",
        "husky": "9.1.7",
        "prettier": "3.9.9"
    }
}
```

- [ ] **Paso 4: instalar y formatear**

```bash
cd /Users/jorge/IdeaProjects/sgspe
yarn install
yarn -s format
yarn -s format:check; echo "salida: $?"
git config --get core.hooksPath
```

Esperado: `yarn.lock` creado (v1); `All matched files use Prettier code style!` y `salida: 0`; `.husky/_`.

- [ ] **Paso 5: commitlint rechaza un mensaje inválido y acepta uno válido**

```bash
cd /Users/jorge/IdeaProjects/sgspe
printf 'cambios varios\n' | yarn -s commitlint; echo "salida: $?"
printf 'chore: prueba de commitlint\n' | yarn -s commitlint; echo "salida: $?"
```

Esperado: el primero lista `subject may not be empty` y `type may not be empty` con `salida: 1`; el segundo, `salida: 0`.

- [ ] **Paso 6: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add package.json yarn.lock commitlint.config.mjs .husky/commit-msg .prettierrc.json .prettierignore SECURITY.md \
  compose.yml src/main/resources/application.yml
git commit -F - <<'EOF'
build: prettier, commitlint y husky como caja-backend

yarn format:check revisa yaml y json; el gancho commit-msg valida Conventional Commits.
SECURITY.md apunta a los avisos privados del repositorio sgspe.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

`compose.yml` y `application.yml` van en el commit solo si `yarn format` los cambió; si `git status --short` no los
muestra, se quitan de la lista de `git add`.

---

### Tarea 6: A4 · `model/`: modelo vacío, los 17 roles y los scripts de aprovisionamiento

**Pista:** A.

**Files:**
- Create (copia de caja-backend@dc9e391 con las ediciones listadas): `model/core_client.py`, `model/fake_core.py`,
  `model/apply.py`
- Create: `model/apply_roles.py` (reescrito a partir del de caja), `model/fixture_model.py`, `model/model.json`,
  `model/roles.json`
- Test: `model/test_apply.py`, `model/test_apply_roles.py`

**Interfaces:**
- Produces:
  - `apply.validate(model) -> list[str]`; `apply.main(argv) -> int` con `--validate-only`, `--dry-run`, `--drop` y
    `--workflows`; `apply.do_workflows(model_dir) -> int`; `apply.DEFINITION_DIRS`.
  - `apply_roles.validate(roles, model) -> list[str]`; `apply_roles.permissions_payload(role) -> {"permissions": [...]}`;
    `apply_roles.field_permissions_payload(role, model) -> {"fields": [...]}`; `apply_roles.main(argv) -> int` con
    `--roles`, `--model`, `--core`, `--email`, `--password` y `--dry-run`.
  - Salidas que esperan otras tareas: `apply.py` en F1 imprime `done: 0 created, 0 updated, 0 skipped`; `apply_roles.py`
    imprime `done: 15 created, 0 updated, 0 skipped` y luego `done: 0 created, 0 updated, 15 skipped`; `apply.py --workflows`
    imprime `done: 0 workflows, 0 document types, 0 automations, 0 views, 0 forms, 0 pages` sin llamar a wasichai.
  - `model/roles.json`: `{"mappedToAdmin": [...], "roles": [{"name", "label", "description", "permisos"}]}`; `"*"` es el
    permiso de todo el tenant; `u` es UPDATE más campos de solo lectura.

- [ ] **Paso 1: copiar los scripts de caja**

```bash
cd /Users/jorge/IdeaProjects/sgspe
mkdir -p model
cp /Users/jorge/IdeaProjects/caja-backend/model/core_client.py /Users/jorge/IdeaProjects/caja-backend/model/fake_core.py \
  /Users/jorge/IdeaProjects/caja-backend/model/apply.py model/
sed -i '' 's/shared by apply.py and the importers\./shared by apply.py and apply_roles.py (copied from caja-backend@dc9e391)./' model/core_client.py
head -1 model/core_client.py
```

Esperado: `"""Minimal wasichai Core REST client shared by apply.py and apply_roles.py (copied from caja-backend@dc9e391).`
`core_client.py` no cambia en nada más.

- [ ] **Paso 2: `model/model.json`** (F1 sin objetos)

```json
{
  "enums": {},
  "objects": [],
  "relationships": []
}
```

- [ ] **Paso 3: `model/roles.json`** (los 15 roles de `security.md` §6.1; solo `AUDITOR` con permisos en F1)

```json
{
  "mappedToAdmin": ["SUPER_ADMIN", "ORGANIZATION_ADMIN"],
  "roles": [
    {
      "name": "PROJECT_ADMIN",
      "label": "Administrador de proyectos",
      "description": "Da de alta y aprueba proyectos y su equipo. Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "PROJECT_MANAGER",
      "label": "Jefe de proyecto",
      "description": "Aprueba la lista de entrevistados y las decisiones de nivel superior del proyecto. Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "SOCIAL_MANAGER",
      "label": "Gerente social",
      "description": "Supervisa el plan de trabajo social. Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "COMMUNITY_RELATIONS_OFFICER",
      "label": "Relacionista comunitario",
      "description": "El equipo social en el territorio. Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "STAKEHOLDER_MANAGER",
      "label": "Gestor de grupos de interés",
      "description": "Mapea y evalúa los grupos de interés. Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "COMMITMENT_MANAGER",
      "label": "Gestor de compromisos",
      "description": "Lleva la matriz de compromisos. Recibe permisos desde F3.",
      "permisos": {}
    },
    {
      "name": "GRIEVANCE_MANAGER",
      "label": "Gestor de quejas, consultas y solicitudes",
      "description": "Atiende las quejas, consultas y solicitudes. Recibe permisos desde F4.",
      "permisos": {}
    },
    {
      "name": "SOCIAL_RISK_MANAGER",
      "label": "Gestor de riesgos sociales",
      "description": "Lleva la matriz de riesgos sociales. Recibe permisos desde F4.",
      "permisos": {}
    },
    {
      "name": "SOCIAL_INVESTMENT_MANAGER",
      "label": "Gestor de inversión social",
      "description": "Gestiona los aportes y los programas sociales. Recibe permisos desde F5.",
      "permisos": {}
    },
    {
      "name": "GIS_ANALYST",
      "label": "Analista SIG",
      "description": "Prepara mapas e importa coordenadas. Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "FIELD_AGENT",
      "label": "Agente de campo",
      "description": "Registra en terreno lo que levanta; sin EDIT_ANY, solo cambia lo que creó (ASM-069). Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "APPROVER",
      "label": "Aprobador",
      "description": "Aprueba mediante acciones declaradas como APPROVE. Recibe permisos desde F3.",
      "permisos": {}
    },
    {
      "name": "AUDITOR",
      "label": "Auditor",
      "description": "Lee todo el tenant y la auditoría; no escribe nada.",
      "permisos": {
        "*": ["READ"]
      }
    },
    {
      "name": "ANALYST",
      "label": "Analista",
      "description": "Lee y reporta por objeto. Recibe permisos desde F2.",
      "permisos": {}
    },
    {
      "name": "READ_ONLY",
      "label": "Solo lectura",
      "description": "Consulta. Recibe permisos desde F2.",
      "permisos": {}
    }
  ]
}
```

- [ ] **Paso 4: `model/fixture_model.py`** (modelo de prueba: F1 no tiene objetos)

```python
"""A small model for the tests of apply.py and apply_roles.py, because model.json has no objects in F1.

Two objects, one relationship, one declared action and one derived field: enough to exercise the payloads, `u` and
"derived": true. Test helper only.
"""

FIXTURE_MODEL = {
    "enums": {"zone_type": ["DIRECTA", "INDIRECTA"]},
    "objects": [
        {
            "name": "zone",
            "label": "Zona",
            "pluralLabel": "Zonas",
            "fields": [
                {"name": "code", "label": "Código", "type": "TEXT", "required": True, "unique": True},
                {"name": "zone_type", "label": "Tipo", "type": "ENUM", "enum": "zone_type"},
                {"name": "area_ha", "label": "Área (ha)", "type": "DECIMAL", "derived": True},
            ],
        },
        {
            "name": "visit",
            "label": "Visita",
            "pluralLabel": "Visitas",
            "requiresReason": True,
            "actions": [{"name": "EDIT_ANY", "label": "Editar lo ajeno"}],
            "fields": [{"name": "visit_date", "label": "Fecha", "type": "DATE", "required": True}],
        },
    ],
    "relationships": [
        {"name": "visit_zone", "label": "Zona", "inverseLabel": "Visitas", "source": "visit", "target": "zone",
         "fieldName": "zone"},
    ],
}
```

- [ ] **Paso 5: escribir la prueba que falla, `model/test_apply_roles.py`**

```python
"""Pruebas de roles.json y de apply_roles.py contra un FakeCore (security.md §6.3 y §14, filas de F1).

Correr: cd model && python3 -m unittest -v
"""
import copy
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import apply_roles
from fake_core import FakeCore
from fixture_model import FIXTURE_MODEL

HERE = os.path.dirname(os.path.abspath(__file__))
ROLES_PATH = os.path.join(HERE, "roles.json")
MODEL_PATH = os.path.join(HERE, "model.json")

# los 15 roles declarados, en el orden de security.md §6.1, con su etiqueta en español
LABELS = {
    "PROJECT_ADMIN": "Administrador de proyectos",
    "PROJECT_MANAGER": "Jefe de proyecto",
    "SOCIAL_MANAGER": "Gerente social",
    "COMMUNITY_RELATIONS_OFFICER": "Relacionista comunitario",
    "STAKEHOLDER_MANAGER": "Gestor de grupos de interés",
    "COMMITMENT_MANAGER": "Gestor de compromisos",
    "GRIEVANCE_MANAGER": "Gestor de quejas, consultas y solicitudes",
    "SOCIAL_RISK_MANAGER": "Gestor de riesgos sociales",
    "SOCIAL_INVESTMENT_MANAGER": "Gestor de inversión social",
    "GIS_ANALYST": "Analista SIG",
    "FIELD_AGENT": "Agente de campo",
    "APPROVER": "Aprobador",
    "AUDITOR": "Auditor",
    "ANALYST": "Analista",
    "READ_ONLY": "Solo lectura",
}
ADMIN_ACTIONS = {"MANAGE_METADATA", "MANAGE_ORGANIZATION"}


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def grants(role):
    """Los permisos de un rol de roles.json como pares (objeto, acción)."""
    return {(obj, action) for obj, actions in role["permisos"].items() for action in actions}


class RolesJsonTests(unittest.TestCase):
    def setUp(self):
        self.file = load(ROLES_PATH)
        self.roles = {r["name"]: r for r in self.file["roles"]}

    def test_declara_los_15_roles_en_orden_con_su_etiqueta_en_espanol(self):
        self.assertEqual([(r["name"], r["label"]) for r in self.file["roles"]], list(LABELS.items()))

    def test_dos_roles_son_el_admin_de_wasichai_y_suman_los_17_del_prompt(self):
        self.assertEqual(self.file["mappedToAdmin"], ["SUPER_ADMIN", "ORGANIZATION_ADMIN"])
        self.assertEqual(len(set(self.roles) | set(self.file["mappedToAdmin"])), 17)
        self.assertTrue(set(self.roles).isdisjoint({"ADMIN", "SUPER_ADMIN", "ORGANIZATION_ADMIN"}))

    def test_solo_auditor_tiene_un_permiso_de_todo_el_tenant_y_es_de_lectura(self):
        tenant = {name for name, role in self.roles.items() if "*" in role["permisos"]}
        self.assertEqual(tenant, {"AUDITOR"})
        self.assertEqual(self.roles["AUDITOR"]["permisos"], {"*": ["READ"]})

    def test_en_f1_los_demas_roles_existen_sin_permisos(self):
        for name, role in self.roles.items():
            if name != "AUDITOR":
                self.assertEqual(role["permisos"], {}, name)

    def test_ningun_rol_lleva_own_records_only(self):
        for name, role in self.roles.items():
            self.assertNotIn("ownRecordsOnly", role, name)

    def test_field_agent_no_tiene_edit_any(self):
        self.assertNotIn("EDIT_ANY", {action for _, action in grants(self.roles["FIELD_AGENT"])})

    def test_ningun_rol_tiene_permisos_de_administracion(self):
        for name, role in self.roles.items():
            self.assertTrue(ADMIN_ACTIONS.isdisjoint({action for _, action in grants(role)}), name)

    def test_valida_contra_el_modelo(self):
        self.assertEqual(apply_roles.validate(self.file, load(MODEL_PATH)), [])

    def test_ningun_derivado_del_modelo_usa_editable(self):
        # vacía en F1; desde F2 vigila que un derivado se marque con "derived": true y nunca con editable: false
        for obj in load(MODEL_PATH)["objects"]:
            for field in obj["fields"]:
                self.assertNotIn("editable", field, f"{obj['name']}.{field['name']}")


class ValidationTests(unittest.TestCase):
    def setUp(self):
        self.file = load(ROLES_PATH)

    def mutate(self, fn, model=None):
        roles = copy.deepcopy(self.file)
        fn(roles)
        return apply_roles.validate(roles, model or load(MODEL_PATH))

    @staticmethod
    def role(name):
        return {"name": name, "label": "Rol de prueba", "permisos": {}}

    def test_declarar_el_admin_o_sus_dos_roles_se_rechaza(self):
        for name in ("ADMIN", "SUPER_ADMIN", "ORGANIZATION_ADMIN"):
            errors = self.mutate(lambda r, n=name: r["roles"].append(self.role(n)))
            self.assertTrue(any(f"role {name}: is wasichai's ADMIN" in e for e in errors), errors)

    def test_mapped_to_admin_debe_ser_exactamente_los_dos(self):
        errors = self.mutate(lambda r: r.update({"mappedToAdmin": ["SUPER_ADMIN"]}))
        self.assertTrue(any(e.startswith("mappedToAdmin must be") for e in errors), errors)

    def test_own_records_only_se_rechaza(self):
        errors = self.mutate(lambda r: r["roles"][10].update({"ownRecordsOnly": True}))
        self.assertTrue(any("FIELD_AGENT: ownRecordsOnly is not used" in e for e in errors), errors)

    def test_el_permiso_de_todo_el_tenant_solo_admite_lectura(self):
        errors = self.mutate(lambda r: r["roles"][12]["permisos"].update({"*": ["READ", "UPDATE"]}))
        self.assertTrue(any("AUDITOR: '*' only takes" in e for e in errors), errors)

    def test_una_accion_de_administracion_se_rechaza(self):
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"zone": ["MANAGE_METADATA"]}), FIXTURE_MODEL)
        self.assertTrue(any("MANAGE_METADATA" in e for e in errors), errors)

    def test_un_objeto_que_el_modelo_no_tiene_se_rechaza(self):
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"project": ["READ"]}))
        self.assertTrue(any("object 'project' is not in model.json" in e for e in errors), errors)

    def test_una_accion_declarada_solo_vale_sobre_su_objeto(self):
        ok = self.mutate(lambda r: r["roles"][0]["permisos"].update({"visit": ["READ", "EDIT_ANY"]}), FIXTURE_MODEL)
        self.assertEqual(ok, [])
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"zone": ["EDIT_ANY"]}), FIXTURE_MODEL)
        self.assertTrue(any("action 'EDIT_ANY' on zone" in e for e in errors), errors)

    def test_u_y_update_juntos_se_rechazan(self):
        errors = self.mutate(lambda r: r["roles"][0]["permisos"].update({"zone": ["READ", "u", "UPDATE"]}), FIXTURE_MODEL)
        self.assertTrue(any("'u' and UPDATE on zone" in e for e in errors), errors)

    def test_un_nombre_que_wasichai_no_acepta_y_un_rol_repetido_se_rechazan(self):
        errors = self.mutate(lambda r: r["roles"].append(self.role("prueba")))
        self.assertTrue(any("role 'prueba': name must match" in e for e in errors), errors)
        errors = self.mutate(lambda r: r["roles"].append(copy.deepcopy(r["roles"][0])))
        self.assertTrue(any("duplicate role name" in e for e in errors), errors)


class ExpansionTests(unittest.TestCase):
    ROLE = {"name": "PRUEBA", "label": "Rol de prueba", "permisos": {"visit": ["READ", "u"], "zone": ["READ", "u"]}}

    def test_u_es_update_en_los_permisos(self):
        self.assertEqual(apply_roles.permissions_payload(self.ROLE), {"permissions": [
            {"objectName": "visit", "action": "READ"}, {"objectName": "visit", "action": "UPDATE"},
            {"objectName": "zone", "action": "READ"}, {"objectName": "zone", "action": "UPDATE"}]})

    def test_u_deja_de_solo_lectura_cada_campo_no_derivado_y_el_de_cada_relacion(self):
        # area_ha es derivado: queda escribible para los listeners y los endpoints
        self.assertEqual(apply_roles.field_permissions_payload(self.ROLE, FIXTURE_MODEL), {"fields": [
            {"objectName": "visit", "fieldName": "visit_date", "read": True, "write": False},
            {"objectName": "visit", "fieldName": "zone", "read": True, "write": False},
            {"objectName": "zone", "fieldName": "code", "read": True, "write": False},
            {"objectName": "zone", "fieldName": "zone_type", "read": True, "write": False}]})

    def test_el_permiso_de_todo_el_tenant_va_sin_objeto(self):
        auditor = {"name": "AUDITOR", "label": "Auditor", "permisos": {"*": ["READ"]}}
        self.assertEqual(apply_roles.permissions_payload(auditor), {"permissions": [{"objectName": None, "action": "READ"}]})
        self.assertEqual(apply_roles.field_permissions_payload(auditor, FIXTURE_MODEL), {"fields": []})


class ApplyRolesTestCase(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)

    def run_cli(self, extra=(), roles_path=ROLES_PATH, model_path=MODEL_PATH):
        args = ["--roles", roles_path, "--model", model_path, "--core", self.core.base_url,
                "--email", "admin@wasichai.local", "--password", "admin", *extra]
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            code = apply_roles.main(args)
        return code, out.getvalue(), err.getvalue()

    def writes(self):
        return [(m, p, b) for m, p, _, b in self.core.requests if m in ("POST", "PUT") and p != "/api/auth/login"]


class ApplyRolesTests(ApplyRolesTestCase):
    def test_dry_run_no_llama_a_core_e_imprime_los_15_roles(self):
        code, out, err = self.run_cli(["--dry-run"])
        self.assertEqual(code, 0, err)
        self.assertEqual(self.core.requests, [])
        self.assertEqual(out.count("# POST /api/roles\n"), 15)

    def test_crea_los_15_roles_y_auditor_lee_todo_el_tenant(self):
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual([b["name"] for m, p, b in self.writes() if m == "POST"], list(LABELS))
        puts = {p: b for m, p, b in self.writes() if m == "PUT"}
        self.assertEqual(puts["/api/roles/AUDITOR/permissions"], {"permissions": [{"objectName": None, "action": "READ"}]})
        self.assertEqual(puts["/api/roles/FIELD_AGENT/permissions"], {"permissions": []})
        self.assertFalse(any(p.endswith("/field-permissions") for p in puts), puts)
        for method, path, auth, body in self.core.requests:
            self.assertEqual(auth, None if path == "/api/auth/login" else "Bearer t")
        self.assertIn("done: 15 created, 0 updated, 0 skipped", out)

    def test_la_segunda_corrida_no_escribe_nada(self):
        self.run_cli()
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual(self.writes(), [])
        self.assertIn("done: 0 created, 0 updated, 15 skipped", out)

    def test_deshace_lo_editado_a_mano_en_el_admin(self):
        # Review Focus 3: alguien activó ownRecordsOnly, cambió la etiqueta y dio un permiso a mano en /admin
        self.run_cli()
        self.core.roles["FIELD_AGENT"].update({"label": "Otro", "ownRecordsOnly": True,
                                               "permissions": [{"objectName": None, "action": "READ", "allowed": True}]})
        self.core.requests.clear()
        code, out, err = self.run_cli()
        self.assertEqual(code, 0, err)
        self.assertEqual(self.writes(), [
            ("PUT", "/api/roles/FIELD_AGENT", {"label": "Agente de campo", "ownRecordsOnly": False}),
            ("PUT", "/api/roles/FIELD_AGENT/permissions", {"permissions": []})])
        self.assertIn("done: 0 created, 1 updated, 14 skipped", out)
        self.core.requests.clear()
        self.assertIn("done: 0 created, 0 updated, 15 skipped", self.run_cli()[1])

    def test_sincroniza_los_permisos_de_campo_de_u(self):
        roles = {"mappedToAdmin": ["SUPER_ADMIN", "ORGANIZATION_ADMIN"],
                 "roles": [{"name": "PRUEBA", "label": "Rol de prueba", "permisos": {"zone": ["READ", "u"]}}]}
        with tempfile.TemporaryDirectory() as folder:
            roles_path = os.path.join(folder, "roles.json")
            model_path = os.path.join(folder, "model.json")
            for path, data in ((roles_path, roles), (model_path, FIXTURE_MODEL)):
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(data, f, ensure_ascii=False)
            code, out, err = self.run_cli(roles_path=roles_path, model_path=model_path)
            self.assertEqual(code, 0, err)
            self.assertIn(("PUT", "/api/roles/PRUEBA/field-permissions", {"fields": [
                {"objectName": "zone", "fieldName": "code", "read": True, "write": False},
                {"objectName": "zone", "fieldName": "zone_type", "read": True, "write": False}]}), self.writes())
            self.core.requests.clear()
            code, out, err = self.run_cli(roles_path=roles_path, model_path=model_path)
        self.assertEqual(code, 0, err)
        self.assertEqual(self.writes(), [])
        self.assertIn("done: 0 created, 0 updated, 1 skipped", out)

    def test_un_rechazo_de_core_detiene_la_corrida(self):
        self.core.fail_on_role = "SOCIAL_MANAGER"
        code, out, err = self.run_cli()
        self.assertEqual(code, 1)
        self.assertIn("error POST /api/roles", err)
        self.assertEqual([b["name"] for m, p, b in self.writes() if m == "POST"],
                         ["PROJECT_ADMIN", "PROJECT_MANAGER", "SOCIAL_MANAGER"])

    def test_un_roles_json_invalido_no_llama_a_core(self):
        roles = load(ROLES_PATH)
        roles["roles"][0]["permisos"]["project"] = ["READ"]
        with tempfile.TemporaryDirectory() as folder:
            path = os.path.join(folder, "roles.json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump(roles, f)
            code, out, err = self.run_cli(roles_path=path)
        self.assertEqual(code, 2)
        self.assertIn("project", out)
        self.assertEqual(self.core.requests, [])

    def test_un_archivo_que_falta_o_que_no_es_json_sale_con_2_sin_traza(self):
        with tempfile.TemporaryDirectory() as folder:
            broken = os.path.join(folder, "roto.json")
            with open(broken, "w", encoding="utf-8") as f:
                f.write("{ no es json")
            missing = os.path.join(folder, "no-existe.json")
            for extra, name in (([], missing), ([], broken), (["--model", missing], missing), (["--model", broken], broken)):
                roles = name if not extra else ROLES_PATH
                code, out, err = self.run_cli(extra, roles_path=roles)
                self.assertEqual(code, 2, (extra, name, out, err))
                self.assertIn(name, err)
                self.assertNotIn("Traceback", err)
        self.assertEqual(self.core.requests, [])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Paso 6: escribir la prueba que falla, `model/test_apply.py`** (reemplaza la de caja, que está atada a sus 13
  objetos)

```python
"""Pruebas de apply.py contra un FakeCore: el modelo vacío de F1, un modelo de prueba, el segundo pase (--workflows) y
el aprovisionamiento de un tenant nuevo en su orden (security.md §4.1 y §14, «Alta de un tenant»).

Correr: cd model && python3 -m unittest -v
"""
import copy
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import apply
import apply_roles
from fake_core import FakeCore
from fixture_model import FIXTURE_MODEL

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(HERE, "model.json")
ROLES_PATH = os.path.join(HERE, "roles.json")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def quiet(main, args):
    """Corre main(args) y devuelve (código, salida estándar, salida de errores)."""
    out, err = io.StringIO(), io.StringIO()
    with redirect_stdout(out), redirect_stderr(err):
        code = main(args)
    return code, out.getvalue(), err.getvalue()


def write_model(folder, model):
    path = os.path.join(folder, "model.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False)
    return path


class ModelOfF1Tests(unittest.TestCase):
    def test_f1_has_no_objects_and_is_valid(self):
        model = load(MODEL_PATH)
        self.assertEqual(model, {"enums": {}, "objects": [], "relationships": []})
        self.assertEqual(apply.validate(model), [])


class ValidationTests(unittest.TestCase):
    def mutate(self, fn):
        model = copy.deepcopy(FIXTURE_MODEL)
        fn(model)
        return apply.validate(model)

    def test_the_fixture_is_valid(self):
        self.assertEqual(apply.validate(FIXTURE_MODEL), [])

    def test_objects_must_be_a_list(self):
        self.assertIn("objects must be a list", self.mutate(lambda m: m.update({"objects": {}})))

    def test_derived_must_be_a_boolean(self):
        errors = self.mutate(lambda m: m["objects"][0]["fields"][2].update({"derived": "si"}))
        self.assertTrue(any("zone field area_ha: derived must be true or false" in e for e in errors), errors)

    def test_editable_is_refused(self):
        errors = self.mutate(lambda m: m["objects"][0]["fields"][2].update({"editable": False}))
        self.assertTrue(any("zone field area_ha: editable is not used" in e for e in errors), errors)


class ApplyTestCase(unittest.TestCase):
    def setUp(self):
        self.core = FakeCore()
        self.addCleanup(self.core.stop)

    def run_apply(self, model_path, *extra):
        return quiet(apply.main, ["--model", model_path, "--core", self.core.base_url,
                                  "--email", "admin@wasichai.local", "--password", "admin", *extra])

    def writes(self):
        return [(m, p, b) for m, p, _, b in self.core.requests if m in ("POST", "PUT", "DELETE") and p != "/api/auth/login"]


class EmptyModelTests(ApplyTestCase):
    def test_the_f1_model_writes_nothing(self):
        code, out, err = self.run_apply(MODEL_PATH)
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 created, 0 updated, 0 skipped", out)
        self.assertEqual(self.writes(), [])

    def test_dry_run_has_nothing_to_send(self):
        code, out, err = self.run_apply(MODEL_PATH, "--dry-run")
        self.assertEqual(code, 0, err)
        self.assertEqual(out, "")
        self.assertEqual(self.core.requests, [])


class FixtureApplyTests(ApplyTestCase):
    def test_creates_objects_action_and_relationship_in_order(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out, err = self.run_apply(write_model(folder, FIXTURE_MODEL))
        self.assertEqual(code, 0, err)
        posts = [(p, b) for m, p, b in self.writes() if m == "POST"]
        self.assertEqual([p for p, _ in posts],
                         ["/api/objects", "/api/objects", "/api/metadata/objects/visit/actions", "/api/relationships"])
        self.assertEqual([b["name"] for p, b in posts if p == "/api/objects"], ["zone", "visit"])
        self.assertTrue(next(b for p, b in posts if p == "/api/objects" and b["name"] == "visit")["requiresReason"])
        self.assertIn("done: 4 created, 0 updated, 0 skipped", out)

    def test_derived_never_reaches_wasichai(self):
        with tempfile.TemporaryDirectory() as folder:
            code, out, err = self.run_apply(write_model(folder, FIXTURE_MODEL))
        self.assertEqual(code, 0, err)
        zone = next(b for m, p, b in self.writes() if p == "/api/objects" and b["name"] == "zone")
        self.assertEqual([f["name"] for f in zone["fields"]], ["code", "zone_type", "area_ha"])
        for field in zone["fields"]:
            keys = {"name", "label", "type", "required", "unique"} | ({"enumOptions"} if field["type"] == "ENUM" else set())
            self.assertEqual(set(field), keys, field)


class WorkflowsPassTests(ApplyTestCase):
    def test_f1_has_no_definitions_and_calls_nothing(self):
        code, out, err = self.run_apply(MODEL_PATH, "--workflows")
        self.assertEqual(code, 0, err)
        self.assertIn("done: 0 workflows, 0 document types, 0 automations, 0 views, 0 forms, 0 pages", out)
        self.assertEqual(self.core.requests, [])

    def test_a_definition_before_its_phase_stops_with_2(self):
        with tempfile.TemporaryDirectory() as folder:
            path = write_model(folder, load(MODEL_PATH))
            os.mkdir(os.path.join(folder, "workflows"))
            with open(os.path.join(folder, "workflows", "project.json"), "w", encoding="utf-8") as f:
                f.write("{}")
            code, out, err = self.run_apply(path, "--workflows")
        self.assertEqual(code, 2)
        self.assertIn("model/workflows/project.json", err)
        self.assertEqual(self.core.requests, [])


class NewTenantTests(unittest.TestCase):
    """security.md §4.1: un tenant nuevo se prepara con apply.py, apply_roles.py y apply.py --workflows, en ese orden."""

    def test_a_new_tenant_is_provisioned_in_order_and_again_is_a_no_op(self):
        core = FakeCore()
        self.addCleanup(core.stop)
        login = ["--core", core.base_url, "--email", "admin-tenant@sgspe.test", "--password", "clave-del-tenant"]
        runs = [
            quiet(apply.main, ["--model", MODEL_PATH, *login]),
            quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login]),
            quiet(apply.main, ["--model", MODEL_PATH, "--workflows", *login]),
        ]
        self.assertEqual([code for code, _, _ in runs], [0, 0, 0], runs)
        self.assertIn("done: 15 created, 0 updated, 0 skipped", runs[1][1])
        self.assertEqual(sorted(core.roles), sorted(r["name"] for r in load(ROLES_PATH)["roles"]))
        # el modelo va antes que los roles: wasichai rechaza un rol que nombra un objeto que no existe
        paths = [p for _, p, _, _ in core.requests]
        self.assertLess(paths.index("/api/objects"), paths.index("/api/roles"))

        again = quiet(apply_roles.main, ["--roles", ROLES_PATH, "--model", MODEL_PATH, *login])
        self.assertEqual(again[0], 0, again)
        self.assertIn("done: 0 created, 0 updated, 15 skipped", again[1])


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Paso 7: correr las pruebas y verlas fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe/model
python3 -m unittest -v 2>&1 | tail -5
```

Esperado: `FAILED (errors=2)`: los dos módulos de prueba fallan al importar con
`ModuleNotFoundError: No module named 'apply_roles'` (todavía no existe `apply_roles.py`).

- [ ] **Paso 8: editar `model/apply.py`** (copia de caja; siete ediciones exactas)

1. Reemplazar el docstring del módulo completo (desde `"""Apply model/model.json to a wasichai Core (caja-backend).`
   hasta el `"""` que cierra después de `examples/gis-sample/perene/apply.py. Stdlib only. See README.md.`) por:

```python
"""Apply model/model.json to a wasichai Core (SGSPE).

Copied from caja-backend@dc9e391 (model/apply.py), itself adapted from wasichai's examples/gis-sample/perene/apply.py.
Stdlib only.

Reads a JSON metadata model (objects, fields, relationships, enums), checks it against Core's rules (--validate-only
stops there) and creates it in Core via the REST API, in file order (so relationship targets already exist), marking
each required relationship's field required. A third phase, after the relationships, puts what needs them: each
object's uniqueConstraints and the `unique: true` of a relationship's field. An object may declare actions of its own
(POST .../actions, a 409 is "already there"): roles are granted them next to READ, CREATE, UPDATE and DELETE. In F1
model.json has no objects: "done: 0 created, 0 updated, 0 skipped".

On a Core that already has the model it syncs instead: it adds the fields and the ENUM options model.json has and Core
lacks, declares the actions of each object, drops the ENUM options it no longer lists and no record uses, relaxes a
field model.json no longer requires, relabels one labelled differently and puts an object's write rules (apiOnly,
appendOnly, requiresReason) and its uniqueConstraints when they differ. It never renames, retypes, makes required or
removes a field or an option in use. --drop tears everything down in reverse order (data included; development only).

A field may carry "derived": true. Only SGSPE's tools read it (apply_roles.py leaves a derived field writable when it
expands `u`); this script never sends it, because it publishes a fixed set of keys. "editable" is refused: wasichai
skips non-editable fields on every write, the platform's too (security.md §6.3).

--workflows is the second pass of a tenant's provisioning, after apply_roles.py, because wasichai refuses a workflow
naming a role the tenant lacks: workflows, document types, automations, views, forms and pages, each from its phase.
F1 has none, so it writes nothing.
"""
```

2. Reemplazar la línea
   `# core's scalar types, plus GEOMETRY from wasichai-gis (caja does not install it; the check is kept as copied)` por
   `# core's scalar types, plus GEOMETRY from wasichai-gis (SGSPE installs it in F2)`.

3. En `validate`, reemplazar:

```python
    objects = model.get("objects")
    if not isinstance(objects, list) or not objects:
        errors.append("objects must be a non-empty list")
        objects = []
```

   por:

```python
    # F1 has no objects yet: an empty list is a valid model
    objects = model.get("objects")
    if not isinstance(objects, list):
        errors.append("objects must be a list")
        objects = []
```

4. En `validate`, reemplazar:

```python
            if fname in RESERVED_FIELD_NAMES:
                errors.append(f"{flabel}: field name is reserved by Core")
```

   por:

```python
            if fname in RESERVED_FIELD_NAMES:
                errors.append(f"{flabel}: field name is reserved by Core")
            # a derived field says so with "derived": true, which only SGSPE's tools read; never editable: false, which
            # wasichai applies to every write, the platform's too (security.md §6.3)
            if "derived" in field and not isinstance(field["derived"], bool):
                errors.append(f"{flabel}: derived must be true or false")
            if "editable" in field:
                errors.append(f"{flabel}: editable is not used; a derived field is marked derived: true")
```

5. Inmediatamente antes de `def default_model_path():`, insertar:

```python
# the second pass, after apply_roles.py (wasichai refuses a workflow naming a role the tenant lacks), each kind from
# its phase (architecture.md §5.4). F1 has none of them: the pass runs in the provisioning order and writes nothing
DEFINITION_DIRS = ("workflows", "document-types", "automations", "views", "forms", "pages")


def definition_files(model_dir):
    """The definition files under model/, by kind and then by name."""
    found = []
    for kind in DEFINITION_DIRS:
        folder = os.path.join(model_dir, kind)
        if os.path.isdir(folder):
            found.extend(f"{kind}/{name}" for name in sorted(os.listdir(folder)) if name.endswith(".json"))
    return found


def do_workflows(model_dir):
    """F1: nothing to apply. A definition that arrives before its phase's code is an error, never a silent skip."""
    files = definition_files(model_dir)
    if files:
        for path in files:
            print(f"error: model/{path}: apply.py --workflows does not apply definitions yet; they arrive with their phase",
                  file=sys.stderr)
        return 2
    print("done: 0 workflows, 0 document types, 0 automations, 0 views, 0 forms, 0 pages")
    return 0


```

6. En `_parse_args`, reemplazar:

```python
    p = argparse.ArgumentParser(description="Apply caja's model.json to a wasichai Core.")
    p.add_argument("--model", default=default_model_path())
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8091"))
```

   por:

```python
    p = argparse.ArgumentParser(description="Apply SGSPE's model.json to a wasichai Core.")
    p.add_argument("--model", default=default_model_path())
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
```

   y reemplazar `    p.add_argument("--validate-only", action="store_true")` por:

```python
    p.add_argument("--validate-only", action="store_true")
    p.add_argument("--workflows", action="store_true", help="the second pass, after apply_roles.py")
```

7. En `main`, reemplazar:

```python
    if args.validate_only:
        return 0

    if args.dry_run:
```

   por:

```python
    if args.validate_only:
        return 0
    if args.workflows:
        return do_workflows(os.path.dirname(os.path.abspath(args.model)))

    if args.dry_run:
```

- [ ] **Paso 9: escribir `model/apply_roles.py`** (reescrito a partir de caja-backend@dc9e391 `model/apply_roles.py`:
  conserva su estructura, `_load`, `_fatal` y la sincronización; agrega `mappedToAdmin`, `"*"`, `u`, permisos de campo y
  `ownRecordsOnly`)

```python
#!/usr/bin/env python3
"""Crea o sincroniza en un wasichai Core los roles de SGSPE que declara model/roles.json.

Copiado de caja-backend@dc9e391 (model/apply_roles.py) y extendido con las reglas de security.md §6.3:

- 15 roles declarados. SUPER_ADMIN y ORGANIZATION_ADMIN no se declaran: son el ADMIN de wasichai y van en la lista
  mappedToAdmin, que con los 15 suma los 17 roles del prompt. Declarar ADMIN, SUPER_ADMIN u ORGANIZATION_ADMIN es un error.
- Cada rol dice, por objeto del modelo, sus acciones: READ, CREATE, UPDATE, DELETE, una acción que el modelo declara para
  ese objeto, o `u`. La clave "*" es el permiso de todo el tenant (objectName null en wasichai) y solo admite READ.
- `u` no es un permiso de wasichai: se expande en UPDATE más una entrada de campo de solo lectura (read sí, write no) para
  cada campo del objeto sin "derived": true y para el campo de cada relación que sale del objeto. Un permiso de campo es
  una restricción, así que un campo nuevo nunca queda escribible por defecto: se regenera en cada fase.
- Ningún rol lleva ownRecordsOnly (ASM-069): roles.json lo rechaza, y un rol de core que lo tenga se corrige.
- Ningún rol recibe MANAGE_METADATA ni MANAGE_ORGANIZATION.

Un rol que falta se crea (POST /api/roles) y recibe sus permisos (PUT /api/roles/{name}/permissions) y, si usa `u`, sus
permisos de campo (PUT /api/roles/{name}/field-permissions). Uno que ya existe queda con lo que dice roles.json, ni uno
más ni uno menos: lo que se edite a mano en /admin se pierde en la corrida siguiente. Es idempotente: la segunda corrida
no escribe nada ("done: 0 created, 0 updated, 15 skipped").

Correr: python3 apply_roles.py [--dry-run] [--roles R] [--model M] [--core URL] [--email E] [--password P]
Salida: 0 si va bien, 1 si core rechaza algo o no responde, 2 si roles.json no es válido, o si roles.json o model.json
faltan o no son JSON.
"""
import argparse
import json
import os
import re
import sys

from core_client import Client, CoreError

HERE = os.path.dirname(os.path.abspath(__file__))
# las de core (wasichai.core.common.Actions) que se dan sobre un objeto
ACTIONS = ("READ", "CREATE", "UPDATE", "DELETE")
# AdminService.createRole
ROLE_NAME = re.compile(r"^[A-Z][A-Z0-9_]{1,48}$")
# el ADMIN de wasichai y los dos roles del prompt que son ese ADMIN (security.md §6.2): nunca se declaran
ADMIN_ROLES = ("ADMIN", "SUPER_ADMIN", "ORGANIZATION_ADMIN")
MAPPED_TO_ADMIN = ["SUPER_ADMIN", "ORGANIZATION_ADMIN"]
# el permiso de todo el tenant: wasichai lo guarda sin objeto (objectName null)
TENANT = "*"
# UPDATE sin campos de negocio escribibles (security.md §7.1)
LINK_ONLY = "u"


def _model_fields(model):
    """Por objeto, sus campos sin "derived": true en el orden de model.json, y después el campo de cada relación que
    sale de él."""
    fields = {}
    for obj in model.get("objects", []):
        fields[obj["name"]] = [f["name"] for f in obj.get("fields", []) if not f.get("derived", False)]
    for rel in model.get("relationships", []):
        fields.setdefault(rel["source"], []).append(rel["fieldName"])
    return fields


def validate(roles, model):
    """Los errores de roles.json frente a model.json; vacía si es válido."""
    errors = []
    # las acciones que cada objeto acepta: las de core y las que el modelo declara para él
    objects = {o["name"]: ACTIONS + tuple(a["name"] for a in o.get("actions", [])) for o in model.get("objects", [])}
    mapped = roles.get("mappedToAdmin")
    if mapped != MAPPED_TO_ADMIN:
        errors.append(f"mappedToAdmin must be {MAPPED_TO_ADMIN}, not {mapped}")
    seen = set()
    for role in roles.get("roles", []):
        name = role.get("name", "")
        if not isinstance(name, str) or not ROLE_NAME.fullmatch(name):
            errors.append(f"role '{name}': name must match {ROLE_NAME.pattern}")
        if name in ADMIN_ROLES:
            errors.append(f"role {name}: is wasichai's ADMIN, never declared (mappedToAdmin)")
        if name in seen:
            errors.append(f"role {name}: duplicate role name")
        seen.add(name)
        if not role.get("label"):
            errors.append(f"role {name}: label is missing")
        if "ownRecordsOnly" in role:
            errors.append(f"role {name}: ownRecordsOnly is not used (ASM-069): EDIT_ANY and EditAnyGuard decide")
        for obj, actions in role.get("permisos", {}).items():
            if obj == TENANT:
                if actions != ["READ"]:
                    errors.append(f"role {name}: '{TENANT}' only takes [\"READ\"], not {actions}")
                continue
            if obj not in objects:
                errors.append(f"role {name}: object '{obj}' is not in model.json")
                continue
            if LINK_ONLY in actions and "UPDATE" in actions:
                errors.append(f"role {name}: '{LINK_ONLY}' and UPDATE on {obj}: '{LINK_ONLY}' is UPDATE without editable fields")
            for action in actions:
                if action != LINK_ONLY and action not in objects[obj]:
                    errors.append(f"role {name}: action '{action}' on {obj} must be one of "
                                  f"{', '.join(objects[obj] + (LINK_ONLY,))}")
    return errors


def permissions_payload(role):
    """El cuerpo de PUT /api/roles/{name}/permissions, en el orden de roles.json: "*" va sin objeto y `u` es UPDATE."""
    return {"permissions": [{"objectName": None if obj == TENANT else obj,
                             "action": "UPDATE" if action == LINK_ONLY else action}
                            for obj, actions in role["permisos"].items() for action in actions]}


def field_permissions_payload(role, model):
    """El cuerpo de PUT /api/roles/{name}/field-permissions: solo lectura para cada campo no derivado de cada objeto en el
    que el rol tiene `u`."""
    fields = _model_fields(model)
    return {"fields": [{"objectName": obj, "fieldName": field, "read": True, "write": False}
                       for obj, actions in role["permisos"].items() if LINK_ONLY in actions
                       for field in fields.get(obj, [])]}


def _granted(permissions):
    """Lo que un rol de core concede, como pares (objeto, acción)."""
    return {(p.get("objectName"), p["action"]) for p in permissions or [] if p.get("allowed", True)}


def _wanted(role):
    return {(p["objectName"], p["action"]) for p in permissions_payload(role)["permissions"]}


def _fields(entries):
    """Permisos de campo como tuplas (objeto, campo, lectura, escritura), para compararlos sin importar el orden."""
    return {(f["objectName"], f["fieldName"], f.get("read", True), f.get("write", True)) for f in entries or []}


def _fatal(method, path, err):
    print(f"error {method} {path} -> {err.status}\n{err.body}", file=sys.stderr)


def do_apply(client, roles, model):
    created = updated = skipped = 0
    try:
        existing = {r["name"]: r for r in client.get("/api/roles") or []}
    except CoreError as e:
        _fatal("GET", "/api/roles", e)
        return 1
    for role in roles["roles"]:
        name = role["name"]
        current = existing.get(name)
        fields = field_permissions_payload(role, model)
        writes = []
        if current is None:
            writes.append(("POST", "/api/roles", {"name": name, "label": role["label"]}, f"create role {name}"))
            writes.append(("PUT", f"/api/roles/{name}/permissions", permissions_payload(role), None))
            if fields["fields"]:
                writes.append(("PUT", f"/api/roles/{name}/field-permissions", fields, None))
        else:
            # la etiqueta de roles.json y nunca ownRecordsOnly, aunque alguien lo haya activado en /admin
            if current.get("label") != role["label"] or current.get("ownRecordsOnly", False):
                writes.append(("PUT", f"/api/roles/{name}", {"label": role["label"], "ownRecordsOnly": False},
                               f"update role {name} (label, ownRecordsOnly)"))
            if _granted(current.get("permissions")) != _wanted(role):
                writes.append(("PUT", f"/api/roles/{name}/permissions", permissions_payload(role),
                               f"update role {name} (permissions)"))
            if _fields(current.get("fieldPermissions")) != _fields(fields["fields"]):
                writes.append(("PUT", f"/api/roles/{name}/field-permissions", fields,
                               f"update role {name} (field permissions)"))
        for method, path, body, message in writes:
            try:
                (client.post if method == "POST" else client.put)(path, body)
            except CoreError as e:
                _fatal(method, path, e)
                return 1
            if message:
                print(message)
        if current is None:
            created += 1
        elif writes:
            updated += 1
        else:
            print(f"skip   role {name} (up to date)")
            skipped += 1
    print(f"done: {created} created, {updated} updated, {skipped} skipped")
    return 0


def _print_dry_run(roles, model):
    for role in roles["roles"]:
        print("# POST /api/roles")
        print(json.dumps({"name": role["name"], "label": role["label"]}, indent=2, ensure_ascii=False))
        print(f"# PUT /api/roles/{role['name']}/permissions")
        print(json.dumps(permissions_payload(role), indent=2, ensure_ascii=False))
        fields = field_permissions_payload(role, model)
        if fields["fields"]:
            print(f"# PUT /api/roles/{role['name']}/field-permissions")
            print(json.dumps(fields, indent=2, ensure_ascii=False))


def _parse_args(argv):
    p = argparse.ArgumentParser(description="Crea o sincroniza los roles de SGSPE (roles.json) en wasichai Core.")
    p.add_argument("--roles", default=os.path.join(HERE, "roles.json"))
    p.add_argument("--model", default=os.path.join(HERE, "model.json"))
    p.add_argument("--core", default=os.environ.get("WASICHAI_CORE", "http://localhost:8094"))
    p.add_argument("--email", default=os.environ.get("WASICHAI_EMAIL", "admin@wasichai.local"))
    p.add_argument("--password", default=os.environ.get("WASICHAI_PASSWORD", "admin"))
    p.add_argument("--dry-run", action="store_true", help="imprime lo que mandaría a un core vacío; no llama a core")
    return p.parse_args(argv)


def _load(path):
    """El json de `path`, o None tras decir en stderr por qué no se pudo leer (sin traza)."""
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except OSError as e:
        print(f"error: {path}: {e.strerror or e}", file=sys.stderr)
    except ValueError as e:
        # json.JSONDecodeError y UnicodeDecodeError son ValueError
        print(f"error: {path}: no es JSON válido: {e}", file=sys.stderr)
    return None


def main(argv=None):
    args = _parse_args(sys.argv[1:] if argv is None else argv)
    roles = _load(args.roles)
    model = _load(args.model)
    if roles is None or model is None:
        return 2
    errors = validate(roles, model)
    if errors:
        for e in errors:
            print(f"roles: {args.roles}: {e}")
        return 2
    if args.dry_run:
        _print_dry_run(roles, model)
        return 0
    client = Client(args.core)
    try:
        client.login(args.email, args.password)
    except CoreError as e:
        _fatal("POST", "/api/auth/login", e)
        return 1
    return do_apply(client, roles, model)


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Paso 10: editar `model/fake_core.py`** (copia de caja; tres ediciones exactas)

1. Reemplazar la segunda línea del docstring, `Adapted from wasichai's examples/gis-sample/perene/test_apply.py. Test helper only.`,
   por `Copied from caja-backend@dc9e391 (model/fake_core.py); roles carry ownRecordsOnly and fieldPermissions. Test helper only.`

2. Después de la línea `ROLE_PERMISSIONS = re.compile(r"^/api/roles/([A-Z0-9_]+)/permissions$")`, agregar:

```python
ROLE_FIELD_PERMISSIONS = re.compile(r"^/api/roles/([A-Z0-9_]+)/field-permissions$")
```

3. Reemplazar el método `_roles` completo (desde `    def _roles(self, method, path, body):` hasta su `return 200, role`)
   por:

```python
    def _roles(self, method, path, body):
        """Core's role routes, addressed by name: list, create, relabel (and ownRecordsOnly), and replace the
        permissions or the field permissions."""
        if path == "/api/roles" and method == "GET":
            return 200, list(self.roles.values())
        if path == "/api/roles" and method == "POST":
            name = body["name"]
            if name == self.fail_on_role:
                return 500, {"message": "boom-role"}
            if name in self.roles:
                return 409, {"detail": f"Role '{name}' already exists"}
            self.roles[name] = {"name": name, "label": body["label"], "ownRecordsOnly": body.get("ownRecordsOnly", False),
                                "permissions": [], "fieldPermissions": []}
            return 201, self.roles[name]
        match = ROLE_FIELD_PERMISSIONS.match(path) or ROLE_PERMISSIONS.match(path) or ROLE.match(path)
        role = self.roles.get(match.group(1)) if match and method == "PUT" else None
        if role is None:
            return 404, {"detail": "not found"}
        if ROLE_FIELD_PERMISSIONS.match(path):
            role["fieldPermissions"] = [{**f, "read": f.get("read", True), "write": f.get("write", True)} for f in body["fields"]]
        elif ROLE_PERMISSIONS.match(path):
            role["permissions"] = [{**p, "allowed": p.get("allowed", True)} for p in body["permissions"]]
        else:
            role["label"] = body.get("label", role["label"])
            role["ownRecordsOnly"] = body.get("ownRecordsOnly", role.get("ownRecordsOnly", False))
        return 200, role
```

   El resto de `fake_core.py` queda como en caja.

- [ ] **Paso 11: correr las pruebas y verlas pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe/model
python3 -m unittest -v 2>&1 | tail -4
python3 apply.py --validate-only; echo "salida: $?"
python3 apply_roles.py --dry-run | grep -c '# POST /api/roles'
```

Esperado: `Ran 41 tests` y `OK`; `salida: 0`; `15`.

- [ ] **Paso 12: comprobar los scripts contra la app real**

Con `bootRun` en segundo plano como en la Tarea 4, Paso 4:

```bash
cd /Users/jorge/IdeaProjects/sgspe/model
python3 apply.py | tail -1
python3 apply_roles.py | tail -1
python3 apply_roles.py | tail -1
python3 apply.py --workflows
```

Esperado, en orden: `done: 0 created, 0 updated, 0 skipped`; `done: 15 created, 0 updated, 0 skipped` (o, si la base de
desarrollo ya tenía los roles, `done: 0 created, 0 updated, 15 skipped`); `done: 0 created, 0 updated, 15 skipped`;
`done: 0 workflows, 0 document types, 0 automations, 0 views, 0 forms, 0 pages`. Detener `bootRun` después. Si wasichai
responde con un error a `"objectName": null` o a `"ownRecordsOnly": false`, parar y reportarlo: es una deriva de la
plataforma que cambia esta tarea.

- [ ] **Paso 13: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add model/model.json model/roles.json model/apply.py model/apply_roles.py model/core_client.py model/fake_core.py \
  model/fixture_model.py model/test_apply.py model/test_apply_roles.py
git commit -F - <<'EOF'
feat(model): modelo vacío de F1, los 17 roles y el aprovisionamiento en orden

roles.json declara 15 roles con etiqueta en español y mapea SUPER_ADMIN y
ORGANIZATION_ADMIN a ADMIN; solo AUDITOR lee todo el tenant. apply_roles.py
entiende "*", u y derived, y corrige ownRecordsOnly. apply.py --workflows es
el segundo pase, sin definiciones en F1.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 7: A5 · Pruebas de contrato con la plataforma

**Pista:** A.

**Files:**
- Create: `src/test/kotlin/sgspe/SgspeApiTest.kt`
- Test: `src/test/kotlin/sgspe/contract/TenantIsolationApiTest.kt`, `TenantProvisioningApiTest.kt`, `SecurityApiTest.kt`,
  `RolesApiTest.kt`, `AuditApiTest.kt`, `ErrorContractApiTest.kt` (todas en `src/test/kotlin/sgspe/contract/`)

**Interfaces:**
- Consumes: `sgspe.SgspeApplication` (Tarea 3); `model/apply.py`, `model/apply_roles.py`, `model/roles.json` y sus salidas
  `done: …` (Tarea 6); `WasichaiIntegrationTest` de `wasichai-test` (`client`, `bearer()`, `bearer(email, password)`,
  `uniqueName(prefix)`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `TEST_JWT_SECRET`).
- Produces, para las Tareas 8 y 9 (`abstract class sgspe.SgspeApiTest : WasichaiIntegrationTest`):
  - `token: String` (el Bearer del admin sembrado, ya aprovisionado con `model/`);
  - `exchange(method, path, body = null, token: String? = this.token, headers = emptyMap()): Response`, con
    `data class Response(val status: HttpStatus, val body: String, val headers: HttpHeaders)`;
  - `send(method, path, body, status, token: String? = this.token, headers = emptyMap()): String`;
  - `tree(body: String): JsonNode`; `field(name, type, unique = false)`;
    `createObject(name, fields, flags = emptyMap(), token = this.token)`;
    `createRecord(objectName, attributes, headers = emptyMap(), token = this.token): String`;
  - `me(token = this.token): JsonNode`; `userWithRole(role): Account`; `provisionTenant(): Account`, con
    `data class Account(val token: String, val email: String, val id: String = "")`;
  - `runScript(args: List<String>, account: Account? = null): ScriptRun`, con
    `data class ScriptRun(val exit: Int, val output: String)`;
  - en el `companion object`: `PASSWORD`, `REASON` (`X-Change-Reason`), `JSON: JsonMapper` y `reason(text): String`.

Ninguna prueba de esta tarea necesita código de producción nuevo: fijan lo que SGSPE supone de wasichai. El paso «que
falla» es la compilación sin la clase base; si una prueba falla contra la plataforma real, eso es una deriva de wasichai
y se reporta, no se ajusta la prueba para que pase.

- [ ] **Paso 1: escribir las pruebas de contrato**

`src/test/kotlin/sgspe/contract/TenantIsolationApiTest.kt`:

```kotlin
package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest

// RQ-SEC-002 y RQ-SEC-017: el tenant es el del token; el administrador de otra organización no ve nada de la primera
class TenantIsolationApiTest : SgspeApiTest() {
    @Test
    fun `el administrador de otra organizacion no ve los objetos, los registros ni la auditoria de la primera`() {
        val name = uniqueName("aislado")
        createObject(name, listOf(field("code", "TEXT")))
        val id = createRecord(name, mapOf("code" to "A-1"))
        val other = provisionTenant()

        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/$name/records/$id", token = other.token).status)
        assertEquals(HttpStatus.NOT_FOUND, exchange("GET", "/api/objects/$name", token = other.token).status)
        val audit = tree(send("GET", "/api/audit?limit=500", null, HttpStatus.OK, other.token))
        assertTrue(audit.values().none { it["recordId"]?.asString() == id }, audit.toString())
        assertNotEquals(me()["organizationId"].asString(), me(other.token)["organizationId"].asString())
    }

    @Test
    fun `cada organizacion tiene su propia copia del modelo y de los roles`() {
        val other = provisionTenant()
        // sin apply_roles.py para ella, la organización nueva solo tiene el ADMIN que crea wasichai
        val roles = tree(send("GET", "/api/roles", null, HttpStatus.OK, other.token)).values().map { it["name"].asString() }
        assertEquals(listOf("ADMIN"), roles)
        assertEquals(0, tree(send("GET", "/api/objects", null, HttpStatus.OK, other.token)).size())
    }
}
```

`src/test/kotlin/sgspe/contract/TenantProvisioningApiTest.kt`:

```kotlin
package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import java.io.File

// security.md §4.1 y §14 «Alta de un tenant»: los scripts reales sobre un tenant nuevo, en orden, sin errores e idempotentes
class TenantProvisioningApiTest : SgspeApiTest() {
    @Test
    fun `un tenant nuevo se prepara con apply, apply_roles y apply workflows en ese orden`() {
        val admin = provisionTenant()

        val model = runScript(listOf("model/apply.py"), admin)
        assertEquals(0, model.exit, model.output)
        assertTrue(model.output.contains("done: 0 created, 0 updated, 0 skipped"), model.output)
        val roles = runScript(listOf("model/apply_roles.py"), admin)
        assertEquals(0, roles.exit, roles.output)
        assertTrue(roles.output.contains("done: 15 created, 0 updated, 0 skipped"), roles.output)
        val workflows = runScript(listOf("model/apply.py", "--workflows"), admin)
        assertEquals(0, workflows.exit, workflows.output)
        val again = runScript(listOf("model/apply_roles.py"), admin)
        assertEquals(0, again.exit, again.output)
        assertTrue(again.output.contains("done: 0 created, 0 updated, 15 skipped"), again.output)

        val declared =
            JSON
                .readTree(File("model/roles.json"))["roles"]
                .values()
                .map { it["name"].asString() }
                .toSet()
        val names = tree(send("GET", "/api/roles", null, HttpStatus.OK, admin.token)).values().map { it["name"].asString() }.toSet()
        assertEquals(declared + "ADMIN", names)
    }
}
```

`src/test/kotlin/sgspe/contract/SecurityApiTest.kt`:

```kotlin
package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import wasichai.test.WasichaiIntegrationTest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// RQ-SEC-001 y RQ-SEC-017: contraseña errada, usuario deshabilitado y token alterado o vencido responden 401
class SecurityApiTest : SgspeApiTest() {
    private fun login(
        email: String,
        password: String
    ) = exchange("POST", "/api/auth/login", mapOf("email" to email, "password" to password), token = null)

    @Test
    fun `una contrasena errada responde 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, login(WasichaiIntegrationTest.ADMIN_EMAIL, "errada-de-prueba").status)
    }

    @Test
    fun `un usuario deshabilitado no entra`() {
        val user = userWithRole("READ_ONLY")
        send("PUT", "/api/users/${user.id}", mapOf("enabled" to false), HttpStatus.OK)
        assertEquals(HttpStatus.UNAUTHORIZED, login(user.email, PASSWORD).status)
    }

    @Test
    fun `un token valido devuelve a quien llama`() {
        val yo = me()
        assertEquals(WasichaiIntegrationTest.ADMIN_EMAIL, yo["email"].asString())
        assertTrue(yo["organizationId"].asString().isNotBlank())
    }

    @Test
    fun `sin token, con un token alterado, con basura o con uno vencido la api responde 401`() {
        val tampered = token.dropLast(2) + if (token.endsWith("AA")) "BB" else "AA"
        for (bearer in listOf(null, tampered, "Bearer no-es-un-jwt", signed(expiresIn = -3600))) {
            assertEquals(HttpStatus.UNAUTHORIZED, exchange("GET", "/api/auth/me", token = bearer).status, "$bearer")
        }
    }

    @Test
    fun `el mismo token firmado igual pero vigente entra`() {
        // control de la prueba anterior: el vencido se rechaza por su fecha, no por su forma
        assertEquals(HttpStatus.OK, exchange("GET", "/api/auth/me", token = signed(expiresIn = 3600)).status)
    }

    @Test
    fun `las sondas de salud no piden token`() {
        for (path in listOf("/actuator/health/liveness", "/actuator/health/readiness")) {
            val response = exchange("GET", path, token = null)
            assertEquals(HttpStatus.OK, response.status, path)
            assertEquals("UP", tree(response.body)["status"].asString(), path)
        }
    }

    // un JWT HS256 con la forma del de wasichai (JwtService: sub, iss, iat, exp, org, email y roles), firmado con el secreto
    // fijo de WasichaiIntegrationTest. solo JDK: ninguna clase interna de wasichai
    private fun signed(expiresIn: Long): String {
        val yo = me()
        val now = Instant.now().epochSecond
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString(JSON.writeValueAsBytes(mapOf("alg" to "HS256", "typ" to "JWT")))
        val claims =
            encoder.encodeToString(
                JSON.writeValueAsBytes(
                    mapOf(
                        "sub" to yo["userId"].asString(),
                        "iss" to "wasichai",
                        "iat" to now - 7200,
                        "exp" to now + expiresIn,
                        "org" to yo["organizationId"].asString(),
                        "email" to yo["email"].asString(),
                        "roles" to yo["roles"].values().map { it.asString() }
                    )
                )
            )
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(WasichaiIntegrationTest.TEST_JWT_SECRET.toByteArray(), "HmacSHA256"))
        val signature = encoder.encodeToString(mac.doFinal("$header.$claims".toByteArray()))
        return "Bearer $header.$claims.$signature"
    }
}
```

`src/test/kotlin/sgspe/contract/RolesApiTest.kt`:

```kotlin
package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import java.io.File

// RQ-SEC-003 y RQ-SEC-007: los 15 roles declarados y ADMIN con sus etiquetas; AUDITOR lee la auditoría y el resto no; un
// rol declarado no administra usuarios ni roles
class RolesApiTest : SgspeApiTest() {
    private val declared: Map<String, String> =
        JSON.readTree(File("model/roles.json"))["roles"].values().associate { it["name"].asString() to it["label"].asString() }

    @Test
    fun `api roles muestra los 15 roles declarados y ADMIN, con su etiqueta y sin ownRecordsOnly`() {
        val roles = tree(send("GET", "/api/roles", null, HttpStatus.OK))
        assertEquals(declared.keys + "ADMIN", roles.values().map { it["name"].asString() }.toSet())
        for (role in roles) {
            val name = role["name"].asString()
            if (name != "ADMIN") assertEquals(declared.getValue(name), role["label"].asString(), name)
            assertFalse(role["ownRecordsOnly"].asBoolean(), name)
        }
        val auditor = roles.values().single { it["name"].asString() == "AUDITOR" }
        val grants = auditor["permissions"].values().map { (if (it["objectName"].isNull) null else it["objectName"].asString()) to it["action"].asString() }
        assertEquals(listOf(null to "READ"), grants)
    }

    @Test
    fun `AUDITOR lee la auditoria y los registros de todo el tenant y no escribe`() {
        val name = uniqueName("leido")
        createObject(name, listOf(field("code", "TEXT")))
        createRecord(name, mapOf("code" to "L-1"))
        val auditor = userWithRole("AUDITOR")

        assertEquals(HttpStatus.OK, exchange("GET", "/api/audit", token = auditor.token).status)
        assertEquals(HttpStatus.OK, exchange("GET", "/api/objects/$name/records", token = auditor.token).status)
        val write = exchange("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "L-2")), auditor.token)
        assertEquals(HttpStatus.FORBIDDEN, write.status)
        assertFalse(tree(send("GET", "/api/auth/me/permissions", null, HttpStatus.OK, auditor.token))["admin"].asBoolean())
    }

    @Test
    fun `un rol sin permisos recibe 403 en la auditoria`() {
        assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/audit", token = userWithRole("ANALYST").token).status)
    }

    @Test
    fun `un rol declarado recibe 403 en usuarios y roles`() {
        for (role in listOf("AUDITOR", "ANALYST")) {
            val account = userWithRole(role)
            assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/users", token = account.token).status, role)
            assertEquals(HttpStatus.FORBIDDEN, exchange("GET", "/api/roles", token = account.token).status, role)
            val create = exchange("POST", "/api/roles", mapOf("name" to "INTRUSO", "label" to "Intruso"), account.token)
            assertEquals(HttpStatus.FORBIDDEN, create.status, role)
        }
    }
}
```

`src/test/kotlin/sgspe/contract/AuditApiTest.kt`:

```kotlin
package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import wasichai.test.WasichaiIntegrationTest
import java.time.Instant

// RQ-AUD-001 y RQ-AUD-002 (security.md §13.1): quién, qué, cuándo, por qué, desde y hacia; motivo obligatorio con tildes
// de ida y vuelta; appendOnly responde 409 y el intento no se audita
class AuditApiTest : SgspeApiTest() {
    @Test
    fun `una escritura deja quien, que, cuando, por que, desde y hacia`() {
        val name = uniqueName("auditado")
        createObject(name, listOf(field("visits", "INTEGER")))
        val before = Instant.now().minusSeconds(60)
        val id = createRecord(name, mapOf("visits" to 10))
        send(
            "PUT",
            "/api/objects/$name/records/$id",
            mapOf("attributes" to mapOf("visits" to 20)),
            HttpStatus.OK,
            headers =
                mapOf(REASON to reason("corrección del conteo"))
        )

        val history = tree(send("GET", "/api/objects/$name/records/$id/history", null, HttpStatus.OK))
        assertEquals(listOf("UPDATE", "CREATE"), history.values().map { it["operation"].asString() })
        val update = history.values().first()
        assertEquals(WasichaiIntegrationTest.ADMIN_EMAIL, update["userEmail"].asString())
        assertEquals(name, update["objectName"].asString())
        assertEquals(id, update["recordId"].asString())
        assertTrue(Instant.parse(update["occurredAt"].asString()).isAfter(before))
        assertEquals("corrección del conteo", update["reason"].asString())
        val change = update["changes"].values().single { it["field"].asString() == "visits" }
        assertEquals(10, change["before"].asInt())
        assertEquals(20, change["after"].asInt())
        // quien lee todo el tenant ve lo mismo en /api/audit
        assertEquals(2, tree(send("GET", "/api/audit?objectName=$name&recordId=$id", null, HttpStatus.OK)).size())
    }

    @Test
    fun `un objeto requiresReason rechaza la escritura sin motivo y no guarda nada`() {
        val name = uniqueName("motivado")
        createObject(name, listOf(field("code", "TEXT")), mapOf("requiresReason" to true))
        val problem = tree(send("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "M-1")), HttpStatus.BAD_REQUEST))
        assertEquals("reason", problem["errors"][0]["field"].asString())
        assertEquals(0, tree(send("GET", "/api/objects/$name/records", null, HttpStatus.OK))["totalElements"].asInt())
        createRecord(name, mapOf("code" to "M-1"), mapOf(REASON to reason("alta con motivo")))
        assertEquals(1, tree(send("GET", "/api/objects/$name/records", null, HttpStatus.OK))["totalElements"].asInt())
    }

    @Test
    fun `un objeto appendOnly responde 409 al cambiarlo o borrarlo y el intento no se audita`() {
        val name = uniqueName("historia")
        createObject(name, listOf(field("code", "TEXT")), mapOf("appendOnly" to true))
        val id = createRecord(name, mapOf("code" to "H-1"))
        send("PUT", "/api/objects/$name/records/$id", mapOf("attributes" to mapOf("code" to "H-2")), HttpStatus.CONFLICT)
        send("DELETE", "/api/objects/$name/records/$id", null, HttpStatus.CONFLICT)
        val history = tree(send("GET", "/api/objects/$name/records/$id/history", null, HttpStatus.OK))
        assertEquals(listOf("CREATE"), history.values().map { it["operation"].asString() })
    }
}
```

`src/test/kotlin/sgspe/contract/ErrorContractApiTest.kt`:

```kotlin
package sgspe.contract

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import sgspe.SgspeApiTest
import tools.jackson.databind.JsonNode
import wasichai.test.WasichaiIntegrationTest

// RNF-INT-001 (api.md §3.3): Problem Details de wasichai para 400, 401, 403, 404 y 409
class ErrorContractApiTest : SgspeApiTest() {
    private fun problem(
        response: Response,
        status: HttpStatus
    ): JsonNode {
        assertEquals(status, response.status, response.body)
        assertTrue(MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(response.headers.contentType), "${response.headers.contentType}")
        val problem = tree(response.body)
        assertEquals(status.value(), problem["status"].asInt())
        assertEquals("https://wasichai.dev/problems/${status.value()}", problem["type"].asString())
        assertTrue(problem["title"].asString().isNotBlank())
        return problem
    }

    @Test
    fun `400 nombra el campo con un valor invalido`() {
        val name = uniqueName("errado")
        createObject(name, listOf(field("visits", "INTEGER")))
        val problem = problem(exchange("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("visits" to "muchas"))), HttpStatus.BAD_REQUEST)
        assertEquals("visits", problem["errors"][0]["field"].asString())
    }

    @Test
    fun `un atributo que el objeto no tiene se ignora sin error`() {
        // contrato de wasichai 0.3.2 fijado a propósito (decisión 10 del plan de F1): el atributo desconocido no se guarda
        // y no hay 400. un nombre mal escrito se pierde en silencio: las pantallas y comandos de sgspe mandan solo campos
        // del modelo. si wasichai empieza a rechazarlo, esta prueba falla y se actualiza api.md
        val name = uniqueName("ignorado")
        createObject(name, listOf(field("code", "TEXT")))
        val created = tree(send("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "I-1", "no_existe" to "x")), HttpStatus.CREATED))
        assertEquals("I-1", created["attributes"]["code"].asString())
        assertTrue(created["attributes"]["no_existe"] == null)
    }

    @Test
    fun `401 del login es Problem Details`() {
        val body = mapOf("email" to WasichaiIntegrationTest.ADMIN_EMAIL, "password" to "errada-de-prueba")
        problem(exchange("POST", "/api/auth/login", body, token = null), HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `401 sin token lo emite spring security con WWW-Authenticate y sin cuerpo`() {
        // contrato de wasichai 0.3.2 fijado a propósito: api.md §3.3 pide Problem Details también aquí. si wasichai empieza
        // a mandarlo, esta prueba falla y se actualiza api.md (decisión 7 del plan de F1)
        val response = exchange("GET", "/api/auth/me", token = null)
        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertTrue(response.headers.getFirst(HttpHeaders.WWW_AUTHENTICATE)!!.startsWith("Bearer"))
        assertEquals("", response.body)
    }

    @Test
    fun `403 cuando falta el permiso`() {
        val name = uniqueName("vedado")
        createObject(name, listOf(field("code", "TEXT")))
        problem(exchange("GET", "/api/objects/$name/records", token = userWithRole("ANALYST").token), HttpStatus.FORBIDDEN)
    }

    @Test
    fun `404 de un objeto que no existe`() {
        problem(exchange("GET", "/api/objects/${uniqueName("ausente")}/records"), HttpStatus.NOT_FOUND)
    }

    @Test
    fun `409 nombra el campo unico repetido`() {
        val name = uniqueName("unico")
        createObject(name, listOf(field("code", "TEXT", unique = true)))
        createRecord(name, mapOf("code" to "U-1"))
        val problem = problem(exchange("POST", "/api/objects/$name/records", mapOf("attributes" to mapOf("code" to "U-1"))), HttpStatus.CONFLICT)
        assertEquals("code", problem["errors"][0]["field"].asString())
    }
}
```

- [ ] **Paso 2: compilarlas y verlas fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Esperado: FAIL con `Unresolved reference 'SgspeApiTest'`.

- [ ] **Paso 3: escribir la clase base `src/test/kotlin/sgspe/SgspeApiTest.kt`**

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import wasichai.test.WasichaiIntegrationTest
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

// la base de las pruebas de contrato con la plataforma (security.md §14). antes de la primera prueba de la JVM aprovisiona
// la organización del admin sembrado con los scripts reales de model/, en el orden de security.md §4.1, contra esta misma
// app; después da el token del admin y las llamadas que usan las pruebas. la base la comparten todas las clases de la
// corrida: cada prueba nombra sus objetos con uniqueName. a diferencia de CajaApiTest (caja-backend@dc9e391), no porta los
// scripts a Kotlin ni toca el almacén de wasichai por debajo: ArchitectureTest lo vigila
abstract class SgspeApiTest : WasichaiIntegrationTest() {
    // el del admin sembrado; una llamada toma otro donde la prueba lo necesita
    protected lateinit var token: String

    @Autowired
    private lateinit var environment: Environment

    @BeforeEach
    fun adminAndProvisioning() {
        token = bearer()
        synchronized(LOCK) {
            if (!provisioned) {
                for (script in PROVISIONING) {
                    val run = runScript(script)
                    assertEquals(0, run.exit, "python3 ${script.joinToString(" ")}\n${run.output}")
                }
                provisioned = true
            }
        }
    }

    protected data class Response(
        val status: HttpStatus,
        val body: String,
        val headers: HttpHeaders
    )

    protected data class Account(
        val token: String,
        val email: String,
        val id: String = ""
    )

    protected data class ScriptRun(
        val exit: Int,
        val output: String
    )

    // estado, cuerpo y cabeceras, sin afirmar nada. token null: sin Authorization
    protected fun exchange(
        method: String,
        path: String,
        body: Any? = null,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val spec =
            when (method) {
                "GET" -> client.get().uri(path)
                "DELETE" -> client.delete().uri(path)
                "PUT" -> client.put().uri(path).bodyValue(body!!)
                "POST" -> if (body == null) client.post().uri(path) else client.post().uri(path).bodyValue(body)
                else -> error("método $method")
            }
        val result =
            spec
                .headers { h ->
                    if (token != null) h.set(HttpHeaders.AUTHORIZATION, token)
                    headers.forEach(h::set)
                }.exchange()
                .expectBody(String::class.java)
                .returnResult()
        return Response(HttpStatus.valueOf(result.status.value()), result.responseBody ?: "", result.responseHeaders)
    }

    // el cuerpo de una llamada que tiene que contestar `status`
    protected fun send(
        method: String,
        path: String,
        body: Any?,
        status: HttpStatus,
        token: String? = this.token,
        headers: Map<String, String> = emptyMap()
    ): String {
        val response = exchange(method, path, body, token, headers)
        assertEquals(status, response.status, "$method $path: ${response.body}")
        return response.body
    }

    protected fun tree(body: String): JsonNode = JSON.readTree(body)

    protected fun field(
        name: String,
        type: String,
        unique: Boolean = false
    ): Map<String, Any> = mapOf("name" to name, "label" to name, "type" to type, "unique" to unique)

    // un objeto de prueba creado por la api de metadata, como admin: F1 no tiene objetos propios
    protected fun createObject(
        name: String,
        fields: List<Map<String, Any>>,
        flags: Map<String, Boolean> = emptyMap(),
        token: String = this.token
    ) {
        send("POST", "/api/objects", mapOf("name" to name, "label" to name, "pluralLabel" to name, "fields" to fields) + flags, HttpStatus.CREATED, token)
    }

    // un registro por la api genérica: su id
    protected fun createRecord(
        objectName: String,
        attributes: Map<String, Any?>,
        headers: Map<String, String> = emptyMap(),
        token: String = this.token
    ): String = tree(send("POST", "/api/objects/$objectName/records", mapOf("attributes" to attributes), HttpStatus.CREATED, token, headers))["id"].asString()

    protected fun me(token: String = this.token): JsonNode = tree(send("GET", "/api/auth/me", null, HttpStatus.OK, token))

    // un usuario (no ADMIN) con un rol de roles.json, con un correo ficticio @sgspe.test
    protected fun userWithRole(role: String): Account {
        val email = "${uniqueName(role.lowercase().replace('_', '-'))}@sgspe.test"
        val created =
            tree(
                send(
                    "POST",
                    "/api/users",
                    mapOf("email" to email, "displayName" to "Usuario de prueba", "password" to PASSWORD, "roles" to listOf(role)),
                    HttpStatus.CREATED
                )
            )
        return Account(bearer(email, PASSWORD), email, created["id"].asString())
    }

    // un tenant nuevo con su primer administrador (security.md §4.1, paso 1): su token y su correo
    protected fun provisionTenant(): Account {
        val slug = uniqueName("tenant")
        val email = "admin-$slug@sgspe.test"
        send(
            "POST",
            "/api/organizations",
            mapOf(
                "name" to "Organización de prueba $slug",
                "slug" to slug,
                "adminEmail" to email,
                "adminPassword" to PASSWORD,
                "adminDisplayName" to "Administración de prueba"
            ),
            HttpStatus.CREATED
        )
        return Account(bearer(email, PASSWORD), email)
    }

    // corre un script de model/ contra esta app como `account` (el admin sembrado si es null): código de salida y salida
    protected fun runScript(
        args: List<String>,
        account: Account? = null
    ): ScriptRun {
        val email = account?.email ?: WasichaiIntegrationTest.ADMIN_EMAIL
        val password = if (account == null) WasichaiIntegrationTest.ADMIN_PASSWORD else PASSWORD
        val port = environment.getRequiredProperty("local.server.port")
        val command = listOf("python3") + args + listOf("--core", "http://127.0.0.1:$port", "--email", email, "--password", password)
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor(120, TimeUnit.SECONDS)) { "${args.first()} no terminó en 120 s" }
        return ScriptRun(process.exitValue(), output)
    }

    companion object {
        // wasichai pide al menos 8 caracteres
        const val PASSWORD = "clave-de-prueba-sgspe"
        const val REASON = "X-Change-Reason"
        val JSON: JsonMapper = JsonMapper.builder().build()

        private val LOCK = Any()

        @Volatile
        private var provisioned = false

        private val PROVISIONING = listOf(listOf("model/apply.py"), listOf("model/apply_roles.py"), listOf("model/apply.py", "--workflows"))

        // el motivo en la forma RFC 8187 que pide wasichai para un texto con tildes (api.md §3.7)
        fun reason(text: String): String = "UTF-8''" + URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
    }
}
```

- [ ] **Paso 4: formato, build y compilación de las pruebas**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build compileTestKotlin
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Paso 5: correr las pruebas de contrato contra la base externa**

```bash
cd /Users/jorge/IdeaProjects/sgspe
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) \
./gradlew integrationTest --tests 'sgspe.contract.*' --rerun
```

Esperado: `BUILD SUCCESSFUL`; 23 pruebas en verde (2 de aislamiento, 1 de aprovisionamiento, 6 de seguridad, 4 de roles, 3
de auditoría y 7 de errores). Una falla aquí es información sobre wasichai 0.3.2: se anota el detalle (método, ruta, estado
y cuerpo del mensaje de la aserción) y se reporta al coordinador antes de tocar la prueba.

- [ ] **Paso 6: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/SgspeApiTest.kt src/test/kotlin/sgspe/contract/TenantIsolationApiTest.kt \
  src/test/kotlin/sgspe/contract/TenantProvisioningApiTest.kt src/test/kotlin/sgspe/contract/SecurityApiTest.kt \
  src/test/kotlin/sgspe/contract/RolesApiTest.kt src/test/kotlin/sgspe/contract/AuditApiTest.kt \
  src/test/kotlin/sgspe/contract/ErrorContractApiTest.kt
git commit -F - <<'EOF'
test(contract): contrato con wasichai: tenants, seguridad, roles, auditoría y errores

SgspeApiTest aprovisiona con los scripts reales de model/ y no toca internos de wasichai.
Las pruebas fijan lo que SGSPE supone de la plataforma (security.md §14, filas de F1).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 8: A6 · `sgspe.observability` y perfil de producción

**Pista:** A.

**Files:**
- Create: `src/main/kotlin/sgspe/observability/CorrelationId.kt`, `src/main/kotlin/sgspe/observability/CorrelationIdFilter.kt`,
  `src/main/resources/application-prod.yml`
- Test: `src/test/kotlin/sgspe/observability/CorrelationIdFilterTest.kt` (unitaria),
  `src/test/kotlin/sgspe/observability/ObservabilityApiTest.kt` (integración), `src/test/kotlin/sgspe/ProdProfileTest.kt`
  (unitaria)

**Interfaces:**
- Consumes: `SgspeApiTest` (Tarea 7): `exchange`, `Response`, `uniqueName`.
- Produces: `object sgspe.observability.CorrelationId` con `HEADER = "X-Correlation-Id"`, `KEY = "correlationId"`,
  `accept(sent: String?): String` y `current(): Mono<String>`; `class CorrelationIdFilter : WebFilter` con
  `@Order(Ordered.HIGHEST_PRECEDENCE)`; el logger `sgspe.access` con una línea
  `"<MÉTODO> <ruta> <estado> <ms>ms correlationId=<id>"` por petición.

- [ ] **Paso 1: escribir las pruebas que fallan**

`src/test/kotlin/sgspe/observability/CorrelationIdFilterTest.kt`:

```kotlin
package sgspe.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import java.util.UUID

// el filtro sin servidor: acepta un identificador con formato, reemplaza cualquier otro por un UUID, lo devuelve en la
// respuesta y lo deja en el contexto de Reactor para el código de sgspe (ASM-034, ADR-0026)
class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    // la cabecera de la respuesta y lo que vio la cadena en su contexto
    private fun run(sent: String?): Pair<String?, String?> {
        val request = MockServerHttpRequest.get("/api/auth/me")
        if (sent != null) request.header(CorrelationId.HEADER, sent)
        val exchange = MockServerWebExchange.from(request.build())
        var seen: String? = null
        filter.filter(exchange, WebFilterChain { _ -> CorrelationId.current().doOnNext { id -> seen = id }.then() }).block()
        return exchange.response.headers.getFirst(CorrelationId.HEADER) to seen
    }

    @Test
    fun `devuelve el que manda el cliente si cumple el formato y lo pone en el contexto`() {
        assertEquals("prueba-123.abc_X" to "prueba-123.abc_X", run("prueba-123.abc_X"))
        assertEquals("x".repeat(64), run("x".repeat(64)).first)
    }

    @Test
    fun `genera un UUID si no viene`() {
        val (header, seen) = run(null)
        UUID.fromString(header)
        assertEquals(header, seen)
    }

    @Test
    fun `reemplaza un valor sin formato y nunca lo devuelve`() {
        // Review Focus 2: largo, espacios, marcas y saltos de línea que inyectarían otra cabecera o una línea de log
        for (hostile in listOf("x".repeat(65), "con espacio", "<script>", "a\r\nSet-Cookie: x=1", "", "ñandú")) {
            val (header, seen) = run(hostile)
            assertNotEquals(hostile, header, hostile)
            UUID.fromString(header)
            assertEquals(header, seen, hostile)
        }
    }

    @Test
    fun `accept es una funcion del valor`() {
        assertEquals("abc", CorrelationId.accept("abc"))
        assertNotEquals(CorrelationId.accept(null), CorrelationId.accept(null))
    }
}
```

`src/test/kotlin/sgspe/observability/ObservabilityApiTest.kt`:

```kotlin
package sgspe.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import java.util.UUID

// RQ-AUD-006 y RNF-OBS-001: el identificador en toda respuesta, también en un 401 y en un error de wasichai, y una línea
// de acceso por petición sin la cadena de consulta
@ExtendWith(OutputCaptureExtension::class)
class ObservabilityApiTest : SgspeApiTest() {
    private fun id(response: Response): String? = response.headers.getFirst(CorrelationId.HEADER)

    @Test
    fun `devuelve el identificador del cliente y genera uno si no cumple el formato`() {
        assertEquals("prueba-api.1", id(exchange("GET", "/api/auth/me", headers = mapOf(CorrelationId.HEADER to "prueba-api.1"))))
        for (hostile in listOf("x".repeat(65), "con espacio", "<script>")) {
            val returned = id(exchange("GET", "/api/auth/me", headers = mapOf(CorrelationId.HEADER to hostile)))
            assertNotEquals(hostile, returned)
            UUID.fromString(returned)
        }
    }

    @Test
    fun `un 401 tambien lleva el identificador`() {
        val response = exchange("GET", "/api/auth/me", token = null, headers = mapOf(CorrelationId.HEADER to "prueba-401"))
        assertEquals(HttpStatus.UNAUTHORIZED, response.status)
        assertEquals("prueba-401", id(response))
    }

    @Test
    fun `un error de wasichai tambien lo lleva`() {
        val response = exchange("GET", "/api/objects/${uniqueName("ausente")}/records")
        assertEquals(HttpStatus.NOT_FOUND, response.status)
        UUID.fromString(id(response))
    }

    @Test
    fun `las sondas de salud lo llevan`() {
        val response = exchange("GET", "/actuator/health/liveness", token = null)
        assertEquals(HttpStatus.OK, response.status)
        UUID.fromString(id(response))
    }

    @Test
    fun `una linea de acceso por peticion, sin la cadena de consulta`(output: CapturedOutput) {
        exchange("GET", "/api/auth/me?pista=secreto-de-prueba", token = null, headers = mapOf(CorrelationId.HEADER to "acceso-1"))
        // la línea se escribe cuando termina la respuesta: se espera hasta 5 s
        val deadline = System.nanoTime() + 5_000_000_000
        while (!output.out.contains("correlationId=acceso-1") && System.nanoTime() < deadline) Thread.sleep(50)
        val line = output.out.lines().single { it.contains("correlationId=acceso-1") }
        assertTrue(line.contains("GET /api/auth/me 401"), line)
        assertFalse(output.out.contains("secreto-de-prueba"))
    }
}
```

`src/test/kotlin/sgspe/ProdProfileTest.kt`:

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource
import java.time.Duration

// RQ-SEC-016, RQ-SEC-001 y ADR-0020: el perfil de producción no tiene semilla ni secreto por defecto, acorta el token y
// escribe los logs en ECS. lee el yaml tal cual, sin levantar la app
class ProdProfileTest {
    private val prod = YamlPropertySourceLoader().load("prod", ClassPathResource("application-prod.yml")).single()

    private fun value(name: String): String? = prod.getProperty(name)?.toString()

    @Test
    fun `apaga la semilla de desarrollo`() {
        assertEquals("false", value("wasichai.seed.dev"))
    }

    @Test
    fun `exige el secreto JWT sin valor por defecto`() {
        assertEquals("\${WASICHAI_JWT_SECRET}", value("wasichai.security.jwt.secret"))
    }

    @Test
    fun `el token dura menos que las 8 horas por defecto`() {
        val ttl = Duration.parse(value("wasichai.security.jwt.ttl"))
        assertTrue(ttl.isPositive && ttl < Duration.ofHours(8), "$ttl")
    }

    @Test
    fun `escribe los logs en ECS`() {
        assertEquals("ecs", value("logging.structured.format.console"))
    }

    @Test
    fun `no existe un perfil llamado test`() {
        // un perfil llamado test apaga beans de algunas librerías sin avisar (wasichai docs/modules/testing.md)
        for (name in listOf("application-test.yml", "application-test.yaml", "application-test.properties")) {
            assertFalse(ClassPathResource(name).exists(), name)
        }
    }
}
```

- [ ] **Paso 2: verlas fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ProdProfileTest'
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Esperado: el primero no llega a correr porque la compilación de las pruebas falla con `Unresolved reference
'CorrelationIdFilter'` y `'CorrelationId'`; el segundo, el mismo error.

- [ ] **Paso 3: `src/main/kotlin/sgspe/observability/CorrelationId.kt`**

```kotlin
package sgspe.observability

import reactor.core.publisher.Mono
import java.util.UUID

// el identificador de correlación de una petición (ASM-034, ADR-0026, api.md §3.8): el que manda el cliente si cumple el
// formato, si no uno nuevo. un valor inválido nunca se devuelve tal cual: podría inyectar texto en una cabecera o en un log
object CorrelationId {
    const val HEADER = "X-Correlation-Id"

    // la clave en el contexto de Reactor y en el MDC de los logs de sgspe
    const val KEY = "correlationId"

    private val FORMAT = Regex("^[A-Za-z0-9._-]{1,64}$")

    fun accept(sent: String?): String = if (sent != null && FORMAT.matches(sent)) sent else UUID.randomUUID().toString()

    // el de la petición en curso, para el código de sgspe que corre en su cadena reactiva
    fun current(): Mono<String> = Mono.deferContextual { context -> Mono.justOrEmpty(context.getOrEmpty<String>(KEY)) }
}
```

- [ ] **Paso 4: `src/main/kotlin/sgspe/observability/CorrelationIdFilter.kt`**

```kotlin
package sgspe.observability

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context

// antes de la cadena de seguridad de wasichai (WebFilterChainProxy, orden -100), así que toda respuesta lleva el
// identificador, también un 401. viaja en el contexto de Reactor y en la línea de acceso: una por petición, con método,
// ruta sin la cadena de consulta, estado, duración e identificador. nunca el cuerpo ni la consulta, que pueden llevar
// datos personales (security.md §11.6). no depende de ningún paquete de dominio (architecture.md §5.3)
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : WebFilter {
    override fun filter(
        exchange: ServerWebExchange,
        chain: WebFilterChain
    ): Mono<Void> {
        val id = CorrelationId.accept(exchange.request.headers.getFirst(CorrelationId.HEADER))
        val response = exchange.response
        response.headers.set(CorrelationId.HEADER, id)
        // un manejador de errores puede rehacer las cabeceras: se vuelve a poner justo antes de responder
        response.beforeCommit {
            response.headers.set(CorrelationId.HEADER, id)
            Mono.empty()
        }
        val start = System.nanoTime()
        return chain
            .filter(exchange)
            .contextWrite(Context.of(CorrelationId.KEY, id))
            .doFinally { access(exchange, id, start) }
    }

    private fun access(
        exchange: ServerWebExchange,
        id: String,
        start: Long
    ) {
        val millis = (System.nanoTime() - start) / 1_000_000
        MDC.putCloseable(CorrelationId.KEY, id).use {
            ACCESS.info(
                "{} {} {} {}ms correlationId={}",
                exchange.request.method.name(),
                exchange.request.path.value(),
                exchange.response.statusCode?.value(),
                millis,
                id
            )
        }
    }

    companion object {
        private val ACCESS = LoggerFactory.getLogger("sgspe.access")
    }
}
```

- [ ] **Paso 5: `src/main/resources/application-prod.yml`**

```yaml
# perfil de producción (architecture.md §10.2, ADR-0020 y ADR-0026). ProdProfileTest lo fija
wasichai:
  seed:
    # nunca el admin@wasichai.local de desarrollo
    dev: false
  security:
    jwt:
      # obligatorio y sin valor por defecto: sin WASICHAI_JWT_SECRET (>= 32 bytes) la app no arranca
      secret: ${WASICHAI_JWT_SECRET}
      # menos que las 8 horas por defecto (RQ-SEC-001); el despliegue puede acortarlo, nunca llevarlo a 8 h o más
      ttl: PT4H

logging:
  structured:
    format:
      # json ECS en la consola, sin datos personales (RNF-OBS-001, RQ-SEC-010)
      console: ecs
```

- [ ] **Paso 6: correr las pruebas unitarias y verlas pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build
```

Esperado: `BUILD SUCCESSFUL`; en el reporte, `CorrelationIdFilterTest` (4) y `ProdProfileTest` (5) en verde.

- [ ] **Paso 7: correr la prueba de integración y verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
yarn -s format
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) \
./gradlew integrationTest --tests 'sgspe.observability.ObservabilityApiTest' --rerun
```

Esperado: `BUILD SUCCESSFUL`, 5 pruebas en verde.

- [ ] **Paso 8: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/observability/CorrelationId.kt src/main/kotlin/sgspe/observability/CorrelationIdFilter.kt \
  src/main/resources/application-prod.yml src/test/kotlin/sgspe/observability/CorrelationIdFilterTest.kt \
  src/test/kotlin/sgspe/observability/ObservabilityApiTest.kt src/test/kotlin/sgspe/ProdProfileTest.kt
git commit -F - <<'EOF'
feat(observability): X-Correlation-Id antes de la seguridad y perfil de producción

El filtro acepta ^[A-Za-z0-9._-]{1,64}$ o genera un UUID, responde también en un 401
y escribe una línea de acceso sin la cadena de consulta. application-prod.yml: sin
semilla, secreto obligatorio, token de 4 horas y logs ECS.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 9: A6 · Base de `sgspe.common`

**Pista:** A.

**Files:**
- Create: `src/main/kotlin/sgspe/common/SgspeWrite.kt`, `src/main/kotlin/sgspe/common/Records.kt`,
  `src/main/kotlin/sgspe/common/Lima.kt`, `src/main/kotlin/sgspe/common/StrictQuery.kt`
- Test: `src/test/kotlin/sgspe/common/SgspeWriteTest.kt`, `LimaTest.kt`, `StrictQueryTest.kt` (unitarias) y
  `RecordsApiTest.kt` (integración), en `src/test/kotlin/sgspe/common/`

**Interfaces:**
- Consumes: `SgspeApiTest` (Tarea 7); `wasichai.core.data.RecordService`, `RecordRequest`, `RecordResponse`,
  `RecordWriteGuard`, `RecordWrite`; `wasichai.core.common.WasichaiException`, `FieldViolation`.
- Produces, para las guardas y endpoints de F2:
  - `object SgspeWrite : AbstractCoroutineContextElement(SgspeWrite.Key)`, que se lee con
    `currentCoroutineContext()[SgspeWrite.Key]`;
  - `@Component class Records(records: RecordService)` con `suspend fun get(objectName, id: UUID): RecordResponse`,
    `suspend fun create(objectName, attributes: Map<String, Any?>, reason: String?): RecordResponse` y
    `suspend fun update(objectName, id: UUID, attributes: Map<String, Any?>, reason: String?): RecordResponse`;
  - `val LIMA: ZoneId` y el bean `Clock` de `@Configuration class Clocks`;
  - `class UnprocessableContentException(violations: List<FieldViolation>) : WasichaiException` (422) y
    `object StrictQuery { fun requireOnly(params: Map<String, List<String>>, allowed: Set<String>) }`.

Las guardas transversales de `architecture.md` §5.2 (`DerivedFieldsGuard`, `TransitionReasonGuard`…) llegan con los
objetos de F2; esta tarea deja solo la base que nombra el roadmap.

- [ ] **Paso 1: escribir las pruebas que fallan**

`src/test/kotlin/sgspe/common/SgspeWriteTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

// la marca vive en la corrutina: dentro de withContext(SgspeWrite) se ve, fuera no
class SgspeWriteTest {
    @Test
    fun `la marca se ve dentro de su contexto y no fuera`() {
        runBlocking {
            assertNull(currentCoroutineContext()[SgspeWrite.Key])
            withContext(SgspeWrite) { assertSame(SgspeWrite, currentCoroutineContext()[SgspeWrite.Key]) }
            assertNull(currentCoroutineContext()[SgspeWrite.Key])
        }
    }
}
```

`src/test/kotlin/sgspe/common/LimaTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset

// las fechas de sgspe son las de Lima, UTC-5 todo el año, no las de la máquina
class LimaTest {
    @Test
    fun `la zona es la de Lima, sin horario de verano`() {
        assertEquals("America/Lima", LIMA.id)
        for (instant in listOf("2026-01-15T12:00:00Z", "2026-07-15T12:00:00Z")) {
            assertEquals(ZoneOffset.ofHours(-5), LIMA.rules.getOffset(Instant.parse(instant)), instant)
        }
    }

    @Test
    fun `el reloj de la app corre en Lima`() {
        assertEquals(LIMA, Clocks().clock().zone)
    }
}
```

`src/test/kotlin/sgspe/common/StrictQueryTest.kt`:

```kotlin
package sgspe.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

// ASM-054 y api.md §3.3: un parámetro de consulta desconocido es un 422 que lo nombra, nunca un filtro ignorado
class StrictQueryTest {
    private val allowed = setOf("project", "periodStart")

    @Test
    fun `deja pasar los parametros admitidos`() {
        StrictQuery.requireOnly(mapOf("project" to listOf("p1"), "periodStart" to listOf("2026-01-01")), allowed)
    }

    @Test
    fun `un parametro desconocido es un 422 que lo nombra y dice cuales se admiten`() {
        val error =
            assertThrows<UnprocessableContentException> { StrictQuery.requireOnly(mapOf("projectId" to listOf("p1"), "project" to listOf("p2")), allowed) }
        assertEquals(422, error.status.value())
        assertEquals("projectId", error.violations.single().field)
        assertEquals("no es un parámetro de esta consulta; se admiten periodStart, project", error.violations.single().message)
    }

    @Test
    fun `varios desconocidos salen todos, en orden`() {
        val error = assertThrows<UnprocessableContentException> { StrictQuery.requireOnly(mapOf("zeta" to listOf(), "alfa" to listOf()), allowed) }
        assertEquals(listOf("alfa", "zeta"), error.violations.map { it.field })
    }
}
```

`src/test/kotlin/sgspe/common/RecordsApiTest.kt`:

```kotlin
package sgspe.common

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import sgspe.SgspeApiTest
import wasichai.core.data.RecordService
import wasichai.core.data.RecordWrite
import wasichai.core.data.RecordWriteGuard
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

// Records escribe con la marca SgspeWrite y con su motivo; la API genérica de wasichai, sin la marca. una guarda de
// prueba (solo en el contexto de esta clase) anota lo que ve, como harán las guardas de F2
class RecordsApiTest : SgspeApiTest() {
    @TestConfiguration
    class Marks {
        @Bean
        fun marks(): RecordWriteGuard =
            object : RecordWriteGuard {
                override suspend fun beforeWrite(change: RecordWrite) {
                    SEEN.add(change.objectName to (currentCoroutineContext()[SgspeWrite.Key] != null))
                }
            }
    }

    @Autowired
    private lateinit var records: Records

    @Autowired
    private lateinit var recordService: RecordService

    @Test
    fun `una escritura de Records lleva la marca y su motivo, y una de la api generica no lleva la marca`() {
        val name = uniqueName("marcado")
        createObject(name, listOf(field("code", "TEXT")))
        val organization = UUID.fromString(me()["organizationId"].asString())

        // como un trabajo de fondo: la plataforma, sin petición (WAS-ADR-0039)
        val created = runBlocking { recordService.asPlatform(organization) { records.create(name, mapOf("code" to "M-1"), "alta de la prueba") } }
        createRecord(name, mapOf("code" to "G-1"))

        assertEquals(listOf(name to true, name to false), SEEN.filter { it.first == name })
        val history = tree(send("GET", "/api/objects/$name/records/${created.id}/history", null, HttpStatus.OK))
        assertEquals("alta de la prueba", history.values().single()["reason"].asString())
    }

    companion object {
        val SEEN = ConcurrentLinkedQueue<Pair<String, Boolean>>()
    }
}
```

- [ ] **Paso 2: verlas fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Esperado: FAIL con `Unresolved reference 'SgspeWrite'`, `'LIMA'`, `'StrictQuery'` y `'Records'`.

- [ ] **Paso 3: `src/main/kotlin/sgspe/common/SgspeWrite.kt`**

```kotlin
package sgspe.common

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// la marca de una escritura que hace el código de sgspe (Records, y en F2 los listeners y los trabajos): un elemento del
// contexto de la corrutina, como EscrituraDeCaja de caja-backend@dc9e391 (src/main/kotlin/caja/comun/EscrituraDeCaja.kt).
// RecordService llama a las guardas en esa misma corrutina, así que una guarda la ve antes de escribir (WAS-ADR-0040).
// una escritura sin la marca entró por otra puerta: la API genérica de wasichai, /admin o un módulo. un cliente http no
// puede ponerla: es un objeto del proceso, no un dato de la petición
object SgspeWrite : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<SgspeWrite>

    override fun toString() = "SgspeWrite"
}
```

- [ ] **Paso 4: `src/main/kotlin/sgspe/common/Records.kt`**

```kotlin
package sgspe.common

import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import wasichai.core.data.RecordRequest
import wasichai.core.data.RecordResponse
import wasichai.core.data.RecordService
import java.util.UUID

// el RecordService de wasichai para el código de sgspe (como Registros de caja-backend@dc9e391,
// src/main/kotlin/caja/comun/Registros.kt). RecordService comprueba los permisos de objeto y de campo de quien llama y
// valida cada escritura, así que aquí no se repite. cada escritura lleva la marca SgspeWrite, para que las guardas de F2
// distingan el código propio de la API genérica, y su motivo, que wasichai deja en audit_log.reason (ADR-0011)
@Component
class Records(
    private val records: RecordService
) {
    suspend fun get(
        objectName: String,
        id: UUID
    ): RecordResponse = records.get(objectName, id)

    suspend fun create(
        objectName: String,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { records.create(objectName, RecordRequest(attributes), reason) }

    suspend fun update(
        objectName: String,
        id: UUID,
        attributes: Map<String, Any?>,
        reason: String?
    ): RecordResponse = withContext(SgspeWrite) { records.update(objectName, id, RecordRequest(attributes), reason) }
}
```

- [ ] **Paso 5: `src/main/kotlin/sgspe/common/Lima.kt`**

```kotlin
package sgspe.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

// la hora de sgspe es la de Lima, no la de la máquina (architecture.md §5.2), como Lima.kt de caja-backend@dc9e391
val LIMA: ZoneId = ZoneId.of("America/Lima")

// el reloj de sgspe. una prueba lo sustituye con un @Bean @Primary propio (Clock.fixed): nada de sgspe lee la hora de otro
// sitio
@Configuration(proxyBeanMethods = false)
class Clocks {
    @Bean
    fun clock(): Clock = Clock.system(LIMA)
}
```

- [ ] **Paso 6: `src/main/kotlin/sgspe/common/StrictQuery.kt`**

```kotlin
package sgspe.common

import org.springframework.http.HttpStatus
import wasichai.core.common.FieldViolation
import wasichai.core.common.WasichaiException

// un parámetro de consulta desconocido o mal formado es un 422 que lo nombra (ASM-054, api.md §3.3): nunca se ignora en
// silencio, porque un filtro mal escrito devolvería todo. el manejador de wasichai lo vuelve problem+json
class UnprocessableContentException(
    violations: List<FieldViolation>
) : WasichaiException(HttpStatus.valueOf(422), "Parámetro de consulta desconocido o mal formado", violations)

object StrictQuery {
    // los parámetros de la consulta deben estar todos en `allowed`; si no, 422 con uno por desconocido, en orden
    fun requireOnly(
        params: Map<String, List<String>>,
        allowed: Set<String>
    ) {
        val unknown = params.keys.filter { it !in allowed }.sorted()
        if (unknown.isEmpty()) return
        val admitted = allowed.sorted().joinToString(", ")
        throw UnprocessableContentException(unknown.map { FieldViolation(it, "no es un parámetro de esta consulta; se admiten $admitted") })
    }
}
```

- [ ] **Paso 7: correr las pruebas y verlas pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) \
./gradlew integrationTest --tests 'sgspe.common.RecordsApiTest' --rerun
```

Esperado: `BUILD SUCCESSFUL` dos veces; `SgspeWriteTest` (1), `LimaTest` (2) y `StrictQueryTest` (3) en el build, y
`RecordsApiTest` (1) en la integración.

- [ ] **Paso 8: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add src/main/kotlin/sgspe/common/SgspeWrite.kt src/main/kotlin/sgspe/common/Records.kt \
  src/main/kotlin/sgspe/common/Lima.kt src/main/kotlin/sgspe/common/StrictQuery.kt \
  src/test/kotlin/sgspe/common/SgspeWriteTest.kt src/test/kotlin/sgspe/common/LimaTest.kt \
  src/test/kotlin/sgspe/common/StrictQueryTest.kt src/test/kotlin/sgspe/common/RecordsApiTest.kt
git commit -F - <<'EOF'
feat(common): marca SgspeWrite, envoltura de RecordService, Lima y 422 estricto

Records escribe con la marca y con motivo; las guardas transversales llegan con F2.
StrictQuery responde 422 con cada parámetro desconocido y los admitidos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 10: A7 · `ArchitectureTest`

**Pista:** A.

**Files:**
- Test: `src/test/kotlin/sgspe/ArchitectureTest.kt`

**Interfaces:**
- Consumes: las fuentes de las Tareas 3, 8 y 9; `model/roles.json` (Tarea 6).
- Produces: las reglas que vigilan F2 a F8: nada bajo `wasichai`, sin APIs internas (también en las pruebas), sin literales
  de rol en `src/main/kotlin`, dominio sin tableros, entregables ni IA, y `sgspe.observability` sin otros paquetes de
  `sgspe`.

- [ ] **Paso 1: escribir la prueba**

```kotlin
package sgspe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.io.File

// las reglas de arquitectura de SGSPE (architecture.md §5.3, security.md §5.10, RQ-SEC-005, RNF-MNT-005), por escaneo de
// fuentes como las pruebas de pureza de caja-backend@dc9e391 (src/test/kotlin/caja/recaudacion/PurezaDeLaRecaudacionTest.kt).
// cada regla tiene su vigía, y una prueba aparte demuestra que el vigía reconoce lo prohibido y deja pasar lo permitido
class ArchitectureTest {
    private val main = File("src/main/kotlin")
    private val test = File("src/test/kotlin")

    private fun sources(root: File): List<File> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    // el código de una fuente: sus líneas sin los comentarios de línea, que pueden nombrar un rol o una api para explicar
    // por qué no se usa
    private fun code(file: File): List<String> = file.readLines().map { it.substringBefore("//") }

    private fun offenders(
        files: List<File>,
        rule: Regex
    ): List<String> =
        files.flatMap { file ->
            code(file).mapIndexedNotNull { i, line -> if (rule.containsMatchIn(line)) "${file.path}:${i + 1}: ${line.trim()}" else null }
        }

    @Test
    fun `el escaneo ve las fuentes de F1`() {
        val names = sources(main).map { it.name }
        assertTrue(names.containsAll(listOf("SgspeApplication.kt", "CorrelationIdFilter.kt", "Records.kt")), "$names")
    }

    @Test
    fun `no hay codigo bajo el paquete wasichai`() {
        assertFalse(File(main, "wasichai").exists())
        assertFalse(File(test, "wasichai").exists())
        assertEquals(emptyList<String>(), offenders(sources(main) + sources(test), WASICHAI_PACKAGE))
    }

    @Test
    fun `no se importan las apis internas de wasichai, tampoco en las pruebas`() {
        assertEquals(emptyList<String>(), offenders(sources(main) + sources(test), INTERNAL_API))
    }

    @Test
    fun `ningun nombre de rol aparece en el codigo principal`() {
        assertEquals(emptyList<String>(), offenders(sources(main), roleLiteral()))
    }

    @Test
    fun `los paquetes de dominio no dependen de tableros, entregables ni IA`() {
        val domain = DOMAIN_PACKAGES.flatMap { sources(File(main, "sgspe/$it")) }
        assertEquals(emptyList<String>(), offenders(domain, REPORTING_IMPORT))
    }

    @Test
    fun `observability no depende de otros paquetes de sgspe`() {
        assertEquals(emptyList<String>(), offenders(sources(File(main, "sgspe/observability")), OTHER_SGSPE_IMPORT))
    }

    @Test
    fun `los vigias reconocen lo prohibido y dejan pasar lo permitido`() {
        assertTrue(WASICHAI_PACKAGE.containsMatchIn("package wasichai.core.data"))
        assertFalse(WASICHAI_PACKAGE.containsMatchIn("import wasichai.core.data.RecordService"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.data.PhysicalTableRecordStore"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.data.RecordStore"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.audit.AuditService"))
        assertTrue(INTERNAL_API.containsMatchIn("import wasichai.core.metadata.ObjectSchemaManager"))
        assertFalse(INTERNAL_API.containsMatchIn("import wasichai.core.data.RecordService"))
        assertFalse(INTERNAL_API.containsMatchIn("import wasichai.core.data.RecordWriteGuard"))
        val roles = roleLiteral()
        assertTrue(roles.containsMatchIn("if (user.roles.contains(\"AUDITOR\")) return"))
        assertTrue(roles.containsMatchIn("val admin = \"ADMIN\""))
        assertTrue(roles.containsMatchIn("listOf(\"GIS_ANALYST\")"))
        assertFalse(roles.containsMatchIn("val isAdmin = user.isAdmin"))
        assertFalse(roles.containsMatchIn("requirePermission(user, \"EDIT_ANY\", objectId)"))
        assertTrue(REPORTING_IMPORT.containsMatchIn("import sgspe.dashboard.Indicators"))
        assertTrue(REPORTING_IMPORT.containsMatchIn("import sgspe.ai.SuggestionWriter"))
        assertFalse(REPORTING_IMPORT.containsMatchIn("import sgspe.common.Records"))
        assertTrue(OTHER_SGSPE_IMPORT.containsMatchIn("import sgspe.common.Records"))
        assertFalse(OTHER_SGSPE_IMPORT.containsMatchIn("import sgspe.observability.CorrelationId"))
    }

    companion object {
        private val WASICHAI_PACKAGE = Regex("""^\s*package\s+wasichai\b""")
        private val INTERNAL_API = Regex("""^\s*import\s+wasichai\.\S*\.(RecordStore|PhysicalTableRecordStore|AuditService|ObjectSchemaManager)\b""")
        private val REPORTING_IMPORT = Regex("""^\s*import\s+sgspe\.(dashboard|deliverable|ai)\.""")
        private val OTHER_SGSPE_IMPORT = Regex("""^\s*import\s+sgspe\.(?!observability\.)""")
        private val DOMAIN_PACKAGES =
            listOf("stakeholder", "engagement", "commitment", "agreement", "attention", "risk", "warning", "investment", "evidence", "territory")

        // los 15 roles declarados, los 2 de mappedToAdmin y el ADMIN de wasichai, leídos de model/roles.json
        private fun roleLiteral(): Regex {
            val file = JsonMapper.builder().build().readTree(File("model/roles.json"))
            val names = file["roles"].values().map { it["name"].asString() } + file["mappedToAdmin"].values().map { it.asString() } + "ADMIN"
            return Regex("\\b(${names.joinToString("|")})\\b")
        }
    }
}
```

- [ ] **Paso 2: plantar una violación y ver que la prueba falla**

```bash
cd /Users/jorge/IdeaProjects/sgspe
printf 'package sgspe\n\nval VIOLACION_DE_PRUEBA = listOf("AUDITOR")\n' > src/main/kotlin/sgspe/ViolacionDePrueba.kt
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew test --tests 'sgspe.ArchitectureTest'
```

Esperado: FAIL en `ningun nombre de rol aparece en el codigo principal`, con
`src/main/kotlin/sgspe/ViolacionDePrueba.kt:3: val VIOLACION_DE_PRUEBA = listOf("AUDITOR")` en el mensaje.

- [ ] **Paso 3: quitar la violación y verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
rm src/main/kotlin/sgspe/ViolacionDePrueba.kt
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build
git status --short src/main
```

Esperado: `BUILD SUCCESSFUL`, `ArchitectureTest` (7) en verde; `git status --short src/main` vacío.

- [ ] **Paso 4: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add src/test/kotlin/sgspe/ArchitectureTest.kt
git commit -F - <<'EOF'
test(arch): reglas de arquitectura por escaneo de fuentes

Sin código bajo wasichai, sin APIs internas, sin literales de rol en el código principal,
dominio sin tableros, entregables ni IA, y observability sin otros paquetes de sgspe.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 11: A8 · CI de `sgspe` y verificación de la trazabilidad

**Pista:** A.

**Files:**
- Create: `.github/workflows/ci.yml` (adaptado de caja), `.github/workflows/commits.yml` y `.github/dependabot.yml`
  (copias exactas de caja-backend@dc9e391)
- Create: `tools/check_traceability.py`
- Test: `tools/test_check_traceability.py`

**Interfaces:**
- Consumes: todo lo de las Tareas 3 a 10; `docs/requirements-analysis.md` y `docs/requirements-traceability.md`.
- Produces: los trabajos `build`, `integration`, `model`, `traceability` y `format` en cada PR; `check_traceability.py` con
  salida `ok: N requisitos, todos con su fila`, que nombra la definición de terminado de F1.

- [ ] **Paso 1: escribir la prueba que falla, `tools/test_check_traceability.py`**

```python
"""Pruebas de check_traceability.py.

Correr: python3 -m unittest discover -s tools -v
"""
import io
import os
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

import check_traceability

ANALYSIS = (
    "| ID | Requisito |\n|---|---|\n"
    "| RF-PRJ-001 | Registrar proyectos |\n"
    "| RNF-I18N-001 | Español e inglés |\n"
    "| RN-COM-001 | Cumplido con acta |\n"
    "| RQ-SEC-001 | JWT de wasichai |\n"
    "Un párrafo que cita RF-PRJ-002 sin definirlo.\n"
)
MATRIX_OK = "| Requirement | Source |\n|---|---|\n| RF-PRJ-001 | x |\n| RNF-I18N-001 | x |\n| RN-COM-001 | x |\n| RQ-SEC-001 | x |\n"


def run(args):
    out, err = io.StringIO(), io.StringIO()
    with redirect_stdout(out), redirect_stderr(err):
        code = check_traceability.main(args)
    return code, out.getvalue(), err.getvalue()


class CheckTraceabilityTests(unittest.TestCase):
    def test_solo_cuenta_los_ids_que_abren_una_fila(self):
        self.assertEqual(check_traceability.row_ids(ANALYSIS), {"RF-PRJ-001", "RNF-I18N-001", "RN-COM-001", "RQ-SEC-001"})

    def test_nada_falta_cuando_cada_id_tiene_su_fila(self):
        self.assertEqual(check_traceability.missing(ANALYSIS, MATRIX_OK), [])

    def test_nombra_los_que_faltan_en_orden(self):
        self.assertEqual(check_traceability.missing(ANALYSIS, "| RQ-SEC-001 | x |\n"), ["RF-PRJ-001", "RN-COM-001", "RNF-I18N-001"])

    def test_la_cli_sale_con_1_y_nombra_los_que_faltan(self):
        with tempfile.TemporaryDirectory() as folder:
            analysis, matrix = os.path.join(folder, "a.md"), os.path.join(folder, "m.md")
            with open(analysis, "w", encoding="utf-8") as f:
                f.write(ANALYSIS)
            with open(matrix, "w", encoding="utf-8") as f:
                f.write("| RF-PRJ-001 | x |\n")
            code, out, err = run(["--analysis", analysis, "--matrix", matrix])
        self.assertEqual(code, 1)
        self.assertIn("sin fila en la matriz: RN-COM-001", out)

    def test_un_archivo_que_falta_sale_con_2(self):
        code, out, err = run(["--analysis", "/no/existe.md"])
        self.assertEqual(code, 2)
        self.assertIn("/no/existe.md", err)

    def test_los_documentos_reales_estan_completos(self):
        code, out, err = run([])
        self.assertEqual(code, 0, out + err)
        self.assertTrue(out.startswith("ok: "), out)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Paso 2: verla fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
python3 -m unittest discover -s tools -v 2>&1 | tail -3
```

Esperado: `ModuleNotFoundError: No module named 'check_traceability'` y `FAILED (errors=1)`.

- [ ] **Paso 3: `tools/check_traceability.py`**

```python
#!/usr/bin/env python3
"""Toda RF, RNF, RN y RQ del análisis tiene su fila en la matriz de trazabilidad (RNF-MNT-009, roadmap F1).

Lee docs/requirements-analysis.md, donde cada requisito se define como la primera celda de una fila de tabla, y
docs/requirements-traceability.md, y nombra los IDs del análisis que no tienen fila en la matriz. Solo biblioteca estándar.

Correr: python3 tools/check_traceability.py [--analysis RUTA] [--matrix RUTA]
Salida: 0 si cada requisito tiene su fila; 1 si falta alguno (los nombra); 2 si un archivo falta.
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROW_ID = re.compile(r"^\| ((?:RF|RNF|RN|RQ)-[A-Z0-9]+-\d{3}) \|", re.MULTILINE)


def row_ids(text):
    """Los IDs de requisito que abren una fila de tabla."""
    return set(ROW_ID.findall(text))


def missing(analysis, matrix):
    """Los IDs definidos en el análisis sin fila en la matriz, ordenados."""
    return sorted(row_ids(analysis) - row_ids(matrix))


def _read(path):
    try:
        with open(path, encoding="utf-8") as f:
            return f.read()
    except OSError as e:
        print(f"error: {path}: {e.strerror or e}", file=sys.stderr)
        return None


def main(argv=None):
    p = argparse.ArgumentParser(description="Comprueba que cada requisito del análisis tenga su fila en la matriz.")
    p.add_argument("--analysis", default=os.path.join(ROOT, "docs", "requirements-analysis.md"))
    p.add_argument("--matrix", default=os.path.join(ROOT, "docs", "requirements-traceability.md"))
    args = p.parse_args(sys.argv[1:] if argv is None else argv)
    analysis, matrix = _read(args.analysis), _read(args.matrix)
    if analysis is None or matrix is None:
        return 2
    absent = missing(analysis, matrix)
    for requirement in absent:
        print(f"sin fila en la matriz: {requirement}")
    if absent:
        return 1
    print(f"ok: {len(row_ids(analysis))} requisitos, todos con su fila")
    return 0


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Paso 4: verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe
python3 -m unittest discover -s tools -v 2>&1 | tail -3
python3 tools/check_traceability.py
```

Esperado: `Ran 6 tests` y `OK`; `ok: 298 requisitos, todos con su fila`.

- [ ] **Paso 5: copiar `commits.yml` y `dependabot.yml` de caja**

```bash
cd /Users/jorge/IdeaProjects/sgspe
mkdir -p .github/workflows
cp /Users/jorge/IdeaProjects/caja-backend/.github/workflows/commits.yml .github/workflows/commits.yml
cp /Users/jorge/IdeaProjects/caja-backend/.github/dependabot.yml .github/dependabot.yml
grep -c '@[0-9a-f]\{40\}' .github/workflows/commits.yml
```

Esperado: `3` (las tres acciones de `commits.yml` fijadas por SHA). Ninguna edición: las dos sirven tal cual.

- [ ] **Paso 6: `.github/workflows/ci.yml`** (adaptado de caja-backend@dc9e391 `.github/workflows/ci.yml`: imagen PostGIS,
  Python para el aprovisionamiento en la integración, `apply_roles.py --dry-run` y el trabajo `traceability`; las mismas
  acciones fijadas por SHA)

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true

# packages: the wasichai libraries come from github packages (settings.gradle.kts reads GITHUB_ACTOR / GITHUB_TOKEN)
permissions:
  contents: read
  packages: read

env:
  GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

jobs:
  build:
    name: Build (ktlint + unit tests)
    runs-on: ubuntu-latest
    # a hung dependency download or gradle daemon must not burn the default 6 h
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
      - name: Build
        run: ./gradlew build --no-daemon
      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: test-reports
          path: build/reports/tests/

  integration:
    name: Integration tests
    runs-on: ubuntu-latest
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-java@de7274f081f381c8f8158605e0321c36c376e2e6 # v6.0.1
        with:
          distribution: temurin
          java-version: '25'
      - uses: gradle/actions/setup-gradle@3f5f9adaf7d9fecd50b5935e54106014257a94e6 # v6.4.0
      # SgspeApiTest provisions with model/*.py: the runner's python 3 (>= 3.11, stdlib only)
      - run: python3 --version
      # github runners have a local docker daemon: testcontainers starts postgis/postgis:18-3.6. no WASICHAI_TEST_DB_*
      # here, on purpose. pulling up front keeps the pull out of the container startup timeout
      - name: Pull database image
        run: docker pull -q postgis/postgis:18-3.6
      - name: Integration tests
        run: ./gradlew integrationTest --no-daemon
      - name: Upload test reports
        if: failure()
        uses: actions/upload-artifact@043fb46d1a93c77aae656e7c1c64a875d1fc6a0a # v7.0.1
        with:
          name: integration-test-reports
          path: build/reports/tests/

  model:
    name: Model and roles (python)
    runs-on: ubuntu-latest
    timeout-minutes: 10
    defaults:
      run:
        working-directory: model
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      # the runner's python 3 (>= 3.11, stdlib only): no setup-python to pin
      - run: python3 --version
      - run: python3 apply.py --validate-only
      - run: python3 apply_roles.py --dry-run > /dev/null
      - run: python3 -m unittest -v

  traceability:
    name: Traceability (python)
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - run: python3 tools/check_traceability.py
      - run: python3 -m unittest discover -s tools -v

  format:
    name: Format (prettier)
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
      - uses: actions/setup-node@820762786026740c76f36085b0efc47a31fe5020 # v7.0.0
        with:
          node-version: '26'
          cache: yarn
      - run: yarn install --frozen-lockfile
      # yaml and json; kotlin is ktlint's (build job), python and markdown keep their hand formatting
      - run: yarn format:check
```

- [ ] **Paso 7: comprobar el formato y la sintaxis de los flujos**

```bash
cd /Users/jorge/IdeaProjects/sgspe
yarn -s format
yarn -s format:check; echo "salida: $?"
grep -c 'postgis/postgis:18-3.6' .github/workflows/ci.yml
```

Esperado: `salida: 0` (prettier también valida que los tres YAML se parsean); `2`.

- [ ] **Paso 8: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add .github/workflows/ci.yml .github/workflows/commits.yml .github/dependabot.yml \
  tools/check_traceability.py tools/test_check_traceability.py
git commit -F - <<'EOF'
ci: build, integración con PostGIS, modelo, trazabilidad y formato

Acciones fijadas por SHA como caja-backend; Dependabot y commitlint copiados de caja.
check_traceability.py exige una fila de la matriz por cada RF, RNF, RN y RQ.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

La CI corre recién cuando se abra el PR (Tarea 21). Si el trabajo `build` falla con `401` al resolver `wasichai-bom`, el
usuario da acceso de lectura al repositorio `wasichai/sgspe` en la configuración del paquete de wasichai.

---

### Tarea 12: B1 · Andamiaje de `sgspe-ui`

**Pista:** B (`/Users/jorge/IdeaProjects/sgspe-ui`, rama `feat/fase-1-fundacion`).

**Files:**
- Create (copia exacta de caja-ui@a45b224): `.npmrc`, `.editorconfig`, `.gitignore`, `.prettierrc.json`, `.husky/commit-msg`,
  `tsconfig.json`, `src/test/setup.ts`
- Create (copia exacta de caja-backend@dc9e391, porque trae la excepción de Dependabot): `commitlint.config.mjs`
- Create (copiados con ediciones): `tsconfig.build.json`, `SECURITY.md`
- Create: `package.json`, `yarn.lock` (generado), `vite.config.ts`, `index.html`, `src/main.tsx`, `src/index.css`
- Test: `src/viteConfig.test.ts`

**Interfaces:**
- Produces: `yarn dev` en el puerto 5182 con proxy `/api` → `http://localhost:8094` (`WASICHAI_API_URL` lo cambia);
  `yarn lint`, `yarn typecheck`, `yarn test` y `yarn build`; Vitest con `jsdom`, `globals` y `src/test/setup.ts`; pruebas
  `src/**/*.test.{ts,tsx}`.

- [ ] **Paso 1: copiar la configuración de caja**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
mkdir -p .husky src/test
for f in .npmrc .editorconfig .gitignore .prettierrc.json tsconfig.json tsconfig.build.json SECURITY.md src/test/setup.ts .husky/commit-msg; do
  cp "/Users/jorge/IdeaProjects/caja-ui/$f" "$f"
done
cp /Users/jorge/IdeaProjects/caja-backend/commitlint.config.mjs commitlint.config.mjs
cat .npmrc
sed -i '' 's#wasichai/caja-ui/security#wasichai/sgspe-ui/security#' SECURITY.md
grep -c 'wasichai/sgspe-ui/security/advisories/new' SECURITY.md
```

Esperado: `@wasichai:registry=https://npm.pkg.github.com` (sin token) y `1`. Si `grep` da `0`, el `SECURITY.md` de caja-ui
usa otra forma del enlace: reemplazar a mano `caja-ui` por `sgspe-ui` en esa línea.

- [ ] **Paso 2: editar `tsconfig.build.json`** (las pruebas `.test.ts` también quedan fuera del build)

Contenido completo:

```json
{
    "extends": "./tsconfig.json",
    "compilerOptions": {
        "types": ["vite/client"]
    },
    "include": ["src"],
    "exclude": ["src/**/*.test.ts", "src/**/*.test.tsx", "src/test"]
}
```

- [ ] **Paso 3: `package.json`** (adaptado de caja-ui@a45b224 `package.json`: nombre, descripción, sin `react-hook-form`
  hasta F2, `lint` y `format` sin `renovate.json`)

```json
{
    "name": "sgspe-ui",
    "version": "0.1.0",
    "private": true,
    "description": "SGSPE: portal y administración sobre @wasichai/*",
    "type": "module",
    "engines": {
        "node": ">=26"
    },
    "scripts": {
        "dev": "vite",
        "preview": "vite preview",
        "lint": "prettier --check src index.html package.json tsconfig.json tsconfig.build.json vite.config.ts commitlint.config.mjs",
        "format": "prettier --write src index.html package.json tsconfig.json tsconfig.build.json vite.config.ts commitlint.config.mjs",
        "test": "vitest run",
        "build": "tsc -p tsconfig.build.json && vite build",
        "typecheck": "tsc -p tsconfig.json",
        "prepare": "husky"
    },
    "dependencies": {
        "@wasichai/core": "0.5.0",
        "@wasichai/forms": "0.5.0",
        "@wasichai/pages": "0.5.0",
        "@wasichai/ui": "0.5.0",
        "@wasichai/views": "0.5.0",
        "@tanstack/react-query": "5.104.0",
        "i18next": "26.4.2",
        "lucide-react": "1.48.0",
        "react": "19.3.0",
        "react-dom": "19.3.0",
        "react-i18next": "17.0.15",
        "react-router": "8.4.0"
    },
    "devDependencies": {
        "@wasichai/testing": "0.5.0",
        "@commitlint/cli": "21.2.3",
        "@commitlint/config-conventional": "21.2.3",
        "@tailwindcss/vite": "4.3.3",
        "@testing-library/dom": "10.4.2",
        "@testing-library/jest-dom": "7.0.1",
        "@testing-library/react": "16.3.3",
        "@testing-library/user-event": "14.6.7",
        "@types/node": "26.6.3",
        "@types/react": "19.3.0",
        "@types/react-dom": "19.3.0",
        "@vitejs/plugin-react": "6.1.1",
        "husky": "9.1.7",
        "jsdom": "30.1.1",
        "prettier": "3.9.9",
        "tailwindcss": "4.3.3",
        "typescript": "7.0.2",
        "vite": "8.3.1",
        "vitest": "5.0.2"
    }
}
```

- [ ] **Paso 4: instalar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
NODE_AUTH_TOKEN=$(gh auth token) yarn install
head -2 yarn.lock
git config --get core.hooksPath
```

Esperado: instalación sin errores; `# THIS IS AN AUTOGENERATED FILE. DO NOT EDIT THIS FILE DIRECTLY.` y `# yarn lockfile v1`;
`.husky/_`. Un `401` al bajar `@wasichai/*` significa que falta el alcance `read:packages` (Tarea 1, Paso 4).

- [ ] **Paso 5: escribir la prueba que falla, `src/viteConfig.test.ts`**

```ts
import { describe, expect, it } from 'vitest'
import config from '../vite.config'

// ADR-0026: Vite sirve en el 5182 y reenvía /api al backend en el 8094; mismo origen, sin CORS
describe('vite.config', () => {
  it('sirve en 5182 y reenvía /api al backend en 8094', () => {
    expect(config.server?.port).toBe(5182)
    expect(config.server?.strictPort).toBe(true)
    expect(config.server?.proxy).toEqual({ '/api': 'http://localhost:8094' })
    expect(config.preview?.port).toBe(5182)
    expect(config.preview?.proxy).toEqual({ '/api': 'http://localhost:8094' })
  })
})
```

- [ ] **Paso 6: verla fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn test 2>&1 | tail -5
```

Esperado: FAIL con `Failed to load url ../vite.config` (o `Cannot find module '../vite.config'`).

- [ ] **Paso 7: `vite.config.ts`, `index.html`, `src/main.tsx` y `src/index.css`**

`vite.config.ts` (adaptado de caja-ui@a45b224: puerto 5182, backend 8094, pruebas `.ts` y `.tsx`):

```ts
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

const api = process.env.WASICHAI_API_URL ?? 'http://localhost:8094'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5182, strictPort: true, proxy: { '/api': api } },
  preview: { port: 5182, strictPort: true, proxy: { '/api': api } },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
    // con carga (CI, varias suites a la vez) una pantalla entera pasa los 5 s de vitest sin nada malo: 15 s
    testTimeout: 15000,
    hookTimeout: 15000
  }
})
```

`index.html` (el script de arranque de temas llega en la Tarea 15):

```html
<!doctype html>
<html lang="es">
    <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>SGSPE</title>
    </head>
    <body>
        <div id="root"></div>
        <script type="module" src="/src/main.tsx"></script>
    </body>
</html>
```

`src/main.tsx` (provisional; la Tarea 13 lo reemplaza):

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <p>SGSPE</p>
  </StrictMode>
)
```

`src/index.css` (adaptado de caja-ui@a45b224 `src/index.css`, sin el tema portal-tributario):

```css
@import 'tailwindcss';
@import '@wasichai/ui/theme.css';
/* los paquetes @wasichai traen nombres de clase, no css: tailwind los escanea */
@source '../node_modules/@wasichai';
```

- [ ] **Paso 8: verla pasar y comprobar el resto**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format
yarn test 2>&1 | tail -4
yarn lint && yarn typecheck && yarn build
```

Esperado: `Test Files  1 passed (1)`; `lint`, `typecheck` y `build` sin errores; `dist/` creado (ignorado por `.gitignore`).

- [ ] **Paso 9: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add .npmrc .editorconfig .gitignore .prettierrc.json commitlint.config.mjs .husky/commit-msg tsconfig.json \
  tsconfig.build.json SECURITY.md package.json yarn.lock vite.config.ts index.html src/main.tsx src/index.css \
  src/test/setup.ts src/viteConfig.test.ts
git commit -F - <<'EOF'
build: andamiaje de sgspe-ui sobre @wasichai/* 0.5.0

Vite 8 en el puerto 5182 con proxy de /api al backend en el 8094, Tailwind con el
tema de @wasichai/ui, TypeScript, prettier, commitlint y husky, como caja-ui.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 13: B2 · `/admin` con `WasichaiApp`

**Pista:** B.

**Files:**
- Create: `src/admin/AdminApp.tsx`
- Modify: `src/main.tsx`
- Test: `src/admin/AdminApp.test.tsx`

**Interfaces:**
- Consumes: el andamiaje de la Tarea 12.
- Produces: `AdminApp` (sin props), montado por `src/main.tsx` cuando la ruta empieza por `/admin`; configuración
  compartida con el portal: `apiBaseUrl: '/api'`, `storagePrefix: 'sgspe'` (claves `sgspe.token`, `sgspe.user`,
  `sgspe.lang`, `sgspe.theme`), `languages: ['es', 'en']`.

- [ ] **Paso 1: escribir la prueba que falla, `src/admin/AdminApp.test.tsx`** (adaptada de caja-ui@a45b224
  `src/admin/AdminApp.test.tsx`)

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { mockFetch, type FetchMock } from '@wasichai/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { AdminApp } from './AdminApp'

const admin = { id: 'u1', email: 'admin@wasichai.local', displayName: 'Administración de prueba', organizationId: 'o1', roles: ['ADMIN'] }

let fetch: FetchMock | null = null
beforeEach(() => {
  localStorage.clear()
  delete document.documentElement.dataset.theme
  window.history.pushState({}, '', '/admin')
})
afterEach(() => {
  fetch?.restore()
  fetch = null
})

// monta la app de verdad y entra por su formulario; lo demás recibe el 404 del mock
async function signIn() {
  fetch = mockFetch([
    { method: 'POST', path: '/auth/login', body: { token: 't', expiresAt: '2026-12-31T00:00:00Z', user: admin } },
    { path: '/auth/me/permissions', body: { admin: true, objects: {} } },
    { path: '/objects', body: [] }
  ])
  render(<AdminApp />)
  const email = await screen.findByLabelText('Correo')
  // en desarrollo, el login de /admin propone el administrador sembrado
  expect(email).toHaveValue('admin@wasichai.local')
  await userEvent.type(screen.getByLabelText('Contraseña'), 'admin')
  await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }))
  expect(await screen.findByRole('heading', { name: 'Inicio' })).toBeInTheDocument()
}

describe('admin', () => {
  it('inicia sesión con el admin sembrado y guarda la sesión bajo sgspe.*', async () => {
    await signIn()
    expect(screen.getAllByText('SGSPE').length).toBeGreaterThan(0)
    expect(localStorage.getItem('sgspe.token')).toBe('t')
  })

  // los starters de F1: views, forms y pages; ningún módulo que el backend no corre, ni el asistente
  it('agrega las pantallas de los starters de F1 y solo esas', async () => {
    await signIn()
    for (const label of ['Vistas', 'Formularios', 'Páginas']) {
      expect(screen.getByRole('link', { name: label })).toBeInTheDocument()
    }
    for (const label of ['Workflows', 'Documentos', 'Mapas', 'Capas', 'Reglas', 'Asistente']) {
      expect(screen.queryByRole('link', { name: label })).not.toBeInTheDocument()
    }
  })

  // el tema del portal es el de /admin: el que se elige en un lado se ve en el otro
  it('abre con el tema que guardó el portal y guarda el que se elige', async () => {
    localStorage.setItem('sgspe.theme', 'dark')
    await signIn()
    expect(document.documentElement.dataset.theme).toBe('dark')
    const theme = screen.getByRole('combobox', { name: 'Tema' })
    expect(theme).toHaveValue('dark')
    await userEvent.selectOptions(theme, 'light')
    expect(document.documentElement.dataset.theme).toBe('light')
    expect(localStorage.getItem('sgspe.theme')).toBe('light')
  })
})
```

- [ ] **Paso 2: verla fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn test src/admin 2>&1 | tail -5
```

Esperado: FAIL con `Failed to resolve import "./AdminApp"`.

- [ ] **Paso 3: `src/admin/AdminApp.tsx`** (adaptado de caja-ui@a45b224 `src/admin/AdminApp.tsx`)

```tsx
import { WasichaiApp } from '@wasichai/core'
import { formsModule } from '@wasichai/forms'
import { pagesModule } from '@wasichai/pages'
import { viewsModule } from '@wasichai/views'

// /admin: core más los módulos de los starters que corre sgspe en F1 (views, forms y pages); nunca un módulo sin su
// starter ni agentModule() (ADR-0024). storagePrefix 'sgspe' es el del portal: un solo login, el mismo tema y el mismo
// idioma. el administrador sembrado se propone solo en desarrollo
export function AdminApp() {
  return (
    <WasichaiApp
      config={{
        apiBaseUrl: '/api',
        appName: 'SGSPE',
        appTagline: 'Administración',
        storagePrefix: 'sgspe',
        basename: '/admin',
        languages: ['es', 'en'],
        defaultLoginEmail: import.meta.env.DEV ? 'admin@wasichai.local' : ''
      }}
      modules={[viewsModule(), formsModule(), pagesModule()]}
    />
  )
}
```

- [ ] **Paso 4: `src/main.tsx`** (la rama del portal llega en la Tarea 14)

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AdminApp } from './admin/AdminApp'
import './index.css'

// dos apps, un bundle: /admin es la administración de wasichai; lo demás, el portal
const admin = /^\/admin(\/|$)/.test(window.location.pathname)

createRoot(document.getElementById('root')!).render(<StrictMode>{admin ? <AdminApp /> : <p>SGSPE</p>}</StrictMode>)
```

- [ ] **Paso 5: verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format && yarn test 2>&1 | tail -4 && yarn lint && yarn typecheck
```

Esperado: `Test Files  2 passed (2)`, 4 pruebas; `lint` y `typecheck` limpios.

- [ ] **Paso 6: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add src/admin/AdminApp.tsx src/admin/AdminApp.test.tsx src/main.tsx
git commit -F - <<'EOF'
feat(admin): /admin con WasichaiApp y los módulos de los starters de F1

views, forms y pages bajo /admin, con storagePrefix sgspe: el token, el tema y el
idioma se comparten con el portal.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 14: B3 · Portal con sesión y login compartidos, `RequireSession` y `safeNext`

**Pista:** B.

**Files:**
- Create: `src/portal/api.ts`, `src/portal/i18n.ts`, `src/portal/textos/es.ts`, `src/portal/PortalApp.tsx`,
  `src/portal/auth/LoginPage.tsx`, `src/portal/auth/RequireSession.tsx`, `src/portal/shell/AppShell.tsx`,
  `src/portal/pages/InicioPage.tsx`, `src/test/portal.ts`
- Modify: `src/main.tsx`
- Test: `src/portal/auth/RequireSession.test.tsx`, `src/portal/login.test.tsx`

**Interfaces:**
- Consumes: `AdminApp` (Tarea 13).
- Produces:
  - `client: ApiClient` (`src/portal/api.ts`), con `storagePrefix: 'sgspe'`;
  - `NS = 'sgspe'`, `IDIOMAS = ['es', 'en']`, `ajustarI18n(i18n)` y `crearI18n(modules = []): I18n` (`src/portal/i18n.ts`);
  - `ES` y `type Textos = typeof ES` (`src/portal/textos/es.ts`), con las claves `app.lema`, `login.*`, `shell.salir`,
    `inicio.titulo` y `noEncontrada`;
  - `PortalApp`, `LoginPage`, `RequireSession({ children })`, `safeNext(next: string | null): string`, `AppShell` (con
    un `<header>` que es el `banner` de las pruebas y un `<main>`), `InicioPage`;
  - en `src/test/portal.ts`: `RELACIONISTA`, `AUDITORA`, `ADMINISTRACION` (`AuthUser` ficticios `@sgspe.test`),
    `SIN_PERMISOS`, `ADMIN_PERMISOS`, `yo(user)`, `rutasDeSesion(user, permisos): MockRoute[]` y `abrirSesion(user)`.

- [ ] **Paso 1: escribir las ayudas de prueba, `src/test/portal.ts`** (adaptadas de caja-ui@a45b224 `src/test/portal.ts`)

```ts
import type { AuthUser, CallerPermissions } from '@wasichai/core'
import type { MockRoute } from '@wasichai/testing'

// lo que comparten las pruebas del portal: cuentas ficticias @sgspe.test, lo que wasichai contesta de ellas y una sesión
// ya abierta. aquí sí se nombran roles: son datos de prueba (boundaries.test.ts deja fuera src/test)

export const RELACIONISTA: AuthUser = {
  id: 'u-relacionista',
  email: 'relacionista@sgspe.test',
  displayName: 'Relacionista de prueba',
  organizationId: 'o1',
  roles: ['COMMUNITY_RELATIONS_OFFICER']
}
export const AUDITORA: AuthUser = {
  id: 'u-auditoria',
  email: 'auditoria@sgspe.test',
  displayName: 'Auditoría de prueba',
  organizationId: 'o1',
  roles: ['AUDITOR']
}
export const ADMINISTRACION: AuthUser = {
  id: 'u-admin',
  email: 'admin@sgspe.test',
  displayName: 'Administración de prueba',
  organizationId: 'o1',
  roles: ['ADMIN']
}

export const SIN_PERMISOS: CallerPermissions = { admin: false, objects: {} }
export const ADMIN_PERMISOS: CallerPermissions = { admin: true, objects: {} }

// GET /api/auth/me: el AuthenticatedUser de wasichai, la cuenta a la que pertenece el token
export const yo = (user: AuthUser) => ({
  userId: user.id,
  organizationId: user.organizationId,
  email: user.email,
  roles: user.roles,
  admin: user.roles.includes('ADMIN')
})

// lo que el portal pregunta con una sesión abierta: quién es el token y qué puede hacer (WAS-ADR-0020)
export function rutasDeSesion(user: AuthUser, permisos: CallerPermissions): MockRoute[] {
  return [
    { path: '/auth/me', body: yo(user) },
    { path: '/auth/me/permissions', body: permisos }
  ]
}

// una sesión abierta antes de cargar el portal, como la guarda core: el token y el usuario bajo sgspe.*
export function abrirSesion(user: AuthUser) {
  localStorage.setItem('sgspe.token', 't')
  localStorage.setItem('sgspe.user', JSON.stringify(user))
}
```

- [ ] **Paso 2: escribir las pruebas que fallan**

`src/portal/auth/RequireSession.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { mockFetch, type FetchMock } from '@wasichai/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { abrirSesion, RELACIONISTA, rutasDeSesion, SIN_PERMISOS } from '../../test/portal'
import { PortalApp } from '../PortalApp'
import { safeNext } from './RequireSession'

let fetch: FetchMock | null = null
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})
afterEach(() => {
  fetch?.restore()
  fetch = null
})

const aqui = () => window.location.pathname + window.location.search

// Review Focus 1: solo rutas internas después del login; lo que el navegador leería como otro host lleva al inicio
describe('safeNext', () => {
  it.each([
    ['/projects?x=1', '/projects?x=1'],
    ['/', '/'],
    [null, '/'],
    ['', '/'],
    ['//otro.example', '/'],
    ['/\\otro.example', '/'],
    ['/\t/otro.example', '/'],
    ['/\n/otro.example', '/'],
    ['https://otro.example', '/'],
    ['javascript:alert(1)', '/'],
    ['projects', '/']
  ])('%j lleva a %j', (next, esperado) => {
    expect(safeNext(next)).toBe(esperado)
  })
})

describe('RequireSession', () => {
  it('lleva al login a quien no tiene sesión, guardando adónde iba', async () => {
    window.history.pushState({}, '', '/?vista=modulos')
    fetch = mockFetch([])
    render(<PortalApp />)
    expect(await screen.findByLabelText('Contraseña')).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/?vista=modulos')
    expect(screen.queryByRole('banner')).not.toBeInTheDocument()
  })

  // Review Focus 4: un 401 a mitad de sesión borra el token, vuelve al login y, al entrar, regresa a donde estaba
  it('un 401 a mitad de sesión vuelve al login y después regresa a la misma ruta', async () => {
    abrirSesion(RELACIONISTA)
    window.history.pushState({}, '', '/?vista=modulos')
    fetch = mockFetch([{ path: '/auth/me/permissions', status: 401, body: { title: 'Unauthorized', status: 401 } }])
    render(<PortalApp />)
    expect(await screen.findByLabelText('Contraseña')).toBeInTheDocument()
    expect(localStorage.getItem('sgspe.token')).toBeNull()
    expect(new URLSearchParams(window.location.search).get('next')).toBe('/?vista=modulos')

    fetch.restore()
    fetch = mockFetch([
      { method: 'POST', path: '/auth/login', body: { token: 't2', expiresAt: '2026-12-31T00:00:00Z', user: RELACIONISTA } },
      ...rutasDeSesion(RELACIONISTA, SIN_PERMISOS)
    ])
    await userEvent.type(screen.getByLabelText('Correo'), RELACIONISTA.email)
    await userEvent.type(screen.getByLabelText('Contraseña'), 'secreta')
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
    expect(await screen.findByRole('banner')).toBeInTheDocument()
    expect(aqui()).toBe('/?vista=modulos')
    expect(localStorage.getItem('sgspe.token')).toBe('t2')
  })
})
```

`src/portal/login.test.tsx` (adaptada de caja-ui@a45b224 `src/portal/login.test.tsx`):

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { mockFetch, type FetchMock, type MockRoute } from '@wasichai/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { RELACIONISTA, rutasDeSesion, SIN_PERMISOS } from '../test/portal'
import { PortalApp } from './PortalApp'

// el login del portal: la sesión de core (POST /api/auth/login, guardada bajo sgspe.*) y RequireSession delante del marco

let fetch: FetchMock | null = null
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})
afterEach(() => {
  fetch?.restore()
  fetch = null
})

const login = (status = 200): MockRoute =>
  status === 200
    ? { method: 'POST', path: '/auth/login', body: { token: 't', expiresAt: '2026-12-31T00:00:00Z', user: RELACIONISTA } }
    : { method: 'POST', path: '/auth/login', status, body: { title: 'Error', status, detail: 'Fallo de prueba' } }

function start(path: string, routes: MockRoute[]) {
  window.history.pushState({}, '', path)
  fetch = mockFetch(routes)
  render(<PortalApp />)
}

async function ingresar(email: string, password: string) {
  const correo = await screen.findByLabelText('Correo')
  await userEvent.clear(correo)
  await userEvent.type(correo, email)
  await userEvent.type(screen.getByLabelText('Contraseña'), password)
  await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))
}

const aqui = () => window.location.pathname + window.location.search

describe('login', () => {
  it('entra con las credenciales correctas, guarda la sesión bajo sgspe.* y sigue a next', async () => {
    start('/?vista=modulos', [login(), ...rutasDeSesion(RELACIONISTA, SIN_PERMISOS)])
    await ingresar('relacionista@sgspe.test', 'secreta')

    expect(await screen.findByRole('banner')).toBeInTheDocument()
    expect(aqui()).toBe('/?vista=modulos')
    const post = fetch!.calls.find((call) => call.method === 'POST' && call.path === '/auth/login')
    expect(post?.body).toEqual({ email: 'relacionista@sgspe.test', password: 'secreta' })
    expect(localStorage.getItem('sgspe.token')).toBe('t')
    expect(JSON.parse(localStorage.getItem('sgspe.user') ?? 'null')).toEqual(RELACIONISTA)
  })

  // el administrador sembrado es el login de desarrollo de /admin; el portal empieza vacío
  it('no propone ningún correo', async () => {
    start('/login', [])
    expect(await screen.findByLabelText('Correo')).toHaveValue('')
  })

  it('dice si las credenciales son incorrectas y no guarda sesión', async () => {
    start('/login', [login(401)])
    await ingresar('relacionista@sgspe.test', 'equivocada')
    expect(await screen.findByRole('alert')).toHaveTextContent('Correo o contraseña incorrectos')
    expect(window.location.pathname).toBe('/login')
    expect(localStorage.getItem('sgspe.token')).toBeNull()
  })

  it('distingue cualquier otro fallo de las credenciales incorrectas', async () => {
    start('/login', [login(503)])
    await ingresar('relacionista@sgspe.test', 'secreta')
    const alerta = await screen.findByRole('alert')
    expect(alerta).toHaveTextContent('No se pudo iniciar sesión')
    expect(alerta).not.toHaveTextContent('Correo o contraseña incorrectos')
  })

  it('va al inicio en lugar de a otro sitio', async () => {
    start('/login?next=//otro.example', [login(), ...rutasDeSesion(RELACIONISTA, SIN_PERMISOS)])
    await ingresar('relacionista@sgspe.test', 'secreta')
    expect(await screen.findByRole('banner')).toBeInTheDocument()
    expect(aqui()).toBe('/')
  })
})
```

- [ ] **Paso 3: verlas fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn test src/portal 2>&1 | tail -5
```

Esperado: FAIL con `Failed to resolve import "../PortalApp"` y `"./RequireSession"`.

- [ ] **Paso 4: el cliente, los textos y el i18n**

`src/portal/api.ts`:

```ts
import { createApiClient } from '@wasichai/core'

// el mismo baseUrl y el mismo storagePrefix que /admin: el token con el que se entra en uno sirve en el otro (sgspe.token)
export const client = createApiClient({ baseUrl: '/api', storagePrefix: 'sgspe' })
```

`src/portal/textos/es.ts`:

```ts
// los textos del portal en español, el idioma por defecto (RNF-I18N-001). en.ts tendrá las mismas claves (Tarea 17)
export const ES = {
  app: { lema: 'Gestión social de proyectos extractivos' },
  login: {
    correo: 'Correo',
    contrasena: 'Contraseña',
    ingresar: 'Ingresar',
    ingresando: 'Ingresando…',
    incorrectas: 'Correo o contraseña incorrectos',
    fallo: 'No se pudo iniciar sesión. Inténtalo de nuevo.'
  },
  shell: { salir: 'Cerrar sesión' },
  inicio: { titulo: 'Dashboard' },
  noEncontrada: 'Esta página no existe'
}

export type Textos = typeof ES
```

`src/portal/i18n.ts`:

```ts
import { createWasichaiI18n, type WasichaiModule } from '@wasichai/core'
import type { i18n as I18n } from 'i18next'
import { client } from './api'
import { ES } from './textos/es'

// el espacio de nombres de los textos del portal; los de core siguen en el suyo
export const NS = 'sgspe'
// español por defecto e inglés (RNF-I18N-001); el idioma elegido se guarda en sgspe.lang, la misma clave de /admin
export const IDIOMAS = ['es', 'en']

export function ajustarI18n(i18n: I18n): void {
  i18n.addResourceBundle('es', NS, ES, true, true)
}

// el i18n de core (sus textos y los de los módulos) más los del portal
export function crearI18n(modules: readonly WasichaiModule[] = []): I18n {
  const i18n = createWasichaiI18n({ languages: IDIOMAS, storageKey: client.keys.lang, modules })
  ajustarI18n(i18n)
  return i18n
}
```

- [ ] **Paso 5: sesión, login, marco e inicio**

`src/portal/auth/RequireSession.tsx` (adaptado de caja-ui@a45b224 `src/portal/auth/RequireSession.tsx`; `safeNext` más
estricto, decisión 8 del plan):

```tsx
import { useAuth } from '@wasichai/core'
import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

// sin sesión, al login con la ruta donde se estaba. un 401 a mitad de sesión cierra la sesión de core (AuthProvider), y
// esta guarda lleva al login con la misma ruta
export function RequireSession({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />
  return children
}

// solo rutas internas después del login (security.md §3.2). //otro.sitio y /\otro.sitio salen del sitio, y un espacio o un
// carácter de control puede convertir una ruta en otra al pasar por el navegador: todo eso lleva al inicio
export function safeNext(next: string | null): string {
  return next !== null && /^\/(?![/\\])[^\\\s\u0000-\u001f\u007f]*$/.test(next) ? next : '/'
}
```

`src/portal/auth/LoginPage.tsx` (adaptado de caja-ui@a45b224 `src/portal/auth/LoginPage.tsx`, sin los borradores de caja):

```tsx
import { ApiError, useAuth, useWasichaiConfig } from '@wasichai/core'
import { Button, Card, CardBody, Input, Label } from '@wasichai/ui'
import { Mountain } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate, useSearchParams } from 'react-router'
import { NS } from '../i18n'
import { safeNext } from './RequireSession'

// el login del portal: la sesión de core. POST /api/auth/login guarda sgspe.token y sgspe.user, los mismos de /admin.
// empieza sin correo: el administrador sembrado es el login de desarrollo de /admin
export function LoginPage() {
  const { t } = useTranslation(NS)
  const { user, signIn } = useAuth()
  const { appName } = useWasichaiConfig()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={next} replace />

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await signIn(email, password)
      navigate(next, { replace: true })
    } catch (e) {
      setError(e instanceof ApiError && e.status === 401 ? t('login.incorrectas') : t('login.fallo'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4">
      <Card className="w-full max-w-sm">
        <CardBody className="space-y-6 py-8">
          <div className="flex flex-col items-center gap-2 text-center">
            <span className="flex size-12 items-center justify-center rounded-full bg-brand-soft text-brand">
              <Mountain className="size-6" />
            </span>
            <h1 className="text-xl font-semibold">{appName}</h1>
            <p className="text-sm text-ink-muted">{t('app.lema')}</p>
          </div>
          <form onSubmit={submit} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">{t('login.correo')}</Label>
              <Input id="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">{t('login.contrasena')}</Label>
              <Input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
            </div>
            {error && (
              <p role="alert" className="text-sm text-danger">
                {error}
              </p>
            )}
            <Button type="submit" className="w-full" disabled={busy}>
              {busy ? t('login.ingresando') : t('login.ingresar')}
            </Button>
          </form>
        </CardBody>
      </Card>
    </div>
  )
}
```

`src/portal/shell/AppShell.tsx` (provisional; la Tarea 16 lo reemplaza por el marco con el árbol de secciones):

```tsx
import { useAuth, useWasichaiConfig } from '@wasichai/core'
import { LogOut } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Outlet } from 'react-router'
import { NS } from '../i18n'

// el marco del portal: cabecera con la marca, la cuenta y la salida; debajo, el contenido
export function AppShell() {
  const { t } = useTranslation(NS)
  const { appName } = useWasichaiConfig()
  const { user, signOut } = useAuth()
  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center gap-4 border-b border-border bg-surface px-4">
        <span className="text-sm font-bold text-brand">{appName}</span>
        <div className="flex-1" />
        <span className="text-sm text-ink">{user?.displayName}</span>
        <button type="button" onClick={signOut} aria-label={t('shell.salir')} className="rounded p-1.5 text-ink-muted hover:bg-surface-muted hover:text-ink">
          <LogOut className="size-4" />
        </button>
      </header>
      <main id="content" className="min-h-0 flex-1 overflow-auto px-6 py-5">
        <Outlet />
      </main>
    </div>
  )
}
```

`src/portal/pages/InicioPage.tsx` (provisional; la Tarea 18 lo completa):

```tsx
import { useTranslation } from 'react-i18next'
import { NS } from '../i18n'

// el Dashboard de F1: el contenido llega en la Tarea 18
export function InicioPage() {
  const { t } = useTranslation(NS)
  return <h1 className="text-xl font-semibold text-ink">{t('inicio.titulo')}</h1>
}
```

`src/portal/PortalApp.tsx` (adaptado de caja-ui@a45b224 `src/portal/PortalApp.tsx`: sin pantallas de caja, sin
`KitDelPortal`, español e inglés):

```tsx
import { QueryClient } from '@tanstack/react-query'
import { ApiError, createRegistry, EmptyState, resolveConfig, WasichaiProviders } from '@wasichai/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createBrowserRouter, createRoutesFromElements, Route, RouterProvider } from 'react-router'
import { client } from './api'
import { LoginPage } from './auth/LoginPage'
import { RequireSession } from './auth/RequireSession'
import { crearI18n, IDIOMAS, NS } from './i18n'
import { InicioPage } from './pages/InicioPage'
import { AppShell } from './shell/AppShell'

// un 4xx no cambia por preguntar otra vez; un corte de red o un 5xx tal vez sí
const retry = (count: number, error: unknown) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2

function NoEncontrada() {
  const { t } = useTranslation(NS)
  return <EmptyState title={t('noEncontrada')} />
}

const rutas = () =>
  createRoutesFromElements(
    <>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireSession>
            <AppShell />
          </RequireSession>
        }
      >
        <Route index element={<InicioPage />} />
        <Route path="*" element={<NoEncontrada />} />
      </Route>
    </>
  )

// el portal: los proveedores de core, como /admin, así que la sesión, el tema y el idioma son los mismos. sin módulos:
// el portal dibuja sus propias pantallas
export function PortalApp() {
  const [app] = useState(() => {
    const config = resolveConfig({ apiBaseUrl: '/api', storagePrefix: 'sgspe', appName: 'SGSPE', languages: IDIOMAS })
    const registry = createRegistry([])
    const i18n = crearI18n(registry.modules)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry, refetchOnWindowFocus: false } } })
    return { config, registry, i18n, queryClient, router: createBrowserRouter(rutas()) }
  })

  return (
    <WasichaiProviders config={app.config} registry={app.registry} apiClient={client} i18n={app.i18n} queryClient={app.queryClient}>
      <RouterProvider router={app.router} />
    </WasichaiProviders>
  )
}
```

`src/main.tsx`:

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AdminApp } from './admin/AdminApp'
import { PortalApp } from './portal/PortalApp'
import './index.css'

// dos apps, un bundle: /admin es la administración de wasichai; lo demás, el portal (ADR-0024)
const admin = /^\/admin(\/|$)/.test(window.location.pathname)

createRoot(document.getElementById('root')!).render(<StrictMode>{admin ? <AdminApp /> : <PortalApp />}</StrictMode>)
```

- [ ] **Paso 6: verlas pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format && yarn test 2>&1 | tail -4 && yarn lint && yarn typecheck
```

Esperado: `Test Files  4 passed (4)`; 22 pruebas en verde (1 de vite, 3 de `/admin`, 13 de `RequireSession.test.tsx` y 5
de `login.test.tsx`); `lint` y `typecheck` limpios.

- [ ] **Paso 7: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add src/portal/api.ts src/portal/i18n.ts src/portal/textos/es.ts src/portal/PortalApp.tsx \
  src/portal/auth/LoginPage.tsx src/portal/auth/RequireSession.tsx src/portal/shell/AppShell.tsx \
  src/portal/pages/InicioPage.tsx src/test/portal.ts src/main.tsx src/portal/auth/RequireSession.test.tsx \
  src/portal/login.test.tsx
git commit -F - <<'EOF'
feat(portal): login compartido con /admin, RequireSession y safeNext

El portal usa la sesión de core bajo sgspe.*; un 401 vuelve al login y regresa a la
misma ruta. safeNext rechaza //, /\ y los caracteres de control.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 15: B4 · Temas claro y oscuro, script de arranque y CSP

**Pista:** B.

**Files:**
- Create: `csp.ts` (raíz del repositorio), `src/portal/shell/ThemeMenu.tsx` (copia de caja-ui con tres ediciones)
- Modify: `index.html`, `vite.config.ts`, `tsconfig.json`, `package.json` (scripts), `src/portal/shell/AppShell.tsx`
- Test: `src/themes.test.ts`, `src/csp.test.ts`, `src/portal/shell/ThemeMenu.test.tsx`

**Interfaces:**
- Consumes: `PortalApp`, `AppShell`, `src/test/portal.ts` (Tarea 14).
- Produces: `bootScript(html: string): string`, `scriptHash(script: string): string` y
  `contentSecurityPolicy(html: string): string` en `csp.ts`; la cabecera `Content-Security-Policy-Report-Only` en `yarn dev` y
  `yarn preview`; `yarn csp`, que imprime la política para el proxy de producción; `ThemeMenu({ className? })`.

- [ ] **Paso 1: escribir las pruebas que fallan**

`src/themes.test.ts` (adaptada de caja-ui@a45b224 `src/themes/registro.test.tsx`):

```ts
import { BUILT_IN_THEMES } from '@wasichai/core'
import { describe, expect, it } from 'vitest'
import html from '../index.html?raw'

// el script de arranque de index.html, corrido contra un tema guardado en sgspe.theme y el ajuste del sistema operativo,
// antes de que cargue el bundle
function boot(stored: string | null, systemDark = false) {
  const script = /<script>([\s\S]*?)<\/script>/.exec(html)?.[1]
  if (!script) throw new Error('index.html no tiene el script de arranque')
  const root = { dataset: {} as Record<string, string>, style: {} as Record<string, string> }
  const run = new Function('localStorage', 'matchMedia', 'document', script)
  run({ getItem: (key: string) => (key === 'sgspe.theme' ? stored : null) }, () => ({ matches: systemDark }), { documentElement: root })
  return { theme: root.dataset.theme, colorScheme: root.style.colorScheme }
}

describe('temas', () => {
  // sgspe no registra temas propios (config.themes): claro y oscuro de core, y el script no puede importarlos
  it('core trae claro y oscuro, los mismos que conoce el script de arranque', () => {
    expect(BUILT_IN_THEMES.map(({ id, colorScheme }) => [id, colorScheme])).toEqual([
      ['light', 'light'],
      ['dark', 'dark']
    ])
  })

  it.each(BUILT_IN_THEMES)('arranca $id con su esquema de color, antes del bundle', ({ id, colorScheme }) => {
    expect(boot(id)).toEqual({ theme: id, colorScheme })
  })

  it.each([null, 'system', 'portal-tributario', 'toString', '__proto__'])('arranca %s con el ajuste del sistema operativo', (stored) => {
    expect(boot(stored)).toEqual({ theme: 'light', colorScheme: 'light' })
    expect(boot(stored, true)).toEqual({ theme: 'dark', colorScheme: 'dark' })
  })
})
```

`src/csp.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { bootScript, contentSecurityPolicy, scriptHash } from '../csp'
import html from '../index.html?raw'
import config from '../vite.config'

// security.md §3.2 y ADR-0020: el script de arranque de temas es el único script en línea y la CSP lo autoriza por su hash;
// en desarrollo la manda Vite en modo de solo informe
describe('CSP', () => {
  it('autoriza el script de arranque por su hash y nada más en línea', () => {
    const policy = contentSecurityPolicy(html)
    expect(policy).toContain(`script-src 'self' ${scriptHash(bootScript(html))}`)
    expect(policy).not.toContain('unsafe-inline')
    expect(policy).toContain("frame-ancestors 'none'")
    expect(html.match(/<script>/g)).toHaveLength(1)
  })

  it('el hash cambia si cambia el script', () => {
    expect(scriptHash('a')).not.toBe(scriptHash('b'))
    expect(scriptHash('a')).toMatch(/^'sha256-[A-Za-z0-9+/]+=*'$/)
  })

  it('Vite la manda en modo de solo informe, en dev y en preview', () => {
    expect(config.server?.headers?.['Content-Security-Policy-Report-Only']).toBe(contentSecurityPolicy(html))
    expect(config.preview?.headers?.['Content-Security-Policy-Report-Only']).toBe(contentSecurityPolicy(html))
  })
})
```

`src/portal/shell/ThemeMenu.test.tsx` (adaptada de caja-ui@a45b224 `src/portal/shell/ThemeMenu.test.tsx`):

```tsx
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { mockFetch, type FetchMock, type MockRoute } from '@wasichai/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { abrirSesion, RELACIONISTA, rutasDeSesion, SIN_PERMISOS } from '../../test/portal'
import { PortalApp } from '../PortalApp'

// el menú de tema del marco: sistema, claro y oscuro. lo elegido se aplica, se guarda en sgspe.theme (lo que lee el script
// de arranque) y en las preferencias del usuario, como hace /admin

let fetch: FetchMock | null = null
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  delete document.documentElement.dataset.theme
})
afterEach(() => {
  fetch?.restore()
  fetch = null
})

function start(routes: MockRoute[] = []) {
  abrirSesion(RELACIONISTA)
  window.history.pushState({}, '', '/')
  fetch = mockFetch([...routes, { path: '/auth/me/preferences', body: { theme: 'system', locale: null } }, ...rutasDeSesion(RELACIONISTA, SIN_PERMISOS)])
  render(<PortalApp />)
}

describe('menú de tema', () => {
  it('cambia el tema desde un menú, lo guarda en sgspe.theme y en las preferencias', async () => {
    start([{ method: 'PUT', path: '/auth/me/preferences', body: { theme: 'dark', locale: null } }])
    const button = await screen.findByRole('button', { name: /^Tema: Sistema/ })
    await userEvent.click(button)
    const menu = screen.getByRole('menu', { name: 'Tema' })
    expect(
      within(menu)
        .getAllByRole('menuitemradio')
        .map((item) => item.textContent)
    ).toEqual(['Sistema', 'Claro', 'Oscuro'])

    await userEvent.click(within(menu).getByRole('menuitemradio', { name: 'Oscuro' }))
    await waitFor(() => expect(document.documentElement.dataset.theme).toBe('dark'))
    expect(document.documentElement.style.colorScheme).toBe('dark')
    expect(localStorage.getItem('sgspe.theme')).toBe('dark')
    await waitFor(() => expect(fetch!.calls.find((c) => c.method === 'PUT' && c.path === '/auth/me/preferences')?.body).toEqual({ theme: 'dark' }))
  })
})
```

- [ ] **Paso 2: verlas fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn test src/themes.test.ts src/csp.test.ts src/portal/shell 2>&1 | tail -6
```

Esperado: FAIL: `index.html no tiene el script de arranque`, `Failed to resolve import "../csp"` y el botón `Tema: Sistema`
que no aparece.

- [ ] **Paso 3: `index.html` con el script de arranque** (adaptado de caja-ui@a45b224 `index.html`: clave `sgspe.theme`,
  solo claro y oscuro)

```html
<!doctype html>
<html lang="es">
    <head>
        <meta charset="UTF-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1.0" />
        <title>SGSPE</title>
        <script>
            // antes del bundle: el tema guardado (portal y /admin comparten sgspe.*), para que un usuario en oscuro no vea un
            // destello claro. cada tema de core, id -> esquema de color: claro y oscuro (themes.test.ts los mantiene a la par).
            // system y un id desconocido toman el ajuste del sistema operativo, como resolveTheme de core. es el único
            // script en línea: la CSP lo autoriza por su hash (csp.ts)
            try {
                const schemes = { light: 'light', dark: 'dark' }
                const stored = localStorage.getItem('sgspe.theme')
                const dark = matchMedia('(prefers-color-scheme: dark)').matches
                const theme = stored && Object.hasOwn(schemes, stored) ? stored : dark ? 'dark' : 'light'
                document.documentElement.dataset.theme = theme
                document.documentElement.style.colorScheme = schemes[theme]
            } catch {}
        </script>
    </head>
    <body>
        <div id="root"></div>
        <script type="module" src="/src/main.tsx"></script>
    </body>
</html>
```

- [ ] **Paso 4: `csp.ts`** (en la raíz, junto a `vite.config.ts`: corre en Node, nunca en el navegador)

```ts
import { createHash } from 'node:crypto'

// la política de security.md §3.2 para el portal y /admin, con el hash del único script en línea: el arranque de temas
// de index.html. Vite la manda en modo de solo informe (ADR-0020); en producción la pone el proxy, que la toma de
// `yarn csp` (ADR-0026). el proveedor del mapa base entra en img-src y connect-src cuando llegue el mapa, en F2

// el texto exacto del script en línea de index.html: lo que el navegador hashea
export function bootScript(html: string): string {
  const match = /<script>([\s\S]*?)<\/script>/.exec(html)
  if (!match) throw new Error('index.html no tiene el script de arranque de temas')
  return match[1]
}

export function scriptHash(script: string): string {
  return `'sha256-${createHash('sha256').update(script, 'utf8').digest('base64')}'`
}

export function contentSecurityPolicy(html: string): string {
  return [
    "default-src 'self'",
    `script-src 'self' ${scriptHash(bootScript(html))}`,
    "style-src 'self'",
    "img-src 'self' data: blob:",
    "connect-src 'self'",
    "worker-src 'self' blob:",
    "font-src 'self'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "frame-ancestors 'none'"
  ].join('; ')
}
```

- [ ] **Paso 5: `vite.config.ts` con la cabecera** (contenido completo)

```ts
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { readFileSync } from 'node:fs'
import { defineConfig } from 'vitest/config'
import { contentSecurityPolicy } from './csp'

const api = process.env.WASICHAI_API_URL ?? 'http://localhost:8094'
// la CSP de security.md §3.2 en modo de solo informe (ADR-0020): en desarrollo la manda Vite; en producción, el proxy
const csp = { 'Content-Security-Policy-Report-Only': contentSecurityPolicy(readFileSync('index.html', 'utf8')) }

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { port: 5182, strictPort: true, proxy: { '/api': api }, headers: csp },
  preview: { port: 5182, strictPort: true, proxy: { '/api': api }, headers: csp },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
    // con carga (CI, varias suites a la vez) una pantalla entera pasa los 5 s de vitest sin nada malo: 15 s
    testTimeout: 15000,
    hookTimeout: 15000
  }
})
```

- [ ] **Paso 6: `tsconfig.json` y `package.json`**

En `tsconfig.json`, reemplazar `"include": ["src", "vite.config.ts"]` por `"include": ["src", "vite.config.ts", "csp.ts"]`.

En `package.json`, reemplazar las líneas de `lint` y `format` y agregar `csp`:

```json
        "lint": "prettier --check src index.html package.json tsconfig.json tsconfig.build.json vite.config.ts csp.ts commitlint.config.mjs",
        "format": "prettier --write src index.html package.json tsconfig.json tsconfig.build.json vite.config.ts csp.ts commitlint.config.mjs",
        "csp": "node --input-type=module -e \"import { readFileSync } from 'node:fs'; import { contentSecurityPolicy } from './csp.ts'; console.log(contentSecurityPolicy(readFileSync('index.html', 'utf8')))\"",
```

- [ ] **Paso 7: el menú de tema**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
cp /Users/jorge/IdeaProjects/caja-ui/src/portal/shell/ThemeMenu.tsx src/portal/shell/ThemeMenu.tsx
```

Tres ediciones exactas en `src/portal/shell/ThemeMenu.tsx`:

1. Reemplazar la primera línea,
   `// copiado de srtm-ui@a1df33a (src/portal/shell/ThemeMenu.tsx): sube a wasichai-ui en la fase 2 (wasichai-ui#14)`, por
   `// copiado de caja-ui@a45b224 (src/portal/shell/ThemeMenu.tsx), que lo copió de srtm-ui@a1df33a; el texto del botón y del menú sale del i18n de core`.
2. Reemplazar
   ``        aria-label={`Tema: ${current.label}. Elegir tema`}`` por ``        aria-label={`${t('theme.label')}: ${current.label}`}``
   y ``        title={error ?? `Tema: ${current.label}`}`` por ``        title={error ?? `${t('theme.label')}: ${current.label}`}``.
3. Reemplazar `          aria-label="Tema"` por `          aria-label={t('theme.label')}`.

En `src/portal/shell/AppShell.tsx`, agregar `import { ThemeMenu } from './ThemeMenu'` después de
`import { NS } from '../i18n'`, e insertar `        <ThemeMenu />` en la línea anterior a
`        <span className="text-sm text-ink">{user?.displayName}</span>`.

- [ ] **Paso 8: verlas pasar y comprobar la política**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format && yarn test 2>&1 | tail -4 && yarn lint && yarn typecheck && yarn build
yarn -s csp
```

Esperado: `Test Files  7 passed (7)`; los demás comandos limpios; `yarn -s csp` imprime una línea que empieza por
`default-src 'self'; script-src 'self' 'sha256-` y termina en `frame-ancestors 'none'`. Vite avisa que `./csp` se importa sin
extensión en `vite.config.ts`: es un aviso sobre su cargador nativo futuro, no un error.

- [ ] **Paso 9: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add index.html csp.ts vite.config.ts tsconfig.json package.json src/portal/shell/ThemeMenu.tsx \
  src/portal/shell/AppShell.tsx src/themes.test.ts src/csp.test.ts src/portal/shell/ThemeMenu.test.tsx
git commit -F - <<'EOF'
feat(temas): claro y oscuro con script de arranque autorizado por hash en la CSP

El script de index.html es el único en línea; csp.ts arma la política de security.md
§3.2 y Vite la manda en modo de solo informe. yarn csp la imprime para el proxy.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 16: B5 · Marco y árbol de navegación de las 13 secciones

**Pista:** B.

**Files:**
- Create: `src/portal/shell/navTree.ts`, `src/portal/pantallas.ts`, `src/portal/shell/GuardaDeSeccion.tsx`
- Modify (contenido completo): `src/portal/shell/AppShell.tsx`, `src/portal/PortalApp.tsx`, `src/portal/textos/es.ts`
- Test: `src/portal/shell/Navigation.test.tsx`

**Interfaces:**
- Consumes: `PortalApp`, `ThemeMenu`, `src/test/portal.ts` y `ES` (Tareas 14 y 15).
- Produces, en `src/portal/shell/navTree.ts`:
  - `type ClaveDeSeccion = 'dashboard' | 'projects' | 'territory' | 'stakeholders' | 'engagement' | 'commitments' |
    'attention' | 'risks' | 'social-programs' | 'documents' | 'tasks' | 'reports' | 'admin'`;
  - `type Fase = 'F1' | … | 'F8'`; `interface Par { objeto: string; accion: Accion }`;
    `interface SeccionNav extends NavTreeLeaf { clave; fases: Fase[]; seOfreceCon?: Par[][]; soloAdmin?: boolean }`;
  - `NAV_TREE: SeccionNav[]` (las 13 secciones de `architecture.md` §9.2, en su orden);
  - `interface Oferta { isAdmin; can(objeto, accion); conPantalla(clave) }`;
    `type Estado = 'disponible' | 'sin-acceso' | 'solo-admin' | 'por-llegar'`;
  - `conPantalla(clave)`, `estadoDe(seccion, oferta): Estado`, `arbolPara(secciones, oferta): SeccionNav[]`,
    `useOferta(): Oferta`, `useArbol(): SeccionNav[]`.
- Produces: `PANTALLAS: Partial<Record<ClaveDeSeccion, ComponentType>>` (vacío en F1); `GuardaDeSeccion({ seccion, children })`;
  la navegación `Secciones` (`<nav aria-label>`) del marco; las claves `shell.secciones`, `shell.leyendoPermisos`,
  `shell.sinAcceso` y `secciones.<clave>` de `ES`.

- [ ] **Paso 1: escribir la prueba que falla, `src/portal/shell/Navigation.test.tsx`**

```tsx
import { render, screen, waitFor, within } from '@testing-library/react'
import type { AuthUser } from '@wasichai/core'
import { mockFetch, type FetchMock, type MockRoute } from '@wasichai/testing'
import type { ComponentType } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { abrirSesion, ADMIN_PERMISOS, ADMINISTRACION, AUDITORA, RELACIONISTA, rutasDeSesion, SIN_PERMISOS, yo } from '../../test/portal'
import { PortalApp } from '../PortalApp'
import { ES } from '../textos/es'
import { arbolPara, estadoDe, NAV_TREE, type ClaveDeSeccion } from './navTree'

// RF-IAM-002 y RNF-USA-001: las 13 secciones en su orden y su fase; una sección se dibuja solo con pantalla y con los
// permisos de GET /api/auth/me/permissions; Administración solo para quien administra. en F1 no hay pantallas registradas:
// cada prueba registra la suya
const registradas = vi.hoisted(() => ({}) as Record<string, ComponentType>)
vi.mock('../pantallas', () => ({ PANTALLAS: registradas }))

function Prueba() {
  return <h2>Pantalla de prueba</h2>
}

let fetch: FetchMock | null = null
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
  for (const clave of Object.keys(registradas)) delete registradas[clave]
})
afterEach(() => {
  fetch?.restore()
  fetch = null
})

function start(user: AuthUser, rutas: MockRoute[], path = '/') {
  abrirSesion(user)
  window.history.pushState({}, '', path)
  fetch = mockFetch(rutas)
  render(<PortalApp />)
}

const lateral = () => screen.getByRole('navigation', { name: 'Secciones' })
const enlaces = () =>
  within(lateral())
    .getAllByRole('link')
    .map((link) => link.textContent)

// una cuenta que puede exactamente estos pares "objeto ACCION", con todas las pantallas registradas
const cuenta = (pares: string[], isAdmin = false) => ({
  isAdmin,
  can: (objeto: string, accion: string) => isAdmin || pares.includes(`${objeto} ${accion}`),
  conPantalla: () => true
})
const soloDashboard = (clave: ClaveDeSeccion) => clave === 'dashboard'

describe('NAV_TREE', () => {
  it('declara las 13 secciones del prompt en su orden, con su ruta y sus fases', () => {
    expect(NAV_TREE.map((s) => [s.label, s.to, s.fases.join(', ')])).toEqual([
      ['Dashboard', '/', 'F1, F8'],
      ['Proyectos', '/projects', 'F2, F7'],
      ['Territorio', '/territory', 'F2, F6'],
      ['Grupos de interés', '/stakeholders', 'F2'],
      ['Relacionamiento', '/engagement', 'F2'],
      ['Compromisos', '/commitments', 'F3'],
      ['Quejas, consultas y solicitudes', '/attention', 'F4'],
      ['Riesgos', '/risks', 'F4'],
      ['Programas sociales', '/social-programs', 'F5'],
      ['Documentos', '/documents', 'F3'],
      ['Tareas', '/tasks', 'F3'],
      ['Reportes', '/reports', 'F2, F8'],
      ['Administración', '/admin', 'F1']
    ])
  })

  it('las etiquetas del árbol son las de los textos en español', () => {
    for (const seccion of NAV_TREE) expect(ES.secciones[seccion.clave]).toBe(seccion.label)
  })

  it('en F1, sin pantallas, el admin ve Dashboard y Administración y los demás solo Dashboard', () => {
    expect(arbolPara(NAV_TREE, { ...cuenta([], true), conPantalla: soloDashboard }).map((s) => s.label)).toEqual(['Dashboard', 'Administración'])
    expect(arbolPara(NAV_TREE, { ...cuenta(['project READ']), conPantalla: soloDashboard }).map((s) => s.label)).toEqual(['Dashboard'])
  })

  it.each([
    [[], ['Dashboard', 'Reportes']],
    [['project READ'], ['Dashboard', 'Proyectos', 'Reportes']],
    [['inquiry READ'], ['Dashboard', 'Quejas, consultas y solicitudes', 'Reportes']],
    [
      ['early_warning READ', 'task READ'],
      ['Dashboard', 'Riesgos', 'Tareas', 'Reportes']
    ],
    [
      ['project CREATE', 'stakeholder UPDATE'],
      ['Dashboard', 'Reportes']
    ]
  ])('con todas las pantallas, la cuenta %j ve %j', (pares, secciones) => {
    expect(arbolPara(NAV_TREE, cuenta(pares)).map((s) => s.label)).toEqual(secciones)
  })

  it('estadoDe dice por qué una sección no se dibuja', () => {
    const f1 = { ...cuenta([]), conPantalla: soloDashboard }
    const de = (clave: ClaveDeSeccion) =>
      estadoDe(
        NAV_TREE.find((s) => s.clave === clave)!,
        f1
      )
    expect([de('dashboard'), de('projects'), de('admin')]).toEqual(['disponible', 'por-llegar', 'solo-admin'])
    expect(estadoDe(NAV_TREE[1], cuenta([]))).toBe('sin-acceso')
  })
})

describe('navegación del portal', () => {
  it('el admin ve Dashboard y Administración, que lleva a /admin', async () => {
    start(ADMINISTRACION, rutasDeSesion(ADMINISTRACION, ADMIN_PERMISOS))
    await screen.findByRole('navigation', { name: 'Secciones' })
    await waitFor(() => expect(enlaces()).toEqual(['Dashboard', 'Administración']))
    expect(within(lateral()).getByRole('link', { name: 'Administración' })).toHaveAttribute('href', '/admin')
  })

  it('AUDITOR no ve Administración', async () => {
    start(AUDITORA, rutasDeSesion(AUDITORA, { admin: false, objects: { project: ['READ'] } }))
    await screen.findByRole('navigation', { name: 'Secciones' })
    expect(enlaces()).toEqual(['Dashboard'])
  })

  it('si los permisos no se pueden leer, nadie ve Administración', async () => {
    start(ADMINISTRACION, [
      { path: '/auth/me', body: yo(ADMINISTRACION) },
      { path: '/auth/me/permissions', status: 403, body: { title: 'Forbidden', status: 403 } }
    ])
    await screen.findByRole('navigation', { name: 'Secciones' })
    expect(enlaces()).toEqual(['Dashboard'])
  })

  it('una sección con pantalla se ofrece con su permiso y abre su pantalla', async () => {
    registradas.projects = Prueba
    start(RELACIONISTA, rutasDeSesion(RELACIONISTA, { admin: false, objects: { project: ['READ'] } }), '/projects')
    expect(await screen.findByRole('heading', { name: 'Pantalla de prueba' })).toBeInTheDocument()
    expect(enlaces()).toEqual(['Dashboard', 'Proyectos'])
  })

  it('sin el permiso, la URL de la sección dice que la cuenta no puede abrirla', async () => {
    registradas.projects = Prueba
    start(RELACIONISTA, rutasDeSesion(RELACIONISTA, SIN_PERMISOS), '/projects')
    expect(await screen.findByText('Su cuenta no puede abrir «Proyectos».')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Pantalla de prueba' })).not.toBeInTheDocument()
    expect(enlaces()).toEqual(['Dashboard'])
  })
})
```

- [ ] **Paso 2: verla fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn test src/portal/shell/Navigation 2>&1 | tail -5
```

Esperado: FAIL con `Failed to resolve import "./navTree"` y `"../pantallas"`.

- [ ] **Paso 3: `src/portal/shell/navTree.ts`** (adaptado de caja-ui@a45b224 `src/portal/shell/navTree.ts`: sin grupos en
  F1, con la fase de cada sección y su estado)

```ts
import { useAuth, type NavTreeLeaf } from '@wasichai/core'
import { useMemo } from 'react'
import { PANTALLAS } from '../pantallas'

// el árbol del portal: las 13 secciones del prompt en su orden, con sus fases (architecture.md §9.2). una sección se dibuja
// solo si tiene pantalla y la cuenta tiene los permisos que pide (RF-IAM-002, RNF-USA-001); los permisos son los de
// GET /api/auth/me/permissions mientras falte UPS-06. Administración solo para quien administra (ADR-0024). sin nombres de
// rol: decide isAdmin de core y los pares objeto/acción (RQ-SEC-005)

export type ClaveDeSeccion =
  | 'dashboard'
  | 'projects'
  | 'territory'
  | 'stakeholders'
  | 'engagement'
  | 'commitments'
  | 'attention'
  | 'risks'
  | 'social-programs'
  | 'documents'
  | 'tasks'
  | 'reports'
  | 'admin'

export type Fase = 'F1' | 'F2' | 'F3' | 'F4' | 'F5' | 'F6' | 'F7' | 'F8'

// lo que una cuenta puede hacer sobre un objeto del modelo, como lo lista GET /api/auth/me/permissions (WAS-ADR-0020)
export type Accion = 'READ' | 'CREATE' | 'UPDATE' | 'DELETE'

export interface Par {
  objeto: string
  accion: Accion
}

export interface SeccionNav extends NavTreeLeaf {
  clave: ClaveDeSeccion
  // las fases en que llega o crece la sección; la primera es la de llegada
  fases: Fase[]
  // cuándo se ofrece: alguna de estas alternativas, cada una una lista de pares que la cuenta debe tener todos
  seOfreceCon?: Par[][]
  // solo para quien administra: la sección Administración
  soloAdmin?: boolean
}

const lee = (objeto: string): Par => ({ objeto, accion: 'READ' })

// los objetos son los del catálogo canónico (domain-model.md §6): el que lee la pantalla principal de cada sección
export const NAV_TREE: SeccionNav[] = [
  { clave: 'dashboard', label: 'Dashboard', to: '/', fases: ['F1', 'F8'] },
  { clave: 'projects', label: 'Proyectos', to: '/projects', fases: ['F2', 'F7'], seOfreceCon: [[lee('project')]] },
  { clave: 'territory', label: 'Territorio', to: '/territory', fases: ['F2', 'F6'], seOfreceCon: [[lee('locality')]] },
  { clave: 'stakeholders', label: 'Grupos de interés', to: '/stakeholders', fases: ['F2'], seOfreceCon: [[lee('stakeholder')]] },
  { clave: 'engagement', label: 'Relacionamiento', to: '/engagement', fases: ['F2'], seOfreceCon: [[lee('engagement')]] },
  { clave: 'commitments', label: 'Compromisos', to: '/commitments', fases: ['F3'], seOfreceCon: [[lee('commitment')]] },
  {
    clave: 'attention',
    label: 'Quejas, consultas y solicitudes',
    to: '/attention',
    fases: ['F4'],
    seOfreceCon: [[lee('grievance')], [lee('inquiry')], [lee('stakeholder_request')]]
  },
  { clave: 'risks', label: 'Riesgos', to: '/risks', fases: ['F4'], seOfreceCon: [[lee('social_risk')], [lee('early_warning')], [lee('incident')]] },
  {
    clave: 'social-programs',
    label: 'Programas sociales',
    to: '/social-programs',
    fases: ['F5'],
    seOfreceCon: [[lee('social_program')], [lee('social_contribution')]]
  },
  { clave: 'documents', label: 'Documentos', to: '/documents', fases: ['F3'], seOfreceCon: [[lee('evidence')]] },
  { clave: 'tasks', label: 'Tareas', to: '/tasks', fases: ['F3'], seOfreceCon: [[lee('task')]] },
  { clave: 'reports', label: 'Reportes', to: '/reports', fases: ['F2', 'F8'] },
  { clave: 'admin', label: 'Administración', to: '/admin', external: true, soloAdmin: true, fases: ['F1'] }
]

// lo que el árbol pregunta de la cuenta (useAuth de core: can es siempre true para el admin) y de las pantallas
export interface Oferta {
  isAdmin: boolean
  can: (objeto: string, accion: string) => boolean
  conPantalla: (clave: ClaveDeSeccion) => boolean
}

export type Estado = 'disponible' | 'sin-acceso' | 'solo-admin' | 'por-llegar'

const seOfrece = (seccion: SeccionNav, can: Oferta['can']) =>
  !seccion.seOfreceCon || seccion.seOfreceCon.some((alternativa) => alternativa.every(({ objeto, accion }) => can(objeto, accion)))

// el Dashboard es la ruta índice del portal y Administración un enlace a /admin: existen sin registrarse en PANTALLAS
export const conPantalla = (clave: ClaveDeSeccion) => clave === 'dashboard' || PANTALLAS[clave] !== undefined

// por qué una sección se dibuja o no para esta cuenta
export function estadoDe(seccion: SeccionNav, oferta: Oferta): Estado {
  if (seccion.soloAdmin && !oferta.isAdmin) return 'solo-admin'
  if (!seccion.external && !oferta.conPantalla(seccion.clave)) return 'por-llegar'
  return seOfrece(seccion, oferta.can) ? 'disponible' : 'sin-acceso'
}

// las secciones que se dibujan para esta cuenta, en su orden
export function arbolPara(secciones: SeccionNav[], oferta: Oferta): SeccionNav[] {
  return secciones.filter((seccion) => estadoDe(seccion, oferta) === 'disponible')
}

// la oferta de la cuenta que inició sesión: mientras sus permisos no se leen, o si no se pueden leer, nada pide permisos
// pasa, y Administración no se dibuja
export function useOferta(): Oferta {
  const { isAdmin, can } = useAuth()
  return useMemo(() => ({ isAdmin, can, conPantalla }), [isAdmin, can])
}

export function useArbol(): SeccionNav[] {
  const oferta = useOferta()
  return useMemo(() => arbolPara(NAV_TREE, oferta), [oferta])
}
```

- [ ] **Paso 4: `src/portal/pantallas.ts` y `src/portal/shell/GuardaDeSeccion.tsx`**

```ts
import type { ComponentType } from 'react'
import type { ClaveDeSeccion } from './shell/navTree'

// la pantalla de cada sección, por su clave (como PANTALLAS de caja-ui@a45b224). una sección sin pantalla no se dibuja:
// una entrada de menú que no lleva a ninguna parte es un defecto. cada fase registra aquí las suyas; F1 no tiene ninguna,
// porque el Dashboard es la ruta índice y Administración es /admin
export const PANTALLAS: Partial<Record<ClaveDeSeccion, ComponentType>> = {}
```

```tsx
import { useAuth } from '@wasichai/core'
import { Alert } from '@wasichai/ui'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { NS } from '../i18n'
import { estadoDe, useOferta, type SeccionNav } from './navTree'

// la pantalla de una sección se guarda con lo mismo que el árbol (como GuardaDeHoja de caja-ui@a45b224): quien llega por la
// URL sin los permisos lee que su cuenta no puede abrirla, en lugar de una pantalla llena de 403
export function GuardaDeSeccion({ seccion, children }: { seccion: SeccionNav; children: ReactNode }) {
  const { t } = useTranslation(NS)
  const { permissions, permissionsError } = useAuth()
  const oferta = useOferta()
  if (!permissions && !permissionsError) return <p className="text-sm text-ink-muted">{t('shell.leyendoPermisos')}</p>
  if (estadoDe(seccion, oferta) === 'disponible') return children
  return <Alert tone="warning">{t('shell.sinAcceso', { seccion: t(`secciones.${seccion.clave}`) })}</Alert>
}
```

- [ ] **Paso 5: `src/portal/textos/es.ts`** (contenido completo)

```ts
// los textos del portal en español, el idioma por defecto (RNF-I18N-001). en.ts tendrá las mismas claves (Tarea 17)
export const ES = {
  app: { lema: 'Gestión social de proyectos extractivos' },
  login: {
    correo: 'Correo',
    contrasena: 'Contraseña',
    ingresar: 'Ingresar',
    ingresando: 'Ingresando…',
    incorrectas: 'Correo o contraseña incorrectos',
    fallo: 'No se pudo iniciar sesión. Inténtalo de nuevo.'
  },
  shell: {
    salir: 'Cerrar sesión',
    secciones: 'Secciones',
    leyendoPermisos: 'Leyendo los permisos de su cuenta…',
    sinAcceso: 'Su cuenta no puede abrir «{{seccion}}».'
  },
  // las 13 secciones de architecture.md §9.2, con la clave de NAV_TREE (Navigation.test.tsx las mantiene iguales)
  secciones: {
    dashboard: 'Dashboard',
    projects: 'Proyectos',
    territory: 'Territorio',
    stakeholders: 'Grupos de interés',
    engagement: 'Relacionamiento',
    commitments: 'Compromisos',
    attention: 'Quejas, consultas y solicitudes',
    risks: 'Riesgos',
    'social-programs': 'Programas sociales',
    documents: 'Documentos',
    tasks: 'Tareas',
    reports: 'Reportes',
    admin: 'Administración'
  },
  inicio: { titulo: 'Dashboard' },
  noEncontrada: 'Esta página no existe'
}

export type Textos = typeof ES
```

- [ ] **Paso 6: `src/portal/shell/AppShell.tsx`** (contenido completo; adaptado del marco clásico de caja-ui@a45b224
  `src/portal/shell/AppShell.tsx`)

```tsx
import { useAuth, useWasichaiConfig } from '@wasichai/core'
import { cn } from '@wasichai/ui'
import { ExternalLink, LayoutDashboard, LogOut, Settings, type LucideIcon } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { NavLink, Outlet } from 'react-router'
import { NS } from '../i18n'
import { useArbol, type ClaveDeSeccion } from './navTree'
import { ThemeMenu } from './ThemeMenu'

const ICONOS: Partial<Record<ClaveDeSeccion, LucideIcon>> = { dashboard: LayoutDashboard, admin: Settings }
const ENLACE = 'flex items-center gap-2.5 rounded-md px-3 py-2 text-sm'

// el marco del portal: cabecera con la marca, el tema, la cuenta y la salida; a la izquierda, las secciones que se le
// ofrecen a la cuenta (useArbol); a la derecha, el contenido. Administración es un enlace a /admin, fuera del portal
export function AppShell() {
  const { t } = useTranslation(NS)
  const { appName } = useWasichaiConfig()
  const { user, signOut } = useAuth()
  const secciones = useArbol()
  return (
    <div className="flex h-full flex-col">
      <header className="flex h-14 shrink-0 items-center gap-4 border-b border-border bg-surface px-4">
        <span className="text-sm font-bold text-brand">{appName}</span>
        <span className="hidden truncate text-sm text-ink-muted lg:inline">{t('app.lema')}</span>
        <div className="flex-1" />
        <ThemeMenu />
        <span className="hidden max-w-48 truncate text-sm text-ink sm:inline" title={user?.email}>
          {user?.displayName}
        </span>
        <button
          type="button"
          onClick={signOut}
          aria-label={t('shell.salir')}
          title={t('shell.salir')}
          className="rounded p-1.5 text-ink-muted hover:bg-surface-muted hover:text-ink"
        >
          <LogOut className="size-4" />
        </button>
      </header>
      <div className="flex min-h-0 flex-1">
        <nav aria-label={t('shell.secciones')} className="w-60 shrink-0 bg-shell p-3">
          <ul className="space-y-1">
            {secciones.map((seccion) => {
              const Icono = ICONOS[seccion.clave] ?? LayoutDashboard
              const rotulo = t(`secciones.${seccion.clave}`)
              return (
                <li key={seccion.clave}>
                  {seccion.external ? (
                    <a href={seccion.to} className={cn(ENLACE, 'text-shell-muted hover:bg-shell-ink/5 hover:text-shell-ink')}>
                      <Icono aria-hidden className="size-4" />
                      {rotulo}
                      <ExternalLink aria-hidden className="ml-auto size-3" />
                    </a>
                  ) : (
                    <NavLink
                      to={seccion.to}
                      end
                      className={({ isActive }) =>
                        cn(ENLACE, isActive ? 'bg-shell-ink/10 text-shell-ink' : 'text-shell-muted hover:bg-shell-ink/5 hover:text-shell-ink')
                      }
                    >
                      <Icono aria-hidden className="size-4" />
                      {rotulo}
                    </NavLink>
                  )}
                </li>
              )
            })}
          </ul>
        </nav>
        <main id="content" className="min-h-0 min-w-0 flex-1 overflow-auto px-6 py-5">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
```

- [ ] **Paso 7: `src/portal/PortalApp.tsx`** (contenido completo: rutas de las secciones con pantalla)

```tsx
import { QueryClient } from '@tanstack/react-query'
import { ApiError, createRegistry, EmptyState, resolveConfig, WasichaiProviders } from '@wasichai/core'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { createBrowserRouter, createRoutesFromElements, Route, RouterProvider } from 'react-router'
import { client } from './api'
import { LoginPage } from './auth/LoginPage'
import { RequireSession } from './auth/RequireSession'
import { crearI18n, IDIOMAS, NS } from './i18n'
import { InicioPage } from './pages/InicioPage'
import { PANTALLAS } from './pantallas'
import { AppShell } from './shell/AppShell'
import { GuardaDeSeccion } from './shell/GuardaDeSeccion'
import { NAV_TREE } from './shell/navTree'

// un 4xx no cambia por preguntar otra vez; un corte de red o un 5xx tal vez sí
const retry = (count: number, error: unknown) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2

function NoEncontrada() {
  const { t } = useTranslation(NS)
  return <EmptyState title={t('noEncontrada')} />
}

// el Dashboard es la ruta índice; cada otra sección tiene ruta solo con su pantalla (PANTALLAS), y la pantalla se guarda
// con los mismos permisos que el árbol (GuardaDeSeccion)
const rutas = () =>
  createRoutesFromElements(
    <>
      <Route path="/login" element={<LoginPage />} />
      <Route
        element={
          <RequireSession>
            <AppShell />
          </RequireSession>
        }
      >
        <Route index element={<InicioPage />} />
        {NAV_TREE.flatMap((seccion) => {
          const Pantalla = PANTALLAS[seccion.clave]
          return Pantalla
            ? [
                <Route
                  key={seccion.clave}
                  path={seccion.to}
                  element={
                    <GuardaDeSeccion seccion={seccion}>
                      <Pantalla />
                    </GuardaDeSeccion>
                  }
                />
              ]
            : []
        })}
        <Route path="*" element={<NoEncontrada />} />
      </Route>
    </>
  )

// el portal: los proveedores de core, como /admin, así que la sesión, el tema y el idioma son los mismos. sin módulos:
// el portal dibuja sus propias pantallas
export function PortalApp() {
  const [app] = useState(() => {
    const config = resolveConfig({ apiBaseUrl: '/api', storagePrefix: 'sgspe', appName: 'SGSPE', languages: IDIOMAS })
    const registry = createRegistry([])
    const i18n = crearI18n(registry.modules)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry, refetchOnWindowFocus: false } } })
    return { config, registry, i18n, queryClient, router: createBrowserRouter(rutas()) }
  })

  return (
    <WasichaiProviders config={app.config} registry={app.registry} apiClient={client} i18n={app.i18n} queryClient={app.queryClient}>
      <RouterProvider router={app.router} />
    </WasichaiProviders>
  )
}
```

- [ ] **Paso 8: verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format && yarn test 2>&1 | tail -4 && yarn lint && yarn typecheck
```

Esperado: `Test Files  8 passed (8)`; `Navigation.test.tsx` con 14 pruebas en verde; `lint` y `typecheck` limpios.

- [ ] **Paso 9: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add src/portal/shell/navTree.ts src/portal/pantallas.ts src/portal/shell/GuardaDeSeccion.tsx \
  src/portal/shell/AppShell.tsx src/portal/PortalApp.tsx src/portal/textos/es.ts src/portal/shell/Navigation.test.tsx
git commit -F - <<'EOF'
feat(portal): las 13 secciones del prompt con su fase, por permisos

Una sección se dibuja solo con pantalla y con los permisos de /api/auth/me/permissions;
Administración solo para quien administra. En F1 se dibujan Dashboard y Administración.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 17: B6 · i18n: inglés, números y fechas es-PE

**Pista:** B.

**Files:**
- Create: `src/portal/textos/en.ts`, `src/portal/shell/LanguageMenu.tsx`
- Modify: `src/portal/i18n.ts` (contenido completo), `src/portal/textos/es.ts` (una clave), `src/portal/shell/AppShell.tsx`
- Test: `src/portal/I18n.test.tsx`

**Interfaces:**
- Consumes: `ES`, `Textos`, `crearI18n`, `NS`, `AppShell` (Tareas 14 a 16).
- Produces: `EN: Textos`; `numeroPe(valor: number): string` y `fechaPe(valor: Date): string` en `src/portal/i18n.ts`; los
  formateadores `number` y `datetime` de i18next en es-PE para los dos idiomas; `LanguageMenu` (un `select` con nombre
  accesible `Idioma`/`Language`) que guarda la elección en `sgspe.lang`; la clave `shell.idioma`.

- [ ] **Paso 1: escribir la prueba que falla, `src/portal/I18n.test.tsx`**

```tsx
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { mockFetch, type FetchMock } from '@wasichai/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { abrirSesion, RELACIONISTA, rutasDeSesion, SIN_PERMISOS } from '../test/portal'
import { crearI18n, fechaPe, NS, numeroPe } from './i18n'
import { PortalApp } from './PortalApp'
import { EN } from './textos/en'
import { ES } from './textos/es'

// RNF-I18N-001: español por defecto e inglés, con números y fechas es-PE en los dos; las etiquetas de la metadata de wasichai
// existen solo en español (CON-051)

let fetch: FetchMock | null = null
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})
afterEach(() => {
  fetch?.restore()
  fetch = null
})

// las claves de un objeto de textos, aplanadas: "shell.salir"
function claves(textos: object, prefijo = ''): string[] {
  return Object.entries(textos).flatMap(([clave, valor]) =>
    typeof valor === 'string' ? [`${prefijo}${clave}`] : claves(valor as object, `${prefijo}${clave}.`)
  )
}

function start() {
  abrirSesion(RELACIONISTA)
  window.history.pushState({}, '', '/')
  fetch = mockFetch(rutasDeSesion(RELACIONISTA, SIN_PERMISOS))
  render(<PortalApp />)
}

describe('i18n del portal', () => {
  it('en.ts tiene exactamente las claves de es.ts', () => {
    expect(claves(EN).sort()).toEqual(claves(ES).sort())
  })

  it('el español es el idioma por defecto', async () => {
    start()
    expect(await screen.findByRole('button', { name: 'Cerrar sesión' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Idioma' })).toHaveValue('es')
  })

  it('con sgspe.lang en inglés, el login también habla inglés', async () => {
    localStorage.setItem('sgspe.lang', 'en')
    window.history.pushState({}, '', '/login')
    fetch = mockFetch([])
    render(<PortalApp />)
    expect(await screen.findByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('elegir inglés cambia los textos y lo guarda en sgspe.lang, la clave de /admin', async () => {
    start()
    await userEvent.selectOptions(await screen.findByRole('combobox', { name: 'Idioma' }), 'en')
    expect(await screen.findByRole('button', { name: 'Sign out' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Sections' })).toBeInTheDocument()
    expect(localStorage.getItem('sgspe.lang')).toBe('en')
  })

  it('los números y las fechas son es-PE en los dos idiomas', () => {
    const i18n = crearI18n()
    const cuando = new Date('2026-10-07T15:00:00Z')
    for (const idioma of ['es', 'en']) {
      i18n.addResource(idioma, NS, 'prueba', '{{n, number}} · {{d, datetime}}')
      expect(i18n.t('prueba', { ns: NS, lng: idioma, n: 12345.5, d: cuando })).toBe(`12,345.5 · ${fechaPe(cuando)}`)
    }
    expect(numeroPe(1234567.25)).toBe('1,234,567.25')
    expect(fechaPe(cuando)).toBe(new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeZone: 'America/Lima' }).format(cuando))
  })
})
```

- [ ] **Paso 2: verla fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn test src/portal/I18n 2>&1 | tail -5
```

Esperado: FAIL con `Failed to resolve import "./textos/en"`.

- [ ] **Paso 3: la clave del idioma en `src/portal/textos/es.ts`**

Reemplazar `    sinAcceso: 'Su cuenta no puede abrir «{{seccion}}».'` por:

```ts
    sinAcceso: 'Su cuenta no puede abrir «{{seccion}}».',
    idioma: 'Idioma'
```

- [ ] **Paso 4: `src/portal/textos/en.ts`**

```ts
import type { Textos } from './es'

// los mismos textos en inglés. Textos obliga a tener cada clave de es.ts (typecheck) y I18n.test.tsx lo comprueba al correr
export const EN: Textos = {
  app: { lema: 'Social management for extractive projects' },
  login: {
    correo: 'Email',
    contrasena: 'Password',
    ingresar: 'Sign in',
    ingresando: 'Signing in…',
    incorrectas: 'Wrong email or password',
    fallo: 'Could not sign in. Please try again.'
  },
  shell: {
    salir: 'Sign out',
    secciones: 'Sections',
    leyendoPermisos: 'Reading your account permissions…',
    sinAcceso: 'Your account cannot open “{{seccion}}”.',
    idioma: 'Language'
  },
  secciones: {
    dashboard: 'Dashboard',
    projects: 'Projects',
    territory: 'Territory',
    stakeholders: 'Stakeholders',
    engagement: 'Engagement',
    commitments: 'Commitments',
    attention: 'Grievances, inquiries and requests',
    risks: 'Risks',
    'social-programs': 'Social programs',
    documents: 'Documents',
    tasks: 'Tasks',
    reports: 'Reports',
    admin: 'Administration'
  },
  inicio: { titulo: 'Dashboard' },
  noEncontrada: 'This page does not exist'
}
```

- [ ] **Paso 5: `src/portal/i18n.ts`** (contenido completo)

```ts
import { createWasichaiI18n, type WasichaiModule } from '@wasichai/core'
import type { i18n as I18n } from 'i18next'
import { client } from './api'
import { EN } from './textos/en'
import { ES } from './textos/es'

// el espacio de nombres de los textos del portal; los de core siguen en el suyo
export const NS = 'sgspe'
// español por defecto e inglés (RNF-I18N-001); el idioma elegido se guarda en sgspe.lang, la misma clave de /admin
export const IDIOMAS = ['es', 'en']

// números y fechas es-PE en los dos idiomas: 12,345.5, y la fecha en la hora de Lima
export const numeroPe = (valor: number): string => new Intl.NumberFormat('es-PE').format(valor)
export const fechaPe = (valor: Date): string => new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeZone: 'America/Lima' }).format(valor)

// los textos del portal y los formateadores es-PE sobre los de i18next (como ajustarI18n de caja-ui@a45b224,
// src/portal/i18n.ts)
export function ajustarI18n(i18n: I18n): void {
  i18n.addResourceBundle('es', NS, ES, true, true)
  i18n.addResourceBundle('en', NS, EN, true, true)
  i18n.services.formatter?.add('number', numeroPe)
  i18n.services.formatter?.add('datetime', fechaPe)
}

// el i18n de core (sus textos y los de los módulos) más los del portal
export function crearI18n(modules: readonly WasichaiModule[] = []): I18n {
  const i18n = createWasichaiI18n({ languages: IDIOMAS, storageKey: client.keys.lang, modules })
  ajustarI18n(i18n)
  return i18n
}
```

- [ ] **Paso 6: `src/portal/shell/LanguageMenu.tsx` y el marco**

```tsx
import { changeLanguage } from '@wasichai/core'
import { useTranslation } from 'react-i18next'
import { client } from '../api'
import { NS } from '../i18n'

// español o inglés. changeLanguage de core guarda la elección en sgspe.lang, la clave de /admin: el idioma se comparte
export function LanguageMenu() {
  const { t, i18n } = useTranslation(NS)
  return (
    <select
      aria-label={t('shell.idioma')}
      value={i18n.resolvedLanguage ?? 'es'}
      onChange={(event) => void changeLanguage(i18n, client.keys.lang, event.target.value)}
      className="rounded border border-border bg-surface px-1.5 py-1 text-sm text-ink"
    >
      <option value="es">Español</option>
      <option value="en">English</option>
    </select>
  )
}
```

En `src/portal/shell/AppShell.tsx`, agregar `import { LanguageMenu } from './LanguageMenu'` antes de
`import { useArbol, type ClaveDeSeccion } from './navTree'`, y reemplazar `        <ThemeMenu />` por:

```tsx
        <LanguageMenu />
        <ThemeMenu />
```

- [ ] **Paso 7: verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format && yarn test 2>&1 | tail -4 && yarn lint && yarn typecheck
```

Esperado: `Test Files  9 passed (9)`; `I18n.test.tsx` con 5 pruebas en verde; `lint` y `typecheck` limpios.

- [ ] **Paso 8: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add src/portal/textos/en.ts src/portal/textos/es.ts src/portal/i18n.ts src/portal/shell/LanguageMenu.tsx \
  src/portal/shell/AppShell.tsx src/portal/I18n.test.tsx
git commit -F - <<'EOF'
feat(i18n): español por defecto e inglés, con números y fechas es-PE

en.ts tiene las mismas claves que es.ts; el idioma se guarda en sgspe.lang y se comparte
con /admin. Los formateadores number y datetime usan es-PE en los dos idiomas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 18: B7 · Dashboard de F1

**Pista:** B.

**Files:**
- Create: `src/portal/queries.ts`
- Modify (contenido completo): `src/portal/pages/InicioPage.tsx`
- Modify: `src/portal/textos/es.ts` y `src/portal/textos/en.ts` (clave `inicio`)
- Test: `src/portal/pages/Inicio.test.tsx`

**Interfaces:**
- Consumes: `NAV_TREE`, `estadoDe`, `useOferta`, `Estado` (Tarea 16); `NS` y los formateadores (Tarea 17).
- Produces: `useCuenta()` (`GET /api/auth/me`) y `useOrganizacion()` (`GET /api/organizations/current`) en
  `src/portal/queries.ts`, con `interface CuentaDeWasichai { userId; organizationId; email; roles: string[] }` e
  `interface Organizacion { id; name; slug }`; el Dashboard con la cuenta, la organización, los roles y la tabla de módulos
  por fase.

- [ ] **Paso 1: escribir la prueba que falla, `src/portal/pages/Inicio.test.tsx`**

```tsx
import { render, screen, waitFor, within } from '@testing-library/react'
import type { AuthUser, CallerPermissions } from '@wasichai/core'
import { mockFetch, type FetchMock, type MockRoute } from '@wasichai/testing'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { abrirSesion, ADMIN_PERMISOS, ADMINISTRACION, AUDITORA, rutasDeSesion } from '../../test/portal'
import { PortalApp } from '../PortalApp'

// el Dashboard de F1 (roadmap, B7): usuario, organización, roles y mapa de módulos por fase, sin indicadores

const ORGANIZACION = { id: 'o1', name: 'Organización de prueba', slug: 'prueba', createdAt: null }

let fetch: FetchMock | null = null
beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})
afterEach(() => {
  fetch?.restore()
  fetch = null
})

function start(user: AuthUser, permisos: CallerPermissions, primero: MockRoute[] = []) {
  abrirSesion(user)
  window.history.pushState({}, '', '/')
  fetch = mockFetch([...primero, { path: '/organizations/current', body: ORGANIZACION }, ...rutasDeSesion(user, permisos)])
  render(<PortalApp />)
}

const filas = () =>
  within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((fila) =>
      within(fila)
        .getAllByRole('cell')
        .map((celda) => celda.textContent)
    )

const POR_LLEGAR = [
  ['Proyectos', 'F2, F7', 'Llega en F2'],
  ['Territorio', 'F2, F6', 'Llega en F2'],
  ['Grupos de interés', 'F2', 'Llega en F2'],
  ['Relacionamiento', 'F2', 'Llega en F2'],
  ['Compromisos', 'F3', 'Llega en F3'],
  ['Quejas, consultas y solicitudes', 'F4', 'Llega en F4'],
  ['Riesgos', 'F4', 'Llega en F4'],
  ['Programas sociales', 'F5', 'Llega en F5'],
  ['Documentos', 'F3', 'Llega en F3'],
  ['Tareas', 'F3', 'Llega en F3'],
  ['Reportes', 'F2, F8', 'Llega en F2']
]

describe('Dashboard de F1', () => {
  it('muestra la cuenta, la organización, los roles y el mapa de módulos por fase', async () => {
    start(ADMINISTRACION, ADMIN_PERMISOS)
    expect(await screen.findByRole('heading', { name: 'Dashboard', level: 1 })).toBeInTheDocument()
    expect(screen.getByText('Administración de prueba (admin@sgspe.test)')).toBeInTheDocument()
    expect(await screen.findByText('Organización de prueba')).toBeInTheDocument()
    expect(await screen.findByText('ADMIN')).toBeInTheDocument()
    await waitFor(() => expect(filas()).toEqual([['Dashboard', 'F1, F8', 'Disponible'], ...POR_LLEGAR, ['Administración', 'F1', 'Disponible']]))
    expect(screen.getByText('2 de 13 secciones disponibles para su cuenta')).toBeInTheDocument()
  })

  it('a AUDITOR le muestra su rol y que Administración es solo para quien administra', async () => {
    start(AUDITORA, { admin: false, objects: {} })
    expect(await screen.findByText('AUDITOR')).toBeInTheDocument()
    expect(filas()).toEqual([['Dashboard', 'F1, F8', 'Disponible'], ...POR_LLEGAR, ['Administración', 'F1', 'Solo para administración']])
    expect(screen.getByText('1 de 13 secciones disponibles para su cuenta')).toBeInTheDocument()
  })

  it('dice que no pudo leer la organización en lugar de inventarla', async () => {
    start(AUDITORA, { admin: false, objects: {} }, [{ path: '/organizations/current', status: 404, body: { title: 'Not Found', status: 404 } }])
    expect(await screen.findByText('No se pudo leer la organización')).toBeInTheDocument()
  })
})
```

- [ ] **Paso 2: verla fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn test src/portal/pages 2>&1 | tail -5
```

Esperado: FAIL: no aparecen `Administración de prueba (admin@sgspe.test)` ni la tabla.

- [ ] **Paso 3: los textos del Dashboard**

En `src/portal/textos/es.ts`, reemplazar `  inicio: { titulo: 'Dashboard' },` por:

```ts
  inicio: {
    titulo: 'Dashboard',
    bienvenida: 'Le damos la bienvenida, {{nombre}}.',
    cuenta: 'Su cuenta',
    usuario: 'Usuario',
    organizacion: 'Organización',
    roles: 'Roles',
    cargando: 'Cargando…',
    sinOrganizacion: 'No se pudo leer la organización',
    sinCuenta: 'No se pudo leer la cuenta',
    sinRoles: 'Sin roles',
    modulos: 'Módulos por fase',
    resumen: '{{disponibles, number}} de {{total, number}} secciones disponibles para su cuenta',
    seccion: 'Sección',
    fases: 'Fases',
    estado: 'Estado',
    llegaEn: 'Llega en {{fase}}',
    estados: { disponible: 'Disponible', 'sin-acceso': 'Sin acceso con sus permisos', 'solo-admin': 'Solo para administración' }
  },
```

En `src/portal/textos/en.ts`, reemplazar `  inicio: { titulo: 'Dashboard' },` por:

```ts
  inicio: {
    titulo: 'Dashboard',
    bienvenida: 'Welcome, {{nombre}}.',
    cuenta: 'Your account',
    usuario: 'User',
    organizacion: 'Organization',
    roles: 'Roles',
    cargando: 'Loading…',
    sinOrganizacion: 'Could not read the organization',
    sinCuenta: 'Could not read the account',
    sinRoles: 'No roles',
    modulos: 'Modules by phase',
    resumen: '{{disponibles, number}} of {{total, number}} sections available to your account',
    seccion: 'Section',
    fases: 'Phases',
    estado: 'Status',
    llegaEn: 'Arrives in {{fase}}',
    estados: { disponible: 'Available', 'sin-acceso': 'No access with your permissions', 'solo-admin': 'Administrators only' }
  },
```

- [ ] **Paso 4: `src/portal/queries.ts`**

```ts
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@wasichai/core'
import { client } from './api'

// GET /api/auth/me: la cuenta a la que pertenece el token, según wasichai (no lo que se tecleó en el login)
export interface CuentaDeWasichai {
  userId: string
  organizationId: string
  email: string
  roles: string[]
}

// GET /api/organizations/current: el tenant de quien llama (cualquier cuenta lo lee)
export interface Organizacion {
  id: string
  name: string
  slug: string
}

export function useCuenta() {
  const { user } = useAuth()
  return useQuery({
    queryKey: ['auth', 'me', user?.id ?? null],
    queryFn: () => client.request<CuentaDeWasichai>('/auth/me'),
    enabled: user !== null,
    staleTime: Infinity
  })
}

export function useOrganizacion() {
  const { user } = useAuth()
  return useQuery({
    queryKey: ['organizations', 'current', user?.id ?? null],
    queryFn: () => client.request<Organizacion>('/organizations/current'),
    enabled: user !== null,
    staleTime: Infinity
  })
}
```

- [ ] **Paso 5: `src/portal/pages/InicioPage.tsx`** (contenido completo)

```tsx
import { useAuth } from '@wasichai/core'
import { Badge, Table, Td, Th } from '@wasichai/ui'
import { useTranslation } from 'react-i18next'
import { NS } from '../i18n'
import { useCuenta, useOrganizacion } from '../queries'
import { estadoDe, NAV_TREE, useOferta, type Estado } from '../shell/navTree'

// el Dashboard de F1 (roadmap, B7): quién es, de qué organización, con qué roles, y el mapa de módulos por fase, sin
// indicadores (dashboards.md §11). los roles son los nombres técnicos que contesta wasichai: sus etiquetas en español solo
// las lee quien administra (/api/roles). lo que no se pudo leer se dice; nunca se inventa
export function InicioPage() {
  const { t } = useTranslation(NS)
  const { user } = useAuth()
  const cuenta = useCuenta()
  const organizacion = useOrganizacion()
  const oferta = useOferta()
  const estados = NAV_TREE.map((seccion) => ({ seccion, estado: estadoDe(seccion, oferta) }))
  const disponibles = estados.filter(({ estado }) => estado === 'disponible').length
  const rotulo = (estado: Estado, fase: string) => (estado === 'por-llegar' ? t('inicio.llegaEn', { fase }) : t(`inicio.estados.${estado}`))

  return (
    <div className="space-y-6">
      <div className="space-y-1">
        <h1 className="text-xl font-semibold text-ink">{t('inicio.titulo')}</h1>
        <p className="text-sm text-ink-muted">{t('inicio.bienvenida', { nombre: user?.displayName ?? '' })}</p>
      </div>
      <section aria-labelledby="inicio-cuenta" className="space-y-2">
        <h2 id="inicio-cuenta" className="text-base font-semibold text-ink">
          {t('inicio.cuenta')}
        </h2>
        <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
          <dt className="text-ink-muted">{t('inicio.usuario')}</dt>
          <dd>{user ? `${user.displayName} (${user.email})` : ''}</dd>
          <dt className="text-ink-muted">{t('inicio.organizacion')}</dt>
          <dd>{organizacion.isPending ? t('inicio.cargando') : organizacion.isError ? t('inicio.sinOrganizacion') : organizacion.data.name}</dd>
          <dt className="text-ink-muted">{t('inicio.roles')}</dt>
          <dd>
            {cuenta.isPending
              ? t('inicio.cargando')
              : cuenta.isError
                ? t('inicio.sinCuenta')
                : cuenta.data.roles.length
                  ? cuenta.data.roles.join(', ')
                  : t('inicio.sinRoles')}
          </dd>
        </dl>
      </section>
      <section aria-labelledby="inicio-modulos" className="space-y-2">
        <h2 id="inicio-modulos" className="text-base font-semibold text-ink">
          {t('inicio.modulos')}
        </h2>
        <p className="text-sm text-ink-muted">{t('inicio.resumen', { disponibles, total: NAV_TREE.length })}</p>
        <Table>
          <thead>
            <tr>
              <Th>{t('inicio.seccion')}</Th>
              <Th>{t('inicio.fases')}</Th>
              <Th>{t('inicio.estado')}</Th>
            </tr>
          </thead>
          <tbody>
            {estados.map(({ seccion, estado }) => (
              <tr key={seccion.clave}>
                <Td>{t(`secciones.${seccion.clave}`)}</Td>
                <Td>{seccion.fases.join(', ')}</Td>
                <Td>
                  <Badge>{rotulo(estado, seccion.fases[0])}</Badge>
                </Td>
              </tr>
            ))}
          </tbody>
        </Table>
      </section>
    </div>
  )
}
```

- [ ] **Paso 6: verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format && yarn test 2>&1 | tail -4 && yarn lint && yarn typecheck && yarn build
```

Esperado: `Test Files  10 passed (10)`; `Inicio.test.tsx` con 3 pruebas en verde; los demás comandos limpios.

- [ ] **Paso 7: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add src/portal/queries.ts src/portal/pages/InicioPage.tsx src/portal/textos/es.ts src/portal/textos/en.ts \
  src/portal/pages/Inicio.test.tsx
git commit -F - <<'EOF'
feat(portal): Dashboard de F1 con cuenta, organización, roles y módulos por fase

La cuenta y la organización salen de wasichai; lo que no se puede leer se dice. El mapa
de módulos usa el mismo estado que el árbol de navegación.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 19: B8 · CI de `sgspe-ui`, Dependabot y `boundaries.test`

**Pista:** B.

**Files:**
- Create (copia exacta de caja-ui@a45b224): `.github/workflows/ci.yml`, `.github/workflows/commits.yml`
- Create: `.github/dependabot.yml`
- Modify: `package.json` (`lint` y `format` con `.github`)
- Test: `src/boundaries.test.ts`

**Interfaces:**
- Consumes: el secreto `PACKAGES_TOKEN` (Tarea 1, Paso 10); todas las fuentes de `src/`.
- Produces: en cada PR, `yarn lint`, `typecheck`, `test` y `build`, y commitlint; Dependabot semanal con `@wasichai/*` en un
  grupo propio; las reglas sin literales de rol ni importaciones internas de `@wasichai/*`.

- [ ] **Paso 1: escribir la prueba, `src/boundaries.test.ts`**

```ts
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

// las fronteras de sgspe-ui (security.md §5.10, ADR-0010): ningún fuente nombra un rol (RQ-SEC-005) y @wasichai/* se importa
// solo por la raíz de cada paquete, nunca por sus archivos internos (RNF-MNT-005). las pruebas y sus ayudas (src/test) sí
// nombran roles: son datos de prueba. como boundaries.test.tsx de caja-ui@a45b224, escanea las fuentes
const SRC = import.meta.dirname

// los 15 roles declarados de model/roles.json de sgspe, los 2 que son ADMIN y el ADMIN de wasichai
const ROLES = [
  'ADMIN',
  'SUPER_ADMIN',
  'ORGANIZATION_ADMIN',
  'PROJECT_ADMIN',
  'PROJECT_MANAGER',
  'SOCIAL_MANAGER',
  'COMMUNITY_RELATIONS_OFFICER',
  'STAKEHOLDER_MANAGER',
  'COMMITMENT_MANAGER',
  'GRIEVANCE_MANAGER',
  'SOCIAL_RISK_MANAGER',
  'SOCIAL_INVESTMENT_MANAGER',
  'GIS_ANALYST',
  'FIELD_AGENT',
  'APPROVER',
  'AUDITOR',
  'ANALYST',
  'READ_ONLY'
]
const ROL = new RegExp(`\\b(${ROLES.join('|')})\\b`)
const IMPORTACION_INTERNA = /(?:from |import |import\()(['"])@wasichai\/[a-z-]+\/[^'"]+\1/

function fuentes(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name)
    if (statSync(path).isDirectory()) return path === join(SRC, 'test') ? [] : fuentes(path)
    return /\.tsx?$/.test(name) && !/\.test\.tsx?$/.test(name) ? [path] : []
  })
}

const ruta = (file: string) => relative(SRC, file).split(sep).join('/')

// el código sin los comentarios de línea, que pueden nombrar un rol para explicar por qué no se usa
const codigo = (file: string) =>
  readFileSync(file, 'utf8')
    .split('\n')
    .map((line) => line.replace(/\/\/.*$/, ''))

const hallazgos = (regla: RegExp) =>
  fuentes(SRC).flatMap((file) => codigo(file).flatMap((line, i) => (regla.test(line) ? [`${ruta(file)}:${i + 1}: ${line.trim()}`] : [])))

describe('fronteras de la interfaz', () => {
  it('ve las fuentes del portal y de /admin', () => {
    expect(fuentes(SRC).map(ruta)).toEqual(expect.arrayContaining(['main.tsx', 'admin/AdminApp.tsx', 'portal/PortalApp.tsx', 'portal/shell/navTree.ts']))
  })

  it('ningún fuente nombra un rol', () => {
    expect(hallazgos(ROL)).toEqual([])
  })

  it('importa @wasichai/* solo por la raíz del paquete', () => {
    expect(hallazgos(IMPORTACION_INTERNA)).toEqual([])
  })

  it('los vigías reconocen lo prohibido y dejan pasar lo permitido', () => {
    expect(ROL.test("user.roles.includes('AUDITOR')")).toBe(true)
    expect(ROL.test("const rol = 'GIS_ANALYST'")).toBe(true)
    expect(ROL.test('const { isAdmin } = useAuth()')).toBe(false)
    expect(ROL.test("label: 'Administración'")).toBe(false)
    expect(IMPORTACION_INTERNA.test("import { x } from '@wasichai/core/dist/auth/AuthProvider'")).toBe(true)
    expect(IMPORTACION_INTERNA.test("import { useAuth } from '@wasichai/core'")).toBe(false)
  })
})
```

- [ ] **Paso 2: plantar una violación y verla fallar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
printf "export const VIOLACION_DE_PRUEBA = ['AUDITOR']\n" > src/violacionDePrueba.ts
yarn test src/boundaries 2>&1 | tail -6
```

Esperado: FAIL en `ningún fuente nombra un rol`, con `violacionDePrueba.ts:1: export const VIOLACION_DE_PRUEBA = ['AUDITOR']`.

- [ ] **Paso 3: quitarla y verla pasar**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
rm src/violacionDePrueba.ts
yarn test src/boundaries 2>&1 | tail -4
```

Esperado: 4 pruebas en verde.

- [ ] **Paso 4: la CI y Dependabot**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
mkdir -p .github/workflows
cp /Users/jorge/IdeaProjects/caja-ui/.github/workflows/ci.yml .github/workflows/ci.yml
cp /Users/jorge/IdeaProjects/caja-ui/.github/workflows/commits.yml .github/workflows/commits.yml
grep -c 'PACKAGES_TOKEN' .github/workflows/ci.yml .github/workflows/commits.yml
```

Esperado: `1` en cada archivo. Ninguna edición: los dos trabajos ya instalan `@wasichai/*` con `PACKAGES_TOKEN` y las
acciones van fijadas por SHA.

`.github/dependabot.yml` (en lugar del `renovate.json` de caja-ui, porque el roadmap pone `PACKAGES_TOKEN` en Dependabot):

```yaml
# actualizaciones semanales. los prefijos mantienen convencionales los commits y los títulos de los PR (commits.yml)
version: 2
registries:
  # @wasichai/* son paquetes internos: dependabot los lee con su secreto PACKAGES_TOKEN (un PAT con read:packages)
  npm-github:
    type: npm-registry
    url: https://npm.pkg.github.com
    token: ${{secrets.PACKAGES_TOKEN}}
updates:
  # las acciones van fijadas por sha; dependabot mueve el sha y el comentario de versión juntos
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: weekly
    commit-message:
      prefix: ci
      include: scope
    groups:
      actions:
        patterns: ['*']

  - package-ecosystem: npm
    directory: /
    registries:
      - npm-github
    schedule:
      interval: weekly
    commit-message:
      prefix: build
      include: scope
    groups:
      # @wasichai/* se actualizan juntos, y en el mismo ciclo que wasichai del backend (ADR-0010)
      wasichai:
        patterns: ['@wasichai/*']
      tooling:
        patterns: ['*']
        exclude-patterns: ['@wasichai/*']
```

En `package.json`, reemplazar las líneas de `lint` y `format` por:

```json
        "lint": "prettier --check src index.html package.json tsconfig.json tsconfig.build.json vite.config.ts csp.ts commitlint.config.mjs .github",
        "format": "prettier --write src index.html package.json tsconfig.json tsconfig.build.json vite.config.ts csp.ts commitlint.config.mjs .github",
```

- [ ] **Paso 5: la verificación completa de la pista B**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn format && yarn lint && yarn typecheck && yarn test 2>&1 | tail -4 && yarn build
```

Esperado: `Test Files  11 passed (11)`; todo limpio.

- [ ] **Paso 6: commit**

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
git add .github/workflows/ci.yml .github/workflows/commits.yml .github/dependabot.yml package.json src/boundaries.test.ts
git commit -F - <<'EOF'
ci: lint, typecheck, pruebas y build con PACKAGES_TOKEN; fronteras de la interfaz

CI y commitlint copiados de caja-ui; Dependabot con el registro npm de @wasichai/* en
un grupo propio. boundaries.test vigila literales de rol e importaciones internas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

---

### Tarea 20: Pista C · Verificación final de F1 con prueba de humo

**Pista:** C. **Responsable:** coordinador (el agente ejecuta la prueba de humo con Playwright MCP, ASM-066).

**Files:**
- Ninguno versionado. La captura de pantalla va al scratchpad de la sesión.

**Interfaces:**
- Consumes: las pistas A y B completas; el túnel al 5435 (Tarea 1); el administrador semilla `admin@wasichai.local` /
  `admin` y la organización semilla `Demo` de wasichai.
- Produces: la evidencia que citan `docs/HISTORY.md` y el PR (Tarea 21).

- [ ] **Paso 1: la base y el túnel**

```bash
cd /Users/jorge/IdeaProjects/sgspe
nc -z localhost 5435 && echo tunel-ok
docker compose ps --format '{{.Name}} {{.Status}}'
```

Esperado: `tunel-ok` y `sgspe-postgis Up … (healthy)`. Si falta el túnel, **[USUARIO]** lo abre (Tarea 1, Paso 8).

- [ ] **Paso 2: arrancar el backend** (herramienta Bash con `run_in_background`)

```bash
cd /Users/jorge/IdeaProjects/sgspe
set -a; source develop/.env; set +a
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun
```

Esperado en el log: `Started SgspeApplication`.

- [ ] **Paso 3: aprovisionar en el orden de `security.md` §4.1**

```bash
cd /Users/jorge/IdeaProjects/sgspe/model
python3 apply.py | tail -1
python3 apply_roles.py | tail -1
python3 apply.py --workflows
```

Esperado: `done: 0 created, 0 updated, 0 skipped`; `done: 0 created, 0 updated, 15 skipped` (los roles ya se crearon en la
Tarea 6; en una base nueva, `done: 15 created, 0 updated, 0 skipped`); `done: 0 workflows, 0 document types, 0 automations,
0 views, 0 forms, 0 pages`.

- [ ] **Paso 4: el usuario `AUDITOR` de la prueba de humo**

```bash
TOKEN=$(curl -s -X POST http://localhost:8094/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@wasichai.local","password":"admin"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8094/api/users -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"email":"auditoria.humo@sgspe.test","displayName":"Auditoría de humo","password":"clave-de-humo-1","roles":["AUDITOR"]}'
```

Esperado: `201` (o `409` si el usuario quedó de una corrida anterior; vale igual).

- [ ] **Paso 5: arrancar la interfaz** (herramienta Bash con `run_in_background`)

```bash
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn dev
```

Esperado en el log: `Local:   http://localhost:5182/`.

- [ ] **Paso 6: correlación y CSP a través del proxy de Vite**

```bash
curl -s -o /dev/null -D - http://localhost:5182/api/auth/me | grep -iE '^(HTTP/|x-correlation-id)'
curl -s -o /dev/null -D - -H 'X-Correlation-Id: humo-1' http://localhost:5182/api/auth/me | grep -i '^x-correlation-id'
curl -s -o /dev/null -D - -H 'X-Correlation-Id: humo con espacio' http://localhost:5182/api/auth/me | grep -i '^x-correlation-id'
curl -s -o /dev/null -D - http://localhost:5182/ | grep -i '^content-security-policy-report-only' | cut -c1-90
```

Esperado: `HTTP/1.1 401` y `x-correlation-id: <un UUID>`; `x-correlation-id: humo-1`; `x-correlation-id: <un UUID>`
(nunca `humo con espacio`); `Content-Security-Policy-Report-Only: default-src 'self'; script-src 'self' 'sha256-…`.

- [ ] **Paso 7: prueba de humo con Playwright MCP**

Cargar las herramientas en una sola llamada: `ToolSearch` con
`select:mcp__plugin_playwright_playwright__browser_navigate,mcp__plugin_playwright_playwright__browser_fill_form,mcp__plugin_playwright_playwright__browser_click,mcp__plugin_playwright_playwright__browser_snapshot,mcp__plugin_playwright_playwright__browser_take_screenshot,mcp__plugin_playwright_playwright__browser_close`.
Después, en este orden, con lo esperado en cada uno:

1. `browser_navigate` a `http://localhost:5182/` → la URL termina en `/login?next=%2F` y se ve el formulario con «Correo»,
   «Contraseña» e «Ingresar».
2. `browser_fill_form` con «Correo» = `admin@wasichai.local` y «Contraseña» = `admin`; `browser_click` en «Ingresar» →
   `browser_snapshot` muestra el título «Dashboard», «Platform Administrator (admin@wasichai.local)», la organización
   «Demo», el rol «ADMIN», la tabla «Módulos por fase» con 13 filas y la navegación «Secciones» con «Dashboard» y
   «Administración».
3. `browser_navigate` a `http://localhost:5182/admin/admin/roles` → sin pedir login otra vez (token compartido
   `sgspe.token`), la tabla de roles lista las 15 etiquetas en español (Administrador de proyectos, Jefe de proyecto, Gerente
   social, Relacionista comunitario, Gestor de grupos de interés, Gestor de compromisos, Gestor de quejas, consultas y
   solicitudes, Gestor de riesgos sociales, Gestor de inversión social, Analista SIG, Agente de campo, Aprobador, Auditor,
   Analista, Solo lectura) más el `ADMIN` de wasichai.
4. `browser_navigate` a `http://localhost:5182/`; `browser_click` en «Cerrar sesión» → vuelve al login.
5. Entrar con `auditoria.humo@sgspe.test` / `clave-de-humo-1` → el Dashboard muestra el rol «AUDITOR», la navegación solo
   tiene «Dashboard» y la fila Administración dice «Solo para administración».
6. `browser_navigate` a `http://localhost:5182/admin/admin/audit` → la página de auditoría carga y lista cambios (las
   escrituras de los pasos 3 y 4 de esta tarea) sin un error de permisos.
7. `browser_take_screenshot` del Dashboard del auditor, guardada en el scratchpad de la sesión como
   `f1-smoke-auditor.png`; `browser_close`.

- [ ] **Paso 8: lo que el auditor no puede hacer**

```bash
AUD=$(curl -s -X POST http://localhost:8094/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"auditoria.humo@sgspe.test","password":"clave-de-humo-1"}' | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')
curl -s -o /dev/null -w 'audit %{http_code}\n' -H "Authorization: Bearer $AUD" http://localhost:8094/api/audit
curl -s -o /dev/null -w 'users %{http_code}\n' -H "Authorization: Bearer $AUD" http://localhost:8094/api/users
curl -s -o /dev/null -w 'roles %{http_code}\n' -H "Authorization: Bearer $AUD" http://localhost:8094/api/roles
```

Esperado: `audit 200`, `users 403`, `roles 403`.

- [ ] **Paso 9: todas las suites**

```bash
cd /Users/jorge/IdeaProjects/sgspe
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build
WASICHAI_TEST_DB_HOST=localhost WASICHAI_TEST_DB_PORT=5435 WASICHAI_TEST_DB_NAME=sgspe_test \
WASICHAI_TEST_DB_USERNAME=sgspe WASICHAI_TEST_DB_PASSWORD=sgspe \
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun
(cd model && python3 -m unittest 2>&1 | tail -2)
python3 -m unittest discover -s tools 2>&1 | tail -2
python3 tools/check_traceability.py
yarn -s format:check
cd /Users/jorge/IdeaProjects/sgspe-ui
yarn lint && yarn typecheck && yarn test 2>&1 | tail -3 && yarn build
```

Esperado: `BUILD SUCCESSFUL` dos veces (32 pruebas de integración: 3 de humo, 23 de contrato, 5 de observabilidad y
1 de `Records`); `OK` dos veces; `ok: 298 requisitos, todos con su fila`; prettier limpio; en
`sgspe-ui`, `Test Files  11 passed (11)` y el build sin errores.

- [ ] **Paso 10: detener los servidores**

Detener `yarn dev` y `bootRun` (las tareas en segundo plano). El túnel del 5435 y el contenedor quedan arriba para el
desarrollo de F2.

---

### Tarea 21: Cierre documental (A9 y B9), push y PR

**Pista:** cierre. **Responsable:** coordinador.

**Files:**
- `sgspe`: Modify `README.md`, `docs/README.md`, `docs/HISTORY.md`, `docs/requirements-traceability.md`, `docs/roadmap.md`;
  Create `CLAUDE.md`
- `sgspe-ui`: Create `README.md`, `CLAUDE.md`, `HISTORY.md`

**Interfaces:**
- Consumes: la evidencia de la Tarea 20 y el resultado del spike (Tarea 2).
- Produces: el estado de F1 en la trazabilidad y el roadmap; los PR de los dos repositorios.

En todos los archivos, `AAAA-MM-DD` es la salida de `date +%F` el día del cierre.

- [ ] **Paso 1: `README.md` de `sgspe`** (contenido completo)

````markdown
# sgspe

SGSPE es un **Sistema de Gestión Social para Proyectos Extractivos**. Sirve a un equipo social para gestionar en un
solo lugar el territorio, las áreas de influencia, los grupos de interés y el relacionamiento con ellos. También cubre
los compromisos, las quejas, consultas y solicitudes, los riesgos y alertas, y los programas sociales. Ofrece vistas en
GIS, tableros y entregables DOCX y PDF.

## Repositorios

- **Este repositorio (`sgspe`):** el backend en Kotlin sobre la plataforma
  [wasichai](https://github.com/wasichai/wasichai) y toda la documentación de diseño.
- **[`sgspe-ui`](https://github.com/wasichai/sgspe-ui):** la interfaz web. Tiene un portal en `/` y la administración
  de wasichai en `/admin`.

## Estado

**Fase 1, fundación:** terminada (ver [`docs/HISTORY.md`](docs/HISTORY.md)). La Fase 2 trae los primeros objetos de
negocio: proyectos, territorio, grupos de interés y relacionamiento ([`docs/roadmap.md`](docs/roadmap.md)).

- La app `sgspe` corre sobre wasichai 0.3.2 con los starters core, views, forms y pages, todavía sin objetos de negocio.
- `model/` declara los 17 roles (15 en `roles.json` y 2 mapeados a `ADMIN`) y aprovisiona un tenant en orden.
- Pruebas de contrato con la plataforma, `X-Correlation-Id`, perfil de producción y pruebas de arquitectura.

| | |
|---|---|
| Servidor | `src/`, puerto 8094 |
| Base de datos | PostgreSQL 18 con PostGIS 3.6 (`compose.yml`, `postgis/postgis:18-3.6`, puerto 5435, base `sgspe`) |
| Login de desarrollo | `admin@wasichai.local` / `admin` (`WASICHAI_SEED_DEV=true` por defecto) |
| Interfaz | [`sgspe-ui`](https://github.com/wasichai/sgspe-ui), puerto 5182 |

## Arrancar

```bash
cp develop/example.env develop/.env
set -a; source develop/.env; set +a
docker compose up -d
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun
cd model && python3 apply.py && python3 apply_roles.py && python3 apply.py --workflows
```

El Docker de desarrollo es remoto: la base llega por un túnel SSH. La guía completa (túnel, variables, pruebas de
integración) está en [`docs/develop/README.md`](docs/develop/README.md).

## Comandos

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build             # ktlint y pruebas unitarias
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest   # en la CI o con la base externa
(cd model && python3 -m unittest -v)                                                             # scripts del modelo
python3 tools/check_traceability.py                                                              # una fila por requisito
yarn format:check                                                                                # yaml y json
```

## Documentación

- [Índice de la documentación](docs/README.md)
- [Desarrollo local](docs/develop/README.md)
- [Decisiones de arquitectura](docs/adr/README.md)
- [Historia de cambios](docs/HISTORY.md)
````

- [ ] **Paso 2: `CLAUDE.md` de `sgspe`**

````markdown
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
````

- [ ] **Paso 3: entrada de `docs/HISTORY.md`**

Insertar antes de `## 2026-10-07 — Fase 0: descubrimiento y diseño`:

```markdown
## AAAA-MM-DD — Fase 1: fundación

La Fase 1 deja SGSPE compilando y ejecutable sobre wasichai 0.3.2 y `@wasichai/*` 0.5.0, todavía sin objetos de negocio:
el arranque de la app, los roles como datos, el contrato con la plataforma, la observabilidad y la CI de los dos
repositorios. El plan ejecutable está en
[`superpowers/plans/2026-10-07-fase-1-fundacion.md`](superpowers/plans/2026-10-07-fase-1-fundacion.md).

### Backend (`sgspe`)

- **App.** `sgspe.SgspeApplication` con `@WasichaiApplication` y los starters core, views, forms y pages; puerto 8094;
  sondas `/actuator/health/liveness` y `/readiness`; `compose.yml` con `postgis/postgis:18-3.6` en el 5435 del VPS.
- **Modelo como código.** `model.json` sin objetos; `roles.json` con los 15 roles declarados y su etiqueta en español, y
  `SUPER_ADMIN` y `ORGANIZATION_ADMIN` mapeados a `ADMIN`; solo `AUDITOR` lee todo el tenant. `apply_roles.py` entiende `"*"`,
  `u` y `"derived": true`, y deshace `ownRecordsOnly`; `apply.py --workflows` es el segundo pase del aprovisionamiento.
- **Contrato con la plataforma.** `TenantIsolationApiTest`, `TenantProvisioningApiTest`, `SecurityApiTest`,
  `RolesApiTest`, `AuditApiTest` y `ErrorContractApiTest`, sobre `SgspeApiTest`, que aprovisiona con los scripts reales.
- **Observabilidad y producción.** `X-Correlation-Id` antes de la seguridad, también en un 401, y una línea de acceso sin
  la cadena de consulta; `application-prod.yml` sin semilla, con secreto obligatorio, token de 4 horas y logs ECS.
- **`sgspe.common`.** La marca `SgspeWrite`, `Records` sobre `RecordService`, la hora de Lima y el 422 de parámetros
  desconocidos.
- **Arquitectura y CI.** `ArchitectureTest`; CI con build, integración, modelo, trazabilidad (`tools/check_traceability.py`)
  y formato.

### Interfaz (`sgspe-ui`)

- Una app Vite con el portal y `/admin` (`WasichaiApp` con views, forms y pages), login compartido en `sgspe.token`,
  `RequireSession` y `safeNext`; temas claro y oscuro con el script de arranque autorizado por hash en la CSP; las 13
  secciones del prompt con su fase; español e inglés con números y fechas es-PE; el Dashboard de F1; CI con
  `PACKAGES_TOKEN` y `boundaries.test`.

### Spike y hallazgos

- El spike de entregables y GetMap dejó su resultado en el «Seguimiento» de ADR-0013 y ADR-0015.
- wasichai 0.3.2 responde el 401 sin token con `WWW-Authenticate: Bearer` y cuerpo vacío, no con Problem Details;
  `ErrorContractApiTest` fija ese contrato.
- wasichai 0.3.2 ignora sin error un atributo que el objeto no tiene; `ErrorContractApiTest` también fija ese contrato.
- `apply.py` de caja rechazaba un modelo sin objetos; la copia de SGSPE lo acepta.

### Verificación

- Pista C: aprovisionamiento en orden, correlación por el proxy de Vite y prueba de humo con Playwright MCP (login,
  Dashboard con la organización y el rol, los 15 roles en `/admin`, un `AUDITOR` que lee la auditoría, no ve Administración
  y recibe 403 en usuarios).
```

- [ ] **Paso 4: `docs/README.md`**

1. Reemplazar:

```markdown
Esta carpeta es el resultado de la **Fase 0 (descubrimiento y diseño)**, que no tiene código. La Fase 1 empieza solo
después de la revisión del checkpoint de la Fase 0 (ver [roadmap](roadmap.md)).
```

   por:

```markdown
Esta carpeta nació en la **Fase 0 (descubrimiento y diseño)**, sin código. La **Fase 1 (fundación)** agregó el código en
la raíz del repositorio; su guía de desarrollo está en [`develop/README.md`](develop/README.md) y su historia en
[`HISTORY.md`](HISTORY.md).
```

2. En la tabla de documentos, después de la fila de `roadmap.md`, agregar:

```markdown
| [`develop/README.md`](develop/README.md) | Desarrollo local: base en el VPS, túnel, arranque, modelo y pruebas |
```

3. En la fila de `superpowers/plans/`, reemplazar `Plan aprobado de las fases 0 y 1, y plan ejecutable de la Fase 0` por
   `Plan aprobado de las fases 0 y 1, y planes ejecutables de las fases 0 y 1`.

- [ ] **Paso 5: `docs/roadmap.md`**

1. En la tabla de «F1 · Fundación (detalle)», después de la fila que empieza por `| Requisitos de la fase |`, agregar:

```markdown
| Estado | Terminada el AAAA-MM-DD: pistas 0, A, B y C en verde y spike hecho; plan ejecutable en `docs/superpowers/plans/2026-10-07-fase-1-fundacion.md` |
```

2. En «Definición de terminado de F1», reemplazar `` `check_traceability.py` (propuesto) en la CI verifica `` por
   `` `tools/check_traceability.py` en la CI verifica ``.

- [ ] **Paso 6: estado de las filas de F1 en `docs/requirements-traceability.md`**

```bash
cd /Users/jorge/IdeaProjects/sgspe
python3 - <<'EOF'
import re

path = "docs/requirements-traceability.md"
text = open(path, encoding="utf-8").read()

# el estado nuevo de cada fila de F1; las que no figuran (RQ-SEC-015, RQ-AUD-009, RQ-AUD-010) siguen como estaban
ESTADOS = {
    "RF-IAM-001": "Implementado",
    "RF-IAM-002": "Implementado; Upstream (UPS-06)",
    "RQ-SEC-001": "Implementado",
    "RQ-SEC-002": "Implementado",
    "RQ-SEC-003": "Implementado; Upstream (UPS-10)",
    "RQ-SEC-004": "Implementado",
    "RQ-SEC-005": "Implementado",
    "RQ-SEC-007": "Implementado; Upstream (UPS-06)",
    "RQ-SEC-016": "Implementado",
    "RQ-SEC-017": "Implementado",
    "RQ-AUD-001": "Implementado",
    "RQ-AUD-006": "Implementado; Upstream (UPS-03)",
    "RQ-AUD-008": "Implementado; Upstream (UPS-02)",
    "RQ-AUD-014": "Implementado",
    "RNF-MNT-001": "Implementado",
    "RNF-MNT-002": "Implementado",
    "RNF-MNT-005": "Implementado",
    "RNF-INT-001": "Implementado",
    "RNF-I18N-001": "Implementado",
    "RNF-OBS-001": "Implementado",
    "RNF-USA-001": "Implementado",
}
for rid, estado in ESTADOS.items():
    row = re.compile(r"^(\| " + re.escape(rid) + r" \|.*\| F1 \| )[^|]+( \|)$", re.M)
    text, n = row.subn(lambda m: m.group(1) + estado + m.group(2), text)
    assert n == 1, (rid, n)

old = "| F1 (`/admin`), F2 (fichas del portal) | Diseñado |"
assert text.count(old) == 1
text = text.replace(old, "| F1 (`/admin`), F2 (fichas del portal) | Implementado en `/admin`; Diseñado (fichas, F2) |")

for test in ("Navigation.test.tsx", "I18n.test.tsx"):
    assert f"`{test}` (propuesto)" in text, test
    text = text.replace(f"`{test}` (propuesto)", f"`{test}`")

# el estado nuevo en §1.2, después de Diseñado
old = "| Decisión pendiente | Depende de una decisión"
assert text.count(old) == 1
text = text.replace(old, "| Implementado | El código y su prueba existen y pasan en la CI de su fase (desde F1). Si la fila lleva además "
                         "Upstream, la brecha sigue abierta y su mitigación está implementada |\n" + old)

# la tabla de cuántas filas llevan cada estado, recontada sobre las filas de la matriz (las de nueve columnas)
rows = [l for l in text.splitlines() if re.match(r"^\| (RF|RNF|RN|RQ)-[A-Z0-9]+-\d{3} \|", l) and l.count("|") == 10]
assert len(rows) == 298, len(rows)
states = ["Diseñado", "Implementado", "Decisión pendiente", "Upstream", "Hueco", "Fuera del MVP"]
count = {s: sum(1 for l in rows if s in l.split("|")[-2]) for s in states}
table = re.compile(r"\| Estado \| Filas que lo llevan \|\n\|---\|---\|\n(?:\| [^|]+ \| \d+ \|\n)+")
new = "| Estado | Filas que lo llevan |\n|---|---|\n" + "".join(f"| {s} | {count[s]} |\n" for s in states)
text, n = table.subn(new, text)
assert n == 1
open(path, "w", encoding="utf-8").write(text)
print(count)
EOF
python3 tools/check_traceability.py
```

Esperado: `{'Diseñado': 242, 'Implementado': 22, 'Decisión pendiente': 14, 'Upstream': 28, 'Hueco': 0, 'Fuera del MVP': 82}`
y `ok: 298 requisitos, todos con su fila`. Si las cifras difieren, revisar que ninguna fila ajena a F1 haya cambiado
(`git diff --stat docs/requirements-traceability.md` muestra solo las filas de la tabla).

- [ ] **Paso 7: `README.md`, `CLAUDE.md` y `HISTORY.md` de `sgspe-ui`**

`/Users/jorge/IdeaProjects/sgspe-ui/README.md`:

````markdown
# sgspe-ui

Interfaz web de **SGSPE** (Sistema de Gestión Social para Proyectos Extractivos) sobre **wasichai-ui** (`@wasichai/*`
0.5.0), para el backend [`sgspe`](https://github.com/wasichai/sgspe), donde viven el diseño y la documentación. Es una
sola app Vite + React con dos partes que comparten el login (el mismo token en `localStorage['sgspe.token']`), el tema y
el idioma:

| Ruta | Para quién | Qué es |
|---|---|---|
| `/` | el equipo social | el **portal**: las 13 secciones del prompt, cada una con su fase; en F1, el Dashboard |
| `/admin` | quien administra | el `WasichaiApp` de wasichai-ui con views, forms y pages |

| | |
|---|---|
| Puerto | 5182 (`yarn dev` y `yarn preview`) |
| API | proxy de `/api` a `http://localhost:8094` (`WASICHAI_API_URL` lo cambia) |
| Login de desarrollo | `admin@wasichai.local` / `admin` (semilla de `sgspe`) |

## Portal (`src/portal`)

- **Sesión:** la de core (`useAuth`), la misma de `/admin`. `RequireSession` lleva a `/login?next=…` sin sesión;
  `safeNext` acepta solo rutas internas (rechaza `//`, `/\` y los caracteres de control). Un 401 vuelve al login.
- **Navegación:** `src/portal/shell/navTree.ts` declara las 13 secciones de `docs/architecture.md` §9.2 de `sgspe`, en su
  orden y con su fase. Una sección se dibuja solo si tiene pantalla (`src/portal/pantallas.ts`) y la cuenta tiene los
  permisos que pide (`GET /api/auth/me/permissions`); Administración, solo para quien administra.
- **Dashboard de F1:** cuenta, organización, roles y mapa de módulos por fase, sin indicadores.
- **Idioma:** español por defecto e inglés (`src/portal/textos/`), con números y fechas es-PE.
- **Temas:** claro y oscuro. El script de arranque de `index.html` es el único script en línea; `csp.ts` arma la CSP con
  su hash, Vite la manda en modo de solo informe y `yarn csp` la imprime para el proxy de producción.
- **Fronteras:** `src/boundaries.test.ts` falla ante un nombre de rol en el código o una importación interna de `@wasichai/*`.

## Requisitos

- Node 26 y Yarn 1.
- Lectura de GitHub Packages: el scope está en el `.npmrc` del proyecto y el token en `~/.npmrc`, fuera del repositorio
  (`//npm.pkg.github.com/:_authToken=…`, con `read:packages`). Instalar con `NODE_AUTH_TOKEN=$(gh auth token) yarn install`.

## Comandos

```bash
yarn dev             # http://localhost:5182, con sgspe corriendo en :8094
yarn test            # vitest
yarn typecheck
yarn lint            # prettier --check (yarn format lo corrige)
yarn build           # dist/, luego yarn preview
yarn -s csp          # la Content-Security-Policy para el proxy
```

## Notas

- `@wasichai/*` va en 0.5.0, igual en todos los paquetes, y se actualiza junto con wasichai del backend (ADR-0010 de
  `sgspe`); Dependabot los agrupa.
- La CI instala `@wasichai/*` con el secreto `PACKAGES_TOKEN` (un PAT con `read:packages`), en Actions y en Dependabot.
````

`/Users/jorge/IdeaProjects/sgspe-ui/CLAUDE.md`:

````markdown
# CLAUDE.md

Guía para agentes en `sgspe-ui`. El diseño vive en el repositorio `sgspe` (`../sgspe/docs/`): la interfaz es
`docs/architecture.md` §9 y ADR-0024; la seguridad, `docs/security.md` §3.2 y §5.10.

## Reglas

- Textos de interfaz en español (es-PE) e inglés, en `src/portal/textos/es.ts` y `en.ts` con las mismas claves; código y
  rutas propias en inglés (ADR-0009).
- Ningún nombre de rol en `src/` fuera de las pruebas: se decide con `useAuth().isAdmin` y los permisos de
  `/api/auth/me/permissions` (`src/boundaries.test.ts`). `@wasichai/*` se importa solo por la raíz de cada paquete.
- `/admin` registra solo los módulos de los starters instalados en `sgspe` y nunca `agentModule()`.
- Una sección nueva se registra en `src/portal/pantallas.ts` con su pantalla; sin pantalla no se dibuja.
- Pruebas primero, con Vitest, Testing Library y `mockFetch` de `@wasichai/testing`; usuarios ficticios `@sgspe.test`.
- Conventional Commits en español; `git add` con rutas explícitas.
- `yarn format && yarn lint && yarn typecheck && yarn test && yarn build` antes de cada commit.
- Instalar con `NODE_AUTH_TOKEN=$(gh auth token) yarn install`; ningún token en archivos del repositorio.
````

`/Users/jorge/IdeaProjects/sgspe-ui/HISTORY.md`:

```markdown
# Historia de cambios

La entrada más reciente va primero. El diseño y los ADR están en el repositorio `sgspe`.

## AAAA-MM-DD — Fase 1: fundación

- Andamiaje copiado de caja-ui: Vite 8 en el puerto 5182 con proxy de `/api` al 8094, Tailwind con `@wasichai/ui`,
  TypeScript, prettier, commitlint y husky.
- `/admin` con `WasichaiApp` (views, forms y pages) y `storagePrefix: 'sgspe'`.
- Portal con login compartido (`sgspe.token`), `RequireSession` y `safeNext` estricto.
- Temas claro y oscuro con el script de arranque autorizado por hash en la CSP (`csp.ts`, modo de solo informe en Vite).
- Las 13 secciones del prompt con su fase; en F1 se dibujan Dashboard y, para quien administra, Administración.
- Español por defecto e inglés, con números y fechas es-PE.
- Dashboard de F1: cuenta, organización, roles y mapa de módulos por fase.
- CI con `PACKAGES_TOKEN`, Dependabot y `boundaries.test`.
```

- [ ] **Paso 8: formato y nombres reales**

```bash
cd /Users/jorge/IdeaProjects/sgspe && yarn -s format:check && python3 tools/check_traceability.py
cd /Users/jorge/IdeaProjects/sgspe-ui && yarn lint
```

Esperado: limpio y `ok: 298 requisitos, todos con su fila`. Además, el coordinador corre su verificación de nombres reales
(RQ-SEC-018) sobre lo que va a commitear en los dos repositorios, con la lista que vive fuera de ellos: cero coincidencias.

- [ ] **Paso 9: commits**

```bash
cd /Users/jorge/IdeaProjects/sgspe
git add README.md CLAUDE.md docs/README.md docs/HISTORY.md docs/requirements-traceability.md docs/roadmap.md \
  docs/superpowers/plans/2026-10-07-fase-1-fundacion.md
git commit -F - <<'EOF'
docs: cierre de la Fase 1, trazabilidad y guía para agentes

README, CLAUDE.md, HISTORY y el índice; las filas de F1 de la matriz pasan a Implementado
y el roadmap marca F1 como terminada. Incluye el plan ejecutable de F1.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF

cd /Users/jorge/IdeaProjects/sgspe-ui
git add README.md CLAUDE.md HISTORY.md
git commit -F - <<'EOF'
docs: README, CLAUDE.md e historia de la Fase 1

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

Si el plan ejecutable ya se versionó antes, quitarlo de la lista de `git add`.

- [ ] **Paso 10 [USUARIO aprueba]: push y PR**

Con la aprobación del usuario. La base del PR de `sgspe` depende de si el PR de la Fase 0 ya se fusionó:

```bash
cd /Users/jorge/IdeaProjects/sgspe
gh pr view fase-0/descubrimiento --json state -q .state
```

`MERGED` → base `main`; `OPEN` → base `fase-0/descubrimiento`. Después, en cada repositorio:

```bash
cd /Users/jorge/IdeaProjects/sgspe
git push -u origin feat/fase-1-fundacion
gh pr create --base main --title "feat: Fase 1, fundación de SGSPE sobre wasichai" --body-file - <<'EOF'
Fase 1 (fundación) según `docs/roadmap.md` y el plan `docs/superpowers/plans/2026-10-07-fase-1-fundacion.md`.

- App `sgspe` sobre wasichai 0.3.2 (core, views, forms, pages), puerto 8094, PostGIS en el compose.
- `model/` con los 17 roles y el aprovisionamiento en orden, con sus pruebas.
- Pruebas de contrato con la plataforma, `X-Correlation-Id`, perfil de producción y `ArchitectureTest`.
- CI: build, integración con PostGIS, modelo, trazabilidad y formato.
- Verificación de la pista C con prueba de humo (Playwright MCP).

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF

cd /Users/jorge/IdeaProjects/sgspe-ui
git push -u origin feat/fase-1-fundacion
gh pr create --base main --title "feat: Fase 1, portal y administración de SGSPE" --body-file - <<'EOF'
Fase 1 (fundación) de la interfaz, según `docs/roadmap.md` de `sgspe` (pista B).

- `/admin` con `WasichaiApp` y login compartido con el portal (`sgspe.token`).
- Portal con `RequireSession`, `safeNext`, temas con CSP por hash, las 13 secciones con su fase, i18n y el Dashboard de F1.
- CI con `PACKAGES_TOKEN`, Dependabot y `boundaries.test`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_017MAgYcNauGp6cC3ekX1h7U
EOF
```

En el PR de `sgspe`, `--base main` se cambia por `--base fase-0/descubrimiento` si el comando anterior dijo `OPEN`.

- [ ] **Paso 11: CI en verde y cierre de la rama**

```bash
cd /Users/jorge/IdeaProjects/sgspe && gh pr checks --watch
cd /Users/jorge/IdeaProjects/sgspe-ui && gh pr checks --watch
```

Esperado: todos los trabajos en verde (`build`, `integration`, `model`, `traceability`, `format`, `commitlint` y
`pr-title` en `sgspe`; `Web`, `commitlint` y `pr-title` en `sgspe-ui`). Después, `superpowers:finishing-a-development-branch`
con el usuario.

---

## Autorrevisión del plan

### 1. Cobertura del spec

| Exigencia (`docs/roadmap.md`, F1, y documentos que cita) | Tarea |
|---|---|
| T0.1 exclusiones y rama `feat/fase-1-fundacion` en los dos repositorios; commit inicial de `sgspe-ui` en `main` | 1 (Pasos 1 a 3) |
| T0.2 `gh auth status` con `read:packages` | 1 (Paso 4) |
| T0.3 BD de desarrollo en el VPS: `compose.yml`, `docker compose up -d`, túnel `ssh -fN -L`, `sgspe_test` | 1 (Pasos 5 a 9) |
| T0.4 `PACKAGES_TOKEN` en Actions y Dependabot (lo carga el usuario) | 1 (Paso 10) |
| T0.5 spike de POI, Gotenberg y GetMap fuera de los repositorios, con hallazgos en ADR-0013 y ADR-0015 | 2 |
| A1 wrapper, build, `SgspeApplication`, `application.yml` en 8094, `SgspeSmokeTest`, `integrationTest` con PostGIS | 3 |
| A2 `compose.yml`, `develop/example.env`, `docs/develop/README.md`, sondas | 4 |
| A3 `package.json`, commitlint, husky, prettier, `.editorconfig` de 160 columnas | 3 (`.editorconfig`) y 5 |
| A4 `model/` de caja, `model.json` vacío, `roles.json` con 15 + `mappedToAdmin`, `AUDITOR` `"*"`, sin `ownRecordsOnly`, `u` y `"derived"`, orden de aprovisionamiento, «15 skipped» | 6 |
| A5 `SgspeApiTest` y `TenantIsolationApiTest`, `SecurityApiTest`, `RolesApiTest`, `AuditApiTest`, `ErrorContractApiTest` | 7 |
| A6 `CorrelationIdFilter` en `sgspe.observability`, 401 con identificador, contexto de Reactor, `application-prod.yml` (sin semilla, secreto, TTL < 8 h, ECS), `ObservabilityApiTest`, `ProdProfileTest` | 8 |
| A6 base de `sgspe.common` (`SgspeWrite`, envoltura de `RecordService`, 422 estricto, Lima) | 9 |
| A7 `ArchitectureTest` (sin `wasichai`, sin APIs internas, sin literales de rol, dominio sin tableros, entregables ni IA) | 10 |
| A8 `ci.yml`, `commits.yml`, `dependabot.yml` por SHA; `check_traceability.py` de la definición de terminado | 11 |
| A9 README, CLAUDE.md, HISTORY, Estado de las filas de F1 | 21 |
| B1 Vite 8 en 5182, proxy a 8094, `.npmrc` sin token, Tailwind con `@wasichai/ui/theme.css`, tsconfig, prettier, commitlint, husky, versiones de caja-ui | 12 |
| B2 `/admin` con `WasichaiApp`, `basename '/admin'`, views, forms y pages, `storagePrefix 'sgspe'`, sin `agentModule()` | 13 |
| B3 login compartido (`sgspe.token`), `RequireSession`, `safeNext` | 14 |
| B4 temas claro y oscuro, script de arranque único autorizado por hash en la CSP | 15 |
| B5 shell y árbol con las 13 secciones en orden y con su fase; hojas sin pantalla no se dibujan; permisos de `/api/auth/me/permissions`; Administración solo para admin | 16 |
| B6 i18n: español por defecto, inglés, números y fechas es-PE | 17 |
| B7 Dashboard de F1: usuario, organización, roles y mapa de módulos por fase | 18 |
| B8 CI con `PACKAGES_TOKEN`, `boundaries.test` | 19 |
| B9 README y CLAUDE.md | 21 |
| Pista C: aprovisionamiento en orden, smoke con Playwright MCP (login, Dashboard con organización y rol, 15 roles en `/admin`, `AUDITOR` con auditoría, sin Administración, 403 en usuarios, `X-Correlation-Id` también en un 401), suites completas | 20 |
| Push y PR con aprobación; `finishing-a-development-branch` | 21 (Pasos 10 y 11) |
| Definición de terminado: las once suites nombradas, trazabilidad de F1, cero nombres reales, `check_traceability.py` en la CI | 6, 7, 8, 10, 11, 19, 21 |
| Cambios 1 a 17 respecto del plan aprobado (los 18 a 25 son de F2 en adelante o de documentación ya hecha en F0) | 6 (1 a 4), 7 (5), 8 (6 y 7), 10 y 19 (8), 8 y 9 (9), 1 y 4 (10), 2 (11), 20 (12), 16 (15), 13 (16), 1 y global (17); el 13 y el 14 no piden trabajo en F1 |
| `security.md` §14, filas de F1: `TenantIsolationApiTest`, `SecurityApiTest`, `ProdProfileTest`, `RolesApiTest`, `test_apply_roles.py`, «Alta de un tenant» (`test_apply.py` y `TenantProvisioningApiTest`), `AuditApiTest`, `ErrorContractApiTest`, `ObservabilityApiTest`, `ArchitectureTest` y `boundaries.test` | 6, 7, 8, 10, 19 |

Fuera de F1 a propósito: `LoginRateLimitTest` (propuesto) prueba el proxy inverso, que no existe hasta que se responda
GAP-022; RQ-SEC-015 sigue como «Upstream (UPS-09)» en la Tarea 21. Las filas de `test_apply_roles.py` que dependen de
objetos (transiciones, `person`, `ai_suggestion`, `evidence_quote`) llegan con esos objetos, desde F2; la regla de `u` y
la de `"derived": true` ya se prueban en F1 con el modelo de `model/fixture_model.py`. Los issues upstream ya los abrió el
coordinador.

### 2. Marcadores pendientes

Revisado: ningún «TBD», «TODO» ni «similar a». Los únicos valores que se completan al ejecutar son datos del día y de la
corrida, con su comando: la fecha (`date +%F`) en `HISTORY.md` y el roadmap, y los resultados del spike (copiados de
`out/resultados.txt`) en las dos viñetas de los ADR, cuya forma y regla de decisión el plan fija.

### 3. Consistencia de nombres y tipos

- Backend: `sgspe.SgspeApplication`; `sgspe.SgspeApiTest` con `token`, `exchange`, `send`, `tree`, `field`,
  `createObject`, `createRecord`, `me`, `userWithRole`, `provisionTenant`, `runScript`, `Response`, `Account`, `ScriptRun`,
  `PASSWORD`, `REASON`, `JSON`, `reason`, usados igual en las Tareas 7, 8 y 9; `CorrelationId.HEADER`, `KEY`, `accept`,
  `current` en las Tareas 8 y 10; `SgspeWrite.Key`, `Records.create/update/get`, `LIMA`, `Clocks.clock()`,
  `UnprocessableContentException`, `StrictQuery.requireOnly` en las Tareas 9 y 10.
- Modelo: `apply.validate`, `apply.do_workflows`, `apply_roles.validate`, `permissions_payload`, `field_permissions_payload`
  y las salidas `done: …` iguales en las Tareas 6, 7 y 20; `FIXTURE_MODEL` en `model/fixture_model.py` para las dos pruebas.
- Interfaz: `client`, `NS`, `IDIOMAS`, `crearI18n`, `ajustarI18n`, `numeroPe`, `fechaPe`, `ES`, `EN`, `Textos`;
  `NAV_TREE`, `SeccionNav`, `ClaveDeSeccion`, `Fase`, `Oferta`, `Estado`, `conPantalla`, `estadoDe`, `arbolPara`,
  `useOferta`, `useArbol`; `PANTALLAS`; `GuardaDeSeccion`; `useCuenta`, `useOrganizacion`; en `src/test/portal.ts`,
  `RELACIONISTA`, `AUDITORA`, `ADMINISTRACION`, `SIN_PERMISOS`, `ADMIN_PERMISOS`, `yo`, `rutasDeSesion`, `abrirSesion`. Las
  claves de i18n que usa cada componente (`shell.secciones`, `shell.sinAcceso`, `shell.idioma`, `secciones.<clave>`,
  `inicio.*`) se agregan en la misma tarea o antes; `EN: Textos` hace que `typecheck` falle si falta una.
- Cuentas de las pruebas: 23 de contrato, 5 de observabilidad, 1 de `Records`, 3 de humo; 41 pruebas de Python en `model/` y
  6 en `tools/`; 11 archivos de prueba en `sgspe-ui` al final de la pista B.

### 4. Review Focus

| Falla | Prueba que la fija | Tarea |
|---|---|---|
| Redirección a otro host con `//`, `/\` o caracteres de control en `next` | `safeNext` en `RequireSession.test.tsx` (11 casos) y «va al inicio en lugar de a otro sitio» en `login.test.tsx` | 14 |
| `X-Correlation-Id` hostil o consulta con datos personales en el log | `CorrelationIdFilterTest` («reemplaza un valor sin formato…») y `ObservabilityApiTest` («una línea de acceso… sin la cadena de consulta») | 8 |
| Rol editado a mano en `/admin` | `test_deshace_lo_editado_a_mano_en_el_admin` en `test_apply_roles.py` | 6 |
| Sesión vencida a mitad de uso | «un 401 a mitad de sesión vuelve al login y después regresa a la misma ruta» en `RequireSession.test.tsx` | 14 |
| Segundo tenant | `TenantProvisioningApiTest` y `TenantIsolationApiTest` | 7 |

### 5. Validación del plan antes de entregarlo

El código del plan se probó en el scratchpad de la sesión, fuera de los repositorios:

- `model/`: los scripts copiados de caja con las ediciones de la Tarea 6, más `apply_roles.py`, `fixture_model.py`,
  `model.json`, `roles.json` y las dos pruebas: `Ran 41 tests … OK`; `apply.py --validate-only` y `apply_roles.py --dry-run`
  como se esperan. El estado «que falla» del Paso 7 también se comprobó.
- Backend: los archivos de las Tareas 3 y 7 a 10 compilan con wasichai 0.3.2; `ktlintFormat` dejó tres bloques reescritos,
  ya incorporados al plan; `build` pasa con 22 pruebas unitarias; `integrationTest` pasa con 32 pruebas contra un PostgreSQL 18
  local, incluido el aprovisionamiento con los scripts reales. Esa corrida encontró la decisión 10 (atributo desconocido
  ignorado), ya reflejada en `ErrorContractApiTest`.
- Interfaz: el estado final de la pista B, armado con las ediciones de las Tareas 12 a 19, pasa `vitest` (11 archivos, 60
  pruebas), `tsc` de `typecheck` y de `build`, `prettier --check` (los bloques que prettier reformateó ya están en el plan) y
  `vite build`; los estados intermedios de las Tareas 14 (4 archivos, 22 pruebas) y 15 (7 archivos, 34 pruebas) también.
- Spike: `Spike.kt` compila con POI 5.4.1 y PDFBox 3.0.8, `run --args=north` genera la flecha y las dos clases `Docx`
  generan documentos válidos (índice, `STYLEREF`, numeración, celdas combinadas y la marca en el encabezado). Gotenberg y
  GeoServer no se probaron: esa es la tarea del spike.

