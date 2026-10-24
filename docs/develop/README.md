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

Las librerías de wasichai (`wasichai:wasichai-bom:1.4.0` y sus starters) se descargan de GitHub Packages, que pide un
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
| `WASICHAI_GIS_GEOSERVER_ENABLED` | `false` | integración con GeoServer (F6, ADR-0015); apagada, `/api/gis/layers` lista las capas con `published=false`; encenderla exige el GeoServer del compose, en `localhost:8095` (sección «GeoServer») |
| `WASICHAI_CORE` | `http://localhost:8094` | URL de los scripts de `model/` |
| `WASICHAI_EMAIL` / `WASICHAI_PASSWORD` | el administrador de desarrollo | login de esos scripts |
| `WASICHAI_TEST_DB_*` | comentadas | pruebas de integración contra la base externa (sección 6) |
| `SGSPE_AI_ENABLED_ORGANIZATIONS` | vacía | slugs, separados por comas, de las organizaciones con la IA activada (F7, ASM-128); vacía, nadie |
| `ANTHROPIC_API_KEY` | nunca en un archivo | la clave del proveedor de IA (F7, GAP-015): solo exportada en el shell que arranca el servidor, nunca en `develop/.env`; sin ella la IA responde `503 AI_UNAVAILABLE` y el resto funciona |
| `WASICHAI_FILES_STORE` | `local` | dónde guarda wasichai-files los archivos de `evidence.file` (ADR-0014): `local` en `WASICHAI_FILES_LOCAL_PATH` (`build/wasichai-files`), o `s3` en el MinIO de `compose.yml`; el perfil `prod` exige `s3` (`ProdGuard`) |
| `WASICHAI_FILES_S3_BUCKET` / `_ENDPOINT` | `sgspe-evidencias` / `http://localhost:9095` | el bucket y la URL del MinIO de desarrollo; en producción, sin valor por defecto |
| `WASICHAI_FILES_S3_ACCESS_KEY` / `_SECRET_KEY` | sin valor por defecto | con `WASICHAI_FILES_STORE=s3` contra el MinIO local, cópielas de `develop/example.env` a `develop/.env`; sin ninguna de las dos, el SDK usa su cadena de credenciales (un rol IAM en producción), y con una sola `ProdGuard` impide el arranque |
| `SGSPE_MINIO_PORT` / `SGSPE_MINIO_CONSOLE_PORT` | `9095` / `9096` | puertos que publica `compose.yml` para la API y la consola de MinIO |
| `WASICHAI_NOTIFICATIONS_EMAIL_ENABLED` / `_FROM` | `false` / `avisos@sgspe.test` | el canal de correo de wasichai-notifications (GAP-021), apagado por defecto |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | `localhost` / `1029` | el SMTP de prueba (Mailpit) de `compose.yml`, por el túnel; el perfil `prod` no tiene servidor por defecto y, con el correo encendido, `ProdGuard` exige `SPRING_MAIL_HOST` |
| `SGSPE_SMTP_PORT` / `SGSPE_MAILPIT_PORT` | `1029` / `8029` | puertos que publica `compose.yml` para el SMTP y la web de Mailpit |
| `SGSPE_LATEX_PORT` | `8097` | desde el 2026-10-19 (ADR-0030), el puerto que publica `compose.yml` para `sgspe-latex` en `127.0.0.1` (sección «LaTeX (sgspe-latex)»); reemplaza a `SGSPE_GOTENBERG_PORT` |
| `SGSPE_DELIVERABLE_LATEX_URL` | `http://localhost:8097` | desde el 2026-10-19, la URL de `sgspe-latex` por el túnel (`sgspe.deliverable.latex.url`); sin él, los borradores quedan FALLIDO con `PDF_CONVERTER_UNAVAILABLE`. Reemplaza a `SGSPE_DELIVERABLE_GOTENBERG_URL` |
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
  entregarlos. GeoServer llega en F6 y, desde el 2026-10-19, `sgspe-latex`, que reemplaza al Gotenberg de F8 (sección
  «LaTeX (sgspe-latex)»).
- Empezar de cero: `docker compose down -v` borra el volumen y todos los datos.

### GeoServer

