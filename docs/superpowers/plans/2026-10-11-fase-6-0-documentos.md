# SGSPE · Fase 6, plan 6.0 (documentos): plan de implementación

> **Para agentes:** SUB-SKILL OBLIGATORIA: usar superpowers:subagent-driven-development (recomendada) o
> superpowers:executing-plans para ejecutar este plan tarea por tarea. Los pasos usan casillas (`- [ ]`) para el
> seguimiento. Cada tarea sigue el ciclo de superpowers:test-driven-development aplicado a documentos: una comprobación
> que falla, los cambios, la comprobación y las pruebas del modelo en verde, commit.

**Goal:** Dejar `docs/` al día con la especificación de F6 antes de tocar el modelo y el código: los IDs nuevos (ASM-120
a ASM-127) con los totales de §20.1 (547) y UPS-13 resuelta; las contradicciones C1 a C9 cerradas en cada documento; el
contrato completo de los endpoints de F6 tal como lo fijaron los planes 6.2a y 6.2b; GeoServer, el proxy WMS, el mapa base,
GDAL, las exportaciones y la calidad descritos en `gis.md`, `security.md`, `architecture.md` y los ADR; las pantallas S09,
S10, S12 a S15, S63 y el tablero territorial; y el roadmap, la trazabilidad, la historia, `docs/develop/README.md` y
`CLAUDE.md`. Los planes 6.1, 6.2a, 6.2b, 6.2c y 6.3 leen estos documentos como fuente de verdad.

**Architecture:** Solo documentos y una prueba del lector de documentos. A diferencia del plan 5.0, las tareas no traen
bloques «reemplazar … por …» de texto exacto: cada una fija lo que el documento debe decir (con las cadenas exactas donde
un lector, una prueba u otro plan las busca) y una comprobación en Python de solo biblioteca estándar que falla mientras
falte el cambio; quien la ejecuta redacta la prosa en el estilo del documento. `model/design_docs.py` lee las fichas, los
catálogos y la matriz de permisos de estos mismos archivos y `test_model.py` y `test_apply_roles.py` los comparan con el
modelo de F5: lo que el modelo de F5 no puede ver todavía se escribe como texto con su fase (decisión 1) y lo pasa a la
forma leída el plan 6.1. Ningún archivo de `src/` ni de `model/*.json` cambia.

**Tech Stack:** Markdown (sin prettier: `.prettierignore` excluye `docs/`, `*.md` y `model/`); Python 3.11 o superior,
solo biblioteca estándar (`model/` y `tools/`); `yarn format:check` (prettier) como compuerta del repositorio; husky y
commitlint para los mensajes.

**Spec:** `docs/superpowers/specs/2026-10-11-fase-6-design.md`, §6 («Cambios en los documentos (plan 6.0)») y las
secciones que cita: §1, §2 (decisiones del usuario y elecciones), §3.1 a §3.7, §4, §5, §7 y §8. Además, los contratos
que ya fijaron los planes de F6, que este plan copia a los documentos: plan 6.1 «Decisiones» e «Interfaces con los otros
planes de F6»; plan 6.2a «Decisiones» (filas 1 a 16) e «Interfaces con el portal»; plan 6.2b «Decisiones» e «Interfaces
con el portal»; plan 6.3 «Ajustes tras el plan 6.0» y sus decisiones 7 a 21.

## Global Constraints

