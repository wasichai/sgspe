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

**Secreto de Dependabot (T0.4).** En la CI de `sgspe`, el job lee GitHub Packages con `GITHUB_TOKEN` (`packages: read`) y no
necesita secreto. Dependabot no recibe `GITHUB_TOKEN`: para que sus actualizaciones de Gradle puedan leer
`wasichai/wasichai`, el repositorio `wasichai/sgspe` necesita `PACKAGES_TOKEN` (un PAT con `read:packages`) como secreto de
**Dependabot** (Settings > Secrets and variables > Dependabot, no Actions), el que lee `.github/dependabot.yml`. Sin él, esas
actualizaciones fallan. Lo carga el usuario, igual que el de `sgspe-ui`.

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
| `WASICHAI_SEED_DEV` | `true` | crea `admin@wasichai.local` / `admin`; producción lo apaga, y con el perfil `prod` `true` impide el arranque (`ProdGuard`) |
| `WASICHAI_GIS_GEOSERVER_ENABLED` | `false` | integración con GeoServer (F6, ADR-0015); apagada, `/api/gis/layers` lista las capas con `published=false`; encenderla exige un GeoServer en `localhost:8081` |
| `WASICHAI_CORE` | `http://localhost:8094` | URL de los scripts de `model/` |
| `WASICHAI_EMAIL` / `WASICHAI_PASSWORD` | el administrador de desarrollo | login de esos scripts |
| `WASICHAI_TEST_DB_*` | comentadas | pruebas de integración contra la base externa (sección 6) |

`WASICHAI_DB_*`, `WASICHAI_JWT_SECRET` y `WASICHAI_SEED_DEV` tienen el mismo valor por defecto en
`src/main/resources/application.yml`: con la base del compose y el túnel, el servidor arranca sin cargar nada. Las demás
las leen `compose.yml` y los scripts de `model/`; las `WASICHAI_TEST_DB_*`, las pruebas de integración.

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
python3 apply.py --validate-only        # el modelo y los workflows cumplen las reglas de wasichai (no necesita servidor)
python3 apply.py                        # objetos, acciones, relaciones e índices. En F2: "done: 168 created, 11 updated, 0 skipped"
python3 apply_roles.py                  # los 15 roles declarados con sus permisos y campos restringidos. La segunda vez: "15 skipped"
python3 apply.py --workflows            # los 5 workflows de F2: "done: 5 workflows (5 created, 0 updated, 0 skipped)"
python3 load_ubigeo.py                  # el ubigeo del INEI: 25 departamentos, 196 provincias y 1892 distritos
python3 -m unittest -v                  # las pruebas de los scripts, contra un core falso, y del modelo contra docs/
```

- `roles.json` es la fuente de verdad: volver a correr `apply_roles.py` deshace lo editado a mano en `/admin`.
- `SUPER_ADMIN` y `ORGANIZATION_ADMIN` no se declaran: son el `ADMIN` de wasichai (`mappedToAdmin`).
- Un tenant nuevo se prepara con `POST /api/organizations` y después estos cuatro pasos, con el administrador del tenant.
  Los cuatro son idempotentes: una subida de fase los repite.
- `model.json` y `roles.json` se editan a mano y `test_model.py` y `test_apply_roles.py` los comparan con
  `database-model.md` y `security.md`: si un documento cambia, la prueba dice qué falta.
- El CSV del INEI trae una fila sin código (`NA`, distrito San Antonio, provincia Mariscal Nieto, Moquegua): `load_ubigeo.py`
  la informa y la salta.

## 6. Pruebas

```bash
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew ktlintFormat   # formatea el Kotlin
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew build          # ktlint y pruebas unitarias
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew compileTestKotlin
```

Las pruebas de integración llevan `@Tag("integration")` (lo heredan de `WasichaiIntegrationTest`): `build` las excluye e
`integrationTest` las corre. Levantan la app entera y aprovisionan el modelo con los scripts reales de `model/`, así que
necesitan `python3` en el `PATH`.

Con el modelo de F2, el primer `@BeforeEach` de la corrida aprovisiona la organización sembrada con los scripts de
`model/`: por el túnel, de 5 a 8 minutos (medido el 2026-10-07). Ese aprovisionamiento corre `apply.py`,
`apply_roles.py` y `apply.py --workflows`, pero no `load_ubigeo.py`: la prueba que lo necesita lo carga ella misma.
`SgspeApiTest.SCRIPT_TIMEOUT_SECONDS` da 20 minutos a cada script. La suite completa por el túnel tarda de 30 minutos a más de una hora, según la carga del túnel; por clase, medido:
`TenantProvisioningApiTest`, entre 5,5 y 13 minutos; `WorkflowMatrixApiTest` (sale de `model/workflows/*.json`), de 8 a
11; `PermissionMatrixApiTest`, la más larga (ver abajo).

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

   La matriz de permisos (`sgspe.security.PermissionMatrixApiTest`) recorre 15 roles por 48 objetos y por el túnel tarda
   entre 11 y 58 minutos según la carga del túnel (una corrida de Gradle midió 57 min 40 s), con el aprovisionamiento del
   tenant incluido. Para depurar
   un cambio de `model/roles.json`, recórtala a unos roles con `-Dmatrix.roles=FIELD_AGENT,GIS_ANALYST` (separados por
   comas; sin ella corren todos): con 3 roles y la prueba del `apiOnly` del administrador tardó unos 9,5 minutos, con el
   aprovisionamiento incluido. La corrida completa queda para la CI.

   ```bash
   GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.security.PermissionMatrixApiTest' -Dmatrix.roles=FIELD_AGENT
   ```

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