Desde F6 el compose trae el servicio `geoserver` (`docker.osgeo.org/geoserver:3.0.1`, contenedor `sgspe-geoserver`,
volumen `geoserver-data`, sin CORS): en el VPS escucha en `127.0.0.1:8095` (`SGSPE_GEOSERVER_PORT`, contenedor 8080) y
se alcanza por el túnel `ssh -fN -L 8095:localhost:8095 "$SGSPE_VPS"`. Credenciales de desarrollo: `sgspe` /
`sgspe-geoserver`. El workspace es `sgspe` y el datastore `sgspe-postgis` apunta a `postgis:5432/sgspe` con el rol de
solo lectura `sgspe_geoserver`, que se crea una vez en la base de desarrollo (`security.md` §10.2, regla 6):

```bash
docker exec -i sgspe-postgis psql -U sgspe -d sgspe -v ON_ERROR_STOP=1 <<'SQL'
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sgspe_geoserver') THEN
    CREATE ROLE sgspe_geoserver LOGIN PASSWORD 'sgspe-geoserver-lectura';
  END IF;
END $$;
GRANT CONNECT ON DATABASE sgspe TO sgspe_geoserver;
GRANT USAGE ON SCHEMA app_data, public TO sgspe_geoserver;
GRANT SELECT ON ALL TABLES IN SCHEMA app_data TO sgspe_geoserver;
ALTER DEFAULT PRIVILEGES FOR ROLE sgspe IN SCHEMA app_data GRANT SELECT ON TABLES TO sgspe_geoserver;
SQL
```

| Variable | Ejemplo | Uso |
|---|---|---|
| `WASICHAI_GIS_GEOSERVER_ENABLED` | `false` | enciende la integración; con ella, las capas públicas se publican y el proxy WMS responde |
| `WASICHAI_GIS_GEOSERVER_URL` | `http://localhost:8095/geoserver` | la URL de GeoServer, por el túnel |
| `WASICHAI_GIS_GEOSERVER_USERNAME` / `_PASSWORD` | `sgspe` / `sgspe-geoserver` | credenciales de la REST de GeoServer |
| `WASICHAI_GIS_GEOSERVER_DATASTORE_HOST` / `_PORT` / `_DATABASE` | `postgis` / `5432` / `sgspe` | la base que lee el datastore, vista desde el contenedor |
| `WASICHAI_GIS_GEOSERVER_DATASTORE_USERNAME` / `_PASSWORD` | `sgspe_geoserver` / `sgspe-geoserver-lectura` | el rol de solo lectura |

Las capas públicas se publican con `publish_layers.py`, después de `load_ubigeo.py` (sección 5).

### LaTeX (sgspe-latex)

Desde el 2026-10-19 (ADR-0030, ASM-139) el PDF de los entregables se compone con LuaLaTeX en el servicio `latex`
(contenedor `sgspe-latex`), que reemplaza a Gotenberg. La imagen es propia y se construye desde
`develop/latex/Dockerfile`: `debian:trixie-slim` fijada por digest, TeX Live de Debian con `latexmk`, Source Sans 3 de
Debian, Source Serif 4 con SHA-256 fijo y un envoltorio HTTP en Python de biblioteca estándar. Corre sin volúmenes, con
el sistema de archivos de solo lectura y un tmpfs, un usuario sin privilegios y sin red de salida en producción (red interna); compone sin
`shell-escape`, con `--nosocket`, `openout_any=p`, y a lo más dos composiciones a la vez (`LATEX_CONCURRENCY`). Se construye y se
levanta en el Docker remoto con:

```bash
DOCKER_HOST=unix:///tmp/docker.sock docker compose -p sgspe up -d --build latex
```

En el VPS escucha en `127.0.0.1:8097` (`SGSPE_LATEX_PORT`) y se alcanza por el túnel
`ssh -fN -L 8097:localhost:8097 "$SGSPE_VPS"`. Solo lo llama JOB-05 y nunca es público (ADR-0026). El backend lo
encuentra con `SGSPE_DELIVERABLE_LATEX_URL=http://localhost:8097` (`sgspe.deliverable.latex.url`, con plazo
`sgspe.deliverable.latex.timeout` de 180 s); `application-prod.yml` no le da valor por defecto. El puerto 8096, que usaba
Gotenberg, queda libre y no se reutiliza en esta fase.