- **Repositorio y rama.** Todo en `~/IdeaProjects/sgspe`, en la rama `docs/fase-6-0-documentos` creada desde `main` con
  la especificación y los planes de F6 integrados (PR sgspe#41). Las tareas paralelas (grupo 1 de «Orden y paralelismo»)
  trabajan cada una en su worktree desde la rama ya con la Tarea 1, y se integran con `git merge --no-ff`.
- **El diseño manda.** `docs/` es la fuente de verdad y `docs/requirements-analysis.md` el único lugar donde se definen
  IDs: ASM-120 a ASM-127 los define la Tarea 1 y las demás solo los citan. Ningún GAP, CON ni UPS nuevo (UPS-30 queda
  libre).
- **El modelo de F5 sigue en verde.** `test_model.py` (con `PHASE = "F5"`) compara las fichas, los únicos, los índices y
  las acciones con `model/model.json`, y `test_apply_roles.py` compara cada celda de la matriz con `model/roles.json`. Las
  filas de campo nuevas llevan «Desde F6.» en la celda Descripción (el lector toma de ahí la fase y la prueba las filtra);
  las acciones, los únicos, los índices y las celdas de la matriz de F6 se escriben como texto con su fase, en las formas
  exactas de la decisión 1, que el normalizador del plan 6.1 (Tarea 1, Paso 2, y Tarea 3, Paso 1) pasa a la forma leída.
- **Idioma.** Español (es-PE) en documentos, comentarios y mensajes; nombres técnicos en inglés (ADR-0009); valores ENUM
  en español MAYÚSCULAS sin tildes; prosa envuelta a 120 columnas; filas de tabla en una línea, como en los documentos.
- **Datos.** Ningún nombre real de persona; ejemplos ficticios («Proyecto Ejemplo A», «Comunidad Ejemplo»,
  `@sgspe.test`).
- **Compuertas de cada tarea** (desde la raíz del checkout de la tarea; en un worktree nuevo, `yarn install
  --frozen-lockfile` una vez antes de `yarn format:check`):

  ```bash
  (cd model && python3 -m unittest 2>&1 | tail -1 | grep -x OK && python3 apply.py --validate-only) && \
    python3 tools/check_traceability.py && yarn format:check
  ```

  Esperado: `OK`, nada de `apply.py --validate-only` (sale con 0), `ok: 298 requisitos, todos con su fila` y
  `All matched files use Prettier code style!`. Este plan no toca código: no hace falta Gradle ni la integración.
- **Commits.** Conventional Commits con asunto en español; cuerpo de 100 columnas como máximo (contadas en caracteres);
  al final, tras una línea en blanco, `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>` y
  `Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM`. `git add` solo con rutas explícitas. Nunca
  se versionan `initial-promp.md`, `arch-*.md`, `tech-landing.txt` ni nada de `~/Documents/gspe`.
- **Push y PR** solo en la Tarea 9.

## Review Focus

1. **Lo que el modelo de F5 no puede ver.** Una acción `` `EXPORT` `` entre comillas invertidas en «Acciones
   declaradas», un «`unique` en `code`», un «`indexed` en `import_batch_id`» o una celda de la matriz con `` `EXPORT` ``
   rompen `test_model.py` o `test_apply_roles.py` en `main` hasta que llegue el plan 6.1. Se espera la CI del PR en
   verde y las formas en prosa de la decisión 1, que el normalizador de 6.1 reconoce. Pruebas: la suite completa del
   modelo en cada tarea y `test_las_fichas_de_f6_traen_lo_que_suma_el_plan_6_0` (Tarea 2), que fija las formas en prosa.
2. **Un mismo endpoint, la misma forma en todos los documentos.** `api.md` §4, `dashboards.md` §4.8, `gis.md` §5 y §8 y
   `ux/screens.md` deben dar las mismas raíces, campos y códigos que «Interfaces con el portal» de 6.2a y 6.2b, porque
   6.2a, 6.2b, 6.2c y 6.3 copian de documentos distintos. Prueba: la comprobación cruzada de códigos y raíces del Paso 1
   de la Tarea 9.
3. **IDs y totales.** §20.1 suma 547 (ASM 127), cada ASM nuevo se define una vez y lo cita al menos otro documento, y
   UPS-13 dice «Resuelta» en todas partes (análisis, `gis.md`, `platform-fit.md`, `roadmap.md`, `security.md`,
   trazabilidad). Pruebas: la comprobación de la Tarea 1 y el conteo del Paso 1 de la Tarea 9.
4. **El WFS pospuesto en todas partes.** Ningún documento vigente promete `GET /api/sgspe/territory/wfs` en F6: `api.md`,
   `roadmap.md`, `gis.md` §4.6 y §10, `security.md` §10.2 regla 5, RQ-GIS-018 y ASM-056 lo dan por pospuesto (ASM-120).
   Prueba: la búsqueda de `territory/wfs` y «WFS» del Paso 1 de la Tarea 9, que solo admite las menciones con «ASM-120»
   o «pospuesto».
5. **El puerto de GeoServer.** La comprobación de 6.1 (Tarea 1, Paso 1) lee el puerto de las líneas con «GeoServer» de
   ADR-0026, `CLAUDE.md` y `docs/develop/README.md`; hoy el README dice `localhost:8081`, que es el GeoServer de otra
   app. Se espera 8095 en las tres, en líneas que no nombren otro puerto 80xx. Prueba: la comprobación de la Tarea 8.

## Decisiones de este plan frente a la especificación y los documentos

| # | Hallazgo | Decisión |
|---|---|---|
| 1 | Las formas que leen las pruebas del modelo de F5 (acciones, únicos, índices y celdas de la matriz) no pueden llevar lo de F6 sin poner roja la CI de `main` hasta el plan 6.1 | Formas en prosa, exactas: «Acciones declaradas» termina en `; desde F6, EXPORT.` sin comillas invertidas (`- **Acciones declaradas:** ninguna; desde F6, EXPORT.`; `` - **Acciones declaradas:** `APPROVE`, `READ_ALL_PROJECTS`; desde F6, EXPORT. ``); en `community` y `environmental_feature`, `` - **Únicos:** ninguno hasta F5; desde F6, `code`. `` y `` - **Índices declarados:** ninguno hasta F5; desde F6, `import_batch_id`. ``; la matriz de `security.md` §7 no cambia sus celdas y §7.8 dice en prosa quién gana `EXPORT` y `C` en `export_log`. El normalizador del plan 6.1 reconoce estas formas (Tarea 1, Paso 2, y Tarea 3, Paso 1). Las filas de campo nuevas y el bullet de §9.7 sí van en la forma leída: nada de F5 los lee (Tareas 2 y 3) |
| 2 | §4.2 de la especificación deja al plan 6.0 la lista de roles con `EXPORT` territorial «con su fuente» | `GIS_ANALYST` (§7.8, «por defecto»), `SOCIAL_MANAGER` (exporta seis objetos de F2 a F4 y administra áreas de influencia) y `PROJECT_MANAGER` (`C` en `export_log` y `EXPORT` sobre `deliverable`), sobre los 13 objetos de §3.5; `GIS_ANALYST` gana `C` en `export_log`; `AUDITOR`, `ANALYST` y los demás, sin `EXPORT` territorial. Es la lista que fijan las pruebas del plan 6.1 (decisión 2 de 6.1) (Tarea 3) |
| 3 | §3.3 pide un puerto de GeoServer «fijado por ADR-0026» | 8095 en loopback (`SGSPE_GEOSERVER_PORT`, contenedor 8080), como la decisión 1 del plan 6.1; 8081 a 8086 son de otras apps del mismo Docker (Tareas 5 y 8) |
| 4 | La especificación §3.3 arma `GetCapabilities` «sin consultar a GeoServer», nombra `LIBKML` y `OGR_KML_FOLLOW_LINKS` en §3.4 y da `null` sin estilo en §5; §5 nombra `land_right.owner` | Mandan los planes que ya decidieron con el código a la vista: la lista de capas publicadas sale de la REST de GeoServer con caché de 60 s (6.2b, decisión 4); solo el driver `KML`, y SGSPE descomprime el KMZ (6.2b, decisión 1); sin estilo, `{ type: 'none' }` y `attribution` (6.2b, decisión 5); el titular es `land_right.holder_person` (6.2a, decisión 4) (Tareas 1, 4 y 5) |
| 5 | §6 de la especificación lista ocho códigos de error nuevos; 6.2a y 6.2b usan además `INVALID_OF`, `ZONE_NOT_FOUND`, `PERIOD_NOT_APPLICABLE`, `AREA_NOT_FOUND`, `LOCALITY_NOT_FOUND`, `COMMUNITY_NOT_FOUND`, `EXPORT_TIMEOUT`, `INVALID_FORMAT` e `INVALID_COORDINATES` | `api.md` §3.3 y §4 traen todos, con el estado y el campo de cada plan (Tarea 4) |
| 6 | `gis.md` §2.2 da LYR-17 a LYR-24 como «F6 (capa)» y `api.md` da `layers/{layer}` como «F2 (básicas), F6», pero ningún plan de F6 las sirve y el plan 6.3 las había dejado fuera | Siguen en F6: las sirve un plan corto 6.2c (capas de gestión en `layers/{layer}`, en paralelo con 6.2a y 6.2b); el grupo Gestión de S12 las lista junto al calor. Los documentos no cambian de fase; `roadmap.md` F6 las nombra en el alcance (Tareas 6 y 7) |
| 7 | `dashboards.md` y `screens.md` ponen el tablero territorial en `/territory` y exigen `locality` o `community`; 6.2a exige `project` (decisión 10) y 6.3 lo pone en `/territory/dashboard` (decisión 13) | `GET /api/sgspe/dashboards/territory` exige `project` (`400 PROJECT_REQUIRED`); KPI-TER-01 entra solo con `locality` o `community`, y sin ellos sale `available: false` con `TERRITORY_REQUIRED`. La ruta del portal es `/territory/dashboard` y S63 vive en `/territory/quality` (Tarea 6) |
| 8 | `gis.md` §6.4 pone «Sin fondo» en el panel de capas | Va sobre el propio mapa, junto al aviso «Sin mapa base», porque S10, S24 y el dibujo no tienen panel de capas (6.3, «Contradicciones», 5) (Tareas 5 y 6) |
| 9 | La forma de `palette.json` y de los estilos no estaba en los documentos; 6.3 suponía otra | `gis.md` §7.1 y §7.2 describen la de 6.1: `model/layers.json` con nueve entradas `{ layer, object, geometry, style, label? }`, ocho capas de GeoServer `<tabla física>__<columna>`, un SLD por estilo y `palette.json` `{ description, styles: { <estilo>: { layer, stroke, fill, label, halo, … } } }`; el portal la copia byte a byte (Tarea 5) |
| 10 | UPS-13 está abierta en el análisis y RQ-GIS-013 en «Upstream (UPS-13); Fuera del MVP» | UPS-13 pasa a «Resuelta con wasichai-ui#38 en `@wasichai/gis` 1.0.0» y RQ-GIS-013 a «Diseñado; Fuera del MVP» (Tareas 1 y 7) |
| 11 | La trazabilidad §8 trae conteos por estado que no coinciden con sus filas desde antes de F5 (decisión 11 del plan 5.0) | Este plan solo cambia el estado de RQ-GIS-013 y no toca §8; la comprobación de la Tarea 7 exige los conteos de las filas de antes con esa única diferencia |

Huecos y contradicciones de la especificación §6 que cierra cada tarea: C1 (Tareas 1, 3, 4, 5 y 7), C2 (1, 2 y 5), C3 (6),
C4 (1, 4, 5, 6, 7 y 8), C5 (5 y 3), C6 (3 y 4), C7 (5), C8 (5 y 8) y C9 (6).

---

## Mapa de archivos

| Ruta | Qué cambia | Tarea |
|---|---|---|
| `docs/requirements-analysis.md` | §10 («CRS de origen»), P-03, RQ-GIS-003 y 018, §18.2 (UPS-13), §19 (ASM-056, ASM-048, ASM-085 y ASM-120 a ASM-127), §20.1 | 1 |
| `docs/database-model.md`, `docs/domain-model.md`, `model/test_design_docs.py` | Fichas de `community` y `environmental_feature` (cuatro campos), acciones en prosa de los 13 objetos, §4 (usos de `source_crs`); el modelo de dominio de BC-02; la prueba del lector | 2 |
| `docs/security.md` | §4.1, §5.3, §7.8, §9.7 (bullet de los maestros), §10 (proxy WMS, lista blanca, GDAL, regla 5), §14 | 3 |
| `docs/api.md` | §2 (`EXPORT`), §3.3 (códigos), §4 (endpoints de F6), §5 | 4 |
| `docs/gis.md`, `docs/adr/0015-…`, `docs/adr/0019-…`, `docs/adr/0026-…`, `docs/adr/README.md` | §1.3, §2.2 (solo «Cómo se sirve» si hace falta), §4, §5, §6, §7, §8, §9, §10, §11; estados y puerto | 5 |
| `docs/dashboards.md`, `docs/ux/screens.md`, `docs/ux/ux-architecture.md` | §4.8, §5 (tablero `territory`), §7, §8; S09, S10, S12 a S15, S24, S63 y el tablero territorial; rutas e inventario | 6 |
| `docs/architecture.md`, `docs/roadmap.md`, `docs/requirements-traceability.md`, `docs/platform-fit.md`, `docs/glossary.md` | §5 y §10; F6; filas RQ-GIS y RF-TER de F6; UPS-13; «Capa pública», «Compuerta espacial» | 7 |
| `docs/develop/README.md`, `CLAUDE.md`, `docs/HISTORY.md` | GeoServer y GDAL en desarrollo; orden de aprovisionamiento, puerto, «Dónde está cada cosa»; la entrada del plan 6.0 | 8 |

## Interfaces con los otros planes de F6

Lo que este plan deja para los siguientes (todos parten de `main` con 6.0 integrado):

- **6.1 (modelo y GeoServer).** Las cuatro filas de campo de `community` y `environmental_feature` en la forma leída con
  «Desde F6.» en la Descripción; las formas en prosa de la decisión 1 (13 líneas de acciones, dos «Únicos», dos «Índices
  declarados»); el bullet «Maestros del tenant» de `security.md` §9.7 en la forma leída; la lista de roles de la decisión 2
  en §7.8; el puerto 8095 en ADR-0026, `CLAUDE.md` y `docs/develop/README.md`; el orden de aprovisionamiento que termina en
  `publish_layers.py` (omitible con `--skip-layers`); el rol de solo lectura `sgspe_geoserver` en el README de desarrollo.
- **6.2a (consultas y KPI-TER).** ASM-122 y ASM-126; los 13 endpoints de consulta con la forma de sus filas, `available`,
  `missingFields` y `truncated`, y sus códigos en `api.md` §3.3 y §4; `TERRITORY_REQUIRED`, `ignoredFilters` y el tablero
  que exige `project` en `dashboards.md` §4.8; `SpatialGate` y la regla de `ArchitectureTest` en `gis.md` §5.2 y
  `architecture.md`; la fila de `CLAUDE.md` que nombra `SpatialGate` con el texto exacto de 6.2a (Tarea 16, Paso 2), para
  que 6.2a no la duplique.
- **6.2b (servicios SIG).** ASM-121, 123, 124, 125 y 127; `/map/settings`, `imports/formats`, `wms`, `exports`,
  `geometry-checks` y `data-quality` en `api.md` §4 con las formas de «Interfaces con el portal» de 6.2b; las claves
  `sgspe.map.*`, `sgspe.territory.wms.*`, `sgspe.territory.gdal.*` e `sgspe.territory.import.max-entries` y
  `max-inflated-bytes` en `gis.md` y el README de desarrollo; las filas de `CLAUDE.md` de las piezas de 6.2b.
- **6.2c (capas de gestión).** LYR-17 a LYR-24 en `gis.md` §2.2 sin cambio de fase; `layers/{layer}` en `api.md` como
  «F2 (básicas), F6 (gestión)»; el alcance de F6 en `roadmap.md`.
- **6.3 (portal).** `ux/screens.md` S09, S10, S12 a S15, S24, S63 y el tablero territorial con las decisiones 7 a 21 de
  6.3; `ux-architecture.md` con `/territory/dashboard` y `/territory/quality`; la paleta de `gis.md` §7.

## Orden y paralelismo

| Grupo | Tareas | Necesita | Por qué juntas |
|---|---|---|---|
| 0 | 1 | La rama creada | Define los IDs que citan las demás |
| 1 | 2, 3, 4, 5, 6, 7 | La Tarea 1 integrada | Archivos disjuntos (ver «Mapa de archivos») |
| 2 | 8, después 9 | Todas las anteriores | `CLAUDE.md` y la historia resumen lo integrado; la 9 verifica, hace push y abre el PR |

---

### Tarea 1: IDs de F6 en el análisis

**Archivos:**
- Modificar: `docs/requirements-analysis.md`

**Interfaces:**
- Consume: la especificación §6 (tabla de IDs) y las decisiones 4 y 10 de este plan.
- Produce: ASM-120 a ASM-127, que citan las Tareas 2 a 8; UPS-13 resuelta; §20.1 con 547.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import re
text = open('docs/requirements-analysis.md', encoding='utf-8').read()
bad = []
for n in range(120, 128):
    if len(re.findall(rf'^\| ASM-{n} \|', text, re.M)) != 1:
        bad.append(f'ASM-{n}: no se define una sola vez')
if not re.search(r'^\| `ASM` \| 127 \|$', text, re.M):
    bad.append('§20.1: ASM no es 127')
total = re.search(r'^\| Total \| (\d+) \|$', text, re.M)
if not total or total.group(1) != '547':
    bad.append(f'§20.1: el total no es 547 ({total and total.group(1)})')
ups13 = next((l for l in text.splitlines() if l.startswith('| UPS-13 |')), '')
if 'Resuelta' not in ups13 or 'wasichai-ui#38' not in ups13:
    bad.append('UPS-13 no está resuelta con wasichai-ui#38')
rq18 = next(l for l in text.splitlines() if l.startswith('| RQ-GIS-018 |'))
if 'ASM-120' not in rq18:
    bad.append('RQ-GIS-018 no cita ASM-120')
crs = next(l for l in text.splitlines() if l.startswith('| CRS de origen |'))
if '`community`' not in crs or '`environmental_feature`' not in crs:
    bad.append('§10 «CRS de origen» no nombra community ni environmental_feature')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con las ocho líneas de los ASM, las dos de §20.1 y las de UPS-13, RQ-GIS-018 y §10.

- [ ] **Paso 2: §19, los supuestos nuevos**

Agregar, después de la fila de ASM-119, estas ocho filas (columnas «ID | Supuesto | Por qué hace falta | Cómo revertirlo |
Afecta a», una línea cada una):

| ID | Supuesto | Por qué hace falta | Cómo revertirlo | Afecta a |
|---|---|---|---|---|
| ASM-120 | El WFS autenticado para analistas (RQ-GIS-018, ASM-056) se pospone: F6 entrega el proxy WMS de las capas públicas y las exportaciones geográficas de LYR-01 a LYR-16 cubren a quien trabaja con QGIS o ArcGIS con archivos, no con una conexión viva; `GET /api/sgspe/territory/wfs` sale de F6 | Un WFS entrega atributos y geometrías vectoriales: necesita los mismos filtros de alcance, campos y divulgación que las exportaciones, y ningún usuario lo pidió para F6 (decisión del usuario del 2026-10-11) | Una fase posterior suma el WFS detrás del mismo proxy, con la lista blanca de capas, `EXPORT` sobre el objeto y la divulgación por capa de ASM-124 | RQ-GIS-018, ASM-056, `api.md` §4, `gis.md` §4.6 y §10, `security.md` §10.2, `roadmap.md` F6 |
| ASM-121 | GeoServer publica solo maestros de `ProjectScope.TENANT` sin campos restringidos, listados en `model/layers.json` (LYR-01, 02, 03, 04, 05, 07 y 16), y el proxy solo entrega imágenes: `GetMap`, `GetLegendGraphic` y `GetCapabilities` armado con la lista blanca; nunca `GetFeatureInfo`, `DescribeLayer`, filtros ni SLD del cliente | GeoServer lee la base sin los permisos de la aplicación (CON-054); con solo imágenes de objetos que todos los roles del tenant leen, nada sale de GeoServer que la API no daría | Publicar otra capa exige que sea un maestro TENANT sin campos restringidos (`test_model.py`, `ArchitectureTest`); servir atributos exige el WFS de ASM-120 | RQ-GIS-012, CON-054, `security.md` §10.2, `gis.md` §7.1, `model/layers.json`, `publish_layers.py`, `WmsProxy` |
| ASM-122 | `SpatialGate` es la única vía de SQL de lectura que cruza tablas del tenant: compone todos los `RecordReadScope` registrados (hoy `ProjectReadScope` y `ConfidentialAttentionScope`) como subconsultas filtradas, los omite para ADMIN y la plataforma como wasichai, revisa READ y la legibilidad de cada objeto y campo, y devuelve ids y medidas; las geometrías de referencia de los filtros por zona se leen antes como quien llama y viajan como parámetro (`ZoneCriterion`), nunca como subconsulta a otra tabla | wasichai no ofrece cruces espaciales entre objetos con los permisos de quien llama (CON-054); copiar los alcances dejaría fuera uno nuevo | Si wasichai suma consultas espaciales con permisos, `SpatialGate` las delega; `SpatialGateScopeParityTest` detecta si wasichai cambia cuándo pide los alcances | RQ-GIS-002, RQ-GIS-017, RQ-DSH-032, `gis.md` §5.2, `architecture.md` §5, `ArchitectureTest`, KPI-TER-04 |
| ASM-123 | Dos destinos nuevos de importación con CRS de origen: `community.territory` y `environmental_feature.boundary` (MULTIPOLYGON), con `code` (único, opcional), `source_crs`, `source_reference` e `import_batch_id` en los dos objetos; se reconcilian por `code` como `mining_concession`, nunca por nombre: sin `code`, la fila se rechaza, y las comunidades ya registradas sin código se completan a mano antes de importar | El territorio comunal y los elementos ambientales llegan en Shapefile o GeoPackage de registros públicos y autoridades (decisión del usuario del 2026-10-11); reconciliar por nombre confundiría comunidades homónimas | Reconciliar por otra clave (partida registral) con su campo único | RQ-GIS-003, RQ-GIS-008, RF-TER-003, RF-TER-008, `gis.md` §4.1 y §4.4, `database-model.md` (`community`, `environmental_feature`) |
| ASM-124 | Las exportaciones geográficas (`POST /api/sgspe/territory/exports`) son solo de los objetos de LYR-01 a LYR-16, con `EXPORT` y READ sobre el objeto, CREATE sobre `export_log` y divulgación por capa: PUBLICO, las capas públicas; EXTERNO, además `community_sector`, `environmental_feature`, `project` (ubicación y área de interés), los accesos de `project_component`, `area_of_influence` y los predios PROPIEDAD, POSESION o COMUNAL sin `registry_record`, con la lista de verificación; INTERNO, todos; `land_right` nunca; las capas de gestión (LYR-17 a LYR-24) no salen como archivo geográfico | Las capas de gestión tienen datos personales y su propia exportación tabular con nivel de divulgación (F2 a F5); un archivo geográfico de ellas no tiene pedido (decisión del usuario del 2026-10-11) | Agregar un objeto de gestión exige su regla de divulgación por capa en `ExportDisclosure` y su `EXPORT` en la matriz | RQ-GIS-018, RQ-SEC-011, RF-REP-002, `api.md` §4, `security.md` §7.8, `gis.md` §4.6 |
| ASM-125 | El mapa base es por organización: `sgspe.map.base-style-url` por defecto y `sgspe.map.base-style-url-by-organization` (slug → URL), con `sgspe.map.attribution` por URL; `GET /api/sgspe/map/settings` lo devuelve como `BasemapSpec` de `@wasichai/gis` (`{ type: 'style', url }` o `{ type: 'none' }`, más `attribution`), y el portal lo aplica a `gisModule({ basemap })` después del login y a sus mapas propios por la prop `basemap`, en lugar de la variable de build de F2 | RQ-GIS-013 pide el mapa base por organización y UPS-13 ya está resuelta (wasichai-ui#38); producción nunca usa las teselas públicas de OpenStreetMap | Guardar el mapa por organización en un objeto del tenant si un administrador lo debe cambiar sin despliegue | RQ-GIS-013, ASM-048, ASM-085, UPS-13, `gis.md` §6.4, `api.md` §4 |
| ASM-126 | Las consultas espaciales tienen límites configurables (`sgspe.territory.queries.*`): `bufferM` de 1 a 20 000 m (500 por defecto), `distanceKm` de 0.1 a 50 km (5 por defecto), `limit` hasta 5 000 filas (con `truncated`), periodo de `concentration` hasta 5 años (12 meses por defecto) y hasta 200 registros relacionados por fila; fuera de rango, `400 OUT_OF_RANGE`; parámetro desconocido, `422` | Un buffer o una distancia sin tope convierten una consulta del mapa en un barrido del tenant; los valores salen del caso de uso de `gis.md` §5.3 | Cambiar los valores por despliegue en `application.yml` | RQ-GIS-002, RQ-GIS-017, `api.md` §4, `gis.md` §5.3 |
| ASM-127 | La importación con GDAL exige `ogrinfo` y `ogr2ogr` 3.8 o superior (`sgspe.territory.gdal.path`, `min-version`), solo con los drivers `ESRI Shapefile`, `KML`, `GPKG`, `OpenFileGDB` y `GPX` (el KMZ lo descomprime SGSPE), con un entorno de proceso limpio y sin red, argumentos fijos y rutas del espacio de trabajo, plazo `IMPORT_TIMEOUT`; el CRS que GDAL detecta es una propuesta que la persona confirma (CON-024); KML, KMZ y GPX son EPSG 4326; sin GDAL, esos formatos responden `422 FORMAT_UNAVAILABLE` y `GET /api/sgspe/territory/imports/formats` dice cuáles hay | GDAL es una dependencia nativa con versiones distintas entre la CI, el desarrollo y un despliegue; un driver o una opción del cliente abriría lecturas de red o de disco | Empaquetar GDAL en la imagen del backend con la versión fijada; sumar drivers solo con su prueba | RQ-GIS-018, GAP-012, `gis.md` §4.1, `security.md` §10, `architecture.md` §10 |

- [ ] **Paso 3: Lo que cambia por los IDs nuevos**

- **ASM-056** (§19): su texto pasa a decir que los formatos de F6 son los de ASM-127 y que el WFS se pospone (ASM-120);
  agregar ASM-120 y ASM-127 a «Afecta a» si no los nombra.
- **ASM-048 y ASM-085** (§19): el mapa base por organización se sirve por `/map/settings` (ASM-125) y UPS-13 está
  resuelta; ninguna de las dos deja de valer.
- **UPS-13** (§18.2): en «Mientras tanto», «Resuelta con wasichai-ui#38 en `@wasichai/gis` 1.0.0 (`basemap` e
  `initialView` en `gisModule` y en `MapView`); SGSPE la adopta en F6 (ASM-125)». Si la tabla de §18.2 tiene una columna
  de estado o una convención para las resueltas (ver UPS-24), seguirla.
- **RQ-GIS-018** (§12): «En F6, importar con GDAL Shapefile, KML/KMZ, GeoPackage, FileGDB y GPX (ASM-127) y exportar
  GeoJSON, CSV y XLSX (en UTM y en latitud y longitud), KML, Shapefile y GeoPackage de las capas LYR-01 a LYR-16
  (ASM-124); el WFS autenticado para analistas se pospone (ASM-120); los lotes de importación siguen ASM-060».
- **RQ-GIS-003** y **P-03**: los destinos `community.territory` y `environmental_feature.boundary` desde F6 (ASM-123).
- **§10, catálogo «CRS de origen»**: la columna de objetos suma `community` y `environmental_feature` (desde F6); los
  valores no cambian (`EPSG 32717 · … · EPSG 4326`).
- **§20.1**: `ASM` de 119 a 127 y el total de 539 a 547. Si §20.4 (u otra sección) repite los totales por familia,
  actualizarla igual.

- [ ] **Paso 4: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas de «Global Constraints». Después:

```bash
git add docs/requirements-analysis.md
git commit -F - <<'MSG'
docs: IDs de la fase 6 (ASM-120 a ASM-127) y UPS-13 resuelta

WFS pospuesto, capas publicadas, compuerta espacial, destinos de importación nuevos, exportaciones por
capa, mapa base por organización, límites de las consultas y GDAL; §20.1 pasa a 547 IDs.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 2: Fichas de `community` y `environmental_feature`, acciones de F6 y la prueba del lector

**Archivos:**
- Modificar: `docs/database-model.md`, `docs/domain-model.md`, `model/test_design_docs.py`

**Interfaces:**
- Consume: ASM-123 y ASM-124 (Tarea 1); la decisión 1.
- Produce: las filas que la Tarea 1 del plan 6.1 exige (`4` y `4` en su Paso 1) y las 13 líneas de acciones en prosa que
  su normalizador convierte.

- [ ] **Paso 1: La prueba que falla**

En `model/test_design_docs.py`, agregar antes de `class BrokenDocsTests` (si el archivo ya tiene una clase de F5 parecida,
seguir su estilo):

```python
# el plan 6.0 suma a los documentos lo que el plan 6.1 lleva al modelo; hasta entonces, lo que el modelo de F5 compara
# va en prosa con su fase (plan 6.0, decisión 1)
F6_EXPORTABLE = ("administrative_unit", "locality", "road_access", "community", "community_sector", "mining_concession",
                 "environmental_feature", "project", "project_component", "area_of_influence", "land_parcel", "school",
                 "health_facility")
F6_FIELDS = (("code", "TEXT", True), ("source_crs", "ENUM", False), ("source_reference", "TEXT", False),
             ("import_batch_id", "UUID", False))


class F6PlanZeroTests(unittest.TestCase):
    def test_las_fichas_de_f6_traen_lo_que_suma_el_plan_6_0(self):
        cards = {card.name: card for card in design_docs.cards()}
        for name in ("community", "environmental_feature"):
            card = cards[name]
            fields = {field["name"]: field for field in card.fields}
            for field, ftype, unique in F6_FIELDS:
                self.assertIn(field, fields, f"{name}.{field}")
                self.assertEqual(design_docs.field_phase(card, fields[field]), "F6", f"{name}.{field}")
                self.assertEqual(fields[field]["unique"], unique, f"{name}.{field}")
            self.assertEqual(fields["source_crs"]["enum"], "source_crs")
            # en prosa hasta el plan 6.1: el modelo de F5 no tiene code ni import_batch_id
            self.assertEqual((card.unique, card.indexed), ([], []), name)
        text = design_docs.read("database-model.md")
        for name in F6_EXPORTABLE:
            self.assertNotIn("EXPORT", cards[name].actions, name)
            block = text.split(f"### `{name}`\n", 1)[1].split("\n### ", 1)[0]
            line = next(l for l in block.splitlines() if l.startswith("- **Acciones declaradas:** "))
            self.assertTrue(line.endswith("; desde F6, EXPORT."), line)
        for name in ("community", "environmental_feature"):
            block = text.split(f"### `{name}`\n", 1)[1].split("\n### ", 1)[0]
            self.assertIn("- **Únicos:** ninguno hasta F5; desde F6, `code`.", block)
            self.assertIn("- **Índices declarados:** ninguno hasta F5; desde F6, `import_batch_id`.", block)
        self.assertNotIn("EXPORT", cards["land_right"].actions)
```

`design_docs.read(name)` lee un documento de `docs/`, `field_phase(card, field)` recibe el diccionario del campo y
devuelve la mayor entre la fase de la ficha y la del campo; comprobar las claves del diccionario de campo (`name`, `enum`,
`unique`) en `design_docs.cards()` y ajustar la prueba a ellas sin cambiar lo que exige.

```bash
cd ~/IdeaProjects/sgspe/model && python3 -m unittest test_design_docs.F6PlanZeroTests -v 2>&1 | tail -3
```

Esperado: FAIL (`code` no está en `community`).

- [ ] **Paso 2: Las fichas de `community` y `environmental_feature`**

En `docs/database-model.md`, en las dos fichas, agregar a la tabla de campos (después de `name` en `community` y en el
lugar equivalente de `environmental_feature`; el orden de la ficha es el que copiará `model.json`), con la forma de las
filas de `mining_concession`:

| Campo | Tipo | Req. | Único | Descripción | Fuente |
|---|---|---|---|---|---|
| `code` | TEXT | no | sí | Código que lo identifica en las importaciones; sin código no se importa (ASM-123). Desde F6. | ASM-123 |
| `source_crs` | ENUM `source_crs` | no | — | CRS en que vino la geometría importada. Desde F6. | RQ-GIS-008 |
| `source_reference` | TEXT | no | — | Fuente de origen (archivo, capa o registro público). Desde F6. | RQ-GIS-008 |
| `import_batch_id` | UUID | no | — | Importación que lo creó o cambió (ASM-060). Desde F6. | ASM-060 |

Las descripciones no llevan `Por defecto:`, `(derivado)` ni `(restringido)` ni el carácter `|`. Las viñetas:
`` - **Únicos:** ninguno hasta F5; desde F6, `code`. `` y
`` - **Índices declarados:** ninguno hasta F5; desde F6, `import_batch_id`. ``. La introducción de cada ficha suma
ASM-123 a sus «Requisitos» y una oración: desde F6 se importa con CRS de origen y se reconcilia por `code`.

- [ ] **Paso 3: Las acciones de los 13 objetos y el catálogo**

En las fichas de `administrative_unit`, `locality`, `road_access`, `community`, `community_sector`, `mining_concession`,
`environmental_feature`, `project`, `project_component`, `area_of_influence`, `land_parcel`, `school` y `health_facility`,
la viñeta «Acciones declaradas» termina en `; desde F6, EXPORT.`, sin comillas invertidas en `EXPORT`:
`- **Acciones declaradas:** ninguna; desde F6, EXPORT.` donde hoy dice «ninguna»; en `project`,
`` - **Acciones declaradas:** `APPROVE`, `READ_ALL_PROJECTS`; desde F6, EXPORT. ``; en `area_of_influence`,
`` - **Acciones declaradas:** `APPROVE`; desde F6, EXPORT. `` (conservar lo que ya declara cada una). `land_right` y
`stakeholder` no cambian. En §3 («En F6, cada objeto con exportación GIS declara además `EXPORT`»), nombrar los 13
objetos y ASM-124. En §4, la fila del catálogo `source_crs` suma `community.source_crs` y
`environmental_feature.source_crs` (desde F6) a sus usos.

- [ ] **Paso 4: El modelo de dominio**

En `docs/domain-model.md` (BC-02 y donde se describan `community` y `environmental_feature` y la importación): los cuatro
campos de F6 y la reconciliación por código (ASM-123); `EXPORT` sobre los 13 objetos (ASM-124); `SpatialGate` como la
compuerta de las consultas (ASM-122) si el documento describe los servicios de `sgspe.territory`. Ninguna tabla que lea
`design_docs.py` cambia de forma (comprobarlo con la suite).

- [ ] **Paso 5: Verificar y commit**

```bash
cd ~/IdeaProjects/sgspe/model && python3 -m unittest test_design_docs.F6PlanZeroTests -v 2>&1 | tail -3
cd ~/IdeaProjects/sgspe && awk '/^### `community`$/,/^### `community_sector`$/' docs/database-model.md | grep -in "^| \`code\`\|^| \`source_crs\`\|^| \`source_reference\`\|^| \`import_batch_id\`" | grep -ic "desde F6"
awk '/^### `environmental_feature`$/,/^## /' docs/database-model.md | grep -in "^| \`code\`\|^| \`source_crs\`\|^| \`source_reference\`\|^| \`import_batch_id\`" | grep -ic "desde F6"
grep -c "; desde F6, EXPORT\.$" docs/database-model.md
```

Esperado: PASS, `4`, `4` y `13`, y las compuertas de «Global Constraints» (`Ran 239 tests`, `OK`). Después:

```bash
git add docs/database-model.md docs/domain-model.md model/test_design_docs.py
git commit -F - <<'MSG'
docs: campos de F6 en community y environmental_feature y EXPORT de las capas territoriales

code y los campos de origen con «Desde F6.»; EXPORT, únicos e índices en prosa hasta que el plan
6.1 los lleve al modelo, con la prueba que fija esas formas.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 3: Seguridad

**Archivos:**
- Modificar: `docs/security.md`

**Interfaces:**
- Consume: ASM-120, 121, 122, 124 y 127 (Tarea 1); las decisiones 1 y 2.
- Produce: el bullet de §9.7 que lee `design_docs.tenant_objects()` del plan 6.1; la lista de roles de §7.8 que normaliza
  su Tarea 3.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import re
text = open('docs/security.md', encoding='utf-8').read()
lines = text.splitlines()
bad = []
TENANT = ["administrative_unit", "locality", "community", "community_sector", "mining_concession", "land_parcel",
          "land_right", "environmental_feature", "person", "organization", "contact_method", "position",
          "actor_relationship", "school", "health_facility", "road_access", "festivity", "state_program",
          "local_conflict", "topic"]
start = next(i for i, l in enumerate(lines) if l.startswith('### 9.7 '))
end = next(i for i in range(start + 1, len(lines)) if lines[i].startswith('#'))
bullet = next((i for i in range(start, end) if lines[i].startswith('- **Maestros del tenant (`ProjectScope.TENANT`):**')), None)
if bullet is None:
    bad.append('§9.7: falta el bullet de los maestros del tenant')
else:
    block = [lines[bullet]]
    for l in lines[bullet + 1:end]:
        if not l.startswith('  '):
            break
        block.append(l)
    names = [n for n in re.findall(r'`([^`]+)`', ' '.join(block)) if re.fullmatch(r'[a-z][a-z0-9_]*', n)]
    if sorted(names) != sorted(TENANT):
        bad.append(f'§9.7: los maestros no son los 20 de ProjectScope.TENANT: {names}')
s78 = text.split('### 7.8', 1)[1].split('\n### ', 1)[0]
for want in ('GIS_ANALYST', 'SOCIAL_MANAGER', 'PROJECT_MANAGER', 'export_log', 'ASM-124'):
    if want not in s78:
        bad.append(f'§7.8: falta {want}')
if 'no muestran esas concesiones de F6' in text:
    bad.append('§7.8: sigue la frase de las concesiones de F6 que las tablas no muestran')
s102 = text.split('### 10.2', 1)[1].split('\n### ', 1)[0]
for want in ('ASM-120', 'ASM-121', 'WMS_PARAMETER_NOT_ALLOWED', 'GEOSERVER_UNAVAILABLE', 'layers.json', 'ASM-127'):
    if want not in s102:
        bad.append(f'§10.2: falta {want}')
for l in lines:
    if l.startswith('| ') and '`EXPORT`' in l and any(f'| `{o}` |' in l for o in ('community', 'school', 'project_component')):
        bad.append(f'§7: celda con EXPORT antes del plan 6.1: {l[:80]}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 (falta el bullet, falta todo lo de §7.8 y §10.2, sobra la frase).

- [ ] **Paso 2: §9.7, el bullet de los maestros del tenant**

Antes de `- **Exentos:**`, en la forma leída, una sola viñeta con continuaciones de dos espacios y sin línea en blanco
dentro, envuelta a 120 columnas, con cada nombre una sola vez y ningún otro identificador simple entre comillas
invertidas:

```markdown
- **Maestros del tenant (`ProjectScope.TENANT`):** `administrative_unit`, `locality`, `community`, `community_sector`,
  `mining_concession`, `land_parcel`, `land_right`, `environmental_feature`, `person`, `organization`, `contact_method`,
  `position`, `actor_relationship`, `school`, `health_facility`, `road_access`, `festivity`, `state_program`,
  `local_conflict`, `topic`. Son los únicos objetos que GeoServer puede publicar, y solo si no tienen campos restringidos (§10.2).
```

Comprobar que la lista es la de `src/main/kotlin/sgspe/scope/ProjectScope.kt` (`TENANT`); si difiere, gana el código y se
avisa.

- [ ] **Paso 3: §7.8 y §5.3, `EXPORT` de las capas territoriales**

- **§7.8:** reemplazar la frase «Las tablas de esta sección no muestran esas concesiones de F6» (y lo que la rodea sobre
  «los roles que ya exportan un objeto, también en formatos GIS») por la lista con su fuente (decisión 2): desde F6,
  `EXPORT` sobre `administrative_unit`, `locality`, `road_access`, `community`, `community_sector`, `mining_concession`,
  `environmental_feature`, `project`, `project_component`, `area_of_influence`, `land_parcel`, `school` y
  `health_facility` (nunca `land_right`) para `GIS_ANALYST` (por defecto), `SOCIAL_MANAGER` (ya exporta los objetos de F2
  a F4 y administra las áreas de influencia) y `PROJECT_MANAGER` (crea filas de `export_log` y exporta entregables);
  `GIS_ANALYST` gana `C` en `export_log`; `AUDITOR`, `ANALYST` y los demás, sin `EXPORT` territorial; divulgación por capa
  de ASM-124; «las celdas de §7.2 y §7.4 los muestran desde que el modelo de F6 está aplicado (plan 6.1)». Las celdas de
  la matriz **no cambian** (decisión 1).
- **§5.3** («Las mismas acciones, por objeto»): la fila de `EXPORT` nombra los 13 objetos en lugar de «Cada objeto de una
  capa `LYR-NN` que se exporte en F6».

- [ ] **Paso 4: §10, GeoServer, el proxy, GDAL y el WFS**

- **§10.2:** la regla del proxy (hoy regla 2) describe `WmsProxy` como §3.3 de la especificación y 6.2b: token de
  wasichai (`401`); lista blanca = capas publicadas de la organización del token que están en `model/layers.json`
  (nombre `<tabla física>__<columna>`, lista de GeoServer por su REST con caché de 60 s) y READ sobre el objeto de cada
  capa (`403`); solo `GetMap`, `GetLegendGraphic` y `GetCapabilities` (armado con la lista blanca); parámetros en lista
  blanca sin distinguir mayúsculas, cada uno una vez; `STYLES` solo los de `layers.json`, `FORMAT` `image/png` o
  `image/jpeg`, `WIDTH` y `HEIGHT` hasta 2048; lo demás `422 WMS_REQUEST_NOT_ALLOWED` o `WMS_PARAMETER_NOT_ALLOWED`;
  GeoServer caído, vencido (`sgspe.territory.wms.timeout`, 5 s) o que no responde una imagen, `503 GEOSERVER_UNAVAILABLE`;
  solo copia `Content-Type` y agrega `Cache-Control: private, max-age=300` (ASM-121). El límite de peticiones por
  persona va en el proxy inverso.
- **Regla 5 (WFS):** pospuesto (ASM-120).
- **Regla de la publicación** (hoy regla 8): `publish_layers.py` publica solo lo de `layers.json`, que `test_model.py` y
  `ArchitectureTest` exigen maestros TENANT sin campos restringidos (§9.7); credenciales de GeoServer solo por variables
  de entorno; datastore con el rol de solo lectura `sgspe_geoserver`.
- **GDAL** (donde §10 trata las importaciones): ASM-127 (drivers, entorno limpio, sin red, argumentos fijos, rutas del
  espacio de trabajo, ZIP sin `../` ni más de `sgspe.territory.import.max-entries` entradas o `max-inflated-bytes`).
- **SQL propio:** `SpatialGate` como la única vía de lectura que cruza tablas (ASM-122) y la regla de `ArchitectureTest`
  (en `sgspe.territory`, solo `SpatialGate` y los escritores `TerritoryImports`, `Areas`, `ProjectComponents` y `GeoSql`
  usan `DatabaseClient`).

- [ ] **Paso 5: §4.1, §194 (CSP), §14 y las brechas**

- **§4.1:** los pasos del alta del tenant terminan en `publish_layers.py` (omitible con `--skip-layers`); «los pasos 1 a
  5 los corre `provision_tenant.py`» pasa a 1 a 6.
- **CSP** (la viñeta de MapLibre y el mapa base): el host del estilo de `/map/settings` (ASM-125); UPS-13 resuelta.
- **§14 (pruebas de seguridad):** `WmsProxyApiTest`, `GdalImportApiTest` (ZIP con `../`, GDAL ausente),
  `TerritoryExportsApiTest` (divulgación por capa, `EXPORT`, `export_log`), `SpatialGateApiTest` y
  `SpatialGateScopeParityTest`, con lo que prueba cada una (especificación §5).
- **La fila de brechas upstream** (hoy en la línea que lista `UPS-01, UPS-02, …`): UPS-13 sale de las abiertas o dice
  «resuelta».

- [ ] **Paso 6: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas. Después:

```bash
git add docs/security.md
git commit -F - <<'MSG'
docs: seguridad de F6: maestros del tenant, EXPORT territorial, proxy WMS y GDAL

§9.7 enumera los maestros de ProjectScope.TENANT; §7.8 nombra quién exporta las capas territoriales
(las celdas, con el modelo de F6); §10 describe el proxy, la publicación, GDAL y el WFS pospuesto.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 4: Contratos de la API de F6

**Archivos:**
- Modificar: `docs/api.md`

**Interfaces:**
- Consume: ASM-120 a ASM-127; las secciones «Interfaces con el portal» y «Decisiones» de los planes 6.2a (líneas 134 a 211
  del plan) y 6.2b (líneas 138 a 186), que son la fuente exacta de cada forma; la decisión 5.
- Produce: el contrato que leen 6.2a (Tarea 16, Paso 1), 6.2b, 6.2c y 6.3.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
text = open('docs/api.md', encoding='utf-8').read()
bad = []
codes = ('OUT_OF_RANGE', 'INVALID_VALUE', 'INVALID_OF', 'ZONE_NOT_FOUND', 'PERIOD_NOT_APPLICABLE', 'PROJECT_NOT_FOUND',
         'AREA_NOT_FOUND', 'LOCALITY_NOT_FOUND', 'COMMUNITY_NOT_FOUND', 'WORKING_CRS_REQUIRED', 'PROJECT_REQUIRED',
         'TOO_MANY_RECORDS', 'TERRITORY_REQUIRED', 'LAYER_REQUIRED', 'FORMAT_UNAVAILABLE', 'OBJECT_NOT_EXPORTABLE',
         'DISCLOSURE_NOT_ALLOWED', 'WMS_REQUEST_NOT_ALLOWED', 'WMS_PARAMETER_NOT_ALLOWED', 'GEOSERVER_UNAVAILABLE',
         'EXPORT_TIMEOUT', 'INVALID_FORMAT', 'INVALID_COORDINATES')
for code in codes:
    if code not in text:
        bad.append(f'falta el código {code}')
for route in ('/api/sgspe/map/settings', '/api/sgspe/territory/imports/formats', 'checklistConfirmed', 'hasNext',
              'detectedCrs', 'holders', 'alreadyMember', 'WITHIN_BUFFER', 'localityType', 'distanceM', 'ASM-120',
              'ASM-124', 'ASM-126'):
    if route not in text:
        bad.append(f'falta {route}')
for line in text.splitlines():
    if 'territory/wfs' in line and 'ASM-120' not in line:
        bad.append(f'el WFS sigue vigente: {line[:100]}')
    if 'proxies WMS y WFS' in line:
        bad.append(f'«proxies WMS y WFS»: {line[:100]}')
rows = [l for l in text.splitlines() if l.startswith('| `GET` | `/api/sgspe/territory/') or l.startswith('| `GET` | `/api/sgspe/areas-of-influence/')]
gate = [l for l in rows if any(k in l for k in ('component-parcels', 'component-communal-land', 'concession-territories',
                                               'sensitive-distances', 'nearby-conflicts', 'concentration', 'summary',
                                               'member-suggestions'))]
for l in gate:
    if 'available' not in l:
        bad.append(f'fila sin available: {l[:90]}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1 con los códigos y las formas que faltan.

- [ ] **Paso 2: §4, los endpoints de consulta (6.2a)**

En la tabla de endpoints de `sgspe.territory` (hoy §4.2, filas de las líneas ~534 a 646), cada fila de los 13 endpoints de
consulta de la especificación §3.1 lleva en su celda de respuesta la forma de «Interfaces con el portal» de 6.2a (una
oración por endpoint, sin el carácter `|` dentro de la celda: escribir «o» en lugar de `|`), sus parámetros, sus códigos y
su fase «F6». Antes de la tabla, o en una nota que la tabla cite, la regla común: toda respuesta de la compuerta lleva
`available` y, si falta leer algo, `missingFields` (`objeto.campo`); `available: false` quiere decir «lista incompleta»,
nunca cero; las listas llevan `truncated`; `limit` opcional de 1 a `max-rows`; los límites de ASM-126; parámetro
desconocido, `422`. Incluir las decisiones de 6.2a que afinan el contrato: titulares de `land_right.holder_person`
(decisión 4); sin periodo, KPI-ENG-01 de los últimos 90 días (9); tierra «privada» = PROPIEDAD o POSESION y «comunal» =
territorio o predio COMUNAL (11); `concentration` cuenta por localidad y si no por comunidad, 12 meses por defecto, hasta 5
años (12); `nearby-conflicts` con `max-related` y `400 TOO_MANY_RECORDS` (13); `sensitive-distances` con `distanceKm` (5
por defecto) y los tipos ZONA DE PROTECCION, FUENTE DE AGUA, SITIO SAGRADO y RESTO ARQUEOLOGICO (14); los desempates de
`locate` (16).

- [ ] **Paso 3: §4, los servicios (6.2b)**

Con las formas de «Interfaces con el portal» de 6.2b:

- `GET /api/sgspe/map/settings` (autenticado) → `{ basemap: { type: 'style', url } o { type: 'none' }, attribution }`
  (ASM-125). Fila nueva.
- `GET /api/sgspe/territory/imports/formats` → `{ formats: [{ format, available, multiLayer, crsFromFile }] }` con
  `xlsx`, `csv`, `geojson`, `shp`, `kml`, `kmz`, `gpkg`, `gdb` y `gpx`. Fila nueva.
- `POST /api/sgspe/territory/imports` y su confirmación: la parte `layer`, `target` con `community` y
  `environmental_feature` (ASM-123), `format`, `layer` y `detectedCrs` en la vista previa, `400 LAYER_REQUIRED` con
  `layers[]: { name, geometryType, featureCount, detectedCrs }`, `sourceCrs` exigido aunque haya `detectedCrs`, KML, KMZ y
  GPX fijos en 4326, `422 FORMAT_UNAVAILABLE` (ASM-127).
- `GET /api/sgspe/territory/wms` → imagen o XML de capacidades; `401`, `403`, `422 WMS_REQUEST_NOT_ALLOWED` o
  `WMS_PARAMETER_NOT_ALLOWED`, `503 GEOSERVER_UNAVAILABLE` (ASM-121).
- `POST /api/sgspe/territory/exports` con `{ object, geometry?, format, coordinates, filters: { project?, componentType?,
  tenureType? }, disclosureLevel, checklistConfirmed? }` → archivo `<objeto>-<nivel>.<ext>` (`shp` como `.zip`); con UTM,
  `filters.project` obligatorio; con LATLON, `easting` y `northing` en la zona UTM del centroide con la columna
  `utm_zone`; fuera de INTERNO se quitan `land_parcel.registry_record`, los cuatro campos registrales de `community`,
  `source_reference` e `import_batch_id`, y `project` sale solo con `code` y `name`; sin filtro, EXTERNO aplica el filtro
  fijo de la capa; códigos `400 OBJECT_NOT_EXPORTABLE`, `403 DISCLOSURE_NOT_ALLOWED`, `503 EXPORT_TIMEOUT`,
  `400 INVALID_FORMAT`, `INVALID_COORDINATES` e `INVALID_GEOMETRY`, y los de las exportaciones de F2
  (`INVALID_DISCLOSURE_LEVEL`, `CHECKLIST_REQUIRED`, `INVALID_FILTER`, `TOO_MANY_RECORDS`, `NOT_ASSIGNED`) (ASM-124).
- `POST /api/sgspe/territory/geometry-checks` con `{ geometry, object, field, project? }` → `{ valid, reason,
  typeMatches, normalized, repaired, areaHaBefore, areaHaAfter, zone: { srid, outOfZone, outOfRegion } }`; READ sobre el
  objeto; no escribe.
- `GET /api/sgspe/territory/data-quality?check=&object=&project=&page=&size=` → `{ check, content: [{ object, id, label,
  field, detail }], page, size, hasNext }`; `check` obligatorio (`INVALID`, `OUT_OF_ZONE`, `STALE_AREA`, `NO_TERRITORY`,
  `NO_GEOMETRY`); `size` hasta 200; un `object` que no se lee, `403`.
- `GET /api/sgspe/territory/layers/{layer}`: fase «F2 (básicas), F6 (gestión)»; LYR-17 a LYR-24 desde F6 (decisión 6).
- `GET /api/sgspe/territory/wfs`: la fila sale de F6 o queda con «pospuesto (ASM-120)».

- [ ] **Paso 4: §2, §3.3 y §5**

- **§2:** la acción `EXPORT` nombra los 13 objetos territoriales desde F6 (ASM-124), además de los de F2 a F5.
- **§3.3 (tabla de códigos por estado):** todos los códigos de la comprobación del Paso 1 en su fila de estado.
- **§5** (y donde se diga «proxies WMS y WFS»): proxy WMS; el WFS, pospuesto (ASM-120).
- El tablero `territory` (en la sección de tableros de la API): exige `project` (`400 PROJECT_REQUIRED`); KPI-TER-02 a
  04 y, con `locality` o `community`, KPI-TER-01 con su desglose `by: "components"`.

- [ ] **Paso 5: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas. Después:

```bash
git add docs/api.md
git commit -F - <<'MSG'
docs: contratos de la API de F6

Las consultas espaciales con available, missingFields y truncated; mapa base, formatos de
importación, proxy WMS, exportaciones geográficas, validación y calidad; los códigos nuevos; el WFS
pospuesto (ASM-120).

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 5: SIG y los ADR

**Archivos:**
- Modificar: `docs/gis.md`, `docs/adr/0015-mapas-de-informe-con-geoserver.md`,
  `docs/adr/0019-importacion-geoespacial-con-reconciliacion.md`, `docs/adr/0026-topologia-puertos-y-observabilidad.md`,
  `docs/adr/README.md`

**Interfaces:**
- Consume: ASM-120 a ASM-127; las decisiones 3, 4, 8 y 9; plan 6.1 «Interfaces» (capas, estilos, paleta, compose).
- Produce: la descripción de `layers.json`, estilos y paleta que copia 6.3; el puerto 8095 que lee 6.1.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import re
gis = open('docs/gis.md', encoding='utf-8').read()
bad = []
for want in ('SpatialGate', 'ZoneCriterion', 'layers.json', 'palette.json', 'publish_layers.py', '/api/sgspe/map/settings',
             'FORMAT_UNAVAILABLE', 'OpenFileGDB', 'NO_TERRITORY', 'NO_GEOMETRY', 'STALE_AREA', 'OUT_OF_ZONE',
             'ASM-120', 'ASM-121', 'ASM-122', 'ASM-123', 'ASM-124', 'ASM-125', 'ASM-127', 'community.territory',
             'environmental_feature.boundary', 'sgspe-lyr-02-location'):
    if want not in gis:
        bad.append(f'gis.md: falta {want}')
for line in gis.splitlines():
    if 'UPS-13' in line and 'esuelta' not in line:
        bad.append(f'gis.md: UPS-13 sigue abierta: {line[:90]}')
    if 'WFS' in line and 'ASM-120' not in line and 'pospuesto' not in line:
        bad.append(f'gis.md: WFS vigente: {line[:90]}')
header = next(l for l in gis.splitlines() if l.startswith('| ID | Capa | Objeto.campo |'))
if '| Sensibilidad |' not in header:
    bad.append('gis.md §2.2: la cabecera perdió «Sensibilidad»')
for path, want in (('docs/adr/0015-mapas-de-informe-con-geoserver.md', 'Aceptado'),
                   ('docs/adr/0019-importacion-geoespacial-con-reconciliacion.md', 'Aceptado'),
                   ('docs/adr/0026-topologia-puertos-y-observabilidad.md', '8095')):
    if want not in open(path, encoding='utf-8').read():
        bad.append(f'{path}: falta {want}')
adr = open('docs/adr/0026-topologia-puertos-y-observabilidad.md', encoding='utf-8').read()
for line in adr.splitlines():
    if 'GeoServer' in line and re.search(r'80[0-9][0-9]', line) and '8095' not in line:
        bad.append(f'ADR-0026: GeoServer con otro puerto: {line[:90]}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: `gis.md`**

Sin cambiar la cabecera de §2.2 (`| ID | Capa | Objeto.campo | Geometría | Fuente del dato | Sensibilidad | Cómo se
sirve | Fase | Respaldo |`), ni las celdas «Objeto.campo» de LYR-01 a 05, 07 y 16, ni la Sensibilidad «Pública» de esas
siete (las lee `design_docs.public_layers()` del plan 6.1); ninguna otra fila puede empezar su Sensibilidad con «Pública»:

- **§1.3** (tabla de lo que da la plataforma): el mapa base deja de esperar UPS-13 (resuelta, ASM-125).
- **§4.1:** los formatos GDAL y los dos destinos nuevos (ASM-123, ASM-127), cerrando C2: la nota «agregando primero los
  campos al modelo de dominio» pasa a decir que F6 los agrega. `GdalReader` (`ogrinfo -json -so`, `ogr2ogr -f GeoJSON`
  sin reproyectar), elección de capa (`LAYER_REQUIRED`), `detectedCrs` como propuesta, KML, KMZ y GPX en 4326, sin GDAL
  `422 FORMAT_UNAVAILABLE` e `imports/formats`. Las claves `sgspe.territory.gdal.path`, `min-version` (`3.8`) y `timeout`
  y `sgspe.territory.import.max-entries` (1000) y `max-inflated-bytes` (209715200).
- **§4.4:** la reconciliación por `code` vale también para `community` y `environmental_feature`; fila sin `code`,
  rechazada; el alta lleva solo `code`, el nombre y la geometría; `environmental_feature` exige `feature_type`.
- **§4.6:** exportaciones solo de LYR-01 a LYR-16 con la tabla de divulgación por capa de la especificación §3.5, los
  formatos y CRS, `export_log` y el tope `sgspe.exports.max-rows` (ASM-124); el WFS, pospuesto (ASM-120).
- **§5.2:** `SpatialGate` (dónde vive, qué compone, que devuelve ids y medidas y que los titulares se leen después por
  `Records`) y `ZoneCriterion`; la regla de `ArchitectureTest` (ASM-122). **§5.3:** cada caso con su endpoint y los
  límites de ASM-126 (`sgspe.territory.queries.*`).
- **§6.4:** el mapa base por `/map/settings` (`sgspe.map.base-style-url`, `base-style-url-by-organization`,
  `attribution`), `gisModule({ basemap })` tras el login y la prop `basemap`; «Sin fondo» sobre el propio mapa, junto al
  aviso «Sin mapa base» (decisión 8); producción sin `tile.openstreetmap.org`.
- **§7.1:** las capas publicadas: `model/layers.json` con la tabla de las nueve entradas del plan 6.1 («Interfaces»,
  «Capas, estilos y paleta»), nombre de capa `<tabla física>__<columna>` en el workspace `sgspe`, `publish_layers.py`
  (idempotente, `--dry-run`, `--validate-only`, credenciales por entorno, `--skip-layers` en el alta y la subida) y el
  proxy (ASM-121). **§7.2:** un SLD 1.0.0 por estilo en `model/styles/`, con reglas de texto según `label`, y
  `model/styles/palette.json` con la forma de la decisión 9, que el portal copia en `src/portal/mapa/paleta.json`.
- **§8:** las cinco revisiones con sus códigos (`INVALID`, `OUT_OF_ZONE`, `STALE_AREA`, `NO_TERRITORY`, `NO_GEOMETRY`),
  `geometry-checks` y S63.
- **§9:** las pruebas de F6 de la especificación §5, por clase.
- **§10 y §11:** GeoServer `docker.osgeo.org/geoserver:3.0.1` en loopback (8095), WMS por el proxy, WFS pospuesto; UPS-13
  resuelta; GAP-011 y GAP-012 siguen abiertas (riesgo 1 de la especificación §8).

- [ ] **Paso 3: Los ADR**

- **ADR-0015:** estado «Aceptado en parte · 2026-10-11 (F6): GeoServer interno, estilos SLD y proxy WMS; los mapas de
  informe por GetMap siguen para F8 con su spike» (C7), con una nota fechada que cite ASM-120 y ASM-121. `docs/adr/README.md`
  igual.
- **ADR-0019:** «Aceptado» (la importación está en uso desde F2), con una nota fechada de F6 que cite ASM-123 y ASM-127.
  README igual.
- **ADR-0026:** GeoServer en la topología: servicio `geoserver` (`sgspe-geoserver`) solo en loopback del VPS en
  **8095** (contenedor 8080, `SGSPE_GEOSERVER_PORT`), sin CORS, nunca alcanzable por el navegador; nombrar los puertos
  ya tomados (8094, 5182, 5435, 9095 y 9096, 1029 y 8029, 3094 y 8194, 8098; 8081 a 8086 de otras apps). La línea que
  nombra GeoServer y su puerto no nombra otro puerto 80xx.

- [ ] **Paso 4: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas (la suite del modelo comprueba que `gis.md` §2.2
sigue legible). Después:

```bash
git add docs/gis.md docs/adr/0015-mapas-de-informe-con-geoserver.md \
  docs/adr/0019-importacion-geoespacial-con-reconciliacion.md docs/adr/0026-topologia-puertos-y-observabilidad.md \
  docs/adr/README.md
git commit -F - <<'MSG'
docs: SIG de F6: compuerta, capas publicadas, GDAL, exportaciones, mapa base y ADR

gis.md describe SpatialGate, layers.json, estilos y paleta, GDAL con los destinos nuevos, las
exportaciones por capa, el mapa base y la calidad; ADR-0015 y ADR-0019 aceptados; GeoServer en 8095.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 6: Tableros y pantallas

**Archivos:**
- Modificar: `docs/dashboards.md`, `docs/ux/screens.md`, `docs/ux/ux-architecture.md`

**Interfaces:**
- Consume: ASM-122, 124, 125 y 126; las decisiones 6, 7 y 8; plan 6.2a «Interfaces con el portal» (tablero) y plan 6.3
  («Ajustes tras el plan 6.0», decisiones 7 a 21).
- Produce: el tablero `territory` que implementa 6.2a y las pantallas que implementa 6.3.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
dash = open('docs/dashboards.md', encoding='utf-8').read()
screens = open('docs/ux/screens.md', encoding='utf-8').read()
ux = open('docs/ux/ux-architecture.md', encoding='utf-8').read()
bad = []
for want in ('TERRITORY_REQUIRED', 'ignoredFilters', 'PROJECT_REQUIRED', '"components"'):
    if want not in dash:
        bad.append(f'dashboards.md: falta {want}')
for want in ('S63', '/territory/quality', '/territory/dashboard', 'geometry-checks', 'member-suggestions',
             'imports/formats', 'Sin mapa base', 'LYR-17'):
    if want not in screens:
        bad.append(f'screens.md: falta {want}')
for want in ('S63', '/territory/quality', '/territory/dashboard'):
    if want not in ux:
        bad.append(f'ux-architecture.md: falta {want}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: `dashboards.md`**

- **§4.8 (KPI-TER):** KPI-TER-01 sin valor propio (`value: null`) con el desglose `by: "components"` y sus filas
  `KPI-STK-02`, `KPI-ENG-01`, `KPI-COM-02`, `KPI-COM-03`, `KPI-GRV-02`, `KPI-RSK-01`, `programs`, `KPI-INV-03` y
  `lastActivity`, cada una con `available` y, si falta, `unavailableReason` y `missingFields`; exige `locality` o
  `community` (si no, `available: false`, `TERRITORY_REQUIRED`, `missingFields: ["locality", "community"]`); con
  `community`, KPI-STK-02 lo informa en `ignoredFilters` (ruling P27); sin periodo, KPI-ENG-01 de los últimos 90 días.
  KPI-TER-02 (`ha`, desglose `area`), KPI-TER-03 (`miembros`, desgloses `areaType`, `memberType`, `memberCondition`) y
  KPI-TER-04 (`componentes`, desgloses `tenureType` con `TERRITORIO COMUNAL` y `componentType`; consulta de
  `SpatialGate` compartida con `summary`, ASM-122) filtran solo por `project` y declaran ignorados los demás filtros.
- **§5 (tablero `territory`):** `GET /api/sgspe/dashboards/territory` exige `project` (`400 PROJECT_REQUIRED`); reúne
  KPI-TER-02 a 04 y, con `locality` o `community`, KPI-TER-01 primero (decisión 7). Corregir la frase que hoy exige
  `locality` o `community` para el tablero.
- **§7 y §8:** los códigos y la forma «no disponible» de KPI-TER (RQ-DSH-032).

- [ ] **Paso 3: `ux/screens.md` y `ux/ux-architecture.md`**

Con las decisiones de 6.3 (sección «Decisiones» de ese plan y «Ajustes tras el plan 6.0»):

- **S12 Mapa territorial (`/territory`):** mapa base de `/map/settings` o «Sin mapa base», con «Sin fondo» sobre el mapa
  (decisión 8); capas por grupo (Referencia, Proyecto, Tenencia, Gestión): las públicas por WMS a través del proxy y el
  resto por GeoJSON; si el proxy responde `503`, las WMS vuelven al GeoJSON con un aviso y «Reintentar»; Gestión lista
  LYR-17 a LYR-24 (decisión 6) y el calor (`concentration`); leyenda con la paleta; filtros de proyecto y periodo; captura
  PNG; `?selected=locality:<id>`; **ficha contextual** con la parte territorial (`…/card`), las cifras de KPI-TER-01 y los
  registros de la zona (`zones/…/records?of=`), cada componente con su «no disponible», y «Ver resumen».
- **Tablero territorial:** S12 en modo tablero en `/territory/dashboard`, exige proyecto, con los tiles de KPI-TER-02 a
  04 (decisión 7).
- **S09 Mapa del proyecto:** panel «Análisis» con seis tablas (predios afectados con titulares si hay permiso, territorio
  comunal, elementos sensibles, localidades en un buffer con control de metros, territorios de las concesiones, conflictos
  cercanos); cada fila selecciona su registro y resalta su geometría.
- **S10 Área de influencia:** «Sugerencias del cálculo» con `member-suggestions`, una a la vez; aceptar crea el miembro
  con `spatially_suggested: true` y condición PROVISIONAL por defecto; nada se escribe sin «Aceptar»; resumen con
  `summary`.
- **S13 y S14:** antes de guardar una geometría editada, `geometry-checks` con el motivo y la reparación propuesta.
- **S15 Importación:** formatos de `imports/formats` elegidos por extensión, elección de capa, «Usar el CRS del archivo»
  como propuesta, destinos comunidad y elemento ambiental.
- **S24 (paso «Lugar») y el formulario rápido de campo:** un clic llama a `locate` y propone unidad, localidad y
  comunidad, que la persona confirma.
- **Exportar:** diálogo desde S12 y S09 (objeto, formato, coordenadas y nivel, solo los permitidos por la capa, con la
  casilla de EXTERNO).
- **S63 Calidad de datos** (nueva, en Territorio, `/territory/quality`): una pestaña por revisión (`INVALID`,
  `OUT_OF_ZONE`, `STALE_AREA`, `NO_TERRITORY`, `NO_GEOMETRY`), filtros de objeto y proyecto, lista paginada con enlace a
  cada registro.
- **`ux-architecture.md`:** las rutas `/territory/dashboard` y `/territory/quality` en la lista de rutas y S63 en el
  inventario (§9).

- [ ] **Paso 4: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y las compuertas. Después:

```bash
git add docs/dashboards.md docs/ux/screens.md docs/ux/ux-architecture.md
git commit -F - <<'MSG'
docs: tablero territorial, KPI-TER y pantallas de F6

El tablero territory exige proyecto y suma KPI-TER-01 con territorio; S12 con capas por grupo, WMS,
calor y ficha; S09, S10, S13 a S15 y S24 con las consultas de F6; S63 Calidad de datos.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 7: Arquitectura, roadmap, trazabilidad, encaje con la plataforma y glosario

**Archivos:**
- Modificar: `docs/architecture.md`, `docs/roadmap.md`, `docs/requirements-traceability.md`, `docs/platform-fit.md`,
  `docs/glossary.md`

**Interfaces:**
- Consume: ASM-120 a ASM-127; las decisiones 6, 10 y 11.
- Produce: la fila F6 del roadmap a la que 6.2a y 6.3 agregan «Estado»; las filas de trazabilidad de F6.

- [ ] **Paso 1: La comprobación falla**

Contar antes los estados de la trazabilidad, para comparar después:

```bash
cd ~/IdeaProjects/sgspe && awk -F'|' '/^\| R[FQN]/{print $(NF-1)}' docs/requirements-traceability.md | sort | uniq -c | sort -rn > "$TMPDIR/estados-antes.txt"; cat "$TMPDIR/estados-antes.txt"
python3 - <<'PY'
road = open('docs/roadmap.md', encoding='utf-8').read()
f6 = road.split('## F6 · SIG avanzado', 1)[1].split('\n## ', 1)[0]
arch = open('docs/architecture.md', encoding='utf-8').read()
trace = open('docs/requirements-traceability.md', encoding='utf-8').read()
fit = open('docs/platform-fit.md', encoding='utf-8').read()
bad = []
for want in ('/api/sgspe/map/settings', 'imports/formats', 'gdal-bin', 'ASM-120', 'LYR-17', 'publish_layers.py', '8095'):
    if want not in f6:
        bad.append(f'roadmap F6: falta {want}')
if 'territory/wfs' in f6.replace('ASM-120', ''):
    bad.append('roadmap F6: el WFS sigue en los endpoints')
for want in ('SpatialGate', 'GeoServer', 'gdal-bin'):
    if want not in arch:
        bad.append(f'architecture.md: falta {want}')
rq13 = next(l for l in trace.splitlines() if l.startswith('| RQ-GIS-013 |'))
if 'Upstream (UPS-13)' in rq13:
    bad.append('trazabilidad: RQ-GIS-013 sigue en Upstream (UPS-13)')
rq18 = next(l for l in trace.splitlines() if l.startswith('| RQ-GIS-018 |'))
if 'ASM-120' not in rq18 or 'TerritoryExportsApiTest' not in rq18:
    bad.append('trazabilidad: RQ-GIS-018 sin ASM-120 o sin sus pruebas')
ups = next(l for l in fit.splitlines() if l.startswith('| UPS-13'))
if 'esuelta' not in ups:
    bad.append('platform-fit.md: UPS-13 sigue abierta')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: `architecture.md`**

§5 (paquetes): `sgspe.territory` suma `SpatialGate`, `ZoneCriterion`, las consultas, `WmsProxy`, `MapSettings`,
`GdalReader` y `GdalWriter`, `TerritoryExports`, `GeometryChecks` y `DataQuality`, y la regla de `ArchitectureTest`
sobre `DatabaseClient` (ASM-122); `sgspe.dashboard` suma `TerritoryKpis` y depende de `sgspe.territory`, nunca al revés.
§10 (despliegue): GeoServer en el compose solo en loopback (8095), datastore con `sgspe_geoserver`, nunca público;
`gdal-bin` (3.8 o superior) en los trabajos de integración de la CI y en el desarrollo (ASM-127); un backend de F6 sin
GeoServer funciona y el proxy responde `503`; orden de despliegue de la especificación §7.

- [ ] **Paso 3: `roadmap.md` F6**

En la tabla de F6: «Alcance» sin el WFS y con las capas de gestión LYR-17 a LYR-24 en el mapa territorial (decisión 6);
«Starters y capacidades» con GeoServer en loopback (8095) y `gdal-bin` ≥ 3.8 en la CI (en lugar de «GDAL en la imagen del
backend»), `model/publish_layers.py`, `layers.json`, estilos y paleta; «Código SGSPE» con `SpatialGate` y sin «proxies WMS
y WFS»; «Endpoints» con `/api/sgspe/map/settings` y `imports/formats`, sin `/territory/wfs`; «Objetos»: ninguno nuevo,
`code` y los campos de origen en `community` y `environmental_feature`, `EXPORT` en los 13 objetos; «Definición de
terminado» con la de la especificación §7 (pruebas de §5, `ArchitectureTest` con la regla de `SpatialGate`, capas
publicadas en el tenant de desarrollo y vistas por el proxy, la medición en «Estado», el tenant `demo` en F6); «Riesgos»
sin «mapa base fijo en `MapView` (UPS-13)», con los riesgos de la especificación §8; «IDs abiertos» sin UPS-13 y con
ASM-120 a ASM-127. No agregar la fila «Estado»: la agrega 6.2a. En las tablas de brechas del roadmap (líneas que nombran
UPS-13 en «Antes de producción», el enlace a wasichai-ui#38 y el riesgo 20 «Mapa base fijo en `MapView`»), UPS-13 queda
como resuelta.

- [ ] **Paso 4: Trazabilidad, encaje y glosario**

- **`requirements-traceability.md`:** las filas RQ-GIS-002, 003, 005, 008, 011, 012, 013, 014, 015, 017, 018 y 019,
  RF-TER-003 y RF-TER-008, RQ-SEC-011 y RQ-DSH-029 ganan sus piezas de F6 (`SpatialGate`, endpoints, `publish_layers.py`)
  y sus pruebas de la especificación §5, sin cambiar su estado salvo RQ-GIS-013 (decisión 10: «Upstream (UPS-13); Fuera
  del MVP» pasa a «Diseñado; Fuera del MVP»); RQ-GIS-018 nombra ASM-120 y ASM-124. La fila de RQ-GIS-013 en la tabla de
  «No aplica» (línea ~675) no cambia de forma. §8 no se toca (decisión 11).
- **`platform-fit.md`:** UPS-13 resuelta (fila y párrafo de las brechas de wasichai-ui); la fila de GIS nombra el proxy
  WMS, `basemap` de `gisModule` y GDAL.
- **`glossary.md`:** «Capa pública» (LYR publicada en GeoServer, ASM-121), «Compuerta espacial» (`SpatialGate`,
  ASM-122) y «Mapa base» (por organización, ASM-125), con el formato de las entradas existentes y en orden alfabético.

- [ ] **Paso 5: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`) y comparar los estados:

```bash
cd ~/IdeaProjects/sgspe && awk -F'|' '/^\| R[FQN]/{print $(NF-1)}' docs/requirements-traceability.md | sort | uniq -c | sort -rn | diff "$TMPDIR/estados-antes.txt" - ; true
```

Esperado: solo «Upstream (UPS-13); Fuera del MVP» baja de 1 a 0 y «Diseñado; Fuera del MVP» sube en 1. Las compuertas de
«Global Constraints» en verde. Después:

```bash
git add docs/architecture.md docs/roadmap.md docs/requirements-traceability.md docs/platform-fit.md docs/glossary.md
git commit -F - <<'MSG'
docs: arquitectura, roadmap y trazabilidad de F6

GeoServer en loopback, GDAL en la CI y SpatialGate en la arquitectura; F6 sin WFS y con las capas de
gestión en el roadmap; RQ-GIS de F6 con sus pruebas y UPS-13 resuelta.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 8: Desarrollo, `CLAUDE.md` y la historia

**Archivos:**
- Modificar: `docs/develop/README.md`, `CLAUDE.md`, `docs/HISTORY.md`

**Interfaces:**
- Consume: las Tareas 1 a 7 integradas; plan 6.1 «Interfaces» (compose y configuración) y Tarea 9 (rol
  `sgspe_geoserver`); plan 6.2a Tarea 16, Paso 2 (filas de `CLAUDE.md`); plan 6.2b «Mapa de archivos».
- Produce: el puerto 8095 que lee la Tarea 1 de 6.1; el orden de aprovisionamiento; la fila de `SpatialGate`.

- [ ] **Paso 1: La comprobación falla**

```bash
cd ~/IdeaProjects/sgspe && python3 - <<'PY'
import re
bad = []
claude = open('CLAUDE.md', encoding='utf-8').read()
dev = open('docs/develop/README.md', encoding='utf-8').read()
hist = open('docs/HISTORY.md', encoding='utf-8').read()
for want in ('publish_layers.py', '--skip-layers', 'GeoServer 8095', 'SpatialGate', 'WmsProxy', 'GdalReader',
             'layers.json'):
    if want not in claude:
        bad.append(f'CLAUDE.md: falta {want}')
for want in ('8095', 'sgspe_geoserver', 'WASICHAI_GIS_GEOSERVER_URL', 'SGSPE_GDAL_PATH', 'FORMAT_UNAVAILABLE',
             'publish_layers.py'):
    if want not in dev:
        bad.append(f'develop/README.md: falta {want}')
for path, text in (('CLAUDE.md', claude), ('docs/develop/README.md', dev)):
    for line in text.splitlines():
        if 'GeoServer' in line:
            ports = set(re.findall(r'80[0-9][0-9]', line))
            if ports and ports != {'8095'}:
                bad.append(f'{path}: GeoServer con otro puerto 80xx en la línea: {line[:90]}')
if '## 2026-10-11 — Plan 6.0: los documentos de F6' not in hist:
    bad.append('HISTORY.md: falta la entrada del plan 6.0')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
```

Esperado: salida 1.

- [ ] **Paso 2: `docs/develop/README.md`**

Una sección «GeoServer» (reemplaza la mención de `localhost:8081`): servicio `geoserver` del compose
(`docker.osgeo.org/geoserver:3.0.1`, contenedor `sgspe-geoserver`, volumen `geoserver-data`, sin CORS), en el VPS en
`127.0.0.1:8095` y por el túnel `ssh -fN -L 8095:localhost:8095 "$SGSPE_VPS"`; credenciales de desarrollo `sgspe` /
`sgspe-geoserver`; workspace `sgspe`; datastore `sgspe-postgis` hacia `postgis:5432/sgspe` con el rol de solo lectura
`sgspe_geoserver`, que se crea una vez en la base de desarrollo con el SQL del plan 6.1 (Tarea 9, líneas ~3629 a 3639:
`CREATE ROLE sgspe_geoserver LOGIN PASSWORD 'sgspe-geoserver-lectura'`, `GRANT USAGE` y `SELECT` sobre `app_data` y los
privilegios por defecto de las tablas futuras; copiarlo de ahí); las variables `WASICHAI_GIS_GEOSERVER_ENABLED`, `URL`,
`USERNAME`, `PASSWORD` y `WASICHAI_GIS_GEOSERVER_DATASTORE_HOST`, `PORT`, `DATABASE`, `USERNAME`, `PASSWORD` en una tabla;
`publish_layers.py` después de `load_ubigeo.py`. Una sección «GDAL»: `ogrinfo` y `ogr2ogr` 3.8 o superior (3.13.3 en la
máquina de desarrollo, en `/opt/homebrew/bin`), `SGSPE_GDAL_PATH`, y que sin GDAL los formatos GDAL responden
`422 FORMAT_UNAVAILABLE`. Una línea para el mapa base de desarrollo (`SGSPE_MAP_BASE_STYLE_URL`, por defecto
`https://demotiles.maplibre.org/style.json`). Cada línea que nombre GeoServer y un puerto 80xx nombra solo 8095.

- [ ] **Paso 3: `CLAUDE.md`**

- **Reglas, aprovisionamiento:** `apply.py` → `apply_roles.py` → `apply.py --workflows` → `load_ubigeo.py` →
  `publish_layers.py` (omitible con `--skip-layers` si el despliegue no tiene GeoServer).
- **Puertos:** una viñeta o frase propia «GeoServer 8095 (solo loopback, por el túnel)», en una línea que no nombre otro
  puerto 80xx (la línea actual de puertos nombra 8094 y 8029: agregar GeoServer en una oración aparte en su propia línea).
- **«Dónde está cada cosa»:** después de la fila que empieza con «| CRS, área oficial, áreas de influencia», las filas de
  6.2a con su texto exacto (plan 6.2a, Tarea 16, Paso 2: la de `SpatialGate` y las consultas, la de KPI-TER y la de
  `F6QueryApiTest`), para que 6.2a no las duplique; una fila «Servicios SIG: mapa base (`MapSettings`), proxy WMS
  (`WmsRequestFilter`, `WmsProxy`), GDAL (`GdalCommand`, `GdalTools`, `GdalReader`, `ImportFormats`, `GdalWriter`),
  exportaciones geográficas (`ExportDisclosure`, `TerritoryExports`), validación (`GeometryChecks`) y calidad
  (`DataQuality`) | `src/main/kotlin/sgspe/territory/`»; una fila «Capas públicas de GeoServer (`layers.json`), estilos
  SLD y paleta (`styles/`), publicación (`publish_layers.py`) | `model/`». Si los nombres de las clases de 6.2b difieren
  en su «Mapa de archivos», ganan los del plan.

- [ ] **Paso 4: La entrada de la historia**

En `docs/HISTORY.md`, antes de `## 2026-10-11 — Plan 5.0: los documentos de F5`, una entrada
`## 2026-10-11 — Plan 6.0: los documentos de F6` con el estilo de la del plan 5.0: un párrafo (los documentos quedan al
día con `docs/superpowers/specs/2026-10-11-fase-6-design.md`; ASM-120 a ASM-127; 547 IDs) y viñetas: **Consultas**
(`SpatialGate`, zona como parámetro, límites), **GeoServer** (capas públicas TENANT, proxy de solo imágenes, 8095, WFS
pospuesto), **Mapa base** (por organización, UPS-13 resuelta), **Importación y exportación** (GDAL, destinos nuevos,
divulgación por capa), **Calidad y pantallas** (S63, tablero territorial), **Huecos cerrados** (C1 a C9), **Lo que
espera al modelo de F6** (acciones, únicos, índices y celdas de `EXPORT` en prosa hasta el plan 6.1).

- [ ] **Paso 5: Verificar y commit**

Repetir la comprobación del Paso 1 (esperado: `ok`), la comprobación del puerto del plan 6.1:

```bash
cd ~/IdeaProjects/sgspe && grep -n "GeoServer" docs/adr/0026-topologia-puertos-y-observabilidad.md CLAUDE.md docs/develop/README.md | grep -o "80[0-9][0-9]\|localhost:[0-9]*" | sort | uniq -c
```

(esperado: solo `8095` y, si aparece, `localhost:8095`) y las compuertas. Después:

```bash
git add docs/develop/README.md CLAUDE.md docs/HISTORY.md
git commit -F - <<'MSG'
docs: GeoServer y GDAL en desarrollo, CLAUDE.md e historia del plan 6.0

El README de desarrollo describe GeoServer en 8095, su rol de solo lectura y GDAL; CLAUDE.md suma
publish_layers.py al aprovisionamiento y las piezas de F6; la historia resume el plan.

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

### Tarea 9: Verificación final, push y PR

**Archivos:** ninguno nuevo.

- [ ] **Paso 1: Todo lo local, la comprobación cruzada y la lectura cruzada**

```bash
cd ~/IdeaProjects/sgspe
(cd model && python3 -m unittest -v 2>&1 | tail -3 && python3 apply.py --validate-only && \
  python3 load_ubigeo.py --dry-run | tail -1)
python3 tools/check_traceability.py && yarn format:check
python3 - <<'PY'
import re, glob
paths = ['docs/api.md', 'docs/gis.md', 'docs/security.md', 'docs/dashboards.md', 'docs/ux/screens.md',
         'docs/roadmap.md', 'docs/requirements-analysis.md', 'docs/database-model.md', 'docs/architecture.md',
         'docs/platform-fit.md', 'docs/requirements-traceability.md', 'docs/develop/README.md', 'CLAUDE.md']
docs = {p: open(p, encoding='utf-8').read() for p in paths}
bad = []
# Review Focus 2: cada código de F6 en api.md y donde lo usa su documento
for code, others in (('TERRITORY_REQUIRED', ['docs/dashboards.md']), ('FORMAT_UNAVAILABLE', ['docs/gis.md']),
                     ('GEOSERVER_UNAVAILABLE', ['docs/security.md']), ('DISCLOSURE_NOT_ALLOWED', ['docs/gis.md']),
                     ('WMS_PARAMETER_NOT_ALLOWED', ['docs/security.md']), ('NO_TERRITORY', ['docs/gis.md', 'docs/ux/screens.md'])):
    for p in ['docs/api.md'] + others:
        if code not in docs[p]:
            bad.append(f'{p}: falta {code}')
# Review Focus 3: cada ASM nuevo se define una vez y otro documento lo cita
for n in range(120, 128):
    asm = f'ASM-{n}'
    if len(re.findall(rf'^\| {asm} \|', docs['docs/requirements-analysis.md'], re.M)) != 1:
        bad.append(f'{asm}: no se define una sola vez')
    if not any(asm in t for p, t in docs.items() if p != 'docs/requirements-analysis.md'):
        bad.append(f'{asm}: ningún otro documento lo cita')
for p, t in docs.items():
    for line in t.splitlines():
        if 'UPS-13' in line and 'esuelta' not in line and 'wasichai-ui#38' not in line:
            bad.append(f'{p}: UPS-13 sin «resuelta»: {line.strip()[:90]}')
        # Review Focus 4: el WFS, pospuesto
        if ('territory/wfs' in line or re.search(r'\bWFS\b', line)) and 'ASM-120' not in line and 'pospuest' not in line \
                and 'RQ-GIS-018' not in line:
            bad.append(f'{p}: WFS vigente: {line.strip()[:90]}')
print('\n'.join(bad) or 'ok')
raise SystemExit(1 if bad else 0)
PY
git status --short
```

Esperado: `Ran 239 tests … OK`, `ubigeo: 25 departamentos, 196 provincias, 1892 distritos`,
`ok: 298 requisitos, todos con su fila`, prettier sin cambios, `ok` de la comprobación cruzada y un `git status` vacío. Una
línea que nombre el WFS en el pasado o en una nota fechada que no lo prometa en F6 se reescribe para citar ASM-120 (nunca se
afloja la comprobación).

Lectura cruzada (Review Focus 2): leer seguidos `api.md` §4 (filas de F6), `dashboards.md` §4.8, `gis.md` §5.3 y §8 y
`ux/screens.md` S12 y S63, y comprobar que cada endpoint da la misma raíz, los mismos campos y los mismos códigos que
«Interfaces con el portal» de 6.2a y 6.2b. Una diferencia se corrige en el documento que se aparta, con un commit
`docs: …` propio.

- [ ] **Paso 2: Push y PR**

```bash
cd ~/IdeaProjects/sgspe && git push -u origin docs/fase-6-0-documentos
gh pr create --base main --title "docs: fase 6, plan 6.0: documentos de F6" --body-file - <<'MSG'
## Qué trae

- ASM-120 a ASM-127; §20.1 suma 547 IDs; UPS-13 resuelta con wasichai-ui#38. Ninguna brecha nueva.
- Consultas espaciales con `SpatialGate` y los 13 endpoints con su contrato (`available`, `missingFields`,
  `truncated`), KPI-TER y el tablero `territory`, que exige proyecto.
- GeoServer en loopback (8095) con capas públicas TENANT, estilos y paleta, y un proxy WMS de solo imágenes; el WFS,
  pospuesto (ASM-120).
- Mapa base por organización (`/map/settings`), GDAL con dos destinos nuevos, exportaciones geográficas con divulgación
  por capa, validación de geometrías y S63 «Calidad de datos».
- `community` y `environmental_feature` con `code` y los campos de origen; `EXPORT` de las capas territoriales en prosa
  hasta que el plan 6.1 lo lleve al modelo.
- Roadmap, trazabilidad, ADR-0015, ADR-0019 y ADR-0026, desarrollo, historia y `CLAUDE.md` al día.

## Pruebas

- Python: las 239 pruebas del modelo, con `test_las_fichas_de_f6_traen_lo_que_suma_el_plan_6_0`.
- `tools/check_traceability.py` y `yarn format:check`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01RmiD2JE3gkHdBNUraZkjgM
MSG
```

---

## Resumen

| Tarea | Entrega | Grupo |
|---|---|---|
| 1 | IDs de F6 en el análisis | 0 |
| 2 | Fichas, acciones en prosa y la prueba del lector | 1 |
| 3 | Seguridad | 1 |
| 4 | API | 1 |
| 5 | SIG y ADR | 1 |
| 6 | Tableros y pantallas | 1 |
| 7 | Arquitectura, roadmap, trazabilidad, encaje y glosario | 1 |
| 8 | Desarrollo, `CLAUDE.md` e historia | 2 |
| 9 | Verificación, push y PR | 2 |

## Cobertura de la especificación §6

| Punto de §6 | Dónde |
|---|---|
| ASM-120 a ASM-127; UPS-13 resuelta; §20.1 (547) | Tarea 1 |
| C1 · WFS en F6: `roadmap.md`, `api.md`, `gis.md` §4.6 y §10, RQ-GIS-018 | Tareas 7, 4, 5 y 1 (y `security.md` §10.2, Tarea 3) |
| C2 · Importación con CRS a MULTIPOLYGON: `domain-model.md`, `database-model.md`, `gis.md` §4.1 | Tareas 2 y 5 |
| C3 · Ficha contextual: `dashboards.md` §4.8, `TERRITORY_REQUIRED`, `ignoredFilters` | Tarea 6 |
| C4 · Mapa base y UPS-13: análisis, `gis.md` §1.3, §6.4 y §11, ASM-048, ASM-085, `roadmap.md`, `/map/settings` | Tareas 1, 5, 7 y 4 (y el portal, Tarea 6) |
| C5 · Lista de capas públicas en `layers.json` | Tareas 5 y 3 |
| C6 · Exportaciones de LYR-01 a LYR-16; `EXPORT` por objeto en `api.md` §2 y `security.md` §7 | Tareas 4 y 3 |
| C7 · ADR-0015 aceptado en parte; ADR-0019 aceptado | Tarea 5 |
| C8 · `SpatialGate` y la regla de `ArchitectureTest` | Tareas 5, 3 y 7 |
| C9 · S63 | Tarea 6 |
| `api.md` §4 (`/map/settings`, `imports/formats`, códigos nuevos, exportación) | Tarea 4 |
| `security.md` §10 (proxy y lista blanca) | Tarea 3 |
| `architecture.md` (GeoServer en el compose, GDAL en la CI) | Tarea 7 |
| `gis.md` §4, §6, §7.1, §8, §10 y §11 | Tarea 5 |
| `dashboards.md` §4.8 | Tarea 6 |
| `roadmap.md` F6 | Tarea 7 |
| `requirements-traceability.md` | Tarea 7 |
| `CLAUDE.md` y `docs/develop/README.md` | Tarea 8 |

## Dependencias de otros planes

Este plan no consume código de otro plan: parte de `main` con la especificación y los planes de F6. Lo que deja para otros:

- **Plan 6.1, en el mismo commit que `model.json` y `roles.json`:** pasar a la forma leída las 13 líneas de acciones
  (`; desde F6, EXPORT.` → `` `EXPORT` (desde F6) ``), los «Únicos» y los «Índices declarados» de `community` y
  `environmental_feature`, y las celdas de `EXPORT` y `C` de `export_log` de §7 (su Tarea 1, Paso 2, y Tarea 3, Paso 1,
  que ya reconocen estas formas); quitar la clase `F6PlanZeroTests` de `test_design_docs.py` (las formas en prosa dejan de
  existir) o cambiarla por las aserciones de su `F6DocsTests`.
- **Plan 6.2a:** agrega la fila «Estado» de `roadmap.md` F6 con la medición; las filas de `CLAUDE.md` ya están (solo
  agrega las que falten).
- **Plan 6.2c (capas de gestión, por escribir con 6.1 en `main`):** sirve LYR-17 a LYR-24 en `layers/{layer}` como dicen
  `gis.md` §2.2 y `api.md` (decisión 6).
- **Planes 6.2b y 6.3:** leen los nombres y los códigos de estos documentos; si al implementar cambia un nombre, se corrige
  primero el documento.
