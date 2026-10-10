# Releases

Cómo se versiona y se publica `sgspe`. Sigue el mismo esquema que
[wasichai](https://github.com/wasichai/wasichai) (release-please sobre Conventional Commits), con una diferencia: `sgspe`
es una aplicación, no un conjunto de librerías, así que un release adjunta el jar ejecutable en lugar de publicar paquetes
Maven. La interfaz [`sgspe-ui`](https://github.com/wasichai/sgspe-ui) tiene su propia línea de versiones
([ADR-0010](../adr/0010-repositorios-sgspe-y-sgspe-ui.md)).

## Flujo

1. Se mezclan a `main` commits convencionales (`feat:`, `fix:`, …). `commits.yml` los verifica en cada pull request, igual
   que el título del PR, porque un squash merge usa el título como commit.
2. `release-please.yml` corre en cada push a `main`. Abre o actualiza un PR de release que sube `version=` en
   `gradle.properties`, actualiza `.release-please-manifest.json` y escribe `CHANGELOG.md`.
3. Al mezclar ese PR, release-please crea el tag `vX.Y.Z` y el GitHub Release.
4. El release dispara `publish.yml`: compila `./gradlew bootJar -Pversion=X.Y.Z` y sube el jar al release como
   `sgspe-X.Y.Z.jar`.

Para bajar el jar de un release:

```bash
gh release download vX.Y.Z --repo wasichai/sgspe --pattern 'sgspe-*.jar'
```

## Versiones

Una sola versión para todo el repositorio, la de `gradle.properties` (entre los marcadores `x-release-please-*`).
Mientras la versión sea 0.x, un `feat` sube el menor, un `fix` sube el parche y un cambio incompatible (`feat!:` o
`BREAKING CHANGE:` en el cuerpo) también sube el menor (`bump-minor-pre-major`). Los tipos `docs`, `chore`, `ci`, `build`,
`test`, `refactor` y `style` no aparecen en el `CHANGELOG.md`.

- La versión parte de `0.0.0`, que es solo el punto de partida del manifest: sin un release anterior, release-please no
  aplica `bump-minor-pre-major` y el primer PR de release, que recoge todo el historial de `main`, propone `1.0.0`.
- Para fijar una versión, un commit que llegue a `main` lleva en el pie `Release-As: X.Y.Z`. Vale una vez: release-please
  actualiza el PR de release abierto y los siguientes vuelven a calcularse por los commits. El primer release se fijó así
  en `0.10.0`; `1.0.0` queda para cerrar el MVP (Release 1 del [roadmap](../roadmap.md)).
- Nada de esto se edita a mano: ni `version=` en `gradle.properties`, ni `.release-please-manifest.json`, ni
  `CHANGELOG.md`. El PR de release es el único que los toca.

## Configuración única (secreto del repositorio)

| Secreto | Dónde | Para qué | PAT de grano fino |
|---|---|---|---|
| `RELEASE_PLEASE_TOKEN` | `wasichai/sgspe` (Settings > Secrets and variables > Actions) | que el PR de release dispare la CI y el release dispare `publish.yml` | en este repositorio: Contents y Pull requests, lectura y escritura |

Un PR o un release hechos con el `GITHUB_TOKEN` del workflow no disparan otros workflows. Por eso el PR de release no
correría la CI y el release no correría `publish.yml`. El secreto lo carga el usuario.

- El propietario del PAT es la organización `wasichai`, que rechaza un PAT de grano fino con más de 366 días de vida:
  renuévalo antes de que venza o release-please falla.
- `release-please.yml` usa `GITHUB_TOKEN` si falta el secreto. Eso solo funciona si Settings > Actions > General permite a
  GitHub Actions crear pull requests, y aun así no se publica ningún jar ni corre la CI del PR de release.
- `publish.yml` sube el jar con el `GITHUB_TOKEN` del workflow (`contents: write`) y lee las librerías de wasichai con
  `packages: read`, igual que `ci.yml`.

## Qué falta

El jar adjunto es el artefacto del release, no un despliegue: el despliegue sigue pendiente de GAP-022 (ver
[arquitectura §10](../architecture.md)). Cuando se decida, por ejemplo una imagen de contenedor, se agrega a
`publish.yml` como un job más.
