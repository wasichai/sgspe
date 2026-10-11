# Desarrollo local

Cómo preparar, iniciar y probar `sgspe` en una máquina de desarrollo. La descripción del proyecto está en el
[README principal](../../README.md) y el diseño en [`docs/`](../README.md). Cómo se versiona y se publica un release está en
[`releasing.md`](releasing.md).

## 1. Requisitos

| Herramienta | Versión | Para |
|---|---|---|
| JDK | 25 | el servidor (toolchain de Gradle) |
| Docker | cualquiera reciente, local o remoto | PostGIS (`compose.yml`) |
| Python | 3.11 o superior, solo biblioteca estándar | los scripts de `model/` |
| Node y Yarn | Node 26 o superior, Yarn 1 | commitlint, husky y prettier; la interfaz es `../sgspe-ui` |
| GitHub CLI | con el alcance `read:packages` | el token de GitHub Packages |

Las librerías de wasichai (`wasichai:wasichai-bom:1.0.0` y sus starters) se descargan de GitHub Packages, que pide un
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
| `WASICHAI_FILES_STORE` | `local` | dónde guarda wasichai-files los archivos de `evidence.file` (ADR-0014): `local` en `WASICHAI_FILES_LOCAL_PATH` (`build/wasichai-files`), o `s3` en el MinIO de `compose.yml`; el perfil `prod` exige `s3` (`ProdGuard`) |
| `WASICHAI_FILES_S3_BUCKET` / `_ENDPOINT` | `sgspe-evidencias` / `http://localhost:9095` | el bucket y la URL del MinIO de desarrollo; en producción, sin valor por defecto |
| `WASICHAI_FILES_S3_ACCESS_KEY` / `_SECRET_KEY` | sin valor por defecto | con `WASICHAI_FILES_STORE=s3` contra el MinIO local, cópielas de `develop/example.env` a `develop/.env`; sin ninguna de las dos, el SDK usa su cadena de credenciales (un rol IAM en producción), y con una sola `ProdGuard` impide el arranque |
| `SGSPE_MINIO_PORT` / `SGSPE_MINIO_CONSOLE_PORT` | `9095` / `9096` | puertos que publica `compose.yml` para la API y la consola de MinIO |
| `WASICHAI_NOTIFICATIONS_EMAIL_ENABLED` / `_FROM` | `false` / `avisos@sgspe.test` | el canal de correo de wasichai-notifications (GAP-021), apagado por defecto |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | `localhost` / `1029` | el SMTP de prueba (Mailpit) de `compose.yml`, por el túnel; el perfil `prod` no tiene servidor por defecto y, con el correo encendido, `ProdGuard` exige `SPRING_MAIL_HOST` |
| `SGSPE_SMTP_PORT` / `SGSPE_MAILPIT_PORT` | `1029` / `8029` | puertos que publica `compose.yml` para el SMTP y la web de Mailpit |
| `SGSPE_TEST_S3_ENDPOINT` / `_ACCESS_KEY` / `_SECRET_KEY` | comentadas | el MinIO de `FileStoreContractTest` cuando Testcontainers no alcanza el Docker remoto (sección 6) |

`WASICHAI_DB_*`, `WASICHAI_JWT_SECRET` y `WASICHAI_SEED_DEV` tienen el mismo valor por defecto en
`src/main/resources/application.yml`: con la base del compose y el túnel, el servidor arranca sin cargar nada. Las demás
las leen `compose.yml` y los scripts de `model/`; las `WASICHAI_TEST_DB_*`, las pruebas de integración.

## 3. Base de datos

El Docker de desarrollo es remoto: `DOCKER_HOST` es un socket reenviado por SSH al VPS. El contenedor corre allá y publica
su puerto en el loopback del VPS; a esta máquina llega por un túnel.

```bash
docker compose up -d                               # postgis, MinIO (crea el bucket sgspe-evidencias) y Mailpit
docker compose ps                                  # postgis y minio deben decir healthy; minio-init termina con 0
ssh -fN -L 5435:localhost:5435 -L 9095:localhost:9095 -L 9096:localhost:9096 \
    -L 1029:localhost:1029 -L 8029:localhost:8029 "$SGSPE_VPS"   # el alias SSH del VPS
docker compose exec postgis createdb -U sgspe sgspe_test   # una sola vez: la base de las pruebas externas
```

