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

**Fase 1, fundación:** en verde en local y en la CI de los dos repositorios (ver [`docs/HISTORY.md`](docs/HISTORY.md)). La Fase 2 trae los primeros objetos de
negocio: proyectos, territorio, grupos de interés y relacionamiento ([`docs/roadmap.md`](docs/roadmap.md)).

- La app `sgspe` corre sobre wasichai 0.6.0 con los starters core, views, forms, pages, gis, workflow y, desde F3,
  automation, documents, files y notifications.
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
cd model && python3 apply.py && python3 apply_roles.py && python3 apply.py --workflows && python3 load_ubigeo.py
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
- [Releases](docs/develop/releasing.md)
- [Decisiones de arquitectura](docs/adr/README.md)
- [Historia de cambios](docs/HISTORY.md)