Las pruebas que componen con LuaLaTeX llevan la etiqueta `latex` (`LatexPdfTest`, que reemplaza a `GotenbergPdfTest`) y
corren en la CI, donde un paso del trabajo de integración construye la imagen y la levanta; aquí corren con el túnel
abierto y la misma variable. La suite lenta (`slowTest`, a pedido) pide GetMap a un GeoServer real y genera un T1 de
unas 100 páginas con 40 fotos en menos de 180 s por pedido:

```bash
SGSPE_DELIVERABLE_LATEX_URL=http://localhost:8097 WASICHAI_GIS_GEOSERVER_URL=http://localhost:8095/geoserver \
  GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) ./gradlew slowTest --rerun
```

### Láminas (sgspe-slides)

Desde el 2026-10-20 (ADR-0031, ASM-144) las presentaciones de una versión emitida se componen con Remotion en el servicio
`slides` (contenedor `sgspe-slides`). El paquete `slides/` (TypeScript, yarn, Node 24) tiene la especificación de una
presentación (`src/spec/schema.ts`, zod), los tokens del sistema visual (`src/design/`), los layouts, la verificación de
cada lámina (`src/checks.ts`), el servidor (`server/`) y las fuentes OFL vendidas (`fonts/`). Se prueba sin Docker:

```bash
cd slides && yarn install && yarn typecheck && yarn test
# con Chrome headless-shell: compone a PDF las presentaciones de demostración y los casos que deben fallar
npx remotion browser ensure && SLIDES_RENDER=1 yarn test
```

**La revisión visual** (`deliverables.md` §16.9) es obligatoria antes de mezclar un cambio del sistema visual: compone cada
lámina a PNG y una hoja de contacto, y se mira con tres públicos que contrasten (`fixtures/demo-t1-*.json`):

```bash
cd slides && yarn review fixtures/demo-t1-direccion.json /tmp/revision
```

La imagen (`slides/Dockerfile`, dos etapas sobre `node:24-bookworm-slim` fijada por digest) prepara el bundle y el Chrome
headless-shell al construir y corre sin red, de solo lectura, con tmpfs en `/tmp` y `/dev/shm`, sin capacidades y como
usuario sin privilegios. Se construye y se levanta en el Docker remoto con:

```bash
DOCKER_HOST=unix:///tmp/docker.sock docker compose -p sgspe up -d --build slides
```

En el VPS escucha en `127.0.0.1:8099` (`SGSPE_SLIDES_PORT`) y se alcanza por el túnel
`ssh -fN -L 8099:localhost:8099 "$SGSPE_VPS"`. `POST /render` recibe la especificación en JSON y devuelve el PDF
(`X-Slides-Count`, `X-Trace`); un error responde `{code, slide}` sin texto del documento: `400 SPEC_INVALID`, `413
TOO_LARGE`, `422 SLIDE_OVERFLOW`, `SLIDE_OVERLAP`, `TEXT_TOO_SMALL`, `SLIDE_EMPTY`, `FONT_MISSING` o `ASSET_INVALID`,
`504 TIMEOUT` y `500 RENDER_FAILED` o `INTERNAL`. Variables: `SLIDES_TIMEOUT` (170 s), `SLIDES_CONCURRENCY` (2),
`SLIDES_MAX_BYTES` (64 MB). Sin `licenseKey`: la organización usa la licencia Free de Remotion (ADR-0031).

El backend lo encuentra con `SGSPE_DELIVERABLE_SLIDES_URL=http://localhost:8099` (`sgspe.deliverable.slides.url`, con
plazo `sgspe.deliverable.slides.timeout` de 180 s y el sondeo de JOB-09 en `sgspe.deliverable.slides.poll`);
`application-prod.yml` no le da valor por defecto, y sin él cada presentación queda FALLIDO con `SLIDES_UNAVAILABLE`. Las
pruebas que componen con el servicio real llevan la etiqueta `slides` (`SlidesPdfTest`) y corren en el trabajo de
integración de la CI; aquí, con el túnel abierto o el servidor local (`yarn serve` con `SLIDES_BUNDLE` y `SLIDES_PORT`):