- `compose.yml` usa un volumen con nombre y ningún bind mount, porque el demonio es remoto.
- Al arrancar, el servidor crea el esquema con las migraciones de wasichai. SGSPE no tiene migraciones propias en F1.
- Los servicios de apoyo llegan con su fase. Desde F3: MinIO (`sgspe-minio`, API en 9095 y consola en 9096, usuario
  `sgspe` / `sgspe-minio`) guarda los archivos de las evidencias con `WASICHAI_FILES_STORE=s3`, y Mailpit (`sgspe-mailpit`,
  SMTP en 1029 y web en 8029) recibe los correos de las notificaciones con `WASICHAI_NOTIFICATIONS_EMAIL_ENABLED=true`, sin
  entregarlos. GeoServer llega en F6 y Gotenberg en F8.
- Empezar de cero: `docker compose down -v` borra el volumen y todos los datos.

## 4. Iniciar el backend

```bash
set -a; source develop/.env; set +a
GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew bootRun
curl -s http://localhost:8094/actuator/health/liveness     # {"status":"UP"}
curl -s http://localhost:8094/actuator/health/readiness    # {"status":"UP"}
```

La clase principal es `sgspe.SgspeApplication`. Toda respuesta trae `X-Correlation-Id` (sección 7).

### Feriados

El acuse de una queja cuenta días hábiles con los feriados nacionales de `sgspe.calendar.holidays` en
`src/main/resources/application.yml` (ASM-096). La lista se configura por año, cuando se publica la oficial en El Peruano
(D. Leg. 713 y sus modificatorias); hoy trae 2026 y 2027. Para agregar un año:

1. Sumar su clave entre comillas bajo `holidays` (`'2028':`) con una fecha `AAAA-MM-DD` por línea, solo feriados
   nacionales: los días no laborables del sector público no lo son.
2. Correr `BusinessDaysTest`, que valida todo año configurado, y desplegar.

Mientras un año falte, cuenta de lunes a viernes y el log lo advierte una vez por año y proceso (y al arrancar, si falta
el año en curso). Una fecha de otro año o repetida impide arrancar. Los plazos ya calculados no se recalculan.

## 5. Modelo y roles

El modelo vive en `model/` y se aplica por REST, con el servidor arriba, siempre en este orden (`security.md` §4.1):

```bash
cd model
python3 apply.py --validate-only        # el modelo, los workflows, los tipos de documento y las automatizaciones (sin servidor)
python3 apply.py                        # objetos, acciones, relaciones e índices. En F5: "done: 380 created, 26 updated, 0 skipped"
python3 apply_roles.py                  # los 15 roles declarados con sus permisos y campos restringidos. La segunda vez: "15 skipped"
python3 apply.py --workflows            # 17 workflows, 2 tipos de documento y 6 automatizaciones de F5
python3 load_ubigeo.py                  # el ubigeo del INEI: 25 departamentos, 196 provincias y 1892 distritos
python3 -m unittest -v                  # las pruebas de los scripts, contra un core falso, y del modelo contra docs/
```

- `roles.json` es la fuente de verdad: volver a correr `apply_roles.py` deshace lo editado a mano en `/admin`.
- `SUPER_ADMIN` y `ORGANIZATION_ADMIN` no se declaran: son el `ADMIN` de wasichai (`mappedToAdmin`).
- Un tenant nuevo se prepara con `POST /api/organizations` y después estos cuatro pasos, con el administrador del tenant.
  Los cuatro son idempotentes: una subida de fase los repite. Desde el plan 4.4 lo hacen dos scripts (ASM-109):

  ```bash
  # alta: como la persona del operador con MANAGE_TENANTS; las contraseñas, por variable o por consola
  SGSPE_OPERATOR_PASSWORD=… SGSPE_TENANT_ADMIN_PASSWORD=… python3 provision_tenant.py --core https://… \
    --operator-email operador@… --name "Cliente Ejemplo" --slug cliente-ejemplo --admin-email admin-cliente-ejemplo@…
  # subida de fase de cada tenant del inventario (no versionado; la forma, en tenants.example.json)
  SGSPE_TENANT_PASSWORD_CLIENTE_EJEMPLO=… python3 upgrade_tenants.py --core https://… --inventory ~/tenants.json
  ```

  Un tenant que falla en `upgrade_tenants.py` se informa y no detiene a los demás; la salida es 1 y el resumen dice qué
  paso falló en cuál. El administrador de un tenant nuevo cambia la contraseña inicial en su primer acceso.