```bash
SGSPE_DELIVERABLE_SLIDES_URL=http://localhost:8099 GITHUB_ACTOR=$(gh api user -q .login) GITHUB_TOKEN=$(gh auth token) \
  ./gradlew integrationTest --tests 'sgspe.deliverable.SlidesPdfTest'
```

Las especificaciones de demostración (`slides/fixtures/generated/`) las escribe `DeckPlannerTest` con datos sintéticos
(`DeckDemo`); si el planificador cambia, se regeneran con `./gradlew test --tests 'sgspe.deliverable.DeckPlannerTest'
-Dsgspe.decks.write=true` y pasan por la revisión visual.

### GDAL

La importación y la exportación de Shapefile, KML, KMZ, GeoPackage, FileGDB y GPX usan `ogrinfo` y `ogr2ogr` 3.8 o
superior (3.13.3 en la máquina de desarrollo, en `/opt/homebrew/bin`). `SGSPE_GDAL_PATH` fija la carpeta de los
ejecutables cuando no están en el `PATH`. Sin GDAL, o sin el driver del formato, esos formatos responden
`422 FORMAT_UNAVAILABLE`; XLSX, CSV y GeoJSON siguen funcionando.

El mapa base de desarrollo sale de `SGSPE_MAP_BASE_STYLE_URL` (por defecto `https://demotiles.maplibre.org/style.json`).

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
python3 apply.py                        # objetos, acciones, relaciones e índices. En F8 (91 objetos): "done: 431 created, 30 updated, 0 skipped"
python3 apply_roles.py                  # los 15 roles declarados con sus permisos y campos restringidos. La segunda vez: "15 skipped"
python3 apply.py --workflows            # 19 workflows, 3 tipos de documento y 7 automatizaciones de F8
python3 load_ubigeo.py                  # el ubigeo del INEI: 25 departamentos, 196 provincias y 1892 distritos
python3 load_indicators.py              # desde F8 (plan 8.2a): el catálogo indicator, sin pisar metas editadas
python3 publish_layers.py               # desde F6: capas públicas en GeoServer (upgrade_tenants.py: --skip-layers)
python3 -m unittest -v                  # las pruebas de los scripts, contra un core falso, y del modelo contra docs/
```

- `roles.json` es la fuente de verdad: volver a correr `apply_roles.py` deshace lo editado a mano en `/admin`.
- `SUPER_ADMIN` y `ORGANIZATION_ADMIN` no se declaran: son el `ADMIN` de wasichai (`mappedToAdmin`).
- Un tenant nuevo se prepara con `POST /api/organizations` y después estos cinco pasos, con el administrador del tenant.
  Los cinco son idempotentes: una subida de fase los repite. Desde el plan 4.4 lo hacen dos scripts (ASM-109):

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

### Conjunto de demostración (T1 y T2)

`model/demo/NN-*.json` es un conjunto **ficticio** (personas, organizaciones y lugares inventados; correos
`@sgspe.test`) que `model/load_demo.py` carga por REST para que los borradores de los entregables T1 (diagnóstico social)
y T2 (informe de campo y avance, #153) salgan con datos en un entorno de demostración. El T2 (`07-entregable-t2.json`)
es de la Comunidad Campesina San Isidro de Wayra, del 1 al 20 de octubre de 2026: sus visitas, interacciones (una
cancelada), redes con sus integrantes, sectores comunales y un incidente de antecedentes. Todo lo que suma cae después
del corte del T1 (2026-09-30), así que no cambia lo que el T1 cuenta; no trae apoyo entregado, porque la entrega de un
aporte exige evidencias con archivo y el cargador no sube archivos. No es parte del aprovisionamiento: no va en `tenant_steps` ni se corre en un
tenant de producción.

- **Orden:** después de `load_indicators.py` (cierra periodos con el catálogo de indicadores) y con el administrador del
  tenant. Los usuarios de la demostración se crean con la contraseña de `SGSPE_DEMO_PASSWORD` (o `--users-password`); nunca
  va en un archivo.
- **Validar sin core:** `python3 load_demo.py --validate-only` comprueba los archivos contra `model.json` (objetos,
  atributos, derivados, enums, obligatorios, vínculos, comandos, correos y el grupo de influencia esperado) y sale con 2
  si hay problemas; `--dry-run` valida e imprime los conteos por objeto.
- **Cargar:** `SGSPE_DEMO_PASSWORD=… python3 load_demo.py --core URL --email … --password …`. Es idempotente: un registro
  que ya existe (por su `match`) no se actualiza y sus comandos corren de nuevo, cada uno idempotente por su cuenta.
- **`--draft`:** pide además el borrador de los entregables (`POST /api/sgspe/deliverables/{id}/drafts`); sin él solo se
  cargan los datos y las secciones.
- **`--issue`** (solo en desarrollo): pide los borradores como `--draft` y, al final de la carga, lleva cada entregable
  con el comando `issue` por WF-H hasta `issued`: espera su borrador LISTO (`--issue-timeout`, 1200 s por defecto), lo
  envía a revisión y lo emite quien escribe la colección (gestión social) y lo aprueba otro usuario de la demostración
  (`approve_as`: gestión de actores), con `POST /api/sgspe/deliverables/{id}/issue`, el SHA-256 del modelo revisado y la
  lista de verificación confirmada. Retoma desde el estado en que esté (`observed` se reenvía), no toca un entregable ya
  emitido y corta sin transiciones ante un borrador FALLIDO o con `MAP_FAILED`, que la emisión rechaza: necesita
  `sgspe-latex` y GeoServer (los túneles 8097 y 8095), así que no corre contra el servidor de `seed-demo-data.sh`.
- **`--workers N`** (o `SGSPE_DEMO_WORKERS`; 1 por defecto): carga hasta N registros de una colección a la vez, en lotes
  que se cortan antes del registro que nombra con `@clave` a otro de su mismo lote. Los hijos, vínculos, transiciones y
  comandos de un registro siguen en orden. **Carrera conocida:** el corte solo ve las dependencias que el conjunto
  declara con `@clave`. Una guarda o un listener que lea otros registros de la misma colección sin nombrarlos (un tope,
  una unicidad o un conteo entre pares) queda en carrera con N > 1. El tope de apoyo de la campaña ya toma su candado;
  ante una regla nueva de ese tipo, cargar con `--workers 1`.
- **Límite conocido:** si el proceso muere entre la creación de un registro y sus transiciones, la segunda corrida falla
  con «state X, expected Y» (el registro ya existe, así que sus transiciones se saltan) y hay que corregir ese registro a
  mano. Si muere entre el alta y sus hijos o vínculos, la segunda corrida tampoco los crea, pero avisa
  («warning: <clave> has N <objeto o relación>, declares M») cuando core tiene menos hijos de un objeto o menos vínculos
  de una relación que los declarados; el aviso no compara el contenido, solo el número.

### Uso de las acciones de las fichas

`uso_de_acciones.py` lee la auditoría de un periodo (`GET /api/audit` por cursor) y cuenta cuántas veces las personas dieron
cada transición o acción de una ficha, y si el portal la ofrece a la vista (la primaria) o en «Más acciones»
(sgspe-ui#75). Sirve para decidir si algo del menú debe volver a la vista. Necesita una cuenta que lea la auditoría de todo
el tenant (ADMIN o AUDITOR) y no escribe nada.

```bash
cd model
python3 uso_de_acciones.py --core URL --email … --password … --desde 2026-07-01 --hasta 2026-10-01
```

Si cambia `CAMINO_DE` en sgspe-ui (`src/portal/fichas/siguienteAccion.ts`), se cambia también `CAMINO` en el script.

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
11; `DemoDiagnosisApiTest` (carga el conjunto de demostración dos veces con `--workers 4` y genera su T1), unos 22 minutos
(1357 s el 2026-10-18; en serie, la primera carga no entraba en los 20 minutos por script); `PermissionMatrixApiTest`, la
más larga (ver abajo).

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

   La matriz de permisos (`sgspe.security.PermissionMatrixApiTest`) recorre 15 roles por 90 objetos y por el túnel tarda
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