- `model.json` y `roles.json` se editan a mano y `test_model.py` y `test_apply_roles.py` los comparan con
  `database-model.md` y `security.md`: si un documento cambia, la prueba dice qué falta.
- El CSV del INEI trae una fila sin código (`NA`, distrito San Antonio, provincia Mariscal Nieto, Moquegua): `load_ubigeo.py`
  la informa y la salta.

### Varios clientes: la siembra de `MANAGE_TENANTS` (ASM-109)

En producción `wasichai.organizations.separate-provisioning` está encendido (`application-prod.yml`): crear y borrar
tenants exige `MANAGE_TENANTS`, que nadie tiene hasta que quien opera la base siembra el primer titular (WAS-ADR-0055). Se
hace **una sola vez, antes del primer despliegue con el interruptor encendido**, para el rol `ADMIN` de la organización
operadora (la del `SUPER_ADMIN`). Nunca va en `roles.json`, que se aplica a cada tenant cliente, y ninguna cuenta de
servicio la recibe:

```sql
INSERT INTO <esquema de metadata>.permissions (role_id, object_id, action)
SELECT r.id, NULL, 'MANAGE_TENANTS'
FROM <esquema de metadata>.roles r JOIN <esquema de metadata>.organizations o ON o.id = r.organization_id
WHERE o.slug = '<slug de la organización operadora>' AND r.name = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM <esquema de metadata>.permissions p WHERE p.role_id = r.id AND p.action = 'MANAGE_TENANTS');
```

El esquema de metadata es `wasichai.database.metadata-schema`. Con el interruptor encendido y sin la fila, nadie crea
tenants hasta sembrarla. Comprobación: `GET /api/auth/me/permissions` del `ADMIN` operador lista `MANAGE_TENANTS` en
`capabilities`, y el de un cliente no. `SeparateProvisioningApiTest` prueba lo mismo.

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

   La matriz de permisos (`sgspe.security.PermissionMatrixApiTest`) recorre 15 roles por 82 objetos y por el túnel tarda
   entre 11 y 58 minutos según la carga del túnel (una corrida de Gradle midió 57 min 40 s), con el aprovisionamiento del
   tenant incluido. Para depurar
   un cambio de `model/roles.json`, recórtala a unos roles con `-Dmatrix.roles=FIELD_AGENT,GIS_ANALYST` (separados por
   comas; sin ella corren todos): con 3 roles y la prueba del `apiOnly` del administrador tardó unos 9,5 minutos, con el
   aprovisionamiento incluido. La corrida completa queda para la CI.

   ```bash
   GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.security.PermissionMatrixApiTest' -Dmatrix.roles=FIELD_AGENT
   ```

   `FileStoreContractTest` prueba el almacenamiento S3 contra un MinIO: en la CI lo levanta Testcontainers; aquí, con el
   MinIO de `compose.yml` y su túnel, se le pasa por variables (la base no le hace falta):

   ```bash
   SGSPE_TEST_S3_ENDPOINT=http://localhost:9095 SGSPE_TEST_S3_ACCESS_KEY=sgspe SGSPE_TEST_S3_SECRET_KEY=sgspe-minio \
     GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew integrationTest --rerun --tests 'sgspe.contract.FileStoreContractTest'
   ```

   En la integración, los archivos de wasichai-files van a `build/wasichai-files` (`wasichai.files.store=local`), y el bucle
   de notificaciones y la limpieza de archivos no corren (`build.gradle.kts`, `integrationDefaults`).

## 7. Observabilidad

- `X-Correlation-Id`: se acepta el del cliente si cumple `^[A-Za-z0-9._-]{1,64}$`; si no, se genera un UUID. Va en toda
  respuesta, también en un `401`, y en la línea de acceso (`sgspe.access`): método, ruta sin la cadena de consulta, estado,
  duración e identificador.
- Producción (`application-prod.yml`): sin semilla, `WASICHAI_JWT_SECRET` obligatorio, token de 4 horas y logs JSON ECS, más revocación de tokens, límite de intentos de login y política de contraseñas
  ([Seguridad §3.2](../security.md#32-endurecimiento-de-sgspe)). En producción toda contraseña, incluida `adminPassword` al
  aprovisionar una organización, debe cumplirla: 12 caracteres o más, con mayúscula, minúscula, dígito y símbolo, y distinta del correo.

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
